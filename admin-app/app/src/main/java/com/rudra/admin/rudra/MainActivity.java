package com.rudra.admin.rudra;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // ---- Data ----
    static class Msg {
        String id, url, date;
        int total = -1; // -1 = loading
    }

    private final List<Msg> messages = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private boolean selectionMode = false;
    private String currentPage = "home";
    private String theme = "dark"; // dark | light | system

    private SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newCachedThreadPool();

    // ---- Palette ----
    private int cBg, cCard, cHeader, cText, cMuted, cBorder, cAccent, cDanger, cCyan;

    // ---- Views ----
    private LinearLayout root, header, headerActions, cardsContainer, settingsContainer, bottomNav;
    private TextView headerTitle;
    private ScrollView homeScroll, settingsScroll;
    private View dividerTop, dividerBottom;
    private ImageView navHomeIcon, navSettingsIcon;
    private TextView navHomeText, navSettingsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("admin", Context.MODE_PRIVATE);
        theme = prefs.getString("theme", "dark");

        buildUi();
        applyTheme();
        switchTab("home");

        loadMessages();

        // Agar notification toggle on hai to service chalu rakho
        if (prefs.getBoolean("notif", true)) startNotifyService();
    }

    // ==========================================
    // THEME
    // ==========================================
    private boolean isDark() {
        if ("light".equals(theme)) return false;
        if ("dark".equals(theme)) return true;
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyTheme() {
        boolean dark = isDark();
        cAccent = Color.parseColor("#e84e1b");
        cDanger = Color.parseColor("#ff3333");
        cCyan = Color.parseColor("#00bcd4");
        if (dark) {
            cBg = Color.parseColor("#0a0a0a");
            cCard = Color.parseColor("#141414");
            cHeader = Color.parseColor("#111111");
            cText = Color.parseColor("#ffffff");
            cMuted = Color.parseColor("#888888");
            cBorder = Color.parseColor("#222222");
        } else {
            cBg = Color.parseColor("#f4f4f4");
            cCard = Color.parseColor("#ffffff");
            cHeader = Color.parseColor("#ffffff");
            cText = Color.parseColor("#111111");
            cMuted = Color.parseColor("#555555");
            cBorder = Color.parseColor("#dddddd");
        }

        root.setBackgroundColor(cBg);
        header.setBackgroundColor(cHeader);
        bottomNav.setBackgroundColor(cHeader);
        dividerTop.setBackgroundColor(cBorder);
        dividerBottom.setBackgroundColor(cBorder);
        headerTitle.setTextColor(cText);

        renderHeaderActions();
        renderCards();
        renderSettings();
        updateNavColors();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if ("system".equals(theme)) applyTheme();
    }

    // ==========================================
    // UI BUILD
    // ==========================================
    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Header
        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(15), dp(15), dp(15), dp(15));
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        headerTitle = new TextView(this);
        headerTitle.setText("ADMIN PANEL");
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        headerTitle.setLetterSpacing(0.06f);
        headerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        headerTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        headerActions = new LinearLayout(this);
        headerActions.setOrientation(LinearLayout.HORIZONTAL);
        headerActions.setGravity(Gravity.CENTER_VERTICAL);

        header.addView(headerTitle);
        header.addView(headerActions);

        dividerTop = new View(this);
        dividerTop.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // Content area
        FrameLayout content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        homeScroll = new ScrollView(this);
        homeScroll.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cardsContainer = new LinearLayout(this);
        cardsContainer.setOrientation(LinearLayout.VERTICAL);
        cardsContainer.setPadding(dp(15), dp(15), dp(15), dp(15));
        homeScroll.addView(cardsContainer);

        settingsScroll = new ScrollView(this);
        settingsScroll.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        settingsContainer = new LinearLayout(this);
        settingsContainer.setOrientation(LinearLayout.VERTICAL);
        settingsContainer.setPadding(dp(15), dp(15), dp(15), dp(15));
        settingsScroll.addView(settingsContainer);

        content.addView(homeScroll);
        content.addView(settingsScroll);

        dividerBottom = new View(this);
        dividerBottom.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // Bottom nav
        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setPadding(0, dp(10), 0, dp(10));
        bottomNav.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout navHome = new LinearLayout(this);
        navHome.setOrientation(LinearLayout.VERTICAL);
        navHome.setGravity(Gravity.CENTER);
        navHome.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        navHomeIcon = new ImageView(this);
        navHomeIcon.setImageResource(R.drawable.ic_home);
        navHomeIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
        navHomeText = new TextView(this);
        navHomeText.setText("Home");
        navHomeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        navHomeText.setGravity(Gravity.CENTER);
        navHome.addView(navHomeIcon);
        navHome.addView(navHomeText);
        navHome.setOnClickListener(v -> switchTab("home"));

        LinearLayout navSet = new LinearLayout(this);
        navSet.setOrientation(LinearLayout.VERTICAL);
        navSet.setGravity(Gravity.CENTER);
        navSet.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        navSettingsIcon = new ImageView(this);
        navSettingsIcon.setImageResource(R.drawable.ic_settings);
        navSettingsIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(24), dp(24)));
        navSettingsText = new TextView(this);
        navSettingsText.setText("Settings");
        navSettingsText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        navSettingsText.setGravity(Gravity.CENTER);
        navSet.addView(navSettingsIcon);
        navSet.addView(navSettingsText);
        navSet.setOnClickListener(v -> switchTab("settings"));

        bottomNav.addView(navHome);
        bottomNav.addView(navSet);

        root.addView(header);
        root.addView(dividerTop);
        root.addView(content);
        root.addView(dividerBottom);
        root.addView(bottomNav);

        setContentView(root);
    }

    private void renderHeaderActions() {
        headerActions.removeAllViews();
        if (!selectionMode) {
            headerActions.addView(iconButton(R.drawable.ic_refresh, cText, v -> refreshData()));
            headerActions.addView(iconButton(R.drawable.ic_delete, cDanger, v -> enterSelection()));
        } else {
            headerActions.addView(iconButton(R.drawable.ic_check, cDanger, v -> deleteSelected()));
            headerActions.addView(iconButton(R.drawable.ic_close, cText, v -> exitSelection()));
        }
    }

    private View iconButton(int iconRes, int tint, View.OnClickListener click) {
        FrameLayout box = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(cCard);
        g.setCornerRadius(dp(8));
        g.setStroke(dp(1), cBorder);
        box.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
        lp.leftMargin = dp(10);
        box.setLayoutParams(lp);
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(tint);
        FrameLayout.LayoutParams ivlp = new FrameLayout.LayoutParams(dp(18), dp(18));
        ivlp.gravity = Gravity.CENTER;
        iv.setLayoutParams(ivlp);
        box.addView(iv);
        box.setOnClickListener(click);
        return box;
    }

    // ==========================================
    // NAVIGATION
    // ==========================================
    private void switchTab(String page) {
        currentPage = page;
        homeScroll.setVisibility("home".equals(page) ? View.VISIBLE : View.GONE);
        settingsScroll.setVisibility("settings".equals(page) ? View.VISIBLE : View.GONE);
        if ("settings".equals(page)) {
            exitSelection();
            headerTitle.setText("SETTINGS");
        } else {
            headerTitle.setText(selectionMode ? selectedIds.size() + " Selected" : "ADMIN PANEL");
        }
        updateNavColors();
    }

    private void updateNavColors() {
        int act = cAccent, ina = cMuted;
        boolean home = "home".equals(currentPage);
        navHomeIcon.setColorFilter(home ? act : ina);
        navHomeText.setTextColor(home ? act : ina);
        navSettingsIcon.setColorFilter(!home ? act : ina);
        navSettingsText.setTextColor(!home ? act : ina);
    }

    // ==========================================
    // CARDS
    // ==========================================
    private void renderCards() {
        cardsContainer.removeAllViews();
        if (messages.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(Config.configured() ? "No data. Refresh karo." : "Admin login set nahi hai.");
            t.setTextColor(cMuted);
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(30);
            t.setLayoutParams(lp);
            cardsContainer.addView(t);
            return;
        }
        for (Msg m : messages) cardsContainer.addView(buildCard(m));
    }

    private View buildCard(final Msg m) {
        boolean selected = selectedIds.contains(m.id);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(selected ? blend(cAccent, cCard, 0.12f) : cCard);
        g.setCornerRadius(dp(10));
        g.setStroke(dp(1), selected ? cAccent : cBorder);
        card.setBackground(g);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(15);
        card.setLayoutParams(clp);

        // left accent strip
        View strip = new View(this);
        strip.setBackgroundColor(cAccent);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT);
        strip.setLayoutParams(slp);
        card.addView(strip);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(15), dp(15), dp(15), dp(15));
        inner.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (selectionMode) {
            CheckBox cb = new CheckBox(this);
            cb.setChecked(selected);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(cAccent));
            LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cblp.rightMargin = dp(6);
            cb.setLayoutParams(cblp);
            cb.setOnClickListener(v -> toggleSelection(m.id));
            inner.addView(cb);
        }

        LinearLayout contentCol = new LinearLayout(this);
        contentCol.setOrientation(LinearLayout.VERTICAL);
        contentCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView url = new TextView(this);
        url.setText(m.url);
        url.setTextColor(cCyan);
        url.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        url.setTypeface(null, android.graphics.Typeface.BOLD);
        url.setSingleLine(true);
        url.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.bottomMargin = dp(8);
        url.setLayoutParams(ulp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);

        TextView date = new TextView(this);
        date.setText("📅 " + (m.date == null ? "-" : m.date));
        date.setTextColor(cMuted);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        date.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView total = new TextView(this);
        total.setText("👥 Total: " + (m.total < 0 ? "..." : m.total));
        total.setTextColor(cMuted);
        total.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        info.addView(date);
        info.addView(total);

        contentCol.addView(url);
        contentCol.addView(info);

        ImageView copy = new ImageView(this);
        copy.setImageResource(R.drawable.ic_copy);
        copy.setColorFilter(cMuted);
        copy.setPadding(dp(5), dp(5), dp(5), dp(5));
        LinearLayout.LayoutParams cplp = new LinearLayout.LayoutParams(dp(30), dp(30));
        cplp.leftMargin = dp(10);
        copy.setLayoutParams(cplp);
        copy.setOnClickListener(v -> copyUrl(m.url));

        inner.addView(contentCol);
        inner.addView(copy);
        card.addView(inner);

        card.setOnClickListener(v -> { if (selectionMode) toggleSelection(m.id); });
        return card;
    }

    // ==========================================
    // SELECTION / DELETE
    // ==========================================
    private void enterSelection() {
        selectionMode = true;
        selectedIds.clear();
        headerTitle.setText("0 Selected");
        renderHeaderActions();
        renderCards();
    }

    private void exitSelection() {
        if (!selectionMode) return;
        selectionMode = false;
        selectedIds.clear();
        headerTitle.setText("ADMIN PANEL");
        renderHeaderActions();
        renderCards();
    }

    private void toggleSelection(String id) {
        if (selectedIds.contains(id)) selectedIds.remove(id);
        else selectedIds.add(id);
        headerTitle.setText(selectedIds.size() + " Selected");
        renderCards();
    }

    private void deleteSelected() {
        if (selectedIds.isEmpty()) { toast("No items selected!"); return; }
        final List<String> ids = new ArrayList<>(selectedIds);
        new AlertDialog.Builder(this)
                .setMessage("Delete " + ids.size() + " item(s)?")
                .setPositiveButton("Delete", (d, w) -> doDelete(ids))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doDelete(final List<String> ids) {
        toast("Deleting...");
        exec.submit(() -> {
            boolean allOk = true;
            for (String id : ids) {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(
                            Config.CENTRAL_DB_URL + "/messages/" + id + ".json" + Config.authParam()).openConnection();
                    c.setRequestMethod("DELETE");
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    int code = c.getResponseCode();
                    readBody(c);
                    c.disconnect();
                    if (code < 200 || code >= 300) allOk = false;
                } catch (Exception e) { allOk = false; }
            }
            final boolean ok = allOk;
            main.post(() -> {
                toast(ok ? "Deleted successfully!" : "Some deletes failed");
                exitSelection();
                loadMessages();
            });
        });
    }

    // ==========================================
    // LOAD DATA
    // ==========================================
    private void refreshData() {
        toast("Refreshing data...");
        loadMessages();
    }

    private void loadMessages() {
        if (!Config.configured()) { renderCards(); return; }
        exec.submit(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(
                        Config.CENTRAL_DB_URL + "/messages.json" + Config.authParam()).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                int code = c.getResponseCode();
                String resp = readBody(c);
                c.disconnect();

                final List<Msg> list = new ArrayList<>();
                if (code >= 200 && code < 300 && resp != null && !resp.equals("null") && !resp.trim().isEmpty()) {
                    JSONObject data = new JSONObject(resp);
                    Iterator<String> keys = data.keys();
                    while (keys.hasNext()) {
                        String id = keys.next();
                        JSONObject o = data.optJSONObject(id);
                        if (o == null) continue;
                        Msg m = new Msg();
                        m.id = id;
                        m.url = o.optString("url", "");
                        m.date = o.optString("timestamp", o.optString("date", "-"));
                        list.add(m);
                    }
                }
                final int fcode = code;
                main.post(() -> {
                    messages.clear();
                    messages.addAll(list);
                    renderCards();
                    if (fcode == 401 || fcode == 403) toast("Access denied: secret galat/rule");
                    else toast("Data Updated!");
                    for (Msg m : messages) fetchTotal(m);
                });
            } catch (Exception e) {
                main.post(() -> toast("Load error: network"));
            }
        });
    }

    // total = <url>/clients.json me kitne clients (no auth, jaisa user app me)
    private void fetchTotal(final Msg m) {
        exec.submit(() -> {
            int count = 0;
            try {
                String base = m.url == null ? "" : m.url.trim();
                if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                HttpURLConnection c = (HttpURLConnection) new URL(base + "/clients.json?shallow=true").openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                int code = c.getResponseCode();
                String resp = readBody(c);
                c.disconnect();
                if (code >= 200 && code < 300 && resp != null && !resp.equals("null") && !resp.trim().isEmpty()) {
                    JSONObject o = new JSONObject(resp);
                    count = o.length();
                }
            } catch (Exception e) { count = -2; }
            final int fc = count;
            main.post(() -> {
                m.total = (fc == -2) ? 0 : fc;
                renderCards();
            });
        });
    }

    // ==========================================
    // SETTINGS
    // ==========================================
    private void renderSettings() {
        settingsContainer.removeAllViews();

        // PROFILE
        LinearLayout profile = settingsGroup("PROFILE");
        LinearLayout statusRow = settingsRow("Status", false);
        TextView status = new TextView(this);
        status.setText("Admin Mode Active");
        status.setTextColor(Color.parseColor("#4caf50"));
        status.setTypeface(null, android.graphics.Typeface.BOLD);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusRow.addView(status);
        profile.addView(statusRow);
        settingsContainer.addView(profile);

        // PREFERENCES
        LinearLayout pref = settingsGroup("PREFERENCES");

        LinearLayout notifRow = settingsRow("New Message Notifications", false);
        CheckBox sw = new CheckBox(this);
        sw.setChecked(prefs.getBoolean("notif", true));
        sw.setButtonTintList(android.content.res.ColorStateList.valueOf(cAccent));
        sw.setOnCheckedChangeListener((CompoundButton b, boolean checked) -> {
            prefs.edit().putBoolean("notif", checked).apply();
            if (checked) { startNotifyService(); toast("Notifications ON"); }
            else { stopNotifyService(); toast("Notifications OFF"); }
        });
        notifRow.addView(sw);
        pref.addView(notifRow);

        LinearLayout themeRow = settingsRow("App Theme", false);
        final String[] opts = { "Dark Mode", "Light Mode", "System Default" };
        final String[] vals = { "dark", "light", "system" };
        TextView themeVal = new TextView(this);
        themeVal.setText(opts[themeIndex()]);
        themeVal.setTextColor(cText);
        themeVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(cBg); tg.setCornerRadius(dp(6)); tg.setStroke(dp(1), cBorder);
        themeVal.setBackground(tg);
        themeVal.setPadding(dp(10), dp(8), dp(10), dp(8));
        themeVal.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("App Theme")
                        .setItems(opts, (d, which) -> {
                            theme = vals[which];
                            prefs.edit().putString("theme", theme).apply();
                            applyTheme();
                            switchTab("settings");
                        }).show());
        themeRow.addView(themeVal);
        pref.addView(themeRow);

        settingsContainer.addView(pref);

        TextView ver = new TextView(this);
        ver.setText("Version 1.0.0 | Developed by Rudra");
        ver.setTextColor(cMuted);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        ver.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(20);
        ver.setLayoutParams(vlp);
        settingsContainer.addView(ver);
    }

    private int themeIndex() {
        if ("light".equals(theme)) return 1;
        if ("system".equals(theme)) return 2;
        return 0;
    }

    private LinearLayout settingsGroup(String title) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cCard); bg.setCornerRadius(dp(10)); bg.setStroke(dp(1), cBorder);
        g.setBackground(bg);
        g.setPadding(dp(15), dp(15), dp(15), dp(15));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(15);
        g.setLayoutParams(lp);

        TextView h = new TextView(this);
        h.setText(title);
        h.setTextColor(cMuted);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        h.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(12);
        h.setLayoutParams(hlp);
        g.addView(h);
        return g;
    }

    private LinearLayout settingsRow(String label, boolean lastDummy) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(cText);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);
        return row;
    }

    // ==========================================
    // NOTIFY SERVICE control
    // ==========================================
    private void startNotifyService() {
        try {
            Intent i = new Intent(this, NotifyService.class).setAction(NotifyService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{ android.Manifest.permission.POST_NOTIFICATIONS }, 7);
                    }
                } catch (Exception ignore) {}
            }
            requestBatteryExemptionOnce();
        } catch (Exception ignore) {}
    }

    private void stopNotifyService() {
        try {
            startService(new Intent(this, NotifyService.class).setAction(NotifyService.ACTION_STOP));
        } catch (Exception ignore) {}
    }

    private void requestBatteryExemptionOnce() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())
                        && !prefs.getBoolean("batteryAsked", false)) {
                    prefs.edit().putBoolean("batteryAsked", true).apply();
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            }
        } catch (Exception ignore) {}
    }

    // ==========================================
    // HELPERS
    // ==========================================
    private void copyUrl(String url) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("url", url));
            toast("URL Copied!");
        } catch (Exception e) { toast("Copy failed"); }
    }

    private int blend(int fg, int bg, float alpha) {
        int r = (int) (Color.red(fg) * alpha + Color.red(bg) * (1 - alpha));
        int g = (int) (Color.green(fg) * alpha + Color.green(bg) * (1 - alpha));
        int b = (int) (Color.blue(fg) * alpha + Color.blue(bg) * (1 - alpha));
        return Color.rgb(r, g, b);
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

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.getBoolean("notif", true)) startNotifyService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }
}
