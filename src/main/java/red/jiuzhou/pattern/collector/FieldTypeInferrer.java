package red.jiuzhou.pattern.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.pattern.model.PatternField;
import red.jiuzhou.pattern.model.PatternField.FieldType;
import red.jiuzhou.pattern.model.PatternField.ValueDomainType;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 字段类型推断器
 * 根据字段名称和样本值推断字段的语义类型
 */
public class FieldTypeInferrer {
    private static final Logger log = LoggerFactory.getLogger(FieldTypeInferrer.class);

    // 引用字段名称模式
    private static final Map<Pattern, String> REFERENCE_PATTERNS = new LinkedHashMap<>();

    // 布尔字段名称模式
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile(
            "^(is_|can_|has_|enable_|use_|allow_|show_|hide_|require_|need_).*",
            Pattern.CASE_INSENSITIVE);

    // 属性增益字段模式
    private static final Pattern BONUS_ATTR_PATTERN = Pattern.compile(
            "^bonus_attr[_]?(\\d+|[a-z]\\d*)$",
            Pattern.CASE_INSENSITIVE);

    // 枚举候选字段名称模式
    private static final Pattern ENUM_CANDIDATE_PATTERN = Pattern.compile(
            ".*(_type|_kind|_mode|_state|_status|_level|_grade|_class|_race|_gender|_slot|_category)$",
            Pattern.CASE_INSENSITIVE);

    // 数值类型检测
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d+\\.\\d+$");

    static {
        // 物品引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*item[_]?id.*"), "items.id");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i)^item$"), "items.id");

        // NPC引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*npc[_]?id.*"), "npcs.id");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i)^npc$"), "npcs.id");

        // 技能引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*skill[_]?id.*"), "skills.id");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i)^skill$"), "skills.id");

        // 任务引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*quest[_]?id.*"), "quests.id");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i)^quest$"), "quests.id");

        // 称号引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*title[_]?id.*"), "titles.id");

        // 地图引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*map[_]?id.*"), "maps.id");
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*world[_]?id.*"), "worlds.id");

        // 副本引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*instance[_]?id.*"), "instances.id");

        // 掉落引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*drop[_]?id.*"), "drops.id");
    }

    /**
     * 推断结果
     */
    public static class InferenceResult {
        private FieldType fieldType;
        private ValueDomainType domainType;
        private String referenceTarget;
        private boolean isBonusAttr;
        private String bonusAttrSlot;
        private String valueMin;
        private String valueMax;
        private List<String> enumValues;
        private double confidence;

        public InferenceResult() {
            this.fieldType = FieldType.STRING;
            this.domainType = ValueDomainType.UNBOUNDED;
            this.confidence = 0.5;
        }

        // Getters and Setters
        public FieldType getFieldType() { return fieldType; }
        public void setFieldType(FieldType fieldType) { this.fieldType = fieldType; }

        public ValueDomainType getDomainType() { return domainType; }
        public void setDomainType(ValueDomainType domainType) { this.domainType = domainType; }

        public String getReferenceTarget() { return referenceTarget; }
        public void setReferenceTarget(String referenceTarget) { this.referenceTarget = referenceTarget; }

        public boolean isBonusAttr() { return isBonusAttr; }
        public void setBonusAttr(boolean bonusAttr) { isBonusAttr = bonusAttr; }

        public String getBonusAttrSlot() { return bonusAttrSlot; }
        public void setBonusAttrSlot(String bonusAttrSlot) { this.bonusAttrSlot = bonusAttrSlot; }

        public String getValueMin() { return valueMin; }
        public void setValueMin(String valueMin) { this.valueMin = valueMin; }

        public String getValueMax() { return valueMax; }
        public void setValueMax(String valueMax) { this.valueMax = valueMax; }

        public List<String> getEnumValues() { return enumValues; }
        public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }

    /**
     * 根据字段名推断类型
     */
    public InferenceResult inferFromName(String fieldName) {
        InferenceResult result = new InferenceResult();

        if (fieldName == null || fieldName.isEmpty()) {
            return result;
        }

        // 检查是否为属性增益字段
        if (BONUS_ATTR_PATTERN.matcher(fieldName).matches()) {
            result.setFieldType(FieldType.BONUS_ATTR);
            result.setBonusAttr(true);
            result.setBonusAttrSlot(fieldName);
            result.setConfidence(0.95);
            return result;
        }

        // 检查是否为引用字段
        for (Map.Entry<Pattern, String> entry : REFERENCE_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(fieldName).matches()) {
                result.setFieldType(FieldType.REFERENCE);
                result.setDomainType(ValueDomainType.REFERENCE);
                result.setReferenceTarget(entry.getValue());
                result.setConfidence(0.85);
                return result;
            }
        }

        // 检查是否为布尔字段
        if (BOOLEAN_PATTERN.matcher(fieldName).matches()) {
            result.setFieldType(FieldType.BOOLEAN);
            result.setDomainType(ValueDomainType.ENUM);
            result.setEnumValues(Arrays.asList("true", "false", "0", "1"));
            result.setConfidence(0.8);
            return result;
        }

        // 检查是否为枚举候选
        if (ENUM_CANDIDATE_PATTERN.matcher(fieldName).matches()) {
            result.setFieldType(FieldType.ENUM);
            result.setDomainType(ValueDomainType.ENUM);
            result.setConfidence(0.6); // 需要进一步验证
            return result;
        }

        // 默认为字符串
        result.setConfidence(0.3);
        return result;
    }

    /**
     * 根据样本值推断类型
     */
    public InferenceResult inferFromValues(String fieldName, List<String> sampleValues) {
        // 先从名称推断
        InferenceResult result = inferFromName(fieldName);

        if (sampleValues == null || sampleValues.isEmpty()) {
            return result;
        }

        // 过滤空值
        List<String> nonNullValues = new ArrayList<>();
        int nullCount = 0;
        for (String v : sampleValues) {
            if (v == null || v.trim().isEmpty()) {
                nullCount++;
            } else {
                nonNullValues.add(v.trim());
            }
        }

        if (nonNullValues.isEmpty()) {
            return result;
        }

        // 检查数值类型
        boolean allInteger = true;
        boolean allDecimal = true;
        BigDecimal minVal = null;
        BigDecimal maxVal = null;

        for (String v : nonNullValues) {
            if (!INTEGER_PATTERN.matcher(v).matches()) {
                allInteger = false;
            }
            if (!DECIMAL_PATTERN.matcher(v).matches() && !INTEGER_PATTERN.matcher(v).matches()) {
                allDecimal = false;
            }

            if (allInteger || allDecimal) {
                try {
                    BigDecimal num = new BigDecimal(v);
                    if (minVal == null || num.compareTo(minVal) < 0) {
                        minVal = num;
                    }
                    if (maxVal == null || num.compareTo(maxVal) > 0) {
                        maxVal = num;
                    }
                } catch (NumberFormatException ignored) {
                    allInteger = false;
                    allDecimal = false;
                }
            }
        }

        if (allInteger && result.getFieldType() == FieldType.STRING) {
            result.setFieldType(FieldType.INTEGER);
            result.setDomainType(ValueDomainType.RANGE);
            result.setValueMin(minVal != null ? minVal.toPlainString() : null);
            result.setValueMax(maxVal != null ? maxVal.toPlainString() : null);
            result.setConfidence(0.9);
        } else if (allDecimal && result.getFieldType() == FieldType.STRING) {
            result.setFieldType(FieldType.DECIMAL);
            result.setDomainType(ValueDomainType.RANGE);
            result.setValueMin(minVal != null ? minVal.toPlainString() : null);
            result.setValueMax(maxVal != null ? maxVal.toPlainString() : null);
            result.setConfidence(0.9);
        }

        // 检查是否为枚举（不同值数量少于20）
        Set<String> distinctValues = new HashSet<>(nonNullValues);
        if (distinctValues.size() <= 20 && distinctValues.size() < nonNullValues.size() / 2) {
            if (result.getFieldType() == FieldType.STRING ||
                (result.getFieldType() == FieldType.INTEGER && distinctValues.size() <= 10)) {
                result.setFieldType(FieldType.ENUM);
                result.setDomainType(ValueDomainType.ENUM);
                result.setEnumValues(new ArrayList<>(distinctValues));
                result.setConfidence(0.85);
            }
        }

        return result;
    }

    /**
     * 应用推断结果到PatternField
     */
    public void applyToField(PatternField field, InferenceResult result) {
        field.setInferredType(result.getFieldType());
        field.setValueDomainType(result.getDomainType());
        field.setReferenceTarget(result.getReferenceTarget());
        field.setIsBonusAttr(result.isBonusAttr());
        field.setBonusAttrSlot(result.getBonusAttrSlot());
        field.setValueMin(result.getValueMin());
        field.setValueMax(result.getValueMax());

        if (result.getEnumValues() != null && !result.getEnumValues().isEmpty()) {
            field.setValueEnum(String.join(",", result.getEnumValues()));
        }
    }
}
