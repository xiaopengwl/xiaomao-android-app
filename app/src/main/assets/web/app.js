(function () {
  const android = window.AndroidApp || null;
  const MOBILE_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36";
  const PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36";
  const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
  const RULE_KEYS = {
    preprocess: ["\u9884\u5904\u7406"],
    recommend: ["\u63a8\u8350"],
    first: ["\u4e00\u7ea7"],
    second: ["\u4e8c\u7ea7"],
    search: ["\u641c\u7d22"],
  };
  const DEFAULT_SNIFFER_MATCH_RULES = [
    "\\.m3u8(\\?|$)",
    "\\.mp4(\\?|$)",
    "\\.m4v(\\?|$)",
    "\\.flv(\\?|$)",
    "\\.webm(\\?|$)",
    "\\.mpd(\\?|$)",
    "\\.mp3(\\?|$)",
    "mime=video",
    "mime_type=video",
    "video_mp4",
    "video/tos",
    "/playlist/",
    "/stream/",
    "playurl",
    "vurl=",
    "url=",
    "m3u8\\?pt=",
    "obj/tos",
    "blob:http",
  ];
  const DEFAULT_SNIFFER_EXCLUDE_RULES = [
    "\\.css(\\?|$)",
    "\\.js(\\?|$)",
    "\\.json(\\?|$)",
    "\\.jpg(\\?|$)",
    "\\.jpeg(\\?|$)",
    "\\.png(\\?|$)",
    "\\.gif(\\?|$)",
    "\\.webp(\\?|$)",
    "\\.svg(\\?|$)",
    "\\.ico(\\?|$)",
    "\\.woff2?(\\?|$)",
    "\\.ttf(\\?|$)",
    "\\.map(\\?|$)",
    "googleads",
    "doubleclick",
    "hm.baidu",
    "cnzz",
    "gstatic",
    "favicon",
    "captcha",
    "analytics",
    "tracker",
    "subtitle",
    "\\.srt(\\?|$)",
    "\\.ass(\\?|$)",
    "\\.vtt(\\?|$)",
  ];
  const SITE_SNIFFER_PROFILES = [
    {
      test: /(vip|jx|parse|parser|analysis|player)/i,
      matchRules: ["getm3u8", "url=", "playurl", "vurl=", "vod=", "player_aaaa", "application/vnd.apple.mpegurl"],
      excludeRules: ["favicon", "logo", "ads?", "analytics", "tracker"],
      maxDepth: 4,
    },
    {
      test: /(bfzy|ffzy|lz|wolong|dbzy|yzzy|360zy|ukzy|heimuer)/i,
      matchRules: ["index.m3u8", "playlist", "/obj/tos", "m3u8\\?pt=", "video/tos"],
      excludeRules: ["cover", "poster", "thumb", "\\/upload\\/"],
      maxDepth: 4,
    },
    {
      test: /(ali|alipan|aliyundrive|quark|uc)/i,
      matchRules: ["download", "play", "preview", "x-oss-process", "x-pan-token"],
      excludeRules: ["iconfont", "avatar", "qrcode"],
      maxDepth: 2,
    },
    {
      test: /(555k7|555dy|bestpipe|dongkadi|kkys|yxxq|sszzyy|gqck|madou8|bttwo|4kvm|chigua|band\.)/i,
      matchRules: [
        "player\\.html\\?v=",
        "iframe",
        "/api\\.php",
        "/player/",
        "/static/player/",
        "__PLAYER__",
        "player_data",
        "player_aaaa",
        "now=play",
        "url=",
        "vkey=",
        "vid=",
      ],
      excludeRules: [
        "share",
        "comment",
        "forum",
        "rank",
        "topic",
        "avatar",
        "banner",
        "notice",
      ],
      maxDepth: 4,
    },
    {
      test: /(madou|mdsp|porn|91|xvideos|phncdn|hanime|missav)/i,
      matchRules: ["master.m3u8", "index.m3u8", ".mp4", "playlist", "stream"],
      excludeRules: ["preview", "sample", "sprite", "storyboard", "poster", "thumb"],
      maxDepth: 2,
    },
  ];

  const state = {
    page: "home",
    sources: [],
    currentSource: null,
    categories: [],
    homeItems: [],
    exploreItems: [],
    searchItems: [],
    detail: null,
    currentMode: "空闲",
    player: null,
    currentPlayback: null,
    featuredItem: null,
    playerUi: {
      orientation: "portrait",
      brightness: 0.65,
      volume: 1,
      gestureCleanup: null,
      gestureHudTimer: null,
    },
  };

  const dom = {
    pages: Array.from(document.querySelectorAll(".page")),
    topTabs: Array.from(document.querySelectorAll(".top-tab")),
    bottomItems: Array.from(document.querySelectorAll(".bottom-item")),
    statusText: document.getElementById("statusText"),
    activeSourceInline: document.getElementById("activeSourceInline"),
    refreshButton: document.getElementById("refreshButton"),
    manageButton: document.getElementById("manageButton"),
    openManagerInlineButton: document.getElementById("openManagerInlineButton"),
    featuredBackdrop: document.getElementById("featuredBackdrop"),
    featuredTitle: document.getElementById("featuredTitle"),
    featuredMeta: document.getElementById("featuredMeta"),
    featuredActionButton: document.getElementById("featuredActionButton"),
    featuredRefreshButton: document.getElementById("featuredRefreshButton"),
    activeSourceStat: document.getElementById("activeSourceStat"),
    activeModeStat: document.getElementById("activeModeStat"),
    resultCountStat: document.getElementById("resultCountStat"),
    homeSummaryText: document.getElementById("homeSummaryText"),
    homeContinueRail: document.getElementById("homeContinueRail"),
    homeHotRail: document.getElementById("homeHotRail"),
    homeCategoryRail: document.getElementById("homeCategoryRail"),
    homeGrid: document.getElementById("homeGrid"),
    categoryList: document.getElementById("categoryList"),
    exploreSummaryText: document.getElementById("exploreSummaryText"),
    exploreTitle: document.getElementById("exploreTitle"),
    exploreGrid: document.getElementById("exploreGrid"),
    searchInput: document.getElementById("searchInput"),
    searchButton: document.getElementById("searchButton"),
    searchTitle: document.getElementById("searchTitle"),
    searchSummaryText: document.getElementById("searchSummaryText"),
    searchGrid: document.getElementById("searchGrid"),
    sourceSelect: document.getElementById("sourceSelect"),
    sourcePanelTitle: document.getElementById("sourcePanelTitle"),
    sourceKindBadge: document.getElementById("sourceKindBadge"),
    sourceHostText: document.getElementById("sourceHostText"),
    sourceTimeoutText: document.getElementById("sourceTimeoutText"),
    sourceCapabilityText: document.getElementById("sourceCapabilityText"),
    sourceBadgeList: document.getElementById("sourceBadgeList"),
    profileSummaryText: document.getElementById("profileSummaryText"),
    sourceCountStat: document.getElementById("sourceCountStat"),
    engineStat: document.getElementById("engineStat"),
    lastDetailStat: document.getElementById("lastDetailStat"),
    nowPlayingStat: document.getElementById("nowPlayingStat"),
    profileOpenDetailButton: document.getElementById("profileOpenDetailButton"),
    profileOpenPlayerButton: document.getElementById("profileOpenPlayerButton"),
    profileOpenExternalButton: document.getElementById("profileOpenExternalButton"),
    detailDrawer: document.getElementById("detailDrawer"),
    detailTitle: document.getElementById("detailTitle"),
    detailImage: document.getElementById("detailImage"),
    detailMeta: document.getElementById("detailMeta"),
    detailContent: document.getElementById("detailContent"),
    playGroups: document.getElementById("playGroups"),
    closeDetailButton: document.getElementById("closeDetailButton"),
    detailOpenExternalButton: document.getElementById("detailOpenExternalButton"),
    playerModal: document.getElementById("playerModal"),
    playerBackdrop: document.getElementById("playerBackdrop"),
    playerSourceChip: document.getElementById("playerSourceChip"),
    playerModeChip: document.getElementById("playerModeChip"),
    playerTitleText: document.getElementById("playerTitleText"),
    playerMetaText: document.getElementById("playerMetaText"),
    playerSummaryText: document.getElementById("playerSummaryText"),
    playerUrlText: document.getElementById("playerUrlText"),
    playerEpisodeRail: document.getElementById("playerEpisodeRail"),
    playerRecommendRail: document.getElementById("playerRecommendRail"),
    playerQueue: document.getElementById("playerQueue"),
    artplayerMount: document.getElementById("artplayerMount"),
    playerGestureHud: document.getElementById("playerGestureHud"),
    playerRotateButton: document.getElementById("playerRotateButton"),
    playerFullscreenButton: document.getElementById("playerFullscreenButton"),
    playerExternalButton: document.getElementById("playerExternalButton"),
    playerNativeButton: document.getElementById("playerNativeButton"),
    closePlayerButton: document.getElementById("closePlayerButton"),
    managerModal: document.getElementById("managerModal"),
    closeManagerButton: document.getElementById("closeManagerButton"),
    sourceTextarea: document.getElementById("sourceTextarea"),
    pasteButton: document.getElementById("pasteButton"),
    saveSourceButton: document.getElementById("saveSourceButton"),
    clearSourceButton: document.getElementById("clearSourceButton"),
  };

  bindEvents();
  bootstrap().catch((error) => setStatus("启动失败", describeError(error)));

  async function bootstrap(preferredSourceId) {
    const builtinSources = ensureArray(android ? safeJsonParse(android.getBuiltinSources(), []) : []);
    const customSources = ensureArray(loadCustomSources());
    state.sources = builtinSources.concat(customSources).map((item) => {
      return parseSourceRecord(item.name, item.content, Boolean(item.custom));
    }).filter(Boolean);

    renderSourceSelect();
    renderSearch("", []);
    renderExplore([], "最新推荐", "选择分类后会在这里加载内容。");
    renderChrome();

    if (!state.sources.length) {
      renderCardGrid(dom.homeGrid, [], "没有加载到内置片源，请检查 assets/sources 或手动添加。");
      return;
    }

    const savedId = localStorage.getItem("xm_current_source");
    const targetId = preferredSourceId || savedId || state.sources[0].id;
    await switchSource(targetId);
  }

  function bindEvents() {
    dom.topTabs.forEach((button) => {
      button.addEventListener("click", () => switchPage(button.dataset.page));
    });

    dom.bottomItems.forEach((button) => {
      button.addEventListener("click", () => switchPage(button.dataset.page));
    });

    dom.refreshButton.addEventListener("click", async () => {
      if (state.currentSource) {
        await switchSource(state.currentSource.id);
      }
    });

    dom.manageButton.addEventListener("click", openManager);
    dom.openManagerInlineButton.addEventListener("click", openManager);
    dom.closeManagerButton.addEventListener("click", closeManager);
    dom.managerModal.querySelector(".overlay-backdrop").addEventListener("click", closeManager);

    dom.sourceSelect.addEventListener("change", async (event) => {
      await switchSource(event.target.value);
    });

    dom.featuredActionButton.addEventListener("click", async () => {
      if (state.featuredItem) {
        await openDetail(state.featuredItem);
      }
    });

    dom.featuredRefreshButton.addEventListener("click", async () => {
      if (state.currentSource) {
        await loadHome(state.currentSource);
      }
    });

    dom.searchButton.addEventListener("click", async () => {
      const keyword = dom.searchInput.value.trim();
      if (!keyword || !state.currentSource) {
        toast("请先输入搜索关键词");
        return;
      }
      switchPage("search");
      setStatus("已发起搜索", "正在搜索：" + keyword);
      const items = await loadSearch(state.currentSource, keyword, 1);
      state.searchItems = items;
      state.currentMode = "搜索";
      renderSearch(keyword, items);
      renderChrome();
    });

    dom.profileOpenDetailButton.addEventListener("click", () => {
      if (state.detail) {
        renderDetail(state.detail);
        dom.detailDrawer.classList.remove("hidden");
      } else {
        toast("还没有解析过详情页");
      }
    });

    dom.profileOpenPlayerButton.addEventListener("click", () => {
      if (state.currentPlayback) {
        dom.playerModal.classList.remove("hidden");
      } else {
        toast("当前没有播放中的会话");
      }
    });

    dom.profileOpenExternalButton.addEventListener("click", () => {
      const url = currentExternalUrl();
      if (!url) {
        toast("当前没有可外部打开的链接");
        return;
      }
      openExternal(url);
    });

    dom.closeDetailButton.addEventListener("click", closeDetail);
    dom.detailDrawer.querySelector(".overlay-backdrop").addEventListener("click", closeDetail);
    dom.detailOpenExternalButton.addEventListener("click", () => {
      if (state.detail && state.detail.vod_id) {
        openExternal(state.detail.vod_id);
      }
    });

    dom.closePlayerButton.addEventListener("click", closePlayer);
    dom.playerModal.querySelector(".overlay-backdrop").addEventListener("click", closePlayer);
    dom.playerExternalButton.addEventListener("click", () => {
      if (state.currentPlayback) {
        openExternal(state.currentPlayback.url);
      }
    });
    dom.playerFullscreenButton.addEventListener("click", () => {
      if (!state.player) {
        return;
      }
      try {
        state.player.fullscreenWeb = !state.player.fullscreenWeb;
      } catch (error) {
        console.warn(error);
      }
    });
    dom.playerRotateButton.addEventListener("click", () => {
      togglePlayerOrientation();
    });
    dom.playerNativeButton.addEventListener("click", () => {
      if (!state.currentPlayback || !android) {
        return;
      }
      android.openPlayer(JSON.stringify({
        title: state.currentPlayback.title,
        url: state.currentPlayback.url,
        headers: state.currentPlayback.headers || {},
      }));
    });

    dom.pasteButton.addEventListener("click", () => {
      if (!android) {
        toast("当前环境不支持剪贴板桥接");
        return;
      }
      dom.sourceTextarea.value = android.getClipboardText() || "";
    });

    dom.saveSourceButton.addEventListener("click", async () => {
      const content = dom.sourceTextarea.value.trim();
      if (!content) {
        toast("请先粘贴片源内容");
        return;
      }

      const parsed = parseSourceRecord("custom-" + Date.now() + ".js", content, true);
      if (!parsed) {
        toast("片源内容不是有效的 rule");
        return;
      }

      const customSources = loadCustomSources();
      customSources.unshift({ name: parsed.name, content: content, custom: true });
      localStorage.setItem("xm_custom_sources", JSON.stringify(customSources));
      closeManager();
      dom.sourceTextarea.value = "";
      await bootstrap(parsed.id);
      switchPage("sources");
      toast("自定义片源已保存");
    });

    dom.clearSourceButton.addEventListener("click", async () => {
      localStorage.removeItem("xm_custom_sources");
      closeManager();
      dom.sourceTextarea.value = "";
      await bootstrap();
      toast("自定义片源已清空");
    });
  }

  function switchPage(page) {
    state.page = page;
    dom.pages.forEach((section) => {
      section.classList.toggle("active", section.dataset.page === page);
    });
    dom.topTabs.forEach((button) => {
      button.classList.toggle("active", button.dataset.page === page);
    });
    dom.bottomItems.forEach((button) => {
      button.classList.toggle("active", button.dataset.page === page);
    });
  }

  async function switchSource(sourceId) {
    const source = state.sources.find((item) => item.id === sourceId) || state.sources[0];
    if (!source) {
      return;
    }

    state.currentSource = clone(source);
    state.searchItems = [];
    state.detail = null;
    state.featuredItem = null;
    closePlayer();
    switchPage("home");
    localStorage.setItem("xm_current_source", state.currentSource.id);
    setStatus("片源已切换", "正在准备运行环境");
    state.currentMode = "切换中";
    renderChrome();

    try {
      await runPreprocessIfNeeded(state.currentSource);
      state.categories = await buildCategories(state.currentSource);
      renderCategories();
      renderSourceProfile();
      await loadHome(state.currentSource);
    } catch (error) {
      setStatus("片源加载失败", describeError(error));
      renderSourceProfile();
      renderCardGrid(dom.homeGrid, [], describeError(error));
      renderCardGrid(dom.exploreGrid, [], describeError(error));
    }
  }

  async function loadHome(source) {
    const items = await loadRecommend(source);
    state.homeItems = items;
    state.exploreItems = items;
    state.featuredItem = items[0] || null;
    state.currentMode = "首页推荐";
    renderHome();
    renderHomeContinueRail(items);
    renderHomeHotRail(items);
    renderHomeCategoryRail();
    renderExplore(state.exploreItems, "最新推荐", "已加载 " + items.length + " 条推荐内容。");
    renderChrome();
    setStatus("推荐加载完成", "已加载 " + items.length + " 条推荐内容。");
  }

  async function loadCategory(source, category, page) {
    const items = await loadCategoryItems(source, category, page);
    state.exploreItems = items;
    state.currentMode = category.name;
    renderExplore(items, category.name, "已加载 " + items.length + " 条内容。");
    renderChrome();
    setStatus("分类加载完成", category.name);
    switchPage("explore");
  }

  async function openDetail(item) {
    if (!state.currentSource) {
      return;
    }

    setStatus("正在解析详情", item.vod_name || item.title || "未命名");
    try {
      const detail = await loadDetail(state.currentSource, item);
      state.detail = detail;
      renderDetail(detail);
      renderChrome();
      dom.detailDrawer.classList.remove("hidden");
      setStatus("详情解析完成", "已拿到播放线路。");
    } catch (error) {
      setStatus("详情解析失败", describeError(error));
      toast("详情解析失败");
    }
  }

  function closeDetail() {
    dom.detailDrawer.classList.add("hidden");
  }

  async function playEpisode(groupName, episode) {
    if (!state.currentSource) {
      return;
    }

    setStatus("正在解析播放地址", episode.name);
    try {
      const resolved = await resolvePlayUrl(state.currentSource, episode.url);
      const playback = {
        title: episode.name,
        url: typeof resolved === "string" ? resolved : resolved.url,
        headers: typeof resolved === "object" && resolved.header ? resolved.header : {},
        groupName: groupName,
        detail: state.detail,
      };

      if (!playback.url) {
        throw new Error("当前片源没有返回可播放地址");
      }

      openPlayer(playback);
      setStatus("开始播放", episode.name);
    } catch (error) {
      setStatus("播放失败", describeError(error));
      toast("播放失败");
    }
  }

  function openPlayer(playback) {
    state.currentPlayback = playback;
    closeDetail();
    renderPlayerExperience(playback);
    dom.playerTitleText.textContent = playback.title || "未命名剧集";
    dom.playerMetaText.textContent =
      (playback.detail && playback.detail.vod_name ? playback.detail.vod_name : state.currentSource.title) +
      " · " + (playback.groupName || "默认线路");
    dom.playerUrlText.textContent = shorten(playback.url, 180);
    renderPlayerQueue();
    renderHomeContinueRail(state.homeItems);
    renderChrome();
    mountArtPlayer(playback);
    dom.playerModal.classList.remove("hidden");
  }

  function closePlayer() {
    dom.playerModal.classList.add("hidden");
    cleanupPlayerGestures();
    hidePlayerGestureHud();
    if (state.player) {
      try {
        state.player.destroy(false);
      } catch (error) {
        console.warn(error);
      }
      state.player = null;
    }
  }

  function openManager() {
    dom.managerModal.classList.remove("hidden");
  }

  function closeManager() {
    dom.managerModal.classList.add("hidden");
  }

  function mountArtPlayer(playback) {
    cleanupPlayerGestures();
    if (state.player) {
      try {
        state.player.destroy(false);
      } catch (error) {
        console.warn(error);
      }
      state.player = null;
    }

    const headers = sanitizeHeaders(playback.headers || {});
    const type = detectMediaType(playback.url);
    syncPlayerSystemState();

    state.player = new Artplayer({
      container: dom.artplayerMount,
      url: playback.url,
      title: playback.title || "",
      poster: playback.detail && playback.detail.vod_pic ? playback.detail.vod_pic : "",
      theme: "#6be9ff",
      lang: "zh-cn",
      autoplay: true,
      autoSize: true,
      playsInline: true,
      fullscreen: false,
      fullscreenWeb: true,
      playbackRate: true,
      aspectRatio: true,
      setting: true,
      pip: true,
      mutex: true,
      backdrop: true,
      hotkey: true,
      type: type === "m3u8" ? "m3u8" : undefined,
      moreVideoAttr: {
        preload: "auto",
        playsInline: true,
        "webkit-playsinline": true,
        "x5-playsinline": true,
      },
      customType: {
        m3u8: function (video, url, art) {
          if (window.Hls && window.Hls.isSupported()) {
            if (art.hls) {
              art.hls.destroy();
            }
            const hls = new window.Hls({
              xhrSetup: function (xhr) {
                applyHeaders(xhr, headers);
              },
              fetchSetup: function (context, initParams) {
                return new Request(context.url, {
                  ...initParams,
                  headers: {
                    ...(initParams && initParams.headers ? initParams.headers : {}),
                    ...headers,
                  },
                });
              },
            });
            hls.loadSource(url);
            hls.attachMedia(video);
            art.hls = hls;
            art.on("destroy", function () {
              hls.destroy();
            });
          } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
            video.src = url;
          } else {
            art.notice.show = "当前 WebView 不支持 HLS 播放";
          }
        },
      },
    });

    state.player.on("error", function () {
      setStatus("ArtPlayer 播放异常", "可以尝试原生兜底或外部播放器。");
    });
    state.player.on("fullscreenWeb", function (value) {
      dom.playerFullscreenButton.textContent = value ? "退出全屏" : "网页全屏";
    });
    state.player.on("ready", function () {
      applyPlayerVolume(state.playerUi.volume);
      applyPlayerBrightness(state.playerUi.brightness);
      bindPlayerGestures();
    });
    dom.playerFullscreenButton.textContent = "网页全屏";
    updatePlayerRotateButton();
  }

  function syncPlayerSystemState() {
    if (!android || typeof android.getPlayerState !== "function") {
      return state.playerUi;
    }
    const info = safeJsonParse(android.getPlayerState(), {});
    if (typeof info.brightness === "number" && !Number.isNaN(info.brightness)) {
      state.playerUi.brightness = clampValue(info.brightness, 0.08, 1);
    }
    if (typeof info.volume === "number" && !Number.isNaN(info.volume)) {
      state.playerUi.volume = clampValue(info.volume, 0, 1);
    }
    if (info.orientation === "landscape" || info.orientation === "portrait") {
      state.playerUi.orientation = info.orientation;
    }
    return state.playerUi;
  }

  function sanitizeHeaders(headers) {
    const normalized = {};
    Object.keys(headers || {}).forEach((key) => {
      const value = headers[key];
      if (value === undefined || value === null) {
        return;
      }
      normalized[String(key)] = String(value);
    });
    return normalized;
  }

  function togglePlayerOrientation() {
    if (android && typeof android.togglePlayerOrientation === "function") {
      const next = String(android.togglePlayerOrientation() || "");
      if (next === "landscape" || next === "portrait") {
        state.playerUi.orientation = next;
      } else {
        state.playerUi.orientation = state.playerUi.orientation === "landscape" ? "portrait" : "landscape";
      }
    } else {
      state.playerUi.orientation = state.playerUi.orientation === "landscape" ? "portrait" : "landscape";
      if (state.player) {
        try {
          state.player.fullscreenWeb = state.playerUi.orientation === "landscape";
        } catch (error) {
          console.warn(error);
        }
      }
    }
    updatePlayerRotateButton();
  }

  function updatePlayerRotateButton() {
    if (!dom.playerRotateButton) {
      return;
    }
    dom.playerRotateButton.textContent = state.playerUi.orientation === "landscape" ? "切回竖屏" : "切到横屏";
  }

  function applyPlayerVolume(value) {
    const next = clampValue(value, 0, 1);
    state.playerUi.volume = next;
    if (android && typeof android.setPlayerVolume === "function") {
      const actual = Number(android.setPlayerVolume(next));
      if (!Number.isNaN(actual)) {
        state.playerUi.volume = clampValue(actual, 0, 1);
      }
    }
    if (state.player && state.player.video) {
      try {
        state.player.video.volume = state.playerUi.volume;
      } catch (error) {
        console.warn(error);
      }
    }
    return state.playerUi.volume;
  }

  function applyPlayerBrightness(value) {
    const next = clampValue(value, 0.08, 1);
    state.playerUi.brightness = next;
    if (android && typeof android.setPlayerBrightness === "function") {
      const actual = Number(android.setPlayerBrightness(next));
      if (!Number.isNaN(actual)) {
        state.playerUi.brightness = clampValue(actual, 0.08, 1);
      }
    }
    dom.artplayerMount.style.setProperty("--player-brightness", String(state.playerUi.brightness));
    return state.playerUi.brightness;
  }

  function showPlayerGestureHud(label, value) {
    if (!dom.playerGestureHud) {
      return;
    }
    dom.playerGestureHud.textContent = label + " " + value;
    dom.playerGestureHud.classList.remove("hidden");
    clearTimeout(state.playerUi.gestureHudTimer);
    state.playerUi.gestureHudTimer = setTimeout(() => {
      hidePlayerGestureHud();
    }, 900);
  }

  function hidePlayerGestureHud() {
    if (!dom.playerGestureHud) {
      return;
    }
    clearTimeout(state.playerUi.gestureHudTimer);
    dom.playerGestureHud.classList.add("hidden");
  }

  function cleanupPlayerGestures() {
    if (state.playerUi.gestureCleanup) {
      state.playerUi.gestureCleanup();
      state.playerUi.gestureCleanup = null;
    }
  }

  function bindPlayerGestures() {
    cleanupPlayerGestures();
    const surface = dom.artplayerMount;
    if (!surface) {
      return;
    }
    let session = null;

    const onStart = (event) => {
      if (!event.touches || event.touches.length !== 1) {
        return;
      }
      if (event.target && event.target.closest(".art-controls, .art-control, .art-setting-panel, .art-bottom")) {
        return;
      }
      const rect = surface.getBoundingClientRect();
      const touch = event.touches[0];
      const localX = touch.clientX - rect.left;
      session = {
        mode: localX < rect.width / 2 ? "volume" : "brightness",
        startY: touch.clientY,
        startValue: localX < rect.width / 2 ? state.playerUi.volume : state.playerUi.brightness,
        height: Math.max(rect.height, 180),
      };
    };

    const onMove = (event) => {
      if (!session || !event.touches || event.touches.length !== 1) {
        return;
      }
      const touch = event.touches[0];
      const deltaY = session.startY - touch.clientY;
      if (Math.abs(deltaY) < 10) {
        return;
      }
      event.preventDefault();
      const next = session.startValue + deltaY / session.height;
      if (session.mode === "volume") {
        const volume = applyPlayerVolume(next);
        showPlayerGestureHud("音量", Math.round(volume * 100) + "%");
      } else {
        const brightness = applyPlayerBrightness(next);
        showPlayerGestureHud("亮度", Math.round(brightness * 100) + "%");
      }
    };

    const onEnd = () => {
      session = null;
    };

    surface.addEventListener("touchstart", onStart, { passive: true });
    surface.addEventListener("touchmove", onMove, { passive: false });
    surface.addEventListener("touchend", onEnd, { passive: true });
    surface.addEventListener("touchcancel", onEnd, { passive: true });
    state.playerUi.gestureCleanup = () => {
      surface.removeEventListener("touchstart", onStart);
      surface.removeEventListener("touchmove", onMove);
      surface.removeEventListener("touchend", onEnd);
      surface.removeEventListener("touchcancel", onEnd);
    };
  }

  function renderChrome() {
    dom.activeSourceInline.textContent = state.currentSource ? state.currentSource.title : "未选择片源";
    dom.activeSourceStat.textContent = state.currentSource ? state.currentSource.title : "未选择";
    dom.activeModeStat.textContent = state.currentMode;
    dom.resultCountStat.textContent = String(currentVisibleItems().length);
    dom.sourceCountStat.textContent = String(state.sources.length);
    dom.engineStat.textContent = "ArtPlayer · HLS";
    dom.lastDetailStat.textContent = state.detail ? shorten(state.detail.vod_name || "已就绪", 22) : "暂无";
    dom.nowPlayingStat.textContent = state.currentPlayback ? shorten(state.currentPlayback.title || "播放中", 22) : "空闲";
    dom.profileSummaryText.textContent = state.currentPlayback
      ? "正在播放《" + state.currentPlayback.title + "》，来自 " + (state.currentSource ? state.currentSource.title : "当前片源")
      : "查看当前片源、最近详情和播放状态。";
  }

  function renderHome() {
    const items = state.homeItems;
    const featured = state.featuredItem;
    if (featured) {
      dom.featuredTitle.textContent = featured.vod_name || featured.title || "精选推荐";
      dom.featuredMeta.textContent = featured.vod_remarks || featured.desc || "已准备好，点击即可查看详情。";
      dom.featuredBackdrop.style.background =
        "linear-gradient(180deg, rgba(4, 10, 24, 0.15), rgba(4, 10, 24, 0.88)), url('" +
        escapeCssUrl(featured.vod_pic || featured.img || "") +
        "') center/cover no-repeat";
    } else {
      dom.featuredTitle.textContent = "等待加载影视源";
      dom.featuredMeta.textContent = "载入片源后，这里会展示首屏推荐内容。";
      dom.featuredBackdrop.style.background =
        "linear-gradient(180deg, rgba(4, 10, 24, 0.15), rgba(4, 10, 24, 0.88)), radial-gradient(circle at top left, rgba(107, 233, 255, 0.16), transparent 26%), #0d1630";
    }
    dom.homeSummaryText.textContent = "已加载 " + items.length + " 条推荐内容。";
    renderCardGrid(dom.homeGrid, items, "当前片源没有返回首页推荐内容。");
  }

  function renderHomeContinueRail(items) {
    const continueItems = [];
    if (state.currentPlayback && state.detail) {
      continueItems.push({
        vod_id: state.detail.vod_id || state.currentPlayback.url,
        vod_name: state.detail.vod_name || state.currentPlayback.title,
        vod_pic: state.detail.vod_pic || "",
        vod_remarks: "继续播放 · " + (state.currentPlayback.title || "当前剧集"),
        _resumePlayback: true,
      });
    }
    ensureArray(items).slice(0, 5).forEach((item) => {
      if (!continueItems.some((entry) => (entry.vod_id || entry.vod_name) === (item.vod_id || item.vod_name))) {
        continueItems.push(item);
      }
    });
    renderHomeRail(dom.homeContinueRail, continueItems.slice(0, 6), "继续观看会显示在这里。", "continue");
  }

  function renderHomeHotRail(items) {
    renderHomeRail(dom.homeHotRail, ensureArray(items).slice(0, 8), "片源返回推荐后，这里会展示热播速览。", "hot");
  }

  function renderHomeCategoryRail() {
    if (!state.categories.length) {
      dom.homeCategoryRail.innerHTML = '<div class="empty-card">当前片源没有返回分类信息。</div>';
      return;
    }

    dom.homeCategoryRail.innerHTML = "";
    state.categories.slice(0, 10).forEach((category) => {
      const button = document.createElement("button");
      button.className = "home-category-chip";
      button.textContent = category.name;
      button.addEventListener("click", async () => {
        await loadCategory(state.currentSource, category, 1);
        renderCategories(category.id);
      });
      dom.homeCategoryRail.appendChild(button);
    });
  }

  function renderHomeRail(target, items, emptyText, variant) {
    const list = ensureArray(items);
    if (!list.length) {
      target.innerHTML = '<div class="empty-card">' + escapeHtml(emptyText) + "</div>";
      return;
    }

    target.innerHTML = "";
    list.forEach((item) => {
      const button = document.createElement("button");
      button.className = "home-rail-card " + (variant === "continue" ? "continue" : "hot");
      button.innerHTML =
        '<img src="' + escapeAttr(item.vod_pic || item.img || "") + '" alt="">' +
        '<div class="home-rail-overlay">' +
        '<div class="home-rail-title">' + escapeHtml(item.vod_name || item.title || "未命名内容") + "</div>" +
        '<div class="home-rail-desc">' + escapeHtml(item.vod_remarks || item.desc || "点开查看详情") + "</div>" +
        "</div>";
      button.addEventListener("click", async () => {
        if (item._resumePlayback && state.currentPlayback) {
          openPlayer(state.currentPlayback);
          return;
        }
        await openDetail(item);
      });
      target.appendChild(button);
    });
  }

  function renderExplore(items, title, summary) {
    dom.exploreTitle.textContent = title;
    dom.exploreSummaryText.textContent = summary;
    renderCardGrid(dom.exploreGrid, items, "请选择一个分类来加载内容。");
  }

  function renderSearch(keyword, items) {
    dom.searchTitle.textContent = keyword ? "搜索：" + keyword : "还没有搜索";
    dom.searchSummaryText.textContent = keyword
      ? "共找到 " + items.length + " 条结果。"
      : "搜索结果会显示在这里。";
    renderCardGrid(dom.searchGrid, items, keyword ? "没有匹配到搜索结果。" : "当前没有搜索内容。");
  }

  function renderCategories(activeId) {
    if (!state.categories.length) {
      dom.categoryList.innerHTML = '<div class="empty-card">当前片源没有提供分类信息。</div>';
      return;
    }

    dom.categoryList.innerHTML = "";
    state.categories.forEach((category) => {
      const button = document.createElement("button");
      button.className = "chip";
      if (category.id === activeId) {
        button.style.borderColor = "rgba(107, 233, 255, 0.42)";
        button.style.background = "linear-gradient(135deg, rgba(107, 233, 255, 0.16), rgba(114, 255, 199, 0.08))";
      }
      button.textContent = category.name;
      button.addEventListener("click", async () => {
        await loadCategory(state.currentSource, category, 1);
        renderCategories(category.id);
      });
      dom.categoryList.appendChild(button);
    });
  }

  function renderSourceSelect() {
    dom.sourceSelect.innerHTML = state.sources.map((source) => {
      return '<option value="' + escapeAttr(source.id) + '">' + escapeHtml(source.title) + "</option>";
    }).join("");
  }

  function renderSourceProfile() {
    const source = state.currentSource;
    if (!source) {
      return;
    }
    dom.sourceSelect.value = source.id;
    dom.sourcePanelTitle.textContent = source.title || "未命名片源";
    dom.sourceKindBadge.textContent = source.custom ? "自定义" : "内置";
    dom.sourceHostText.textContent = source.host || "-";
    dom.sourceTimeoutText.textContent = source.timeout ? source.timeout + " ms" : "默认";
    dom.sourceCapabilityText.textContent = getSourceCapabilities(source).join(" · ") || "基础解析";
    dom.sourceBadgeList.innerHTML = getSourceCapabilities(source).map((item) => {
      return '<span class="badge-item">' + escapeHtml(item) + "</span>";
    }).join("");
  }

  function renderDetail(detail) {
    dom.detailTitle.textContent = detail.vod_name || "未命名";
    dom.detailImage.src = detail.vod_pic || "";
    dom.detailMeta.textContent = detail.vod_remarks || "暂无信息";
    dom.detailContent.textContent = detail.vod_content || "暂无简介";
    dom.playGroups.innerHTML = "";

    if (!detail.playGroups || !detail.playGroups.length) {
      dom.playGroups.innerHTML = '<div class="empty-card">这个详情页没有解析出播放线路。</div>';
      return;
    }

    detail.playGroups.forEach((group) => {
      const wrapper = document.createElement("section");
      wrapper.className = "play-group";
      const title = document.createElement("h4");
      title.textContent = group.name;
      const episodes = document.createElement("div");
      episodes.className = "episodes";

      group.items.forEach((episode) => {
        const button = document.createElement("button");
        button.className = "episode-button";
        button.textContent = episode.name;
        button.addEventListener("click", async () => {
          await playEpisode(group.name, episode);
        });
        episodes.appendChild(button);
      });

      wrapper.appendChild(title);
      wrapper.appendChild(episodes);
      dom.playGroups.appendChild(wrapper);
    });
  }

  function renderPlayerQueue() {
    dom.playerQueue.innerHTML = "";
    if (!state.detail || !state.detail.playGroups || !state.detail.playGroups.length) {
      dom.playerQueue.innerHTML = '<div class="empty-card">解析详情后，这里会显示选集队列。</div>';
      return;
    }

    state.detail.playGroups.forEach((group) => {
      const groupWrap = document.createElement("section");
      groupWrap.className = "queue-group";

      const title = document.createElement("div");
      title.className = "queue-group-title";
      title.textContent = group.name;
      groupWrap.appendChild(title);

      group.items.forEach((episode) => {
        const button = document.createElement("button");
        button.className = "queue-item";
        if (state.currentPlayback && (state.currentPlayback.title === episode.name || state.currentPlayback.url === episode.url)) {
          button.classList.add("active");
        }
        button.textContent = episode.name;
        button.addEventListener("click", async () => {
          await playEpisode(group.name, episode);
        });
        groupWrap.appendChild(button);
      });

      dom.playerQueue.appendChild(groupWrap);
    });
  }

  function renderPlayerExperience(playback) {
    const detail = playback && playback.detail ? playback.detail : state.detail;
    const seriesTitle = detail && detail.vod_name ? detail.vod_name : (state.currentSource ? state.currentSource.title : "当前片源");
    const lineName = playback && playback.groupName ? playback.groupName : "默认线路";
    const summary = detail && (detail.vod_content || detail.vod_remarks)
      ? (detail.vod_content || detail.vod_remarks)
      : "当前视频已进入播放页，可以切换剧集、线路，或从相关推荐继续探索。";

    dom.playerTitleText.textContent = playback.title || "未命名剧集";
    dom.playerMetaText.textContent = seriesTitle + " · " + lineName;
    dom.playerSummaryText.textContent = summary;
    dom.playerUrlText.textContent = shorten(playback.url, 180);
    dom.playerSourceChip.textContent = state.currentSource ? state.currentSource.title : "当前片源";
    dom.playerModeChip.textContent = lineName;
    dom.playerBackdrop.style.background =
      "linear-gradient(180deg, rgba(4, 8, 18, 0.2), rgba(4, 8, 18, 0.92)), url('" +
      escapeCssUrl(detail && detail.vod_pic ? detail.vod_pic : "") +
      "') center/cover no-repeat";

    renderPlayerEpisodeRail(detail, playback);
    renderPlayerRecommendRail(detail);
  }

  function renderPlayerEpisodeRail(detail, playback) {
    dom.playerEpisodeRail.innerHTML = "";
    if (!detail || !detail.playGroups || !detail.playGroups.length) {
      dom.playerEpisodeRail.innerHTML = '<div class="empty-card">解析到详情后，这里会展示可横向切换的剧集。</div>';
      return;
    }

    detail.playGroups.forEach((group) => {
      const shelf = document.createElement("section");
      shelf.className = "player-rail-group";

      const label = document.createElement("div");
      label.className = "player-rail-title";
      label.textContent = group.name;
      shelf.appendChild(label);

      const rail = document.createElement("div");
      rail.className = "episode-rail";

      group.items.forEach((episode) => {
        const button = document.createElement("button");
        button.className = "episode-pill";
        if (playback && (playback.title === episode.name || playback.url === episode.url)) {
          button.classList.add("active");
        }
        button.textContent = episode.name;
        button.addEventListener("click", async () => {
          await playEpisode(group.name, episode);
        });
        rail.appendChild(button);
      });

      shelf.appendChild(rail);
      dom.playerEpisodeRail.appendChild(shelf);
    });
  }

  function renderPlayerRecommendRail(detail) {
    const detailId = detail ? (detail.vod_id || detail.vod_name) : "";
    const pool = []
      .concat(ensureArray(state.homeItems))
      .concat(ensureArray(state.exploreItems))
      .concat(ensureArray(state.searchItems))
      .filter((item) => item && (item.vod_id || item.vod_name));
    const unique = [];
    const seen = {};
    pool.forEach((item) => {
      const key = item.vod_id || item.vod_name;
      if (!key || key === detailId || seen[key]) {
        return;
      }
      seen[key] = true;
      unique.push(item);
    });

    if (!unique.length) {
      dom.playerRecommendRail.innerHTML = '<div class="empty-card">推荐内容会跟随当前片源和浏览结果自动更新。</div>';
      return;
    }

    dom.playerRecommendRail.innerHTML = "";
    unique.slice(0, 8).forEach((item) => {
      const button = document.createElement("button");
      button.className = "player-recommend-card";
      button.innerHTML =
        '<img src="' + escapeAttr(item.vod_pic || item.img || "") + '" alt="">' +
        '<div class="player-recommend-copy">' +
        '<strong>' + escapeHtml(item.vod_name || item.title || "未命名内容") + '</strong>' +
        '<span>' + escapeHtml(item.vod_remarks || item.desc || "点击查看详情") + "</span>" +
        "</div>";
      button.addEventListener("click", async () => {
        await openDetail(item);
      });
      dom.playerRecommendRail.appendChild(button);
    });
  }

  function renderCardGrid(target, items, emptyText) {
    const list = ensureArray(items);
    if (!list.length) {
      target.innerHTML = '<div class="empty-card">' + escapeHtml(emptyText) + "</div>";
      return;
    }

    target.innerHTML = "";
    list.forEach((item) => {
      const button = document.createElement("button");
      button.className = "poster-card";
      button.innerHTML =
        '<img src="' + escapeAttr(item.vod_pic || item.img || "") + '" alt="">' +
        '<div class="poster-card-body">' +
        '<div class="poster-card-title">' + escapeHtml(item.vod_name || item.title || "未命名") + "</div>" +
        '<div class="poster-card-desc">' + escapeHtml(item.vod_remarks || item.desc || "暂无简介") + "</div>" +
        "</div>";
      button.addEventListener("click", async () => {
        await openDetail(item);
      });
      target.appendChild(button);
    });
  }

  function loadCustomSources() {
    return ensureArray(safeJsonParse(localStorage.getItem("xm_custom_sources"), []));
  }

  function parseSourceRecord(name, content, custom) {
    try {
      const rule = evaluateRule(content);
      if (!rule || typeof rule !== "object") {
        return null;
      }
      const source = clone(rule);
      source.id = (custom ? "custom:" : "builtin:") + name;
      source.name = name;
      source.custom = custom;
      source.content = content;
      source.title = source.title || name;
      source.headers = normalizeRuleHeaders(source.headers || {});
      if (source.host && !source.headers.Referer) {
        source.headers.Referer = normalizeBaseUrl(source.host);
      }
      return source;
    } catch (error) {
      console.warn("parseSourceRecord failed", name, error);
      return null;
    }
  }

  function evaluateRule(content) {
    const guessedHost = guessHost(content);
    const $js = {
      toString: function (fn) {
        const source = String(fn).trim();
        const body = source.slice(source.indexOf("{") + 1, source.lastIndexOf("}"));
        return "js:" + body;
      },
    };

    const factory = new Function(
      "HOST",
      "MOBILE_UA",
      "PC_UA",
      "$js",
      "CryptoJS",
      content + "\nreturn typeof rule !== 'undefined' ? rule : null;"
    );
    return factory(guessedHost, MOBILE_UA, PC_UA, $js, window.CryptoJS);
  }

  function guessHost(content) {
    const match = content.match(/host\s*:\s*['"]([^'"]+)['"]/);
    return match ? match[1] : "";
  }

  async function runPreprocessIfNeeded(rule) {
    const preprocess = getRuleValue(rule, RULE_KEYS.preprocess);
    if (typeof preprocess === "string" && preprocess.startsWith("js:")) {
      const context = createExecContext(rule, rule.host || "");
      await executeJsRule(preprocess, context);
      Object.assign(rule, context.rule);
    }
  }

  async function buildCategories(rule) {
    if (rule.class_name && rule.class_url) {
      const names = String(rule.class_name).split("&");
      const urls = String(rule.class_url).split("&");
      return names.map((name, index) => ({
        id: "class-" + index,
        name: name,
        url: urls[index] || "",
      }));
    }

    if (rule.class_parse) {
      const html = requestText(rule.host, { headers: rule.headers, timeout: rule.timeout });
      return parseCategoriesByClassParse(rule.class_parse, html, rule.host);
    }

    return [];
  }

  async function loadRecommend(rule) {
    const recommend = getRuleValue(rule, RULE_KEYS.recommend);
    if (!recommend) {
      return [];
    }
    if (typeof recommend === "string" && recommend.startsWith("js:")) {
      const context = createExecContext(rule, rule.host || "");
      await executeJsRule(recommend, context);
      return normalizeItems(context.result || []);
    }
    const html = requestText(rule.host, { headers: rule.headers, timeout: rule.timeout });
    return parseListBySelector(recommend, html, rule.host);
  }

  async function loadCategoryItems(rule, category, page) {
    const firstLevel = getRuleValue(rule, RULE_KEYS.first);
    const categoryUrl = buildCategoryUrl(rule, category.url, page);
    if (typeof firstLevel === "string" && firstLevel.startsWith("js:")) {
      const context = createExecContext(rule, categoryUrl);
      context.MY_PAGE = page;
      await executeJsRule(firstLevel, context);
      return normalizeItems(context.result || []);
    }
    const html = requestText(categoryUrl, { headers: rule.headers, timeout: rule.timeout });
    return parseListBySelector(firstLevel, html, rule.host);
  }

  async function loadSearch(rule, keyword, page) {
    const searchRule = getRuleValue(rule, RULE_KEYS.search);
    const searchUrl = buildSearchUrl(rule, keyword, page);
    if (typeof searchRule === "string" && searchRule.startsWith("js:")) {
      const context = createExecContext(rule, searchUrl);
      context.MY_PAGE = page;
      await executeJsRule(searchRule, context);
      return normalizeItems(context.result || []);
    }
    const html = requestText(searchUrl, { headers: rule.headers, timeout: rule.timeout });
    return parseListBySelector(searchRule, html, rule.host);
  }

  async function loadDetail(rule, item) {
    const detailRule = getRuleValue(rule, RULE_KEYS.second);
    const detailUrl = absoluteUrl(item.vod_id || item.url || "", rule.host);

    if (typeof detailRule === "string" && detailRule.startsWith("js:")) {
      const context = createExecContext(rule, detailUrl);
      await executeJsRule(detailRule, context);
      const payload = context.VOD || (context.result && context.result.VOD) || context.result;
      return normalizeDetail(payload);
    }

    if (detailRule && typeof detailRule === "object") {
      const html = requestText(detailUrl, { headers: rule.headers, timeout: rule.timeout });
      return normalizeDetail(parseDetailObject(detailRule, html, rule.host, detailUrl));
    }

    return normalizeDetail({
      vod_id: detailUrl,
      vod_name: item.vod_name || item.title || "未命名",
      vod_pic: item.vod_pic || item.img || "",
      playGroups: [{ name: "默认线路", items: [{ name: "播放", url: detailUrl }] }],
    });
  }

  async function resolvePlayUrl(rule, inputUrl) {
    const lazy = rule.lazy;
    const initialUrl = absoluteUrl(inputUrl, rule.host);
    let candidate = {
      url: initialUrl,
      header: getPlayHeaders(rule),
      parse: 0,
      jx: 0,
    };

    if (typeof lazy === "string" && lazy.startsWith("js:")) {
      const context = createExecContext(rule, initialUrl);
      await executeJsRule(lazy, context);
      const lazyOutput = context.input !== initialUrl ? context.input : (context.result || context.input);
      if (typeof lazyOutput === "string") {
        candidate = {
          url: lazyOutput,
          header: getPlayHeaders(rule),
          parse: 0,
          jx: 0,
        };
      } else if (lazyOutput && typeof lazyOutput === "object") {
        candidate = {
          url: lazyOutput.url || initialUrl,
          header: mergeHeaders(getPlayHeaders(rule), lazyOutput.header || {}),
          parse: Number(lazyOutput.parse || 0),
          jx: Number(lazyOutput.jx || 0),
        };
      }
    }

    candidate.url = absoluteUrl(candidate.url, rule.host);
    if (shouldSniffPlaybackCandidate(candidate)) {
      const sniffed = sniffPlaybackCandidate(candidate);
      if (sniffed && sniffed.url) {
        return {
          url: sniffed.url,
          header: sanitizeHeaders(candidate.header || {}),
        };
      }
      const extracted = extractMediaUrlFromHtml(rule, candidate);
      if (extracted) {
        return {
          url: extracted,
          header: sanitizeHeaders(candidate.header || {}),
        };
      }
      if (!isDirectPlayableUrl(candidate.url)) {
        throw new Error("未解析到真实播放地址");
      }
    }

    return {
      url: candidate.url,
      header: sanitizeHeaders(candidate.header || {}),
    };
  }

  function extractMediaUrlFromHtml(rule, candidate) {
    try {
      const html = requestText(candidate.url, {
        headers: sanitizeHeaders(candidate.header || {}),
        timeout: Math.min(Number(rule.timeout || 20000), 30000),
      });
      const patterns = [
        /(?:url|playurl|video_url|src)\s*[:=]\s*["']([^"'<>]+?(?:m3u8|mp4|m4v|flv|mpd|webm)[^"']*)["']/ig,
        /["'](https?:\/\/[^"']+?(?:m3u8|mp4|m4v|flv|mpd|webm)[^"']*)["']/ig,
      ];
      for (let i = 0; i < patterns.length; i += 1) {
        let match;
        while ((match = patterns[i].exec(html))) {
          const normalized = normalizeEmbeddedMediaUrl(match[1], rule.host);
          if (normalized && isDirectPlayableUrl(normalized)) {
            return normalized;
          }
        }
      }
    } catch (error) {
      console.warn(error);
    }
    return "";
  }

  function normalizeEmbeddedMediaUrl(value, host) {
    let normalized = String(value || "").trim();
    if (!normalized) {
      return "";
    }
    normalized = normalized
      .replace(/\\u0026/g, "&")
      .replace(/\\\//g, "/")
      .replace(/&amp;/g, "&");
    return absoluteUrl(normalized, host);
  }

  function normalizeItems(items) {
    return ensureArray(items).map((item, index) => ({
      id: item.vod_id || item.url || "item-" + index,
      vod_id: item.vod_id || item.url || "",
      vod_name: item.vod_name || item.title || "未命名 " + (index + 1),
      vod_pic: item.vod_pic || item.img || "",
      vod_remarks: item.vod_remarks || item.desc || "",
      title: item.title || item.vod_name || "",
      img: item.img || item.vod_pic || "",
      desc: item.desc || item.vod_remarks || "",
      url: item.url || item.vod_id || "",
    }));
  }

  function normalizeDetail(payload) {
    const detail = payload || {};
    let playGroups = ensureArray(detail.playGroups);
    if (!playGroups.length && detail.vod_play_url) {
      const froms = String(detail.vod_play_from || "默认线路").split("$$$");
      const groups = String(detail.vod_play_url).split("$$$");
      playGroups = groups.map((groupText, groupIndex) => ({
        name: froms[groupIndex] || "线路 " + (groupIndex + 1),
        items: String(groupText).split("#").filter(Boolean).map((entry, itemIndex) => {
          const parts = entry.split("$");
          return {
            name: parts[0] || "播放 " + (itemIndex + 1),
            url: parts.slice(1).join("$"),
          };
        }),
      }));
    }
    return {
      vod_id: detail.vod_id || "",
      vod_name: detail.vod_name || detail.title || "未命名",
      vod_pic: detail.vod_pic || detail.img || "",
      vod_remarks: detail.vod_remarks || detail.desc || "",
      vod_content: detail.vod_content || detail.content || "",
      playGroups: playGroups,
    };
  }

  function buildCategoryUrl(rule, classId, page) {
    let url = String(rule.url || "");
    url = url.replace(/fyclass/g, classId || "");
    url = url.replace(/fypage/g, String(page || 1));
    url = url.replace(/fyfilter/g, "");
    return absoluteUrl(url, rule.host);
  }

  function buildSearchUrl(rule, keyword, page) {
    let url = String(rule.searchUrl || "");
    url = url.replace(/\*\*/g, encodeURIComponent(keyword));
    url = url.replace(/fypage/g, String(page || 1));
    return absoluteUrl(url, rule.host);
  }

  function createExecContext(rule, input) {
    const fetchParams = {};
    const context = {
      rule: clone(rule),
      input: input,
      result: null,
      VOD: null,
      MY_PAGE: 1,
      MY_PAGECOUNT: 1,
      MY_TOTAL: 1,
    };

    context.requestRaw = function (url, options) {
      const opts = options || {};
      const finalUrl = absoluteUrl(url, context.rule.host || rule.host);
      const response = nativeRequest({
        url: finalUrl,
        method: opts.method || "GET",
        headers: mergeHeaders(context.rule.headers || {}, opts.headers || {}),
        timeout: opts.timeout || context.rule.timeout || 20000,
        body: opts.body || "",
      });
      if (!response.ok && !response.body) {
        throw new Error(response.error || "请求失败");
      }
      return response;
    };

    context.request = function (url, options) {
      const opts = options || {};
      const response = context.requestRaw(url, opts);
      return opts.withHeaders || opts.returnObj ? response : (response.body || "");
    };

    context.fetch = context.request;
    context.req = context.request;
    context.post = function (url, body, headers) {
      return context.request(url, {
        method: "POST",
        body: typeof body === "string" ? body : JSON.stringify(body || ""),
        headers: headers || {},
      });
    };
    context.setResult = function (value) {
      context.result = value;
    };
    context.getItem = function (key) {
      return localStorage.getItem(storageKey(context.rule, key)) || "";
    };
    context.setItem = function (key, value) {
      localStorage.setItem(storageKey(context.rule, key), value);
    };
    context.clearItem = function (key) {
      localStorage.removeItem(storageKey(context.rule, key));
    };
    context.pdfa = function (html, selector) {
      return selectEnhanced(parseHtml(html), selector).map((node) => node.outerHTML);
    };
    context.pdfh = function (html, expr) {
      return extractByRule(parseHtmlFragment(html), expr, context.rule.host);
    };
    context.pd = function (html, expr, host) {
      return absoluteUrl(extractByRule(parseHtmlFragment(html), expr, host || context.rule.host), host || context.rule.host);
    };
    context.urljoin = function (base, relative) {
      if (relative == null) {
        return absoluteUrl(base, context.rule.host || rule.host);
      }
      return absoluteUrl(relative, base || context.rule.host || rule.host);
    };
    context.buildUrl = context.urljoin;
    context.base64Encode = function (value) {
      return window.btoa(unescape(encodeURIComponent(String(value == null ? "" : value))));
    };
    context.base64Decode = function (value) {
      try {
        return decodeURIComponent(escape(window.atob(String(value || ""))));
      } catch (error) {
        return "";
      }
    };
    context.md5 = function (value) {
      return window.CryptoJS ? String(window.CryptoJS.MD5(String(value || ""))) : "";
    };
    context.sha1 = function (value) {
      return window.CryptoJS ? String(window.CryptoJS.SHA1(String(value || ""))) : "";
    };
    context.stringify = function (value) {
      return JSON.stringify(value);
    };
    context.jsonParse = function (value, fallback) {
      return safeJsonParse(value, fallback || {});
    };
    context.fetch_params = fetchParams;
    context.log = function () {
      if (android) {
        android.log(Array.from(arguments).map(String).join(" "));
      }
    };
    context.MOBILE_UA = MOBILE_UA;
    context.PC_UA = PC_UA;
    context.UA = context.rule.headers && context.rule.headers["User-Agent"] ? context.rule.headers["User-Agent"] : MOBILE_UA;
    context.HOST = context.rule.host || rule.host || "";
    context.CryptoJS = window.CryptoJS;
    return context;
  }

  async function executeJsRule(jsRule, context) {
    const code = String(jsRule).replace(/^js:/, "");
    const runner = new AsyncFunction(
      "ctx",
      `
      var rule = ctx.rule;
      var input = ctx.input;
      var VOD = ctx.VOD;
      var MY_PAGE = ctx.MY_PAGE;
      var MY_PAGECOUNT = ctx.MY_PAGECOUNT;
      var MY_TOTAL = ctx.MY_TOTAL;
      var requestRaw = ctx.requestRaw;
      var request = ctx.request;
      var fetch = ctx.fetch;
      var req = ctx.req;
      var post = ctx.post;
      var setResult = ctx.setResult;
      var getItem = ctx.getItem;
      var setItem = ctx.setItem;
      var clearItem = ctx.clearItem;
      var pdfa = ctx.pdfa;
      var pdfh = ctx.pdfh;
      var pd = ctx.pd;
      var urljoin = ctx.urljoin;
      var buildUrl = ctx.buildUrl;
      var base64Encode = ctx.base64Encode;
      var base64Decode = ctx.base64Decode;
      var md5 = ctx.md5;
      var sha1 = ctx.sha1;
      var stringify = ctx.stringify;
      var jsonParse = ctx.jsonParse;
      var fetch_params = ctx.fetch_params;
      var log = ctx.log;
      var MOBILE_UA = ctx.MOBILE_UA;
      var PC_UA = ctx.PC_UA;
      var UA = ctx.UA;
      var HOST = ctx.HOST;
      var CryptoJS = ctx.CryptoJS;
      ${code}
      ctx.rule = rule;
      ctx.input = input;
      ctx.VOD = VOD;
      ctx.MY_PAGE = MY_PAGE;
      ctx.MY_PAGECOUNT = MY_PAGECOUNT;
      ctx.MY_TOTAL = MY_TOTAL;
      return ctx;
      `
    );
    return runner(context);
  }

  function parseCategoriesByClassParse(classParse, html, host) {
    const parts = String(classParse).split(";");
    if (parts.length < 4) {
      return [];
    }
    return selectEnhanced(parseHtml(html), parts[0]).map((node, index) => {
      const name = extractByRule(node, parts[1], host);
      const href = extractByRule(node, parts[2], host);
      let url = href;
      if (parts[3]) {
        const match = href.match(new RegExp(parts[3]));
        if (match) {
          url = match[1] || match[0];
        }
      }
      return { id: "class-" + index, name: name, url: url };
    }).filter((item) => item.name && item.url);
  }

  function parseListBySelector(ruleValue, html, host) {
    if (!ruleValue) {
      return [];
    }
    const parts = String(ruleValue).split(";");
    if (parts.length < 5) {
      return [];
    }
    return selectEnhanced(parseHtml(html), parts[0]).map((node, index) => {
      const title = extractByRule(node, parts[1], host);
      const img = absoluteUrl(extractByRule(node, parts[2], host), host);
      const desc = extractByRule(node, parts[3], host);
      const url = absoluteUrl(extractByRule(node, parts[4], host), host);
      return {
        id: url || "item-" + index,
        vod_id: url,
        vod_name: title,
        vod_pic: img,
        vod_remarks: desc,
        title: title,
        img: img,
        desc: desc,
        url: url,
      };
    }).filter((item) => item.vod_name || item.url);
  }

  function parseDetailObject(config, html, host, detailUrl) {
    const root = parseHtml(html);
    const detail = {
      vod_id: detailUrl,
      vod_name: extractMulti(config.title, root, host).join(" "),
      vod_pic: absoluteUrl(extractByRule(root, config.img, host), host),
      vod_remarks: extractMulti(config.desc, root, host).join(" "),
      vod_content: extractByRule(root, config.content, host),
      playGroups: [],
    };

    if (config.tabs && config.lists) {
      selectEnhanced(root, config.tabs).forEach((tabNode, index) => {
        const tabName = extractByRule(tabNode, config.tab_text || "body&&Text", host) || "线路 " + (index + 1);
        const selector = String(config.lists).replace(/#id/g, String(index));
        const items = selectEnhanced(root, selector).map((episodeNode, episodeIndex) => ({
          name: extractByRule(episodeNode, config.list_text || "body&&Text", host) || "播放 " + (episodeIndex + 1),
          url: absoluteUrl(extractByRule(episodeNode, config.list_url || "a&&href", host), host),
        })).filter((item) => item.url);
        if (items.length) {
          detail.playGroups.push({ name: tabName, items: items });
        }
      });
    }

    if (!detail.playGroups.length && config.lists) {
      const items = selectEnhanced(root, config.lists).map((episodeNode, episodeIndex) => ({
        name: extractByRule(episodeNode, config.list_text || "body&&Text", host) || "鎾斁 " + (episodeIndex + 1),
        url: absoluteUrl(extractByRule(episodeNode, config.list_url || "a&&href", host), host),
      })).filter((item) => item.url);
      if (items.length) {
        detail.playGroups.push({
          name: config.tab_text_default || config.list_group_name || "默认线路",
          items: items,
        });
      }
    }

    return detail;
  }

  function extractMulti(expr, root, host) {
    return String(expr || "").split(";").map((part) => extractByRule(root, part, host)).filter(Boolean);
  }

  function extractByRule(root, expr, host) {
    if (!expr) {
      return "";
    }
    const parts = String(expr).split("&&");
    let attr = "Text";
    let segments = parts.slice();
    if (segments.length > 1 && isAttributeToken(segments[segments.length - 1])) {
      attr = segments.pop();
    }
    let current = [normalizeRoot(root)];
    segments.forEach((selector) => {
      current = current.flatMap((node) => selectOneStep(node, selector));
    });
    const first = current[0];
    if (!first) {
      return "";
    }
    if (attr === "Text") {
      return normalizeText(first.textContent || "");
    }
    const value = first.getAttribute ? first.getAttribute(attr) : "";
    if (attr === "href" || attr === "src" || attr.indexOf("data-") === 0) {
      return absoluteUrl(value || "", host);
    }
    return value || "";
  }

  function isAttributeToken(token) {
    return token === "Text" || /^[a-zA-Z0-9:_-]+$/.test(token);
  }

  function selectEnhanced(root, selector) {
    return String(selector || "").split("&&").reduce((nodes, step) => {
      return nodes.flatMap((node) => selectOneStep(node, step));
    }, [normalizeRoot(root)]);
  }

  function selectOneStep(root, selector) {
    const normalizedRoot = normalizeRoot(root);
    const rawSelector = String(selector || "").trim();
    if (!rawSelector) {
      return [];
    }
    const nestedEqMatch = rawSelector.match(/^(.*?):eq\((-?\d+)\)(.*)$/);
    if (nestedEqMatch && nestedEqMatch[3]) {
      const baseNodes = selectOneStep(normalizedRoot, nestedEqMatch[1] + ":eq(" + nestedEqMatch[2] + ")");
      return baseNodes.flatMap((node) => selectOneStep(node, nestedEqMatch[3].trim()));
    }
    const eqMatch = rawSelector.match(/^(.*?):eq\((-?\d+)\)$/);
    if (eqMatch) {
      const candidates = queryAllWithSelf(normalizedRoot, eqMatch[1] || "*");
      const index = Number(eqMatch[2]);
      const finalIndex = index < 0 ? candidates.length + index : index;
      return candidates[finalIndex] ? [candidates[finalIndex]] : [];
    }
    if (rawSelector === "body") {
      return [normalizedRoot.body || normalizedRoot];
    }
    return queryAllWithSelf(normalizedRoot, rawSelector);
  }

  function queryAllWithSelf(root, selector) {
    const normalizedRoot = normalizeRoot(root);
    const normalizedSelector = selector.trim().replace(/^>\s*/, ":scope > ");
    const results = [];
    const seen = new Set();

    const pushNode = (node) => {
      if (!node || seen.has(node)) {
        return;
      }
      seen.add(node);
      results.push(node);
    };

    try {
      if (normalizedRoot instanceof Element && canMatchSelector(normalizedRoot, normalizedSelector) && normalizedRoot.matches(normalizedSelector)) {
        pushNode(normalizedRoot);
      }
    } catch (error) {
      if (normalizedRoot instanceof Element && canMatchSelector(normalizedRoot, selector) && normalizedRoot.matches(selector)) {
        pushNode(normalizedRoot);
      }
    }

    try {
      Array.from(normalizedRoot.querySelectorAll(normalizedSelector)).forEach(pushNode);
    } catch (error) {
      try {
        Array.from(normalizedRoot.querySelectorAll(selector)).forEach(pushNode);
      } catch (ignored) {
        return results;
      }
    }
    return results;
  }

  function canMatchSelector(node, selector) {
    if (!(node instanceof Element)) {
      return false;
    }
    try {
      node.matches(selector);
      return true;
    } catch (error) {
      return false;
    }
  }

  function normalizeRoot(root) {
    if (root instanceof Document || root instanceof Element) {
      return root;
    }
    if (typeof root === "string") {
      return parseHtmlFragment(root);
    }
    return document;
  }

  function parseHtml(html) {
    return new DOMParser().parseFromString(html || "", "text/html");
  }

  function parseHtmlFragment(html) {
    const wrapper = document.createElement("div");
    wrapper.innerHTML = html || "";
    return wrapper;
  }

  function requestText(url, options) {
    const response = nativeRequest({
      url: absoluteUrl(url, state.currentSource ? state.currentSource.host : ""),
      method: options && options.method ? options.method : "GET",
      headers: options && options.headers ? options.headers : {},
      timeout: options && options.timeout ? options.timeout : 20000,
      body: options && options.body ? options.body : "",
    });
    if (!response.ok && !response.body) {
      throw new Error(response.error || "请求失败");
    }
    return response.body || "";
  }

  function nativeRequest(payload) {
    if (!android) {
      throw new Error("Android bridge is not available");
    }
    return safeJsonParse(android.httpRequest(JSON.stringify(payload)), {});
  }

  function absoluteUrl(url, host) {
    if (!url) {
      return "";
    }
    if (/^https?:\/\//i.test(url)) {
      return url;
    }
    if (url.indexOf("//") === 0) {
      return "https:" + url;
    }
    if (!host) {
      return url;
    }
    const base = host.endsWith("/") ? host.slice(0, -1) : host;
    if (url.startsWith("/")) {
      return base + url;
    }
    return base + "/" + url;
  }

  function normalizeBaseUrl(host) {
    return host.endsWith("/") ? host : host + "/";
  }

  function getRuleValue(rule, keys) {
    for (let i = 0; i < keys.length; i += 1) {
      if (Object.prototype.hasOwnProperty.call(rule, keys[i])) {
        return rule[keys[i]];
      }
    }
    return undefined;
  }

  function getSourceCapabilities(source) {
    const capabilities = [];
    if (getRuleValue(source, RULE_KEYS.recommend)) capabilities.push("首页解析");
    if (getRuleValue(source, RULE_KEYS.first)) capabilities.push("分类解析");
    if (getRuleValue(source, RULE_KEYS.search)) capabilities.push("搜索解析");
    if (getRuleValue(source, RULE_KEYS.second)) capabilities.push("详情解析");
    if (source.lazy) capabilities.push("懒加载播放");
    if (getRuleValue(source, RULE_KEYS.preprocess)) capabilities.push("预处理");
    if (source.filterable) capabilities.push("筛选支持");
    return capabilities;
  }

  function currentVisibleItems() {
    if (state.page === "search") return state.searchItems;
    if (state.page === "explore") return state.exploreItems;
    return state.homeItems;
  }

  function currentExternalUrl() {
    if (state.currentPlayback && state.currentPlayback.url) return state.currentPlayback.url;
    if (state.detail && state.detail.vod_id) return state.detail.vod_id;
    return "";
  }

  function detectMediaType(url) {
    return String(url || "").toLowerCase().indexOf(".m3u8") !== -1 ? "m3u8" : "normal";
  }

  function isDirectPlayableUrl(url) {
    const lower = String(url || "").toLowerCase();
    return lower.indexOf(".m3u8") !== -1
      || lower.indexOf(".mp4") !== -1
      || lower.indexOf(".m4v") !== -1
      || lower.indexOf(".webm") !== -1
      || lower.indexOf(".flv") !== -1
      || lower.indexOf(".mpd") !== -1
      || lower.indexOf("mime=video") !== -1
      || lower.indexOf("video_mp4") !== -1
      || lower.indexOf("blob:http") === 0;
  }

  function shouldSniffPlaybackCandidate(candidate) {
    const url = candidate && candidate.url ? candidate.url : "";
    if (!url || !/^https?:\/\//i.test(url)) {
      return false;
    }
    if (candidate.parse === 1 || candidate.jx === 1) {
      return true;
    }
    return !isDirectPlayableUrl(url);
  }

  function sniffPlaybackCandidate(candidate) {
    if (!android || typeof android.sniffMediaUrl !== "function") {
      return null;
    }
    const snifferRules = getSnifferRules(state.currentSource, candidate.url);
    const response = safeJsonParse(android.sniffMediaUrl(JSON.stringify({
      url: candidate.url,
      headers: candidate.header || {},
      timeout: 15000,
      maxDepth: snifferRules.maxDepth,
      matchRules: snifferRules.matchRules,
      excludeRules: snifferRules.excludeRules,
    })), {});
    if (response && response.found && response.url) {
      return response;
    }
    return null;
  }

  function getSnifferRules(source, playbackUrl) {
    const matchRules = DEFAULT_SNIFFER_MATCH_RULES.slice();
    const excludeRules = DEFAULT_SNIFFER_EXCLUDE_RULES.slice();
    let maxDepth = 3;
    appendRuleValues(matchRules, source && (source.sniffer_match || source.snifferMatch));
    appendRuleValues(excludeRules, source && (source.sniffer_exclude || source.snifferExclude));
    SITE_SNIFFER_PROFILES.forEach((profile) => {
      if (matchesSnifferProfile(profile, source, playbackUrl)) {
        appendRuleValues(matchRules, profile.matchRules);
        appendRuleValues(excludeRules, profile.excludeRules);
        maxDepth = Math.max(maxDepth, Number(profile.maxDepth || 0));
      }
    });
    if (source && (source.sniffer_depth || source.snifferDepth)) {
      maxDepth = Math.max(maxDepth, Number(source.sniffer_depth || source.snifferDepth || 0));
    }
    return { matchRules, excludeRules, maxDepth: Math.min(4, Math.max(2, maxDepth)) };
  }

  function appendRuleValues(target, value) {
    if (!value) {
      return;
    }
    if (Array.isArray(value)) {
      value.forEach((item) => appendRuleValues(target, item));
      return;
    }
    String(value).split(/[|,;\n]/).map((item) => item.trim()).filter(Boolean).forEach((item) => {
      if (target.indexOf(item) === -1) {
        target.push(item);
      }
    });
  }

  function matchesSnifferProfile(profile, source, playbackUrl) {
    const samples = [
      source && source.host,
      source && source.title,
      source && source.name,
      playbackUrl,
    ].filter(Boolean).join(" ");
    return Boolean(samples && profile.test && profile.test.test(samples));
  }

  function applyHeaders(xhr, headers) {
    Object.keys(headers || {}).forEach((key) => {
      try {
        xhr.setRequestHeader(key, headers[key]);
      } catch (error) {
        console.warn(error);
      }
    });
  }

  function openExternal(url) {
    if (android) {
      android.openExternal(url);
      return;
    }
    window.open(url, "_blank", "noopener,noreferrer");
  }

  function storageKey(rule, key) {
    return "xm:" + (rule.title || rule.name || "source") + ":" + key;
  }

  function normalizeRuleHeaders(headers) {
    const normalized = {};
    Object.keys(headers || {}).forEach((key) => {
      let value = headers[key];
      if (value === "MOBILE_UA") {
        value = MOBILE_UA;
      } else if (value === "PC_UA" || value === "UA") {
        value = PC_UA;
      }
      normalized[key] = value;
    });
    return normalized;
  }

  function getPlayHeaders(rule) {
    return mergeHeaders(rule && rule.headers, rule && rule.play_headers);
  }

  function mergeHeaders(base, extra) {
    return normalizeRuleHeaders(Object.assign({}, base || {}, extra || {}));
  }

  function setStatus(title, detail) {
    dom.statusText.textContent = title;
    if (state.page === "home" && detail) {
      dom.homeSummaryText.textContent = detail;
    }
  }

  function ensureArray(value) {
    return Array.isArray(value) ? value : [];
  }

  function clone(value) {
    return safeJsonParse(JSON.stringify(value), {});
  }

  function safeJsonParse(value, fallback) {
    try {
      return JSON.parse(value);
    } catch (error) {
      return fallback;
    }
  }

  function normalizeText(value) {
    return String(value || "").replace(/\s+/g, " ").trim();
  }

  function escapeHtml(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function escapeAttr(value) {
    return escapeHtml(value);
  }

  function escapeCssUrl(value) {
    return String(value || "").replace(/["\\]/g, "\\$&");
  }

  function shorten(value, limit) {
    const text = String(value || "");
    return text.length <= limit ? text : text.slice(0, limit - 1) + "…";
  }

  function clampValue(value, min, max) {
    return Math.min(max, Math.max(min, Number(value || 0)));
  }

  function describeError(error) {
    return error && error.message ? error.message : String(error || "未知错误");
  }

  function toast(message) {
    if (android) {
      android.toast(String(message));
    } else {
      console.log(message);
    }
  }
})();

