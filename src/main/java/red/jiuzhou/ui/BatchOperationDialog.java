package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.batch.BatchDdlGenerator;
import red.jiuzhou.batch.BatchXmlImporter;

import java.io.File;
import java.util.List;

/**
 * 批量操作对话框
 *
 * 支持：
 * - 批量生成DDL
 * - 批量导入XML
 * - 进度显示
 * - 结果统计
 *
 * @author yanxq
 * @date 2025-12-19
 */
public class BatchOperationDialog extends Stage {

    private static final Logger log = LoggerFactory.getLogger(BatchOperationDialog.class);

    private final String path;
    private final OperationType operationType;

    private ProgressBar progressBar;
    private Label progressLabel;
    private Label statusLabel;
    private TextArea resultArea;
    private Button startBtn;
    private Button closeBtn;

    private CheckBox recursiveCheck;

    public enum OperationType {
        GENERATE_DDL("生成DDL"),
        IMPORT_XML("导入到数据库");

        private final String displayName;

        OperationType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public BatchOperationDialog(String path, OperationType operationType) {
        this.path = path;
        this.operationType = operationType;

        initUI();
    }

    private void initUI() {
        setTitle("批量操作 - " + operationType.getDisplayName());
        initModality(Modality.APPLICATION_MODAL);
        setWidth(700);
        setHeight(500);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        // 标题
        Label titleLabel = new Label(operationType.getDisplayName());
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // 路径信息
        HBox pathBox = new HBox(10);
        pathBox.setAlignment(Pos.CENTER_LEFT);
        Label pathLabel = new Label("目标路径:");
        TextField pathField = new TextField(path);
        pathField.setEditable(false);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        pathBox.getChildren().addAll(pathLabel, pathField);

        // 选项
        HBox optionsBox = new HBox(15);
        optionsBox.setAlignment(Pos.CENTER_LEFT);

        File file = new File(path);
        boolean isDirectory = file.isDirectory();

        recursiveCheck = new CheckBox("递归处理子目录");
        recursiveCheck.setSelected(true);
        recursiveCheck.setDisable(!isDirectory);

        Label typeLabel = new Label(isDirectory ? "📁 目录" : "📄 文件");
        typeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        optionsBox.getChildren().addAll(typeLabel, recursiveCheck);

        // 进度区域
        VBox progressBox = new VBox(8);
        progressBox.setPadding(new Insets(10));
        progressBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");

        progressLabel = new Label("准备中...");
        progressLabel.setStyle("-fx-font-size: 12px;");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);

        statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        progressBox.getChildren().addAll(progressLabel, progressBar, statusLabel);

        // 结果区域
        Label resultTitle = new Label("执行结果:");
        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        resultArea.setPrefRowCount(10);
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        // 按钮区域
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        startBtn = new Button("▶️ 开始执行");
        startBtn.setDefaultButton(true);
        startBtn.setOnAction(e -> startOperation());

        closeBtn = new Button("关闭");
        closeBtn.setCancelButton(true);
        closeBtn.setOnAction(e -> close());

        buttonBox.getChildren().addAll(startBtn, closeBtn);

        root.getChildren().addAll(
            titleLabel,
            pathBox,
            optionsBox,
            new Separator(),
            progressBox,
            resultTitle,
            resultArea,
            buttonBox
        );

        Scene scene = new Scene(root);
        setScene(scene);
    }

    /**
     * 开始执行操作
     */
    private void startOperation() {
        startBtn.setDisable(true);
        progressBar.setProgress(0);
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
     * 执行DDL生成
     */
    private void executeDdlGeneration(File file, boolean recursive) {
        log.info("开始批量生成DDL: {}, 递归={}", path, recursive);

        BatchDdlGenerator.ProgressCallback callback = new BatchDdlGenerator.ProgressCallback() {
            @Override
            public void onProgress(int current, int total, String currentFile) {
                Platform.runLater(() -> {
                    double progress = (double) current / total;
                    progressBar.setProgress(progress);
                    progressLabel.setText(String.format("正在处理... (%d/%d)", current, total));
                    statusLabel.setText("当前文件: " + currentFile);
                });
            }

            @Override
            public void onComplete(BatchDdlGenerator.BatchResult result) {
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    progressLabel.setText("完成!");

                    // 显示结果
                    StringBuilder sb = new StringBuilder();
                    sb.append("✅ 批量生成DDL完成\n\n");
                    sb.append(result.getSummary()).append("\n\n");

                    if (!result.getSuccessFiles().isEmpty()) {
                        sb.append("--- 成功文件 ---\n");
                        result.getSuccessFiles().forEach(f ->
                            sb.append("✓ ").append(f).append("\n")
                        );
                        sb.append("\n");
                    }

                    if (!result.getFailedFiles().isEmpty()) {
                        sb.append("--- 失败文件 ---\n");
                        result.getFailedFiles().forEach(f ->
                            sb.append("✗ ").append(f.toString()).append("\n")
                        );
                    }

                    resultArea.setText(sb.toString());
                    startBtn.setDisable(false);

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

        // 导入选项
        BatchXmlImporter.ImportOptions options = new BatchXmlImporter.ImportOptions();
        options.setClearTableFirst(true);

        BatchXmlImporter.ProgressCallback callback = new BatchXmlImporter.ProgressCallback() {
            @Override
            public void onProgress(int current, int total, String currentFile) {
                Platform.runLater(() -> {
                    double progress = (double) current / total;
                    progressBar.setProgress(progress);
                    progressLabel.setText(String.format("正在导入... (%d/%d)", current, total));
                    statusLabel.setText("当前文件: " + currentFile);
                });
            }

            @Override
            public void onComplete(BatchXmlImporter.BatchImportResult result) {
                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    progressLabel.setText("完成!");

                    // 显示结果
                    StringBuilder sb = new StringBuilder();
                    sb.append("✅ 批量导入XML完成\n\n");
                    sb.append(result.getSummary()).append("\n\n");

                    if (!result.getSuccessFiles().isEmpty()) {
                        sb.append("--- 成功文件 ---\n");
                        result.getSuccessFiles().forEach(f ->
                            sb.append("✓ ").append(f).append("\n")
                        );
                        sb.append("\n");
                    }

                    if (!result.getFailedFiles().isEmpty()) {
                        sb.append("--- 失败文件 ---\n");
                        result.getFailedFiles().forEach(f ->
                            sb.append("✗ ").append(f.toString()).append("\n")
                        );
                    }

                    resultArea.setText(sb.toString());
                    startBtn.setDisable(false);

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
}
