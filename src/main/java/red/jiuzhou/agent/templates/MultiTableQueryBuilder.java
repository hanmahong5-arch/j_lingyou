package red.jiuzhou.agent.templates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.templates.TableRelationshipAnalyzer.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 多表查询构建器
 *
 * <p>基于表关系分析器的结果，自动构建多表JOIN查询。
 * 支持：
 * <ul>
 *   <li>智能JOIN生成 - 自动选择合适的JOIN类型</li>
 *   <li>字段别名 - 避免字段名冲突</li>
 *   <li>查询优化 - 限制JOIN深度，控制结果集大小</li>
 *   <li>多种查询场景 - 物品追溯、NPC关联、任务依赖等</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class MultiTableQueryBuilder {

    private static final Logger log = LoggerFactory.getLogger(MultiTableQueryBuilder.class);

    private final TableRelationshipAnalyzer analyzer;

    // 配置
    private int maxJoins = 5;
    private int defaultLimit = 100;
    private boolean includeIncomingRefs = false;

    public MultiTableQueryBuilder() {
        this.analyzer = TableRelationshipAnalyzer.getInstance();
    }

    public MultiTableQueryBuilder(TableRelationshipAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    // ==================== 配置方法 ====================

    public MultiTableQueryBuilder maxJoins(int maxJoins) {
        this.maxJoins = maxJoins;
        return this;
    }

    public MultiTableQueryBuilder limit(int limit) {
        this.defaultLimit = limit;
        return this;
    }

    public MultiTableQueryBuilder includeIncoming(boolean include) {
        this.includeIncomingRefs = include;
        return this;
    }

    // ==================== 核心查询构建方法 ====================

    /**
     * 构建智能关联查询
     *
     * @param mainTable 主表名
     * @return 包含SQL和说明的查询结果
     */
    public QueryResult buildSmartJoinQuery(String mainTable) {
        return buildSmartJoinQuery(mainTable, null, null);
    }

    /**
     * 构建带条件的智能关联查询
     *
     * @param mainTable 主表名
     * @param whereCondition WHERE条件（可选）
     * @param selectFields 选择的字段（可选）
     * @return 查询结果
     */
    public QueryResult buildSmartJoinQuery(String mainTable, String whereCondition, List<String> selectFields) {
        List<TableRelation> relations = analyzer.getTableRelations(mainTable);

        // 筛选出向引用
        List<TableRelation> outgoing = relations.stream()
            .filter(r -> r.relationType == RelationType.OUTGOING)
            .sorted((a, b) -> Double.compare(b.confidence, a.confidence))
            .limit(maxJoins)
            .collect(Collectors.toList());

        if (outgoing.isEmpty()) {
            // 无关联，返回简单查询
            String sql = String.format("SELECT * FROM `%s`%s LIMIT %d",
                mainTable,
                whereCondition != null ? " WHERE " + whereCondition : "",
                defaultLimit);
            return new QueryResult(sql, "简单查询（未发现关联表）", Collections.singletonList(mainTable));
        }

        StringBuilder sql = new StringBuilder();
        List<String> involvedTables = new ArrayList<>();
        involvedTables.add(mainTable);

        // SELECT 子句
        sql.append("SELECT \n");
        if (selectFields != null && !selectFields.isEmpty()) {
            sql.append("  ").append(String.join(",\n  ", selectFields));
        } else {
            sql.append("  m.*");
            // 添加关联表的name字段
            for (int i = 0; i < outgoing.size(); i++) {
                TableRelation r = outgoing.get(i);
                String alias = getTableAlias(r.targetTable);
                sql.append(String.format(",\n  t%d.name AS %s_name", i, alias));
                // 如果有其他重要字段也添加
                if (hasColumn(r.targetTable, "level")) {
                    sql.append(String.format(",\n  t%d.level AS %s_level", i, alias));
                }
            }
        }

        // FROM 子句
        sql.append(String.format("\nFROM `%s` m", mainTable));

        // JOIN 子句
        for (int i = 0; i < outgoing.size(); i++) {
            TableRelation r = outgoing.get(i);
            sql.append(String.format("\nLEFT JOIN `%s` t%d ON m.`%s` = t%d.`%s`",
                r.targetTable, i, r.sourceField, i, r.targetField));
            involvedTables.add(r.targetTable);
        }

        // WHERE 子句
        if (whereCondition != null && !whereCondition.isEmpty()) {
            sql.append("\nWHERE ").append(whereCondition);
        }

        // LIMIT
        sql.append("\nLIMIT ").append(defaultLimit);

        // 生成描述
        String description = String.format("关联查询: %s + %d个关联表 (%s)",
            mainTable, outgoing.size(),
            outgoing.stream().map(r -> r.targetTable).collect(Collectors.joining(", ")));

        return new QueryResult(sql.toString(), description, involvedTables);
    }

    /**
     * 构建物品使用场景查询
     */
    public QueryResult buildItemUsageQuery(String itemId) {
        StringBuilder sql = new StringBuilder();
        sql.append("-- 物品使用场景分析\n");
        sql.append("-- 1. 商店出售\n");
        sql.append("SELECT '商店' as 来源, s.id as 来源ID, s.name as 商店名, s.price as 价格\n");
        sql.append("FROM shop_templates s WHERE s.item_id = ").append(itemId).append("\n");
        sql.append("UNION ALL\n");
        sql.append("-- 2. 任务奖励\n");
        sql.append("SELECT '任务奖励' as 来源, q.id as 来源ID, q.name as 任务名, NULL as 价格\n");
        sql.append("FROM client_quests q WHERE q.reward_item LIKE '%").append(itemId).append("%'\n");
        sql.append("UNION ALL\n");
        sql.append("-- 3. 掉落\n");
        sql.append("SELECT '掉落' as 来源, d.id as 来源ID, CONCAT('掉落组-', d.id) as 来源名, d.chance as 概率\n");
        sql.append("FROM drop_templates d WHERE d.item_id = ").append(itemId).append("\n");
        sql.append("LIMIT 100");

        return new QueryResult(
            sql.toString(),
            "物品 " + itemId + " 的使用场景分析",
            Arrays.asList("shop_templates", "client_quests", "drop_templates")
        );
    }

    /**
     * 构建物品来源追溯查询
     */
    public QueryResult buildItemSourceQuery(String itemId) {
        StringBuilder sql = new StringBuilder();
        sql.append("-- 物品来源追溯\n");
        sql.append("SELECT \n");
        sql.append("  i.id, i.name as 物品名, i.level as 等级, i.quality as 品质,\n");
        sql.append("  (\n");
        sql.append("    SELECT GROUP_CONCAT(CONCAT(s.name, '(', s.price, ')') SEPARATOR ', ')\n");
        sql.append("    FROM shop_templates s WHERE s.item_id = i.id\n");
        sql.append("  ) as 商店来源,\n");
        sql.append("  (\n");
        sql.append("    SELECT GROUP_CONCAT(q.name SEPARATOR ', ')\n");
        sql.append("    FROM client_quests q WHERE q.reward_item LIKE CONCAT('%', i.id, '%')\n");
        sql.append("  ) as 任务奖励,\n");
        sql.append("  (\n");
        sql.append("    SELECT COUNT(*) FROM drop_templates d WHERE d.item_id = i.id\n");
        sql.append("  ) as 掉落来源数\n");
        sql.append("FROM client_items i\n");
        sql.append("WHERE i.id = ").append(itemId);

        return new QueryResult(
            sql.toString(),
            "物品 " + itemId + " 的来源追溯",
            Arrays.asList("client_items", "shop_templates", "client_quests", "drop_templates")
        );
    }

    /**
     * 构建NPC完整信息查询
     */
    public QueryResult buildNpcFullInfoQuery(String npcId) {
        StringBuilder sql = new StringBuilder();
        sql.append("-- NPC完整信息查询\n");
        sql.append("SELECT \n");
        sql.append("  n.id, n.name as NPC名称, n.level as 等级,\n");
        sql.append("  -- 相关任务\n");
        sql.append("  (SELECT GROUP_CONCAT(q.name SEPARATOR ', ') FROM client_quests q WHERE q.start_npc = n.id OR q.end_npc = n.id) as 相关任务,\n");
        sql.append("  -- 商店\n");
        sql.append("  (SELECT COUNT(*) FROM shop_templates s WHERE s.npc_id = n.id) as 商店数量,\n");
        sql.append("  -- 掉落\n");
        sql.append("  (SELECT GROUP_CONCAT(DISTINCT i.name SEPARATOR ', ') FROM drop_npc dn \n");
        sql.append("   LEFT JOIN drop_templates dt ON dn.drop_id = dt.id\n");
        sql.append("   LEFT JOIN client_items i ON dt.item_id = i.id\n");
        sql.append("   WHERE dn.npc_id = n.id LIMIT 5) as 掉落物品\n");
        sql.append("FROM client_npcs_npc n\n");
        sql.append("WHERE n.id = ").append(npcId);

        return new QueryResult(
            sql.toString(),
            "NPC " + npcId + " 的完整信息",
            Arrays.asList("client_npcs_npc", "client_quests", "shop_templates", "drop_npc", "drop_templates", "client_items")
        );
    }

    /**
     * 构建任务依赖分析查询
     */
    public QueryResult buildQuestDependencyQuery(String questId) {
        StringBuilder sql = new StringBuilder();
        sql.append("-- 任务依赖分析\n");
        sql.append("SELECT \n");
        sql.append("  q.id, q.name as 任务名,\n");
        sql.append("  -- 起始NPC\n");
        sql.append("  (SELECT n.name FROM client_npcs_npc n WHERE n.id = q.start_npc) as 起始NPC,\n");
        sql.append("  -- 结束NPC\n");
        sql.append("  (SELECT n.name FROM client_npcs_npc n WHERE n.id = q.end_npc) as 结束NPC,\n");
        sql.append("  -- 前置任务\n");
        sql.append("  (SELECT pq.name FROM client_quests pq WHERE pq.id = q.prev_quest) as 前置任务,\n");
        sql.append("  -- 收集物品\n");
        sql.append("  q.collect_item as 需收集物品ID,\n");
        sql.append("  -- 奖励物品\n");
        sql.append("  q.reward_item as 奖励物品ID,\n");
        sql.append("  -- 奖励技能\n");
        sql.append("  (SELECT s.name FROM client_skills s WHERE s.id = q.reward_skill) as 奖励技能\n");
        sql.append("FROM client_quests q\n");
        sql.append("WHERE q.id = ").append(questId);

        return new QueryResult(
            sql.toString(),
            "任务 " + questId + " 的依赖分析",
            Arrays.asList("client_quests", "client_npcs_npc", "client_skills")
        );
    }

    /**
     * 构建影响分析查询
     */
    public QueryResult buildImpactAnalysisQuery(String tableName, String idValue) {
        List<TableRelation> relations = analyzer.getTableRelations(tableName);

        // 获取入向引用（谁引用了这个表）
        List<TableRelation> incoming = relations.stream()
            .filter(r -> r.relationType == RelationType.INCOMING)
            .collect(Collectors.toList());

        if (incoming.isEmpty()) {
            return new QueryResult(
                "-- 未发现其他表引用 " + tableName + " 表",
                "影响分析：无引用",
                Collections.singletonList(tableName)
            );
        }

        StringBuilder sql = new StringBuilder();
        sql.append("-- 影响分析: 修改 ").append(tableName).append(" id=").append(idValue).append(" 的影响范围\n\n");

        List<String> involvedTables = new ArrayList<>();
        involvedTables.add(tableName);

        for (int i = 0; i < incoming.size(); i++) {
            TableRelation r = incoming.get(i);
            if (i > 0) sql.append("\nUNION ALL\n");
            sql.append(String.format(
                "SELECT '%s' as 受影响表, '%s' as 引用字段, COUNT(*) as 影响行数\n" +
                "FROM `%s` WHERE `%s` = %s",
                r.targetTable, r.sourceField, r.targetTable, r.sourceField, idValue
            ));
            involvedTables.add(r.targetTable);
        }

        return new QueryResult(
            sql.toString(),
            "影响分析: " + tableName + " id=" + idValue,
            involvedTables
        );
    }

    /**
     * 构建跨表引用检查查询
     */
    public QueryResult buildCrossReferenceQuery(String idValue) {
        StringBuilder sql = new StringBuilder();
        sql.append("-- 跨表引用检查: ID值 ").append(idValue).append("\n\n");

        // 检查常见的引用场景
        String[] checkQueries = {
            "SELECT 'client_items' as 表, 'id' as 字段, COUNT(*) as 匹配数 FROM client_items WHERE id = " + idValue,
            "SELECT 'client_npcs_npc' as 表, 'id' as 字段, COUNT(*) as 匹配数 FROM client_npcs_npc WHERE id = " + idValue,
            "SELECT 'client_quests' as 表, 'id' as 字段, COUNT(*) as 匹配数 FROM client_quests WHERE id = " + idValue,
            "SELECT 'client_skills' as 表, 'id' as 字段, COUNT(*) as 匹配数 FROM client_skills WHERE id = " + idValue,
            "SELECT 'shop_templates' as 表, 'item_id' as 字段, COUNT(*) as 匹配数 FROM shop_templates WHERE item_id = " + idValue,
            "SELECT 'drop_templates' as 表, 'item_id' as 字段, COUNT(*) as 匹配数 FROM drop_templates WHERE item_id = " + idValue
        };

        sql.append(String.join("\nUNION ALL\n", checkQueries));

        return new QueryResult(
            sql.toString(),
            "跨表引用检查: ID=" + idValue,
            Arrays.asList("client_items", "client_npcs_npc", "client_quests", "client_skills", "shop_templates", "drop_templates")
        );
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取表别名（简化表名）
     */
    private String getTableAlias(String tableName) {
        return tableName
            .replace("client_", "")
            .replace("_templates", "")
            .replace("_", "");
    }

    /**
     * 检查表是否有指定列
     */
    private boolean hasColumn(String tableName, String columnName) {
        List<ColumnInfo> columns = analyzer.getTableColumns(tableName);
        return columns.stream().anyMatch(c -> c.name.equalsIgnoreCase(columnName));
    }

    // ==================== 数据结构 ====================

    /**
     * 查询结果
     */
    public static class QueryResult {
        private final String sql;
        private final String description;
        private final List<String> involvedTables;

        public QueryResult(String sql, String description, List<String> involvedTables) {
            this.sql = sql;
            this.description = description;
            this.involvedTables = involvedTables;
        }

        public String getSql() { return sql; }
        public String getDescription() { return description; }
        public List<String> getInvolvedTables() { return involvedTables; }

        public int getTableCount() { return involvedTables.size(); }

        @Override
        public String toString() {
            return String.format("QueryResult{tables=%d, description='%s'}",
                involvedTables.size(), description);
        }
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 根据模板ID构建查询
     */
    public QueryResult buildFromTemplate(String templateId, Map<String, String> params) {
        return switch (templateId) {
            case "multi_table_item_usage" -> buildItemUsageQuery(params.get("item_id"));
            case "multi_table_item_source" -> buildItemSourceQuery(params.get("item_id"));
            case "multi_table_npc_full_info" -> buildNpcFullInfoQuery(params.get("npc_id"));
            case "multi_table_quest_dependencies" -> buildQuestDependencyQuery(params.get("quest_id"));
            case "auto_join_query" -> buildSmartJoinQuery(params.get("table"));
            case "impact_analysis" -> buildImpactAnalysisQuery(params.get("table"), params.get("id_value"));
            case "multi_table_cross_reference" -> buildCrossReferenceQuery(params.get("id_value"));
            default -> null;
        };
    }

    /**
     * 判断是否为多表模板
     */
    public static boolean isMultiTableTemplate(String templateId) {
        return templateId != null && (
            templateId.startsWith("multi_table_") ||
            templateId.equals("auto_join_query") ||
            templateId.equals("impact_analysis")
        );
    }
}
