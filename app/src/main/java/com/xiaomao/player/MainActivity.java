package com.xiaomao.player;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
            "鎬绘", "鍓ч泦", "鐢靛奖", "缁艰壓", "鍔ㄦ极", "椋欏崌", "闄㈢嚎"
    };
    private static final long SOURCE_MIGRATION_INTERVAL_MS = 30L * 60L * 1000L;

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
    private View categoryScrollView;
    private ChipGroup categoryGroup;
    private ChipGroup rankFilterGroup;
    private TextInputEditText searchInput;
    private TextView emptyTextView;
    private TextView browseStatusView;
    private TextView pageTextView;
    private TextView mineFavoritesEmptyView;
    private TextView mineHistoryEmptyView;
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
    private View searchHistoryRow;
    private LinearLayout searchHistoryContent;
    private View searchHistoryClearButton;
    private View headerSearchAction;
    private View headerMoreAction;
    private View homeActionCategory;
    private View homeActionFeatured;
    private View homeActionShort;
    private View homeActionSchedule;
    private View homeActionLive;
    private RecyclerView mineFavoritesRecyclerView;
    private RecyclerView mineHistoryRecyclerView;

    private final ArrayList<SourceStore.SourceItem> sources = new ArrayList<>();
    private final ArrayList<NativeDrpyEngine.Category> categories = new ArrayList<>();
    private final ArrayList<NativeDrpyEngine.MediaItem> homeItems = new ArrayList<>();
    private final ArrayList<NativeDrpyEngine.MediaItem> libraryItems = new ArrayList<>();
    private final ArrayList<NativeDrpyEngine.MediaItem> rankItems = new ArrayList<>();
    private final MediaGridAdapter gridAdapter = new MediaGridAdapter();
    private final MediaGridAdapter homeRecommendAdapter = new MediaGridAdapter();
    private final RankListAdapter rankAdapter = new RankListAdapter();
    private final MediaRailAdapter continueAdapter = new MediaRailAdapter();
    private final CategorySidebarAdapter sidebarAdapter = new CategorySidebarAdapter();
    private final ShelfRailAdapter favoriteShelfAdapter = new ShelfRailAdapter();
    private final ShelfRailAdapter historyShelfAdapter = new ShelfRailAdapter();

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
    private boolean pageLoading = false;
    private boolean reachedEnd = false;
    private boolean mineSignatureExpanded = false;
    private long lastSourceMigrationAt = 0L;
    private String lastMineSignatureText = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupRecycler();
        setupEvents();
        bindQuickActions();
        renderRankFilters();
        refreshMineSettings();
        refreshMineProfile();
        refreshMineShelves();
        applyTabState();
        loadSources();
        scheduleRemoteSourceMigration();
        maybeShowOnboarding();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncSourceIfNeeded();
        refreshMineSettings();
        refreshMineProfile();
        refreshMineShelves();
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
        categoryScrollView = findViewById(R.id.category_scroll);
        categoryGroup = findViewById(R.id.category_group);
        rankFilterGroup = findViewById(R.id.rank_filter_group);
        searchInput = findViewById(R.id.search_input);
        emptyTextView = findViewById(R.id.empty_text);
        browseStatusView = findViewById(R.id.browse_status_text);
        pageTextView = findViewById(R.id.page_text);
        mineFavoritesEmptyView = findViewById(R.id.mine_favorites_empty);
        mineHistoryEmptyView = findViewById(R.id.mine_history_empty);
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
        searchHistoryRow = findViewById(R.id.search_history_row);
        searchHistoryContent = findViewById(R.id.search_history_content);
        searchHistoryClearButton = findViewById(R.id.search_history_clear_button);
        headerSearchAction = findViewById(R.id.header_search_action);
        headerMoreAction = findViewById(R.id.header_more_action);
        homeActionCategory = findViewById(R.id.home_action_category);
        homeActionFeatured = findViewById(R.id.home_action_featured);
        homeActionShort = findViewById(R.id.home_action_short);
        homeActionSchedule = findViewById(R.id.home_action_schedule);
        homeActionLive = findViewById(R.id.home_action_live);
        mineFavoritesRecyclerView = findViewById(R.id.mine_favorites_recycler);
        mineHistoryRecyclerView = findViewById(R.id.mine_history_recycler);
    }

    private void setupRecycler() {
        mediaRecyclerView.setLayoutManager(new GridLayoutManager(this, computeGridSpanCount()));
        mediaRecyclerView.setAdapter(gridAdapter);
        gridAdapter.setOnItemClickListener(this::openDetail);
        gridAdapter.setOnItemLongClickListener(this::showMediaActions);

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
        rankAdapter.setOnItemLongClickListener(this::showRankActions);

        homeContinueRecyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        homeContinueRecyclerView.setAdapter(continueAdapter);
        continueAdapter.setOnItemClickListener(this::openDetail);
        continueAdapter.setOnItemLongClickListener(this::showMediaActions);

        homeMovieRecyclerView.setLayoutManager(new GridLayoutManager(this, computeGridSpanCount()));
        homeMovieRecyclerView.setAdapter(homeRecommendAdapter);
        homeRecommendAdapter.setOnItemClickListener(this::openDetail);
        homeRecommendAdapter.setOnItemLongClickListener(this::showMediaActions);

        mineFavoritesRecyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        mineFavoritesRecyclerView.setAdapter(favoriteShelfAdapter);
        favoriteShelfAdapter.setOnItemClickListener(card -> {
            if (card.payload instanceof FavoriteStore.FavoriteItem) {
                openFavoriteDetail((FavoriteStore.FavoriteItem) card.payload, false);
            }
        });
        favoriteShelfAdapter.setOnItemLongClickListener((card, anchor) -> {
            if (card.payload instanceof FavoriteStore.FavoriteItem) {
                showFavoriteActions((FavoriteStore.FavoriteItem) card.payload, anchor);
            }
        });

        mineHistoryRecyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        mineHistoryRecyclerView.setAdapter(historyShelfAdapter);
        historyShelfAdapter.setOnItemClickListener(card -> {
            if (card.payload instanceof WatchHistoryStore.HistoryItem) {
                openHistoryPlayback((WatchHistoryStore.HistoryItem) card.payload);
            }
        });
        historyShelfAdapter.setOnItemLongClickListener((card, anchor) -> {
            if (card.payload instanceof WatchHistoryStore.HistoryItem) {
                showHistoryActions((WatchHistoryStore.HistoryItem) card.payload, anchor);
            }
        });

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.xm_accent));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.xm_surface));
        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> {
            if (currentTab == MainTab.HOME) {
                return homeScrollView.canScrollVertically(-1);
            }
            if (currentTab == MainTab.MINE) {
                return mineScrollView.canScrollVertically(-1);
            }
            if (currentTab == MainTab.RANK) {
                return rankRecyclerView.canScrollVertically(-1);
            }
            return mediaRecyclerView.canScrollVertically(-1);
        });
        setupEndlessPaging();
    }

    private void setupEvents() {
        if (searchButton != null) {
            UiEffects.bindPressScale(searchButton);
        }
        UiEffects.bindPressScale(featuredActionButton);
        UiEffects.bindPressScale(homeButton);
        UiEffects.bindPressScale(prevButton);
        UiEffects.bindPressScale(nextButton);
        UiEffects.bindPressScale(mineSettingsButton);
        UiEffects.bindPressScale(mineImportButton);
        UiEffects.bindPressScale(mineSourceManageButton);
        UiEffects.bindPressScale(mineQueryButton);
        UiEffects.bindPressScale(mineClearProfileButton);
        UiEffects.bindPressScale(headerSearchAction);
        UiEffects.bindPressScale(headerMoreAction);
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> performSearch(1));
        }
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
        headerSearchAction.setOnClickListener(v -> openSearchPage(""));
        headerMoreAction.setOnClickListener(v -> openNativePage(SettingsActivity.class));
        if (searchHistoryClearButton != null) {
            UiEffects.bindPressScale(searchHistoryClearButton);
            searchHistoryClearButton.setOnClickListener(v -> {
                SearchHistoryStore.clear(this);
                renderSearchHistory();
                toast("\u5DF2\u6E05\u7A7A\u641C\u7D22\u5386\u53F2");
            });
        }
        swipeRefreshLayout.setOnRefreshListener(() -> reloadCurrentPage(true));
        if (searchInput != null) {
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
        bottomNavigationView.setOnItemSelectedListener(this::onBottomNavigationSelected);
        mineAvatarView.setOnTouchListener((v, event) -> {
            if (event == null) {
                return false;
            }
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(1.16f).scaleY(1.16f).setDuration(120L).start();
            } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(160L).start();
            }
            return false;
        });
        mineSignatureView.setOnClickListener(v -> toggleMineSignatureExpanded());

        mineThemeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressMineSwitchCallbacks) {
                return;
            }
            if (SettingsStore.followSystemTheme(this)) {
                refreshMineSettings();
                toast("\u5F53\u524D\u5DF2\u5F00\u542F\u8DDF\u968F\u7CFB\u7EDF\u4E3B\u9898");
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
            if (!isChecked && searchInput != null) {
                searchInput.setText("");
            }
            renderSearchHistory();
        });
        mineDefaultLibrarySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!suppressMineSwitchCallbacks) {
                SettingsStore.setDefaultLibrary(this, isChecked);
            }
        });
    }

    private void bindQuickActions() {
        UiEffects.bindPressScale(homeActionCategory);
        UiEffects.bindPressScale(homeActionFeatured);
        UiEffects.bindPressScale(homeActionShort);
        UiEffects.bindPressScale(homeActionSchedule);
        UiEffects.bindPressScale(homeActionLive);
        homeActionCategory.setOnClickListener(v -> openLibraryTab(true));
        homeActionFeatured.setOnClickListener(v -> openHomeTab(true));
        homeActionShort.setOnClickListener(v -> openSearchPage(getString(R.string.home_action_short)));
        homeActionSchedule.setOnClickListener(v -> openRankTab(true));
        homeActionLive.setOnClickListener(v -> openNativePage(SourceManagementActivity.class));
    }

    private void setupEndlessPaging() {
        homeScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (currentTab != MainTab.HOME || scrollY <= oldScrollY) {
                return;
            }
            View child = v.getChildAt(0);
            if (child == null) {
                return;
            }
            if (scrollY + v.getHeight() >= child.getHeight() - dp(140)) {
                loadNextPageIfNeeded();
            }
        });
        mediaRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && currentTab == MainTab.LIBRARY) {
                    maybeLoadMoreFromRecycler(recyclerView);
                }
            }
        });
        rankRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0 && currentTab == MainTab.RANK) {
                    maybeLoadMoreFromRecycler(recyclerView);
                }
            }
        });
    }

    private void maybeLoadMoreFromRecycler(RecyclerView recyclerView) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) {
            return;
        }
        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
        int lastVisible = linearLayoutManager.findLastVisibleItemPosition();
        int total = linearLayoutManager.getItemCount();
        if (total > 0 && lastVisible >= total - Math.max(2, computeGridSpanCount())) {
            loadNextPageIfNeeded();
        }
    }

    private void loadNextPageIfNeeded() {
        if (pageLoading || reachedEnd || swipeRefreshLayout.isRefreshing() || currentTab == MainTab.MINE) {
            return;
        }
        if (engine == null && browseMode != BrowseMode.RANK) {
            return;
        }
        if (browseMode == BrowseMode.SEARCH) {
            performSearch(currentPage + 1);
        } else if (browseMode == BrowseMode.CATEGORY && activeCategory != null) {
            loadCategoryPage(activeCategory, currentPage + 1);
        } else if (browseMode == BrowseMode.RANK) {
            loadRankPage(currentPage + 1);
        } else if (browseMode == BrowseMode.HOME) {
            loadHomePage(currentPage + 1);
        }
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
        openSearchPage("");
    }

    private void openSearchPage(String keyword) {
        Intent intent = new Intent(this, SearchActivity.class);
        SourceStore.SourceItem source = currentSource != null ? currentSource : (sources.isEmpty() ? null : sources.get(0));
        if (source != null) {
            intent.putExtra(SearchActivity.EXTRA_SOURCE_TITLE, source.title == null ? "" : source.title.trim());
            intent.putExtra(SearchActivity.EXTRA_SOURCE_HOST, source.host == null ? "" : source.host.trim());
            intent.putExtra(SearchActivity.EXTRA_SOURCE_RAW, source.raw == null ? "" : source.raw.trim());
        }
        if (!TextUtils.isEmpty(keyword)) {
            intent.putExtra(SearchActivity.EXTRA_SEARCH_KEYWORD, keyword.trim());
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openNativePage(Class<?> cls) {
        pageLauncher.launch(new Intent(this, cls));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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
        long now = System.currentTimeMillis();
        if (now - lastSourceMigrationAt < SOURCE_MIGRATION_INTERVAL_MS) {
            return;
        }
        lastSourceMigrationAt = now;
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
        pageLoading = false;
        reachedEnd = false;
        browseMode = BrowseMode.HOME;
        currentTab = MainTab.HOME;
        activeCategory = null;
        selectedRankFilterIndex = 0;
        categories.clear();
        homeItems.clear();
        libraryItems.clear();
        rankItems.clear();
        gridAdapter.setSourceLabel(record.title);
        continueAdapter.setSourceLabel(record.title);
        homeRecommendAdapter.setSourceLabel(record.title);
        gridAdapter.submitList(new ArrayList<>());
        continueAdapter.submitList(new ArrayList<>());
        homeRecommendAdapter.submitList(new ArrayList<>());
        rankAdapter.submitList(new ArrayList<>());
        renderCategories();
        renderRankFilters();
        syncSourceLabels();
        refreshMineProfile();
        renderSearchHistory();
        applyTabState();
        if (SettingsStore.defaultLibrary(this)) {
            syncBottomSelection(R.id.menu_library);
            openLibraryTab(true);
        } else {
            syncBottomSelection(R.id.menu_home);
            loadHomePage(1);
        }
    }

    private void loadCategories() {
        if (engine == null && currentTab != MainTab.RANK && browseMode != BrowseMode.RANK) {
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
        if (pageLoading && targetPage > currentPage) {
            return;
        }
        final int token = ++contentVersion;
        final int sourceToken = sourceVersion;
        final boolean append = targetPage > 1;
        if (!append) {
            reachedEnd = false;
        }
        pageLoading = true;
        currentTab = MainTab.HOME;
        browseMode = BrowseMode.HOME;
        currentPage = targetPage;
        applyTabState();
        syncBottomSelection(R.id.menu_home);
        if (!append) {
            showLoading(true, getString(R.string.main_msg_home_loading));
        }
        engine.loadRecommend(targetPage, (items, err) -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
                return;
            }
            pageLoading = false;
            swipeRefreshLayout.setRefreshing(false);
            if (!TextUtils.isEmpty(err) && (items == null || items.isEmpty())) {
                showLoading(false, getString(R.string.main_msg_home_failed, err));
                return;
            }
            if (append && (items == null || items.isEmpty())) {
                reachedEnd = true;
                showLoading(false, homeItems.isEmpty() ? getString(R.string.main_msg_home_last_page) : "");
                return;
            }
            if (!append) {
                homeItems.clear();
            }
            if (items != null) {
                homeItems.addAll(items);
            }
            continueAdapter.submitList(sliceItems(homeItems, 0, 8));
            homeRecommendAdapter.submitList(sliceTailItems(homeItems, 8));
            currentPage = targetPage;
            reachedEnd = items == null || items.isEmpty();
            showLoading(false, homeItems.isEmpty() ? getString(R.string.main_msg_home_empty) : "");
            setBrowseStatus(getString(R.string.main_msg_home_status, currentPage));
            syncFeaturedContent();
            if (!append) {
                homeScrollView.scrollTo(0, 0);
            }
        });
    }

    private void loadCategoryPage(NativeDrpyEngine.Category category, int page) {
        if (engine == null || category == null) {
            return;
        }
        final int targetPage = Math.max(1, page);
        if (pageLoading && targetPage > currentPage) {
            return;
        }
        final int token = ++contentVersion;
        final int sourceToken = sourceVersion;
        final boolean append = targetPage > 1;
        if (!append) {
            reachedEnd = false;
        }
        pageLoading = true;
        currentTab = MainTab.LIBRARY;
        browseMode = BrowseMode.CATEGORY;
        activeCategory = category;
        currentPage = targetPage;
        applyTabState();
        syncBottomSelection(R.id.menu_library);
        renderCategories();
        if (!append) {
            showLoading(true, getString(R.string.main_msg_category_loading, category.name));
        }
        engine.loadCategoryItems(category.url, targetPage, (items, err) -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
                return;
            }
            pageLoading = false;
            swipeRefreshLayout.setRefreshing(false);
            if (!TextUtils.isEmpty(err) && (items == null || items.isEmpty())) {
                showLoading(false, getString(R.string.main_msg_category_failed, err));
                return;
            }
            if (append && (items == null || items.isEmpty())) {
                reachedEnd = true;
                showLoading(false, libraryItems.isEmpty() ? getString(R.string.main_msg_category_last_page) : "");
                return;
            }
            if (!append) {
                libraryItems.clear();
            }
            if (items != null) {
                libraryItems.addAll(items);
            }
            gridAdapter.submitList(new ArrayList<>(libraryItems));
            currentPage = targetPage;
            reachedEnd = items == null || items.isEmpty();
            showLoading(false, libraryItems.isEmpty() ? getString(R.string.main_msg_category_empty) : "");
            setBrowseStatus(getString(R.string.main_msg_category_status, category.name, currentPage));
            syncFeaturedContent();
            if (!append) {
                mediaRecyclerView.scrollToPosition(0);
            }
        });
    }

    private void loadRankPage(int page) {
        final int targetPage = Math.max(1, page);
        if (pageLoading && targetPage > currentPage) {
            return;
        }
        final int token = ++contentVersion;
        final int sourceToken = sourceVersion;
        final boolean append = targetPage > 1;
        if (!append) {
            reachedEnd = false;
        }
        pageLoading = true;
        currentTab = MainTab.RANK;
        browseMode = BrowseMode.RANK;
        currentPage = targetPage;
        if (!append) {
            rankItems.clear();
            rankAdapter.submitList(new ArrayList<>());
        }
        applyTabState();
        syncBottomSelection(R.id.menu_rank);
        if (!append) {
            showLoading(true, getString(R.string.main_msg_rank_loading));
        }
        DoubanRankService.fetch(selectedRankFilterIndex, targetPage, (result, err) -> runOnUiThread(() -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
                return;
            }
            pageLoading = false;
            swipeRefreshLayout.setRefreshing(false);
            ArrayList<NativeDrpyEngine.MediaItem> items = result == null ? new ArrayList<>() : result.items;
            if (!TextUtils.isEmpty(err) && items.isEmpty()) {
                showLoading(false, getString(R.string.main_msg_rank_failed, err));
                return;
            }
            if (append && items.isEmpty()) {
                reachedEnd = true;
                showLoading(false, rankItems.isEmpty() ? getString(R.string.main_msg_rank_last_page) : "");
                return;
            }
            if (!append) {
                rankItems.clear();
            }
            rankItems.addAll(items);
            rankAdapter.submitList(new ArrayList<>(rankItems));
            currentPage = targetPage;
            reachedEnd = result == null || !result.hasMore || items.isEmpty();
            showLoading(false, rankItems.isEmpty() ? getString(R.string.main_msg_rank_empty) : "");
            setBrowseStatus(RANK_FILTERS[Math.max(0, Math.min(selectedRankFilterIndex, RANK_FILTERS.length - 1))]
                    + " 璺?"
                    + getString(R.string.main_msg_rank_status, currentPage));
            syncFeaturedContent();
            if (!append) {
                rankRecyclerView.scrollToPosition(0);
            }
        }));
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
        if (pageLoading && targetPage > currentPage) {
            return;
        }
        final int token = ++contentVersion;
        final int sourceToken = sourceVersion;
        final boolean append = targetPage > 1;
        if (!append) {
            reachedEnd = false;
        }
        pageLoading = true;
        currentTab = MainTab.LIBRARY;
        browseMode = BrowseMode.SEARCH;
        currentPage = targetPage;
        applyTabState();
        syncBottomSelection(R.id.menu_library);
        renderCategories();
        if (!append) {
            showLoading(true, getString(R.string.main_msg_search_loading, keyword));
            SearchHistoryStore.save(this, keyword);
            renderSearchHistory();
        }
        engine.search(keyword, targetPage, (items, err) -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
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
                showLoading(false, libraryItems.isEmpty() ? getString(R.string.main_msg_search_last_page) : "");
                return;
            }
            if (!append) {
                libraryItems.clear();
            }
            if (items != null) {
                libraryItems.addAll(items);
            }
            gridAdapter.submitList(new ArrayList<>(libraryItems));
            currentPage = targetPage;
            reachedEnd = items == null || items.isEmpty();
            showLoading(false, libraryItems.isEmpty() ? getString(R.string.main_msg_search_empty) : "");
            setBrowseStatus(getString(R.string.main_msg_search_status, currentPage));
            syncFeaturedContent();
            if (!append) {
                mediaRecyclerView.scrollToPosition(0);
            }
        });
    }

    private void reloadCurrentPage(boolean userInitiated) {
        if (engine == null && currentTab != MainTab.RANK && browseMode != BrowseMode.RANK) {
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
        if (currentTab == MainTab.MINE) {
            return;
        }
        if (engine == null && browseMode != BrowseMode.RANK) {
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
        if (categoryGroup != null) {
            categoryGroup.removeAllViews();
        }
        for (NativeDrpyEngine.Category category : categories) {
            if (categoryGroup != null) {
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
                if (currentTab == MainTab.RANK) {
                    reachedEnd = false;
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
        chip.setChipCornerRadius(dp(12));
        chip.setChipStartPadding(dp(12));
        chip.setChipEndPadding(dp(12));
        int checkedText = ContextCompat.getColor(this, R.color.xm_accent);
        int defaultText = ContextCompat.getColor(this, R.color.xm_text_primary);
        int checkedBg = ContextCompat.getColor(this, R.color.xm_info_bg);
        int defaultBg = ContextCompat.getColor(this, R.color.xm_surface_elevated);
        int checkedStroke = ContextCompat.getColor(this, R.color.xm_accent);
        int defaultStroke = ContextCompat.getColor(this, R.color.xm_stroke_soft);
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
        UiEffects.bindPressScale(chip);
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
        pageControlsView.setVisibility(View.GONE);
        if (searchPanel != null) {
            searchPanel.setVisibility(View.GONE);
        }
        if (categoryScrollView != null) {
            categoryScrollView.setVisibility(View.GONE);
        }

        if (showMine) {
            loadingSkeletonContainer.setVisibility(View.GONE);
            emptyContainer.setVisibility(View.GONE);
        }
        updateHeaderContent();
        updatePager();
        syncFeaturedContent();
        syncSourceLabels();
        animateActivePanel(showHome ? homeScrollView : showLibrary ? libraryContainer : showRank ? rankContainer : mineScrollView);
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
            headerSubtitleView.setText(RANK_FILTERS[Math.max(0, Math.min(selectedRankFilterIndex, RANK_FILTERS.length - 1))]
                    + " 璺?"
                    + getString(R.string.main_header_subtitle_rank));
            return;
        }
        if (currentTab == MainTab.LIBRARY) {
            headerTitleView.setText(getString(R.string.nav_library));
            if (browseMode == BrowseMode.SEARCH) {
                String keyword = textOf(searchInput);
                headerSubtitleView.setText(keyword.isEmpty()
                        ? getString(R.string.main_header_subtitle_library)
                        : getString(R.string.main_msg_search_title, keyword));
            } else if (activeCategory != null && !activeCategory.name.isEmpty()) {
                headerSubtitleView.setText(activeCategory.name + " 璺?" + getString(R.string.main_header_subtitle_library));
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
        homeRecommendAdapter.setSourceLabel(sourceTitle);
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
        boolean followSystemTheme = SettingsStore.followSystemTheme(this);
        mineThemeSwitch.setChecked(followSystemTheme ? isNightModeActive() : SettingsStore.nightModeEnabled(this));
        mineThemeSwitch.setEnabled(!followSystemTheme);
        mineThemeSwitch.setAlpha(followSystemTheme ? 0.45f : 1f);
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
        String signature = profile.signature.isEmpty()
                ? getString(R.string.mine_profile_default_signature)
                : profile.signature;
        if (!TextUtils.equals(lastMineSignatureText, signature)) {
            lastMineSignatureText = signature;
            mineSignatureExpanded = false;
        }
        mineSignatureView.setText(signature);
        mineSignatureView.setMaxLines(mineSignatureExpanded ? 6 : 2);
        mineSignatureView.setEllipsize(mineSignatureExpanded ? null : TextUtils.TruncateAt.END);
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
        openDetail(item, false);
    }

    private void openDetail(NativeDrpyEngine.MediaItem item, boolean autoPlayFirst) {
        if (isDoubanRankItem(item)) {
            resolveDoubanRankItem(item, autoPlayFirst);
            return;
        }
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
        intent.putExtra("auto_play_first", autoPlayFirst);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void showRankActions(NativeDrpyEngine.MediaItem item, View anchor) {
        if (!isDoubanRankItem(item)) {
            showMediaActions(item, anchor);
            return;
        }
        if (item == null || anchor == null) {
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "\u5FEB\u901F\u64AD\u653E");
        menu.getMenu().add(0, 2, 1, "\u5339\u914D\u7247\u6E90");
        if (!TextUtils.isEmpty(item.url)) {
            menu.getMenu().add(0, 3, 2, "\u6253\u5F00\u8C46\u74E3");
        }
        menu.setOnMenuItemClickListener(entry -> {
            int id = entry.getItemId();
            if (id == 1) {
                resolveDoubanRankItem(item, true);
                return true;
            }
            if (id == 2) {
                resolveDoubanRankItem(item, false);
                return true;
            }
            if (id == 3) {
                openExternalUrl(item.url);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void resolveDoubanRankItem(NativeDrpyEngine.MediaItem item, boolean autoPlayFirst) {
        if (item == null) {
            return;
        }
        if (engine == null || currentSource == null) {
            if (!TextUtils.isEmpty(item.url)) {
                openExternalUrl(item.url);
            } else {
                toast("\u5F53\u524D\u672A\u52A0\u8F7D\u7247\u6E90");
            }
            return;
        }
        final String keyword = buildRankSearchKeyword(item);
        if (TextUtils.isEmpty(keyword)) {
            if (!TextUtils.isEmpty(item.url)) {
                openExternalUrl(item.url);
            }
            return;
        }
        final int sourceToken = sourceVersion;
        final SourceStore.SourceItem sourceSnapshot = currentSource;
        swipeRefreshLayout.setRefreshing(true);
        engine.search(keyword, 1, (items, err) -> {
            swipeRefreshLayout.setRefreshing(false);
            if (sourceToken != sourceVersion || !sameSource(sourceSnapshot, currentSource)) {
                return;
            }
            NativeDrpyEngine.MediaItem match = pickBestRankMatch(keyword, items);
            if (match != null) {
                openDetail(match, autoPlayFirst);
                return;
            }
            if (!TextUtils.isEmpty(err)) {
                toast(err);
                return;
            }
            toast("\u5F53\u524D\u7247\u6E90\u672A\u627E\u5230\u300A" + keyword + "\u300B");
        });
    }

    private boolean isDoubanRankItem(NativeDrpyEngine.MediaItem item) {
        if (item == null) {
            return false;
        }
        String url = item.url == null ? "" : item.url;
        return url.startsWith("https://movie.douban.com/subject/");
    }

    private String buildRankSearchKeyword(NativeDrpyEngine.MediaItem item) {
        if (item == null || item.title == null) {
            return "";
        }
        return item.title.trim();
    }

    private NativeDrpyEngine.MediaItem pickBestRankMatch(String keyword, List<NativeDrpyEngine.MediaItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        String target = normalizeTitle(keyword);
        NativeDrpyEngine.MediaItem partial = null;
        for (NativeDrpyEngine.MediaItem item : items) {
            if (item == null || TextUtils.isEmpty(item.title)) {
                continue;
            }
            String candidate = normalizeTitle(item.title);
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.equals(target)) {
                return item;
            }
            if (partial == null && (candidate.contains(target) || target.contains(candidate))) {
                partial = item;
            }
        }
        return partial != null ? partial : items.get(0);
    }

    private String normalizeTitle(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch) || Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }

    private void openExternalUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            toast("\u65E0\u6CD5\u6253\u5F00\u5916\u90E8\u94FE\u63A5");
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

    private ArrayList<NativeDrpyEngine.MediaItem> sliceTailItems(List<NativeDrpyEngine.MediaItem> items, int start) {
        ArrayList<NativeDrpyEngine.MediaItem> sliced = new ArrayList<>();
        if (items == null || items.isEmpty() || start >= items.size()) {
            return sliced;
        }
        for (int i = Math.max(0, start); i < items.size(); i++) {
            sliced.add(items.get(i));
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

    private void renderSearchHistory() {
        if (searchHistoryRow == null || searchHistoryContent == null || searchInput == null) {
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
            Chip chip = buildChip(history, false);
            chip.setText(history);
            chip.setCheckable(false);
            chip.setChecked(false);
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

    private void refreshMineShelves() {
        ArrayList<ShelfRailAdapter.ShelfCard> favoriteCards = new ArrayList<>();
        for (FavoriteStore.FavoriteItem item : FavoriteStore.list(this)) {
            favoriteCards.add(new ShelfRailAdapter.ShelfCard(
                    item.title,
                    item.remark.isEmpty() ? "\u70B9\u51FB\u67E5\u770B\u8BE6\u60C5" : item.remark,
                    item.sourceTitle,
                    item.poster,
                    "\u6536\u85CF",
                    item
            ));
        }
        favoriteShelfAdapter.submitList(favoriteCards);
        if (mineFavoritesEmptyView != null && mineFavoritesRecyclerView != null) {
            mineFavoritesEmptyView.setVisibility(favoriteCards.isEmpty() ? View.VISIBLE : View.GONE);
            mineFavoritesRecyclerView.setVisibility(favoriteCards.isEmpty() ? View.GONE : View.VISIBLE);
        }

        ArrayList<ShelfRailAdapter.ShelfCard> historyCards = new ArrayList<>();
        for (WatchHistoryStore.HistoryItem item : WatchHistoryStore.list(this)) {
            historyCards.add(new ShelfRailAdapter.ShelfCard(
                    item.seriesTitle,
                    buildHistoryRemark(item),
                    item.sourceTitle,
                    item.poster,
                    "\u7EED\u64AD",
                    item
            ));
        }
        historyShelfAdapter.submitList(historyCards);
        if (mineHistoryEmptyView != null && mineHistoryRecyclerView != null) {
            mineHistoryEmptyView.setVisibility(historyCards.isEmpty() ? View.VISIBLE : View.GONE);
            mineHistoryRecyclerView.setVisibility(historyCards.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void showMediaActions(NativeDrpyEngine.MediaItem item, View anchor) {
        if (currentSource == null || item == null || anchor == null) {
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        boolean favored = FavoriteStore.contains(this, currentSource.host, item.url.isEmpty() ? item.vodId : item.url);
        menu.getMenu().add(0, 1, 0, "\u5FEB\u901F\u64AD\u653E");
        menu.getMenu().add(0, 2, 1, "\u67E5\u770B\u8BE6\u60C5");
        menu.getMenu().add(0, 3, 2, favored ? "\u53D6\u6D88\u6536\u85CF" : "\u52A0\u5165\u6536\u85CF");
        menu.setOnMenuItemClickListener(entry -> {
            int id = entry.getItemId();
            if (id == 1) {
                openDetail(item, true);
                return true;
            }
            if (id == 2) {
                openDetail(item, false);
                return true;
            }
            if (id == 3) {
                boolean nowFavored = FavoriteStore.toggle(this, currentSource, item);
                refreshMineShelves();
                toast(nowFavored ? "\u5DF2\u52A0\u5165\u6536\u85CF" : "\u5DF2\u53D6\u6D88\u6536\u85CF");
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showFavoriteActions(FavoriteStore.FavoriteItem item, View anchor) {
        if (item == null || anchor == null) {
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "\u7EE7\u7EED\u89C2\u770B");
        menu.getMenu().add(0, 2, 1, "\u67E5\u770B\u8BE6\u60C5");
        menu.getMenu().add(0, 3, 2, "\u79FB\u51FA\u6536\u85CF");
        menu.setOnMenuItemClickListener(entry -> {
            int id = entry.getItemId();
            if (id == 1) {
                openFavoriteDetail(item, true);
                return true;
            }
            if (id == 2) {
                openFavoriteDetail(item, false);
                return true;
            }
            if (id == 3) {
                FavoriteStore.remove(this, item);
                refreshMineShelves();
                toast("\u5DF2\u79FB\u51FA\u6536\u85CF");
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showHistoryActions(WatchHistoryStore.HistoryItem item, View anchor) {
        if (item == null || anchor == null) {
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        boolean favored = FavoriteStore.contains(this, item.sourceHost, item.detailUrl);
        menu.getMenu().add(0, 1, 0, "\u7EE7\u7EED\u64AD\u653E");
        menu.getMenu().add(0, 2, 1, "\u67E5\u770B\u8BE6\u60C5");
        menu.getMenu().add(0, 3, 2, favored ? "\u53D6\u6D88\u6536\u85CF" : "\u52A0\u5165\u6536\u85CF");
        menu.getMenu().add(0, 4, 3, "\u5220\u9664\u8BB0\u5F55");
        menu.setOnMenuItemClickListener(entry -> {
            int id = entry.getItemId();
            if (id == 1) {
                openHistoryPlayback(item);
                return true;
            }
            if (id == 2) {
                openHistoryDetail(item);
                return true;
            }
            if (id == 3) {
                boolean nowFavored = FavoriteStore.toggle(this, buildStoredSource(item.sourceTitle, item.sourceHost, item.sourceRaw), buildStoredMediaItem(item.seriesTitle, item.poster, item.remark, item.detailUrl));
                refreshMineShelves();
                toast(nowFavored ? "\u5DF2\u52A0\u5165\u6536\u85CF" : "\u5DF2\u53D6\u6D88\u6536\u85CF");
                return true;
            }
            if (id == 4) {
                WatchHistoryStore.remove(this, item);
                refreshMineShelves();
                toast("\u5DF2\u5220\u9664\u89C2\u770B\u8BB0\u5F55");
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void openFavoriteDetail(FavoriteStore.FavoriteItem item, boolean autoPlayFirst) {
        if (item == null) {
            return;
        }
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("source_title", item.sourceTitle);
        intent.putExtra("source_host", item.sourceHost);
        intent.putExtra("source_raw", item.sourceRaw);
        intent.putExtra("item_url", item.itemUrl);
        intent.putExtra("item_title", item.title);
        intent.putExtra("item_poster", item.poster);
        intent.putExtra("item_remark", item.remark);
        intent.putExtra("auto_play_first", autoPlayFirst);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openHistoryDetail(WatchHistoryStore.HistoryItem item) {
        if (item == null || item.detailUrl.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("source_title", item.sourceTitle);
        intent.putExtra("source_host", item.sourceHost);
        intent.putExtra("source_raw", item.sourceRaw);
        intent.putExtra("item_url", item.detailUrl);
        intent.putExtra("item_title", item.seriesTitle);
        intent.putExtra("item_poster", item.poster);
        intent.putExtra("item_remark", item.remark);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void openHistoryPlayback(WatchHistoryStore.HistoryItem item) {
        if (item == null || item.playInput.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, NativePlayerActivity.class);
        intent.putExtra("title", item.episodeTitle.isEmpty() ? item.seriesTitle : item.episodeTitle);
        intent.putExtra("series_title", item.seriesTitle);
        intent.putExtra("line", item.line);
        intent.putExtra("input", item.playInput);
        intent.putExtra("source_title", item.sourceTitle);
        intent.putExtra("source_host", item.sourceHost);
        intent.putExtra("source_raw", item.sourceRaw);
        intent.putExtra("detail_page_url", item.detailUrl);
        intent.putExtra("detail_poster", item.poster);
        intent.putExtra("detail_remark", item.remark);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private SourceStore.SourceItem buildStoredSource(String title, String host, String raw) {
        return new SourceStore.SourceItem("local:" + host, title, host, raw, true);
    }

    private NativeDrpyEngine.MediaItem buildStoredMediaItem(String title, String poster, String remark, String url) {
        return new NativeDrpyEngine.MediaItem("", "", title, poster, remark, url);
    }

    private String buildHistoryRemark(WatchHistoryStore.HistoryItem item) {
        if (item == null) {
            return "\u70B9\u51FB\u7EE7\u7EED\u64AD\u653E";
        }
        StringBuilder builder = new StringBuilder();
        if (!item.episodeTitle.isEmpty()) {
            builder.append(item.episodeTitle);
        }
        if (item.progressMs > 0L) {
            if (builder.length() > 0) {
                builder.append(" \u00B7 ");
            }
            builder.append("\u7EED\u64AD ").append(formatTime(item.progressMs));
        }
        if (builder.length() == 0 && !item.remark.isEmpty()) {
            builder.append(item.remark);
        }
        if (builder.length() == 0) {
            builder.append("\u70B9\u51FB\u7EE7\u7EED\u64AD\u653E");
        }
        return builder.toString();
    }

    private void toggleMineSignatureExpanded() {
        String signature = mineSignatureView == null ? "" : String.valueOf(mineSignatureView.getText());
        if (signature.trim().length() <= 28 && mineSignatureView.getLineCount() <= 2) {
            return;
        }
        mineSignatureExpanded = !mineSignatureExpanded;
        mineSignatureView.setMaxLines(mineSignatureExpanded ? 6 : 2);
        mineSignatureView.setEllipsize(mineSignatureExpanded ? null : TextUtils.TruncateAt.END);
    }

    private void maybeShowOnboarding() {
        if (!SettingsStore.shouldShowOnboarding(this) || isFinishing()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("\u4F7F\u7528\u63D0\u793A")
                .setMessage("\u0031\u002E \u9996\u9875\u3001\u7247\u5E93\u3001\u699C\u5355\u90FD\u652F\u6301\u4E0A\u62C9\u7FFB\u9875\u3002\n"
                        + "\u0032\u002E \u957F\u6309\u5361\u7247\u53EF\u4EE5\u5FEB\u901F\u64AD\u653E\u6216\u52A0\u5165\u6536\u85CF\u3002\n"
                        + "\u0033\u002E \u64AD\u653E\u9875\u4F1A\u8BB0\u5F55\u7EE7\u64AD\u8FDB\u5EA6\uFF0C\u201C\u6211\u7684\u201D\u91CC\u53EF\u4EE5\u7EE7\u7EED\u89C2\u770B\u3002")
                .setPositiveButton("\u6211\u77E5\u9053\u4E86", (dialog, which) -> {
                    SettingsStore.markOnboardingShown(this);
                    dialog.dismiss();
                })
                .setCancelable(true)
                .show();
    }

    private boolean isNightModeActive() {
        int nightModeMask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeMask == Configuration.UI_MODE_NIGHT_YES;
    }

    private void animateActivePanel(View target) {
        if (target == null || target.getVisibility() != View.VISIBLE) {
            return;
        }
        target.setAlpha(0f);
        target.setTranslationX(dp(18));
        target.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(180L)
                .start();
    }

    private String formatTime(long positionMs) {
        long totalSeconds = Math.max(0L, positionMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

}

