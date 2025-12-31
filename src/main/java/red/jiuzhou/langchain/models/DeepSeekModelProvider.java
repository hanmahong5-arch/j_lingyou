package red.jiuzhou.langchain.models;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.langchain.config.LangChainProperties;

import java.time.Duration;

/**
 * DeepSeek 模型提供者
 *
 * <p>使用 OpenAI 兼容接口连接 DeepSeek API。
 *
 * <p>支持的模型：
 * <ul>
 *   <li>deepseek-chat - 对话模型</li>
 *   <li>deepseek-coder - 代码模型</li>
 *   <li>deepseek-r1 - 推理模型</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class DeepSeekModelProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekModelProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1";

    private final LangChainProperties.ModelConfig config;
    private final LangChainProperties properties;

    public DeepSeekModelProvider(LangChainProperties properties) {
        this.properties = properties;
        this.config = properties.getDeepseek();
    }

    @Override
    public String getName() {
        return "deepseek";
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{"deepseek"};
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public ChatLanguageModel createChatModel() {
        if (!isConfigured()) {
            throw new IllegalStateException("DeepSeek 模型未配置 API Key");
        }

        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL;
        log.info("创建 DeepSeek ChatModel: {} (baseUrl: {})", config.getModel(), baseUrl);

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

    @Override
    public StreamingChatLanguageModel createStreamingChatModel() {
        if (!isConfigured()) {
            throw new IllegalStateException("DeepSeek 模型未配置 API Key");
        }

        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL;
        log.info("创建 DeepSeek StreamingChatModel: {} (baseUrl: {})", config.getModel(), baseUrl);

        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(config.getApikey())
                .modelName(config.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }

    @Override
    public String getDisplayName() {
        return "DeepSeek (" + config.getModel() + ")";
    }

    @Override
    public String getDescription() {
        return "DeepSeek 大语言模型，擅长推理和代码生成";
    }
}
