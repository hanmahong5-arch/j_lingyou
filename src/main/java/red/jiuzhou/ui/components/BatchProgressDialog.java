package red.jiuzhou.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * 批量操作进度对话框
 *
 * 显示批量操作的实时进度、成功/失败统计、详细日志
 *
 * @author Claude
 * @date 2025-12-20
 */
public class BatchProgressDialog extends Stage {

    private ProgressBar progressBar;
    private Label progressLabel;
    private Label statsLabel;
    private TextArea logArea;
    private Button closeButton;
    private Button cancelButton;

    private int totalCount;
    private int currentCount;
    private int successCount;
    private int failureCount;
    private volatile boolean cancelled = false;

    public BatchProgressDialog(Stage owner, String title, int totalCount) {
        this.totalCount = totalCount;
        this.currentCount = 0;
        this.successCount = 0;
        this.failureCount = 0;

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(title);
        setWidth(600);
        setHeight(450);
        setResizable(true);

        initUI();
    }

    private void initUI() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: white;");

        // 标题
        Label titleLabel = new Label(getTitle());
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        // 进度条
        HBox progressBox = new HBox(10);
        progressBox.setAlignment(Pos.CENTER_LEFT);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        progressLabel = new Label("0 / " + totalCount);
        progressLabel.setStyle("-fx-font-size: 13; -fx-font-weight: bold;");
        progressLabel.setPrefWidth(100);

        progressBox.getChildren().addAll(progressBar, progressLabel);

        // 统计信息
        statsLabel = new Label("等待开始...");
        statsLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #666;");

        // 日志区域
        Label logLabel = new Label("详细日志:");
        logLabel.setStyle("-fx-font-size: 12; -fx-font-weight: bold;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        // 按钮区
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        closeButton = new Button("完成");
        closeButton.setPrefWidth(80);
        closeButton.setDisable(true);
        closeButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        closeButton.setOnAction(e -> close());

        cancelButton = new Button("取消");
        cancelButton.setPrefWidth(80);
        cancelButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        cancelButton.setOnAction(e -> {
            cancelled = true;
            cancelButton.setDisable(true);
            log("❌ 用户取消操作");
        });

        buttonBox.getChildren().addAll(cancelButton, closeButton);

        root.getChildren().addAll(
            titleLabel,
            progressBox,
            statsLabel,
            new Separator(),
            logLabel,
            logArea,
            buttonBox
        );

        Scene scene = new Scene(root);
        setScene(scene);

        // 关闭请求
        setOnCloseRequest(e -> {
            if (!closeButton.isDisabled()) {
                close();
            } else {
                e.consume();  // 进行中时阻止关闭
            }
        });
    }

    /**
     * 更新进度
     */
    public void updateProgress(int current, boolean success) {
        Platform.runLater(() -> {
            this.currentCount = current;
            if (success) {
                this.successCount++;
            } else {
                this.failureCount++;
            }

            double progress = (double) current / totalCount;
            progressBar.setProgress(progress);
            progressLabel.setText(current + " / " + totalCount);

            // 更新统计
            statsLabel.setText(String.format(
                "成功: %d  |  失败: %d  |  进度: %.1f%%",
                successCount, failureCount, progress * 100
            ));

            // 完成时启用关闭按钮
            if (current >= totalCount) {
                closeButton.setDisable(false);
                cancelButton.setDisable(true);

                // 显示最终结果
                if (failureCount == 0) {
                    log("✅ 全部完成！成功: " + successCount);
                } else {
                    log(String.format("⚠️ 完成，成功: %d，失败: %d", successCount, failureCount));
                }
            }
        });
    }

    /**
     * 添加日志
     */
    public void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);  // 自动滚动到底部
        });
    }

    /**
     * 添加成功日志
     */
    public void logSuccess(String message) {
        log("✅ " + message);
    }

    /**
     * 添加失败日志
     */
    public void logError(String message) {
        log("❌ " + message);
    }

    /**
     * 添加警告日志
     */
    public void logWarning(String message) {
        log("⚠️ " + message);
    }

    /**
     * 添加信息日志
     */
    public void logInfo(String message) {
        log("ℹ️ " + message);
    }

    /**
     * 检查是否已取消
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * 设置为完成状态
     */
    public void setCompleted() {
        Platform.runLater(() -> {
            closeButton.setDisable(false);
            cancelButton.setDisable(true);
        });
    }

    /**
     * 显示对话框（非阻塞）
     */
    public void showNonBlocking() {
        Platform.runLater(this::show);
    }

    /**
     * 显示对话框并等待关闭
     */
    public void showAndWaitForCompletion() {
        Platform.runLater(this::showAndWait);
    }
}
