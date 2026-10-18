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
        Map<String, BrowseCategory> genreSink = new LinkedHashMap<>();

        List<BrowseItem> featured = new ArrayList<>();
        for (Element slide : home.select("#suggested-movies .splide__slide")) {
            Element anchor = slide.selectFirst("a.stretched-link[href]");
            if (anchor != null) {
                BrowseItem item = parseFeatured(slide, anchor, homeUrl);
                if (item != null) featured.add(item);
            }
        }
        if (!featured.isEmpty()) catalog.sections.add(new BrowseSection("ویژه‌ها", homeUrl, "mixed", featured));

        for (Element section : home.select("section.list-section")) {
            Element heading = section.selectFirst(".fsz-22, h2, h3");
            String title = heading == null ? section.id() : heading.text().trim();
            if (title.isEmpty()) title = "Homepage section";
            Element viewAll = section.selectFirst("a.btn-toPrimary[href]");
            String sectionUrl = viewAll == null ? homeUrl : viewAll.absUrl("href");
            List<BrowseItem> items = parseArticles(section.select("article.entry"), typeFromText(title), homeUrl, genreSink);
            if (!items.isEmpty()) catalog.sections.add(new BrowseSection(title, sectionUrl, typeFromText(title), items));
        }

        String siteOrigin = originOf(homeUrl);
        addArchiveSection(catalog, "فیلم‌ها", siteOrigin + "/movies/", "movie", genreSink);
        addArchiveSection(catalog, "سریال‌ها", siteOrigin + "/series/", "series", genreSink);

        for (BrowseCategory category : genreSink.values()) {
            if ("series".equals(category.contentType)) {
                catalog.seriesCategories.add(category);
            } else {
                catalog.movieCategories.add(category);
            }
        }
        return catalog;
    }

    List<BrowseItem> loadListing(String url, String contentType) throws IOException {
        return loadListingPage(url, contentType, 1).items;
    }

    ListingPage loadListingPage(String url, String contentType, int page) throws IOException {
        Document document = fetch(pageUrl(url, page));
        boolean hasNext = hasNextPage(document);
        List<BrowseItem> items = parseArticles(document.select("main article.entry"), contentType, document.location(), null);
        if (items.isEmpty()) {
            // جست‌وجو و بعضی آرشیوها آیتم‌ها را خارج از main برمی‌گردانند
            items = parseArticles(document.select("article.entry"), contentType, document.location(), null);
        }
        return new ListingPage(items, hasNext, document.location());
    }

    /**
     * جست‌وجوی سراسری آرشیو از طریق quick-search (کل آرشیو، نه فقط فهرست دانلودشده).
     * پاسخ JSON است؛ title ممکن است تگ <em> داشته باشد و مقادیر می‌توانند عدد باشند.
     */
    List<BrowseItem> quickSearch(String query) throws IOException {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return new ArrayList<>();
        String origin = originOf(HOME_URL);
        Connection.Response response = Jsoup.connect(origin + "/quick-search")
                .userAgent("Mozilla/5.0 (Linux; Android TV) NeftlixTV/1.0")
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .data("q", q, "sort", "modified_at:desc")
                .method(Connection.Method.GET)
                .timeout(25000)
                .ignoreContentType(true)
                .ignoreHttpErrors(false)
                .execute();
        try {
            return parseQuickSearch(response.body(), origin);
        } catch (org.json.JSONException e) {
            throw new IOException("Bad search response", e);
        }
    }

    /**
     * تجزیهٔ پاسخ quick-search؛ خالص و بدون شبکه برای تست واحد.
     * گاهی سایت به‌جای فیلدهای مستقیم، آبجکت `_formatted` برمی‌گرداند.
     */
    static List<BrowseItem> parseQuickSearch(String body, String origin) throws org.json.JSONException {
        List<BrowseItem> items = new ArrayList<>();
        org.json.JSONArray results = new org.json.JSONArray(body);
        for (int i = 0; i < results.length(); i++) {
            org.json.JSONObject hit = results.optJSONObject(i);
            if (hit == null) continue;
            org.json.JSONObject formatted = hit.optJSONObject("_formatted");
            String title = stripTagsStatic(hit.optString("title", ""));
            String titleFa = hit.optString("title_fa", "");
            String path = hit.optString("url", "");
            String image = hit.optString("image", "");
            if (formatted != null) {
                if (title.isEmpty()) title = stripTagsStatic(formatted.optString("title", ""));
                if (titleFa.isEmpty()) titleFa = formatted.optString("title_fa", "");
                if (path.isEmpty()) path = formatted.optString("url", "");
                if (image.isEmpty()) image = formatted.optString("image", "");
            }
            if (title.isEmpty() || path.isEmpty()) continue;
            String itemUrl = path.startsWith("http") ? path : origin + (path.startsWith("/") ? path : "/" + path);
            String cover = image.isEmpty() ? "" : (image.startsWith("http") ? image : origin + (image.startsWith("/") ? image : "/" + image));
            String type = hit.optString("type", "");
            String contentType;
            if ("series".equalsIgnoreCase(type)) {
                contentType = "series";
            } else if ("post".equalsIgnoreCase(type)) {
                contentType = "movie";
            } else {
                contentType = itemUrl.contains("/series/") ? "series" : "movie";
            }
            items.add(new BrowseItem(title, titleFa, cover, itemUrl, contentType));
        }
        return items;
    }

    private static String stripTagsStatic(String value) {
        if (value == null) return "";
        return value.replaceAll("<[^>]*>", "").trim();
    }

    String stripTags(String value) {
        return stripTagsStatic(value);
    }

    List<BrowseItem> search(String query) throws IOException {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return new ArrayList<>();
        String url = originOf(HOME_URL) + "/?s=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8);
        Document document = fetch(url);
        List<BrowseItem> items = parseArticles(document.select("main article.entry"), "mixed", document.location(), null);
        if (items.isEmpty()) {
            items = parseArticles(document.select("article.entry"), "mixed", document.location(), null);
        }
        return items;
    }

    // ── کمک‌متدهای زیر modificator ندارند تا تست واحد به آن‌ها دسترسی داشته باشد ──

    String pageUrl(String url, int page) {
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

    boolean hasNextPage(Document document) {
        if (document.selectFirst("link[rel=next]") != null) return true;
        Elements next = document.select(".pagination a.page-numbers, .pagination a.next, a.next.page-numbers");
        return !next.isEmpty();
    }

    ItemDetails loadItem(String url) throws IOException {
        Document document = fetch(url);
        String finalUrl = document.location();
        Element titleElement = document.selectFirst("head title");
        String title = titleElement == null ? "Item" : titleElement.text().trim();
        Element poster = pickPoster(document);
        String cover = poster == null ? "" : imageUrl(poster, finalUrl);
        Element backdrop = document.selectFirst("#post-intro img.coverimg");
        if (backdrop == null) backdrop = document.selectFirst("img.mb-coverimg");

        ItemDetails details = new ItemDetails(title, cover, finalUrl);
        if (backdrop != null) details.backdropUrl = imageUrl(backdrop, finalUrl);

        Element excerpt = document.selectFirst(".entry-excerpt");
        if (excerpt != null) details.story = excerpt.text().trim();
        Element imdbAnchor = document.selectFirst("#post-intro a[href*=imdb.com/title]");
        if (imdbAnchor == null) imdbAnchor = document.selectFirst("a[href*=imdb.com/title]");
        if (imdbAnchor != null) {
            Matcher imdbId = Pattern.compile("title/(tt\\d+)").matcher(imdbAnchor.attr("href"));
            if (imdbId.find()) details.imdbId = imdbId.group(1);
            Element score = imdbAnchor.selectFirst("strong");
            if (score != null) details.imdbRating = score.text().trim();
        }
        Element satisfaction = document.selectFirst(".feedback-satisfaction");
        if (satisfaction != null) details.satisfaction = satisfaction.text().trim();
        for (Element genreAnchor : document.select(".entry-genres a")) {
            String genre = genreAnchor.text().trim();
            if (!genre.isEmpty() && !details.genres.contains(genre)) details.genres.add(genre);
        }
        Element intro = document.selectFirst("#post-intro");
        if (intro != null) {
            for (Element icon : intro.select(".entry-icons i[title]")) {
                String flag = icon.attr("title").trim();
                if (!flag.isEmpty() && !details.flags.contains(flag)) details.flags.add(flag);
            }
        }
        for (Element comment : document.select("li.comment")) {
            if (details.comments.size() >= 15) break;
            Element author = comment.selectFirst("[itemprop=name]");
            Element time = comment.selectFirst("time");
            Element text = comment.selectFirst(".comment-text");
            String commentText = text == null ? "" : text.text().trim();
            if (commentText.isEmpty()) continue;
            details.comments.add(new CommentItem(
                    author == null ? "" : author.text().trim(),
                    time == null ? "" : time.text().trim(),
                    commentText));
        }

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

    private void addArchiveSection(CatalogData catalog, String title, String url, String type,
                                    Map<String, BrowseCategory> genreSink) {
        try {
            Document archive = fetch(url);
            List<BrowseItem> items = parseArticles(archive.select("main article.entry"), type, archive.location(), genreSink);
            catalog.sections.add(new BrowseSection(title, url, type, items));
        } catch (IOException ignored) {
            catalog.sections.add(new BrowseSection(title, url, type, new ArrayList<>()));
        }
    }

    List<BrowseItem> parseArticles(Elements articles, String contentType, String baseUrl,
                                           Map<String, BrowseCategory> genreSink) {
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
            String itemType = "mixed".equals(contentType)
                    ? (url.contains("/series/") ? "series" : "movie")
                    : contentType;
            items.add(new BrowseItem(title, anchor.attr("title"), cover, url, itemType, cardMeta(article)));

            if (genreSink != null) {
                for (Element genreAnchor : article.select(".entry-ganers a[href]")) {
                    String genreName = genreAnchor.text().trim();
                    String genreUrl = genreAnchor.absUrl("href");
                    if (genreName.isEmpty() || genreUrl.isEmpty()) continue;
                    int queryIndex = genreUrl.indexOf('?');
                    String cleanUrl = queryIndex >= 0 ? genreUrl.substring(0, queryIndex) : genreUrl;
                    if (cleanUrl.endsWith("/")) cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
                    String key = itemType + "|" + cleanUrl;
                    if (!genreSink.containsKey(key)) {
                        genreSink.put(key, new BrowseCategory(
                                genreName, itemType, cleanUrl + "/?type=" + itemType, ""));
                    }
                }
            }
        }
        return items;
    }

    /** خط کوتاه زیر کارت: امتیاز و یکی دو ژانر، اگر در صفحه باشد. */
    String cardMeta(Element article) {
        String rating = "";
        Element ratingElement = article.selectFirst(".entry-meta strong");
        if (ratingElement != null) rating = ratingElement.text().trim();
        List<String> genres = new ArrayList<>();
        for (Element genreAnchor : article.select(".entry-ganers a")) {
            String genre = genreAnchor.text().trim();
            if (!genre.isEmpty() && genres.size() < 2) genres.add(genre);
        }
        StringBuilder meta = new StringBuilder();
        if (!rating.isEmpty()) meta.append("★ ").append(rating);
        if (!genres.isEmpty()) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(String.join("، ", genres));
        }
        return meta.toString();
    }

    BrowseItem parseFeatured(Element slide, Element anchor, String baseUrl) {
        String url = anchor.absUrl("href");
        Element heading = anchor.selectFirst("h2, h3");
        String title = heading == null ? anchor.attr("title") : heading.text().trim();
        Element image = slide.selectFirst("img");
        if (url.isEmpty() || title.isEmpty()) return null;
        return new BrowseItem(title, anchor.attr("title"), image == null ? "" : imageUrl(image, baseUrl), url, typeFromText(url));
    }

    DownloadOption parseOption(Element row, String baseUrl) {
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

    void putLink(Map<String, MediaLink> links, String url, String label, Element row, String baseUrl) {
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

    boolean isOnlineLabel(String label) {
        String lower = label == null ? "" : label.toLowerCase(Locale.ROOT);
        return lower.contains("پخش آنلاین") || lower.contains("تماشای آنلاین") || lower.contains("watch online") || lower.contains("play online");
    }

    boolean isSiteHost(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return "www.myf2mi.top".equals(host) || "myf2mi.top".equals(host);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    String imageUrl(Element image, String baseUrl) {
        return imageUrlStatic(image);
    }

    static String imageUrlStatic(Element image) {
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

    private static final Pattern PORTRAIT_IMAGE = Pattern.compile(
            "-(\\d{2,4})x(\\d{2,4})\\.", Pattern.CASE_INSENSITIVE);

    /**
     * پوستر عمودی عنوان (مثل کارت‌های فهرست)؛ هنر افقی coverimg برای پس‌زمینه می‌ماند.
     * بدون شبکه و بدون اندروید تا تست واحد داشته باشد.
     */
    static Element pickPoster(Element root) {
        if (root == null) return null;
        Element explicit = root.selectFirst("#post-intro .entry-poster img");
        if (explicit != null) return explicit;
        Elements candidates = root.select("#post-intro img");
        for (Element image : candidates) {
            Matcher size = PORTRAIT_IMAGE.matcher(imageUrlStatic(image));
            if (size.find()) {
                try {
                    if (Integer.parseInt(size.group(1)) < Integer.parseInt(size.group(2))) {
                        return image;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return candidates.first();
    }

    String typeFromText(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return lower.contains("series") || lower.contains("سریال") ? "series" : "movie";
    }

    String originOf(String url) {
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
