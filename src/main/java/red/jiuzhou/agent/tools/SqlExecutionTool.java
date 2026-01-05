package red.jiuzhou.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.langchain4j.model.chat.ChatLanguageModel;
import red.jiuzhou.langchain.LangChainModelFactory;
import red.jiuzhou.util.SpringContextHolder;
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
    private final ConfigBasedSchemaProvider configProvider;
    private final SqlValidator sqlValidator;
    private final ChatLanguageModel chatModel;
    private LangChainModelFactory modelFactory;

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
        this.configProvider = new ConfigBasedSchemaProvider(jdbcTemplate);
        this.sqlValidator = new SqlValidator(jdbcTemplate);

        // 使用 LangChain4j 初始化 AI 模型
        if (aiModel == null || aiModel.isEmpty()) {
            aiModel = "qwen"; // 默认使用通义千问
        }
        this.chatModel = getModelFactory().getModel(aiModel);

        log.info("SqlExecutionTool 初始化完成 (LangChain4j), AI模型: {}, SqlValidator: enabled", aiModel);
    }

    /**
     * 获取模型工厂（延迟初始化）
     */
    private LangChainModelFactory getModelFactory() {
        if (modelFactory == null) {
            modelFactory = SpringContextHolder.getBean(LangChainModelFactory.class);
        }
        return modelFactory;
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

            // 调用 LangChain4j 生成SQL
            long startTime = System.currentTimeMillis();
            String response = chatModel.generate(prompt);
            long aiTime = System.currentTimeMillis() - startTime;

            log.info("AI生成耗时: {} ms", aiTime);

            // 提取SQL和解释
            String sql = extractSql(response);
            String explanation = extractExplanation(response);

            if (sql == null || sql.trim().isEmpty()) {
                return SqlGenerationResult.error("AI未能生成有效的SQL语句");
            }

            // 使用 SqlValidator 验证和修正 SQL
            SqlValidator.ValidationResult validationResult = sqlValidator.validate(sql);

            if (!validationResult.isValid()) {
                // 如果有致命错误，返回错误信息
                log.warn("SQL验证失败: {}", String.join("; ", validationResult.getErrors()));
                return SqlGenerationResult.error("SQL验证失败: " + String.join("; ", validationResult.getErrors()));
            }

            // 使用修正后的SQL
            sql = validationResult.getCorrectedSql();

            // 记录修正信息
            if (validationResult.hasCorrections()) {
                log.info("SQL自动修正: {}", String.join(", ", validationResult.getCorrections()));
                if (explanation == null || explanation.isEmpty()) {
                    explanation = "";
                }
                explanation += "\n[自动修正: " + String.join(", ", validationResult.getCorrections()) + "]";
            }

            // 记录警告
            if (!validationResult.getWarnings().isEmpty()) {
                log.warn("SQL验证警告: {}", String.join("; ", validationResult.getWarnings()));
            }

            log.info("生成的SQL: {}", sql);
            return SqlGenerationResult.success(sql, explanation);

        } catch (Exception e) {
            log.error("生成SQL失败", e);
            return SqlGenerationResult.error("生成失败: " + e.getMessage());
        }
    }

    /**
     * 构建SQL生成Prompt (配置驱动版,基于实际项目配置)
     */
    private String buildSqlGenerationPrompt(String query, List<String> relatedTables) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个精通Aion游戏数据库的MySQL SQL专家。基于以下数据库schema和项目配置,将自然语言查询转换为标准SQL。\n\n");

        // 智能推荐相关表(基于实际配置文件)
        if (relatedTables == null || relatedTables.isEmpty()) {
            relatedTables = configProvider.recommendRelatedTables(query);
            log.info("智能推荐相关表: {}", relatedTables);
        }

        // 使用配置驱动的Schema描述(来自JSON配置文件)
        String enhancedSchema = configProvider.getEnhancedSchemaDescription(relatedTables);
        prompt.append(enhancedSchema).append("\n");

        // 添加基于配置的SQL提示
        String hints = configProvider.generateSqlHints(query);
        if (hints != null && !hints.isEmpty()) {
            prompt.append(hints).append("\n");
        }

        prompt.append("## 用户查询\n");
        prompt.append(query).append("\n\n");

        prompt.append("## 返回格式要求\n");
        prompt.append("```sql\n");
        prompt.append("-- 这里写SQL语句\n");
        prompt.append("```\n\n");
        prompt.append("解释: (简要解释SQL的作用和查询的业务含义)\n\n");

        prompt.append("## 重要提示\n");
        prompt.append("1. **只返回SELECT查询**,不要生成DELETE/UPDATE/INSERT等修改语句\n");
        prompt.append("2. **使用标准MySQL语法**\n");
        prompt.append("3. **表关联**: 如果需要关联多张表,使用JOIN\n");
        prompt.append("4. **字段名**: 中文或特殊字符字段用反引号包围\n");
        prompt.append("5. **数据过滤**: 根据表的实际字段和配置进行过滤\n");
        prompt.append("6. **查询优化**: 优先使用索引字段(id等)进行过滤\n");

        return prompt.toString();
    }

    /**
     * 从AI响应中提取SQL（多重策略）
     *
     * 策略优先级：
     * 1. 标准 markdown sql 代码块
     * 2. 无语言标记的代码块（含SQL关键字）
     * 3. 直接 SQL 语句（以关键字开头）
     * 4. 任意代码块（最后兜底）
     */
    private String extractSql(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        String sql = null;

        // 策略1: 标准 markdown sql 代码块 (```sql ... ```)
        Pattern p1 = Pattern.compile("```sql\\s*\\n?(.+?)\\n?```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m1 = p1.matcher(response);
        if (m1.find()) {
            sql = cleanupSql(m1.group(1));
            if (isValidSql(sql)) {
                log.debug("SQL提取策略1(sql代码块)成功");
                return sql;
            }
        }

        // 策略2: 无语言标记的代码块，但内容以SQL关键字开头
        Pattern p2 = Pattern.compile("```\\s*\\n?((?:SELECT|UPDATE|INSERT|DELETE|WITH)\\s+.+?)\\n?```",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m2 = p2.matcher(response);
        if (m2.find()) {
            sql = cleanupSql(m2.group(1));
            if (isValidSql(sql)) {
                log.debug("SQL提取策略2(无标记代码块)成功");
                return sql;
            }
        }

        // 策略3: 直接 SQL 语句（以关键字开头，到分号或双换行结束）
        Pattern p3 = Pattern.compile("^\\s*(SELECT|UPDATE|INSERT|DELETE|WITH)\\s+.+?(?:;|\\n\\n|$)",
                Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        java.util.regex.Matcher m3 = p3.matcher(response);
        if (m3.find()) {
            sql = cleanupSql(m3.group(0));
            if (isValidSql(sql)) {
                log.debug("SQL提取策略3(直接SQL)成功");
                return sql;
            }
        }

        // 策略4: 任意代码块（兜底）
        Pattern p4 = Pattern.compile("```\\s*\\n?(.+?)\\n?```", Pattern.DOTALL);
        java.util.regex.Matcher m4 = p4.matcher(response);
        if (m4.find()) {
            String content = m4.group(1).trim();
            // 检查是否包含SQL关键字
            if (content.toUpperCase().contains("SELECT") ||
                content.toUpperCase().contains("FROM")) {
                sql = cleanupSql(content);
                if (isValidSql(sql)) {
                    log.debug("SQL提取策略4(兜底代码块)成功");
                    return sql;
                }
            }
        }

        // 策略5: 宽松匹配 - 查找任意SELECT语句
        Pattern p5 = Pattern.compile("SELECT\\s+.+?FROM\\s+.+?(?:WHERE\\s+.+?)?(?:LIMIT\\s+\\d+)?",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        java.util.regex.Matcher m5 = p5.matcher(response);
        if (m5.find()) {
            sql = cleanupSql(m5.group(0));
            log.debug("SQL提取策略5(宽松匹配)成功");
            return sql;
        }

        log.warn("所有SQL提取策略均失败");
        return null;
    }

    /**
     * 清理SQL字符串
     */
    private String cleanupSql(String sql) {
        if (sql == null) return null;

        // 移除注释行
        sql = sql.replaceAll("--.*?(\\n|$)", "\n");

        // 移除多余空白
        sql = sql.replaceAll("\\s+", " ");

        // 移除首尾空白和分号
        sql = sql.trim().replaceAll(";+$", "").trim();

        return sql;
    }

    /**
     * 验证SQL基本有效性
     */
    private boolean isValidSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }

        String upperSql = sql.toUpperCase().trim();

        // 必须以SQL关键字开头
        return upperSql.startsWith("SELECT") ||
               upperSql.startsWith("UPDATE") ||
               upperSql.startsWith("INSERT") ||
               upperSql.startsWith("DELETE") ||
               upperSql.startsWith("WITH");
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

            return chatModel.generate(prompt);

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

            return chatModel.generate(prompt);

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
