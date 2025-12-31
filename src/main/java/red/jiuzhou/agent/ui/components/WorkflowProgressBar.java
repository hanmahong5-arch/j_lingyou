package red.jiuzhou.agent.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import red.jiuzhou.agent.workflow.WorkflowState;
import red.jiuzhou.agent.workflow.WorkflowStep;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流进度条组件
 *
 * <p>可视化显示工作流的执行进度，包括：
 * <ul>
 *   <li>步骤指示器（圆点 + 连接线）</li>
 *   <li>当前步骤高亮</li>
 *   <li>已完成/待执行状态区分</li>
 *   <li>进度百分比显示</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class WorkflowProgressBar extends VBox {

    // 步骤指示器列表
    private final List<StepIndicator> stepIndicators = new ArrayList<>();

    // 进度条
    private final ProgressBar progressBar;

    // 进度文本
    private final Label progressLabel;

    // 当前步骤索引
    private int currentStepIndex = 0;

    // 样式常量
    private static final String COLOR_COMPLETED = "#4CAF50";   // 绿色 - 已完成
    private static final String COLOR_CURRENT = "#2196F3";     // 蓝色 - 当前
    private static final String COLOR_PENDING = "#9E9E9E";     // 灰色 - 待执行
    private static final String COLOR_SKIPPED = "#FF9800";     // 橙色 - 已跳过

    public WorkflowProgressBar() {
        this.setSpacing(8);
        this.setPadding(new Insets(10));
        this.getStyleClass().add("workflow-progress-bar");

        // 进度条
        progressBar = new ProgressBar(0);
        progressBar.setPrefHeight(6);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        // 进度文本
        progressLabel = new Label("准备中...");
        progressLabel.getStyleClass().add("progress-label");

        // 添加进度条和文本
        HBox progressRow = new HBox(10, progressBar, progressLabel);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        this.getChildren().add(progressRow);
    }

    /**
     * 初始化工作流步骤
     *
     * @param steps 步骤列表
     */
    public void initSteps(List<WorkflowStep> steps) {
        // 清空现有指示器
        stepIndicators.clear();

        // 步骤指示器容器
        HBox stepsContainer = new HBox();
        stepsContainer.setAlignment(Pos.CENTER);
        stepsContainer.setSpacing(0);

        for (int i = 0; i < steps.size(); i++) {
            WorkflowStep step = steps.get(i);

            // 创建步骤指示器
            StepIndicator indicator = new StepIndicator(i, step);
            stepIndicators.add(indicator);

            // 添加到容器
            stepsContainer.getChildren().add(indicator);

            // 添加连接线（除了最后一个）
            if (i < steps.size() - 1) {
                stepsContainer.getChildren().add(createConnector());
            }
        }

        // 确保步骤容器在进度条上方
        if (this.getChildren().size() > 1) {
            this.getChildren().set(0, stepsContainer);
        } else {
            this.getChildren().add(0, stepsContainer);
        }

        // 更新初始状态
        updateProgress(0);
    }

    /**
     * 更新进度
     *
     * @param stepIndex 当前步骤索引
     */
    public void updateProgress(int stepIndex) {
        this.currentStepIndex = stepIndex;

        // 更新步骤指示器状态
        for (int i = 0; i < stepIndicators.size(); i++) {
            StepIndicator indicator = stepIndicators.get(i);
            if (i < stepIndex) {
                indicator.setCompleted();
            } else if (i == stepIndex) {
                indicator.setCurrent();
            } else {
                indicator.setPending();
            }
        }

        // 更新进度条
        double progress = stepIndicators.isEmpty() ? 0 :
                (double) stepIndex / stepIndicators.size();
        progressBar.setProgress(progress);

        // 更新进度文本
        if (!stepIndicators.isEmpty() && stepIndex < stepIndicators.size()) {
            WorkflowStep currentStep = stepIndicators.get(stepIndex).getStep();
            progressLabel.setText(String.format("步骤 %d/%d: %s",
                    stepIndex + 1, stepIndicators.size(), currentStep.name()));
        }
    }

    /**
     * 标记步骤为已跳过
     *
     * @param stepIndex 步骤索引
     */
    public void markSkipped(int stepIndex) {
        if (stepIndex >= 0 && stepIndex < stepIndicators.size()) {
            stepIndicators.get(stepIndex).setSkipped();
        }
    }

    /**
     * 标记工作流完成
     */
    public void markCompleted() {
        for (StepIndicator indicator : stepIndicators) {
            indicator.setCompleted();
        }
        progressBar.setProgress(1.0);
        progressLabel.setText("工作流已完成");
    }

    /**
     * 标记工作流取消
     */
    public void markCancelled() {
        progressLabel.setText("工作流已取消");
    }

    /**
     * 标记工作流失败
     */
    public void markFailed() {
        if (currentStepIndex < stepIndicators.size()) {
            stepIndicators.get(currentStepIndex).setFailed();
        }
        progressLabel.setText("工作流执行失败");
    }

    /**
     * 从工作流状态更新
     *
     * @param state 工作流状态
     */
    public void updateFromState(WorkflowState state) {
        if (state == null) return;

        // 初始化步骤（如果需要）
        if (stepIndicators.isEmpty()) {
            initSteps(state.getSteps());
        }

        // 更新进度
        updateProgress(state.getCurrentStepIndex());

        // 更新状态
        switch (state.getStatus()) {
            case COMPLETED -> markCompleted();
            case CANCELLED -> markCancelled();
            case FAILED -> markFailed();
            default -> {
                // RUNNING 或 WAITING 状态，进度已更新
            }
        }
    }

    /**
     * 创建步骤间的连接线
     */
    private HBox createConnector() {
        HBox connector = new HBox();
        connector.setPrefWidth(40);
        connector.setPrefHeight(2);
        connector.setStyle("-fx-background-color: #E0E0E0;");
        connector.setAlignment(Pos.CENTER);
        return connector;
    }

    // ==================== 内部类：步骤指示器 ====================

    /**
     * 单个步骤的可视化指示器
     */
    private static class StepIndicator extends VBox {

        private final Circle circle;
        private final Label label;
        private final WorkflowStep step;

        public StepIndicator(int index, WorkflowStep step) {
            this.step = step;
            this.setAlignment(Pos.CENTER);
            this.setSpacing(4);

            // 圆形指示器
            circle = new Circle(12);
            circle.setStroke(Color.web(COLOR_PENDING));
            circle.setStrokeWidth(2);
            circle.setFill(Color.WHITE);

            // 步骤序号
            Label numberLabel = new Label(String.valueOf(index + 1));
            numberLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");

            // 步骤名称
            label = new Label(step.name());
            label.setStyle("-fx-font-size: 11px;");
            label.setMaxWidth(80);
            label.setWrapText(true);
            label.setAlignment(Pos.CENTER);

            // 工具提示
            Tooltip tooltip = new Tooltip(step.description());
            Tooltip.install(circle, tooltip);
            Tooltip.install(label, tooltip);

            this.getChildren().addAll(circle, label);
        }

        public WorkflowStep getStep() {
            return step;
        }

        public void setCompleted() {
            circle.setStroke(Color.web(COLOR_COMPLETED));
            circle.setFill(Color.web(COLOR_COMPLETED));
            label.setStyle("-fx-font-size: 11px; -fx-text-fill: " + COLOR_COMPLETED + ";");
        }

        public void setCurrent() {
            circle.setStroke(Color.web(COLOR_CURRENT));
            circle.setFill(Color.web(COLOR_CURRENT));
            label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + COLOR_CURRENT + ";");
        }

        public void setPending() {
            circle.setStroke(Color.web(COLOR_PENDING));
            circle.setFill(Color.WHITE);
            label.setStyle("-fx-font-size: 11px; -fx-text-fill: " + COLOR_PENDING + ";");
        }

        public void setSkipped() {
            circle.setStroke(Color.web(COLOR_SKIPPED));
            circle.setFill(Color.web(COLOR_SKIPPED));
            label.setStyle("-fx-font-size: 11px; -fx-text-fill: " + COLOR_SKIPPED + ";");
        }

        public void setFailed() {
            circle.setStroke(Color.web("#F44336"));
            circle.setFill(Color.web("#F44336"));
            label.setStyle("-fx-font-size: 11px; -fx-text-fill: #F44336;");
        }
    }
}
