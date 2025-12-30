package red.jiuzhou.ui.components;

import javafx.animation.FadeTransition;
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
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import red.jiuzhou.analysis.aion.AionMechanismCategory;
import red.jiuzhou.analysis.aion.MechanismFileMapper;
import red.jiuzhou.analysis.aion.MechanismOverrideConfig;
import red.jiuzhou.ui.MechanismOverrideEditorDialog;

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

    // 搜索执行器（虚拟线程）
    private final ExecutorService searchExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // 回调
    private Consumer<TreeItem<T>> onItemSelected;
    private Consumer<TreeItem<T>> onItemDoubleClicked;
    private Consumer<TreeItem<T>> onItemOpen;
    private Function<TreeItem<T>, String> pathResolver;
    private Runnable onRefresh;
    private Consumer<String> onBatchGenerateDdl;  // 批量生成DDL回调
    private Consumer<String> onBatchImportXml;    // 批量导入XML回调

    // 机制过滤
    private MechanismTagBar mechanismTagBar;
    private AionMechanismCategory currentMechanismFilter = null;
    private boolean mechanismFilterEnabled = false;

    public SearchableTreeView() {
        setSpacing(0);

        // 创建机制标签栏（默认隐藏）
        mechanismTagBar = new MechanismTagBar();
        mechanismTagBar.setVisible(false);
        mechanismTagBar.setManaged(false);
        mechanismTagBar.setOnMechanismSelected(this::onMechanismFilterChanged);

        // 创建搜索栏
        HBox searchBar = createSearchBar();

        // 创建树视图
        treeView = new TreeView<>();
        treeView.setShowRoot(true);
        VBox.setVgrow(treeView, Priority.ALWAYS);

        // 创建状态栏
        HBox statusBar = createStatusBar();

        // 布局
        getChildren().addAll(mechanismTagBar, searchBar, treeView, statusBar);

        // 初始化默认搜索匹配器
        searchMatcher = (item, keyword) -> {
            if (item == null) return false;
            return item.toString().toLowerCase().contains(keyword.toLowerCase());
        };

        setupTreeViewListeners();
        setupContextMenu();
    }

    /**
     * 机制过滤变更处理（焦点模式）
     */
    private void onMechanismFilterChanged(AionMechanismCategory category) {
        this.currentMechanismFilter = category;

        // 刷新单元格以更新焦点视觉效果
        refreshTreeCells();

        // 执行过滤搜索
        performFilteredSearch();

        // 如果有焦点机制，自动展开包含匹配文件的文件夹
        if (category != null && originalRoot != null) {
            autoExpandMatchingFolders(originalRoot, category);
        }

        log.debug("机制焦点切换: {}", category != null ? category.getDisplayName() : "全部");
    }

    /**
     * 自动展开包含匹配文件的文件夹
     */
    private void autoExpandMatchingFolders(TreeItem<T> item, AionMechanismCategory mechanism) {
        if (item == null || mechanism == null) return;

        boolean hasMatchingDescendant = hasMatchingDescendant(item, mechanism);

        if (hasMatchingDescendant) {
            // 展开包含匹配文件的文件夹
            item.setExpanded(true);

            // 递归处理子节点
            for (TreeItem<T> child : item.getChildren()) {
                if (!child.isLeaf()) {
                    autoExpandMatchingFolders(child, mechanism);
                }
            }
        } else {
            // 折叠不包含匹配文件的文件夹
            item.setExpanded(false);
        }
    }

    /**
     * 检查节点是否有匹配指定机制的后代
     */
    private boolean hasMatchingDescendant(TreeItem<T> item, AionMechanismCategory mechanism) {
        if (item == null) return false;

        if (item.isLeaf()) {
            // 叶子节点：检查是否匹配
            if (pathResolver != null) {
                String path = pathResolver.apply(item);
                if (path != null && path.toLowerCase().endsWith(".xml")) {
                    AionMechanismCategory fileMech = MechanismFileMapper.detectMechanismStatic(path);
                    return fileMech == mechanism;
                }
            }
            return false;
        }

        // 非叶子节点：递归检查子节点
        for (TreeItem<T> child : item.getChildren()) {
            if (hasMatchingDescendant(child, mechanism)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行带机制过滤的搜索
     */
    private void performFilteredSearch() {
        if (originalRoot == null) return;

        matchedItems.clear();
        currentMatchIndex = -1;
        matchCount = 0;

        // 如果没有任何过滤条件，恢复原始树
        if ((currentSearchText == null || currentSearchText.trim().isEmpty())
            && currentMechanismFilter == null) {
            treeView.setRoot(originalRoot);
            updateStats();
            return;
        }

        // 过滤树
        String keyword = (currentSearchText != null) ? currentSearchText.toLowerCase() : "";
        TreeItem<T> filteredRoot = filterTreeWithMechanism(originalRoot, keyword, currentMechanismFilter);

        if (filteredRoot != null) {
            treeView.setRoot(filteredRoot);
            expandMatchedPaths(filteredRoot);
        } else {
            // 没有匹配项时显示空根
            TreeItem<T> emptyRoot = new TreeItem<>(originalRoot.getValue());
            treeView.setRoot(emptyRoot);
        }

        updateStats();

        // 自动选中第一个匹配项
        if (!matchedItems.isEmpty()) {
            currentMatchIndex = 0;
            selectAndScrollTo(matchedItems.get(0));
        }
    }

    /**
     * 带机制过滤的树过滤
     */
    private TreeItem<T> filterTreeWithMechanism(TreeItem<T> item, String keyword, AionMechanismCategory mechanism) {
        if (item == null) return null;

        boolean keywordMatches = keyword.isEmpty() || searchMatcher.test(item.getValue(), keyword);
        boolean mechanismMatches = checkMechanismMatch(item, mechanism);

        // 检查子节点
        List<TreeItem<T>> filteredChildren = new ArrayList<>();
        for (TreeItem<T> child : item.getChildren()) {
            TreeItem<T> filteredChild = filterTreeWithMechanism(child, keyword, mechanism);
            if (filteredChild != null) {
                filteredChildren.add(filteredChild);
            }
        }

        // 叶子节点：必须同时满足关键词和机制条件
        if (item.isLeaf()) {
            if (keywordMatches && mechanismMatches) {
                TreeItem<T> copy = new TreeItem<>(item.getValue());
                copy.setGraphic(item.getGraphic());
                matchedItems.add(copy);
                matchCount++;
                return copy;
            }
            return null;
        }

        // 非叶子节点：如果有匹配的子节点，则保留
        if (!filteredChildren.isEmpty()) {
            TreeItem<T> copy = new TreeItem<>(item.getValue());
            copy.setGraphic(item.getGraphic());
            copy.setExpanded(true);
            copy.getChildren().addAll(filteredChildren);
            return copy;
        }

        return null;
    }

    /**
     * 检查节点是否匹配机制过滤
     */
    private boolean checkMechanismMatch(TreeItem<T> item, AionMechanismCategory mechanism) {
        if (mechanism == null) return true; // 无机制过滤
        if (pathResolver == null) return true; // 无法解析路径

        String path = pathResolver.apply(item);
        if (path == null || path.isEmpty()) return true;

        // 使用静态方法检测文件机制
        AionMechanismCategory fileMechanism = MechanismFileMapper.detectMechanismStatic(path);
        return fileMechanism == mechanism;
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

        // 数据操作组
        MenuItem generateDdlItem = new MenuItem("⚙️ 生成DDL");
        generateDdlItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && pathResolver != null && onBatchGenerateDdl != null) {
                String path = pathResolver.apply(selected);
                onBatchGenerateDdl.accept(path);
            }
        });

        MenuItem importXmlItem = new MenuItem("📥 导入到数据库");
        importXmlItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && pathResolver != null && onBatchImportXml != null) {
                String path = pathResolver.apply(selected);
                onBatchImportXml.accept(path);
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

        // 机制分类管理组
        MenuItem changeMechanismItem = new MenuItem("🎮 修改机制分类...");
        changeMechanismItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && pathResolver != null) {
                String path = pathResolver.apply(selected);
                File file = new File(path);
                if (file.isFile() && file.getName().toLowerCase().endsWith(".xml")) {
                    changeMechanismClassification(file.getName());
                }
            }
        });

        MenuItem excludeFileItem = new MenuItem("🚫 从机制中排除");
        excludeFileItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && pathResolver != null) {
                String path = pathResolver.apply(selected);
                File file = new File(path);
                if (file.isFile() && file.getName().toLowerCase().endsWith(".xml")) {
                    excludeXmlFile(file.getName());
                }
            }
        });

        MenuItem resetAutoItem = new MenuItem("🔄 重置为自动检测");
        resetAutoItem.setOnAction(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && pathResolver != null) {
                String path = pathResolver.apply(selected);
                File file = new File(path);
                if (file.isFile() && file.getName().toLowerCase().endsWith(".xml")) {
                    resetToAutoDetection(file.getName());
                }
            }
        });

        MenuItem manageAllItem = new MenuItem("⚙️ 管理所有机制分类...");
        manageAllItem.setOnAction(e -> openMechanismManager());

        // 组装菜单（优化结构：核心数据操作前置）
        contextMenu.getItems().addAll(
            openItem,
            openFolderItem,
            openExternalItem,
            new SeparatorMenuItem(),
            generateDdlItem,     // 数据操作前置
            importXmlItem,
            new SeparatorMenuItem(),
            expandItem,
            collapseItem,
            new SeparatorMenuItem(),
            copyPathItem,
            copyNameItem,
            new SeparatorMenuItem(),
            changeMechanismItem,
            excludeFileItem,
            resetAutoItem,
            new SeparatorMenuItem(),
            searchItem,
            refreshItem,
            new SeparatorMenuItem(),
            manageAllItem
        );

        // 动态启用/禁用菜单项并调整文案
        contextMenu.setOnShowing(e -> {
            TreeItem<T> selected = treeView.getSelectionModel().getSelectedItem();
            boolean hasSelection = selected != null;
            boolean isLeaf = hasSelection && selected.isLeaf();
            boolean hasPath = hasSelection && pathResolver != null;

            // 判断是文件还是目录
            boolean isDirectory = false;
            if (hasPath) {
                String path = pathResolver.apply(selected);
                File file = new File(path);
                isDirectory = file.isDirectory();
            }

            // 根据文件/目录类型动态调整菜单文本
            if (isDirectory) {
                generateDdlItem.setText("⚙️ 生成目录DDL...");
                importXmlItem.setText("📥 批量导入到数据库...");
            } else if (isLeaf) {
                generateDdlItem.setText("⚙️ 生成DDL");
                importXmlItem.setText("📥 导入到数据库");
            } else {
                generateDdlItem.setText("⚙️ 生成DDL");
                importXmlItem.setText("📥 导入到数据库");
            }

            openItem.setDisable(!hasSelection);
            openFolderItem.setDisable(!hasPath);
            openExternalItem.setDisable(!hasPath || !isLeaf);
            expandItem.setDisable(!hasSelection || isLeaf);
            collapseItem.setDisable(!hasSelection || isLeaf);
            copyPathItem.setDisable(!hasPath);
            copyNameItem.setDisable(!hasSelection);
            generateDdlItem.setDisable(!hasPath || onBatchGenerateDdl == null);
            importXmlItem.setDisable(!hasPath || onBatchImportXml == null);
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

        // 如果启用了机制过滤，使用组合过滤
        if (mechanismFilterEnabled) {
            performFilteredSearch();
            // 添加到搜索历史
            if (currentSearchText != null && !currentSearchText.trim().isEmpty()) {
                addToHistory(currentSearchText);
            }
            return;
        }

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
        StringBuilder sb = new StringBuilder();
        boolean hasFilter = false;

        // 机制过滤信息
        if (mechanismFilterEnabled && currentMechanismFilter != null) {
            sb.append("[").append(currentMechanismFilter.getDisplayName()).append("] ");
            hasFilter = true;
        }

        if (currentSearchText == null || currentSearchText.isEmpty()) {
            if (hasFilter) {
                if (matchedItems.isEmpty()) {
                    sb.append("无匹配文件");
                    statsLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-size: 11px;");
                } else {
                    sb.append(matchedItems.size()).append(" 个文件");
                    statsLabel.setStyle("-fx-text-fill: #17a2b8; -fx-font-size: 11px;");
                }
            } else {
                int totalNodes = countNodes(originalRoot);
                sb.append("共 ").append(totalNodes).append(" 个节点");
                statsLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");
            }
        } else {
            if (matchedItems.isEmpty()) {
                sb.append("未找到匹配项");
                statsLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-size: 11px;");
            } else {
                sb.append(String.format("找到 %d 个匹配 (%d/%d)",
                        matchedItems.size(), currentMatchIndex + 1, matchedItems.size()));
                statsLabel.setStyle("-fx-text-fill: #28a745; -fx-font-size: 11px;");
            }
        }

        statsLabel.setText(sb.toString());
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
     * 展开所有节点（异步分批优化版本）
     */
    public void expandAll() {
        expandOrCollapseAllAsync(true);
    }

    /**
     * 折叠所有节点（异步分批优化版本）
     */
    public void collapseAll() {
        expandOrCollapseAllAsync(false);
    }

    /**
     * 异步分批展开/折叠所有节点
     * 避免大量节点时阻塞UI线程
     */
    private void expandOrCollapseAllAsync(boolean expand) {
        TreeItem<T> root = treeView.getRoot();
        if (root == null) return;

        // 禁用展开/折叠按钮，防止重复点击
        expandAllBtn.setDisable(true);
        collapseAllBtn.setDisable(true);

        // 收集所有节点
        javafx.concurrent.Task<List<TreeItem<T>>> collectTask = new javafx.concurrent.Task<>() {
            @Override
            protected List<TreeItem<T>> call() {
                List<TreeItem<T>> allItems = new ArrayList<>();
                collectItemsRecursively(root, allItems);
                return allItems;
            }

            @Override
            protected void succeeded() {
                List<TreeItem<T>> allItems = getValue();
                int totalItems = allItems.size();

                if (totalItems == 0) {
                    javafx.application.Platform.runLater(() -> {
                        expandAllBtn.setDisable(false);
                        collapseAllBtn.setDisable(false);
                    });
                    return;
                }

                // 更新状态
                String operation = expand ? "展开" : "折叠";
                javafx.application.Platform.runLater(() -> {
                    statsLabel.setText(String.format("正在%s节点... 0/%d", operation, totalItems));
                    statsLabel.setStyle("-fx-text-fill: #007bff; -fx-font-size: 11px;");
                });

                // 分批处理节点（每批50个）
                int batchSize = 50;
                int[] processed = {0};

                javafx.concurrent.Task<Void> expandTask = new javafx.concurrent.Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        for (int i = 0; i < allItems.size(); i += batchSize) {
                            final int start = i;
                            final int end = Math.min(i + batchSize, allItems.size());
                            final List<TreeItem<T>> batch = allItems.subList(start, end);

                            // 在UI线程中更新节点展开状态
                            javafx.application.Platform.runLater(() -> {
                                for (TreeItem<T> item : batch) {
                                    item.setExpanded(expand);
                                }

                                processed[0] += batch.size();
                                statsLabel.setText(String.format("正在%s节点... %d/%d (%.1f%%)",
                                        operation, processed[0], totalItems,
                                        (processed[0] * 100.0 / totalItems)));
                            });

                            // 短暂休眠，让UI有时间响应
                            Thread.sleep(10);
                        }
                        return null;
                    }

                    @Override
                    protected void succeeded() {
                        javafx.application.Platform.runLater(() -> {
                            statsLabel.setText(String.format("%s完成！共 %,d 个节点", operation, totalItems));
                            statsLabel.setStyle("-fx-text-fill: #28a745; -fx-font-size: 11px;");

                            // 恢复按钮状态
                            expandAllBtn.setDisable(false);
                            collapseAllBtn.setDisable(false);

                            // 3秒后恢复默认状态文本
                            new java.util.Timer().schedule(new java.util.TimerTask() {
                                @Override
                                public void run() {
                                    javafx.application.Platform.runLater(() -> {
                                        statsLabel.setText("就绪");
                                        statsLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");
                                    });
                                }
                            }, 3000);
                        });
                    }

                    @Override
                    protected void failed() {
                        Throwable ex = getException();
                        log.error("节点{}失败: {}", operation, ex.getMessage(), ex);

                        javafx.application.Platform.runLater(() -> {
                            statsLabel.setText(operation + "失败: " + ex.getMessage());
                            statsLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-size: 11px;");

                            expandAllBtn.setDisable(false);
                            collapseAllBtn.setDisable(false);
                        });
                    }
                };

                // 启动展开/折叠任务
                Thread expandThread = new Thread(expandTask);
                expandThread.setDaemon(true);
                expandThread.start();
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                log.error("收集节点失败: {}", ex.getMessage(), ex);

                javafx.application.Platform.runLater(() -> {
                    statsLabel.setText("操作失败");
                    statsLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-size: 11px;");
                    expandAllBtn.setDisable(false);
                    collapseAllBtn.setDisable(false);
                });
            }
        };

        // 启动收集任务
        Thread collectThread = new Thread(collectTask);
        collectThread.setDaemon(true);
        collectThread.start();
    }

    /**
     * 递归收集所有TreeItem（用于分批处理）
     */
    private void collectItemsRecursively(TreeItem<T> item, List<TreeItem<T>> result) {
        if (item == null) return;
        result.add(item);
        for (TreeItem<T> child : item.getChildren()) {
            collectItemsRecursively(child, result);
        }
    }

    /**
     * 递归展开/折叠单个节点及其子节点（同步版本，用于右键菜单）
     */
    private void expandRecursively(TreeItem<T> item, boolean expand) {
        if (item == null) return;

        // 收集所有子节点
        List<TreeItem<T>> allItems = new ArrayList<>();
        collectItemsRecursively(item, allItems);

        // 同步展开/折叠（因为通常是单个文件夹，节点不多）
        for (TreeItem<T> treeItem : allItems) {
            treeItem.setExpanded(expand);
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
     * 设置批量生成DDL回调
     */
    public void setOnBatchGenerateDdl(Consumer<String> handler) {
        this.onBatchGenerateDdl = handler;
    }

    /**
     * 设置批量导入XML回调
     */
    public void setOnBatchImportXml(Consumer<String> handler) {
        this.onBatchImportXml = handler;
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

    // ==================== 机制过滤相关方法 ====================

    /**
     * 启用机制过滤功能
     */
    public void enableMechanismFilter(boolean enable) {
        this.mechanismFilterEnabled = enable;
        mechanismTagBar.setVisible(enable);
        mechanismTagBar.setManaged(enable);

        if (enable) {
            // 刷新标签统计
            mechanismTagBar.refreshTags();
            // 设置机制感知的单元格工厂
            setupMechanismAwareCellFactory();
        } else {
            // 清除机制过滤
            currentMechanismFilter = null;
            if (originalRoot != null) {
                treeView.setRoot(originalRoot);
            }
        }
    }

    /**
     * 设置机制感知的单元格工厂（使用焦点感知版本）
     */
    private void setupMechanismAwareCellFactory() {
        if (pathResolver == null) return;

        // 使用 FocusAwareTreeCell，支持焦点模式的视觉效果
        treeView.setCellFactory(FocusAwareTreeCell.createFactory(
            pathResolver,
            // 过滤回调
            mechanism -> setMechanismFilter(mechanism),
            // 打开机制浏览器回调（可由外部设置）
            null,
            // 传递当前焦点机制
            currentMechanismFilter
        ));
    }

    /**
     * 刷新树视图的单元格（用于更新焦点状态）
     */
    private void refreshTreeCells() {
        if (!mechanismFilterEnabled || pathResolver == null) return;

        // 重新设置单元格工厂以刷新所有单元格的焦点状态
        treeView.setCellFactory(FocusAwareTreeCell.createFactory(
            pathResolver,
            mechanism -> setMechanismFilter(mechanism),
            null,
            currentMechanismFilter
        ));
    }

    /**
     * 获取机制过滤是否启用
     */
    public boolean isMechanismFilterEnabled() {
        return mechanismFilterEnabled;
    }

    /**
     * 获取机制标签栏
     */
    public MechanismTagBar getMechanismTagBar() {
        return mechanismTagBar;
    }

    /**
     * 设置当前机制过滤（焦点）
     */
    public void setMechanismFilter(AionMechanismCategory category) {
        this.currentMechanismFilter = category;

        // 更新标签栏选中状态
        if (mechanismTagBar != null) {
            mechanismTagBar.selectMechanism(category);
        }

        // 刷新单元格以更新焦点视觉效果
        refreshTreeCells();

        // 执行过滤搜索
        performFilteredSearch();

        // 如果有焦点机制，自动展开包含匹配文件的文件夹
        if (category != null && originalRoot != null) {
            autoExpandMatchingFolders(originalRoot, category);
        }

        log.debug("设置机制焦点: {}", category != null ? category.getDisplayName() : "全部");
    }

    /**
     * 获取当前机制过滤
     */
    public AionMechanismCategory getMechanismFilter() {
        return currentMechanismFilter;
    }

    /**
     * 清除机制过滤
     */
    public void clearMechanismFilter() {
        currentMechanismFilter = null;
        if (mechanismTagBar != null) {
            mechanismTagBar.clearSelection();
        }

        // 刷新单元格
        refreshTreeCells();

        performFilteredSearch();
        log.debug("清除机制焦点");
    }

    /**
     * 从文件路径反向定位到机制
     */
    public void highlightFileMechanism(String filePath) {
        if (filePath == null || !mechanismFilterEnabled) return;

        AionMechanismCategory category = MechanismFileMapper.detectMechanismStatic(filePath);
        if (mechanismTagBar != null) {
            mechanismTagBar.highlightMechanism(category);
        }
    }

    /**
     * 扫描目录建立机制映射
     */
    public void scanDirectoryForMechanisms(String rootPath) {
        MechanismFileMapper.getInstance().scanDirectory(rootPath);
        if (mechanismTagBar != null && mechanismFilterEnabled) {
            mechanismTagBar.refreshTags();
        }
    }

    // ==================== 机制分类管理方法 ====================

    /**
     * 修改文件的机制分类
     */
    private void changeMechanismClassification(String fileName) {
        ChoiceDialog<AionMechanismCategory> dialog = new ChoiceDialog<AionMechanismCategory>();
        dialog.setTitle("修改机制分类");
        dialog.setHeaderText("文件: " + fileName);
        dialog.setContentText("选择新的机制分类:");

        // 添加所有机制分类（除了OTHER）
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            if (category != AionMechanismCategory.OTHER) {
                dialog.getItems().add(category);
            }
        }

        // 获取当前分类并设置为默认选项
        AionMechanismCategory currentCategory = MechanismFileMapper.detectMechanismStatic(fileName);
        if (currentCategory != null && currentCategory != AionMechanismCategory.OTHER) {
            dialog.setSelectedItem(currentCategory);
        }

        Optional<AionMechanismCategory> result = dialog.showAndWait();
        result.ifPresent(category -> {
            try {
                MechanismOverrideConfig config = MechanismOverrideConfig.getInstance();
                config.addOverride(fileName, category);
                config.save();

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("分类已更新");
                alert.setHeaderText("机制分类已修改");
                alert.setContentText(String.format(
                        "文件: %s\n新分类: %s\n\n⚠️ 请重启应用以使更改生效",
                        fileName, category.getDisplayName()));
                alert.showAndWait();

                log.info("已修改文件分类: {} -> {}", fileName, category.getDisplayName());
            } catch (Exception e) {
                log.error("修改分类失败", e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("修改失败");
                alert.setHeaderText("无法修改机制分类");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        });
    }

    /**
     * 从机制中排除文件
     */
    private void excludeXmlFile(String fileName) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认排除");
        confirmAlert.setHeaderText("文件: " + fileName);
        confirmAlert.setContentText("确定要从所有机制分类中排除此文件吗？\n排除后，该文件将不再出现在任何机制分类中。");

        Optional<ButtonType> confirmation = confirmAlert.showAndWait();
        if (confirmation.isPresent() && confirmation.get() == ButtonType.OK) {
            try {
                MechanismOverrideConfig config = MechanismOverrideConfig.getInstance();
                config.addExcluded(fileName);
                config.save();

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("已排除文件");
                alert.setHeaderText("文件已从机制分类中排除");
                alert.setContentText(String.format(
                        "文件: %s\n\n⚠️ 请重启应用以使更改生效",
                        fileName));
                alert.showAndWait();

                log.info("已排除文件: {}", fileName);
            } catch (Exception e) {
                log.error("排除文件失败", e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("排除失败");
                alert.setHeaderText("无法排除文件");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }

    /**
     * 重置为自动检测
     */
    private void resetToAutoDetection(String fileName) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认重置");
        confirmAlert.setHeaderText("文件: " + fileName);
        confirmAlert.setContentText("确定要移除此文件的手动分类设置吗？\n重置后，系统将使用自动检测来确定文件的机制分类。");

        Optional<ButtonType> confirmation = confirmAlert.showAndWait();
        if (confirmation.isPresent() && confirmation.get() == ButtonType.OK) {
            try {
                MechanismOverrideConfig config = MechanismOverrideConfig.getInstance();
                config.removeOverride(fileName);
                config.removeExcluded(fileName);
                config.save();

                // 检测自动分类
                AionMechanismCategory autoCategory = MechanismFileMapper.detectMechanismStatic(fileName);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("已重置分类");
                alert.setHeaderText("文件已重置为自动检测");
                alert.setContentText(String.format(
                        "文件: %s\n自动检测分类: %s\n\n⚠️ 请重启应用以使更改生效",
                        fileName, autoCategory.getDisplayName()));
                alert.showAndWait();

                log.info("已重置文件分类: {} -> 自动检测({})", fileName, autoCategory.getDisplayName());
            } catch (Exception e) {
                log.error("重置分类失败", e);
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("重置失败");
                alert.setHeaderText("无法重置分类");
                alert.setContentText(e.getMessage());
                alert.showAndWait();
            }
        }
    }

    /**
     * 打开机制管理器
     */
    private void openMechanismManager() {
        try {
            MechanismOverrideEditorDialog dialog = new MechanismOverrideEditorDialog();
            dialog.show();
            log.info("打开机制分类管理器");
        } catch (Exception e) {
            log.error("打开管理器失败", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("打开失败");
            alert.setHeaderText("无法打开机制分类管理器");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }
}
