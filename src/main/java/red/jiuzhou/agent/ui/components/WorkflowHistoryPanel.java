package red.jiuzhou.agent.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.workflow.DataSnapshot;
import red.jiuzhou.agent.workflow.UndoManager;
import red.jiuzhou.agent.workflow.WorkflowAuditLog;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 工作流历史面板
 *
 * <p>提供工作流操作的可视化历史记录和撤销功能：
 * <ul>
 *   <li>时间线视图 - 按时间顺序展示所有工作流事件</li>
 *   <li>可撤销操作列表 - 显示可以撤销的数据修改操作</li>
 *   <li>快照浏览器 - 查看可用的数据恢复点</li>
 *   <li>一键撤销 - 支持撤销最近操作或指定操作</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class WorkflowHistoryPanel extends VBox {

    private static final Logger log = LoggerFactory.getLogger(WorkflowHistoryPanel.class);

    // 管理器引用
    private final UndoManager undoManager = UndoManager.getInstance();
    private final WorkflowAuditLog auditLog = WorkflowAuditLog.getInstance();
    private final DataSnapshot dataSnapshot = DataSnapshot.getInstance();

    // UI 组件
    private TabPane tabPane;
    private ListView<TimelineEntry> timelineListView;
    private ListView<UndoManager.UndoableOperation> undoListView;
    private ListView<SnapshotEntry> snapshotListView;
    private Label statusLabel;
    private Button undoLastButton;

    // 当前工作流ID
    private String currentWorkflowId;

    // 撤销回调
    private Consumer<UndoManager.UndoResult> onUndoComplete;

    // 时间格式化
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    public WorkflowHistoryPanel() {
        initializeUI();
        setupStyles();
    }

    private void initializeUI() {
        setSpacing(10);
        setPadding(new Insets(10));
        setMinWidth(280);
        setPrefWidth(320);

        // 标题栏
        HBox titleBar = createTitleBar();

        // 标签页
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // 时间线标签页
        Tab timelineTab = new Tab("时间线");
        timelineTab.setContent(createTimelineView());

        // 可撤销操作标签页
        Tab undoTab = new Tab("可撤销");
        undoTab.setContent(createUndoView());

        // 快照标签页
        Tab snapshotTab = new Tab("快照");
        snapshotTab.setContent(createSnapshotView());

        tabPane.getTabs().addAll(timelineTab, undoTab, snapshotTab);

        // 底部操作栏
        HBox actionBar = createActionBar();

        // 状态栏
        statusLabel = new Label("就绪");
        statusLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");

        VBox.setVgrow(tabPane, Priority.ALWAYS);
        getChildren().addAll(titleBar, tabPane, actionBar, statusLabel);
    }

    private HBox createTitleBar() {
        HBox titleBar = new HBox(10);
        titleBar.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("📜 工作流历史");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshButton = new Button("🔄");
        refreshButton.setTooltip(new Tooltip("刷新"));
        refreshButton.setOnAction(e -> refresh());

        Button clearButton = new Button("🗑");
        clearButton.setTooltip(new Tooltip("清空历史"));
        clearButton.setOnAction(e -> confirmClearHistory());

        titleBar.getChildren().addAll(titleLabel, spacer, refreshButton, clearButton);
        return titleBar;
    }

    private Node createTimelineView() {
        VBox container = new VBox(5);
        container.setPadding(new Insets(5));

        timelineListView = new ListView<>();
        timelineListView.setCellFactory(lv -> new TimelineCell());
        timelineListView.setPlaceholder(new Label("暂无历史记录"));

        VBox.setVgrow(timelineListView, Priority.ALWAYS);
        container.getChildren().add(timelineListView);

        return container;
    }

    private Node createUndoView() {
        VBox container = new VBox(5);
        container.setPadding(new Insets(5));

        // 说明文字
        Label helpLabel = new Label("以下操作可以撤销，按时间倒序排列");
        helpLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        helpLabel.setWrapText(true);

        undoListView = new ListView<>();
        undoListView.setCellFactory(lv -> new UndoOperationCell());
        undoListView.setPlaceholder(new Label("没有可撤销的操作"));

        VBox.setVgrow(undoListView, Priority.ALWAYS);
        container.getChildren().addAll(helpLabel, undoListView);

        return container;
    }

    private Node createSnapshotView() {
        VBox container = new VBox(5);
        container.setPadding(new Insets(5));

        // 说明文字
        Label helpLabel = new Label("数据快照用于恢复修改前的数据状态");
        helpLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        helpLabel.setWrapText(true);

        snapshotListView = new ListView<>();
        snapshotListView.setCellFactory(lv -> new SnapshotCell());
        snapshotListView.setPlaceholder(new Label("没有可用的快照"));

        VBox.setVgrow(snapshotListView, Priority.ALWAYS);
        container.getChildren().addAll(helpLabel, snapshotListView);

        return container;
    }

    private HBox createActionBar() {
        HBox actionBar = new HBox(10);
        actionBar.setAlignment(Pos.CENTER);
        actionBar.setPadding(new Insets(5, 0, 5, 0));

        undoLastButton = new Button("↩ 撤销最近操作");
        undoLastButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        undoLastButton.setDisable(true);
        undoLastButton.setOnAction(e -> undoLastOperation());

        Button undoAllButton = new Button("↩↩ 撤销全部");
        undoAllButton.setStyle("-fx-background-color: #F44336; -fx-text-fill: white;");
        undoAllButton.setOnAction(e -> confirmUndoAll());

        actionBar.getChildren().addAll(undoLastButton, undoAllButton);
        return actionBar;
    }

    private void setupStyles() {
        setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #E0E0E0; -fx-border-width: 1;");
    }

    // ==================== 公共方法 ====================

    /**
     * 设置当前工作流ID
     */
    public void setCurrentWorkflowId(String workflowId) {
        this.currentWorkflowId = workflowId;
        refresh();
    }

    /**
     * 设置撤销完成回调
     */
    public void setOnUndoComplete(Consumer<UndoManager.UndoResult> callback) {
        this.onUndoComplete = callback;
    }

    /**
     * 刷新面板数据
     */
    public void refresh() {
        refreshTimeline();
        refreshUndoList();
        refreshSnapshotList();
        updateUndoButtonState();
    }

    /**
     * 添加时间线条目
     */
    public void addTimelineEntry(String eventType, String description, String detail) {
        TimelineEntry entry = new TimelineEntry();
        entry.eventType = eventType;
        entry.description = description;
        entry.detail = detail;
        entry.timestamp = Instant.now();

        Platform.runLater(() -> {
            timelineListView.getItems().add(0, entry);
            updateStatus("新增事件: " + description);
        });
    }

    /**
     * 通知有新的可撤销操作
     */
    public void notifyNewUndoableOperation() {
        Platform.runLater(() -> {
            refreshUndoList();
            updateUndoButtonState();
        });
    }

    // ==================== 私有方法 ====================

    private void refreshTimeline() {
        Platform.runLater(() -> {
            timelineListView.getItems().clear();

            if (currentWorkflowId != null) {
                // 从审计日志获取时间线
                List<WorkflowAuditLog.TimelineEntry> timeline =
                        auditLog.getWorkflowTimeline(currentWorkflowId);

                for (WorkflowAuditLog.TimelineEntry entry : timeline) {
                    TimelineEntry uiEntry = new TimelineEntry();
                    uiEntry.eventType = entry.eventType;
                    uiEntry.description = entry.description;
                    uiEntry.detail = entry.detail;
                    uiEntry.timestamp = entry.timestamp;
                    timelineListView.getItems().add(uiEntry);
                }
            }
        });
    }

    private void refreshUndoList() {
        Platform.runLater(() -> {
            undoListView.getItems().clear();

            List<UndoManager.UndoableOperation> operations;
            if (currentWorkflowId != null) {
                operations = undoManager.getWorkflowUndoableOperations(currentWorkflowId);
            } else {
                operations = undoManager.getUndoableOperations();
            }

            undoListView.getItems().addAll(operations);
        });
    }

    private void refreshSnapshotList() {
        Platform.runLater(() -> {
            snapshotListView.getItems().clear();

            if (currentWorkflowId != null) {
                List<DataSnapshot.SnapshotEntry> snapshots = dataSnapshot.getWorkflowSnapshots(currentWorkflowId);

                for (DataSnapshot.SnapshotEntry snapshot : snapshots) {
                    SnapshotEntry entry = new SnapshotEntry();
                    entry.snapshotId = snapshot.snapshotId;
                    entry.tableName = snapshot.tableName;
                    entry.rowCount = snapshot.getRowCount();
                    entry.timestamp = snapshot.createdAt;
                    entry.restored = snapshot.isRestored();
                    snapshotListView.getItems().add(entry);
                }
            }
        });
    }

    private void updateUndoButtonState() {
        Platform.runLater(() -> {
            boolean canUndo;
            if (currentWorkflowId != null) {
                canUndo = undoManager.canUndo(currentWorkflowId);
            } else {
                canUndo = undoManager.canUndo();
            }

            undoLastButton.setDisable(!canUndo);

            if (canUndo) {
                String desc = undoManager.getLastUndoDescription();
                undoLastButton.setTooltip(new Tooltip(desc));
            }
        });
    }

    private void undoLastOperation() {
        UndoManager.UndoResult result;
        if (currentWorkflowId != null) {
            result = undoManager.undoWorkflowLast(currentWorkflowId);
        } else {
            result = undoManager.undoLast();
        }

        handleUndoResult(result);
    }

    private void undoOperation(UndoManager.UndoableOperation operation) {
        UndoManager.UndoResult result = undoManager.undoOperation(operation.operationId);
        handleUndoResult(result);
    }

    private void restoreSnapshot(SnapshotEntry snapshot) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认恢复");
        confirm.setHeaderText("恢复数据快照");
        confirm.setContentText(String.format(
                "确定要恢复快照吗？\n\n表名: %s\n行数: %d\n时间: %s",
                snapshot.tableName,
                snapshot.rowCount,
                formatTime(snapshot.timestamp)
        ));

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                int restored = dataSnapshot.restoreSnapshot(snapshot.snapshotId);
                if (restored >= 0) {
                    updateStatus("已恢复 " + restored + " 行数据");
                    refresh();

                    if (onUndoComplete != null) {
                        onUndoComplete.accept(UndoManager.UndoResult.success("快照恢复成功", restored));
                    }
                } else {
                    showError("快照恢复失败");
                }
            }
        });
    }

    private void handleUndoResult(UndoManager.UndoResult result) {
        if (result.success) {
            updateStatus(result.message + " (恢复 " + result.restoredRows + " 行)");
            refresh();

            // 添加时间线条目
            addTimelineEntry("UNDO", "撤销操作", result.message);

            if (onUndoComplete != null) {
                onUndoComplete.accept(result);
            }
        } else {
            showError(result.message);
        }
    }

    private void confirmUndoAll() {
        if (currentWorkflowId == null) {
            showError("请先选择一个工作流");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认撤销");
        confirm.setHeaderText("撤销所有操作");
        confirm.setContentText("确定要撤销当前工作流的所有操作吗？\n\n此操作将恢复所有数据修改，不可恢复。");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                UndoManager.UndoResult result = undoManager.undoWorkflowAll(currentWorkflowId);
                handleUndoResult(result);
            }
        });
    }

    private void confirmClearHistory() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认清空");
        confirm.setHeaderText("清空历史记录");
        confirm.setContentText("确定要清空所有历史记录吗？\n\n注意：这不会影响数据库中的数据，只是清空UI显示。");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                timelineListView.getItems().clear();
                updateStatus("历史记录已清空");
            }
        });
    }

    private void updateStatus(String message) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
        });
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private String formatTime(Instant instant) {
        if (instant == null) return "";
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DATETIME_FORMATTER);
    }

    // ==================== 内部类型 ====================

    /**
     * 时间线条目
     */
    public static class TimelineEntry {
        public String eventType;
        public String description;
        public String detail;
        public Instant timestamp;
    }

    /**
     * 快照条目
     */
    public static class SnapshotEntry {
        public String snapshotId;
        public String tableName;
        public int rowCount;
        public Instant timestamp;
        public boolean restored;
    }

    /**
     * 时间线单元格
     */
    private class TimelineCell extends ListCell<TimelineEntry> {
        @Override
        protected void updateItem(TimelineEntry entry, boolean empty) {
            super.updateItem(entry, empty);

            if (empty || entry == null) {
                setGraphic(null);
                setText(null);
            } else {
                VBox container = new VBox(2);
                container.setPadding(new Insets(5));

                // 时间和图标
                HBox header = new HBox(5);
                header.setAlignment(Pos.CENTER_LEFT);

                String icon = getEventIcon(entry.eventType);
                Label iconLabel = new Label(icon);
                iconLabel.setStyle("-fx-font-size: 14px;");

                Label timeLabel = new Label(formatTime(entry.timestamp));
                timeLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");

                header.getChildren().addAll(iconLabel, timeLabel);

                // 描述
                Label descLabel = new Label(entry.description);
                descLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

                container.getChildren().addAll(header, descLabel);

                // 详情（如果有）
                if (entry.detail != null && !entry.detail.isEmpty()) {
                    Label detailLabel = new Label(entry.detail);
                    detailLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
                    detailLabel.setWrapText(true);
                    detailLabel.setMaxWidth(250);
                    container.getChildren().add(detailLabel);
                }

                setGraphic(container);
            }
        }

        private String getEventIcon(String eventType) {
            if (eventType == null) return "📌";
            return switch (eventType.toUpperCase()) {
                case "WORKFLOW_STARTED" -> "🚀";
                case "WORKFLOW_COMPLETED" -> "✅";
                case "WORKFLOW_CANCELLED" -> "❌";
                case "WORKFLOW_FAILED" -> "💥";
                case "STEP_STARTED" -> "▶";
                case "STEP_CONFIRMED" -> "✔";
                case "STEP_CORRECTED" -> "✏";
                case "STEP_SKIPPED" -> "⏭";
                case "SQL_EXECUTED" -> "💾";
                case "DATA_ROLLBACK" -> "↩";
                case "UNDO" -> "↶";
                default -> "📌";
            };
        }
    }

    /**
     * 可撤销操作单元格
     */
    private class UndoOperationCell extends ListCell<UndoManager.UndoableOperation> {
        @Override
        protected void updateItem(UndoManager.UndoableOperation op, boolean empty) {
            super.updateItem(op, empty);

            if (empty || op == null) {
                setGraphic(null);
                setText(null);
            } else {
                HBox container = new HBox(10);
                container.setAlignment(Pos.CENTER_LEFT);
                container.setPadding(new Insets(5));

                // 操作类型图标
                String sqlType = op.getSqlType();
                String icon = switch (sqlType) {
                    case "UPDATE" -> "✏";
                    case "INSERT" -> "➕";
                    case "DELETE" -> "🗑";
                    default -> "💾";
                };

                Label iconLabel = new Label(icon);
                iconLabel.setStyle("-fx-font-size: 16px;");

                // 操作信息
                VBox info = new VBox(2);
                HBox.setHgrow(info, Priority.ALWAYS);

                Label sqlLabel = new Label(op.getShortSql());
                sqlLabel.setStyle("-fx-font-size: 11px; -fx-font-family: monospace;");
                sqlLabel.setMaxWidth(180);

                HBox meta = new HBox(10);
                Label timeLabel = new Label(op.getFormattedTime());
                timeLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");

                Label rowsLabel = new Label(op.affectedRows + " 行");
                rowsLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 10px;");

                meta.getChildren().addAll(timeLabel, rowsLabel);
                info.getChildren().addAll(sqlLabel, meta);

                // 撤销按钮
                Button undoBtn = new Button("↩");
                undoBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-size: 12px;");
                undoBtn.setTooltip(new Tooltip("撤销此操作"));
                undoBtn.setOnAction(e -> undoOperation(op));

                container.getChildren().addAll(iconLabel, info, undoBtn);
                setGraphic(container);
            }
        }
    }

    /**
     * 快照单元格
     */
    private class SnapshotCell extends ListCell<SnapshotEntry> {
        @Override
        protected void updateItem(SnapshotEntry entry, boolean empty) {
            super.updateItem(entry, empty);

            if (empty || entry == null) {
                setGraphic(null);
                setText(null);
            } else {
                HBox container = new HBox(10);
                container.setAlignment(Pos.CENTER_LEFT);
                container.setPadding(new Insets(5));

                // 快照图标
                Label iconLabel = new Label(entry.restored ? "📂" : "📁");
                iconLabel.setStyle("-fx-font-size: 16px;");

                // 快照信息
                VBox info = new VBox(2);
                HBox.setHgrow(info, Priority.ALWAYS);

                Label tableLabel = new Label(entry.tableName);
                tableLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

                HBox meta = new HBox(10);
                Label timeLabel = new Label(formatTime(entry.timestamp));
                timeLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10px;");

                Label rowsLabel = new Label(entry.rowCount + " 行");
                rowsLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 10px;");

                if (entry.restored) {
                    Label restoredLabel = new Label("(已恢复)");
                    restoredLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-size: 10px;");
                    meta.getChildren().addAll(timeLabel, rowsLabel, restoredLabel);
                } else {
                    meta.getChildren().addAll(timeLabel, rowsLabel);
                }

                info.getChildren().addAll(tableLabel, meta);

                // 恢复按钮
                Button restoreBtn = new Button("恢复");
                restoreBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 11px;");
                restoreBtn.setDisable(entry.restored);
                restoreBtn.setOnAction(e -> restoreSnapshot(entry));

                container.getChildren().addAll(iconLabel, info, restoreBtn);
                setGraphic(container);
            }
        }
    }
}
