# Ringdown

**An Android live wallpaper that turns a habit into rings: one ring per day.**
Breaking a habit? The rings disappear one by one. Building one? They get built one by one.
You see your progress every time you look at your phone.

<p align="center"><img src="docs/demo.gif" width="300" alt="A ring lifts off the orb, faces you and shatters; the count drops from 78 to 77"></p>

<p align="center"><b><a href="https://github.com/poshithNandyala/ringdown/raw/main/apk/Ringdown.apk">⬇ Download the APK (v1.3.1)</a></b> · Android 8.0 or newer</p>

## Why rings?

Habits run as loops in your brain: a cue, the routine, then the reward. Repeat a loop often enough and the
brain packs it into one automatic chunk. MIT researchers saw neurons in the striatum fire at the start and the
end of these chunks, like brackets around the habit (Martiros, Burgess & Graybiel, 2018). Ringdown draws that
loop as rings, one ring for each day.

- **Breaking a habit: watch the loop fade.** All rings start lit. Every day you stay away from the habit, one
  ring breaks, and the loop in your brain gets weaker too: pathways you stop using fade. The brain recovers
  faster than you'd think: when smokers quit, the extra nicotine receptors their brains had built up dropped
  back to about non-smoker levels in 6 to 12 weeks (Cosgrove et al., 2009). A craving can still pop up now and
  then. That's normal, and it passes. If you slip, restart and keep going.
- **Building a habit: watch the loop form.** Rings start as a faint blueprint. Every day you show up, one ring
  gets built. In a UCL study, a daily habit took a median of 66 days to feel automatic (anywhere from 18 to
  254 days), and missing a single day didn't undo the progress (Lally et al., 2010).
- **Why seeing it helps.** Monitoring progress makes people more likely to reach their goals, especially when
  the progress is recorded somewhere visible (Harkin et al., 2016, a meta-analysis of 138 studies). Your lock
  screen is the place you look most, often right when a craving or an excuse shows up.

## What it looks like

**Break a habit** (rings disappear)

![Orb, Grid and Tunnel styles in break mode](docs/break.png)

**Build a habit** (rings get built)

![Orb, Grid and Tunnel styles in build mode](docs/build.png)

## Features

- **Two modes:** Break a habit (countdown) or Build a habit (rings get added).
- **32 ready-made habits** (no sugar, no smoking, no social media, gym, reading, DSA practice, meditation...)
  or type your own name. Each comes with a sensible number of days.
- **Any length:** 1 to 365 days. Already on a streak? Enter how many days you've already done.
- **Three 3D styles:** Orb (a gyroscope of rings), Grid (a calendar of spinning rings), Tunnel (gates toward a light).
- **A reward every day:** today's ring charges up, then shatters into sparks (or gathers light and settles into
  place), the light flies into your progress dial, the counter rolls and you get a short message.
  Bigger moments on day 1, at 25/50/75% and on the last ring, with fireworks at the end.
- Six color themes, size and position sliders, optional haptic tick, battery saver.
- Tap the home screen and the rings react; double-tap to replay today's animation.
- "On my wallpaper" button plays the next day's animation on your real wallpaper right away.
- Built-in "How it works" guide with the research above.
- **Remote control:** update notices (in the app, on the wallpaper, as a notification), news and extra pages
  are switched on from one file in this repo, without a new release. See [REMOTE.md](REMOTE.md).

## How to use

1. Download [`apk/Ringdown.apk`](https://github.com/poshithNandyala/ringdown/raw/main/apk/Ringdown.apk) on your phone and open it (allow "Install unknown apps" for your browser or file manager when Android asks).
2. Open Ringdown, choose **Break a habit** or **Build a habit**, pick a habit or name your own.
3. Set the number of days (1 ring = 1 day) and, if you're already on a streak, the days you've done.
4. Pick a style and color, tap **Set as wallpaper** and choose **Home and lock screen**.
5. Every midnight a day completes. The first time you look at your phone after that, a ring breaks (or gets
   built) and the count updates.
6. Slipped or missed a day? Tap **Restart**. One bad day doesn't define you.

> Ringdown is a motivation tool, not treatment. Stopping alcohol, some drugs or medicines suddenly can be
> dangerous, so talk to a doctor first. In India, Tele-MANAS (14416) offers free 24×7 mental health support.

## Build from source

No Gradle needed. Requires JDK 17+.

```bash
./build.sh            # -> out/apk/Ringdown.apk (GitHub) and out/aab/Ringdown.aab (Google Play)
```

`build.sh` finds the Android tools in this order: the env vars `SDK_JAR` / `AAPT2` / `R8_CP` /
`BUNDLETOOL`, then `$ANDROID_HOME` (platform 36 + newest build-tools), then a local `buildtools/` folder
(`bundletool.jar` from [google/bundletool](https://github.com/google/bundletool/releases)). A signing key
and its password are created in `keystore/` on the first build. Keep both: Android only installs an update
over an existing app if both are signed with the same key.

Publishing on Google Play: [PLAYSTORE.md](PLAYSTORE.md). Privacy policy: [PRIVACY.md](PRIVACY.md).

### Project layout

| Path | What's there |
| --- | --- |
| `src/core` | The whole scene: 3D ring math, both modes, animations, particles, text. Pure Java, no Android. |
| `src/app` | Android: the wallpaper service, settings screen, guide, and a Canvas renderer that draws all lines as one GPU mesh (`drawVertices`). |
| `harness` | Desktop preview renderer (`Render`), a fuzz test for the scene (`Fuzz`) and tests for the remote control (`RemoteTest`). |
| `res`, `assets` | Icon, wallpaper metadata, bundled Roboto fonts. |
| `remote/config.json` | The remote control file the app reads ([REMOTE.md](REMOTE.md)). |
| `store` | Google Play listing text, icon, feature graphic and screenshots. |
| `apk` | The latest APK for direct download. |

## Sources

- Lally, van Jaarsveld, Potts & Wardle (2010). How are habits formed: Modelling habit formation in the real
  world. *European Journal of Social Psychology*. [doi:10.1002/ejsp.674](https://doi.org/10.1002/ejsp.674)
- Martiros, Burgess & Graybiel (2018). Inversely active striatal projection neurons and interneurons
  selectively delimit useful behavioral sequences. *Current Biology*. [PMC5820126](https://pmc.ncbi.nlm.nih.gov/articles/PMC5820126)
- Cosgrove et al. (2009). β2-Nicotinic acetylcholine receptor availability during acute and prolonged
  abstinence from tobacco smoking. *Archives of General Psychiatry* 66(6). [doi:10.1001/archgenpsychiatry.2009.41](https://doi.org/10.1001/archgenpsychiatry.2009.41)
- Harkin et al. (2016). Does monitoring goal progress promote goal attainment? A meta-analysis of the
  experimental evidence. *Psychological Bulletin* 142(2). [doi:10.1037/bul0000025](https://doi.org/10.1037/bul0000025)

## License

Code: MIT (see `LICENSE`). Roboto fonts in `assets/fonts`: © Google, Apache License 2.0 (`assets/fonts/LICENSE-Roboto.txt`).
