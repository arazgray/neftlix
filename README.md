# Neftlix — Android TV App

![Neftlix](logo.png)

Persian (RTL) Android TV app for browsing `myf2mi.top`: movies and series catalog,
whole-archive search, expandable download groups, an internal ExoPlayer-based player
with subtitle support, and in-app updates. No server needed — the app talks to the
site directly.

![Neftlix home screen](screenshot.png)

## Features

- **Home catalog** — featured rail, per-section rails (movies, series, anime, …),
  movie + series genre rows. Genres are harvested from the live catalog cards, so they
  keep working even as the site changes its menu.
- **Fast launch with cache** — the last catalog is stored on-device and shown
  instantly; a quiet refresh runs in the background on every fresh open
  (⟳ کاتالوگ forces a full refresh with a centered loader).
- **Whole-archive search** — queries the site's `quick-search` endpoint (full title +
  Persian title, cover, series/movie type); falls back to page search, then to the
  downloaded catalog. English voice search (microphone permission is requested
  first); keyboard hides on submit and focus jumps to the first result.
- **Details page** — poster + dimmed backdrop art, IMDb rating, satisfaction %,
  genre pills, dub/sub flags, full Persian story, link counts, download groups
  collapsed by default (season → category → quality → episode links) in a
  two-column layout (description right, downloads left), top user comments.
  Play buttons read **پخش رایگان** with a persistent vector play icon.
- **Player** — internal ExoPlayer (Media3): red focus highlights with unchanged
  sizes, first Back hides the controls, second Back exits; ←/→ seek ∓15s only
  while controls are hidden; subtitle (CC) button always visible, Persian preferred
  and auto-selected, Vazirmatn font, transparent background with drop shadow, RTL.
- **Self-update** — update button checks `version.json`, downloads the new APK
  with a progress dialog (+cancel), cleans the file afterward, and opens the
  installer.
- **Info** — ⓘ button opens an app card (name, description, version, author, site
  link, QR code to arazgray.com).

Remote: D-pad moves, OK opens/expands/plays, Back goes back. Black/red Netflix-style
theme, launcher name **Neftlix**, icon/banner/logo from `icon.png` / `logo.png`,
Vazirmatn font throughout (app + subtitles).

## Requirements

- Xiaomi Mi Box 4K (or any Android TV, API 21+).
- Internet access to `myf2mi.top` and `github.com`.

## Install

1. Take `neftlix-android-tv.apk` from the latest GitHub release
   (or the `neftlix-tv-apk` artifact of the “Build Neftlix TV APK” workflow run).
2. Sideload to the Mi Box (USB stick, `adb install`, or a file-manager app) and open it.

> **One-time reinstall note (v1.3+):** releases up to v1.2 were signed with throwaway
> CI keys, so Android refuses to update them in place (“not installed”). Uninstall the
> old app once and install v1.3+ — all later updates share one persistent key and
> install over each other normally.

## Ship an update

1. Bump `versionCode`/`versionName` in `app/build.gradle`.
2. Commit, tag, push: `git tag vX.Y && git push origin vX.Y` — the release workflow
   builds a signed APK and publishes a `vX.Y` GitHub release.
3. Update `version.json` (`versionCode`, `versionName`, `apkUrl`, `notes`) and push.
4. The app's به‌روزرسانی نفتلیکس button will offer the update. On Android 8+, allow
   “install unknown apps” once when asked.

## Signing

Release builds are signed with a persistent PKCS#12 key stored as the
`NEFTLIX_KEYSTORE_*` GitHub secrets (keystore itself is never committed).
CI restores it before building; local builds without it fall back to the default
debug key. If the key is ever replaced, users must reinstall once (same as above).

## Build (CI)

GitHub Actions, no local Android SDK needed:

- `android-tv-apk.yml` — debug APK artifact on every push to `app/`.
- `release-apk.yml` — on `v*` tags, builds and publishes a GitHub release.

## Project layout

- `app/` — native app (Java, Media3 ExoPlayer, JSoup, Glide).
- `version.json` — latest published version consumed by the in-app updater.
- `icon.png` / `logo.png` — launcher icon/banner and in-app logo sources.
- `tv-preview.html` — static UI mockup.
