package red.jiuzhou.ai;

/**
 * DeepSeek 客户端
 *
 * <p>基于 DashScope SDK，继承公共基类
 * <p>使用默认消息格式（仅用户消息）
 *
 * @author Claude
 * @refactored Claude (2025-12-26) - 继承 BaseDashScopeClient
 */
public class DeepSeekClient extends BaseDashScopeClient {

    public DeepSeekClient() {
        super("deepseek", "DeepSeek");
    }

    @Override
    protected String getConfigPrefix() {
        return "deepseek";
    }

    // 使用基类默认的 buildMessages 实现（仅用户消息）
}
