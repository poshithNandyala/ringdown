package com.poshith.ringdown;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.service.wallpaper.WallpaperService;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.SurfaceHolder;

import com.ringdown.core.Countdown;
import com.ringdown.core.Palette;
import com.ringdown.core.RemoteRules;
import com.ringdown.core.RingScene;
import com.ringdown.core.SceneConfig;

/**
 * The live wallpaper. Each time it becomes visible it checks the calendar; if midnight has
 * passed since the rings were last shown, it plays the animation for the missing day(s).
 */
public final class RingsWallpaperService extends WallpaperService {
    private static final String TAG = "Ringdown";

    @Override
    public Engine onCreateEngine() {
        return new RingsEngine();
    }

    private final class RingsEngine extends Engine implements Choreographer.FrameCallback,
            SharedPreferences.OnSharedPreferenceChangeListener, RingScene.Listener {

        private final RingScene scene = new RingScene();
        private final AndroidGfx gfx = new AndroidGfx(RingsWallpaperService.this);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private SharedPreferences prefs;
        private boolean visible, surfaceReady, looping, hwFailed, synced, haptics = true, lowPower;
        private long lastFrame, lastDraw, lastDayCheck;
        private long shownDay = Long.MIN_VALUE;
        private int width, height;          // surface size
        private int viewW, viewH;           // the part of the surface that is on screen
        private float offX = 0.5f, offY = 0.5f;
        private int paletteIdx = -1;
        private long lastTapAt;
        private int lastTapX, lastTapY;

        /** In the system picker preview, show off the animation now and then. */
        private final Runnable demo = new Runnable() {
            @Override public void run() {
                if (visible) {
                    scene.playDemo();
                    startLoop();
                }
                handler.postDelayed(this, 11_000);
            }
        };

        private final Runnable requestedDemo = new Runnable() {
            @Override public void run() {
                if (visible) {
                    scene.playDemo();
                    startLoop();
                }
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            prefs = Prefs.get(RingsWallpaperService.this);
            prefs.registerOnSharedPreferenceChangeListener(this);
            scene.setListener(this);
            setOffsetNotificationsEnabled(true);
            loadConfig();
            loadNotice();
        }

        /** The optional line under the progress bar, as the remote config asks (e.g. an update). */
        private void loadNotice() {
            if (isPreview()) {
                scene.setNotice(null);
                return;
            }
            try {
                scene.setNotice(RemoteRules.wallpaperNotice(Remote.config(prefs),
                        Remote.device(RingsWallpaperService.this, prefs), Remote.set(prefs, Remote.K_DISMISSED)));
            } catch (Throwable t) {
                scene.setNotice(null);
            }
        }

        @Override
        public void onDestroy() {
            if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(this);
            handler.removeCallbacksAndMessages(null);
            stopLoop();
            super.onDestroy();
        }

        private boolean powerSave() {
            try {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                return pm != null && pm.isPowerSaveMode();
            } catch (Throwable t) {
                return false;
            }
        }

        private void loadConfig() {
            SceneConfig c = Prefs.config(prefs);
            // Android 8/9 has no fast mesh path on hardware canvases: draw lighter there too.
            c.lowPower = c.lowPower || powerSave() || Build.VERSION.SDK_INT < 29;
            lowPower = c.lowPower;
            haptics = prefs.getBoolean(Prefs.K_HAPTIC, true);
            scene.setConfig(c);
            if (c.palette != paletteIdx) {
                paletteIdx = c.palette;
                if (Build.VERSION.SDK_INT >= 27) {
                    try { notifyColorsChanged(); } catch (Throwable ignored) {}
                }
            }
        }

        /** Compare the calendar with what was last shown and animate the difference. */
        private void syncDay(boolean animate) {
            long today = Countdown.today();
            shownDay = today;
            int left = Prefs.ringsLeft(prefs, today);
            int startsIn = Prefs.startsIn(prefs, today);
            if (isPreview()) {
                if (!synced || scene.targetLeft() != left) scene.setProgress(left, startsIn);
                synced = true;
                return;
            }
            int total = Prefs.total(prefs);
            int seen = prefs.getInt(Prefs.K_SEEN, -1);
            if (animate && seen >= 0 && seen <= total && left < seen) {
                if (!synced) scene.setProgress(seen, startsIn);
                scene.animateTo(left, startsIn, 0.7f);
                // K_SEEN is saved as each day's animation actually plays (onRingBurst), so one
                // the user never got to watch is replayed next time.
            } else {
                if (!synced || scene.targetLeft() != left) scene.setProgress(left, startsIn);
                else scene.animateTo(left, startsIn, 0f);   // refreshes the "starts in" label
                if (seen != left) prefs.edit().putInt(Prefs.K_SEEN, left).apply();
            }
            synced = true;
        }

        /**
         * Before the first time we're visible, show the count the user last saw (not today's),
         * so the animation still plays when they look. Writes nothing.
         */
        private void showLastSeen() {
            long today = Countdown.today();
            int left = Prefs.ringsLeft(prefs, today);
            int startsIn = Prefs.startsIn(prefs, today);
            int seen = prefs.getInt(Prefs.K_SEEN, -1);
            if (!isPreview() && seen >= 0 && seen <= Prefs.total(prefs) && left < seen) scene.setProgress(seen, startsIn);
            else scene.setProgress(left, startsIn);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            if (v) {
                if (powerSave() != lowPower && !prefs.getBoolean(Prefs.K_LOW, false) && Build.VERSION.SDK_INT >= 29) loadConfig();
                syncDay(true);
                lastFrame = 0;
                drawFrame(System.nanoTime());
                startLoop();
                if (isPreview()) {
                    handler.removeCallbacks(demo);
                    handler.postDelayed(demo, 1_400);
                } else {
                    loadNotice();
                    Remote.refresh(RingsWallpaperService.this, Remote.maxAge(prefs));
                    long asked = prefs.getLong(Prefs.K_DEMO_AT, 0L);
                    if (asked > 0L) {
                        prefs.edit().putLong(Prefs.K_DEMO_AT, 0L).apply();
                        if (Math.abs(System.currentTimeMillis() - asked) < 30_000L) {
                            handler.removeCallbacks(requestedDemo);
                            handler.postDelayed(requestedDemo, 700);
                        }
                    }
                }
            } else {
                stopLoop();
                handler.removeCallbacks(demo);
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            surfaceReady = true;
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            surfaceReady = true;
            width = w;
            height = h;
            updateViewport();
            if (!synced) showLastSeen();
            drawFrame(System.nanoTime());
        }

        /**
         * Some launchers make the wallpaper surface wider than the screen and scroll it with
         * the home screen pages. Lay the scene out for the visible screen only, and follow the
         * scroll offset when drawing, so it always sits centered on screen.
         */
        private void updateViewport() {
            int sw = 0, sh = 0;
            try {
                DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
                Display d = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
                if (d != null) {
                    DisplayMetrics m = new DisplayMetrics();
                    d.getRealMetrics(m);
                    sw = m.widthPixels;
                    sh = m.heightPixels;
                }
            } catch (Throwable ignored) {
            }
            viewW = (sw > 0 && width > sw * 1.05f) ? sw : width;
            viewH = (sh > 0 && height > sh * 1.05f) ? sh : height;
            scene.setViewport(viewW, viewH);
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            super.onSurfaceRedrawNeeded(holder);
            drawFrame(System.nanoTime());
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            stopLoop();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xStep, float yStep, int xPx, int yPx) {
            if (!Float.isNaN(xOffset)) offX = Math.max(0f, Math.min(1f, xOffset));
            if (!Float.isNaN(yOffset)) offY = Math.max(0f, Math.min(1f, yOffset));
            scene.setParallax(xOffset);
            if (visible && (viewW < width || viewH < height)) drawFrame(System.nanoTime());
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (WallpaperManager.COMMAND_TAP.equals(action)) {
                float sx = x - offX * (width - viewW), sy = y - offY * (height - viewH);
                long now = SystemClock.uptimeMillis();
                boolean dbl = now - lastTapAt < 380 && Math.abs(x - lastTapX) < 120 && Math.abs(y - lastTapY) < 120;
                lastTapAt = dbl ? 0 : now;
                lastTapX = x;
                lastTapY = y;
                scene.tap(sx, sy);
                if (dbl) scene.playDemo();   // double-tap the home screen to replay the animation
                startLoop();
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        @Override
        public WallpaperColors onComputeColors() {
            Palette p = scene.palette();
            if (Build.VERSION.SDK_INT >= 31) {
                return new WallpaperColors(Color.valueOf(p.a), Color.valueOf(p.b), Color.valueOf(p.bgBottom),
                        WallpaperColors.HINT_SUPPORTS_DARK_THEME);
            }
            return new WallpaperColors(Color.valueOf(p.a), Color.valueOf(p.b), Color.valueOf(p.bgBottom));
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
            if (key != null && key.startsWith(Remote.PREFIX)) {   // remote config: only the notice can change
                loadNotice();
                if (visible) startLoop();
                return;
            }
            if (Prefs.K_SEEN.equals(key) || Prefs.K_DEMO_AT.equals(key) || Prefs.K_GUIDE.equals(key)
                    || Prefs.K_HABIT.equals(key)) return;
            loadConfig();
            if (key == null || Prefs.K_MODE.equals(key)) loadNotice();   // messages can target a mode
            if (key == null || Prefs.K_TOTAL.equals(key) || Prefs.K_START.equals(key) || Prefs.K_MODE.equals(key)) {
                long today = Countdown.today();
                int left = Prefs.ringsLeft(sp, today);
                scene.setProgress(left, Prefs.startsIn(sp, today));
                if (!isPreview()) sp.edit().putInt(Prefs.K_SEEN, left).apply();
            }
            if (visible) startLoop();
        }

        @Override
        public void onRingBurst(boolean milestone, boolean isDemo) {
            if (isDemo || isPreview()) return;
            prefs.edit().putInt(Prefs.K_SEEN, scene.ringsLeft()).apply();
            if (!haptics) return;
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v == null || !v.hasVibrator()) return;
                if (Build.VERSION.SDK_INT >= 29) {
                    v.vibrate(VibrationEffect.createPredefined(milestone
                            ? VibrationEffect.EFFECT_DOUBLE_CLICK : VibrationEffect.EFFECT_HEAVY_CLICK));
                } else {
                    v.vibrate(VibrationEffect.createOneShot(milestone ? 45 : 28, VibrationEffect.DEFAULT_AMPLITUDE));
                }
            } catch (Throwable ignored) {
            }
        }

        // ------------------------------------------------------------------ frame loop

        private void startLoop() {
            if (looping || !visible) return;
            looping = true;
            Choreographer.getInstance().postFrameCallback(this);
        }

        private void stopLoop() {
            looping = false;
            Choreographer.getInstance().removeFrameCallback(this);
        }

        @Override
        public void doFrame(long now) {
            if (!looping) return;
            if (!visible || !surfaceReady) {
                looping = false;
                return;
            }
            long interval = scene.isAnimating() ? 15_500_000L : (lowPower ? 48_000_000L : 31_000_000L);
            if (now - lastDraw >= interval) {
                drawFrame(now);
                lastDraw = now;
            }
            if (now - lastDayCheck > 2_000_000_000L) {
                lastDayCheck = now;
                if (Countdown.today() != shownDay) syncDay(true);   // midnight while on screen
            }
            if (looping) Choreographer.getInstance().postFrameCallback(this);
        }

        private void drawFrame(long now) {
            if (!surfaceReady || width <= 0 || height <= 0) return;
            float dt = lastFrame == 0 ? 0f : (now - lastFrame) / 1e9f;
            lastFrame = now;
            scene.update(dt);
            SurfaceHolder holder = getSurfaceHolder();
            Canvas c = null;
            try {
                if (!hwFailed) {
                    try {
                        c = holder.lockHardwareCanvas();
                    } catch (Throwable t) {
                        c = holder.lockCanvas();
                        if (c != null) hwFailed = true;   // software works, hardware doesn't: stick to software
                    }
                } else {
                    c = holder.lockCanvas();
                }
                if (c == null) return;
                gfx.setCanvas(c);
                boolean wide = viewW < width || viewH < height;
                if (wide) {
                    c.drawColor(scene.palette().bgBottom);
                    c.save();
                    c.translate(offX * (width - viewW), offY * (height - viewH));
                }
                scene.draw(gfx);
                if (wide) c.restore();
            } catch (Throwable t) {
                Log.w(TAG, "draw failed", t);
            } finally {
                if (c != null) {
                    try { holder.unlockCanvasAndPost(c); } catch (Throwable ignored) {}
                }
            }
        }
    }
}
