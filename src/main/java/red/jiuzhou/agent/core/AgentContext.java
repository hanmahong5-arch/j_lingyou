package red.jiuzhou.agent.core;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent上下文
 *
 * 保存Agent执行过程中的状态和依赖
 * 在工具调用之间传递共享数据
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class AgentContext {

    /** 数据库连接模板 */
    private JdbcTemplate jdbcTemplate;

    /** 当前使用的AI模型名称 */
    private String currentModel;

    /** 会话ID */
    private String sessionId;

    /** 用户ID（可选） */
    private String userId;

    /** 当前选中的数据库Schema */
    private String currentSchema;

    /** 表元数据缓存 */
    private Map<String, TableMetadata> tableMetadataCache;

    /** 会话级别的变量存储 */
    private Map<String, Object> sessionVariables;

    /** 待确认的操作 */
    private PendingOperation pendingOperation;

    /** 是否启用安全模式（默认启用） */
    private boolean safetyMode = true;

    /** 单次操作最大影响行数 */
    private int maxAffectedRows = 1000;

    public AgentContext() {
        this.tableMetadataCache = new HashMap<>();
        this.sessionVariables = new HashMap<>();
    }

    /**
     * 表元数据
     */
    public static class TableMetadata {
        private String tableName;
        private String tableComment;
        private Map<String, ColumnMetadata> columns;
        private long rowCount;

        public TableMetadata(String tableName) {
            this.tableName = tableName;
            this.columns = new HashMap<>();
        }

        // Getters and Setters
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getTableComment() { return tableComment; }
        public void setTableComment(String tableComment) { this.tableComment = tableComment; }
        public Map<String, ColumnMetadata> getColumns() { return columns; }
        public void setColumns(Map<String, ColumnMetadata> columns) { this.columns = columns; }
        public long getRowCount() { return rowCount; }
        public void setRowCount(long rowCount) { this.rowCount = rowCount; }

        public void addColumn(ColumnMetadata column) {
            this.columns.put(column.getColumnName(), column);
        }
    }

    /**
     * 列元数据
     */
    public static class ColumnMetadata {
        private String columnName;
        private String columnType;
        private String columnComment;
        private boolean isPrimaryKey;
        private boolean isNullable;
        private String defaultValue;

        // Getters and Setters
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getColumnType() { return columnType; }
        public void setColumnType(String columnType) { this.columnType = columnType; }
        public String getColumnComment() { return columnComment; }
        public void setColumnComment(String columnComment) { this.columnComment = columnComment; }
        public boolean isPrimaryKey() { return isPrimaryKey; }
        public void setPrimaryKey(boolean primaryKey) { isPrimaryKey = primaryKey; }
        public boolean isNullable() { return isNullable; }
        public void setNullable(boolean nullable) { isNullable = nullable; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    }

    /**
     * 待确认的操作
     */
    public static class PendingOperation {
        private String operationId;
        private String sql;
        private String operationType;  // UPDATE, INSERT, DELETE
        private int estimatedAffectedRows;
        private long timestamp;
        private Map<String, Object> previewData;

        // Getters and Setters
        public String getOperationId() { return operationId; }
        public void setOperationId(String operationId) { this.operationId = operationId; }
        public String getSql() { return sql; }
        public void setSql(String sql) { this.sql = sql; }
        public String getOperationType() { return operationType; }
        public void setOperationType(String operationType) { this.operationType = operationType; }
        public int getEstimatedAffectedRows() { return estimatedAffectedRows; }
        public void setEstimatedAffectedRows(int estimatedAffectedRows) { this.estimatedAffectedRows = estimatedAffectedRows; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public Map<String, Object> getPreviewData() { return previewData; }
        public void setPreviewData(Map<String, Object> previewData) { this.previewData = previewData; }
    }

    // ========== 便捷方法 ==========

    /**
     * 设置会话变量
     */
    public void setVariable(String key, Object value) {
        sessionVariables.put(key, value);
    }

    /**
     * 获取会话变量
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        return (T) sessionVariables.get(key);
    }

    /**
     * 检查是否有待确认的操作
     */
    public boolean hasPendingOperation() {
        return pendingOperation != null;
    }

    /**
     * 清除待确认的操作
     */
    public void clearPendingOperation() {
        this.pendingOperation = null;
    }

    /**
     * 缓存表元数据
     */
    public void cacheTableMetadata(String tableName, TableMetadata metadata) {
        tableMetadataCache.put(tableName.toLowerCase(), metadata);
    }

    /**
     * 获取缓存的表元数据
     */
    public TableMetadata getCachedTableMetadata(String tableName) {
        return tableMetadataCache.get(tableName.toLowerCase());
    }

    // ========== Getter/Setter ==========

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String getCurrentModel() {
        return currentModel;
    }

    public void setCurrentModel(String currentModel) {
        this.currentModel = currentModel;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCurrentSchema() {
        return currentSchema;
    }

    public void setCurrentSchema(String currentSchema) {
        this.currentSchema = currentSchema;
    }

    public Map<String, TableMetadata> getTableMetadataCache() {
        return tableMetadataCache;
    }

    public Map<String, Object> getSessionVariables() {
        return sessionVariables;
    }

    public PendingOperation getPendingOperation() {
        return pendingOperation;
    }

    public void setPendingOperation(PendingOperation pendingOperation) {
        this.pendingOperation = pendingOperation;
    }

    public boolean isSafetyMode() {
        return safetyMode;
    }

    public void setSafetyMode(boolean safetyMode) {
        this.safetyMode = safetyMode;
    }

    public int getMaxAffectedRows() {
        return maxAffectedRows;
    }

    public void setMaxAffectedRows(int maxAffectedRows) {
        this.maxAffectedRows = maxAffectedRows;
    }
}
