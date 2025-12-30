package red.jiuzhou.ai;

import java.util.List;

/**
 * AI模型工厂（Java 14+ switch表达式 + 不可变集合）
 *
 * <p>根据模型名称创建对应的AI客户端实例。
 */
public class AiModelFactory {

    /** 支持的模型列表（不可变） */
    private static final List<String> SUPPORTED_MODELS = List.of("qwen", "doubao", "kimi", "deepseek");

    /**
     * 根据模型名称获取AI客户端
     *
     * @param modelName 模型名称
     * @return AI客户端实例
     * @throws IllegalArgumentException 如果模型不支持
     */
    public static AiModelClient getClient(String modelName) {
        return switch (modelName.toLowerCase()) {
            case "qwen" -> new TongYiClient();
            case "doubao" -> new DoubaoClient();
            case "kimi" -> new KimiClient();
            case "deepseek" -> new DeepSeekClient();
            default -> throw new IllegalArgumentException("不支持的模型类型：" + modelName);
        };
    }

    /**
     * 获取模型的规范名称
     */
    public static String canonicalName(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        String normalized = modelName.toLowerCase().trim();
        if (SUPPORTED_MODELS.contains(normalized)) {
            return normalized;
        }
        // 处理别名
        if (normalized.contains("tongyi") || normalized.contains("qianwen")) {
            return "qwen";
        }
        if (normalized.contains("deep") && normalized.contains("seek")) {
            return "deepseek";
        }
        throw new IllegalArgumentException("不支持的模型类型：" + modelName);
    }

    /**
     * 返回支持的模型列表
     */
    public static List<String> supportedModels() {
        return SUPPORTED_MODELS;
    }
}