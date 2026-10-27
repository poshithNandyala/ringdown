package com.ringdown.core;

/** Everything the user can change that affects how the scene looks. */
public final class SceneConfig {
    public static final int MIN_RINGS = 1, MAX_RINGS = 365;

    public int mode = RingScene.MODE_BREAK;   // break a habit (rings vanish) or build one (rings appear)
    public int total = 90;          // number of rings / days
    public int style = RingScene.STYLE_ORB;
    public int palette = 0;
    public String goal = "";        // label shown above the count, e.g. "No sugar"
    public boolean quotes = true;   // motivational line after each day
    public float size = 1f;         // 0.7 .. 1.3
    public float pos = 0f;          // -1 (up) .. 1 (down)
    public boolean lowPower = false;

    public SceneConfig copy() {
        SceneConfig c = new SceneConfig();
        c.mode = mode; c.total = total; c.style = style; c.palette = palette; c.goal = goal; c.quotes = quotes;
        c.size = size; c.pos = pos; c.lowPower = lowPower;
        return c;
    }

    public static int clampRings(int n) {
        return Math.max(MIN_RINGS, Math.min(MAX_RINGS, n));
    }
}
