# Publishing Ringdown on Google Play

A step-by-step guide. The Play Console **Dashboard** also lists every task in order, so you can follow it
alongside this.

## What you need

- A **Google Play developer account** (one-time fee of US$25): <https://play.google.com/console/signup>.
  Choose a personal account. Google verifies your identity, which can take a few days.
- **`Ringdown.aab`**: the file you upload to Google Play. Build it with `./build.sh` (it ends up in
  `out/aab/`).
- **Your signing key**: `keystore/ringdown.p12` and `keystore/password.txt`. Keep two copies somewhere safe
  (for example Google Drive and your laptop). Never put them on GitHub (the `.gitignore` already blocks them).
- **12 testers**: people with Android phones and Google accounts (friends, classmates). New personal accounts
  must run a 14-day test before the app can go public.

## 1. Create the app

Play Console → **Create app**:

- App name: `Ringdown: Habit Wallpaper`
- Default language: English
- App or game: **App**
- Free or paid: **Free** (a free app can't be changed to paid later; see "Making money later" below)
- Accept the declarations → **Create app**

## 2. Answer the "App content" questions

Dashboard → **Set up your app** (also under **Policy and programs → App content**):

| Question | Answer |
| --- | --- |
| Privacy policy | `https://github.com/poshithNandyala/ringdown/blob/main/PRIVACY.md` |
| App access | All functionality is available without any access restrictions |
| Ads | No, my app doesn't contain ads |
| Content rating | Start the questionnaire, category **All other app types**. No violence, sexual content, bad language or gambling; users can't chat or share content. When it asks about drugs, alcohol or tobacco, answer **Yes**: the habit names mention them (as things to quit). |
| Target audience | **18 and over** (the habits include alcohol, drugs, gambling and porn) |
| News app | No |
| Data safety | "Does your app collect or share any of the required user data types?" → **No**. Ringdown keeps everything on the phone, and the update check sends nothing about the person. |
| Government app | No |
| Financial features | My app doesn't provide any financial features |
| Health apps | Tick **Mental and Behavioral Health** (the app helps people quit smoking, alcohol and drugs) |
| Advertising ID | No, my app doesn't use an advertising ID |

## 3. Fill in the store listing

**Grow users → Store presence → Main store listing**. Copy the texts and images from
[`store/listing.md`](store/listing.md):

- App name, short description, full description
- App icon: `store/icon-512.png`
- Feature graphic: `store/feature-graphic.png`
- Phone screenshots: the six files in `store/screenshots/`

Also set **Store settings**: category **Personalization**, and a contact email (shown publicly on Google
Play).

## 4. Run the closed test (14 days, 12 testers)

1. **Test and release → Testing → Closed testing** → open the default track (or create one).
2. **Testers** tab → create an email list with the Gmail addresses of at least 12 testers → Save.
3. **Releases** → **Create new release**.
   - When asked about app signing, keep **Google-generated key** (Play App Signing). Your key then becomes
     the "upload key".
   - Upload `Ringdown.aab`, release name `1.3.1`, release notes `First release on Google Play.`
   - **Next** → **Save** → send the release for review.
4. When it's approved, copy the **opt-in link** from the Testers tab and send it to your testers. Each one
   opens it, taps **Become a tester**, then installs Ringdown from Google Play.
5. Keep at least 12 testers opted in for **14 days in a row**. Ask them to actually use it; when you apply
   for production, Google asks what you learned from the test.

If a tester has the GitHub version installed, they must uninstall it first (see "Your signing key").

After uploading, Google also tests the app on real phones automatically. Check **Test and release →
Pre-launch report** for crashes.

## 5. Go public

After the 14 days: **Dashboard → Apply for production** → answer the questions about your test. Google
reviews it, usually within a few days. Then **Test and release → Production → Create new release** → add
the same build (from the library) → **Roll out**.

## Releasing an update

1. Make your changes, then in `build.sh` add 1 to `VERSION_CODE` and change `VERSION_NAME` (for example
   `VERSION_CODE=5`, `VERSION_NAME=1.4`).
2. Run `./build.sh`. You get `out/aab/Ringdown.aab` (for Play) and `out/apk/Ringdown.apk` (for GitHub).
3. Play Console → **Production → Create new release** → upload the `.aab` → release notes → **Roll out**.
4. Copy `out/apk/Ringdown.apk` over `apk/Ringdown.apk` and push, for people who use the GitHub download.
5. When the update is live on Google Play, set `update.version_code` (and `version_name`) in
   `remote/config.json` to the new numbers. Everyone on an older version then gets the update banner, the
   line on the wallpaper and a notification. See [REMOTE.md](REMOTE.md).

## Your signing key

- With Play App Signing, Google signs the app that people download from Google Play. Your key
  (`ringdown.p12`) is only the upload key. If you lose it or it leaks, ask for an upload key reset in Play
  Console (**Test and release → App integrity → App signing**).
- The APK on GitHub is signed with your key, so it is not interchangeable with the Google Play version.
  To switch from the GitHub version to the Play version, uninstall it first (the habit settings start fresh;
  set "Days already done" again).

## Making money later

- **Charging money:** Google Play doesn't let a free app become paid. The way to charge is in-app purchases
  (for example a one-time "Ringdown Pro" with extra styles) or a subscription, through Google Play Billing.
  That needs an app update that adds Google's billing library. After that update, the `flags` in
  `remote/config.json` can turn offers on and off without more updates.
- **Ads:** needs an ad library (such as AdMob) in an app update. Google Play only allows ads inside the
  app's own screens: never on the wallpaper, the lock screen or in notifications. When you add ads, also
  update the Ads and Data safety answers and [PRIVACY.md](PRIVACY.md).
- **Messages, notifications and new pages:** already possible now, from GitHub. See [REMOTE.md](REMOTE.md).

## Rules to keep in mind

- **Target API level:** Google raises it every year around 31 August. Right now new apps and updates must
  target Android 16 (API 36), which `build.sh` does. When the next level is required, change `TARGET` in
  `build.sh`.
- **Package name** `com.poshith.ringdown` is permanent on Google Play.
- **Developer verification:** apps on Google Play are registered automatically. Google is also starting to
  require apps installed from outside Google Play (like the GitHub APK) to be registered to a verified
  developer: in a few countries from September 2026, and worldwide in 2027. When Play Console offers it,
  register the package name and your key there so the GitHub APK keeps installing.
