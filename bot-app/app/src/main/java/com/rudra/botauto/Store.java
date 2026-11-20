package com.rudra.botauto;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

// Automations SharedPreferences me JSON array ke roop me save hoti hain.
public class Store {
    public static final String PREFS = "botauto";

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static JSONArray load(Context c) {
        try { return new JSONArray(prefs(c).getString("autos", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static void save(Context c, JSONArray a) {
        prefs(c).edit().putString("autos", a.toString()).apply();
    }

    public static boolean isRunning(Context c) { return prefs(c).getBoolean("running", false); }
    public static void setRunning(Context c, boolean v) { prefs(c).edit().putBoolean("running", v).apply(); }

    public static JSONObject obj(String id, String name, String token, String chatId,
                                 String trigger, String triggerText,
                                 String action, String target, String actionText, boolean enabled) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id); o.put("name", name); o.put("token", token); o.put("chatId", chatId);
            o.put("trigger", trigger); o.put("triggerText", triggerText);
            o.put("action", action); o.put("target", target); o.put("actionText", actionText);
            o.put("enabled", enabled);
        } catch (Exception ignore) {}
        return o;
    }
}
