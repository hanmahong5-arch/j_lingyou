package red.jiuzhou.pattern.rule.engine;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import red.jiuzhou.pattern.rule.model.FieldModification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 表达式求值器
 *
 * 基于 Spring Expression Language (SpEL) 实现
 * 支持设计师友好的表达式语法
 */
public class ExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 计算表达式
     *
     * @param modification 字段修改规则
     * @param record 当前记录数据
     * @return 计算结果
     */
    public Object evaluate(FieldModification modification, Map<String, Object> record) {
        String spelExpr = modification.toSpelExpression();
        if (spelExpr == null || spelExpr.isEmpty()) {
            return null;
        }

        // 获取当前字段值
        Object currentValue = record.get(modification.getFieldName());

        // 构建求值上下文
        StandardEvaluationContext context = createContext(currentValue, record);

        try {
            Expression expression = parser.parseExpression(spelExpr);
            Object result = expression.getValue(context);

            // 处理数值类型转换
            return normalizeResult(result, currentValue);
        } catch (Exception e) {
            throw new ExpressionEvaluationException(
                "表达式求值失败: " + spelExpr + ", 错误: " + e.getMessage(), e);
        }
    }

    /**
     * 计算表达式（便捷方法）
     *
     * @param expression 表达式字符串
     * @param currentValue 当前值
     * @param record 完整记录
     * @return 计算结果
     */
    public Object evaluate(String expression, Object currentValue, Map<String, Object> record) {
        FieldModification mod = new FieldModification();
        mod.setExpression(expression);
        mod.setFieldName("_temp");

        Map<String, Object> tempRecord = new HashMap<>(record);
        tempRecord.put("_temp", currentValue);

        return evaluate(mod, tempRecord);
    }

    /**
     * 创建SpEL求值上下文
     */
    private StandardEvaluationContext createContext(Object currentValue, Map<String, Object> record) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 设置当前值变量
        context.setVariable("current", toNumber(currentValue));
        context.setVariable("当前值", toNumber(currentValue));

        // 设置记录中的所有字段为变量
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String varName = sanitizeVariableName(entry.getKey());
            context.setVariable(varName, entry.getValue());
        }

        // 注册自定义函数
        registerCustomFunctions(context);

        return context;
    }

    /**
     * 注册自定义函数
     */
    private void registerCustomFunctions(StandardEvaluationContext context) {
        try {
            // CLAMP(value, min, max)
            context.registerFunction("CLAMP",
                ExpressionEvaluator.class.getDeclaredMethod("clamp", double.class, double.class, double.class));

            // PERCENT_CHANGE(base, percent) - 增加/减少百分比
            context.registerFunction("PERCENT_CHANGE",
                ExpressionEvaluator.class.getDeclaredMethod("percentChange", double.class, double.class));

            // ROUND(value, decimals)
            context.registerFunction("ROUND_TO",
                ExpressionEvaluator.class.getDeclaredMethod("roundTo", double.class, int.class));

        } catch (NoSuchMethodException e) {
            // 忽略，自定义函数可选
        }
    }

    /**
     * 将值转换为数值类型
     */
    private Object toNumber(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return value;
    }

    /**
     * 标准化结果（保持原类型）
     */
    private Object normalizeResult(Object result, Object originalValue) {
        if (result == null) return null;

        // 如果结果是数值，根据原始类型转换
        if (result instanceof Number) {
            double doubleResult = ((Number) result).doubleValue();

            if (originalValue instanceof Integer) {
                return (int) Math.round(doubleResult);
            }
            if (originalValue instanceof Long) {
                return Math.round(doubleResult);
            }
            if (originalValue instanceof Float) {
                return (float) doubleResult;
            }
            if (originalValue instanceof BigDecimal) {
                return BigDecimal.valueOf(doubleResult).setScale(2, RoundingMode.HALF_UP);
            }

            // 默认返回整数（游戏数值通常是整数）
            if (doubleResult == Math.floor(doubleResult)) {
                return (int) doubleResult;
            }
            return doubleResult;
        }

        return result;
    }

    /**
     * 清理变量名（移除特殊字符）
     */
    private String sanitizeVariableName(String name) {
        // 将下划线转驼峰，便于在表达式中使用
        return name.replace("-", "_");
    }

    // ========== 自定义函数 ==========

    /**
     * CLAMP函数 - 限制值在指定范围内
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 百分比变化函数
     * percentChange(100, 20) -> 120 (增加20%)
     * percentChange(100, -10) -> 90 (减少10%)
     */
    public static double percentChange(double base, double percent) {
        return base * (1 + percent / 100.0);
    }

    /**
     * 保留指定小数位
     */
    public static double roundTo(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    /**
     * 表达式求值异常
     */
    public static class ExpressionEvaluationException extends RuntimeException {
        public ExpressionEvaluationException(String message) {
            super(message);
        }

        public ExpressionEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
