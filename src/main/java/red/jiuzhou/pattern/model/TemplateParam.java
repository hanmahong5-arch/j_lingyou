package red.jiuzhou.pattern.model;

import java.sql.Timestamp;

/**
 * 模板参数实体
 * 对应 template_param 表，存储模板的占位符定义
 */
public class TemplateParam {
    private Integer id;
    private Integer templateId;

    // 参数基本信息
    private String paramName;          // 显示名
    private String paramCode;          // 占位符标识
    private ParamType paramType;

    // 参数约束
    private Boolean isRequired;
    private String defaultValue;
    private String minValue;
    private String maxValue;
    private String enumValues;         // JSON数组

    // 值生成器
    private GeneratorType generatorType;
    private String generatorConfig;    // JSON对象

    // 显示配置
    private Integer displayOrder;
    private String displayHint;
    private String displayGroup;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    /**
     * 参数类型枚举
     */
    public enum ParamType {
        STRING("字符串"),
        INTEGER("整数"),
        DECIMAL("小数"),
        BOOLEAN("布尔"),
        ENUM("枚举"),
        REFERENCE("引用"),
        BONUS_ATTR("属性增益");

        private final String displayName;

        ParamType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    /**
     * 生成器类型枚举
     */
    public enum GeneratorType {
        SEQUENCE("序列生成"),           // 自增ID
        RANDOM("随机生成"),             // 随机值
        FORMULA("公式计算"),            // 基于其他字段计算
        LOOKUP("查找引用"),             // 从引用表查找
        BONUS_ATTR("属性增益生成");     // 智能生成属性组合

        private final String displayName;

        GeneratorType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    public TemplateParam() {
        this.paramType = ParamType.STRING;
        this.isRequired = true;
        this.displayOrder = 0;
    }

    public TemplateParam(Integer templateId, String paramName, String paramCode) {
        this();
        this.templateId = templateId;
        this.paramName = paramName;
        this.paramCode = paramCode;
    }

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getTemplateId() { return templateId; }
    public void setTemplateId(Integer templateId) { this.templateId = templateId; }

    public String getParamName() { return paramName; }
    public void setParamName(String paramName) { this.paramName = paramName; }

    public String getParamCode() { return paramCode; }
    public void setParamCode(String paramCode) { this.paramCode = paramCode; }

    public ParamType getParamType() { return paramType; }
    public void setParamType(ParamType paramType) { this.paramType = paramType; }

    public Boolean getIsRequired() { return isRequired; }
    public void setIsRequired(Boolean isRequired) { this.isRequired = isRequired; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getMinValue() { return minValue; }
    public void setMinValue(String minValue) { this.minValue = minValue; }

    public String getMaxValue() { return maxValue; }
    public void setMaxValue(String maxValue) { this.maxValue = maxValue; }

    public String getEnumValues() { return enumValues; }
    public void setEnumValues(String enumValues) { this.enumValues = enumValues; }

    public GeneratorType getGeneratorType() { return generatorType; }
    public void setGeneratorType(GeneratorType generatorType) { this.generatorType = generatorType; }

    public String getGeneratorConfig() { return generatorConfig; }
    public void setGeneratorConfig(String generatorConfig) { this.generatorConfig = generatorConfig; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public String getDisplayHint() { return displayHint; }
    public void setDisplayHint(String displayHint) { this.displayHint = displayHint; }

    public String getDisplayGroup() { return displayGroup; }
    public void setDisplayGroup(String displayGroup) { this.displayGroup = displayGroup; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "TemplateParam{" +
                "paramName='" + paramName + '\'' +
                ", paramCode='" + paramCode + '\'' +
                ", paramType=" + paramType +
                ", isRequired=" + isRequired +
                '}';
    }
}
