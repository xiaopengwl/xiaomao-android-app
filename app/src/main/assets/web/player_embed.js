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

  function isInternalHeader(key) {
    return /^x-xm-/i.test(String(key || ''));
  }

  function isForbiddenBrowserHeader(key) {
    return /^(accept-charset|accept-encoding|access-control-request-headers|access-control-request-method|connection|content-length|cookie|cookie2|date|dnt|expect|host|keep-alive|origin|referer|te|trailer|transfer-encoding|upgrade|user-agent|via)$/i.test(String(key || ''));
  }

  function stripInternalHeaders(input) {
    const headers = {};
    Object.keys(input || {}).forEach((key) => {
      if (!isInternalHeader(key) && !isForbiddenBrowserHeader(key)) {
        headers[key] = input[key];
      }
    });
    return headers;
  }

  function headerValue(headers, name) {
    const target = String(name || '').toLowerCase();
    const key = Object.keys(headers || {}).find((item) => String(item).toLowerCase() === target);
    return key ? headers[key] : '';
  }

  function parseBackupHosts(raw) {
    const text = String(raw || '').trim();
    if (!text) {
      return [];
    }
    const parseList = function parseList(value) {
      try {
        const data = JSON.parse(value);
        if (Array.isArray(data)) {
          return data.map((item) => String(item || '').replace(/^https?:\/\//i, '').split('/')[0].trim()).filter(Boolean);
        }
      } catch (error) {
        console.warn(error);
      }
      return [];
    };
    let hosts = parseList(text);
    if (!hosts.length) {
      try {
        hosts = parseList(atob(text));
      } catch (error) {
        console.warn(error);
      }
    }
    if (!hosts.length) {
      hosts = text.split(/[,\s]+/).map((item) => item.replace(/^https?:\/\//i, '').split('/')[0].trim()).filter(Boolean);
    }
    return hosts.filter((host, index) => hosts.indexOf(host) === index);
  }

  function hostOf(url) {
    try {
      return new URL(url).hostname;
    } catch (error) {
      return '';
    }
  }

  function replaceHost(url, host) {
    try {
      const next = new URL(url);
      next.hostname = host;
      return next.toString();
    } catch (error) {
      return '';
    }
  }

  function nextBackupUrl(url, backupHosts, attemptedHosts) {
    if (!String(url || '').toLowerCase().includes('/m3u8/')) {
      return '';
    }
    const currentHost = hostOf(url);
    if (currentHost && !attemptedHosts.includes(currentHost)) {
      attemptedHosts.push(currentHost);
    }
    for (let i = 0; i < backupHosts.length; i += 1) {
      const host = backupHosts[i];
      if (!host || host === currentHost || attemptedHosts.includes(host)) {
        continue;
      }
      const next = replaceHost(url, host);
      if (next && next !== url) {
        attemptedHosts.push(host);
        return next;
      }
    }
    return '';
  }

  function applyHeaders(target, headers) {
    Object.keys(headers).forEach((key) => {
      if (isInternalHeader(key)) {
        return;
      }
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

  function toUint8Array(payload) {
    if (!payload) {
      return null;
    }
    if (payload instanceof Uint8Array) {
      return payload;
    }
    if (payload instanceof ArrayBuffer) {
      return new Uint8Array(payload);
    }
    if (ArrayBuffer.isView(payload)) {
      return new Uint8Array(payload.buffer, payload.byteOffset, payload.byteLength);
    }
    return null;
  }

  function hasPngSignature(bytes) {
    return !!bytes
      && bytes.length > 8
      && bytes[0] === 0x89
      && bytes[1] === 0x50
      && bytes[2] === 0x4e
      && bytes[3] === 0x47
      && bytes[4] === 0x0d
      && bytes[5] === 0x0a
      && bytes[6] === 0x1a
      && bytes[7] === 0x0a;
  }

  function looksLikeTsAt(bytes, offset) {
    if (!bytes || offset < 0 || offset >= bytes.length || bytes[offset] !== 0x47) {
      return false;
    }
    if (offset + 188 < bytes.length && bytes[offset + 188] === 0x47) {
      return true;
    }
    if (offset + 376 < bytes.length && bytes[offset + 376] === 0x47) {
      return true;
    }
    return offset + 564 >= bytes.length;
  }

  function unwrapPngWrappedTs(payload) {
    const bytes = toUint8Array(payload);
    if (!hasPngSignature(bytes)) {
      return payload;
    }
    let pngEnd = -1;
    for (let i = 8; i + 7 < bytes.length; i += 1) {
      if (bytes[i] === 0x49
        && bytes[i + 1] === 0x45
        && bytes[i + 2] === 0x4e
        && bytes[i + 3] === 0x44) {
        pngEnd = i + 8;
        break;
      }
    }
    if (pngEnd < 0 || pngEnd >= bytes.length) {
      return payload;
    }
    for (let i = pngEnd; i < bytes.length; i += 1) {
      if (looksLikeTsAt(bytes, i)) {
        return bytes.slice(i).buffer;
      }
    }
    return payload;
  }

  function createWrappedTsAwareLoader() {
    const BaseLoader = window.Hls && window.Hls.DefaultConfig ? window.Hls.DefaultConfig.loader : null;
    if (!BaseLoader) {
      return null;
    }
    function WrappedLoader(config) {
      this.loader = new BaseLoader(config);
      this.context = null;
      this.stats = null;
      this.response = null;
    }
    WrappedLoader.prototype.destroy = function destroy() {
      if (this.loader && typeof this.loader.destroy === 'function') {
        this.loader.destroy();
      }
      this.loader = null;
    };
    WrappedLoader.prototype.abort = function abort() {
      if (this.loader && typeof this.loader.abort === 'function') {
        this.loader.abort();
      }
    };
    WrappedLoader.prototype.load = function load(context, config, callbacks) {
      const self = this;
      const nextCallbacks = Object.assign({}, callbacks, {
        onSuccess: function onSuccess(response, stats, innerContext, networkDetails) {
          if (response && response.data) {
            response.data = unwrapPngWrappedTs(response.data);
          }
          self.stats = stats;
          self.context = innerContext;
          self.response = response;
          callbacks.onSuccess(response, stats, innerContext, networkDetails);
        },
        onProgress: function onProgress(stats, innerContext, data, networkDetails) {
          const nextData = data ? unwrapPngWrappedTs(data) : data;
          if (typeof callbacks.onProgress === 'function') {
            callbacks.onProgress(stats, innerContext, nextData, networkDetails);
          }
        },
      });
      this.loader.load(context, config, nextCallbacks);
    };
    return WrappedLoader;
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
    const requestHeaders = stripInternalHeaders(headers);
    const backupHosts = parseBackupHosts(headerValue(headers, 'X-XM-Backup-Hosts') || options.backupHosts || window._pdf);
    const attemptedHosts = [];
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
      autoplay: true,
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
            const WrappedLoader = createWrappedTsAwareLoader();
            hlsInstance = new window.Hls({
              loader: WrappedLoader || undefined,
              xhrSetup: function (xhr) {
                applyHeaders(xhr, requestHeaders);
              },
              fetchSetup: function (context, initParams) {
                const next = Object.assign({}, initParams || {});
                next.headers = Object.assign({}, next.headers || {}, requestHeaders);
                return new Request(context.url, next);
              },
            });
            if (backupHosts.length && window.Hls && window.Hls.Events) {
              try {
                const initialHost = hostOf(url);
                if (initialHost && !attemptedHosts.includes(initialHost)) {
                  attemptedHosts.push(initialHost);
                }
                hlsInstance.on(window.Hls.Events.ERROR, function onHlsError(event, data) {
                  try {
                    if (!data || !data.fatal || data.type !== window.Hls.ErrorTypes.NETWORK_ERROR) {
                      return;
                    }
                    const nextUrl = nextBackupUrl(url, backupHosts, attemptedHosts);
                    if (!nextUrl) {
                      hlsInstance.startLoad();
                      return;
                    }
                    if (art && art.notice) {
                      art.notice.show = '线路异常，正在切换备用线路...';
                    }
                    if (art && typeof art.switchUrl === 'function') {
                      art.switchUrl(nextUrl).catch(function () {
                        notify('onPlayerError', '备用线路切换失败');
                      });
                    } else {
                      hlsInstance.loadSource(nextUrl);
                    }
                  } catch (error) {
                    console.warn(error);
                  }
                });
              } catch (error) {
                console.warn(error);
              }
            }
            try {
              hlsInstance.on(window.Hls.Events.MANIFEST_PARSED, function onManifestParsed() {
                notify('onPlayerReady');
                try {
                  const promise = video.play && video.play();
                  if (promise && promise.catch) {
                    promise.catch(function () {});
                  }
                } catch (error) {
                  console.warn(error);
                }
              });
            } catch (error) {
              console.warn(error);
            }
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
