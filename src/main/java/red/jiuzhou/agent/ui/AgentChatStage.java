package red.jiuzhou.agent.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.agent.context.DesignContext;
import red.jiuzhou.agent.core.AgentMessage;
import red.jiuzhou.langchain.LangChainGameDataAgent;
import red.jiuzhou.util.SpringContextHolder;
import red.jiuzhou.agent.tools.SqlExecutionTool;
import red.jiuzhou.agent.ui.components.*;
import red.jiuzhou.agent.workflow.*;
import red.jiuzhou.util.DatabaseUtil;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI Agent 对话窗口
 *
 * 提供聊天式交互界面
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class AgentChatStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(AgentChatStage.class);

    private LangChainGameDataAgent agent;
    private JdbcTemplate jdbcTemplate;
    private SqlExecutionTool sqlTool;

    // UI组件
    private VBox chatContainer;
    private ScrollPane chatScrollPane;
    private TextArea inputArea;
    private Button sendButton;
    private ComboBox<String> modelSelector;
    private Label statusLabel;
    private ProgressIndicator loadingIndicator;
    private ToggleButton sqlModeToggle;
    private TabPane resultTabPane;

    // 协作式工作流组件
    private CollaborativeWorkflowEngine workflowEngine;
    private WorkflowProgressBar workflowProgressBar;
    private ContextTransparencyPanel contextPanel;
    private AiCapabilityGuide capabilityGuide;
    private ToggleButton workflowModeToggle;

    // 追溯和撤销组件
    private WorkflowHistoryPanel historyPanel;
    private Button undoButton;

    // 设计师体验增强组件
    private DesignerWorkbenchPanel workbenchPanel;
    private WorkflowVisualization workflowVisualization;
    private DomainKnowledgeCards knowledgeCards;

    // 样式常量
    private static final String USER_BG = "#E3F2FD";
    private static final String ASSISTANT_BG = "#F5F5F5";
    private static final String SYSTEM_BG = "#FFF3E0";
    private static final String ERROR_BG = "#FFEBEE";
    private static final String PENDING_BG = "#FFF8E1";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 无参构造函数，使用DatabaseUtil获取JdbcTemplate
     */
    public AgentChatStage() {
        this(DatabaseUtil.getJdbcTemplate(null));
    }

    public AgentChatStage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        log.info("AgentChatStage 构造函数开始");
        initUI();
        log.info("UI 初始化完成");
        initAgent();
        log.info("Agent 初始化完成，窗口准备就绪");
    }

    private void initUI() {
        setTitle("AI 游戏数据助手 (协作增强版)");
        setWidth(1300);
        setHeight(800);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: white;");

        // 顶部：工具栏 + 工作流进度条
        VBox topContainer = new VBox();
        topContainer.getChildren().add(createToolbar());

        // 工作流进度条（默认隐藏）
        workflowProgressBar = new WorkflowProgressBar();
        workflowProgressBar.setVisible(false);
        workflowProgressBar.setManaged(false);
        topContainer.getChildren().add(workflowProgressBar);

        root.setTop(topContainer);

        // 左侧：上下文透明化面板 + AI能力说明
        VBox leftPanel = createLeftPanel();
        leftPanel.setPrefWidth(260);
        leftPanel.setMinWidth(200);

        // 中间区域：聊天 + 结果展示(使用SplitPane)
        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);

        // 聊天区域
        BorderPane chatPane = new BorderPane();
        chatPane.setCenter(createChatArea());
        chatPane.setBottom(createInputArea());

        // 右侧：结果展示区域
        resultTabPane = new TabPane();
        resultTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        resultTabPane.setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 0 1;");

        mainSplitPane.getItems().addAll(chatPane, resultTabPane);
        mainSplitPane.setDividerPositions(0.6);

        // 使用外层SplitPane包含左侧面板和主区域
        SplitPane outerSplitPane = new SplitPane();
        outerSplitPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        outerSplitPane.getItems().addAll(leftPanel, mainSplitPane);
        outerSplitPane.setDividerPositions(0.2);

        root.setCenter(outerSplitPane);

        Scene scene = new Scene(root);
        setScene(scene);

        // 初始化工作流引擎
        initWorkflowEngine();

        // 关闭时清理
        setOnCloseRequest(e -> {
            if (agent != null) {
                agent.clearSession();
            }
            if (workflowEngine != null && workflowEngine.hasActiveWorkflow()) {
                workflowEngine.cancelWorkflow();
            }
        });
    }

    /**
     * 创建左侧面板（设计师工作台 + 上下文透明化 + 工作流可视化 + 历史）
     */
    private VBox createLeftPanel() {
        VBox leftPanel = new VBox(0);  // 无间距，TabPane 占满
        leftPanel.setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #e0e0e0; -fx-border-width: 0 1 0 0;");
        leftPanel.setPrefWidth(280);  // 增加默认宽度

        // ==================== 设计师工作台面板 ====================
        workbenchPanel = new DesignerWorkbenchPanel();
        workbenchPanel.setOnOperationSelected(operation -> {
            switch (operation) {
                case "query" -> inputArea.setText("查询");
                case "modify" -> inputArea.setText("修改");
                case "analyze" -> inputArea.setText("分析");
                case "template" -> addSystemMessage("打开SQL模板库");
            }
            inputArea.requestFocus();
        });
        workbenchPanel.setOnSuggestionExecuted(suggestion -> {
            if (suggestion.sql() != null && !suggestion.sql().isEmpty()) {
                // 直接执行SQL
                executeSqlDirectly(suggestion.sql(), suggestion.title());
            } else if (suggestion.prompt() != null) {
                // 发送提示词到AI
                inputArea.setText(suggestion.prompt());
                sendMessage();
            }
        });
        workbenchPanel.setOnCustomPromptSubmitted(prompt -> {
            inputArea.setText(prompt);
            sendMessage();
        });
        workbenchPanel.setOnSqlExecuteRequested(sql -> {
            executeSqlDirectly(sql, "知识库SQL");
        });

        // ==================== 上下文透明化面板 ====================
        contextPanel = new ContextTransparencyPanel();
        contextPanel.setOnSupplementAdded(supplement -> {
            addSystemMessage("已添加补充说明: " + supplement);
        });

        // ==================== 工作流可视化面板 ====================
        workflowVisualization = new WorkflowVisualization();
        workflowVisualization.setOnStepAction((step, action) -> {
            switch (action) {
                case "confirm" -> workflowEngine.confirmStep();
                case "skip" -> workflowEngine.skipStep();
                case "cancel" -> workflowEngine.cancelWorkflow();
                case "regenerate" -> addSystemMessage("重新生成功能正在开发中");
                case "supplement" -> {
                    // 弹出补充说明对话框
                    TextInputDialog dialog = new TextInputDialog();
                    dialog.setTitle("补充说明");
                    dialog.setHeaderText("为当前步骤添加补充说明");
                    dialog.setContentText("说明:");
                    dialog.showAndWait().ifPresent(text -> {
                        workflowEngine.modifyStep(text);
                    });
                }
            }
        });
        workflowVisualization.setOnWorkflowComplete(result -> {
            if (result.success()) {
                addSystemMessage("✅ 工作流完成: " + result.message());
            } else {
                addErrorMessage("工作流失败: " + result.message());
            }
        });

        // ==================== AI能力说明面板 ====================
        capabilityGuide = new AiCapabilityGuide();

        // ==================== 工作流历史面板 ====================
        historyPanel = new WorkflowHistoryPanel();
        historyPanel.setOnUndoComplete(result -> {
            if (result.success) {
                addSystemMessage("↩ 撤销成功: " + result.message + " (恢复 " + result.restoredRows + " 行)");
            } else {
                addErrorMessage("撤销失败: " + result.message);
            }
        });

        // ==================== 领域知识卡片面板 ====================
        knowledgeCards = new DomainKnowledgeCards();
        knowledgeCards.setOnExecuteClicked(sql -> {
            executeSqlDirectly(sql, "知识库SQL");
        });

        // ==================== 使用TabPane组织面板 ====================
        TabPane leftTabPane = new TabPane();
        leftTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        leftTabPane.setTabMinWidth(40);  // 设置最小宽度
        leftTabPane.setStyle("-fx-background-color: #FAFAFA;");

        // 使用纯文本标签，避免 emoji 显示问题
        Tab workbenchTab = new Tab("工作台");
        workbenchTab.setContent(workbenchPanel);

        Tab contextTab = new Tab("上下文");
        contextTab.setContent(contextPanel);

        Tab workflowTab = new Tab("工作流");
        workflowTab.setContent(workflowVisualization);

        Tab historyTab = new Tab("历史");
        historyTab.setContent(historyPanel);

        Tab guideTab = new Tab("AI能力");
        guideTab.setContent(capabilityGuide);

        Tab knowledgeTab = new Tab("知识库");
        knowledgeTab.setContent(knowledgeCards);

        leftTabPane.getTabs().addAll(workbenchTab, contextTab, workflowTab, historyTab, guideTab, knowledgeTab);

        leftPanel.getChildren().add(leftTabPane);
        VBox.setVgrow(leftTabPane, Priority.ALWAYS);

        return leftPanel;
    }

    /**
     * 直接执行SQL并显示结果
     */
    private void executeSqlDirectly(String sql, String queryName) {
        if (sql == null || sql.trim().isEmpty()) {
            addErrorMessage("SQL为空");
            return;
        }

        setLoading(true);
        addSystemMessage("执行SQL: " + sql.substring(0, Math.min(50, sql.length())) + "...");

        new Thread(() -> {
            try {
                SqlExecutionTool.SqlExecutionResult result = sqlTool.executeSql(sql);

                Platform.runLater(() -> {
                    if (result.isSuccess()) {
                        addAssistantMessage(String.format("✅ 查询完成\n返回 %d 行数据, 耗时 %d ms",
                            result.getRowCount(), result.getExecutionTimeMs()));
                        displayResultTable(result.getRows(), queryName);
                    } else {
                        addErrorMessage("SQL执行失败: " + result.getError());
                    }
                    setLoading(false);
                });
            } catch (Exception e) {
                log.error("SQL执行失败", e);
                Platform.runLater(() -> {
                    addErrorMessage("SQL执行失败: " + e.getMessage());
                    setLoading(false);
                });
            }
        }).start();
    }

    // 工作流执行器
    private WorkflowExecutors workflowExecutors;

    /**
     * 初始化工作流引擎
     */
    private void initWorkflowEngine() {
        workflowEngine = new CollaborativeWorkflowEngine();

        // 工作流执行器将在agent初始化后设置
        // 见 initAgent() 中的延迟初始化

        // 添加工作流监听器
        workflowEngine.addListener(new WorkflowListener() {
            @Override
            public void onWorkflowStarted(WorkflowState state) {
                Platform.runLater(() -> {
                    workflowProgressBar.setVisible(true);
                    workflowProgressBar.setManaged(true);
                    workflowProgressBar.updateFromState(state);
                    addSystemMessage("工作流已启动: " + state.getWorkflowType());

                    // 更新历史面板的工作流ID
                    if (historyPanel != null) {
                        historyPanel.setCurrentWorkflowId(state.getWorkflowId());
                        historyPanel.addTimelineEntry("WORKFLOW_STARTED", "工作流启动",
                                "类型: " + state.getWorkflowType());
                    }

                    // 更新工作流可视化面板
                    if (workflowVisualization != null) {
                        workflowVisualization.setWorkflowType(state.getWorkflowType());
                        // 根据工作流类型创建步骤
                        var steps = switch (state.getWorkflowType()) {
                            case "query" -> WorkflowVisualization.createQueryWorkflowSteps();
                            case "modify" -> WorkflowVisualization.createModifyWorkflowSteps();
                            case "analyze" -> WorkflowVisualization.createAnalyzeWorkflowSteps();
                            default -> WorkflowVisualization.createQueryWorkflowSteps();
                        };
                        workflowVisualization.setSteps(steps);
                    }

                    // 更新工作台面板的上下文
                    if (workbenchPanel != null && state.getContext() != null) {
                        workbenchPanel.updateContext(state.getContext());
                    }
                });
            }

            @Override
            public void onStepStarted(WorkflowStep step) {
                Platform.runLater(() -> {
                    addSystemMessage(step.getDisplayText() + " - " + step.description());

                    // 记录步骤开始
                    WorkflowState state = workflowEngine.getCurrentState();
                    if (state != null) {
                        state.logStepStarted();
                    }

                    // 更新历史面板
                    if (historyPanel != null) {
                        historyPanel.addTimelineEntry("STEP_STARTED", step.name(), step.description());
                    }

                    // 更新工作流可视化面板
                    if (workflowVisualization != null && state != null) {
                        workflowVisualization.updateStepStatus(state.getCurrentStepIndex(),
                            WorkflowVisualization.StepStatus.RUNNING);
                    }
                });
            }

            @Override
            public void onStepResultReady(WorkflowStep step, WorkflowStepResult result) {
                Platform.runLater(() -> {
                    // 显示步骤确认对话框
                    showStepConfirmationDialog(step, result);
                });
            }

            @Override
            public void onStepConfirmed(WorkflowStep step) {
                Platform.runLater(() -> {
                    WorkflowState state = workflowEngine.getCurrentState();
                    workflowProgressBar.updateProgress(state.getCurrentStepIndex());

                    // 记录步骤确认
                    state.logStepConfirmed();

                    // 更新历史面板
                    if (historyPanel != null) {
                        historyPanel.addTimelineEntry("STEP_CONFIRMED", step.name() + " 已确认", null);
                        historyPanel.notifyNewUndoableOperation();
                    }

                    // 更新工作流可视化面板 - 标记为完成
                    if (workflowVisualization != null) {
                        int stepIndex = state.getCurrentStepIndex() - 1; // 当前已移到下一步
                        if (stepIndex >= 0) {
                            workflowVisualization.updateStepStatus(stepIndex,
                                WorkflowVisualization.StepStatus.COMPLETED);
                            workflowVisualization.addHistoryEntry(
                                workflowVisualization.getSteps().get(stepIndex),
                                "步骤已确认");
                        }
                    }

                    // 更新撤销按钮状态
                    updateUndoButtonState();
                });
            }

            @Override
            public void onStepSkipped(WorkflowStep step) {
                Platform.runLater(() -> {
                    WorkflowState state = workflowEngine.getCurrentState();
                    if (state != null) {
                        state.logStepSkipped();
                    }

                    addSystemMessage("⏭ 已跳过: " + step.name());

                    // 更新历史面板
                    if (historyPanel != null) {
                        historyPanel.addTimelineEntry("STEP_SKIPPED", step.name() + " 已跳过", null);
                    }

                    // 更新工作流可视化面板 - 标记为跳过
                    if (workflowVisualization != null && state != null) {
                        int stepIndex = state.getCurrentStepIndex() - 1;
                        if (stepIndex >= 0) {
                            workflowVisualization.updateStepStatus(stepIndex,
                                WorkflowVisualization.StepStatus.SKIPPED);
                            workflowVisualization.addHistoryEntry(
                                workflowVisualization.getSteps().get(stepIndex),
                                "步骤已跳过");
                        }
                    }
                });
            }

            @Override
            public void onStepCorrected(WorkflowStep step, String correction) {
                Platform.runLater(() -> {
                    WorkflowState state = workflowEngine.getCurrentState();
                    if (state != null) {
                        state.logStepCorrected(correction);
                    }

                    addSystemMessage("✏ 已修正: " + step.name() + "\n修正内容: " + correction);

                    // 更新历史面板
                    if (historyPanel != null) {
                        historyPanel.addTimelineEntry("STEP_CORRECTED", step.name() + " 已修正", correction);
                    }

                    // 更新工作流可视化面板 - 显示修正信息
                    if (workflowVisualization != null && state != null) {
                        int stepIndex = state.getCurrentStepIndex();
                        if (stepIndex >= 0 && stepIndex < workflowVisualization.getSteps().size()) {
                            var vizStep = workflowVisualization.getSteps().get(stepIndex);
                            vizStep.setOutput("修正: " + correction);
                            workflowVisualization.showStepDetail(vizStep);
                            workflowVisualization.addHistoryEntry(vizStep, "用户修正: " + correction);
                        }
                    }
                });
            }

            @Override
            public void onWorkflowCompleted(WorkflowState state) {
                Platform.runLater(() -> {
                    workflowProgressBar.markCompleted();

                    // 显示完成摘要
                    WorkflowState.WorkflowSummary summary = state.getSummary();
                    addSystemMessage(String.format(
                            "✅ 工作流已完成！\n" +
                            "影响行数: %d\n" +
                            "操作次数: %d\n" +
                            "修正次数: %d\n" +
                            "耗时: %d ms",
                            summary.totalAffectedRows(),
                            summary.operationCount(),
                            summary.correctionCount(),
                            summary.getDurationMs()
                    ));

                    // 更新历史面板
                    if (historyPanel != null) {
                        historyPanel.addTimelineEntry("WORKFLOW_COMPLETED", "工作流完成",
                                "影响 " + state.getTotalAffectedRows() + " 行");
                        historyPanel.refresh();
                    }

                    // 更新工作流可视化面板 - 标记所有步骤完成
                    if (workflowVisualization != null) {
                        var steps = workflowVisualization.getSteps();
                        for (int i = 0; i < steps.size(); i++) {
                            var step = steps.get(i);
                            if (step.getStatus() == WorkflowVisualization.StepStatus.PENDING ||
                                step.getStatus() == WorkflowVisualization.StepStatus.RUNNING) {
                                workflowVisualization.updateStepStatus(i,
                                    WorkflowVisualization.StepStatus.COMPLETED);
                            }
                        }
                        workflowVisualization.addHistoryEntry(null,
                            "工作流完成, 影响 " + state.getTotalAffectedRows() + " 行");
                    }

                    // 更新撤销按钮状态
                    updateUndoButtonState();

                    // 延迟隐藏进度条
                    new Thread(() -> {
                        try {
                            Thread.sleep(3000);
                            Platform.runLater(() -> {
                                workflowProgressBar.setVisible(false);
                                workflowProgressBar.setManaged(false);
                            });
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                });
            }

            @Override
            public void onWorkflowCancelled() {
                Platform.runLater(() -> {
                    workflowProgressBar.markCancelled();
                    addSystemMessage("工作流已取消");

                    // 更新历史面板
                    if (historyPanel != null) {
                        historyPanel.addTimelineEntry("WORKFLOW_CANCELLED", "工作流已取消", null);
                        historyPanel.refresh();
                    }

                    // 更新工作流可视化面板 - 重置状态
                    if (workflowVisualization != null) {
                        workflowVisualization.reset();
                        workflowVisualization.addHistoryEntry(null, "工作流已取消");
                    }

                    workflowProgressBar.setVisible(false);
                    workflowProgressBar.setManaged(false);
                });
            }

            @Override
            public void onWorkflowError(WorkflowStep step, Throwable error) {
                Platform.runLater(() -> {
                    workflowProgressBar.markFailed();
                    addErrorMessage("工作流执行失败: " + error.getMessage());

                    // 更新历史面板
                    if (historyPanel != null) {
                        historyPanel.addTimelineEntry("WORKFLOW_FAILED", "工作流失败", error.getMessage());
                        historyPanel.refresh();
                    }

                    // 更新工作流可视化面板 - 标记当前步骤失败
                    if (workflowVisualization != null) {
                        WorkflowState state = workflowEngine.getCurrentState();
                        if (state != null) {
                            int stepIndex = state.getCurrentStepIndex();
                            if (stepIndex >= 0 && stepIndex < workflowVisualization.getSteps().size()) {
                                workflowVisualization.updateStepStatus(stepIndex,
                                    WorkflowVisualization.StepStatus.FAILED);
                                var vizStep = workflowVisualization.getSteps().get(stepIndex);
                                vizStep.setOutput("错误: " + error.getMessage());
                                workflowVisualization.showStepDetail(vizStep);
                            }
                        }
                        workflowVisualization.addHistoryEntry(null, "工作流失败: " + error.getMessage());
                    }
                });
            }
        });
    }

    /**
     * 显示步骤确认对话框
     */
    private void showStepConfirmationDialog(WorkflowStep step, WorkflowStepResult result) {
        boolean hasPrevious = workflowEngine.getCurrentState().hasPreviousStep();

        Optional<StepConfirmationDialog.UserAction> action =
                StepConfirmationDialog.show(step, result, hasPrevious);

        action.ifPresent(userAction -> {
            switch (userAction.type()) {
                case CONFIRM -> workflowEngine.confirmStep();
                case MODIFY -> workflowEngine.modifyStep(userAction.correction());
                case SKIP -> workflowEngine.skipStep();
                case PREVIOUS -> workflowEngine.previousStep();
                case CANCEL -> workflowEngine.cancelWorkflow();
            }
        });
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        // 模型选择
        Label modelLabel = new Label("AI模型:");
        modelSelector = new ComboBox<>();
        modelSelector.getItems().addAll("qwen", "doubao", "kimi", "deepseek");
        modelSelector.setValue("qwen");
        modelSelector.setOnAction(e -> {
            if (agent != null) {
                agent.switchModel(modelSelector.getValue());
                addSystemMessage("已切换到 " + modelSelector.getValue() + " 模型");

                // 同步更新工作流执行器的模型
                if (workflowExecutors != null) {
                    workflowExecutors.setCurrentModel(modelSelector.getValue());
                }
            }
        });

        // 新对话按钮
        Button newChatBtn = new Button("新对话");
        newChatBtn.setOnAction(e -> {
            if (agent != null) {
                agent.startNewSession();
                chatContainer.getChildren().clear();
                addSystemMessage("已开始新对话");
            }
        });

        // 清空按钮
        Button clearBtn = new Button("清空");
        clearBtn.setOnAction(e -> {
            if (agent != null) {
                agent.clearSession();
                chatContainer.getChildren().clear();
                addSystemMessage("对话已清空");
            }
        });

        // SQL模式切换
        sqlModeToggle = new ToggleButton("📊 SQL查询模式");
        sqlModeToggle.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        sqlModeToggle.setOnAction(e -> {
            if (sqlModeToggle.isSelected()) {
                sqlModeToggle.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
                addSystemMessage("已切换到SQL查询模式\n输入自然语言,AI将自动生成SQL并展示结果");
            } else {
                sqlModeToggle.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
                addSystemMessage("已退出SQL查询模式");
            }
        });

        // 上下文管理按钮
        Button contextBtn = new Button("\uD83E\uDDE0 上下文管理");
        contextBtn.setTooltip(new Tooltip("管理AI的语义上下文和预设映射"));
        contextBtn.setOnAction(e -> openContextManager());

        // 工作流模式切换
        workflowModeToggle = new ToggleButton("\uD83D\uDD04 协作模式");
        workflowModeToggle.setTooltip(new Tooltip("开启协作模式：AI每步操作都需要确认"));
        workflowModeToggle.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white;");
        workflowModeToggle.setOnAction(e -> {
            if (workflowModeToggle.isSelected()) {
                workflowModeToggle.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
                addSystemMessage("\uD83D\uDD04 已开启协作模式\n" +
                        "AI的每个操作步骤都会显示确认对话框\n" +
                        "您可以确认、修正、跳过或回退每一步");
                capabilityGuide.highlightForOperation("modify");
            } else {
                workflowModeToggle.setStyle("-fx-background-color: #9C27B0; -fx-text-fill: white;");
                addSystemMessage("已关闭协作模式");
                capabilityGuide.resetHighlight();
            }
        });

        // 撤销按钮
        undoButton = new Button("↩ 撤销");
        undoButton.setTooltip(new Tooltip("撤销最近的数据修改操作"));
        undoButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        undoButton.setDisable(true);
        undoButton.setOnAction(e -> performUndo());

        // 状态指示
        statusLabel = new Label("就绪");
        statusLabel.setStyle("-fx-text-fill: #666;");

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(20, 20);
        loadingIndicator.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(
            modelLabel, modelSelector,
            new Separator(),
            newChatBtn, clearBtn,
            new Separator(),
            sqlModeToggle, workflowModeToggle, contextBtn,
            new Separator(),
            undoButton,
            spacer,
            loadingIndicator, statusLabel
        );

        return toolbar;
    }

    private ScrollPane createChatArea() {
        chatContainer = new VBox(10);
        chatContainer.setPadding(new Insets(15));
        chatContainer.setStyle("-fx-background-color: white;");

        chatScrollPane = new ScrollPane(chatContainer);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setStyle("-fx-background-color: white; -fx-background: white;");

        // 自动滚动到底部
        chatContainer.heightProperty().addListener((obs, old, newVal) -> {
            chatScrollPane.setVvalue(1.0);
        });

        return chatScrollPane;
    }

    private VBox createInputArea() {
        VBox inputBox = new VBox(5);
        inputBox.setPadding(new Insets(10));
        inputBox.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");

        // 待确认提示条
        HBox pendingBar = createPendingBar();
        pendingBar.setVisible(false);
        pendingBar.setManaged(false);

        // 输入区域
        HBox inputRow = new HBox(10);
        inputRow.setAlignment(Pos.BOTTOM_CENTER);

        inputArea = new TextArea();
        inputArea.setPromptText("输入您的问题...（Ctrl+Enter发送）");
        inputArea.setPrefRowCount(3);
        inputArea.setWrapText(true);
        HBox.setHgrow(inputArea, Priority.ALWAYS);

        // 快捷键
        inputArea.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && e.isControlDown()) {
                sendMessage();
                e.consume();
            }
        });

        sendButton = new Button("发送");
        sendButton.setPrefWidth(80);
        sendButton.setPrefHeight(60);
        sendButton.setStyle("-fx-background-color: #1976D2; -fx-text-fill: white; -fx-font-weight: bold;");
        sendButton.setOnAction(e -> sendMessage());

        inputRow.getChildren().addAll(inputArea, sendButton);

        // 快捷提示
        HBox hints = new HBox(15);
        hints.setAlignment(Pos.CENTER_LEFT);

        Label hintLabel = new Label("快捷输入:");
        hintLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11;");

        String[] quickInputs = {"查询所有物品表", "显示紫色装备", "分析技能伤害分布", "查看操作历史"};
        for (String hint : quickInputs) {
            Hyperlink link = new Hyperlink(hint);
            link.setStyle("-fx-font-size: 11;");
            link.setOnAction(e -> {
                inputArea.setText(hint);
                sendMessage();
            });
            hints.getChildren().add(link);
        }

        hints.getChildren().add(0, hintLabel);

        inputBox.getChildren().addAll(pendingBar, inputRow, hints);
        return inputBox;
    }

    private HBox createPendingBar() {
        HBox bar = new HBox(15);
        bar.setPadding(new Insets(8));
        bar.setAlignment(Pos.CENTER);
        bar.setStyle("-fx-background-color: #FFF8E1; -fx-border-color: #FFB300; " +
                    "-fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;");

        Label icon = new Label("⚠️");
        Label text = new Label("有待确认的操作");
        text.setStyle("-fx-font-weight: bold;");

        Button confirmBtn = new Button("确认执行");
        confirmBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        confirmBtn.setOnAction(e -> {
            inputArea.setText("确认");
            sendMessage();
        });

        Button cancelBtn = new Button("取消");
        cancelBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        cancelBtn.setOnAction(e -> {
            inputArea.setText("取消");
            sendMessage();
        });

        bar.getChildren().addAll(icon, text, confirmBtn, cancelBtn);
        return bar;
    }

    private void initAgent() {
        // 先初始化基础组件（即使 AI 失败也能用）
        try {
            // 初始化SQL工具（不依赖 AI）
            sqlTool = new SqlExecutionTool(jdbcTemplate, modelSelector.getValue());
            log.info("SQL工具初始化成功");

            // 初始化追溯和撤销管理器
            initUndoManagers();
            log.info("撤销管理器初始化成功");
        } catch (Exception e) {
            log.error("基础工具初始化失败", e);
            addErrorMessage("⚠ 基础工具初始化失败: " + e.getMessage());
        }

        // 初始化 AI Agent
        try {
            // 从 Spring 容器获取 LangChain4j Agent
            agent = SpringContextHolder.getBean(LangChainGameDataAgent.class);

            // 设置消息回调
            agent.setMessageCallback(message -> {
                Platform.runLater(() -> addMessageToChat(message));
            });

            // 初始化Agent（快速启动，不阻塞UI）
            agent.initialize(jdbcTemplate);

            addSystemMessage("✅ AI游戏数据助手已就绪！\n\n" +
                "🗣 对话模式: 可以用自然语言查询和修改游戏数据\n" +
                "📊 SQL模式: 自动生成SQL查询并展示结果表格\n" +
                "🔄 协作模式: AI每步操作都需要确认\n" +
                "↩ 撤销功能: 支持回滚数据修改操作\n\n" +
                "示例: \"查询所有50级以上的紫色武器\"");

            // 初始化工作流执行器（在agent初始化后）
            initWorkflowExecutors();

            // 异步初始化动态语义（后台进行，不阻塞UI）
            initDynamicSemanticsAsync();

        } catch (Exception e) {
            log.error("AI Agent初始化失败", e);
            e.printStackTrace();

            Platform.runLater(() -> {
                addErrorMessage("⚠ AI Agent 初始化失败\n\n" +
                    "错误: " + e.getMessage() + "\n\n" +
                    "可能原因:\n" +
                    "1. AI API Key 未配置或无效\n" +
                    "2. 网络连接问题\n" +
                    "3. DashScope SDK 版本不兼容\n\n" +
                    "💡 提示：\n" +
                    "- 可以继续使用 SQL 直接查询功能\n" +
                    "- 请检查 application.yml 中的 AI 配置\n" +
                    "- 确保配置了有效的 API Key");
            });
        }
    }

    /**
     * 初始化工作流执行器
     */
    private void initWorkflowExecutors() {
        if (agent == null) {
            log.warn("Agent 未初始化，跳过工作流执行器初始化");
            return;
        }

        try {
            workflowExecutors = new WorkflowExecutors(jdbcTemplate, agent);
            workflowExecutors.setCurrentModel(modelSelector.getValue());

            // 设置执行器提供者
            workflowEngine.setExecutorProvider(workflowExecutors.createExecutorProvider());

            log.info("工作流执行器初始化完成");
        } catch (Exception e) {
            log.error("工作流执行器初始化失败", e);
        }
    }

    /**
     * 异步初始化动态语义
     * PromptBuilder.setJdbcTemplate()会自动触发异步初始化
     */
    private void initDynamicSemanticsAsync() {
        // 显示加载提示
        statusLabel.setText("正在后台加载智能上下文...");

        // 延迟5秒后显示完成消息（动态语义在后台异步加载）
        new Thread(() -> {
            try {
                Thread.sleep(5000);  // 等待5秒
                Platform.runLater(() -> {
                    statusLabel.setText("就绪");
                    addSystemMessage("🧠 智能上下文已加载\n" +
                        "AI现在可以理解游戏专业术语（如\"紫装\"、\"火属性\"等）\n" +
                        "点击 🧠 上下文管理 查看详情");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "DynamicSemantics-Notify").start();
    }

    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        inputArea.clear();
        setLoading(true);

        // 判断是否为SQL模式
        boolean isSqlMode = sqlModeToggle.isSelected();

        // SQL模式下需要手动显示用户消息（因为不调用agent.chat()）
        // 非SQL模式下，agent.chat()会通过回调自动显示用户消息
        if (isSqlMode) {
            AgentMessage userMsg = AgentMessage.user(text);
            Platform.runLater(() -> addMessageToChat(userMsg));
        }

        // 在后台线程执行
        new Thread(() -> {
            try {
                if (isSqlMode) {
                    // SQL模式: 生成并执行SQL
                    handleSqlMode(text);
                } else {
                    // 普通对话模式（agent.chat会通过回调显示用户消息）
                    agent.chat(text);
                }
            } catch (Exception e) {
                log.error("消息处理失败", e);
                Platform.runLater(() -> addErrorMessage("处理失败: " + e.getMessage()));
            } finally {
                Platform.runLater(() -> {
                    setLoading(false);
                    updatePendingBar();
                });
            }
        }).start();
    }

    /**
     * 处理SQL模式查询
     */
    private void handleSqlMode(String query) {
        log.info("SQL模式查询: {}", query);

        try {
            // 1. 生成SQL
            Platform.runLater(() -> statusLabel.setText("生成SQL中..."));
            SqlExecutionTool.SqlGenerationResult genResult = sqlTool.generateSql(query);

            if (!genResult.isSuccess()) {
                Platform.runLater(() -> addErrorMessage("SQL生成失败: " + genResult.getError()));
                return;
            }

            String sql = genResult.getSql();
            String explanation = genResult.getExplanation();

            // 2. 显示生成的SQL
            Platform.runLater(() -> {
                addSqlMessage(sql, explanation);
            });

            // 3. 执行SQL
            Platform.runLater(() -> statusLabel.setText("执行SQL中..."));
            SqlExecutionTool.SqlExecutionResult execResult = sqlTool.executeSql(sql);

            if (!execResult.isSuccess()) {
                Platform.runLater(() -> addErrorMessage("SQL执行失败: " + execResult.getError()));
                return;
            }

            // 4. 显示结果
            List<Map<String, Object>> rows = execResult.getRows();
            int rowCount = execResult.getRowCount();
            long execTime = execResult.getExecutionTimeMs();

            Platform.runLater(() -> {
                addAssistantMessage(String.format("✅ 查询完成\n返回 %d 行数据, 耗时 %d ms",
                    rowCount, execTime));

                if (execResult.isTruncated()) {
                    addAssistantMessage("⚠️ 结果已截断,仅显示前 1000 行");
                }

                // 在右侧面板显示表格
                displayResultTable(rows, query);
            });

        } catch (Exception e) {
            log.error("SQL模式处理失败", e);
            Platform.runLater(() -> addErrorMessage("处理失败: " + e.getMessage()));
        }
    }

    /**
     * 显示SQL代码块消息
     */
    private void addSqlMessage(String sql, String explanation) {
        StringBuilder content = new StringBuilder();
        content.append("生成的SQL:\n\n");
        content.append("```sql\n");
        content.append(sql);
        content.append("\n```\n");

        if (explanation != null && !explanation.isEmpty()) {
            content.append("\n").append(explanation);
        }

        AgentMessage msg = AgentMessage.assistant(content.toString());
        addMessageToChat(msg);
    }

    /**
     * 在右侧面板显示查询结果表格
     */
    private void displayResultTable(List<Map<String, Object>> rows, String queryName) {
        if (rows == null || rows.isEmpty()) {
            addAssistantMessage("查询无结果");
            return;
        }

        // 创建TableView
        TableView<Map<String, Object>> tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 动态创建列
        Map<String, Object> firstRow = rows.get(0);
        for (String columnName : firstRow.keySet()) {
            TableColumn<Map<String, Object>, String> column = new TableColumn<>(columnName);
            column.setCellValueFactory(cellData -> {
                Object value = cellData.getValue().get(columnName);
                return new javafx.beans.property.SimpleStringProperty(
                    value != null ? value.toString() : "NULL"
                );
            });
            tableView.getColumns().add(column);
        }

        // 填充数据
        tableView.getItems().addAll(rows);

        // 添加到ResultTabPane
        Tab resultTab = new Tab("结果: " + queryName);
        resultTab.setContent(tableView);
        resultTabPane.getTabs().add(resultTab);
        resultTabPane.getSelectionModel().select(resultTab);
    }

    /**
     * 添加助手消息
     */
    private void addAssistantMessage(String text) {
        AgentMessage msg = AgentMessage.assistant(text);
        addMessageToChat(msg);
    }

    private void addMessageToChat(AgentMessage message) {
        VBox messageBox = createMessageBox(message);
        chatContainer.getChildren().add(messageBox);
    }

    private VBox createMessageBox(AgentMessage message) {
        VBox box = new VBox(5);
        box.setPadding(new Insets(10));
        box.setMaxWidth(700);

        // 根据角色设置样式
        String bgColor;
        Pos alignment;

        switch (message.getRole()) {
            case USER:
                bgColor = USER_BG;
                alignment = Pos.CENTER_RIGHT;
                break;
            case ASSISTANT:
                bgColor = message.getType() == AgentMessage.MessageType.PENDING_CONFIRMATION ?
                    PENDING_BG : ASSISTANT_BG;
                alignment = Pos.CENTER_LEFT;
                break;
            case SYSTEM:
                bgColor = SYSTEM_BG;
                alignment = Pos.CENTER;
                break;
            default:
                bgColor = message.getType() == AgentMessage.MessageType.ERROR ? ERROR_BG : ASSISTANT_BG;
                alignment = Pos.CENTER_LEFT;
        }

        box.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 8; -fx-border-radius: 8;",
            bgColor));

        // 头部：角色图标和时间
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label roleIcon = new Label(message.getRole().getIcon());
        roleIcon.setFont(Font.font(14));

        Label timeLabel = new Label(message.getTimestamp().format(TIME_FORMATTER));
        timeLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 10;");

        header.getChildren().addAll(roleIcon, timeLabel);

        // 内容
        Label content = new Label(message.getContent());
        content.setWrapText(true);
        content.setStyle("-fx-font-size: 13;");

        // 特殊类型标记
        if (message.getType() == AgentMessage.MessageType.PENDING_CONFIRMATION) {
            Label badge = new Label("待确认");
            badge.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; " +
                          "-fx-padding: 2 6; -fx-background-radius: 3; -fx-font-size: 10;");
            header.getChildren().add(badge);
        } else if (message.getType() == AgentMessage.MessageType.ERROR) {
            Label badge = new Label("错误");
            badge.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; " +
                          "-fx-padding: 2 6; -fx-background-radius: 3; -fx-font-size: 10;");
            header.getChildren().add(badge);
        }

        box.getChildren().addAll(header, content);

        // 外层容器用于对齐
        HBox wrapper = new HBox();
        wrapper.setAlignment(alignment);
        wrapper.getChildren().add(box);

        VBox result = new VBox();
        result.getChildren().add(wrapper);
        return result;
    }

    private void addSystemMessage(String text) {
        AgentMessage msg = AgentMessage.system(text);
        addMessageToChat(msg);
    }

    private void addErrorMessage(String text) {
        AgentMessage msg = AgentMessage.error(text);
        addMessageToChat(msg);
    }

    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        sendButton.setDisable(loading);
        inputArea.setDisable(loading);
        statusLabel.setText(loading ? "思考中..." : "就绪");
    }

    private void updatePendingBar() {
        try {
            boolean hasPending = agent != null && agent.hasPendingOperation();

            // 查找pending bar（第一个子节点）
            VBox inputBox = (VBox) sendButton.getParent().getParent();
            if (inputBox != null && inputBox.getChildren().size() > 0
                && inputBox.getChildren().get(0) instanceof HBox) {
                HBox bar = (HBox) inputBox.getChildren().get(0);
                if (bar.getChildren().size() > 2) { // 确保是pending bar
                    bar.setVisible(hasPending);
                    bar.setManaged(hasPending);
                }
            }
        } catch (Exception e) {
            // 忽略pending bar更新失败（不影响主流程）
            log.debug("更新pending bar失败", e);
        }
    }

    /**
     * 打开上下文管理器
     */
    private void openContextManager() {
        try {
            // 检查 Agent 是否初始化（使用 jdbcTemplate 判断）
            red.jiuzhou.agent.texttosql.GameSemanticEnhancer enhancer =
                (agent != null && jdbcTemplate != null) ?
                    new red.jiuzhou.agent.texttosql.GameSemanticEnhancer(jdbcTemplate) :
                    null;

            if (enhancer == null) {
                addErrorMessage("语义增强器未初始化");
                return;
            }

            SemanticContextManagerStage contextStage = new SemanticContextManagerStage(enhancer, jdbcTemplate);
            contextStage.show();
        } catch (Exception e) {
            log.error("打开上下文管理器失败", e);
            addErrorMessage("打开上下文管理器失败: " + e.getMessage());
        }
    }

    // ==================== 上下文感知消息 ====================

    /**
     * 发送简单消息（供外部调用）
     *
     * @param message 要发送的消息
     */
    public void sendMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        Platform.runLater(() -> {
            // 设置输入框内容
            inputArea.setText(message);
            // 触发发送（调用私有的无参sendMessage方法）
            sendMessage();
        });
    }

    /**
     * 发送上下文感知的消息
     *
     * 用于从右键菜单等外部触发的AI操作，将上下文信息和用户请求一起发送给AI。
     * 支持协作工作流模式。
     *
     * @param context 设计上下文
     * @param prompt 用户提示词
     * @param operationType 操作类型（用于显示）
     */
    public void sendContextAwareMessage(DesignContext context, String prompt, String operationType) {
        if (agent == null) {
            addErrorMessage("AI助手未初始化");
            return;
        }

        // 更新上下文透明化面板
        if (contextPanel != null) {
            contextPanel.updateContext(context);
        }

        // 更新设计师工作台面板
        if (workbenchPanel != null) {
            workbenchPanel.updateContext(context);
        }

        // 更新AI能力说明高亮
        capabilityGuide.highlightForOperation(operationType);

        // 显示上下文信息提示
        String operationLabel = getOperationLabel(operationType);
        addSystemMessage(String.format(
            "\uD83D\uDCCD 上下文感知模式\n" +
            "操作: %s\n" +
            "位置: %s",
            operationLabel,
            context.getSummary()
        ));

        // 判断是否需要工作流模式
        boolean useWorkflow = workflowModeToggle.isSelected() ||
                              CollaborativeWorkflowEngine.requiresWorkflow(operationType);

        if (useWorkflow) {
            // 使用协作工作流模式
            startCollaborativeWorkflow(context, prompt, operationType);
        } else {
            // 普通模式
            sendNormalContextMessage(context, prompt);
        }
    }

    /**
     * 启动协作工作流
     */
    private void startCollaborativeWorkflow(DesignContext context, String prompt, String operationType) {
        // 推断工作流类型
        String workflowType = CollaborativeWorkflowEngine.inferWorkflowType(operationType);

        addSystemMessage(String.format(
            "\uD83D\uDD04 启动协作工作流: %s\n" +
            "每个步骤都会显示确认对话框，您可以随时修正或取消",
            workflowType
        ));

        // 启动工作流
        workflowEngine.startWorkflow(workflowType, context, prompt);
    }

    /**
     * 发送普通上下文消息
     */
    private void sendNormalContextMessage(DesignContext context, String prompt) {
        inputArea.setText(prompt);
        setLoading(true);

        new Thread(() -> {
            try {
                agent.chat(prompt, context);
            } catch (Exception e) {
                log.error("上下文感知消息处理失败", e);
                Platform.runLater(() -> addErrorMessage("处理失败: " + e.getMessage()));
            } finally {
                Platform.runLater(() -> {
                    setLoading(false);
                    inputArea.clear();
                    updatePendingBar();
                });
            }
        }).start();
    }

    /**
     * 显示数据对比对话框
     *
     * @param beforeData 修改前数据
     * @param afterData 修改后数据
     * @param modifiedColumns 被修改的列
     * @return 用户选中的行索引列表
     */
    public List<Integer> showDataComparison(
            List<Map<String, Object>> beforeData,
            List<Map<String, Object>> afterData,
            List<String> modifiedColumns) {

        return DataComparisonView.showDialog(
                "数据修改预览",
                beforeData,
                afterData,
                modifiedColumns
        );
    }

    /**
     * 获取操作类型的显示标签
     */
    private String getOperationLabel(String operationType) {
        return switch (operationType) {
            case "analyze" -> "🔍 分析文件";
            case "explain" -> "📖 解释数据结构";
            case "generate" -> "✨ 生成相似配置";
            case "check_refs" -> "🔗 检查引用完整性";
            case "explain_row" -> "📖 解释行数据";
            case "balance_check" -> "⚖️ 数值平衡性分析";
            case "find_similar" -> "🔎 查找相似配置";
            case "generate_variant" -> "✨ 生成变体";
            default -> "🤖 AI操作";
        };
    }

    /**
     * 获取Agent实例（用于外部访问）
     */
    public LangChainGameDataAgent getAgent() {
        return agent;
    }

    /**
     * 执行撤销操作
     */
    private void performUndo() {
        UndoManager undoManager = UndoManager.getInstance();

        if (!undoManager.canUndo()) {
            addSystemMessage("没有可撤销的操作");
            return;
        }

        // 显示确认对话框
        String undoDesc = undoManager.getLastUndoDescription();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认撤销");
        confirm.setHeaderText("撤销最近操作");
        confirm.setContentText(undoDesc + "\n\n确定要撤销吗？");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                UndoManager.UndoResult result = undoManager.undoLast();

                if (result.success) {
                    addSystemMessage("↩ " + result.message + " (恢复 " + result.restoredRows + " 行)");

                    // 刷新历史面板
                    if (historyPanel != null) {
                        historyPanel.refresh();
                    }
                } else {
                    addErrorMessage("撤销失败: " + result.message);
                }

                updateUndoButtonState();
            }
        });
    }

    /**
     * 更新撤销按钮状态
     */
    private void updateUndoButtonState() {
        Platform.runLater(() -> {
            UndoManager undoManager = UndoManager.getInstance();
            boolean canUndo = undoManager.canUndo();
            undoButton.setDisable(!canUndo);

            if (canUndo) {
                undoButton.setTooltip(new Tooltip(undoManager.getLastUndoDescription()));
            } else {
                undoButton.setTooltip(new Tooltip("没有可撤销的操作"));
            }
        });
    }

    /**
     * 初始化追溯和撤销管理器
     */
    private void initUndoManagers() {
        // 设置JdbcTemplate到各管理器
        WorkflowAuditLog.getInstance().setJdbcTemplate(jdbcTemplate);
        DataSnapshot.getInstance().setJdbcTemplate(jdbcTemplate);
        UndoManager.getInstance().setJdbcTemplate(jdbcTemplate);

        log.info("追溯和撤销管理器初始化完成");
    }

    /**
     * 显示窗口
     */
    public static void showAgent(JdbcTemplate jdbcTemplate) {
        Platform.runLater(() -> {
            AgentChatStage stage = new AgentChatStage(jdbcTemplate);
            stage.show();
        });
    }
}
