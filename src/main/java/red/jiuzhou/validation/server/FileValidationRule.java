package red.jiuzhou.validation.server;

import java.util.*;

/**
 * 文件级验证规则 - 为每个XML文件/表定义专属的验证规则
 *
 * <p>规则包括：
 * <ul>
 *   <li>字段黑名单 - 服务器不支持的字段（导出时自动移除）</li>
 *   <li>值域约束 - 字段的取值范围和默认值</li>
 *   <li>必填字段 - 必须存在的字段</li>
 *   <li>引用完整性 - 外键引用验证（如item_id必须在items表中存在）</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2025-12-29
 */
public class FileValidationRule {

    private final String tableName;                           // 表名（如 "skills"）
    private final String xmlFileName;                         // XML文件名（如 "skills.xml"）
    private final Set<String> blacklistFields;                // 字段黑名单
    private final Map<String, FieldConstraint> constraints;   // 字段约束
    private final Set<String> requiredFields;                 // 必填字段
    private final Map<String, String> referenceFields;        // 引用字段 (字段名 -> 引用表名)
    private final String description;                         // 规则描述

    private FileValidationRule(Builder builder) {
        this.tableName = builder.tableName;
        this.xmlFileName = builder.xmlFileName;
        this.blacklistFields = Collections.unmodifiableSet(new HashSet<>(builder.blacklistFields));
        this.constraints = Collections.unmodifiableMap(new HashMap<>(builder.constraints));
        this.requiredFields = Collections.unmodifiableSet(new HashSet<>(builder.requiredFields));
        this.referenceFields = Collections.unmodifiableMap(new HashMap<>(builder.referenceFields));
        this.description = builder.description;
    }

    /**
     * 检查字段是否在黑名单中
     */
    public boolean isBlacklisted(String fieldName) {
        return blacklistFields.contains(fieldName);
    }

    /**
     * 获取字段约束
     */
    public Optional<FieldConstraint> getConstraint(String fieldName) {
        return Optional.ofNullable(constraints.get(fieldName));
    }

    /**
     * 检查字段是否为必填
     */
    public boolean isRequired(String fieldName) {
        return requiredFields.contains(fieldName);
    }

    /**
     * 获取引用的目标表名
     */
    public Optional<String> getReferenceTable(String fieldName) {
        return Optional.ofNullable(referenceFields.get(fieldName));
    }

    public String getTableName() {
        return tableName;
    }

    public String getXmlFileName() {
        return xmlFileName;
    }

    public Set<String> getBlacklistFields() {
        return blacklistFields;
    }

    public Set<String> getRequiredFields() {
        return requiredFields;
    }

    public Map<String, String> getReferenceFields() {
        return referenceFields;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 统计规则数量（用于分析报告）
     */
    public int getTotalRuleCount() {
        return blacklistFields.size() + constraints.size() + requiredFields.size() + referenceFields.size();
    }

    public static class Builder {
        private String tableName;
        private String xmlFileName;
        private final Set<String> blacklistFields = new HashSet<>();
        private final Map<String, FieldConstraint> constraints = new HashMap<>();
        private final Set<String> requiredFields = new HashSet<>();
        private final Map<String, String> referenceFields = new HashMap<>();
        private String description;

        public Builder(String tableName) {
            this.tableName = tableName;
            this.xmlFileName = tableName + ".xml";
        }

        public Builder xmlFileName(String xmlFileName) {
            this.xmlFileName = xmlFileName;
            return this;
        }

        /**
         * 添加黑名单字段（可变参数）
         */
        public Builder addBlacklistFields(String... fields) {
            this.blacklistFields.addAll(Arrays.asList(fields));
            return this;
        }

        /**
         * 批量添加黑名单字段
         */
        public Builder addBlacklistFields(Collection<String> fields) {
            this.blacklistFields.addAll(fields);
            return this;
        }

        /**
         * 添加值域约束
         */
        public Builder addConstraint(FieldConstraint constraint) {
            this.constraints.put(constraint.getFieldName(), constraint);
            return this;
        }

        /**
         * 添加数值范围约束（快捷方法）
         */
        public Builder addNumericConstraint(String fieldName, Number min, Number max, Number defaultValue) {
            return addConstraint(FieldConstraint.numericRange(fieldName, min, max, defaultValue));
        }

        /**
         * 添加必填字段
         */
        public Builder addRequiredFields(String... fields) {
            this.requiredFields.addAll(Arrays.asList(fields));
            return this;
        }

        /**
         * 添加引用完整性约束
         */
        public Builder addReferenceField(String fieldName, String targetTable) {
            this.referenceFields.put(fieldName, targetTable);
            return this;
        }

        /**
         * 设置规则描述
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public FileValidationRule build() {
            Objects.requireNonNull(tableName, "tableName不能为null");
            Objects.requireNonNull(xmlFileName, "xmlFileName不能为null");
            return new FileValidationRule(this);
        }
    }

    @Override
    public String toString() {
        return String.format("FileValidationRule{table='%s', xml='%s', rules=%d}",
                tableName, xmlFileName, getTotalRuleCount());
    }
}
