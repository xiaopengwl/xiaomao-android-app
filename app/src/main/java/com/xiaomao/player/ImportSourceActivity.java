package com.xiaomao.player;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;
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
        ThemeHelper.apply(this);
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
            Toast.makeText(this, getString(R.string.import_msg_empty_rule), Toast.LENGTH_SHORT).show();
            return;
        }
        if (SourceStore.looksLikeRemoteRuleUrl(raw)) {
            setSaveEnabled(false);
            Toast.makeText(this, getString(R.string.import_msg_downloading_rule), Toast.LENGTH_SHORT).show();
            importExecutor.execute(() -> {
                try {
                    String downloadedRaw = SourceStore.downloadRemoteRule(raw);
                    runOnUiThread(() -> persistSource(title, host, downloadedRaw));
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        setSaveEnabled(true);
                        Toast.makeText(
                                this,
                                getString(R.string.import_msg_download_failed, friendlyError(e)),
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
            Toast.makeText(this, getString(R.string.import_msg_invalid_rule), Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, getString(R.string.import_msg_imported, item.title), Toast.LENGTH_SHORT).show();
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void setSaveEnabled(boolean enabled) {
        if (saveButton != null) {
            saveButton.setEnabled(enabled);
        }
    }

    private String friendlyError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return getString(R.string.import_msg_network_error);
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("downloaded content is not a rule file") || message.contains("规则文件")) {
            return getString(R.string.import_msg_not_rule_file);
        }
        return message;
    }
}
