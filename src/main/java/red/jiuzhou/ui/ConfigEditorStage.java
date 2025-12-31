package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.config.ConfigFileEntry;
import red.jiuzhou.config.ConfigFileService;

import java.awt.Desktop;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.util.Duration;

/**
 * 配置文件编辑器 v2.0
 *
 * <p>提供配置文件的可视化查看和编辑功能。
 *
 * <p>功能特性：
 * <ul>
 *   <li>分类分组的文件列表</li>
 *   <li>带行号的代码编辑器</li>
 *   <li>搜索和跳转功能</li>
 *   <li>实时格式验证</li>
 *   <li>备份差异对比</li>
 *   <li>YAML/JSON结构预览</li>
 *   <li>右键菜单和快捷键</li>
 * </ul>
 *
 * @author Claude
 * @version 2.0
 */
public class ConfigEditorStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(ConfigEditorStage.class);

    private final ConfigFileService configService;

    // UI组件
    private TreeView<Object> fileTreeView;
    private TextField searchField;
    private TextArea editorArea;
    private VBox lineNumberPane;
    private ScrollPane editorScrollPane;
    private Label statusLabel;
    private Label cursorPosLabel;
    private Label charCountLabel;
    private Label fileInfoLabel;
    private Label validationLabel;
    private Button saveButton;
    private Button reloadButton;
    private Button backupButton;
    private Button formatButton;
    private Button exportButton;
    private CheckBox showSensitiveCheckbox;
    private TreeView<String> structureTree;
    private VBox infoPane;
    private HBox searchBar;

    // 当前状态
    private ConfigFileEntry currentEntry;
    private String originalContent;
    private boolean showSensitive = false;
    private boolean searchBarVisible = false;

    // 时间格式
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ConfigEditorStage() {
        this.configService = new ConfigFileService();

        setTitle("配置文件管理器");
        initModality(Modality.NONE);
        setWidth(1200);
        setHeight(750);

        initUI();
        loadConfigTree();
    }

    private void initUI() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #f0f0f0;");

        // 左侧 - 配置文件树
        VBox leftPane = createFileTreePane();
        leftPane.setPrefWidth(260);
        leftPane.setMinWidth(220);

        // 中间 - 编辑器区域
        BorderPane centerPane = createEditorPane();

        // 右侧 - 信息面板
        infoPane = createInfoPane();
        infoPane.setPrefWidth(220);

        // 分隔器
        SplitPane mainSplit = new SplitPane(leftPane, centerPane, infoPane);
        mainSplit.setDividerPositions(0.20, 0.78);

        root.setCenter(mainSplit);

        // 底部状态栏
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        Scene scene = new Scene(root);

        // 注册快捷键
        registerShortcuts(scene);

        // 应用样式
        applyStyles(scene);

        setScene(scene);
    }

    private void registerShortcuts(Scene scene) {
        // Ctrl+S 保存
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                this::saveCurrentConfig);

        // Ctrl+F 搜索
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                this::toggleSearchBar);

        // Ctrl+G 跳转到行
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN),
                this::showGotoLineDialog);

        // Ctrl+Shift+F 格式化
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::formatContent);

        // Escape 关闭搜索栏
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE && searchBarVisible) {
                toggleSearchBar();
                e.consume();
            }
        });
    }

    private void applyStyles(Scene scene) {
        // 可以加载外部CSS，这里用内联样式
    }

    // ==================== 左侧文件树 ====================

    private VBox createFileTreePane() {
        VBox pane = new VBox(8);
        pane.setPadding(new Insets(8));
        pane.setStyle("-fx-background-color: #fafafa; -fx-border-color: #ddd; -fx-border-width: 0 1 0 0;");

        // 标题栏
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("配置文件");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("⟳");
        refreshBtn.setTooltip(new Tooltip("刷新列表"));
        refreshBtn.setStyle("-fx-font-size: 14px; -fx-padding: 2 6;");
        refreshBtn.setOnAction(e -> {
            configService.refresh();
            loadConfigTree();
            showStatus("配置列表已刷新", false);
        });

        header.getChildren().addAll(title, spacer, refreshBtn);

        // 搜索框
        searchField = new TextField();
        searchField.setPromptText("搜索配置...");
        searchField.textProperty().addListener((obs, old, text) -> filterTree(text));

        // 文件树
        fileTreeView = new TreeView<>();
        fileTreeView.setShowRoot(false);
        fileTreeView.setCellFactory(tv -> new ConfigTreeCell());
        fileTreeView.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> {
                    if (selected != null && selected.getValue() instanceof ConfigFileEntry) {
                        onFileSelected((ConfigFileEntry) selected.getValue());
                    }
                });

        // 右键菜单
        fileTreeView.setContextMenu(createTreeContextMenu());

        VBox.setVgrow(fileTreeView, Priority.ALWAYS);

        // 快捷操作
        HBox quickActions = new HBox(5);
        quickActions.setAlignment(Pos.CENTER);

        Button newConfigBtn = new Button("+ 新建");
        newConfigBtn.setStyle("-fx-font-size: 11px;");
        newConfigBtn.setOnAction(e -> showNewConfigDialog());

        Label hintLabel = new Label("右键查看更多");
        hintLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 10px;");

        quickActions.getChildren().addAll(newConfigBtn, hintLabel);

        pane.getChildren().addAll(header, searchField, fileTreeView, quickActions);
        return pane;
    }

    private ContextMenu createTreeContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem openFolder = new MenuItem("在资源管理器中打开");
        openFolder.setOnAction(e -> {
            TreeItem<Object> selected = fileTreeView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() instanceof ConfigFileEntry) {
                ConfigFileEntry entry = (ConfigFileEntry) selected.getValue();
                openInExplorer(entry.getFile().getParentFile());
            }
        });

        MenuItem copyPath = new MenuItem("复制文件路径");
        copyPath.setOnAction(e -> {
            TreeItem<Object> selected = fileTreeView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getValue() instanceof ConfigFileEntry) {
                ConfigFileEntry entry = (ConfigFileEntry) selected.getValue();
                copyToClipboard(entry.getFile().getAbsolutePath());
                showStatus("路径已复制到剪贴板", false);
            }
        });

        MenuItem reload = new MenuItem("重新加载");
        reload.setOnAction(e -> reloadCurrentConfig());

        MenuItem showBackups = new MenuItem("查看备份历史...");
        showBackups.setOnAction(e -> showBackupHistory());

        menu.getItems().addAll(openFolder, copyPath, new SeparatorMenuItem(), reload, showBackups);
        return menu;
    }

    private void loadConfigTree() {
        TreeItem<Object> root = new TreeItem<>("root");

        // 按分类分组
        Map<ConfigFileEntry.ConfigCategory, List<ConfigFileEntry>> grouped = new LinkedHashMap<>();
        for (ConfigFileEntry.ConfigCategory cat : ConfigFileEntry.ConfigCategory.values()) {
            grouped.put(cat, new ArrayList<>());
        }

        for (ConfigFileEntry entry : configService.getAllConfigs()) {
            grouped.get(entry.getCategory()).add(entry);
        }

        // 构建树
        for (Map.Entry<ConfigFileEntry.ConfigCategory, List<ConfigFileEntry>> e : grouped.entrySet()) {
            if (e.getValue().isEmpty()) continue;

            ConfigFileEntry.ConfigCategory category = e.getKey();
            TreeItem<Object> categoryNode = new TreeItem<>(category);
            categoryNode.setExpanded(true);

            for (ConfigFileEntry entry : e.getValue()) {
                TreeItem<Object> fileNode = new TreeItem<>(entry);
                categoryNode.getChildren().add(fileNode);
            }

            root.getChildren().add(categoryNode);
        }

        fileTreeView.setRoot(root);

        // 自动选中第一个文件
        for (TreeItem<Object> cat : root.getChildren()) {
            if (!cat.getChildren().isEmpty()) {
                fileTreeView.getSelectionModel().select(cat.getChildren().get(0));
                break;
            }
        }
    }

    private void filterTree(String filter) {
        if (filter == null || filter.trim().isEmpty()) {
            loadConfigTree();
            return;
        }

        String lowerFilter = filter.toLowerCase();
        TreeItem<Object> root = new TreeItem<>("root");

        Map<ConfigFileEntry.ConfigCategory, List<ConfigFileEntry>> grouped = new LinkedHashMap<>();
        for (ConfigFileEntry.ConfigCategory cat : ConfigFileEntry.ConfigCategory.values()) {
            grouped.put(cat, new ArrayList<>());
        }

        for (ConfigFileEntry entry : configService.getAllConfigs()) {
            if (entry.getName().toLowerCase().contains(lowerFilter) ||
                entry.getDisplayName().toLowerCase().contains(lowerFilter)) {
                grouped.get(entry.getCategory()).add(entry);
            }
        }

        for (Map.Entry<ConfigFileEntry.ConfigCategory, List<ConfigFileEntry>> e : grouped.entrySet()) {
            if (e.getValue().isEmpty()) continue;

            TreeItem<Object> categoryNode = new TreeItem<>(e.getKey());
            categoryNode.setExpanded(true);

            for (ConfigFileEntry entry : e.getValue()) {
                categoryNode.getChildren().add(new TreeItem<>(entry));
            }
            root.getChildren().add(categoryNode);
        }

        fileTreeView.setRoot(root);
    }

    // ==================== 中间编辑器 ====================

    private BorderPane createEditorPane() {
        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(5));

        // 顶部工具栏
        VBox topBox = new VBox(5);
        topBox.getChildren().add(createEditorToolbar());

        // 搜索栏（默认隐藏）
        searchBar = createSearchBar();
        searchBar.setVisible(false);
        searchBar.setManaged(false);
        topBox.getChildren().add(searchBar);

        pane.setTop(topBox);

        // 编辑器（带行号）
        HBox editorBox = createEditorWithLineNumbers();
        pane.setCenter(editorBox);

        return pane;
    }

    private HBox createEditorToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(5, 0, 8, 0));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // 文件信息
        fileInfoLabel = new Label("请选择一个配置文件");
        fileInfoLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        // 验证状态
        validationLabel = new Label();
        validationLabel.setStyle("-fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 显示敏感信息复选框
        showSensitiveCheckbox = new CheckBox("显示敏感");
        showSensitiveCheckbox.setTooltip(new Tooltip("显示密码、API Key等敏感信息"));
        showSensitiveCheckbox.setOnAction(e -> {
            showSensitive = showSensitiveCheckbox.isSelected();
            if (currentEntry != null) {
                refreshEditorContent();
            }
        });

        // 工具按钮
        Button searchBtn = new Button("🔍");
        searchBtn.setTooltip(new Tooltip("搜索 (Ctrl+F)"));
        searchBtn.setOnAction(e -> toggleSearchBar());

        formatButton = new Button("格式化");
        formatButton.setTooltip(new Tooltip("格式化内容 (Ctrl+Shift+F)"));
        formatButton.setDisable(true);
        formatButton.setOnAction(e -> formatContent());

        reloadButton = new Button("↻ 重载");
        reloadButton.setTooltip(new Tooltip("重新加载文件"));
        reloadButton.setDisable(true);
        reloadButton.setOnAction(e -> reloadCurrentConfig());

        saveButton = new Button("💾 保存");
        saveButton.setTooltip(new Tooltip("保存 (Ctrl+S)"));
        saveButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        saveButton.setDisable(true);
        saveButton.setOnAction(e -> saveCurrentConfig());

        backupButton = new Button("📋 备份");
        backupButton.setTooltip(new Tooltip("查看备份历史"));
        backupButton.setDisable(true);
        backupButton.setOnAction(e -> showBackupHistory());

        Button importButton = new Button("📥 导入");
        importButton.setTooltip(new Tooltip("导入配置文件"));
        importButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        importButton.setOnAction(e -> importConfig());

        exportButton = new Button("📤 导出");
        exportButton.setTooltip(new Tooltip("导出配置文件"));
        exportButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        exportButton.setDisable(true);
        exportButton.setOnAction(e -> exportConfig());

        toolbar.getChildren().addAll(
                fileInfoLabel, validationLabel, spacer,
                showSensitiveCheckbox,
                new Separator(Orientation.VERTICAL),
                searchBtn, formatButton, reloadButton, saveButton, backupButton,
                new Separator(Orientation.VERTICAL),
                importButton, exportButton
        );

        return toolbar;
    }

    private HBox createSearchBar() {
        HBox bar = new HBox(8);
        bar.setPadding(new Insets(5, 0, 5, 0));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #fff3cd; -fx-padding: 8; -fx-background-radius: 4;");

        Label label = new Label("搜索:");
        TextField findField = new TextField();
        findField.setPrefWidth(200);
        findField.setPromptText("输入关键字...");

        Button findNext = new Button("下一个");
        Button findPrev = new Button("上一个");
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-font-size: 10px;");

        Label resultLabel = new Label();
        resultLabel.setStyle("-fx-text-fill: #666;");

        findField.setOnAction(e -> findInEditor(findField.getText(), true, resultLabel));
        findNext.setOnAction(e -> findInEditor(findField.getText(), true, resultLabel));
        findPrev.setOnAction(e -> findInEditor(findField.getText(), false, resultLabel));
        closeBtn.setOnAction(e -> toggleSearchBar());

        bar.getChildren().addAll(label, findField, findNext, findPrev, resultLabel, new Region(), closeBtn);
        HBox.setHgrow(bar.getChildren().get(5), Priority.ALWAYS);

        return bar;
    }

    private HBox createEditorWithLineNumbers() {
        HBox box = new HBox();

        // 行号面板
        lineNumberPane = new VBox();
        lineNumberPane.setStyle("-fx-background-color: #2d2d2d; -fx-padding: 5 8;");
        lineNumberPane.setMinWidth(45);
        lineNumberPane.setAlignment(Pos.TOP_RIGHT);

        // 编辑器
        editorArea = new TextArea();
        editorArea.setFont(Font.font("Consolas", 13));
        editorArea.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #d4d4d4;");
        editorArea.setWrapText(false);
        editorArea.setDisable(true);

        // 监听文本变化
        editorArea.textProperty().addListener((obs, old, newText) -> {
            updateLineNumbers();
            if (currentEntry != null && originalContent != null) {
                boolean modified = !newText.equals(originalContent);
                currentEntry.setModified(modified);
                updateSaveButtonState();
                updateTitle();
                validateContent(newText);
            }
        });

        // 监听光标变化
        editorArea.caretPositionProperty().addListener((obs, old, pos) -> updateCursorPosition());

        // 同步滚动
        editorArea.scrollTopProperty().addListener((obs, old, val) -> {
            // 行号随编辑器滚动
        });

        // 右键菜单
        editorArea.setContextMenu(createEditorContextMenu());

        // 包装
        ScrollPane lineScroll = new ScrollPane(lineNumberPane);
        lineScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        lineScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        lineScroll.setFitToWidth(true);
        lineScroll.setStyle("-fx-background-color: #2d2d2d;");

        editorScrollPane = new ScrollPane(editorArea);
        editorScrollPane.setFitToWidth(true);
        editorScrollPane.setFitToHeight(true);

        HBox.setHgrow(editorArea, Priority.ALWAYS);
        box.getChildren().addAll(lineNumberPane, editorArea);
        HBox.setHgrow(box.getChildren().get(1), Priority.ALWAYS);

        return box;
    }

    private ContextMenu createEditorContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem cut = new MenuItem("剪切");
        cut.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));
        cut.setOnAction(e -> editorArea.cut());

        MenuItem copy = new MenuItem("复制");
        copy.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
        copy.setOnAction(e -> editorArea.copy());

        MenuItem paste = new MenuItem("粘贴");
        paste.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));
        paste.setOnAction(e -> editorArea.paste());

        MenuItem selectAll = new MenuItem("全选");
        selectAll.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN));
        selectAll.setOnAction(e -> editorArea.selectAll());

        MenuItem find = new MenuItem("搜索...");
        find.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN));
        find.setOnAction(e -> toggleSearchBar());

        MenuItem gotoLine = new MenuItem("跳转到行...");
        gotoLine.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN));
        gotoLine.setOnAction(e -> showGotoLineDialog());

        MenuItem format = new MenuItem("格式化");
        format.setOnAction(e -> formatContent());

        menu.getItems().addAll(cut, copy, paste, new SeparatorMenuItem(),
                selectAll, new SeparatorMenuItem(), find, gotoLine, new SeparatorMenuItem(), format);
        return menu;
    }

    private void updateLineNumbers() {
        lineNumberPane.getChildren().clear();
        String text = editorArea.getText();
        int lineCount = text.isEmpty() ? 1 : text.split("\n", -1).length;

        for (int i = 1; i <= lineCount; i++) {
            Label lineLabel = new Label(String.valueOf(i));
            lineLabel.setFont(Font.font("Consolas", 12));
            lineLabel.setTextFill(Color.web("#858585"));
            lineLabel.setMinHeight(17.5); // 与编辑器行高匹配
            lineNumberPane.getChildren().add(lineLabel);
        }
    }

    private void updateCursorPosition() {
        if (editorArea.getText().isEmpty()) {
            cursorPosLabel.setText("行 1, 列 1");
            return;
        }

        int caretPos = editorArea.getCaretPosition();
        String text = editorArea.getText();

        int line = 1;
        int col = 1;
        for (int i = 0; i < caretPos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }

        cursorPosLabel.setText(String.format("行 %d, 列 %d", line, col));
    }

    private void validateContent(String content) {
        if (currentEntry == null) return;

        boolean valid = true;
        String message = "";

        try {
            switch (currentEntry.getType()) {
                case YAML:
                    new org.yaml.snakeyaml.Yaml().load(content);
                    message = "✓ YAML 格式正确";
                    break;
                case JSON:
                    com.alibaba.fastjson.JSON.parse(content);
                    message = "✓ JSON 格式正确";
                    break;
                default:
                    message = "";
            }
        } catch (Exception e) {
            valid = false;
            message = "✗ 格式错误: " + e.getMessage();
        }

        final boolean isValid = valid;
        final String msg = message;
        Platform.runLater(() -> {
            validationLabel.setText(msg);
            validationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isValid ? "#4CAF50" : "#f44336") + ";");
        });
    }

    // ==================== 右侧信息面板 ====================

    private VBox createInfoPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));
        pane.setStyle("-fx-background-color: #fafafa; -fx-border-color: #ddd; -fx-border-width: 0 0 0 1;");

        Label title = new Label("文件详情");
        title.setFont(Font.font("System", FontWeight.BOLD, 13));

        // 详情区域
        VBox detailBox = new VBox(6);
        detailBox.setId("detail-box");

        // 结构预览
        Label structureTitle = new Label("结构预览");
        structureTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        structureTitle.setPadding(new Insets(10, 0, 0, 0));

        structureTree = new TreeView<>();
        structureTree.setShowRoot(true);
        structureTree.setPrefHeight(200);
        VBox.setVgrow(structureTree, Priority.ALWAYS);

        // 快速操作
        Label actionsTitle = new Label("快速操作");
        actionsTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        actionsTitle.setPadding(new Insets(10, 0, 5, 0));

        VBox actionsBox = new VBox(5);

        Button compareBtn = new Button("与备份对比...");
        compareBtn.setMaxWidth(Double.MAX_VALUE);
        compareBtn.setOnAction(e -> showDiffDialog());

        Button copyAllBtn = new Button("复制全部内容");
        copyAllBtn.setMaxWidth(Double.MAX_VALUE);
        copyAllBtn.setOnAction(e -> {
            copyToClipboard(editorArea.getText());
            showStatus("内容已复制", false);
        });

        actionsBox.getChildren().addAll(compareBtn, copyAllBtn);

        pane.getChildren().addAll(title, new Separator(), detailBox,
                structureTitle, structureTree, actionsTitle, actionsBox);

        return pane;
    }

    private void updateInfoPane(ConfigFileEntry entry) {
        VBox detailBox = (VBox) infoPane.lookup("#detail-box");
        if (detailBox == null) return;

        detailBox.getChildren().clear();

        if (entry == null) {
            Label emptyLabel = new Label("请选择文件");
            emptyLabel.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
            detailBox.getChildren().add(emptyLabel);
            return;
        }

        // ==================== 文件基本信息 ====================
        // 文件名（带图标）
        HBox nameBox = new HBox(5);
        nameBox.setAlignment(Pos.CENTER_LEFT);
        String icon = getFileIcon(entry);
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 16px;");
        Label nameLabel = new Label(entry.getName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        nameBox.getChildren().addAll(iconLabel, nameLabel);
        detailBox.getChildren().add(nameBox);

        detailBox.getChildren().add(new Separator());

        // 类型和分类
        addDetailRow(detailBox, "类型", entry.getType().getDisplayName());
        addDetailRow(detailBox, "分类", entry.getCategory().getDisplayName());

        // 路径（可点击打开文件夹）
        HBox pathRow = new HBox(5);
        Label pathKeyLabel = new Label("路径:");
        pathKeyLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        pathKeyLabel.setMinWidth(60);

        String path = entry.getFile().getAbsolutePath();
        Label pathLabel = new Label(path);
        pathLabel.setWrapText(true);
        pathLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #0066cc; -fx-underline: true; -fx-cursor: hand;");
        pathLabel.setOnMouseClicked(e -> openInExplorer(entry.getFile().getParentFile()));
        pathLabel.setTooltip(new Tooltip("点击打开文件夹"));

        pathRow.getChildren().addAll(pathKeyLabel, pathLabel);
        detailBox.getChildren().add(pathRow);

        // ==================== 文件状态 ====================
        // 获取内容（用于后续健康度检查）
        String content = editorArea.getText();

        if (entry.exists()) {
            long size = entry.getFile().length();
            addDetailRow(detailBox, "大小", formatFileSize(size));

            // 最后修改时间（带相对时间）
            if (entry.getLastModified() != null) {
                String timeStr = entry.getLastModified().format(TIME_FMT);
                String relativeTime = getRelativeTime(entry.getLastModified());
                addDetailRow(detailBox, "修改时间", timeStr + " (" + relativeTime + ")");
            }

            // 内容统计
            if (!content.isEmpty()) {
                detailBox.getChildren().add(new Separator());

                // 行数
                int lineCount = content.split("\n").length;
                addDetailRow(detailBox, "行数", String.format("%,d", lineCount));

                // 字符数
                int charCount = content.length();
                addDetailRow(detailBox, "字符数", String.format("%,d", charCount));

                // 配置项数量（针对 YAML/JSON/Properties）
                int configCount = countConfigItems(content, entry.getType());
                if (configCount > 0) {
                    addDetailRow(detailBox, "配置项", String.format("%,d", configCount));
                }
            }

            // 备份信息
            List<File> backups = configService.getBackups(entry);
            if (!backups.isEmpty()) {
                Label backupLabel = new Label(String.format("✓ 有 %d 个备份", backups.size()));
                backupLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 10px;");
                detailBox.getChildren().add(backupLabel);
            } else {
                Label noBackupLabel = new Label("⚠ 暂无备份");
                noBackupLabel.setStyle("-fx-text-fill: #FF9800; -fx-font-size: 10px;");
                detailBox.getChildren().add(noBackupLabel);
            }

        } else {
            Label errorLabel = new Label("❌ 文件不存在");
            errorLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold;");
            detailBox.getChildren().add(errorLabel);
        }

        // 敏感标记
        if (entry.isSensitive()) {
            detailBox.getChildren().add(new Separator());
            HBox sensitiveBox = new HBox(5);
            sensitiveBox.setStyle("-fx-background-color: #fff3cd; -fx-padding: 5; -fx-background-radius: 3;");
            Label sensitiveIcon = new Label("⚠");
            sensitiveIcon.setStyle("-fx-font-size: 14px; -fx-text-fill: #FF9800;");
            Label sensitiveText = new Label("包含敏感信息");
            sensitiveText.setStyle("-fx-text-fill: #856404; -fx-font-weight: bold; -fx-font-size: 11px;");
            sensitiveBox.getChildren().addAll(sensitiveIcon, sensitiveText);
            detailBox.getChildren().add(sensitiveBox);
        }

        // 文件健康度指示器
        detailBox.getChildren().add(new Separator());
        VBox healthBox = createHealthIndicator(entry, content);
        detailBox.getChildren().add(healthBox);

        // 更新结构树
        updateStructureTree(entry);
    }

    /**
     * 创建文件健康度指示器
     */
    private VBox createHealthIndicator(ConfigFileEntry entry, String content) {
        VBox box = new VBox(3);
        Label title = new Label("文件健康度");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #555;");
        box.getChildren().add(title);

        int healthScore = 0;
        int maxScore = 5;

        // 1. 文件存在 (1分)
        if (entry.exists()) {
            healthScore++;
        }

        // 2. 格式有效 (2分)
        if (content != null && !content.isEmpty()) {
            if (validateConfigContent(content, getFileType(entry.getName()))) {
                healthScore += 2;
            }
        }

        // 3. 有备份 (1分)
        List<File> backups = configService.getBackups(entry);
        if (!backups.isEmpty()) {
            healthScore++;
        }

        // 4. 最近修改过 (1分)
        if (entry.getLastModified() != null) {
            long daysSinceModified = java.time.Duration.between(
                    entry.getLastModified(),
                    LocalDateTime.now()
            ).toDays();
            if (daysSinceModified < 30) {
                healthScore++;
            }
        }

        // 健康度条
        HBox barBox = new HBox(2);
        for (int i = 0; i < maxScore; i++) {
            Label bar = new Label("■");
            if (i < healthScore) {
                if (healthScore >= 4) {
                    bar.setStyle("-fx-text-fill: #4CAF50;"); // 绿色
                } else if (healthScore >= 2) {
                    bar.setStyle("-fx-text-fill: #FF9800;"); // 橙色
                } else {
                    bar.setStyle("-fx-text-fill: #f44336;"); // 红色
                }
            } else {
                bar.setStyle("-fx-text-fill: #ddd;");
            }
            barBox.getChildren().add(bar);
        }

        Label scoreLabel = new Label(String.format("%d/%d", healthScore, maxScore));
        scoreLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        HBox healthRow = new HBox(5);
        healthRow.setAlignment(Pos.CENTER_LEFT);
        healthRow.getChildren().addAll(barBox, scoreLabel);
        box.getChildren().add(healthRow);

        return box;
    }

    /**
     * 获取文件图标
     */
    private String getFileIcon(ConfigFileEntry entry) {
        switch (entry.getType()) {
            case YAML: return "📄";
            case JSON: return "📋";
            case PROPERTIES: return "⚙";
            case ENV: return "🔐";
            default: return "📝";
        }
    }

    /**
     * 计算相对时间
     */
    private String getRelativeTime(LocalDateTime dateTime) {
        long minutes = java.time.Duration.between(dateTime, LocalDateTime.now()).toMinutes();
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + " 分钟前";

        long hours = minutes / 60;
        if (hours < 24) return hours + " 小时前";

        long days = hours / 24;
        if (days < 7) return days + " 天前";
        if (days < 30) return (days / 7) + " 周前";
        if (days < 365) return (days / 30) + " 个月前";

        return (days / 365) + " 年前";
    }

    /**
     * 统计配置项数量
     */
    private int countConfigItems(String content, ConfigFileEntry.ConfigType type) {
        try {
            switch (type) {
                case YAML:
                    org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                    Object yamlData = yaml.load(content);
                    return countMapEntries(yamlData);

                case JSON:
                    com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSON.parseObject(content);
                    return countJsonEntries(json);

                case PROPERTIES:
                case ENV:
                    return (int) content.lines()
                            .filter(line -> line.contains("=") && !line.trim().startsWith("#"))
                            .count();

                default:
                    return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private int countMapEntries(Object data) {
        if (data instanceof Map) {
            int count = ((Map<?, ?>) data).size();
            for (Object value : ((Map<?, ?>) data).values()) {
                count += countMapEntries(value);
            }
            return count;
        } else if (data instanceof List) {
            int count = 0;
            for (Object item : (List<?>) data) {
                count += countMapEntries(item);
            }
            return count;
        }
        return 0;
    }

    private int countJsonEntries(com.alibaba.fastjson.JSONObject json) {
        int count = json.size();
        for (Object value : json.values()) {
            if (value instanceof com.alibaba.fastjson.JSONObject) {
                count += countJsonEntries((com.alibaba.fastjson.JSONObject) value);
            } else if (value instanceof com.alibaba.fastjson.JSONArray) {
                com.alibaba.fastjson.JSONArray arr = (com.alibaba.fastjson.JSONArray) value;
                for (int i = 0; i < arr.size(); i++) {
                    Object item = arr.get(i);
                    if (item instanceof com.alibaba.fastjson.JSONObject) {
                        count += countJsonEntries((com.alibaba.fastjson.JSONObject) item);
                    }
                }
            }
        }
        return count;
    }

    private void addDetailRow(VBox container, String label, String value) {
        addDetailRow(container, label, new Label(value));
    }

    private void addDetailRow(VBox container, String label, Node valueNode) {
        HBox row = new HBox(5);
        Label keyLabel = new Label(label + ":");
        keyLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        keyLabel.setMinWidth(60);
        row.getChildren().addAll(keyLabel, valueNode);
        container.getChildren().add(row);
    }

    @SuppressWarnings("unchecked")
    private void updateStructureTree(ConfigFileEntry entry) {
        if (entry == null || !entry.exists()) {
            structureTree.setRoot(null);
            return;
        }

        try {
            String content = editorArea.getText();
            if (content.isEmpty()) {
                structureTree.setRoot(new TreeItem<>("(空)"));
                return;
            }

            TreeItem<String> root;

            switch (entry.getType()) {
                case YAML:
                    Map<String, Object> yamlData = configService.parseYaml(content);
                    root = buildStructureTree(entry.getName(), yamlData);
                    break;
                case JSON:
                    Object jsonData = com.alibaba.fastjson.JSON.parse(content);
                    root = buildStructureTree(entry.getName(), jsonData);
                    break;
                default:
                    root = new TreeItem<>(entry.getName());
                    String[] lines = content.split("\n");
                    int count = 0;
                    for (String line : lines) {
                        if (count++ > 20) {
                            root.getChildren().add(new TreeItem<>("..."));
                            break;
                        }
                        if (!line.trim().isEmpty() && !line.trim().startsWith("#")) {
                            root.getChildren().add(new TreeItem<>(line.trim()));
                        }
                    }
            }

            root.setExpanded(true);
            structureTree.setRoot(root);

        } catch (Exception e) {
            structureTree.setRoot(new TreeItem<>("解析失败: " + e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private TreeItem<String> buildStructureTree(String name, Object data) {
        TreeItem<String> item = new TreeItem<>(name);

        if (data instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) data;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                TreeItem<String> child = buildStructureTree(e.getKey(), e.getValue());
                item.getChildren().add(child);
            }
        } else if (data instanceof List) {
            List<?> list = (List<?>) data;
            item.setValue(name + " [" + list.size() + "]");
            int idx = 0;
            for (Object o : list) {
                if (idx > 10) {
                    item.getChildren().add(new TreeItem<>("..."));
                    break;
                }
                TreeItem<String> child = buildStructureTree("[" + idx + "]", o);
                item.getChildren().add(child);
                idx++;
            }
        } else if (data != null) {
            String value = data.toString();
            if (value.length() > 50) {
                value = value.substring(0, 47) + "...";
            }
            item.setValue(name + ": " + value);
        } else {
            item.setValue(name + ": null");
        }

        return item;
    }

    // ==================== 状态栏 ====================

    private HBox createStatusBar() {
        HBox statusBar = new HBox(15);
        statusBar.setPadding(new Insets(8, 10, 8, 10));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: #e8e8e8; -fx-border-color: #ccc; -fx-border-width: 1 0 0 0;");

        statusLabel = new Label("就绪");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        cursorPosLabel = new Label("行 1, 列 1");
        cursorPosLabel.setStyle("-fx-text-fill: #555;");

        charCountLabel = new Label("0 字符");
        charCountLabel.setStyle("-fx-text-fill: #555;");

        Label shortcutHint = new Label("Ctrl+S 保存 | Ctrl+F 搜索 | Ctrl+G 跳转行");
        shortcutHint.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");

        statusBar.getChildren().addAll(statusLabel, spacer, cursorPosLabel,
                new Separator(Orientation.VERTICAL), charCountLabel,
                new Separator(Orientation.VERTICAL), shortcutHint);

        return statusBar;
    }

    // ==================== 操作方法 ====================

    private void onFileSelected(ConfigFileEntry entry) {
        // 检查未保存修改
        if (currentEntry != null && currentEntry.isModified()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("未保存的修改");
            alert.setHeaderText("当前文件有未保存的修改");
            alert.setContentText("是否保存修改？");
            alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == ButtonType.YES) {
                    saveCurrentConfig();
                } else if (result.get() == ButtonType.CANCEL) {
                    return;
                }
            }
        }

        currentEntry = entry;
        loadConfigContent(entry);
        updateInfoPane(entry);
    }

    private void loadConfigContent(ConfigFileEntry entry) {
        try {
            if (!entry.exists()) {
                editorArea.setText("# 文件不存在: " + entry.getFile().getAbsolutePath());
                editorArea.setDisable(true);
                disableButtons();
                return;
            }

            String content = configService.readConfig(entry);
            originalContent = content;

            refreshEditorContent();
            updateLineNumbers();
            updateCursorPosition();
            updateCharCount();

            editorArea.setDisable(false);
            enableButtons();
            updateSaveButtonState();

            fileInfoLabel.setText(entry.getDisplayName());
            showStatus("已加载: " + entry.getName(), false);
            updateTitle();

        } catch (Exception e) {
            log.error("加载配置文件失败", e);
            editorArea.setText("# 加载失败: " + e.getMessage());
            showStatus("加载失败: " + e.getMessage(), true);
        }
    }

    private void refreshEditorContent() {
        if (currentEntry == null || originalContent == null) return;

        String displayContent;
        if (showSensitive || !currentEntry.isSensitive()) {
            displayContent = originalContent;
        } else {
            displayContent = configService.maskSensitiveContent(originalContent, currentEntry);
        }

        int caretPos = editorArea.getCaretPosition();
        editorArea.setText(displayContent);
        if (caretPos <= displayContent.length()) {
            editorArea.positionCaret(caretPos);
        }

        updateCharCount();
    }

    private void updateCharCount() {
        String text = editorArea.getText();
        int chars = text.length();
        int lines = text.isEmpty() ? 0 : text.split("\n", -1).length;
        charCountLabel.setText(String.format("%d 字符, %d 行", chars, lines));
    }

    private void reloadCurrentConfig() {
        if (currentEntry == null) return;

        if (currentEntry.isModified()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("确认重新加载");
            alert.setHeaderText("当前有未保存的修改");
            alert.setContentText("重新加载将丢失所有修改，确定继续？");

            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        loadConfigContent(currentEntry);
        showStatus("已重新加载: " + currentEntry.getName(), false);
    }

    private void saveCurrentConfig() {
        if (currentEntry == null || !currentEntry.isModified()) return;

        String content = editorArea.getText();
        boolean success = configService.saveConfig(currentEntry, content);

        if (success) {
            originalContent = content;
            currentEntry.setModified(false);
            updateSaveButtonState();
            updateTitle();
            showStatus("✓ 已保存: " + currentEntry.getName(), false);
            fileTreeView.refresh();

            // 闪烁效果
            saveButton.setStyle("-fx-background-color: #81C784; -fx-text-fill: white;");
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    Platform.runLater(() ->
                        saveButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;"));
                } catch (InterruptedException ignored) {}
            }).start();
        } else {
            showStatus("保存失败，请检查格式", true);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("保存失败");
            alert.setHeaderText("无法保存配置文件");
            alert.setContentText("请检查文件格式是否正确。");
            alert.showAndWait();
        }
    }

    private void formatContent() {
        if (currentEntry == null) return;

        try {
            String content = editorArea.getText();
            String formatted = content;

            switch (currentEntry.getType()) {
                case YAML:
                    Map<String, Object> yamlData = configService.parseYaml(content);
                    formatted = configService.toYamlString(yamlData);
                    break;
                case JSON:
                    Object jsonData = com.alibaba.fastjson.JSON.parse(content);
                    formatted = com.alibaba.fastjson.JSON.toJSONString(jsonData, true);
                    break;
                default:
                    showStatus("此格式不支持格式化", false);
                    return;
            }

            editorArea.setText(formatted);
            showStatus("已格式化", false);

        } catch (Exception e) {
            showStatus("格式化失败: " + e.getMessage(), true);
        }
    }

    private void toggleSearchBar() {
        searchBarVisible = !searchBarVisible;
        searchBar.setVisible(searchBarVisible);
        searchBar.setManaged(searchBarVisible);

        if (searchBarVisible) {
            // 聚焦搜索框
            Platform.runLater(() -> {
                TextField findField = (TextField) searchBar.getChildren().get(1);
                findField.requestFocus();
                findField.selectAll();
            });
        }
    }

    private void findInEditor(String keyword, boolean forward, Label resultLabel) {
        if (keyword == null || keyword.isEmpty()) {
            resultLabel.setText("");
            return;
        }

        String text = editorArea.getText().toLowerCase();
        String searchKey = keyword.toLowerCase();
        int currentPos = editorArea.getCaretPosition();

        int foundPos;
        if (forward) {
            foundPos = text.indexOf(searchKey, currentPos);
            if (foundPos == -1) {
                foundPos = text.indexOf(searchKey); // 从头开始
            }
        } else {
            foundPos = text.lastIndexOf(searchKey, currentPos - keyword.length() - 1);
            if (foundPos == -1) {
                foundPos = text.lastIndexOf(searchKey);
            }
        }

        if (foundPos >= 0) {
            editorArea.selectRange(foundPos, foundPos + keyword.length());
            editorArea.requestFocus();

            // 统计匹配数
            int count = 0;
            int pos = 0;
            while ((pos = text.indexOf(searchKey, pos)) >= 0) {
                count++;
                pos++;
            }
            resultLabel.setText("找到 " + count + " 处");
            resultLabel.setStyle("-fx-text-fill: #4CAF50;");
        } else {
            resultLabel.setText("未找到");
            resultLabel.setStyle("-fx-text-fill: #f44336;");
        }
    }

    private void showGotoLineDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("跳转到行");
        dialog.setHeaderText(null);
        dialog.setContentText("输入行号:");

        dialog.showAndWait().ifPresent(input -> {
            try {
                int lineNum = Integer.parseInt(input.trim());
                gotoLine(lineNum);
            } catch (NumberFormatException e) {
                showStatus("请输入有效的行号", true);
            }
        });
    }

    private void gotoLine(int lineNumber) {
        String text = editorArea.getText();
        String[] lines = text.split("\n", -1);

        if (lineNumber < 1 || lineNumber > lines.length) {
            showStatus("行号超出范围 (1-" + lines.length + ")", true);
            return;
        }

        int pos = 0;
        for (int i = 0; i < lineNumber - 1; i++) {
            pos += lines[i].length() + 1;
        }

        editorArea.positionCaret(pos);
        editorArea.requestFocus();
        showStatus("跳转到第 " + lineNumber + " 行", false);
    }

    private void showBackupHistory() {
        if (currentEntry == null) return;

        List<File> backups = configService.getBackups(currentEntry);

        if (backups.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("备份历史");
            alert.setHeaderText("暂无备份");
            alert.setContentText("首次保存后会自动创建备份。");
            alert.showAndWait();
            return;
        }

        // 创建备份选择对话框
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(this);
        dialog.setTitle("备份历史 - " + currentEntry.getName());
        dialog.setWidth(500);
        dialog.setHeight(400);

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        Label header = new Label("选择要恢复的备份版本 (共 " + backups.size() + " 个):");
        header.setFont(Font.font("System", FontWeight.BOLD, 13));

        TableView<File> backupTable = new TableView<>();
        backupTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<File, String> nameCol = new TableColumn<>("备份文件");
        nameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
        nameCol.setPrefWidth(280);

        TableColumn<File, String> sizeCol = new TableColumn<>("大小");
        sizeCol.setCellValueFactory(cell ->
            new SimpleStringProperty(formatFileSize(cell.getValue().length())));
        sizeCol.setPrefWidth(80);

        TableColumn<File, String> timeCol = new TableColumn<>("时间");
        timeCol.setCellValueFactory(cell ->
            new SimpleStringProperty(new java.text.SimpleDateFormat("MM-dd HH:mm")
                .format(new java.util.Date(cell.getValue().lastModified()))));
        timeCol.setPrefWidth(100);

        backupTable.getColumns().addAll(nameCol, sizeCol, timeCol);
        backupTable.getItems().addAll(backups);
        VBox.setVgrow(backupTable, Priority.ALWAYS);

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button previewBtn = new Button("预览");
        previewBtn.setDisable(true);
        previewBtn.setOnAction(e -> {
            File selected = backupTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                previewBackup(selected);
            }
        });

        Button restoreBtn = new Button("恢复此备份");
        restoreBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        restoreBtn.setDisable(true);
        restoreBtn.setOnAction(e -> {
            File selected = backupTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                boolean success = configService.restoreBackup(currentEntry, selected);
                if (success) {
                    loadConfigContent(currentEntry);
                    dialog.close();
                    showStatus("已恢复备份: " + selected.getName(), false);
                }
            }
        });

        backupTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, sel) -> {
                    previewBtn.setDisable(sel == null);
                    restoreBtn.setDisable(sel == null);
                });

        Button cancelBtn = new Button("关闭");
        cancelBtn.setOnAction(e -> dialog.close());

        buttons.getChildren().addAll(previewBtn, restoreBtn, cancelBtn);

        content.getChildren().addAll(header, backupTable, buttons);

        Scene scene = new Scene(content);
        dialog.setScene(scene);
        dialog.show();
    }

    private void previewBackup(File backupFile) {
        try {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(backupFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            Stage preview = new Stage();
            preview.initModality(Modality.APPLICATION_MODAL);
            preview.initOwner(this);
            preview.setTitle("备份预览 - " + backupFile.getName());
            preview.setWidth(600);
            preview.setHeight(500);

            TextArea previewArea = new TextArea(content.toString());
            previewArea.setEditable(false);
            previewArea.setFont(Font.font("Consolas", 12));
            previewArea.setStyle("-fx-control-inner-background: #2d2d2d; -fx-text-fill: #d4d4d4;");

            Scene scene = new Scene(previewArea);
            preview.setScene(scene);
            preview.show();

        } catch (Exception e) {
            showStatus("预览失败: " + e.getMessage(), true);
        }
    }

    private void showDiffDialog() {
        if (currentEntry == null) return;

        List<File> backups = configService.getBackups(currentEntry);
        if (backups.isEmpty()) {
            showStatus("没有备份可供对比", false);
            return;
        }

        // 简单对比：显示当前与最新备份的差异
        File latestBackup = backups.get(0);
        try {
            StringBuilder backupContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(latestBackup), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    backupContent.append(line).append("\n");
                }
            }

            Stage diffStage = new Stage();
            diffStage.initModality(Modality.APPLICATION_MODAL);
            diffStage.initOwner(this);
            diffStage.setTitle("差异对比");
            diffStage.setWidth(1000);
            diffStage.setHeight(600);

            SplitPane splitPane = new SplitPane();

            // 左侧：备份
            VBox leftBox = new VBox(5);
            Label leftLabel = new Label("备份版本: " + latestBackup.getName());
            leftLabel.setStyle("-fx-font-weight: bold;");
            TextArea leftArea = new TextArea(backupContent.toString());
            leftArea.setEditable(false);
            leftArea.setFont(Font.font("Consolas", 12));
            VBox.setVgrow(leftArea, Priority.ALWAYS);
            leftBox.getChildren().addAll(leftLabel, leftArea);

            // 右侧：当前
            VBox rightBox = new VBox(5);
            Label rightLabel = new Label("当前版本");
            rightLabel.setStyle("-fx-font-weight: bold;");
            TextArea rightArea = new TextArea(editorArea.getText());
            rightArea.setEditable(false);
            rightArea.setFont(Font.font("Consolas", 12));
            VBox.setVgrow(rightArea, Priority.ALWAYS);
            rightBox.getChildren().addAll(rightLabel, rightArea);

            splitPane.getItems().addAll(leftBox, rightBox);
            splitPane.setDividerPositions(0.5);

            Scene scene = new Scene(splitPane);
            diffStage.setScene(scene);
            diffStage.show();

        } catch (Exception e) {
            showStatus("对比失败: " + e.getMessage(), true);
        }
    }

    private void showNewConfigDialog() {
        Dialog<ConfigFileEntry> dialog = new Dialog<>();
        dialog.setTitle("新建配置文件");
        dialog.setHeaderText("创建新的配置文件");
        dialog.initModality(Modality.APPLICATION_MODAL);

        // 表单
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("例如: my-config.yml");

        ComboBox<ConfigFileEntry.ConfigType> typeBox = new ComboBox<>();
        typeBox.getItems().addAll(ConfigFileEntry.ConfigType.values());
        typeBox.setValue(ConfigFileEntry.ConfigType.YAML);

        ComboBox<ConfigFileEntry.ConfigCategory> categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll(ConfigFileEntry.ConfigCategory.values());
        categoryBox.setValue(ConfigFileEntry.ConfigCategory.OTHER);

        grid.add(new Label("文件名:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("类型:"), 0, 1);
        grid.add(typeBox, 1, 1);
        grid.add(new Label("分类:"), 0, 2);
        grid.add(categoryBox, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) return null;

                try {
                    return configService.createConfig(name, typeBox.getValue(),
                            categoryBox.getValue(), "# " + name + "\n");
                } catch (Exception e) {
                    showStatus("创建失败: " + e.getMessage(), true);
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(entry -> {
            loadConfigTree();
            showStatus("已创建: " + entry.getName(), false);
        });
    }

    // ==================== 辅助方法 ====================

    private void enableButtons() {
        reloadButton.setDisable(false);
        backupButton.setDisable(false);
        formatButton.setDisable(false);
        exportButton.setDisable(false);
    }

    private void disableButtons() {
        saveButton.setDisable(true);
        reloadButton.setDisable(true);
        backupButton.setDisable(true);
        formatButton.setDisable(true);
        exportButton.setDisable(true);
    }

    private void updateSaveButtonState() {
        saveButton.setDisable(currentEntry == null || !currentEntry.isModified());
    }

    private void updateTitle() {
        String title = "配置文件管理器";
        if (currentEntry != null) {
            title += " - " + currentEntry.getName();
            if (currentEntry.isModified()) {
                title += " *";
            }
        }
        setTitle(title);
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError ? "-fx-text-fill: #f44336;" : "-fx-text-fill: #333;");
    }

    private void openInExplorer(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception e) {
            showStatus("无法打开: " + e.getMessage(), true);
        }
    }

    private void copyToClipboard(String text) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // ==================== 导入导出功能 ====================

    /**
     * 导入配置文件
     * 支持导入单个配置文件或批量导入配置文件集合
     */
    private void importConfig() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("选择要导入的配置文件");
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("配置文件", "*.yml", "*.yaml", "*.json", "*.properties"),
                new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(this);
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            return;
        }

        // 显示导入确认对话框
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.initOwner(this);
        confirmDialog.setTitle("确认导入");
        confirmDialog.setHeaderText("导入配置文件");
        confirmDialog.setContentText(String.format(
                "将导入 %d 个配置文件：\n\n%s\n\n是否继续？",
                selectedFiles.size(),
                selectedFiles.stream()
                        .map(File::getName)
                        .limit(5)
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("") + (selectedFiles.size() > 5 ? "\n..." : "")
        ));

        if (confirmDialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        // 执行导入
        int successCount = 0;
        int failedCount = 0;
        StringBuilder errorMsg = new StringBuilder();

        for (File file : selectedFiles) {
            try {
                // 读取文件内容
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                }

                // 验证文件格式
                String fileContent = content.toString();
                if (!validateConfigContent(fileContent, getFileType(file.getName()))) {
                    failedCount++;
                    errorMsg.append(String.format("• %s: 文件格式验证失败\n", file.getName()));
                    continue;
                }

                // 将文件保存到配置目录
                String targetFileName = file.getName();
                File targetFile = new File(configService.getConfigDir(), targetFileName);

                // 如果目标文件已存在，询问是否覆盖
                if (targetFile.exists()) {
                    Alert overwriteDialog = new Alert(Alert.AlertType.CONFIRMATION);
                    overwriteDialog.initOwner(this);
                    overwriteDialog.setTitle("文件已存在");
                    overwriteDialog.setHeaderText("覆盖确认");
                    overwriteDialog.setContentText(String.format("配置文件 %s 已存在，是否覆盖？", targetFileName));

                    if (overwriteDialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                        continue;
                    }

                    // 创建备份
                    String fileName = targetFile.getName();
                    configService.createBackup(new ConfigFileEntry(
                            fileName,                      // id
                            fileName,                      // name
                            targetFile,                    // file
                            getConfigType(fileName),       // type
                            getConfigCategory(fileName)    // category
                    ));
                }

                // 写入文件
                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
                    writer.write(fileContent);
                }

                successCount++;
                log.info("成功导入配置文件: {}", targetFileName);

            } catch (Exception e) {
                failedCount++;
                errorMsg.append(String.format("• %s: %s\n", file.getName(), e.getMessage()));
                log.error("导入配置文件失败: {}", file.getName(), e);
            }
        }

        // 刷新配置树
        loadConfigTree();

        // 显示导入结果
        Alert resultDialog = new Alert(
                failedCount == 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING
        );
        resultDialog.initOwner(this);
        resultDialog.setTitle("导入完成");
        resultDialog.setHeaderText(String.format("成功: %d 个, 失败: %d 个", successCount, failedCount));

        if (failedCount > 0) {
            resultDialog.setContentText("失败文件:\n" + errorMsg.toString());
        } else {
            resultDialog.setContentText("所有配置文件导入成功！");
        }

        resultDialog.showAndWait();
        showStatus(String.format("导入完成: 成功 %d 个, 失败 %d 个", successCount, failedCount), failedCount > 0);
    }

    /**
     * 导出配置文件
     * 将当前选中的配置文件导出到指定位置
     */
    private void exportConfig() {
        if (currentEntry == null) {
            showStatus("请先选择要导出的配置文件", true);
            return;
        }

        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("导出配置文件");
        fileChooser.setInitialFileName(currentEntry.getName());
        fileChooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("配置文件", "*.yml", "*.yaml", "*.json", "*.properties"),
                new javafx.stage.FileChooser.ExtensionFilter("所有文件", "*.*")
        );

        File targetFile = fileChooser.showSaveDialog(this);
        if (targetFile == null) {
            return;
        }

        try {
            // 获取当前编辑器内容（如果有未保存的修改）
            String content = editorArea.getText();

            // 写入文件
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
                writer.write(content);
            }

            Alert successDialog = new Alert(Alert.AlertType.INFORMATION);
            successDialog.initOwner(this);
            successDialog.setTitle("导出成功");
            successDialog.setHeaderText("配置文件已导出");
            successDialog.setContentText(String.format(
                    "文件: %s\n路径: %s\n大小: %s",
                    targetFile.getName(),
                    targetFile.getAbsolutePath(),
                    formatFileSize(targetFile.length())
            ));
            successDialog.showAndWait();

            showStatus("导出成功: " + targetFile.getAbsolutePath(), false);
            log.info("成功导出配置文件: {}", targetFile.getAbsolutePath());

        } catch (Exception e) {
            Alert errorDialog = new Alert(Alert.AlertType.ERROR);
            errorDialog.initOwner(this);
            errorDialog.setTitle("导出失败");
            errorDialog.setHeaderText("无法导出配置文件");
            errorDialog.setContentText(e.getMessage());
            errorDialog.showAndWait();

            showStatus("导出失败: " + e.getMessage(), true);
            log.error("导出配置文件失败", e);
        }
    }

    /**
     * 验证配置文件内容格式
     */
    private boolean validateConfigContent(String content, String fileType) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        try {
            if ("YAML".equals(fileType)) {
                org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                yaml.load(content);
                return true;
            } else if ("JSON".equals(fileType)) {
                com.alibaba.fastjson.JSON.parseObject(content);
                return true;
            } else if ("PROPERTIES".equals(fileType)) {
                java.util.Properties props = new java.util.Properties();
                props.load(new java.io.StringReader(content));
                return true;
            }
            return true; // 其他类型不验证
        } catch (Exception e) {
            log.warn("配置文件格式验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 根据文件名获取文件类型
     */
    private String getFileType(String fileName) {
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
            return "YAML";
        } else if (fileName.endsWith(".json")) {
            return "JSON";
        } else if (fileName.endsWith(".properties")) {
            return "PROPERTIES";
        }
        return "UNKNOWN";
    }

    /**
     * 根据文件名获取配置类型
     */
    private ConfigFileEntry.ConfigType getConfigType(String fileName) {
        ConfigFileEntry.ConfigType type = ConfigFileEntry.ConfigType.fromFileName(fileName);
        return type != null ? type : ConfigFileEntry.ConfigType.YAML;
    }

    /**
     * 根据文件名获取配置分类
     */
    private ConfigFileEntry.ConfigCategory getConfigCategory(String fileName) {
        if (fileName.equals("application.yml") || fileName.equals("application.yaml")) {
            return ConfigFileEntry.ConfigCategory.CORE;
        } else if (fileName.contains("database") || fileName.contains("db")) {
            return ConfigFileEntry.ConfigCategory.DATABASE;
        } else if (fileName.contains("ai") || fileName.contains("model")) {
            return ConfigFileEntry.ConfigCategory.AI;
        } else if (fileName.contains("aion") || fileName.contains("game") || fileName.contains("xml")) {
            return ConfigFileEntry.ConfigCategory.PATH;
        } else if (fileName.contains("menu") || fileName.contains("LeftMenu")) {
            return ConfigFileEntry.ConfigCategory.MENU;
        }
        return ConfigFileEntry.ConfigCategory.OTHER;
    }

    // ==================== 导航和高亮方法 (世界级错误处理系统) ====================

    /**
     * 导航到指定的配置键
     * @param configKey 配置键，如 "ai.qwen.apikey"
     */
    public void navigateToKey(String configKey) {
        if (configKey == null || configKey.isEmpty()) return;

        // 首先确保加载了 application.yml
        loadApplicationYml();

        Platform.runLater(() -> {
            String content = editorArea.getText();
            int lineNum = findConfigKeyLine(content, configKey);

            if (lineNum > 0) {
                gotoLine(lineNum);
                highlightLine(lineNum);
                log.info("导航到配置键: {} (第{}行)", configKey, lineNum);
            } else {
                log.warn("未找到配置键: {}", configKey);
                setStatus("未找到配置项: " + configKey, true);
            }
        });
    }

    /**
     * 加载 application.yml 文件
     */
    private void loadApplicationYml() {
        // 查找 application.yml 配置项
        for (ConfigFileEntry entry : configService.discoverConfigFiles()) {
            if (entry.getFileName().equals("application.yml")) {
                loadConfigFile(entry);
                break;
            }
        }
    }

    /**
     * 跳转到指定行号
     * @param lineNum 行号 (1-based)
     */
    public void gotoLine(int lineNum) {
        if (lineNum <= 0) return;

        Platform.runLater(() -> {
            String text = editorArea.getText();
            String[] lines = text.split("\n", -1);

            if (lineNum > lines.length) {
                lineNum = lines.length;
            }

            // 计算目标位置
            int targetPos = 0;
            for (int i = 0; i < lineNum - 1 && i < lines.length; i++) {
                targetPos += lines[i].length() + 1;
            }

            // 设置光标位置
            editorArea.positionCaret(targetPos);
            editorArea.requestFocus();

            // 选中整行
            int lineEnd = targetPos + (lineNum <= lines.length ? lines[lineNum - 1].length() : 0);
            editorArea.selectRange(targetPos, lineEnd);

            // 滚动到可见区域
            scrollToLine(lineNum);
        });
    }

    /**
     * 高亮指定行
     * @param lineNum 行号
     */
    public void highlightLine(int lineNum) {
        Platform.runLater(() -> {
            // 更新行号面板高亮
            updateLineNumberHighlight(lineNum);

            // 闪烁动画
            animateHighlight();
        });
    }

    /**
     * 高亮指定范围
     * @param startLine 起始行
     * @param startCol 起始列
     * @param endLine 结束行
     * @param endCol 结束列
     */
    public void highlightRange(int startLine, int startCol, int endLine, int endCol) {
        Platform.runLater(() -> {
            String text = editorArea.getText();
            String[] lines = text.split("\n", -1);

            // 计算起始位置
            int startPos = 0;
            for (int i = 0; i < startLine - 1 && i < lines.length; i++) {
                startPos += lines[i].length() + 1;
            }
            startPos += Math.max(0, startCol - 1);

            // 计算结束位置
            int endPos = 0;
            for (int i = 0; i < endLine - 1 && i < lines.length; i++) {
                endPos += lines[i].length() + 1;
            }
            endPos += endCol;

            // 选中范围
            editorArea.selectRange(startPos, endPos);
            editorArea.requestFocus();

            // 闪烁动画
            animateHighlight();
        });
    }

    /**
     * 滚动到指定行
     */
    private void scrollToLine(int lineNum) {
        String text = editorArea.getText();
        int totalLines = text.split("\n", -1).length;

        if (totalLines > 0) {
            double scrollPos = (double) (lineNum - 1) / totalLines;
            scrollPos = Math.max(0, Math.min(1, scrollPos - 0.1)); // 稍微往上一点
            editorScrollPane.setVvalue(scrollPos);
        }
    }

    /**
     * 更新行号高亮
     */
    private void updateLineNumberHighlight(int highlightLine) {
        for (int i = 0; i < lineNumberPane.getChildren().size(); i++) {
            Node node = lineNumberPane.getChildren().get(i);
            if (node instanceof Label) {
                Label label = (Label) node;
                if (i == highlightLine - 1) {
                    label.setStyle("-fx-background-color: #fff3e0; -fx-text-fill: #e65100; " +
                        "-fx-font-weight: bold; -fx-padding: 0 5;");
                } else {
                    label.setStyle("-fx-text-fill: #858585; -fx-padding: 0 5;");
                }
            }
        }
    }

    /**
     * 高亮闪烁动画
     */
    private void animateHighlight() {
        String originalStyle = editorArea.getStyle();
        String highlightStyle = originalStyle +
            " -fx-border-color: #ff9800; -fx-border-width: 2;";

        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(editorArea.styleProperty(), highlightStyle)),
            new KeyFrame(Duration.millis(200)),
            new KeyFrame(Duration.millis(400),
                new KeyValue(editorArea.styleProperty(), originalStyle)),
            new KeyFrame(Duration.millis(600),
                new KeyValue(editorArea.styleProperty(), highlightStyle)),
            new KeyFrame(Duration.millis(800),
                new KeyValue(editorArea.styleProperty(), originalStyle))
        );

        timeline.play();
    }

    /**
     * 查找配置键所在的行号
     * @return 行号 (1-based)，未找到返回 -1
     */
    private int findConfigKeyLine(String content, String configKey) {
        if (content == null || configKey == null) return -1;

        String[] lines = content.split("\n");
        String[] keyParts = configKey.split("\\.");

        int currentIndent = -1;
        int targetPartIndex = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int indent = countLeadingSpaces(line);
            String trimmed = line.trim();

            // 跳过空行和注释
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            // 检查当前层级的键
            String currentKey = keyParts[targetPartIndex];
            if (trimmed.startsWith(currentKey + ":") ||
                trimmed.startsWith(currentKey + " :")) {

                if (targetPartIndex == keyParts.length - 1) {
                    return i + 1; // 找到目标行
                }

                // 进入下一层
                currentIndent = indent;
                targetPartIndex++;
            } else if (indent <= currentIndent && targetPartIndex > 0) {
                // 缩进变小，说明离开了当前层级，重置搜索
                targetPartIndex = 0;
                currentIndent = -1;

                // 重新检查当前行
                if (trimmed.startsWith(keyParts[0] + ":") ||
                    trimmed.startsWith(keyParts[0] + " :")) {
                    currentIndent = indent;
                    targetPartIndex = 1;
                }
            }
        }

        return -1;
    }

    /**
     * 计算前导空格数
     */
    private int countLeadingSpaces(String line) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') count++;
            else if (c == '\t') count += 2; // tab算2个空格
            else break;
        }
        return count;
    }

    /**
     * 设置状态消息
     */
    private void setStatus(String message, boolean isError) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: " + (isError ? "#d32f2f" : "#666") + ";");
        });
    }

    // ==================== 自定义单元格 ====================

    private static class ConfigTreeCell extends TreeCell<Object> {
        @Override
        protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            if (item instanceof ConfigFileEntry.ConfigCategory) {
                // 分类节点
                ConfigFileEntry.ConfigCategory cat = (ConfigFileEntry.ConfigCategory) item;
                Label label = new Label(cat.getDisplayName());
                label.setFont(Font.font("System", FontWeight.BOLD, 12));
                label.setStyle("-fx-text-fill: #555;");
                setGraphic(label);
                setText(null);
            } else if (item instanceof ConfigFileEntry) {
                // 文件节点
                ConfigFileEntry entry = (ConfigFileEntry) item;
                VBox box = new VBox(2);

                HBox nameBox = new HBox(5);
                nameBox.setAlignment(Pos.CENTER_LEFT);

                Label nameLabel = new Label(entry.getDisplayName());
                nameLabel.setFont(Font.font("System", 12));

                if (entry.isModified()) {
                    nameLabel.setText(entry.getDisplayName() + " *");
                    nameLabel.setStyle("-fx-text-fill: #d32f2f;");
                }

                if (entry.isSensitive()) {
                    Label badge = new Label("敏感");
                    badge.setStyle("-fx-font-size: 9px; -fx-text-fill: white; " +
                            "-fx-background-color: #FF9800; -fx-padding: 0 3; " +
                            "-fx-background-radius: 2;");
                    nameBox.getChildren().addAll(nameLabel, badge);
                } else {
                    nameBox.getChildren().add(nameLabel);
                }

                Label typeLabel = new Label(entry.getType().name());
                typeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");

                if (!entry.exists()) {
                    typeLabel.setText(typeLabel.getText() + " (不存在)");
                    typeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #d32f2f;");
                }

                box.getChildren().addAll(nameBox, typeLabel);
                setGraphic(box);
                setText(null);
            } else {
                setText(item.toString());
                setGraphic(null);
            }
        }
    }
}
