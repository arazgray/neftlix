package com.arazgray.neftlix;

import android.content.Context;
import android.content.SharedPreferences;

final class SettingsStore {
    static final String LANG_FA = "fa";
    static final String LANG_EN = "en";

    static final int[] ACCENTS = {
            0xFFE50914, 0xFF1E88E5, 0xFF43A047, 0xFF8E24AA, 0xFFF57C00,
    };

    private static final String PREFS = "neftlix_settings";
    private static final String KEY_LANG = "lang";
    private static final String KEY_ACCENT = "accent";

    private SettingsStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String getLang(Context context) {
        String lang = prefs(context).getString(KEY_LANG, null);
        if (LANG_FA.equals(lang)) return LANG_FA;
        return LANG_EN;
    }

    static boolean isPersian(Context context) {
        return LANG_FA.equals(getLang(context));
    }

    static void setLang(Context context, String lang) {
        if (!LANG_EN.equals(lang) && !LANG_FA.equals(lang)) return;
        prefs(context).edit().putString(KEY_LANG, lang).apply();
    }

    static int getAccentIndex(Context context) {
        int index = prefs(context).getInt(KEY_ACCENT, 0);
        if (index < 0 || index >= ACCENTS.length) return 0;
        return index;
    }

    static int getAccentColor(Context context) {
        return ACCENTS[getAccentIndex(context)];
    }

    static void setAccentIndex(Context context, int index) {
        if (index < 0 || index >= ACCENTS.length) return;
        prefs(context).edit().putInt(KEY_ACCENT, index).apply();
    }
}
