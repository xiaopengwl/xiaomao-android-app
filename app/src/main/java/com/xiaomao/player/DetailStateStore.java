package com.xiaomao.player;

import android.content.Context;
import android.content.SharedPreferences;

public final class DetailStateStore {
    private static final String PREFS = "xiaomao_detail_state";
    private static final String KEY_EXPANDED_PREFIX = "expanded_";

    private DetailStateStore() {
    }

    public static boolean isIntroExpanded(Context context, String itemUrl) {
        if (context == null) {
            return false;
        }
        return prefs(context).getBoolean(KEY_EXPANDED_PREFIX + key(itemUrl), false);
    }

    public static void setIntroExpanded(Context context, String itemUrl, boolean expanded) {
        if (context == null) {
            return;
        }
        prefs(context).edit()
                .putBoolean(KEY_EXPANDED_PREFIX + key(itemUrl), expanded)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String value) {
        String safe = value == null ? "" : value.trim();
        return Integer.toHexString(safe.hashCode());
    }
}
