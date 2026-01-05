package red.jiuzhou.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQL验证器
 *
 * 提供SQL语句的验证和自动修正功能
 *
 * 核心功能:
 * - 语法检查（基本验证）
 * - 表名/字段名存在性验证
 * - 自动补全 LIMIT
 * - 自动修正常见错误（如缺少引号、表名大小写）
 *
 * @author Claude
 * @date 2025-12-31
 */
public class SqlValidator {

    private static final Logger log = LoggerFactory.getLogger(SqlValidator.class);

    private final DatabaseSchemaProvider schemaProvider;
    private final JdbcTemplate jdbcTemplate;

    /** 默认LIMIT值 */
    private static final int DEFAULT_LIMIT = 100;

    /** 最大LIMIT值（防止返回过多数据） */
    private static final int MAX_LIMIT = 1000;

    /** SQL关键字（用于语法检查） */
    private static final Set<String> SQL_KEYWORDS = Set.of(
        "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "BETWEEN",
        "LIKE", "IS", "NULL", "ORDER", "BY", "ASC", "DESC", "LIMIT",
        "OFFSET", "GROUP", "HAVING", "JOIN", "LEFT", "RIGHT", "INNER",
        "OUTER", "ON", "AS", "DISTINCT", "COUNT", "SUM", "AVG", "MAX", "MIN",
        "UPDATE", "SET", "INSERT", "INTO", "VALUES", "DELETE"
    );

    /** 危险操作关键字 */
    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
        "DROP", "TRUNCATE", "ALTER", "CREATE", "GRANT", "REVOKE"
    );

    public SqlValidator() {
        this(DatabaseUtil.getJdbcTemplate(null));
    }

    public SqlValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaProvider = new DatabaseSchemaProvider(jdbcTemplate);
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private boolean valid;
        private String originalSql;
        private String correctedSql;
        private List<String> errors;
        private List<String> warnings;
        private List<String> corrections;

        public ValidationResult(String originalSql) {
            this.originalSql = originalSql;
            this.correctedSql = originalSql;
            this.valid = true;
            this.errors = new ArrayList<>();
            this.warnings = new ArrayList<>();
            this.corrections = new ArrayList<>();
        }

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }

        public String getOriginalSql() { return originalSql; }
        public String getCorrectedSql() { return correctedSql; }
        public void setCorrectedSql(String sql) { this.correctedSql = sql; }

        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public List<String> getCorrections() { return corrections; }

        public void addError(String error) {
            this.errors.add(error);
            this.valid = false;
        }

        public void addWarning(String warning) {
            this.warnings.add(warning);
        }

        public void addCorrection(String correction) {
            this.corrections.add(correction);
        }

        public boolean hasCorrections() {
            return !corrections.isEmpty();
        }

        /**
         * 生成摘要报告
         */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();

            if (valid) {
                sb.append("✅ SQL验证通过\n");
            } else {
                sb.append("❌ SQL验证失败\n");
            }

            if (!errors.isEmpty()) {
                sb.append("\n错误:\n");
                for (String error : errors) {
                    sb.append("  • ").append(error).append("\n");
                }
            }

            if (!warnings.isEmpty()) {
                sb.append("\n警告:\n");
                for (String warning : warnings) {
                    sb.append("  • ").append(warning).append("\n");
                }
            }

            if (!corrections.isEmpty()) {
                sb.append("\n自动修正:\n");
                for (String correction : corrections) {
                    sb.append("  • ").append(correction).append("\n");
                }
            }

            return sb.toString();
        }
    }

    /**
     * 完整验证SQL语句
     *
     * @param sql 原始SQL
     * @return 验证结果
     */
    public ValidationResult validate(String sql) {
        ValidationResult result = new ValidationResult(sql);

        if (sql == null || sql.trim().isEmpty()) {
            result.addError("SQL语句为空");
            return result;
        }

        String trimmedSql = sql.trim();

        // 1. 危险操作检查
        checkDangerousOperations(trimmedSql, result);
        if (!result.isValid()) {
            return result;  // 危险操作直接返回
        }

        // 2. 基本语法检查
        checkBasicSyntax(trimmedSql, result);

        // 3. 表名验证
        validateTableNames(trimmedSql, result);

        // 4. 字段名验证
        validateColumnNames(trimmedSql, result);

        // 5. 自动修正
        String correctedSql = autoCorrect(trimmedSql, result);
        result.setCorrectedSql(correctedSql);

        log.debug("SQL验证完成: valid={}, errors={}, corrections={}",
            result.isValid(), result.getErrors().size(), result.getCorrections().size());

        return result;
    }

    /**
     * 快速验证（仅基础检查，不查询数据库）
     */
    public ValidationResult quickValidate(String sql) {
        ValidationResult result = new ValidationResult(sql);

        if (sql == null || sql.trim().isEmpty()) {
            result.addError("SQL语句为空");
            return result;
        }

        String trimmedSql = sql.trim();

        // 1. 危险操作检查
        checkDangerousOperations(trimmedSql, result);

        // 2. 基本语法检查
        checkBasicSyntax(trimmedSql, result);

        // 3. 自动修正（不含表名修正）
        String correctedSql = autoCorrectBasic(trimmedSql, result);
        result.setCorrectedSql(correctedSql);

        return result;
    }

    /**
     * 检查危险操作
     */
    private void checkDangerousOperations(String sql, ValidationResult result) {
        String upperSql = sql.toUpperCase();

        for (String keyword : DANGEROUS_KEYWORDS) {
            // 检查是否以危险关键字开头或包含危险关键字（前后有空格或开头）
            Pattern pattern = Pattern.compile("(^|\\s)" + keyword + "(\\s|$)");
            if (pattern.matcher(upperSql).find()) {
                result.addError("检测到危险操作: " + keyword + " - 此操作被禁止");
                return;
            }
        }
    }

    /**
     * 基本语法检查
     */
    private void checkBasicSyntax(String sql, ValidationResult result) {
        String upperSql = sql.toUpperCase().trim();

        // 检查SQL类型
        if (!upperSql.startsWith("SELECT") &&
            !upperSql.startsWith("UPDATE") &&
            !upperSql.startsWith("INSERT") &&
            !upperSql.startsWith("DELETE") &&
            !upperSql.startsWith("WITH")) {
            result.addError("不支持的SQL类型，仅支持 SELECT/UPDATE/INSERT/DELETE/WITH");
            return;
        }

        // SELECT 语句检查
        if (upperSql.startsWith("SELECT")) {
            if (!upperSql.contains("FROM")) {
                result.addError("SELECT语句缺少 FROM 子句");
            }
        }

        // UPDATE 语句检查
        if (upperSql.startsWith("UPDATE")) {
            if (!upperSql.contains("SET")) {
                result.addError("UPDATE语句缺少 SET 子句");
            }
            if (!upperSql.contains("WHERE")) {
                result.addWarning("UPDATE语句没有 WHERE 条件，将影响所有行");
            }
        }

        // DELETE 语句检查
        if (upperSql.startsWith("DELETE")) {
            if (!upperSql.contains("WHERE")) {
                result.addWarning("DELETE语句没有 WHERE 条件，将删除所有行");
            }
        }

        // 括号匹配检查
        int openParens = 0;
        for (char c : sql.toCharArray()) {
            if (c == '(') openParens++;
            if (c == ')') openParens--;
            if (openParens < 0) {
                result.addError("括号不匹配：多余的右括号");
                break;
            }
        }
        if (openParens > 0) {
            result.addError("括号不匹配：缺少 " + openParens + " 个右括号");
        }

        // 引号匹配检查
        int singleQuotes = 0;
        boolean inEscape = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\\') {
                inEscape = !inEscape;
            } else if (c == '\'' && !inEscape) {
                singleQuotes++;
            } else {
                inEscape = false;
            }
        }
        if (singleQuotes % 2 != 0) {
            result.addError("引号不匹配：缺少闭合的单引号");
        }
    }

    /**
     * 验证表名是否存在
     */
    private void validateTableNames(String sql, ValidationResult result) {
        List<String> tableNames = extractTableNames(sql);
        List<String> allTables = schemaProvider.getAllTableNames();
        Set<String> allTablesLower = allTables.stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        for (String tableName : tableNames) {
            if (!allTablesLower.contains(tableName.toLowerCase())) {
                // 尝试模糊匹配
                List<String> similar = findSimilarTables(tableName, allTables);
                if (!similar.isEmpty()) {
                    result.addError("表 '" + tableName + "' 不存在，您是否指的是: " +
                        String.join(", ", similar));
                } else {
                    result.addError("表 '" + tableName + "' 不存在");
                }
            }
        }
    }

    /**
     * 验证字段名是否存在
     */
    private void validateColumnNames(String sql, ValidationResult result) {
        List<String> tableNames = extractTableNames(sql);
        if (tableNames.isEmpty()) {
            return;
        }

        // 获取所有相关表的字段
        Set<String> allColumns = new HashSet<>();
        for (String tableName : tableNames) {
            DatabaseSchemaProvider.TableInfo tableInfo = schemaProvider.getTableInfo(tableName);
            if (tableInfo != null) {
                for (DatabaseSchemaProvider.ColumnInfo col : tableInfo.getColumns()) {
                    allColumns.add(col.getColumnName().toLowerCase());
                }
            }
        }

        // 提取SQL中的字段名并验证
        List<String> columnRefs = extractColumnReferences(sql);
        for (String col : columnRefs) {
            // 跳过 *、数字、函数等
            if (col.equals("*") || col.matches("\\d+") || isFunction(col)) {
                continue;
            }
            // 跳过带表别名的完整引用（如 t.column）
            if (col.contains(".")) {
                col = col.substring(col.lastIndexOf('.') + 1);
            }

            if (!allColumns.contains(col.toLowerCase()) && !col.isEmpty()) {
                // 只发出警告，因为可能是别名或表达式
                result.addWarning("字段 '" + col + "' 可能不存在于目标表中");
            }
        }
    }

    /**
     * 自动修正SQL
     */
    private String autoCorrect(String sql, ValidationResult result) {
        String corrected = sql;

        // 1. 基础修正
        corrected = autoCorrectBasic(corrected, result);

        // 2. 表名大小写修正
        corrected = correctTableNameCase(corrected, result);

        return corrected;
    }

    /**
     * 基础自动修正（不查询数据库）
     */
    private String autoCorrectBasic(String sql, ValidationResult result) {
        String corrected = sql;
        String upperSql = sql.toUpperCase();

        // 1. 添加 LIMIT（仅对 SELECT）
        if (upperSql.startsWith("SELECT") && !upperSql.contains("LIMIT")) {
            corrected = corrected + " LIMIT " + DEFAULT_LIMIT;
            result.addCorrection("自动添加 LIMIT " + DEFAULT_LIMIT);
        }

        // 2. 限制过大的 LIMIT
        Pattern limitPattern = Pattern.compile("LIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher limitMatcher = limitPattern.matcher(corrected);
        if (limitMatcher.find()) {
            int limitValue = Integer.parseInt(limitMatcher.group(1));
            if (limitValue > MAX_LIMIT) {
                corrected = corrected.substring(0, limitMatcher.start()) +
                           "LIMIT " + MAX_LIMIT +
                           corrected.substring(limitMatcher.end());
                result.addCorrection("LIMIT 值从 " + limitValue + " 降低到 " + MAX_LIMIT);
            }
        }

        // 3. 移除多余的分号
        if (corrected.endsWith(";")) {
            corrected = corrected.substring(0, corrected.length() - 1).trim();
            result.addCorrection("移除末尾分号");
        }

        // 4. 修正常见拼写错误
        corrected = correctCommonTypos(corrected, result);

        return corrected;
    }

    /**
     * 修正表名大小写
     */
    private String correctTableNameCase(String sql, ValidationResult result) {
        List<String> allTables = schemaProvider.getAllTableNames();
        Map<String, String> tableCaseMap = new HashMap<>();
        for (String table : allTables) {
            tableCaseMap.put(table.toLowerCase(), table);
        }

        String corrected = sql;
        List<String> extractedTables = extractTableNames(sql);

        for (String table : extractedTables) {
            String correctCase = tableCaseMap.get(table.toLowerCase());
            if (correctCase != null && !correctCase.equals(table)) {
                // 使用边界匹配替换表名
                Pattern pattern = Pattern.compile("\\b" + Pattern.quote(table) + "\\b",
                    Pattern.CASE_INSENSITIVE);
                corrected = pattern.matcher(corrected).replaceAll(correctCase);
                result.addCorrection("表名大小写修正: " + table + " → " + correctCase);
            }
        }

        return corrected;
    }

    /**
     * 修正常见拼写错误
     */
    private String correctCommonTypos(String sql, ValidationResult result) {
        String corrected = sql;

        // 常见拼写错误映射
        Map<String, String> typos = Map.of(
            "SLECT", "SELECT",
            "SELET", "SELECT",
            "FORM", "FROM",
            "WEHRE", "WHERE",
            "WHEER", "WHERE",
            "ODER", "ORDER",
            "LIMT", "LIMIT",
            "GRUOP", "GROUP",
            "HAIVNG", "HAVING"
        );

        for (Map.Entry<String, String> entry : typos.entrySet()) {
            Pattern pattern = Pattern.compile("\\b" + entry.getKey() + "\\b",
                Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(corrected);
            if (matcher.find()) {
                corrected = matcher.replaceAll(entry.getValue());
                result.addCorrection("修正拼写: " + entry.getKey() + " → " + entry.getValue());
            }
        }

        return corrected;
    }

    /**
     * 从SQL中提取表名
     */
    private List<String> extractTableNames(String sql) {
        List<String> tables = new ArrayList<>();

        // FROM 子句
        Pattern fromPattern = Pattern.compile(
            "FROM\\s+([`\\w]+(?:\\s*,\\s*[`\\w]+)*)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher fromMatcher = fromPattern.matcher(sql);
        while (fromMatcher.find()) {
            String tableList = fromMatcher.group(1);
            for (String table : tableList.split("\\s*,\\s*")) {
                tables.add(cleanTableName(table));
            }
        }

        // JOIN 子句
        Pattern joinPattern = Pattern.compile(
            "JOIN\\s+([`\\w]+)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher joinMatcher = joinPattern.matcher(sql);
        while (joinMatcher.find()) {
            tables.add(cleanTableName(joinMatcher.group(1)));
        }

        // UPDATE 子句
        Pattern updatePattern = Pattern.compile(
            "UPDATE\\s+([`\\w]+)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher updateMatcher = updatePattern.matcher(sql);
        if (updateMatcher.find()) {
            tables.add(cleanTableName(updateMatcher.group(1)));
        }

        // INSERT INTO 子句
        Pattern insertPattern = Pattern.compile(
            "INSERT\\s+INTO\\s+([`\\w]+)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher insertMatcher = insertPattern.matcher(sql);
        if (insertMatcher.find()) {
            tables.add(cleanTableName(insertMatcher.group(1)));
        }

        // DELETE FROM 子句
        Pattern deletePattern = Pattern.compile(
            "DELETE\\s+FROM\\s+([`\\w]+)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher deleteMatcher = deletePattern.matcher(sql);
        if (deleteMatcher.find()) {
            tables.add(cleanTableName(deleteMatcher.group(1)));
        }

        return tables.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 清理表名（移除反引号、别名等）
     */
    private String cleanTableName(String tableName) {
        String cleaned = tableName.trim()
            .replaceAll("`", "")
            .split("\\s+")[0];  // 移除别名
        return cleaned;
    }

    /**
     * 提取SQL中的字段引用
     */
    private List<String> extractColumnReferences(String sql) {
        List<String> columns = new ArrayList<>();

        // SELECT 子句中的字段
        Pattern selectPattern = Pattern.compile(
            "SELECT\\s+(.+?)\\s+FROM",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher selectMatcher = selectPattern.matcher(sql);
        if (selectMatcher.find()) {
            String selectList = selectMatcher.group(1);
            // 分割字段列表，但要处理函数内的逗号
            List<String> parts = splitSelectList(selectList);
            for (String part : parts) {
                String cleaned = part.trim()
                    .replaceAll("\\s+AS\\s+\\w+", "")  // 移除 AS 别名
                    .replaceAll("`", "")
                    .trim();
                if (!cleaned.isEmpty()) {
                    columns.add(cleaned);
                }
            }
        }

        // WHERE 子句中的字段
        Pattern wherePattern = Pattern.compile(
            "WHERE\\s+(.+?)(?:ORDER|GROUP|LIMIT|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher whereMatcher = wherePattern.matcher(sql);
        if (whereMatcher.find()) {
            String whereClause = whereMatcher.group(1);
            // 提取条件中的字段名
            Pattern colPattern = Pattern.compile(
                "([`\\w]+(?:\\.[`\\w]+)?)\\s*(?:=|!=|<>|>|<|>=|<=|LIKE|IN|BETWEEN|IS)",
                Pattern.CASE_INSENSITIVE
            );
            Matcher colMatcher = colPattern.matcher(whereClause);
            while (colMatcher.find()) {
                String col = colMatcher.group(1).replaceAll("`", "");
                columns.add(col);
            }
        }

        return columns;
    }

    /**
     * 分割SELECT列表（处理函数内逗号）
     */
    private List<String> splitSelectList(String selectList) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (char c : selectList.toCharArray()) {
            if (c == '(') {
                depth++;
                current.append(c);
            } else if (c == ')') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                parts.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            parts.add(current.toString().trim());
        }

        return parts;
    }

    /**
     * 判断是否为SQL函数
     */
    private boolean isFunction(String name) {
        Set<String> functions = Set.of(
            "COUNT", "SUM", "AVG", "MAX", "MIN", "CONCAT", "SUBSTRING",
            "UPPER", "LOWER", "TRIM", "LENGTH", "COALESCE", "IFNULL",
            "NOW", "DATE", "YEAR", "MONTH", "DAY"
        );
        return functions.contains(name.toUpperCase()) || name.contains("(");
    }

    /**
     * 查找相似的表名
     */
    private List<String> findSimilarTables(String tableName, List<String> allTables) {
        String lower = tableName.toLowerCase();
        return allTables.stream()
            .filter(t -> {
                String tLower = t.toLowerCase();
                // 包含关系
                if (tLower.contains(lower) || lower.contains(tLower)) {
                    return true;
                }
                // 编辑距离小于3
                return levenshteinDistance(lower, tLower) <= 3;
            })
            .limit(3)
            .collect(Collectors.toList());
    }

    /**
     * 计算编辑距离（Levenshtein Distance）
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * 验证并修正SQL（便捷方法）
     *
     * @param sql 原始SQL
     * @return 修正后的SQL（如果验证失败返回null）
     */
    public String validateAndCorrect(String sql) {
        ValidationResult result = validate(sql);
        if (result.isValid()) {
            return result.getCorrectedSql();
        }
        log.warn("SQL验证失败: {}", result.getSummary());
        return null;
    }

    /**
     * 仅修正SQL（不验证）
     */
    public String correctOnly(String sql) {
        ValidationResult result = new ValidationResult(sql);
        return autoCorrect(sql, result);
    }
}
