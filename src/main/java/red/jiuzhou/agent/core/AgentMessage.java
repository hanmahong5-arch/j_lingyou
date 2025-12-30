package red.jiuzhou.agent.core;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agent消息
 *
 * 表示对话中的一条消息
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class AgentMessage {

    /** 消息ID */
    private String id;

    /** 消息角色 */
    private Role role;

    /** 消息内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime timestamp;

    /** 消息类型 */
    private MessageType type;

    /** 关联的工具调用 */
    private ToolCall toolCall;

    /** 关联的工具结果 */
    private String toolResult;

    /**
     * 消息角色枚举
     */
    public enum Role {
        /** 用户消息 */
        USER("user", "👤"),
        /** 助手消息 */
        ASSISTANT("assistant", "🤖"),
        /** 系统消息 */
        SYSTEM("system", "⚙️"),
        /** 工具消息 */
        TOOL("tool", "🔧");

        private final String value;
        private final String icon;

        Role(String value, String icon) {
            this.value = value;
            this.icon = icon;
        }

        public String getValue() { return value; }
        public String getIcon() { return icon; }
    }

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        /** 普通文本 */
        TEXT,
        /** 工具调用请求 */
        TOOL_CALL,
        /** 工具调用结果 */
        TOOL_RESULT,
        /** 数据表格 */
        DATA_TABLE,
        /** 待确认操作 */
        PENDING_CONFIRMATION,
        /** 系统提示 */
        SYSTEM_NOTICE,
        /** 错误信息 */
        ERROR
    }

    /**
     * 工具调用（Record模式 - 不可变）
     *
     * @param toolName   工具名称
     * @param parameters 参数JSON字符串
     * @param callId     调用ID
     */
    public record ToolCall(String toolName, String parameters, String callId) {
        /**
         * 创建工具调用（自动生成callId）
         */
        public static ToolCall of(String toolName, String parameters) {
            return new ToolCall(toolName, parameters, UUID.randomUUID().toString().substring(0, 8));
        }

        // 兼容性getter方法
        public String getToolName() { return toolName; }
        public String getParameters() { return parameters; }
        public String getCallId() { return callId; }
    }

    // ========== 静态工厂方法 ==========

    /**
     * 创建用户消息
     */
    public static AgentMessage user(String content) {
        AgentMessage msg = new AgentMessage();
        msg.id = UUID.randomUUID().toString();
        msg.role = Role.USER;
        msg.content = content;
        msg.type = MessageType.TEXT;
        msg.timestamp = LocalDateTime.now();
        return msg;
    }

    /**
     * 创建助手消息
     */
    public static AgentMessage assistant(String content) {
        AgentMessage msg = new AgentMessage();
        msg.id = UUID.randomUUID().toString();
        msg.role = Role.ASSISTANT;
        msg.content = content;
        msg.type = MessageType.TEXT;
        msg.timestamp = LocalDateTime.now();
        return msg;
    }

    /**
     * 创建系统消息
     */
    public static AgentMessage system(String content) {
        AgentMessage msg = new AgentMessage();
        msg.id = UUID.randomUUID().toString();
        msg.role = Role.SYSTEM;
        msg.content = content;
        msg.type = MessageType.SYSTEM_NOTICE;
        msg.timestamp = LocalDateTime.now();
        return msg;
    }

    /**
     * 创建工具调用消息
     */
    public static AgentMessage toolCall(String toolName, String parameters) {
        AgentMessage msg = new AgentMessage();
        msg.id = UUID.randomUUID().toString();
        msg.role = Role.ASSISTANT;
        msg.type = MessageType.TOOL_CALL;
        msg.toolCall = ToolCall.of(toolName, parameters);
        msg.content = String.format("调用工具: %s", toolName);
        msg.timestamp = LocalDateTime.now();
        return msg;
    }

    /**
     * 创建工具结果消息
     */
    public static AgentMessage toolResult(String toolName, String result) {
        AgentMessage msg = new AgentMessage();
        msg.id = UUID.randomUUID().toString();
        msg.role = Role.TOOL;
        msg.type = MessageType.TOOL_RESULT;
        msg.content = result;
        msg.toolResult = result;
        msg.timestamp = LocalDateTime.now();
        return msg;
    }

    /**
     * 创建错误消息
     */
    public static AgentMessage error(String errorContent) {
        AgentMessage msg = new AgentMessage();
        msg.id = UUID.randomUUID().toString();
        msg.role = Role.SYSTEM;
        msg.type = MessageType.ERROR;
        msg.content = errorContent;
        msg.timestamp = LocalDateTime.now();
        return msg;
    }

    /**
     * 创建待确认消息
     */
    public static AgentMessage pendingConfirmation(String sql, int affectedRows) {
        AgentMessage msg = new AgentMessage();
        msg.id = UUID.randomUUID().toString();
        msg.role = Role.ASSISTANT;
        msg.type = MessageType.PENDING_CONFIRMATION;
        msg.content = String.format("待确认操作:\nSQL: %s\n预计影响: %d 行", sql, affectedRows);
        msg.timestamp = LocalDateTime.now();
        return msg;
    }

    // ========== Getter/Setter ==========

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public ToolCall getToolCall() { return toolCall; }
    public void setToolCall(ToolCall toolCall) { this.toolCall = toolCall; }
    public String getToolResult() { return toolResult; }
    public void setToolResult(String toolResult) { this.toolResult = toolResult; }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", timestamp, role.getIcon(), content);
    }
}
