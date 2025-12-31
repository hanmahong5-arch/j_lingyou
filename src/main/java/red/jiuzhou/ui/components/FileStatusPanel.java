package red.jiuzhou.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.DatabaseUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文件状态面板
 *
 * 实时展示当前选中XML文件在服务端的状态，包括：
 * - 数据库导入状态
 * - 记录数量
 * - 编码信息
 * - 往返一致性
 * - 文件基本信息
 *
 * @author Claude
 * @version 1.0
 */
public class FileStatusPanel extends VBox {

    private static final Logger log = LoggerFactory.getLogger(FileStatusPanel.class);

    // 状态标签
    private Label fileNameLabel;
    private Label filePathLabel;
    private Label fileSizeLabel;
    private Label fileModifiedLabel;

    // 数据库状态
    private Label dbStatusLabel;
    private Label dbRecordCountLabel;
    private Label dbLastImportLabel;

    // 编码状态
    private Label encodingLabel;
    private Label bomLabel;
    private Label roundTripLabel;

    // 同步状态
    private Label syncStatusLabel;

    // 服务器状态
    private Label serverLoadedLabel;
    private Label serverPriorityLabel;
    private Label serverModuleLabel;
    private Label dependsOnLabel;
    private Label referencedByLabel;

    // AI操作回调
    private java.util.function.BiConsumer<String, String> onAiOperation;

    // 操作按钮
    private Button refreshButton;
    private Button importButton;
    private Button exportButton;
    private Button detailButton;

    // 导入导出回调
    private java.util.function.Consumer<String> onImportRequest;
    private java.util.function.Consumer<String> onExportRequest;

    // 当前文件路径
    private String currentFilePath;
    private String currentTableName;

    // 异步执行器
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 折叠状态
    private boolean collapsed = false;
    private TitledPane mainPane;

    public FileStatusPanel() {
        initializeUI();
    }

    private void initializeUI() {
        setPrefWidth(280);
        setMinWidth(50);
        setMaxWidth(350);
        setSpacing(0);
        setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 0 0 0 1;");

        // 创建主标题面板
        mainPane = new TitledPane();
        mainPane.setText("📋 文件状态");
        mainPane.setCollapsible(true);
        mainPane.setExpanded(true);
        mainPane.setStyle("-fx-font-weight: bold;");

        VBox content = new VBox(12);
        content.setPadding(new Insets(12));

        // ==================== 文件基本信息 ====================
        VBox fileInfoBox = createSection("📁 文件信息");
        fileNameLabel = createValueLabel("未选择文件");
        fileNameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        filePathLabel = createValueLabel("-");
        filePathLabel.setWrapText(true);
        fileSizeLabel = createValueLabel("-");
        fileModifiedLabel = createValueLabel("-");

        fileInfoBox.getChildren().addAll(
            fileNameLabel,
            createRow("路径:", filePathLabel),
            createRow("大小:", fileSizeLabel),
            createRow("修改:", fileModifiedLabel)
        );

        // ==================== 数据库状态 ====================
        VBox dbBox = createSection("🗄️ 数据库状态");
        dbStatusLabel = createStatusLabel("未检测", StatusType.UNKNOWN);
        dbRecordCountLabel = createValueLabel("-");
        dbLastImportLabel = createValueLabel("-");

        dbBox.getChildren().addAll(
            createRow("导入状态:", dbStatusLabel),
            createRow("记录数:", dbRecordCountLabel),
            createRow("最后导入:", dbLastImportLabel)
        );

        // ==================== 编码信息 ====================
        VBox encodingBox = createSection("🔤 编码信息");
        encodingLabel = createValueLabel("-");
        bomLabel = createValueLabel("-");
        roundTripLabel = createStatusLabel("未验证", StatusType.UNKNOWN);

        encodingBox.getChildren().addAll(
            createRow("文件编码:", encodingLabel),
            createRow("BOM标记:", bomLabel),
            createRow("往返一致:", roundTripLabel)
        );

        // ==================== 同步状态 ====================
        VBox syncBox = createSection("🔄 同步状态");
        syncStatusLabel = createStatusLabel("未知", StatusType.UNKNOWN);

        syncBox.getChildren().addAll(
            createRow("状态:", syncStatusLabel)
        );

        // ==================== 服务器状态 ====================
        VBox serverBox = createSection("🗄️ 服务器配置");
        serverLoadedLabel = createStatusLabel("未检测", StatusType.UNKNOWN);
        serverPriorityLabel = createValueLabel("-");
        serverModuleLabel = createValueLabel("-");
        dependsOnLabel = createValueLabel("-");
        referencedByLabel = createValueLabel("-");

        serverBox.getChildren().addAll(
            createRow("加载状态:", serverLoadedLabel),
            createRow("优先级:", serverPriorityLabel),
            createRow("所属模块:", serverModuleLabel),
            createRow("依赖文件:", dependsOnLabel),
            createRow("被依赖:", referencedByLabel)
        );

        // ==================== AI 快捷操作 ====================
        VBox aiBox = createSection("🤖 AI 快捷操作");
        HBox aiButtonRow1 = createAiButtonRow(
            new String[]{"分析结构", "检查引用", "生成SQL"},
            new String[]{"analyze_structure", "check_references", "generate_sql"}
        );
        HBox aiButtonRow2 = createAiButtonRow(
            new String[]{"数据分析", "生成文档", "问题诊断"},
            new String[]{"data_analysis", "generate_doc", "diagnose"}
        );
        aiBox.getChildren().addAll(aiButtonRow1, aiButtonRow2);

        // ==================== 操作按钮 ====================
        HBox buttonBox = new HBox(8);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(8, 0, 0, 0));

        refreshButton = new Button("🔄 刷新");
        refreshButton.setOnAction(e -> refresh());
        refreshButton.setDisable(true);

        Button detailButton = new Button("📊 详情");
        detailButton.setOnAction(e -> showDetailDialog());

        buttonBox.getChildren().addAll(refreshButton, detailButton);

        // 快捷操作按钮区域
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER);
        actionBox.setPadding(new Insets(8, 0, 0, 0));

        Button importBtn = new Button("📥 导入");
        importBtn.setTooltip(new Tooltip("从此XML文件导入到数据库"));
        importBtn.setOnAction(e -> triggerImport());
        importBtn.setDisable(true);

        Button exportBtn = new Button("📤 导出");
        exportBtn.setTooltip(new Tooltip("从数据库导出到XML文件"));
        exportBtn.setOnAction(e -> triggerExport());
        exportBtn.setDisable(true);

        actionBox.getChildren().addAll(importBtn, exportBtn);

        // 保存按钮引用以便后续启用/禁用
        this.importButton = importBtn;
        this.exportButton = exportBtn;
        this.detailButton = detailButton;

        // 组装
        content.getChildren().addAll(
            fileInfoBox,
            new Separator(),
            serverBox,
            new Separator(),
            dbBox,
            new Separator(),
            encodingBox,
            new Separator(),
            syncBox,
            new Separator(),
            aiBox,
            buttonBox,
            actionBox
        );

        mainPane.setContent(content);
        getChildren().add(mainPane);
        VBox.setVgrow(mainPane, Priority.ALWAYS);

        // 初始提示
        showEmptyState();
    }

    /**
     * 创建分组标题
     */
    private VBox createSection(String title) {
        VBox box = new VBox(6);
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        titleLabel.setTextFill(Color.web("#495057"));
        box.getChildren().add(titleLabel);
        return box;
    }

    /**
     * 创建键值对行
     */
    private HBox createRow(String key, Label valueLabel) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Label keyLabel = new Label(key);
        keyLabel.setMinWidth(65);
        keyLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");

        valueLabel.setStyle(valueLabel.getStyle() + " -fx-font-size: 11px;");

        row.getChildren().addAll(keyLabel, valueLabel);
        return row;
    }

    /**
     * 创建值标签
     */
    private Label createValueLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #212529;");
        return label;
    }

    /**
     * 创建状态标签
     */
    private Label createStatusLabel(String text, StatusType type) {
        Label label = new Label(text);
        updateStatusStyle(label, type);
        return label;
    }

    /**
     * 更新状态标签样式
     */
    private void updateStatusStyle(Label label, StatusType type) {
        String style = switch (type) {
            case SUCCESS -> "-fx-text-fill: #28a745; -fx-font-weight: bold;";
            case WARNING -> "-fx-text-fill: #ffc107; -fx-font-weight: bold;";
            case ERROR -> "-fx-text-fill: #dc3545; -fx-font-weight: bold;";
            case UNKNOWN -> "-fx-text-fill: #6c757d;";
        };
        label.setStyle(style + " -fx-font-size: 11px;");
    }

    /**
     * 显示空状态
     */
    private void showEmptyState() {
        fileNameLabel.setText("👆 请在左侧选择文件");
        filePathLabel.setText("-");
        fileSizeLabel.setText("-");
        fileModifiedLabel.setText("-");

        dbStatusLabel.setText("未检测");
        updateStatusStyle(dbStatusLabel, StatusType.UNKNOWN);
        dbRecordCountLabel.setText("-");
        dbLastImportLabel.setText("-");

        encodingLabel.setText("-");
        bomLabel.setText("-");
        roundTripLabel.setText("未验证");
        updateStatusStyle(roundTripLabel, StatusType.UNKNOWN);

        syncStatusLabel.setText("未知");
        updateStatusStyle(syncStatusLabel, StatusType.UNKNOWN);

        // 服务器状态重置
        serverLoadedLabel.setText("未检测");
        updateStatusStyle(serverLoadedLabel, StatusType.UNKNOWN);
        serverPriorityLabel.setText("-");
        serverModuleLabel.setText("-");
        dependsOnLabel.setText("-");
        referencedByLabel.setText("-");

        refreshButton.setDisable(true);
        importButton.setDisable(true);
        exportButton.setDisable(true);
        detailButton.setDisable(true);
    }

    /**
     * 更新显示的文件
     */
    public void updateFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            showEmptyState();
            return;
        }

        this.currentFilePath = filePath;
        this.currentTableName = inferTableName(filePath);

        refreshButton.setDisable(false);
        importButton.setDisable(false);
        detailButton.setDisable(false);

        // 显示基本文件信息（同步）
        updateFileInfo(filePath);

        // 异步加载数据库状态
        loadDatabaseStatus();
    }

    /**
     * 更新文件基本信息
     */
    private void updateFileInfo(String filePath) {
        try {
            File file = new File(filePath);
            Path path = file.toPath();

            fileNameLabel.setText(file.getName());
            filePathLabel.setText(truncatePath(filePath, 35));
            filePathLabel.setTooltip(new Tooltip(filePath));

            if (file.exists()) {
                // 文件大小
                long size = Files.size(path);
                fileSizeLabel.setText(formatFileSize(size));

                // 修改时间
                long lastModified = file.lastModified();
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
                fileModifiedLabel.setText(sdf.format(new Date(lastModified)));
            } else {
                fileSizeLabel.setText("文件不存在");
                fileModifiedLabel.setText("-");
            }

        } catch (Exception e) {
            log.warn("读取文件信息失败: {}", e.getMessage());
            fileSizeLabel.setText("读取失败");
        }
    }

    /**
     * 异步加载数据库状态
     */
    private void loadDatabaseStatus() {
        // 先显示加载中
        Platform.runLater(() -> {
            dbStatusLabel.setText("检测中...");
            updateStatusStyle(dbStatusLabel, StatusType.UNKNOWN);
            dbRecordCountLabel.setText("...");
            dbLastImportLabel.setText("...");
            encodingLabel.setText("...");
            bomLabel.setText("...");
            roundTripLabel.setText("检测中...");
            syncStatusLabel.setText("检测中...");
            serverLoadedLabel.setText("检测中...");
            updateStatusStyle(serverLoadedLabel, StatusType.UNKNOWN);
            serverPriorityLabel.setText("...");
            serverModuleLabel.setText("...");
            dependsOnLabel.setText("...");
            referencedByLabel.setText("...");
        });

        executor.submit(() -> {
            try {
                // 检查表是否存在
                boolean tableExists = checkTableExists(currentTableName);

                // 获取记录数
                long recordCount = tableExists ? getRecordCount(currentTableName) : 0;

                // 获取编码元数据
                Map<String, Object> encodingMeta = getEncodingMetadata(currentTableName);

                // 获取服务器配置信息
                Map<String, Object> serverConfig = getServerConfigInfo(currentTableName);

                // 更新UI
                Platform.runLater(() -> {
                    updateDatabaseStatusUI(tableExists, recordCount, encodingMeta);
                    updateServerConfigUI(serverConfig);
                });

            } catch (Exception e) {
                log.error("加载数据库状态失败", e);
                Platform.runLater(() -> {
                    dbStatusLabel.setText("检测失败");
                    updateStatusStyle(dbStatusLabel, StatusType.ERROR);
                    serverLoadedLabel.setText("检测失败");
                    updateStatusStyle(serverLoadedLabel, StatusType.ERROR);
                });
            }
        });
    }

    /**
     * 更新数据库状态UI
     */
    private void updateDatabaseStatusUI(boolean tableExists, long recordCount,
                                        Map<String, Object> encodingMeta) {
        // 数据库导入状态
        if (tableExists && recordCount > 0) {
            dbStatusLabel.setText("✅ 已导入");
            updateStatusStyle(dbStatusLabel, StatusType.SUCCESS);
            dbRecordCountLabel.setText(String.format("%,d 条", recordCount));
            exportButton.setDisable(false);  // 有数据才能导出
        } else if (tableExists) {
            dbStatusLabel.setText("⚠️ 表存在但无数据");
            updateStatusStyle(dbStatusLabel, StatusType.WARNING);
            dbRecordCountLabel.setText("0 条");
            exportButton.setDisable(true);
        } else {
            dbStatusLabel.setText("❌ 未导入");
            updateStatusStyle(dbStatusLabel, StatusType.ERROR);
            dbRecordCountLabel.setText("-");
            exportButton.setDisable(true);
        }

        // 编码信息
        if (encodingMeta != null && !encodingMeta.isEmpty()) {
            String encoding = (String) encodingMeta.getOrDefault("original_encoding", "未知");
            Boolean hasBom = (Boolean) encodingMeta.getOrDefault("has_bom", false);
            Boolean validated = (Boolean) encodingMeta.get("last_validation_result");
            Object lastImport = encodingMeta.get("last_import_time");

            encodingLabel.setText(encoding);
            bomLabel.setText(hasBom ? "✅ 有" : "❌ 无");

            if (validated != null) {
                if (validated) {
                    roundTripLabel.setText("✅ 通过");
                    updateStatusStyle(roundTripLabel, StatusType.SUCCESS);
                } else {
                    roundTripLabel.setText("❌ 不一致");
                    updateStatusStyle(roundTripLabel, StatusType.ERROR);
                }
            } else {
                roundTripLabel.setText("未验证");
                updateStatusStyle(roundTripLabel, StatusType.UNKNOWN);
            }

            if (lastImport != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm");
                dbLastImportLabel.setText(sdf.format(lastImport));
            }

            // 同步状态
            determineSyncStatus(tableExists, recordCount, encodingMeta);

        } else {
            encodingLabel.setText("无记录");
            bomLabel.setText("-");
            roundTripLabel.setText("未验证");
            updateStatusStyle(roundTripLabel, StatusType.UNKNOWN);
            dbLastImportLabel.setText("-");

            // 同步状态
            if (tableExists && recordCount > 0) {
                syncStatusLabel.setText("⚠️ 缺少元数据");
                updateStatusStyle(syncStatusLabel, StatusType.WARNING);
            } else {
                syncStatusLabel.setText("🆕 待导入");
                updateStatusStyle(syncStatusLabel, StatusType.UNKNOWN);
            }
        }
    }

    /**
     * 判断同步状态
     * 综合考虑：文件修改时间、导入时间、往返验证结果
     */
    private void determineSyncStatus(boolean tableExists, long recordCount,
                                     Map<String, Object> encodingMeta) {
        if (!tableExists || recordCount == 0) {
            syncStatusLabel.setText("🆕 待导入");
            updateStatusStyle(syncStatusLabel, StatusType.UNKNOWN);
            return;
        }

        // 检查文件修改时间与导入时间
        boolean fileModifiedAfterImport = false;
        Object lastImport = encodingMeta.get("last_import_time");
        if (lastImport != null && currentFilePath != null) {
            try {
                File file = new File(currentFilePath);
                if (file.exists()) {
                    long fileModTime = file.lastModified();
                    long importTime = 0;
                    if (lastImport instanceof java.sql.Timestamp) {
                        importTime = ((java.sql.Timestamp) lastImport).getTime();
                    } else if (lastImport instanceof java.util.Date) {
                        importTime = ((java.util.Date) lastImport).getTime();
                    }
                    // 文件修改时间比导入时间晚超过1秒
                    fileModifiedAfterImport = (fileModTime - importTime) > 1000;
                }
            } catch (Exception e) {
                log.debug("检查文件修改时间失败: {}", e.getMessage());
            }
        }

        // 综合判断同步状态
        Boolean validated = (Boolean) encodingMeta.get("last_validation_result");

        if (fileModifiedAfterImport) {
            // 文件有新修改
            syncStatusLabel.setText("📝 文件已修改");
            updateStatusStyle(syncStatusLabel, StatusType.WARNING);
            syncStatusLabel.setTooltip(new Tooltip("文件在上次导入后被修改，建议重新导入"));
        } else if (validated != null && validated) {
            syncStatusLabel.setText("✅ 已同步");
            updateStatusStyle(syncStatusLabel, StatusType.SUCCESS);
            syncStatusLabel.setTooltip(new Tooltip("文件与数据库保持一致"));
        } else if (validated != null && !validated) {
            syncStatusLabel.setText("⚠️ 有差异");
            updateStatusStyle(syncStatusLabel, StatusType.WARNING);
            syncStatusLabel.setTooltip(new Tooltip("往返验证失败，导出结果与原文件不完全一致"));
        } else {
            syncStatusLabel.setText("❓ 未验证");
            updateStatusStyle(syncStatusLabel, StatusType.UNKNOWN);
            syncStatusLabel.setTooltip(new Tooltip("尚未进行往返一致性验证"));
        }
    }

    /**
     * 刷新状态
     */
    public void refresh() {
        if (currentFilePath != null) {
            updateFile(currentFilePath);
        }
    }

    /**
     * 显示详情对话框
     */
    private void showDetailDialog() {
        if (currentFilePath == null) return;

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("📋 文件完整信息");
        dialog.setHeaderText(new File(currentFilePath).getName());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        // 构建详情内容
        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setMinWidth(450);

        // 文件信息区
        TitledPane filePane = new TitledPane();
        filePane.setText("📁 文件信息");
        filePane.setCollapsible(false);
        VBox fileContent = new VBox(6);
        fileContent.setPadding(new Insets(8));

        File file = new File(currentFilePath);
        fileContent.getChildren().addAll(
            createDetailRow("文件名:", file.getName()),
            createDetailRow("完整路径:", currentFilePath),
            createDetailRow("文件大小:", fileSizeLabel.getText()),
            createDetailRow("最后修改:", fileModifiedLabel.getText()),
            createDetailRow("推断表名:", currentTableName)
        );
        filePane.setContent(fileContent);

        // 数据库信息区
        TitledPane dbPane = new TitledPane();
        dbPane.setText("🗄️ 数据库状态");
        dbPane.setCollapsible(false);
        VBox dbContent = new VBox(6);
        dbContent.setPadding(new Insets(8));
        dbContent.getChildren().addAll(
            createDetailRow("导入状态:", dbStatusLabel.getText()),
            createDetailRow("记录数量:", dbRecordCountLabel.getText()),
            createDetailRow("最后导入:", dbLastImportLabel.getText())
        );
        dbPane.setContent(dbContent);

        // 编码信息区
        TitledPane encodingPane = new TitledPane();
        encodingPane.setText("🔤 编码信息");
        encodingPane.setCollapsible(false);
        VBox encodingContent = new VBox(6);
        encodingContent.setPadding(new Insets(8));
        encodingContent.getChildren().addAll(
            createDetailRow("文件编码:", encodingLabel.getText()),
            createDetailRow("BOM标记:", bomLabel.getText()),
            createDetailRow("往返验证:", roundTripLabel.getText()),
            createDetailRow("同步状态:", syncStatusLabel.getText())
        );
        encodingPane.setContent(encodingContent);

        // 操作建议区
        TitledPane suggestPane = new TitledPane();
        suggestPane.setText("💡 建议操作");
        suggestPane.setCollapsible(false);
        VBox suggestContent = new VBox(6);
        suggestContent.setPadding(new Insets(8));

        String suggestion = generateSuggestion();
        Label suggestLabel = new Label(suggestion);
        suggestLabel.setWrapText(true);
        suggestLabel.setStyle("-fx-text-fill: #495057;");
        suggestContent.getChildren().add(suggestLabel);
        suggestPane.setContent(suggestContent);

        content.getChildren().addAll(filePane, dbPane, encodingPane, suggestPane);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /**
     * 创建详情行
     */
    private HBox createDetailRow(String label, String value) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label keyLabel = new Label(label);
        keyLabel.setMinWidth(80);
        keyLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-weight: bold;");

        Label valueLabel = new Label(value != null ? value : "-");
        valueLabel.setWrapText(true);
        valueLabel.setStyle("-fx-text-fill: #212529;");

        row.getChildren().addAll(keyLabel, valueLabel);
        return row;
    }

    /**
     * 生成操作建议
     */
    private String generateSuggestion() {
        String dbStatus = dbStatusLabel.getText();
        String roundTrip = roundTripLabel.getText();
        String syncStatus = syncStatusLabel.getText();

        if (dbStatus.contains("未导入")) {
            return "📥 此文件尚未导入数据库。\n建议：点击「导入」按钮将XML数据导入到数据库中。";
        }

        if (dbStatus.contains("无数据")) {
            return "⚠️ 数据库表存在但没有数据。\n建议：重新导入XML文件，或检查导入过程是否出错。";
        }

        if (syncStatus.contains("文件已修改")) {
            return "📝 检测到文件在导入后被修改！\n建议：如果XML是外部修改的，请重新导入以同步数据库。\n如果是误操作，可以忽略此提示。";
        }

        if (roundTrip.contains("不一致")) {
            return "⚠️ 往返一致性验证失败！\n说明：导出的XML与原始文件不完全一致。\n建议：检查编码设置，或重新导入以更新元数据。";
        }

        if (roundTrip.contains("未验证")) {
            return "❓ 尚未进行往返一致性验证。\n建议：执行一次「导出」操作，系统会自动验证一致性。";
        }

        return "✅ 文件状态正常。\n数据已导入数据库，往返验证通过。可以正常进行编辑和导出操作。";
    }

    /**
     * 触发导入操作
     */
    private void triggerImport() {
        if (currentFilePath == null) return;

        if (onImportRequest != null) {
            onImportRequest.accept(currentFilePath);
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("导入提示");
            alert.setHeaderText("导入功能");
            alert.setContentText("请使用主界面的「导入」菜单导入此文件：\n" + new File(currentFilePath).getName());
            alert.showAndWait();
        }
    }

    /**
     * 触发导出操作
     */
    private void triggerExport() {
        if (currentTableName == null) return;

        if (onExportRequest != null) {
            onExportRequest.accept(currentTableName);
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("导出提示");
            alert.setHeaderText("导出功能");
            alert.setContentText("请使用主界面的「导出」菜单导出表：\n" + currentTableName);
            alert.showAndWait();
        }
    }

    /**
     * 设置导入请求回调
     */
    public void setOnImportRequest(java.util.function.Consumer<String> handler) {
        this.onImportRequest = handler;
    }

    /**
     * 设置导出请求回调
     */
    public void setOnExportRequest(java.util.function.Consumer<String> handler) {
        this.onExportRequest = handler;
    }

    // ==================== AI 按钮创建 ====================

    /**
     * 创建 AI 操作按钮行
     */
    private HBox createAiButtonRow(String[] labels, String[] actions) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(4, 0, 4, 0));

        for (int i = 0; i < labels.length && i < actions.length; i++) {
            Button btn = createAiButton(labels[i], actions[i]);
            row.getChildren().add(btn);
        }

        return row;
    }

    /**
     * 创建单个 AI 操作按钮
     */
    private Button createAiButton(String label, String action) {
        Button btn = new Button("🤖 " + label);
        btn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #667eea, #764ba2);" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 10px;" +
            "-fx-padding: 4 8;" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: hand;"
        );

        // 悬停效果
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #764ba2, #667eea);" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 10px;" +
            "-fx-padding: 4 8;" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: hand;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 4, 0, 0, 1);"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #667eea, #764ba2);" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 10px;" +
            "-fx-padding: 4 8;" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: hand;"
        ));

        // 点击事件
        btn.setOnAction(e -> {
            if (onAiOperation != null && currentTableName != null) {
                onAiOperation.accept(action, currentTableName);
            } else if (currentTableName == null) {
                showAiHint("请先选择一个文件");
            } else {
                showAiHint("AI 功能尚未配置");
            }
        });

        // 工具提示
        btn.setTooltip(new Tooltip(getAiActionTooltip(action)));

        return btn;
    }

    /**
     * 获取 AI 操作的提示文本
     */
    private String getAiActionTooltip(String action) {
        return switch (action) {
            case "analyze_structure" -> "分析表结构、字段类型、数据分布";
            case "check_references" -> "检查ID引用完整性，找出断链";
            case "generate_sql" -> "用自然语言描述生成SQL查询";
            case "data_analysis" -> "分析数据特征、异常值、分布";
            case "generate_doc" -> "自动生成字段说明文档";
            case "diagnose" -> "诊断常见配置问题";
            default -> "AI 辅助操作";
        };
    }

    /**
     * 显示 AI 提示
     */
    private void showAiHint(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("🤖 AI 助手");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 设置 AI 操作回调
     * @param handler BiConsumer<action, tableName>
     */
    public void setOnAiOperation(java.util.function.BiConsumer<String, String> handler) {
        this.onAiOperation = handler;
    }

    // ==================== 数据库查询方法 ====================

    /**
     * 检查表是否存在
     */
    private boolean checkTableExists(String tableName) {
        if (tableName == null) return false;
        try {
            var jdbc = DatabaseUtil.getJdbcTemplate();
            String sql = "SELECT COUNT(*) FROM information_schema.tables " +
                         "WHERE table_schema = DATABASE() AND table_name = ?";
            Integer count = jdbc.queryForObject(sql, Integer.class, tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            log.debug("检查表存在失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取记录数
     */
    private long getRecordCount(String tableName) {
        if (tableName == null) return 0;
        try {
            var jdbc = DatabaseUtil.getJdbcTemplate();
            String sql = "SELECT COUNT(*) FROM `" + tableName + "`";
            Long count = jdbc.queryForObject(sql, Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.debug("获取记录数失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 获取编码元数据
     */
    private Map<String, Object> getEncodingMetadata(String tableName) {
        if (tableName == null) return null;
        try {
            var jdbc = DatabaseUtil.getJdbcTemplate();
            String sql = "SELECT * FROM file_encoding_metadata WHERE table_name = ?";
            List<Map<String, Object>> results = jdbc.queryForList(sql, tableName);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            log.debug("获取编码元数据失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取服务器配置信息
     */
    private Map<String, Object> getServerConfigInfo(String tableName) {
        if (tableName == null) return null;
        try {
            var jdbc = DatabaseUtil.getJdbcTemplate();
            String sql = "SELECT load_priority, server_module, depends_on, referenced_by, is_server_loaded " +
                         "FROM server_config_files WHERE table_name = ?";
            List<Map<String, Object>> results = jdbc.queryForList(sql, tableName);
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            log.debug("获取服务器配置信息失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 更新服务器配置状态UI
     */
    private void updateServerConfigUI(Map<String, Object> serverConfig) {
        if (serverConfig == null || serverConfig.isEmpty()) {
            serverLoadedLabel.setText("📋 非服务器文件");
            updateStatusStyle(serverLoadedLabel, StatusType.UNKNOWN);
            serverPriorityLabel.setText("-");
            serverModuleLabel.setText("-");
            dependsOnLabel.setText("-");
            referencedByLabel.setText("-");
            return;
        }

        // 服务器加载状态
        Boolean isLoaded = (Boolean) serverConfig.getOrDefault("is_server_loaded", false);
        if (Boolean.TRUE.equals(isLoaded)) {
            serverLoadedLabel.setText("🚀 服务器加载");
            updateStatusStyle(serverLoadedLabel, StatusType.SUCCESS);
        } else {
            serverLoadedLabel.setText("📋 客户端配置");
            updateStatusStyle(serverLoadedLabel, StatusType.UNKNOWN);
        }

        // 优先级
        Integer priority = (Integer) serverConfig.get("load_priority");
        if (priority != null) {
            String priorityText = switch (priority) {
                case 1 -> "🚀 核心 (1)";
                case 2 -> "⭐ 重要 (2)";
                case 3 -> "📋 普通 (3)";
                default -> "❓ 未知 (" + priority + ")";
            };
            serverPriorityLabel.setText(priorityText);
        } else {
            serverPriorityLabel.setText("-");
        }

        // 所属模块
        String module = (String) serverConfig.get("server_module");
        serverModuleLabel.setText(module != null && !module.isEmpty() ? module : "-");

        // 依赖文件数
        String dependsOn = (String) serverConfig.get("depends_on");
        int dependsCount = countJsonArray(dependsOn);
        dependsOnLabel.setText(dependsCount > 0 ? dependsCount + " 个" : "-");
        if (dependsCount > 0) {
            dependsOnLabel.setTooltip(new Tooltip("依赖: " + dependsOn));
        }

        // 被依赖数
        String referencedBy = (String) serverConfig.get("referenced_by");
        int refCount = countJsonArray(referencedBy);
        referencedByLabel.setText(refCount > 0 ? refCount + " 个" : "-");
        if (refCount > 0) {
            referencedByLabel.setTooltip(new Tooltip("被依赖: " + referencedBy));
        }
    }

    /**
     * 简单计算JSON数组元素数量
     */
    private int countJsonArray(String json) {
        if (json == null || json.isEmpty() || json.equals("[]") || json.equals("null")) return 0;
        // 简单统计逗号数量+1
        int count = 1;
        for (char c : json.toCharArray()) {
            if (c == ',') count++;
        }
        return count;
    }

    // ==================== 工具方法 ====================

    /**
     * 从文件路径推断表名
     */
    private String inferTableName(String filePath) {
        if (filePath == null) return null;
        File file = new File(filePath);
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }
        return name.toLowerCase();
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 截断路径
     */
    private String truncatePath(String path, int maxLen) {
        if (path == null || path.length() <= maxLen) return path;
        return "..." + path.substring(path.length() - maxLen + 3);
    }

    /**
     * 清理资源
     */
    public void dispose() {
        executor.shutdownNow();
    }

    /**
     * 状态类型枚举
     */
    private enum StatusType {
        SUCCESS, WARNING, ERROR, UNKNOWN
    }
}
