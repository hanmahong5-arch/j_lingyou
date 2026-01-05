package red.jiuzhou.langchain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import red.jiuzhou.agent.context.DesignContext;
import red.jiuzhou.agent.core.AgentContext;
import red.jiuzhou.agent.core.AgentMessage;
import red.jiuzhou.agent.execution.OperationExecutor;
import red.jiuzhou.agent.history.OperationLogger;
import red.jiuzhou.langchain.rag.GameContentRetriever;
import red.jiuzhou.langchain.rag.GameSchemaEmbeddingService;
import red.jiuzhou.langchain.service.GameDataAssistant;
import red.jiuzhou.langchain.service.GameDataAssistantFactory;
import red.jiuzhou.langchain.tools.GameDataTools;

import java.util.*;
import java.util.function.Consumer;

/**
 * 基于 LangChain4j 的游戏数据 Agent
 *
 * <p>使用 LangChain4j AI Service 重构的 GameDataAgent，提供：
 * <ul>
 *   <li>声明式 AI Service - 通过接口定义对话行为</li>
 *   <li>自动工具调用 - @Tool 注解的方法自动发现和调用</li>
 *   <li>内置对话记忆 - ChatMemory 管理对话历史</li>
 *   <li>RAG 增强 - 可选的语义检索能力</li>
 * </ul>
 *
 * <p>保持与原 GameDataAgent 兼容的对外接口。
 *
 * @author Claude
 * @version 2.0
 */
@Component
public class LangChainGameDataAgent {

    private static final Logger log = LoggerFactory.getLogger(LangChainGameDataAgent.class);

    // ========== 依赖组件 ==========
    private final LangChainModelFactory modelFactory;
    private final GameDataAssistantFactory assistantFactory;
    private final GameSchemaEmbeddingService embeddingService;
    private final GameContentRetriever contentRetriever;

    private JdbcTemplate jdbcTemplate;
    private OperationLogger operationLogger;
    private OperationExecutor operationExecutor;
    private GameDataTools gameDataTools;

    // ========== 当前状态 ==========
    private String currentModel = "qwen";
    private String currentSessionId;
    private GameDataAssistant assistant;
    private boolean ragEnabled = true;

    // ========== 回调 ==========
    private Consumer<AgentMessage> messageCallback;

    // ========== 对话历史（本地缓存） ==========
    private final Map<String, List<AgentMessage>> sessionHistory = new LinkedHashMap<>();

    @Autowired
    public LangChainGameDataAgent(
            LangChainModelFactory modelFactory,
            GameDataAssistantFactory assistantFactory,
            @Autowired(required = false) GameSchemaEmbeddingService embeddingService,
            @Autowired(required = false) GameContentRetriever contentRetriever
    ) {
        this.modelFactory = modelFactory;
        this.assistantFactory = assistantFactory;
        this.embeddingService = embeddingService;
        this.contentRetriever = contentRetriever;

        // RAG 功能是否可用取决于依赖是否注入
        this.ragEnabled = (embeddingService != null && contentRetriever != null);

        log.info("LangChainGameDataAgent 创建完成, RAG功能: {}", ragEnabled ? "已启用" : "未配置");
    }

    /**
     * 初始化 Agent
     */
    public void initialize(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.operationLogger = new OperationLogger();
        this.operationLogger.setJdbcTemplate(jdbcTemplate);
        this.operationExecutor = new OperationExecutor(jdbcTemplate, operationLogger);
        this.gameDataTools = new GameDataTools(jdbcTemplate);

        // 检查 API key 是否配置
        if (isAiConfigured()) {
            try {
                // 创建 AI Service
                createAssistant();

                // 创建新会话
                startNewSession();

                log.info("LangChainGameDataAgent 初始化完成，模型: {}, RAG: {}", currentModel, ragEnabled);
            } catch (Exception e) {
                log.warn("AI 功能初始化失败，将以纯工具模式运行: {}", e.getMessage());
                this.assistant = null;
            }
        } else {
            log.warn("AI API Key 未配置，Agent 将以纯工具模式运行（仅支持直接SQL执行）");
            this.assistant = null;
        }
    }

    /**
     * 检查 AI 是否已配置
     */
    private boolean isAiConfigured() {
        var qwenConfig = modelFactory.getProperties().getQwen();
        return qwenConfig != null && qwenConfig.isConfigured();
    }

    /**
     * 创建 AI Service
     */
    private void createAssistant() {
        try {
            log.info("开始创建 Assistant，模型: {}, RAG: {}", currentModel, ragEnabled);
            if (ragEnabled && contentRetriever != null) {
                assistant = assistantFactory.createAssistantWithRag(currentModel, contentRetriever);
                log.info("创建带 RAG 的 GameDataAssistant");
            } else {
                log.info("调用 assistantFactory.getAssistant()");
                assistant = assistantFactory.getAssistant(currentModel);
                log.info("创建标准 GameDataAssistant 成功");
            }
        } catch (Exception e) {
            log.error("创建 Assistant 失败", e);
            throw new RuntimeException("创建 AI Assistant 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 开始新会话
     */
    public String startNewSession() {
        currentSessionId = UUID.randomUUID().toString().substring(0, 8);
        sessionHistory.put(currentSessionId, new ArrayList<>());

        log.info("创建新会话: {}", currentSessionId);
        return currentSessionId;
    }

    /**
     * 处理用户消息
     */
    public AgentMessage chat(String userInput) {
        return chat(userInput, null);
    }

    /**
     * 处理用户消息（带上下文）
     */
    public AgentMessage chat(String userInput, DesignContext context) {
        if (currentSessionId == null) {
            startNewSession();
        }

        log.info("处理用户输入: {}", userInput);

        // 构建增强输入
        String enhancedInput = userInput;
        if (context != null) {
            enhancedInput = buildContextAwareInput(context, userInput);
            log.debug("增强输入: {}", enhancedInput);
        }

        // 记录用户消息
        AgentMessage userMessage = AgentMessage.user(enhancedInput);
        addToHistory(userMessage);
        notifyMessage(userMessage);

        // 处理确认/取消命令
        if (isConfirmCommand(userInput)) {
            return handleConfirm();
        } else if (isCancelCommand(userInput)) {
            return handleCancel();
        }

        // 检查 AI 是否可用
        if (assistant == null) {
            AgentMessage errorMsg = AgentMessage.assistant("AI 功能未配置或初始化失败。请检查 application.yml 中的 AI API Key 配置。");
            addToHistory(errorMsg);
            notifyMessage(errorMsg);
            return errorMsg;
        }

        // 调用 AI Service
        try {
            String response = assistant.chat(currentSessionId, enhancedInput);

            // 检查是否有待确认操作
            if (hasPendingOperation()) {
                AgentMessage pendingMsg = createPendingMessage();
                addToHistory(pendingMsg);
                notifyMessage(pendingMsg);
                return pendingMsg;
            }

            // 创建助手消息
            AgentMessage assistantMsg = AgentMessage.assistant(response);
            addToHistory(assistantMsg);
            notifyMessage(assistantMsg);
            return assistantMsg;

        } catch (Exception e) {
            log.error("AI 调用失败", e);
            AgentMessage errorMsg = AgentMessage.error("AI 服务调用失败: " + e.getMessage());
            addToHistory(errorMsg);
            notifyMessage(errorMsg);
            return errorMsg;
        }
    }

    /**
     * 快速查询（不保留历史）
     */
    public String quickQuery(String question) {
        if (assistant == null) {
            throw new IllegalStateException("Agent 未初始化");
        }
        return assistant.quickQuery(question);
    }

    /**
     * 生成 SQL（不执行）
     */
    public String generateSql(String query, String tableSchema) {
        if (assistant == null) {
            throw new IllegalStateException("Agent 未初始化");
        }
        return assistant.generateSql(query, tableSchema);
    }

    /**
     * 数据分析
     */
    public String analyzeData(String analysisRequest) {
        if (assistant == null) {
            throw new IllegalStateException("Agent 未初始化");
        }
        return assistant.analyzeData(analysisRequest);
    }

    /**
     * 构建带上下文的输入
     */
    private String buildContextAwareInput(DesignContext context, String userInput) {
        StringBuilder sb = new StringBuilder();

        // 添加上下文信息
        if (context.getTableName() != null) {
            sb.append("【当前表】").append(context.getTableName()).append("\n");
        }
        if (context.getFocusPath() != null) {
            sb.append("【文件路径】").append(context.getFocusPath()).append("\n");
        }
        if (context.getRowData() != null && !context.getRowData().isEmpty()) {
            sb.append("【选中数据】\n");
            for (Map.Entry<String, Object> entry : context.getRowData().entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append("\n");
            }
        }

        // 添加用户输入
        if (sb.length() > 0) {
            sb.append("\n【用户问题】\n");
        }
        sb.append(userInput);

        return sb.toString();
    }

    /**
     * 处理确认操作
     */
    private AgentMessage handleConfirm() {
        if (!hasPendingOperation()) {
            return AgentMessage.assistant("当前没有待确认的操作");
        }

        // 获取并执行待确认操作
        Collection<GameDataTools.PendingOperation> pending = gameDataTools.getPendingOperations();
        if (pending.isEmpty()) {
            return AgentMessage.assistant("当前没有待确认的操作");
        }

        GameDataTools.PendingOperation op = pending.iterator().next();
        GameDataTools.ExecutionResult result = gameDataTools.confirmAndExecute(op.operationId());

        AgentMessage msg;
        if (result.success()) {
            msg = AgentMessage.assistant(String.format(
                    "✅ 操作执行成功\n\n**影响行数**: %d\n**说明**: %s",
                    result.affectedRows(), result.message()));
        } else {
            msg = AgentMessage.error("❌ 操作执行失败: " + result.message());
        }

        addToHistory(msg);
        notifyMessage(msg);
        return msg;
    }

    /**
     * 处理取消操作
     */
    private AgentMessage handleCancel() {
        if (!hasPendingOperation()) {
            return AgentMessage.assistant("当前没有待确认的操作");
        }

        Collection<GameDataTools.PendingOperation> pending = gameDataTools.getPendingOperations();
        for (GameDataTools.PendingOperation op : pending) {
            gameDataTools.cancelOperation(op.operationId());
        }

        AgentMessage msg = AgentMessage.assistant("已取消所有待确认操作");
        addToHistory(msg);
        notifyMessage(msg);
        return msg;
    }

    /**
     * 创建待确认消息
     */
    private AgentMessage createPendingMessage() {
        Collection<GameDataTools.PendingOperation> pending = gameDataTools.getPendingOperations();
        if (pending.isEmpty()) {
            return AgentMessage.assistant("操作完成");
        }

        GameDataTools.PendingOperation op = pending.iterator().next();

        StringBuilder sb = new StringBuilder();
        sb.append("## ⚠️ 待确认操作\n\n");
        sb.append("**操作类型**: ").append(op.sqlType()).append("\n");
        sb.append("**操作说明**: ").append(op.description()).append("\n");
        sb.append("**预计影响**: ").append(op.estimatedRows()).append(" 行\n\n");
        sb.append("### SQL语句\n");
        sb.append("```sql\n").append(op.sql()).append("\n```\n\n");
        sb.append("---\n");
        sb.append("请输入 **确认** 执行操作，或 **取消** 放弃操作。");

        AgentMessage msg = AgentMessage.pendingConfirmation(op.sql(), op.estimatedRows());
        msg.setContent(sb.toString());
        return msg;
    }

    /**
     * 切换 AI 模型
     */
    public void switchModel(String modelName) {
        this.currentModel = modelName;
        createAssistant();
        log.info("切换 AI 模型: {}", modelName);
    }

    /**
     * 启用/禁用 RAG
     */
    public void setRagEnabled(boolean enabled) {
        if (this.ragEnabled != enabled) {
            this.ragEnabled = enabled;
            createAssistant();
            log.info("RAG 已{}", enabled ? "启用" : "禁用");
        }
    }

    /**
     * 清空当前会话
     */
    public void clearSession() {
        if (currentSessionId != null) {
            sessionHistory.remove(currentSessionId);
            assistantFactory.clearSessionMemory(currentSessionId);
        }
        startNewSession();
    }

    /**
     * 获取会话历史
     */
    public List<AgentMessage> getHistory() {
        return sessionHistory.getOrDefault(currentSessionId, Collections.emptyList());
    }

    /**
     * 检查是否有待确认操作
     */
    public boolean hasPendingOperation() {
        return gameDataTools != null && !gameDataTools.getPendingOperations().isEmpty();
    }

    // ========== 私有方法 ==========

    private void addToHistory(AgentMessage message) {
        sessionHistory.computeIfAbsent(currentSessionId, k -> new ArrayList<>()).add(message);
    }

    private void notifyMessage(AgentMessage message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }

    private boolean isConfirmCommand(String input) {
        if (input == null) return false;
        String lower = input.trim().toLowerCase();
        return lower.equals("确认") || lower.equals("confirm") ||
                lower.equals("yes") || lower.equals("y") ||
                lower.equals("执行") || lower.equals("ok");
    }

    private boolean isCancelCommand(String input) {
        if (input == null) return false;
        String lower = input.trim().toLowerCase();
        return lower.equals("取消") || lower.equals("cancel") ||
                lower.equals("no") || lower.equals("n") ||
                lower.equals("放弃") || lower.equals("abort");
    }

    // ========== Getter/Setter ==========

    public String getCurrentModel() {
        return currentModel;
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public boolean isRagEnabled() {
        return ragEnabled;
    }

    public OperationLogger getOperationLogger() {
        return operationLogger;
    }

    public void setMessageCallback(Consumer<AgentMessage> callback) {
        this.messageCallback = callback;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public GameDataTools getGameDataTools() {
        return gameDataTools;
    }
}
