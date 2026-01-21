package red.jiuzhou.ops.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.model.ServerProcess;
import red.jiuzhou.ops.model.ServerProcess.ProcessStatus;
import red.jiuzhou.ops.model.ServerProcess.StartupPriority;
import red.jiuzhou.ops.service.ServerProcessService;

import java.io.File;
import java.time.format.DateTimeFormatter;

/**
 * Aion 服务器进程管理面板
 *
 * 功能:
 * - 显示所有服务器进程及其状态
 * - 单独启动/停止服务器
 * - 批量启动/停止
 * - 配置启动优先级和参数
 * - 实时状态监控
 */
public class ServerManagerPanel extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(ServerManagerPanel.class);

    private final ServerProcessService processService;

    // UI组件
    private TextField pathField;
    private TableView<ServerProcess> serverTable;
    private Label statusLabel;
    private Label summaryLabel;
    private ProgressBar progressBar;
    private CheckBox autoRefreshCheck;

    public ServerManagerPanel() {
        this.processService = new ServerProcessService();
        initializeUI();
        setupBindings();

        // 启动时立即刷新状态并开启自动刷新
        Platform.runLater(() -> {
            processService.refreshAllStatus();
            updateSummary();
            serverTable.refresh();

            // 默认开启自动刷新
            autoRefreshCheck.setSelected(true);
            processService.startAutoRefresh(3); // 3秒刷新一次
            setStatus("服务器管理面板已就绪，自动刷新已开启");
        });
    }

    private void initializeUI() {
        setSpacing(15);
        setPadding(new Insets(20));
        setStyle("-fx-background-color: #2b2b2b;");

        // 标题
        Label titleLabel = new Label("Aion 服务器进程管理");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.WHITE);

        // 路径配置区
        HBox pathBox = createPathConfigBox();

        // 工具栏
        HBox toolBar = createToolBar();

        // 服务器表格
        serverTable = createServerTable();
        VBox.setVgrow(serverTable, Priority.ALWAYS);

        // 状态栏
        HBox statusBar = createStatusBar();

        getChildren().addAll(titleLabel, pathBox, toolBar, serverTable, statusBar);

        // 设置状态监听
        processService.setStatusListener(this::setStatus);

        // 应用已保存的配置
        processService.applyLoadedConfig();
    }

    private HBox createPathConfigBox() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #3c3f41; -fx-background-radius: 5;");

        Label pathLabel = new Label("服务器路径:");
        pathLabel.setTextFill(Color.LIGHTGRAY);

        pathField = new TextField(processService.getServerRootPath());
        pathField.setPrefWidth(400);
        pathField.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white; -fx-border-color: #555;");
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button browseBtn = new Button("浏览...");
        browseBtn.setOnAction(e -> browseServerPath());
        styleButton(browseBtn, "#4a4a4a");

        Button rescanBtn = new Button("重新扫描");
        rescanBtn.setOnAction(e -> {
            processService.setServerRootPath(pathField.getText());
            processService.scanServerProcesses();
            processService.applyLoadedConfig();
        });
        styleButton(rescanBtn, "#2196F3");

        box.getChildren().addAll(pathLabel, pathField, browseBtn, rescanBtn);
        return box;
    }

    private HBox createToolBar() {
        HBox toolBar = new HBox(10);
        toolBar.setAlignment(Pos.CENTER_LEFT);
        toolBar.setPadding(new Insets(5, 0, 5, 0));

        // 左侧按钮组
        Button startAllBtn = new Button("启动全部");
        startAllBtn.setOnAction(e -> startAllServers());
        styleButton(startAllBtn, "#4CAF50");

        Button stopAllBtn = new Button("停止全部");
        stopAllBtn.setOnAction(e -> stopAllServers());
        styleButton(stopAllBtn, "#f44336");

        Button refreshBtn = new Button("刷新状态");
        refreshBtn.setOnAction(e -> processService.refreshAllStatus());
        styleButton(refreshBtn, "#FF9800");

        Button saveConfigBtn = new Button("保存配置");
        saveConfigBtn.setOnAction(e -> {
            processService.saveConfig();
            setStatus("配置已保存");
        });
        styleButton(saveConfigBtn, "#9C27B0");

        // 分隔
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 自动刷新
        autoRefreshCheck = new CheckBox("自动刷新 (5秒)");
        autoRefreshCheck.setTextFill(Color.LIGHTGRAY);
        autoRefreshCheck.setOnAction(e -> {
            if (autoRefreshCheck.isSelected()) {
                processService.startAutoRefresh(5);
            } else {
                processService.stopAutoRefresh();
            }
        });

        // 统计信息
        summaryLabel = new Label();
        summaryLabel.setTextFill(Color.LIGHTGRAY);
        updateSummary();

        toolBar.getChildren().addAll(
            startAllBtn, stopAllBtn, refreshBtn, saveConfigBtn,
            spacer, autoRefreshCheck, summaryLabel
        );

        return toolBar;
    }

    @SuppressWarnings("unchecked")
    private TableView<ServerProcess> createServerTable() {
        TableView<ServerProcess> table = new TableView<>();
        table.setStyle("-fx-background-color: #2b2b2b; -fx-control-inner-background: #2b2b2b;");
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // 序号列
        TableColumn<ServerProcess, String> orderCol = new TableColumn<>("#");
        orderCol.setCellValueFactory(data ->
            new SimpleStringProperty(String.valueOf(data.getValue().getStartupOrder())));
        orderCol.setPrefWidth(40);
        orderCol.setStyle("-fx-alignment: CENTER;");

        // 状态列
        TableColumn<ServerProcess, ProcessStatus> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ProcessStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status.getIcon() + " " + status.getLabel());
                    setStyle(status.getStyle() + " -fx-alignment: CENTER;");
                }
            }
        });
        statusCol.setPrefWidth(80);

        // 名称列
        TableColumn<ServerProcess, String> nameCol = new TableColumn<>("服务器名称");
        nameCol.setCellValueFactory(data -> data.getValue().displayNameProperty());
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                } else {
                    setText(name);
                    setTextFill(Color.WHITE);
                }
            }
        });
        nameCol.setPrefWidth(150);

        // 进程名列
        TableColumn<ServerProcess, String> processCol = new TableColumn<>("进程名");
        processCol.setCellValueFactory(data -> data.getValue().processNameProperty());
        processCol.setPrefWidth(150);

        // PID列
        TableColumn<ServerProcess, Number> pidCol = new TableColumn<>("PID");
        pidCol.setCellValueFactory(data -> data.getValue().pidProperty());
        pidCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number pid, boolean empty) {
                super.updateItem(pid, empty);
                if (empty || pid == null || pid.intValue() < 0) {
                    setText("-");
                } else {
                    setText(String.valueOf(pid));
                }
                setStyle("-fx-alignment: CENTER;");
            }
        });
        pidCol.setPrefWidth(70);

        // 内存列
        TableColumn<ServerProcess, String> memCol = new TableColumn<>("内存");
        memCol.setCellValueFactory(data ->
            new SimpleStringProperty(data.getValue().getFormattedMemoryUsage()));
        memCol.setPrefWidth(80);
        memCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        // 优先级列（可编辑）
        TableColumn<ServerProcess, StartupPriority> priorityCol = new TableColumn<>("启动优先级");
        priorityCol.setCellValueFactory(data -> data.getValue().priorityProperty());
        priorityCol.setCellFactory(ComboBoxTableCell.forTableColumn(
            FXCollections.observableArrayList(StartupPriority.values())
        ));
        priorityCol.setOnEditCommit(event -> {
            event.getRowValue().setPriority(event.getNewValue());
            updateSummary();
        });
        priorityCol.setPrefWidth(100);

        // 描述列
        TableColumn<ServerProcess, String> descCol = new TableColumn<>("描述");
        descCol.setCellValueFactory(data -> data.getValue().descriptionProperty());
        descCol.setPrefWidth(200);

        // 操作列
        TableColumn<ServerProcess, Void> actionCol = new TableColumn<>("操作");
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button startBtn = new Button("启动");
            private final Button stopBtn = new Button("停止");
            private final HBox box = new HBox(5, startBtn, stopBtn);

            {
                box.setAlignment(Pos.CENTER);
                styleButton(startBtn, "#4CAF50");
                styleButton(stopBtn, "#f44336");
                startBtn.setMinWidth(50);
                stopBtn.setMinWidth(50);

                startBtn.setOnAction(e -> {
                    ServerProcess server = getTableView().getItems().get(getIndex());
                    processService.startServer(server);
                });

                stopBtn.setOnAction(e -> {
                    ServerProcess server = getTableView().getItems().get(getIndex());
                    processService.stopServer(server);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ServerProcess server = getTableView().getItems().get(getIndex());
                    startBtn.setDisable(server.getStatus() == ProcessStatus.RUNNING);
                    stopBtn.setDisable(server.getStatus() != ProcessStatus.RUNNING);
                    setGraphic(box);
                }
            }
        });
        actionCol.setPrefWidth(130);

        table.getColumns().addAll(
            orderCol, statusCol, nameCol, processCol, pidCol,
            memCol, priorityCol, descCol, actionCol
        );

        table.setItems(processService.getServerProcesses());

        // 添加右键菜单
        table.setRowFactory(tv -> {
            TableRow<ServerProcess> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();

            MenuItem startItem = new MenuItem("启动");
            startItem.setOnAction(e -> processService.startServer(row.getItem()));

            MenuItem stopItem = new MenuItem("停止");
            stopItem.setOnAction(e -> processService.stopServer(row.getItem()));

            MenuItem openFolderItem = new MenuItem("打开目录");
            openFolderItem.setOnAction(e -> {
                try {
                    Runtime.getRuntime().exec("explorer.exe " + row.getItem().getWorkingDir());
                } catch (Exception ex) {
                    logger.error("打开目录失败", ex);
                }
            });

            MenuItem setRequiredItem = new MenuItem("设为必需");
            setRequiredItem.setOnAction(e -> {
                row.getItem().setPriority(StartupPriority.REQUIRED);
                updateSummary();
            });

            MenuItem setDisabledItem = new MenuItem("设为禁用");
            setDisabledItem.setOnAction(e -> {
                row.getItem().setPriority(StartupPriority.DISABLED);
                updateSummary();
            });

            contextMenu.getItems().addAll(
                startItem, stopItem, new SeparatorMenuItem(),
                openFolderItem, new SeparatorMenuItem(),
                setRequiredItem, setDisabledItem
            );

            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(contextMenu)
            );

            return row;
        });

        return table;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(15);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(10));
        statusBar.setStyle("-fx-background-color: #3c3f41; -fx-background-radius: 5;");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);

        statusLabel = new Label("就绪");
        statusLabel.setTextFill(Color.LIGHTGREEN);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        Label timeLabel = new Label();
        timeLabel.setTextFill(Color.GRAY);

        // 更新时间
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
                timeLabel.setText(java.time.LocalTime.now()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            })
        );
        timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        timeline.play();

        statusBar.getChildren().addAll(progressBar, statusLabel, timeLabel);
        return statusBar;
    }

    private void setupBindings() {
        // 监听进程列表变化，更新统计和刷新表格
        processService.getServerProcesses().addListener(
            (javafx.collections.ListChangeListener<ServerProcess>) c -> {
                Platform.runLater(() -> {
                    updateSummary();
                    serverTable.refresh();
                });
            }
        );

        // 监听每个进程的状态变化
        processService.getServerProcesses().forEach(server -> {
            server.statusProperty().addListener((obs, oldVal, newVal) -> {
                Platform.runLater(() -> {
                    updateSummary();
                    serverTable.refresh();
                });
            });
        });
    }

    private void browseServerPath() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择服务器根目录");
        chooser.setInitialDirectory(new File(processService.getServerRootPath()));

        File selected = chooser.showDialog(getScene().getWindow());
        if (selected != null) {
            pathField.setText(selected.getAbsolutePath());
            processService.setServerRootPath(selected.getAbsolutePath());
            processService.applyLoadedConfig();
        }
    }

    private void startAllServers() {
        setStatus("正在批量启动...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1);

        processService.startAllServers()
            .thenRun(() -> Platform.runLater(() -> {
                progressBar.setVisible(false);
                updateSummary();
            }));
    }

    private void stopAllServers() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认停止");
        confirm.setHeaderText("停止所有服务器");
        confirm.setContentText("确定要停止所有运行中的服务器吗？");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                setStatus("正在批量停止...");
                progressBar.setVisible(true);
                progressBar.setProgress(-1);

                processService.stopAllServers()
                    .thenRun(() -> Platform.runLater(() -> {
                        progressBar.setVisible(false);
                        updateSummary();
                    }));
            }
        });
    }

    private void updateSummary() {
        long running = processService.getServerProcesses().stream()
            .filter(s -> s.getStatus() == ProcessStatus.RUNNING)
            .count();
        long total = processService.getServerProcesses().size();
        long required = processService.getServerProcesses().stream()
            .filter(s -> s.getPriority() == StartupPriority.REQUIRED)
            .count();
        long disabled = processService.getServerProcesses().stream()
            .filter(s -> s.getPriority() == StartupPriority.DISABLED)
            .count();

        summaryLabel.setText(String.format(
            "运行: %d/%d | 必需: %d | 禁用: %d",
            running, total, required, disabled
        ));
    }

    private void setStatus(String message) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            logger.info("Server Manager: {}", message);
        });
    }

    private void styleButton(Button button, String color) {
        button.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: white; " +
            "-fx-background-radius: 3; -fx-cursor: hand;", color
        ));
        button.setOnMouseEntered(e ->
            button.setStyle(button.getStyle() + "-fx-opacity: 0.8;"));
        button.setOnMouseExited(e ->
            button.setStyle(button.getStyle().replace("-fx-opacity: 0.8;", "")));
    }

    /**
     * 清理资源
     */
    public void shutdown() {
        processService.shutdown();
    }
}
