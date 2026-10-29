package com.arazgray.neftlix;

/**
 * چیدمان صفحه‌کلید داخل برنامه و ویرایش متن جست‌وجو.
 * منطق خالص است تا بدون اندروید تست شود.
 */
final class SearchKeys {
    static final String DIGITS = "1234567890";

    private static final String PERSIAN_LETTERS = "ابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی";

    private SearchKeys() {
    }

    /** ردیف‌های حروف؛ ارقام جداگانه در {@link #DIGITS} هستند. */
    static String[] letterRows(boolean persian) {
        if (persian) {
            return new String[] {
                    "ضصثقفغعهخحجچ",
                    "شسیبلاتنمکگ",
                    "ظطژزرذدپوآئ"
            };
        }
        return new String[] {
                "qwertyuiop",
                "asdfghjkl",
                "zxcvbnm"
        };
    }

    static boolean coversPersianAlphabet() {
        StringBuilder all = new StringBuilder();
        for (String row : letterRows(true)) all.append(row);
        for (int i = 0; i < PERSIAN_LETTERS.length(); i++) {
            if (all.indexOf(String.valueOf(PERSIAN_LETTERS.charAt(i))) < 0) return false;
        }
        return true;
    }

    static boolean coversEnglishAlphabet() {
        StringBuilder all = new StringBuilder();
        for (String row : letterRows(false)) all.append(row);
        for (char c = 'a'; c <= 'z'; c++) {
            if (all.indexOf(String.valueOf(c)) < 0) return false;
        }
        return true;
    }

    static String insert(String text, String key) {
        if (key == null || key.isEmpty()) return text == null ? "" : text;
        if (text == null) return key;
        return text + key;
    }

    static String backspace(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, text.length() - 1);
    }
}
