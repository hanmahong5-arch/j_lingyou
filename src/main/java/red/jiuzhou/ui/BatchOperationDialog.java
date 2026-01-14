package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.batch.BatchDdlGenerator;
import red.jiuzhou.batch.BatchXmlImporter;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 批量操作对话框（设计师友好版）
 *
 * <p>为游戏设计师提供清晰、可控的数据操作界面：
 * <ul>
 *   <li>操作前：清晰解释将要发生什么</li>
 *   <li>操作中：实时显示进度，支持取消</li>
 *   <li>操作后：明确显示成功/失败结果</li>
 * </ul>
 *
 * @author yanxq
 * @date 2025-12-19
 */
public class BatchOperationDialog extends Stage {

    private static final Logger log = LoggerFactory.getLogger(BatchOperationDialog.class);

    private final String path;
    private final OperationType operationType;

    // UI 组件
    private VBox explanationBox;
    private ProgressBar progressBar;
    private Label progressLabel;
    private Label statusLabel;
    private Label currentFileLabel;
    private TextArea resultArea;
    private Button startBtn;
    private Button cancelBtn;
    private Button closeBtn;
    private CheckBox recursiveCheck;

    // 取消标志
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile boolean isRunning = false;

    public enum OperationType {
        GENERATE_DDL("生成数据表结构",
            "将XML配置文件转换为数据库表结构(DDL)",
            "• 读取XML文件的结构\n• 自动分析字段类型\n• 生成对应的SQL建表语句\n• 保存到同目录的.sql文件",
            "生成的SQL文件可以直接在数据库中执行，创建对应的数据表。"),

        IMPORT_XML("导入配置到数据库",
            "将XML配置数据导入到数据库中",
            "• 读取XML文件中的配置数据\n• 自动清空目标数据表\n• 将数据写入数据库\n• 保持数据完整性（全部成功或全部回滚）",
            "导入后可以在数据库中查询和编辑这些配置数据。");

        private final String displayName;
        private final String briefDescription;
        private final String detailSteps;
        private final String afterNote;

        OperationType(String displayName, String briefDescription, String detailSteps, String afterNote) {
            this.displayName = displayName;
            this.briefDescription = briefDescription;
            this.detailSteps = detailSteps;
            this.afterNote = afterNote;
        }

        public String getDisplayName() { return displayName; }
        public String getBriefDescription() { return briefDescription; }
        public String getDetailSteps() { return detailSteps; }
        public String getAfterNote() { return afterNote; }
    }

    public BatchOperationDialog(String path, OperationType operationType) {
        this.path = path;
        this.operationType = operationType;

        initUI();
    }

    private void initUI() {
        setTitle(operationType.getDisplayName());
        initModality(Modality.APPLICATION_MODAL);
        setWidth(750);
        setHeight(600);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #fafafa;");

        // ========== 1. 标题区域 ==========
        HBox titleBox = new HBox(10);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label(operationType == OperationType.GENERATE_DDL ? "🛠️" : "📥");
        iconLabel.setStyle("-fx-font-size: 24px;");

        VBox titleTextBox = new VBox(2);
        Label titleLabel = new Label(operationType.getDisplayName());
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label subtitleLabel = new Label(operationType.getBriefDescription());
        subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        titleTextBox.getChildren().addAll(titleLabel, subtitleLabel);

        titleBox.getChildren().addAll(iconLabel, titleTextBox);

        // ========== 2. 操作说明区域（操作前显示）==========
        explanationBox = new VBox(10);
        explanationBox.setPadding(new Insets(15));
        explanationBox.setStyle("-fx-background-color: #e3f2fd; -fx-background-radius: 8;");

        Label whatWillHappenLabel = new Label("📋 操作步骤说明");
        whatWillHappenLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label stepsLabel = new Label(operationType.getDetailSteps());
        stepsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #333;");
        stepsLabel.setWrapText(true);

        Label noteLabel = new Label("💡 " + operationType.getAfterNote());
        noteLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #1976d2;");
        noteLabel.setWrapText(true);

        explanationBox.getChildren().addAll(whatWillHappenLabel, stepsLabel, new Separator(), noteLabel);

        // ========== 3. 目标路径 ==========
        HBox pathBox = new HBox(10);
        pathBox.setAlignment(Pos.CENTER_LEFT);
        pathBox.setPadding(new Insets(10));
        pathBox.setStyle("-fx-background-color: white; -fx-background-radius: 5; -fx-border-color: #ddd; -fx-border-radius: 5;");

        File file = new File(path);
        boolean isDirectory = file.isDirectory();

        Label pathIconLabel = new Label(isDirectory ? "📁" : "📄");
        pathIconLabel.setStyle("-fx-font-size: 16px;");

        VBox pathInfoBox = new VBox(2);
        Label pathTypeLabel = new Label(isDirectory ? "目标目录" : "目标文件");
        pathTypeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");
        Label pathValueLabel = new Label(shortenPath(path, 60));
        pathValueLabel.setStyle("-fx-font-size: 12px;");
        pathValueLabel.setTooltip(new Tooltip(path));
        pathInfoBox.getChildren().addAll(pathTypeLabel, pathValueLabel);

        HBox.setHgrow(pathInfoBox, Priority.ALWAYS);
        pathBox.getChildren().addAll(pathIconLabel, pathInfoBox);

        // ========== 4. 选项 ==========
        HBox optionsBox = new HBox(20);
        optionsBox.setAlignment(Pos.CENTER_LEFT);

        recursiveCheck = new CheckBox("包含子目录中的所有文件");
        recursiveCheck.setSelected(true);
        recursiveCheck.setDisable(!isDirectory);
        if (!isDirectory) {
            recursiveCheck.setStyle("-fx-opacity: 0.5;");
        }

        if (isDirectory) {
            int xmlCount = countXmlFiles(file, true);
            Label countLabel = new Label("（预计处理 " + xmlCount + " 个XML文件）");
            countLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
            optionsBox.getChildren().addAll(recursiveCheck, countLabel);

            recursiveCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
                int count = countXmlFiles(file, newVal);
                countLabel.setText("（预计处理 " + count + " 个XML文件）");
            });
        } else {
            optionsBox.getChildren().add(recursiveCheck);
        }

        // ========== 5. 进度区域 ==========
        VBox progressBox = new VBox(8);
        progressBox.setPadding(new Insets(15));
        progressBox.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e0e0e0; -fx-border-radius: 8;");

        HBox progressHeaderBox = new HBox(10);
        progressHeaderBox.setAlignment(Pos.CENTER_LEFT);
        progressLabel = new Label("准备就绪，点击「开始执行」按钮");
        progressLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        progressHeaderBox.getChildren().add(progressLabel);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(20);

        currentFileLabel = new Label("");
        currentFileLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");

        progressBox.getChildren().addAll(progressHeaderBox, progressBar, currentFileLabel, statusLabel);

        // ========== 6. 结果区域 ==========
        VBox resultBox = new VBox(5);
        Label resultTitle = new Label("📊 执行结果");
        resultTitle.setStyle("-fx-font-weight: bold;");

        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        resultArea.setPrefRowCount(8);
        resultArea.setPromptText("执行完成后，结果将显示在这里...");
        resultArea.setStyle("-fx-font-family: 'Microsoft YaHei', 'SimHei', monospace;");
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        resultBox.getChildren().addAll(resultTitle, resultArea);

        // ========== 7. 按钮区域 ==========
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        startBtn = new Button("▶️ 开始执行");
        startBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20;");
        startBtn.setDefaultButton(true);
        startBtn.setOnAction(e -> confirmAndStart());

        cancelBtn = new Button("⏹️ 取消");
        cancelBtn.setStyle("-fx-padding: 8 15;");
        cancelBtn.setDisable(true);
        cancelBtn.setOnAction(e -> cancelOperation());

        closeBtn = new Button("关闭");
        closeBtn.setStyle("-fx-padding: 8 15;");
        closeBtn.setCancelButton(true);
        closeBtn.setOnAction(e -> {
            if (isRunning) {
                if (confirmCancel()) {
                    cancelled.set(true);
                    close();
                }
            } else {
                close();
            }
        });

        buttonBox.getChildren().addAll(startBtn, cancelBtn, closeBtn);

        // 组装
        root.getChildren().addAll(
            titleBox,
            explanationBox,
            pathBox,
            optionsBox,
            new Separator(),
            progressBox,
            resultBox,
            buttonBox
        );

        VBox.setVgrow(resultBox, Priority.ALWAYS);

        Scene scene = new Scene(root);
        setScene(scene);
    }

    /**
     * 确认并开始操作
     */
    private void confirmAndStart() {
        String message;
        if (operationType == OperationType.GENERATE_DDL) {
            message = "即将生成数据表结构(DDL)。\n\n" +
                      "这个操作会：\n" +
                      "• 读取选中的XML文件\n" +
                      "• 在同目录生成.sql文件\n\n" +
                      "已有的.sql文件会被覆盖，确定继续吗？";
        } else {
            message = "即将导入配置数据到数据库。\n\n" +
                      "⚠️ 重要提醒：\n" +
                      "• 目标数据表中的现有数据会被清空\n" +
                      "• 新数据将完整导入\n" +
                      "• 如果中途失败，会自动回滚\n\n" +
                      "确定要继续吗？";
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认操作");
        confirm.setHeaderText(operationType.getDisplayName());
        confirm.setContentText(message);
        confirm.getButtonTypes().setAll(
            new ButtonType("确定执行", ButtonBar.ButtonData.YES),
            new ButtonType("取消", ButtonBar.ButtonData.NO)
        );

        confirm.showAndWait().ifPresent(response -> {
            if (response.getButtonData() == ButtonBar.ButtonData.YES) {
                startOperation();
            }
        });
    }

    /**
     * 开始执行操作
     */
    private void startOperation() {
        isRunning = true;
        cancelled.set(false);

        // UI 状态切换
        startBtn.setDisable(true);
        cancelBtn.setDisable(false);
        recursiveCheck.setDisable(true);
        explanationBox.setVisible(false);
        explanationBox.setManaged(false);

        progressBar.setProgress(0);
        progressLabel.setText("正在准备...");
        progressLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1976d2;");
        resultArea.clear();

        boolean recursive = recursiveCheck.isSelected();
        File file = new File(path);

        if (operationType == OperationType.GENERATE_DDL) {
            executeDdlGeneration(file, recursive);
        } else {
            executeXmlImport(file, recursive);
        }
    }

    /**
     * 取消操作
     */
    private void cancelOperation() {
        if (confirmCancel()) {
            cancelled.set(true);
            progressLabel.setText("正在取消...");
            progressLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ff9800;");
            cancelBtn.setDisable(true);
        }
    }

    private boolean confirmCancel() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认取消");
        alert.setHeaderText("确定要取消当前操作吗？");
        alert.setContentText("已完成的部分不会被撤销。");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /**
     * 执行DDL生成
     */
    private void executeDdlGeneration(File file, boolean recursive) {
        log.info("开始批量生成DDL: {}, 递归={}", path, recursive);

        BatchDdlGenerator.ProgressCallback callback = new BatchDdlGenerator.ProgressCallback() {
            @Override
            public void onProgress(int current, int total, String currentFile) {
                if (cancelled.get()) return;

                Platform.runLater(() -> {
                    double progress = (double) current / total;
                    progressBar.setProgress(progress);
                    progressLabel.setText(String.format("正在处理 (%d/%d)", current, total));
                    currentFileLabel.setText("📄 " + getFileName(currentFile));
                    statusLabel.setText("进度: " + String.format("%.0f%%", progress * 100));
                });
            }

            @Override
            public void onComplete(BatchDdlGenerator.BatchResult result) {
                Platform.runLater(() -> {
                    isRunning = false;
                    cancelBtn.setDisable(true);
                    startBtn.setDisable(false);

                    if (cancelled.get()) {
                        showCancelledResult(result);
                    } else {
                        showDdlResult(result);
                    }

                    log.info("DDL生成完成: {}", result.getSummary());
                });
            }
        };

        if (file.isDirectory()) {
            BatchDdlGenerator.generateDirectoryDdl(path, recursive, callback);
        } else {
            BatchDdlGenerator.generateBatchDdl(java.util.Collections.singletonList(file), callback);
        }
    }

    /**
     * 执行XML导入
     */
    private void executeXmlImport(File file, boolean recursive) {
        log.info("开始批量导入XML: {}, 递归={}", path, recursive);

        BatchXmlImporter.ImportOptions options = new BatchXmlImporter.ImportOptions();
        options.setClearTableFirst(true);

        BatchXmlImporter.ProgressCallback callback = new BatchXmlImporter.ProgressCallback() {
            @Override
            public void onProgress(int current, int total, String currentFile) {
                if (cancelled.get()) return;

                Platform.runLater(() -> {
                    double progress = (double) current / total;
                    progressBar.setProgress(progress);
                    progressLabel.setText(String.format("正在导入 (%d/%d)", current, total));
                    currentFileLabel.setText("📄 " + getFileName(currentFile));
                    statusLabel.setText("进度: " + String.format("%.0f%%", progress * 100));
                });
            }

            @Override
            public void onComplete(BatchXmlImporter.BatchImportResult result) {
                Platform.runLater(() -> {
                    isRunning = false;
                    cancelBtn.setDisable(true);
                    startBtn.setDisable(false);

                    if (cancelled.get()) {
                        showCancelledImportResult(result);
                    } else {
                        showImportResult(result);

                        // 新增：如果有失败项，弹出智能诊断对话框
                        if (result.getFailed() > 0) {
                            red.jiuzhou.ui.diagnosis.BatchImportDiagnosticDialog diagnosticDialog =
                                new red.jiuzhou.ui.diagnosis.BatchImportDiagnosticDialog(result);
                            diagnosticDialog.show();
                        }
                    }

                    log.info("XML导入完成: {}", result.getSummary());
                });
            }
        };

        if (file.isDirectory()) {
            BatchXmlImporter.importDirectoryXml(path, recursive, options, callback);
        } else {
            BatchXmlImporter.importBatchXml(java.util.Collections.singletonList(file), options, callback);
        }
    }

    /**
     * 显示DDL生成结果
     */
    private void showDdlResult(BatchDdlGenerator.BatchResult result) {
        boolean allSuccess = result.getFailed() == 0;

        if (allSuccess) {
            progressBar.setProgress(1.0);
            progressLabel.setText("✅ 全部完成！");
            progressLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");
        } else {
            progressLabel.setText("⚠️ 完成（有失败）");
            progressLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ff9800;");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("          📊 DDL生成结果汇总\n");
        sb.append("═══════════════════════════════════════\n\n");

        sb.append(String.format("  ✅ 成功: %d 个文件\n", result.getSuccess()));
        sb.append(String.format("  ❌ 失败: %d 个文件\n", result.getFailed()));
        sb.append(String.format("  ⏭️ 跳过: %d 个文件\n", result.getSkipped()));
        sb.append(String.format("  📁 总计: %d 个文件\n", result.getTotal()));

        if (allSuccess && result.getSuccess() > 0) {
            sb.append("\n───────────────────────────────────────\n");
            sb.append("💡 提示: SQL文件已生成到各XML文件所在目录\n");
            sb.append("   可以直接在数据库工具中执行这些SQL文件\n");
        }

        if (!result.getFailedFiles().isEmpty()) {
            sb.append("\n───────────────────────────────────────\n");
            sb.append("❌ 失败详情（请检查以下文件）:\n\n");
            result.getFailedFiles().forEach(f -> {
                sb.append("  • ").append(getFileName(f.getPath())).append("\n");
                sb.append("    原因: ").append(simplifyErrorMessage(f.getError())).append("\n\n");
            });
        }

        resultArea.setText(sb.toString());
    }

    /**
     * 显示导入结果
     */
    private void showImportResult(BatchXmlImporter.BatchImportResult result) {
        boolean allSuccess = result.getFailed() == 0;

        if (allSuccess) {
            progressBar.setProgress(1.0);
            progressLabel.setText("✅ 全部导入成功！");
            progressLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");
        } else {
            progressLabel.setText("⚠️ 导入完成（有失败）");
            progressLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #ff9800;");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("          📊 数据导入结果汇总\n");
        sb.append("═══════════════════════════════════════\n\n");

        sb.append(String.format("  ✅ 成功: %d 个文件\n", result.getSuccess()));
        sb.append(String.format("  ❌ 失败: %d 个文件\n", result.getFailed()));
        sb.append(String.format("  ⏭️ 跳过: %d 个文件\n", result.getSkipped()));
        sb.append(String.format("  📁 总计: %d 个文件\n", result.getTotal()));

        if (allSuccess && result.getSuccess() > 0) {
            sb.append("\n───────────────────────────────────────\n");
            sb.append("💡 提示: 数据已成功导入数据库\n");
            sb.append("   • 每个文件的数据都完整导入\n");
            sb.append("   • 可以在数据库中查询和编辑\n");
        }

        if (!result.getFailedFiles().isEmpty()) {
            sb.append("\n───────────────────────────────────────\n");
            sb.append("❌ 失败详情（这些文件的数据未导入）:\n\n");
            result.getFailedFiles().forEach(f -> {
                sb.append("  • ").append(f.fileName()).append("\n");
                sb.append("    原因: ").append(f.structuredError().title()).append("\n");
                sb.append("    错误码: ").append(f.getErrorCode()).append("\n\n");
            });
            sb.append("💡 失败的数据已自动回滚，不影响其他数据\n");
            sb.append("💡 查看详细诊断请点击弹出的诊断对话框\n");
        }

        resultArea.setText(sb.toString());
    }

    /**
     * 显示取消结果
     */
    private void showCancelledResult(BatchDdlGenerator.BatchResult result) {
        progressLabel.setText("🛑 操作已取消");
        progressLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #666;");

        StringBuilder sb = new StringBuilder();
        sb.append("操作已取消\n\n");
        sb.append(String.format("已完成: %d 个文件\n", result.getSuccess()));
        sb.append(String.format("未处理: %d 个文件\n", result.getTotal() - result.getSuccess() - result.getFailed()));
        resultArea.setText(sb.toString());
    }

    private void showCancelledImportResult(BatchXmlImporter.BatchImportResult result) {
        progressLabel.setText("🛑 操作已取消");
        progressLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #666;");

        StringBuilder sb = new StringBuilder();
        sb.append("操作已取消\n\n");
        sb.append(String.format("已完成: %d 个文件\n", result.getSuccess()));
        sb.append(String.format("未处理: %d 个文件\n", result.getTotal() - result.getSuccess() - result.getFailed()));
        resultArea.setText(sb.toString());
    }

    // ==================== 工具方法 ====================

    /**
     * 简化错误信息，让设计师更容易理解
     */
    private String simplifyErrorMessage(String errorMessage) {
        if (errorMessage == null) return "未知错误";

        // 常见错误的友好化翻译
        if (errorMessage.contains("找不到表配置")) {
            return "这个XML文件没有对应的表配置，无法处理";
        }
        if (errorMessage.contains("FileNotFoundException") || errorMessage.contains("找不到文件")) {
            return "文件不存在或无法访问";
        }
        if (errorMessage.contains("Duplicate entry")) {
            return "数据库中存在重复的主键数据";
        }
        if (errorMessage.contains("Data too long")) {
            return "某些字段的数据太长，超出了数据库限制";
        }
        if (errorMessage.contains("Connection") || errorMessage.contains("connect")) {
            return "无法连接到数据库，请检查数据库是否启动";
        }
        if (errorMessage.contains("Syntax error") || errorMessage.contains("syntax")) {
            return "XML文件格式有误";
        }

        // 截断过长的错误信息
        if (errorMessage.length() > 100) {
            return errorMessage.substring(0, 100) + "...";
        }

        return errorMessage;
    }

    /**
     * 缩短路径显示
     */
    private String shortenPath(String path, int maxLength) {
        if (path.length() <= maxLength) return path;

        int lastSep = path.lastIndexOf(File.separator);
        if (lastSep > 0) {
            String fileName = path.substring(lastSep);
            int remaining = maxLength - fileName.length() - 3;
            if (remaining > 10) {
                return path.substring(0, remaining) + "..." + fileName;
            }
        }
        return "..." + path.substring(path.length() - maxLength + 3);
    }

    /**
     * 获取文件名
     */
    private String getFileName(String path) {
        if (path == null) return "";
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSep >= 0 ? path.substring(lastSep + 1) : path;
    }

    /**
     * 统计XML文件数量
     */
    private int countXmlFiles(File dir, boolean recursive) {
        if (!dir.isDirectory()) return 1;

        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".xml")) {
                    count++;
                } else if (file.isDirectory() && recursive) {
                    count += countXmlFiles(file, true);
                }
            }
        }
        return count;
    }
}
