package red.jiuzhou.config;

import java.io.File;
import java.time.LocalDateTime;

/**
 * 配置文件条目
 *
 * <p>表示一个可管理的配置文件。
 *
 * @author Claude
 * @version 1.0
 */
public class ConfigFileEntry {

    /** 配置文件类型 */
    public enum ConfigType {
        YAML("YAML配置", "yml", "yaml"),
        JSON("JSON配置", "json"),
        PROPERTIES("Properties配置", "properties"),
        ENV("环境变量", "env");

        private final String displayName;
        private final String[] extensions;

        ConfigType(String displayName, String... extensions) {
            this.displayName = displayName;
            this.extensions = extensions;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String[] getExtensions() {
            return extensions;
        }

        public static ConfigType fromFileName(String fileName) {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
                return YAML;
            } else if (lower.endsWith(".json")) {
                return JSON;
            } else if (lower.endsWith(".properties")) {
                return PROPERTIES;
            } else if (lower.endsWith(".env") || lower.contains(".env")) {
                return ENV;
            }
            return null;
        }
    }

    /** 配置分类 */
    public enum ConfigCategory {
        CORE("核心配置", "应用运行必需的核心配置"),
        DATABASE("数据库配置", "数据库连接和设置"),
        AI("AI服务配置", "AI模型和API配置"),
        PATH("路径配置", "文件和目录路径设置"),
        MECHANISM("机制配置", "游戏机制相关配置"),
        MENU("菜单配置", "界面菜单和布局"),
        OTHER("其他配置", "其他杂项配置");

        private final String displayName;
        private final String description;

        ConfigCategory(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    // 基本信息
    private String id;
    private String name;
    private String displayName;
    private String description;
    private File file;
    private ConfigType type;
    private ConfigCategory category;

    // 状态信息
    private boolean modified;
    private boolean sensitive;
    private LocalDateTime lastModified;
    private String content;

    // 构造函数
    public ConfigFileEntry() {
    }

    public ConfigFileEntry(String id, String name, File file, ConfigType type, ConfigCategory category) {
        this.id = id;
        this.name = name;
        this.displayName = name;
        this.file = file;
        this.type = type;
        this.category = category;
    }

    // Builder风格设置
    public ConfigFileEntry displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public ConfigFileEntry description(String description) {
        this.description = description;
        return this;
    }

    public ConfigFileEntry sensitive(boolean sensitive) {
        this.sensitive = sensitive;
        return this;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public ConfigType getType() {
        return type;
    }

    public void setType(ConfigType type) {
        this.type = type;
    }

    public ConfigCategory getCategory() {
        return category;
    }

    public void setCategory(ConfigCategory category) {
        this.category = category;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean exists() {
        return file != null && file.exists();
    }

    @Override
    public String toString() {
        return displayName != null ? displayName : name;
    }
}
