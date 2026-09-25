package com.eluqen.sensorlock;

import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

public final class LanguageManager {
    private static final String PREFS = "ui_preferences";
    private static final String KEY_LANGUAGE = "language";

    private LanguageManager() {}

    public static String getLanguage(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = sp.getString(KEY_LANGUAGE, null);
        if (saved != null) return normalize(saved);

        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager lm = context.getSystemService(LocaleManager.class);
            if (lm != null && !lm.getApplicationLocales().isEmpty()) {
                return normalize(lm.getApplicationLocales().get(0).getLanguage());
            }
        }

        return normalize(Locale.getDefault().getLanguage());
    }

    public static void setLanguage(Context context, String language) {
        String normalized = normalize(language);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, normalized).apply();

        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager lm = context.getSystemService(LocaleManager.class);
            if (lm != null) {
                lm.setApplicationLocales(LocaleList.forLanguageTags(normalized));
            }
        }
    }

    public static Context wrap(Context base) {
        String code = getLanguage(base);
        Locale locale = Locale.forLanguageTag(code);
        Locale.setDefault(locale);

        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        return base.createConfigurationContext(config);
    }

    private static String normalize(String code) {
        if ("fa".equalsIgnoreCase(code)) return "fa";
        if ("fr".equalsIgnoreCase(code)) return "fr";
        return "en";
    }
}
