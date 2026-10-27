package com.ringdown.core;

/**
 * Minimal drawing surface the scene renders into. Implemented with android.graphics.Canvas
 * on the phone and with Java2D for desktop previews, so the scene itself stays platform-free.
 * Colors are 0xAARRGGBB; the extra alpha argument (0..1) multiplies the color's own alpha.
 * "Additive" draws add light onto what is already there (glow look).
 */
public interface Gfx {
    int FONT_THIN = 0, FONT_LIGHT = 1, FONT_REGULAR = 2, FONT_MEDIUM = 3;
    int ALIGN_LEFT = 0, ALIGN_CENTER = 1, ALIGN_RIGHT = 2;

    /** Fill the whole surface with a vertical gradient. */
    void background(int topColor, int bottomColor, float w, float h);

    /** Soft additive radial glow, brightest at the center, gone at radius r. */
    void glow(float cx, float cy, float r, int color, float alpha);

    /** Crisp line segments; pts holds x0,y0,x1,y1 groups, count is the number of floats used. */
    void lines(float[] pts, int count, int color, float alpha, float width, boolean additive);

    /** Soft additive glow lines: brightest (alpha) along the middle, fading to 0 at width/2. */
    void glowLines(float[] pts, int count, int color, float alpha, float width);

    /** Filled circle. */
    void dot(float cx, float cy, float r, int color, float alpha, boolean additive);

    /** Round points; pts holds x,y pairs, count is the number of floats used; size is the diameter. */
    void points(float[] pts, int count, int color, float alpha, float size, boolean additive);

    /** Stroked circle. */
    void ring(float cx, float cy, float r, int color, float alpha, float width, boolean additive);

    /** Filled rounded rectangle (normal blending). */
    void rect(float l, float t, float r, float b, float radius, int color, float alpha);

    /** Text with baseline at y. tracking is letter spacing in em. */
    void text(String s, float x, float y, float size, int color, float alpha, int font, float tracking, int align);
}
