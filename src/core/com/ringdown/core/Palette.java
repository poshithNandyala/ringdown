package com.ringdown.core;

/** Color themes. Gold "accent" is the reward color: completed days and bursts use it. */
public final class Palette {
    public final String name;
    public final int bgTop, bgBottom;   // background gradient
    public final int a, b;              // ring gradient (first ring -> last ring)
    public final int accent;            // rewards, today's ring, completed ticks
    public final int core;              // inner light

    private Palette(String name, int bgTop, int bgBottom, int a, int b, int accent, int core) {
        this.name = name; this.bgTop = bgTop; this.bgBottom = bgBottom;
        this.a = a; this.b = b; this.accent = accent; this.core = core;
    }

    public static final Palette[] ALL = {
        new Palette("Aurora",  0xFF04050D, 0xFF0A0F25, 0xFF54F2D6, 0xFF8B7BFF, 0xFFFFD27A, 0xFFE9F6FF),
        new Palette("Solar",   0xFF090503, 0xFF1D1007, 0xFFFFC965, 0xFFFF6A3A, 0xFFFFF1C9, 0xFFFFF3DA),
        new Palette("Glacier", 0xFF02060E, 0xFF07192D, 0xFF8FE4FF, 0xFF3F6DFF, 0xFFFFE3A0, 0xFFE6F8FF),
        new Palette("Rose",    0xFF0B0307, 0xFF200A17, 0xFFFF7BA9, 0xFFFFB26B, 0xFFFFE2A0, 0xFFFFEEF4),
        new Palette("Emerald", 0xFF020906, 0xFF061F19, 0xFF6BFFB6, 0xFF25B6FF, 0xFFFFE08A, 0xFFEAFFF5),
        new Palette("Mono",    0xFF050505, 0xFF121317, 0xFFF2F4F8, 0xFF7C8391, 0xFFFFD27A, 0xFFFFFFFF),
    };

    public static Palette get(int i) {
        return ALL[Math.max(0, Math.min(ALL.length - 1, i))];
    }

    public static int lerp(int c1, int c2, float t) {
        if (t <= 0) return c1;
        if (t >= 1) return c2;
        int a1 = (c1 >>> 24), r1 = (c1 >> 16) & 255, g1 = (c1 >> 8) & 255, b1 = c1 & 255;
        int a2 = (c2 >>> 24), r2 = (c2 >> 16) & 255, g2 = (c2 >> 8) & 255, b2 = c2 & 255;
        int a = (int) (a1 + (a2 - a1) * t + 0.5f), r = (int) (r1 + (r2 - r1) * t + 0.5f);
        int g = (int) (g1 + (g2 - g1) * t + 0.5f), b = (int) (b1 + (b2 - b1) * t + 0.5f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
