package com.xiaomao.player;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DoubanRankService {
    public interface Callback {
        void onResult(Page page, String error);
    }

    public static final class Page {
        public final ArrayList<NativeDrpyEngine.MediaItem> items;
        public final boolean hasMore;

        Page(ArrayList<NativeDrpyEngine.MediaItem> items, boolean hasMore) {
            this.items = items == null ? new ArrayList<>() : items;
            this.hasMore = hasMore;
        }
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int PAGE_SIZE = 20;
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36";
    private static final String API_TEMPLATE = "https://m.douban.com/rexxar/api/v2/subject_collection/%s/items?start=%d&count=%d";
    private static final RankFeed[] FEEDS = new RankFeed[]{
            new RankFeed("movie_top250"),
            new RankFeed("tv_hot", "tv_global_best_weekly", "tv_domestic"),
            new RankFeed("movie_hot_gaia", "movie_latest"),
            new RankFeed("show_hot"),
            new RankFeed("tv_animation"),
            new RankFeed("movie_real_time_hotest", "movie_hot_gaia"),
            new RankFeed("movie_showing", "movie_real_time_hotest")
    };

    private DoubanRankService() {
    }

    public static void fetch(int filterIndex, int page, Callback callback) {
        final int safePage = Math.max(1, page);
        final RankFeed feed = feedFor(filterIndex);
        EXECUTOR.execute(() -> {
            Page result = new Page(new ArrayList<>(), false);
            String error = "";
            try {
                result = load(feed, safePage);
            } catch (Exception e) {
                error = e.getMessage() == null ? "Douban rank request failed" : e.getMessage();
            }
            if (callback != null) {
                callback.onResult(result, error);
            }
        });
    }

    private static Page load(RankFeed feed, int page) throws Exception {
        int start = Math.max(0, (page - 1) * PAGE_SIZE);
        Exception lastError = null;
        Page lastPage = null;
        for (String collectionId : feed.collectionIds) {
            try {
                Page pageResult = loadCollection(collectionId, start);
                if (!pageResult.items.isEmpty() || pageResult.hasMore) {
                    return pageResult;
                }
                lastPage = pageResult;
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (lastPage != null) {
            return lastPage;
        }
        if (lastError != null) {
            throw lastError;
        }
        return new Page(new ArrayList<>(), false);
    }

    private static Page loadCollection(String collectionId, int start) throws Exception {
        String url = String.format(Locale.US, API_TEMPLATE, collectionId, start, PAGE_SIZE);
        String body = request(url);
        JSONObject root = new JSONObject(body == null ? "{}" : body);
        JSONArray itemsArray = root.optJSONArray("subject_collection_items");
        ArrayList<NativeDrpyEngine.MediaItem> items = new ArrayList<>();
        if (itemsArray != null) {
            for (int i = 0; i < itemsArray.length(); i++) {
                NativeDrpyEngine.MediaItem item = parseItem(itemsArray.optJSONObject(i));
                if (item != null) {
                    items.add(item);
                }
            }
        }
        int total = root.optInt("total", 0);
        if (total <= 0) {
            JSONObject collection = root.optJSONObject("subject_collection");
            if (collection != null) {
                total = Math.max(collection.optInt("total", 0), collection.optInt("subject_count", 0));
            }
        }
        boolean hasMore = total > 0 ? (start + items.size()) < total : items.size() >= PAGE_SIZE;
        return new Page(items, hasMore);
    }

    private static NativeDrpyEngine.MediaItem parseItem(JSONObject object) {
        if (object == null) {
            return null;
        }
        String id = safe(object.optString("id", ""));
        String title = safe(object.optString("title", ""));
        if (title.isEmpty()) {
            return null;
        }
        String poster = "";
        JSONObject cover = object.optJSONObject("cover");
        if (cover != null) {
            poster = safe(cover.optString("url", ""));
        }
        if (poster.isEmpty()) {
            poster = safe(object.optString("cover_url", ""));
        }
        String rating = "";
        JSONObject ratingObject = object.optJSONObject("rating");
        if (ratingObject != null) {
            double value = ratingObject.optDouble("value", 0d);
            if (value > 0d) {
                rating = trimRating(value);
            }
        }
        if (rating.isEmpty()) {
            rating = safe(object.optString("score", ""));
        }
        String subtitle = first(object, "card_subtitle", "info", "description");
        String remark = buildRemark(rating, subtitle);
        String subjectUrl = buildSubjectUrl(id);
        return new NativeDrpyEngine.MediaItem(id, id, title, poster, remark, subjectUrl);
    }

    private static String buildRemark(String rating, String subtitle) {
        StringBuilder builder = new StringBuilder();
        if (!rating.isEmpty()) {
            builder.append("Douban ").append(rating);
        }
        if (!subtitle.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(subtitle);
        }
        return builder.toString();
    }

    private static String buildSubjectUrl(String id) {
        if (id == null || id.trim().isEmpty()) {
            return "";
        }
        return "https://movie.douban.com/subject/" + id.trim() + "/";
    }

    private static String trimRating(double value) {
        String text = String.format(Locale.US, "%.1f", value);
        if (text.endsWith(".0")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }

    private static String request(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            connection.setRequestProperty("Referer", "https://m.douban.com/");
            int code = connection.getResponseCode();
            InputStream rawStream = code >= 200 && code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (rawStream == null) {
                throw new IllegalStateException("Douban rank endpoint returned no response");
            }
            try (InputStream stream = rawStream) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int length;
                while ((length = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }
                String body = output.toString(StandardCharsets.UTF_8.name());
                if (code < 200 || code >= 400) {
                    throw new IllegalStateException(body.isEmpty() ? "Douban rank endpoint is unavailable" : body);
                }
                return body;
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static RankFeed feedFor(int filterIndex) {
        int safeIndex = Math.max(0, Math.min(filterIndex, FEEDS.length - 1));
        return FEEDS[safeIndex];
    }

    private static String first(JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = safe(object.optString(key, ""));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return "null".equalsIgnoreCase(text) ? "" : text;
    }

    private static final class RankFeed {
        final String[] collectionIds;

        RankFeed(String... collectionIds) {
            this.collectionIds = collectionIds == null ? new String[0] : collectionIds;
        }
    }
}
