package com.xiaomao.player;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class DetailActivity extends AppCompatActivity {
    private static final int COLLAPSED_CONTENT_LINES = 4;

    private TextView toolbarTitleView;
    private ImageView backdropView;
    private ImageView posterView;
    private TextView titleView;
    private TextView metaView;
    private TextView contentView;
    private MaterialButton contentToggleButton;
    private View loadingContainer;
    private TextView loadingTextView;
    private LinearLayout groupsContainer;
    private MaterialButton playFirstButton;

    private NativeSource source;
    private NativeDrpyEngine engine;
    private String itemUrl = "";
    private String itemTitle = "";
    private String itemPoster = "";
    private String itemRemark = "";
    private String detailContent = "";
    private boolean contentExpanded = false;
    private boolean autoPlayFirstRequested = false;
    private NativeDrpyEngine.MediaDetail currentDetail;

    private static final class EpisodeTarget {
        final NativeDrpyEngine.EpisodeGroup group;
        final int index;

        EpisodeTarget(NativeDrpyEngine.EpisodeGroup group, int index) {
            this.group = group;
            this.index = index;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        bindViews();
        bindInput();
        setupViews();
        loadDetail();
    }

    @Override
    protected void onDestroy() {
        if (engine != null) {
            engine.release();
            engine = null;
        }
        super.onDestroy();
    }

    private void bindViews() {
        toolbarTitleView = findViewById(R.id.detail_toolbar_title);
        backdropView = findViewById(R.id.detail_backdrop);
        posterView = findViewById(R.id.detail_poster);
        titleView = findViewById(R.id.detail_title);
        metaView = findViewById(R.id.detail_meta);
        contentView = findViewById(R.id.detail_content);
        contentToggleButton = findViewById(R.id.detail_intro_toggle);
        loadingContainer = findViewById(R.id.detail_loading_container);
        loadingTextView = findViewById(R.id.detail_loading_text);
        groupsContainer = findViewById(R.id.detail_episode_groups);
        playFirstButton = findViewById(R.id.detail_play_first_button);

        ImageButton backButton = findViewById(R.id.detail_back_button);
        UiEffects.bindPressScale(backButton);
        UiEffects.bindPressScale(playFirstButton);
        backButton.setOnClickListener(v -> finish());
        playFirstButton.setOnClickListener(v -> playFirstEpisode());
        contentToggleButton.setOnClickListener(v -> toggleContentExpanded());
    }

    private void bindInput() {
        Intent intent = getIntent();
        source = new NativeSource(
                intent.getStringExtra("source_title"),
                intent.getStringExtra("source_host"),
                intent.getStringExtra("source_raw")
        );
        itemUrl = safe(intent.getStringExtra("item_url"));
        itemTitle = safe(intent.getStringExtra("item_title"));
        itemPoster = safe(intent.getStringExtra("item_poster"));
        itemRemark = safe(intent.getStringExtra("item_remark"));
        autoPlayFirstRequested = intent.getBooleanExtra("auto_play_first", false);
        engine = new NativeDrpyEngine(this, source);
    }

    private void setupViews() {
        toolbarTitleView.setText(itemTitle.isEmpty() ? getString(R.string.detail_toolbar_title) : itemTitle);
        titleView.setText(itemTitle.isEmpty() ? getString(R.string.detail_loading) : itemTitle);
        metaView.setText(itemRemark.isEmpty() ? getString(R.string.detail_meta_loading) : itemRemark);
        contentView.setText(getString(R.string.detail_content_loading));
        PosterLoader.load(posterView, itemPoster, itemTitle);
        PosterLoader.load(backdropView, itemPoster, itemTitle);
    }

    private void loadDetail() {
        showLoading(getString(R.string.detail_loading));
        engine.loadDetail(itemUrl, itemTitle, itemPoster, (detail, err) -> {
            if (!TextUtils.isEmpty(err) && detail.playGroups.isEmpty()) {
                showLoading(getString(R.string.detail_loading_failed, err));
                return;
            }
            currentDetail = detail;
            renderDetail(detail);
        });
    }

    private void renderDetail(NativeDrpyEngine.MediaDetail detail) {
        loadingContainer.setVisibility(View.GONE);
        toolbarTitleView.setText(detail.title);
        titleView.setText(detail.title);
        ArrayList<NativeDrpyEngine.EpisodeGroup> sortedGroups = sortPlayGroups(detail.playGroups);
        metaView.setText(composeDetailMeta(detail, sortedGroups));
        detailContent = detail.content.isEmpty() ? getString(R.string.detail_no_content) : detail.content;
        contentExpanded = DetailStateStore.isIntroExpanded(this, itemUrl);
        applyDetailContent();
        PosterLoader.load(posterView, detail.poster, detail.title);
        PosterLoader.load(backdropView, detail.poster, detail.title);
        renderEpisodeGroups(sortedGroups);
        EpisodeTarget target = findFirstPlayable(sortedGroups);
        playFirstButton.setEnabled(target != null);
        if (autoPlayFirstRequested && target != null) {
            autoPlayFirstRequested = false;
            playFirstButton.post(this::playFirstEpisode);
        }
    }

    private void applyDetailContent() {
        contentView.setText(detailContent);
        contentView.setMaxLines(contentExpanded ? Integer.MAX_VALUE : COLLAPSED_CONTENT_LINES);
        contentView.setEllipsize(contentExpanded ? null : TextUtils.TruncateAt.END);
        contentToggleButton.setText(contentExpanded
                ? getString(R.string.detail_intro_collapse)
                : getString(R.string.detail_intro_expand));
        contentView.post(() -> {
            boolean needsToggle = contentView.getLineCount() > COLLAPSED_CONTENT_LINES || contentExpanded;
            contentToggleButton.setVisibility(needsToggle ? View.VISIBLE : View.GONE);
        });
    }

    private void toggleContentExpanded() {
        contentExpanded = !contentExpanded;
        DetailStateStore.setIntroExpanded(this, itemUrl, contentExpanded);
        applyDetailContent();
    }

    private void renderEpisodeGroups(ArrayList<NativeDrpyEngine.EpisodeGroup> groups) {
        groupsContainer.removeAllViews();
        if (groups == null || groups.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText(getString(R.string.detail_no_lines));
            emptyView.setTextColor(ContextCompat.getColor(this, R.color.xm_text_secondary));
            emptyView.setTextSize(14f);
            groupsContainer.addView(emptyView);
            return;
        }

        int headerColor = ContextCompat.getColor(this, R.color.xm_text_primary);
        int chipTextColor = ContextCompat.getColor(this, R.color.xm_text_primary);
        int chipBackgroundColor = ContextCompat.getColor(this, R.color.xm_panel_surface);
        int chipStrokeColor = ContextCompat.getColor(this, R.color.xm_stroke_soft);
        int chipRippleColor = ContextCompat.getColor(this, R.color.xm_accent);

        for (NativeDrpyEngine.EpisodeGroup group : groups) {
            TextView header = new TextView(this);
            header.setText(group.name);
            header.setTextColor(headerColor);
            header.setTextSize(15f);
            header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            headerParams.topMargin = dp(12);
            groupsContainer.addView(header, headerParams);

            HorizontalScrollView scrollView = new HorizontalScrollView(this);
            scrollView.setHorizontalScrollBarEnabled(false);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            chipParams.topMargin = dp(8);
            groupsContainer.addView(scrollView, chipParams);

            LinearLayout chipRow = new LinearLayout(this);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);
            scrollView.addView(chipRow, new HorizontalScrollView.LayoutParams(
                    HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                    HorizontalScrollView.LayoutParams.WRAP_CONTENT
            ));

            ArrayList<NativeDrpyEngine.EpisodeItem> items = group.items == null ? new ArrayList<>() : group.items;
            for (int i = 0; i < items.size(); i++) {
                NativeDrpyEngine.EpisodeItem episode = items.get(i);
                Chip chip = new Chip(this);
                chip.setText(episode.name.isEmpty() ? ("鎾斁 " + (i + 1)) : episode.name);
                chip.setCheckable(false);
                chip.setClickable(true);
                chip.setEnsureMinTouchTargetSize(false);
                chip.setMinHeight(dp(38));
                chip.setChipCornerRadius(dp(12));
                chip.setChipStartPadding(dp(12));
                chip.setChipEndPadding(dp(12));
                chip.setTextColor(chipTextColor);
                chip.setChipBackgroundColor(ColorStateList.valueOf(chipBackgroundColor));
                chip.setChipStrokeColor(ColorStateList.valueOf(chipStrokeColor));
                chip.setChipStrokeWidth(dp(1));
                chip.setRippleColor(ColorStateList.valueOf(chipRippleColor));
                UiEffects.bindPressScale(chip);
                final int index = i;
                chip.setOnClickListener(v -> openNativePlayer(group, index));
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                rowParams.rightMargin = dp(10);
                chipRow.addView(chip, rowParams);
            }
        }
    }

    private void playFirstEpisode() {
        EpisodeTarget target = findFirstPlayable(currentDetail);
        if (target == null) {
            toast(getString(R.string.detail_no_episode));
            return;
        }
        openNativePlayer(target.group, target.index);
    }

    private EpisodeTarget findFirstPlayable(NativeDrpyEngine.MediaDetail detail) {
        if (detail == null) {
            return null;
        }
        return findFirstPlayable(detail.playGroups);
    }

    private EpisodeTarget findFirstPlayable(ArrayList<NativeDrpyEngine.EpisodeGroup> groups) {
        if (groups == null) {
            return null;
        }
        for (NativeDrpyEngine.EpisodeGroup group : groups) {
            ArrayList<NativeDrpyEngine.EpisodeItem> items = group.items == null ? new ArrayList<>() : group.items;
            for (int i = 0; i < items.size(); i++) {
                if (!safe(items.get(i).url).isEmpty()) {
                    return new EpisodeTarget(group, i);
                }
            }
        }
        return null;
    }

    private void openNativePlayer(NativeDrpyEngine.EpisodeGroup group, int index) {
        if (group == null || group.items == null || index < 0 || index >= group.items.size()) {
            toast(getString(R.string.detail_invalid_episode));
            return;
        }
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> inputs = new ArrayList<>();
        for (NativeDrpyEngine.EpisodeItem item : group.items) {
            names.add(item.name);
            inputs.add(item.url);
        }
        NativeDrpyEngine.EpisodeItem episode = group.items.get(index);
        Intent intent = new Intent(this, NativePlayerActivity.class);
        intent.putExtra("title", episode.name);
        intent.putExtra("series_title", currentDetail == null ? itemTitle : currentDetail.title);
        intent.putExtra("line", group.name);
        intent.putExtra("input", episode.url);
        intent.putExtra("source_title", source.title);
        intent.putExtra("source_host", source.host);
        intent.putExtra("source_raw", source.raw);
        intent.putExtra("detail_page_url", itemUrl);
        intent.putExtra("detail_poster", currentDetail == null ? itemPoster : currentDetail.poster);
        intent.putExtra("detail_remark", currentDetail == null ? itemRemark : currentDetail.remark);
        intent.putStringArrayListExtra("episode_names", names);
        intent.putStringArrayListExtra("episode_inputs", inputs);
        intent.putExtra("episode_index", index);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void showLoading(String text) {
        loadingContainer.setVisibility(View.VISIBLE);
        loadingTextView.setText(text);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private ArrayList<NativeDrpyEngine.EpisodeGroup> sortPlayGroups(ArrayList<NativeDrpyEngine.EpisodeGroup> groups) {
        ArrayList<NativeDrpyEngine.EpisodeGroup> result = groups == null ? new ArrayList<>() : new ArrayList<>(groups);
        Collections.sort(result, Comparator.comparingInt(this::groupPriority));
        return result;
    }

    private int groupPriority(NativeDrpyEngine.EpisodeGroup group) {
        String name = safe(group == null ? "" : group.name).toLowerCase(Locale.ROOT);
        if (name.contains("default")
                || name.contains("默认")
                || name.contains("在线")
                || name.contains("道长")
                || name.contains("榛樿")
                || name.contains("閬撻暱")
                || name.contains("鍦ㄧ嚎")) {
            return 0;
        }
        if (name.contains("蓝光")
                || name.contains("超清")
                || name.contains("高清")
                || name.contains("极清")
                || name.contains("钃濆厜")
                || name.contains("瓒呮竻")
                || name.contains("楂樻竻")
                || name.contains("鏋侀珮")) {
            return 1;
        }
        if (name.contains("备用") || name.contains("备线") || name.contains("澶囩敤") || name.contains("澶囩嚎")) {
            return 3;
        }
        if (name.contains("解析") || name.contains("瑙ｆ瀽")) {
            return 4;
        }
        return 2;
    }

    private String composeDetailMeta(NativeDrpyEngine.MediaDetail detail, ArrayList<NativeDrpyEngine.EpisodeGroup> groups) {
        String base = safe(detail == null ? "" : detail.remark);
        if (base.isEmpty()) {
            base = safe(itemRemark);
        }
        int groupCount = groups == null ? 0 : groups.size();
        int episodeCount = 0;
        if (groups != null) {
            for (NativeDrpyEngine.EpisodeGroup group : groups) {
                episodeCount = Math.max(episodeCount, group.items == null ? 0 : group.items.size());
            }
        }
        if (base.isEmpty()) {
            if (groupCount > 0 && episodeCount > 0) {
                return "已加载 " + groupCount + " 条播放源 · " + episodeCount + " 集";
            }
            return getString(R.string.detail_line_ready);
        }
        if (groupCount > 0) {
            return base + " · " + groupCount + " 源";
        }
        return base;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
