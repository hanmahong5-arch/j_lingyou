package red.jiuzhou.ui.components;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.analysis.aion.IdNameResolver;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 增强型数据表格组件
 *
 * <p>为游戏设计师提供高效的数据浏览体验：
 * <ul>
 *   <li>智能列宽自适应</li>
 *   <li>实时搜索过滤（支持多字段）</li>
 *   <li>ID字段自动显示对应NAME</li>
 *   <li>单元格复制、多选</li>
 *   <li>统计信息显示</li>
 *   <li>列排序</li>
 * </ul>
 *
 * @author Claude
 * @version 2.0
 */
public class EnhancedDataTable extends VBox {

    private static final Logger log = LoggerFactory.getLogger(EnhancedDataTable.class);

    // 配置
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_COLUMN_WIDTH = 400;
    private static final int MIN_COLUMN_WIDTH = 60;

    // UI组件
    private TableView<Map<String, Object>> tableView;
    private TextField searchField;
    private ComboBox<String> searchColumnCombo;
    private Label statsLabel;
    private Label selectionLabel;
    private HBox toolBar;
    private Pagination pagination;

    // 数据
    private ObservableList<Map<String, Object>> allData = FXCollections.observableArrayList();
    private ObservableList<Map<String, Object>> filteredData = FXCollections.observableArrayList();
    private List<String> columnNames = new ArrayList<>();
    private int pageSize = DEFAULT_PAGE_SIZE;
    private int currentPage = 0;
    private String currentSearchText = "";
    private String currentSearchColumn = "全部字段";

    // ID解析器
    private final IdNameResolver idNameResolver = IdNameResolver.getInstance();

    // 回调
    private Consumer<Map<String, Object>> onRowDoubleClick;
    private Consumer<List<Map<String, Object>>> onSelectionChange;

    // 虚拟线程执行器（Java 21+）
    private final ExecutorService searchExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public EnhancedDataTable() {
        setSpacing(0);
        setStyle("-fx-background-color: white;");

        // 创建工具栏
        toolBar = createToolBar();

        // 创建表格
        tableView = createTableView();

        // 创建分页控件
        pagination = createPagination();

        // 创建底部统计栏
        HBox statusBar = createStatusBar();

        // 布局
        VBox.setVgrow(tableView, Priority.ALWAYS);
        getChildren().addAll(toolBar, tableView, pagination, statusBar);
    }

    /**
     * 创建工具栏
     */
    private HBox createToolBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 12, 10, 12));
        bar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e9ecef; -fx-border-width: 0 0 1 0;");

        // 搜索图标
        Label searchIcon = new Label("\uD83D\uDD0D");
        searchIcon.setStyle("-fx-font-size: 14px;");

        // 搜索字段选择
        searchColumnCombo = new ComboBox<>();
        searchColumnCombo.getItems().add("全部字段");
        searchColumnCombo.setValue("全部字段");
        searchColumnCombo.setPrefWidth(130);
        searchColumnCombo.setStyle("-fx-font-size: 12px;");

        // 搜索输入框
        searchField = new TextField();
        searchField.setPromptText("输入关键词搜索...");
        searchField.setPrefWidth(250);
        searchField.setStyle("-fx-font-size: 12px;");

        // 实时搜索（带防抖）
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            currentSearchText = newVal;
            debounceSearch();
        });

        searchColumnCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentSearchColumn = newVal;
            if (!currentSearchText.isEmpty()) {
                performSearch();
            }
        });

        // 清空按钮
        Button clearBtn = new Button("清空");
        clearBtn.setStyle("-fx-background-color: #6c757d; -fx-text-fill: white; -fx-font-size: 11px;");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            currentSearchText = "";
            performSearch();
        });

        // 每页条数选择
        Label pageSizeLabel = new Label("每页:");
        pageSizeLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");

        ComboBox<Integer> pageSizeCombo = new ComboBox<>();
        pageSizeCombo.getItems().addAll(25, 50, 100, 200, 500);
        pageSizeCombo.setValue(pageSize);
        pageSizeCombo.setPrefWidth(70);
        pageSizeCombo.setStyle("-fx-font-size: 11px;");
        pageSizeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                pageSize = newVal;
                currentPage = 0;
                updatePagination();
                updateTableData();
            }
        });

        // 统计标签
        statsLabel = new Label("共 0 条记录");
        statsLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 导出按钮
        Button exportBtn = new Button("导出");
        exportBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-size: 11px;");
        exportBtn.setOnAction(e -> exportData());

        bar.getChildren().addAll(
                searchIcon, searchColumnCombo, searchField, clearBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                pageSizeLabel, pageSizeCombo,
                spacer,
                statsLabel,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                exportBtn
        );

        return bar;
    }

    /**
     * 创建表格
     */
    private TableView<Map<String, Object>> createTableView() {
        TableView<Map<String, Object>> table = new TableView<>();

        // 单元格选择模式
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // 表格样式
        table.setStyle("-fx-font-size: 12px;");
        table.setPlaceholder(new Label("暂无数据"));

        // 快捷键 - 复制
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.C && event.isControlDown()) {
                copySelectedCells();
            }
        });

        // 双击行
        table.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    if (onRowDoubleClick != null) {
                        onRowDoubleClick.accept(row.getItem());
                    }
                }
            });
            return row;
        });

        // 选择变化监听
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateSelectionLabel();
            if (onSelectionChange != null) {
                List<Map<String, Object>> selected = new ArrayList<>();
                for (TablePosition<?, ?> pos : table.getSelectionModel().getSelectedCells()) {
                    Map<String, Object> item = table.getItems().get(pos.getRow());
                    if (!selected.contains(item)) {
                        selected.add(item);
                    }
                }
                onSelectionChange.accept(selected);
            }
        });

        return table;
    }

    /**
     * 创建分页控件
     */
    private Pagination createPagination() {
        Pagination pag = new Pagination(1, 0);
        pag.setMaxPageIndicatorCount(10);
        pag.setStyle("-fx-padding: 8 0;");

        pag.currentPageIndexProperty().addListener((obs, oldVal, newVal) -> {
            currentPage = newVal.intValue();
            updateTableData();
        });

        return pag;
    }

    /**
     * 创建状态栏
     */
    private HBox createStatusBar() {
        HBox bar = new HBox(15);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e9ecef; -fx-border-width: 1 0 0 0;");

        selectionLabel = new Label("未选中");
        selectionLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");

        Label hintLabel = new Label("提示: Ctrl+C 复制选中单元格 | 双击查看详情 | 点击列头排序");
        hintLabel.setStyle("-fx-text-fill: #adb5bd; -fx-font-size: 10px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(selectionLabel, spacer, hintLabel);
        return bar;
    }

    /**
     * 设置数据
     */
    public void setData(List<Map<String, Object>> data) {
        allData.clear();
        if (data != null) {
            allData.addAll(data);
        }

        // 提取列名
        if (!allData.isEmpty()) {
            columnNames = new ArrayList<>(allData.get(0).keySet());
            setupColumns();
            updateSearchColumnCombo();
        }

        filteredData.setAll(allData);
        currentPage = 0;
        updatePagination();
        updateTableData();
        updateStats();
    }

    /**
     * 追加数据
     */
    public void appendData(List<Map<String, Object>> data) {
        if (data != null && !data.isEmpty()) {
            allData.addAll(data);
            if (currentSearchText.isEmpty()) {
                filteredData.addAll(data);
            } else {
                // 对新数据应用过滤
                List<Map<String, Object>> filtered = filterData(data);
                filteredData.addAll(filtered);
            }
            updatePagination();
            updateStats();
        }
    }

    /**
     * 设置列
     */
    private void setupColumns() {
        tableView.getColumns().clear();

        for (String colName : columnNames) {
            TableColumn<Map<String, Object>, Object> column = new TableColumn<>(colName);

            // 值提取
            column.setCellValueFactory(cellData ->
                    new ReadOnlyObjectWrapper<>(cellData.getValue().get(colName)));

            // 检测是否是ID字段
            final boolean isIdField = idNameResolver.isIdField(colName);
            if (isIdField) {
                // 设置自定义单元格渲染
                column.setCellFactory(col -> createIdNameCell(colName));
                column.setText(colName + " →");  // 标记ID字段
            }

            // 设置列宽
            column.setPrefWidth(calculateColumnWidth(colName));
            column.setMinWidth(MIN_COLUMN_WIDTH);

            // 列头右键菜单
            column.setContextMenu(createColumnContextMenu(colName));

            tableView.getColumns().add(column);
        }
    }

    /**
     * 创建ID-NAME单元格
     */
    private TableCell<Map<String, Object>, Object> createIdNameCell(String columnName) {
        return new TableCell<Map<String, Object>, Object>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                } else {
                    String idValue = item.toString();
                    String formattedValue = idNameResolver.formatIdWithName(columnName, idValue);
                    setText(formattedValue);

                    // 如果有NAME映射，显示不同样式
                    if (!formattedValue.equals(idValue)) {
                        setStyle("-fx-text-fill: #2980b9;");
                        String name = idNameResolver.resolveByField(columnName, idValue);
                        Tooltip tip = new Tooltip(
                                "ID: " + idValue + "\n" +
                                "名称: " + name + "\n" +
                                "字段: " + columnName
                        );
                        tip.setStyle("-fx-font-size: 11px;");
                        setTooltip(tip);
                    } else {
                        setStyle("");
                        setTooltip(null);
                    }
                }
            }
        };
    }

    /**
     * 计算列宽
     */
    private double calculateColumnWidth(String columnName) {
        // 基于列名长度
        int baseWidth = Math.max(columnName.length() * 10, MIN_COLUMN_WIDTH);

        // 采样数据估算
        if (!allData.isEmpty()) {
            int maxLen = columnName.length();
            int sampleSize = Math.min(20, allData.size());
            for (int i = 0; i < sampleSize; i++) {
                Object val = allData.get(i).get(columnName);
                if (val != null) {
                    maxLen = Math.max(maxLen, val.toString().length());
                }
            }
            baseWidth = Math.max(baseWidth, maxLen * 8);
        }

        return Math.min(baseWidth, MAX_COLUMN_WIDTH);
    }

    /**
     * 创建列头右键菜单
     */
    private ContextMenu createColumnContextMenu(String columnName) {
        ContextMenu menu = new ContextMenu();

        MenuItem filterItem = new MenuItem("按此列筛选");
        filterItem.setOnAction(e -> {
            searchColumnCombo.setValue(columnName);
            searchField.requestFocus();
        });

        MenuItem sortAscItem = new MenuItem("升序排列 ↑");
        sortAscItem.setOnAction(e -> sortByColumn(columnName, true));

        MenuItem sortDescItem = new MenuItem("降序排列 ↓");
        sortDescItem.setOnAction(e -> sortByColumn(columnName, false));

        MenuItem statsItem = new MenuItem("统计此列");
        statsItem.setOnAction(e -> showColumnStatistics(columnName));

        menu.getItems().addAll(filterItem, new SeparatorMenuItem(), sortAscItem, sortDescItem, new SeparatorMenuItem(), statsItem);
        return menu;
    }

    /**
     * 更新搜索字段下拉框
     */
    private void updateSearchColumnCombo() {
        searchColumnCombo.getItems().clear();
        searchColumnCombo.getItems().add("全部字段");
        searchColumnCombo.getItems().addAll(columnNames);
        searchColumnCombo.setValue("全部字段");
    }

    /**
     * 防抖搜索
     */
    private long lastSearchTime = 0;
    private void debounceSearch() {
        lastSearchTime = System.currentTimeMillis();
        searchExecutor.submit(() -> {
            try {
                Thread.sleep(200);  // 200ms 防抖
                if (System.currentTimeMillis() - lastSearchTime >= 200) {
                    Platform.runLater(this::performSearch);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 执行搜索
     */
    private void performSearch() {
        if (currentSearchText == null || currentSearchText.trim().isEmpty()) {
            filteredData.setAll(allData);
        } else {
            List<Map<String, Object>> filtered = filterData(allData);
            filteredData.setAll(filtered);
        }

        currentPage = 0;
        updatePagination();
        updateTableData();
        updateStats();
    }

    /**
     * 过滤数据
     */
    private List<Map<String, Object>> filterData(List<Map<String, Object>> source) {
        String keyword = currentSearchText.toLowerCase().trim();
        if (keyword.isEmpty()) {
            return new ArrayList<>(source);
        }

        return source.stream().filter(row -> {
            if ("全部字段".equals(currentSearchColumn)) {
                // 搜索所有字段
                for (Object val : row.values()) {
                    if (val != null && val.toString().toLowerCase().contains(keyword)) {
                        return true;
                    }
                }
                return false;
            } else {
                // 搜索指定字段
                Object val = row.get(currentSearchColumn);
                return val != null && val.toString().toLowerCase().contains(keyword);
            }
        }).collect(Collectors.toList());
    }

    /**
     * 更新分页控件
     */
    private void updatePagination() {
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredData.size() / pageSize));
        pagination.setPageCount(totalPages);
        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }
        pagination.setCurrentPageIndex(currentPage);
    }

    /**
     * 更新表格数据（当前页）
     */
    private void updateTableData() {
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, filteredData.size());

        if (start >= filteredData.size()) {
            tableView.setItems(FXCollections.observableArrayList());
        } else {
            List<Map<String, Object>> pageData = filteredData.subList(start, end);
            tableView.setItems(FXCollections.observableArrayList(pageData));
        }
    }

    /**
     * 更新统计信息
     */
    private void updateStats() {
        int total = allData.size();
        int filtered = filteredData.size();
        int pageStart = currentPage * pageSize + 1;
        int pageEnd = Math.min((currentPage + 1) * pageSize, filtered);

        if (total == filtered) {
            statsLabel.setText(String.format("共 %d 条 | 显示 %d-%d", total, pageStart, pageEnd));
        } else {
            statsLabel.setText(String.format("筛选 %d/%d 条 | 显示 %d-%d", filtered, total, pageStart, pageEnd));
        }
    }

    /**
     * 更新选择标签
     */
    private void updateSelectionLabel() {
        int selectedCells = tableView.getSelectionModel().getSelectedCells().size();
        if (selectedCells == 0) {
            selectionLabel.setText("未选中");
        } else if (selectedCells == 1) {
            selectionLabel.setText("已选中 1 个单元格");
        } else {
            selectionLabel.setText("已选中 " + selectedCells + " 个单元格");
        }
    }

    /**
     * 复制选中单元格
     */
    private void copySelectedCells() {
        StringBuilder sb = new StringBuilder();
        ObservableList<TablePosition> positions = tableView.getSelectionModel().getSelectedCells();

        int prevRow = -1;
        for (TablePosition<?, ?> pos : positions) {
            int row = pos.getRow();
            int col = pos.getColumn();

            if (row >= tableView.getItems().size() || col >= tableView.getColumns().size()) {
                continue;
            }

            Object cell = tableView.getColumns().get(col).getCellData(row);

            if (prevRow == row) {
                sb.append('\t');
            } else if (prevRow != -1) {
                sb.append('\n');
            }
            sb.append(cell != null ? cell.toString() : "");
            prevRow = row;
        }

        if (sb.length() > 0) {
            ClipboardContent content = new ClipboardContent();
            content.putString(sb.toString());
            Clipboard.getSystemClipboard().setContent(content);
            log.info("已复制 {} 个单元格到剪贴板", positions.size());
        }
    }

    /**
     * 按列排序
     */
    private void sortByColumn(String columnName, boolean ascending) {
        Comparator<Map<String, Object>> comparator = (a, b) -> {
            Object va = a.get(columnName);
            Object vb = b.get(columnName);

            if (va == null && vb == null) return 0;
            if (va == null) return ascending ? -1 : 1;
            if (vb == null) return ascending ? 1 : -1;

            // 尝试数值比较
            try {
                double da = Double.parseDouble(va.toString());
                double db = Double.parseDouble(vb.toString());
                return ascending ? Double.compare(da, db) : Double.compare(db, da);
            } catch (NumberFormatException e) {
                // 字符串比较
                int cmp = va.toString().compareToIgnoreCase(vb.toString());
                return ascending ? cmp : -cmp;
            }
        };

        filteredData.sort(comparator);
        updateTableData();
    }

    /**
     * 显示列统计
     */
    private void showColumnStatistics(String columnName) {
        Map<String, Integer> valueCounts = new LinkedHashMap<>();
        int nullCount = 0;
        double sum = 0;
        int numericCount = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (Map<String, Object> row : allData) {
            Object val = row.get(columnName);
            if (val == null || val.toString().trim().isEmpty()) {
                nullCount++;
            } else {
                String strVal = val.toString();
                valueCounts.merge(strVal, 1, Integer::sum);

                try {
                    double num = Double.parseDouble(strVal);
                    sum += num;
                    numericCount++;
                    min = Math.min(min, num);
                    max = Math.max(max, num);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // 构建统计信息
        StringBuilder sb = new StringBuilder();
        sb.append("=== 列统计: ").append(columnName).append(" ===\n\n");
        sb.append("总记录数: ").append(allData.size()).append("\n");
        sb.append("空值数: ").append(nullCount).append(" (").append(String.format("%.1f%%", 100.0 * nullCount / allData.size())).append(")\n");
        sb.append("唯一值数: ").append(valueCounts.size()).append("\n");

        if (numericCount > 0) {
            sb.append("\n数值统计:\n");
            sb.append("  数值数量: ").append(numericCount).append("\n");
            sb.append("  最小值: ").append(min).append("\n");
            sb.append("  最大值: ").append(max).append("\n");
            sb.append("  平均值: ").append(String.format("%.2f", sum / numericCount)).append("\n");
        }

        // TOP 10 值
        if (!valueCounts.isEmpty()) {
            sb.append("\nTOP 10 值:\n");
            valueCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(10)
                    .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("次\n"));
        }

        // 显示对话框
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("列统计");
        alert.setHeaderText(columnName + " 统计信息");

        TextArea textArea = new TextArea(sb.toString());
        textArea.setEditable(false);
        textArea.setFont(Font.font("Consolas", 12));
        textArea.setPrefRowCount(20);
        textArea.setPrefColumnCount(40);

        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }

    /**
     * 导出数据
     */
    private void exportData() {
        // 简单的TSV导出到剪贴板
        StringBuilder sb = new StringBuilder();

        // 表头
        sb.append(String.join("\t", columnNames)).append("\n");

        // 数据（当前筛选结果）
        for (Map<String, Object> row : filteredData) {
            List<String> values = new ArrayList<>();
            for (String col : columnNames) {
                Object val = row.get(col);
                values.add(val != null ? val.toString() : "");
            }
            sb.append(String.join("\t", values)).append("\n");
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("导出成功");
        alert.setHeaderText(null);
        alert.setContentText("已将 " + filteredData.size() + " 条数据复制到剪贴板（TSV格式）");
        alert.showAndWait();
    }

    // === Getters & Setters ===

    public TableView<Map<String, Object>> getTableView() {
        return tableView;
    }

    public void setOnRowDoubleClick(Consumer<Map<String, Object>> handler) {
        this.onRowDoubleClick = handler;
    }

    public void setOnSelectionChange(Consumer<List<Map<String, Object>>> handler) {
        this.onSelectionChange = handler;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int size) {
        this.pageSize = size;
        updatePagination();
        updateTableData();
    }

    public List<Map<String, Object>> getAllData() {
        return new ArrayList<>(allData);
    }

    public List<Map<String, Object>> getFilteredData() {
        return new ArrayList<>(filteredData);
    }

    public void refresh() {
        performSearch();
    }

    public void clear() {
        allData.clear();
        filteredData.clear();
        columnNames.clear();
        tableView.getColumns().clear();
        tableView.getItems().clear();
        updateStats();
    }

    /**
     * 释放资源
     */
    public void dispose() {
        searchExecutor.shutdownNow();
    }
}
