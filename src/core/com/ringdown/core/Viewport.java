package com.ringdown.core;

/**
 * Works out which part of the wallpaper surface is actually on screen.
 *
 * Some launchers make the wallpaper surface wider than the screen and scroll it with the home
 * screen pages, so only a screen-sized part of it shows at a time. The screen size comes from
 * Android separately, and right after a rotation or on the lock screen it can come back the other
 * way round (landscape numbers for a portrait surface). Taken as is, a 687 x 1536 surface compared
 * with a 1536 x 687 screen looks "taller than the screen": the scene gets squeezed into a 687 x 687
 * square in the middle, the rest is painted flat (a sharp color band) and the text comes out tiny.
 * So the screen size is turned around first when only the turned size can belong to the surface.
 */
public final class Viewport {
    private Viewport() {}

    /** Returns {width, height} of the visible part of a surfaceW x surfaceH wallpaper surface. */
    public static int[] visible(int surfaceW, int surfaceH, int displayW, int displayH) {
        int dw = displayW, dh = displayH;
        if (!matches(surfaceW, surfaceH, dw, dh) && matches(surfaceW, surfaceH, dh, dw)) {
            dw = displayH;
            dh = displayW;
        }
        int w = dw > 0 && surfaceW > dw * 1.05f ? dw : surfaceW;
        int h = dh > 0 && surfaceH > dh * 1.05f ? dh : surfaceH;
        return new int[] {w, h};
    }

    /**
     * Can a dw x dh screen be showing this surface? The surface must cover it, and a launcher that
     * scrolls the wallpaper stretches the surface along one side only, so the other side is the same
     * size as the screen. If neither way round fits, the screen size is used as given.
     */
    private static boolean matches(int surfaceW, int surfaceH, int dw, int dh) {
        if (dw <= 0 || dh <= 0) return false;
        boolean covers = surfaceW >= dw * 0.95f && surfaceH >= dh * 0.95f;
        return covers && (near(surfaceW, dw) || near(surfaceH, dh));
    }

    private static boolean near(int a, int b) {
        return Math.abs(a - b) <= b * 0.05f;
    }
}
