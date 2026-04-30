package com.xiaomao.player;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceManagementActivity extends AppCompatActivity {
    private static final Pattern TITLE_PATTERN = Pattern.compile("title\\s*:\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern HOST_PATTERN = Pattern.compile("host\\s*:\\s*['\"]([^'\"]+)['\"]");
    private static final int TEST_STEP_TOTAL = 6;

    private TextView summaryView;
    private TextView activeSourceView;
    private android.view.View emptyCardView;
    private RecyclerView recyclerView;
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
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_source_management);
        bindViews();
        loadSources();
    }

    private void bindViews() {
        ImageButton backButton = findViewById(R.id.source_manage_back_button);
        MaterialButton importButton = findViewById(R.id.source_manage_import_button);
        MaterialButton emptyImportButton = findViewById(R.id.source_manage_empty_button);
        MaterialButton settingsButton = findViewById(R.id.source_manage_settings_button);
        summaryView = findViewById(R.id.source_manage_summary);
        activeSourceView = findViewById(R.id.source_manage_active_source);
        emptyCardView = findViewById(R.id.source_manage_empty_card);
        recyclerView = findViewById(R.id.source_manage_recycler);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        adapter.setListener(new SourceManageAdapter.Listener() {
            @Override
            public void onSelect(SourceStore.SourceItem item) {
                SourceStore.setSelectedSourceId(SourceManagementActivity.this, item.id);
                Intent data = new Intent();
                data.putExtra("selected_source_id", item.id);
                setResult(Activity.RESULT_OK, data);
                Toast.makeText(
                        SourceManagementActivity.this,
                        getString(R.string.source_manage_switch_success, item.title),
                        Toast.LENGTH_SHORT
                ).show();
                finish();
            }

            @Override
            public void onTest(SourceStore.SourceItem item) {
                new SourceTestRunner(item).start();
            }

            @Override
            public void onDebug(SourceStore.SourceItem item) {
                showSourceDebug(item);
            }

            @Override
            public void onDelete(SourceStore.SourceItem item) {
                confirmDelete(item);
            }
        });

        backButton.setOnClickListener(v -> finish());
        importButton.setOnClickListener(v -> openImportPage());
        emptyImportButton.setOnClickListener(v -> openImportPage());
        settingsButton.setOnClickListener(v -> pageLauncher.launch(new Intent(this, SettingsActivity.class)));
    }

    private void loadSources() {
        ArrayList<SourceStore.SourceItem> items = SourceStore.loadAll(this);
        String selectedId = SourceStore.getSelectedSourceId(this);
        if (selectedId.isEmpty() && !items.isEmpty()) {
            selectedId = items.get(0).id;
        }
        summaryView.setText(getString(R.string.source_manage_summary, items.size()));
        String activeTitle = getString(R.string.source_manage_active_source_empty);
        for (SourceStore.SourceItem item : items) {
            if (TextUtils.equals(item.id, selectedId)) {
                activeTitle = getString(R.string.source_manage_active_source, item.title);
                break;
            }
        }
        activeSourceView.setText(activeTitle);
        adapter.submitList(items, selectedId);
        boolean hasItems = !items.isEmpty();
        recyclerView.setVisibility(hasItems ? android.view.View.VISIBLE : android.view.View.GONE);
        emptyCardView.setVisibility(hasItems ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private void openImportPage() {
        pageLauncher.launch(new Intent(this, ImportSourceActivity.class));
    }

    private void confirmDelete(SourceStore.SourceItem item) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.source_manage_delete_title)
                .setMessage(getString(R.string.source_manage_delete_message, item.title))
                .setNegativeButton(R.string.source_manage_delete_cancel, null)
                .setPositiveButton(R.string.source_manage_delete_confirm, (dialog, which) -> {
                    SourceStore.deleteCustomSource(this, item.id);
                    setResult(Activity.RESULT_OK);
                    loadSources();
                })
                .show();
    }

    private void showSourceDebug(SourceStore.SourceItem item) {
        ReportDialogHolder holder = showReportDialog(
                getString(R.string.source_manage_debug_title, item.title),
                getString(R.string.source_manage_debug_subtitle)
        );
        holder.bodyView.setText(buildDebugReport(item));
    }

    private String buildDebugReport(SourceStore.SourceItem item) {
        StringBuilder report = new StringBuilder();
        String typeLabel = getString(item.custom
                ? R.string.source_manage_type_custom
                : R.string.source_manage_type_builtin);
        String hostText = TextUtils.isEmpty(item.host)
                ? getString(R.string.source_manage_debug_value_unknown)
                : item.host;
        String selectedText = TextUtils.equals(SourceStore.getSelectedSourceId(this), item.id)
                ? getString(R.string.source_manage_debug_yes)
                : getString(R.string.source_manage_debug_no);
        String modeLabel;
        if (TextUtils.isEmpty(item.raw)) {
            modeLabel = getString(R.string.source_manage_debug_mode_empty);
        } else if (SourceStore.looksLikeRemoteRuleUrl(item.raw)) {
            modeLabel = getString(R.string.source_manage_debug_mode_remote);
        } else {
            modeLabel = getString(R.string.source_manage_debug_mode_inline);
        }
        String ruleTitle = firstMatch(item.raw, TITLE_PATTERN);
        String ruleHost = firstMatch(item.raw, HOST_PATTERN);
        appendReportLine(report, getString(R.string.source_manage_debug_source_id, item.id));
        appendReportLine(report, getString(R.string.source_manage_debug_source_type, typeLabel));
        appendReportLine(report, getString(R.string.source_manage_debug_selected, selectedText));
        appendReportLine(report, getString(R.string.source_manage_debug_host, hostText));
        appendReportLine(report, getString(R.string.source_manage_debug_rule_mode, modeLabel));
        appendReportLine(report, getString(
                R.string.source_manage_debug_rule_title,
                TextUtils.isEmpty(ruleTitle) ? getString(R.string.source_manage_debug_value_unknown) : ruleTitle
        ));
        appendReportLine(report, getString(
                R.string.source_manage_debug_rule_host,
                TextUtils.isEmpty(ruleHost) ? getString(R.string.source_manage_debug_value_unknown) : ruleHost
        ));
        appendReportLine(report, getString(R.string.source_manage_debug_rule_length, item.raw == null ? 0 : item.raw.length()));
        report.append('\n');
        report.append(getString(R.string.source_manage_debug_rule_preview)).append('\n');
        report.append(limitPreview(item.raw));
        return report.toString();
    }

    private ReportDialogHolder showReportDialog(String title, String subtitle) {
        android.view.View view = LayoutInflater.from(this).inflate(R.layout.dialog_source_report, null, false);
        TextView subtitleView = view.findViewById(R.id.source_report_subtitle);
        TextView bodyView = view.findViewById(R.id.source_report_body);
        subtitleView.setText(subtitle);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.show();
        return new ReportDialogHolder(dialog, subtitleView, bodyView);
    }

    private static void appendReportLine(StringBuilder report, String line) {
        if (report.length() > 0) {
            report.append('\n');
        }
        report.append(line);
    }

    private static String limitPreview(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        String normalized = raw.replace("\r", "").trim();
        if (normalized.length() <= 900) {
            return normalized;
        }
        return normalized.substring(0, 900) + "\n...";
    }

    private static String firstMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private final class SourceTestRunner {
        private final SourceStore.SourceItem item;
        private final StringBuilder report = new StringBuilder();
        private final TestState state = new TestState();
        private ReportDialogHolder holder;
        private NativeDrpyEngine engine;
        private boolean closed = false;

        SourceTestRunner(SourceStore.SourceItem item) {
            this.item = item;
        }

        void start() {
            holder = showReportDialog(
                    getString(R.string.source_manage_test_title, item.title),
                    getString(R.string.source_manage_test_subtitle)
            );
            holder.dialog.setOnDismissListener(dialog -> {
                closed = true;
                releaseEngine();
            });
            append(getString(R.string.source_manage_test_start));
            append(getString(R.string.source_manage_debug_host,
                    TextUtils.isEmpty(item.host)
                            ? getString(R.string.source_manage_debug_value_unknown)
                            : item.host));
            engine = new NativeDrpyEngine(SourceManagementActivity.this, item.toNativeSource());
            testCategories();
        }

        private void testCategories() {
            if (isClosed()) {
                return;
            }
            engine.loadCategories((categories, err) -> {
                if (isClosed()) {
                    return;
                }
                if (!TextUtils.isEmpty(err)) {
                    fail(R.string.source_manage_test_categories, err);
                    testRecommend();
                    return;
                }
                state.categories = categories == null ? new ArrayList<>() : categories;
                if (state.categories.isEmpty()) {
                    fail(R.string.source_manage_test_categories, getString(R.string.source_manage_test_result_empty));
                    skip(R.string.source_manage_test_category_items, getString(R.string.source_manage_test_result_empty));
                    testRecommend();
                    return;
                }
                pass(R.string.source_manage_test_categories,
                        getString(R.string.source_manage_test_result_count, state.categories.size()));
                testCategoryItems();
            });
        }

        private void testCategoryItems() {
            NativeDrpyEngine.Category firstCategory = state.categories.get(0);
            if (TextUtils.isEmpty(firstCategory.url)) {
                skip(R.string.source_manage_test_category_items, getString(R.string.source_manage_test_result_empty));
                testRecommend();
                return;
            }
            engine.loadCategoryItems(firstCategory.url, 1, (items, err) -> {
                if (isClosed()) {
                    return;
                }
                if (!TextUtils.isEmpty(err)) {
                    fail(R.string.source_manage_test_category_items, err);
                    testRecommend();
                    return;
                }
                ArrayList<NativeDrpyEngine.MediaItem> results = items == null ? new ArrayList<>() : items;
                if (results.isEmpty()) {
                    fail(R.string.source_manage_test_category_items, getString(R.string.source_manage_test_result_empty));
                } else {
                    pass(R.string.source_manage_test_category_items,
                            getString(R.string.source_manage_test_result_count, results.size()));
                }
                testRecommend();
            });
        }

        private void testRecommend() {
            engine.loadRecommend(1, (items, err) -> {
                if (isClosed()) {
                    return;
                }
                if (!TextUtils.isEmpty(err)) {
                    fail(R.string.source_manage_test_recommend, err);
                    state.recommendItems = new ArrayList<>();
                    testSearch();
                    return;
                }
                state.recommendItems = items == null ? new ArrayList<>() : items;
                if (state.recommendItems.isEmpty()) {
                    fail(R.string.source_manage_test_recommend, getString(R.string.source_manage_test_result_empty));
                } else {
                    NativeDrpyEngine.MediaItem first = state.recommendItems.get(0);
                    pass(R.string.source_manage_test_recommend,
                            getString(R.string.source_manage_test_result_count, state.recommendItems.size())
                                    + "，" + getString(R.string.source_manage_test_result_item, first.title));
                }
                testSearch();
            });
        }

        private void testSearch() {
            state.searchKeyword = buildSearchKeyword();
            append("  " + getString(R.string.source_manage_test_result_keyword, state.searchKeyword));
            engine.search(state.searchKeyword, 1, (items, err) -> {
                if (isClosed()) {
                    return;
                }
                if (!TextUtils.isEmpty(err)) {
                    fail(R.string.source_manage_test_search, err);
                    state.searchItems = new ArrayList<>();
                    testDetail();
                    return;
                }
                state.searchItems = items == null ? new ArrayList<>() : items;
                if (state.searchItems.isEmpty()) {
                    fail(R.string.source_manage_test_search, getString(R.string.source_manage_test_result_empty));
                } else {
                    pass(R.string.source_manage_test_search,
                            getString(R.string.source_manage_test_result_count, state.searchItems.size()));
                }
                testDetail();
            });
        }

        private void testDetail() {
            state.targetItem = pickTargetItem();
            if (state.targetItem == null) {
                skip(R.string.source_manage_test_detail, getString(R.string.source_manage_test_result_empty));
                skip(R.string.source_manage_test_lazy, getString(R.string.source_manage_test_result_empty));
                finish();
                return;
            }
            append("  " + getString(R.string.source_manage_test_result_item, state.targetItem.title));
            engine.loadDetail(
                    state.targetItem.url,
                    state.targetItem.title,
                    state.targetItem.poster,
                    (detail, err) -> {
                        if (isClosed()) {
                            return;
                        }
                        if (!TextUtils.isEmpty(err)) {
                            fail(R.string.source_manage_test_detail, err);
                            skip(R.string.source_manage_test_lazy, getString(R.string.source_manage_test_result_empty));
                            finish();
                            return;
                        }
                        state.detail = detail;
                        int groupCount = detail == null || detail.playGroups == null ? 0 : detail.playGroups.size();
                        if (groupCount < 1) {
                            fail(R.string.source_manage_test_detail, getString(R.string.source_manage_test_result_empty));
                            skip(R.string.source_manage_test_lazy, getString(R.string.source_manage_test_result_empty));
                            finish();
                            return;
                        }
                        pass(R.string.source_manage_test_detail,
                                getString(R.string.source_manage_test_result_groups, groupCount));
                        testLazy();
                    }
            );
        }

        private void testLazy() {
            NativeDrpyEngine.EpisodeItem episode = pickFirstEpisode(state.detail);
            if (episode == null || TextUtils.isEmpty(episode.url)) {
                skip(R.string.source_manage_test_lazy, getString(R.string.source_manage_test_result_empty));
                finish();
                return;
            }
            append("  " + getString(R.string.source_manage_test_result_episode, episode.name));
            engine.runLazy(episode.url, (result, err) -> {
                if (isClosed()) {
                    return;
                }
                if (!TextUtils.isEmpty(err)) {
                    fail(R.string.source_manage_test_lazy, err);
                    finish();
                    return;
                }
                String url = result == null ? "" : result.url;
                if (TextUtils.isEmpty(url)) {
                    fail(R.string.source_manage_test_lazy, getString(R.string.source_manage_test_result_empty));
                } else {
                    String detail = getString(R.string.source_manage_test_result_url, summarizeUrl(url));
                    if (result != null && result.parse != 0) {
                        detail += "，parse=" + result.parse;
                    }
                    pass(R.string.source_manage_test_lazy, detail);
                }
                finish();
            });
        }

        private void pass(int stepResId, String detail) {
            state.passed++;
            append(formatStep(R.string.source_manage_test_ok, stepResId, detail));
        }

        private void fail(int stepResId, String error) {
            append(formatStep(
                    R.string.source_manage_test_fail,
                    stepResId,
                    getString(R.string.source_manage_test_result_error, safeError(error))
            ));
        }

        private void skip(int stepResId, String detail) {
            append(formatStep(R.string.source_manage_test_skip, stepResId, detail));
        }

        private String formatStep(int statusResId, int stepResId, String detail) {
            String line = getString(statusResId) + " " + getString(stepResId);
            if (!TextUtils.isEmpty(detail)) {
                line += " - " + detail;
            }
            return line;
        }

        private String safeError(String error) {
            if (TextUtils.isEmpty(error)) {
                return getString(R.string.import_msg_network_error);
            }
            return error;
        }

        private String buildSearchKeyword() {
            if (state.recommendItems != null && !state.recommendItems.isEmpty()) {
                String title = state.recommendItems.get(0).title == null ? "" : state.recommendItems.get(0).title.trim();
                title = title.replace(" ", "");
                if (!title.isEmpty()) {
                    return title.substring(0, Math.min(4, title.length()));
                }
            }
            return "最新";
        }

        private NativeDrpyEngine.MediaItem pickTargetItem() {
            if (state.recommendItems != null && !state.recommendItems.isEmpty()) {
                return state.recommendItems.get(0);
            }
            if (state.searchItems != null && !state.searchItems.isEmpty()) {
                return state.searchItems.get(0);
            }
            return null;
        }

        private NativeDrpyEngine.EpisodeItem pickFirstEpisode(NativeDrpyEngine.MediaDetail detail) {
            if (detail == null || detail.playGroups == null) {
                return null;
            }
            for (NativeDrpyEngine.EpisodeGroup group : detail.playGroups) {
                if (group != null && group.items != null && !group.items.isEmpty()) {
                    return group.items.get(0);
                }
            }
            return null;
        }

        private void append(String line) {
            appendReportLine(report, line);
            holder.bodyView.setText(report.toString());
        }

        private void finish() {
            append(getString(R.string.source_manage_test_result_summary, state.passed, TEST_STEP_TOTAL));
            append(getString(R.string.source_manage_test_finish));
            releaseEngine();
        }

        private boolean isClosed() {
            return closed || isFinishing() || isDestroyed();
        }

        private void releaseEngine() {
            if (engine != null) {
                engine.release();
                engine = null;
            }
        }
    }

    private static String summarizeUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        if (url.length() <= 96) {
            return url;
        }
        return url.substring(0, 96) + "...";
    }

    private static final class TestState {
        int passed = 0;
        ArrayList<NativeDrpyEngine.Category> categories = new ArrayList<>();
        ArrayList<NativeDrpyEngine.MediaItem> recommendItems = new ArrayList<>();
        ArrayList<NativeDrpyEngine.MediaItem> searchItems = new ArrayList<>();
        NativeDrpyEngine.MediaItem targetItem;
        NativeDrpyEngine.MediaDetail detail;
        String searchKeyword = "";
    }

    private static final class ReportDialogHolder {
        final AlertDialog dialog;
        final TextView subtitleView;
        final TextView bodyView;

        ReportDialogHolder(AlertDialog dialog, TextView subtitleView, TextView bodyView) {
            this.dialog = dialog;
            this.subtitleView = subtitleView;
            this.bodyView = bodyView;
        }
    }
}
