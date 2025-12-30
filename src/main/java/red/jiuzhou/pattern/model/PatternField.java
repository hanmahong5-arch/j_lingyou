package red.jiuzhou.pattern.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

/**
 * 字段模式实体
 * 对应 pattern_field 表，存储每个机制下的字段定义和值域
 */
public class PatternField {
    private Integer id;
    private Integer schemaId;
    private String fieldName;
    private String fieldPath;
    private Boolean isAttribute;

    // 类型推断结果
    private FieldType inferredType;
    private ValueDomainType valueDomainType;

    // 值域定义
    private String valueMin;
    private String valueMax;
    private String valueEnum;          // JSON数组
    private String referenceTarget;

    // 属性增益特殊字段
    private Boolean isBonusAttr;
    private String bonusAttrSlot;

    // 统计信息
    private BigDecimal occurrenceRate;
    private BigDecimal nullRate;
    private Integer distinctCount;
    private Integer totalCount;
    private String sampleValues;       // JSON数组

    private Timestamp createdAt;
    private Timestamp updatedAt;

    // 非持久化字段
    private List<PatternValue> values;
    private String schemaCode;         // 关联的机制代码

    public PatternField() {
        this.isAttribute = false;
        this.inferredType = FieldType.STRING;
        this.valueDomainType = ValueDomainType.UNBOUNDED;
        this.isBonusAttr = false;
        this.occurrenceRate = BigDecimal.ZERO;
        this.nullRate = BigDecimal.ZERO;
        this.distinctCount = 0;
        this.totalCount = 0;
    }

    public PatternField(Integer schemaId, String fieldName) {
        this();
        this.schemaId = schemaId;
        this.fieldName = fieldName;
    }

    /**
     * 字段推断类型枚举
     */
    public enum FieldType {
        STRING("字符串"),
        INTEGER("整数"),
        DECIMAL("小数"),
        BOOLEAN("布尔"),
        ENUM("枚举"),
        REFERENCE("引用"),
        BONUS_ATTR("属性增益");

        private final String displayName;

        FieldType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    /**
     * 值域类型枚举
     */
    public enum ValueDomainType {
        UNBOUNDED("无界"),
        RANGE("范围"),
        ENUM("枚举"),
        REFERENCE("引用");

        private final String displayName;

        ValueDomainType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getSchemaId() { return schemaId; }
    public void setSchemaId(Integer schemaId) { this.schemaId = schemaId; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getFieldPath() { return fieldPath; }
    public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }

    public Boolean getIsAttribute() { return isAttribute; }
    public void setIsAttribute(Boolean isAttribute) { this.isAttribute = isAttribute; }

    public FieldType getInferredType() { return inferredType; }
    public void setInferredType(FieldType inferredType) { this.inferredType = inferredType; }

    public ValueDomainType getValueDomainType() { return valueDomainType; }
    public void setValueDomainType(ValueDomainType valueDomainType) { this.valueDomainType = valueDomainType; }

    public String getValueMin() { return valueMin; }
    public void setValueMin(String valueMin) { this.valueMin = valueMin; }

    public String getValueMax() { return valueMax; }
    public void setValueMax(String valueMax) { this.valueMax = valueMax; }

    public String getValueEnum() { return valueEnum; }
    public void setValueEnum(String valueEnum) { this.valueEnum = valueEnum; }

    public String getReferenceTarget() { return referenceTarget; }
    public void setReferenceTarget(String referenceTarget) { this.referenceTarget = referenceTarget; }

    public Boolean getIsBonusAttr() { return isBonusAttr; }
    public void setIsBonusAttr(Boolean isBonusAttr) { this.isBonusAttr = isBonusAttr; }

    public String getBonusAttrSlot() { return bonusAttrSlot; }
    public void setBonusAttrSlot(String bonusAttrSlot) { this.bonusAttrSlot = bonusAttrSlot; }

    public BigDecimal getOccurrenceRate() { return occurrenceRate; }
    public void setOccurrenceRate(BigDecimal occurrenceRate) { this.occurrenceRate = occurrenceRate; }

    public BigDecimal getNullRate() { return nullRate; }
    public void setNullRate(BigDecimal nullRate) { this.nullRate = nullRate; }

    public Integer getDistinctCount() { return distinctCount; }
    public void setDistinctCount(Integer distinctCount) { this.distinctCount = distinctCount; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public String getSampleValues() { return sampleValues; }
    public void setSampleValues(String sampleValues) { this.sampleValues = sampleValues; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public List<PatternValue> getValues() { return values; }
    public void setValues(List<PatternValue> values) { this.values = values; }

    public String getSchemaCode() { return schemaCode; }
    public void setSchemaCode(String schemaCode) { this.schemaCode = schemaCode; }

    @Override
    public String toString() {
        return "PatternField{" +
                "id=" + id +
                ", fieldName='" + fieldName + '\'' +
                ", inferredType=" + inferredType +
                ", isBonusAttr=" + isBonusAttr +
                '}';
    }
}
