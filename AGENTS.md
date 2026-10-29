# AGENTS.md — Neftlix Android TV

Guide for AI agents working on this repo. Read this before touching anything.

## What this is

Neftlix — a **bilingual (Persian RTL / English LTR) Android TV** client (Java 17, Gradle, minSdk 21 / target 34 /
compileSdk 35) that scrapes the catalog off `https://www.myf2mi.top/` with Jsoup and plays
video with media3 ExoPlayer. D-pad remote driven. All UI is built **programmatically in Java —
there are no XML layouts**. User-visible strings live in `values/strings.xml` (English)
and `values-fa/strings.xml` (Persian) — keep both in sync, never hardcode UI text,
never force layout direction (it follows the locale); site-sourced Persian content
(story, comments) may keep explicit direction.

Repo: `github.com/arazgray/neftlix` (public). App id: `com.arazgray.neftlix`.

## Golden rules

1. **Do not break behavior.** No data/network/parsing regressions. When in doubt, view-layer
   changes only. `CatalogRepository.java` and `Models.java` are the data layer — treat as frozen
   unless the task is explicitly about them.
2. **CI is the only real verification.** There is usually no local JDK/Android SDK here.
   Push a branch and let GitHub Actions compile *and run unit tests*. A green
   `Build Neftlix TV APK` run takes ~2-4 min (tests + APK).
3. **Never run or re-add emulator/screenshot CI** — explicitly vetoed by the user.
   Visual reference = committed `screenshot.png` / `screenshot-details.png`.
4. `version.json` is the update-check/publish manifest — **do not bump it unless releasing**;
   `app/build.gradle` is where version bumps go during normal work.
5. No code comments in the codebase (user request — do not add any); commits are short, terse, often with a
   scope prefix like `v1.11: …` or `Hero fallback …`.

## Environment / toolchain

- Workstation: Linux, git + `gh` CLI authenticated as `arazgray`.
- No local Xcode/JDK/Android SDK — **do not attempt local builds**; use CI.
- Useful: `gh run list --workflow android-tv-apk.yml --limit 5`, `gh run watch <id>`,
  `gh run download <id> -n <artifact> -D dir`.

## Workflows cheat-sheet

| Workflow | Trigger | Purpose |
|---|---|---|
| `android-tv-apk.yml` | push touching `app/**`, gradle files, itself; manual | unit tests + debug APK (~2-4 min) |
| `release-apk.yml` | tag `v*` | builds + publishes release APK to GitHub Releases |

Commands:

```bash
# tests + compile check
git push origin <branch> && gh run watch $(gh run list -L1 --workflow android-tv-apk.yml --json databaseId -q '.[0].databaseId')
```

Signing: `neftlix-release.keystore` is restored from secret `NEFTLIX_KEYSTORE_BASE64` (same
key for debug+release so updates install over previous installs). Env vars:
`NEFTLIX_KEYSTORE_FILE/PASSWORD`, `NEFTLIX_KEY_ALIAS/PASSWORD`.

## Architecture map

```
app/src/main/java/com/arazgray/neftlix/
  CatalogRepository.java   data layer: Jsoup scraping (home, listings, search, details, episodes/downloads)
  Models.java              POJOs: CatalogData/BrowseSection/BrowseItem/GenreCategory/DetailsInfo/DownloadGroup...
  SettingsStore.java       SharedPreferences: UI language (fa/en) + accent color index
  LocaleHelper.java        wraps context with the stored locale (attachBaseContext in each activity)
  Ui.java                  design system (scale, palette, widget factories — see below)
                           + per-activity `brand` accent / `persian` flag, locale-aware
                           digits, rails mirror automatically via layout direction (reveal() clamps
                           both ways; chevron/back icons mirror); **new screens should use these
                           factories, not raw views.**
  DisplayText.java         pure display-text helpers (title cleaning, watch labels,
                           trailer queries), unit-tested
  MainActivity.java        home: rails/grid + search + genres + watchlist page (no hero),
                           auto update check
  DetailsActivity.java     details: title/key-art, trailer + watchlist-toggle buttons,
                           comments + playlist collapsibles, watch-list hierarchy
  WatchlistStore.java      persisted watch-later list (JSON file), unit-tested
  PlayerActivity.java      ExoPlayer wrapper, accent focus on player buttons only, single
                           compact options dialog, keep-screen-on
  CrashLogger.java         black crash screen + file log
app/src/main/res/drawable*/ → brand assets + vector icons (in-app marks use transparent
  `logo_transparent.png`, fa locale overrides in `drawable-fa-nodpi/`); res/font → Vazirmatn
app/src/test/ → JVM unit tests (JUnit 4): UiTest, DisplayTextTest, CatalogRepositoryTest,
  CatalogCacheTest, WatchlistStoreTest — run in CI via `testDebugUnitTest`
```

## Design system — `Ui.java`

The whole redesign lives here; **new screens should use these factories, not raw views.**

- **Design space: 960dp wide.** `Ui(context)` computes `scale = clamp(screenWidthDp/960, 0.75, 2.4)`;
  every dimension is `dp(value)` = value * scale. Verified identical at 320dpi (1 dp = 1 px @1080p
  ratio) and 160dpi (scale pegged at max).
- Palette + tokens for spacing/radii/type; focus ring style.
- Factories: poster cards (focus ring, no resize + hi-res image upgrade), pills, ghost/primary
  buttons, centered icon buttons, chips, horizontal rails with focus auto-reveal (`reveal()` —
  direction-agnostic, formula verified against AOSP), wrapping grids (`weight-1` rows + `padRow()` to stop
  the last row stretching), depth-aware `expandable()`.
- Focus never resizes anything (user vetoed the zoom: focused views overflowed their frames).
  Focus = color/border/bold only. Same for `posterCard` and `iconButton`.
- Layout follows the locale (RTL fa / LTR en); rails clamp visibility both ways. Don't
  "fix" direction math unless you re-derive it.

## Screen behaviors (so you don't regress them)

- **Home:** status pill (50% translucent, hidden when empty) + horizontal rails; genre rails
  at the bottom; search box opens listing/search. No hero billboard (removed in v1.13).
- **Details:** header (2:3 portrait poster like list cards + title + pills + gray
  «پخش تریلر» ghost button, red only on focus → YouTube search `DisplayText.trailerQuery()`),
  then two columns: glassy story panel on the right, collapsed «نظرات» + collapsed
  «فهرست پخش» on the left (seasons → categories → quality `expandable()` hierarchy;
  3-column episode grid, «تماشا رایگان» for direct links). `expandable()` wires focus
  explicitly (summary ↔ first child) so the remote never skips stream buttons.
  `DisplayText.cleanTitle()` strips noisy site suffixes.
  All links stream in the player — there is intentionally **no download feature** (Android TV);
  UI wording is تماشا/پخش everywhere, site «دانلود…» labels are rewritten to تماشا at display time.
- **Player:** opens from a link, black shutter, buffering indicator, styled error card,
  `setKeepScreenOn(true)` so boxes (Mi Box) never drop to the screensaver mid-play.
  Every controller button highlights red on focus (`markFocusable()` covers button views
  only, never containers, and replaces stock backgrounds so no white survives;
  rewind/forward are wired explicitly by id, all stepping 10s);
  CC opens one compact glassy playback-options dialog (subtitles + Off, audio, quality —
  sections only when meaningful), all options red-on-focus with apply-on-select; the gear
  button is hidden.
- **Watchlist:** «علاقه‌مندی‌ها» top-bar button opens the saved grid; toggle beside the
  trailer button on details; long-press OK on a card offers delete (`WatchlistStore`).
- **Updates:** `checkAppUpdate(true)` runs once in `MainActivity.onCreate` (silent unless an
  update is found); the update button calls `checkAppUpdate(false)`.

## Live-site scraping contract (fragile by nature)

- Home: `section.list-section`, heading `p.fsz-22` (fallback `h2/h3`), "view all" `a.btn-toPrimary`.
- Featured slider id `#suggested-movies` — **may be absent**; code must keep working without it.
- Home card images arrive lazily via `data-splide-lazy` (`…-180x280.jpg` thumbs); details key art
  is `#post-intro img.coverimg` (2048×1152 landscape), details poster 360×480.
- Genres: accumulated into `genreSink` while parsing; split into `movieCategories`/`seriesCategories`.
- `quickSearch()` parses direct fields, then falls back to the site's `_formatted` object
  (the site sometimes returns `_db_error` + formatted-only hits) — see
  `CatalogRepository.parseQuickSearch()` + `CatalogRepositoryTest`.
- Keep parsing logic in package-private/static helpers so unit tests can reach it;
  Android-dependent code (views, Glide, ExoPlayer) stays out of the tested surface.

## Emulator / screenshots — DO NOT RE-ADD

- The screenshot workflow (`.github/workflows/ui-screenshots.yml` + script) was **deleted at the
  user's explicit order** (a full pass cost ~7+ min of runner time). Never re-add emulator CI.
- Visual reference = committed `screenshot.png` / `screenshot-details.png` + `tv-preview.html`.
  They are refreshed manually from a real install when the UI changes — they may lag the code
  slightly after wording-only changes.

## Versioning & release

- Bump `versionCode`/`versionName` in `app/build.gradle`, mirror in `version.json`, commit.
- Merge to `main`, push, then tag: `git tag vX.Y && git push origin vX.Y` — `release-apk.yml`
  builds and publishes the GitHub release from the tag.
- `version.json` (repo root) is what the installed app checks for updates; it points at
  `releases/latest/download/neftlix-android-tv.apk`. Bump it only when releasing.
- Current: 23 / 1.22 on `main` (v1.22 tag released).
- Docs: `README.md` (English, features/design/screenshots), `tv-preview.html` (static preview
  using committed screenshots).

## Current state (v1.22 released, Oct 2026)

- v1.22 on `main` (versionCode 23): instant watchlist icon tint (icon-wrapper
  invalidation fix), translucent top bars, compact transparent About dialog with
  white idle buttons.
- v1.21 on `main` (versionCode 22): About dialog with red border, 0.7x width, roomier
  padding, «بستن»/«باز کردن سایت» side by side bottom-center.
- v1.20 on `main` (versionCode 21): watchlist toggle via red bookmark icon (red-bordered
  focus), opaque on-screen keyboard, top bars elevated above zoomed cards, playlist
  auto-scroll-to-bottom + single-link direct play + «پخش مستقیم» wording, min-duration
  splash + window background, dropped redundant «صفحه اصلی» button.
- v1.19 on `main` (versionCode 20): on-screen Persian/English keyboard (`SearchPad` +
  `SearchKeys`, unit-tested) replacing the system keyboard, card focus zoom (1.15x),
  edge-anchored rail reveal with first-card vertical entry, player fwd/rew white-tint fix.
- v1.17 on `main` (versionCode 18): stock button backgrounds replaced at wire time (no white),
  glassy compact options dialog, watchlist («علاقه‌مندی‌ها») with long-press delete,
  Vazirmatn everywhere, 10s seek steps.
- In-app updater reads `version.json` from `main` via raw.githubusercontent.
- Screenshot workflow permanently removed — do not re-add emulator CI.
- Committed `screenshot.png` / `screenshot-details.png` predate the تماشا wording;
  refresh them from a real install next time the UI changes visually.
