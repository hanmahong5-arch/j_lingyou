package red.jiuzhou.langchain;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import red.jiuzhou.langchain.config.LangChainProperties;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangChain4j 模型工厂
 *
 * <p>统一创建和管理各类 LLM 模型实例：
 * <ul>
 *   <li>通义千问 (Qwen) - 使用 langchain4j-community-dashscope</li>
 *   <li>DeepSeek - 使用 OpenAI 兼容接口</li>
 *   <li>Kimi (Moonshot) - 使用 OpenAI 兼容接口</li>
 * </ul>
 *
 * <p>特性：
 * <ul>
 *   <li>模型实例缓存 - 避免重复创建</li>
 *   <li>配置驱动 - 从 application.yml 读取配置</li>
 *   <li>统一接口 - 返回 ChatLanguageModel 接口</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
@Component
public class LangChainModelFactory {

    private static final Logger log = LoggerFactory.getLogger(LangChainModelFactory.class);

    private final LangChainProperties properties;

    // 模型实例缓存
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();

    // OpenAI 兼容接口的 Base URL
    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1";
    private static final String KIMI_BASE_URL = "https://api.moonshot.cn/v1";

    public LangChainModelFactory(LangChainProperties properties) {
        this.properties = properties;
        log.info("LangChainModelFactory 初始化完成，默认模型: {}", properties.getDefaultModel());
    }

    /**
     * 获取默认模型
     */
    public ChatLanguageModel getDefaultModel() {
        return getModel(properties.getDefaultModel());
    }

    /**
     * 获取指定模型
     *
     * @param modelName 模型名称: qwen, deepseek, kimi
     * @return ChatLanguageModel 实例
     */
    public ChatLanguageModel getModel(String modelName) {
        String key = modelName.toLowerCase();
        return modelCache.computeIfAbsent(key, this::createModel);
    }

    /**
     * 创建模型实例
     */
    private ChatLanguageModel createModel(String modelName) {
        log.info("创建 LangChain4j 模型: {}", modelName);

        LangChainProperties.ModelConfig config = properties.getModelConfig(modelName);
        if (!config.isConfigured()) {
            throw new IllegalStateException("模型 " + modelName + " 未配置 API Key 或模型名称");
        }

        return switch (modelName.toLowerCase()) {
            case "qwen", "tongyi", "dashscope" -> createQwenModel(config);
            case "deepseek" -> createDeepSeekModel(config);
            case "kimi", "moonshot" -> createKimiModel(config);
            default -> {
                log.warn("未知模型 {}，使用默认 Qwen", modelName);
                yield createQwenModel(properties.getQwen());
            }
        };
    }

    /**
     * 创建通义千问模型
     */
    private ChatLanguageModel createQwenModel(LangChainProperties.ModelConfig config) {
        log.info("创建 Qwen 模型: {}", config.getModel());

        return QwenChatModel.builder()
                .apiKey(config.getApikey())
                .modelName(config.getModel())
                .temperature((float) properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .build();
    }

    /**
     * 创建 DeepSeek 模型（OpenAI 兼容）
     */
    private ChatLanguageModel createDeepSeekModel(LangChainProperties.ModelConfig config) {
        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : DEEPSEEK_BASE_URL;
        log.info("创建 DeepSeek 模型: {} (baseUrl: {})", config.getModel(), baseUrl);

        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(config.getApikey())
                .modelName(config.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .maxRetries(properties.getMaxRetries())
                .build();
    }

    /**
     * 创建 Kimi 模型（OpenAI 兼容）
     */
    private ChatLanguageModel createKimiModel(LangChainProperties.ModelConfig config) {
        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : KIMI_BASE_URL;
        log.info("创建 Kimi 模型: {} (baseUrl: {})", config.getModel(), baseUrl);

        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(config.getApikey())
                .modelName(config.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .maxRetries(properties.getMaxRetries())
                .build();
    }

    /**
     * 清除模型缓存
     */
    public void clearCache() {
        modelCache.clear();
        log.info("模型缓存已清除");
    }

    /**
     * 刷新指定模型（重新创建）
     */
    public ChatLanguageModel refreshModel(String modelName) {
        String key = modelName.toLowerCase();
        modelCache.remove(key);
        return getModel(modelName);
    }

    /**
     * 获取已缓存的模型名称列表
     */
    public java.util.Set<String> getCachedModelNames() {
        return modelCache.keySet();
    }

    /**
     * 检查模型是否已配置
     */
    public boolean isModelConfigured(String modelName) {
        return properties.getModelConfig(modelName).isConfigured();
    }

    /**
     * 获取配置属性
     */
    public LangChainProperties getProperties() {
        return properties;
    }
}
