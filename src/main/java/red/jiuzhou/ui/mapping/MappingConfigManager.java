package red.jiuzhou.ui.mapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 映射配置管理器
 *
 * 管理表映射的权重规则、路径优先级和批量操作配置
 *
 * @author Claude
 * @version 1.0
 */
public class MappingConfigManager {

    private static final Logger log = LoggerFactory.getLogger(MappingConfigManager.class);

    // ==================== 表名权重规则 ====================

    /**
     * 表名权重规则
     * 匹配特定模式的表名会获得额外的权重加成
     */
    public static class TableWeightRule {
        public final String pattern;           // 正则表达式模式
        public final double weightBonus;       // 权重加成 (0.0 - 1.0)
        public final String description;       // 规则描述
        private final Pattern compiledPattern;

        public TableWeightRule(String pattern, double weightBonus, String description) {
            this.pattern = pattern;
            this.weightBonus = Math.max(0.0, Math.min(1.0, weightBonus));
            this.description = description;
            this.compiledPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        }

        public boolean matches(String tableName) {
            return compiledPattern.matcher(tableName).find();
        }
    }

    // 默认表名权重规则
    private static final List<TableWeightRule> TABLE_WEIGHT_RULES = new ArrayList<>();

    static {
        // strings相关表权重提升到80%
        TABLE_WEIGHT_RULES.add(new TableWeightRule(
            "^(client_)?string[s_].*",
            0.80,
            "strings相关表 - 高优先级匹配"
        ));

        // 本地化字符串表
        TABLE_WEIGHT_RULES.add(new TableWeightRule(
            "^(client_)?.*_string[s]?$",
            0.75,
            "本地化字符串表 - 较高优先级"
        ));

        // 核心数据表（item, npc, skill等）
        TABLE_WEIGHT_RULES.add(new TableWeightRule(
            "^(client_)?(item|npc|skill|quest|monster|world)[s_]?",
            0.70,
            "核心数据表 - 标准优先级"
        ));

        // 配置表
        TABLE_WEIGHT_RULES.add(new TableWeightRule(
            "^(client_)?.*_(config|setting|option)[s]?$",
            0.65,
            "配置表 - 标准优先级"
        ));
    }

    /**
     * 获取表名的权重加成
     *
     * @param tableName 表名
     * @return 权重加成值 (0.0 表示无加成)
     */
    public static double getTableWeightBonus(String tableName) {
        for (TableWeightRule rule : TABLE_WEIGHT_RULES) {
            if (rule.matches(tableName)) {
                log.debug("表 {} 匹配规则 '{}', 权重加成: {:.0f}%",
                    tableName, rule.description, rule.weightBonus * 100);
                return rule.weightBonus;
            }
        }
        return 0.0;
    }

    /**
     * 添加自定义权重规则
     */
    public static void addTableWeightRule(String pattern, double weightBonus, String description) {
        TABLE_WEIGHT_RULES.add(0, new TableWeightRule(pattern, weightBonus, description));
        log.info("添加表名权重规则: {} -> {:.0f}%", pattern, weightBonus * 100);
    }

    /**
     * 获取所有权重规则
     */
    public static List<TableWeightRule> getTableWeightRules() {
        return Collections.unmodifiableList(TABLE_WEIGHT_RULES);
    }

    // ==================== 路径优先级规则 ====================

    /**
     * XML路径优先级
     * 优先级从高到低排序
     */
    public static class PathPriority {
        public final String pathPattern;       // 路径模式
        public final int priority;             // 优先级 (数字越大优先级越高)
        public final String description;

        public PathPriority(String pathPattern, int priority, String description) {
            this.pathPattern = pathPattern;
            this.priority = priority;
            this.description = description;
        }
    }

    // 默认路径优先级规则
    private static final List<PathPriority> PATH_PRIORITIES = new ArrayList<>();

    static {
        // China目录最高优先级
        PATH_PRIORITIES.add(new PathPriority("China", 100, "中国本地化目录"));
        PATH_PRIORITIES.add(new PathPriority("china", 100, "中国本地化目录(小写)"));

        // 其他本地化目录
        PATH_PRIORITIES.add(new PathPriority("Localized", 90, "本地化目录"));
        PATH_PRIORITIES.add(new PathPriority("Local", 85, "本地目录"));

        // 默认XML目录
        PATH_PRIORITIES.add(new PathPriority("XML", 50, "默认XML目录"));
    }

    /**
     * 根据路径获取优先级
     */
    public static int getPathPriority(String path) {
        if (path == null) return 0;

        for (PathPriority pp : PATH_PRIORITIES) {
            if (path.contains(pp.pathPattern)) {
                return pp.priority;
            }
        }
        return 0;
    }

    /**
     * 在多个路径中选择优先级最高的
     */
    public static String selectBestPath(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return null;
        }

        String bestPath = paths.get(0);
        int bestPriority = getPathPriority(bestPath);

        for (String path : paths) {
            int priority = getPathPriority(path);
            if (priority > bestPriority) {
                bestPriority = priority;
                bestPath = path;
            }
        }

        log.debug("从 {} 个路径中选择: {} (优先级: {})", paths.size(), bestPath, bestPriority);
        return bestPath;
    }

    /**
     * 查找文件，优先从China目录
     *
     * @param basePath 基础路径
     * @param fileName 文件名
     * @return 找到的文件路径，优先China目录
     */
    public static Path findFileWithPriority(String basePath, String fileName) {
        if (basePath == null || fileName == null) {
            return null;
        }

        // 优先查找的目录顺序
        String[] priorityDirs = {"China", "china", "Localized", ""};

        for (String dir : priorityDirs) {
            Path searchPath;
            if (dir.isEmpty()) {
                searchPath = Paths.get(basePath, fileName);
            } else {
                searchPath = Paths.get(basePath, dir, fileName);
            }

            if (Files.exists(searchPath)) {
                log.debug("找到文件: {} (目录: {})", searchPath, dir.isEmpty() ? "根目录" : dir);
                return searchPath;
            }
        }

        log.warn("未找到文件: {} (基础路径: {})", fileName, basePath);
        return null;
    }

    /**
     * 查找所有匹配的文件，按优先级排序
     */
    public static List<Path> findAllFilesWithPriority(String basePath, String filePattern) {
        List<Path> results = new ArrayList<>();

        if (basePath == null) {
            return results;
        }

        try {
            Path base = Paths.get(basePath);
            if (!Files.exists(base)) {
                return results;
            }

            // 递归查找所有匹配的文件
            Files.walk(base)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().matches(filePattern))
                .forEach(results::add);

            // 按路径优先级排序（优先级高的在前）
            results.sort((p1, p2) -> {
                int pri1 = getPathPriority(p1.toString());
                int pri2 = getPathPriority(p2.toString());
                return Integer.compare(pri2, pri1);
            });

        } catch (Exception e) {
            log.error("查找文件失败: {} / {}", basePath, filePattern, e);
        }

        return results;
    }

    // ==================== 表间关系识别 ====================

    /**
     * 表间关系类型
     */
    public enum TableRelationType {
        PARENT_CHILD("父子关系", "主表-子表关系，通过外键关联"),
        REFERENCE("引用关系", "通过ID字段引用"),
        LOCALIZATION("本地化关系", "主表与本地化字符串表"),
        SIBLING("兄弟关系", "同一主表的不同子表"),
        UNKNOWN("未知关系", "无法确定的关系");

        public final String displayName;
        public final String description;

        TableRelationType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    /**
     * 表间关系
     */
    public static class TableRelation {
        public final String sourceTable;
        public final String targetTable;
        public final TableRelationType relationType;
        public final String relationField;    // 关联字段
        public final double confidence;       // 置信度 0-1

        public TableRelation(String sourceTable, String targetTable,
                           TableRelationType relationType, String relationField,
                           double confidence) {
            this.sourceTable = sourceTable;
            this.targetTable = targetTable;
            this.relationType = relationType;
            this.relationField = relationField;
            this.confidence = confidence;
        }

        @Override
        public String toString() {
            return String.format("%s -> %s (%s, 字段:%s, 置信度:%.0f%%)",
                sourceTable, targetTable, relationType.displayName,
                relationField, confidence * 100);
        }
    }

    // 常见的引用字段模式
    private static final Map<String, String> REFERENCE_FIELD_PATTERNS = new LinkedHashMap<>();

    static {
        // ID引用模式
        REFERENCE_FIELD_PATTERNS.put("item_id", "item");
        REFERENCE_FIELD_PATTERNS.put("npc_id", "npc");
        REFERENCE_FIELD_PATTERNS.put("skill_id", "skill");
        REFERENCE_FIELD_PATTERNS.put("quest_id", "quest");
        REFERENCE_FIELD_PATTERNS.put("monster_id", "monster");
        REFERENCE_FIELD_PATTERNS.put("world_id", "world");
        REFERENCE_FIELD_PATTERNS.put("map_id", "world");
        REFERENCE_FIELD_PATTERNS.put("zone_id", "zone");

        // 字符串引用
        REFERENCE_FIELD_PATTERNS.put("name_id", "string");
        REFERENCE_FIELD_PATTERNS.put("desc_id", "string");
        REFERENCE_FIELD_PATTERNS.put("description_id", "string");
        REFERENCE_FIELD_PATTERNS.put("title_id", "string");
    }

    /**
     * 检测表间引用关系
     */
    public static List<TableRelation> detectTableRelations(
            DatabaseTableScanner.TableInfo table,
            List<DatabaseTableScanner.TableInfo> allTables) {

        List<TableRelation> relations = new ArrayList<>();

        for (DatabaseTableScanner.ColumnInfo column : table.getColumns()) {
            String columnName = column.getColumnName().toLowerCase();

            // 检查是否匹配已知的引用模式
            for (Map.Entry<String, String> entry : REFERENCE_FIELD_PATTERNS.entrySet()) {
                if (columnName.equals(entry.getKey()) ||
                    columnName.endsWith("_" + entry.getKey())) {

                    String targetTablePattern = entry.getValue();

                    // 查找目标表
                    for (DatabaseTableScanner.TableInfo targetTable : allTables) {
                        String targetName = targetTable.getTableName().toLowerCase();
                        if (targetName.contains(targetTablePattern) &&
                            !targetName.equals(table.getTableName().toLowerCase())) {

                            TableRelationType relType = entry.getKey().contains("_id") ?
                                TableRelationType.REFERENCE : TableRelationType.LOCALIZATION;

                            double confidence = calculateRelationConfidence(
                                column, table, targetTable);

                            relations.add(new TableRelation(
                                table.getTableName(),
                                targetTable.getTableName(),
                                relType,
                                column.getColumnName(),
                                confidence
                            ));
                        }
                    }
                }
            }
        }

        // 检测父子关系（通过表名判断）
        String baseName = table.getTableName().replace("client_", "");
        for (DatabaseTableScanner.TableInfo other : allTables) {
            String otherName = other.getTableName().replace("client_", "");

            if (!otherName.equals(baseName)) {
                // 检查是否为子表
                if (otherName.startsWith(baseName + "_") ||
                    otherName.startsWith(baseName + "__")) {

                    relations.add(new TableRelation(
                        table.getTableName(),
                        other.getTableName(),
                        TableRelationType.PARENT_CHILD,
                        "id",
                        0.90
                    ));
                }
                // 检查是否为兄弟表
                else if (baseName.contains("_") && otherName.contains("_")) {
                    String[] baseParts = baseName.split("_");
                    String[] otherParts = otherName.split("_");
                    if (baseParts.length >= 2 && otherParts.length >= 2 &&
                        baseParts[0].equals(otherParts[0])) {

                        relations.add(new TableRelation(
                            table.getTableName(),
                            other.getTableName(),
                            TableRelationType.SIBLING,
                            null,
                            0.70
                        ));
                    }
                }
            }
        }

        return relations;
    }

    /**
     * 计算关系置信度
     */
    private static double calculateRelationConfidence(
            DatabaseTableScanner.ColumnInfo sourceField,
            DatabaseTableScanner.TableInfo sourceTable,
            DatabaseTableScanner.TableInfo targetTable) {

        double confidence = 0.5;

        // 如果是主键引用，置信度更高
        if (sourceField.isPrimaryKey()) {
            confidence += 0.2;
        }

        // 如果字段类型是整数类型（通常用于ID），置信度更高
        String dataType = sourceField.getDataType().toLowerCase();
        if (dataType.contains("int") || dataType.contains("bigint")) {
            confidence += 0.15;
        }

        // 如果目标表有对应的主键，置信度更高
        for (DatabaseTableScanner.ColumnInfo col : targetTable.getColumns()) {
            if (col.isPrimaryKey() && col.getColumnName().equalsIgnoreCase("id")) {
                confidence += 0.15;
            }
        }

        return Math.min(1.0, confidence);
    }

    // ==================== 批量操作支持 ====================

    /**
     * 批量操作类型
     */
    public enum BatchOperationType {
        GENERATE_DDL("生成DDL", "生成CREATE TABLE语句"),
        IMPORT_XML_TO_DB("导入到数据库", "将XML数据导入数据库"),
        EXPORT_DB_TO_XML("导出到XML", "将数据库数据导出为XML"),
        VALIDATE_MAPPING("验证映射", "验证表映射关系"),
        SYNC_STRUCTURE("同步结构", "同步表结构"),
        COMPARE_DATA("对比数据", "对比客户端和服务端数据");

        public final String displayName;
        public final String description;

        BatchOperationType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    /**
     * 批量操作结果
     */
    public static class BatchOperationResult {
        public final BatchOperationType operationType;
        public int totalCount;
        public int successCount;
        public int failedCount;
        public List<String> successItems = new ArrayList<>();
        public List<String> failedItems = new ArrayList<>();
        public Map<String, String> errorMessages = new HashMap<>();
        public long executionTimeMs;

        public BatchOperationResult(BatchOperationType operationType) {
            this.operationType = operationType;
        }

        public void recordSuccess(String item) {
            successCount++;
            successItems.add(item);
        }

        public void recordFailure(String item, String errorMessage) {
            failedCount++;
            failedItems.add(item);
            errorMessages.put(item, errorMessage);
        }

        public double getSuccessRate() {
            return totalCount > 0 ? (double) successCount / totalCount : 0;
        }

        public String getSummary() {
            return String.format("%s: 总计%d项, 成功%d项, 失败%d项 (%.1f%%), 耗时%.2f秒",
                operationType.displayName,
                totalCount, successCount, failedCount,
                getSuccessRate() * 100,
                executionTimeMs / 1000.0);
        }
    }

    /**
     * 获取可批量操作的表列表
     */
    public static List<String> getTablesForBatchOperation(
            String directoryPath,
            BatchOperationType operationType) {

        List<String> tables = new ArrayList<>();

        try {
            Path dir = Paths.get(directoryPath);
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return tables;
            }

            // 根据操作类型筛选
            Files.list(dir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                .forEach(p -> {
                    String fileName = p.getFileName().toString();
                    // 移除.xml后缀作为表名
                    String tableName = fileName.substring(0, fileName.length() - 4);
                    tables.add(tableName);
                });

            // 按优先级排序
            tables.sort((t1, t2) -> {
                double w1 = getTableWeightBonus(t1);
                double w2 = getTableWeightBonus(t2);
                return Double.compare(w2, w1);
            });

        } catch (Exception e) {
            log.error("获取批量操作表列表失败: {}", directoryPath, e);
        }

        return tables;
    }
}
