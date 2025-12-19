package red.jiuzhou.agent.tools;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.core.AgentContext;
import red.jiuzhou.agent.security.SqlSecurityFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查询工具
 *
 * 执行SELECT查询并返回结果
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class QueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(QueryTool.class);

    private SqlSecurityFilter securityFilter;

    /** 默认返回行数限制 */
    private int defaultLimit = 100;

    /** 最大返回行数 */
    private int maxLimit = 1000;

    public QueryTool() {
        this.securityFilter = new SqlSecurityFilter();
    }

    public QueryTool(SqlSecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }

    @Override
    public String getName() {
        return "query";
    }

    @Override
    public String getDescription() {
        return "执行SELECT查询，返回数据结果。用于查看游戏数据。";
    }

    @Override
    public String getParameterSchema() {
        return "{\n" +
               "  \"type\": \"object\",\n" +
               "  \"properties\": {\n" +
               "    \"sql\": {\n" +
               "      \"type\": \"string\",\n" +
               "      \"description\": \"要执行的SELECT SQL语句\"\n" +
               "    },\n" +
               "    \"limit\": {\n" +
               "      \"type\": \"integer\",\n" +
               "      \"description\": \"返回行数限制，默认100，最大1000\"\n" +
               "    }\n" +
               "  },\n" +
               "  \"required\": [\"sql\"]\n" +
               "}";
    }

    @Override
    public ToolResult execute(AgentContext context, String parameters) {
        log.info("执行查询工具，参数: {}", parameters);

        // 解析参数
        JSONObject params;
        try {
            params = JSON.parseObject(parameters);
        } catch (Exception e) {
            return ToolResult.error("参数解析失败: " + e.getMessage());
        }

        String sql = params.getString("sql");
        if (sql == null || sql.trim().isEmpty()) {
            return ToolResult.error("SQL语句不能为空");
        }

        Integer limit = params.getInteger("limit");
        if (limit == null) {
            limit = defaultLimit;
        } else {
            limit = Math.min(limit, maxLimit);
        }

        // 安全验证
        SqlSecurityFilter.ValidationResult validation = securityFilter.validate(sql);
        if (!validation.isValid()) {
            return ToolResult.error("SQL验证失败: " + validation.getMessage());
        }

        if (!"SELECT".equals(validation.getSqlType())) {
            return ToolResult.error("此工具只支持SELECT查询，请使用modify工具执行修改操作");
        }

        // 确保有LIMIT
        String finalSql = ensureLimit(sql, limit);

        // 执行查询
        try {
            if (context.getJdbcTemplate() == null) {
                return ToolResult.error("数据库连接未配置");
            }

            List<Map<String, Object>> results = context.getJdbcTemplate().queryForList(finalSql);

            // 提取列名
            List<String> columns = new ArrayList<>();
            if (!results.isEmpty()) {
                columns.addAll(results.get(0).keySet());
            }

            log.info("查询成功，返回 {} 条记录", results.size());
            return ToolResult.table(results, columns);

        } catch (Exception e) {
            log.error("查询执行失败", e);
            return ToolResult.error("查询执行失败: " + e.getMessage());
        }
    }

    @Override
    public boolean requiresConfirmation() {
        return false;  // 查询不需要确认
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.QUERY;
    }

    /**
     * 确保SQL有LIMIT子句
     */
    private String ensureLimit(String sql, int limit) {
        String upperSql = sql.toUpperCase().trim();

        // 如果已经有LIMIT，检查是否超过最大值
        if (upperSql.contains("LIMIT")) {
            // 尝试解析现有的LIMIT值
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "LIMIT\\s+(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(sql);
            if (matcher.find()) {
                int existingLimit = Integer.parseInt(matcher.group(1));
                if (existingLimit > maxLimit) {
                    // 替换为最大限制
                    sql = sql.substring(0, matcher.start(1)) + maxLimit + sql.substring(matcher.end(1));
                }
            }
            return sql;
        }

        // 添加LIMIT
        return sql.trim() + " LIMIT " + limit;
    }

    // ========== Getter/Setter ==========

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(int maxLimit) {
        this.maxLimit = maxLimit;
    }

    public void setSecurityFilter(SqlSecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }
}
