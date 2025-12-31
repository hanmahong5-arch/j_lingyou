package red.jiuzhou.langchain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LangChain4j 配置属性
 *
 * <p>从 application.yml 读取 AI 模型配置，支持：
 * <ul>
 *   <li>通义千问 (Qwen) - DashScope 官方支持</li>
 *   <li>DeepSeek - OpenAI 兼容接口</li>
 *   <li>Kimi - OpenAI 兼容接口</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class LangChainProperties {

    private ModelConfig qwen = new ModelConfig();
    private ModelConfig deepseek = new ModelConfig();
    private ModelConfig kimi = new ModelConfig();

    // 默认使用的模型
    private String defaultModel = "qwen";

    // 通用配置
    private double temperature = 0.7;
    private int maxTokens = 4096;
    private int maxRetries = 3;
    private int timeoutSeconds = 60;

    // RAG 配置
    private RagConfig rag = new RagConfig();

    // Getters and Setters
    public ModelConfig getQwen() {
        return qwen;
    }

    public void setQwen(ModelConfig qwen) {
        this.qwen = qwen;
    }

    public ModelConfig getDeepseek() {
        return deepseek;
    }

    public void setDeepseek(ModelConfig deepseek) {
        this.deepseek = deepseek;
    }

    public ModelConfig getKimi() {
        return kimi;
    }

    public void setKimi(ModelConfig kimi) {
        this.kimi = kimi;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public RagConfig getRag() {
        return rag;
    }

    public void setRag(RagConfig rag) {
        this.rag = rag;
    }

    /**
     * 获取指定模型的配置
     */
    public ModelConfig getModelConfig(String modelName) {
        return switch (modelName.toLowerCase()) {
            case "qwen", "tongyi", "dashscope" -> qwen;
            case "deepseek" -> deepseek;
            case "kimi", "moonshot" -> kimi;
            default -> qwen;
        };
    }

    /**
     * 单个模型配置
     */
    public static class ModelConfig {
        private String apikey;
        private String model;
        private String baseUrl;  // 可选，用于 OpenAI 兼容接口

        public String getApikey() {
            return apikey;
        }

        public void setApikey(String apikey) {
            this.apikey = apikey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean isConfigured() {
            return apikey != null && !apikey.isBlank() && model != null && !model.isBlank();
        }
    }

    /**
     * RAG 配置
     */
    public static class RagConfig {
        private boolean enabled = true;
        private String embeddingModel = "text-embedding-v2";  // DashScope 嵌入模型
        private int maxResults = 5;
        private double minScore = 0.7;
        private int chunkSize = 500;
        private int chunkOverlap = 50;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public void setMaxResults(int maxResults) {
            this.maxResults = maxResults;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }
    }
}
