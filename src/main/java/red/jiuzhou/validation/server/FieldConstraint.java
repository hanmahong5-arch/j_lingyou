package red.jiuzhou.validation.server;

import java.util.Objects;

/**
 * 字段约束 - 定义字段的值域范围、默认值等
 *
 * @author Claude Code
 * @since 2025-12-29
 */
public class FieldConstraint {

    private final String fieldName;
    private final ConstraintType type;
    private final Object minValue;
    private final Object maxValue;
    private final Object defaultValue;
    private final String description;

    private FieldConstraint(Builder builder) {
        this.fieldName = builder.fieldName;
        this.type = builder.type;
        this.minValue = builder.minValue;
        this.maxValue = builder.maxValue;
        this.defaultValue = builder.defaultValue;
        this.description = builder.description;
    }

    /**
     * 验证值是否符合约束
     */
    public boolean isValid(Object value) {
        if (value == null) {
            return true; // null值由必填字段检查处理
        }

        switch (type) {
            case NUMERIC_RANGE:
                return validateNumericRange(value);
            case STRING_LENGTH:
                return validateStringLength(value);
            case ENUM_VALUES:
                return validateEnumValue(value);
            case PATTERN:
                return validatePattern(value);
            default:
                return true;
        }
    }

    private boolean validateNumericRange(Object value) {
        if (!(value instanceof Number)) {
            return false;
        }
        double numValue = ((Number) value).doubleValue();

        if (minValue != null && numValue < ((Number) minValue).doubleValue()) {
            return false;
        }
        if (maxValue != null && numValue > ((Number) maxValue).doubleValue()) {
            return false;
        }
        return true;
    }

    private boolean validateStringLength(Object value) {
        String strValue = value.toString();
        int length = strValue.length();

        if (minValue != null && length < ((Number) minValue).intValue()) {
            return false;
        }
        if (maxValue != null && length > ((Number) maxValue).intValue()) {
            return false;
        }
        return true;
    }

    private boolean validateEnumValue(Object value) {
        // 枚举值验证（需要在maxValue中传入允许的值列表）
        return true; // 简化实现
    }

    private boolean validatePattern(Object value) {
        // 正则模式验证
        return true; // 简化实现
    }

    public String getFieldName() {
        return fieldName;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 创建数值范围约束
     */
    public static FieldConstraint numericRange(String fieldName, Number min, Number max, Number defaultValue) {
        return new Builder(fieldName, ConstraintType.NUMERIC_RANGE)
                .minValue(min)
                .maxValue(max)
                .defaultValue(defaultValue)
                .build();
    }

    /**
     * 创建字符串长度约束
     */
    public static FieldConstraint stringLength(String fieldName, int maxLength, String defaultValue) {
        return new Builder(fieldName, ConstraintType.STRING_LENGTH)
                .maxValue(maxLength)
                .defaultValue(defaultValue)
                .build();
    }

    public enum ConstraintType {
        NUMERIC_RANGE,    // 数值范围
        STRING_LENGTH,    // 字符串长度
        ENUM_VALUES,      // 枚举值
        PATTERN           // 正则模式
    }

    public static class Builder {
        private final String fieldName;
        private final ConstraintType type;
        private Object minValue;
        private Object maxValue;
        private Object defaultValue;
        private String description;

        public Builder(String fieldName, ConstraintType type) {
            this.fieldName = fieldName;
            this.type = type;
        }

        public Builder minValue(Object minValue) {
            this.minValue = minValue;
            return this;
        }

        public Builder maxValue(Object maxValue) {
            this.maxValue = maxValue;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public FieldConstraint build() {
            return new FieldConstraint(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldConstraint that = (FieldConstraint) o;
        return Objects.equals(fieldName, that.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldName);
    }
}
