# Neftlix

This PHP script reads the public catalog pages on `myf2mi.top` and writes a JSON index containing each section's items, cover images, and item-page URLs. It can also fetch one item page and return its listed download URLs as JSON. It does not download images or media files.

## Requirements

- PHP 8.1 or newer
- PHP extensions: `curl`, `dom`, and `json`

## Crawl the catalog

From this directory, run:

```sh
php scraper.php crawl
```

The output is `catalog.json`. A starter snapshot is included; running the command refreshes it. By default, the script indexes all homepage carousels and the first 3 pages of both the Movies and Series archives. You can pass a starting homepage URL without `www` or a scheme; crawl requests follow up to 5 HTTP(S) redirects, including cross-host redirects. Archive pagination stays on the redirected page's origin.

```sh
php scraper.php crawl --url=myf2mi.top --pages=3
```

To crawl more archive pages, set the per-archive page limit:

```sh
php scraper.php crawl --pages=10 --output=public/catalog.json --delay=2
```

Use `--pages=all` to follow archive pagination until there are no more pages. This can make many requests; the default delay is 1 second between requests. The script also deduplicates items within each archive section.

The JSON structure is:

```json
{
  "source": "https://www.myf2mi.top",
  "generated_at": "...",
  "sections": [
    {
      "name": "Movies",
      "type": "archive",
      "url": "https://www.myf2mi.top/movies/",
      "pages_crawled": 3,
      "items": [
        {
          "title": "Example title",
          "page_title": "...",
          "cover_image": "https://.../cover.jpg",
          "url": "https://www.myf2mi.top/.../"
        }
      ]
    }
  ]
}
```

## Get an item's listed download URLs

CLI:

```sh
php scraper.php item 'https://www.myf2mi.top/87822/matchbox-the-movie-2026/'
```

Or, after placing `scraper.php` on a PHP-enabled web server:

```text
/scraper.php?item=https%3A%2F%2Fwww.myf2mi.top%2F87822%2Fmatchbox-the-movie-2026%2F
```

The item response includes `download_groups`, nested to match the page hierarchy: season → download category/language → quality → episode/download links. The flat `download_links` list is also included for compatibility. Link entries retain quality, encoder, season, and labels when available; player/online links are excluded. Item lookup accepts HTTP or HTTPS URLs (including bare domains and site-relative paths) and follows up to 5 redirects, including cross-host redirects. For safety, web requests reject localhost, private/reserved IPs, and nonstandard ports. Crawling can start at any public HTTP(S) homepage; archive pagination stays on the redirected page's origin.

```json
{
  "type": "season",
  "title": "Season 1",
  "children": [
    {
      "type": "download-list",
      "title": "Persian subtitles",
      "children": [
        {
          "type": "quality",
          "quality": "WEB-DL 1080p",
          "encoder": "PSA",
          "children": [
            { "type": "download", "label": "Episode 01", "url": "https://..." }
          ]
        }
      ]
    }
  ]
}
```

## Browse the simple mirror

Refresh `catalog.json`, then start PHP's built-in server from this directory:

```sh
php scraper.php crawl
php -S 127.0.0.1:8000
```

Open `http://127.0.0.1:8000/`. The page has search, all items, homepage sections/featured lists, movie and series archives, and movie/series genre menus. Genre and archive listings load their current first page when opened; item pages display download groups in the site's parent/child order.

## Android TV app (RTL Persian, remote-friendly, internal player)

Native Android TV app in `app/`: opens with a fresh catalog fetch, Persian RTL home with
featured/sections, movie + series genre rows, archive lists with pagination, title search
(server search with local fallback), series/movie details with collapsed download groups
(season → category → quality → episode links), and an internal ExoPlayer (Media3) player.

Remote: D-pad navigates cards/buttons, OK expands groups or opens details/player, Back
returns to the catalog. Target is sideload-friendly (`minSdk 21`, debug APK) for
Xiaomi Mi Box 4K.

Build the installable APK with GitHub Actions (no local Android SDK needed):

1. Push this repo to GitHub.
2. Actions → “Build Android TV APK” → Run (also runs on pushes touching `app/`).
3. Download artifact `neftlix-tv-apk` → `app-debug.apk`.
4. Sideload to Mi Box (USB stick, `adb install`, or a file-manager app) and open it.

## Tests

Run parser tests without making network requests:

```sh
php tests/parser_test.php
```
