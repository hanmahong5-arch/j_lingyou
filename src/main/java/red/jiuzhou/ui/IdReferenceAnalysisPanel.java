package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import red.jiuzhou.analysis.aion.reference.FieldReferenceAnalyzer;
import red.jiuzhou.analysis.aion.reference.FieldReferenceEntry;
import red.jiuzhou.analysis.aion.reference.FieldReferenceResult;
import red.jiuzhou.analysis.aion.reference.FieldReferenceService;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * ID引用分析面板
 *
 * <p>展示XML文件之间的字段级ID引用关系。
 *
 * @author Claude
 * @version 1.0
 */
public class IdReferenceAnalysisPanel extends BorderPane {

    private final FieldReferenceService referenceService;

    private final TableView<ReferenceRow> tableView = new TableView<>();
    private final FilteredList<ReferenceRow> filteredRows;
    private final ObservableList<ReferenceRow> allRows = FXCollections.observableArrayList();

    private final Label statusLabel = new Label("点击「分析」开始扫描");
    private final Label statsLabel = new Label("");
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final Button analyzeButton = new Button("分析");
    private final Button refreshButton = new Button("强制刷新");
    private final ComboBox<String> systemFilter = new ComboBox<>();

    public IdReferenceAnalysisPanel() {
        this.referenceService = new FieldReferenceService();
        this.filteredRows = new FilteredList<>(allRows, r -> true);

        initUI();
        loadCachedData();
    }

    private void initUI() {
        setPadding(new Insets(10));

        // 顶部工具栏
        HBox toolbar = createToolbar();

        // 表格
        tableView.setItems(filteredRows);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setRowFactory(tv -> createStyledRow());
        buildColumns();

        // 详情面板
        VBox detailPanel = createDetailPanel();

        // 布局
        setTop(toolbar);
        setCenter(tableView);
        setBottom(detailPanel);
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 10, 0));

        // 分析按钮
        analyzeButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        analyzeButton.setOnAction(e -> runAnalysis(false));

        // 强制刷新按钮
        refreshButton.setOnAction(e -> runAnalysis(true));

        // 系统过滤
        systemFilter.setPromptText("按目标系统过滤");
        systemFilter.setPrefWidth(150);
        systemFilter.getItems().add("全部系统");
        systemFilter.getItems().addAll(new FieldReferenceAnalyzer().getSupportedSystems());
        systemFilter.setValue("全部系统");
        systemFilter.setOnAction(e -> applySystemFilter());

        // 搜索框
        TextField searchField = new TextField();
        searchField.setPromptText("搜索文件或字段...");
        searchField.setPrefWidth(200);
        searchField.textProperty().addListener((obs, old, text) -> applyTextFilter(text));

        // 进度指示器
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(20, 20);

        // 状态标签
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(
                analyzeButton, refreshButton,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                systemFilter, searchField,
                progressIndicator,
                spacer, statsLabel, statusLabel
        );

        return toolbar;
    }

    private void buildColumns() {
        TableColumn<ReferenceRow, String> sourceFileCol = new TableColumn<>("源文件");
        sourceFileCol.setCellValueFactory(p -> p.getValue().sourceFile);
        sourceFileCol.setPrefWidth(180);

        TableColumn<ReferenceRow, String> sourceFieldCol = new TableColumn<>("字段");
        sourceFieldCol.setCellValueFactory(p -> p.getValue().sourceField);
        sourceFieldCol.setPrefWidth(120);

        TableColumn<ReferenceRow, String> mechanismCol = new TableColumn<>("机制");
        mechanismCol.setCellValueFactory(p -> p.getValue().mechanism);
        mechanismCol.setPrefWidth(100);

        TableColumn<ReferenceRow, String> targetSystemCol = new TableColumn<>("目标系统");
        targetSystemCol.setCellValueFactory(p -> p.getValue().targetSystem);
        targetSystemCol.setPrefWidth(100);

        TableColumn<ReferenceRow, String> targetTableCol = new TableColumn<>("目标表");
        targetTableCol.setCellValueFactory(p -> p.getValue().targetTable);
        targetTableCol.setPrefWidth(140);

        TableColumn<ReferenceRow, Number> countCol = new TableColumn<>("引用数");
        countCol.setCellValueFactory(p -> p.getValue().referenceCount);
        countCol.setPrefWidth(80);

        TableColumn<ReferenceRow, Number> distinctCol = new TableColumn<>("不重复");
        distinctCol.setCellValueFactory(p -> p.getValue().distinctCount);
        distinctCol.setPrefWidth(80);

        TableColumn<ReferenceRow, String> validRateCol = new TableColumn<>("有效率");
        validRateCol.setCellValueFactory(p -> p.getValue().validRate);
        validRateCol.setPrefWidth(80);

        TableColumn<ReferenceRow, String> samplesCol = new TableColumn<>("样本");
        samplesCol.setCellValueFactory(p -> p.getValue().samples);
        samplesCol.setPrefWidth(250);

        tableView.getColumns().addAll(
                sourceFileCol, sourceFieldCol, mechanismCol,
                targetSystemCol, targetTableCol,
                countCol, distinctCol, validRateCol, samplesCol
        );
    }

    private TableRow<ReferenceRow> createStyledRow() {
        return new TableRow<ReferenceRow>() {
            @Override
            protected void updateItem(ReferenceRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setStyle("");
                } else {
                    // 根据有效率着色
                    int invalid = row.getEntry().getInvalidReferences();
                    if (invalid > 0) {
                        setStyle("-fx-background-color: rgba(244, 67, 54, 0.15);");
                    } else {
                        setStyle("-fx-background-color: rgba(76, 175, 80, 0.08);");
                    }
                }
            }
        };
    }

    private VBox createDetailPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(10, 0, 0, 0));
        panel.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 10; -fx-background-radius: 4;");

        Label title = new Label("详情");
        title.setStyle("-fx-font-weight: bold;");

        Label detailLabel = new Label("选择一行查看详情");
        detailLabel.setStyle("-fx-text-fill: #666;");

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                FieldReferenceEntry entry = selected.getEntry();
                StringBuilder sb = new StringBuilder();
                sb.append("源文件: ").append(entry.getSourceFile()).append("\n");
                sb.append("字段路径: ").append(entry.getSourceFieldPath()).append("\n");
                sb.append("目标系统: ").append(entry.getTargetSystem())
                        .append(" (表: ").append(entry.getTargetTable()).append(")\n");
                sb.append("引用统计: ").append(entry.getReferenceCount()).append("个引用, ")
                        .append(entry.getDistinctValues()).append("个不重复值\n");
                sb.append("有效性: ").append(entry.getValidReferences()).append("个有效, ")
                        .append(entry.getInvalidReferences()).append("个无效 (")
                        .append(entry.getValidRatePercent()).append(")\n");

                // 样本值
                List<String> values = entry.getSampleValues();
                List<String> names = entry.getSampleNames();
                if (!values.isEmpty()) {
                    sb.append("样本值:\n");
                    for (int i = 0; i < values.size(); i++) {
                        String name = i < names.size() ? names.get(i) : "???";
                        sb.append("  • ").append(values.get(i)).append(" → ").append(name).append("\n");
                    }
                }

                detailLabel.setText(sb.toString());
            } else {
                detailLabel.setText("选择一行查看详情");
            }
        });

        panel.getChildren().addAll(title, detailLabel);
        panel.setMaxHeight(150);
        return panel;
    }

    private void loadCachedData() {
        String xmlPath = YamlUtils.getProperty("aion.xmlPath");
        if (xmlPath == null || xmlPath.isEmpty()) {
            statusLabel.setText("未配置XML路径");
            return;
        }

        File xmlDir = new File(xmlPath);
        FieldReferenceResult cached = referenceService.loadFromCache(xmlDir);
        if (cached != null) {
            displayResult(cached);
            statusLabel.setText("已加载缓存数据");
        }
    }

    private void runAnalysis(boolean forceRefresh) {
        String xmlPath = YamlUtils.getProperty("aion.xmlPath");
        if (xmlPath == null || xmlPath.isEmpty()) {
            showAlert("错误", "未配置aion.xmlPath");
            return;
        }

        File xmlDir = new File(xmlPath);
        if (!xmlDir.exists()) {
            showAlert("错误", "目录不存在: " + xmlPath);
            return;
        }

        // 显示进度
        analyzeButton.setDisable(true);
        refreshButton.setDisable(true);
        progressIndicator.setVisible(true);
        statusLabel.setText("正在分析...");

        // 异步执行
        new Thread(() -> {
            try {
                FieldReferenceResult result = referenceService.analyze(xmlDir, forceRefresh);

                Platform.runLater(() -> {
                    displayResult(result);
                    statusLabel.setText(result.getSummary());
                    analyzeButton.setDisable(false);
                    refreshButton.setDisable(false);
                    progressIndicator.setVisible(false);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("分析失败: " + e.getMessage());
                    analyzeButton.setDisable(false);
                    refreshButton.setDisable(false);
                    progressIndicator.setVisible(false);
                });
            }
        }).start();
    }

    private void displayResult(FieldReferenceResult result) {
        allRows.clear();

        for (FieldReferenceEntry entry : result.getEntries()) {
            allRows.add(new ReferenceRow(entry));
        }

        // 更新统计
        updateStats(result);
    }

    private void updateStats(FieldReferenceResult result) {
        int total = result.getTotalEntries();
        int invalid = result.getTotalInvalidReferences();
        int valid = result.getTotalValidReferences();

        String stats = String.format("共 %d 个字段引用 | 有效: %d | 无效: %d",
                total, valid, invalid);
        statsLabel.setText(stats);

        if (invalid > 0) {
            statsLabel.setTextFill(Color.web("#d32f2f"));
        } else {
            statsLabel.setTextFill(Color.web("#4CAF50"));
        }
    }

    private void applySystemFilter() {
        String system = systemFilter.getValue();
        if (system == null || "全部系统".equals(system)) {
            filteredRows.setPredicate(r -> true);
        } else {
            filteredRows.setPredicate(r -> system.equals(r.getEntry().getTargetSystem()));
        }
    }

    private void applyTextFilter(String text) {
        if (text == null || text.trim().isEmpty()) {
            applySystemFilter();  // 恢复系统过滤
            return;
        }

        String lower = text.trim().toLowerCase(Locale.ROOT);
        String currentSystem = systemFilter.getValue();

        filteredRows.setPredicate(r -> {
            // 先检查系统过滤
            if (currentSystem != null && !"全部系统".equals(currentSystem)) {
                if (!currentSystem.equals(r.getEntry().getTargetSystem())) {
                    return false;
                }
            }

            // 再检查文本过滤
            FieldReferenceEntry entry = r.getEntry();
            return entry.getSourceFileName().toLowerCase(Locale.ROOT).contains(lower)
                    || entry.getSourceField().toLowerCase(Locale.ROOT).contains(lower)
                    || (entry.getSourceMechanism() != null &&
                    entry.getSourceMechanism().toLowerCase(Locale.ROOT).contains(lower))
                    || entry.getTargetSystem().toLowerCase(Locale.ROOT).contains(lower);
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ========== 行数据模型 ==========

    public static class ReferenceRow {
        private final FieldReferenceEntry entry;
        private final SimpleStringProperty sourceFile;
        private final SimpleStringProperty sourceField;
        private final SimpleStringProperty mechanism;
        private final SimpleStringProperty targetSystem;
        private final SimpleStringProperty targetTable;
        private final SimpleIntegerProperty referenceCount;
        private final SimpleIntegerProperty distinctCount;
        private final SimpleStringProperty validRate;
        private final SimpleStringProperty samples;

        public ReferenceRow(FieldReferenceEntry entry) {
            this.entry = entry;
            this.sourceFile = new SimpleStringProperty(entry.getSourceFileName());
            this.sourceField = new SimpleStringProperty(entry.getSourceField());
            this.mechanism = new SimpleStringProperty(
                    entry.getSourceMechanism() != null ? entry.getSourceMechanism() : "-");
            this.targetSystem = new SimpleStringProperty(entry.getTargetSystem());
            this.targetTable = new SimpleStringProperty(entry.getTargetTable());
            this.referenceCount = new SimpleIntegerProperty(entry.getReferenceCount());
            this.distinctCount = new SimpleIntegerProperty(entry.getDistinctValues());
            this.validRate = new SimpleStringProperty(entry.getValidRatePercent());

            // 构建样本显示
            List<String> values = entry.getSampleValues();
            List<String> names = entry.getSampleNames();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(3, values.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(values.get(i));
                if (i < names.size() && !names.get(i).equals(values.get(i))) {
                    sb.append("(").append(truncate(names.get(i), 10)).append(")");
                }
            }
            if (values.size() > 3) {
                sb.append("...");
            }
            this.samples = new SimpleStringProperty(sb.toString());
        }

        private String truncate(String s, int max) {
            return s.length() > max ? s.substring(0, max) + "..." : s;
        }

        public FieldReferenceEntry getEntry() {
            return entry;
        }
    }
}
