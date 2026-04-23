package com.xiaomao.player;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.view.WindowManager;

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
import java.util.ArrayDeque;
import java.util.ArrayList;
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
    private static final int DEFAULT_SNIFF_MAX_DEPTH = 3;
    private WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private float playerBrightness = 0.65f;

    private static final class SniffTarget {
        final String url;
        final int depth;
        final String source;

        SniffTarget(String url, int depth, String source) {
            this.url = url;
            this.depth = depth;
            this.source = source;
        }
    }

    private static final class SniffCandidate {
        final String url;
        final String source;
        final String pageUrl;
        final int depth;
        int score;

        SniffCandidate(String url, String source, String pageUrl, int depth, int score) {
            this.url = url;
            this.source = source;
            this.pageUrl = pageUrl;
            this.depth = depth;
            this.score = score;
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        playerBrightness = readCurrentBrightness();
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
        public void openNativePlayer(String payload) {
            try {
                JSONObject json = new JSONObject(payload);
                Intent intent = new Intent(MainActivity.this, NativePlayerActivity.class);
                intent.putExtra("title", json.optString("title"));
                intent.putExtra("series_title", json.optString("seriesTitle"));
                intent.putExtra("line", json.optString("line"));
                intent.putExtra("input", json.optString("input"));
                intent.putExtra("source_title", json.optString("sourceTitle"));
                intent.putExtra("source_host", json.optString("sourceHost"));
                intent.putExtra("source_raw", json.optString("sourceRaw"));
                intent.putStringArrayListExtra("episode_names", new java.util.ArrayList<>(jsonArrayToList(json.optJSONArray("episodeNames"))));
                intent.putStringArrayListExtra("episode_inputs", new java.util.ArrayList<>(jsonArrayToList(json.optJSONArray("episodeInputs"))));
                intent.putExtra("episode_index", json.optInt("episodeIndex", 0));
                startActivity(intent);
            } catch (Exception e) {
                toast("Open native player failed: " + e.getMessage());
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

        @JavascriptInterface
        public String getPlayerState() {
            JSONObject json = new JSONObject();
            try {
                json.put("brightness", readCurrentBrightness());
                json.put("volume", readCurrentVolume());
                json.put("orientation", isLandscape() ? "landscape" : "portrait");
            } catch (JSONException ignored) {
            }
            return json.toString();
        }

        @JavascriptInterface
        public double setPlayerBrightness(double value) {
            float clamped = clamp01((float) value, 0.08f);
            playerBrightness = clamped;
            mainHandler.post(() -> {
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.screenBrightness = clamped;
                getWindow().setAttributes(params);
            });
            return clamped;
        }

        @JavascriptInterface
        public double setPlayerVolume(double value) {
            if (audioManager == null) {
                return value;
            }
            float clamped = clamp01((float) value, 0f);
            int maxVolume = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int targetVolume = Math.min(maxVolume, Math.max(0, Math.round(clamped * maxVolume)));
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0);
            return readCurrentVolume();
        }

        @JavascriptInterface
        public String togglePlayerOrientation() {
            final AtomicReference<String> result = new AtomicReference<>(isLandscape() ? "landscape" : "portrait");
            final CountDownLatch latch = new CountDownLatch(1);
            mainHandler.post(() -> {
                boolean landscape = isLandscape();
                setRequestedOrientation(landscape
                        ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                result.set(landscape ? "portrait" : "landscape");
                latch.countDown();
            });
            try {
                latch.await(600, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return result.get();
        }
    }

    private void toast(String message) {
        mainHandler.post(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private float readCurrentBrightness() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        if (params.screenBrightness >= 0f) {
            playerBrightness = clamp01(params.screenBrightness, 0.08f);
            return playerBrightness;
        }
        return playerBrightness;
    }

    private double readCurrentVolume() {
        if (audioManager == null) {
            return 1.0d;
        }
        int maxVolume = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int currentVolume = Math.max(0, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
        return Math.min(1.0d, Math.max(0.0d, currentVolume / (double) maxVolume));
    }

    private float clamp01(float value, float min) {
        return Math.max(min, Math.min(1f, value));
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
        final int maxDepth = Math.max(0, Math.min(request.optInt("maxDepth", DEFAULT_SNIFF_MAX_DEPTH), 4));
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
            final Set<String> visitedPages = new HashSet<>();
            final ArrayDeque<SniffTarget> pendingTargets = new ArrayDeque<>();
            final ArrayList<SniffCandidate> candidates = new ArrayList<>();
            final AtomicReference<SniffTarget> currentTarget = new AtomicReference<>();
            final Runnable[] timeoutHolder = new Runnable[1];
            final Runnable[] advanceHolder = new Runnable[1];
            final Runnable[] pageIdleHolder = new Runnable[1];
            final Runnable[] candidateSettleHolder = new Runnable[1];

            enqueueSniffTarget(pendingTargets, startUrl, 0, "root", maxDepth, excludeRules);

            candidateSettleHolder[0] = () -> completeBestSniffCandidate(
                    sniffView,
                    finished,
                    latch,
                    resultRef,
                    timeoutHolder[0],
                    candidates
            );

            timeoutHolder[0] = () -> {
                if (completeBestSniffCandidate(sniffView, finished, latch, resultRef, timeoutHolder[0], candidates)) {
                    return;
                }
                completeSniffResult(
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
            };

            pageIdleHolder[0] = () -> {
                if (!finished.get()) {
                    advanceHolder[0].run();
                }
            };

            advanceHolder[0] = () -> {
                if (finished.get()) {
                    return;
                }

                SniffTarget nextTarget = null;
                while (!pendingTargets.isEmpty() && nextTarget == null) {
                    SniffTarget target = pendingTargets.poll();
                    if (target == null || TextUtils.isEmpty(target.url)) {
                        continue;
                    }
                    if (visitedPages.add(target.url)) {
                        nextTarget = target;
                    }
                }

                if (nextTarget == null) {
                    if (completeBestSniffCandidate(sniffView, finished, latch, resultRef, timeoutHolder[0], candidates)) {
                        return;
                    }
                    completeSniffResult(
                            sniffView,
                            finished,
                            latch,
                            resultRef,
                            timeoutHolder[0],
                            "",
                            false,
                            "not_found",
                            sniffView.getUrl(),
                            "No media resource matched"
                    );
                    return;
                }

                currentTarget.set(nextTarget);
                mainHandler.removeCallbacks(pageIdleHolder[0]);
                if (headers.isEmpty()) {
                    sniffView.loadUrl(nextTarget.url);
                } else {
                    sniffView.loadUrl(nextTarget.url, headers);
                }
            };

            sniffView.setWebChromeClient(new WebChromeClient());
            sniffView.setWebViewClient(new WebViewClient() {
                private void maybeCapture(String candidate, String source) {
                    if (TextUtils.isEmpty(candidate)) {
                        return;
                    }
                    String normalized = normalizePotentialUrl(candidate, sniffView.getUrl());
                    if (TextUtils.isEmpty(normalized) || !seenUrls.add(normalized)) {
                        return;
                    }
                    if (looksLikeMediaUrl(normalized, matchRules, excludeRules)) {
                        SniffTarget target = currentTarget.get();
                        int depth = target == null ? 0 : target.depth;
                        int score = scoreSniffCandidate(normalized, source, sniffView.getUrl(), depth);
                        String pageUrl = sniffView.getUrl();
                        mainHandler.post(() -> {
                            if (finished.get()) {
                                return;
                            }
                            rememberSniffCandidate(candidates, normalized, source, pageUrl, depth, score);
                            mainHandler.removeCallbacks(candidateSettleHolder[0]);
                            mainHandler.postDelayed(candidateSettleHolder[0], score >= 130 ? 450 : 900);
                        });
                    }
                }

                private void maybeQueue(String candidate, String source, String baseUrl) {
                    SniffTarget target = currentTarget.get();
                    int nextDepth = target == null ? 1 : target.depth + 1;
                    String normalized = normalizePotentialUrl(candidate, baseUrl);
                    if (seenUrls.contains(normalized)) {
                        return;
                    }
                    enqueueSniffTarget(pendingTargets, normalized, nextDepth, source, maxDepth, excludeRules);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String candidate = request.getUrl().toString();
                    maybeCapture(candidate, "navigate");
                    maybeQueue(candidate, "navigate", view.getUrl());
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
                    mainHandler.removeCallbacks(pageIdleHolder[0]);
                    String js = "(function(){try{var out=[];var pages=[];var seen={};var seenPages={};"
                            + "function clickAdControls(){try{var sels=['.skip','.skip-btn','.skipad','.btn-skip','.ad-skip','.video-ad-skip','.close','.close-btn','.close-icon','.layui-layer-close','.icon-close','[class*=skip]','[class*=close]','[id*=skip]','[id*=close]'];"
                            + "for(var a=0;a<sels.length;a++){var nodes=document.querySelectorAll(sels[a]);for(var b=0;b<nodes.length;b++){var el=nodes[b];var text=((el.innerText||el.textContent||'')+' '+(el.value||'')).toLowerCase();if(!text||/skip|close|jump|跳过|关闭|继续播放|立即播放|进入播放|我已看完/.test(text)){try{el.click();}catch(e){}}}}"
                            + "var taps=document.querySelectorAll('button,a,div,span');for(var c=0;c<taps.length;c++){var item=taps[c];var label=((item.innerText||item.textContent||'')+' '+(item.value||'')).trim();if(label&&/跳过|关闭|继续播放|立即播放|进入播放|我已看完广告|skip|close/i.test(label)){try{item.click();}catch(e){}}}"
                            + "}catch(e){}}"
                            + "function add(u){u=String(u||'').trim();if(!u||seen[u])return;seen[u]=1;out.push(u);try{if(/%[0-9a-f]{2}/i.test(u)){var du=decodeURIComponent(u);if(du&&!seen[du]){seen[du]=1;out.push(du);}}}catch(e){}}"
                            + "function addPage(u){if(!u||seenPages[u])return;seenPages[u]=1;pages.push(u);}"
                            + "clickAdControls();"
                            + "var nodes=document.querySelectorAll('video,source,audio,iframe,embed');"
                            + "for(var i=0;i<nodes.length;i++){"
                            + "add(nodes[i].src);add(nodes[i].getAttribute('src'));add(nodes[i].getAttribute('data-src'));add(nodes[i].currentSrc);"
                            + "if(nodes[i].tagName==='IFRAME'){addPage(nodes[i].src);addPage(nodes[i].getAttribute('src'));addPage(nodes[i].getAttribute('data-src'));}"
                            + "}"
                            + "var attrs=['data-config','data-play','data-url','data-src','data-player','data-from','data-href','data-play-url','data-player-url','data-target'];"
                            + "var vids=document.querySelectorAll('[data-config],[data-play],[data-url],[data-src],[data-player],[data-from],[data-href],[data-play-url],[data-player-url],[data-target],a[href],iframe[src],iframe[data-src],script[src],[onclick]');"
                            + "for(var j=0;j<vids.length;j++){"
                            + "for(var k=0;k<attrs.length;k++){var val=vids[j].getAttribute(attrs[k]);add(val);addPage(val);}"
                            + "add(vids[j].href);addPage(vids[j].href);add(vids[j].src);addPage(vids[j].src);"
                            + "}"
                            + "var html=document.documentElement?document.documentElement.innerHTML:'';"
                            + "var patterns=["
                            + "/(?:url|playurl|video_url|video|src|play_url|playUrl)\\\\s*[:=]\\\\s*[\\\"']([^\\\"'<>\\\\s]+)[\\\"']/ig,"
                            + "/(?:player_?[a-z0-9]*)\\\\s*=\\\\s*\\\\{[\\\\s\\\\S]*?(?:url|src)\\\\s*[:=]\\\\s*[\\\"']([^\\\"']+)[\\\"'][\\\\s\\\\S]*?\\\\}/ig,"
                            + "/(?:player_aaaa|player_data|__PLAYER__|MacPlayerConfig)\\\\s*=\\\\s*\\\\{[\\\\s\\\\S]*?(?:url|src|link_next|parse|parse_api|play_url)\\\\s*[:=]\\\\s*[\\\"']([^\\\"']+)[\\\"'][\\\\s\\\\S]*?\\\\}/ig,"
                            + "/(?:thisUrl|video_src|videoUrl)\\\\s*[:=]\\\\s*[\\\"']([^\\\"']+)[\\\"']/ig,"
                            + "/src\\\\s*:\\\\s*[\\\"']([^\\\"']+)[\\\"']/ig,"
                            + "/[\\\"'](https?:\\\\/\\\\/[^\\\"']+?(?:m3u8|mp4|m4v|flv|mpd|webm)[^\\\"']*)[\\\"']/ig,"
                            + "/[\\\"']((?:https?:)?\\\\/\\\\/[^\\\"']+?(?:player|parse|api|iframe)[^\\\"']*)[\\\"']/ig,"
                            + "/[\\\"']((?:\\\\/|\\.\\\\/|\\.\\.\\\\/)[^\\\"']+?(?:player|parse|api)[^\\\"']*)[\\\"']/ig,"
                            + "/[\\\"'](https?:\\\\\\\\/\\\\\\\\/[^\\\"']+?(?:m3u8|mp4|m4v|flv|mpd|webm|player|parse)[^\\\"']*)[\\\"']/ig,"
                            + "/[\\\"'](%[0-9a-f]{2}[^\\\"']*(?:%6d%33%75%38|%6d%70%34)[^\\\"']*)[\\\"']/ig"
                            + "];"
                            + "for(var p=0;p<patterns.length;p++){var match;while((match=patterns[p].exec(html))){add(match[1]);addPage(match[1]);}}"
                            + "var framePattern=/<iframe[^>]+(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"']/ig;var frameMatch;"
                            + "while((frameMatch=framePattern.exec(html))){addPage(frameMatch[1]);}"
                            + "return JSON.stringify({urls:out,pages:pages,title:document.title||''});}catch(e){return JSON.stringify({error:String(e)})}})();";
                    view.evaluateJavascript(js, value -> {
                        try {
                            String decoded = decodeJsString(value);
                            JSONObject json = new JSONObject(decoded);
                            JSONArray urls = json.optJSONArray("urls");
                            if (urls != null) {
                                for (int i = 0; i < urls.length(); i++) {
                                    maybeCapture(urls.optString(i), "dom");
                                    if (finished.get()) {
                                        return;
                                    }
                                }
                            }
                            JSONArray pages = json.optJSONArray("pages");
                            if (pages != null) {
                                for (int i = 0; i < pages.length(); i++) {
                                    maybeQueue(pages.optString(i), "iframe", url);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        if (!finished.get()) {
                            mainHandler.postDelayed(pageIdleHolder[0], 2600);
                        }
                    });
                    super.onPageFinished(view, url);
                }
            });

            mainHandler.postDelayed(timeoutHolder[0], timeout);
            advanceHolder[0].run();
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
            if (found) {
                json.put("headers", buildSniffResultHeaders(mediaUrl, pageUrl));
            }
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

    private boolean completeBestSniffCandidate(
            WebView sniffView,
            AtomicBoolean finished,
            CountDownLatch latch,
            AtomicReference<JSONObject> resultRef,
            Runnable timeoutRunnable,
            ArrayList<SniffCandidate> candidates
    ) {
        if (finished.get() || candidates.isEmpty()) {
            return false;
        }
        SniffCandidate best = candidates.get(0);
        for (SniffCandidate candidate : candidates) {
            if (candidate.score > best.score) {
                best = candidate;
            }
        }
        completeSniffResult(
                sniffView,
                finished,
                latch,
                resultRef,
                timeoutRunnable,
                best.url,
                true,
                best.source,
                TextUtils.isEmpty(best.pageUrl) ? sniffView.getUrl() : best.pageUrl,
                ""
        );
        return true;
    }

    private static void rememberSniffCandidate(
            ArrayList<SniffCandidate> candidates,
            String url,
            String source,
            String pageUrl,
            int depth,
            int score
    ) {
        for (SniffCandidate candidate : candidates) {
            if (candidate.url.equals(url)) {
                candidate.score = Math.max(candidate.score, score);
                return;
            }
        }
        candidates.add(new SniffCandidate(url, source == null ? "" : source, pageUrl == null ? "" : pageUrl, depth, score));
    }

    private JSONObject buildSniffResultHeaders(String mediaUrl, String pageUrl) throws JSONException {
        JSONObject headers = new JSONObject();
        if (!TextUtils.isEmpty(pageUrl)) {
            headers.put("Referer", pageUrl);
        }
        headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 14; Xiaomao) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
        String cookies = mergeCookieStrings(collectCookieHeader(mediaUrl), collectCookieHeader(pageUrl));
        if (!TextUtils.isEmpty(cookies)) {
            headers.put("Cookie", cookies);
        }
        headers.put("Accept", "*/*");
        return headers;
    }

    private String collectCookieHeader(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        try {
            String cookie = CookieManager.getInstance().getCookie(url);
            return cookie == null ? "" : cookie.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String mergeCookieStrings(String... values) {
        java.util.LinkedHashMap<String, String> merged = new java.util.LinkedHashMap<>();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            String[] parts = value.split(";");
            for (String part : parts) {
                String item = part == null ? "" : part.trim();
                int split = item.indexOf('=');
                if (split <= 0) continue;
                String name = item.substring(0, split).trim();
                if (!TextUtils.isEmpty(name)) {
                    merged.put(name, item);
                }
            }
        }
        StringBuilder builder = new StringBuilder();
        for (String item : merged.values()) {
            if (builder.length() > 0) builder.append("; ");
            builder.append(item);
        }
        return builder.toString();
    }

    private static boolean looksLikeMediaUrl(String url, List<String> matchRules, List<String> excludeRules) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase();
        if (matchesAnyRule(lower, excludeRules)) {
            return false;
        }
        if (hasDirectMediaFingerprint(lower)) {
            return true;
        }
        return matchesAnyRule(lower, matchRules) && !isLikelyHtmlPage(lower);
    }

    private static int scoreSniffCandidate(String url, String source, String pageUrl, int depth) {
        String lower = url == null ? "" : url.toLowerCase();
        int score = 40 + mediaFingerprintScore(lower);
        if ("intercept".equalsIgnoreCase(source) || "resource".equalsIgnoreCase(source)) score += 18;
        if ("dom".equalsIgnoreCase(source)) score += 10;
        if ("navigate".equalsIgnoreCase(source)) score += 4;
        score -= Math.max(0, depth) * 7;
        if (!TextUtils.isEmpty(pageUrl) && sameHost(url, pageUrl)) score += 10;
        if (isLikelyNoiseMedia(lower)) score -= 70;
        if (lower.contains("preview") || lower.contains("sample") || lower.contains("sprite") || lower.contains("storyboard")) score -= 35;
        return score;
    }

    private static int mediaFingerprintScore(String lower) {
        if (lower.contains(".m3u8") || lower.contains("/m3u8") || lower.contains("application/vnd.apple.mpegurl")) return 90;
        if (lower.contains(".mp4") || lower.contains("video_mp4")) return 78;
        if (lower.contains(".flv") || lower.contains(".m4v") || lower.contains(".webm")) return 70;
        if (lower.contains(".mpd")) return 62;
        if (lower.contains("mime=video") || lower.contains("mime_type=video") || lower.contains("obj/tos")) return 66;
        return 35;
    }

    private static boolean isLikelyNoiseMedia(String lower) {
        return lower.contains("googleads")
                || lower.contains("doubleclick")
                || lower.contains("analytics")
                || lower.contains("tracker")
                || lower.contains("adsystem")
                || lower.contains("/ads/")
                || lower.contains("advert")
                || lower.contains("favicon");
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
        if (candidate.contains("%")) {
            try {
                String decoded = java.net.URLDecoder.decode(candidate, "UTF-8");
                if (!TextUtils.isEmpty(decoded)) {
                    candidate = decoded;
                }
            } catch (Exception ignored) {
            }
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

    private static boolean sameHost(String left, String right) {
        try {
            URL leftUrl = new URL(left);
            URL rightUrl = new URL(right);
            return leftUrl.getHost().equalsIgnoreCase(rightUrl.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void enqueueSniffTarget(
            ArrayDeque<SniffTarget> pendingTargets,
            String candidate,
            int depth,
            String source,
            int maxDepth,
            List<String> excludeRules
    ) {
        if (depth > maxDepth) {
            return;
        }
        String normalized = normalizePotentialUrl(candidate, "");
        if (!shouldFollowSniffPage(normalized, excludeRules)) {
            return;
        }
        pendingTargets.offer(new SniffTarget(normalized, depth, source));
    }

    private static boolean shouldFollowSniffPage(String url, List<String> excludeRules) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase().trim();
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            return false;
        }
        if (lower.startsWith("javascript:") || lower.startsWith("mailto:") || lower.startsWith("tel:")) {
            return false;
        }
        if (matchesAnyRule(lower, excludeRules)) {
            return false;
        }
        return !hasDirectMediaFingerprint(lower);
    }

    private static boolean hasDirectMediaFingerprint(String lower) {
        return lower.contains(".m3u8")
                || lower.contains(".mp4")
                || lower.contains(".m4v")
                || lower.contains(".flv")
                || lower.contains(".mp3")
                || lower.contains(".mpd")
                || lower.contains(".webm")
                || lower.contains("mime=video")
                || lower.contains("mime_type=video")
                || lower.contains("video_mp4")
                || lower.contains("application/vnd.apple.mpegurl")
                || lower.startsWith("blob:http");
    }

    private static boolean isLikelyHtmlPage(String lower) {
        return lower.contains(".html")
                || lower.contains(".htm")
                || lower.contains(".php")
                || lower.contains(".asp")
                || lower.contains(".aspx")
                || lower.contains(".jsp")
                || lower.contains("=iframe")
                || lower.contains("type=iframe");
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
