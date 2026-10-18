package com.arazgray.neftlix;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CatalogRepository {
    static final String HOME_URL = "https://www.myf2mi.top/";
    private static final Pattern CLICK_DOWNLOAD = Pattern.compile("handleDownloadClick\\s*\\(\\s*(['\"])(https?://.*?)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_IN_TEXT = Pattern.compile("https?://[^\\s<>\\\"]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCODER_LABEL = Pattern.compile("(?:انکودر|encoder)\\s*[:：]?", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALITY_LABEL = Pattern.compile("(?:کیفیت|quality)\\s*[:：]?\\s*([^،|]+)", Pattern.CASE_INSENSITIVE);

    CatalogData loadCatalog() throws IOException {
        Document home = fetch(HOME_URL);
        CatalogData catalog = new CatalogData();
        String homeUrl = home.location();

        List<BrowseItem> featured = new ArrayList<>();
        for (Element slide : home.select("#suggested-movies .splide__slide")) {
            Element anchor = slide.selectFirst("a.stretched-link[href]");
            if (anchor != null) {
                BrowseItem item = parseFeatured(slide, anchor, homeUrl);
                if (item != null) featured.add(item);
            }
        }
        if (!featured.isEmpty()) catalog.sections.add(new BrowseSection("Featured", homeUrl, "mixed", featured));

        for (Element section : home.select("section.list-section")) {
            Element heading = section.selectFirst(".fsz-22, h2, h3");
            String title = heading == null ? section.id() : heading.text().trim();
            if (title.isEmpty()) title = "Homepage section";
            Element viewAll = section.selectFirst("a.btn-toPrimary[href]");
            String sectionUrl = viewAll == null ? homeUrl : viewAll.absUrl("href");
            List<BrowseItem> items = parseArticles(section.select("article.entry"), typeFromText(title), homeUrl);
            if (!items.isEmpty()) catalog.sections.add(new BrowseSection(title, sectionUrl, typeFromText(title), items));
        }

        parseCategories(home, "genres-movies", "movie", catalog.movieCategories, homeUrl);
        parseCategories(home, "genres-series", "series", catalog.seriesCategories, homeUrl);

        String siteOrigin = originOf(homeUrl);
        addArchiveSection(catalog, "Movies", siteOrigin + "/movies/", "movie");
        addArchiveSection(catalog, "Series", siteOrigin + "/series/", "series");
        return catalog;
    }

    List<BrowseItem> loadListing(String url, String contentType) throws IOException {
        return loadListingPage(url, contentType, 1).items;
    }

    ListingPage loadListingPage(String url, String contentType, int page) throws IOException {
        Document document = fetch(pageUrl(url, page));
        boolean hasNext = hasNextPage(document);
        List<BrowseItem> items = parseArticles(document.select("main article.entry"), contentType, document.location());
        if (items.isEmpty()) {
            // جست‌وجو و بعضی آرشیوها آیتم‌ها را خارج از main برمی‌گردانند
            items = parseArticles(document.select("article.entry"), contentType, document.location());
        }
        return new ListingPage(items, hasNext, document.location());
    }

    List<BrowseItem> search(String query) throws IOException {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return new ArrayList<>();
        String url = originOf(HOME_URL) + "/?s=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8);
        Document document = fetch(url);
        List<BrowseItem> items = parseArticles(document.select("main article.entry"), "mixed", document.location());
        if (items.isEmpty()) {
            items = parseArticles(document.select("article.entry"), "mixed", document.location());
        }
        return items;
    }

    private String pageUrl(String url, int page) {
        if (page <= 1 || url == null) return url;
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath() == null ? "/" : uri.getPath();
            path = path.replaceAll("/page/\\d+/?$", "/");
            if (!path.endsWith("/")) path += "/";
            path += "page/" + page + "/";
            java.net.URI rebuilt = new java.net.URI(
                    uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                    path, uri.getQuery(), uri.getFragment());
            return rebuilt.toString();
        } catch (Exception ignored) {
            return url;
        }
    }

    private boolean hasNextPage(Document document) {
        if (document.selectFirst("link[rel=next]") != null) return true;
        Elements next = document.select(".pagination a.page-numbers, .pagination a.next, a.next.page-numbers");
        return !next.isEmpty();
    }

    ItemDetails loadItem(String url) throws IOException {
        Document document = fetch(url);
        String finalUrl = document.location();
        Element titleElement = document.selectFirst("head title");
        String title = titleElement == null ? "Item" : titleElement.text().trim();
        Element poster = document.selectFirst("#post-intro .entry-poster img, #post-intro img.coverimg");
        String cover = poster == null ? "" : imageUrl(poster, finalUrl);

        ItemDetails details = new ItemDetails(title, cover, finalUrl);
        Map<Element, DownloadGroup> seasons = new IdentityHashMap<>();
        for (Element list : document.select(".download-list")) {
            Element seasonElement = list.closest(".download-season");
            DownloadGroup parent;
            if (seasonElement != null) {
                parent = seasons.get(seasonElement);
                if (parent == null) {
                    Element seasonButton = seasonElement.selectFirst("button[data-bs-target]");
                    String seasonTitle = seasonButton == null ? "Season" : seasonButton.text().trim();
                    Element statusElement = seasonButton == null ? null : seasonButton.selectFirst(".fsz-14");
                    String status = statusElement == null ? "" : statusElement.text().trim();
                    if (!status.isEmpty()) seasonTitle = seasonTitle.replace(status, "").trim();
                    parent = new DownloadGroup(seasonTitle, status, true);
                    seasons.put(seasonElement, parent);
                    details.groups.add(parent);
                }
            } else {
                parent = new DownloadGroup("", "", false);
                details.groups.add(parent);
            }

            Element titleNode = list.selectFirst(".title");
            String categoryTitle = titleNode == null ? "Downloads" : titleNode.text().trim();
            StringBuilder cssClasses = new StringBuilder();
            for (String className : list.classNames()) {
                if (!"download-list".equals(className)) {
                    if (cssClasses.length() > 0) cssClasses.append(", ");
                    cssClasses.append(className);
                }
            }
            DownloadCategory category = new DownloadCategory(categoryTitle, cssClasses.toString());
            parent.categories.add(category);

            Elements rows = list.select("li");
            if (rows.isEmpty()) {
                DownloadOption option = parseOption(list, finalUrl);
                if (!option.links.isEmpty()) category.options.add(option);
            } else {
                for (Element row : rows) {
                    DownloadOption option = parseOption(row, finalUrl);
                    if (!option.links.isEmpty()) category.options.add(option);
                }
            }
        }
        return details;
    }

    private void addArchiveSection(CatalogData catalog, String title, String url, String type) {
        try {
            Document archive = fetch(url);
            List<BrowseItem> items = parseArticles(archive.select("main article.entry"), type, archive.location());
            catalog.sections.add(new BrowseSection(title, url, type, items));
        } catch (IOException ignored) {
            catalog.sections.add(new BrowseSection(title, url, type, new ArrayList<>()));
        }
    }

    private void parseCategories(Document home, String elementId, String type, List<BrowseCategory> output, String baseUrl) {
        for (Element anchor : home.select("#" + elementId + " a[href]")) {
            String url = anchor.absUrl("href");
            String title = anchor.text().trim();
            if (url.isEmpty() || title.isEmpty()) continue;
            Element listItem = anchor.parent();
            String count = "";
            if (listItem != null) {
                Matcher matcher = Pattern.compile("\\(([0-9]+)\\)").matcher(listItem.text());
                if (matcher.find()) count = matcher.group(1);
            }
            output.add(new BrowseCategory(title, type, url, count));
        }
    }

    private List<BrowseItem> parseArticles(Elements articles, String contentType, String baseUrl) {
        List<BrowseItem> items = new ArrayList<>();
        for (Element article : articles) {
            Element anchor = article.selectFirst("a.stretched-link[href], a[rel~=bookmark][href]");
            if (anchor == null) anchor = article.selectFirst("a[href]");
            if (anchor == null) continue;

            String url = anchor.absUrl("href");
            Element titleElement = article.selectFirst(".entry-title, h2, h3");
            String title = titleElement == null ? anchor.attr("title") : titleElement.text().trim();
            if (url.isEmpty() || title.isEmpty()) continue;

            String cover = "";
            Element image = article.selectFirst(".entry-cover img, img");
            if (image != null) cover = imageUrl(image, baseUrl);
            items.add(new BrowseItem(title, anchor.attr("title"), cover, url, contentType));
        }
        return items;
    }

    private BrowseItem parseFeatured(Element slide, Element anchor, String baseUrl) {
        String url = anchor.absUrl("href");
        Element heading = anchor.selectFirst("h2, h3");
        String title = heading == null ? anchor.attr("title") : heading.text().trim();
        Element image = slide.selectFirst("img");
        if (url.isEmpty() || title.isEmpty()) return null;
        return new BrowseItem(title, anchor.attr("title"), image == null ? "" : imageUrl(image, baseUrl), url, typeFromText(url));
    }

    private DownloadOption parseOption(Element row, String baseUrl) {
        Element qualityElement = row.selectFirst(".text[dir=ltr]");
        String quality = qualityElement == null ? "" : qualityElement.text().trim();
        if (quality.isEmpty()) {
            Matcher matcher = QUALITY_LABEL.matcher(row.text());
            if (matcher.find()) quality = matcher.group(1).trim();
        }
        String encoder = "";
        for (Element label : row.select(".text-muted")) {
            if (label.text().toLowerCase(Locale.ROOT).contains("encoder") || label.text().contains("انکودر")) {
                Element parent = label.parent();
                String parentText = parent == null ? "" : parent.text();
                encoder = ENCODER_LABEL.matcher(parentText).replaceFirst("").trim();
                break;
            }
        }

        DownloadOption option = new DownloadOption(quality, encoder);
        Map<String, MediaLink> links = new LinkedHashMap<>();
        for (Element anchor : row.select("a[href], a[onclick]")) {
            String cssClass = anchor.className();
            if (cssClass.contains("require-login")) continue;

            String href = anchor.absUrl("href");
            String label = anchor.text().trim();
            if (!href.isEmpty() && !isSiteHost(href) && !"#".equals(anchor.attr("href")) && !isOnlineLabel(label)) {
                putLink(links, href, label, row, baseUrl);
            }

            Matcher click = CLICK_DOWNLOAD.matcher(anchor.attr("onclick"));
            if (click.find()) {
                putLink(links, click.group(2), label, row, baseUrl);
            }
        }

        for (Element textarea : row.select("textarea")) {
            Matcher matcher = URL_IN_TEXT.matcher(textarea.text());
            while (matcher.find()) {
                String mediaUrl = matcher.group();
                putLink(links, mediaUrl, "", row, baseUrl);
            }
        }
        option.links.addAll(links.values());
        return option;
    }

    private void putLink(Map<String, MediaLink> links, String url, String label, Element row, String baseUrl) {
        if (url == null || url.isEmpty() || isSiteHost(url) || isOnlineLabel(label)) return;
        String finalLabel = label == null ? "" : label.trim();
        if (finalLabel.isEmpty() || "KMPlayer".equalsIgnoreCase(finalLabel)) {
            for (Element anchor : row.select(".d-flex a[href]")) {
                String href = anchor.absUrl("href");
                String siblingText = anchor.text().trim();
                if (url.equals(href) && !siblingText.isEmpty() && !"KMPlayer".equalsIgnoreCase(siblingText)) {
                    finalLabel = siblingText;
                    break;
                }
            }
        }
        if (finalLabel.isEmpty()) {
            String path = url;
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) path = path.substring(0, queryIndex);
            int slash = path.lastIndexOf('/');
            finalLabel = slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : "Download";
        }
        links.putIfAbsent(url, new MediaLink(finalLabel, url));
    }

    private boolean isOnlineLabel(String label) {
        String lower = label == null ? "" : label.toLowerCase(Locale.ROOT);
        return lower.contains("پخش آنلاین") || lower.contains("تماشای آنلاین") || lower.contains("watch online") || lower.contains("play online");
    }

    private boolean isSiteHost(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return "www.myf2mi.top".equals(host) || "myf2mi.top".equals(host);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String imageUrl(Element image, String baseUrl) {
        for (String attr : new String[]{"data-splide-lazy", "data-src", "data-original", "src"}) {
            String value = image.attr(attr).trim();
            if (!value.isEmpty() && !value.startsWith("data:image/")) {
                String absolute = image.absUrl(attr);
                return absolute.isEmpty() ? value : absolute;
            }
        }
        String sourceSet = image.attr("srcset").trim();
        if (!sourceSet.isEmpty()) return sourceSet.split(",")[0].trim().split("\\s+")[0];
        return "";
    }

    private String typeFromText(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return lower.contains("series") || lower.contains("سریال") ? "series" : "movie";
    }

    private String originOf(String url) {
        java.net.URI uri = java.net.URI.create(url);
        int port = uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + (port < 0 ? "" : ":" + port);
    }

    private Document fetch(String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android TV) NeftlixTV/1.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .timeout(25000)
                .maxBodySize(16 * 1024 * 1024)
                .followRedirects(true)
                .ignoreHttpErrors(false);
        return connection.get();
    }
}
