package red.jiuzhou.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.context.DesignContext;
import red.jiuzhou.agent.texttosql.SqlExampleLibrary;
import red.jiuzhou.agent.texttosql.GameSemanticEnhancer;
import red.jiuzhou.agent.tools.ToolRegistry;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prompt构建器（增强版）
 *
 * 构建发送给AI的系统提示词
 * 包含角色定义、表结构信息、工具说明、游戏语义映射
 *
 * 新增：
 * - SQL示例库（Few-Shot Learning）
 * - 游戏语义增强
 * - 动态上下文构建
 *
 * @author yanxq
 * @date 2025-01-13
 * @updated 2025-12-20 (TEXT-TO-SQL增强 + 异步加载优化)
 */
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    private SchemaMetadataService metadataService;
    private ToolRegistry toolRegistry;
    private SqlExampleLibrary exampleLibrary;
    private GameSemanticEnhancer semanticEnhancer;

    public PromptBuilder(SchemaMetadataService metadataService, ToolRegistry toolRegistry) {
        this.metadataService = metadataService;
        this.toolRegistry = toolRegistry;
        this.exampleLibrary = SqlExampleLibrary.getInstance();
    }

    /**
     * 设置JDBC模板（用于语义增强）
     * 注意：此方法会异步初始化动态语义，不会阻塞启动
     */
    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate != null && this.semanticEnhancer == null) {
            this.semanticEnhancer = new GameSemanticEnhancer(jdbcTemplate);

            // 异步初始化动态语义（不阻塞启动）
            this.semanticEnhancer.initializeDynamicSemanticsAsync(() -> {
                log.info("动态语义异步初始化完成");
            });
        }
    }

    /**
     * 获取语义增强器
     */
    public GameSemanticEnhancer getSemanticEnhancer() {
        return semanticEnhancer;
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

    /** 角色定义模板（文本块） */
    private static final String ROLE_DEFINITION = """
        # 角色定义

        你是一个专业的游戏数据管理助手，专门帮助游戏设计师通过自然语言查询和修改游戏数据。

        ## 数据库环境
        - **数据库类型**: MySQL 8.0
        - **重要**: 必须使用MySQL语法，禁止使用SQLite、PostgreSQL等其他数据库的语法
        - **查询元数据**: 使用 `information_schema.tables` 和 `information_schema.columns`
        - **禁止**: 使用 `sqlite_master`、`pg_catalog` 等非MySQL语法

        ## 你的能力
        1. **数据查询**: 根据用户的自然语言描述，生成MySQL SQL查询并返回结果
        2. **数据修改**: 理解用户的修改意图，生成修改SQL并在用户确认后执行
        3. **数据分析**: 分析游戏数据分布，给出平衡性建议
        4. **历史回滚**: 查看操作历史，支持回滚到指定时间点

        ## 工作原则
        - 永远先理解用户意图，必要时请求澄清
        - 修改操作必须生成预览，等待用户确认
        - 提供清晰的执行结果和影响范围说明
        - 对于不确定的操作，给出多个方案让用户选择
        - **始终使用MySQL语法**""";

    /**
     * 构建角色定义
     */
    private String buildRoleDefinition() {
        return ROLE_DEFINITION;
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
     * 构建游戏语义映射（增强版）
     */
    private String buildSemanticSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 游戏语义映射\n\n");

        // 使用新的语义增强器
        if (semanticEnhancer != null) {
            sb.append(semanticEnhancer.generateSemanticPrompt());
        } else if (metadataService != null) {
            sb.append(metadataService.generateSemanticMappingDescription());
        } else {
            sb.append("语义映射尚未加载。\n");
        }

        return sb.toString();
    }

    /**
     * 构建针对特定查询的增强提示词（Few-Shot）
     *
     * @param userQuery 用户查询
     * @return 增强后的提示词
     */
    public String buildEnhancedPrompt(String userQuery) {
        StringBuilder sb = new StringBuilder();

        // 1. 基础系统提示词（精简版）
        sb.append(buildCompactSystemPrompt());
        sb.append("\n\n");

        // 2. Few-Shot示例
        if (exampleLibrary != null) {
            sb.append(exampleLibrary.generateFewShotPrompt(userQuery, 3));
            sb.append("\n\n");
        }

        // 3. 语义增强提示
        if (semanticEnhancer != null) {
            String hints = semanticEnhancer.translateToSqlHints(userQuery);
            if (!hints.isEmpty()) {
                sb.append(hints);
                sb.append("\n\n");
            }
        }

        // 4. 当前用户查询
        sb.append("## 用户查询\n\n");
        sb.append(userQuery);
        sb.append("\n\n");

        // 5. 生成指导
        sb.append("## 生成指导\n\n");
        sb.append("请根据以上示例和语义映射，生成准确的SQL查询。\n");
        sb.append("- 使用示例中的SQL模式作为参考\n");
        sb.append("- 应用语义映射转换游戏术语\n");
        sb.append("- 确保SQL语法正确且性能良好\n");
        sb.append("- 添加LIMIT子句限制返回行数\n");

        return sb.toString();
    }

    /** 安全规则模板（文本块） */
    private static final String SAFETY_RULES = """
        # 安全规则

        请严格遵守以下安全规则：

        ## 禁止的操作
        - DROP TABLE / DROP DATABASE
        - TRUNCATE TABLE
        - ALTER TABLE（结构修改）
        - CREATE / GRANT 等DDL语句
        - 无WHERE条件的UPDATE/DELETE

        ## 允许的操作
        - SELECT 查询（无限制）
        - UPDATE（必须有WHERE条件）
        - INSERT（单次不超过100条）
        - DELETE（必须有WHERE条件，需二次确认）

        ## 影响限制
        - 单次修改最多影响 **1000** 行
        - 超过100行的修改需要用户二次确认
        - 所有修改操作会自动记录日志，支持回滚""";

    /**
     * 构建安全规则
     */
    private String buildSafetyRules() {
        return SAFETY_RULES;
    }

    /** 输出格式模板（文本块） */
    private static final String OUTPUT_FORMAT = """
        # 输出格式要求

        ## 当需要执行操作时
        如果你需要执行查询或修改，请在回复中包含工具调用JSON块：
        ```json
        {"tool": "工具名", "parameters": {...}}
        ```

        ## 当回复普通消息时
        直接用文字回复，不需要工具调用。

        ## 显示查询结果时
        使用markdown表格格式展示数据，方便用户阅读。

        ## 修改操作说明
        修改操作需要说明：
        1. 将要执行的SQL语句
        2. 预计影响的数据行数
        3. 修改前后的数据对比（前3条样本）
        4. 等待用户输入 "确认" 或 "取消\"""";

    /**
     * 构建输出格式要求
     */
    private String buildOutputFormat() {
        return OUTPUT_FORMAT;
    }

    /** 精简版系统提示词（文本块） */
    private static final String COMPACT_SYSTEM_PROMPT = """
        # 游戏数据助手

        你是游戏数据管理助手，帮助设计师通过自然语言查询和修改游戏数据。

        ## 可用工具
        - `query`: 执行SELECT查询
        - `modify`: 生成修改SQL（需确认）
        - `analyze`: 分析数据分布
        - `history`: 查看/回滚操作历史

        ## 安全规则
        - 禁止: DROP, TRUNCATE, ALTER
        - UPDATE/DELETE必须有WHERE
        - 单次最多修改1000行

        ## 输出格式
        工具调用使用JSON: `{"tool": "名称", "parameters": {...}}`""";

    /**
     * 构建精简版系统提示词（用于上下文较长时）
     */
    public String buildCompactSystemPrompt() {
        return COMPACT_SYSTEM_PROMPT;
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

    // ==================== 上下文感知提示词构建 ====================

    /**
     * 构建上下文感知的提示词
     *
     * 将设计上下文（DesignContext）与用户查询结合，生成增强的提示词。
     * 上下文信息会被格式化并添加到用户查询之前，让AI能够理解设计师当前的操作位置和意图。
     *
     * @param context 设计上下文
     * @param userQuery 用户查询
     * @return 增强后的提示词
     */
    public String buildContextAwarePrompt(DesignContext context, String userQuery) {
        if (context == null) {
            return userQuery;
        }

        StringBuilder sb = new StringBuilder();

        // 添加上下文标题
        sb.append("# 当前设计上下文\n\n");

        // 使用 DesignContext 的 toPrompt() 方法获取格式化的上下文
        sb.append(context.toPrompt());
        sb.append("\n\n");

        // 如果有表名，添加表结构详情
        if (context.getTableName() != null && metadataService != null && metadataService.isInitialized()) {
            sb.append("# 相关表详情\n\n");
            sb.append(buildTableContextPrompt(context.getTableName()));
            sb.append("\n\n");
        }

        // 如果有语义增强器，添加相关语义提示
        if (semanticEnhancer != null) {
            String hints = semanticEnhancer.translateToSqlHints(userQuery);
            if (hints != null && !hints.isEmpty()) {
                sb.append("# 语义提示\n\n");
                sb.append(hints);
                sb.append("\n\n");
            }
        }

        // 添加用户查询
        sb.append("# 用户请求\n\n");
        sb.append(userQuery);

        // 添加操作指导
        sb.append("\n\n# 操作指导\n\n");
        sb.append("请根据以上上下文信息回答用户的问题。");
        sb.append("上下文已经提供了设计师当前正在查看的数据位置和相关信息，");
        sb.append("请充分利用这些信息来给出准确、有帮助的回答。\n");

        return sb.toString();
    }

    /**
     * 构建针对特定操作类型的上下文提示词
     *
     * @param context 设计上下文
     * @param operationType 操作类型
     * @return 操作特定的提示词
     */
    public String buildOperationSpecificPrompt(DesignContext context, String operationType) {
        StringBuilder sb = new StringBuilder();

        // 基础上下文
        sb.append(buildContextAwarePrompt(context, ""));

        // 操作特定指导
        sb.append("\n## 操作类型: ");
        sb.append(getOperationDescription(operationType));
        sb.append("\n\n");

        sb.append("### 操作指导\n");
        sb.append(getOperationGuidance(operationType));

        return sb.toString();
    }

    /**
     * 获取操作类型的描述
     */
    private String getOperationDescription(String operationType) {
        return switch (operationType) {
            case "analyze" -> "文件分析";
            case "explain" -> "数据结构解释";
            case "generate" -> "生成相似配置";
            case "check_refs" -> "引用完整性检查";
            case "explain_row" -> "行数据解释";
            case "balance_check" -> "数值平衡性分析";
            case "find_similar" -> "查找相似配置";
            case "generate_variant" -> "生成变体";
            default -> operationType;
        };
    }

    /**
     * 获取操作的指导说明
     */
    private String getOperationGuidance(String operationType) {
        return switch (operationType) {
            case "analyze" -> """
                请分析当前文件的数据结构，包括：
                1. 文件的主要用途和在游戏系统中的角色
                2. 关键字段的含义和作用
                3. 与其他配置文件/表的关联关系
                4. 设计师可能关心的重点数据
                """;

            case "explain" -> """
                请解释当前数据的结构和含义，包括：
                1. 每个字段的游戏含义
                2. 数值范围的合理性
                3. 枚举值的含义（如品质、种族、职业等）
                4. 字段之间的逻辑关系
                """;

            case "check_refs" -> """
                请检查当前数据的引用关系，包括：
                1. 本数据引用的外部数据是否存在
                2. 引用的ID是否有效
                3. 是否存在循环引用
                4. 可能导致问题的悬空引用
                """;

            case "balance_check" -> """
                请分析当前数据的数值平衡性，包括：
                1. 数值是否在合理范围内
                2. 与同类型数据相比是否异常
                3. 成长曲线是否平滑
                4. 可能导致游戏失衡的问题
                """;

            case "find_similar" -> """
                请在数据库中查找与当前数据相似的配置，基于：
                1. 相似的数值属性
                2. 相同的分类/类型
                3. 相近的等级要求
                4. 类似的用途
                """;

            case "generate_variant" -> """
                请基于当前数据生成一个变体版本：
                1. 保持核心特征不变
                2. 适当调整数值（±10-20%）
                3. 可以改变名称和描述
                4. 确保生成的数据合理可用
                """;

            default -> "请根据上下文执行相应操作。";
        };
    }

    // ========== Getter/Setter ==========

    public void setMetadataService(SchemaMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    public void setToolRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }
}
