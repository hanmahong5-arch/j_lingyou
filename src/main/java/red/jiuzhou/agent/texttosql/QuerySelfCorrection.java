package red.jiuzhou.agent.texttosql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询自我修正机制
 *
 * 当SQL执行失败时，自动检测错误原因并尝试修正
 * 类似Vanna.AI的查询优化功能
 *
 * @author Claude
 * @date 2025-12-20
 */
public class QuerySelfCorrection {

    private static final Logger log = LoggerFactory.getLogger(QuerySelfCorrection.class);

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, List<String>> tableCache = new HashMap<>();
    private final Map<String, List<String>> columnCache = new HashMap<>();

    public QuerySelfCorrection(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        loadDatabaseMetadata();
    }

    /**
     * 加载数据库元数据
     */
    private void loadDatabaseMetadata() {
        try {
            // 获取所有表名
            List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class
            );

            for (String table : tables) {
                tableCache.put(table.toLowerCase(), Collections.singletonList(table));

                // 获取表的列名
                List<String> columns = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = ? AND table_schema = DATABASE()",
                    String.class,
                    table
                );
                columnCache.put(table.toLowerCase(), columns);
            }

            log.info("数据库元数据加载完成，共 {} 个表", tables.size());
        } catch (Exception e) {
            log.error("加载数据库元数据失败", e);
        }
    }

    /**
     * 尝试修正SQL错误
     *
     * @param originalSql 原始SQL
     * @param errorMessage 错误信息
     * @return 修正后的SQL，如果无法修正则返回null
     */
    public String attemptCorrection(String originalSql, String errorMessage) {
        log.info("尝试修正SQL: {}", originalSql);
        log.info("错误信息: {}", errorMessage);

        // 1. 表名不存在错误
        if (errorMessage.contains("Table") && errorMessage.contains("doesn't exist")) {
            return correctTableName(originalSql, errorMessage);
        }

        // 2. 字段名不存在错误
        if (errorMessage.contains("Unknown column")) {
            return correctColumnName(originalSql, errorMessage);
        }

        // 3. 语法错误
        if (errorMessage.contains("syntax error")) {
            return correctSyntax(originalSql, errorMessage);
        }

        // 4. 类型不匹配错误
        if (errorMessage.contains("type") && errorMessage.contains("mismatch")) {
            return correctTypeMismatch(originalSql, errorMessage);
        }

        return null;
    }

    /**
     * 修正表名错误
     */
    private String correctTableName(String sql, String errorMessage) {
        // 提取错误的表名
        Pattern pattern = Pattern.compile("Table '.*?\\.(.*?)'");
        Matcher matcher = pattern.matcher(errorMessage);

        if (matcher.find()) {
            String wrongTable = matcher.group(1);
            String correctTable = findSimilarTable(wrongTable);

            if (correctTable != null) {
                String correctedSql = sql.replaceAll(
                    "\\b" + wrongTable + "\\b",
                    correctTable
                );
                log.info("表名修正: {} → {}", wrongTable, correctTable);
                return correctedSql;
            }
        }

        return null;
    }

    /**
     * 修正字段名错误
     */
    private String correctColumnName(String sql, String errorMessage) {
        // 提取错误的字段名
        Pattern pattern = Pattern.compile("Unknown column '(.*?)'");
        Matcher matcher = pattern.matcher(errorMessage);

        if (matcher.find()) {
            String wrongColumn = matcher.group(1);

            // 提取表名
            String tableName = extractTableName(sql);
            if (tableName != null) {
                String correctColumn = findSimilarColumn(tableName, wrongColumn);

                if (correctColumn != null) {
                    String correctedSql = sql.replaceAll(
                        "\\b" + wrongColumn + "\\b",
                        correctColumn
                    );
                    log.info("字段名修正: {} → {}", wrongColumn, correctColumn);
                    return correctedSql;
                }
            }
        }

        return null;
    }

    /**
     * 修正语法错误
     */
    private String correctSyntax(String sql, String errorMessage) {
        // 常见语法错误修正
        String corrected = sql;

        // 1. 缺少空格
        corrected = corrected.replaceAll("(\\w)(SELECT|FROM|WHERE|ORDER BY|GROUP BY)", "$1 $2");

        // 2. 多余的逗号
        corrected = corrected.replaceAll(",\\s*FROM", " FROM");
        corrected = corrected.replaceAll(",\\s*WHERE", " WHERE");

        // 3. 括号不匹配
        int openCount = corrected.length() - corrected.replace("(", "").length();
        int closeCount = corrected.length() - corrected.replace(")", "").length();
        if (openCount > closeCount) {
            for (int i = 0; i < (openCount - closeCount); i++) {
                corrected += ")";
            }
        }

        if (!corrected.equals(sql)) {
            log.info("语法修正完成");
            return corrected;
        }

        return null;
    }

    /**
     * 修正类型不匹配错误
     */
    private String correctTypeMismatch(String sql, String errorMessage) {
        // 尝试添加类型转换
        // 例如: WHERE id = '123' → WHERE id = 123
        String corrected = sql.replaceAll("= '(\\d+)'", "= $1");

        if (!corrected.equals(sql)) {
            log.info("类型匹配修正完成");
            return corrected;
        }

        return null;
    }

    /**
     * 查找相似的表名（基于编辑距离）
     */
    private String findSimilarTable(String wrongTable) {
        int minDistance = Integer.MAX_VALUE;
        String bestMatch = null;

        for (String table : tableCache.keySet()) {
            int distance = levenshteinDistance(wrongTable.toLowerCase(), table.toLowerCase());
            if (distance < minDistance && distance <= 3) {  // 容忍度为3
                minDistance = distance;
                bestMatch = table;
            }
        }

        return bestMatch;
    }

    /**
     * 查找相似的字段名
     */
    private String findSimilarColumn(String tableName, String wrongColumn) {
        List<String> columns = columnCache.get(tableName.toLowerCase());
        if (columns == null) {
            return null;
        }

        int minDistance = Integer.MAX_VALUE;
        String bestMatch = null;

        for (String column : columns) {
            int distance = levenshteinDistance(wrongColumn.toLowerCase(), column.toLowerCase());
            if (distance < minDistance && distance <= 3) {
                minDistance = distance;
                bestMatch = column;
            }
        }

        return bestMatch;
    }

    /**
     * 从SQL中提取表名
     */
    private String extractTableName(String sql) {
        Pattern pattern = Pattern.compile("FROM\\s+(\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
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
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * 验证SQL是否可执行
     */
    public boolean validateSql(String sql) {
        try {
            // 使用EXPLAIN来验证SQL语法
            String explainSql = "EXPLAIN " + sql;
            jdbcTemplate.queryForList(explainSql);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取SQL改进建议
     */
    public List<String> getSuggestions(String sql) {
        List<String> suggestions = new ArrayList<>();

        // 1. 建议添加LIMIT
        if (!sql.toUpperCase().contains("LIMIT")) {
            suggestions.add("建议添加LIMIT子句限制返回行数，例如: LIMIT 20");
        }

        // 2. 检查是否使用了索引字段
        // TODO: 分析查询计划

        // 3. 检查是否有潜在的性能问题
        if (sql.toUpperCase().contains("SELECT *")) {
            suggestions.add("建议明确指定需要的列，而不是使用SELECT *");
        }

        return suggestions;
    }
}
