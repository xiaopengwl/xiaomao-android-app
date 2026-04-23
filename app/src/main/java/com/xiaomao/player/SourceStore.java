package com.xiaomao.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SourceStore {
    private static final String PREFS = "xiaomao_sources";
    private static final String KEY_CUSTOM_SOURCES = "custom_sources";
    private static final String KEY_SELECTED_SOURCE_ID = "selected_source_id";
    private static final Pattern TITLE_PATTERN = Pattern.compile("title\\s*:\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern HOST_PATTERN = Pattern.compile("host\\s*:\\s*['\"]([^'\"]+)['\"]");

    public static final class SourceItem {
        public final String id;
        public final String title;
        public final String host;
        public final String raw;
        public final boolean custom;

        SourceItem(String id, String title, String host, String raw, boolean custom) {
            this.id = id == null ? "" : id;
            this.title = title == null || title.trim().isEmpty() ? "未命名片源" : title.trim();
            this.host = host == null ? "" : host.trim();
            this.raw = raw == null ? "" : raw;
            this.custom = custom;
        }

        NativeSource toNativeSource() {
            return new NativeSource(title, host, raw);
        }
    }

    private SourceStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static ArrayList<SourceItem> loadAll(Context context) {
        ArrayList<SourceItem> list = new ArrayList<>();
        list.addAll(loadBuiltIn(context));
        list.addAll(loadCustom(context));
        return list;
    }

    public static SourceItem resolveSelected(Context context, ArrayList<SourceItem> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        String selectedId = SettingsStore.rememberSource(context) ? getSelectedSourceId(context) : "";
        if (!selectedId.isEmpty()) {
            for (SourceItem item : sources) {
                if (TextUtils.equals(item.id, selectedId)) {
                    return item;
                }
            }
        }
        return sources.get(0);
    }

    public static String getSelectedSourceId(Context context) {
        return prefs(context).getString(KEY_SELECTED_SOURCE_ID, "");
    }

    public static void setSelectedSourceId(Context context, String sourceId) {
        prefs(context).edit().putString(KEY_SELECTED_SOURCE_ID, sourceId == null ? "" : sourceId).apply();
    }

    public static SourceItem saveCustomSource(Context context, String title, String host, String raw) {
        String safeRaw = raw == null ? "" : raw.trim();
        if (safeRaw.isEmpty()) {
            return null;
        }
        String parsedTitle = title == null || title.trim().isEmpty() ? matchFirst(safeRaw, TITLE_PATTERN) : title.trim();
        String parsedHost = host == null || host.trim().isEmpty() ? matchFirst(safeRaw, HOST_PATTERN) : host.trim();
        if (parsedTitle.isEmpty()) {
            parsedTitle = "自定义片源";
        }
        String id = "custom:" + System.currentTimeMillis();
        JSONObject object = new JSONObject();
        try {
            object.put("id", id);
            object.put("title", parsedTitle);
            object.put("host", parsedHost);
            object.put("raw", safeRaw);
        } catch (Exception ignored) {
        }
        JSONArray array = getCustomArray(context);
        array.put(object);
        prefs(context).edit()
                .putString(KEY_CUSTOM_SOURCES, array.toString())
                .putString(KEY_SELECTED_SOURCE_ID, id)
                .apply();
        return new SourceItem(id, parsedTitle, parsedHost, safeRaw, true);
    }

    public static void deleteCustomSource(Context context, String sourceId) {
        if (sourceId == null || !sourceId.startsWith("custom:")) {
            return;
        }
        JSONArray array = getCustomArray(context);
        JSONArray next = new JSONArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            if (!TextUtils.equals(sourceId, object.optString("id", ""))) {
                next.put(object);
            }
        }
        SharedPreferences.Editor editor = prefs(context).edit().putString(KEY_CUSTOM_SOURCES, next.toString());
        if (TextUtils.equals(sourceId, getSelectedSourceId(context))) {
            editor.putString(KEY_SELECTED_SOURCE_ID, "");
        }
        editor.apply();
    }

    private static ArrayList<SourceItem> loadBuiltIn(Context context) {
        ArrayList<SourceItem> list = new ArrayList<>();
        try {
            String[] names = context.getAssets().list("sources");
            if (names == null) {
                return list;
            }
            Arrays.sort(names, Comparator.naturalOrder());
            for (String name : names) {
                if (!name.endsWith(".js")) {
                    continue;
                }
                String raw = readAssetText(context, "sources/" + name);
                String title = matchFirst(raw, TITLE_PATTERN);
                if (title.isEmpty()) {
                    title = name.replace(".js", "");
                }
                String host = matchFirst(raw, HOST_PATTERN);
                list.add(new SourceItem("asset:" + name, title, host, raw, false));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static ArrayList<SourceItem> loadCustom(Context context) {
        ArrayList<SourceItem> list = new ArrayList<>();
        JSONArray array = getCustomArray(context);
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            list.add(new SourceItem(
                    object.optString("id", "custom:" + i),
                    object.optString("title", "自定义片源"),
                    object.optString("host", ""),
                    object.optString("raw", ""),
                    true
            ));
        }
        return list;
    }

    private static JSONArray getCustomArray(Context context) {
        String json = prefs(context).getString(KEY_CUSTOM_SOURCES, "[]");
        try {
            return new JSONArray(json);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static String readAssetText(Context context, String path) throws IOException {
        try (InputStream inputStream = context.getAssets().open(path);
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    private static String matchFirst(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }
}
