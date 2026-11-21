package com.rudra.gallery;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ViewerActivity extends Activity {

    private List<Media.Item> list;
    private int pos;
    private ZoomImageView image;
    private LinearLayout topBar, bottomBar;
    private TextView counter;
    private boolean uiVisible = true;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private static final int DEL_REQ = 21;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        list = Media.CURRENT;
        pos = getIntent().getIntExtra("pos", 0);
        if (list == null || list.isEmpty()) { finish(); return; }
        if (pos < 0 || pos >= list.size()) pos = 0;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));

        image = new ZoomImageView(this);
        image.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        image.setSwipe(new ZoomImageView.Swipe() {
            @Override public void next() { if (pos < list.size() - 1) { pos++; loadCurrent(); } }
            @Override public void prev() { if (pos > 0) { pos--; loadCurrent(); } }
            @Override public void tap() { toggleUi(); }
        });
        root.addView(image);

        // Top bar
        topBar = new LinearLayout(this);
        topBar.setBackgroundColor(Color.parseColor("#99000000"));
        topBar.setPadding(dp(12), dp(14), dp(12), dp(14));
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        Button back = flatBtn("‹  Back");
        back.setOnClickListener(v -> finish());
        counter = new TextView(this);
        counter.setTextColor(Color.WHITE);
        counter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        counter.setGravity(Gravity.CENTER);
        counter.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        TextView spacer = new TextView(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(60), -2));
        topBar.addView(back); topBar.addView(counter); topBar.addView(spacer);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2);
        tlp.gravity = Gravity.TOP;
        topBar.setLayoutParams(tlp);
        root.addView(topBar);

        // Bottom bar
        bottomBar = new LinearLayout(this);
        bottomBar.setBackgroundColor(Color.parseColor("#99000000"));
        bottomBar.setPadding(dp(8), dp(10), dp(8), dp(10));
        bottomBar.addView(barBtn("Edit", v -> openEditor()));
        bottomBar.addView(barBtn("Share", v -> share()));
        bottomBar.addView(barBtn("Delete", v -> delete()));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, -2);
        blp.gravity = Gravity.BOTTOM;
        bottomBar.setLayoutParams(blp);
        root.addView(bottomBar);

        setContentView(root);
        loadCurrent();
    }

    private void loadCurrent() {
        final Uri uri = list.get(pos).uri;
        counter.setText((pos + 1) + " / " + list.size());
        int req = Math.max(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);
        exec.submit(() -> {
            final Bitmap bmp = ThumbLoader.decode(this, uri, req);
            main.post(() -> {
                if (bmp != null) { image.setImageBitmap(bmp); image.fitCenter(); }
            });
        });
    }

    private void toggleUi() {
        uiVisible = !uiVisible;
        topBar.setVisibility(uiVisible ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(uiVisible ? View.VISIBLE : View.GONE);
    }

    private void openEditor() {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra("uri", list.get(pos).uri.toString());
        startActivity(i);
    }

    private void share() {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/*");
            i.putExtra(Intent.EXTRA_STREAM, list.get(pos).uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share"));
        } catch (Exception e) { toast("Share nahi hua"); }
    }

    private void delete() {
        final Uri uri = list.get(pos).uri;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                ArrayList<Uri> uris = new ArrayList<>(); uris.add(uri);
                PendingIntent pi = android.provider.MediaStore.createDeleteRequest(getContentResolver(), uris);
                startIntentSenderForResult(pi.getIntentSender(), DEL_REQ, null, 0, 0, 0);
            } else {
                getContentResolver().delete(uri, null, null);
                afterDelete();
            }
        } catch (Exception e) { toast("Delete nahi hua"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == DEL_REQ && res == RESULT_OK) afterDelete();
    }

    private void afterDelete() {
        if (pos >= 0 && pos < list.size()) list.remove(pos);
        if (list.isEmpty()) { finish(); return; }
        if (pos >= list.size()) pos = list.size() - 1;
        loadCurrent();
        toast("Deleted");
    }

    private Button flatBtn(String text) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setBackgroundColor(Color.TRANSPARENT);
        try { b.setStateListAnimator(null); } catch (Exception ignore) {}
        b.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        return b;
    }

    private Button barBtn(String text, View.OnClickListener cl) {
        Button b = new Button(this);
        b.setText(text); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setBackgroundColor(Color.TRANSPARENT);
        try { b.setStateListAnimator(null); } catch (Exception ignore) {}
        b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        b.setOnClickListener(cl);
        return b;
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
