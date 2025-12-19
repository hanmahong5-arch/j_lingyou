package red.jiuzhou.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多轮对话管理器
 *
 * 管理对话历史，支持上下文保持
 * 实现对话记忆和会话隔离
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class ConversationManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationManager.class);

    /** 会话存储：sessionId -> 消息列表 */
    private final Map<String, List<AgentMessage>> sessions = new ConcurrentHashMap<>();

    /** 会话元数据 */
    private final Map<String, SessionMetadata> sessionMetadata = new ConcurrentHashMap<>();

    /** 最大历史消息数（避免上下文过长） */
    private int maxHistorySize = 50;

    /** 上下文窗口大小（发送给AI的最近N条消息） */
    private int contextWindowSize = 20;

    /**
     * 会话元数据
     */
    public static class SessionMetadata {
        private String sessionId;
        private long createdAt;
        private long lastActiveAt;
        private String title;
        private int messageCount;

        public SessionMetadata(String sessionId) {
            this.sessionId = sessionId;
            this.createdAt = System.currentTimeMillis();
            this.lastActiveAt = this.createdAt;
            this.title = "新对话";
            this.messageCount = 0;
        }

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public long getCreatedAt() { return createdAt; }
        public long getLastActiveAt() { return lastActiveAt; }
        public void setLastActiveAt(long lastActiveAt) { this.lastActiveAt = lastActiveAt; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public int getMessageCount() { return messageCount; }
        public void setMessageCount(int messageCount) { this.messageCount = messageCount; }
    }

    /**
     * 创建新会话
     *
     * @return 会话ID
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new ArrayList<>());
        sessionMetadata.put(sessionId, new SessionMetadata(sessionId));
        log.info("创建新会话: {}", sessionId);
        return sessionId;
    }

    /**
     * 添加消息到会话
     *
     * @param sessionId 会话ID
     * @param message 消息
     */
    public void addMessage(String sessionId, AgentMessage message) {
        List<AgentMessage> history = sessions.get(sessionId);
        if (history == null) {
            log.warn("会话不存在: {}，自动创建", sessionId);
            sessions.put(sessionId, new ArrayList<>());
            sessionMetadata.put(sessionId, new SessionMetadata(sessionId));
            history = sessions.get(sessionId);
        }

        history.add(message);

        // 更新元数据
        SessionMetadata metadata = sessionMetadata.get(sessionId);
        if (metadata != null) {
            metadata.setLastActiveAt(System.currentTimeMillis());
            metadata.setMessageCount(history.size());

            // 用第一条用户消息作为标题
            if (metadata.getTitle().equals("新对话") && message.getRole() == AgentMessage.Role.USER) {
                String title = message.getContent();
                if (title.length() > 30) {
                    title = title.substring(0, 30) + "...";
                }
                metadata.setTitle(title);
            }
        }

        // 限制历史大小
        if (history.size() > maxHistorySize) {
            // 保留系统消息和最近的消息
            List<AgentMessage> trimmed = new ArrayList<>();
            for (AgentMessage msg : history) {
                if (msg.getRole() == AgentMessage.Role.SYSTEM) {
                    trimmed.add(msg);
                }
            }
            int startIndex = history.size() - (maxHistorySize - trimmed.size());
            for (int i = startIndex; i < history.size(); i++) {
                AgentMessage msg = history.get(i);
                if (msg.getRole() != AgentMessage.Role.SYSTEM) {
                    trimmed.add(msg);
                }
            }
            sessions.put(sessionId, trimmed);
            log.debug("会话 {} 历史消息已裁剪至 {} 条", sessionId, trimmed.size());
        }

        log.debug("会话 {} 添加消息: {}", sessionId, message.getRole());
    }

    /**
     * 获取会话历史
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    public List<AgentMessage> getHistory(String sessionId) {
        return sessions.getOrDefault(sessionId, Collections.emptyList());
    }

    /**
     * 获取上下文窗口（发送给AI的消息）
     *
     * @param sessionId 会话ID
     * @return 最近的消息列表
     */
    public List<AgentMessage> getContextWindow(String sessionId) {
        List<AgentMessage> history = sessions.get(sessionId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }

        // 总是包含系统消息
        List<AgentMessage> context = new ArrayList<>();
        for (AgentMessage msg : history) {
            if (msg.getRole() == AgentMessage.Role.SYSTEM) {
                context.add(msg);
            }
        }

        // 添加最近的对话消息
        int nonSystemCount = 0;
        for (int i = history.size() - 1; i >= 0 && nonSystemCount < contextWindowSize; i--) {
            AgentMessage msg = history.get(i);
            if (msg.getRole() != AgentMessage.Role.SYSTEM) {
                context.add(1, msg);  // 插入到系统消息之后
                nonSystemCount++;
            }
        }

        return context;
    }

    /**
     * 构建发送给AI的消息列表
     *
     * @param sessionId 会话ID
     * @return AI格式的消息列表
     */
    public List<Map<String, String>> buildAiMessages(String sessionId) {
        List<AgentMessage> context = getContextWindow(sessionId);
        List<Map<String, String>> aiMessages = new ArrayList<>();

        for (AgentMessage msg : context) {
            Map<String, String> aiMsg = new HashMap<>();
            aiMsg.put("role", msg.getRole().getValue());
            aiMsg.put("content", msg.getContent());
            aiMessages.add(aiMsg);
        }

        return aiMessages;
    }

    /**
     * 清空会话历史
     *
     * @param sessionId 会话ID
     */
    public void clearSession(String sessionId) {
        List<AgentMessage> history = sessions.get(sessionId);
        if (history != null) {
            // 保留系统消息
            List<AgentMessage> systemMessages = new ArrayList<>();
            for (AgentMessage msg : history) {
                if (msg.getRole() == AgentMessage.Role.SYSTEM) {
                    systemMessages.add(msg);
                }
            }
            history.clear();
            history.addAll(systemMessages);
            log.info("会话 {} 已清空（保留系统消息）", sessionId);
        }
    }

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     */
    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
        sessionMetadata.remove(sessionId);
        log.info("会话 {} 已删除", sessionId);
    }

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话ID
     * @return 是否存在
     */
    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * 获取所有会话元数据
     *
     * @return 会话元数据列表
     */
    public List<SessionMetadata> getAllSessions() {
        List<SessionMetadata> list = new ArrayList<>(sessionMetadata.values());
        // 按最后活跃时间排序
        list.sort((a, b) -> Long.compare(b.getLastActiveAt(), a.getLastActiveAt()));
        return list;
    }

    /**
     * 获取会话元数据
     *
     * @param sessionId 会话ID
     * @return 元数据
     */
    public SessionMetadata getSessionMetadata(String sessionId) {
        return sessionMetadata.get(sessionId);
    }

    /**
     * 添加系统消息
     *
     * @param sessionId 会话ID
     * @param systemPrompt 系统提示词
     */
    public void setSystemPrompt(String sessionId, String systemPrompt) {
        List<AgentMessage> history = sessions.get(sessionId);
        if (history != null) {
            // 移除旧的系统消息
            history.removeIf(msg -> msg.getRole() == AgentMessage.Role.SYSTEM);
            // 添加新的系统消息到开头
            history.add(0, AgentMessage.system(systemPrompt));
            log.debug("会话 {} 设置系统提示词", sessionId);
        }
    }

    /**
     * 获取最后一条消息
     *
     * @param sessionId 会话ID
     * @return 最后一条消息
     */
    public AgentMessage getLastMessage(String sessionId) {
        List<AgentMessage> history = sessions.get(sessionId);
        if (history != null && !history.isEmpty()) {
            return history.get(history.size() - 1);
        }
        return null;
    }

    /**
     * 获取最后一条助手消息
     *
     * @param sessionId 会话ID
     * @return 最后一条助手消息
     */
    public AgentMessage getLastAssistantMessage(String sessionId) {
        List<AgentMessage> history = sessions.get(sessionId);
        if (history != null) {
            for (int i = history.size() - 1; i >= 0; i--) {
                AgentMessage msg = history.get(i);
                if (msg.getRole() == AgentMessage.Role.ASSISTANT) {
                    return msg;
                }
            }
        }
        return null;
    }

    // ========== 配置方法 ==========

    public int getMaxHistorySize() {
        return maxHistorySize;
    }

    public void setMaxHistorySize(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    public int getContextWindowSize() {
        return contextWindowSize;
    }

    public void setContextWindowSize(int contextWindowSize) {
        this.contextWindowSize = contextWindowSize;
    }
}
