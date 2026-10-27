package com.ringdown.core;

import java.util.TimeZone;

/** Day math. One ring disappears at every local midnight after the start day. */
public final class Countdown {
    private static final long DAY_MS = 86_400_000L;

    private Countdown() {}

    /** Days since 1970-01-01 in the device's local time zone. */
    public static long today() {
        long now = System.currentTimeMillis();
        return epochDay(now, TimeZone.getDefault().getOffset(now));
    }

    public static long epochDay(long utcMillis, int offsetMillis) {
        return Math.floorDiv(utcMillis + offsetMillis, DAY_MS);
    }

    /** Days fully completed (= rings already gone), 0..total. */
    public static int done(int total, long startDay, long today) {
        long d = today - startDay;
        if (d < 0) return 0;
        if (d > total) return total;
        return (int) d;
    }

    public static int left(int total, long startDay, long today) {
        return total - done(total, startDay, today);
    }

    /** Days until the countdown begins (start date in the future), else 0. */
    public static int startsIn(long startDay, long today) {
        long d = startDay - today;
        return d > 0 ? (int) Math.min(d, 9999) : 0;
    }

    /** Epoch day on which the last ring is gone. */
    public static long clearDay(int total, long startDay) {
        return startDay + total;
    }
}
