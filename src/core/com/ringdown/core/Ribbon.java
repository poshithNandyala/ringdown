package com.ringdown.core;

/**
 * Turns line segments into one triangle mesh with soft, antialiased edges (no GPU AA needed).
 * Consecutive segments that share endpoints become one continuous strip with mitered joins,
 * so rings have no overlaps or gaps at the joints (which would show up with additive blending).
 *
 * crisp: solid core with a 1px feather on each side.  soft: tent profile, for glow.
 * Output: v (x,y pairs, vn floats), c (one ARGB color per vertex), ix (triangle indices, in used).
 */
public final class Ribbon {
    public static final int MAX_SEGMENTS_PER_BUILD = 4000;   // keeps vertex count < 65536
    private static final float FEATHER = 1.0f;

    public float[] v = new float[8192];
    public int[] c = new int[4096];
    public short[] ix = new short[16384];
    public int vn, in;
    private int nv;

    private final float[] off = new float[4];
    private final int[] col = new int[4];
    private int across;

    /** Builds segments pts[from, to) (4 floats each). Returns false if nothing to draw. */
    public boolean build(float[] pts, int from, int to, int color, float alpha, float width, boolean soft) {
        vn = 0; in = 0; nv = 0;
        to -= (to - from) & 3;
        if (to - from < 4 || alpha <= 0.002f || !(width > 0f)) return false;
        float a = Math.min(1f, alpha) * ((color >>> 24) / 255f);
        int rgb = color & 0x00FFFFFF;
        if (soft) {
            across = 3;
            float h = width * 0.5f;
            off[0] = -h; off[1] = 0f; off[2] = h;
            col[0] = rgb; col[1] = argb(a, rgb); col[2] = rgb;
        } else {
            across = 4;
            float h = Math.max(0f, (width - FEATHER) * 0.5f);
            float core = width >= FEATHER ? a : a * width / FEATHER;
            off[0] = -(h + FEATHER); off[1] = -h; off[2] = h; off[3] = h + FEATHER;
            col[0] = rgb; col[1] = argb(core, rgb); col[2] = col[1]; col[3] = rgb;
        }
        int i = from;
        while (i < to) {
            int j = i;
            while (j + 4 < to && pts[j + 2] == pts[j + 4] && pts[j + 3] == pts[j + 5]) j += 4;
            emitRun(pts, i, (j - i) / 4 + 1);
            i = j + 4;
        }
        return in > 0;
    }

    private static int argb(float a, int rgb) {
        int ai = (int) (a * 255f + 0.5f);
        if (ai < 0) ai = 0; else if (ai > 255) ai = 255;
        return (ai << 24) | rgb;
    }

    /** One contiguous polyline of segs segments starting at pts[s]. */
    private void emitRun(float[] pts, int s, int segs) {
        int m = segs + 1;   // points
        float endX = pts[s + 4 * (segs - 1) + 2], endY = pts[s + 4 * (segs - 1) + 3];
        float dx0 = endX - pts[s], dy0 = endY - pts[s + 1];
        boolean closed = segs >= 3 && dx0 * dx0 + dy0 * dy0 < 0.01f;
        int pointCount = closed ? segs : m;
        ensure(pointCount * across, (closed ? pointCount : pointCount - 1) * (across - 1) * 6);
        int base = nv;
        for (int k = 0; k < pointCount; k++) {
            float x = px(pts, s, segs, k), y = py(pts, s, segs, k);
            // incoming / outgoing directions
            float ix0 = 0, iy0 = 0, ox = 0, oy = 0;
            boolean hasIn = false, hasOut = false;
            if (k > 0 || closed) {
                int p = k > 0 ? k - 1 : pointCount - 1;
                ix0 = x - px(pts, s, segs, p); iy0 = y - py(pts, s, segs, p);
                float l = (float) Math.sqrt(ix0 * ix0 + iy0 * iy0);
                if (l > 1e-4f) { ix0 /= l; iy0 /= l; hasIn = true; }
            }
            if (k < pointCount - 1 || closed) {
                int q = k < pointCount - 1 ? k + 1 : 0;
                ox = px(pts, s, segs, q) - x; oy = py(pts, s, segs, q) - y;
                float l = (float) Math.sqrt(ox * ox + oy * oy);
                if (l > 1e-4f) { ox /= l; oy /= l; hasOut = true; }
            }
            float tx, ty;
            if (hasIn && hasOut) { tx = ix0 + ox; ty = iy0 + oy; }
            else if (hasOut) { tx = ox; ty = oy; }
            else if (hasIn) { tx = ix0; ty = iy0; }
            else { tx = 1f; ty = 0f; }
            float tl = (float) Math.sqrt(tx * tx + ty * ty);
            if (tl < 1e-4f) { tx = hasOut ? ox : 1f; ty = hasOut ? oy : 0f; tl = 1f; }
            tx /= tl; ty /= tl;
            float nx = -ty, ny = tx;
            float miter = 1f;
            if (hasIn && hasOut) {
                float d = nx * -oy + ny * ox;           // cos of half the turn angle
                miter = 1f / Math.max(0.35f, d);
            }
            for (int a = 0; a < across; a++) {
                float o = off[a] * miter;
                v[vn++] = x + nx * o;
                v[vn++] = y + ny * o;
                c[nv++] = col[a];
            }
        }
        int quads = closed ? pointCount : pointCount - 1;
        for (int k = 0; k < quads; k++) {
            int r0 = base + k * across;
            int r1 = base + ((k + 1) % pointCount) * across;
            for (int a = 0; a < across - 1; a++) {
                ix[in++] = (short) (r0 + a);
                ix[in++] = (short) (r1 + a);
                ix[in++] = (short) (r0 + a + 1);
                ix[in++] = (short) (r0 + a + 1);
                ix[in++] = (short) (r1 + a);
                ix[in++] = (short) (r1 + a + 1);
            }
        }
    }

    private static float px(float[] pts, int s, int segs, int k) {
        return k < segs ? pts[s + 4 * k] : pts[s + 4 * (segs - 1) + 2];
    }

    private static float py(float[] pts, int s, int segs, int k) {
        return k < segs ? pts[s + 4 * k + 1] : pts[s + 4 * (segs - 1) + 3];
    }

    private void ensure(int moreVerts, int moreIdx) {
        if (vn + moreVerts * 2 > v.length) {
            float[] nvv = new float[Math.max(v.length * 2, vn + moreVerts * 2)];
            System.arraycopy(v, 0, nvv, 0, vn);
            v = nvv;
        }
        if (nv + moreVerts > c.length) {
            int[] nc = new int[Math.max(c.length * 2, nv + moreVerts)];
            System.arraycopy(c, 0, nc, 0, nv);
            c = nc;
        }
        if (in + moreIdx > ix.length) {
            short[] ni = new short[Math.max(ix.length * 2, in + moreIdx)];
            System.arraycopy(ix, 0, ni, 0, in);
            ix = ni;
        }
    }
}
