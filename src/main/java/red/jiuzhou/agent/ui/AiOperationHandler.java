package red.jiuzhou.agent.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.context.DesignContext;
import red.jiuzhou.agent.core.AgentMessage;

import java.util.function.BiConsumer;

/**
 * AI操作处理器
 *
 * 统一处理来自菜单树、表格等各处的AI操作请求。
 * 负责打开AI助手窗口并发送带上下文的请求。
 *
 * @author Claude
 * @version 1.0
 */
public class AiOperationHandler implements BiConsumer<DesignContext, String> {

    private static final Logger log = LoggerFactory.getLogger(AiOperationHandler.class);

    /** AI聊天窗口（单例复用） */
    private AgentChatStage chatStage;

    /** 主窗口（用于设置owner） */
    private Stage ownerStage;

    /** AI操作状态回调 */
    private Runnable onOperationStart;
    private Runnable onOperationEnd;

    public AiOperationHandler() {
    }

    public AiOperationHandler(Stage ownerStage) {
        this.ownerStage = ownerStage;
    }

    /**
     * 处理AI操作
     *
     * @param context 设计上下文
     * @param operationType 操作类型
     */
    @Override
    public void accept(DesignContext context, String operationType) {
        handleAiOperation(context, operationType);
    }

    /**
     * 处理AI操作请求
     *
     * @param context 设计上下文
     * @param operationType 操作类型
     */
    public void handleAiOperation(DesignContext context, String operationType) {
        if (context == null) {
            log.warn("AI操作被调用但上下文为空");
            showError("无法执行AI操作", "未能获取上下文信息");
            return;
        }

        log.info("处理AI操作: {} - 上下文: {}", operationType, context.getSummary());

        // 通知操作开始
        if (onOperationStart != null) {
            Platform.runLater(onOperationStart);
        }

        Platform.runLater(() -> {
            try {
                // 确保聊天窗口存在
                ensureChatStage();

                // 显示窗口
                if (!chatStage.isShowing()) {
                    chatStage.show();
                }
                chatStage.toFront();
                chatStage.requestFocus();

                // 发送上下文感知消息
                String prompt = buildOperationPrompt(context, operationType);
                chatStage.sendContextAwareMessage(context, prompt, operationType);

                log.info("AI操作已发送: {}", operationType);

            } catch (Exception e) {
                log.error("执行AI操作失败", e);
                showError("AI操作失败", e.getMessage());
            } finally {
                // 通知操作结束
                if (onOperationEnd != null) {
                    Platform.runLater(onOperationEnd);
                }
            }
        });
    }

    /**
     * 确保聊天窗口存在
     */
    private void ensureChatStage() {
        if (chatStage == null || !chatStage.isShowing()) {
            chatStage = new AgentChatStage();
            if (ownerStage != null) {
                chatStage.initOwner(ownerStage);
            }
            chatStage.setTitle("AI 游戏数据助手 - 上下文感知模式");
        }
    }

    /**
     * 构建操作提示词
     *
     * 设计原则：
     * 1. 使用设计师日常会说的话，不用技术术语
     * 2. 问题要具体、可回答
     * 3. 结果要能直接指导设计决策
     */
    private String buildOperationPrompt(DesignContext context, String operationType) {
        String tableName = context.getTableName();
        String dataType = inferDataType(tableName);  // 怪物/技能/物品/任务等
        String rowData = formatRowDataForPrompt(context);

        return switch (operationType) {
            // ==================== 文件级操作 ====================
            case "analyze" -> String.format(
                "我在看 %s 这个配置表，帮我快速了解一下：\n" +
                "1. 这个表是干什么用的？\n" +
                "2. 里面有哪些重要的字段？\n" +
                "3. 它和其他表有什么关联？",
                context.getSummary()
            );

            case "explain" -> String.format(
                "帮我解释一下 %s 里面各个字段的意思，" +
                "特别是那些数字代表什么、有什么取值范围。",
                context.getSummary()
            );

            case "generate" -> String.format(
                "参考 %s 现有的数据规律，帮我生成一条新的配置，" +
                "要符合现有的设计风格。",
                context.getSummary()
            );

            case "check_refs" -> String.format(
                "帮我检查一下 %s 里面引用的ID是不是都存在，" +
                "有没有引用了不存在的数据。",
                context.getSummary()
            );

            // ==================== 行级操作 - 我想了解 ====================
            case "what_is_this" -> String.format(
                "帮我看看这条%s配置：\n%s\n\n" +
                "用通俗的话告诉我：\n" +
                "1. 这是个什么东西？\n" +
                "2. 在游戏里是怎么表现的？\n" +
                "3. 玩家会在什么情况下遇到它？",
                dataType, rowData
            );

            case "explain_numbers" -> String.format(
                "这条%s数据：\n%s\n\n" +
                "帮我解释一下这些数值：\n" +
                "- 每个数字具体代表什么？\n" +
                "- 数值大小对游戏体验有什么影响？\n" +
                "- 有没有什么特殊的取值含义？",
                dataType, rowData
            );

            case "show_relations" -> String.format(
                "这条%s：\n%s\n\n" +
                "帮我理一理它的关联关系：\n" +
                "- 它引用了哪些其他数据？\n" +
                "- 有哪些数据引用了它？\n" +
                "- 修改它会影响到什么？",
                dataType, rowData
            );

            // ==================== 行级操作 - 帮我评估 ====================
            case "check_balance" -> String.format(
                "帮我评估一下这条%s的数值是否合理：\n%s\n\n" +
                "从这几个角度分析：\n" +
                "- 数值是偏高、偏低、还是正常？\n" +
                "- 跟同等级/同类型的比起来如何？\n" +
                "- 有没有明显不合理的地方？",
                dataType, rowData
            );

            case "compare_similar" -> String.format(
                "把这条%s跟同类的对比一下：\n%s\n\n" +
                "告诉我：\n" +
                "- 它在同类中处于什么水平？\n" +
                "- 有什么特点或差异？\n" +
                "- 设计定位是什么？",
                dataType, rowData
            );

            case "predict_experience" -> String.format(
                "从玩家体验的角度，帮我分析这条%s：\n%s\n\n" +
                "玩家遇到它时：\n" +
                "- 会是什么感受？（太难/太简单/刚好）\n" +
                "- 需要什么条件才能应对？\n" +
                "- 奖励和付出匹配吗？",
                dataType, rowData
            );

            // ==================== 行级操作 - 帮我找 ====================
            case "find_similar" -> String.format(
                "帮我找找有没有跟这条%s类似的配置：\n%s\n\n" +
                "找那些：\n" +
                "- 等级/强度差不多的\n" +
                "- 功能/定位类似的\n" +
                "- 可以参考借鉴的",
                dataType, rowData
            );

            case "find_related" -> String.format(
                "帮我找找和这条%s相关的数据：\n%s\n\n" +
                "包括：\n" +
                "- 它用到的资源/道具/技能\n" +
                "- 会产出的奖励/掉落\n" +
                "- 触发条件和前置需求",
                dataType, rowData
            );

            // ==================== 行级操作 - 帮我改进 ====================
            case "suggest_improvements" -> String.format(
                "帮我看看这条%s有什么可以优化的：\n%s\n\n" +
                "给我一些具体建议：\n" +
                "- 数值上可以怎么调整？\n" +
                "- 设计上有什么可以改进的？\n" +
                "- 有没有潜在的问题要注意？",
                dataType, rowData
            );

            case "generate_variant" -> String.format(
                "基于这条%s，帮我设计一个变体版本：\n%s\n\n" +
                "要求：\n" +
                "- 保持核心定位不变\n" +
                "- 数值适当调整（上下浮动10-20%%）\n" +
                "- 可以增加一点特色差异化",
                dataType, rowData
            );

            // ==================== FileStatusPanel 快捷操作 ====================
            case "analyze_structure" -> String.format(
                "帮我分析 %s 表的结构：\n" +
                "1. 表中有哪些字段，各是什么类型？\n" +
                "2. 哪些是关键字段，哪些是可选的？\n" +
                "3. 有没有枚举类型的字段，取值范围是什么？",
                tableName
            );

            case "check_references" -> String.format(
                "帮我检查 %s 表的引用完整性：\n" +
                "1. 哪些字段引用了其他表？\n" +
                "2. 有没有断链的引用（引用了不存在的ID）？\n" +
                "3. 这个表被哪些其他表引用？",
                tableName
            );

            case "generate_sql" -> String.format(
                "我想查询 %s 表的数据，请帮我生成SQL：\n" +
                "（你可以问我想查什么条件的数据）",
                tableName
            );

            case "data_analysis" -> String.format(
                "帮我分析 %s 表的数据特征：\n" +
                "1. 总共有多少条记录？\n" +
                "2. 各个数值字段的范围、平均值是多少？\n" +
                "3. 有没有异常值或可疑数据？",
                tableName
            );

            case "generate_doc" -> String.format(
                "帮我为 %s 表生成字段说明文档：\n" +
                "要求：\n" +
                "- 每个字段用中文解释含义\n" +
                "- 标注必填/可选\n" +
                "- 如果是枚举，列出可能的取值",
                tableName
            );

            case "diagnose" -> String.format(
                "帮我诊断 %s 表是否有配置问题：\n" +
                "1. 有没有重复ID？\n" +
                "2. 有没有空值或异常值？\n" +
                "3. 有没有引用断链？\n" +
                "4. 有没有明显不合理的数值配置？",
                tableName
            );

            default -> String.format(
                "帮我分析一下这条%s数据：\n%s",
                dataType, rowData
            );
        };
    }

    /**
     * 根据表名推断数据类型（用于自然语言描述）
     */
    private String inferDataType(String tableName) {
        if (tableName == null) return "配置";

        String lower = tableName.toLowerCase();

        // 按优先级匹配
        if (lower.contains("npc") || lower.contains("monster") || lower.contains("mob"))
            return "怪物";
        if (lower.contains("skill")) return "技能";
        if (lower.contains("item") || lower.contains("equip") || lower.contains("weapon"))
            return "物品";
        if (lower.contains("quest")) return "任务";
        if (lower.contains("drop") || lower.contains("loot")) return "掉落";
        if (lower.contains("spawn")) return "刷怪点";
        if (lower.contains("instance") || lower.contains("dungeon")) return "副本";
        if (lower.contains("shop") || lower.contains("goods")) return "商店";
        if (lower.contains("recipe") || lower.contains("craft")) return "配方";
        if (lower.contains("buff") || lower.contains("effect")) return "效果";
        if (lower.contains("dialog") || lower.contains("npc_string")) return "对话";

        return "配置";
    }

    /**
     * 格式化行数据用于提示词
     */
    private String formatRowDataForPrompt(DesignContext context) {
        if (context.getRowData() == null || context.getRowData().isEmpty()) {
            return "(无数据)";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("表名: ").append(context.getTableName()).append("\n");

        for (var entry : context.getRowData().entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ")
              .append(entry.getValue()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 显示错误对话框
     */
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // ==================== 简化调用方法 ====================

    /**
     * 简化的AI操作处理（用于FileStatusPanel等只有表名的场景）
     *
     * @param operationType 操作类型
     * @param tableName 表名
     */
    public void handleSimpleOperation(String operationType, String tableName) {
        // 创建一个简单的上下文
        DesignContext context = new DesignContext();
        context.setTableName(tableName);
        context.setCurrentTableName(tableName);

        handleAiOperation(context, operationType);
    }

    /**
     * 创建用于 FileStatusPanel 的回调
     * @return BiConsumer<actionId, tableName>
     */
    public java.util.function.BiConsumer<String, String> createFileStatusCallback() {
        return (actionId, tableName) -> handleSimpleOperation(actionId, tableName);
    }

    // ==================== Getters & Setters ====================

    public void setOwnerStage(Stage ownerStage) {
        this.ownerStage = ownerStage;
    }

    public void setOnOperationStart(Runnable onOperationStart) {
        this.onOperationStart = onOperationStart;
    }

    public void setOnOperationEnd(Runnable onOperationEnd) {
        this.onOperationEnd = onOperationEnd;
    }

    public AgentChatStage getChatStage() {
        return chatStage;
    }

    /**
     * 关闭并清理资源
     */
    public void dispose() {
        if (chatStage != null) {
            chatStage.close();
            chatStage = null;
        }
    }
}
