package red.jiuzhou.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.dbxml.TableConf;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强版Schema提供者
 *
 * 整合项目的表配置和领域知识,为AI提供更丰富的上下文
 *
 * 核心功能:
 * - 融合数据库Schema和业务配置
 * - 提供表的业务含义和关联关系
 * - 智能推荐相关表
 * - 生成领域相关的SQL示例
 *
 * @author yanxq
 * @date 2025-12-19
 */
public class EnhancedSchemaProvider {

    private static final Logger log = LoggerFactory.getLogger(EnhancedSchemaProvider.class);

    private final DatabaseSchemaProvider baseProvider;
    private final JdbcTemplate jdbcTemplate;

    /** 领域知识缓存 */
    private static final Map<String, DomainContext> domainCache = new HashMap<>();

    /**
     * 领域上下文
     */
    public static class DomainContext {
        private String category;              // 分类（NPC/道具/技能/任务等）
        private String description;           // 业务描述
        private List<String> relatedTables;   // 关联表
        private List<String> commonQueries;   // 常用查询示例
        private Map<String, String> fieldMeanings;  // 字段业务含义

        public DomainContext(String category) {
            this.category = category;
            this.relatedTables = new ArrayList<>();
            this.commonQueries = new ArrayList<>();
            this.fieldMeanings = new HashMap<>();
        }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getRelatedTables() { return relatedTables; }
        public List<String> getCommonQueries() { return commonQueries; }
        public Map<String, String> getFieldMeanings() { return fieldMeanings; }
    }

    public EnhancedSchemaProvider() {
        this(DatabaseUtil.getJdbcTemplate(null));
    }

    public EnhancedSchemaProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.baseProvider = new DatabaseSchemaProvider(jdbcTemplate);
        initDomainKnowledge();
    }

    /**
     * 初始化领域知识
     */
    private void initDomainKnowledge() {
        // NPC相关
        DomainContext npcContext = new DomainContext("NPC/怪物");
        npcContext.setDescription("游戏中的非玩家角色和怪物");
        npcContext.getRelatedTables().addAll(Arrays.asList("npc", "npc_template", "spawn", "drops"));
        npcContext.getCommonQueries().add("查询所有BOSS");
        npcContext.getCommonQueries().add("查询掉落稀有装备的怪物");
        npcContext.getFieldMeanings().put("level", "等级");
        npcContext.getFieldMeanings().put("hp", "生命值");
        npcContext.getFieldMeanings().put("name", "名称");
        domainCache.put("npc", npcContext);

        // 道具相关
        DomainContext itemContext = new DomainContext("道具/装备");
        itemContext.setDescription("游戏中的物品和装备");
        itemContext.getRelatedTables().addAll(Arrays.asList("items", "item_templates", "drops", "client_items"));
        itemContext.getCommonQueries().add("查询所有紫色装备");
        itemContext.getCommonQueries().add("查询50级以上的武器");
        itemContext.getFieldMeanings().put("quality", "品质(白/绿/蓝/紫/橙)");
        itemContext.getFieldMeanings().put("level", "等级要求");
        itemContext.getFieldMeanings().put("item_type", "道具类型");
        domainCache.put("items", itemContext);
        domainCache.put("client_items", itemContext);

        // 技能相关
        DomainContext skillContext = new DomainContext("技能");
        skillContext.setDescription("角色技能和法术");
        skillContext.getRelatedTables().addAll(Arrays.asList("skill_templates", "client_skill", "skill_data"));
        skillContext.getCommonQueries().add("查询伤害最高的技能");
        skillContext.getCommonQueries().add("按元素属性分析技能分布");
        skillContext.getFieldMeanings().put("damage", "伤害值");
        skillContext.getFieldMeanings().put("element_type", "元素属性(火/冰/雷等)");
        skillContext.getFieldMeanings().put("level", "学习等级");
        domainCache.put("skill", skillContext);
        domainCache.put("client_skill", skillContext);

        // 任务相关
        DomainContext questContext = new DomainContext("任务");
        questContext.setDescription("游戏任务和剧情");
        questContext.getRelatedTables().addAll(Arrays.asList("quest_templates", "quest_scripts", "quest_rewards"));
        questContext.getCommonQueries().add("查询主线任务");
        questContext.getCommonQueries().add("查询奖励丰厚的任务");
        questContext.getFieldMeanings().put("quest_type", "任务类型(主线/支线/日常)");
        questContext.getFieldMeanings().put("level", "接取等级");
        domainCache.put("quest", questContext);

        log.info("领域知识初始化完成: {} 个类别", domainCache.size());
    }

    /**
     * 获取表的领域上下文
     */
    public DomainContext getDomainContext(String tableName) {
        // 精确匹配
        if (domainCache.containsKey(tableName)) {
            return domainCache.get(tableName);
        }

        // 模糊匹配
        for (Map.Entry<String, DomainContext> entry : domainCache.entrySet()) {
            if (tableName.toLowerCase().contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 生成增强的Schema描述(融合领域知识)
     */
    public String getEnhancedSchemaDescription(List<String> tableNames) {
        StringBuilder sb = new StringBuilder();

        sb.append("# 游戏数据库Schema (增强版)\n\n");

        // 如果没有指定表,智能推荐
        if (tableNames == null || tableNames.isEmpty()) {
            tableNames = getRecommendedTables();
        }

        sb.append("## 核心表列表\n\n");

        for (String tableName : tableNames) {
            // 获取基础Schema
            DatabaseSchemaProvider.TableInfo tableInfo = baseProvider.getTableInfo(tableName);
            if (tableInfo == null) {
                continue;
            }

            // 获取领域上下文
            DomainContext domain = getDomainContext(tableName);

            sb.append("### 表: ").append(tableName);
            if (domain != null) {
                sb.append(" (").append(domain.getCategory()).append(")");
            }
            sb.append("\n");

            // 业务描述
            if (domain != null && domain.getDescription() != null) {
                sb.append("**业务说明**: ").append(domain.getDescription()).append("\n");
            }

            // 字段列表
            sb.append("**字段**:\n");
            for (DatabaseSchemaProvider.ColumnInfo col : tableInfo.getColumns()) {
                sb.append("  - `").append(col.getColumnName()).append("` (").append(col.getDataType()).append(")");

                // 字段业务含义
                if (domain != null && domain.getFieldMeanings().containsKey(col.getColumnName())) {
                    sb.append(" -- ").append(domain.getFieldMeanings().get(col.getColumnName()));
                } else if (col.getComment() != null && !col.getComment().isEmpty()) {
                    sb.append(" -- ").append(col.getComment());
                }

                if (!col.isNullable()) {
                    sb.append(" [必填]");
                }

                if (tableInfo.getPrimaryKeys().contains(col.getColumnName())) {
                    sb.append(" [主键]");
                }

                sb.append("\n");
            }

            // 关联表
            if (domain != null && !domain.getRelatedTables().isEmpty()) {
                sb.append("**关联表**: ");
                sb.append(String.join(", ", domain.getRelatedTables()));
                sb.append("\n");
            }

            // 常用查询示例
            if (domain != null && !domain.getCommonQueries().isEmpty()) {
                sb.append("**常用查询**:\n");
                for (String query : domain.getCommonQueries()) {
                    sb.append("  - ").append(query).append("\n");
                }
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 智能推荐相关表
     */
    public List<String> recommendRelatedTables(String query) {
        Set<String> recommended = new HashSet<>();

        String lowerQuery = query.toLowerCase();

        // 关键字匹配
        Map<String, List<String>> keywords = new HashMap<>();
        keywords.put("npc|怪物|boss|精英", Arrays.asList("npc", "npc_template", "spawn"));
        keywords.put("道具|装备|武器|防具|物品", Arrays.asList("items", "client_items", "item_templates"));
        keywords.put("技能|法术|魔法", Arrays.asList("skill_templates", "client_skill"));
        keywords.put("任务|剧情|主线|支线", Arrays.asList("quest_templates", "quest_scripts"));
        keywords.put("掉落|奖励", Arrays.asList("drops", "quest_rewards"));

        for (Map.Entry<String, List<String>> entry : keywords.entrySet()) {
            String[] patterns = entry.getKey().split("\\|");
            for (String pattern : patterns) {
                if (lowerQuery.contains(pattern)) {
                    recommended.addAll(entry.getValue());
                    break;
                }
            }
        }

        // 如果没有匹配,返回常用表
        if (recommended.isEmpty()) {
            recommended.addAll(Arrays.asList("npc", "items", "client_skill", "quest_templates"));
        }

        return new ArrayList<>(recommended);
    }

    /**
     * 获取推荐的核心表列表
     */
    private List<String> getRecommendedTables() {
        List<String> allTables = baseProvider.getAllTableNames();

        // 优先返回常用的核心表
        List<String> corePatterns = Arrays.asList(
            "npc", "items", "client_items", "client_skill", "skill", "quest",
            "spawn", "drops", "world", "player"
        );

        return allTables.stream()
            .filter(table -> corePatterns.stream()
                .anyMatch(pattern -> table.toLowerCase().contains(pattern)))
            .limit(20)
            .collect(Collectors.toList());
    }

    /**
     * 生成智能SQL提示
     */
    public String generateSqlHints(String query) {
        StringBuilder hints = new StringBuilder();

        hints.append("## SQL生成提示\n\n");

        // 推荐相关表
        List<String> tables = recommendRelatedTables(query);
        if (!tables.isEmpty()) {
            hints.append("**建议使用的表**: ");
            hints.append(String.join(", ", tables));
            hints.append("\n\n");
        }

        // 领域知识提示
        for (String tableName : tables) {
            DomainContext domain = getDomainContext(tableName);
            if (domain != null && !domain.getCommonQueries().isEmpty()) {
                hints.append("**").append(tableName).append(" 常用查询**:\n");
                for (String commonQuery : domain.getCommonQueries()) {
                    hints.append("  - ").append(commonQuery).append("\n");
                }
            }
        }

        return hints.toString();
    }

    /**
     * 获取表的示例数据
     */
    public String getTableSampleData(String tableName, int limit) {
        try {
            String sql = "SELECT * FROM " + tableName + " LIMIT " + limit;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            if (rows.isEmpty()) {
                return "表 " + tableName + " 无数据";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("表 ").append(tableName).append(" 示例数据 (").append(rows.size()).append(" 行):\n");

            // 显示前几行
            for (int i = 0; i < Math.min(3, rows.size()); i++) {
                Map<String, Object> row = rows.get(i);
                sb.append("  Row ").append(i + 1).append(": ");
                sb.append(row.toString());
                sb.append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("获取表示例数据失败: {}", tableName, e);
            return "获取示例数据失败: " + e.getMessage();
        }
    }

    /**
     * 获取基础Schema提供者
     */
    public DatabaseSchemaProvider getBaseProvider() {
        return baseProvider;
    }
}
