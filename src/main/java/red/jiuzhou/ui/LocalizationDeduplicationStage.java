package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.localization.LocalizationDeduplicator;
import red.jiuzhou.localization.LocalizationDeduplicator.DeduplicationPreview;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Localization Deduplication UI Window
 *
 * <p>Provides a user-friendly interface for removing duplicate entries
 * from public XML directory based on China localization files.
 *
 * @author yanxq
 * @date 2025-01-05
 */
public class LocalizationDeduplicationStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(LocalizationDeduplicationStage.class);

    // Default paths
    private static final String DEFAULT_PUBLIC_PATH = "D:\\AionReal58\\AionMap\\XML";
    private static final String DEFAULT_CHINA_PATH = "D:\\AionReal58\\AionMap\\XML\\China";

    // UI components
    private TextField chinaPathField;
    private TextField publicPathField;
    private TableView<DeduplicationPreview> previewTable;
    private ObservableList<DeduplicationPreview> previewData;
    private ProgressBar progressBar;
    private Label statusLabel;
    private TextArea logArea;
    private CheckBox backupCheckBox;
    private Button scanBtn;
    private Button executeBtn;
    private Button closeBtn;

    // State
    private Map<String, Set<String>> chinaIds;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public LocalizationDeduplicationStage() {
        initUI();
    }

    private void initUI() {
        setTitle("本地化条目去重工具");
        initModality(Modality.NONE);
        setWidth(900);
        setHeight(700);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #fafafa;");

        // ========== 1. Title ==========
        HBox titleBox = createTitleBox();

        // ========== 2. Directory Configuration ==========
        VBox pathBox = createPathConfigBox();

        // ========== 3. Preview Table ==========
        VBox tableBox = createPreviewTableBox();

        // ========== 4. Options ==========
        HBox optionsBox = createOptionsBox();

        // ========== 5. Progress & Log ==========
        VBox progressBox = createProgressBox();

        // ========== 6. Buttons ==========
        HBox buttonBox = createButtonBox();

        root.getChildren().addAll(titleBox, pathBox, tableBox, optionsBox, progressBox, buttonBox);
        VBox.setVgrow(tableBox, Priority.ALWAYS);

        Scene scene = new Scene(root);
        setScene(scene);
    }

    private HBox createTitleBox() {
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label("🌏");
        iconLabel.setStyle("-fx-font-size: 24px;");

        VBox titleTextBox = new VBox(2);
        Label titleLabel = new Label("本地化条目去重工具");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label subtitleLabel = new Label("在公共目录中删除与 China 目录相同 ID 的条目");
        subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        titleTextBox.getChildren().addAll(titleLabel, subtitleLabel);

        titleBox.getChildren().addAll(iconLabel, titleTextBox);
        return titleBox;
    }

    private VBox createPathConfigBox() {
        VBox pathBox = new VBox(10);
        pathBox.setPadding(new Insets(15));
        pathBox.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e0e0e0; -fx-border-radius: 8;");

        Label sectionLabel = new Label("📁 目录配置");
        sectionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        // China path
        HBox chinaBox = new HBox(10);
        chinaBox.setAlignment(Pos.CENTER_LEFT);
        Label chinaLabel = new Label("本地化目录:");
        chinaLabel.setPrefWidth(80);
        chinaPathField = new TextField(DEFAULT_CHINA_PATH);
        chinaPathField.setPrefWidth(500);
        HBox.setHgrow(chinaPathField, Priority.ALWAYS);
        Button chinaBrowseBtn = new Button("选择...");
        chinaBrowseBtn.setOnAction(e -> browseDirectory(chinaPathField));
        chinaBox.getChildren().addAll(chinaLabel, chinaPathField, chinaBrowseBtn);

        // Public path
        HBox publicBox = new HBox(10);
        publicBox.setAlignment(Pos.CENTER_LEFT);
        Label publicLabel = new Label("公共目录:");
        publicLabel.setPrefWidth(80);
        publicPathField = new TextField(DEFAULT_PUBLIC_PATH);
        publicPathField.setPrefWidth(500);
        HBox.setHgrow(publicPathField, Priority.ALWAYS);
        Button publicBrowseBtn = new Button("选择...");
        publicBrowseBtn.setOnAction(e -> browseDirectory(publicPathField));
        publicBox.getChildren().addAll(publicLabel, publicPathField, publicBrowseBtn);

        pathBox.getChildren().addAll(sectionLabel, chinaBox, publicBox);
        return pathBox;
    }

    private VBox createPreviewTableBox() {
        VBox tableBox = new VBox(8);
        tableBox.setPadding(new Insets(15));
        tableBox.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e0e0e0; -fx-border-radius: 8;");

        Label sectionLabel = new Label("📊 扫描结果预览");
        sectionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        previewData = FXCollections.observableArrayList();
        previewTable = new TableView<>(previewData);
        previewTable.setPrefHeight(200);

        TableColumn<DeduplicationPreview, String> fileCol = new TableColumn<>("文件名");
        fileCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFileName()));
        fileCol.setPrefWidth(250);

        TableColumn<DeduplicationPreview, Integer> chinaCol = new TableColumn<>("China ID数");
        chinaCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getChinaIdCount()).asObject());
        chinaCol.setPrefWidth(100);
        chinaCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<DeduplicationPreview, Integer> publicCol = new TableColumn<>("公共 ID数");
        publicCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getPublicIdCount()).asObject());
        publicCol.setPrefWidth(100);
        publicCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<DeduplicationPreview, Integer> dupCol = new TableColumn<>("待删除数");
        dupCol.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getDuplicateCount()).asObject());
        dupCol.setPrefWidth(100);
        dupCol.setStyle("-fx-alignment: CENTER;");
        dupCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.valueOf(item));
                    setStyle("-fx-alignment: CENTER; -fx-text-fill: #e53935; -fx-font-weight: bold;");
                }
            }
        });

        previewTable.getColumns().addAll(fileCol, chinaCol, publicCol, dupCol);
        previewTable.setPlaceholder(new Label("点击「扫描预览」按钮开始分析"));

        // Double-click to show IDs
        previewTable.setRowFactory(tv -> {
            TableRow<DeduplicationPreview> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showDuplicateIds(row.getItem());
                }
            });
            return row;
        });

        VBox.setVgrow(previewTable, Priority.ALWAYS);

        statusLabel = new Label("准备就绪");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        tableBox.getChildren().addAll(sectionLabel, previewTable, statusLabel);
        return tableBox;
    }

    private HBox createOptionsBox() {
        HBox optionsBox = new HBox(20);
        optionsBox.setAlignment(Pos.CENTER_LEFT);
        optionsBox.setPadding(new Insets(10));
        optionsBox.setStyle("-fx-background-color: white; -fx-background-radius: 5; -fx-border-color: #e0e0e0; -fx-border-radius: 5;");

        Label optLabel = new Label("⚙ 操作选项:");
        optLabel.setStyle("-fx-font-weight: bold;");

        backupCheckBox = new CheckBox("执行前创建备份");
        backupCheckBox.setSelected(true);
        backupCheckBox.setTooltip(new Tooltip("将原始文件备份到 .backup 目录"));

        optionsBox.getChildren().addAll(optLabel, backupCheckBox);
        return optionsBox;
    }

    private VBox createProgressBox() {
        VBox progressBox = new VBox(8);
        progressBox.setPadding(new Insets(15));
        progressBox.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e0e0e0; -fx-border-radius: 8;");

        Label sectionLabel = new Label("📝 操作日志");
        sectionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(15);

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(6);
        logArea.setStyle("-fx-font-family: 'Consolas', 'Microsoft YaHei', monospace; -fx-font-size: 11px;");

        progressBox.getChildren().addAll(sectionLabel, progressBar, logArea);
        return progressBox;
    }

    private HBox createButtonBox() {
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        scanBtn = new Button("🔍 扫描预览");
        scanBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20;");
        scanBtn.setOnAction(e -> scanAndPreview());

        executeBtn = new Button("▶️ 执行去重");
        executeBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20;");
        executeBtn.setDisable(true);
        executeBtn.setOnAction(e -> confirmAndExecute());

        closeBtn = new Button("关闭");
        closeBtn.setStyle("-fx-padding: 8 15;");
        closeBtn.setCancelButton(true);
        closeBtn.setOnAction(e -> close());

        buttonBox.getChildren().addAll(scanBtn, executeBtn, closeBtn);
        return buttonBox;
    }

    private void browseDirectory(TextField textField) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择目录");

        File current = new File(textField.getText());
        if (current.exists() && current.isDirectory()) {
            chooser.setInitialDirectory(current);
        }

        File selected = chooser.showDialog(this);
        if (selected != null) {
            textField.setText(selected.getAbsolutePath());
        }
    }

    private void scanAndPreview() {
        if (isRunning.get()) {
            return;
        }

        String chinaPath = chinaPathField.getText().trim();
        String publicPath = publicPathField.getText().trim();

        if (!validatePaths(chinaPath, publicPath)) {
            return;
        }

        isRunning.set(true);
        scanBtn.setDisable(true);
        executeBtn.setDisable(true);
        previewData.clear();
        logArea.clear();
        progressBar.setProgress(-1); // Indeterminate

        appendLog("开始扫描...");
        appendLog("本地化目录: " + chinaPath);
        appendLog("公共目录: " + publicPath);

        Thread.startVirtualThread(() -> {
            try {
                LocalizationDeduplicator deduplicator = new LocalizationDeduplicator(chinaPath, publicPath);
                deduplicator.setLogCallback(this::appendLog);

                // Scan China directory
                chinaIds = deduplicator.scanChinaDirectory();
                appendLog("扫描完成，找到 " + chinaIds.size() + " 个文件");

                // Find duplicates
                List<DeduplicationPreview> previews = deduplicator.findDuplicates(chinaIds);

                int totalDuplicates = previews.stream().mapToInt(DeduplicationPreview::getDuplicateCount).sum();
                appendLog("分析完成，共 " + previews.size() + " 个文件有重复，待删除 " + totalDuplicates + " 个条目");

                Platform.runLater(() -> {
                    previewData.addAll(previews);
                    statusLabel.setText("共 " + previews.size() + " 个文件有重复，待删除 " + totalDuplicates + " 个条目");
                    progressBar.setProgress(1.0);
                    executeBtn.setDisable(previews.isEmpty());
                });

            } catch (Exception e) {
                log.error("扫描失败", e);
                appendLog("扫描失败: " + e.getMessage());
                Platform.runLater(() -> progressBar.setProgress(0));
            } finally {
                Platform.runLater(() -> {
                    isRunning.set(false);
                    scanBtn.setDisable(false);
                });
            }
        });
    }

    private void confirmAndExecute() {
        if (chinaIds == null || chinaIds.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "请先执行扫描预览");
            return;
        }

        int totalToRemove = previewData.stream().mapToInt(DeduplicationPreview::getDuplicateCount).sum();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认执行");
        confirm.setHeaderText("即将删除 " + totalToRemove + " 个条目");
        confirm.setContentText("此操作将修改公共目录中的 XML 文件。\n" +
                (backupCheckBox.isSelected() ? "原始文件将备份到 .backup 目录。" : "警告：未启用备份！"));

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        executeDeduplication();
    }

    private void executeDeduplication() {
        isRunning.set(true);
        scanBtn.setDisable(true);
        executeBtn.setDisable(true);
        progressBar.setProgress(0);

        appendLog("");
        appendLog("========== 开始执行去重 ==========");

        String publicPath = publicPathField.getText().trim();
        String chinaPath = chinaPathField.getText().trim();
        boolean createBackup = backupCheckBox.isSelected();

        Thread.startVirtualThread(() -> {
            try {
                LocalizationDeduplicator deduplicator = new LocalizationDeduplicator(chinaPath, publicPath);
                deduplicator.setLogCallback(this::appendLog);

                int totalRemoved = deduplicator.executeDeduplication(chinaIds, createBackup, progress -> {
                    Platform.runLater(() -> progressBar.setProgress(progress));
                });

                appendLog("========== 执行完成 ==========");
                appendLog("共删除 " + totalRemoved + " 个条目");

                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    statusLabel.setText("执行完成，共删除 " + totalRemoved + " 个条目");
                    showAlert(Alert.AlertType.INFORMATION, "执行完成\n\n共删除 " + totalRemoved + " 个条目");
                });

            } catch (Exception e) {
                log.error("执行失败", e);
                appendLog("执行失败: " + e.getMessage());
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    showAlert(Alert.AlertType.ERROR, "执行失败: " + e.getMessage());
                });
            } finally {
                Platform.runLater(() -> {
                    isRunning.set(false);
                    scanBtn.setDisable(false);
                });
            }
        });
    }

    private boolean validatePaths(String chinaPath, String publicPath) {
        File chinaDir = new File(chinaPath);
        File publicDir = new File(publicPath);

        if (!chinaDir.exists() || !chinaDir.isDirectory()) {
            showAlert(Alert.AlertType.ERROR, "本地化目录不存在: " + chinaPath);
            return false;
        }

        if (!publicDir.exists() || !publicDir.isDirectory()) {
            showAlert(Alert.AlertType.ERROR, "公共目录不存在: " + publicPath);
            return false;
        }

        return true;
    }

    private void showDuplicateIds(DeduplicationPreview preview) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("重复 ID 列表");
        dialog.setHeaderText(preview.getFileName() + " - " + preview.getDuplicateCount() + " 个重复 ID");

        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setText(String.join("\n", preview.getDuplicateIds()));
        textArea.setPrefRowCount(15);
        textArea.setPrefColumnCount(40);

        dialog.getDialogPane().setContent(textArea);
        dialog.showAndWait();
    }

    private void appendLog(String message) {
        String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String logLine = "[" + timestamp + "] " + message + "\n";

        Platform.runLater(() -> {
            logArea.appendText(logLine);
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "错误" : "提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
