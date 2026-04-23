package com.xiaomao.player;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import cn.jzvd.Jzvd;
import cn.jzvd.JzvdStd;

public class PlayerActivity extends AppCompatActivity {
    private JzvdStd playerView;
    private TextView titleView;
    private TextView subtitleView;
    private String playUrl;
    private String titleText;
    private String headersJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        titleView = findViewById(R.id.player_title);
        subtitleView = findViewById(R.id.player_subtitle);
        playerView = findViewById(R.id.player_video);
        ImageButton backButton = findViewById(R.id.player_back_button);
        Button externalButton = findViewById(R.id.player_external_button);

        titleText = safe(getIntent().getStringExtra("title"));
        playUrl = safe(getIntent().getStringExtra("url"));
        headersJson = safe(getIntent().getStringExtra("headers"));

        if (titleText.isEmpty()) {
            titleText = "晓鹏壳子";
        }

        titleView.setText(titleText);
        subtitleView.setText(parseHeaders(headersJson).isEmpty() ? "JZVideo 原生播放" : "JZVideo 原生播放 · 已附带请求头");

        backButton.setOnClickListener(v -> returnToMainPage());
        externalButton.setOnClickListener(v -> openExternal());

        startPlayback();
    }

    private void startPlayback() {
        if (TextUtils.isEmpty(playUrl)) {
            Toast.makeText(this, "No playable url", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            Map<String, String> headers = parseHeaders(headersJson);
            if (!setupWithHeaders(headers)) {
                setupSimple();
            }
            playerView.startVideo();
        } catch (Throwable error) {
            Toast.makeText(this, "JZVideo init failed, trying external player", Toast.LENGTH_SHORT).show();
            openExternal();
        }
    }

    private boolean setupWithHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        try {
            Class<?> dataSourceClass = Class.forName("cn.jzvd.JZDataSource");
            Object dataSource = dataSourceClass.getConstructor(String.class, String.class).newInstance(playUrl, titleText);
            try {
                Field headerMapField = dataSourceClass.getField("headerMap");
                headerMapField.set(dataSource, headers);
            } catch (NoSuchFieldException ignored) {
                Method setHeaderMap = dataSourceClass.getMethod("setHeaderMap", HashMap.class);
                setHeaderMap.invoke(dataSource, new HashMap<>(headers));
            }
            Method setUp = playerView.getClass().getMethod("setUp", dataSourceClass, int.class);
            setUp.invoke(playerView, dataSource, Jzvd.SCREEN_NORMAL);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void setupSimple() throws Exception {
        try {
            Method method = playerView.getClass().getMethod("setUp", String.class, String.class);
            method.invoke(playerView, playUrl, titleText);
            return;
        } catch (NoSuchMethodException ignored) {
        }
        Method method = playerView.getClass().getMethod("setUp", String.class, String.class, int.class);
        method.invoke(playerView, playUrl, titleText, Jzvd.SCREEN_NORMAL);
    }

    private void openExternal() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(playUrl), "video/*");
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No external player found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (Jzvd.backPress()) {
            return;
        }
        returnToMainPage();
    }

    private void returnToMainPage() {
        Jzvd.releaseAllVideos();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tryInvokeJzvd("goOnPlayOnResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Jzvd.releaseAllVideos();
    }

    @Override
    protected void onDestroy() {
        Jzvd.releaseAllVideos();
        super.onDestroy();
    }

    private Map<String, String> parseHeaders(String rawJson) {
        Map<String, String> headers = new HashMap<>();
        if (rawJson == null || rawJson.isEmpty()) {
            return headers;
        }
        try {
            JSONObject jsonObject = new JSONObject(rawJson);
            Iterator<String> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = jsonObject.optString(key, "");
                if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                    headers.put(key, value);
                }
            }
        } catch (Exception ignored) {
        }
        return headers;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void tryInvokeJzvd(String methodName) {
        try {
            Method method = Jzvd.class.getMethod(methodName);
            method.invoke(null);
        } catch (Throwable ignored) {
        }
    }
}
