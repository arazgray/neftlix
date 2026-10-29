package com.arazgray.neftlix;

final class DisplayText {
    private DisplayText() {
    }

    static String cleanTitle(String raw) {
        if (raw == null) return "";
        String title = raw.trim();
        title = title.replaceFirst(
                "^دانلود\\s+(فیلم و سریال|فیلم|سریال|انیمیشن|انیمه|مستند|برنامه)\\s+", "");
        String[] tails = {
                "بدون سانسور با زیرنویس فارسی چسبیده",
                "بدون سانسور و زیرنویس فارسی چسبیده",
                "بدون سانسور با زیرنویس چسبیده",
                "با زیرنویس فارسی چسبیده",
                "با زیرنویس چسبیده",
                "زیرنویس فارسی چسبیده",
                "زیرنویس چسبیده",
                "بدون سانسور",
                "دوبله فارسی",
                "کامل",
                "رایگان",
        };
        boolean changed = true;
        for (int round = 0; round < 3 && changed; round++) {
            changed = false;
            for (String tail : tails) {
                if (title.endsWith(tail)) {
                    title = title.substring(0, title.length() - tail.length()).trim();
                    changed = true;
                }
            }
        }
        return title.isEmpty() ? raw.trim() : title;
    }

    static String watchLabel(String siteLabel) {
        String label = siteLabel == null ? "" : siteLabel.replace("دانلود", "تماشا").trim();
        if (label.contains("مستقیم")) return "پخش مستقیم";
        return label.isEmpty() ? "تماشا" : label;
    }

    static String trailerQuery(String title) {
        String raw = title == null ? "" : title;
        String latin = raw.replaceAll("[^A-Za-z0-9 ]", "").replaceAll("\\s+", " ").trim()
                .toLowerCase(java.util.Locale.ROOT);
        if (latin.isEmpty()) {
            String fallback = raw.trim();
            return fallback.isEmpty() ? "trailer" : fallback + " trailer";
        }
        return latin + " trailer";
    }
}
