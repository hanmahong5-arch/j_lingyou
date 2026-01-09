package red.jiuzhou.langchain.memory;

import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * 基于数据库的 ChatMemory 存储
 *
 * <p>将对话历史持久化到数据库，支持：
 * <ul>
 *   <li>会话持久化 - 重启后保留对话历史</li>
 *   <li>多会话隔离 - 不同会话独立存储</li>
 *   <li>消息窗口 - 可配置保留的消息数量</li>
 *   <li>过期清理 - 自动清理过期会话</li>
 * </ul>
 *
 * <p>表结构：
 * <pre>
 * CREATE TABLE chat_memory (
 *     id BIGINT AUTO_INCREMENT PRIMARY KEY,
 *     memory_id VARCHAR(100) NOT NULL,
 *     message_type VARCHAR(20) NOT NULL,
 *     message_content TEXT NOT NULL,
 *     tool_name VARCHAR(100),
 *     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
 *     INDEX idx_memory_id (memory_id),
 *     INDEX idx_created_at (created_at)
 * );
 * </pre>
 *
 * @author Claude
 * @version 1.0
 */
@Component
public class DatabaseChatMemoryStore implements ChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(DatabaseChatMemoryStore.class);

    private final JdbcTemplate jdbcTemplate;

    /** 默认最大消息数 */
    private int maxMessages = 100;

    /** 会话过期时间（小时） */
    private int sessionExpiryHours = 24;

    public DatabaseChatMemoryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureTableExists();
    }

    /**
     * 确保存储表存在 (PostgreSQL)
     */
    private void ensureTableExists() {
        try {
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS chat_memory (
                    id BIGSERIAL PRIMARY KEY,
                    memory_id VARCHAR(100) NOT NULL,
                    message_type VARCHAR(20) NOT NULL,
                    message_content TEXT NOT NULL,
                    tool_name VARCHAR(100),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
            jdbcTemplate.execute(createTableSql);

            // PostgreSQL: 单独创建索引
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_id ON chat_memory (memory_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_created_at ON chat_memory (created_at)");

            log.info("chat_memory 表已就绪");
        } catch (Exception e) {
            log.warn("创建 chat_memory 表失败（可能已存在）: {}", e.getMessage());
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String id = memoryId.toString();
        log.debug("获取会话消息: {}", id);

        try {
            String sql = """
                SELECT message_type, message_content, tool_name
                FROM chat_memory
                WHERE memory_id = ?
                ORDER BY id ASC
                """;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
            List<ChatMessage> messages = new ArrayList<>();

            for (Map<String, Object> row : rows) {
                String type = (String) row.get("message_type");
                String content = (String) row.get("message_content");
                String toolName = (String) row.get("tool_name");

                ChatMessage message = deserializeMessage(type, content, toolName);
                if (message != null) {
                    messages.add(message);
                }
            }

            // 如果消息数超过限制，只返回最近的
            if (messages.size() > maxMessages) {
                messages = messages.subList(messages.size() - maxMessages, messages.size());
            }

            log.debug("获取到 {} 条消息", messages.size());
            return messages;

        } catch (Exception e) {
            log.error("获取会话消息失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String id = memoryId.toString();
        log.debug("更新会话消息: {}, 消息数: {}", id, messages.size());

        try {
            // 先删除旧消息
            jdbcTemplate.update("DELETE FROM chat_memory WHERE memory_id = ?", id);

            // 插入新消息
            String insertSql = """
                INSERT INTO chat_memory (memory_id, message_type, message_content, tool_name)
                VALUES (?, ?, ?, ?)
                """;

            for (ChatMessage message : messages) {
                MessageRecord record = serializeMessage(message);
                jdbcTemplate.update(insertSql, id, record.type(), record.content(), record.toolName());
            }

            log.debug("会话消息更新完成");

        } catch (Exception e) {
            log.error("更新会话消息失败: {}", e.getMessage());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String id = memoryId.toString();
        log.info("删除会话消息: {}", id);

        try {
            int deleted = jdbcTemplate.update("DELETE FROM chat_memory WHERE memory_id = ?", id);
            log.info("删除了 {} 条消息", deleted);
        } catch (Exception e) {
            log.error("删除会话消息失败: {}", e.getMessage());
        }
    }

    /**
     * 清理过期会话
     */
    public int cleanupExpiredSessions() {
        log.info("清理过期会话，过期时间: {} 小时", sessionExpiryHours);

        try {
            Timestamp expiry = Timestamp.from(
                    Instant.now().minusSeconds(sessionExpiryHours * 3600L)
            );

            String sql = "DELETE FROM chat_memory WHERE created_at < ?";
            int deleted = jdbcTemplate.update(sql, expiry);

            log.info("清理了 {} 条过期消息", deleted);
            return deleted;

        } catch (Exception e) {
            log.error("清理过期会话失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 获取所有会话ID
     */
    public List<String> getAllMemoryIds() {
        try {
            String sql = "SELECT DISTINCT memory_id FROM chat_memory ORDER BY MAX(created_at) DESC";
            return jdbcTemplate.queryForList(sql, String.class);
        } catch (Exception e) {
            log.error("获取会话列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取会话统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();

        try {
            // 总消息数
            Integer totalMessages = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chat_memory", Integer.class);
            stats.put("totalMessages", totalMessages);

            // 会话数
            Integer sessionCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT memory_id) FROM chat_memory", Integer.class);
            stats.put("sessionCount", sessionCount);

            // 按类型统计
            List<Map<String, Object>> typeStats = jdbcTemplate.queryForList(
                    "SELECT message_type, COUNT(*) as count FROM chat_memory GROUP BY message_type");
            stats.put("messageTypes", typeStats);

        } catch (Exception e) {
            log.error("获取统计信息失败: {}", e.getMessage());
        }

        return stats;
    }

    // ==================== 序列化/反序列化 ====================

    private MessageRecord serializeMessage(ChatMessage message) {
        String type;
        String content;
        String toolName = null;

        if (message instanceof UserMessage userMsg) {
            type = "USER";
            content = userMsg.singleText();
        } else if (message instanceof AiMessage aiMsg) {
            type = "AI";
            content = aiMsg.text() != null ? aiMsg.text() : "";
            // 如果有工具调用，序列化为 JSON
            if (aiMsg.hasToolExecutionRequests()) {
                content = JSON.toJSONString(Map.of(
                        "text", content,
                        "toolRequests", aiMsg.toolExecutionRequests()
                ));
                type = "AI_TOOL_REQUEST";
            }
        } else if (message instanceof ToolExecutionResultMessage toolMsg) {
            type = "TOOL_RESULT";
            content = toolMsg.text();
            toolName = toolMsg.toolName();
        } else if (message instanceof SystemMessage sysMsg) {
            type = "SYSTEM";
            content = sysMsg.text();
        } else {
            type = "UNKNOWN";
            content = message.toString();
        }

        return new MessageRecord(type, content, toolName);
    }

    private ChatMessage deserializeMessage(String type, String content, String toolName) {
        return switch (type) {
            case "USER" -> UserMessage.from(content);
            case "AI" -> AiMessage.from(content);
            case "AI_TOOL_REQUEST" -> {
                // 简化处理，只恢复文本部分
                try {
                    Map<String, Object> data = JSON.parseObject(content, Map.class);
                    yield AiMessage.from((String) data.get("text"));
                } catch (Exception e) {
                    yield AiMessage.from(content);
                }
            }
            case "TOOL_RESULT" -> ToolExecutionResultMessage.from(
                    null,  // id 在恢复时不需要
                    toolName != null ? toolName : "unknown",
                    content
            );
            case "SYSTEM" -> SystemMessage.from(content);
            default -> null;
        };
    }

    private record MessageRecord(String type, String content, String toolName) {}

    // ==================== Getter/Setter ====================

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public int getSessionExpiryHours() {
        return sessionExpiryHours;
    }

    public void setSessionExpiryHours(int sessionExpiryHours) {
        this.sessionExpiryHours = sessionExpiryHours;
    }
}
