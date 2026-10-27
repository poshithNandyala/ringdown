package com.poshith.ringdown;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;

import com.ringdown.core.Gfx;
import com.ringdown.core.Ribbon;

import java.util.HashMap;

/** android.graphics implementation of the scene's drawing surface. Allocation-free per frame. */
final class AndroidGfx implements Gfx {
    private static final float[] GLOW_POS = {0f, 0.2f, 0.45f, 0.7f, 1f};
    private static final float[] GLOW_A = {1f, 0.62f, 0.26f, 0.07f, 0f};

    private Canvas c;
    private boolean mesh;                       // draw lines as one triangle mesh (fast path)
    private final Ribbon rb = new Ribbon();
    private final Paint meshP = new Paint();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint bgP = new Paint();
    private final PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);
    private final Typeface[] faces = new Typeface[4];
    private final HashMap<Integer, RadialGradient> glows = new HashMap<>();
    private LinearGradient bgShader;
    private int bgTop, bgBottom;
    private float bgH = -1f;

    AndroidGfx(Context ctx) {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        meshP.setColor(0xFFFFFFFF);
        fill.setStyle(Paint.Style.FILL);
        glowP.setStyle(Paint.Style.FILL);
        glowP.setXfermode(add);
        // Bundled Roboto so the wallpaper looks the same on every phone (OEM fonts differ a lot).
        faces[FONT_THIN] = font(ctx, "Roboto-Thin.ttf", "sans-serif-thin");
        faces[FONT_LIGHT] = font(ctx, "Roboto-Light.ttf", "sans-serif-light");
        faces[FONT_REGULAR] = font(ctx, "Roboto-Regular.ttf", "sans-serif");
        faces[FONT_MEDIUM] = font(ctx, "Roboto-Medium.ttf", "sans-serif-medium");
    }

    private static Typeface font(Context ctx, String file, String fallback) {
        try {
            return Typeface.createFromAsset(ctx.getAssets(), "fonts/" + file);
        } catch (Throwable t) {
            return Typeface.create(fallback, Typeface.NORMAL);
        }
    }

    /**
     * Hardware canvases get drawVertices from Android 10 on; before that, thick antialiased
     * drawLines is the fallback (butt caps keep it on Skia's fast rectangle path).
     */
    void setCanvas(Canvas canvas) {
        c = canvas;
        mesh = !canvas.isHardwareAccelerated() || Build.VERSION.SDK_INT >= 29;
    }

    private static int alpha(int color, float a) {
        float v = ((color >>> 24) & 255) * a;
        return v <= 0f ? 0 : (v >= 255f ? 255 : (int) (v + 0.5f));
    }

    private void prep(Paint p, int color, float a, boolean additive) {
        p.setColor(color);
        p.setAlpha(alpha(color, a));
        p.setXfermode(additive ? add : null);
    }

    @Override public void background(int top, int bottom, float w, float h) {
        if (bgShader == null || top != bgTop || bottom != bgBottom || h != bgH) {
            bgShader = new LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP);
            bgTop = top; bgBottom = bottom; bgH = h;
            bgP.setShader(bgShader);
        }
        c.drawRect(0f, 0f, w, h, bgP);
    }

    @Override public void glow(float cx, float cy, float r, int color, float a) {
        if (r < 0.5f || a <= 0.002f) return;
        int key = color | 0xFF000000;
        RadialGradient sh = glows.get(key);
        if (sh == null) {
            int[] cols = new int[GLOW_POS.length];
            int rgb = key & 0x00FFFFFF;
            for (int i = 0; i < cols.length; i++) cols[i] = ((int) (GLOW_A[i] * 255f + 0.5f) << 24) | rgb;
            sh = new RadialGradient(0f, 0f, 1f, cols, GLOW_POS, Shader.TileMode.CLAMP);
            glows.put(key, sh);
        }
        glowP.setShader(sh);
        glowP.setAlpha(alpha(0xFF000000, Math.min(1f, a)));
        c.save();
        c.translate(cx, cy);
        c.scale(r, r);
        c.drawCircle(0f, 0f, 1f, glowP);
        c.restore();
    }

    @Override public void lines(float[] pts, int count, int color, float a, float width, boolean additive) {
        count -= count & 3;
        if (count < 4 || a <= 0.002f) return;
        if (mesh) { drawMesh(pts, count, color, a, width, false, additive); return; }
        prep(line, color, a, additive);
        line.setStrokeCap(Paint.Cap.BUTT);
        line.setStrokeWidth(Math.max(0.3f, width));
        c.drawLines(pts, 0, count, line);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override public void glowLines(float[] pts, int count, int color, float a, float width) {
        count -= count & 3;
        if (count < 4 || a <= 0.002f) return;
        if (mesh) { drawMesh(pts, count, color, a, width, true, true); return; }
        lines(pts, count, color, a * 0.5f, width, true);
    }

    private void drawMesh(float[] pts, int count, int color, float a, float width, boolean soft, boolean additive) {
        meshP.setXfermode(additive ? add : null);
        int step = Ribbon.MAX_SEGMENTS_PER_BUILD * 4;
        for (int from = 0; from < count; from += step) {
            if (!rb.build(pts, from, Math.min(count, from + step), color, a, width, soft)) continue;
            c.drawVertices(Canvas.VertexMode.TRIANGLES, rb.vn, rb.v, 0, null, 0, rb.c, 0, rb.ix, 0, rb.in, meshP);
        }
    }

    @Override public void dot(float cx, float cy, float r, int color, float a, boolean additive) {
        if (r <= 0.05f || a <= 0.002f) return;
        prep(fill, color, a, additive);
        c.drawCircle(cx, cy, r, fill);
    }

    @Override public void points(float[] pts, int count, int color, float a, float size, boolean additive) {
        count -= count & 1;
        if (count < 2 || a <= 0.002f) return;
        prep(line, color, a, additive);
        line.setStrokeWidth(Math.max(0.5f, size));
        c.drawPoints(pts, 0, count, line);
    }

    @Override public void ring(float cx, float cy, float r, int color, float a, float width, boolean additive) {
        if (r <= 0.1f || a <= 0.002f) return;
        prep(line, color, a, additive);
        line.setStrokeWidth(Math.max(0.3f, width));
        c.drawCircle(cx, cy, r, line);
    }

    @Override public void rect(float l, float t, float r, float b, float radius, int color, float a) {
        if (a <= 0.002f) return;
        prep(fill, color, a, false);
        c.drawRoundRect(l, t, r, b, radius, radius, fill);
    }

    @Override public void text(String s, float x, float y, float size, int color, float a, int font,
                               float tracking, int align) {
        if (s == null || s.length() == 0 || a <= 0.002f) return;
        textP.setTypeface(faces[font]);
        textP.setTextSize(size);
        textP.setLetterSpacing(tracking);
        textP.setTextAlign(align == ALIGN_CENTER ? Paint.Align.CENTER : (align == ALIGN_RIGHT ? Paint.Align.RIGHT : Paint.Align.LEFT));
        textP.setColor(color);
        textP.setAlpha(alpha(color, a));
        c.drawText(s, x, y, textP);
    }
}
