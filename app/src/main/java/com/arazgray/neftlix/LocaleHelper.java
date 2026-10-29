package com.arazgray.neftlix;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

final class LocaleHelper {
    private LocaleHelper() {
    }

    static Context wrap(Context base) {
        String lang = SettingsStore.getLang(base);
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        return base.createConfigurationContext(config);
    }
}
