package com.xiaomao.player;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PlayerActivity extends AppCompatActivity {
    private VideoView videoView;
    private TextView titleView;
    private String playUrl;
    private String headersJson;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        titleView = findViewById(R.id.player_title);
        videoView = findViewById(R.id.player_video);
        Button externalButton = findViewById(R.id.player_external_button);

        String title = getIntent().getStringExtra("title");
        playUrl = getIntent().getStringExtra("url");
        headersJson = getIntent().getStringExtra("headers");
        titleView.setText(title == null || title.isEmpty() ? "Player" : title);

        externalButton.setOnClickListener(v -> openExternal());
        startPlayback();
    }

    private void startPlayback() {
        if (playUrl == null || playUrl.isEmpty()) {
            Toast.makeText(this, "No playable url", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        videoView.setVideoURI(Uri.parse(playUrl), parseHeaders(headersJson));
        videoView.setOnPreparedListener(mediaPlayer -> {
            mediaPlayer.setOnVideoSizeChangedListener((mp, width, height) -> {
                if (width > 0 && height > 0) {
                    videoView.requestLayout();
                }
            });
            videoView.start();
        });
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Internal player failed, trying external player", Toast.LENGTH_SHORT).show();
            openExternal();
            return true;
        });
        videoView.start();
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
                headers.put(key, jsonObject.optString(key));
            }
        } catch (Exception ignored) {
        }
        return headers;
    }
}
