package red.jiuzhou.ui.error.structured;

/**
 * 错误类别
 *
 * @author Claude
 * @version 1.0
 */
public enum ErrorCategory {

    /** 配置错误 */
    CONFIGURATION("配置错误", "CFG"),

    /** 数据库错误 */
    DATABASE("数据库错误", "DB"),

    /** AI服务错误 */
    AI_SERVICE("AI服务错误", "AI"),

    /** 文件IO错误 */
    FILE_IO("文件IO错误", "IO"),

    /** 网络错误 */
    NETWORK("网络错误", "NET"),

    /** 系统错误 */
    SYSTEM("系统错误", "SYS"),

    /** 验证错误 */
    VALIDATION("验证错误", "VAL"),

    /** 未知错误 */
    UNKNOWN("未知错误", "UNK");

    private final String displayName;
    private final String prefix;

    ErrorCategory(String displayName, String prefix) {
        this.displayName = displayName;
        this.prefix = prefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * 根据异常类型推断类别
     */
    public static ErrorCategory fromException(Throwable t) {
        if (t == null) return UNKNOWN;

        String className = t.getClass().getName().toLowerCase();
        String message = t.getMessage() != null ? t.getMessage().toLowerCase() : "";

        // 数据库相关
        if (className.contains("sql") || className.contains("jdbc") ||
            className.contains("hikari") || className.contains("mysql")) {
            return DATABASE;
        }

        // 配置相关
        if (className.contains("config") || className.contains("properties") ||
            message.contains("配置") || message.contains("config")) {
            return CONFIGURATION;
        }

        // AI相关
        if (className.contains("ai") || className.contains("dashscope") ||
            message.contains("api key") || message.contains("apikey")) {
            return AI_SERVICE;
        }

        // 文件IO相关
        if (className.contains("io") || className.contains("file") ||
            className.contains("path") || className.contains("stream")) {
            return FILE_IO;
        }

        // 网络相关
        if (className.contains("socket") || className.contains("http") ||
            className.contains("connect") || className.contains("timeout")) {
            return NETWORK;
        }

        return UNKNOWN;
    }
}
