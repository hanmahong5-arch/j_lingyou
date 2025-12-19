package red.jiuzhou.agent.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI响应解析器
 *
 * 解析AI返回的文本，提取工具调用和普通消息
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class ResponseParser {

    private static final Logger log = LoggerFactory.getLogger(ResponseParser.class);

    /** JSON代码块正则表达式 */
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile(
        "```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```",
        Pattern.MULTILINE
    );

    /** 内联JSON正则表达式 */
    private static final Pattern INLINE_JSON_PATTERN = Pattern.compile(
        "\\{\\s*\"tool\"\\s*:\\s*\"[^\"]+\"[^}]*\\}",
        Pattern.DOTALL
    );

    /**
     * 解析结果
     */
    public static class ParsedResponse {
        /** 原始响应文本 */
        private String rawResponse;

        /** 纯文本消息（去除工具调用后的部分） */
        private String textMessage;

        /** 工具调用列表 */
        private List<ToolCallRequest> toolCalls;

        /** 是否包含工具调用 */
        private boolean hasToolCall;

        public ParsedResponse() {
            this.toolCalls = new ArrayList<>();
        }

        // Getters and Setters
        public String getRawResponse() { return rawResponse; }
        public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }
        public String getTextMessage() { return textMessage; }
        public void setTextMessage(String textMessage) { this.textMessage = textMessage; }
        public List<ToolCallRequest> getToolCalls() { return toolCalls; }
        public void setToolCalls(List<ToolCallRequest> toolCalls) { this.toolCalls = toolCalls; }
        public boolean hasToolCall() { return hasToolCall; }
        public void setHasToolCall(boolean hasToolCall) { this.hasToolCall = hasToolCall; }

        public void addToolCall(ToolCallRequest call) {
            this.toolCalls.add(call);
            this.hasToolCall = true;
        }
    }

    /**
     * 工具调用请求
     */
    public static class ToolCallRequest {
        private String toolName;
        private JSONObject parameters;
        private String rawJson;

        public ToolCallRequest(String toolName, JSONObject parameters) {
            this.toolName = toolName;
            this.parameters = parameters;
        }

        // Getters and Setters
        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }
        public JSONObject getParameters() { return parameters; }
        public void setParameters(JSONObject parameters) { this.parameters = parameters; }
        public String getRawJson() { return rawJson; }
        public void setRawJson(String rawJson) { this.rawJson = rawJson; }

        /**
         * 获取字符串参数
         */
        public String getString(String key) {
            return parameters != null ? parameters.getString(key) : null;
        }

        /**
         * 获取整数参数
         */
        public Integer getInt(String key) {
            return parameters != null ? parameters.getInteger(key) : null;
        }

        /**
         * 获取布尔参数
         */
        public Boolean getBoolean(String key) {
            return parameters != null ? parameters.getBoolean(key) : null;
        }

        @Override
        public String toString() {
            return String.format("ToolCallRequest{tool='%s', params=%s}", toolName, parameters);
        }
    }

    /**
     * 解析AI响应
     *
     * @param response AI响应文本
     * @return 解析结果
     */
    public ParsedResponse parse(String response) {
        ParsedResponse result = new ParsedResponse();
        result.setRawResponse(response);

        if (response == null || response.trim().isEmpty()) {
            result.setTextMessage("");
            return result;
        }

        StringBuilder textBuilder = new StringBuilder();
        String remaining = response;

        // 1. 首先查找代码块中的JSON
        Matcher blockMatcher = JSON_BLOCK_PATTERN.matcher(remaining);
        List<int[]> foundRanges = new ArrayList<>();

        while (blockMatcher.find()) {
            String jsonContent = blockMatcher.group(1).trim();
            foundRanges.add(new int[]{blockMatcher.start(), blockMatcher.end()});

            ToolCallRequest toolCall = tryParseToolCall(jsonContent);
            if (toolCall != null) {
                toolCall.setRawJson(blockMatcher.group(0));
                result.addToolCall(toolCall);
            }
        }

        // 2. 查找内联JSON（不在代码块中的）
        Matcher inlineMatcher = INLINE_JSON_PATTERN.matcher(remaining);
        while (inlineMatcher.find()) {
            // 检查是否已在代码块范围内
            int start = inlineMatcher.start();
            boolean inBlock = false;
            for (int[] range : foundRanges) {
                if (start >= range[0] && start < range[1]) {
                    inBlock = true;
                    break;
                }
            }

            if (!inBlock) {
                String jsonContent = inlineMatcher.group().trim();
                ToolCallRequest toolCall = tryParseToolCall(jsonContent);
                if (toolCall != null) {
                    toolCall.setRawJson(inlineMatcher.group());
                    result.addToolCall(toolCall);
                    foundRanges.add(new int[]{inlineMatcher.start(), inlineMatcher.end()});
                }
            }
        }

        // 3. 提取纯文本部分（去除工具调用JSON）
        if (!foundRanges.isEmpty()) {
            // 按位置排序
            foundRanges.sort((a, b) -> a[0] - b[0]);

            int lastEnd = 0;
            for (int[] range : foundRanges) {
                if (range[0] > lastEnd) {
                    textBuilder.append(remaining.substring(lastEnd, range[0]));
                }
                lastEnd = range[1];
            }
            if (lastEnd < remaining.length()) {
                textBuilder.append(remaining.substring(lastEnd));
            }

            result.setTextMessage(cleanTextMessage(textBuilder.toString()));
        } else {
            result.setTextMessage(response.trim());
        }

        return result;
    }

    /**
     * 尝试解析工具调用JSON
     */
    private ToolCallRequest tryParseToolCall(String json) {
        try {
            JSONObject obj = JSON.parseObject(json);

            // 检查是否是工具调用格式
            if (obj.containsKey("tool")) {
                String toolName = obj.getString("tool");
                JSONObject parameters = obj.getJSONObject("parameters");

                if (toolName != null && !toolName.isEmpty()) {
                    return new ToolCallRequest(toolName, parameters != null ? parameters : new JSONObject());
                }
            }

        } catch (JSONException e) {
            log.debug("JSON解析失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 清理文本消息
     */
    private String cleanTextMessage(String text) {
        return text
            .replaceAll("\\n{3,}", "\n\n")  // 多个空行替换为两个
            .trim();
    }

    /**
     * 从文本中提取SQL语句
     *
     * @param text 文本
     * @return SQL语句列表
     */
    public List<String> extractSqlStatements(String text) {
        List<String> sqls = new ArrayList<>();

        // 查找SQL代码块
        Pattern sqlBlockPattern = Pattern.compile(
            "```(?:sql)?\\s*\\n?([\\s\\S]*?)\\n?```",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = sqlBlockPattern.matcher(text);
        while (matcher.find()) {
            String sql = matcher.group(1).trim();
            if (!sql.isEmpty()) {
                sqls.add(sql);
            }
        }

        // 如果没有找到代码块，尝试查找裸SQL语句
        if (sqls.isEmpty()) {
            Pattern sqlPattern = Pattern.compile(
                "(SELECT|UPDATE|INSERT|DELETE)\\s+[^;]+;?",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );

            matcher = sqlPattern.matcher(text);
            while (matcher.find()) {
                String sql = matcher.group().trim();
                // 移除末尾的分号
                if (sql.endsWith(";")) {
                    sql = sql.substring(0, sql.length() - 1).trim();
                }
                if (!sql.isEmpty()) {
                    sqls.add(sql);
                }
            }
        }

        return sqls;
    }

    /**
     * 检测用户意图
     *
     * @param userMessage 用户消息
     * @return 意图类型
     */
    public UserIntent detectIntent(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return UserIntent.UNKNOWN;
        }

        String lower = userMessage.toLowerCase();

        // 确认/取消
        if (lower.equals("确认") || lower.equals("yes") || lower.equals("是") ||
            lower.equals("执行") || lower.equals("ok")) {
            return UserIntent.CONFIRM;
        }

        if (lower.equals("取消") || lower.equals("no") || lower.equals("否") ||
            lower.equals("算了") || lower.equals("cancel")) {
            return UserIntent.CANCEL;
        }

        // 回滚
        if (lower.contains("回滚") || lower.contains("撤销") || lower.contains("恢复")) {
            return UserIntent.ROLLBACK;
        }

        // 历史
        if (lower.contains("历史") || lower.contains("记录") || lower.contains("操作日志")) {
            return UserIntent.HISTORY;
        }

        // 查询类关键词
        if (lower.contains("查询") || lower.contains("查看") || lower.contains("显示") ||
            lower.contains("找") || lower.contains("搜索") || lower.contains("列出") ||
            lower.contains("有哪些") || lower.contains("多少")) {
            return UserIntent.QUERY;
        }

        // 修改类关键词
        if (lower.contains("修改") || lower.contains("更新") || lower.contains("改成") ||
            lower.contains("设置") || lower.contains("调整") || lower.contains("提高") ||
            lower.contains("降低") || lower.contains("增加") || lower.contains("减少")) {
            return UserIntent.MODIFY;
        }

        // 删除类关键词
        if (lower.contains("删除") || lower.contains("移除") || lower.contains("清除")) {
            return UserIntent.DELETE;
        }

        // 分析类关键词
        if (lower.contains("分析") || lower.contains("统计") || lower.contains("分布") ||
            lower.contains("平衡") || lower.contains("对比")) {
            return UserIntent.ANALYZE;
        }

        return UserIntent.UNKNOWN;
    }

    /**
     * 用户意图枚举
     */
    public enum UserIntent {
        QUERY,      // 查询
        MODIFY,     // 修改
        DELETE,     // 删除
        ANALYZE,    // 分析
        HISTORY,    // 查看历史
        ROLLBACK,   // 回滚
        CONFIRM,    // 确认操作
        CANCEL,     // 取消操作
        UNKNOWN     // 未知
    }
}
