package red.jiuzhou.agent.templates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.context.DesignContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI 模板库
 *
 * 管理预设模板和用户自定义模板，支持：
 * - 预设 SQL 查询模板
 * - 用户保存的自定义模板
 * - 基于上下文的模板推荐
 * - 使用频率统计
 *
 * @author Claude
 * @version 1.0
 */
public class AiTemplateLibrary {

    private static final Logger log = LoggerFactory.getLogger(AiTemplateLibrary.class);

    // 单例实例
    private static volatile AiTemplateLibrary instance;

    // 预设模板
    private final List<AiTemplate> presetTemplates = new ArrayList<>();

    // 用户自定义模板
    private final Map<String, AiTemplate> userTemplates = new ConcurrentHashMap<>();

    // 使用统计
    private final Map<String, Integer> usageStats = new ConcurrentHashMap<>();

    // ==================== 模板分类 ====================

    public enum TemplateCategory {
        QUERY("查询", "数据查询类模板"),
        ANALYSIS("分析", "数据分析类模板"),
        VALIDATION("校验", "数据校验类模板"),
        GENERATION("生成", "SQL生成类模板"),
        MODIFICATION("修改", "数据修改类模板");

        private final String name;
        private final String description;

        TemplateCategory(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    // ==================== 模板参数 ====================

    public record TemplateParam(
        String name,           // 参数名
        String label,          // 显示标签
        String type,           // 类型：text, number, select
        String defaultValue,   // 默认值
        List<String> options   // select 类型的选项
    ) {
        public TemplateParam(String name, String label, String type, String defaultValue) {
            this(name, label, type, defaultValue, null);
        }

        public TemplateParam(String name, String label) {
            this(name, label, "text", "", null);
        }
    }

    // ==================== 模板结构 ====================

    public record AiTemplate(
        String id,                    // 唯一标识
        String name,                  // 模板名称
        String description,           // 模板描述
        TemplateCategory category,    // 分类
        String promptTemplate,        // Prompt 模板（含占位符）
        String sqlTemplate,           // SQL 模板（可选，含占位符）
        List<TemplateParam> params,   // 参数列表
        List<String> applicableTables,// 适用的表名模式
        int priority                  // 推荐优先级（越高越优先）
    ) {
        /**
         * 渲染 Prompt（替换占位符）
         */
        public String renderPrompt(Map<String, String> paramValues) {
            String result = promptTemplate;
            for (Map.Entry<String, String> entry : paramValues.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return result;
        }

        /**
         * 渲染 SQL（替换占位符）
         */
        public String renderSql(Map<String, String> paramValues) {
            if (sqlTemplate == null || sqlTemplate.isEmpty()) return null;
            String result = sqlTemplate;
            for (Map.Entry<String, String> entry : paramValues.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            return result;
        }

        /**
         * 检查是否适用于指定表
         */
        public boolean isApplicableTo(String tableName) {
            if (applicableTables == null || applicableTables.isEmpty()) return true;
            for (String pattern : applicableTables) {
                if (pattern.equals("*")) return true;
                if (pattern.endsWith("*") && tableName.startsWith(pattern.substring(0, pattern.length() - 1))) {
                    return true;
                }
                if (tableName.equalsIgnoreCase(pattern)) return true;
            }
            return false;
        }
    }

    // ==================== 单例访问 ====================

    public static AiTemplateLibrary getInstance() {
        if (instance == null) {
            synchronized (AiTemplateLibrary.class) {
                if (instance == null) {
                    instance = new AiTemplateLibrary();
                }
            }
        }
        return instance;
    }

    // ==================== 构造函数 ====================

    private AiTemplateLibrary() {
        initPresetTemplates();
        log.info("AI 模板库初始化完成，预设模板: {} 个", presetTemplates.size());
    }

    /**
     * 初始化预设模板
     */
    private void initPresetTemplates() {
        // ==================== 查询类模板 ====================

        presetTemplates.add(new AiTemplate(
            "query_high_level_items",
            "查询高等级物品",
            "查找指定等级以上的所有物品",
            TemplateCategory.QUERY,
            "在 {table} 表中查找等级大于等于 {level} 的物品",
            "SELECT * FROM `{table}` WHERE level >= {level} ORDER BY level DESC LIMIT 100",
            List.of(
                new TemplateParam("table", "表名", "text", "client_items"),
                new TemplateParam("level", "最低等级", "number", "50")
            ),
            List.of("*_items", "client_items", "item_*"),
            90
        ));

        presetTemplates.add(new AiTemplate(
            "query_by_name",
            "按名称搜索",
            "模糊搜索包含指定关键词的记录",
            TemplateCategory.QUERY,
            "在 {table} 表中查找名称包含 \"{keyword}\" 的记录",
            "SELECT * FROM `{table}` WHERE name LIKE '%{keyword}%' OR name_cn LIKE '%{keyword}%' LIMIT 100",
            List.of(
                new TemplateParam("table", "表名"),
                new TemplateParam("keyword", "关键词")
            ),
            List.of("*"),
            100
        ));

        presetTemplates.add(new AiTemplate(
            "query_unused_configs",
            "查找未使用的配置",
            "查找没有被引用的配置记录",
            TemplateCategory.QUERY,
            "在 {table} 表中查找没有被其他表引用的记录（孤立数据）",
            null, // 复杂查询需要 AI 生成
            List.of(new TemplateParam("table", "表名")),
            List.of("*"),
            70
        ));

        presetTemplates.add(new AiTemplate(
            "query_duplicate_ids",
            "查找重复ID",
            "检查是否存在重复的ID记录",
            TemplateCategory.VALIDATION,
            "在 {table} 表中查找重复的ID记录",
            "SELECT id, COUNT(*) as cnt FROM `{table}` GROUP BY id HAVING cnt > 1",
            List.of(new TemplateParam("table", "表名")),
            List.of("*"),
            85
        ));

        // ==================== 技能相关模板 ====================

        presetTemplates.add(new AiTemplate(
            "query_skill_by_element",
            "按属性查询技能",
            "查找指定属性类型的技能",
            TemplateCategory.QUERY,
            "在技能表中查找 {element} 属性的技能，按伤害排序",
            null,
            List.of(
                new TemplateParam("element", "属性类型", "select", "fire",
                    List.of("fire", "water", "earth", "wind", "light", "dark"))
            ),
            List.of("*skill*", "client_skill*"),
            80
        ));

        presetTemplates.add(new AiTemplate(
            "query_skill_cooldown",
            "技能冷却时间分析",
            "分析技能冷却时间分布",
            TemplateCategory.ANALYSIS,
            "分析 {table} 表中技能的冷却时间分布，找出异常值",
            null,
            List.of(new TemplateParam("table", "表名", "text", "client_skill")),
            List.of("*skill*"),
            75
        ));

        // ==================== NPC相关模板 ====================

        presetTemplates.add(new AiTemplate(
            "query_npc_by_level",
            "按等级查询NPC",
            "查找指定等级范围的NPC",
            TemplateCategory.QUERY,
            "在NPC表中查找等级在 {minLevel} 到 {maxLevel} 之间的NPC",
            "SELECT * FROM `{table}` WHERE level BETWEEN {minLevel} AND {maxLevel} ORDER BY level",
            List.of(
                new TemplateParam("table", "表名", "text", "client_npcs"),
                new TemplateParam("minLevel", "最低等级", "number", "1"),
                new TemplateParam("maxLevel", "最高等级", "number", "65")
            ),
            List.of("*npc*", "client_npc*"),
            80
        ));

        presetTemplates.add(new AiTemplate(
            "query_npc_spawn_check",
            "NPC刷新点检查",
            "检查NPC刷新配置是否完整",
            TemplateCategory.VALIDATION,
            "检查 {table} 表中的NPC是否都配置了刷新点",
            null,
            List.of(new TemplateParam("table", "表名", "text", "spawns")),
            List.of("spawn*", "*spawn*"),
            70
        ));

        // ==================== 任务相关模板 ====================

        presetTemplates.add(new AiTemplate(
            "query_quest_chain",
            "任务链分析",
            "分析任务的前置后置关系",
            TemplateCategory.ANALYSIS,
            "分析 {questId} 任务的完整任务链（前置和后续任务）",
            null,
            List.of(new TemplateParam("questId", "任务ID")),
            List.of("*quest*"),
            75
        ));

        presetTemplates.add(new AiTemplate(
            "query_quest_rewards",
            "任务奖励统计",
            "统计任务奖励分布",
            TemplateCategory.ANALYSIS,
            "统计 {table} 表中任务奖励的类型和数量分布",
            null,
            List.of(new TemplateParam("table", "表名", "text", "client_quest")),
            List.of("*quest*"),
            70
        ));

        // ==================== 数据分析模板 ====================

        presetTemplates.add(new AiTemplate(
            "analyze_field_distribution",
            "字段分布分析",
            "分析指定字段的值分布情况",
            TemplateCategory.ANALYSIS,
            "分析 {table} 表中 {field} 字段的值分布",
            "SELECT `{field}`, COUNT(*) as cnt FROM `{table}` GROUP BY `{field}` ORDER BY cnt DESC LIMIT 50",
            List.of(
                new TemplateParam("table", "表名"),
                new TemplateParam("field", "字段名")
            ),
            List.of("*"),
            85
        ));

        presetTemplates.add(new AiTemplate(
            "analyze_null_values",
            "空值统计",
            "统计表中各字段的空值数量",
            TemplateCategory.ANALYSIS,
            "统计 {table} 表中所有字段的空值和NULL数量",
            null,
            List.of(new TemplateParam("table", "表名")),
            List.of("*"),
            80
        ));

        presetTemplates.add(new AiTemplate(
            "analyze_numeric_range",
            "数值范围分析",
            "分析数值字段的范围和异常值",
            TemplateCategory.ANALYSIS,
            "分析 {table} 表中 {field} 字段的最大值、最小值、平均值，找出异常值",
            "SELECT MIN(`{field}`) as min_val, MAX(`{field}`) as max_val, AVG(`{field}`) as avg_val FROM `{table}`",
            List.of(
                new TemplateParam("table", "表名"),
                new TemplateParam("field", "数值字段")
            ),
            List.of("*"),
            80
        ));

        // ==================== 引用检查模板 ====================

        presetTemplates.add(new AiTemplate(
            "check_id_references",
            "ID引用检查",
            "检查ID字段的引用完整性",
            TemplateCategory.VALIDATION,
            "检查 {table} 表中 {idField} 字段引用的 {refTable} 是否都存在",
            null,
            List.of(
                new TemplateParam("table", "来源表"),
                new TemplateParam("idField", "ID字段"),
                new TemplateParam("refTable", "引用表")
            ),
            List.of("*"),
            90
        ));

        presetTemplates.add(new AiTemplate(
            "check_broken_links",
            "断链检查",
            "查找所有断链的引用",
            TemplateCategory.VALIDATION,
            "检查 {table} 表中所有ID类型字段的引用完整性，列出断链记录",
            null,
            List.of(new TemplateParam("table", "表名")),
            List.of("*"),
            85
        ));

        // ==================== 生成类模板 ====================

        presetTemplates.add(new AiTemplate(
            "generate_field_doc",
            "生成字段文档",
            "自动生成表的字段说明文档",
            TemplateCategory.GENERATION,
            "为 {table} 表的所有字段生成详细的中文说明文档，包括字段用途和可能的值",
            null,
            List.of(new TemplateParam("table", "表名")),
            List.of("*"),
            70
        ));

        presetTemplates.add(new AiTemplate(
            "generate_sample_data",
            "生成示例数据",
            "生成符合表结构的示例数据",
            TemplateCategory.GENERATION,
            "为 {table} 表生成 {count} 条符合结构和业务规则的示例INSERT语句",
            null,
            List.of(
                new TemplateParam("table", "表名"),
                new TemplateParam("count", "数量", "number", "5")
            ),
            List.of("*"),
            60
        ));

        // ==================== 修改类模板 ====================

        presetTemplates.add(new AiTemplate(
            "batch_update_field",
            "批量更新字段",
            "批量修改满足条件的记录",
            TemplateCategory.MODIFICATION,
            "将 {table} 表中满足 {condition} 条件的记录的 {field} 字段更新为 {value}",
            "UPDATE `{table}` SET `{field}` = '{value}' WHERE {condition}",
            List.of(
                new TemplateParam("table", "表名"),
                new TemplateParam("field", "字段名"),
                new TemplateParam("value", "新值"),
                new TemplateParam("condition", "条件", "text", "1=1")
            ),
            List.of("*"),
            50
        ));

        // ==================== 多表关联查询模板 ====================

        presetTemplates.add(new AiTemplate(
            "multi_table_item_usage",
            "物品使用场景分析",
            "分析物品在任务、商店、掉落等系统中的使用情况",
            TemplateCategory.ANALYSIS,
            "分析物品 {item_id} 在游戏各系统中的使用情况：任务奖励、商店出售、NPC掉落、配方等",
            null, // 复杂查询需要AI生成
            List.of(new TemplateParam("item_id", "物品ID")),
            List.of("*item*", "client_items"),
            95
        ));

        presetTemplates.add(new AiTemplate(
            "multi_table_npc_full_info",
            "NPC完整信息查询",
            "查询NPC及其关联的任务、商店、掉落等信息",
            TemplateCategory.QUERY,
            "查询NPC {npc_id} 的完整信息，包括所属任务、商店、掉落物品等",
            null,
            List.of(new TemplateParam("npc_id", "NPC ID")),
            List.of("*npc*", "client_npcs*"),
            95
        ));

        presetTemplates.add(new AiTemplate(
            "multi_table_quest_dependencies",
            "任务依赖分析",
            "分析任务涉及的所有物品、NPC、技能等",
            TemplateCategory.ANALYSIS,
            "分析任务 {quest_id} 的完整依赖：需要的物品、涉及的NPC、奖励内容等",
            null,
            List.of(new TemplateParam("quest_id", "任务ID")),
            List.of("*quest*", "client_quest*"),
            95
        ));

        presetTemplates.add(new AiTemplate(
            "multi_table_item_source",
            "物品来源追溯",
            "追溯物品的所有获取途径",
            TemplateCategory.QUERY,
            "查找物品 {item_id} 的所有获取途径：商店购买、NPC掉落、任务奖励、配方制作等",
            null,
            List.of(new TemplateParam("item_id", "物品ID")),
            List.of("*item*", "client_items"),
            90
        ));

        presetTemplates.add(new AiTemplate(
            "multi_table_shop_inventory",
            "商店完整库存",
            "查询商店及其所有商品详情",
            TemplateCategory.QUERY,
            "查询商店 {shop_id} 的完整商品列表，包括物品名称、价格、等级限制等",
            "SELECT s.*, i.name as item_name, i.level as item_level, i.quality as item_quality " +
            "FROM shop_templates s " +
            "LEFT JOIN client_items i ON s.item_id = i.id " +
            "WHERE s.shop_id = {shop_id} OR s.id = {shop_id} " +
            "ORDER BY i.level",
            List.of(new TemplateParam("shop_id", "商店ID")),
            List.of("shop*", "*shop*"),
            90
        ));

        presetTemplates.add(new AiTemplate(
            "multi_table_drop_analysis",
            "掉落表关联分析",
            "分析掉落表关联的NPC和物品",
            TemplateCategory.ANALYSIS,
            "分析掉落配置 {drop_id} 关联的所有NPC和掉落物品，包括掉落概率",
            null,
            List.of(new TemplateParam("drop_id", "掉落ID")),
            List.of("drop*", "*drop*"),
            85
        ));

        presetTemplates.add(new AiTemplate(
            "multi_table_luna_shop",
            "Luna商店商品分析",
            "查询Luna商店的商品及物品详情",
            TemplateCategory.QUERY,
            "查询Luna商店中的商品，包括物品详情、价格、限购等信息",
            null,
            List.of(),
            List.of("luna*", "*luna*"),
            85
        ));

        presetTemplates.add(new AiTemplate(
            "multi_table_recipe_materials",
            "配方材料追溯",
            "分析配方的材料来源和产物用途",
            TemplateCategory.ANALYSIS,
            "分析配方 {recipe_id} 的材料清单，并追溯每种材料的获取途径",
            null,
            List.of(new TemplateParam("recipe_id", "配方ID")),
            List.of("recipe*", "*recipe*"),
            85
        ));

        presetTemplates.add(new AiTemplate(
            "multi_table_skill_usage",
            "技能使用场景",
            "分析技能在NPC、任务、物品中的使用",
            TemplateCategory.ANALYSIS,
            "分析技能 {skill_id} 的使用场景：哪些NPC使用、哪些任务奖励、哪些物品附带",
            null,
            List.of(new TemplateParam("skill_id", "技能ID")),
            List.of("*skill*", "client_skill*"),
            85
        ));

        presetTemplates.add(new AiTemplate(
            "multi_table_cross_reference",
            "跨表引用检查",
            "检查指定ID在多个表中的引用情况",
            TemplateCategory.VALIDATION,
            "检查ID值 {id_value} 在物品、NPC、任务、技能等表中的引用情况",
            null,
            List.of(new TemplateParam("id_value", "ID值")),
            List.of("*"),
            80
        ));

        presetTemplates.add(new AiTemplate(
            "auto_join_query",
            "智能关联查询",
            "自动识别表关联并生成JOIN查询",
            TemplateCategory.QUERY,
            "对 {table} 表进行智能关联查询，自动JOIN相关表并显示关键信息",
            null,
            List.of(new TemplateParam("table", "主表名")),
            List.of("*"),
            100
        ));

        presetTemplates.add(new AiTemplate(
            "impact_analysis",
            "影响分析",
            "分析修改某条数据的影响范围",
            TemplateCategory.ANALYSIS,
            "分析修改 {table} 表中 id={id_value} 的记录会影响哪些关联数据",
            null,
            List.of(
                new TemplateParam("table", "表名"),
                new TemplateParam("id_value", "记录ID")
            ),
            List.of("*"),
            90
        ));
    }

    // ==================== 公共方法 ====================

    /**
     * 获取所有预设模板
     */
    public List<AiTemplate> getAllPresetTemplates() {
        return Collections.unmodifiableList(presetTemplates);
    }

    /**
     * 获取所有用户模板
     */
    public Collection<AiTemplate> getAllUserTemplates() {
        return userTemplates.values();
    }

    /**
     * 按分类获取模板
     */
    public List<AiTemplate> getTemplatesByCategory(TemplateCategory category) {
        List<AiTemplate> result = new ArrayList<>();
        result.addAll(presetTemplates.stream()
            .filter(t -> t.category() == category)
            .toList());
        result.addAll(userTemplates.values().stream()
            .filter(t -> t.category() == category)
            .toList());
        return result;
    }

    /**
     * 根据上下文推荐模板
     */
    public List<AiTemplate> getRecommendedTemplates(DesignContext context) {
        String tableName = context != null ? context.getCurrentTableName() : null;

        List<AiTemplate> candidates = new ArrayList<>();

        // 收集适用的模板
        for (AiTemplate template : presetTemplates) {
            if (tableName == null || template.isApplicableTo(tableName)) {
                candidates.add(template);
            }
        }

        for (AiTemplate template : userTemplates.values()) {
            if (tableName == null || template.isApplicableTo(tableName)) {
                candidates.add(template);
            }
        }

        // 按优先级和使用频率排序
        candidates.sort((a, b) -> {
            int priorityDiff = b.priority() - a.priority();
            if (priorityDiff != 0) return priorityDiff;

            int usageA = usageStats.getOrDefault(a.id(), 0);
            int usageB = usageStats.getOrDefault(b.id(), 0);
            return usageB - usageA;
        });

        return candidates.stream().limit(10).toList();
    }

    /**
     * 根据ID获取模板
     */
    public Optional<AiTemplate> getTemplateById(String id) {
        // 先查用户模板
        if (userTemplates.containsKey(id)) {
            return Optional.of(userTemplates.get(id));
        }
        // 再查预设模板
        return presetTemplates.stream()
            .filter(t -> t.id().equals(id))
            .findFirst();
    }

    /**
     * 保存用户自定义模板
     */
    public void saveUserTemplate(AiTemplate template) {
        userTemplates.put(template.id(), template);
        log.info("保存用户模板: {}", template.name());
    }

    /**
     * 删除用户模板
     */
    public boolean deleteUserTemplate(String id) {
        AiTemplate removed = userTemplates.remove(id);
        if (removed != null) {
            log.info("删除用户模板: {}", removed.name());
            return true;
        }
        return false;
    }

    /**
     * 记录模板使用
     */
    public void recordUsage(String templateId) {
        usageStats.merge(templateId, 1, Integer::sum);
    }

    /**
     * 获取热门模板
     */
    public List<AiTemplate> getPopularTemplates(int limit) {
        return usageStats.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .limit(limit)
            .map(e -> getTemplateById(e.getKey()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    /**
     * 搜索模板
     */
    public List<AiTemplate> searchTemplates(String keyword) {
        String lowerKeyword = keyword.toLowerCase();

        List<AiTemplate> results = new ArrayList<>();

        // 搜索预设模板
        results.addAll(presetTemplates.stream()
            .filter(t -> t.name().toLowerCase().contains(lowerKeyword) ||
                         t.description().toLowerCase().contains(lowerKeyword))
            .toList());

        // 搜索用户模板
        results.addAll(userTemplates.values().stream()
            .filter(t -> t.name().toLowerCase().contains(lowerKeyword) ||
                         t.description().toLowerCase().contains(lowerKeyword))
            .toList());

        return results;
    }

    /**
     * 获取模板统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("presetCount", presetTemplates.size());
        stats.put("userCount", userTemplates.size());
        stats.put("totalUsage", usageStats.values().stream().mapToInt(Integer::intValue).sum());

        // 按分类统计
        Map<TemplateCategory, Long> categoryStats = presetTemplates.stream()
            .collect(Collectors.groupingBy(AiTemplate::category, Collectors.counting()));
        stats.put("categoryStats", categoryStats);

        return stats;
    }
}
