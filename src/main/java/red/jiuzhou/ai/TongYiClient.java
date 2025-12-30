package red.jiuzhou.ai;

import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;

import java.util.List;

/**
 * 通义千问客户端
 *
 * <p>基于 DashScope SDK，继承公共基类
 * <p>特点：包含系统消息 "You are a helpful assistant."
 *
 * @author dream
 * @refactored Claude (2025-12-26) - 继承 BaseDashScopeClient
 */
public class TongYiClient extends BaseDashScopeClient {

    public TongYiClient() {
        super("qwen", "通义千问");
    }

    @Override
    protected String getConfigPrefix() {
        return "qwen";
    }

    /**
     * 构建消息列表（包含系统消息）
     */
    @Override
    protected List<Message> buildMessages(String prompt) {
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content("You are a helpful assistant.")
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();
        return List.of(systemMsg, userMsg);
    }
}
