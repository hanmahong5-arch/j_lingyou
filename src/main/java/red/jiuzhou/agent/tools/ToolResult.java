package red.jiuzhou.agent.tools;

import java.util.List;
import java.util.Map;

/**
 * 工具执行结果（Record）
 *
 * <p>封装Agent工具执行后的返回数据，支持多种结果类型
 *
 * @param success             执行是否成功
 * @param type                结果类型
 * @param message             文本消息
 * @param tableData           表格数据
 * @param columns             表头信息
 * @param affectedRows        影响行数
 * @param generatedSql        生成的SQL语句
 * @param pendingConfirmation 是否需要确认
 * @param previewData         预览数据
 * @param extraData           额外数据
 * @param operationId         操作ID
 */
public record ToolResult(
    boolean success,
    ResultType type,
    String message,
    List<Map<String, Object>> tableData,
    List<String> columns,
    int affectedRows,
    String generatedSql,
    boolean pendingConfirmation,
    List<Map<String, Object>> previewData,
    Map<String, Object> extraData,
    String operationId
) {
    /**
     * 结果类型枚举
     */
    public enum ResultType {
        /** 纯文本消息 */
        TEXT,
        /** 表格数据 */
        TABLE,
        /** JSON数据 */
        JSON,
        /** 待确认操作 */
        PENDING_CONFIRMATION,
        /** 错误信息 */
        ERROR,
        /** 分析报告 */
        ANALYSIS
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建成功的文本结果
     */
    public static ToolResult text(String message) {
        return new ToolResult(true, ResultType.TEXT, message,
            null, null, 0, null, false, null, null, null);
    }

    /**
     * 创建错误结果
     */
    public static ToolResult error(String errorMessage) {
        return new ToolResult(false, ResultType.ERROR, errorMessage,
            null, null, 0, null, false, null, null, null);
    }

    /**
     * 创建表格结果
     */
    public static ToolResult table(List<Map<String, Object>> data, List<String> columns) {
        String message = "查询返回 %d 条记录".formatted(data != null ? data.size() : 0);
        return new ToolResult(true, ResultType.TABLE, message,
            data, columns, 0, null, false, null, null, null);
    }

    /**
     * 创建待确认结果
     */
    public static ToolResult pendingConfirmation(String sql, List<Map<String, Object>> previewData,
                                                  int estimatedRows, String operationId) {
        String message = "将影响 %d 行数据，请确认是否执行".formatted(estimatedRows);
        return new ToolResult(true, ResultType.PENDING_CONFIRMATION, message,
            null, null, estimatedRows, sql, true, previewData, null, operationId);
    }

    /**
     * 创建带自定义消息的待确认结果
     */
    public static ToolResult pendingConfirmation(String sql, List<Map<String, Object>> previewData,
                                                  int estimatedRows, String operationId, String customMessage) {
        return new ToolResult(true, ResultType.PENDING_CONFIRMATION, customMessage,
            null, null, estimatedRows, sql, true, previewData, null, operationId);
    }

    /**
     * 创建分析报告结果
     */
    public static ToolResult analysis(String report, Map<String, Object> extraData) {
        return new ToolResult(true, ResultType.ANALYSIS, report,
            null, null, 0, null, false, null, extraData, null);
    }

    // ========== 兼容性方法（getter别名）==========

    public boolean isSuccess() {
        return success;
    }

    public ResultType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public List<Map<String, Object>> getTableData() {
        return tableData;
    }

    public List<String> getColumns() {
        return columns;
    }

    public int getAffectedRows() {
        return affectedRows;
    }

    public String getGeneratedSql() {
        return generatedSql;
    }

    public boolean isPendingConfirmation() {
        return pendingConfirmation;
    }

    public List<Map<String, Object>> getPreviewData() {
        return previewData;
    }

    public Map<String, Object> getExtraData() {
        return extraData;
    }

    public String getOperationId() {
        return operationId;
    }

    @Override
    public String toString() {
        return "ToolResult{success=%s, type=%s, message='%s'}".formatted(success, type, message);
    }
}
