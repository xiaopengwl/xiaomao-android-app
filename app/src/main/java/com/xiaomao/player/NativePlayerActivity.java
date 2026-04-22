package com.xiaomao.player;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.jzvd.Jzvd;
import cn.jzvd.JzvdStd;

public class NativePlayerActivity extends Activity {
    private JzvdStd playerView;
    private WebView sniffWeb;
    private View playerOverlay;
    private ProgressBar loading;
    private TextView titleView;
    private TextView lineView;
    private TextView stateView;
    private LinearLayout episodeWrap;

    private NativeSource source;
    private NativeDrpyEngine engine;
    private String title;
    private String line;
    private String input;
    private String playUrl;
    private boolean sniffing = false;
    private final java.util.LinkedHashMap<String, String> activeHeaders = new java.util.LinkedHashMap<>();

    private final ArrayList<String> episodeNames = new ArrayList<>();
    private final ArrayList<String> episodeInputs = new ArrayList<>();
    private int currentIndex = 0;
    private String seriesTitle = "晓鹏壳子";

    private final ArrayList<String> snifferMatchRules = new ArrayList<>();
    private final ArrayList<String> snifferExcludeRules = new ArrayList<>();
    private final ArrayList<String> snifferFollowRules = new ArrayList<>();
    private final ArrayList<String> snifferMediaRules = new ArrayList<>();
    private final ArrayList<SniffTask> sniffQueue = new ArrayList<>();
    private final HashSet<String> sniffVisited = new HashSet<>();
    private int maxSniffDepth = 4;
    private long sniffSessionId = 0L;
    private String sniffCurrentUrl = "";
    private int sniffCurrentDepth = 0;

    private final Handler handler = new Handler();
    private final Runnable hideState = () -> {
        if (playerOverlay != null && !sniffing) {
            playerOverlay.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        input = safe(getIntent().getStringExtra("input"));
        title = safe(getIntent().getStringExtra("title"));
        if (title.isEmpty()) title = "晓鹏壳子";
        line = safe(getIntent().getStringExtra("line"));
        if (line.isEmpty()) line = "默认线路";
        seriesTitle = safe(getIntent().getStringExtra("series_title"));
        if (seriesTitle.isEmpty()) {
            int split = title.indexOf(" · ");
            seriesTitle = split > 0 ? title.substring(0, split) : title;
        }
        ArrayList<String> names = getIntent().getStringArrayListExtra("episode_names");
        ArrayList<String> inputs = getIntent().getStringArrayListExtra("episode_inputs");
        if (names != null && inputs != null) {
            int size = Math.min(names.size(), inputs.size());
            for (int i = 0; i < size; i++) {
                episodeNames.add(safe(names.get(i)));
                episodeInputs.add(safe(inputs.get(i)));
            }
        }
        currentIndex = getIntent().getIntExtra("episode_index", 0);
        if (currentIndex < 0 || currentIndex >= episodeInputs.size()) currentIndex = 0;
        if (episodeInputs.isEmpty()) {
            episodeNames.add("播放");
            episodeInputs.add(input);
            currentIndex = 0;
        } else if (input.isEmpty()) {
            input = episodeInputs.get(currentIndex);
        }

        source = new NativeSource(
                getIntent().getStringExtra("source_title"),
                getIntent().getStringExtra("source_host"),
                getIntent().getStringExtra("source_raw")
        );
        engine = new NativeDrpyEngine(this, source);
        loadSnifferRules();
        buildUi();
        updateHeader();
        buildEpisodeButtons();
        resolveAndPlay();
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.parseColor("#090B10"));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(12), dp(10), dp(12), dp(10));
        nav.setBackgroundColor(Color.parseColor("#0B0F18"));
        page.addView(nav, new LinearLayout.LayoutParams(-1, dp(72)));

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(30);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setGravity(Gravity.CENTER);
        back.setBackground(cardBg("#171D2B", "#2D3548", 18));
        nav.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> onBackPressed());

        LinearLayout navText = new LinearLayout(this);
        navText.setOrientation(LinearLayout.VERTICAL);
        navText.setPadding(dp(10), 0, dp(10), 0);
        nav.addView(navText, new LinearLayout.LayoutParams(0, -1, 1));

        titleView = makeText("", 16, "#FFFFFF", true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        navText.addView(titleView, new LinearLayout.LayoutParams(-1, 0, 1));

        lineView = makeText("", 11, "#9EAFD6", false);
        lineView.setSingleLine(true);
        lineView.setEllipsize(TextUtils.TruncateAt.END);
        navText.addView(lineView, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView sourceTag = makeChip("原生播放", "#35141A", "#E75B68", "#FFE6EA");
        LinearLayout.LayoutParams sourceTagLp = new LinearLayout.LayoutParams(-2, dp(32));
        sourceTagLp.leftMargin = dp(6);
        nav.addView(sourceTag, sourceTagLp);

        FrameLayout playerBox = new FrameLayout(this);
        playerBox.setBackground(cardBg("#05070B", "#151B2A", 0));
        page.addView(playerBox, new LinearLayout.LayoutParams(-1, dp(232)));

        playerView = new JzvdStd(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerBox.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(18), dp(18), dp(18), dp(18));
        overlay.setClickable(false);
        overlay.setFocusable(false);
        playerOverlay = overlay;
        playerBox.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(this);
        overlay.addView(loading, new LinearLayout.LayoutParams(dp(38), dp(38)));

        stateView = makeText("正在解析播放地址…", 14, "#DDE5FF", false);
        stateView.setGravity(Gravity.CENTER);
        stateView.setPadding(dp(14), dp(14), dp(14), 0);
        overlay.addView(stateView, new LinearLayout.LayoutParams(-2, -2));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        page.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(18));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout heroCard = new LinearLayout(this);
        heroCard.setOrientation(LinearLayout.VERTICAL);
        heroCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        heroCard.setBackground(cardBg("#101521", "#222D42", 20));
        root.addView(heroCard, new LinearLayout.LayoutParams(-1, -2));

        heroCard.addView(makeText("原生解析播放", 18, "#FFFFFF", true));
        TextView tip = makeText("使用 kezi 风格的原生 lazy 解析和网页嗅探，拿到真实地址后交给 JZPlayer 播放。", 13, "#C9D4F4", false);
        tip.setPadding(0, dp(10), 0, 0);
        heroCard.addView(tip);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(12), 0, 0);
        heroCard.addView(chips);
        TextView lineChip = makeChip("线路 " + line, "#1A2337", "#3D4B72", "#DCE7FF");
        TextView sourceChip = makeChip("源 " + source.title, "#182717", "#2F7E57", "#D8FFE7");
        lineChip.setSingleLine(true);
        lineChip.setEllipsize(TextUtils.TruncateAt.END);
        sourceChip.setSingleLine(true);
        sourceChip.setEllipsize(TextUtils.TruncateAt.END);
        sourceChip.setMaxWidth(dp(220));
        LinearLayout.LayoutParams chip1 = new LinearLayout.LayoutParams(-2, dp(34));
        chip1.rightMargin = dp(8);
        chips.addView(lineChip, chip1);
        chips.addView(sourceChip, new LinearLayout.LayoutParams(-2, dp(34)));

        TextView episodeTitle = makeText("选集", 16, "#FFFFFF", true);
        episodeTitle.setPadding(0, dp(18), 0, dp(8));
        root.addView(episodeTitle);

        LinearLayout railCard = new LinearLayout(this);
        railCard.setOrientation(LinearLayout.VERTICAL);
        railCard.setPadding(dp(12), dp(10), dp(12), dp(12));
        railCard.setBackground(cardBg("#0F1420", "#28334D", 18));
        root.addView(railCard, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView scrollRail = new HorizontalScrollView(this);
        scrollRail.setHorizontalScrollBarEnabled(false);
        railCard.addView(scrollRail, new LinearLayout.LayoutParams(-1, dp(54)));

        episodeWrap = new LinearLayout(this);
        episodeWrap.setOrientation(LinearLayout.HORIZONTAL);
        episodeWrap.setPadding(0, dp(8), 0, 0);
        scrollRail.addView(episodeWrap, new HorizontalScrollView.LayoutParams(-2, -1));

        setContentView(page);
    }

    private void updateHeader() {
        String episodeName = currentIndex >= 0 && currentIndex < episodeNames.size() ? episodeNames.get(currentIndex) : "播放";
        titleView.setText(seriesTitle + " · " + episodeName);
        lineView.setText("线路: " + line + " · 源: " + source.title + " · 共 " + episodeInputs.size() + " 集 · 嗅探深度 " + maxSniffDepth);
        title = seriesTitle + " · " + episodeName;
    }

    private void buildEpisodeButtons() {
        episodeWrap.removeAllViews();
        for (int i = 0; i < episodeInputs.size(); i++) {
            final int index = i;
            TextView ep = new TextView(this);
            ep.setText(episodeNames.get(i).isEmpty() ? ("第" + (i + 1) + "集") : episodeNames.get(i));
            ep.setTextSize(13);
            ep.setTextColor(Color.parseColor(i == currentIndex ? "#FFFFFF" : "#DDE6FF"));
            ep.setTypeface(Typeface.DEFAULT_BOLD);
            ep.setGravity(Gravity.CENTER);
            ep.setSingleLine(true);
            ep.setEllipsize(TextUtils.TruncateAt.END);
            ep.setMaxWidth(dp(260));
            ep.setPadding(dp(16), 0, dp(16), 0);
            ep.setBackground(i == currentIndex ? cardBg("#E50914", "#FF5260", 18) : cardBg("#182033", "#33415F", 18));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(40));
            params.rightMargin = dp(8);
            episodeWrap.addView(ep, params);
            ep.setOnClickListener(v -> switchEpisode(index));
        }
    }

    private void switchEpisode(int index) {
        if (index < 0 || index >= episodeInputs.size() || index == currentIndex) return;
        currentIndex = index;
        input = safe(episodeInputs.get(index));
        updateHeader();
        buildEpisodeButtons();
        resolveAndPlay();
    }

    private WebView createSnifferWebView() {
        WebView web = new WebView(this);
        web.setVisibility(View.INVISIBLE);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
        web.addJavascriptInterface(new PlayerBridge(), "HermesPlayer");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) captureSniff(request.getUrl().toString(), sniffCurrentDepth, false);
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                captureSniff(url, sniffCurrentDepth, false);
                super.onLoadResource(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!sniffing) return;
                showState("Sniffing page resources...", true, 1f);
                probeCurrentPage(url, sniffCurrentDepth);
            }
        });
        return web;
    }

    private void resolveAndPlay() {
        releaseSniffer();
        playUrl = null;
        activeHeaders.clear();
        showState("Resolving play url...", true, 1f);
        if (source.raw != null && source.raw.contains("var rule")) {
            engine.runLazy(input, (result, err) -> runOnUiThread(() -> {
                if ((result == null || safe(result.url).isEmpty()) && err != null && !err.isEmpty()) {
                    showError("解析失败: " + err);
                } else {
                    if (result != null) {
                        activeHeaders.clear();
                        activeHeaders.putAll(result.headers);
                        startPlayer(result.url, result.parse == 1 || result.jx == 1);
                    } else {
                        startPlayer(input, false);
                    }
                }
            }));
            return;
        }
        startPlayer(input, false);
    }

    private void startPlayer(String url, boolean forceSniff) {
        String next = safe(url).isEmpty() ? input : safe(url);
        playUrl = next;
        if (playUrl.isEmpty()) {
            showError("未获取到可播放地址");
            return;
        }
        if (forceSniff || !looksLikeMedia(playUrl)) {
            startSniff(playUrl, "解析结果不是直链，正在按规则网页嗅探…");
            return;
        }
        playInPlace(playUrl);
    }

    private void playInPlace(String mediaUrl) {
        sniffing = false;
        releaseSniffer();
        playUrl = mediaUrl;
        showState("Loading player...", true, 1f);
        Jzvd.releaseAllVideos();
        try {
            Map<String, String> headers = parseHeaders(buildHeadersJson());
            if (!setupPlayerWithHeaders(headers)) {
                setupPlayerSimple();
            }
            playerView.startVideo();
            showReadyState();
        } catch (Throwable error) {
            Toast.makeText(this, "JZPlayer init failed, trying external player", Toast.LENGTH_SHORT).show();
            openExternalPlayer();
        }
    }

    private void startSniff(String pageUrl, String message) {
        if (safe(pageUrl).isEmpty()) {
            showError("没有可嗅探的页面地址");
            return;
        }
        sniffing = true;
        releaseSniffer();
        sniffQueue.clear();
        sniffVisited.clear();
        sniffCurrentUrl = "";
        sniffCurrentDepth = 0;
        sniffSessionId++;
        showState(message, true, 1f);
        sniffWeb = createSnifferWebView();
        addContentView(sniffWeb, new ViewGroup.LayoutParams(1, 1));
        loadSniffFrame(pageUrl, 0);
        final long token = sniffSessionId;
        handler.postDelayed(() -> {
            if (sniffing && token == sniffSessionId) showError("嗅探超时，请换线路再试");
        }, 22000);
    }

    private void loadSniffFrame(String pageUrl, int depth) {
        if (!sniffing || sniffWeb == null) return;
        String clean = safe(pageUrl);
        if (clean.isEmpty() || depth > maxSniffDepth) {
            processNextSniffTask();
            return;
        }
        String visitKey = depth + "::" + clean;
        if (sniffVisited.contains(visitKey)) {
            processNextSniffTask();
            return;
        }
        sniffVisited.add(visitKey);
        sniffCurrentUrl = clean;
        sniffCurrentDepth = depth;
        try {
            java.util.HashMap<String, String> headers = new java.util.HashMap<>(activeHeaders);
            if (!headers.containsKey("Referer") && source != null && !safe(source.host).isEmpty()) {
                headers.put("Referer", source.host + "/");
            }
            if (headers.isEmpty()) {
                sniffWeb.loadUrl(clean);
            } else {
                sniffWeb.loadUrl(clean, headers);
            }
        } catch (Exception ignored) {
            processNextSniffTask();
        }
    }

    private void enqueueSniffFrame(String url, int depth) {
        String clean = safe(url);
        if (clean.isEmpty() || depth > maxSniffDepth) return;
        String visitKey = depth + "::" + clean;
        if (sniffVisited.contains(visitKey)) return;
        for (SniffTask task : sniffQueue) {
            if (task.depth == depth && clean.equals(task.url)) return;
        }
        sniffQueue.add(new SniffTask(clean, depth));
    }

    private void processNextSniffTask() {
        if (!sniffing || sniffQueue.isEmpty()) return;
        SniffTask task = sniffQueue.remove(0);
        loadSniffFrame(task.url, task.depth);
    }

    private void probeCurrentPage(String url, int depth) {
        if (sniffWeb == null) return;
        String js = "(function(){try{var out=[];"
                + "function add(u,t){u=String(u||'').trim();if(!u)return;out.push({url:u,type:t||''});try{if(/%[0-9a-f]{2}/i.test(u)){var du=decodeURIComponent(u);if(du&&du!==u)out.push({url:du,type:(t||'')+'-decoded'});}}catch(e){}}"
                + "var vids=document.querySelectorAll('video,source,audio');for(var i=0;i<vids.length;i++){add(vids[i].currentSrc||vids[i].src,'video');add(vids[i].getAttribute('src'),'media');add(vids[i].getAttribute('data-src'),'media');}"
                + "var links=document.querySelectorAll('a[href],iframe[src],iframe[data-src],embed[src],object[data],[data-play],[data-url],[data-player],[data-play-url]');"
                + "for(var k=0;k<links.length;k++){var n=links[k];add(n.getAttribute('href')||n.getAttribute('src')||n.getAttribute('data')||n.getAttribute('data-play')||n.getAttribute('data-url')||n.getAttribute('data-player')||n.getAttribute('data-play-url'), n.tagName.toLowerCase());}"
                + "var html=document.documentElement?document.documentElement.outerHTML:'';"
                + "var regs=[/https?:\\/\\/[^\\s\"'<>]+/g,/(?:thisUrl|video_src|videoUrl)\\s*[:=]\\s*[\\\"']([^\\\"']+)[\\\"']/ig,/(?:player_aaaa|player_data|__PLAYER__|MacPlayerConfig)\\s*=\\s*\\{[\\s\\S]*?(?:url|src|video_url|parse_api)\\s*[:=]\\s*[\\\"']([^\\\"']+)[\\\"'][\\s\\S]*?\\}/ig,/src\\s*:\\s*[\\\"']([^\\\"']+)[\\\"']/ig,/[\\\"'](%[0-9a-f]{2}[^\\\"']*(?:%6d%33%75%38|%6d%70%34)[^\\\"']*)[\\\"']/ig];"
                + "for(var r=0;r<regs.length;r++){var m;while((m=regs[r].exec(html))){add(m[1]||m[0],'html');}}"
                + "HermesPlayer.onSniffResult(JSON.stringify(out)," + depth + "," + JSONObject.quote(url == null ? "" : url) + ");"
                + "}catch(e){HermesPlayer.onSniffResult('[]'," + depth + "," + JSONObject.quote(url == null ? "" : url) + ");}})();";
        sniffWeb.evaluateJavascript(js, null);
    }

    private void captureSniff(String url, int depth, boolean fromDom) {
        String normalized = normalizeSniffUrl(url, sniffCurrentUrl);
        if (!sniffing || !shouldSniffUrl(normalized)) return;
        if (looksLikeMedia(normalized)) {
            runOnUiThread(() -> {
                if (!sniffing) return;
                sniffing = false;
                showState("Media url found, starting playback...", true, 1f);
                playInPlace(normalized);
            });
            return;
        }
        if (!fromDom && !shouldFollowPage(normalized)) return;
        enqueueSniffFrame(normalized, depth + 1);
        runOnUiThread(this::processNextSniffTask);
    }

    private boolean looksLikeMedia(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("blob:") || lower.startsWith("data:")) return false;
        if (matchesRule(snifferMediaRules, lower)) return true;
        return lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".flv")
                || lower.contains(".mkv") || lower.contains(".mpd") || lower.contains("mime=video")
                || lower.contains("/m3u8") || lower.contains("video_mp4")
                || lower.contains("application/vnd.apple.mpegurl");
    }

    private boolean shouldFollowPage(String url) {
        if (safe(url).isEmpty()) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        if (looksLikeMedia(lower)) return true;
        if (lower.startsWith("javascript:")) return false;
        if (matchesRule(snifferFollowRules, lower)) return true;
        if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png")
                || lower.contains(".gif") || lower.contains(".webp") || lower.contains(".css")
                || lower.contains(".js") || lower.contains("favicon") || lower.contains(".svg")) return false;
        return lower.contains("iframe") || lower.contains("player") || lower.contains("play")
                || lower.contains("video") || lower.contains("?url=") || lower.contains("/v/") || lower.contains("/vod/");
    }

    private boolean shouldSniffUrl(String url) {
        if (safe(url).isEmpty()) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("blob:") || lower.startsWith("data:")) return false;
        if (matchesRule(snifferExcludeRules, lower)) return false;
        if (matchesRule(snifferMatchRules, lower)) return true;
        if (lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png")
                || lower.contains(".gif") || lower.contains(".webp") || lower.contains(".css")
                || lower.contains(".js") || lower.contains("favicon") || lower.contains("googleads")
                || lower.contains("doubleclick")) return false;
        return looksLikeMedia(lower) || shouldFollowPage(lower);
    }

    private boolean matchesRule(ArrayList<String> rules, String url) {
        for (String rule : rules) {
            if (rule == null || rule.trim().isEmpty()) continue;
            try {
                if (Pattern.compile(rule, Pattern.CASE_INSENSITIVE).matcher(url).find()) return true;
            } catch (Exception ignored) {
                if (url.contains(rule.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    private void loadSnifferRules() {
        snifferMatchRules.clear();
        snifferExcludeRules.clear();
        snifferFollowRules.clear();
        snifferMediaRules.clear();
        addRules(snifferMatchRules, parseRuleValues(source.raw, "sniffer_match"));
        addRules(snifferMatchRules, parseRuleValues(source.raw, "snifferMatch"));
        addRules(snifferExcludeRules, parseRuleValues(source.raw, "sniffer_exclude"));
        addRules(snifferExcludeRules, parseRuleValues(source.raw, "snifferExclude"));
        addRules(snifferFollowRules, parseRuleValues(source.raw, "sniffer_follow"));
        addRules(snifferFollowRules, parseRuleValues(source.raw, "snifferFollow"));
        addRules(snifferMediaRules, parseRuleValues(source.raw, "sniffer_media"));
        addRules(snifferMediaRules, parseRuleValues(source.raw, "snifferMedia"));
        if (snifferMatchRules.isEmpty()) addRules(snifferMatchRules, "m3u8", "mp4", "flv", "mpd", "player", "iframe", "video", "?url=", "player_aaaa", "player_data", "MacPlayerConfig", "parse_api", "thisUrl", "video_url", "obj/tos");
        if (snifferExcludeRules.isEmpty()) addRules(snifferExcludeRules, "googleads", "doubleclick", "favicon", ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg");
        if (snifferFollowRules.isEmpty()) addRules(snifferFollowRules, "iframe", "player", "play", "video", "?url=", "/v/", "/vod/", "parse", "api.php", "player_aaaa");
        if (snifferMediaRules.isEmpty()) addRules(snifferMediaRules, "m3u8", "mp4", "flv", "mkv", "mpd", "application/vnd.apple.mpegurl", "video_mp4", "mime=video", "mime_type=video", "obj/tos");
        String depth = pickRuleScalar(source.raw, "sniffer_depth");
        if (depth.isEmpty()) depth = pickRuleScalar(source.raw, "sniff_depth");
        try {
            maxSniffDepth = Math.min(6, Math.max(2, Integer.parseInt(depth.trim())));
        } catch (Exception ignored) {
            maxSniffDepth = 4;
        }
    }

    private ArrayList<String> parseRuleValues(String raw, String key) {
        ArrayList<String> out = new ArrayList<>();
        if (safe(raw).isEmpty()) return out;
        Matcher arrayMatcher = Pattern.compile(key + "\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(raw);
        while (arrayMatcher.find()) {
            Matcher stringMatcher = Pattern.compile("['\"`](.*?)['\"`]", Pattern.DOTALL).matcher(arrayMatcher.group(1));
            while (stringMatcher.find()) addRules(out, stringMatcher.group(1));
        }
        String scalar = pickRuleScalar(raw, key);
        if (!scalar.isEmpty()) {
            String[] parts = scalar.split("\\|\\||\\||,|&|\\n");
            for (String part : parts) addRules(out, part);
        }
        return out;
    }

    private String pickRuleScalar(String raw, String key) {
        if (safe(raw).isEmpty()) return "";
        Matcher matcher = Pattern.compile(key + "\\s*:\\s*(['\"`])(.*?)\\1", Pattern.DOTALL).matcher(raw);
        return matcher.find() ? matcher.group(2).trim() : "";
    }

    private String normalizeSniffUrl(String raw, String base) {
        String url = safe(raw);
        if (url.isEmpty()) return "";
        if (url.contains("%")) {
            try {
                url = java.net.URLDecoder.decode(url, "UTF-8");
            } catch (Exception ignored) {
            }
        }
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("javascript:") || url.startsWith("blob:") || url.startsWith("data:")) return url;
        String fallbackBase = safe(base).isEmpty() ? source.host : base;
        try {
            return new URL(new URL(fallbackBase), url).toString();
        } catch (Exception ignored) {
            if (!source.host.isEmpty()) {
                if (url.startsWith("/")) return source.host + url;
                return source.host + "/" + url;
            }
            return url;
        }
    }

    private void addRules(ArrayList<String> out, String... values) {
        LinkedHashSet<String> seen = new LinkedHashSet<>(out);
        for (String value : values) {
            String v = safe(value);
            if (!v.isEmpty()) seen.add(v);
        }
        out.clear();
        out.addAll(seen);
    }

    private void addRules(ArrayList<String> out, ArrayList<String> values) {
        addRules(out, values.toArray(new String[0]));
    }

    private boolean setupPlayerWithHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        try {
            Class<?> dataSourceClass = Class.forName("cn.jzvd.JZDataSource");
            Object dataSource = dataSourceClass.getConstructor(String.class, String.class).newInstance(playUrl, title);
            try {
                Field headerMapField = dataSourceClass.getField("headerMap");
                headerMapField.set(dataSource, headers);
            } catch (NoSuchFieldException ignored) {
                Method setHeaderMap = dataSourceClass.getMethod("setHeaderMap", HashMap.class);
                setHeaderMap.invoke(dataSource, new HashMap<>(headers));
            }
            Method setUp = playerView.getClass().getMethod("setUp", dataSourceClass, int.class);
            setUp.invoke(playerView, dataSource, Jzvd.SCREEN_NORMAL);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void setupPlayerSimple() throws Exception {
        try {
            Method method = playerView.getClass().getMethod("setUp", String.class, String.class);
            method.invoke(playerView, playUrl, title);
            return;
        } catch (NoSuchMethodException ignored) {
        }
        Method method = playerView.getClass().getMethod("setUp", String.class, String.class, int.class);
        method.invoke(playerView, playUrl, title, Jzvd.SCREEN_NORMAL);
    }

    private Map<String, String> parseHeaders(String rawJson) {
        Map<String, String> headers = new HashMap<>();
        if (safe(rawJson).isEmpty()) {
            return headers;
        }
        try {
            JSONObject jsonObject = new JSONObject(rawJson);
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = jsonObject.optString(key, "");
                if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                    headers.put(key, value);
                }
            }
        } catch (Exception ignored) {
        }
        return headers;
    }
    private void openExternalPlayer() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(playUrl), "video/*");
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No external player found", Toast.LENGTH_SHORT).show();
            showError("No external player found");
        }
    }

    private void showReadyState() {
        showState("Playing", false, 1f);
        handler.removeCallbacks(hideState);
        handler.postDelayed(hideState, 1200);
    }

    private void showState(String text, boolean showLoading, float alpha) {
        handler.removeCallbacks(hideState);
        if (playerOverlay != null) {
            playerOverlay.setVisibility(View.VISIBLE);
            playerOverlay.bringToFront();
        }
        loading.setVisibility(showLoading ? View.VISIBLE : View.GONE);
        stateView.setVisibility(View.VISIBLE);
        stateView.setAlpha(alpha);
        stateView.setText(text);
    }

    private void showError(String text) {
        sniffing = false;
        releaseSniffer();
        showState(text, false, 1f);
    }

    private void releaseSniffer() {
        sniffQueue.clear();
        sniffVisited.clear();
        if (sniffWeb != null) {
            try {
                ViewGroup parent = (ViewGroup) sniffWeb.getParent();
                if (parent != null) parent.removeView(sniffWeb);
                sniffWeb.stopLoading();
                sniffWeb.loadUrl("about:blank");
                sniffWeb.destroy();
            } catch (Exception ignored) {
            }
            sniffWeb = null;
        }
    }
    @Override
    public void onBackPressed() {
        if (Jzvd.backPress()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tryInvokeJzvd("goOnPlayOnResume");
        if (sniffWeb != null) sniffWeb.onResume();
    }

    @Override
    protected void onPause() {
        if (sniffWeb != null) sniffWeb.onPause();
        Jzvd.releaseAllVideos();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler.removeCallbacksAndMessages(null);
        releaseSniffer();
        Jzvd.releaseAllVideos();
        super.onDestroy();
    }

    private void tryInvokeJzvd(String methodName) {
        try {
            Method method = Jzvd.class.getMethod(methodName);
            method.invoke(null);
        } catch (Throwable ignored) {
        }
    }

    private TextView makeText(String text, int sp, String color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor(color));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView makeChip(String text, String bg, String stroke, String color) {
        TextView chip = makeText(text, 11, color, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), 0, dp(10), 0);
        chip.setBackground(cardBg(bg, stroke, 16));
        return chip;
    }

    private GradientDrawable cardBg(String color, String stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), Color.parseColor(stroke));
        return drawable;
    }

    private String buildHeadersJson() {
        try {
            JSONObject object = new JSONObject();
            for (Map.Entry<String, String> entry : activeHeaders.entrySet()) {
                if (entry.getKey() == null || safe(entry.getValue()).isEmpty()) continue;
                object.put(entry.getKey(), entry.getValue());
            }
            if (!object.has("Referer") && source != null && !safe(source.host).isEmpty()) {
                object.put("Referer", source.host + "/");
            }
            if (!object.has("User-Agent")) {
                object.put("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            }
            return object.toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private final class PlayerBridge {

        @JavascriptInterface
        public void onSniffResult(String payload, int depth, String pageUrl) {
            if (!sniffing) return;
            ArrayList<String> nested = new ArrayList<>();
            try {
                JSONArray array = new JSONArray(payload == null ? "[]" : payload);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    String url = object == null ? array.optString(i) : object.optString("url");
                    String type = object == null ? "" : object.optString("type");
                    if (looksLikeMedia(url) && shouldSniffUrl(url)) {
                        captureSniff(url, depth, true);
                        return;
                    }
                    if ("iframe".equalsIgnoreCase(type) || "embed".equalsIgnoreCase(type) || shouldFollowPage(url)) {
                        nested.add(url);
                    }
                }
            } catch (Exception ignored) {
            }
            for (String next : nested) {
                if (shouldSniffUrl(next)) enqueueSniffFrame(next, depth + 1);
            }
            runOnUiThread(NativePlayerActivity.this::processNextSniffTask);
        }
    }

    private static final class SniffTask {
        final String url;
        final int depth;

        SniffTask(String url, int depth) {
            this.url = url;
            this.depth = depth;
        }
    }
}
