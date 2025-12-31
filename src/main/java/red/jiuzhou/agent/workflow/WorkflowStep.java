package red.jiuzhou.agent.workflow;

import red.jiuzhou.agent.context.DesignContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * 工作流步骤定义
 *
 * <p>使用Record定义不可变的工作流步骤，包含步骤的所有元信息和执行器。
 *
 * @param id 步骤唯一标识
 * @param name 步骤显示名称
 * @param description 步骤描述说明
 * @param type 步骤类型
 * @param skippable 是否可跳过
 * @param requiresConfirmation 是否需要用户确认
 * @param executor 步骤执行器
 *
 * @author Claude
 * @version 1.0
 */
public record WorkflowStep(
        String id,
        String name,
        String description,
        StepType type,
        boolean skippable,
        boolean requiresConfirmation,
        StepExecutor executor
) {

    /**
     * 步骤类型枚举
     */
    public enum StepType {
        /**
         * 意图理解 - AI解析用户意图
         */
        UNDERSTAND("理解意图", "AI正在理解您的需求..."),

        /**
         * 筛选步骤 - 确定操作范围
         */
        FILTER("筛选数据", "正在筛选符合条件的数据..."),

        /**
         * 预览步骤 - 展示查询结果
         */
        PREVIEW("预览结果", "展示查询结果..."),

        /**
         * 对比步骤 - 修改前后对比
         */
        COMPARE("对比预览", "展示修改前后的差异..."),

        /**
         * 确认步骤 - 最终确认
         */
        CONFIRM("确认执行", "请确认是否执行操作..."),

        /**
         * 执行步骤 - 执行实际操作
         */
        EXECUTE("执行操作", "正在执行操作..."),

        /**
         * 验证步骤 - 验证执行结果
         */
        VALIDATE("验证结果", "正在验证执行结果...");

        private final String displayName;
        private final String progressMessage;

        StepType(String displayName, String progressMessage) {
            this.displayName = displayName;
            this.progressMessage = progressMessage;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getProgressMessage() {
            return progressMessage;
        }
    }

    /**
     * 步骤执行器函数式接口
     *
     * <p>接收设计上下文和用户修正，返回异步执行结果。
     */
    @FunctionalInterface
    public interface StepExecutor {
        /**
         * 执行步骤
         *
         * @param context 设计上下文
         * @param correction 用户修正信息（可能为null）
         * @return 异步执行结果
         */
        CompletableFuture<WorkflowStepResult> execute(DesignContext context, String correction);
    }

    // ==================== 便捷构建方法 ====================

    /**
     * 创建理解意图步骤
     */
    public static WorkflowStep understand(String id, StepExecutor executor) {
        return new WorkflowStep(
                id,
                "理解意图",
                "AI正在分析您的需求，请确认理解是否正确",
                StepType.UNDERSTAND,
                false,  // 不可跳过
                true,   // 需要确认
                executor
        );
    }

    /**
     * 创建筛选步骤
     */
    public static WorkflowStep filter(String id, StepExecutor executor) {
        return new WorkflowStep(
                id,
                "筛选数据",
                "请确认筛选条件和结果范围",
                StepType.FILTER,
                false,  // 不可跳过
                true,   // 需要确认
                executor
        );
    }

    /**
     * 创建预览步骤
     */
    public static WorkflowStep preview(String id, StepExecutor executor) {
        return new WorkflowStep(
                id,
                "预览数据",
                "查看符合条件的数据",
                StepType.PREVIEW,
                true,   // 可跳过
                true,   // 需要确认
                executor
        );
    }

    /**
     * 创建对比步骤
     */
    public static WorkflowStep compare(String id, StepExecutor executor) {
        return new WorkflowStep(
                id,
                "对比预览",
                "查看修改前后的差异，可以逐条选择确认或拒绝",
                StepType.COMPARE,
                false,  // 不可跳过
                true,   // 需要确认
                executor
        );
    }

    /**
     * 创建确认步骤
     */
    public static WorkflowStep confirm(String id, StepExecutor executor) {
        return new WorkflowStep(
                id,
                "最终确认",
                "请确认是否执行操作，此操作可回滚",
                StepType.CONFIRM,
                false,  // 不可跳过
                true,   // 需要确认
                executor
        );
    }

    /**
     * 创建执行步骤
     */
    public static WorkflowStep execute(String id, StepExecutor executor) {
        return new WorkflowStep(
                id,
                "执行操作",
                "正在执行数据修改...",
                StepType.EXECUTE,
                false,  // 不可跳过
                false,  // 自动执行，不需要用户确认
                executor
        );
    }

    /**
     * 创建验证步骤
     */
    public static WorkflowStep validate(String id, StepExecutor executor) {
        return new WorkflowStep(
                id,
                "验证结果",
                "验证操作是否成功执行",
                StepType.VALIDATE,
                true,   // 可跳过
                true,   // 需要确认（展示结果）
                executor
        );
    }

    // ==================== 执行方法 ====================

    /**
     * 执行步骤（无修正）
     */
    public CompletableFuture<WorkflowStepResult> execute(DesignContext context) {
        return executor.execute(context, null);
    }

    /**
     * 执行步骤（带修正）
     */
    public CompletableFuture<WorkflowStepResult> executeWithCorrection(DesignContext context, String correction) {
        return executor.execute(context, correction);
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取步骤图标
     */
    public String getIcon() {
        return switch (type) {
            case UNDERSTAND -> "\uD83E\uDDE0"; // 脑
            case FILTER -> "\uD83D\uDD0D"; // 放大镜
            case PREVIEW -> "\uD83D\uDC41"; // 眼睛
            case COMPARE -> "\u2194\uFE0F"; // 双向箭头
            case CONFIRM -> "\u2705"; // 绿色勾
            case EXECUTE -> "\u26A1"; // 闪电
            case VALIDATE -> "\u2714\uFE0F"; // 勾
        };
    }

    /**
     * 获取完整显示文本
     */
    public String getDisplayText() {
        return getIcon() + " " + name;
    }

    @Override
    public String toString() {
        return String.format("WorkflowStep{id='%s', name='%s', type=%s, skippable=%s}",
                id, name, type, skippable);
    }
}
