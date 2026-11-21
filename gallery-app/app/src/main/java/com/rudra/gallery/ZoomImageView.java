package com.rudra.gallery;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

// Pinch-zoom + pan + double-tap zoom. Zoom na ho to left/right swipe next/prev.
public class ZoomImageView extends ImageView {

    public interface Swipe { void next(); void prev(); void tap(); }
    private Swipe swipe;

    private final Matrix matrix = new Matrix();
    private static final int NONE = 0, DRAG = 1, ZOOM = 2;
    private int mode = NONE;

    private final PointF last = new PointF();
    private final PointF start = new PointF();
    private float minScale = 1f, maxScale = 5f, saveScale = 1f;
    private final float[] mv = new float[9];
    private float origW, origH;
    private int viewW, viewH;

    private ScaleGestureDetector sgd;
    private GestureDetector gd;

    public ZoomImageView(Context c) { super(c); init(c); }

    public void setSwipe(Swipe s) { this.swipe = s; }

    private void init(Context c) {
        super.setClickable(true);
        setScaleType(ScaleType.MATRIX);
        sgd = new ScaleGestureDetector(c, new ScaleListener());
        gd = new GestureDetector(c, new GestureListener());
        setOnTouchListener((v, ev) -> {
            sgd.onTouchEvent(ev);
            gd.onTouchEvent(ev);
            PointF curr = new PointF(ev.getX(), ev.getY());
            switch (ev.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    last.set(curr); start.set(last); mode = DRAG; break;
                case MotionEvent.ACTION_MOVE:
                    if (mode == DRAG && saveScale > 1f) {
                        matrix.getValues(mv);
                        float dx = curr.x - last.x, dy = curr.y - last.y;
                        matrix.postTranslate(dx, dy);
                        fixTrans();
                        last.set(curr.x, curr.y);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    mode = NONE; break;
            }
            setImageMatrix(matrix);
            invalidate();
            return true;
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        viewW = w; viewH = h;
        fitCenter();
    }

    public void fitCenter() {
        if (getDrawable() == null || viewW == 0 || viewH == 0) return;
        int bw = getDrawable().getIntrinsicWidth();
        int bh = getDrawable().getIntrinsicHeight();
        if (bw == 0 || bh == 0) return;
        float scale = Math.min((float) viewW / bw, (float) viewH / bh);
        matrix.reset();
        matrix.postScale(scale, scale);
        float dx = (viewW - bw * scale) / 2f;
        float dy = (viewH - bh * scale) / 2f;
        matrix.postTranslate(dx, dy);
        origW = bw * scale;
        origH = bh * scale;
        saveScale = 1f;
        setImageMatrix(matrix);
    }

    private void fixTrans() {
        matrix.getValues(mv);
        float transX = mv[Matrix.MTRANS_X];
        float transY = mv[Matrix.MTRANS_Y];
        float fixX = getFix(transX, viewW, origW * saveScale);
        float fixY = getFix(transY, viewH, origH * saveScale);
        matrix.postTranslate(fixX, fixY);
    }

    private float getFix(float trans, int viewSize, float contentSize) {
        float minTrans, maxTrans;
        if (contentSize <= viewSize) { minTrans = (viewSize - contentSize) / 2f; maxTrans = minTrans; }
        else { minTrans = viewSize - contentSize; maxTrans = 0; }
        if (trans < minTrans) return minTrans - trans;
        if (trans > maxTrans) return maxTrans - trans;
        return 0;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override public boolean onScaleBegin(ScaleGestureDetector d) { mode = ZOOM; return true; }
        @Override public boolean onScale(ScaleGestureDetector d) {
            float factor = d.getScaleFactor();
            float prev = saveScale;
            saveScale *= factor;
            if (saveScale > maxScale) { saveScale = maxScale; factor = maxScale / prev; }
            else if (saveScale < minScale) { saveScale = minScale; factor = minScale / prev; }
            matrix.postScale(factor, factor, d.getFocusX(), d.getFocusY());
            fixTrans();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override public boolean onSingleTapConfirmed(MotionEvent e) {
            if (swipe != null) swipe.tap();
            return true;
        }
        @Override public boolean onDoubleTap(MotionEvent e) {
            if (saveScale > 1.1f) { fitCenter(); }
            else {
                float target = 2.5f;
                matrix.postScale(target, target, e.getX(), e.getY());
                saveScale = target;
                fixTrans();
                setImageMatrix(matrix);
            }
            return true;
        }
        @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            if (saveScale > 1.1f) return false; // zoomed -> pan, no swipe
            if (e1 == null || e2 == null) return false;
            float dx = e2.getX() - e1.getX();
            if (Math.abs(dx) > 120 && Math.abs(vx) > 200 && Math.abs(dx) > Math.abs(e2.getY() - e1.getY())) {
                if (swipe != null) { if (dx < 0) swipe.next(); else swipe.prev(); }
                return true;
            }
            return false;
        }
    }
}
