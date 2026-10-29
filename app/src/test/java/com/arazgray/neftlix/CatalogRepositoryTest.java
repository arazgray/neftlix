package com.arazgray.neftlix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CatalogRepositoryTest {
    private final CatalogRepository repository = new CatalogRepository();

    @Test
    public void pageUrl_buildsPagination() {
        assertEquals("https://site/x/", repository.pageUrl("https://site/x/", 1));
        assertEquals("https://site/x/", repository.pageUrl("https://site/x/", 0));
        assertEquals("https://site/x/page/2/", repository.pageUrl("https://site/x/", 2));
        assertEquals("https://site/x/page/4/",
                repository.pageUrl("https://site/x/page/3/", 4));
        assertEquals(null, repository.pageUrl(null, 5));
    }

    @Test
    public void hasNextPage_detectsPagination() {
        assertTrue(repository.hasNextPage(
                Jsoup.parse("<html><head><link rel=\"next\" href=\"/p/2/\"></head></html>")));
        assertTrue(repository.hasNextPage(Jsoup.parse(
                "<div class=\"pagination\"><a class=\"next page-numbers\" href=\"/p/2/\">بعد</a></div>")));
        assertFalse(repository.hasNextPage(Jsoup.parse("<div class=\"list\"></div>")));
    }

    @Test
    public void typeFromText_detectsSeries() {
        assertEquals("series", repository.typeFromText("سریال‌ها"));
        assertEquals("series", repository.typeFromText("/series/breaking-bad/"));
        assertEquals("movie", repository.typeFromText("فیلم‌ها"));
        assertEquals("movie", repository.typeFromText(null));
    }

    @Test
    public void originOf_stripsPathAndQuery() {
        assertEquals("https://www.myf2mi.top",
                repository.originOf("https://www.myf2mi.top/series/?s=x"));
        assertEquals("http://host:8080", repository.originOf("http://host:8080/a/b"));
    }

    @Test
    public void stripTags_removesMarkup() {
        assertEquals("dark", repository.stripTags("<em>dark</em>"));
        assertEquals("", repository.stripTags(null));
    }

    @Test
    public void parseQuickSearch_readsDirectFields() throws Exception {
        String body = "[{\"title\":\"<em>Dark</em>\",\"title_fa\":\"تاریک\","
                + "\"url\":\"/series/dark/\",\"image\":\"/img/dark-180x280.jpg\","
                + "\"type\":\"series\"}]";
        List<BrowseItem> items = CatalogRepository.parseQuickSearch(body, "https://www.myf2mi.top");
        assertEquals(1, items.size());
        BrowseItem item = items.get(0);
        assertEquals("Dark", item.title);
        assertEquals("تاریک", item.pageTitle);
        assertEquals("https://www.myf2mi.top/series/dark/", item.url);
        assertEquals("series", item.contentType);
        assertTrue(item.coverUrl, item.coverUrl.startsWith("https://www.myf2mi.top/"));
    }

    @Test
    public void parseQuickSearch_fallsBackToFormatted() throws Exception {
        
        String body = "[{\"title\":\"\",\"title_fa\":\"\",\"url\":\"\",\"image\":\"\","
                + "\"_db_error\":\"x\","
                + "\"_formatted\":{\"title\":\"<em>Godzilla</em>\",\"title_fa\":\"گودزیلا\","
                + "\"url\":\"/movies/godzilla/\",\"image\":\"https://cdn/x/g.jpg\"}}]";
        List<BrowseItem> items = CatalogRepository.parseQuickSearch(body, "https://www.myf2mi.top");
        assertEquals(1, items.size());
        assertEquals("Godzilla", items.get(0).title);
        assertEquals("https://www.myf2mi.top/movies/godzilla/", items.get(0).url);
        assertEquals("https://cdn/x/g.jpg", items.get(0).coverUrl);
        assertEquals("movie", items.get(0).contentType);
    }

    @Test
    public void parseQuickSearch_skipsIncompleteHits() throws Exception {
        String body = "[{\"title\":\"NoUrl\",\"url\":\"\"},"
                + "{\"title\":\"\",\"url\":\"/movies/x/\"},"
                + "\"not-an-object\","
                + "{\"title\":\"Ok\",\"url\":\"/movies/ok/\",\"type\":\"post\"}]";
        List<BrowseItem> items = CatalogRepository.parseQuickSearch(body, "https://www.myf2mi.top");
        assertEquals(1, items.size());
        assertEquals("movie", items.get(0).contentType);
    }

    private Elements articles(String html) {
        Document document = Jsoup.parse(html, "https://www.myf2mi.top/");
        return document.select("article.entry");
    }

    @Test
    public void parseArticles_readsCardsAndGenres() {
        String html = "<article class=\"entry\">"
                + "<a class=\"stretched-link\" href=\"/series/dark/\" title=\"page title\">link</a>"
                + "<h2 class=\"entry-title\">Dark تاریک</h2>"
                + "<div class=\"entry-cover\"><img src=\"https://cdn/x/dark-180x280.jpg\"></div>"
                + "<div class=\"entry-meta\"><strong>8.7</strong></div>"
                + "<div class=\"entry-ganers\"><a href=\"/genre/drama/\">درام</a></div>"
                + "</article>"
                + "<article class=\"entry\"><h2>بدون لینک</h2></article>";
        Map<String, BrowseCategory> sink = new LinkedHashMap<>();
        List<BrowseItem> items = repository.parseArticles(articles(html), "mixed",
                "https://www.myf2mi.top/", sink);
        assertEquals(1, items.size());
        BrowseItem item = items.get(0);
        assertEquals("Dark تاریک", item.title);
        assertEquals("https://www.myf2mi.top/series/dark/", item.url);
        assertEquals("series", item.contentType);
        assertEquals("https://cdn/x/dark-180x280.jpg", item.coverUrl);
        assertEquals(1, sink.size());
        assertTrue(sink.keySet().iterator().next().contains("/genre/drama"));
    }

    @Test
    public void parseOption_readsDirectLinkQualityAndEncoder() {
        String html = "<li><span class=\"text\" dir=\"ltr\">1080p BluRay</span>"
                + "<span class=\"text-muted\">انکودر: x265</span>"
                + "<a href=\"https://dl.example.com/dark-1080.mkv\">تماشا با کیفیت 1080p</a>"
                + "</li>";
        Document document = Jsoup.parse(html, "https://www.myf2mi.top/");
        DownloadOption option = repository.parseOption(document.selectFirst("li"),
                "https://www.myf2mi.top/");
        assertEquals("1080p BluRay", option.quality);
        assertTrue(option.encoder, option.encoder.contains("x265"));
        assertEquals(1, option.links.size());
        assertEquals("https://dl.example.com/dark-1080.mkv", option.links.get(0).url);
    }

    @Test
    public void parseOption_skipsLoginOnlyLinks() {
        String html = "<li><span class=\"text\" dir=\"ltr\">720p</span>"
                + "<a class=\"require-login\" href=\"https://dl.example.com/x.mkv\">عضویت</a></li>";
        Document document = Jsoup.parse(html, "https://www.myf2mi.top/");
        DownloadOption option = repository.parseOption(document.selectFirst("li"),
                "https://www.myf2mi.top/");
        assertTrue(option.links.isEmpty());
    }

    @Test
    public void parseOption_readsOnclickAndTextareaLinks() {
        String html = "<li><span class=\"text\" dir=\"ltr\">480p</span>"
                + "<a onclick=\"handleDownloadClick('https://dl.example.com/a.mkv')\">بخش ۱</a>"
                + "<textarea>https://dl.example.com/b.mkv</textarea></li>";
        Document document = Jsoup.parse(html, "https://www.myf2mi.top/");
        DownloadOption option = repository.parseOption(document.selectFirst("li"),
                "https://www.myf2mi.top/");
        assertEquals(2, option.links.size());
    }

    @Test
    public void pickPoster_prefersPortraitLikeLists() {
        Document document = Jsoup.parse(
                "<div id=\"post-intro\">"
                        + "<img class=\"coverimg\" src=\"https://cdn/x/back-2048x1152.jpg\">"
                        + "<img src=\"https://cdn/x/poster-360x480.jpg\">"
                        + "</div>",
                "https://www.myf2mi.top/m/x/");
        Element poster = CatalogRepository.pickPoster(document);
        assertEquals("https://cdn/x/poster-360x480.jpg", poster.absUrl("src"));
    }

    @Test
    public void pickPoster_prefersEntryPoster() {
        Document document = Jsoup.parse(
                "<div id=\"post-intro\">"
                        + "<img class=\"coverimg\" src=\"https://cdn/x/back-2048x1152.jpg\">"
                        + "<span class=\"entry-poster\"><img src=\"https://cdn/x/p.jpg\"></span>"
                        + "</div>",
                "https://www.myf2mi.top/m/x/");
        Element poster = CatalogRepository.pickPoster(document);
        assertEquals("https://cdn/x/p.jpg", poster.absUrl("src"));
    }

    @Test
    public void pickPoster_fallsBackToFirstImage() {
        Document document = Jsoup.parse(
                "<div id=\"post-intro\">"
                        + "<img class=\"coverimg\" src=\"https://cdn/x/back-2048x1152.jpg\">"
                        + "</div>",
                "https://www.myf2mi.top/m/x/");
        Element poster = CatalogRepository.pickPoster(document);
        assertEquals("https://cdn/x/back-2048x1152.jpg", poster.absUrl("src"));
    }

    @Test
    public void pickPoster_noImagesReturnsNull() {
        assertNull(CatalogRepository.pickPoster(Jsoup.parse("<div></div>")));
        assertNull(CatalogRepository.pickPoster(null));
    }

    @Test
    public void guards_classifyLabelsAndHosts() {        assertTrue(repository.isOnlineLabel("پخش آنلاین"));
        assertTrue(repository.isOnlineLabel("Watch Online now"));
        assertFalse(repository.isOnlineLabel("تماشا با کیفیت 1080p"));
        assertTrue(repository.isSiteHost("https://www.myf2mi.top/x/"));
        assertTrue(repository.isSiteHost("https://myf2mi.top/x/"));
        assertFalse(repository.isSiteHost("https://dl.example.com/x.mkv"));
    }
}
