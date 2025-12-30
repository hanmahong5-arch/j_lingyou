package red.jiuzhou.pattern.rule.model;

/**
 * 条件定义 - 用于筛选目标记录
 *
 * 设计理念：设计师表达"想要什么"，系统自动完成"怎么做"
 */
public class Condition {

    /** 字段名 */
    private String fieldName;

    /** 操作符 */
    private Operator operator;

    /** 比较值 */
    private Object value;

    /** 逻辑连接符（与下一个条件的关系） */
    private LogicOperator logicOperator = LogicOperator.AND;

    public Condition() {}

    public Condition(String fieldName, Operator operator, Object value) {
        this.fieldName = fieldName;
        this.operator = operator;
        this.value = value;
    }

    public Condition(String fieldName, Operator operator, Object value, LogicOperator logicOperator) {
        this(fieldName, operator, value);
        this.logicOperator = logicOperator;
    }

    /**
     * 操作符枚举
     */
    public enum Operator {
        EQUALS("=", "等于"),
        NOT_EQUALS("!=", "不等于"),
        GREATER_THAN(">", "大于"),
        GREATER_OR_EQUALS(">=", "大于等于"),
        LESS_THAN("<", "小于"),
        LESS_OR_EQUALS("<=", "小于等于"),
        CONTAINS("CONTAINS", "包含"),
        NOT_CONTAINS("NOT_CONTAINS", "不包含"),
        STARTS_WITH("STARTS_WITH", "开头是"),
        ENDS_WITH("ENDS_WITH", "结尾是"),
        IN("IN", "在列表中"),
        NOT_IN("NOT_IN", "不在列表中"),
        IS_NULL("IS_NULL", "为空"),
        IS_NOT_NULL("IS_NOT_NULL", "不为空"),
        BETWEEN("BETWEEN", "在范围内"),
        REGEX("REGEX", "正则匹配");

        private final String symbol;
        private final String displayName;

        Operator(String symbol, String displayName) {
            this.symbol = symbol;
            this.displayName = displayName;
        }

        public String getSymbol() { return symbol; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * 逻辑运算符
     */
    public enum LogicOperator {
        AND("AND", "并且"),
        OR("OR", "或者");

        private final String symbol;
        private final String displayName;

        LogicOperator(String symbol, String displayName) {
            this.symbol = symbol;
            this.displayName = displayName;
        }

        public String getSymbol() { return symbol; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * 生成人类可读的条件描述
     */
    public String toDisplayString() {
        String valueStr = value != null ? value.toString() : "空";
        return String.format("%s %s %s", fieldName, operator.getDisplayName(), valueStr);
    }

    /**
     * 生成SQL WHERE子句片段
     */
    public String toSqlFragment() {
        if (operator == Operator.IS_NULL) {
            return fieldName + " IS NULL";
        }
        if (operator == Operator.IS_NOT_NULL) {
            return fieldName + " IS NOT NULL";
        }
        if (operator == Operator.CONTAINS) {
            return fieldName + " LIKE '%" + escapeSql(value.toString()) + "%'";
        }
        if (operator == Operator.STARTS_WITH) {
            return fieldName + " LIKE '" + escapeSql(value.toString()) + "%'";
        }
        if (operator == Operator.ENDS_WITH) {
            return fieldName + " LIKE '%" + escapeSql(value.toString()) + "'";
        }
        if (operator == Operator.IN || operator == Operator.NOT_IN) {
            String inClause = operator == Operator.IN ? " IN " : " NOT IN ";
            return fieldName + inClause + "(" + value + ")";
        }
        if (operator == Operator.BETWEEN && value instanceof Object[]) {
            Object[] range = (Object[]) value;
            return fieldName + " BETWEEN " + range[0] + " AND " + range[1];
        }

        // 基础比较
        String op = operator.getSymbol();
        if (value instanceof String) {
            return fieldName + " " + op + " '" + escapeSql(value.toString()) + "'";
        }
        return fieldName + " " + op + " " + value;
    }

    private String escapeSql(String str) {
        return str.replace("'", "''");
    }

    // Getters and Setters
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public Operator getOperator() { return operator; }
    public void setOperator(Operator operator) { this.operator = operator; }

    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }

    public LogicOperator getLogicOperator() { return logicOperator; }
    public void setLogicOperator(LogicOperator logicOperator) { this.logicOperator = logicOperator; }
}
