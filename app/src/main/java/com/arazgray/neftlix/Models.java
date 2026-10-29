package com.arazgray.neftlix;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class CatalogData {
    final List<BrowseSection> sections = new ArrayList<>();
    final List<BrowseCategory> movieCategories = new ArrayList<>();
    final List<BrowseCategory> seriesCategories = new ArrayList<>();
}

final class BrowseSection {
    final String title;
    final String url;
    final String contentType;
    final List<BrowseItem> items;

    BrowseSection(String title, String url, String contentType, List<BrowseItem> items) {
        this.title = title;
        this.url = url;
        this.contentType = contentType;
        this.items = items;
    }
}

final class BrowseCategory {
    final String title;
    final String contentType;
    final String url;
    final String count;

    BrowseCategory(String title, String contentType, String url, String count) {
        this.title = title;
        this.contentType = contentType;
        this.url = url;
        this.count = count;
    }
}

final class BrowseItem {
    final String title;
    final String pageTitle;
    final String coverUrl;
    final String url;
    final String contentType;
    final String meta;

    BrowseItem(String title, String pageTitle, String coverUrl, String url, String contentType) {
        this(title, pageTitle, coverUrl, url, contentType, "");
    }

    BrowseItem(String title, String pageTitle, String coverUrl, String url, String contentType, String meta) {
        this.title = title;
        this.pageTitle = pageTitle;
        this.coverUrl = coverUrl;
        this.url = url;
        this.contentType = contentType;
        this.meta = meta == null ? "" : meta;
    }
}

final class ItemDetails {
    final String title;
    final String coverUrl;
    final String url;
    String backdropUrl = "";
    String story = "";
    String imdbRating = "";
    String imdbId = "";
    String satisfaction = "";
    final List<String> genres = new ArrayList<>();
    final List<String> flags = new ArrayList<>();
    final List<CommentItem> comments = new ArrayList<>();
    final List<DownloadGroup> groups = new ArrayList<>();

    ItemDetails(String title, String coverUrl, String url) {
        this.title = title;
        this.coverUrl = coverUrl;
        this.url = url;
    }
}

final class CommentItem {
    final String author;
    final String time;
    final String text;

    CommentItem(String author, String time, String text) {
        this.author = author;
        this.time = time;
        this.text = text;
    }
}

final class DownloadGroup {
    final String title;
    final String status;
    final boolean season;
    final List<DownloadCategory> categories = new ArrayList<>();

    DownloadGroup(String title, String status, boolean season) {
        this.title = title;
        this.status = status;
        this.season = season;
    }
}

final class DownloadCategory {
    final String title;
    final String classes;
    final List<DownloadOption> options = new ArrayList<>();

    DownloadCategory(String title, String classes) {
        this.title = title;
        this.classes = classes;
    }
}

final class DownloadOption {
    final String quality;
    final String encoder;
    final List<MediaLink> links = new ArrayList<>();

    DownloadOption(String quality, String encoder) {
        this.quality = quality;
        this.encoder = encoder;
    }
}

final class MediaLink {
    final String label;
    final String url;

    MediaLink(String label, String url) {
        this.label = label;
        this.url = url;
    }
}

final class ListingPage {
    final List<BrowseItem> items;
    final boolean hasNext;
    final String url;

    ListingPage(List<BrowseItem> items, boolean hasNext, String url) {
        this.items = items;
        this.hasNext = hasNext;
        this.url = url;
    }
}

final class CatalogCache {
    static void save(File file, CatalogData catalog) throws Exception {
        org.json.JSONObject root = new org.json.JSONObject();
        org.json.JSONArray sections = new org.json.JSONArray();
        for (BrowseSection section : catalog.sections) {
            org.json.JSONObject s = new org.json.JSONObject();
            s.put("title", section.title);
            s.put("url", section.url);
            s.put("contentType", section.contentType);
            org.json.JSONArray items = new org.json.JSONArray();
            for (BrowseItem item : section.items) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("title", item.title);
                o.put("pageTitle", item.pageTitle);
                o.put("coverUrl", item.coverUrl);
                o.put("url", item.url);
                o.put("contentType", item.contentType);
                o.put("meta", item.meta);
                items.put(o);
            }
            s.put("items", items);
            sections.put(s);
        }
        root.put("sections", sections);
        root.put("movieCategories", categoriesToJson(catalog.movieCategories));
        root.put("seriesCategories", categoriesToJson(catalog.seriesCategories));
        java.io.FileWriter writer = new java.io.FileWriter(file);
        writer.write(root.toString());
        writer.close();
    }

    static CatalogData load(File file) {
        try {
            if (!file.exists() || file.length() == 0) return null;
            StringBuilder body = new StringBuilder();
            java.io.BufferedReader reader =
                    new java.io.BufferedReader(new java.io.FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            reader.close();
            org.json.JSONObject root = new org.json.JSONObject(body.toString());
            CatalogData catalog = new CatalogData();
            org.json.JSONArray sections = root.optJSONArray("sections");
            if (sections == null) return null;
            for (int i = 0; i < sections.length(); i++) {
                org.json.JSONObject s = sections.optJSONObject(i);
                if (s == null) continue;
                List<BrowseItem> items = new ArrayList<>();
                org.json.JSONArray arr = s.optJSONArray("items");
                if (arr != null) {
                    for (int j = 0; j < arr.length(); j++) {
                        org.json.JSONObject o = arr.optJSONObject(j);
                        if (o == null || o.optString("url", "").isEmpty()) continue;
                        items.add(new BrowseItem(
                                o.optString("title", ""),
                                o.optString("pageTitle", ""),
                                o.optString("coverUrl", ""),
                                o.optString("url", ""),
                                o.optString("contentType", ""),
                                o.optString("meta", "")));
                    }
                }
                catalog.sections.add(new BrowseSection(
                        s.optString("title", ""),
                        s.optString("url", ""),
                        s.optString("contentType", ""),
                        items));
            }
            categoriesFromJson(root.optJSONArray("movieCategories"), catalog.movieCategories);
            categoriesFromJson(root.optJSONArray("seriesCategories"), catalog.seriesCategories);
            return catalog.sections.isEmpty() ? null : catalog;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static org.json.JSONArray categoriesToJson(List<BrowseCategory> categories) {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (BrowseCategory category : categories) {
            try {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("title", category.title);
                o.put("contentType", category.contentType);
                o.put("url", category.url);
                o.put("count", category.count);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr;
    }

    private static void categoriesFromJson(org.json.JSONArray arr, List<BrowseCategory> out) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject o = arr.optJSONObject(i);
            if (o == null || o.optString("url", "").isEmpty()) continue;
            out.add(new BrowseCategory(
                    o.optString("title", ""),
                    o.optString("contentType", ""),
                    o.optString("url", ""),
                    o.optString("count", "")));
        }
    }
}
