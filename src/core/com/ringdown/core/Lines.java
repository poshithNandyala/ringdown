package com.ringdown.core;

/** Short lines shown after each completed day. Milestones get their own. */
public final class Lines {
    private Lines() {}

    private static final String[] BREAK = {
        "One less. Keep going.",
        "You showed up. That counts.",
        "The old loop is fading.",
        "Another ring cleared.",
        "Quietly unstoppable.",
        "That pathway is getting quieter.",
        "Future you says thanks.",
        "Cravings pass. You stayed.",
        "Today counted.",
        "The load is getting lighter.",
        "Proof you can do hard things.",
        "Still here. Still winning.",
        "One day closer.",
        "Nothing can undo today.",
        "You kept your word.",
        "Steady wins.",
    };

    private static final String[] BUILD = {
        "Another ring built.",
        "The new loop is wiring in.",
        "Brick by brick.",
        "You showed up. That counts.",
        "Stronger every day.",
        "Consistency beats intensity.",
        "Future you says thanks.",
        "Momentum is building.",
        "Today counted.",
        "It's becoming who you are.",
        "Discipline looks good on you.",
        "Keep the streak alive.",
        "That's how it's done.",
        "Repetition is rewiring you.",
        "Small steps, real distance.",
        "One day closer.",
    };

    /** done = days completed after this one, left = days remaining. */
    public static String pick(int done, int total, int left, boolean build) {
        if (left == 0) return build ? "Every ring built. It's part of you now." : "Every ring cleared. You did it.";
        if (left == 1) return build ? "One ring to go. Finish it." : "One ring left. Finish it.";
        if (done == 1) return build ? "First ring built. It's begun." : "First ring down. It's begun.";
        if (total >= 8) {
            if (crossed(done, total, 2)) return build ? "Halfway built. It's becoming you." : "Halfway. Look how far you've come.";
            if (crossed(done, total, 1)) return build ? "A quarter built. Keep stacking." : "A quarter done. Strong start.";
            if (crossed(done, total, 3)) return build ? "Three quarters. Almost automatic." : "Three quarters. Final stretch.";
        }
        if (done % 10 == 0 && total > 20) return done + " days strong.";
        String[] pool = build ? BUILD : BREAK;
        return pool[(int) ((done * 7L + total) % pool.length)];
    }

    /** True when this day crossed the q-th quarter mark (q = 1, 2, 3). */
    public static boolean crossed(int done, int total, int q) {
        return (done * 4) / total >= q && ((done - 1) * 4) / total < q;
    }

    public static boolean isMilestone(int done, int total, int left) {
        if (left <= 1 || done == 1) return true;
        return total >= 8 && (crossed(done, total, 1) || crossed(done, total, 2) || crossed(done, total, 3));
    }
}
