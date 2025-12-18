package red.jiuzhou.ui.components;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 增强状态栏组件
 *
 * <p>为游戏设计师提供丰富的状态信息：
 * <ul>
 *   <li>当前操作状态和进度</li>
 *   <li>数据库连接状态</li>
 *   <li>内存使用情况</li>
 *   <li>消息通知队列</li>
 *   <li>快速操作按钮</li>
 *   <li>时间显示</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class EnhancedStatusBar extends HBox {

    private static final Logger log = LoggerFactory.getLogger(EnhancedStatusBar.class);

    // UI组件
    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final Label progressLabel;
    private final Circle connectionIndicator;
    private final Label connectionLabel;
    private final ProgressBar memoryBar;
    private final Label memoryLabel;
    private final Label timeLabel;
    private final HBox messageArea;
    private final Label messageLabel;

    // 消息队列
    private final Queue<StatusMessage> messageQueue = new ConcurrentLinkedQueue<>();
    private Timeline messageTimeline;
    private Timeline updateTimeline;

    // 状态
    private boolean isConnected = false;
    private String databaseName = "未连接";
    private double currentProgress = 0;
    private String currentStatus = "就绪";

    /**
     * 状态消息类型
     */
    public enum MessageType {
        INFO("#17a2b8", "ℹ️"),
        SUCCESS("#28a745", "✓"),
        WARNING("#ffc107", "⚠"),
        ERROR("#dc3545", "✕");

        final String color;
        final String icon;

        MessageType(String color, String icon) {
            this.color = color;
            this.icon = icon;
        }
    }

    /**
     * 状态消息
     */
    public static class StatusMessage {
        final String text;
        final MessageType type;
        final long timestamp;
        final int displaySeconds;

        public StatusMessage(String text, MessageType type, int displaySeconds) {
            this.text = text;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
            this.displaySeconds = displaySeconds;
        }
    }

    public EnhancedStatusBar() {
        setSpacing(0);
        setAlignment(Pos.CENTER_LEFT);
        setStyle("-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef); " +
                "-fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");
        setPadding(new Insets(4, 10, 4, 10));

        // === 左侧区域：状态和进度 ===
        HBox leftArea = new HBox(10);
        leftArea.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("就绪");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #495057;");
        statusLabel.setMinWidth(80);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(120);
        progressBar.setPrefHeight(12);
        progressBar.setVisible(false);

        progressLabel = new Label("");
        progressLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");
        progressLabel.setVisible(false);

        leftArea.getChildren().addAll(statusLabel, progressBar, progressLabel);

        // === 中间区域：消息通知 ===
        messageArea = new HBox(5);
        messageArea.setAlignment(Pos.CENTER);
        HBox.setHgrow(messageArea, Priority.ALWAYS);

        messageLabel = new Label("");
        messageLabel.setStyle("-fx-font-size: 11px;");
        messageArea.getChildren().add(messageLabel);

        // === 右侧区域：系统信息 ===
        HBox rightArea = new HBox(15);
        rightArea.setAlignment(Pos.CENTER_RIGHT);

        // 数据库连接状态
        HBox connectionBox = new HBox(5);
        connectionBox.setAlignment(Pos.CENTER);

        connectionIndicator = new Circle(5);
        connectionIndicator.setFill(Color.web("#dc3545"));

        connectionLabel = new Label("未连接");
        connectionLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");

        Tooltip connectionTip = new Tooltip("点击查看数据库连接详情");
        Tooltip.install(connectionBox, connectionTip);
        connectionBox.setOnMouseClicked(e -> showConnectionDetails());

        connectionBox.getChildren().addAll(connectionIndicator, connectionLabel);

        // 内存使用
        HBox memoryBox = new HBox(5);
        memoryBox.setAlignment(Pos.CENTER);

        Label memoryIcon = new Label("💾");
        memoryIcon.setStyle("-fx-font-size: 10px;");

        memoryBar = new ProgressBar(0);
        memoryBar.setPrefWidth(60);
        memoryBar.setPrefHeight(10);

        memoryLabel = new Label("0%");
        memoryLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6c757d;");
        memoryLabel.setMinWidth(35);

        Tooltip memoryTip = new Tooltip("内存使用情况\n点击进行垃圾回收");
        Tooltip.install(memoryBox, memoryTip);
        memoryBox.setOnMouseClicked(e -> {
            System.gc();
            showMessage("已触发垃圾回收", MessageType.INFO, 2);
        });

        memoryBox.getChildren().addAll(memoryIcon, memoryBar, memoryLabel);

        // 时间显示
        timeLabel = new Label("");
        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #adb5bd;");
        timeLabel.setMinWidth(55);

        // 分隔符
        Separator sep1 = createVerticalSeparator();
        Separator sep2 = createVerticalSeparator();
        Separator sep3 = createVerticalSeparator();

        rightArea.getChildren().addAll(sep1, connectionBox, sep2, memoryBox, sep3, timeLabel);

        // 组装
        getChildren().addAll(leftArea, messageArea, rightArea);

        // 启动定时更新
        startPeriodicUpdates();

        // 启动消息显示
        startMessageProcessor();
    }

    /**
     * 创建垂直分隔符
     */
    private Separator createVerticalSeparator() {
        Separator sep = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep.setPadding(new Insets(2, 0, 2, 0));
        return sep;
    }

    /**
     * 启动定时更新
     */
    private void startPeriodicUpdates() {
        updateTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            updateMemoryInfo();
            updateTimeLabel();
        }));
        updateTimeline.setCycleCount(Timeline.INDEFINITE);
        updateTimeline.play();
    }

    /**
     * 启动消息处理器
     */
    private void startMessageProcessor() {
        messageTimeline = new Timeline(new KeyFrame(Duration.millis(100), e -> processMessageQueue()));
        messageTimeline.setCycleCount(Timeline.INDEFINITE);
        messageTimeline.play();
    }

    /**
     * 处理消息队列
     */
    private void processMessageQueue() {
        StatusMessage msg = messageQueue.peek();
        if (msg != null) {
            long elapsed = System.currentTimeMillis() - msg.timestamp;
            if (elapsed > msg.displaySeconds * 1000L) {
                messageQueue.poll();
                // 检查下一条消息
                StatusMessage next = messageQueue.peek();
                if (next != null) {
                    displayMessage(next);
                } else {
                    messageLabel.setText("");
                }
            }
        }
    }

    /**
     * 显示消息
     */
    private void displayMessage(StatusMessage msg) {
        messageLabel.setText(msg.type.icon + " " + msg.text);
        messageLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + msg.type.color + ";");
    }

    /**
     * 更新内存信息
     */
    private void updateMemoryInfo() {
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

            long used = heapUsage.getUsed();
            long max = heapUsage.getMax();
            double ratio = (double) used / max;

            memoryBar.setProgress(ratio);

            // 根据使用率调整颜色
            String color;
            if (ratio > 0.9) {
                color = "#dc3545";  // 红色
            } else if (ratio > 0.7) {
                color = "#ffc107";  // 黄色
            } else {
                color = "#28a745";  // 绿色
            }

            memoryLabel.setText(String.format("%.0f%%", ratio * 100));
            memoryLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + color + ";");

        } catch (Exception e) {
            log.debug("更新内存信息失败: {}", e.getMessage());
        }
    }

    /**
     * 更新时间显示
     */
    private void updateTimeLabel() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        timeLabel.setText(sdf.format(new Date()));
    }

    // ==================== 公共方法 ====================

    /**
     * 设置状态文本
     */
    public void setStatus(String status) {
        Platform.runLater(() -> {
            currentStatus = status;
            statusLabel.setText(status);
        });
    }

    /**
     * 显示进度
     */
    public void showProgress(double progress, String text) {
        Platform.runLater(() -> {
            currentProgress = progress;
            progressBar.setProgress(progress);
            progressLabel.setText(text);
            progressBar.setVisible(true);
            progressLabel.setVisible(true);
        });
    }

    /**
     * 隐藏进度
     */
    public void hideProgress() {
        Platform.runLater(() -> {
            progressBar.setVisible(false);
            progressLabel.setVisible(false);
            progressBar.setProgress(0);
        });
    }

    /**
     * 设置数据库连接状态
     */
    public void setConnectionStatus(boolean connected, String dbName) {
        Platform.runLater(() -> {
            isConnected = connected;
            databaseName = dbName != null ? dbName : "未知";

            if (connected) {
                connectionIndicator.setFill(Color.web("#28a745"));
                connectionLabel.setText(databaseName);
            } else {
                connectionIndicator.setFill(Color.web("#dc3545"));
                connectionLabel.setText("未连接");
            }
        });
    }

    /**
     * 显示消息
     */
    public void showMessage(String text, MessageType type, int displaySeconds) {
        StatusMessage msg = new StatusMessage(text, type, displaySeconds);
        messageQueue.offer(msg);

        // 如果是第一条消息，立即显示
        if (messageQueue.size() == 1) {
            Platform.runLater(() -> displayMessage(msg));
        }
    }

    /**
     * 显示信息消息
     */
    public void info(String text) {
        showMessage(text, MessageType.INFO, 3);
    }

    /**
     * 显示成功消息
     */
    public void success(String text) {
        showMessage(text, MessageType.SUCCESS, 3);
    }

    /**
     * 显示警告消息
     */
    public void warning(String text) {
        showMessage(text, MessageType.WARNING, 5);
    }

    /**
     * 显示错误消息
     */
    public void error(String text) {
        showMessage(text, MessageType.ERROR, 8);
    }

    /**
     * 显示连接详情
     */
    private void showConnectionDetails() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("数据库连接详情");
        alert.setHeaderText(isConnected ? "已连接" : "未连接");

        StringBuilder sb = new StringBuilder();
        sb.append("数据库名称: ").append(databaseName).append("\n");
        sb.append("连接状态: ").append(isConnected ? "正常" : "断开").append("\n");

        alert.setContentText(sb.toString());
        alert.showAndWait();
    }

    /**
     * 开始操作
     */
    public void startOperation(String operationName) {
        setStatus(operationName + "...");
        showProgress(ProgressIndicator.INDETERMINATE_PROGRESS, "处理中");
    }

    /**
     * 结束操作
     */
    public void endOperation(boolean success, String message) {
        hideProgress();
        setStatus("就绪");
        if (success) {
            success(message);
        } else {
            error(message);
        }
    }

    /**
     * 清除所有消息
     */
    public void clearMessages() {
        messageQueue.clear();
        Platform.runLater(() -> messageLabel.setText(""));
    }

    /**
     * 释放资源
     */
    public void dispose() {
        if (messageTimeline != null) {
            messageTimeline.stop();
        }
        if (updateTimeline != null) {
            updateTimeline.stop();
        }
    }

    // ==================== 便捷方法 ====================

    /**
     * 设置当前表信息
     */
    public void setTableInfo(String tableName, int rowCount) {
        setStatus(String.format("%s (%d 行)", tableName, rowCount));
    }

    /**
     * 设置选择信息
     */
    public void setSelectionInfo(int selectedCount, int totalCount) {
        if (selectedCount == 0) {
            setStatus(String.format("共 %d 行", totalCount));
        } else {
            setStatus(String.format("已选 %d/%d 行", selectedCount, totalCount));
        }
    }
}
