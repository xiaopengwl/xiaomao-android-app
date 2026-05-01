package com.xiaomao.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.extractor.DefaultExtractorsFactory;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;

public class XiaomaoDkExoPlayer extends AbstractPlayer {
    private static final String DEFAULT_MOBILE_UA = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";
    private static final String INTERNAL_STREAM_TYPE_HEADER = "X-XM-Stream-Type";
    private static final String INTERNAL_HEADER_PREFIX = "X-XM-";

    private final Context appContext;
    private ExoPlayer player;
    private String dataSource = "";
    private final HashMap<String, String> requestHeaders = new HashMap<>();
    private String forcedStreamType = "";
    private AssetFileDescriptor assetFileDescriptor;
    private Surface surface;
    private SurfaceHolder surfaceHolder;
    private float speed = 1.0f;
    private boolean looping = false;
    private boolean prepared = false;
    private boolean buffering = false;

    public XiaomaoDkExoPlayer(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void initPlayer() {
        releasePlayer();
        player = new ExoPlayer.Builder(appContext).build();
        player.addListener(playerListener);
        player.setRepeatMode(looping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        player.setPlaybackParameters(new PlaybackParameters(speed));
        applyRenderSurface();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void setDataSource(String path, Map headers) {
        dataSource = path == null ? "" : path.trim();
        assetFileDescriptor = null;
        requestHeaders.clear();
        forcedStreamType = "";
        if (headers == null) {
            return;
        }
        for (Object keyObj : headers.keySet()) {
            if (keyObj == null) {
                continue;
            }
            Object valueObj = headers.get(keyObj);
            if (valueObj == null) {
                continue;
            }
            String key = String.valueOf(keyObj).trim();
            String value = String.valueOf(valueObj).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            if (INTERNAL_STREAM_TYPE_HEADER.equalsIgnoreCase(key)) {
                forcedStreamType = value.toLowerCase(Locale.ROOT);
                continue;
            }
            if (key.regionMatches(true, 0, INTERNAL_HEADER_PREFIX, 0, INTERNAL_HEADER_PREFIX.length())) {
                continue;
            }
            requestHeaders.put(key, value);
        }
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        assetFileDescriptor = fd;
        dataSource = "";
        requestHeaders.clear();
        forcedStreamType = "";
    }

    @Override
    public void start() {
        if (player != null) {
            player.play();
        }
    }

    @Override
    public void pause() {
        if (player != null) {
            player.pause();
        }
    }

    @Override
    public void stop() {
        if (player != null) {
            player.stop();
        }
        prepared = false;
        buffering = false;
    }

    @Override
    public void prepareAsync() {
        if (player == null) {
            initPlayer();
        }
        if (player == null) {
            notifyError();
            return;
        }
        if (assetFileDescriptor != null || TextUtils.isEmpty(dataSource)) {
            notifyError();
            return;
        }
        prepared = false;
        buffering = false;
        StreamType streamType = inferStreamType(dataSource, forcedStreamType, requestHeaders);
        DataSource.Factory dataSourceFactory = buildDataSourceFactory(requestHeaders);
        MediaItem mediaItem = buildMediaItem(dataSource, streamType);
        MediaSource mediaSource = buildMediaSource(mediaItem, dataSourceFactory, streamType);
        player.setRepeatMode(looping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        player.setPlaybackParameters(new PlaybackParameters(speed));
        applyRenderSurface();
        player.setMediaSource(mediaSource);
        player.prepare();
    }

    @Override
    public void reset() {
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        prepared = false;
        buffering = false;
    }

    @Override
    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    @Override
    public void seekTo(long time) {
        if (player != null) {
            player.seekTo(Math.max(0L, time));
        }
    }

    @Override
    public void release() {
        releasePlayer();
        requestHeaders.clear();
        dataSource = "";
        forcedStreamType = "";
        assetFileDescriptor = null;
        surface = null;
        surfaceHolder = null;
        prepared = false;
        buffering = false;
    }

    @Override
    public long getCurrentPosition() {
        return player == null ? 0L : Math.max(0L, player.getCurrentPosition());
    }

    @Override
    public long getDuration() {
        return player == null ? 0L : Math.max(0L, player.getDuration());
    }

    @Override
    public int getBufferedPercentage() {
        return player == null ? 0 : Math.max(0, player.getBufferedPercentage());
    }

    @Override
    public void setSurface(Surface surface) {
        this.surface = surface;
        this.surfaceHolder = null;
        applyRenderSurface();
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        this.surfaceHolder = holder;
        this.surface = null;
        applyRenderSurface();
    }

    @Override
    public void setVolume(float v1, float v2) {
        if (player != null) {
            player.setVolume(Math.max(0f, Math.min(1f, (v1 + v2) / 2f)));
        }
    }

    @Override
    public void setLooping(boolean isLooping) {
        looping = isLooping;
        if (player != null) {
            player.setRepeatMode(looping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
        }
    }

    @Override
    public void setOptions() {
        // No extra player options are required right now.
    }

    @Override
    public void setSpeed(float speed) {
        this.speed = speed <= 0f ? 1.0f : speed;
        if (player != null) {
            player.setPlaybackParameters(new PlaybackParameters(this.speed));
        }
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public long getTcpSpeed() {
        return 0L;
    }

    private void applyRenderSurface() {
        if (player == null) {
            return;
        }
        if (surface != null) {
            player.setVideoSurface(surface);
        } else if (surfaceHolder != null) {
            player.setVideoSurfaceHolder(surfaceHolder);
        }
    }

    private DataSource.Factory buildDataSourceFactory(Map<String, String> headers) {
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000);
        String userAgent = headers == null ? "" : headers.get("User-Agent");
        httpFactory.setUserAgent(TextUtils.isEmpty(userAgent) ? DEFAULT_MOBILE_UA : userAgent);
        HashMap<String, String> requestProps = new HashMap<>();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().trim();
                String value = entry.getValue() == null ? "" : entry.getValue().trim();
                if (key.isEmpty()
                        || value.isEmpty()
                        || INTERNAL_STREAM_TYPE_HEADER.equalsIgnoreCase(key)
                        || key.regionMatches(true, 0, INTERNAL_HEADER_PREFIX, 0, INTERNAL_HEADER_PREFIX.length())) {
                    continue;
                }
                requestProps.put(key, value);
            }
        }
        if (!requestProps.isEmpty()) {
            httpFactory.setDefaultRequestProperties(requestProps);
        }
        DataSource.Factory upstreamFactory = new DefaultDataSource.Factory(appContext, httpFactory);
        return new WrappedSegmentDataSource.Factory(
                upstreamFactory,
                shouldEnableWrappedSegmentFix(dataSource, forcedStreamType, requestHeaders)
        );
    }

    private MediaItem buildMediaItem(String url, StreamType streamType) {
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

    private MediaSource buildMediaSource(MediaItem mediaItem, DataSource.Factory dataSourceFactory, StreamType streamType) {
        if (streamType == StreamType.HLS) {
            return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        }
        if (streamType == StreamType.DASH) {
            return new DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        }
        if (streamType == StreamType.PROGRESSIVE) {
            DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                    .setConstantBitrateSeekingEnabled(true);
            return new ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    .createMediaSource(mediaItem);
        }
        return new DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem);
    }

    private StreamType inferStreamType(String url, String forcedType, Map<String, String> headers) {
        String forced = forcedType == null ? "" : forcedType.trim().toLowerCase(Locale.ROOT);
        if ("hls".equals(forced) || "m3u8".equals(forced)) {
            return StreamType.HLS;
        }
        if ("dash".equals(forced) || "mpd".equals(forced)) {
            return StreamType.DASH;
        }
        if ("progressive".equals(forced) || "mp4".equals(forced)) {
            return StreamType.PROGRESSIVE;
        }
        String normalized = decodeUrl(url).toLowerCase(Locale.ROOT);
        String contentType = "";
        if (headers != null) {
            String headerValue = headers.get("Content-Type");
            if (TextUtils.isEmpty(headerValue)) {
                headerValue = headers.get("content-type");
            }
            contentType = headerValue == null ? "" : headerValue.trim().toLowerCase(Locale.ROOT);
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

    private boolean shouldEnableWrappedSegmentFix(String url, String forcedType, Map<String, String> headers) {
        if (inferStreamType(url, forcedType, headers) != StreamType.HLS) {
            return false;
        }
        String lower = decodeUrl(url).toLowerCase(Locale.ROOT);
        if (lower.contains("zijieapi.douyinbyte.com/m3u8/")) {
            return true;
        }
        String referer = headers == null ? "" : String.valueOf(headers.getOrDefault("Referer", ""));
        return referer.toLowerCase(Locale.ROOT).contains("4kvm.me");
    }

    private String inferProgressiveMimeType(String url) {
        String normalized = decodeUrl(url).toLowerCase(Locale.ROOT);
        if (normalized.contains(".mkv")) {
            return MimeTypes.VIDEO_MATROSKA;
        }
        if (normalized.contains(".flv")) {
            return MimeTypes.VIDEO_FLV;
        }
        if (normalized.contains(".ts")) {
            return MimeTypes.VIDEO_MP2T;
        }
        return MimeTypes.VIDEO_MP4;
    }

    private String decodeUrl(String url) {
        String safeUrl = url == null ? "" : url.trim();
        if (safeUrl.contains("%")) {
            try {
                safeUrl = URLDecoder.decode(safeUrl, "UTF-8");
            } catch (Exception ignored) {
            }
        }
        return safeUrl;
    }

    private void notifyError() {
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onError();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.removeListener(playerListener);
            player.release();
            player = null;
        }
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (mPlayerEventListener == null) {
                return;
            }
            if (playbackState == Player.STATE_BUFFERING) {
                if (!buffering) {
                    buffering = true;
                    mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, getBufferedPercentage());
                }
                return;
            }
            if (buffering && playbackState == Player.STATE_READY) {
                buffering = false;
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
            }
            if (playbackState == Player.STATE_READY && !prepared) {
                prepared = true;
                mPlayerEventListener.onPrepared();
                mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                return;
            }
            if (playbackState == Player.STATE_ENDED) {
                mPlayerEventListener.onCompletion();
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            prepared = false;
            buffering = false;
            notifyError();
        }

        @Override
        public void onVideoSizeChanged(VideoSize videoSize) {
            if (mPlayerEventListener != null && videoSize != null) {
                mPlayerEventListener.onVideoSizeChanged(videoSize.width, videoSize.height);
            }
        }
    };

    private enum StreamType {
        AUTO,
        HLS,
        DASH,
        PROGRESSIVE
    }
}
