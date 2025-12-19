package red.jiuzhou.util;

import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 反射工具类
 *
 * 提供基于反射的对象操作：
 * - XML元素转Java对象
 * - Java对象转XML字符串
 * - 字段名获取和映射
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class ReflectionUtils {

    private static final Logger log = LoggerFactory.getLogger(ReflectionUtils.class);

    // 字段缓存，避免重复反射
    private static final Map<Class<?>, List<Field>> FIELD_CACHE = new HashMap<>();

    /**
     * 获取类的所有字段名
     *
     * @param clazz 类
     * @return 字段名列表
     */
    public static List<String> getFieldNames(Class<?> clazz) {
        List<String> names = new ArrayList<>();
        for (Field field : getAllFields(clazz)) {
            names.add(field.getName());
        }
        return names;
    }

    /**
     * 获取类的所有字段名（通过类名）
     *
     * @param className 完整类名
     * @return 字段名列表
     */
    public static List<String> getFieldNames(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return getFieldNames(clazz);
        } catch (ClassNotFoundException e) {
            log.error("Class not found: {}", className, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取类的所有字段（包括父类）
     */
    public static List<Field> getAllFields(Class<?> clazz) {
        if (FIELD_CACHE.containsKey(clazz)) {
            return FIELD_CACHE.get(clazz);
        }

        List<Field> fields = new ArrayList<>();
        Class<?> currentClass = clazz;

        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                fields.add(field);
            }
            currentClass = currentClass.getSuperclass();
        }

        FIELD_CACHE.put(clazz, fields);
        return fields;
    }

    /**
     * 从XML元素填充对象属性
     *
     * @param element XML元素
     * @param target 目标对象
     * @param <T> 对象类型
     */
    public static <T> void populateFromXml(Element element, T target) {
        populateFromXml(element, target, null);
    }

    /**
     * 从XML元素填充对象属性（带自定义处理器）
     *
     * @param element XML元素
     * @param target 目标对象
     * @param customHandler 自定义字段处理器
     * @param <T> 对象类型
     */
    public static <T> void populateFromXml(Element element, T target,
                                            FieldHandler customHandler) {
        List<Field> fields = getAllFields(target.getClass());

        for (Field field : fields) {
            String fieldName = field.getName();
            Element childElement = element.element(fieldName);

            if (childElement == null) {
                continue;
            }

            field.setAccessible(true);

            try {
                // 检查是否有自定义处理器
                if (customHandler != null && customHandler.canHandle(field)) {
                    Object value = customHandler.handle(field, childElement);
                    if (value != null) {
                        field.set(target, value);
                    }
                    continue;
                }

                // 默认处理：字符串类型
                Class<?> fieldType = field.getType();
                if (fieldType == String.class) {
                    field.set(target, childElement.getText());
                } else if (fieldType == Integer.class || fieldType == int.class) {
                    String text = childElement.getText();
                    if (text != null && !text.isEmpty()) {
                        field.set(target, Integer.parseInt(text));
                    }
                } else if (fieldType == Long.class || fieldType == long.class) {
                    String text = childElement.getText();
                    if (text != null && !text.isEmpty()) {
                        field.set(target, Long.parseLong(text));
                    }
                } else if (fieldType == Double.class || fieldType == double.class) {
                    String text = childElement.getText();
                    if (text != null && !text.isEmpty()) {
                        field.set(target, Double.parseDouble(text));
                    }
                } else if (fieldType == Boolean.class || fieldType == boolean.class) {
                    String text = childElement.getText();
                    if (text != null && !text.isEmpty()) {
                        field.set(target, Boolean.parseBoolean(text));
                    }
                }
                // List类型需要自定义处理器
            } catch (Exception e) {
                log.warn("Failed to set field {} from XML: {}", fieldName, e.getMessage());
            }
        }
    }

    /**
     * 将对象转换为XML字符串
     *
     * @param target 目标对象
     * @param indent 缩进字符串
     * @param excludeFields 要排除的字段名
     * @param <T> 对象类型
     * @return XML字符串
     */
    public static <T> String toXmlString(T target, String indent, String... excludeFields) {
        StringBuilder sb = new StringBuilder();
        List<Field> fields = getAllFields(target.getClass());

        List<String> excludeList = new ArrayList<>();
        for (String field : excludeFields) {
            excludeList.add(field);
        }

        for (Field field : fields) {
            String fieldName = field.getName();

            // 跳过排除的字段
            if (excludeList.contains(fieldName)) {
                continue;
            }

            field.setAccessible(true);

            try {
                Object value = field.get(target);
                if (value != null) {
                    String text = value.toString().trim();
                    if (!text.isEmpty()) {
                        sb.append(indent)
                          .append("<").append(fieldName).append(">")
                          .append(escapeXml(text))
                          .append("</").append(fieldName).append(">\r\n");
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get field {} value: {}", fieldName, e.getMessage());
            }
        }

        return sb.toString();
    }

    /**
     * 将对象转换为Map
     *
     * @param target 目标对象
     * @param <T> 对象类型
     * @return 字段名到值的映射
     */
    public static <T> Map<String, Object> toMap(T target) {
        Map<String, Object> map = new HashMap<>();
        List<Field> fields = getAllFields(target.getClass());

        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(target);
                if (value != null) {
                    map.put(field.getName(), value);
                }
            } catch (Exception e) {
                log.warn("Failed to get field {} value: {}", field.getName(), e.getMessage());
            }
        }

        return map;
    }

    /**
     * 从Map填充对象
     *
     * @param map 源Map
     * @param target 目标对象
     * @param <T> 对象类型
     */
    public static <T> void populateFromMap(Map<String, Object> map, T target) {
        List<Field> fields = getAllFields(target.getClass());

        for (Field field : fields) {
            String fieldName = field.getName();
            if (!map.containsKey(fieldName)) {
                continue;
            }

            field.setAccessible(true);
            Object value = map.get(fieldName);

            try {
                if (value != null) {
                    Class<?> fieldType = field.getType();
                    if (fieldType.isAssignableFrom(value.getClass())) {
                        field.set(target, value);
                    } else if (fieldType == String.class) {
                        field.set(target, value.toString());
                    }
                    // 可以添加更多类型转换
                }
            } catch (Exception e) {
                log.warn("Failed to set field {} from map: {}", fieldName, e.getMessage());
            }
        }
    }

    /**
     * 复制对象的所有字段到另一个对象
     *
     * @param source 源对象
     * @param target 目标对象
     * @param <T> 对象类型
     */
    public static <T> void copyFields(T source, T target) {
        List<Field> fields = getAllFields(source.getClass());

        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(source);
                field.set(target, value);
            } catch (Exception e) {
                log.warn("Failed to copy field {}: {}", field.getName(), e.getMessage());
            }
        }
    }

    /**
     * 获取字段的泛型类型
     *
     * @param field 字段
     * @return 泛型类型，如果不是泛型则返回null
     */
    public static Class<?> getGenericType(Field field) {
        Type genericType = field.getGenericType();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] actualTypes = pt.getActualTypeArguments();
            if (actualTypes.length > 0 && actualTypes[0] instanceof Class) {
                return (Class<?>) actualTypes[0];
            }
        }
        return null;
    }

    /**
     * 转义XML特殊字符
     */
    private static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    /**
     * 清除字段缓存
     */
    public static void clearCache() {
        FIELD_CACHE.clear();
    }

    /**
     * 自定义字段处理器接口
     */
    public interface FieldHandler {
        /**
         * 检查是否可以处理该字段
         */
        boolean canHandle(Field field);

        /**
         * 处理字段并返回值
         */
        Object handle(Field field, Element element);
    }
}
