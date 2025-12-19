package red.jiuzhou.agent.tools;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.core.AgentContext;
import red.jiuzhou.agent.history.OperationLog;
import red.jiuzhou.agent.history.OperationLogger;

import java.util.*;

/**
 * 历史工具
 *
 * 查看操作历史和回滚操作
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class HistoryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(HistoryTool.class);

    private OperationLogger operationLogger;

    public HistoryTool() {
    }

    public HistoryTool(OperationLogger operationLogger) {
        this.operationLogger = operationLogger;
    }

    @Override
    public String getName() {
        return "history";
    }

    @Override
    public String getDescription() {
        return "查看操作历史记录，支持按时间筛选和回滚操作。";
    }

    @Override
    public String getParameterSchema() {
        return "{\n" +
               "  \"type\": \"object\",\n" +
               "  \"properties\": {\n" +
               "    \"action\": {\n" +
               "      \"type\": \"string\",\n" +
               "      \"enum\": [\"list\", \"detail\", \"rollback\"],\n" +
               "      \"description\": \"操作类型：list=列出历史, detail=查看详情, rollback=回滚\"\n" +
               "    },\n" +
               "    \"operation_id\": {\n" +
               "      \"type\": \"string\",\n" +
               "      \"description\": \"操作ID（detail和rollback时需要）\"\n" +
               "    },\n" +
               "    \"limit\": {\n" +
               "      \"type\": \"integer\",\n" +
               "      \"description\": \"返回记录数限制，默认20\"\n" +
               "    },\n" +
               "    \"since\": {\n" +
               "      \"type\": \"string\",\n" +
               "      \"description\": \"起始时间（格式：yyyy-MM-dd HH:mm:ss）\"\n" +
               "    }\n" +
               "  },\n" +
               "  \"required\": [\"action\"]\n" +
               "}";
    }

    @Override
    public ToolResult execute(AgentContext context, String parameters) {
        log.info("执行历史工具，参数: {}", parameters);

        // 解析参数
        JSONObject params;
        try {
            params = JSON.parseObject(parameters);
        } catch (Exception e) {
            return ToolResult.error("参数解析失败: " + e.getMessage());
        }

        String action = params.getString("action");
        if (action == null) {
            action = "list";
        }

        if (operationLogger == null) {
            return ToolResult.error("操作日志服务未配置");
        }

        switch (action) {
            case "list":
                return listHistory(params);
            case "detail":
                return getDetail(params);
            case "rollback":
                return rollback(context, params);
            default:
                return ToolResult.error("未知操作类型: " + action);
        }
    }

    @Override
    public boolean requiresConfirmation() {
        return false;  // 查看历史不需要确认，回滚会单独确认
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.HISTORY;
    }

    /**
     * 列出历史记录
     */
    private ToolResult listHistory(JSONObject params) {
        Integer limit = params.getInteger("limit");
        if (limit == null) {
            limit = 20;
        }

        String since = params.getString("since");

        List<OperationLog> logs;
        if (since != null) {
            logs = operationLogger.getLogsSince(since, limit);
        } else {
            logs = operationLogger.getRecentLogs(limit);
        }

        if (logs.isEmpty()) {
            return ToolResult.text("暂无操作记录");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 操作历史记录\n\n");
        sb.append("| 操作ID | 时间 | 类型 | 表名 | 影响行数 | 状态 |\n");
        sb.append("|--------|------|------|------|----------|------|\n");

        for (OperationLog log : logs) {
            sb.append("| ").append(log.getOperationId());
            sb.append(" | ").append(log.getFormattedTimestamp());
            sb.append(" | ").append(log.getOperationType());
            sb.append(" | ").append(log.getTableName());
            sb.append(" | ").append(log.getAffectedRows());
            sb.append(" | ").append(log.isSuccess() ? "✅" : "❌");
            sb.append(" |\n");
        }

        sb.append("\n使用 `history detail <操作ID>` 查看详情");
        sb.append("\n使用 `history rollback <操作ID>` 回滚操作");

        return ToolResult.text(sb.toString());
    }

    /**
     * 查看操作详情
     */
    private ToolResult getDetail(JSONObject params) {
        String operationId = params.getString("operation_id");
        if (operationId == null || operationId.isEmpty()) {
            return ToolResult.error("请提供操作ID");
        }

        OperationLog log = operationLogger.getLog(operationId);
        if (log == null) {
            return ToolResult.error("未找到操作记录: " + operationId);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 操作详情: ").append(operationId).append("\n\n");

        sb.append("**操作时间**: ").append(log.getFormattedTimestamp()).append("\n");
        sb.append("**操作类型**: ").append(log.getOperationType()).append("\n");
        sb.append("**目标表**: ").append(log.getTableName()).append("\n");
        sb.append("**影响行数**: ").append(log.getAffectedRows()).append("\n");
        sb.append("**执行状态**: ").append(log.isSuccess() ? "成功" : "失败").append("\n\n");

        sb.append("### 执行的SQL\n");
        sb.append("```sql\n").append(log.getSqlStatement()).append("\n```\n\n");

        if (log.getRollbackSql() != null && !log.getRollbackSql().isEmpty()) {
            sb.append("### 回滚SQL\n");
            sb.append("```sql\n").append(log.getRollbackSql()).append("\n```\n\n");
        }

        if (log.getBeforeState() != null && !log.getBeforeState().isEmpty()) {
            sb.append("### 操作前数据（前5条）\n");
            sb.append("```\n").append(formatState(log.getBeforeState())).append("```\n\n");
        }

        if (log.getAfterState() != null && !log.getAfterState().isEmpty()) {
            sb.append("### 操作后数据（前5条）\n");
            sb.append("```\n").append(formatState(log.getAfterState())).append("```\n");
        }

        return ToolResult.text(sb.toString());
    }

    /**
     * 回滚操作
     */
    private ToolResult rollback(AgentContext context, JSONObject params) {
        String operationId = params.getString("operation_id");
        if (operationId == null || operationId.isEmpty()) {
            return ToolResult.error("请提供要回滚的操作ID");
        }

        OperationLog log = operationLogger.getLog(operationId);
        if (log == null) {
            return ToolResult.error("未找到操作记录: " + operationId);
        }

        if (!log.isSuccess()) {
            return ToolResult.error("该操作执行失败，无需回滚");
        }

        String rollbackSql = log.getRollbackSql();
        if (rollbackSql == null || rollbackSql.isEmpty()) {
            return ToolResult.error("该操作没有生成回滚SQL，无法回滚");
        }

        // 创建回滚的待确认操作
        String rollbackOperationId = UUID.randomUUID().toString().substring(0, 8);

        AgentContext.PendingOperation pending = new AgentContext.PendingOperation();
        pending.setOperationId(rollbackOperationId);
        pending.setSql(rollbackSql);
        pending.setOperationType("ROLLBACK");
        pending.setEstimatedAffectedRows(log.getAffectedRows());
        pending.setTimestamp(System.currentTimeMillis());

        Map<String, Object> previewData = new HashMap<>();
        previewData.put("originalOperationId", operationId);
        previewData.put("originalSql", log.getSqlStatement());
        pending.setPreviewData(previewData);

        context.setPendingOperation(pending);

        // 返回待确认
        StringBuilder sb = new StringBuilder();
        sb.append("## ⚠️ 回滚确认\n\n");
        sb.append("**原操作ID**: ").append(operationId).append("\n");
        sb.append("**原操作时间**: ").append(log.getFormattedTimestamp()).append("\n");
        sb.append("**原操作类型**: ").append(log.getOperationType()).append("\n");
        sb.append("**预计恢复**: ").append(log.getAffectedRows()).append(" 行数据\n\n");

        sb.append("### 原SQL\n");
        sb.append("```sql\n").append(log.getSqlStatement()).append("\n```\n\n");

        sb.append("### 回滚SQL\n");
        sb.append("```sql\n").append(rollbackSql).append("\n```\n\n");

        sb.append("---\n");
        sb.append("请输入 **确认** 执行回滚，或 **取消** 放弃。");

        List<Map<String, Object>> preview = new ArrayList<>();
        return ToolResult.pendingConfirmation(rollbackSql, preview, log.getAffectedRows(), rollbackOperationId);
    }

    /**
     * 格式化状态数据
     */
    private String formatState(String state) {
        try {
            List<?> data = JSON.parseArray(state);
            StringBuilder sb = new StringBuilder();
            for (Object row : data) {
                sb.append(row.toString()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return state;
        }
    }

    // ========== Getter/Setter ==========

    public void setOperationLogger(OperationLogger operationLogger) {
        this.operationLogger = operationLogger;
    }
}
