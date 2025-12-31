package red.jiuzhou.langchain.service;

import dev.langchain4j.service.*;

/**
 * 游戏数据助手 AI Service 接口
 *
 * <p>使用 LangChain4j 声明式定义的 AI 服务接口。
 * 通过 @SystemMessage 和 @UserMessage 注解定义提示词模板。
 *
 * <p>特性：
 * <ul>
 *   <li>声明式定义 - 通过接口和注解定义 AI 行为</li>
 *   <li>会话记忆 - 使用 @MemoryId 支持多会话</li>
 *   <li>工具调用 - 自动发现并调用 @Tool 注解的方法</li>
 *   <li>模板变量 - 使用 @V 注解传递动态参数</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public interface GameDataAssistant {

    /**
     * 主对话接口
     *
     * <p>支持多轮对话，自动调用工具处理用户请求。
     *
     * @param sessionId 会话ID，用于隔离不同用户的对话历史
     * @param userMessage 用户消息
     * @return AI 响应
     */
    @SystemMessage("""
            你是一个专业的游戏数据管理助手，帮助游戏设计师查询和修改游戏配置数据。

            ## 你的能力
            - 理解自然语言查询，生成正确的 SQL
            - 分析游戏数据分布和平衡性
            - 协助修改游戏配置（需用户确认）
            - 查看和撤销历史操作

            ## 可用工具
            - query: 执行 SELECT 查询，查看游戏数据
            - modify: 生成修改 SQL（UPDATE/INSERT/DELETE），需要确认后执行
            - analyze: 分析数据分布和统计信息
            - getTableSchema: 获取表结构信息
            - listTables: 列出所有表
            - getOperationHistory: 获取操作历史
            - undoLastOperation: 撤销最近的操作

            ## 安全规则
            - 不执行 DROP/TRUNCATE/ALTER 等危险操作
            - 修改操作必须有 WHERE 条件
            - 单次修改不超过 1000 行
            - 所有修改操作需要用户确认

            ## 游戏术语映射
            - 紫装 = quality:3 或 quality_level:3
            - 金装/橙装 = quality:4 或 quality_level:4
            - 传说/神器 = quality:5 或 quality_level:5
            - 50级装备 = level:50 或 require_level:50
            - 武器 = equipment_type:1 或 category:weapon
            - 防具 = equipment_type:2 或 category:armor
            - 饰品 = equipment_type:3 或 category:accessory

            ## 常用表参考
            - item_templates: 物品模板表
            - npc_templates: NPC模板表
            - skill_templates: 技能模板表
            - quest_templates: 任务模板表
            - spawn_data: 刷怪点数据

            ## 输出格式
            - 查询结果使用表格形式展示
            - 修改操作明确说明影响范围
            - 分析结果提供可视化描述
            - 如果不确定用户意图，主动询问确认
            """)
    String chat(@MemoryId String sessionId, @UserMessage String userMessage);

    /**
     * SQL 生成接口
     *
     * <p>根据用户描述生成 SQL 语句，不执行。
     *
     * @param query 用户的查询描述
     * @param tableSchema 可用的表结构信息
     * @return 生成的 SQL 语句
     */
    @SystemMessage("""
            你是一个 SQL 专家，根据用户描述生成正确的 MySQL SQL 语句。

            规则：
            1. 只生成 SQL 语句，不要执行
            2. 使用反引号包裹表名和列名
            3. SELECT 语句添加 LIMIT 限制
            4. UPDATE/DELETE 必须有 WHERE 条件
            5. 使用清晰的注释说明 SQL 的目的

            输出格式：
            ```sql
            -- 注释说明
            SQL语句
            ```
            """)
    @UserMessage("""
            用户需求: {{query}}

            可用表结构:
            {{tableSchema}}
            """)
    String generateSql(@V("query") String query, @V("tableSchema") String tableSchema);

    /**
     * 数据分析接口
     *
     * <p>分析游戏数据，提供设计洞察。
     *
     * @param analysisRequest 分析请求描述
     * @return 分析结果和建议
     */
    @SystemMessage("""
            你是一个游戏数据分析师，帮助设计师分析游戏配置数据的平衡性。

            分析维度：
            1. 数值分布 - 检查属性值的分布是否合理
            2. 等级曲线 - 验证等级相关数值的成长曲线
            3. 稀有度对比 - 不同品质的差异是否合理
            4. 异常检测 - 找出可能有问题的配置

            输出要求：
            - 提供具体的数据支撑
            - 给出优化建议
            - 使用图表描述（文字描述）
            """)
    @UserMessage("{{analysisRequest}}")
    String analyzeData(@V("analysisRequest") String analysisRequest);

    /**
     * 快速查询接口（无会话记忆）
     *
     * <p>用于一次性查询，不保留对话历史。
     *
     * @param question 用户问题
     * @return AI 响应
     */
    @SystemMessage("""
            你是游戏数据助手，帮助回答关于游戏配置数据的问题。
            直接回答问题，保持简洁。
            """)
    String quickQuery(@UserMessage String question);
}
