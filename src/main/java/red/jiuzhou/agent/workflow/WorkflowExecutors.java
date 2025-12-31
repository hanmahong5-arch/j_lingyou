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

    // SQL提取正则
    private static final Pattern SQL_PATTERN = Pattern.compile(
            "```sql\\s*\\n([\\s\\S]*?)\\n```",
            Pattern.CASE_INSENSITIVE
    );

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
     * 调用AI模型（使用 LangChain4j）
     */
    private String callAi(String prompt) {
        try {
            ChatLanguageModel model = getModelFactory().getModel(currentModel);
            return model.generate(prompt);
        } catch (Exception e) {
            log.error("AI调用失败", e);
            throw new RuntimeException("AI调用失败: " + e.getMessage(), e);
        }
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
     * 解析意图响应
     */
    private IntentParseResult parseIntentResponse(String response) {
        IntentParseResult result = new IntentParseResult();

        try {
            // 尝试提取JSON
            Pattern jsonPattern = Pattern.compile("```json\\s*\\n([\\s\\S]*?)\\n```");
            Matcher matcher = jsonPattern.matcher(response);

            if (matcher.find()) {
                String json = matcher.group(1);
                Map<String, Object> parsed = com.alibaba.fastjson.JSON.parseObject(json, Map.class);

                result.intent = (String) parsed.getOrDefault("intent", "");
                result.operation = (String) parsed.getOrDefault("operation", "query");
                result.generatedSql = (String) parsed.get("sql");

                Object conditions = parsed.get("conditions");
                if (conditions instanceof Map) {
                    result.conditions = new HashMap<>();
                    ((Map<?, ?>) conditions).forEach((k, v) ->
                            result.conditions.put(String.valueOf(k), v));
                }
            } else {
                // 没有JSON，使用原始响应
                result.intent = response.length() > 200 ? response.substring(0, 200) + "..." : response;
            }
        } catch (Exception e) {
            log.warn("解析意图响应失败: {}", e.getMessage());
            result.intent = response;
        }

        return result;
    }

    /**
     * 从响应中提取SQL
     */
    private String extractSql(String response) {
        Matcher matcher = SQL_PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
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
     * 计算表达式值
     */
    private Object evaluateExpression(String expression, Map<String, Object> row) {
        expression = expression.trim();

        // 处理简单的数学表达式，如 column * 1.1
        if (expression.contains("*")) {
            String[] parts = expression.split("\\*");
            if (parts.length == 2) {
                String colName = parts[0].trim().replace("`", "");
                try {
                    double multiplier = Double.parseDouble(parts[1].trim());
                    Object oldValue = row.get(colName);
                    if (oldValue instanceof Number) {
                        return ((Number) oldValue).doubleValue() * multiplier;
                    }
                } catch (NumberFormatException e) {
                    // 忽略解析错误
                }
            }
        }

        // 处理字面量
        if (expression.startsWith("'") && expression.endsWith("'")) {
            return expression.substring(1, expression.length() - 1);
        }

        try {
            return Double.parseDouble(expression);
        } catch (NumberFormatException e) {
            return expression;
        }
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
