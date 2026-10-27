package com.ringdown.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Decides what the remote config shows on this phone: the banner in the app, the line on the
 * wallpaper, and which notifications are due. Pure logic, so it is tested on the desktop.
 */
public final class RemoteRules {
    private RemoteRules() {}

    public static final String DEFAULT_UPDATE_TITLE = "Update available";
    public static final String DEFAULT_UPDATE_TEXT = "A new version of Ringdown is ready.";
    public static final String DEFAULT_UPDATE_BUTTON = "Update";
    public static final String DEFAULT_UPDATE_WALLPAPER = "New version available";
    public static final int MAX_NOTES_PER_CHECK = 3;

    /** Facts about this phone that the rules depend on. */
    public static final class Device {
        public int versionCode;
        public long today;
        public int mode = RingScene.MODE_BREAK;
        public boolean fromPlay, tester;
        public String pkg = "";
    }

    public static final class Banner {
        public final String key, title, text, button, url;
        public final boolean dismissable, update;

        Banner(String key, String title, String text, String button, String url, boolean dismissable, boolean update) {
            this.key = key; this.title = title; this.text = text; this.button = button; this.url = url;
            this.dismissable = dismissable; this.update = update;
        }
    }

    /** A system notification to post once. */
    public static final class Note {
        public final String key, title, text, url;

        Note(String key, String title, String text, String url) {
            this.key = key; this.title = title; this.text = text; this.url = url;
        }
    }

    public static String updateKey(RemoteConfig c) {
        return "update:" + c.update.versionCode;
    }

    public static String messageKey(RemoteConfig.Message m) {
        return "msg:" + m.id;
    }

    public static boolean updateAvailable(RemoteConfig c, Device d) {
        return c != null && d.versionCode > 0 && c.update.versionCode > d.versionCode;
    }

    public static boolean updateRequired(RemoteConfig c, Device d) {
        return updateAvailable(c, d) && d.versionCode < c.update.requiredBelow;
    }

    /**
     * Where "Update" leads. Copies installed from Google Play only ever go to Google Play
     * (Play doesn't allow updating from anywhere else); other copies can use the APK link.
     */
    public static String updateUrl(RemoteConfig c, Device d) {
        String play = c.update.playUrl.length() > 0 ? c.update.playUrl
                : "https://play.google.com/store/apps/details?id=" + d.pkg;
        if (d.fromPlay) return play;
        return c.update.apkUrl.length() > 0 ? c.update.apkUrl : play;
    }

    public static boolean active(RemoteConfig.Message m, Device d) {
        if (m.testersOnly && !d.tester) return false;
        if (d.today < m.from || d.today > m.until) return false;
        if (m.minVersion > 0 && d.versionCode < m.minVersion) return false;
        if (m.maxVersion > 0 && d.versionCode > m.maxVersion) return false;
        return m.mode == RemoteConfig.MODE_ANY || m.mode == d.mode;
    }

    /** The one banner to show at the top of the app, or null. */
    public static Banner banner(RemoteConfig c, Device d, Set<String> dismissed) {
        if (c == null) return null;
        if (updateAvailable(c, d) && c.update.inApp) {
            String key = updateKey(c);
            boolean required = updateRequired(c, d);
            if (required || !dismissed.contains(key)) {
                return new Banner(key, or(c.update.title, DEFAULT_UPDATE_TITLE), or(c.update.text, DEFAULT_UPDATE_TEXT),
                        or(c.update.button, DEFAULT_UPDATE_BUTTON), updateUrl(c, d), !required, true);
            }
        }
        for (RemoteConfig.Message m : c.messages) {
            if (!m.inApp || !active(m, d)) continue;
            String key = messageKey(m);
            if (dismissed.contains(key)) continue;
            String button = m.url.length() == 0 ? "" : or(m.button, "Open");
            return new Banner(key, m.title, m.text, button, m.url, true, false);
        }
        return null;
    }

    /** The short line to show on the wallpaper, or null. */
    public static String wallpaperNotice(RemoteConfig c, Device d, Set<String> dismissed) {
        if (c == null) return null;
        if (updateAvailable(c, d) && c.update.wallpaper
                && (updateRequired(c, d) || !dismissed.contains(updateKey(c)))) {
            return or(c.update.wallpaperText, DEFAULT_UPDATE_WALLPAPER);
        }
        for (RemoteConfig.Message m : c.messages) {
            if (!m.wallpaper || !active(m, d) || dismissed.contains(messageKey(m))) continue;
            String s = m.wallpaperText.length() > 0 ? m.wallpaperText : RemoteConfig.clean(m.title.length() > 0 ? m.title : m.text, 42);
            if (s.length() > 0) return s;
        }
        return null;
    }

    /** Notifications that are due and haven't been shown on this phone yet. */
    public static List<Note> notifications(RemoteConfig c, Device d, Set<String> notified) {
        List<Note> out = new ArrayList<>();
        if (c == null) return out;
        if (updateAvailable(c, d) && c.update.notify && !notified.contains(updateKey(c))) {
            out.add(new Note(updateKey(c), or(c.update.title, DEFAULT_UPDATE_TITLE),
                    or(c.update.text, DEFAULT_UPDATE_TEXT), updateUrl(c, d)));
        }
        for (RemoteConfig.Message m : c.messages) {
            if (out.size() >= MAX_NOTES_PER_CHECK) break;
            if (!m.notify || !active(m, d) || notified.contains(messageKey(m))) continue;
            String title = m.title.length() > 0 ? m.title : "Ringdown";
            out.add(new Note(messageKey(m), title, m.text, m.url));
        }
        return out;
    }

    /** Pages to list in the app (testers-only pages only on tester phones). */
    public static List<RemoteConfig.Page> pages(RemoteConfig c, Device d) {
        List<RemoteConfig.Page> out = new ArrayList<>();
        if (c == null) return out;
        for (RemoteConfig.Page p : c.pages) if (!p.testersOnly || d.tester) out.add(p);
        return out;
    }

    private static String or(String s, String def) {
        return s != null && s.length() > 0 ? s : def;
    }
}
