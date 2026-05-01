package com.xiaomao.player;

import android.content.Context;
import android.content.SharedPreferences;

public final class SettingsStore {
    private static final String PREFS = "xiaomao_settings";
    private static final String PLAYER_MEMORY_PREFS = "xiaomao_player_memory";
    private static final String KEY_REMEMBER_SOURCE = "remember_source";
    private static final String KEY_DEFAULT_LIBRARY = "default_library";
    private static final String KEY_KEEP_LAST_SEARCH = "keep_last_search";
    private static final String KEY_LAST_SEARCH = "last_search";
    private static final String KEY_PLAYER_KERNEL = "player_kernel";
    private static final String KEY_NIGHT_MODE = "night_mode";

    public static final String PLAYER_KERNEL_SYSTEM = "system";
    public static final String PLAYER_KERNEL_EXO = "exo";

    private SettingsStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SharedPreferences playerMemoryPrefs(Context context) {
        return context.getSharedPreferences(PLAYER_MEMORY_PREFS, Context.MODE_PRIVATE);
    }

    public static boolean rememberSource(Context context) {
        return prefs(context).getBoolean(KEY_REMEMBER_SOURCE, true);
    }

    public static void setRememberSource(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_REMEMBER_SOURCE, enabled).apply();
    }

    public static boolean defaultLibrary(Context context) {
        return prefs(context).getBoolean(KEY_DEFAULT_LIBRARY, false);
    }

    public static void setDefaultLibrary(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DEFAULT_LIBRARY, enabled).apply();
    }

    public static boolean keepLastSearch(Context context) {
        return prefs(context).getBoolean(KEY_KEEP_LAST_SEARCH, false);
    }

    public static void setKeepLastSearch(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_KEEP_LAST_SEARCH, enabled).apply();
        if (!enabled) {
            setLastSearch(context, "");
        }
    }

    public static String lastSearch(Context context) {
        return prefs(context).getString(KEY_LAST_SEARCH, "");
    }

    public static void setLastSearch(Context context, String value) {
        prefs(context).edit().putString(KEY_LAST_SEARCH, value == null ? "" : value.trim()).apply();
    }

    public static String playerKernel(Context context) {
        return PLAYER_KERNEL_SYSTEM;
    }

    public static void setPlayerKernel(Context context, String kernel) {
        prefs(context).edit()
                .putString(KEY_PLAYER_KERNEL, PLAYER_KERNEL_SYSTEM)
                .apply();
    }

    public static boolean useExoKernel(Context context) {
        return false;
    }

    public static boolean nightModeEnabled(Context context) {
        return prefs(context).getBoolean(KEY_NIGHT_MODE, false);
    }

    public static void setNightModeEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_NIGHT_MODE, enabled).apply();
    }

    public static int playerCompatMemoryCount(Context context) {
        return playerMemoryPrefs(context).getAll().size();
    }

    public static void clearPlayerCompatMemory(Context context) {
        playerMemoryPrefs(context).edit().clear().apply();
    }
}
