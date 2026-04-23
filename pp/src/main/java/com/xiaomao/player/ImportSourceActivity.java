package com.xiaomao.player;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class ImportSourceActivity extends AppCompatActivity {
    private TextInputEditText titleInput;
    private TextInputEditText hostInput;
    private TextInputEditText rawInput;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_source);
        bindViews();
    }

    private void bindViews() {
        ImageButton backButton = findViewById(R.id.import_back_button);
        MaterialButton saveButton = findViewById(R.id.import_save_button);
        titleInput = findViewById(R.id.import_title_input);
        hostInput = findViewById(R.id.import_host_input);
        rawInput = findViewById(R.id.import_raw_input);

        backButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> saveSource());
    }

    private void saveSource() {
        String title = titleInput.getText() == null ? "" : titleInput.getText().toString().trim();
        String host = hostInput.getText() == null ? "" : hostInput.getText().toString().trim();
        String raw = rawInput.getText() == null ? "" : rawInput.getText().toString().trim();
        SourceStore.SourceItem item = SourceStore.saveCustomSource(this, title, host, raw);
        if (item == null) {
            Toast.makeText(this, "请先粘贴片源规则内容", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "已导入片源：" + item.title, Toast.LENGTH_SHORT).show();
        setResult(Activity.RESULT_OK);
        finish();
    }
}
