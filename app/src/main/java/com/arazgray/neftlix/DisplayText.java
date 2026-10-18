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
}
