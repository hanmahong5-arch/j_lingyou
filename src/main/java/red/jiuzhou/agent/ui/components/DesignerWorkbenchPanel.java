package red.jiuzhou.agent.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import red.jiuzhou.agent.context.DesignContext;
import red.jiuzhou.agent.context.FieldReference;
import red.jiuzhou.analysis.aion.AionMechanismCategory;

import java.util.*;
import java.util.function.Consumer;

/**
 * 设计师工作台面板
 *
 * <p>整合所有AI辅助功能的统一入口，让设计师在一个面板中完成
 * 数据探索、查询、修改等操作。
 *
 * <p>核心功能：
 * <ul>
 *   <li>上下文摘要卡片 - 显示当前表、机制、引用关系</li>
 *   <li>智能建议列表 - 基于上下文生成的操作建议</li>
 *   <li>快速操作按钮 - 查询/修改/分析/模板的快捷入口</li>
 *   <li>领域知识卡片 - 可展开的游戏语义映射</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class DesignerWorkbenchPanel extends VBox {

    // ==================== 颜色常量 ====================

    private static final String COLOR_PRIMARY = "#2196F3";
    private static final String COLOR_SUCCESS = "#4CAF50";
    private static final String COLOR_WARNING = "#FF9800";
    private static final String COLOR_DANGER = "#F44336";
    private static final String COLOR_INFO = "#00BCD4";

    // ==================== 内嵌组件 ====================

    private final SmartSuggestionPanel suggestionPanel;
    private final DomainKnowledgeCards knowledgeCards;

    // ==================== UI组件 ====================

    private final VBox contextSummaryCard;
    private final HBox quickActionButtons;
    private final TitledPane suggestionPane;
    private final TitledPane knowledgePane;

    // 上下文摘要组件
    private final Label tableNameLabel;
    private final Label mechanismLabel;
    private final Label referencesLabel;
    private final Label rowCountLabel;

    // ==================== 状态 ====================

    private DesignContext currentContext;

    // ==================== 回调 ====================

    private Consumer<String> onOperationSelected;
    private Consumer<SmartSuggestionPanel.Suggestion> onSuggestionExecuted;
    private Consumer<String> onCustomPromptSubmitted;
    private Consumer<String> onSqlExecuteRequested;

    // ==================== 构造器 ====================

    public DesignerWorkbenchPanel() {
        this.setSpacing(12);
        this.setPadding(new Insets(12));
        this.getStyleClass().add("designer-workbench-panel");
        this.setStyle("-fx-background-color: #F5F5F5;");

        // 初始化内嵌组件
        suggestionPanel = new SmartSuggestionPanel();
        knowledgeCards = new DomainKnowledgeCards();

        // ==================== 标题 ====================
        Label titleLabel = new Label("设计师工作台");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        HBox titleBox = new HBox(8, new Label("🎨"), titleLabel);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        // ==================== 上下文摘要卡片 ====================
        tableNameLabel = new Label("--");
        tableNameLabel.setStyle("-fx-font-weight: bold;");

        mechanismLabel = new Label("--");
        mechanismLabel.setStyle("-fx-text-fill: #666;");

        referencesLabel = new Label("--");
        referencesLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");

        rowCountLabel = new Label("");
        rowCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");

        contextSummaryCard = createContextSummaryCard();

        // ==================== 快速操作按钮 ====================
        quickActionButtons = createQuickActionButtons();

        // ==================== 智能建议面板（可折叠） ====================
        suggestionPane = new TitledPane("💡 智能建议", suggestionPanel);
        suggestionPane.setExpanded(true);
        suggestionPane.setAnimated(true);

        // ==================== 领域知识卡片（可折叠） ====================
        knowledgePane = new TitledPane("📚 领域知识", createKnowledgePreview());
        knowledgePane.setExpanded(false);
        knowledgePane.setAnimated(true);

        // ==================== 组装面板 ====================
        VBox.setVgrow(suggestionPane, Priority.ALWAYS);

        this.getChildren().addAll(
            titleBox,
            contextSummaryCard,
            quickActionButtons,
            suggestionPane,
            knowledgePane
        );

        // 设置回调
        setupCallbacks();

        // 初始状态
        showEmptyState();
    }

    // ==================== 创建UI组件 ====================

    /**
     * 创建上下文摘要卡片
     */
    private VBox createContextSummaryCard() {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                     "-fx-border-color: #E0E0E0; -fx-border-radius: 8;");

        // 标题行
        Label cardTitle = new Label("📍 当前上下文");
        cardTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        // 表名行
        HBox tableRow = new HBox(8);
        tableRow.setAlignment(Pos.CENTER_LEFT);
        Label tableIcon = new Label("📊");
        Label tableLabel = new Label("表:");
        tableLabel.setStyle("-fx-text-fill: #666;");
        tableRow.getChildren().addAll(tableIcon, tableLabel, tableNameLabel);

        // 机制行
        HBox mechanismRow = new HBox(8);
        mechanismRow.setAlignment(Pos.CENTER_LEFT);
        Label mechanismIcon = new Label("🎮");
        Label mechLabel = new Label("机制:");
        mechLabel.setStyle("-fx-text-fill: #666;");
        mechanismRow.getChildren().addAll(mechanismIcon, mechLabel, mechanismLabel);

        // 引用行
        HBox refRow = new HBox(8);
        refRow.setAlignment(Pos.CENTER_LEFT);
        Label refIcon = new Label("🔗");
        Label refLabel = new Label("引用:");
        refLabel.setStyle("-fx-text-fill: #666;");
        refRow.getChildren().addAll(refIcon, refLabel, referencesLabel);

        // 记录数行
        HBox countRow = new HBox(8);
        countRow.setAlignment(Pos.CENTER_LEFT);
        Label countIcon = new Label("📋");
        countRow.getChildren().addAll(countIcon, rowCountLabel);

        card.getChildren().addAll(cardTitle, tableRow, mechanismRow, refRow, countRow);

        return card;
    }

    /**
     * 创建快速操作按钮
     */
    private HBox createQuickActionButtons() {
        HBox buttons = new HBox(8);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(8));

        Button queryBtn = createActionButton("🔍", "查询", COLOR_PRIMARY, "query");
        Button modifyBtn = createActionButton("✏️", "修改", COLOR_WARNING, "modify");
        Button analyzeBtn = createActionButton("📊", "分析", COLOR_INFO, "analyze");
        Button templateBtn = createActionButton("📋", "模板", COLOR_SUCCESS, "template");

        buttons.getChildren().addAll(queryBtn, modifyBtn, analyzeBtn, templateBtn);

        return buttons;
    }

    /**
     * 创建操作按钮
     */
    private Button createActionButton(String icon, String text, String color, String action) {
        Button btn = new Button(icon + " " + text);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; " +
                    "-fx-font-size: 12px; -fx-padding: 8 16; -fx-background-radius: 6; " +
                    "-fx-cursor: hand;");
        btn.setPrefWidth(80);

        btn.setOnAction(e -> {
            if (onOperationSelected != null) {
                onOperationSelected.accept(action);
            }
        });

        // 悬停效果
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle() + "-fx-opacity: 0.9;"));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace("-fx-opacity: 0.9;", "")));

        return btn;
    }

    /**
     * 创建领域知识预览（简化版，点击展开完整版）
     */
    private VBox createKnowledgePreview() {
        VBox preview = new VBox(8);
        preview.setPadding(new Insets(8));

        // 快速访问常用知识
        FlowPane quickCards = new FlowPane(8, 8);

        // 品质映射快捷卡片
        VBox qualityCard = createMiniCard("⭐ 品质", "3=紫装\n4=橙装\n5=传说", () -> {
            knowledgeCards.showCategory(DomainKnowledgeCards.CardCategory.QUALITY);
            showFullKnowledgePanel();
        });

        // 元素映射快捷卡片
        VBox elementCard = createMiniCard("🔥 元素", "fire=火\nwater=水\nlight=光", () -> {
            knowledgeCards.showCategory(DomainKnowledgeCards.CardCategory.ELEMENT);
            showFullKnowledgePanel();
        });

        // 职业映射快捷卡片
        VBox classCard = createMiniCard("👤 职业", "1=战士\n2=法师\n3=牧师", () -> {
            knowledgeCards.showCategory(DomainKnowledgeCards.CardCategory.CLASS);
            showFullKnowledgePanel();
        });

        quickCards.getChildren().addAll(qualityCard, elementCard, classCard);

        // 展开完整知识库按钮
        Button expandBtn = new Button("展开完整知识库 →");
        expandBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #2196F3; " +
                          "-fx-font-size: 11px; -fx-cursor: hand;");
        expandBtn.setOnAction(e -> showFullKnowledgePanel());

        preview.getChildren().addAll(quickCards, expandBtn);

        return preview;
    }

    /**
     * 创建迷你知识卡片
     */
    private VBox createMiniCard(String title, String content, Runnable onClick) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(8));
        card.setMinWidth(100);
        card.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 6; " +
                     "-fx-border-color: #E0E0E0; -fx-border-radius: 6; -fx-cursor: hand;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        Label contentLabel = new Label(content);
        contentLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        card.getChildren().addAll(titleLabel, contentLabel);

        card.setOnMouseClicked(e -> onClick.run());
        card.setOnMouseEntered(e -> card.setStyle(card.getStyle() + "-fx-background-color: #e8f4fc;"));
        card.setOnMouseExited(e -> card.setStyle(card.getStyle().replace("-fx-background-color: #e8f4fc;", "-fx-background-color: #f8f9fa;")));

        return card;
    }

    /**
     * 设置回调
     */
    private void setupCallbacks() {
        // 建议执行回调
        suggestionPanel.setOnSuggestionExecuted(suggestion -> {
            if (onSuggestionExecuted != null) {
                onSuggestionExecuted.accept(suggestion);
            }
        });

        // 自定义提示词回调
        suggestionPanel.setOnCustomPromptSubmitted(prompt -> {
            if (onCustomPromptSubmitted != null) {
                onCustomPromptSubmitted.accept(prompt);
            }
        });

        // 知识库SQL执行回调
        knowledgeCards.setOnExecuteClicked(sql -> {
            if (onSqlExecuteRequested != null) {
                onSqlExecuteRequested.accept(sql);
            }
        });

        // 知识库复制回调
        knowledgeCards.setOnCopyClicked(content -> {
            // 可以在这里添加复制成功的提示
        });
    }

    // ==================== 公共方法 ====================

    /**
     * 更新上下文
     */
    public void updateContext(DesignContext context) {
        this.currentContext = context;

        if (context == null) {
            Platform.runLater(this::showEmptyState);
            return;
        }

        Platform.runLater(() -> {
            // 更新上下文摘要
            updateContextSummary(context);

            // 更新智能建议
            suggestionPanel.updateForContext(context);
        });
    }

    /**
     * 刷新智能建议
     */
    public void refreshSuggestions() {
        if (currentContext != null) {
            suggestionPanel.updateForContext(currentContext);
        }
    }

    /**
     * 显示特定领域知识卡片
     */
    public void showDomainCard(String category) {
        try {
            DomainKnowledgeCards.CardCategory cat = DomainKnowledgeCards.CardCategory.valueOf(category.toUpperCase());
            knowledgeCards.showCategory(cat);
            showFullKnowledgePanel();
        } catch (IllegalArgumentException e) {
            // 忽略无效的分类
        }
    }

    /**
     * 设置操作选择回调
     */
    public void setOnOperationSelected(Consumer<String> callback) {
        this.onOperationSelected = callback;
    }

    /**
     * 设置建议执行回调
     */
    public void setOnSuggestionExecuted(Consumer<SmartSuggestionPanel.Suggestion> callback) {
        this.onSuggestionExecuted = callback;
    }

    /**
     * 设置自定义提示词回调
     */
    public void setOnCustomPromptSubmitted(Consumer<String> callback) {
        this.onCustomPromptSubmitted = callback;
    }

    /**
     * 设置SQL执行回调
     */
    public void setOnSqlExecuteRequested(Consumer<String> callback) {
        this.onSqlExecuteRequested = callback;
    }

    /**
     * 获取智能建议面板
     */
    public SmartSuggestionPanel getSuggestionPanel() {
        return suggestionPanel;
    }

    /**
     * 获取领域知识卡片面板
     */
    public DomainKnowledgeCards getKnowledgeCards() {
        return knowledgeCards;
    }

    /**
     * 获取当前上下文
     */
    public DesignContext getCurrentContext() {
        return currentContext;
    }

    // ==================== 内部方法 ====================

    /**
     * 更新上下文摘要显示
     */
    private void updateContextSummary(DesignContext context) {
        // 表名
        String tableName = context.getTableName();
        if (tableName != null) {
            tableNameLabel.setText(tableName);
        } else if (context.getFocusPath() != null) {
            // 显示文件名
            String path = context.getFocusPath();
            int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            tableNameLabel.setText(lastSlash >= 0 ? path.substring(lastSlash + 1) : path);
        } else {
            tableNameLabel.setText("--");
        }

        // 机制
        AionMechanismCategory mechanism = context.getMechanism();
        if (mechanism != null) {
            mechanismLabel.setText(mechanism.getIcon() + " " + mechanism.getDisplayName());
        } else {
            mechanismLabel.setText("未识别");
        }

        // 引用关系统计
        int outCount = context.getOutgoingRefs() != null ? context.getOutgoingRefs().size() : 0;
        int inCount = context.getIncomingRefs() != null ? context.getIncomingRefs().size() : 0;
        if (outCount > 0 || inCount > 0) {
            referencesLabel.setText(String.format("引用 %d 个表，被 %d 个表引用", outCount, inCount));
        } else {
            referencesLabel.setText("无引用关系");
        }

        // 行数据
        if (context.getRowData() != null && !context.getRowData().isEmpty()) {
            rowCountLabel.setText("选中行: " + context.getFocusId());
            rowCountLabel.setVisible(true);
        } else {
            rowCountLabel.setVisible(false);
        }

        // 更新卡片样式（根据机制颜色）
        if (mechanism != null) {
            String borderColor = mechanism.getColor();
            contextSummaryCard.setStyle(contextSummaryCard.getStyle().replace(
                "-fx-border-color: #E0E0E0;",
                "-fx-border-color: " + borderColor + ";"
            ));
        }
    }

    /**
     * 显示空状态
     */
    private void showEmptyState() {
        tableNameLabel.setText("--");
        mechanismLabel.setText("--");
        referencesLabel.setText("--");
        rowCountLabel.setVisible(false);

        contextSummaryCard.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                                   "-fx-border-color: #E0E0E0; -fx-border-radius: 8;");
    }

    /**
     * 显示完整知识库面板
     */
    private void showFullKnowledgePanel() {
        // 展开知识面板
        knowledgePane.setExpanded(true);

        // 替换预览为完整知识库
        knowledgePane.setContent(knowledgeCards);

        // 添加收起按钮
        Button collapseBtn = new Button("收起");
        collapseBtn.setStyle("-fx-font-size: 11px;");
        collapseBtn.setOnAction(e -> {
            knowledgePane.setContent(createKnowledgePreview());
        });

        // 在知识库顶部添加收起按钮
        if (knowledgeCards.getChildren().size() > 0) {
            // 将收起按钮添加到标题栏
        }
    }

    /**
     * 添加操作到建议面板
     */
    public void addQuickSuggestion(String title, String description, String sql) {
        SmartSuggestionPanel.Suggestion suggestion = new SmartSuggestionPanel.Suggestion(
            title, description, sql, null, 1.0, SmartSuggestionPanel.SuggestionType.QUERY
        );
        suggestionPanel.addSuggestion(suggestion);
    }

    /**
     * 清空建议
     */
    public void clearSuggestions() {
        suggestionPanel.clearSuggestions();
    }

    /**
     * 销毁面板（释放资源）
     */
    public void dispose() {
        suggestionPanel.dispose();
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建精简版工作台（不含知识库）
     */
    public static DesignerWorkbenchPanel createCompact() {
        DesignerWorkbenchPanel panel = new DesignerWorkbenchPanel();
        panel.knowledgePane.setVisible(false);
        panel.knowledgePane.setManaged(false);
        return panel;
    }

    /**
     * 创建指定上下文的工作台
     */
    public static DesignerWorkbenchPanel createForContext(DesignContext context) {
        DesignerWorkbenchPanel panel = new DesignerWorkbenchPanel();
        panel.updateContext(context);
        return panel;
    }
}
