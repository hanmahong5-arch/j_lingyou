package red.jiuzhou.ui.error.structured;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 结构化错误信息 - 世界级错误诊断的核心数据结构
 *
 * <p>设计参考:
 * <ul>
 *   <li>Rust Compiler Diagnostics (错误码、建议、示例)</li>
 *   <li>TypeScript Language Server (位置标注、相关信息)</li>
 *   <li>IntelliJ IDEA (一键修复、错误导航)</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public record StructuredError(
    // ========== 错误标识 ==========
    String errorId,           // 唯一错误ID (UUID)
    String errorCode,         // 错误码 (如 "CFG-0001")
    ErrorLevel level,         // 严重级别
    ErrorCategory category,   // 错误类别

    // ========== 错误位置 ==========
    ErrorLocation location,   // 主要位置
    List<ErrorLocation> relatedLocations, // 相关位置

    // ========== 错误描述 ==========
    String title,             // 简短标题
    String message,           // 详细描述
    String hint,              // 提示信息 (Rust风格的 "help: ...")

    // ========== 上下文信息 ==========
    Map<String, Object> context,  // 上下文数据
    String stackTrace,            // 堆栈跟踪
    Throwable cause,              // 原始异常

    // ========== 修复建议 ==========
    List<FixSuggestion> suggestions,  // 建议修复方案
    String documentationUrl,          // 文档链接

    // ========== 元数据 ==========
    Instant timestamp,        // 发生时间
    String component          // 来源组件
) {

    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /**
     * 生成 Rust 风格的人类可读错误输出
     */
    public String toHumanReadable() {
        StringBuilder sb = new StringBuilder();

        // 错误头: error[CFG-0001]: 标题
        sb.append(level.getLowerName())
          .append("[").append(errorCode).append("]: ")
          .append(title).append("\n");

        // 位置信息
        if (location != null && location.isNavigable()) {
            sb.append("  --> ").append(location.toClickableString()).append("\n");

            // 源码上下文
            if (location.lineContent() != null) {
                String lineNum = String.format("%4d", location.line());
                sb.append("   |\n");
                sb.append(lineNum).append(" | ").append(location.lineContent()).append("\n");
                sb.append("   | ");

                // 下划线标记
                sb.append(location.generateUnderline(message)).append("\n");
                sb.append("   |\n");
            }
        } else if (message != null) {
            sb.append("  = ").append(message).append("\n");
        }

        // 提示信息
        if (hint != null && !hint.isEmpty()) {
            sb.append("  = help: ").append(hint).append("\n");
        }

        // 修复建议
        if (suggestions != null && !suggestions.isEmpty()) {
            for (FixSuggestion suggestion : suggestions) {
                if (suggestion.type() == FixSuggestion.FixType.QUICK_FIX ||
                    suggestion.type() == FixSuggestion.FixType.NAVIGATE) {
                    sb.append("  = suggestion: ").append(suggestion.title()).append("\n");
                }
            }
        }

        // 文档链接
        if (documentationUrl != null) {
            sb.append("\n更多信息请参阅: ").append(documentationUrl).append("\n");
        }

        return sb.toString();
    }

    /**
     * 生成 AI 友好的 JSON 格式
     */
    public String toJson() {
        Map<String, Object> json = new LinkedHashMap<>();

        json.put("error_id", errorId);
        json.put("error_code", errorCode);
        json.put("level", level != null ? level.name() : "UNKNOWN");
        json.put("category", category != null ? category.name() : "UNKNOWN");
        json.put("title", title);
        json.put("message", message);
        json.put("hint", hint);

        if (location != null) {
            Map<String, Object> loc = new LinkedHashMap<>();
            loc.put("file", location.filePath());
            loc.put("file_name", location.fileName());
            loc.put("line", location.line());
            loc.put("column", location.column());
            loc.put("end_line", location.endLine());
            loc.put("end_column", location.endColumn());
            if (location.lineContent() != null) {
                loc.put("line_content", location.lineContent());
            }
            json.put("location", loc);
        }

        if (context != null && !context.isEmpty()) {
            json.put("context", context);
        }

        json.put("timestamp", timestamp != null ? timestamp.toString() : null);
        json.put("component", component);

        if (suggestions != null && !suggestions.isEmpty()) {
            json.put("suggestions", suggestions.stream()
                .map(s -> Map.of(
                    "id", s.id(),
                    "title", s.title(),
                    "type", s.type().name(),
                    "auto_applicable", s.autoApplicable()
                ))
                .toList());
        }

        json.put("documentation_url", documentationUrl);

        return JSON.toJSONString(json, JSONWriter.Feature.PrettyFormat);
    }

    /**
     * 生成简短的摘要 (用于列表显示)
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(level.getIcon()).append(" ");
        sb.append("[").append(errorCode).append("] ");
        sb.append(title);

        if (location != null && location.isNavigable()) {
            sb.append(" @ ").append(location.toClickableString());
        }

        return sb.toString();
    }

    /**
     * 格式化时间戳
     */
    public String getFormattedTimestamp() {
        return timestamp != null ? TIME_FORMAT.format(timestamp) : "";
    }

    // ==================== Builder ====================

    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 从 ErrorCodes 枚举创建
     */
    public static Builder fromCode(ErrorCodes code) {
        return builder()
            .errorCode(code.getCode())
            .level(code.getLevel())
            .category(code.getCategory())
            .title(code.getTitle())
            .message(code.getDescription())
            .documentationUrl(code.getDocUrl());
    }

    /**
     * 从异常创建
     */
    public static Builder fromException(Throwable t, String component) {
        ErrorCodes code = ErrorCodes.fromException(t);
        ErrorCategory category = ErrorCategory.fromException(t);

        return builder()
            .errorCode(code.getCode())
            .level(code.getLevel())
            .category(category)
            .title(code.getTitle())
            .message(t.getMessage())
            .cause(t)
            .stackTrace(getStackTraceString(t))
            .component(component)
            .documentationUrl(code.getDocUrl());
    }

    private static String getStackTraceString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * 构建器
     */
    public static class Builder {
        private String errorId = UUID.randomUUID().toString();
        private String errorCode;
        private ErrorLevel level = ErrorLevel.ERROR;
        private ErrorCategory category = ErrorCategory.UNKNOWN;
        private ErrorLocation location;
        private List<ErrorLocation> relatedLocations = new ArrayList<>();
        private String title;
        private String message;
        private String hint;
        private Map<String, Object> context = new HashMap<>();
        private String stackTrace;
        private Throwable cause;
        private List<FixSuggestion> suggestions = new ArrayList<>();
        private String documentationUrl;
        private Instant timestamp = Instant.now();
        private String component;

        public Builder errorId(String errorId) {
            this.errorId = errorId;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder level(ErrorLevel level) {
            this.level = level;
            return this;
        }

        public Builder category(ErrorCategory category) {
            this.category = category;
            return this;
        }

        public Builder location(ErrorLocation location) {
            this.location = location;
            return this;
        }

        public Builder location(String filePath, int line) {
            this.location = ErrorLocation.line(filePath, line);
            return this;
        }

        public Builder location(String filePath, int line, int column, int endColumn) {
            this.location = ErrorLocation.precise(filePath, line, column, endColumn);
            return this;
        }

        public Builder addRelatedLocation(ErrorLocation location) {
            this.relatedLocations.add(location);
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder hint(String hint) {
            this.hint = hint;
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public Builder addContext(String key, Object value) {
            this.context.put(key, value);
            return this;
        }

        public Builder stackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public Builder suggestions(List<FixSuggestion> suggestions) {
            this.suggestions = suggestions;
            return this;
        }

        public Builder addSuggestion(FixSuggestion suggestion) {
            this.suggestions.add(suggestion);
            return this;
        }

        public Builder documentationUrl(String url) {
            this.documentationUrl = url;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder component(String component) {
            this.component = component;
            return this;
        }

        public StructuredError build() {
            return new StructuredError(
                errorId, errorCode, level, category,
                location, relatedLocations,
                title, message, hint,
                context, stackTrace, cause,
                suggestions, documentationUrl,
                timestamp, component
            );
        }
    }
}
