package com.xiaomao.player;

import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Surface;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import cn.jzvd.JZDataSource;
import cn.jzvd.JZMediaInterface;
import cn.jzvd.Jzvd;

public class XiaomaoMediaExo extends JZMediaInterface {
    private ExoPlayer exoPlayer;
    private Surface surface;
    private SurfaceTexture surfaceTexture;
    private boolean preparedNotified = false;
    private float speed = 1.0f;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            postBufferProgress();
            if (playbackState == Player.STATE_READY && !preparedNotified) {
                preparedNotified = true;
                postToUi(() -> {
                    if (jzvd != null) {
                        jzvd.onPrepared();
                    }
                });
                return;
            }
            if (playbackState == Player.STATE_ENDED) {
                postToUi(() -> {
                    if (jzvd != null) {
                        jzvd.onCompletion();
                    }
                });
            }
        }

        @Override
        public void onVideoSizeChanged(VideoSize videoSize) {
            postToUi(() -> {
                if (jzvd != null) {
                    jzvd.onVideoSizeChanged(videoSize.width, videoSize.height);
                }
            });
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            postToUi(() -> {
                if (jzvd != null) {
                    jzvd.onError(error.errorCode, 0);
                }
            });
        }

        @Override
        public void onIsLoadingChanged(boolean isLoading) {
            postBufferProgress();
        }
    };

    public XiaomaoMediaExo(Jzvd jzvd) {
        super(jzvd);
    }

    @Override
    public void start() {
        postToUi(() -> {
            if (exoPlayer != null) {
                exoPlayer.play();
            }
        });
    }

    @Override
    public void prepare() {
        postToUi(this::prepareInternal);
    }

    @Override
    public void pause() {
        postToUi(() -> {
            if (exoPlayer != null) {
                exoPlayer.pause();
            }
        });
    }

    @Override
    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    @Override
    public void seekTo(long time) {
        postToUi(() -> {
            if (exoPlayer == null) {
                return;
            }
            exoPlayer.seekTo(time);
            if (jzvd != null) {
                jzvd.onSeekComplete();
            }
        });
    }

    @Override
    public void release() {
        postToUi(this::releaseInternal);
    }

    @Override
    public long getCurrentPosition() {
        if (exoPlayer == null) {
            return 0L;
        }
        return Math.max(0L, exoPlayer.getCurrentPosition());
    }

    @Override
    public long getDuration() {
        if (exoPlayer == null) {
            return 0L;
        }
        long duration = exoPlayer.getDuration();
        return duration == C.TIME_UNSET ? 0L : Math.max(0L, duration);
    }

    @Override
    public void setSpeed(float speed) {
        this.speed = speed > 0f ? speed : 1.0f;
        postToUi(() -> {
            if (exoPlayer != null) {
                exoPlayer.setPlaybackParameters(new PlaybackParameters(this.speed));
            }
        });
    }

    @Override
    public void setSurface(Surface surface) {
        if (this.surface != null && this.surface != surface) {
            this.surface.release();
        }
        this.surface = surface;
        postToUi(() -> {
            if (exoPlayer != null) {
                exoPlayer.setVideoSurface(surface);
            }
        });
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        float volume = Math.max(0f, Math.min(1f, (leftVolume + rightVolume) * 0.5f));
        postToUi(() -> {
            if (exoPlayer != null) {
                exoPlayer.setVolume(volume);
            }
        });
    }

    private void prepareInternal() {
        releaseInternal();
        if (jzvd == null || jzvd.getContext() == null || jzvd.jzDataSource == null) {
            return;
        }

        Map<String, String> requestHeaders = buildHeaders(jzvd.jzDataSource);
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true);
        String userAgent = requestHeaders.get("User-Agent");
        if (!TextUtils.isEmpty(userAgent)) {
            httpFactory.setUserAgent(userAgent);
        }
        if (!requestHeaders.isEmpty()) {
            httpFactory.setDefaultRequestProperties(requestHeaders);
        }

        exoPlayer = new ExoPlayer.Builder(jzvd.getContext())
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .build();
        exoPlayer.addListener(playerListener);
        exoPlayer.setPlaybackParameters(new PlaybackParameters(speed));
        if (surface != null) {
            exoPlayer.setVideoSurface(surface);
        }
        exoPlayer.setMediaItem(buildMediaItem(jzvd.jzDataSource));
        exoPlayer.prepare();
    }

    private void releaseInternal() {
        preparedNotified = false;
        if (exoPlayer == null) {
            return;
        }
        exoPlayer.removeListener(playerListener);
        exoPlayer.release();
        exoPlayer = null;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        this.surfaceTexture = surfaceTexture;
        setSurface(new Surface(surfaceTexture));
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        if (exoPlayer != null) {
            exoPlayer.setVideoSurface(null);
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        this.surfaceTexture = null;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    private MediaItem buildMediaItem(JZDataSource dataSource) {
        String url = "";
        if (dataSource != null && dataSource.getCurrentUrl() != null) {
            url = String.valueOf(dataSource.getCurrentUrl());
        }
        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains(".m3u8")) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        } else if (lower.contains(".mpd")) {
            builder.setMimeType(MimeTypes.APPLICATION_MPD);
        } else if (lower.contains(".mp4")) {
            builder.setMimeType(MimeTypes.VIDEO_MP4);
        }
        return builder.build();
    }

    private Map<String, String> buildHeaders(JZDataSource dataSource) {
        HashMap<String, String> headers = new HashMap<>();
        if (dataSource != null && dataSource.headerMap != null) {
            headers.putAll(dataSource.headerMap);
        }
        if (!headers.containsKey("Accept")) {
            headers.put("Accept", "*/*");
        }
        return headers;
    }

    private void postBufferProgress() {
        if (exoPlayer == null) {
            return;
        }
        int bufferProgress = exoPlayer.getBufferedPercentage();
        postToUi(() -> {
            if (jzvd != null) {
                jzvd.setBufferProgress(bufferProgress);
            }
        });
    }

    private void postToUi(Runnable runnable) {
        if (runnable == null || handler == null) {
            return;
        }
        handler.post(runnable);
    }
}
