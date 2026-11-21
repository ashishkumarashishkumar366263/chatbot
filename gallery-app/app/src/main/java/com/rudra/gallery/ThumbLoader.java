package com.rudra.gallery;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Async thumbnail loader with memory cache (RecyclerView/Glide ke bina).
public class ThumbLoader {
    private static final LruCache<String, Bitmap> CACHE;
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static {
        int max = (int) (Runtime.getRuntime().maxMemory() / 1024);
        CACHE = new LruCache<String, Bitmap>(max / 8) {
            @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount() / 1024; }
        };
    }

    public static void load(final ImageView iv, final Uri uri, final int size) {
        final String key = uri.toString() + "#" + size;
        iv.setTag(key);
        Bitmap cached = CACHE.get(key);
        if (cached != null) { iv.setImageBitmap(cached); return; }
        iv.setImageBitmap(null);
        EXEC.submit(() -> {
            final Bitmap bmp = decode(iv.getContext(), uri, size);
            if (bmp == null) return;
            CACHE.put(key, bmp);
            MAIN.post(() -> {
                if (key.equals(iv.getTag())) iv.setImageBitmap(bmp);
            });
        });
    }

    public static Bitmap decode(Context ctx, Uri uri, int reqSize) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream is = ctx.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(is, null, o);
            if (is != null) is.close();

            int sample = 1;
            int w = o.outWidth, h = o.outHeight;
            while (w / sample > reqSize * 2 || h / sample > reqSize * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            InputStream is2 = ctx.getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(is2, null, o2);
            if (is2 != null) is2.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}
