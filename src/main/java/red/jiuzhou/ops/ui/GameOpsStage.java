package red.jiuzhou.ops.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.core.AuditLog;
import red.jiuzhou.ops.core.AuditLog.OperationType;
import red.jiuzhou.ops.core.CacheManager;
import red.jiuzhou.ops.core.EventBus;
import red.jiuzhou.ops.core.EventBus.*;
import red.jiuzhou.ops.core.OperationChain;
import red.jiuzhou.ops.db.SqlServerConnection;
import red.jiuzhou.ops.model.ProcedureCategory;
import red.jiuzhou.ops.model.StoredProcedure;
import red.jiuzhou.ops.service.CharacterService;
import red.jiuzhou.ops.service.GameOpsService;
import red.jiuzhou.util.YamlUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏运维控制中心
 *
 * 提供 SQL Server 游戏数据库的可视化运维界面：
 * - 存储过程浏览和执行
 * - 角色/公会/物品管理
 * - 快捷操作工具栏
 * - 实时状态监控
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class GameOpsStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(GameOpsStage.class);

    // Core Services (Architecture Components)
    private final AuditLog auditLog = AuditLog.getInstance();
    private final EventBus eventBus = EventBus.getInstance();
    private final CacheManager cacheManager = CacheManager.getInstance();

    // Database connection
    private SqlServerConnection connection;
    private GameOpsService opsService;
    private String currentDatabase;

    // Status Panel
    private ServerStatusPanel statusPanel;

    // Feature Panels
    private CharacterPanel characterPanel;
    private GuildPanel guildPanel;
    private ItemPanel itemPanel;
    private WhaleFallPanel whaleFallPanel;
    private ServerManagerPanel serverManagerPanel;
    private SplitPane procedureExplorer;

    // UI Components - Navigation
    private final TreeView<String> navTree = new TreeView<>();
    private final StackPane workArea = new StackPane();
    private final Label connectionStatus = new Label("未连接");
    private final ComboBox<String> databaseSelector = new ComboBox<>();

    // UI Components - Procedure Explorer
    private final TreeView<ProcedureTreeItem> procedureTree = new TreeView<>();
    private final TableView<StoredProcedure.Parameter> parameterTable = new TableView<>();
    private final TextArea sqlPreview = new TextArea();
    private final TextArea resultArea = new TextArea();

    // UI Components - Status
    private final Label statusLabel = new Label("就绪");
    private final ProgressBar progressBar = new ProgressBar(0);

    // Data
    private List<StoredProcedure> allProcedures = new ArrayList<>();
    private StoredProcedure selectedProcedure;
    private final Map<String, TextField> parameterInputs = new LinkedHashMap<>();

    public GameOpsStage() {
        setTitle("游戏运维控制中心 - Aion Game Operations");
        setScene(buildScene());
        setMinWidth(1400);
        setMinHeight(800);

        // Initialize connection from config
        initializeConnection();

        // Setup event subscriptions
        setupEventSubscriptions();

        // Log stage open
        auditLog.info(OperationType.SYSTEM, "打开运维控制台", null, null);

        setOnCloseRequest(event -> {
            cleanup();
        });
    }

    /**
     * Setup event subscriptions for decoupled architecture
     */
    private void setupEventSubscriptions() {
        // Subscribe to connection events
        eventBus.subscribe(ConnectionEvent.class, event -> {
            Platform.runLater(() -> {
                updateConnectionStatus(event.isConnected(), event.getMessage());
            });
        });

        // Subscribe to operation completed events
        eventBus.subscribe(OperationCompletedEvent.class, event -> {
            Platform.runLater(() -> {
                String msg = event.isSuccess() ? "✅ " : "❌ ";
                msg += event.getOperationType() + ": " + event.getResult();
                updateStatus(msg);
            });
        });

        // Subscribe to system events
        eventBus.subscribe(SystemEvent.class, event -> {
            if ("refresh".equals(event.getType())) {
                Platform.runLater(this::refreshAll);
            }
        });
    }

    /**
     * Cleanup resources on close
     */
    private void cleanup() {
        auditLog.info(OperationType.SYSTEM, "关闭运维控制台", null, null);

        if (statusPanel != null) {
            statusPanel.stop();
        }

        if (serverManagerPanel != null) {
            serverManagerPanel.shutdown();
        }

        if (connection != null) {
            connection.close();
        }
    }

    private void initializeConnection() {
        try {
            String host = YamlUtils.getPropertyOrDefault("sqlserver.host", "localhost");
            int port = Integer.parseInt(YamlUtils.getPropertyOrDefault("sqlserver.port", "1433"));
            String username = YamlUtils.getPropertyOrDefault("sqlserver.username", "sa");
            String password = YamlUtils.getPropertyOrDefault("sqlserver.password", "aion.5201314");
            String primaryDb = YamlUtils.getPropertyOrDefault("sqlserver.databases.primary", "AionWorldLive");

            connection = SqlServerConnection.create(host, port, username, password);
            opsService = new GameOpsService(connection);
            currentDatabase = primaryDb;

            // Test connection in background
            connectToDatabase(primaryDb);

            auditLog.info(OperationType.SYSTEM, "初始化连接", host + ":" + port, null);
        } catch (Exception e) {
            log.error("初始化 SQL Server 连接失败", e);
            updateConnectionStatus(false, "配置错误: " + e.getMessage());
            auditLog.failure(OperationType.SYSTEM, "初始化连接", null, e.getMessage());
        }
    }

    /**
     * Refresh all data
     */
    private void refreshAll() {
        loadProcedures();
        if (statusPanel != null) {
            statusPanel.refresh();
        }
    }

    private Scene buildScene() {
        BorderPane root = new BorderPane();

        // Top - Connection Bar
        root.setTop(buildConnectionBar());

        // Center - Main Split Pane with Status Panel
        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);

        // Navigation + Work Area split
        SplitPane leftSplit = new SplitPane();
        leftSplit.setOrientation(Orientation.HORIZONTAL);
        leftSplit.getItems().addAll(buildNavigationPane(), buildWorkArea());
        leftSplit.setDividerPositions(0.22);

        // Right panel - Status Monitoring
        statusPanel = new ServerStatusPanel(opsService);
        statusPanel.setMinWidth(320);
        statusPanel.setMaxWidth(400);

        mainSplit.getItems().addAll(leftSplit, statusPanel);
        mainSplit.setDividerPositions(0.75);

        root.setCenter(mainSplit);

        // Bottom - Status Bar & Quick Actions
        root.setBottom(buildBottomSection());

        return new Scene(root, 1400, 800);
    }

    private HBox buildConnectionBar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #2c3e50;");

        Label icon = new Label("📡");
        icon.setStyle("-fx-font-size: 18px;");

        Label connLabel = new Label("连接:");
        connLabel.setStyle("-fx-text-fill: white;");

        connectionStatus.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");

        Label dbLabel = new Label("数据库:");
        dbLabel.setStyle("-fx-text-fill: white;");

        databaseSelector.setMinWidth(180);
        databaseSelector.setOnAction(e -> {
            String selected = databaseSelector.getValue();
            if (selected != null && !selected.equals(currentDatabase)) {
                connectToDatabase(selected);
            }
        });

        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setOnAction(e -> refreshDatabaseList());

        Button testBtn = new Button("🔌 测试连接");
        testBtn.setOnAction(e -> testConnection());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label serverInfo = new Label();
        serverInfo.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 11px;");
        serverInfo.textProperty().bind(connectionStatus.textProperty());

        bar.getChildren().addAll(
                icon, connLabel, connectionStatus,
                new Separator(Orientation.VERTICAL),
                dbLabel, databaseSelector, refreshBtn, testBtn,
                spacer, serverInfo
        );

        return bar;
    }

    private VBox buildNavigationPane() {
        VBox nav = new VBox(8);
        nav.setPadding(new Insets(8));
        nav.setStyle("-fx-background-color: #34495e;");

        Label title = new Label("📋 功能导航");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

        // Build navigation tree
        TreeItem<String> root = new TreeItem<>("功能模块");
        root.setExpanded(true);

        // Add category nodes
        for (ProcedureCategory category : ProcedureCategory.displayOrder()) {
            TreeItem<String> catItem = new TreeItem<>(category.getDisplayWithIcon());
            root.getChildren().add(catItem);
        }

        navTree.setRoot(root);
        navTree.setShowRoot(false);
        navTree.setStyle("-fx-background-color: #34495e;");

        navTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String selected = newVal.getValue();
                showCategoryPanel(selected);
            }
        });

        VBox.setVgrow(navTree, Priority.ALWAYS);
        nav.getChildren().addAll(title, navTree);

        return nav;
    }

    private StackPane buildWorkArea() {
        workArea.setPadding(new Insets(8));

        // Initialize panels (lazy loaded)
        procedureExplorer = buildProcedureExplorer();

        // Default view - Procedure Explorer
        workArea.getChildren().add(procedureExplorer);

        return workArea;
    }

    /**
     * Initialize feature panels lazily
     */
    private void initializePanels() {
        if (characterPanel == null && connection != null) {
            characterPanel = new CharacterPanel(new CharacterService(connection));
        }
        if (guildPanel == null && opsService != null) {
            guildPanel = new GuildPanel(opsService);
        }
        if (itemPanel == null && opsService != null) {
            itemPanel = new ItemPanel(opsService);
        }
        if (whaleFallPanel == null && connection != null) {
            whaleFallPanel = new WhaleFallPanel(connection);
        }
        if (serverManagerPanel == null) {
            serverManagerPanel = new ServerManagerPanel();
        }
    }

    /**
     * Switch to a specific panel in work area
     */
    private void switchToPanel(javafx.scene.Node panel) {
        workArea.getChildren().clear();
        workArea.getChildren().add(panel);
    }

    private SplitPane buildProcedureExplorer() {
        SplitPane explorer = new SplitPane();
        explorer.setOrientation(Orientation.HORIZONTAL);

        // Left - Procedure Tree
        VBox leftPane = new VBox(8);
        leftPane.setPadding(new Insets(8));

        Label treeTitle = new Label("🔧 存储过程浏览器");
        treeTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        TextField searchField = new TextField();
        searchField.setPromptText("搜索存储过程...");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterProcedures(newVal));

        procedureTree.setShowRoot(false);
        VBox.setVgrow(procedureTree, Priority.ALWAYS);

        procedureTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getValue() != null && newVal.getValue().procedure != null) {
                selectProcedure(newVal.getValue().procedure);
            }
        });

        leftPane.getChildren().addAll(treeTitle, searchField, procedureTree);

        // Right - Procedure Details
        VBox rightPane = buildProcedureDetailPane();

        explorer.getItems().addAll(leftPane, rightPane);
        explorer.setDividerPositions(0.35);

        return explorer;
    }

    // 存储过程描述显示标签
    private Label procedureDescLabel;

    private VBox buildProcedureDetailPane() {
        VBox pane = new VBox(8);
        pane.setPadding(new Insets(8));

        // Description section - 显示存储过程注释/描述
        TitledPane descSection = new TitledPane();
        descSection.setText("📖 存储过程描述");
        descSection.setCollapsible(true);
        descSection.setExpanded(true);

        procedureDescLabel = new Label("选择一个存储过程查看描述");
        procedureDescLabel.setWrapText(true);
        procedureDescLabel.setStyle("-fx-padding: 8; -fx-background-color: #f8f9fa; -fx-background-radius: 4;");
        procedureDescLabel.setMinHeight(40);
        descSection.setContent(procedureDescLabel);

        // Parameters section
        TitledPane paramSection = new TitledPane();
        paramSection.setText("📝 参数");
        paramSection.setCollapsible(false);

        VBox paramContent = new VBox(8);
        paramContent.setPadding(new Insets(8));
        paramContent.setId("parameterContainer");
        paramSection.setContent(paramContent);

        // SQL Preview
        TitledPane sqlSection = new TitledPane();
        sqlSection.setText("📄 SQL 预览");
        sqlSection.setCollapsible(true);
        sqlSection.setExpanded(false);

        sqlPreview.setEditable(false);
        sqlPreview.setPrefRowCount(4);
        sqlPreview.setStyle("-fx-font-family: 'Consolas', monospace;");
        sqlSection.setContent(sqlPreview);

        // Execute Button
        HBox actionBar = new HBox(8);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        Button executeBtn = new Button("▶ 执行");
        executeBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        executeBtn.setOnAction(e -> executeProcedure());

        Button clearBtn = new Button("🗑 清空");
        clearBtn.setOnAction(e -> clearResults());

        Label warningLabel = new Label();
        warningLabel.setId("warningLabel");
        warningLabel.setStyle("-fx-text-fill: #e74c3c;");

        actionBar.getChildren().addAll(executeBtn, clearBtn, warningLabel);

        // Results section
        TitledPane resultSection = new TitledPane();
        resultSection.setText("📊 执行结果");
        resultSection.setCollapsible(false);

        resultArea.setEditable(false);
        resultArea.setStyle("-fx-font-family: 'Consolas', monospace;");
        VBox.setVgrow(resultArea, Priority.ALWAYS);
        resultSection.setContent(resultArea);
        VBox.setVgrow(resultSection, Priority.ALWAYS);

        pane.getChildren().addAll(descSection, paramSection, sqlSection, actionBar, resultSection);
        return pane;
    }

    private VBox buildBottomSection() {
        VBox bottom = new VBox();

        // Quick Actions Bar
        HBox quickBar = new HBox(12);
        quickBar.setPadding(new Insets(8, 12, 8, 12));
        quickBar.setAlignment(Pos.CENTER_LEFT);
        quickBar.setStyle("-fx-background-color: #ecf0f1;");

        Label quickLabel = new Label("⚡ 快捷操作:");
        quickLabel.setStyle("-fx-font-weight: bold;");

        Button queryCharBtn = createQuickButton("🔍 查角色", "aion_GetCharInfo");
        Button sendItemBtn = createQuickButton("📦 发物品", "aion_AddItemAmount");
        Button banAccountBtn = createQuickButton("🚫 封号", "aion_BanAccount");
        Button cleanDataBtn = createQuickButton("🧹 清数据", "aion_CheckOldUserOfLowlevelToDelete");

        Button cacheBtn = new Button("📦 缓存状态");
        cacheBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        cacheBtn.setOnAction(e -> showCacheStats());

        Button auditBtn = new Button("📝 审计日志");
        auditBtn.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white;");
        auditBtn.setOnAction(e -> showAuditLog());

        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        refreshBtn.setOnAction(e -> refreshAll());

        quickBar.getChildren().addAll(
                quickLabel, queryCharBtn, sendItemBtn, banAccountBtn,
                cleanDataBtn, cacheBtn, auditBtn, refreshBtn
        );

        // Status Bar
        HBox statusBar = new HBox(12);
        statusBar.setPadding(new Insets(4, 12, 4, 12));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: #bdc3c7;");

        progressBar.setPrefWidth(150);
        progressBar.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label procCount = new Label("存储过程: 0");
        procCount.setId("procCountLabel");

        statusBar.getChildren().addAll(statusLabel, progressBar, spacer, procCount);

        bottom.getChildren().addAll(quickBar, statusBar);
        return bottom;
    }

    private Button createQuickButton(String text, String procedureName) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        if (procedureName != null) {
            btn.setOnAction(e -> quickExecute(procedureName));
        } else {
            btn.setOnAction(e -> showAlert(Alert.AlertType.INFORMATION, "提示", "功能开发中..."));
        }
        return btn;
    }

    // ==================== Database Operations ====================

    private void connectToDatabase(String database) {
        updateStatus("正在连接到 " + database + "...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                connection.connect(database);
                return connection.testConnection();
            }
        };

        task.setOnSucceeded(e -> {
            boolean success = task.getValue();
            if (success) {
                currentDatabase = database;
                updateConnectionStatus(true, connection.getConnectionSummary());
                loadProcedures();
            } else {
                updateConnectionStatus(false, "连接失败");
            }
            progressBar.setVisible(false);
        });

        task.setOnFailed(e -> {
            updateConnectionStatus(false, "错误: " + task.getException().getMessage());
            progressBar.setVisible(false);
        });

        new Thread(task).start();
    }

    private void refreshDatabaseList() {
        if (connection == null) return;

        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                return connection.listDatabases();
            }
        };

        task.setOnSucceeded(e -> {
            List<String> databases = task.getValue();
            Platform.runLater(() -> {
                databaseSelector.setItems(FXCollections.observableArrayList(databases));
                if (currentDatabase != null) {
                    databaseSelector.setValue(currentDatabase);
                }
            });
        });

        new Thread(task).start();
    }

    private void testConnection() {
        if (connection == null) {
            showAlert(Alert.AlertType.WARNING, "警告", "请先配置数据库连接");
            return;
        }

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (connection.testConnection()) {
                    return connection.getServerVersion();
                }
                throw new RuntimeException("连接失败");
            }
        };

        task.setOnSucceeded(e -> {
            String version = task.getValue();
            showAlert(Alert.AlertType.INFORMATION, "连接成功",
                    "已成功连接到 SQL Server\n\n" + version);
        });

        task.setOnFailed(e -> {
            showAlert(Alert.AlertType.ERROR, "连接失败",
                    "无法连接到数据库: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void loadProcedures() {
        if (connection == null) return;

        updateStatus("正在加载存储过程...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        Task<List<StoredProcedure>> task = new Task<>() {
            @Override
            protected List<StoredProcedure> call() throws Exception {
                return connection.listStoredProcedures();
            }
        };

        task.setOnSucceeded(e -> {
            allProcedures = task.getValue();
            Platform.runLater(() -> {
                buildProcedureTree(allProcedures);
                updateStatus("已加载 " + allProcedures.size() + " 个存储过程");
                updateProcCount(allProcedures.size());
            });
            progressBar.setVisible(false);
        });

        task.setOnFailed(e -> {
            log.error("加载存储过程失败", task.getException());
            updateStatus("加载失败: " + task.getException().getMessage());
            progressBar.setVisible(false);
        });

        new Thread(task).start();
    }

    private void buildProcedureTree(List<StoredProcedure> procedures) {
        TreeItem<ProcedureTreeItem> root = new TreeItem<>(new ProcedureTreeItem("存储过程", null));
        root.setExpanded(true);

        // Group by category
        Map<ProcedureCategory, List<StoredProcedure>> grouped = procedures.stream()
                .collect(Collectors.groupingBy(StoredProcedure::category));

        for (ProcedureCategory category : ProcedureCategory.displayOrder()) {
            List<StoredProcedure> procs = grouped.getOrDefault(category, Collections.emptyList());
            if (!procs.isEmpty()) {
                TreeItem<ProcedureTreeItem> catItem = new TreeItem<>(
                        new ProcedureTreeItem(category.getDisplayWithIcon() + " (" + procs.size() + ")", null)
                );

                for (StoredProcedure proc : procs) {
                    TreeItem<ProcedureTreeItem> procItem = new TreeItem<>(
                            new ProcedureTreeItem(proc.name(), proc)
                    );
                    catItem.getChildren().add(procItem);
                }

                root.getChildren().add(catItem);
            }
        }

        procedureTree.setRoot(root);

        // 设置 CellFactory 添加 Tooltip 显示描述
        procedureTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(ProcedureTreeItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item.displayName());
                    if (item.procedure() != null) {
                        String desc = item.procedure().description();
                        if (desc != null && !desc.isEmpty()) {
                            setTooltip(new Tooltip(item.procedure().name() + "\n\n" + desc));
                        } else {
                            setTooltip(new Tooltip(item.procedure().name() + " (暂无描述)"));
                        }
                    }
                }
            }
        });
    }

    private void filterProcedures(String filter) {
        if (filter == null || filter.isBlank()) {
            buildProcedureTree(allProcedures);
            return;
        }

        String lowerFilter = filter.toLowerCase();
        List<StoredProcedure> filtered = allProcedures.stream()
                .filter(p -> p.name().toLowerCase().contains(lowerFilter))
                .toList();

        buildProcedureTree(filtered);
    }

    private void selectProcedure(StoredProcedure procedure) {
        this.selectedProcedure = procedure;
        parameterInputs.clear();

        // Update description display
        if (procedureDescLabel != null) {
            String desc = procedure.description();
            if (desc == null || desc.isEmpty()) {
                procedureDescLabel.setText("📌 " + procedure.name() + "\n（暂无描述）");
                procedureDescLabel.setStyle("-fx-padding: 8; -fx-background-color: #fff3cd; -fx-background-radius: 4;");
            } else {
                procedureDescLabel.setText("📌 " + procedure.name() + "\n\n" + desc);
                procedureDescLabel.setStyle("-fx-padding: 8; -fx-background-color: #d4edda; -fx-background-radius: 4;");
            }
        }

        // Load parameters
        Task<List<StoredProcedure.Parameter>> task = new Task<>() {
            @Override
            protected List<StoredProcedure.Parameter> call() throws Exception {
                return connection.getProcedureParameters(procedure.name());
            }
        };

        task.setOnSucceeded(e -> {
            List<StoredProcedure.Parameter> params = task.getValue();
            Platform.runLater(() -> buildParameterInputs(params));
        });

        new Thread(task).start();

        // Update warning label
        Label warningLabel = (Label) getScene().lookup("#warningLabel");
        if (warningLabel != null) {
            if (procedure.isDangerous()) {
                warningLabel.setText("⚠️ 危险操作！请谨慎执行");
            } else {
                warningLabel.setText("");
            }
        }
    }

    private void buildParameterInputs(List<StoredProcedure.Parameter> params) {
        VBox container = (VBox) getScene().lookup("#parameterContainer");
        if (container == null) return;

        container.getChildren().clear();
        parameterInputs.clear();

        if (params.isEmpty()) {
            container.getChildren().add(new Label("此存储过程没有参数"));
            updateSqlPreview();
            return;
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        int row = 0;
        for (StoredProcedure.Parameter param : params) {
            if (param.isInput()) {
                Label label = new Label("@" + param.name() + " (" + param.type() + "):");
                TextField input = new TextField();
                input.setPromptText(param.hasDefault() ? "可选" : "必填");
                input.textProperty().addListener((obs, oldVal, newVal) -> updateSqlPreview());

                parameterInputs.put(param.name(), input);

                grid.add(label, 0, row);
                grid.add(input, 1, row);
                row++;
            }
        }

        container.getChildren().add(grid);
        updateSqlPreview();
    }

    private void updateSqlPreview() {
        if (selectedProcedure == null) {
            sqlPreview.clear();
            return;
        }

        StringBuilder sql = new StringBuilder("EXEC ");
        sql.append(selectedProcedure.name());

        List<String> paramStrings = new ArrayList<>();
        for (Map.Entry<String, TextField> entry : parameterInputs.entrySet()) {
            String value = entry.getValue().getText();
            if (!value.isBlank()) {
                paramStrings.add("@" + entry.getKey() + " = " + formatValue(value));
            }
        }

        if (!paramStrings.isEmpty()) {
            sql.append(" ").append(String.join(", ", paramStrings));
        }

        sqlPreview.setText(sql.toString());
    }

    private String formatValue(String value) {
        // Simple type detection
        if (value.matches("-?\\d+")) {
            return value;  // Integer
        } else if (value.matches("-?\\d+\\.\\d+")) {
            return value;  // Decimal
        } else {
            return "'" + value.replace("'", "''") + "'";  // String
        }
    }

    private void executeProcedure() {
        if (selectedProcedure == null) {
            showAlert(Alert.AlertType.WARNING, "警告", "请先选择一个存储过程");
            return;
        }

        // Confirm dangerous operations
        if (selectedProcedure.isDangerous()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("确认执行");
            confirm.setHeaderText("危险操作警告");
            confirm.setContentText("您即将执行一个可能修改或删除数据的操作:\n\n" +
                    selectedProcedure.name() + "\n\n确定要继续吗？");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                auditLog.warning(OperationType.QUERY, "取消执行", selectedProcedure.name(), "用户取消危险操作");
                return;
            }
        }

        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, TextField> entry : parameterInputs.entrySet()) {
            String value = entry.getValue().getText();
            if (!value.isBlank()) {
                params.put(entry.getKey(), parseValue(value));
            }
        }

        String procName = selectedProcedure.name();
        String paramsStr = params.toString();

        updateStatus("正在执行 " + procName + "...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        // Use operation chain for reliability
        Task<OperationChain.ChainResult> task = new Task<>() {
            @Override
            protected OperationChain.ChainResult call() throws Exception {
                final List<Map<String, Object>>[] results = new List[]{null};

                OperationChain chain = OperationChain.builder("执行存储过程: " + procName)
                        .step("参数验证", () -> {
                            // Validate parameters
                            log.debug("验证参数: {}", params);
                        }, null)
                        .step("执行过程", () -> {
                            results[0] = connection.callProcedure(procName, params);
                        }, () -> {
                            // Rollback - log the failure
                            log.warn("存储过程执行失败，记录回滚: {}", procName);
                        })
                        .step("记录审计", () -> {
                            auditLog.success(OperationType.QUERY, "执行存储过程",
                                    procName, "返回 " + results[0].size() + " 行");
                        }, null)
                        .build();

                OperationChain.ChainResult chainResult = chain.execute();

                // Attach results to chain result metadata
                if (chainResult.isSuccess() && results[0] != null) {
                    chainResult = new OperationChain.ChainResult(
                            chainResult.chainId(),
                            chainResult.chainName(),
                            chainResult.success(),
                            chainResult.errorMessage(),
                            chainResult.completedSteps(),
                            chainResult.totalSteps(),
                            chainResult.totalDuration(),
                            chainResult.finalState(),
                            chainResult.failedStepName(),
                            chainResult.startTime(),
                            chainResult.endTime(),
                            Map.of("results", results[0])
                    );
                }

                return chainResult;
            }
        };

        task.setOnSucceeded(e -> {
            OperationChain.ChainResult chainResult = task.getValue();
            progressBar.setVisible(false);

            if (chainResult.isSuccess()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> results =
                        (List<Map<String, Object>>) chainResult.metadata().get("results");

                if (results != null) {
                    Platform.runLater(() -> displayResults(results));
                    updateStatus("✅ 执行完成，返回 " + results.size() + " 行 (" +
                            chainResult.getDuration() + "ms)");
                } else {
                    updateStatus("✅ 执行完成");
                }

                // Publish success event
                eventBus.publishAsync(new OperationCompletedEvent(
                        UUID.randomUUID().toString(),
                        "存储过程",
                        true,
                        procName + " 执行成功"
                ));
            } else {
                resultArea.setText("错误: " + chainResult.error().getMessage());
                updateStatus("❌ 执行失败: " + chainResult.failedStep());

                auditLog.failure(OperationType.QUERY, "执行存储过程",
                        procName, chainResult.error().getMessage());

                // Publish failure event
                eventBus.publishAsync(new OperationCompletedEvent(
                        UUID.randomUUID().toString(),
                        "存储过程",
                        false,
                        procName + " 执行失败"
                ));
            }
        });

        task.setOnFailed(e -> {
            log.error("执行存储过程失败", task.getException());
            resultArea.setText("错误: " + task.getException().getMessage());
            updateStatus("❌ 执行失败");
            progressBar.setVisible(false);

            auditLog.failure(OperationType.QUERY, "执行存储过程",
                    procName, task.getException().getMessage());
        });

        Thread.startVirtualThread(task);
    }

    private Object parseValue(String value) {
        if (value.matches("-?\\d+")) {
            return Long.parseLong(value);
        } else if (value.matches("-?\\d+\\.\\d+")) {
            return Double.parseDouble(value);
        }
        return value;
    }

    private void displayResults(List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            resultArea.setText("执行成功，无返回数据");
            return;
        }

        StringBuilder sb = new StringBuilder();

        // Header
        Set<String> columns = results.get(0).keySet();
        sb.append(String.join("\t|\t", columns)).append("\n");
        sb.append("-".repeat(80)).append("\n");

        // Data
        for (Map<String, Object> row : results) {
            List<String> values = columns.stream()
                    .map(col -> String.valueOf(row.get(col)))
                    .toList();
            sb.append(String.join("\t|\t", values)).append("\n");
        }

        sb.append("\n总计: ").append(results.size()).append(" 行");
        resultArea.setText(sb.toString());
    }

    private void clearResults() {
        resultArea.clear();
        parameterInputs.values().forEach(tf -> tf.clear());
        sqlPreview.clear();
    }

    private void quickExecute(String procedureName) {
        // Find procedure by name
        Optional<StoredProcedure> proc = allProcedures.stream()
                .filter(p -> p.name().equalsIgnoreCase(procedureName))
                .findFirst();

        if (proc.isPresent()) {
            selectProcedure(proc.get());
            // Focus on first parameter input
            if (!parameterInputs.isEmpty()) {
                parameterInputs.values().iterator().next().requestFocus();
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "提示", "未找到存储过程: " + procedureName);
        }
    }

    private void showCategoryPanel(String category) {
        // Initialize panels if needed
        initializePanels();

        // Check for special panels first
        if (category.contains("服务器管理")) {
            switchToPanel(serverManagerPanel);
            auditLog.info(OperationType.SYSTEM, "切换面板", "服务器管理", null);
            return;
        } else if (category.contains("角色管理")) {
            switchToPanel(characterPanel);
            auditLog.info(OperationType.SYSTEM, "切换面板", "角色管理", null);
            return;
        } else if (category.contains("公会")) {
            switchToPanel(guildPanel);
            auditLog.info(OperationType.SYSTEM, "切换面板", "公会管理", null);
            return;
        } else if (category.contains("物品")) {
            switchToPanel(itemPanel);
            auditLog.info(OperationType.SYSTEM, "切换面板", "物品管理", null);
            return;
        } else if (category.contains("鲸落")) {
            switchToPanel(whaleFallPanel);
            auditLog.info(OperationType.SYSTEM, "切换面板", "鲸落系统", null);
            return;
        } else if (category.contains("存储过程")) {
            switchToPanel(procedureExplorer);
            return;
        }

        // Filter procedures by category for procedure explorer
        switchToPanel(procedureExplorer);
        for (ProcedureCategory cat : ProcedureCategory.values()) {
            if (category.contains(cat.getDisplayName())) {
                List<StoredProcedure> filtered = allProcedures.stream()
                        .filter(p -> p.category() == cat)
                        .toList();
                buildProcedureTree(filtered);
                break;
            }
        }
    }

    // ==================== Cache & Audit ====================

    /**
     * Show cache statistics dialog
     */
    private void showCacheStats() {
        CacheManager.CacheStats stats = cacheManager.getStats();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("缓存状态");
        dialog.setHeaderText("📦 缓存管理器状态");

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));

        // Stats grid
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(8);

        int row = 0;
        grid.add(new Label("当前容量:"), 0, row);
        grid.add(new Label(stats.size() + " / " + stats.maxSize()), 1, row++);

        grid.add(new Label("命中率:"), 0, row);
        ProgressBar hitBar = new ProgressBar(stats.hitRate());
        hitBar.setPrefWidth(200);
        Label hitLabel = new Label(String.format("%.1f%%", stats.hitRate() * 100));
        HBox hitBox = new HBox(8, hitBar, hitLabel);
        grid.add(hitBox, 1, row++);

        grid.add(new Label("命中次数:"), 0, row);
        grid.add(new Label(String.valueOf(stats.hits())), 1, row++);

        grid.add(new Label("未命中次数:"), 0, row);
        grid.add(new Label(String.valueOf(stats.misses())), 1, row++);

        grid.add(new Label("驱逐次数:"), 0, row);
        grid.add(new Label(String.valueOf(stats.evictions())), 1, row++);

        content.getChildren().add(grid);

        // Action buttons
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER);

        Button clearBtn = new Button("🗑 清空缓存");
        clearBtn.setOnAction(e -> {
            cacheManager.clear();
            auditLog.info(OperationType.SYSTEM, "清空缓存", null, null);
            showAlert(Alert.AlertType.INFORMATION, "成功", "缓存已清空");
            dialog.close();
        });

        Button resetBtn = new Button("📊 重置统计");
        resetBtn.setOnAction(e -> {
            cacheManager.resetStats();
            showAlert(Alert.AlertType.INFORMATION, "成功", "统计已重置");
            dialog.close();
        });

        actions.getChildren().addAll(clearBtn, resetBtn);
        content.getChildren().add(actions);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    /**
     * Show audit log dialog
     */
    private void showAuditLog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("审计日志");
        dialog.setHeaderText("📝 最近操作记录");

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setPrefWidth(700);
        content.setPrefHeight(500);

        // Filter controls
        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> typeFilter = new ComboBox<>();
        typeFilter.getItems().addAll("全部", "查询", "角色", "公会", "物品", "系统");
        typeFilter.setValue("全部");

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("全部", "成功", "失败", "警告", "信息");
        statusFilter.setValue("全部");

        Label countLabel = new Label();

        filterBar.getChildren().addAll(
                new Label("类型:"), typeFilter,
                new Label("状态:"), statusFilter,
                countLabel
        );

        // Log table
        TableView<AuditLog.AuditEntry> logTable = new TableView<>();
        VBox.setVgrow(logTable, Priority.ALWAYS);

        TableColumn<AuditLog.AuditEntry, String> timeCol = new TableColumn<>("时间");
        timeCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().timestamp().format(
                                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                        )
                ));
        timeCol.setPrefWidth(80);

        TableColumn<AuditLog.AuditEntry, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().status().getIcon()
                ));
        statusCol.setPrefWidth(50);

        TableColumn<AuditLog.AuditEntry, String> opCol = new TableColumn<>("操作");
        opCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().operation()));
        opCol.setPrefWidth(150);

        TableColumn<AuditLog.AuditEntry, String> targetCol = new TableColumn<>("目标");
        targetCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().target() != null ? cell.getValue().target() : ""
                ));
        targetCol.setPrefWidth(200);

        TableColumn<AuditLog.AuditEntry, String> detailCol = new TableColumn<>("详情");
        detailCol.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().detail() != null ? cell.getValue().detail() : ""
                ));
        detailCol.setPrefWidth(200);

        logTable.getColumns().addAll(timeCol, statusCol, opCol, targetCol, detailCol);

        // Load data
        Runnable loadData = () -> {
            List<AuditLog.AuditEntry> entries = auditLog.getRecentEntries(100);
            logTable.setItems(FXCollections.observableArrayList(entries));
            countLabel.setText("共 " + entries.size() + " 条记录");
        };
        loadData.run();

        // Stats section
        HBox statsBar = new HBox(20);
        statsBar.setPadding(new Insets(8));
        statsBar.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 4;");

        AuditLog.AuditStats stats = auditLog.getStats();
        statsBar.getChildren().addAll(
                new Label("总操作: " + stats.totalOperations()),
                new Label("✅ 成功: " + stats.successCount()),
                new Label("❌ 失败: " + stats.failureCount()),
                new Label("⚠️ 警告: " + stats.warningCount())
        );

        content.getChildren().addAll(filterBar, logTable, statsBar);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    // ==================== UI Helpers ====================

    private void updateConnectionStatus(boolean connected, String message) {
        Platform.runLater(() -> {
            connectionStatus.setText(message);
            connectionStatus.setStyle(connected ?
                    "-fx-text-fill: #27ae60; -fx-font-weight: bold;" :
                    "-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        });
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    private void updateProcCount(int count) {
        Platform.runLater(() -> {
            Label label = (Label) getScene().lookup("#procCountLabel");
            if (label != null) {
                label.setText("存储过程: " + count);
            }
        });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    // ==================== Inner Classes ====================

    /**
     * Wrapper for TreeView items
     */
    private record ProcedureTreeItem(String displayName, StoredProcedure procedure) {
        @Override
        public String toString() {
            return displayName;
        }
    }
}
