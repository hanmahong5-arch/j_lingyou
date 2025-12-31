package red.jiuzhou.langchain.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import red.jiuzhou.langchain.LangChainModelFactory;

import java.util.function.Supplier;

/**
 * LangChain4j Spring 配置类
 *
 * <p>配置 LangChain4j 相关的 Spring Bean：
 * <ul>
 *   <li>ChatLanguageModel - 聊天模型（默认）</li>
 *   <li>EmbeddingModel - 嵌入模型（用于 RAG）</li>
 *   <li>EmbeddingStore - 向量存储（内存实现）</li>
 *   <li>ChatMemory - 对话记忆</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
@Configuration
@EnableConfigurationProperties(LangChainProperties.class)
public class LangChainConfig {

    private static final Logger log = LoggerFactory.getLogger(LangChainConfig.class);

    /**
     * 默认聊天模型
     */
    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel(LangChainModelFactory factory) {
        log.info("初始化默认 ChatLanguageModel");
        return factory.getDefaultModel();
    }

    /**
     * 嵌入模型（用于 RAG 语义搜索）
     *
     * <p>使用本地 ONNX 模型 AllMiniLmL6V2，无需网络调用，适合离线使用。
     * 如果需要使用 DashScope 嵌入模型，可以在这里替换。
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("初始化嵌入模型: AllMiniLmL6V2 (本地 ONNX)");
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * 向量存储（内存实现）
     *
     * <p>用于存储和检索嵌入向量。
     * 生产环境可以替换为持久化存储（如 Milvus、Pinecone 等）。
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("初始化内存向量存储");
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * ChatMemory 提供者（工厂方法）
     *
     * <p>为每个会话创建独立的对话记忆。
     * 使用 MessageWindowChatMemory 保留最近 N 条消息。
     */
    @Bean
    public Supplier<ChatMemory> chatMemorySupplier(LangChainProperties properties) {
        int maxMessages = 50;  // 保留最近50条消息
        log.info("初始化 ChatMemory 提供者，最大消息数: {}", maxMessages);

        return () -> MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .build();
    }

    /**
     * 默认对话记忆（单例，用于简单场景）
     */
    @Bean
    public ChatMemory defaultChatMemory() {
        log.info("初始化默认 ChatMemory");
        return MessageWindowChatMemory.builder()
                .maxMessages(50)
                .build();
    }
}
