package red.jiuzhou.server.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.server.dao.ServerConfigFileDao;
import red.jiuzhou.server.model.ServerConfigFile;
import red.jiuzhou.server.service.ServerLogAnalyzer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 服务器配置文件管理器 UI
 * 展示和管理服务器实际加载的 XML 文件清单 - "文件层的唯一真理"
 */
public class ServerConfigFileManagerStage extends Stage {
    private static final Logger log = LoggerFactory.getLogger(ServerConfigFileManagerStage.class);

    private ServerConfigFileDao dao;
    private ServerLogAnalyzer analyzer;

    private TableView<ServerConfigFile> tableView;
    private ObservableList<ServerConfigFile> configFiles;

    private Label statusLabel;
    private ComboBox<String> filterComboBox;

    private boolean initError = false;
    private String initErrorMessage = "";

    public ServerConfigFileManagerStage() {
        log.info("开始创建 ServerConfigFileManagerStage");

        try {
            this.dao = new ServerConfigFileDao();
            log.info("ServerConfigFileDao 创建成功");
        } catch (Exception e) {
            log.error("创建 ServerConfigFileDao 失败", e);
            initError = true;
            initErrorMessage = "数据库连接失败: " + e.getMessage();
        }

        try {
            this.analyzer = new ServerLogAnalyzer(dao);
            log.info("ServerLogAnalyzer 创建成功");
        } catch (Exception e) {
            log.error("创建 ServerLogAnalyzer 失败", e);
            if (!initError) {
                initError = true;
                initErrorMessage = "日志分析器初始化失败: " + e.getMessage();
            }
        }

        setTitle("服务器配置文件清单 - 文件层的唯一真理");
        setWidth(1200);
        setHeight(700);

        try {
            VBox root = createContent();
            Scene scene = new Scene(root);
            setScene(scene);
            log.info("UI 创建成功");
        } catch (Exception e) {
            log.error("创建 UI 失败", e);
            // 创建一个错误提示界面
            VBox errorRoot = new VBox(20);
            errorRoot.setAlignment(Pos.CENTER);
            errorRoot.setPadding(new Insets(50));
            Label errorLabel = new Label("界面初始化失败:\n" + e.getMessage());
            errorLabel.setStyle("-fx-text-fill: red; -fx-font-size: 14px;");
            errorRoot.getChildren().add(errorLabel);
            setScene(new Scene(errorRoot));
        }

        // 如果初始化有错误，显示错误信息
        if (initError) {
            Platform.runLater(() -> {
                updateStatus("初始化错误: " + initErrorMessage);
            });
        } else {
            loadConfigFiles();
        }
    }

    private VBox createContent() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        // 顶部工具栏
        HBox toolbar = createToolbar();
        log.info("工具栏创建成功");

        // 过滤栏
        HBox filterBar = createFilterBar();
        log.info("过滤栏创建成功");

        // 表格
        tableView = createTableView();
        log.info("表格创建成功");

        // 底部状态栏
        HBox statusBar = createStatusBar();
        log.info("状态栏创建成功");

        root.getChildren().addAll(toolbar, filterBar, tableView, statusBar);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        return root;
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(5));

        // 分析日志按钮
        Button analyzeButton = new Button("📊 分析服务器日志");
        analyzeButton.setStyle("-fx-font-size: 13px;");
        analyzeButton.setOnAction(e -> analyzeServerLogs());

        // 刷新按钮
        Button refreshButton = new Button("🔄 刷新");
        refreshButton.setStyle("-fx-font-size: 13px;");
        refreshButton.setOnAction(e -> loadConfigFiles());

        // 导出清单按钮
        Button exportButton = new Button("📤 导出清单");
        exportButton.setStyle("-fx-font-size: 13px;");
        exportButton.setOnAction(e -> exportFileList());

        // 统计信息标签
        Label statsLabel = new Label("工具栏已加载");
        HBox.setHgrow(statsLabel, Priority.ALWAYS);

        toolbar.getChildren().addAll(
                analyzeButton, refreshButton, exportButton,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                statsLabel
        );

        return toolbar;
    }

    private HBox createFilterBar() {
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(5));

        Label filterLabel = new Label("筛选:");

        filterComboBox = new ComboBox<>();
        filterComboBox.getItems().addAll(
                "全部文件",
                "✅ 服务器已加载",
                "🔥 核心配置",
                "📦 物品配置",
                "⚔️ 技能配置",
                "📜 任务配置",
                "🧑 NPC配置",
                "🗺️ 世界配置"
        );
        filterComboBox.setValue("全部文件");
        filterComboBox.setOnAction(e -> applyFilter());

        // 搜索框
        TextField searchField = new TextField();
        searchField.setPromptText("搜索文件名...");
        searchField.setPrefWidth(200);
        searchField.textProperty().addListener((obs, old, newVal) -> applySearch(newVal));

        filterBar.getChildren().addAll(filterLabel, filterComboBox, searchField);

        return filterBar;
    }

    private TableView<ServerConfigFile> createTableView() {
        TableView<ServerConfigFile> table = new TableView<>();

        // 文件名列
        TableColumn<ServerConfigFile, String> fileNameCol = new TableColumn<>("文件名");
        fileNameCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        fileNameCol.setPrefWidth(250);

        // 表名列
        TableColumn<ServerConfigFile, String> tableNameCol = new TableColumn<>("数据库表名");
        tableNameCol.setCellValueFactory(new PropertyValueFactory<>("tableName"));
        tableNameCol.setPrefWidth(200);

        // 服务器加载列
        TableColumn<ServerConfigFile, String> loadedCol = new TableColumn<>("服务器加载");
        loadedCol.setCellValueFactory(cellData -> {
            Boolean loaded = cellData.getValue().getIsServerLoaded();
            return new SimpleStringProperty(Boolean.TRUE.equals(loaded) ? "✅ 是" : "❌ 否");
        });
        loadedCol.setPrefWidth(100);

        // 优先级列
        TableColumn<ServerConfigFile, String> priorityCol = new TableColumn<>("优先级");
        priorityCol.setCellValueFactory(cellData -> {
            Integer priority = cellData.getValue().getLoadPriority();
            int p = priority != null ? priority : 3;
            String text = switch (p) {
                case 1 -> "🔥 核心";
                case 2 -> "⚠️ 重要";
                default -> "📄 一般";
            };
            return new SimpleStringProperty(text);
        });
        priorityCol.setPrefWidth(100);

        // 分类列
        TableColumn<ServerConfigFile, String> categoryCol = new TableColumn<>("文件分类");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("fileCategory"));
        categoryCol.setPrefWidth(120);

        // 导入次数列
        TableColumn<ServerConfigFile, Integer> importCountCol = new TableColumn<>("导入次数");
        importCountCol.setCellValueFactory(cellData -> {
            Integer count = cellData.getValue().getImportCount();
            return new SimpleIntegerProperty(count != null ? count : 0).asObject();
        });
        importCountCol.setPrefWidth(80);

        // 导出次数列
        TableColumn<ServerConfigFile, Integer> exportCountCol = new TableColumn<>("导出次数");
        exportCountCol.setCellValueFactory(cellData -> {
            Integer count = cellData.getValue().getExportCount();
            return new SimpleIntegerProperty(count != null ? count : 0).asObject();
        });
        exportCountCol.setPrefWidth(80);

        // 验证状态列
        TableColumn<ServerConfigFile, String> validationCol = new TableColumn<>("验证状态");
        validationCol.setCellValueFactory(cellData -> {
            String status = cellData.getValue().getValidationStatus();
            String text = switch (status != null ? status : "unknown") {
                case "valid" -> "✅ 有效";
                case "invalid" -> "❌ 无效";
                case "missing" -> "⚠️ 缺失";
                default -> "❓ 未知";
            };
            return new SimpleStringProperty(text);
        });
        validationCol.setPrefWidth(100);

        table.getColumns().addAll(
                fileNameCol, tableNameCol, loadedCol, priorityCol,
                categoryCol, importCountCol, exportCountCol, validationCol
        );

        // 双击查看详情
        table.setRowFactory(tv -> {
            TableRow<ServerConfigFile> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showFileDetails(row.getItem());
                }
            });
            return row;
        });

        // 设置占位符
        table.setPlaceholder(new Label("暂无数据，请点击「📊 分析服务器日志」加载数据"));

        return table;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-background-color: #f0f0f0;");

        statusLabel = new Label("就绪");

        statusBar.getChildren().add(statusLabel);

        return statusBar;
    }

    /**
     * 加载配置文件列表
     */
    private void loadConfigFiles() {
        if (dao == null) {
            updateStatus("错误：数据库连接未初始化");
            return;
        }

        updateStatus("正在加载配置文件...");
        log.info("开始加载配置文件列表");

        CompletableFuture.runAsync(() -> {
            try {
                List<ServerConfigFile> files = dao.findAll();
                log.info("从数据库加载了 {} 个配置文件", files.size());

                Platform.runLater(() -> {
                    configFiles = FXCollections.observableArrayList(files);
                    tableView.setItems(configFiles);
                    updateStatus("共 " + files.size() + " 个配置文件");
                });
            } catch (Exception e) {
                log.error("加载配置文件失败", e);
                Platform.runLater(() -> {
                    configFiles = FXCollections.observableArrayList(new ArrayList<>());
                    tableView.setItems(configFiles);
                    updateStatus("加载失败: " + e.getMessage());

                    // 显示详细错误
                    showError("加载配置文件失败",
                            "错误信息: " + e.getMessage() +
                            "\n\n可能的原因:\n" +
                            "1. 数据库表 server_config_files 不存在\n" +
                            "2. 数据库连接失败\n" +
                            "3. 表结构不匹配\n\n" +
                            "请检查数据库配置。");
                });
            }
        });
    }

    /**
     * 分析服务器日志
     */
    private void analyzeServerLogs() {
        log.info("用户点击了分析服务器日志按钮");

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择服务器日志目录");

        File defaultDir = new File("d:/AionReal58/AionServer/MainServer/log");
        if (defaultDir.exists()) {
            chooser.setInitialDirectory(defaultDir);
        }

        File selectedDir = chooser.showDialog(this);
        if (selectedDir == null) {
            log.info("用户取消了目录选择");
            return;
        }

        updateStatus("正在分析日志目录: " + selectedDir.getAbsolutePath() + " ...");

        CompletableFuture.runAsync(() -> {
            try {
                ServerLogAnalyzer.AnalysisResult result = analyzer.analyzeLogDirectory(selectedDir.getAbsolutePath());

                if (result.getErrorMessage() != null) {
                    Platform.runLater(() -> {
                        showError("分析失败", result.getErrorMessage());
                        updateStatus("分析失败");
                    });
                    return;
                }

                int savedCount = analyzer.saveAnalysisResult(result, "MainServer");

                Platform.runLater(() -> {
                    updateStatus("✅ 分析完成！发现 " + result.getXmlFiles().size() + " 个文件，保存 " + savedCount + " 条记录");
                    loadConfigFiles();

                    showInfo("分析完成",
                            "发现 " + result.getXmlFiles().size() + " 个 XML 文件\n" +
                            "保存 " + savedCount + " 条记录到数据库");
                });
            } catch (Exception e) {
                log.error("分析日志失败", e);
                Platform.runLater(() -> {
                    showError("分析失败", e.getMessage());
                    updateStatus("分析失败");
                });
            }
        });
    }

    /**
     * 应用过滤器
     */
    private void applyFilter() {
        if (dao == null) {
            return;
        }

        String filter = filterComboBox.getValue();
        updateStatus("正在筛选...");

        CompletableFuture.runAsync(() -> {
            try {
                List<ServerConfigFile> files;

                if (filter.contains("服务器已加载")) {
                    files = dao.findServerLoaded();
                } else if (filter.contains("核心配置")) {
                    files = dao.findCriticalFiles();
                } else if (filter.contains("物品配置")) {
                    files = dao.findByCategory("items");
                } else if (filter.contains("技能配置")) {
                    files = dao.findByCategory("skills");
                } else if (filter.contains("任务配置")) {
                    files = dao.findByCategory("quests");
                } else if (filter.contains("NPC配置")) {
                    files = dao.findByCategory("npcs");
                } else if (filter.contains("世界配置")) {
                    files = dao.findByCategory("worlds");
                } else {
                    files = dao.findAll();
                }

                Platform.runLater(() -> {
                    configFiles = FXCollections.observableArrayList(files);
                    tableView.setItems(configFiles);
                    updateStatus("筛选结果: " + files.size() + " 个文件");
                });
            } catch (Exception e) {
                log.error("筛选失败", e);
                Platform.runLater(() -> {
                    updateStatus("筛选失败: " + e.getMessage());
                });
            }
        });
    }

    /**
     * 应用搜索
     */
    private void applySearch(String searchText) {
        if (configFiles == null) {
            return;
        }

        if (searchText == null || searchText.trim().isEmpty()) {
            tableView.setItems(configFiles);
            return;
        }

        ObservableList<ServerConfigFile> filtered = configFiles.filtered(file ->
                file.getFileName().toLowerCase().contains(searchText.toLowerCase()) ||
                (file.getTableName() != null && file.getTableName().toLowerCase().contains(searchText.toLowerCase()))
        );

        tableView.setItems(filtered);
        updateStatus("搜索结果: " + filtered.size() + " 个文件");
    }

    /**
     * 显示文件详情
     */
    private void showFileDetails(ServerConfigFile file) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("配置文件详情");
        alert.setHeaderText(file.getFileName());

        StringBuilder content = new StringBuilder();
        content.append("文件名: ").append(file.getFileName()).append("\n");
        content.append("表名: ").append(file.getTableName()).append("\n");
        content.append("服务器加载: ").append(Boolean.TRUE.equals(file.getIsServerLoaded()) ? "是" : "否").append("\n");
        content.append("优先级: ").append(file.getLoadPriority()).append("\n");
        content.append("分类: ").append(file.getFileCategory()).append("\n");
        content.append("是否核心: ").append(Boolean.TRUE.equals(file.getIsCritical()) ? "是" : "否").append("\n");
        content.append("导入次数: ").append(file.getImportCount()).append("\n");
        content.append("导出次数: ").append(file.getExportCount()).append("\n");

        if (file.getValidationErrors() != null) {
            content.append("\n验证错误:\n").append(file.getValidationErrors());
        }

        alert.setContentText(content.toString());
        alert.showAndWait();
    }

    /**
     * 导出文件清单
     */
    private void exportFileList() {
        showInfo("功能开发中", "导出清单功能即将推出...");
    }

    private void updateStatus(String message) {
        if (statusLabel != null) {
            if (Platform.isFxApplicationThread()) {
                statusLabel.setText(message);
            } else {
                Platform.runLater(() -> statusLabel.setText(message));
            }
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
