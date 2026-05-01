package com.xiaomao.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public final class FavoriteStore {
    private static final String PREFS = "xiaomao_favorites";
    private static final String KEY_ITEMS = "items";
    private static final int LIMIT = 60;

    public static final class FavoriteItem {
        public final String key;
        public final String sourceTitle;
        public final String sourceHost;
        public final String sourceRaw;
        public final String itemUrl;
        public final String title;
        public final String poster;
        public final String remark;
        public final long updatedAt;

        FavoriteItem(String key, String sourceTitle, String sourceHost, String sourceRaw,
                     String itemUrl, String title, String poster, String remark, long updatedAt) {
            this.key = safe(key);
            this.sourceTitle = safe(sourceTitle);
            this.sourceHost = safe(sourceHost);
            this.sourceRaw = sourceRaw == null ? "" : sourceRaw;
            this.itemUrl = safe(itemUrl);
            this.title = safe(title);
            this.poster = safe(poster);
            this.remark = safe(remark);
            this.updatedAt = updatedAt;
        }
    }

    private FavoriteStore() {
    }

    public static ArrayList<FavoriteItem> list(Context context) {
        ArrayList<FavoriteItem> items = new ArrayList<>();
        if (context == null) {
            return items;
        }
        String raw = prefs(context).getString(KEY_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                FavoriteItem item = fromJson(object);
                if (item != null) {
                    items.add(item);
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    public static boolean contains(Context context, String sourceHost, String itemUrl) {
        String key = buildKey(sourceHost, itemUrl);
        for (FavoriteItem item : list(context)) {
            if (TextUtils.equals(item.key, key)) {
                return true;
            }
        }
        return false;
    }

    public static boolean toggle(Context context, SourceStore.SourceItem source, NativeDrpyEngine.MediaItem item) {
        if (context == null || source == null || item == null) {
            return false;
        }
        ArrayList<FavoriteItem> items = list(context);
        String itemUrl = safe(item.url.isEmpty() ? item.vodId : item.url);
        String key = buildKey(source.host, itemUrl);
        for (int i = 0; i < items.size(); i++) {
            if (TextUtils.equals(items.get(i).key, key)) {
                items.remove(i);
                persist(context, items);
                return false;
            }
        }
        items.add(0, new FavoriteItem(
                key,
                source.title,
                source.host,
                source.raw,
                itemUrl,
                item.title,
                item.poster,
                item.remark,
                System.currentTimeMillis()
        ));
        trim(items);
        persist(context, items);
        return true;
    }

    public static void remove(Context context, FavoriteItem target) {
        if (context == null || target == null) {
            return;
        }
        ArrayList<FavoriteItem> items = list(context);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (TextUtils.equals(items.get(i).key, target.key)) {
                items.remove(i);
            }
        }
        persist(context, items);
    }

    private static void trim(ArrayList<FavoriteItem> items) {
        while (items.size() > LIMIT) {
            items.remove(items.size() - 1);
        }
    }

    private static void persist(Context context, ArrayList<FavoriteItem> items) {
        JSONArray array = new JSONArray();
        for (FavoriteItem item : items) {
            try {
                JSONObject object = new JSONObject();
                object.put("key", item.key);
                object.put("sourceTitle", item.sourceTitle);
                object.put("sourceHost", item.sourceHost);
                object.put("sourceRaw", item.sourceRaw);
                object.put("itemUrl", item.itemUrl);
                object.put("title", item.title);
                object.put("poster", item.poster);
                object.put("remark", item.remark);
                object.put("updatedAt", item.updatedAt);
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    private static FavoriteItem fromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        String itemUrl = safe(object.optString("itemUrl", ""));
        if (itemUrl.isEmpty()) {
            return null;
        }
        return new FavoriteItem(
                object.optString("key", ""),
                object.optString("sourceTitle", ""),
                object.optString("sourceHost", ""),
                object.optString("sourceRaw", ""),
                itemUrl,
                object.optString("title", ""),
                object.optString("poster", ""),
                object.optString("remark", ""),
                object.optLong("updatedAt", 0L)
        );
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String buildKey(String sourceHost, String itemUrl) {
        return safe(sourceHost) + "|" + safe(itemUrl);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
