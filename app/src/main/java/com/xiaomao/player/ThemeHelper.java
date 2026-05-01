package com.xiaomao.player;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeHelper {
    private ThemeHelper() {
    }

    public static void apply(Context context) {
        int expectedMode;
        if (SettingsStore.followSystemTheme(context)) {
            expectedMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else {
            expectedMode = SettingsStore.nightModeEnabled(context)
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO;
        }
        if (AppCompatDelegate.getDefaultNightMode() != expectedMode) {
            AppCompatDelegate.setDefaultNightMode(expectedMode);
        }
    }
}
