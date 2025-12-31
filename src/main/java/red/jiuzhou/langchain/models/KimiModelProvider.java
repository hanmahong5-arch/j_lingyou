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
 * Kimi (Moonshot) 模型提供者
 *
 * <p>使用 OpenAI 兼容接口连接 Moonshot API。
 *
 * <p>支持的模型：
 * <ul>
 *   <li>moonshot-v1-8k - 8K 上下文</li>
 *   <li>moonshot-v1-32k - 32K 上下文</li>
 *   <li>moonshot-v1-128k - 128K 上下文</li>
 *   <li>Moonshot-Kimi-K2-Instruct - K2 指令模型</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class KimiModelProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(KimiModelProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.moonshot.cn/v1";

    private final LangChainProperties.ModelConfig config;
    private final LangChainProperties properties;

    public KimiModelProvider(LangChainProperties properties) {
        this.properties = properties;
        this.config = properties.getKimi();
    }

    @Override
    public String getName() {
        return "kimi";
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{"kimi", "moonshot"};
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public ChatLanguageModel createChatModel() {
        if (!isConfigured()) {
            throw new IllegalStateException("Kimi 模型未配置 API Key");
        }

        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL;
        log.info("创建 Kimi ChatModel: {} (baseUrl: {})", config.getModel(), baseUrl);

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
            throw new IllegalStateException("Kimi 模型未配置 API Key");
        }

        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : DEFAULT_BASE_URL;
        log.info("创建 Kimi StreamingChatModel: {} (baseUrl: {})", config.getModel(), baseUrl);

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
        return "Kimi (" + config.getModel() + ")";
    }

    @Override
    public String getDescription() {
        return "Moonshot Kimi 大语言模型，支持超长上下文对话";
    }
}
