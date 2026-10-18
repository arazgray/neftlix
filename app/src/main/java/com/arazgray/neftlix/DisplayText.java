package com.arazgray.neftlix;

/**
 * متن نمایشی عنوان‌ها: خالص و بدون وابستگی به اندروید تا تست واحد داشته باشد.
 */
final class DisplayText {
    private DisplayText() {
    }

    /**
     * عنوان تمیز برای نمایش: پیشوند «دانلود …» و عبارت‌های تبلیغی انتهای عنوان
     * سایت حذف می‌شوند (فقط برای نمایش؛ دادهٔ اصلی دست‌نخورده می‌ماند).
     */
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

    /**
     * برچسب دکمهٔ لینک: زبان «دانلود» سایت به «تماشا» برمی‌گردد؛
     * «مستقیم» یعنی تماشای رایگان.
     */
    static String watchLabel(String siteLabel) {
        String label = siteLabel == null ? "" : siteLabel.replace("دانلود", "تماشا").trim();
        if (label.contains("مستقیم")) return "تماشا رایگان";
        return label.isEmpty() ? "تماشا" : label;
    }

    /**
     * عبارت جست‌وجوی تریلر یوتیوب از روی عنوان و سال، مثلاً
     * «Spider-Man (2012)» می‌شود «spiderman 2012 trailer».
     * عنوان کاملاً فارسی همان‌طور که هست جست‌وجو می‌شود.
     */
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
