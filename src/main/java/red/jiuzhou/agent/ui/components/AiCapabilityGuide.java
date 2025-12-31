package red.jiuzhou.agent.ui.components;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.*;

/**
 * AI能力边界说明面板
 *
 * <p>让设计师清楚了解AI的能力范围，包括：
 * <ul>
 *   <li>AI能做的事（绿色）</li>
 *   <li>AI不能做的事（红色）</li>
 *   <li>AI可能出错的场景（橙色）</li>
 *   <li>最佳实践（蓝色）</li>
 * </ul>
 *
 * <p>设计原则：
 * <ol>
 *   <li>透明 - 明确展示AI的能力边界</li>
 *   <li>引导 - 帮助设计师正确使用AI</li>
 *   <li>防错 - 提前预警可能出错的场景</li>
 * </ol>
 *
 * @author Claude
 * @version 1.0
 */
public class AiCapabilityGuide extends VBox {

    // 面板类型颜色
    private static final String COLOR_CAN_DO = "#4CAF50";       // 绿色 - 能做
    private static final String COLOR_CANNOT_DO = "#F44336";    // 红色 - 不能做
    private static final String COLOR_MAY_ERROR = "#FF9800";    // 橙色 - 可能出错
    private static final String COLOR_BEST_PRACTICE = "#2196F3"; // 蓝色 - 最佳实践

    // 四个折叠面板
    private final TitledPane canDoPane;
    private final TitledPane cannotDoPane;
    private final TitledPane mayErrorPane;
    private final TitledPane bestPracticePane;

    // 当前高亮的操作类型
    private String currentOperationType;

    public AiCapabilityGuide() {
        this.setSpacing(4);
        this.setPadding(new Insets(8));
        this.getStyleClass().add("ai-capability-guide");

        // 标题
        Label titleLabel = new Label("AI 能力说明");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // 创建四个面板
        canDoPane = createCapabilityPane(
                "AI 能做的事",
                COLOR_CAN_DO,
                getCanDoItems()
        );

        cannotDoPane = createCapabilityPane(
                "AI 不能做的事",
                COLOR_CANNOT_DO,
                getCannotDoItems()
        );

        mayErrorPane = createCapabilityPane(
                "AI 可能出错的场景",
                COLOR_MAY_ERROR,
                getMayErrorItems()
        );

        bestPracticePane = createCapabilityPane(
                "最佳实践",
                COLOR_BEST_PRACTICE,
                getBestPracticeItems()
        );

        // 默认只展开最佳实践
        canDoPane.setExpanded(false);
        cannotDoPane.setExpanded(false);
        mayErrorPane.setExpanded(false);
        bestPracticePane.setExpanded(true);

        // 使用Accordion让面板互斥展开
        Accordion accordion = new Accordion(canDoPane, cannotDoPane, mayErrorPane, bestPracticePane);
        accordion.setExpandedPane(bestPracticePane);
        VBox.setVgrow(accordion, Priority.ALWAYS);

        this.getChildren().addAll(titleLabel, accordion);
    }

    /**
     * 创建能力说明面板
     */
    private TitledPane createCapabilityPane(String title, String color, List<CapabilityItem> items) {
        VBox content = new VBox(6);
        content.setPadding(new Insets(8));

        for (CapabilityItem item : items) {
            HBox row = createCapabilityRow(item, color);
            content.getChildren().add(row);
        }

        TitledPane pane = new TitledPane(title, content);
        pane.setAnimated(true);

        // 设置标题颜色指示器
        Circle indicator = new Circle(6, Color.web(color));
        Label titleLabel = new Label(" " + title);
        HBox titleBox = new HBox(4, indicator, titleLabel);
        pane.setGraphic(titleBox);
        pane.setText("");

        return pane;
    }

    /**
     * 创建单个能力项
     */
    private HBox createCapabilityRow(CapabilityItem item, String color) {
        HBox row = new HBox(8);
        row.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        // 图标
        Label iconLabel = new Label(item.icon);
        iconLabel.setMinWidth(20);

        // 内容
        VBox contentBox = new VBox(2);

        Label titleLabel = new Label(item.title);
        titleLabel.setStyle("-fx-font-weight: bold;");
        titleLabel.setWrapText(true);

        if (item.description != null && !item.description.isEmpty()) {
            Label descLabel = new Label(item.description);
            descLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
            descLabel.setWrapText(true);
            contentBox.getChildren().addAll(titleLabel, descLabel);
        } else {
            contentBox.getChildren().add(titleLabel);
        }

        row.getChildren().addAll(iconLabel, contentBox);

        // 存储操作类型用于高亮
        row.setUserData(item.operationType);

        return row;
    }

    /**
     * 根据操作类型高亮相关说明
     *
     * @param operationType 操作类型（如 "query", "modify", "analyze"）
     */
    public void highlightForOperation(String operationType) {
        this.currentOperationType = operationType;

        // 根据操作类型决定展开哪个面板
        if (operationType == null) {
            bestPracticePane.setExpanded(true);
            return;
        }

        switch (operationType) {
            case "query", "find_similar", "find_related" -> {
                canDoPane.setExpanded(true);
            }
            case "modify", "batch_modify" -> {
                cannotDoPane.setExpanded(true);
                mayErrorPane.setExpanded(true);
            }
            case "analyze", "check_balance" -> {
                canDoPane.setExpanded(true);
            }
            case "generate_variant", "suggest_improvements" -> {
                mayErrorPane.setExpanded(true);
                bestPracticePane.setExpanded(true);
            }
            default -> bestPracticePane.setExpanded(true);
        }

        // TODO: 可以进一步实现具体项的高亮
    }

    /**
     * 重置高亮状态
     */
    public void resetHighlight() {
        this.currentOperationType = null;
        canDoPane.setExpanded(false);
        cannotDoPane.setExpanded(false);
        mayErrorPane.setExpanded(false);
        bestPracticePane.setExpanded(true);
    }

    // ==================== 能力项定义 ====================

    private List<CapabilityItem> getCanDoItems() {
        return List.of(
                new CapabilityItem(
                        "\uD83D\uDCAC", // 💬
                        "理解自然语言查询",
                        "将中文描述转换为精确的SQL查询，如「查找所有50级以上的紫装」",
                        "query"
                ),
                new CapabilityItem(
                        "\uD83D\uDCCA", // 📊
                        "分析数据结构和字段含义",
                        "理解表之间的关系、字段的业务含义",
                        "analyze"
                ),
                new CapabilityItem(
                        "\uD83D\uDD17", // 🔗
                        "检查引用完整性",
                        "发现数据之间的引用关系，检测孤立数据",
                        "check_references"
                ),
                new CapabilityItem(
                        "\u2696\uFE0F", // ⚖️
                        "生成数值平衡性建议",
                        "分析数值分布，提供平衡性调整建议",
                        "check_balance"
                ),
                new CapabilityItem(
                        "\uD83D\uDD0D", // 🔍
                        "查找相似或相关数据",
                        "根据当前选中项查找类似配置或关联数据",
                        "find_similar"
                ),
                new CapabilityItem(
                        "\uD83D\uDCDD", // 📝
                        "生成数据变体",
                        "基于现有数据生成新的配置变体（需确认后执行）",
                        "generate_variant"
                )
        );
    }

    private List<CapabilityItem> getCannotDoItems() {
        return List.of(
                new CapabilityItem(
                        "\u26D4", // ⛔
                        "自动执行修改",
                        "所有数据修改都需要您明确确认后才会执行",
                        "modify"
                ),
                new CapabilityItem(
                        "\uD83D\uDEAB", // 🚫
                        "删除或修改表结构",
                        "AI无法执行DROP TABLE、ALTER TABLE等DDL操作",
                        "ddl"
                ),
                new CapabilityItem(
                        "\u2753", // ❓
                        "理解项目特有的自定义约定",
                        "如果您的项目有特殊的命名规则或业务逻辑，需要手动补充说明",
                        "custom"
                ),
                new CapabilityItem(
                        "\uD83D\uDD12", // 🔒
                        "访问外部系统",
                        "AI只能操作当前数据库，无法访问文件系统或网络资源",
                        "external"
                ),
                new CapabilityItem(
                        "\u23F0", // ⏰
                        "执行需要长时间运行的操作",
                        "复杂查询可能会超时，建议分批处理",
                        "long_running"
                )
        );
    }

    private List<CapabilityItem> getMayErrorItems() {
        return List.of(
                new CapabilityItem(
                        "\u26A0\uFE0F", // ⚠️
                        "复杂的嵌套查询或多表关联",
                        "涉及3个以上表的复杂JOIN可能生成不正确的SQL",
                        "complex_query"
                ),
                new CapabilityItem(
                        "\uD83D\uDCA8", // 💨
                        "过于模糊的自然语言描述",
                        "如「优化一下这个数据」，请尽量具体描述需求",
                        "vague_input"
                ),
                new CapabilityItem(
                        "\uD83C\uDFAF", // 🎯
                        "需要业务知识判断的问题",
                        "如「这个技能是否平衡」，需要您提供判断标准",
                        "business_logic"
                ),
                new CapabilityItem(
                        "\uD83D\uDCC8", // 📈
                        "大规模数据的精确统计",
                        "数万条以上数据的聚合分析可能不够精确",
                        "large_data"
                ),
                new CapabilityItem(
                        "\uD83D\uDD04", // 🔄
                        "循环引用的依赖分析",
                        "存在循环依赖时可能无法正确分析影响范围",
                        "circular_ref"
                )
        );
    }

    private List<CapabilityItem> getBestPracticeItems() {
        return List.of(
                new CapabilityItem(
                        "\u2705", // ✅
                        "先用查询验证AI理解",
                        "在执行修改前，先用查询确认AI正确理解了您的意图",
                        "verify_first"
                ),
                new CapabilityItem(
                        "\uD83D\uDC41", // 👁
                        "仔细检查修改预览",
                        "修改操作前会显示对比预览，请逐条确认",
                        "check_preview"
                ),
                new CapabilityItem(
                        "\uD83E\uDDEA", // 🧪
                        "大批量修改前先小范围测试",
                        "建议先用「只处理前10条」验证修改效果",
                        "test_small"
                ),
                new CapabilityItem(
                        "\uD83D\uDCCB", // 📋
                        "提供具体的数值和条件",
                        "如「攻击力提高10%」比「适当提高攻击力」更精确",
                        "be_specific"
                ),
                new CapabilityItem(
                        "\uD83D\uDCAC", // 💬
                        "补充业务背景信息",
                        "使用补充说明功能告诉AI特殊的业务规则",
                        "add_context"
                ),
                new CapabilityItem(
                        "\u21A9\uFE0F", // ↩️
                        "善用回退功能",
                        "每个修改都可以回滚，不确定时可以大胆尝试",
                        "use_rollback"
                )
        );
    }

    // ==================== 内部类：能力项 ====================

    /**
     * 能力说明项
     */
    private record CapabilityItem(
            String icon,
            String title,
            String description,
            String operationType
    ) {}

    // ==================== 静态工厂方法 ====================

    /**
     * 创建精简版（只显示最佳实践）
     */
    public static AiCapabilityGuide createCompact() {
        AiCapabilityGuide guide = new AiCapabilityGuide();
        guide.canDoPane.setVisible(false);
        guide.canDoPane.setManaged(false);
        guide.cannotDoPane.setVisible(false);
        guide.cannotDoPane.setManaged(false);
        guide.mayErrorPane.setVisible(false);
        guide.mayErrorPane.setManaged(false);
        return guide;
    }

    /**
     * 创建针对特定操作的说明
     */
    public static VBox createForOperation(String operationType) {
        AiCapabilityGuide guide = new AiCapabilityGuide();
        guide.highlightForOperation(operationType);
        return guide;
    }
}
