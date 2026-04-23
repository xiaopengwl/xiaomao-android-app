package com.xiaomao.player;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
        SEARCH
    }

    private static final Pattern TITLE_PATTERN = Pattern.compile("title\\s*:\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern HOST_PATTERN = Pattern.compile("host\\s*:\\s*['\"]([^'\"]+)['\"]");

    private TextView mainTitleView;
    private TextView mainSubtitleView;
    private TextView sectionTitleView;
    private TextView statusTextView;
    private TextView pageTextView;
    private TextView emptyTextView;
    private View emptyContainer;
    private View loadingIndicator;
    private Spinner sourceSpinner;
    private MaterialButton refreshButton;
    private MaterialButton searchButton;
    private MaterialButton homeButton;
    private MaterialButton prevButton;
    private MaterialButton nextButton;
    private TextInputEditText searchInput;
    private ChipGroup categoryGroup;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView mediaRecyclerView;

    private final ArrayList<SourceRecord> sources = new ArrayList<>();
    private final ArrayList<NativeDrpyEngine.Category> categories = new ArrayList<>();
    private final MediaGridAdapter adapter = new MediaGridAdapter();

    private NativeDrpyEngine engine;
    private SourceRecord currentSource;
    private NativeDrpyEngine.Category activeCategory;
    private BrowseMode browseMode = BrowseMode.HOME;
    private String currentKeyword = "";
    private int currentPage = 1;
    private int sourceVersion = 0;
    private int contentVersion = 0;
    private boolean ignoreSourceSelection = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupRecycler();
        setupEvents();
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
        sectionTitleView = findViewById(R.id.section_title);
        statusTextView = findViewById(R.id.status_text);
        pageTextView = findViewById(R.id.page_text);
        emptyTextView = findViewById(R.id.empty_text);
        emptyContainer = findViewById(R.id.empty_container);
        loadingIndicator = findViewById(R.id.loading_indicator);
        sourceSpinner = findViewById(R.id.source_spinner);
        refreshButton = findViewById(R.id.refresh_button);
        searchButton = findViewById(R.id.search_button);
        homeButton = findViewById(R.id.home_button);
        prevButton = findViewById(R.id.prev_button);
        nextButton = findViewById(R.id.next_button);
        searchInput = findViewById(R.id.search_input);
        categoryGroup = findViewById(R.id.category_group);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        mediaRecyclerView = findViewById(R.id.media_recycler);
    }

    private void setupRecycler() {
        GridLayoutManager layoutManager = new GridLayoutManager(this, computeSpanCount());
        mediaRecyclerView.setLayoutManager(layoutManager);
        mediaRecyclerView.setAdapter(adapter);
        adapter.setOnItemClickListener(this::openDetail);
    }

    private void setupEvents() {
        refreshButton.setOnClickListener(v -> reloadCurrentPage(true));
        searchButton.setOnClickListener(v -> performSearch(1));
        homeButton.setOnClickListener(v -> {
            browseMode = BrowseMode.HOME;
            activeCategory = null;
            currentPage = 1;
            renderCategories();
            loadHomePage(1);
        });
        prevButton.setOnClickListener(v -> changePage(-1));
        nextButton.setOnClickListener(v -> changePage(1));
        swipeRefreshLayout.setOnRefreshListener(() -> reloadCurrentPage(true));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enterPressed = event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
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
    }

    private void loadSources() {
        try {
            String[] names = getAssets().list("sources");
            if (names == null) {
                showLoading(false, "未找到内置片源");
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
            showLoading(false, "片源载入失败：" + e.getMessage());
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
        browseMode = BrowseMode.HOME;
        activeCategory = null;
        currentKeyword = "";
        currentPage = 1;
        categories.clear();
        adapter.submitList(new ArrayList<>());
        mainTitleView.setText(record.title);
        mainSubtitleView.setText(record.host.isEmpty() ? "当前片源未声明 host" : record.host);
        renderCategories();
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
        });
    }

    private void loadHomePage(int page) {
        if (engine == null) {
            return;
        }
        final int targetPage = Math.max(1, page);
        final int token = ++contentVersion;
        final int sourceToken = sourceVersion;
        browseMode = BrowseMode.HOME;
        activeCategory = null;
        renderCategories();
        setSectionTitle("首页推荐");
        showLoading(true, "正在加载首页推荐...");
        engine.loadRecommend(targetPage, (items, err) -> {
            if (token != contentVersion || sourceToken != sourceVersion) {
                return;
            }
            swipeRefreshLayout.setRefreshing(false);
            if (!TextUtils.isEmpty(err) && (items == null || items.isEmpty())) {
                showLoading(false, "首页推荐加载失败\n" + err);
                return;
            }
            if (targetPage > 1 && (items == null || items.isEmpty())) {
                showLoading(false, adapter.getItemCount() == 0 ? "没有更多推荐内容" : "");
                toast("没有更多推荐了");
                return;
            }
            currentPage = targetPage;
            adapter.submitList(items);
            showLoading(false, items == null || items.isEmpty() ? "当前片源没有返回推荐内容" : "");
            setStatus("首页推荐 · 第 " + currentPage + " 页");
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
        browseMode = BrowseMode.CATEGORY;
        activeCategory = category;
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
                showLoading(false, adapter.getItemCount() == 0 ? "没有更多分类内容" : "");
                toast("没有更多内容了");
                return;
            }
            currentPage = targetPage;
            adapter.submitList(items);
            showLoading(false, items == null || items.isEmpty() ? "当前分类没有内容" : "");
            setStatus(category.name + " · 第 " + currentPage + " 页");
            updatePager();
            mediaRecyclerView.scrollToPosition(0);
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
        browseMode = BrowseMode.SEARCH;
        currentKeyword = keyword;
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
                showLoading(false, adapter.getItemCount() == 0 ? "没有更多搜索结果" : "");
                toast("没有更多搜索结果了");
                return;
            }
            currentPage = targetPage;
            adapter.submitList(items);
            showLoading(false, items == null || items.isEmpty() ? "没有匹配到搜索结果" : "");
            setStatus("搜索 " + keyword + " · 第 " + currentPage + " 页");
            updatePager();
            mediaRecyclerView.scrollToPosition(0);
        });
    }

    private void reloadCurrentPage(boolean userInitiated) {
        if (engine == null) {
            return;
        }
        if (userInitiated) {
            swipeRefreshLayout.setRefreshing(true);
        }
        if (browseMode == BrowseMode.SEARCH) {
            performSearch(currentPage);
        } else if (browseMode == BrowseMode.CATEGORY && activeCategory != null) {
            loadCategoryPage(activeCategory, currentPage);
        } else {
            loadHomePage(currentPage);
        }
    }

    private void changePage(int delta) {
        if (engine == null) {
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
        } else {
            loadHomePage(targetPage);
        }
    }

    private void renderCategories() {
        categoryGroup.removeAllViews();
        Chip homeChip = buildChip("推荐", browseMode == BrowseMode.HOME);
        homeChip.setOnClickListener(v -> {
            browseMode = BrowseMode.HOME;
            activeCategory = null;
            currentPage = 1;
            renderCategories();
            loadHomePage(1);
        });
        categoryGroup.addView(homeChip);
        for (NativeDrpyEngine.Category category : categories) {
            Chip chip = buildChip(category.name, activeCategory != null && TextUtils.equals(activeCategory.url, category.url));
            chip.setOnClickListener(v -> {
                activeCategory = category;
                browseMode = BrowseMode.CATEGORY;
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
                new int[]{0xFFFFFFFF, 0xFFD6E3F7}
        ));
        chip.setChipBackgroundColor(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xFFE44F4F, 0xFF172230}
        ));
        chip.setChipStrokeWidth(dp(1));
        chip.setChipStrokeColor(ColorStateList.valueOf(0xFF304257));
        return chip;
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
        if (loading) {
            emptyContainer.setVisibility(View.VISIBLE);
            loadingIndicator.setVisibility(View.VISIBLE);
            emptyTextView.setText(message);
        } else if (!TextUtils.isEmpty(message)) {
            emptyContainer.setVisibility(View.VISIBLE);
            loadingIndicator.setVisibility(View.GONE);
            emptyTextView.setText(message);
        } else {
            emptyContainer.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            loadingIndicator.setVisibility(View.GONE);
            if (adapter.getItemCount() == 0) {
                emptyTextView.setText("这里还没有内容");
            }
        }
        updatePager();
    }

    private void updatePager() {
        pageTextView.setText("第 " + currentPage + " 页");
        prevButton.setEnabled(currentPage > 1);
        boolean hasSource = currentSource != null;
        nextButton.setEnabled(hasSource);
        homeButton.setEnabled(hasSource);
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
            styleTextView(view, 0xFFFFFFFF, 0xFF15202D);
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
