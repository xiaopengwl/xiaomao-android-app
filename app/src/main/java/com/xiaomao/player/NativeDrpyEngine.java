package com.xiaomao.player;

import android.app.Activity;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class NativeDrpyEngine {
    private final Activity activity;
    private final WebView webView;
    private final NativeSource source;
    private String lastResult = "";
    private String currentHost = "";
    private final LinkedHashMap<String, String> cookieJar = new LinkedHashMap<>();

    public interface Callback<T> {
        void done(T data, String err);
    }

    public NativeDrpyEngine(Activity activity, NativeSource source) {
        this.activity = activity;
        this.source = source;
        this.currentHost = source == null ? "" : source.host;
        this.webView = new WebView(activity);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.loadData("<html><body>drpy</body></html>", "text/html", "utf-8");
    }

    public class Bridge {
        @JavascriptInterface
        public String request(String url, String options) {
            try {
                HttpResult result = requestRaw(abs(url), parseHttpOptions(options));
                updateCurrentHost(result.finalUrl);
                return result.body;
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void setResult(String json) {
            lastResult = json == null ? "" : json;
        }

        @JavascriptInterface
        public String log(String value) {
            return value;
        }
    }

    public void runLazy(String input, Callback<LazyResult> callback) {
        lastResult = "";
        String js = "(function(){try{" + baseJs(input)
                + "document.html=request(input,{headers:(rule.headers||{})});"
                + "var code=rule['lazy']||'';code=String(code);if(code.indexOf('js:')===0)code=code.substring(3);"
                + "if(code.length>0){eval(code);}"
                + "if(typeof input==='object'){if(!input.header&&!input.headers)input.header=(rule.play_headers||rule.headers||{});Android.setResult(JSON.stringify(input));}"
                + "else{Android.setResult(JSON.stringify({url:String(input||''),parse:rule.play_parse?1:0,jx:0,header:(rule.play_headers||rule.headers||{})}));}return 'ok';"
                + "}catch(e){Android.setResult(JSON.stringify({url:" + quote(input) + ",error:String(e)}));return 'err';}})()";
        webView.evaluateJavascript(js, value -> {
            try {
                JSONObject object = new JSONObject(lastResult);
                callback.done(parseLazyResult(object, input), object.optString("error", ""));
            } catch (Exception e) {
                callback.done(new LazyResult(input), e.toString());
            }
        });
    }

    private LazyResult parseLazyResult(JSONObject object, String fallbackInput) {
        LazyResult result = new LazyResult(object.optString("url", fallbackInput));
        result.error = object.optString("error", "");
        result.parse = object.optInt("parse", 0);
        result.jx = object.optInt("jx", 0);
        JSONObject headerObject = object.optJSONObject("header");
        if (headerObject == null) {
            headerObject = object.optJSONObject("headers");
        }
        if (headerObject != null) {
            Iterator<String> keys = headerObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = headerObject.optString(key, "");
                if (!TextUtils.isEmpty(value)) {
                    result.headers.put(key, value);
                }
            }
        }
        return result;
    }

    private String baseJs(String input) {
        String raw = source == null || source.raw == null ? "" : source.raw;
        String host = source == null ? "" : source.host;
        return "var input=" + quote(input) + ";var MY_PAGE=1;var MY_PAGECOUNT=999;var MY_TOTAL=99999;"
                + "var MOBILE_UA='Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36';"
                + "var PC_UA='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36';"
                + "var HOST='" + js(host) + "';var rule_fetch_params={headers:{}};var fetch_params={headers:{}};"
                + "var document={html:''};var localStore={};"
                + "function getItem(k){return localStore[k]||'';}function setItem(k,v){localStore[k]=String(v||'');}"
                + "var jsp={};var $={};var $js={toString:function(fn){var s=String(fn);var a=s.indexOf('{'),b=s.lastIndexOf('}');return 'js:'+(a>=0&&b>a?s.substring(a+1,b):s);}};"
                + "function mergeHeaders(){var out={},arr=[rule&&rule.headers,rule_fetch_params&&rule_fetch_params.headers,fetch_params&&fetch_params.headers];for(var i=0;i<arr.length;i++){var hs=arr[i]||{};for(var k in hs){if(Object.prototype.hasOwnProperty.call(hs,k)&&hs[k]!=null&&String(hs[k]).length>0)out[k]=String(hs[k]);}}return out;}"
                + "function mergeReqOpt(opt){var cfg=opt&&typeof opt==='object'?opt:{};var merged={};for(var k in cfg){if(Object.prototype.hasOwnProperty.call(cfg,k))merged[k]=cfg[k];}merged.headers=mergeHeaders();if(cfg.headers){for(var hk in cfg.headers){if(Object.prototype.hasOwnProperty.call(cfg.headers,hk))merged.headers[hk]=String(cfg.headers[hk]);}}if(!merged.headers['User-Agent'])merged.headers['User-Agent']=MOBILE_UA;return merged;}"
                + "function request(url,opt){var cfg=mergeReqOpt(opt||{});var r=Android.request(String(url||''),JSON.stringify(cfg||{}));document.html=r;return r;}"
                + "function requestRaw(url,opt){return request(url,opt);}function fetch(u,o){return request(u,o);}function post(u,o){return request(u,o);}function getHtml(u,o){return request(u,o);}"
                + "function setResult(v){Android.setResult(JSON.stringify(v||[]));}function setResult2(v){setResult(v);}function log(v){Android.log(String(v));}"
                + jsRuntime() + raw + "\n;";
    }

    private static String jsRuntime() {
        return "if(!String.prototype.strip){String.prototype.strip=function(){return String(this).trim();};}"
                + "function normalizeUrl(rel,base){try{return new URL(String(rel||''), String(base||rule.host||HOST)).toString();}catch(e){rel=String(rel||'');if(/^https?:/i.test(rel))return rel;if(rel.indexOf('//')===0)return 'https:'+rel;if(rel.charAt(0)==='/')return String(base||rule.host||HOST).replace(/\\/$/,'')+rel;return String(base||rule.host||HOST).replace(/\\/$/,'')+'/'+rel.replace(/^\\//,'');}}"
                + "function absu(u,base){u=String(u||'');if(!u)return '';return normalizeUrl(u, base||rule.host||HOST);}"
                + "function buildUrl(u,base){return absu(u,base);}function urljoin(a,b){return absu(b||a,a||rule.host||HOST);}function getHome(){return rule.host||HOST;}function getHost(){return rule.host||HOST;}"
                + "function realInput(k){return absu(input&&input!=='/'?input:(rule.homeUrl||rule.host), rule.host||HOST);}";
    }

    private HttpOptions parseHttpOptions(String options) {
        HttpOptions out = new HttpOptions();
        out.referer = currentHost + "/";
        if (options == null || options.trim().isEmpty()) return out;
        try {
            JSONObject object = new JSONObject(options);
            out.method = object.optString("method", out.method);
            out.body = first(object, "body", "data", "postData");
            out.userAgent = first(object, "ua", "userAgent", "User-Agent");
            out.contentType = first(object, "contentType", "mime", "Content-Type");
            int timeout = object.optInt("timeout", 0);
            if (timeout > 0) {
                out.connectTimeout = timeout;
                out.readTimeout = timeout;
            }
            String referer = first(object, "referer", "Referer");
            if (!referer.isEmpty()) out.referer = referer;
            JSONObject headers = object.optJSONObject("headers");
            if (headers != null) {
                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = headers.optString(key, "");
                    if (!value.isEmpty()) out.headers.put(key, value);
                }
            }
            if (out.userAgent.isEmpty() && out.headers.containsKey("User-Agent")) out.userAgent = out.headers.get("User-Agent");
            if (out.contentType.isEmpty() && out.headers.containsKey("Content-Type")) out.contentType = out.headers.get("Content-Type");
        } catch (Exception ignored) {
        }
        return out;
    }

    private HttpResult requestRaw(String url, HttpOptions options) throws Exception {
        HttpOptions opt = options == null ? new HttpOptions() : options;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(opt.connectTimeout);
        connection.setReadTimeout(opt.readTimeout);
        String userAgent = opt.userAgent.isEmpty()
                ? "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
                : opt.userAgent;
        connection.setRequestProperty("User-Agent", userAgent);
        connection.setRequestProperty("Referer", opt.referer.isEmpty() ? currentHost + "/" : opt.referer);
        String cookie = cookieJar.get(cookieHostKey(url));
        if (cookie != null && !cookie.isEmpty() && !opt.headers.containsKey("Cookie") && !opt.headers.containsKey("cookie")) {
            connection.setRequestProperty("Cookie", cookie);
        }
        for (Map.Entry<String, String> entry : opt.headers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) continue;
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        String method = opt.method == null || opt.method.trim().isEmpty() ? "GET" : opt.method.trim().toUpperCase();
        if ((!opt.body.isEmpty()) || "POST".equals(method)) {
            connection.setDoOutput(true);
            connection.setRequestMethod("POST".equals(method) ? "POST" : method);
            if (!opt.contentType.isEmpty()) connection.setRequestProperty("Content-Type", opt.contentType);
            if (!opt.body.isEmpty()) connection.getOutputStream().write(opt.body.getBytes("UTF-8"));
        } else {
            connection.setRequestMethod(method);
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (stream != null) {
            byte[] buffer = new byte[8192];
            int size;
            while ((size = stream.read(buffer)) > 0) output.write(buffer, 0, size);
            stream.close();
        }
        HttpResult result = new HttpResult();
        result.body = output.toString("UTF-8");
        result.finalUrl = connection.getURL().toString();
        result.contentType = connection.getContentType() == null ? "" : connection.getContentType();
        storeCookies(result.finalUrl, connection.getHeaderFields());
        return result;
    }

    private void updateCurrentHost(String url) {
        try {
            URL parsed = new URL(url);
            currentHost = parsed.getProtocol() + "://" + parsed.getHost();
        } catch (Exception ignored) {
        }
    }

    private String cookieHostKey(String url) {
        try {
            URL parsed = new URL(url);
            return parsed.getHost();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void storeCookies(String finalUrl, Map<String, List<String>> headerFields) {
        if (headerFields == null || headerFields.isEmpty()) return;
        String hostKey = cookieHostKey(finalUrl);
        if (hostKey.isEmpty()) return;
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        String existing = cookieJar.get(hostKey);
        if (existing != null && !existing.isEmpty()) {
            String[] pairs = existing.split(";\\s*");
            for (String pair : pairs) {
                int index = pair.indexOf('=');
                if (index > 0) merged.put(pair.substring(0, index), pair.substring(index + 1));
            }
        }
        for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
            String key = entry.getKey();
            if (key == null || !"set-cookie".equalsIgnoreCase(key)) continue;
            for (String value : entry.getValue()) {
                if (value == null || value.isEmpty()) continue;
                String pair = value.split(";", 2)[0];
                int index = pair.indexOf('=');
                if (index > 0) merged.put(pair.substring(0, index), pair.substring(index + 1));
            }
        }
        if (!merged.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, String> entry : merged.entrySet()) {
                if (builder.length() > 0) builder.append("; ");
                builder.append(entry.getKey()).append("=").append(entry.getValue());
            }
            cookieJar.put(hostKey, builder.toString());
        }
    }

    private String abs(String value) {
        if (value == null || value.isEmpty()) return currentHost;
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        if (value.startsWith("//")) return "https:" + value;
        if (value.startsWith("/")) return currentHost + value;
        return currentHost + "/" + value;
    }

    private static String first(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "");
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String quote(String value) {
        return JSONObject.quote(value == null ? "" : value);
    }

    private static String js(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    static final class HttpOptions {
        String method = "GET";
        String body = "";
        String referer = "";
        String userAgent = "";
        String contentType = "";
        int connectTimeout = 15000;
        int readTimeout = 15000;
        LinkedHashMap<String, String> headers = new LinkedHashMap<>();
    }

    static final class HttpResult {
        String body = "";
        String finalUrl = "";
        String contentType = "";
    }

    public static final class LazyResult {
        public String url;
        public String error = "";
        public int parse = 0;
        public int jx = 0;
        public final LinkedHashMap<String, String> headers = new LinkedHashMap<>();

        LazyResult(String url) {
            this.url = url == null ? "" : url;
        }
    }
}
