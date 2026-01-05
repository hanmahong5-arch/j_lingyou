package red.jiuzhou.langchain.service;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import red.jiuzhou.langchain.LangChainModelFactory;
import red.jiuzhou.langchain.tools.GameDataTools;
import red.jiuzhou.langchain.tools.HistoryTools;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameDataAssistant 工厂类
 *
 * <p>构建带有工具和记忆的 AI Service 实例。
 *
 * <p>特性：
 * <ul>
 *   <li>工具注入 - 自动注入 GameDataTools 和 HistoryTools</li>
 *   <li>会话记忆 - 为每个会话创建独立的对话记忆</li>
 *   <li>RAG 集成 - 可选注入 ContentRetriever</li>
 *   <li>实例缓存 - 缓存已创建的 AI Service 实例</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
@Component
public class GameDataAssistantFactory {

    private static final Logger log = LoggerFactory.getLogger(GameDataAssistantFactory.class);

    private final LangChainModelFactory modelFactory;
    private final JdbcTemplate jdbcTemplate;

    // AI Service 实例缓存（按模型名称）
    private final Map<String, GameDataAssistant> assistantCache = new ConcurrentHashMap<>();

    // 会话记忆缓存
    private final Map<String, ChatMemory> memoryCache = new ConcurrentHashMap<>();

    // 默认最大消息数
    private static final int DEFAULT_MAX_MESSAGES = 50;

    public GameDataAssistantFactory(LangChainModelFactory modelFactory, JdbcTemplate jdbcTemplate) {
        this.modelFactory = modelFactory;
        this.jdbcTemplate = jdbcTemplate;
        log.info("GameDataAssistantFactory 初始化完成");
    }

    /**
     * 获取默认模型的 AI Service
     */
    public GameDataAssistant getAssistant() {
        return getAssistant(modelFactory.getProperties().getDefaultModel());
    }

    /**
     * 获取指定模型的 AI Service
     */
    public GameDataAssistant getAssistant(String modelName) {
        return assistantCache.computeIfAbsent(modelName, this::createAssistant);
    }

    /**
     * 创建 AI Service 实例
     */
    private GameDataAssistant createAssistant(String modelName) {
        log.info("创建 GameDataAssistant，模型: {}", modelName);

        try {
            log.info("获取 ChatLanguageModel");
            ChatLanguageModel model = modelFactory.getModel(modelName);
            log.info("创建 GameDataTools");
            GameDataTools gameDataTools = new GameDataTools(jdbcTemplate);
            log.info("创建 HistoryTools");
            HistoryTools historyTools = new HistoryTools();

            // 创建会话记忆提供者
            log.info("创建 ChatMemoryProvider");
            ChatMemoryProvider memoryProvider = memoryId -> {
                String key = memoryId.toString();
                return memoryCache.computeIfAbsent(key, k ->
                        MessageWindowChatMemory.builder()
                                .maxMessages(DEFAULT_MAX_MESSAGES)
                                .build()
                );
            };

            log.info("调用 AiServices.builder()");
            GameDataAssistant assistant = AiServices.builder(GameDataAssistant.class)
                    .chatLanguageModel(model)
                    .tools(gameDataTools, historyTools)
                    .chatMemoryProvider(memoryProvider)
                    .build();

            log.info("GameDataAssistant 创建成功，模型: {}", modelName);
            return assistant;
        } catch (Exception e) {
            log.error("创建 GameDataAssistant 失败", e);
            throw new RuntimeException("创建 GameDataAssistant 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建带有 RAG 的 AI Service（用于增强检索）
     */
    public GameDataAssistant createAssistantWithRag(String modelName, ContentRetriever contentRetriever) {
        log.info("创建带 RAG 的 GameDataAssistant，模型: {}", modelName);

        ChatLanguageModel model = modelFactory.getModel(modelName);
        GameDataTools gameDataTools = new GameDataTools(jdbcTemplate);
        HistoryTools historyTools = new HistoryTools();

        ChatMemoryProvider memoryProvider = memoryId -> {
            String key = memoryId.toString();
            return memoryCache.computeIfAbsent(key, k ->
                    MessageWindowChatMemory.builder()
                            .maxMessages(DEFAULT_MAX_MESSAGES)
                            .build()
            );
        };

        return AiServices.builder(GameDataAssistant.class)
                .chatLanguageModel(model)
                .tools(gameDataTools, historyTools)
                .chatMemoryProvider(memoryProvider)
                .contentRetriever(contentRetriever)
                .build();
    }

    /**
     * 创建简单的 AI Service（无工具，用于纯对话）
     */
    public GameDataAssistant createSimpleAssistant(String modelName) {
        log.info("创建简单 GameDataAssistant，模型: {}", modelName);

        ChatLanguageModel model = modelFactory.getModel(modelName);

        return AiServices.builder(GameDataAssistant.class)
                .chatLanguageModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(DEFAULT_MAX_MESSAGES))
                .build();
    }

    /**
     * 清除指定会话的记忆
     */
    public void clearSessionMemory(String sessionId) {
        ChatMemory removed = memoryCache.remove(sessionId);
        if (removed != null) {
            removed.clear();
            log.info("已清除会话记忆: {}", sessionId);
        }
    }

    /**
     * 清除所有会话记忆
     */
    public void clearAllMemories() {
        memoryCache.values().forEach(ChatMemory::clear);
        memoryCache.clear();
        log.info("已清除所有会话记忆");
    }

    /**
     * 刷新 AI Service（重新创建）
     */
    public GameDataAssistant refreshAssistant(String modelName) {
        assistantCache.remove(modelName);
        return getAssistant(modelName);
    }

    /**
     * 获取 GameDataTools 实例
     */
    public GameDataTools createGameDataTools() {
        return new GameDataTools(jdbcTemplate);
    }

    /**
     * 获取 HistoryTools 实例
     */
    public HistoryTools createHistoryTools() {
        return new HistoryTools();
    }
}
