package com.arazgray.neftlix;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** انگلیسی معادل برچسب‌های فارسی سایت؛ ناشناخته‌ها همان فارسی می‌مانند. */
final class CatalogLabels {
    private static final Map<String, String> EN = new HashMap<>();
    private static final Pattern TOP_YEAR = Pattern.compile("^برترین فیلم‌های (\\d+)$");

    static {
        put("ویژه‌ها", "Featured");
        put("فیلم‌های به روز شده", "Updated movies");
        put("سریال‌های به روز شده", "Updated series");
        put("برترین فیلم‌های 2026", "Top movies of 2026");
        put("سریال‌های جهانی", "Global series");
        put("سریال‌های کره‌ای", "Korean series");
        put("سریال‌های ترکی", "Turkish series");
        put("انیمه", "Anime");
        put("سریال‌های چینی", "Chinese series");
        put("فیلم‌های دوبله فارسی", "Persian-dubbed movies");
        put("بزودی", "Coming soon");
        put("250 فیلم برتر", "Top 250 movies");
        put("250 سریال برتر", "Top 250 series");
        put("زیرنویس فارسی", "Persian subtitles");
        put("زیرنویس فارسی چسبیده", "Persian hardsubs");
        put("زیرنویس چسبیده", "Hardsubs");
        put("دوبله فارسی", "Persian dub");
        put("سانسور شده", "Censored");
        put("زبان اصلی", "Original language");
        put("بدون سانسور", "Uncensored");
        put("اکشن", "Action");
        put("انیمیشن", "Animation");
        put("برنامه تلویزیونی", "TV shows");
        put("بیوگرافی", "Biography");
        put("تاریخی", "History");
        put("تاک شو", "Talk show");
        put("ترسناک", "Horror");
        put("جنایی", "Crime");
        put("جنگی", "War");
        put("خانوادگی", "Family");
        put("درام", "Drama");
        put("عاشقانه", "Romance");
        put("علمی تخیلی", "Sci-Fi");
        put("فانتزی", "Fantasy");
        put("فراطبیعی", "Supernatural");
        put("فیلم-نوآر", "Film noir");
        put("کمدی", "Comedy");
        put("کوتاه", "Short");
        put("ماجراجویی", "Adventure");
        put("مستند", "Documentary");
        put("معمایی", "Mystery");
        put("ملودرام", "Melodrama");
        put("موزیک", "Music");
        put("موزیکال", "Musical");
        put("نامشخص", "Unknown");
        put("هیجان انگیز", "Thriller");
        put("ورزشی", "Sports");
        put("وسترن", "Western");
        put("پزشکی", "Medical");
        put("جاسوسی", "Espionage");
        put("حقوقی", "Legal");
        put("سفر", "Travel");
        put("طبیعت", "Nature");
        put("غذا", "Food");
        put("کودک و نوجوان", "Kids");
        put("مسابقه ها", "Game shows");
        put("adult", "Adult");
        put("diy", "DIY");
        put("science-fiction", "Sci-Fi");
        put("sports", "Sports");
    }

    private CatalogLabels() {
    }

    private static void put(String fa, String en) {
        EN.put(normalize(fa), en);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace("‌", "").trim().replaceAll("\\s+", " ");
    }

    static String english(String fa) {
        if (fa == null || fa.isEmpty()) return fa == null ? "" : fa;
        String hit = EN.get(normalize(fa));
        if (hit != null) return hit;
        Matcher year = TOP_YEAR.matcher(fa.trim());
        if (year.matches()) return "Top movies of " + year.group(1);
        if (fa.startsWith("قسمت ")) return "Episode " + latinDigits(fa.substring("قسمت ".length()).trim());
        if (fa.contains("مستقیم")) return "Direct play";
        if (fa.startsWith("دانلود با کیفیت ") || fa.startsWith("تماشا با کیفیت ")) {
            String tail = fa.substring(fa.indexOf("کیفیت ") + "کیفیت ".length()).trim();
            return "Download in " + latinDigits(tail) + " quality";
        }
        return latinDigits(fa)
                .replace("دانلود", "Download")
                .replace("تماشا", "Watch")
                .replace("پخش", "Play")
                .replace("کیفیت", "quality");
    }

    static String englishMeta(String meta) {
        if (meta == null || meta.isEmpty()) return meta == null ? "" : meta;
        String[] parts = meta.split(" · ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            String trimmed = part.trim();
            String translated;
            if (trimmed.startsWith("★")) {
                translated = trimmed;
            } else {
                String[] genres = trimmed.split("[،,]");
                StringBuilder joined = new StringBuilder();
                for (String genre : genres) {
                    String g = genre.trim();
                    if (g.isEmpty()) continue;
                    if (joined.length() > 0) joined.append(", ");
                    joined.append(english(g));
                }
                translated = joined.toString();
            }
            if (translated.isEmpty()) continue;
            if (out.length() > 0) out.append(" · ");
            out.append(translated);
        }
        return out.toString();
    }

    private static String latinDigits(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '۰' && c <= '۹') out.append((char) ('0' + (c - '۰')));
            else out.append(c);
        }
        return out.toString();
    }
}
