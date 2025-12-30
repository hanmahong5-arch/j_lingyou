package red.jiuzhou.pattern.model;

import java.sql.Timestamp;
import java.util.List;

/**
 * 生成模板实体
 * 对应 data_template 表，存储批量生成的模板定义
 */
public class DataTemplate {
    private Integer id;
    private Integer schemaId;

    // 模板基本信息
    private String templateName;
    private String templateCode;
    private TemplateType templateType;

    // 模板内容
    private String templateXml;
    private String placeholderList;    // JSON数组

    // 默认值和生成器
    private String defaultValues;      // JSON对象
    private String valueGenerators;    // JSON对象

    // 验证规则
    private String validationRules;    // JSON数组

    // 元信息
    private String description;
    private Integer usageCount;
    private Boolean isActive;
    private String createdBy;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    // 非持久化字段
    private List<TemplateParam> params;
    private String schemaCode;

    /**
     * 模板类型枚举
     */
    public enum TemplateType {
        CREATE("创建新数据"),
        MODIFY("修改现有数据"),
        CLONE("克隆并修改");

        private final String displayName;

        TemplateType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    public DataTemplate() {
        this.templateType = TemplateType.CREATE;
        this.usageCount = 0;
        this.isActive = true;
    }

    public DataTemplate(Integer schemaId, String templateName, String templateCode) {
        this();
        this.schemaId = schemaId;
        this.templateName = templateName;
        this.templateCode = templateCode;
    }

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getSchemaId() { return schemaId; }
    public void setSchemaId(Integer schemaId) { this.schemaId = schemaId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }

    public TemplateType getTemplateType() { return templateType; }
    public void setTemplateType(TemplateType templateType) { this.templateType = templateType; }

    public String getTemplateXml() { return templateXml; }
    public void setTemplateXml(String templateXml) { this.templateXml = templateXml; }

    public String getPlaceholderList() { return placeholderList; }
    public void setPlaceholderList(String placeholderList) { this.placeholderList = placeholderList; }

    public String getDefaultValues() { return defaultValues; }
    public void setDefaultValues(String defaultValues) { this.defaultValues = defaultValues; }

    public String getValueGenerators() { return valueGenerators; }
    public void setValueGenerators(String valueGenerators) { this.valueGenerators = valueGenerators; }

    public String getValidationRules() { return validationRules; }
    public void setValidationRules(String validationRules) { this.validationRules = validationRules; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getUsageCount() { return usageCount; }
    public void setUsageCount(Integer usageCount) { this.usageCount = usageCount; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public List<TemplateParam> getParams() { return params; }
    public void setParams(List<TemplateParam> params) { this.params = params; }

    public String getSchemaCode() { return schemaCode; }
    public void setSchemaCode(String schemaCode) { this.schemaCode = schemaCode; }

    @Override
    public String toString() {
        return "DataTemplate{" +
                "templateName='" + templateName + '\'' +
                ", templateCode='" + templateCode + '\'' +
                ", templateType=" + templateType +
                '}';
    }
}
