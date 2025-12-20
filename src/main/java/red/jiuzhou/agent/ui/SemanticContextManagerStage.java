package red.jiuzhou.agent.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.agent.texttosql.DynamicSemanticBuilder;
import red.jiuzhou.agent.texttosql.GameSemanticEnhancer;
import red.jiuzhou.agent.texttosql.TypeFieldDiscovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 语义上下文管理窗口
 *
 * 让设计师查看、启用/禁用、管理动态发现的语义映射
 *
 * @author Claude
 * @date 2025-12-20
 */
public class SemanticContextManagerStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(SemanticContextManagerStage.class);

    private GameSemanticEnhancer enhancer;
    private DynamicSemanticBuilder dynamicBuilder;
    private TypeFieldDiscovery discovery;
    private JdbcTemplate jdbcTemplate;

    // UI组件
    private ListView<DynamicSemanticBuilder.PresetContext> presetListView;
    private TableView<DynamicSemanticBuilder.SemanticMapping> mappingTableView;
    private TextArea detailsArea;
    private Label statsLabel;
    private ProgressIndicator loadingIndicator;

    public SemanticContextManagerStage(GameSemanticEnhancer enhancer, JdbcTemplate jdbcTemplate) {
        this.enhancer = enhancer;
        this.jdbcTemplate = jdbcTemplate;
        this.dynamicBuilder = enhancer.getDynamicBuilder();
        this.discovery = new TypeFieldDiscovery(jdbcTemplate);

        initUI();
        loadData();
    }

    private void initUI() {
        setTitle("AI上下文管理 - 动态语义映射");
        setWidth(1200);
        setHeight(800);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: white;");

        // 顶部工具栏
        root.setTop(createToolbar());

        // 中间：分割面板
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);

        // 左侧：预设上下文列表
        VBox leftPane = createPresetPane();
        leftPane.setPrefWidth(300);

        // 中间：语义映射表格
        VBox centerPane = createMappingPane();

        // 右侧：详细信息
        VBox rightPane = createDetailsPane();
        rightPane.setPrefWidth(350);

        splitPane.getItems().addAll(leftPane, centerPane, rightPane);
        splitPane.setDividerPositions(0.25, 0.75);

        root.setCenter(splitPane);

        // 底部状态栏
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root);
        setScene(scene);
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        Label titleLabel = new Label("🧠 AI上下文管理");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        Button refreshBtn = new Button("🔄 重新扫描");
        refreshBtn.setTooltip(new Tooltip("重新扫描数据库，发现新的type字段"));
        refreshBtn.setOnAction(e -> reloadSemantics());

        Button exportBtn = new Button("📥 导出报告");
        exportBtn.setTooltip(new Tooltip("导出type字段发现报告"));
        exportBtn.setOnAction(e -> exportReport());

        Button addCustomBtn = new Button("➕ 添加自定义");
        addCustomBtn.setTooltip(new Tooltip("添加用户自定义的语义映射"));
        addCustomBtn.setOnAction(e -> addCustomMapping());

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(20, 20);
        loadingIndicator.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(
            titleLabel,
            new Separator(),
            refreshBtn, exportBtn, addCustomBtn,
            spacer,
            loadingIndicator
        );

        return toolbar;
    }

    private VBox createPresetPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));
        pane.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 1 0 0;");

        Label label = new Label("📚 预设上下文");
        label.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        presetListView = new ListView<>();
        presetListView.setCellFactory(lv -> new PresetContextCell());
        presetListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                loadMappingsForPreset(newVal);
            }
        });

        VBox.setVgrow(presetListView, Priority.ALWAYS);

        pane.getChildren().addAll(label, presetListView);
        return pane;
    }

    private VBox createMappingPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        Label label = new Label("🔤 语义映射");
        label.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        mappingTableView = new TableView<>();
        mappingTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 启用列
        TableColumn<DynamicSemanticBuilder.SemanticMapping, Boolean> enabledCol = new TableColumn<>("启用");
        enabledCol.setPrefWidth(60);
        enabledCol.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleBooleanProperty(cellData.getValue().isEnabled()));
        enabledCol.setCellFactory(CheckBoxTableCell.forTableColumn(enabledCol));
        enabledCol.setEditable(true);

        // 关键词列
        TableColumn<DynamicSemanticBuilder.SemanticMapping, String> keywordCol = new TableColumn<>("关键词");
        keywordCol.setPrefWidth(120);
        keywordCol.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getKeyword()));

        // SQL条件列
        TableColumn<DynamicSemanticBuilder.SemanticMapping, String> sqlCol = new TableColumn<>("SQL条件");
        sqlCol.setPrefWidth(200);
        sqlCol.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getSqlCondition()));

        // 表名列
        TableColumn<DynamicSemanticBuilder.SemanticMapping, String> tableCol = new TableColumn<>("表");
        tableCol.setPrefWidth(100);
        tableCol.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTableName()));

        // 字段列
        TableColumn<DynamicSemanticBuilder.SemanticMapping, String> columnCol = new TableColumn<>("字段");
        columnCol.setPrefWidth(100);
        columnCol.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getColumnName()));

        // 使用次数列
        TableColumn<DynamicSemanticBuilder.SemanticMapping, String> useCountCol = new TableColumn<>("使用");
        useCountCol.setPrefWidth(60);
        useCountCol.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(String.valueOf(cellData.getValue().getUseCount())));

        mappingTableView.getColumns().addAll(enabledCol, keywordCol, sqlCol, tableCol, columnCol, useCountCol);
        mappingTableView.setEditable(true);

        // 选中映射时显示详情
        mappingTableView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                showMappingDetails(newVal);
            }
        });

        VBox.setVgrow(mappingTableView, Priority.ALWAYS);

        pane.getChildren().addAll(label, mappingTableView);
        return pane;
    }

    private VBox createDetailsPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));
        pane.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 0 1;");

        Label label = new Label("📋 详细信息");
        label.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11;");

        VBox.setVgrow(detailsArea, Priority.ALWAYS);

        pane.getChildren().addAll(label, detailsArea);
        return pane;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(15);
        statusBar.setPadding(new Insets(8));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");

        statsLabel = new Label("就绪");
        statsLabel.setStyle("-fx-text-fill: #666;");

        statusBar.getChildren().add(statsLabel);
        return statusBar;
    }

    /**
     * 加载数据
     */
    private void loadData() {
        if (dynamicBuilder == null) {
            showError("动态语义未初始化");
            return;
        }

        // 加载预设上下文
        Map<String, DynamicSemanticBuilder.PresetContext> presets = dynamicBuilder.getPresetContexts();
        presetListView.getItems().clear();
        presetListView.getItems().addAll(presets.values());

        // 选中第一个
        if (!presetListView.getItems().isEmpty()) {
            presetListView.getSelectionModel().selectFirst();
        }

        updateStats();
    }

    /**
     * 加载预设上下文的映射
     */
    private void loadMappingsForPreset(DynamicSemanticBuilder.PresetContext preset) {
        mappingTableView.getItems().clear();
        mappingTableView.getItems().addAll(preset.getMappings());

        detailsArea.setText(String.format(
            "预设: %s\n" +
            "分类: %s\n" +
            "描述: %s\n" +
            "状态: %s\n" +
            "映射数量: %d\n\n" +
            "点击下方的映射查看详细信息",
            preset.getName(),
            preset.getCategory(),
            preset.getDescription(),
            preset.isEnabled() ? "已启用" : "已禁用",
            preset.getMappings().size()
        ));
    }

    /**
     * 显示映射详情
     */
    private void showMappingDetails(DynamicSemanticBuilder.SemanticMapping mapping) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 语义映射详情 ===\n\n");
        sb.append(String.format("关键词: %s\n", mapping.getKeyword()));
        sb.append(String.format("SQL条件: %s\n\n", mapping.getSqlCondition()));
        sb.append(String.format("所属表: %s\n", mapping.getTableName()));
        sb.append(String.format("所属字段: %s\n\n", mapping.getColumnName()));
        sb.append(String.format("描述: %s\n\n", mapping.getDescription()));
        sb.append(String.format("状态: %s\n", mapping.isEnabled() ? "✅ 已启用" : "❌ 已禁用"));
        sb.append(String.format("类型: %s\n", mapping.isUserDefined() ? "👤 用户自定义" : "🤖 自动发现"));
        sb.append(String.format("使用次数: %d\n\n", mapping.getUseCount()));

        sb.append("--- 示例查询 ---\n");
        sb.append(String.format("用户: \"查询所有%s的数据\"\n", mapping.getKeyword()));
        sb.append(String.format("AI生成: SELECT * FROM %s WHERE %s LIMIT 20\n",
            mapping.getTableName(), mapping.getSqlCondition()));

        detailsArea.setText(sb.toString());
    }

    /**
     * 重新加载语义
     */
    private void reloadSemantics() {
        loadingIndicator.setVisible(true);
        statsLabel.setText("正在重新扫描数据库...");

        new Thread(() -> {
            try {
                enhancer.reloadDynamicSemantics();
                dynamicBuilder = enhancer.getDynamicBuilder();

                Platform.runLater(() -> {
                    loadData();
                    loadingIndicator.setVisible(false);
                    statsLabel.setText("重新扫描完成");
                    showInfo("动态语义已重新加载");
                });
            } catch (Exception e) {
                log.error("重新加载语义失败", e);
                Platform.runLater(() -> {
                    loadingIndicator.setVisible(false);
                    statsLabel.setText("扫描失败");
                    showError("重新加载失败: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * 导出报告
     */
    private void exportReport() {
        try {
            List<TypeFieldDiscovery.TypeFieldInfo> fields = discovery.discoverAllTypeFields();
            String report = discovery.generateSummaryReport(fields);

            // 显示在详情区
            detailsArea.setText(report);

            showInfo("报告已生成，显示在右侧详情区");
        } catch (Exception e) {
            log.error("导出报告失败", e);
            showError("导出失败: " + e.getMessage());
        }
    }

    /**
     * 添加自定义映射
     */
    private void addCustomMapping() {
        Dialog<DynamicSemanticBuilder.SemanticMapping> dialog = new Dialog<>();
        dialog.setTitle("添加自定义语义映射");
        dialog.setHeaderText("定义新的自然语言→SQL映射");

        ButtonType addButtonType = new ButtonType("添加", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField keywordField = new TextField();
        keywordField.setPromptText("例如: 传说武器");

        TextField sqlField = new TextField();
        sqlField.setPromptText("例如: quality = 4 AND item_type = 'weapon'");

        TextField tableField = new TextField();
        tableField.setPromptText("例如: item_weapons");

        TextField columnField = new TextField();
        columnField.setPromptText("例如: quality");

        TextField descField = new TextField();
        descField.setPromptText("例如: 传说品质的武器");

        grid.add(new Label("关键词:"), 0, 0);
        grid.add(keywordField, 1, 0);
        grid.add(new Label("SQL条件:"), 0, 1);
        grid.add(sqlField, 1, 1);
        grid.add(new Label("表名:"), 0, 2);
        grid.add(tableField, 1, 2);
        grid.add(new Label("字段名:"), 0, 3);
        grid.add(columnField, 1, 3);
        grid.add(new Label("描述:"), 0, 4);
        grid.add(descField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == addButtonType) {
                return new DynamicSemanticBuilder.SemanticMapping(
                    keywordField.getText(),
                    sqlField.getText(),
                    tableField.getText(),
                    columnField.getText(),
                    descField.getText()
                );
            }
            return null;
        });

        dialog.showAndWait().ifPresent(mapping -> {
            mapping.setUserDefined(true);
            dynamicBuilder.addUserMapping(
                mapping.getKeyword(),
                mapping.getSqlCondition(),
                mapping.getTableName(),
                mapping.getColumnName(),
                mapping.getDescription()
            );
            loadData();
            showInfo("自定义映射已添加");
        });
    }

    /**
     * 更新统计信息
     */
    private void updateStats() {
        int totalMappings = dynamicBuilder.getMappings().size();
        int enabledMappings = (int) dynamicBuilder.getMappings().values().stream()
            .filter(DynamicSemanticBuilder.SemanticMapping::isEnabled)
            .count();
        int presetCount = dynamicBuilder.getPresetContexts().size();

        statsLabel.setText(String.format(
            "预设上下文: %d  |  语义映射: %d  |  已启用: %d",
            presetCount, totalMappings, enabledMappings
        ));
    }

    /**
     * 预设上下文列表单元格
     */
    private class PresetContextCell extends ListCell<DynamicSemanticBuilder.PresetContext> {
        @Override
        protected void updateItem(DynamicSemanticBuilder.PresetContext preset, boolean empty) {
            super.updateItem(preset, empty);

            if (empty || preset == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox box = new VBox(5);
                box.setPadding(new Insets(5));

                HBox header = new HBox(10);
                header.setAlignment(Pos.CENTER_LEFT);

                CheckBox enabledBox = new CheckBox();
                enabledBox.setSelected(preset.isEnabled());
                enabledBox.setOnAction(e -> {
                    preset.setEnabled(enabledBox.isSelected());
                    updateStats();
                });

                Label nameLabel = new Label(preset.getName());
                nameLabel.setStyle("-fx-font-weight: bold;");

                Label countLabel = new Label(String.format("(%d)", preset.getMappings().size()));
                countLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11;");

                header.getChildren().addAll(enabledBox, nameLabel, countLabel);

                Label categoryLabel = new Label(preset.getCategory());
                categoryLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #999;");

                box.getChildren().addAll(header, categoryLabel);

                setGraphic(box);
            }
        }
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
