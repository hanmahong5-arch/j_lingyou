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
import red.jiuzhou.ops.model.GameGuild;
import red.jiuzhou.ops.service.GameOpsService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 公会管理面板
 *
 * 提供公会数据的查询和管理功能：
 * - 公会搜索和列表
 * - 公会详情查看
 * - 公会改名/解散
 * - 成员管理
 * - 公会历史记录
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class GuildPanel extends VBox {

    private static final Logger log = LoggerFactory.getLogger(GuildPanel.class);

    private final GameOpsService opsService;
    private final AuditLog auditLog = AuditLog.getInstance();
    private final EventBus eventBus = EventBus.getInstance();

    // Search
    private final TextField searchField = new TextField();
    private final ComboBox<String> raceFilter = new ComboBox<>();
    private final Spinner<Integer> levelFilter = new Spinner<>(0, 10, 0);

    // Table
    private final TableView<GameGuild> guildTable = new TableView<>();
    private final ObservableList<GameGuild> guildData = FXCollections.observableArrayList();

    // Detail
    private final Label detailTitle = new Label("公会详情");
    private final GridPane detailGrid = new GridPane();
    private final TextArea announcementArea = new TextArea();

    // Members
    private final TableView<Map<String, Object>> memberTable = new TableView<>();

    // Status
    private final Label statusLabel = new Label("就绪");
    private final ProgressIndicator progress = new ProgressIndicator();

    private GameGuild selectedGuild;

    public GuildPanel(GameOpsService opsService) {
        this.opsService = opsService;
        buildUI();
    }

    private void buildUI() {
        setSpacing(12);
        setPadding(new Insets(12));

        // Header
        Label header = new Label("👥 公会管理");
        header.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Search bar
        HBox searchBar = buildSearchBar();

        // Main content - Split pane
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.5);

        // Left - Guild table
        VBox tableBox = buildGuildTable();

        // Right - Guild details
        VBox detailBox = buildDetailPane();

        splitPane.getItems().addAll(tableBox, detailBox);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        // Status bar
        HBox statusBar = buildStatusBar();

        getChildren().addAll(header, searchBar, splitPane, statusBar);
    }

    private HBox buildSearchBar() {
        HBox bar = new HBox(12);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8));
        bar.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 4;");

        // Search field
        searchField.setPromptText("输入公会名称或ID搜索...");
        searchField.setPrefWidth(250);
        searchField.setOnAction(e -> searchGuilds());

        // Race filter
        raceFilter.getItems().addAll("全部", "天族", "魔族");
        raceFilter.setValue("全部");
        raceFilter.setOnAction(e -> searchGuilds());

        // Level filter
        levelFilter.setEditable(true);
        levelFilter.setPrefWidth(80);

        // Search button
        Button searchBtn = new Button("🔍 搜索");
        searchBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        searchBtn.setOnAction(e -> searchGuilds());

        // Refresh button
        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setOnAction(e -> loadAllGuilds());

        bar.getChildren().addAll(
                new Label("搜索:"), searchField,
                new Label("种族:"), raceFilter,
                new Label("等级≥:"), levelFilter,
                searchBtn, refreshBtn
        );

        return bar;
    }

    @SuppressWarnings("unchecked")
    private VBox buildGuildTable() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));

        Label title = new Label("公会列表");
        title.setStyle("-fx-font-weight: bold;");

        // Table columns
        TableColumn<GameGuild, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().guildId())));
        idCol.setPrefWidth(60);

        TableColumn<GameGuild, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().name()));
        nameCol.setPrefWidth(150);

        TableColumn<GameGuild, String> levelCol = new TableColumn<>("等级");
        levelCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().level())));
        levelCol.setPrefWidth(50);

        TableColumn<GameGuild, String> raceCol = new TableColumn<>("种族");
        raceCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getRaceDisplay()));
        raceCol.setPrefWidth(60);

        TableColumn<GameGuild, String> membersCol = new TableColumn<>("成员");
        membersCol.setCellValueFactory(cell ->
                new SimpleStringProperty(
                        cell.getValue().memberCount() + "/" + cell.getValue().maxMembers()
                ));
        membersCol.setPrefWidth(70);

        TableColumn<GameGuild, String> leaderCol = new TableColumn<>("会长");
        leaderCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().leaderName()));
        leaderCol.setPrefWidth(100);

        TableColumn<GameGuild, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getStatusDisplay()));
        statusCol.setPrefWidth(60);

        guildTable.getColumns().addAll(idCol, nameCol, levelCol, raceCol, membersCol, leaderCol, statusCol);
        guildTable.setItems(guildData);
        VBox.setVgrow(guildTable, Priority.ALWAYS);

        // Selection listener
        guildTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectGuild(newVal);
            }
        });

        // Action buttons
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button renameBtn = new Button("✏️ 改名");
        renameBtn.setOnAction(e -> renameGuild());

        Button disbandBtn = new Button("🗑️ 解散");
        disbandBtn.setStyle("-fx-text-fill: #e74c3c;");
        disbandBtn.setOnAction(e -> disbandGuild());

        Button exportBtn = new Button("📤 导出");
        exportBtn.setOnAction(e -> exportGuildData());

        actions.getChildren().addAll(renameBtn, disbandBtn, exportBtn);

        box.getChildren().addAll(title, guildTable, actions);
        return box;
    }

    private VBox buildDetailPane() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(8));

        // Detail header
        detailTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // Detail grid
        detailGrid.setHgap(12);
        detailGrid.setVgap(8);
        detailGrid.setPadding(new Insets(8));
        detailGrid.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 4;");

        // Announcement
        TitledPane announcementPane = new TitledPane("公告", announcementArea);
        announcementPane.setCollapsible(true);
        announcementArea.setEditable(false);
        announcementArea.setPrefRowCount(3);
        announcementArea.setWrapText(true);

        // Members table
        TitledPane membersPane = buildMembersPane();
        VBox.setVgrow(membersPane, Priority.ALWAYS);

        // Action buttons
        HBox detailActions = new HBox(8);
        detailActions.setAlignment(Pos.CENTER);

        Button viewHistoryBtn = new Button("📜 历史记录");
        viewHistoryBtn.setOnAction(e -> showGuildHistory());

        Button viewWarehouseBtn = new Button("📦 仓库日志");
        viewWarehouseBtn.setOnAction(e -> showWarehouseHistory());

        Button editAnnouncementBtn = new Button("✏️ 修改公告");
        editAnnouncementBtn.setOnAction(e -> editAnnouncement());

        detailActions.getChildren().addAll(viewHistoryBtn, viewWarehouseBtn, editAnnouncementBtn);

        box.getChildren().addAll(detailTitle, detailGrid, announcementPane, membersPane, detailActions);
        return box;
    }

    @SuppressWarnings("unchecked")
    private TitledPane buildMembersPane() {
        VBox content = new VBox(8);

        // Member table columns
        TableColumn<Map<String, Object>, String> nameCol = new TableColumn<>("角色名");
        nameCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().get("name"))));
        nameCol.setPrefWidth(100);

        TableColumn<Map<String, Object>, String> rankCol = new TableColumn<>("职位");
        rankCol.setCellValueFactory(cell ->
                new SimpleStringProperty(getMemberRank(cell.getValue().get("rank"))));
        rankCol.setPrefWidth(60);

        TableColumn<Map<String, Object>, String> levelCol = new TableColumn<>("等级");
        levelCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().get("level"))));
        levelCol.setPrefWidth(50);

        TableColumn<Map<String, Object>, String> contribCol = new TableColumn<>("贡献");
        contribCol.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().get("contribution"))));
        contribCol.setPrefWidth(70);

        TableColumn<Map<String, Object>, String> onlineCol = new TableColumn<>("在线");
        onlineCol.setCellValueFactory(cell -> {
            Object online = cell.getValue().get("online");
            return new SimpleStringProperty(
                    online != null && (Integer) online == 1 ? "✅" : "❌"
            );
        });
        onlineCol.setPrefWidth(50);

        memberTable.getColumns().addAll(nameCol, rankCol, levelCol, contribCol, onlineCol);
        memberTable.setPrefHeight(200);

        // Member actions
        HBox memberActions = new HBox(8);
        memberActions.setAlignment(Pos.CENTER_LEFT);

        Button kickBtn = new Button("踢出成员");
        kickBtn.setOnAction(e -> kickMember());

        Button promoteBtn = new Button("提升职位");
        promoteBtn.setOnAction(e -> promoteMember());

        memberActions.getChildren().addAll(kickBtn, promoteBtn);

        content.getChildren().addAll(memberTable, memberActions);

        TitledPane pane = new TitledPane("成员列表", content);
        pane.setCollapsible(true);
        return pane;
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

        Label countLabel = new Label("公会数: 0");
        countLabel.setId("guildCountLabel");

        bar.getChildren().addAll(statusLabel, progress, spacer, countLabel);
        return bar;
    }

    // ==================== Data Operations ====================

    private void searchGuilds() {
        String keyword = searchField.getText().trim();
        String race = raceFilter.getValue();
        int minLevel = levelFilter.getValue();

        setLoading(true, "搜索中...");

        Task<List<GameGuild>> task = new Task<>() {
            @Override
            protected List<GameGuild> call() throws Exception {
                if (opsService == null) {
                    return List.of();
                }

                // Build search conditions
                Map<String, Object> conditions = new java.util.HashMap<>();

                if (!keyword.isBlank()) {
                    // Try as ID first
                    try {
                        int guildId = Integer.parseInt(keyword);
                        conditions.put("id", guildId);
                    } catch (NumberFormatException e) {
                        // Search by name
                        conditions.put("name", keyword);
                    }
                }

                if (!"全部".equals(race)) {
                    conditions.put("race", "天族".equals(race) ? "ELYOS" : "ASMODIAN");
                }

                if (minLevel > 0) {
                    conditions.put("min_level", minLevel);
                }

                return opsService.searchGuilds(conditions, 100);
            }
        };

        task.setOnSucceeded(e -> {
            List<GameGuild> results = task.getValue();
            Platform.runLater(() -> {
                guildData.setAll(results);
                setLoading(false, "找到 " + results.size() + " 个公会");
                updateGuildCount(results.size());
            });

            auditLog.info(OperationType.GUILD, "搜索公会",
                    "关键词:" + keyword, "结果:" + results.size());
        });

        task.setOnFailed(e -> {
            log.error("搜索公会失败", task.getException());
            setLoading(false, "搜索失败: " + task.getException().getMessage());
        });

        Thread.startVirtualThread(task);
    }

    private void loadAllGuilds() {
        setLoading(true, "加载公会列表...");

        Task<List<GameGuild>> task = new Task<>() {
            @Override
            protected List<GameGuild> call() throws Exception {
                if (opsService == null) {
                    return List.of();
                }
                return opsService.getAllGuilds(0, 500);
            }
        };

        task.setOnSucceeded(e -> {
            List<GameGuild> results = task.getValue();
            Platform.runLater(() -> {
                guildData.setAll(results);
                setLoading(false, "已加载 " + results.size() + " 个公会");
                updateGuildCount(results.size());
            });
        });

        task.setOnFailed(e -> {
            log.error("加载公会失败", task.getException());
            setLoading(false, "加载失败");
        });

        Thread.startVirtualThread(task);
    }

    private void selectGuild(GameGuild guild) {
        this.selectedGuild = guild;

        // Update detail title
        detailTitle.setText("公会详情 - " + guild.name());

        // Update detail grid
        detailGrid.getChildren().clear();
        int row = 0;

        addDetailRow(row++, "ID", String.valueOf(guild.guildId()));
        addDetailRow(row++, "名称", guild.name());
        addDetailRow(row++, "等级", String.valueOf(guild.level()));
        addDetailRow(row++, "种族", guild.getRaceDisplay());
        addDetailRow(row++, "成员", guild.memberCount() + "/" + guild.maxMembers());
        addDetailRow(row++, "会长", guild.leaderName() + " (ID:" + guild.leaderId() + ")");
        addDetailRow(row++, "贡献点", String.valueOf(guild.contribution()));
        addDetailRow(row++, "资金", formatKinah(guild.kinah()));
        addDetailRow(row++, "仓库等级", String.valueOf(guild.warehouseLevel()));
        addDetailRow(row++, "排名", guild.rank() > 0 ? "#" + guild.rank() : "未排名");
        addDetailRow(row++, "创建时间", formatDateTime(guild.createTime()));
        addDetailRow(row++, "状态", guild.getStatusDisplay());

        // Update announcement
        announcementArea.setText(guild.announcement() != null ? guild.announcement() : "(无公告)");

        // Load members
        loadGuildMembers(guild.guildId());
    }

    private void addDetailRow(int row, String label, String value) {
        Label labelNode = new Label(label + ":");
        labelNode.setStyle("-fx-font-weight: bold;");
        Label valueNode = new Label(value);
        detailGrid.add(labelNode, 0, row);
        detailGrid.add(valueNode, 1, row);
    }

    private void loadGuildMembers(int guildId) {
        Task<List<Map<String, Object>>> task = new Task<>() {
            @Override
            protected List<Map<String, Object>> call() throws Exception {
                if (opsService == null) {
                    return List.of();
                }
                return opsService.getGuildMembers(guildId);
            }
        };

        task.setOnSucceeded(e -> {
            List<Map<String, Object>> members = task.getValue();
            Platform.runLater(() -> {
                memberTable.setItems(FXCollections.observableArrayList(members));
            });
        });

        Thread.startVirtualThread(task);
    }

    // ==================== Guild Actions ====================

    private void renameGuild() {
        if (selectedGuild == null) {
            showAlert(Alert.AlertType.WARNING, "请先选择一个公会");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selectedGuild.name());
        dialog.setTitle("公会改名");
        dialog.setHeaderText("修改公会名称");
        dialog.setContentText("新名称:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (newName.isBlank() || newName.equals(selectedGuild.name())) {
                return;
            }

            // Execute rename with operation chain
            Task<OperationChain.ChainResult> task = new Task<>() {
                @Override
                protected OperationChain.ChainResult call() throws Exception {
                    String oldName = selectedGuild.name();
                    int guildId = selectedGuild.guildId();

                    OperationChain chain = OperationChain.builder("公会改名: " + guildId)
                            .step("验证名称", () -> {
                                if (newName.length() < 2 || newName.length() > 16) {
                                    throw new IllegalArgumentException("名称长度必须在2-16字符之间");
                                }
                            }, null)
                            .step("执行改名", () -> {
                                opsService.renameGuild(guildId, newName);
                            }, () -> {
                                // Rollback - restore old name
                                log.warn("改名失败，尝试回滚: {} -> {}", newName, oldName);
                                try {
                                    opsService.renameGuild(guildId, oldName);
                                } catch (Exception e) {
                                    log.error("回滚失败", e);
                                }
                            })
                            .step("记录审计", () -> {
                                auditLog.success(OperationType.GUILD, "公会改名",
                                        guildId + ":" + oldName, "新名称:" + newName);
                            }, null)
                            .build();

                    return chain.execute();
                }
            };

            task.setOnSucceeded(e -> {
                OperationChain.ChainResult chainResult = task.getValue();
                if (chainResult.isSuccess()) {
                    showAlert(Alert.AlertType.INFORMATION, "公会改名成功");
                    searchGuilds(); // Refresh
                } else {
                    showAlert(Alert.AlertType.ERROR, "改名失败: " + chainResult.error().getMessage());
                }
            });

            Thread.startVirtualThread(task);
        });
    }

    private void disbandGuild() {
        if (selectedGuild == null) {
            showAlert(Alert.AlertType.WARNING, "请先选择一个公会");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认解散");
        confirm.setHeaderText("危险操作警告");
        confirm.setContentText("确定要解散公会 \"" + selectedGuild.name() + "\" 吗？\n\n" +
                "此操作不可撤销！所有成员将被移出公会。");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    opsService.disbandGuild(selectedGuild.guildId());
                    auditLog.warning(OperationType.GUILD, "解散公会",
                            selectedGuild.guildId() + ":" + selectedGuild.name(),
                            "成员数:" + selectedGuild.memberCount());
                    return true;
                }
            };

            task.setOnSucceeded(e -> {
                showAlert(Alert.AlertType.INFORMATION, "公会已解散");
                searchGuilds();
            });

            task.setOnFailed(e -> {
                showAlert(Alert.AlertType.ERROR, "解散失败: " + task.getException().getMessage());
                auditLog.failure(OperationType.GUILD, "解散公会",
                        selectedGuild.name(), task.getException().getMessage());
            });

            Thread.startVirtualThread(task);
        }
    }

    private void showGuildHistory() {
        if (selectedGuild == null) {
            showAlert(Alert.AlertType.WARNING, "请先选择一个公会");
            return;
        }

        // Show history dialog
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("公会历史 - " + selectedGuild.name());
        dialog.setHeaderText("📜 公会事件历史记录");

        VBox content = new VBox(8);
        content.setPadding(new Insets(12));
        content.setPrefWidth(600);
        content.setPrefHeight(400);

        TextArea historyArea = new TextArea();
        historyArea.setEditable(false);
        historyArea.setWrapText(true);
        VBox.setVgrow(historyArea, Priority.ALWAYS);

        // Load history
        Task<List<Map<String, Object>>> task = new Task<>() {
            @Override
            protected List<Map<String, Object>> call() throws Exception {
                return opsService.getGuildHistory(selectedGuild.guildId());
            }
        };

        task.setOnSucceeded(e -> {
            List<Map<String, Object>> history = task.getValue();
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> entry : history) {
                sb.append(String.format("[%s] %s - %s\n",
                        entry.get("time"),
                        entry.get("action"),
                        entry.get("detail")
                ));
            }
            historyArea.setText(sb.length() > 0 ? sb.toString() : "暂无历史记录");
        });

        Thread.startVirtualThread(task);

        content.getChildren().add(historyArea);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void showWarehouseHistory() {
        if (selectedGuild == null) {
            showAlert(Alert.AlertType.WARNING, "请先选择一个公会");
            return;
        }

        // 显示仓库日志对话框
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("仓库日志 - " + selectedGuild.name());
        dialog.setHeaderText("📦 公会仓库操作记录");

        VBox content = new VBox(8);
        content.setPadding(new Insets(12));
        content.setPrefWidth(600);
        content.setPrefHeight(400);

        TextArea historyArea = new TextArea();
        historyArea.setEditable(false);
        historyArea.setWrapText(true);
        VBox.setVgrow(historyArea, Priority.ALWAYS);

        // 加载仓库历史
        Task<List<Map<String, Object>>> task = new Task<>() {
            @Override
            protected List<Map<String, Object>> call() throws Exception {
                // 调用存储过程获取仓库历史
                return opsService.getConnection().callProcedure(
                        "aion_GetGuildWarehouseHistoryList",
                        Map.of("guild_id", selectedGuild.guildId())
                );
            }
        };

        task.setOnSucceeded(e -> {
            List<Map<String, Object>> history = task.getValue();
            StringBuilder sb = new StringBuilder();
            if (history.isEmpty()) {
                sb.append("暂无仓库操作记录");
            } else {
                for (Map<String, Object> entry : history) {
                    sb.append(String.format("[%s] %s - %s (数量:%s)\n",
                            entry.getOrDefault("time", "-"),
                            entry.getOrDefault("action", "-"),
                            entry.getOrDefault("item_name", "-"),
                            entry.getOrDefault("count", "-")
                    ));
                }
            }
            historyArea.setText(sb.toString());
        });

        task.setOnFailed(e -> {
            log.warn("获取仓库日志失败", task.getException());
            historyArea.setText("获取仓库日志失败: " + task.getException().getMessage());
        });

        Thread.startVirtualThread(task);

        content.getChildren().add(historyArea);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void editAnnouncement() {
        if (selectedGuild == null) {
            showAlert(Alert.AlertType.WARNING, "请先选择一个公会");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selectedGuild.announcement());
        dialog.setTitle("修改公告");
        dialog.setHeaderText("修改公会公告");
        dialog.setContentText("公告内容:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newAnnouncement -> {
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    opsService.updateGuildAnnouncement(selectedGuild.guildId(), newAnnouncement);
                    auditLog.success(OperationType.GUILD, "修改公告",
                            selectedGuild.name(), newAnnouncement.substring(0, Math.min(50, newAnnouncement.length())));
                    return null;
                }
            };

            task.setOnSucceeded(e -> {
                announcementArea.setText(newAnnouncement);
                showAlert(Alert.AlertType.INFORMATION, "公告已更新");
            });

            Thread.startVirtualThread(task);
        });
    }

    private void kickMember() {
        Map<String, Object> selected = memberTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "请先选择一个成员");
            return;
        }

        String memberName = String.valueOf(selected.get("name"));
        Object memberIdObj = selected.get("char_id");
        if (memberIdObj == null) {
            memberIdObj = selected.get("id");
        }
        int memberId = memberIdObj instanceof Number ? ((Number) memberIdObj).intValue() : 0;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认踢出");
        confirm.setContentText("确定要将 \"" + memberName + "\" 踢出公会吗？");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    // 调用存储过程踢出成员
                    opsService.getConnection().executeProcedure(
                            "aion_RemoveGuildMember",
                            Map.of("guild_id", selectedGuild.guildId(), "char_id", memberId)
                    );
                    return true;
                }
            };

            task.setOnSucceeded(e -> {
                auditLog.success(OperationType.GUILD, "踢出成员",
                        selectedGuild.name(), memberName);
                showAlert(Alert.AlertType.INFORMATION, "成员已被踢出");
                loadGuildMembers(selectedGuild.guildId());
            });

            task.setOnFailed(e -> {
                log.error("踢出成员失败", task.getException());
                showAlert(Alert.AlertType.ERROR, "踢出失败: " + task.getException().getMessage());
                auditLog.failure(OperationType.GUILD, "踢出成员", memberName, task.getException().getMessage());
            });

            Thread.startVirtualThread(task);
        }
    }

    private void promoteMember() {
        Map<String, Object> selected = memberTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "请先选择一个成员");
            return;
        }

        String memberName = String.valueOf(selected.get("name"));
        Object memberIdObj = selected.get("char_id");
        if (memberIdObj == null) {
            memberIdObj = selected.get("id");
        }
        int memberId = memberIdObj instanceof Number ? ((Number) memberIdObj).intValue() : 0;
        int currentRank = selected.get("rank") instanceof Number
                ? ((Number) selected.get("rank")).intValue() : 3;

        // 选择新职位
        ChoiceDialog<String> dialog = new ChoiceDialog<>(
                getMemberRank(currentRank),
                "会长", "副会长", "精英", "成员"
        );
        dialog.setTitle("修改职位");
        dialog.setHeaderText("修改成员 \"" + memberName + "\" 的职位");
        dialog.setContentText("选择新职位:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newRankStr -> {
            int newRank = switch (newRankStr) {
                case "会长" -> 0;
                case "副会长" -> 1;
                case "精英" -> 2;
                default -> 3;
            };

            if (newRank == currentRank) {
                showAlert(Alert.AlertType.INFORMATION, "职位未变更");
                return;
            }

            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    // 调用存储过程修改成员职位
                    opsService.getConnection().executeProcedure(
                            "aion_SetGuildMemberRank",
                            Map.of("guild_id", selectedGuild.guildId(),
                                   "char_id", memberId,
                                   "rank", newRank)
                    );
                    return true;
                }
            };

            task.setOnSucceeded(e -> {
                auditLog.success(OperationType.GUILD, "修改职位",
                        selectedGuild.name() + ":" + memberName,
                        getMemberRank(currentRank) + " -> " + newRankStr);
                showAlert(Alert.AlertType.INFORMATION, "职位已修改为: " + newRankStr);
                loadGuildMembers(selectedGuild.guildId());
            });

            task.setOnFailed(e -> {
                log.error("修改职位失败", task.getException());
                showAlert(Alert.AlertType.ERROR, "修改失败: " + task.getException().getMessage());
            });

            Thread.startVirtualThread(task);
        });
    }

    private void exportGuildData() {
        if (guildData.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "没有数据可导出");
            return;
        }

        // 使用文件选择器选择导出位置
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("导出公会数据");
        fileChooser.setInitialFileName("guilds_export.csv");
        fileChooser.getExtensionFilters().add(
                new javafx.stage.FileChooser.ExtensionFilter("CSV文件", "*.csv")
        );

        java.io.File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }

        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                try (java.io.PrintWriter writer = new java.io.PrintWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    // 写入 CSV 头
                    writer.println("ID,名称,等级,种族,成员数,最大成员,会长,贡献点,资金,状态");

                    // 写入数据
                    for (GameGuild guild : guildData) {
                        writer.printf("%d,%s,%d,%s,%d,%d,%s,%d,%d,%s%n",
                                guild.guildId(),
                                escapeCsv(guild.name()),
                                guild.level(),
                                guild.getRaceDisplay(),
                                guild.memberCount(),
                                guild.maxMembers(),
                                escapeCsv(guild.leaderName()),
                                guild.contribution(),
                                guild.kinah(),
                                guild.getStatusDisplay()
                        );
                    }
                }
                return true;
            }
        };

        task.setOnSucceeded(e -> {
            auditLog.success(OperationType.GUILD, "导出数据",
                    "数量:" + guildData.size(), file.getName());
            showAlert(Alert.AlertType.INFORMATION, "已导出 " + guildData.size() + " 条公会数据到:\n" + file.getAbsolutePath());
        });

        task.setOnFailed(e -> {
            log.error("导出失败", task.getException());
            showAlert(Alert.AlertType.ERROR, "导出失败: " + task.getException().getMessage());
        });

        Thread.startVirtualThread(task);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // ==================== Helpers ====================

    private void setLoading(boolean loading, String message) {
        Platform.runLater(() -> {
            progress.setVisible(loading);
            statusLabel.setText(message);
        });
    }

    private void updateGuildCount(int count) {
        Label label = (Label) lookup("#guildCountLabel");
        if (label != null) {
            label.setText("公会数: " + count);
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

    private String getMemberRank(Object rank) {
        if (rank == null) return "成员";
        int r = rank instanceof Number ? ((Number) rank).intValue() : 0;
        return switch (r) {
            case 0 -> "会长";
            case 1 -> "副会长";
            case 2 -> "精英";
            default -> "成员";
        };
    }

    private String formatKinah(long kinah) {
        if (kinah >= 1_000_000_000) {
            return String.format("%.2fG", kinah / 1_000_000_000.0);
        } else if (kinah >= 1_000_000) {
            return String.format("%.2fM", kinah / 1_000_000.0);
        } else if (kinah >= 1_000) {
            return String.format("%.2fK", kinah / 1_000.0);
        }
        return String.valueOf(kinah);
    }

    private String formatDateTime(java.time.LocalDateTime dt) {
        if (dt == null) return "-";
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
