package com.arazgray.neftlix;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * فهرست «علاقه‌مندی‌ها» روی حافظه داخلی؛ بدون نیاز به حساب یا سرور.
 * فقط با File و org.json کار می‌کند تا تست واحد داشته باشد.
 */
final class WatchlistStore {
    private final File file;
    private final List<BrowseItem> items = new ArrayList<>();

    WatchlistStore(File file) {
        this.file = file;
        load();
    }

    List<BrowseItem> items() {
        return new ArrayList<>(items);
    }

    int size() {
        return items.size();
    }

    boolean contains(String url) {
        return indexOf(url) >= 0;
    }

    /** اضافه می‌کند (تکراری به اول منتقل می‌شود)؛ برمی‌گرداند که داخل فهرست هست. */
    boolean toggle(BrowseItem item) {
        if (item == null || item.url == null || item.url.isEmpty()) return false;
        int index = indexOf(item.url);
        if (index >= 0) {
            items.remove(index);
            save();
            return false;
        }
        items.add(0, item);
        save();
        return true;
    }

    void remove(String url) {
        int index = indexOf(url);
        if (index >= 0) {
            items.remove(index);
            save();
        }
    }

    private int indexOf(String url) {
        if (url == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (url.equals(items.get(i).url)) return i;
        }
        return -1;
    }

    private void load() {
        items.clear();
        try {
            if (!file.exists() || file.length() == 0) return;
            StringBuilder body = new StringBuilder();
            java.io.BufferedReader reader =
                    new java.io.BufferedReader(new java.io.FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            reader.close();
            org.json.JSONArray arr = new org.json.JSONArray(body.toString());
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null || o.optString("url", "").isEmpty()) continue;
                items.add(new BrowseItem(
                        o.optString("title", ""),
                        o.optString("pageTitle", ""),
                        o.optString("coverUrl", ""),
                        o.optString("url", ""),
                        o.optString("contentType", ""),
                        o.optString("meta", "")));
            }
        } catch (Exception ignored) {
            items.clear();
        }
    }

    private void save() {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (BrowseItem item : items) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("title", item.title);
                o.put("pageTitle", item.pageTitle);
                o.put("coverUrl", item.coverUrl);
                o.put("url", item.url);
                o.put("contentType", item.contentType);
                o.put("meta", item.meta);
                arr.put(o);
            }
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(arr.toString());
            writer.close();
        } catch (Exception ignored) {
        }
    }
}
