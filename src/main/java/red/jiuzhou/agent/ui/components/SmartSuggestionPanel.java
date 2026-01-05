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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 智能建议面板
 *
 * <p>基于当前上下文，预测设计师可能想要执行的操作，提供一键执行的建议。
 *
 * <p>建议生成策略：
 * <ul>
 *   <li>基于表类型 - 不同表有不同的常用操作</li>
 *   <li>基于机制分类 - 27种Aion机制各有专属建议</li>
 *   <li>基于引用关系 - 推荐关联查询</li>
 *   <li>基于历史操作 - 学习用户常用的操作模式</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class SmartSuggestionPanel extends VBox {

    // ==================== 建议类型枚举 ====================

    public enum SuggestionType {
        QUERY("查询", "#2196F3"),      // 蓝色
        MODIFY("修改", "#FF9800"),     // 橙色
        ANALYZE("分析", "#9C27B0"),    // 紫色
        TEMPLATE("模板", "#4CAF50");   // 绿色

        private final String displayName;
        private final String color;

        SuggestionType(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }

    // ==================== 建议数据结构 ====================

    public record Suggestion(
        String title,           // 建议标题
        String description,     // 详细描述
        String sql,             // 预生成的SQL（可选）
        String prompt,          // AI提示词（用于动态生成）
        double confidence,      // 置信度 (0-1)
        SuggestionType type     // 类型
    ) {
        public String getIcon() {
            return switch (type) {
                case QUERY -> "🔍";
                case MODIFY -> "✏️";
                case ANALYZE -> "📊";
                case TEMPLATE -> "📋";
            };
        }

        public String getConfidenceDisplay() {
            return String.format("%.0f%%", confidence * 100);
        }
    }

    // ==================== UI组件 ====================

    private final VBox recommendedSection;
    private final VBox templateSection;
    private final VBox customSection;

    private final VBox recommendedList;
    private final VBox templateList;
    private final TextField customInput;
    private final Button generateButton;

    // ==================== 状态 ====================

    private DesignContext currentContext;
    private final List<Suggestion> currentSuggestions = new ArrayList<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // ==================== 回调 ====================

    private Consumer<Suggestion> onSuggestionSelected;
    private Consumer<Suggestion> onSuggestionExecuted;
    private Consumer<String> onCustomPromptSubmitted;

    // ==================== 表类型到建议的映射 ====================

    private static final Map<String, List<Suggestion>> TABLE_SUGGESTIONS = new HashMap<>();

    static {
        // 物品表建议
        TABLE_SUGGESTIONS.put("client_items", List.of(
            new Suggestion("查询所有数据", "查看表中的所有记录",
                "SELECT * FROM client_items LIMIT 100", null, 0.95, SuggestionType.QUERY),
            new Suggestion("统计品质分布", "按品质等级统计装备数量",
                "SELECT quality, COUNT(*) as count FROM client_items GROUP BY quality ORDER BY quality",
                null, 0.88, SuggestionType.ANALYZE),
            new Suggestion("查询紫装", "查询所有紫色品质装备",
                "SELECT * FROM client_items WHERE quality = 3 LIMIT 100",
                null, 0.85, SuggestionType.QUERY),
            new Suggestion("查询高等级装备", "查询50级以上的装备",
                "SELECT * FROM client_items WHERE level > 50 LIMIT 100",
                null, 0.82, SuggestionType.QUERY)
        ));

        // NPC表建议
        TABLE_SUGGESTIONS.put("client_npcs_npc", List.of(
            new Suggestion("查询所有NPC", "查看所有NPC数据",
                "SELECT * FROM client_npcs_npc LIMIT 100", null, 0.95, SuggestionType.QUERY),
            new Suggestion("按类型统计", "统计各类型NPC数量",
                "SELECT npc_type, COUNT(*) as count FROM client_npcs_npc GROUP BY npc_type",
                null, 0.88, SuggestionType.ANALYZE),
            new Suggestion("查询商人NPC", "查询所有商人类型的NPC",
                "SELECT * FROM client_npcs_npc WHERE npc_type = 'merchant' LIMIT 100",
                null, 0.80, SuggestionType.QUERY)
        ));

        // 任务表建议
        TABLE_SUGGESTIONS.put("client_quest_base", List.of(
            new Suggestion("查询所有任务", "查看所有任务数据",
                "SELECT * FROM client_quest_base LIMIT 100", null, 0.95, SuggestionType.QUERY),
            new Suggestion("任务依赖分析", "分析任务的前置和后续关系",
                null, "分析任务依赖链，找出有前置任务要求的任务", 0.85, SuggestionType.ANALYZE),
            new Suggestion("主线任务", "查询主线任务",
                "SELECT * FROM client_quest_base WHERE quest_type = 'main' LIMIT 100",
                null, 0.82, SuggestionType.QUERY)
        ));

        // 技能表建议
        TABLE_SUGGESTIONS.put("client_skill_base", List.of(
            new Suggestion("查询所有技能", "查看所有技能数据",
                "SELECT * FROM client_skill_base LIMIT 100", null, 0.95, SuggestionType.QUERY),
            new Suggestion("按职业统计", "统计各职业的技能数量",
                "SELECT class_type, COUNT(*) as count FROM client_skill_base GROUP BY class_type",
                null, 0.88, SuggestionType.ANALYZE)
        ));

        // 掉落表建议
        TABLE_SUGGESTIONS.put("client_drop_base", List.of(
            new Suggestion("查询所有掉落", "查看所有掉落配置",
                "SELECT * FROM client_drop_base LIMIT 100", null, 0.95, SuggestionType.QUERY),
            new Suggestion("高概率掉落", "查询掉落概率大于50%的配置",
                "SELECT * FROM client_drop_base WHERE drop_rate > 0.5 LIMIT 100",
                null, 0.85, SuggestionType.QUERY)
        ));
    }

    // ==================== 机制到建议的映射 ====================

    private static final Map<AionMechanismCategory, List<Suggestion>> MECHANISM_SUGGESTIONS = new HashMap<>();

    static {
        // 物品系统建议
        MECHANISM_SUGGESTIONS.put(AionMechanismCategory.ITEM, List.of(
            new Suggestion("物品使用场景", "分析物品在各系统中的使用情况",
                null, "分析该物品在任务奖励、商店出售、NPC掉落中的使用情况", 0.90, SuggestionType.ANALYZE),
            new Suggestion("物品来源追踪", "追踪物品的所有获取途径",
                null, "追踪该物品的获取来源：掉落、任务、商店、制作等", 0.88, SuggestionType.ANALYZE)
        ));

        // NPC系统建议
        MECHANISM_SUGGESTIONS.put(AionMechanismCategory.NPC, List.of(
            new Suggestion("NPC关联分析", "分析NPC的所有关联数据",
                null, "分析该NPC关联的任务、掉落、商店等信息", 0.90, SuggestionType.ANALYZE),
            new Suggestion("NPC位置分布", "查看NPC的地图分布",
                null, "分析该类型NPC在各地图的分布情况", 0.85, SuggestionType.ANALYZE)
        ));

        // 任务系统建议
        MECHANISM_SUGGESTIONS.put(AionMechanismCategory.QUEST, List.of(
            new Suggestion("任务依赖链", "分析任务的完整依赖链",
                null, "分析该任务的前置任务和后续任务链", 0.90, SuggestionType.ANALYZE),
            new Suggestion("任务奖励分析", "分析任务的奖励配置",
                null, "分析该任务的经验、金币、物品奖励", 0.88, SuggestionType.ANALYZE)
        ));

        // 技能系统建议
        MECHANISM_SUGGESTIONS.put(AionMechanismCategory.SKILL, List.of(
            new Suggestion("技能使用场景", "分析技能在各职业的使用",
                null, "分析该技能被哪些职业使用，以及学习条件", 0.90, SuggestionType.ANALYZE)
        ));

        // 掉落系统建议
        MECHANISM_SUGGESTIONS.put(AionMechanismCategory.DROP, List.of(
            new Suggestion("掉落物品分析", "分析掉落表的物品配置",
                null, "分析该掉落表包含的所有物品及其概率", 0.90, SuggestionType.ANALYZE)
        ));
    }

    // ==================== 构造器 ====================

    public SmartSuggestionPanel() {
        this.setSpacing(12);
        this.setPadding(new Insets(12));
        this.getStyleClass().add("smart-suggestion-panel");
        this.setStyle("-fx-background-color: #FAFAFA;");

        // 标题
        Label titleLabel = new Label("智能建议");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        HBox titleBox = new HBox(8, new Label("💡"), titleLabel);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        // ==================== 推荐操作区域 ====================
        Label recommendedLabel = new Label("🔥 推荐操作");
        recommendedLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        recommendedList = new VBox(6);
        recommendedList.setPadding(new Insets(8, 0, 0, 0));

        recommendedSection = new VBox(4, recommendedLabel, recommendedList);

        // ==================== 常用模板区域 ====================
        Label templateLabel = new Label("📚 常用模板");
        templateLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        templateList = new VBox(4);
        templateList.setPadding(new Insets(8, 0, 0, 0));

        // 添加默认模板链接
        addTemplateLink("物品使用场景分析", "分析物品 {id} 在游戏各系统中的使用情况");
        addTemplateLink("掉落关系查询", "查询该物品的所有掉落来源");
        addTemplateLink("任务依赖分析", "分析任务的完整依赖链");
        addTemplateLink("NPC完整信息", "查询NPC的所有关联数据");

        templateSection = new VBox(4, templateLabel, templateList);

        // ==================== 自定义区域 ====================
        Label customLabel = new Label("💬 自定义");
        customLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        customInput = new TextField();
        customInput.setPromptText("输入您的需求...");
        customInput.setPrefWidth(200);
        customInput.setOnAction(e -> submitCustomPrompt());

        generateButton = new Button("生成");
        generateButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        generateButton.setOnAction(e -> submitCustomPrompt());

        HBox customInputRow = new HBox(8, customInput, generateButton);
        HBox.setHgrow(customInput, Priority.ALWAYS);
        customInputRow.setAlignment(Pos.CENTER_LEFT);

        customSection = new VBox(8, customLabel, customInputRow);

        // ==================== 组装面板 ====================
        VBox content = new VBox(16, recommendedSection, templateSection, customSection);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        this.getChildren().addAll(titleBox, scrollPane);

        // 初始状态
        showEmptyState();
    }

    // ==================== 公共方法 ====================

    /**
     * 根据上下文更新建议
     */
    public void updateForContext(DesignContext context) {
        this.currentContext = context;

        if (context == null) {
            Platform.runLater(this::showEmptyState);
            return;
        }

        executor.submit(() -> {
            List<Suggestion> suggestions = generateSuggestions(context);
            Platform.runLater(() -> displaySuggestions(suggestions));
        });
    }

    /**
     * 添加自定义建议
     */
    public void addSuggestion(Suggestion suggestion) {
        currentSuggestions.add(suggestion);
        Platform.runLater(() -> addSuggestionRow(suggestion));
    }

    /**
     * 清空建议
     */
    public void clearSuggestions() {
        currentSuggestions.clear();
        Platform.runLater(() -> {
            recommendedList.getChildren().clear();
            showEmptyState();
        });
    }

    /**
     * 设置建议选择回调
     */
    public void setOnSuggestionSelected(Consumer<Suggestion> callback) {
        this.onSuggestionSelected = callback;
    }

    /**
     * 设置建议执行回调
     */
    public void setOnSuggestionExecuted(Consumer<Suggestion> callback) {
        this.onSuggestionExecuted = callback;
    }

    /**
     * 设置自定义提示词提交回调
     */
    public void setOnCustomPromptSubmitted(Consumer<String> callback) {
        this.onCustomPromptSubmitted = callback;
    }

    /**
     * 获取当前建议列表
     */
    public List<Suggestion> getCurrentSuggestions() {
        return new ArrayList<>(currentSuggestions);
    }

    // ==================== 内部方法 ====================

    /**
     * 生成建议列表
     */
    private List<Suggestion> generateSuggestions(DesignContext context) {
        List<Suggestion> suggestions = new ArrayList<>();

        String tableName = context.getTableName();
        AionMechanismCategory mechanism = context.getMechanism();

        // 1. 基于表类型的建议
        if (tableName != null) {
            List<Suggestion> tableSuggestions = TABLE_SUGGESTIONS.get(tableName);
            if (tableSuggestions != null) {
                suggestions.addAll(tableSuggestions);
            } else {
                // 通用表建议
                suggestions.add(new Suggestion("查询所有数据", "查看表中的所有记录",
                    "SELECT * FROM " + tableName + " LIMIT 100", null, 0.95, SuggestionType.QUERY));
                suggestions.add(new Suggestion("统计记录数", "统计表中的记录总数",
                    "SELECT COUNT(*) as total FROM " + tableName, null, 0.90, SuggestionType.ANALYZE));
            }
        }

        // 2. 基于机制分类的建议
        if (mechanism != null) {
            List<Suggestion> mechanismSuggestions = MECHANISM_SUGGESTIONS.get(mechanism);
            if (mechanismSuggestions != null) {
                suggestions.addAll(mechanismSuggestions);
            }
        }

        // 3. 基于引用关系的建议
        List<FieldReference> outgoingRefs = context.getOutgoingRefs();
        if (outgoingRefs != null && !outgoingRefs.isEmpty()) {
            for (FieldReference ref : outgoingRefs) {
                suggestions.add(new Suggestion(
                    "查看关联的" + ref.getTargetTable(),
                    "通过 " + ref.getSourceField() + " 查看关联数据",
                    null,
                    "查询 " + tableName + " 中 " + ref.getSourceField() + " 关联的 " + ref.getTargetTable() + " 数据",
                    0.75,
                    SuggestionType.QUERY
                ));
            }
        }

        // 4. 去重并按置信度排序
        suggestions = suggestions.stream()
            .distinct()
            .sorted((a, b) -> Double.compare(b.confidence(), a.confidence()))
            .limit(8)
            .toList();

        return suggestions;
    }

    /**
     * 显示建议列表
     */
    private void displaySuggestions(List<Suggestion> suggestions) {
        recommendedList.getChildren().clear();
        currentSuggestions.clear();
        currentSuggestions.addAll(suggestions);

        if (suggestions.isEmpty()) {
            showEmptyState();
            return;
        }

        for (Suggestion suggestion : suggestions) {
            addSuggestionRow(suggestion);
        }
    }

    /**
     * 添加建议行
     */
    private void addSuggestionRow(Suggestion suggestion) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));
        row.setStyle("-fx-background-color: white; -fx-background-radius: 6; " +
                     "-fx-border-color: #E0E0E0; -fx-border-radius: 6; -fx-cursor: hand;");

        // 类型颜色指示器
        Circle typeIndicator = new Circle(5, Color.web(suggestion.type().getColor()));

        // 图标
        Label iconLabel = new Label(suggestion.getIcon());

        // 标题和描述
        VBox textBox = new VBox(2);
        Label titleLabel = new Label(suggestion.title());
        titleLabel.setStyle("-fx-font-weight: bold;");

        Label descLabel = new Label(suggestion.description());
        descLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        descLabel.setWrapText(true);

        textBox.getChildren().addAll(titleLabel, descLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        // 置信度标签
        Label confidenceLabel = new Label(suggestion.getConfidenceDisplay());
        confidenceLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #999; -fx-background-color: #f5f5f5; " +
                                "-fx-padding: 2 6; -fx-background-radius: 10;");

        // 执行按钮
        Button executeBtn = new Button("▶");
        executeBtn.setStyle("-fx-background-color: " + suggestion.type().getColor() + "; -fx-text-fill: white; " +
                           "-fx-font-size: 10px; -fx-padding: 4 8; -fx-background-radius: 4;");
        executeBtn.setOnAction(e -> executeSuggestion(suggestion));

        row.getChildren().addAll(typeIndicator, iconLabel, textBox, confidenceLabel, executeBtn);

        // 点击选择
        row.setOnMouseClicked(e -> {
            if (onSuggestionSelected != null) {
                onSuggestionSelected.accept(suggestion);
            }
        });

        // 悬停效果
        row.setOnMouseEntered(e -> row.setStyle(row.getStyle() + "-fx-background-color: #f5f5f5;"));
        row.setOnMouseExited(e -> row.setStyle(row.getStyle().replace("-fx-background-color: #f5f5f5;", "-fx-background-color: white;")));

        recommendedList.getChildren().add(row);
    }

    /**
     * 添加模板链接
     */
    private void addTemplateLink(String name, String prompt) {
        Hyperlink link = new Hyperlink("• " + name);
        link.setStyle("-fx-font-size: 12px;");
        link.setOnAction(e -> {
            Suggestion templateSuggestion = new Suggestion(
                name, prompt, null, prompt, 1.0, SuggestionType.TEMPLATE
            );
            executeSuggestion(templateSuggestion);
        });
        templateList.getChildren().add(link);
    }

    /**
     * 执行建议
     */
    private void executeSuggestion(Suggestion suggestion) {
        if (onSuggestionExecuted != null) {
            onSuggestionExecuted.accept(suggestion);
        }
    }

    /**
     * 提交自定义提示词
     */
    private void submitCustomPrompt() {
        String prompt = customInput.getText().trim();
        if (prompt.isEmpty()) {
            return;
        }

        if (onCustomPromptSubmitted != null) {
            onCustomPromptSubmitted.accept(prompt);
        }

        customInput.clear();
    }

    /**
     * 显示空状态
     */
    private void showEmptyState() {
        recommendedList.getChildren().clear();
        Label emptyLabel = new Label("选择一个表或文件以获取智能建议");
        emptyLabel.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
        recommendedList.getChildren().add(emptyLabel);
    }

    /**
     * 销毁面板
     */
    public void dispose() {
        executor.shutdown();
    }
}
