package com.xiaomao.player;

import android.net.Uri;
import android.text.TextUtils;

import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class WrappedSegmentDataSource implements DataSource {
    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final DataSource upstream;
    private final boolean unwrapEnabled;

    private Uri currentUri;
    private Map<String, List<String>> responseHeaders = Collections.emptyMap();
    private byte[] memoryBuffer;
    private int memoryOffset;

    WrappedSegmentDataSource(DataSource upstream, boolean unwrapEnabled) {
        this.upstream = upstream;
        this.unwrapEnabled = unwrapEnabled;
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        upstream.addTransferListener(transferListener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        memoryBuffer = null;
        memoryOffset = 0;
        currentUri = null;
        responseHeaders = Collections.emptyMap();

        long length = upstream.open(dataSpec);
        currentUri = upstream.getUri();
        responseHeaders = upstream.getResponseHeaders();

        if (!shouldUnwrap(dataSpec, currentUri, responseHeaders)) {
            return length;
        }

        byte[] raw = readAll(upstream);
        memoryBuffer = unwrapPngWrappedTs(raw);
        memoryOffset = 0;
        return memoryBuffer.length;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (memoryBuffer == null) {
            return upstream.read(buffer, offset, length);
        }
        if (memoryOffset >= memoryBuffer.length) {
            return -1;
        }
        int copyLength = Math.min(length, memoryBuffer.length - memoryOffset);
        System.arraycopy(memoryBuffer, memoryOffset, buffer, offset, copyLength);
        memoryOffset += copyLength;
        return copyLength;
    }

    @Override
    public Uri getUri() {
        return currentUri != null ? currentUri : upstream.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders == null ? Collections.emptyMap() : responseHeaders;
    }

    @Override
    public void close() throws IOException {
        memoryBuffer = null;
        memoryOffset = 0;
        currentUri = null;
        responseHeaders = Collections.emptyMap();
        upstream.close();
    }

    private boolean shouldUnwrap(DataSpec dataSpec, Uri uri, Map<String, List<String>> headers) {
        if (!unwrapEnabled) {
            return false;
        }
        String lower = "";
        if (uri != null) {
            lower = String.valueOf(uri).toLowerCase(Locale.ROOT);
        } else if (dataSpec != null && dataSpec.uri != null) {
            lower = String.valueOf(dataSpec.uri).toLowerCase(Locale.ROOT);
        }
        if (lower.contains(".m3u8") || lower.contains("/m3u8/")) {
            return false;
        }
        if (lower.contains(".png") || lower.contains("xhscdn.com") || lower.contains("yximgs.com")) {
            return true;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
            if (!"content-type".equals(key)) {
                continue;
            }
            for (String value : entry.getValue()) {
                if (!TextUtils.isEmpty(value) && value.toLowerCase(Locale.ROOT).contains("image/png")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static byte[] readAll(DataSource source) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(256 * 1024);
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = source.read(buffer, 0, buffer.length)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static byte[] unwrapPngWrappedTs(byte[] raw) {
        if (!hasPngSignature(raw)) {
            return raw;
        }
        int pngEnd = findPngEnd(raw);
        if (pngEnd < 0 || pngEnd >= raw.length) {
            return raw;
        }
        for (int i = pngEnd; i < raw.length; i++) {
            if (looksLikeTsAt(raw, i)) {
                return Arrays.copyOfRange(raw, i, raw.length);
            }
        }
        return raw;
    }

    private static boolean hasPngSignature(byte[] raw) {
        if (raw == null || raw.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (raw[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static int findPngEnd(byte[] raw) {
        for (int i = 8; i + 7 < raw.length; i++) {
            if (raw[i] == 0x49
                    && raw[i + 1] == 0x45
                    && raw[i + 2] == 0x4E
                    && raw[i + 3] == 0x44) {
                return i + 8;
            }
        }
        return -1;
    }

    private static boolean looksLikeTsAt(byte[] raw, int offset) {
        if (offset < 0 || offset >= raw.length || raw[offset] != 0x47) {
            return false;
        }
        if (offset + 188 < raw.length && raw[offset + 188] == 0x47) {
            return true;
        }
        if (offset + 376 < raw.length && raw[offset + 376] == 0x47) {
            return true;
        }
        return offset + 564 >= raw.length;
    }

    static final class Factory implements DataSource.Factory {
        private final DataSource.Factory upstreamFactory;
        private final boolean unwrapEnabled;

        Factory(DataSource.Factory upstreamFactory, boolean unwrapEnabled) {
            this.upstreamFactory = upstreamFactory;
            this.unwrapEnabled = unwrapEnabled;
        }

        @Override
        public DataSource createDataSource() {
            return new WrappedSegmentDataSource(upstreamFactory.createDataSource(), unwrapEnabled);
        }
    }
}
