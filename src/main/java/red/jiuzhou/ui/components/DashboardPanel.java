package red.jiuzhou.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;

/**
 * 仪表盘面板组件
 *
 * <p>组织和展示多个统计卡片的容器：
 * <ul>
 *   <li>分组展示</li>
 *   <li>响应式布局</li>
 *   <li>紧凑/宽松模式</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class DashboardPanel extends VBox {

    private final FlowPane cardContainer;
    private final List<DashboardGroup> groups = new ArrayList<>();
    private final VBox groupsContainer;

    private boolean compactMode = false;

    public DashboardPanel() {
        setSpacing(15);
        setPadding(new Insets(15));
        setStyle("-fx-background-color: #f8f9fa;");

        // 默认卡片容器（无分组时使用）
        cardContainer = new FlowPane();
        cardContainer.setHgap(12);
        cardContainer.setVgap(12);
        cardContainer.setPadding(new Insets(5));

        // 分组容器
        groupsContainer = new VBox(20);

        getChildren().add(groupsContainer);
    }

    /**
     * 仪表盘分组
     */
    public static class DashboardGroup {
        private final String title;
        private final FlowPane cardPane;
        private final VBox container;

        public DashboardGroup(String title) {
            this.title = title;

            container = new VBox(8);

            // 标题
            Label titleLabel = new Label(title);
            titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
            titleLabel.setStyle("-fx-text-fill: #495057;");

            // 卡片容器
            cardPane = new FlowPane();
            cardPane.setHgap(12);
            cardPane.setVgap(12);

            container.getChildren().addAll(titleLabel, cardPane);
        }

        public DashboardGroup addCard(StatCard card) {
            cardPane.getChildren().add(card);
            return this;
        }

        public DashboardGroup addCards(StatCard... cards) {
            for (StatCard card : cards) {
                cardPane.getChildren().add(card);
            }
            return this;
        }

        public VBox getContainer() {
            return container;
        }

        public String getTitle() {
            return title;
        }

        public void clear() {
            cardPane.getChildren().clear();
        }
    }

    /**
     * 添加卡片（无分组）
     */
    public DashboardPanel addCard(StatCard card) {
        if (groups.isEmpty()) {
            if (!getChildren().contains(cardContainer)) {
                getChildren().add(0, cardContainer);
            }
            cardContainer.getChildren().add(card);
        }
        return this;
    }

    /**
     * 添加多个卡片
     */
    public DashboardPanel addCards(StatCard... cards) {
        for (StatCard card : cards) {
            addCard(card);
        }
        return this;
    }

    /**
     * 创建分组
     */
    public DashboardGroup createGroup(String title) {
        DashboardGroup group = new DashboardGroup(title);
        groups.add(group);
        groupsContainer.getChildren().add(group.getContainer());
        return group;
    }

    /**
     * 获取分组
     */
    public DashboardGroup getGroup(String title) {
        for (DashboardGroup group : groups) {
            if (group.getTitle().equals(title)) {
                return group;
            }
        }
        return null;
    }

    /**
     * 设置紧凑模式
     */
    public DashboardPanel setCompactMode(boolean compact) {
        this.compactMode = compact;
        int gap = compact ? 8 : 12;
        int padding = compact ? 10 : 15;

        cardContainer.setHgap(gap);
        cardContainer.setVgap(gap);
        setPadding(new Insets(padding));

        for (DashboardGroup group : groups) {
            group.cardPane.setHgap(gap);
            group.cardPane.setVgap(gap);
        }

        return this;
    }

    /**
     * 设置标题
     */
    public DashboardPanel setTitle(String title) {
        // 移除旧标题
        getChildren().removeIf(node -> node instanceof Label && "dashboard-title".equals(node.getId()));

        // 添加新标题
        Label titleLabel = new Label(title);
        titleLabel.setId("dashboard-title");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        titleLabel.setStyle("-fx-text-fill: #2c3e50;");
        titleLabel.setPadding(new Insets(0, 0, 10, 0));

        getChildren().add(0, titleLabel);
        return this;
    }

    /**
     * 清除所有内容
     */
    public void clear() {
        cardContainer.getChildren().clear();
        groupsContainer.getChildren().clear();
        groups.clear();
    }

    /**
     * 刷新布局
     */
    public void refresh() {
        layout();
    }

    /**
     * 创建带滚动的仪表盘
     */
    public static ScrollPane createScrollable(DashboardPanel panel) {
        ScrollPane scrollPane = new ScrollPane(panel);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        return scrollPane;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建概览仪表盘
     */
    public static DashboardPanel createOverviewDashboard() {
        DashboardPanel panel = new DashboardPanel();
        panel.setTitle("数据概览");
        return panel;
    }

    /**
     * 创建统计仪表盘
     */
    public static DashboardPanel createStatsDashboard(String title,
                                                       String... statTitles) {
        DashboardPanel panel = new DashboardPanel();
        panel.setTitle(title);

        for (String statTitle : statTitles) {
            panel.addCard(StatCard.create("📊", statTitle, "0")
                    .color(StatCard.COLOR_PRIMARY));
        }

        return panel;
    }

    /**
     * 创建Aion机制浏览器仪表盘
     */
    public static DashboardPanel createAionMechanismDashboard() {
        DashboardPanel panel = new DashboardPanel();
        panel.setCompactMode(true);

        // 概览分组
        DashboardGroup overview = panel.createGroup("📊 数据概览");
        overview.addCards(
                StatCard.create("🎮", "游戏机制", "27", StatCard.COLOR_PRIMARY).small(),
                StatCard.create("📁", "配置文件", "0", StatCard.COLOR_INFO).small(),
                StatCard.create("📝", "字段总数", "0", StatCard.COLOR_SUCCESS).small(),
                StatCard.create("🔗", "引用关系", "0", StatCard.COLOR_PURPLE).small()
        );

        // 文件分组
        DashboardGroup files = panel.createGroup("📂 文件分布");
        files.addCards(
                StatCard.create("🌐", "公共文件", "0", StatCard.COLOR_INFO).small(),
                StatCard.create("🇨🇳", "本地化文件", "0", StatCard.COLOR_WARNING).small()
        );

        return panel;
    }

    /**
     * 创建设计洞察仪表盘
     */
    public static DashboardPanel createDesignerInsightDashboard() {
        DashboardPanel panel = new DashboardPanel();
        panel.setCompactMode(true);

        // 数据质量分组
        DashboardGroup quality = panel.createGroup("📊 数据质量");
        quality.addCards(
                StatCard.percentage("完整度", 0),
                StatCard.percentage("一致性", 0),
                StatCard.create("⚠", "异常项", "0", StatCard.COLOR_WARNING).small()
        );

        // 统计分组
        DashboardGroup stats = panel.createGroup("📈 数据统计");
        stats.addCards(
                StatCard.recordCount("总记录", 0),
                StatCard.create("📋", "字段数", "0", StatCard.COLOR_INFO).small(),
                StatCard.create("🔢", "唯一值", "0", StatCard.COLOR_SUCCESS).small()
        );

        return panel;
    }
}
