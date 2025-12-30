package red.jiuzhou.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 工具注册中心（枚举单例模式 - Java最佳实践）
 *
 * <p>管理所有可用的Agent工具，提供工具查找和列表功能。
 * <p>使用枚举单例模式确保线程安全且防止反射攻击。
 *
 * @author yanxq
 * @date 2025-01-13
 */
public enum ToolRegistry {
    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    /** 工具映射表：名称 -> 工具实例 */
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /**
     * 获取单例实例（兼容性方法）
     */
    public static ToolRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册工具
     *
     * @param tool 工具实例
     */
    public void register(AgentTool tool) {
        if (tool == null || tool.getName() == null) {
            log.warn("尝试注册空工具或工具名为空");
            return;
        }

        String name = tool.getName().toLowerCase();
        if (tools.containsKey(name)) {
            log.warn("工具 {} 已存在，将被覆盖", name);
        }

        tools.put(name, tool);
        log.info("注册工具: {} - {}", name, tool.getDescription());
    }

    /**
     * 注销工具
     *
     * @param toolName 工具名称
     */
    public void unregister(String toolName) {
        if (toolName != null) {
            AgentTool removed = tools.remove(toolName.toLowerCase());
            if (removed != null) {
                log.info("注销工具: {}", toolName);
            }
        }
    }

    /**
     * 获取工具
     *
     * @param toolName 工具名称
     * @return 工具实例，不存在返回null
     */
    public AgentTool getTool(String toolName) {
        if (toolName == null) {
            return null;
        }
        return tools.get(toolName.toLowerCase());
    }

    /**
     * 检查工具是否存在
     *
     * @param toolName 工具名称
     * @return 是否存在
     */
    public boolean hasTool(String toolName) {
        return toolName != null && tools.containsKey(toolName.toLowerCase());
    }

    /**
     * 获取所有工具
     *
     * @return 工具列表（只读）
     */
    public Collection<AgentTool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * 获取所有工具名称
     *
     * @return 工具名称列表
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /**
     * 按分类获取工具
     *
     * @param category 工具分类
     * @return 该分类下的工具列表
     */
    public List<AgentTool> getToolsByCategory(AgentTool.ToolCategory category) {
        List<AgentTool> result = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            if (tool.getCategory() == category) {
                result.add(tool);
            }
        }
        return result;
    }

    /**
     * 生成工具描述文档（用于AI提示词）
     *
     * @return 工具描述JSON数组
     */
    public String generateToolsDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        boolean first = true;
        for (AgentTool tool : tools.values()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;

            sb.append("  {\n");
            sb.append("    \"name\": \"").append(escapeJson(tool.getName())).append("\",\n");
            sb.append("    \"description\": \"").append(escapeJson(tool.getDescription())).append("\",\n");
            sb.append("    \"category\": \"").append(tool.getCategory().getDisplayName()).append("\",\n");
            sb.append("    \"requires_confirmation\": ").append(tool.requiresConfirmation()).append(",\n");
            sb.append("    \"parameters\": ").append(tool.getParameterSchema()).append("\n");
            sb.append("  }");
        }

        sb.append("\n]");
        return sb.toString();
    }

    /**
     * 生成工具调用格式说明（用于AI提示词）
     *
     * @return 格式说明文本
     */
    public String generateToolCallFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("要调用工具，请使用以下JSON格式：\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"tool\": \"工具名称\",\n");
        sb.append("  \"parameters\": {\n");
        sb.append("    \"参数名\": \"参数值\"\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("```\n\n");
        sb.append("可用的工具：\n");

        for (AgentTool tool : tools.values()) {
            sb.append("- **").append(tool.getName()).append("**: ");
            sb.append(tool.getDescription());
            if (tool.requiresConfirmation()) {
                sb.append(" [需要确认]");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 清空所有工具
     */
    public void clear() {
        tools.clear();
        log.info("已清空所有注册的工具");
    }

    /**
     * 获取工具数量
     */
    public int size() {
        return tools.size();
    }

    /**
     * JSON字符串转义
     */
    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
