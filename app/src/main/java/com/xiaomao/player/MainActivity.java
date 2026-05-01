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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

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

    private static final String[] RANK_FILTERS = new String[]{
            "总榜", "剧集", "电影", "综艺", "动漫", "飙升", "院线"
    };

    private SwipeRefreshLayout swipeRefreshLayout;
    private BottomNavigationView bottomNavigationView;
    private View searchPanel;
    private NestedScrollView homeScrollView;
    private View libraryContainer;
    private View rankContainer;
    private NestedScrollView mineScrollView;
    private View loadingSkeletonContainer;
    private View emptyContainer;
    private View pageControlsView;
    private RecyclerView mediaRecyclerView;
    private RecyclerView rankRecyclerView;
    private RecyclerView librarySidebarRecyclerView;
    private RecyclerView homeContinueRecyclerView;
    private RecyclerView homeMovieRecyclerView;
    private ChipGroup categoryGroup;
    private ChipGroup rankFilterGroup;
    private TextInputEditText searchInput;
    private TextView emptyTextView;
    private TextView browseStatusView;
    private TextView pageTextView;
    private TextView headerTitleView;
    private TextView headerSubtitleView;
    private TextView featuredSourceView;
    private TextView featuredTitleView;
    private TextView featuredRemarkView;
    private TextView librarySourceTextView;
    private TextView mineSourceNameView;
    private TextView mineSourceHostView;
    private TextView mineNicknameView;
    private TextView mineSignatureView;
    private TextView mineKernelNameView;
    private TextView mineKernelTipView;
    private TextInputEditText mineQqInput;
    private ImageView mineAvatarView;
    private MaterialButton searchButton;
    private MaterialButton featuredActionButton;
    private MaterialButton homeButton;
    private MaterialButton prevButton;
    private MaterialButton nextButton;
    private MaterialButton mineSettingsButton;
    private MaterialButton mineImportButton;
    private MaterialButton mineSourceManageButton;
    private MaterialButton mineQueryButton;
    private MaterialButton mineClearProfileButton;
    private SwitchMaterial mineThemeSwitch;
    private SwitchMaterial mineAutoPlaySwitch;
    private SwitchMaterial mineRememberSourceSwitch;
    private SwitchMaterial mineKeepSearchSwitch;
    private SwitchMaterial mineDefaultLibrarySwitch;
    private View headerSearchAction;
    private View headerMoreAction;
    private View homeActionCategory;
    private View homeActionFeatured;
    private View homeActionShort;
    private View homeActionSchedule;
    private View homeActionLive;

    private final ArrayList<SourceStore.SourceItem> sources = new ArrayList<>();
    private final ArrayList<NativeDrpyEngine.Category> categories = new ArrayList<>();
    private final ArrayList<NativeDrpyEngine.MediaItem> homeItems = new ArrayList<>();
    private final MediaGridAdapter gridAdapter = new MediaGridAdapter();
    private final RankListAdapter rankAdapter = new RankListAdapter();
    private final MediaRailAdapter continueAdapter = new MediaRailAdapter();
    private final MediaRailAdapter movieAdapter = new MediaRailAdapter();
    private final CategorySidebarAdapter sidebarAdapter = new CategorySidebarAdapter();

    private final ActivityResultLauncher<Intent> pageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    String selectedId = result.getData() == null ? "" : result.getData().getStringExtra("selected_source_id");
                    loadSources(selectedId);
                } else {
                    syncSourceIfNeeded();
                    refreshMineSettings();
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
    private int selectedRankFilterIndex = 0;
    private boolean ignoreBottomSelection = false;
    private boolean sourceMigrationRunning = false;
    private boolean suppressMineSwitchCallbacks = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupRecycler();
        setupEvents();
        bindQuickActions();
        if (SettingsStore.keepLastSearch(this)) {
            searchInput.setText(SettingsStore.lastSearch(this));
        }
        renderRankFilters();
        refreshMineSettings();
        refreshMineProfile();
        applyTabState();
        loadSources();
        scheduleRemoteSourceMigration();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncSourceIfNeeded();
        refreshMineSettings();
        refreshMineProfile();
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

    private void bindViews() {
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        bottomNavigationView = findViewById(R.id.bottom_nav);
        searchPanel = findViewById(R.id.search_panel);
        homeScrollView = findViewById(R.id.home_scroll);
        libraryContainer = findViewById(R.id.library_container);
        rankContainer = findViewById(R.id.rank_container);
        mineScrollView = findViewById(R.id.mine_scroll);
        loadingSkeletonContainer = findViewById(R.id.loading_skeleton_container);
        emptyContainer = findViewById(R.id.empty_container);
        pageControlsView = findViewById(R.id.page_controls);
        mediaRecyclerView = findViewById(R.id.media_recycler);
        rankRecyclerView = findViewById(R.id.rank_recycler);
        librarySidebarRecyclerView = findViewById(R.id.library_sidebar_recycler);
        homeContinueRecyclerView = findViewById(R.id.home_continue_recycler);
        homeMovieRecyclerView = findViewById(R.id.home_movie_recycler);
        categoryGroup = findViewById(R.id.category_group);
        rankFilterGroup = findViewById(R.id.rank_filter_group);
        searchInput = findViewById(R.id.search_input);
        emptyTextView = findViewById(R.id.empty_text);
        browseStatusView = findViewById(R.id.browse_status_text);
        pageTextView = findViewById(R.id.page_text);
        headerTitleView = findViewById(R.id.header_title);
        headerSubtitleView = findViewById(R.id.header_subtitle);
        featuredSourceView = findViewById(R.id.featured_source_text);
        featuredTitleView = findViewById(R.id.featured_title);
        featuredRemarkView = findViewById(R.id.featured_remark);
        librarySourceTextView = findViewById(R.id.library_source_text);
        mineSourceNameView = findViewById(R.id.mine_source_name);
        mineSourceHostView = findViewById(R.id.mine_source_host);
        mineNicknameView = findViewById(R.id.mine_nickname);
        mineSignatureView = findViewById(R.id.mine_signature);
        mineKernelNameView = findViewById(R.id.mine_kernel_name);
        mineKernelTipView = findViewById(R.id.mine_kernel_tip);
        mineQqInput = findViewById(R.id.mine_qq_input);
        mineAvatarView = findViewById(R.id.mine_avatar);
        searchButton = findViewById(R.id.search_button);
        featuredActionButton = findViewById(R.id.featured_action_button);
        homeButton = findViewById(R.id.home_button);
        prevButton = findViewById(R.id.prev_button);
        nextButton = findViewById(R.id.next_button);
        mineSettingsButton = findViewById(R.id.mine_settings_button);
        mineImportButton = findViewById(R.id.mine_import_button);
        mineSourceManageButton = findViewById(R.id.mine_source_manage_button);
        mineQueryButton = findViewById(R.id.mine_query_button);
        mineClearProfileButton = findViewById(R.id.mine_clear_profile_button);
        mineThemeSwitch = findViewById(R.id.mine_theme_switch);
        mineAutoPlaySwitch = findViewById(R.id.mine_auto_play_switch);
        mineRememberSourceSwitch = findViewById(R.id.mine_remember_source_switch);
        mineKeepSearchSwitch = findViewById(R.id.mine_keep_search_switch);
        mineDefaultLibrarySwitch = findViewById(R.id.mine_default_library_switch);
        headerSearchAction = findViewById(R.id.header_search_action);
        headerMoreAction = findViewById(R.id.header_more_action);
        homeActionCategory = findViewById(R.id.home_action_category);
        homeActionFeatured = findViewById(R.id.home_action_featured);
        homeActionShort = findViewById(R.id.home_action_short);
        homeActionSchedule = findViewById(R.id.home_action_schedule);
        homeActionLive = findViewById(R.id.home_action_live);
    }

    private void setupRecycler() {
        mediaRecyclerView.setLayoutManager(new GridLayoutManager(this, computeGridSpanCount()));
        mediaRecyclerView.setAdapter(gridAdapter);
        gridAdapter.setOnItemClickListener(this::openDetail);

        librarySidebarRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        librarySidebarRecyclerView.setAdapter(sidebarAdapter);
        sidebarAdapter.setOnItemClickListener(category -> {
            activeCategory = category;
            browseMode = BrowseMode.CATEGORY;
            currentTab = MainTab.LIBRARY;
            renderCategories();
            loadCategoryPage(category, 1);
        });

        rankRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        rankRecyclerView.setAdapter(rankAdapter);
        rankAdapter.setOnItemClickListener(this::openDetail);

        homeContinueRecyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        homeContinueRecyclerView.setAdapter(continueAdapter);
        continueAdapter.setOnItemClickListener(this::openDetail);

        homeMovieRecyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        homeMovieRecyclerView.setAdapter(movieAdapter);
        movieAdapter.setOnItemClickListener(this::openDetail);

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.xm_accent));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.xm_surface));
    }

    private void setupEvents() {
        searchButton.setOnClickListener(v -> performSearch(1));
        homeButton.setOnClickListener(v -> jumpToFirstPage());
        prevButton.setOnClickListener(v -> changePage(-1));
        nextButton.setOnClickListener(v -> changePage(1));
        featuredActionButton.setOnClickListener(v -> {
            NativeDrpyEngine.MediaItem item = resolveFeaturedItem();
            if (item != null) {
                openDetail(item);
            }
        });
        mineSettingsButton.setOnClickListener(v -> openNativePage(SettingsActivity.class));
        mineImportButton.setOnClickListener(v -> openNativePage(ImportSourceActivity.class));
        mineSourceManageButton.setOnClickListener(v -> openNativePage(SourceManagementActivity.class));
        mineQueryButton.setOnClickListener(v -> queryQqProfile());
        mineClearProfileButton.setOnClickListener(v -> clearQqProfile());
        headerSearchAction.setOnClickListener(v -> focusSearchField());
        headerMoreAction.setOnClickListener(v -> openNativePage(SettingsActivity.class));
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

        mineThemeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressMineSwitchCallbacks) {
                return;
            }
            SettingsStore.setNightModeEnabled(this, isChecked);
            ThemeHelper.apply(this);
            recreate();
        });
        mineAutoPlaySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppressMineSwitchCallbacks) {
                SettingsStore.setAutoPlayEnabled(this, isChecked);
            }
        });
        mineRememberSourceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppressMineSwitchCallbacks) {
                SettingsStore.setRememberSource(this, isChecked);
            }
        });
        mineKeepSearchSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressMineSwitchCallbacks) {
                return;
            }
            SettingsStore.setKeepLastSearch(this, isChecked);
            if (!isChecked) {
                searchInput.setText("");
            }
        });
        mineDefaultLibrarySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppressMineSwitchCallbacks) {
                SettingsStore.setDefaultLibrary(this, isChecked);
            }
        });
    }

    private void bindQuickActions() {
        homeActionCategory.setOnClickListener(v -> openLibraryTab(true));
        homeActionFeatured.setOnClickListener(v -> openHomeTab(true));
        homeActionShort.setOnClickListener(v -> {
            searchInput.setText("短剧");
            performSearch(1);
        });
        homeActionSchedule.setOnClickListener(v -> openRankTab(true));
        homeActionLive.setOnClickListener(v -> openNativePage(SourceManagementActivity.class));
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

    private void focusSearchField() {
        if (currentTab == MainTab.MINE) {
            openLibraryTab(false);
        }
        searchInput.requestFocus();
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
            return;
        }
        currentSource = selected;
        syncSourceLabels();
        refreshMineProfile();
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
        currentPage = 1;
        browseMode = BrowseMode.HOME;
        currentTab = MainTab.HOME;
        activeCategory = null;
        selectedRankFilterIndex = 0;
        categories.clear();
        homeItems.clear();
        gridAdapter.setSourceLabel(record.title);
        continueAdapter.setSourceLabel(record.title);
        movieAdapter.setSourceLabel(record.title);
        gridAdapter.submitList(new ArrayList<>());
        continueAdapter.submitList(new ArrayList<>());
        movieAdapter.submitList(new ArrayList<>());
        rankAdapter.submitList(new ArrayList<>());
        renderCategories();
        renderRankFilters();
        syncSourceLabels();
        refreshMineProfile();
        if (!SettingsStore.keepLastSearch(this)) {
            searchInput.setText("");
        } else {
            searchInput.setText(SettingsStore.lastSearch(this));
        }
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
            if (activeCategory == null && !categories.isEmpty()) {
                activeCategory = categories.get(0);
            }
            renderCategories();
            if (!TextUtils.isEmpty(err)) {
                toast(getString(R.string.main_msg_load_categories_failed));
            }
            if (currentTab == MainTab.LIBRARY && browseMode != BrowseMode.SEARCH && activeCategory != null && gridAdapter.getDataCount() == 0) {
                loadCategoryPage(activeCategory, 1);
            }
        });
    }

    private void openHomeTab(boolean reload) {
        currentTab = MainTab.HOME;
        browseMode = BrowseMode.HOME;
        currentPage = 1;
        applyTabState();
        syncBottomSelection(R.id.menu_home);
        if (reload || homeItems.isEmpty()) {
            loadHomePage(1);
        } else {
            showLoading(false, homeItems.isEmpty() ? getString(R.string.main_msg_home_empty) : "");
            setBrowseStatus(getString(R.string.main_msg_home_status, currentPage));
            syncFeaturedContent();
        }
    }

    private void openLibraryTab(boolean reload) {
        currentTab = MainTab.LIBRARY;
        if (browseMode == BrowseMode.HOME || browseMode == BrowseMode.RANK) {
            browseMode = BrowseMode.CATEGORY;
        }
        if (browseMode != BrowseMode.SEARCH && activeCategory == null && !categories.isEmpty()) {
            activeCategory = categories.get(0);
        }
        currentPage = 1;
        applyTabState();
        syncBottomSelection(R.id.menu_library);
        renderCategories();
        if (browseMode == BrowseMode.SEARCH) {
            if (reload) {
                performSearch(1);
            } else {
                showLoading(false, gridAdapter.getDataCount() == 0 ? getString(R.string.main_msg_search_empty) : "");
            }
            return;
        }
        if (categories.isEmpty()) {
            showLoading(true, getString(R.string.main_msg_library_loading));
            loadCategories();
            return;
        }
        if (reload || gridAdapter.getDataCount() == 0) {
            loadCategoryPage(activeCategory, 1);
        } else {
            showLoading(false, gridAdapter.getDataCount() == 0 ? getString(R.string.main_msg_category_empty) : "");
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
            showLoading(false, rankAdapter.getDataCount() == 0 ? getString(R.string.main_msg_rank_empty) : "");
        }
    }

    private void openMineTab() {
        currentTab = MainTab.MINE;
        applyTabState();
        syncBottomSelection(R.id.menu_mine);
        refreshMineSettings();
        refreshMineProfile();
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
        currentPage = targetPage;
        applyTabState();
        syncBottomSelection(R.id.menu_home);
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
                showLoading(false, homeItems.isEmpty() ? getString(R.string.main_msg_home_last_page) : "");
                toast(getString(R.string.main_msg_home_last_page_toast));
                return;
            }
            homeItems.clear();
            if (items != null) {
                homeItems.addAll(items);
            }
            continueAdapter.submitList(sliceItems(homeItems, 0, 8));
            movieAdapter.submitList(sliceItems(homeItems, homeItems.size() > 8 ? 8 : 0, 10));
            currentPage = targetPage;
            showLoading(false, homeItems.isEmpty() ? getString(R.string.main_msg_home_empty) : "");
            setBrowseStatus(getString(R.string.main_msg_home_status, currentPage));
            updatePager();
            syncFeaturedContent();
            homeScrollView.scrollTo(0, 0);
        });
    }

    private void loadCategoryPage(NativeDrpyEngine.Category category, int page) {
        if (engine == null || category == null) {
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
                showLoading(false, gridAdapter.getDataCount() == 0 ? getString(R.string.main_msg_category_last_page) : "");
                toast(getString(R.string.main_msg_category_last_page_toast));
                return;
            }
            gridAdapter.submitList(items);
            currentPage = targetPage;
            showLoading(false, gridAdapter.getDataCount() == 0 ? getString(R.string.main_msg_category_empty) : "");
            updatePager();
            syncFeaturedContent();
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
            showLoading(false, rankAdapter.getDataCount() == 0 ? getString(R.string.main_msg_rank_empty) : "");
            updatePager();
            syncFeaturedContent();
            rankRecyclerView.scrollToPosition(0);
        });
    }

    private void performSearch(int page) {
        if (engine == null) {
            return;
        }
        String keyword = textOf(searchInput);
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
                showLoading(false, gridAdapter.getDataCount() == 0 ? getString(R.string.main_msg_search_last_page) : "");
                toast(getString(R.string.main_msg_search_last_page_toast));
                return;
            }
            gridAdapter.submitList(items);
            currentPage = targetPage;
            showLoading(false, gridAdapter.getDataCount() == 0 ? getString(R.string.main_msg_search_empty) : "");
            updatePager();
            syncFeaturedContent();
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
        if (userInitiated && (currentTab == MainTab.HOME || currentTab == MainTab.LIBRARY || currentTab == MainTab.RANK)) {
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

    private void jumpToFirstPage() {
        if (browseMode == BrowseMode.SEARCH) {
            performSearch(1);
        } else if (browseMode == BrowseMode.CATEGORY && activeCategory != null) {
            loadCategoryPage(activeCategory, 1);
        } else if (browseMode == BrowseMode.RANK) {
            loadRankPage(1);
        } else {
            loadHomePage(1);
        }
    }

    private void renderCategories() {
        categoryGroup.removeAllViews();
        for (NativeDrpyEngine.Category category : categories) {
            boolean checked = activeCategory != null && TextUtils.equals(activeCategory.url, category.url) && browseMode != BrowseMode.SEARCH;
            Chip chip = buildChip(category.name, checked);
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
        sidebarAdapter.submitList(categories, browseMode == BrowseMode.SEARCH || activeCategory == null ? "" : activeCategory.url);
    }

    private void renderRankFilters() {
        rankFilterGroup.removeAllViews();
        for (int i = 0; i < RANK_FILTERS.length; i++) {
            final int index = i;
            Chip chip = buildChip(RANK_FILTERS[i], selectedRankFilterIndex == i);
            chip.setOnClickListener(v -> {
                selectedRankFilterIndex = index;
                renderRankFilters();
                if (currentTab == MainTab.RANK && rankAdapter.getDataCount() == 0) {
                    loadRankPage(1);
                }
            });
            rankFilterGroup.addView(chip);
        }
    }

    private Chip buildChip(String text, boolean checked) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(true);
        chip.setChecked(checked);
        chip.setClickable(true);
        chip.setChipMinHeight(dp(38));
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipCornerRadius(dp(10));
        chip.setChipStartPadding(dp(12));
        chip.setChipEndPadding(dp(12));
        int checkedText = ContextCompat.getColor(this, R.color.xm_accent_dark);
        int defaultText = ContextCompat.getColor(this, R.color.xm_text_primary);
        int checkedBg = ContextCompat.getColor(this, R.color.xm_accent);
        int defaultBg = ContextCompat.getColor(this, R.color.xm_surface);
        int checkedStroke = ContextCompat.getColor(this, R.color.xm_accent);
        int defaultStroke = ContextCompat.getColor(this, R.color.xm_stroke);
        chip.setTextColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{checkedText, defaultText}
        ));
        chip.setChipBackgroundColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{checkedBg, defaultBg}
        ));
        chip.setChipStrokeWidth(dp(1));
        chip.setChipStrokeColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{checkedStroke, defaultStroke}
        ));
        return chip;
    }

    private void applyTabState() {
        boolean showHome = currentTab == MainTab.HOME;
        boolean showLibrary = currentTab == MainTab.LIBRARY;
        boolean showRank = currentTab == MainTab.RANK;
        boolean showMine = currentTab == MainTab.MINE;

        swipeRefreshLayout.setVisibility(showMine ? View.GONE : View.VISIBLE);
        mineScrollView.setVisibility(showMine ? View.VISIBLE : View.GONE);
        homeScrollView.setVisibility(showHome ? View.VISIBLE : View.GONE);
        libraryContainer.setVisibility(showLibrary ? View.VISIBLE : View.GONE);
        rankContainer.setVisibility(showRank ? View.VISIBLE : View.GONE);
        pageControlsView.setVisibility(showMine ? View.GONE : View.VISIBLE);
        searchPanel.setVisibility(showMine ? View.GONE : View.VISIBLE);

        if (showMine) {
            loadingSkeletonContainer.setVisibility(View.GONE);
            emptyContainer.setVisibility(View.GONE);
        }
        updateHeaderContent();
        updatePager();
        syncFeaturedContent();
        syncSourceLabels();
    }

    private void updateHeaderContent() {
        if (headerTitleView == null || headerSubtitleView == null) {
            return;
        }
        if (currentTab == MainTab.MINE) {
            headerTitleView.setText(getString(R.string.nav_mine));
            headerSubtitleView.setText(getString(R.string.main_header_subtitle_mine));
            return;
        }
        if (currentTab == MainTab.RANK) {
            headerTitleView.setText(getString(R.string.nav_rank));
            headerSubtitleView.setText(getString(R.string.main_header_subtitle_rank));
            return;
        }
        if (currentTab == MainTab.LIBRARY) {
            headerTitleView.setText(getString(R.string.nav_library));
            if (browseMode == BrowseMode.SEARCH) {
                String keyword = textOf(searchInput);
                headerSubtitleView.setText(keyword.isEmpty()
                        ? getString(R.string.main_header_subtitle_library)
                        : getString(R.string.main_msg_search_title, keyword));
            } else {
                headerSubtitleView.setText(getString(R.string.main_header_subtitle_library));
            }
            return;
        }
        headerTitleView.setText(getString(R.string.app_name));
        headerSubtitleView.setText(getString(R.string.main_header_subtitle));
    }

    private void syncBottomSelection(int itemId) {
        ignoreBottomSelection = true;
        bottomNavigationView.setSelectedItemId(itemId);
        ignoreBottomSelection = false;
    }

    private void showLoading(boolean loading, String message) {
        if (currentTab == MainTab.MINE) {
            loadingSkeletonContainer.setVisibility(View.GONE);
            emptyContainer.setVisibility(View.GONE);
            return;
        }
        if (loading) {
            loadingSkeletonContainer.setVisibility(View.VISIBLE);
            emptyContainer.setVisibility(View.GONE);
            updatePager();
            return;
        }
        loadingSkeletonContainer.setVisibility(View.GONE);
        int count = currentContentCount();
        if (!TextUtils.isEmpty(message) && count == 0) {
            emptyContainer.setVisibility(View.VISIBLE);
            emptyTextView.setText(message);
        } else if (count == 0) {
            emptyContainer.setVisibility(View.VISIBLE);
            emptyTextView.setText(getString(R.string.main_msg_no_content));
        } else {
            emptyContainer.setVisibility(View.GONE);
        }
        updatePager();
    }

    private int currentContentCount() {
        if (currentTab == MainTab.RANK) {
            return rankAdapter.getDataCount();
        }
        if (currentTab == MainTab.LIBRARY) {
            return gridAdapter.getDataCount();
        }
        if (currentTab == MainTab.HOME) {
            return homeItems.size();
        }
        return 1;
    }

    private void updatePager() {
        pageTextView.setText(getString(R.string.main_page_label, currentPage));
        boolean enabled = currentSource != null && currentTab != MainTab.MINE;
        pageTextView.setAlpha(enabled ? 1f : 0.72f);
        prevButton.setEnabled(enabled && currentPage > 1);
        nextButton.setEnabled(enabled);
        homeButton.setEnabled(enabled);
    }

    private void setBrowseStatus(String status) {
        browseStatusView.setText(status == null ? "" : status);
    }

    private void syncSourceLabels() {
        String sourceTitle = currentSource == null || currentSource.title == null || currentSource.title.trim().isEmpty()
                ? getString(R.string.main_msg_source_title_loading)
                : currentSource.title;
        featuredSourceView.setText(sourceTitle);
        librarySourceTextView.setText(sourceTitle);
        gridAdapter.setSourceLabel(sourceTitle);
        continueAdapter.setSourceLabel(sourceTitle);
        movieAdapter.setSourceLabel(sourceTitle);
    }

    private void syncFeaturedContent() {
        NativeDrpyEngine.MediaItem item = resolveFeaturedItem();
        String title = item == null || item.title.isEmpty()
                ? getString(R.string.main_section_home)
                : item.title;
        String remark = item == null
                ? getString(R.string.main_status_preparing)
                : (item.remark.isEmpty() ? getString(R.string.rank_hint) : item.remark);
        featuredTitleView.setText(title);
        featuredRemarkView.setText(remark);
        featuredActionButton.setEnabled(item != null);
        featuredActionButton.setAlpha(item != null ? 1f : 0.72f);
        PosterLoader.load(findViewById(R.id.featured_backdrop), item == null ? "" : item.poster, title);
    }

    private NativeDrpyEngine.MediaItem resolveFeaturedItem() {
        if (!homeItems.isEmpty()) {
            return homeItems.get(0);
        }
        if (gridAdapter.getDataCount() > 0) {
            List<NativeDrpyEngine.MediaItem> items = gridAdapter.getItems();
            return items.isEmpty() ? null : items.get(0);
        }
        if (rankAdapter.getDataCount() > 0) {
            ArrayList<NativeDrpyEngine.MediaItem> items = rankAdapter.getItems();
            return items.isEmpty() ? null : items.get(0);
        }
        return null;
    }

    private void refreshMineSettings() {
        suppressMineSwitchCallbacks = true;
        mineThemeSwitch.setChecked(SettingsStore.nightModeEnabled(this));
        mineAutoPlaySwitch.setChecked(SettingsStore.autoPlayEnabled(this));
        mineRememberSourceSwitch.setChecked(SettingsStore.rememberSource(this));
        mineKeepSearchSwitch.setChecked(SettingsStore.keepLastSearch(this));
        mineDefaultLibrarySwitch.setChecked(SettingsStore.defaultLibrary(this));
        suppressMineSwitchCallbacks = false;
        mineKernelNameView.setText(getString(R.string.main_mine_kernel_name, BuildConfig.VERSION_NAME));
        mineKernelTipView.setText(getString(R.string.main_mine_kernel_tip));
    }

    private void refreshMineProfile() {
        String sourceTitle = currentSource == null
                ? getString(R.string.main_msg_source_title_loading)
                : getString(R.string.main_msg_source_title, currentSource.title);
        String sourceHost = currentSource == null || currentSource.host.isEmpty()
                ? getString(R.string.main_msg_source_host_unavailable)
                : getString(R.string.main_msg_source_host, currentSource.host);
        mineSourceNameView.setText(sourceTitle);
        mineSourceHostView.setText(sourceHost);

        QqProfileStore.Profile profile = QqProfileStore.load(this);
        if (profile.qq.isEmpty()) {
            mineQqInput.setText("");
        } else if (!TextUtils.equals(profile.qq, textOf(mineQqInput))) {
            mineQqInput.setText(profile.qq);
        }
        mineNicknameView.setText(profile.nickname.isEmpty()
                ? getString(R.string.mine_profile_default_name)
                : profile.nickname);
        mineSignatureView.setText(profile.signature.isEmpty()
                ? getString(R.string.mine_profile_default_signature)
                : profile.signature);
        AvatarLoader.load(mineAvatarView, profile.avatarUrl);
    }

    private void queryQqProfile() {
        String qq = textOf(mineQqInput);
        if (qq.isEmpty()) {
            toast(getString(R.string.mine_profile_input_hint));
            return;
        }
        mineQueryButton.setEnabled(false);
        mineQueryButton.setText(getString(R.string.mine_profile_querying));
        QqProfileService.fetch(qq, (profile, error) -> runOnUiThread(() -> {
            mineQueryButton.setEnabled(true);
            mineQueryButton.setText(getString(R.string.mine_profile_query));
            if (!TextUtils.isEmpty(error) || profile == null) {
                toast(TextUtils.isEmpty(error) ? getString(R.string.mine_profile_query_failed) : error);
                return;
            }
            QqProfileStore.save(this, profile);
            refreshMineProfile();
            toast(getString(R.string.mine_profile_query_success));
        }));
    }

    private void clearQqProfile() {
        QqProfileStore.clear(this);
        refreshMineProfile();
        toast(getString(R.string.mine_profile_clear_success));
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

    private boolean sameSource(SourceStore.SourceItem left, SourceStore.SourceItem right) {
        if (left == null || right == null) {
            return false;
        }
        return TextUtils.equals(left.id, right.id)
                && TextUtils.equals(left.title, right.title)
                && TextUtils.equals(left.host, right.host)
                && TextUtils.equals(left.raw, right.raw);
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

    private ArrayList<NativeDrpyEngine.MediaItem> sliceItems(List<NativeDrpyEngine.MediaItem> items, int start, int count) {
        ArrayList<NativeDrpyEngine.MediaItem> sliced = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return sliced;
        }
        int safeStart = Math.max(0, Math.min(start, items.size()));
        int end = Math.min(items.size(), safeStart + Math.max(1, count));
        for (int i = safeStart; i < end; i++) {
            sliced.add(items.get(i));
        }
        if (sliced.isEmpty() && safeStart > 0) {
            return sliceItems(items, 0, count);
        }
        return sliced;
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

}
