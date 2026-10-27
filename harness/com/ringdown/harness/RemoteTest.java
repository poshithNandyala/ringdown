package com.ringdown.harness;

import com.ringdown.core.Json;
import com.ringdown.core.RemoteConfig;
import com.ringdown.core.RemoteRules;
import com.ringdown.core.RingScene;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests for the remote control: the JSON reader, the config checks and the rules for what shows
 * where. Also checks that remote/config.json and every JSON example in REMOTE.md are valid.
 *   java RemoteTest remote/config.json REMOTE.md
 */
public final class RemoteTest {
    private static int passed, failed;

    public static void main(String[] args) throws Exception {
        String configPath = args.length > 0 ? args[0] : "remote/config.json";
        String docPath = args.length > 1 ? args[1] : "REMOTE.md";
        json();
        sanitizing();
        dates();
        defaultConfig(read(configPath));
        updates();
        messages();
        pages();
        fuzz(read(configPath));
        docs(read(docPath));
        System.out.printf("%d passed, %d failed%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ JSON reader

    static void json() throws Exception {
        Object o = Json.parse("\uFEFF {\"a\": [1, 2.5, -3e2, true, false, null, \"x\\u00e9\\n\\\"\"], \"b\": {}}");
        check(o instanceof java.util.Map, "object parses");
        List<?> a = (List<?>) ((java.util.Map<?, ?>) o).get("a");
        check(a.size() == 7 && ((Double) a.get(2)) == -300d && "xé\n\"".equals(a.get(6)), "values and escapes");
        String[] bad = {"", "{", "{\"a\":1,}", "[1,]", "{a:1}", "{'a':1}", "[01]", "[1.]", "[.5]", "[-]", "[1e]",
                "\"line\nbreak\"", "[\"\\x\"]", "[\"\\u12\"]", "tru", "{\"a\":1}}", "[1 2]", "{\"a\" 1}", "nul",
                "// comment\n{}", "{\"a\":1} x", "[\"unclosed]"};
        for (String b : bad) {
            boolean threw = false;
            try {
                Json.parse(b);
            } catch (Json.Error e) {
                threw = true;
            }
            check(threw, "rejects " + b.replace("\n", "\\n"));
        }
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 40; i++) deep.append('[');
        for (int i = 0; i < 40; i++) deep.append(']');
        boolean threw = false;
        try {
            Json.parse(deep.toString());
        } catch (Json.Error e) {
            threw = true;
        }
        check(threw, "rejects very deep nesting");
        check(RemoteConfig.parse("[1,2]") == null, "top level must be an object");
        check(RemoteConfig.parse("{}") != null, "empty object is a valid (empty) config");
        check(RemoteConfig.parse(null) == null, "null text");
    }

    // ------------------------------------------------------------------ value checks

    static void sanitizing() {
        RemoteConfig c = RemoteConfig.parse("{\"messages\":[{\"id\":\"a\",\"title\":\"  Hi\\n\\tthere\\u0007  \","
                + "\"text\":\"" + "x".repeat(400) + "\",\"url\":\"javascript:alert(1)\"},"
                + "{\"id\":\"b\",\"title\":\"ok\",\"url\":\"http://plain.example\"},"
                + "{\"id\":\"c\",\"title\":\"ok\",\"url\":\"https://has space.example\"},"
                + "{\"id\":\"d\",\"title\":\"ok\",\"url\":\"market://details?id=x.y\"},"
                + "{\"id\":\"d\",\"title\":\"duplicate id\"},"
                + "{\"title\":\"no id\"},"
                + "{\"id\":\"e\"}]}");
        check(c != null, "config with odd values still parses");
        check(c.messages.size() == 4, "skips duplicate ids, missing ids and empty messages (" + c.messages.size() + ")");
        check("Hi there".equals(c.messages.get(0).title), "control characters and extra spaces removed");
        check(c.messages.get(0).text.length() == 240 && c.messages.get(0).text.endsWith("…"), "long text cut to 240");
        check(c.messages.get(0).url.isEmpty(), "javascript: link refused");
        check(c.messages.get(1).url.isEmpty(), "http link refused (https only)");
        check(c.messages.get(2).url.isEmpty(), "link with a space refused");
        check(c.messages.get(3).url.startsWith("market://"), "market:// allowed for messages");
        RemoteConfig p = RemoteConfig.parse("{\"pages\":[{\"id\":\"p\",\"title\":\"T\",\"url\":\"market://x.y.z\"}],"
                + "\"refresh_hours\": 0.2, \"move_to\": \"http://evil.example/c.json\","
                + "\"update\": {\"version_code\": \"5\", \"in_app\": \"yes\"}}");
        check(p.pages.isEmpty(), "pages must be https");
        check(p.refreshHours == 1, "refresh_hours clamped to at least 1");
        check(p.moveTo.isEmpty(), "move_to must be https");
        check(p.update.versionCode == 0, "version_code as text is ignored");
        check(p.update.inApp, "wrong type keeps the default");
        RemoteConfig big = RemoteConfig.parse("{\"refresh_hours\": 1e300, \"update\": {\"version_code\": -5}}");
        check(big.refreshHours == 72 && big.update.versionCode == 0, "huge and negative numbers clamped");
        RemoteConfig f = RemoteConfig.parse("{\"flags\": {\"pro\": true, \"price\": 49, \"note\": \"hi\", \"bad\": [1], \"\": true}}");
        check(f.flag("pro", false) && f.number("price", 0) == 49 && "hi".equals(f.flagText("note", "")), "flags read");
        check(!f.flags.containsKey("bad") && !f.flags.containsKey(""), "only simple flag values kept");
    }

    static void dates() {
        Random r = new Random(7);
        for (int i = 0; i < 2000; i++) {
            LocalDate d = LocalDate.ofEpochDay(10_000 + r.nextInt(40_000));
            if (RemoteConfig.epochDay(d.getYear(), d.getMonthValue(), d.getDayOfMonth()) != d.toEpochDay()) {
                check(false, "epochDay matches java.time for " + d);
                return;
            }
        }
        check(true, "epochDay matches java.time on 2000 random dates");
        RemoteConfig c = RemoteConfig.parse("{\"messages\":[{\"id\":\"a\",\"title\":\"t\",\"from\":\"2026-02-30\",\"until\":\"2026-13-01\"}]}");
        check(c.messages.get(0).from == Long.MIN_VALUE && c.messages.get(0).until == Long.MAX_VALUE, "impossible dates ignored");
    }

    // ------------------------------------------------------------------ rules

    static RemoteRules.Device device(int code, String day, boolean play) {
        RemoteRules.Device d = new RemoteRules.Device();
        d.versionCode = code;
        d.today = LocalDate.parse(day).toEpochDay();
        d.mode = RingScene.MODE_BREAK;
        d.fromPlay = play;
        d.pkg = "com.poshith.ringdown";
        return d;
    }

    static void defaultConfig(String text) {
        RemoteConfig c = RemoteConfig.parse(text);
        check(c != null, "remote/config.json is valid");
        if (c == null) return;
        RemoteRules.Device d = device(4, "2026-10-28", true);
        Set<String> none = new HashSet<>();
        check(RemoteRules.banner(c, d, none) == null, "default config: no banner on 1.3");
        check(RemoteRules.wallpaperNotice(c, d, none) == null, "default config: nothing on the wallpaper");
        check(RemoteRules.notifications(c, d, none).isEmpty(), "default config: no notifications");
        check(RemoteRules.pages(c, d).isEmpty(), "default config: no extra pages");
    }

    static void updates() {
        String cfg = "{\"update\":{\"version_code\":5,\"version_name\":\"1.4\","
                + "\"play_url\":\"https://play.google.com/store/apps/details?id=com.poshith.ringdown\","
                + "\"apk_url\":\"https://github.com/poshithNandyala/ringdown/raw/main/apk/Ringdown.apk\"}}";
        RemoteConfig c = RemoteConfig.parse(cfg);
        Set<String> none = new HashSet<>();
        RemoteRules.Device old = device(4, "2026-11-01", true);
        RemoteRules.Banner b = RemoteRules.banner(c, old, none);
        check(b != null && b.update && b.dismissable, "update banner on the old version");
        check(b != null && b.title.equals(RemoteRules.DEFAULT_UPDATE_TITLE) && b.button.equals("Update"), "default texts");
        check(b != null && b.url.startsWith("https://play.google.com/"), "Play install goes to Google Play");
        check(RemoteRules.banner(c, device(5, "2026-11-01", true), none) == null, "no banner once updated");
        check(RemoteRules.banner(c, device(0, "2026-11-01", true), none) == null, "unknown own version: no banner");
        RemoteRules.Device side = device(4, "2026-11-01", false);
        check(RemoteRules.banner(c, side, none).url.contains("github.com"), "GitHub install goes to the APK");
        check(RemoteRules.wallpaperNotice(c, old, none).equals(RemoteRules.DEFAULT_UPDATE_WALLPAPER), "wallpaper line");
        Set<String> dismissed = new HashSet<>();
        dismissed.add(RemoteRules.updateKey(c));
        check(RemoteRules.banner(c, old, dismissed) == null, "\"Not now\" hides the banner");
        check(RemoteRules.wallpaperNotice(c, old, dismissed) == null, "...and the wallpaper line");
        List<RemoteRules.Note> notes = RemoteRules.notifications(c, old, none);
        check(notes.size() == 1 && notes.get(0).key.equals("update:5"), "one update notification");
        Set<String> notified = new HashSet<>();
        notified.add("update:5");
        check(RemoteRules.notifications(c, old, notified).isEmpty(), "notification only once per version");

        RemoteConfig req = RemoteConfig.parse("{\"update\":{\"version_code\":6,\"required_below\":5}}");
        RemoteRules.Banner rb = RemoteRules.banner(req, old, dismissed);
        check(rb != null && !rb.dismissable, "required update can't be dismissed");
        Set<String> d6 = new HashSet<>();
        d6.add("update:6");
        check(RemoteRules.wallpaperNotice(req, old, d6) != null, "required update stays on the wallpaper");
        check(RemoteRules.banner(req, device(5, "2026-11-01", true), d6) == null, "version 5 isn't forced");

        RemoteConfig quiet = RemoteConfig.parse("{\"update\":{\"version_code\":5,\"in_app\":false,\"wallpaper\":false,\"notify\":false}}");
        check(RemoteRules.banner(quiet, old, none) == null && RemoteRules.wallpaperNotice(quiet, old, none) == null
                && RemoteRules.notifications(quiet, old, none).isEmpty(), "each update channel can be turned off");
        RemoteConfig noPlayUrl = RemoteConfig.parse("{\"update\":{\"version_code\":5}}");
        check(RemoteRules.updateUrl(noPlayUrl, side).equals("https://play.google.com/store/apps/details?id=com.poshith.ringdown"),
                "missing links fall back to the Play listing");
    }

    static void messages() {
        String cfg = "{\"messages\":["
                + "{\"id\":\"diwali\",\"title\":\"Happy Diwali\",\"text\":\"New colors\",\"url\":\"https://example.com/d\","
                + "\"notify\":true,\"wallpaper\":true,\"from\":\"2026-11-01\",\"until\":\"2026-11-05\"},"
                + "{\"id\":\"builders\",\"title\":\"Builders only\",\"mode\":\"build\",\"notify\":true},"
                + "{\"id\":\"test\",\"title\":\"Testers only\",\"testers_only\":true,\"notify\":true,\"wallpaper\":true},"
                + "{\"id\":\"new\",\"title\":\"For 1.4+\",\"min_version\":5},"
                + "{\"id\":\"old\",\"title\":\"For up to 1.3\",\"max_version\":4,\"in_app\":false,\"notify\":true}"
                + "]}";
        RemoteConfig c = RemoteConfig.parse(cfg);
        Set<String> none = new HashSet<>();
        RemoteRules.Device before = device(4, "2026-10-31", true);
        RemoteRules.Device during = device(4, "2026-11-05", true);
        RemoteRules.Device after = device(4, "2026-11-06", true);
        check(RemoteRules.banner(c, before, none) == null, "message waits for its start date");
        RemoteRules.Banner b = RemoteRules.banner(c, during, none);
        check(b != null && b.title.equals("Happy Diwali") && b.button.equals("Open") && !b.update, "message banner on the last day");
        check(RemoteRules.banner(c, after, none) == null, "message ends after its last day");
        check("Happy Diwali".equals(RemoteRules.wallpaperNotice(c, during, none)), "wallpaper uses the title when no wallpaper_text");
        List<RemoteRules.Note> notes = RemoteRules.notifications(c, during, none);
        check(notes.size() == 2 && notes.get(0).key.equals("msg:diwali") && notes.get(1).key.equals("msg:old"),
                "notifications: dated one + version-limited one (" + notes.size() + ")");
        RemoteRules.Device builder = device(4, "2026-10-28", true);
        builder.mode = RingScene.MODE_BUILD;
        check(RemoteRules.banner(c, builder, none).title.equals("Builders only"), "mode targeting");
        check(RemoteRules.notifications(c, before, none).size() == 1, "break-mode phone doesn't get the build message");
        RemoteRules.Device tester = device(4, "2026-10-28", true);
        tester.tester = true;
        check("Testers only".equals(RemoteRules.wallpaperNotice(c, tester, none)), "tester phone sees testers_only");
        check(RemoteRules.wallpaperNotice(c, before, none) == null, "normal phone doesn't");
        check(RemoteRules.banner(c, device(5, "2026-10-28", true), none).title.equals("For 1.4+"), "min_version");
        Set<String> dismissed = new HashSet<>();
        dismissed.add("msg:diwali");
        check(RemoteRules.banner(c, during, dismissed) == null, "dismissed message stays hidden");
        RemoteConfig many = RemoteConfig.parse("{\"messages\":[" + manyMessages(8) + "]}");
        check(RemoteRules.notifications(many, before, none).size() == RemoteRules.MAX_NOTES_PER_CHECK, "at most 3 notifications per check");
        RemoteConfig tooMany = RemoteConfig.parse("{\"messages\":[" + manyMessages(25) + "]}");
        check(tooMany.messages.size() == RemoteConfig.MAX_MESSAGES, "message list capped at 10");
    }

    static String manyMessages(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) b.append(',');
            b.append("{\"id\":\"m").append(i).append("\",\"title\":\"t\",\"notify\":true}");
        }
        return b.toString();
    }

    static void pages() {
        RemoteConfig c = RemoteConfig.parse("{\"pages\":["
                + "{\"id\":\"news\",\"title\":\"What's new\",\"url\":\"https://example.com/news\"},"
                + "{\"id\":\"web\",\"title\":\"Website\",\"url\":\"https://example.com\",\"open_in\":\"browser\"},"
                + "{\"id\":\"beta\",\"title\":\"Beta\",\"url\":\"https://example.com/beta\",\"testers_only\":true},"
                + "{\"id\":\"bad\",\"title\":\"Bad\",\"url\":\"http://example.com\"}]}");
        RemoteRules.Device d = device(4, "2026-10-28", true);
        List<com.ringdown.core.RemoteConfig.Page> ps = RemoteRules.pages(c, d);
        check(ps.size() == 2 && !ps.get(0).inBrowser && ps.get(1).inBrowser, "pages listed, open_in read, http skipped");
        d.tester = true;
        check(RemoteRules.pages(c, d).size() == 3, "testers see testers_only pages");
    }

    // ------------------------------------------------------------------ robustness

    /** Random edits to a valid file must never crash: parse returns a config or null. */
    static void fuzz(String base) {
        Random r = new Random(42);
        String alphabet = "{}[]\",:0123456789.-eE \\u\ntruefalsenul\u0000\u00e9\ud83d\ude00";
        int nulls = 0;
        for (int i = 0; i < 20000; i++) {
            StringBuilder s = new StringBuilder(base);
            int edits = 1 + r.nextInt(6);
            for (int e = 0; e < edits; e++) {
                int pos = s.length() == 0 ? 0 : r.nextInt(s.length());
                switch (r.nextInt(3)) {
                    case 0: if (s.length() > 0) s.deleteCharAt(pos); break;
                    case 1: s.insert(pos, alphabet.charAt(r.nextInt(alphabet.length()))); break;
                    default: if (s.length() > 0) s.setCharAt(pos, alphabet.charAt(r.nextInt(alphabet.length())));
                }
            }
            try {
                RemoteConfig c = RemoteConfig.parse(s.toString());
                if (c == null) { nulls++; continue; }
                RemoteRules.Device d = device(4, "2026-10-28", r.nextBoolean());
                RemoteRules.banner(c, d, new HashSet<String>());
                RemoteRules.wallpaperNotice(c, d, new HashSet<String>());
                RemoteRules.notifications(c, d, new HashSet<String>());
            } catch (Throwable t) {
                check(false, "fuzz case " + i + " threw " + t);
                return;
            }
        }
        check(true, "20000 random edits: no crash (" + nulls + " rejected as invalid)");
    }

    /** Every ```json example in REMOTE.md must be a valid config. */
    static void docs(String md) {
        if (md == null) {
            check(false, "REMOTE.md found");
            return;
        }
        Matcher m = Pattern.compile("```json\\n(.*?)```", Pattern.DOTALL).matcher(md);
        int n = 0;
        while (m.find()) {
            n++;
            String block = m.group(1);
            String json = block.trim().startsWith("{") ? block : "{" + block + "}";
            try {
                RemoteConfig.parseOrThrow(json);
                check(true, "REMOTE.md example " + n);
            } catch (Json.Error e) {
                check(false, "REMOTE.md example " + n + ": " + e.getMessage() + "\n" + block);
            }
        }
        check(n > 0, "REMOTE.md has examples (" + n + ")");
    }

    static String read(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    static void check(boolean ok, String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL: " + what);
        }
    }
}
