package com.rudra.gallery;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private List<Media.Item> all = new ArrayList<>();
    private List<Media.Item> shown = new ArrayList<>();
    private List<Media.Album> albums = new ArrayList<>();
    private String currentBucket = "all";

    private LinearLayout root, chipsRow;
    private HorizontalScrollView chipsScroll;
    private GridView grid;
    private TextView emptyText;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int cell;

    private static final int REQ = 11;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        if (hasPerm()) loadData();
        else requestPerm();
    }

    private boolean hasPerm() {
        if (Build.VERSION.SDK_INT < 23) return true; // install-time permission
        String p = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPerm() {
        String[] perms = Build.VERSION.SDK_INT >= 33
                ? new String[]{ Manifest.permission.READ_MEDIA_IMAGES }
                : new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE };
        try { requestPermissions(perms, REQ); } catch (Exception ignore) {}
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] p, int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        if (rc == REQ && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) loadData();
        else { emptyText.setText("Photos dekhne ke liye permission do.\nSettings me jaake allow karo."); emptyText.setVisibility(View.VISIBLE); }
    }

    private void buildUi() {
        int sw = getResources().getDisplayMetrics().widthPixels;
        cell = sw / 3;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0a0a0a"));
        root.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setBackgroundColor(Color.parseColor("#111111"));
        header.setPadding(dp(16), dp(16), dp(16), dp(14));
        TextView title = new TextView(this);
        title.setText("Gallery");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title);
        root.addView(header);

        chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setBackgroundColor(Color.parseColor("#111111"));
        chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(dp(10), dp(4), dp(10), dp(10));
        chipsScroll.addView(chipsRow);
        root.addView(chipsScroll);

        grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setVerticalSpacing(dp(2));
        grid.setHorizontalSpacing(dp(2));
        grid.setPadding(dp(2), dp(2), dp(2), dp(2));
        grid.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        grid.setAdapter(new GridAdapter());
        grid.setOnItemClickListener((parent, view, position, id) -> openViewer(position));

        emptyText = new TextView(this);
        emptyText.setText("Loading...");
        emptyText.setTextColor(Color.parseColor("#888888"));
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(dp(20), dp(40), dp(20), dp(20));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        body.addView(grid);
        body.addView(emptyText);
        root.addView(body);

        setContentView(root);
    }

    private void loadData() {
        emptyText.setText("Loading...");
        emptyText.setVisibility(View.VISIBLE);
        Executors.newSingleThreadExecutor().submit(() -> {
            final List<Media.Item> items = Media.queryAll(this);
            final List<Media.Album> alb = Media.albums(items);
            main.post(() -> {
                all = items;
                albums = alb;
                renderChips();
                applyFilter("all");
                emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                if (items.isEmpty()) emptyText.setText("Koi photo nahi mili.");
            });
        });
    }

    private void renderChips() {
        chipsRow.removeAllViews();
        chipsRow.addView(chip("All", "all"));
        for (Media.Album a : albums) chipsRow.addView(chip(a.name + " (" + a.count + ")", a.id));
    }

    private View chip(String text, final String bucket) {
        TextView t = new TextView(this);
        t.setText(text);
        boolean sel = bucket.equals(currentBucket);
        t.setTextColor(sel ? Color.WHITE : Color.parseColor("#aaaaaa"));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(sel ? Color.parseColor("#e84e1b") : Color.parseColor("#1f1f1f"));
        t.setBackground(g);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> applyFilter(bucket));
        return t;
    }

    private void applyFilter(String bucket) {
        currentBucket = bucket;
        shown = Media.filter(all, bucket);
        renderChips();
        ((GridAdapter) grid.getAdapter()).notifyDataSetChanged();
    }

    private void openViewer(int pos) {
        Media.CURRENT = new ArrayList<>(shown);
        Intent i = new Intent(this, ViewerActivity.class);
        i.putExtra("pos", pos);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // edit/delete ke baad refresh
        if (hasPerm() && !all.isEmpty()) loadData();
    }

    private class GridAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int p) { return shown.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public View getView(int pos, View convert, ViewGroup parent) {
            ImageView iv;
            if (convert instanceof ImageView) iv = (ImageView) convert;
            else {
                iv = new ImageView(MainActivity.this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setLayoutParams(new GridView.LayoutParams(cell, cell));
                iv.setBackgroundColor(Color.parseColor("#1a1a1a"));
            }
            ThumbLoader.load(iv, shown.get(pos).uri, cell);
            return iv;
        }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
