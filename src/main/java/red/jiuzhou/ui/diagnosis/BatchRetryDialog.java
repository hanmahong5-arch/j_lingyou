package red.jiuzhou.ui.diagnosis;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.batch.BatchXmlImporter;
import red.jiuzhou.batch.diagnosis.DiagnosticFailure;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量重试对话框
 *
 * 显示批量重试的进度和结果，让设计师清晰了解每个文件的重试状态
 *
 * @author Claude AI
 * @date 2026-01-15
 */
public class BatchRetryDialog extends Stage {

    private static final Logger log = LoggerFactory.getLogger(BatchRetryDialog.class);

    private final List<DiagnosticFailure> failuresToRetry;
    private final BatchImportDiagnosticDialog parentDialog;

    private ProgressBar progressBar;
    private Label progressLabel;
    private Label currentFileLabel;
    private TextArea resultArea;
    private Button closeBtn;

    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    /**
     * 构造函数
     *
     * @param failuresToRetry 需要重试的失败项列表
     * @param parentDialog 父对话框（用于刷新）
     */
    public BatchRetryDialog(List<DiagnosticFailure> failuresToRetry,
                           BatchImportDiagnosticDialog parentDialog) {
        this.failuresToRetry = failuresToRetry;
        this.parentDialog = parentDialog;

        initUI();
        startRetry();
    }

    /**
     * 初始化 UI
     */
    private void initUI() {
        setTitle("🔄 批量重试");
        initModality(Modality.APPLICATION_MODAL);
        setWidth(650);
        setHeight(500);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #fafafa;");

        // 标题
        Label titleLabel = new Label("批量重试导入");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // 进度条
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(20);

        // 进度标签
        progressLabel = new Label(String.format("准备重试 %d 个文件...", failuresToRetry.size()));
        progressLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        // 当前文件标签
        currentFileLabel = new Label("");
        currentFileLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        // 结果文本区
        Label resultTitleLabel = new Label("📊 重试详情");
        resultTitleLabel.setStyle("-fx-font-weight: bold;");

        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        resultArea.setStyle("-fx-font-family: 'Microsoft YaHei', monospace;");
        VBox.setVgrow(resultArea, javafx.scene.layout.Priority.ALWAYS);

        // 关闭按钮
        closeBtn = new Button("关闭");
        closeBtn.setDisable(true);  // 重试完成后启用
        closeBtn.setOnAction(e -> close());

        javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(closeBtn);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(
            titleLabel,
            progressBar,
            progressLabel,
            currentFileLabel,
            resultTitleLabel,
            resultArea,
            buttonBox
        );

        Scene scene = new Scene(root);
        setScene(scene);
    }

    /**
     * 开始批量重试
     */
    private void startRetry() {
        new Thread(() -> {
            int total = failuresToRetry.size();
            AtomicInteger processed = new AtomicInteger(0);

            for (DiagnosticFailure failure : failuresToRetry) {
                int current = processed.incrementAndGet();

                Platform.runLater(() -> {
                    double progress = (double) current / total;
                    progressBar.setProgress(progress);
                    progressLabel.setText(String.format("正在重试 (%d/%d)", current, total));
                    currentFileLabel.setText("📄 " + failure.fileName());
                });

                try {
                    log.info("批量重试 [{}/{}]: {}", current, total, failure.fileName());

                    // 调用导入方法
                    BatchXmlImporter.ImportOptions options = new BatchXmlImporter.ImportOptions();
                    boolean success = BatchXmlImporter.importSingleXmlSync(failure.filePath(), options);

                    if (success) {
                        successCount.incrementAndGet();
                        Platform.runLater(() -> {
                            appendResult(String.format("✅ [%d/%d] %s - 重试成功",
                                current, total, failure.fileName()));
                        });
                        log.info("✅ 批量重试成功: {}", failure.fileName());
                    } else {
                        failureCount.incrementAndGet();
                        Platform.runLater(() -> {
                            appendResult(String.format("❌ [%d/%d] %s - 重试失败",
                                current, total, failure.fileName()));
                        });
                        log.warn("❌ 批量重试失败: {}", failure.fileName());
                    }

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    Platform.runLater(() -> {
                        appendResult(String.format("❌ [%d/%d] %s - 异常: %s",
                            current, total, failure.fileName(), e.getMessage()));
                    });
                    log.error("批量重试异常: " + failure.fileName(), e);
                }
            }

            // 全部完成
            Platform.runLater(() -> {
                progressBar.setProgress(1.0);
                progressLabel.setText("✅ 批量重试完成");
                progressLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
                currentFileLabel.setText("");

                appendResult("\n═══════════════════════════════════════");
                appendResult("          📊 批量重试结果汇总");
                appendResult("═══════════════════════════════════════\n");
                appendResult(String.format("  ✅ 成功: %d 个文件", successCount.get()));
                appendResult(String.format("  ❌ 失败: %d 个文件", failureCount.get()));
                appendResult(String.format("  📁 总计: %d 个文件\n", total));

                if (successCount.get() > 0) {
                    appendResult("💡 提示: 成功的文件已从失败列表移除");
                }

                closeBtn.setDisable(false);

                // 刷新父对话框
                if (parentDialog != null) {
                    parentDialog.refreshAfterRetry();
                }

                log.info("批量重试完成: 成功={}, 失败={}, 总计={}",
                    successCount.get(), failureCount.get(), total);
            });

        }, "BatchRetry").start();
    }

    /**
     * 追加结果文本
     */
    private void appendResult(String text) {
        resultArea.appendText(text + "\n");
    }
}
