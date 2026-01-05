package red.jiuzhou.agent.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import red.jiuzhou.agent.workflow.*;
import red.jiuzhou.agent.workflow.WorkflowAuditLog.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * 工作流历史面板
 *
 * <p>提供完整的工作流执行历史追溯能力：
 * <ul>
 *   <li>时间轴视图 - 显示工作流执行的每个步骤</li>
 *   <li>撤销栈管理 - 显示和执行撤销操作</li>
 *   <li>详情展示 - SQL语句、影响行数等</li>
 *   <li>历史回顾 - 查看过去的工作流记录</li>
 * </ul>
 *
 * <p>设计原则：
 * <ol>
 *   <li>可追溯 - 完整记录每次AI操作的历史</li>
 *   <li>可撤销 - 一键撤销任何数据修改</li>
 *   <li>可视化 - 清晰展示执行流程和状态</li>
 * </ol>
 *
 * @author Claude
 * @version 1.0
 */
public class WorkflowHistoryPanel extends VBox {

    // 颜色常量
    private static final String COLOR_TIMELINE = "#673AB7";     // 紫色 - 时间轴
    private static final String COLOR_UNDO = "#FF5722";         // 橙红色 - 撤销
    private static final String COLOR_DETAIL = "#009688";       // 青色 - 详情
    private static final String COLOR_HISTORY = "#607D8B";      // 灰蓝色 - 历史

    // 状态颜色
    private static final String STATUS_RUNNING = "#2196F3";     // 蓝色
    private static final String STATUS_COMPLETED = "#4CAF50";   // 绿色
    private static final String STATUS_FAILED = "#F44336";      // 红色
    private static final String STATUS_CANCELLED = "#9E9E9E";   // 灰色
    private static final String STATUS_PENDING = "#FF9800";     // 橙色

    // 工作流管理器
    private final WorkflowAuditLog auditLog;
    private final UndoManager undoManager;

    // 当前工作流
    private String currentWorkflowId;

    // UI组件
    private final VBox timelineContent;
    private final VBox undoStackContent;
    private final VBox detailContent;
    private final VBox historyListContent;

    private final Label undoCountLabel;
    private final Button undoLastButton;
    private final Button undoAllButton;

    private final Label detailTitleLabel;
    private final Label detailSqlLabel;
    private final Label detailAffectedLabel;
    private final Label detailSnapshotLabel;

    // 回调
    private Consumer<UndoManager.UndoResult> onUndoComplete;
    private Consumer<String> onWorkflowSelected;

    // 日期格式
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public WorkflowHistoryPanel() {
        this.auditLog = WorkflowAuditLog.getInstance();
        this.undoManager = UndoManager.getInstance();

        this.setSpacing(8);
        this.setPadding(new Insets(8));
        this.getStyleClass().add("workflow-history-panel");

        // 标题
        Label titleLabel = new Label("工作流历史");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // 1. 时间轴区域
        timelineContent = new VBox(4);
        TitledPane timelinePane = createSection("时间轴", COLOR_TIMELINE, timelineContent);

        // 2. 撤销栈区域
        undoStackContent = new VBox(4);
        undoCountLabel = new Label("0 个可撤销操作");
        undoCountLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        undoLastButton = new Button("↩ 撤销最近操作");
        undoLastButton.setDisable(true);
        undoLastButton.setOnAction(e -> performUndoLast());

        undoAllButton = new Button("↩↩ 撤销全部");
        undoAllButton.setDisable(true);
        undoAllButton.setOnAction(e -> performUndoAll());

        HBox undoButtons = new HBox(8, undoLastButton, undoAllButton);
        undoButtons.setAlignment(Pos.CENTER_LEFT);

        VBox undoContainer = new VBox(8, undoCountLabel, undoStackContent, undoButtons);
        TitledPane undoPane = createSection("撤销栈", COLOR_UNDO, undoContainer);

        // 3. 详情区域
        detailContent = new VBox(4);
        detailTitleLabel = new Label("选择一个步骤查看详情");
        detailTitleLabel.setStyle("-fx-font-weight: bold;");

        detailSqlLabel = new Label("");
        detailSqlLabel.setWrapText(true);
        detailSqlLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-background-color: #f5f5f5; -fx-padding: 4;");

        detailAffectedLabel = new Label("");
        detailAffectedLabel.setStyle("-fx-font-size: 11px;");

        detailSnapshotLabel = new Label("");
        detailSnapshotLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        detailContent.getChildren().addAll(detailTitleLabel, detailSqlLabel, detailAffectedLabel, detailSnapshotLabel);
        TitledPane detailPane = createSection("详情", COLOR_DETAIL, detailContent);

        // 4. 历史记录区域
        historyListContent = new VBox(4);
        ScrollPane historyScroll = new ScrollPane(historyListContent);
        historyScroll.setFitToWidth(true);
        historyScroll.setPrefHeight(150);
        historyScroll.setStyle("-fx-background-color: transparent;");

        Button refreshHistoryBtn = new Button("刷新历史");
        refreshHistoryBtn.setOnAction(e -> loadRecentHistory());

        VBox historyContainer = new VBox(8, historyScroll, refreshHistoryBtn);
        TitledPane historyPane = createSection("历史记录", COLOR_HISTORY, historyContainer);

        // 使用Accordion组织面板
        Accordion accordion = new Accordion(timelinePane, undoPane, detailPane, historyPane);
        accordion.setExpandedPane(timelinePane);
        VBox.setVgrow(accordion, Priority.ALWAYS);

        this.getChildren().addAll(titleLabel, accordion);

        // 初始化显示
        clearTimeline();
        updateUndoStack();
        loadRecentHistory();
    }

    /**
     * 创建带颜色指示器的区域
     */
    private TitledPane createSection(String title, String color, Region content) {
        TitledPane pane = new TitledPane();
        pane.setAnimated(true);

        // 颜色指示器
        Circle indicator = new Circle(6, Color.web(color));
        Label titleLbl = new Label(" " + title);
        HBox titleBox = new HBox(4, indicator, titleLbl);
        pane.setGraphic(titleBox);
        pane.setText("");

        if (content instanceof VBox vbox) {
            vbox.setPadding(new Insets(8));
        }
        pane.setContent(content);

        return pane;
    }

    // ==================== 公共方法 ====================

    /**
     * 设置当前工作流ID
     */
    public void setCurrentWorkflowId(String workflowId) {
        this.currentWorkflowId = workflowId;
        Platform.runLater(this::refresh);
    }

    /**
     * 获取当前工作流ID
     */
    public String getCurrentWorkflowId() {
        return currentWorkflowId;
    }

    /**
     * 添加时间轴条目
     */
    public void addTimelineEntry(String type, String label, String details) {
        Platform.runLater(() -> {
            HBox entry = createTimelineEntry(type, label, details, STATUS_RUNNING);
            timelineContent.getChildren().add(entry);
        });
    }

    /**
     * 更新时间轴条目状态
     */
    public void updateTimelineEntryStatus(String label, String status) {
        Platform.runLater(() -> {
            for (var node : timelineContent.getChildren()) {
                if (node instanceof HBox hbox && node.getUserData() != null) {
                    if (node.getUserData().toString().equals(label)) {
                        // 更新状态指示器颜色
                        updateEntryStatusColor(hbox, status);
                        break;
                    }
                }
            }
        });
    }

    /**
     * 刷新面板显示
     */
    public void refresh() {
        Platform.runLater(() -> {
            refreshTimeline();
            updateUndoStack();
        });
    }

    /**
     * 通知有新的可撤销操作
     */
    public void notifyNewUndoableOperation() {
        Platform.runLater(this::updateUndoStack);
    }

    /**
     * 设置撤销完成回调
     */
    public void setOnUndoComplete(Consumer<UndoManager.UndoResult> callback) {
        this.onUndoComplete = callback;
    }

    /**
     * 设置工作流选择回调
     */
    public void setOnWorkflowSelected(Consumer<String> callback) {
        this.onWorkflowSelected = callback;
    }

    /**
     * 清空时间轴
     */
    public void clearTimeline() {
        Platform.runLater(() -> {
            timelineContent.getChildren().clear();
            Label emptyLabel = new Label("暂无执行记录");
            emptyLabel.setStyle("-fx-text-fill: #999;");
            timelineContent.getChildren().add(emptyLabel);
        });
    }

    /**
     * 显示步骤详情
     */
    public void showStepDetail(String title, String sql, int affectedRows, String snapshotId) {
        Platform.runLater(() -> {
            detailTitleLabel.setText(title);
            detailSqlLabel.setText(sql != null && !sql.isEmpty() ? sql : "(无SQL)");
            detailAffectedLabel.setText("影响行数: " + affectedRows);
            detailSnapshotLabel.setText(snapshotId != null ? "快照ID: " + snapshotId : "(无快照)");
        });
    }

    // ==================== 内部方法 ====================

    /**
     * 刷新时间轴显示
     */
    private void refreshTimeline() {
        timelineContent.getChildren().clear();

        if (currentWorkflowId == null) {
            Label emptyLabel = new Label("暂无执行记录");
            emptyLabel.setStyle("-fx-text-fill: #999;");
            timelineContent.getChildren().add(emptyLabel);
            return;
        }

        // 获取时间轴事件
        List<TimelineEvent> timeline = auditLog.getWorkflowTimeline(currentWorkflowId);

        if (timeline.isEmpty()) {
            Label emptyLabel = new Label("工作流尚未开始");
            emptyLabel.setStyle("-fx-text-fill: #999;");
            timelineContent.getChildren().add(emptyLabel);
            return;
        }

        for (TimelineEvent event : timeline) {
            String statusColor = getStatusColorForEventType(event.type);
            HBox entry = createTimelineEntry(
                    event.getTypeIcon(),
                    event.title,
                    event.getFormattedTime() + " - " + event.detail,
                    statusColor
            );
            entry.setUserData(event.title);

            // 点击查看详情
            entry.setOnMouseClicked(e -> showEventDetail(event));
            entry.setStyle("-fx-cursor: hand;");

            timelineContent.getChildren().add(entry);
        }
    }

    /**
     * 创建时间轴条目
     */
    private HBox createTimelineEntry(String icon, String label, String detail, String statusColor) {
        HBox entry = new HBox(8);
        entry.setAlignment(Pos.TOP_LEFT);
        entry.setPadding(new Insets(4));
        entry.setStyle("-fx-background-color: #fafafa; -fx-background-radius: 4;");

        // 状态指示器
        Circle statusIndicator = new Circle(5, Color.web(statusColor));

        // 图标
        Label iconLabel = new Label(icon);
        iconLabel.setMinWidth(24);

        // 内容
        VBox contentBox = new VBox(2);
        Label labelText = new Label(label);
        labelText.setStyle("-fx-font-weight: bold;");

        Label detailText = new Label(detail);
        detailText.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        detailText.setWrapText(true);

        contentBox.getChildren().addAll(labelText, detailText);
        HBox.setHgrow(contentBox, Priority.ALWAYS);

        entry.getChildren().addAll(statusIndicator, iconLabel, contentBox);

        return entry;
    }

    /**
     * 更新撤销栈显示
     */
    private void updateUndoStack() {
        undoStackContent.getChildren().clear();

        List<UndoManager.UndoableOperation> operations;

        if (currentWorkflowId != null) {
            operations = undoManager.getWorkflowUndoableOperations(currentWorkflowId);
        } else {
            operations = undoManager.getUndoableOperations();
        }

        int count = operations.size();
        undoCountLabel.setText(count + " 个可撤销操作");
        undoLastButton.setDisable(count == 0);
        undoAllButton.setDisable(count == 0);

        if (operations.isEmpty()) {
            Label emptyLabel = new Label("没有可撤销的操作");
            emptyLabel.setStyle("-fx-text-fill: #999;");
            undoStackContent.getChildren().add(emptyLabel);
            return;
        }

        // 只显示最近5个
        int showCount = Math.min(5, operations.size());
        for (int i = 0; i < showCount; i++) {
            UndoManager.UndoableOperation op = operations.get(i);
            HBox opEntry = createUndoOperationEntry(op);
            undoStackContent.getChildren().add(opEntry);
        }

        if (operations.size() > 5) {
            Label moreLabel = new Label("... 还有 " + (operations.size() - 5) + " 个操作");
            moreLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11px;");
            undoStackContent.getChildren().add(moreLabel);
        }
    }

    /**
     * 创建撤销操作条目
     */
    private HBox createUndoOperationEntry(UndoManager.UndoableOperation op) {
        HBox entry = new HBox(8);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setPadding(new Insets(4));
        entry.setStyle("-fx-background-color: #fff3e0; -fx-background-radius: 4;");

        // SQL类型标签
        Label typeLabel = new Label(op.getSqlType());
        typeLabel.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white; -fx-padding: 2 6; -fx-background-radius: 3; -fx-font-size: 10px;");

        // SQL摘要
        Label sqlLabel = new Label(op.getShortSql());
        sqlLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        sqlLabel.setMaxWidth(200);
        HBox.setHgrow(sqlLabel, Priority.ALWAYS);

        // 时间
        Label timeLabel = new Label(op.getFormattedTime());
        timeLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 10px;");

        // 单独撤销按钮
        Button undoBtn = new Button("↩");
        undoBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6;");
        undoBtn.setOnAction(e -> performUndoOperation(op.operationId));

        entry.getChildren().addAll(typeLabel, sqlLabel, timeLabel, undoBtn);

        // 点击显示详情
        entry.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                showStepDetail(
                        op.getSqlType() + " 操作",
                        op.sql,
                        op.affectedRows,
                        op.snapshotId
                );
            }
        });
        entry.setStyle(entry.getStyle() + "-fx-cursor: hand;");

        return entry;
    }

    /**
     * 加载最近历史记录
     */
    private void loadRecentHistory() {
        historyListContent.getChildren().clear();

        List<WorkflowLogEntry> recentWorkflows = auditLog.getRecentWorkflows(10);

        if (recentWorkflows.isEmpty()) {
            Label emptyLabel = new Label("暂无历史记录");
            emptyLabel.setStyle("-fx-text-fill: #999;");
            historyListContent.getChildren().add(emptyLabel);
            return;
        }

        for (WorkflowLogEntry workflow : recentWorkflows) {
            HBox entry = createHistoryEntry(workflow);
            historyListContent.getChildren().add(entry);
        }
    }

    /**
     * 创建历史记录条目
     */
    private HBox createHistoryEntry(WorkflowLogEntry workflow) {
        HBox entry = new HBox(8);
        entry.setAlignment(Pos.CENTER_LEFT);
        entry.setPadding(new Insets(4));
        entry.setStyle("-fx-background-color: #eceff1; -fx-background-radius: 4;");

        // 状态指示器
        String statusColor = getStatusColor(workflow.status);
        Circle indicator = new Circle(5, Color.web(statusColor));

        // 工作流类型
        Label typeLabel = new Label(workflow.workflowType != null ? workflow.workflowType : "未知");
        typeLabel.setStyle("-fx-font-weight: bold;");

        // 状态
        Label statusLabel = new Label(workflow.getStatusDisplay());
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + statusColor + ";");

        // 持续时间
        Label durationLabel = new Label(workflow.getFormattedDuration());
        durationLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #999;");

        // 影响行数
        Label rowsLabel = new Label(workflow.totalAffectedRows + " 行");
        rowsLabel.setStyle("-fx-font-size: 10px;");

        VBox infoBox = new VBox(2);
        HBox firstRow = new HBox(8, typeLabel, statusLabel);
        HBox secondRow = new HBox(8, durationLabel, rowsLabel);
        infoBox.getChildren().addAll(firstRow, secondRow);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        entry.getChildren().addAll(indicator, infoBox);

        // 点击选择工作流
        entry.setOnMouseClicked(e -> {
            currentWorkflowId = workflow.workflowId;
            refresh();
            if (onWorkflowSelected != null) {
                onWorkflowSelected.accept(workflow.workflowId);
            }
        });
        entry.setStyle(entry.getStyle() + "-fx-cursor: hand;");

        // 高亮当前工作流
        if (workflow.workflowId.equals(currentWorkflowId)) {
            entry.setStyle(entry.getStyle() + "-fx-border-color: #673AB7; -fx-border-width: 2;");
        }

        return entry;
    }

    /**
     * 显示事件详情
     */
    private void showEventDetail(TimelineEvent event) {
        detailTitleLabel.setText(event.title);
        detailSqlLabel.setText(event.detail);
        detailAffectedLabel.setText("");
        detailSnapshotLabel.setText("时间: " + event.getFormattedTime());
    }

    /**
     * 执行撤销最近操作
     */
    private void performUndoLast() {
        UndoManager.UndoResult result;

        if (currentWorkflowId != null) {
            result = undoManager.undoWorkflowLast(currentWorkflowId);
        } else {
            result = undoManager.undoLast();
        }

        handleUndoResult(result);
    }

    /**
     * 执行撤销全部操作
     */
    private void performUndoAll() {
        if (currentWorkflowId == null) {
            showAlert("警告", "请先选择一个工作流");
            return;
        }

        // 确认对话框
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认撤销");
        confirm.setHeaderText("撤销工作流所有操作");
        confirm.setContentText("这将撤销当前工作流的所有数据修改，确定继续吗？");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                UndoManager.UndoResult result = undoManager.undoWorkflowAll(currentWorkflowId);
                handleUndoResult(result);
            }
        });
    }

    /**
     * 执行单个操作撤销
     */
    private void performUndoOperation(String operationId) {
        UndoManager.UndoResult result = undoManager.undoOperation(operationId);
        handleUndoResult(result);
    }

    /**
     * 处理撤销结果
     */
    private void handleUndoResult(UndoManager.UndoResult result) {
        if (result.success) {
            showAlert("撤销成功", result.message + "\n恢复了 " + result.restoredRows + " 行数据");
        } else {
            showAlert("撤销失败", result.message);
        }

        // 刷新显示
        updateUndoStack();

        // 回调
        if (onUndoComplete != null) {
            onUndoComplete.accept(result);
        }
    }

    /**
     * 获取状态颜色
     */
    private String getStatusColor(WorkflowLogStatus status) {
        return switch (status) {
            case RUNNING -> STATUS_RUNNING;
            case COMPLETED -> STATUS_COMPLETED;
            case FAILED -> STATUS_FAILED;
            case CANCELLED -> STATUS_CANCELLED;
        };
    }

    /**
     * 根据事件类型获取状态颜色
     */
    private String getStatusColorForEventType(EventType type) {
        return switch (type) {
            case WORKFLOW_STARTED, STEP_STARTED -> STATUS_RUNNING;
            case WORKFLOW_COMPLETED, STEP_CONFIRMED -> STATUS_COMPLETED;
            case WORKFLOW_FAILED -> STATUS_FAILED;
            case WORKFLOW_CANCELLED, STEP_SKIPPED -> STATUS_CANCELLED;
            case STEP_RESULT_READY, STEP_CORRECTED -> STATUS_PENDING;
            case STEP_ROLLED_BACK, DATA_ROLLED_BACK -> COLOR_UNDO;
            case SQL_EXECUTED -> STATUS_COMPLETED;
        };
    }

    /**
     * 更新条目状态颜色
     */
    private void updateEntryStatusColor(HBox entry, String status) {
        for (var node : entry.getChildren()) {
            if (node instanceof Circle circle) {
                String color = switch (status.toUpperCase()) {
                    case "RUNNING" -> STATUS_RUNNING;
                    case "COMPLETED", "SUCCESS" -> STATUS_COMPLETED;
                    case "FAILED", "ERROR" -> STATUS_FAILED;
                    case "CANCELLED" -> STATUS_CANCELLED;
                    case "PENDING" -> STATUS_PENDING;
                    default -> STATUS_RUNNING;
                };
                circle.setFill(Color.web(color));
                break;
            }
        }
    }

    /**
     * 显示提示框
     */
    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建精简版面板（只显示时间轴和撤销）
     */
    public static WorkflowHistoryPanel createCompact() {
        WorkflowHistoryPanel panel = new WorkflowHistoryPanel();
        // 隐藏详情和历史面板
        // 由于使用Accordion，不需要特殊处理
        return panel;
    }

    /**
     * 创建指定工作流的面板
     */
    public static WorkflowHistoryPanel createForWorkflow(String workflowId) {
        WorkflowHistoryPanel panel = new WorkflowHistoryPanel();
        panel.setCurrentWorkflowId(workflowId);
        return panel;
    }
}
