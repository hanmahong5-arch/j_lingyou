package red.jiuzhou.pattern.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 样本数据实体
 * 对应 pattern_sample 表，存储真实配置样本
 */
public class PatternSample {
    private Integer id;
    private Integer schemaId;

    // 来源信息
    private String sourceFile;
    private String sourceFileName;

    // 记录标识
    private String recordId;
    private String recordName;

    // 原始数据
    private String rawXml;
    private String parsedJson;

    // 属性增益信息
    private Boolean hasBonusAttr;
    private Integer bonusAttrCount;
    private String bonusAttrSummary;   // JSON

    // 模板适用性
    private Boolean isTemplateCandidate;
    private BigDecimal templateScore;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public PatternSample() {
        this.hasBonusAttr = false;
        this.bonusAttrCount = 0;
        this.isTemplateCandidate = false;
        this.templateScore = BigDecimal.ZERO;
    }

    public PatternSample(Integer schemaId, String sourceFile) {
        this();
        this.schemaId = schemaId;
        this.sourceFile = sourceFile;
    }

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getSchemaId() { return schemaId; }
    public void setSchemaId(Integer schemaId) { this.schemaId = schemaId; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public String getRecordName() { return recordName; }
    public void setRecordName(String recordName) { this.recordName = recordName; }

    public String getRawXml() { return rawXml; }
    public void setRawXml(String rawXml) { this.rawXml = rawXml; }

    public String getParsedJson() { return parsedJson; }
    public void setParsedJson(String parsedJson) { this.parsedJson = parsedJson; }

    public Boolean getHasBonusAttr() { return hasBonusAttr; }
    public void setHasBonusAttr(Boolean hasBonusAttr) { this.hasBonusAttr = hasBonusAttr; }

    public Integer getBonusAttrCount() { return bonusAttrCount; }
    public void setBonusAttrCount(Integer bonusAttrCount) { this.bonusAttrCount = bonusAttrCount; }

    public String getBonusAttrSummary() { return bonusAttrSummary; }
    public void setBonusAttrSummary(String bonusAttrSummary) { this.bonusAttrSummary = bonusAttrSummary; }

    public Boolean getIsTemplateCandidate() { return isTemplateCandidate; }
    public void setIsTemplateCandidate(Boolean isTemplateCandidate) { this.isTemplateCandidate = isTemplateCandidate; }

    public BigDecimal getTemplateScore() { return templateScore; }
    public void setTemplateScore(BigDecimal templateScore) { this.templateScore = templateScore; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "PatternSample{" +
                "sourceFileName='" + sourceFileName + '\'' +
                ", recordId='" + recordId + '\'' +
                ", hasBonusAttr=" + hasBonusAttr +
                '}';
    }
}
