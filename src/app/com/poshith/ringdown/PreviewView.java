package com.poshith.ringdown;

import android.content.Context;
import android.graphics.Canvas;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import com.ringdown.core.RingScene;

/** Live, scaled-down copy of the wallpaper for the settings screen. */
final class PreviewView extends View implements Choreographer.FrameCallback {
    final RingScene scene = new RingScene();
    private final AndroidGfx gfx;
    private boolean running;
    private long last;

    PreviewView(Context c) {
        super(c);
        gfx = new AndroidGfx(c);
        setClickable(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        scene.setViewport(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        gfx.setCanvas(canvas);
        scene.draw(gfx);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) start(); else stop();
    }

    void start() {
        if (running) return;
        running = true;
        last = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void doFrame(long now) {
        if (!running) return;
        if (last != 0 && now - last < 15_000_000L) {      // cap at ~60 fps on high-refresh screens
            Choreographer.getInstance().postFrameCallback(this);
            return;
        }
        float dt = last == 0 ? 0f : (now - last) / 1e9f;
        last = now;
        scene.update(dt);
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            scene.tap(e.getX(), e.getY());
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP) performClick();
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
