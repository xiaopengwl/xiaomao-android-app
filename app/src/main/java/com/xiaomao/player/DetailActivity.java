package com.xiaomao.player;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;

public class DetailActivity extends AppCompatActivity {
    private TextView toolbarTitleView;
    private ImageView posterView;
    private TextView titleView;
    private TextView metaView;
    private TextView contentView;
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
        posterView = findViewById(R.id.detail_poster);
        titleView = findViewById(R.id.detail_title);
        metaView = findViewById(R.id.detail_meta);
        contentView = findViewById(R.id.detail_content);
        loadingContainer = findViewById(R.id.detail_loading_container);
        loadingTextView = findViewById(R.id.detail_loading_text);
        groupsContainer = findViewById(R.id.detail_episode_groups);
        playFirstButton = findViewById(R.id.detail_play_first_button);

        ImageButton backButton = findViewById(R.id.detail_back_button);
        backButton.setOnClickListener(v -> finish());
        playFirstButton.setOnClickListener(v -> playFirstEpisode());
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
        engine = new NativeDrpyEngine(this, source);
    }

    private void setupViews() {
        toolbarTitleView.setText(itemTitle.isEmpty() ? "Details" : itemTitle);
        titleView.setText(itemTitle.isEmpty() ? "Loading..." : itemTitle);
        metaView.setText(itemRemark.isEmpty() ? "Fetching detail and playable lines" : itemRemark);
        contentView.setText("Description, lines and episodes will show here after parsing.");
        PosterLoader.load(posterView, itemPoster, itemTitle);
    }

    private void loadDetail() {
        showLoading("Loading details...");
        engine.loadDetail(itemUrl, itemTitle, itemPoster, (detail, err) -> {
            if (!TextUtils.isEmpty(err) && detail.playGroups.isEmpty()) {
                showLoading("Detail load failed\n" + err);
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
        metaView.setText(detail.remark.isEmpty() ? "Playable lines loaded" : detail.remark);
        contentView.setText(detail.content.isEmpty() ? "No description was returned by this source." : detail.content);
        PosterLoader.load(posterView, detail.poster, detail.title);
        renderEpisodeGroups(detail.playGroups);
        playFirstButton.setEnabled(findFirstPlayable(detail) != null);
    }

    private void renderEpisodeGroups(ArrayList<NativeDrpyEngine.EpisodeGroup> groups) {
        groupsContainer.removeAllViews();
        if (groups == null || groups.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("No playable lines were returned.");
            emptyView.setTextColor(0xFF95A4B8);
            emptyView.setTextSize(14f);
            groupsContainer.addView(emptyView);
            return;
        }
        for (NativeDrpyEngine.EpisodeGroup group : groups) {
            TextView header = new TextView(this);
            header.setText(group.name);
            header.setTextColor(0xFFFFFFFF);
            header.setTextSize(16f);
            header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            headerParams.topMargin = dp(14);
            groupsContainer.addView(header, headerParams);

            ChipGroup chipGroup = new ChipGroup(this);
            chipGroup.setChipSpacingHorizontal(dp(8));
            chipGroup.setChipSpacingVertical(dp(8));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            chipParams.topMargin = dp(8);
            groupsContainer.addView(chipGroup, chipParams);

            ArrayList<NativeDrpyEngine.EpisodeItem> items = group.items == null ? new ArrayList<>() : group.items;
            for (int i = 0; i < items.size(); i++) {
                NativeDrpyEngine.EpisodeItem episode = items.get(i);
                Chip chip = new Chip(this);
                chip.setText(episode.name.isEmpty() ? ("Play " + (i + 1)) : episode.name);
                chip.setCheckable(false);
                chip.setClickable(true);
                chip.setTextColor(0xFFDCE7F6);
                chip.setChipBackgroundColor(ColorStateList.valueOf(0xFF172230));
                chip.setChipStrokeColor(ColorStateList.valueOf(0xFF304257));
                chip.setChipStrokeWidth(dp(1));
                final int index = i;
                chip.setOnClickListener(v -> openNativePlayer(group, index));
                chipGroup.addView(chip);
            }
        }
    }

    private void playFirstEpisode() {
        EpisodeTarget target = findFirstPlayable(currentDetail);
        if (target == null) {
            toast("No playable episode");
            return;
        }
        openNativePlayer(target.group, target.index);
    }

    private EpisodeTarget findFirstPlayable(NativeDrpyEngine.MediaDetail detail) {
        if (detail == null || detail.playGroups == null) {
            return null;
        }
        for (NativeDrpyEngine.EpisodeGroup group : detail.playGroups) {
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
            toast("Invalid episode");
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
        intent.putStringArrayListExtra("episode_names", names);
        intent.putStringArrayListExtra("episode_inputs", inputs);
        intent.putExtra("episode_index", index);
        startActivity(intent);
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
}
