package com.rudra.gallery;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Media {

    public static class Item {
        public long id;
        public Uri uri;
        public String bucketId = "";
        public String bucketName = "";
    }

    public static class Album {
        public String id;
        public String name;
        public int count;
        public Uri cover;
    }

    // Jo list abhi dikh rahi hai (Viewer/Editor isse padhte hain) — Intent size limit se bachne ke liye
    public static List<Item> CURRENT = new ArrayList<>();

    public static List<Item> queryAll(Context ctx) {
        List<Item> list = new ArrayList<>();
        Uri base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] proj = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        };
        String sort = MediaStore.Images.Media.DATE_MODIFIED + " DESC";
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(base, proj, null, null, sort);
            if (c != null) {
                int iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int iB = c.getColumnIndex(MediaStore.Images.Media.BUCKET_ID);
                int iBn = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                while (c.moveToNext()) {
                    Item it = new Item();
                    it.id = c.getLong(iId);
                    it.uri = ContentUris.withAppendedId(base, it.id);
                    if (iB >= 0) it.bucketId = String.valueOf(c.getLong(iB));
                    if (iBn >= 0) it.bucketName = c.getString(iBn) == null ? "Unknown" : c.getString(iBn);
                    list.add(it);
                }
            }
        } catch (Exception ignore) {
        } finally {
            if (c != null) c.close();
        }
        return list;
    }

    public static List<Album> albums(List<Item> all) {
        Map<String, Album> map = new LinkedHashMap<>();
        for (Item it : all) {
            Album a = map.get(it.bucketId);
            if (a == null) {
                a = new Album();
                a.id = it.bucketId;
                a.name = it.bucketName;
                a.cover = it.uri;
                a.count = 0;
                map.put(it.bucketId, a);
            }
            a.count++;
        }
        return new ArrayList<>(map.values());
    }

    public static List<Item> filter(List<Item> all, String bucketId) {
        if (bucketId == null || bucketId.equals("all")) return new ArrayList<>(all);
        List<Item> out = new ArrayList<>();
        for (Item it : all) if (bucketId.equals(it.bucketId)) out.add(it);
        return out;
    }

    // Edited image ko gallery me save karo; saved Uri return (ya null)
    public static Uri save(Context ctx, Bitmap bmp) {
        String name = "edit_" + System.currentTimeMillis() + ".jpg";
        ContentResolver cr = ctx.getContentResolver();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GalleryEdits");
                v.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return null;
                OutputStream os = cr.openOutputStream(uri);
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, os);
                if (os != null) os.close();
                v.clear();
                v.put(MediaStore.Images.Media.IS_PENDING, 0);
                cr.update(uri, v, null, null);
                return uri;
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GalleryEdits");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name);
                OutputStream os = new java.io.FileOutputStream(f);
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, os);
                os.close();
                // media scan
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DATA, f.getAbsolutePath());
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                return cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
