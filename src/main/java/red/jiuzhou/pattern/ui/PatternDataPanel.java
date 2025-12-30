package red.jiuzhou.pattern.ui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.pattern.dao.*;
import red.jiuzhou.pattern.model.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * 模式数据展示面板
 *
 * 用于在机制浏览器中展示已收集的领域数据模式：
 * - 机制级别：字段数、样本数、收集状态
 * - 字段级别：推断类型、值域分布、引用关系
 * - 属性字典：126个属性的使用统计
 */
public class PatternDataPanel extends VBox {
    private static final Logger log = LoggerFactory.getLogger(PatternDataPanel.class);

    // DAO层
    private final PatternSchemaDao schemaDao;
    private final PatternFieldDao fieldDao;
    private final PatternValueDao valueDao;
    private final PatternRefDao refDao;
    private final AttrDictionaryDao attrDao;

    // UI组件
    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private VBox schemaInfoBox;
    private TableView<PatternField> fieldPatternTable;
    private FlowPane valueDistributionPane;
    private VBox attrUsageBox;
    private TabPane tabPane;

    // 当前数据
    private PatternSchema currentSchema;
    private String currentMechanismCode;

    // 缓存
    private final Map<String, PatternSchema> schemaCache = new HashMap<>();
    private final Map<Integer, List<PatternField>> fieldCache = new HashMap<>();
    private List<AttrDictionary> attrDictionaryCache = null;
    private boolean attrDictionaryLoaded = false;
    private volatile boolean isLoading = false;

    public PatternDataPanel() {
        this.schemaDao = new PatternSchemaDao();
        this.fieldDao = new PatternFieldDao();
        this.valueDao = new PatternValueDao();
        this.refDao = new PatternRefDao();
        this.attrDao = new AttrDictionaryDao();

        initUI();
    }

    /**
     * 初始化UI
     */
    private void initUI() {
        this.setSpacing(10);
        this.setPadding(new Insets(10));
        this.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-border-color: #e0e0e0; -fx-border-radius: 8;");

        // 标题栏
        HBox headerBox = createHeaderBox();

        // Tab面板
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab1: 模式概览
        Tab overviewTab = new Tab("模式概览");
        overviewTab.setContent(createOverviewPane());

        // Tab2: 字段模式
        Tab fieldTab = new Tab("字段模式");
        fieldTab.setContent(createFieldPatternPane());

        // Tab3: 值域分布
        Tab valueTab = new Tab("值域分布");
        valueTab.setContent(createValueDistributionPane());

        // Tab4: 属性词典
        Tab attrTab = new Tab("属性词典");
        attrTab.setContent(createAttrDictionaryPane());

        tabPane.getTabs().addAll(overviewTab, fieldTab, valueTab, attrTab);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        this.getChildren().addAll(headerBox, tabPane);
    }

    /**
     * 创建标题栏
     */
    private HBox createHeaderBox() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(5, 0, 5, 0));

        Label titleLabel = new Label("领域模式分析");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));
        titleLabel.setStyle("-fx-text-fill: #8e44ad;");

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(18, 18);
        progressIndicator.setVisible(false);

        statusLabel = new Label("未加载");
        statusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-cursor: hand; -fx-font-size: 11px;");
        refreshBtn.setOnAction(e -> refresh());

        Button collectBtn = new Button("📊 收集模式");
        collectBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; " +
                "-fx-cursor: hand; -fx-font-size: 11px;");
        collectBtn.setOnAction(e -> collectPatterns());

        box.getChildren().addAll(titleLabel, progressIndicator, statusLabel, spacer, refreshBtn, collectBtn);
        return box;
    }

    /**
     * 创建模式概览面板
     */
    private VBox createOverviewPane() {
        schemaInfoBox = new VBox(10);
        schemaInfoBox.setPadding(new Insets(10));

        // 初始状态提示
        Label hintLabel = new Label("选择一个机制分类以查看其模式数据");
        hintLabel.setStyle("-fx-text-fill: #95a5a6;");
        schemaInfoBox.getChildren().add(hintLabel);

        ScrollPane scrollPane = new ScrollPane(schemaInfoBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        VBox container = new VBox(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        return container;
    }

    /**
     * 创建字段模式面板
     */
    private VBox createFieldPatternPane() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));

        fieldPatternTable = createFieldPatternTable();
        VBox.setVgrow(fieldPatternTable, Priority.ALWAYS);

        box.getChildren().add(fieldPatternTable);
        return box;
    }

    /**
     * 创建字段模式表格
     */
    @SuppressWarnings("unchecked")
    private TableView<PatternField> createFieldPatternTable() {
        TableView<PatternField> table = new TableView<>();

        // 字段名列
        TableColumn<PatternField, String> nameCol = new TableColumn<>("字段名");
        nameCol.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue().getFieldName()));
        nameCol.setPrefWidth(150);

        // 推断类型列
        TableColumn<PatternField, String> typeCol = new TableColumn<>("推断类型");
        typeCol.setCellValueFactory(data -> {
            PatternField.FieldType type = data.getValue().getInferredType();
            return new javafx.beans.property.SimpleStringProperty(
                    type != null ? type.name() : "未知");
        });
        typeCol.setPrefWidth(100);
        typeCol.setCellFactory(col -> new TableCell<PatternField, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    // 根据类型设置颜色
                    String color = getTypeColor(item);
                    setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                }
            }
        });

        // 值域类型列
        TableColumn<PatternField, String> domainCol = new TableColumn<>("值域类型");
        domainCol.setCellValueFactory(data -> {
            PatternField.ValueDomainType domain = data.getValue().getValueDomainType();
            return new javafx.beans.property.SimpleStringProperty(
                    domain != null ? domain.name() : "-");
        });
        domainCol.setPrefWidth(80);

        // 样本数列
        TableColumn<PatternField, String> countCol = new TableColumn<>("样本数");
        countCol.setCellValueFactory(data -> {
            Integer count = data.getValue().getTotalCount();
            return new javafx.beans.property.SimpleStringProperty(
                    count != null ? String.valueOf(count) : "0");
        });
        countCol.setPrefWidth(60);

        // 空值率列
        TableColumn<PatternField, String> nullRateCol = new TableColumn<>("空值率");
        nullRateCol.setCellValueFactory(data -> {
            BigDecimal rate = data.getValue().getNullRate();
            if (rate != null) {
                return new javafx.beans.property.SimpleStringProperty(
                        String.format("%.1f%%", rate.doubleValue() * 100));
            }
            return new javafx.beans.property.SimpleStringProperty("-");
        });
        nullRateCol.setPrefWidth(60);

        // 引用目标列
        TableColumn<PatternField, String> refCol = new TableColumn<>("引用目标");
        refCol.setCellValueFactory(data -> {
            String ref = data.getValue().getReferenceTarget();
            return new javafx.beans.property.SimpleStringProperty(ref != null ? ref : "");
        });
        refCol.setPrefWidth(100);
        refCol.setCellFactory(col -> new TableCell<PatternField, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setStyle("");
                } else {
                    setText("→ " + item);
                    setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
            }
        });

        table.getColumns().addAll(nameCol, typeCol, domainCol, countCol, nullRateCol, refCol);

        // 双击查看字段详情
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                PatternField selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    showFieldDetail(selected);
                }
            }
        });

        return table;
    }

    /**
     * 创建值域分布面板
     */
    private VBox createValueDistributionPane() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));

        Label hintLabel = new Label("在字段模式中双击字段查看值域分布");
        hintLabel.setStyle("-fx-text-fill: #95a5a6;");

        valueDistributionPane = new FlowPane();
        valueDistributionPane.setHgap(10);
        valueDistributionPane.setVgap(10);

        ScrollPane scrollPane = new ScrollPane(valueDistributionPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        box.getChildren().addAll(hintLabel, scrollPane);
        return box;
    }

    /**
     * 创建属性词典面板
     */
    private VBox createAttrDictionaryPane() {
        attrUsageBox = new VBox(10);
        attrUsageBox.setPadding(new Insets(10));

        Label hintLabel = new Label("属性词典数据（126个服务端属性）");
        hintLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-weight: bold;");

        ScrollPane scrollPane = new ScrollPane(attrUsageBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        VBox container = new VBox(10);
        container.setPadding(new Insets(10));
        container.getChildren().addAll(hintLabel, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        return container;
    }

    /**
     * 加载指定机制的模式数据（带缓存优化）
     */
    public void loadMechanismPattern(String mechanismCode) {
        if (mechanismCode == null || mechanismCode.equals(currentMechanismCode)) {
            return;
        }

        // 防止并发加载
        if (isLoading) {
            return;
        }

        this.currentMechanismCode = mechanismCode;

        // 先检查缓存
        PatternSchema cachedSchema = schemaCache.get(mechanismCode);
        if (cachedSchema != null) {
            // 从缓存加载，无需异步
            currentSchema = cachedSchema;
            updateOverviewPane(currentSchema);
            loadFieldPatternsFromCache(currentSchema.getId());
            statusLabel.setText("已加载: " + mechanismCode + " (缓存)");
            return;
        }

        // 缓存未命中，异步加载
        isLoading = true;
        progressIndicator.setVisible(true);
        statusLabel.setText("加载中...");

        CompletableFuture.runAsync(() -> {
            try {
                // 查询模式schema
                Optional<PatternSchema> schemaOpt = schemaDao.findByMechanismCode(mechanismCode);

                if (schemaOpt.isPresent()) {
                    PatternSchema schema = schemaOpt.get();
                    // 加入缓存
                    schemaCache.put(mechanismCode, schema);

                    // 预加载字段数据到缓存
                    List<PatternField> fields = fieldDao.findBySchemaId(schema.getId());
                    fieldCache.put(schema.getId(), fields);

                    Platform.runLater(() -> {
                        currentSchema = schema;
                        updateOverviewPane(currentSchema);
                        fieldPatternTable.setItems(FXCollections.observableArrayList(fields));
                        statusLabel.setText("已加载: " + mechanismCode);
                        progressIndicator.setVisible(false);
                        isLoading = false;
                    });
                } else {
                    Platform.runLater(() -> {
                        currentSchema = null;
                        showNoDataHint(mechanismCode);
                        statusLabel.setText("未收集: " + mechanismCode);
                        progressIndicator.setVisible(false);
                        isLoading = false;
                    });
                }

            } catch (Exception e) {
                log.error("加载模式数据失败", e);
                Platform.runLater(() -> {
                    statusLabel.setText("加载失败: " + e.getMessage());
                    progressIndicator.setVisible(false);
                    isLoading = false;
                });
            }
        });
    }

    /**
     * 从缓存加载字段模式
     */
    private void loadFieldPatternsFromCache(Integer schemaId) {
        List<PatternField> cached = fieldCache.get(schemaId);
        if (cached != null) {
            fieldPatternTable.setItems(FXCollections.observableArrayList(cached));
        } else {
            // 缓存未命中，异步加载
            loadFieldPatterns(schemaId);
        }
    }

    /**
     * 更新概览面板
     */
    private void updateOverviewPane(PatternSchema schema) {
        schemaInfoBox.getChildren().clear();

        // 机制信息卡片
        VBox infoCard = new VBox(8);
        infoCard.setPadding(new Insets(15));
        infoCard.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8; " +
                "-fx-border-color: #e0e0e0; -fx-border-radius: 8;");

        Label titleLabel = new Label(schema.getMechanismIcon() + " " + schema.getMechanismName());
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        titleLabel.setStyle("-fx-text-fill: #2c3e50;");

        Label codeLabel = new Label("机制代码: " + schema.getMechanismCode());
        codeLabel.setStyle("-fx-text-fill: #7f8c8d;");

        infoCard.getChildren().addAll(titleLabel, codeLabel);

        // 统计卡片行
        HBox statsRow = new HBox(15);
        statsRow.setPadding(new Insets(10, 0, 0, 0));

        VBox fileCountCard = createStatCard("📁", "文件数",
                String.valueOf(schema.getFileCount()), "#3498db");
        VBox fieldCountCard = createStatCard("📋", "字段数",
                String.valueOf(schema.getFieldCount()), "#27ae60");
        VBox sampleCountCard = createStatCard("📊", "样本数",
                String.valueOf(schema.getSampleCount()), "#e67e22");

        statsRow.getChildren().addAll(fileCountCard, fieldCountCard, sampleCountCard);

        // 典型字段
        if (schema.getTypicalFields() != null && !schema.getTypicalFields().isEmpty()) {
            VBox typicalBox = new VBox(5);
            typicalBox.setPadding(new Insets(10, 0, 0, 0));

            Label typicalLabel = new Label("典型字段:");
            typicalLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-weight: bold;");

            FlowPane fieldsPane = new FlowPane();
            fieldsPane.setHgap(5);
            fieldsPane.setVgap(5);

            String[] fields = schema.getTypicalFields().split(",");
            for (String field : fields) {
                Label tag = new Label(field.trim());
                tag.setPadding(new Insets(3, 8, 3, 8));
                tag.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 10; " +
                        "-fx-text-fill: #2c3e50; -fx-font-size: 11px;");
                fieldsPane.getChildren().add(tag);
            }

            typicalBox.getChildren().addAll(typicalLabel, fieldsPane);
            infoCard.getChildren().add(typicalBox);
        }

        schemaInfoBox.getChildren().addAll(infoCard, statsRow);
    }

    /**
     * 创建统计卡片
     */
    private VBox createStatCard(String icon, String label, String value, String color) {
        VBox card = new VBox(3);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(10, 20, 10, 20));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-border-color: " + color + "; -fx-border-radius: 8; -fx-border-width: 2;");

        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font(20));

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
        valueLabel.setStyle("-fx-text-fill: " + color + ";");

        Label labelLabel = new Label(label);
        labelLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11px;");

        card.getChildren().addAll(iconLabel, valueLabel, labelLabel);
        return card;
    }

    /**
     * 显示无数据提示
     */
    private void showNoDataHint(String mechanismCode) {
        schemaInfoBox.getChildren().clear();
        fieldPatternTable.getItems().clear();

        VBox hintBox = new VBox(15);
        hintBox.setAlignment(Pos.CENTER);
        hintBox.setPadding(new Insets(30));

        Label iconLabel = new Label("📭");
        iconLabel.setFont(Font.font(48));

        Label hintLabel = new Label("尚未收集 [" + mechanismCode + "] 的模式数据");
        hintLabel.setStyle("-fx-text-fill: #95a5a6; -fx-font-size: 14px;");

        Label tipLabel = new Label("点击「收集模式」按钮开始收集");
        tipLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 12px;");

        hintBox.getChildren().addAll(iconLabel, hintLabel, tipLabel);
        schemaInfoBox.getChildren().add(hintBox);
    }

    /**
     * 加载字段模式
     */
    private void loadFieldPatterns(Integer schemaId) {
        CompletableFuture.runAsync(() -> {
            try {
                List<PatternField> fields = fieldDao.findBySchemaId(schemaId);

                Platform.runLater(() -> {
                    fieldPatternTable.setItems(FXCollections.observableArrayList(fields));
                });

            } catch (Exception e) {
                log.error("加载字段模式失败", e);
            }
        });
    }

    /**
     * 显示字段详情
     */
    private void showFieldDetail(PatternField field) {
        // 切换到值域分布Tab
        tabPane.getSelectionModel().select(2);

        valueDistributionPane.getChildren().clear();

        // 字段信息
        VBox infoBox = new VBox(8);
        infoBox.setPadding(new Insets(15));
        infoBox.setMinWidth(300);
        infoBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8;");

        Label nameLabel = new Label("字段: " + field.getFieldName());
        nameLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        Label typeLabel = new Label("类型: " + (field.getInferredType() != null ?
                field.getInferredType().name() : "未知"));
        typeLabel.setStyle("-fx-text-fill: " + getTypeColor(
                field.getInferredType() != null ? field.getInferredType().name() : "") + ";");

        infoBox.getChildren().addAll(nameLabel, typeLabel);

        // 加载值分布
        CompletableFuture.runAsync(() -> {
            try {
                List<PatternValue> values = valueDao.findTopByFieldId(field.getId(), 20);

                Platform.runLater(() -> {
                    if (values.isEmpty()) {
                        Label noDataLabel = new Label("无值分布数据");
                        noDataLabel.setStyle("-fx-text-fill: #95a5a6;");
                        infoBox.getChildren().add(noDataLabel);
                    } else {
                        Label distLabel = new Label("值分布 (Top " + values.size() + "):");
                        distLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-weight: bold;");
                        infoBox.getChildren().add(distLabel);

                        for (PatternValue pv : values) {
                            HBox valueRow = new HBox(10);
                            valueRow.setAlignment(Pos.CENTER_LEFT);

                            // 值标签
                            Label valueLabel = new Label(truncate(pv.getValueContent(), 30));
                            valueLabel.setMinWidth(150);

                            // 百分比条
                            double pct = pv.getPercentage() != null ?
                                    pv.getPercentage().doubleValue() : 0;
                            ProgressBar bar = new ProgressBar(pct);
                            bar.setPrefWidth(100);
                            bar.setStyle("-fx-accent: #3498db;");

                            // 数量标签
                            Label countLabel = new Label(String.format("%d (%.1f%%)",
                                    pv.getOccurrenceCount(), pct * 100));
                            countLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11px;");

                            valueRow.getChildren().addAll(valueLabel, bar, countLabel);
                            infoBox.getChildren().add(valueRow);
                        }
                    }
                });

            } catch (Exception e) {
                log.error("加载值分布失败", e);
            }
        });

        valueDistributionPane.getChildren().add(infoBox);
    }

    /**
     * 加载属性词典（带缓存）
     */
    public void loadAttrDictionary() {
        // 如果已加载且有缓存，直接使用缓存渲染
        if (attrDictionaryLoaded && attrDictionaryCache != null) {
            renderAttrDictionary(attrDictionaryCache);
            return;
        }

        attrUsageBox.getChildren().clear();

        // 显示加载提示
        Label loadingLabel = new Label("正在加载属性词典...");
        loadingLabel.setStyle("-fx-text-fill: #7f8c8d;");
        attrUsageBox.getChildren().add(loadingLabel);

        CompletableFuture.runAsync(() -> {
            try {
                List<AttrDictionary> attrs = attrDao.findAll();
                // 缓存结果
                attrDictionaryCache = attrs;
                attrDictionaryLoaded = true;

                Platform.runLater(() -> renderAttrDictionary(attrs));

            } catch (Exception e) {
                log.error("加载属性词典失败", e);
                Platform.runLater(() -> {
                    attrUsageBox.getChildren().clear();
                    Label errorLabel = new Label("加载失败: " + e.getMessage());
                    errorLabel.setStyle("-fx-text-fill: #e74c3c;");
                    attrUsageBox.getChildren().add(errorLabel);
                });
            }
        });
    }

    /**
     * 渲染属性词典（从数据或缓存）
     */
    private void renderAttrDictionary(List<AttrDictionary> attrs) {
        attrUsageBox.getChildren().clear();

        if (attrs == null || attrs.isEmpty()) {
            Label emptyLabel = new Label("尚未收集属性词典数据");
            emptyLabel.setStyle("-fx-text-fill: #95a5a6;");
            attrUsageBox.getChildren().add(emptyLabel);
            return;
        }

        // 按分类分组
        Map<String, List<AttrDictionary>> byCategory = new LinkedHashMap<>();
        for (AttrDictionary attr : attrs) {
            String cat = attr.getAttrCategory() != null ? attr.getAttrCategory() : "其他";
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(attr);
        }

        for (Map.Entry<String, List<AttrDictionary>> entry : byCategory.entrySet()) {
            // 分类标题
            Label catLabel = new Label(entry.getKey() + " (" + entry.getValue().size() + "个)");
            catLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 12));
            catLabel.setStyle("-fx-text-fill: #2c3e50;");
            catLabel.setPadding(new Insets(10, 0, 5, 0));

            // 属性列表
            FlowPane attrPane = new FlowPane();
            attrPane.setHgap(8);
            attrPane.setVgap(8);

            for (AttrDictionary attr : entry.getValue()) {
                VBox attrCard = createAttrCard(attr);
                attrPane.getChildren().add(attrCard);
            }

            attrUsageBox.getChildren().addAll(catLabel, attrPane);
        }
    }

    /**
     * 创建属性卡片
     */
    private VBox createAttrCard(AttrDictionary attr) {
        VBox card = new VBox(2);
        card.setPadding(new Insets(8));
        card.setMinWidth(120);
        card.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5; " +
                "-fx-border-color: #e0e0e0; -fx-border-radius: 5;");

        Label codeLabel = new Label(attr.getAttrCode());
        codeLabel.setStyle("-fx-text-fill: #2c3e50; -fx-font-weight: bold; -fx-font-size: 11px;");

        Label nameLabel = new Label(attr.getAttrName() != null ? attr.getAttrName() : "");
        nameLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 10px;");

        int usage = (attr.getUsedInItems() != null ? attr.getUsedInItems() : 0) +
                (attr.getUsedInPets() != null ? attr.getUsedInPets() : 0) +
                (attr.getTotalUsage() != null ? attr.getTotalUsage() : 0);

        Label usageLabel = new Label("使用: " + usage + "次");
        usageLabel.setStyle("-fx-text-fill: #3498db; -fx-font-size: 10px;");

        card.getChildren().addAll(codeLabel, nameLabel, usageLabel);

        // 悬停提示
        Tooltip tooltip = new Tooltip(String.format(
                "属性: %s\n名称: %s\n分类: %s\n范围: %s ~ %s",
                attr.getAttrCode(),
                attr.getAttrName(),
                attr.getAttrCategory(),
                attr.getTypicalMin() != null ? attr.getTypicalMin() : "?",
                attr.getTypicalMax() != null ? attr.getTypicalMax() : "?"
        ));
        Tooltip.install(card, tooltip);

        return card;
    }

    /**
     * 刷新数据（清除缓存后重新加载）
     */
    public void refresh() {
        // 清除缓存
        clearCache();

        if (currentMechanismCode != null) {
            String code = currentMechanismCode;
            currentMechanismCode = null; // 强制重新加载
            loadMechanismPattern(code);
        }
        loadAttrDictionary();
    }

    /**
     * 清除所有缓存
     */
    public void clearCache() {
        schemaCache.clear();
        fieldCache.clear();
        attrDictionaryCache = null;
        attrDictionaryLoaded = false;
    }

    /**
     * 触发模式收集
     */
    private void collectPatterns() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("模式收集");
        alert.setHeaderText("开始收集模式数据");
        alert.setContentText("模式收集功能将在后台运行。\n" +
                "这可能需要几分钟时间，请稍候...\n\n" +
                "收集完成后请点击「刷新」查看结果。");
        alert.showAndWait();

        // TODO: 集成PatternCollectorService进行实际收集
        statusLabel.setText("模式收集进行中...");
    }

    /**
     * 获取类型对应的颜色
     */
    private String getTypeColor(String type) {
        if (type == null) return "#7f8c8d";
        switch (type) {
            case "INTEGER": return "#3498db";
            case "DECIMAL": return "#9b59b6";
            case "BOOLEAN": return "#27ae60";
            case "ENUM": return "#e67e22";
            case "REFERENCE": return "#e74c3c";
            case "BONUS_ATTR": return "#f39c12";
            default: return "#7f8c8d";
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }
}
