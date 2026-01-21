package red.jiuzhou.ops.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.model.GameCharacter;
import red.jiuzhou.ops.service.CharacterService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 角色管理面板
 *
 * 提供角色查询、修改、删除等可视化操作界面
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class CharacterPanel extends BorderPane {

    private static final Logger log = LoggerFactory.getLogger(CharacterPanel.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CharacterService characterService;

    // UI Components
    private final TextField searchField = new TextField();
    private final ComboBox<String> searchType = new ComboBox<>();
    private final TableView<GameCharacter> characterTable = new TableView<>();
    private final TextArea detailArea = new TextArea();
    private final Label statusLabel = new Label("就绪");

    // Current selection
    private GameCharacter selectedCharacter;

    public CharacterPanel(CharacterService characterService) {
        this.characterService = characterService;
        buildUI();
    }

    private void buildUI() {
        // Top - Search Bar
        setTop(buildSearchBar());

        // Center - Split Pane (Table + Details)
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        splitPane.getItems().addAll(buildTablePane(), buildDetailPane());
        splitPane.setDividerPositions(0.6);
        setCenter(splitPane);

        // Bottom - Action Bar
        setBottom(buildActionBar());
    }

    private HBox buildSearchBar() {
        HBox bar = new HBox(10);
        bar.setPadding(new Insets(10));
        bar.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("🔍 搜索角色:");

        searchType.setItems(FXCollections.observableArrayList(
                "角色ID", "角色名称", "账号ID"
        ));
        searchType.setValue("角色名称");

        searchField.setPromptText("输入搜索关键词...");
        searchField.setPrefWidth(200);
        searchField.setOnAction(e -> doSearch());

        Button searchBtn = new Button("搜索");
        searchBtn.setOnAction(e -> doSearch());

        Button clearBtn = new Button("清空");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            characterTable.getItems().clear();
            detailArea.clear();
            selectedCharacter = null;
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(
                searchLabel, searchType, searchField, searchBtn, clearBtn,
                spacer, statusLabel
        );

        return bar;
    }

    private VBox buildTablePane() {
        VBox pane = new VBox(8);
        pane.setPadding(new Insets(8));

        Label title = new Label("📋 角色列表");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // Configure table columns
        TableColumn<GameCharacter, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().charId())
        ));
        idCol.setPrefWidth(60);

        TableColumn<GameCharacter, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().name()
        ));
        nameCol.setPrefWidth(120);

        TableColumn<GameCharacter, String> levelCol = new TableColumn<>("等级");
        levelCol.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().level())
        ));
        levelCol.setPrefWidth(50);

        TableColumn<GameCharacter, String> raceCol = new TableColumn<>("种族");
        raceCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getRaceDisplay()
        ));
        raceCol.setPrefWidth(60);

        TableColumn<GameCharacter, String> classCol = new TableColumn<>("职业");
        classCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getClassDisplay()
        ));
        classCol.setPrefWidth(80);

        TableColumn<GameCharacter, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getStatusDisplay()
        ));
        statusCol.setPrefWidth(60);

        characterTable.getColumns().addAll(idCol, nameCol, levelCol, raceCol, classCol, statusCol);
        characterTable.setPlaceholder(new Label("请输入搜索条件查询角色"));

        characterTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectCharacter(newVal);
            }
        });

        VBox.setVgrow(characterTable, Priority.ALWAYS);
        pane.getChildren().addAll(title, characterTable);

        return pane;
    }

    private VBox buildDetailPane() {
        VBox pane = new VBox(8);
        pane.setPadding(new Insets(8));

        Label title = new Label("📄 角色详情");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        detailArea.setEditable(false);
        detailArea.setWrapText(true);
        detailArea.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 12px;");

        VBox.setVgrow(detailArea, Priority.ALWAYS);
        pane.getChildren().addAll(title, detailArea);

        return pane;
    }

    private HBox buildActionBar() {
        HBox bar = new HBox(10);
        bar.setPadding(new Insets(10));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: #ecf0f1;");

        Label actionLabel = new Label("⚡ 操作:");

        Button renameBtn = createActionButton("✏️ 改名", this::doRename);
        Button changeRaceBtn = createActionButton("🔄 改种族", this::doChangeRace);
        Button setLevelBtn = createActionButton("📈 改等级", this::doSetLevel);
        Button addKinahBtn = createActionButton("💰 加金币", this::doAddKinah);
        Button teleportBtn = createActionButton("📍 传送", this::doTeleport);
        Button deleteBtn = createActionButton("🗑️ 删除", this::doDelete);
        deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");

        bar.getChildren().addAll(
                actionLabel, renameBtn, changeRaceBtn, setLevelBtn,
                addKinahBtn, teleportBtn, new Separator(javafx.geometry.Orientation.VERTICAL),
                deleteBtn
        );

        return bar;
    }

    private Button createActionButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setOnAction(e -> {
            if (selectedCharacter == null) {
                showAlert(Alert.AlertType.WARNING, "提示", "请先选择一个角色");
                return;
            }
            action.run();
        });
        return btn;
    }

    // ==================== Actions ====================

    private void doSearch() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "提示", "请输入搜索关键词");
            return;
        }

        String type = searchType.getValue();
        updateStatus("正在搜索...");

        Task<List<GameCharacter>> task = new Task<>() {
            @Override
            protected List<GameCharacter> call() throws Exception {
                return switch (type) {
                    case "角色ID" -> {
                        int id = Integer.parseInt(keyword);
                        yield characterService.findById(id)
                                .map(List::of)
                                .orElse(List.of());
                    }
                    case "角色名称" -> characterService.findByName(keyword)
                            .map(List::of)
                            .orElse(characterService.search(keyword, 100));
                    case "账号ID" -> {
                        int accountId = Integer.parseInt(keyword);
                        yield characterService.findByAccountId(accountId);
                    }
                    default -> List.of();
                };
            }
        };

        task.setOnSucceeded(e -> {
            List<GameCharacter> results = task.getValue();
            Platform.runLater(() -> {
                characterTable.setItems(FXCollections.observableArrayList(results));
                updateStatus("找到 " + results.size() + " 个角色");
            });
        });

        task.setOnFailed(e -> {
            log.error("搜索失败", task.getException());
            updateStatus("搜索失败: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void selectCharacter(GameCharacter character) {
        this.selectedCharacter = character;
        showCharacterDetails(character);
    }

    private void showCharacterDetails(GameCharacter c) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════\n");
        sb.append("         角色信息\n");
        sb.append("═══════════════════════════════\n\n");

        sb.append("【基本信息】\n");
        sb.append("  角色ID: ").append(c.charId()).append("\n");
        sb.append("  账号ID: ").append(c.accountId()).append("\n");
        sb.append("  名  称: ").append(c.name()).append("\n");
        sb.append("  种  族: ").append(c.getRaceDisplay()).append("\n");
        sb.append("  职  业: ").append(c.getClassDisplay()).append("\n");
        sb.append("  等  级: ").append(c.level()).append("\n");
        sb.append("  状  态: ").append(c.getStatusDisplay()).append("\n");

        sb.append("\n【属性数据】\n");
        sb.append("  经验值: ").append(String.format("%,d", c.exp())).append("\n");
        sb.append("  金  币: ").append(String.format("%,d", c.kinah())).append("\n");
        sb.append("  HP/MP/DP: ").append(c.hp()).append("/").append(c.mp()).append("/").append(c.dp()).append("\n");
        sb.append("  称号ID: ").append(c.titleId()).append("\n");

        sb.append("\n【公会信息】\n");
        if (c.hasGuild()) {
            sb.append("  公会ID: ").append(c.guildId()).append("\n");
            sb.append("  公会名: ").append(c.guildName()).append("\n");
        } else {
            sb.append("  未加入公会\n");
        }

        sb.append("\n【位置信息】\n");
        sb.append("  ").append(c.getPositionString()).append("\n");
        sb.append("  朝  向: ").append(c.heading()).append("\n");

        sb.append("\n【时间记录】\n");
        if (c.createTime() != null) {
            sb.append("  创建时间: ").append(c.createTime().format(DATE_FORMAT)).append("\n");
        }
        if (c.lastOnline() != null) {
            sb.append("  最后在线: ").append(c.lastOnline().format(DATE_FORMAT)).append("\n");
        }

        detailArea.setText(sb.toString());
    }

    private void doRename() {
        TextInputDialog dialog = new TextInputDialog(selectedCharacter.name());
        dialog.setTitle("修改角色名称");
        dialog.setHeaderText("角色: " + selectedCharacter.name());
        dialog.setContentText("新名称:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (newName.equals(selectedCharacter.name())) {
                return;
            }
            if (characterService.changeName(selectedCharacter.charId(), newName)) {
                showAlert(Alert.AlertType.INFORMATION, "成功", "角色名称已修改为: " + newName);
                doSearch(); // Refresh
            } else {
                showAlert(Alert.AlertType.ERROR, "失败", "修改名称失败，可能名称已被使用");
            }
        });
    }

    private void doChangeRace() {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(
                selectedCharacter.race(),
                CharacterService.getAvailableRaces()
        );
        dialog.setTitle("修改角色种族");
        dialog.setHeaderText("角色: " + selectedCharacter.name());
        dialog.setContentText("选择种族:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newRace -> {
            if (characterService.changeRace(selectedCharacter.charId(), newRace)) {
                showAlert(Alert.AlertType.INFORMATION, "成功", "角色种族已修改");
                doSearch();
            } else {
                showAlert(Alert.AlertType.ERROR, "失败", "修改种族失败");
            }
        });
    }

    private void doSetLevel() {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(selectedCharacter.level()));
        dialog.setTitle("设置角色等级");
        dialog.setHeaderText("角色: " + selectedCharacter.name());
        dialog.setContentText("新等级 (1-85):");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(levelStr -> {
            try {
                int newLevel = Integer.parseInt(levelStr);
                if (characterService.setLevel(selectedCharacter.charId(), newLevel)) {
                    showAlert(Alert.AlertType.INFORMATION, "成功", "角色等级已设置为: " + newLevel);
                    doSearch();
                } else {
                    showAlert(Alert.AlertType.ERROR, "失败", "设置等级失败");
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "错误", "请输入有效的数字");
            }
        });
    }

    private void doAddKinah() {
        TextInputDialog dialog = new TextInputDialog("1000000");
        dialog.setTitle("增加金币");
        dialog.setHeaderText("角色: " + selectedCharacter.name() + "\n当前金币: " +
                String.format("%,d", selectedCharacter.kinah()));
        dialog.setContentText("增加数量:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(amountStr -> {
            try {
                long amount = Long.parseLong(amountStr);
                if (characterService.addKinah(selectedCharacter.charId(), amount)) {
                    showAlert(Alert.AlertType.INFORMATION, "成功",
                            "已增加 " + String.format("%,d", amount) + " 金币");
                    doSearch();
                } else {
                    showAlert(Alert.AlertType.ERROR, "失败", "增加金币失败");
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "错误", "请输入有效的数字");
            }
        });
    }

    private void doTeleport() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("传送角色");
        dialog.setHeaderText("角色: " + selectedCharacter.name() + "\n当前位置: " +
                selectedCharacter.getPositionString());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField worldField = new TextField(String.valueOf(selectedCharacter.worldId()));
        TextField xField = new TextField(String.valueOf(selectedCharacter.x()));
        TextField yField = new TextField(String.valueOf(selectedCharacter.y()));
        TextField zField = new TextField(String.valueOf(selectedCharacter.z()));

        grid.add(new Label("世界ID:"), 0, 0);
        grid.add(worldField, 1, 0);
        grid.add(new Label("X:"), 0, 1);
        grid.add(xField, 1, 1);
        grid.add(new Label("Y:"), 0, 2);
        grid.add(yField, 1, 2);
        grid.add(new Label("Z:"), 0, 3);
        grid.add(zField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                return new String[]{
                        worldField.getText(),
                        xField.getText(),
                        yField.getText(),
                        zField.getText()
                };
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(coords -> {
            try {
                int worldId = Integer.parseInt(coords[0]);
                float x = Float.parseFloat(coords[1]);
                float y = Float.parseFloat(coords[2]);
                float z = Float.parseFloat(coords[3]);

                if (characterService.teleport(selectedCharacter.charId(), worldId, x, y, z)) {
                    showAlert(Alert.AlertType.INFORMATION, "成功", "角色已传送到新位置");
                    doSearch();
                } else {
                    showAlert(Alert.AlertType.ERROR, "失败", "传送失败");
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "错误", "请输入有效的数字");
            }
        });
    }

    private void doDelete() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText("危险操作警告");
        confirm.setContentText("确定要删除角色 \"" + selectedCharacter.name() + "\" 吗？\n\n" +
                "此操作为软删除，可以恢复。");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (characterService.delete(selectedCharacter.charId())) {
                showAlert(Alert.AlertType.INFORMATION, "成功", "角色已删除");
                doSearch();
            } else {
                showAlert(Alert.AlertType.ERROR, "失败", "删除角色失败");
            }
        }
    }

    // ==================== Helpers ====================

    private void updateStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}
