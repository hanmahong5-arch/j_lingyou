package red.jiuzhou.agent.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import red.jiuzhou.agent.core.AgentContext;
import red.jiuzhou.agent.history.OperationLog;
import red.jiuzhou.agent.history.OperationLogger;
import red.jiuzhou.agent.security.SqlSecurityFilter;

/**
 * 操作执行器
 *
 * 负责实际执行SQL修改操作
 * 支持事务和回滚
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class OperationExecutor {

    private static final Logger log = LoggerFactory.getLogger(OperationExecutor.class);

    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;
    private OperationLogger operationLogger;
    private SqlSecurityFilter securityFilter;

    public OperationExecutor() {
    }

    public OperationExecutor(JdbcTemplate jdbcTemplate, OperationLogger operationLogger) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationLogger = operationLogger;
        this.securityFilter = new SqlSecurityFilter(jdbcTemplate);
    }

    /**
     * 执行结果
     */
    public static class ExecutionResult {
        private boolean success;
        private String message;
        private int affectedRows;
        private String operationId;
        private String errorMessage;
        private long executionTime;

        public static ExecutionResult success(int affectedRows, String operationId) {
            ExecutionResult result = new ExecutionResult();
            result.success = true;
            result.affectedRows = affectedRows;
            result.operationId = operationId;
            result.message = String.format("操作成功，影响 %d 行数据", affectedRows);
            return result;
        }

        public static ExecutionResult failure(String errorMessage) {
            ExecutionResult result = new ExecutionResult();
            result.success = false;
            result.errorMessage = errorMessage;
            result.message = "操作失败: " + errorMessage;
            return result;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getAffectedRows() { return affectedRows; }
        public String getOperationId() { return operationId; }
        public String getErrorMessage() { return errorMessage; }
        public long getExecutionTime() { return executionTime; }
        public void setExecutionTime(long executionTime) { this.executionTime = executionTime; }
    }

    /**
     * 执行待确认的操作
     *
     * @param context Agent上下文
     * @return 执行结果
     */
    public ExecutionResult executePending(AgentContext context) {
        AgentContext.PendingOperation pending = context.getPendingOperation();
        if (pending == null) {
            return ExecutionResult.failure("没有待确认的操作");
        }

        String operationId = pending.getOperationId();
        String sql = pending.getSql();
        String operationType = pending.getOperationType();
        String sessionId = context.getSessionId();

        log.info("执行待确认操作: {}, SQL: {}", operationId, sql);

        // 清除待确认状态
        context.clearPendingOperation();

        // 执行操作
        return execute(sql, operationType, operationId, sessionId);
    }

    /**
     * 直接执行SQL
     *
     * @param sql SQL语句
     * @param operationType 操作类型
     * @param operationId 操作ID
     * @param sessionId 会话ID
     * @return 执行结果
     */
    public ExecutionResult execute(String sql, String operationType, String operationId, String sessionId) {
        if (jdbcTemplate == null) {
            return ExecutionResult.failure("数据库连接未配置");
        }

        // 安全检查
        if (securityFilter != null) {
            SqlSecurityFilter.ValidationResult validation = securityFilter.validate(sql);
            if (!validation.isValid()) {
                return ExecutionResult.failure("安全检查失败: " + validation.getMessage());
            }
        }

        long startTime = System.currentTimeMillis();
        OperationLog opLog = null;

        // 开始记录日志
        if (operationLogger != null) {
            opLog = operationLogger.startOperation(operationId, sql, operationType, sessionId);
        }

        try {
            int affectedRows;

            // 使用事务执行
            if (transactionTemplate != null) {
                affectedRows = transactionTemplate.execute(status -> {
                    try {
                        return jdbcTemplate.update(sql);
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        throw e;
                    }
                });
            } else {
                affectedRows = jdbcTemplate.update(sql);
            }

            long executionTime = System.currentTimeMillis() - startTime;

            // 记录成功
            if (opLog != null) {
                opLog.setExecutionTime(executionTime);
                operationLogger.completeOperation(opLog, affectedRows, true, null);
            }

            ExecutionResult result = ExecutionResult.success(affectedRows, operationId);
            result.setExecutionTime(executionTime);

            log.info("操作执行成功: {} 影响 {} 行, 耗时 {}ms",
                operationId, affectedRows, executionTime);

            return result;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            String errorMsg = e.getMessage();

            // 记录失败
            if (opLog != null) {
                opLog.setExecutionTime(executionTime);
                operationLogger.completeOperation(opLog, 0, false, errorMsg);
            }

            log.error("操作执行失败: {}", errorMsg, e);
            return ExecutionResult.failure(errorMsg);
        }
    }

    /**
     * 取消待确认的操作
     *
     * @param context Agent上下文
     * @return 是否成功取消
     */
    public boolean cancelPending(AgentContext context) {
        AgentContext.PendingOperation pending = context.getPendingOperation();
        if (pending != null) {
            log.info("取消待确认操作: {}", pending.getOperationId());
            context.clearPendingOperation();
            return true;
        }
        return false;
    }

    /**
     * 执行回滚
     *
     * @param operationId 要回滚的操作ID
     * @param sessionId 会话ID
     * @return 执行结果
     */
    public ExecutionResult rollback(String operationId, String sessionId) {
        if (operationLogger == null) {
            return ExecutionResult.failure("操作日志服务未配置");
        }

        OperationLogger.RollbackResult rollbackResult = operationLogger.rollback(operationId);

        if (rollbackResult.isSuccess()) {
            return ExecutionResult.success(
                rollbackResult.getAffectedRows(),
                rollbackResult.getRollbackOperationId()
            );
        } else {
            return ExecutionResult.failure(rollbackResult.getMessage());
        }
    }

    /**
     * 执行SELECT查询
     *
     * @param sql SELECT语句
     * @return 查询结果
     */
    public java.util.List<java.util.Map<String, Object>> executeQuery(String sql) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("数据库连接未配置");
        }

        // 安全检查
        if (securityFilter != null) {
            SqlSecurityFilter.ValidationResult validation = securityFilter.validate(sql);
            if (!validation.isValid()) {
                throw new IllegalArgumentException("安全检查失败: " + validation.getMessage());
            }
        }

        return jdbcTemplate.queryForList(sql);
    }

    // ========== Getter/Setter ==========

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        if (this.securityFilter != null) {
            this.securityFilter.setJdbcTemplate(jdbcTemplate);
        }
    }

    public void setTransactionTemplate(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    public void setOperationLogger(OperationLogger operationLogger) {
        this.operationLogger = operationLogger;
    }

    public void setSecurityFilter(SqlSecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }
}
