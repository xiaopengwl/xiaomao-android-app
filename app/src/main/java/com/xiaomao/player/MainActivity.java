package com.xiaomao.player;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.HorizontalScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern TITLE_PATTERN = Pattern.compile("title\\s*:\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern HOST_PATTERN = Pattern.compile("host\\s*:\\s*['\"]([^'\"]+)['\"]");

    private TextView mainTitleView;
    private TextView mainSubtitleView;
    private TextView headerSourceTextView;
    private TextView sectionTitleView;
    private TextView statusTextView;
    private TextView pageTextView;
    private TextView emptyTextView;
    private TextView mineSourceNameView;
    private TextView mineSourceHostView;
    private TextView mineFeatureTextView;
    private View emptyContainer;
    private View loadingIndicator;
    private View pageControlsView;
    private HorizontalScrollView categoryScrollView;
    private Spinner sourceSpinner;
    private MaterialButton refreshButton;
    private MaterialButton searchButton;
    private MaterialButton homeButton;
    private MaterialButton prevButton;
    private MaterialButton nextButton;
    private MaterialButton mineRecommendButton;
    private MaterialButton mineLibraryButton;
    private TextInputEditText searchInput;
    private ChipGroup categoryGroup;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView mediaRecyclerView;
    private RecyclerView rankRecyclerView;
    private View mineScrollView;
    private BottomNavigationView bottomNavigationView;

    private final ArrayList<SourceRecord> sources = new ArrayList<>();
    private final ArrayList<NativeDrpyEngine.Category> categories = new ArrayList<>();
    private final MediaGridAdapter adapter = new MediaGridAdapter();
    private final RankListAdapter rankAdapter = new RankListAdapter();

    private NativeDrpyEngine engine;
    private SourceRecord currentSource;
    private NativeDrpyEngine.Category activeCategory;
    private BrowseMode browseMode = BrowseMode.HOME;
    private MainTab currentTab = MainTab.HOME;
    private String currentKeyword = "";
    private int currentPage = 1;
    private int sourceVersion = 0;
    private int contentVersion = 0;
    private boolean ignoreSourceSelection = false;
    private boolean ignoreBottomSelection = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupRecycler();
        setupEvents();
        applyTabState();
        loadSources();
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
        mainTitleView = findViewById(R.id.main_title);
        mainSubtitleView = findViewById(R.id.main_subtitle);
        headerSourceTextView = findViewById(R.id.header_source_text);
        sectionTitleView = findViewById(R.id.section_title);
        statusTextView = findViewById(R.id.status_text);
        pageTextView = findViewById(R.id.page_text);
        emptyTextView = findViewById(R.id.empty_text);
        mineSourceNameView = findViewById(R.id.mine_source_name);
        mineSourceHostView = findViewById(R.id.mine_source_host);
        mineFeatureTextView = findViewById(R.id.mine_feature_text);
        emptyContainer = findViewById(R.id.empty_container);
        loadingIndicator = findViewById(R.id.loading_indicator);
        pageControlsView = findViewById(R.id.page_controls);
        categoryScrollView = findViewById(R.id.category_scroll);
        sourceSpinner = findViewById(R.id.source_spinner);
        refreshButton = findViewById(R.id.refresh_button);
        searchButton = findViewById(R.id.search_button);
        homeButton = findViewById(R.id.home_button);
        prevButton = findViewById(R.id.prev_button);
        nextButton = findViewById(R.id.next_button);
        mineRecommendButton = findViewById(R.id.mine_recommend_button);
        mineLibraryButton = findViewById(R.id.mine_library_button);
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
    }

    private void setupEvents() {
        refreshButton.setOnClickListener(v -> reloadCurrentPage(true));
        searchButton.setOnClickListener(v -> performSearch(1));
        homeButton.setOnClickListener(v -> openHomeTab(true));
        prevButton.setOnClickListener(v -> changePage(-1));
        nextButton.setOnClickListener(v -> changePage(1));
        mineRecommendButton.setOnClickListener(v -> openHomeTab(true));
        mineLibraryButton.setOnClickListener(v -> openLibraryTab(true));
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
        sourceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (ignoreSourceSelection) {
                    return;
                }
                if (position >= 0 && position < sources.size()) {
                    switchSource(sources.get(position));
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
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

    private void loadSources() {
        try {
            String[] names = getAssets().list("sources");
            if (names == null) {
                showLoading(false, "没有找到内置片源");
                return;
            }
            Arrays.sort(names, Comparator.naturalOrder());
            sources.clear();
            for (String name : names) {
                if (!name.endsWith(".js")) {
                    continue;
                }
                String raw = readAssetText("sources/" + name);
                String title = matchFirst(raw, TITLE_PATTERN);
                if (title.isEmpty()) {
                    title = name.replace(".js", "");
                }
                String host = matchFirst(raw, HOST_PATTERN);
                sources.add(new SourceRecord(name, title, host, raw));
            }
            if (sources.isEmpty()) {
                showLoading(false, "当前没有可用片源");
                return;
            }
            bindSourceSpinner();
            switchSource(sources.get(0));
        } catch (Exception e) {
            showLoading(false, "片源加载失败：" + e.getMessage());
        }
    }

    private void bindSourceSpinner() {
        ignoreSourceSelection = true;
        sourceSpinner.setAdapter(new SourceAdapter(sources));
        sourceSpinner.setSelection(0, false);
        ignoreSourceSelection = false;
    }

    private void switchSource(SourceRecord record) {
        if (record == null) {
            return;
        }
        if (currentSource != null && currentSource.fileName.equals(record.fileName)) {
            return;
        }
        if (engine != null) {
            engine.release();
        }
        currentSource = record;
        engine = new NativeDrpyEngine(this, record.toNativeSource());
        sourceVersion += 1;
        contentVersion += 1;
        currentKeyword = "";
        currentPage = 1;
        browseMode = BrowseMode.HOME;
        currentTab = MainTab.HOME;
        activeCategory = null;
        categories.clear();
        adapter.submitList(new ArrayList<>());
        rankAdapter.submitList(new ArrayList<>());
        mainTitleView.setText(getString(R.string.app_name));
        mainSubtitleView.setText("原生聚合 · 中文界面 · 直接播放");
        updateSourceSummary();
        renderCategories();
        applyTabState();
        syncBottomSelection(R.id.menu_home);
        loadCategories();
        loadHomePage(1);
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
                toast("分类加载失败");
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
        currentPage = 1;
        activeCategory = null;
        applyTabState();
        syncBottomSelection(R.id.menu_home);
        renderCategories();
        if (reload || adapter.getDataCount() == 0) {
            loadHomePage(1);
        } else {
            setSectionTitle("推荐");
            setStatus("当前片源推荐内容");
            showLoading(false, "");
        }
    }

    private void openLibraryTab(boolean reload) {
        currentTab = MainTab.LIBRARY;
        applyTabState();
        syncBottomSelection(R.id.menu_library);
        renderCategories();
        if (categories.isEmpty()) {
            setSectionTitle("片库");
            setStatus("正在加载分类...");
            showLoading(categories.isEmpty(), categories.isEmpty() ? "正在加载分类..." : "");
            loadCategories();
            return;
        }
        if (activeCategory == null) {
            activeCategory = categories.get(0);
        }
        browseMode = BrowseMode.CATEGORY;
        currentKeyword = "";
        if (reload || adapter.getDataCount() == 0 || currentTab != MainTab.LIBRARY) {
            loadCategoryPage(activeCategory, 1);
        } else {
            setSectionTitle(activeCategory.name);
            setStatus("浏览分类内容");
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
            setSectionTitle("热播榜单");
            setStatus("当前片源热门内容");
            showLoading(false, "");
        }
    }

    private void openMineTab() {
        currentTab = MainTab.MINE;
        applyTabState();
        syncBottomSelection(R.id.menu_mine);
        setSectionTitle("我的");
        setStatus("片源、播放器与使用说明");
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
        setSectionTitle("推荐");
        showLoading(true, "正在加载推荐内容...");
        engine.loadRecommend(targetPage, (items, err) -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
                return;
            }
            swipeRefreshLayout.setRefreshing(false);
            if (!TextUtils.isEmpty(err) && (items == null || items.isEmpty())) {
                showLoading(false, "推荐加载失败\n" + err);
                return;
            }
            if (targetPage > 1 && (items == null || items.isEmpty())) {
                showLoading(false, adapter.getDataCount() == 0 ? "没有更多推荐内容" : "");
                toast("没有更多推荐内容了");
                return;
            }
            adapter.submitList(items);
            currentPage = targetPage;
            showLoading(false, items == null || items.isEmpty() ? "当前片源没有返回推荐内容" : "");
            setStatus("推荐内容 · 第 " + currentPage + " 页");
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
        currentKeyword = "";
        applyTabState();
        syncBottomSelection(R.id.menu_library);
        renderCategories();
        setSectionTitle(category.name);
        showLoading(true, "正在加载分类：" + category.name);
        engine.loadCategoryItems(category.url, targetPage, (items, err) -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
                return;
            }
            swipeRefreshLayout.setRefreshing(false);
            if (!TextUtils.isEmpty(err) && (items == null || items.isEmpty())) {
                showLoading(false, "分类加载失败\n" + err);
                return;
            }
            if (targetPage > 1 && (items == null || items.isEmpty())) {
                showLoading(false, adapter.getDataCount() == 0 ? "没有更多分类内容" : "");
                toast("没有更多内容了");
                return;
            }
            adapter.submitList(items);
            currentPage = targetPage;
            showLoading(false, items == null || items.isEmpty() ? "当前分类暂无内容" : "");
            setStatus(category.name + " · 第 " + currentPage + " 页");
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
        setSectionTitle("热播榜单");
        showLoading(true, "正在整理榜单...");
        engine.loadRecommend(targetPage, (items, err) -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
                return;
            }
            swipeRefreshLayout.setRefreshing(false);
            if (!TextUtils.isEmpty(err) && (items == null || items.isEmpty())) {
                showLoading(false, "榜单加载失败\n" + err);
                return;
            }
            if (targetPage > 1 && (items == null || items.isEmpty())) {
                showLoading(false, rankAdapter.getDataCount() == 0 ? "没有更多榜单内容" : "");
                toast("没有更多榜单内容了");
                return;
            }
            rankAdapter.submitList(items);
            currentPage = targetPage;
            showLoading(false, items == null || items.isEmpty() ? "当前片源没有可展示的榜单内容" : "");
            setStatus("热播榜 · 第 " + currentPage + " 页");
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
            toast("请先输入搜索关键词");
            return;
        }
        final int targetPage = Math.max(1, page);
        final int token = ++contentVersion;
        final int sourceToken = sourceVersion;
        currentTab = MainTab.LIBRARY;
        browseMode = BrowseMode.SEARCH;
        currentKeyword = keyword;
        currentPage = targetPage;
        applyTabState();
        syncBottomSelection(R.id.menu_library);
        renderCategories();
        setSectionTitle("搜索：" + keyword);
        showLoading(true, "正在搜索 " + keyword + "...");
        engine.search(keyword, targetPage, (items, err) -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
                return;
            }
            swipeRefreshLayout.setRefreshing(false);
            if (!TextUtils.isEmpty(err) && (items == null || items.isEmpty())) {
                showLoading(false, "搜索失败\n" + err);
                return;
            }
            if (targetPage > 1 && (items == null || items.isEmpty())) {
                showLoading(false, adapter.getDataCount() == 0 ? "没有更多搜索结果" : "");
                toast("没有更多搜索结果了");
                return;
            }
            adapter.submitList(items);
            currentPage = targetPage;
            showLoading(false, items == null || items.isEmpty() ? "没有匹配到搜索结果" : "");
            setStatus("搜索结果 · 第 " + currentPage + " 页");
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
        chip.setTextColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xFF052111, 0xFFD2E7D9}
        ));
        chip.setChipBackgroundColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xFF1FCB63, 0xFF122019}
        ));
        chip.setChipStrokeWidth(dp(1));
        chip.setChipStrokeColor(ColorStateList.valueOf(0xFF28523A));
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

        if (showMine) {
            emptyContainer.setVisibility(View.GONE);
        }
        updatePager();
        updateMinePanel();
    }

    private void syncBottomSelection(int itemId) {
        ignoreBottomSelection = true;
        bottomNavigationView.setSelectedItemId(itemId);
        ignoreBottomSelection = false;
    }

    private void updateSourceSummary() {
        String title = currentSource == null ? "未选择" : currentSource.title;
        String host = currentSource == null || currentSource.host.isEmpty() ? "站点地址未提供" : currentSource.host;
        headerSourceTextView.setText("当前片源：" + title + " · " + host);
        updateMinePanel();
    }

    private void updateMinePanel() {
        String title = currentSource == null ? "片源：加载中" : "片源：" + currentSource.title;
        String host = currentSource == null || currentSource.host.isEmpty() ? "站点：当前源未提供 host" : "站点：" + currentSource.host;
        mineSourceNameView.setText(title);
        mineSourceHostView.setText(host);
        mineFeatureTextView.setText("原生详情页、选集、倍速、长按加速、竖屏播放入口、广告层自动尝试点击与增强嗅探。");
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
                emptyTextView.setText("这里还没有内容");
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
        pageTextView.setText("第 " + currentPage + " 页");
        boolean enabled = currentSource != null && currentTab != MainTab.MINE;
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

    private String readAssetText(String path) throws IOException {
        try (InputStream inputStream = getAssets().open(path);
             InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    private String matchFirst(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private static final class SourceRecord {
        final String fileName;
        final String title;
        final String host;
        final String raw;

        SourceRecord(String fileName, String title, String host, String raw) {
            this.fileName = fileName == null ? "" : fileName;
            this.title = title == null ? "" : title;
            this.host = host == null ? "" : host;
            this.raw = raw == null ? "" : raw;
        }

        NativeSource toNativeSource() {
            return new NativeSource(title, host, raw);
        }

        @NonNull
        @Override
        public String toString() {
            return title;
        }
    }

    private final class SourceAdapter extends ArrayAdapter<SourceRecord> {
        SourceAdapter(ArrayList<SourceRecord> items) {
            super(MainActivity.this, android.R.layout.simple_spinner_item, items);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            styleTextView(view, 0xFFFFFFFF, 0x00000000);
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = super.getDropDownView(position, convertView, parent);
            styleTextView(view, 0xFFFFFFFF, 0xFF13221A);
            return view;
        }

        private void styleTextView(View view, int textColor, int backgroundColor) {
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                textView.setTextColor(textColor);
                textView.setBackgroundColor(backgroundColor);
                textView.setPadding(dp(12), dp(10), dp(12), dp(10));
            }
        }
    }
}
