package red.jiuzhou.agent.ui.components;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据对比预览组件
 *
 * <p>展示修改前后的数据差异，支持：
 * <ul>
 *   <li>表格展示修改前后数据</li>
 *   <li>差异高亮（旧值删除线 → 新值绿色加粗）</li>
 *   <li>每行可单独勾选确认/拒绝</li>
 *   <li>统计信息（共X条，已选Y条）</li>
 *   <li>全选/取消全选</li>
 * </ul>
 *
 * <p>对比表格样式：
 * <pre>
 * | ☑ | # | 名称 | 攻击力 | 防御力 |
 * |---|---|------|--------|--------|
 * | ☑ | 1 | 紫装A | ~~100~~ → **110** | 50 |
 * | ☑ | 2 | 紫装B | ~~150~~ → **165** | 75 |
 * | ☐ | 3 | 紫装C | ~~200~~ → **220** | 100 |  ← 用户取消选择
 * </pre>
 *
 * @author Claude
 * @version 1.0
 */
public class DataComparisonView extends VBox {

    // 数据表格
    private final TableView<ComparisonRow> table;

    // 数据源
    private final ObservableList<ComparisonRow> data = FXCollections.observableArrayList();

    // 全选复选框
    private final CheckBox selectAllCheckBox;

    // 统计标签
    private final Label statsLabel;

    // 已修改的列名列表
    private List<String> modifiedColumns = new ArrayList<>();

    // 所有列名
    private List<String> allColumns = new ArrayList<>();

    public DataComparisonView() {
        this.setSpacing(8);
        this.setPadding(new Insets(8));
        this.getStyleClass().add("data-comparison-view");

        // 顶部工具栏
        HBox toolbar = createToolbar();

        // 数据表格
        table = new TableView<>(data);
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("comparison-table");
        VBox.setVgrow(table, Priority.ALWAYS);

        // 底部统计栏
        statsLabel = new Label("共 0 条记录，已选 0 条");
        statsLabel.getStyleClass().add("stats-label");
        statsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        HBox bottomBar = new HBox(statsLabel);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(4, 0, 0, 0));

        // 全选复选框
        selectAllCheckBox = new CheckBox("全选");
        selectAllCheckBox.setSelected(true);
        selectAllCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            for (ComparisonRow row : data) {
                row.setSelected(newVal);
            }
            updateStats();
        });

        this.getChildren().addAll(toolbar, table, bottomBar);
    }

    /**
     * 创建工具栏
     */
    private HBox createToolbar() {
        selectAllCheckBox.setSelected(true);

        Button invertButton = new Button("反选");
        invertButton.setOnAction(e -> {
            for (ComparisonRow row : data) {
                row.setSelected(!row.isSelected());
            }
            updateSelectAllState();
            updateStats();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label titleLabel = new Label("修改预览");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        HBox toolbar = new HBox(10, selectAllCheckBox, invertButton, spacer, titleLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 8, 0));

        return toolbar;
    }

    /**
     * 设置对比数据
     *
     * @param beforeData 修改前数据
     * @param afterData 修改后数据
     * @param modifiedColumns 被修改的列名
     */
    public void setComparisonData(
            List<Map<String, Object>> beforeData,
            List<Map<String, Object>> afterData,
            List<String> modifiedColumns) {

        this.modifiedColumns = modifiedColumns != null ? modifiedColumns : new ArrayList<>();

        // 清空现有数据
        data.clear();
        table.getColumns().clear();

        if (beforeData == null || beforeData.isEmpty()) {
            statsLabel.setText("无数据");
            return;
        }

        // 收集所有列名
        Set<String> columnSet = new LinkedHashSet<>();
        for (Map<String, Object> row : beforeData) {
            columnSet.addAll(row.keySet());
        }
        allColumns = new ArrayList<>(columnSet);

        // 创建表格列
        createColumns();

        // 创建对比行数据
        for (int i = 0; i < beforeData.size(); i++) {
            Map<String, Object> before = beforeData.get(i);
            Map<String, Object> after = i < afterData.size() ? afterData.get(i) : before;
            data.add(new ComparisonRow(i, before, after, modifiedColumns));
        }

        // 更新统计
        updateStats();
        selectAllCheckBox.setSelected(true);
    }

    /**
     * 创建表格列
     */
    private void createColumns() {
        // 选择列
        TableColumn<ComparisonRow, Boolean> selectCol = new TableColumn<>("");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(col -> new CheckBoxTableCell<>());
        selectCol.setEditable(true);
        selectCol.setPrefWidth(30);
        selectCol.setMaxWidth(30);
        selectCol.setMinWidth(30);

        // 行号列
        TableColumn<ComparisonRow, String> indexCol = new TableColumn<>("#");
        indexCol.setCellValueFactory(cellData ->
                new SimpleStringProperty(String.valueOf(cellData.getValue().getIndex() + 1)));
        indexCol.setPrefWidth(40);
        indexCol.setMaxWidth(50);

        table.getColumns().add(selectCol);
        table.getColumns().add(indexCol);

        // 数据列
        for (String colName : allColumns) {
            boolean isModified = modifiedColumns.contains(colName);

            TableColumn<ComparisonRow, String> col = new TableColumn<>(colName);
            col.setCellValueFactory(cellData -> {
                ComparisonRow row = cellData.getValue();
                return new SimpleStringProperty(row.getDisplayValue(colName));
            });

            // 自定义单元格渲染（显示差异）
            col.setCellFactory(tc -> new DiffTableCell(colName, isModified));

            // 被修改的列使用不同的标题样式
            if (isModified) {
                col.setStyle("-fx-font-weight: bold; -fx-text-fill: #2196F3;");
            }

            table.getColumns().add(col);
        }

        // 监听选择变化
        for (ComparisonRow row : data) {
            row.selectedProperty().addListener((obs, oldVal, newVal) -> {
                updateSelectAllState();
                updateStats();
            });
        }
    }

    /**
     * 更新全选复选框状态
     */
    private void updateSelectAllState() {
        long selectedCount = data.stream().filter(ComparisonRow::isSelected).count();
        if (selectedCount == 0) {
            selectAllCheckBox.setSelected(false);
            selectAllCheckBox.setIndeterminate(false);
        } else if (selectedCount == data.size()) {
            selectAllCheckBox.setSelected(true);
            selectAllCheckBox.setIndeterminate(false);
        } else {
            selectAllCheckBox.setIndeterminate(true);
        }
    }

    /**
     * 更新统计信息
     */
    private void updateStats() {
        long selectedCount = data.stream().filter(ComparisonRow::isSelected).count();
        statsLabel.setText(String.format("共 %d 条记录，已选 %d 条", data.size(), selectedCount));
    }

    /**
     * 获取已选中的行索引
     */
    public List<Integer> getSelectedIndices() {
        return data.stream()
                .filter(ComparisonRow::isSelected)
                .map(ComparisonRow::getIndex)
                .collect(Collectors.toList());
    }

    /**
     * 获取已选中的行数
     */
    public int getSelectedCount() {
        return (int) data.stream().filter(ComparisonRow::isSelected).count();
    }

    /**
     * 获取总行数
     */
    public int getTotalCount() {
        return data.size();
    }

    /**
     * 检查是否有选中的行
     */
    public boolean hasSelection() {
        return data.stream().anyMatch(ComparisonRow::isSelected);
    }

    // ==================== 内部类：对比行数据 ====================

    /**
     * 对比行数据模型
     */
    public static class ComparisonRow {

        private final int index;
        private final Map<String, Object> beforeData;
        private final Map<String, Object> afterData;
        private final List<String> modifiedColumns;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(true);

        public ComparisonRow(int index, Map<String, Object> beforeData,
                             Map<String, Object> afterData, List<String> modifiedColumns) {
            this.index = index;
            this.beforeData = beforeData;
            this.afterData = afterData;
            this.modifiedColumns = modifiedColumns;
        }

        public int getIndex() {
            return index;
        }

        public boolean isSelected() {
            return selected.get();
        }

        public void setSelected(boolean value) {
            selected.set(value);
        }

        public SimpleBooleanProperty selectedProperty() {
            return selected;
        }

        public Object getBeforeValue(String column) {
            return beforeData.get(column);
        }

        public Object getAfterValue(String column) {
            return afterData.get(column);
        }

        public boolean isColumnModified(String column) {
            return modifiedColumns.contains(column);
        }

        public boolean hasValueChanged(String column) {
            Object before = beforeData.get(column);
            Object after = afterData.get(column);
            if (before == null && after == null) return false;
            if (before == null || after == null) return true;
            return !before.equals(after);
        }

        public String getDisplayValue(String column) {
            Object before = beforeData.get(column);
            Object after = afterData.get(column);

            if (!hasValueChanged(column)) {
                return before != null ? before.toString() : "";
            }

            // 显示差异
            String beforeStr = before != null ? before.toString() : "null";
            String afterStr = after != null ? after.toString() : "null";
            return beforeStr + " → " + afterStr;
        }
    }

    // ==================== 内部类：差异单元格渲染 ====================

    /**
     * 差异高亮单元格
     */
    private static class DiffTableCell extends TableCell<ComparisonRow, String> {

        private final String columnName;
        private final boolean isModifiedColumn;

        public DiffTableCell(String columnName, boolean isModifiedColumn) {
            this.columnName = columnName;
            this.isModifiedColumn = isModifiedColumn;
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
                return;
            }

            ComparisonRow row = getTableView().getItems().get(getIndex());

            if (row.hasValueChanged(columnName)) {
                // 使用富文本显示差异
                TextFlow textFlow = createDiffDisplay(row);
                setGraphic(textFlow);
                setText(null);
                setStyle("-fx-background-color: #FFF9C4;"); // 浅黄色背景
            } else {
                setText(item);
                setGraphic(null);
                setStyle(isModifiedColumn ? "-fx-background-color: #E3F2FD;" : "");
            }
        }

        private TextFlow createDiffDisplay(ComparisonRow row) {
            Object before = row.getBeforeValue(columnName);
            Object after = row.getAfterValue(columnName);

            String beforeStr = before != null ? before.toString() : "null";
            String afterStr = after != null ? after.toString() : "null";

            // 旧值（删除线样式）
            Text oldText = new Text(beforeStr);
            oldText.setStrikethrough(true);
            oldText.setFill(Color.web("#F44336")); // 红色

            // 箭头
            Text arrow = new Text(" → ");
            arrow.setFill(Color.GRAY);

            // 新值（加粗绿色）
            Text newText = new Text(afterStr);
            newText.setStyle("-fx-font-weight: bold;");
            newText.setFill(Color.web("#4CAF50")); // 绿色

            return new TextFlow(oldText, arrow, newText);
        }
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建并填充对比视图
     */
    public static DataComparisonView create(
            List<Map<String, Object>> beforeData,
            List<Map<String, Object>> afterData,
            List<String> modifiedColumns) {

        DataComparisonView view = new DataComparisonView();
        view.setComparisonData(beforeData, afterData, modifiedColumns);
        return view;
    }

    /**
     * 在对话框中显示对比数据
     *
     * @return 用户选中的行索引列表，如果取消则返回空列表
     */
    public static List<Integer> showDialog(
            String title,
            List<Map<String, Object>> beforeData,
            List<Map<String, Object>> afterData,
            List<String> modifiedColumns) {

        Dialog<List<Integer>> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("请检查以下修改，勾选需要执行的行");
        dialog.setResizable(true);

        // 创建对比视图
        DataComparisonView view = create(beforeData, afterData, modifiedColumns);
        view.setPrefWidth(800);
        view.setPrefHeight(400);

        dialog.getDialogPane().setContent(view);

        // 按钮
        ButtonType confirmButton = new ButtonType("确认修改", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButton, cancelButton);

        // 结果转换
        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmButton) {
                return view.getSelectedIndices();
            }
            return new ArrayList<>();
        });

        return dialog.showAndWait().orElse(new ArrayList<>());
    }
}
