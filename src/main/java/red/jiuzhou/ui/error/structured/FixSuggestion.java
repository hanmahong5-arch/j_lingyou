package red.jiuzhou.ui.error.structured;

import java.util.function.Supplier;

/**
 * 修复建议 - 参考 IntelliJ IDEA Quick Fix
 *
 * @author Claude
 * @version 1.0
 */
public record FixSuggestion(
    String id,               // 建议ID
    String title,            // 显示标题
    String description,      // 详细描述
    FixType type,            // 修复类型
    Supplier<Boolean> action, // 修复动作
    int priority,            // 优先级 (1最高)
    boolean autoApplicable   // 是否可自动应用
) {

    /**
     * 修复类型
     */
    public enum FixType {
        /** 一键修复 */
        QUICK_FIX("快速修复", "🔧"),

        /** 建议操作 */
        SUGGESTION("建议", "💡"),

        /** 需手动操作 */
        MANUAL("手动操作", "✋"),

        /** 查看文档 */
        DOCUMENTATION("查看文档", "📚"),

        /** 导航到位置 */
        NAVIGATE("导航", "🔗");

        private final String displayName;
        private final String icon;

        FixType(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIcon() {
            return icon;
        }
    }

    /**
     * 执行修复
     * @return true 表示修复成功
     */
    public boolean apply() {
        if (action != null) {
            return action.get();
        }
        return false;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建导航到配置文件的建议
     */
    public static FixSuggestion navigateToConfig(String configPath, int line) {
        return new FixSuggestion(
            "nav-config-" + System.currentTimeMillis(),
            "打开配置文件",
            "在配置编辑器中打开并跳转到第 " + line + " 行",
            FixType.NAVIGATE,
            () -> {
                // 这里的具体实现由调用方提供
                return true;
            },
            1,
            true
        );
    }

    /**
     * 创建导航到配置键的建议
     */
    public static FixSuggestion navigateToKey(String configKey) {
        return new FixSuggestion(
            "nav-key-" + configKey.replace(".", "-"),
            "编辑配置项: " + configKey,
            "在配置编辑器中定位到 " + configKey,
            FixType.NAVIGATE,
            null,
            1,
            true
        );
    }

    /**
     * 创建使用默认值的建议
     */
    public static FixSuggestion useDefaultValue(String key, String defaultValue) {
        return new FixSuggestion(
            "default-" + key.replace(".", "-"),
            "使用默认值: " + truncate(defaultValue, 30),
            "将配置项 " + key + " 设置为 " + defaultValue,
            FixType.QUICK_FIX,
            null,
            2,
            true
        );
    }

    /**
     * 创建查看文档的建议
     */
    public static FixSuggestion viewDocumentation(String title, String url) {
        return new FixSuggestion(
            "doc-" + System.currentTimeMillis(),
            title,
            "打开文档: " + url,
            FixType.DOCUMENTATION,
            () -> {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            },
            5,
            true
        );
    }

    /**
     * 创建手动操作建议
     */
    public static FixSuggestion manual(String title, String description) {
        return new FixSuggestion(
            "manual-" + System.currentTimeMillis(),
            title,
            description,
            FixType.MANUAL,
            null,
            10,
            false
        );
    }

    /**
     * 创建自定义快速修复
     */
    public static FixSuggestion quickFix(String id, String title, String description,
                                          Supplier<Boolean> action) {
        return new FixSuggestion(id, title, description, FixType.QUICK_FIX, action, 1, true);
    }

    // ==================== 辅助方法 ====================

    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }

    /**
     * 格式化为显示字符串
     */
    public String toDisplayString() {
        return type.getIcon() + " " + title;
    }
}
