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
import java.util.Iterator;
import java.util.Map;
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
