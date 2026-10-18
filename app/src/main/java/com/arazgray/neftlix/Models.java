package com.arazgray.neftlix;

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

    BrowseItem(String title, String pageTitle, String coverUrl, String url, String contentType) {
        this.title = title;
        this.pageTitle = pageTitle;
        this.coverUrl = coverUrl;
        this.url = url;
        this.contentType = contentType;
    }
}

final class ItemDetails {
    final String title;
    final String coverUrl;
    final String url;
    final List<DownloadGroup> groups = new ArrayList<>();

    ItemDetails(String title, String coverUrl, String url) {
        this.title = title;
        this.coverUrl = coverUrl;
        this.url = url;
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
