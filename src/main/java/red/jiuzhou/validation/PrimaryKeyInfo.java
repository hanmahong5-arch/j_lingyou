package red.jiuzhou.validation;

/**
 * 主键信息
 *
 * @author Claude
 * @date 2025-12-28
 */
public class PrimaryKeyInfo {

    private String fieldName;        // 主键字段名
    private PrimaryKeyType fieldType; // 主键类型（属性/元素）
    private String detectedStrategy;  // 检测策略

    public enum PrimaryKeyType {
        ATTRIBUTE,  // XML属性（如 <item id="123">）
        ELEMENT     // XML子元素（如 <item><id>123</id></item>）
    }

    public PrimaryKeyInfo(String fieldName, PrimaryKeyType fieldType) {
        this.fieldName = fieldName;
        this.fieldType = fieldType;
    }

    public PrimaryKeyInfo(String fieldName, PrimaryKeyType fieldType, String detectedStrategy) {
        this.fieldName = fieldName;
        this.fieldType = fieldType;
        this.detectedStrategy = detectedStrategy;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public PrimaryKeyType getFieldType() {
        return fieldType;
    }

    public void setFieldType(PrimaryKeyType fieldType) {
        this.fieldType = fieldType;
    }

    public String getDetectedStrategy() {
        return detectedStrategy;
    }

    public void setDetectedStrategy(String detectedStrategy) {
        this.detectedStrategy = detectedStrategy;
    }

    /**
     * 默认策略（使用id作为主键）
     */
    public static PrimaryKeyInfo defaultStrategy() {
        return new PrimaryKeyInfo("id", PrimaryKeyType.ELEMENT, "DEFAULT");
    }

    @Override
    public String toString() {
        return String.format("PrimaryKey[field=%s, type=%s, strategy=%s]",
                fieldName, fieldType, detectedStrategy);
    }
}
