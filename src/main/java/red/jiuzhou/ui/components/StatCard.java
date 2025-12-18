package red.jiuzhou.ui.components;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.text.DecimalFormat;
import java.util.function.Consumer;

/**
 * 统计卡片组件
 *
 * <p>紧凑的数据展示卡片，支持：
 * <ul>
 *   <li>主标题和副标题</li>
 *   <li>大数字显示</li>
 *   <li>趋势指示器</li>
 *   <li>图标支持</li>
 *   <li>点击事件</li>
 *   <li>动画效果</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class StatCard extends VBox {

    // 颜色预设
    public static final String COLOR_PRIMARY = "#3498db";
    public static final String COLOR_SUCCESS = "#28a745";
    public static final String COLOR_WARNING = "#ffc107";
    public static final String COLOR_DANGER = "#dc3545";
    public static final String COLOR_INFO = "#17a2b8";
    public static final String COLOR_PURPLE = "#6f42c1";
    public static final String COLOR_PINK = "#e83e8c";
    public static final String COLOR_ORANGE = "#fd7e14";

    // UI组件
    private final Label iconLabel;
    private final Label titleLabel;
    private final Label valueLabel;
    private final Label subtitleLabel;
    private final Label trendLabel;
    private final HBox trendBox;

    // 配置
    private String accentColor = COLOR_PRIMARY;
    private Consumer<StatCard> onClickHandler;

    public StatCard() {
        this("", "", "0", "");
    }

    public StatCard(String icon, String title, String value, String subtitle) {
        setSpacing(6);
        setPadding(new Insets(12, 14, 12, 14));
        setAlignment(Pos.CENTER_LEFT);
        setMinWidth(140);
        setPrefWidth(160);
        setMaxWidth(200);

        // 设置基础样式
        updateStyle();

        // 图标
        iconLabel = new Label(icon);
        iconLabel.setFont(Font.font("System", FontWeight.NORMAL, 20));

        // 标题
        titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");
        titleLabel.setWrapText(true);

        // 顶部行：图标 + 标题
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().addAll(iconLabel, titleLabel);

        // 数值
        valueLabel = new Label(value);
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        valueLabel.setStyle("-fx-text-fill: " + accentColor + ";");

        // 副标题
        subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-text-fill: #adb5bd; -fx-font-size: 10px;");
        subtitleLabel.setWrapText(true);

        // 趋势指示
        trendLabel = new Label("");
        trendLabel.setStyle("-fx-font-size: 11px;");

        trendBox = new HBox(4);
        trendBox.setAlignment(Pos.CENTER_LEFT);
        trendBox.getChildren().add(trendLabel);
        trendBox.setVisible(false);

        // 组装
        getChildren().addAll(topRow, valueLabel, subtitleLabel, trendBox);

        // 鼠标悬停效果
        setOnMouseEntered(e -> onHover(true));
        setOnMouseExited(e -> onHover(false));

        // 点击效果
        setOnMouseClicked(e -> {
            if (onClickHandler != null) {
                playClickAnimation();
                onClickHandler.accept(this);
            }
        });
    }

    /**
     * 更新样式
     */
    private void updateStyle() {
        setStyle(String.format(
                "-fx-background-color: white; " +
                "-fx-background-radius: 8; " +
                "-fx-border-color: %s30; " +
                "-fx-border-width: 1; " +
                "-fx-border-radius: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 4, 0, 0, 2);",
                accentColor
        ));
    }

    /**
     * 悬停效果
     */
    private void onHover(boolean hover) {
        if (hover) {
            setStyle(String.format(
                    "-fx-background-color: white; " +
                    "-fx-background-radius: 8; " +
                    "-fx-border-color: %s; " +
                    "-fx-border-width: 2; " +
                    "-fx-border-radius: 8; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 3); " +
                    "-fx-cursor: hand;",
                    accentColor
            ));
        } else {
            updateStyle();
        }
    }

    /**
     * 点击动画
     */
    private void playClickAnimation() {
        ScaleTransition scale = new ScaleTransition(Duration.millis(100), this);
        scale.setFromX(1.0);
        scale.setFromY(1.0);
        scale.setToX(0.95);
        scale.setToY(0.95);
        scale.setAutoReverse(true);
        scale.setCycleCount(2);
        scale.play();
    }

    // ==================== Builder 方法 ====================

    /**
     * 设置图标
     */
    public StatCard icon(String icon) {
        iconLabel.setText(icon);
        return this;
    }

    /**
     * 设置标题
     */
    public StatCard title(String title) {
        titleLabel.setText(title);
        return this;
    }

    /**
     * 设置数值
     */
    public StatCard value(String value) {
        valueLabel.setText(value);
        return this;
    }

    /**
     * 设置数值（数字）
     */
    public StatCard value(int value) {
        valueLabel.setText(formatNumber(value));
        return this;
    }

    /**
     * 设置数值（带动画）
     */
    public StatCard valueAnimated(String value) {
        FadeTransition fade = new FadeTransition(Duration.millis(200), valueLabel);
        fade.setFromValue(0.5);
        fade.setToValue(1.0);
        valueLabel.setText(value);
        fade.play();
        return this;
    }

    /**
     * 设置副标题
     */
    public StatCard subtitle(String subtitle) {
        subtitleLabel.setText(subtitle);
        return this;
    }

    /**
     * 设置主题颜色
     */
    public StatCard color(String color) {
        this.accentColor = color;
        valueLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-font-size: 24px;");
        iconLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 20px;");
        updateStyle();
        return this;
    }

    /**
     * 设置趋势（上升）
     */
    public StatCard trendUp(String text) {
        trendLabel.setText("↑ " + text);
        trendLabel.setStyle("-fx-text-fill: #28a745; -fx-font-size: 11px;");
        trendBox.setVisible(true);
        return this;
    }

    /**
     * 设置趋势（下降）
     */
    public StatCard trendDown(String text) {
        trendLabel.setText("↓ " + text);
        trendLabel.setStyle("-fx-text-fill: #dc3545; -fx-font-size: 11px;");
        trendBox.setVisible(true);
        return this;
    }

    /**
     * 设置趋势（持平）
     */
    public StatCard trendFlat(String text) {
        trendLabel.setText("→ " + text);
        trendLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");
        trendBox.setVisible(true);
        return this;
    }

    /**
     * 隐藏趋势
     */
    public StatCard hideTrend() {
        trendBox.setVisible(false);
        return this;
    }

    /**
     * 设置提示
     */
    public StatCard tooltip(String text) {
        Tooltip tip = new Tooltip(text);
        tip.setStyle("-fx-font-size: 11px;");
        Tooltip.install(this, tip);
        return this;
    }

    /**
     * 设置点击处理器
     */
    public StatCard onClick(Consumer<StatCard> handler) {
        this.onClickHandler = handler;
        return this;
    }

    /**
     * 设置小尺寸
     */
    public StatCard small() {
        setMinWidth(100);
        setPrefWidth(120);
        setMaxWidth(140);
        setPadding(new Insets(8, 10, 8, 10));
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        iconLabel.setFont(Font.font("System", FontWeight.NORMAL, 16));
        return this;
    }

    /**
     * 设置大尺寸
     */
    public StatCard large() {
        setMinWidth(180);
        setPrefWidth(220);
        setMaxWidth(280);
        setPadding(new Insets(16, 20, 16, 20));
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 32));
        iconLabel.setFont(Font.font("System", FontWeight.NORMAL, 24));
        return this;
    }

    // ==================== 工具方法 ====================

    /**
     * 格式化数字
     */
    private String formatNumber(int value) {
        if (value >= 1000000) {
            return new DecimalFormat("#.#M").format(value / 1000000.0);
        } else if (value >= 1000) {
            return new DecimalFormat("#.#K").format(value / 1000.0);
        }
        return String.valueOf(value);
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建统计卡片
     */
    public static StatCard create(String icon, String title, String value) {
        return new StatCard(icon, title, value, "");
    }

    /**
     * 创建带颜色的统计卡片
     */
    public static StatCard create(String icon, String title, String value, String color) {
        return new StatCard(icon, title, value, "").color(color);
    }

    /**
     * 创建完整统计卡片
     */
    public static StatCard create(String icon, String title, String value, String subtitle, String color) {
        return new StatCard(icon, title, value, subtitle).color(color);
    }

    /**
     * 创建计数卡片
     */
    public static StatCard count(String title, int count) {
        return new StatCard("📊", title, String.valueOf(count), "")
                .color(COLOR_PRIMARY);
    }

    /**
     * 创建百分比卡片
     */
    public static StatCard percentage(String title, double percent) {
        String color;
        if (percent >= 80) {
            color = COLOR_SUCCESS;
        } else if (percent >= 50) {
            color = COLOR_WARNING;
        } else {
            color = COLOR_DANGER;
        }
        return new StatCard("📈", title, String.format("%.1f%%", percent), "")
                .color(color);
    }

    /**
     * 创建文件计数卡片
     */
    public static StatCard fileCount(String title, int count) {
        return new StatCard("📁", title, String.valueOf(count), "个文件")
                .color(COLOR_INFO);
    }

    /**
     * 创建记录计数卡片
     */
    public static StatCard recordCount(String title, int count) {
        return new StatCard("📝", title, formatLargeNumber(count), "条记录")
                .color(COLOR_PRIMARY);
    }

    /**
     * 格式化大数字
     */
    private static String formatLargeNumber(int value) {
        if (value >= 1000000) {
            return new DecimalFormat("#.#M").format(value / 1000000.0);
        } else if (value >= 1000) {
            return new DecimalFormat("#,###").format(value);
        }
        return String.valueOf(value);
    }
}
