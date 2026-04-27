package com.xiaomao.player;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private enum BrowseMode {
        HOME,
        CATEGORY,
        SEARCH,
        RANK
    }

    private enum MainTab {
        HOME,
        LIBRARY,
        RANK,
        MINE
    }

    private TextView sectionTitleView;
    private TextView statusTextView;
    private TextView pageTextView;
    private TextView emptyTextView;
    private TextView mineSourceNameView;
    private TextView mineSourceHostView;
    private TextView mineFeatureTextView;
    private TextView mineKernelNameView;
    private TextView mineKernelTipView;
    private View emptyContainer;
    private View loadingIndicator;
    private View pageControlsView;
    private HorizontalScrollView categoryScrollView;
    private MaterialButton searchButton;
    private MaterialButton homeButton;
    private MaterialButton prevButton;
    private MaterialButton nextButton;
    private MaterialButton mineSettingsButton;
    private MaterialButton mineImportButton;
    private MaterialButton mineSourceManageButton;
    private MaterialButton mineKernelSwitchButton;
    private TextInputEditText searchInput;
    private ChipGroup categoryGroup;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView mediaRecyclerView;
    private RecyclerView rankRecyclerView;
    private View mineScrollView;
    private BottomNavigationView bottomNavigationView;

    private final ArrayList<SourceStore.SourceItem> sources = new ArrayList<>();
    private final ArrayList<NativeDrpyEngine.Category> categories = new ArrayList<>();
    private final MediaGridAdapter adapter = new MediaGridAdapter();
    private final RankListAdapter rankAdapter = new RankListAdapter();

    private final ActivityResultLauncher<Intent> pageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    String selectedId = result.getData() == null ? "" : result.getData().getStringExtra("selected_source_id");
                    loadSources(selectedId);
                }
            });

    private NativeDrpyEngine engine;
    private SourceStore.SourceItem currentSource;
    private NativeDrpyEngine.Category activeCategory;
    private BrowseMode browseMode = BrowseMode.HOME;
    private MainTab currentTab = MainTab.HOME;
    private int currentPage = 1;
    private int sourceVersion = 0;
    private int contentVersion = 0;
    private boolean ignoreBottomSelection = false;
    private boolean sourceMigrationRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupRecycler();
        setupEvents();
        if (SettingsStore.keepLastSearch(this)) {
            searchInput.setText(SettingsStore.lastSearch(this));
        }
        applyTabState();
        loadSources();
        scheduleRemoteSourceMigration();
    }

    @Override
    protected void onDestroy() {
        if (engine != null) {
            engine.release();
            engine = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncSourceIfNeeded();
        scheduleRemoteSourceMigration();
    }

    private void bindViews() {
        sectionTitleView = findViewById(R.id.section_title);
        statusTextView = findViewById(R.id.status_text);
        pageTextView = findViewById(R.id.page_text);
        emptyTextView = findViewById(R.id.empty_text);
        mineSourceNameView = findViewById(R.id.mine_source_name);
        mineSourceHostView = findViewById(R.id.mine_source_host);
        mineFeatureTextView = findViewById(R.id.mine_feature_text);
        mineKernelNameView = findViewById(R.id.mine_kernel_name);
        mineKernelTipView = findViewById(R.id.mine_kernel_tip);
        emptyContainer = findViewById(R.id.empty_container);
        loadingIndicator = findViewById(R.id.loading_indicator);
        pageControlsView = findViewById(R.id.page_controls);
        categoryScrollView = findViewById(R.id.category_scroll);
        searchButton = findViewById(R.id.search_button);
        homeButton = findViewById(R.id.home_button);
        prevButton = findViewById(R.id.prev_button);
        nextButton = findViewById(R.id.next_button);
        mineSettingsButton = findViewById(R.id.mine_settings_button);
        mineImportButton = findViewById(R.id.mine_import_button);
        mineSourceManageButton = findViewById(R.id.mine_source_manage_button);
        mineKernelSwitchButton = findViewById(R.id.mine_kernel_switch_button);
        searchInput = findViewById(R.id.search_input);
        categoryGroup = findViewById(R.id.category_group);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        mediaRecyclerView = findViewById(R.id.media_recycler);
        rankRecyclerView = findViewById(R.id.rank_recycler);
        mineScrollView = findViewById(R.id.mine_scroll);
        bottomNavigationView = findViewById(R.id.bottom_nav);
    }

    private void setupRecycler() {
        mediaRecyclerView.setLayoutManager(new GridLayoutManager(this, computeSpanCount()));
        mediaRecyclerView.setAdapter(adapter);
        adapter.setOnItemClickListener(this::openDetail);

        rankRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        rankRecyclerView.setAdapter(rankAdapter);
        rankAdapter.setOnItemClickListener(this::openDetail);

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.xm_accent));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.xm_surface));
    }

    private void setupEvents() {
        searchButton.setOnClickListener(v -> performSearch(1));
        homeButton.setOnClickListener(v -> openHomeTab(true));
        prevButton.setOnClickListener(v -> changePage(-1));
        nextButton.setOnClickListener(v -> changePage(1));
        mineSettingsButton.setOnClickListener(v -> openNativePage(SettingsActivity.class));
        mineImportButton.setOnClickListener(v -> openNativePage(ImportSourceActivity.class));
        mineSourceManageButton.setOnClickListener(v -> openNativePage(SourceManagementActivity.class));
        mineKernelSwitchButton.setOnClickListener(v -> togglePlayerKernel());
        swipeRefreshLayout.setOnRefreshListener(() -> reloadCurrentPage(true));
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
        bottomNavigationView.setOnItemSelectedListener(this::onBottomNavigationSelected);
    }

    private boolean onBottomNavigationSelected(@NonNull MenuItem item) {
        if (ignoreBottomSelection) {
            return true;
        }
        int itemId = item.getItemId();
        if (itemId == R.id.menu_home) {
            openHomeTab(true);
            return true;
        }
        if (itemId == R.id.menu_library) {
            openLibraryTab(true);
            return true;
        }
        if (itemId == R.id.menu_rank) {
            openRankTab(true);
            return true;
        }
        if (itemId == R.id.menu_mine) {
            openMineTab();
            return true;
        }
        return false;
    }

    private void openNativePage(Class<?> cls) {
        pageLauncher.launch(new Intent(this, cls));
    }

    private void loadSources() {
        loadSources("");
    }

    private void loadSources(String preferredSourceId) {
        sources.clear();
        sources.addAll(SourceStore.loadAll(this));
        if (sources.isEmpty()) {
            showLoading(false, getString(R.string.main_msg_no_source));
            return;
        }
        SourceStore.SourceItem selected = findSourceById(sources, preferredSourceId);
        if (selected == null) {
            selected = SourceStore.resolveSelected(this, sources);
        }
        if (selected == null) {
            selected = sources.get(0);
        }
        switchSource(selected);
    }

    private void syncSourceIfNeeded() {
        ArrayList<SourceStore.SourceItem> latest = SourceStore.loadAll(this);
        if (latest.isEmpty()) {
            return;
        }
        SourceStore.SourceItem selected = SourceStore.resolveSelected(this, latest);
        if (selected == null) {
            selected = latest.get(0);
        }
        sources.clear();
        sources.addAll(latest);
        if (currentSource == null || !sameSource(currentSource, selected)) {
            switchSource(selected);
        }
    }

    private boolean sameSource(SourceStore.SourceItem left, SourceStore.SourceItem right) {
        if (left == null || right == null) {
            return false;
        }
        return TextUtils.equals(left.id, right.id)
                && TextUtils.equals(left.title, right.title)
                && TextUtils.equals(left.host, right.host)
                && TextUtils.equals(left.raw, right.raw);
    }

    private void scheduleRemoteSourceMigration() {
        if (sourceMigrationRunning) {
            return;
        }
        sourceMigrationRunning = true;
        SourceStore.migrateRemoteCustomSourcesAsync(getApplicationContext(), changed -> {
            sourceMigrationRunning = false;
            if (!changed || isFinishing() || isDestroyed()) {
                return;
            }
            syncSourceIfNeeded();
        });
    }

    private SourceStore.SourceItem findSourceById(ArrayList<SourceStore.SourceItem> items, String sourceId) {
        if (items == null || items.isEmpty() || TextUtils.isEmpty(sourceId)) {
            return null;
        }
        for (SourceStore.SourceItem item : items) {
            if (TextUtils.equals(item.id, sourceId)) {
                return item;
            }
        }
        return null;
    }

    private void switchSource(SourceStore.SourceItem record) {
        if (record == null) {
            return;
        }
        if (engine != null) {
            engine.release();
        }
        currentSource = record;
        SourceStore.setSelectedSourceId(this, record.id);
        engine = new NativeDrpyEngine(this, record.toNativeSource());
        sourceVersion += 1;
        contentVersion += 1;
        browseMode = BrowseMode.HOME;
        currentTab = MainTab.HOME;
        activeCategory = null;
        currentPage = 1;
        categories.clear();
        adapter.submitList(new ArrayList<>());
        rankAdapter.submitList(new ArrayList<>());
        if (!SettingsStore.keepLastSearch(this)) {
            searchInput.setText("");
        } else {
            searchInput.setText(SettingsStore.lastSearch(this));
        }
        updateMinePanel();
        renderCategories();
        applyTabState();
        loadCategories();
        if (SettingsStore.defaultLibrary(this)) {
            syncBottomSelection(R.id.menu_library);
            openLibraryTab(true);
        } else {
            syncBottomSelection(R.id.menu_home);
            loadHomePage(1);
        }
    }

    private void loadCategories() {
        if (engine == null) {
            return;
        }
        final int token = sourceVersion;
        engine.loadCategories((items, err) -> {
            if (token != sourceVersion) {
                return;
            }
            categories.clear();
            if (items != null) {
                categories.addAll(items);
            }
            renderCategories();
            if (!TextUtils.isEmpty(err)) {
                toast(getString(R.string.main_msg_load_categories_failed));
            }
            if (currentTab == MainTab.LIBRARY && activeCategory == null && !categories.isEmpty()) {
                activeCategory = categories.get(0);
                loadCategoryPage(activeCategory, 1);
            }
        });
    }

    private void openHomeTab(boolean reload) {
        currentTab = MainTab.HOME;
        browseMode = BrowseMode.HOME;
        activeCategory = null;
        currentPage = 1;
        applyTabState();
        syncBottomSelection(R.id.menu_home);
        renderCategories();
        if (reload || adapter.getDataCount() == 0) {
            loadHomePage(1);
        } else {
            setSectionTitle(getString(R.string.main_section_home));
            setStatus(getString(R.string.main_msg_home_cached));
            showLoading(false, "");
        }
    }

    private void openLibraryTab(boolean reload) {
        currentTab = MainTab.LIBRARY;
        applyTabState();
        syncBottomSelection(R.id.menu_library);
        renderCategories();
        if (categories.isEmpty()) {
            setSectionTitle(getString(R.string.main_msg_library_title));
            setStatus(getString(R.string.main_msg_library_loading));
            showLoading(true, getString(R.string.main_msg_library_loading));
            loadCategories();
            return;
        }
        if (activeCategory == null) {
            activeCategory = categories.get(0);
        }
        browseMode = BrowseMode.CATEGORY;
        currentPage = 1;
        if (reload || adapter.getDataCount() == 0) {
            loadCategoryPage(activeCategory, 1);
        } else {
            setSectionTitle(activeCategory.name);
            setStatus(getString(R.string.main_msg_library_cached));
            showLoading(false, "");
        }
    }

    private void openRankTab(boolean reload) {
        currentTab = MainTab.RANK;
        browseMode = BrowseMode.RANK;
        currentPage = 1;
        applyTabState();
        syncBottomSelection(R.id.menu_rank);
        if (reload || rankAdapter.getDataCount() == 0) {
            loadRankPage(1);
        } else {
            setSectionTitle(getString(R.string.main_msg_rank_title));
            setStatus(getString(R.string.main_msg_rank_cached));
            showLoading(false, "");
        }
    }

    private void openMineTab() {
        currentTab = MainTab.MINE;
        applyTabState();
        syncBottomSelection(R.id.menu_mine);
        setSectionTitle(getString(R.string.main_msg_mine_title));
        setStatus(getString(R.string.main_msg_mine_status));
        updateMinePanel();
        showLoading(false, "");
    }

    private void loadHomePage(int page) {
        if (engine == null) {
            return;
        }
        final int targetPage = Math.max(1, page);
        final int token = ++contentVersion;
        final int sourceToken = sourceVersion;
        currentTab = MainTab.HOME;
        browseMode = BrowseMode.HOME;
        activeCategory = null;
        currentPage = targetPage;
        applyTabState();
        syncBottomSelection(R.id.menu_home);
        setSectionTitle(getString(R.string.main_section_home));
        showLoading(true, getString(R.string.main_msg_home_loading));
        engine.loadRecommend(targetPage, (items, err) -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
                return;
            }
            swipeRefreshLayout.setRefreshing(false);
            if (!TextUtils.isEmpty(err) && (items == null || items.isEmpty())) {
                showLoading(false, getString(R.string.main_msg_home_failed, err));
                return;
            }
            if (targetPage > 1 && (items == null || items.isEmpty())) {
                showLoading(false, adapter.getDataCount() == 0 ? getString(R.string.main_msg_home_last_page) : "");
                toast(getString(R.string.main_msg_home_last_page_toast));
                return;
            }
            adapter.submitList(items);
            currentPage = targetPage;
            showLoading(false, items == null || items.isEmpty() ? getString(R.string.main_msg_home_empty) : "");
            setStatus(getString(R.string.main_msg_home_status, currentPage));
            updatePager();
            mediaRecyclerView.scrollToPosition(0);
        });
    }

    private void loadCategoryPage(@NonNull NativeDrpyEngine.Category category, int page) {
        if (engine == null) {
            return;
        }
        final int targetPage = Math.max(1, page);
        final int token = ++contentVersion;
        final int sourceToken = sourceVersion;
        currentTab = MainTab.LIBRARY;
        browseMode = BrowseMode.CATEGORY;
        activeCategory = category;
        currentPage = targetPage;
        applyTabState();
        syncBottomSelection(R.id.menu_library);
        renderCategories();
        setSectionTitle(category.name);
        showLoading(true, getString(R.string.main_msg_category_loading, category.name));
        engine.loadCategoryItems(category.url, targetPage, (items, err) -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
                return;
            }
            swipeRefreshLayout.setRefreshing(false);
            if (!TextUtils.isEmpty(err) && (items == null || items.isEmpty())) {
                showLoading(false, getString(R.string.main_msg_category_failed, err));
                return;
            }
            if (targetPage > 1 && (items == null || items.isEmpty())) {
                showLoading(false, adapter.getDataCount() == 0 ? getString(R.string.main_msg_category_last_page) : "");
                toast(getString(R.string.main_msg_category_last_page_toast));
                return;
            }
            adapter.submitList(items);
            currentPage = targetPage;
            showLoading(false, items == null || items.isEmpty() ? getString(R.string.main_msg_category_empty) : "");
            setStatus(getString(R.string.main_msg_category_status, category.name, currentPage));
            updatePager();
            mediaRecyclerView.scrollToPosition(0);
        });
    }

    private void loadRankPage(int page) {
        if (engine == null) {
            return;
        }
        final int targetPage = Math.max(1, page);
        final int token = ++contentVersion;
        final int sourceToken = sourceVersion;
        currentTab = MainTab.RANK;
        browseMode = BrowseMode.RANK;
        currentPage = targetPage;
        applyTabState();
        syncBottomSelection(R.id.menu_rank);
        setSectionTitle(getString(R.string.main_msg_rank_title));
        showLoading(true, getString(R.string.main_msg_rank_loading));
        engine.loadRecommend(targetPage, (items, err) -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
                return;
            }
            swipeRefreshLayout.setRefreshing(false);
            if (!TextUtils.isEmpty(err) && (items == null || items.isEmpty())) {
                showLoading(false, getString(R.string.main_msg_rank_failed, err));
                return;
            }
            if (targetPage > 1 && (items == null || items.isEmpty())) {
                showLoading(false, rankAdapter.getDataCount() == 0 ? getString(R.string.main_msg_rank_last_page) : "");
                toast(getString(R.string.main_msg_rank_last_page_toast));
                return;
            }
            rankAdapter.submitList(items);
            currentPage = targetPage;
            showLoading(false, items == null || items.isEmpty() ? getString(R.string.main_msg_rank_empty) : "");
            setStatus(getString(R.string.main_msg_rank_status, currentPage));
            updatePager();
            rankRecyclerView.scrollToPosition(0);
        });
    }

    private void performSearch(int page) {
        if (engine == null) {
            return;
        }
        String keyword = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        if (keyword.isEmpty()) {
            toast(getString(R.string.main_msg_search_empty_keyword));
            return;
        }
        if (SettingsStore.keepLastSearch(this)) {
            SettingsStore.setLastSearch(this, keyword);
        }
        final int targetPage = Math.max(1, page);
        final int token = ++contentVersion;
        final int sourceToken = sourceVersion;
        currentTab = MainTab.LIBRARY;
        browseMode = BrowseMode.SEARCH;
        currentPage = targetPage;
        applyTabState();
        syncBottomSelection(R.id.menu_library);
        renderCategories();
        setSectionTitle(getString(R.string.main_msg_search_title, keyword));
        showLoading(true, getString(R.string.main_msg_search_loading, keyword));
        engine.search(keyword, targetPage, (items, err) -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
                return;
            }
            swipeRefreshLayout.setRefreshing(false);
            if (!TextUtils.isEmpty(err) && (items == null || items.isEmpty())) {
                showLoading(false, getString(R.string.main_msg_search_failed, err));
                return;
            }
            if (targetPage > 1 && (items == null || items.isEmpty())) {
                showLoading(false, adapter.getDataCount() == 0 ? getString(R.string.main_msg_search_last_page) : "");
                toast(getString(R.string.main_msg_search_last_page_toast));
                return;
            }
            adapter.submitList(items);
            currentPage = targetPage;
            showLoading(false, items == null || items.isEmpty() ? getString(R.string.main_msg_search_empty) : "");
            setStatus(getString(R.string.main_msg_search_status, currentPage));
            updatePager();
            mediaRecyclerView.scrollToPosition(0);
        });
    }

    private void reloadCurrentPage(boolean userInitiated) {
        if (engine == null) {
            return;
        }
        if (currentTab == MainTab.MINE) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        if (userInitiated && (currentTab == MainTab.HOME || currentTab == MainTab.LIBRARY)) {
            swipeRefreshLayout.setRefreshing(true);
        }
        if (browseMode == BrowseMode.SEARCH) {
            performSearch(currentPage);
        } else if (browseMode == BrowseMode.CATEGORY && activeCategory != null) {
            loadCategoryPage(activeCategory, currentPage);
        } else if (browseMode == BrowseMode.RANK) {
            loadRankPage(currentPage);
        } else {
            loadHomePage(currentPage);
        }
    }

    private void changePage(int delta) {
        if (engine == null || currentTab == MainTab.MINE) {
            return;
        }
        int targetPage = currentPage + delta;
        if (targetPage < 1) {
            return;
        }
        if (browseMode == BrowseMode.SEARCH) {
            performSearch(targetPage);
        } else if (browseMode == BrowseMode.CATEGORY && activeCategory != null) {
            loadCategoryPage(activeCategory, targetPage);
        } else if (browseMode == BrowseMode.RANK) {
            loadRankPage(targetPage);
        } else {
            loadHomePage(targetPage);
        }
    }

    private void renderCategories() {
        categoryGroup.removeAllViews();
        for (NativeDrpyEngine.Category category : categories) {
            Chip chip = buildChip(category.name, activeCategory != null && TextUtils.equals(activeCategory.url, category.url));
            chip.setOnClickListener(v -> {
                activeCategory = category;
                browseMode = BrowseMode.CATEGORY;
                currentTab = MainTab.LIBRARY;
                currentPage = 1;
                renderCategories();
                loadCategoryPage(category, 1);
            });
            categoryGroup.addView(chip);
        }
    }

    private Chip buildChip(String text, boolean checked) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setClickable(true);
        chip.setChipMinHeight(dp(36));
        chip.setEnsureMinTouchTargetSize(false);
        int checkedText = ContextCompat.getColor(this, R.color.xm_accent_dark);
        int defaultText = ContextCompat.getColor(this, R.color.xm_text_primary);
        int checkedBg = ContextCompat.getColor(this, R.color.xm_accent);
        int defaultBg = ContextCompat.getColor(this, R.color.xm_surface_alt);
        int strokeColor = ContextCompat.getColor(this, R.color.xm_stroke_soft);
        chip.setTextColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{checkedText, defaultText}
        ));
        chip.setChipBackgroundColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{checkedBg, defaultBg}
        ));
        chip.setChipStrokeWidth(dp(1));
        chip.setChipStrokeColor(ColorStateList.valueOf(strokeColor));
        return chip;
    }

    private void applyTabState() {
        boolean showMedia = currentTab == MainTab.HOME || currentTab == MainTab.LIBRARY;
        boolean showRank = currentTab == MainTab.RANK;
        boolean showMine = currentTab == MainTab.MINE;

        categoryScrollView.setVisibility(currentTab == MainTab.LIBRARY && !categories.isEmpty() ? View.VISIBLE : View.GONE);
        swipeRefreshLayout.setVisibility(showMedia ? View.VISIBLE : View.GONE);
        rankRecyclerView.setVisibility(showRank ? View.VISIBLE : View.GONE);
        mineScrollView.setVisibility(showMine ? View.VISIBLE : View.GONE);
        pageControlsView.setVisibility(showMine ? View.GONE : View.VISIBLE);
        pageTextView.setVisibility(showMine ? View.GONE : View.VISIBLE);

        if (showMine) {
            emptyContainer.setVisibility(View.GONE);
        }
        updatePager();
        updateMinePanel();
        updateKernelPanel();
        syncKernelPanelLabels();
    }

    private void syncBottomSelection(int itemId) {
        ignoreBottomSelection = true;
        bottomNavigationView.setSelectedItemId(itemId);
        ignoreBottomSelection = false;
    }

    private void updateMinePanel() {
        String title = currentSource == null
                ? getString(R.string.main_msg_source_title_loading)
                : getString(R.string.main_msg_source_title, currentSource.title);
        String host = currentSource == null || currentSource.host.isEmpty()
                ? getString(R.string.main_msg_source_host_unavailable)
                : getString(R.string.main_msg_source_host, currentSource.host);
        mineSourceNameView.setText(title);
        mineSourceHostView.setText(host);
        mineFeatureTextView.setText(getString(R.string.main_mine_source_desc));
    }

    private void updateKernelPanel() {
        mineKernelNameView.setText(getString(R.string.main_mine_kernel_name));
        mineKernelTipView.setText(getString(R.string.main_mine_kernel_tip));
        mineKernelSwitchButton.setText(getString(R.string.main_mine_kernel_switch));
        mineKernelSwitchButton.setEnabled(false);
        mineKernelSwitchButton.setAlpha(0.6f);
    }

    private void togglePlayerKernel() {
        updateKernelPanel();
        toast(getString(R.string.main_msg_kernel_fixed_dk));
    }

    private void syncKernelPanelLabels() {
        mineKernelNameView.setText(getString(R.string.main_mine_kernel_name));
        mineKernelTipView.setText(getString(R.string.main_mine_kernel_tip));
        mineKernelSwitchButton.setText(getString(R.string.main_mine_kernel_switch));
        mineKernelSwitchButton.setEnabled(false);
        mineKernelSwitchButton.setAlpha(0.6f);
    }

    private void openDetail(NativeDrpyEngine.MediaItem item) {
        if (currentSource == null || item == null) {
            return;
        }
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("source_title", currentSource.title);
        intent.putExtra("source_host", currentSource.host);
        intent.putExtra("source_raw", currentSource.raw);
        intent.putExtra("item_url", item.url.isEmpty() ? item.vodId : item.url);
        intent.putExtra("item_title", item.title);
        intent.putExtra("item_poster", item.poster);
        intent.putExtra("item_remark", item.remark);
        startActivity(intent);
    }

    private void setSectionTitle(String title) {
        sectionTitleView.setText(title);
    }

    private void setStatus(String status) {
        statusTextView.setText(status);
    }

    private void showLoading(boolean loading, String message) {
        if (currentTab == MainTab.MINE) {
            emptyContainer.setVisibility(View.GONE);
            loadingIndicator.setVisibility(View.GONE);
            updatePager();
            return;
        }
        if (loading) {
            emptyContainer.setVisibility(View.VISIBLE);
            loadingIndicator.setVisibility(View.VISIBLE);
            emptyTextView.setText(message);
        } else if (!TextUtils.isEmpty(message)) {
            emptyContainer.setVisibility(View.VISIBLE);
            loadingIndicator.setVisibility(View.GONE);
            emptyTextView.setText(message);
        } else {
            emptyContainer.setVisibility(currentContentCount() == 0 ? View.VISIBLE : View.GONE);
            loadingIndicator.setVisibility(View.GONE);
            if (currentContentCount() == 0) {
                emptyTextView.setText(getString(R.string.main_msg_no_content));
            }
        }
        updatePager();
    }

    private int currentContentCount() {
        if (currentTab == MainTab.RANK) {
            return rankAdapter.getDataCount();
        }
        if (currentTab == MainTab.MINE) {
            return 1;
        }
        return adapter.getDataCount();
    }

    private void updatePager() {
        pageTextView.setText(getString(R.string.main_page_label, currentPage));
        boolean enabled = currentSource != null && currentTab != MainTab.MINE;
        pageTextView.setAlpha(enabled ? 1f : 0.72f);
        prevButton.setEnabled(enabled && currentPage > 1);
        nextButton.setEnabled(enabled);
        homeButton.setEnabled(enabled);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int computeSpanCount() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float widthDp = metrics.widthPixels / metrics.density;
        return widthDp >= 840 ? 4 : (widthDp >= 600 ? 3 : 2);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
