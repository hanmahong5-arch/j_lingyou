package red.jiuzhou.pattern.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 值域分布实体
 * 对应 pattern_value 表，存储枚举字段的值分布统计
 */
public class PatternValue {
    private Integer id;
    private Integer fieldId;
    private String valueContent;
    private String valueDisplay;       // 中文翻译

    // 统计信息
    private Integer occurrenceCount;
    private BigDecimal percentage;

    // 来源追踪
    private String sourceFiles;        // JSON数组
    private String firstSeenFile;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public PatternValue() {
        this.occurrenceCount = 1;
        this.percentage = BigDecimal.ZERO;
    }

    public PatternValue(Integer fieldId, String valueContent) {
        this();
        this.fieldId = fieldId;
        this.valueContent = valueContent;
    }

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getFieldId() { return fieldId; }
    public void setFieldId(Integer fieldId) { this.fieldId = fieldId; }

    public String getValueContent() { return valueContent; }
    public void setValueContent(String valueContent) { this.valueContent = valueContent; }

    public String getValueDisplay() { return valueDisplay; }
    public void setValueDisplay(String valueDisplay) { this.valueDisplay = valueDisplay; }

    public Integer getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }

    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }

    public String getSourceFiles() { return sourceFiles; }
    public void setSourceFiles(String sourceFiles) { this.sourceFiles = sourceFiles; }

    public String getFirstSeenFile() { return firstSeenFile; }
    public void setFirstSeenFile(String firstSeenFile) { this.firstSeenFile = firstSeenFile; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "PatternValue{" +
                "valueContent='" + valueContent + '\'' +
                ", occurrenceCount=" + occurrenceCount +
                ", percentage=" + percentage +
                '}';
    }
}
