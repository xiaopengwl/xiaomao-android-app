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
    private SwitchMaterial autoPlaySwitch;
    private SwitchMaterial rememberSourceSwitch;
    private SwitchMaterial defaultLibrarySwitch;
    private SwitchMaterial keepSearchSwitch;
    private SwitchMaterial nightModeSwitch;
    private SwitchMaterial followSystemThemeSwitch;
    private MaterialButton clearPlayerMemoryButton;
    private TextView appVersionView;
    private TextView appCoreView;
    private TextView appSupportView;
    private TextView playerMemoryValueView;
    private boolean suppressSwitchCallbacks = false;

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
        autoPlaySwitch = findViewById(R.id.settings_auto_play_switch);
        rememberSourceSwitch = findViewById(R.id.settings_remember_source_switch);
        defaultLibrarySwitch = findViewById(R.id.settings_default_library_switch);
        keepSearchSwitch = findViewById(R.id.settings_keep_search_switch);
        nightModeSwitch = findViewById(R.id.settings_night_mode_switch);
        followSystemThemeSwitch = findViewById(R.id.settings_follow_system_switch);
        clearPlayerMemoryButton = findViewById(R.id.settings_clear_player_memory_button);
        appVersionView = findViewById(R.id.settings_app_version);
        appCoreView = findViewById(R.id.settings_app_core);
        appSupportView = findViewById(R.id.settings_app_support);
        playerMemoryValueView = findViewById(R.id.settings_player_memory_value);

        backButton.setOnClickListener(v -> finish());
        doneButton.setOnClickListener(v -> {
            saveSettings();
            finish();
        });
        clearPlayerMemoryButton.setOnClickListener(v -> {
            SettingsStore.clearPlayerCompatMemory(this);
            refreshPlayerMemorySummary();
            Toast.makeText(this, R.string.settings_player_memory_cleared, Toast.LENGTH_SHORT).show();
        });
        bindEvents();
    }

    private void bindData() {
        suppressSwitchCallbacks = true;
        autoPlaySwitch.setChecked(SettingsStore.autoPlayEnabled(this));
        rememberSourceSwitch.setChecked(SettingsStore.rememberSource(this));
        defaultLibrarySwitch.setChecked(SettingsStore.defaultLibrary(this));
        keepSearchSwitch.setChecked(SettingsStore.keepLastSearch(this));
        nightModeSwitch.setChecked(SettingsStore.nightModeEnabled(this));
        followSystemThemeSwitch.setChecked(SettingsStore.followSystemTheme(this));
        updateThemeSwitchState();
        appVersionView.setText(getString(R.string.settings_about_version, BuildConfig.VERSION_NAME));
        appCoreView.setText(getString(R.string.settings_about_core));
        appSupportView.setText(getString(R.string.settings_about_support));
        refreshPlayerMemorySummary();
        suppressSwitchCallbacks = false;
    }

    private void saveSettings() {
        setResult(Activity.RESULT_OK);
    }

    private void bindEvents() {
        autoPlaySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppressSwitchCallbacks) {
                SettingsStore.setAutoPlayEnabled(this, isChecked);
                setResult(Activity.RESULT_OK);
            }
        });
        rememberSourceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppressSwitchCallbacks) {
                SettingsStore.setRememberSource(this, isChecked);
                setResult(Activity.RESULT_OK);
            }
        });
        defaultLibrarySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppressSwitchCallbacks) {
                SettingsStore.setDefaultLibrary(this, isChecked);
                setResult(Activity.RESULT_OK);
            }
        });
        keepSearchSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppressSwitchCallbacks) {
                SettingsStore.setKeepLastSearch(this, isChecked);
                setResult(Activity.RESULT_OK);
            }
        });
        nightModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchCallbacks) {
                return;
            }
            SettingsStore.setNightModeEnabled(this, isChecked);
            setResult(Activity.RESULT_OK);
            if (!followSystemThemeSwitch.isChecked()) {
                ThemeHelper.apply(this);
                recreate();
            }
        });
        followSystemThemeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSwitchCallbacks) {
                return;
            }
            SettingsStore.setFollowSystemTheme(this, isChecked);
            updateThemeSwitchState();
            setResult(Activity.RESULT_OK);
            ThemeHelper.apply(this);
            recreate();
        });
    }

    private void updateThemeSwitchState() {
        boolean followSystem = followSystemThemeSwitch != null && followSystemThemeSwitch.isChecked();
        if (nightModeSwitch != null) {
            nightModeSwitch.setEnabled(!followSystem);
            nightModeSwitch.setAlpha(followSystem ? 0.45f : 1f);
        }
    }

    private void refreshPlayerMemorySummary() {
        int count = SettingsStore.playerCompatMemoryCount(this);
        playerMemoryValueView.setText(getString(R.string.settings_player_memory_value, count));
        clearPlayerMemoryButton.setEnabled(count > 0);
        clearPlayerMemoryButton.setAlpha(count > 0 ? 1f : 0.5f);
    }
}
