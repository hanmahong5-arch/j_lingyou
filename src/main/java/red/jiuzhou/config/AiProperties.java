package red.jiuzhou.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI模型配置属性（Spring Boot 4 风格）
 *
 * <p>从 application.yml 中读取 ai.* 配置项，支持多模型配置。
 *
 * @author Claude
 * @version 1.0
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    /** 默认模型名称 */
    private String defaultModel = "qwen";

    /** 通义千问配置 */
    private ModelConfig qwen = new ModelConfig();

    /** 豆包配置 */
    private ModelConfig doubao = new ModelConfig();

    /** Kimi配置 */
    private ModelConfig kimi = new ModelConfig();

    /** DeepSeek配置 */
    private ModelConfig deepseek = new ModelConfig();

    /**
     * 单个模型配置
     */
    public static class ModelConfig {
        private String apikey;
        private String model;
        private String baseUrl;
        private int timeout = 60000;
        private int maxRetries = 3;

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

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public boolean isConfigured() {
            return apikey != null && !apikey.isBlank() && model != null && !model.isBlank();
        }
    }

    // ========== Getters & Setters ==========

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public ModelConfig getQwen() {
        return qwen;
    }

    public void setQwen(ModelConfig qwen) {
        this.qwen = qwen;
    }

    public ModelConfig getDoubao() {
        return doubao;
    }

    public void setDoubao(ModelConfig doubao) {
        this.doubao = doubao;
    }

    public ModelConfig getKimi() {
        return kimi;
    }

    public void setKimi(ModelConfig kimi) {
        this.kimi = kimi;
    }

    public ModelConfig getDeepseek() {
        return deepseek;
    }

    public void setDeepseek(ModelConfig deepseek) {
        this.deepseek = deepseek;
    }

    /**
     * 根据模型名称获取配置
     */
    public ModelConfig getModelConfig(String modelName) {
        return switch (modelName.toLowerCase()) {
            case "qwen" -> qwen;
            case "doubao" -> doubao;
            case "kimi" -> kimi;
            case "deepseek" -> deepseek;
            default -> qwen;
        };
    }
}
