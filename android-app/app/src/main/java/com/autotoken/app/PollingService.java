package com.autotoken.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Foreground service that runs the Telegram polling + Firebase push loop.
 * Keeps working when the app is minimized or swiped out of recents.
 * All logic is a 1:1 port of the Telegram/Firebase part of script.js.
 */
public class PollingService extends Service {

    public static final String ACTION_START = "com.autotoken.app.START";
    public static final String ACTION_STOP  = "com.autotoken.app.STOP";

    // Broadcast back to the UI (terminal logs + running state)
    public static final String ACTION_EVENT = "com.autotoken.app.EVENT";
    public static final String EX_KIND = "kind";     // "log" | "state"
    public static final String EX_MSG = "msg";
    public static final String EX_TYPE = "type";
    public static final String EX_RUNNING = "running";

    private static final String CHANNEL_ID = "autotoken_poll";
    private static final int NOTIF_ID = 1001;

    private volatile boolean running = false;
    private Thread worker;
    private PowerManager.WakeLock wakeLock;

    // Config passed from the Activity
    private String token = "";
    private String chatId = "";
    private String firebaseUrl = "";
    private String deviceId = "";
    private String simIndex = "0";

    private long lastUpdateId = 0;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopEverything();
            return START_NOT_STICKY;
        }

        // START
        if (intent != null) {
            token = orEmpty(intent.getStringExtra("token"));
            chatId = orEmpty(intent.getStringExtra("chatId"));
            firebaseUrl = orEmpty(intent.getStringExtra("firebaseUrl"));
            deviceId = orEmpty(intent.getStringExtra("deviceId"));
            simIndex = orEmpty(intent.getStringExtra("simIndex"));
            if (TextUtils.isEmpty(simIndex)) simIndex = "0";
        }

        startForeground(NOTIF_ID, buildNotification());
        setRunningFlag(true);

        if (!running) {
            running = true;
            acquireWakeLock();
            worker = new Thread(this::pollLoop, "AutoTokenPoll");
            worker.start();
        }
        // Keep the service alive and restart it if the system kills it.
        return START_STICKY;
    }

    // ==========================================
    // POLLING LOOP  (port of startTelegramPolling)
    // ==========================================
    private void pollLoop() {
        while (running) {
            try {
                String url = "https://api.telegram.org/bot" + token +
                        "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=15";
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                String resp = readBody(c);
                c.disconnect();

                JSONObject data = new JSONObject(resp);

                if (data.optBoolean("ok", false)) {
                    JSONArray result = data.optJSONArray("result");
                    if (result != null && result.length() > 0) {
                        for (int i = 0; i < result.length(); i++) {
                            JSONObject update = result.getJSONObject(i);
                            lastUpdateId = update.optLong("update_id", lastUpdateId);

                            JSONObject post = update.optJSONObject("channel_post");
                            if (post == null) post = update.optJSONObject("message");
                            if (post == null) continue;

                            JSONObject chat = post.optJSONObject("chat");
                            String postChatId = chat != null ? String.valueOf(chat.opt("id")) : "";
                            if (!postChatId.equals(String.valueOf(chatId))) continue;

                            String text = post.optString("text", "");
                            if (TextUtils.isEmpty(text)) text = post.optString("caption", "");
                            if (TextUtils.isEmpty(text)) continue;

                            String[] parsed = extractData(text);
                            String target = parsed[0];
                            String content = parsed[1];

                            if (target != null && content != null) {
                                log("[TELEGRAM] New Msg Detected:", "success");
                                log("To: " + target, "info");
                                log("Body: " + content, "info");
                                pushToFirebase(target, content);
                            } else {
                                log("[TELEGRAM] Ignored (Format not matched)", "warn");
                            }
                        }
                    }
                } else {
                    String desc = data.optString("description", "Unknown Error");
                    log("[TELEGRAM ERR] " + desc, "err");
                    stopEverything();
                    return;
                }
            } catch (Exception e) {
                log("[NETWORK ERR] Retrying...", "warn");
            }

            // 1 second gap between polls (same as script.js)
            try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
        }
    }

    // ==========================================
    // EXTRACTION  (same regex as script.js)
    // ==========================================
    private String[] extractData(String rawText) {
        String target = null;
        String content = null;
        Matcher toM = Pattern.compile("To\\s*(?:\\(Tap to copy\\))?\\s*:\\s*([+\\d]+)",
                Pattern.CASE_INSENSITIVE).matcher(rawText);
        if (toM.find()) target = toM.group(1).trim();

        Matcher bodyM = Pattern.compile("Body\\s*(?:\\(Tap to copy\\))?\\s*:\\s*([\\s\\S]+)",
                Pattern.CASE_INSENSITIVE).matcher(rawText);
        if (bodyM.find()) content = bodyM.group(1).trim();

        return new String[]{ target, content };
    }

    // ==========================================
    // FIREBASE PUSH  (same payload/format as script.js)
    // ==========================================
    private void pushToFirebase(String target, String body) {
        if (TextUtils.isEmpty(firebaseUrl)) {
            log("[FIREBASE ERR] Default Firebase URL not set!", "err");
            return;
        }
        String url = firebaseUrl + "/clients/" + deviceId + "/webhookEvent/sendSms.json";

        JSONObject payload = new JSONObject();
        try {
            payload.put("from", simIndex);   // "0" for SIM 1, "1" for SIM 2
            payload.put("to", target);
            payload.put("message", body);
            payload.put("isSended", false);
        } catch (Exception ignore) {}

        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("PUT");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            OutputStream os = c.getOutputStream();
            os.write(payload.toString().getBytes("UTF-8"));
            os.flush();
            os.close();
            int code = c.getResponseCode();
            try { InputStream is = c.getInputStream(); byte[] b = new byte[2048]; while (is.read(b) != -1) {} is.close(); } catch (Exception ignore) {}
            c.disconnect();

            if (code >= 200 && code < 300) {
                log("[FIREBASE] ✅ SMS Queued successfully!", "success");
            } else {
                log("[FIREBASE ERR] ❌ Failed (HTTP " + code + ")", "err");
            }
        } catch (Exception e) {
            log("[FIREBASE ERR] ❌ Network Problem", "err");
        }
    }

    // ==========================================
    // LIFECYCLE / HELPERS
    // ==========================================
    private void stopEverything() {
        running = false;
        if (worker != null) worker.interrupt();
        releaseWakeLock();
        setRunningFlag(false);
        sendState(false);
        log("[SYSTEM] Bot Stopped.", "warn");
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // App ko recents se hatane par bhi service chalta rahe:
        // kuch bhi stop nahi karte. Foreground service background me live rahega.
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        releaseWakeLock();
        super.onDestroy();
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AutoToken::poll");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Exception ignore) {}
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignore) {}
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "AUTO TOKEN Service",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Background polling");
            if (nm != null) nm.createNotificationChannel(ch);
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent, piFlags);

        Intent stopIntent = new Intent(this, PollingService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent, piFlags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("AUTO TOKEN")
         .setContentText("Running in background...")
         .setSmallIcon(android.R.drawable.stat_notify_sync)
         .setOngoing(true)
         .setContentIntent(openPi)
         .addAction(0, "STOP", stopPi);

        return b.build();
    }

    private void setRunningFlag(boolean v) {
        try {
            SharedPreferences prefs = getSharedPreferences("autotoken", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("isRunning", v).apply();
        } catch (Exception ignore) {}
    }

    private void log(String msg, String type) {
        Intent i = new Intent(ACTION_EVENT).setPackage(getPackageName());
        i.putExtra(EX_KIND, "log");
        i.putExtra(EX_MSG, msg);
        i.putExtra(EX_TYPE, type);
        sendBroadcast(i);
    }

    private void sendState(boolean isRunning) {
        Intent i = new Intent(ACTION_EVENT).setPackage(getPackageName());
        i.putExtra(EX_KIND, "state");
        i.putExtra(EX_RUNNING, isRunning);
        sendBroadcast(i);
    }

    private String readBody(HttpURLConnection c) throws Exception {
        InputStream is;
        try { is = c.getInputStream(); }
        catch (Exception e) { is = c.getErrorStream(); }
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        return bos.toString("UTF-8");
    }

    private String orEmpty(String s) { return s == null ? "" : s; }
}
