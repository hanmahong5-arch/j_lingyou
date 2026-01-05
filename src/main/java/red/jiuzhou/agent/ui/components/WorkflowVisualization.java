package red.jiuzhou.agent.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 工作流可视化组件
 *
 * <p>将AI的多步骤处理过程可视化，让设计师清楚看到每一步在做什么，
 * 并可以在关键步骤介入。
 *
 * <p>7种工作流步骤类型：
 * <ul>
 *   <li>UNDERSTAND - 意图理解</li>
 *   <li>FILTER - 数据过滤</li>
 *   <li>PREVIEW - 预览确认</li>
 *   <li>COMPARE - 数据对比</li>
 *   <li>CONFIRM - 确认执行</li>
 *   <li>EXECUTE - 执行操作</li>
 *   <li>VALIDATE - 验证结果</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class WorkflowVisualization extends VBox {

    // ==================== 步骤类型枚举 ====================

    public enum StepType {
        UNDERSTAND("意图理解", "🧠", "#2196F3"),
        FILTER("数据过滤", "🔍", "#9C27B0"),
        PREVIEW("预览确认", "👁", "#FF9800"),
        COMPARE("数据对比", "⚖", "#00BCD4"),
        CONFIRM("确认执行", "✅", "#4CAF50"),
        EXECUTE("执行操作", "⚡", "#F44336"),
        VALIDATE("验证结果", "🔬", "#607D8B");

        private final String displayName;
        private final String icon;
        private final String color;

        StepType(String displayName, String icon, String color) {
            this.displayName = displayName;
            this.icon = icon;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getIcon() { return icon; }
        public String getColor() { return color; }
    }

    // ==================== 步骤状态枚举 ====================

    public enum StepStatus {
        PENDING("待执行", "#9E9E9E"),
        RUNNING("执行中", "#2196F3"),
        COMPLETED("已完成", "#4CAF50"),
        FAILED("失败", "#F44336"),
        SKIPPED("已跳过", "#FF9800");

        private final String displayName;
        private final String color;

        StepStatus(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }

    // ==================== 工作流步骤数据结构 ====================

    public static class WorkflowStep {
        private final int index;
        private final StepType type;
        private final String title;
        private final String description;
        private StepStatus status = StepStatus.PENDING;
        private String input;
        private String output;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String errorMessage;

        public WorkflowStep(int index, StepType type, String title, String description) {
            this.index = index;
            this.type = type;
            this.title = title;
            this.description = description;
        }

        // Getters and setters
        public int getIndex() { return index; }
        public StepType getType() { return type; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public StepStatus getStatus() { return status; }
        public void setStatus(StepStatus status) { this.status = status; }
        public String getInput() { return input; }
        public void setInput(String input) { this.input = input; }
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public String getDuration() {
            if (startTime == null || endTime == null) return "--";
            long millis = java.time.Duration.between(startTime, endTime).toMillis();
            if (millis < 1000) return millis + "ms";
            return String.format("%.1fs", millis / 1000.0);
        }
    }

    // ==================== 工作流结果 ====================

    public record WorkflowResult(
        boolean success,
        String message,
        List<WorkflowStep> steps,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
        public String getTotalDuration() {
            if (startTime == null || endTime == null) return "--";
            long millis = java.time.Duration.between(startTime, endTime).toMillis();
            if (millis < 1000) return millis + "ms";
            return String.format("%.1fs", millis / 1000.0);
        }
    }

    // ==================== UI组件 ====================

    private final HBox progressBar;
    private final VBox stepDetailBox;
    private final VBox historyBox;
    private final Label currentStepLabel;
    private final Label workflowTypeLabel;

    // ==================== 状态 ====================

    private String workflowType = "unknown";
    private final List<WorkflowStep> steps = new ArrayList<>();
    private int currentStepIndex = -1;
    private final List<HistoryEntry> historyEntries = new ArrayList<>();

    // ==================== 回调 ====================

    private BiConsumer<WorkflowStep, String> onStepAction;
    private Consumer<WorkflowResult> onWorkflowComplete;

    // ==================== 历史记录条目 ====================

    private record HistoryEntry(
        LocalDateTime time,
        String stepTitle,
        StepStatus status,
        String message,
        String duration
    ) {}

    // ==================== 日期格式 ====================

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ==================== 构造器 ====================

    public WorkflowVisualization() {
        this.setSpacing(12);
        this.setPadding(new Insets(12));
        this.getStyleClass().add("workflow-visualization");
        this.setStyle("-fx-background-color: #FAFAFA;");

        // 标题区域
        Label titleLabel = new Label("工作流进度");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        workflowTypeLabel = new Label("");
        workflowTypeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        HBox titleBox = new HBox(8, new Label("🔄"), titleLabel, workflowTypeLabel);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        // ==================== 进度条区域 ====================
        progressBar = new HBox(0);
        progressBar.setAlignment(Pos.CENTER);
        progressBar.setPadding(new Insets(16, 8, 16, 8));
        progressBar.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                            "-fx-border-color: #E0E0E0; -fx-border-radius: 8;");

        // ==================== 当前步骤详情区域 ====================
        currentStepLabel = new Label("📋 当前步骤: 等待开始");
        currentStepLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        stepDetailBox = new VBox(8);
        stepDetailBox.setPadding(new Insets(12));
        stepDetailBox.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                              "-fx-border-color: #E0E0E0; -fx-border-radius: 8;");

        VBox currentStepSection = new VBox(8, currentStepLabel, stepDetailBox);

        // ==================== 步骤历史区域 ====================
        Label historyLabel = new Label("📊 步骤历史");
        historyLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        historyBox = new VBox(4);
        historyBox.setPadding(new Insets(8, 0, 0, 0));

        ScrollPane historyScroll = new ScrollPane(historyBox);
        historyScroll.setFitToWidth(true);
        historyScroll.setPrefHeight(120);
        historyScroll.setStyle("-fx-background-color: transparent;");

        VBox historySection = new VBox(8, historyLabel, historyScroll);

        // ==================== 组装面板 ====================
        VBox.setVgrow(currentStepSection, Priority.ALWAYS);

        this.getChildren().addAll(titleBox, progressBar, currentStepSection, historySection);

        // 初始状态
        showEmptyState();
    }

    // ==================== 公共方法 ====================

    /**
     * 设置工作流类型
     */
    public void setWorkflowType(String type) {
        this.workflowType = type;
        Platform.runLater(() -> {
            String displayType = switch (type) {
                case "query" -> "查询工作流";
                case "modify" -> "修改工作流";
                case "analyze" -> "分析工作流";
                case "generate" -> "生成工作流";
                default -> type;
            };
            workflowTypeLabel.setText("(" + displayType + ")");
        });
    }

    /**
     * 设置工作流步骤
     */
    public void setSteps(List<WorkflowStep> newSteps) {
        this.steps.clear();
        this.steps.addAll(newSteps);
        this.currentStepIndex = -1;
        this.historyEntries.clear();

        Platform.runLater(() -> {
            buildProgressBar();
            historyBox.getChildren().clear();
            showWaitingState();
        });
    }

    /**
     * 更新步骤状态
     */
    public void updateStepStatus(int index, StepStatus status) {
        if (index < 0 || index >= steps.size()) return;

        WorkflowStep step = steps.get(index);
        step.setStatus(status);

        if (status == StepStatus.RUNNING) {
            step.setStartTime(LocalDateTime.now());
            currentStepIndex = index;
        } else if (status == StepStatus.COMPLETED || status == StepStatus.FAILED) {
            step.setEndTime(LocalDateTime.now());
        }

        Platform.runLater(() -> {
            updateProgressBarStep(index, status);
            if (status == StepStatus.RUNNING) {
                showStepDetail(step);
            }
        });
    }

    /**
     * 显示步骤详情
     */
    public void showStepDetail(WorkflowStep step) {
        Platform.runLater(() -> {
            currentStepLabel.setText("📋 当前步骤: " + step.getType().getDisplayName());
            stepDetailBox.getChildren().clear();

            // 步骤描述
            VBox contentBox = new VBox(6);

            Label statusLabel = new Label(step.getType().getIcon() + " " + getStatusText(step.getStatus()));
            statusLabel.setStyle("-fx-font-size: 12px;");

            contentBox.getChildren().add(statusLabel);

            // 输入信息
            if (step.getInput() != null && !step.getInput().isEmpty()) {
                Label inputLabel = new Label("输入: " + step.getInput());
                inputLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
                inputLabel.setWrapText(true);
                contentBox.getChildren().add(inputLabel);
            }

            // 输出信息
            if (step.getOutput() != null && !step.getOutput().isEmpty()) {
                Label outputLabel = new Label("理解: " + step.getOutput());
                outputLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
                outputLabel.setWrapText(true);
                contentBox.getChildren().add(outputLabel);
            }

            // 错误信息
            if (step.getErrorMessage() != null && !step.getErrorMessage().isEmpty()) {
                Label errorLabel = new Label("错误: " + step.getErrorMessage());
                errorLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #F44336;");
                errorLabel.setWrapText(true);
                contentBox.getChildren().add(errorLabel);
            }

            // 操作按钮
            HBox buttonBox = createStepActionButtons(step);
            contentBox.getChildren().add(buttonBox);

            stepDetailBox.getChildren().add(contentBox);
        });
    }

    /**
     * 添加历史记录条目
     *
     * @param step 工作流步骤，可为null（用于工作流级别事件）
     * @param message 消息内容
     */
    public void addHistoryEntry(WorkflowStep step, String message) {
        HistoryEntry entry;
        if (step != null) {
            entry = new HistoryEntry(
                LocalDateTime.now(),
                step.getTitle(),
                step.getStatus(),
                message,
                step.getDuration()
            );
        } else {
            // 工作流级别的事件（完成、取消、失败等）
            entry = new HistoryEntry(
                LocalDateTime.now(),
                "工作流",
                StepStatus.COMPLETED,
                message,
                "--"
            );
        }
        historyEntries.add(entry);

        Platform.runLater(() -> {
            HBox row = createHistoryRow(entry);
            historyBox.getChildren().add(0, row); // 新的在顶部
        });
    }

    /**
     * 设置步骤操作回调
     */
    public void setOnStepAction(BiConsumer<WorkflowStep, String> callback) {
        this.onStepAction = callback;
    }

    /**
     * 设置工作流完成回调
     */
    public void setOnWorkflowComplete(Consumer<WorkflowResult> callback) {
        this.onWorkflowComplete = callback;
    }

    /**
     * 标记工作流完成
     */
    public void markWorkflowComplete(boolean success, String message) {
        Platform.runLater(() -> {
            if (success) {
                currentStepLabel.setText("✅ 工作流完成");
                stepDetailBox.setStyle(stepDetailBox.getStyle() + "-fx-border-color: #4CAF50;");
            } else {
                currentStepLabel.setText("❌ 工作流失败");
                stepDetailBox.setStyle(stepDetailBox.getStyle() + "-fx-border-color: #F44336;");
            }

            stepDetailBox.getChildren().clear();
            Label resultLabel = new Label(message);
            resultLabel.setWrapText(true);
            stepDetailBox.getChildren().add(resultLabel);
        });

        if (onWorkflowComplete != null) {
            WorkflowResult result = new WorkflowResult(
                success, message, new ArrayList<>(steps), null, LocalDateTime.now()
            );
            onWorkflowComplete.accept(result);
        }
    }

    /**
     * 重置可视化
     */
    public void reset() {
        steps.clear();
        historyEntries.clear();
        currentStepIndex = -1;

        Platform.runLater(() -> {
            progressBar.getChildren().clear();
            stepDetailBox.getChildren().clear();
            historyBox.getChildren().clear();
            showEmptyState();
        });
    }

    /**
     * 获取当前步骤
     */
    public WorkflowStep getCurrentStep() {
        if (currentStepIndex >= 0 && currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }

    // ==================== 内部方法 ====================

    /**
     * 构建进度条
     */
    private void buildProgressBar() {
        progressBar.getChildren().clear();

        for (int i = 0; i < steps.size(); i++) {
            WorkflowStep step = steps.get(i);

            // 步骤节点
            VBox stepNode = createStepNode(step, i);
            progressBar.getChildren().add(stepNode);

            // 连接线（非最后一个）
            if (i < steps.size() - 1) {
                Region connector = createConnector();
                progressBar.getChildren().add(connector);
            }
        }
    }

    /**
     * 创建步骤节点
     */
    private VBox createStepNode(WorkflowStep step, int index) {
        VBox node = new VBox(4);
        node.setAlignment(Pos.CENTER);
        node.setMinWidth(60);

        // 圆形指示器
        Circle indicator = new Circle(16);
        indicator.setFill(Color.web(step.getStatus().getColor()));
        indicator.setStroke(Color.web("#E0E0E0"));
        indicator.setStrokeWidth(2);

        // 步骤序号
        Label numberLabel = new Label(String.valueOf(index + 1));
        numberLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");

        StackPane indicatorPane = new StackPane(indicator, numberLabel);

        // 步骤名称
        Label nameLabel = new Label(step.getType().getDisplayName());
        nameLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        // 状态图标
        Label statusIcon = new Label(getStatusIcon(step.getStatus()));
        statusIcon.setStyle("-fx-font-size: 12px;");

        node.getChildren().addAll(indicatorPane, nameLabel, statusIcon);
        node.setUserData(index);

        // 点击查看详情
        node.setOnMouseClicked(e -> showStepDetail(step));
        node.setStyle("-fx-cursor: hand;");

        return node;
    }

    /**
     * 创建连接线
     */
    private Region createConnector() {
        Region connector = new Region();
        connector.setMinWidth(30);
        connector.setMaxHeight(2);
        connector.setStyle("-fx-background-color: #E0E0E0;");
        HBox.setHgrow(connector, Priority.ALWAYS);
        return connector;
    }

    /**
     * 更新进度条中的步骤状态
     */
    private void updateProgressBarStep(int index, StepStatus status) {
        int nodeIndex = index * 2; // 考虑连接线
        if (nodeIndex < progressBar.getChildren().size()) {
            var node = progressBar.getChildren().get(nodeIndex);
            if (node instanceof VBox vbox && !vbox.getChildren().isEmpty()) {
                var indicatorPane = vbox.getChildren().get(0);
                if (indicatorPane instanceof StackPane stackPane && !stackPane.getChildren().isEmpty()) {
                    var circle = stackPane.getChildren().get(0);
                    if (circle instanceof Circle c) {
                        c.setFill(Color.web(status.getColor()));
                    }
                }
                // 更新状态图标
                if (vbox.getChildren().size() > 2) {
                    var statusIcon = vbox.getChildren().get(2);
                    if (statusIcon instanceof Label label) {
                        label.setText(getStatusIcon(status));
                    }
                }
            }
        }

        // 更新连接线颜色（已完成的步骤后面的连接线变绿）
        if (status == StepStatus.COMPLETED && nodeIndex + 1 < progressBar.getChildren().size()) {
            var connector = progressBar.getChildren().get(nodeIndex + 1);
            if (connector instanceof Region region) {
                region.setStyle("-fx-background-color: #4CAF50;");
            }
        }
    }

    /**
     * 创建步骤操作按钮
     */
    private HBox createStepActionButtons(WorkflowStep step) {
        HBox buttonBox = new HBox(8);
        buttonBox.setPadding(new Insets(8, 0, 0, 0));

        Button detailBtn = new Button("查看详情");
        detailBtn.setStyle("-fx-font-size: 11px;");
        detailBtn.setOnAction(e -> {
            if (onStepAction != null) {
                onStepAction.accept(step, "detail");
            }
        });

        buttonBox.getChildren().add(detailBtn);

        // 根据步骤类型添加不同的操作按钮
        switch (step.getType()) {
            case UNDERSTAND -> {
                Button supplementBtn = new Button("补充说明");
                supplementBtn.setStyle("-fx-font-size: 11px;");
                supplementBtn.setOnAction(e -> {
                    if (onStepAction != null) {
                        onStepAction.accept(step, "supplement");
                    }
                });
                buttonBox.getChildren().add(supplementBtn);
            }
            case PREVIEW, COMPARE -> {
                Button confirmBtn = new Button("确认");
                confirmBtn.setStyle("-fx-font-size: 11px; -fx-background-color: #4CAF50; -fx-text-fill: white;");
                confirmBtn.setOnAction(e -> {
                    if (onStepAction != null) {
                        onStepAction.accept(step, "confirm");
                    }
                });

                Button cancelBtn = new Button("取消");
                cancelBtn.setStyle("-fx-font-size: 11px;");
                cancelBtn.setOnAction(e -> {
                    if (onStepAction != null) {
                        onStepAction.accept(step, "cancel");
                    }
                });

                buttonBox.getChildren().addAll(confirmBtn, cancelBtn);
            }
            case EXECUTE -> {
                Button skipBtn = new Button("跳过");
                skipBtn.setStyle("-fx-font-size: 11px; -fx-text-fill: #FF9800;");
                skipBtn.setOnAction(e -> {
                    if (onStepAction != null) {
                        onStepAction.accept(step, "skip");
                    }
                });
                buttonBox.getChildren().add(skipBtn);
            }
            default -> {}
        }

        // 重新生成按钮（适用于所有步骤）
        Button regenerateBtn = new Button("重新生成");
        regenerateBtn.setStyle("-fx-font-size: 11px; -fx-text-fill: #2196F3;");
        regenerateBtn.setOnAction(e -> {
            if (onStepAction != null) {
                onStepAction.accept(step, "regenerate");
            }
        });
        buttonBox.getChildren().add(regenerateBtn);

        return buttonBox;
    }

    /**
     * 创建历史记录行
     */
    private HBox createHistoryRow(HistoryEntry entry) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4));

        // 时间
        Label timeLabel = new Label(entry.time().format(TIME_FORMAT));
        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #999;");
        timeLabel.setMinWidth(60);

        // 步骤名称
        Label nameLabel = new Label(entry.stepTitle());
        nameLabel.setStyle("-fx-font-size: 11px;");

        // 状态图标
        Label statusLabel = new Label(getStatusIcon(entry.status()));

        // 持续时间
        Label durationLabel = new Label("(" + entry.duration() + ")");
        durationLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");

        row.getChildren().addAll(timeLabel, nameLabel, statusLabel, durationLabel);

        // 添加消息提示
        if (entry.message() != null && !entry.message().isEmpty()) {
            Tooltip tooltip = new Tooltip(entry.message());
            Tooltip.install(row, tooltip);
        }

        return row;
    }

    /**
     * 获取状态图标
     */
    private String getStatusIcon(StepStatus status) {
        return switch (status) {
            case PENDING -> "⏹";
            case RUNNING -> "🔄";
            case COMPLETED -> "✅";
            case FAILED -> "❌";
            case SKIPPED -> "⏭";
        };
    }

    /**
     * 获取状态文本
     */
    private String getStatusText(StepStatus status) {
        return switch (status) {
            case PENDING -> "等待执行...";
            case RUNNING -> "正在执行...";
            case COMPLETED -> "执行完成";
            case FAILED -> "执行失败";
            case SKIPPED -> "已跳过";
        };
    }

    /**
     * 显示空状态
     */
    private void showEmptyState() {
        progressBar.getChildren().clear();
        Label emptyLabel = new Label("等待工作流开始...");
        emptyLabel.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
        progressBar.getChildren().add(emptyLabel);

        stepDetailBox.getChildren().clear();
        Label detailEmpty = new Label("选择上方的步骤查看详情");
        detailEmpty.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
        stepDetailBox.getChildren().add(detailEmpty);
    }

    /**
     * 显示等待状态
     */
    private void showWaitingState() {
        currentStepLabel.setText("📋 当前步骤: 等待开始");
        stepDetailBox.getChildren().clear();
        Label waitLabel = new Label("工作流已准备就绪，等待执行...");
        waitLabel.setStyle("-fx-text-fill: #666;");
        stepDetailBox.getChildren().add(waitLabel);
    }

    /**
     * 获取所有步骤
     */
    public List<WorkflowStep> getSteps() {
        return new ArrayList<>(steps);
    }

    /**
     * 获取历史记录
     */
    public List<HistoryEntry> getHistory() {
        return new ArrayList<>(historyEntries);
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建查询工作流的默认步骤
     */
    public static List<WorkflowStep> createQueryWorkflowSteps() {
        return List.of(
            new WorkflowStep(0, StepType.UNDERSTAND, "理解意图", "分析用户的查询意图"),
            new WorkflowStep(1, StepType.FILTER, "生成SQL", "根据意图生成SQL查询"),
            new WorkflowStep(2, StepType.PREVIEW, "预览结果", "预览查询结果"),
            new WorkflowStep(3, StepType.EXECUTE, "执行查询", "执行SQL查询")
        );
    }

    /**
     * 创建修改工作流的默认步骤
     */
    public static List<WorkflowStep> createModifyWorkflowSteps() {
        return List.of(
            new WorkflowStep(0, StepType.UNDERSTAND, "理解意图", "分析用户的修改意图"),
            new WorkflowStep(1, StepType.FILTER, "生成SQL", "根据意图生成修改SQL"),
            new WorkflowStep(2, StepType.PREVIEW, "预览变更", "预览将要修改的数据"),
            new WorkflowStep(3, StepType.COMPARE, "对比确认", "对比修改前后的数据"),
            new WorkflowStep(4, StepType.CONFIRM, "确认执行", "用户确认是否执行"),
            new WorkflowStep(5, StepType.EXECUTE, "执行修改", "执行数据修改"),
            new WorkflowStep(6, StepType.VALIDATE, "验证结果", "验证修改结果")
        );
    }

    /**
     * 创建分析工作流的默认步骤
     */
    public static List<WorkflowStep> createAnalyzeWorkflowSteps() {
        return List.of(
            new WorkflowStep(0, StepType.UNDERSTAND, "理解意图", "分析用户的分析需求"),
            new WorkflowStep(1, StepType.FILTER, "收集数据", "收集相关数据"),
            new WorkflowStep(2, StepType.EXECUTE, "执行分析", "执行数据分析"),
            new WorkflowStep(3, StepType.PREVIEW, "展示结果", "展示分析结果")
        );
    }
}
