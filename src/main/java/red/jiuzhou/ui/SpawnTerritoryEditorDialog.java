package red.jiuzhou.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import red.jiuzhou.util.game.SpawnTerritory;
import red.jiuzhou.util.game.WorldSpawnEditor;

/**
 * 刷怪区域编辑器对话框
 *
 * 支持新增和修改刷怪区域配置
 *
 * @author yanxq
 * @date 2025-01-19
 */
public class SpawnTerritoryEditorDialog extends Stage {

    private final String mapName;
    private final WorldSpawnEditor editor;
    private SpawnTerritory territory;

    // UI组件
    private TextField nameField;
    private CheckBox noRespawnCheck;
    private CheckBox aerialSpawnCheck;
    private CheckBox generatePathfindCheck;
    private Spinner<Integer> spawnVersionSpinner;

    // NPC列表
    private ListView<SpawnTerritory.SpawnNpc> npcListView;

    private boolean saved = false;

    /**
     * 新增模式
     */
    public SpawnTerritoryEditorDialog(String mapName, WorldSpawnEditor editor) {
        this(mapName, editor, null);
    }

    /**
     * 编辑模式
     */
    public SpawnTerritoryEditorDialog(String mapName, WorldSpawnEditor editor, SpawnTerritory territory) {
        this.mapName = mapName;
        this.editor = editor;
        this.territory = territory != null ? territory : new SpawnTerritory();

        initUI();
        loadData();
    }

    private void initUI() {
        setTitle(territory.getName() == null ? "新增刷怪区域" : "编辑刷怪区域: " + territory.getName());
        initModality(Modality.APPLICATION_MODAL);
        setWidth(600);
        setHeight(650);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        // 基本信息区
        root.getChildren().add(createBasicInfoPane());

        // NPC配置区
        root.getChildren().add(createNpcConfigPane());

        // 按钮区
        root.getChildren().add(createButtonPane());

        Scene scene = new Scene(root);
        setScene(scene);
    }

    /**
     * 基本信息面板
     */
    private TitledPane createBasicInfoPane() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        int row = 0;

        // 区域名称
        grid.add(new Label("区域名称:"), 0, row);
        nameField = new TextField();
        nameField.setPromptText("必填，唯一标识");
        grid.add(nameField, 1, row);
        row++;

        // 刷怪版本
        grid.add(new Label("刷怪版本:"), 0, row);
        spawnVersionSpinner = new Spinner<>(0, 100, 0);
        spawnVersionSpinner.setEditable(true);
        grid.add(spawnVersionSpinner, 1, row);
        row++;

        // 复选框
        noRespawnCheck = new CheckBox("不重生");
        aerialSpawnCheck = new CheckBox("空中刷怪");
        generatePathfindCheck = new CheckBox("生成寻路");

        HBox checkBoxes = new HBox(15, noRespawnCheck, aerialSpawnCheck, generatePathfindCheck);
        grid.add(checkBoxes, 1, row);
        row++;

        TitledPane pane = new TitledPane("基本信息", grid);
        pane.setCollapsible(false);
        return pane;
    }

    /**
     * NPC配置面板
     */
    private TitledPane createNpcConfigPane() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        // NPC列表
        npcListView = new ListView<>();
        npcListView.setPrefHeight(200);
        npcListView.setCellFactory(lv -> new ListCell<SpawnTerritory.SpawnNpc>() {
            @Override
            protected void updateItem(SpawnTerritory.SpawnNpc item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                }
            }
        });

        // 按钮组
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        Button addNpcBtn = new Button("添加NPC");
        Button editNpcBtn = new Button("编辑");
        Button removeNpcBtn = new Button("删除");

        addNpcBtn.setOnAction(e -> showNpcEditor(null));
        editNpcBtn.setOnAction(e -> {
            SpawnTerritory.SpawnNpc selected = npcListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showNpcEditor(selected);
            }
        });
        removeNpcBtn.setOnAction(e -> {
            SpawnTerritory.SpawnNpc selected = npcListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                npcListView.getItems().remove(selected);
            }
        });

        buttonBox.getChildren().addAll(addNpcBtn, editNpcBtn, removeNpcBtn);

        vbox.getChildren().addAll(npcListView, buttonBox);

        TitledPane pane = new TitledPane("NPC配置", vbox);
        pane.setCollapsible(false);
        VBox.setVgrow(pane, Priority.ALWAYS);
        return pane;
    }

    /**
     * 按钮面板
     */
    private HBox createButtonPane() {
        HBox hbox = new HBox(15);
        hbox.setAlignment(Pos.CENTER_RIGHT);
        hbox.setPadding(new Insets(10, 0, 0, 0));

        Button saveBtn = new Button("保存");
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> save());

        Button cancelBtn = new Button("取消");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> close());

        hbox.getChildren().addAll(saveBtn, cancelBtn);
        return hbox;
    }

    /**
     * 加载数据到表单
     */
    private void loadData() {
        if (territory != null) {
            nameField.setText(territory.getName());
            noRespawnCheck.setSelected(territory.isNoRespawn());
            aerialSpawnCheck.setSelected(territory.isAerialSpawn());
            generatePathfindCheck.setSelected(territory.isGeneratePathfind());
            spawnVersionSpinner.getValueFactory().setValue(territory.getSpawnVersion());

            npcListView.getItems().addAll(territory.getNpcs());
        }
    }

    /**
     * 保存数据
     */
    private void save() {
        // 验证
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError("区域名称不能为空");
            return;
        }

        if (npcListView.getItems().isEmpty()) {
            boolean confirm = showConfirm("警告", "没有配置NPC，确认保存？");
            if (!confirm) return;
        }

        try {
            // 更新Territory对象
            if (territory == null) {
                territory = new SpawnTerritory();
            }

            territory.setName(name);
            territory.setNoRespawn(noRespawnCheck.isSelected());
            territory.setAerialSpawn(aerialSpawnCheck.isSelected());
            territory.setGeneratePathfind(generatePathfindCheck.isSelected());
            territory.setSpawnVersion(spawnVersionSpinner.getValue());

            territory.getNpcs().clear();
            territory.getNpcs().addAll(npcListView.getItems());

            // 调用编辑器保存（幂等性操作）
            WorldSpawnEditor.OperationResult result = editor.upsertTerritory(mapName, territory);

            // 显示结果
            showSuccess(result);

            saved = true;
            close();

        } catch (Exception e) {
            showError("保存失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 显示NPC编辑器
     */
    private void showNpcEditor(SpawnTerritory.SpawnNpc npc) {
        Dialog<SpawnTerritory.SpawnNpc> dialog = new Dialog<>();
        dialog.setTitle(npc == null ? "添加NPC" : "编辑NPC");
        dialog.setHeaderText(null);

        // 创建表单
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        nameField.setPromptText("NPC名称或ID");
        Spinner<Integer> countSpinner = new Spinner<>(1, 1000, 1);
        Spinner<Integer> spawnTimeSpinner = new Spinner<>(1, 36000, 120);
        Spinner<Integer> spawnTimeExSpinner = new Spinner<>(0, 3600, 0);

        if (npc != null) {
            nameField.setText(npc.getName());
            countSpinner.getValueFactory().setValue(npc.getCount());
            spawnTimeSpinner.getValueFactory().setValue(npc.getSpawnTime());
            spawnTimeExSpinner.getValueFactory().setValue(npc.getSpawnTimeEx());
        }

        grid.add(new Label("NPC名称:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("数量:"), 0, 1);
        grid.add(countSpinner, 1, 1);
        grid.add(new Label("刷新时间(秒):"), 0, 2);
        grid.add(spawnTimeSpinner, 1, 2);
        grid.add(new Label("时间偏差(秒):"), 0, 3);
        grid.add(spawnTimeExSpinner, 1, 3);

        dialog.getDialogPane().setContent(grid);

        // 按钮
        ButtonType saveButtonType = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // 结果转换
        dialog.setResultConverter(buttonType -> {
            if (buttonType == saveButtonType) {
                SpawnTerritory.SpawnNpc result = npc != null ? npc : new SpawnTerritory.SpawnNpc();
                result.setName(nameField.getText());
                result.setCount(countSpinner.getValue());
                result.setSpawnTime(spawnTimeSpinner.getValue());
                result.setSpawnTimeEx(spawnTimeExSpinner.getValue());
                return result;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            if (npc == null) {
                npcListView.getItems().add(result);
            } else {
                npcListView.refresh();
            }
        });
    }

    /**
     * 显示错误对话框
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示成功对话框
     */
    private void showSuccess(WorldSpawnEditor.OperationResult result) {
        String title = "";
        String content = "";

        switch (result.getStatus()) {
            case CREATED:
                title = "创建成功";
                content = "新刷怪区域已创建: " + territory.getName();
                break;
            case UPDATED:
                title = "更新成功";
                content = "刷怪区域已更新: " + territory.getName();
                break;
            case NO_CHANGE:
                title = "无变化";
                content = "配置未变化，无需保存: " + territory.getName();
                break;
            default:
                title = result.getStatus().toString();
                content = result.getMessage();
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content + "\n\n备份已自动创建");
        alert.showAndWait();
    }

    /**
     * 显示确认对话框
     */
    private boolean showConfirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    public boolean isSaved() {
        return saved;
    }

    public SpawnTerritory getTerritory() {
        return territory;
    }
}
