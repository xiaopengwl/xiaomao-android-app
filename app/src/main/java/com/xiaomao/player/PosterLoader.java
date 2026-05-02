package com.xiaomao.player;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;

import java.net.URL;
import java.util.Locale;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestOptions;

public final class PosterLoader {
    private PosterLoader() {
    }

    public static void load(ImageView imageView, String url, String title) {
        String cacheKey = url == null ? "" : url.trim();
        Bitmap placeholder = buildPlaceholder(title);
        imageView.setImageBitmap(placeholder);
        if (cacheKey.isEmpty()) {
            Glide.with(imageView).clear(imageView);
            return;
        }
        BitmapDrawable placeholderDrawable = new BitmapDrawable(imageView.getResources(), placeholder);
        Object model = buildModel(cacheKey);
        Glide.with(imageView)
                .load(model)
                .apply(new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(placeholderDrawable)
                        .error(placeholderDrawable))
                .into(imageView);
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
                0xFF18211B,
                0xFF28342D,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, width, height, background);

        Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        accent.setColor(0x3854CC78);
        canvas.drawRoundRect(new RectF(24, 26, 132, 58), 14f, 14f, accent);
        accent.setColor(0x18FFFFFF);
        canvas.drawRoundRect(new RectF(24, 368, width - 24, height - 24), 20f, 20f, accent);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFFF3F5F7);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(16f);
        text.setFakeBoldText(true);
        canvas.drawText("XIAOMAO", 38f, 47f, text);

        String label = shorten(title);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setTextSize(28f);
        text.setColor(0xFFF4F7FA);
        canvas.drawText(label, width / 2f, 424f, text);

        text.setFakeBoldText(false);
        text.setTextSize(16f);
        text.setColor(0xFF9AA8B8);
        canvas.drawText("Poster", width / 2f, 454f, text);
        return bitmap;
    }

    private static Object buildModel(String url) {
        String value = url == null ? "" : url.trim();
        if (value.isEmpty()) {
            return "";
        }
        LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
                .addHeader("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        String referer = buildReferer(value);
        if (!referer.isEmpty()) {
            headers.addHeader("Referer", referer);
        }
        return new GlideUrl(value, headers.build());
    }

    private static String buildReferer(String url) {
        try {
            URL parsed = new URL(url);
            String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.US);
            if (host.contains("doubanio.com")) {
                return "https://m.douban.com/";
            }
            return parsed.getProtocol() + "://" + parsed.getHost() + "/";
        } catch (Exception ignored) {
            return "";
        }
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
