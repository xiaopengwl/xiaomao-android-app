package com.xiaomao.player;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImportSourceActivity extends AppCompatActivity {
    private TextInputEditText titleInput;
    private TextInputEditText hostInput;
    private TextInputEditText rawInput;
    private MaterialButton saveButton;
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_source);
        bindViews();
    }

    @Override
    protected void onDestroy() {
        importExecutor.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        ImageButton backButton = findViewById(R.id.import_back_button);
        saveButton = findViewById(R.id.import_save_button);
        titleInput = findViewById(R.id.import_title_input);
        hostInput = findViewById(R.id.import_host_input);
        rawInput = findViewById(R.id.import_raw_input);

        backButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> saveSource());
    }

    private void saveSource() {
        final String title = titleInput.getText() == null ? "" : titleInput.getText().toString().trim();
        final String host = hostInput.getText() == null ? "" : hostInput.getText().toString().trim();
        final String raw = rawInput.getText() == null ? "" : rawInput.getText().toString().trim();
        if (raw.isEmpty()) {
            Toast.makeText(this, "??????????", Toast.LENGTH_SHORT).show();
            return;
        }
        if (looksLikeRemoteRuleUrl(raw)) {
            setSaveEnabled(false);
            Toast.makeText(this, "????????...", Toast.LENGTH_SHORT).show();
            importExecutor.execute(() -> {
                try {
                    String downloadedRaw = downloadRule(raw);
                    runOnUiThread(() -> persistSource(title, host, downloadedRaw));
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        setSaveEnabled(true);
                        Toast.makeText(
                                this,
                                "????????:" + friendlyError(e),
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }
            });
            return;
        }
        persistSource(title, host, raw);
    }

    private void persistSource(String title, String host, String raw) {
        setSaveEnabled(false);
        SourceStore.SourceItem item = SourceStore.saveCustomSource(this, title, host, raw);
        if (item == null) {
            setSaveEnabled(true);
            Toast.makeText(this, "??????,?? var rule", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "?????:" + item.title, Toast.LENGTH_SHORT).show();
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void setSaveEnabled(boolean enabled) {
        if (saveButton != null) {
            saveButton.setEnabled(enabled);
        }
    }

    private static boolean looksLikeRemoteRuleUrl(String raw) {
        return raw.startsWith("http://") || raw.startsWith("https://");
    }

    private static String friendlyError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "????";
        }
        return message;
    }

    private static String downloadRule(String rawUrl) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(rawUrl).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36"
            );
            connection.setRequestProperty("Accept", "*/*");
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (stream == null) {
                throw new IllegalStateException("HTTP " + code);
            }
            String body = readAll(stream).trim();
            if (code < 200 || code >= 400) {
                throw new IllegalStateException("HTTP " + code);
            }
            if (!body.contains("var rule")) {
                throw new IllegalArgumentException("??????????");
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readAll(InputStream inputStream) throws Exception {
        try (InputStream stream = inputStream;
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }
}
