package red.jiuzhou.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * 快捷操作面板
 *
 * 提供常用操作的快捷按钮，支持：
 * - 数据操作（导入/导出/DDL）
 * - 查询分析（查看数据/统计/追踪）
 * - AI 辅助（智能查询/分析/生成）
 * - 高级工具（批量修改/迁移/备份）
 *
 * @author Claude
 * @version 1.0
 */
public class QuickActionPanel extends VBox {

    private static final Logger log = LoggerFactory.getLogger(QuickActionPanel.class);

    // 操作分类
    public enum ActionCategory {
        DATA("📥 数据操作", "#4CAF50"),
        QUERY("🔍 查询分析", "#2196F3"),
        AI("🤖 AI 辅助", "#9C27B0"),
        TOOLS("🛠️ 高级工具", "#FF9800");

        private final String title;
        private final String color;

        ActionCategory(String title, String color) {
            this.title = title;
            this.color = color;
        }

        public String getTitle() { return title; }
        public String getColor() { return color; }
    }

    // 操作定义
    public record QuickAction(
        String id,
        String icon,
        String label,
        String tooltip,
        ActionCategory category,
        boolean requiresSelection  // 是否需要选中文件
    ) {}

    // 当前状态
    private String currentTableName;
    private String currentFilePath;

    // 操作回调
    private BiConsumer<String, Map<String, Object>> onAction;

    // 按钮映射（用于启用/禁用）
    private final Map<String, Button> actionButtons = new HashMap<>();

    // 预设操作
    private final List<QuickAction> dataActions = List.of(
        new QuickAction("import", "📥", "导入", "从XML导入到数据库", ActionCategory.DATA, true),
        new QuickAction("export", "📤", "导出", "从数据库导出到XML", ActionCategory.DATA, true),
        new QuickAction("generate_ddl", "💾", "DDL", "生成建表语句", ActionCategory.DATA, true),
        new QuickAction("sync", "🔄", "同步", "同步文件与数据库", ActionCategory.DATA, true)
    );

    private final List<QuickAction> queryActions = List.of(
        new QuickAction("view_data", "👁️", "查看", "查看表数据", ActionCategory.QUERY, true),
        new QuickAction("field_stats", "📊", "统计", "字段统计分析", ActionCategory.QUERY, true),
        new QuickAction("trace_refs", "🔗", "追踪", "引用关系追踪", ActionCategory.QUERY, true),
        new QuickAction("compare", "⚖️", "对比", "版本对比", ActionCategory.QUERY, true)
    );

    private final List<QuickAction> aiActions = List.of(
        new QuickAction("ai_query", "🔍", "智能查询", "自然语言查询", ActionCategory.AI, false),
        new QuickAction("ai_analyze", "📈", "数据分析", "AI数据分析", ActionCategory.AI, true),
        new QuickAction("ai_generate", "✨", "生成配置", "AI生成配置", ActionCategory.AI, false),
        new QuickAction("ai_diagnose", "🩺", "问题诊断", "AI问题诊断", ActionCategory.AI, true)
    );

    private final List<QuickAction> toolActions = List.of(
        new QuickAction("batch_modify", "✏️", "批量修改", "批量修改数据", ActionCategory.TOOLS, true),
        new QuickAction("migrate", "🚚", "数据迁移", "表数据迁移", ActionCategory.TOOLS, true),
        new QuickAction("backup", "💾", "备份", "备份数据", ActionCategory.TOOLS, true),
        new QuickAction("cleanup", "🧹", "清理", "清理冗余数据", ActionCategory.TOOLS, true)
    );

    public QuickActionPanel() {
        initializeUI();
    }

    private void initializeUI() {
        setSpacing(12);
        setPadding(new Insets(12));
        setStyle("-fx-background-color: #fafafa; -fx-border-color: #e8e8e8; -fx-border-width: 1 0 0 0;");

        // 标题
        Label titleLabel = new Label("⚡ 快捷操作");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        titleLabel.setTextFill(Color.web("#333333"));

        // 创建各分类的操作区
        VBox dataBox = createActionSection(ActionCategory.DATA, dataActions);
        VBox queryBox = createActionSection(ActionCategory.QUERY, queryActions);
        VBox aiBox = createActionSection(ActionCategory.AI, aiActions);
        VBox toolBox = createActionSection(ActionCategory.TOOLS, toolActions);

        // 组装
        getChildren().addAll(
            titleLabel,
            new Separator(),
            dataBox,
            queryBox,
            aiBox,
            toolBox
        );

        // 初始状态：禁用需要选中文件的按钮
        updateButtonStates();
    }

    /**
     * 创建操作分类区
     */
    private VBox createActionSection(ActionCategory category, List<QuickAction> actions) {
        VBox section = new VBox(6);
        section.setPadding(new Insets(4, 0, 8, 0));

        // 分类标题
        Label titleLabel = new Label(category.getTitle());
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 11));
        titleLabel.setTextFill(Color.web("#555555"));

        // 按钮行
        FlowPane buttonPane = new FlowPane(6, 6);
        buttonPane.setAlignment(Pos.CENTER_LEFT);

        for (QuickAction action : actions) {
            Button btn = createActionButton(action, category.getColor());
            actionButtons.put(action.id(), btn);
            buttonPane.getChildren().add(btn);
        }

        section.getChildren().addAll(titleLabel, buttonPane);
        return section;
    }

    /**
     * 创建操作按钮
     */
    private Button createActionButton(QuickAction action, String accentColor) {
        Button btn = new Button(action.icon() + " " + action.label());

        String baseStyle = String.format(
            "-fx-background-color: white;" +
            "-fx-border-color: %s;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;" +
            "-fx-text-fill: #333333;" +
            "-fx-font-size: 11px;" +
            "-fx-padding: 4 8;" +
            "-fx-cursor: hand;",
            accentColor
        );

        String hoverStyle = String.format(
            "-fx-background-color: %s;" +
            "-fx-border-color: %s;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 4;" +
            "-fx-background-radius: 4;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 11px;" +
            "-fx-padding: 4 8;" +
            "-fx-cursor: hand;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 4, 0, 0, 1);",
            accentColor, accentColor
        );

        btn.setStyle(baseStyle);
        btn.setOnMouseEntered(e -> { if (!btn.isDisabled()) btn.setStyle(hoverStyle); });
        btn.setOnMouseExited(e -> { if (!btn.isDisabled()) btn.setStyle(baseStyle); });

        btn.setTooltip(new Tooltip(action.tooltip()));

        btn.setOnAction(e -> handleAction(action));

        return btn;
    }

    /**
     * 处理操作
     */
    private void handleAction(QuickAction action) {
        if (action.requiresSelection() && (currentTableName == null || currentTableName.isEmpty())) {
            showAlert("提示", "请先在左侧选择一个文件");
            return;
        }

        log.info("执行操作: {} ({})", action.label(), action.id());

        if (onAction != null) {
            Map<String, Object> context = new HashMap<>();
            context.put("tableName", currentTableName);
            context.put("filePath", currentFilePath);
            context.put("actionId", action.id());
            context.put("category", action.category().name());

            onAction.accept(action.id(), context);
        } else {
            showAlert("提示", "操作 \"" + action.label() + "\" 尚未配置处理器");
        }
    }

    /**
     * 更新按钮状态
     */
    private void updateButtonStates() {
        boolean hasSelection = currentTableName != null && !currentTableName.isEmpty();

        // 遍历所有操作，根据 requiresSelection 更新状态
        updateActionsState(dataActions, hasSelection);
        updateActionsState(queryActions, hasSelection);
        updateActionsState(aiActions, hasSelection);
        updateActionsState(toolActions, hasSelection);
    }

    private void updateActionsState(List<QuickAction> actions, boolean hasSelection) {
        for (QuickAction action : actions) {
            Button btn = actionButtons.get(action.id());
            if (btn != null) {
                boolean enabled = !action.requiresSelection() || hasSelection;
                btn.setDisable(!enabled);

                if (!enabled) {
                    btn.setStyle(
                        "-fx-background-color: #f5f5f5;" +
                        "-fx-border-color: #cccccc;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-text-fill: #999999;" +
                        "-fx-font-size: 11px;" +
                        "-fx-padding: 4 8;"
                    );
                }
            }
        }
    }

    /**
     * 显示提示
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ==================== 公共方法 ====================

    /**
     * 更新当前选中的文件
     */
    public void updateSelection(String tableName, String filePath) {
        this.currentTableName = tableName;
        this.currentFilePath = filePath;
        updateButtonStates();
    }

    /**
     * 设置操作回调
     * @param handler BiConsumer<actionId, context>
     */
    public void setOnAction(BiConsumer<String, Map<String, Object>> handler) {
        this.onAction = handler;
    }

    /**
     * 获取指定操作的按钮（用于外部自定义）
     */
    public Optional<Button> getActionButton(String actionId) {
        return Optional.ofNullable(actionButtons.get(actionId));
    }

    /**
     * 添加自定义操作
     */
    public void addCustomAction(QuickAction action) {
        // 找到对应分类的区域并添加按钮
        for (var node : getChildren()) {
            if (node instanceof VBox section) {
                for (var child : section.getChildren()) {
                    if (child instanceof Label label && label.getText().equals(action.category().getTitle())) {
                        // 找到分类，添加按钮
                        for (var sectionChild : section.getChildren()) {
                            if (sectionChild instanceof FlowPane pane) {
                                Button btn = createActionButton(action, action.category().getColor());
                                actionButtons.put(action.id(), btn);
                                pane.getChildren().add(btn);
                                break;
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * 获取所有操作ID
     */
    public Set<String> getAllActionIds() {
        return actionButtons.keySet();
    }
}
