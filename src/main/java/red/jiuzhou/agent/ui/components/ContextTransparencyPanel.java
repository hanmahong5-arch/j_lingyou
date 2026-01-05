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
import red.jiuzhou.agent.context.ColumnMetadata;
import red.jiuzhou.agent.context.TableMetadata;

import java.util.*;
import java.util.function.Consumer;

/**
 * 上下文透明化面板
 *
 * <p>让设计师清楚看到AI正在使用的所有上下文信息，包括：
 * <ul>
 *   <li>当前位置（表、行、字段）</li>
 *   <li>表结构信息（字段列表、类型）</li>
 *   <li>引用关系（入度、出度）</li>
 *   <li>AI的理解（意图、条件）</li>
 *   <li>补充说明（用户输入）</li>
 * </ul>
 *
 * <p>设计原则：
 * <ol>
 *   <li>透明 - 展示AI使用的所有信息</li>
 *   <li>可编辑 - 允许用户补充语义提示</li>
 *   <li>实时 - 随用户操作自动更新</li>
 * </ol>
 *
 * @author Claude
 * @version 1.0
 */
public class ContextTransparencyPanel extends VBox {

    // 颜色常量
    private static final String COLOR_LOCATION = "#2196F3";    // 蓝色 - 位置
    private static final String COLOR_SCHEMA = "#4CAF50";      // 绿色 - 结构
    private static final String COLOR_REFERENCE = "#9C27B0";   // 紫色 - 引用
    private static final String COLOR_AI = "#FF9800";          // 橙色 - AI理解
    private static final String COLOR_SUPPLEMENT = "#00BCD4";  // 青色 - 补充

    // UI组件
    private final VBox locationSection;
    private final VBox schemaSection;
    private final VBox referenceSection;
    private final VBox aiUnderstandingSection;
    private final VBox supplementSection;

    // 位置信息组件
    private final Label locationTypeLabel;
    private final Label locationPathLabel;
    private final Label locationIdLabel;

    // 表结构组件
    private final Label tableNameLabel;
    private final VBox columnsContainer;

    // 引用关系组件
    private final VBox outgoingRefsContainer;
    private final VBox incomingRefsContainer;

    // AI理解组件
    private final Label intentLabel;
    private final VBox conditionsContainer;
    private final Label sqlPreviewLabel;

    // 补充说明组件
    private final TextField supplementInput;
    private final VBox supplementsContainer;

    // 当前上下文
    private DesignContext currentContext;

    // 回调
    private Consumer<String> onSupplementAdded;

    // 已添加的补充说明
    private final List<String> supplements = new ArrayList<>();

    // 高亮的字段
    private String highlightedField;

    public ContextTransparencyPanel() {
        this.setSpacing(8);
        this.setPadding(new Insets(8));
        this.getStyleClass().add("context-transparency-panel");
        this.setStyle("-fx-background-color: #FAFAFA;");

        // 标题
        Label titleLabel = new Label("当前上下文");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // ==================== 位置信息区域 ====================
        locationTypeLabel = new Label("--");
        locationPathLabel = new Label("--");
        locationPathLabel.setWrapText(true);
        locationIdLabel = new Label("");
        locationIdLabel.setStyle("-fx-text-fill: #666;");

        locationSection = createSection("位置", COLOR_LOCATION,
            createLabelRow("类型:", locationTypeLabel),
            createLabelRow("路径:", locationPathLabel),
            locationIdLabel
        );

        // ==================== 表结构区域 ====================
        tableNameLabel = new Label("--");
        tableNameLabel.setStyle("-fx-font-weight: bold;");
        columnsContainer = new VBox(2);
        columnsContainer.setPadding(new Insets(4, 0, 0, 16));

        schemaSection = createSection("表结构", COLOR_SCHEMA,
            createLabelRow("表名:", tableNameLabel),
            new Label("字段:"),
            columnsContainer
        );

        // ==================== 引用关系区域 ====================
        outgoingRefsContainer = new VBox(2);
        incomingRefsContainer = new VBox(2);

        VBox refsContent = new VBox(6);
        refsContent.getChildren().addAll(
            new Label("引用了 →"),
            outgoingRefsContainer,
            new Label("被引用 ←"),
            incomingRefsContainer
        );

        referenceSection = createSection("引用关系", COLOR_REFERENCE, refsContent);

        // ==================== AI理解区域 ====================
        intentLabel = new Label("--");
        intentLabel.setWrapText(true);
        intentLabel.setStyle("-fx-font-style: italic;");

        conditionsContainer = new VBox(2);
        conditionsContainer.setPadding(new Insets(4, 0, 0, 16));

        sqlPreviewLabel = new Label("");
        sqlPreviewLabel.setWrapText(true);
        sqlPreviewLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-text-fill: #666;");

        aiUnderstandingSection = createSection("AI 理解", COLOR_AI,
            createLabelRow("意图:", intentLabel),
            new Label("条件:"),
            conditionsContainer,
            sqlPreviewLabel
        );
        aiUnderstandingSection.setVisible(false);
        aiUnderstandingSection.setManaged(false);

        // ==================== 补充说明区域 ====================
        supplementInput = new TextField();
        supplementInput.setPromptText("添加补充说明...");
        supplementInput.setOnAction(e -> addSupplement());

        Button addButton = new Button("+");
        addButton.setStyle("-fx-background-color: " + COLOR_SUPPLEMENT + "; -fx-text-fill: white;");
        addButton.setOnAction(e -> addSupplement());

        HBox inputRow = new HBox(4, supplementInput, addButton);
        HBox.setHgrow(supplementInput, Priority.ALWAYS);

        supplementsContainer = new VBox(4);

        supplementSection = createSection("补充说明", COLOR_SUPPLEMENT,
            inputRow,
            supplementsContainer
        );

        // ==================== 组装面板 ====================
        ScrollPane scrollPane = new ScrollPane();
        VBox content = new VBox(8);
        content.getChildren().addAll(
            locationSection,
            schemaSection,
            referenceSection,
            aiUnderstandingSection,
            supplementSection
        );
        scrollPane.setContent(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        this.getChildren().addAll(titleLabel, scrollPane);
    }

    /**
     * 创建带标题和颜色指示器的区域
     */
    private VBox createSection(String title, String color, Region... children) {
        VBox section = new VBox(4);
        section.setPadding(new Insets(8));
        section.setStyle("-fx-background-color: white; -fx-background-radius: 4; " +
                        "-fx-border-color: #E0E0E0; -fx-border-radius: 4;");

        // 标题行
        Circle indicator = new Circle(5, Color.web(color));
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        HBox titleRow = new HBox(6, indicator, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        section.getChildren().add(titleRow);
        section.getChildren().addAll(children);

        return section;
    }

    /**
     * 创建标签行
     */
    private HBox createLabelRow(String label, Label valueLabel) {
        Label keyLabel = new Label(label);
        keyLabel.setMinWidth(40);
        keyLabel.setStyle("-fx-text-fill: #666;");
        HBox row = new HBox(4, keyLabel, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * 更新上下文显示
     */
    public void updateContext(DesignContext context) {
        this.currentContext = context;
        Platform.runLater(() -> {
            if (context == null) {
                clearDisplay();
                return;
            }

            // 更新位置信息
            updateLocationSection(context);

            // 更新表结构
            updateSchemaSection(context);

            // 更新引用关系
            updateReferenceSection(context);

            // 清空AI理解（等待新的理解结果）
            clearAiUnderstanding();
        });
    }

    /**
     * 更新位置信息区域
     */
    private void updateLocationSection(DesignContext context) {
        String locationType = context.getLocation() != null ?
            getLocationTypeDisplay(context.getLocation()) : "--";
        locationTypeLabel.setText(locationType);

        String path = context.getFocusPath();
        if (path != null) {
            // 只显示文件名或表名
            int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            String displayPath = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
            locationPathLabel.setText(displayPath);
            locationPathLabel.setTooltip(new Tooltip(path));
        } else {
            locationPathLabel.setText("--");
        }

        String id = context.getFocusId();
        if (id != null && !id.isEmpty()) {
            locationIdLabel.setText("ID: " + id);
            locationIdLabel.setVisible(true);
            locationIdLabel.setManaged(true);
        } else {
            locationIdLabel.setVisible(false);
            locationIdLabel.setManaged(false);
        }
    }

    /**
     * 更新表结构区域
     */
    private void updateSchemaSection(DesignContext context) {
        columnsContainer.getChildren().clear();

        String tableName = context.getTableName();
        if (tableName != null) {
            tableNameLabel.setText(tableName);
        } else {
            tableNameLabel.setText("--");
        }

        TableMetadata schema = context.getTableSchema();
        if (schema != null && schema.getColumns() != null) {
            for (ColumnMetadata col : schema.getColumns()) {
                HBox colRow = createColumnRow(col);
                columnsContainer.getChildren().add(colRow);
            }
            schemaSection.setVisible(true);
            schemaSection.setManaged(true);
        } else {
            schemaSection.setVisible(tableName != null);
            schemaSection.setManaged(tableName != null);
        }
    }

    /**
     * 创建字段行
     */
    private HBox createColumnRow(ColumnMetadata col) {
        Label nameLabel = new Label(col.getName());
        nameLabel.setMinWidth(80);

        // 如果是高亮字段，添加背景色
        if (col.getName().equals(highlightedField)) {
            nameLabel.setStyle("-fx-background-color: #FFEB3B; -fx-font-weight: bold;");
        }

        Label typeLabel = new Label("(" + col.getType() + ")");
        typeLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 10px;");

        HBox row = new HBox(4, nameLabel, typeLabel);
        row.setAlignment(Pos.CENTER_LEFT);

        // 添加注释提示（HBox没有setTooltip，使用Tooltip.install）
        if (col.getComment() != null && !col.getComment().isEmpty()) {
            Tooltip.install(row, new Tooltip(col.getComment()));
        }

        return row;
    }

    /**
     * 更新引用关系区域
     */
    private void updateReferenceSection(DesignContext context) {
        outgoingRefsContainer.getChildren().clear();
        incomingRefsContainer.getChildren().clear();

        List<FieldReference> outgoing = context.getOutgoingRefs();
        List<FieldReference> incoming = context.getIncomingRefs();

        boolean hasRefs = (outgoing != null && !outgoing.isEmpty()) ||
                          (incoming != null && !incoming.isEmpty());

        if (outgoing != null) {
            for (FieldReference ref : outgoing) {
                Label refLabel = new Label("  " + ref.getSourceField() + " → " +
                                           ref.getTargetTable() + "." + ref.getTargetField());
                refLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
                outgoingRefsContainer.getChildren().add(refLabel);
            }
        }
        if (outgoingRefsContainer.getChildren().isEmpty()) {
            outgoingRefsContainer.getChildren().add(new Label("  (无)"));
        }

        if (incoming != null) {
            for (FieldReference ref : incoming) {
                Label refLabel = new Label("  " + ref.getSourceTable() + "." +
                                           ref.getSourceField() + " → " + ref.getTargetField());
                refLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
                incomingRefsContainer.getChildren().add(refLabel);
            }
        }
        if (incomingRefsContainer.getChildren().isEmpty()) {
            incomingRefsContainer.getChildren().add(new Label("  (无)"));
        }

        referenceSection.setVisible(hasRefs);
        referenceSection.setManaged(hasRefs);
    }

    /**
     * 显示AI理解结果
     */
    public void showAiUnderstanding(String intent, List<String> conditions, String sqlPreview) {
        Platform.runLater(() -> {
            intentLabel.setText(intent != null ? intent : "--");

            conditionsContainer.getChildren().clear();
            if (conditions != null && !conditions.isEmpty()) {
                for (String condition : conditions) {
                    Label condLabel = new Label("• " + condition);
                    condLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
                    conditionsContainer.getChildren().add(condLabel);
                }
            } else {
                conditionsContainer.getChildren().add(new Label("• (无具体条件)"));
            }

            if (sqlPreview != null && !sqlPreview.isEmpty()) {
                sqlPreviewLabel.setText("SQL: " + sqlPreview);
                sqlPreviewLabel.setVisible(true);
                sqlPreviewLabel.setManaged(true);
            } else {
                sqlPreviewLabel.setVisible(false);
                sqlPreviewLabel.setManaged(false);
            }

            aiUnderstandingSection.setVisible(true);
            aiUnderstandingSection.setManaged(true);
        });
    }

    /**
     * 清空AI理解显示
     */
    private void clearAiUnderstanding() {
        intentLabel.setText("--");
        conditionsContainer.getChildren().clear();
        sqlPreviewLabel.setText("");
        aiUnderstandingSection.setVisible(false);
        aiUnderstandingSection.setManaged(false);
    }

    /**
     * 添加补充说明
     */
    private void addSupplement() {
        String text = supplementInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        supplements.add(text);
        supplementInput.clear();

        // 添加到显示列表
        HBox supplementRow = createSupplementRow(text);
        supplementsContainer.getChildren().add(supplementRow);

        // 触发回调
        if (onSupplementAdded != null) {
            onSupplementAdded.accept(text);
        }
    }

    /**
     * 创建补充说明行
     */
    private HBox createSupplementRow(String text) {
        Label textLabel = new Label("• " + text);
        textLabel.setWrapText(true);
        textLabel.setStyle("-fx-text-fill: #333;");
        HBox.setHgrow(textLabel, Priority.ALWAYS);

        Button removeBtn = new Button("×");
        removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #999; " +
                          "-fx-font-size: 10px; -fx-padding: 0 4;");
        removeBtn.setOnAction(e -> {
            supplements.remove(text);
            supplementsContainer.getChildren().remove(removeBtn.getParent());
        });

        HBox row = new HBox(4, textLabel, removeBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * 高亮指定字段
     */
    public void highlightField(String fieldName) {
        this.highlightedField = fieldName;
        // 如果有当前上下文，刷新显示
        if (currentContext != null) {
            updateSchemaSection(currentContext);
        }
    }

    /**
     * 清除高亮
     */
    public void clearHighlight() {
        this.highlightedField = null;
        if (currentContext != null) {
            updateSchemaSection(currentContext);
        }
    }

    /**
     * 清空显示
     */
    private void clearDisplay() {
        locationTypeLabel.setText("--");
        locationPathLabel.setText("--");
        locationIdLabel.setText("");
        tableNameLabel.setText("--");
        columnsContainer.getChildren().clear();
        outgoingRefsContainer.getChildren().clear();
        incomingRefsContainer.getChildren().clear();
        clearAiUnderstanding();
    }

    /**
     * 获取位置类型的显示文本
     */
    private String getLocationTypeDisplay(DesignContext.ContextLocation location) {
        return switch (location) {
            case FILE -> "文件";
            case TABLE -> "数据库表";
            case ROW -> "表格行";
            case FIELD -> "字段";
            case MECHANISM -> "游戏机制";
        };
    }

    /**
     * 获取当前上下文
     */
    public DesignContext getCurrentContext() {
        return currentContext;
    }

    /**
     * 获取所有补充说明
     */
    public List<String> getSupplements() {
        return new ArrayList<>(supplements);
    }

    /**
     * 清空补充说明
     */
    public void clearSupplements() {
        supplements.clear();
        supplementsContainer.getChildren().clear();
    }

    /**
     * 设置补充说明添加回调
     */
    public void setOnSupplementAdded(Consumer<String> callback) {
        this.onSupplementAdded = callback;
    }
}
