package com.ringdown.core;

/** Fixed-capacity particle pool (no allocation while running). */
final class Particles {
    static final byte SPARK3D = 0;  // world space, projected by the scene
    static final byte SPARK2D = 1;  // screen space with gravity
    static final byte HOMING = 2;   // screen space, flies to (tx, ty)
    static final byte STREAK = 3;   // screen space warp line, accelerates outward
    static final byte EMBER = 4;    // screen space, drifts slowly upward
    static final byte CONVERGE = 5; // screen space: flies from start (vx, vy) to (tx, ty) on a curve over its life

    final int cap;
    final float[] x, y, z, vx, vy, vz, life, max, size, tx, ty, ph;
    final int[] color;
    final byte[] kind;
    int n;
    int arrived;       // homing particles that reached their target (scene resets)
    float lastArrX, lastArrY;

    Particles(int cap) {
        this.cap = cap;
        x = new float[cap]; y = new float[cap]; z = new float[cap];
        vx = new float[cap]; vy = new float[cap]; vz = new float[cap];
        life = new float[cap]; max = new float[cap]; size = new float[cap];
        tx = new float[cap]; ty = new float[cap]; ph = new float[cap];
        color = new int[cap]; kind = new byte[cap];
    }

    int add(byte k, float px, float py, float pz, float pvx, float pvy, float pvz,
            float lifeSec, float sz, int col, float phase) {
        if (n >= cap) return -1;
        int i = n++;
        kind[i] = k; x[i] = px; y[i] = py; z[i] = pz;
        vx[i] = pvx; vy[i] = pvy; vz[i] = pvz;
        life[i] = lifeSec; max[i] = lifeSec; size[i] = sz; color[i] = col; ph[i] = phase;
        tx[i] = px; ty[i] = py;
        return i;
    }

    void clear() { n = 0; arrived = 0; }

    void update(float dt, float u) {
        for (int i = 0; i < n; ) {
            life[i] -= dt;
            if (life[i] <= 0f) { remove(i); continue; }
            if (kind[i] == CONVERGE) {
                float p = 1f - life[i] / max[i];
                float e = p < 0.5f ? 4f * p * p * p : 1f - (float) Math.pow(-2f * p + 2f, 3) / 2f;
                float sx = vx[i], sy = vy[i];
                float dx = tx[i] - sx, dy = ty[i] - sy;
                float len = (float) Math.sqrt(dx * dx + dy * dy) + 1e-3f;
                float bend = z[i] * (float) Math.sin(Math.PI * e);
                x[i] = sx + dx * e - dy / len * bend;
                y[i] = sy + dy * e + dx / len * bend;
                i++;
                continue;
            }
            switch (kind[i]) {
                case SPARK3D: {
                    float dr = (float) Math.exp(-1.7f * dt);
                    vx[i] *= dr; vy[i] *= dr; vz[i] *= dr;
                    vy[i] -= 0.30f * dt;
                    break;
                }
                case SPARK2D: {
                    float dr = (float) Math.exp(-2.1f * dt);
                    vx[i] *= dr; vy[i] *= dr;
                    vy[i] += 55f * u * dt;
                    break;
                }
                case HOMING: {
                    float age = max[i] - life[i];
                    float dx = tx[i] - x[i], dy = ty[i] - y[i];
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d < 5f * u || (age > 0.5f && d < 12f * u && dot(dx, dy, vx[i], vy[i]) < 0)) {
                        arrived++; lastArrX = tx[i]; lastArrY = ty[i];
                        remove(i);
                        continue;
                    }
                    float k = clamp01((age - 0.18f) / 0.55f);
                    float spd = (260f + 260f * k) * u;
                    float want = Math.min(1f, dt * (3f + 9f * k)) * k;
                    vx[i] += (dx / d * spd - vx[i]) * want;
                    vy[i] += (dy / d * spd - vy[i]) * want;
                    if (k < 1f) { float dr = (float) Math.exp(-2.5f * dt); vx[i] *= dr; vy[i] *= dr; }
                    // never expire before arriving
                    if (life[i] < 0.3f) life[i] = 0.3f;
                    break;
                }
                case STREAK: {
                    float g = 1f + 2.6f * dt;
                    vx[i] *= g; vy[i] *= g;
                    break;
                }
                case EMBER: {
                    float dr = (float) Math.exp(-0.8f * dt);
                    vx[i] *= dr; vy[i] *= dr;
                    vy[i] -= 9f * u * dt;
                    break;
                }
            }
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt;
            i++;
        }
    }

    private void remove(int i) {
        int j = --n;
        if (i == j) return;
        kind[i] = kind[j]; x[i] = x[j]; y[i] = y[j]; z[i] = z[j];
        vx[i] = vx[j]; vy[i] = vy[j]; vz[i] = vz[j];
        life[i] = life[j]; max[i] = max[j]; size[i] = size[j]; color[i] = color[j];
        tx[i] = tx[j]; ty[i] = ty[j]; ph[i] = ph[j];
    }

    private static float dot(float ax, float ay, float bx, float by) { return ax * bx + ay * by; }

    static float clamp01(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
}
