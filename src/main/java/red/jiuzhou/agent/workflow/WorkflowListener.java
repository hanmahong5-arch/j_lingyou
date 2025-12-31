package red.jiuzhou.agent.workflow;

/**
 * 工作流事件监听器
 *
 * <p>监听工作流生命周期中的关键事件，用于UI更新和日志记录。
 *
 * @author Claude
 * @version 1.0
 */
public interface WorkflowListener {

    /**
     * 工作流启动时调用
     *
     * @param state 工作流初始状态
     */
    default void onWorkflowStarted(WorkflowState state) {}

    /**
     * 步骤开始执行时调用
     *
     * @param step 当前步骤
     */
    default void onStepStarted(WorkflowStep step) {}

    /**
     * 步骤结果就绪，等待用户确认
     *
     * @param step 当前步骤
     * @param result 步骤执行结果
     */
    default void onStepResultReady(WorkflowStep step, WorkflowStepResult result) {}

    /**
     * 步骤被用户确认
     *
     * @param step 已确认的步骤
     */
    default void onStepConfirmed(WorkflowStep step) {}

    /**
     * 步骤被用户跳过
     *
     * @param step 被跳过的步骤
     */
    default void onStepSkipped(WorkflowStep step) {}

    /**
     * 步骤需要重新执行（用户提供了修正）
     *
     * @param step 需要重新执行的步骤
     * @param correction 用户提供的修正信息
     */
    default void onStepCorrected(WorkflowStep step, String correction) {}

    /**
     * 工作流完成
     *
     * @param state 工作流最终状态
     */
    default void onWorkflowCompleted(WorkflowState state) {}

    /**
     * 工作流被取消
     */
    default void onWorkflowCancelled() {}

    /**
     * 工作流出错
     *
     * @param step 出错的步骤（可能为null）
     * @param error 错误信息
     */
    default void onWorkflowError(WorkflowStep step, Throwable error) {}
}
