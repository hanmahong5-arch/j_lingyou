package red.jiuzhou.agent.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import red.jiuzhou.agent.workflow.WorkflowStep;
import red.jiuzhou.agent.workflow.WorkflowStepResult;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 步骤确认对话框
 *
 * <p>设计师体验核心组件，提供：
 * <ul>
 *   <li>步骤结果展示</li>
 *   <li>确认/修正/跳过/回退操作</li>
 *   <li>用户修正输入</li>
 *   <li>进度可视化</li>
 * </ul>
 *
 * <p>对话框结构：
 * <pre>
 * ┌─────────────────────────────────────────┐
 * │  [图标] 步骤名称                         │
 * ├─────────────────────────────────────────┤
 * │  说明: 步骤描述...                       │
 * │                                         │
 * │  [结果展示区域]                          │
 * │                                         │
 * ├─────────────────────────────────────────┤
 * │  ▼ 修正AI理解（可选）                   │
 * │  [补充说明输入框]                        │
 * ├─────────────────────────────────────────┤
 * │ [取消] ... [上一步] [跳过] [确认并继续]  │
 * └─────────────────────────────────────────┘
 * </pre>
 *
 * @author Claude
 * @version 1.0
 */
public class StepConfirmationDialog extends Dialog<StepConfirmationDialog.UserAction> {

    /**
     * 用户操作结果
     */
    public record UserAction(
            ActionType type,
            String correction
    ) {
        public enum ActionType {
            CONFIRM,        // 确认并继续
            MODIFY,         // 修正后重试
            SKIP,           // 跳过
            PREVIOUS,       // 回退上一步
            CANCEL          // 取消工作流
        }
    }

    // UI组件
    private final VBox contentArea;
    private final TextArea correctionInput;
    private final TitledPane correctionPane;
    private final Label summaryLabel;
    private final VBox resultContainer;

    // 当前步骤信息
    private WorkflowStep currentStep;
    private WorkflowStepResult currentResult;

    // 按钮
    private final Button confirmButton;
    private final Button modifyButton;
    private final Button skipButton;
    private final Button previousButton;

    public StepConfirmationDialog() {
        this.setTitle("步骤确认");
        this.setResizable(true);

        // 对话框面板
        DialogPane dialogPane = this.getDialogPane();
        dialogPane.setPrefWidth(600);
        dialogPane.setPrefHeight(500);
        dialogPane.getStyleClass().add("step-confirmation-dialog");

        // 主内容区域
        contentArea = new VBox(12);
        contentArea.setPadding(new Insets(16));

        // 摘要标签
        summaryLabel = new Label();
        summaryLabel.setWrapText(true);
        summaryLabel.getStyleClass().add("summary-label");

        // 结果展示容器
        resultContainer = new VBox(8);
        resultContainer.getStyleClass().add("result-container");
        ScrollPane resultScroll = new ScrollPane(resultContainer);
        resultScroll.setFitToWidth(true);
        resultScroll.setPrefHeight(200);
        VBox.setVgrow(resultScroll, Priority.ALWAYS);

        // 修正输入区域（可折叠）
        correctionInput = new TextArea();
        correctionInput.setPromptText("如果AI理解有误，请在这里补充说明...");
        correctionInput.setPrefRowCount(3);
        correctionInput.setWrapText(true);

        correctionPane = new TitledPane("修正AI理解（可选）", correctionInput);
        correctionPane.setExpanded(false);
        correctionPane.setAnimated(true);

        // 组装内容
        contentArea.getChildren().addAll(summaryLabel, resultScroll, correctionPane);
        dialogPane.setContent(contentArea);

        // 创建按钮
        confirmButton = new Button("确认并继续");
        confirmButton.getStyleClass().add("confirm-button");
        confirmButton.setDefaultButton(true);

        modifyButton = new Button("修正后重试");
        modifyButton.getStyleClass().add("modify-button");

        skipButton = new Button("跳过");
        skipButton.getStyleClass().add("skip-button");

        previousButton = new Button("上一步");
        previousButton.getStyleClass().add("previous-button");

        Button cancelButton = new Button("取消");
        cancelButton.setCancelButton(true);

        // 按钮布局
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(10, 0, 0, 0));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        buttonBar.getChildren().addAll(
                cancelButton, spacer, previousButton, skipButton, modifyButton, confirmButton
        );

        // 添加按钮到对话框
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        dialogPane.lookupButton(ButtonType.CLOSE).setVisible(false);

        // 在内容区域底部添加按钮
        contentArea.getChildren().add(buttonBar);

        // 按钮事件
        confirmButton.setOnAction(e -> {
            setResult(new UserAction(UserAction.ActionType.CONFIRM, null));
            close();
        });

        modifyButton.setOnAction(e -> {
            String correction = correctionInput.getText().trim();
            if (correction.isEmpty()) {
                // 提示用户输入修正内容
                correctionPane.setExpanded(true);
                correctionInput.requestFocus();
                return;
            }
            setResult(new UserAction(UserAction.ActionType.MODIFY, correction));
            close();
        });

        skipButton.setOnAction(e -> {
            setResult(new UserAction(UserAction.ActionType.SKIP, null));
            close();
        });

        previousButton.setOnAction(e -> {
            setResult(new UserAction(UserAction.ActionType.PREVIOUS, null));
            close();
        });

        cancelButton.setOnAction(e -> {
            setResult(new UserAction(UserAction.ActionType.CANCEL, null));
            close();
        });

        // 默认结果转换器
        this.setResultConverter(dialogButton -> null);
    }

    /**
     * 显示步骤结果并等待用户操作
     *
     * @param step 当前步骤
     * @param result 步骤执行结果
     * @param hasPrevious 是否有上一步
     * @return 用户操作
     */
    public Optional<UserAction> showAndWait(WorkflowStep step, WorkflowStepResult result, boolean hasPrevious) {
        this.currentStep = step;
        this.currentResult = result;

        // 更新标题
        this.setTitle(step.getDisplayText());
        this.setHeaderText(step.description());

        // 更新摘要
        if (result.getSummary() != null) {
            summaryLabel.setText(result.getSummary());
            summaryLabel.setVisible(true);
        } else {
            summaryLabel.setVisible(false);
        }

        // 更新结果展示
        updateResultDisplay(result);

        // 更新按钮状态
        skipButton.setDisable(!step.skippable());
        previousButton.setDisable(!hasPrevious);

        // 清空修正输入
        correctionInput.clear();
        correctionPane.setExpanded(false);

        // 显示对话框
        return super.showAndWait();
    }

    /**
     * 更新结果展示区域
     */
    private void updateResultDisplay(WorkflowStepResult result) {
        resultContainer.getChildren().clear();

        // 显示AI解析的意图
        if (result.getParsedIntent() != null) {
            VBox intentBox = createInfoBox("AI理解的意图", result.getParsedIntent(), "#E3F2FD");
            resultContainer.getChildren().add(intentBox);
        }

        // 显示生成的SQL
        if (result.getGeneratedSql() != null) {
            VBox sqlBox = createCodeBox("生成的SQL", result.getGeneratedSql());
            resultContainer.getChildren().add(sqlBox);
        }

        // 显示解析的条件
        if (result.getParsedConditions() != null && !result.getParsedConditions().isEmpty()) {
            VBox conditionsBox = createConditionsBox(result.getParsedConditions());
            resultContainer.getChildren().add(conditionsBox);
        }

        // 显示查询结果统计
        if (result.hasQueryData()) {
            Label statsLabel = new Label(String.format("查询结果: %d 条记录", result.getTotalRows()));
            statsLabel.getStyleClass().add("stats-label");
            resultContainer.getChildren().add(statsLabel);
        }

        // 显示对比数据统计
        if (result.hasComparisonData()) {
            Label compareLabel = new Label(String.format(
                    "修改预览: %d 条记录将被修改",
                    result.getBeforeData().size()
            ));
            compareLabel.getStyleClass().add("stats-label");
            resultContainer.getChildren().add(compareLabel);

            if (result.getModifiedColumns() != null) {
                Label columnsLabel = new Label("修改字段: " + String.join(", ", result.getModifiedColumns()));
                columnsLabel.getStyleClass().add("columns-label");
                resultContainer.getChildren().add(columnsLabel);
            }
        }

        // 显示消息
        if (result.getMessage() != null) {
            Label messageLabel = new Label(result.getMessage());
            messageLabel.setWrapText(true);
            messageLabel.getStyleClass().add("message-label");
            resultContainer.getChildren().add(messageLabel);
        }
    }

    /**
     * 创建信息框
     */
    private VBox createInfoBox(String title, String content, String bgColor) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 4;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        Label contentLabel = new Label(content);
        contentLabel.setWrapText(true);
        contentLabel.setStyle("-fx-font-size: 13px;");

        box.getChildren().addAll(titleLabel, contentLabel);
        return box;
    }

    /**
     * 创建代码框
     */
    private VBox createCodeBox(String title, String code) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: #F5F5F5; -fx-background-radius: 4;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        TextArea codeArea = new TextArea(code);
        codeArea.setEditable(false);
        codeArea.setWrapText(true);
        codeArea.setPrefRowCount(3);
        codeArea.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 12px;");

        box.getChildren().addAll(titleLabel, codeArea);
        return box;
    }

    /**
     * 创建条件展示框
     */
    private VBox createConditionsBox(java.util.Map<String, Object> conditions) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: #FFF3E0; -fx-background-radius: 4;");

        Label titleLabel = new Label("解析的条件");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        VBox conditionsList = new VBox(2);
        for (var entry : conditions.entrySet()) {
            HBox row = new HBox(8);
            Label keyLabel = new Label(entry.getKey() + ":");
            keyLabel.setStyle("-fx-font-weight: bold;");
            Label valueLabel = new Label(String.valueOf(entry.getValue()));
            row.getChildren().addAll(keyLabel, valueLabel);
            conditionsList.getChildren().add(row);
        }

        box.getChildren().addAll(titleLabel, conditionsList);
        return box;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 显示步骤确认对话框
     *
     * @param step 当前步骤
     * @param result 步骤执行结果
     * @param hasPrevious 是否有上一步
     * @return 用户操作
     */
    public static Optional<UserAction> show(WorkflowStep step, WorkflowStepResult result, boolean hasPrevious) {
        StepConfirmationDialog dialog = new StepConfirmationDialog();
        return dialog.showAndWait(step, result, hasPrevious);
    }

    /**
     * 显示简单确认对话框
     *
     * @param title 标题
     * @param message 消息
     * @return 是否确认
     */
    public static boolean showSimpleConfirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        ButtonType confirmButton = new ButtonType("确认", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(confirmButton, cancelButton);

        return alert.showAndWait().orElse(cancelButton) == confirmButton;
    }

    /**
     * 显示带输入的对话框
     *
     * @param title 标题
     * @param message 消息
     * @param defaultValue 默认值
     * @return 用户输入（如果取消则为空）
     */
    public static Optional<String> showInput(String title, String message, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(message);
        return dialog.showAndWait();
    }
}
