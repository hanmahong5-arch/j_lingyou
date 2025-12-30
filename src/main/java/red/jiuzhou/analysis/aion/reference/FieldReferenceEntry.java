package red.jiuzhou.analysis.aion.reference;

import com.alibaba.fastjson.JSON;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 字段引用条目
 *
 * <p>表示一个XML文件中的字段对另一个系统的引用关系。
 * 例如：drop_templates.xml 中的 item_id 字段引用 物品系统。
 *
 * @author Claude
 * @version 1.0
 */
public class FieldReferenceEntry {

    // ========== 源端信息 ==========

    /** 源文件完整路径 */
    private String sourceFile;

    /** 源文件名（不含路径） */
    private String sourceFileName;

    /** 字段名（如 item_id） */
    private String sourceField;

    /** 字段在XML中的路径（如 items/item@item_id） */
    private String sourceFieldPath;

    /** 源文件所属机制分类 */
    private String sourceMechanism;

    // ========== 目标端信息 ==========

    /** 目标系统名称（如 物品系统） */
    private String targetSystem;

    /** 目标数据库表名（如 client_items） */
    private String targetTable;

    // ========== 统计信息 ==========

    /** 引用总数 */
    private int referenceCount;

    /** 不重复的ID值数量 */
    private int distinctValues;

    /** 有效引用数（在目标表中存在） */
    private int validReferences;

    /** 无效引用数（在目标表中不存在） */
    private int invalidReferences;

    // ========== 样本数据 ==========

    /** 示例ID值（最多10个） */
    private List<String> sampleValues = new ArrayList<>();

    /** 示例名称（对应sampleValues） */
    private List<String> sampleNames = new ArrayList<>();

    // ========== 元数据 ==========

    /** 检测置信度（0.0-1.0） */
    private double confidence = 1.0;

    /** 最后分析时间 */
    private LocalDateTime lastAnalysisTime;

    /** 数据库主键ID */
    private Long id;

    // ========== 构造函数 ==========

    public FieldReferenceEntry() {
    }

    public FieldReferenceEntry(String sourceFile, String sourceField, String targetSystem) {
        this.sourceFile = sourceFile;
        this.sourceFileName = extractFileName(sourceFile);
        this.sourceField = sourceField;
        this.targetSystem = targetSystem;
    }

    // ========== 便捷方法 ==========

    /**
     * 从完整路径提取文件名
     */
    private static String extractFileName(String path) {
        if (path == null) return "";
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * 计算有效率
     */
    public double getValidRate() {
        int total = validReferences + invalidReferences;
        if (total == 0) return 1.0;
        return (double) validReferences / total;
    }

    /**
     * 获取有效率百分比字符串
     */
    public String getValidRatePercent() {
        return String.format("%.1f%%", getValidRate() * 100);
    }

    /**
     * 判断是否有无效引用
     */
    public boolean hasInvalidReferences() {
        return invalidReferences > 0;
    }

    /**
     * 获取样本值的JSON字符串（用于数据库存储）
     */
    public String getSampleValuesJson() {
        return JSON.toJSONString(sampleValues);
    }

    /**
     * 获取样本名称的JSON字符串（用于数据库存储）
     */
    public String getSampleNamesJson() {
        return JSON.toJSONString(sampleNames);
    }

    /**
     * 从JSON字符串解析样本值
     */
    public void setSampleValuesFromJson(String json) {
        if (json != null && !json.isEmpty()) {
            this.sampleValues = JSON.parseArray(json, String.class);
        }
    }

    /**
     * 从JSON字符串解析样本名称
     */
    public void setSampleNamesFromJson(String json) {
        if (json != null && !json.isEmpty()) {
            this.sampleNames = JSON.parseArray(json, String.class);
        }
    }

    /**
     * 添加样本值和对应名称
     */
    public void addSample(String value, String name) {
        if (sampleValues.size() < 10) {  // 最多保存10个样本
            sampleValues.add(value);
            sampleNames.add(name != null ? name : value);
        }
    }

    /**
     * 获取显示用的摘要信息
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(sourceFileName).append(" → ").append(targetSystem);
        sb.append(" (").append(sourceField).append(")");
        sb.append(" : ").append(referenceCount).append("个引用");
        if (invalidReferences > 0) {
            sb.append(", ").append(invalidReferences).append("个无效");
        }
        return sb.toString();
    }

    // ========== Getters and Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
        this.sourceFileName = extractFileName(sourceFile);
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getSourceFieldPath() {
        return sourceFieldPath;
    }

    public void setSourceFieldPath(String sourceFieldPath) {
        this.sourceFieldPath = sourceFieldPath;
    }

    public String getSourceMechanism() {
        return sourceMechanism;
    }

    public void setSourceMechanism(String sourceMechanism) {
        this.sourceMechanism = sourceMechanism;
    }

    public String getTargetSystem() {
        return targetSystem;
    }

    public void setTargetSystem(String targetSystem) {
        this.targetSystem = targetSystem;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    public void setReferenceCount(int referenceCount) {
        this.referenceCount = referenceCount;
    }

    public int getDistinctValues() {
        return distinctValues;
    }

    public void setDistinctValues(int distinctValues) {
        this.distinctValues = distinctValues;
    }

    public int getValidReferences() {
        return validReferences;
    }

    public void setValidReferences(int validReferences) {
        this.validReferences = validReferences;
    }

    public int getInvalidReferences() {
        return invalidReferences;
    }

    public void setInvalidReferences(int invalidReferences) {
        this.invalidReferences = invalidReferences;
    }

    public List<String> getSampleValues() {
        return sampleValues;
    }

    public void setSampleValues(List<String> sampleValues) {
        this.sampleValues = sampleValues != null ? sampleValues : new ArrayList<>();
    }

    public List<String> getSampleNames() {
        return sampleNames;
    }

    public void setSampleNames(List<String> sampleNames) {
        this.sampleNames = sampleNames != null ? sampleNames : new ArrayList<>();
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public LocalDateTime getLastAnalysisTime() {
        return lastAnalysisTime;
    }

    public void setLastAnalysisTime(LocalDateTime lastAnalysisTime) {
        this.lastAnalysisTime = lastAnalysisTime;
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
