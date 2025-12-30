package red.jiuzhou.ai;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.YamlUtils;

import java.util.List;

/**
 * DashScope SDK 客户端公共基类
 *
 * <p>提取自 TongYiClient、KimiClient、DeepSeekClient 的公共代码
 * <p>遵循《重构》原则：Extract Superclass（提取超类）
 *
 * <p>子类只需实现 {@link #buildMessages(String)} 方法来定制消息格式
 *
 * @author Claude (重构)
 * @date 2025-12-26
 */
public abstract class BaseDashScopeClient implements AiModelClient {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 模型名称 */
    protected final String model;

    /** API密钥 */
    protected final String apiKey;

    /** 客户端显示名称（用于日志） */
    protected final String clientName;

    /**
     * 构造函数
     *
     * @param configPrefix 配置前缀（如 "qwen"、"kimi"、"deepseek"）
     * @param clientName   客户端显示名称（用于日志和错误消息）
     */
    protected BaseDashScopeClient(String configPrefix, String clientName) {
        this.model = YamlUtils.getProperty("ai." + configPrefix + ".model");
        this.apiKey = YamlUtils.getProperty("ai." + configPrefix + ".apikey");
        this.clientName = clientName;
    }

    /**
     * 校验 API Key 是否已配置
     *
     * @throws RuntimeException 如果 API Key 未配置
     */
    protected void validateApiKey() {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("未配置 %s 的 API KEY，请检查配置项 ai.%s.apikey"
                    .formatted(clientName, getConfigPrefix()));
        }
    }

    /**
     * 获取配置前缀（用于错误消息）
     */
    protected abstract String getConfigPrefix();

    /**
     * 构建消息列表（模板方法，子类可覆盖）
     *
     * <p>默认实现只包含用户消息，子类可添加系统消息等
     *
     * @param prompt 用户输入
     * @return 消息列表
     */
    protected List<Message> buildMessages(String prompt) {
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();
        return List.of(userMsg);
    }

    /**
     * 构建生成参数（模板方法，子类可覆盖）
     *
     * @param messages 消息列表
     * @return 生成参数
     */
    protected GenerationParam buildGenerationParam(List<Message> messages) {
        return GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(messages)
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .enableThinking(false)
                .build();
    }

    /**
     * 从结果中提取内容（模板方法，子类可覆盖）
     *
     * @param result 生成结果
     * @return 提取的内容
     */
    protected String extractContent(GenerationResult result) {
        if (result.getOutput() != null
                && result.getOutput().getChoices() != null
                && !result.getOutput().getChoices().isEmpty()) {
            return result.getOutput().getChoices().get(0).getMessage().getContent();
        }
        log.warn("{}模型返回空内容：{}", clientName, result);
        return "";
    }

    /**
     * 发送对话请求（模板方法模式）
     *
     * <p>执行流程：
     * <ol>
     *   <li>校验 API Key</li>
     *   <li>构建消息列表</li>
     *   <li>构建生成参数</li>
     *   <li>调用 API</li>
     *   <li>提取并返回内容</li>
     * </ol>
     */
    @Override
    public String chat(String prompt) throws RuntimeException {
        validateApiKey();
        log.info("向{}模型发送消息：{}", clientName, prompt);

        try {
            // 1. 构建消息
            List<Message> messages = buildMessages(prompt);

            // 2. 构建参数
            GenerationParam param = buildGenerationParam(messages);

            // 3. 调用 API
            Generation generation = new Generation();
            GenerationResult result = generation.call(param);

            // 4. 提取内容
            String content = extractContent(result);

            // 5. 校验并返回
            if (content == null || content.isEmpty()) {
                log.error("{}模型处理异常：{}", clientName, result);
                return "";
            }

            log.info("{}模型返回：{}", clientName, content);
            return content;

        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.error("调用{}模型出错：{}", clientName, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
