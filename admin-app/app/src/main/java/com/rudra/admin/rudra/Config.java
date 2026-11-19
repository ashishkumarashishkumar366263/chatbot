package com.rudra.admin.rudra;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Config {
    // Sensitive strings AES-encrypted (decompile se plaintext na nikle)
    public static final String CENTRAL_DB_URL = Sec.d("q7JnIZlSt9F7g9dALO6R8NUdsWIJ8XZQu5yIBb/v/ZG2fn0HqQ3kGAH1WW3+INQua4mAQja2AOpF2mQI7bP8h4wpknovM/vm9zhH1ACYSCI=");
    public static final String API_KEY = Sec.d("4FourbbV3yPWZxslufWM9CstQTLN5VenGR/5VoUpSrl8qbmik3inAP6r0CQCuomJ");
    public static final String ADMIN_EMAIL = Sec.d("cdr/bXJj1BUzo3HlgRN5O5GDle488tJwkXkh1YdCevU=");
    public static final String ADMIN_PASSWORD = Sec.d("6jdgzQ8nQtbQAztSqL8MEA==");

    private static String cachedToken = null;
    private static long cachedAt = 0;

    public static boolean configured() {
        return ADMIN_EMAIL != null && !ADMIN_EMAIL.isEmpty() && !ADMIN_EMAIL.startsWith("PASTE")
                && ADMIN_PASSWORD != null && !ADMIN_PASSWORD.isEmpty() && !ADMIN_PASSWORD.startsWith("PASTE");
    }

    // Valid idToken (sign-in / refresh as needed). Background thread par call karo.
    public static synchronized String idToken() {
        if (!configured()) return null;
        long now = System.currentTimeMillis();
        if (cachedToken != null && (now - cachedAt) < 50 * 60 * 1000L) return cachedToken;
        String t = signIn();
        if (t != null) { cachedToken = t; cachedAt = now; }
        return t;
    }

    // "?auth=<idToken>" ya "" (agar token na mile). Background thread par call karo.
    public static String authParam() {
        String t = idToken();
        return t != null ? "?auth=" + t : "";
    }

    private static String signIn() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + API_KEY)
                    .openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            JSONObject body = new JSONObject();
            body.put("email", ADMIN_EMAIL);
            body.put("password", ADMIN_PASSWORD);
            body.put("returnSecureToken", true);
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.flush();
            os.close();
            String resp = readBody(c);
            c.disconnect();
            JSONObject o = new JSONObject(resp);
            String tok = o.optString("idToken", "");
            return tok.isEmpty() ? null : tok;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readBody(HttpURLConnection c) throws Exception {
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
