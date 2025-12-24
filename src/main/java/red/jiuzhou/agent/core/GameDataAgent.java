package red.jiuzhou.agent.core;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.agent.execution.OperationExecutor;
import red.jiuzhou.agent.history.OperationLogger;
import red.jiuzhou.agent.security.SqlSecurityFilter;
import red.jiuzhou.agent.texttosql.QuerySelfCorrection;
import red.jiuzhou.agent.texttosql.SqlExampleLibrary;
import red.jiuzhou.agent.tools.*;
import red.jiuzhou.ai.AiModelClient;
import red.jiuzhou.ai.AiModelFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 游戏数据Agent主控制器
 *
 * 整合所有组件，提供统一的对话接口
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class GameDataAgent {

    private static final Logger log = LoggerFactory.getLogger(GameDataAgent.class);

    // ========== 核心组件 ==========
    private JdbcTemplate jdbcTemplate;
    private ConversationManager conversationManager;
    private SchemaMetadataService metadataService;
    private PromptBuilder promptBuilder;
    private ResponseParser responseParser;
    private ToolRegistry toolRegistry;
    private OperationExecutor operationExecutor;
    private OperationLogger operationLogger;
    private SqlSecurityFilter securityFilter;

    // ========== TEXT-TO-SQL增强组件 ==========
    private QuerySelfCorrection querySelfCorrection;
    private SqlExampleLibrary exampleLibrary;

    // ========== AI模型 ==========
    private String currentModel = "qwen";  // 默认使用qwen
    private AiModelClient aiClient;

    // ========== 当前会话 ==========
    private String currentSessionId;
    private AgentContext currentContext;

    // ========== 回调 ==========
    private Consumer<AgentMessage> messageCallback;

    public GameDataAgent() {
        this.conversationManager = new ConversationManager();
        this.responseParser = new ResponseParser();
        this.toolRegistry = ToolRegistry.getInstance();
        this.operationLogger = new OperationLogger();
    }

    /**
     * 初始化Agent（增强版）
     *
     * @param jdbcTemplate 数据库连接
     */
    public void initialize(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;

        // 初始化核心组件
        this.securityFilter = new SqlSecurityFilter(jdbcTemplate);
        this.operationLogger.setJdbcTemplate(jdbcTemplate);
        this.operationExecutor = new OperationExecutor(jdbcTemplate, operationLogger);
        this.metadataService = new SchemaMetadataService(jdbcTemplate);
        this.metadataService.initialize();

        // 初始化TEXT-TO-SQL增强组件
        this.querySelfCorrection = new QuerySelfCorrection(jdbcTemplate);
        this.exampleLibrary = SqlExampleLibrary.getInstance();

        // 注册工具
        registerTools();

        // 初始化Prompt构建器（增强版）
        this.promptBuilder = new PromptBuilder(metadataService, toolRegistry);
        this.promptBuilder.setJdbcTemplate(jdbcTemplate);

        // 初始化AI客户端
        switchModel(currentModel);

        // 创建新会话
        startNewSession();

        log.info("GameDataAgent 初始化完成（TEXT-TO-SQL增强版），当前模型: {}", currentModel);
    }

    /**
     * 注册所有工具
     */
    private void registerTools() {
        toolRegistry.clear();

        // 查询工具
        QueryTool queryTool = new QueryTool(securityFilter);
        toolRegistry.register(queryTool);

        // 修改工具
        ModifyTool modifyTool = new ModifyTool(securityFilter);
        toolRegistry.register(modifyTool);

        // 分析工具
        AnalyzeTool analyzeTool = new AnalyzeTool();
        toolRegistry.register(analyzeTool);

        // 历史工具
        HistoryTool historyTool = new HistoryTool(operationLogger);
        toolRegistry.register(historyTool);

        log.info("已注册 {} 个工具", toolRegistry.size());
    }

    /**
     * 开始新会话
     */
    public String startNewSession() {
        currentSessionId = conversationManager.createSession();

        // 创建上下文
        currentContext = new AgentContext();
        currentContext.setSessionId(currentSessionId);
        currentContext.setJdbcTemplate(jdbcTemplate);
        currentContext.setCurrentModel(currentModel);

        // 设置系统提示词
        String systemPrompt = promptBuilder.buildSystemPrompt();
        conversationManager.setSystemPrompt(currentSessionId, systemPrompt);

        log.info("创建新会话: {}", currentSessionId);
        return currentSessionId;
    }

    /**
     * 处理用户消息
     *
     * @param userInput 用户输入
     * @return Agent响应
     */
    public AgentMessage chat(String userInput) {
        if (currentSessionId == null) {
            startNewSession();
        }

        log.info("处理用户输入: {}", userInput);

        // 添加用户消息到会话
        AgentMessage userMessage = AgentMessage.user(userInput);
        conversationManager.addMessage(currentSessionId, userMessage);
        notifyMessage(userMessage);

        // 检测用户意图
        ResponseParser.UserIntent intent = responseParser.detectIntent(userInput);

        // 处理确认/取消操作
        if (intent == ResponseParser.UserIntent.CONFIRM) {
            return handleConfirm();
        } else if (intent == ResponseParser.UserIntent.CANCEL) {
            return handleCancel();
        }

        // 调用AI获取响应
        try {
            String aiResponse = callAi();
            return processAiResponse(aiResponse);
        } catch (Exception e) {
            log.error("AI调用失败", e);
            AgentMessage errorMsg = AgentMessage.error("AI服务调用失败: " + e.getMessage());
            conversationManager.addMessage(currentSessionId, errorMsg);
            notifyMessage(errorMsg);
            return errorMsg;
        }
    }

    /**
     * 调用AI获取响应
     */
    private String callAi() {
        if (aiClient == null) {
            throw new IllegalStateException("AI客户端未初始化");
        }

        // 构建消息列表并转换为提示词
        List<Map<String, String>> messages = conversationManager.buildAiMessages(currentSessionId);
        String prompt = buildPromptFromMessages(messages);

        // 调用AI
        return aiClient.chat(prompt);
    }

    /**
     * 将消息列表转换为提示词字符串
     */
    private String buildPromptFromMessages(List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder();

        for (Map<String, String> msg : messages) {
            String role = msg.get("role");
            String content = msg.get("content");

            if ("system".equals(role)) {
                sb.append("【系统】\n").append(content).append("\n\n");
            } else if ("user".equals(role)) {
                sb.append("【用户】\n").append(content).append("\n\n");
            } else if ("assistant".equals(role)) {
                sb.append("【助手】\n").append(content).append("\n\n");
            } else if ("tool".equals(role)) {
                sb.append("【工具结果】\n").append(content).append("\n\n");
            }
        }

        sb.append("【助手】\n");
        return sb.toString();
    }

    /**
     * 处理AI响应
     */
    private AgentMessage processAiResponse(String aiResponse) {
        log.debug("AI响应: {}", aiResponse);

        // 解析响应
        ResponseParser.ParsedResponse parsed = responseParser.parse(aiResponse);

        // 处理工具调用
        if (parsed.hasToolCall()) {
            for (ResponseParser.ToolCallRequest toolCall : parsed.getToolCalls()) {
                AgentMessage toolResult = executeToolCall(toolCall);

                // 如果是待确认操作，直接返回
                if (toolResult.getType() == AgentMessage.MessageType.PENDING_CONFIRMATION) {
                    return toolResult;
                }

                // 将工具结果添加到会话
                conversationManager.addMessage(currentSessionId, toolResult);
                notifyMessage(toolResult);
            }
        }

        // 创建助手消息
        String textContent = parsed.getTextMessage();
        if (textContent != null && !textContent.isEmpty()) {
            AgentMessage assistantMsg = AgentMessage.assistant(textContent);
            conversationManager.addMessage(currentSessionId, assistantMsg);
            notifyMessage(assistantMsg);
            return assistantMsg;
        }

        // 如果只有工具调用没有文本，返回最后一个工具结果
        AgentMessage lastMsg = conversationManager.getLastMessage(currentSessionId);
        return lastMsg != null ? lastMsg : AgentMessage.assistant("操作完成");
    }

    /**
     * 执行工具调用
     */
    private AgentMessage executeToolCall(ResponseParser.ToolCallRequest toolCall) {
        String toolName = toolCall.getToolName();
        log.info("执行工具: {}", toolName);

        AgentTool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return AgentMessage.error("未知的工具: " + toolName);
        }

        // 记录工具调用
        AgentMessage callMsg = AgentMessage.toolCall(toolName, toolCall.getRawJson());
        conversationManager.addMessage(currentSessionId, callMsg);
        notifyMessage(callMsg);

        // 执行工具
        String params = toolCall.getParameters() != null ?
            toolCall.getParameters().toJSONString() : "{}";
        ToolResult result = tool.execute(currentContext, params);

        // 处理结果
        if (result.isPendingConfirmation()) {
            // 待确认操作
            AgentMessage pendingMsg = AgentMessage.pendingConfirmation(
                result.getGeneratedSql(), result.getAffectedRows());
            pendingMsg.setContent(result.getMessage());
            pendingMsg.setType(AgentMessage.MessageType.PENDING_CONFIRMATION);
            conversationManager.addMessage(currentSessionId, pendingMsg);
            notifyMessage(pendingMsg);
            return pendingMsg;

        } else if (result.getType() == ToolResult.ResultType.TABLE) {
            // 表格结果
            AgentMessage tableMsg = AgentMessage.toolResult(toolName, formatTableResult(result));
            tableMsg.setType(AgentMessage.MessageType.DATA_TABLE);
            return tableMsg;

        } else if (result.isSuccess()) {
            // 成功结果
            return AgentMessage.toolResult(toolName, result.getMessage());

        } else {
            // 错误结果
            return AgentMessage.error(result.getMessage());
        }
    }

    /**
     * 格式化表格结果
     */
    private String formatTableResult(ToolResult result) {
        List<Map<String, Object>> data = result.getTableData();
        List<String> columns = result.getColumns();

        if (data == null || data.isEmpty()) {
            return "查询返回空结果";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("查询返回 ").append(data.size()).append(" 条记录\n\n");

        // 表头
        sb.append("| ");
        for (String col : columns) {
            sb.append(col).append(" | ");
        }
        sb.append("\n|");
        for (int i = 0; i < columns.size(); i++) {
            sb.append("---|");
        }
        sb.append("\n");

        // 数据行（最多显示20行）
        int displayCount = Math.min(data.size(), 20);
        for (int i = 0; i < displayCount; i++) {
            Map<String, Object> row = data.get(i);
            sb.append("| ");
            for (String col : columns) {
                Object val = row.get(col);
                sb.append(val != null ? val.toString() : "NULL").append(" | ");
            }
            sb.append("\n");
        }

        if (data.size() > displayCount) {
            sb.append("\n... 共 ").append(data.size()).append(" 条记录，仅显示前 ")
              .append(displayCount).append(" 条");
        }

        return sb.toString();
    }

    /**
     * 处理确认操作
     */
    private AgentMessage handleConfirm() {
        if (!currentContext.hasPendingOperation()) {
            return AgentMessage.assistant("当前没有待确认的操作");
        }

        OperationExecutor.ExecutionResult result = operationExecutor.executePending(currentContext);

        AgentMessage msg;
        if (result.isSuccess()) {
            msg = AgentMessage.assistant(String.format(
                "✅ 操作执行成功\n\n**操作ID**: %s\n**影响行数**: %d\n**耗时**: %dms",
                result.getOperationId(), result.getAffectedRows(), result.getExecutionTime()));
        } else {
            msg = AgentMessage.error("❌ 操作执行失败: " + result.getErrorMessage());
        }

        conversationManager.addMessage(currentSessionId, msg);
        notifyMessage(msg);
        return msg;
    }

    /**
     * 处理取消操作
     */
    private AgentMessage handleCancel() {
        if (!currentContext.hasPendingOperation()) {
            return AgentMessage.assistant("当前没有待确认的操作");
        }

        operationExecutor.cancelPending(currentContext);

        AgentMessage msg = AgentMessage.assistant("已取消操作");
        conversationManager.addMessage(currentSessionId, msg);
        notifyMessage(msg);
        return msg;
    }

    /**
     * 切换AI模型
     */
    public void switchModel(String modelName) {
        this.currentModel = modelName;
        this.aiClient = AiModelFactory.getClient(modelName);

        if (currentContext != null) {
            currentContext.setCurrentModel(modelName);
        }

        log.info("切换AI模型: {}", modelName);
    }

    /**
     * 清空当前会话
     */
    public void clearSession() {
        if (currentSessionId != null) {
            conversationManager.clearSession(currentSessionId);
            if (currentContext != null) {
                currentContext.clearPendingOperation();
            }
        }
    }

    /**
     * 获取会话历史
     */
    public List<AgentMessage> getHistory() {
        if (currentSessionId != null) {
            return conversationManager.getHistory(currentSessionId);
        }
        return java.util.Collections.emptyList();
    }

    /**
     * 设置消息回调
     */
    public void setMessageCallback(Consumer<AgentMessage> callback) {
        this.messageCallback = callback;
    }

    /**
     * 通知消息
     */
    private void notifyMessage(AgentMessage message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }

    /**
     * 检查是否有待确认操作
     */
    public boolean hasPendingOperation() {
        return currentContext != null && currentContext.hasPendingOperation();
    }

    /**
     * 获取待确认操作信息
     */
    public AgentContext.PendingOperation getPendingOperation() {
        return currentContext != null ? currentContext.getPendingOperation() : null;
    }

    // ========== Getter/Setter ==========

    public String getCurrentModel() {
        return currentModel;
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public SchemaMetadataService getMetadataService() {
        return metadataService;
    }

    public OperationLogger getOperationLogger() {
        return operationLogger;
    }

    public ConversationManager getConversationManager() {
        return conversationManager;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
}
