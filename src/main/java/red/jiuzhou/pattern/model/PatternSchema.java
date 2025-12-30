package red.jiuzhou.pattern.model;

import java.sql.Timestamp;
import java.util.List;

/**
 * 模式分类实体
 * 对应 pattern_schema 表，存储27个机制分类的模式定义
 */
public class PatternSchema {
    private Integer id;
    private String mechanismCode;
    private String mechanismName;
    private String mechanismIcon;
    private String mechanismColor;
    private String typicalFields;      // JSON数组
    private String typicalStructure;   // XML模板
    private Integer fileCount;
    private Integer fieldCount;
    private Integer sampleCount;
    private String description;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // 非持久化字段
    private List<PatternField> fields;

    public PatternSchema() {}

    public PatternSchema(String mechanismCode, String mechanismName) {
        this.mechanismCode = mechanismCode;
        this.mechanismName = mechanismName;
        this.fileCount = 0;
        this.fieldCount = 0;
        this.sampleCount = 0;
    }

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getMechanismCode() { return mechanismCode; }
    public void setMechanismCode(String mechanismCode) { this.mechanismCode = mechanismCode; }

    public String getMechanismName() { return mechanismName; }
    public void setMechanismName(String mechanismName) { this.mechanismName = mechanismName; }

    public String getMechanismIcon() { return mechanismIcon; }
    public void setMechanismIcon(String mechanismIcon) { this.mechanismIcon = mechanismIcon; }

    public String getMechanismColor() { return mechanismColor; }
    public void setMechanismColor(String mechanismColor) { this.mechanismColor = mechanismColor; }

    public String getTypicalFields() { return typicalFields; }
    public void setTypicalFields(String typicalFields) { this.typicalFields = typicalFields; }

    public String getTypicalStructure() { return typicalStructure; }
    public void setTypicalStructure(String typicalStructure) { this.typicalStructure = typicalStructure; }

    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }

    public Integer getFieldCount() { return fieldCount; }
    public void setFieldCount(Integer fieldCount) { this.fieldCount = fieldCount; }

    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public List<PatternField> getFields() { return fields; }
    public void setFields(List<PatternField> fields) { this.fields = fields; }

    @Override
    public String toString() {
        return "PatternSchema{" +
                "id=" + id +
                ", mechanismCode='" + mechanismCode + '\'' +
                ", mechanismName='" + mechanismName + '\'' +
                ", fileCount=" + fileCount +
                ", fieldCount=" + fieldCount +
                '}';
    }
}
