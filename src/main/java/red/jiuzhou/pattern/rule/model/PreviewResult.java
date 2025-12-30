package red.jiuzhou.pattern.rule.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则预览结果
 *
 * 在执行前让设计师清楚看到：
 * - 会影响多少条记录
 * - 每个字段会如何变化
 * - 变化的统计分布
 */
public class PreviewResult {

    /** 规则ID */
    private String ruleId;

    /** 规则名称 */
    private String ruleName;

    /** 匹配的记录数 */
    private int matchedCount;

    /** 受影响的记录详情（包含变更对比） */
    private List<RecordChange> recordChanges = new ArrayList<>();

    /** 字段变更统计 */
    private Map<String, FieldChangeStats> fieldStats = new HashMap<>();

    /** 预览是否成功 */
    private boolean success = true;

    /** 错误消息（如果预览失败） */
    private String errorMessage;

    /** 警告消息列表 */
    private List<String> warnings = new ArrayList<>();

    /**
     * 单条记录的变更详情
     */
    public static class RecordChange {
        /** 记录ID */
        private Object recordId;

        /** 记录名称（用于展示） */
        private String recordName;

        /** 原始值（字段名 -> 原值） */
        private Map<String, Object> originalValues = new HashMap<>();

        /** 新值（字段名 -> 新值） */
        private Map<String, Object> newValues = new HashMap<>();

        /** 完整的原始记录（用于回滚） */
        private Map<String, Object> originalRecord;

        public RecordChange() {}

        public RecordChange(Object recordId, String recordName) {
            this.recordId = recordId;
            this.recordName = recordName;
        }

        public void addChange(String field, Object original, Object newValue) {
            this.originalValues.put(field, original);
            this.newValues.put(field, newValue);
        }

        /**
         * 生成变更描述
         */
        public String toChangeDescription() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(recordId).append("] ").append(recordName).append(": ");

            List<String> changes = new ArrayList<>();
            for (String field : originalValues.keySet()) {
                Object oldVal = originalValues.get(field);
                Object newVal = newValues.get(field);
                changes.add(field + ": " + oldVal + " → " + newVal);
            }
            sb.append(String.join(", ", changes));
            return sb.toString();
        }

        // Getters and Setters
        public Object getRecordId() { return recordId; }
        public void setRecordId(Object recordId) { this.recordId = recordId; }

        public String getRecordName() { return recordName; }
        public void setRecordName(String recordName) { this.recordName = recordName; }

        public Map<String, Object> getOriginalValues() { return originalValues; }
        public void setOriginalValues(Map<String, Object> originalValues) { this.originalValues = originalValues; }

        public Map<String, Object> getNewValues() { return newValues; }
        public void setNewValues(Map<String, Object> newValues) { this.newValues = newValues; }

        public Map<String, Object> getOriginalRecord() { return originalRecord; }
        public void setOriginalRecord(Map<String, Object> originalRecord) { this.originalRecord = originalRecord; }
    }

    /**
     * 字段变更统计
     */
    public static class FieldChangeStats {
        /** 字段名 */
        private String fieldName;

        /** 变更前最小值 */
        private double beforeMin;

        /** 变更前最大值 */
        private double beforeMax;

        /** 变更前平均值 */
        private double beforeAvg;

        /** 变更后最小值 */
        private double afterMin;

        /** 变更后最大值 */
        private double afterMax;

        /** 变更后平均值 */
        private double afterAvg;

        /** 变化百分比（平均） */
        private double changePercent;

        /** 变化绝对值（平均） */
        private double changeAbsolute;

        public FieldChangeStats() {}

        public FieldChangeStats(String fieldName) {
            this.fieldName = fieldName;
        }

        /**
         * 生成统计描述
         */
        public String toStatsDescription() {
            return String.format("%s: %.1f → %.1f (%.1f%%，平均%+.1f)",
                fieldName, beforeAvg, afterAvg, changePercent * 100, changeAbsolute);
        }

        // Getters and Setters
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }

        public double getBeforeMin() { return beforeMin; }
        public void setBeforeMin(double beforeMin) { this.beforeMin = beforeMin; }

        public double getBeforeMax() { return beforeMax; }
        public void setBeforeMax(double beforeMax) { this.beforeMax = beforeMax; }

        public double getBeforeAvg() { return beforeAvg; }
        public void setBeforeAvg(double beforeAvg) { this.beforeAvg = beforeAvg; }

        public double getAfterMin() { return afterMin; }
        public void setAfterMin(double afterMin) { this.afterMin = afterMin; }

        public double getAfterMax() { return afterMax; }
        public void setAfterMax(double afterMax) { this.afterMax = afterMax; }

        public double getAfterAvg() { return afterAvg; }
        public void setAfterAvg(double afterAvg) { this.afterAvg = afterAvg; }

        public double getChangePercent() { return changePercent; }
        public void setChangePercent(double changePercent) { this.changePercent = changePercent; }

        public double getChangeAbsolute() { return changeAbsolute; }
        public void setChangeAbsolute(double changeAbsolute) { this.changeAbsolute = changeAbsolute; }
    }

    /**
     * 生成预览摘要
     */
    public String toSummary() {
        if (!success) {
            return "预览失败: " + errorMessage;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【").append(ruleName).append("】预览结果\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("匹配记录: ").append(matchedCount).append(" 条\n\n");

        if (!fieldStats.isEmpty()) {
            sb.append("字段变更统计:\n");
            for (FieldChangeStats stats : fieldStats.values()) {
                sb.append("  • ").append(stats.toStatsDescription()).append("\n");
            }
        }

        if (!warnings.isEmpty()) {
            sb.append("\n⚠️ 警告:\n");
            for (String warning : warnings) {
                sb.append("  • ").append(warning).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 添加警告
     */
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    /**
     * 标记为失败
     */
    public void markFailed(String error) {
        this.success = false;
        this.errorMessage = error;
    }

    // Getters and Setters
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public int getMatchedCount() { return matchedCount; }
    public void setMatchedCount(int matchedCount) { this.matchedCount = matchedCount; }

    public List<RecordChange> getRecordChanges() { return recordChanges; }
    public void setRecordChanges(List<RecordChange> recordChanges) { this.recordChanges = recordChanges; }

    public Map<String, FieldChangeStats> getFieldStats() { return fieldStats; }
    public void setFieldStats(Map<String, FieldChangeStats> fieldStats) { this.fieldStats = fieldStats; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
