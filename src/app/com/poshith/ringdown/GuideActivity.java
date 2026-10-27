package com.poshith.ringdown;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** "How it works": how to use the app and the research behind the rings. */
public final class GuideActivity extends Activity {
    private Typeface medium, regular, light;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        getWindow().setStatusBarColor(SettingsActivity.BG);
        getWindow().setNavigationBarColor(SettingsActivity.BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(SettingsActivity.BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(28), dp(24), dp(36));
        scroll.addView(col, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView kicker = text("HOW RINGDOWN WORKS", 12, SettingsActivity.GOLD, medium);
        kicker.setLetterSpacing(0.2f);
        col.addView(kicker);
        TextView title = text("Every ring is a habit loop", 26, SettingsActivity.TXT, medium);
        title.setPadding(0, dp(8), 0, 0);
        col.addView(title);

        para(col, "Habits run as loops in your brain: a cue, the routine, then the reward. Repeat a loop often "
                + "enough and your brain packs it into one automatic chunk. Researchers at MIT saw neurons in "
                + "the striatum fire at the start and the end of these chunks, like brackets around the habit. "
                + "Ringdown shows that loop as rings, one ring for each day.");

        heading(col, "Breaking a habit: watch the loop fade");
        para(col, "All the rings start lit. Every day you don't feed the old loop, one ring breaks, and the loop "
                + "in your brain gets weaker too: pathways you stop using fade. The brain recovers faster than "
                + "you'd think. When smokers quit, the extra nicotine receptors their brains had built up dropped "
                + "back to about non-smoker levels in 6 to 12 weeks. A craving can still pop up now and then. "
                + "That's normal, and it passes. Every one you ride out is a win. If you slip, restart and keep going.");

        heading(col, "Building a habit: watch the loop form");
        para(col, "The rings start as a faint blueprint. Every day you show up, one ring gets built. In a UCL "
                + "study, a daily habit took a median of 66 days to feel automatic (anywhere from 18 to 254 days), "
                + "and missing a single day didn't undo the progress.");

        heading(col, "Why seeing it helps");
        para(col, "Tracking progress makes people more likely to reach their goals, and it works best when the "
                + "progress is recorded somewhere you can see it (a review of 138 studies). Your lock screen is "
                + "the place you look most, often right when a craving or an excuse shows up.");

        heading(col, "How to use it");
        step(col, "1", "Choose Break a habit or Build a habit, then pick one from the list or type your own.");
        step(col, "2", "Set the number of days. 1 ring = 1 day. Already on a streak? Enter the days you've already done.");
        step(col, "3", "Pick a style and a color, tap Set as wallpaper and choose Home and lock screen.");
        step(col, "4", "At midnight a day completes. The first time you look at your phone after that, a ring breaks (or gets built) and the count updates.");
        step(col, "5", "Tap the home screen to make the rings react. Double-tap to replay today's animation.");
        step(col, "6", "Slipped or missed a day? Tap Restart. One bad day doesn't define you.");

        LinearLayout warn = new LinearLayout(this);
        warn.setOrientation(LinearLayout.VERTICAL);
        warn.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable wb = new GradientDrawable();
        wb.setColor(0x14FF8F87);
        wb.setStroke(dp(1), 0x44FF8F87);
        wb.setCornerRadius(dp(16));
        warn.setBackground(wb);
        warn.addView(text("Important", 15, 0xFFFFB3AD, medium));
        TextView wt = text("Ringdown is a motivation tool, not treatment. Stopping alcohol, some drugs or medicines "
                + "suddenly can be dangerous, so talk to a doctor first. In India you can call Tele-MANAS on 14416 "
                + "(free, 24×7) for mental health support.", 14, 0xFFE8D6D4, regular);
        wt.setPadding(0, dp(6), 0, 0);
        wt.setLineSpacing(0, 1.25f);
        warn.addView(wt);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(24);
        col.addView(warn, wlp);

        heading(col, "Sources");
        source(col, "Lally et al. (2010). How are habits formed. European Journal of Social Psychology.",
                "https://doi.org/10.1002/ejsp.674");
        source(col, "Martiros, Burgess & Graybiel (2018). Inversely active striatal projection neurons and interneurons selectively delimit useful behavioral sequences. Current Biology.",
                "https://pmc.ncbi.nlm.nih.gov/articles/PMC5820126");
        source(col, "Cosgrove et al. (2009). β2-Nicotinic acetylcholine receptor availability during acute and prolonged abstinence from tobacco smoking. Archives of General Psychiatry.",
                "https://doi.org/10.1001/archgenpsychiatry.2009.41");
        source(col, "Harkin et al. (2016). Does monitoring goal progress promote goal attainment? Psychological Bulletin.",
                "https://doi.org/10.1037/bul0000025");

        heading(col, "Privacy");
        source(col, "Ringdown keeps your habit and settings on your phone. It has no account and doesn't "
                + "track you. Read the privacy policy ›", SettingsActivity.PRIVACY_URL);

        TextView go = text("Got it", 16, 0xFF1C1406, medium);
        go.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFFE6AA, 0xFFFFC35C});
        g.setCornerRadius(dp(16));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(dp(16));
        go.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, mask));
        go.setClickable(true);
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        glp.topMargin = dp(28);
        col.addView(go, glp);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(SettingsActivity.BG);
        root.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Edges.pad(root);
        setContentView(root);
    }

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

    private void heading(LinearLayout col, String s) {
        TextView t = text(s, 18, SettingsActivity.TXT, medium);
        t.setPadding(0, dp(26), 0, 0);
        col.addView(t);
    }

    private void para(LinearLayout col, String s) {
        TextView t = text(s, 15, 0xFFC9CFDC, light);
        t.setLineSpacing(0, 1.3f);
        t.setPadding(0, dp(10), 0, 0);
        col.addView(t);
    }

    private void step(LinearLayout col, String n, String s) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, 0);
        TextView num = text(n, 13, SettingsActivity.GOLD, medium);
        num.setGravity(Gravity.CENTER);
        GradientDrawable c = new GradientDrawable();
        c.setShape(GradientDrawable.OVAL);
        c.setStroke(dp(1), 0x88FFD27A);
        num.setBackground(c);
        row.addView(num, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView t = text(s, 15, 0xFFC9CFDC, light);
        t.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(12);
        row.addView(t, lp);
        col.addView(row);
    }

    private void source(LinearLayout col, String s, final String url) {
        TextView t = text(s, 12.5f, SettingsActivity.SUB, regular);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(0, dp(8), 0, 0);
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Throwable ignored) {}
            }
        });
        col.addView(t);
    }
}
