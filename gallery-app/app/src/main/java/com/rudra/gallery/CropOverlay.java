package com.rudra.gallery;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

// Crop selection overlay: draggable/resizable rectangle. Aspect ratio support.
public class CropOverlay extends View {

    private float bmpW, bmpH;
    private final RectF img = new RectF();   // displayed image rect (fit-center)
    private final RectF crop = new RectF();  // current selection
    private float aspect = 0f;               // 0 = free

    private final Paint dim = new Paint();
    private final Paint border = new Paint();
    private final Paint handle = new Paint();
    private final Paint grid = new Paint();

    private int active = -1; // 0 TL,1 TR,2 BL,3 BR, 4 inside
    private float lastX, lastY;
    private final float touch;

    public CropOverlay(Context c) {
        super(c);
        dim.setColor(Color.parseColor("#99000000"));
        border.setColor(Color.WHITE); border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(dp(2)); border.setAntiAlias(true);
        handle.setColor(Color.parseColor("#e84e1b")); handle.setAntiAlias(true);
        grid.setColor(Color.parseColor("#66ffffff")); grid.setStrokeWidth(dp(1));
        touch = dp(24);
    }

    public void setBitmapSize(float w, float h) {
        bmpW = w; bmpH = h;
        recomputeImg();
    }

    public void setAspect(float a) {
        aspect = a;
        resetCrop();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        recomputeImg();
    }

    private void recomputeImg() {
        if (bmpW <= 0 || bmpH <= 0 || getWidth() == 0 || getHeight() == 0) return;
        float scale = Math.min(getWidth() / bmpW, getHeight() / bmpH);
        float w = bmpW * scale, h = bmpH * scale;
        float l = (getWidth() - w) / 2f, t = (getHeight() - h) / 2f;
        img.set(l, t, l + w, t + h);
        resetCrop();
        invalidate();
    }

    private void resetCrop() {
        if (aspect <= 0) { crop.set(img); return; }
        float iw = img.width(), ih = img.height();
        float w = iw, h = iw / aspect;
        if (h > ih) { h = ih; w = ih * aspect; }
        float cx = img.centerX(), cy = img.centerY();
        crop.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (crop.isEmpty()) return;
        // dim outside
        c.drawRect(img.left, img.top, img.right, crop.top, dim);
        c.drawRect(img.left, crop.bottom, img.right, img.bottom, dim);
        c.drawRect(img.left, crop.top, crop.left, crop.bottom, dim);
        c.drawRect(crop.right, crop.top, img.right, crop.bottom, dim);
        // grid thirds
        float tw = crop.width() / 3, th = crop.height() / 3;
        for (int i = 1; i < 3; i++) {
            c.drawLine(crop.left + tw * i, crop.top, crop.left + tw * i, crop.bottom, grid);
            c.drawLine(crop.left, crop.top + th * i, crop.right, crop.top + th * i, grid);
        }
        c.drawRect(crop, border);
        float r = dp(8);
        c.drawCircle(crop.left, crop.top, r, handle);
        c.drawCircle(crop.right, crop.top, r, handle);
        c.drawCircle(crop.left, crop.bottom, r, handle);
        c.drawCircle(crop.right, crop.bottom, r, handle);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                active = pick(x, y); lastX = x; lastY = y;
                return active != -1;
            case MotionEvent.ACTION_MOVE:
                if (active == -1) return false;
                float dx = x - lastX, dy = y - lastY;
                applyDrag(dx, dy);
                lastX = x; lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                active = -1;
                return true;
        }
        return false;
    }

    private int pick(float x, float y) {
        if (near(x, y, crop.left, crop.top)) return 0;
        if (near(x, y, crop.right, crop.top)) return 1;
        if (near(x, y, crop.left, crop.bottom)) return 2;
        if (near(x, y, crop.right, crop.bottom)) return 3;
        if (crop.contains(x, y)) return 4;
        return -1;
    }

    private boolean near(float x, float y, float px, float py) {
        return Math.abs(x - px) < touch && Math.abs(y - py) < touch;
    }

    private void applyDrag(float dx, float dy) {
        float min = dp(48);
        RectF n = new RectF(crop);
        if (active == 4) {
            n.offset(dx, dy);
            if (n.left < img.left) n.offset(img.left - n.left, 0);
            if (n.top < img.top) n.offset(0, img.top - n.top);
            if (n.right > img.right) n.offset(img.right - n.right, 0);
            if (n.bottom > img.bottom) n.offset(0, img.bottom - n.bottom);
            crop.set(n);
            return;
        }
        if (active == 0) { n.left += dx; n.top += dy; }
        else if (active == 1) { n.right += dx; n.top += dy; }
        else if (active == 2) { n.left += dx; n.bottom += dy; }
        else if (active == 3) { n.right += dx; n.bottom += dy; }

        if (aspect > 0) {
            // keep ratio by adjusting height from width
            float w = n.width();
            float h = w / aspect;
            if (active == 0 || active == 1) n.top = n.bottom - h; else n.bottom = n.top + h;
        }
        // clamp
        n.left = Math.max(img.left, Math.min(n.left, n.right - min));
        n.top = Math.max(img.top, Math.min(n.top, n.bottom - min));
        n.right = Math.min(img.right, Math.max(n.right, n.left + min));
        n.bottom = Math.min(img.bottom, Math.max(n.bottom, n.top + min));
        crop.set(n);
    }

    // Crop rect ko bitmap pixel coords me map karo
    public Rect getCropInBitmap() {
        if (img.width() <= 0) return new Rect(0, 0, (int) bmpW, (int) bmpH);
        float scale = bmpW / img.width();
        int l = Math.round((crop.left - img.left) * scale);
        int t = Math.round((crop.top - img.top) * scale);
        int rr = Math.round((crop.right - img.left) * scale);
        int bb = Math.round((crop.bottom - img.top) * scale);
        l = Math.max(0, Math.min(l, (int) bmpW - 1));
        t = Math.max(0, Math.min(t, (int) bmpH - 1));
        rr = Math.max(l + 1, Math.min(rr, (int) bmpW));
        bb = Math.max(t + 1, Math.min(bb, (int) bmpH));
        return new Rect(l, t, rr, bb);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
