package red.jiuzhou.agent.texttosql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏语义增强器
 *
 * 动态构建游戏领域词汇表和语义映射
 * 帮助AI更好地理解游戏设计师的自然语言意图
 *
 * @author Claude
 * @date 2025-12-20
 */
public class GameSemanticEnhancer {

    private static final Logger log = LoggerFactory.getLogger(GameSemanticEnhancer.class);

    // 语义映射：自然语言 → SQL表达式
    private final Map<String, String> semanticMap = new ConcurrentHashMap<>();

    // 表别名映射：简称 → 完整表名
    private final Map<String, List<String>> tableAliases = new ConcurrentHashMap<>();

    // 字段别名映射：自然语言 → 字段名
    private final Map<String, Map<String, String>> fieldAliases = new ConcurrentHashMap<>();

    private JdbcTemplate jdbcTemplate;
    private DynamicSemanticBuilder dynamicBuilder;
    private boolean dynamicSemanticsEnabled = true;
    private boolean dynamicSemanticsInitialized = false;

    public GameSemanticEnhancer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initializeSemantics();
        // 不在构造函数中初始化动态语义，避免阻塞启动
        // 改为延迟加载或按需加载
    }

    /**
     * 异步初始化动态语义（推荐使用）
     */
    public void initializeDynamicSemanticsAsync(Runnable onComplete) {
        if (dynamicSemanticsInitialized) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        new Thread(() -> {
            try {
                initializeDynamicSemantics();
                dynamicSemanticsInitialized = true;
                if (onComplete != null) {
                    onComplete.run();
                }
            } catch (Exception e) {
                log.error("异步初始化动态语义失败", e);
                dynamicSemanticsEnabled = false;
            }
        }, "DynamicSemantics-Init").start();
    }

    /**
     * 初始化游戏语义
     */
    private void initializeSemantics() {
        // ========== 品质/稀有度映射 ==========
        semanticMap.put("白色品质", "quality = 0 OR quality = 'common'");
        semanticMap.put("绿色品质", "quality = 1 OR quality = 'uncommon'");
        semanticMap.put("蓝色品质", "quality = 2 OR quality = 'rare'");
        semanticMap.put("紫色品质", "quality = 3 OR quality = 'epic'");
        semanticMap.put("橙色品质", "quality = 4 OR quality = 'legendary'");
        semanticMap.put("普通", "quality = 0 OR quality = 'common'");
        semanticMap.put("精良", "quality = 1 OR quality = 'uncommon'");
        semanticMap.put("稀有", "quality = 2 OR quality = 'rare'");
        semanticMap.put("史诗", "quality = 3 OR quality = 'epic'");
        semanticMap.put("传说", "quality = 4 OR quality = 'legendary'");

        // ========== 简写映射（常用游戏术语） ==========
        semanticMap.put("白装", "quality = 0 OR quality = 'common'");
        semanticMap.put("绿装", "quality = 1 OR quality = 'uncommon'");
        semanticMap.put("蓝装", "quality = 2 OR quality = 'rare'");
        semanticMap.put("紫装", "quality = 3 OR quality = 'epic'");
        semanticMap.put("橙装", "quality = 4 OR quality = 'legendary'");
        semanticMap.put("金装", "quality = 5 OR quality = 'mythic'");
        semanticMap.put("神器", "quality >= 5 OR quality = 'artifact'");

        // ========== 数值比较简写 ==========
        semanticMap.put("高等级", "level >= 50");
        semanticMap.put("低等级", "level <= 20");
        semanticMap.put("高伤害", "damage >= 1000 OR base_damage >= 1000");
        semanticMap.put("低CD", "cooldown <= 5");
        semanticMap.put("无CD", "cooldown = 0");

        // ========== 元素属性映射 ==========
        semanticMap.put("火属性", "element = 'fire' OR element_type = 1");
        semanticMap.put("水属性", "element = 'water' OR element_type = 2");
        semanticMap.put("风属性", "element = 'wind' OR element_type = 3");
        semanticMap.put("土属性", "element = 'earth' OR element_type = 4");
        semanticMap.put("光属性", "element = 'light' OR element_type = 5");
        semanticMap.put("暗属性", "element = 'dark' OR element_type = 6");

        // ========== 职业映射 ==========
        semanticMap.put("战士", "class = 'warrior' OR class_id = 1");
        semanticMap.put("法师", "class = 'mage' OR class_id = 2");
        semanticMap.put("刺客", "class = 'assassin' OR class_id = 3");
        semanticMap.put("牧师", "class = 'priest' OR class_id = 4");

        // ========== NPC等级映射 ==========
        semanticMap.put("普通怪", "rank = 'normal' OR npc_grade = 0");
        semanticMap.put("精英怪", "rank = 'elite' OR npc_grade = 1");
        semanticMap.put("BOSS", "rank = 'boss' OR npc_grade >= 3");
        semanticMap.put("世界BOSS", "rank = 'world_boss' OR npc_grade = 4");

        // ========== 表别名 ==========
        addTableAlias("物品", Arrays.asList("item_armors", "item_weapons", "client_item"));
        addTableAlias("装备", Arrays.asList("item_armors", "item_weapons"));
        addTableAlias("武器", Arrays.asList("item_weapons"));
        addTableAlias("护甲", Arrays.asList("item_armors"));
        addTableAlias("技能", Arrays.asList("client_skill", "skill_data"));
        addTableAlias("任务", Arrays.asList("quest", "client_quest"));
        addTableAlias("NPC", Arrays.asList("client_npc", "npc_data"));
        addTableAlias("怪物", Arrays.asList("client_npc", "npc_data"));

        // ========== 字段别名 ==========
        Map<String, String> itemFields = new HashMap<>();
        itemFields.put("名称", "name");
        itemFields.put("等级", "level");
        itemFields.put("品质", "quality");
        itemFields.put("稀有度", "quality");
        itemFields.put("价格", "price");
        itemFields.put("售价", "price");
        fieldAliases.put("item", itemFields);

        Map<String, String> skillFields = new HashMap<>();
        skillFields.put("名称", "name");
        skillFields.put("伤害", "damage");
        skillFields.put("冷却", "cooldown");
        skillFields.put("CD", "cooldown");
        skillFields.put("消耗", "cost");
        skillFields.put("MP消耗", "mp_cost");
        fieldAliases.put("skill", skillFields);

        log.info("游戏语义增强器初始化完成，加载 {} 个语义映射", semanticMap.size());
    }

    /**
     * 添加表别名
     */
    private void addTableAlias(String alias, List<String> tableNames) {
        tableAliases.put(alias, tableNames);
    }

    /**
     * 翻译自然语言查询为SQL片段提示
     */
    public String translateToSqlHints(String naturalQuery) {
        StringBuilder hints = new StringBuilder();
        hints.append("## 查询意图分析\n\n");

        // 检测品质/等级等条件
        for (Map.Entry<String, String> entry : semanticMap.entrySet()) {
            if (naturalQuery.contains(entry.getKey())) {
                hints.append(String.format("- 检测到 \"%s\"，建议使用: `%s`\n",
                    entry.getKey(), entry.getValue()));
            }
        }

        // 检测表名
        for (Map.Entry<String, List<String>> entry : tableAliases.entrySet()) {
            if (naturalQuery.contains(entry.getKey())) {
                hints.append(String.format("- 检测到 \"%s\"，相关表: %s\n",
                    entry.getKey(), String.join(", ", entry.getValue())));
            }
        }

        return hints.toString();
    }

    /**
     * 智能表名推荐
     */
    public List<String> suggestTables(String query) {
        Set<String> tables = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> entry : tableAliases.entrySet()) {
            if (query.contains(entry.getKey())) {
                tables.addAll(entry.getValue());
            }
        }

        // 如果没有匹配，返回所有物品相关表作为默认
        if (tables.isEmpty()) {
            tables.addAll(Arrays.asList("item_armors", "item_weapons", "client_skill", "quest"));
        }

        return new ArrayList<>(tables);
    }

    /**
     * 获取字段建议
     */
    public Map<String, String> getFieldSuggestions(String category) {
        return fieldAliases.getOrDefault(category, Collections.emptyMap());
    }


    /**
     * 初始化动态语义（从实际数据库发现）
     * 注意：此方法可能耗时较长（5-10秒），建议在后台线程调用
     */
    private void initializeDynamicSemantics() {
        try {
            long startTime = System.currentTimeMillis();
            log.info("开始初始化动态语义（可能需要5-10秒）...");

            dynamicBuilder = new DynamicSemanticBuilder(jdbcTemplate);
            dynamicBuilder.buildSemanticMappings();

            // 将动态发现的映射合并到静态语义中
            mergeDynamicSemantics();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("动态语义初始化完成，耗时 {} ms", elapsed);
        } catch (Exception e) {
            log.error("动态语义初始化失败", e);
            dynamicSemanticsEnabled = false;
        }
    }

    /**
     * 合并动态语义到静态语义
     */
    private void mergeDynamicSemantics() {
        if (dynamicBuilder == null) {
            return;
        }

        Map<String, DynamicSemanticBuilder.SemanticMapping> dynamicMappings = dynamicBuilder.getMappings();
        int mergedCount = 0;

        for (DynamicSemanticBuilder.SemanticMapping mapping : dynamicMappings.values()) {
            if (!mapping.isEnabled()) {
                continue;
            }

            String keyword = mapping.getKeyword();
            String sqlCondition = mapping.getSqlCondition();

            // 如果静态语义中没有这个关键词，添加它
            if (!semanticMap.containsKey(keyword)) {
                semanticMap.put(keyword, sqlCondition);
                mergedCount++;
            }
        }

        log.info("合并了 {} 个动态语义映射到静态语义库", mergedCount);
    }

    /**
     * 生成完整的语义增强提示词（包含动态语义）
     */
    public String generateSemanticPrompt() {
        StringBuilder sb = new StringBuilder();

        // 静态语义部分
        sb.append(generateStaticSemanticPrompt());
        sb.append("\n\n");

        // 动态语义部分
        if (dynamicSemanticsEnabled && dynamicBuilder != null) {
            sb.append("---\n\n");
            sb.append(dynamicBuilder.generateDynamicPrompt());
        }

        return sb.toString();
    }

    /**
     * 生成静态语义提示词
     */
    private String generateStaticSemanticPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 游戏语义映射表（预定义）\n\n");
        sb.append("当用户使用以下游戏术语时，请转换为对应的SQL条件：\n\n");

        // 按类别组织
        sb.append("## 品质/稀有度\n");
        for (Map.Entry<String, String> entry : semanticMap.entrySet()) {
            if (entry.getKey().contains("品质") || entry.getKey().contains("普通") ||
                entry.getKey().contains("精良") || entry.getKey().contains("稀有") ||
                entry.getKey().contains("史诗") || entry.getKey().contains("传说")) {
                sb.append(String.format("- \"%s\" → `%s`\n", entry.getKey(), entry.getValue()));
            }
        }

        sb.append("\n## 元素属性\n");
        for (Map.Entry<String, String> entry : semanticMap.entrySet()) {
            if (entry.getKey().contains("属性")) {
                sb.append(String.format("- \"%s\" → `%s`\n", entry.getKey(), entry.getValue()));
            }
        }

        sb.append("\n## NPC等级\n");
        for (Map.Entry<String, String> entry : semanticMap.entrySet()) {
            if (entry.getKey().contains("怪") || entry.getKey().contains("BOSS")) {
                sb.append(String.format("- \"%s\" → `%s`\n", entry.getKey(), entry.getValue()));
            }
        }

        sb.append("\n## 表名映射\n");
        for (Map.Entry<String, List<String>> entry : tableAliases.entrySet()) {
            sb.append(String.format("- \"%s\" → %s\n",
                entry.getKey(), String.join(", ", entry.getValue())));
        }

        return sb.toString();
    }

    /**
     * 获取动态语义构建器（供UI使用）
     */
    public DynamicSemanticBuilder getDynamicBuilder() {
        return dynamicBuilder;
    }

    /**
     * 启用/禁用动态语义
     */
    public void setDynamicSemanticsEnabled(boolean enabled) {
        this.dynamicSemanticsEnabled = enabled;
        log.info("动态语义已{}", enabled ? "启用" : "禁用");
    }

    /**
     * 重新加载动态语义（同步版本，会阻塞）
     */
    public void reloadDynamicSemantics() {
        log.info("重新加载动态语义...");
        dynamicSemanticsInitialized = false;
        initializeDynamicSemantics();
        dynamicSemanticsInitialized = true;
    }

    /**
     * 异步重新加载动态语义
     */
    public void reloadDynamicSemanticsAsync(Runnable onComplete) {
        log.info("异步重新加载动态语义...");
        dynamicSemanticsInitialized = false;

        new Thread(() -> {
            initializeDynamicSemantics();
            dynamicSemanticsInitialized = true;
            if (onComplete != null) {
                onComplete.run();
            }
        }, "DynamicSemantics-Reload").start();
    }

    /**
     * 检查动态语义是否已初始化
     */
    public boolean isDynamicSemanticsInitialized() {
        return dynamicSemanticsInitialized;
    }

    /**
     * 动态学习新的语义映射（从成功的查询中学习）
     */
    public void learnFromQuery(String naturalQuery, String successfulSql) {
        // TODO: 实现机器学习逻辑，从成功的查询中提取新的语义映射
        log.debug("记录成功查询：{} → {}", naturalQuery, successfulSql);
    }
}
