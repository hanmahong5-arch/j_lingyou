package red.jiuzhou.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 操作日志面板
 *
 * 在数据页签下方显示操作信息和日志，避免频繁切换页面
 *
 * 特性：
 * - 可折叠/展开
 * - 彩色日志级别（INFO/SUCCESS/WARNING/ERROR）
 * - 自动滚动到最新日志
 * - 清空日志按钮
 * - 复制日志按钮
 * - 美观的UI设计
 *
 * @author Claude
 * @date 2025-12-21
 */
public class OperationLogPanel extends VBox {

    private static final Logger log = LoggerFactory.getLogger(OperationLogPanel.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private TextArea logArea;
    private Label titleLabel;
    private Button toggleButton;
    private VBox contentBox;
    private boolean isExpanded = true;
    private int maxLogLines = 500; // 最多保留500行日志

    public OperationLogPanel() {
        initUI();
    }

    private void initUI() {
        setSpacing(0);
        setStyle("-fx-background-color: white;");

        // 标题栏
        HBox titleBar = createTitleBar();

        // 日志内容区
        contentBox = createContentBox();

        getChildren().addAll(titleBar, contentBox);
    }

    /**
     * 创建标题栏
     */
    private HBox createTitleBar() {
        HBox titleBar = new HBox(10);
        titleBar.setPadding(new Insets(8, 10, 8, 10));
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #f5f7fa 0%, #e8ecf1 100%);" +
            "-fx-border-color: #d0d7de;" +
            "-fx-border-width: 0 0 1 0;"
        );

        // 折叠/展开按钮
        toggleButton = new Button("▼");
        toggleButton.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #586069;" +
            "-fx-font-size: 10px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 2 5 2 5;"
        );
        toggleButton.setOnAction(e -> toggleExpanded());

        // 标题
        titleLabel = new Label("📋 操作日志");
        titleLabel.setFont(Font.font("Microsoft YaHei", 12));
        titleLabel.setStyle("-fx-text-fill: #24292f; -fx-font-weight: bold;");

        // 状态指示器
        Label statusLabel = new Label("就绪");
        statusLabel.setStyle(
            "-fx-text-fill: #57606a;" +
            "-fx-font-size: 10px;" +
            "-fx-background-color: #ddf4ff;" +
            "-fx-padding: 2 6 2 6;" +
            "-fx-background-radius: 10;"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 工具按钮
        Button clearButton = createToolButton("🗑️", "清空日志");
        clearButton.setOnAction(e -> clearLog());

        Button copyButton = createToolButton("📋", "复制日志");
        copyButton.setOnAction(e -> copyLog());

        titleBar.getChildren().addAll(
            toggleButton,
            titleLabel,
            statusLabel,
            spacer,
            clearButton,
            copyButton
        );

        return titleBar;
    }

    /**
     * 创建工具按钮
     */
    private Button createToolButton(String text, String tooltip) {
        Button button = new Button(text);
        button.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #d0d7de;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 8 4 8;" +
            "-fx-font-size: 11px;"
        );
        button.setTooltip(new Tooltip(tooltip));

        // 悬停效果
        button.setOnMouseEntered(e -> button.setStyle(
            "-fx-background-color: #f6f8fa;" +
            "-fx-border-color: #d0d7de;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 8 4 8;" +
            "-fx-font-size: 11px;"
        ));
        button.setOnMouseExited(e -> button.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #d0d7de;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 4 8 4 8;" +
            "-fx-font-size: 11px;"
        ));

        return button;
    }

    /**
     * 创建内容区
     */
    private VBox createContentBox() {
        VBox box = new VBox();
        box.setPadding(new Insets(5));
        box.setStyle("-fx-background-color: white;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(250);  // 初始高度，能显示约10条日志
        logArea.setMinHeight(100);
        logArea.setMaxHeight(400);   // 最大高度调大，允许显示更多日志
        logArea.setStyle(
            "-fx-font-family: 'Consolas', 'Monaco', monospace;" +
            "-fx-font-size: 11px;" +
            "-fx-background-color: #f6f8fa;" +
            "-fx-control-inner-background: #f6f8fa;" +
            "-fx-text-fill: #24292f;" +
            "-fx-border-color: #d0d7de;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;"
        );

        VBox.setVgrow(logArea, Priority.ALWAYS);
        box.getChildren().add(logArea);

        // 不添加默认欢迎信息，由调用方控制初始日志内容

        return box;
    }

    /**
     * 折叠/展开
     */
    private void toggleExpanded() {
        isExpanded = !isExpanded;
        contentBox.setVisible(isExpanded);
        contentBox.setManaged(isExpanded);
        toggleButton.setText(isExpanded ? "▼" : "▶");
    }

    /**
     * 设置展开状态
     */
    public void setExpanded(boolean expanded) {
        if (this.isExpanded != expanded) {
            toggleExpanded();
        }
    }

    /**
     * 日志级别枚举
     */
    public enum LogLevel {
        INFO("ℹ️", "#0969da"),      // 蓝色
        SUCCESS("✅", "#1a7f37"),   // 绿色
        WARNING("⚠️", "#9a6700"),  // 黄色
        ERROR("❌", "#cf222e"),      // 红色
        DEBUG("🔧", "#6e7781");      // 灰色

        private final String icon;
        private final String color;

        LogLevel(String icon, String color) {
            this.icon = icon;
            this.color = color;
        }
    }

    /**
     * 添加日志（主方法）
     */
    public void appendLog(LogLevel level, String message) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            String logLine = String.format("[%s] %s %s\n",
                timestamp, level.icon, message);

            logArea.appendText(logLine);

            // 限制日志行数
            String text = logArea.getText();
            String[] lines = text.split("\n");
            if (lines.length > maxLogLines) {
                int removeLines = lines.length - maxLogLines;
                int firstNewlineIndex = 0;
                for (int i = 0; i < removeLines; i++) {
                    firstNewlineIndex = text.indexOf('\n', firstNewlineIndex) + 1;
                }
                logArea.setText(text.substring(firstNewlineIndex));
            }

            // 滚动到底部
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    /**
     * 便捷方法：INFO级别日志
     */
    public void info(String message) {
        appendLog(LogLevel.INFO, message);
        log.info(message);
    }

    /**
     * 便捷方法：SUCCESS级别日志
     */
    public void success(String message) {
        appendLog(LogLevel.SUCCESS, message);
        log.info("SUCCESS: " + message);
    }

    /**
     * 便捷方法：WARNING级别日志
     */
    public void warning(String message) {
        appendLog(LogLevel.WARNING, message);
        log.warn(message);
    }

    /**
     * 便捷方法：ERROR级别日志
     */
    public void error(String message) {
        appendLog(LogLevel.ERROR, message);
        log.error(message);
    }

    /**
     * 便捷方法：ERROR级别日志（带异常）
     */
    public void error(String message, Throwable t) {
        appendLog(LogLevel.ERROR, message + ": " + t.getMessage());
        log.error(message, t);
    }

    /**
     * 便捷方法：DEBUG级别日志
     */
    public void debug(String message) {
        appendLog(LogLevel.DEBUG, message);
        log.debug(message);
    }

    /**
     * 清空日志
     */
    public void clearLog() {
        Platform.runLater(() -> {
            logArea.clear();
            info("日志已清空");
        });
    }

    /**
     * 复制日志到剪贴板
     */
    public void copyLog() {
        Platform.runLater(() -> {
            String text = logArea.getText();
            if (text != null && !text.isEmpty()) {
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(text);
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                success("日志已复制到剪贴板");
            } else {
                warning("日志为空，无法复制");
            }
        });
    }

    /**
     * 设置日志区域高度
     */
    public void setLogAreaHeight(double height) {
        logArea.setPrefHeight(height);
    }

    /**
     * 获取日志文本
     */
    public String getLogText() {
        return logArea.getText();
    }
}
