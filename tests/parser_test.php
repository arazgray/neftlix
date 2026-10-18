<?php
declare(strict_types=1);

require_once __DIR__ . '/../scraper.php';

function assertSameValue(mixed $expected, mixed $actual, string $message): void
{
    if ($expected !== $actual) {
        throw new RuntimeException($message . '\nExpected: ' . var_export($expected, true) . '\nActual: ' . var_export($actual, true));
    }
}

function assertTrue(bool $condition, string $message): void
{
    if (!$condition) {
        throw new RuntimeException($message);
    }
}

$homeHtml = <<<'HTML'
<!doctype html><html><body>
<section id="section-a" class="list-section">
  <p class="fsz-22">Recently added</p>
  <a class="btn-toPrimary" href="/movies/">All movies</a>
  <div class="posts">
    <article class="entry"><figure class="entry-cover">
      <img src="data:image/gif;base64,abc" data-splide-lazy="/covers/movie-one.jpg">
    </figure><a class="stretched-link" rel="bookmark" href="/123/movie-one/" title="Movie One page">
      <h2 class="entry-title">Movie One</h2></a></article>
  </div>
</section>
<section id="section-b" class="list-section">
  <h2 class="fsz-22">Series section</h2>
  <div class="posts"><article class="entry"><figure class="entry-cover">
    <img src="/covers/series-one.jpg"></figure>
    <a class="stretched-link" href="/series/series-one/"><h2 class="entry-title">Series One</h2></a>
  </article></div>
</section>
</body></html>
HTML;

$sections = extractHomeSections($homeHtml, SITE_ORIGIN . '/');
assertSameValue(2, count($sections), 'Homepage sections should remain separate.');
assertSameValue('Recently added', $sections[0]['name'], 'Homepage section title should be extracted.');
assertSameValue(1, count($sections[0]['items']), 'Each homepage section should include only its own items.');
assertSameValue('Movie One', $sections[0]['items'][0]['title'], 'Item title should be extracted.');
assertSameValue(SITE_ORIGIN . '/covers/movie-one.jpg', $sections[0]['items'][0]['cover_image'], 'Lazy-loaded cover URL should be preferred to the placeholder.');
assertSameValue(SITE_ORIGIN . '/123/movie-one/', $sections[0]['items'][0]['url'], 'Relative item URL should be resolved.');
assertSameValue(1, count($sections[1]['items']), 'The next section should not receive the first section items.');
$categoryHtml = <<<'HTML'
<div id="genres-movies"><ul><li><a href="/genres/action/?type=movie">Action</a> (123)</li></ul></div>
<div id="genres-series"><ul><li><a href="/genres/drama/?type=series">Drama</a> (45)</li></ul></div>
HTML;
$categories = extractCategories($categoryHtml, SITE_ORIGIN . '/');
assertSameValue(2, count($categories), 'Movie and series genres should be indexed separately.');
assertSameValue('movie', $categories[0]['type'], 'Movie genre type should be preserved.');
assertSameValue(123, $categories[0]['item_count'], 'Category item count should be parsed.');
assertSameValue('series', $categories[1]['type'], 'Series genre type should be preserved.');

$movieHtml = <<<'HTML'
<!doctype html><html><head><title>Movie title</title></head><body>
<section id="post-intro"><figure class="entry-poster"><img src="/covers/poster.jpg"></figure></section>
<div class="download-list hardsub"><p class="title"><span>Persian subtitles</span></p><ul><li>
  <span class="text" dir="ltr">WEB-DL 1080p</span>
  <div><span class="text-muted">انکودر :</span> YIFY</div>
  <a href="https://files.example/movie.mkv" download>دانلود مستقیم</a>
  <a href="https://www.myf2mi.top/movie/?playit=123" class="require-login">پخش آنلاین</a>
  <a href="#" onclick="handleDownloadClick('https://files.example/movie.mkv'); return false;"><img alt="KMPlayer"></a>
</li></ul></div>
</body></html>
HTML;

$movie = itemDetails($movieHtml, SITE_ORIGIN . '/123/movie/');
assertSameValue('Movie title', $movie['title'], 'Item-page title should be extracted.');
assertSameValue(SITE_ORIGIN . '/covers/poster.jpg', $movie['cover_image'], 'Item-page cover should be extracted.');
assertSameValue(1, $movie['download_link_count'], 'Duplicate player/download URLs should be deduplicated and online links skipped.');
assertSameValue('دانلود مستقیم', $movie['download_links'][0]['label'], 'Download anchor label should be preserved.');
assertSameValue('WEB-DL 1080p', $movie['download_links'][0]['quality'], 'Download quality should be extracted.');
assertSameValue('YIFY', $movie['download_links'][0]['encoder'], 'Encoder should be extracted.');
assertSameValue(1, $movie['download_group_count'], 'Movie download sections should be grouped.');
assertSameValue('Persian subtitles', $movie['download_groups'][0]['title'], 'Movie group title should be retained.');
assertSameValue(['hardsub'], $movie['download_groups'][0]['classes'], 'Movie download category class should be retained.');
assertSameValue('WEB-DL 1080p', $movie['download_groups'][0]['children'][0]['quality'], 'Movie quality should be a child of its category.');
assertSameValue(
    'دانلود مستقیم',
    $movie['download_groups'][0]['children'][0]['children'][0]['label'],
    'Movie download links should be nested beneath the quality option.'
);

$seriesHtml = <<<'HTML'
<!doctype html><html><head><title>Series title</title></head><body>
<div class="download-season"><button data-bs-target="#season">Season One</button>
  <div class="download-list hardsub"><p class="title">Subtitled</p><ul><li>
    <span class="text" dir="ltr">WEB-DL 1080p</span><div><span class="text-muted">انکودر :</span> PSA</div>
    <div class="series-downloaditems">
      <div class="d-flex">
        <a href="#" onclick="handleDownloadClick('https://files.example/episode-01.mkv'); return false;"><img alt="KMPlayer"></a>
        <a href="https://files.example/episode-01.mkv">قسمت 01</a>
      </div>
      <label><textarea>https://files.example/episode-01.mkv
https://files.example/episode-02.mkv</textarea></label>
    </div>
  </li></ul></div>
</div></body></html>
HTML;

$series = itemDetails($seriesHtml, SITE_ORIGIN . '/series/series-one/');
assertSameValue(2, $series['download_link_count'], 'Series episode links from anchors and copy-all text should be captured without duplicates.');
assertSameValue('قسمت 01', $series['download_links'][0]['label'], 'Episode label should be inferred from its sibling link.');
assertSameValue('Season One', $series['download_links'][0]['season'], 'Season label should be retained.');
assertSameValue('WEB-DL 1080p', $series['download_links'][0]['quality'], 'Series quality should be extracted.');
assertSameValue('Season One', $series['download_groups'][0]['title'], 'Season should be the top-level parent group.');
assertSameValue('Subtitled', $series['download_groups'][0]['children'][0]['title'], 'Download language/category should be nested under its season.');
assertSameValue(
    'WEB-DL 1080p',
    $series['download_groups'][0]['children'][0]['children'][0]['quality'],
    'Series quality should be nested beneath its category.'
);
assertSameValue(
    2,
    count($series['download_groups'][0]['children'][0]['children'][0]['children']),
    'Series episode links should be nested beneath their quality option.'
);

$blocked = false;
try {
    assertSiteUrl('https://example.org/not-an-item/');
} catch (InvalidArgumentException) {
    $blocked = true;
}
assertTrue($blocked, 'Item lookups must reject URLs outside the target site.');
assertSameValue(
    SITE_ORIGIN . '/series/dark-matter/',
    assertSiteUrl('https://www.myf2mi.top/series/dark-matter/'),
    'Series item URLs should pass validation.'
);
assertSameValue(
    SITE_ORIGIN . '/76906/spider-man-brand-new-day/',
    assertSiteUrl('https://www.myf2mi.top/76906/spider-man-brand-new-day/'),
    'Movie item URLs should pass validation.'
);
assertSameValue(
    'https://www.myf2mi.top/series/dark-matter/',
    assertHttpUrl('www.myf2mi.top/series/dark-matter/'),
    'A URL without a scheme should default to HTTPS.'
);
assertSameValue(
    'https://www.myf2mi.top/series/dark-matter/',
    assertHttpUrl('/series/dark-matter/'),
    'A site-relative item path should be accepted.'
);
assertSameValue(
    'http://media.example.org/file.mkv',
    assertHttpUrl('http://media.example.org/file.mkv'),
    'External HTTP item URLs should be accepted.'
);
assertSameValue(
    'https://example.org:443/path',
    assertHttpUrl('https://example.org:443/path'),
    'Explicit standard ports should be retained.'
);
assertSameValue(
    'http://media.example.org/path',
    absoluteUrl('//media.example.org/path', 'http://example.org/'),
    'Protocol-relative redirects should preserve the current scheme.'
);
assertSameValue(
    'https://example.org/page/2/',
    sameOriginUrl('/page/2/', 'https://example.org/page/1/'),
    'Relative archive pagination should stay on the page origin.'
);
assertSameValue(
    null,
    sameOriginUrl('https://elsewhere.example/page/2/', 'https://example.org/page/1/'),
    'Archive pagination should not crawl links to unrelated origins.'
);
$crawlOptions = parseOptions(['--url=myf2mi.top', '--pages=2']);
assertSameValue('https://myf2mi.top/', $crawlOptions['url'], 'The crawl command should accept scheme-less starting URLs.');
assertSameValue(2, $crawlOptions['pages'], 'The crawl page limit should still be parsed.');

$privateHostBlocked = false;
try {
    publicAddressesForHost('127.0.0.1');
} catch (InvalidArgumentException) {
    $privateHostBlocked = true;
}
assertTrue($privateHostBlocked, 'The web lookup must block private IP destinations.');

fwrite(STDOUT, "Parser tests passed.\n");
