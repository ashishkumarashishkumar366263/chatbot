package com.rudra.admin.rudra;

public class Config {
    // Central secured Realtime Database (rudra-auto-token)
    public static final String CENTRAL_DB_URL =
            "https://rudra-auto-token-default-rtdb.asia-southeast1.firebasedatabase.app";

    // Firebase Realtime Database SECRET (admin access, rules bypass).
    // Console -> Project Settings -> Service accounts -> Database secrets -> Show.
    // NOTE: yahan apna secret paste hoga (abhi placeholder hai).
    public static final String ADMIN_DB_SECRET = "PASTE_DATABASE_SECRET_HERE";

    public static boolean hasSecret() {
        return ADMIN_DB_SECRET != null
                && !ADMIN_DB_SECRET.isEmpty()
                && !ADMIN_DB_SECRET.equals("PASTE_DATABASE_SECRET_HERE");
    }

    // auth query param for central DB calls
    public static String authParam() {
        return hasSecret() ? "?auth=" + ADMIN_DB_SECRET : "";
    }
}
