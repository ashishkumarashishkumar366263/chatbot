package com.rudra.admin.rudra;

import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

// Sensitive strings AES-encrypted rakhi gayi hain; runtime pe decrypt hoti hain.
final class Sec {
    private static byte[] key() {
        byte[] x = {
            (byte)0x6d,(byte)0x0b,(byte)0xc0,(byte)0x76,(byte)0x24,(byte)0x4b,(byte)0xee,(byte)0x39,
            (byte)0x82,(byte)0x15,(byte)0x7a,(byte)0xcf,(byte)0xbc,(byte)0x60,(byte)0xd6,(byte)0x07
        };
        byte[] k = new byte[x.length];
        for (int i = 0; i < x.length; i++) k[i] = (byte) (x[i] ^ 0x5A);
        return k;
    }

    static String d(String b64) {
        try {
            byte[] ct = Base64.decode(b64, Base64.DEFAULT);
            Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"));
            return new String(c.doFinal(ct), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
