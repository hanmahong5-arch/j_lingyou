package red.jiuzhou.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.ai.AiModelFactory;
import red.jiuzhou.ai.client.AiClient;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;
import java.util.regex.Pattern;

/**
 * SQL执行工具
 *
 * 提供SQL生成、验证和执行能力
 *
 * 核心功能:
 * - 基于自然语言生成SQL
 * - SQL安全验证
 * - 执行SQL查询
 * - 结果格式化
 *
 * @author yanxq
 * @date 2025-12-19
 */
public class SqlExecutionTool {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutionTool.class);

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSchemaProvider schemaProvider;
    private final AiClient aiClient;

    /** 危险操作关键字(禁止执行) */
    private static final Set<String> DANGEROUS_KEYWORDS = new HashSet<>(Arrays.asList(
        "DROP", "TRUNCATE", "DELETE", "UPDATE", "INSERT", "ALTER", "CREATE", "GRANT", "REVOKE"
    ));

    /** 最大查询结果数(防止大查询) */
    private static final int MAX_RESULT_ROWS = 1000;

    /**
     * SQL生成结果
     */
    public static class SqlGenerationResult {
        private boolean success;
        private String sql;
        private String explanation;
        private String error;

        public static SqlGenerationResult success(String sql, String explanation) {
            SqlGenerationResult result = new SqlGenerationResult();
            result.success = true;
            result.sql = sql;
            result.explanation = explanation;
            return result;
        }

        public static SqlGenerationResult error(String error) {
            SqlGenerationResult result = new SqlGenerationResult();
            result.success = false;
            result.error = error;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getSql() {
            return sql;
        }

        public String getExplanation() {
            return explanation;
        }

        public String getError() {
            return error;
        }
    }

    /**
     * SQL执行结果
     */
    public static class SqlExecutionResult {
        private boolean success;
        private List<Map<String, Object>> rows;
        private int rowCount;
        private long executionTimeMs;
        private String error;
        private boolean truncated;

        public static SqlExecutionResult success(List<Map<String, Object>> rows, long executionTimeMs) {
            SqlExecutionResult result = new SqlExecutionResult();
            result.success = true;
            result.rows = rows;
            result.rowCount = rows.size();
            result.executionTimeMs = executionTimeMs;
            result.truncated = rows.size() >= MAX_RESULT_ROWS;
            return result;
        }

        public static SqlExecutionResult error(String error) {
            SqlExecutionResult result = new SqlExecutionResult();
            result.success = false;
            result.error = error;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public List<Map<String, Object>> getRows() {
            return rows;
        }

        public int getRowCount() {
            return rowCount;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs;
        }

        public String getError() {
            return error;
        }

        public boolean isTruncated() {
            return truncated;
        }
    }

    public SqlExecutionTool() {
        this(DatabaseUtil.getJdbcTemplate(null), null);
    }

    public SqlExecutionTool(String aiModel) {
        this(DatabaseUtil.getJdbcTemplate(null), aiModel);
    }

    public SqlExecutionTool(JdbcTemplate jdbcTemplate, String aiModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaProvider = new DatabaseSchemaProvider(jdbcTemplate);

        // 初始化AI客户端
        if (aiModel == null || aiModel.isEmpty()) {
            aiModel = "qwen"; // 默认使用通义千问
        }
        this.aiClient = AiModelFactory.getClient(aiModel);

        log.info("SqlExecutionTool 初始化完成, AI模型: {}", aiModel);
    }

    /**
     * 基于自然语言生成SQL
     *
     * @param naturalLanguageQuery 自然语言查询
     * @return SQL生成结果
     */
    public SqlGenerationResult generateSql(String naturalLanguageQuery) {
        return generateSql(naturalLanguageQuery, null);
    }

    /**
     * 基于自然语言生成SQL(带上下文)
     *
     * @param naturalLanguageQuery 自然语言查询
     * @param relatedTables 相关表名(可选,用于减少schema上下文)
     * @return SQL生成结果
     */
    public SqlGenerationResult generateSql(String naturalLanguageQuery, List<String> relatedTables) {
        try {
            log.info("生成SQL: {}", naturalLanguageQuery);

            // 构建Prompt
            String prompt = buildSqlGenerationPrompt(naturalLanguageQuery, relatedTables);

            // 调用AI生成SQL
            long startTime = System.currentTimeMillis();
            String response = aiClient.chat(prompt);
            long aiTime = System.currentTimeMillis() - startTime;

            log.info("AI生成耗时: {} ms", aiTime);

            // 提取SQL和解释
            String sql = extractSql(response);
            String explanation = extractExplanation(response);

            if (sql == null || sql.trim().isEmpty()) {
                return SqlGenerationResult.error("AI未能生成有效的SQL语句");
            }

            // 添加LIMIT保护
            sql = addLimitIfNeeded(sql);

            log.info("生成的SQL: {}", sql);
            return SqlGenerationResult.success(sql, explanation);

        } catch (Exception e) {
            log.error("生成SQL失败", e);
            return SqlGenerationResult.error("生成失败: " + e.getMessage());
        }
    }

    /**
     * 构建SQL生成Prompt
     */
    private String buildSqlGenerationPrompt(String query, List<String> relatedTables) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个MySQL SQL专家。基于以下数据库schema,将自然语言查询转换为标准SQL。\n\n");

        // 添加Schema上下文
        if (relatedTables != null && !relatedTables.isEmpty()) {
            // 只包含相关表
            prompt.append("相关表结构:\n");
            for (String tableName : relatedTables) {
                String tableDesc = schemaProvider.getTableDescription(tableName);
                prompt.append(tableDesc).append("\n");
            }
        } else {
            // 包含所有表(简化版)
            String schemaDesc = schemaProvider.getSchemaDescription(false);
            prompt.append(schemaDesc).append("\n");
        }

        prompt.append("\n用户查询: ").append(query).append("\n\n");

        prompt.append("请按以下格式返回:\n");
        prompt.append("```sql\n");
        prompt.append("-- 这里写SQL语句\n");
        prompt.append("```\n\n");
        prompt.append("解释: (简要解释SQL的作用)\n\n");

        prompt.append("注意:\n");
        prompt.append("1. 只返回SELECT查询,不要生成DELETE/UPDATE/INSERT等修改语句\n");
        prompt.append("2. 使用标准MySQL语法\n");
        prompt.append("3. 如果需要关联多张表,使用JOIN\n");
        prompt.append("4. 字段名和表名如果是中文或特殊字符,用反引号包围\n");
        prompt.append("5. 如果查询不明确,返回最合理的解释\n");

        return prompt.toString();
    }

    /**
     * 从AI响应中提取SQL
     */
    private String extractSql(String response) {
        // 提取```sql代码块
        Pattern pattern = Pattern.compile("```sql\\s*\\n(.+?)\\n```", Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            String sql = matcher.group(1).trim();
            // 移除注释行
            sql = sql.replaceAll("--.*?\\n", "\n").trim();
            return sql;
        }

        // 如果没有代码块,尝试查找SELECT语句
        pattern = Pattern.compile("(SELECT\\s+.+?)(;|$)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    /**
     * 从AI响应中提取解释
     */
    private String extractExplanation(String response) {
        Pattern pattern = Pattern.compile("解释[:：]\\s*(.+?)($|\\n\\n)", Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(response);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return "";
    }

    /**
     * 自动添加LIMIT子句(如果需要)
     */
    private String addLimitIfNeeded(String sql) {
        String upperSql = sql.toUpperCase();

        // 如果已经有LIMIT,不重复添加
        if (upperSql.contains("LIMIT")) {
            return sql;
        }

        // 只对SELECT语句添加LIMIT
        if (upperSql.trim().startsWith("SELECT")) {
            // 移除末尾的分号
            sql = sql.replaceAll(";\\s*$", "");
            sql = sql + " LIMIT " + MAX_RESULT_ROWS;
        }

        return sql;
    }

    /**
     * 验证SQL安全性
     *
     * @param sql SQL语句
     * @return 验证结果(true=安全,false=危险)
     */
    public boolean validateSqlSafety(String sql) {
        String upperSql = sql.toUpperCase().trim();

        // 检查危险关键字
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (upperSql.contains(keyword)) {
                log.warn("SQL包含危险操作: {}", keyword);
                return false;
            }
        }

        // 只允许SELECT和WITH(CTE)
        if (!upperSql.startsWith("SELECT") && !upperSql.startsWith("WITH")) {
            log.warn("SQL不是SELECT查询");
            return false;
        }

        return true;
    }

    /**
     * 执行SQL查询
     *
     * @param sql SQL语句
     * @return 执行结果
     */
    public SqlExecutionResult executeSql(String sql) {
        try {
            // 安全验证
            if (!validateSqlSafety(sql)) {
                return SqlExecutionResult.error("SQL安全验证失败,只允许执行SELECT查询");
            }

            log.info("执行SQL: {}", sql);

            long startTime = System.currentTimeMillis();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            long executionTime = System.currentTimeMillis() - startTime;

            log.info("查询完成: {} 行, 耗时 {} ms", rows.size(), executionTime);

            return SqlExecutionResult.success(rows, executionTime);

        } catch (Exception e) {
            log.error("SQL执行失败: {}", sql, e);
            return SqlExecutionResult.error("执行失败: " + e.getMessage());
        }
    }

    /**
     * 解释SQL语句(由AI生成解释)
     *
     * @param sql SQL语句
     * @return SQL解释文本
     */
    public String explainSql(String sql) {
        try {
            String prompt = "请解释以下SQL语句的作用:\n\n```sql\n" + sql + "\n```\n\n" +
                           "用简洁的中文说明这个SQL在做什么,查询了哪些表,有什么筛选条件。";

            return aiClient.chat(prompt);

        } catch (Exception e) {
            log.error("解释SQL失败", e);
            return "解释失败: " + e.getMessage();
        }
    }

    /**
     * 优化SQL语句(由AI提供优化建议)
     *
     * @param sql SQL语句
     * @return 优化建议
     */
    public String optimizeSql(String sql) {
        try {
            String schemaContext = schemaProvider.getSchemaDescription(false);

            String prompt = "基于以下数据库schema,分析SQL并提供优化建议:\n\n" +
                           schemaContext + "\n\n" +
                           "SQL:\n```sql\n" + sql + "\n```\n\n" +
                           "请提供:\n" +
                           "1. 性能分析\n" +
                           "2. 优化建议(如索引、JOIN方式等)\n" +
                           "3. 优化后的SQL(如果有)\n";

            return aiClient.chat(prompt);

        } catch (Exception e) {
            log.error("优化SQL失败", e);
            return "优化失败: " + e.getMessage();
        }
    }

    /**
     * 智能搜索表名(基于查询意图)
     *
     * @param query 自然语言查询
     * @return 可能相关的表名列表
     */
    public List<String> suggestTables(String query) {
        List<String> allTables = schemaProvider.getAllTableNames();
        List<String> suggestions = new ArrayList<>();

        String lowerQuery = query.toLowerCase();

        // 简单的关键字匹配
        for (String table : allTables) {
            String lowerTable = table.toLowerCase();
            if (lowerQuery.contains(lowerTable) || lowerTable.contains(lowerQuery)) {
                suggestions.add(table);
            }
        }

        // 如果没有匹配,返回常用表
        if (suggestions.isEmpty()) {
            suggestions = allTables.stream()
                .filter(t -> t.toLowerCase().matches(".*(npc|item|quest|skill|player|world).*"))
                .collect(java.util.stream.Collectors.toList());
        }

        return suggestions;
    }
}
