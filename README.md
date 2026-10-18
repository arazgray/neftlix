# Neftlix — Android TV App

![Neftlix](logo.png)

Persian (RTL) Android TV app for browsing `myf2mi.top`: movies and series catalog,
whole-archive search, expandable watch lists, an internal ExoPlayer-based player
with subtitle support, and in-app updates. No server needed — the app talks to the
site directly. Media source: فیلم۲مدیا (`myf2mi.top`), where the whole archive can
be downloaded from.

![Neftlix home screen](screenshot.png)

## Features

- **Home** — one horizontal rail per site section (20-item preview + «مشاهده همه»),
  then genre chip rails for movies and series. Focused cards never change size:
  focus is shown with a red ring + bold title only, so nothing overflows its frame.
- **Same look on every TV** — every screen is drawn from one 960dp design space
  (`Ui.java`): sizes, spacing and typography are scaled by `screenWidthDp / 960`, so
  720p, 1080p and 4K panels — and boxes that report `density = 1` — get the same
  proportions instead of a tiny, shrunken UI.
- **Fast launch with cache** — the last catalog is stored on-device and shown
  instantly; a quiet refresh runs in the background on every fresh open
  (`کاتالوگ` forces a full refresh with a centered loader).
- **Whole-archive search** — queries the site's `quick-search` endpoint (full title +
  Persian title, cover, series/movie type); falls back to page search, then to the
  downloaded catalog. English voice search (microphone permission is requested
  first); keyboard hides on submit and focus jumps to the first result.
- **Details page** — wide key-art header with poster, cleaned title (the noisy
  «دانلود … بدون سانسور …» prefix/suffix of the site title is stripped for display),
  IMDb rating / satisfaction % / type / link-count pills and a «پخش تریلر» button
  (opens the YouTube app searching «`<title> <year> trailer`»). Below: a glassy
  translucent story + genre/flags panel, then two collapsed sections — comments
  first, «فهرست پخش» second (season → dub/sub category → quality → episode link
  grid, every link streams directly in the internal player).
- **Player** — internal ExoPlayer (Media3): red focus highlights with unchanged
  sizes, first Back hides the controls, second Back exits; ←/→ seek ∓15s only
  while controls are hidden; the screen is kept awake so the box never drops to the
  screensaver mid-play; subtitle (CC) button always visible, Persian preferred
  and auto-selected, Vazirmatn font, transparent background with drop shadow, RTL.
- **Self-update** — the app checks `version.json` once on every launch and shows the
  update dialog when a newer version exists; the update button checks on demand,
  downloads the new APK with a progress dialog (+cancel), cleans the file afterward,
  and opens the installer.
- **Info** — the info button opens an app card (name, description, media source
  credit, version, author, site link, QR code to arazgray.com).

Remote: D-pad moves, OK opens/expands/plays, Back goes back. Netflix-style black/red
theme, brand-red focus rings (focused elements never resize, so borders stay intact),
glassy pills, full-screen transparent-logo splash until the catalog shows, launcher name **Neftlix**,
icon/banner/logo from `icon.png` / `logo.png` / `main-bg.png` (in-app marks use the
transparent `logo_transparent.png`), Vazirmatn font throughout (app + subtitles).

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
4. The app's update button will offer the update. On Android 8+, allow
   “install unknown apps” once when asked.

## Signing

Release builds are signed with a persistent PKCS#12 key stored as the
`NEFTLIX_KEYSTORE_*` GitHub secrets (keystore itself is never committed).
CI restores it before building; local builds without it fall back to the default
debug key. If the key is ever replaced, users must reinstall once (same as above).

## Build (CI)

GitHub Actions, no local Android SDK needed:

- `android-tv-apk.yml` — runs unit tests (`testDebugUnitTest`) and uploads the debug
  APK artifact on every push to `app/`.
- `release-apk.yml` — on `v*` tags, builds and publishes a GitHub release.

Screenshots are committed as static files (`screenshot.png`, `screenshot-details.png`) —
there is intentionally no emulator CI: booting a TV emulator on GitHub runners takes
several minutes, so visual checks are done from these images and a normal install.

## Tests

JVM unit tests under `app/src/test/` (JUnit 4, run in CI on every push to `app/`):

- `UiTest` — Persian digits, content-type labels, hi-res image URLs, 960dp scale
  clamping, grid column math.
- `DisplayTextTest` — title cleaning (prefix/tails/edge cases), watch-label
  rewriting, YouTube trailer queries.
- `CatalogRepositoryTest` — pagination URLs, next-page detection, type/host/label
  guards, quick-search JSON incl. the site's `_formatted` fallback, article cards,
  genre extraction, download-link rows (direct/`onclick`/textarea, login-gated skip).
- `CatalogCacheTest` — cache save/load round-trip, missing and corrupt files.

Android-dependent code (views, Glide, ExoPlayer) is intentionally kept out of the
tested surface: pure logic lives in static/package-private helpers.

## Project layout

- `app/` — native app (Java, Media3 ExoPlayer, JSoup, Glide).
- `app/src/main/java/com/arazgray/neftlix/Ui.java` — design system: palette, spacing,
  scale factor and the reusable TV widgets (pill buttons, chips, poster cards, rails,
  expandable sections).
- `app/src/main/java/com/arazgray/neftlix/DisplayText.java` — pure display-text
  helpers (title cleaning, watch labels, trailer queries), covered by unit tests.
- `app/src/test/` — JVM unit tests (see Tests above).
- `version.json` — latest published version consumed by the in-app updater.
- `icon.png` / `logo.png` — launcher icon/banner and in-app logo sources.
- `tv-preview.html` — static preview of the screens.
