package red.jiuzhou.agent.core;

import red.jiuzhou.agent.tools.ToolRegistry;

/**
 * Prompt构建器
 *
 * 构建发送给AI的系统提示词
 * 包含角色定义、表结构信息、工具说明、游戏语义映射
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class PromptBuilder {

    private SchemaMetadataService metadataService;
    private ToolRegistry toolRegistry;

    public PromptBuilder(SchemaMetadataService metadataService, ToolRegistry toolRegistry) {
        this.metadataService = metadataService;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 构建完整的系统提示词
     */
    public String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();

        // 角色定义
        sb.append(buildRoleDefinition());
        sb.append("\n\n");

        // 工具说明
        sb.append(buildToolsSection());
        sb.append("\n\n");

        // 数据库表结构
        sb.append(buildSchemaSection());
        sb.append("\n\n");

        // 游戏语义映射
        sb.append(buildSemanticSection());
        sb.append("\n\n");

        // 安全规则
        sb.append(buildSafetyRules());
        sb.append("\n\n");

        // 输出格式要求
        sb.append(buildOutputFormat());

        return sb.toString();
    }

    /**
     * 构建角色定义
     */
    private String buildRoleDefinition() {
        return "# 角色定义\n\n" +
               "你是一个专业的游戏数据管理助手，专门帮助游戏设计师通过自然语言查询和修改游戏数据。\n\n" +
               "## 你的能力\n" +
               "1. **数据查询**: 根据用户的自然语言描述，生成SQL查询并返回结果\n" +
               "2. **数据修改**: 理解用户的修改意图，生成修改SQL并在用户确认后执行\n" +
               "3. **数据分析**: 分析游戏数据分布，给出平衡性建议\n" +
               "4. **历史回滚**: 查看操作历史，支持回滚到指定时间点\n\n" +
               "## 工作原则\n" +
               "- 永远先理解用户意图，必要时请求澄清\n" +
               "- 修改操作必须生成预览，等待用户确认\n" +
               "- 提供清晰的执行结果和影响范围说明\n" +
               "- 对于不确定的操作，给出多个方案让用户选择";
    }

    /**
     * 构建工具说明
     */
    private String buildToolsSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 可用工具\n\n");
        sb.append("你可以通过调用以下工具来执行操作：\n\n");

        if (toolRegistry != null) {
            sb.append(toolRegistry.generateToolCallFormat());
        } else {
            sb.append("工具列表尚未加载。\n");
        }

        sb.append("\n## 工具调用示例\n\n");
        sb.append("**查询物品**:\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"tool\": \"query\",\n");
        sb.append("  \"parameters\": {\n");
        sb.append("    \"sql\": \"SELECT * FROM client_item WHERE level > 50 LIMIT 20\"\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("```\n\n");

        sb.append("**修改数据**:\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"tool\": \"modify\",\n");
        sb.append("  \"parameters\": {\n");
        sb.append("    \"sql\": \"UPDATE client_skill SET damage = damage * 1.1 WHERE element_type = 1\",\n");
        sb.append("    \"description\": \"将火属性技能伤害提高10%\"\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("```\n");

        return sb.toString();
    }

    /**
     * 构建表结构说明
     */
    private String buildSchemaSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 数据库结构\n\n");

        if (metadataService != null && metadataService.isInitialized()) {
            sb.append("当前数据库共有 ").append(metadataService.getTableCount()).append(" 个表。\n\n");
            sb.append(metadataService.generateTableList());
        } else {
            sb.append("数据库元数据尚未加载，请使用 `list_tables` 工具查看可用表。\n");
        }

        return sb.toString();
    }

    /**
     * 构建游戏语义映射
     */
    private String buildSemanticSection() {
        if (metadataService != null) {
            return metadataService.generateSemanticMappingDescription();
        }
        return "# 游戏语义映射\n\n语义映射尚未加载。\n";
    }

    /**
     * 构建安全规则
     */
    private String buildSafetyRules() {
        return "# 安全规则\n\n" +
               "请严格遵守以下安全规则：\n\n" +
               "## 禁止的操作\n" +
               "- ❌ DROP TABLE / DROP DATABASE\n" +
               "- ❌ TRUNCATE TABLE\n" +
               "- ❌ ALTER TABLE（结构修改）\n" +
               "- ❌ CREATE / GRANT 等DDL语句\n" +
               "- ❌ 无WHERE条件的UPDATE/DELETE\n\n" +
               "## 允许的操作\n" +
               "- ✅ SELECT 查询（无限制）\n" +
               "- ✅ UPDATE（必须有WHERE条件）\n" +
               "- ✅ INSERT（单次不超过100条）\n" +
               "- ✅ DELETE（必须有WHERE条件，需二次确认）\n\n" +
               "## 影响限制\n" +
               "- 单次修改最多影响 **1000** 行\n" +
               "- 超过100行的修改需要用户二次确认\n" +
               "- 所有修改操作会自动记录日志，支持回滚";
    }

    /**
     * 构建输出格式要求
     */
    private String buildOutputFormat() {
        return "# 输出格式要求\n\n" +
               "## 当需要执行操作时\n" +
               "如果你需要执行查询或修改，请在回复中包含工具调用JSON块：\n" +
               "```json\n" +
               "{\"tool\": \"工具名\", \"parameters\": {...}}\n" +
               "```\n\n" +
               "## 当回复普通消息时\n" +
               "直接用文字回复，不需要工具调用。\n\n" +
               "## 显示查询结果时\n" +
               "使用markdown表格格式展示数据，方便用户阅读。\n\n" +
               "## 修改操作说明\n" +
               "修改操作需要说明：\n" +
               "1. 将要执行的SQL语句\n" +
               "2. 预计影响的数据行数\n" +
               "3. 修改前后的数据对比（前3条样本）\n" +
               "4. 等待用户输入 \"确认\" 或 \"取消\"";
    }

    /**
     * 构建精简版系统提示词（用于上下文较长时）
     */
    public String buildCompactSystemPrompt() {
        return "# 游戏数据助手\n\n" +
               "你是游戏数据管理助手，帮助设计师通过自然语言查询和修改游戏数据。\n\n" +
               "## 可用工具\n" +
               "- `query`: 执行SELECT查询\n" +
               "- `modify`: 生成修改SQL（需确认）\n" +
               "- `analyze`: 分析数据分布\n" +
               "- `history`: 查看/回滚操作历史\n\n" +
               "## 安全规则\n" +
               "- 禁止: DROP, TRUNCATE, ALTER\n" +
               "- UPDATE/DELETE必须有WHERE\n" +
               "- 单次最多修改1000行\n\n" +
               "## 输出格式\n" +
               "工具调用使用JSON: `{\"tool\": \"名称\", \"parameters\": {...}}`";
    }

    /**
     * 构建针对特定表的上下文提示词
     *
     * @param tableName 表名
     * @return 表相关的上下文
     */
    public String buildTableContextPrompt(String tableName) {
        if (metadataService == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 当前关注的表: ").append(tableName).append("\n\n");

        // 获取表的详细结构
        java.util.List<red.jiuzhou.ui.mapping.DatabaseTableScanner.ColumnInfo> columns =
            metadataService.getTableColumns(tableName);

        if (!columns.isEmpty()) {
            sb.append("### 字段结构\n");
            sb.append("| 字段名 | 类型 | 说明 |\n");
            sb.append("|--------|------|------|\n");

            for (red.jiuzhou.ui.mapping.DatabaseTableScanner.ColumnInfo col : columns) {
                sb.append("| ").append(col.getColumnName());
                sb.append(" | ").append(col.getColumnType());
                sb.append(" | ").append(col.getComment() != null ? col.getComment() : "");
                if (col.isPrimaryKey()) {
                    sb.append(" [PK]");
                }
                sb.append(" |\n");
            }
        }

        // 获取样本数据
        java.util.List<java.util.Map<String, Object>> samples =
            metadataService.getSampleData(tableName, 5);

        if (!samples.isEmpty()) {
            sb.append("\n### 样本数据（前5条）\n");
            sb.append("```\n");
            for (java.util.Map<String, Object> row : samples) {
                sb.append(row.toString()).append("\n");
            }
            sb.append("```\n");
        }

        return sb.toString();
    }

    // ========== Getter/Setter ==========

    public void setMetadataService(SchemaMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    public void setToolRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }
}
