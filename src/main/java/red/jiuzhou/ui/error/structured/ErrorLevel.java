package red.jiuzhou.ui.error.structured;

/**
 * 错误严重级别
 *
 * @author Claude
 * @version 1.0
 */
public enum ErrorLevel {

    /** 致命错误 - 应用无法继续运行 */
    FATAL("致命", 4, "#b71c1c"),

    /** 错误 - 功能无法正常使用 */
    ERROR("错误", 3, "#f44336"),

    /** 警告 - 功能可能受影响 */
    WARNING("警告", 2, "#ff9800"),

    /** 信息 - 仅供参考 */
    INFO("信息", 1, "#2196f3");

    private final String displayName;
    private final int severity;
    private final String color;

    ErrorLevel(String displayName, int severity, String color) {
        this.displayName = displayName;
        this.severity = severity;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSeverity() {
        return severity;
    }

    public String getColor() {
        return color;
    }

    /**
     * 获取图标
     */
    public String getIcon() {
        return switch (this) {
            case FATAL -> "💀";
            case ERROR -> "❌";
            case WARNING -> "⚠️";
            case INFO -> "ℹ️";
        };
    }

    /**
     * 获取小写名称 (用于 Rust 风格输出)
     */
    public String getLowerName() {
        return switch (this) {
            case FATAL -> "fatal";
            case ERROR -> "error";
            case WARNING -> "warning";
            case INFO -> "info";
        };
    }
}
