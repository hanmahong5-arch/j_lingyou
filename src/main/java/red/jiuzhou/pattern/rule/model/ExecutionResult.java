package red.jiuzhou.pattern.rule.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则执行结果
 *
 * 记录执行过程和结果，支持回滚
 */
public class ExecutionResult {

    /** 执行ID（唯一标识本次执行） */
    private String executionId;

    /** 规则ID */
    private String ruleId;

    /** 规则名称 */
    private String ruleName;

    /** 执行是否成功 */
    private boolean success = true;

    /** 影响的记录数 */
    private int affectedCount;

    /** 执行开始时间 */
    private LocalDateTime startTime;

    /** 执行结束时间 */
    private LocalDateTime endTime;

    /** 执行耗时（毫秒） */
    private long durationMs;

    /** 错误消息（如果失败） */
    private String errorMessage;

    /** 执行的SQL语句（用于审计） */
    private List<String> executedSqls = new ArrayList<>();

    /** 回滚SQL语句（用于撤销） */
    private List<String> rollbackSqls = new ArrayList<>();

    /** 回滚数据（原始记录快照） */
    private List<PreviewResult.RecordChange> rollbackData = new ArrayList<>();

    /** 是否已回滚 */
    private boolean rolledBack = false;

    /** 回滚时间 */
    private LocalDateTime rollbackTime;

    /** 执行者 */
    private String executedBy;

    /** 目标表名 */
    private String targetTable;

    public ExecutionResult() {
        this.executionId = java.util.UUID.randomUUID().toString();
        this.startTime = LocalDateTime.now();
    }

    public ExecutionResult(String ruleId, String ruleName) {
        this();
        this.ruleId = ruleId;
        this.ruleName = ruleName;
    }

    /**
     * 标记执行完成
     */
    public void markComplete(int affectedCount) {
        this.endTime = LocalDateTime.now();
        this.affectedCount = affectedCount;
        this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.success = true;
    }

    /**
     * 标记执行失败
     */
    public void markFailed(String error) {
        this.endTime = LocalDateTime.now();
        this.durationMs = java.time.Duration.between(startTime, endTime).toMillis();
        this.success = false;
        this.errorMessage = error;
    }

    /**
     * 标记已回滚
     */
    public void markRolledBack() {
        this.rolledBack = true;
        this.rollbackTime = LocalDateTime.now();
    }

    /**
     * 添加执行的SQL
     */
    public void addExecutedSql(String sql) {
        this.executedSqls.add(sql);
    }

    /**
     * 添加回滚SQL
     */
    public void addRollbackSql(String sql) {
        this.rollbackSqls.add(sql);
    }

    /**
     * 是否可以回滚
     */
    public boolean canRollback() {
        return success && !rolledBack && !rollbackSqls.isEmpty();
    }

    /**
     * 生成执行摘要
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(ruleName).append("】执行结果\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

        if (success) {
            sb.append("✅ 执行成功\n");
            sb.append("影响记录: ").append(affectedCount).append(" 条\n");
            sb.append("执行耗时: ").append(durationMs).append(" ms\n");

            if (canRollback()) {
                sb.append("状态: 可回滚\n");
            } else if (rolledBack) {
                sb.append("状态: 已回滚 (").append(formatDateTime(rollbackTime)).append(")\n");
            }
        } else {
            sb.append("❌ 执行失败\n");
            sb.append("错误: ").append(errorMessage).append("\n");
        }

        sb.append("\n执行时间: ").append(formatDateTime(startTime));
        if (endTime != null) {
            sb.append(" - ").append(formatDateTime(endTime));
        }

        return sb.toString();
    }

    /**
     * 生成审计日志条目
     */
    public String toAuditLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(formatDateTime(startTime)).append("] ");
        sb.append("规则: ").append(ruleName);
        sb.append(" | 执行者: ").append(executedBy != null ? executedBy : "system");
        sb.append(" | 结果: ").append(success ? "成功" : "失败");
        sb.append(" | 影响: ").append(affectedCount).append("条");
        if (targetTable != null) {
            sb.append(" | 表: ").append(targetTable);
        }
        if (!success) {
            sb.append(" | 错误: ").append(errorMessage);
        }
        return sb.toString();
    }

    private String formatDateTime(LocalDateTime dt) {
        if (dt == null) return "";
        return dt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // Getters and Setters
    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public int getAffectedCount() { return affectedCount; }
    public void setAffectedCount(int affectedCount) { this.affectedCount = affectedCount; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public List<String> getExecutedSqls() { return executedSqls; }
    public void setExecutedSqls(List<String> executedSqls) { this.executedSqls = executedSqls; }

    public List<String> getRollbackSqls() { return rollbackSqls; }
    public void setRollbackSqls(List<String> rollbackSqls) { this.rollbackSqls = rollbackSqls; }

    public List<PreviewResult.RecordChange> getRollbackData() { return rollbackData; }
    public void setRollbackData(List<PreviewResult.RecordChange> rollbackData) { this.rollbackData = rollbackData; }

    public boolean isRolledBack() { return rolledBack; }
    public void setRolledBack(boolean rolledBack) { this.rolledBack = rolledBack; }

    public LocalDateTime getRollbackTime() { return rollbackTime; }
    public void setRollbackTime(LocalDateTime rollbackTime) { this.rollbackTime = rollbackTime; }

    public String getExecutedBy() { return executedBy; }
    public void setExecutedBy(String executedBy) { this.executedBy = executedBy; }

    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }
}
