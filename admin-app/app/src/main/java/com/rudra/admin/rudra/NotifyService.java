package com.rudra.admin.rudra;

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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Foreground service: central DB (messages) ko poll karta hai aur nayi URL aane par
 * "NEW URL SUBMIT" notification bhejta hai. App minimize/recents se hat jane par bhi chalta hai.
 */
public class NotifyService extends Service {

    public static final String ACTION_START = "com.rudra.admin.rudra.START";
    public static final String ACTION_STOP  = "com.rudra.admin.rudra.STOP";

    private static final String CH_ONGOING = "admin_ongoing";
    private static final String CH_ALERT = "admin_alert";
    private static final int NOTIF_ID = 2001;

    private volatile boolean running = false;
    private Thread worker;
    private PowerManager.WakeLock wakeLock;
    private int alertId = 3000;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            running = false;
            if (worker != null) worker.interrupt();
            releaseWakeLock();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIF_ID, buildOngoing());
        if (!running) {
            running = true;
            acquireWakeLock();
            worker = new Thread(this::loop, "AdminNotify");
            worker.start();
        }
        return START_STICKY;
    }

    private void loop() {
        // Pehli baar: existing keys ko "known" maan lo (purano pe notify na karo)
        boolean firstRun = !haveKnown();
        while (running) {
            try {
                Set<String> current = fetchKeys();
                if (current != null) {
                    Set<String> known = loadKnown();
                    if (firstRun) {
                        saveKnown(current);
                        firstRun = false;
                    } else {
                        int newCount = 0;
                        for (String k : current) if (!known.contains(k)) newCount++;
                        if (newCount > 0) {
                            for (int i = 0; i < newCount; i++) notifyNewUrl();
                            saveKnown(current);
                        } else if (current.size() != known.size()) {
                            // kuch delete hua -> known update
                            saveKnown(current);
                        }
                    }
                }
            } catch (Exception ignore) {}

            try { Thread.sleep(20000); } catch (InterruptedException e) { break; }
        }
    }

    private Set<String> fetchKeys() {
        if (!Config.hasSecret()) return null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(
                    Config.CENTRAL_DB_URL + "/messages.json?shallow=true&auth=" + Config.ADMIN_DB_SECRET)
                    .openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            int code = c.getResponseCode();
            String resp = readBody(c);
            c.disconnect();
            if (code < 200 || code >= 300) return null;
            Set<String> keys = new HashSet<>();
            if (resp != null && !resp.equals("null") && !resp.trim().isEmpty()) {
                JSONObject o = new JSONObject(resp);
                Iterator<String> it = o.keys();
                while (it.hasNext()) keys.add(it.next());
            }
            return keys;
        } catch (Exception e) { return null; }
    }

    private boolean haveKnown() {
        return getPrefs().contains("knownKeys");
    }

    private Set<String> loadKnown() {
        Set<String> s = new HashSet<>();
        try {
            String raw = getPrefs().getString("knownKeys", "[]");
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
        } catch (Exception ignore) {}
        return s;
    }

    private void saveKnown(Set<String> keys) {
        JSONArray a = new JSONArray();
        for (String k : keys) a.put(k);
        getPrefs().edit().putString("knownKeys", a.toString()).apply();
    }

    private void notifyNewUrl() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH_ALERT, "New URL Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, flags);

        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CH_ALERT) : new Notification.Builder(this);
        b.setContentTitle("Admin Panel")
         .setContentText("NEW URL SUBMIT")
         .setSmallIcon(android.R.drawable.stat_notify_chat)
         .setAutoCancel(true)
         .setContentIntent(pi);
        if (nm != null) nm.notify(alertId++, b.build());
    }

    private Notification buildOngoing() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH_ONGOING, "Admin Service",
                    NotificationManager.IMPORTANCE_LOW);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, flags);

        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(this, CH_ONGOING) : new Notification.Builder(this);
        b.setContentTitle("Admin Panel")
         .setContentText("Watching for new URLs...")
         .setSmallIcon(android.R.drawable.stat_notify_sync)
         .setOngoing(true)
         .setContentIntent(pi);
        return b.build();
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences("admin", Context.MODE_PRIVATE);
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Admin::notify");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Exception ignore) {}
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignore) {}
    }

    @Override public void onTaskRemoved(Intent rootIntent) { super.onTaskRemoved(rootIntent); }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) worker.interrupt();
        releaseWakeLock();
        super.onDestroy();
    }

    private String readBody(HttpURLConnection c) throws Exception {
        InputStream is;
        try { is = c.getInputStream(); } catch (Exception e) { is = c.getErrorStream(); }
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        return bos.toString("UTF-8");
    }
}
