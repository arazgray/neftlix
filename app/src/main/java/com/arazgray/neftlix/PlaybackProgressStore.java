package com.arazgray.neftlix;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class PlaybackProgressStore {
    private static final int MAX_ITEMS = 500;
    private final File file;
    private final LinkedHashMap<String, Long> positions = new LinkedHashMap<>();

    PlaybackProgressStore(File file) {
        this.file = file;
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) body.write(buffer, 0, count);
            org.json.JSONArray entries = new org.json.JSONArray(body.toString("UTF-8"));
            for (int i = Math.max(0, entries.length() - MAX_ITEMS); i < entries.length(); i++) {
                org.json.JSONObject entry = entries.optJSONObject(i);
                if (entry == null) continue;
                String key = entry.optString("url", "");
                long position = entry.optLong("position", 0);
                if (!key.isEmpty() && position > 0) positions.put(key, position);
            }
        } catch (Exception ignored) {
        }
    }

    long position(String url) {
        Long position = positions.get(url);
        return position == null ? 0 : position;
    }

    void save(String url, long position) {
        if (url == null || url.isEmpty()) return;
        positions.remove(url);
        if (position > 0) positions.put(url, position);
        while (positions.size() > MAX_ITEMS) positions.remove(positions.keySet().iterator().next());
        File pending = new File(file.getPath() + ".tmp");
        try {
            org.json.JSONArray entries = new org.json.JSONArray();
            for (Map.Entry<String, Long> item : positions.entrySet()) {
                org.json.JSONObject entry = new org.json.JSONObject();
                entry.put("url", item.getKey());
                entry.put("position", item.getValue());
                entries.put(entry);
            }
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(pending)) {
                output.write(entries.toString().getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            pending.renameTo(file);
        } catch (Exception ignored) {
        }
    }
}
