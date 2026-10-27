package com.poshith.ringdown;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.DownloadListener;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.util.Locale;

/**
 * Shows one of the extra pages listed in the remote config (an https web page) inside the app.
 * No bridge to the app's data: the page is just a web page.
 */
public final class WebPageActivity extends Activity {
    static final String EXTRA_URL = "url", EXTRA_TITLE = "title";
    private WebView web;
    private ProgressBar bar;
    private OnBackInvokedCallback backCallback;
    private boolean backRegistered;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String url = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("https://")) {
            finish();
            return;
        }
        getWindow().setStatusBarColor(SettingsActivity.BG);
        getWindow().setNavigationBarColor(SettingsActivity.BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SettingsActivity.BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(6), dp(16), dp(6));
        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        close.setTextColor(SettingsActivity.TXT);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close");
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFFFFFFFF);
        close.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, mask));
        close.setClickable(true);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        top.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView t = new TextView(this);
        t.setText(title == null ? "" : title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setTextColor(SettingsActivity.TXT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setSingleLine(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(8);
        top.addView(t, tlp);
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout body = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(SettingsActivity.BG);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return handleLink(r.getUrl());
            }

            @Override public void onPageFinished(WebView v, String u) {
                bar.setVisibility(View.GONE);
            }

            @Override public void doUpdateVisitedHistory(WebView v, String u, boolean reload) {
                updateBack();
            }
        });
        web.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String u, String ua, String cd, String mime, long len) {
                open(Uri.parse(u));
            }
        });
        body.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bar = new ProgressBar(this);
        bar.setIndeterminate(true);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(36), dp(36));
        blp.gravity = Gravity.CENTER;
        body.addView(bar, blp);
        root.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Edges.pad(root);
        setContentView(root);
        if (state != null) web.restoreState(state);
        else web.loadUrl(url);
    }

    /** https stays in the page; other links open in their own app; risky schemes are blocked. */
    private boolean handleLink(Uri u) {
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("https")) return false;
        if (scheme.equals("intent") || scheme.equals("javascript") || scheme.equals("file")
                || scheme.equals("content") || scheme.equals("data") || scheme.equals("about")) return true;
        open(u);
        return true;
    }

    private void open(Uri u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Throwable ignored) {
        }
    }

    /** Android 13+: back goes back inside the page while it can, then closes it. */
    private void updateBack() {
        if (Build.VERSION.SDK_INT < 33 || web == null) return;
        boolean want = web.canGoBack();
        if (want == backRegistered) return;
        OnBackInvokedDispatcher d = getOnBackInvokedDispatcher();
        if (backCallback == null) {
            backCallback = new OnBackInvokedCallback() {
                @Override public void onBackInvoked() {
                    if (web.canGoBack()) web.goBack();
                    updateBack();
                }
            };
        }
        if (want) d.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        else d.unregisterOnBackInvokedCallback(backCallback);
        backRegistered = want;
    }

    /** Android 12 and older. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.setWebViewClient(new WebViewClient());
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
