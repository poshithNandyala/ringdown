package com.ringdown.harness;

import com.ringdown.core.RingScene;
import com.ringdown.core.SceneConfig;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Desktop preview renderer.
 *   java Render out=frames/ style=0 pal=0 total=90 left=78 w=540 h=1170 secs=5 fps=30 demo=1.0 [still=1] [goal=..]
 * Writes PNG frames (or one still) so the look and the release animation can be checked off-device.
 */
public final class Render {
    public static void main(String[] args) throws Exception {
        Map<String, String> a = new HashMap<>();
        for (String s : args) { int i = s.indexOf('='); if (i > 0) a.put(s.substring(0, i), s.substring(i + 1)); }
        String out = a.getOrDefault("out", "frames");
        int w = Integer.parseInt(a.getOrDefault("w", "540"));
        int h = Integer.parseInt(a.getOrDefault("h", "1170"));
        float secs = Float.parseFloat(a.getOrDefault("secs", "4"));
        int fps = Integer.parseInt(a.getOrDefault("fps", "30"));
        float demoAt = Float.parseFloat(a.getOrDefault("demo", "-1"));
        float tapAt = Float.parseFloat(a.getOrDefault("tap", "-1"));
        float realAt = Float.parseFloat(a.getOrDefault("real", "-1"));
        int realTo = Integer.parseInt(a.getOrDefault("realto", "-1"));
        boolean still = a.containsKey("still");
        int every = Integer.parseInt(a.getOrDefault("every", "1"));
        String fonts = a.getOrDefault("fonts", "assets/fonts");

        SceneConfig c = new SceneConfig();
        c.style = Integer.parseInt(a.getOrDefault("style", "0"));
        c.mode = Integer.parseInt(a.getOrDefault("mode", "0"));
        c.palette = Integer.parseInt(a.getOrDefault("pal", "0"));
        c.total = Integer.parseInt(a.getOrDefault("total", "90"));
        c.goal = a.getOrDefault("goal", "");
        c.size = Float.parseFloat(a.getOrDefault("size", "1"));
        c.pos = Float.parseFloat(a.getOrDefault("pos", "0"));
        c.lowPower = a.containsKey("low");
        int left = Integer.parseInt(a.getOrDefault("left", String.valueOf(c.total)));
        int startsIn = Integer.parseInt(a.getOrDefault("startsin", "0"));

        RingScene scene = new RingScene();
        scene.setConfig(c);
        scene.setViewport(w, h);
        scene.setProgress(left, startsIn);
        if (a.containsKey("notice")) scene.setNotice(a.get("notice"));

        File dir = new File(out);
        if (!still) dir.mkdirs();
        int frames = Math.max(1, Math.round(secs * fps));
        float dt = 1f / fps;
        boolean demoDone = false, tapDone = false, realDone = false;
        long t0 = System.nanoTime();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        J2DGfx g = new J2DGfx(img, fonts);
        for (int f = 0; f < frames; f++) {
            float t = f * dt;
            if (!demoDone && demoAt >= 0 && t >= demoAt) { demoDone = true; scene.playDemo(); }
            if (!tapDone && tapAt >= 0 && t >= tapAt) { tapDone = true; scene.tap(w * 0.62f, h * 0.36f); }
            if (!realDone && realAt >= 0 && t >= realAt) { realDone = true; scene.animateTo(realTo, 0, 0.2f); }
            scene.update(dt);
            if (still && f < frames - 1) continue;
            if (!still && f % every != 0) continue;
            scene.draw(g);
            File o = still ? new File(out) : new File(dir, String.format("f%04d.png", f));
            ImageIO.write(img, "png", o);
        }
        System.out.printf("rendered %d frames in %.1fs -> %s%n", still ? 1 : frames / every,
                (System.nanoTime() - t0) / 1e9, out);
    }
}
