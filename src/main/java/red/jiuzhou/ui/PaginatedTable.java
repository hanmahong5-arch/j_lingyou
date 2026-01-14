package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import red.jiuzhou.analysis.aion.IdNameResolver;
import red.jiuzhou.dbxml.*;
import red.jiuzhou.agent.context.ContextCollector;
import red.jiuzhou.agent.context.DesignContext;
import red.jiuzhou.ui.components.OperationLogPanel;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.JSONRecord;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.util.YmlConfigUtil;
import red.jiuzhou.validation.DatabaseValidationService;
import red.jiuzhou.validation.DatabaseValidationService.Severity;
import red.jiuzhou.validation.DatabaseValidationService.ValidationIssue;
import red.jiuzhou.xmltosql.XmlProcess;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
/**
 * @className: red.jiuzhou.ui.PaginatedTable.java
 * @description: 分页表格
 * @author: yanxq
 * @date:  2025-04-15 20:43
 * @version V1.0
 */
public class PaginatedTable{

    private static final Logger log = LoggerFactory.getLogger(PaginatedTable.class);
    private String tabName;

    private String tabFilePath;

    private TableView<Map<String, Object>> tableView;
    // 总行数
    private int totalRows;
    private Pagination pagination;
    private TextField searchField;
    private Label progressLabel;
    private  ProgressBar progressBar;

    private  String mapType;
    // 任务执行器（虚拟线程）
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private List<String> filterList;

    // 操作日志面板
    private OperationLogPanel logPanel;

    // AI操作回调（用于集成AI助手）
    private BiConsumer<DesignContext, String> onAiOperation;

    // 上下文收集器
    private final ContextCollector contextCollector = new ContextCollector();

    public VBox createVbox(TabPane tabPane, Tab tab) {
        long startTime = System.currentTimeMillis();
        log.info("start time {}", startTime);
        String tabName = tab.getText();
        try {
            filterList = new ArrayList<>();
            //System.out.println("tabPane::::" + tabPane);
            if(tabName == null || tabName.isEmpty()){
                return new VBox();
            }
            this.tabName = tabName;
            this.tabFilePath = tab.getUserData() + "";
            log.info("tabFilePath init: {}", tabFilePath);

            // 初始化为0，稍后异步加载
            totalRows = 0;

            // 创建 TableView
            tableView = new TableView<>();
            // 单元格选择
            tableView.getSelectionModel().setCellSelectionEnabled(true);
            // 多选
            tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            tableView.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.C && event.isControlDown()) {
                    StringBuilder clipboardString = new StringBuilder();
                    ObservableList<TablePosition> posList = tableView.getSelectionModel().getSelectedCells();

                    int prevRow = -1;
                    for (TablePosition position : posList) {
                        int row = position.getRow();
                        int col = position.getColumn();
                        Object cell = tableView.getColumns().get(col).getCellData(row);

                        if (prevRow == row) {
                            clipboardString.append('\t'); // 同一行：列之间用 tab 分隔
                        } else if (prevRow != -1) {
                            clipboardString.append('\n'); // 不同行：换行
                        }
                        clipboardString.append(cell != null ? cell.toString() : "");
                        prevRow = row;
                    }

                    final ClipboardContent clipboardContent = new ClipboardContent();
                    clipboardContent.putString(clipboardString.toString());
                    Clipboard.getSystemClipboard().setContent(clipboardContent);
                }
            });

            // 设置行工厂，支持右键菜单（包含AI操作）
            setupRowContextMenu();

            createColumns();

            // 创建查询框和按钮
            searchField = new TextField();
            searchField.setPromptText("输入 ID 进行查询");

            Button searchButton = new Button("搜索");
            Button clearFilterButton = new Button("清除筛选");
            Button xmlToDb = new Button("xmlToDb");
            // --- 新增：带有字段选择的 XML 导入按钮 ---
            Button xmlToDbWithField = new Button("xmlToDbWithField");
            // 点击时调用
            xmlToDbWithField.setOnAction(e -> {
                String filePath = ensureXmlExtension((String) tab.getUserData());
                showColumnSelectionForXmlToDb(filePath);
            });

            //Button choosexmlToDb = new Button("chooseXmlToDb");
            Button dbToXml = new Button("dbToXml");
            Button ddlBun = new Button("DDL生成");
            if("world".equals(tabName)){
                ddlBun.setDisable(true);
            }
            ddlBun.setOnAction(e -> {
                // 获取文件路径，确保有.xml扩展名（但不重复添加）
                String userData = (String) tab.getUserData();
                String selectedFile = userData;
                if (selectedFile != null && !selectedFile.toLowerCase().endsWith(".xml")) {
                    selectedFile = selectedFile + ".xml";
                }

                log.info("选择文件：{}", selectedFile);
                logPanel.info("开始生成DDL，文件: " + selectedFile);
                try {
                    String sqlDdlFilePath = XmlProcess.parseXmlFile(selectedFile);
                    //执行sql文件
                    log.info("执行sql文件：{}", sqlDdlFilePath);
                    logPanel.info("执行SQL脚本: " + sqlDdlFilePath);
                    DatabaseUtil.executeSqlScript(sqlDdlFilePath);
                    log.info("生成DDL成功");
                    logPanel.success("DDL生成并建表成功");
                } catch (SQLException ex) {
                    logPanel.error("DDL生成失败", ex);
                    throw new RuntimeException(ex);
                }
            });
            // 绑定查询功能
            searchButton.setOnAction(e -> searchById());
            clearFilterButton.setOnAction(e -> {
                filterList.clear();
                refreshTotalRowsAsync();
                logPanel.info("已清除所有筛选条件，正在刷新数据...");
            });
            xmlToDb.setOnAction(e -> {
                String filePath = ensureXmlExtension((String) tab.getUserData());
                logPanel.info("开始导入XML到数据库: " + filePath);
                xmlToDb(filePath, null, null);
            });
            dbToXml.setOnAction(e -> {
                logPanel.info("开始导出数据库到XML");
                dbToXml();
            });
            progressLabel = new Label("");

            // 数据校验按钮 - 一键检查当前表的数据质量
            Button validateBtn = new Button("✓ 校验");
            validateBtn.setTooltip(new Tooltip("一键检查当前表的数据质量\n• 引用完整性\n• 空值检测\n• 重复数据\n• 异常值"));
            validateBtn.setStyle("-fx-background-color: #E8F5E9;");
            validateBtn.setOnAction(e -> runTableValidation());

            // 按钮区域
            HBox searchBox = new HBox(10, searchField, searchButton, clearFilterButton, validateBtn, xmlToDb, xmlToDbWithField, dbToXml, ddlBun);
            searchBox.setPadding(new Insets(10));
            VBox progressBox = null;
            if("world".equals(tabName)){
                Path path = Paths.get(tabFilePath);
                mapType = path.getName(path.getNameCount() - 2).toString();
                log.info("msgType:{}", mapType);
            }
            progressBox = new VBox(5, progressLabel, new Region());
            progressBox.setPadding(new Insets(10));

            // 创建 Pagination 控件（初始页数设为1，稍后异步加载总行数后更新）
            pagination = new Pagination(1, 0);
            pagination.setMaxPageIndicatorCount(10);
            pagination.setPageFactory(this::createPage);

            // 创建操作日志面板
            logPanel = new OperationLogPanel();
            logPanel.setLogAreaHeight(250); // 设置初始高度，能显示至少10条日志

            VBox rightControl = new VBox();
            rightControl.getChildren().add(tabPane);
            // 添加到右侧面板
            rightControl.getChildren().addAll(searchBox, progressBox);
            rightControl.getChildren().add(pagination);
            rightControl.getChildren().add(logPanel); // 添加日志面板
            //VBox vBox = new VBox(searchBox, progressBox, pagination);

            // 添加初始化日志
            logPanel.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logPanel.success("数据页签已加载完成");
            logPanel.info("表名: " + tabName);
            logPanel.info("文件路径: " + tabFilePath);
            if ("world".equals(tabName) && mapType != null) {
                logPanel.info("地图类型: " + mapType);
            }
            logPanel.info("页面大小: " + DatabaseUtil.ROWS_PER_PAGE + " 行/页");
            logPanel.info("正在统计总行数...");
            logPanel.info("加载耗时: " + (System.currentTimeMillis() - startTime) + " ms");

            // ==================== 异步加载总行数（性能优化）====================
            // 在后台线程查询总行数，避免阻塞UI
            javafx.concurrent.Task<Integer> countTask = new javafx.concurrent.Task<>() {
                @Override
                protected Integer call() throws Exception {
                    return DatabaseUtil.getTotalRowCount(tabName + buildWhereClause());
                }

                @Override
                protected void succeeded() {
                    totalRows = getValue();
                    int pageCount = (int) Math.ceil((double) totalRows / DatabaseUtil.ROWS_PER_PAGE);

                    Platform.runLater(() -> {
                        // 更新分页控件
                        pagination.setPageCount(Math.max(1, pageCount));

                        // 更新日志
                        logPanel.success(String.format("总行数统计完成: %,d 行", totalRows));
                        logPanel.info("总页数: " + pageCount);
                        logPanel.success("系统就绪，可以开始操作");
                        logPanel.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    });
                }

                @Override
                protected void failed() {
                    Throwable ex = getException();
                    log.error("获取总行数失败: {}", ex.getMessage(), ex);

                    Platform.runLater(() -> {
                        logPanel.error("获取总行数失败: " + ex.getMessage());
                        logPanel.warning("使用默认分页，可能显示不完整");
                        logPanel.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                        // 设置一个默认值
                        totalRows = 1000;
                        pagination.setPageCount(10);
                    });
                }
            };

            // 启动异步任务
            Thread countThread = new Thread(countTask);
            countThread.setDaemon(true);
            countThread.start();

            return rightControl;
        } catch (Exception e) {
            showError(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void createColumns() {
        // 避免重复添加列
        tableView.getColumns().clear();
        List<Map<String, Object>> sampleData = null;
        try  {
            // 优化：只使用 LIMIT 1 获取列结构，加载更快
            // PostgreSQL: 表名使用双引号
            sampleData = DatabaseUtil.getJdbcTemplate()
                    .queryForList("SELECT * FROM \"" + tabName + "\" LIMIT 1");
        } catch (Exception e) {
            log.error("获取数据失败:{}", e.getMessage());
            if (logPanel != null) {
                logPanel.error("获取表数据失败: " + e.getMessage());
            }
            //showError(e.getMessage());
            //throw new RuntimeException(e);
        }

        if (sampleData != null && !sampleData.isEmpty()) {
            log.info("sampleData_size:{}", sampleData.size());
            Map<String, Object> firstRow = sampleData.get(0);

            // ID->NAME解析器实例
            final IdNameResolver idNameResolver = IdNameResolver.getInstance();

            // 创建列
            for (String columnName : firstRow.keySet()) {
                TableColumn<Map<String, Object>, Object> column = new TableColumn<>(columnName);
                column.setCellValueFactory(cellData ->
                        new ReadOnlyObjectWrapper<>(cellData.getValue().get(columnName))
                );

                // 检测是否是ID引用字段，添加NAME显示
                final boolean isIdField = idNameResolver.isIdField(columnName);
                if (isIdField) {
                    final String colName = columnName;
                    column.setCellFactory(col -> new TableCell<Map<String, Object>, Object>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty || item == null) {
                                setText(null);
                                setTooltip(null);
                            } else {
                                String idValue = item.toString();
                                String formattedValue = idNameResolver.formatIdWithName(colName, idValue);
                                setText(formattedValue);
                                // 如果有NAME，添加详细提示
                                if (!formattedValue.equals(idValue)) {
                                    String name = idNameResolver.resolveByField(colName, idValue);
                                    Tooltip tip = new Tooltip(
                                        "ID: " + idValue + "\n" +
                                        "名称: " + name + "\n" +
                                        "字段: " + colName
                                    );
                                    setTooltip(tip);
                                }
                            }
                        }
                    });
                    // 标记ID列（可选：改变标题样式）
                    column.setText(columnName + " \u279C");  // 添加箭头标记
                }

                // 右键菜单
                ContextMenu contextMenu = new ContextMenu();
                MenuItem showPopup = new MenuItem("查看");
                MenuItem clearFilter = new MenuItem("清除条件");
                showPopup.setOnAction(event -> showColumnDetails(columnName));
                clearFilter.setOnAction(event -> {
                    filterList.removeIf(item -> item.startsWith(columnName + "="));
                    refreshTotalRowsAsync();
                    if (logPanel != null) {
                        logPanel.info("已清除 " + columnName + " 列的筛选条件");
                    }
                });

                contextMenu.getItems().add(showPopup);
                contextMenu.getItems().add(clearFilter);
                column.setContextMenu(contextMenu);

                tableView.getColumns().add(column);
            }

            // 使用 ObservableList 来绑定数据
            ObservableList<Map<String, Object>> observableData = FXCollections.observableArrayList(sampleData);

            // 更新 TableView 数据
            tableView.setItems(observableData);
        }
    }

    public String buildWhereClause() {
        if (filterList == null || filterList.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner(" AND ");
        for (String condition : filterList) {
            joiner.add(condition);
        }

        return " WHERE " + joiner.toString();
    }
    /**
     * 弹出一个窗口，展示两列小列表
     */
    private void showColumnDetails(String columnName) {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("列详情：" + columnName);

        // 创建 TableView
        TableView<EnumQuery.DataRow> tableView = new TableView<>();

        // "值" 列（文本类型）
        TableColumn<EnumQuery.DataRow, String> valueColumn = new TableColumn<>("值");
        valueColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getValue()));

        // "次数" 列（int 类型 + 可排序）
        TableColumn<EnumQuery.DataRow, Number> countColumn = new TableColumn<>("次数");
        countColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getCount()));
        // 确保支持排序
        countColumn.setSortable(true);


        tableView.getColumns().addAll(valueColumn, countColumn);
        List<EnumQuery.DataRow> dataRows = new EnumQuery().getDataRows(tabName, columnName);
        ObservableList<EnumQuery.DataRow> observableList = FXCollections.observableArrayList(dataRows);

        tableView.setItems(observableList);

        // 添加双击事件监听
        tableView.setRowFactory(tv -> {
            TableRow<EnumQuery.DataRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    EnumQuery.DataRow rowData = row.getItem();
                    String condition = columnName + "='" + rowData.getValue() + "'";
                    filterList.removeIf(item -> item.startsWith(columnName + "="));
                    filterList.add(condition);
                    refreshTotalRowsAsync();
                    if (logPanel != null) {
                        logPanel.info("已添加筛选条件: " + condition);
                    }
                    popupStage.close();
                }
            });
            return row;
        });

        // 布局
        VBox layout = new VBox(10, tableView);
        layout.setPadding(new Insets(10));

        Scene scene = new Scene(layout, 500, 350);
        popupStage.setScene(scene);
        popupStage.show();
    }


    /**
     * 创建某个分页的数据页面（异步加载优化）
     */
    private VBox createPage(int pageIndex) {
        // 创建加载指示器
        ProgressIndicator loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(50, 50);
        Label loadingLabel = new Label("正在加载数据...");
        loadingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        VBox loadingBox = new VBox(10, loadingIndicator, loadingLabel);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setPrefHeight(400);

        VBox pageBox = new VBox(loadingBox);

        // 在后台线程异步加载数据
        javafx.concurrent.Task<List<Map<String, Object>>> loadTask = new javafx.concurrent.Task<>() {
            @Override
            protected List<Map<String, Object>> call() throws Exception {
                return DatabaseUtil.fetchPageData(tabName, pageIndex, buildWhereClause(), tabFilePath);
            }

            @Override
            protected void succeeded() {
                List<Map<String, Object>> data = getValue();
                // 在 JavaFX 线程更新 UI
                Platform.runLater(() -> {
                    ObservableList<Map<String, Object>> observableData =
                            FXCollections.observableArrayList(data);
                    tableView.setItems(observableData);

                    // 移除加载指示器，显示表格
                    pageBox.getChildren().clear();
                    pageBox.getChildren().add(tableView);

                    if (logPanel != null) {
                        logPanel.info(String.format("加载第 %d 页数据完成，共 %d 条记录",
                                pageIndex + 1, data.size()));
                    }
                });
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                log.error("加载数据失败: {}", ex.getMessage(), ex);

                Platform.runLater(() -> {
                    // 显示错误信息
                    Label errorLabel = new Label("❌ 数据加载失败");
                    errorLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #f44336; -fx-font-weight: bold;");

                    Label errorDetail = new Label(ex.getMessage());
                    errorDetail.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
                    errorDetail.setWrapText(true);
                    errorDetail.setMaxWidth(600);

                    Button retryButton = new Button("🔄 重试");
                    retryButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
                    retryButton.setOnAction(e -> {
                        // 重新加载当前页
                        pagination.setPageFactory(PaginatedTable.this::createPage);
                    });

                    VBox errorBox = new VBox(15, errorLabel, errorDetail, retryButton);
                    errorBox.setAlignment(Pos.CENTER);
                    errorBox.setPadding(new Insets(50));

                    pageBox.getChildren().clear();
                    pageBox.getChildren().add(errorBox);

                    if (logPanel != null) {
                        logPanel.error("加载第 " + (pageIndex + 1) + " 页数据失败: " + ex.getMessage());
                    }
                });
            }
        };

        // 在后台线程池执行任务
        Thread loadThread = new Thread(loadTask);
        loadThread.setDaemon(true);
        loadThread.start();

        return pageBox;
    }

    /**
     * 按 ID 查询
     */
    private void searchById() {
        String id = searchField.getText().trim();
        if (id.isEmpty()) {
            showError("请输入有效的 ID 进行查询！");
            logPanel.warning("搜索失败: 请输入有效的ID");
            return;
        }

        String sql = "SELECT * FROM " + tabName + " WHERE id = ?";
        List<Map<String, Object>> result;
        try {
            logPanel.info("正在搜索ID: " + id);
            result = DatabaseUtil.getJdbcTemplate().queryForList(sql, id);
            if (result.isEmpty()) {
                showError("未找到对应 ID 的数据！");
                logPanel.warning("未找到ID为 " + id + " 的数据");
            } else {
                logPanel.success("找到 " + result.size() + " 条记录，ID: " + id);
            }
        } catch (Exception e) {
            showError("查询失败: " + e.getMessage());
            logPanel.error("查询失败: " + e.getMessage());
            return;
        }

        // 更新 TableView 数据
        ObservableList<Map<String, Object>> observableData = FXCollections.observableArrayList(result);
        tableView.setItems(observableData);
    }

    /**
     * 进度条导入数据（模拟）
     */
    private void xmlToDb(String filePath, List<String> selectedColumns, String aiModule) {
        tabIsExist();
        if ("world".equals(tabName)) {
            if (mapType == null || mapType.trim().isEmpty()) {
                logPanel.error("请选择地图类型");
                throw new RuntimeException("请选择地图类型");
            }
        }
        Stage progressStage = createProgressDialog("正在导入XML至数据库...");

        executor.execute(() -> {
            updateProgress(0, "导入数据中...");
            logPanel.info("XML导入任务开始，文件: " + filePath);
            XmlToDbGenerator xmlToDbGenerator = new XmlToDbGenerator(tabName, mapType, filePath, tabFilePath);

            AtomicReference<Throwable> threadException = new AtomicReference<>();
            // 启动线程
            Thread importThread = new Thread(() -> {
                try {
                    xmlToDbGenerator.xmlTodb(aiModule, selectedColumns);
                } catch (Throwable t) {
                    threadException.set(t);
                }
            });
            importThread.start();

            // 等待导入线程结束
            while (importThread.isAlive()) {
                try {
                    Thread.sleep(1000);
                    double progress = xmlToDbGenerator.getProgress();
                    updateProgress(progress, "导入进度: " + (progress * 100) + "%");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showErrorAndClose(progressStage, "线程被中断: " + e.getMessage());
                    return;
                }
            }

            // 导入线程已经结束，此时判断是否抛出异常
            if (threadException.get() != null) {
                String msg = threadException.get().getMessage();

                log.error("导入失败: " + JSONRecord.getErrorMsg(threadException.get()));
                logPanel.error("XML导入失败: " + threadException.get().getMessage());
                Pattern colPattern = Pattern.compile("Data too long for column '(.+?)'");
                Pattern tablePattern = Pattern.compile("(?i)insert\\s+into\\s+[`]?([a-zA-Z0-9_]+)[`]?");

                Matcher colMatcher = colPattern.matcher(msg);
                Matcher tableMatcher = tablePattern.matcher(msg);
                if (colMatcher.find() && tableMatcher.find()) {
                    String columnName = colMatcher.group(1);
                    String realTableName = tableMatcher.group(1);
                    // 获取当前长度
                    String infoSchemaSql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE table_schema = current_schema() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
                    Integer oldLength = DatabaseUtil.getJdbcTemplate().queryForObject(infoSchemaSql, new Object[]{realTableName, columnName}, Integer.class);

                    if (oldLength == null || oldLength <= 0) {
                        throw new RuntimeException("无法获取字段原始长度");
                    }

                    int newLength = oldLength * 2;

                    String alterSql = String.format("ALTER TABLE `%s` MODIFY COLUMN `%s` VARCHAR(%d)", realTableName, columnName, newLength);
                    System.out.println("字段过长，尝试修改字段长度并重试：" + alterSql);
                    logPanel.warning("字段过长，自动扩展字段: " + columnName + " -> " + newLength);
                    DatabaseUtil.getJdbcTemplate().execute(alterSql);
                    // 不跳过当前记录，重试
                }
                showErrorAndClose(progressStage, "导入失败: " + threadException.get().getMessage());
                return;
            }

            updateProgress(1, "导入完成");
            logPanel.success("XML导入完成: " + filePath);
            Platform.runLater(progressStage::close);
        });
    }
    // 错误提示 + 关闭窗口的封装
    private void showErrorAndClose(Stage stage, String message) {
        Platform.runLater(() -> {
            stage.close();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText("导入过程中出现错误");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    /**
     * 进度条导出数据
     */
    private void dbToXml() {
        tabIsExist();
        if("world".equals(tabName)){
            if(mapType == null || mapType.trim().isEmpty()){
                logPanel.error("请选择地图类型");
                throw new RuntimeException("请选择地图类型");
            }
        }

        // ==================== 数据量预警机制（2025-12-29新增）====================
        // 导出前检查数据量，大表需要二次确认，避免误操作
        try {
            int rowCount = DatabaseUtil.getTotalRowCount(tabName + buildWhereClause());

            // 数据量预警阈值
            final int WARNING_THRESHOLD = 10000;  // 1万行
            final int DANGER_THRESHOLD = 50000;   // 5万行

            if (rowCount > WARNING_THRESHOLD) {
                String warningMsg;
                String detailMsg;

                if (rowCount > DANGER_THRESHOLD) {
                    warningMsg = String.format("⚠️ 数据量超大警告\n\n" +
                        "表 %s 包含 %,d 行数据（超过 %,d 行）\n" +
                        "导出可能需要较长时间（预计 %d 分钟以上）。\n\n" +
                        "是否确认导出？",
                        tabName, rowCount, DANGER_THRESHOLD, rowCount / 10000);
                    detailMsg = String.format("💡 提示：\n" +
                        "• 建议使用筛选条件缩小范围\n" +
                        "• 或者分批导出数据\n" +
                        "• 导出过程中请勿关闭应用");
                } else {
                    warningMsg = String.format("⚠️ 数据量较大提醒\n\n" +
                        "表 %s 包含 %,d 行数据\n" +
                        "导出可能需要 %d 秒左右。\n\n" +
                        "是否继续？",
                        tabName, rowCount, rowCount / 100);
                    detailMsg = "💡 提示：可以使用筛选功能缩小导出范围";
                }

                // 显示确认对话框
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("数据量确认");
                    alert.setHeaderText(warningMsg);
                    alert.setContentText(detailMsg);

                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        // 用户确认后继续导出
                        logPanel.info(String.format("用户确认导出大表：%s（%,d 行）", tabName, rowCount));
                        performExport();
                    } else {
                        logPanel.info("用户取消导出操作");
                    }
                });
                return; // 等待用户确认，不直接执行
            } else {
                // 数据量不大，直接导出
                logPanel.info(String.format("准备导出表 %s（%,d 行）", tabName, rowCount));
            }
        } catch (Exception e) {
            log.warn("获取数据量失败，跳过预警检查: {}", e.getMessage());
        }
        // ===========================================================

        performExport();
    }

    /**
     * 执行实际的导出操作（提取为独立方法，支持预警后调用）
     */
    private void performExport() {
        Stage progressStage = createProgressDialog("正在导出数据至XML...");
        executor.execute(() -> {
            updateProgress(0, "导出数据中...");
            logPanel.info("数据库导出任务开始，表: " + tabName);
            Thread importThread = null;
            final String[] exportedFilePath = {null}; // 存储导出的文件路径
            final AtomicReference<Throwable> threadException = new AtomicReference<>();

            try {
                if("world".equals(tabName)){
                    WorldDbToXmlGenerator dbToXmlGenerator = new WorldDbToXmlGenerator(tabName, mapType, tabFilePath);
                    // 在新线程中执行 dbToXml 导出，并获取返回的文件路径
                    importThread = new Thread(() -> {
                        try {
                            exportedFilePath[0] = dbToXmlGenerator.processAndMerge();
                        } catch (Throwable t) {
                            log.error("导出过程发生异常: {}", JSONRecord.getErrorMsg(t));
                            threadException.set(t);
                        }
                    });
                    importThread.start();
                    while (importThread.isAlive()){
                        try {
                            Thread.sleep(500);
                            // 检查是否有异常
                            if (threadException.get() != null) {
                                break;
                            }
                            double progress = dbToXmlGenerator.getProgress();
                            updateProgress(progress, "导出进度: " + String.format("%.2f", (progress * 100)) + "%");
                            if(progress == 1){
                                break;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logPanel.error("导出任务被中断", e);
                            showErrorAndClose(progressStage, "导出任务被中断: " + e.getMessage());
                            return;
                        }
                    }
                }else{
                    DbToXmlGenerator dbToXmlGenerator = new DbToXmlGenerator(tabName, mapType, tabFilePath);
                    importThread = new Thread(() -> {
                        try {
                            exportedFilePath[0] = dbToXmlGenerator.processAndMerge();
                        } catch (Throwable t) {
                            log.error("导出过程发生异常: {}", JSONRecord.getErrorMsg(t));
                            threadException.set(t);
                        }
                    });
                    importThread.start();
                    while (importThread.isAlive()){
                        try {
                            Thread.sleep(500);
                            // 检查是否有异常
                            if (threadException.get() != null) {
                                break;
                            }
                            double progress = dbToXmlGenerator.getProgress();
                            updateProgress(progress, "导出进度: " + String.format("%.2f", (progress * 100)) + "%");
                            if(progress == 1){
                                break;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logPanel.error("导出任务被中断", e);
                            showErrorAndClose(progressStage, "导出任务被中断: " + e.getMessage());
                            return;
                        }
                    }
                }

                // 检查导出线程是否抛出异常
                if (threadException.get() != null) {
                    String errorMsg = threadException.get().getMessage();
                    log.error("导出失败: " + JSONRecord.getErrorMsg(threadException.get()));
                    logPanel.error("数据库导出失败: " + errorMsg);
                    showErrorAndClose(progressStage, "导出失败: " + errorMsg);
                    return;
                }

                updateProgress(1, "导出完成");
                logPanel.success("数据库导出完成，表: " + tabName);
                if (exportedFilePath[0] != null) {
                    logPanel.success("文件已保存到: " + exportedFilePath[0]);
                }
                Platform.runLater(progressStage::close);
            } catch (Exception e) {
                log.error("导出任务执行异常: {}", JSONRecord.getErrorMsg(e));
                logPanel.error("导出任务执行异常: " + e.getMessage());
                showErrorAndClose(progressStage, "导出失败: " + e.getMessage());
            }
        });
    }

    /**
     * 创建进度条弹窗
     */
    private Stage createProgressDialog(String title) {
        Stage progressStage = new Stage();
        progressStage.setTitle(title);
        progressStage.setResizable(false);

        progressBar = new ProgressBar(0);

        progressBar.setPrefWidth(300);
        progressLabel = new Label("请稍候...");

        VBox progressBox = new VBox(10, progressLabel, progressBar);
        progressBox.setPadding(new Insets(10));
        progressBox.setAlignment(Pos.CENTER);

        Scene scene = new Scene(progressBox, 350, 120);
        progressStage.setScene(scene);
        progressStage.show();

        return progressStage;
    }


    /**
     * 更新进度条
     */
    private void updateProgress(double progress, String message) {
        Platform.runLater(() -> {
            double roundedValue = Math.round(progress * 100.0) / 100.0;
            progressBar.setProgress(roundedValue);
            progressLabel.setText(message);
        });
    }

    public void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误提示");
        alert.setHeaderText("发生异常");
        alert.setContentText(message);
        alert.show();
    }

    private void tabIsExist(){
        try {
            TableConf tale = TabConfLoad.getTale(tabName, tabFilePath);
        } catch (Exception e) {
            String message = e.getMessage();
            if(message.contains("表配置文件不存在")){
                message = "该表配置不存在，请先执行\"DDL生成\"";
            }
            showError(message);
            throw new RuntimeException(message);
        }
    }

    /**
     * 确保文件路径有.xml扩展名（但不会重复添加）
     * 遵循XML-Only设计原则：只处理XML文件，其他文件被忽略
     *
     * @param filePath 文件路径
     * @return 带有.xml扩展名的文件路径
     */
    private String ensureXmlExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return filePath;
        }
        // 如果已经有.xml扩展名，直接返回
        if (filePath.toLowerCase().endsWith(".xml")) {
            return filePath;
        }
        // 添加.xml扩展名
        return filePath + ".xml";
    }

    /**
     * 显示一个弹出框，让用户选择要ai改写的列。
     * 选中的列将传递给 xmlTodbWithSelectedFields 方法。
     */
    /*private void showColumnSelectionForXmlToDb(String xmlFile) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("选择AI改写的列");

        VBox dialogVBox = new VBox(10);
        dialogVBox.setPadding(new Insets(10));

        // --- 模型选择下拉框 ---
        Label modelLabel = new Label("选择AI模型：");
        ComboBox<String> modelComboBox = new ComboBox<>();
        List<String> modelKeys = YamlUtils.loadAiModelKeys("application.yml");

        if (modelKeys.isEmpty()) {
            showError("未从配置文件中读取到 AI 模型 key，请检查 配置文件！");
            dialogStage.close();
            return;
        }

        modelComboBox.getItems().addAll(modelKeys);
        modelComboBox.setValue(modelKeys.get(0));

        HBox modelBox = new HBox(10, modelLabel, modelComboBox);
        modelBox.setAlignment(Pos.CENTER_LEFT);
        dialogVBox.getChildren().add(modelBox);

        // 用于存储列名和对应 CheckBox 的映射
        Map<String, CheckBox> columnCheckBoxes = new LinkedHashMap<>();

        List<String> allColumnNames = tableView.getColumns().stream()
                .map(TableColumn::getText)
                .collect(Collectors.toList());

        if (allColumnNames.isEmpty()) {
            try {
                allColumnNames = DatabaseUtil.getColumnNamesFromDb(tabName);
                if (allColumnNames.isEmpty()) {
                    showError("数据库表 [" + tabName + "] 中未找到任何列！");
                    dialogStage.close();
                    return;
                }
            } catch (Exception e) {
                showError("无法读取数据库表 [" + tabName + "]，请确认表是否存在！");
                e.printStackTrace();
                dialogStage.close();
                return;
            }
        }

        VBox checkboxesContainer = new VBox(5);
        for (String colName : allColumnNames) {
            CheckBox checkBox = new CheckBox(colName);
            checkBox.setSelected(false); // 默认不选中
            columnCheckBoxes.put(colName, checkBox);
            checkboxesContainer.getChildren().add(checkBox);

            final String currentColumnName = colName;
            checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    boolean isValid = validateColumnSelection(currentColumnName);
                    if (!isValid) {
                        Platform.runLater(() -> {
                            checkBox.setSelected(false);
                            showWarning("列 '" + currentColumnName + "' 提示词未配置，无法勾选。");
                        });
                    }
                }
            });
        }

        ScrollPane scrollPane = new ScrollPane(checkboxesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(Math.min(300, checkboxesContainer.getChildren().size() * 30));

        dialogVBox.getChildren().add(scrollPane);

        // --- 底部按钮区域 ---
        Button confirmButton = new Button("确定");
        confirmButton.setOnAction(event -> {
            List<String> selectedColumns = new ArrayList<>();
            columnCheckBoxes.forEach((colName, checkBox) -> {
                if (checkBox.isSelected()) {
                    selectedColumns.add(colName);
                }
            });

            if (selectedColumns.isEmpty()) {
                showError("请至少选择一列进行导入！");
                return;
            }

            String selectedModel = modelComboBox.getValue();
            log.info("用户从文件 [{}] 中选择了列：{}，使用模型：{}", xmlFile, selectedColumns, selectedModel);

            xmlToDb(xmlFile, selectedColumns, selectedModel); // 调用带模型参数的方法
            dialogStage.close();
        });

        Button cancelButton = new Button("取消");
        cancelButton.setOnAction(event -> dialogStage.close());

        HBox buttonBox = new HBox(10, confirmButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        dialogVBox.getChildren().add(buttonBox);

        Scene scene = new Scene(dialogVBox, 400, Region.USE_COMPUTED_SIZE);
        dialogStage.setScene(scene);
        dialogStage.sizeToScene();
        dialogStage.showAndWait();
    }*/

    private void showColumnSelectionForXmlToDb(String xmlFile) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("选择AI改写的列");

        VBox dialogVBox = new VBox(10);
        dialogVBox.setPadding(new Insets(10));
        // 设置对话框宽度，确保能显示完整
        dialogStage.setWidth(1100);
        dialogStage.setMinWidth(1100);

        // VBox设置合适宽度
        dialogVBox.setPrefWidth(1100);
        // --- 模型选择下拉框 ---
        Label modelLabel = new Label("选择AI模型：");
        ComboBox<String> modelComboBox = new ComboBox<>();
        List<String> modelKeys = YamlUtils.loadAiModelKeys("application.yml");

        if (modelKeys.isEmpty()) {
            showError("未从配置文件中读取到 AI 模型 key，请检查 配置文件！");
            dialogStage.close();
            return;
        }

        modelComboBox.getItems().addAll(modelKeys);
        modelComboBox.setValue(modelKeys.get(0));

        HBox modelBox = new HBox(10, modelLabel, modelComboBox);
        modelBox.setAlignment(Pos.CENTER_LEFT);
        dialogVBox.getChildren().add(modelBox);

        // --- 获取所有列名 ---
        List<String> allColumnNames = tableView.getColumns().stream()
                .map(TableColumn::getText)
                .collect(Collectors.toList());

        if (allColumnNames.isEmpty()) {
            try {
                allColumnNames = DatabaseUtil.getColumnNamesFromDb(tabName);
                if (allColumnNames.isEmpty()) {
                    showError("数据库表 [" + tabName + "] 中未找到任何列！");
                    dialogStage.close();
                    return;
                }
            } catch (Exception e) {
                showError("无法读取数据库表 [" + tabName + "]，请确认表是否存在！");
                e.printStackTrace();
                dialogStage.close();
                return;
            }
        }

        // --- 表格显示列名 & 提示词 ---
        TableView<ColumnPrompt> table = new TableView<>();
        table.setEditable(true);
        // 让table宽度跟VBox宽度绑定，随窗口变动
        table.prefWidthProperty().bind(dialogVBox.widthProperty());

        // 勾选列
        TableColumn<ColumnPrompt, Boolean> selectCol = new TableColumn<>("选择");
        selectCol.setCellValueFactory(param -> param.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(60);
        // 假设是你初始化 TableView 时，给选择列的复选框监听：
        selectCol.setCellFactory(tc -> {
            CheckBoxTableCell<ColumnPrompt, Boolean> cell = new CheckBoxTableCell<>(index -> {
                BooleanProperty selected = table.getItems().get(index).selectedProperty();
                // 监听selected属性变化
                selected.addListener((obs, wasSelected, isNowSelected) -> {
                    if (isNowSelected) {
                        ColumnPrompt rowItem = table.getItems().get(index);

                        if (rowItem.getPrompt() == null || rowItem.getPrompt().isEmpty()) {
                            String property = YamlUtils.getProperty("ai.promptKey." + tabName + "@" + rowItem.getName());
                            rowItem.setPrompt(property);
                        }
                    }
                });
                return selected;
            });
            return cell;
        });

        // 列名
        TableColumn<ColumnPrompt, String> nameCol = new TableColumn<>("列名");
        nameCol.setCellValueFactory(param -> param.getValue().nameProperty());
        nameCol.setEditable(false);


        // 提示词（可下拉、可输入、带校验）
        TableColumn<ColumnPrompt, String> promptCol = new TableColumn<>("提示词");
        List<String> defaultPrompts = YamlUtils.loadPromptListFromYaml("ai.promptKey.common");
        ObservableList<String> promptOptions = FXCollections.observableArrayList(defaultPrompts);
        promptCol.setPrefWidth(870);
        promptCol.setCellValueFactory(param -> param.getValue().promptProperty());
        promptCol.setCellFactory(col -> {
            TableCell<ColumnPrompt, String> cell = new TableCell<ColumnPrompt, String>() {
                private final ComboBox<String> comboBox = new ComboBox<>(promptOptions);
                {
                    comboBox.setEditable(true);
                    // 宽度绑定：ComboBox宽度 = 当前列宽度 - 10 (留出一点边距)
                    comboBox.prefWidthProperty().bind(promptCol.widthProperty().subtract(10));
                    comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                        if (!isEmpty()) {
                            ColumnPrompt rowItem = getTableView().getItems().get(getIndex());
                            rowItem.setPrompt(newVal);
                        }
                    });
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        comboBox.setValue(item);
                        setGraphic(comboBox);
                    }
                }
            };
            return cell;
        });

        table.getColumns().addAll(selectCol, nameCol, promptCol);

        // 填充表格数据
        for (String colName : allColumnNames) {
            table.getItems().add(new ColumnPrompt(colName));
        }

        dialogVBox.getChildren().add(table);
        // --- 底部按钮 ---
        Button confirmButton = new Button("确定");
        confirmButton.setOnAction(event -> {

            List<ColumnPrompt> selectedItems = table.getItems().stream()
                    .filter(ColumnPrompt::isSelected)    // 只要被勾选的列
                    .collect(Collectors.toList());
            Map<String, String> colNameToPrompt = selectedItems.stream()
                    .collect(Collectors.toMap(ColumnPrompt::getName, ColumnPrompt::getPrompt));

            if (colNameToPrompt.isEmpty()) {
                showError("请至少选择一列进行导入！");
                return;
            }
            colNameToPrompt.forEach((colName, prompt) -> {
                if (prompt == null || prompt.trim().isEmpty()) {
                    showError("请为列 [" + colName + "] 填写有效的提示词！");
                }
                YmlConfigUtil.updateResourcesYml("ai.promptKey." + tabName + "@"  + colName, prompt);
            });

            List<String> selectedColumns = selectedItems.stream()
                    .map(ColumnPrompt::getName)
                    .collect(Collectors.toList());

            String selectedModel = modelComboBox.getValue();
            log.info("用户从文件 [{}] 中选择了列：{}，使用模型：{}", xmlFile, selectedColumns, selectedModel);

            xmlToDb(xmlFile, selectedColumns, selectedModel);
            dialogStage.close();
        });

        Button cancelButton = new Button("取消");
        cancelButton.setOnAction(event -> dialogStage.close());

        HBox buttonBox = new HBox(10, confirmButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        dialogVBox.getChildren().add(buttonBox);

        Scene scene = new Scene(dialogVBox, 600, 400);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    // 校验提示词
//    private boolean isPromptValid(String prompt) {
//        if (prompt == null || prompt.trim().isEmpty()) return false;
//        if (prompt.length() > 50) return false;
//        return prompt.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9]+$");
//    }

    // 数据模型
    public static class ColumnPrompt {
        private final StringProperty name = new SimpleStringProperty();
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private final StringProperty prompt = new SimpleStringProperty();

        public ColumnPrompt(String name) {
            this.name.set(name);
            this.prompt.set("");
        }

        public StringProperty nameProperty() { return name; }
        public BooleanProperty selectedProperty() { return selected; }
        public StringProperty promptProperty() { return prompt; }

        public String getName() { return name.get(); }
        public boolean isSelected() { return selected.get(); }
        public String getPrompt() { return prompt.get(); }
        public void setPrompt(String value) { prompt.set(value); }
    }



    /**
     * 判断所选列是否合法。
     * 这里只是一个示例，您需要根据您的业务需求实现具体的判断逻辑。
     * @param columnName 被勾选的列名
     * @return 如果列合法则返回 true，否则返回 false
     */
    private boolean validateColumnSelection(String columnName) {
        String property = YamlUtils.getProperty("ai.promptKey." + tabName + "@" +columnName);
        log.info("正在验证列选择: {}", columnName);
        if (!StringUtils.hasLength(property)) {
            log.warn("列 '{}' prompt未配置。", columnName);
            return false;
        }

        return true;
    }

    /**
     * 显示一个警告提示框。
     * @param message 警告信息
     */
    public void showWarning(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("警告");
            alert.setHeaderText(null); // 不显示头部文本
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * 执行表数据校验
     * 一键检查当前表的数据质量，结果显示在日志面板中
     */
    private void runTableValidation() {
        if (tabName == null || tabName.isEmpty()) {
            logPanel.warning("请先选择一个表");
            return;
        }

        logPanel.info("━━━━━━ 开始数据校验 ━━━━━━");
        logPanel.info("表名: " + tabName);

        executor.submit(() -> {
            try {
                DatabaseValidationService service = new DatabaseValidationService(DatabaseUtil.getJdbcTemplate());
                List<ValidationIssue> issues = service.validateTable(tabName);

                Platform.runLater(() -> {
                    if (issues.isEmpty()) {
                        logPanel.success("✅ 校验通过，未发现问题");
                    } else {
                        // 统计各类问题
                        long errors = issues.stream().filter(i -> i.getSeverity() == Severity.ERROR).count();
                        long warnings = issues.stream().filter(i -> i.getSeverity() == Severity.WARNING).count();
                        long infos = issues.stream().filter(i -> i.getSeverity() == Severity.INFO).count();

                        logPanel.info("发现 " + issues.size() + " 个问题:");
                        if (errors > 0) logPanel.error("  ❌ 错误: " + errors + " 个");
                        if (warnings > 0) logPanel.warning("  ⚠️ 警告: " + warnings + " 个");
                        if (infos > 0) logPanel.info("  ℹ️ 提示: " + infos + " 个");

                        logPanel.info("────────────────────────────");

                        // 显示问题详情（限制数量避免刷屏）
                        int shown = 0;
                        for (ValidationIssue issue : issues) {
                            if (shown >= 20) {
                                logPanel.info("... 还有 " + (issues.size() - 20) + " 个问题，请导出完整报告查看");
                                break;
                            }

                            String icon = issue.getSeverityIcon();
                            String msg = icon + " [" + issue.getType() + "] " + issue.getMessage();

                            switch (issue.getSeverity()) {
                                case ERROR:
                                    logPanel.error(msg);
                                    break;
                                case WARNING:
                                    logPanel.warning(msg);
                                    break;
                                default:
                                    logPanel.info(msg);
                            }

                            // 显示建议（如果有）
                            if (issue.getSuggestion() != null) {
                                logPanel.info("   💡 " + issue.getSuggestion());
                            }

                            shown++;
                        }
                    }

                    logPanel.info("━━━━━━ 校验完成 ━━━━━━");
                });

            } catch (Exception e) {
                log.error("数据校验失败", e);
                Platform.runLater(() -> {
                    logPanel.error("校验失败: " + e.getMessage());
                });
            }
        });
    }

    /**
     * 异步刷新总行数和分页（性能优化方法）
     * 在后台线程查询总行数，避免阻塞UI
     */
    private void refreshTotalRowsAsync() {
        javafx.concurrent.Task<Integer> countTask = new javafx.concurrent.Task<>() {
            @Override
            protected Integer call() throws Exception {
                return DatabaseUtil.getTotalRowCount(tabName + buildWhereClause());
            }

            @Override
            protected void succeeded() {
                totalRows = getValue();
                int pageCount = (int) Math.ceil((double) totalRows / DatabaseUtil.ROWS_PER_PAGE);

                Platform.runLater(() -> {
                    pagination.setPageCount(Math.max(1, pageCount));
                    pagination.setCurrentPageIndex(0);
                    pagination.setPageFactory(PaginatedTable.this::createPage);

                    if (logPanel != null) {
                        logPanel.info(String.format("总行数: %,d 行, 总页数: %d", totalRows, pageCount));
                    }
                });
            }

            @Override
            protected void failed() {
                Throwable ex = getException();
                log.error("刷新总行数失败: {}", ex.getMessage(), ex);

                Platform.runLater(() -> {
                    if (logPanel != null) {
                        logPanel.error("刷新总行数失败: " + ex.getMessage());
                    }
                });
            }
        };

        Thread countThread = new Thread(countTask);
        countThread.setDaemon(true);
        countThread.start();
    }

    /**
     * 设置行右键菜单（包含AI操作）
     */
    private void setupRowContextMenu() {
        tableView.setRowFactory(tv -> {
            TableRow<Map<String, Object>> row = new TableRow<>();

            // 创建右键菜单
            ContextMenu contextMenu = new ContextMenu();

            // 复制菜单项
            MenuItem copyItem = new MenuItem("复制选中数据");
            copyItem.setOnAction(e -> {
                Map<String, Object> item = row.getItem();
                if (item != null) {
                    StringBuilder sb = new StringBuilder();
                    item.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
                    ClipboardContent content = new ClipboardContent();
                    content.putString(sb.toString());
                    Clipboard.getSystemClipboard().setContent(content);
                    if (logPanel != null) logPanel.info("已复制到剪贴板");
                }
            });

            // ==================== AI 助手菜单（按设计师意图分类） ====================
            Menu aiMenu = new Menu("🤖 问问AI");

            // 一、我想了解...（认知/学习）
            Menu understandMenu = new Menu("🤔 我想了解...");
            MenuItem whatIsThis = new MenuItem("这是什么？有什么用？");
            whatIsThis.setOnAction(e -> triggerAiOperation(row.getItem(), "what_is_this"));
            MenuItem whatDoNumbersMean = new MenuItem("这些数值代表什么？");
            whatDoNumbersMean.setOnAction(e -> triggerAiOperation(row.getItem(), "explain_numbers"));
            MenuItem whatRelated = new MenuItem("它关联了哪些数据？");
            whatRelated.setOnAction(e -> triggerAiOperation(row.getItem(), "show_relations"));
            understandMenu.getItems().addAll(whatIsThis, whatDoNumbersMean, whatRelated);

            // 二、帮我评估...（判断/决策）
            Menu evaluateMenu = new Menu("⚖️ 帮我评估...");
            MenuItem isBalanced = new MenuItem("数值平衡吗？");
            isBalanced.setOnAction(e -> triggerAiOperation(row.getItem(), "check_balance"));
            MenuItem compareWithOthers = new MenuItem("跟同类比怎么样？");
            compareWithOthers.setOnAction(e -> triggerAiOperation(row.getItem(), "compare_similar"));
            MenuItem playerExperience = new MenuItem("玩家体验会如何？");
            playerExperience.setOnAction(e -> triggerAiOperation(row.getItem(), "predict_experience"));
            evaluateMenu.getItems().addAll(isBalanced, compareWithOthers, playerExperience);

            // 三、帮我找...（搜索/发现）
            Menu findMenu = new Menu("🔍 帮我找...");
            MenuItem findSimilar = new MenuItem("类似的配置");
            findSimilar.setOnAction(e -> triggerAiOperation(row.getItem(), "find_similar"));
            MenuItem findRelated = new MenuItem("相关的数据");
            findRelated.setOnAction(e -> triggerAiOperation(row.getItem(), "find_related"));
            findMenu.getItems().addAll(findSimilar, findRelated);

            // 四、帮我改进...（优化/生成）
            Menu improveMenu = new Menu("✨ 帮我改进...");
            MenuItem giveSuggestions = new MenuItem("给点优化建议");
            giveSuggestions.setOnAction(e -> triggerAiOperation(row.getItem(), "suggest_improvements"));
            MenuItem createVariant = new MenuItem("生成一个变体");
            createVariant.setOnAction(e -> triggerAiOperation(row.getItem(), "generate_variant"));
            improveMenu.getItems().addAll(giveSuggestions, createVariant);

            aiMenu.getItems().addAll(understandMenu, evaluateMenu, findMenu, improveMenu);

            // 分隔线
            SeparatorMenuItem separator = new SeparatorMenuItem();

            contextMenu.getItems().addAll(copyItem, separator, aiMenu);

            // 仅在有数据的行上显示菜单
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(contextMenu)
            );

            return row;
        });
    }

    /**
     * 触发AI操作
     */
    private void triggerAiOperation(Map<String, Object> rowData, String operationType) {
        if (onAiOperation == null) {
            log.warn("AI操作回调未设置");
            if (logPanel != null) {
                logPanel.warning("AI助手未启用，请先配置AI服务");
            }
            return;
        }

        if (rowData == null || rowData.isEmpty()) {
            if (logPanel != null) {
                logPanel.warning("请先选择一行数据");
            }
            return;
        }

        // 收集上下文
        DesignContext context = contextCollector.collectFromTableRow(tabName, rowData);
        log.info("触发AI操作: {} - 表: {} - 行数据: {}", operationType, tabName, rowData);

        // 调用回调
        onAiOperation.accept(context, operationType);
    }

    // ==================== AI 操作支持 ====================

    /**
     * 设置AI操作回调
     *
     * @param onAiOperation AI操作回调函数
     */
    public void setOnAiOperation(BiConsumer<DesignContext, String> onAiOperation) {
        this.onAiOperation = onAiOperation;
    }

    /**
     * 获取当前表名
     */
    public String getTabName() {
        return tabName;
    }

    /**
     * 获取表格视图（用于外部集成）
     */
    public TableView<Map<String, Object>> getTableView() {
        return tableView;
    }
}

