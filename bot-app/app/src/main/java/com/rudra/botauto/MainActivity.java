package com.rudra.botauto;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

public class MainActivity extends Activity {

    private String page = "dash";       // dash | autos | settings | editor
    private String theme = "dark";
    private boolean running = false;

    private final Handler main = new Handler(Looper.getMainLooper());
    private BroadcastReceiver logReceiver;

    // palette
    private int cBg, cCard, cHeader, cText, cMuted, cBorder, cAccent, cGreen, cRed, cCyan, cInput;

    // views
    private LinearLayout root, header, bottomNav, logBox, autosList;
    private TextView headerTitle, statusText;
    private ScrollView logScroll, autosScroll, settingsScroll, editorScroll;
    private FrameLayout content;
    private LinearLayout dashView, settingsView, editorView;
    private View dTop, dBot;
    private ImageView nDashI, nAutoI, nSetI;
    private TextView nDashT, nAutoT, nSetT;
    private Button startBtn;

    // editor state
    private String editId = null;
    private EditText eName, eToken, eChat, eTrigText, eTarget, eActText;
    private String selTrigger = "any";
    private String selAction = "reply";
    private LinearLayout trigRow, actRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        theme = Store.prefs(this).getString("theme", "dark");
        running = Store.isRunning(this);
        buildUi();
        applyTheme();
        switchPage("dash");
        registerLogReceiver();

        addLog("[SYSTEM] Bot Automation ready.", "info");
        addLog("[SYSTEM] Automations me rule add karo, phir START dabao.", "info");
    }

    // ================= THEME =================
    private boolean isDark() {
        if ("light".equals(theme)) return false;
        if ("dark".equals(theme)) return true;
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyTheme() {
        boolean d = isDark();
        cAccent = Color.parseColor("#e84e1b");
        cGreen = Color.parseColor("#4caf50");
        cRed = Color.parseColor("#ff3333");
        cCyan = Color.parseColor("#00bcd4");
        if (d) {
            cBg = Color.parseColor("#0a0a0a"); cCard = Color.parseColor("#141414");
            cHeader = Color.parseColor("#111111"); cText = Color.parseColor("#ffffff");
            cMuted = Color.parseColor("#888888"); cBorder = Color.parseColor("#222222");
            cInput = Color.parseColor("#1f1f1f");
        } else {
            cBg = Color.parseColor("#f4f4f4"); cCard = Color.parseColor("#ffffff");
            cHeader = Color.parseColor("#ffffff"); cText = Color.parseColor("#111111");
            cMuted = Color.parseColor("#555555"); cBorder = Color.parseColor("#dddddd");
            cInput = Color.parseColor("#eeeeee");
        }
        root.setBackgroundColor(cBg);
        header.setBackgroundColor(cHeader);
        bottomNav.setBackgroundColor(cHeader);
        dTop.setBackgroundColor(cBorder);
        dBot.setBackgroundColor(cBorder);
        headerTitle.setTextColor(cText);
        if (logBox != null) logBox.setBackgroundColor(Color.parseColor(d ? "#000000" : "#111111"));
        renderDash();
        renderAutos();
        renderSettings();
        updateNav();
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if ("system".equals(theme)) applyTheme();
    }

    // ================= UI BUILD =================
    private void buildUi() {
        root = col();
        root.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(15), dp(15), dp(15), dp(15));
        headerTitle = new TextView(this);
        headerTitle.setText("BOT AUTOMATION");
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        headerTitle.setTypeface(null, Typeface.BOLD);
        headerTitle.setLetterSpacing(0.06f);
        headerTitle.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(headerTitle);

        dTop = new View(this);
        dTop.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));

        content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));

        // Dashboard
        dashView = col();
        dashView.setPadding(dp(15), dp(15), dp(15), dp(15));
        dashView.setLayoutParams(fp());
        buildDash();

        // Automations
        autosScroll = new ScrollView(this); autosScroll.setLayoutParams(fp());
        autosList = col(); autosList.setPadding(dp(15), dp(15), dp(15), dp(15));
        autosScroll.addView(autosList);

        // Settings
        settingsScroll = new ScrollView(this); settingsScroll.setLayoutParams(fp());
        settingsView = col(); settingsView.setPadding(dp(15), dp(15), dp(15), dp(15));
        settingsScroll.addView(settingsView);

        // Editor
        editorScroll = new ScrollView(this); editorScroll.setLayoutParams(fp());
        editorView = col(); editorView.setPadding(dp(15), dp(15), dp(15), dp(15));
        editorScroll.addView(editorView);

        content.addView(dashView);
        content.addView(autosScroll);
        content.addView(settingsScroll);
        content.addView(editorScroll);

        dBot = new View(this);
        dBot.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setPadding(0, dp(10), 0, dp(10));
        nDashI = new ImageView(this); nDashT = new TextView(this);
        nAutoI = new ImageView(this); nAutoT = new TextView(this);
        nSetI = new ImageView(this); nSetT = new TextView(this);
        bottomNav.addView(navItem(nDashI, R.drawable.ic_dash, nDashT, "Dashboard", () -> switchPage("dash")));
        bottomNav.addView(navItem(nAutoI, R.drawable.ic_bolt, nAutoT, "Automations", () -> switchPage("autos")));
        bottomNav.addView(navItem(nSetI, R.drawable.ic_settings, nSetT, "Settings", () -> switchPage("settings")));

        root.addView(header);
        root.addView(dTop);
        root.addView(content);
        root.addView(dBot);
        root.addView(bottomNav);
        setContentView(root);
    }

    private void buildDash() {
        dashView.removeAllViews();
        logScroll = new ScrollView(this);
        logScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.BLACK); g.setCornerRadius(dp(10)); g.setStroke(dp(1), Color.parseColor("#333333"));
        logScroll.setBackground(g);
        logScroll.setPadding(dp(12), dp(12), dp(12), dp(12));
        logBox = col();
        logScroll.addView(logBox);

        statusText = new TextView(this);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statusText.setPadding(dp(2), dp(8), dp(2), dp(8));

        startBtn = new Button(this);
        startBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        startBtn.setTypeface(null, Typeface.BOLD);
        startBtn.setTextColor(Color.WHITE);
        try { startBtn.setStateListAnimator(null); } catch (Exception ignore) {}
        startBtn.setOnClickListener(v -> { if (running) stopEngine(); else startEngine(); });
        LinearLayout.LayoutParams sb = new LinearLayout.LayoutParams(-1, -2);
        startBtn.setLayoutParams(sb);

        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lsp.bottomMargin = dp(10);
        logScroll.setLayoutParams(lsp);

        dashView.addView(logScroll);
        dashView.addView(statusText);
        dashView.addView(startBtn);
    }

    // ================= NAV / PAGES =================
    private void switchPage(String p) {
        if (!"editor".equals(p)) page = p;
        dashView.setVisibility("dash".equals(p) ? View.VISIBLE : View.GONE);
        autosScroll.setVisibility("autos".equals(p) ? View.VISIBLE : View.GONE);
        settingsScroll.setVisibility("settings".equals(p) ? View.VISIBLE : View.GONE);
        editorScroll.setVisibility("editor".equals(p) ? View.VISIBLE : View.GONE);
        bottomNav.setVisibility("editor".equals(p) ? View.GONE : View.VISIBLE);

        if ("dash".equals(p)) headerTitle.setText("BOT AUTOMATION");
        else if ("autos".equals(p)) headerTitle.setText("AUTOMATIONS");
        else if ("settings".equals(p)) headerTitle.setText("SETTINGS");
        else if ("editor".equals(p)) headerTitle.setText(editId == null ? "NEW AUTOMATION" : "EDIT AUTOMATION");
        updateNav();
        if ("dash".equals(p)) syncRunning();
    }

    private void updateNav() {
        nDashI.setColorFilter("dash".equals(page) ? cAccent : cMuted);
        nDashT.setTextColor("dash".equals(page) ? cAccent : cMuted);
        nAutoI.setColorFilter("autos".equals(page) ? cAccent : cMuted);
        nAutoT.setTextColor("autos".equals(page) ? cAccent : cMuted);
        nSetI.setColorFilter("settings".equals(page) ? cAccent : cMuted);
        nSetT.setTextColor("settings".equals(page) ? cAccent : cMuted);
    }

    // ================= DASHBOARD render =================
    private void renderDash() {
        if (statusText == null) return;
        syncRunning();
    }

    private void syncRunning() {
        running = Store.isRunning(this);
        if (statusText != null) {
            statusText.setText(running ? "● ENGINE RUNNING" : "○ ENGINE STOPPED");
            statusText.setTextColor(running ? cGreen : cMuted);
        }
        if (startBtn != null) {
            startBtn.setText(running ? "STOP ENGINE" : "START ENGINE");
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(10));
            g.setColor(running ? cRed : cAccent);
            startBtn.setBackground(g);
            startBtn.setPadding(dp(14), dp(14), dp(14), dp(14));
        }
    }

    private void addLog(String msg, String type) {
        if (logBox == null) return;
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(Typeface.MONOSPACE);
        int col;
        switch (type == null ? "" : type) {
            case "info": col = cCyan; break;
            case "warn": col = Color.parseColor("#ffc107"); break;
            case "err": col = cRed; break;
            case "success": col = cGreen; break;
            default: col = Color.parseColor("#00ff00");
        }
        tv.setTextColor(col);
        logBox.addView(tv);
        if (logScroll != null) logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void startEngine() {
        if (Store.load(this).length() == 0) { toast("Pehle ek automation add karo!"); switchPage("autos"); return; }
        running = true; Store.setRunning(this, true);
        Intent i = new Intent(this, BotService.class).setAction(BotService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{ android.Manifest.permission.POST_NOTIFICATIONS }, 9);
            } catch (Exception ignore) {}
        }
        askBatteryOnce();
        syncRunning();
    }

    private void stopEngine() {
        running = false; Store.setRunning(this, false);
        try { startService(new Intent(this, BotService.class).setAction(BotService.ACTION_STOP)); } catch (Exception ignore) {}
        syncRunning();
    }

    // ================= AUTOMATIONS =================
    private void renderAutos() {
        if (autosList == null) return;
        autosList.removeAllViews();

        Button add = new Button(this);
        add.setText("+  ADD AUTOMATION");
        add.setTextColor(Color.WHITE);
        add.setTypeface(null, Typeface.BOLD);
        try { add.setStateListAnimator(null); } catch (Exception ignore) {}
        GradientDrawable ag = new GradientDrawable(); ag.setColor(cAccent); ag.setCornerRadius(dp(10));
        add.setBackground(ag);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2);
        al.bottomMargin = dp(15);
        add.setLayoutParams(al);
        add.setOnClickListener(v -> openEditor(null));
        autosList.addView(add);

        JSONArray arr = Store.load(this);
        if (arr.length() == 0) {
            TextView t = new TextView(this);
            t.setText("Abhi koi automation nahi. '+ ADD' se banao.");
            t.setTextColor(cMuted); t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2); tl.topMargin = dp(20);
            t.setLayoutParams(tl);
            autosList.addView(t);
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            final JSONObject a = arr.optJSONObject(i);
            if (a != null) autosList.addView(autoCard(a));
        }
    }

    private View autoCard(final JSONObject a) {
        LinearLayout card = col();
        GradientDrawable g = new GradientDrawable();
        g.setColor(cCard); g.setCornerRadius(dp(10)); g.setStroke(dp(1), cBorder);
        card.setBackground(g);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2); cl.bottomMargin = dp(12);
        card.setLayoutParams(cl);

        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(a.optString("name", "Automation"));
        name.setTextColor(cAccent); name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        name.setTypeface(null, Typeface.BOLD);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        CheckBox en = new CheckBox(this);
        en.setChecked(a.optBoolean("enabled", true));
        en.setButtonTintList(android.content.res.ColorStateList.valueOf(cAccent));
        en.setOnClickListener(v -> toggleAuto(a.optString("id"), en.isChecked()));
        top.addView(name); top.addView(en);
        card.addView(top);

        String summary = "Trigger: " + a.optString("trigger", "any")
                + (a.optString("triggerText", "").isEmpty() ? "" : " (" + a.optString("triggerText") + ")")
                + "\nAction: " + a.optString("action", "reply")
                + (a.optString("target", "").isEmpty() ? "" : " -> " + a.optString("target"));
        TextView sum = new TextView(this);
        sum.setText(summary);
        sum.setTextColor(cMuted); sum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sum.setPadding(0, dp(6), 0, dp(10));
        card.addView(sum);

        LinearLayout btns = new LinearLayout(this); btns.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = smallBtn("EDIT", cBorder, cText);
        edit.setOnClickListener(v -> openEditor(a));
        Button del = smallBtn("DELETE", cRed, Color.WHITE);
        del.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Delete '" + a.optString("name") + "'?")
                .setPositiveButton("Delete", (dd, w) -> deleteAuto(a.optString("id")))
                .setNegativeButton("Cancel", null).show());
        btns.addView(edit); btns.addView(del);
        card.addView(btns);
        return card;
    }

    private void toggleAuto(String id, boolean on) {
        JSONArray arr = Store.load(this);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a != null && id.equals(a.optString("id"))) { try { a.put("enabled", on); } catch (Exception ignore) {} }
        }
        Store.save(this, arr);
    }

    private void deleteAuto(String id) {
        JSONArray arr = Store.load(this);
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a != null && !id.equals(a.optString("id"))) out.put(a);
        }
        Store.save(this, out);
        renderAutos();
        toast("Deleted");
    }

    // ================= EDITOR =================
    private void openEditor(JSONObject a) {
        editId = a == null ? null : a.optString("id");
        selTrigger = a == null ? "any" : a.optString("trigger", "any");
        selAction = a == null ? "reply" : a.optString("action", "reply");
        editorView.removeAllViews();

        editorView.addView(label("Name"));
        eName = input("Automation ka naam", a == null ? "" : a.optString("name"), false);
        editorView.addView(eName);

        editorView.addView(label("Bot Token"));
        eToken = input("123456:ABC...", a == null ? "" : a.optString("token"), false);
        editorView.addView(eToken);

        editorView.addView(label("Chat ID filter (optional)"));
        eChat = input("Khali = koi bhi chat. e.g. -100...", a == null ? "" : a.optString("chatId"), false);
        editorView.addView(eChat);

        editorView.addView(label("Trigger"));
        trigRow = selectorRow(new String[]{"any","contains","regex"}, new String[]{"Any","Contains","Regex"},
                selTrigger, v -> { selTrigger = v; });
        editorView.addView(trigRow);
        editorView.addView(label("Trigger text (Contains/Regex ke liye)"));
        eTrigText = input("e.g. OTP  (Any me khali chhodo)", a == null ? "" : a.optString("triggerText"), false);
        editorView.addView(eTrigText);

        editorView.addView(label("Action"));
        actRow = selectorRow(new String[]{"reply","forward","webhook"}, new String[]{"Reply","Forward","Webhook"},
                selAction, v -> { selAction = v; });
        editorView.addView(actRow);

        editorView.addView(label("Target (Forward: chat id | Webhook: URL)"));
        eTarget = input("Forward target chat id / Webhook URL", a == null ? "" : a.optString("target"), false);
        editorView.addView(eTarget);

        editorView.addView(label("Reply text ({text} {chat} use kar sakte ho)"));
        eActText = input("Reply me bhejne wala message", a == null ? "" : a.optString("actionText"), false);
        editorView.addView(eActText);

        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2); rl.topMargin = dp(10);
        row.setLayoutParams(rl);
        Button cancel = smallBtn("CANCEL", cBorder, cText);
        cancel.setOnClickListener(v -> switchPage("autos"));
        Button save = smallBtn("SAVE", cGreen, Color.WHITE);
        save.setOnClickListener(v -> saveEditor());
        LinearLayout.LayoutParams g1 = new LinearLayout.LayoutParams(0, -2, 1f);
        cancel.setLayoutParams(g1);
        LinearLayout.LayoutParams g2 = new LinearLayout.LayoutParams(0, -2, 1f); g2.leftMargin = dp(10);
        save.setLayoutParams(g2);
        row.addView(cancel); row.addView(save);
        editorView.addView(row);

        switchPage("editor");
    }

    private void saveEditor() {
        String name = eName.getText().toString().trim();
        String token = eToken.getText().toString().trim();
        if (name.isEmpty()) { toast("Name likho"); return; }
        if (token.isEmpty()) { toast("Bot token likho"); return; }
        String id = editId != null ? editId : UUID.randomUUID().toString();
        JSONObject o = Store.obj(id, name, token,
                eChat.getText().toString().trim(),
                selTrigger, eTrigText.getText().toString().trim(),
                selAction, eTarget.getText().toString().trim(),
                eActText.getText().toString(), true);

        JSONArray arr = Store.load(this);
        try {
            boolean replaced = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e != null && id.equals(e.optString("id"))) {
                    o.put("enabled", e.optBoolean("enabled", true));
                    arr.put(i, o); replaced = true; break;
                }
            }
            if (!replaced) arr.put(o);
        } catch (Exception ignore) {}
        Store.save(this, arr);
        renderAutos();
        switchPage("autos");
        toast("Saved");
        if (running) toast("Naya bot token hai to Stop/Start karo");
    }

    // ================= SETTINGS =================
    private void renderSettings() {
        if (settingsView == null) return;
        settingsView.removeAllViews();

        LinearLayout grp = group("PREFERENCES");
        LinearLayout tRow = srow("App Theme");
        final String[] opts = {"Dark Mode", "Light Mode", "System Default"};
        final String[] vals = {"dark", "light", "system"};
        TextView tv = new TextView(this);
        tv.setText(opts["light".equals(theme) ? 1 : "system".equals(theme) ? 2 : 0]);
        tv.setTextColor(cText); tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable tg = new GradientDrawable(); tg.setColor(cInput); tg.setCornerRadius(dp(6)); tg.setStroke(dp(1), cBorder);
        tv.setBackground(tg); tv.setPadding(dp(10), dp(8), dp(10), dp(8));
        tv.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Theme")
                .setItems(opts, (d, w) -> { theme = vals[w]; Store.prefs(this).edit().putString("theme", theme).apply(); applyTheme(); switchPage("settings"); }).show());
        tRow.addView(tv);
        grp.addView(tRow);
        settingsView.addView(grp);

        LinearLayout grp2 = group("BACKGROUND");
        TextView info = new TextView(this);
        info.setText("Engine foreground service me chalta hai (notification ke saath) taaki minimize/recents ke baad bhi 24/7 chale. Reboot ke baad khud start ho jata hai.");
        info.setTextColor(cMuted); info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        info.setPadding(0, 0, 0, dp(10));
        grp2.addView(info);
        Button batt = smallBtn("BATTERY: UNRESTRICTED KARO", cBorder, cText);
        batt.setOnClickListener(v -> askBatteryOnce(true));
        grp2.addView(batt);
        settingsView.addView(grp2);

        TextView ver = new TextView(this);
        ver.setText("Bot Automation v1.0 | Developed by Rudra");
        ver.setTextColor(cMuted); ver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        ver.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-1, -2); vl.topMargin = dp(20);
        ver.setLayoutParams(vl);
        settingsView.addView(ver);
    }

    private void askBatteryOnce() { askBatteryOnce(false); }
    private void askBatteryOnce(boolean force) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm == null) return;
                boolean ignoring = pm.isIgnoringBatteryOptimizations(getPackageName());
                if (!ignoring && (force || !Store.prefs(this).getBoolean("battAsk", false))) {
                    Store.prefs(this).edit().putBoolean("battAsk", true).apply();
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } else if (ignoring && force) {
                    toast("Already unrestricted ✓");
                }
            }
        } catch (Exception ignore) {}
    }

    // ================= HELPERS =================
    private void registerLogReceiver() {
        logReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (i == null) return;
                String msg = i.getStringExtra(BotService.EX_MSG);
                String type = i.getStringExtra(BotService.EX_TYPE);
                if (msg != null) addLog(msg, type);
                syncRunning();
            }
        };
        IntentFilter f = new IntentFilter(BotService.ACTION_LOG);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(logReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(logReceiver, f);
    }

    private LinearLayout col() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private FrameLayout.LayoutParams fp() { return new FrameLayout.LayoutParams(-1, -1); }

    private View navItem(ImageView icon, int res, TextView txt, String label, final Runnable click) {
        LinearLayout c = col(); c.setGravity(Gravity.CENTER);
        c.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        icon.setImageResource(res);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
        txt.setText(label); txt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11); txt.setGravity(Gravity.CENTER);
        c.addView(icon); c.addView(txt);
        c.setOnClickListener(v -> click.run());
        return c;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(cMuted); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12); lp.bottomMargin = dp(4);
        t.setLayoutParams(lp);
        return t;
    }

    private EditText input(String hint, String val, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(val);
        e.setTextColor(cText); e.setHintTextColor(cMuted);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setPadding(dp(10), dp(10), dp(10), dp(10));
        e.setInputType(InputType.TYPE_CLASS_TEXT | (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        GradientDrawable g = new GradientDrawable(); g.setColor(cInput); g.setCornerRadius(dp(6)); g.setStroke(dp(1), cBorder);
        e.setBackground(g);
        e.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    private interface Pick { void on(String v); }
    private LinearLayout selectorRow(String[] vals, String[] labels, String current, final Pick pick) {
        final LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        final Button[] btns = new Button[vals.length];
        for (int i = 0; i < vals.length; i++) {
            final String val = vals[i];
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            try { b.setStateListAnimator(null); } catch (Exception ignore) {}
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) lp.leftMargin = dp(8);
            b.setLayoutParams(lp);
            btns[i] = b;
            final int idx = i;
            b.setOnClickListener(v -> { pick.on(val); styleSelector(btns, vals, val); });
            row.addView(b);
        }
        styleSelector(btns, vals, current);
        return row;
    }

    private void styleSelector(Button[] btns, String[] vals, String sel) {
        for (int i = 0; i < btns.length; i++) {
            boolean on = vals[i].equals(sel);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(6));
            g.setColor(on ? cAccent : cInput);
            g.setStroke(dp(1), on ? cAccent : cBorder);
            btns[i].setBackground(g);
            btns[i].setTextColor(on ? Color.WHITE : cMuted);
        }
    }

    private Button smallBtn(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(true); b.setTextColor(fg);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setTypeface(null, Typeface.BOLD);
        try { b.setStateListAnimator(null); } catch (Exception ignore) {}
        GradientDrawable g = new GradientDrawable(); g.setColor(bg); g.setCornerRadius(dp(8));
        b.setBackground(g);
        b.setMinWidth(0); b.setMinimumWidth(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8); lp.topMargin = dp(4);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout group(String title) {
        LinearLayout g = col();
        GradientDrawable bg = new GradientDrawable(); bg.setColor(cCard); bg.setCornerRadius(dp(10)); bg.setStroke(dp(1), cBorder);
        g.setBackground(bg); g.setPadding(dp(15), dp(15), dp(15), dp(15));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(15);
        g.setLayoutParams(lp);
        TextView h = new TextView(this); h.setText(title); h.setTextColor(cMuted);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12); h.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2); hl.bottomMargin = dp(12);
        h.setLayoutParams(hl);
        g.addView(h);
        return g;
    }

    private LinearLayout srow(String labelText) {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, dp(8));
        r.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        TextView l = new TextView(this); l.setText(labelText); l.setTextColor(cText);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(l);
        return r;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    @Override protected void onResume() { super.onResume(); syncRunning(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (logReceiver != null) unregisterReceiver(logReceiver); } catch (Exception ignore) {}
    }
}
