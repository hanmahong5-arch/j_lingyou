package red.jiuzhou.agent.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流步骤执行结果
 *
 * <p>包含步骤执行后的所有相关数据，用于展示给设计师确认。
 *
 * @author Claude
 * @version 1.0
 */
public class WorkflowStepResult {

    /**
     * 结果状态
     */
    public enum Status {
        SUCCESS,        // 执行成功
        PARTIAL,        // 部分成功
        FAILED,         // 执行失败
        PENDING,        // 等待确认
        CANCELLED       // 已取消
    }

    private Status status = Status.PENDING;
    private String message;
    private String summary;

    // 查询结果数据
    private List<Map<String, Object>> queryData;
    private int totalRows;

    // 修改前后对比数据
    private List<Map<String, Object>> beforeData;
    private List<Map<String, Object>> afterData;
    private List<String> modifiedColumns;

    // AI解析结果
    private String parsedIntent;        // AI理解的意图
    private String generatedSql;        // 生成的SQL
    private Map<String, Object> parsedConditions;  // 解析的条件

    // 用户选择（对比视图中用户勾选的行）
    private List<Integer> confirmedRowIndices;

    // 额外数据
    private Map<String, Object> extra = new HashMap<>();

    // ==================== 构建方法 ====================

    public static WorkflowStepResult success(String message) {
        WorkflowStepResult result = new WorkflowStepResult();
        result.status = Status.SUCCESS;
        result.message = message;
        return result;
    }

    public static WorkflowStepResult pending(String message) {
        WorkflowStepResult result = new WorkflowStepResult();
        result.status = Status.PENDING;
        result.message = message;
        return result;
    }

    public static WorkflowStepResult failed(String message) {
        WorkflowStepResult result = new WorkflowStepResult();
        result.status = Status.FAILED;
        result.message = message;
        return result;
    }

    // ==================== 链式设置方法 ====================

    public WorkflowStepResult withSummary(String summary) {
        this.summary = summary;
        return this;
    }

    public WorkflowStepResult withQueryData(List<Map<String, Object>> data) {
        this.queryData = data;
        this.totalRows = data != null ? data.size() : 0;
        return this;
    }

    public WorkflowStepResult withTotalRows(int totalRows) {
        this.totalRows = totalRows;
        return this;
    }

    public WorkflowStepResult withComparisonData(
            List<Map<String, Object>> before,
            List<Map<String, Object>> after,
            List<String> modifiedColumns) {
        this.beforeData = before;
        this.afterData = after;
        this.modifiedColumns = modifiedColumns;
        return this;
    }

    public WorkflowStepResult withParsedIntent(String intent) {
        this.parsedIntent = intent;
        return this;
    }

    public WorkflowStepResult withGeneratedSql(String sql) {
        this.generatedSql = sql;
        return this;
    }

    public WorkflowStepResult withParsedConditions(Map<String, Object> conditions) {
        this.parsedConditions = conditions;
        return this;
    }

    public WorkflowStepResult withExtra(String key, Object value) {
        this.extra.put(key, value);
        return this;
    }

    // ==================== Getter/Setter ====================

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<Map<String, Object>> getQueryData() {
        return queryData;
    }

    public void setQueryData(List<Map<String, Object>> queryData) {
        this.queryData = queryData;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public List<Map<String, Object>> getBeforeData() {
        return beforeData;
    }

    public void setBeforeData(List<Map<String, Object>> beforeData) {
        this.beforeData = beforeData;
    }

    public List<Map<String, Object>> getAfterData() {
        return afterData;
    }

    public void setAfterData(List<Map<String, Object>> afterData) {
        this.afterData = afterData;
    }

    public List<String> getModifiedColumns() {
        return modifiedColumns;
    }

    public void setModifiedColumns(List<String> modifiedColumns) {
        this.modifiedColumns = modifiedColumns;
    }

    public String getParsedIntent() {
        return parsedIntent;
    }

    public void setParsedIntent(String parsedIntent) {
        this.parsedIntent = parsedIntent;
    }

    public String getGeneratedSql() {
        return generatedSql;
    }

    public void setGeneratedSql(String generatedSql) {
        this.generatedSql = generatedSql;
    }

    public Map<String, Object> getParsedConditions() {
        return parsedConditions;
    }

    public void setParsedConditions(Map<String, Object> parsedConditions) {
        this.parsedConditions = parsedConditions;
    }

    public List<Integer> getConfirmedRowIndices() {
        return confirmedRowIndices;
    }

    public void setConfirmedRowIndices(List<Integer> confirmedRowIndices) {
        this.confirmedRowIndices = confirmedRowIndices;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    @SuppressWarnings("unchecked")
    public <T> T getExtra(String key, Class<T> type) {
        return (T) extra.get(key);
    }

    // ==================== 便捷方法 ====================

    /**
     * 是否有对比数据
     */
    public boolean hasComparisonData() {
        return beforeData != null && afterData != null && !beforeData.isEmpty();
    }

    /**
     * 是否有查询数据
     */
    public boolean hasQueryData() {
        return queryData != null && !queryData.isEmpty();
    }

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * 是否等待确认
     */
    public boolean isPending() {
        return status == Status.PENDING;
    }

    /**
     * 获取确认的行数（用户在对比视图中勾选的）
     */
    public int getConfirmedRowCount() {
        return confirmedRowIndices != null ? confirmedRowIndices.size() : 0;
    }

    /**
     * 获取所有行索引（默认全选）
     */
    public List<Integer> getAllRowIndices() {
        if (beforeData == null) return new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < beforeData.size(); i++) {
            indices.add(i);
        }
        return indices;
    }

    @Override
    public String toString() {
        return String.format("WorkflowStepResult{status=%s, message='%s', totalRows=%d}",
                status, message, totalRows);
    }
}
