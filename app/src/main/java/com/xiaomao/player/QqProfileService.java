package com.xiaomao.player;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class QqProfileService {
    public interface Callback {
        void onResult(QqProfileStore.Profile profile, String error);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final String API = "https://uapis.cn/api/v1/social/qq/userinfo?qq=";

    private QqProfileService() {
    }

    public static void fetch(String qq, Callback callback) {
        final String query = qq == null ? "" : qq.trim();
        EXECUTOR.execute(() -> {
            QqProfileStore.Profile profile = null;
            String error = "";
            try {
                if (query.isEmpty()) {
                    throw new IllegalArgumentException("请输入 QQ 号码");
                }
                String body = request(API + query);
                profile = parse(query, body);
                if (profile == null || (profile.nickname.isEmpty() && profile.avatarUrl.isEmpty())) {
                    throw new IllegalStateException("接口未返回可展示的 QQ 资料");
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "QQ 信息获取失败" : e.getMessage();
            }
            if (callback != null) {
                callback.onResult(profile, error);
            }
        });
    }

    private static String request(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            int code = connection.getResponseCode();
            InputStream rawStream = code >= 200 && code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (rawStream == null) {
                throw new IllegalStateException("QQ 信息接口暂时不可用");
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
                    throw new IllegalStateException(body.isEmpty() ? "QQ 信息接口暂时不可用" : body);
                }
                return body;
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static QqProfileStore.Profile parse(String qq, String body) throws Exception {
        JSONObject root = new JSONObject(body == null ? "{}" : body);
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            data = root.optJSONObject("result");
        }
        if (data == null) {
            data = root;
        }
        String avatar = first(data,
                "avatar_url",
                "avatar",
                "avatarUrl",
                "qlogo",
                "head",
                "logo",
                "imgurl");
        String nickname = first(data,
                "nickname",
                "nick",
                "name",
                "qq_name");
        String signature = first(data,
                "long_nick",
                "signature",
                "sign",
                "desc",
                "message",
                "content");
        if (signature.isEmpty()) {
            signature = "这个 QQ 暂未返回个性签名";
        }
        return new QqProfileStore.Profile(qq, avatar, nickname, signature);
    }

    private static String first(JSONObject object, String... keys) {
        if (object == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = object.optString(key, "");
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }
}
