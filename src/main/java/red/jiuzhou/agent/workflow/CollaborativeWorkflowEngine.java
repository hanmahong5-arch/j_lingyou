package red.jiuzhou.agent.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.context.DesignContext;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 协作式工作流引擎
 *
 * <p>设计师体验增强的核心组件，提供：
 * <ul>
 *   <li>多步骤工作流管理</li>
 *   <li>每步确认/修正/跳过/回退支持</li>
 *   <li>上下文透明化</li>
 *   <li>与现有AI系统的集成</li>
 * </ul>
 *
 * <p>设计原则：
 * <ol>
 *   <li>设计师主导 - AI只提建议，所有执行都需要明确确认</li>
 *   <li>透明可控 - 设计师能看到AI使用的所有信息</li>
 *   <li>可回退 - 每一步都能回退</li>
 *   <li>渐进式 - 从简单操作开始，复杂操作分步确认</li>
 * </ol>
 *
 * @author Claude
 * @version 1.0
 */
public class CollaborativeWorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(CollaborativeWorkflowEngine.class);

    // 工作流模板注册表
    private static final Map<String, WorkflowTemplate> TEMPLATES = new HashMap<>();

    // 当前工作流状态
    private WorkflowState currentState;

    // 事件监听器
    private final List<WorkflowListener> listeners = new CopyOnWriteArrayList<>();

    // 步骤执行器提供者（由外部注入，如GameDataAgent）
    private Function<String, WorkflowStep.StepExecutor> executorProvider;

    static {
        // 注册内置工作流模板
        registerBuiltinTemplates();
    }

    // ==================== 工作流模板 ====================

    /**
     * 工作流模板接口
     */
    @FunctionalInterface
    public interface WorkflowTemplate {
        /**
         * 创建工作流步骤列表
         *
         * @param context 设计上下文
         * @param userIntent 用户意图
         * @param executorProvider 执行器提供者
         * @return 步骤列表
         */
        List<WorkflowStep> createSteps(DesignContext context, String userIntent,
                                       Function<String, WorkflowStep.StepExecutor> executorProvider);
    }

    /**
     * 注册内置工作流模板
     */
    private static void registerBuiltinTemplates() {
        // 查询工作流：理解 → 筛选 → 预览
        TEMPLATES.put("query", (context, intent, provider) -> List.of(
                WorkflowStep.understand("understand", provider.apply("understand")),
                WorkflowStep.filter("filter", provider.apply("filter")),
                WorkflowStep.preview("preview", provider.apply("preview"))
        ));

        // 修改工作流：理解 → 筛选 → 对比 → 确认 → 执行 → 验证
        TEMPLATES.put("modify", (context, intent, provider) -> List.of(
                WorkflowStep.understand("understand", provider.apply("understand")),
                WorkflowStep.filter("filter", provider.apply("filter")),
                WorkflowStep.compare("compare", provider.apply("compare")),
                WorkflowStep.confirm("confirm", provider.apply("confirm")),
                WorkflowStep.execute("execute", provider.apply("execute")),
                WorkflowStep.validate("validate", provider.apply("validate"))
        ));

        // 分析工作流：理解 → 筛选 → 分析展示
        TEMPLATES.put("analyze", (context, intent, provider) -> List.of(
                WorkflowStep.understand("understand", provider.apply("understand")),
                WorkflowStep.filter("filter", provider.apply("filter")),
                WorkflowStep.preview("analyze", provider.apply("analyze"))
        ));

        // 生成工作流：理解 → 生成 → 对比 → 确认
        TEMPLATES.put("generate", (context, intent, provider) -> List.of(
                WorkflowStep.understand("understand", provider.apply("understand")),
                WorkflowStep.preview("generate", provider.apply("generate")),
                WorkflowStep.compare("compare", provider.apply("compare")),
                WorkflowStep.confirm("confirm", provider.apply("confirm"))
        ));

        // 简单查询工作流：直接执行，不需要确认
        TEMPLATES.put("simple_query", (context, intent, provider) -> List.of(
                WorkflowStep.preview("query", provider.apply("simple_query"))
        ));
    }

    /**
     * 注册自定义工作流模板
     */
    public static void registerTemplate(String type, WorkflowTemplate template) {
        TEMPLATES.put(type, template);
        log.info("注册工作流模板: {}", type);
    }

    // ==================== 公共API ====================

    /**
     * 设置执行器提供者
     *
     * @param provider 根据步骤类型返回对应执行器的函数
     */
    public void setExecutorProvider(Function<String, WorkflowStep.StepExecutor> provider) {
        this.executorProvider = provider;
    }

    /**
     * 启动工作流
     *
     * @param type 工作流类型
     * @param context 设计上下文
     * @param userIntent 用户意图
     */
    public void startWorkflow(String type, DesignContext context, String userIntent) {
        if (executorProvider == null) {
            throw new IllegalStateException("必须先设置执行器提供者");
        }

        WorkflowTemplate template = TEMPLATES.get(type);
        if (template == null) {
            throw new IllegalArgumentException("未知的工作流类型: " + type);
        }

        // 创建步骤列表
        List<WorkflowStep> steps = template.createSteps(context, userIntent, executorProvider);

        // 创建工作流状态
        currentState = new WorkflowState(type, steps);
        currentState.setContext(context);
        currentState.setUserIntent(userIntent);
        currentState.markRunning();

        log.info("启动工作流: type={}, steps={}, intent='{}'",
                type, steps.size(), truncate(userIntent, 50));

        // 通知监听器
        notifyWorkflowStarted();

        // 执行第一步
        executeCurrentStep();
    }

    /**
     * 确认当前步骤
     */
    public void confirmStep() {
        if (currentState == null || currentState.isEnded()) {
            log.warn("没有活动的工作流");
            return;
        }

        WorkflowStep current = currentState.getCurrentStep();
        WorkflowStepResult result = currentState.getPendingResult();

        if (result == null) {
            log.warn("当前步骤没有待确认的结果");
            return;
        }

        log.info("用户确认步骤: {}", current.name());

        // 保存结果
        currentState.saveStepResult(result);

        // 通知确认
        notifyStepConfirmed(current);

        // 进入下一步或完成
        if (currentState.hasNextStep()) {
            currentState.nextStep();
            executeCurrentStep();
        } else {
            completeWorkflow();
        }
    }

    /**
     * 修正当前步骤并重新执行
     *
     * @param correction 用户提供的修正信息
     */
    public void modifyStep(String correction) {
        if (currentState == null || currentState.isEnded()) {
            log.warn("没有活动的工作流");
            return;
        }

        WorkflowStep current = currentState.getCurrentStep();
        log.info("用户修正步骤: {}, correction='{}'", current.name(), truncate(correction, 50));

        // 记录修正
        currentState.addCorrection(correction);

        // 通知修正
        notifyStepCorrected(current, correction);

        // 重新执行当前步骤
        executeCurrentStepWithCorrection(correction);
    }

    /**
     * 回退到上一步
     */
    public void previousStep() {
        if (currentState == null || currentState.isEnded()) {
            log.warn("没有活动的工作流");
            return;
        }

        if (!currentState.hasPreviousStep()) {
            log.warn("已经是第一步，无法回退");
            return;
        }

        currentState.previousStep();
        log.info("回退到步骤: {}", currentState.getCurrentStep().name());

        // 重新执行
        executeCurrentStep();
    }

    /**
     * 跳过当前步骤
     */
    public void skipStep() {
        if (currentState == null || currentState.isEnded()) {
            log.warn("没有活动的工作流");
            return;
        }

        WorkflowStep current = currentState.getCurrentStep();
        if (!current.skippable()) {
            log.warn("当前步骤不可跳过: {}", current.name());
            return;
        }

        log.info("跳过步骤: {}", current.name());

        // 通知跳过
        notifyStepSkipped(current);

        // 跳过并进入下一步
        currentState.skipCurrentStep();

        if (currentState.hasNextStep()) {
            currentState.nextStep();
            executeCurrentStep();
        } else {
            completeWorkflow();
        }
    }

    /**
     * 取消整个工作流
     */
    public void cancelWorkflow() {
        if (currentState == null) {
            return;
        }

        log.info("取消工作流: {}", currentState.getWorkflowId());

        currentState.markCancelled();
        notifyWorkflowCancelled();
        currentState = null;
    }

    // ==================== 步骤执行 ====================

    /**
     * 执行当前步骤
     */
    private void executeCurrentStep() {
        executeCurrentStepWithCorrection(null);
    }

    /**
     * 执行当前步骤（带修正）
     */
    private void executeCurrentStepWithCorrection(String correction) {
        WorkflowStep step = currentState.getCurrentStep();
        if (step == null) {
            log.error("无法获取当前步骤");
            return;
        }

        currentState.markRunning();

        // 通知步骤开始
        notifyStepStarted(step);

        log.info("执行步骤: {} ({})", step.name(), step.type());

        // 异步执行
        CompletableFuture<WorkflowStepResult> future = correction != null
                ? step.executeWithCorrection(currentState.getContext(), correction)
                : step.execute(currentState.getContext());

        future.thenAccept(result -> {
            log.info("步骤执行完成: {}, status={}", step.name(), result.getStatus());

            if (step.requiresConfirmation()) {
                // 需要用户确认
                currentState.setPendingResult(result);
                currentState.markWaiting();
                notifyStepResultReady(step, result);
            } else {
                // 不需要确认，自动进入下一步
                currentState.saveStepResult(result);
                if (currentState.hasNextStep()) {
                    currentState.nextStep();
                    executeCurrentStep();
                } else {
                    completeWorkflow();
                }
            }
        }).exceptionally(error -> {
            log.error("步骤执行失败: {}", step.name(), error);
            currentState.markFailed();
            notifyWorkflowError(step, error);
            return null;
        });
    }

    /**
     * 完成工作流
     */
    private void completeWorkflow() {
        log.info("工作流完成: {}, 影响行数: {}",
                currentState.getWorkflowId(),
                currentState.getTotalAffectedRows());

        currentState.markCompleted();
        notifyWorkflowCompleted();
    }

    // ==================== 监听器管理 ====================

    /**
     * 添加监听器
     */
    public void addListener(WorkflowListener listener) {
        listeners.add(listener);
    }

    /**
     * 移除监听器
     */
    public void removeListener(WorkflowListener listener) {
        listeners.remove(listener);
    }

    private void notifyWorkflowStarted() {
        for (WorkflowListener listener : listeners) {
            try {
                listener.onWorkflowStarted(currentState);
            } catch (Exception e) {
                log.error("监听器执行失败", e);
            }
        }
    }

    private void notifyStepStarted(WorkflowStep step) {
        for (WorkflowListener listener : listeners) {
            try {
                listener.onStepStarted(step);
            } catch (Exception e) {
                log.error("监听器执行失败", e);
            }
        }
    }

    private void notifyStepResultReady(WorkflowStep step, WorkflowStepResult result) {
        for (WorkflowListener listener : listeners) {
            try {
                listener.onStepResultReady(step, result);
            } catch (Exception e) {
                log.error("监听器执行失败", e);
            }
        }
    }

    private void notifyStepConfirmed(WorkflowStep step) {
        for (WorkflowListener listener : listeners) {
            try {
                listener.onStepConfirmed(step);
            } catch (Exception e) {
                log.error("监听器执行失败", e);
            }
        }
    }

    private void notifyStepSkipped(WorkflowStep step) {
        for (WorkflowListener listener : listeners) {
            try {
                listener.onStepSkipped(step);
            } catch (Exception e) {
                log.error("监听器执行失败", e);
            }
        }
    }

    private void notifyStepCorrected(WorkflowStep step, String correction) {
        for (WorkflowListener listener : listeners) {
            try {
                listener.onStepCorrected(step, correction);
            } catch (Exception e) {
                log.error("监听器执行失败", e);
            }
        }
    }

    private void notifyWorkflowCompleted() {
        for (WorkflowListener listener : listeners) {
            try {
                listener.onWorkflowCompleted(currentState);
            } catch (Exception e) {
                log.error("监听器执行失败", e);
            }
        }
    }

    private void notifyWorkflowCancelled() {
        for (WorkflowListener listener : listeners) {
            try {
                listener.onWorkflowCancelled();
            } catch (Exception e) {
                log.error("监听器执行失败", e);
            }
        }
    }

    private void notifyWorkflowError(WorkflowStep step, Throwable error) {
        for (WorkflowListener listener : listeners) {
            try {
                listener.onWorkflowError(step, error);
            } catch (Exception e) {
                log.error("监听器执行失败", e);
            }
        }
    }

    // ==================== 状态查询 ====================

    /**
     * 是否有活动的工作流
     */
    public boolean hasActiveWorkflow() {
        return currentState != null && !currentState.isEnded();
    }

    /**
     * 获取当前工作流状态
     */
    public WorkflowState getCurrentState() {
        return currentState;
    }

    /**
     * 获取当前步骤
     */
    public WorkflowStep getCurrentStep() {
        return currentState != null ? currentState.getCurrentStep() : null;
    }

    /**
     * 获取待确认的结果
     */
    public WorkflowStepResult getPendingResult() {
        return currentState != null ? currentState.getPendingResult() : null;
    }

    /**
     * 获取进度百分比
     */
    public double getProgress() {
        return currentState != null ? currentState.getProgressPercent() : 0;
    }

    /**
     * 获取进度描述
     */
    public String getProgressDescription() {
        return currentState != null ? currentState.getProgressDescription() : "";
    }

    // ==================== 工具方法 ====================

    /**
     * 根据操作类型推断工作流类型
     */
    public static String inferWorkflowType(String operationType) {
        if (operationType == null) return "query";

        return switch (operationType) {
            case "what_is_this", "explain_numbers", "show_relations",
                 "find_similar", "find_related", "analyze_structure",
                 "check_references", "data_analysis" -> "query";

            case "check_balance", "compare_similar", "predict_experience" -> "analyze";

            case "suggest_improvements", "generate_variant", "generate_doc" -> "generate";

            case "batch_modify", "modify" -> "modify";

            default -> "query";
        };
    }

    /**
     * 判断操作是否需要工作流模式
     */
    public static boolean requiresWorkflow(String operationType) {
        // 这些操作涉及数据修改，需要工作流模式
        Set<String> workflowOperations = Set.of(
                "generate_variant",
                "suggest_improvements",
                "batch_modify",
                "modify"
        );
        return workflowOperations.contains(operationType);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
