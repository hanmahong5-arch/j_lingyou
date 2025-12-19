package red.jiuzhou.agent.history;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 操作日志记录器
 *
 * 记录所有数据修改操作，支持回滚
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class OperationLogger {

    private static final Logger log = LoggerFactory.getLogger(OperationLogger.class);

    /** 内存日志存储（按操作ID索引） */
    private final Map<String, OperationLog> logStore = new ConcurrentHashMap<>();

    /** 按时间排序的日志列表 */
    private final List<OperationLog> logList = Collections.synchronizedList(new ArrayList<>());

    /** JdbcTemplate */
    private JdbcTemplate jdbcTemplate;

    /** 是否持久化到数据库 */
    private boolean persistToDatabase = false;

    /** 最大保留日志数 */
    private int maxLogCount = 1000;

    /** 日期格式化器 */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public OperationLogger() {
    }

    public OperationLogger(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 记录操作开始（获取操作前状态）
     *
     * @param operationId 操作ID
     * @param sql SQL语句
     * @param operationType 操作类型
     * @param sessionId 会话ID
     * @return 操作日志对象
     */
    public OperationLog startOperation(String operationId, String sql, String operationType, String sessionId) {
        OperationLog opLog = new OperationLog(operationId);
        opLog.setSessionId(sessionId);
        opLog.setSqlStatement(sql);
        opLog.setOperationType(operationType);
        opLog.setTableName(extractTableName(sql));

        // 捕获操作前状态
        if (jdbcTemplate != null && ("UPDATE".equals(operationType) || "DELETE".equals(operationType))) {
            try {
                String beforeSql = buildSelectSql(sql, operationType);
                if (beforeSql != null) {
                    List<Map<String, Object>> beforeData = jdbcTemplate.queryForList(beforeSql);
                    opLog.setBeforeState(JSON.toJSONString(beforeData));

                    // 生成回滚SQL
                    String rollbackSql = generateRollbackSql(sql, operationType, beforeData);
                    opLog.setRollbackSql(rollbackSql);
                }
            } catch (Exception e) {
                log.warn("捕获操作前状态失败: {}", e.getMessage());
            }
        }

        return opLog;
    }

    /**
     * 记录操作完成
     *
     * @param opLog 操作日志对象
     * @param affectedRows 影响行数
     * @param success 是否成功
     * @param errorMessage 错误信息
     */
    public void completeOperation(OperationLog opLog, int affectedRows, boolean success, String errorMessage) {
        opLog.setAffectedRows(affectedRows);
        opLog.setSuccess(success);
        opLog.setErrorMessage(errorMessage);

        // 捕获操作后状态
        if (success && jdbcTemplate != null && opLog.getBeforeState() != null) {
            try {
                String afterSql = buildSelectSql(opLog.getSqlStatement(), opLog.getOperationType());
                if (afterSql != null) {
                    List<Map<String, Object>> afterData = jdbcTemplate.queryForList(afterSql);
                    opLog.setAfterState(JSON.toJSONString(afterData));
                }
            } catch (Exception e) {
                log.debug("捕获操作后状态失败: {}", e.getMessage());
            }
        }

        // 存储日志
        saveLog(opLog);

        log.info("操作记录完成: {}", opLog.getSummary());
    }

    /**
     * 保存日志
     */
    private void saveLog(OperationLog opLog) {
        logStore.put(opLog.getOperationId(), opLog);
        logList.add(opLog);

        // 清理旧日志
        if (logList.size() > maxLogCount) {
            OperationLog oldest = logList.remove(0);
            logStore.remove(oldest.getOperationId());
        }

        // 持久化到数据库
        if (persistToDatabase && jdbcTemplate != null) {
            persistLog(opLog);
        }
    }

    /**
     * 持久化日志到数据库
     */
    private void persistLog(OperationLog opLog) {
        try {
            String sql = "INSERT INTO agent_operation_log " +
                "(operation_id, session_id, operation_type, table_name, sql_statement, " +
                "description, before_state, after_state, rollback_sql, affected_rows, " +
                "success, error_message, timestamp, execution_time, user_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            jdbcTemplate.update(sql,
                opLog.getOperationId(),
                opLog.getSessionId(),
                opLog.getOperationType(),
                opLog.getTableName(),
                opLog.getSqlStatement(),
                opLog.getDescription(),
                opLog.getBeforeState(),
                opLog.getAfterState(),
                opLog.getRollbackSql(),
                opLog.getAffectedRows(),
                opLog.isSuccess(),
                opLog.getErrorMessage(),
                opLog.getTimestamp(),
                opLog.getExecutionTime(),
                opLog.getUserId()
            );
        } catch (Exception e) {
            log.warn("持久化日志失败: {}", e.getMessage());
        }
    }

    /**
     * 获取操作日志
     */
    public OperationLog getLog(String operationId) {
        return logStore.get(operationId);
    }

    /**
     * 获取最近的日志
     */
    public List<OperationLog> getRecentLogs(int limit) {
        int size = logList.size();
        int start = Math.max(0, size - limit);
        List<OperationLog> result = new ArrayList<>();
        for (int i = size - 1; i >= start; i--) {
            result.add(logList.get(i));
        }
        return result;
    }

    /**
     * 获取指定时间之后的日志
     */
    public List<OperationLog> getLogsSince(String sinceTime, int limit) {
        try {
            LocalDateTime since = LocalDateTime.parse(sinceTime, FORMATTER);
            return logList.stream()
                .filter(log -> log.getTimestamp().isAfter(since))
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("解析时间失败: {}", sinceTime);
            return getRecentLogs(limit);
        }
    }

    /**
     * 获取会话的所有日志
     */
    public List<OperationLog> getSessionLogs(String sessionId) {
        return logList.stream()
            .filter(log -> sessionId.equals(log.getSessionId()))
            .collect(Collectors.toList());
    }

    /**
     * 执行回滚
     *
     * @param operationId 要回滚的操作ID
     * @return 回滚结果
     */
    public RollbackResult rollback(String operationId) {
        OperationLog opLog = logStore.get(operationId);
        if (opLog == null) {
            return RollbackResult.failure("未找到操作记录: " + operationId);
        }

        if (!opLog.isSuccess()) {
            return RollbackResult.failure("该操作执行失败，无需回滚");
        }

        String rollbackSql = opLog.getRollbackSql();
        if (rollbackSql == null || rollbackSql.isEmpty()) {
            return RollbackResult.failure("该操作没有生成回滚SQL");
        }

        if (jdbcTemplate == null) {
            return RollbackResult.failure("数据库连接未配置");
        }

        try {
            int affected = jdbcTemplate.update(rollbackSql);

            // 记录回滚操作
            String rollbackOpId = UUID.randomUUID().toString().substring(0, 8);
            OperationLog rollbackLog = new OperationLog(rollbackOpId);
            rollbackLog.setOperationType("ROLLBACK");
            rollbackLog.setTableName(opLog.getTableName());
            rollbackLog.setSqlStatement(rollbackSql);
            rollbackLog.setDescription("回滚操作: " + operationId);
            rollbackLog.setAffectedRows(affected);
            rollbackLog.setSuccess(true);
            saveLog(rollbackLog);

            return RollbackResult.success(affected, rollbackOpId);

        } catch (Exception e) {
            log.error("回滚失败", e);
            return RollbackResult.failure("回滚执行失败: " + e.getMessage());
        }
    }

    /**
     * 回滚结果
     */
    public static class RollbackResult {
        private boolean success;
        private String message;
        private int affectedRows;
        private String rollbackOperationId;

        public static RollbackResult success(int affectedRows, String rollbackOpId) {
            RollbackResult result = new RollbackResult();
            result.success = true;
            result.affectedRows = affectedRows;
            result.rollbackOperationId = rollbackOpId;
            result.message = String.format("回滚成功，恢复了 %d 行数据", affectedRows);
            return result;
        }

        public static RollbackResult failure(String message) {
            RollbackResult result = new RollbackResult();
            result.success = false;
            result.message = message;
            return result;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getAffectedRows() { return affectedRows; }
        public String getRollbackOperationId() { return rollbackOperationId; }
    }

    /**
     * 从SQL中提取表名
     */
    private String extractTableName(String sql) {
        Pattern[] patterns = {
            Pattern.compile("FROM\\s+`?(\\w+)`?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("UPDATE\\s+`?(\\w+)`?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("INTO\\s+`?(\\w+)`?", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(sql);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return "unknown";
    }

    /**
     * 构建SELECT语句获取将被影响的数据
     */
    private String buildSelectSql(String sql, String operationType) {
        String upperSql = sql.toUpperCase().trim();
        String tableName = extractTableName(sql);

        if ("UPDATE".equals(operationType)) {
            int whereIdx = upperSql.indexOf("WHERE");
            if (whereIdx > 0) {
                String whereClause = sql.substring(sql.toUpperCase().indexOf("WHERE"));
                return String.format("SELECT * FROM `%s` %s LIMIT 100", tableName, whereClause);
            }
        } else if ("DELETE".equals(operationType)) {
            int whereIdx = upperSql.indexOf("WHERE");
            if (whereIdx > 0) {
                String whereClause = sql.substring(sql.toUpperCase().indexOf("WHERE"));
                return String.format("SELECT * FROM `%s` %s LIMIT 100", tableName, whereClause);
            }
        }

        return null;
    }

    /**
     * 生成回滚SQL
     */
    private String generateRollbackSql(String sql, String operationType, List<Map<String, Object>> beforeData) {
        if (beforeData == null || beforeData.isEmpty()) {
            return null;
        }

        String tableName = extractTableName(sql);

        if ("UPDATE".equals(operationType)) {
            // 生成UPDATE回滚（恢复原值）
            return generateUpdateRollback(sql, tableName, beforeData);
        } else if ("DELETE".equals(operationType)) {
            // 生成INSERT回滚（重新插入删除的数据）
            return generateInsertRollback(tableName, beforeData);
        }

        return null;
    }

    /**
     * 生成UPDATE的回滚SQL
     */
    private String generateUpdateRollback(String originalSql, String tableName, List<Map<String, Object>> beforeData) {
        if (beforeData.isEmpty()) {
            return null;
        }

        // 找到主键字段
        String pkField = findPrimaryKeyField(beforeData.get(0));
        if (pkField == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for (Map<String, Object> row : beforeData) {
            Object pkValue = row.get(pkField);
            if (pkValue == null) continue;

            sb.append("UPDATE `").append(tableName).append("` SET ");

            boolean first = true;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equals(pkField)) continue;

                if (!first) sb.append(", ");
                first = false;

                sb.append("`").append(entry.getKey()).append("` = ");
                sb.append(formatValue(entry.getValue()));
            }

            sb.append(" WHERE `").append(pkField).append("` = ");
            sb.append(formatValue(pkValue));
            sb.append(";\n");
        }

        return sb.toString().trim();
    }

    /**
     * 生成DELETE的回滚SQL（INSERT）
     */
    private String generateInsertRollback(String tableName, List<Map<String, Object>> beforeData) {
        if (beforeData.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        for (Map<String, Object> row : beforeData) {
            sb.append("INSERT INTO `").append(tableName).append("` (");

            // 列名
            sb.append(row.keySet().stream()
                .map(k -> "`" + k + "`")
                .collect(Collectors.joining(", ")));

            sb.append(") VALUES (");

            // 值
            sb.append(row.values().stream()
                .map(this::formatValue)
                .collect(Collectors.joining(", ")));

            sb.append(");\n");
        }

        return sb.toString().trim();
    }

    /**
     * 查找主键字段
     */
    private String findPrimaryKeyField(Map<String, Object> row) {
        // 常见的主键字段名
        String[] pkCandidates = {"id", "ID", "Id", "pk", "PK"};

        for (String pk : pkCandidates) {
            if (row.containsKey(pk)) {
                return pk;
            }
        }

        // 返回第一个字段
        if (!row.isEmpty()) {
            return row.keySet().iterator().next();
        }

        return null;
    }

    /**
     * 格式化SQL值
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof Number) {
            return value.toString();
        }

        if (value instanceof Boolean) {
            return (Boolean) value ? "1" : "0";
        }

        // 字符串类型需要转义
        String str = value.toString();
        str = str.replace("'", "''");
        return "'" + str + "'";
    }

    // ========== Getter/Setter ==========

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isPersistToDatabase() {
        return persistToDatabase;
    }

    public void setPersistToDatabase(boolean persistToDatabase) {
        this.persistToDatabase = persistToDatabase;
    }

    public int getMaxLogCount() {
        return maxLogCount;
    }

    public void setMaxLogCount(int maxLogCount) {
        this.maxLogCount = maxLogCount;
    }

    public int getLogCount() {
        return logList.size();
    }
}
