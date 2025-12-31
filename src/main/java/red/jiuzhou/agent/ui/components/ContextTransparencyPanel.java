package red.jiuzhou.agent.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import red.jiuzhou.agent.context.*;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 上下文透明化面板
 *
 * <p>让设计师清晰看到AI正在使用的所有上下文信息，包括：
 * <ul>
 *   <li>位置信息（文件/表/行）</li>
 *   <li>表结构元数据</li>
 *   <li>当前行数据</li>
 *   <li>引用关系（入度/出度）</li>
 *   <li>语义提示（游戏术语映射）</li>
 * </ul>
 *
 * <p>设计原则：
 * <ol>
 *   <li>透明 - 所有AI使用的信息都可见</li>
 *   <li>可编辑 - 设计师可以补充/修正信息</li>
 *   <li>可信度 - 显示信息来源和可信度</li>
 * </ol>
 *
 * @author Claude
 * @version 1.0
 */
public class ContextTransparencyPanel extends VBox {

    // 上下文树视图
    private final TreeView<ContextItem> contextTree;

    // 根节点
    private final TreeItem<ContextItem> rootItem;

    // 补充说明输入
    private final TextArea supplementInput;

    // 当前上下文
    private DesignContext currentContext;

    // 补充说明回调
    private Consumer<String> onSupplementAdded;

    // 分类节点
    private TreeItem<ContextItem> locationNode;
    private TreeItem<ContextItem> schemaNode;
    private TreeItem<ContextItem> rowDataNode;
    private TreeItem<ContextItem> refsNode;
    private TreeItem<ContextItem> semanticsNode;

    public ContextTransparencyPanel() {
        this.setSpacing(8);
        this.setPadding(new Insets(8));
        this.getStyleClass().add("context-transparency-panel");

        // 标题
        Label titleLabel = new Label("当前上下文");
        titleLabel.getStyleClass().add("panel-title");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // 上下文树
        rootItem = new TreeItem<>(new ContextItem("root", "上下文信息", ContextItem.ItemType.ROOT));
        rootItem.setExpanded(true);

        contextTree = new TreeView<>(rootItem);
        contextTree.setShowRoot(false);
        contextTree.setCellFactory(tv -> new ContextTreeCell());
        contextTree.getStyleClass().add("context-tree");
        VBox.setVgrow(contextTree, Priority.ALWAYS);

        // 初始化分类节点
        initCategoryNodes();

        // 补充说明区域
        TitledPane supplementPane = createSupplementPane();

        // 组装
        this.getChildren().addAll(titleLabel, contextTree, supplementPane);
    }

    /**
     * 初始化分类节点
     */
    private void initCategoryNodes() {
        locationNode = new TreeItem<>(new ContextItem("location", "位置信息", ContextItem.ItemType.CATEGORY));
        locationNode.setExpanded(true);

        schemaNode = new TreeItem<>(new ContextItem("schema", "表结构", ContextItem.ItemType.CATEGORY));
        schemaNode.setExpanded(false);

        rowDataNode = new TreeItem<>(new ContextItem("rowData", "当前行数据", ContextItem.ItemType.CATEGORY));
        rowDataNode.setExpanded(false);

        refsNode = new TreeItem<>(new ContextItem("refs", "引用关系", ContextItem.ItemType.CATEGORY));
        refsNode.setExpanded(true);

        semanticsNode = new TreeItem<>(new ContextItem("semantics", "语义提示", ContextItem.ItemType.CATEGORY));
        semanticsNode.setExpanded(true);

        rootItem.getChildren().addAll(locationNode, schemaNode, rowDataNode, refsNode, semanticsNode);
    }

    /**
     * 创建补充说明面板
     */
    private TitledPane createSupplementPane() {
        supplementInput = new TextArea();
        supplementInput.setPromptText("补充AI可能需要的信息...\n例如：这个物品是活动奖励，不应该被普通怪物掉落");
        supplementInput.setPrefRowCount(3);
        supplementInput.setWrapText(true);

        Button addButton = new Button("添加到上下文");
        addButton.setOnAction(e -> {
            String text = supplementInput.getText().trim();
            if (!text.isEmpty()) {
                addSupplementToContext(text);
                supplementInput.clear();
            }
        });

        VBox content = new VBox(8, supplementInput, addButton);
        content.setAlignment(Pos.CENTER_RIGHT);

        TitledPane pane = new TitledPane("补充说明", content);
        pane.setExpanded(false);
        pane.setAnimated(true);
        return pane;
    }

    /**
     * 更新上下文显示
     *
     * @param context 设计上下文
     */
    public void updateContext(DesignContext context) {
        this.currentContext = context;

        Platform.runLater(() -> {
            // 清空所有分类节点的子项
            locationNode.getChildren().clear();
            schemaNode.getChildren().clear();
            rowDataNode.getChildren().clear();
            refsNode.getChildren().clear();
            semanticsNode.getChildren().clear();

            if (context == null) {
                addPlaceholder(locationNode, "无上下文");
                return;
            }

            // 更新位置信息
            updateLocationInfo(context);

            // 更新表结构
            updateSchemaInfo(context);

            // 更新行数据
            updateRowData(context);

            // 更新引用关系
            updateReferences(context);

            // 更新语义提示
            updateSemantics(context);
        });
    }

    /**
     * 更新位置信息
     */
    private void updateLocationInfo(DesignContext context) {
        // 位置类型
        if (context.getLocation() != null) {
            String locationType = switch (context.getLocation()) {
                case FILE -> "文件";
                case TABLE -> "数据库表";
                case ROW -> "表格行";
                case FIELD -> "字段";
                case MECHANISM -> "游戏机制";
            };
            addItem(locationNode, "type", "类型", locationType, ContextItem.ItemType.INFO);
        }

        // 表名
        if (context.getTableName() != null) {
            addItem(locationNode, "table", "表名", context.getTableName(), ContextItem.ItemType.INFO);
        }

        // 焦点路径
        if (context.getFocusPath() != null) {
            addItem(locationNode, "path", "路径", context.getFocusPath(), ContextItem.ItemType.INFO);
        }

        // 焦点ID
        if (context.getFocusId() != null) {
            addItem(locationNode, "id", "ID", context.getFocusId(), ContextItem.ItemType.INFO);
        }

        // 所属机制
        if (context.getMechanism() != null) {
            addItem(locationNode, "mechanism", "机制",
                    context.getMechanism().getDisplayName(),
                    ContextItem.ItemType.HIGHLIGHT);
        }

        if (locationNode.getChildren().isEmpty()) {
            addPlaceholder(locationNode, "无位置信息");
        }
    }

    /**
     * 更新表结构信息
     */
    private void updateSchemaInfo(DesignContext context) {
        TableMetadata schema = context.getTableSchema();
        if (schema == null) {
            addPlaceholder(schemaNode, "无表结构信息");
            return;
        }

        // 表基本信息
        addItem(schemaNode, "tableName", "表名", schema.getTableName(), ContextItem.ItemType.INFO);
        addItem(schemaNode, "columnCount", "字段数", String.valueOf(schema.getColumns().size()), ContextItem.ItemType.INFO);

        // 字段列表
        TreeItem<ContextItem> columnsItem = new TreeItem<>(
                new ContextItem("columns", "字段列表", ContextItem.ItemType.CATEGORY));
        schemaNode.getChildren().add(columnsItem);

        for (ColumnMetadata col : schema.getColumns()) {
            String display = col.getName() + " (" + col.getType() + ")";
            if (col.getComment() != null && !col.getComment().isEmpty()) {
                display += " // " + col.getComment();
            }
            addItem(columnsItem, col.getName(), col.getName(), display, ContextItem.ItemType.FIELD);
        }
    }

    /**
     * 更新行数据
     */
    private void updateRowData(DesignContext context) {
        Map<String, Object> rowData = context.getRowData();
        if (rowData == null || rowData.isEmpty()) {
            addPlaceholder(rowDataNode, "无行数据");
            return;
        }

        for (Map.Entry<String, Object> entry : rowData.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString() : "null";
            // 截断过长的值
            if (value.length() > 100) {
                value = value.substring(0, 100) + "...";
            }
            addItem(rowDataNode, entry.getKey(), entry.getKey(), value, ContextItem.ItemType.DATA);
        }
    }

    /**
     * 更新引用关系
     */
    private void updateReferences(DesignContext context) {
        // 出度引用（引用了谁）
        if (!context.getOutgoingRefs().isEmpty()) {
            TreeItem<ContextItem> outgoingItem = new TreeItem<>(
                    new ContextItem("outgoing", "引用了 (" + context.getOutgoingRefs().size() + ")", ContextItem.ItemType.CATEGORY));
            refsNode.getChildren().add(outgoingItem);

            for (FieldReference ref : context.getOutgoingRefs()) {
                addItem(outgoingItem, ref.targetTable() + "." + ref.targetField(),
                        "→ " + ref.targetTable(),
                        ref.targetField() + (ref.count() > 0 ? " (" + ref.count() + "处)" : ""),
                        ContextItem.ItemType.REF_OUT);
            }
        }

        // 入度引用（被谁引用）
        if (!context.getIncomingRefs().isEmpty()) {
            TreeItem<ContextItem> incomingItem = new TreeItem<>(
                    new ContextItem("incoming", "被引用 (" + context.getIncomingRefs().size() + ")", ContextItem.ItemType.CATEGORY));
            refsNode.getChildren().add(incomingItem);

            for (FieldReference ref : context.getIncomingRefs()) {
                addItem(incomingItem, ref.sourceTable() + "." + ref.sourceField(),
                        "← " + ref.sourceTable(),
                        ref.sourceField() + (ref.count() > 0 ? " (" + ref.count() + "处)" : ""),
                        ContextItem.ItemType.REF_IN);
            }
        }

        if (refsNode.getChildren().isEmpty()) {
            addPlaceholder(refsNode, "无引用关系");
        }
    }

    /**
     * 更新语义提示
     */
    private void updateSemantics(DesignContext context) {
        Map<String, String> hints = context.getSemanticHints();
        if (hints == null || hints.isEmpty()) {
            addPlaceholder(semanticsNode, "无语义提示");
            return;
        }

        for (Map.Entry<String, String> entry : hints.entrySet()) {
            addItem(semanticsNode, entry.getKey(), entry.getKey(), entry.getValue(), ContextItem.ItemType.SEMANTIC);
        }
    }

    /**
     * 添加项到树节点
     */
    private void addItem(TreeItem<ContextItem> parent, String id, String key, String value, ContextItem.ItemType type) {
        ContextItem item = new ContextItem(id, key + ": " + value, type);
        item.setKey(key);
        item.setValue(value);
        parent.getChildren().add(new TreeItem<>(item));
    }

    /**
     * 添加占位符
     */
    private void addPlaceholder(TreeItem<ContextItem> parent, String text) {
        parent.getChildren().add(new TreeItem<>(new ContextItem("placeholder", text, ContextItem.ItemType.PLACEHOLDER)));
    }

    /**
     * 添加补充信息到上下文
     */
    private void addSupplementToContext(String text) {
        if (currentContext != null) {
            currentContext.addSemanticHint("用户补充", text);
            updateContext(currentContext);
        }

        if (onSupplementAdded != null) {
            onSupplementAdded.accept(text);
        }
    }

    /**
     * 设置补充说明回调
     */
    public void setOnSupplementAdded(Consumer<String> callback) {
        this.onSupplementAdded = callback;
    }

    /**
     * 高亮指定的上下文项（AI正在使用的项）
     *
     * @param itemIds 要高亮的项ID列表
     */
    public void highlightItems(java.util.List<String> itemIds) {
        Platform.runLater(() -> {
            // 遍历所有节点，设置高亮状态
            highlightItemsRecursive(rootItem, itemIds);
        });
    }

    private void highlightItemsRecursive(TreeItem<ContextItem> node, java.util.List<String> itemIds) {
        if (node.getValue() != null) {
            node.getValue().setHighlighted(itemIds.contains(node.getValue().getId()));
        }
        for (TreeItem<ContextItem> child : node.getChildren()) {
            highlightItemsRecursive(child, itemIds);
        }
        contextTree.refresh();
    }

    /**
     * 获取当前上下文
     */
    public DesignContext getCurrentContext() {
        return currentContext;
    }

    // ==================== 内部类：上下文项 ====================

    /**
     * 上下文树节点数据
     */
    public static class ContextItem {

        public enum ItemType {
            ROOT,           // 根节点
            CATEGORY,       // 分类节点
            INFO,           // 普通信息
            DATA,           // 数据值
            FIELD,          // 字段
            REF_IN,         // 入度引用
            REF_OUT,        // 出度引用
            SEMANTIC,       // 语义提示
            HIGHLIGHT,      // 高亮项
            PLACEHOLDER     // 占位符
        }

        private final String id;
        private final String display;
        private final ItemType type;
        private String key;
        private String value;
        private boolean highlighted;

        public ContextItem(String id, String display, ItemType type) {
            this.id = id;
            this.display = display;
            this.type = type;
        }

        public String getId() { return id; }
        public String getDisplay() { return display; }
        public ItemType getType() { return type; }
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public boolean isHighlighted() { return highlighted; }
        public void setHighlighted(boolean highlighted) { this.highlighted = highlighted; }

        @Override
        public String toString() {
            return display;
        }
    }

    // ==================== 内部类：树单元格渲染 ====================

    /**
     * 自定义树单元格渲染
     */
    private static class ContextTreeCell extends TreeCell<ContextItem> {

        @Override
        protected void updateItem(ContextItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
                return;
            }

            setText(item.getDisplay());

            // 根据类型设置样式
            String style = switch (item.getType()) {
                case CATEGORY -> "-fx-font-weight: bold;";
                case PLACEHOLDER -> "-fx-text-fill: #9E9E9E; -fx-font-style: italic;";
                case REF_IN -> "-fx-text-fill: #4CAF50;";  // 绿色
                case REF_OUT -> "-fx-text-fill: #2196F3;"; // 蓝色
                case SEMANTIC -> "-fx-text-fill: #FF9800;"; // 橙色
                case HIGHLIGHT -> "-fx-text-fill: #E91E63; -fx-font-weight: bold;"; // 粉色
                default -> "";
            };

            // 高亮状态
            if (item.isHighlighted()) {
                style += "-fx-background-color: #FFEB3B; -fx-background-radius: 3;";
            }

            setStyle(style);
        }
    }
}
