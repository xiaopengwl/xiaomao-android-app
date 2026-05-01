package com.xiaomao.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public final class WatchHistoryStore {
    private static final String PREFS = "xiaomao_watch_history";
    private static final String KEY_ITEMS = "items";
    private static final int LIMIT = 80;

    public static final class HistoryItem {
        public final String key;
        public final String sourceTitle;
        public final String sourceHost;
        public final String sourceRaw;
        public final String detailUrl;
        public final String seriesTitle;
        public final String episodeTitle;
        public final String poster;
        public final String remark;
        public final String line;
        public final String playInput;
        public final long progressMs;
        public final long updatedAt;

        HistoryItem(String key, String sourceTitle, String sourceHost, String sourceRaw,
                    String detailUrl, String seriesTitle, String episodeTitle, String poster,
                    String remark, String line, String playInput, long progressMs, long updatedAt) {
            this.key = safe(key);
            this.sourceTitle = safe(sourceTitle);
            this.sourceHost = safe(sourceHost);
            this.sourceRaw = sourceRaw == null ? "" : sourceRaw;
            this.detailUrl = safe(detailUrl);
            this.seriesTitle = safe(seriesTitle);
            this.episodeTitle = safe(episodeTitle);
            this.poster = safe(poster);
            this.remark = safe(remark);
            this.line = safe(line);
            this.playInput = safe(playInput);
            this.progressMs = Math.max(0L, progressMs);
            this.updatedAt = updatedAt;
        }
    }

    private WatchHistoryStore() {
    }

    public static ArrayList<HistoryItem> list(Context context) {
        ArrayList<HistoryItem> items = new ArrayList<>();
        if (context == null) {
            return items;
        }
        String raw = prefs(context).getString(KEY_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                HistoryItem item = fromJson(object);
                if (item != null) {
                    items.add(item);
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    public static void record(Context context, HistoryItem nextItem) {
        if (context == null || nextItem == null || nextItem.playInput.isEmpty()) {
            return;
        }
        ArrayList<HistoryItem> items = list(context);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (TextUtils.equals(items.get(i).key, nextItem.key)) {
                items.remove(i);
            }
        }
        items.add(0, nextItem);
        while (items.size() > LIMIT) {
            items.remove(items.size() - 1);
        }
        persist(context, items);
    }

    public static void remove(Context context, HistoryItem target) {
        if (context == null || target == null) {
            return;
        }
        ArrayList<HistoryItem> items = list(context);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (TextUtils.equals(items.get(i).key, target.key)) {
                items.remove(i);
            }
        }
        persist(context, items);
    }

    private static void persist(Context context, ArrayList<HistoryItem> items) {
        JSONArray array = new JSONArray();
        for (HistoryItem item : items) {
            try {
                JSONObject object = new JSONObject();
                object.put("key", item.key);
                object.put("sourceTitle", item.sourceTitle);
                object.put("sourceHost", item.sourceHost);
                object.put("sourceRaw", item.sourceRaw);
                object.put("detailUrl", item.detailUrl);
                object.put("seriesTitle", item.seriesTitle);
                object.put("episodeTitle", item.episodeTitle);
                object.put("poster", item.poster);
                object.put("remark", item.remark);
                object.put("line", item.line);
                object.put("playInput", item.playInput);
                object.put("progressMs", item.progressMs);
                object.put("updatedAt", item.updatedAt);
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    private static HistoryItem fromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        String playInput = safe(object.optString("playInput", ""));
        if (playInput.isEmpty()) {
            return null;
        }
        return new HistoryItem(
                object.optString("key", ""),
                object.optString("sourceTitle", ""),
                object.optString("sourceHost", ""),
                object.optString("sourceRaw", ""),
                object.optString("detailUrl", ""),
                object.optString("seriesTitle", ""),
                object.optString("episodeTitle", ""),
                object.optString("poster", ""),
                object.optString("remark", ""),
                object.optString("line", ""),
                playInput,
                object.optLong("progressMs", 0L),
                object.optLong("updatedAt", 0L)
        );
    }

    public static String buildKey(String sourceHost, String detailUrl, String playInput) {
        return safe(sourceHost) + "|" + safe(detailUrl) + "|" + safe(playInput);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
