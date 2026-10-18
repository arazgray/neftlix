<?php
declare(strict_types=1);

const SITE_ORIGIN = 'https://www.myf2mi.top';
const SITE_HOSTS = ['www.myf2mi.top', 'myf2mi.top'];
const CRAWLER_USER_AGENT = 'neftlix-catalog-crawler/1.0';
const MAX_RESPONSE_BYTES = 16777216;

/**
 * Turn an HTML URL into an absolute HTTP(S) URL. Relative URLs are resolved
 * against the supplied page URL; javascript:, data:, and fragment URLs are
 * intentionally ignored.
 */
function absoluteUrl(string $value, string $baseUrl): ?string
{
    $value = trim(html_entity_decode($value, ENT_QUOTES | ENT_HTML5, 'UTF-8'));
    if ($value === '' || str_starts_with($value, '#') || preg_match('/^(javascript|data|mailto|tel):/i', $value)) {
        return null;
    }

    $base = parse_url($baseUrl);
    if (!is_array($base) || empty($base['host'])) {
        return null;
    }

    if (str_starts_with($value, '//')) {
        $baseScheme = strtolower(parse_url($baseUrl, PHP_URL_SCHEME) ?: 'https');
        $value = $baseScheme . ':' . $value;
    }

    $parts = parse_url($value);
    if ($parts === false) {
        return null;
    }

    if (!empty($parts['scheme'])) {
        if (
            !in_array(strtolower($parts['scheme']), ['http', 'https'], true) ||
            empty($parts['host']) || isset($parts['user']) || isset($parts['pass'])
        ) {
            return null;
        }
        $scheme = strtolower($parts['scheme']);
        $host = strtolower($parts['host']);
        if (isset($parts['port'])) {
            $host .= ':' . $parts['port'];
        }
        $path = $parts['path'] ?? '/';
        $query = isset($parts['query']) ? '?' . $parts['query'] : '';
        return $scheme . '://' . $host . normalizePath($path) . $query;
    }

    $scheme = strtolower($base['scheme'] ?? 'https');
    $host = strtolower($base['host']);
    if (isset($base['port'])) {
        $host .= ':' . $base['port'];
    }
    $basePath = $base['path'] ?? '/';
    if (isset($parts['host'])) {
        return null;
    }

    if (!isset($parts['path']) || $parts['path'] === '') {
        $path = $basePath;
        $query = isset($parts['query'])
            ? '?' . $parts['query']
            : (isset($base['query']) ? '?' . $base['query'] : '');
    } else {
        $path = str_starts_with($parts['path'], '/')
            ? $parts['path']
            : rtrim(dirname($basePath), '/') . '/' . $parts['path'];
        $query = isset($parts['query']) ? '?' . $parts['query'] : '';
    }

    return $scheme . '://' . $host . normalizePath($path) . $query;
}

function normalizePath(string $path): string
{
    $trailingSlash = str_ends_with($path, '/');
    $segments = [];
    foreach (explode('/', $path) as $segment) {
        if ($segment === '' || $segment === '.') {
            continue;
        }
        if ($segment === '..') {
            array_pop($segments);
            continue;
        }
        $segments[] = $segment;
    }

    $normalized = '/' . implode('/', $segments);
    if ($trailingSlash && $normalized !== '/') {
        $normalized .= '/';
    }
    return $normalized;
}

function normalizeInputUrl(string $url): ?string
{
    $url = trim($url);
    if ($url === '') {
        return null;
    }

    if (str_starts_with($url, '//')) {
        $url = 'https:' . $url;
    } elseif (preg_match('~^[a-z][a-z0-9+.-]*://~i', $url) !== 1) {
        if (str_starts_with($url, '/') || str_starts_with($url, '?')) {
            return absoluteUrl($url, SITE_ORIGIN . '/');
        }

        $firstPart = preg_split('~[/?#]~', $url, 2)[0] ?? '';
        if (str_contains($firstPart, '.') && preg_match('/^[a-z0-9.-]+(?::\d+)?$/i', $firstPart) === 1) {
            $url = 'https://' . $url;
        } else {
            return absoluteUrl($url, SITE_ORIGIN . '/');
        }
    }

    return absoluteUrl($url, SITE_ORIGIN . '/');
}

function assertHttpUrl(string $url): string
{
    $absolute = normalizeInputUrl($url);
    $parts = $absolute !== null ? parse_url($absolute) : false;
    if (
        $absolute === null || $parts === false ||
        !in_array(strtolower($parts['scheme'] ?? ''), ['http', 'https'], true) ||
        empty($parts['host']) || isset($parts['user']) || isset($parts['pass']) ||
        (isset($parts['port']) && !in_array((int) $parts['port'], [80, 443], true))
    ) {
        throw new InvalidArgumentException('Use an absolute HTTP or HTTPS URL (or a path on myf2mi.top).');
    }

    return $absolute;
}

function urlOrigin(string $url): string
{
    $parts = parse_url($url);
    if ($parts === false || empty($parts['scheme']) || empty($parts['host'])) {
        throw new InvalidArgumentException('Could not determine the URL origin.');
    }
    $origin = strtolower($parts['scheme']) . '://' . $parts['host'];
    if (isset($parts['port'])) {
        $origin .= ':' . $parts['port'];
    }
    return $origin;
}

function sameOriginUrl(string $url, string $baseUrl): ?string
{
    $resolved = absoluteUrl($url, $baseUrl);
    if ($resolved === null) {
        return null;
    }
    try {
        $absolute = assertHttpUrl($resolved);
    } catch (InvalidArgumentException) {
        return null;
    }
    $target = parse_url($absolute);
    $base = parse_url($baseUrl);
    if ($target === false || $base === false) {
        return null;
    }

    $targetScheme = strtolower($target['scheme'] ?? '');
    $baseScheme = strtolower($base['scheme'] ?? '');
    $targetPort = (int) ($target['port'] ?? ($targetScheme === 'https' ? 443 : 80));
    $basePort = (int) ($base['port'] ?? ($baseScheme === 'https' ? 443 : 80));
    if (
        $targetScheme !== $baseScheme ||
        strtolower($target['host'] ?? '') !== strtolower($base['host'] ?? '') ||
        $targetPort !== $basePort
    ) {
        return null;
    }
    return $absolute;
}

/** Resolve and validate a public address before web-mode fetches (SSRF guard). */
function publicAddressesForHost(string $host): array
{
    $host = strtolower(rtrim(trim($host, '[]'), '.'));
    if (
        $host === '' || $host === 'localhost' ||
        preg_match('/(?:^|\.)(?:localhost|local|internal|test|invalid)$/i', $host) === 1
    ) {
        throw new InvalidArgumentException('Private and local network hosts are not accepted.');
    }

    if (filter_var($host, FILTER_VALIDATE_IP) !== false) {
        $addresses = [$host];
    } else {
        $records = @dns_get_record($host, DNS_A | DNS_AAAA);
        $addresses = [];
        if (is_array($records)) {
            foreach ($records as $record) {
                if (isset($record['ip'])) {
                    $addresses[] = $record['ip'];
                } elseif (isset($record['ipv6'])) {
                    $addresses[] = $record['ipv6'];
                }
            }
        }
        if ($addresses === false || $addresses === []) {
            $ipv4Addresses = @gethostbynamel($host);
            if (is_array($ipv4Addresses)) {
                $addresses = $ipv4Addresses;
            }
        }
    }

    $addresses = array_values(array_unique($addresses));
    if ($addresses === []) {
        throw new RuntimeException('Could not resolve host: ' . $host);
    }

    foreach ($addresses as $address) {
        if (filter_var($address, FILTER_VALIDATE_IP, FILTER_FLAG_NO_PRIV_RANGE | FILTER_FLAG_NO_RES_RANGE) === false) {
            throw new InvalidArgumentException('Private or reserved IP addresses are not accepted.');
        }
    }

    return $addresses;
}

function assertSiteUrl(string $url): string
{
    $absolute = assertHttpUrl($url);
    $parts = $absolute !== null ? parse_url($absolute) : false;
    if (
        $absolute === null || $parts === false ||
        strtolower($parts['scheme'] ?? '') !== 'https' ||
        !in_array(strtolower($parts['host'] ?? ''), SITE_HOSTS, true) ||
        isset($parts['user']) || isset($parts['pass']) ||
        (isset($parts['port']) && (int) $parts['port'] !== 443)
    ) {
        $scheme = is_array($parts) ? strtolower($parts['scheme'] ?? 'invalid') : 'invalid';
        $host = is_array($parts) ? strtolower($parts['host'] ?? 'invalid') : 'invalid';
        throw new InvalidArgumentException(
            'URL rejected (' . $scheme . '://' . $host . '); only HTTPS URLs on myf2mi.top are accepted.'
        );
    }

    return $absolute;
}

/** Fetch an HTML page, optionally allowing public cross-host URLs and redirects. */
function fetchHtml(string $url, bool $allowExternal = false, bool $publicOnly = false): array
{
    $currentUrl = $allowExternal ? assertHttpUrl($url) : assertSiteUrl($url);

    for ($redirect = 0; $redirect <= 5; $redirect++) {
        $currentParts = parse_url($currentUrl);
        if ($currentParts === false || empty($currentParts['host'])) {
            throw new RuntimeException('The requested URL is invalid.');
        }
        $currentHost = strtolower(trim($currentParts['host'], '[]'));
        $currentPort = (int) ($currentParts['port'] ?? (strtolower($currentParts['scheme']) === 'https' ? 443 : 80));
        $resolve = [];
        if ($publicOnly && filter_var($currentHost, FILTER_VALIDATE_IP) === false) {
            $addresses = publicAddressesForHost($currentHost);
            $address = $addresses[0];
            $resolve[] = $currentHost . ':' . $currentPort . ':' . (str_contains($address, ':') ? '[' . $address . ']' : $address);
        } elseif ($publicOnly) {
            // Literal IPs are checked here too; no DNS override is needed.
            publicAddressesForHost($currentHost);
        }

        $location = null;
        $curl = curl_init($currentUrl);
        if ($curl === false) {
            throw new RuntimeException('Could not initialize the HTTP client.');
        }

        $curlOptions = [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_FOLLOWLOCATION => false,
            CURLOPT_CONNECTTIMEOUT => 10,
            CURLOPT_TIMEOUT => 30,
            CURLOPT_USERAGENT => CRAWLER_USER_AGENT,
            CURLOPT_HTTPHEADER => ['Accept: text/html,application/xhtml+xml'],
            CURLOPT_ENCODING => '',
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_PROTOCOLS => $allowExternal ? CURLPROTO_HTTP | CURLPROTO_HTTPS : CURLPROTO_HTTPS,
            CURLOPT_MAXFILESIZE => MAX_RESPONSE_BYTES,
            CURLOPT_HEADERFUNCTION => static function ($handle, string $header) use (&$location): int {
                if (stripos($header, 'Location:') === 0) {
                    $location = trim(substr($header, strlen('Location:')));
                }
                return strlen($header);
            },
        ];
        if ($resolve !== []) {
            $curlOptions[CURLOPT_RESOLVE] = $resolve;
        }
        curl_setopt_array($curl, $curlOptions);

        $html = curl_exec($curl);
        $curlError = curl_error($curl);
        $curlErrno = curl_errno($curl);
        $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);

        if ($html === false) {
            throw new RuntimeException(
                'Request failed (cURL ' . $curlErrno . '): ' . ($curlError ?: 'unknown cURL error')
            );
        }
        if (strlen($html) > MAX_RESPONSE_BYTES) {
            throw new RuntimeException('The response exceeded the 16 MB safety limit.');
        }

        if ($status >= 300 && $status < 400 && $location !== null) {
            if ($redirect === 5) {
                throw new RuntimeException('The site redirected too many times.');
            }
            $nextUrl = absoluteUrl($location, $currentUrl);
            if ($nextUrl === null) {
                throw new RuntimeException('The site returned an invalid redirect.');
            }
            $currentUrl = $allowExternal ? assertHttpUrl($nextUrl) : assertSiteUrl($nextUrl);
            continue;
        }

        if ($status < 200 || $status >= 300) {
            throw new RuntimeException('The site returned HTTP ' . $status . '.');
        }

        return ['html' => $html, 'url' => $currentUrl];
    }

    throw new RuntimeException('Could not fetch the requested page.');
}

function parseHtml(string $html): array
{
    $previous = libxml_use_internal_errors(true);
    $document = new DOMDocument();
    $loaded = $document->loadHTML('<?xml encoding="UTF-8">' . $html, LIBXML_NONET | LIBXML_NOWARNING | LIBXML_NOERROR);
    libxml_clear_errors();
    libxml_use_internal_errors($previous);

    if (!$loaded) {
        throw new RuntimeException('The page could not be parsed as HTML.');
    }

    return [$document, new DOMXPath($document)];
}

function classQuery(string $className): string
{
    return "contains(concat(' ', normalize-space(@class), ' '), ' " . $className . " ')";
}

function nodeText(?DOMNode $node): string
{
    if ($node === null) {
        return '';
    }
    $text = html_entity_decode($node->textContent, ENT_QUOTES | ENT_HTML5, 'UTF-8');
    return trim((string) preg_replace('/\s+/u', ' ', $text));
}

function firstNode(DOMXPath $xpath, string $query, ?DOMNode $context = null): ?DOMNode
{
    $nodes = $xpath->query($query, $context);
    return $nodes !== false && $nodes->length > 0 ? $nodes->item(0) : null;
}

function firstText(DOMXPath $xpath, string $query, ?DOMNode $context = null): string
{
    return nodeText(firstNode($xpath, $query, $context));
}

function nodeHasClass(DOMNode $node, string $className): bool
{
    if (!$node instanceof DOMElement) {
        return false;
    }
    return preg_match('/(?:^|\s)' . preg_quote($className, '/') . '(?:\s|$)/', $node->getAttribute('class')) === 1;
}

function nearestAncestorWithClass(DOMNode $node, string $className): ?DOMNode
{
    for ($current = $node; $current !== null; $current = $current->parentNode) {
        if (nodeHasClass($current, $className)) {
            return $current;
        }
    }
    return null;
}

function imageUrlFromNode(DOMXPath $xpath, DOMNode $context, string $baseUrl): ?string
{
    $images = $xpath->query('.//img', $context);
    if ($images === false) {
        return null;
    }

    foreach ($images as $image) {
        if (!$image instanceof DOMElement) {
            continue;
        }
        foreach (['data-splide-lazy', 'data-src', 'data-original', 'src'] as $attribute) {
            $value = trim($image->getAttribute($attribute));
            if ($value !== '' && !str_starts_with(strtolower($value), 'data:image/')) {
                return absoluteUrl($value, $baseUrl);
            }
        }
        $srcset = trim($image->getAttribute('srcset'));
        if ($srcset !== '') {
            $candidate = trim(explode(',', $srcset)[0]);
            $candidate = preg_split('/\s+/', $candidate)[0] ?? '';
            if ($candidate !== '') {
                return absoluteUrl($candidate, $baseUrl);
            }
        }
    }

    return null;
}

function parseEntry(DOMXPath $xpath, DOMNode $article, string $baseUrl): ?array
{
    $anchor = firstNode($xpath, './/a[@href and (contains(concat(" ", normalize-space(@rel), " "), " bookmark ") or ' . classQuery('stretched-link') . ')]', $article);
    if (!$anchor instanceof DOMElement) {
        $anchor = firstNode($xpath, './/a[@href]', $article);
    }
    if (!$anchor instanceof DOMElement) {
        return null;
    }

    $url = absoluteUrl($anchor->getAttribute('href'), $baseUrl);
    if ($url === null) {
        return null;
    }

    $title = firstText($xpath, './/*[self::h2 or self::h3 or ' . classQuery('entry-title') . ']', $article);
    if ($title === '') {
        $title = trim($anchor->getAttribute('title'));
    }
    if ($title === '') {
        return null;
    }

    $pageTitle = trim($anchor->getAttribute('title'));
    $image = imageUrlFromNode($xpath, $article, $baseUrl);

    return [
        'title' => $title,
        'page_title' => $pageTitle !== '' ? $pageTitle : null,
        'cover_image' => $image,
        'url' => $url,
    ];
}

function extractArticleItems(DOMXPath $xpath, string $query, string $baseUrl, ?DOMNode $context = null): array
{
    $nodes = $xpath->query($query, $context);
    if ($nodes === false) {
        return [];
    }

    $items = [];
    $seen = [];
    foreach ($nodes as $node) {
        $item = parseEntry($xpath, $node, $baseUrl);
        if ($item === null || isset($seen[$item['url']])) {
            continue;
        }
        $seen[$item['url']] = true;
        $items[] = $item;
    }
    return $items;
}

function extractFeaturedItems(DOMXPath $xpath, string $baseUrl): array
{
    $slides = $xpath->query("//*[@id='suggested-movies']//*[" . classQuery('splide__slide') . "]");
    if ($slides === false) {
        return [];
    }

    $items = [];
    $seen = [];
    foreach ($slides as $slide) {
        $anchor = firstNode($xpath, './/a[@href and ' . classQuery('stretched-link') . ']', $slide);
        if (!$anchor instanceof DOMElement) {
            continue;
        }
        $url = absoluteUrl($anchor->getAttribute('href'), $baseUrl);
        $title = firstText($xpath, './/*[self::h2 or self::h3]', $anchor);
        if ($url === null || $title === '' || isset($seen[$url])) {
            continue;
        }
        $seen[$url] = true;
        $items[] = [
            'title' => $title,
            'page_title' => trim($anchor->getAttribute('title')) ?: null,
            'cover_image' => imageUrlFromNode($xpath, $slide, $baseUrl),
            'url' => $url,
        ];
    }
    return $items;
}

function extractHomeSections(string $html, string $pageUrl): array
{
    [, $xpath] = parseHtml($html);
    $sections = [];
    $sectionNodes = $xpath->query("//section[" . classQuery('list-section') . "]");

    if ($sectionNodes !== false) {
        foreach ($sectionNodes as $section) {
            $name = firstText($xpath, './/*[' . classQuery('fsz-22') . ' or self::h2 or self::h3]', $section);
            if ($name === '') {
                $name = trim(($section instanceof DOMElement) ? $section->getAttribute('aria-label') : '');
            }
            if ($name === '') {
                $name = ($section instanceof DOMElement ? $section->getAttribute('id') : '') ?: 'Homepage section';
            }
            $sectionUrl = $pageUrl;
            $viewAll = firstNode($xpath, './/a[' . classQuery('btn-toPrimary') . ' and @href]', $section);
            if ($viewAll instanceof DOMElement) {
                $sectionUrl = absoluteUrl($viewAll->getAttribute('href'), $pageUrl) ?? $pageUrl;
            }

            $items = extractArticleItems(
                $xpath,
                './/article[' . classQuery('entry') . ']',
                $pageUrl,
                $section
            );
            if ($items !== []) {
                $sections[] = [
                    'name' => $name,
                    'type' => 'homepage',
                    'url' => $sectionUrl,
                    'items' => $items,
                ];
            }
        }
    }

    $featured = extractFeaturedItems($xpath, $pageUrl);
    if ($featured !== []) {
        array_unshift($sections, [
            'name' => 'Featured',
            'type' => 'homepage',
            'url' => $pageUrl,
            'items' => $featured,
        ]);
    }

    return $sections;
}

function extractCategories(string $html, string $pageUrl): array
{
    [, $xpath] = parseHtml($html);
    $categories = [];
    $seen = [];
    foreach (['genres-movies' => 'movie', 'genres-series' => 'series'] as $containerId => $contentType) {
        $anchors = $xpath->query("//*[@id='" . $containerId . "']//a[@href]");
        if ($anchors === false) {
            continue;
        }
        foreach ($anchors as $anchor) {
            if (!$anchor instanceof DOMElement) {
                continue;
            }
            $url = absoluteUrl($anchor->getAttribute('href'), $pageUrl);
            $url = $url !== null ? sameOriginUrl($url, $pageUrl) : null;
            $name = nodeText($anchor);
            if ($url === null || $name === '') {
                continue;
            }
            $key = $contentType . '|' . $url;
            if (isset($seen[$key])) {
                continue;
            }
            $seen[$key] = true;

            $itemCount = null;
            $listItemText = nodeText($anchor->parentNode);
            if (preg_match('/\(([0-9٠-٩۰-۹]+)\)/u', $listItemText, $matches) === 1) {
                $digits = strtr($matches[1], ['٠' => '0', '١' => '1', '٢' => '2', '٣' => '3', '٤' => '4', '٥' => '5', '٦' => '6', '٧' => '7', '٨' => '8', '٩' => '9', '۰' => '0', '۱' => '1', '۲' => '2', '۳' => '3', '۴' => '4', '۵' => '5', '۶' => '6', '۷' => '7', '۸' => '8', '۹' => '9']);
                $itemCount = (int) $digits;
            }

            $categories[] = [
                'name' => $name,
                'type' => $contentType,
                'url' => $url,
                'item_count' => $itemCount,
            ];
        }
    }

    return $categories;
}

function nextPageUrl(DOMXPath $xpath, string $pageUrl): ?string
{
    $next = firstNode($xpath, "//link[translate(@rel, 'NEXT', 'next')='next'][@href]");
    if (!$next instanceof DOMElement) {
        return null;
    }
    $url = absoluteUrl($next->getAttribute('href'), $pageUrl);
    if ($url === null) {
        return null;
    }

    return sameOriginUrl($url, $pageUrl);
}

function politeFetch(string $url, int $delaySeconds, int &$requestCount): array
{
    if ($requestCount > 0 && $delaySeconds > 0) {
        sleep($delaySeconds);
    }
    $requestCount++;
    return fetchHtml($url, true, true);
}

function crawlArchive(string $name, string $startUrl, ?int $pageLimit, int $delaySeconds, int &$requestCount): array
{
    $pageUrl = assertHttpUrl($startUrl);
    $items = [];
    $seenItems = [];
    $seenPages = [];
    $pagesCrawled = 0;

    while ($pageUrl !== null && !isset($seenPages[$pageUrl]) && ($pageLimit === null || $pagesCrawled < $pageLimit)) {
        $seenPages[$pageUrl] = true;
        $response = politeFetch($pageUrl, $delaySeconds, $requestCount);
        [, $xpath] = parseHtml($response['html']);
        $pageItems = extractArticleItems(
            $xpath,
            "//main//article[" . classQuery('entry') . "]",
            $response['url']
        );
        foreach ($pageItems as $item) {
            if (!isset($seenItems[$item['url']])) {
                $seenItems[$item['url']] = true;
                $items[] = $item;
            }
        }

        $pagesCrawled++;
        $pageUrl = nextPageUrl($xpath, $response['url']);
    }

    return [
        'name' => $name,
        'type' => 'archive',
        'url' => $startUrl,
        'pages_crawled' => $pagesCrawled,
        'items' => $items,
    ];
}

function crawlSite(?int $pageLimit, int $delaySeconds, string $startUrl = SITE_ORIGIN . '/'): array
{
    $startUrl = assertHttpUrl($startUrl);
    $requests = 0;
    $home = politeFetch($startUrl, $delaySeconds, $requests);
    $origin = urlOrigin($home['url']);
    $sections = extractHomeSections($home['html'], $home['url']);
    $categories = extractCategories($home['html'], $home['url']);
    $sections[] = crawlArchive('Movies', $origin . '/movies/', $pageLimit, $delaySeconds, $requests);
    $sections[] = crawlArchive('Series', $origin . '/series/', $pageLimit, $delaySeconds, $requests);

    return [
        'source' => $home['url'],
        'generated_at' => (new DateTimeImmutable())->format(DateTimeInterface::ATOM),
        'crawl_options' => [
            'start_url' => $startUrl,
            'archive_pages_per_section' => $pageLimit ?? 'all',
            'delay_seconds' => $delaySeconds,
        ],
        'categories' => $categories,
        'sections' => $sections,
    ];
}

function textAfterLabel(DOMXPath $xpath, DOMNode $context, string $pattern): ?string
{
    $nodes = $xpath->query('.//*', $context);
    if ($nodes === false) {
        return null;
    }
    foreach ($nodes as $node) {
        if (!nodeHasClass($node, 'text-muted')) {
            continue;
        }
        $label = nodeText($node);
        if (preg_match($pattern, $label) !== 1) {
            continue;
        }
        $parentText = nodeText($node->parentNode);
        $value = preg_replace($pattern, '', $parentText, 1);
        $value = trim((string) preg_replace('/^[\s:：-]+/u', '', (string) $value));
        if ($value !== '') {
            return $value;
        }
    }
    return null;
}

function mediaQuality(DOMXPath $xpath, DOMNode $context): ?string
{
    $quality = firstText($xpath, './/*[' . classQuery('text') . '][@dir="ltr"]', $context);
    if ($quality !== '') {
        return $quality;
    }
    $text = nodeText($context);
    if (preg_match('/(?:کیفیت|quality)\s*[:：]?\s*([^،|]+)/iu', $text, $matches) === 1) {
        return trim($matches[1]);
    }
    return null;
}

function mediaEncoder(DOMXPath $xpath, DOMNode $context): ?string
{
    return textAfterLabel($xpath, $context, '/(?:انکودر|encoder)\s*[:：]?/iu');
}

function seasonDetails(DOMXPath $xpath, DOMNode $downloadList): ?array
{
    $season = nearestAncestorWithClass($downloadList, 'download-season');
    if ($season === null) {
        return null;
    }
    $button = firstNode($xpath, './/button[@data-bs-target]', $season);
    $title = nodeText($button);
    $status = $button !== null
        ? firstText($xpath, './/*[' . classQuery('fsz-14') . ']', $button)
        : '';
    if ($status !== '') {
        $title = trim(str_replace($status, '', $title));
    }
    return $title !== '' ? ['title' => $title, 'status' => $status !== '' ? $status : null] : null;
}

function hasOnlineLabel(string $text): bool
{
    return preg_match('/پخش\s*آنلاین|تماشای\s*آنلاین|watch\s*online|play\s*online|\bstream(?:ing)?\b/i', $text) === 1;
}

function externalHttpUrl(string $url, string $baseUrl): ?string
{
    $absolute = absoluteUrl($url, $baseUrl);
    if ($absolute === null) {
        return null;
    }
    $parts = parse_url($absolute);
    if ($parts === false || !in_array(strtolower($parts['scheme'] ?? ''), ['http', 'https'], true)) {
        return null;
    }
    return $absolute;
}

function isSiteHost(string $url): bool
{
    $host = parse_url($url, PHP_URL_HOST);
    return is_string($host) && in_array(strtolower($host), SITE_HOSTS, true);
}

function anchorLabel(DOMXPath $xpath, DOMElement $anchor, string $targetUrl): string
{
    $label = nodeText($anchor);
    if ($label === '') {
        $image = firstNode($xpath, './/img[@alt or @title]', $anchor);
        if ($image instanceof DOMElement) {
            $label = trim($image->getAttribute('alt') ?: $image->getAttribute('title'));
        }
    }

    if ($label === '' || preg_match('/^KMPlayer$/i', $label) === 1) {
        for ($parent = $anchor->parentNode; $parent !== null && $parent instanceof DOMElement; $parent = $parent->parentNode) {
            if ($parent->tagName === 'li' || nodeHasClass($parent, 'download-list')) {
                break;
            }
            if ($parent->tagName !== 'div' || !nodeHasClass($parent, 'd-flex')) {
                continue;
            }
            $siblings = $xpath->query('.//a[@href]', $parent);
            if ($siblings !== false) {
                foreach ($siblings as $sibling) {
                    if (!$sibling instanceof DOMElement) {
                        continue;
                    }
                    $href = externalHttpUrl($sibling->getAttribute('href'), SITE_ORIGIN . '/');
                    $siblingText = nodeText($sibling);
                    if ($href === $targetUrl && $siblingText !== '') {
                        return $siblingText;
                    }
                }
            }
        }
    }

    return $label;
}

function collectDownloadLinksForNode(
    DOMXPath $xpath,
    DOMNode $context,
    DOMNode $downloadList,
    string $itemUrl,
    ?string $quality,
    ?string $encoder,
    ?string $season,
    ?string $group
): array {
    $targets = [];

    $anchors = $xpath->query('.//a[@href or @onclick]', $context);
    if ($anchors !== false) {
        foreach ($anchors as $anchor) {
            if (!$anchor instanceof DOMElement) {
                continue;
            }

            $class = $anchor->getAttribute('class');
            $candidateUrl = externalHttpUrl($anchor->getAttribute('href'), $itemUrl);
            if (
                $candidateUrl !== null && !isSiteHost($candidateUrl) &&
                stripos($class, 'require-login') === false
            ) {
                $label = anchorLabel($xpath, $anchor, $candidateUrl);
                $contextText = nodeText($anchor);
                if (!hasOnlineLabel($label . ' ' . $contextText)) {
                    $targets[$candidateUrl] = $targets[$candidateUrl] ?? ['url' => $candidateUrl, 'label' => $label];
                    if ($targets[$candidateUrl]['label'] === '' && $label !== '') {
                        $targets[$candidateUrl]['label'] = $label;
                    }
                }
            }

            if (preg_match("/handleDownloadClick\\s*\\(\\s*(['\"])(https?:\\/\\/.*?)\\1/is", $anchor->getAttribute('onclick'), $matches) === 1) {
                $onclickUrl = externalHttpUrl($matches[2], $itemUrl);
                if ($onclickUrl !== null && !isSiteHost($onclickUrl)) {
                    $label = anchorLabel($xpath, $anchor, $onclickUrl);
                    if (!hasOnlineLabel($label)) {
                        $targets[$onclickUrl] = $targets[$onclickUrl] ?? ['url' => $onclickUrl, 'label' => $label];
                        if ($targets[$onclickUrl]['label'] === '' && $label !== '') {
                            $targets[$onclickUrl]['label'] = $label;
                        }
                    }
                }
            }
        }
    }

    // Some series sections expose the episode URLs in a copy-all textarea.
    $textareas = $xpath->query('.//textarea', $context);
    if ($textareas !== false) {
        foreach ($textareas as $textarea) {
            $raw = html_entity_decode($textarea->textContent, ENT_QUOTES | ENT_HTML5, 'UTF-8');
            if (preg_match_all('~https?://[^\s<>"\']+~i', $raw, $matches) > 0) {
                foreach ($matches[0] as $lineIndex => $foundUrl) {
                    $copyUrl = externalHttpUrl($foundUrl, $itemUrl);
                    if ($copyUrl !== null && !isSiteHost($copyUrl) && !isset($targets[$copyUrl])) {
                        $targets[$copyUrl] = ['url' => $copyUrl, 'label' => 'Download link ' . ($lineIndex + 1)];
                    }
                }
            }
        }
    }

    $result = [];
    foreach ($targets as $target) {
        $label = trim((string) $target['label']);
        if ($label === '') {
            $path = parse_url($target['url'], PHP_URL_PATH);
            $filename = is_string($path) ? basename($path) : '';
            $label = $filename !== '' ? rawurldecode($filename) : 'Download';
        }
        $result[] = [
            'label' => $label,
            'url' => $target['url'],
            'quality' => $quality,
            'encoder' => $encoder,
            'season' => $season,
            'group' => $group,
        ];
    }

    return $result;
}

function extractDownloadData(DOMXPath $xpath, string $itemUrl): array
{
    $lists = $xpath->query("//*[" . classQuery('download-list') . "]");
    if ($lists === false) {
        return ['groups' => [], 'links' => []];
    }

    $groups = [];
    $seasonIndexes = [];
    $flatLinks = [];

    foreach ($lists as $list) {
        $title = firstText($xpath, './/*[' . classQuery('title') . ']', $list);
        $season = seasonDetails($xpath, $list);
        $classes = $list instanceof DOMElement
            ? array_values(array_filter(
                preg_split('/\s+/', trim($list->getAttribute('class'))) ?: [],
                static fn (string $class): bool => $class !== 'download-list'
            ))
            : [];
        $listGroup = [
            'type' => 'download-list',
            'title' => $title !== '' ? $title : null,
            'classes' => $classes,
            'children' => [],
        ];

        $rows = $xpath->query('.//li', $list);
        if ($rows !== false && $rows->length > 0) {
            foreach ($rows as $row) {
                $quality = mediaQuality($xpath, $row);
                $encoder = mediaEncoder($xpath, $row);
                $rowDownloads = collectDownloadLinksForNode(
                    $xpath,
                    $row,
                    $list,
                    $itemUrl,
                    $quality,
                    $encoder,
                    $season['title'] ?? null,
                    $title !== '' ? $title : null
                );
                if ($rowDownloads === []) {
                    continue;
                }

                $qualityGroup = [
                    'type' => 'quality',
                    'quality' => $quality,
                    'encoder' => $encoder,
                    'children' => [],
                ];
                foreach ($rowDownloads as $download) {
                    $qualityGroup['children'][] = [
                        'type' => 'download',
                        'label' => $download['label'],
                        'url' => $download['url'],
                    ];
                    $flatLinks[] = $download;
                }
                $listGroup['children'][] = $qualityGroup;
            }
        } else {
            $rowDownloads = collectDownloadLinksForNode(
                $xpath,
                $list,
                $list,
                $itemUrl,
                null,
                null,
                $season['title'] ?? null,
                $title !== '' ? $title : null
            );
            if ($rowDownloads !== []) {
                $qualityGroup = [
                    'type' => 'quality',
                    'quality' => null,
                    'encoder' => null,
                    'children' => [],
                ];
                foreach ($rowDownloads as $download) {
                    $qualityGroup['children'][] = [
                        'type' => 'download',
                        'label' => $download['label'],
                        'url' => $download['url'],
                    ];
                    $flatLinks[] = $download;
                }
                $listGroup['children'][] = $qualityGroup;
            }
        }

        if ($season !== null) {
            $seasonNode = nearestAncestorWithClass($list, 'download-season');
            if ($seasonNode !== null) {
                $seasonKey = spl_object_id($seasonNode);
                if (!isset($seasonIndexes[$seasonKey])) {
                    $seasonIndexes[$seasonKey] = count($groups);
                    $groups[] = [
                        'type' => 'season',
                        'title' => $season['title'],
                        'status' => $season['status'],
                        'children' => [],
                    ];
                }
                $groups[$seasonIndexes[$seasonKey]]['children'][] = $listGroup;
            }
        } else {
            $groups[] = $listGroup;
        }
    }

    return ['groups' => $groups, 'links' => $flatLinks];
}

function itemDetails(string $html, string $url): array
{
    [$document, $xpath] = parseHtml($html);
    $title = firstText($xpath, '//head/title');
    $cover = null;
    $poster = firstNode($xpath, "//*[@id='post-intro']//*[" . classQuery('entry-poster') . "]");
    if ($poster !== null) {
        $cover = imageUrlFromNode($xpath, $poster, $url);
    }
    if ($cover === null) {
        $cover = imageUrlFromNode($xpath, firstNode($xpath, "//*[@id='post-intro']") ?? $document->documentElement, $url);
    }

    $downloadData = extractDownloadData($xpath, $url);
    return [
        'title' => $title !== '' ? $title : null,
        'url' => $url,
        'cover_image' => $cover,
        'download_group_count' => count($downloadData['groups']),
        'download_groups' => $downloadData['groups'],
        'download_link_count' => count($downloadData['links']),
        'download_links' => $downloadData['links'],
    ];
}

function encodeJson(array $value): string
{
    $json = json_encode(
        $value,
        JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE
    );
    if ($json === false) {
        throw new RuntimeException('Could not encode the result as JSON.');
    }
    return $json . PHP_EOL;
}

function printUsage(): void
{
    $usage = <<<'TEXT'
Usage:
  php scraper.php crawl [--url=https://www.myf2mi.top/] [--pages=3|all] [--output=catalog.json] [--delay=1]
  php scraper.php item <https://www.myf2mi.top/item-page/>

Web item lookup:
  /scraper.php?item=https%3A%2F%2Fwww.myf2mi.top%2Fitem-page%2F

TEXT;
    fwrite(STDOUT, $usage);
}

function parseOptions(array $arguments): array
{
    $options = ['pages' => 3, 'output' => 'catalog.json', 'delay' => 1, 'url' => SITE_ORIGIN . '/'];
    foreach ($arguments as $argument) {
        if (!str_starts_with($argument, '--')) {
            continue;
        }
        [$name, $value] = array_pad(explode('=', substr($argument, 2), 2), 2, null);
        if ($value === null) {
            throw new InvalidArgumentException('Options must use the --name=value form.');
        }
        if ($name === 'pages') {
            if (strtolower($value) === 'all') {
                $options['pages'] = null;
            } elseif (ctype_digit($value) && (int) $value >= 1 && (int) $value <= 1000) {
                $options['pages'] = (int) $value;
            } else {
                throw new InvalidArgumentException('--pages must be a number from 1 to 1000 or "all".');
            }
        } elseif ($name === 'delay') {
            if (!ctype_digit($value) || (int) $value > 30) {
                throw new InvalidArgumentException('--delay must be a whole number from 0 to 30 seconds.');
            }
            $options['delay'] = (int) $value;
        } elseif ($name === 'output') {
            if (trim($value) === '') {
                throw new InvalidArgumentException('--output cannot be empty.');
            }
            $options['output'] = $value;
        } elseif ($name === 'url') {
            $options['url'] = assertHttpUrl($value);
        } else {
            throw new InvalidArgumentException('Unknown option: --' . $name);
        }
    }
    return $options;
}

function writeCatalog(string $path, array $catalog): void
{
    $json = encodeJson($catalog);
    $temporaryPath = $path . '.tmp';
    if (file_put_contents($temporaryPath, $json, LOCK_EX) === false) {
        throw new RuntimeException('Could not write to ' . $temporaryPath . '.');
    }
    if (!rename($temporaryPath, $path)) {
        @unlink($temporaryPath);
        throw new RuntimeException('Could not move the completed catalog into place at ' . $path . '.');
    }
}

function sendJson(array $value, int $status = 200): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('X-Content-Type-Options: nosniff');
    echo encodeJson($value);
    exit;
}

function main(array $arguments): int
{
    $command = $arguments[1] ?? '';
    if ($command === 'crawl') {
        $options = parseOptions(array_slice($arguments, 2));
        $catalog = crawlSite($options['pages'], $options['delay'], $options['url']);
        writeCatalog($options['output'], $catalog);
        $itemCount = array_sum(array_map(static fn (array $section): int => count($section['items']), $catalog['sections']));
        fwrite(STDOUT, 'Wrote ' . $itemCount . ' item entries in ' . count($catalog['sections']) . ' sections to ' . $options['output'] . ".\n");
        return 0;
    }

    if ($command === 'item') {
        $itemUrl = $arguments[2] ?? '';
        $response = fetchHtml(assertHttpUrl($itemUrl), true, true);
        $result = itemDetails($response['html'], $response['url']);
        fwrite(STDOUT, encodeJson($result));
        return 0;
    }

    printUsage();
    return $command === '' || $command === 'help' || $command === '--help' ? 0 : 2;
}

if (PHP_SAPI === 'cli' && isset($_SERVER['SCRIPT_FILENAME']) && realpath($_SERVER['SCRIPT_FILENAME']) === __FILE__) {
    try {
        exit(main($argv));
    } catch (Throwable $error) {
        fwrite(STDERR, 'Error: ' . $error->getMessage() . PHP_EOL);
        exit(1);
    }
}

if (PHP_SAPI !== 'cli' && realpath($_SERVER['SCRIPT_FILENAME'] ?? '') === __FILE__) {
    $itemUrl = $_GET['item'] ?? $_GET['url'] ?? null;
    if (!is_string($itemUrl) || strlen($itemUrl) > 2048) {
        sendJson(['error' => 'Pass an item URL using ?item=<URL>.'], 400);
    }
    try {
        $safeUrl = assertHttpUrl($itemUrl);
        $response = fetchHtml($safeUrl, true, true);
        sendJson(itemDetails($response['html'], $response['url']));
    } catch (InvalidArgumentException $error) {
        sendJson(['error' => $error->getMessage()], 400);
    } catch (Throwable $error) {
        sendJson(['error' => $error->getMessage()], 502);
    }
}
