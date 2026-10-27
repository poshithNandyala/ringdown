package com.poshith.ringdown;

import com.ringdown.core.RingScene;

/** Ready-made habits to break or build, with a sensible number of days for each. */
final class Habits {
    static final class Habit {
        final String id, emoji, name;
        final int mode, days;

        Habit(String id, int mode, String emoji, String name, int days) {
            this.id = id; this.mode = mode; this.emoji = emoji; this.name = name; this.days = days;
        }
    }

    static final String CUSTOM = "custom";
    private static final int B = RingScene.MODE_BREAK, U = RingScene.MODE_BUILD;

    static final Habit[] ALL = {
        // ---- break a habit
        new Habit("no_sugar", B, "🍬", "No sugar", 30),
        new Habit("no_smoking", B, "🚭", "No smoking", 90),
        new Habit("no_vaping", B, "💨", "No vaping", 90),
        new Habit("no_alcohol", B, "🍺", "No alcohol", 90),
        new Habit("no_drugs", B, "💊", "No drugs", 90),
        new Habit("no_social", B, "📱", "No social media", 30),
        new Habit("no_porn", B, "🚫", "No porn", 90),
        new Habit("no_junk", B, "🍔", "No junk food", 30),
        new Habit("no_soda", B, "🥤", "No soda", 30),
        new Habit("no_caffeine", B, "☕", "No caffeine", 30),
        new Habit("no_gaming", B, "🎮", "No gaming", 30),
        new Habit("no_gambling", B, "🎰", "No gambling", 90),
        new Habit("no_doomscroll", B, "🌀", "No doomscrolling", 30),
        new Habit("no_phone_bed", B, "🌙", "No phone in bed", 21),
        new Habit("no_nail_biting", B, "💅", "No nail biting", 30),
        new Habit("no_impulse", B, "🛒", "No impulse buying", 30),
        // ---- build a habit (66 days: median time to automaticity, Lally et al. 2010)
        new Habit("gym", U, "🏋️", "Gym", 66),
        new Habit("run", U, "🏃", "Run", 66),
        new Habit("steps", U, "🚶", "10k steps", 66),
        new Habit("read", U, "📚", "Read", 66),
        new Habit("meditate", U, "🧘", "Meditate", 66),
        new Habit("dsa", U, "💻", "DSA practice", 100),
        new Habit("study", U, "📖", "Study", 66),
        new Habit("journal", U, "✍️", "Journal", 30),
        new Habit("water", U, "💧", "Drink water", 30),
        new Habit("wake_early", U, "⏰", "Wake up early", 66),
        new Habit("sleep_early", U, "😴", "Sleep by 11", 66),
        new Habit("yoga", U, "🤸", "Yoga", 66),
        new Habit("language", U, "🗣️", "Learn a language", 90),
        new Habit("music", U, "🎸", "Practice music", 66),
        new Habit("eat_healthy", U, "🥗", "Eat healthy", 66),
        new Habit("gratitude", U, "🙏", "Gratitude", 30),
    };

    static Habit find(String id) {
        if (id == null) return null;
        for (Habit h : ALL) if (h.id.equals(id)) return h;
        return null;
    }

    private Habits() {}
}
