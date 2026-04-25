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
    private static final Pattern BARE_JS_FIELD_PATTERN = Pattern.compile(
            "(?m)^(\\s*(?:['\"][^'\"]+['\"]|[A-Za-z_\\u4e00-\\u9fa5][\\w\\u4e00-\\u9fa5]*)\\s*:\\s*)js\\s*:"
    );

    public static final class SourceItem {
        public final String id;
        public final String title;
        public final String host;
        public final String raw;
        public final boolean custom;

        SourceItem(String id, String title, String host, String raw, boolean custom) {
            this.id = id == null ? "" : id;
            this.title = title == null || title.trim().isEmpty() ? "?????" : title.trim();
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
        String safeRaw = normalizeRuleRaw(raw == null ? "" : raw.trim());
        if (safeRaw.isEmpty() || !looksLikeRuleContent(safeRaw)) {
            return null;
        }
        String parsedTitle = title == null || title.trim().isEmpty() ? matchFirst(safeRaw, TITLE_PATTERN) : title.trim();
        String parsedHost = host == null || host.trim().isEmpty() ? matchFirst(safeRaw, HOST_PATTERN) : host.trim();
        if (parsedTitle.isEmpty()) {
            parsedTitle = "?????";
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
                String raw = normalizeRuleRaw(readAssetText(context, "sources/" + name));
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
        JSONArray migrated = new JSONArray();
        boolean changed = false;
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                continue;
            }
            String originalRaw = object.optString("raw", "");
            String normalizedRaw = normalizeRuleRaw(originalRaw);
            if (!TextUtils.equals(originalRaw, normalizedRaw)) {
                changed = true;
                try {
                    object.put("raw", normalizedRaw);
                } catch (Exception ignored) {
                }
            }
            migrated.put(object);
            list.add(new SourceItem(
                    object.optString("id", "custom:" + i),
                    object.optString("title", "?????"),
                    object.optString("host", ""),
                    normalizedRaw,
                    true
            ));
        }
        if (changed) {
            prefs(context).edit().putString(KEY_CUSTOM_SOURCES, migrated.toString()).apply();
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

    public static String normalizeRuleRaw(String raw) {
        String safeRaw = raw == null ? "" : raw;
        if (safeRaw.isEmpty() || !safeRaw.contains("js:")) {
            return safeRaw;
        }
        Matcher matcher = BARE_JS_FIELD_PATTERN.matcher(safeRaw);
        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        while (matcher.find(cursor)) {
            int bodyStart = matcher.end();
            int bodyEnd = findBareJsFieldEnd(safeRaw, bodyStart);
            if (bodyEnd <= bodyStart) {
                cursor = matcher.end();
                continue;
            }
            builder.append(safeRaw, cursor, matcher.start());
            builder.append(matcher.group(1));
            builder.append('`');
            builder.append("js:");
            builder.append(escapeTemplateLiteral(safeRaw.substring(bodyStart, bodyEnd)));
            builder.append('`');
            cursor = bodyEnd;
        }
        if (cursor == 0) {
            return safeRaw;
        }
        builder.append(safeRaw.substring(cursor));
        return builder.toString();
    }

    private static boolean looksLikeRuleContent(String raw) {
        if (raw == null) {
            return false;
        }
        String normalized = raw.replace("\r", "");
        return normalized.contains("var rule")
                || normalized.contains("let rule")
                || normalized.contains("const rule")
                || normalized.contains("rule = {");
    }

    private static int findBareJsFieldEnd(String text, int start) {
        int braceDepth = 0;
        int bracketDepth = 0;
        int parenDepth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inTemplate = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inSingle) {
                if (ch == '\\') {
                    i++;
                    continue;
                }
                if (ch == '\'') {
                    inSingle = false;
                }
                continue;
            }
            if (inDouble) {
                if (ch == '\\') {
                    i++;
                    continue;
                }
                if (ch == '"') {
                    inDouble = false;
                }
                continue;
            }
            if (inTemplate) {
                if (ch == '\\') {
                    i++;
                    continue;
                }
                if (ch == '`') {
                    inTemplate = false;
                }
                continue;
            }
            if (ch == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (ch == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (ch == '\'') {
                inSingle = true;
                continue;
            }
            if (ch == '"') {
                inDouble = true;
                continue;
            }
            if (ch == '`') {
                inTemplate = true;
                continue;
            }
            if (ch == '{') {
                braceDepth++;
                continue;
            }
            if (ch == '}') {
                if (braceDepth == 0 && bracketDepth == 0 && parenDepth == 0) {
                    return i;
                }
                if (braceDepth > 0) {
                    braceDepth--;
                }
                continue;
            }
            if (ch == '[') {
                bracketDepth++;
                continue;
            }
            if (ch == ']') {
                if (bracketDepth > 0) {
                    bracketDepth--;
                }
                continue;
            }
            if (ch == '(') {
                parenDepth++;
                continue;
            }
            if (ch == ')') {
                if (parenDepth > 0) {
                    parenDepth--;
                }
                continue;
            }
            if (ch == ',' && braceDepth == 0 && bracketDepth == 0 && parenDepth == 0
                    && looksLikeNextProperty(text, i + 1)) {
                return i;
            }
        }
        return text.length();
    }

    private static boolean looksLikeNextProperty(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        if (index >= text.length()) {
            return false;
        }
        char ch = text.charAt(index);
        if (ch == '}') {
            return true;
        }
        if (ch == '\'' || ch == '"') {
            char quote = ch;
            index++;
            while (index < text.length()) {
                char current = text.charAt(index);
                if (current == '\\') {
                    index += 2;
                    continue;
                }
                if (current == quote) {
                    index++;
                    break;
                }
                index++;
            }
        } else {
            if (!Character.isLetter(ch) && ch != '_' && !isCjkChar(ch)) {
                return false;
            }
            index++;
            while (index < text.length()) {
                char current = text.charAt(index);
                if (Character.isLetterOrDigit(current) || current == '_' || isCjkChar(current)) {
                    index++;
                    continue;
                }
                break;
            }
        }
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index < text.length() && text.charAt(index) == ':';
    }

    private static boolean isCjkChar(char ch) {
        return ch >= '\u4e00' && ch <= '\u9fa5';
    }

    private static String escapeTemplateLiteral(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("${", "\\${");
    }
}
