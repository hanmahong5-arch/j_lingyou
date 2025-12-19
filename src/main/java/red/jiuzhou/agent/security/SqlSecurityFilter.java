package red.jiuzhou.agent.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL安全过滤器
 *
 * 验证SQL语句的安全性
 * 防止危险操作，估算影响范围
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class SqlSecurityFilter {

    private static final Logger log = LoggerFactory.getLogger(SqlSecurityFilter.class);

    /** 黑名单关键词（禁止的SQL操作） */
    private static final Set<String> BLACKLIST_KEYWORDS = new HashSet<>(Arrays.asList(
        "DROP", "TRUNCATE", "ALTER", "CREATE", "GRANT", "REVOKE",
        "EXEC", "EXECUTE", "XP_", "SP_",
        "LOAD_FILE", "INTO OUTFILE", "INTO DUMPFILE",
        "BENCHMARK", "SLEEP"
    ));

    /** 白名单语句类型 */
    private static final Set<String> WHITELIST_TYPES = new HashSet<>(Arrays.asList(
        "SELECT", "UPDATE", "INSERT", "DELETE"
    ));

    /** 单次最大影响行数 */
    private int maxAffectedRows = 1000;

    /** 需要二次确认的行数阈值 */
    private int confirmationThreshold = 100;

    /** JdbcTemplate用于估算影响行数 */
    private JdbcTemplate jdbcTemplate;

    public SqlSecurityFilter() {
    }

    public SqlSecurityFilter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private boolean valid;
        private String message;
        private String sqlType;
        private int estimatedAffectedRows;
        private boolean requiresConfirmation;
        private List<String> warnings;

        public ValidationResult() {
            this.warnings = new ArrayList<>();
        }

        public static ValidationResult success(String sqlType) {
            ValidationResult result = new ValidationResult();
            result.valid = true;
            result.sqlType = sqlType;
            result.message = "SQL验证通过";
            return result;
        }

        public static ValidationResult failure(String message) {
            ValidationResult result = new ValidationResult();
            result.valid = false;
            result.message = message;
            return result;
        }

        // Getters and Setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getSqlType() { return sqlType; }
        public void setSqlType(String sqlType) { this.sqlType = sqlType; }
        public int getEstimatedAffectedRows() { return estimatedAffectedRows; }
        public void setEstimatedAffectedRows(int estimatedAffectedRows) {
            this.estimatedAffectedRows = estimatedAffectedRows;
        }
        public boolean isRequiresConfirmation() { return requiresConfirmation; }
        public void setRequiresConfirmation(boolean requiresConfirmation) {
            this.requiresConfirmation = requiresConfirmation;
        }
        public List<String> getWarnings() { return warnings; }
        public void addWarning(String warning) { this.warnings.add(warning); }
    }

    /**
     * 验证SQL安全性
     *
     * @param sql SQL语句
     * @return 验证结果
     */
    public ValidationResult validate(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return ValidationResult.failure("SQL语句为空");
        }

        String normalizedSql = normalizeSql(sql);
        String upperSql = normalizedSql.toUpperCase();

        // 1. 检查黑名单关键词
        for (String keyword : BLACKLIST_KEYWORDS) {
            if (containsKeyword(upperSql, keyword)) {
                return ValidationResult.failure(
                    String.format("SQL包含禁止的关键词: %s", keyword));
            }
        }

        // 2. 检测SQL类型
        String sqlType = detectSqlType(upperSql);
        if (sqlType == null) {
            return ValidationResult.failure("无法识别的SQL类型");
        }

        if (!WHITELIST_TYPES.contains(sqlType)) {
            return ValidationResult.failure(
                String.format("不允许的SQL类型: %s", sqlType));
        }

        ValidationResult result = ValidationResult.success(sqlType);

        // 3. 针对不同类型的特殊检查
        switch (sqlType) {
            case "SELECT":
                validateSelect(normalizedSql, result);
                break;
            case "UPDATE":
                validateUpdate(normalizedSql, result);
                break;
            case "DELETE":
                validateDelete(normalizedSql, result);
                break;
            case "INSERT":
                validateInsert(normalizedSql, result);
                break;
        }

        // 4. 估算影响行数（对于修改操作）
        if (result.isValid() && !sqlType.equals("SELECT")) {
            estimateAffectedRows(normalizedSql, result);

            // 检查是否超过限制
            if (result.getEstimatedAffectedRows() > maxAffectedRows) {
                result.setValid(false);
                result.setMessage(String.format(
                    "预计影响 %d 行，超过最大限制 %d 行",
                    result.getEstimatedAffectedRows(), maxAffectedRows));
            } else if (result.getEstimatedAffectedRows() > confirmationThreshold) {
                result.setRequiresConfirmation(true);
                result.addWarning(String.format(
                    "预计影响 %d 行，需要二次确认",
                    result.getEstimatedAffectedRows()));
            }
        }

        return result;
    }

    /**
     * 检测SQL类型
     */
    private String detectSqlType(String upperSql) {
        String trimmed = upperSql.trim();

        if (trimmed.startsWith("SELECT")) return "SELECT";
        if (trimmed.startsWith("UPDATE")) return "UPDATE";
        if (trimmed.startsWith("DELETE")) return "DELETE";
        if (trimmed.startsWith("INSERT")) return "INSERT";

        return null;
    }

    /**
     * 验证SELECT语句
     */
    private void validateSelect(String sql, ValidationResult result) {
        // SELECT相对安全，只做基本检查

        // 检查是否有子查询修改数据
        String upperSql = sql.toUpperCase();
        if (upperSql.contains("UPDATE") || upperSql.contains("DELETE") || upperSql.contains("INSERT")) {
            // 可能是子查询，需要更仔细检查
            if (hasModifyingSubquery(sql)) {
                result.setValid(false);
                result.setMessage("SELECT语句中不允许包含修改数据的子查询");
            }
        }

        // 检查是否限制了返回行数
        if (!upperSql.contains("LIMIT")) {
            result.addWarning("建议添加LIMIT限制返回行数");
        }
    }

    /**
     * 验证UPDATE语句
     */
    private void validateUpdate(String sql, ValidationResult result) {
        String upperSql = sql.toUpperCase();

        // 必须有WHERE条件
        if (!upperSql.contains("WHERE")) {
            result.setValid(false);
            result.setMessage("UPDATE语句必须包含WHERE条件");
            return;
        }

        // 检查WHERE条件是否有效（不能是恒真条件）
        if (hasTautologyCondition(sql)) {
            result.setValid(false);
            result.setMessage("UPDATE的WHERE条件不能是恒真条件（如 1=1）");
            return;
        }

        result.setRequiresConfirmation(true);
    }

    /**
     * 验证DELETE语句
     */
    private void validateDelete(String sql, ValidationResult result) {
        String upperSql = sql.toUpperCase();

        // 必须有WHERE条件
        if (!upperSql.contains("WHERE")) {
            result.setValid(false);
            result.setMessage("DELETE语句必须包含WHERE条件");
            return;
        }

        // 检查WHERE条件是否有效
        if (hasTautologyCondition(sql)) {
            result.setValid(false);
            result.setMessage("DELETE的WHERE条件不能是恒真条件（如 1=1）");
            return;
        }

        // DELETE操作总是需要确认
        result.setRequiresConfirmation(true);
        result.addWarning("DELETE操作将永久删除数据，请谨慎确认");
    }

    /**
     * 验证INSERT语句
     */
    private void validateInsert(String sql, ValidationResult result) {
        // 估算插入行数
        String upperSql = sql.toUpperCase();

        // 检查是否是INSERT ... SELECT
        if (upperSql.contains("SELECT")) {
            result.addWarning("INSERT ... SELECT 语句，影响行数取决于SELECT结果");
            result.setRequiresConfirmation(true);
        } else {
            // 计算VALUES子句的数量
            int rowCount = countInsertRows(sql);
            result.setEstimatedAffectedRows(rowCount);

            if (rowCount > 100) {
                result.setValid(false);
                result.setMessage(String.format(
                    "单次INSERT不能超过100行，当前: %d 行", rowCount));
            }
        }
    }

    /**
     * 估算影响行数
     */
    private void estimateAffectedRows(String sql, ValidationResult result) {
        if (jdbcTemplate == null) {
            result.addWarning("无法估算影响行数（JdbcTemplate未设置）");
            return;
        }

        try {
            // 将UPDATE/DELETE转换为SELECT COUNT(*)来估算
            String countSql = convertToCountSql(sql);
            if (countSql != null) {
                Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);
                result.setEstimatedAffectedRows(count != null ? count : 0);
            }
        } catch (Exception e) {
            log.warn("估算影响行数失败: {}", e.getMessage());
            result.addWarning("无法估算影响行数: " + e.getMessage());
        }
    }

    /**
     * 将UPDATE/DELETE语句转换为COUNT查询
     */
    private String convertToCountSql(String sql) {
        String upperSql = sql.toUpperCase().trim();

        if (upperSql.startsWith("UPDATE")) {
            // UPDATE table SET ... WHERE condition
            // -> SELECT COUNT(*) FROM table WHERE condition
            Pattern pattern = Pattern.compile(
                "UPDATE\\s+`?(\\w+)`?\\s+SET\\s+.+?\\s+(WHERE\\s+.+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcher = pattern.matcher(sql);
            if (matcher.find()) {
                String tableName = matcher.group(1);
                String whereClause = matcher.group(2);
                return String.format("SELECT COUNT(*) FROM `%s` %s", tableName, whereClause);
            }
        } else if (upperSql.startsWith("DELETE")) {
            // DELETE FROM table WHERE condition
            // -> SELECT COUNT(*) FROM table WHERE condition
            Pattern pattern = Pattern.compile(
                "DELETE\\s+FROM\\s+`?(\\w+)`?\\s+(WHERE\\s+.+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcher = pattern.matcher(sql);
            if (matcher.find()) {
                String tableName = matcher.group(1);
                String whereClause = matcher.group(2);
                return String.format("SELECT COUNT(*) FROM `%s` %s", tableName, whereClause);
            }
        }

        return null;
    }

    /**
     * 检查是否包含关键词（作为独立单词）
     */
    private boolean containsKeyword(String sql, String keyword) {
        String pattern = "\\b" + keyword + "\\b";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(sql).find();
    }

    /**
     * 检查是否有修改数据的子查询
     */
    private boolean hasModifyingSubquery(String sql) {
        // 简单检查：括号内是否有UPDATE/DELETE/INSERT
        int depth = 0;
        String upperSql = sql.toUpperCase();

        for (int i = 0; i < upperSql.length(); i++) {
            char c = upperSql.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (depth > 0) {
                // 在括号内检查
                if (i + 6 < upperSql.length()) {
                    String sub = upperSql.substring(i, i + 6);
                    if (sub.equals("UPDATE") || sub.equals("DELETE") || sub.equals("INSERT")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 检查是否有恒真条件
     */
    private boolean hasTautologyCondition(String sql) {
        String upperSql = sql.toUpperCase();

        // 常见的恒真条件
        String[] tautologies = {
            "1=1", "1 = 1", "'1'='1'", "1<>0", "1 <> 0",
            "TRUE", "'A'='A'", "0=0", "2>1"
        };

        for (String tautology : tautologies) {
            if (upperSql.contains(tautology)) {
                // 确保不是更复杂条件的一部分
                // 比如 "WHERE status = 1 AND 1=1" 是有问题的
                // 但 "WHERE id = 1" 是可以的
                int idx = upperSql.indexOf("WHERE");
                if (idx >= 0) {
                    String whereClause = upperSql.substring(idx);
                    // 如果WHERE后只有恒真条件
                    if (whereClause.replace("WHERE", "").trim().equals(tautology)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 计算INSERT语句的行数
     */
    private int countInsertRows(String sql) {
        // 计算VALUES关键词后面有多少组括号
        String upperSql = sql.toUpperCase();
        int valuesIdx = upperSql.indexOf("VALUES");
        if (valuesIdx < 0) {
            return 1;
        }

        String valuesPart = sql.substring(valuesIdx);
        int count = 0;
        int depth = 0;

        for (char c : valuesPart.toCharArray()) {
            if (c == '(') {
                if (depth == 0) {
                    count++;
                }
                depth++;
            } else if (c == ')') {
                depth--;
            }
        }

        return Math.max(count, 1);
    }

    /**
     * 标准化SQL语句
     */
    private String normalizeSql(String sql) {
        return sql.trim()
                  .replaceAll("\\s+", " ")  // 多个空白替换为单个空格
                  .replaceAll(";\\s*$", ""); // 移除末尾分号
    }

    /**
     * 快速检查SQL是否安全（不做完整验证）
     */
    public boolean quickCheck(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }

        String upperSql = sql.toUpperCase().trim();

        // 快速黑名单检查
        for (String keyword : BLACKLIST_KEYWORDS) {
            if (upperSql.contains(keyword)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 从SQL中提取表名
     */
    public String extractTableName(String sql) {
        String upperSql = sql.toUpperCase().trim();

        // SELECT ... FROM table
        // UPDATE table SET
        // DELETE FROM table
        // INSERT INTO table

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

        return null;
    }

    // ========== Getter/Setter ==========

    public int getMaxAffectedRows() {
        return maxAffectedRows;
    }

    public void setMaxAffectedRows(int maxAffectedRows) {
        this.maxAffectedRows = maxAffectedRows;
    }

    public int getConfirmationThreshold() {
        return confirmationThreshold;
    }

    public void setConfirmationThreshold(int confirmationThreshold) {
        this.confirmationThreshold = confirmationThreshold;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
}
