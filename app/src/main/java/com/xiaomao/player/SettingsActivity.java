package com.xiaomao.player;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {
    private SwitchMaterial rememberSourceSwitch;
    private SwitchMaterial defaultLibrarySwitch;
    private SwitchMaterial keepSearchSwitch;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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

        backButton.setOnClickListener(v -> finish());
        doneButton.setOnClickListener(v -> {
            saveSettings();
            finish();
        });
    }

    private void bindData() {
        rememberSourceSwitch.setChecked(SettingsStore.rememberSource(this));
        defaultLibrarySwitch.setChecked(SettingsStore.defaultLibrary(this));
        keepSearchSwitch.setChecked(SettingsStore.keepLastSearch(this));
    }

    private void saveSettings() {
        SettingsStore.setRememberSource(this, rememberSourceSwitch.isChecked());
        SettingsStore.setDefaultLibrary(this, defaultLibrarySwitch.isChecked());
        SettingsStore.setKeepLastSearch(this, keepSearchSwitch.isChecked());
        setResult(Activity.RESULT_OK);
    }
}
