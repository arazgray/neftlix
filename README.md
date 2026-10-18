# Neftlix

Android TV app (RTL Persian, remote-friendly, internal player) for browsing `myf2mi.top`.
No server or PHP needed — the app fetches everything itself, live from the site.

## What the app does

- **Catalog on open**: fetches the homepage (featured + sections), movie/series genres, and the first page of the Movies and Series archives.
- **Refresh**: the به‌روزرسانی button re-fetches the catalog at any time.
- **Browse**: Persian RTL home with featured/sections, movie + series genre rows, archive lists with صفحه قبل/بعد pagination.
- **Search**: whole-archive title search via the site's `quick-search` endpoint
  (server search with `/?s=` and local-filter fallbacks).
- **Details**: series/movie pages show download groups collapsed by default (season → category/language → quality → episode links), expandable with the remote.
- **Player**: internal ExoPlayer (Media3) player; D-pad controls, Back exits.

Remote: D-pad navigates cards/buttons, OK expands groups or opens details/player, Back
returns to the catalog. Target is sideload-friendly (`minSdk 21`, debug APK) for
Xiaomi Mi Box 4K.

## Build the installable APK

GitHub Actions (no local Android SDK needed):

1. Push this repo to GitHub.
2. Actions → “Build Neftlix TV APK” → Run (also runs on pushes touching `app/`).
3. Download artifact `neftlix-tv-apk` → `app-debug.apk`.
4. Sideload to Mi Box (USB stick, `adb install`, or a file-manager app) and open it.
