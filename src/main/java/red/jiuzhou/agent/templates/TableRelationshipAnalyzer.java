package red.jiuzhou.agent.templates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 表关系分析器
 *
 * <p>分析数据库表之间的引用关系，支持：
 * <ul>
 *   <li>基于字段名模式的引用检测（item_id → items表）</li>
 *   <li>构建表依赖图</li>
 *   <li>查找关联表</li>
 *   <li>生成多表JOIN查询</li>
 * </ul>
 *
 * <p>设计原则：
 * <ol>
 *   <li>懒加载 - 按需分析表结构</li>
 *   <li>缓存优化 - 缓存分析结果避免重复查询</li>
 *   <li>游戏领域感知 - 内置Aion游戏表关系知识</li>
 * </ol>
 *
 * @author Claude
 * @version 1.0
 */
public class TableRelationshipAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(TableRelationshipAnalyzer.class);

    // 单例实例
    private static volatile TableRelationshipAnalyzer instance;

    // 数据库连接
    private final JdbcTemplate jdbcTemplate;

    // 表结构缓存: tableName -> List<ColumnInfo>
    private final Map<String, List<ColumnInfo>> tableColumnCache = new ConcurrentHashMap<>();

    // 表关系缓存: tableName -> List<TableRelation>
    private final Map<String, List<TableRelation>> relationCache = new ConcurrentHashMap<>();

    // 所有表名缓存
    private volatile List<String> allTableNames;

    // ==================== 引用模式配置 ====================

    /**
     * 字段名模式 -> 目标表信息
     * 按优先级排序，越具体的模式优先级越高
     */
    private static final List<ReferencePattern> REFERENCE_PATTERNS = new ArrayList<>();

    static {
        // ========== 物品系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^item_id$|^itemid$"),
            "ITEM", Arrays.asList("client_items", "item_templates"), "id", 100
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i).*item[_]?id.*"),
            "ITEM", Arrays.asList("client_items", "item_templates"), "id", 80
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^reward_item[_]?\\d*$|^give_item[_]?\\d*$"),
            "ITEM", Arrays.asList("client_items"), "id", 90
        ));

        // ========== NPC系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^npc_id$|^npcid$"),
            "NPC", Arrays.asList("client_npcs_npc", "client_npcs", "npc_templates"), "id", 100
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i).*npc[_]?id.*|.*monster[_]?id.*"),
            "NPC", Arrays.asList("client_npcs_npc", "client_npcs", "npc_templates"), "id", 80
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^target_npc$|^start_npc$|^end_npc$"),
            "NPC", Arrays.asList("client_npcs_npc"), "id", 95
        ));

        // ========== 技能系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^skill_id$|^skillid$"),
            "SKILL", Arrays.asList("client_skills", "skill_templates"), "id", 100
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i).*skill[_]?id.*"),
            "SKILL", Arrays.asList("client_skills", "skill_templates"), "id", 80
        ));

        // ========== 任务系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^quest_id$|^questid$"),
            "QUEST", Arrays.asList("client_quests", "quest_templates"), "id", 100
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i).*quest[_]?id.*|^prev_quest$|^next_quest$"),
            "QUEST", Arrays.asList("client_quests", "quest_templates"), "id", 80
        ));

        // ========== 商店系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^shop_id$|^shopid$"),
            "SHOP", Arrays.asList("shop_templates", "client_shops"), "id", 100
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i).*shop[_]?id.*|.*goods[_]?id.*"),
            "SHOP", Arrays.asList("shop_templates", "client_shops"), "id", 80
        ));

        // ========== 掉落系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^drop_id$|^dropid$"),
            "DROP", Arrays.asList("drop_templates", "drop_npc"), "id", 100
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i).*drop[_]?id.*"),
            "DROP", Arrays.asList("drop_templates", "drop_npc"), "id", 80
        ));

        // ========== 地图系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^map_id$|^mapid$|^world_id$|^worldid$"),
            "MAP", Arrays.asList("world", "world_maps", "client_world_maps"), "id", 100
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i).*map[_]?id.*|.*world[_]?id.*"),
            "MAP", Arrays.asList("world", "world_maps"), "id", 80
        ));

        // ========== 副本系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^instance_id$|^instanceid$"),
            "INSTANCE", Arrays.asList("instance_cooltime", "instance_templates"), "id", 100
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i).*instance[_]?id.*"),
            "INSTANCE", Arrays.asList("instance_cooltime"), "id", 80
        ));

        // ========== 称号系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^title_id$|^titleid$"),
            "TITLE", Arrays.asList("client_titles", "title_templates"), "id", 100
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i).*title[_]?id.*"),
            "TITLE", Arrays.asList("client_titles"), "id", 80
        ));

        // ========== 宠物系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^pet_id$|^petid$|^toypet_id$"),
            "PET", Arrays.asList("client_toypets", "toypet_templates"), "id", 100
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i).*pet[_]?id.*|.*toypet[_]?id.*"),
            "PET", Arrays.asList("client_toypets"), "id", 80
        ));

        // ========== 坐骑系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^ride_id$|^rideid$|^mount_id$"),
            "RIDE", Arrays.asList("client_rides", "ride_templates"), "id", 100
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i).*ride[_]?id.*|.*mount[_]?id.*"),
            "RIDE", Arrays.asList("client_rides"), "id", 80
        ));

        // ========== 配方系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^recipe_id$|^recipeid$"),
            "RECIPE", Arrays.asList("recipe_templates", "client_recipes"), "id", 100
        ));
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i).*recipe[_]?id.*"),
            "RECIPE", Arrays.asList("recipe_templates"), "id", 80
        ));

        // ========== Luna商店 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^luna_id$|^lunaid$|.*luna.*shop.*"),
            "LUNA", Arrays.asList("luna_shop", "luna_templates"), "id", 90
        ));

        // ========== 成就系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^achievement_id$|.*achieve.*id.*"),
            "ACHIEVEMENT", Arrays.asList("achievement_templates", "client_achievements"), "id", 85
        ));

        // ========== 效果/Buff系统 ==========
        REFERENCE_PATTERNS.add(new ReferencePattern(
            Pattern.compile("(?i)^effect_id$|^buff_id$|.*effect[_]?id.*|.*buff[_]?id.*"),
            "EFFECT", Arrays.asList("skill_effects", "buff_templates"), "id", 80
        ));

        // 按优先级排序（高优先级在前）
        REFERENCE_PATTERNS.sort((a, b) -> b.priority - a.priority);
    }

    // ==================== 游戏领域知识 ====================

    /**
     * 核心表的已知关联关系
     * 这些是预定义的高优先级关联，不需要通过字段名推断
     */
    private static final Map<String, List<KnownRelation>> KNOWN_RELATIONS = new HashMap<>();

    static {
        // 物品表的关联
        KNOWN_RELATIONS.put("client_items", Arrays.asList(
            new KnownRelation("client_quests", "reward_item", "任务奖励物品"),
            new KnownRelation("client_quests", "collect_item", "任务收集物品"),
            new KnownRelation("shop_templates", "item_id", "商店出售物品"),
            new KnownRelation("drop_templates", "item_id", "掉落物品"),
            new KnownRelation("recipe_templates", "product_item", "配方产物"),
            new KnownRelation("recipe_templates", "material_item", "配方材料"),
            new KnownRelation("luna_shop", "item_id", "Luna商店物品"),
            new KnownRelation("client_npcs_npc", "sell_item", "NPC出售物品")
        ));

        // NPC表的关联
        KNOWN_RELATIONS.put("client_npcs_npc", Arrays.asList(
            new KnownRelation("client_quests", "start_npc", "任务起始NPC"),
            new KnownRelation("client_quests", "end_npc", "任务结束NPC"),
            new KnownRelation("spawns", "npc_id", "NPC刷新点"),
            new KnownRelation("drop_npc", "npc_id", "NPC掉落"),
            new KnownRelation("shop_templates", "npc_id", "NPC商店")
        ));

        // 任务表的关联
        KNOWN_RELATIONS.put("client_quests", Arrays.asList(
            new KnownRelation("client_items", "reward_item", "任务奖励"),
            new KnownRelation("client_npcs_npc", "start_npc", "起始NPC"),
            new KnownRelation("client_npcs_npc", "end_npc", "结束NPC"),
            new KnownRelation("client_skills", "reward_skill", "奖励技能"),
            new KnownRelation("client_titles", "reward_title", "奖励称号")
        ));

        // 技能表的关联
        KNOWN_RELATIONS.put("client_skills", Arrays.asList(
            new KnownRelation("skill_effects", "effect_id", "技能效果"),
            new KnownRelation("client_items", "item_id", "技能消耗物品"),
            new KnownRelation("client_quests", "reward_skill", "任务奖励技能")
        ));

        // 商店表的关联
        KNOWN_RELATIONS.put("shop_templates", Arrays.asList(
            new KnownRelation("client_items", "item_id", "出售物品"),
            new KnownRelation("client_npcs_npc", "npc_id", "商店NPC")
        ));

        // 掉落表的关联
        KNOWN_RELATIONS.put("drop_templates", Arrays.asList(
            new KnownRelation("client_items", "item_id", "掉落物品"),
            new KnownRelation("client_npcs_npc", "npc_id", "掉落NPC")
        ));
    }

    // ==================== 构造函数和单例 ====================

    private TableRelationshipAnalyzer() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate(null);
    }

    public TableRelationshipAnalyzer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static TableRelationshipAnalyzer getInstance() {
        if (instance == null) {
            synchronized (TableRelationshipAnalyzer.class) {
                if (instance == null) {
                    instance = new TableRelationshipAnalyzer();
                }
            }
        }
        return instance;
    }

    // ==================== 核心分析方法 ====================

    /**
     * 获取表的所有关联关系
     *
     * @param tableName 表名
     * @return 关联关系列表
     */
    public List<TableRelation> getTableRelations(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return Collections.emptyList();
        }

        // 检查缓存
        if (relationCache.containsKey(tableName)) {
            return relationCache.get(tableName);
        }

        List<TableRelation> relations = new ArrayList<>();

        // 1. 添加已知的预定义关联
        addKnownRelations(tableName, relations);

        // 2. 基于字段名模式分析
        List<ColumnInfo> columns = getTableColumns(tableName);
        for (ColumnInfo column : columns) {
            Optional<TableRelation> relation = analyzeColumnReference(tableName, column);
            relation.ifPresent(r -> {
                // 避免重复
                if (!containsRelation(relations, r)) {
                    relations.add(r);
                }
            });
        }

        // 3. 查找反向引用（谁引用了这个表）
        addIncomingReferences(tableName, relations);

        // 按相关性排序
        relations.sort((a, b) -> Double.compare(b.confidence, a.confidence));

        // 缓存结果
        relationCache.put(tableName, relations);

        log.debug("分析表 {} 的关联关系，发现 {} 个关联", tableName, relations.size());
        return relations;
    }

    /**
     * 获取表的关联表名列表（用于快速查询）
     */
    public List<String> getRelatedTableNames(String tableName) {
        return getTableRelations(tableName).stream()
            .map(r -> r.targetTable)
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * 构建多表JOIN SQL
     *
     * @param mainTable 主表
     * @param selectFields 选择的字段（可选，默认 * ）
     * @param maxJoins 最大JOIN数量
     * @return JOIN SQL语句
     */
    public String buildMultiTableJoin(String mainTable, List<String> selectFields, int maxJoins) {
        List<TableRelation> relations = getTableRelations(mainTable);

        if (relations.isEmpty()) {
            String fields = selectFields != null && !selectFields.isEmpty()
                ? String.join(", ", selectFields) : "*";
            return String.format("SELECT %s FROM `%s` LIMIT 100", fields, mainTable);
        }

        // 选择最相关的关联
        List<TableRelation> topRelations = relations.stream()
            .filter(r -> r.relationType == RelationType.OUTGOING) // 只使用出向关联
            .limit(maxJoins)
            .collect(Collectors.toList());

        StringBuilder sql = new StringBuilder();

        // SELECT 子句
        if (selectFields != null && !selectFields.isEmpty()) {
            sql.append("SELECT ");
            sql.append(selectFields.stream()
                .map(f -> f.contains(".") ? f : "m." + f)
                .collect(Collectors.joining(", ")));
        } else {
            sql.append("SELECT m.*");
            // 添加关联表的关键字段
            for (int i = 0; i < topRelations.size(); i++) {
                TableRelation r = topRelations.get(i);
                sql.append(String.format(", t%d.name AS %s_name", i, r.targetTable.replace("client_", "")));
            }
        }

        // FROM 子句
        sql.append(String.format("\nFROM `%s` m", mainTable));

        // JOIN 子句
        for (int i = 0; i < topRelations.size(); i++) {
            TableRelation r = topRelations.get(i);
            sql.append(String.format("\nLEFT JOIN `%s` t%d ON m.`%s` = t%d.`%s`",
                r.targetTable, i, r.sourceField, i, r.targetField));
        }

        // LIMIT
        sql.append("\nLIMIT 100");

        return sql.toString();
    }

    /**
     * 构建关联查询提示
     */
    public String buildRelationHint(String tableName) {
        List<TableRelation> relations = getTableRelations(tableName);

        if (relations.isEmpty()) {
            return "该表没有检测到明显的关联关系。";
        }

        StringBuilder hint = new StringBuilder();
        hint.append("## 表 `").append(tableName).append("` 的关联关系\n\n");

        // 分组：出向引用和入向引用
        List<TableRelation> outgoing = relations.stream()
            .filter(r -> r.relationType == RelationType.OUTGOING)
            .collect(Collectors.toList());
        List<TableRelation> incoming = relations.stream()
            .filter(r -> r.relationType == RelationType.INCOMING)
            .collect(Collectors.toList());

        if (!outgoing.isEmpty()) {
            hint.append("### 引用其他表（可JOIN查询）\n");
            for (TableRelation r : outgoing) {
                hint.append(String.format("- `%s` → `%s`.`%s` (%s, 置信度: %.0f%%)\n",
                    r.sourceField, r.targetTable, r.targetField, r.description, r.confidence * 100));
            }
            hint.append("\n");
        }

        if (!incoming.isEmpty()) {
            hint.append("### 被其他表引用（可反向查询）\n");
            for (TableRelation r : incoming) {
                hint.append(String.format("- `%s`.`%s` → 本表 (%s)\n",
                    r.targetTable, r.sourceField, r.description));
            }
        }

        return hint.toString();
    }

    /**
     * 获取表的完整关系图（用于可视化）
     */
    public RelationshipGraph buildRelationshipGraph(String centerTable, int depth) {
        RelationshipGraph graph = new RelationshipGraph(centerTable);
        Set<String> visited = new HashSet<>();
        buildGraphRecursive(centerTable, graph, visited, depth);
        return graph;
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取表的列信息
     */
    public List<ColumnInfo> getTableColumns(String tableName) {
        if (tableColumnCache.containsKey(tableName)) {
            return tableColumnCache.get(tableName);
        }

        try {
            List<ColumnInfo> columns = jdbcTemplate.query(
                "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                (rs, rowNum) -> new ColumnInfo(
                    rs.getString("COLUMN_NAME"),
                    rs.getString("DATA_TYPE"),
                    rs.getString("COLUMN_COMMENT")
                ),
                tableName
            );
            tableColumnCache.put(tableName, columns);
            return columns;
        } catch (Exception e) {
            log.warn("获取表 {} 列信息失败: {}", tableName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取所有表名
     */
    public List<String> getAllTableNames() {
        if (allTableNames != null) {
            return allTableNames;
        }

        try {
            allTableNames = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'",
                String.class
            );
            return allTableNames;
        } catch (Exception e) {
            log.warn("获取表名列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 检查表是否存在
     */
    public boolean tableExists(String tableName) {
        return getAllTableNames().stream()
            .anyMatch(t -> t.equalsIgnoreCase(tableName));
    }

    /**
     * 查找实际存在的目标表
     */
    private String findExistingTable(List<String> candidates) {
        List<String> allTables = getAllTableNames();
        for (String candidate : candidates) {
            for (String actual : allTables) {
                if (actual.equalsIgnoreCase(candidate)) {
                    return actual;
                }
            }
        }
        return null;
    }

    /**
     * 分析单个字段的引用关系
     */
    private Optional<TableRelation> analyzeColumnReference(String sourceTable, ColumnInfo column) {
        String columnName = column.name;

        // 跳过主键
        if (columnName.equalsIgnoreCase("id")) {
            return Optional.empty();
        }

        for (ReferencePattern pattern : REFERENCE_PATTERNS) {
            if (pattern.pattern.matcher(columnName).matches()) {
                // 查找实际存在的目标表
                String targetTable = findExistingTable(pattern.targetTables);
                if (targetTable != null && !targetTable.equalsIgnoreCase(sourceTable)) {
                    TableRelation relation = new TableRelation(
                        sourceTable, columnName,
                        targetTable, pattern.targetField,
                        RelationType.OUTGOING,
                        pattern.systemName + "引用",
                        pattern.priority / 100.0
                    );
                    return Optional.of(relation);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * 添加已知的预定义关联
     */
    private void addKnownRelations(String tableName, List<TableRelation> relations) {
        List<KnownRelation> known = KNOWN_RELATIONS.get(tableName);
        if (known == null) return;

        for (KnownRelation kr : known) {
            if (tableExists(kr.relatedTable)) {
                TableRelation relation = new TableRelation(
                    tableName, kr.fieldHint,
                    kr.relatedTable, "id",
                    RelationType.OUTGOING,
                    kr.description,
                    0.95 // 高置信度
                );
                relations.add(relation);
            }
        }
    }

    /**
     * 添加入向引用（谁引用了这个表）
     */
    private void addIncomingReferences(String tableName, List<TableRelation> relations) {
        // 检查已知的反向引用
        for (Map.Entry<String, List<KnownRelation>> entry : KNOWN_RELATIONS.entrySet()) {
            String otherTable = entry.getKey();
            for (KnownRelation kr : entry.getValue()) {
                if (kr.relatedTable.equalsIgnoreCase(tableName) && tableExists(otherTable)) {
                    TableRelation relation = new TableRelation(
                        tableName, "id",
                        otherTable, kr.fieldHint,
                        RelationType.INCOMING,
                        "被" + otherTable + "引用: " + kr.description,
                        0.90
                    );
                    if (!containsRelation(relations, relation)) {
                        relations.add(relation);
                    }
                }
            }
        }
    }

    /**
     * 检查是否已包含相同关系
     */
    private boolean containsRelation(List<TableRelation> relations, TableRelation newRelation) {
        return relations.stream().anyMatch(r ->
            r.sourceTable.equals(newRelation.sourceTable) &&
            r.targetTable.equals(newRelation.targetTable) &&
            r.sourceField.equals(newRelation.sourceField)
        );
    }

    /**
     * 递归构建关系图
     */
    private void buildGraphRecursive(String table, RelationshipGraph graph, Set<String> visited, int depth) {
        if (depth <= 0 || visited.contains(table)) {
            return;
        }
        visited.add(table);

        List<TableRelation> relations = getTableRelations(table);
        for (TableRelation r : relations) {
            if (r.relationType == RelationType.OUTGOING) {
                graph.addNode(r.targetTable);
                graph.addEdge(table, r.targetTable, r);

                // 递归展开
                if (depth > 1) {
                    buildGraphRecursive(r.targetTable, graph, visited, depth - 1);
                }
            }
        }
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        tableColumnCache.clear();
        relationCache.clear();
        allTableNames = null;
        log.info("表关系分析器缓存已清除");
    }

    // ==================== 内部数据结构 ====================

    /**
     * 列信息
     */
    public static class ColumnInfo {
        public final String name;
        public final String dataType;
        public final String comment;

        public ColumnInfo(String name, String dataType, String comment) {
            this.name = name;
            this.dataType = dataType;
            this.comment = comment;
        }
    }

    /**
     * 表关联关系
     */
    public static class TableRelation {
        public final String sourceTable;
        public final String sourceField;
        public final String targetTable;
        public final String targetField;
        public final RelationType relationType;
        public final String description;
        public final double confidence;

        public TableRelation(String sourceTable, String sourceField,
                           String targetTable, String targetField,
                           RelationType relationType, String description, double confidence) {
            this.sourceTable = sourceTable;
            this.sourceField = sourceField;
            this.targetTable = targetTable;
            this.targetField = targetField;
            this.relationType = relationType;
            this.description = description;
            this.confidence = confidence;
        }

        @Override
        public String toString() {
            return String.format("%s.%s -> %s.%s (%s)",
                sourceTable, sourceField, targetTable, targetField, description);
        }
    }

    /**
     * 关联方向
     */
    public enum RelationType {
        OUTGOING,  // 本表引用其他表
        INCOMING   // 其他表引用本表
    }

    /**
     * 引用模式定义
     */
    private static class ReferencePattern {
        final Pattern pattern;
        final String systemName;
        final List<String> targetTables;
        final String targetField;
        final int priority;

        ReferencePattern(Pattern pattern, String systemName, List<String> targetTables,
                        String targetField, int priority) {
            this.pattern = pattern;
            this.systemName = systemName;
            this.targetTables = targetTables;
            this.targetField = targetField;
            this.priority = priority;
        }
    }

    /**
     * 已知关联定义
     */
    private static class KnownRelation {
        final String relatedTable;
        final String fieldHint;
        final String description;

        KnownRelation(String relatedTable, String fieldHint, String description) {
            this.relatedTable = relatedTable;
            this.fieldHint = fieldHint;
            this.description = description;
        }
    }

    /**
     * 关系图（用于可视化）
     */
    public static class RelationshipGraph {
        private final String centerTable;
        private final Set<String> nodes = new LinkedHashSet<>();
        private final List<GraphEdge> edges = new ArrayList<>();

        public RelationshipGraph(String centerTable) {
            this.centerTable = centerTable;
            this.nodes.add(centerTable);
        }

        public void addNode(String table) {
            nodes.add(table);
        }

        public void addEdge(String from, String to, TableRelation relation) {
            edges.add(new GraphEdge(from, to, relation));
        }

        public String getCenterTable() { return centerTable; }
        public Set<String> getNodes() { return nodes; }
        public List<GraphEdge> getEdges() { return edges; }

        public int getNodeCount() { return nodes.size(); }
        public int getEdgeCount() { return edges.size(); }
    }

    /**
     * 图边
     */
    public static class GraphEdge {
        public final String from;
        public final String to;
        public final TableRelation relation;

        public GraphEdge(String from, String to, TableRelation relation) {
            this.from = from;
            this.to = to;
            this.relation = relation;
        }
    }
}
