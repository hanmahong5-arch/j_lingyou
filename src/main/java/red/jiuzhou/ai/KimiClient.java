package red.jiuzhou.ai;

/**
 * Moonshot-Kimi-K2-Instruct 模型客户端
 *
 * <p>基于 DashScope SDK，继承公共基类
 * <p>使用默认消息格式（仅用户消息）
 *
 * @author Claude
 * @refactored Claude (2025-12-26) - 继承 BaseDashScopeClient
 */
public class KimiClient extends BaseDashScopeClient {

    public KimiClient() {
        super("kimi", "Kimi");
    }

    @Override
    protected String getConfigPrefix() {
        return "kimi";
    }

    // 使用基类默认的 buildMessages 实现（仅用户消息）
}
