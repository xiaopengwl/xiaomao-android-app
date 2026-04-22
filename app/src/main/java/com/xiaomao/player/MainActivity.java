package com.xiaomao.player;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " XiaomaoAndroid/1.0");

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false);
        }

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                return true;
            }
        });
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidApp");
        webView.loadUrl("file:///android_asset/web/index.html");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidApp");
            webView.destroy();
        }
        super.onDestroy();
    }

    private final class AndroidBridge {
        @JavascriptInterface
        public String getBuiltinSources() {
            JSONArray array = new JSONArray();
            try {
                String[] names = getAssets().list("sources");
                if (names == null) {
                    return array.toString();
                }
                for (String name : names) {
                    if (!name.endsWith(".js")) {
                        continue;
                    }
                    JSONObject item = new JSONObject();
                    item.put("name", name);
                    item.put("content", readAssetText("sources/" + name));
                    array.put(item);
                }
            } catch (Exception e) {
                return errorJson(e);
            }
            return array.toString();
        }

        @JavascriptInterface
        public String getClipboardText() {
            ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null || !manager.hasPrimaryClip()) {
                return "";
            }
            ClipData clipData = manager.getPrimaryClip();
            if (clipData == null || clipData.getItemCount() == 0) {
                return "";
            }
            CharSequence text = clipData.getItemAt(0).coerceToText(MainActivity.this);
            return text == null ? "" : text.toString();
        }

        @JavascriptInterface
        public void copyText(String text) {
            ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText("xiaomao", text));
                toast("Copied to clipboard");
            }
        }

        @JavascriptInterface
        public String httpRequest(String payload) {
            try {
                JSONObject request = new JSONObject(payload);
                return performRequest(request).toString();
            } catch (Exception e) {
                return errorJson(e);
            }
        }

        @JavascriptInterface
        public String sniffMediaUrl(String payload) {
            try {
                JSONObject request = new JSONObject(payload);
                return performSniff(request).toString();
            } catch (Exception e) {
                return errorJson(e);
            }
        }

        @JavascriptInterface
        public void openPlayer(String payload) {
            try {
                JSONObject json = new JSONObject(payload);
                Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                intent.putExtra("title", json.optString("title"));
                intent.putExtra("url", json.optString("url"));
                intent.putExtra("headers", json.optJSONObject("headers") == null ? "{}" : json.optJSONObject("headers").toString());
                startActivity(intent);
            } catch (Exception e) {
                toast("Open player failed: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void openExternal(String url) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            } catch (Exception e) {
                toast("Unable to open external link");
            }
        }

        @JavascriptInterface
        public void toast(String message) {
            MainActivity.this.toast(message);
        }

        @JavascriptInterface
        public void log(String message) {
            android.util.Log.d("XiaomaoApp", message);
        }

        @JavascriptInterface
        public String getAppInfo() {
            JSONObject json = new JSONObject();
            try {
                json.put("appName", getString(R.string.app_name));
                json.put("versionName", BuildConfig.VERSION_NAME);
                json.put("versionCode", BuildConfig.VERSION_CODE);
            } catch (JSONException ignored) {
            }
            return json.toString();
        }
    }

    private void toast(String message) {
        mainHandler.post(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private String readAssetText(String path) throws IOException {
        try (InputStream inputStream = getAssets().open(path);
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

    private JSONObject performRequest(JSONObject request) throws IOException, JSONException {
        String urlValue = request.optString("url");
        String method = request.optString("method", "GET").toUpperCase();
        String body = request.optString("body", "");
        int timeout = request.optInt("timeout", 20000);
        JSONObject headersJson = request.optJSONObject("headers");

        HttpURLConnection connection = (HttpURLConnection) new URL(urlValue).openConnection();
        if (connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setInstanceFollowRedirects(true);
        }
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);
        connection.setUseCaches(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept-Encoding", "gzip");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Xiaomao) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
        if (headersJson != null) {
            Iterator<String> keys = headersJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                connection.setRequestProperty(key, headersJson.optString(key));
            }
        }
        if (!body.isEmpty() && ("POST".equals(method) || "PUT".equals(method))) {
            connection.setDoOutput(true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }
        }

        int statusCode = connection.getResponseCode();
        InputStream rawStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (rawStream == null) {
            rawStream = InputStream.nullInputStream();
        }
        String contentEncoding = connection.getContentEncoding();
        if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
            rawStream = new GZIPInputStream(rawStream);
        }
        byte[] bytes = readAllBytes(rawStream);
        String charset = findCharset(connection.getContentType());
        String responseBody = new String(bytes, Charset.forName(charset));

        JSONObject response = new JSONObject();
        response.put("status", statusCode);
        response.put("ok", statusCode >= 200 && statusCode < 400);
        response.put("url", connection.getURL().toString());
        response.put("body", responseBody);

        JSONObject responseHeaders = new JSONObject();
        Map<String, java.util.List<String>> headerFields = connection.getHeaderFields();
        for (Map.Entry<String, java.util.List<String>> entry : headerFields.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String joined = String.join("; ", entry.getValue());
            responseHeaders.put(key, joined);
        }
        response.put("headers", responseHeaders);
        connection.disconnect();
        return response;
    }

    private JSONObject performSniff(JSONObject request) throws JSONException, InterruptedException {
        final String startUrl = request.optString("url");
        final int timeout = Math.max(3000, Math.min(request.optInt("timeout", 15000), 30000));
        final Map<String, String> headers = jsonToMap(request.optJSONObject("headers"));
        final List<String> matchRules = jsonArrayToList(request.optJSONArray("matchRules"));
        final List<String> excludeRules = jsonArrayToList(request.optJSONArray("excludeRules"));
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<JSONObject> resultRef = new AtomicReference<>();

        mainHandler.post(() -> {
            final WebView sniffView = new WebView(MainActivity.this);
            configureWebView(sniffView);

            String userAgent = headers.get("User-Agent");
            if (!TextUtils.isEmpty(userAgent)) {
                sniffView.getSettings().setUserAgentString(userAgent);
            }

            final AtomicBoolean finished = new AtomicBoolean(false);
            final Set<String> seenUrls = new HashSet<>();
            final Runnable[] timeoutHolder = new Runnable[1];

            timeoutHolder[0] = () -> completeSniffResult(
                    sniffView,
                    finished,
                    latch,
                    resultRef,
                    timeoutHolder[0],
                    "",
                    false,
                    "timeout",
                    sniffView.getUrl(),
                    "Sniffer timed out"
            );

            sniffView.setWebChromeClient(new WebChromeClient());
            sniffView.setWebViewClient(new WebViewClient() {
                private void maybeCapture(String candidate, String source) {
                    if (TextUtils.isEmpty(candidate) || !seenUrls.add(candidate)) {
                        return;
                    }
                    if (looksLikeMediaUrl(candidate, matchRules, excludeRules)) {
                        completeSniffResult(
                                sniffView,
                                finished,
                                latch,
                                resultRef,
                                timeoutHolder[0],
                                candidate,
                                true,
                                source,
                                sniffView.getUrl(),
                                ""
                        );
                    }
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    maybeCapture(request.getUrl().toString(), "navigate");
                    return false;
                }

                @Override
                public void onLoadResource(WebView view, String url) {
                    maybeCapture(url, "resource");
                    super.onLoadResource(view, url);
                }

                @Override
                public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    maybeCapture(request.getUrl().toString(), "intercept");
                    return super.shouldInterceptRequest(view, request);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    String js = "(function(){try{var out=[];var seen={};"
                            + "function add(u){if(!u||seen[u])return;seen[u]=1;out.push(u);}"
                            + "var nodes=document.querySelectorAll('video,source,audio,iframe');"
                            + "for(var i=0;i<nodes.length;i++){add(nodes[i].src);add(nodes[i].getAttribute('src'));add(nodes[i].getAttribute('data-src'));add(nodes[i].currentSrc);}"
                            + "var vids=document.querySelectorAll('[data-config],[data-play],[data-url]');"
                            + "for(var j=0;j<vids.length;j++){add(vids[j].getAttribute('data-config'));add(vids[j].getAttribute('data-play'));add(vids[j].getAttribute('data-url'));}"
                            + "return JSON.stringify({urls:out});}catch(e){return JSON.stringify({error:String(e)})}})();";
                    view.evaluateJavascript(js, value -> {
                        try {
                            String decoded = decodeJsString(value);
                            JSONObject json = new JSONObject(decoded);
                            JSONArray urls = json.optJSONArray("urls");
                            if (urls != null) {
                                for (int i = 0; i < urls.length(); i++) {
                                    String candidate = urls.optString(i);
                                    maybeCapture(normalizePotentialUrl(candidate, url), "dom");
                                    if (finished.get()) {
                                        return;
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    });
                    super.onPageFinished(view, url);
                }
            });

            mainHandler.postDelayed(timeoutHolder[0], timeout);
            if (headers.isEmpty()) {
                sniffView.loadUrl(startUrl);
            } else {
                sniffView.loadUrl(startUrl, headers);
            }
        });

        if (!latch.await(timeout + 5000L, TimeUnit.MILLISECONDS)) {
            JSONObject timeoutResult = new JSONObject();
            timeoutResult.put("ok", false);
            timeoutResult.put("found", false);
            timeoutResult.put("url", "");
            timeoutResult.put("source", "timeout");
            timeoutResult.put("error", "Sniffer timed out");
            return timeoutResult;
        }

        JSONObject result = resultRef.get();
        if (result == null) {
            JSONObject emptyResult = new JSONObject();
            emptyResult.put("ok", false);
            emptyResult.put("found", false);
            emptyResult.put("url", "");
            emptyResult.put("source", "unknown");
            emptyResult.put("error", "No sniff result");
            return emptyResult;
        }
        return result;
    }

    private void configureWebView(WebView target) {
        WebSettings settings = target.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }

    private void completeSniffResult(
            WebView sniffView,
            AtomicBoolean finished,
            CountDownLatch latch,
            AtomicReference<JSONObject> resultRef,
            Runnable timeoutRunnable,
            String mediaUrl,
            boolean found,
            String source,
            String pageUrl,
            String error
    ) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }

        mainHandler.removeCallbacks(timeoutRunnable);
        JSONObject json = new JSONObject();
        try {
            json.put("ok", found);
            json.put("found", found);
            json.put("url", mediaUrl == null ? "" : mediaUrl);
            json.put("source", source == null ? "" : source);
            json.put("pageUrl", pageUrl == null ? "" : pageUrl);
            json.put("error", error == null ? "" : error);
        } catch (JSONException ignored) {
        }
        resultRef.set(json);

        try {
            sniffView.stopLoading();
            sniffView.loadUrl("about:blank");
            sniffView.destroy();
        } catch (Exception ignored) {
        }
        latch.countDown();
    }

    private static boolean looksLikeMediaUrl(String url, List<String> matchRules, List<String> excludeRules) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase();
        if (matchesAnyRule(lower, excludeRules)) {
            return false;
        }
        if (matchesAnyRule(lower, matchRules)) {
            return true;
        }
        return lower.contains(".m3u8")
                || lower.contains(".mp4")
                || lower.contains(".m4v")
                || lower.contains(".flv")
                || lower.contains(".mp3")
                || lower.contains(".mpd")
                || lower.contains("mime=video")
                || lower.contains("video_mp4")
                || lower.startsWith("blob:http");
    }

    private static boolean matchesAnyRule(String lowerUrl, List<String> rules) {
        if (rules == null || rules.isEmpty()) {
            return false;
        }
        for (String rule : rules) {
            if (TextUtils.isEmpty(rule)) {
                continue;
            }
            try {
                if (lowerUrl.matches(".*" + rule.toLowerCase() + ".*")) {
                    return true;
                }
            } catch (Exception ignored) {
                if (lowerUrl.contains(rule.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalizePotentialUrl(String value, String baseUrl) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String candidate = value.replace("\\/", "/").replace("&amp;", "&").trim();
        if (candidate.startsWith("\"") && candidate.endsWith("\"") && candidate.length() > 1) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        if (candidate.startsWith("//")) {
            return "https:" + candidate;
        }
        if (candidate.startsWith("http://") || candidate.startsWith("https://") || candidate.startsWith("blob:")) {
            return candidate;
        }
        if (candidate.startsWith("/") && !TextUtils.isEmpty(baseUrl)) {
            try {
                URL url = new URL(baseUrl);
                return url.getProtocol() + "://" + url.getHost() + candidate;
            } catch (Exception ignored) {
            }
        }
        return candidate;
    }

    private static String decodeJsString(String value) {
        if (TextUtils.isEmpty(value) || "null".equals(value)) {
            return "{}";
        }
        try {
            return new JSONArray("[" + value + "]").getString(0);
        } catch (JSONException e) {
            return value;
        }
    }

    @NonNull
    private static Map<String, String> jsonToMap(JSONObject object) {
        Map<String, String> map = new HashMap<>();
        if (object == null) {
            return map;
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, object.optString(key));
        }
        return map;
    }

    @NonNull
    private static java.util.ArrayList<String> jsonArrayToList(JSONArray array) {
        java.util.ArrayList<String> list = new java.util.ArrayList<>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i);
            if (!TextUtils.isEmpty(value)) {
                list.add(value);
            }
        }
        return list;
    }

    @NonNull
    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = bufferedInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    @NonNull
    private static String findCharset(String contentType) {
        if (contentType != null) {
            String[] segments = contentType.split(";");
            for (String segment : segments) {
                String trimmed = segment.trim().toLowerCase();
                if (trimmed.startsWith("charset=")) {
                    return trimmed.substring("charset=".length());
                }
            }
        }
        return "UTF-8";
    }

    private String errorJson(Exception exception) {
        JSONObject json = new JSONObject();
        try {
            json.put("ok", false);
            json.put("error", exception.getClass().getSimpleName() + ": " + exception.getMessage());
        } catch (JSONException ignored) {
        }
        return json.toString();
    }
}
