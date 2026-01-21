package red.jiuzhou.ops.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.core.AuditLog;
import red.jiuzhou.ops.core.AuditLog.OperationType;
import red.jiuzhou.ops.core.EventBus;
import red.jiuzhou.ops.core.OperationChain;
import red.jiuzhou.ops.model.GameItem;
import red.jiuzhou.ops.service.GameOpsService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 物品管理面板
 *
 * 提供物品数据的查询和管理功能：
 * - 物品搜索（按ID、名称、所有者）
 * - 物品详情查看
 * - 发送物品给玩家
 * - 删除物品
 * - 物品转移
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class ItemPanel extends VBox {

    private static final Logger log = LoggerFactory.getLogger(ItemPanel.class);

    private final GameOpsService opsService;
    private final AuditLog auditLog = AuditLog.getInstance();
    private final EventBus eventBus = EventBus.getInstance();

    // Search
    private final TextField searchField = new TextField();
    private final ComboBox<String> searchType = new ComboBox<>();
    private final ComboBox<String> storageFilter = new ComboBox<>();

    // Table
    private final TableView<GameItem> itemTable = new TableView<>();
    private final ObservableList<GameItem> itemData = FXCollections.observableArrayList();

    // Detail
    private final GridPane detailGrid = new GridPane();

    // Send Item Form
    private final TextField sendCharIdField = new TextField();
    private final TextField sendItemIdField = new TextField();
    private final Spinner<Integer> sendCountSpinner = new Spinner<>(1, 9999, 1);

    // Status
    private final Label statusLabel = new Label("就绪");
    private final ProgressIndicator progress = new ProgressIndicator();

    private GameItem selectedItem;

    public ItemPanel(GameOpsService opsService) {
        this.opsService = opsService;
        buildUI();
    }

    private void buildUI() {
        setSpacing(12);
        setPadding(new Insets(12));

        // Header
        Label header = new Label("💰 物品管理");
        header.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Search bar
        HBox searchBar = buildSearchBar();

        // Main content - Tab pane
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Tab 1 - Item Search
        Tab searchTab = new Tab("🔍 物品查询", buildSearchPane());

        // Tab 2 - Send Item
        Tab sendTab = new Tab("📦 发送物品", buildSendItemPane());

        // Tab 3 - Item Statistics
        Tab statsTab = new Tab("📊 物品统计", buildStatsPane());

        tabPane.getTabs().addAll(searchTab, sendTab, statsTab);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Status bar
        HBox statusBar = buildStatusBar();

        getChildren().addAll(header, searchBar, tabPane, statusBar);
    }

    private HBox buildSearchBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8));
        bar.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 4;");

        // Search type
        searchType.getItems().addAll("角色ID", "角色名", "物品ID", "物品唯一ID");
        searchType.setValue("角色ID");

        // Search field
        searchField.setPromptText("输入搜索关键词...");
        searchField.setPrefWidth(200);
        searchField.setOnAction(e -> searchItems());

        // Storage filter
        storageFilter.getItems().addAll("全部", "背包", "仓库", "账号仓库", "公会仓库");
        storageFilter.setValue("全部");

        // Search button
        Button searchBtn = new Button("🔍 搜索");
        searchBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        searchBtn.setOnAction(e -> searchItems());

        bar.getChildren().addAll(
                new Label("搜索类型:"), searchType,
                searchField,
                new Label("存储:"), storageFilter,
                searchBtn
        );

        return bar;
    }

    @SuppressWarnings("unchecked")
    private VBox buildSearchPane() {
        VBox pane = new VBox(12);
        pane.setPadding(new Insets(12));

        // Split pane - table and detail
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.65);

        // Left - Item table
        VBox tableBox = new VBox(8);

        // Table columns
        TableColumn<GameItem, String> uniqueIdCol = new TableColumn<>("唯一ID");
        uniqueIdCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().itemUniqueId())));
        uniqueIdCol.setPrefWidth(80);

        TableColumn<GameItem, String> itemIdCol = new TableColumn<>("物品ID");
        itemIdCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().itemId())));
        itemIdCol.setPrefWidth(70);

        TableColumn<GameItem, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getDisplayName()));
        nameCol.setPrefWidth(180);

        TableColumn<GameItem, String> countCol = new TableColumn<>("数量");
        countCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().count())));
        countCol.setPrefWidth(50);

        TableColumn<GameItem, String> ownerCol = new TableColumn<>("所有者");
        ownerCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().ownerName()));
        ownerCol.setPrefWidth(100);

        TableColumn<GameItem, String> storageCol = new TableColumn<>("存储位置");
        storageCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getStorageTypeDisplay()));
        storageCol.setPrefWidth(80);

        TableColumn<GameItem, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getStatusDisplay()));
        statusCol.setPrefWidth(100);

        itemTable.getColumns().addAll(uniqueIdCol, itemIdCol, nameCol, countCol, ownerCol, storageCol, statusCol);
        itemTable.setItems(itemData);
        VBox.setVgrow(itemTable, Priority.ALWAYS);

        // Selection listener
        itemTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectItem(newVal);
            }
        });

        // Action buttons
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button deleteBtn = new Button("🗑️ 删除");
        deleteBtn.setStyle("-fx-text-fill: #e74c3c;");
        deleteBtn.setOnAction(e -> deleteItem());

        Button transferBtn = new Button("📤 转移");
        transferBtn.setOnAction(e -> transferItem());

        Button duplicateBtn = new Button("📋 复制");
        duplicateBtn.setOnAction(e -> duplicateItem());

        actions.getChildren().addAll(deleteBtn, transferBtn, duplicateBtn);

        tableBox.getChildren().addAll(itemTable, actions);

        // Right - Item detail
        VBox detailBox = buildDetailPane();

        splitPane.getItems().addAll(tableBox, detailBox);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        pane.getChildren().add(splitPane);
        return pane;
    }

    private VBox buildDetailPane() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));

        Label title = new Label("物品详情");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Detail grid
        detailGrid.setHgap(12);
        detailGrid.setVgap(8);
        detailGrid.setPadding(new Insets(8));
        detailGrid.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 4;");

        // Placeholder
        detailGrid.add(new Label("选择物品查看详情"), 0, 0);

        box.getChildren().addAll(title, detailGrid);
        return box;
    }

    private VBox buildSendItemPane() {
        VBox pane = new VBox(16);
        pane.setPadding(new Insets(20));
        pane.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("📦 发送物品给玩家");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Form grid
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.setAlignment(Pos.CENTER);
        form.setMaxWidth(500);

        int row = 0;

        // Character ID
        Label charLabel = new Label("角色ID *:");
        sendCharIdField.setPromptText("输入角色ID");
        sendCharIdField.setPrefWidth(200);
        form.add(charLabel, 0, row);
        form.add(sendCharIdField, 1, row++);

        // Item ID
        Label itemLabel = new Label("物品ID *:");
        sendItemIdField.setPromptText("输入物品模板ID");
        sendItemIdField.setPrefWidth(200);
        form.add(itemLabel, 0, row);
        form.add(sendItemIdField, 1, row++);

        // Count
        Label countLabel = new Label("数量:");
        sendCountSpinner.setEditable(true);
        sendCountSpinner.setPrefWidth(200);
        form.add(countLabel, 0, row);
        form.add(sendCountSpinner, 1, row++);

        // Enchant level (optional)
        Label enchantLabel = new Label("强化等级:");
        Spinner<Integer> enchantSpinner = new Spinner<>(0, 15, 0);
        enchantSpinner.setEditable(true);
        enchantSpinner.setPrefWidth(200);
        form.add(enchantLabel, 0, row);
        form.add(enchantSpinner, 1, row++);

        // Soul bound checkbox
        CheckBox soulBoundCheck = new CheckBox("绑定");
        form.add(soulBoundCheck, 1, row++);

        // Send button
        Button sendBtn = new Button("📤 发送物品");
        sendBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        sendBtn.setPrefWidth(200);
        sendBtn.setOnAction(e -> sendItem(enchantSpinner.getValue(), soulBoundCheck.isSelected()));

        // Clear button
        Button clearBtn = new Button("🗑️ 清空");
        clearBtn.setOnAction(e -> {
            sendCharIdField.clear();
            sendItemIdField.clear();
            sendCountSpinner.getValueFactory().setValue(1);
            enchantSpinner.getValueFactory().setValue(0);
            soulBoundCheck.setSelected(false);
        });

        HBox buttons = new HBox(12, sendBtn, clearBtn);
        buttons.setAlignment(Pos.CENTER);

        // Recent sends table
        Label recentTitle = new Label("📋 最近发送记录");
        recentTitle.setStyle("-fx-font-weight: bold;");

        TableView<Map<String, Object>> recentTable = new TableView<>();
        recentTable.setPrefHeight(200);

        TableColumn<Map<String, Object>, String> timeCol = new TableColumn<>("时间");
        timeCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().get("time"))));
        timeCol.setPrefWidth(120);

        TableColumn<Map<String, Object>, String> charCol = new TableColumn<>("角色");
        charCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().get("char"))));
        charCol.setPrefWidth(100);

        TableColumn<Map<String, Object>, String> itemCol = new TableColumn<>("物品");
        itemCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().get("item"))));
        itemCol.setPrefWidth(150);

        TableColumn<Map<String, Object>, String> countCol2 = new TableColumn<>("数量");
        countCol2.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().get("count"))));
        countCol2.setPrefWidth(60);

        recentTable.getColumns().addAll(timeCol, charCol, itemCol, countCol2);

        pane.getChildren().addAll(title, form, buttons, recentTitle, recentTable);
        return pane;
    }

    private VBox buildStatsPane() {
        VBox pane = new VBox(16);
        pane.setPadding(new Insets(20));

        Label title = new Label("📊 物品统计");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Stats cards
        HBox cardsRow = new HBox(20);
        cardsRow.setAlignment(Pos.CENTER);

        cardsRow.getChildren().addAll(
                createStatCard("总物品数", "0", "#3498db"),
                createStatCard("已绑定物品", "0", "#9b59b6"),
                createStatCard("强化装备", "0", "#e67e22"),
                createStatCard("过期物品", "0", "#e74c3c")
        );

        // Item ranking
        Label rankTitle = new Label("📈 物品排行");
        rankTitle.setStyle("-fx-font-weight: bold;");

        TableView<Map<String, Object>> rankTable = new TableView<>();
        rankTable.setPrefHeight(300);

        TableColumn<Map<String, Object>, String> itemIdCol = new TableColumn<>("物品ID");
        itemIdCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().get("item_id"))));
        itemIdCol.setPrefWidth(80);

        TableColumn<Map<String, Object>, String> itemNameCol = new TableColumn<>("名称");
        itemNameCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().get("name"))));
        itemNameCol.setPrefWidth(200);

        TableColumn<Map<String, Object>, String> totalCol = new TableColumn<>("总数量");
        totalCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().get("total"))));
        totalCol.setPrefWidth(100);

        TableColumn<Map<String, Object>, String> ownersCol = new TableColumn<>("持有人数");
        ownersCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().get("owners"))));
        ownersCol.setPrefWidth(100);

        rankTable.getColumns().addAll(itemIdCol, itemNameCol, totalCol, ownersCol);

        // Refresh button
        Button refreshBtn = new Button("🔄 刷新统计");
        refreshBtn.setOnAction(e -> refreshStats());

        pane.getChildren().addAll(title, cardsRow, rankTitle, rankTable, refreshBtn);
        return pane;
    }

    private VBox createStatCard(String title, String value, String color) {
        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(16, 24, 16, 24));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 4, 0, 0, 2);");
        card.setPrefWidth(150);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        Label valueLabel = new Label(value);
        valueLabel.setStyle(String.format(
                "-fx-text-fill: %s; -fx-font-size: 24px; -fx-font-weight: bold;", color
        ));

        card.getChildren().addAll(titleLabel, valueLabel);
        return card;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.setStyle("-fx-background-color: #bdc3c7;");

        progress.setMaxSize(16, 16);
        progress.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label countLabel = new Label("物品数: 0");
        countLabel.setId("itemCountLabel");

        bar.getChildren().addAll(statusLabel, progress, spacer, countLabel);
        return bar;
    }

    // ==================== Data Operations ====================

    private void searchItems() {
        String keyword = searchField.getText().trim();
        String type = searchType.getValue();
        String storage = storageFilter.getValue();

        if (keyword.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "请输入搜索关键词");
            return;
        }

        setLoading(true, "搜索中...");

        Task<List<GameItem>> task = new Task<>() {
            @Override
            protected List<GameItem> call() throws Exception {
                if (opsService == null) {
                    return List.of();
                }

                // Determine storage type filter
                Integer storageType = switch (storage) {
                    case "背包" -> GameItem.STORAGE_INVENTORY;
                    case "仓库" -> GameItem.STORAGE_REGULAR_WAREHOUSE;
                    case "账号仓库" -> GameItem.STORAGE_ACCOUNT_WAREHOUSE;
                    case "公会仓库" -> GameItem.STORAGE_LEGION_WAREHOUSE;
                    default -> null;
                };

                return switch (type) {
                    case "角色ID" -> opsService.getItemsByOwner(Integer.parseInt(keyword), storageType);
                    case "角色名" -> opsService.getItemsByOwnerName(keyword, storageType);
                    case "物品ID" -> opsService.getItemsByItemId(Integer.parseInt(keyword));
                    case "物品唯一ID" -> {
                        GameItem item = opsService.getItemByUniqueId(Long.parseLong(keyword));
                        yield item != null ? List.of(item) : List.of();
                    }
                    default -> List.of();
                };
            }
        };

        task.setOnSucceeded(e -> {
            List<GameItem> results = task.getValue();
            Platform.runLater(() -> {
                itemData.setAll(results);
                setLoading(false, "找到 " + results.size() + " 个物品");
                updateItemCount(results.size());
            });

            auditLog.info(OperationType.ITEM, "搜索物品",
                    type + ":" + keyword, "结果:" + results.size());
        });

        task.setOnFailed(e -> {
            log.error("搜索物品失败", task.getException());
            setLoading(false, "搜索失败: " + task.getException().getMessage());
        });

        Thread.startVirtualThread(task);
    }

    private void selectItem(GameItem item) {
        this.selectedItem = item;

        // Update detail grid
        detailGrid.getChildren().clear();
        int row = 0;

        addDetailRow(row++, "唯一ID", String.valueOf(item.itemUniqueId()));
        addDetailRow(row++, "物品ID", String.valueOf(item.itemId()));
        addDetailRow(row++, "名称", item.getDisplayName());
        addDetailRow(row++, "数量", String.valueOf(item.count()));
        addDetailRow(row++, "所有者", item.ownerName() + " (ID:" + item.ownerId() + ")");
        addDetailRow(row++, "存储位置", item.getStorageTypeDisplay());
        addDetailRow(row++, "槽位", String.valueOf(item.slot()));
        addDetailRow(row++, "强化等级", "+" + item.enchantLevel());
        addDetailRow(row++, "绑定状态", item.isSoulBound() ? "已绑定" : "未绑定");
        addDetailRow(row++, "装备状态", item.isEquipped() ? "装备中" : "未装备");

        if (item.isFused()) {
            addDetailRow(row++, "融合物品", String.valueOf(item.fusionedItemId()));
        }

        if (item.expireTime() != null) {
            addDetailRow(row++, "过期时间", item.expireTime().toString());
            if (item.isExpired()) {
                Label expiredLabel = new Label("⚠️ 已过期");
                expiredLabel.setStyle("-fx-text-fill: #e74c3c;");
                detailGrid.add(expiredLabel, 1, row);
            }
        }
    }

    private void addDetailRow(int row, String label, String value) {
        Label labelNode = new Label(label + ":");
        labelNode.setStyle("-fx-font-weight: bold;");
        Label valueNode = new Label(value);
        detailGrid.add(labelNode, 0, row);
        detailGrid.add(valueNode, 1, row);
    }

    // ==================== Item Actions ====================

    private void sendItem(int enchantLevel, boolean soulBound) {
        String charIdStr = sendCharIdField.getText().trim();
        String itemIdStr = sendItemIdField.getText().trim();
        int count = sendCountSpinner.getValue();

        if (charIdStr.isBlank() || itemIdStr.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "请填写角色ID和物品ID");
            return;
        }

        int charId, itemId;
        try {
            charId = Integer.parseInt(charIdStr);
            itemId = Integer.parseInt(itemIdStr);
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "角色ID和物品ID必须是数字");
            return;
        }

        // Confirm
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认发送");
        confirm.setHeaderText("发送物品确认");
        confirm.setContentText(String.format(
                "角色ID: %d\n物品ID: %d\n数量: %d\n强化: +%d\n绑定: %s\n\n确定发送?",
                charId, itemId, count, enchantLevel, soulBound ? "是" : "否"
        ));

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        setLoading(true, "发送物品中...");

        Task<OperationChain.ChainResult> task = new Task<>() {
            @Override
            protected OperationChain.ChainResult call() throws Exception {
                OperationChain chain = OperationChain.builder("发送物品")
                        .step("验证角色", () -> {
                            // Verify character exists
                            if (opsService.getCharacterInfo(charId) == null) {
                                throw new IllegalArgumentException("角色不存在: " + charId);
                            }
                        }, null)
                        .step("发送物品", () -> {
                            opsService.sendItem(charId, itemId, count, enchantLevel, soulBound);
                        }, () -> {
                            log.warn("发送物品失败，无法自动回滚");
                        })
                        .step("记录审计", () -> {
                            auditLog.success(OperationType.ITEM, "发送物品",
                                    "角色:" + charId,
                                    String.format("物品:%d x%d +%d", itemId, count, enchantLevel));
                        }, null)
                        .build();

                return chain.execute();
            }
        };

        task.setOnSucceeded(e -> {
            OperationChain.ChainResult chainResult = task.getValue();
            setLoading(false, chainResult.isSuccess() ? "发送成功" : "发送失败");

            if (chainResult.isSuccess()) {
                showAlert(Alert.AlertType.INFORMATION, "物品发送成功！");

                // Publish event
                eventBus.publishAsync(new EventBus.ItemEvent(
                        0, itemId, charId, "send", count
                ));
            } else {
                showAlert(Alert.AlertType.ERROR, "发送失败: " + chainResult.error().getMessage());
            }
        });

        task.setOnFailed(e -> {
            setLoading(false, "发送失败");
            showAlert(Alert.AlertType.ERROR, "发送失败: " + task.getException().getMessage());
            auditLog.failure(OperationType.ITEM, "发送物品", "角色:" + charId, task.getException().getMessage());
        });

        Thread.startVirtualThread(task);
    }

    private void deleteItem() {
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "请先选择一个物品");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText("危险操作");
        confirm.setContentText(String.format(
                "确定要删除物品吗？\n\n%s (x%d)\n所有者: %s\n\n此操作不可撤销！",
                selectedItem.getDisplayName(),
                selectedItem.count(),
                selectedItem.ownerName()
        ));

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    opsService.deleteItem(selectedItem.itemUniqueId());
                    auditLog.warning(OperationType.ITEM, "删除物品",
                            "唯一ID:" + selectedItem.itemUniqueId(),
                            selectedItem.getDisplayName() + " from " + selectedItem.ownerName());
                    return true;
                }
            };

            task.setOnSucceeded(e -> {
                showAlert(Alert.AlertType.INFORMATION, "物品已删除");
                itemData.remove(selectedItem);
                selectedItem = null;
            });

            task.setOnFailed(e -> {
                showAlert(Alert.AlertType.ERROR, "删除失败: " + task.getException().getMessage());
            });

            Thread.startVirtualThread(task);
        }
    }

    private void transferItem() {
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "请先选择一个物品");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("转移物品");
        dialog.setHeaderText("将物品转移到另一个角色");
        dialog.setContentText("目标角色ID:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(targetIdStr -> {
            try {
                int targetId = Integer.parseInt(targetIdStr);

                Task<Boolean> task = new Task<>() {
                    @Override
                    protected Boolean call() throws Exception {
                        opsService.transferItem(selectedItem.itemUniqueId(), targetId);
                        auditLog.success(OperationType.ITEM, "转移物品",
                                "唯一ID:" + selectedItem.itemUniqueId(),
                                String.format("%s -> 角色:%d", selectedItem.ownerName(), targetId));
                        return true;
                    }
                };

                task.setOnSucceeded(e -> {
                    showAlert(Alert.AlertType.INFORMATION, "物品转移成功");
                    searchItems(); // Refresh
                });

                task.setOnFailed(e -> {
                    showAlert(Alert.AlertType.ERROR, "转移失败: " + task.getException().getMessage());
                });

                Thread.startVirtualThread(task);
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "无效的角色ID");
            }
        });
    }

    private void duplicateItem() {
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "请先选择一个物品");
            return;
        }

        // Pre-fill send form with selected item
        sendCharIdField.setText(String.valueOf(selectedItem.ownerId()));
        sendItemIdField.setText(String.valueOf(selectedItem.itemId()));
        sendCountSpinner.getValueFactory().setValue(selectedItem.count());

        showAlert(Alert.AlertType.INFORMATION, "已填充发送表单，请切换到\"发送物品\"标签页确认发送");
    }

    private void refreshStats() {
        setLoading(true, "加载统计数据...");

        Task<Map<String, Object>> task = new Task<>() {
            @Override
            protected Map<String, Object> call() throws Exception {
                Map<String, Object> stats = new java.util.HashMap<>();

                if (opsService == null) {
                    return stats;
                }

                // 查询总物品数
                try {
                    var results = opsService.getConnection().getJdbcTemplate()
                            .queryForList("SELECT COUNT(*) as cnt FROM inventory");
                    if (!results.isEmpty()) {
                        stats.put("totalItems", results.get(0).get("cnt"));
                    }
                } catch (Exception e) {
                    stats.put("totalItems", 0);
                }

                // 查询已绑定物品数
                try {
                    var results = opsService.getConnection().getJdbcTemplate()
                            .queryForList("SELECT COUNT(*) as cnt FROM inventory WHERE is_soul_bound = 1");
                    if (!results.isEmpty()) {
                        stats.put("boundItems", results.get(0).get("cnt"));
                    }
                } catch (Exception e) {
                    stats.put("boundItems", 0);
                }

                // 查询强化装备数量
                try {
                    var results = opsService.getConnection().getJdbcTemplate()
                            .queryForList("SELECT COUNT(*) as cnt FROM inventory WHERE enchant_level > 0");
                    if (!results.isEmpty()) {
                        stats.put("enchantedItems", results.get(0).get("cnt"));
                    }
                } catch (Exception e) {
                    stats.put("enchantedItems", 0);
                }

                // 查询过期物品数量
                try {
                    var results = opsService.getConnection().getJdbcTemplate()
                            .queryForList("SELECT COUNT(*) as cnt FROM inventory WHERE expire_time IS NOT NULL AND expire_time < GETDATE()");
                    if (!results.isEmpty()) {
                        stats.put("expiredItems", results.get(0).get("cnt"));
                    }
                } catch (Exception e) {
                    stats.put("expiredItems", 0);
                }

                return stats;
            }
        };

        task.setOnSucceeded(e -> {
            Map<String, Object> stats = task.getValue();
            Platform.runLater(() -> {
                // 更新统计卡片
                updateStatCard("总物品数", stats.getOrDefault("totalItems", 0));
                updateStatCard("已绑定物品", stats.getOrDefault("boundItems", 0));
                updateStatCard("强化装备", stats.getOrDefault("enchantedItems", 0));
                updateStatCard("过期物品", stats.getOrDefault("expiredItems", 0));
                setLoading(false, "统计数据已刷新");
            });
        });

        task.setOnFailed(e -> {
            log.error("加载统计失败", task.getException());
            setLoading(false, "加载统计失败: " + task.getException().getMessage());
        });

        Thread.startVirtualThread(task);
    }

    private void updateStatCard(String title, Object value) {
        // 查找并更新统计卡片的值
        // 遍历子节点找到对应的卡片并更新
        for (javafx.scene.Node node : getChildren()) {
            if (node instanceof TabPane tabPane) {
                for (Tab tab : tabPane.getTabs()) {
                    if (tab.getText().contains("统计") && tab.getContent() instanceof VBox vbox) {
                        for (javafx.scene.Node child : vbox.getChildren()) {
                            if (child instanceof HBox hbox) {
                                for (javafx.scene.Node card : hbox.getChildren()) {
                                    if (card instanceof VBox cardBox && cardBox.getChildren().size() >= 2) {
                                        if (cardBox.getChildren().get(0) instanceof Label titleLabel
                                                && titleLabel.getText().equals(title)) {
                                            if (cardBox.getChildren().get(1) instanceof Label valueLabel) {
                                                valueLabel.setText(formatNumber(value));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private String formatNumber(Object num) {
        if (num == null) return "0";
        long value = num instanceof Number ? ((Number) num).longValue() : 0;
        if (value >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }

    // ==================== Helpers ====================

    private void setLoading(boolean loading, String message) {
        Platform.runLater(() -> {
            progress.setVisible(loading);
            statusLabel.setText(message);
        });
    }

    private void updateItemCount(int count) {
        Label label = (Label) lookup("#itemCountLabel");
        if (label != null) {
            label.setText("物品数: " + count);
        }
    }

    private void showAlert(Alert.AlertType type, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}
