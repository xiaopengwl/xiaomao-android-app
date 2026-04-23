package com.xiaomao.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PosterLoader {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 16)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value == null ? 0 : value.getByteCount();
        }
    };

    private PosterLoader() {
    }

    public static void load(ImageView imageView, String url, String title) {
        String cacheKey = url == null ? "" : url.trim();
        imageView.setTag(cacheKey);
        Bitmap placeholder = buildPlaceholder(title);
        imageView.setImageBitmap(placeholder);
        if (cacheKey.isEmpty()) {
            return;
        }
        Bitmap cached = CACHE.get(cacheKey);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }
        EXECUTOR.execute(() -> {
            Bitmap bitmap = download(cacheKey);
            if (bitmap == null) {
                return;
            }
            CACHE.put(cacheKey, bitmap);
            imageView.post(() -> {
                Object tag = imageView.getTag();
                if (tag != null && cacheKey.equals(tag.toString())) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }

    private static Bitmap download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
            try (InputStream stream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(stream);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static Bitmap buildPlaceholder(String title) {
        int width = 360;
        int height = 500;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setShader(new LinearGradient(
                0,
                0,
                width,
                height,
                0xFF14213D,
                0xFF0A0F1F,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, width, height, background);

        Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        accent.setColor(0x332BE4FF);
        canvas.drawCircle(70, 82, 92, accent);
        accent.setColor(0x2872FFC7);
        canvas.drawCircle(308, 438, 124, accent);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFFDCE7F6);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(34f);
        text.setFakeBoldText(true);
        canvas.drawText("XIAOMAO", width / 2f, 240f, text);

        String label = shorten(title);
        text.setFakeBoldText(false);
        text.setTextSize(22f);
        text.setColor(0xFF97A9C0);
        Rect bounds = new Rect();
        text.getTextBounds(label, 0, label.length(), bounds);
        canvas.drawText(label, width / 2f, 288f, text);
        return bitmap;
    }

    private static String shorten(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) {
            return "Poster";
        }
        if (value.length() > 10) {
            return value.substring(0, 10);
        }
        return value;
    }
}
