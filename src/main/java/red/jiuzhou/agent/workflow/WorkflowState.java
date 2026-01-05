package red.jiuzhou.agent.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.context.DesignContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流状态机
 *
 * <p>管理工作流的完整状态，包括：
 * <ul>
 *   <li>步骤列表和当前步骤索引</li>
 *   <li>每个步骤的执行结果</li>
 *   <li>用户修正历史</li>
 *   <li>设计上下文</li>
 *   <li>审计日志追溯</li>
 *   <li>操作记录和撤销支持</li>
 * </ul>
 *
 * @author Claude
 * @version 1.1 - 增加追溯和撤销支持
 */
public class WorkflowState {

    private static final Logger log = LoggerFactory.getLogger(WorkflowState.class);

    /**
     * 工作流状态枚举
     */
    public enum Status {
        CREATED,        // 已创建
        RUNNING,        // 运行中
        WAITING,        // 等待用户确认
        COMPLETED,      // 已完成
        CANCELLED,      // 已取消
        FAILED          // 失败
    }

    // 基础信息
    private final String workflowId;
    private final String workflowType;
    private final Instant startTime;
    private Status status = Status.CREATED;

    // 步骤管理
    private final List<WorkflowStep> steps;
    private int currentStepIndex = 0;

    // 步骤结果存储
    private final Map<String, WorkflowStepResult> stepResults = new HashMap<>();

    // 当前步骤的结果（等待确认时使用）
    private WorkflowStepResult pendingResult;

    // 用户修正历史
    private final List<CorrectionRecord> corrections = new ArrayList<>();

    // 设计上下文
    private DesignContext context;

    // 用户原始意图
    private String userIntent;

    // 统计信息
    private int totalAffectedRows = 0;
    private Instant endTime;

    // 追溯支持
    private final List<OperationRecord> operationRecords = new ArrayList<>();
    private final List<String> snapshotIds = new ArrayList<>();
    private boolean auditEnabled = true;

    // 审计日志和撤销管理器引用
    private final WorkflowAuditLog auditLog = WorkflowAuditLog.getInstance();
    private final UndoManager undoManager = UndoManager.getInstance();
    private final DataSnapshot dataSnapshot = DataSnapshot.getInstance();

    /**
     * 创建工作流状态
     */
    public WorkflowState(String workflowType, List<WorkflowStep> steps) {
        this.workflowId = generateWorkflowId();
        this.workflowType = workflowType;
        this.steps = new ArrayList<>(steps);
        this.startTime = Instant.now();

        // 记录工作流创建
        log.info("创建工作流: id={}, type={}, steps={}", workflowId, workflowType, steps.size());
    }

    private String generateWorkflowId() {
        return "WF-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }

    // ==================== 步骤导航 ====================

    /**
     * 获取当前步骤
     */
    public WorkflowStep getCurrentStep() {
        if (currentStepIndex < 0 || currentStepIndex >= steps.size()) {
            return null;
        }
        return steps.get(currentStepIndex);
    }

    /**
     * 获取当前步骤索引
     */
    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    /**
     * 是否有下一步
     */
    public boolean hasNextStep() {
        return currentStepIndex < steps.size() - 1;
    }

    /**
     * 是否有上一步
     */
    public boolean hasPreviousStep() {
        return currentStepIndex > 0;
    }

    /**
     * 进入下一步
     */
    public void nextStep() {
        if (hasNextStep()) {
            currentStepIndex++;
            pendingResult = null;
        }
    }

    /**
     * 回到上一步
     */
    public void previousStep() {
        if (hasPreviousStep()) {
            currentStepIndex--;
            pendingResult = null;
        }
    }

    /**
     * 跳过当前步骤
     */
    public void skipCurrentStep() {
        WorkflowStep current = getCurrentStep();
        if (current != null && current.skippable()) {
            // 记录跳过状态
            WorkflowStepResult skipped = WorkflowStepResult.success("用户跳过");
            saveStepResult(skipped);
            if (hasNextStep()) {
                nextStep();
            }
        }
    }

    /**
     * 当前步骤是否可跳过
     */
    public boolean isCurrentStepSkippable() {
        WorkflowStep current = getCurrentStep();
        return current != null && current.skippable();
    }

    // ==================== 结果管理 ====================

    /**
     * 设置当前步骤的待确认结果
     */
    public void setPendingResult(WorkflowStepResult result) {
        this.pendingResult = result;
        this.status = Status.WAITING;
    }

    /**
     * 获取当前步骤的待确认结果
     */
    public WorkflowStepResult getPendingResult() {
        return pendingResult;
    }

    /**
     * 保存步骤结果
     */
    public void saveStepResult(WorkflowStepResult result) {
        WorkflowStep current = getCurrentStep();
        if (current != null) {
            stepResults.put(current.id(), result);

            // 累计影响行数
            if (result.getTotalRows() > 0) {
                totalAffectedRows += result.getTotalRows();
            }
        }
        pendingResult = null;
    }

    /**
     * 获取指定步骤的结果
     */
    public WorkflowStepResult getStepResult(String stepId) {
        return stepResults.get(stepId);
    }

    /**
     * 获取上一步骤的结果
     */
    public WorkflowStepResult getPreviousStepResult() {
        if (currentStepIndex > 0) {
            String prevStepId = steps.get(currentStepIndex - 1).id();
            return stepResults.get(prevStepId);
        }
        return null;
    }

    // ==================== 修正管理 ====================

    /**
     * 添加用户修正
     */
    public void addCorrection(String correction) {
        WorkflowStep current = getCurrentStep();
        if (current != null && correction != null && !correction.isBlank()) {
            corrections.add(new CorrectionRecord(
                    current.id(),
                    correction,
                    Instant.now()
            ));

            // 同时更新上下文
            if (context != null) {
                context.addSemanticHint("用户修正", correction);
            }
        }
    }

    /**
     * 获取当前步骤的最新修正
     */
    public String getCurrentCorrection() {
        WorkflowStep current = getCurrentStep();
        if (current == null) return null;

        return corrections.stream()
                .filter(c -> c.stepId().equals(current.id()))
                .reduce((first, second) -> second)  // 取最后一个
                .map(CorrectionRecord::correction)
                .orElse(null);
    }

    /**
     * 获取所有修正
     */
    public List<CorrectionRecord> getAllCorrections() {
        return new ArrayList<>(corrections);
    }

    // ==================== 状态管理 ====================

    /**
     * 标记为运行中
     */
    public void markRunning() {
        this.status = Status.RUNNING;
        if (auditEnabled) {
            auditLog.logWorkflowStarted(workflowId, workflowType, userIntent);
        }
    }

    /**
     * 标记为等待确认
     */
    public void markWaiting() {
        this.status = Status.WAITING;
    }

    /**
     * 标记为完成
     */
    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.endTime = Instant.now();
        if (auditEnabled) {
            auditLog.logWorkflowCompleted(workflowId, totalAffectedRows);
        }
    }

    /**
     * 标记为取消
     */
    public void markCancelled() {
        this.status = Status.CANCELLED;
        this.endTime = Instant.now();
        if (auditEnabled) {
            auditLog.logWorkflowCancelled(workflowId, "用户取消");
        }
    }

    /**
     * 标记为失败
     */
    public void markFailed() {
        markFailed("未知错误");
    }

    /**
     * 标记为失败（带原因）
     */
    public void markFailed(String reason) {
        this.status = Status.FAILED;
        this.endTime = Instant.now();
        if (auditEnabled) {
            auditLog.logWorkflowFailed(workflowId, reason);
        }
    }

    /**
     * 是否已结束
     */
    public boolean isEnded() {
        return status == Status.COMPLETED ||
               status == Status.CANCELLED ||
               status == Status.FAILED;
    }

    // ==================== Getter/Setter ====================

    public String getWorkflowId() {
        return workflowId;
    }

    public String getWorkflowType() {
        return workflowType;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Status getStatus() {
        return status;
    }

    public List<WorkflowStep> getSteps() {
        return new ArrayList<>(steps);
    }

    public int getStepCount() {
        return steps.size();
    }

    public DesignContext getContext() {
        return context;
    }

    public void setContext(DesignContext context) {
        this.context = context;
    }

    public String getUserIntent() {
        return userIntent;
    }

    public void setUserIntent(String userIntent) {
        this.userIntent = userIntent;
    }

    public int getTotalAffectedRows() {
        return totalAffectedRows;
    }

    // ==================== 进度信息 ====================

    /**
     * 获取进度百分比
     */
    public double getProgressPercent() {
        if (steps.isEmpty()) return 0;
        return (double) currentStepIndex / steps.size() * 100;
    }

    /**
     * 获取进度描述
     */
    public String getProgressDescription() {
        return String.format("步骤 %d / %d", currentStepIndex + 1, steps.size());
    }

    // ==================== 追溯和撤销支持 ====================

    /**
     * 记录SQL执行操作（用于追溯和撤销）
     *
     * @param sql 执行的SQL
     * @param affectedRows 影响行数
     * @param snapshotId 数据快照ID（可选）
     * @return 操作ID
     */
    public String recordSqlExecution(String sql, int affectedRows, String snapshotId) {
        WorkflowStep current = getCurrentStep();
        String stepId = current != null ? current.id() : "unknown";

        // 记录到操作历史
        OperationRecord record = new OperationRecord(
                generateOperationId(),
                stepId,
                sql,
                affectedRows,
                snapshotId,
                Instant.now()
        );
        operationRecords.add(record);

        // 记录到审计日志
        if (auditEnabled) {
            auditLog.logSqlExecuted(workflowId, stepId, sql, affectedRows, snapshotId);
        }

        // 记录到撤销管理器
        String operationId = undoManager.recordOperation(workflowId, stepId, sql, affectedRows, snapshotId);

        // 保存快照ID
        if (snapshotId != null && !snapshotId.isEmpty()) {
            snapshotIds.add(snapshotId);
        }

        // 累计影响行数
        totalAffectedRows += affectedRows;

        log.debug("记录SQL执行: stepId={}, sql={}, rows={}", stepId, truncate(sql, 50), affectedRows);

        return operationId;
    }

    /**
     * 在执行修改操作前创建快照
     *
     * @param tableName 表名
     * @param whereClause WHERE条件
     * @param sql 将执行的SQL
     * @return 快照ID
     */
    public String createSnapshotBeforeModify(String tableName, String whereClause, String sql) {
        WorkflowStep current = getCurrentStep();
        String stepId = current != null ? current.id() : "unknown";

        String snapshotId = dataSnapshot.createSnapshot(workflowId, stepId, tableName, whereClause, sql);

        if (snapshotId != null) {
            snapshotIds.add(snapshotId);
            log.info("创建修改前快照: snapshotId={}, table={}", snapshotId, tableName);
        }

        return snapshotId;
    }

    /**
     * 记录步骤开始
     */
    public void logStepStarted() {
        WorkflowStep current = getCurrentStep();
        if (current != null && auditEnabled) {
            auditLog.logStepStarted(workflowId, current.id(), current.name());
        }
    }

    /**
     * 记录步骤确认
     */
    public void logStepConfirmed() {
        WorkflowStep current = getCurrentStep();
        if (current != null && auditEnabled) {
            auditLog.logStepConfirmed(workflowId, current.id());
        }
    }

    /**
     * 记录步骤被修正
     */
    public void logStepCorrected(String correction) {
        WorkflowStep current = getCurrentStep();
        if (current != null && auditEnabled) {
            auditLog.logStepCorrected(workflowId, current.id(), correction);
        }
    }

    /**
     * 记录步骤被跳过
     */
    public void logStepSkipped() {
        WorkflowStep current = getCurrentStep();
        if (current != null && auditEnabled) {
            auditLog.logStepSkipped(workflowId, current.id());
        }
    }

    /**
     * 撤销最近一次操作
     *
     * @return 撤销结果
     */
    public UndoManager.UndoResult undoLastOperation() {
        return undoManager.undoWorkflowLast(workflowId);
    }

    /**
     * 撤销所有操作
     *
     * @return 撤销结果
     */
    public UndoManager.UndoResult undoAllOperations() {
        return undoManager.undoWorkflowAll(workflowId);
    }

    /**
     * 撤销指定步骤及其后续操作
     *
     * @param stepId 步骤ID
     * @return 撤销结果
     */
    public UndoManager.UndoResult undoFromStep(String stepId) {
        return undoManager.undoStepAndAfter(workflowId, stepId);
    }

    /**
     * 获取可撤销操作列表
     */
    public List<UndoManager.UndoableOperation> getUndoableOperations() {
        return undoManager.getWorkflowUndoableOperations(workflowId);
    }

    /**
     * 是否有可撤销的操作
     */
    public boolean canUndo() {
        return undoManager.canUndo(workflowId);
    }

    /**
     * 获取工作流时间线
     */
    public List<WorkflowAuditLog.TimelineEvent> getTimeline() {
        return auditLog.getWorkflowTimeline(workflowId);
    }

    /**
     * 获取所有操作记录
     */
    public List<OperationRecord> getOperationRecords() {
        return new ArrayList<>(operationRecords);
    }

    /**
     * 获取所有快照ID
     */
    public List<String> getSnapshotIds() {
        return new ArrayList<>(snapshotIds);
    }

    /**
     * 启用/禁用审计日志
     */
    public void setAuditEnabled(boolean enabled) {
        this.auditEnabled = enabled;
    }

    /**
     * 是否启用了审计日志
     */
    public boolean isAuditEnabled() {
        return auditEnabled;
    }

    /**
     * 获取工作流执行摘要
     */
    public WorkflowSummary getSummary() {
        return new WorkflowSummary(
                workflowId,
                workflowType,
                userIntent,
                status,
                startTime,
                endTime,
                steps.size(),
                currentStepIndex,
                totalAffectedRows,
                operationRecords.size(),
                corrections.size()
        );
    }

    private String generateOperationId() {
        return "OP-" + workflowId + "-" + (operationRecords.size() + 1);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    // ==================== 辅助类型 ====================

    /**
     * 修正记录
     */
    public record CorrectionRecord(
            String stepId,
            String correction,
            Instant timestamp
    ) {}

    /**
     * 操作记录
     */
    public record OperationRecord(
            String operationId,
            String stepId,
            String sql,
            int affectedRows,
            String snapshotId,
            Instant timestamp
    ) {}

    /**
     * 工作流执行摘要
     */
    public record WorkflowSummary(
            String workflowId,
            String workflowType,
            String userIntent,
            Status status,
            Instant startTime,
            Instant endTime,
            int totalSteps,
            int completedSteps,
            int totalAffectedRows,
            int operationCount,
            int correctionCount
    ) {
        public long getDurationMs() {
            if (startTime == null) return 0;
            Instant end = endTime != null ? endTime : Instant.now();
            return end.toEpochMilli() - startTime.toEpochMilli();
        }

        public String getStatusText() {
            return switch (status) {
                case CREATED -> "已创建";
                case RUNNING -> "运行中";
                case WAITING -> "等待确认";
                case COMPLETED -> "已完成";
                case CANCELLED -> "已取消";
                case FAILED -> "失败";
            };
        }
    }

    @Override
    public String toString() {
        return String.format("WorkflowState{id='%s', type='%s', status=%s, step=%d/%d, ops=%d}",
                workflowId, workflowType, status, currentStepIndex + 1, steps.size(), operationRecords.size());
    }
}
