package com.rudra.gallery;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditorActivity extends Activity {

    private Bitmap base, preview;
    private int filterIdx = 0;
    private int bright = 0, contrast = 0, sat = 0, warmth = 0;

    private ImageView img;
    private CropOverlay crop;
    private LinearLayout tabsRow, panel;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private String tool = "filters";

    private final String[] FILTERS = {"Original","Mono","Sepia","Vivid","Warm","Cool","Vintage","B&W"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        final String u = getIntent().getStringExtra("uri");
        if (u == null) { finish(); return; }
        final Uri uri = Uri.parse(u);
        exec.submit(() -> {
            int req = Math.max(getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels) * 2;
            final Bitmap loaded = ThumbLoader.decode(this, uri, Math.min(req, 2200));
            main.post(() -> {
                if (loaded == null) { toast("Image load nahi hui"); finish(); return; }
                base = loaded;
                crop.setBitmapSize(base.getWidth(), base.getHeight());
                renderPreview();
                selectTool("filters");
            });
        });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));

        // top bar
        LinearLayout top = new LinearLayout(this);
        top.setBackgroundColor(Color.parseColor("#111111"));
        top.setPadding(dp(10), dp(12), dp(10), dp(12));
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = textBtn("‹ Cancel", Color.WHITE);
        back.setOnClickListener(v -> finish());
        TextView t = new TextView(this);
        t.setText("Editor"); t.setTextColor(Color.WHITE); t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16); t.setTypeface(null, Typeface.BOLD);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        Button save = textBtn("SAVE", Color.parseColor("#e84e1b"));
        save.setTypeface(null, Typeface.BOLD);
        save.setOnClickListener(v -> save());
        top.addView(back); top.addView(t); top.addView(save);
        root.addView(top);

        // image area
        FrameLayout area = new FrameLayout(this);
        area.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        img.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        area.addView(img);
        crop = new CropOverlay(this);
        crop.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        crop.setVisibility(View.GONE);
        area.addView(crop);
        root.addView(area);

        // dynamic panel
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.parseColor("#0f0f0f"));
        panel.setPadding(dp(10), dp(10), dp(10), dp(6));
        root.addView(panel);

        // tabs
        tabsRow = new LinearLayout(this);
        tabsRow.setBackgroundColor(Color.parseColor("#111111"));
        tabsRow.setPadding(0, dp(6), 0, dp(6));
        tabsRow.addView(tab("Filters", "filters"));
        tabsRow.addView(tab("Adjust", "adjust"));
        tabsRow.addView(tab("Crop", "crop"));
        tabsRow.addView(tab("Rotate", "rotate"));
        root.addView(tabsRow);

        setContentView(root);
    }

    private View tab(String label, final String key) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER);
        c.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView t = new TextView(this);
        t.setText(label); t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTag(key);
        c.addView(t);
        c.setOnClickListener(v -> selectTool(key));
        return c;
    }

    private void selectTool(String key) {
        tool = key;
        for (int i = 0; i < tabsRow.getChildCount(); i++) {
            LinearLayout c = (LinearLayout) tabsRow.getChildAt(i);
            TextView t = (TextView) c.getChildAt(0);
            boolean on = key.equals(t.getTag());
            t.setTextColor(on ? Color.parseColor("#e84e1b") : Color.parseColor("#999999"));
            t.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
        crop.setVisibility("crop".equals(key) ? View.VISIBLE : View.GONE);
        if ("filters".equals(key)) buildFilters();
        else if ("adjust".equals(key)) buildAdjust();
        else if ("crop".equals(key)) buildCrop();
        else if ("rotate".equals(key)) buildRotate();
    }

    // ---------- FILTERS ----------
    private void buildFilters() {
        panel.removeAllViews();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < FILTERS.length; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(FILTERS[i]); b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            boolean on = i == filterIdx;
            b.setTextColor(on ? Color.WHITE : Color.parseColor("#bbbbbb"));
            try { b.setStateListAnimator(null); } catch (Exception ignore) {}
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(8));
            g.setColor(on ? Color.parseColor("#e84e1b") : Color.parseColor("#1f1f1f"));
            b.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(8);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> { filterIdx = idx; renderPreview(); buildFilters(); });
            row.addView(b);
        }
        hs.addView(row);
        panel.addView(hs);
    }

    // ---------- ADJUST ----------
    private void buildAdjust() {
        panel.removeAllViews();
        panel.addView(slider("Brightness", bright, v -> { bright = v; renderPreview(); }));
        panel.addView(slider("Contrast", contrast, v -> { contrast = v; renderPreview(); }));
        panel.addView(slider("Saturation", sat, v -> { sat = v; renderPreview(); }));
        panel.addView(slider("Warmth", warmth, v -> { warmth = v; renderPreview(); }));
    }

    private interface IntCb { void on(int v); }
    private View slider(String label, int value, final IntCb cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(label); t.setTextColor(Color.parseColor("#cccccc"));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        SeekBar sb = new SeekBar(this);
        sb.setMax(200);
        sb.setProgress(value + 100);
        sb.getProgressDrawable().setColorFilter(Color.parseColor("#e84e1b"), android.graphics.PorterDuff.Mode.SRC_IN);
        sb.getThumb().setColorFilter(Color.parseColor("#e84e1b"), android.graphics.PorterDuff.Mode.SRC_IN);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { cb.on(p - 100); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        row.addView(t); row.addView(sb);
        return row;
    }

    // ---------- CROP ----------
    private void buildCrop() {
        panel.removeAllViews();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        final String[] names = {"Free","1:1","4:3","3:4","16:9"};
        final float[] vals = {0f, 1f, 4f/3f, 3f/4f, 16f/9f};
        for (int i = 0; i < names.length; i++) {
            final float a = vals[i];
            Button b = pill(names[i]);
            b.setOnClickListener(v -> crop.setAspect(a));
            row.addView(b);
        }
        Button apply = pill("APPLY CROP");
        apply.setOnClickListener(v -> applyCrop());
        row.addView(apply);
        hs.addView(row);
        panel.addView(hs);
    }

    private void applyCrop() {
        if (base == null || preview == null) return;
        Rect r = crop.getCropInBitmap();
        try {
            Bitmap cut = Bitmap.createBitmap(preview, r.left, r.top, r.width(), r.height());
            base = cut;
            resetColor();
            crop.setBitmapSize(base.getWidth(), base.getHeight());
            renderPreview();
            toast("Cropped");
        } catch (Exception e) { toast("Crop fail"); }
    }

    // ---------- ROTATE ----------
    private void buildRotate() {
        panel.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button rl = pill("Rotate ⟲"); rl.setOnClickListener(v -> rotateBake(-90));
        Button rr = pill("Rotate ⟳"); rr.setOnClickListener(v -> rotateBake(90));
        Button fh = pill("Flip ⇋"); fh.setOnClickListener(v -> flipBake(true));
        Button fv = pill("Flip ⇅"); fv.setOnClickListener(v -> flipBake(false));
        row.addView(rl); row.addView(rr); row.addView(fh); row.addView(fv);
        panel.addView(row);
    }

    private void rotateBake(float deg) {
        if (preview == null) return;
        Matrix m = new Matrix(); m.postRotate(deg);
        bake(m);
    }
    private void flipBake(boolean horizontal) {
        if (preview == null) return;
        Matrix m = new Matrix();
        if (horizontal) m.postScale(-1, 1); else m.postScale(1, -1);
        bake(m);
    }
    private void bake(Matrix m) {
        try {
            Bitmap nb = Bitmap.createBitmap(preview, 0, 0, preview.getWidth(), preview.getHeight(), m, true);
            base = nb;
            resetColor();
            crop.setBitmapSize(base.getWidth(), base.getHeight());
            renderPreview();
        } catch (Exception e) { toast("Fail"); }
    }

    // ---------- RENDER ----------
    private void resetColor() { filterIdx = 0; bright = 0; contrast = 0; sat = 0; warmth = 0; }

    private void renderPreview() {
        if (base == null) return;
        try {
            ColorMatrix cm = buildMatrix();
            Bitmap out = Bitmap.createBitmap(base.getWidth(), base.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(new ColorMatrixColorFilter(cm));
            canvas.drawBitmap(base, 0, 0, p);
            preview = out;
            img.setImageBitmap(out);
        } catch (Exception e) { img.setImageBitmap(base); preview = base; }
    }

    private ColorMatrix buildMatrix() {
        ColorMatrix sm = new ColorMatrix();
        sm.setSaturation(1 + sat / 100f);

        float sc = 1 + contrast / 100f;
        float tr = 128 * (1 - sc) + bright;
        ColorMatrix cbm = new ColorMatrix(new float[]{
                sc, 0, 0, 0, tr,
                0, sc, 0, 0, tr,
                0, 0, sc, 0, tr,
                0, 0, 0, 1, 0});

        float w = warmth * 0.6f;
        ColorMatrix wm = new ColorMatrix(new float[]{
                1, 0, 0, 0, w,
                0, 1, 0, 0, 0,
                0, 0, 1, 0, -w,
                0, 0, 0, 1, 0});

        ColorMatrix out = new ColorMatrix(sm);
        out.postConcat(cbm);
        out.postConcat(wm);
        out.postConcat(filterMatrix(filterIdx));
        return out;
    }

    private ColorMatrix filterMatrix(int idx) {
        switch (idx) {
            case 1: { ColorMatrix m = new ColorMatrix(); m.setSaturation(0); return m; }
            case 2: return new ColorMatrix(new float[]{
                    0.393f,0.769f,0.189f,0,0, 0.349f,0.686f,0.168f,0,0, 0.272f,0.534f,0.131f,0,0, 0,0,0,1,0});
            case 3: { ColorMatrix m = new ColorMatrix(); m.setSaturation(1.5f);
                ColorMatrix c = new ColorMatrix(new float[]{1.1f,0,0,0,-10, 0,1.1f,0,0,-10, 0,0,1.1f,0,-10, 0,0,0,1,0});
                m.postConcat(c); return m; }
            case 4: return new ColorMatrix(new float[]{1.1f,0,0,0,15, 0,1f,0,0,5, 0,0,0.9f,0,-10, 0,0,0,1,0});
            case 5: return new ColorMatrix(new float[]{0.9f,0,0,0,-10, 0,1f,0,0,0, 0,0,1.15f,0,15, 0,0,0,1,0});
            case 6: { ColorMatrix m = new ColorMatrix(new float[]{
                    0.35f,0.6f,0.13f,0,20, 0.3f,0.6f,0.15f,0,15, 0.25f,0.5f,0.12f,0,10, 0,0,0,1,0});
                return m; }
            case 7: { ColorMatrix m = new ColorMatrix(); m.setSaturation(0);
                ColorMatrix c = new ColorMatrix(new float[]{1.25f,0,0,0,-25, 0,1.25f,0,0,-25, 0,0,1.25f,0,-25, 0,0,0,1,0});
                m.postConcat(c); return m; }
            default: return new ColorMatrix();
        }
    }

    private void save() {
        if (preview == null) { toast("Kuch nahi"); return; }
        toast("Saving...");
        final Bitmap out = preview;
        exec.submit(() -> {
            final Uri saved = Media.save(this, out);
            main.post(() -> {
                if (saved != null) { toast("Saved to gallery ✓"); finish(); }
                else toast("Save fail (permission?)");
            });
        });
    }

    private Button pill(String text) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        try { b.setStateListAnimator(null); } catch (Exception ignore) {}
        GradientDrawable g = new GradientDrawable(); g.setCornerRadius(dp(8)); g.setColor(Color.parseColor("#1f1f1f"));
        b.setBackground(g);
        b.setMinWidth(0); b.setMinimumWidth(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.rightMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private Button textBtn(String text, int color) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextColor(color);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setBackgroundColor(Color.TRANSPARENT);
        try { b.setStateListAnimator(null); } catch (Exception ignore) {}
        b.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        return b;
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
