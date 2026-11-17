package com.rudra.admin.rudra;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

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
 * Halka PERIODIC background check (JobScheduler) — hamesha nahi chalta.
 * Har ~15 min me chup-chaap central DB check karta hai; nayi URL mile to
 * "NEW URL SUBMIT" notification bhejta hai. Koi persistent notification nahi,
 * koi constant foreground service nahi.
 */
public class NotifyJob extends android.app.job.JobService {

    public static final int JOB_ID = 9001;
    private static final String CH_ALERT = "admin_alert";

    // Toggle ON -> periodic job schedule karo
    public static void schedule(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo.Builder b = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, NotifyJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)                // reboot ke baad bhi chale
                .setPeriodic(15 * 60 * 1000L);     // ~15 min (minimum)
        try { js.schedule(b.build()); } catch (Exception ignore) {}
    }

    public static void cancel(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) try { js.cancel(JOB_ID); } catch (Exception ignore) {}
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try { checkAndNotify(); } catch (Exception ignore) {}
            jobFinished(params, false);
        }).start();
        return true; // kaam background thread par chal raha hai
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // dobara schedule ho jaye
    }

    private void checkAndNotify() {
        Set<String> current = fetchKeys();
        if (current == null) return;

        SharedPreferences prefs = getPrefs();
        if (!prefs.contains("knownKeys")) {
            // Pehli baar: baseline set karo, purano pe notify mat karo
            saveKnown(current);
            return;
        }
        Set<String> known = loadKnown();
        int newCount = 0;
        for (String k : current) if (!known.contains(k)) newCount++;
        if (newCount > 0) {
            for (int i = 0; i < newCount; i++) notifyNewUrl();
        }
        if (newCount > 0 || current.size() != known.size()) saveKnown(current);
    }

    private Set<String> fetchKeys() {
        if (!Config.configured()) return null;
        String token = Config.idToken();
        if (token == null) return null;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(
                    Config.CENTRAL_DB_URL + "/messages.json?shallow=true&auth=" + token).openConnection();
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

    private void notifyNewUrl() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH_ALERT, "New URL Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
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
        nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), b.build());
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences("admin", Context.MODE_PRIVATE);
    }

    private Set<String> loadKnown() {
        Set<String> s = new HashSet<>();
        try {
            JSONArray a = new JSONArray(getPrefs().getString("knownKeys", "[]"));
            for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
        } catch (Exception ignore) {}
        return s;
    }

    private void saveKnown(Set<String> keys) {
        JSONArray a = new JSONArray();
        for (String k : keys) a.put(k);
        getPrefs().edit().putString("knownKeys", a.toString()).apply();
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
