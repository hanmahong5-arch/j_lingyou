package red.jiuzhou.agent.tools;

import java.util.List;
import java.util.Map;

/**
 * 工具执行结果
 *
 * 封装Agent工具执行后的返回数据
 * 支持多种结果类型：文本、表格、JSON等
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class ToolResult {

    /** 执行是否成功 */
    private boolean success;

    /** 结果类型 */
    private ResultType type;

    /** 文本消息（用于简单结果或错误信息） */
    private String message;

    /** 表格数据（用于查询结果） */
    private List<Map<String, Object>> tableData;

    /** 表头信息 */
    private List<String> columns;

    /** 影响行数（用于修改操作） */
    private int affectedRows;

    /** 生成的SQL语句 */
    private String generatedSql;

    /** 是否需要确认 */
    private boolean pendingConfirmation;

    /** 预览数据（修改前的数据快照） */
    private List<Map<String, Object>> previewData;

    /** 额外数据（灵活存储其他信息） */
    private Map<String, Object> extraData;

    /** 操作ID（用于后续确认或回滚） */
    private String operationId;

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
        ToolResult result = new ToolResult();
        result.success = true;
        result.type = ResultType.TEXT;
        result.message = message;
        return result;
    }

    /**
     * 创建错误结果
     */
    public static ToolResult error(String errorMessage) {
        ToolResult result = new ToolResult();
        result.success = false;
        result.type = ResultType.ERROR;
        result.message = errorMessage;
        return result;
    }

    /**
     * 创建表格结果
     */
    public static ToolResult table(List<Map<String, Object>> data, List<String> columns) {
        ToolResult result = new ToolResult();
        result.success = true;
        result.type = ResultType.TABLE;
        result.tableData = data;
        result.columns = columns;
        result.message = String.format("查询返回 %d 条记录", data != null ? data.size() : 0);
        return result;
    }

    /**
     * 创建待确认结果
     */
    public static ToolResult pendingConfirmation(String sql, List<Map<String, Object>> previewData,
                                                  int estimatedRows, String operationId) {
        ToolResult result = new ToolResult();
        result.success = true;
        result.type = ResultType.PENDING_CONFIRMATION;
        result.pendingConfirmation = true;
        result.generatedSql = sql;
        result.previewData = previewData;
        result.affectedRows = estimatedRows;
        result.operationId = operationId;
        result.message = String.format("将影响 %d 行数据，请确认是否执行", estimatedRows);
        return result;
    }

    /**
     * 创建分析报告结果
     */
    public static ToolResult analysis(String report, Map<String, Object> extraData) {
        ToolResult result = new ToolResult();
        result.success = true;
        result.type = ResultType.ANALYSIS;
        result.message = report;
        result.extraData = extraData;
        return result;
    }

    // ========== Getter/Setter ==========

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public ResultType getType() {
        return type;
    }

    public void setType(ResultType type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<Map<String, Object>> getTableData() {
        return tableData;
    }

    public void setTableData(List<Map<String, Object>> tableData) {
        this.tableData = tableData;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public int getAffectedRows() {
        return affectedRows;
    }

    public void setAffectedRows(int affectedRows) {
        this.affectedRows = affectedRows;
    }

    public String getGeneratedSql() {
        return generatedSql;
    }

    public void setGeneratedSql(String generatedSql) {
        this.generatedSql = generatedSql;
    }

    public boolean isPendingConfirmation() {
        return pendingConfirmation;
    }

    public void setPendingConfirmation(boolean pendingConfirmation) {
        this.pendingConfirmation = pendingConfirmation;
    }

    public List<Map<String, Object>> getPreviewData() {
        return previewData;
    }

    public void setPreviewData(List<Map<String, Object>> previewData) {
        this.previewData = previewData;
    }

    public Map<String, Object> getExtraData() {
        return extraData;
    }

    public void setExtraData(Map<String, Object> extraData) {
        this.extraData = extraData;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    @Override
    public String toString() {
        return String.format("ToolResult{success=%s, type=%s, message='%s'}",
                success, type, message);
    }
}
