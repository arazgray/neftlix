# AGENTS.md — Neftlix Android TV

Guide for AI agents working on this repo. Read this before touching anything.

## What this is

Neftlix — a **Persian (RTL) Android TV** client (Java 17, Gradle, minSdk 21 / target 34 /
compileSdk 35) that scrapes the catalog off `https://www.myf2mi.top/` with Jsoup and plays
video with media3 ExoPlayer. D-pad remote driven. All UI is built **programmatically in Java —
there are no XML layouts**. Strings are Persian; keep the RTL/fa tone in user-visible text.

Repo: `github.com/arazgray/neftlix` (public). App id: `com.arazgray.neftlix`.

## Golden rules

1. **Do not break behavior.** No data/network/parsing regressions. When in doubt, view-layer
   changes only. `CatalogRepository.java` and `Models.java` are the data layer — treat as frozen
   unless the task is explicitly about them.
2. **CI is the only real verification.** There is usually no local JDK/Android SDK here.
   Push a branch and let GitHub Actions compile. A green `Build Neftlix TV APK` run ≈ 1 min.
3. **Visually verify UI work** with the screenshot workflow (see below). Compile-green is not
   enough for a redesign.
4. `version.json` is the update-check/publish manifest — **do not bump it unless releasing**;
   `app/build.gradle` is where version bumps go during normal work.
5. Keep Persian comments/style of surrounding code; commits are short, terse, often with a
   scope prefix like `v1.11: …` or `Hero fallback …`.

## Environment / toolchain

- Workstation: Linux, git + `gh` CLI authenticated as `arazgray`. Repo is public, so Actions
  runs with KVM-capable runners.
- No local Xcode/JDK/Android SDK — **do not attempt local builds**; use CI.
- Useful: `gh run list --workflow android-tv-apk.yml --limit 5`, `gh run watch <id>`,
  `gh run download <id> -n <artifact> -D dir`.

## Workflows cheat-sheet

| Workflow | Trigger | Purpose |
|---|---|---|
| `android-tv-apk.yml` | push touching `app/**`, gradle files, itself; manual | compiles debug APK (~1 min) |
| `release-apk.yml` | tag `v*` | builds + publishes release APK to GitHub Releases |
| `ui-screenshots.yml` | push to `ui-redesign` (temporary) + manual | boots TV emulator, captures screenshots |

Commands:

```bash
# compile check
git push origin <branch> && gh run watch $(gh run list -L1 --workflow android-tv-apk.yml --json databaseId -q '.[0].databaseId')

# screenshots (choose the branch with the UI under test)
gh workflow run ui-screenshots.yml --ref <branch>
gh run list -L1 --workflow ui-screenshots.yml
gh run watch <id>
gh run download <id> -n ui-screenshots -D /tmp/opencode/shots-new   # downloads are slow (~5 min): long timeouts
```

Signing: `neftlix-release.keystore` is restored from secret `NEFTLIX_KEYSTORE_BASE64` (same
key for debug+release so updates install over previous installs). Env vars:
`NEFTLIX_KEYSTORE_FILE/PASSWORD`, `NEFTLIX_KEY_ALIAS/PASSWORD`.

## Architecture map

```
app/src/main/java/com/arazgray/neftlix/
  CatalogRepository.java   data layer: Jsoup scraping (home, listings, search, details, episodes/downloads)
  Models.java              POJOs: CatalogData/BrowseSection/BrowseItem/GenreCategory/DetailsInfo/DownloadGroup...
  Ui.java                  design system: scale, palette, widget factories (see below)
  MainActivity.java        home: hero billboard + rails/grid + search + genres
  DetailsActivity.java     details: title/key-art, buttons, synopsis, download hierarchy
  PlayerActivity.java      ExoPlayer wrapper, lightly themed
  CrashLogger.java         black crash screen + file log
app/src/main/res/drawable*/ → brand assets + vector icons; res/font → Vazirmatn
```

## Design system — `Ui.java`

The whole redesign lives here; **new screens should use these factories, not raw views.**

- **Design space: 960dp wide.** `Ui(context)` computes `scale = clamp(screenWidthDp/960, 0.75, 2.4)`;
  every dimension is `dp(value)` = value * scale. Verified identical at 320dpi (1 dp = 1 px @1080p
  ratio) and 160dpi (scale pegged at max).
- Palette + tokens for spacing/radii/type; focus ring style.
- Factories: poster cards (focus ring + scale-up + hi-res image upgrade), pills, ghost/primary
  buttons, chips, horizontal rails with focus auto-reveal (`reveal()` — RTL-correct, formula
  verified against AOSP), wrapping grids (`weight-1` rows + `padRow()` to stop the last row
  stretching), depth-aware `expandable()`.
- RTL: everything mirrors automatically; rails start scrollX at `max` (RTL origin). Don't
  "fix" direction math unless you re-derive it.

## Screen behaviors (so you don't regress them)

- **Hero billboard:** uses the site's «ویژهها» slider (`#suggested-movies`) when present —
  landscape key art. The slider **comes and goes on the live site**; fallback = first section's
  daily pick («پیشنهاد امروز») and an async fetch of that item's wide key art from its details
  page (`heroSection()`, `showHeroBackdrop`, `heroArt` cache, `hasLandscapeArt`). In CI
  screenshots the fallback path is what usually renders — that's expected.
- **Home:** hero + horizontal rails; genre rails at the bottom; search box opens listing/search.
- **Details:** seasons → categories → quality `expandable()` hierarchy; 3-column episode grid;
  `cleanTitle()` strips noisy site suffixes. Comments section at the bottom.
- **Player:** opens from a link, black shutter, buffering indicator, styled error card.

## Live-site scraping contract (fragile by nature)

- Home: `section.list-section`, heading `p.fsz-22` (fallback `h2/h3`), "view all" `a.btn-toPrimary`.
- Featured slider id `#suggested-movies` — **may be absent**; code must keep working without it.
- Home card images arrive lazily via `data-splide-lazy` (`…-180x280.jpg` thumbs); details key art
  is `#post-intro img.coverimg` (2048×1152 landscape), details poster 360×480.
- Genres: accumulated into `genreSink` while parsing; split into `movieCategories`/`seriesCategories`.
- **Known bug/backlog (not yet fixed):** `CatalogRepository.quickSearch()` expects JSON fields
  (`title`, `url`, `image`); the site's `/quick-search` sometimes returns objects with `_db_error`
  + `_formatted` fields instead, so app falls back to page search. Fix = parse `_formatted`
  (`_formatted.title`, …) as fallback. Only attempt if the task asks; test on-device via CI.

## Emulator / screenshot gotchas (hard-won)

- `reactivecircus/android-emulator-runner` reports "This user doesn't have permissions to use
  KVM" → pre-step `sudo chmod 666 /dev/kvm` restores acceleration (boot ~1–2 min vs ~5+ min and
  broken services with TCG).
- Emulator boots in **touch mode**; programmatic `requestFocus()` silently fails until a D-pad
  event — script sends `KEYCODE_DPAD_DOWN` on the launcher before `am start`.
- Script waits for `sys.boot_completed` + `package/input/settings/window/activity` services,
  retries `adb install`, waits for `mCurrentFocus` to be the app, and dumps logcat at the end.
- Captures two passes: `tv320-*` (profile density) and `hdpi160-*` (density 160 → scale pegged),
  to prove the scale system.
- Emulator config: API 30, `profile: tv_1080p`, `enable-hw-keyboard: true` (else `adb shell
  input text` misbehaves), swiftshader GPU.

## Versioning & release

- Bump `versionCode`/`versionName` in `app/build.gradle` (branch `ui-redesign` carries 12 / 1.11;
  `main` is at `fe1f7d4` = v1.10).
- `version.json` (repo root) is what the installed app checks for updates; it points at
  `releases/latest/download/neftlix-android-tv.apk`. It is **publishing state** — only the user
  decides when to bump it and push a `v*` tag.
- Docs: `README.md` (English, features/design/screenshots), `tv-preview.html` (static preview
  using committed screenshots).

## Current state (last updated while merging ui-redesign, Oct 2026)

- Branch `ui-redesign` holds the full redesign (design system + rewritten home/details + light
  player polish + screenshot workflow). `main` still at `fe1f7d4` (v1.10). README + `tv-preview.html`
  rewrites and screenshots were pending commit at the time of writing.
- Untracked: `logo-transparent.png` (unreferenced — decide whether to keep/delete).
- `ui-screenshots.yml` is marked **temporary**; once the design is merged it should become
  `workflow_dispatch`-only (or be deleted) per README.
