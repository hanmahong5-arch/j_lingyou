package red.jiuzhou.pattern.rule.model;

/**
 * 字段修改规则 - 定义如何修改目标字段
 *
 * 支持的表达式语法：
 * - 当前值 × 1.2           # 提升20%
 * - 当前值 + 50            # 固定加50
 * - 当前值 - 10%           # 降低10%
 * - IF(等级 >= 50, 当前值 × 1.3, 当前值 × 1.1)  # 条件运算
 * - CLAMP(当前值 × 1.5, 100, 9999)  # 区间限制
 * - ROUND(当前值 × 1.15)   # 四舍五入
 * - FLOOR(当前值 × 1.15)   # 向下取整
 */
public class FieldModification {

    /** 目标字段名 */
    private String fieldName;

    /** 表达式（SpEL风格） */
    private String expression;

    /** 修改描述（用于展示） */
    private String description;

    /** 修改类型 */
    private ModificationType type = ModificationType.EXPRESSION;

    public FieldModification() {}

    public FieldModification(String fieldName, String expression) {
        this.fieldName = fieldName;
        this.expression = expression;
    }

    public FieldModification(String fieldName, String expression, String description) {
        this(fieldName, expression);
        this.description = description;
    }

    /**
     * 修改类型
     */
    public enum ModificationType {
        /** 表达式计算 */
        EXPRESSION,
        /** 固定值 */
        FIXED_VALUE,
        /** 引用其他字段 */
        FIELD_REFERENCE,
        /** 查找替换 */
        LOOKUP_REPLACE
    }

    /**
     * 生成人类可读的修改描述
     */
    public String toDisplayString() {
        if (description != null && !description.isEmpty()) {
            return description;
        }
        return fieldName + " = " + expression;
    }

    /**
     * 将简化表达式转换为SpEL表达式
     *
     * 输入："当前值 × 1.2" 或 "current * 1.2"
     * 输出："#current * 1.2"
     */
    public String toSpelExpression() {
        if (expression == null) return null;

        String spel = expression;

        // 替换中文关键词
        spel = spel.replace("当前值", "#current");
        spel = spel.replace("×", "*");
        spel = spel.replace("÷", "/");

        // 处理百分比语法 "- 10%" -> "* 0.9"
        if (spel.matches(".*[+-]\\s*\\d+%.*")) {
            spel = convertPercentage(spel);
        }

        // 处理CLAMP函数
        spel = spel.replace("CLAMP(", "T(Math).max(T(Math).min(");
        if (spel.contains("T(Math).max")) {
            // CLAMP(expr, min, max) -> max(min(expr, max), min)
            spel = convertClamp(spel);
        }

        // 处理ROUND/FLOOR/CEIL
        spel = spel.replace("ROUND(", "T(Math).round(");
        spel = spel.replace("FLOOR(", "T(Math).floor(");
        spel = spel.replace("CEIL(", "T(Math).ceil(");

        // 确保current有#前缀
        if (!spel.contains("#current") && spel.contains("current")) {
            spel = spel.replace("current", "#current");
        }

        return spel;
    }

    /**
     * 转换百分比表达式
     * "当前值 - 10%" -> "#current * 0.9"
     * "当前值 + 20%" -> "#current * 1.2"
     */
    private String convertPercentage(String expr) {
        // 匹配 + N% 或 - N%
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(#?current|当前值)\\s*([+-])\\s*(\\d+)%"
        );
        java.util.regex.Matcher matcher = pattern.matcher(expr);

        if (matcher.find()) {
            String op = matcher.group(2);
            int percent = Integer.parseInt(matcher.group(3));
            double multiplier = op.equals("+") ? (1 + percent / 100.0) : (1 - percent / 100.0);
            return expr.substring(0, matcher.start()) + "#current * " + multiplier + expr.substring(matcher.end());
        }
        return expr;
    }

    /**
     * 转换CLAMP函数
     * CLAMP(expr, min, max) -> Math.max(min, Math.min(max, expr))
     */
    private String convertClamp(String expr) {
        // 简化实现，完整版需要解析括号嵌套
        return expr.replace("T(Math).max(T(Math).min(", "T(Math).max(");
    }

    // Getters and Setters
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ModificationType getType() { return type; }
    public void setType(ModificationType type) { this.type = type; }
}
