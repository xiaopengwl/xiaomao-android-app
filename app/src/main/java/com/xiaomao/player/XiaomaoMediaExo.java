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
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;

import cn.jzvd.JZDataSource;
import cn.jzvd.JZMediaInterface;
import cn.jzvd.Jzvd;

public class XiaomaoMediaExo extends JZMediaInterface {
    private static final long PREPARE_TIMEOUT_MS = 12000L;

    private enum StreamType {
        HLS,
        DASH,
        PROGRESSIVE
    }

    private ExoPlayer exoPlayer;
    private Surface surface;
    private SurfaceTexture surfaceTexture;
    private boolean preparedNotified = false;
    private float speed = 1.0f;
    private JZDataSource pendingDataSource;
    private ArrayList<StreamType> streamTypes = new ArrayList<>();
    private int streamTypeIndex = -1;
    private final Runnable prepareTimeoutRunnable = () -> {
        if (!preparedNotified) {
            retryWithNextStreamType();
        }
    };

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            postBufferProgress();
            if (playbackState == Player.STATE_READY && !preparedNotified) {
                cancelPrepareTimeout();
                preparedNotified = true;
                postToUi(() -> {
                    if (jzvd != null) {
                        jzvd.onPrepared();
                    }
                });
                return;
            }
            if (playbackState == Player.STATE_IDLE && !preparedNotified) {
                retryWithNextStreamType();
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
            if (retryWithNextStreamType()) {
                return;
            }
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
        pendingDataSource = resolveDataSource();
        if (jzvd == null || jzvd.getContext() == null || pendingDataSource == null) {
            return;
        }
        preparedNotified = false;
        streamTypes = buildStreamTypeQueue(pendingDataSource);
        streamTypeIndex = -1;
        prepareCurrentStreamType(true);
    }

    private void releaseInternal() {
        cancelPrepareTimeout();
        preparedNotified = false;
        pendingDataSource = null;
        streamTypeIndex = -1;
        streamTypes.clear();
        releasePlayerOnly();
    }

    private void releasePlayerOnly() {
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

    private MediaItem buildMediaItem(JZDataSource dataSource, StreamType streamType) {
        String url = "";
        if (dataSource != null && dataSource.getCurrentUrl() != null) {
            url = String.valueOf(dataSource.getCurrentUrl()).trim();
        }
        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));
        if (streamType == StreamType.HLS) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        } else if (streamType == StreamType.DASH) {
            builder.setMimeType(MimeTypes.APPLICATION_MPD);
        } else if (streamType == StreamType.PROGRESSIVE) {
            builder.setMimeType(inferProgressiveMimeType(url));
        }
        return builder.build();
    }

    private JZDataSource resolveDataSource() {
        try {
            java.lang.reflect.Field field = JZMediaInterface.class.getDeclaredField("jzDataSource");
            field.setAccessible(true);
            Object value = field.get(this);
            if (value instanceof JZDataSource) {
                return (JZDataSource) value;
            }
        } catch (Throwable ignored) {
        }
        if (jzvd != null) {
            return jzvd.jzDataSource;
        }
        return null;
    }

    private Map<String, String> buildHeaders(JZDataSource dataSource) {
        HashMap<String, String> headers = new HashMap<>();
        if (dataSource != null && dataSource.headerMap != null) {
            for (Map.Entry<String, String> entry : dataSource.headerMap.entrySet()) {
                String key = normalizeHeaderName(entry.getKey());
                String value = entry.getValue();
                if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                    headers.put(key, value);
                }
            }
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

    private boolean prepareCurrentStreamType(boolean firstAttempt) {
        if (pendingDataSource == null || jzvd == null || jzvd.getContext() == null) {
            return false;
        }
        if (firstAttempt) {
            streamTypeIndex = 0;
        } else {
            streamTypeIndex++;
        }
        if (streamTypeIndex < 0 || streamTypeIndex >= streamTypes.size()) {
            return false;
        }

        cancelPrepareTimeout();
        releasePlayerOnly();
        preparedNotified = false;

        StreamType streamType = streamTypes.get(streamTypeIndex);
        Map<String, String> requestHeaders = buildHeaders(pendingDataSource);
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs((int) PREPARE_TIMEOUT_MS)
                .setReadTimeoutMs((int) PREPARE_TIMEOUT_MS);
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
        MediaItem mediaItem = buildMediaItem(pendingDataSource, streamType);
        exoPlayer.setMediaSource(buildMediaSource(mediaItem, httpFactory, streamType));
        exoPlayer.prepare();
        schedulePrepareTimeout();
        return true;
    }

    private boolean retryWithNextStreamType() {
        if (pendingDataSource == null) {
            return false;
        }
        return prepareCurrentStreamType(false);
    }

    private void schedulePrepareTimeout() {
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(prepareTimeoutRunnable);
        handler.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS);
    }

    private void cancelPrepareTimeout() {
        if (handler != null) {
            handler.removeCallbacks(prepareTimeoutRunnable);
        }
    }

    private ArrayList<StreamType> buildStreamTypeQueue(JZDataSource dataSource) {
        LinkedHashSet<StreamType> ordered = new LinkedHashSet<>();
        String url = "";
        if (dataSource != null && dataSource.getCurrentUrl() != null) {
            url = String.valueOf(dataSource.getCurrentUrl());
        }
        StreamType primary = inferPrimaryStreamType(url, buildHeaders(dataSource));
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
            contentType = headerValue == null ? "" : headerValue.toLowerCase(Locale.ROOT);
        }
        if (normalized.contains(".m3u8")
                || normalized.contains("/m3u8")
                || normalized.contains("m3u8?")
                || normalized.contains("type=m3u8")
                || normalized.contains("format=m3u8")
                || normalized.contains("application/vnd.apple.mpegurl")
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
                || normalized.contains(".flv")
                || normalized.contains(".mkv")
                || normalized.contains("video_mp4")
                || contentType.startsWith("video/")) {
            return StreamType.PROGRESSIVE;
        }
        return StreamType.HLS;
    }

    private MediaSource buildMediaSource(MediaItem mediaItem, DefaultHttpDataSource.Factory httpFactory, StreamType streamType) {
        if (streamType == StreamType.HLS) {
            return new HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem);
        }
        if (streamType == StreamType.DASH) {
            return new DashMediaSource.Factory(httpFactory).createMediaSource(mediaItem);
        }
        return new ProgressiveMediaSource.Factory(httpFactory).createMediaSource(mediaItem);
    }

    private String inferProgressiveMimeType(String url) {
        String normalized = decodeUrl(url).toLowerCase(Locale.ROOT);
        if (normalized.contains(".mp4")) {
            return MimeTypes.VIDEO_MP4;
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
        String value = url == null ? "" : url.trim();
        if (value.contains("%")) {
            try {
                value = java.net.URLDecoder.decode(value, "UTF-8");
            } catch (Exception ignored) {
            }
        }
        return value.replace("\\/", "/");
    }

    private String normalizeHeaderName(String name) {
        if (TextUtils.isEmpty(name)) {
            return name;
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        if ("user-agent".equals(lower)) {
            return "User-Agent";
        }
        if ("referer".equals(lower)) {
            return "Referer";
        }
        if ("cookie".equals(lower)) {
            return "Cookie";
        }
        if ("accept".equals(lower)) {
            return "Accept";
        }
        if ("content-type".equals(lower)) {
            return "Content-Type";
        }
        return name.trim();
    }
}
