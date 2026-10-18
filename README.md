# Neftlix

Android TV app (RTL Persian, remote-friendly, black/red Netflix-style theme) for browsing
`myf2mi.top`. No server needed — the app fetches everything itself, live from the site.

## Features

- **Catalog on open**: fetches the homepage (featured + sections), movie/series genres, and the first page of the Movies and Series archives.
- **Refresh**: the به‌روزرسانی button re-fetches the catalog at any time.
- **Browse**: Persian RTL home with featured/sections, movie + series genre rows, archive lists with صفحه قبل/بعد pagination.
- **Search**: whole-archive title search via the site's `quick-search` endpoint (server search with `/?s=` and local-filter fallbacks).
- **Details**: series/movie pages show download groups collapsed by default (season → category/language → quality → episode links), expandable with the remote. Download buttons play directly (پخش رایگان).
- **Player**: internal ExoPlayer (Media3) player with subtitle (CC) button and Persian subtitle preference; D-pad controls, Back exits.
- **Self-update**: the ⬇ نسخه جدید button checks `version.json`, downloads a newer APK, and opens the installer.

Remote: D-pad navigates cards/buttons, OK expands groups or opens details/player, Back
returns to the catalog. Target is sideload-friendly (`minSdk 21`, debug APK) for
Xiaomi Mi Box 4K.

## Install

1. Actions → “Build Neftlix TV APK” → download artifact `neftlix-tv-apk` → `app-debug.apk`
   (or take the APK from the latest GitHub release).
2. Sideload to Mi Box (USB stick, `adb install`, or a file-manager app) and open it.
   The launcher shows **Neftlix**.

## Shipping an update

1. Bump `versionCode` (and `versionName`) in `app/build.gradle`.
2. Commit, tag, push: `git tag v1.1 && git push origin v1.1` — the release workflow builds the APK and publishes a `v1.1` GitHub release with the new binary.
3. Update `version.json` (`versionCode`, `versionName`, `apkUrl`, `notes`) and push to `main`.
4. The app's ⬇ نسخه جدید button will now offer the update: download + install prompt. On Android 8+, the user must allow “install unknown apps” once when asked.

## Build (CI)

GitHub Actions, no local Android SDK needed:

- `android-tv-apk.yml` — builds the installable debug APK on every push to `app/` (artifact `neftlix-tv-apk`).
- `release-apk.yml` — on `v*` tags, builds and publishes a GitHub release with the APK.

## Project structure

- `app/` — native Android TV app (Java, Media3 ExoPlayer, JSoup, Glide).
- `version.json` — latest published version info consumed by the in-app updater.
- `icon.png` / `logo.png` — launcher icon/banner and in-app logo sources.
- `tv-preview.html` — static UI mockup of the TV screens.
