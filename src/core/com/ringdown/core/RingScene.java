package com.ringdown.core;

/**
 * The whole wallpaper: background, the ring formation (Orb / Grid / Tunnel), the daily
 * animation, particles, and the count text. Platform free; draws through {@link Gfx}.
 *
 * Two modes:
 *   BREAK a habit: every ring starts present; one disappears per completed day (the old loop fades).
 *   BUILD a habit: rings start as faint blueprint ghosts; one is built per completed day.
 * Ring index k runs in event order: completing day k+1 affects ring k. done = total - left.
 * BREAK: rings k >= done are present. BUILD: rings k < done are present. k == done is "today's ring".
 */
public final class RingScene {
    public static final int STYLE_ORB = 0, STYLE_GRID = 1, STYLE_TUNNEL = 2;
    public static final String[] STYLE_NAMES = {"Orb", "Grid", "Tunnel"};
    public static final int MODE_BREAK = 0, MODE_BUILD = 1;

    public interface Listener {
        /** A day just completed (ring broke or got built). milestone = 25/50/75%, first, last or final. */
        void onRingBurst(boolean milestone, boolean demo);
    }

    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float PI = (float) Math.PI;
    private static final int WHITE = 0xFFFFFFFF;

    private static final int BANDS = 6, LV = 5;
    private static final int SPEC = BANDS * LV;   // LV depth buckets for single-color specials
    private static final int FLAT = SPEC + LV;    // 3 flat scratch buckets
    private static final int GHOST = FLAT + 3;    // 2 buckets for blueprint rings (build mode)
    private static final int NBK = GHOST + 2;
    private static final int PK_STAR = 0, PK_BEAD = 8, NPK = PK_BEAD + BANDS * 3;   // point buckets

    private static final float CHARGE_T = 0.95f;  // charge-up before the burst
    private static final float END_T = 3.4f;      // release animation length
    private static final float ORB_D = 4.2f;      // orb camera distance
    private static final float TUN_D0 = 1.15f, TUN_DZ = 0.42f;
    private static final float GRID_TILT = 0.40f;
    private static final float GLOW_K = 1.8f;    // soft (tent) glow lines carry about half the light of solid ones

    // depth level look: index 0 = far/back, LV-1 = near/front. widths are in units of u.
    private static final float[] ORB_CA = {0.10f, 0.17f, 0.28f, 0.45f, 0.68f};
    private static final float[] ORB_GA = {0.022f, 0.035f, 0.052f, 0.075f, 0.10f};
    private static final float[] ORB_CW = {0.55f, 0.68f, 0.84f, 1.04f, 1.30f};
    private static final float[] ORB_GW = {2.3f, 2.8f, 3.3f, 3.9f, 4.7f};

    private static final float[] GRID_CA = {0.30f, 0.42f, 0.58f, 0.76f, 0.95f};
    private static final float[] GRID_GA = {0.03f, 0.045f, 0.06f, 0.08f, 0.10f};
    private static final float[] GRID_CW = {0.75f, 0.85f, 0.95f, 1.10f, 1.30f};
    private static final float[] GRID_GW = {2.4f, 2.8f, 3.2f, 3.6f, 4.2f};

    private static final float[] TUN_CA = {0.09f, 0.18f, 0.32f, 0.55f, 0.86f};
    private static final float[] TUN_GA = {0.018f, 0.03f, 0.045f, 0.065f, 0.09f};
    private static final float[] TUN_CW = {0.45f, 0.6f, 0.8f, 1.1f, 1.5f};
    private static final float[] TUN_GW = {1.8f, 2.4f, 3.1f, 4.0f, 5.2f};

    // ---------------- config ----------------
    private SceneConfig cfg = new SceneConfig();
    private Palette pal = Palette.ALL[0];
    private int total = 90;
    private String goalUpper = "";
    private boolean build;          // build mode (rings get added)
    private Listener listener;

    // ---------------- countdown ----------------
    private int left = 90;          // rings present right now (a ring in flight still counts until it bursts)
    private int targetLeft = 90;    // what the calendar says
    private int startsIn = 0;
    private int tickLit = 0;        // completed ticks drawn gold (lags until particles arrive)

    // ---------------- viewport / layout ----------------
    private float W = 1080, H = 2340, u = 2.7f;
    private boolean landscape;
    private float cx, cy, formR, orbF;
    private float textX, textTop, numSize, txtScale;
    private float goalY, numBase, line1Y, line2Y, barY, barW;
    private float parallax = 0.5f;
    private int gCols = 1, gRows = 1;
    private float gCell = 10, gRingR = 3;
    private float[] gX = new float[0], gY = new float[0];

    // ---------------- time ----------------
    private double time;      // seconds
    private double motion;    // seconds, sped up by taps
    private float spinBoost;

    // ---------------- release animation ----------------
    private boolean rel, relBurst, relDemo;
    private float relT, relSpeed = 1f, relSpin, relBeadAng;
    private int relK = -1;
    private float queueGap, startDelay;
    private float demoRestore = -1f;
    private float restoreT = 1f;
    private int restoreK = -1;
    private double lastBurstTime = -99;
    private int freshK = -1;        // build mode: the ring built most recently (glows gold, then settles)
    private float freshT = 1f;

    // ---------------- reward fx ----------------
    private final Particles parts = new Particles(900);
    private float flashT = 1f, flashX, flashY, flashR, flashA = 0.5f;
    private float shockT = 1f, shockX, shockY, shockR0, shockR1;
    private float waveT = 1f, waveFrom, waveTo = 1f, waveCol, waveRow, waveMax = 1f;
    private float coreBoost, tickFlash, glintT = 1f, todayGlow = 1f;
    private int tickFlashIdx = -1;
    private float celebrateT = -1f, celebrateNext;
    private float ambientNext = 2f;
    private int cntFrom, cntTo;
    private float cntT = 1f;
    private String msgTitle = "", msgLine = "";
    private float msgT = -1f, msgDur = 5.5f;
    private float shownProg;
    private String noticeShown = "", noticeNext = "";   // optional line under the progress bar (remote notice)
    private float noticeA;

    private final float[] tapX = new float[6], tapY = new float[6], tapT = {1, 1, 1, 1, 1, 1};
    private int tapIdx;
    private double gridTapT = -99;
    private float gridTapCol, gridTapRow;

    // ---------------- stars ----------------
    private static final int STARS = 150;
    private final float[] stX = new float[STARS], stY = new float[STARS], stS = new float[STARS],
            stA = new float[STARS], stP = new float[STARS];

    // ---------------- orb ring data ----------------
    private float[] oR, oCos, oSin, oPhi, oOm, oB0, oBs;
    private int[] oBand;
    private float r00, r01, r02, r10, r11, r12, r20, r21, r22;
    private float curUx, curUy, curUz, curVx, curVy, curVz, curR;

    // ---------------- scratch ----------------
    private final float[][] bk = new float[NBK][];
    private final int[] bkN = new int[NBK];
    private final int[] bandCol = new int[BANDS];
    private final int[] beadCol = new int[BANDS];
    private final float[][] pk = new float[NPK][];
    private final int[] pkN = new int[NPK];
    private final float[] ptX = new float[132], ptY = new float[132], ptZ = new float[132];
    private final float[][] cosT = new float[129][], sinT = new float[129][];
    private final String[] numCacheS = new String[4];
    private final int[] numCacheV = {-1, -1, -1, -1};
    private String dayStr = "";
    private int dayKey = Integer.MIN_VALUE;
    private float tcX, tcY;
    private long seed = 0x2545F4914F6CDD1DL;

    public RingScene() {
        initStars();
        setConfig(new SceneConfig());
        setProgress(90, 0);
    }

    // =====================================================================================
    // Public API
    // =====================================================================================

    public void setListener(Listener l) { listener = l; }

    /** A short line shown under the progress bar (e.g. "New version available"); null or "" hides it. */
    public void setNotice(String s) {
        String t = s == null ? "" : s.trim().toUpperCase(java.util.Locale.ROOT);
        if (t.length() > 42) t = t.substring(0, 42);
        noticeNext = t;
    }

    public Palette palette() { return pal; }

    public int ringsLeft() { return left; }

    /** The count the scene is heading to (equals ringsLeft() unless a release is pending). */
    public int targetLeft() { return targetLeft; }

    public void setConfig(SceneConfig c) {
        int newTotal = SceneConfig.clampRings(c.total);
        boolean rebuild = newTotal != total || oR == null || c.mode != cfg.mode;
        cfg = c.copy();
        total = newTotal;
        build = cfg.mode == MODE_BUILD;
        pal = Palette.get(c.palette);
        goalUpper = c.goal == null ? "" : c.goal.trim().toUpperCase();
        if (goalUpper.length() > 32) goalUpper = goalUpper.substring(0, 32);
        for (int i = 0; i < BANDS; i++) {
            bandCol[i] = Palette.lerp(pal.a, pal.b, (i + 0.5f) / BANDS);
            beadCol[i] = Palette.lerp(bandCol[i], WHITE, 0.55f);
        }
        if (rebuild) {
            buildOrb();
            cancelRelease();
            freshK = -1;
            freshT = 1f;
            if (left > total) left = total;
            if (targetLeft > total) targetLeft = total;
            tickLit = Math.min(tickLit, total);
        }
        dayKey = Integer.MIN_VALUE;
        layout();
    }

    public void setViewport(float w, float h) {
        W = Math.max(8f, w);
        H = Math.max(8f, h);
        landscape = W > H * 1.1f;
        layout();
    }

    /** Launcher page offset, 0..1. */
    public void setParallax(float xOffset) {
        if (!Float.isNaN(xOffset)) parallax = clampf(xOffset, 0f, 1f);
    }

    /** Jump to a state with no animation. */
    public void setProgress(int ringsLeft, int startsInDays) {
        cancelRelease();
        left = targetLeft = clampi(ringsLeft, 0, total);
        startsIn = Math.max(0, startsInDays);
        tickLit = total - left;
        shownProg = progress();
        cntT = 1f;
        msgT = -1f;
        celebrateT = -1f;
        todayGlow = 1f;
        dayKey = Integer.MIN_VALUE;
    }

    /** The calendar moved on: break rings one by one down to ringsLeft. */
    public void animateTo(int ringsLeft, int startsInDays, float delay) {
        startsIn = Math.max(0, startsInDays);
        int t = clampi(ringsLeft, 0, total);
        boolean demoActive = (rel && relDemo) || demoRestore > 0f;
        if (t > left && !demoActive) { setProgress(t, startsInDays); return; }
        targetLeft = t;
        if (left > targetLeft && !rel) startDelay = Math.max(startDelay, delay);
        dayKey = Integer.MIN_VALUE;
    }

    /** Show what tomorrow's release looks like without changing anything. */
    public boolean playDemo() {
        if (rel || demoRestore > 0f || left > targetLeft) return false;
        if (left == 0) { startCelebration(); return true; }
        beginRelease(true);
        return true;
    }

    public void tap(float x, float y) {
        int i = tapIdx++ % tapX.length;
        tapX[i] = x; tapY[i] = y; tapT[i] = 0f;
        spinBoost = Math.min(1.6f, spinBoost + 0.9f);
        int cnt = cfg.lowPower ? 6 : 12;
        for (int n = 0; n < cnt; n++) {
            float a = rnd() * TAU, s = (40f + rnd() * 90f) * u;
            parts.add(Particles.SPARK2D, x, y, 0, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 0,
                    0.5f + rnd() * 0.5f, 0.5f + rnd() * 0.7f, rnd() < 0.5f ? pal.a : WHITE, rnd() * TAU);
        }
        if (cfg.style == STYLE_GRID && gCell > 0) {
            gridTapT = time;
            gridTapCol = (x - (cx - (gCols - 1) * gCell * 0.5f)) / gCell;
            gridTapRow = (y - (cy - (gRows - 1) * gCell * 0.5f)) / gCell;
        }
    }

    public boolean isAnimating() {
        if (rel || parts.n > 0 || cntT < 1f || flashT < 1f || shockT < 1f || waveT < 1f) return true;
        if (demoRestore > 0f || restoreT < 1f || left > targetLeft || tickFlash > 0.02f) return true;
        if (celebrateT >= 0f || glintT < 1f || spinBoost > 0.05f || todayGlow < 1f || freshT < 1f) return true;
        for (float t : tapT) if (t < 1f) return true;
        if (!noticeShown.equals(noticeNext) || (noticeShown.length() > 0 && noticeA < 1f)) return true;
        return msgT >= 0f && (msgT < 0.6f || msgT > msgDur - 0.9f);
    }

    // =====================================================================================
    // Update
    // =====================================================================================

    public void update(float dt) {
        if (!(dt > 0f)) dt = 0f;
        if (dt > 0.1f) dt = 0.1f;
        time += dt;
        spinBoost *= (float) Math.exp(-dt * 1.1f);
        motion += dt * (1f + 2.4f * spinBoost);

        boolean demoActive = (rel && relDemo) || demoRestore > 0f;
        if (!rel && !demoActive && left > targetLeft) {
            if (startDelay > 0f) startDelay -= dt;
            else if (queueGap > 0f) queueGap -= dt;
            else {
                if (left - targetLeft > 3) {   // missed several days: animate only the last 3
                    left = targetLeft + 3;
                    tickLit = total - left;
                }
                beginRelease(false);
            }
        }

        if (rel) {
            relT += dt * relSpeed;
            float c = clamp01(relT / CHARGE_T);
            relSpin += dt * relSpeed * (0.4f + 7.5f * c * c);
            relBeadAng += dt * relSpeed * (1.0f + 11f * c * c);
            if (!relBurst && relT >= CHARGE_T) burst();
            if (relT >= END_T) endRelease();
        }

        if (demoRestore > 0f) {
            demoRestore -= dt;
            if (demoRestore <= 0f) {
                demoRestore = -1f;
                int from = left;
                left = Math.min(total, left + 1);
                restoreK = total - left;
                restoreT = 0f;
                freshK = -1;
                freshT = 1f;
                tickLit = total - left;
                counterRoll(from, left);
                if (msgT >= 0f && msgT < msgDur - 0.7f) msgT = msgDur - 0.7f;
                dayKey = Integer.MIN_VALUE;
            }
        }

        parts.update(dt, u);
        if (parts.arrived > 0) {
            parts.arrived = 0;
            onParticlesArrived();
        }
        if (tickLit < total - left && time - lastBurstTime > 1.7) onParticlesArrived();

        if (cntT < 1f) cntT = Math.min(1f, cntT + dt / 0.75f);
        if (msgT >= 0f) { msgT += dt; if (msgT > msgDur) msgT = -1f; }
        flashT = Math.min(1f, flashT + dt / 0.6f);
        shockT = Math.min(1f, shockT + dt / 0.8f);
        if (waveT < 1f) {
            waveT = Math.min(1f, waveT + dt / 1.15f);
            if (waveT >= 1f) coreBoost = 1f;
        }
        coreBoost *= (float) Math.exp(-dt * 1.5f);
        tickFlash *= (float) Math.exp(-dt * 2.4f);
        glintT = Math.min(1f, glintT + dt / 0.9f);
        todayGlow = Math.min(1f, todayGlow + dt / 0.8f);
        restoreT = Math.min(1f, restoreT + dt / 0.8f);
        freshT = Math.min(1f, freshT + dt / 2.4f);
        for (int i = 0; i < tapT.length; i++) if (tapT[i] < 1f) tapT[i] = Math.min(1f, tapT[i] + dt / 0.9f);
        shownProg += (progress() - shownProg) * Math.min(1f, dt * 2.4f);
        if (!noticeShown.equals(noticeNext)) {                 // fade the old line out, then swap
            noticeA = noticeShown.length() == 0 ? 0f : noticeA - dt / 0.4f;
            if (noticeA <= 0f) { noticeA = 0f; noticeShown = noticeNext; }
        } else if (noticeShown.length() > 0) {
            noticeA = Math.min(1f, noticeA + dt / 0.8f);
        }

        if (celebrateT >= 0f) {
            celebrateT += dt;
            celebrateNext -= dt;
            if (celebrateNext <= 0f && celebrateT < 4.2f) {
                celebrateNext = 0.32f + rnd() * 0.3f;
                firework();
            }
            if (celebrateT > 6.5f) celebrateT = -1f;
        }
        if (left == 0 && !rel) {
            ambientNext -= dt;
            if (ambientNext <= 0f) {
                ambientNext = 1.2f + rnd() * 1.6f;
                ambientEmbers();
            }
        }
    }

    private float progress() {
        return total <= 0 ? 0f : (total - left) / (float) total;
    }

    private void beginRelease(boolean demo) {
        if (left <= 0) return;
        rel = true;
        relDemo = demo;
        relBurst = false;
        relT = 0f;
        relSpin = 0f;
        relBeadAng = 0f;
        relK = total - left;
        relSpeed = (!demo && left - targetLeft > 1) ? 1.7f : 1f;
        if (build) gather();
    }

    private void cancelRelease() {
        rel = false; relBurst = false; relDemo = false;
        demoRestore = -1f; restoreT = 1f; restoreK = -1;
        startDelay = 0f; queueGap = 0f;
        waveT = 1f; flashT = 1f; shockT = 1f;
        parts.clear();
    }

    private void endRelease() {
        rel = false;
        if (relDemo) demoRestore = 1.9f;
        else queueGap = 0.35f;
        relK = -1;
    }

    private void burst() {
        relBurst = true;
        int from = left;
        left--;
        counterRoll(from, left);
        lastBurstTime = time;
        todayGlow = -1.1f;   // next ring takes over the highlight ~1s after the burst
        dayKey = Integer.MIN_VALUE;
        int done = total - left;
        boolean milestone = Lines.isMilestone(done, total, left);

        msgTitle = left == 0 ? allDoneTitle() : "DAY " + done + " COMPLETE";
        msgLine = cfg.quotes ? Lines.pick(done, total, left, build) : "";
        msgT = 0f;
        msgDur = left == 0 ? 7.5f : 5.6f;

        float q = cfg.lowPower ? 0.55f : 1f;
        if (milestone) q *= 1.5f;
        switch (cfg.style) {
            case STYLE_GRID: if (build) formGrid(q); else burstGrid(q); break;
            case STYLE_TUNNEL: if (build) formTunnel(q); else burstTunnel(q); break;
            default: if (build) formOrb(q); else burstOrb(q); break;
        }
        if (left == 0) startCelebration();
        else if (milestone) {
            // extra sparkle around the counter
            for (int i = 0; i < (int) (36 * q); i++) {
                float a = rnd() * TAU, s = (70f + rnd() * 170f) * u;
                parts.add(Particles.SPARK2D, textX, numBase - numSize * 0.35f, 0,
                        (float) Math.cos(a) * s, (float) Math.sin(a) * s * 0.7f - 40f * u, 0,
                        0.9f + rnd() * 0.9f, 0.6f + rnd() * 0.9f, rnd() < 0.6f ? pal.accent : WHITE, rnd() * TAU);
            }
        }
        if (listener != null) listener.onRingBurst(milestone, relDemo);
    }

    private void onParticlesArrived() {
        int done = total - left;
        if (tickLit < done) {
            tickFlashIdx = done - 1;
            tickLit = done;
        }
        tickFlash = 1f;
        glintT = 0f;
    }

    private void counterRoll(int from, int to) {
        if (from == to) return;
        cntFrom = from; cntTo = to; cntT = 0f;
    }

    private void startCelebration() {
        celebrateT = 0f;
        celebrateNext = 0f;
        coreBoost = 1f;
        msgTitle = allDoneTitle();
        msgLine = cfg.quotes ? Lines.pick(total, total, 0, build) : "";
        msgT = 0f;
        msgDur = 7.5f;
    }

    private String allDoneTitle() {
        return "ALL " + total + (build ? " RINGS BUILT" : " RINGS CLEARED");
    }

    private void firework() {
        float x = cx + (rnd() - 0.5f) * formR * 2.2f;
        float y = cy + (rnd() - 0.5f) * formR * 1.8f;
        int col = rnd() < 0.5f ? pal.accent : (rnd() < 0.5f ? pal.a : pal.b);
        int cnt = cfg.lowPower ? 26 : 54;
        float sp = (110f + rnd() * 80f) * u;
        for (int i = 0; i < cnt; i++) {
            float a = TAU * i / cnt + rnd() * 0.1f;
            float s = sp * (0.85f + rnd() * 0.3f);
            parts.add(Particles.SPARK2D, x, y, 0, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 0,
                    1.1f + rnd() * 0.8f, 0.6f + rnd() * 0.8f, rnd() < 0.25f ? WHITE : col, rnd() * TAU);
        }
        flashX = x; flashY = y; flashR = formR * 0.8f; flashT = 0f; flashA = 0.35f;
    }

    private void ambientEmbers() {
        float bx, by, spread;
        if (cfg.style == STYLE_GRID) { bx = cx; by = cy; spread = gCols * gCell * 0.5f; }
        else { bx = cx; by = cy; spread = formR * 0.5f; }
        int cnt = cfg.lowPower ? 3 : 6;
        for (int i = 0; i < cnt; i++) {
            parts.add(Particles.EMBER, bx + (rnd() - 0.5f) * spread * 2f, by + (rnd() - 0.3f) * spread, 0,
                    (rnd() - 0.5f) * 14f * u, -(8f + rnd() * 16f) * u, 0,
                    2.2f + rnd() * 1.8f, 0.5f + rnd() * 0.8f, rnd() < 0.6f ? pal.accent : pal.core, rnd() * TAU);
        }
    }

    // =====================================================================================
    // Layout
    // =====================================================================================

    private void layout() {
        float s = clampf(cfg.size, 0.6f, 1.4f);
        txtScale = clampf(0.9f + (s - 1f) * 0.45f, 0.75f, 1.12f);
        float pos = clampf(cfg.pos, -1f, 1f);
        if (!landscape) {
            u = Math.min(W, H * 0.56f) / 400f;
            cx = W * 0.5f;
            formR = W * 0.355f * s;
            cy = H * 0.385f + pos * H * 0.13f;
            textX = W * 0.5f;
            numSize = W * 0.15f * txtScale;
        } else {
            u = Math.min(W * 0.5f, H) / 400f;
            cx = W * 0.33f;
            cy = H * 0.5f + pos * H * 0.08f;
            formR = H * 0.33f * s;
            textX = W * 0.73f;
            numSize = H * 0.17f * txtScale;
        }
        orbF = formR * ORB_D;
        layoutGrid(s);

        float ext;
        if (cfg.style == STYLE_GRID) ext = gRows * gCell * 0.5f;
        else if (cfg.style == STYLE_TUNNEL) ext = formR * 1.06f;
        else ext = formR * 1.25f;

        float ts = txtScale;
        float goalH = goalUpper.length() > 0 ? 21f * u * ts : 0f;
        float blockH = goalH + numSize * 0.8f + 17f * u * ts + 15f * u * ts + 12f * u * ts + 2f * u;
        textTop = landscape ? cy - blockH * 0.5f : cy + ext + 14f * u;
        float y = textTop;
        goalY = y + 9.5f * u * ts;
        y += goalH;
        numBase = y + numSize * 0.80f;
        line1Y = numBase + 17f * u * ts;
        line2Y = line1Y + 15f * u * ts;
        barY = line2Y + 12f * u * ts;
        barW = 116f * u * ts;
    }

    private void layoutGrid(float s) {
        int n = total;
        float aw = (landscape ? W * 0.52f : W * 0.86f) * s;
        float ah = (landscape ? H * 0.78f : H * 0.44f) * s;
        int bestC = 1;
        float best = 0f;
        for (int c = 1; c <= n; c++) {
            int r = (n + c - 1) / c;
            float cell = Math.min(aw / c, ah / r);
            if (cell > best + 0.01f) { best = cell; bestC = c; }
        }
        gCols = bestC;
        gRows = (n + bestC - 1) / bestC;
        gCell = Math.min(best, (landscape ? H : W) * 0.24f * s);
        gRingR = gCell * 0.34f;
        if (gX.length != n) { gX = new float[n]; gY = new float[n]; }
        float y0 = cy - (gRows - 1) * gCell * 0.5f;
        for (int k = 0; k < n; k++) {
            int r = k / gCols, c = k % gCols;
            int inRow = (r == gRows - 1) ? n - r * gCols : gCols;
            float x0 = cx - (inRow - 1) * gCell * 0.5f;
            gX[k] = x0 + c * gCell;
            gY[k] = y0 + r * gCell;
        }
    }

    private void buildOrb() {
        int n = total;
        oR = new float[n]; oCos = new float[n]; oSin = new float[n]; oPhi = new float[n];
        oOm = new float[n]; oB0 = new float[n]; oBs = new float[n]; oBand = new int[n];
        for (int k = 0; k < n; k++) {
            float rel = n == 1 ? 0f : k / (float) (n - 1);
            oR[k] = build ? 0.32f + 0.68f * rel : 1f - 0.68f * rel;   // build grows outward, break shrinks inward
            float c = 1f - 2f * frac(0.5f + k * 0.61803398875f);
            c = clampf(c, -0.985f, 0.985f);
            oCos[k] = c;
            oSin[k] = (float) Math.sqrt(1f - c * c);
            oPhi[k] = frac(k * 0.38196601125f) * TAU;
            float sp = 0.045f + 0.15f * frac(k * 0.7548776662f + 0.31f);
            oOm[k] = (k & 1) == 0 ? sp : -sp;
            oB0[k] = frac(k * 0.5698402910f) * TAU;
            oBs[k] = (0.22f + 0.4f * frac(k * 0.41421356f)) * ((k % 3) == 0 ? -1f : 1f);
            oBand[k] = Math.min(BANDS - 1, (int) (rel * BANDS));
        }
    }

    private void initStars() {
        long s = seed;
        seed = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < STARS; i++) {
            stX[i] = rnd();
            stY[i] = rnd();
            float r = rnd();
            stS[i] = 0.35f + r * r * 1.1f;
            stA[i] = 0.08f + rnd() * 0.42f;
            stP[i] = rnd() * TAU;
        }
        seed = s;
    }

    // =====================================================================================
    // Draw
    // =====================================================================================

    public void draw(Gfx g) {
        g.background(pal.bgTop, pal.bgBottom, W, H);
        g.glow(cx, cy, formR * 2.2f, pal.a, 0.065f);
        g.glow(cx + formR * 0.5f, cy + formR * 0.35f, formR * 1.6f, pal.b, 0.055f);
        drawStars(g);
        switch (cfg.style) {
            case STYLE_GRID: drawGrid(g); break;
            case STYLE_TUNNEL: drawTunnel(g); break;
            default: drawOrb(g); break;
        }
        drawFx(g);
        drawText(g);
        drawParticles(g);
        drawTaps(g);
    }

    private void drawStars(Gfx g) {
        int n = cfg.lowPower ? STARS / 2 : STARS;
        float px = (0.5f - parallax) * W * 0.05f;
        for (int b = PK_STAR; b < PK_BEAD; b++) pkN[b] = 0;
        for (int i = 0; i < n; i++) {
            float tw = 0.6f + 0.4f * (float) Math.sin(time * (0.5 + (i % 7) * 0.13) + stP[i]);
            float x = stX[i] * W + px * (0.3f + stS[i]);
            if (x < 0) x += W; else if (x > W) x -= W;
            int lvl = Math.min(3, (int) (stA[i] * tw * 8f));
            addP(PK_STAR + lvl * 2 + (stS[i] < 0.75f ? 0 : 1), x, stY[i] * H);
        }
        for (int lvl = 0; lvl < 4; lvl++)
            for (int sz = 0; sz < 2; sz++) {
                int b = PK_STAR + lvl * 2 + sz;
                if (pkN[b] > 0) g.points(pk[b], pkN[b], WHITE, (lvl + 0.5f) / 8f, (sz == 0 ? 1.0f : 2.2f) * u, true);
            }
    }

    private float focusAmount() {
        if (!rel) return 0f;
        if (!relBurst) return easeInOut(clamp01(relT / CHARGE_T));
        return clamp01(1f - (relT - CHARGE_T) / 0.7f);
    }

    // ------------------------------------- ORB ------------------------------------------

    private void setRot(float tilt, float yaw) {
        float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt);
        float cyw = (float) Math.cos(yaw), syw = (float) Math.sin(yaw);
        r00 = cyw;       r01 = 0f;  r02 = syw;
        r10 = st * syw;  r11 = ct;  r12 = -st * cyw;
        r20 = -ct * syw; r21 = st;  r22 = ct * cyw;
    }

    private int orbSegs(float r, float qual) {
        return clampi((int) ((16f + 46f * r) * qual), 12, 96);
    }

    private int orbRingPoints(int k, float phiExtra, float rScale, float qual) {
        return orbRingPoints(k, phiExtra, rScale, qual, 0f);
    }

    /**
     * Projects orb ring k into ptX/ptY/ptZ. face (0..1) turns the ring to face the camera.
     * Returns the number of points (segments + 1).
     */
    private int orbRingPoints(int k, float phiExtra, float rScale, float qual, float face) {
        float phi = oPhi[k] + (float) ((oOm[k] * motion) % TAU) + phiExtra;
        float sp = (float) Math.sin(phi), cp = (float) Math.cos(phi);
        float cA = oCos[k], sA = oSin[k];
        float ux = sp, uz = -cp;                          // uy = 0
        float vx = -cA * cp, vy = sA, vz = -cA * sp;
        curUx = r00 * ux + r02 * uz; curUy = r10 * ux + r12 * uz; curUz = r20 * ux + r22 * uz;
        curVx = r00 * vx + r01 * vy + r02 * vz;
        curVy = r10 * vx + r11 * vy + r12 * vz;
        curVz = r20 * vx + r21 * vy + r22 * vz;
        if (face > 0f) faceCamera(face);
        float r = oR[k] * rScale;
        curR = r;
        int segs = orbSegs(Math.min(r, 2.2f), qual);
        float[] ct = cosTab(segs), st = sinTab(segs);
        for (int s = 0; s <= segs; s++) {
            float c = ct[s], sn = st[s];
            float x = r * (c * curUx + sn * curVx);
            float y = r * (c * curUy + sn * curVy);
            float z = r * (c * curUz + sn * curVz);
            float sc = orbF / Math.max(0.3f, ORB_D - z);
            ptX[s] = cx + x * sc;
            ptY[s] = cy - y * sc;
            ptZ[s] = z;
        }
        return segs + 1;
    }

    private void drawOrb(Gfx g) {
        int done = total - left;
        float tilt = 0.36f + 0.06f * (float) Math.sin(motion * 0.09);
        float yaw = (float) ((motion * 0.03) % TAU) + (parallax - 0.5f) * 0.5f;
        setRot(tilt, yaw);
        float dimAll = 1f - 0.42f * focusAmount();
        float dens = clampf((float) Math.sqrt(70f / total), 0.42f, 1.25f);
        float qual = (cfg.lowPower ? 0.6f : 1f) * clampf((float) Math.sqrt(130f / total), 0.5f, 1f);
        boolean glowPass = total <= 220 && !cfg.lowPower;

        drawOrbCore(g);
        drawDial(g, done);

        clearBuckets();
        int todayK = (left > 0 && (!rel || relBurst)) ? done : -1;
        float wf = -9f;
        if (waveT < 1f) wf = waveFrom + (waveTo - waveFrom) * easeOut(waveT);
        float beadA = dens * dimAll;
        for (int b = PK_BEAD; b < NPK; b++) pkN[b] = 0;
        int from = build ? 0 : done, to = build ? done : total;
        for (int k = from; k < to; k++) {
            if (rel && !relBurst && k == relK) continue;
            float rs = 1f, fade = 1f;
            if (!build && k == restoreK && restoreT < 1f) { rs = 0.6f + 0.4f * easeOut(restoreT); fade = restoreT; }
            int n = orbRingPoints(k, 0f, rs, qual);
            if (!build && k == todayK) {
                drawOrbToday(g, n, k, fade * dimAll, false);
                continue;
            }
            int base = oBand[k] * LV;
            for (int s = 0; s < n - 1; s++) {
                float zm = (ptZ[s] + ptZ[s + 1]) * 0.5f;
                int lv = clampi((int) ((zm + 1f) * 0.5f * LV), 0, LV - 1);
                addSeg(base + lv, ptX[s], ptY[s], ptX[s + 1], ptY[s + 1]);
            }
            if (wf > -1f) {
                float rel01 = total == 1 ? 0f : k / (float) (total - 1);
                float d = (rel01 - wf) / 0.07f;
                float boost = (float) Math.exp(-d * d) * (1f - waveT * 0.4f);
                if (boost > 0.06f) {
                    specialLevels(n);
                    drawSpecLevels(g, pal.accent, 0.55f * boost, 0.2f * boost, 1.7f * u);
                }
            }
            if (build && k == freshK && freshT < 1f) {
                float a = 1f - freshT;
                specialLevels(n);
                drawSpecGlow(g, pal.accent, 0.30f * a, 0.10f * a, 6f * u);
                drawSpecLevels(g, Palette.lerp(pal.accent, WHITE, 0.3f), 0.85f * a, 0.35f * a, 1.5f * u);
            }
            if (!cfg.lowPower) {
                float b = oB0[k] + (float) ((oBs[k] * motion) % TAU);
                orbPoint((float) Math.cos(b), (float) Math.sin(b));
                int lvl = Math.min(2, (int) (3f * (pz + curR) / (2f * curR + 1e-4f)));
                addP(PK_BEAD + oBand[k] * 3 + lvl, px, py);
            }
        }
        flushBuckets(g, ORB_CA, ORB_GA, ORB_CW, ORB_GW, dens * dimAll, glowPass);
        for (int band = 0; band < BANDS; band++)
            for (int lvl = 0; lvl < 3; lvl++) {
                int b = PK_BEAD + band * 3 + lvl;
                if (pkN[b] == 0) continue;
                float a = (0.2f + 0.8f * (lvl + 0.5f) / 3f) * beadA;
                float sz = (0.8f + 0.25f * lvl) * u;
                g.points(pk[b], pkN[b], beadCol[band], 0.10f * a, sz * 5.6f, true);
                g.points(pk[b], pkN[b], beadCol[band], 0.85f * a, sz * 1.9f, true);
            }
        if (build) drawOrbGhosts(g, done, todayK, qual, dens * dimAll);
        if (rel) drawOrbRelease(g, qual);
    }

    /** Build mode: the rings still to come, drawn as a faint blueprint, plus today's ring. */
    private void drawOrbGhosts(Gfx g, int done, int todayK, float qual, float alphaMul) {
        bkN[GHOST] = 0;
        bkN[GHOST + 1] = 0;
        for (int k = done; k < total; k++) {
            if (rel && !relBurst && k == relK) continue;          // being formed right now
            if (k == restoreK && restoreT < 1f) {                  // end of a preview: fade back out
                int n = orbRingPoints(k, 0f, 1f, qual);
                float a = 1f - restoreT;
                specialLevels(n);
                drawSpecLevels(g, bandCol[oBand[k]], 0.75f * a, 0.25f * a, 1.1f * u);
                continue;
            }
            if (k == todayK) {
                int n = orbRingPoints(k, 0f, 1f, qual);
                drawOrbToday(g, n, k, alphaMul, true);
                continue;
            }
            int n = orbRingPoints(k, 0f, 1f, qual * 0.55f);
            for (int s = 0; s < n - 1; s++) {
                float zm = ptZ[s] + ptZ[s + 1];
                addSeg(zm > 0f ? GHOST : GHOST + 1, ptX[s], ptY[s], ptX[s + 1], ptY[s + 1]);
            }
        }
        int gc = Palette.lerp(pal.a, WHITE, 0.35f);
        g.lines(bk[GHOST], bkN[GHOST], gc, 0.12f * alphaMul, 0.6f * u, true);
        g.lines(bk[GHOST + 1], bkN[GHOST + 1], gc, 0.05f * alphaMul, 0.5f * u, true);
    }

    /** Rotates the current ring basis so its normal turns toward the viewer (Rodrigues). */
    private void faceCamera(float amount) {
        float nx = curUy * curVz - curUz * curVy;
        float ny = curUz * curVx - curUx * curVz;
        float nz = curUx * curVy - curUy * curVx;
        float tz = nz >= 0f ? 1f : -1f;                   // ring is symmetric: pick the nearer side
        // axis = n x t, with t = (0, 0, tz)
        float ax = ny * tz, ay = -nx * tz;
        float al = (float) Math.sqrt(ax * ax + ay * ay);
        if (al < 1e-5f) return;
        ax /= al; ay /= al;
        float ang = (float) Math.acos(clampf(Math.abs(nz), -1f, 1f)) * clamp01(amount);
        float c = (float) Math.cos(ang), s = (float) Math.sin(ang), ic = 1f - c;
        // rotate U
        float d = ax * curUx + ay * curUy;
        float rx = curUx * c + (ay * curUz) * s + ax * d * ic;
        float ry = curUy * c + (-ax * curUz) * s + ay * d * ic;
        float rz = curUz * c + (ax * curUy - ay * curUx) * s;
        curUx = rx; curUy = ry; curUz = rz;
        d = ax * curVx + ay * curVy;
        rx = curVx * c + (ay * curVz) * s + ax * d * ic;
        ry = curVy * c + (-ax * curVz) * s + ay * d * ic;
        rz = curVz * c + (ax * curVy - ay * curVx) * s;
        curVx = rx; curVy = ry; curVz = rz;
    }

    // orbPoint output
    private float px, py, pz, ps;

    /** Point on the last computed orb ring at angle (c, s). */
    private void orbPoint(float c, float s) {
        float x = curR * (c * curUx + s * curVx);
        float y = curR * (c * curUy + s * curVy);
        float z = curR * (c * curUz + s * curVz);
        float sc = orbF / Math.max(0.3f, ORB_D - z);
        px = cx + x * sc; py = cy - y * sc; pz = z; ps = sc / formR;
    }

    private void drawOrbToday(Gfx g, int n, int k, float fade, boolean ghost) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.2);
        float tg = Math.max(0f, todayGlow) * fade;
        if (tg <= 0.01f) return;
        int col = Palette.lerp(pal.accent, WHITE, 0.18f);
        float m = ghost ? 0.55f : 1f;
        specialLevels(n);
        drawSpecGlow(g, pal.accent, (0.07f + 0.06f * pulse) * tg * m, 0.025f * tg * m, 6.5f * u);
        if (ghost) drawSpecLevels(g, col, (0.36f + 0.24f * pulse) * tg, 0.12f * tg, 1.1f * u);
        else drawSpecLevels(g, col, (0.72f + 0.25f * pulse) * tg, 0.26f * tg, 1.45f * u);
        // comet bead with a short tail
        double b0 = oB0[k] + motion * 0.8;
        for (int i = 0; i < 6; i++) {
            float a = (float) (b0 - i * 0.075);
            orbPoint((float) Math.cos(a), (float) Math.sin(a));
            float depth = 0.35f + 0.65f * (pz + curR) / (2f * curR + 1e-4f);
            float al = (1f - i / 6f) * depth * tg;
            if (i == 0) g.dot(px, py, 6.5f * u * ps, pal.accent, 0.22f * al, true);
            g.dot(px, py, (1.8f - i * 0.22f) * u * ps, WHITE, 0.95f * al, true);
        }
    }

    private void drawOrbRelease(Gfx g, float qual) {
        int k = relK;
        if (k < 0 || k >= total) return;
        if (build) {
            if (relBurst) return;   // it is a normal (fresh) ring now
            // light gathers into a halo, then the halo turns and settles into its orbit
            float c = clamp01(relT / CHARGE_T);
            float m = easeOut(clamp01(c / 0.55f));
            float d = easeInOut(clamp01((c - 0.55f) / 0.45f));
            float sc = orbChargedScale(k) * (1f - d) + d;
            int n = orbRingPoints(k, 0f, sc, qual * 1.2f, 1f - d);
            int col = Palette.lerp(WHITE, pal.accent, 0.35f + 0.4f * d);
            specialLevels(n);
            drawSpecGlow(g, pal.accent, (0.08f + 0.30f * m) * (1f - 0.4f * d), 0.04f + 0.08f * m, (7f - 2f * d) * u);
            drawSpecLevels(g, col, 0.15f + 0.8f * m, 0.08f + 0.35f * m, (1.2f + 0.6f * m) * u);
            return;
        }
        if (!relBurst) {
            float c = clamp01(relT / CHARGE_T), e = easeInOut(c);
            float grow = (1.06f / Math.max(0.1f, oR[k]) - 1f) * easeInOut(clamp01(c / 0.8f));
            float sc = 1f + grow - 0.05f * clamp01((c - 0.8f) / 0.2f);
            int n = orbRingPoints(k, relSpin, sc, qual * 1.2f, easeInOut(clamp01(c / 0.85f)));
            int col = Palette.lerp(pal.accent, WHITE, 0.45f * e);
            specialLevels(n);
            drawSpecGlow(g, pal.accent, 0.10f + 0.28f * e, 0.04f + 0.08f * e, (6f + 5f * e) * u);
            drawSpecLevels(g, col, 0.85f + 0.15f * e, 0.35f + 0.3f * e, (1.5f + 1.1f * e) * u);
            int beads = 1 + (int) (e * 5f);
            for (int b = 0; b < beads; b++) {
                for (int i = 0; i < 5; i++) {
                    float a = oB0[k] + relBeadAng + b * TAU / beads - i * 0.09f * (1f + 2f * e);
                    orbPoint((float) Math.cos(a), (float) Math.sin(a));
                    float al = (1f - i / 5f) * (0.5f + 0.5f * e);
                    g.dot(px, py, (1.7f - i * 0.25f) * u * ps, WHITE, al, true);
                }
            }
        } else {
            float b = (relT - CHARGE_T) / 0.6f;
            if (b < 1f) {
                float e = easeOut(b);
                int n = orbRingPoints(k, relSpin, orbChargedScale(k) * (1f + 0.7f * e), qual * 1.2f, 1f);
                float a = (1f - b) * (1f - b);
                specialLevels(n);
                drawSpecGlow(g, pal.accent, 0.35f * a, 0.15f * a, 9f * u);
                drawSpecLevels(g, WHITE, 0.95f * a, 0.45f * a, (2.6f * (1f - b) + 0.6f) * u);
            }
        }
    }

    private void drawOrbCore(Gfx g) {
        float p = shownProg;
        float breathe = 1f + 0.06f * (float) Math.sin(time * 1.3);
        float cr = formR * (0.16f + 0.22f * p) * breathe * (1f + 0.25f * coreBoost);
        g.glow(cx, cy, cr * 3.2f, pal.core, 0.05f + 0.20f * p + 0.25f * coreBoost);
        g.glow(cx, cy, cr * 1.2f, pal.core, 0.10f + 0.42f * p + 0.35f * coreBoost);
        g.dot(cx, cy, u * (1.0f + 2.4f * p), WHITE, 0.45f + 0.5f * p, true);
        if (left == 0 && !rel) {
            float hr = formR * (build ? 1.08f : 0.44f) * breathe;
            g.ring(cx, cy, hr, pal.accent, 0.40f, 1.3f * u, true);
            g.ring(cx, cy, hr, pal.accent, 0.07f, 8f * u, true);
        }
    }

    private void drawDial(Gfx g, int done) {
        float R = formR * 1.17f;
        int n = total;
        float w = (n > 150 ? 0.55f : 0.85f) * u;
        bkN[FLAT] = 0; bkN[FLAT + 1] = 0;
        int todayIdx = left > 0 ? done : -1;
        float tx = 0, ty = 0, ux0 = 0, uy0 = 0;
        for (int i = 0; i < n; i++) {
            float ang = -PI * 0.5f + TAU * (i + 0.5f) / n;
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            if (i < tickLit) {
                addSeg(FLAT, cx + c * R, cy + s * R, cx + c * (R + 3.6f * u), cy + s * (R + 3.6f * u));
            } else if (i == todayIdx) {
                tx = c; ty = s;
            } else {
                addSeg(FLAT + 1, cx + c * (R + 0.8f * u), cy + s * (R + 0.8f * u), cx + c * (R + 2.6f * u), cy + s * (R + 2.6f * u));
            }
            if (i == tickFlashIdx) { ux0 = c; uy0 = s; }
        }
        g.lines(bk[FLAT], bkN[FLAT], pal.accent, 0.85f, w, true);
        g.lines(bk[FLAT + 1], bkN[FLAT + 1], WHITE, 0.14f, w, true);
        if (todayIdx >= 0) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.2);
            bkN[FLAT + 2] = 0;
            addSeg(FLAT + 2, cx + tx * (R - 0.8f * u), cy + ty * (R - 0.8f * u), cx + tx * (R + 4.8f * u), cy + ty * (R + 4.8f * u));
            g.lines(bk[FLAT + 2], bkN[FLAT + 2], pal.a, (0.45f + 0.45f * pulse) * Math.max(0.25f, todayGlow), w * 1.7f, true);
        }
        if (tickFlash > 0.02f && tickFlashIdx >= 0) {
            float fx = cx + ux0 * (R + 1.8f * u), fy = cy + uy0 * (R + 1.8f * u);
            g.glow(fx, fy, 14f * u, pal.accent, 0.9f * tickFlash);
            g.dot(fx, fy, 1.6f * u, WHITE, tickFlash, true);
        }
    }

    /** Ring scale at the end of the charge: lifted off the orb to a halo just inside the dial. */
    private float orbChargedScale(int k) {
        return 1.06f / Math.max(0.1f, oR[k]) - 0.05f;
    }

    private void burstOrb(float q) {
        int k = relK;
        orbRingPoints(k, relSpin, orbChargedScale(k), 1f, 1f);
        int cnt = (int) (190 * q);
        for (int i = 0; i < cnt; i++) {
            float th = rnd() * TAU;
            float c = (float) Math.cos(th), s = (float) Math.sin(th);
            float x = curR * (c * curUx + s * curVx), y = curR * (c * curUy + s * curVy), z = curR * (c * curUz + s * curVz);
            float inv = 1f / Math.max(1e-3f, curR);
            float tx = -s * curUx + c * curVx, ty = -s * curUy + c * curVy, tz = -s * curUz + c * curVz;
            float sp = 0.35f + rnd() * rnd() * 2.4f, tan = (rnd() - 0.5f) * 0.6f;
            float vx = x * inv * sp + tx * tan + (rnd() - 0.5f) * 0.35f;
            float vy = y * inv * sp + ty * tan + (rnd() - 0.5f) * 0.35f + 0.18f;
            float vz = z * inv * sp + tz * tan + (rnd() - 0.5f) * 1.4f;
            float pick = rnd();
            int col = pick < 0.45f ? pal.accent : (pick < 0.68f ? WHITE : bandCol[oBand[k]]);
            parts.add(Particles.SPARK3D, x, y, z, vx, vy, vz, 0.9f + rnd() * 1.5f, 0.45f + rnd() * 1.1f, col, rnd() * TAU);
        }
        homingToTick(k, cfg.lowPower ? 10 : 22);
        shockX = cx; shockY = cy; shockR0 = formR * 1.0f; shockR1 = formR * 2.1f; shockT = 0f;
        flashX = cx; flashY = cy; flashR = formR * 1.5f; flashT = 0f; flashA = 0.45f;
        waveFrom = total == 1 ? 0f : k / (float) (total - 1);
        waveTo = 1f;
        waveT = 0f;
    }

    /** A stream of light from the last computed ring into day k's tick on the dial. */
    private void homingToTick(int k, int cnt) {
        float ang = -PI * 0.5f + TAU * (k + 0.5f) / total;
        float R = formR * 1.17f + 1.8f * u;
        float tx = cx + (float) Math.cos(ang) * R, ty = cy + (float) Math.sin(ang) * R;
        for (int i = 0; i < cnt; i++) {
            float th = rnd() * TAU;
            orbPoint((float) Math.cos(th), (float) Math.sin(th));
            float dx = px - cx, dy = py - cy, dl = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
            float sp = (110f + rnd() * 160f) * u;
            int id = parts.add(Particles.HOMING, px, py, 0, dx / dl * sp + (rnd() - 0.5f) * 90f * u,
                    dy / dl * sp + (rnd() - 0.5f) * 90f * u, 0, 3f, 0.7f + rnd() * 0.6f, pal.accent, rnd() * TAU);
            if (id >= 0) { parts.tx[id] = tx; parts.ty[id] = ty; }
        }
    }

    /** Build mode, orb: the new ring has settled into its orbit. */
    private void formOrb(float q) {
        int k = relK;
        orbRingPoints(k, 0f, 1f, 1f);
        int cnt = (int) (80 * q);
        for (int i = 0; i < cnt; i++) {
            float th = rnd() * TAU;
            float c = (float) Math.cos(th), s = (float) Math.sin(th);
            float x = curR * (c * curUx + s * curVx), y = curR * (c * curUy + s * curVy), z = curR * (c * curUz + s * curVz);
            float inv = 1f / Math.max(1e-3f, curR), sp = 0.12f + rnd() * 0.5f;
            parts.add(Particles.SPARK3D, x, y, z, x * inv * sp + (rnd() - 0.5f) * 0.2f, y * inv * sp + (rnd() - 0.5f) * 0.2f + 0.1f,
                    z * inv * sp + (rnd() - 0.5f) * 0.3f, 0.6f + rnd() * 0.8f, 0.4f + rnd() * 0.8f,
                    rnd() < 0.55f ? pal.accent : WHITE, rnd() * TAU);
        }
        homingToTick(k, cfg.lowPower ? 8 : 16);
        float rp = formR * oR[k];
        shockX = cx; shockY = cy; shockR0 = rp * 0.95f; shockR1 = rp + formR * 0.55f; shockT = 0f;
        flashX = cx; flashY = cy; flashR = formR * 1.1f; flashT = 0f; flashA = 0.35f;
        waveFrom = total == 1 ? 0f : k / (float) (total - 1);
        waveTo = 0f;
        waveT = 0f;
        freshK = k;
        freshT = 0f;
    }

    /** Build mode: light starts flowing toward the ring that's about to form. */
    private void gather() {
        int k = relK;
        float q = cfg.lowPower ? 0.5f : 1f;
        float sp = relSpeed;
        int[] cols = {pal.accent, WHITE, pal.a};
        if (cfg.style == STYLE_GRID) {
            float gx = gX[k], gy = gY[k];
            int cnt = (int) (70 * q);
            for (int i = 0; i < cnt; i++) {
                float a = rnd() * TAU, r = gCell * (1.8f + rnd() * 2.8f);
                float t = rnd() * TAU;
                addConverge(gx + (float) Math.cos(a) * r, gy + (float) Math.sin(a) * r,
                        gx + (float) Math.cos(t) * gRingR, gy + (float) Math.sin(t) * gRingR * 0.92f,
                        (0.45f + rnd() * 0.35f) / sp, (rnd() - 0.5f) * gCell, cols[(int) (rnd() * 3)]);
            }
        } else if (cfg.style == STYLE_TUNNEL) {
            float d = TUN_D0 + k * TUN_DZ;
            tunnelCenter(d);
            float s = formR * TUN_D0 / d;
            float X = cx + tcX * s, Y = cy - tcY * s;
            int cnt = (int) (90 * q);
            for (int i = 0; i < cnt; i++) {
                float a = rnd() * TAU;
                float c = (float) Math.cos(a), sn = (float) Math.sin(a);
                addConverge(cx + c * W * (0.55f + rnd() * 0.3f), cy + sn * H * (0.35f + rnd() * 0.2f),
                        X + c * s, Y + sn * s * 0.97f, (0.5f + rnd() * 0.35f) / sp, (rnd() - 0.5f) * formR * 0.3f,
                        cols[(int) (rnd() * 3)]);
            }
        } else {
            int n = orbRingPoints(k, 0f, orbChargedScale(k), 1f, 1f);   // the halo it gathers into
            int cnt = (int) (130 * q);
            for (int i = 0; i < cnt; i++) {
                int idx = (int) (rnd() * (n - 1));
                float a = rnd() * TAU, r = formR * (1.7f + rnd() * 1.3f);
                addConverge(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r * 1.15f,
                        ptX[idx], ptY[idx], (0.34f + rnd() * 0.22f) / sp, (rnd() - 0.5f) * formR * 0.6f,
                        cols[(int) (rnd() * 3)]);
            }
        }
    }

    private void addConverge(float sx, float sy, float tx, float ty, float life, float bend, int col) {
        int id = parts.add(Particles.CONVERGE, sx, sy, bend, sx, sy, 0, life, 0.5f + rnd() * 0.7f, col, rnd() * TAU);
        if (id >= 0) { parts.tx[id] = tx; parts.ty[id] = ty; }
    }

    // ------------------------------------- GRID -----------------------------------------

    private float gridTheta(int col, int row) {
        double sway = 0.42 * Math.sin(motion * 0.55 + col * 0.42 + row * 0.27);
        double cycle = 12.0, delay = (col + row) * 0.075;
        double local = ((motion - delay) % cycle + cycle) % cycle;
        double flip = local < 1.6 ? TAU * easeInOut((float) (local / 1.6)) : 0.0;
        double lt = time - gridTapT - Math.hypot(col - gridTapCol, row - gridTapRow) * 0.07;
        if (lt > 0 && lt < 1.1) flip += TAU * easeInOut((float) (lt / 1.1));
        return (float) (sway + flip);
    }

    /** Projects grid ring k. ptZ is normalized depth -1..1. Returns point count. */
    private int gridRingPoints(int k, float extraTheta, float scale) {
        int col = k % gCols, row = k / gCols;
        float th = gridTheta(col, row) + extraTheta;
        float ct = (float) Math.cos(th), st = (float) Math.sin(th);
        float tc = (float) Math.cos(GRID_TILT), ts = (float) Math.sin(GRID_TILT);
        float rr = gRingR * scale;
        float D = gRingR * 4.6f;
        int segs = clampi((int) (rr / u * 0.9f), 16, 64);
        float[] cs = cosTab(segs), sn = sinTab(segs);
        float gx = gX[k], gy = gY[k];
        for (int s = 0; s <= segs; s++) {
            float x = rr * cs[s], y = rr * sn[s];
            float x1 = x * ct, z1 = -x * st;
            float y2 = y * tc - z1 * ts, z2 = y * ts + z1 * tc;
            float p = D / Math.max(D * 0.2f, D - z2);
            ptX[s] = gx + x1 * p;
            ptY[s] = gy - y2 * p;
            ptZ[s] = z2 / Math.max(1e-3f, rr);
        }
        return segs + 1;
    }

    private float rippleBump(int k) {
        if (waveT >= 1f) return 0f;
        int col = k % gCols, row = k / gCols;
        float dist = (float) Math.hypot(col - waveCol, row - waveRow);
        float d = dist - easeOut(waveT) * waveMax;
        return 0.30f * (float) Math.exp(-d * d / 1.1f) * (1f - waveT);
    }

    private int gridBand(int k) {
        return Math.min(BANDS - 1, (int) (k / (float) total * BANDS));
    }

    private void drawGrid(Gfx g) {
        int done = total - left;
        clearBuckets();
        float dr = Math.max(1.3f * u, gRingR * 0.12f);
        int todayK = (left > 0 && (!rel || relBurst)) ? done : -1;
        float dimAll = 1f - 0.35f * focusAmount();
        if (!build) {
            for (int k = 0; k < done; k++) {
                if (rel && relBurst && k == relK) continue;
                addPt(FLAT, gX[k], gY[k]);
            }
            float tw = 0.85f + 0.15f * (float) Math.sin(time * 1.7);
            if (bkN[FLAT] > 0) {
                g.points(bk[FLAT], bkN[FLAT], pal.accent, 0.06f, dr * 9f, true);
                g.points(bk[FLAT], bkN[FLAT], pal.accent, 0.12f, dr * 4.5f, true);
                g.points(bk[FLAT], bkN[FLAT], Palette.lerp(pal.accent, WHITE, 0.25f), 0.9f * tw, dr * 2f, true);
            }
            if (rel && relBurst && relK >= 0 && relK < done) {
                float p = elastic(clamp01((relT - CHARGE_T) / 0.7f));
                g.glow(gX[relK], gY[relK], dr * 9f, pal.accent, 0.35f * (1f - clamp01((relT - CHARGE_T) / 1.2f)));
                g.dot(gX[relK], gY[relK], dr * p, pal.accent, 0.9f, true);
            }
            for (int k = done; k < total; k++) {
                if (rel && k == relK) continue;
                float sc = 1f + rippleBump(k);
                float fade = 1f;
                if (k == restoreK && restoreT < 1f) { sc *= 0.3f + 0.7f * easeOutBack(restoreT); fade = restoreT; }
                int n = gridRingPoints(k, 0f, sc);
                if (k == todayK) {
                    drawGridToday(g, n, k, fade, false);
                    continue;
                }
                addGridSegs(n, gridBand(k) * LV);
            }
        } else {
            // days still to come: small placeholders
            for (int k = done; k < total; k++) {
                if (k == todayK || (rel && !relBurst && k == relK) || (k == restoreK && restoreT < 1f)) continue;
                addPt(FLAT, gX[k], gY[k]);
            }
            if (bkN[FLAT] > 0) g.points(bk[FLAT], bkN[FLAT], WHITE, 0.17f * dimAll, dr * 1.5f, true);
            // built rings
            for (int k = 0; k < done; k++) {
                float sc = 1f + rippleBump(k);
                if (k == freshK && freshT < 1f) sc *= 0.55f + 0.45f * easeOutBack(clamp01(freshT * 3f));
                int n = gridRingPoints(k, 0f, sc);
                addGridSegs(n, gridBand(k) * LV);
                if (k == freshK && freshT < 1f) {
                    float a = 1f - freshT;
                    specialLevels(n);
                    drawSpecGlow(g, pal.accent, 0.35f * a, 0.12f * a, 5f * u);
                    drawSpecLevels(g, Palette.lerp(pal.accent, WHITE, 0.3f), 0.9f * a, 0.4f * a, 1.5f * u);
                }
            }
            if (todayK >= 0) drawGridToday(g, gridRingPoints(todayK, 0f, 1f), todayK, 1f, true);
            if (restoreK >= done && restoreK < total && restoreT < 1f) {
                float a = 1f - restoreT;
                int n = gridRingPoints(restoreK, 0f, 1f - 0.3f * restoreT);
                specialLevels(n);
                drawSpecLevels(g, bandCol[gridBand(restoreK)], 0.8f * a, 0.4f * a, 1.1f * u);
            }
        }
        flushBuckets(g, GRID_CA, GRID_GA, GRID_CW, GRID_GW, dimAll, !cfg.lowPower);
        if (rel) drawGridRelease(g);
    }

    private void addGridSegs(int n, int base) {
        for (int s = 0; s < n - 1; s++) {
            float zm = (ptZ[s] + ptZ[s + 1]) * 0.5f;
            int lv = clampi((int) ((zm + 1f) * 0.5f * LV), 0, LV - 1);
            addSeg(base + lv, ptX[s], ptY[s], ptX[s + 1], ptY[s + 1]);
        }
    }

    private void drawGridToday(Gfx g, int n, int k, float fade, boolean ghost) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.2);
        float tg = Math.max(0f, todayGlow) * fade;
        if (tg <= 0.01f) return;
        float m = ghost ? 0.5f : 1f;
        g.glow(gX[k], gY[k], gRingR * 2.6f, pal.accent, (0.10f + 0.08f * pulse) * tg * m);
        specialLevels(n);
        drawSpecGlow(g, pal.accent, (0.10f + 0.06f * pulse) * tg * m, 0.04f * tg * m, 5f * u);
        drawSpecLevels(g, Palette.lerp(pal.accent, WHITE, 0.2f), (ghost ? 0.4f + 0.25f * pulse : 0.8f + 0.2f * pulse) * tg,
                (ghost ? 0.18f : 0.4f) * tg, (ghost ? 1.1f : 1.5f) * u);
        int idx = (int) (((time * 0.35) % 1.0) * (n - 1));
        g.dot(ptX[idx], ptY[idx], 1.6f * u, WHITE, 0.95f * tg, true);
        g.dot(ptX[idx], ptY[idx], 4.5f * u, pal.accent, 0.25f * tg, true);
    }

    private void drawGridRelease(Gfx g) {
        int k = relK;
        if (k < 0 || k >= total) return;
        if (build) {
            if (relBurst) return;
            float c = clamp01(relT / CHARGE_T), e = easeInOut(c);
            float sc = 1f + 0.25f * (float) Math.sin(PI * c);
            int n = gridRingPoints(k, TAU * 2f * e, sc);          // two flips, ends upright
            g.glow(gX[k], gY[k], gRingR * 3.2f, pal.accent, 0.06f + 0.30f * e);
            specialLevels(n);
            drawSpecGlow(g, pal.accent, 0.05f + 0.30f * e, 0.03f + 0.1f * e, (4f + 3f * e) * u);
            drawSpecLevels(g, Palette.lerp(WHITE, pal.accent, 0.4f), 0.15f + 0.8f * e, 0.08f + 0.4f * e, (1.1f + 0.6f * e) * u);
            return;
        }
        if (!relBurst) {
            float c = clamp01(relT / CHARGE_T), e = easeInOut(c);
            float sc = 1f + 0.5f * easeOutBack(clamp01(c / 0.75f));
            int n = gridRingPoints(k, relSpin * 1.6f, sc);
            g.glow(gX[k], gY[k], gRingR * 3.6f * sc, pal.accent, 0.12f + 0.3f * e);
            specialLevels(n);
            drawSpecGlow(g, pal.accent, 0.12f + 0.25f * e, 0.05f + 0.1f * e, (5f + 4f * e) * u);
            drawSpecLevels(g, Palette.lerp(pal.accent, WHITE, 0.5f * e), 0.9f + 0.1f * e, 0.45f + 0.3f * e, (1.5f + 0.9f * e) * u);
        } else {
            float b = (relT - CHARGE_T) / 0.45f;
            if (b < 1f) {
                int n = gridRingPoints(k, relSpin * 1.6f, 1.5f + 1.7f * easeOut(b));
                float a = (1f - b) * (1f - b);
                specialLevels(n);
                drawSpecLevels(g, WHITE, 0.95f * a, 0.5f * a, (2.2f * (1f - b) + 0.5f) * u);
            }
        }
    }

    private void burstGrid(float q) {
        int k = relK;
        int n = gridRingPoints(k, relSpin * 1.6f, 1.5f);
        float gx = gX[k], gy = gY[k];
        int cnt = (int) (120 * q);
        for (int i = 0; i < cnt; i++) {
            int s = (int) (rnd() * (n - 1));
            float x = ptX[s], y = ptY[s];
            float dx = x - gx, dy = y - gy, dl = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
            float sp = (80f + rnd() * 240f) * u;
            float pick = rnd();
            int col = pick < 0.5f ? pal.accent : (pick < 0.72f ? WHITE : bandCol[gridBand(k)]);
            parts.add(Particles.SPARK2D, x, y, 0, dx / dl * sp + (rnd() - 0.5f) * 60f * u,
                    dy / dl * sp + (rnd() - 0.5f) * 60f * u - 40f * u, 0,
                    0.8f + rnd() * 1.1f, 0.6f + rnd() * 1.0f, col, rnd() * TAU);
        }
        homingToBar(gx, gy, cfg.lowPower ? 8 : 16);
        shockX = gx; shockY = gy; shockR0 = gRingR * 1.5f; shockR1 = gRingR * 1.5f + gCell * 2.6f; shockT = 0f;
        flashX = gx; flashY = gy; flashR = gCell * 2.4f; flashT = 0f; flashA = 0.55f;
        waveCol = k % gCols; waveRow = k / gCols;
        waveMax = (float) Math.hypot(gCols, gRows);
        waveT = 0f;
    }

    /** Build mode, grid: the new ring pops into its slot. */
    private void formGrid(float q) {
        int k = relK;
        float gx = gX[k], gy = gY[k];
        int cnt = (int) (50 * q);
        for (int i = 0; i < cnt; i++) {
            float a = rnd() * TAU, sp = (40f + rnd() * 120f) * u;
            parts.add(Particles.SPARK2D, gx + (float) Math.cos(a) * gRingR, gy + (float) Math.sin(a) * gRingR, 0,
                    (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 30f * u, 0,
                    0.6f + rnd() * 0.7f, 0.5f + rnd() * 0.8f, rnd() < 0.55f ? pal.accent : WHITE, rnd() * TAU);
        }
        homingToBar(gx, gy, cfg.lowPower ? 8 : 14);
        shockX = gx; shockY = gy; shockR0 = gRingR; shockR1 = gRingR + gCell * 2.2f; shockT = 0f;
        flashX = gx; flashY = gy; flashR = gCell * 2.2f; flashT = 0f; flashA = 0.5f;
        waveCol = k % gCols; waveRow = k / gCols;
        waveMax = (float) Math.hypot(gCols, gRows);
        waveT = 0f;
        freshK = k;
        freshT = 0f;
    }

    private void homingToBar(float fromX, float fromY, int cnt) {
        float tx = textX - barW * 0.5f + barW * progress();
        float ty = barY + 0.8f * u;
        for (int i = 0; i < cnt; i++) {
            float a = rnd() * TAU, sp = (90f + rnd() * 150f) * u;
            int id = parts.add(Particles.HOMING, fromX, fromY, 0, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                    0, 3f, 0.7f + rnd() * 0.6f, pal.accent, rnd() * TAU);
            if (id >= 0) { parts.tx[id] = tx; parts.ty[id] = ty; }
        }
    }

    // ------------------------------------ TUNNEL ----------------------------------------

    private void tunnelCenter(float d) {
        float ramp = 1f - (float) Math.exp(-(d - TUN_D0) / 5f);
        tcX = 3.0f * ramp * (float) Math.sin(d * 0.11 + motion * 0.13);
        tcY = 2.1f * ramp * (float) Math.cos(d * 0.085 + motion * 0.10);
    }

    /** Adds the four arcs of a tunnel ring at distance d into bucket b. */
    private void tunnelRing(int b, float d, float psi, float gap, float qual) {
        tunnelCenter(d);
        float f = formR * TUN_D0;
        float rp = f / d;
        int segs = clampi((int) (rp / (1.6f * u) * qual), 16, 128);
        float tilt = 0.22f * Math.min(1f, d);
        float tc = (float) Math.cos(tilt), ts = (float) Math.sin(tilt);
        int perArc = Math.max(2, segs / 4);
        float g8 = gap * TAU / 8f;
        for (int q = 0; q < 4; q++) {
            float a0 = q * TAU / 4f + g8, a1 = (q + 1) * TAU / 4f - g8;
            float lx = 0, ly = 0;
            boolean have = false;
            for (int i = 0; i <= perArc; i++) {
                float a = psi + a0 + (a1 - a0) * i / perArc;
                float x = (float) Math.cos(a), y = (float) Math.sin(a);
                float depth = d - y * ts;
                if (depth < 0.06f) { have = false; continue; }
                float sc = f / depth;
                float sx = cx + (tcX + x) * sc, sy = cy - (tcY + y * tc) * sc;
                if (have) addSeg(b, lx, ly, sx, sy);
                lx = sx; ly = sy; have = true;
            }
        }
    }

    private void drawTunnel(Gfx g) {
        if (build) { drawTunnelBuild(g); return; }
        int done = total - left;
        float qual = cfg.lowPower ? 0.6f : 1f;
        float shift = 0f;
        if (rel && relBurst) shift = 1f - easeInOut(clamp01((relT - CHARGE_T) / 1.3f));
        float dMax = TUN_D0 + Math.max(1, total) * TUN_DZ;
        float lnMax = (float) Math.log(dMax / TUN_D0) + 1e-3f;
        float f = formR * TUN_D0;
        float dimAll = 1f - 0.35f * focusAmount();

        drawTunnelLight(g, shift);
        clearBuckets();
        boolean charging = rel && !relBurst;
        int todayK = (left > 0 && (!rel || relBurst)) ? done : -1;
        float baseSpin = (float) ((motion * 0.12) % TAU);
        for (int j = 0; j < left; j++) {
            int k = done + j;
            if (charging && k == relK) continue;
            float d = TUN_D0 + (j + shift) * TUN_DZ;
            float fade = 1f;
            if (k == restoreK && restoreT < 1f) { d = TUN_D0 * (0.35f + 0.65f * easeOut(restoreT)); fade = restoreT; }
            if (f / d < 1.3f) break;
            float psi = baseSpin + k * 0.11f;
            if (k == todayK) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.2);
                float tg = Math.max(0f, todayGlow) * fade;
                bkN[FLAT] = 0;
                tunnelRing(FLAT, d, psi, 0.07f, qual);
                g.glowLines(bk[FLAT], bkN[FLAT], pal.accent, (0.09f + 0.05f * pulse) * tg * GLOW_K, 7f * u);
                g.lines(bk[FLAT], bkN[FLAT], Palette.lerp(pal.accent, WHITE, 0.2f), (0.75f + 0.2f * pulse) * tg, 1.6f * u, true);
                continue;
            }
            int lv = LV - 1 - clampi((int) (Math.log(d / TUN_D0) / lnMax * LV), 0, LV - 1);
            int band = Math.min(BANDS - 1, (int) (k / (float) total * BANDS));
            tunnelRing(band * LV + lv, d, psi, 0.07f, qual);
        }
        flushBuckets(g, TUN_CA, TUN_GA, TUN_CW, TUN_GW, dimAll, !cfg.lowPower);
        if (rel) drawTunnelRelease(g, baseSpin);
    }

    private void drawTunnelLight(Gfx g, float shift) {
        float dEnd = TUN_D0 + ((build ? total : left) + shift) * TUN_DZ + 0.8f;
        tunnelCenter(dEnd);
        float s = formR * TUN_D0 / dEnd;
        float X = cx + tcX * s, Y = cy - tcY * s;
        float p = shownProg;
        float lr = build ? (5f + 24f * p) * u : Math.max(5f * u, s * (0.5f + 0.9f * p));
        g.glow(X, Y, lr * 3.2f, pal.core, 0.10f + 0.32f * p + 0.3f * coreBoost);
        g.glow(X, Y, lr * 1.1f, WHITE, 0.16f + 0.42f * p + 0.3f * coreBoost);
        g.dot(X, Y, Math.max(1.2f * u, lr * 0.12f), WHITE, 0.85f, true);
        if (left == 0 && !rel) {
            float breathe = 1f + 0.05f * (float) Math.sin(time * 1.3);
            g.ring(X, Y, lr * 1.25f * breathe, pal.accent, 0.35f, 1.3f * u, true);
        }
    }

    /** Build mode tunnel: gates sit at fixed depths; built ones are lit, the rest are a faint blueprint. */
    private void drawTunnelBuild(Gfx g) {
        int done = total - left;
        float qual = cfg.lowPower ? 0.6f : 1f;
        float dMax = TUN_D0 + Math.max(1, total) * TUN_DZ;
        float lnMax = (float) Math.log(dMax / TUN_D0) + 1e-3f;
        float f = formR * TUN_D0;
        float dimAll = 1f - 0.35f * focusAmount();
        drawTunnelLight(g, 0f);
        clearBuckets();
        int todayK = (left > 0 && (!rel || relBurst)) ? done : -1;
        float baseSpin = (float) ((motion * 0.12) % TAU);
        for (int k = 0; k < total; k++) {
            float d = TUN_D0 + k * TUN_DZ;
            if (f / d < 1.3f) break;
            float psi = baseSpin + k * 0.11f;
            if (k < done) {
                int lv = LV - 1 - clampi((int) (Math.log(d / TUN_D0) / lnMax * LV), 0, LV - 1);
                int band = Math.min(BANDS - 1, (int) (k / (float) total * BANDS));
                tunnelRing(band * LV + lv, d, psi, 0.07f, qual);
                if (k == freshK && freshT < 1f) {
                    float a = 1f - freshT;
                    bkN[FLAT] = 0;
                    tunnelRing(FLAT, d, psi, 0.07f, qual);
                    g.glowLines(bk[FLAT], bkN[FLAT], pal.accent, 0.4f * a * GLOW_K, 8f * u);
                    g.lines(bk[FLAT], bkN[FLAT], Palette.lerp(pal.accent, WHITE, 0.3f), 0.9f * a, 1.6f * u, true);
                }
            } else if (k == todayK) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.2);
                float tg = Math.max(0f, todayGlow);
                bkN[FLAT] = 0;
                tunnelRing(FLAT, d, psi, 0.07f, qual);
                g.glowLines(bk[FLAT], bkN[FLAT], pal.accent, (0.05f + 0.04f * pulse) * tg * GLOW_K, 6f * u);
                g.lines(bk[FLAT], bkN[FLAT], Palette.lerp(pal.accent, WHITE, 0.2f), (0.38f + 0.22f * pulse) * tg, 1.1f * u, true);
            } else if (rel && !relBurst && k == relK) {
                // being built, drawn by the release animation
            } else if (k == restoreK && restoreT < 1f) {
                float a = 1f - restoreT;
                bkN[FLAT] = 0;
                tunnelRing(FLAT, d, psi, 0.07f, qual);
                g.lines(bk[FLAT], bkN[FLAT], bandCol[Math.min(BANDS - 1, (int) (k / (float) total * BANDS))], 0.8f * a, 1.1f * u, true);
            } else {
                tunnelRing(k - done < 8 ? GHOST : GHOST + 1, d, psi, 0.07f, qual * 0.6f);
            }
        }
        flushBuckets(g, TUN_CA, TUN_GA, TUN_CW, TUN_GW, dimAll, !cfg.lowPower);
        int gc = Palette.lerp(pal.a, WHITE, 0.3f);
        g.lines(bk[GHOST], bkN[GHOST], gc, 0.13f * dimAll, 0.6f * u, true);
        g.lines(bk[GHOST + 1], bkN[GHOST + 1], gc, 0.06f * dimAll, 0.5f * u, true);
        if (rel) drawTunnelRelease(g, baseSpin);
    }

    private void drawTunnelRelease(Gfx g, float baseSpin) {
        int k = relK;
        if (k < 0) return;
        bkN[FLAT] = 0;
        if (build) {
            if (relBurst) return;
            float c = clamp01(relT / CHARGE_T), e = easeInOut(c);
            float psi = baseSpin + k * 0.11f + PI * e;        // half turn: the 4 arcs line up again
            tunnelRing(FLAT, TUN_D0 + k * TUN_DZ, psi, 0.07f * (1f - e), 1.3f);
            g.glowLines(bk[FLAT], bkN[FLAT], pal.accent, (0.06f + 0.3f * e) * GLOW_K, (6f + 4f * e) * u);
            g.lines(bk[FLAT], bkN[FLAT], Palette.lerp(WHITE, pal.accent, 0.4f), 0.15f + 0.8f * e, (1.1f + 0.8f * e) * u, true);
            return;
        }
        float psi = baseSpin + k * 0.11f + relSpin;
        if (!relBurst) {
            float c = clamp01(relT / CHARGE_T), e = easeInOut(c);
            tunnelRing(FLAT, TUN_D0 * (1f - 0.05f * e), psi, 0.07f * (1f - e), 1.3f);
            g.glowLines(bk[FLAT], bkN[FLAT], pal.accent, (0.12f + 0.28f * e) * GLOW_K, (7f + 6f * e) * u);
            g.lines(bk[FLAT], bkN[FLAT], Palette.lerp(pal.accent, WHITE, 0.5f * e), 0.85f + 0.15f * e, (1.6f + 1.2f * e) * u, true);
        } else {
            float b = clamp01((relT - CHARGE_T) / 0.6f);
            if (b < 1f) {
                float d = TUN_D0 * (1f - 0.8f * easeIn(b));
                float a = (1f - b) * (1f - b);
                tunnelRing(FLAT, d, psi, 0f, 1.3f);
                g.glowLines(bk[FLAT], bkN[FLAT], pal.accent, 0.35f * a * GLOW_K, 14f * u);
                g.lines(bk[FLAT], bkN[FLAT], WHITE, 0.95f * a, (2f + 6f * b) * u, true);
            }
        }
    }

    private void burstTunnel(float q) {
        int cnt = (int) (90 * q);
        for (int i = 0; i < cnt; i++) {
            float a = rnd() * TAU, r0 = (0.06f + rnd() * 0.3f) * formR;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float sp = (60f + rnd() * 120f) * u;
            parts.add(Particles.STREAK, cx + c * r0, cy + s * r0, 0, c * sp, s * sp, 0,
                    0.6f + rnd() * 0.6f, 0.8f + rnd() * 0.6f, rnd() < 0.5f ? WHITE : pal.a, rnd() * TAU);
        }
        int sc = (int) (60 * q);
        for (int i = 0; i < sc; i++) {
            float a = rnd() * TAU, r0 = formR * (0.95f + rnd() * 0.1f);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float sp = (40f + rnd() * 140f) * u;
            parts.add(Particles.SPARK2D, cx + c * r0, cy + s * r0, 0, c * sp, s * sp, 0,
                    0.7f + rnd() * 0.9f, 0.6f + rnd() * 0.9f, rnd() < 0.55f ? pal.accent : WHITE, rnd() * TAU);
        }
        homingToBar(cx, cy, cfg.lowPower ? 8 : 16);
        shockX = cx; shockY = cy; shockR0 = formR * 0.9f; shockR1 = formR * 1.9f; shockT = 0f;
        flashX = cx; flashY = cy; flashR = formR * 1.7f; flashT = 0f; flashA = 0.5f;
        waveT = 0f;
    }

    /** Build mode, tunnel: the new gate locks in. */
    private void formTunnel(float q) {
        int k = relK;
        float d = TUN_D0 + k * TUN_DZ;
        tunnelCenter(d);
        float s = formR * TUN_D0 / d;
        float X = cx + tcX * s, Y = cy - tcY * s;
        int cnt = (int) (50 * q);
        for (int i = 0; i < cnt; i++) {
            float a = rnd() * TAU, sp = (30f + rnd() * 110f) * u;
            parts.add(Particles.SPARK2D, X + (float) Math.cos(a) * s, Y + (float) Math.sin(a) * s, 0,
                    (float) Math.cos(a) * sp, (float) Math.sin(a) * sp, 0,
                    0.6f + rnd() * 0.7f, 0.5f + rnd() * 0.8f, rnd() < 0.55f ? pal.accent : WHITE, rnd() * TAU);
        }
        homingToBar(X, Y, cfg.lowPower ? 8 : 14);
        shockX = X; shockY = Y; shockR0 = s; shockR1 = Math.max(s * 2.2f, s + 40f * u); shockT = 0f;
        flashX = X; flashY = Y; flashR = Math.max(s * 2.4f, 40f * u); flashT = 0f; flashA = 0.45f;
        waveT = 0f;
        freshK = k;
        freshT = 0f;
    }

    // ---------------------------------- shared fx ---------------------------------------

    private void drawFx(Gfx g) {
        if (shockT < 1f) {
            float e = easeOut(shockT);
            float r = shockR0 + (shockR1 - shockR0) * e;
            float a = (1f - shockT);
            g.ring(shockX, shockY, r, WHITE, 0.55f * a * a, (0.6f + 2.6f * (1f - e)) * u, true);
            g.ring(shockX, shockY, r, pal.accent, 0.12f * a, 9f * u, true);
        }
        if (flashT < 1f) {
            float a = (1f - flashT);
            g.glow(flashX, flashY, flashR * (0.6f + 0.4f * flashT), WHITE, flashA * a * a);
        }
    }

    private void drawTaps(Gfx g) {
        for (int i = 0; i < tapT.length; i++) {
            float t = tapT[i];
            if (t >= 1f) continue;
            float r = 6f * u + 46f * u * easeOut(t);
            g.ring(tapX[i], tapY[i], r, WHITE, 0.45f * (1f - t), 1.2f * u, true);
        }
    }

    private void drawParticles(Gfx g) {
        Particles p = parts;
        for (int i = 0; i < p.n; i++) {
            float lifeF = p.life[i] / p.max[i];
            float a = Math.min(1f, lifeF * 2.2f) * (0.72f + 0.28f * (float) Math.sin(time * 17.0 + p.ph[i]));
            float x, y, sz;
            byte k = p.kind[i];
            if (k == Particles.SPARK3D) {
                float depth = ORB_D - p.z[i];
                if (depth < 0.4f) continue;
                float sc = orbF / depth;
                x = cx + p.x[i] * sc;
                y = cy - p.y[i] * sc;
                sz = p.size[i] * u * (sc / formR);
            } else {
                x = p.x[i];
                y = p.y[i];
                sz = p.size[i] * u;
            }
            if (k == Particles.STREAK) {
                bkN[FLAT] = 0;
                addSeg(FLAT, x, y, x - p.vx[i] * 0.045f, y - p.vy[i] * 0.045f);
                g.lines(bk[FLAT], 4, p.color[i], 0.8f * a, sz, true);
                continue;
            }
            if (k == Particles.HOMING) a = Math.max(a, 0.75f);
            if (k == Particles.CONVERGE) a = Math.min(1f, (1f - lifeF) * 5f) * (0.75f + 0.25f * (float) Math.sin(time * 17.0 + p.ph[i]));
            if (k == Particles.EMBER) a *= Math.min(1f, (p.max[i] - p.life[i]) * 2f) * 0.8f;
            g.dot(x, y, sz * 2.6f, p.color[i], 0.13f * a, true);
            g.dot(x, y, sz * 0.8f, p.color[i], a, true);
        }
    }

    // ------------------------------------- text -----------------------------------------

    private void drawText(Gfx g) {
        float x = textX, ts = txtScale;
        if (goalUpper.length() > 0) {
            g.text(goalUpper, x, goalY, 10.5f * u * ts, WHITE, 0.62f, Gfx.FONT_MEDIUM, 0.28f, Gfx.ALIGN_CENTER);
        }
        if (cntT < 1f) {
            float e = easeOutCubic(cntT);
            float off = numSize * 0.42f;
            boolean dayDone = cntTo < cntFrom;                  // left went down: a day completed
            boolean up = build == dayDone;                       // displayed number went up
            drawNumber(g, cntFrom, x, numBase + (up ? e : -e) * off, 1f - e);
            drawNumber(g, cntTo, x, numBase + (up ? -(1f - e) : (1f - e)) * off, e);
            if (dayDone) g.glow(x, numBase - numSize * 0.36f, numSize * 1.15f, pal.accent, 0.28f * (1f - e));
        } else {
            drawNumber(g, left, x, numBase, 1f);
        }

        float m = messageAlpha();
        float labA = clamp01(1f - m * 2f);          // labels fade out first...
        float msgA = clamp01(m * 2f - 1f);          // ...then the message fades in
        String lab;
        if (left == 0) lab = "DAYS COMPLETE";
        else if (build) lab = (total - left) == 1 ? "RING BUILT" : "RINGS BUILT";
        else lab = left == 1 ? "RING LEFT" : "RINGS LEFT";
        if (labA > 0f) {
            g.text(lab, x, line1Y, 9.5f * u * ts, WHITE, 0.55f * labA, Gfx.FONT_REGULAR, 0.32f, Gfx.ALIGN_CENTER);
            g.text(dayLine(), x, line2Y, 9f * u * ts, WHITE, 0.40f * labA, Gfx.FONT_REGULAR, 0.2f, Gfx.ALIGN_CENTER);
        }
        if (msgA > 0f) {
            g.text(msgTitle, x, line1Y, 9.5f * u * ts, pal.accent, 0.95f * msgA, Gfx.FONT_MEDIUM, 0.26f, Gfx.ALIGN_CENTER);
            if (msgLine.length() > 0)
                g.text(msgLine, x, line2Y + 1f * u, 11f * u * ts, WHITE, 0.85f * msgA, Gfx.FONT_LIGHT, 0.01f, Gfx.ALIGN_CENTER);
        }

        float l = x - barW * 0.5f, bh = 1.6f * u;
        g.rect(l, barY, l + barW, barY + bh, bh * 0.5f, WHITE, 0.10f);
        float fw = barW * clamp01(shownProg);
        if (fw > 0.5f) g.rect(l, barY, l + fw, barY + bh, bh * 0.5f, pal.accent, 0.85f);
        if (glintT < 1f) {
            float a = 1f - glintT;
            g.glow(l + fw, barY + bh * 0.5f, 12f * u, pal.accent, 0.9f * a);
            g.dot(l + fw, barY + bh * 0.5f, 1.5f * u, WHITE, a, true);
        }
        if (noticeShown.length() > 0 && noticeA > 0.01f) {
            float size = 8.4f * u * ts;
            float ny = Math.min(barY + bh + 24f * u * ts, H - 36f * u);
            float pulse = 0.8f + 0.2f * (float) Math.sin(time * 2.2);
            g.glow(x, ny - size * 0.35f, size * 4.5f, pal.accent, 0.07f * noticeA * pulse);
            g.text(noticeShown, x, ny, size, pal.accent, 0.92f * noticeA * pulse, Gfx.FONT_MEDIUM, 0.22f, Gfx.ALIGN_CENTER);
        }
    }

    /** Draws the count for a given number of rings left: rings left (break) or rings built (build). */
    private void drawNumber(Gfx g, int leftValue, float x, float y, float alpha) {
        if (alpha <= 0.01f) return;
        boolean done = leftValue == 0;
        String s = numStr(done ? total : (build ? total - leftValue : leftValue));
        g.text(s, x, y, numSize, done ? pal.accent : WHITE, alpha * (done ? 1f : 0.96f), Gfx.FONT_THIN, -0.02f, Gfx.ALIGN_CENTER);
    }

    private float messageAlpha() {
        if (msgT < 0f) return 0f;
        if (msgT < 0.7f) return msgT / 0.7f;
        if (msgT > msgDur - 0.9f) return clamp01((msgDur - msgT) / 0.9f);
        return 1f;
    }

    private String dayLine() {
        int key = ((left * 1000 + total) * 16 + Math.min(startsIn, 15)) * 2 + (build ? 1 : 0);
        if (key != dayKey) {
            dayKey = key;
            if (startsIn > 0) dayStr = startsIn == 1 ? "STARTS TOMORROW" : "STARTS IN " + startsIn + " DAYS";
            else if (left > 0) dayStr = "DAY " + (total - left + 1) + " OF " + total;
            else dayStr = allDoneTitle();
        }
        return dayStr;
    }

    private String numStr(int v) {
        for (int i = 0; i < 4; i++) if (numCacheV[i] == v) return numCacheS[i];
        int slot = (int) (time * 10) & 3;
        for (int i = 0; i < 4; i++) if (numCacheV[i] == -1) { slot = i; break; }
        numCacheV[slot] = v;
        numCacheS[slot] = String.valueOf(v);
        return numCacheS[slot];
    }

    // ------------------------------------ buckets ---------------------------------------

    private void clearBuckets() {
        for (int i = 0; i < NBK; i++) bkN[i] = 0;
    }

    private void addSeg(int b, float x0, float y0, float x1, float y1) {
        float[] a = bk[b];
        int n = bkN[b];
        if (a == null || n + 4 > a.length) {
            float[] na = new float[Math.max(256, (n + 4) * 2)];
            if (a != null) System.arraycopy(a, 0, na, 0, n);
            bk[b] = a = na;
        }
        a[n] = x0; a[n + 1] = y0; a[n + 2] = x1; a[n + 3] = y1;
        bkN[b] = n + 4;
    }

    private void addPt(int b, float x, float y) {
        float[] a = bk[b];
        int n = bkN[b];
        if (a == null || n + 2 > a.length) {
            float[] na = new float[Math.max(256, (n + 2) * 2)];
            if (a != null) System.arraycopy(a, 0, na, 0, n);
            bk[b] = a = na;
        }
        a[n] = x; a[n + 1] = y;
        bkN[b] = n + 2;
    }

    private void addP(int b, float x, float y) {
        float[] a = pk[b];
        int n = pkN[b];
        if (a == null || n + 2 > a.length) {
            float[] na = new float[Math.max(128, (n + 2) * 2)];
            if (a != null) System.arraycopy(a, 0, na, 0, n);
            pk[b] = a = na;
        }
        a[n] = x; a[n + 1] = y;
        pkN[b] = n + 2;
    }

    private void flushBuckets(Gfx g, float[] ca, float[] ga, float[] cw, float[] gw, float alphaMul, boolean glowPass) {
        if (glowPass) {
            for (int band = 0; band < BANDS; band++)
                for (int lv = 0; lv < LV; lv++) {
                    int i = band * LV + lv;
                    if (bkN[i] > 0) g.glowLines(bk[i], bkN[i], bandCol[band], Math.min(1f, ga[lv] * alphaMul * GLOW_K), gw[lv] * u);
                }
        }
        for (int band = 0; band < BANDS; band++)
            for (int lv = 0; lv < LV; lv++) {
                int i = band * LV + lv;
                if (bkN[i] > 0) g.lines(bk[i], bkN[i], bandCol[band], Math.min(1f, ca[lv] * alphaMul), cw[lv] * u, true);
            }
    }

    /** Puts the current pt arrays into the LV special buckets by depth (ptZ in -1..1 or -r..r). */
    private void specialLevels(int n) {
        for (int i = 0; i < LV; i++) bkN[SPEC + i] = 0;
        float zmax = 1e-4f;
        for (int s = 0; s < n; s++) zmax = Math.max(zmax, Math.abs(ptZ[s]));
        for (int s = 0; s < n - 1; s++) {
            float zm = (ptZ[s] + ptZ[s + 1]) * 0.5f / zmax;
            int lv = clampi((int) ((zm + 1f) * 0.5f * LV), 0, LV - 1);
            addSeg(SPEC + lv, ptX[s], ptY[s], ptX[s + 1], ptY[s + 1]);
        }
    }

    private void drawSpecLevels(Gfx g, int color, float aFront, float aBack, float width) {
        for (int lv = 0; lv < LV; lv++) {
            int i = SPEC + lv;
            if (bkN[i] == 0) continue;
            float t = lv / (float) (LV - 1);
            g.lines(bk[i], bkN[i], color, Math.min(1f, aBack + (aFront - aBack) * t), width * (0.8f + 0.4f * t), true);
        }
    }

    private void drawSpecGlow(Gfx g, int color, float aFront, float aBack, float width) {
        for (int lv = 0; lv < LV; lv++) {
            int i = SPEC + lv;
            if (bkN[i] == 0) continue;
            float t = lv / (float) (LV - 1);
            g.glowLines(bk[i], bkN[i], color, Math.min(1f, (aBack + (aFront - aBack) * t) * GLOW_K), width * (0.8f + 0.4f * t));
        }
    }

    private float[] cosTab(int n) {
        if (cosT[n] == null) buildTab(n);
        return cosT[n];
    }

    private float[] sinTab(int n) {
        if (sinT[n] == null) buildTab(n);
        return sinT[n];
    }

    private void buildTab(int n) {
        float[] c = new float[n + 1], s = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2.0 * i / n;
            c[i] = (float) Math.cos(a);
            s[i] = (float) Math.sin(a);
        }
        cosT[n] = c;
        sinT[n] = s;
    }

    // ------------------------------------- math -----------------------------------------

    private float rnd() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return ((seed >>> 40) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float frac(float v) { return v - (float) Math.floor(v); }

    static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    static float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    static int clampi(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    static float easeInOut(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    static float easeOut(float t) { t = clamp01(t); float i = 1f - t; return 1f - i * i * i; }

    static float easeOutCubic(float t) { return easeOut(t); }

    static float easeIn(float t) { t = clamp01(t); return t * t * t; }

    static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1f, x = t - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    static float elastic(float t) {
        t = clamp01(t);
        if (t == 0f || t == 1f) return t;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (TAU / 3f)) + 1);
    }
}
