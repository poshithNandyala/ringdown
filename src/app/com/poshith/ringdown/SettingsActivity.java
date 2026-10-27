package com.poshith.ringdown;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.ringdown.core.Countdown;
import com.ringdown.core.Palette;
import com.ringdown.core.RemoteConfig;
import com.ringdown.core.RemoteRules;
import com.ringdown.core.RingScene;
import com.ringdown.core.SceneConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Launcher screen: live preview + every setting. Built in code (no XML layouts). */
public final class SettingsActivity extends Activity {
    static final String REPO_URL = "https://github.com/poshithNandyala/ringdown";
    static final String PRIVACY_URL = REPO_URL + "/blob/main/PRIVACY.md";
    private static final long DAY_MS = 86_400_000L;
    private static final int REQ_NOTIFY = 7;
    static final int BG = 0xFF07080F, CARD = 0xFF0E1120, FIELD = 0xFF0A0D18, STROKE = 0xFF1C2236;
    static final int TXT = 0xFFF2F4F8, SUB = 0xFF8C95AA, GOLD = 0xFFFFD27A, DANGER = 0xFFFF8F87;
    private static final int[] PRESETS = {21, 30, 66, 90, 100, 365};
    private static final String[] STYLE_HELP = {
            "A 3D gyroscope of rings. Breaking: it shrinks day by day. Building: it grows from the inside out.",
            "A calendar of spinning rings. Every finished day is marked, so you can count your streak at a glance.",
            "A tunnel of gates. Breaking: fly through one gate a day. Building: light up one gate a day.",
    };
    private static final String[] MODE_HELP = {
            "Each ring is the habit loop in your brain. Every day you stay away from it, one ring breaks and the old pathway gets weaker.",
            "You start with a faint blueprint. Every day you do it, one ring gets built and the new pathway gets stronger.",
    };
    private static final String[] DAYS_HELP = {
            "Most people pick 30 or 90 days. Bigger habits like smoking, alcohol or drugs usually need longer.",
            "66 days is the median time it took a daily habit to feel automatic in a UCL study (Lally et al., 2010).",
    };

    private SharedPreferences prefs;
    private PreviewView preview;
    private TextView statusTitle, statusSub, setBtn, dateLine, colorLabel, styleHelp, modeHelp, daysHelp, restartBtn;
    private EditText ringsEdit, goalEdit, doneEdit;
    private FlowLayout habitFlow;
    private final TextView[] modeBtns = new TextView[2];
    private final TextView[] styleBtns = new TextView[3];
    private final GradientDrawable[] swatchBg = new GradientDrawable[Palette.ALL.length];
    private final View[] swatches = new View[Palette.ALL.length];
    private final TextView[] chips = new TextView[PRESETS.length];
    private final ArrayList<TextView> habitChips = new ArrayList<>();
    private final ArrayList<String> habitIds = new ArrayList<>();
    private boolean binding;
    private Typeface medium, regular;
    private LinearLayout bannerBox, pagesBox;
    private TextView pagesHeader;
    private boolean waitForGuide;

    /** Remote config changes (a check finished, a banner closed): redraw the banner and pages. */
    private final SharedPreferences.OnSharedPreferenceChangeListener remoteListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                    if (key == null || key.startsWith(Remote.PREFIX)) refreshRemote();
                }
            };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = Prefs.get(this);
        Prefs.startDay(prefs);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.addView(buildUi(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Edges.pad(root);
        setContentView(root);
        bindControls();
        if (!prefs.getBoolean(Prefs.K_GUIDE, false)) {
            prefs.edit().putBoolean(Prefs.K_GUIDE, true).apply();
            waitForGuide = true;
            startActivity(new Intent(this, GuideActivity.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPreview();
        refreshStatus();
        refreshSetButton();
        showDone();
        prefs.registerOnSharedPreferenceChangeListener(remoteListener);
        refreshRemote();
        Remote.refresh(this, Remote.APP_MAX_AGE);
        if (waitForGuide) waitForGuide = false;   // the guide is opening on top; ask after it closes
        else maybeAskNotifications();
    }

    @Override
    protected void onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(remoteListener);
        super.onPause();
    }

    // ================================================================== UI

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(18), dp(20), dp(40));
        col.setFocusableInTouchMode(true);   // keeps the text fields from grabbing focus on launch
        scroll.addView(col, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("Ringdown", 28, TXT, medium), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView how = pill("How it works");
        how.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, GuideActivity.class)); }
        });
        head.addView(how, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));
        col.addView(head);
        TextView tag = text("Break a bad habit or build a good one. One ring a day.", 14, SUB, regular);
        tag.setPadding(0, dp(2), 0, 0);
        col.addView(tag);

        // ---- banner from the remote config (update available, news); hidden when there's none
        bannerBox = new LinearLayout(this);
        bannerBox.setOrientation(LinearLayout.VERTICAL);
        bannerBox.setVisibility(View.GONE);
        col.addView(bannerBox, full(ViewGroup.LayoutParams.WRAP_CONTENT, 16));

        // ---- live preview, shaped like this phone's screen
        DisplayMetrics real = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(real);
        float aspect = Math.max(real.widthPixels, real.heightPixels) / (float) Math.max(1, Math.min(real.widthPixels, real.heightPixels));
        int pw = Math.min((int) (getResources().getDisplayMetrics().widthPixels * 0.6f), dp(290));
        int ph = (int) (pw * Math.min(2.4f, Math.max(1.5f, aspect)));
        final float radius = dp(26);
        FrameLayout card = new FrameLayout(this);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        card.setElevation(dp(10));
        preview = new PreviewView(this);
        card.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View border = new View(this);
        border.setBackground(round(0x00000000, 0x30FFFFFF, radius));
        card.addView(border, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(pw, ph);
        clp.gravity = Gravity.CENTER_HORIZONTAL;
        clp.topMargin = dp(20);
        col.addView(card, clp);
        TextView hint = text("Live preview · tap it", 12, SUB, regular);
        hint.setGravity(Gravity.CENTER);
        col.addView(hint, full(ViewGroup.LayoutParams.WRAP_CONTENT, 8));

        preview.scene.setListener(new RingScene.Listener() {
            @Override public void onRingBurst(boolean milestone, boolean demo) {
                if (prefs.getBoolean(Prefs.K_HAPTIC, true)) {
                    preview.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                            ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS);
                }
            }
        });

        // ---- main actions
        setBtn = button("Set as wallpaper", true);
        setBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyWallpaper(); }
        });
        col.addView(setBtn, full(dp(54), 20));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView demoBtn = button("▶  Preview here", false);
        demoBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { preview.scene.playDemo(); }
        });
        TextView wallBtn = button("✦  On my wallpaper", false);
        wallBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playOnWallpaper(); }
        });
        actions.addView(demoBtn, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        wlp.leftMargin = dp(10);
        actions.addView(wallBtn, wlp);
        col.addView(actions, full(ViewGroup.LayoutParams.WRAP_CONTENT, 10));

        // ---- status
        LinearLayout status = card();
        statusTitle = text("", 19, TXT, medium);
        statusSub = text("", 13.5f, SUB, regular);
        statusSub.setPadding(0, dp(3), 0, 0);
        status.addView(statusTitle);
        status.addView(statusSub);
        col.addView(status, full(ViewGroup.LayoutParams.WRAP_CONTENT, 18));

        // ---- your habit
        col.addView(section("YOUR HABIT"));
        LinearLayout hb = card();
        LinearLayout modeSeg = segmented();
        String[] modeNames = {"Break a habit", "Build a habit"};
        for (int i = 0; i < 2; i++) {
            final int m = i;
            TextView t = text(modeNames[i], 15, SUB, medium);
            t.setGravity(Gravity.CENTER);
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { setMode(m); }
            });
            modeSeg.addView(t, new LinearLayout.LayoutParams(0, dp(44), 1f));
            modeBtns[i] = t;
        }
        hb.addView(modeSeg);
        modeHelp = text("", 12.5f, SUB, regular);
        hb.addView(modeHelp, full(ViewGroup.LayoutParams.WRAP_CONTENT, 8));

        hb.addView(label("Pick one", true));
        habitFlow = new FlowLayout(this, dp(8), dp(8));
        hb.addView(habitFlow, full(ViewGroup.LayoutParams.WRAP_CONTENT, 8));

        hb.addView(label("Name it", true));
        goalEdit = field("e.g. No sugar · Gym · DSA prep", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        goalEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        goalEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        goalEdit.addTextChangedListener(new Watcher() {
            @Override public void afterTextChanged(Editable s) {
                if (binding) return;
                String name = s.toString().trim();
                SharedPreferences.Editor e = prefs.edit().putString(Prefs.K_GOAL, name);
                Habits.Habit h = Habits.find(prefs.getString(Prefs.K_HABIT, ""));
                if (h != null && !h.name.equals(name)) e.putString(Prefs.K_HABIT, Habits.CUSTOM);
                e.apply();
                bindHabitChips();
                refreshPreviewConfig();
            }
        });
        hb.addView(goalEdit, full(dp(48), 8));

        hb.addView(label("Number of days  ·  1 ring = 1 day", true));
        ringsEdit = field("90", InputType.TYPE_CLASS_NUMBER);
        hb.addView(stepper(ringsEdit, new Step() {
            @Override public void by(int d) { setTotal(Prefs.total(prefs) + d); }
        }), full(ViewGroup.LayoutParams.WRAP_CONTENT, 8));
        ringsEdit.addTextChangedListener(new Watcher() {
            @Override public void afterTextChanged(Editable s) {
                if (binding) return;
                int n = parse(s);
                if (n >= SceneConfig.MIN_RINGS && n <= SceneConfig.MAX_RINGS && n != Prefs.total(prefs)) applyTotal(n, false);
            }
        });
        ringsEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) { if (!has) showTotal(); }
        });
        FlowLayout presetRow = new FlowLayout(this, dp(8), dp(8));
        for (int i = 0; i < PRESETS.length; i++) {
            final int value = PRESETS[i];
            TextView chip = chip(String.valueOf(value));
            chip.setMinWidth(dp(48));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { setTotal(value); }
            });
            presetRow.addView(chip);
            chips[i] = chip;
        }
        hb.addView(presetRow, full(ViewGroup.LayoutParams.WRAP_CONTENT, 10));
        daysHelp = text("", 12.5f, SUB, regular);
        hb.addView(daysHelp, full(ViewGroup.LayoutParams.WRAP_CONTENT, 8));

        hb.addView(label("Days already done (approximately)", true));
        doneEdit = field("0", InputType.TYPE_CLASS_NUMBER);
        hb.addView(stepper(doneEdit, new Step() {
            @Override public void by(int d) { setDone(Prefs.daysDone(prefs, Countdown.today()) + d); }
        }), full(ViewGroup.LayoutParams.WRAP_CONTENT, 8));
        doneEdit.addTextChangedListener(new Watcher() {
            @Override public void afterTextChanged(Editable s) {
                if (binding) return;
                int n = parse(s);
                if (n >= 0 && n <= Prefs.total(prefs) && n != Prefs.daysDone(prefs, Countdown.today())) applyDone(n, false);
            }
        });
        doneEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) { if (!has) showDone(); }
        });
        dateLine = text("", 12.5f, GOLD, regular);
        dateLine.setPadding(0, dp(8), 0, dp(4));
        dateLine.setClickable(true);
        dateLine.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickDate(); }
        });
        hb.addView(dateLine);
        col.addView(hb, full(ViewGroup.LayoutParams.WRAP_CONTENT, 0));

        // ---- look
        col.addView(section("LOOK"));
        LinearLayout look = card();
        look.addView(label("Style", false));
        LinearLayout seg = segmented();
        for (int i = 0; i < 3; i++) {
            final int style = i;
            TextView t = text(RingScene.STYLE_NAMES[i], 15, SUB, medium);
            t.setGravity(Gravity.CENTER);
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    prefs.edit().putInt(Prefs.K_STYLE, style).apply();
                    refreshPreviewConfig();
                    bindStyle();
                }
            });
            seg.addView(t, new LinearLayout.LayoutParams(0, dp(42), 1f));
            styleBtns[i] = t;
        }
        look.addView(seg, full(ViewGroup.LayoutParams.WRAP_CONTENT, 8));
        styleHelp = text("", 12.5f, SUB, regular);
        look.addView(styleHelp, full(ViewGroup.LayoutParams.WRAP_CONTENT, 8));

        colorLabel = label("Color", true);
        look.addView(colorLabel);
        LinearLayout sw = new LinearLayout(this);
        sw.setOrientation(LinearLayout.HORIZONTAL);
        sw.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < Palette.ALL.length; i++) {
            final int idx = i;
            Palette p = Palette.ALL[i];
            GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{p.a, p.b});
            gd.setShape(GradientDrawable.OVAL);
            View s = new View(this);
            s.setBackground(gd);
            s.setClickable(true);
            s.setContentDescription(p.name);
            s.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    prefs.edit().putInt(Prefs.K_PALETTE, idx).apply();
                    refreshPreviewConfig();
                    bindPalette();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
            lp.rightMargin = dp(12);
            sw.addView(s, lp);
            swatches[i] = s;
            swatchBg[i] = gd;
        }
        look.addView(sw, full(dp(48), 8));

        look.addView(label("Size", true));
        look.addView(slider(60, Prefs.K_SIZE, 70, 100), full(dp(40), 2));
        look.addView(label("Position (up ↔ down)", true));
        look.addView(slider(200, Prefs.K_POS, -100, 0), full(dp(40), 2));
        col.addView(look, full(ViewGroup.LayoutParams.WRAP_CONTENT, 0));

        // ---- extras
        col.addView(section("REWARDS & BATTERY"));
        LinearLayout ex = card();
        ex.addView(switchRow("Haptic tick", "A small buzz when a day completes", Prefs.K_HAPTIC, true));
        ex.addView(switchRow("Motivational line", "A short line after each day", Prefs.K_QUOTES, true));
        ex.addView(switchRow("Battery saver", "Lighter animation and lower frame rate", Prefs.K_LOW, false));
        col.addView(ex, full(ViewGroup.LayoutParams.WRAP_CONTENT, 0));

        // ---- extra pages from the remote config; hidden when there are none
        pagesHeader = section("MORE");
        pagesHeader.setVisibility(View.GONE);
        col.addView(pagesHeader);
        pagesBox = card();
        pagesBox.setPadding(dp(16), dp(6), dp(16), dp(6));
        pagesBox.setVisibility(View.GONE);
        col.addView(pagesBox, full(ViewGroup.LayoutParams.WRAP_CONTENT, 0));

        restartBtn = button("", false);
        restartBtn.setTextColor(DANGER);
        restartBtn.setBackground(ripple(round(0x00000000, 0x55FF8F87, dp(16)), dp(16)));
        restartBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmRestart(); }
        });
        col.addView(restartBtn, full(dp(48), 22));

        TextView tip = text("Tip: set it on your lock screen too. The day's animation plays the first time you look at your phone after midnight. Double-tap the home screen to replay it.", 12.5f, SUB, regular);
        col.addView(tip, full(ViewGroup.LayoutParams.WRAP_CONTENT, 18));
        TextView ver = text("Ringdown " + Remote.versionName(this) + "  ·  open source on GitHub", 12, 0x77FFFFFF, regular);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(10), 0, dp(4));
        ver.setClickable(true);
        ver.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl(REPO_URL); }
        });
        ver.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { developerMenu(); return true; }
        });
        col.addView(ver, full(ViewGroup.LayoutParams.WRAP_CONTENT, 12));
        TextView privacy = text("Privacy policy", 12, 0x77FFFFFF, regular);
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(0, dp(4), 0, dp(10));
        privacy.setClickable(true);
        privacy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl(PRIVACY_URL); }
        });
        col.addView(privacy, full(ViewGroup.LayoutParams.WRAP_CONTENT, 0));
        return scroll;
    }

    // ================================================================== remote config

    private void refreshRemote() {
        if (bannerBox == null) return;
        RemoteConfig c = Remote.config(prefs);
        RemoteRules.Device d = Remote.device(this, prefs);
        RemoteRules.Banner b = RemoteRules.banner(c, d, Remote.set(prefs, Remote.K_DISMISSED));
        bannerBox.removeAllViews();
        if (b == null) {
            bannerBox.setVisibility(View.GONE);
        } else {
            bannerBox.addView(bannerView(b));
            bannerBox.setVisibility(View.VISIBLE);
        }
        List<RemoteConfig.Page> pages = RemoteRules.pages(c, d);
        pagesBox.removeAllViews();
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) {
                View line = new View(this);
                line.setBackgroundColor(STROKE);
                pagesBox.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));
            }
            pagesBox.addView(pageRow(pages.get(i)));
        }
        int vis = pages.isEmpty() ? View.GONE : View.VISIBLE;
        pagesBox.setVisibility(vis);
        pagesHeader.setVisibility(vis);
    }

    private View bannerView(final RemoteRules.Banner b) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(round(0x16FFD27A, 0x66FFD27A, dp(20)));
        if (b.title.length() > 0) box.addView(text(b.title, 16, TXT, medium));
        if (b.text.length() > 0) {
            TextView t = text(b.text, 13.5f, 0xFFC9CFDC, regular);
            t.setLineSpacing(0, 1.2f);
            t.setPadding(0, b.title.length() > 0 ? dp(4) : 0, 0, 0);
            box.addView(t);
        }
        boolean hasAction = b.button.length() > 0 && b.url.length() > 0;
        if (hasAction || b.dismissable) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            if (hasAction) {
                TextView go = button(b.button, true);
                go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
                go.setPadding(dp(18), 0, dp(18), 0);
                go.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { openUrl(b.url); }
                });
                row.addView(go, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
            }
            if (b.dismissable) {
                TextView later = text(b.update ? "Not now" : "Dismiss", 14, SUB, medium);
                later.setGravity(Gravity.CENTER);
                later.setPadding(dp(16), 0, dp(16), 0);
                later.setBackground(ripple(round(0x00000000, 0, dp(14)), dp(14)));
                later.setClickable(true);
                later.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { Remote.dismiss(prefs, b.key); }
                });
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
                llp.leftMargin = hasAction ? dp(6) : 0;
                row.addView(later, llp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(12);
            box.addView(row, rlp);
        }
        return box;
    }

    private View pageRow(final RemoteConfig.Page p) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(p.title, 15.5f, TXT, regular));
        if (p.subtitle.length() > 0) {
            TextView st = text(p.subtitle, 12.5f, SUB, regular);
            st.setPadding(0, dp(2), 0, 0);
            texts.addView(st);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(text("›", 22, SUB, regular));
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (p.inBrowser) {
                    openUrl(p.url);
                    return;
                }
                Intent i = new Intent(SettingsActivity.this, WebPageActivity.class);
                i.putExtra(WebPageActivity.EXTRA_URL, p.url);
                i.putExtra(WebPageActivity.EXTRA_TITLE, p.title);
                startActivity(i);
            }
        });
        return row;
    }

    /** Android 13+ needs permission for notifications. Ask once, after the first-run guide. */
    private void maybeAskNotifications() {
        if (Build.VERSION.SDK_INT < 33 || prefs.getBoolean(Remote.K_ASKED, false)) return;
        prefs.edit().putBoolean(Remote.K_ASKED, true).apply();
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        try {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQ_NOTIFY && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            Remote.notifyDue(getApplicationContext(), prefs);
        }
    }

    /** Long-press the version line: check the remote config now, or make this a tester phone. */
    private void developerMenu() {
        final boolean tester = prefs.getBoolean(Remote.K_TESTER, false);
        String[] items = {"Check for news now", tester ? "Tester mode: ON (tap to turn off)" : "Tester mode: OFF (tap to turn on)"};
        new AlertDialog.Builder(this)
                .setTitle("Remote control")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            prefs.edit().remove(Remote.K_TRY_AT).apply();
                            Remote.refresh(SettingsActivity.this, 0L);
                            Toast.makeText(SettingsActivity.this, "Checking…", Toast.LENGTH_SHORT).show();
                        } else {
                            prefs.edit().putBoolean(Remote.K_TESTER, !tester).apply();
                            Toast.makeText(SettingsActivity.this, tester ? "Tester mode off" : "Tester mode on", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    // ================================================================== binding

    private void bindControls() {
        binding = true;
        goalEdit.setText(prefs.getString(Prefs.K_GOAL, ""));
        binding = false;
        showTotal();
        showDone();
        bindMode();
        bindStyle();
        bindPalette();
    }

    private void bindMode() {
        int m = Prefs.mode(prefs);
        for (int i = 0; i < 2; i++) {
            boolean on = i == m;
            modeBtns[i].setTextColor(on ? GOLD : SUB);
            modeBtns[i].setBackground(on ? round(0x24FFD27A, 0x88FFD27A, dp(11)) : null);
        }
        modeHelp.setText(MODE_HELP[m]);
        daysHelp.setText(DAYS_HELP[m]);
        restartBtn.setText(m == RingScene.MODE_BUILD ? "Missed a day? Restart from today" : "Slipped? Restart from today");
        buildHabitChips(m);
    }

    private void buildHabitChips(int mode) {
        habitFlow.removeAllViews();
        habitChips.clear();
        habitIds.clear();
        for (final Habits.Habit h : Habits.ALL) {
            if (h.mode != mode) continue;
            TextView c = chip(h.emoji + "  " + h.name);
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { pickHabit(h); }
            });
            habitFlow.addView(c);
            habitChips.add(c);
            habitIds.add(h.id);
        }
        TextView custom = chip("✏️  Custom");
        custom.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putString(Prefs.K_HABIT, Habits.CUSTOM).apply();
                bindHabitChips();
                goalEdit.requestFocus();
                goalEdit.setSelection(goalEdit.getText().length());
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(goalEdit, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        habitFlow.addView(custom);
        habitChips.add(custom);
        habitIds.add(Habits.CUSTOM);
        bindHabitChips();
    }

    private void bindHabitChips() {
        String sel = prefs.getString(Prefs.K_HABIT, "");
        for (int i = 0; i < habitChips.size(); i++) styleChip(habitChips.get(i), habitIds.get(i).equals(sel));
    }

    private void showTotal() {
        binding = true;
        ringsEdit.setText(String.valueOf(Prefs.total(prefs)));
        binding = false;
        int total = Prefs.total(prefs);
        for (int i = 0; i < chips.length; i++) styleChip(chips[i], PRESETS[i] == total);
    }

    private void showDone() {
        binding = true;
        doneEdit.setText(String.valueOf(Prefs.daysDone(prefs, Countdown.today())));
        binding = false;
        refreshDate();
    }

    private void bindStyle() {
        int s = Prefs.config(prefs).style;
        for (int i = 0; i < 3; i++) {
            boolean on = i == s;
            styleBtns[i].setTextColor(on ? GOLD : SUB);
            styleBtns[i].setBackground(on ? round(0x24FFD27A, 0x88FFD27A, dp(11)) : null);
        }
        styleHelp.setText(STYLE_HELP[s]);
    }

    private void bindPalette() {
        int p = Math.max(0, Math.min(Palette.ALL.length - 1, Prefs.config(prefs).palette));
        for (int i = 0; i < swatches.length; i++) {
            boolean on = i == p;
            swatchBg[i].setStroke(on ? dp(3) : 0, 0xFFFFFFFF);
            swatches[i].setScaleX(on ? 1.12f : 1f);
            swatches[i].setScaleY(on ? 1.12f : 1f);
        }
        colorLabel.setText("Color · " + Palette.ALL[p].name);
    }

    private void refreshPreviewConfig() {
        SceneConfig c = Prefs.config(prefs);
        if (Build.VERSION.SDK_INT < 29) c.lowPower = true;
        preview.scene.setConfig(c);
    }

    private void refreshPreview() {
        refreshPreviewConfig();
        long today = Countdown.today();
        preview.scene.setProgress(Prefs.ringsLeft(prefs, today), Prefs.startsIn(prefs, today));
    }

    private void refreshStatus() {
        long today = Countdown.today();
        int total = Prefs.total(prefs);
        long start = Prefs.startDay(prefs);
        int left = Countdown.left(total, start, today);
        int done = total - left;
        int startsIn = Countdown.startsIn(start, today);
        boolean build = Prefs.mode(prefs) == RingScene.MODE_BUILD;
        String clear = formatDay(Countdown.clearDay(total, start));
        String goal = prefs.getString(Prefs.K_GOAL, "").trim();
        String prefix = goal.length() > 0 ? goal + "  ·  " : "";
        if (startsIn > 0) {
            statusTitle.setText(prefix + (startsIn == 1 ? "Starts tomorrow" : "Starts in " + startsIn + " days"));
            statusSub.setText(total + " rings ready · " + (build ? "complete on " : "all clear on ") + clear);
        } else if (left > 0) {
            statusTitle.setText(prefix + "Day " + (done + 1) + " of " + total);
            if (build) statusSub.setText(done + (done == 1 ? " ring" : " rings") + " built · complete on " + clear);
            else statusSub.setText(left + (left == 1 ? " ring" : " rings") + " left · all clear on " + clear);
        } else {
            statusTitle.setText(prefix + "All " + total + (build ? " rings built" : " rings cleared"));
            statusSub.setText("Finished on " + clear + ". Restart below to begin a new one.");
        }
        refreshDate();
    }

    private void refreshDate() {
        long start = Prefs.startDay(prefs);
        long today = Countdown.today();
        String d = start == today ? "today (" + formatDay(start) + ")" : formatDay(start);
        dateLine.setText("Day 1: " + d + "   ·   pick exact date ›");
    }

    private boolean ourWallpaperActive() {
        try {
            WallpaperInfo info = WallpaperManager.getInstance(this).getWallpaperInfo();
            return info != null && getPackageName().equals(info.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private void refreshSetButton() {
        setBtn.setText(ourWallpaperActive() ? "Wallpaper is on  ✓   Re-apply" : "Set as wallpaper");
    }

    // ================================================================== actions

    private void setMode(int m) {
        if (m == Prefs.mode(prefs)) return;
        SharedPreferences.Editor e = prefs.edit().putInt(Prefs.K_MODE, m);
        Habits.Habit h = Habits.find(prefs.getString(Prefs.K_HABIT, ""));
        if (h != null && h.mode != m) e.putString(Prefs.K_HABIT, "");
        e.apply();
        Prefs.markSeen(prefs);
        bindMode();
        refreshPreview();
        refreshStatus();
        refreshRemote();   // messages can target break or build mode
    }

    private void pickHabit(Habits.Habit h) {
        prefs.edit().putString(Prefs.K_HABIT, h.id).putString(Prefs.K_GOAL, h.name).putInt(Prefs.K_TOTAL, h.days).apply();
        Prefs.markSeen(prefs);
        binding = true;
        goalEdit.setText(h.name);
        binding = false;
        bindHabitChips();
        showTotal();
        showDone();
        refreshPreview();
        refreshStatus();
    }

    private void setTotal(int n) {
        applyTotal(SceneConfig.clampRings(n), true);
    }

    private void applyTotal(int n, boolean rewrite) {
        prefs.edit().putInt(Prefs.K_TOTAL, n).apply();
        Prefs.markSeen(prefs);
        if (rewrite) showTotal();
        else for (int i = 0; i < chips.length; i++) styleChip(chips[i], PRESETS[i] == n);
        showDone();
        refreshPreview();
        refreshStatus();
    }

    private void setDone(int n) {
        applyDone(Math.max(0, Math.min(Prefs.total(prefs), n)), true);
    }

    /** Days already done = n, so day 1 was n days ago. */
    private void applyDone(int n, boolean rewrite) {
        prefs.edit().putLong(Prefs.K_START, Countdown.today() - n).apply();
        Prefs.markSeen(prefs);
        if (rewrite) showDone(); else refreshDate();
        refreshPreview();
        refreshStatus();
    }

    private void pickDate() {
        long start = Prefs.startDay(prefs);
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(start * DAY_MS);
        DatePickerDialog d = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override public void onDateSet(DatePicker view, int y, int m, int day) {
                Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                u.clear();
                u.set(y, m, day);
                long epochDay = Math.floorDiv(u.getTimeInMillis(), DAY_MS);
                prefs.edit().putLong(Prefs.K_START, epochDay).apply();
                Prefs.markSeen(prefs);
                showDone();
                refreshPreview();
                refreshStatus();
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        d.show();
    }

    private void confirmRestart() {
        final int total = Prefs.total(prefs);
        boolean build = Prefs.mode(prefs) == RingScene.MODE_BUILD;
        new AlertDialog.Builder(this)
                .setTitle("Restart from today?")
                .setMessage((build ? "The rings you built are cleared" : "All " + total + " rings come back")
                        + " and day 1 starts today. A slip is part of the process. What matters is starting again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restart", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        prefs.edit().putLong(Prefs.K_START, Countdown.today()).putInt(Prefs.K_SEEN, total).apply();
                        showDone();
                        refreshPreview();
                        refreshStatus();
                    }
                })
                .show();
    }

    private void applyWallpaper() {
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, RingsWallpaperService.class));
        try {
            startActivity(i);
            return;
        } catch (Throwable ignored) {
        }
        try {
            startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            return;
        } catch (Throwable ignored) {
        }
        Toast.makeText(this, "Open Settings › Wallpaper › Live wallpapers and pick Ringdown", Toast.LENGTH_LONG).show();
    }

    /** Go to the home screen and have the real wallpaper play tomorrow's animation. */
    private void playOnWallpaper() {
        if (!ourWallpaperActive()) {
            Toast.makeText(this, "Set Ringdown as your wallpaper first", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putLong(Prefs.K_DEMO_AT, System.currentTimeMillis()).apply();
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(home);
        } catch (Throwable ignored) {
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable ignored) {
        }
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private static int parse(Editable s) {
        try {
            return Integer.parseInt(s.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ================================================================== view helpers

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 12, SUB, medium);
        t.setLetterSpacing(0.18f);
        t.setPadding(dp(4), dp(26), 0, dp(10));
        return t;
    }

    private TextView label(String s, boolean gapAbove) {
        TextView t = text(s, 13.5f, 0xFFC4CAD8, regular);
        t.setPadding(0, gapAbove ? dp(16) : 0, 0, 0);
        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackground(round(CARD, STROKE, dp(20)));
        return l;
    }

    private LinearLayout segmented() {
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(round(FIELD, STROKE, dp(14)));
        return seg;
    }

    private LinearLayout.LayoutParams full(int height, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    private GradientDrawable round(int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private Drawable ripple(Drawable content, float radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, round(0xFFFFFFFF, 0, radius));
    }

    private TextView button(String s, boolean primary) {
        TextView b = text(s, 15.5f, primary ? 0xFF1C1406 : GOLD, medium);
        b.setGravity(Gravity.CENTER);
        float r = dp(16);
        Drawable bg;
        if (primary) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFE6AA, 0xFFFFC35C});
            g.setCornerRadius(r);
            bg = g;
        } else {
            bg = round(0x00000000, 0x55FFD27A, r);
        }
        b.setBackground(ripple(bg, r));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private TextView pill(String s) {
        TextView b = text(s, 13, GOLD, medium);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setBackground(ripple(round(0x1AFFD27A, 0x66FFD27A, dp(18)), dp(18)));
        b.setClickable(true);
        return b;
    }

    private TextView chip(String s) {
        TextView c = text(s, 14, SUB, regular);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(12), dp(8), dp(12), dp(8));
        c.setClickable(true);
        styleChip(c, false);
        return c;
    }

    private void styleChip(TextView c, boolean on) {
        c.setTextColor(on ? GOLD : 0xFFC4CAD8);
        c.setBackground(ripple(round(on ? 0x24FFD27A : FIELD, on ? 0x99FFD27A : STROKE, dp(12)), dp(12)));
    }

    private interface Step { void by(int delta); }

    /** [−] [ number ] [+] ; long-press the buttons for ±10. */
    private LinearLayout stepper(final EditText edit, final Step step) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = stepButton("−");
        TextView plus = stepButton("+");
        edit.setGravity(Gravity.CENTER);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        edit.setTypeface(medium);
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                edit.clearFocus();
                hideKeyboard(v);
                return false;
            }
        });
        row.addView(minus, new LinearLayout.LayoutParams(dp(54), dp(52)));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        elp.leftMargin = dp(10);
        elp.rightMargin = dp(10);
        row.addView(edit, elp);
        row.addView(plus, new LinearLayout.LayoutParams(dp(54), dp(52)));
        minus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { step.by(-1); }
        });
        plus.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { step.by(1); }
        });
        minus.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { step.by(-10); return true; }
        });
        plus.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { step.by(10); return true; }
        });
        return row;
    }

    private TextView stepButton(String s) {
        TextView b = text(s, 24, TXT, regular);
        b.setGravity(Gravity.CENTER);
        b.setBackground(ripple(round(FIELD, STROKE, dp(14)), dp(14)));
        b.setClickable(true);
        return b;
    }

    private EditText field(String hint, int inputType) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0x55FFFFFF);
        e.setTextColor(TXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setSingleLine(true);
        e.setInputType(inputType);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(round(FIELD, STROKE, dp(12)));
        return e;
    }

    private SeekBar slider(int max, final String key, final int offset, int def) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(prefs.getInt(key, def) - offset);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (!fromUser) return;
                prefs.edit().putInt(key, progress + offset).apply();
                refreshPreviewConfig();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        return sb;
    }

    private View switchRow(String title, String sub, final String key, boolean def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 15.5f, TXT, regular));
        TextView st = text(sub, 12.5f, SUB, regular);
        st.setPadding(0, dp(2), 0, 0);
        texts.addView(st);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch s = new Switch(this);
        s.setChecked(prefs.getBoolean(key, def));
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.edit().putBoolean(key, checked).apply();
                refreshPreviewConfig();
            }
        });
        row.addView(s);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { s.toggle(); }
        });
        return row;
    }

    static String formatDay(long epochDay) {
        SimpleDateFormat f = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault());
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(epochDay * DAY_MS));
    }

    private abstract static class Watcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }
}
