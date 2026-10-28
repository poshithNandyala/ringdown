# Remote control

One file, [`remote/config.json`](remote/config.json), controls things in the app **without publishing a new
version**:

- tell people about a new version (banner in the app, a line on the wallpaper, a notification)
- send a message or a notification
- add pages to the app
- switch features on and off (for features added in future versions)

The default file has everything switched off, so the app looks exactly as it does without it.

**Who gets it:** Ringdown 1.3 and newer. Phones check the file every few hours (when the wallpaper is shown),
and every time someone opens the app (at most every 30 minutes). Most phones see a change within a few hours.

## How to change it

1. Open [`remote/config.json`](remote/config.json) on GitHub and click the pencil icon (Edit).
2. Make your change. Keep it valid JSON: quotes around text, commas between items, no comma after the last
   item. Paste it into [jsonlint.com](https://jsonlint.com) if unsure.
3. Click **Commit changes**.

If the file is broken, phones ignore it and keep the last good version, so a mistake can't break the app.
Just fix it and commit again.

### Try it on your own phone first

In the app, **long-press the version line** at the bottom of the screen ("Ringdown 1.3.1 · open source on
GitHub"):

- **Check for news now** fetches the file right away (GitHub can take up to 5 minutes to serve a new
  version).
- **Tester mode** makes your phone a tester phone. Anything marked `"testers_only": true` shows only on tester
  phones, so you can check how it looks before everyone sees it.

## 1. Tell people about a new version

Do this **after** the new version is live on Google Play and `apk/Ringdown.apk` on GitHub is updated.
Change `version_code` (and `version_name`) to the new version's numbers from `build.sh`:

```json
"update": {
  "version_code": 5,
  "version_name": "1.4",
  "required_below": 0,
  "title": "Update available",
  "text": "Version 1.4 adds two new styles.",
  "button": "Update",
  "in_app": true,
  "wallpaper": true,
  "wallpaper_text": "New version available",
  "notify": true,
  "play_url": "https://play.google.com/store/apps/details?id=com.poshith.ringdown",
  "apk_url": "https://github.com/poshithNandyala/ringdown/raw/main/apk/Ringdown.apk"
}
```

Everyone on an older version then gets:

- a banner at the top of the app with an **Update** button (they can tap **Not now**)
- a small gold line under the progress bar on the wallpaper
- one notification

People who installed from Google Play are always sent to Google Play. People who installed the APK from
GitHub are sent to `apk_url`.

- `"in_app": false`, `"wallpaper": false` or `"notify": false` turns off that part.
- `"required_below": 5` means versions below 5 get a banner they can't dismiss (the app keeps working).

## 2. Send a message

Messages can show as a banner in the app, a notification, and/or a line on the wallpaper:

```json
"messages": [
  {
    "id": "diwali-2026",
    "title": "Happy Diwali",
    "text": "Thanks for using Ringdown. Keep your streak going!",
    "in_app": true,
    "notify": true,
    "wallpaper": true,
    "wallpaper_text": "Happy Diwali",
    "from": "2026-11-07",
    "until": "2026-11-09"
  }
]
```

| Field | What it does |
| --- | --- |
| `id` | Any unique name. **Never reuse an id**: each phone shows a notification once per id. |
| `title`, `text` | Up to 60 and 240 characters. |
| `button`, `url` | Optional button that opens a link (`https://` or `market://`). |
| `in_app` | Banner at the top of the app (default `true`). |
| `notify` | Notification, once per phone (default `false`). |
| `wallpaper` | Line under the progress bar (default `false`). `wallpaper_text` is up to 42 characters; without it the title is used. |
| `from`, `until` | Dates (`YYYY-MM-DD`, the phone's local date), both included. Leave out for "always". |
| `mode` | `"break"` or `"build"` to reach only people in that mode. |
| `min_version`, `max_version` | Only these app versions (version codes). |
| `testers_only` | Only tester phones (see above). |

Up to 10 messages; each phone shows at most 3 new notifications per check. People can close a banner with
**Dismiss**, which also hides that message's wallpaper line.

A message only for people still on old versions, linking to a page:

```json
"messages": [
  {
    "id": "old-version-tip",
    "title": "Your version is old",
    "text": "The new version fixes the lock screen on some phones.",
    "button": "See what's new",
    "url": "https://github.com/poshithNandyala/ringdown",
    "max_version": 4
  }
]
```

## 3. Add pages to the app

Pages show in a **MORE** section of the app. They're web pages, so you can build them with HTML/JS and host
them anywhere (GitHub Pages, Vercel, ...):

```json
"pages": [
  {
    "id": "whats-new",
    "title": "What's new",
    "subtitle": "Changes in each version",
    "url": "https://example.com/ringdown/whats-new"
  },
  {
    "id": "community",
    "title": "Join the community",
    "url": "https://example.com/ringdown/community",
    "open_in": "browser"
  }
]
```

Pages open inside the app (or in the browser with `"open_in": "browser"`), must be `https://`, and can't read
anything from the app. Up to 8 pages. `"testers_only": true` works here too.

## 4. Switches for future versions

```json
"flags": {
  "pro_enabled": false,
  "new_styles": true
}
```

Version 1.3 doesn't read any flags. When a future version adds a feature, it can check a flag here, so you
can turn the feature on or off for everyone without another update. Values can be `true`/`false`, numbers or
text.

## 5. Check speed

```json
"refresh_hours": 6
```

How often phones check the file while the wallpaper is in use (1 to 72 hours). Lower means news arrives
faster but phones make more requests. 6 is a good default.

## 6. Move the file to your own server later

```json
"move_to": "https://api.example.com/ringdown/config.json"
```

Phones read the config from that address from then on (GitHub stays as a backup if it's down). Only do this
once that address serves a file in this same format. To undo it, remove `move_to` in the file at the new
address.

## Rules that keep the app on Google Play

- **No ads on the wallpaper or in notifications.** Google Play only allows ads inside the app's own screens,
  and never on the lock screen.
- Use notifications for things about Ringdown (new versions, features, tips), and not too often.
- If a page collects data (analytics, forms, sign-in), first update [PRIVACY.md](PRIVACY.md) and the Data
  safety form in Play Console.
- Payments for features in the app must go through Google Play's billing, not a web page.
- Don't announce a version before it's live on Google Play.

## Everything at once

The whole file with every option, for reference:

```json
{
  "refresh_hours": 6,
  "update": {
    "version_code": 5,
    "version_name": "1.4",
    "required_below": 0,
    "title": "Update available",
    "text": "A new version of Ringdown is ready.",
    "button": "Update",
    "in_app": true,
    "wallpaper": true,
    "wallpaper_text": "New version available",
    "notify": true,
    "play_url": "https://play.google.com/store/apps/details?id=com.poshith.ringdown",
    "apk_url": "https://github.com/poshithNandyala/ringdown/raw/main/apk/Ringdown.apk"
  },
  "messages": [
    {
      "id": "welcome-pages",
      "title": "New: a What's new page",
      "text": "Find it at the bottom of the app.",
      "in_app": true,
      "notify": false,
      "wallpaper": false,
      "mode": "build",
      "min_version": 4,
      "testers_only": true
    }
  ],
  "pages": [
    { "id": "whats-new", "title": "What's new", "subtitle": "Changes in each version", "url": "https://example.com/ringdown/whats-new" }
  ],
  "flags": { "pro_enabled": false },
  "move_to": ""
}
```
