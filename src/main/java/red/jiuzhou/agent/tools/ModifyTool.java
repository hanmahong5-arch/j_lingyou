package red.jiuzhou.agent.tools;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.core.AgentContext;
import red.jiuzhou.agent.security.SqlSecurityFilter;

import java.util.*;

/**
 * 修改工具
 *
 * 生成UPDATE/INSERT/DELETE操作
 * 需要用户确认后才能执行
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class ModifyTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ModifyTool.class);

    private SqlSecurityFilter securityFilter;

    /** 预览数据条数 */
    private int previewRows = 5;

    public ModifyTool() {
        this.securityFilter = new SqlSecurityFilter();
    }

    public ModifyTool(SqlSecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }

    @Override
    public String getName() {
        return "modify";
    }

    @Override
    public String getDescription() {
        return "生成数据修改SQL（UPDATE/INSERT/DELETE），需要用户确认后执行。";
    }

    @Override
    public String getParameterSchema() {
        return "{\n" +
               "  \"type\": \"object\",\n" +
               "  \"properties\": {\n" +
               "    \"sql\": {\n" +
               "      \"type\": \"string\",\n" +
               "      \"description\": \"要执行的SQL语句（UPDATE/INSERT/DELETE）\"\n" +
               "    },\n" +
               "    \"description\": {\n" +
               "      \"type\": \"string\",\n" +
               "      \"description\": \"操作描述，说明这个修改的目的\"\n" +
               "    }\n" +
               "  },\n" +
               "  \"required\": [\"sql\", \"description\"]\n" +
               "}";
    }

    @Override
    public ToolResult execute(AgentContext context, String parameters) {
        log.info("执行修改工具，参数: {}", parameters);

        // 解析参数
        JSONObject params;
        try {
            params = JSON.parseObject(parameters);
        } catch (Exception e) {
            return ToolResult.error("参数解析失败: " + e.getMessage());
        }

        String sql = params.getString("sql");
        String description = params.getString("description");

        if (sql == null || sql.trim().isEmpty()) {
            return ToolResult.error("SQL语句不能为空");
        }

        if (description == null || description.trim().isEmpty()) {
            return ToolResult.error("请提供操作描述");
        }

        // 安全验证
        if (securityFilter != null) {
            securityFilter.setJdbcTemplate(context.getJdbcTemplate());
        }

        SqlSecurityFilter.ValidationResult validation = securityFilter.validate(sql);
        if (!validation.isValid()) {
            return ToolResult.error("SQL验证失败: " + validation.getMessage());
        }

        String sqlType = validation.getSqlType();
        if ("SELECT".equals(sqlType)) {
            return ToolResult.error("此工具不支持SELECT，请使用query工具");
        }

        // 获取预览数据
        List<Map<String, Object>> previewData = new ArrayList<>();
        int estimatedRows = validation.getEstimatedAffectedRows();

        try {
            if (context.getJdbcTemplate() != null) {
                previewData = getPreviewData(context, sql, sqlType);

                // 如果验证没有估算行数，使用预览数据的数量
                if (estimatedRows == 0 && !previewData.isEmpty()) {
                    // 通过COUNT查询获取精确数量
                    estimatedRows = getAffectedRowCount(context, sql, sqlType);
                }
            }
        } catch (Exception e) {
            log.warn("获取预览数据失败: {}", e.getMessage());
        }

        // 生成操作ID
        String operationId = UUID.randomUUID().toString().substring(0, 8);

        // 创建待确认操作
        AgentContext.PendingOperation pending = new AgentContext.PendingOperation();
        pending.setOperationId(operationId);
        pending.setSql(sql);
        pending.setOperationType(sqlType);
        pending.setEstimatedAffectedRows(estimatedRows);
        pending.setTimestamp(System.currentTimeMillis());

        Map<String, Object> previewMap = new HashMap<>();
        previewMap.put("description", description);
        previewMap.put("data", previewData);
        pending.setPreviewData(previewMap);

        // 设置到上下文
        context.setPendingOperation(pending);

        // 返回待确认结果
        ToolResult result = ToolResult.pendingConfirmation(sql, previewData, estimatedRows, operationId);
        result.setMessage(buildConfirmationMessage(sqlType, description, sql, estimatedRows, previewData));

        // 添加警告信息
        for (String warning : validation.getWarnings()) {
            // 可以添加到extra data中
        }

        log.info("修改操作已生成，等待确认。操作ID: {}, 预计影响: {} 行", operationId, estimatedRows);
        return result;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.MODIFY;
    }

    /**
     * 获取预览数据
     */
    private List<Map<String, Object>> getPreviewData(AgentContext context, String sql, String sqlType) {
        if (context.getJdbcTemplate() == null) {
            return Collections.emptyList();
        }

        try {
            // 构建预览查询
            String previewSql = buildPreviewSql(sql, sqlType);
            if (previewSql != null) {
                return context.getJdbcTemplate().queryForList(previewSql);
            }
        } catch (Exception e) {
            log.debug("构建预览查询失败: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * 构建预览SQL
     */
    private String buildPreviewSql(String sql, String sqlType) {
        String upperSql = sql.toUpperCase().trim();

        if ("UPDATE".equals(sqlType) || "DELETE".equals(sqlType)) {
            // 从UPDATE/DELETE提取表名和WHERE条件
            String tableName = securityFilter.extractTableName(sql);
            if (tableName == null) {
                return null;
            }

            // 查找WHERE子句
            int whereIdx = upperSql.indexOf("WHERE");
            if (whereIdx > 0) {
                String whereClause = sql.substring(
                    sql.toUpperCase().indexOf("WHERE"));
                return String.format("SELECT * FROM `%s` %s LIMIT %d",
                    tableName, whereClause, previewRows);
            }
        }

        return null;
    }

    /**
     * 获取影响行数
     */
    private int getAffectedRowCount(AgentContext context, String sql, String sqlType) {
        if (context.getJdbcTemplate() == null) {
            return 0;
        }

        try {
            String tableName = securityFilter.extractTableName(sql);
            if (tableName == null) {
                return 0;
            }

            String upperSql = sql.toUpperCase();
            int whereIdx = upperSql.indexOf("WHERE");
            if (whereIdx > 0) {
                String whereClause = sql.substring(
                    sql.toUpperCase().indexOf("WHERE"));
                String countSql = String.format("SELECT COUNT(*) FROM `%s` %s",
                    tableName, whereClause);
                Integer count = context.getJdbcTemplate().queryForObject(countSql, Integer.class);
                return count != null ? count : 0;
            }
        } catch (Exception e) {
            log.debug("获取影响行数失败: {}", e.getMessage());
        }

        return 0;
    }

    /**
     * 构建确认消息
     */
    private String buildConfirmationMessage(String sqlType, String description,
                                           String sql, int estimatedRows,
                                           List<Map<String, Object>> previewData) {
        StringBuilder sb = new StringBuilder();

        sb.append("## ⚠️ 待确认操作\n\n");
        sb.append("**操作类型**: ").append(sqlType).append("\n");
        sb.append("**操作说明**: ").append(description).append("\n");
        sb.append("**预计影响**: ").append(estimatedRows).append(" 行\n\n");

        sb.append("### SQL语句\n");
        sb.append("```sql\n").append(sql).append("\n```\n\n");

        if (!previewData.isEmpty()) {
            sb.append("### 将被影响的数据（前").append(previewData.size()).append("条）\n");
            sb.append("```\n");
            for (Map<String, Object> row : previewData) {
                sb.append(row.toString()).append("\n");
            }
            sb.append("```\n\n");
        }

        sb.append("---\n");
        sb.append("请输入 **确认** 执行操作，或 **取消** 放弃操作。");

        return sb.toString();
    }

    // ========== Getter/Setter ==========

    public int getPreviewRows() {
        return previewRows;
    }

    public void setPreviewRows(int previewRows) {
        this.previewRows = previewRows;
    }

    public void setSecurityFilter(SqlSecurityFilter securityFilter) {
        this.securityFilter = securityFilter;
    }
}
