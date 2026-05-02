package com.xiaomao.player;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;

public class SearchActivity extends AppCompatActivity {
    public static final String EXTRA_SOURCE_TITLE = "source_title";
    public static final String EXTRA_SOURCE_HOST = "source_host";
    public static final String EXTRA_SOURCE_RAW = "source_raw";
    public static final String EXTRA_SEARCH_KEYWORD = "search_keyword";

    private ImageButton backButton;
    private TextView titleView;
    private TextView subtitleView;
    private TextView sourceTextView;
    private TextView statusTextView;
    private TextInputEditText searchInput;
    private MaterialButton searchButton;
    private View searchHistoryRow;
    private LinearLayout searchHistoryContent;
    private View searchHistoryClearButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private View loadingView;
    private View emptyView;
    private TextView emptyTextView;

    private final MediaGridAdapter adapter = new MediaGridAdapter();
    private final ArrayList<NativeDrpyEngine.MediaItem> searchItems = new ArrayList<>();

    private NativeDrpyEngine engine;
    private String sourceTitle = "";
    private String sourceHost = "";
    private String sourceRaw = "";
    private String activeKeyword = "";
    private int currentPage = 1;
    private int contentVersion = 0;
    private boolean pageLoading = false;
    private boolean reachedEnd = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        bindViews();
        bindSource();
        setupRecycler();
        setupEvents();
        renderHeader();
        renderSearchHistory();
        if (SettingsStore.keepLastSearch(this)) {
            String lastKeyword = SettingsStore.lastSearch(this);
            if (!lastKeyword.isEmpty()) {
                searchInput.setText(lastKeyword);
                searchInput.setSelection(lastKeyword.length());
            }
        }
        String intentKeyword = safe(getIntent().getStringExtra(EXTRA_SEARCH_KEYWORD));
        if (!intentKeyword.isEmpty()) {
            searchInput.setText(intentKeyword);
            searchInput.setSelection(intentKeyword.length());
            performSearch(1);
        } else if (engine == null) {
            showLoading(false, getString(R.string.main_msg_no_source));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderSearchHistory();
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
        backButton = findViewById(R.id.search_back_button);
        titleView = findViewById(R.id.search_header_title);
        subtitleView = findViewById(R.id.search_header_subtitle);
        sourceTextView = findViewById(R.id.search_source_text);
        statusTextView = findViewById(R.id.search_status_text);
        searchInput = findViewById(R.id.search_input);
        searchButton = findViewById(R.id.search_button);
        searchHistoryRow = findViewById(R.id.search_history_row);
        searchHistoryContent = findViewById(R.id.search_history_content);
        searchHistoryClearButton = findViewById(R.id.search_history_clear_button);
        swipeRefreshLayout = findViewById(R.id.search_swipe_refresh);
        recyclerView = findViewById(R.id.search_recycler);
        loadingView = findViewById(R.id.loading_skeleton_container);
        emptyView = findViewById(R.id.empty_container);
        emptyTextView = findViewById(R.id.empty_text);
    }

    private void bindSource() {
        Intent intent = getIntent();
        sourceTitle = safe(intent.getStringExtra(EXTRA_SOURCE_TITLE));
        sourceHost = safe(intent.getStringExtra(EXTRA_SOURCE_HOST));
        sourceRaw = safe(intent.getStringExtra(EXTRA_SOURCE_RAW));
        if (!sourceRaw.isEmpty()) {
            engine = new NativeDrpyEngine(this, new NativeSource(sourceTitle, sourceHost, sourceRaw));
        }
        adapter.setSourceLabel(sourceTitle.isEmpty() ? getString(R.string.nav_library) : sourceTitle);
    }

    private void setupRecycler() {
        recyclerView.setLayoutManager(new GridLayoutManager(this, computeGridSpanCount()));
        recyclerView.setAdapter(adapter);
        adapter.setOnItemClickListener(this::openDetail);
        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.xm_accent));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.xm_surface_elevated));
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (activeKeyword.isEmpty()) {
                swipeRefreshLayout.setRefreshing(false);
                return;
            }
            performSearch(Math.max(1, currentPage), true);
        });
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    maybeLoadMore();
                }
            }
        });
    }

    private void setupEvents() {
        UiEffects.bindPressScale(backButton);
        UiEffects.bindPressScale(searchButton);
        backButton.setOnClickListener(v -> finishPage());
        searchButton.setOnClickListener(v -> performSearch(1));
        if (searchHistoryClearButton != null) {
            UiEffects.bindPressScale(searchHistoryClearButton);
            searchHistoryClearButton.setOnClickListener(v -> {
                SearchHistoryStore.clear(this);
                renderSearchHistory();
                toast("\u5DF2\u6E05\u7A7A\u641C\u7D22\u5386\u53F2");
            });
        }
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enterPressed) {
                performSearch(1);
                return true;
            }
            return false;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                renderSearchHistory();
            }
        });
    }

    private void renderHeader() {
        titleView.setText(getString(R.string.search_page_title));
        String subtitle = sourceTitle.isEmpty()
                ? getString(R.string.search_page_subtitle)
                : getString(R.string.main_msg_source_title, sourceTitle);
        subtitleView.setText(subtitle);
        sourceTextView.setText(sourceTitle.isEmpty()
                ? getString(R.string.main_msg_source_title_loading)
                : getString(R.string.main_msg_source_title, sourceTitle));
        statusTextView.setText(sourceHost.isEmpty()
                ? getString(R.string.search_page_status_idle)
                : getString(R.string.main_msg_source_host, sourceHost));
    }

    private void maybeLoadMore() {
        if (pageLoading || reachedEnd || swipeRefreshLayout.isRefreshing() || activeKeyword.isEmpty() || engine == null) {
            return;
        }
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) {
            return;
        }
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
        int lastVisible = linearLayoutManager.findLastVisibleItemPosition();
        int total = linearLayoutManager.getItemCount();
        if (total > 0 && lastVisible >= total - Math.max(2, computeGridSpanCount())) {
            performSearch(currentPage + 1, true);
        }
    }

    private void performSearch(int page) {
        performSearch(page, false);
    }

    private void performSearch(int page, boolean keepActiveKeyword) {
        if (engine == null) {
            showLoading(false, getString(R.string.main_msg_no_source));
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        String keyword = keepActiveKeyword && !activeKeyword.isEmpty() ? activeKeyword : textOf(searchInput);
        if (keyword.isEmpty()) {
            swipeRefreshLayout.setRefreshing(false);
            toast(getString(R.string.main_msg_search_empty_keyword));
            return;
        }
        if (SettingsStore.keepLastSearch(this)) {
            SettingsStore.setLastSearch(this, keyword);
        }
        final int targetPage = Math.max(1, page);
        if (pageLoading && targetPage > currentPage) {
            return;
        }
        final int token = ++contentVersion;
        final boolean append = targetPage > 1;
        if (!append) {
            reachedEnd = false;
            activeKeyword = keyword;
        }
        pageLoading = true;
        if (!append) {
            showLoading(true, getString(R.string.main_msg_search_loading, keyword));
            SearchHistoryStore.save(this, keyword);
            renderSearchHistory();
        }
        engine.search(activeKeyword, targetPage, (items, err) -> runOnUiThread(() -> {
            if (token != contentVersion || isFinishing() || isDestroyed()) {
                return;
            }
            pageLoading = false;
            swipeRefreshLayout.setRefreshing(false);
            if (!TextUtils.isEmpty(err) && (items == null || items.isEmpty())) {
                showLoading(false, getString(R.string.main_msg_search_failed, err));
                return;
            }
            if (append && (items == null || items.isEmpty())) {
                reachedEnd = true;
                showLoading(false, searchItems.isEmpty() ? getString(R.string.main_msg_search_last_page) : "");
                return;
            }
            if (!append) {
                searchItems.clear();
            }
            if (items != null) {
                searchItems.addAll(items);
            }
            adapter.submitList(new ArrayList<>(searchItems));
            currentPage = targetPage;
            reachedEnd = items == null || items.isEmpty();
            showLoading(false, searchItems.isEmpty() ? getString(R.string.main_msg_search_empty) : "");
            statusTextView.setText(getString(R.string.main_msg_search_title, activeKeyword)
                    + " 路 "
                    + getString(R.string.main_msg_search_status, currentPage));
            if (!append) {
                recyclerView.scrollToPosition(0);
            }
        }));
    }

    private void showLoading(boolean loading, String message) {
        if (loading) {
            loadingView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            return;
        }
        loadingView.setVisibility(View.GONE);
        if (!TextUtils.isEmpty(message) && searchItems.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            emptyTextView.setText(message);
        } else if (searchItems.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            emptyTextView.setText(getString(R.string.main_msg_no_content));
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    private void renderSearchHistory() {
        if (searchHistoryRow == null || searchHistoryContent == null) {
            return;
        }
        ArrayList<String> histories = SearchHistoryStore.list(this);
        searchHistoryContent.removeAllViews();
        if (histories.isEmpty()) {
            searchHistoryRow.setVisibility(View.GONE);
            return;
        }
        searchHistoryRow.setVisibility(View.VISIBLE);
        for (String history : histories) {
            Chip chip = buildChip(history);
            chip.setOnClickListener(v -> {
                searchInput.setText(history);
                searchInput.setSelection(history.length());
                performSearch(1);
            });
            chip.setOnLongClickListener(v -> {
                SearchHistoryStore.remove(this, history);
                renderSearchHistory();
                toast("\u5DF2\u5220\u9664\u641C\u7D22\u8BB0\u5F55");
                return true;
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.rightMargin = dp(8);
            searchHistoryContent.addView(chip, params);
        }
    }

    private Chip buildChip(String text) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(false);
        chip.setClickable(true);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipMinHeight(dp(38));
        chip.setChipCornerRadius(dp(12));
        chip.setChipStartPadding(dp(12));
        chip.setChipEndPadding(dp(12));
        chip.setTextColor(ContextCompat.getColor(this, R.color.xm_text_primary));
        chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.xm_surface_elevated)));
        chip.setChipStrokeWidth(dp(1));
        chip.setChipStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.xm_stroke_soft)));
        UiEffects.bindPressScale(chip);
        return chip;
    }

    private void openDetail(NativeDrpyEngine.MediaItem item) {
        if (item == null) {
            return;
        }
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("source_title", sourceTitle);
        intent.putExtra("source_host", sourceHost);
        intent.putExtra("source_raw", sourceRaw);
        intent.putExtra("item_url", item.url.isEmpty() ? item.vodId : item.url);
        intent.putExtra("item_title", item.title);
        intent.putExtra("item_poster", item.poster);
        intent.putExtra("item_remark", item.remark);
        intent.putExtra("auto_play_first", false);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void finishPage() {
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private int computeGridSpanCount() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float widthDp = metrics.widthPixels / metrics.density;
        return widthDp >= 900 ? 4 : (widthDp >= 600 ? 3 : 2);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String textOf(TextInputEditText editText) {
        return editText == null || editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
