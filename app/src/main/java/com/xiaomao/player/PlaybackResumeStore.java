package com.xiaomao.player;

import android.content.Context;
import android.content.SharedPreferences;

public final class PlaybackResumeStore {
    private static final String PREFS = "xiaomao_playback_resume";
    private static final long MIN_RESUME_MS = 5000L;

    private PlaybackResumeStore() {
    }

    public static long load(Context context, String sourceHost, String seriesTitle, String line, String input) {
        if (context == null) {
            return 0L;
        }
        return prefs(context).getLong(buildKey(sourceHost, seriesTitle, line, input), 0L);
    }

    public static void save(Context context, String sourceHost, String seriesTitle, String line, String input, long positionMs) {
        if (context == null) {
            return;
        }
        String key = buildKey(sourceHost, seriesTitle, line, input);
        if (positionMs < MIN_RESUME_MS) {
            prefs(context).edit().remove(key).apply();
            return;
        }
        prefs(context).edit().putLong(key, Math.max(0L, positionMs)).apply();
    }

    public static void clear(Context context, String sourceHost, String seriesTitle, String line, String input) {
        if (context == null) {
            return;
        }
        prefs(context).edit().remove(buildKey(sourceHost, seriesTitle, line, input)).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String buildKey(String sourceHost, String seriesTitle, String line, String input) {
        return safe(sourceHost) + "|" + safe(seriesTitle) + "|" + safe(line) + "|" + safe(input);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
