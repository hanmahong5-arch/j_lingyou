package red.jiuzhou.agent.history;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 操作日志数据模型
 *
 * 记录每次数据修改操作的详细信息
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class OperationLog {

    /** 操作ID（唯一标识） */
    private String operationId;

    /** 会话ID */
    private String sessionId;

    /** 操作类型：UPDATE, INSERT, DELETE, ROLLBACK */
    private String operationType;

    /** 目标表名 */
    private String tableName;

    /** 执行的SQL语句 */
    private String sqlStatement;

    /** 操作描述 */
    private String description;

    /** 操作前的数据快照（JSON格式） */
    private String beforeState;

    /** 操作后的数据快照（JSON格式） */
    private String afterState;

    /** 回滚SQL */
    private String rollbackSql;

    /** 影响的行数 */
    private int affectedRows;

    /** 是否执行成功 */
    private boolean success;

    /** 错误信息（如果失败） */
    private String errorMessage;

    /** 操作时间 */
    private LocalDateTime timestamp;

    /** 执行耗时（毫秒） */
    private long executionTime;

    /** 操作用户（可选） */
    private String userId;

    /** 日期格式化器 */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public OperationLog() {
        this.timestamp = LocalDateTime.now();
    }

    public OperationLog(String operationId) {
        this();
        this.operationId = operationId;
    }

    // ========== 便捷方法 ==========

    /**
     * 获取格式化的时间戳
     */
    public String getFormattedTimestamp() {
        return timestamp != null ? timestamp.format(FORMATTER) : "";
    }

    /**
     * 是否可以回滚
     */
    public boolean isRollbackable() {
        return success && rollbackSql != null && !rollbackSql.isEmpty();
    }

    /**
     * 获取操作摘要
     */
    public String getSummary() {
        return String.format("[%s] %s %s - %d rows %s",
            getFormattedTimestamp(),
            operationType,
            tableName,
            affectedRows,
            success ? "✓" : "✗");
    }

    // ========== Getter/Setter ==========

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSqlStatement() {
        return sqlStatement;
    }

    public void setSqlStatement(String sqlStatement) {
        this.sqlStatement = sqlStatement;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBeforeState() {
        return beforeState;
    }

    public void setBeforeState(String beforeState) {
        this.beforeState = beforeState;
    }

    public String getAfterState() {
        return afterState;
    }

    public void setAfterState(String afterState) {
        this.afterState = afterState;
    }

    public String getRollbackSql() {
        return rollbackSql;
    }

    public void setRollbackSql(String rollbackSql) {
        this.rollbackSql = rollbackSql;
    }

    public int getAffectedRows() {
        return affectedRows;
    }

    public void setAffectedRows(int affectedRows) {
        this.affectedRows = affectedRows;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
