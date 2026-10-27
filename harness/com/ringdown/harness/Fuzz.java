package com.ringdown.harness;

import com.ringdown.core.Gfx;
import com.ringdown.core.RingScene;
import com.ringdown.core.SceneConfig;

import java.util.Random;

/** Hammers the scene with random calls; checks invariants and measures per-frame draw work. */
public final class Fuzz {
    static final class CountGfx implements Gfx {
        long calls, segs, pts;
        private void chk(float... v) { for (float f : v) if (Float.isNaN(f) || Float.isInfinite(f)) throw new IllegalStateException("NaN/Inf in draw args"); }
        @Override public void background(int t, int b, float w, float h) { calls++; chk(w, h); }
        @Override public void glow(float x, float y, float r, int c, float a) { calls++; chk(x, y, r, a); }
        @Override public void lines(float[] p, int n, int c, float a, float w, boolean add) {
            calls++; segs += n / 4; chk(a, w);
            for (int i = 0; i < n; i++) if (Float.isNaN(p[i]) || Float.isInfinite(p[i])) throw new IllegalStateException("NaN in line pts");
        }
        final com.ringdown.core.Ribbon rb = new com.ringdown.core.Ribbon();
        long tris;
        @Override public void glowLines(float[] p, int n, int c, float a, float w) {
            lines(p, n, c, a, w, true);
            if (rb.build(p, 0, Math.min(n, com.ringdown.core.Ribbon.MAX_SEGMENTS_PER_BUILD * 4), c, a, w, true)) tris += rb.in / 3;
        }
        @Override public void dot(float x, float y, float r, int c, float a, boolean add) { calls++; chk(x, y, r, a); }
        @Override public void points(float[] p, int n, int c, float a, float s, boolean add) {
            calls++; pts += n / 2; chk(a, s);
            for (int i = 0; i < n; i++) if (Float.isNaN(p[i])) throw new IllegalStateException("NaN in points");
        }
        @Override public void ring(float x, float y, float r, int c, float a, float w, boolean add) { calls++; chk(x, y, r, a, w); }
        @Override public void rect(float l, float t, float r, float b, float rad, int c, float a) { calls++; chk(l, t, r, b); }
        @Override public void text(String s, float x, float y, float sz, int c, float a, int f, float tr, int al) {
            calls++; chk(x, y, sz, a);
            if (s == null) throw new IllegalStateException("null text");
        }
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 1;
        Random r = new Random(seed);
        CountGfx g = new CountGfx();
        long maxCalls = 0, maxSegs = 0;
        int[] totals = {1, 2, 3, 7, 30, 66, 90, 100, 200, 365};
        for (int run = 0; run < 60; run++) {
            RingScene s = new RingScene();
            SceneConfig c = new SceneConfig();
            c.total = totals[r.nextInt(totals.length)];
            c.style = r.nextInt(3);
            c.mode = r.nextInt(2);
            c.palette = r.nextInt(6);
            c.goal = r.nextBoolean() ? "No sugar" : "";
            c.size = 0.7f + r.nextFloat() * 0.6f;
            c.pos = r.nextFloat() * 2 - 1;
            c.lowPower = r.nextInt(4) == 0;
            s.setConfig(c);
            s.setViewport(r.nextBoolean() ? 1080 : 2340, r.nextBoolean() ? 2340 : 1080);
            int left = r.nextInt(c.total + 1);
            s.setProgress(left, r.nextInt(3) == 0 ? r.nextInt(5) : 0);
            for (int f = 0; f < 2400; f++) {
                int ev = r.nextInt(400);
                if (ev == 0) { left = Math.max(0, left - 1 - r.nextInt(3)); s.animateTo(left, 0, 0.7f); }
                else if (ev == 1) s.playDemo();
                else if (ev == 2) s.tap(r.nextFloat() * 1080, r.nextFloat() * 2340);
                else if (ev == 3) { c.style = r.nextInt(3); if (r.nextInt(3) == 0) c.mode = 1 - c.mode; s.setConfig(c); s.setProgress(left, 0); }
                else if (ev == 4) { c.palette = r.nextInt(6); c.size = 0.7f + r.nextFloat() * 0.6f; s.setConfig(c); }
                else if (ev == 5) { c.total = totals[r.nextInt(totals.length)]; s.setConfig(c); left = Math.min(left, c.total); s.setProgress(left, 0); }
                else if (ev == 6) { left = r.nextInt(c.total + 1); s.setProgress(left, 0); }
                else if (ev == 7) s.setViewport(720 + r.nextInt(1500), 720 + r.nextInt(2000));
                else if (ev == 8) s.setParallax(r.nextFloat());
                else if (ev == 9) s.setNotice(r.nextBoolean() ? null : (r.nextBoolean() ? "New version available" : "x".repeat(r.nextInt(80))));
                s.update(r.nextInt(50) == 0 ? 0.2f : 1f / 30f);
                g.calls = 0; g.segs = 0; g.pts = 0;
                s.draw(g);
                maxCalls = Math.max(maxCalls, g.calls);
                maxSegs = Math.max(maxSegs, g.segs);
                int L = s.ringsLeft();
                if (L < 0 || L > c.total) throw new IllegalStateException("left out of range " + L);
            }
            // let animations finish: the scene must settle on the calendar's count
            for (int f = 0; f < 900; f++) { s.update(1f / 30f); s.draw(g); }
            if (s.ringsLeft() != s.targetLeft()) throw new IllegalStateException("did not settle: " + s.ringsLeft() + " vs " + s.targetLeft());
            if (s.targetLeft() != Math.min(left, c.total)) throw new IllegalStateException("target mismatch " + s.targetLeft() + " vs " + left);
        }
        System.out.println("fuzz ok seed=" + seed + " maxDrawCalls/frame=" + maxCalls + " maxSegments/frame=" + maxSegs);

        // typical idle frame cost per style (90 rings)
        for (int st = 0; st < 6; st++) {
            RingScene s = new RingScene();
            SceneConfig c = new SceneConfig(); c.style = st % 3; c.mode = st / 3; s.setConfig(c); s.setViewport(1080, 2340); s.setProgress(st / 3 == 0 ? 78 : 40, 0);
            for (int f = 0; f < 60; f++) s.update(1f / 30f);
            g.calls = 0; g.segs = 0; g.pts = 0; s.draw(g);
            long t0 = System.nanoTime();
            for (int f = 0; f < 300; f++) { s.update(1f / 30f); s.draw(new CountGfx()); }
            System.out.printf("%s %s idle: %d draw calls, %d segments, %d points, %.2f ms/frame cpu (desktop)%n",
                    st / 3 == 0 ? "break" : "build", RingScene.STYLE_NAMES[st % 3], g.calls, g.segs, g.pts, (System.nanoTime() - t0) / 300 / 1e6);
        }
    }
}
