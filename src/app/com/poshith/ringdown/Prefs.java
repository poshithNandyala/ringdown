package com.poshith.ringdown;

import android.content.Context;
import android.content.SharedPreferences;

import com.ringdown.core.Countdown;
import com.ringdown.core.RingScene;
import com.ringdown.core.SceneConfig;

/** Settings shared by the wallpaper and the settings screen (same process, same prefs instance). */
final class Prefs {
    static final String FILE = "ringdown";
    static final String K_MODE = "mode";          // RingScene.MODE_BREAK / MODE_BUILD
    static final String K_HABIT = "habit";        // Habits id or "custom"
    static final String K_TOTAL = "total";
    static final String K_START = "start_day";
    static final String K_STYLE = "style";
    static final String K_PALETTE = "palette";
    static final String K_GOAL = "goal";
    static final String K_QUOTES = "quotes";
    static final String K_HAPTIC = "haptic";
    static final String K_SIZE = "size";          // int percent 70..130
    static final String K_POS = "pos";            // int -100..100
    static final String K_LOW = "low_power";
    static final String K_SEEN = "last_seen_left"; // rings left the wallpaper last showed
    static final String K_DEMO_AT = "demo_at";     // time the app asked the wallpaper to play a preview
    static final String K_GUIDE = "guide_seen";

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Start day; the first time anything asks, the countdown starts today. */
    static long startDay(SharedPreferences p) {
        long s = p.getLong(K_START, Long.MIN_VALUE);
        if (s == Long.MIN_VALUE) {
            s = Countdown.today();
            p.edit().putLong(K_START, s).apply();
        }
        return s;
    }

    static int total(SharedPreferences p) {
        return SceneConfig.clampRings(p.getInt(K_TOTAL, 90));
    }

    static int mode(SharedPreferences p) {
        return p.getInt(K_MODE, RingScene.MODE_BREAK) == RingScene.MODE_BUILD ? RingScene.MODE_BUILD : RingScene.MODE_BREAK;
    }

    static SceneConfig config(SharedPreferences p) {
        SceneConfig c = new SceneConfig();
        c.mode = mode(p);
        c.total = total(p);
        c.style = Math.max(0, Math.min(2, p.getInt(K_STYLE, RingScene.STYLE_ORB)));
        c.palette = p.getInt(K_PALETTE, 0);
        c.goal = p.getString(K_GOAL, "");
        c.quotes = p.getBoolean(K_QUOTES, true);
        c.size = Math.max(70, Math.min(130, p.getInt(K_SIZE, 100))) / 100f;
        c.pos = Math.max(-100, Math.min(100, p.getInt(K_POS, 0))) / 100f;
        c.lowPower = p.getBoolean(K_LOW, false);
        return c;
    }

    static int ringsLeft(SharedPreferences p, long today) {
        return Countdown.left(total(p), startDay(p), today);
    }

    static int daysDone(SharedPreferences p, long today) {
        return Countdown.done(total(p), startDay(p), today);
    }

    static int startsIn(SharedPreferences p, long today) {
        return Countdown.startsIn(startDay(p), today);
    }

    /** Tell the wallpaper the current count is already known (no animation for it). */
    static void markSeen(SharedPreferences p) {
        p.edit().putInt(K_SEEN, ringsLeft(p, Countdown.today())).apply();
    }
}
