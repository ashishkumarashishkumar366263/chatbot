package com.rudra.botauto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Foreground service: har enabled automation ke bot token ko long-poll karta hai,
 * incoming messages ko rules se match karta hai aur action chalata hai
 * (reply / forward / webhook). App minimize/recents se hat jaaye to bhi 24/7 chale.
 */
public class BotService extends Service {

    public static final String ACTION_START = "com.rudra.botauto.START";
    public static final String ACTION_STOP  = "com.rudra.botauto.STOP";
    public static final String ACTION_LOG   = "com.rudra.botauto.LOG";
    public static final String EX_MSG = "msg";
    public static final String EX_TYPE = "type";

    private static final String CH = "botauto_run";
    private static final int NOTIF_ID = 5001;

    private volatile boolean running = false;
    private final List<Thread> workers = new ArrayList<>();
    private final Map<String, Long> offsets = new HashMap<>();
    private PowerManager.WakeLock wakeLock;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopEngine();
            return START_NOT_STICKY;
        }

        startForeground(NOTIF_ID, buildNotif());
        Store.setRunning(this, true);
        if (!running) {
            running = true;
            acquireWakeLock();
            startWorkers();
            log("[SYSTEM] Engine started.", "success");
        }
        return START_STICKY;
    }

    private void startWorkers() {
        // Unique bot tokens (enabled automations se)
        Set<String> tokens = new HashSet<>();
        JSONArray autos = Store.load(this);
        for (int i = 0; i < autos.length(); i++) {
            JSONObject a = autos.optJSONObject(i);
            if (a == null) continue;
            if (!a.optBoolean("enabled", true)) continue;
            String t = a.optString("token", "").trim();
            if (!t.isEmpty()) tokens.add(t);
        }
        if (tokens.isEmpty()) {
            log("[SYSTEM] Koi enabled automation nahi. Automations me add karo.", "warn");
        }
        for (final String token : tokens) {
            Thread th = new Thread(() -> pollLoop(token), "poll");
            workers.add(th);
            th.start();
        }
    }

    private void pollLoop(final String token) {
        log("[BOT] Polling started (" + shortTok(token) + ")", "info");
        while (running) {
            try {
                long off = offsets.containsKey(token) ? offsets.get(token) : 0L;
                String url = "https://api.telegram.org/bot" + token +
                        "/getUpdates?offset=" + (off + 1) + "&timeout=25";
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(35000);
                String resp = readBody(c);
                c.disconnect();

                JSONObject data = new JSONObject(resp);
                if (!data.optBoolean("ok", false)) {
                    log("[BOT ERR] " + data.optString("description", "Unknown") + " (" + shortTok(token) + ")", "err");
                    sleep(3000);
                    continue;
                }
                JSONArray result = data.optJSONArray("result");
                if (result != null) {
                    for (int i = 0; i < result.length(); i++) {
                        JSONObject up = result.getJSONObject(i);
                        offsets.put(token, up.optLong("update_id", off));
                        handleUpdate(token, up);
                    }
                }
            } catch (Exception e) {
                if (running) { log("[NET] Retry... (" + shortTok(token) + ")", "warn"); sleep(2000); }
            }
        }
    }

    private void handleUpdate(String token, JSONObject up) {
        JSONObject post = up.optJSONObject("message");
        if (post == null) post = up.optJSONObject("channel_post");
        if (post == null) post = up.optJSONObject("edited_message");
        if (post == null) return;

        JSONObject chat = post.optJSONObject("chat");
        String chatId = chat != null ? String.valueOf(chat.opt("id")) : "";
        long messageId = post.optLong("message_id", 0);
        String text = post.optString("text", "");
        if (TextUtils.isEmpty(text)) text = post.optString("caption", "");

        // Har enabled automation (isi token wali) check karo
        JSONArray autos = Store.load(this);
        for (int i = 0; i < autos.length(); i++) {
            JSONObject a = autos.optJSONObject(i);
            if (a == null || !a.optBoolean("enabled", true)) continue;
            if (!token.equals(a.optString("token", "").trim())) continue;

            String filterChat = a.optString("chatId", "").trim();
            if (!filterChat.isEmpty() && !filterChat.equals(chatId)) continue;

            if (!matches(a, text)) continue;

            String name = a.optString("name", "rule");
            log("[MATCH] " + name + " <- " + chatId, "success");
            doAction(a, token, chatId, messageId, text);
        }
    }

    private boolean matches(JSONObject a, String text) {
        String trig = a.optString("trigger", "any");
        if ("any".equals(trig)) return true;
        String tt = a.optString("triggerText", "");
        if (tt.isEmpty()) return true;
        if ("contains".equals(trig)) {
            return text.toLowerCase().contains(tt.toLowerCase());
        }
        if ("regex".equals(trig)) {
            try { return Pattern.compile(tt, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text).find(); }
            catch (Exception e) { return false; }
        }
        return false;
    }

    private void doAction(JSONObject a, String token, String chatId, long messageId, String text) {
        String action = a.optString("action", "reply");
        String target = a.optString("target", "");
        String actionText = a.optString("actionText", "");
        try {
            if ("reply".equals(action)) {
                JSONObject body = new JSONObject();
                body.put("chat_id", chatId);
                body.put("text", applyVars(actionText, text, chatId));
                tgPost(token, "sendMessage", body);
                log("[ACTION] Replied -> " + chatId, "info");
            } else if ("forward".equals(action)) {
                JSONObject body = new JSONObject();
                body.put("chat_id", target);
                body.put("from_chat_id", chatId);
                body.put("message_id", messageId);
                tgPost(token, "forwardMessage", body);
                log("[ACTION] Forwarded -> " + target, "info");
            } else if ("webhook".equals(action)) {
                JSONObject body = new JSONObject();
                body.put("text", text);
                body.put("chat_id", chatId);
                body.put("message_id", messageId);
                int code = httpPost(target, body.toString());
                log("[ACTION] Webhook -> HTTP " + code, code >= 200 && code < 300 ? "info" : "err");
            }
        } catch (Exception e) {
            log("[ACTION ERR] " + e.getMessage(), "err");
        }
    }

    private String applyVars(String tmpl, String text, String chatId) {
        if (tmpl == null) return "";
        return tmpl.replace("{text}", text).replace("{chat}", chatId);
    }

    private void tgPost(String token, String method, JSONObject body) {
        try {
            int code = httpPost("https://api.telegram.org/bot" + token + "/" + method, body.toString());
            if (code < 200 || code >= 300) log("[TG ERR] " + method + " HTTP " + code, "err");
        } catch (Exception e) { log("[TG ERR] " + e.getMessage(), "err"); }
    }

    private int httpPost(String urlStr, String json) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        OutputStream os = c.getOutputStream();
        os.write(json.getBytes("UTF-8"));
        os.flush();
        os.close();
        int code = c.getResponseCode();
        readStream(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();
        return code;
    }

    // ---- lifecycle ----
    private void stopEngine() {
        running = false;
        Store.setRunning(this, false);
        for (Thread t : workers) t.interrupt();
        workers.clear();
        releaseWakeLock();
        log("[SYSTEM] Engine stopped.", "warn");
        stopForeground(true);
        stopSelf();
    }

    @Override public void onTaskRemoved(Intent rootIntent) { super.onTaskRemoved(rootIntent); }

    @Override
    public void onDestroy() {
        running = false;
        for (Thread t : workers) t.interrupt();
        releaseWakeLock();
        super.onDestroy();
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BotAuto::engine");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Exception ignore) {}
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignore) {}
    }

    private Notification buildNotif() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH, "Bot Engine", NotificationManager.IMPORTANCE_LOW);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, flags);
        Intent stop = new Intent(this, BotService.class).setAction(ACTION_STOP);
        PendingIntent sp = PendingIntent.getService(this, 1, stop, flags);

        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setContentTitle("Bot Automation")
         .setContentText("Engine running...")
         .setSmallIcon(android.R.drawable.stat_notify_sync)
         .setOngoing(true)
         .setContentIntent(pi)
         .addAction(0, "STOP", sp);
        return b.build();
    }

    private void log(String msg, String type) {
        Intent i = new Intent(ACTION_LOG).setPackage(getPackageName());
        i.putExtra(EX_MSG, msg);
        i.putExtra(EX_TYPE, type);
        sendBroadcast(i);
    }

    private String shortTok(String t) {
        int idx = t.indexOf(':');
        return idx > 0 ? t.substring(0, idx) : (t.length() > 6 ? t.substring(0, 6) : t);
    }

    private void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { /* stop */ } }

    private String readBody(HttpURLConnection c) throws Exception {
        InputStream is;
        try { is = c.getInputStream(); } catch (Exception e) { is = c.getErrorStream(); }
        return readStream(is);
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        return bos.toString("UTF-8");
    }
}
