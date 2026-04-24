(function () {
  let art = null;
  let hlsInstance = null;
  let currentRate = 1.0;

  function bridge() {
    return window.XmVideoBridge || null;
  }

  function notify(method, payload) {
    try {
      const b = bridge();
      if (b && typeof b[method] === 'function') {
        if (payload === undefined) {
          b[method]();
        } else {
          b[method](String(payload));
        }
      }
    } catch (error) {
      console.warn(error);
    }
  }

  function normalizeHeaders(input) {
    if (!input || typeof input !== 'object') {
      return {};
    }
    const headers = {};
    Object.keys(input).forEach((key) => {
      const value = input[key];
      if (key && value !== undefined && value !== null && String(value).trim()) {
        headers[key] = String(value).trim();
      }
    });
    return headers;
  }

  function applyHeaders(target, headers) {
    Object.keys(headers).forEach((key) => {
      try {
        target.setRequestHeader(key, headers[key]);
      } catch (error) {
        console.warn(error);
      }
    });
  }

  function detectType(url, explicitType) {
    if (explicitType) {
      return explicitType;
    }
    const lower = String(url || '').toLowerCase();
    if (lower.indexOf('.m3u8') !== -1 || lower.indexOf('type=m3u8') !== -1 || lower.indexOf('format=m3u8') !== -1) {
      return 'm3u8';
    }
    return 'normal';
  }

  function destroyPlayer() {
    if (art) {
      try {
        art.destroy(false);
      } catch (error) {
        console.warn(error);
      }
      art = null;
    }
    if (hlsInstance) {
      try {
        hlsInstance.destroy();
      } catch (error) {
        console.warn(error);
      }
      hlsInstance = null;
    }
  }

  window.xmPlayerInit = function xmPlayerInit(config) {
    destroyPlayer();
    const options = typeof config === 'string' ? JSON.parse(config) : (config || {});
    const headers = normalizeHeaders(options.headers);
    const type = detectType(options.url, options.type);
    const mount = document.getElementById('artplayer-app');
    if (!mount) {
      notify('onPlayerError', '播放器容器不存在');
      return;
    }

    art = new Artplayer({
      container: mount,
      url: options.url || '',
      title: options.title || '',
      poster: options.poster || '',
      lang: 'zh-cn',
      theme: '#e13f5a',
      autoSize: true,
      autoMini: false,
      playsInline: true,
      fullscreen: true,
      fullscreenWeb: true,
      playbackRate: true,
      aspectRatio: true,
      setting: true,
      pip: true,
      mutex: true,
      backdrop: false,
      hotkey: false,
      type: type === 'm3u8' ? 'm3u8' : undefined,
      moreVideoAttr: {
        preload: 'auto',
        playsInline: true,
        'webkit-playsinline': true,
        'x5-playsinline': true,
        'x5-video-player-type': 'h5',
        'x5-video-player-fullscreen': 'true',
      },
      customType: {
        m3u8: function (video, url) {
          if (window.Hls && window.Hls.isSupported()) {
            hlsInstance = new window.Hls({
              xhrSetup: function (xhr) {
                applyHeaders(xhr, headers);
              },
              fetchSetup: function (context, initParams) {
                const next = Object.assign({}, initParams || {});
                next.headers = Object.assign({}, next.headers || {}, headers);
                return new Request(context.url, next);
              },
            });
            hlsInstance.loadSource(url);
            hlsInstance.attachMedia(video);
          } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
            video.src = url;
          } else {
            notify('onPlayerError', '当前 WebView 不支持 HLS 播放');
          }
        },
      },
    });

    art.on('ready', function () {
      currentRate = 1.0;
      notify('onPlayerReady');
      notify('onRateChanged', currentRate.toFixed(2));
    });
    art.on('error', function () {
      notify('onPlayerError', 'Artplayer 播放异常');
    });
    art.on('fullscreen', function (value) {
      notify('onFullscreenChanged', value ? '1' : '0');
    });
    art.on('fullscreenWeb', function (value) {
      notify('onWebFullscreenChanged', value ? '1' : '0');
    });
  };

  window.xmPlayerSetRate = function xmPlayerSetRate(rate) {
    currentRate = Number(rate || 1) || 1;
    if (art) {
      art.playbackRate = currentRate;
    }
    notify('onRateChanged', currentRate.toFixed(2));
  };

  window.xmPlayerToggleFullscreen = function xmPlayerToggleFullscreen() {
    if (art) {
      art.fullscreen = !art.fullscreen;
    }
  };

  window.xmPlayerToggleWebFullscreen = function xmPlayerToggleWebFullscreen() {
    if (art) {
      art.fullscreenWeb = !art.fullscreenWeb;
    }
  };

  window.xmPlayerDestroy = function xmPlayerDestroy() {
    destroyPlayer();
  };
})();
