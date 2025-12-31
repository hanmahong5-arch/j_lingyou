package red.jiuzhou.langchain.models;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.langchain.config.LangChainProperties;

/**
 * 通义千问模型提供者
 *
 * <p>使用 LangChain4j 官方支持的 DashScope 集成。
 *
 * <p>支持的模型：
 * <ul>
 *   <li>qwen-plus - 增强版，推荐</li>
 *   <li>qwen-turbo - 快速版</li>
 *   <li>qwen-max - 最强版</li>
 *   <li>qwen-long - 长文本版</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class QwenModelProvider implements ModelProvider {

    private static final Logger log = LoggerFactory.getLogger(QwenModelProvider.class);

    private final LangChainProperties.ModelConfig config;
    private final LangChainProperties properties;

    public QwenModelProvider(LangChainProperties properties) {
        this.properties = properties;
        this.config = properties.getQwen();
    }

    @Override
    public String getName() {
        return "qwen";
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{"qwen", "tongyi", "dashscope"};
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public ChatLanguageModel createChatModel() {
        if (!isConfigured()) {
            throw new IllegalStateException("Qwen 模型未配置 API Key");
        }

        log.info("创建 Qwen ChatModel: {}", config.getModel());

        return QwenChatModel.builder()
                .apiKey(config.getApikey())
                .modelName(config.getModel())
                .temperature((float) properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .build();
    }

    @Override
    public StreamingChatLanguageModel createStreamingChatModel() {
        if (!isConfigured()) {
            throw new IllegalStateException("Qwen 模型未配置 API Key");
        }

        log.info("创建 Qwen StreamingChatModel: {}", config.getModel());

        return QwenStreamingChatModel.builder()
                .apiKey(config.getApikey())
                .modelName(config.getModel())
                .temperature((float) properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .build();
    }

    @Override
    public String getDisplayName() {
        return "通义千问 (" + config.getModel() + ")";
    }

    @Override
    public String getDescription() {
        return "阿里云通义千问大语言模型，适合中文对话和代码生成";
    }
}
