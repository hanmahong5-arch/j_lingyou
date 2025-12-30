package red.jiuzhou.pattern.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 引用关系实体
 * 对应 pattern_ref 表，存储字段间的跨表引用关系
 */
public class PatternRef {
    private Integer id;

    // 源端
    private Integer sourceSchemaId;
    private Integer sourceFieldId;
    private String sourceFieldName;

    // 目标端
    private Integer targetSchemaId;
    private String targetFieldName;
    private String targetTableName;

    // 引用类型
    private RefType refType;
    private BigDecimal confidence;

    // 验证信息
    private Boolean isVerified;
    private String samplePairs;        // JSON数组

    private Timestamp createdAt;
    private Timestamp updatedAt;

    /**
     * 引用类型枚举
     */
    public enum RefType {
        ID_REFERENCE("ID引用"),         // item_id -> items.id
        NAME_REFERENCE("名称引用"),     // skill_name -> skills.name
        BONUS_ATTR("属性引用");         // bonus_attr1 -> attr_dictionary.attr_code

        private final String displayName;

        RefType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    public PatternRef() {
        this.refType = RefType.ID_REFERENCE;
        this.confidence = new BigDecimal("0.5");
        this.isVerified = false;
    }

    public PatternRef(Integer sourceSchemaId, Integer sourceFieldId, String sourceFieldName) {
        this();
        this.sourceSchemaId = sourceSchemaId;
        this.sourceFieldId = sourceFieldId;
        this.sourceFieldName = sourceFieldName;
    }

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getSourceSchemaId() { return sourceSchemaId; }
    public void setSourceSchemaId(Integer sourceSchemaId) { this.sourceSchemaId = sourceSchemaId; }

    public Integer getSourceFieldId() { return sourceFieldId; }
    public void setSourceFieldId(Integer sourceFieldId) { this.sourceFieldId = sourceFieldId; }

    public String getSourceFieldName() { return sourceFieldName; }
    public void setSourceFieldName(String sourceFieldName) { this.sourceFieldName = sourceFieldName; }

    public Integer getTargetSchemaId() { return targetSchemaId; }
    public void setTargetSchemaId(Integer targetSchemaId) { this.targetSchemaId = targetSchemaId; }

    public String getTargetFieldName() { return targetFieldName; }
    public void setTargetFieldName(String targetFieldName) { this.targetFieldName = targetFieldName; }

    public String getTargetTableName() { return targetTableName; }
    public void setTargetTableName(String targetTableName) { this.targetTableName = targetTableName; }

    public RefType getRefType() { return refType; }
    public void setRefType(RefType refType) { this.refType = refType; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public Boolean getIsVerified() { return isVerified; }
    public void setIsVerified(Boolean isVerified) { this.isVerified = isVerified; }

    public String getSamplePairs() { return samplePairs; }
    public void setSamplePairs(String samplePairs) { this.samplePairs = samplePairs; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "PatternRef{" +
                "sourceFieldName='" + sourceFieldName + '\'' +
                " -> " + targetTableName + "." + targetFieldName +
                ", refType=" + refType +
                ", confidence=" + confidence +
                '}';
    }
}
