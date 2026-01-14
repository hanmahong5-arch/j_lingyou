package red.jiuzhou.ui.diagnosis;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.batch.BatchXmlImporter;
import red.jiuzhou.batch.diagnosis.DiagnosticFailure;
import red.jiuzhou.ui.error.structured.ErrorCategory;
import red.jiuzhou.ui.error.structured.ErrorLevel;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 批量导入诊断对话框（智能诊断系统核心 UI）
 *
 * 设计目标：
 * - 快速了解哪个文件出了什么问题（可视化分类面板）
 * - 一键定位到问题文件或具体行（交互式详情窗口）
 * - 失败文件可选择性重新导入（恢复能力）
 * - "超级思考"般的体验 - 无需技术背景也能自行解决问题
 *
 * @author Claude AI
 * @date 2026-01-15
 */
public class BatchImportDiagnosticDialog extends Stage {

    private static final Logger log = LoggerFactory.getLogger(BatchImportDiagnosticDialog.class);

    private final BatchXmlImporter.BatchImportResult result;

    // UI 组件
    private VBox totalLabel;
    private VBox successLabel;
    private VBox failedLabel;
    private VBox skippedLabel;

    // 统计数值标签（用于更新）
    private Label totalValueLabel;
    private Label successValueLabel;
    private Label failedValueLabel;
    private Label skippedValueLabel;

    private PieChart categoryChart;
    private BarChart<String, Number> levelChart;

    private TableView<DiagnosticFailureRow> failureTable;
    private ObservableList<DiagnosticFailureRow> failureData;

    private TextArea detailArea;
    private Label detailTitleLabel;

    private Button applyFixBtn;
    private Button retryBtn;
    private Button batchRetryBtn;
    private Button exportBtn;
    private Button locateBtn;
    private Button closeBtn;

    /**
     * 构造函数
     *
     * @param result 批量导入结果（包含失败诊断信息）
     */
    public BatchImportDiagnosticDialog(BatchXmlImporter.BatchImportResult result) {
        this.result = result;
        this.failureData = FXCollections.observableArrayList();

        initUI();
        loadData();
    }

    /**
     * 初始化 UI 布局
     */
    private void initUI() {
        setTitle("📊 批量导入诊断报告");
        initModality(Modality.APPLICATION_MODAL);
        setWidth(1100);
        setHeight(750);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #fafafa;");

        // ========== 1. 统计概览区 ==========
        HBox statisticsBox = createStatisticsBox();

        // ========== 2. 图表区域（错误分类） ==========
        HBox chartsBox = createChartsBox();

        // ========== 3. 失败文件列表 ==========
        VBox tableBox = createTableBox();

        // ========== 4. 详细信息面板 ==========
        VBox detailBox = createDetailBox();

        // ========== 5. 操作按钮区 ==========
        HBox buttonBox = createButtonBox();

        // ========== 组装布局 ==========
        VBox.setVgrow(tableBox, Priority.ALWAYS);
        VBox.setVgrow(detailBox, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.getItems().addAll(tableBox, detailBox);
        splitPane.setDividerPositions(0.55);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        root.getChildren().addAll(
            statisticsBox,
            chartsBox,
            splitPane,
            buttonBox
        );

        Scene scene = new Scene(root);
        setScene(scene);
    }

    /**
     * 创建统计概览区
     */
    private HBox createStatisticsBox() {
        HBox box = new HBox(20);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(15));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                     "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");

        // 创建统计标签并保存值标签引用
        totalLabel = createStatLabel("总计", "0", "#3498db", l -> totalValueLabel = l);
        successLabel = createStatLabel("成功", "0", "#27ae60", l -> successValueLabel = l);
        failedLabel = createStatLabel("失败", "0", "#e74c3c", l -> failedValueLabel = l);
        skippedLabel = createStatLabel("跳过", "0", "#f39c12", l -> skippedValueLabel = l);

        box.getChildren().addAll(totalLabel, createSeparator(),
                                successLabel, createSeparator(),
                                failedLabel, createSeparator(),
                                skippedLabel);

        return box;
    }

    /**
     * 创建统计标签
     */
    private VBox createStatLabel(String title, String value, String color, java.util.function.Consumer<Label> valueLabelConsumer) {
        VBox box = new VBox(5);
        box.setAlignment(Pos.CENTER);
        box.setPrefWidth(120);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle(String.format("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: %s;", color));

        // 保存 valueLabel 引用
        if (valueLabelConsumer != null) {
            valueLabelConsumer.accept(valueLabel);
        }

        box.getChildren().addAll(titleLabel, valueLabel);
        return box;
    }

    /**
     * 创建分隔线
     */
    private Separator createSeparator() {
        Separator sep = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep.setPrefHeight(50);
        return sep;
    }

    /**
     * 创建图表区域
     */
    private HBox createChartsBox() {
        HBox box = new HBox(15);
        box.setPrefHeight(220);

        // 饼图：按错误类别
        categoryChart = new PieChart();
        categoryChart.setTitle("错误分类");
        categoryChart.setLegendVisible(true);
        categoryChart.setPrefWidth(400);
        categoryChart.setStyle("-fx-background-color: white; -fx-background-radius: 8;");

        // 柱状图：按严重程度
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("严重程度");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("数量");

        levelChart = new BarChart<>(xAxis, yAxis);
        levelChart.setTitle("严重程度分布");
        levelChart.setLegendVisible(false);
        levelChart.setPrefWidth(400);
        levelChart.setStyle("-fx-background-color: white; -fx-background-radius: 8;");

        HBox.setHgrow(categoryChart, Priority.ALWAYS);
        HBox.setHgrow(levelChart, Priority.ALWAYS);

        box.getChildren().addAll(categoryChart, levelChart);

        return box;
    }

    /**
     * 创建失败文件列表
     */
    private VBox createTableBox() {
        VBox box = new VBox(10);

        Label titleLabel = new Label("❌ 失败文件列表");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        failureTable = new TableView<>();
        failureTable.setPlaceholder(new Label("🎉 没有失败项！"));

        // 列1: 勾选框
        TableColumn<DiagnosticFailureRow, Boolean> selectCol = new TableColumn<>("选择");
        selectCol.setPrefWidth(50);
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setEditable(true);

        // 列2: 文件名
        TableColumn<DiagnosticFailureRow, String> fileCol = new TableColumn<>("文件名");
        fileCol.setPrefWidth(200);
        fileCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getFailure().fileName()));

        // 列3: 表名
        TableColumn<DiagnosticFailureRow, String> tableCol = new TableColumn<>("表名");
        tableCol.setPrefWidth(150);
        tableCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getFailure().tableName()));

        // 列4: 错误码
        TableColumn<DiagnosticFailureRow, String> codeCol = new TableColumn<>("错误码");
        codeCol.setPrefWidth(100);
        codeCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getFailure().getErrorCode()));

        // 列5: 错误标题
        TableColumn<DiagnosticFailureRow, String> titleCol = new TableColumn<>("错误描述");
        titleCol.setPrefWidth(300);
        titleCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getFailure().structuredError().title()));

        // 列6: 阶段
        TableColumn<DiagnosticFailureRow, String> phaseCol = new TableColumn<>("失败阶段");
        phaseCol.setPrefWidth(120);
        phaseCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getFailure().importPhase()));

        // 列7: 可重试
        TableColumn<DiagnosticFailureRow, String> retryableCol = new TableColumn<>("可重试");
        retryableCol.setPrefWidth(70);
        retryableCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getFailure().retryable() ? "✅" : "❌"));

        failureTable.getColumns().addAll(selectCol, fileCol, tableCol, codeCol,
                                        titleCol, phaseCol, retryableCol);
        failureTable.setEditable(true);

        // 选中行时显示详情
        failureTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    showDetail(newSelection.getFailure());
                }
            }
        );

        VBox.setVgrow(failureTable, Priority.ALWAYS);

        box.getChildren().addAll(titleLabel, failureTable);

        return box;
    }

    /**
     * 创建详细信息面板
     */
    private VBox createDetailBox() {
        VBox box = new VBox(10);

        detailTitleLabel = new Label("📄 选中项详细信息");
        detailTitleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        detailArea = new TextArea();
        detailArea.setEditable(false);
        detailArea.setWrapText(true);
        detailArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; " +
                          "-fx-font-size: 12px;");
        detailArea.setText("请在上方列表中选择一个失败项以查看详细信息。");

        VBox.setVgrow(detailArea, Priority.ALWAYS);

        box.getChildren().addAll(detailTitleLabel, detailArea);

        return box;
    }

    /**
     * 创建操作按钮区
     */
    private HBox createButtonBox() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(10, 0, 0, 0));

        applyFixBtn = new Button("🔧 应用修复");
        applyFixBtn.setDisable(true);
        applyFixBtn.setOnAction(e -> applyFix());

        retryBtn = new Button("🔄 重试选中项");
        retryBtn.setDisable(true);
        retryBtn.setOnAction(e -> retrySelected());

        batchRetryBtn = new Button("🔄 批量重试");
        batchRetryBtn.setOnAction(e -> batchRetry());

        exportBtn = new Button("📥 导出报告");
        exportBtn.setOnAction(e -> exportReport());

        locateBtn = new Button("📂 打开文件位置");
        locateBtn.setDisable(true);
        locateBtn.setOnAction(e -> locateFile());

        closeBtn = new Button("关闭");
        closeBtn.setOnAction(e -> close());

        // 样式
        applyFixBtn.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white;");
        retryBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        batchRetryBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        exportBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        locateBtn.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;");

        box.getChildren().addAll(applyFixBtn, retryBtn, batchRetryBtn, exportBtn, locateBtn, closeBtn);

        return box;
    }

    /**
     * 加载数据
     */
    private void loadData() {
        // 更新统计概览
        updateStatistics();

        // 更新图表
        updateCharts();

        // 加载失败文件列表
        List<DiagnosticFailure> failures = result.getFailedFiles();
        for (DiagnosticFailure failure : failures) {
            failureData.add(new DiagnosticFailureRow(failure));
        }
        failureTable.setItems(failureData);

        // 如果只有一个失败项，自动选中并显示详情
        if (failures.size() == 1) {
            failureTable.getSelectionModel().selectFirst();
        }
    }

    /**
     * 更新统计概览
     */
    private void updateStatistics() {
        totalValueLabel.setText(String.valueOf(result.getTotal()));
        successValueLabel.setText(String.valueOf(result.getSuccess()));
        failedValueLabel.setText(String.valueOf(result.getFailed()));
        skippedValueLabel.setText(String.valueOf(result.getSkipped()));
    }

    /**
     * 更新图表
     */
    private void updateCharts() {
        // 饼图：按类别
        categoryChart.getData().clear();
        Map<ErrorCategory, Integer> categoryMap = result.getErrorsByCategory();
        if (!categoryMap.isEmpty()) {
            for (Map.Entry<ErrorCategory, Integer> entry : categoryMap.entrySet()) {
                PieChart.Data data = new PieChart.Data(
                    entry.getKey().getDisplayName() + " (" + entry.getValue() + ")",
                    entry.getValue()
                );
                categoryChart.getData().add(data);
            }
        } else {
            categoryChart.setTitle("错误分类（无数据）");
        }

        // 柱状图：按严重程度
        levelChart.getData().clear();
        Map<ErrorLevel, Integer> levelMap = result.getErrorsByLevel();
        if (!levelMap.isEmpty()) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            for (Map.Entry<ErrorLevel, Integer> entry : levelMap.entrySet()) {
                series.getData().add(new XYChart.Data<>(
                    entry.getKey().getDisplayName(),
                    entry.getValue()
                ));
            }
            levelChart.getData().add(series);
        } else {
            levelChart.setTitle("严重程度分布（无数据）");
        }
    }

    /**
     * 显示选中项的详细信息
     */
    private void showDetail(DiagnosticFailure failure) {
        StringBuilder sb = new StringBuilder();

        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("📄 文件信息\n");
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append(String.format("文件路径: %s\n", failure.filePath()));
        sb.append(String.format("表名: %s\n", failure.tableName()));
        sb.append(String.format("失败阶段: %s\n", failure.importPhase()));
        sb.append(String.format("失败时间: %s\n", failure.failedTime()));
        sb.append(String.format("可重试: %s\n", failure.retryable() ? "✅ 是" : "❌ 否"));
        sb.append(String.format("重试次数: %d\n", failure.retryCount()));
        sb.append("\n");

        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("🔍 错误详情（Rust风格）\n");
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append(failure.getErrorDetail());
        sb.append("\n");

        // 修复建议
        if (failure.structuredError().suggestions() != null &&
            !failure.structuredError().suggestions().isEmpty()) {
            sb.append("═══════════════════════════════════════════════════\n");
            sb.append("💡 修复建议\n");
            sb.append("═══════════════════════════════════════════════════\n");
            int idx = 1;
            for (var suggestion : failure.structuredError().suggestions()) {
                sb.append(String.format("%d. %s\n", idx++, suggestion.title()));
                sb.append(String.format("   类型: %s\n", suggestion.type()));
                sb.append(String.format("   描述: %s\n", suggestion.description()));
                sb.append("\n");
            }
        }

        // 上下文信息
        if (failure.context() != null && !failure.context().isEmpty()) {
            sb.append("═══════════════════════════════════════════════════\n");
            sb.append("📋 上下文信息\n");
            sb.append("═══════════════════════════════════════════════════\n");
            failure.context().forEach((key, value) ->
                sb.append(String.format("%s: %s\n", key, value))
            );
            sb.append("\n");
        }

        // AI 诊断（如果有）
        if (failure.hasAiDiagnosis()) {
            sb.append("═══════════════════════════════════════════════════\n");
            sb.append("🤖 AI 智能诊断\n");
            sb.append("═══════════════════════════════════════════════════\n");
            sb.append(failure.aiDiagnosis());
            sb.append("\n");
        }

        detailArea.setText(sb.toString());

        // 更新按钮状态
        boolean hasFixableProblem = canApplyFix(failure);
        applyFixBtn.setDisable(!hasFixableProblem);
        retryBtn.setDisable(!failure.retryable() || failure.hasExceededRetryLimit());
        locateBtn.setDisable(false);
    }

    /**
     * 应用修复
     */
    private void applyFix() {
        DiagnosticFailureRow selected = failureTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("请先选择一个失败项");
            return;
        }

        DiagnosticFailure failure = selected.getFailure();
        String errorCode = failure.getErrorCode();

        // 根据错误码选择修复策略
        ChoiceDialog<String> dialog = new ChoiceDialog<>();
        dialog.setTitle("选择修复策略");
        dialog.setHeaderText(String.format("文件: %s\n错误码: %s", failure.fileName(), errorCode));
        dialog.setContentText("请选择修复策略:");

        // 根据错误类型提供修复选项
        java.util.List<String> fixOptions = new java.util.ArrayList<>();

        if (errorCode.startsWith("CFG")) {
            fixOptions.add("自动生成配置文件 (.conf)");
        }
        if (errorCode.startsWith("DB") && failure.originalException().getMessage().contains("不存在")) {
            fixOptions.add("自动建表（执行 DDL）");
        }
        if (errorCode.startsWith("DB") && failure.originalException().getMessage().contains("duplicate key")) {
            fixOptions.add("清空表数据后重新导入");
            fixOptions.add("删除重复记录（保留最新）");
        }
        if (errorCode.startsWith("IO") && failure.originalException().getMessage().contains("编码")) {
            fixOptions.add("修复文件编码为 UTF-8");
        }

        if (fixOptions.isEmpty()) {
            showAlert("该错误暂无自动修复方案\n\n建议：\n1. 查看详细错误信息\n2. 手动修复后重试");
            return;
        }

        dialog.getItems().addAll(fixOptions);
        dialog.setSelectedItem(fixOptions.get(0));

        dialog.showAndWait().ifPresent(choice -> {
            red.jiuzhou.diagnosis.AutoFixService.FixResult fixResult = null;

            try {
                if (choice.contains("自动生成配置文件")) {
                    fixResult = red.jiuzhou.diagnosis.AutoFixService.autoGenerateConfig(failure);
                } else if (choice.contains("自动建表")) {
                    fixResult = red.jiuzhou.diagnosis.AutoFixService.autoCreateTable(failure);
                } else if (choice.contains("清空表数据")) {
                    fixResult = red.jiuzhou.diagnosis.AutoFixService.clearTableData(failure.tableName());
                } else if (choice.contains("删除重复记录")) {
                    String primaryKey = red.jiuzhou.util.DatabaseUtil.getPrimaryKeyColumn(failure.tableName());
                    fixResult = red.jiuzhou.diagnosis.AutoFixService.removeDuplicateRecords(
                        failure.tableName(), primaryKey);
                } else if (choice.contains("修复文件编码")) {
                    fixResult = red.jiuzhou.diagnosis.AutoFixService.fixFileEncoding(failure.file());
                }

                if (fixResult != null) {
                    if (fixResult.isSuccess()) {
                        showSuccessDialog(fixResult);
                    } else {
                        showAlert("修复失败\n\n" + fixResult.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("应用修复失败", e);
                showAlert("修复失败: " + e.getMessage());
            }
        });
    }

    /**
     * 判断是否可以应用修复
     */
    private boolean canApplyFix(DiagnosticFailure failure) {
        String errorCode = failure.getErrorCode();

        // 配置错误：可以自动生成配置
        if (errorCode.startsWith("CFG")) {
            return true;
        }

        // 数据库错误：表不存在可以建表，主键冲突可以清空
        if (errorCode.startsWith("DB")) {
            String message = failure.originalException().getMessage();
            return message.contains("不存在") || message.contains("duplicate key");
        }

        // IO 错误：编码问题可以修复
        if (errorCode.startsWith("IO")) {
            return failure.originalException().getMessage().contains("编码");
        }

        return false;
    }

    /**
     * 重试选中项
     */
    private void retrySelected() {
        DiagnosticFailureRow selected = failureTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("请先选择要重试的失败项");
            return;
        }

        DiagnosticFailure failure = selected.getFailure();
        if (!failure.retryable()) {
            showAlert("该错误不可重试，请先应用修复建议");
            return;
        }

        if (failure.hasExceededRetryLimit()) {
            showAlert("已达到最大重试次数（3次），请检查修复建议");
            return;
        }

        // 执行重试
        retryImport(failure, selected);
    }

    /**
     * 执行重试导入
     */
    private void retryImport(DiagnosticFailure failure, DiagnosticFailureRow row) {
        // 禁用按钮
        retryBtn.setDisable(true);
        batchRetryBtn.setDisable(true);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                log.info("开始重试导入: {}", failure.fileName());

                // 调用导入方法
                red.jiuzhou.batch.BatchXmlImporter.ImportOptions options =
                    new red.jiuzhou.batch.BatchXmlImporter.ImportOptions();

                boolean success = red.jiuzhou.batch.BatchXmlImporter.importSingleXmlSync(
                    failure.filePath(), options);

                Platform.runLater(() -> {
                    if (success) {
                        // 从失败列表移除
                        failureData.remove(row);
                        result.getFailedFiles().remove(failure);
                        result.setFailed(result.getFailed() - 1);
                        result.setSuccess(result.getSuccess() + 1);

                        // 更新统计和图表
                        updateStatistics();
                        updateCharts();

                        showAlert("✅ 重试成功！\n\n文件已成功导入数据库");
                        log.info("✅ 重试成功: {}", failure.fileName());
                    } else {
                        showAlert("❌ 重试失败\n\n请查看日志了解详情");
                        log.warn("❌ 重试失败: {}", failure.fileName());
                    }

                    // 恢复按钮
                    retryBtn.setDisable(false);
                    batchRetryBtn.setDisable(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    log.error("重试导入失败", e);
                    showAlert("❌ 重试失败\n\n" + e.getMessage());

                    // 更新重试计数
                    DiagnosticFailure updated = failure.withRetry();
                    row.failure = updated;
                    failureTable.refresh();

                    // 恢复按钮
                    retryBtn.setDisable(false);
                    batchRetryBtn.setDisable(false);
                });
            }
        });
    }

    /**
     * 批量重试
     */
    private void batchRetry() {
        List<DiagnosticFailure> retryableFailures = result.getRetryableFailures();

        if (retryableFailures.isEmpty()) {
            showAlert("没有可重试的失败项");
            return;
        }

        // 创建批量重试对话框
        red.jiuzhou.ui.diagnosis.BatchRetryDialog retryDialog =
            new red.jiuzhou.ui.diagnosis.BatchRetryDialog(retryableFailures, this);
        retryDialog.show();
    }

    /**
     * 导出报告
     */
    private void exportReport() {
        // TODO: 实现报告导出逻辑（P4 阶段）
        showAlert("报告导出功能将在 P4 阶段实现");
    }

    /**
     * 定位文件
     */
    private void locateFile() {
        DiagnosticFailureRow selected = failureTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("请先选择一个失败项");
            return;
        }

        File file = selected.getFailure().file();
        if (!file.exists()) {
            showAlert("文件不存在: " + file.getAbsolutePath());
            return;
        }

        try {
            // 在资源管理器中打开并选中文件
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                    desktop.browseFileDirectory(file);
                } else {
                    // 备用：打开父目录
                    desktop.open(file.getParentFile());
                }
            }
        } catch (IOException e) {
            log.error("打开文件位置失败", e);
            showAlert("打开文件位置失败: " + e.getMessage());
        }
    }

    /**
     * 显示提示对话框
     */
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示成功对话框（修复成功后）
     */
    private void showSuccessDialog(red.jiuzhou.diagnosis.AutoFixService.FixResult fixResult) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("✅ 修复成功");
        alert.setHeaderText(fixResult.getMessage());
        alert.setContentText("下一步：\n" + fixResult.getNextStep());
        alert.showAndWait();
    }

    /**
     * 失败文件行（用于 TableView）
     */
    public static class DiagnosticFailureRow {
        private DiagnosticFailure failure;  // 改为可变（用于重试后更新）
        private final javafx.beans.property.BooleanProperty selected =
            new javafx.beans.property.SimpleBooleanProperty(false);

        public DiagnosticFailureRow(DiagnosticFailure failure) {
            this.failure = failure;
        }

        public DiagnosticFailure getFailure() {
            return failure;
        }

        public javafx.beans.property.BooleanProperty selectedProperty() {
            return selected;
        }
    }

    /**
     * 刷新诊断对话框（重试成功后调用）
     */
    public void refreshAfterRetry() {
        updateStatistics();
        updateCharts();
        failureTable.refresh();
    }
}
