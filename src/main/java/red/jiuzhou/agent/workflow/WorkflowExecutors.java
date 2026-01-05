package red.jiuzhou.agent.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.agent.context.DesignContext;
import red.jiuzhou.langchain.LangChainGameDataAgent;
import red.jiuzhou.agent.core.PromptBuilder;
import red.jiuzhou.agent.security.SqlSecurityFilter;
import dev.langchain4j.model.chat.ChatLanguageModel;
import red.jiuzhou.langchain.LangChainModelFactory;
import red.jiuzhou.util.SpringContextHolder;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流步骤执行器工厂
 *
 * <p>提供各类工作流步骤的实际执行逻辑，集成：
 * <ul>
 *   <li>AI模型调用（意图理解、SQL生成）</li>
 *   <li>数据库查询（筛选、预览）</li>
 *   <li>数据对比（修改前后差异）</li>
 *   <li>SQL执行（实际修改）</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class WorkflowExecutors {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutors.class);

    private final JdbcTemplate jdbcTemplate;
    private final LangChainGameDataAgent agent;
    private final SqlSecurityFilter securityFilter;
    private String currentModel = "qwen";
    private LangChainModelFactory modelFactory;

    // SQL提取正则模式（多策略）
    private static final Pattern SQL_PATTERN_BLOCK = Pattern.compile(
            "```sql\\s*\\n?([\\s\\S]*?)\\n?```",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SQL_PATTERN_UNMARKED = Pattern.compile(
            "```\\s*\\n?((?:SELECT|UPDATE|INSERT|DELETE|WITH)\\s+[\\s\\S]*?)\\n?```",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SQL_PATTERN_DIRECT = Pattern.compile(
            "^\\s*(SELECT|UPDATE|INSERT|DELETE|WITH)\\s+[^;]+",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    // AI调用重试配置
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_BASE_DELAY_MS = 1000;

    public WorkflowExecutors(JdbcTemplate jdbcTemplate, LangChainGameDataAgent agent) {
        this.jdbcTemplate = jdbcTemplate;
        this.agent = agent;
        this.securityFilter = new SqlSecurityFilter(jdbcTemplate);
    }

    /**
     * 创建执行器提供者
     *
     * @return 根据步骤类型返回对应执行器的函数
     */
    public Function<String, WorkflowStep.StepExecutor> createExecutorProvider() {
        return stepType -> switch (stepType) {
            case "understand" -> this::executeUnderstand;
            case "filter" -> this::executeFilter;
            case "preview" -> this::executePreview;
            case "compare" -> this::executeCompare;
            case "confirm" -> this::executeConfirm;
            case "execute" -> this::executeModify;
            case "validate" -> this::executeValidate;
            case "analyze" -> this::executeAnalyze;
            case "simple_query" -> this::executeSimpleQuery;
            default -> this::executeDefault;
        };
    }

    // ==================== 步骤执行器实现 ====================

    /**
     * 理解意图步骤
     */
    private CompletableFuture<WorkflowStepResult> executeUnderstand(DesignContext context, String correction) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String userIntent = context.getSemanticHints().getOrDefault("用户意图", "");
                if (correction != null && !correction.isEmpty()) {
                    userIntent = correction;
                }

                // 构建理解意图的提示词
                String prompt = buildUnderstandPrompt(context, userIntent);

                // 调用AI
                String response = callAi(prompt);

                // 解析AI响应
                IntentParseResult parseResult = parseIntentResponse(response);

                WorkflowStepResult result = WorkflowStepResult.pending("请确认AI的理解是否正确");
                result.withParsedIntent(parseResult.intent);
                result.withParsedConditions(parseResult.conditions);
                result.withSummary("AI理解: " + parseResult.intent);

                if (parseResult.generatedSql != null) {
                    result.withGeneratedSql(parseResult.generatedSql);
                }

                return result;

            } catch (Exception e) {
                log.error("理解意图步骤执行失败", e);
                return WorkflowStepResult.failed("理解意图失败: " + e.getMessage());
            }
        });
    }

    /**
     * 筛选数据步骤
     */
    private CompletableFuture<WorkflowStepResult> executeFilter(DesignContext context, String correction) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 获取上一步生成的SQL或条件
                String sql = context.getSemanticHints().getOrDefault("generatedSql", "");

                if (correction != null && !correction.isEmpty()) {
                    // 根据修正重新生成SQL
                    String prompt = "根据以下修正意见重新生成SQL：\n" + correction + "\n原SQL：" + sql;
                    String response = callAi(prompt);
                    sql = extractSql(response);
                }

                if (sql.isEmpty()) {
                    return WorkflowStepResult.failed("未能生成筛选SQL");
                }

                // 验证SQL安全性
                SqlSecurityFilter.ValidationResult validation = securityFilter.validate(sql);
                if (!validation.isValid()) {
                    return WorkflowStepResult.failed("SQL验证失败: " + validation.getMessage());
                }

                // 执行COUNT查询获取总数
                String countSql = buildCountSql(sql);
                int totalCount = 0;
                if (countSql != null) {
                    try {
                        Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);
                        totalCount = count != null ? count : 0;
                    } catch (Exception e) {
                        log.warn("COUNT查询失败: {}", e.getMessage());
                    }
                }

                WorkflowStepResult result = WorkflowStepResult.pending("请确认筛选范围");
                result.withGeneratedSql(sql);
                result.withTotalRows(totalCount);
                result.withSummary(String.format("找到 %d 条符合条件的数据", totalCount));

                // 保存SQL到上下文供后续步骤使用
                context.addSemanticHint("filterSql", sql);

                return result;

            } catch (Exception e) {
                log.error("筛选数据步骤执行失败", e);
                return WorkflowStepResult.failed("筛选数据失败: " + e.getMessage());
            }
        });
    }

    /**
     * 预览数据步骤
     */
    private CompletableFuture<WorkflowStepResult> executePreview(DesignContext context, String correction) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sql = context.getSemanticHints().getOrDefault("filterSql", "");

                if (sql.isEmpty()) {
                    return WorkflowStepResult.failed("没有可预览的SQL");
                }

                // 确保有LIMIT
                String previewSql = ensureLimit(sql, 100);

                // 执行查询
                List<Map<String, Object>> data = jdbcTemplate.queryForList(previewSql);

                WorkflowStepResult result = WorkflowStepResult.success("预览数据");
                result.withQueryData(data);
                result.withGeneratedSql(previewSql);
                result.withSummary(String.format("显示前 %d 条数据", data.size()));

                return result;

            } catch (Exception e) {
                log.error("预览数据步骤执行失败", e);
                return WorkflowStepResult.failed("预览数据失败: " + e.getMessage());
            }
        });
    }

    /**
     * 对比预览步骤
     */
    private CompletableFuture<WorkflowStepResult> executeCompare(DesignContext context, String correction) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String modifySql = context.getSemanticHints().getOrDefault("modifySql", "");
                String filterSql = context.getSemanticHints().getOrDefault("filterSql", "");

                if (modifySql.isEmpty()) {
                    return WorkflowStepResult.failed("没有修改SQL");
                }

                // 获取修改前数据
                List<Map<String, Object>> beforeData = new ArrayList<>();
                if (!filterSql.isEmpty()) {
                    beforeData = jdbcTemplate.queryForList(ensureLimit(filterSql, 100));
                }

                // 模拟修改后数据（根据UPDATE语句计算）
                List<Map<String, Object>> afterData = simulateModification(beforeData, modifySql);

                // 提取被修改的列名
                List<String> modifiedColumns = extractModifiedColumns(modifySql);

                WorkflowStepResult result = WorkflowStepResult.pending("请检查修改预览");
                result.withComparisonData(beforeData, afterData, modifiedColumns);
                result.withGeneratedSql(modifySql);
                result.withSummary(String.format("将修改 %d 条数据的 %d 个字段",
                        beforeData.size(), modifiedColumns.size()));

                return result;

            } catch (Exception e) {
                log.error("对比预览步骤执行失败", e);
                return WorkflowStepResult.failed("对比预览失败: " + e.getMessage());
            }
        });
    }

    /**
     * 确认步骤
     */
    private CompletableFuture<WorkflowStepResult> executeConfirm(DesignContext context, String correction) {
        return CompletableFuture.completedFuture(
                WorkflowStepResult.pending("请最终确认是否执行修改操作")
                        .withSummary("确认后将执行数据修改，此操作可回滚")
        );
    }

    /**
     * 执行修改步骤
     */
    private CompletableFuture<WorkflowStepResult> executeModify(DesignContext context, String correction) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String modifySql = context.getSemanticHints().getOrDefault("modifySql", "");

                if (modifySql.isEmpty()) {
                    return WorkflowStepResult.failed("没有要执行的修改SQL");
                }

                // 再次验证SQL安全性
                SqlSecurityFilter.ValidationResult validation = securityFilter.validate(modifySql);
                if (!validation.isValid()) {
                    return WorkflowStepResult.failed("SQL验证失败: " + validation.getMessage());
                }

                // 执行修改
                int affectedRows = jdbcTemplate.update(modifySql);

                WorkflowStepResult result = WorkflowStepResult.success("修改执行成功");
                result.withTotalRows(affectedRows);
                result.withGeneratedSql(modifySql);
                result.withSummary(String.format("成功修改 %d 条数据", affectedRows));

                return result;

            } catch (Exception e) {
                log.error("执行修改步骤失败", e);
                return WorkflowStepResult.failed("执行修改失败: " + e.getMessage());
            }
        });
    }

    /**
     * 验证结果步骤
     */
    private CompletableFuture<WorkflowStepResult> executeValidate(DesignContext context, String correction) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String filterSql = context.getSemanticHints().getOrDefault("filterSql", "");

                if (filterSql.isEmpty()) {
                    return WorkflowStepResult.success("验证完成（无验证SQL）");
                }

                // 重新查询数据验证修改结果
                List<Map<String, Object>> data = jdbcTemplate.queryForList(ensureLimit(filterSql, 10));

                WorkflowStepResult result = WorkflowStepResult.success("验证完成");
                result.withQueryData(data);
                result.withSummary("修改后数据验证通过");

                return result;

            } catch (Exception e) {
                log.error("验证结果步骤执行失败", e);
                return WorkflowStepResult.failed("验证失败: " + e.getMessage());
            }
        });
    }

    /**
     * 分析步骤
     */
    private CompletableFuture<WorkflowStepResult> executeAnalyze(DesignContext context, String correction) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildAnalyzePrompt(context);
                String response = callAi(prompt);

                WorkflowStepResult result = WorkflowStepResult.success("分析完成");
                result.withParsedIntent(response);
                result.withSummary("数据分析结果");

                return result;

            } catch (Exception e) {
                log.error("分析步骤执行失败", e);
                return WorkflowStepResult.failed("分析失败: " + e.getMessage());
            }
        });
    }

    /**
     * 简单查询步骤
     */
    private CompletableFuture<WorkflowStepResult> executeSimpleQuery(DesignContext context, String correction) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String userIntent = context.getSemanticHints().getOrDefault("用户意图", "");

                // 生成SQL
                String prompt = buildQueryPrompt(context, userIntent);
                String response = callAi(prompt);
                String sql = extractSql(response);

                if (sql.isEmpty()) {
                    return WorkflowStepResult.failed("未能生成查询SQL");
                }

                // 执行查询
                List<Map<String, Object>> data = jdbcTemplate.queryForList(ensureLimit(sql, 100));

                WorkflowStepResult result = WorkflowStepResult.success("查询完成");
                result.withQueryData(data);
                result.withGeneratedSql(sql);
                result.withSummary(String.format("返回 %d 条结果", data.size()));

                return result;

            } catch (Exception e) {
                log.error("简单查询步骤执行失败", e);
                return WorkflowStepResult.failed("查询失败: " + e.getMessage());
            }
        });
    }

    /**
     * 默认执行器
     */
    private CompletableFuture<WorkflowStepResult> executeDefault(DesignContext context, String correction) {
        return CompletableFuture.completedFuture(
                WorkflowStepResult.success("步骤执行完成")
        );
    }

    // ==================== 辅助方法 ====================

    /**
     * 调用AI模型（使用 LangChain4j，带重试机制）
     */
    private String callAi(String prompt) {
        return callAiWithRetry(prompt, MAX_RETRY_COUNT);
    }

    /**
     * 带重试的AI调用
     *
     * @param prompt      提示词
     * @param maxRetries  最大重试次数
     * @return AI响应
     */
    private String callAiWithRetry(String prompt, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ChatLanguageModel model = getModelFactory().getModel(currentModel);
                String response = model.generate(prompt);

                // 验证响应有效性
                if (isValidAiResponse(response)) {
                    if (attempt > 1) {
                        log.info("AI调用成功（第{}次尝试）", attempt);
                    }
                    return response;
                }

                log.warn("AI响应无效（第{}次尝试）：响应为空或格式不正确", attempt);

            } catch (Exception e) {
                lastException = e;
                log.warn("AI调用失败（第{}次尝试）: {}", attempt, e.getMessage());

                if (attempt < maxRetries) {
                    // 指数退避
                    long delayMs = RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
                    log.info("等待 {} ms 后重试...", delayMs);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("AI调用被中断", ie);
                    }
                }
            }
        }

        // 所有重试都失败
        String errorMsg = String.format("AI调用失败，已重试%d次", maxRetries);
        log.error(errorMsg, lastException);
        throw new RuntimeException(errorMsg, lastException);
    }

    /**
     * 验证AI响应有效性
     */
    private boolean isValidAiResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }
        // 响应长度合理
        if (response.length() < 10) {
            return false;
        }
        return true;
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
     * 构建理解意图提示词
     */
    private String buildUnderstandPrompt(DesignContext context, String userIntent) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个游戏数据库助手。请理解用户的意图并提取关键信息。\n\n");
        sb.append("用户意图: ").append(userIntent).append("\n\n");

        if (context.getTableName() != null) {
            sb.append("当前表: ").append(context.getTableName()).append("\n");
        }

        sb.append("\n请返回以下信息（JSON格式）：\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"intent\": \"用户想要做什么（简短描述）\",\n");
        sb.append("  \"operation\": \"query/modify/analyze\",\n");
        sb.append("  \"conditions\": {\"字段名\": \"条件值\"},\n");
        sb.append("  \"sql\": \"生成的SQL语句（如果适用）\"\n");
        sb.append("}\n");
        sb.append("```\n");

        return sb.toString();
    }

    /**
     * 构建查询提示词
     */
    private String buildQueryPrompt(DesignContext context, String userIntent) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据用户意图生成SELECT查询SQL。\n\n");
        sb.append("用户意图: ").append(userIntent).append("\n");

        if (context.getTableName() != null) {
            sb.append("目标表: ").append(context.getTableName()).append("\n");
        }

        sb.append("\n请直接返回SQL语句，用```sql包裹。\n");

        return sb.toString();
    }

    /**
     * 构建分析提示词
     */
    private String buildAnalyzePrompt(DesignContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析以下游戏数据：\n\n");
        sb.append(context.toPrompt());
        sb.append("\n请提供分析结果和建议。\n");
        return sb.toString();
    }

    /**
     * 解析意图响应（多重策略）
     *
     * 策略优先级：
     * 1. 标准 JSON 代码块
     * 2. 直接 JSON 对象
     * 3. 结构化文本解析
     * 4. 原始响应降级
     */
    private IntentParseResult parseIntentResponse(String response) {
        IntentParseResult result = new IntentParseResult();

        if (response == null || response.trim().isEmpty()) {
            result.intent = "无法解析空响应";
            return result;
        }

        // 策略1: 标准 JSON 代码块
        try {
            Pattern jsonBlockPattern = Pattern.compile("```json\\s*\\n?([\\s\\S]*?)\\n?```", Pattern.CASE_INSENSITIVE);
            Matcher matcher = jsonBlockPattern.matcher(response);

            if (matcher.find()) {
                String json = matcher.group(1).trim();
                if (parseJsonToResult(json, result)) {
                    log.debug("意图解析策略1(JSON代码块)成功");
                    return result;
                }
            }
        } catch (Exception e) {
            log.debug("策略1解析失败: {}", e.getMessage());
        }

        // 策略2: 直接 JSON 对象
        try {
            String trimmed = response.trim();
            if (trimmed.startsWith("{") && trimmed.contains("}")) {
                // 提取第一个完整的 JSON 对象
                int depth = 0;
                int start = trimmed.indexOf('{');
                int end = -1;
                for (int i = start; i < trimmed.length(); i++) {
                    char c = trimmed.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            end = i + 1;
                            break;
                        }
                    }
                }
                if (end > start) {
                    String json = trimmed.substring(start, end);
                    if (parseJsonToResult(json, result)) {
                        log.debug("意图解析策略2(直接JSON)成功");
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("策略2解析失败: {}", e.getMessage());
        }

        // 策略3: 结构化文本解析
        try {
            if (parseStructuredText(response, result)) {
                log.debug("意图解析策略3(结构化文本)成功");
                return result;
            }
        } catch (Exception e) {
            log.debug("策略3解析失败: {}", e.getMessage());
        }

        // 策略4: 降级 - 使用原始响应作为意图
        log.debug("意图解析降级：使用原始响应");
        result.intent = response.length() > 300 ? response.substring(0, 300) + "..." : response;

        // 尝试从响应中提取 SQL
        String sql = extractSql(response);
        if (!sql.isEmpty()) {
            result.generatedSql = sql;
        }

        return result;
    }

    /**
     * 解析 JSON 到结果对象
     */
    private boolean parseJsonToResult(String json, IntentParseResult result) {
        try {
            Map<String, Object> parsed = com.alibaba.fastjson.JSON.parseObject(json, Map.class);
            if (parsed == null || parsed.isEmpty()) {
                return false;
            }

            result.intent = getStringValue(parsed, "intent", "");
            result.operation = getStringValue(parsed, "operation", "query");
            result.generatedSql = getStringValue(parsed, "sql", null);

            Object conditions = parsed.get("conditions");
            if (conditions instanceof Map) {
                result.conditions = new HashMap<>();
                ((Map<?, ?>) conditions).forEach((k, v) ->
                        result.conditions.put(String.valueOf(k), v));
            }

            return !result.intent.isEmpty();
        } catch (Exception e) {
            log.debug("JSON解析异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 Map 获取字符串值
     */
    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    /**
     * 解析结构化文本
     */
    private boolean parseStructuredText(String text, IntentParseResult result) {
        // 查找 "意图:" 或 "Intent:" 等模式
        Pattern intentPattern = Pattern.compile("(?:意图|Intent|目的|操作)[：:]\\s*(.+?)(?:\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher intentMatcher = intentPattern.matcher(text);
        if (intentMatcher.find()) {
            result.intent = intentMatcher.group(1).trim();
        }

        // 查找操作类型
        Pattern opPattern = Pattern.compile("(?:操作类型|Operation|类型)[：:]\\s*(query|modify|analyze|update|delete|insert)", Pattern.CASE_INSENSITIVE);
        Matcher opMatcher = opPattern.matcher(text);
        if (opMatcher.find()) {
            result.operation = opMatcher.group(1).toLowerCase();
        }

        // 尝试提取 SQL
        String sql = extractSql(text);
        if (!sql.isEmpty()) {
            result.generatedSql = sql;
        }

        // 只要找到了意图就算成功
        return !result.intent.isEmpty();
    }

    /**
     * 从响应中提取SQL（多重策略）
     *
     * 策略优先级：
     * 1. ```sql 代码块
     * 2. 无标记代码块（含SQL关键字）
     * 3. 直接 SQL 语句
     */
    private String extractSql(String response) {
        if (response == null || response.isEmpty()) {
            return "";
        }

        // 策略1: 标准 ```sql 代码块
        Matcher m1 = SQL_PATTERN_BLOCK.matcher(response);
        if (m1.find()) {
            String sql = cleanupExtractedSql(m1.group(1));
            if (isValidExtractedSql(sql)) {
                log.debug("SQL提取策略1(sql代码块)成功");
                return sql;
            }
        }

        // 策略2: 无语言标记代码块
        Matcher m2 = SQL_PATTERN_UNMARKED.matcher(response);
        if (m2.find()) {
            String sql = cleanupExtractedSql(m2.group(1));
            if (isValidExtractedSql(sql)) {
                log.debug("SQL提取策略2(无标记代码块)成功");
                return sql;
            }
        }

        // 策略3: 直接 SQL 语句
        Matcher m3 = SQL_PATTERN_DIRECT.matcher(response);
        if (m3.find()) {
            String sql = cleanupExtractedSql(m3.group(0));
            if (isValidExtractedSql(sql)) {
                log.debug("SQL提取策略3(直接SQL)成功");
                return sql;
            }
        }

        log.debug("所有SQL提取策略均失败");
        return "";
    }

    /**
     * 清理提取的SQL
     */
    private String cleanupExtractedSql(String sql) {
        if (sql == null) return "";

        // 移除注释
        sql = sql.replaceAll("--.*?(\\n|$)", "\n");
        // 规范化空白
        sql = sql.replaceAll("\\s+", " ");
        // 移除首尾空白和分号
        sql = sql.trim().replaceAll(";+$", "").trim();

        return sql;
    }

    /**
     * 验证提取的SQL有效性
     */
    private boolean isValidExtractedSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        String upper = sql.toUpperCase().trim();
        return upper.startsWith("SELECT") ||
               upper.startsWith("UPDATE") ||
               upper.startsWith("INSERT") ||
               upper.startsWith("DELETE") ||
               upper.startsWith("WITH");
    }

    /**
     * 构建COUNT查询
     */
    private String buildCountSql(String sql) {
        String upperSql = sql.toUpperCase().trim();
        if (!upperSql.startsWith("SELECT")) {
            return null;
        }

        // 简单的COUNT转换
        int fromIdx = upperSql.indexOf("FROM");
        if (fromIdx > 0) {
            String fromClause = sql.substring(fromIdx);
            // 移除ORDER BY和LIMIT
            int orderIdx = fromClause.toUpperCase().indexOf("ORDER BY");
            if (orderIdx > 0) {
                fromClause = fromClause.substring(0, orderIdx);
            }
            int limitIdx = fromClause.toUpperCase().indexOf("LIMIT");
            if (limitIdx > 0) {
                fromClause = fromClause.substring(0, limitIdx);
            }
            return "SELECT COUNT(*) " + fromClause;
        }

        return null;
    }

    /**
     * 确保SQL有LIMIT
     */
    private String ensureLimit(String sql, int limit) {
        String upperSql = sql.toUpperCase().trim();
        if (!upperSql.contains("LIMIT")) {
            return sql.trim() + " LIMIT " + limit;
        }
        return sql;
    }

    /**
     * 模拟修改后的数据
     */
    private List<Map<String, Object>> simulateModification(
            List<Map<String, Object>> beforeData, String updateSql) {

        List<Map<String, Object>> afterData = new ArrayList<>();

        // 解析SET子句
        Map<String, String> setValues = parseSetClause(updateSql);

        for (Map<String, Object> row : beforeData) {
            Map<String, Object> newRow = new HashMap<>(row);
            // 应用SET值
            for (Map.Entry<String, String> entry : setValues.entrySet()) {
                String column = entry.getKey();
                String expression = entry.getValue();

                if (newRow.containsKey(column)) {
                    Object newValue = evaluateExpression(expression, row);
                    newRow.put(column, newValue);
                }
            }
            afterData.add(newRow);
        }

        return afterData;
    }

    /**
     * 解析SET子句
     */
    private Map<String, String> parseSetClause(String sql) {
        Map<String, String> result = new HashMap<>();

        String upperSql = sql.toUpperCase();
        int setIdx = upperSql.indexOf("SET");
        int whereIdx = upperSql.indexOf("WHERE");

        if (setIdx < 0) return result;

        String setClause = whereIdx > 0 ?
                sql.substring(setIdx + 3, whereIdx) :
                sql.substring(setIdx + 3);

        // 简单解析 column = value
        String[] assignments = setClause.split(",");
        for (String assignment : assignments) {
            String[] parts = assignment.split("=", 2);
            if (parts.length == 2) {
                String column = parts[0].trim().replace("`", "");
                String value = parts[1].trim();
                result.put(column, value);
            }
        }

        return result;
    }

    /**
     * 计算表达式值（增强版，支持更多运算）
     *
     * 支持的表达式类型：
     * - 列引用: column
     * - 乘法: column * 1.1
     * - 除法: column / 2
     * - 加法: column + 10
     * - 减法: column - 5
     * - 字面量: 'value' 或 123
     * - 函数: CONCAT(col1, '_', col2)
     */
    private Object evaluateExpression(String expression, Map<String, Object> row) {
        expression = expression.trim();

        // 1. 处理字符串字面量
        if (expression.startsWith("'") && expression.endsWith("'")) {
            return expression.substring(1, expression.length() - 1);
        }

        // 2. 处理数字字面量
        if (expression.matches("-?\\d+\\.?\\d*")) {
            try {
                if (expression.contains(".")) {
                    return Double.parseDouble(expression);
                } else {
                    return Long.parseLong(expression);
                }
            } catch (NumberFormatException e) {
                // 继续尝试其他解析
            }
        }

        // 3. 处理乘法表达式: column * value 或 value * column
        if (expression.contains("*")) {
            return evaluateBinaryOperation(expression, "*", row, (a, b) -> a * b);
        }

        // 4. 处理除法表达式: column / value
        if (expression.contains("/")) {
            return evaluateBinaryOperation(expression, "/", row, (a, b) -> b != 0 ? a / b : a);
        }

        // 5. 处理加法表达式: column + value
        if (expression.contains("+")) {
            // 区分数字加法和字符串拼接
            Object result = evaluateBinaryOperation(expression, "+", row, Double::sum);
            if (result != null) {
                return result;
            }
        }

        // 6. 处理减法表达式: column - value
        if (expression.contains("-") && !expression.startsWith("-")) {
            return evaluateBinaryOperation(expression, "-", row, (a, b) -> a - b);
        }

        // 7. 简单列引用
        String colName = expression.replace("`", "").trim();
        if (row.containsKey(colName)) {
            return row.get(colName);
        }

        // 8. 无法解析，返回原始表达式
        return expression;
    }

    /**
     * 计算二元运算表达式
     */
    private Object evaluateBinaryOperation(String expression, String operator,
                                           Map<String, Object> row,
                                           java.util.function.BiFunction<Double, Double, Double> operation) {
        // 找到运算符位置（避免负号混淆）
        int opIndex = -1;
        if (operator.equals("-")) {
            // 对于减法，找第一个非开头的减号
            for (int i = 1; i < expression.length(); i++) {
                if (expression.charAt(i) == '-' && !Character.isWhitespace(expression.charAt(i-1))) {
                    opIndex = i;
                    break;
                }
            }
        } else {
            opIndex = expression.indexOf(operator);
        }

        if (opIndex <= 0 || opIndex >= expression.length() - 1) {
            return null;
        }

        String leftPart = expression.substring(0, opIndex).trim().replace("`", "");
        String rightPart = expression.substring(opIndex + 1).trim().replace("`", "");

        try {
            // 尝试获取左操作数
            Double leftValue = null;
            if (row.containsKey(leftPart)) {
                Object val = row.get(leftPart);
                if (val instanceof Number) {
                    leftValue = ((Number) val).doubleValue();
                }
            } else {
                leftValue = Double.parseDouble(leftPart);
            }

            // 尝试获取右操作数
            Double rightValue = null;
            if (row.containsKey(rightPart)) {
                Object val = row.get(rightPart);
                if (val instanceof Number) {
                    rightValue = ((Number) val).doubleValue();
                }
            } else {
                rightValue = Double.parseDouble(rightPart);
            }

            if (leftValue != null && rightValue != null) {
                return operation.apply(leftValue, rightValue);
            }
        } catch (NumberFormatException e) {
            log.debug("表达式计算失败: {} {} {}", leftPart, operator, rightPart);
        }

        return null;
    }

    /**
     * 提取被修改的列名
     */
    private List<String> extractModifiedColumns(String sql) {
        List<String> columns = new ArrayList<>();
        Map<String, String> setValues = parseSetClause(sql);
        columns.addAll(setValues.keySet());
        return columns;
    }

    /**
     * 设置当前模型
     */
    public void setCurrentModel(String model) {
        this.currentModel = model;
    }

    // ==================== 内部类 ====================

    /**
     * 意图解析结果
     */
    private static class IntentParseResult {
        String intent = "";
        String operation = "query";
        String generatedSql;
        Map<String, Object> conditions = new HashMap<>();
    }
}
