package red.jiuzhou.agent.texttosql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Type字段自动发现机制
 *
 * 扫描数据库中所有包含"type"的字段，分析其值域和语义
 * 为AI助手提供动态的、与实际数据匹配的上下文
 *
 * @author Claude
 * @date 2025-12-20
 */
public class TypeFieldDiscovery {

    private static final Logger log = LoggerFactory.getLogger(TypeFieldDiscovery.class);

    private final JdbcTemplate jdbcTemplate;

    /**
     * Type字段信息
     */
    public static class TypeFieldInfo {
        private String tableName;        // 表名
        private String columnName;       // 字段名
        private String columnType;       // 字段类型（varchar, int等）
        private String columnComment;    // 字段注释
        private List<ValueInfo> values;  // 值域信息
        private int totalCount;          // 总记录数
        private SemanticType semanticType; // 语义类型

        public TypeFieldInfo(String tableName, String columnName, String columnType, String columnComment) {
            this.tableName = tableName;
            this.columnName = columnName;
            this.columnType = columnType;
            this.columnComment = columnComment;
            this.values = new ArrayList<>();
        }

        // Getters
        public String getTableName() { return tableName; }
        public String getColumnName() { return columnName; }
        public String getColumnType() { return columnType; }
        public String getColumnComment() { return columnComment; }
        public List<ValueInfo> getValues() { return values; }
        public int getTotalCount() { return totalCount; }
        public SemanticType getSemanticType() { return semanticType; }

        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
        public void setSemanticType(SemanticType semanticType) { this.semanticType = semanticType; }

        /**
         * 获取字段的完整标识（表名.字段名）
         */
        public String getFullName() {
            return tableName + "." + columnName;
        }

        /**
         * 判断是否为枚举类型（值域较小）
         */
        public boolean isEnumLike() {
            return values.size() <= 20 && values.size() > 0;
        }
    }

    /**
     * 值域信息
     */
    public static class ValueInfo {
        private Object value;      // 值
        private int count;         // 出现次数
        private double percentage; // 占比

        public ValueInfo(Object value, int count, double percentage) {
            this.value = value;
            this.count = count;
            this.percentage = percentage;
        }

        public Object getValue() { return value; }
        public int getCount() { return count; }
        public double getPercentage() { return percentage; }

        public String getValueString() {
            return value != null ? value.toString() : "NULL";
        }
    }

    /**
     * 语义类型
     */
    public enum SemanticType {
        ELEMENT("元素属性"),        // fire, water, wind等
        QUALITY("品质等级"),        // common, rare, epic等
        NPC_RANK("NPC等级"),       // normal, elite, boss等
        CLASS("职业类别"),         // warrior, mage等
        CATEGORY("分类标识"),      // 通用分类
        BOOLEAN_FLAG("布尔标志"),  // 0/1, true/false
        UNKNOWN("未知类型");       // 无法识别

        private final String description;

        SemanticType(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    public TypeFieldDiscovery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 发现所有type字段
     */
    public List<TypeFieldInfo> discoverAllTypeFields() {
        log.info("开始扫描数据库中的type字段...");
        List<TypeFieldInfo> results = new ArrayList<>();

        try {
            // 查询所有包含"type"的字段
            String sql = "SELECT table_name, column_name, column_type, column_comment " +
                        "FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() " +
                        "AND (LOWER(column_name) LIKE '%type%' " +
                        "     OR LOWER(column_name) LIKE '%kind%' " +
                        "     OR LOWER(column_name) LIKE '%category%' " +
                        "     OR LOWER(column_name) = 'rank' " +
                        "     OR LOWER(column_name) = 'grade' " +
                        "     OR LOWER(column_name) = 'level' " +
                        "     OR LOWER(column_name) = 'quality' " +
                        "     OR LOWER(column_name) = 'rarity' " +
                        "     OR LOWER(column_name) = 'element' " +
                        "     OR LOWER(column_name) = 'class') " +
                        "ORDER BY table_name, column_name";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> row : rows) {
                String tableName = (String) row.get("table_name");
                String columnName = (String) row.get("column_name");
                String columnType = (String) row.get("column_type");
                String columnComment = (String) row.get("column_comment");

                TypeFieldInfo fieldInfo = new TypeFieldInfo(tableName, columnName, columnType, columnComment);

                // 分析字段值域
                analyzeFieldValues(fieldInfo);

                // 推断语义类型
                fieldInfo.setSemanticType(inferSemanticType(fieldInfo));

                results.add(fieldInfo);
            }

            log.info("发现 {} 个type相关字段", results.size());
        } catch (Exception e) {
            log.error("发现type字段失败", e);
        }

        return results;
    }

    /**
     * 分析字段的值域
     */
    private void analyzeFieldValues(TypeFieldInfo fieldInfo) {
        try {
            String tableName = fieldInfo.getTableName();
            String columnName = fieldInfo.getColumnName();

            // 查询总记录数
            String countSql = String.format("SELECT COUNT(*) FROM %s", tableName);
            Integer totalCount = jdbcTemplate.queryForObject(countSql, Integer.class);
            fieldInfo.setTotalCount(totalCount != null ? totalCount : 0);

            if (fieldInfo.getTotalCount() == 0) {
                return;
            }

            // 查询值域分布（最多取前50个不同值）
            String valueSql = String.format(
                "SELECT %s as value, COUNT(*) as count " +
                "FROM %s " +
                "WHERE %s IS NOT NULL " +
                "GROUP BY %s " +
                "ORDER BY count DESC " +
                "LIMIT 50",
                columnName, tableName, columnName, columnName
            );

            List<Map<String, Object>> valueRows = jdbcTemplate.queryForList(valueSql);

            for (Map<String, Object> row : valueRows) {
                Object value = row.get("value");
                Number countObj = (Number) row.get("count");
                int count = countObj != null ? countObj.intValue() : 0;
                double percentage = (double) count / fieldInfo.getTotalCount() * 100;

                fieldInfo.getValues().add(new ValueInfo(value, count, percentage));
            }

        } catch (Exception e) {
            log.warn("分析字段 {}.{} 的值域失败: {}",
                fieldInfo.getTableName(), fieldInfo.getColumnName(), e.getMessage());
        }
    }

    /**
     * 推断字段的语义类型
     */
    private SemanticType inferSemanticType(TypeFieldInfo fieldInfo) {
        String columnName = fieldInfo.getColumnName().toLowerCase();
        String columnComment = fieldInfo.getColumnComment() != null ?
            fieldInfo.getColumnComment().toLowerCase() : "";

        // 基于字段名推断
        if (columnName.contains("element") || columnComment.contains("元素") || columnComment.contains("属性")) {
            return SemanticType.ELEMENT;
        }
        if (columnName.contains("quality") || columnName.contains("rarity") ||
            columnComment.contains("品质") || columnComment.contains("稀有")) {
            return SemanticType.QUALITY;
        }
        if (columnName.contains("rank") || columnName.contains("grade") ||
            (columnName.contains("npc") && columnName.contains("type")) ||
            columnComment.contains("等级") || columnComment.contains("品级")) {
            return SemanticType.NPC_RANK;
        }
        if (columnName.contains("class") || columnComment.contains("职业") || columnComment.contains("类别")) {
            return SemanticType.CLASS;
        }
        if (columnName.contains("category") || columnName.contains("kind") ||
            columnComment.contains("分类") || columnComment.contains("类型")) {
            return SemanticType.CATEGORY;
        }

        // 基于值域推断
        if (fieldInfo.isEnumLike() && fieldInfo.getValues().size() == 2) {
            // 只有两个值，可能是布尔标志
            Set<String> valueStrings = new HashSet<>();
            for (ValueInfo vi : fieldInfo.getValues()) {
                valueStrings.add(vi.getValueString().toLowerCase());
            }
            if (valueStrings.contains("0") && valueStrings.contains("1") ||
                valueStrings.contains("true") && valueStrings.contains("false") ||
                valueStrings.contains("yes") && valueStrings.contains("no")) {
                return SemanticType.BOOLEAN_FLAG;
            }
        }

        // 检查是否为元素类型（通过值域）
        if (containsElementKeywords(fieldInfo.getValues())) {
            return SemanticType.ELEMENT;
        }

        // 检查是否为品质类型
        if (containsQualityKeywords(fieldInfo.getValues())) {
            return SemanticType.QUALITY;
        }

        // 检查是否为NPC等级
        if (containsRankKeywords(fieldInfo.getValues())) {
            return SemanticType.NPC_RANK;
        }

        return SemanticType.UNKNOWN;
    }

    /**
     * 检查值域是否包含元素关键词
     */
    private boolean containsElementKeywords(List<ValueInfo> values) {
        Set<String> elementKeywords = new HashSet<>(Arrays.asList(
            "fire", "water", "wind", "earth", "light", "dark", "ice", "thunder", "lightning"
        ));

        for (ValueInfo vi : values) {
            String val = vi.getValueString().toLowerCase();
            if (elementKeywords.contains(val)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查值域是否包含品质关键词
     */
    private boolean containsQualityKeywords(List<ValueInfo> values) {
        Set<String> qualityKeywords = new HashSet<>(Arrays.asList(
            "common", "uncommon", "rare", "epic", "legendary", "mythic",
            "normal", "magic", "unique", "set", "white", "green", "blue", "purple", "orange"
        ));

        for (ValueInfo vi : values) {
            String val = vi.getValueString().toLowerCase();
            if (qualityKeywords.contains(val)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查值域是否包含等级关键词
     */
    private boolean containsRankKeywords(List<ValueInfo> values) {
        Set<String> rankKeywords = new HashSet<>(Arrays.asList(
            "normal", "elite", "boss", "world_boss", "raid", "champion", "minion", "monster"
        ));

        for (ValueInfo vi : values) {
            String val = vi.getValueString().toLowerCase();
            if (rankKeywords.contains(val)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成字段的统计报告
     */
    public String generateReport(TypeFieldInfo fieldInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s.%s\n\n", fieldInfo.getTableName(), fieldInfo.getColumnName()));
        sb.append(String.format("- **字段类型**: %s\n", fieldInfo.getColumnType()));
        sb.append(String.format("- **字段注释**: %s\n", fieldInfo.getColumnComment() != null ? fieldInfo.getColumnComment() : "无"));
        sb.append(String.format("- **语义类型**: %s (%s)\n", fieldInfo.getSemanticType(), fieldInfo.getSemanticType().getDescription()));
        sb.append(String.format("- **总记录数**: %d\n", fieldInfo.getTotalCount()));
        sb.append(String.format("- **不同值数**: %d\n\n", fieldInfo.getValues().size()));

        if (!fieldInfo.getValues().isEmpty()) {
            sb.append("### 值域分布\n\n");
            sb.append("| 值 | 数量 | 占比 |\n");
            sb.append("|---|------|------|\n");

            int displayCount = Math.min(10, fieldInfo.getValues().size());
            for (int i = 0; i < displayCount; i++) {
                ValueInfo vi = fieldInfo.getValues().get(i);
                sb.append(String.format("| %s | %d | %.2f%% |\n",
                    vi.getValueString(), vi.getCount(), vi.getPercentage()));
            }

            if (fieldInfo.getValues().size() > displayCount) {
                sb.append(String.format("\n... 共 %d 个不同值\n", fieldInfo.getValues().size()));
            }
        }

        return sb.toString();
    }

    /**
     * 生成所有type字段的汇总报告
     */
    public String generateSummaryReport(List<TypeFieldInfo> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Type字段发现报告\n\n");
        sb.append(String.format("发现时间: %s\n\n", new java.util.Date()));
        sb.append(String.format("共发现 **%d** 个type相关字段\n\n", fields.size()));

        // 按语义类型分组
        Map<SemanticType, List<TypeFieldInfo>> grouped = new HashMap<>();
        for (TypeFieldInfo field : fields) {
            grouped.computeIfAbsent(field.getSemanticType(), k -> new ArrayList<>()).add(field);
        }

        sb.append("## 语义类型分布\n\n");
        for (SemanticType type : SemanticType.values()) {
            int count = grouped.getOrDefault(type, Collections.emptyList()).size();
            if (count > 0) {
                sb.append(String.format("- **%s** (%s): %d 个字段\n",
                    type, type.getDescription(), count));
            }
        }
        sb.append("\n");

        // 详细列表
        sb.append("## 字段详情\n\n");
        for (SemanticType type : SemanticType.values()) {
            List<TypeFieldInfo> typeFields = grouped.get(type);
            if (typeFields != null && !typeFields.isEmpty()) {
                sb.append(String.format("### %s (%s)\n\n", type, type.getDescription()));
                for (TypeFieldInfo field : typeFields) {
                    sb.append(String.format("- **%s**: %d个不同值",
                        field.getFullName(), field.getValues().size()));
                    if (field.isEnumLike()) {
                        sb.append(" [");
                        for (int i = 0; i < Math.min(5, field.getValues().size()); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(field.getValues().get(i).getValueString());
                        }
                        if (field.getValues().size() > 5) {
                            sb.append(", ...");
                        }
                        sb.append("]");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}
