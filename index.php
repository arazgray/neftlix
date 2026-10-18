<?php
declare(strict_types=1);

require_once __DIR__ . '/scraper.php';

function e(mixed $value): string
{
    return htmlspecialchars((string) $value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function queryInt(string $name, int $default = 0): int
{
    $value = $_GET[$name] ?? null;
    if (!is_scalar($value) || !preg_match('/^\d+$/', (string) $value)) {
        return $default;
    }
    return max(0, (int) $value);
}

function containsText(string $haystack, string $needle): bool
{
    if (function_exists('mb_stripos')) {
        return mb_stripos($haystack, $needle, 0, 'UTF-8') !== false;
    }
    return stripos($haystack, $needle) !== false;
}

function filterItems(array $items, string $query): array
{
    if ($query === '') {
        return $items;
    }
    return array_values(array_filter($items, static function (array $item) use ($query): bool {
        return containsText(
            (string) ($item['title'] ?? '') . ' ' . (string) ($item['page_title'] ?? ''),
            $query
        );
    }));
}

function itemType(array $item, string $sectionName = ''): string
{
    $url = strtolower((string) ($item['url'] ?? ''));
    $label = strtolower($sectionName . ' ' . (string) ($item['page_title'] ?? ''));
    return str_contains($url, '/series/') || str_contains($label, 'series') || str_contains($label, 'سریال')
        ? 'Series'
        : 'Movie';
}

function allCatalogItems(array $sections): array
{
    $itemsByUrl = [];
    foreach ($sections as $section) {
        $sectionName = (string) ($section['name'] ?? '');
        foreach (($section['items'] ?? []) as $item) {
            if (!is_array($item) || empty($item['url'])) {
                continue;
            }
            $url = (string) $item['url'];
            if (!isset($itemsByUrl[$url])) {
                $item['content_type'] = itemType($item, $sectionName);
                $item['section_name'] = $sectionName;
                $itemsByUrl[$url] = $item;
            } elseif (itemType($item, $sectionName) === 'Series') {
                $itemsByUrl[$url]['content_type'] = 'Series';
            }
        }
    }
    return array_values($itemsByUrl);
}

function pageUrlFor(string $listingUrl, int $page): string
{
    $listingUrl = assertHttpUrl($listingUrl);
    if ($page <= 1) {
        return $listingUrl;
    }

    $parts = parse_url($listingUrl);
    if ($parts === false) {
        return $listingUrl;
    }
    $path = preg_replace('~/page/\d+/?$~', '/', $parts['path'] ?? '/') ?? ($parts['path'] ?? '/');
    $path = rtrim($path, '/') . '/page/' . $page . '/';
    $query = isset($parts['query']) ? '?' . $parts['query'] : '';
    return urlOrigin($listingUrl) . $path . $query;
}

function fetchListingPage(string $listingUrl, int $page): array
{
    $response = fetchHtml(pageUrlFor($listingUrl, $page), true, true);
    [, $xpath] = parseHtml($response['html']);
    return [
        'items' => extractArticleItems($xpath, "//main//article[" . classQuery('entry') . "]", $response['url']),
        'has_next' => nextPageUrl($xpath, $response['url']) !== null,
        'url' => $response['url'],
    ];
}

function renderItemCards(array $items): void
{
    if ($items === []) {
        echo '<p class="notice">No items found.</p>';
        return;
    }

    echo '<div class="items">';
    foreach ($items as $item) {
        if (!is_array($item) || empty($item['url'])) {
            continue;
        }
        $title = (string) ($item['title'] ?? $item['page_title'] ?? 'Untitled');
        $cover = (string) ($item['cover_image'] ?? '');
        $type = (string) ($item['content_type'] ?? itemType($item, (string) ($item['section_name'] ?? '')));
        echo '<article class="item-card">';
        echo '<a class="item-link" href="?item=' . rawurlencode((string) $item['url']) . '">';
        if ($cover !== '') {
            echo '<img loading="lazy" src="' . e($cover) . '" alt="' . e($title) . '">';
        } else {
            echo '<span class="no-cover">No cover</span>';
        }
        echo '<span class="item-kind">' . e($type) . '</span>';
        echo '<strong>' . e($title) . '</strong>';
        echo '</a></article>';
    }
    echo '</div>';
}

function renderDownloadGroups(array $groups): void
{
    if ($groups === []) {
        echo '<p class="notice">No download options are listed on this page.</p>';
        return;
    }

    foreach ($groups as $group) {
        $type = (string) ($group['type'] ?? 'group');
        if ($type === 'season') {
            echo '<details class="download-season"><summary><strong>' . e($group['title'] ?? 'Season') . '</strong>';
            if (!empty($group['status'])) {
                echo ' <span class="muted">' . e($group['status']) . '</span>';
            }
            echo '</summary>';
            renderDownloadGroups($group['children'] ?? []);
            echo '</details>';
            continue;
        }

        if ($type === 'download-list') {
            echo '<details class="download-category"><summary><strong>' . e($group['title'] ?? 'Downloads') . '</strong>';
            if (!empty($group['classes'])) {
                echo ' <small class="muted">' . e(implode(', ', $group['classes'])) . '</small>';
            }
            echo '</summary>';
            renderDownloadGroups($group['children'] ?? []);
            echo '</details>';
            continue;
        }

        if ($type === 'quality') {
            $quality = trim((string) ($group['quality'] ?? ''));
            $encoder = trim((string) ($group['encoder'] ?? ''));
            echo '<details class="quality-option"><summary><strong>' . e($quality !== '' ? $quality : 'Download') . '</strong>';
            if ($encoder !== '') {
                echo ' <span class="muted">' . e($encoder) . '</span>';
            }
            echo '</summary><ul>';
            foreach (($group['children'] ?? []) as $link) {
                if (($link['type'] ?? '') !== 'download') {
                    continue;
                }
                echo '<li><a target="_blank" rel="noopener noreferrer" href="' . e($link['url'] ?? '') . '">' . e($link['label'] ?? 'Download') . '</a></li>';
            }
            echo '</ul></details>';
        }
    }
}

function renderNavigation(array $catalog, ?int $activeSection, ?int $activeCategory): void
{
    echo '<nav class="navigation">';
    echo '<a href="index.php">Home</a> <a href="?view=all">All items</a>';

    echo '<details><summary>Sections</summary><div class="nav-list">';
    foreach (($catalog['sections'] ?? []) as $index => $section) {
        $active = $activeSection === $index ? ' aria-current="page"' : '';
        echo '<a' . $active . ' href="?section=' . (int) $index . '">' . e($section['name'] ?? 'Section') . '</a>';
    }
    echo '</div></details>';

    foreach (['movie' => 'Movie categories', 'series' => 'Series categories'] as $type => $label) {
        echo '<details><summary>' . e($label) . '</summary><div class="nav-list">';
        foreach (($catalog['categories'] ?? []) as $index => $category) {
            if (($category['type'] ?? '') !== $type) {
                continue;
            }
            $active = $activeCategory === $index ? ' aria-current="page"' : '';
            $count = isset($category['item_count']) ? ' (' . (int) $category['item_count'] . ')' : '';
            echo '<a' . $active . ' href="?category=' . (int) $index . '">' . e($category['name'] ?? 'Category') . e($count) . '</a>';
        }
        echo '</div></details>';
    }
    echo '</nav>';
}

function renderHeader(array $catalog, string $query, ?int $activeSection, ?int $activeCategory, array $context = []): void
{
    echo '<!doctype html><html lang="en"><head><meta charset="utf-8">';
    echo '<meta name="viewport" content="width=device-width, initial-scale=1">';
    echo '<title>Neftlix mirror</title><style>';
    echo 'body{font:16px/1.5 system-ui,sans-serif;margin:0;color:#222;background:#f5f5f5}';
    echo 'header,main,.navigation{padding:1rem;max-width:1200px;margin:auto}';
    echo 'header{display:flex;gap:1rem;align-items:center;justify-content:space-between;flex-wrap:wrap}';
    echo 'header h1{font-size:1.4rem;margin:0}a{color:#1457a6}';
    echo '.search{display:flex;gap:.4rem;flex-wrap:wrap}.search input{min-width:15rem;padding:.5rem}button{padding:.5rem .8rem;cursor:pointer}';
    echo '.navigation{display:flex;gap:.7rem;align-items:flex-start;flex-wrap:wrap;background:#fff;border-block:1px solid #ddd}';
    echo '.navigation details{position:relative}.navigation summary{cursor:pointer}.nav-list{display:grid;gap:.25rem;max-height:55vh;overflow:auto;min-width:14rem;padding:.6rem;background:#fff;border:1px solid #ddd;position:absolute;z-index:5}';
    echo 'main{background:#fff;min-height:60vh}.items{display:grid;grid-template-columns:repeat(auto-fill,minmax(145px,1fr));gap:1rem}';
    echo '.item-card{border:1px solid #ddd;padding:.5rem;background:#fff}.item-link{display:flex;flex-direction:column;gap:.3rem;text-decoration:none;color:inherit}';
    echo '.item-card img{width:100%;height:220px;object-fit:cover;background:#eee}.no-cover{height:220px;display:grid;place-items:center;background:#eee;color:#666}';
    echo '.item-kind,.muted{color:#666;font-size:.9rem}.item-kind{font-size:.8rem}.notice{padding:.7rem;background:#fff6d8;border:1px solid #ebdcaa}';
    echo '.download-season,.download-category{border:1px solid #ddd;padding:.7rem;margin:1rem 0}.download-season>summary,.download-category>summary,.quality-option>summary{cursor:pointer}';
    echo '.quality-option{padding:.5rem;border-top:1px solid #eee}.quality-option ul{margin:.3rem 0;padding-left:1.5rem}.pagination{display:flex;gap:1rem;margin:1rem 0}';
    echo '.detail{display:grid;grid-template-columns:minmax(150px,240px) 1fr;gap:1.2rem}.detail img{max-width:100%;height:auto}@media(max-width:600px){.detail{grid-template-columns:1fr}}';
    echo '</style></head><body><header><h1><a href="index.php">Neftlix mirror</a></h1><form class="search" method="get" action="index.php">';
    foreach ($context as $name => $value) {
        echo '<input type="hidden" name="' . e($name) . '" value="' . e($value) . '">';
    }
    echo '<input type="search" name="q" placeholder="Search titles" value="' . e($query) . '"><button type="submit">Search</button></form></header>';
    renderNavigation($catalog, $activeSection, $activeCategory);
    echo '<main>';
}

function renderFooter(): void
{
    echo '</main></body></html>';
}

$catalogPath = __DIR__ . '/catalog.json';
$catalog = is_file($catalogPath) ? json_decode((string) file_get_contents($catalogPath), true) : null;
if (!is_array($catalog) || !is_array($catalog['sections'] ?? null)) {
    http_response_code(503);
    renderHeader(['sections' => [], 'categories' => []], '', null, null);
    echo '<h2>Catalog unavailable</h2><p class="notice">Create or refresh catalog.json with <code>php scraper.php crawl</code>.</p>';
    renderFooter();
    exit;
}

$query = isset($_GET['q']) && is_string($_GET['q']) ? trim(substr($_GET['q'], 0, 120)) : '';
$activeSection = isset($_GET['section']) ? queryInt('section', -1) : null;
$activeCategory = isset($_GET['category']) ? queryInt('category', -1) : null;
$page = min(1000, max(1, queryInt('page', 1)));

if (isset($_GET['item']) && is_string($_GET['item'])) {
    renderHeader($catalog, $query, $activeSection, $activeCategory);
    try {
        $itemUrl = assertHttpUrl(substr($_GET['item'], 0, 2048));
        $response = fetchHtml($itemUrl, true, true);
        $item = itemDetails($response['html'], $response['url']);
        echo '<p><a href="javascript:history.back()">&larr; Back</a> · <a target="_blank" rel="noopener noreferrer" href="' . e($item['url']) . '">Original page</a></p>';
        echo '<div class="detail">';
        if (!empty($item['cover_image'])) {
            echo '<img src="' . e($item['cover_image']) . '" alt="' . e($item['title'] ?? '') . '">';
        }
        echo '<div><h2>' . e($item['title'] ?? 'Item') . '</h2><p class="muted">' . (int) $item['download_group_count'] . ' download groups · ' . (int) $item['download_link_count'] . ' links</p>';
        renderDownloadGroups($item['download_groups']);
        echo '</div></div>';
    } catch (Throwable $error) {
        echo '<h2>Could not load item</h2><p class="notice">' . e($error->getMessage()) . '</p>';
    }
    renderFooter();
    exit;
}

$context = [];
if ($activeCategory !== null) {
    $context['category'] = $activeCategory;
    $context['page'] = $page;
} elseif ($activeSection !== null) {
    $context['section'] = $activeSection;
    $context['page'] = $page;
} elseif (($_GET['view'] ?? '') === 'all') {
    $context['view'] = 'all';
}
renderHeader($catalog, $query, $activeSection, $activeCategory, $context);

if ($activeCategory !== null && isset($catalog['categories'][$activeCategory])) {
    $category = $catalog['categories'][$activeCategory];
    $title = (string) ($category['name'] ?? 'Category');
    echo '<h2>' . e($title) . ' <span class="muted">(' . e($category['type'] ?? '') . ')</span></h2>';
    try {
        $listing = fetchListingPage((string) $category['url'], $page);
        $items = filterItems($listing['items'], $query);
        renderItemCards($items);
        echo '<div class="pagination">';
        if ($page > 1) {
            echo '<a href="?category=' . $activeCategory . '&amp;page=' . ($page - 1) . '">Previous</a>';
        }
        if ($listing['has_next']) {
            echo '<a href="?category=' . $activeCategory . '&amp;page=' . ($page + 1) . '">Next</a>';
        }
        echo '</div>';
    } catch (Throwable $error) {
        echo '<p class="notice">Could not load this category: ' . e($error->getMessage()) . '</p>';
    }
} elseif ($activeSection !== null && isset($catalog['sections'][$activeSection])) {
    $section = $catalog['sections'][$activeSection];
    echo '<h2>' . e($section['name'] ?? 'Section') . '</h2>';
    if (($section['type'] ?? '') === 'archive') {
        try {
            $listing = fetchListingPage((string) $section['url'], $page);
            $items = filterItems($listing['items'], $query);
            renderItemCards($items);
            echo '<div class="pagination">';
            if ($page > 1) {
                echo '<a href="?section=' . $activeSection . '&amp;page=' . ($page - 1) . '">Previous</a>';
            }
            if ($listing['has_next']) {
                echo '<a href="?section=' . $activeSection . '&amp;page=' . ($page + 1) . '">Next</a>';
            }
            echo '</div>';
        } catch (Throwable $error) {
            echo '<p class="notice">Could not refresh this listing: ' . e($error->getMessage()) . '</p>';
            renderItemCards(filterItems(allCatalogItems([$section]), $query));
        }
    } else {
        $items = array_map(static function (array $item) use ($section): array {
            $item['content_type'] = itemType($item, (string) ($section['name'] ?? ''));
            return $item;
        }, $section['items'] ?? []);
        renderItemCards(filterItems($items, $query));
    }
} elseif (($_GET['view'] ?? '') === 'all' || $query !== '') {
    echo '<h2>' . ($query !== '' ? 'Search results' : 'All catalog items') . '</h2>';
    renderItemCards(filterItems(allCatalogItems($catalog['sections']), $query));
} else {
    echo '<h2>Featured and sections</h2>';
    $homepageSections = array_values(array_filter(
        $catalog['sections'],
        static fn (array $section): bool => ($section['type'] ?? '') === 'homepage'
    ));
    foreach ($homepageSections as $section) {
        echo '<section><h3><a href="?section=' . (int) array_search($section, $catalog['sections'], true) . '">' . e($section['name'] ?? 'Section') . '</a></h3>';
        $items = array_map(static function (array $item) use ($section): array {
            $item['content_type'] = itemType($item, (string) ($section['name'] ?? ''));
            return $item;
        }, $section['items'] ?? []);
        renderItemCards($items);
        echo '</section>';
    }
}

renderFooter();
