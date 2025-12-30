package red.jiuzhou.validation.server;

import java.util.*;

/**
 * XML文件验证规则注册表 - 基于服务器日志分析的结果
 *
 * <p>根据对MainServer和NPCServer日志的深度分析，为每个XML文件构建专属验证规则
 *
 * <h3>日志分析总结：</h3>
 * <ul>
 *   <li>MainServer日志：100,698行，主要错误：unknown item name（19,559次）</li>
 *   <li>NPCServer日志：105,654行，主要错误：undefined token（45,571次）</li>
 *   <li>TOP错误字段：__order_index(44,324次), status_fx_slot_lv(405次), toggle_id(378次)</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2025-12-29
 */
public class XmlFileValidationRules {

    private static final Map<String, FileValidationRule> RULES = new HashMap<>();

    static {
        initializeRules();
    }

    private static void initializeRules() {
        // ===========================
        // 1. ItemDB 物品数据库规则
        // ===========================
        RULES.put("items", new FileValidationRule.Builder("items")
                .xmlFileName("items.xml")
                .description("物品数据库 - 禁用扩展drop字段和__order_index")
                // 黑名单字段（从NPCServer日志分析得出）
                .addBlacklistFields(
                        "__order_index",        // 44,324次错误
                        "drop_prob_6", "drop_prob_7", "drop_prob_8", "drop_prob_9",
                        "drop_monster_6", "drop_monster_7", "drop_monster_8", "drop_monster_9",
                        "drop_item_6", "drop_item_7", "drop_item_8", "drop_item_9",
                        "drop_each_member_6", "drop_each_member_7", "drop_each_member_8", "drop_each_member_9",
                        "erect",                // 60次错误
                        "monsterbook_race"      // 30次错误
                )
                // 必填字段
                .addRequiredFields("id", "name", "level")
                // 值域约束
                .addNumericConstraint("stack", 1, 9999, 1)
                .addNumericConstraint("level", 0, 100, 1)
                .build()
        );

        // ===========================
        // 2. SkillDB 技能数据库规则
        // ===========================
        RULES.put("skill_base", new FileValidationRule.Builder("skill_base")
                .xmlFileName("skill_base.xml")
                .description("技能数据库 - 禁用status_fx_slot_lv和toggle_id字段")
                // 黑名单字段（从NPCServer日志分析得出）
                .addBlacklistFields(
                        "__order_index",        // 44,324次错误
                        "status_fx_slot_lv",    // 405次错误
                        "toggle_id",            // 378次错误
                        "is_familiar_skill"     // 288次错误
                )
                // 必填字段
                .addRequiredFields("id", "name", "level")
                // 值域约束
                .addNumericConstraint("casting_delay", 0, 30000, 0)  // 最大30秒
                .addNumericConstraint("cool_time", 0, 3600000, 0)    // 最大1小时
                .addNumericConstraint("level", 1, 100, 1)
                .build()
        );

        // ===========================
        // 3. QuestDB 任务数据库规则
        // ===========================
        RULES.put("quest_random_rewards", new FileValidationRule.Builder("quest_random_rewards")
                .xmlFileName("quest_random_rewards.xml")
                .description("任务随机奖励 - 需验证物品引用完整性")
                // 黑名单字段
                .addBlacklistFields("__order_index")
                // 必填字段
                .addRequiredFields("id")
                // 引用完整性约束（item字段引用items表）
                .addReferenceField("item", "items")
                .build()
        );

        // ===========================
        // 4. NpcDB NPC数据库规则
        // ===========================
        RULES.put("npcs", new FileValidationRule.Builder("npcs")
                .xmlFileName("npcs.xml")
                .description("NPC数据库")
                .addBlacklistFields("__order_index")
                .addRequiredFields("id", "name", "level")
                .addNumericConstraint("level", 1, 100, 1)
                .build()
        );

        // ===========================
        // 5. 通用物品规则（适用于所有item_xxx表）
        // ===========================
        String[] itemCategories = {
                "item_weapons", "item_armors", "item_accessories",
                "item_consumables", "item_materials", "item_quest"
        };

        for (String category : itemCategories) {
            RULES.put(category, new FileValidationRule.Builder(category)
                    .xmlFileName(category + ".xml")
                    .description("物品分类: " + category)
                    .addBlacklistFields(
                            "__order_index",
                            "drop_prob_6", "drop_prob_7", "drop_prob_8", "drop_prob_9",
                            "drop_monster_6", "drop_monster_7", "drop_monster_8", "drop_monster_9",
                            "drop_item_6", "drop_item_7", "drop_item_8", "drop_item_9",
                            "drop_each_member_6", "drop_each_member_7", "drop_each_member_8", "drop_each_member_9"
                    )
                    .addRequiredFields("id", "name")
                    .build()
            );
        }

        // ===========================
        // 6. 技能相关表的通用规则
        // ===========================
        String[] skillTables = {
                "skill_learns", "skill_charge", "skill_conflictcounts",
                "skill_damageattenuation", "skill_prohibit", "skill_qualification",
                "skill_randomdamage", "skill_signetdata"
        };

        for (String table : skillTables) {
            RULES.put(table, new FileValidationRule.Builder(table)
                    .xmlFileName(table + ".xml")
                    .description("技能相关表: " + table)
                    .addBlacklistFields("__order_index", "status_fx_slot_lv", "toggle_id")
                    .build()
            );
        }

        // ===========================
        // 7. 宠物系统规则
        // ===========================
        String[] petTables = {
                "toypets", "toypet_feed", "toypet_buff", "toypet_doping",
                "toypet_looting", "toypet_warehouse", "toypet_merchant",
                "familiars", "familiar_contract", "familiar_sgrade_ratio"
        };

        for (String table : petTables) {
            RULES.put(table, new FileValidationRule.Builder(table)
                    .xmlFileName(table + ".xml")
                    .description("宠物系统: " + table)
                    .addBlacklistFields("__order_index")
                    .build()
            );
        }

        // ===========================
        // 8. 任务系统规则
        // ===========================
        String[] questTables = {
                "quest", "Quest_SimpleHunt", "Quest_SimpleCollectItem",
                "Quest_SimpleTalk", "Quest_SimpleGather", "Quest_SimpleUseItem",
                "Quest_SimpleSerialHunt", "Quest_SimpleItemPlay", "Quest_CombineTask",
                "data_driven_quest", "jumping_addquest", "jumping_endquest",
                "npcfactions_quest", "challenge_task"
        };

        for (String table : questTables) {
            RULES.put(table, new FileValidationRule.Builder(table)
                    .xmlFileName(table + ".xml")
                    .description("任务系统: " + table)
                    .addBlacklistFields("__order_index")
                    .build()
            );
        }

        // ===========================
        // 9. 深渊系统规则
        // ===========================
        String[] abyssTables = {
                "abyss", "abyss_op", "abyss_mist_times", "abyss_levelgroup",
                "abyss_race_bonuses", "abyss_raid_carrier_times",
                "abysspoint_world_mod", "abyss_leader_skill"
        };

        for (String table : abyssTables) {
            RULES.put(table, new FileValidationRule.Builder(table)
                    .xmlFileName(table + ".xml")
                    .description("深渊系统: " + table)
                    .addBlacklistFields("__order_index")
                    .build()
            );
        }

        // ===========================
        // 10. PVP系统规则
        // ===========================
        String[] pvpTables = {
                "pvp_rank", "pvp_exp_table", "pvp_exp_mod_table",
                "pvp_mod_table", "pvp_world_adjust", "spvp_time_table",
                "ranking"
        };

        for (String table : pvpTables) {
            RULES.put(table, new FileValidationRule.Builder(table)
                    .xmlFileName(table + ".xml")
                    .description("PVP系统: " + table)
                    .addBlacklistFields("__order_index")
                    .build()
            );
        }

        // ===========================
        // 11. NPC商店系统规则
        // ===========================
        String[] shopTables = {
                "goodslist", "abgoodslist", "purchase_list",
                "trade_in_list"
        };

        for (String table : shopTables) {
            RULES.put(table, new FileValidationRule.Builder(table)
                    .xmlFileName(table + ".xml")
                    .description("NPC商店: " + table)
                    .addBlacklistFields("__order_index")
                    .build()
            );
        }

        // ===========================
        // 12. 副本系统规则
        // ===========================
        String[] instanceTables = {
                "instance_bonusattr", "instance_cooltime", "instance_cooltime2",
                "instance_creation", "instance_pool", "instance_restrict",
                "instance_scaling", "instant_dungeon_define",
                "instant_dungeon_battleground", "instant_dungeon_tournament"
        };

        for (String table : instanceTables) {
            RULES.put(table, new FileValidationRule.Builder(table)
                    .xmlFileName(table + ".xml")
                    .description("副本系统: " + table)
                    .addBlacklistFields("__order_index")
                    .build()
            );
        }

        // ===========================
        // 13. 物品强化/合成系统规则
        // ===========================
        String[] enchantTables = {
                "enchant_cpstone", "item_skill_enhance", "item_random_option",
                "item_option_probability", "combine_recipe", "disassembly_item",
                "exchange_equipment", "item_upgrade", "item_multi_return",
                "setitem"
        };

        for (String table : enchantTables) {
            RULES.put(table, new FileValidationRule.Builder(table)
                    .xmlFileName(table + ".xml")
                    .description("物品强化/合成: " + table)
                    .addBlacklistFields("__order_index")
                    .build()
            );
        }
    }

    /**
     * 获取指定表的验证规则
     */
    public static Optional<FileValidationRule> getRule(String tableName) {
        return Optional.ofNullable(RULES.get(tableName));
    }

    /**
     * 检查是否存在规则
     */
    public static boolean hasRule(String tableName) {
        return RULES.containsKey(tableName);
    }

    /**
     * 获取所有已注册的表名
     */
    public static Set<String> getAllTableNames() {
        return Collections.unmodifiableSet(RULES.keySet());
    }

    /**
     * 获取所有规则
     */
    public static Collection<FileValidationRule> getAllRules() {
        return Collections.unmodifiableCollection(RULES.values());
    }

    /**
     * 统计总规则数
     */
    public static int getTotalRuleCount() {
        return RULES.values().stream()
                .mapToInt(FileValidationRule::getTotalRuleCount)
                .sum();
    }

    /**
     * 生成规则统计报告
     */
    public static String generateRuleSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=".repeat(80)).append("\n");
        sb.append("服务器合规性验证规则统计\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append(String.format("共注册 %d 个XML文件/表的验证规则\n", RULES.size()));
        sb.append(String.format("总规则数：%d 条\n\n", getTotalRuleCount()));

        sb.append("规则详情：\n");
        sb.append("-".repeat(80)).append("\n");

        for (FileValidationRule rule : RULES.values()) {
            sb.append(String.format("表: %-30s 规则数: %3d  黑名单字段: %2d  必填字段: %2d\n",
                    rule.getTableName(),
                    rule.getTotalRuleCount(),
                    rule.getBlacklistFields().size(),
                    rule.getRequiredFields().size()
            ));
        }

        sb.append("=".repeat(80)).append("\n");
        return sb.toString();
    }

    /**
     * 获取黑名单字段统计（用于分析报告）
     */
    public static Map<String, Integer> getBlacklistFieldStatistics() {
        Map<String, Integer> stats = new HashMap<>();

        for (FileValidationRule rule : RULES.values()) {
            for (String field : rule.getBlacklistFields()) {
                stats.merge(field, 1, Integer::sum);
            }
        }

        return stats;
    }

    /**
     * 私有构造函数（工具类）
     */
    private XmlFileValidationRules() {
        throw new AssertionError("工具类不应被实例化");
    }
}
