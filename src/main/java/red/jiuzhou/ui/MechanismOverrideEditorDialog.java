package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.analysis.aion.AionMechanismCategory;
import red.jiuzhou.analysis.aion.MechanismOverrideConfig;

import java.util.Map;
import java.util.Optional;

/**
 * 机制覆盖配置编辑器对话框
 *
 * 功能：
 * 1. 图形化管理手动覆盖配置
 * 2. 添加/删除文件到机制分类
 * 3. 管理排除文件列表
 * 4. 保存配置到YAML文件
 *
 * @author Claude Sonnet 4.5
 * @date 2025-12-21
 */
public class MechanismOverrideEditorDialog extends Stage {
    private static final Logger log = LoggerFactory.getLogger(MechanismOverrideEditorDialog.class);

    private final MechanismOverrideConfig config;

    // 手动覆盖表格
    private TableView<OverrideEntry> overrideTable;
    private ObservableList<OverrideEntry> overrideData;

    // 排除文件表格
    private TableView<ExcludedEntry> excludedTable;
    private ObservableList<ExcludedEntry> excludedData;

    // 统计标签
    private Label statsLabel;

    /**
     * 构造函数
     */
    public MechanismOverrideEditorDialog() {
        this.config = MechanismOverrideConfig.getInstance();

        setTitle("机制分类管理器");
        setWidth(900);
        setHeight(600);
        initModality(Modality.APPLICATION_MODAL);

        // 创建UI
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        // 标题和说明
        Label titleLabel = new Label("🎮 游戏机制分类管理");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label descLabel = new Label("管理XML文件的机制分类配置，修改后需要重启应用生效");
        descLabel.setStyle("-fx-text-fill: #666;");

        // 统计信息
        statsLabel = new Label();
        updateStats();

        // 创建Tab页
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1: 手动覆盖
        Tab overrideTab = new Tab("手动覆盖配置");
        overrideTab.setContent(createOverridePanel());

        // Tab 2: 排除文件
        Tab excludedTab = new Tab("排除文件列表");
        excludedTab.setContent(createExcludedPanel());

        tabPane.getTabs().addAll(overrideTab, excludedTab);

        // 底部按钮
        HBox buttonBox = createButtonBar();

        root.getChildren().addAll(titleLabel, descLabel, statsLabel, tabPane, buttonBox);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        Scene scene = new Scene(root);
        setScene(scene);

        // 加载数据
        loadData();
    }

    /**
     * 创建手动覆盖面板
     */
    private VBox createOverridePanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        // 说明文本
        Label infoLabel = new Label("💡 手动覆盖的优先级最高，会覆盖自动检测结果");
        infoLabel.setStyle("-fx-text-fill: #0969da; -fx-background-color: #ddf4ff; -fx-padding: 8px; -fx-background-radius: 4px;");

        // 创建表格
        overrideTable = new TableView<>();
        overrideData = FXCollections.observableArrayList();
        overrideTable.setItems(overrideData);

        // 文件名列
        TableColumn<OverrideEntry, String> fileCol = new TableColumn<>("文件名");
        fileCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        fileCol.setPrefWidth(400);

        // 机制分类列
        TableColumn<OverrideEntry, String> categoryCol = new TableColumn<>("机制分类");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        categoryCol.setPrefWidth(200);

        // 操作列
        TableColumn<OverrideEntry, Void> actionCol = new TableColumn<>("操作");
        actionCol.setPrefWidth(150);
        actionCol.setCellFactory(param -> new TableCell<OverrideEntry, Void>() {
            private final Button editBtn = new Button("修改");
            private final Button deleteBtn = new Button("删除");

            {
                editBtn.setOnAction(event -> {
                    OverrideEntry entry = getTableView().getItems().get(getIndex());
                    editOverride(entry);
                });

                deleteBtn.setOnAction(event -> {
                    OverrideEntry entry = getTableView().getItems().get(getIndex());
                    deleteOverride(entry);
                });

                editBtn.setStyle("-fx-font-size: 11px;");
                deleteBtn.setStyle("-fx-font-size: 11px;");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(5, editBtn, deleteBtn);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                }
            }
        });

        overrideTable.getColumns().addAll(fileCol, categoryCol, actionCol);

        // 工具栏
        HBox toolbar = new HBox(10);
        Button addBtn = new Button("➕ 添加覆盖");
        addBtn.setOnAction(e -> addOverride());

        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setOnAction(e -> loadData());

        toolbar.getChildren().addAll(addBtn, refreshBtn);

        panel.getChildren().addAll(infoLabel, toolbar, overrideTable);
        VBox.setVgrow(overrideTable, Priority.ALWAYS);

        return panel;
    }

    /**
     * 创建排除文件面板
     */
    private VBox createExcludedPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        // 说明文本
        Label infoLabel = new Label("💡 排除的文件会被分类为 OTHER，不属于任何游戏机制");
        infoLabel.setStyle("-fx-text-fill: #9a6700; -fx-background-color: #fff8c5; -fx-padding: 8px; -fx-background-radius: 4px;");

        // 创建表格
        excludedTable = new TableView<>();
        excludedData = FXCollections.observableArrayList();
        excludedTable.setItems(excludedData);

        // 文件名列
        TableColumn<ExcludedEntry, String> fileCol = new TableColumn<>("文件名");
        fileCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        fileCol.setPrefWidth(600);

        // 操作列
        TableColumn<ExcludedEntry, Void> actionCol = new TableColumn<>("操作");
        actionCol.setPrefWidth(150);
        actionCol.setCellFactory(param -> new TableCell<ExcludedEntry, Void>() {
            private final Button deleteBtn = new Button("删除");

            {
                deleteBtn.setOnAction(event -> {
                    ExcludedEntry entry = getTableView().getItems().get(getIndex());
                    deleteExcluded(entry);
                });

                deleteBtn.setStyle("-fx-font-size: 11px;");
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteBtn);
                }
            }
        });

        excludedTable.getColumns().addAll(fileCol, actionCol);

        // 工具栏
        HBox toolbar = new HBox(10);
        Button addBtn = new Button("➕ 添加排除");
        addBtn.setOnAction(e -> addExcluded());

        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setOnAction(e -> loadData());

        toolbar.getChildren().addAll(addBtn, refreshBtn);

        panel.getChildren().addAll(infoLabel, toolbar, excludedTable);
        VBox.setVgrow(excludedTable, Priority.ALWAYS);

        return panel;
    }

    /**
     * 创建底部按钮栏
     */
    private HBox createButtonBar() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_RIGHT);

        Button saveBtn = new Button("💾 保存配置");
        saveBtn.setStyle("-fx-background-color: #2ea44f; -fx-text-fill: white; -fx-font-weight: bold;");
        saveBtn.setOnAction(e -> saveConfig());

        Button closeBtn = new Button("关闭");
        closeBtn.setOnAction(e -> close());

        box.getChildren().addAll(saveBtn, closeBtn);
        return box;
    }

    /**
     * 加载数据
     */
    private void loadData() {
        // 加载手动覆盖
        overrideData.clear();
        Map<String, AionMechanismCategory> overrides = config.getAllOverrides();
        for (Map.Entry<String, AionMechanismCategory> entry : overrides.entrySet()) {
            overrideData.add(new OverrideEntry(entry.getKey(), entry.getValue()));
        }

        // 加载排除文件
        excludedData.clear();
        for (String fileName : config.getExcludedFiles()) {
            excludedData.add(new ExcludedEntry(fileName));
        }

        updateStats();
    }

    /**
     * 更新统计信息
     */
    private void updateStats() {
        int overrideCount = config.getOverrideCount();
        int excludedCount = config.getExcludedCount();
        statsLabel.setText(String.format("📊 当前配置: %d 个手动覆盖, %d 个排除文件", overrideCount, excludedCount));
        statsLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #24292f;");
    }

    /**
     * 添加覆盖
     */
    private void addOverride() {
        Dialog<OverrideEntry> dialog = new Dialog<>();
        dialog.setTitle("添加手动覆盖");
        dialog.setHeaderText("指定文件的机制分类");

        // 创建表单
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField fileNameField = new TextField();
        fileNameField.setPromptText("例如: custom_skill.xml");

        ComboBox<AionMechanismCategory> categoryBox = new ComboBox<>();
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            if (category != AionMechanismCategory.OTHER) {
                categoryBox.getItems().add(category);
            }
        }
        categoryBox.setPromptText("选择机制分类");

        grid.add(new Label("文件名:"), 0, 0);
        grid.add(fileNameField, 1, 0);
        grid.add(new Label("机制分类:"), 0, 1);
        grid.add(categoryBox, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // 设置结果转换器
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                String fileName = fileNameField.getText().trim();
                AionMechanismCategory category = categoryBox.getValue();
                if (!fileName.isEmpty() && category != null) {
                    return new OverrideEntry(fileName, category);
                }
            }
            return null;
        });

        Platform.runLater(fileNameField::requestFocus);

        Optional<OverrideEntry> result = dialog.showAndWait();
        result.ifPresent(entry -> {
            config.addOverride(entry.getFileName(), entry.getCategory());
            loadData();
        });
    }

    /**
     * 编辑覆盖
     */
    private void editOverride(OverrideEntry entry) {
        Dialog<AionMechanismCategory> dialog = new Dialog<>();
        dialog.setTitle("修改机制分类");
        dialog.setHeaderText("文件: " + entry.getFileName());

        ComboBox<AionMechanismCategory> categoryBox = new ComboBox<>();
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            if (category != AionMechanismCategory.OTHER) {
                categoryBox.getItems().add(category);
            }
        }
        categoryBox.setValue(entry.getCategory());

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.getChildren().addAll(new Label("选择新的机制分类:"), categoryBox);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return categoryBox.getValue();
            }
            return null;
        });

        Optional<AionMechanismCategory> result = dialog.showAndWait();
        result.ifPresent(category -> {
            config.addOverride(entry.getFileName(), category);
            loadData();
        });
    }

    /**
     * 删除覆盖
     */
    private void deleteOverride(OverrideEntry entry) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText("确定要删除此覆盖配置吗？");
        alert.setContentText("文件: " + entry.getFileName() + "\n机制: " + entry.getCategoryName());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            config.removeOverride(entry.getFileName());
            loadData();
        }
    }

    /**
     * 添加排除
     */
    private void addExcluded() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("添加排除文件");
        dialog.setHeaderText("指定要排除的文件");
        dialog.setContentText("文件名:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(fileName -> {
            if (!fileName.trim().isEmpty()) {
                config.addExcluded(fileName.trim());
                loadData();
            }
        });
    }

    /**
     * 删除排除
     */
    private void deleteExcluded(ExcludedEntry entry) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText("确定要从排除列表中删除此文件吗？");
        alert.setContentText("文件: " + entry.getFileName());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            config.removeExcluded(entry.getFileName());
            loadData();
        }
    }

    /**
     * 保存配置
     */
    private void saveConfig() {
        try {
            config.save();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("保存成功");
            alert.setHeaderText("配置已保存");
            alert.setContentText("配置已成功保存到 mechanism_manual_overrides.yml\n\n⚠️ 请重启应用以使更改生效");
            alert.showAndWait();

        } catch (Exception e) {
            log.error("保存配置失败", e);

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("保存失败");
            alert.setHeaderText("保存配置时发生错误");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * 手动覆盖条目（用于表格显示）
     */
    public static class OverrideEntry {
        private final SimpleStringProperty fileName;
        private final SimpleStringProperty categoryName;
        private final AionMechanismCategory category;

        public OverrideEntry(String fileName, AionMechanismCategory category) {
            this.fileName = new SimpleStringProperty(fileName);
            this.category = category;
            this.categoryName = new SimpleStringProperty(category.getDisplayName());
        }

        public String getFileName() {
            return fileName.get();
        }

        public String getCategoryName() {
            return categoryName.get();
        }

        public AionMechanismCategory getCategory() {
            return category;
        }
    }

    /**
     * 排除文件条目（用于表格显示）
     */
    public static class ExcludedEntry {
        private final SimpleStringProperty fileName;

        public ExcludedEntry(String fileName) {
            this.fileName = new SimpleStringProperty(fileName);
        }

        public String getFileName() {
            return fileName.get();
        }
    }
}
