package red.jiuzhou.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * 基于数据库的即时校验服务
 *
 * 设计原则：
 * 1. 简单 - 一键校验，无需复杂配置
 * 2. 即时 - 快速返回结果，不需要生成报告
 * 3. 上下文感知 - 根据当前表自动推断校验规则
 * 4. 可操作 - 问题可直接跳转定位
 */
public class DatabaseValidationService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseValidationService.class);

    private final JdbcTemplate jdbcTemplate;

    /** 常见的ID引用模式 */
    private static final Map<String, String> REFERENCE_PATTERNS = new LinkedHashMap<>();
    static {
        REFERENCE_PATTERNS.put("item_id", "client_items");
        REFERENCE_PATTERNS.put("npc_id", "client_npcs");
        REFERENCE_PATTERNS.put("skill_id", "skill_data");
        REFERENCE_PATTERNS.put("quest_id", "quest_data");
        REFERENCE_PATTERNS.put("map_id", "world_maps");
        REFERENCE_PATTERNS.put("spawn_id", "spawn_data");
        REFERENCE_PATTERNS.put("drop_id", "drop_templates");
        REFERENCE_PATTERNS.put("recipe_id", "recipe_templates");
        REFERENCE_PATTERNS.put("pet_id", "pet_templates");
    }

    public DatabaseValidationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 一键校验表
     *
     * @param tableName 表名
     * @return 校验结果列表
     */
    public List<ValidationIssue> validateTable(String tableName) {
        List<ValidationIssue> issues = new ArrayList<>();

        try {
            // 1. 检查引用完整性
            issues.addAll(checkReferenceIntegrity(tableName));

            // 2. 检查数据质量
            issues.addAll(checkDataQuality(tableName));

            // 3. 检查数值范围
            issues.addAll(checkValueRanges(tableName));

            log.info("校验完成: {} - 发现 {} 个问题", tableName, issues.size());

        } catch (Exception e) {
            log.error("校验失败: " + tableName, e);
            issues.add(ValidationIssue.error("校验异常", e.getMessage(), null, null));
        }

        return issues;
    }

    /**
     * 检查引用完整性
     */
    private List<ValidationIssue> checkReferenceIntegrity(String tableName) {
        List<ValidationIssue> issues = new ArrayList<>();

        // 获取表的所有列
        List<String> columns = getTableColumns(tableName);

        for (String column : columns) {
            // 检查是否是引用字段
            String targetTable = guessReferenceTarget(column);
            if (targetTable != null && tableExists(targetTable)) {
                issues.addAll(checkColumnReferences(tableName, column, targetTable));
            }
        }

        return issues;
    }

    /**
     * 检查特定列的引用
     */
    private List<ValidationIssue> checkColumnReferences(String sourceTable, String column, String targetTable) {
        List<ValidationIssue> issues = new ArrayList<>();

        try {
            // 查找无效引用
            String sql = String.format(
                "SELECT s.id, s.%s AS ref_value " +
                "FROM %s s " +
                "WHERE s.%s IS NOT NULL AND s.%s != '' AND s.%s != 0 " +
                "AND NOT EXISTS (SELECT 1 FROM %s t WHERE t.id = s.%s) " +
                "LIMIT 100",
                column, sourceTable, column, column, column, targetTable, column
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> row : rows) {
                Object id = row.get("id");
                Object refValue = row.get("ref_value");

                ValidationIssue issue = new ValidationIssue();
                issue.setSeverity(Severity.ERROR);
                issue.setType("引用错误");
                issue.setMessage(String.format("字段 %s 引用了不存在的 %s: %s", column, targetTable, refValue));
                issue.setRecordId(id);
                issue.setFieldName(column);
                issue.setCurrentValue(refValue != null ? refValue.toString() : null);
                issue.setSuggestion("检查ID是否正确，或从 " + targetTable + " 表中选择有效的值");

                issues.add(issue);
            }

        } catch (Exception e) {
            log.debug("引用检查跳过: {} - {}", column, e.getMessage());
        }

        return issues;
    }

    /**
     * 检查数据质量
     */
    private List<ValidationIssue> checkDataQuality(String tableName) {
        List<ValidationIssue> issues = new ArrayList<>();

        // 检查必填字段的空值
        List<String> requiredColumns = guessRequiredColumns(tableName);
        for (String column : requiredColumns) {
            issues.addAll(checkNullValues(tableName, column));
        }

        // 检查重复数据
        issues.addAll(checkDuplicates(tableName));

        return issues;
    }

    /**
     * 检查空值
     */
    private List<ValidationIssue> checkNullValues(String tableName, String column) {
        List<ValidationIssue> issues = new ArrayList<>();

        try {
            String sql = String.format(
                "SELECT id FROM %s WHERE %s IS NULL OR %s = '' LIMIT 50",
                tableName, column, column
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> row : rows) {
                ValidationIssue issue = new ValidationIssue();
                issue.setSeverity(Severity.WARNING);
                issue.setType("空值");
                issue.setMessage(String.format("必填字段 %s 为空", column));
                issue.setRecordId(row.get("id"));
                issue.setFieldName(column);
                issue.setSuggestion("请填写此字段的值");

                issues.add(issue);
            }

        } catch (Exception e) {
            log.debug("空值检查跳过: {}", e.getMessage());
        }

        return issues;
    }

    /**
     * 检查重复数据
     */
    private List<ValidationIssue> checkDuplicates(String tableName) {
        List<ValidationIssue> issues = new ArrayList<>();

        // 检查name字段的重复
        if (columnExists(tableName, "name")) {
            try {
                String sql = String.format(
                    "SELECT name, COUNT(*) as cnt FROM %s " +
                    "WHERE name IS NOT NULL AND name != '' " +
                    "GROUP BY name HAVING cnt > 1 LIMIT 20",
                    tableName
                );

                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

                for (Map<String, Object> row : rows) {
                    String name = (String) row.get("name");
                    Long count = ((Number) row.get("cnt")).longValue();

                    ValidationIssue issue = new ValidationIssue();
                    issue.setSeverity(Severity.INFO);
                    issue.setType("重复数据");
                    issue.setMessage(String.format("名称 \"%s\" 重复出现 %d 次", name, count));
                    issue.setFieldName("name");
                    issue.setCurrentValue(name);
                    issue.setSuggestion("检查是否为有意重复，如果不是请修改名称");

                    issues.add(issue);
                }

            } catch (Exception e) {
                log.debug("重复检查跳过: {}", e.getMessage());
            }
        }

        return issues;
    }

    /**
     * 检查数值范围
     */
    private List<ValidationIssue> checkValueRanges(String tableName) {
        List<ValidationIssue> issues = new ArrayList<>();

        // 获取数值列
        List<String> numericColumns = getNumericColumns(tableName);

        for (String column : numericColumns) {
            issues.addAll(checkColumnRange(tableName, column));
        }

        return issues;
    }

    /**
     * 检查数值列的范围
     */
    private List<ValidationIssue> checkColumnRange(String tableName, String column) {
        List<ValidationIssue> issues = new ArrayList<>();

        try {
            // 计算平均值和标准差
            String statsSql = String.format(
                "SELECT AVG(%s) as avg_val, STDDEV(%s) as std_val FROM %s WHERE %s IS NOT NULL",
                column, column, tableName, column
            );

            Map<String, Object> stats = jdbcTemplate.queryForMap(statsSql);
            Double avg = stats.get("avg_val") != null ? ((Number) stats.get("avg_val")).doubleValue() : null;
            Double std = stats.get("std_val") != null ? ((Number) stats.get("std_val")).doubleValue() : null;

            if (avg != null && std != null && std > 0) {
                // 查找异常值（超过3个标准差）
                double lowerBound = avg - 3 * std;
                double upperBound = avg + 3 * std;

                String outlierSql = String.format(
                    "SELECT id, %s as val FROM %s WHERE %s < ? OR %s > ? LIMIT 20",
                    column, tableName, column, column
                );

                List<Map<String, Object>> outliers = jdbcTemplate.queryForList(
                    outlierSql, lowerBound, upperBound
                );

                for (Map<String, Object> row : outliers) {
                    ValidationIssue issue = new ValidationIssue();
                    issue.setSeverity(Severity.INFO);
                    issue.setType("异常值");
                    issue.setMessage(String.format("字段 %s 的值 %s 偏离正常范围 (%.0f ~ %.0f)",
                        column, row.get("val"), lowerBound, upperBound));
                    issue.setRecordId(row.get("id"));
                    issue.setFieldName(column);
                    issue.setCurrentValue(row.get("val") != null ? row.get("val").toString() : null);
                    issue.setSuggestion(String.format("建议值范围: %.0f ~ %.0f", avg - std, avg + std));

                    issues.add(issue);
                }
            }

        } catch (Exception e) {
            log.debug("范围检查跳过: {} - {}", column, e.getMessage());
        }

        return issues;
    }

    // ========== 工具方法 ==========

    /**
     * 获取表的所有列
     */
    private List<String> getTableColumns(String tableName) {
        try {
            String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                         "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
            return jdbcTemplate.queryForList(sql, String.class, tableName);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 获取数值列
     */
    private List<String> getNumericColumns(String tableName) {
        try {
            String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                         "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                         "AND DATA_TYPE IN ('int', 'bigint', 'decimal', 'float', 'double')";
            return jdbcTemplate.queryForList(sql, String.class, tableName);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 推测引用目标表
     */
    private String guessReferenceTarget(String columnName) {
        String lowerColumn = columnName.toLowerCase();

        // 精确匹配
        if (REFERENCE_PATTERNS.containsKey(lowerColumn)) {
            return REFERENCE_PATTERNS.get(lowerColumn);
        }

        // 模式匹配
        for (Map.Entry<String, String> entry : REFERENCE_PATTERNS.entrySet()) {
            if (lowerColumn.endsWith(entry.getKey()) || lowerColumn.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 推测必填列
     */
    private List<String> guessRequiredColumns(String tableName) {
        List<String> required = new ArrayList<>();

        // 常见必填字段
        String[] commonRequired = {"name", "name_id", "title"};
        for (String col : commonRequired) {
            if (columnExists(tableName, col)) {
                required.add(col);
            }
        }

        return required;
    }

    /**
     * 检查表是否存在
     */
    private boolean tableExists(String tableName) {
        try {
            String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                         "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查列是否存在
     */
    private boolean columnExists(String tableName, String columnName) {
        try {
            String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                         "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName, columnName);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ========== 数据模型 ==========

    /**
     * 校验问题严重程度
     */
    public enum Severity {
        ERROR,      // 错误 - 必须修复
        WARNING,    // 警告 - 建议修复
        INFO        // 信息 - 可选优化
    }

    /**
     * 校验问题
     */
    public static class ValidationIssue {
        private Severity severity;
        private String type;
        private String message;
        private Object recordId;
        private String fieldName;
        private String currentValue;
        private String suggestion;

        // Getter and Setter methods
        public Severity getSeverity() {
            return severity;
        }

        public void setSeverity(Severity severity) {
            this.severity = severity;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Object getRecordId() {
            return recordId;
        }

        public void setRecordId(Object recordId) {
            this.recordId = recordId;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getCurrentValue() {
            return currentValue;
        }

        public void setCurrentValue(String currentValue) {
            this.currentValue = currentValue;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }

        public static ValidationIssue error(String type, String message, Object recordId, String field) {
            ValidationIssue issue = new ValidationIssue();
            issue.setSeverity(Severity.ERROR);
            issue.setType(type);
            issue.setMessage(message);
            issue.setRecordId(recordId);
            issue.setFieldName(field);
            return issue;
        }

        public String getSeverityIcon() {
            switch (severity) {
                case ERROR: return "❌";
                case WARNING: return "⚠️";
                case INFO: return "ℹ️";
                default: return "•";
            }
        }

        public String getSeverityColor() {
            switch (severity) {
                case ERROR: return "#d32f2f";
                case WARNING: return "#f57c00";
                case INFO: return "#1976d2";
                default: return "#666666";
            }
        }
    }
}
