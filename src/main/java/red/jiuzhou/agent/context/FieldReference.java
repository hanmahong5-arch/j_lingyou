package red.jiuzhou.agent.context;

/**
 * 字段引用关系
 *
 * 表示一个字段引用另一个表的字段的关系
 */
public class FieldReference {

    /** 源表名 */
    private String sourceTable;

    /** 源字段名 */
    private String sourceField;

    /** 目标表名 */
    private String targetTable;

    /** 目标字段名 */
    private String targetField;

    /** 引用数量（有多少条记录建立了此引用） */
    private int referenceCount;

    /** 引用类型（如 FOREIGN_KEY, LOGICAL_REFERENCE 等） */
    private String referenceType;

    public FieldReference() {
    }

    public FieldReference(String sourceTable, String sourceField,
                          String targetTable, String targetField) {
        this.sourceTable = sourceTable;
        this.sourceField = sourceField;
        this.targetTable = targetTable;
        this.targetField = targetField;
    }

    /**
     * 创建出度引用（当前表引用其他表）
     */
    public static FieldReference outgoing(String sourceTable, String sourceField,
                                          String targetTable, String targetField) {
        return new FieldReference(sourceTable, sourceField, targetTable, targetField);
    }

    /**
     * 创建入度引用（其他表引用当前表）
     */
    public static FieldReference incoming(String sourceTable, String sourceField,
                                          String targetTable, String targetField) {
        return new FieldReference(sourceTable, sourceField, targetTable, targetField);
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getSourceField() {
        return sourceField;
    }

    public void setSourceField(String sourceField) {
        this.sourceField = sourceField;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public String getTargetField() {
        return targetField;
    }

    public void setTargetField(String targetField) {
        this.targetField = targetField;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    public void setReferenceCount(int referenceCount) {
        this.referenceCount = referenceCount;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    @Override
    public String toString() {
        return sourceTable + "." + sourceField + " → " + targetTable + "." + targetField;
    }
}
