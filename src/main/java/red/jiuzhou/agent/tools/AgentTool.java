package red.jiuzhou.agent.tools;

import red.jiuzhou.agent.core.AgentContext;

/**
 * Agent工具接口
 *
 * 定义AI Agent可调用的工具规范
 * 每个工具对应一种数据操作能力
 *
 * @author yanxq
 * @date 2025-01-13
 */
public interface AgentTool {

    /**
     * 获取工具名称
     * 用于AI识别和调用
     *
     * @return 工具名称（如 query, modify, analyze）
     */
    String getName();

    /**
     * 获取工具描述
     * 提供给AI理解工具用途
     *
     * @return 工具描述
     */
    String getDescription();

    /**
     * 获取工具参数Schema
     * JSON Schema格式，描述工具接受的参数
     *
     * @return 参数Schema JSON字符串
     */
    String getParameterSchema();

    /**
     * 执行工具
     *
     * @param context Agent上下文
     * @param parameters 工具参数（JSON格式）
     * @return 执行结果
     */
    ToolResult execute(AgentContext context, String parameters);

    /**
     * 是否需要用户确认
     * 查询类工具通常不需要，修改类工具需要
     *
     * @return true表示需要用户确认后才能执行
     */
    boolean requiresConfirmation();

    /**
     * 获取工具分类
     *
     * @return 工具分类
     */
    default ToolCategory getCategory() {
        return ToolCategory.UTILITY;
    }

    /**
     * 工具分类枚举
     */
    enum ToolCategory {
        /** 查询类工具 - 只读操作 */
        QUERY("查询", "🔍"),
        /** 修改类工具 - 写入操作 */
        MODIFY("修改", "✏️"),
        /** 分析类工具 - 数据分析 */
        ANALYZE("分析", "📊"),
        /** 历史类工具 - 操作历史 */
        HISTORY("历史", "📜"),
        /** 工具类 - 辅助功能 */
        UTILITY("工具", "🔧");

        private final String displayName;
        private final String icon;

        ToolCategory(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIcon() {
            return icon;
        }
    }
}
