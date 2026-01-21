package red.jiuzhou.ops.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.core.AuditLog;
import red.jiuzhou.ops.core.CacheManager;
import red.jiuzhou.ops.service.GameOpsService;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.Map;

/**
 * 服务器状态监控面板
 *
 * 实时显示：
 * - 数据库连接状态
 * - 在线玩家数量
 * - CPU/内存使用率
 * - 操作统计图表
 * - 缓存状态
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class ServerStatusPanel extends VBox {

    private static final Logger log = LoggerFactory.getLogger(ServerStatusPanel.class);
    private static final int CHART_HISTORY_SIZE = 60; // 60 data points

    private final GameOpsService opsService;

    // Status Cards
    private final Label onlinePlayersLabel = new Label("0");
    private final Label totalCharsLabel = new Label("0");
    private final Label totalGuildsLabel = new Label("0");
    private final Label opsPerMinLabel = new Label("0");

    // System Status
    private final ProgressBar cpuBar = new ProgressBar(0);
    private final ProgressBar memBar = new ProgressBar(0);
    private final Label cpuLabel = new Label("CPU: 0%");
    private final Label memLabel = new Label("内存: 0%");

    // Cache Status
    private final Label cacheHitRateLabel = new Label("0%");
    private final Label cacheSizeLabel = new Label("0/10000");

    // Chart
    private final XYChart.Series<Number, Number> onlineSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> opsSeries = new XYChart.Series<>();
    private final LinkedList<Long> onlineHistory = new LinkedList<>();
    private int chartIndex = 0;

    // Update timeline
    private Timeline updateTimeline;

    public ServerStatusPanel(GameOpsService opsService) {
        this.opsService = opsService;
        buildUI();
        startAutoRefresh();
    }

    private void buildUI() {
        setSpacing(12);
        setPadding(new Insets(12));
        setStyle("-fx-background-color: #f8f9fa;");

        // Header
        Label header = new Label("📊 服务器状态监控");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Stats Cards Row
        HBox statsRow = createStatsCards();

        // System Status Row
        HBox systemRow = createSystemStatus();

        // Chart
        VBox chartBox = createChart();

        // Recent Operations
        VBox recentOps = createRecentOperations();

        getChildren().addAll(header, statsRow, systemRow, chartBox, recentOps);
    }

    private HBox createStatsCards() {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);

        row.getChildren().addAll(
                createStatCard("🟢 在线玩家", onlinePlayersLabel, "#27ae60"),
                createStatCard("👤 总角色", totalCharsLabel, "#3498db"),
                createStatCard("👥 总公会", totalGuildsLabel, "#9b59b6"),
                createStatCard("⚡ 操作/分", opsPerMinLabel, "#e67e22")
        );

        return row;
    }

    private VBox createStatCard(String title, Label valueLabel, String color) {
        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12, 20, 12, 20));
        card.setStyle(String.format(
                "-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);"
        ));
        card.setPrefWidth(140);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        valueLabel.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 24px; -fx-font-weight: bold;", color
        ));

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private HBox createSystemStatus() {
        HBox row = new HBox(20);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12));
        row.setStyle("-fx-background-color: white; -fx-background-radius: 8;");

        // CPU
        VBox cpuBox = new VBox(4);
        cpuBox.setAlignment(Pos.CENTER_LEFT);
        cpuBar.setPrefWidth(150);
        cpuBar.setStyle("-fx-accent: #3498db;");
        cpuLabel.setStyle("-fx-font-size: 11px;");
        cpuBox.getChildren().addAll(cpuLabel, cpuBar);

        // Memory
        VBox memBox = new VBox(4);
        memBox.setAlignment(Pos.CENTER_LEFT);
        memBar.setPrefWidth(150);
        memBar.setStyle("-fx-accent: #9b59b6;");
        memLabel.setStyle("-fx-font-size: 11px;");
        memBox.getChildren().addAll(memLabel, memBar);

        // Cache
        VBox cacheBox = new VBox(4);
        cacheBox.setAlignment(Pos.CENTER_LEFT);
        Label cacheTitle = new Label("📦 缓存");
        cacheTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        HBox cacheInfo = new HBox(8);
        cacheInfo.getChildren().addAll(
                new Label("命中率:"), cacheHitRateLabel,
                new Label("容量:"), cacheSizeLabel
        );
        cacheBox.getChildren().addAll(cacheTitle, cacheInfo);

        // Time
        Label timeLabel = new Label();
        timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #999;");
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            timeLabel.setText(LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("HH:mm:ss")
            ));
        }));
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(cpuBox, memBox, cacheBox, spacer, timeLabel);
        return row;
    }

    private VBox createChart() {
        VBox chartBox = new VBox(8);

        Label chartTitle = new Label("📈 实时趋势");
        chartTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        NumberAxis xAxis = new NumberAxis(0, 60, 10);
        xAxis.setLabel("时间 (秒)");
        xAxis.setAutoRanging(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("数量");
        yAxis.setAutoRanging(true);

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setPrefHeight(200);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setLegendVisible(true);

        onlineSeries.setName("在线玩家");
        opsSeries.setName("操作数/分钟");

        chart.getData().addAll(onlineSeries, opsSeries);

        chartBox.getChildren().addAll(chartTitle, chart);
        return chartBox;
    }

    private VBox createRecentOperations() {
        VBox box = new VBox(8);

        Label title = new Label("📝 最近操作");
        title.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        VBox logBox = new VBox(2);
        logBox.setId("recentOpsBox");
        logBox.setPadding(new Insets(8));
        logBox.setStyle("-fx-background-color: #2c3e50; -fx-background-radius: 4;");
        logBox.setPrefHeight(120);

        // Add some placeholder
        for (int i = 0; i < 5; i++) {
            Label placeholder = new Label("等待操作记录...");
            placeholder.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11px;");
            logBox.getChildren().add(placeholder);
        }

        // Subscribe to audit log
        AuditLog.getInstance().addListener(entry -> {
            Platform.runLater(() -> updateRecentOps(logBox, entry));
        });

        box.getChildren().addAll(title, logBox);
        return box;
    }

    private void updateRecentOps(VBox logBox, AuditLog.AuditEntry entry) {
        String color = switch (entry.status()) {
            case SUCCESS -> "#27ae60";
            case FAILED -> "#e74c3c";
            case WARNING -> "#f39c12";
            default -> "#3498db";
        };

        Label logLine = new Label(String.format("[%s] %s %s - %s",
                entry.timestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                entry.status().getIcon(),
                entry.operation(),
                entry.target() != null ? entry.target() : ""
        ));
        logLine.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 11px; -fx-font-family: 'Consolas';", color));

        logBox.getChildren().add(0, logLine);

        // Keep only last 10 entries
        while (logBox.getChildren().size() > 10) {
            logBox.getChildren().remove(logBox.getChildren().size() - 1);
        }
    }

    private void startAutoRefresh() {
        updateTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> refreshStats()));
        updateTimeline.setCycleCount(Animation.INDEFINITE);
        updateTimeline.play();
    }

    private void refreshStats() {
        // Update in background thread
        new Thread(() -> {
            try {
                // Get server stats
                Map<String, Object> stats = null;
                if (opsService != null) {
                    stats = opsService.getServerStatistics();
                }

                // Get system stats - 使用真实的 CPU 使用率
                OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
                MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();

                double cpuLoad = 0;
                // 尝试获取真实的 CPU 使用率 (Windows 兼容)
                if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                    cpuLoad = sunOsBean.getCpuLoad(); // Java 17+ 方法
                    if (cpuLoad < 0) {
                        cpuLoad = sunOsBean.getSystemCpuLoad(); // 旧方法
                    }
                }
                if (cpuLoad < 0) {
                    cpuLoad = osBean.getSystemLoadAverage() / osBean.getAvailableProcessors();
                }
                if (cpuLoad < 0) cpuLoad = 0; // 如果还是获取不到，显示 0 而不是假数据

                long usedMem = memBean.getHeapMemoryUsage().getUsed();
                long maxMem = memBean.getHeapMemoryUsage().getMax();
                double memPercent = (double) usedMem / maxMem;

                // Get cache stats
                CacheManager.CacheStats cacheStats = CacheManager.getInstance().getStats();

                // Get operations per minute
                long opsPerMin = AuditLog.getInstance().getRecentOperationCount(1);

                // Update UI on FX thread
                final Map<String, Object> finalStats = stats;
                final double finalCpu = Math.min(cpuLoad, 1.0);
                final double finalMem = memPercent;
                final CacheManager.CacheStats finalCacheStats = cacheStats;
                final long finalOps = opsPerMin;

                Platform.runLater(() -> {
                    // Update stat cards
                    if (finalStats != null) {
                        Object online = finalStats.get("onlinePlayers");
                        Object chars = finalStats.get("totalCharacters");
                        Object guilds = finalStats.get("totalGuilds");

                        if (online != null) onlinePlayersLabel.setText(formatNumber(online));
                        if (chars != null) totalCharsLabel.setText(formatNumber(chars));
                        if (guilds != null) totalGuildsLabel.setText(formatNumber(guilds));

                        // Update chart
                        long onlineCount = online != null ? ((Number) online).longValue() : 0;
                        updateChart(onlineCount, finalOps);
                    }

                    opsPerMinLabel.setText(String.valueOf(finalOps));

                    // Update system status
                    cpuBar.setProgress(finalCpu);
                    cpuLabel.setText(String.format("CPU: %.0f%%", finalCpu * 100));

                    memBar.setProgress(finalMem);
                    memLabel.setText(String.format("内存: %.0f%%", finalMem * 100));

                    // Update cache
                    cacheHitRateLabel.setText(String.format("%.1f%%", finalCacheStats.hitRate() * 100));
                    cacheSizeLabel.setText(String.format("%d/%d", finalCacheStats.size(), finalCacheStats.maxSize()));
                });

            } catch (Exception ex) {
                log.error("刷新状态失败", ex);
            }
        }).start();
    }

    private void updateChart(long onlineCount, long opsCount) {
        chartIndex++;

        // Add data points
        onlineSeries.getData().add(new XYChart.Data<>(chartIndex, onlineCount));
        opsSeries.getData().add(new XYChart.Data<>(chartIndex, opsCount));

        // Keep only last N points
        if (onlineSeries.getData().size() > CHART_HISTORY_SIZE) {
            onlineSeries.getData().remove(0);
            opsSeries.getData().remove(0);
        }

        // Update X axis range
        NumberAxis xAxis = (NumberAxis) onlineSeries.getChart().getXAxis();
        xAxis.setLowerBound(Math.max(0, chartIndex - CHART_HISTORY_SIZE));
        xAxis.setUpperBound(chartIndex);
    }

    private String formatNumber(Object num) {
        if (num == null) return "0";
        long value = ((Number) num).longValue();
        if (value >= 1000000) {
            return String.format("%.1fM", value / 1000000.0);
        } else if (value >= 1000) {
            return String.format("%.1fK", value / 1000.0);
        }
        return String.valueOf(value);
    }

    /**
     * Stop auto refresh
     */
    public void stop() {
        if (updateTimeline != null) {
            updateTimeline.stop();
        }
    }

    /**
     * Manual refresh
     */
    public void refresh() {
        refreshStats();
    }
}
