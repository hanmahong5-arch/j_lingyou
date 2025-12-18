package red.jiuzhou.ui.components;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 可搜索树视图组件
 *
 * <p>增强TreeView，支持：
 * <ul>
 *   <li>实时搜索过滤（支持多层级）</li>
 *   <li>高亮匹配项</li>
 *   <li>搜索历史</li>
 *   <li>展开/折叠控制</li>
 *   <li>快捷键支持</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class SearchableTreeView<T> extends VBox {

    private static final Logger log = LoggerFactory.getLogger(SearchableTreeView.class);

    // UI组件
    private TextField searchField;
    private TreeView<T> treeView;
    private Label statsLabel;
    private Button expandAllBtn;
    private Button collapseAllBtn;
    private Button clearBtn;

    // 数据
    private TreeItem<T> originalRoot;
    private final ObservableList<String> searchHistory = FXCollections.observableArrayList();
    private static final int MAX_HISTORY = 20;

    // 搜索配置
    private BiPredicate<T, String> searchMatcher;
    private String currentSearchText = "";
    private int matchCount = 0;

    // 搜索结果导航
    private List<TreeItem<T>> matchedItems = new ArrayList<>();
    private int currentMatchIndex = -1;

    // 线程池
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();

    // 回调
    private Consumer<TreeItem<T>> onItemSelected;
    private Consumer<TreeItem<T>> onItemDoubleClicked;
    private Consumer<TreeItem<T>> onItemOpen;
    private Function<TreeItem<T>, String> pathResolver;
    private Runnable onRefresh;

    public SearchableTreeView() {
        setSpacing(0);

        // 创建搜索栏
        HBox searchBar = createSearchBar();

        // 创建树视图
        treeView = new TreeView<>();
        treeView.setShowRoot(true);
        VBox.setVgrow(treeView, Priority.ALWAYS);

        // 创建状态栏
        HBox statusBar = createStatusBar();

        // 布局
        getChildren().addAll(searchBar, treeView, statusBar);

        // 初始化默认搜索匹配器
        searchMatcher = (item, keyword) -> {
            if (item == null) return false;
            return item.toString().toLowerCase().contains(keyword.toLowerCase());
        };

        setupTreeViewListeners();
        setupContextMenu();
    }

    /**
     * 设置右键菜单
     */
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        // 打开组
        MenuItem openItem = new MenuItem("📄 打开");
        openItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                if (onItemOpen != null) {
                    onItemOpen.accept(selected);
                } else if (onItemDoubleClicked != null) {
                    onItemDoubleClicked.accept(selected);
                }
            }
        });

        MenuItem openFolderItem = new MenuItem("📁 在资源管理器中显示");
        openFolderItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && pathResolver != null) {
                String path = pathResolver.apply(selected);
                ContextMenuFactory.openInExplorer(path);
            }
        });

        MenuItem openExternalItem = new MenuItem("🔗 使用外部程序打开");
        openExternalItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && pathResolver != null) {
                String path = pathResolver.apply(selected);
                ContextMenuFactory.openWithDesktop(path);
            }
        });

        // 展开/折叠组
        MenuItem expandItem = new MenuItem("📂 展开此项");
        expandItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                expandRecursively(selected, true);
            }
        });

        MenuItem collapseItem = new MenuItem("📁 折叠此项");
        collapseItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                expandRecursively(selected, false);
            }
        });

        MenuItem expandAllItem = new MenuItem("📂 全部展开");
        expandAllItem.setOnAction(e -> expandAll());

        MenuItem collapseAllItem = new MenuItem("📁 全部折叠");
        collapseAllItem.setOnAction(e -> collapseAll());

        // 复制组
        MenuItem copyPathItem = new MenuItem("📋 复制路径");
        copyPathItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && pathResolver != null) {
                String path = pathResolver.apply(selected);
                ContextMenuFactory.copyToClipboard(path);
                log.info("已复制路径: {}", path);
            }
        });

        MenuItem copyNameItem = new MenuItem("📝 复制名称");
        copyNameItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                String name = selected.getValue().toString();
                ContextMenuFactory.copyToClipboard(name);
                log.info("已复制名称: {}", name);
            }
        });

        // 搜索组
        MenuItem searchItem = new MenuItem("🔍 搜索... (Ctrl+F)");
        searchItem.setOnAction(e -> focusSearchField());

        // 刷新
        MenuItem refreshItem = new MenuItem("🔄 刷新");
        refreshItem.setOnAction(e -> {
            if (onRefresh != null) {
                onRefresh.run();
            }
        });

        // 组装菜单
        contextMenu.getItems().addAll(
            openItem,
            openFolderItem,
            openExternalItem,
            new SeparatorMenuItem(),
            expandItem,
            collapseItem,
            expandAllItem,
            collapseAllItem,
            new SeparatorMenuItem(),
            copyPathItem,
            copyNameItem,
            new SeparatorMenuItem(),
            searchItem,
            refreshItem
        );

        // 动态启用/禁用菜单项
        contextMenu.setOnShowing(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            boolean hasSelection = selected != null;
            boolean isLeaf = hasSelection && selected.isLeaf();
            boolean hasPath = hasSelection && pathResolver != null;

            openItem.setDisable(!hasSelection);
            openFolderItem.setDisable(!hasPath);
            openExternalItem.setDisable(!hasPath || !isLeaf);
            expandItem.setDisable(!hasSelection || isLeaf);
            collapseItem.setDisable(!hasSelection || isLeaf);
            copyPathItem.setDisable(!hasPath);
            copyNameItem.setDisable(!hasSelection);
            refreshItem.setDisable(onRefresh == null);
        });

        treeView.setContextMenu(contextMenu);
    }

    /**
     * 创建搜索栏
     */
    private HBox createSearchBar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 10, 8, 10));
        bar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e9ecef; -fx-border-width: 0 0 1 0;");

        // 搜索图标
        Label searchIcon = new Label("\uD83D\uDD0D");
        searchIcon.setStyle("-fx-font-size: 13px;");

        // 搜索输入框
        searchField = new TextField();
        searchField.setPromptText("输入关键词搜索菜单... (Ctrl+F)");
        searchField.setPrefWidth(200);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setStyle("-fx-font-size: 12px;");

        // 实时搜索（带防抖）
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            currentSearchText = newVal;
            debounceSearch();
        });

        // Enter键导航到下一个匹配项
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                navigateToNextMatch();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                clearSearch();
                treeView.requestFocus();
            }
        });

        // 清空按钮
        clearBtn = new Button("×");
        clearBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #6c757d; -fx-font-size: 14px; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> clearSearch());
        clearBtn.setVisible(false);

        // 导航按钮
        Button prevBtn = new Button("↑");
        prevBtn.setTooltip(new Tooltip("上一个匹配项"));
        prevBtn.setStyle("-fx-background-color: #e9ecef; -fx-font-size: 11px; -fx-padding: 2 6;");
        prevBtn.setOnAction(e -> navigateToPreviousMatch());

        Button nextBtn = new Button("↓");
        nextBtn.setTooltip(new Tooltip("下一个匹配项"));
        nextBtn.setStyle("-fx-background-color: #e9ecef; -fx-font-size: 11px; -fx-padding: 2 6;");
        nextBtn.setOnAction(e -> navigateToNextMatch());

        // 展开/折叠按钮
        expandAllBtn = new Button("全部展开");
        expandAllBtn.setStyle("-fx-background-color: #17a2b8; -fx-text-fill: white; -fx-font-size: 10px;");
        expandAllBtn.setOnAction(e -> expandAll());

        collapseAllBtn = new Button("全部折叠");
        collapseAllBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-font-size: 10px;");
        collapseAllBtn.setOnAction(e -> collapseAll());

        bar.getChildren().addAll(searchIcon, searchField, clearBtn, prevBtn, nextBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL), expandAllBtn, collapseAllBtn);

        return bar;
    }

    /**
     * 创建状态栏
     */
    private HBox createStatusBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(5, 10, 5, 10));
        bar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e9ecef; -fx-border-width: 1 0 0 0;");

        statsLabel = new Label("就绪");
        statsLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");

        bar.getChildren().add(statsLabel);
        return bar;
    }

    /**
     * 设置树视图监听器
     */
    private void setupTreeViewListeners() {
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (onItemSelected != null && newVal != null) {
                onItemSelected.accept(newVal);
            }
        });

        treeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && onItemDoubleClicked != null) {
                TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    onItemDoubleClicked.accept(selected);
                }
            }
        });

        // Ctrl+F 聚焦搜索框
        treeView.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.F) {
                searchField.requestFocus();
                searchField.selectAll();
            }
        });
    }

    /**
     * 防抖搜索
     */
    private long lastSearchTime = 0;
    private void debounceSearch() {
        lastSearchTime = System.currentTimeMillis();
        clearBtn.setVisible(!currentSearchText.isEmpty());

        searchExecutor.submit(() -> {
            try {
                Thread.sleep(150);  // 150ms 防抖
                if (System.currentTimeMillis() - lastSearchTime >= 150) {
                    Platform.runLater(this::performSearch);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 执行搜索
     */
    private void performSearch() {
        if (originalRoot == null) return;

        matchedItems.clear();
        currentMatchIndex = -1;

        if (currentSearchText == null || currentSearchText.trim().isEmpty()) {
            // 恢复原始树
            treeView.setRoot(originalRoot);
            matchCount = 0;
            updateStats();
            return;
        }

        // 添加到搜索历史
        addToHistory(currentSearchText);

        // 过滤并高亮
        TreeItem<T> filteredRoot = filterTree(originalRoot, currentSearchText.toLowerCase());
        if (filteredRoot != null) {
            treeView.setRoot(filteredRoot);
            // 展开所有匹配的路径
            expandMatchedPaths(filteredRoot);
        }

        updateStats();

        // 自动选中第一个匹配项
        if (!matchedItems.isEmpty()) {
            currentMatchIndex = 0;
            selectAndScrollTo(matchedItems.get(0));
        }
    }

    /**
     * 过滤树（递归）
     */
    private TreeItem<T> filterTree(TreeItem<T> item, String keyword) {
        if (item == null) return null;

        boolean itemMatches = searchMatcher.test(item.getValue(), keyword);

        // 检查子节点
        List<TreeItem<T>> filteredChildren = new ArrayList<>();
        for (TreeItem<T> child : item.getChildren()) {
            TreeItem<T> filteredChild = filterTree(child, keyword);
            if (filteredChild != null) {
                filteredChildren.add(filteredChild);
            }
        }

        // 如果当前节点匹配或有匹配的子节点，则保留
        if (itemMatches || !filteredChildren.isEmpty()) {
            TreeItem<T> copy = new TreeItem<>(item.getValue());
            copy.setGraphic(item.getGraphic());
            copy.setExpanded(true);

            if (itemMatches) {
                matchedItems.add(copy);
                matchCount++;
            }

            // 添加过滤后的子节点
            copy.getChildren().addAll(filteredChildren);
            return copy;
        }

        return null;
    }

    /**
     * 展开匹配路径
     */
    private void expandMatchedPaths(TreeItem<T> item) {
        if (item == null) return;
        item.setExpanded(true);
        for (TreeItem<T> child : item.getChildren()) {
            expandMatchedPaths(child);
        }
    }

    /**
     * 导航到下一个匹配项
     */
    public void navigateToNextMatch() {
        if (matchedItems.isEmpty()) return;

        currentMatchIndex = (currentMatchIndex + 1) % matchedItems.size();
        selectAndScrollTo(matchedItems.get(currentMatchIndex));
        updateStats();
    }

    /**
     * 导航到上一个匹配项
     */
    public void navigateToPreviousMatch() {
        if (matchedItems.isEmpty()) return;

        currentMatchIndex = currentMatchIndex - 1;
        if (currentMatchIndex < 0) {
            currentMatchIndex = matchedItems.size() - 1;
        }
        selectAndScrollTo(matchedItems.get(currentMatchIndex));
        updateStats();
    }

    /**
     * 选中并滚动到指定项
     */
    private void selectAndScrollTo(TreeItem<T> item) {
        if (item == null) return;

        // 确保父节点都展开
        TreeItem<T> parent = item.getParent();
        while (parent != null) {
            parent.setExpanded(true);
            parent = parent.getParent();
        }

        // 选中并滚动
        treeView.getSelectionModel().select(item);
        int index = treeView.getRow(item);
        if (index >= 0) {
            treeView.scrollTo(index);
        }
    }

    /**
     * 清空搜索
     */
    public void clearSearch() {
        searchField.clear();
        currentSearchText = "";
        matchedItems.clear();
        currentMatchIndex = -1;
        matchCount = 0;
        clearBtn.setVisible(false);

        if (originalRoot != null) {
            treeView.setRoot(originalRoot);
        }
        updateStats();
    }

    /**
     * 更新统计信息
     */
    private void updateStats() {
        if (currentSearchText == null || currentSearchText.isEmpty()) {
            int totalNodes = countNodes(originalRoot);
            statsLabel.setText("共 " + totalNodes + " 个节点");
        } else {
            if (matchedItems.isEmpty()) {
                statsLabel.setText("未找到匹配项");
                statsLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-size: 11px;");
            } else {
                statsLabel.setText(String.format("找到 %d 个匹配 (%d/%d)",
                        matchedItems.size(), currentMatchIndex + 1, matchedItems.size()));
                statsLabel.setStyle("-fx-text-fill: #28a745; -fx-font-size: 11px;");
            }
        }
    }

    /**
     * 计算节点总数
     */
    private int countNodes(TreeItem<T> item) {
        if (item == null) return 0;
        int count = 1;
        for (TreeItem<T> child : item.getChildren()) {
            count += countNodes(child);
        }
        return count;
    }

    /**
     * 添加到搜索历史
     */
    private void addToHistory(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return;

        searchHistory.remove(keyword);
        searchHistory.add(0, keyword);

        while (searchHistory.size() > MAX_HISTORY) {
            searchHistory.remove(searchHistory.size() - 1);
        }
    }

    /**
     * 展开所有节点
     */
    public void expandAll() {
        expandRecursively(treeView.getRoot(), true);
    }

    /**
     * 折叠所有节点
     */
    public void collapseAll() {
        expandRecursively(treeView.getRoot(), false);
    }

    /**
     * 递归展开/折叠
     */
    private void expandRecursively(TreeItem<T> item, boolean expand) {
        if (item == null) return;
        item.setExpanded(expand);
        for (TreeItem<T> child : item.getChildren()) {
            expandRecursively(child, expand);
        }
    }

    // ==================== Getters & Setters ====================

    /**
     * 获取内部TreeView
     */
    public TreeView<T> getTreeView() {
        return treeView;
    }

    /**
     * 设置根节点
     */
    public void setRoot(TreeItem<T> root) {
        this.originalRoot = root;
        treeView.setRoot(root);
        updateStats();
    }

    /**
     * 获取根节点
     */
    public TreeItem<T> getRoot() {
        return originalRoot;
    }

    /**
     * 设置搜索匹配器
     */
    public void setSearchMatcher(BiPredicate<T, String> matcher) {
        this.searchMatcher = matcher;
    }

    /**
     * 设置单元格工厂
     */
    public void setCellFactory(javafx.util.Callback<TreeView<T>, TreeCell<T>> factory) {
        treeView.setCellFactory(factory);
    }

    /**
     * 设置选择监听器
     */
    public void setOnItemSelected(Consumer<TreeItem<T>> handler) {
        this.onItemSelected = handler;
    }

    /**
     * 设置双击监听器
     */
    public void setOnItemDoubleClicked(Consumer<TreeItem<T>> handler) {
        this.onItemDoubleClicked = handler;
    }

    /**
     * 设置打开监听器（右键菜单"打开"操作）
     */
    public void setOnItemOpen(Consumer<TreeItem<T>> handler) {
        this.onItemOpen = handler;
    }

    /**
     * 设置路径解析器（用于右键菜单的路径相关操作）
     */
    public void setPathResolver(Function<TreeItem<T>, String> resolver) {
        this.pathResolver = resolver;
    }

    /**
     * 设置刷新回调
     */
    public void setOnRefresh(Runnable handler) {
        this.onRefresh = handler;
    }

    /**
     * 获取搜索历史
     */
    public ObservableList<String> getSearchHistory() {
        return searchHistory;
    }

    /**
     * 聚焦搜索框
     */
    public void focusSearchField() {
        searchField.requestFocus();
        searchField.selectAll();
    }

    /**
     * 获取选中项
     */
    public TreeItem<T> getSelectedItem() {
        return treeView.getSelectionModel().getSelectedItem();
    }

    /**
     * 设置显示根节点
     */
    public void setShowRoot(boolean show) {
        treeView.setShowRoot(show);
    }

    /**
     * 释放资源
     */
    public void dispose() {
        searchExecutor.shutdownNow();
    }
}
