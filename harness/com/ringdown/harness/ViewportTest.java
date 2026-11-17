package com.ringdown.harness;

import com.ringdown.core.Viewport;

import java.util.Random;

/**
 * Tests for Viewport: which part of the wallpaper surface is on screen.
 *   java ViewportTest
 */
public final class ViewportTest {
    private static int passed, failed;

    public static void main(String[] args) {
        // The phone from the closed test: portrait surface, screen size reported in landscape.
        // 1.3.1 squeezed the scene into a 687 x 687 square here (the color band and tiny text).
        check("screen reported sideways (the color band bug)", 687, 1536, 1536, 687, 687, 1536);
        check("normal phone", 1080, 2400, 1080, 2400, 1080, 2400);
        check("normal phone, screen reported sideways", 1080, 2400, 2400, 1080, 1080, 2400);
        check("scrolling launcher, 2 screens wide", 2160, 1920, 1080, 1920, 1080, 1920);
        check("scrolling launcher, screen reported sideways", 2160, 1920, 1920, 1080, 1080, 1920);
        check("tablet, square surface, landscape", 2560, 2560, 2560, 1600, 2560, 1600);
        check("tablet, square surface, portrait", 2560, 2560, 1600, 2560, 1600, 2560);
        check("landscape tablet", 2560, 1600, 2560, 1600, 2560, 1600);
        check("surface a little shorter than the screen", 1080, 2340, 1080, 2400, 1080, 2340);
        check("square scrolling surface", 2160, 2160, 1080, 1920, 1080, 1920);
        check("foldable inner screen reported sideways", 1840, 2208, 2208, 1840, 1840, 2208);
        check("screen size unknown", 1080, 2400, 0, 0, 1080, 2400);
        randomized();
        System.out.printf("%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    /**
     * When the screen size comes the right way round, the result is exactly what 1.3.1 did.
     * When it comes sideways, the result is what 1.3.1 did with the right numbers. A (nearly)
     * square surface can't tell which way the screen is, so there it must at least do no worse
     * than 1.3.1 did with the numbers it was given.
     */
    private static void randomized() {
        Random r = new Random(7);
        int same = 0, sideways = 0, ambiguous = 0, n = 200_000;
        for (int i = 0; i < n; i++) {
            int a = 320 + r.nextInt(3000), b = 320 + r.nextInt(3000);
            if (Math.max(a, b) < Math.min(a, b) * 1.1f) continue;   // near-square screens: ambiguous
            int dw = a, dh = b;
            int sw, sh;
            switch (r.nextInt(4)) {
                case 0: sw = dw; sh = dh; break;                                // usual
                case 1: sw = dw * 2; sh = dh; break;                            // scrolling launcher
                case 2: sw = dw; sh = Math.round(dh * (0.96f + r.nextFloat() * 0.04f)); break;  // a bit short
                default: sw = Math.max(dw, dh); sh = sw; break;                 // square surface
            }
            int[] want = before(sw, sh, dw, dh);
            if (eq(Viewport.visible(sw, sh, dw, dh), want)) same++;
            else fail(String.format("right way round %dx%d on %dx%d", sw, sh, dw, dh), Viewport.visible(sw, sh, dw, dh), want);
            int[] got = Viewport.visible(sw, sh, dh, dw);
            if (Math.max(sw, sh) < Math.min(sw, sh) * 1.1f) {
                if (eq(got, want) || eq(got, before(sw, sh, dh, dw))) ambiguous++;
                else fail(String.format("square-ish %dx%d on %dx%d", sw, sh, dh, dw), got, want);
                continue;
            }
            if (eq(got, want)) sideways++;
            else fail(String.format("sideways %dx%d on %dx%d", sw, sh, dh, dw), got, want);
        }
        System.out.printf("randomized: %d same as 1.3.1, %d sideways cases fixed, %d square-ish no worse%n",
                same, sideways, ambiguous);
        passed++;
    }

    /** What 1.3.1 computed. */
    private static int[] before(int sw, int sh, int dw, int dh) {
        int w = (dw > 0 && sw > dw * 1.05f) ? dw : sw;
        int h = (dh > 0 && sh > dh * 1.05f) ? dh : sh;
        return new int[] {w, h};
    }

    private static void check(String name, int sw, int sh, int dw, int dh, int ww, int wh) {
        int[] got = Viewport.visible(sw, sh, dw, dh);
        if (eq(got, new int[] {ww, wh})) passed++;
        else fail(name, got, new int[] {ww, wh});
    }

    private static boolean eq(int[] x, int[] y) {
        return x[0] == y[0] && x[1] == y[1];
    }

    private static void fail(String name, int[] got, int[] want) {
        if (failed++ < 20) System.out.printf("FAIL %s: got %dx%d, want %dx%d%n", name, got[0], got[1], want[0], want[1]);
    }
}
