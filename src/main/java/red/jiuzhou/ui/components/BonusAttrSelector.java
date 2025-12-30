package red.jiuzhou.ui.components;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.pattern.dao.AttrDictionaryDao;
import red.jiuzhou.pattern.model.AttrDictionary;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 属性增益选择器
 *
 * <p>用于选择和配置游戏属性增益的专业UI组件。从 attr_dictionary 表加载126个属性，
 * 支持多选、分类过滤、数值设置，为设计师提供直观的属性配置界面。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>从 attr_dictionary 表加载126个属性定义</li>
 *   <li>按分类显示（基础属性、战斗属性、HP/MP、暴击、命中回避等）</li>
 *   <li>多选模式（支持选择多个属性）</li>
 *   <li>为每个属性设置数值</li>
 *   <li>实时搜索过滤（按属性代码或名称）</li>
 *   <li>显示属性典型值范围（作为参考）</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>物品属性配置（bonus_attr1-10）</li>
 *   <li>称号属性设置</li>
 *   <li>宠物属性增益</li>
 *   <li>技能Buff效果</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * BonusAttrSelector selector = new BonusAttrSelector();
 *
 * // 预设属性值
 * Map<String, String> initialValues = new HashMap<>();
 * initialValues.put("physical_attack", "100");
 * initialValues.put("max_hp", "500");
 * selector.setSelectedAttributes(initialValues);
 *
 * // 获取选中的属性
 * Map<String, String> selected = selector.getSelectedAttributes();
 * }</pre>
 *
 * @author Claude
 * @version 1.0
 * @since 2025-12-30
 */
public class BonusAttrSelector extends VBox {
    private static final Logger log = LoggerFactory.getLogger(BonusAttrSelector.class);

    private final JdbcTemplate jdbcTemplate;
    private final AttrDictionaryDao attrDictionaryDao;

    private TableView<AttrRow> tableView;
    private ComboBox<String> categoryFilter;
    private TextField searchField;
    private Label summaryLabel;

    private ObservableList<AttrRow> allAttributes;
    private ObservableList<AttrRow> filteredAttributes;

    // ========== 构造函数 ==========

    public BonusAttrSelector() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        this.attrDictionaryDao = new AttrDictionaryDao(jdbcTemplate);
        this.allAttributes = FXCollections.observableArrayList();
        this.filteredAttributes = FXCollections.observableArrayList();

        initialize();
        loadDataAsync();
    }

    // ========== 初始化 ==========

    /**
     * 初始化组件
     */
    private void initialize() {
        setSpacing(10);
        setPadding(new Insets(10));

        // 工具栏
        HBox toolbar = createToolbar();
        getChildren().add(toolbar);

        // 表格
        tableView = createTableView();
        VBox.setVgrow(tableView, Priority.ALWAYS);
        getChildren().add(tableView);

        // 摘要栏
        summaryLabel = new Label("已选择: 0 个属性");
        summaryLabel.setStyle("-fx-font-weight: bold;");
        getChildren().add(summaryLabel);
    }

    /**
     * 创建工具栏
     */
    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(5));

        // 分类过滤器
        Label categoryLabel = new Label("分类:");
        categoryFilter = new ComboBox<>();
        categoryFilter.getItems().addAll(
                "全部",
                "基础属性",
                "战斗属性",
                "生命魔力",
                "暴击属性",
                "命中回避",
                "速度属性",
                "抗性属性",
                "特殊属性"
        );
        categoryFilter.setValue("全部");
        categoryFilter.setOnAction(e -> applyFilters());

        // 搜索框
        searchField = new TextField();
        searchField.setPromptText("搜索属性...");
        searchField.setPrefWidth(200);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // 全选/取消全选按钮
        Button selectAllBtn = new Button("全选");
        selectAllBtn.setOnAction(e -> selectAll());

        Button deselectAllBtn = new Button("取消全选");
        deselectAllBtn.setOnAction(e -> deselectAll());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(
                categoryLabel, categoryFilter,
                searchField,
                spacer,
                selectAllBtn, deselectAllBtn
        );

        return toolbar;
    }

    /**
     * 创建表格视图
     */
    private TableView<AttrRow> createTableView() {
        TableView<AttrRow> table = new TableView<>();
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 选择列
        TableColumn<AttrRow, Boolean> selectedCol = new TableColumn<>("选择");
        selectedCol.setCellValueFactory(new PropertyValueFactory<>("selected"));
        selectedCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectedCol));
        selectedCol.setPrefWidth(60);
        selectedCol.setEditable(true);

        // 属性代码列
        TableColumn<AttrRow, String> codeCol = new TableColumn<>("属性代码");
        codeCol.setCellValueFactory(new PropertyValueFactory<>("attrCode"));
        codeCol.setPrefWidth(150);

        // 属性名称列
        TableColumn<AttrRow, String> nameCol = new TableColumn<>("属性名称");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("attrName"));
        nameCol.setPrefWidth(120);

        // 分类列
        TableColumn<AttrRow, String> categoryCol = new TableColumn<>("分类");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(100);

        // 数值列
        TableColumn<AttrRow, String> valueCol = new TableColumn<>("数值");
        valueCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valueCol.setCellFactory(col -> new TextFieldTableCell<>());
        valueCol.setPrefWidth(100);
        valueCol.setEditable(true);

        // 典型范围列
        TableColumn<AttrRow, String> rangeCol = new TableColumn<>("典型范围");
        rangeCol.setCellValueFactory(new PropertyValueFactory<>("typicalRange"));
        rangeCol.setPrefWidth(120);

        table.getColumns().addAll(selectedCol, codeCol, nameCol, categoryCol, valueCol, rangeCol);
        table.setItems(filteredAttributes);

        // 监听选择变化
        table.itemsProperty().addListener((obs, oldList, newList) -> updateSummary());

        return table;
    }

    // ========== 数据加载 ==========

    /**
     * 异步加载数据
     */
    private void loadDataAsync() {
        Task<List<AttrDictionary>> task = new Task<>() {
            @Override
            protected List<AttrDictionary> call() {
                return attrDictionaryDao.findAll();
            }
        };

        task.setOnSucceeded(event -> {
            List<AttrDictionary> attrs = task.getValue();
            populateTable(attrs);
            log.info("属性数据加载成功: {} 个属性", attrs.size());
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();
            log.error("属性数据加载失败", error);
            showError("加载属性数据失败: " + error.getMessage());
        });

        Thread.ofVirtual().start(task);
    }

    /**
     * 填充表格数据
     */
    private void populateTable(List<AttrDictionary> attributes) {
        allAttributes.clear();

        for (AttrDictionary attr : attributes) {
            AttrRow row = new AttrRow(attr);

            // 监听选择状态变化
            row.selectedProperty().addListener((obs, wasSelected, isNowSelected) -> {
                updateSummary();

                // 如果取消选择，清空数值
                if (!isNowSelected) {
                    row.setValue("");
                }
            });

            allAttributes.add(row);
        }

        filteredAttributes.setAll(allAttributes);
        updateSummary();
    }

    // ========== 过滤和搜索 ==========

    /**
     * 应用过滤条件
     */
    private void applyFilters() {
        String category = categoryFilter.getValue();
        String searchText = searchField.getText().toLowerCase();

        List<AttrRow> filtered = allAttributes.stream()
                .filter(row -> matchesCategory(row, category))
                .filter(row -> matchesSearch(row, searchText))
                .collect(Collectors.toList());

        filteredAttributes.setAll(filtered);
        updateSummary();
    }

    /**
     * 检查是否匹配分类
     */
    private boolean matchesCategory(AttrRow row, String category) {
        if (category == null || category.equals("全部")) {
            return true;
        }
        return row.getCategory().equals(category);
    }

    /**
     * 检查是否匹配搜索
     */
    private boolean matchesSearch(AttrRow row, String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            return true;
        }

        return row.getAttrCode().toLowerCase().contains(searchText) ||
               row.getAttrName().toLowerCase().contains(searchText);
    }

    /**
     * 全选
     */
    private void selectAll() {
        filteredAttributes.forEach(row -> row.setSelected(true));
        updateSummary();
    }

    /**
     * 取消全选
     */
    private void deselectAll() {
        filteredAttributes.forEach(row -> row.setSelected(false));
        updateSummary();
    }

    /**
     * 更新摘要
     */
    private void updateSummary() {
        long selectedCount = allAttributes.stream()
                .filter(AttrRow::isSelected)
                .count();

        summaryLabel.setText(String.format("已选择: %d 个属性 (共 %d 个)",
                selectedCount, allAttributes.size()));
    }

    // ========== 公共方法 ==========

    /**
     * 获取选中的属性
     *
     * @return 属性代码 → 数值映射
     */
    public Map<String, String> getSelectedAttributes() {
        Map<String, String> selected = new LinkedHashMap<>();

        for (AttrRow row : allAttributes) {
            if (row.isSelected()) {
                String value = row.getValue();
                if (value != null && !value.isEmpty()) {
                    selected.put(row.getAttrCode(), value);
                }
            }
        }

        return selected;
    }

    /**
     * 设置选中的属性
     *
     * @param attributes 属性代码 → 数值映射
     */
    public void setSelectedAttributes(Map<String, String> attributes) {
        // 先清空所有选择
        deselectAll();

        if (attributes == null || attributes.isEmpty()) {
            return;
        }

        // 设置选中项
        for (AttrRow row : allAttributes) {
            String value = attributes.get(row.getAttrCode());
            if (value != null) {
                row.setSelected(true);
                row.setValue(value);
            }
        }

        updateSummary();
    }

    /**
     * 清空选择
     */
    public void clear() {
        deselectAll();
    }

    /**
     * 刷新数据
     */
    public void refresh() {
        clear();
        loadDataAsync();
    }

    // ========== 辅助方法 ==========

    /**
     * 显示错误提示
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText("属性选择器错误");
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ========== 内部类 ==========

    /**
     * 属性行数据模型
     */
    public static class AttrRow {
        private final BooleanProperty selected;
        private final StringProperty attrCode;
        private final StringProperty attrName;
        private final StringProperty category;
        private final StringProperty value;
        private final StringProperty typicalRange;

        public AttrRow(AttrDictionary attr) {
            this.selected = new SimpleBooleanProperty(false);
            this.attrCode = new SimpleStringProperty(attr.getAttrCode());
            this.attrName = new SimpleStringProperty(attr.getAttrName());

            // 映射分类
            String categoryName = mapCategory(attr.getAttrCategory());
            this.category = new SimpleStringProperty(categoryName);

            this.value = new SimpleStringProperty("");

            // 格式化典型范围
            String range = formatTypicalRange(attr);
            this.typicalRange = new SimpleStringProperty(range);
        }

        /**
         * 映射分类名称
         */
        private String mapCategory(String category) {
            if (category == null) return "未分类";

            return switch (category) {
                case "BASIC" -> "基础属性";
                case "COMBAT" -> "战斗属性";
                case "HP_MP" -> "生命魔力";
                case "CRITICAL" -> "暴击属性";
                case "ACCURACY" -> "命中回避";
                case "SPEED" -> "速度属性";
                case "RESIST" -> "抗性属性";
                case "SPECIAL" -> "特殊属性";
                default -> "未分类";
            };
        }

        /**
         * 格式化典型范围
         */
        private String formatTypicalRange(AttrDictionary attr) {
            if (attr.getTypicalMin() == null && attr.getTypicalMax() == null) {
                return "—";
            }

            String min = attr.getTypicalMin() != null ? attr.getTypicalMin().toString() : "0";
            String max = attr.getTypicalMax() != null ? attr.getTypicalMax().toString() : "∞";

            return String.format("%s ~ %s", min, max);
        }

        // Property getters
        public BooleanProperty selectedProperty() { return selected; }
        public StringProperty attrCodeProperty() { return attrCode; }
        public StringProperty attrNameProperty() { return attrName; }
        public StringProperty categoryProperty() { return category; }
        public StringProperty valueProperty() { return value; }
        public StringProperty typicalRangeProperty() { return typicalRange; }

        // Getters and Setters
        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean selected) { this.selected.set(selected); }

        public String getAttrCode() { return attrCode.get(); }
        public void setAttrCode(String attrCode) { this.attrCode.set(attrCode); }

        public String getAttrName() { return attrName.get(); }
        public void setAttrName(String attrName) { this.attrName.set(attrName); }

        public String getCategory() { return category.get(); }
        public void setCategory(String category) { this.category.set(category); }

        public String getValue() { return value.get(); }
        public void setValue(String value) { this.value.set(value); }

        public String getTypicalRange() { return typicalRange.get(); }
        public void setTypicalRange(String typicalRange) { this.typicalRange.set(typicalRange); }
    }

    /**
     * 文本框单元格（用于数值列）
     */
    private static class TextFieldTableCell<S> extends TableCell<S, String> {
        private TextField textField;

        @Override
        public void startEdit() {
            super.startEdit();

            if (textField == null) {
                createTextField();
            }

            textField.setText(getItem());
            setText(null);
            setGraphic(textField);
            textField.selectAll();
            textField.requestFocus();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem());
            setGraphic(null);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (textField != null) {
                        textField.setText(getItem());
                    }
                    setText(null);
                    setGraphic(textField);
                } else {
                    setText(getItem());
                    setGraphic(null);
                }
            }
        }

        private void createTextField() {
            textField = new TextField(getItem());
            textField.setOnAction(evt -> commitEdit(textField.getText()));
            textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) {
                    commitEdit(textField.getText());
                }
            });
        }
    }
}
