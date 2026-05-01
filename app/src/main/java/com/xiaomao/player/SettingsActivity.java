package com.xiaomao.player;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {
    private SwitchMaterial rememberSourceSwitch;
    private SwitchMaterial defaultLibrarySwitch;
    private SwitchMaterial keepSearchSwitch;
    private SwitchMaterial nightModeSwitch;
    private MaterialButton clearPlayerMemoryButton;
    private TextView appVersionView;
    private TextView appCoreView;
    private TextView appSupportView;
    private TextView playerMemoryValueView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        bindViews();
        bindData();
    }

    private void bindViews() {
        ImageButton backButton = findViewById(R.id.settings_back_button);
        MaterialButton doneButton = findViewById(R.id.settings_done_button);
        rememberSourceSwitch = findViewById(R.id.settings_remember_source_switch);
        defaultLibrarySwitch = findViewById(R.id.settings_default_library_switch);
        keepSearchSwitch = findViewById(R.id.settings_keep_search_switch);
        nightModeSwitch = findViewById(R.id.settings_night_mode_switch);
        clearPlayerMemoryButton = findViewById(R.id.settings_clear_player_memory_button);
        appVersionView = findViewById(R.id.settings_app_version);
        appCoreView = findViewById(R.id.settings_app_core);
        appSupportView = findViewById(R.id.settings_app_support);
        playerMemoryValueView = findViewById(R.id.settings_player_memory_value);

        backButton.setOnClickListener(v -> finish());
        doneButton.setOnClickListener(v -> {
            saveSettings();
            ThemeHelper.apply(this);
            finish();
        });
        clearPlayerMemoryButton.setOnClickListener(v -> {
            SettingsStore.clearPlayerCompatMemory(this);
            refreshPlayerMemorySummary();
            Toast.makeText(this, R.string.settings_player_memory_cleared, Toast.LENGTH_SHORT).show();
        });
    }

    private void bindData() {
        rememberSourceSwitch.setChecked(SettingsStore.rememberSource(this));
        defaultLibrarySwitch.setChecked(SettingsStore.defaultLibrary(this));
        keepSearchSwitch.setChecked(SettingsStore.keepLastSearch(this));
        nightModeSwitch.setChecked(SettingsStore.nightModeEnabled(this));
        appVersionView.setText(getString(R.string.settings_about_version, BuildConfig.VERSION_NAME));
        appCoreView.setText(getString(R.string.settings_about_core));
        appSupportView.setText(getString(R.string.settings_about_support));
        refreshPlayerMemorySummary();
    }

    private void saveSettings() {
        SettingsStore.setRememberSource(this, rememberSourceSwitch.isChecked());
        SettingsStore.setDefaultLibrary(this, defaultLibrarySwitch.isChecked());
        SettingsStore.setKeepLastSearch(this, keepSearchSwitch.isChecked());
        SettingsStore.setNightModeEnabled(this, nightModeSwitch.isChecked());
        setResult(Activity.RESULT_OK);
    }

    private void refreshPlayerMemorySummary() {
        int count = SettingsStore.playerCompatMemoryCount(this);
        playerMemoryValueView.setText(getString(R.string.settings_player_memory_value, count));
        clearPlayerMemoryButton.setEnabled(count > 0);
        clearPlayerMemoryButton.setAlpha(count > 0 ? 1f : 0.5f);
    }
}
