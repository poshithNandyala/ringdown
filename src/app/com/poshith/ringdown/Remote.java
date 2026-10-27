package com.poshith.ringdown;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import com.ringdown.core.Countdown;
import com.ringdown.core.RemoteConfig;
import com.ringdown.core.RemoteRules;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * The remote control. Now and then the app reads remote/config.json from the GitHub repo and
 * shows what it asks for: an update banner, a line on the wallpaper, a notification, extra
 * pages. With the file's default content it shows nothing. All failures are silent: no
 * network or a broken file just means the last good config (or nothing) stays in place.
 */
final class Remote {
    static final String URL_GITHUB = "https://raw.githubusercontent.com/poshithNandyala/ringdown/main/remote/config.json";
    static final String URL_CDN = "https://cdn.jsdelivr.net/gh/poshithNandyala/ringdown@main/remote/config.json";

    /** Every key starts with this, so the wallpaper knows such changes only affect the notice. */
    static final String PREFIX = "remote_";
    static final String K_JSON = "remote_json";            // last good config text
    static final String K_ETAG = "remote_etag";
    static final String K_ETAG_URL = "remote_etag_url";
    static final String K_AT = "remote_at";                // last successful check
    static final String K_TRY_AT = "remote_try_at";        // last attempt
    static final String K_MOVED = "remote_moved_to";       // "move_to" from the config
    static final String K_DISMISSED = "remote_dismissed";  // banners the user closed
    static final String K_NOTIFIED = "remote_notified";    // notifications already shown
    static final String K_TESTER = "remote_tester";        // tester phone: also gets testers_only items
    static final String K_ASKED = "remote_asked_notify";   // asked for the notification permission

    static final long APP_MAX_AGE = 30 * 60_000L;          // opening the app checks if older than this
    private static final long RETRY_AFTER = 20 * 60_000L;
    private static final long NOTIFY_CHECK_EVERY = 30 * 60_000L;
    private static final int MAX_BYTES = 64 * 1024;
    static final String CHANNEL = "news";

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "ringdown-remote");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        }
    });
    private static volatile boolean busy;
    private static volatile long lastNotifyCheck;
    private static String parsedFrom;
    private static RemoteConfig parsed;
    private static int versionCode = -1;
    private static String versionName;
    private static Boolean fromPlay;

    private Remote() {}

    // ------------------------------------------------------------------ reading

    /** The last good config, or null. Re-parses only when the stored text changed. */
    static synchronized RemoteConfig config(SharedPreferences p) {
        String json = p.getString(K_JSON, null);
        if (json == null) {
            parsedFrom = null;
            parsed = null;
        } else if (!json.equals(parsedFrom)) {
            parsed = RemoteConfig.parse(json);
            parsedFrom = json;
        }
        return parsed;
    }

    static RemoteRules.Device device(Context ctx, SharedPreferences p) {
        RemoteRules.Device d = new RemoteRules.Device();
        d.versionCode = versionCode(ctx);
        d.today = Countdown.today();
        d.mode = Prefs.mode(p);
        d.fromPlay = fromPlay(ctx);
        d.tester = p.getBoolean(K_TESTER, false);
        d.pkg = ctx.getPackageName();
        return d;
    }

    static Set<String> set(SharedPreferences p, String key) {
        return new HashSet<>(p.getStringSet(key, Collections.<String>emptySet()));
    }

    static void dismiss(SharedPreferences p, String key) {
        Set<String> s = set(p, K_DISMISSED);
        s.add(key);
        p.edit().putStringSet(K_DISMISSED, s).apply();
    }

    /** How often to check, as the config asks (default 6 hours). */
    static long maxAge(SharedPreferences p) {
        RemoteConfig c = config(p);
        return (c == null ? 6 : c.refreshHours) * 3_600_000L;
    }

    // ------------------------------------------------------------------ checking

    /** Checks for a new config in the background if the last check is older than maxAgeMs. */
    static void refresh(Context ctx, long maxAgeMs) {
        final Context app = ctx.getApplicationContext();
        final SharedPreferences p = Prefs.get(app);
        long now = System.currentTimeMillis();
        long at = p.getLong(K_AT, 0L), tried = p.getLong(K_TRY_AT, 0L);
        boolean fresh = at > 0 && now >= at && now - at < maxAgeMs;
        boolean backoff = maxAgeMs > 0 && tried > 0 && now >= tried && now - tried < RETRY_AFTER;
        if (busy) return;
        if (fresh || backoff) {
            // Nothing to download, but a message's start date may have arrived since.
            if (now - lastNotifyCheck > NOTIFY_CHECK_EVERY) {
                lastNotifyCheck = now;
                run(new Runnable() {
                    @Override public void run() { notifyDue(app, p); }
                });
            }
            return;
        }
        busy = true;
        if (!run(new Runnable() {
            @Override public void run() {
                try {
                    check(app, p);
                } finally {
                    busy = false;
                }
            }
        })) busy = false;
    }

    private static boolean run(final Runnable r) {
        try {
            IO.execute(new Runnable() {
                @Override public void run() {
                    try { r.run(); } catch (Throwable ignored) {}
                }
            });
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void check(Context app, SharedPreferences p) {
        long now = System.currentTimeMillis();
        p.edit().putLong(K_TRY_AT, now).apply();
        String moved = p.getString(K_MOVED, "");
        List<String> urls = new ArrayList<>();
        if (moved.startsWith("https://")) urls.add(moved);
        urls.add(URL_GITHUB);
        urls.add(URL_CDN);
        for (String u : urls) {
            String etag = u.equals(p.getString(K_ETAG_URL, "")) ? p.getString(K_ETAG, "") : "";
            Fetched f = get(u, etag);
            if (f == null) continue;
            if (f.notModified) {
                p.edit().putLong(K_AT, now).apply();
                break;
            }
            RemoteConfig c = RemoteConfig.parse(f.body);
            if (c == null) continue;   // broken file: keep the last good one, try the next source
            SharedPreferences.Editor e = p.edit()
                    .putString(K_JSON, f.body).putLong(K_AT, now)
                    .putString(K_ETAG, f.etag).putString(K_ETAG_URL, u);
            if (!c.moveTo.equals(moved)) e.putString(K_MOVED, c.moveTo);
            e.apply();
            break;
        }
        lastNotifyCheck = now;
        notifyDue(app, p);
    }

    private static final class Fetched {
        String body = "", etag = "";
        boolean notModified;
    }

    private static Fetched get(String url, String etag) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Accept", "application/json, text/plain");
            if (etag.length() > 0) c.setRequestProperty("If-None-Match", etag);
            int code = c.getResponseCode();
            Fetched f = new Fetched();
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                f.notModified = true;
                return f;
            }
            if (code != HttpURLConnection.HTTP_OK) return null;
            if (!"https".equalsIgnoreCase(c.getURL().getProtocol())) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) return null;
                out.write(buf, 0, n);
            }
            f.body = new String(out.toByteArray(), "UTF-8");
            String et = c.getHeaderField("ETag");
            f.etag = et == null ? "" : et;
            return f;
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) {
                try { c.disconnect(); } catch (Throwable ignored) {}
            }
        }
    }

    // ------------------------------------------------------------------ notifications

    static boolean canNotify(Context app) {
        if (Build.VERSION.SDK_INT >= 33
                && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    /** Posts the notifications the config asks for that this phone hasn't shown yet. */
    static void notifyDue(Context app, SharedPreferences p) {
        try {
            RemoteConfig c = config(p);
            if (c == null || !canNotify(app)) return;
            NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            Set<String> notified = set(p, K_NOTIFIED);
            List<RemoteRules.Note> due = RemoteRules.notifications(c, device(app, p), notified);
            if (due.isEmpty()) return;
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Updates and news", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("New versions of Ringdown and messages from its developer");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            for (RemoteRules.Note n : due) {
                try {
                    nm.notify(n.key.hashCode() & 0x7fffffff, build(app, n));
                    notified.add(n.key);
                } catch (Throwable ignored) {
                }
            }
            p.edit().putStringSet(K_NOTIFIED, notified).apply();
        } catch (Throwable ignored) {
        }
    }

    private static Notification build(Context app, RemoteRules.Note n) {
        Intent open = null;
        if (n.url.length() > 0) open = new Intent(Intent.ACTION_VIEW, Uri.parse(n.url));
        if (open == null) open = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (open == null) open = new Intent(app, SettingsActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(app, n.key.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(SettingsActivity.GOLD)
                .setContentTitle(n.title)
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (n.text.length() > 0) b.setContentText(n.text).setStyle(new Notification.BigTextStyle().bigText(n.text));
        return b.build();
    }

    // ------------------------------------------------------------------ about this install

    @SuppressWarnings("deprecation")
    static synchronized int versionCode(Context ctx) {
        if (versionCode < 0) {
            try {
                PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
                versionCode = Build.VERSION.SDK_INT >= 28 ? (int) pi.getLongVersionCode() : pi.versionCode;
                versionName = pi.versionName;
            } catch (Throwable t) {
                versionCode = 0;
            }
        }
        return versionCode;
    }

    static String versionName(Context ctx) {
        versionCode(ctx);
        return versionName == null ? "" : versionName;
    }

    /** True if Google Play installed this copy (then updates must come from Google Play). */
    @SuppressWarnings("deprecation")
    static synchronized boolean fromPlay(Context ctx) {
        if (fromPlay == null) {
            String installer = null;
            try {
                PackageManager pm = ctx.getPackageManager();
                installer = Build.VERSION.SDK_INT >= 30
                        ? pm.getInstallSourceInfo(ctx.getPackageName()).getInstallingPackageName()
                        : pm.getInstallerPackageName(ctx.getPackageName());
            } catch (Throwable ignored) {
            }
            fromPlay = "com.android.vending".equals(installer);
        }
        return fromPlay;
    }
}
