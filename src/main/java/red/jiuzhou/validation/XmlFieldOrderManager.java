package red.jiuzhou.validation;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * XML字段顺序管理器
 *
 * <p>负责管理和维护XML字段的正确顺序，确保导入导出的往返一致性。
 *
 * <p>核心功能：
 * <ul>
 *   <li>从table_structure_cache.json加载字段定义顺序（ordinalPosition）</li>
 *   <li>提供字段排序服务，确保XML输出顺序稳定</li>
 *   <li>过滤黑名单字段后，保持剩余字段的相对顺序</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>ID字段始终排在最前面</li>
 *   <li>黑名单字段（如__order_index）自动过滤</li>
 *   <li>其他字段按照数据库定义的ordinalPosition排序</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class XmlFieldOrderManager {

    private static final Logger log = LoggerFactory.getLogger(XmlFieldOrderManager.class);

    /**
     * 缓存：表名 -> 字段顺序映射（字段名 -> ordinalPosition）
     */
    private static final Map<String, Map<String, Integer>> TABLE_FIELD_ORDER_CACHE = new ConcurrentHashMap<>();

    /**
     * 缓存：表名 -> 有序字段列表（已过滤黑名单）
     */
    private static final Map<String, List<String>> TABLE_ORDERED_FIELDS_CACHE = new ConcurrentHashMap<>();

    /**
     * 缓存文件路径
     */
    private static final String CACHE_FILE_PATH = "cache/table_structure_cache.json";

    /**
     * 是否已初始化
     */
    private static volatile boolean initialized = false;

    /**
     * 初始化字段顺序管理器
     *
     * @return true表示初始化成功，false表示失败
     */
    public static synchronized boolean initialize() {
        if (initialized) {
            return true;
        }

        try {
            String content = Files.readString(Paths.get(CACHE_FILE_PATH));
            JSONObject root = JSON.parseObject(content);
            JSONArray tables = root.getJSONArray("tables");

            if (tables == null) {
                log.error("table_structure_cache.json 格式错误：缺少 tables 数组");
                return false;
            }

            int tableCount = 0;
            int fieldCount = 0;

            for (int i = 0; i < tables.size(); i++) {
                JSONObject table = tables.getJSONObject(i);
                String tableName = table.getString("tableName");
                JSONArray columns = table.getJSONArray("columns");

                if (tableName == null || columns == null) {
                    continue;
                }

                Map<String, Integer> fieldOrderMap = new HashMap<>();

                for (int j = 0; j < columns.size(); j++) {
                    JSONObject column = columns.getJSONObject(j);
                    String columnName = column.getString("columnName");
                    Integer ordinalPosition = column.getInteger("ordinalPosition");

                    if (columnName != null && ordinalPosition != null) {
                        fieldOrderMap.put(columnName, ordinalPosition);
                        fieldCount++;
                    }
                }

                TABLE_FIELD_ORDER_CACHE.put(tableName, fieldOrderMap);
                tableCount++;
            }

            initialized = true;
            log.info("✅ 字段顺序管理器初始化成功：加载 {} 个表，{} 个字段", tableCount, fieldCount);
            return true;

        } catch (IOException e) {
            log.error("❌ 无法读取 table_structure_cache.json 文件", e);
            return false;
        } catch (Exception e) {
            log.error("❌ 解析 table_structure_cache.json 文件失败", e);
            return false;
        }
    }

    /**
     * 获取表的有序字段列表（已过滤黑名单，ID字段优先）
     *
     * @param tableName 表名
     * @return 有序字段列表
     */
    public static List<String> getOrderedFields(String tableName) {
        if (!initialized) {
            initialize();
        }

        // 检查缓存
        if (TABLE_ORDERED_FIELDS_CACHE.containsKey(tableName)) {
            return TABLE_ORDERED_FIELDS_CACHE.get(tableName);
        }

        Map<String, Integer> fieldOrderMap = TABLE_FIELD_ORDER_CACHE.get(tableName);
        if (fieldOrderMap == null || fieldOrderMap.isEmpty()) {
            log.warn("表 {} 没有字段顺序定义", tableName);
            return Collections.emptyList();
        }

        // 1. 过滤黑名单字段
        List<String> fields = fieldOrderMap.keySet().stream()
                .filter(field -> !XmlFieldBlacklist.shouldFilter(tableName, field))
                .collect(Collectors.toList());

        // 2. 按照ordinalPosition排序
        fields.sort(Comparator.comparingInt(fieldOrderMap::get));

        // 3. 确保ID字段排在最前面
        List<String> orderedFields = new ArrayList<>();
        for (String idField : Arrays.asList("id", "_attr_id", "ID")) {
            if (fields.contains(idField)) {
                orderedFields.add(idField);
                fields.remove(idField);
            }
        }
        orderedFields.addAll(fields);

        // 缓存结果
        TABLE_ORDERED_FIELDS_CACHE.put(tableName, orderedFields);

        return orderedFields;
    }

    /**
     * 对字段集合进行排序（按照数据库定义顺序，并过滤黑名单）
     *
     * @param tableName 表名
     * @param fields    原始字段集合
     * @return 排序后的字段列表（LinkedHashSet保持顺序，已过滤黑名单）
     */
    public static Set<String> sortFields(String tableName, Set<String> fields) {
        if (!initialized) {
            initialize();
        }

        Map<String, Integer> fieldOrderMap = TABLE_FIELD_ORDER_CACHE.get(tableName);
        if (fieldOrderMap == null || fieldOrderMap.isEmpty()) {
            log.warn("表 {} 没有字段顺序定义，使用原始顺序", tableName);
            // 即使没有顺序定义，也要过滤黑名单
            return fields.stream()
                    .filter(field -> !XmlFieldBlacklist.shouldFilter(tableName, field))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        // 分离已知字段和未知字段（同时过滤黑名单）
        List<String> knownFields = new ArrayList<>();
        List<String> unknownFields = new ArrayList<>();

        for (String field : fields) {
            // ==================== 过滤黑名单字段 ====================
            if (XmlFieldBlacklist.shouldFilter(tableName, field)) {
                continue;
            }

            if (fieldOrderMap.containsKey(field)) {
                knownFields.add(field);
            } else {
                unknownFields.add(field);
            }
        }

        // 按照ordinalPosition排序已知字段
        knownFields.sort(Comparator.comparingInt(field -> fieldOrderMap.getOrDefault(field, Integer.MAX_VALUE)));

        // ID字段优先
        List<String> result = new ArrayList<>();
        for (String idField : Arrays.asList("id", "_attr_id", "ID")) {
            if (knownFields.contains(idField)) {
                result.add(idField);
                knownFields.remove(idField);
            }
        }

        // 添加剩余已知字段
        result.addAll(knownFields);

        // 添加未知字段（保持原始顺序）
        result.addAll(unknownFields);

        return new LinkedHashSet<>(result);
    }

    /**
     * 获取字段的ordinalPosition
     *
     * @param tableName 表名
     * @param fieldName 字段名
     * @return ordinalPosition，如果字段不存在则返回null
     */
    public static Integer getFieldPosition(String tableName, String fieldName) {
        if (!initialized) {
            initialize();
        }

        Map<String, Integer> fieldOrderMap = TABLE_FIELD_ORDER_CACHE.get(tableName);
        if (fieldOrderMap == null) {
            return null;
        }

        return fieldOrderMap.get(fieldName);
    }

    /**
     * 清除缓存（用于测试或重新加载）
     */
    public static synchronized void clearCache() {
        TABLE_FIELD_ORDER_CACHE.clear();
        TABLE_ORDERED_FIELDS_CACHE.clear();
        initialized = false;
        log.info("字段顺序管理器缓存已清除");
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public static String getStatistics() {
        if (!initialized) {
            initialize();
        }

        int totalTables = TABLE_FIELD_ORDER_CACHE.size();
        int totalFields = TABLE_FIELD_ORDER_CACHE.values().stream()
                .mapToInt(Map::size)
                .sum();
        int cachedOrderedFields = TABLE_ORDERED_FIELDS_CACHE.size();

        return String.format("表: %d, 字段: %d, 缓存的有序字段列表: %d",
                totalTables, totalFields, cachedOrderedFields);
    }
}
