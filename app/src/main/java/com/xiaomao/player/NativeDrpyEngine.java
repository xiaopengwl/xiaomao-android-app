package com.xiaomao.player;

import android.app.Activity;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NativeDrpyEngine {
    private final Activity activity;
    private final WebView webView;
    private final NativeSource source;
    private final String helperJs;
    private final ArrayList<Runnable> pendingActions = new ArrayList<>();
    private String lastResult = "";
    private String currentHost = "";
    private final LinkedHashMap<String, String> cookieJar = new LinkedHashMap<>();
    private boolean ready = false;
    private boolean released = false;

    public interface Callback<T> {
        void done(T data, String err);
    }

    public static final class Category {
        public final String id;
        public final String name;
        public final String url;

        Category(String id, String name, String url) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
        }
    }

    public static final class MediaItem {
        public final String id;
        public final String vodId;
        public final String title;
        public final String poster;
        public final String remark;
        public final String url;

        MediaItem(String id, String vodId, String title, String poster, String remark, String url) {
            this.id = id == null ? "" : id;
            this.vodId = vodId == null ? "" : vodId;
            this.title = title == null ? "" : title;
            this.poster = poster == null ? "" : poster;
            this.remark = remark == null ? "" : remark;
            this.url = url == null ? "" : url;
        }
    }

    public static final class EpisodeItem {
        public final String name;
        public final String url;

        EpisodeItem(String name, String url) {
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
        }
    }

    public static final class EpisodeGroup {
        public final String name;
        public final ArrayList<EpisodeItem> items;

        EpisodeGroup(String name, ArrayList<EpisodeItem> items) {
            this.name = name == null ? "" : name;
            this.items = items == null ? new ArrayList<>() : items;
        }
    }

    public static final class MediaDetail {
        public final String vodId;
        public final String title;
        public final String poster;
        public final String remark;
        public final String content;
        public final ArrayList<EpisodeGroup> playGroups;

        MediaDetail(String vodId, String title, String poster, String remark, String content, ArrayList<EpisodeGroup> playGroups) {
            this.vodId = vodId == null ? "" : vodId;
            this.title = title == null ? "" : title;
            this.poster = poster == null ? "" : poster;
            this.remark = remark == null ? "" : remark;
            this.content = content == null ? "" : content;
            this.playGroups = playGroups == null ? new ArrayList<>() : playGroups;
        }
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

    public NativeDrpyEngine(Activity activity, NativeSource source) {
        this.activity = activity;
        this.source = source;
        this.currentHost = source == null ? "" : source.host;
        this.helperJs = readAssetText("runtime/native_rule_runtime.js");
        this.webView = new WebView(activity);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                ready = true;
                flushPendingActions();
            }
        });
        webView.loadDataWithBaseURL(
                currentHost.isEmpty() ? "https://appassets.androidplatform.net/" : currentHost,
                "<html><body>native-runtime</body></html>",
                "text/html",
                "utf-8",
                null
        );
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

    public void loadCategories(Callback<ArrayList<Category>> callback) {
        String body = ""
                + "__xmRunPreprocess();"
                + "var out=[];"
                + "if(rule.class_name && rule.class_url){"
                + "var names=String(rule.class_name).split('&');"
                + "var urls=String(rule.class_url).split('&');"
                + "for(var i=0;i<names.length;i++){out.push({id:'class-'+i,name:names[i]||'',url:urls[i]||''});}"
                + "}else if(rule.class_parse){"
                + "var html=request(rule.host,{headers:(rule.headers||{})});"
                + "out=__xmParseCategoriesByClassParse(rule.class_parse, html, rule.host||HOST);"
                + "}"
                + "Android.setResult(JSON.stringify({categories:out}));";
        runJsonRule("", body, (json, err) -> {
            if (!err.isEmpty()) {
                callback.done(new ArrayList<>(), err);
                return;
            }
            callback.done(parseCategories(json), parseRuleError(json));
        });
    }

    public void loadRecommend(int page, Callback<ArrayList<MediaItem>> callback) {
        int targetPage = Math.max(1, page);
        String body = ""
                + "var page=" + targetPage + ";"
                + "MY_PAGE=page;"
                + "__xmRunPreprocess();"
                + "var cfg=__xmGetRuleValue(rule,__xmRuleKeys.recommend);"
                + "if(!cfg){Android.setResult(JSON.stringify({items:[]}));}"
                + "else if(typeof cfg==='string' && cfg.indexOf('js:')===0){"
                + "input=__xmBuildRecommendUrl(rule,page);"
                + "document.html=request(input,{headers:(rule.headers||{})});"
                + "var code=String(cfg).substring(3);"
                + "var VODS=[];var d=[];var result=[];"
                + "eval(code);"
                + "Android.setResult(JSON.stringify({items:__xmNormalizeItems(__xmPickListResult(VODS,d,result,input), rule.host||HOST)}));"
                + "}else{"
                + "var url=__xmBuildRecommendUrl(rule,page);"
                + "var html=request(url,{headers:(rule.headers||{})});"
                + "Android.setResult(JSON.stringify({items:__xmParseListBySelector(cfg, html, rule.host||HOST)}));"
                + "}";
        runJsonRule("", body, (json, err) -> {
            if (!err.isEmpty()) {
                callback.done(new ArrayList<>(), err);
                return;
            }
            callback.done(parseMediaItems(json), parseRuleError(json));
        });
    }

    public void loadCategoryItems(String categoryUrl, int page, Callback<ArrayList<MediaItem>> callback) {
        int targetPage = Math.max(1, page);
        String safeCategory = categoryUrl == null ? "" : categoryUrl;
        String body = ""
                + "var page=" + targetPage + ";"
                + "MY_PAGE=page;"
                + "__xmRunPreprocess();"
                + "var cfg=__xmGetRuleValue(rule,__xmRuleKeys.first);"
                + "var target=__xmBuildCategoryUrl(rule," + quote(safeCategory) + ",page);"
                + "if(!cfg){Android.setResult(JSON.stringify({items:[]}));}"
                + "else if(typeof cfg==='string' && cfg.indexOf('js:')===0){"
                + "input=target;"
                + "document.html=request(target,{headers:(rule.headers||{})});"
                + "var code=String(cfg).substring(3);"
                + "var VODS=[];var d=[];var result=[];"
                + "eval(code);"
                + "Android.setResult(JSON.stringify({items:__xmNormalizeItems(__xmPickListResult(VODS,d,result,input), rule.host||HOST)}));"
                + "}else{"
                + "var html=request(target,{headers:(rule.headers||{})});"
                + "Android.setResult(JSON.stringify({items:__xmParseListBySelector(cfg, html, rule.host||HOST)}));"
                + "}";
        runJsonRule("", body, (json, err) -> {
            if (!err.isEmpty()) {
                callback.done(new ArrayList<>(), err);
                return;
            }
            callback.done(parseMediaItems(json), parseRuleError(json));
        });
    }

    public void search(String keyword, int page, Callback<ArrayList<MediaItem>> callback) {
        int targetPage = Math.max(1, page);
        String safeKeyword = keyword == null ? "" : keyword;
        String body = ""
                + "var page=" + targetPage + ";"
                + "MY_PAGE=page;"
                + "__xmRunPreprocess();"
                + "var cfg=__xmGetRuleValue(rule,__xmRuleKeys.search);"
                + "var target=__xmBuildSearchUrl(rule," + quote(safeKeyword) + ",page);"
                + "if(!cfg){Android.setResult(JSON.stringify({items:[]}));}"
                + "else if(typeof cfg==='string' && cfg.indexOf('js:')===0){"
                + "input=target;"
                + "document.html=request(target,{headers:(rule.headers||{})});"
                + "var code=String(cfg).substring(3);"
                + "var VODS=[];var d=[];var result=[];"
                + "eval(code);"
                + "Android.setResult(JSON.stringify({items:__xmNormalizeItems(__xmPickListResult(VODS,d,result,input), rule.host||HOST)}));"
                + "}else{"
                + "var html=request(target,{headers:(rule.headers||{})});"
                + "Android.setResult(JSON.stringify({items:__xmParseListBySelector(cfg, html, rule.host||HOST)}));"
                + "}";
        runJsonRule("", body, (json, err) -> {
            if (!err.isEmpty()) {
                callback.done(new ArrayList<>(), err);
                return;
            }
            callback.done(parseMediaItems(json), parseRuleError(json));
        });
    }

    public void loadDetail(String itemUrl, String fallbackTitle, String fallbackPic, Callback<MediaDetail> callback) {
        String safeUrl = itemUrl == null ? "" : itemUrl;
        String safeTitle = fallbackTitle == null ? "" : fallbackTitle;
        String safePic = fallbackPic == null ? "" : fallbackPic;
        String body = ""
                + "var detailUrl=__xmAbsoluteUrl(" + quote(safeUrl) + ", rule.host||HOST);"
                + "__xmRunPreprocess();"
                + "var cfg=__xmGetRuleValue(rule,__xmRuleKeys.second);"
                + "if(typeof cfg==='string' && cfg.indexOf('js:')===0){"
                + "input=detailUrl;"
                + "document.html=request(detailUrl,{headers:(rule.headers||{})});"
                + "var code=String(cfg).substring(3);"
                + "var VOD=null;var result=null;"
                + "eval(code);"
                + "var payload=__xmPickDetailResult(VOD,(result&&result.VOD)?result.VOD:null,result);"
                + "Android.setResult(JSON.stringify({detail:__xmNormalizeDetail(payload, rule.host||HOST, " + quote(safeTitle) + ", " + quote(safePic) + ", detailUrl)}));"
                + "}else if(cfg && typeof cfg==='object'){"
                + "var html=request(detailUrl,{headers:(rule.headers||{})});"
                + "var payload2=__xmParseDetailObject(cfg, html, rule.host||HOST, detailUrl);"
                + "Android.setResult(JSON.stringify({detail:__xmNormalizeDetail(payload2, rule.host||HOST, " + quote(safeTitle) + ", " + quote(safePic) + ", detailUrl)}));"
                + "}else{"
                + "Android.setResult(JSON.stringify({detail:__xmNormalizeDetail({vod_id:detailUrl,vod_name:" + quote(safeTitle) + ",vod_pic:" + quote(safePic) + ",playGroups:[{name:'默认线路',items:[{name:'播放',url:detailUrl}]}]}, rule.host||HOST, " + quote(safeTitle) + ", " + quote(safePic) + ", detailUrl)}));"
                + "}";
        runJsonRule(safeUrl, body, (json, err) -> {
            if (!err.isEmpty()) {
                callback.done(new MediaDetail(safeUrl, safeTitle, safePic, "", err, new ArrayList<>()), err);
                return;
            }
            callback.done(parseMediaDetail(json, safeUrl, safeTitle, safePic), parseRuleError(json));
        });
    }

    public void runLazy(String input, Callback<LazyResult> callback) {
        String fallbackInput = input == null ? "" : input;
        String body = ""
                + "__xmRunPreprocess();"
                + "document.html=request(input,{headers:(rule.headers||{})});"
                + "var __xmLazyInput=String(input||'');"
                + "var code=rule['lazy']||'';"
                + "code=String(code);"
                + "if(code.indexOf('js:')===0)code=code.substring(3);"
                + "if(code.length>0){eval(code);}"
                + "var __xmLazyResolved=typeof input==='object'?String(input.url||''):String(input||'');"
                + "if(!__xmLazyResolved||__xmLazyResolved===__xmLazyInput){"
                + "var __xmAutoLazy=__xmBuildLazyFallback(document.html,__xmLazyInput,rule.host||HOST,(rule.play_headers||rule.headers||{}));"
                + "if(__xmAutoLazy){input=__xmAutoLazy;}"
                + "}"
                + "if(typeof input==='object'){"
                + "if(!input.header&&!input.headers)input.header=(rule.play_headers||rule.headers||{});"
                + "Android.setResult(JSON.stringify(input));"
                + "}else{"
                + "Android.setResult(JSON.stringify({url:String(input||''),parse:rule.play_parse?1:0,jx:0,header:(rule.play_headers||rule.headers||{})}));"
                + "}";
        runJsonRule(fallbackInput, body, (json, err) -> {
            if (!err.isEmpty()) {
                callback.done(new LazyResult(fallbackInput), err);
                return;
            }
            try {
                JSONObject object = new JSONObject(json);
                callback.done(parseLazyResult(object, fallbackInput), object.optString("error", ""));
            } catch (Exception e) {
                callback.done(new LazyResult(fallbackInput), e.toString());
            }
        });
    }

    public void release() {
        activity.runOnUiThread(() -> {
            released = true;
            pendingActions.clear();
            try {
                webView.removeJavascriptInterface("Android");
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.destroy();
            } catch (Exception ignored) {
            }
        });
    }

    private void runJsonRule(String input, String body, Callback<String> callback) {
        String js = "(function(){try{"
                + baseJs(input == null ? "" : input)
                + body
                + "}catch(e){Android.setResult(JSON.stringify({__xm_error:String(e)}));}})();";
        evaluateScript(js, callback);
    }

    private void evaluateScript(String script, Callback<String> callback) {
        withReady(() -> {
            if (released) {
                callback.done("", "engine released");
                return;
            }
            lastResult = "";
            webView.evaluateJavascript(script, value -> {
                String result = lastResult;
                if (TextUtils.isEmpty(result)) {
                    result = decodeJsString(value);
                }
                callback.done(result == null ? "" : result, "");
            });
        });
    }

    private void withReady(Runnable action) {
        activity.runOnUiThread(() -> {
            if (released) {
                return;
            }
            if (ready) {
                action.run();
            } else {
                pendingActions.add(action);
            }
        });
    }

    private void flushPendingActions() {
        ArrayList<Runnable> actions = new ArrayList<>(pendingActions);
        pendingActions.clear();
        for (Runnable action : actions) {
            action.run();
        }
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

    private ArrayList<Category> parseCategories(String json) {
        ArrayList<Category> list = new ArrayList<>();
        try {
            JSONObject object = new JSONObject(json);
            JSONArray array = object.optJSONArray("categories");
            if (array == null) {
                return list;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                list.add(new Category(
                        item.optString("id", "class-" + i),
                        item.optString("name", ""),
                        item.optString("url", "")
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private ArrayList<MediaItem> parseMediaItems(String json) {
        ArrayList<MediaItem> list = new ArrayList<>();
        try {
            JSONObject object = new JSONObject(json);
            JSONArray array = object.optJSONArray("items");
            if (array == null) {
                return list;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                list.add(new MediaItem(
                        item.optString("id", ""),
                        item.optString("vod_id", item.optString("url", "")),
                        item.optString("vod_name", item.optString("title", "")),
                        item.optString("vod_pic", item.optString("img", "")),
                        item.optString("vod_remarks", item.optString("desc", "")),
                        item.optString("url", item.optString("vod_id", ""))
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private MediaDetail parseMediaDetail(String json, String fallbackId, String fallbackTitle, String fallbackPic) {
        try {
            JSONObject object = new JSONObject(json);
            JSONObject detail = object.optJSONObject("detail");
            if (detail == null) {
                return new MediaDetail(fallbackId, fallbackTitle, fallbackPic, "", "", new ArrayList<>());
            }
            ArrayList<EpisodeGroup> groups = new ArrayList<>();
            JSONArray groupArray = detail.optJSONArray("playGroups");
            if (groupArray != null) {
                for (int i = 0; i < groupArray.length(); i++) {
                    JSONObject group = groupArray.optJSONObject(i);
                    if (group == null) {
                        continue;
                    }
                    ArrayList<EpisodeItem> items = new ArrayList<>();
                    JSONArray itemArray = group.optJSONArray("items");
                    if (itemArray != null) {
                        for (int j = 0; j < itemArray.length(); j++) {
                            JSONObject entry = itemArray.optJSONObject(j);
                            if (entry == null) {
                                continue;
                            }
                            items.add(new EpisodeItem(
                                    entry.optString("name", "播放 " + (j + 1)),
                                    entry.optString("url", "")
                            ));
                        }
                    }
                    groups.add(new EpisodeGroup(group.optString("name", "线路 " + (i + 1)), items));
                }
            }
            return new MediaDetail(
                    detail.optString("vod_id", fallbackId),
                    detail.optString("vod_name", fallbackTitle),
                    detail.optString("vod_pic", fallbackPic),
                    detail.optString("vod_remarks", ""),
                    detail.optString("vod_content", ""),
                    groups
            );
        } catch (Exception ignored) {
            return new MediaDetail(fallbackId, fallbackTitle, fallbackPic, "", "", new ArrayList<>());
        }
    }

    private String parseRuleError(String json) {
        try {
            JSONObject object = new JSONObject(json);
            String error = object.optString("__xm_error", "");
            if (!error.isEmpty()) {
                return error;
            }
            return object.optString("error", "");
        } catch (Exception ignored) {
            return "";
        }
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
                + jsRuntime()
                + helperJs
                + raw
                + "\n;";
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
        if (options == null || options.trim().isEmpty()) {
            return out;
        }
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
            if (!referer.isEmpty()) {
                out.referer = referer;
            }
            JSONObject headers = object.optJSONObject("headers");
            if (headers != null) {
                Iterator<String> keys = headers.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = headers.optString(key, "");
                    if (!value.isEmpty()) {
                        out.headers.put(key, value);
                    }
                }
            }
            if (out.userAgent.isEmpty() && out.headers.containsKey("User-Agent")) {
                out.userAgent = out.headers.get("User-Agent");
            }
            if (out.contentType.isEmpty() && out.headers.containsKey("Content-Type")) {
                out.contentType = out.headers.get("Content-Type");
            }
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
        String cookie = mergeCookieHeader(cookieJar.get(cookieHostKey(url)), defaultCookieForUrl(url));
        if (cookie != null && !cookie.isEmpty() && !opt.headers.containsKey("Cookie") && !opt.headers.containsKey("cookie")) {
            connection.setRequestProperty("Cookie", cookie);
        }
        for (Map.Entry<String, String> entry : opt.headers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        String method = opt.method == null || opt.method.trim().isEmpty() ? "GET" : opt.method.trim().toUpperCase();
        if ((!opt.body.isEmpty()) || "POST".equals(method)) {
            connection.setDoOutput(true);
            connection.setRequestMethod("POST".equals(method) ? "POST" : method);
            if (!opt.contentType.isEmpty()) {
                connection.setRequestProperty("Content-Type", opt.contentType);
            }
            if (!opt.body.isEmpty()) {
                connection.getOutputStream().write(opt.body.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            connection.setRequestMethod(method);
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (stream != null) {
            byte[] buffer = new byte[8192];
            int size;
            while ((size = stream.read(buffer)) > 0) {
                output.write(buffer, 0, size);
            }
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

    private String defaultCookieForUrl(String url) {
        String hostKey = cookieHostKey(url).toLowerCase();
        if (hostKey.contains("51cg1.com") || hostKey.contains("isppven.com") || hostKey.contains("51cg")) {
            return "user-choose=true";
        }
        return "";
    }

    private String mergeCookieHeader(String existing, String extra) {
        if ((existing == null || existing.isEmpty()) && (extra == null || extra.isEmpty())) {
            return "";
        }
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        for (String cookieHeader : new String[]{existing, extra}) {
            if (cookieHeader == null || cookieHeader.isEmpty()) {
                continue;
            }
            String[] parts = cookieHeader.split(";\\s*");
            for (String part : parts) {
                int index = part.indexOf('=');
                if (index > 0) {
                    merged.put(part.substring(0, index), part.substring(index + 1));
                }
            }
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private void storeCookies(String finalUrl, Map<String, List<String>> headerFields) {
        if (headerFields == null || headerFields.isEmpty()) {
            return;
        }
        String hostKey = cookieHostKey(finalUrl);
        if (hostKey.isEmpty()) {
            return;
        }
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        String existing = cookieJar.get(hostKey);
        if (existing != null && !existing.isEmpty()) {
            String[] pairs = existing.split(";\\s*");
            for (String pair : pairs) {
                int index = pair.indexOf('=');
                if (index > 0) {
                    merged.put(pair.substring(0, index), pair.substring(index + 1));
                }
            }
        }
        for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
            String key = entry.getKey();
            if (key == null || !"set-cookie".equalsIgnoreCase(key)) {
                continue;
            }
            for (String value : entry.getValue()) {
                if (value == null || value.isEmpty()) {
                    continue;
                }
                String pair = value.split(";", 2)[0];
                int index = pair.indexOf('=');
                if (index > 0) {
                    merged.put(pair.substring(0, index), pair.substring(index + 1));
                }
            }
        }
        if (!merged.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, String> entry : merged.entrySet()) {
                if (builder.length() > 0) {
                    builder.append("; ");
                }
                builder.append(entry.getKey()).append("=").append(entry.getValue());
            }
            cookieJar.put(hostKey, builder.toString());
        }
    }

    private String abs(String value) {
        if (value == null || value.isEmpty()) {
            return currentHost;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        if (value.startsWith("/")) {
            return currentHost + value;
        }
        return currentHost + "/" + value;
    }

    private String readAssetText(String path) {
        try (InputStream inputStream = activity.getAssets().open(path);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int size;
            while ((size = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, size);
            }
            return outputStream.toString("UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String first(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String quote(String value) {
        return JSONObject.quote(value == null ? "" : value);
    }

    private static String js(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n");
    }

    private static String decodeJsString(String value) {
        if (TextUtils.isEmpty(value) || "null".equals(value)) {
            return "";
        }
        try {
            return new JSONArray("[" + value + "]").getString(0);
        } catch (Exception ignored) {
            return value;
        }
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
}
