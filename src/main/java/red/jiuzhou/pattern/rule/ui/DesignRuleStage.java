package red.jiuzhou.pattern.rule.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.analysis.aion.AionMechanismCategory;
import red.jiuzhou.pattern.rule.engine.DesignRuleEngine;
import red.jiuzhou.pattern.rule.model.*;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 设计规则工作台
 *
 * 意图驱动设计系统的核心UI：
 * 1. 定义规则（条件 + 修改）
 * 2. 预览效果
 * 3. 批量执行
 * 4. 回滚管理
 */
public class DesignRuleStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(DesignRuleStage.class);

    private final JdbcTemplate jdbcTemplate;
    private final DesignRuleEngine engine;

    /**
     * 无参构造函数，使用DatabaseUtil获取JdbcTemplate
     */
    public DesignRuleStage() {
        this(DatabaseUtil.getJdbcTemplate(null));
    }

    // === UI 组件 ===
    private TextField ruleNameField;
    private ComboBox<String> mechanismCombo;
    private TextField tableNameField;
    private VBox conditionsContainer;
    private VBox modificationsContainer;
    private TextArea previewArea;
    private TableView<PreviewResult.RecordChange> changesTable;
    private Label statsLabel;
    private Button previewBtn;
    private Button executeBtn;
    private Button rollbackBtn;

    // === 数据 ===
    private DesignRule currentRule;
    private PreviewResult currentPreview;
    private final ObservableList<PreviewResult.RecordChange> changesData = FXCollections.observableArrayList();
    private final List<ConditionRow> conditionRows = new ArrayList<>();
    private final List<ModificationRow> modificationRows = new ArrayList<>();

    /** 当前表的字段列表（用于下拉选择） */
    private final ObservableList<String> currentTableFields = FXCollections.observableArrayList();

    /** 机制到表名的映射 */
    private static final Map<String, String> MECHANISM_TABLE_MAP = new LinkedHashMap<>();
    static {
        // 核心机制
        MECHANISM_TABLE_MAP.put("ITEM", "client_items");
        MECHANISM_TABLE_MAP.put("NPC", "client_npcs");
        MECHANISM_TABLE_MAP.put("SKILL", "client_skills");
        MECHANISM_TABLE_MAP.put("QUEST", "client_quest_data");
        MECHANISM_TABLE_MAP.put("DROP", "client_drop_templates");
        // 战斗系统
        MECHANISM_TABLE_MAP.put("ABYSS", "client_abyss_npclist");
        MECHANISM_TABLE_MAP.put("PVP", "client_pvp_rank");
        MECHANISM_TABLE_MAP.put("SIEGE", "client_siege_location");
        // 经济系统
        MECHANISM_TABLE_MAP.put("SHOP", "client_trade_list");
        MECHANISM_TABLE_MAP.put("RECIPE", "client_recipes");
        MECHANISM_TABLE_MAP.put("DECOMPOSE", "client_decomposable_items");
        // 成长系统
        MECHANISM_TABLE_MAP.put("PET", "client_toypets");
        MECHANISM_TABLE_MAP.put("MOUNT", "client_rides");
        MECHANISM_TABLE_MAP.put("HOUSING", "client_housing_objects");
        // 副本系统
        MECHANISM_TABLE_MAP.put("INSTANCE", "client_instance_cooltime");
        MECHANISM_TABLE_MAP.put("SPAWN", "client_spawn_data");
        // 活动系统
        MECHANISM_TABLE_MAP.put("EVENT", "client_events");
        MECHANISM_TABLE_MAP.put("GOTCHA", "client_gotchas");
    }

    /** 数值类型字段（用于智能推荐表达式） */
    private static final Set<String> NUMERIC_FIELD_PATTERNS = new HashSet<>(Arrays.asList(
        "id", "level", "price", "count", "rate", "hp", "mp", "attack", "defense",
        "damage", "heal", "duration", "cooldown", "stack", "weight", "quality"
    ));

    public DesignRuleStage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.engine = new DesignRuleEngine(jdbcTemplate);
        initUI();
        initNewRule();
    }

    private void initUI() {
        setTitle("📐 设计规则工作台 - 意图驱动批量修改");
        setWidth(1200);
        setHeight(800);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // 顶部：工具栏
        root.setTop(createToolbar());

        // 中央：主编辑区
        SplitPane centerPane = new SplitPane();
        centerPane.setDividerPositions(0.5);

        // 左侧：规则定义
        VBox rulePane = createRuleDefinitionPane();

        // 右侧：预览结果
        VBox previewPane = createPreviewPane();

        centerPane.getItems().addAll(rulePane, previewPane);
        root.setCenter(centerPane);

        // 底部：状态栏
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root);
        setScene(scene);
    }

    /**
     * 创建工具栏
     */
    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(5, 10, 10, 10));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button newBtn = new Button("📄 新建规则");
        newBtn.setOnAction(e -> initNewRule());

        Button loadBtn = new Button("📂 加载规则");
        loadBtn.setOnAction(e -> showLoadDialog());

        Button saveBtn = new Button("💾 保存规则");
        saveBtn.setOnAction(e -> saveCurrentRule());

        Separator sep1 = new Separator();
        sep1.setOrientation(javafx.geometry.Orientation.VERTICAL);

        previewBtn = new Button("👁️ 预览效果");
        previewBtn.setStyle("-fx-base: #4CAF50;");
        previewBtn.setOnAction(e -> doPreview());

        executeBtn = new Button("🚀 执行规则");
        executeBtn.setStyle("-fx-base: #2196F3;");
        executeBtn.setDisable(true);
        executeBtn.setOnAction(e -> doExecute());

        Separator sep2 = new Separator();
        sep2.setOrientation(javafx.geometry.Orientation.VERTICAL);

        rollbackBtn = new Button("↩️ 回滚历史");
        rollbackBtn.setOnAction(e -> showRollbackDialog());

        Button historyBtn = new Button("📜 执行记录");
        historyBtn.setOnAction(e -> showHistoryDialog());

        // 右侧：帮助
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button helpBtn = new Button("❓ 帮助");
        helpBtn.setOnAction(e -> showHelp());

        toolbar.getChildren().addAll(
            newBtn, loadBtn, saveBtn,
            sep1,
            previewBtn, executeBtn,
            sep2,
            rollbackBtn, historyBtn,
            spacer,
            helpBtn
        );

        return toolbar;
    }

    /**
     * 创建规则定义面板
     */
    private VBox createRuleDefinitionPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        // 规则基本信息
        TitledPane basicPane = new TitledPane();
        basicPane.setText("📋 规则信息");
        basicPane.setCollapsible(false);

        GridPane basicGrid = new GridPane();
        basicGrid.setHgap(10);
        basicGrid.setVgap(8);
        basicGrid.setPadding(new Insets(10));

        // 规则名称
        basicGrid.add(new Label("规则名称:"), 0, 0);
        ruleNameField = new TextField();
        ruleNameField.setPromptText("如：法师装备强化 v2.1");
        ruleNameField.setPrefWidth(300);
        basicGrid.add(ruleNameField, 1, 0);

        // 目标机制
        basicGrid.add(new Label("目标机制:"), 0, 1);
        mechanismCombo = new ComboBox<>();
        mechanismCombo.setEditable(false);
        mechanismCombo.setPrefWidth(200);
        populateMechanisms();
        basicGrid.add(mechanismCombo, 1, 1);

        // 目标表名（自动推断或手动输入）
        basicGrid.add(new Label("目标表名:"), 0, 2);
        tableNameField = new TextField();
        tableNameField.setPromptText("选择机制后自动填充");
        tableNameField.setPrefWidth(200);
        basicGrid.add(tableNameField, 1, 2);

        // 表名变化时加载字段
        setupTableNameListener();

        basicPane.setContent(basicGrid);

        // 作用范围（条件）
        TitledPane conditionPane = new TitledPane();
        conditionPane.setText("🎯 作用范围（满足以下条件的记录）");
        conditionPane.setCollapsible(false);

        VBox conditionBox = new VBox(5);
        conditionBox.setPadding(new Insets(10));

        conditionsContainer = new VBox(5);
        Button addConditionBtn = new Button("+ 添加条件");
        addConditionBtn.setOnAction(e -> addConditionRow());

        conditionBox.getChildren().addAll(conditionsContainer, addConditionBtn);
        conditionPane.setContent(conditionBox);

        // 修改规则
        TitledPane modifyPane = new TitledPane();
        modifyPane.setText("✏️ 修改规则");
        modifyPane.setCollapsible(false);

        VBox modifyBox = new VBox(5);
        modifyBox.setPadding(new Insets(10));

        modificationsContainer = new VBox(5);
        Button addModifyBtn = new Button("+ 添加修改");
        addModifyBtn.setOnAction(e -> addModificationRow());

        // 表达式帮助
        Label exprHelp = new Label("表达式示例: 当前值 × 1.2  |  当前值 + 50  |  ROUND(当前值 × 1.15)  |  CLAMP(当前值 × 1.5, 100, 9999)");
        exprHelp.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        modifyBox.getChildren().addAll(modificationsContainer, addModifyBtn, exprHelp);
        modifyPane.setContent(modifyBox);

        // 使用VBox.setVgrow让修改规则区域可扩展
        VBox.setVgrow(modifyPane, Priority.ALWAYS);

        pane.getChildren().addAll(basicPane, conditionPane, modifyPane);

        return pane;
    }

    /**
     * 创建预览面板
     */
    private VBox createPreviewPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        // 统计信息
        TitledPane statsPane = new TitledPane();
        statsPane.setText("📊 变更统计");
        statsPane.setCollapsible(false);

        statsLabel = new Label("点击「预览效果」查看变更统计");
        statsLabel.setWrapText(true);
        statsLabel.setPadding(new Insets(10));
        statsPane.setContent(statsLabel);

        // 变更列表
        TitledPane changesPane = new TitledPane();
        changesPane.setText("📋 变更详情");
        changesPane.setCollapsible(false);

        changesTable = new TableView<>();
        changesTable.setItems(changesData);
        changesTable.setPlaceholder(new Label("暂无数据"));

        TableColumn<PreviewResult.RecordChange, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cd -> new SimpleStringProperty(
            cd.getValue().getRecordId() != null ? cd.getValue().getRecordId().toString() : ""));
        idCol.setPrefWidth(80);

        TableColumn<PreviewResult.RecordChange, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getRecordName()));
        nameCol.setPrefWidth(150);

        TableColumn<PreviewResult.RecordChange, String> changeCol = new TableColumn<>("变更");
        changeCol.setCellValueFactory(cd -> {
            PreviewResult.RecordChange change = cd.getValue();
            StringBuilder sb = new StringBuilder();
            for (String field : change.getOriginalValues().keySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(field).append(": ")
                  .append(change.getOriginalValues().get(field))
                  .append(" → ")
                  .append(change.getNewValues().get(field));
            }
            return new SimpleStringProperty(sb.toString());
        });
        changeCol.setPrefWidth(300);

        changesTable.getColumns().addAll(idCol, nameCol, changeCol);

        changesPane.setContent(changesTable);
        VBox.setVgrow(changesPane, Priority.ALWAYS);

        // 预览摘要
        TitledPane summaryPane = new TitledPane();
        summaryPane.setText("📝 规则摘要");
        summaryPane.setExpanded(false);

        previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setPrefRowCount(6);
        previewArea.setWrapText(true);
        summaryPane.setContent(previewArea);

        pane.getChildren().addAll(statsPane, changesPane, summaryPane);

        return pane;
    }

    /**
     * 创建状态栏
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(8));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ddd; -fx-border-width: 1 0 0 0;");

        Label statusLabel = new Label("就绪");
        statusLabel.setStyle("-fx-text-fill: #666;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label tipLabel = new Label("提示: 先预览效果，确认无误后再执行");
        tipLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11px;");

        statusBar.getChildren().addAll(statusLabel, spacer, tipLabel);

        return statusBar;
    }

    // ========== 条件行组件 ==========

    /**
     * 条件行UI组件
     */
    private class ConditionRow extends HBox {
        ComboBox<String> logicCombo;
        ComboBox<String> fieldNameCombo;  // 改为下拉选择
        ComboBox<Condition.Operator> operatorCombo;
        TextField valueField;
        Button removeBtn;

        ConditionRow(boolean isFirst) {
            super(5);
            setAlignment(Pos.CENTER_LEFT);

            // 逻辑连接符（第一行不显示）
            logicCombo = new ComboBox<>();
            logicCombo.getItems().addAll("AND", "OR");
            logicCombo.setValue("AND");
            logicCombo.setVisible(!isFirst);
            logicCombo.setPrefWidth(60);

            // 字段名（下拉选择，支持输入）
            fieldNameCombo = new ComboBox<>(currentTableFields);
            fieldNameCombo.setEditable(true);
            fieldNameCombo.setPromptText("选择字段");
            fieldNameCombo.setPrefWidth(150);
            // 输入时自动过滤
            fieldNameCombo.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isEmpty() && !currentTableFields.contains(newVal)) {
                    // 自动过滤匹配的字段
                    List<String> filtered = currentTableFields.stream()
                        .filter(f -> f.toLowerCase().contains(newVal.toLowerCase()))
                        .collect(Collectors.toList());
                    if (!filtered.isEmpty() && !fieldNameCombo.isShowing()) {
                        fieldNameCombo.show();
                    }
                }
            });

            // 操作符
            operatorCombo = new ComboBox<>();
            operatorCombo.getItems().addAll(Condition.Operator.values());
            operatorCombo.setValue(Condition.Operator.EQUALS);
            operatorCombo.setPrefWidth(100);
            operatorCombo.setConverter(new javafx.util.StringConverter<Condition.Operator>() {
                @Override
                public String toString(Condition.Operator op) {
                    return op != null ? op.getDisplayName() : "";
                }
                @Override
                public Condition.Operator fromString(String s) {
                    return null;
                }
            });

            // 值
            valueField = new TextField();
            valueField.setPromptText("值");
            valueField.setPrefWidth(150);

            // 删除按钮
            removeBtn = new Button("✕");
            removeBtn.setStyle("-fx-text-fill: red;");
            removeBtn.setOnAction(e -> removeConditionRow(this));

            getChildren().addAll(logicCombo, fieldNameCombo, operatorCombo, valueField, removeBtn);
        }

        Condition toCondition() {
            Condition c = new Condition();
            String fieldName = fieldNameCombo.getValue();
            if (fieldName == null || fieldName.isEmpty()) {
                fieldName = fieldNameCombo.getEditor().getText();
            }
            c.setFieldName(fieldName != null ? fieldName.trim() : "");
            c.setOperator(operatorCombo.getValue());

            // 智能解析值
            String valStr = valueField.getText().trim();
            if (valStr.matches("-?\\d+")) {
                c.setValue(Integer.parseInt(valStr));
            } else if (valStr.matches("-?\\d+\\.\\d+")) {
                c.setValue(Double.parseDouble(valStr));
            } else {
                c.setValue(valStr);
            }

            c.setLogicOperator("OR".equals(logicCombo.getValue()) ?
                Condition.LogicOperator.OR : Condition.LogicOperator.AND);

            return c;
        }
    }

    private void addConditionRow() {
        ConditionRow row = new ConditionRow(conditionRows.isEmpty());
        conditionRows.add(row);
        conditionsContainer.getChildren().add(row);
    }

    private void removeConditionRow(ConditionRow row) {
        conditionRows.remove(row);
        conditionsContainer.getChildren().remove(row);
        // 更新第一行的逻辑符可见性
        if (!conditionRows.isEmpty()) {
            conditionRows.get(0).logicCombo.setVisible(false);
        }
    }

    // ========== 修改行组件 ==========

    /**
     * 修改规则行UI组件
     */
    private class ModificationRow extends HBox {
        ComboBox<String> fieldNameCombo;  // 改为下拉选择
        TextField expressionField;
        TextField descriptionField;
        Button removeBtn;

        ModificationRow() {
            super(5);
            setAlignment(Pos.CENTER_LEFT);

            // 字段名（下拉选择，支持输入）
            fieldNameCombo = new ComboBox<>(currentTableFields);
            fieldNameCombo.setEditable(true);
            fieldNameCombo.setPromptText("选择字段");
            fieldNameCombo.setPrefWidth(150);

            // 选择字段后智能推荐表达式
            fieldNameCombo.setOnAction(e -> {
                String selected = fieldNameCombo.getValue();
                if (selected != null && expressionField.getText().isEmpty()) {
                    // 如果是数值类型字段，推荐表达式
                    boolean isNumeric = NUMERIC_FIELD_PATTERNS.stream()
                        .anyMatch(p -> selected.toLowerCase().contains(p));
                    if (isNumeric) {
                        expressionField.setPromptText("如：当前值 * 1.2");
                    } else {
                        expressionField.setPromptText("新值");
                    }
                }
            });

            Label eqLabel = new Label("=");

            // 表达式
            expressionField = new TextField();
            expressionField.setPromptText("表达式，如：当前值 * 1.2");
            expressionField.setPrefWidth(200);

            // 描述
            descriptionField = new TextField();
            descriptionField.setPromptText("说明（可选）");
            descriptionField.setPrefWidth(150);

            // 删除按钮
            removeBtn = new Button("✕");
            removeBtn.setStyle("-fx-text-fill: red;");
            removeBtn.setOnAction(e -> removeModificationRow(this));

            getChildren().addAll(fieldNameCombo, eqLabel, expressionField, descriptionField, removeBtn);
        }

        FieldModification toModification() {
            FieldModification m = new FieldModification();
            String fieldName = fieldNameCombo.getValue();
            if (fieldName == null || fieldName.isEmpty()) {
                fieldName = fieldNameCombo.getEditor().getText();
            }
            m.setFieldName(fieldName != null ? fieldName.trim() : "");
            m.setExpression(expressionField.getText().trim());
            m.setDescription(descriptionField.getText().trim());
            return m;
        }
    }

    private void addModificationRow() {
        ModificationRow row = new ModificationRow();
        modificationRows.add(row);
        modificationsContainer.getChildren().add(row);
    }

    private void removeModificationRow(ModificationRow row) {
        modificationRows.remove(row);
        modificationsContainer.getChildren().remove(row);
    }

    // ========== 业务逻辑 ==========

    private void initNewRule() {
        currentRule = new DesignRule();
        currentPreview = null;

        ruleNameField.clear();
        mechanismCombo.getSelectionModel().clearSelection();
        tableNameField.clear();

        conditionRows.clear();
        conditionsContainer.getChildren().clear();

        modificationRows.clear();
        modificationsContainer.getChildren().clear();

        changesData.clear();
        statsLabel.setText("点击「预览效果」查看变更统计");
        previewArea.clear();

        executeBtn.setDisable(true);

        // 添加一个默认的条件行和修改行
        addConditionRow();
        addModificationRow();
    }

    private void populateMechanisms() {
        mechanismCombo.getItems().clear();

        // 从映射表中获取已配置的机制（优先显示）
        for (String mech : MECHANISM_TABLE_MAP.keySet()) {
            String displayName = getDisplayNameForMechanism(mech);
            mechanismCombo.getItems().add(mech + " - " + displayName);
        }

        // 添加Aion机制分类中其他的
        for (AionMechanismCategory cat : AionMechanismCategory.values()) {
            String item = cat.name() + " - " + cat.getDisplayName();
            if (!mechanismCombo.getItems().contains(item)) {
                mechanismCombo.getItems().add(item);
            }
        }

        // 选择机制后自动推断表名并加载字段
        mechanismCombo.setOnAction(e -> onMechanismSelected());
    }

    /**
     * 获取机制的显示名称
     */
    private String getDisplayNameForMechanism(String mech) {
        Map<String, String> names = new HashMap<>();
        names.put("ITEM", "物品");
        names.put("NPC", "NPC");
        names.put("SKILL", "技能");
        names.put("QUEST", "任务");
        names.put("DROP", "掉落");
        names.put("ABYSS", "深渊");
        names.put("PVP", "PVP");
        names.put("SIEGE", "攻城");
        names.put("SHOP", "商店");
        names.put("RECIPE", "配方");
        names.put("DECOMPOSE", "分解");
        names.put("PET", "宠物");
        names.put("MOUNT", "坐骑");
        names.put("HOUSING", "住宅");
        names.put("INSTANCE", "副本");
        names.put("SPAWN", "刷怪");
        names.put("EVENT", "活动");
        names.put("GOTCHA", "扭蛋");
        return names.getOrDefault(mech, mech);
    }

    /**
     * 选择机制后的处理
     */
    private void onMechanismSelected() {
        String selected = mechanismCombo.getValue();
        if (selected == null) return;

        String mechCode = selected.split(" - ")[0];

        // 自动推断表名
        String tableName = MECHANISM_TABLE_MAP.get(mechCode);
        if (tableName != null) {
            tableNameField.setText(tableName);
            tableNameField.setStyle("-fx-text-fill: #2196F3;");  // 蓝色表示自动推断

            // 异步加载字段列表
            loadTableFields(tableName);
        } else {
            tableNameField.clear();
            tableNameField.setStyle("");
            currentTableFields.clear();
        }
    }

    /**
     * 加载表的字段列表
     */
    private void loadTableFields(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            currentTableFields.clear();
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // 从数据库获取表的列信息
                String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                             "WHERE table_schema = current_schema() AND TABLE_NAME = ? " +
                             "ORDER BY ORDINAL_POSITION";

                List<String> fields = jdbcTemplate.queryForList(sql, String.class, tableName);

                Platform.runLater(() -> {
                    currentTableFields.clear();
                    currentTableFields.addAll(fields);
                    log.info("加载表 {} 的字段: {} 个", tableName, fields.size());
                });
            } catch (Exception e) {
                log.warn("加载表字段失败: {} - {}", tableName, e.getMessage());
                Platform.runLater(() -> currentTableFields.clear());
            }
        });
    }

    /**
     * 表名输入框变化时加载字段
     */
    private void setupTableNameListener() {
        tableNameField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String tableName = tableNameField.getText().trim();
                if (!tableName.isEmpty()) {
                    loadTableFields(tableName);
                }
            }
        });
    }

    /**
     * 获取ComboBox的文本值（支持可编辑模式）
     */
    private String getComboBoxText(ComboBox<String> combo) {
        String value = combo.getValue();
        if (value != null && !value.isEmpty()) {
            return value.trim();
        }
        // 如果是可编辑模式，获取编辑器中的文本
        if (combo.isEditable()) {
            String editorText = combo.getEditor().getText();
            return editorText != null ? editorText.trim() : "";
        }
        return "";
    }

    private DesignRule buildRuleFromUI() {
        DesignRule rule = new DesignRule();
        rule.setName(ruleNameField.getText().trim());

        // 解析机制
        String mechStr = mechanismCombo.getValue();
        if (mechStr != null && mechStr.contains(" - ")) {
            rule.setTargetMechanism(mechStr.split(" - ")[0]);
        }

        if (!tableNameField.getText().trim().isEmpty()) {
            rule.setTargetTable(tableNameField.getText().trim());
        }

        // 收集条件
        for (ConditionRow row : conditionRows) {
            String fieldName = getComboBoxText(row.fieldNameCombo);
            if (!fieldName.isEmpty()) {
                rule.addCondition(row.toCondition());
            }
        }

        // 收集修改
        for (ModificationRow row : modificationRows) {
            String fieldName = getComboBoxText(row.fieldNameCombo);
            if (!fieldName.isEmpty() && !row.expressionField.getText().trim().isEmpty()) {
                rule.addModification(row.toModification());
            }
        }

        return rule;
    }

    private void doPreview() {
        DesignRule rule = buildRuleFromUI();

        // 验证
        if (!rule.isValid()) {
            showAlert(Alert.AlertType.WARNING, "验证失败", String.join("\n", rule.getValidationErrors()));
            return;
        }

        previewBtn.setDisable(true);
        previewBtn.setText("预览中...");

        CompletableFuture.runAsync(() -> {
            PreviewResult result = engine.preview(rule);

            Platform.runLater(() -> {
                previewBtn.setDisable(false);
                previewBtn.setText("👁️ 预览效果");

                currentRule = rule;
                currentPreview = result;

                if (result.isSuccess()) {
                    // 更新统计
                    StringBuilder stats = new StringBuilder();
                    stats.append("匹配记录: ").append(result.getMatchedCount()).append(" 条\n\n");

                    if (!result.getFieldStats().isEmpty()) {
                        stats.append("字段变更:\n");
                        for (PreviewResult.FieldChangeStats fs : result.getFieldStats().values()) {
                            stats.append("  • ").append(fs.toStatsDescription()).append("\n");
                        }
                    }

                    if (!result.getWarnings().isEmpty()) {
                        stats.append("\n⚠️ 警告:\n");
                        for (String w : result.getWarnings()) {
                            stats.append("  • ").append(w).append("\n");
                        }
                    }

                    statsLabel.setText(stats.toString());

                    // 更新变更列表
                    changesData.clear();
                    changesData.addAll(result.getRecordChanges());

                    // 更新摘要
                    previewArea.setText(rule.toSummary() + "\n\n" + result.toSummary());

                    // 启用执行按钮
                    executeBtn.setDisable(result.getMatchedCount() == 0);

                } else {
                    showAlert(Alert.AlertType.ERROR, "预览失败", result.getErrorMessage());
                    executeBtn.setDisable(true);
                }
            });
        });
    }

    private void doExecute() {
        if (currentRule == null || currentPreview == null) {
            showAlert(Alert.AlertType.WARNING, "提示", "请先预览规则效果");
            return;
        }

        // 确认对话框
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认执行");
        confirm.setHeaderText("即将执行规则: " + currentRule.getName());
        confirm.setContentText("将修改 " + currentPreview.getMatchedCount() + " 条记录，是否继续？");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            executeBtn.setDisable(true);
            executeBtn.setText("执行中...");

            CompletableFuture.runAsync(() -> {
                ExecutionResult execResult = engine.execute(currentRule);

                Platform.runLater(() -> {
                    executeBtn.setDisable(false);
                    executeBtn.setText("🚀 执行规则");

                    if (execResult.isSuccess()) {
                        showAlert(Alert.AlertType.INFORMATION, "执行成功",
                            "成功修改 " + execResult.getAffectedCount() + " 条记录\n" +
                            "执行ID: " + execResult.getExecutionId());

                        // 重置界面
                        initNewRule();
                    } else {
                        showAlert(Alert.AlertType.ERROR, "执行失败", execResult.getErrorMessage());
                    }
                });
            });
        }
    }

    private void showLoadDialog() {
        showAlert(Alert.AlertType.INFORMATION, "加载规则", "规则库功能开发中...");
    }

    private void saveCurrentRule() {
        DesignRule rule = buildRuleFromUI();
        if (!rule.isValid()) {
            showAlert(Alert.AlertType.WARNING, "验证失败", String.join("\n", rule.getValidationErrors()));
            return;
        }
        showAlert(Alert.AlertType.INFORMATION, "保存规则", "规则保存功能开发中...\n\n规则预览:\n" + rule.toSummary());
    }

    private void showRollbackDialog() {
        List<ExecutionResult> rollbackable = engine.getRollbackableExecutions();
        if (rollbackable.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "回滚", "没有可回滚的执行记录");
            return;
        }

        // 简单的选择对话框
        ChoiceDialog<ExecutionResult> dialog = new ChoiceDialog<>(rollbackable.get(0), rollbackable);
        dialog.setTitle("选择回滚");
        dialog.setHeaderText("选择要回滚的执行");
        dialog.setContentText("执行记录:");

        Optional<ExecutionResult> result = dialog.showAndWait();
        if (result.isPresent()) {
            ExecutionResult toRollback = result.get();

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("确认回滚");
            confirm.setHeaderText("即将回滚: " + toRollback.getRuleName());
            confirm.setContentText("将恢复 " + toRollback.getAffectedCount() + " 条记录，是否继续？");

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                ExecutionResult rollbackResult = engine.rollback(toRollback.getExecutionId());
                if (rollbackResult.isSuccess()) {
                    showAlert(Alert.AlertType.INFORMATION, "回滚成功",
                        "成功恢复 " + rollbackResult.getAffectedCount() + " 条记录");
                } else {
                    showAlert(Alert.AlertType.ERROR, "回滚失败", rollbackResult.getErrorMessage());
                }
            }
        }
    }

    private void showHistoryDialog() {
        List<ExecutionResult> history = engine.getExecutionHistory();
        if (history.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "执行记录", "暂无执行记录");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (ExecutionResult r : history) {
            sb.append(r.toAuditLog()).append("\n");
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("执行记录");
        alert.setHeaderText("最近 " + history.size() + " 条执行记录");

        TextArea area = new TextArea(sb.toString());
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(15);
        alert.getDialogPane().setContent(area);
        alert.showAndWait();
    }

    private void showHelp() {
        StringBuilder sb = new StringBuilder();
        sb.append("设计规则工作台 使用说明\n");
        sb.append("========================================\n\n");
        sb.append("【核心理念】\n");
        sb.append("设计师表达\"想要什么\"，系统自动完成\"怎么做\"\n\n");
        sb.append("【工作流程】\n");
        sb.append("1. 定义规则名称和目标机制\n");
        sb.append("2. 设置筛选条件（哪些记录会被修改）\n");
        sb.append("3. 设置修改规则（如何修改）\n");
        sb.append("4. 点击「预览效果」查看变更\n");
        sb.append("5. 确认无误后点击「执行规则」\n\n");
        sb.append("【表达式语法】\n");
        sb.append("  当前值 * 1.2      - 提升20%\n");
        sb.append("  当前值 + 50       - 固定加50\n");
        sb.append("  当前值 - 10%      - 降低10%\n");
        sb.append("  ROUND(当前值 * 1.15)  - 四舍五入\n");
        sb.append("  FLOOR(当前值 * 1.15)  - 向下取整\n");
        sb.append("  CLAMP(当前值 * 1.5, 100, 9999)  - 限制范围\n\n");
        sb.append("【条件示例】\n");
        sb.append("  职业限制 = 法师\n");
        sb.append("  等级需求 >= 50\n");
        sb.append("  物品类型 IN (武器,防具)\n\n");
        sb.append("【安全保障】\n");
        sb.append("  所有修改都会先预览\n");
        sb.append("  执行后支持一键回滚\n");
        sb.append("  详细的执行日志\n");
        String help = sb.toString();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("使用帮助");
        alert.setHeaderText(null);

        TextArea area = new TextArea(help);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(25);
        area.setPrefWidth(500);
        alert.getDialogPane().setContent(area);
        alert.showAndWait();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
