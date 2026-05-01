package com.xiaomao.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AvatarLoader {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 24)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value == null ? 0 : value.getByteCount();
        }
    };

    private AvatarLoader() {
    }

    public static void load(ImageView imageView, String url) {
        String cacheKey = url == null ? "" : url.trim();
        imageView.setTag(cacheKey);
        imageView.setImageBitmap(buildPlaceholder(imageView.getContext()));
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
            Bitmap circle = cropCircle(bitmap);
            CACHE.put(cacheKey, circle);
            imageView.post(() -> {
                Object tag = imageView.getTag();
                if (tag != null && cacheKey.equals(tag.toString())) {
                    imageView.setImageBitmap(circle);
                }
            });
        });
    }

    private static Bitmap download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
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

    private static Bitmap cropCircle(Bitmap source) {
        int size = Math.min(source.getWidth(), source.getHeight());
        Bitmap squared = Bitmap.createBitmap(source,
                Math.max(0, (source.getWidth() - size) / 2),
                Math.max(0, (source.getHeight() - size) / 2),
                size,
                size);
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(squared, 0, 0, paint);
        return output;
    }

    private static Bitmap buildPlaceholder(Context context) {
        Bitmap raw = BitmapFactory.decodeResource(context.getResources(), R.drawable.xm_launcher_cat);
        if (raw == null) {
            Bitmap fallback = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(fallback);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(0xFF1F7A46);
            canvas.drawCircle(48f, 48f, 48f, paint);
            return fallback;
        }
        return cropCircle(raw);
    }
}
