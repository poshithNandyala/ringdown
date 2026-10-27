package com.ringdown.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The remote control file (remote/config.json in the GitHub repo), read into checked, typed
 * fields. Every value is optional; anything missing, of the wrong type or unsafe falls back to
 * "off", so a broken or empty file can never change how the app behaves.
 * See REMOTE.md for the format.
 */
public final class RemoteConfig {
    public static final int MODE_ANY = -1;
    public static final int MAX_MESSAGES = 10, MAX_PAGES = 8, MAX_FLAGS = 50;

    public static final class Update {
        /** Newest version code available. 0 = no update announced. */
        public int versionCode;
        /** Versions below this get a banner that can't be dismissed. 0 = never required. */
        public int requiredBelow;
        public String versionName = "", title = "", text = "", button = "", wallpaperText = "";
        public boolean inApp = true, wallpaper = true, notify = true;
        public String playUrl = "", apkUrl = "";
    }

    public static final class Message {
        public String id = "", title = "", text = "", button = "", url = "", wallpaperText = "";
        public boolean inApp = true, notify, wallpaper, testersOnly;
        /** Local calendar days (epoch day), inclusive. */
        public long from = Long.MIN_VALUE, until = Long.MAX_VALUE;
        /** RingScene.MODE_BREAK, MODE_BUILD or MODE_ANY. */
        public int mode = MODE_ANY;
        /** App version limits, 0 = no limit. */
        public int minVersion, maxVersion;
    }

    public static final class Page {
        public String id = "", title = "", subtitle = "", url = "";
        public boolean inBrowser, testersOnly;
    }

    /** How often phones check this file, in hours (1 to 72). */
    public int refreshHours = 6;
    /** If set (https), phones read the config from this address from now on. */
    public String moveTo = "";
    public final Update update = new Update();
    public final List<Message> messages = new ArrayList<>();
    public final List<Page> pages = new ArrayList<>();
    /** Free-form switches for future app versions: name -> true/false, number or text. */
    public final Map<String, Object> flags = new LinkedHashMap<>();

    /** Returns null if the text isn't a valid config. Never throws. */
    public static RemoteConfig parse(String json) {
        try {
            return parseOrThrow(json);
        } catch (Json.Error e) {
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static RemoteConfig parseOrThrow(String json) throws Json.Error {
        Object root = Json.parse(json);
        if (!(root instanceof Map)) throw new Json.Error("the file must be one { ... } object");
        Map<?, ?> m = (Map<?, ?>) root;
        RemoteConfig c = new RemoteConfig();
        c.refreshHours = clamp(integer(m, "refresh_hours", 6), 1, 72);
        c.moveTo = url(m, "move_to", false);

        Map<?, ?> u = map(m, "update");
        if (u != null) {
            Update up = c.update;
            up.versionCode = Math.max(0, integer(u, "version_code", 0));
            up.requiredBelow = Math.max(0, integer(u, "required_below", 0));
            up.versionName = text(u, "version_name", 20);
            up.title = text(u, "title", 60);
            up.text = text(u, "text", 240);
            up.button = text(u, "button", 20);
            up.wallpaperText = text(u, "wallpaper_text", 42);
            up.inApp = bool(u, "in_app", true);
            up.wallpaper = bool(u, "wallpaper", true);
            up.notify = bool(u, "notify", true);
            up.playUrl = url(u, "play_url", true);
            up.apkUrl = url(u, "apk_url", false);
        }

        Set<String> ids = new HashSet<>();
        for (Object o : list(m, "messages")) {
            if (!(o instanceof Map) || c.messages.size() >= MAX_MESSAGES) continue;
            Map<?, ?> x = (Map<?, ?>) o;
            Message msg = new Message();
            msg.id = text(x, "id", 64);
            msg.title = text(x, "title", 60);
            msg.text = text(x, "text", 240);
            msg.button = text(x, "button", 20);
            msg.url = url(x, "url", true);
            msg.wallpaperText = text(x, "wallpaper_text", 42);
            msg.inApp = bool(x, "in_app", true);
            msg.notify = bool(x, "notify", false);
            msg.wallpaper = bool(x, "wallpaper", false);
            msg.testersOnly = bool(x, "testers_only", false);
            msg.from = day(x, "from", Long.MIN_VALUE);
            msg.until = day(x, "until", Long.MAX_VALUE);
            msg.mode = mode(x, "mode");
            msg.minVersion = Math.max(0, integer(x, "min_version", 0));
            msg.maxVersion = Math.max(0, integer(x, "max_version", 0));
            if (msg.id.length() == 0 || !ids.add(msg.id)) continue;
            if (msg.title.length() == 0 && msg.text.length() == 0) continue;
            c.messages.add(msg);
        }

        ids.clear();
        for (Object o : list(m, "pages")) {
            if (!(o instanceof Map) || c.pages.size() >= MAX_PAGES) continue;
            Map<?, ?> x = (Map<?, ?>) o;
            Page p = new Page();
            p.id = text(x, "id", 64);
            p.title = text(x, "title", 40);
            p.subtitle = text(x, "subtitle", 80);
            p.url = url(x, "url", false);
            p.inBrowser = "browser".equals(text(x, "open_in", 20));
            p.testersOnly = bool(x, "testers_only", false);
            if (p.id.length() == 0 || p.title.length() == 0 || p.url.length() == 0 || !ids.add(p.id)) continue;
            c.pages.add(p);
        }

        Map<?, ?> f = map(m, "flags");
        if (f != null) {
            for (Map.Entry<?, ?> e : f.entrySet()) {
                if (c.flags.size() >= MAX_FLAGS) break;
                Object k = e.getKey(), v = e.getValue();
                if (!(k instanceof String) || ((String) k).length() == 0 || ((String) k).length() > 40) continue;
                if (v instanceof Boolean || v instanceof Double) c.flags.put((String) k, v);
                else if (v instanceof String) c.flags.put((String) k, clean((String) v, 200));
            }
        }
        return c;
    }

    public boolean flag(String key, boolean def) {
        Object v = flags.get(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    public double number(String key, double def) {
        Object v = flags.get(key);
        return v instanceof Double ? (Double) v : def;
    }

    public String flagText(String key, String def) {
        Object v = flags.get(key);
        return v instanceof String ? (String) v : def;
    }

    // ------------------------------------------------------------------ reading values safely

    private static Map<?, ?> map(Map<?, ?> m, String k) {
        Object v = m.get(k);
        return v instanceof Map ? (Map<?, ?>) v : null;
    }

    private static List<?> list(Map<?, ?> m, String k) {
        Object v = m.get(k);
        return v instanceof List ? (List<?>) v : new ArrayList<Object>();
    }

    private static boolean bool(Map<?, ?> m, String k, boolean def) {
        Object v = m.get(k);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    private static int integer(Map<?, ?> m, String k, int def) {
        Object v = m.get(k);
        if (!(v instanceof Double)) return def;
        double d = (Double) v;
        if (Double.isNaN(d) || Double.isInfinite(d)) return def;
        if (d > 2_000_000_000d) return 2_000_000_000;
        if (d < -2_000_000_000d) return -2_000_000_000;
        return (int) Math.floor(d);
    }

    private static String text(Map<?, ?> m, String k, int max) {
        Object v = m.get(k);
        return v instanceof String ? clean((String) v, max) : "";
    }

    /** Removes control characters, squeezes spaces, trims, and cuts to max characters. */
    static String clean(String s, int max) {
        StringBuilder b = new StringBuilder(Math.min(s.length(), max + 8));
        boolean space = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ctl = c < 0x20 || (c >= 0x7F && c <= 0x9F) || c == ' ' || c == ' ';
            if (ctl || Character.isWhitespace(c)) {
                space = b.length() > 0;
                continue;
            }
            if (space) { b.append(' '); space = false; }
            b.append(c);
        }
        if (b.length() <= max) return b.toString();
        int cut = max - 1;
        if (cut > 0 && Character.isHighSurrogate(b.charAt(cut - 1))) cut--;
        return b.substring(0, cut).trim() + "…";
    }

    /** https:// links only (and market:// where allowed). Anything else becomes "". */
    private static String url(Map<?, ?> m, String k, boolean allowMarket) {
        Object v = m.get(k);
        if (!(v instanceof String)) return "";
        String s = ((String) v).trim();
        if (s.length() > 2000) return "";
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 0x20 || c >= 0x7F) return "";
        }
        String low = s.toLowerCase(java.util.Locale.ROOT);
        if (low.startsWith("https://") && s.length() > "https://".length() + 3) return s;
        if (allowMarket && low.startsWith("market://") && s.length() > "market://".length() + 3) return s;
        return "";
    }

    private static int mode(Map<?, ?> m, String k) {
        String s = text(m, k, 10).toLowerCase(java.util.Locale.ROOT);
        if (s.equals("break")) return RingScene.MODE_BREAK;
        if (s.equals("build")) return RingScene.MODE_BUILD;
        return MODE_ANY;
    }

    /** "YYYY-MM-DD" to a day number (days since 1970-01-01); def if missing or not a real date. */
    private static long day(Map<?, ?> m, String k, long def) {
        String s = text(m, k, 10);
        if (s.length() != 10 || s.charAt(4) != '-' || s.charAt(7) != '-') return def;
        try {
            int y = Integer.parseInt(s.substring(0, 4));
            int mo = Integer.parseInt(s.substring(5, 7));
            int d = Integer.parseInt(s.substring(8, 10));
            if (y < 2000 || mo < 1 || mo > 12 || d < 1 || d > daysInMonth(y, mo)) return def;
            return epochDay(y, mo, d);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static int daysInMonth(int y, int m) {
        if (m == 2) return (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) ? 29 : 28;
        return (m == 4 || m == 6 || m == 9 || m == 11) ? 30 : 31;
    }

    /** Days since 1970-01-01 for a calendar date (proleptic Gregorian). */
    public static long epochDay(int y, int m, int d) {
        y -= m <= 2 ? 1 : 0;
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
