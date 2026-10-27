package com.ringdown.harness;

import com.ringdown.core.Gfx;
import com.ringdown.core.Ribbon;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Java2D implementation of Gfx, used only for desktop previews of the wallpaper. */
public final class J2DGfx implements Gfx {
    private final Graphics2D g;
    private final Font[] base = new Font[4];
    private final Map<String, Font> cache = new HashMap<>();
    private static final Composite ADD = new AddComposite();
    private static final float[] GLOW_POS = {0f, 0.2f, 0.45f, 0.7f, 1f};
    private static final float[] GLOW_A = {1f, 0.62f, 0.26f, 0.07f, 0f};

    private final Ribbon rb = new Ribbon();
    private final int[] buf;
    private final int bw, bh;
    public boolean strokeLines = false;   // true: old Java2D strokes instead of the phone's mesh path

    public J2DGfx(BufferedImage img, String fontDir) throws Exception {
        buf = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        bw = img.getWidth();
        bh = img.getHeight();
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        String[] names = {"Roboto-Thin.ttf", "Roboto-Light.ttf", "Roboto-Regular.ttf", "Roboto-Medium.ttf"};
        for (int i = 0; i < 4; i++) base[i] = Font.createFont(Font.TRUETYPE_FONT, new File(fontDir, names[i]));
    }

    private static Color col(int c, float alpha) {
        int a = Math.round(((c >>> 24) & 255) * Math.max(0f, Math.min(1f, alpha)));
        return new Color((c >> 16) & 255, (c >> 8) & 255, c & 255, Math.max(0, Math.min(255, a)));
    }

    private void blend(boolean additive) {
        g.setComposite(additive ? ADD : AlphaComposite.SrcOver);
    }

    @Override public void background(int top, int bottom, float w, float h) {
        g.setComposite(AlphaComposite.Src);
        g.setPaint(new GradientPaint(0, 0, col(top, 1f), 0, h, col(bottom, 1f)));
        g.fill(new Rectangle2D.Float(0, 0, w, h));
    }

    @Override public void glow(float cx, float cy, float r, int color, float alpha) {
        if (r < 0.5f || alpha <= 0.002f) return;
        Color[] cs = new Color[GLOW_POS.length];
        for (int i = 0; i < cs.length; i++) cs[i] = col(color, Math.min(1f, alpha) * GLOW_A[i]);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r, GLOW_POS, cs));
        blend(true);
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
    }

    @Override public void lines(float[] pts, int count, int color, float alpha, float width, boolean additive) {
        if (count < 4 || alpha <= 0.002f) return;
        if (strokeLines) { strokeLines(pts, count, color, alpha, width, additive); return; }
        mesh(pts, count, color, alpha, width, false, additive);
    }

    @Override public void glowLines(float[] pts, int count, int color, float alpha, float width) {
        if (count < 4 || alpha <= 0.002f) return;
        if (strokeLines) { strokeLines(pts, count, color, alpha * 0.5f, width, true); return; }
        mesh(pts, count, color, alpha, width, true, true);
    }

    private void mesh(float[] pts, int count, int color, float alpha, float width, boolean soft, boolean additive) {
        int step = Ribbon.MAX_SEGMENTS_PER_BUILD * 4;
        for (int from = 0; from < count; from += step) {
            int to = Math.min(count, from + step);
            if (!rb.build(pts, from, to, color, alpha, width, soft)) continue;
            int rgb = color & 0xFFFFFF;
            int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;
            for (int t = 0; t < rb.in; t += 3) {
                int i0 = rb.ix[t] & 0xFFFF, i1 = rb.ix[t + 1] & 0xFFFF, i2 = rb.ix[t + 2] & 0xFFFF;
                tri(rb.v[2 * i0], rb.v[2 * i0 + 1], rb.c[i0] >>> 24,
                    rb.v[2 * i1], rb.v[2 * i1 + 1], rb.c[i1] >>> 24,
                    rb.v[2 * i2], rb.v[2 * i2 + 1], rb.c[i2] >>> 24, cr, cg, cb, additive);
            }
        }
    }

    /** Barycentric triangle fill with interpolated alpha (matches drawVertices on the phone). */
    private void tri(float x0, float y0, int a0, float x1, float y1, int a1, float x2, float y2, int a2,
                     int cr, int cg, int cb, boolean add) {
        if (a0 == 0 && a1 == 0 && a2 == 0) return;
        float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (Math.abs(area) < 1e-6f) return;
        float inv = 1f / area;
        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = Math.min(bw - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = Math.min(bh - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
        for (int py = minY; py <= maxY; py++) {
            float sy = py + 0.5f;
            int row = py * bw;
            for (int px = minX; px <= maxX; px++) {
                float sx = px + 0.5f;
                float w0 = ((x1 - sx) * (y2 - sy) - (x2 - sx) * (y1 - sy)) * inv;
                if (w0 < 0f) continue;
                float w1 = ((x2 - sx) * (y0 - sy) - (x0 - sx) * (y2 - sy)) * inv;
                if (w1 < 0f) continue;
                float w2 = 1f - w0 - w1;
                if (w2 < 0f) continue;
                float a = (w0 * a0 + w1 * a1 + w2 * a2) * (1f / 255f);
                if (a <= 0f) continue;
                int d = buf[row + px];
                int dr = (d >> 16) & 255, dg = (d >> 8) & 255, db = d & 255;
                if (add) {
                    dr = Math.min(255, dr + (int) (cr * a + 0.5f));
                    dg = Math.min(255, dg + (int) (cg * a + 0.5f));
                    db = Math.min(255, db + (int) (cb * a + 0.5f));
                } else {
                    dr = (int) (dr + (cr - dr) * a + 0.5f);
                    dg = (int) (dg + (cg - dg) * a + 0.5f);
                    db = (int) (db + (cb - db) * a + 0.5f);
                }
                buf[row + px] = (dr << 16) | (dg << 8) | db;
            }
        }
    }

    private void strokeLines(float[] pts, int count, int color, float alpha, float width, boolean additive) {
        Path2D.Float p = new Path2D.Float(Path2D.WIND_NON_ZERO, count / 2);
        for (int i = 0; i + 3 < count; i += 4) {
            p.moveTo(pts[i], pts[i + 1]);
            p.lineTo(pts[i + 2], pts[i + 3]);
        }
        g.setStroke(new BasicStroke(Math.max(0.3f, width), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setPaint(col(color, alpha));
        blend(additive);
        g.draw(p);
    }

    @Override public void dot(float cx, float cy, float r, int color, float alpha, boolean additive) {
        if (r <= 0.05f || alpha <= 0.002f) return;
        g.setPaint(col(color, alpha));
        blend(additive);
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
    }

    @Override public void points(float[] pts, int count, int color, float alpha, float size, boolean additive) {
        if (count < 2 || alpha <= 0.002f) return;
        Path2D.Float p = new Path2D.Float();
        float r = size * 0.5f;
        for (int i = 0; i + 1 < count; i += 2) p.append(new Ellipse2D.Float(pts[i] - r, pts[i + 1] - r, size, size), false);
        g.setPaint(col(color, alpha));
        blend(additive);
        g.fill(p);
    }

    @Override public void ring(float cx, float cy, float r, int color, float alpha, float width, boolean additive) {
        if (r <= 0.1f || alpha <= 0.002f) return;
        g.setStroke(new BasicStroke(Math.max(0.3f, width)));
        g.setPaint(col(color, alpha));
        blend(additive);
        g.draw(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
    }

    @Override public void rect(float l, float t, float r, float b, float radius, int color, float alpha) {
        g.setPaint(col(color, alpha));
        blend(false);
        g.fill(new RoundRectangle2D.Float(l, t, r - l, b - t, radius * 2, radius * 2));
    }

    @Override public void text(String s, float x, float y, float size, int color, float alpha, int font,
                               float tracking, int align) {
        if (s == null || s.isEmpty() || alpha <= 0.002f) return;
        String key = font + ":" + size + ":" + tracking;
        Font f = cache.get(key);
        if (f == null) {
            Map<TextAttribute, Object> at = new HashMap<>();
            at.put(TextAttribute.SIZE, size);
            at.put(TextAttribute.TRACKING, tracking);
            f = base[font].deriveFont(at);
            cache.put(key, f);
        }
        g.setFont(f);
        float w = (float) f.getStringBounds(s, g.getFontRenderContext()).getWidth();
        float dx = align == ALIGN_CENTER ? -w / 2f : (align == ALIGN_RIGHT ? -w : 0f);
        g.setPaint(col(color, alpha));
        blend(false);
        g.drawString(s, x + dx, y);
    }

    /** Saturating additive blend so glow looks like it will on the phone. */
    static final class AddComposite implements Composite {
        @Override public CompositeContext createContext(ColorModel srcCM, ColorModel dstCM, RenderingHints hints) {
            final boolean pre = srcCM.isAlphaPremultiplied();
            return new CompositeContext() {
                @Override public void dispose() {}
                @Override public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
                    int w = Math.min(src.getWidth(), dstIn.getWidth());
                    int h = Math.min(src.getHeight(), dstIn.getHeight());
                    int sb = src.getNumBands(), db = dstIn.getNumBands();
                    int[] sp = new int[w * sb], dp = new int[w * db];
                    for (int y = 0; y < h; y++) {
                        src.getPixels(src.getMinX(), src.getMinY() + y, w, 1, sp);
                        dstIn.getPixels(dstIn.getMinX(), dstIn.getMinY() + y, w, 1, dp);
                        for (int x = 0; x < w; x++) {
                            int a = sb >= 4 ? sp[x * sb + 3] : 255;
                            if (a == 0) continue;
                            float af = pre ? 1f : a / 255f;
                            for (int c = 0; c < 3; c++) {
                                int v = dp[x * db + c] + (int) (sp[x * sb + c] * af + 0.5f);
                                dp[x * db + c] = v > 255 ? 255 : v;
                            }
                        }
                        dstOut.setPixels(dstOut.getMinX(), dstOut.getMinY() + y, w, 1, dp);
                    }
                }
            };
        }
    }
}
