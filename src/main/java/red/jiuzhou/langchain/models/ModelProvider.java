package red.jiuzhou.langchain.models;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;

/**
 * 模型提供者接口
 *
 * <p>定义统一的模型创建接口，各模型提供商实现此接口。
 *
 * @author Claude
 * @version 1.0
 */
public interface ModelProvider {

    /**
     * 获取提供商名称
     */
    String getName();

    /**
     * 获取支持的模型类型标识列表
     */
    String[] getSupportedTypes();

    /**
     * 检查是否已正确配置
     */
    boolean isConfigured();

    /**
     * 创建聊天模型
     */
    ChatLanguageModel createChatModel();

    /**
     * 创建流式聊天模型（可选）
     *
     * @return 流式模型，如不支持返回 null
     */
    default StreamingChatLanguageModel createStreamingChatModel() {
        return null;
    }

    /**
     * 获取模型显示名称
     */
    default String getDisplayName() {
        return getName();
    }

    /**
     * 获取模型描述
     */
    default String getDescription() {
        return getName() + " Language Model";
    }
}
