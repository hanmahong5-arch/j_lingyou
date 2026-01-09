package red.jiuzhou.config.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import red.jiuzhou.ui.error.structured.*;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 配置验证服务 - 启动时和运行时检查配置完整性
 *
 * @author Claude
 * @version 1.0
 */
@Service
public class ConfigValidationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigValidationService.class);

    private static final String CONFIG_FILE = "src/main/resources/application.yml";

    /**
     * 配置分类
     */
    public enum ConfigCategory {
        DATABASE("数据库配置", "db", "🗄️"),
        AI_SERVICE("AI服务配置", "ai", "🤖"),
        FILE_PATH("文件路径配置", "path", "📁"),
        SYSTEM("系统配置", "system", "⚙️");

        private final String displayName;
        private final String id;
        private final String icon;

        ConfigCategory(String displayName, String id, String icon) {
            this.displayName = displayName;
            this.id = id;
            this.icon = icon;
        }

        public String getDisplayName() { return displayName; }
        public String getId() { return id; }
        public String getIcon() { return icon; }
    }

    /**
     * 验证结果
     */
    public record ValidationResult(
        boolean valid,
        String message,
        ErrorLevel level
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult error(String msg) {
            return new ValidationResult(false, msg, ErrorLevel.ERROR);
        }

        public static ValidationResult warning(String msg) {
            return new ValidationResult(false, msg, ErrorLevel.WARNING);
        }
    }

    /**
     * 配置项要求定义
     */
    public record ConfigRequirement(
        String key,                     // 配置键 (如 "ai.qwen.apikey")
        String displayName,             // 显示名称
        boolean required,               // 是否必填
        String description,             // 描述
        String defaultValue,            // 默认值
        ConfigCategory category,        // 分类
        Function<String, ValidationResult> validator  // 验证器
    ) {
        /**
         * 验证配置值
         */
        public ValidationResult validate(String value) {
            if (validator != null) {
                return validator.apply(value);
            }
            if (required && (value == null || value.isBlank())) {
                return ValidationResult.error("必填项不能为空");
            }
            return ValidationResult.ok();
        }
    }

    /**
     * 配置状态
     */
    public record ConfigStatus(
        ConfigRequirement requirement,
        String currentValue,
        ValidationResult validationResult
    ) {
        public boolean isValid() {
            return validationResult.valid();
        }

        public String getStatusIcon() {
            if (validationResult.valid()) {
                return "✅";
            }
            if (!requirement.required()) {
                return "⚠️";
            }
            return "❌";
        }
    }

    // ==================== 预定义的配置项 ====================

    private static final List<ConfigRequirement> REQUIREMENTS = List.of(
        // 数据库配置 (必填)
        new ConfigRequirement(
            "spring.datasource.url", "数据库连接URL", true,
            "数据库连接地址，支持 PostgreSQL (jdbc:postgresql://) 或 MySQL (jdbc:mysql://)",
            null, ConfigCategory.DATABASE,
            value -> {
                if (value == null || value.isBlank()) {
                    return ValidationResult.error("数据库URL不能为空");
                }
                if (!value.startsWith("jdbc:postgresql://") && !value.startsWith("jdbc:mysql://")) {
                    return ValidationResult.error("必须是有效的 PostgreSQL 或 MySQL JDBC URL");
                }
                return ValidationResult.ok();
            }
        ),
        new ConfigRequirement(
            "spring.datasource.username", "数据库用户名", true,
            "数据库登录用户名",
            "root", ConfigCategory.DATABASE,
            value -> (value != null && !value.isBlank())
                ? ValidationResult.ok()
                : ValidationResult.error("用户名不能为空")
        ),
        new ConfigRequirement(
            "spring.datasource.password", "数据库密码", true,
            "数据库登录密码",
            null, ConfigCategory.DATABASE,
            value -> {
                if (value == null || value.isBlank()) {
                    return ValidationResult.error("密码不能为空");
                }
                if (value.equals("your-password") || value.contains("your_password")) {
                    return ValidationResult.warning("请设置真实的数据库密码");
                }
                return ValidationResult.ok();
            }
        ),

        // AI配置 (可选，但会影响功能)
        new ConfigRequirement(
            "ai.qwen.apikey", "通义千问API密钥", false,
            "阿里云通义千问服务的API密钥，用于AI对话和数据分析功能",
            null, ConfigCategory.AI_SERVICE,
            value -> {
                if (value == null || value.isBlank()) {
                    return ValidationResult.warning("未配置API密钥，AI功能不可用");
                }
                if (value.contains("your-") || value.contains("${")) {
                    return ValidationResult.warning("请配置真实的API密钥");
                }
                return ValidationResult.ok();
            }
        ),
        new ConfigRequirement(
            "ai.qwen.model", "通义千问模型", false,
            "使用的模型名称，如 qwen-plus, qwen-max",
            "qwen-plus", ConfigCategory.AI_SERVICE,
            value -> {
                if (value == null || value.isBlank()) {
                    return ValidationResult.warning("未配置模型名称");
                }
                return ValidationResult.ok();
            }
        ),

        // 路径配置 (可选)
        new ConfigRequirement(
            "aion.xmlPath", "Aion XML数据路径", false,
            "Aion游戏XML数据文件目录，用于机制浏览和数据分析",
            null, ConfigCategory.FILE_PATH,
            value -> {
                if (value == null || value.isBlank()) {
                    return ValidationResult.warning("未配置路径，机制浏览功能不可用");
                }
                File dir = new File(value);
                if (!dir.exists()) {
                    return ValidationResult.warning("路径不存在: " + value);
                }
                if (!dir.isDirectory()) {
                    return ValidationResult.error("路径不是目录: " + value);
                }
                return ValidationResult.ok();
            }
        ),
        new ConfigRequirement(
            "aion.localizedPath", "Aion本地化路径", false,
            "本地化XML文件目录（如中文版数据）",
            null, ConfigCategory.FILE_PATH,
            value -> {
                if (value == null || value.isBlank()) {
                    return ValidationResult.warning("未配置本地化路径");
                }
                File dir = new File(value);
                if (!dir.exists()) {
                    return ValidationResult.warning("路径不存在: " + value);
                }
                return ValidationResult.ok();
            }
        )
    );

    // ==================== 公共方法 ====================

    /**
     * 执行完整配置验证
     * @return 验证错误列表
     */
    public List<StructuredError> validateAll() {
        List<StructuredError> errors = new ArrayList<>();

        // 首先检查配置文件是否存在
        Path configPath = Path.of(CONFIG_FILE);
        if (!Files.exists(configPath)) {
            errors.add(StructuredError.fromCode(ErrorCodes.CFG_FILE_NOT_FOUND)
                .location(CONFIG_FILE, 1)
                .hint("请复制 application.yml.example 为 application.yml")
                .addSuggestion(FixSuggestion.manual(
                    "复制配置模板",
                    "运行: copy src/main/resources/application.yml.example src/main/resources/application.yml"))
                .build());
            return errors;
        }

        // 验证每个配置项
        for (ConfigRequirement req : REQUIREMENTS) {
            String value = getConfigValue(req.key());
            ValidationResult result = req.validate(value);

            if (!result.valid()) {
                errors.add(createConfigError(req, result, value));
            }
        }

        log.info("配置验证完成: {} 个错误, {} 个警告",
            errors.stream().filter(e -> e.level() == ErrorLevel.ERROR).count(),
            errors.stream().filter(e -> e.level() == ErrorLevel.WARNING).count());

        return errors;
    }

    /**
     * 检查关键配置是否满足启动要求
     * @return true 如果可以启动
     */
    public boolean canStartApplication() {
        return REQUIREMENTS.stream()
            .filter(ConfigRequirement::required)
            .allMatch(req -> {
                String value = getConfigValue(req.key());
                return req.validate(value).valid();
            });
    }

    /**
     * 获取缺失的必填配置
     */
    public List<ConfigRequirement> getMissingRequiredConfigs() {
        return REQUIREMENTS.stream()
            .filter(ConfigRequirement::required)
            .filter(req -> {
                String value = getConfigValue(req.key());
                return !req.validate(value).valid();
            })
            .toList();
    }

    /**
     * 获取所有配置项的状态
     */
    public List<ConfigStatus> getAllConfigStatus() {
        return REQUIREMENTS.stream()
            .map(req -> {
                String value = getConfigValue(req.key());
                ValidationResult result = req.validate(value);
                return new ConfigStatus(req, maskSensitive(req.key(), value), result);
            })
            .toList();
    }

    /**
     * 获取指定分类的配置状态
     */
    public List<ConfigStatus> getConfigStatusByCategory(ConfigCategory category) {
        return getAllConfigStatus().stream()
            .filter(s -> s.requirement().category() == category)
            .toList();
    }

    /**
     * 获取所有配置要求
     */
    public List<ConfigRequirement> getAllRequirements() {
        return REQUIREMENTS;
    }

    /**
     * 检查是否有任何错误级别的问题
     */
    public boolean hasErrors() {
        return REQUIREMENTS.stream()
            .filter(ConfigRequirement::required)
            .anyMatch(req -> {
                String value = getConfigValue(req.key());
                ValidationResult result = req.validate(value);
                return !result.valid() && result.level() == ErrorLevel.ERROR;
            });
    }

    /**
     * 检查是否有警告级别的问题
     */
    public boolean hasWarnings() {
        return REQUIREMENTS.stream()
            .anyMatch(req -> {
                String value = getConfigValue(req.key());
                ValidationResult result = req.validate(value);
                return !result.valid() && result.level() == ErrorLevel.WARNING;
            });
    }

    // ==================== 私有方法 ====================

    /**
     * 获取配置值
     */
    private String getConfigValue(String key) {
        try {
            return YamlUtils.getProperty(key);
        } catch (Exception e) {
            log.debug("读取配置失败: {}", key);
            return null;
        }
    }

    /**
     * 创建配置错误
     */
    private StructuredError createConfigError(ConfigRequirement req,
                                               ValidationResult result,
                                               String currentValue) {
        ErrorCodes code = req.required() && result.level() == ErrorLevel.ERROR
            ? ErrorCodes.CFG_MISSING_REQUIRED
            : ErrorCodes.CFG_AI_NOT_CONFIGURED;

        ErrorLocation location = ErrorLocation.fromConfigKey(CONFIG_FILE, req.key());

        StructuredError.Builder builder = StructuredError.builder()
            .errorCode(code.getCode())
            .level(result.level())
            .category(ErrorCategory.CONFIGURATION)
            .title(req.displayName() + " - " + (req.required() ? "必填" : "可选"))
            .message(result.message())
            .location(location)
            .hint(req.description())
            .addContext("config_key", req.key())
            .addContext("current_value", maskSensitive(req.key(), currentValue))
            .addContext("category", req.category().getDisplayName())
            .component("ConfigValidationService");

        // 添加修复建议
        builder.addSuggestion(FixSuggestion.navigateToKey(req.key()));

        if (req.defaultValue() != null) {
            builder.addSuggestion(FixSuggestion.useDefaultValue(req.key(), req.defaultValue()));
        }

        // AI配置特殊提示
        if (req.key().contains("ai.") && req.key().contains("apikey")) {
            builder.addSuggestion(FixSuggestion.viewDocumentation(
                "获取API密钥",
                "https://dashscope.console.aliyun.com/"));
        }

        return builder.build();
    }

    /**
     * 遮蔽敏感信息
     */
    private String maskSensitive(String key, String value) {
        if (value == null) return null;

        String lowerKey = key.toLowerCase();
        if (lowerKey.contains("password") || lowerKey.contains("secret") ||
            lowerKey.contains("apikey") || lowerKey.contains("api_key")) {
            if (value.length() <= 4) {
                return "****";
            }
            return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
        }
        return value;
    }
}
