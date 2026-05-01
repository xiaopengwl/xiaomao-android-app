package com.xiaomao.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;

public final class SearchHistoryStore {
    private static final String PREFS = "xiaomao_search_history";
    private static final String KEY_ITEMS = "items";
    private static final int LIMIT = 12;

    private SearchHistoryStore() {
    }

    public static ArrayList<String> list(Context context) {
        ArrayList<String> items = new ArrayList<>();
        if (context == null) {
            return items;
        }
        String raw = prefs(context).getString(KEY_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                String item = safe(array.optString(i, ""));
                if (!item.isEmpty() && !items.contains(item)) {
                    items.add(item);
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    public static void save(Context context, String keyword) {
        if (context == null) {
            return;
        }
        String value = safe(keyword);
        if (value.isEmpty()) {
            return;
        }
        ArrayList<String> items = list(context);
        items.remove(value);
        items.add(0, value);
        while (items.size() > LIMIT) {
            items.remove(items.size() - 1);
        }
        persist(context, items);
    }

    public static void remove(Context context, String keyword) {
        if (context == null) {
            return;
        }
        ArrayList<String> items = list(context);
        items.remove(safe(keyword));
        persist(context, items);
    }

    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        prefs(context).edit().remove(KEY_ITEMS).apply();
    }

    private static void persist(Context context, ArrayList<String> items) {
        JSONArray array = new JSONArray();
        for (String item : items) {
            String value = safe(item);
            if (!value.isEmpty()) {
                array.put(value);
            }
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
