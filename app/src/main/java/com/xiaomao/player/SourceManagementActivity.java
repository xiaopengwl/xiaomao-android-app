package com.xiaomao.player;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;

public class SourceManagementActivity extends AppCompatActivity {
    private TextView summaryView;
    private final SourceManageAdapter adapter = new SourceManageAdapter();
    private final ActivityResultLauncher<Intent> pageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    setResult(Activity.RESULT_OK);
                    loadSources();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_source_management);
        bindViews();
        loadSources();
    }

    private void bindViews() {
        ImageButton backButton = findViewById(R.id.source_manage_back_button);
        MaterialButton importButton = findViewById(R.id.source_manage_import_button);
        MaterialButton settingsButton = findViewById(R.id.source_manage_settings_button);
        summaryView = findViewById(R.id.source_manage_summary);
        RecyclerView recyclerView = findViewById(R.id.source_manage_recycler);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        adapter.setListener(new SourceManageAdapter.Listener() {
            @Override
            public void onSelect(SourceStore.SourceItem item) {
                SourceStore.setSelectedSourceId(SourceManagementActivity.this, item.id);
                setResult(Activity.RESULT_OK);
                Toast.makeText(SourceManagementActivity.this, "已切换到：" + item.title, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onDelete(SourceStore.SourceItem item) {
                confirmDelete(item);
            }
        });

        backButton.setOnClickListener(v -> finish());
        importButton.setOnClickListener(v -> pageLauncher.launch(new Intent(this, ImportSourceActivity.class)));
        settingsButton.setOnClickListener(v -> pageLauncher.launch(new Intent(this, SettingsActivity.class)));
    }

    private void loadSources() {
        ArrayList<SourceStore.SourceItem> items = SourceStore.loadAll(this);
        String selectedId = SourceStore.getSelectedSourceId(this);
        if (selectedId.isEmpty() && !items.isEmpty()) {
            selectedId = items.get(0).id;
        }
        summaryView.setText("共 " + items.size() + " 个片源，当前选中会在首页和播放页直接生效。");
        adapter.submitList(items, selectedId);
    }

    private void confirmDelete(SourceStore.SourceItem item) {
        new AlertDialog.Builder(this)
                .setTitle("删除自定义片源")
                .setMessage("确认删除“" + item.title + "”吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    SourceStore.deleteCustomSource(this, item.id);
                    setResult(Activity.RESULT_OK);
                    loadSources();
                })
                .show();
    }
}
