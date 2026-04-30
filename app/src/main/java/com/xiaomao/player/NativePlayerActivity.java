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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
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

import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

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

import xyz.doikki.videocontroller.StandardVideoController;
import xyz.doikki.videoplayer.player.BaseVideoView;
import xyz.doikki.videoplayer.player.VideoView;

public class NativePlayerActivity extends Activity {
    private static final long PREPARE_TIMEOUT_MS = 15000L;
    private static final String DEFAULT_MOBILE_UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";

    private PlayerView playerView;
    private ExoPlayer mediaPlayer;
    private VideoView dkPlayerView;
    private WebView artPlayerWebView;
    private LinearLayout navBar;
    private FrameLayout playerBox;
    private LinearLayout.LayoutParams playerBoxLayoutParams;
    private ScrollView contentScrollView;
    private WebView sniffWeb;
    private View playerOverlay;
    private ProgressBar loading;
    private TextView titleView;
    private TextView lineView;
    private TextView stateView;
    private TextView portraitModeButton;
    private TextView portraitExitButton;
    private TextView speedButton;
    private TextView resizeButton;
    private LinearLayout episodeWrap;

    private NativeSource source;
    private NativeDrpyEngine engine;
    private String title;
    private String line;
    private String input;
    private String playUrl;
    private boolean sniffing = false;
    private boolean portraitPlayerMode = false;
    private boolean artPlayerReady = false;
    private boolean artPlayerFullscreen = false;
    private boolean artPlayerWebFullscreen = false;
    private boolean preparedNotified = false;
    private boolean tempSpeedBoost = false;
    private boolean playWhenReady = true;
    private float selectedSpeed = 1.0f;
    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private int dkScreenScaleType = VideoView.SCREEN_SCALE_DEFAULT;
    private long playbackPosition = 0L;
    private final java.util.LinkedHashMap<String, String> activeHeaders = new java.util.LinkedHashMap<>();
    private final HashSet<String> rejectedSniffUrls = new HashSet<>();
    private boolean autoSniffRecoveryTried = false;
    private boolean currentPlaybackFromSniff = false;
    private String currentPlaybackPageUrl = "";

    private final ArrayList<String> episodeNames = new ArrayList<>();
    private final ArrayList<String> episodeInputs = new ArrayList<>();
    private int currentIndex = 0;
    private String seriesTitle = "\u89c6\u9891\u64ad\u653e";

    private final ArrayList<String> snifferMatchRules = new ArrayList<>();
    private final ArrayList<String> snifferExcludeRules = new ArrayList<>();
    private final ArrayList<String> snifferFollowRules = new ArrayList<>();
    private final ArrayList<String> snifferMediaRules = new ArrayList<>();
    private final ArrayList<StreamType> streamTypes = new ArrayList<>();
    private final ArrayList<SniffTask> sniffQueue = new ArrayList<>();
    private final HashSet<String> sniffVisited = new HashSet<>();
    private final ArrayList<SniffCandidate> sniffCandidates = new ArrayList<>();
    private int maxSniffDepth = 4;
    private long sniffSessionId = 0L;
    private String sniffCurrentUrl = "";
    private int sniffCurrentDepth = 0;
    private int streamTypeIndex = -1;
    private String pendingArtPlayerConfig = "";
    private String artPlayerDocument = "";
    private View fullscreenCustomView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    private final Handler handler = new Handler();
    private final Runnable playBestSniffCandidate = () -> chooseBestSniffCandidate(false);
    private final Runnable advanceSniffQueueRunnable = this::processNextSniffTask;
    private final Runnable prepareTimeoutRunnable = () -> {
        if (!preparedNotified) {
            String message = "\u64ad\u653e\u5668\u52a0\u8f7d\u8d85\u65f6\uff0c\u8bf7\u6362\u7ebf\u8def\u518d\u8bd5";
            if (!recoverFromPlaybackFailure(message)) {
                showError(message);
            }
        }
    };
    private final Runnable longPressSpeedRunnable = () -> {
        tempSpeedBoost = true;
        applyPlaybackSpeed(2.0f);
        showState("闀挎寜涓紝涓存椂 2.0x 鎾斁", false, 0.9f);
    };
    private final Runnable hideState = () -> {
        if (playerOverlay != null && !sniffing) {
            playerOverlay.setVisibility(View.GONE);
        }
    };
    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_BUFFERING) {
                showState("\u6b63\u5728\u7f13\u51b2\u89c6\u9891...", true, 0.92f);
                return;
            }
            if (playbackState == Player.STATE_READY) {
                preparedNotified = true;
                cancelPrepareTimeout();
                showReadyState();
                return;
            }
            if (playbackState == Player.STATE_IDLE && !preparedNotified) {
                retryWithNextStreamType();
                return;
            }
            if (playbackState == Player.STATE_ENDED) {
                playbackPosition = 0L;
                showState("\u64ad\u653e\u5b8c\u6210", false, 0.95f);
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            String message = safe(error == null ? "" : error.getMessage());
            if (message.isEmpty()) {
                message = "\u64ad\u653e\u5668\u521d\u59cb\u5316\u5931\u8d25";
            }
            if (!recoverFromPlaybackFailure(message)) {
                showError(message);
            }
        }
    };
    private enum StreamType {
        AUTO,
        HLS,
        DASH,
        PROGRESSIVE
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        input = safe(getIntent().getStringExtra("input"));
        title = safe(getIntent().getStringExtra("title"));
        if (title.isEmpty()) title = "\u89c6\u9891\u64ad\u653e";
        line = safe(getIntent().getStringExtra("line"));
        if (line.isEmpty()) line = "\u9ed8\u8ba4\u7ebf\u8def";
        seriesTitle = safe(getIntent().getStringExtra("series_title"));
        if (seriesTitle.isEmpty()) {
            int split = title.indexOf(" 路 ");
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
            episodeNames.add("\u64ad\u653e");
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
        normalizePlaybackDefaultsSafe();
        buildUi();
        updateHeader();
        refreshHeaderTextSafe();
        buildEpisodeButtons();
        resolveAndPlay();
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(color(R.color.xm_player_bg));

        navBar = new LinearLayout(this);
        LinearLayout nav = navBar;
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(12), dp(10), dp(12), dp(10));
        nav.setBackgroundColor(color(R.color.xm_player_nav));
        page.addView(nav, new LinearLayout.LayoutParams(-1, dp(72)));

        TextView back = new TextView(this);
        back.setText("<");
        back.setTextColor(color(R.color.xm_player_text_primary));
        back.setTextSize(30);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setGravity(Gravity.CENTER);
        back.setBackground(cardBgRes(R.color.xm_player_panel_alt, R.color.xm_player_stroke, 14));
        nav.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        back.setOnClickListener(v -> onBackPressed());

        LinearLayout navText = new LinearLayout(this);
        navText.setOrientation(LinearLayout.VERTICAL);
        navText.setPadding(dp(10), 0, dp(10), 0);
        nav.addView(navText, new LinearLayout.LayoutParams(0, -1, 1));

        titleView = makeText("", 16, R.color.xm_player_text_primary, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        navText.addView(titleView, new LinearLayout.LayoutParams(-1, 0, 1));

        lineView = makeText("", 11, R.color.xm_player_text_secondary, false);
        lineView.setSingleLine(true);
        lineView.setEllipsize(TextUtils.TruncateAt.END);
        navText.addView(lineView, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView sourceTag = makeChip("DK \u5f15\u64ce", R.color.xm_player_chip_primary_bg, R.color.xm_player_chip_primary_stroke, R.color.xm_player_chip_primary_text);
        LinearLayout.LayoutParams sourceTagLp = new LinearLayout.LayoutParams(-2, dp(32));
        sourceTagLp.leftMargin = dp(6);
        nav.addView(sourceTag, sourceTagLp);

        playerBox = new FrameLayout(this);
        playerBox.setBackground(cardBgRes(R.color.xm_player_bg, R.color.xm_player_stroke, 0));
        playerBoxLayoutParams = new LinearLayout.LayoutParams(-1, dp(232));
        page.addView(playerBox, playerBoxLayoutParams);

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setResizeMode(resizeMode);
        bindPlayerGestures();
        playerView.setVisibility(View.GONE);
        playerBox.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        dkPlayerView = new VideoView(this);
        dkPlayerView.setBackgroundColor(Color.BLACK);
        dkPlayerView.setScreenScaleType(dkScreenScaleType);
        StandardVideoController dkController = new StandardVideoController(this);
        dkController.addDefaultControlComponent(title, false);
        dkPlayerView.setVideoController(dkController);
        dkPlayerView.addOnStateChangeListener(new BaseVideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                handleDkPlayState(playState);
            }
        });
        playerBox.addView(dkPlayerView, new FrameLayout.LayoutParams(-1, -1));

        artPlayerWebView = createArtPlayerWebView();
        artPlayerWebView.setVisibility(View.GONE);
        playerBox.addView(artPlayerWebView, new FrameLayout.LayoutParams(-1, -1));
        bindPlayerGestures();

        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(18), dp(18), dp(18), dp(18));
        overlay.setClickable(false);
        overlay.setFocusable(false);
        playerOverlay = overlay;
        playerBox.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        portraitExitButton = makeChip("\u9000\u51fa\u5168\u5c4f", R.color.xm_player_chip_tonal_bg, R.color.xm_player_chip_tonal_stroke, R.color.xm_player_chip_tonal_text);
        portraitExitButton.setVisibility(View.GONE);
        portraitExitButton.setOnClickListener(v -> applyPlayerBoxMode(false));
        FrameLayout.LayoutParams portraitExitLp = new FrameLayout.LayoutParams(-2, dp(34), Gravity.TOP | Gravity.END);
        portraitExitLp.topMargin = dp(12);
        portraitExitLp.rightMargin = dp(12);
        playerBox.addView(portraitExitButton, portraitExitLp);

        loading = new ProgressBar(this);
        overlay.addView(loading, new LinearLayout.LayoutParams(dp(38), dp(38)));

        stateView = makeText("\u6b63\u5728\u89e3\u6790\u64ad\u653e\u5730\u5740...", 14, R.color.xm_player_text_primary, false);
        stateView.setGravity(Gravity.CENTER);
        stateView.setPadding(dp(14), dp(14), dp(14), 0);
        overlay.addView(stateView, new LinearLayout.LayoutParams(-2, -2));

        contentScrollView = new ScrollView(this);
        ScrollView scrollView = contentScrollView;
        scrollView.setFillViewport(true);
        page.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(18));
        scrollView.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout heroCard = new LinearLayout(this);
        heroCard.setOrientation(LinearLayout.VERTICAL);
        heroCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        heroCard.setBackground(cardBgRes(R.color.xm_player_panel, R.color.xm_player_stroke, 12));
        root.addView(heroCard, new LinearLayout.LayoutParams(-1, -2));

        heroCard.addView(makeText("\u5f53\u524d\u64ad\u653e", 17, R.color.xm_player_text_primary, true));
        TextView tip = makeText("\u81ea\u52a8\u89e3\u6790\u76f4\u94fe\uff0c\u5e76\u7531 DK \u64ad\u653e\u5668\u627f\u63a5\u64ad\u653e\u3002", 12, R.color.xm_player_text_secondary, false);
        tip.setPadding(0, dp(10), 0, 0);
        heroCard.addView(tip);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(12), 0, 0);
        heroCard.addView(chips);
        TextView lineChip = makeChip("\u7ebf\u8def " + line, R.color.xm_player_chip_tonal_bg, R.color.xm_player_chip_tonal_stroke, R.color.xm_player_chip_tonal_text);
        TextView sourceChip = makeChip("\u7247\u6e90 " + source.title, R.color.xm_player_chip_primary_bg, R.color.xm_player_chip_primary_stroke, R.color.xm_player_chip_primary_text);
        lineChip.setSingleLine(true);
        lineChip.setEllipsize(TextUtils.TruncateAt.END);
        sourceChip.setSingleLine(true);
        sourceChip.setEllipsize(TextUtils.TruncateAt.END);
        sourceChip.setMaxWidth(dp(220));
        LinearLayout.LayoutParams chip1 = new LinearLayout.LayoutParams(-2, dp(34));
        chip1.rightMargin = dp(8);
        chips.addView(lineChip, chip1);
        chips.addView(sourceChip, new LinearLayout.LayoutParams(-2, dp(34)));

        TextView episodeTitle = makeText("\u9009\u96c6", 16, R.color.xm_player_text_primary, true);
        episodeTitle.setPadding(0, dp(18), 0, dp(8));
        root.addView(episodeTitle);

        LinearLayout playModeRow = new LinearLayout(this);
        playModeRow.setOrientation(LinearLayout.HORIZONTAL);
        playModeRow.setGravity(Gravity.CENTER_VERTICAL);
        playModeRow.setPadding(0, 0, 0, dp(8));
        root.addView(playModeRow, new LinearLayout.LayoutParams(-1, -2));

        TextView modeHint = makeText("\u89c2\u770b\u7ad6\u5c4f\u5185\u5bb9\u65f6\uff0c\u53ef\u5207\u6362\u5230\u66f4\u5927\u7684\u64ad\u653e\u533a\u57df\u3002", 12, R.color.xm_player_text_secondary, false);
        playModeRow.addView(modeHint, new LinearLayout.LayoutParams(0, -2, 1));

        portraitModeButton = makeChip("\u5168\u5c4f\u64ad\u653e", R.color.xm_player_chip_tonal_bg, R.color.xm_player_chip_tonal_stroke, R.color.xm_player_chip_tonal_text);
        portraitModeButton.setOnClickListener(v -> togglePortraitPlayerMode());
        playModeRow.addView(portraitModeButton, new LinearLayout.LayoutParams(-2, dp(34)));

        LinearLayout playerActionRow = new LinearLayout(this);
        playerActionRow.setOrientation(LinearLayout.HORIZONTAL);
        playerActionRow.setGravity(Gravity.CENTER_VERTICAL);
        playerActionRow.setPadding(0, 0, 0, dp(8));
        root.addView(playerActionRow, new LinearLayout.LayoutParams(-1, -2));

        speedButton = makeChip("1.0x", R.color.xm_player_chip_tonal_bg, R.color.xm_player_chip_tonal_stroke, R.color.xm_player_chip_tonal_text);
        speedButton.setOnClickListener(v -> cyclePlaybackSpeed());
        LinearLayout.LayoutParams speedLp = new LinearLayout.LayoutParams(-2, dp(34));
        speedLp.rightMargin = dp(8);
        playerActionRow.addView(speedButton, speedLp);

        resizeButton = makeChip("\u9002\u914d", R.color.xm_player_chip_tonal_bg, R.color.xm_player_chip_tonal_stroke, R.color.xm_player_chip_tonal_text);
        resizeButton.setOnClickListener(v -> cycleResizeMode());
        playerActionRow.addView(resizeButton, new LinearLayout.LayoutParams(-2, dp(34)));

        updateSpeedButton();
        updateResizeButton();

        LinearLayout railCard = new LinearLayout(this);
        railCard.setOrientation(LinearLayout.VERTICAL);
        railCard.setPadding(dp(12), dp(10), dp(12), dp(12));
        railCard.setBackground(cardBgRes(R.color.xm_player_panel_alt, R.color.xm_player_stroke, 12));
        root.addView(railCard, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView scrollRail = new HorizontalScrollView(this);
        scrollRail.setHorizontalScrollBarEnabled(false);
        railCard.addView(scrollRail, new LinearLayout.LayoutParams(-1, dp(54)));

        episodeWrap = new LinearLayout(this);
        episodeWrap.setOrientation(LinearLayout.HORIZONTAL);
        episodeWrap.setPadding(0, dp(8), 0, 0);
        scrollRail.addView(episodeWrap, new HorizontalScrollView.LayoutParams(-2, -1));

        applyPlayerBoxMode(false);
        setContentView(page);
    }

    private void togglePortraitPlayerMode() {
        applyPlayerBoxMode(!portraitPlayerMode);
    }

    private void applyPlayerBoxMode(boolean enabled) {
        portraitPlayerMode = enabled;
        if (playerBoxLayoutParams != null) {
            playerBoxLayoutParams.height = enabled ? 0 : dp(232);
            playerBoxLayoutParams.weight = enabled ? 1f : 0f;
            if (playerBox != null) {
                playerBox.setLayoutParams(playerBoxLayoutParams);
                playerBox.requestLayout();
            }
        }
        if (navBar != null) {
            navBar.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
        if (contentScrollView != null) {
            contentScrollView.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }
        if (portraitExitButton != null) {
            portraitExitButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
            portraitExitButton.bringToFront();
        }
        if (enabled) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (portraitModeButton != null) {
            portraitModeButton.setText(enabled ? "\u9000\u51fa\u5168\u5c4f" : "\u5168\u5c4f\u64ad\u653e");
            portraitModeButton.setBackground(enabled
                    ? cardBgRes(R.color.xm_player_chip_primary_bg, R.color.xm_player_chip_primary_stroke, 16)
                    : cardBgRes(R.color.xm_player_chip_tonal_bg, R.color.xm_player_chip_tonal_stroke, 16));
            portraitModeButton.setTextColor(color(enabled ? R.color.xm_player_chip_primary_text : R.color.xm_player_chip_tonal_text));
        }
    }

    private WebView createArtPlayerWebView() {
        WebView webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        webView.addJavascriptInterface(new ArtPlayerBridge(), "XmVideoBridge");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreenCustomView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                fullscreenCustomView = view;
                fullscreenCallback = callback;
                FrameLayout decor = (FrameLayout) getWindow().getDecorView();
                decor.addView(view, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            @Override
            public void onHideCustomView() {
                hideArtPlayerCustomView();
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                artPlayerReady = true;
                injectArtPlayerConfigIfReady();
            }
        });
        return webView;
    }

    private void hideArtPlayerCustomView() {
        if (fullscreenCustomView == null) {
            return;
        }
        FrameLayout decor = (FrameLayout) getWindow().getDecorView();
        decor.removeView(fullscreenCustomView);
        fullscreenCustomView = null;
        if (fullscreenCallback != null) {
            try {
                fullscreenCallback.onCustomViewHidden();
            } catch (Throwable ignored) {
            }
            fullscreenCallback = null;
        }
        if (!portraitPlayerMode) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    private void injectArtPlayerConfigIfReady() {
        if (artPlayerWebView == null || !artPlayerReady || safe(pendingArtPlayerConfig).isEmpty()) {
            return;
        }
        String js = "window.xmPlayerInit(" + JSONObject.quote(pendingArtPlayerConfig) + ");";
        artPlayerWebView.evaluateJavascript(js, null);
    }

    private void loadArtPlayer(String mediaUrl, Map<String, String> headers) {
        if (artPlayerWebView == null) {
            throw new IllegalStateException("art player webview missing");
        }
        releaseDkPlayer();
        releaseMediaPlayer();
        preparedNotified = false;
        cancelPrepareTimeout();
        if (dkPlayerView != null) {
            dkPlayerView.setVisibility(View.GONE);
        }
        if (playerView != null) {
            playerView.setVisibility(View.GONE);
        }
        artPlayerWebView.setVisibility(View.VISIBLE);
        artPlayerReady = false;
        artPlayerFullscreen = false;
        artPlayerWebFullscreen = false;
        JSONObject config = new JSONObject();
        try {
            config.put("url", mediaUrl);
            config.put("title", title);
            config.put("poster", "");
            config.put("type", inferPrimaryStreamType(mediaUrl, headers) == StreamType.HLS ? "m3u8" : "normal");
            JSONObject headerJson = new JSONObject();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                headerJson.put(entry.getKey(), entry.getValue());
            }
            config.put("headers", headerJson);
        } catch (Exception ignored) {
        }
        pendingArtPlayerConfig = config.toString();
        if (artPlayerDocument.isEmpty()) {
            artPlayerDocument = buildArtPlayerDocument();
        }
        String baseUrl = buildOriginFromUrl(mediaUrl);
        if (baseUrl.isEmpty()) {
            baseUrl = buildOriginFromUrl(source == null ? "" : source.host);
        }
        if (baseUrl.isEmpty()) {
            baseUrl = "https://appassets.androidplatform.net/";
        }
        artPlayerWebView.loadDataWithBaseURL(baseUrl, artPlayerDocument, "text/html", "utf-8", null);
        updateResizeButton();
    }

    private String buildArtPlayerDocument() {
        String artplayerJs = safe(readAssetText("web/vendor/artplayer.js"));
        String hlsJs = safe(readAssetText("web/vendor/hls.light.min.js"));
        String playerJs = safe(readAssetText("web/player_embed.js"));
        return "<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1,viewport-fit=cover'>"
                + "<style>"
                + "html,body{margin:0;height:100%;background:#000;overflow:hidden;}"
                + "body{font-family:system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC',sans-serif;}"
                + "#artplayer-app{width:100%;height:100%;background:#000;}"
                + ".art-video-player{height:100%!important;}"
                + "</style></head><body><div id='artplayer-app'></div>"
                + "<script>" + escapeInlineScript(artplayerJs) + "</script>"
                + "<script>" + escapeInlineScript(hlsJs) + "</script>"
                + "<script>" + escapeInlineScript(playerJs) + "</script>"
                + "</body></html>";
    }

    private String escapeInlineScript(String script) {
        return safe(script).replace("</script>", "<\\/script>");
    }

    private String readAssetText(String assetPath) {
        try {
            java.io.InputStream inputStream = getAssets().open(assetPath);
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, count);
            }
            inputStream.close();
            return outputStream.toString("UTF-8");
        } catch (Exception ignored) {
            return "";
        }
    }

    private void bindPlayerGestures() {
        View gestureSurface = dkPlayerView != null ? dkPlayerView : (artPlayerWebView != null ? artPlayerWebView : playerView);
        if (gestureSurface == null) {
            return;
        }
        gestureSurface.setOnTouchListener((v, event) -> {
            if (event == null) {
                return false;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                handler.postDelayed(longPressSpeedRunnable, 350);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(longPressSpeedRunnable);
                if (tempSpeedBoost) {
                    tempSpeedBoost = false;
                    applyPlaybackSpeed(selectedSpeed);
                    showState("鎭㈠涓?" + formatSpeed(selectedSpeed), false, 0.9f);
                    handler.postDelayed(hideState, 800);
                }
            }
            return false;
        });
    }

    private void cyclePlaybackSpeed() {
        float[] speeds = new float[]{1.0f, 1.25f, 1.5f, 2.0f};
        int nextIndex = 0;
        for (int i = 0; i < speeds.length; i++) {
            if (Math.abs(speeds[i] - selectedSpeed) < 0.01f) {
                nextIndex = (i + 1) % speeds.length;
                break;
            }
        }
        selectedSpeed = speeds[nextIndex];
        if (!tempSpeedBoost) {
            applyPlaybackSpeed(selectedSpeed);
        }
        updateSpeedButton();
        showState("鍒囨崲鍊嶉€熶负 " + formatSpeed(selectedSpeed), false, 0.92f);
        handler.postDelayed(hideState, 800);
    }

    private void cycleResizeMode() {
        if (dkPlayerView != null) {
            if (dkScreenScaleType == VideoView.SCREEN_SCALE_DEFAULT) {
                dkScreenScaleType = VideoView.SCREEN_SCALE_CENTER_CROP;
            } else if (dkScreenScaleType == VideoView.SCREEN_SCALE_CENTER_CROP) {
                dkScreenScaleType = VideoView.SCREEN_SCALE_MATCH_PARENT;
            } else {
                dkScreenScaleType = VideoView.SCREEN_SCALE_DEFAULT;
            }
            dkPlayerView.setScreenScaleType(dkScreenScaleType);
        }
        updateResizeButton();
        showState(currentResizeLabel(), false, 0.92f);
        handler.postDelayed(hideState, 800);
    }

    private void updateSpeedButton() {
        if (speedButton != null) {
            speedButton.setText(formatSpeed(selectedSpeed));
        }
    }

    private void updateResizeButton() {
        if (resizeButton != null) {
            resizeButton.setText(currentResizeLabel());
        }
    }

    private String currentResizeLabel() {
        if (dkScreenScaleType == VideoView.SCREEN_SCALE_CENTER_CROP) {
            return "\u88c1\u526a\u586b\u5145";
        }
        if (dkScreenScaleType == VideoView.SCREEN_SCALE_MATCH_PARENT) {
            return "\u62c9\u4f38\u94fa\u6ee1";
        }
        return "\u9002\u914d";
    }

    private String formatSpeed(float speed) {
        return String.format(Locale.US, "%.2fx", speed).replace(".00x", ".0x").replace(".50x", ".5x");
    }

    private void applyPlaybackSpeed(float speed) {
        if (dkPlayerView != null) {
            dkPlayerView.setSpeed(speed);
        } else if (artPlayerWebView != null) {
            artPlayerWebView.evaluateJavascript("window.xmPlayerSetRate && window.xmPlayerSetRate(" + speed + ");", null);
        } else if (mediaPlayer != null) {
            mediaPlayer.setPlaybackParameters(new PlaybackParameters(speed));
        }
    }

    private int portraitPlayerHeight() {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int target = (int) (screenHeight * 0.64f);
        return Math.max(dp(360), Math.min(target, dp(560)));
    }

    private void updateHeader() {
        String episodeName = currentIndex >= 0 && currentIndex < episodeNames.size() ? episodeNames.get(currentIndex) : "\u64ad\u653e";
        titleView.setText(seriesTitle + " \u00b7 " + episodeName);
        lineView.setText(buildHeaderMetaText());
        title = seriesTitle + " 路 " + episodeName;
    }

    private void buildEpisodeButtons() {
        episodeWrap.removeAllViews();
        for (int i = 0; i < episodeInputs.size(); i++) {
            final int index = i;
            TextView ep = new TextView(this);
            ep.setText(episodeNames.get(i).isEmpty() ? ("\u7b2c" + (i + 1) + "\u96c6") : episodeNames.get(i));
            ep.setTextSize(13);
            ep.setTextColor(color(i == currentIndex ? R.color.xm_player_chip_primary_text : R.color.xm_player_text_primary));
            ep.setTypeface(Typeface.DEFAULT_BOLD);
            ep.setGravity(Gravity.CENTER);
            ep.setSingleLine(true);
            ep.setEllipsize(TextUtils.TruncateAt.END);
            ep.setMaxWidth(dp(260));
            ep.setPadding(dp(16), 0, dp(16), 0);
            ep.setBackground(i == currentIndex
                    ? cardBgRes(R.color.xm_player_chip_primary_bg, R.color.xm_player_chip_primary_stroke, 18)
                    : cardBgRes(R.color.xm_player_panel_alt, R.color.xm_player_stroke, 18));
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
        refreshHeaderTextSafe();
        buildEpisodeButtons();
        resolveAndPlay();
    }

    /*
    private void normalizePlaybackDefaults() {
        if (title.isEmpty() || looksBrokenPlaybackText(title)) {
            title = "闂傚倸鍊峰ù鍥х暦閻㈢绐楅柟鎵閸嬶繝鏌曟径鍫濆壔婵炴垶菤閺€浠嬫倵閿濆啫濡烽柛瀣崌瀹曟帡鎮欓弻銉ユ暪闂備礁鎼ú銊╁磻閻旂⒈鏁婇柟鐑樺焾濞撳鏌曢崼婵囶棡閻忓繒鏁婚弻娑㈡偐閸愭彃顫掗悗?;
        }
        if (line.isEmpty() || looksBrokenPlaybackText(line)) {
            line = "婵犵數濮甸鏍窗濡ゅ啯鏆滄俊銈呭暟閻瑩鏌熼悜妯镐粶闁逞屽墾缁犳挸鐣锋總绋课ㄦい鏃囧Г濞呭秹姊绘担鍝勫付妞ゎ偅娲熷畷鎰旈埀顒冪亱濡炪倖鐗滈崑鐐烘偂閻樼粯鐓欏Λ棰佽兌椤︼箓鏌ｈ箛銉хМ闁?;
        }
        if (seriesTitle.isEmpty() || looksBrokenPlaybackText(seriesTitle)) {
            seriesTitle = title;
        }
        if (episodeInputs.isEmpty()) {
            episodeInputs.add(input);
        }
        if (episodeNames.isEmpty()) {
            episodeNames.add("\u64ad\u653e");
        }
        int count = Math.min(episodeNames.size(), episodeInputs.size());
        for (int i = 0; i < count; i++) {
            String name = safe(episodeNames.get(i));
            if (name.isEmpty() || looksBrokenPlaybackText(name)) {
                episodeNames.set(i, count > 1 ? ("\u7b2c" + (i + 1) + "\u96c6") : "\u64ad\u653e");
            }
        }
        if (currentIndex < 0 || currentIndex >= episodeInputs.size()) {
            currentIndex = 0;
        }
    }

    private void refreshHeaderText() {
        String episodeName = currentIndex >= 0 && currentIndex < episodeNames.size()
                ? safe(episodeNames.get(currentIndex))
                : "鎾斁";
        if (episodeName.isEmpty()) {
            episodeName = "鎾斁";
        }
        seriesTitle = safe(seriesTitle).isEmpty() ? title : seriesTitle;
        title = seriesTitle + " 路 " + episodeName;
        if (titleView != null) {
            titleView.setText(title);
        }
        if (lineView != null) {
            lineView.setText("绾胯矾: " + line + " 路 婧? " + source.title + " 路 鍏?" + episodeInputs.size() + " 闆?路 鍡呮帰娣卞害 " + maxSniffDepth);
        }
    }

    private boolean looksBrokenPlaybackText(String value) {
        String text = safe(value);
        if (text.isEmpty()) {
            return false;
        }
        return text.contains("锟斤拷")
                || text.contains("闂?)
                || text.contains("濠?)
                || text.contains("缂?)
                || text.contains("婵?);






    }

    */
    private void normalizePlaybackDefaultsSafe() {
        if (title.isEmpty() || looksBrokenPlaybackTextSafe(title)) {
            title = "\u89c6\u9891\u64ad\u653e";
        }
        if (line.isEmpty() || looksBrokenPlaybackTextSafe(line)) {
            line = "\u9ed8\u8ba4\u7ebf\u8def";
        }
        if (seriesTitle.isEmpty() || looksBrokenPlaybackTextSafe(seriesTitle)) {
            seriesTitle = title;
        }
        if (episodeInputs.isEmpty()) {
            episodeInputs.add(input);
        }
        if (episodeNames.isEmpty()) {
            episodeNames.add("\u64ad\u653e");
        }
        int count = Math.min(episodeNames.size(), episodeInputs.size());
        for (int i = 0; i < count; i++) {
            String name = safe(episodeNames.get(i));
            if (name.isEmpty() || looksBrokenPlaybackTextSafe(name)) {
                episodeNames.set(i, count > 1 ? ("\u7b2c" + (i + 1) + "\u96c6") : "\u64ad\u653e");
            }
        }
        if (currentIndex < 0 || currentIndex >= episodeInputs.size()) {
            currentIndex = 0;
        }
    }

    private void refreshHeaderTextSafe() {
        String episodeName = currentIndex >= 0 && currentIndex < episodeNames.size()
                ? safe(episodeNames.get(currentIndex))
                : "\u64ad\u653e";
        if (episodeName.isEmpty()) {
            episodeName = "\u64ad\u653e";
        }
        seriesTitle = safe(seriesTitle).isEmpty() ? title : seriesTitle;
        title = seriesTitle + " \u00b7 " + episodeName;
        if (titleView != null) {
            titleView.setText(title);
        }
        if (lineView != null) {
            lineView.setText(buildHeaderMetaText());
        }
    }

    private String buildHeaderMetaText() {
        return "\u7ebf\u8def: " + line
                + " \u00b7 \u6e90: " + source.title
                + " \u00b7 \u5171 " + episodeInputs.size() + " \u96c6";
    }

    private boolean looksBrokenPlaybackTextSafe(String value) {
        String text = safe(value);
        if (text.isEmpty()) {
            return false;
        }
        return text.contains("\u93c5")
                || text.contains("\u59d2")
                || text.contains("\u93b4")
                || text.contains("\u7efe")
                || text.contains("\u5a67")
                || text.contains("\u95c6")
                || text.contains("\u95b8")
                || text.contains("\u9421")
                || text.contains("\u9422")
                || text.contains("\ufffd");
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
                autoDismissAgeAndAdLayers(view);
                showLoadingState("\u6b63\u5728\u55c5\u63a2\u9875\u9762\u8d44\u6e90\u2026");
                probeCurrentPage(url, sniffCurrentDepth);
            }
        });
        return web;
    }

    private void autoDismissAgeAndAdLayers(WebView view) {
        if (view == null) {
            return;
        }
        String script = "(function(){try{"
                + "try{document.cookie='user-choose=true; path=/; SameSite=Lax';document.cookie='newuser=1; path=/; SameSite=Lax';localStorage.setItem('newuser','1');sessionStorage.setItem('newuser','1');}catch(e){}"
                + "function tapAll(selectors){for(var i=0;i<selectors.length;i++){var nodes=document.querySelectorAll(selectors[i]);for(var j=0;j<nodes.length;j++){try{nodes[j].click();}catch(e){}}}}"
                + "function shouldTap(label){label=String(label||'').trim();if(!label)return false;return /(?:18|adult|continue|confirm|agree|allow|enter|play|skip|close|warning|visit|\\u6211\\u5df2\\u6ee118\\u5468\\u5c81|\\u8fdb\\u5165|\\u786e\\u8ba4|\\u540c\\u610f|\\u7ee7\\u7eed\\u8bbf\\u95ee|\\u7ee7\\u7eed\\u64ad\\u653e|\\u7acb\\u5373\\u64ad\\u653e|\\u8df3\\u8fc7|\\u5173\\u95ed)/i.test(label);}"
                + "var selectors=['#wanrningconfirm','#warningconfirm','.confirm','.btn-confirm','.popup-confirm','.dialog-confirm','.enter','.enter-btn','.skip','.skip-btn','.skipad','.btn-skip','.ad-skip','.video-ad-skip','.close','.close-btn','.close-icon','.layui-layer-close','.icon-close','[data-dismiss]','[class*=confirm]','[id*=confirm]','[class*=skip]','[id*=skip]','[class*=close]','[id*=close]'];"
                + "tapAll(selectors);"
                + "var taps=document.querySelectorAll('button,a,div,span,input');"
                + "for(var k=0;k<taps.length;k++){var el=taps[k];var label=((el.innerText||el.textContent||'')+' '+(el.value||'')).trim();if(shouldTap(label)){try{el.click();}catch(e){}}}"
                + "setTimeout(function(){tapAll(selectors);},600);setTimeout(function(){tapAll(selectors);},1600);setTimeout(function(){tapAll(selectors);},3200);"
                + "}catch(e){}})();";
        view.evaluateJavascript(script, null);
    }

    private void resolveAndPlay() {
        releaseSniffer();
        releaseDkPlayer();
        releaseMediaPlayer();
        releaseArtPlayer();
        playUrl = null;
        currentPlaybackPageUrl = "";
        currentPlaybackFromSniff = false;
        autoSniffRecoveryTried = false;
        rejectedSniffUrls.clear();
        activeHeaders.clear();
        showLoadingState("\u6b63\u5728\u89e3\u6790\u64ad\u653e\u5730\u5740\u2026");
        if (source.raw != null && source.raw.contains("var rule")) {
            engine.runLazy(input, (result, err) -> runOnUiThread(() -> {
                if ((result == null || safe(result.url).isEmpty()) && err != null && !err.isEmpty()) {
                    showError("\u89e3\u6790\u5931\u8d25: " + err);
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
            showError("\u672a\u83b7\u53d6\u5230\u53ef\u64ad\u653e\u5730\u5740");
            return;
        }
        if (forceSniff || !looksLikeDirectMedia(playUrl)) {
            startSniff(resolveSniffEntryUrl(playUrl), "\u89e3\u6790\u7ed3\u679c\u4e0d\u662f\u76f4\u94fe\uff0c\u6b63\u5728\u6309\u89c4\u5219\u7f51\u9875\u55c5\u63a2\u2026");
            return;
        }
        playInPlace(playUrl, false, "");
    }

    private void playInPlace(String mediaUrl, boolean fromSniff, String pageUrl) {
        sniffing = false;
        currentPlaybackFromSniff = fromSniff;
        currentPlaybackPageUrl = safe(pageUrl);
        stopSniffer(fromSniff);
        releaseDkPlayer();
        playUrl = mediaUrl;
        showLoadingState("正在加载播放器...");
        playbackPosition = 0L;
        playWhenReady = true;
        try {
            prepareDkPlayer(mediaUrl, buildPlayerHeaders());
        } catch (Throwable error) {
            String message = "播放器初始化失败";
            if (recoverFromPlaybackFailure(message)) {
                return;
            }
            Toast.makeText(this, message + "，尝试外部播放器", Toast.LENGTH_SHORT).show();
            openExternalPlayer();
        }
    }

    private void startSniff(String pageUrl, String message) {
        if (safe(pageUrl).isEmpty()) {
            showError("\u6ca1\u6709\u53ef\u55c5\u63a2\u7684\u9875\u9762\u5730\u5740");
            return;
        }
        sniffing = true;
        currentPlaybackFromSniff = false;
        currentPlaybackPageUrl = "";
        stopSniffer(false);
        sniffQueue.clear();
        sniffVisited.clear();
        sniffCandidates.clear();
        sniffCurrentUrl = "";
        sniffCurrentDepth = 0;
        sniffSessionId++;
        showState(message, true, 1f);
        sniffWeb = createSnifferWebView();
        addContentView(sniffWeb, new ViewGroup.LayoutParams(1, 1));
        loadSniffFrame(pageUrl, 0);
        final long token = sniffSessionId;
        handler.postDelayed(() -> {
            if (sniffing && token == sniffSessionId && !chooseBestSniffCandidate(true)) {
                showError("\u55c5\u63a2\u8d85\u65f6\uff0c\u8bf7\u6362\u7ebf\u8def\u518d\u8bd5");
            }
        }, 22000);
    }

    private void loadSniffFrame(String pageUrl, int depth) {
        if (!sniffing || sniffWeb == null) return;
        String clean = safe(pageUrl);
        if (clean.isEmpty() || depth > maxSniffDepth) {
            processNextSniffTask();
            return;
        }
        String visitKey = clean;
        if (sniffVisited.contains(visitKey)) {
            processNextSniffTask();
            return;
        }
        sniffVisited.add(visitKey);
        sniffCurrentUrl = clean;
        sniffCurrentDepth = depth;
        try {
            ensureAdultGateBypass(clean);
            ensureAdultGateBypass(source == null ? "" : source.host);
            java.util.HashMap<String, String> headers = new java.util.HashMap<>(activeHeaders);
            if (!headers.containsKey("Referer") && source != null && !safe(source.host).isEmpty()) {
                headers.put("Referer", source.host + "/");
            }
            String cookies = mergeCookieStrings(headers.get("Cookie"), collectCookieHeader(clean), collectCookieHeader(source == null ? "" : source.host));
            if (!cookies.isEmpty()) {
                headers.put("Cookie", cookies);
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

    private void scheduleNextSniffTask(long delayMs) {
        handler.removeCallbacks(advanceSniffQueueRunnable);
        handler.postDelayed(advanceSniffQueueRunnable, Math.max(60L, delayMs));
    }

    private void enqueueSniffFrame(String url, int depth) {
        String clean = safe(url);
        if (clean.isEmpty() || depth > maxSniffDepth) return;
        String visitKey = clean;
        if (sniffVisited.contains(visitKey)) return;
        for (SniffTask task : sniffQueue) {
            if (clean.equals(task.url)) return;
        }
        sniffQueue.add(new SniffTask(clean, depth));
    }

    private void processNextSniffTask() {
        handler.removeCallbacks(advanceSniffQueueRunnable);
        if (!sniffing) {
            return;
        }
        if (sniffQueue.isEmpty()) {
            if (!sniffCandidates.isEmpty()) {
                handler.removeCallbacks(playBestSniffCandidate);
                handler.postDelayed(playBestSniffCandidate, 900);
            }
            return;
        }
        SniffTask task = sniffQueue.remove(0);
        loadSniffFrame(task.url, task.depth);
    }

    private void probeCurrentPage(String url, int depth) {
        if (sniffWeb == null) return;
        if (Build.VERSION.SDK_INT >= 0) {
            sniffWeb.evaluateJavascript(buildProbeScript(url, depth), null);
            return;
        }
        String js = "(function(){try{"
                + "function clickAdControls(){try{var sels=['.skip','.skip-btn','.skipad','.btn-skip','.ad-skip','.video-ad-skip','.close','.close-btn','.close-icon','.layui-layer-close','.icon-close','[class*=skip]','[class*=close]','[id*=skip]','[id*=close]'];"
                + "for(var i=0;i<sels.length;i++){var nodes=document.querySelectorAll(sels[i]);for(var j=0;j<nodes.length;j++){var el=nodes[j];var text=((el.innerText||el.textContent||'')+' '+(el.value||'')).toLowerCase();if(!text||/skip|close|jump|闂傚倸鍊峰ù鍥х暦閸偅鍙忛柟缁㈠枛缁犺銇勯幇鍫曟闁稿浜弻娑㈠Ψ閵忊剝鐝曢悗瑙勬礃閻擄繝寮诲☉娆愬劅闁炽儲鍓氭导鍐⒑閸濆嫭婀扮紒瀣墱缁鈽夐姀鐘愁棟闁圭厧鐡ㄩ幐濠氭倶閸℃绡€缁剧増蓱椤﹪鏌涚€ｎ亝顥㈤柡浣哥У閹峰懘鎼归崷顓ㄧ闯闂備胶顭堥張顒勬偡瑜忕划鏃堫敆閸曨剛鍘卞┑鐐村灥瀹曨剟鎮橀敓鐘崇厸闁糕剝娲栧畵鍡樻叏婵犲啯銇濈€规洜鍏橀、姗€鍩勯崘閿亾濡ゅ懏鈷戦柛娑樷姇閼测晛鍨濇繛鍡楁禋閸ゆ洘銇勯幒宥堝厡妞も晜鐓￠弻娑樷槈閸楃偟浠紒鐐劤閵堟悂寮婚敓鐘茬＜婵炴垵宕崝宀勬⒑缁嬪潡顎楃紒缁樺浮閸┾偓妞ゆ帒鍠氬鎰版煙閸濄儺鐒鹃摶鐐寸節闂堟侗鍎戠€规挷鐒﹂幈銊ヮ渻鐠囪弓澹曟俊鐐€戦崹娲晝閵忋倕绠栭柕蹇嬪€曞婵嗏攽閻樻彃鏆熼柣锝囧磱闂傚倸鍊风粈渚€骞栭位鍥敃閿曗偓閻ょ偓绻濇繝鍌涘櫧闁活厽鐟╅弻鐔告綇閸撗呮殸闂佹椿鍘介〃濠囧蓟濞戙垹鐒洪柛鎰典簴濡插牏绱撴担鍝勑ョ紒顕呭灦婵＄敻宕熼姘鳖啋闁荤姾娅ｉ崕銈夋倵妤ｅ啯鈷戦柛婵嗗濡叉椽鏌ｉ敐搴″⒋婵﹦绮幏鍛存倻濡儤鐣梻浣割吔閺夊灝顫囬悗瑙勬礀缂嶅﹪骞冮悾宀€鐭欓悹渚厛濡查亶姊绘担鑺ョ《闁哥姵鎸婚幈銊╂偨缁嬭法锛熼梺纭呮彧闂勫嫰宕愰悽鍛婄叆婵犻潧妫涢崙鍦磼閵娿倗鐭欓柡?.test(text)){try{el.click();}catch(e){}}}}"
                + "var taps=document.querySelectorAll('button,a,div,span');for(var k=0;k<taps.length;k++){var item=taps[k];var label=((item.innerText||item.textContent||'')+' '+(item.value||'')).trim();if(label&&/闂傚倸鍊峰ù鍥х暦閸偅鍙忛柟缁㈠枛缁犺銇勯幇鍫曟闁稿浜弻娑㈠Ψ閵忊剝鐝曢悗瑙勬礃閻擄繝寮诲☉娆愬劅闁炽儲鍓氭导鍐⒑閸濆嫭婀扮紒瀣墱缁鈽夐姀鐘愁棟闁圭厧鐡ㄩ幐濠氭倶閸℃绡€缁剧増蓱椤﹪鏌涚€ｎ亝顥㈤柡浣哥У閹峰懘鎼归崷顓ㄧ闯闂備胶顭堥張顒勬偡瑜忕划鏃堫敆閸曨剛鍘卞┑鐐村灥瀹曨剟鎮橀敓鐘崇厸闁糕剝娲栧畵鍡樻叏婵犲啯銇濈€规洜鍏橀、姗€鍩勯崘閿亾濡ゅ懏鈷戦柛娑樷姇閼测晛鍨濇繛鍡楁禋閸ゆ洘銇勯幒宥堝厡妞も晜鐓￠弻娑樷槈閸楃偟浠紒鐐劤閵堟悂寮婚敓鐘茬＜婵炴垵宕崝宀勬⒑缁嬪潡顎楃紒缁樺浮閸┾偓妞ゆ帒鍠氬鎰版煙閸濄儺鐒鹃摶鐐寸節闂堟侗鍎戠€规挷鐒﹂幈銊ヮ渻鐠囪弓澹曟俊鐐€戦崹娲晝閵忋倕绠栭柕蹇嬪€曞婵嗏攽閻樻彃鏆熼柣锝囧磱闂傚倸鍊风粈渚€骞栭位鍥敃閿曗偓閻ょ偓绻濇繝鍌涘櫧闁活厽鐟╅弻鐔告綇閸撗呮殸闂佹椿鍘介〃濠囧蓟濞戙垹鐒洪柛鎰典簴濡插牏绱撴担鍝勑ョ紒顕呭灦婵＄敻宕熼姘鳖啋闁荤姾娅ｉ崕銈夋倵妤ｅ啯鈷戦柛婵嗗濡叉椽鏌ｉ敐搴″⒋婵﹦绮幏鍛存倻濡儤鐣梻浣割吔閺夊灝顫囬悗瑙勬礀缂嶅﹪骞冮悾宀€鐭欓悹渚厛濡查亶姊绘担鑺ョ《闁哥姵鎸婚幈銊╂偨缁嬭法锛熼梺纭呮彧闂勫嫰宕愰悽鍛婄叆婵犻潧妫涢崙鍦磼閵娿倗鐭欓柡宀嬬節瀹曞崬螣绾拌鲸娈规俊鐐€戦崝濠囧磿閻㈢绠栨繛鍡樻尰閸ゆ垶銇勯幋锝呭姷婵＄偓鎮傚缁樻媴閻戞ê娈屽┑鈽嗗灠閿曨亜顕ｉ妸鈹у洤顬婂绱亅close/i.test(label)){try{item.click();}catch(e){}}}"
                + "}catch(e){}}"
                + "function report(tag){try{var out=[];var seen={};"
                + "function add(u,t){u=String(u||'').trim();if(!u||seen[u])return;seen[u]=1;out.push({url:u,type:t||''});try{if(/%[0-9a-f]{2}/i.test(u)){var du=decodeURIComponent(u);if(du&&du!==u&&!seen[du]){seen[du]=1;out.push({url:du,type:(t||'')+'-decoded'});}}}catch(e){}}"
                + "var vids=document.querySelectorAll('video,source,audio');for(var i=0;i<vids.length;i++){add(vids[i].currentSrc||vids[i].src,'video');add(vids[i].getAttribute('src'),'media');add(vids[i].getAttribute('data-src'),'media');}"
                + "var links=document.querySelectorAll('a[href],iframe[src],iframe[data-src],embed[src],object[data],[data-play],[data-url],[data-player],[data-play-url],[data-href],[onclick]');"
                + "for(var k=0;k<links.length;k++){var n=links[k];add(n.getAttribute('href')||n.getAttribute('src')||n.getAttribute('data')||n.getAttribute('data-play')||n.getAttribute('data-url')||n.getAttribute('data-player')||n.getAttribute('data-play-url')||n.getAttribute('data-href'), n.tagName.toLowerCase());add(n.getAttribute('onclick'),'onclick');}"
                + "var html=document.documentElement?document.documentElement.outerHTML:'';"
                + "var regs=[/https?:\\/\\/[^\\s\"'<>]+/g,/(?:thisUrl|video_src|videoUrl|play_url|playUrl)\\s*[:=]\\s*[\\\"']([^\\\"']+)[\\\"']/ig,/(?:player_aaaa|player_data|__PLAYER__|MacPlayerConfig)\\s*=\\s*\\{[\\s\\S]*?(?:url|src|video_url|parse_api|link_next)\\s*[:=]\\s*[\\\"']([^\\\"']+)[\\\"'][\\s\\S]*?\\}/ig,/src\\s*:\\s*[\\\"']([^\\\"']+)[\\\"']/ig,/[\\\"'](%[0-9a-f]{2}[^\\\"']*(?:%6d%33%75%38|%6d%70%34)[^\\\"']*)[\\\"']/ig];"
                + "for(var r=0;r<regs.length;r++){var m;while((m=regs[r].exec(html))){add(m[1]||m[0],'html-'+tag);}}"
                + "HermesPlayer.onSniffResult(JSON.stringify(out)," + depth + ",location.href||" + JSONObject.quote(url == null ? "" : url) + ");"
                + "}catch(e){HermesPlayer.onSniffResult('[]'," + depth + ",location.href||" + JSONObject.quote(url == null ? "" : url) + ");}}}"
                + "if(!window.__xmSniffHooked){window.__xmSniffHooked=1;try{var rawFetch=window.fetch;if(rawFetch){window.fetch=function(){try{var target=arguments[0];var hookUrl=(typeof target==='string'?target:(target&&target.url)||'');if(hookUrl){HermesPlayer.onSniffResult(JSON.stringify([{url:hookUrl,type:'fetch-call'}])," + depth + ",location.href||" + JSONObject.quote(url == null ? "" : url) + ");}}catch(e){}return rawFetch.apply(this,arguments).then(function(resp){try{if(resp&&resp.url){HermesPlayer.onSniffResult(JSON.stringify([{url:resp.url,type:'fetch'}])," + depth + ",location.href||" + JSONObject.quote(url == null ? "" : url) + ");}}catch(e){}return resp;});};}}catch(e){}"
                + "try{var xhrOpen=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(method,u){this.__xmUrl=u;return xhrOpen.apply(this,arguments);};var xhrSend=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.send=function(){var xhr=this;function done(){try{var finalUrl=xhr.responseURL||xhr.__xmUrl||'';if(finalUrl){HermesPlayer.onSniffResult(JSON.stringify([{url:finalUrl,type:'xhr'}])," + depth + ",location.href||" + JSONObject.quote(url == null ? "" : url) + ");}}catch(e){}}xhr.addEventListener('load',done);return xhrSend.apply(this,arguments);};}catch(e){}"
                + "}"
                + "clickAdControls();report('now');setTimeout(function(){clickAdControls();report('delay1');},1200);setTimeout(function(){clickAdControls();report('delay2');},3200);setTimeout(function(){clickAdControls();report('delay3');},5600);"
                + "}catch(e){HermesPlayer.onSniffResult('[]'," + depth + "," + JSONObject.quote(url == null ? "" : url) + ");}})();";
        sniffWeb.evaluateJavascript(js, null);
    }

    private String buildProbeScript(String url, int depth) {
        String fallbackUrl = JSONObject.quote(url == null ? "" : url);
        return """
                (function(){
                  try{
                    var fallback = __FALLBACK__;
                    var depth = __DEPTH__;
                    function emit(items, page){
                      try{
                        if(!items || !items.length) return;
                        HermesPlayer.onSniffResult(JSON.stringify(items), depth, page || location.href || fallback);
                      }catch(e){}
                    }
                    function createCollector(){
                      var out = [];
                      var seen = {};
                      function add(u, t){
                        u = String(u || '').trim();
                        if(!u || seen[u] || /^(javascript:|about:blank|blob:|data:)/i.test(u)) return;
                        seen[u] = 1;
                        out.push({url:u, type:t || ''});
                        try{
                          if(/\\\\u002f|\\\\u003a|\\\\u0026/i.test(u)){
                            var unicodeUrl = u.replace(/\\\\u002f/ig, '/').replace(/\\\\u003a/ig, ':').replace(/\\\\u0026/ig, '&');
                            if(unicodeUrl && unicodeUrl !== u && !seen[unicodeUrl]){
                              seen[unicodeUrl] = 1;
                              out.push({url:unicodeUrl, type:(t || '') + '-unicode'});
                            }
                          }
                        }catch(e){}
                        try{
                          if(/%[0-9a-f]{2}/i.test(u)){
                            var decoded = decodeURIComponent(u);
                            if(decoded && decoded !== u && !seen[decoded]){
                              seen[decoded] = 1;
                              out.push({url:decoded, type:(t || '') + '-decoded'});
                            }
                          }
                        }catch(e){}
                        try{
                          var slashDecoded = u.replace(/\\\\\\//g, '/');
                          if(slashDecoded && slashDecoded !== u && !seen[slashDecoded]){
                            seen[slashDecoded] = 1;
                            out.push({url:slashDecoded, type:(t || '') + '-slashes'});
                          }
                        }catch(e){}
                      }
                      return {out:out, add:add};
                    }
                    function collectFromText(text, tag, collector){
                      try{
                        text = text == null ? '' : String(text);
                        if(!text) return;
                        if(text.length > 400000) text = text.slice(0, 400000);
                        var normalized = text
                          .replace(/\\\\u002f/ig, '/')
                          .replace(/\\\\u003a/ig, ':')
                          .replace(/\\\\u0026/ig, '&')
                          .replace(/\\\\\\//g, '/');
                        var patterns = [
                          /https?:\\/\\/[^\\s"'<>\\\\]+/ig,
                          /(?:thisUrl|video_src|videoUrl|play_url|playUrl|url|src|video_url|file|hls|hls_url|stream_url|link_next|jump|videoLink)\\s*[:=]\\s*["']([^"'<>]+)["']/ig,
                          /(?:player_aaaa|player_data|__PLAYER__|MacPlayerConfig)\\s*=\\s*\\{[\\s\\S]*?(?:url|src|video_url|parse_api|link_next)\\s*[:=]\\s*["']([^"']+)["'][\\s\\S]*?\\}/ig,
                          /["'](%[0-9a-f]{2}[^"']*(?:%6d%33%75%38|%6d%70%34|%66%6c%76|%6d%70%64)[^"']*)["']/ig,
                          /["']((?:\\\\\\/|\\/|https?:\\/\\/)?[^"'\\s<>]+\\.(?:m3u8|mp4|flv|mpd)(?:\\?[^"'<>]*)?)["']/ig,
                          /(?:url|src|video|play|source)=((?:https?:)?%2f%2f[^&"']+)/ig
                        ];
                        for(var i = 0; i < patterns.length; i++){
                          var match;
                          while((match = patterns[i].exec(normalized))){
                            collector.add(match[1] || match[0], tag);
                          }
                        }
                      }catch(e){}
                    }
                    function shouldInspectBody(targetUrl, contentType){
                      var lowerUrl = String(targetUrl || '').toLowerCase();
                      var lowerType = String(contentType || '').toLowerCase();
                      if(lowerUrl.indexOf('.m3u8') >= 0 || lowerUrl.indexOf('.mp4') >= 0 || lowerUrl.indexOf('.flv') >= 0 || lowerUrl.indexOf('.mpd') >= 0) return true;
                      if(lowerType.indexOf('json') >= 0 || lowerType.indexOf('text') >= 0 || lowerType.indexOf('javascript') >= 0) return true;
                      if(lowerType.indexOf('html') >= 0 || lowerType.indexOf('xml') >= 0 || lowerType.indexOf('mpegurl') >= 0) return true;
                      return /player|parse|video|api|play|source|stream/.test(lowerUrl);
                    }
                    function collectFromPlayData(data, tag, collector){
                      try{
                        if(!data) return;
                        if(typeof data === 'string'){
                          collectFromText(data, tag, collector);
                          return;
                        }
                        var payload = data && data.data ? data.data : data;
                        collectFromText(JSON.stringify(payload), tag, collector);
                        if(payload && payload.url) collector.add(payload.url, tag + '-url');
                        if(payload && payload.src) collector.add(payload.src, tag + '-src');
                        var list = payload && payload.quality_urls;
                        if(list && typeof list.length === 'number'){
                          for(var i = 0; i < list.length; i++){
                            var item = list[i] || {};
                            collector.add(item.url, tag + '-quality');
                            collector.add(item.src, tag + '-quality-src');
                            collectFromText(JSON.stringify(item), tag + '-quality-json', collector);
                          }
                        }
                      }catch(e){}
                    }
                    function triggerCurrentEpisodeLoad(){
                      try{
                        var manager = window.episodeManagerInstance;
                        var target = null;
                        if(manager){
                          var currentSelector = 'a[data-line="' + (manager.currentLine || 1) + '"][data-episode="' + (manager.currentEpisode || 1) + '"][dataid]';
                          target = document.querySelector(currentSelector);
                        }
                        if(!target) target = document.querySelector('a.episode-link[dataid][data-line][data-episode]');
                        if(!target) return;
                        var dataid = (target.getAttribute('dataid') || '').trim();
                        if(!dataid) return;
                        var href = target.getAttribute('href') || location.href || fallback;
                        var line = parseInt(target.getAttribute('data-line') || (manager && manager.currentLine) || '1', 10);
                        if(!(line > 0)) line = 1;
                        var episode = parseInt(target.getAttribute('data-episode') || (manager && manager.currentEpisode) || '1', 10);
                        if(!(episode > 0)) episode = 1;
                        var secretKey = '';
                        try{
                          if(href.indexOf('/play/') >= 0){
                            secretKey = href.split('/play/')[1] || '';
                          }
                        }catch(e){}
                        if(manager && typeof manager.loadPlayUrl === 'function'){
                          manager.loadPlayUrl(dataid, secretKey, '1080', false, true).catch(function(){});
                          return;
                        }
                        if(manager && typeof manager.handleEpisodeClick === 'function'){
                          manager.handleEpisodeClick(href, dataid, line, episode);
                        }
                      }catch(e){}
                    }
                    function clickAdControls(){
                      try{
                        var sels = ['#wanrningconfirm','#warningconfirm','.confirm','.btn-confirm','.popup-confirm','.dialog-confirm','.enter','.enter-btn','.skip','.skip-btn','.skipad','.btn-skip','.ad-skip','.video-ad-skip','.close','.close-btn','.close-icon','.layui-layer-close','.icon-close','[data-dismiss]','[class*=confirm]','[id*=confirm]','[class*=skip]','[class*=close]','[id*=skip]','[id*=close]'];
                        for(var i = 0; i < sels.length; i++){
                          var nodes = document.querySelectorAll(sels[i]);
                          for(var j = 0; j < nodes.length; j++){
                            var el = nodes[j];
                            var text = ((el.innerText || el.textContent || '') + ' ' + (el.value || '')).toLowerCase();
                            if(!text || /skip|close|jump|continue|confirm|agree|allow|enter|play|warning|adult|18/.test(text)){
                              try{ el.click(); }catch(e){}
                              continue;
                            }
                            if(!text || /skip|close|jump|闂傚倸鍊搁崐宄懊归崶褏鏆﹂柛顭戝亝閸欏繘鏌熺紒銏犳灈缂佺姾顫夐妵鍕箛閸洘顎嶉梺绋款儛娴滎亪寮诲☉銏犖ㄩ柕蹇婂墲閻濇洟鎮楃憴鍕闁绘搫绻濆璇测槈濞嗘劕鍔呴梺鐐藉劜閸撴碍瀵奸崘顔解拺闁告繂瀚﹢鎵磼鐎ｎ偆澧辩紒顔款嚙閳藉濮€閻樻剚妫熼梺鍦帶閻°劑骞愭繝姘€堕柛鈩冾焽缁♀偓缂佸墽澧楄摫妞ゎ偄锕弻娑氣偓锝庝簼椤ャ垽鏌℃担鍝バｉ柟宄版嚇閹煎綊宕烽銊ч棷闂傚倷鑳堕…鍫ュ嫉椤掑嫭鍋＄憸蹇曞垝閺冨牜鏁嗛柛鏇ㄥ墰閸樺崬鈹戦悙鏉戠仴鐎规洦鍓熼幃姗€鏁撻悩宕囧幐闂佺硶鍓濆ú鏍х暤閸℃ɑ鍙忓┑鐘插暞閵囨繄鈧娲滈崗姗€銆佸鈧崺鍕礃闁款垰浜炬俊銈呮噺閳锋垿鏌涘☉妯峰闁兼祴鏅涢崹婵囩箾閸℃绂嬮柛銈嗘礃閵囧嫰骞掑鍫濆帯濡炪倐鏅滈悡锟犲蓟濞戞ǚ妲堥柛妤冨仧娴狀垳绱掗悙顒€鍔ら柕鍫熸倐瀵鏁撻悩鑼紲濠电偞鍨靛畷顒勫礉瀹€鍕拺缂佸娼￠妤冪磼缂佹ê娴柛鈹惧亾濡炪倖甯掗崰姘焽閹扮増鐓欓柛婵勫労閻掗箖鎽堕悙瀵哥瘈闂傚牊渚楅崕鎴犫偓瑙勬尫閻掞箓骞堥妸銉富閻犲洩寮撴竟鏇熶繆閻愵亜鈧垿宕瑰ú顏呮櫇闁靛繈鍊曠粻鏍煏韫囧鈧洖顔忓┑鍡忔斀闁绘ɑ褰冮弳鐔兼煟閿濆洤纾遍梻鍌氬€搁崐椋庣矆娓氣偓楠炴牠顢曚綅閸ヮ剦鏁冮柨鏇楀亾闁汇倗鍋撶换婵囩節閸屾稑娅ч梺娲诲幗閻熲晠寮婚悢鍛婄秶闁告挆鍛闂備焦妞块崢浠嬨€冩繝鍥ц摕婵炴垯鍨归悞娲煕閹板吀绨存俊鎻掔墢缁辨挻鎷呴崫鍕戙儳绱掗鍛仸濠碉紕鏁诲畷鐔碱敍濮橀硸鍟嬮梺鑽ゅЬ濞咃綁宕曢妶澶嬪€靛Δ锝呭暞閳锋垿鏌涘┑鍡楊伌婵″弶妞介弻锝夋晲鎼粹€斥拫濠殿喖锕︾划顖炲箯閸涘瓨鍊绘俊顖滃劋閻ｎ剟姊绘担鍓插悢闁哄鐏濋～鍥倵鐟欏嫭绀€缂傚秴锕獮鍐偩瀹€鈧惌娆撴偣娓氼垳鍘涙俊鏌ヤ憾濮婄粯鎷呴懞銉с€婇梺鍝ュУ閹稿骞堥妸鈺傚仺缂佸娉曢敍鐔兼⒑绾懏褰ч梻鍕瀹曟劙鎮介崨濠勫弳濠电娀娼уΛ娑㈠礄閸︻厾纾奸柕濞垮€楅惌娆撴煛?.test(text)){
                              try{ el.click(); }catch(e){}
                            }
                          }
                        }
                        var taps = document.querySelectorAll('button,a,div,span');
                        for(var k = 0; k < taps.length; k++){
                          var item = taps[k];
                          var label = ((item.innerText || item.textContent || '') + ' ' + (item.value || '')).trim();
                          if(label && /skip|close|continue|confirm|agree|allow|enter|play|warning|adult|18/i.test(label)){
                            try{ item.click(); }catch(e){}
                            continue;
                          }
                          if(label && /闂傚倸鍊搁崐宄懊归崶褏鏆﹂柛顭戝亝閸欏繘鏌熺紒銏犳灈缂佺姾顫夐妵鍕箛閸洘顎嶉梺绋款儛娴滎亪寮诲☉銏犖ㄩ柕蹇婂墲閻濇洟鎮楃憴鍕闁绘搫绻濆璇测槈濞嗘劕鍔呴梺鐐藉劜閸撴碍瀵奸崘顔解拺闁告繂瀚﹢鎵磼鐎ｎ偆澧辩紒顔款嚙閳藉濮€閻樻剚妫熼梺鍦帶閻°劑骞愭繝姘€堕柛鈩冾焽缁♀偓缂佸墽澧楄摫妞ゎ偄锕弻娑氣偓锝庝簼椤ャ垽鏌℃担鍝バｉ柟宄版嚇閹煎綊宕烽銊ч棷闂傚倷鑳堕…鍫ュ嫉椤掑嫭鍋＄憸蹇曞垝閺冨牜鏁嗛柛鏇ㄥ墰閸樺崬鈹戦悙鏉戠仴鐎规洦鍓熼幃姗€鏁撻悩宕囧幐闂佺硶鍓濆ú鏍х暤閸℃ɑ鍙忓┑鐘插暞閵囨繄鈧娲滈崗姗€銆佸鈧崺鍕礃闁款垰浜炬俊銈呮噺閳锋垿鏌涘☉妯峰闁兼祴鏅涢崹婵囩箾閸℃绂嬮柛銈嗘礃閵囧嫰骞掑鍫濆帯濡炪倐鏅滈悡锟犲蓟濞戞ǚ妲堥柛妤冨仧娴狀垳绱掗悙顒€鍔ら柕鍫熸倐瀵鏁撻悩鑼紲濠电偞鍨靛畷顒勫礉瀹€鍕拺缂佸娼￠妤冪磼缂佹ê娴柛鈹惧亾濡炪倖甯掗崰姘焽閹扮増鐓欓柛婵勫労閻掗箖鎽堕悙瀵哥瘈闂傚牊渚楅崕鎴犫偓瑙勬尫閻掞箓骞堥妸銉富閻犲洩寮撴竟鏇熶繆閻愵亜鈧垿宕瑰ú顏呮櫇闁靛繈鍊曠粻鏍煏韫囧鈧洖顔忓┑鍡忔斀闁绘ɑ褰冮弳鐔兼煟閿濆洤纾遍梻鍌氬€搁崐椋庣矆娓氣偓楠炴牠顢曚綅閸ヮ剦鏁冮柨鏇楀亾闁汇倗鍋撶换婵囩節閸屾稑娅ч梺娲诲幗閻熲晠寮婚悢鍛婄秶闁告挆鍛闂備焦妞块崢浠嬨€冩繝鍥ц摕婵炴垯鍨归悞娲煕閹板吀绨存俊鎻掔墢缁辨挻鎷呴崫鍕戙儳绱掗鍛仸濠碉紕鏁诲畷鐔碱敍濮橀硸鍟嬮梺鑽ゅЬ濞咃綁宕曢妶澶嬪€靛Δ锝呭暞閳锋垿鏌涘┑鍡楊伌婵″弶妞介弻锝夋晲鎼粹€斥拫濠殿喖锕︾划顖炲箯閸涘瓨鍊绘俊顖滃劋閻ｎ剟姊绘担鍓插悢闁哄鐏濋～鍥倵鐟欏嫭绀€缂傚秴锕獮鍐偩瀹€鈧惌娆撴偣娓氼垳鍘涙俊鏌ヤ憾濮婄粯鎷呴懞銉с€婇梺鍝ュУ閹稿骞堥妸鈺傚仺缂佸娉曢敍鐔兼⒑绾懏褰ч梻鍕瀹曟劙鎮介崨濠勫弳濠电娀娼уΛ娑㈠礄閸︻厾纾奸柕濞垮€楅惌娆撴煛瀹€瀣瘈鐎规洖宕灒缁炬媽椴稿▓瑙勪繆閻愵亜鈧垿宕濇繝鍥х？闁汇垻顭堢粻鏍ㄧ箾閸℃ɑ灏伴柛銈嗗灦閵囧嫰骞嬮敐鍛Х濠碉紕鍋撻幃鍌氼潖缂佹ɑ濯撮柣鎴灻▓灞解攽閳藉棗鐏犻柨鏇ㄤ簻椤曪綁濡搁埞褍娲ら‖濠傤嚈缁变簠close/i.test(label)){
                            try{ item.click(); }catch(e){}
                          }
                        }
                      }catch(e){}
                    }
                    function report(tag){
                      try{
                        var collector = createCollector();
                        var vids = document.querySelectorAll('video,source,audio');
                        for(var i = 0; i < vids.length; i++){
                          collector.add(vids[i].currentSrc || vids[i].src, 'video');
                          collector.add(vids[i].getAttribute('src'), 'media');
                          collector.add(vids[i].getAttribute('data-src'), 'media');
                        }
                        var links = document.querySelectorAll('a[href],iframe[src],iframe[data-src],embed[src],object[data],[data-play],[data-url],[data-player],[data-play-url],[data-href],[onclick]');
                        for(var k = 0; k < links.length; k++){
                          var node = links[k];
                          collector.add(node.getAttribute('href') || node.getAttribute('src') || node.getAttribute('data') || node.getAttribute('data-play') || node.getAttribute('data-url') || node.getAttribute('data-player') || node.getAttribute('data-play-url') || node.getAttribute('data-href'), node.tagName.toLowerCase());
                          collector.add(node.getAttribute('onclick'), 'onclick');
                        }
                        collectFromText(document.documentElement ? document.documentElement.outerHTML : '', 'html-' + tag, collector);
                        emit(collector.out);
                      }catch(e){
                        emit([], fallback);
                      }
                    }
                    if(!window.__xmSniffHooked){
                      window.__xmSniffHooked = 1;
                      try{
                        window.addEventListener('player:update', function(event){
                          try{
                            var collector = createCollector();
                            collectFromPlayData(event && event.detail, 'player-update', collector);
                            emit(collector.out, location.href || fallback);
                          }catch(e){}
                        }, true);
                      }catch(e){}
                      try{
                        var rawFetch = window.fetch;
                        if(rawFetch){
                          window.fetch = function(){
                            var requestUrl = '';
                            try{
                              var target = arguments[0];
                              requestUrl = (typeof target === 'string' ? target : (target && target.url) || '');
                              if(requestUrl) emit([{url:requestUrl, type:'fetch-call'}], requestUrl);
                            }catch(e){}
                            return rawFetch.apply(this, arguments).then(function(resp){
                              try{
                                var finalUrl = (resp && resp.url) || requestUrl || '';
                                if(finalUrl) emit([{url:finalUrl, type:'fetch'}], finalUrl);
                                var contentType = '';
                                try{ contentType = (resp && resp.headers && resp.headers.get && resp.headers.get('content-type')) || ''; }catch(e){}
                                if(resp && resp.clone && shouldInspectBody(finalUrl, contentType)){
                                  resp.clone().text().then(function(text){
                                    var collector = createCollector();
                                    collectFromText(text, 'fetch-body', collector);
                                    emit(collector.out, finalUrl);
                                  }).catch(function(){});
                                }
                              }catch(e){}
                              return resp;
                            });
                          };
                        }
                      }catch(e){}
                      try{
                        var xhrOpen = XMLHttpRequest.prototype.open;
                        XMLHttpRequest.prototype.open = function(method, u){
                          this.__xmUrl = u;
                          return xhrOpen.apply(this, arguments);
                        };
                        var xhrSend = XMLHttpRequest.prototype.send;
                        XMLHttpRequest.prototype.send = function(){
                          var xhr = this;
                          function done(){
                            try{
                              var finalUrl = xhr.responseURL || xhr.__xmUrl || '';
                              if(finalUrl) emit([{url:finalUrl, type:'xhr'}], finalUrl);
                              var responseType = xhr.responseType || '';
                              var contentType = '';
                              try{ contentType = xhr.getResponseHeader('content-type') || ''; }catch(e){}
                              if(!responseType || responseType === 'text' || responseType === 'json' || shouldInspectBody(finalUrl, contentType)){
                                var body = '';
                                try{
                                  if(responseType === 'json' && xhr.response){
                                    body = JSON.stringify(xhr.response);
                                  }else if(typeof xhr.responseText === 'string'){
                                    body = xhr.responseText;
                                  }else if(typeof xhr.response === 'string'){
                                    body = xhr.response;
                                  }
                                }catch(e){}
                                if(body){
                                  var collector = createCollector();
                                  collectFromText(body, 'xhr-body', collector);
                                  emit(collector.out, finalUrl);
                                }
                              }
                            }catch(e){}
                          }
                          xhr.addEventListener('load', done);
                          return xhrSend.apply(this, arguments);
                        };
                      }catch(e){}
                    }
                    clickAdControls();
                    report('now');
                    triggerCurrentEpisodeLoad();
                    setTimeout(function(){ triggerCurrentEpisodeLoad(); }, 350);
                    setTimeout(function(){ clickAdControls(); report('delay1'); }, 1200);
                    setTimeout(function(){ triggerCurrentEpisodeLoad(); }, 1500);
                    setTimeout(function(){ clickAdControls(); report('delay2'); }, 3200);
                    setTimeout(function(){ triggerCurrentEpisodeLoad(); }, 3600);
                    setTimeout(function(){ clickAdControls(); report('delay3'); }, 5600);
                  }catch(e){
                    HermesPlayer.onSniffResult('[]', __DEPTH__, __FALLBACK__);
                  }
                })();
                """
                .replace("__FALLBACK__", fallbackUrl)
                .replace("__DEPTH__", String.valueOf(depth));
    }

    private void captureSniff(String url, int depth, boolean fromDom) {
        captureSniff(url, depth, fromDom, fromDom ? "dom" : "resource");
    }

    private void captureSniff(String url, int depth, boolean fromDom, String origin) {
        String normalized = normalizeSniffUrl(url, sniffCurrentUrl);
        if (!sniffing || !shouldSniffUrl(normalized)) return;
        if (looksLikeMedia(normalized)) {
            if (looksLikeDirectMedia(normalized)) {
                String sniffOrigin = safe(origin);
                if (sniffOrigin.isEmpty()) sniffOrigin = fromDom ? "dom" : "resource";
                rememberSniffCandidate(normalized, depth, sniffOrigin, sniffCurrentUrl);
            } else if (shouldFollowPage(normalized)) {
                enqueueSniffFrame(normalized, depth + 1);
                runOnUiThread(() -> scheduleNextSniffTask(160L));
            }
            return;
        }
        if (!fromDom && !shouldFollowPage(normalized)) return;
        enqueueSniffFrame(normalized, depth + 1);
        runOnUiThread(() -> scheduleNextSniffTask(160L));
    }

    private void rememberSniffCandidate(String url, int depth, String origin, String pageUrl) {
        final String candidateUrl = normalizeSniffUrl(url, pageUrl);
        if (candidateUrl.isEmpty()) return;
        final String candidateOrigin = safe(origin);
        final String candidatePage = safe(pageUrl);
        runOnUiThread(() -> {
            if (!sniffing || !looksLikeDirectMedia(candidateUrl) || !shouldSniffUrl(candidateUrl)) return;
            if (rejectedSniffUrls.contains(candidateUrl)) return;
            int score = scoreSniffCandidate(candidateUrl, depth, candidateOrigin, candidatePage);
            boolean updated = false;
            for (SniffCandidate candidate : sniffCandidates) {
                if (candidate.url.equals(candidateUrl)) {
                    candidate.score = Math.max(candidate.score, score);
                    candidate.depth = Math.min(candidate.depth, depth);
                    if (candidate.pageUrl.isEmpty()) candidate.pageUrl = candidatePage;
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                sniffCandidates.add(new SniffCandidate(candidateUrl, candidatePage, candidateOrigin, depth, score));
            }
            showState("\u5df2\u627e\u5230\u5a92\u4f53\u5730\u5740\uff0c\u6b63\u5728\u9009\u62e9\u6700\u4f73\u6e05\u6670\u5ea6...", true, 1f);
            handler.removeCallbacks(playBestSniffCandidate);
            handler.postDelayed(playBestSniffCandidate, score >= 130 ? 1100 : 1900);
        });
    }

    private boolean chooseBestSniffCandidate(boolean finalAttempt) {
        handler.removeCallbacks(playBestSniffCandidate);
        if (sniffCandidates.isEmpty()) {
            return false;
        }
        SniffCandidate best = null;
        for (SniffCandidate candidate : sniffCandidates) {
            if (rejectedSniffUrls.contains(candidate.url)) {
                continue;
            }
            if (best == null || candidate.score > best.score) {
                best = candidate;
            }
        }
        if (best == null) {
            return false;
        }
        applySniffCandidateHeaders(best);
        sniffing = false;
        showState(finalAttempt ? "\u4f7f\u7528\u6700\u4f73\u55c5\u63a2\u5730\u5740\u5f00\u59cb\u64ad\u653e..." : "\u5df2\u627e\u5230\u5a92\u4f53\u5730\u5740\uff0c\u5f00\u59cb\u64ad\u653e...", true, 1f);
        playInPlace(best.url, true, best.pageUrl);
        return true;
    }

    private void applySniffCandidateHeaders(SniffCandidate candidate) {
        if (candidate == null) {
            return;
        }
        String referer = safe(candidate.pageUrl);
        if (referer.isEmpty()) {
            referer = safe(sniffCurrentUrl);
        }
        if (referer.isEmpty()) {
            referer = safe(source == null ? "" : source.host);
        }
        if (!referer.isEmpty()) {
            activeHeaders.put("Referer", referer);
            String origin = buildOriginFromUrl(referer);
            if (!origin.isEmpty()) {
                activeHeaders.put("Origin", origin);
            }
        }
        String cookie = mergeCookieStrings(
                activeHeaders.get("Cookie"),
                collectCookieHeader(candidate.url),
                collectCookieHeader(candidate.pageUrl),
                collectCookieHeader(sniffCurrentUrl),
                collectCookieHeader(source == null ? "" : source.host)
        );
        if (!cookie.isEmpty()) {
            activeHeaders.put("Cookie", cookie);
        }
        if (!activeHeaders.containsKey("User-Agent")) {
            activeHeaders.put("User-Agent", DEFAULT_MOBILE_UA);
        }
    }

    private int scoreSniffCandidate(String url, int depth, String origin, String pageUrl) {
        String lower = safe(url).toLowerCase(Locale.ROOT);
        String originLower = safe(origin).toLowerCase(Locale.ROOT);
        int score = 40 + mediaFingerprintScore(lower);
        if ("intercept".equalsIgnoreCase(origin) || "resource".equalsIgnoreCase(origin)) score += 18;
        if ("dom".equalsIgnoreCase(origin)) score += 10;
        if (originLower.startsWith("fetch") || originLower.startsWith("xhr")) score += 16;
        if (originLower.contains("body")) score += 24;
        if (originLower.startsWith("html")) score += 12;
        if (originLower.contains("decoded") || originLower.contains("unicode")) score += 4;
        score -= Math.max(0, depth) * 7;
        if (!safe(pageUrl).isEmpty() && sameHost(url, pageUrl)) score += 10;
        if (source != null && !safe(source.host).isEmpty() && sameHost(url, source.host)) score += 6;
        if (isAdOrNoise(lower)) score -= 70;
        if (lower.contains("preview") || lower.contains("sample") || lower.contains("sprite") || lower.contains("storyboard")) score -= 35;
        return score;
    }

    private int mediaFingerprintScore(String lower) {
        if (lower.contains(".m3u8") || lower.contains("/m3u8") || lower.contains("application/vnd.apple.mpegurl")) return 90;
        if (lower.contains(".mp4") || lower.contains("video_mp4")) return 78;
        if (lower.contains(".flv") || lower.contains(".mkv") || lower.contains(".webm")) return 70;
        if (lower.contains(".mpd")) return 62;
        if (lower.contains("mime=video") || lower.contains("mime_type=video") || lower.contains("obj/tos")) return 66;
        return 35;
    }

    private boolean isAdOrNoise(String lower) {
        return lower.contains("googleads")
                || lower.contains("doubleclick")
                || lower.contains("analytics")
                || lower.contains("tracker")
                || lower.contains("adsystem")
                || lower.contains("/ads/")
                || lower.contains("advert")
                || lower.contains("favicon");
    }

    private boolean sameHost(String left, String right) {
        try {
            URL leftUrl = new URL(left);
            URL rightUrl = new URL(right);
            return leftUrl.getHost().equalsIgnoreCase(rightUrl.getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean looksLikeMedia(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("blob:") || lower.startsWith("data:")) return false;
        if (matchesRule(snifferMediaRules, lower)) return true;
        return lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".flv")
                || lower.contains(".mkv") || lower.contains(".mpd") || lower.contains(".ts")
                || lower.contains(".m2ts") || lower.contains("mime=video")
                || lower.contains("/m3u8") || lower.contains("video_mp4")
                || lower.contains("application/vnd.apple.mpegurl")
                || lower.contains("response-content-type=video")
                || lower.contains("mime_type=video")
                || lower.contains("obj/tos");
    }

    private boolean looksLikeDirectMedia(String url) {
        if (!looksLikeMedia(url)) {
            return false;
        }
        String lower = safe(url).toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return false;
        }
        if (isLikelyParserLikeMediaUrl(lower)) {
            return false;
        }
        return true;
    }

    private boolean isLikelyParserLikeMediaUrl(String lower) {
        if (safe(lower).isEmpty()) {
            return false;
        }
        boolean parserPath = lower.contains(".php")
                || lower.contains(".html")
                || lower.contains(".asp")
                || lower.contains(".aspx")
                || lower.contains(".jsp")
                || lower.contains("/player")
                || lower.contains("player.php")
                || lower.contains("/parse")
                || lower.contains("parse.php")
                || lower.contains("/api.php")
                || lower.contains("/jx")
                || lower.contains("url=http")
                || lower.contains("url=https")
                || lower.contains("v=http")
                || lower.contains("v=https");
        boolean mediaPath = lower.matches(".*(\\.m3u8|\\.mp4|\\.flv|\\.mkv|\\.mpd|\\.ts|\\.m2ts)(\\?.*)?$")
                || lower.contains("/m3u8")
                || lower.contains("mime=video")
                || lower.contains("mime_type=video")
                || lower.contains("application/vnd.apple.mpegurl")
                || lower.contains("response-content-type=video")
                || lower.contains("obj/tos");
        return parserPath && !mediaPath;
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

    private Map<String, String> buildPlayerHeaders() {
        Map<String, String> raw = parseHeaders(buildHeadersJson());
        HashMap<String, String> headers = new HashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String key = normalizeHeaderName(entry.getKey());
            String value = safe(entry.getValue());
            if (!key.isEmpty() && !value.isEmpty()) {
                headers.put(key, value);
            }
        }
        if (!headers.containsKey("Accept")) {
            headers.put("Accept", "*/*");
        }
        if (!headers.containsKey("Accept-Language")) {
            headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        }
        if (!headers.containsKey("Referer") && !safe(currentPlaybackPageUrl).isEmpty()) {
            headers.put("Referer", currentPlaybackPageUrl);
        }
        if (!headers.containsKey("Origin")) {
            String origin = buildOriginFromUrl(headers.get("Referer"));
            if (origin.isEmpty()) {
                origin = buildOriginFromUrl(currentPlaybackPageUrl);
            }
            if (!origin.isEmpty()) {
                headers.put("Origin", origin);
            }
        }
        return headers;
    }

    private void preparePlayer(Map<String, String> headers, boolean firstAttempt) {
        if (safe(playUrl).isEmpty()) {
            throw new IllegalStateException("empty play url");
        }
        if (firstAttempt) {
            streamTypes.clear();
            streamTypes.addAll(buildStreamTypeQueue(playUrl, headers));
            streamTypeIndex = -1;
        }
        if (!prepareCurrentStreamType(headers, firstAttempt)) {
            throw new IllegalStateException("no playable stream type");
        }
    }

    private void prepareDkPlayer(String mediaUrl, Map<String, String> headers) {
        if (dkPlayerView == null) {
            throw new IllegalStateException("DKVideoPlayer view missing");
        }
        releaseArtPlayer();
        releaseMediaPlayer();
        preparedNotified = false;
        cancelPrepareTimeout();
        dkPlayerView.setVisibility(View.VISIBLE);
        if (artPlayerWebView != null) {
            artPlayerWebView.setVisibility(View.GONE);
        }
        if (playerView != null) {
            playerView.setVisibility(View.GONE);
        }
        dkPlayerView.setPlayerFactory(XiaomaoDkExoPlayerFactory.create());
        dkPlayerView.setScreenScaleType(dkScreenScaleType);
        dkPlayerView.setSpeed(tempSpeedBoost ? 2.0f : selectedSpeed);
        dkPlayerView.setUrl(mediaUrl, headers);
        dkPlayerView.start();
        schedulePrepareTimeout();
    }

    private void handleDkPlayState(int playState) {
        if (playState == VideoView.STATE_PREPARING || playState == VideoView.STATE_BUFFERING) {
            showState("\u6b63\u5728\u7f13\u51b2\u89c6\u9891...", true, 0.92f);
            return;
        }
        if (playState == VideoView.STATE_PREPARED
                || playState == VideoView.STATE_PLAYING
                || playState == VideoView.STATE_BUFFERED) {
            if (dkPlayerView != null) {
                dkPlayerView.setSpeed(tempSpeedBoost ? 2.0f : selectedSpeed);
            }
            preparedNotified = true;
            cancelPrepareTimeout();
            showReadyState();
            return;
        }
        if (playState == VideoView.STATE_ERROR) {
            String message = "DKVideoPlayer \u64ad\u653e\u5f02\u5e38\uff0c\u8bf7\u6362\u7ebf\u8def\u518d\u8bd5";
            if (!recoverFromPlaybackFailure(message)) {
                showError(message);
            }
            return;
        }
        if (playState == VideoView.STATE_PLAYBACK_COMPLETED) {
            playbackPosition = 0L;
            showState("\u64ad\u653e\u5b8c\u6210", false, 0.95f);
        }
    }
    private boolean prepareCurrentStreamType(Map<String, String> headers, boolean firstAttempt) {
        if (firstAttempt) {
            streamTypeIndex = 0;
        } else {
            streamTypeIndex++;
        }
        if (streamTypeIndex < 0 || streamTypeIndex >= streamTypes.size()) {
            return false;
        }

        cancelPrepareTimeout();
        releaseMediaPlayer();
        preparedNotified = false;

        StreamType streamType = streamTypes.get(streamTypeIndex);
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs((int) PREPARE_TIMEOUT_MS)
                .setReadTimeoutMs((int) PREPARE_TIMEOUT_MS);
        String userAgent = headers.get("User-Agent");
        httpFactory.setUserAgent(TextUtils.isEmpty(userAgent) ? DEFAULT_MOBILE_UA : userAgent);
        if (!headers.isEmpty()) {
            httpFactory.setDefaultRequestProperties(headers);
        }
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, httpFactory);
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true);
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);

        mediaPlayer = new ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();
        mediaPlayer.addListener(playerListener);
        mediaPlayer.setPlayWhenReady(playWhenReady);
        mediaPlayer.setPlaybackParameters(new PlaybackParameters(tempSpeedBoost ? 2.0f : selectedSpeed));
        playerView.setPlayer(mediaPlayer);
        playerView.setResizeMode(resizeMode);

        MediaItem mediaItem = buildMediaItem(playUrl, streamType);
        mediaPlayer.setMediaSource(buildMediaSource(mediaItem, dataSourceFactory, streamType));
        if (playbackPosition > 0L) {
            mediaPlayer.seekTo(playbackPosition);
        }
        mediaPlayer.prepare();
        schedulePrepareTimeout();
        return true;
    }

    private boolean retryWithNextStreamType() {
        if (safe(playUrl).isEmpty()) {
            return false;
        }
        try {
            return prepareCurrentStreamType(buildPlayerHeaders(), false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void schedulePrepareTimeout() {
        handler.removeCallbacks(prepareTimeoutRunnable);
        handler.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS);
    }

    private void cancelPrepareTimeout() {
        handler.removeCallbacks(prepareTimeoutRunnable);
    }

    private void releaseMediaPlayer() {
        cancelPrepareTimeout();
        handler.removeCallbacks(longPressSpeedRunnable);
        if (mediaPlayer == null) {
            return;
        }
        playbackPosition = Math.max(0L, mediaPlayer.getCurrentPosition());
        playWhenReady = mediaPlayer.getPlayWhenReady();
        mediaPlayer.removeListener(playerListener);
        mediaPlayer.release();
        mediaPlayer = null;
        if (playerView != null) {
            playerView.setPlayer(null);
        }
    }

    private void releaseDkPlayer() {
        cancelPrepareTimeout();
        handler.removeCallbacks(longPressSpeedRunnable);
        if (dkPlayerView == null) {
            return;
        }
        try {
            playbackPosition = Math.max(0L, dkPlayerView.getCurrentPosition());
            playWhenReady = dkPlayerView.isPlaying();
            dkPlayerView.release();
        } catch (Exception ignored) {
        }
    }

    private void releaseArtPlayer() {
        artPlayerReady = false;
        artPlayerFullscreen = false;
        artPlayerWebFullscreen = false;
        if (artPlayerWebView != null) {
            try {
                artPlayerWebView.evaluateJavascript("window.xmPlayerDestroy && window.xmPlayerDestroy();", null);
                artPlayerWebView.loadUrl("about:blank");
            } catch (Exception ignored) {
            }
        }
        hideArtPlayerCustomView();
        updateResizeButton();
    }

    private ArrayList<StreamType> buildStreamTypeQueue(String url, Map<String, String> headers) {
        LinkedHashSet<StreamType> ordered = new LinkedHashSet<>();
        ordered.add(StreamType.AUTO);
        StreamType primary = inferPrimaryStreamType(url, headers);
        if (primary != null) {
            ordered.add(primary);
        }
        ordered.add(StreamType.HLS);
        ordered.add(StreamType.PROGRESSIVE);
        ordered.add(StreamType.DASH);
        return new ArrayList<>(ordered);
    }

    private StreamType inferPrimaryStreamType(String url, Map<String, String> headers) {
        String normalized = decodeUrl(url).toLowerCase(Locale.ROOT);
        String contentType = "";
        if (headers != null) {
            String headerValue = headers.get("Content-Type");
            if (TextUtils.isEmpty(headerValue)) {
                headerValue = headers.get("content-type");
            }
            contentType = safe(headerValue).toLowerCase(Locale.ROOT);
        }
        if (normalized.contains(".m3u8")
                || normalized.contains("/m3u8")
                || normalized.contains("m3u8?")
                || normalized.contains("type=m3u8")
                || normalized.contains("format=m3u8")
                || contentType.contains("mpegurl")
                || contentType.contains("x-mpegurl")) {
            return StreamType.HLS;
        }
        if (normalized.contains(".mpd")
                || normalized.contains("type=mpd")
                || contentType.contains("dash+xml")) {
            return StreamType.DASH;
        }
        if (normalized.contains(".mp4")
                || normalized.contains(".ts")
                || normalized.contains(".flv")
                || normalized.contains(".mkv")
                || normalized.contains("video_mp4")
                || normalized.contains("mime_type=video")
                || normalized.contains("response-content-type=video")
                || normalized.contains("download=1")
                || normalized.contains("obj/tos")
                || contentType.startsWith("video/")) {
            return StreamType.PROGRESSIVE;
        }
        return StreamType.AUTO;
    }

    private MediaItem buildMediaItem(String url, StreamType streamType) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(safe(url)));
        if (streamType == StreamType.HLS) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        } else if (streamType == StreamType.DASH) {
            builder.setMimeType(MimeTypes.APPLICATION_MPD);
        } else if (streamType == StreamType.PROGRESSIVE) {
            builder.setMimeType(inferProgressiveMimeType(url));
        }
        return builder.build();
    }

    private MediaSource buildMediaSource(MediaItem mediaItem, DataSource.Factory dataSourceFactory, StreamType streamType) {
        if (streamType == StreamType.AUTO) {
            return new DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem);
        }
        if (streamType == StreamType.HLS) {
            return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        }
        if (streamType == StreamType.DASH) {
            return new DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        }
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true);
        return new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory).createMediaSource(mediaItem);
    }

    private String inferProgressiveMimeType(String url) {
        String normalized = decodeUrl(url).toLowerCase(Locale.ROOT);
        if (normalized.contains(".mp4")) {
            return MimeTypes.VIDEO_MP4;
        }
        if (normalized.contains(".ts") || normalized.contains(".m2ts")) {
            return MimeTypes.VIDEO_MP2T;
        }
        if (normalized.contains(".flv")) {
            return "video/x-flv";
        }
        if (normalized.contains(".mkv")) {
            return "video/x-matroska";
        }
        return null;
    }

    private String decodeUrl(String url) {
        String value = safe(url).replace("\\/", "/");
        if (value.contains("%")) {
            try {
                value = java.net.URLDecoder.decode(value, "UTF-8");
            } catch (Exception ignored) {
            }
        }
        return value;
    }

    private String normalizeHeaderName(String name) {
        String lower = safe(name).toLowerCase(Locale.ROOT);
        if ("user-agent".equals(lower)) return "User-Agent";
        if ("referer".equals(lower)) return "Referer";
        if ("cookie".equals(lower)) return "Cookie";
        if ("accept".equals(lower)) return "Accept";
        if ("accept-language".equals(lower)) return "Accept-Language";
        if ("content-type".equals(lower)) return "Content-Type";
        return safe(name);
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
            Toast.makeText(this, "\u672a\u627e\u5230\u53ef\u7528\u7684\u5916\u90e8\u64ad\u653e\u5668", Toast.LENGTH_SHORT).show();
            showError("\u672a\u627e\u5230\u53ef\u7528\u7684\u5916\u90e8\u64ad\u653e\u5668");
        }
    }

    private void showReadyState() {
        showState("\u5f00\u59cb\u64ad\u653e", false, 1f);
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

    private void showLoadingState(String text) {
        showState(text, true, 1f);
    }

    private void showError(String text) {
        sniffing = false;
        releaseSniffer();
        showState(text, false, 1f);
    }

    private void releaseSniffer() {
        stopSniffer(false);
    }

    private void stopSniffer(boolean keepCandidates) {
        sniffQueue.clear();
        sniffVisited.clear();
        handler.removeCallbacks(playBestSniffCandidate);
        handler.removeCallbacks(advanceSniffQueueRunnable);
        if (!keepCandidates) {
            sniffCandidates.clear();
        }
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

    private boolean recoverFromPlaybackFailure(String message) {
        if (sniffing) {
            return chooseBestSniffCandidate(true);
        }
        String failedUrl = normalizeSniffUrl(playUrl, currentPlaybackPageUrl);
        if (currentPlaybackFromSniff && !failedUrl.isEmpty()) {
            rejectedSniffUrls.add(failedUrl);
            if (chooseBestSniffCandidate(true)) {
                return true;
            }
        }
        if (!autoSniffRecoveryTried) {
            String sniffEntry = resolveSniffEntryUrl(currentPlaybackPageUrl);
            if (!sniffEntry.isEmpty()) {
                autoSniffRecoveryTried = true;
                startSniff(sniffEntry, "\u76f4\u94fe\u64ad\u653e\u5931\u8d25\uff0c\u6b63\u5728\u5c1d\u8bd5\u7f51\u9875\u55c5\u63a2\u2026");
                return true;
            }
        }
        return false;
    }

    private String resolveSniffEntryUrl(String preferred) {
        String[] candidates = new String[]{
                preferred,
                input,
                currentPlaybackPageUrl,
                sniffCurrentUrl
        };
        for (String candidate : candidates) {
            String resolved = sanitizeSniffEntry(candidate);
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }
        return "";
    }

    private boolean shouldUseWebHlsBridge(String mediaUrl) {
        String lower = safe(mediaUrl).toLowerCase(Locale.ROOT);
        if (!lower.contains(".m3u8") && !lower.contains("/m3u8/")) {
            return false;
        }
        String marker = (source == null ? "" : safe(source.title) + " " + safe(source.host) + " " + safe(source.raw))
                .toLowerCase(Locale.ROOT);
        return marker.contains("4kvm.me")
                || marker.contains("4kvm")
                || lower.contains("zijieapi.douyinbyte.com/m3u8/");
    }

    private String sanitizeSniffEntry(String raw) {
        String value = safe(raw);
        if (value.isEmpty()) {
            return "";
        }
        int split = value.indexOf("@@");
        if (split > 0) {
            value = value.substring(0, split);
        }
        value = normalizeSniffUrl(value, source == null ? "" : source.host);
        if (value.isEmpty()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (looksLikeDirectMedia(value) && !isLikelyParserLikeMediaUrl(lower)) {
            return "";
        }
        if (value.equals(playUrl)) {
            return "";
        }
        return value;
    }
    @Override
    public void onBackPressed() {
        if (dkPlayerView != null && dkPlayerView.onBackPressed()) {
            return;
        }
        if (fullscreenCustomView != null) {
            hideArtPlayerCustomView();
            return;
        }
        if (artPlayerWebFullscreen && artPlayerWebView != null) {
            artPlayerWebView.evaluateJavascript("window.xmPlayerToggleWebFullscreen && window.xmPlayerToggleWebFullscreen();", null);
            return;
        }
        if (portraitPlayerMode) {
            applyPlayerBoxMode(false);
            return;
        }
        returnToMainPage();
    }

    private void returnToMainPage() {
        releaseSniffer();
        releaseDkPlayer();
        releaseMediaPlayer();
        releaseArtPlayer();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (artPlayerWebView != null) artPlayerWebView.onResume();
        if (dkPlayerView != null && playWhenReady) {
            dkPlayerView.resume();
        }
        if (mediaPlayer != null && playWhenReady) {
            mediaPlayer.play();
        }
        if (sniffWeb != null) sniffWeb.onResume();
    }

    @Override
    protected void onPause() {
        if (sniffWeb != null) sniffWeb.onPause();
        if (artPlayerWebView != null) artPlayerWebView.onPause();
        if (dkPlayerView != null) {
            playbackPosition = Math.max(0L, dkPlayerView.getCurrentPosition());
            playWhenReady = dkPlayerView.isPlaying();
            dkPlayerView.pause();
        }
        if (mediaPlayer != null) {
            playbackPosition = Math.max(0L, mediaPlayer.getCurrentPosition());
            playWhenReady = mediaPlayer.getPlayWhenReady();
            mediaPlayer.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        handler.removeCallbacksAndMessages(null);
        releaseSniffer();
        releaseDkPlayer();
        releaseMediaPlayer();
        releaseArtPlayer();
        if (artPlayerWebView != null) {
            try {
                artPlayerWebView.removeJavascriptInterface("XmVideoBridge");
                artPlayerWebView.destroy();
            } catch (Exception ignored) {
            }
            artPlayerWebView = null;
        }
        super.onDestroy();
    }

    private TextView makeText(String text, int sp, int colorRes, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color(colorRes));
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView makeChip(String text, int bgRes, int strokeRes, int textRes) {
        TextView chip = makeText(text, 11, textRes, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), 0, dp(10), 0);
        chip.setBackground(cardBgRes(bgRes, strokeRes, 16));
        return chip;
    }

    private GradientDrawable cardBg(int color, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable cardBgRes(int colorRes, int strokeRes, int radiusDp) {
        return cardBg(color(colorRes), color(strokeRes), radiusDp);
    }

    private int color(int colorRes) {
        return ContextCompat.getColor(this, colorRes);
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
            if (!object.has("Origin")) {
                String origin = buildOriginFromUrl(object.optString("Referer", ""));
                if (origin.isEmpty() && source != null) {
                    origin = buildOriginFromUrl(source.host);
                }
                if (!origin.isEmpty()) {
                    object.put("Origin", origin);
                }
            }
            if (!object.has("User-Agent")) {
                object.put("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            }
            if (!object.has("Accept-Language")) {
                object.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            }
            String cookies = mergeCookieStrings(
                    object.optString("Cookie", ""),
                    collectCookieHeader(playUrl),
                    collectCookieHeader(currentPlaybackPageUrl),
                    collectCookieHeader(sniffCurrentUrl),
                    collectCookieHeader(source == null ? "" : source.host)
            );
            if (!cookies.isEmpty()) {
                object.put("Cookie", cookies);
            }
            if (!object.has("Accept")) {
                object.put("Accept", "*/*");
            }
            return object.toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String buildOriginFromUrl(String url) {
        String clean = safe(url);
        if (clean.isEmpty()) {
            return "";
        }
        try {
            URL parsed = new URL(clean);
            String protocol = safe(parsed.getProtocol());
            String host = safe(parsed.getHost());
            if (protocol.isEmpty() || host.isEmpty()) {
                return "";
            }
            int port = parsed.getPort();
            if (port > 0 && port != parsed.getDefaultPort()) {
                return protocol + "://" + host + ":" + port;
            }
            return protocol + "://" + host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private void ensureAdultGateBypass(String url) {
        String clean = safe(url);
        if (!isAdultGateHost(clean)) {
            return;
        }
        try {
            CookieManager manager = CookieManager.getInstance();
            manager.setAcceptCookie(true);
            if (sniffWeb != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                manager.setAcceptThirdPartyCookies(sniffWeb, true);
            }
            for (String target : adultGateCookieTargets(clean)) {
                manager.setCookie(target, "user-choose=true; Path=/");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                manager.flush();
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isAdultGateHost(String url) {
        String lower = safe(url).toLowerCase(Locale.ROOT);
        return lower.contains("51cg1.com")
                || lower.contains("isppven.com")
                || lower.contains("chigua.com")
                || lower.contains("51cg");
    }

    private ArrayList<String> adultGateCookieTargets(String url) {
        ArrayList<String> targets = new ArrayList<>();
        try {
            URL parsed = new URL(url);
            String protocol = parsed.getProtocol();
            String host = parsed.getHost();
            if (!host.isEmpty()) {
                targets.add(protocol + "://" + host + "/");
                String[] parts = host.split("\\.");
                if (parts.length >= 2) {
                    String root = parts[parts.length - 2] + "." + parts[parts.length - 1];
                    targets.add(protocol + "://" + root + "/");
                    targets.add("https://" + root + "/");
                }
            }
        } catch (Exception ignored) {
        }
        if (targets.isEmpty() && !safe(url).isEmpty()) {
            targets.add(url);
        }
        return targets;
    }

    private String collectCookieHeader(String url) {
        String clean = safe(url);
        if (clean.isEmpty()) return "";
        try {
            ensureAdultGateBypass(clean);
            String cookies = safe(CookieManager.getInstance().getCookie(clean));
            if (isAdultGateHost(clean)) {
                return mergeCookieStrings(cookies, "user-choose=true");
            }
            return cookies;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String mergeCookieStrings(String... values) {
        java.util.LinkedHashMap<String, String> merged = new java.util.LinkedHashMap<>();
        for (String value : values) {
            String cookieHeader = safe(value);
            if (cookieHeader.isEmpty()) continue;
            String[] parts = cookieHeader.split(";");
            for (String part : parts) {
                String item = safe(part);
                int split = item.indexOf('=');
                if (split <= 0) continue;
                String name = item.substring(0, split).trim();
                if (!name.isEmpty()) merged.put(name, item);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (String item : merged.values()) {
            if (builder.length() > 0) builder.append("; ");
            builder.append(item);
        }
        return builder.toString();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private final class ArtPlayerBridge {

        @JavascriptInterface
        public void onPlayerReady() {
            runOnUiThread(NativePlayerActivity.this::showReadyState);
        }

        @JavascriptInterface
        public void onPlayerError(String message) {
            runOnUiThread(() -> {
                String error = safe(message).isEmpty() ? "Artplayer \u64ad\u653e\u5f02\u5e38" : safe(message);
                if (!recoverFromPlaybackFailure(error)) {
                    showError(error);
                }
            });
        }

        @JavascriptInterface
        public void onFullscreenChanged(String value) {
            artPlayerFullscreen = "1".equals(safe(value));
        }

        @JavascriptInterface
        public void onWebFullscreenChanged(String value) {
            artPlayerWebFullscreen = "1".equals(safe(value));
            runOnUiThread(NativePlayerActivity.this::updateResizeButton);
        }

        @JavascriptInterface
        public void onRateChanged(String value) {
            try {
                selectedSpeed = Float.parseFloat(safe(value));
            } catch (Exception ignored) {
            }
            runOnUiThread(NativePlayerActivity.this::updateSpeedButton);
        }
    }

    private final class PlayerBridge {

        @JavascriptInterface
        public void onSniffResult(String payload, int depth, String pageUrl) {
            if (!sniffing) return;
            ArrayList<String> nested = new ArrayList<>();
            boolean foundMedia = false;
            try {
                JSONArray array = new JSONArray(payload == null ? "[]" : payload);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    String url = object == null ? array.optString(i) : object.optString("url");
                    String type = object == null ? "" : object.optString("type");
                    if (looksLikeMedia(url) && shouldSniffUrl(url)) {
                        captureSniff(url, depth, true, type);
                        foundMedia = true;
                        continue;
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
            final boolean hasMedia = foundMedia;
            runOnUiThread(() -> scheduleNextSniffTask(hasMedia ? 1500L : 120L));
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

    private static final class SniffCandidate {
        final String url;
        String pageUrl;
        final String origin;
        int depth;
        int score;

        SniffCandidate(String url, String pageUrl, String origin, int depth, int score) {
            this.url = url;
            this.pageUrl = pageUrl == null ? "" : pageUrl;
            this.origin = origin == null ? "" : origin;
            this.depth = depth;
            this.score = score;
        }
    }
}


