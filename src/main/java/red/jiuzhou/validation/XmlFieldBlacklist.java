package red.jiuzhou.validation;

import java.util.Set;

/**
 * XML字段黑名单配置
 *
 * <p>根据Aion服务器启动日志分析，某些XML字段在旧版本服务器中不被识别，
 * 需要在导出时自动过滤。
 *
 * <p>数据来源（2025-12-29最新分析）：
 * <ul>
 *   <li>MainServer undefined日志：57,244行 undefined token 错误</li>
 *   <li>NPCServer undefined日志：45,581行 undefined token 错误</li>
 *   <li>总计：102,825行错误，覆盖22种不同的undefined字段类型</li>
 * </ul>
 *
 * <p>黑名单规模（2025-12-29双服务器交叉验证）：
 * <ul>
 *   <li>__order_index: 44,324次（NPCServer，最频繁，占97.2%）</li>
 *   <li>道具分解系统字段: 1,063+次/字段（MainServer）</li>
 *   <li>授权系统字段: 900+次（MainServer）</li>
 *   <li>CP系统字段: 415+次（MainServer）</li>
 *   <li>status_fx_slot_lv: 405次（NPCServer验证）</li>
 *   <li>toggle_id: 378次（NPCServer验证）</li>
 *   <li>掉落扩展字段: 40次（drop_each_member_6~9，双服务器）</li>
 * </ul>
 *
 * @author Claude
 * @version 2.1 (2025-12-29 - NPCServer日志交叉验证，新增drop_each_member字段)
 * @see DbToXmlGenerator
 */
public class XmlFieldBlacklist {

    /**
     * 全局黑名单字段（所有表通用）
     *
     * <p>这些字段是XML处理工具自动添加的内部字段，服务器不需要
     */
    public static final Set<String> GLOBAL_BLACKLIST = Set.of(
            "__order_index",      // XML工具内部排序索引（44324次错误）
            "__row_index",        // 可能的行索引字段
            "__original_id"       // 可能的原始ID字段
    );

    /**
     * 技能系统黑名单字段
     *
     * <p>NPCServer 无法识别的新版本技能字段
     * <p>统计：status_fx_slot_lv(135次), toggle_id(126次), is_familiar_skill(96次)
     */
    public static final Set<String> SKILL_BLACKLIST = Set.of(
            "status_fx_slot_lv",      // 状态效果槽位等级（135次错误）
            "toggle_id",              // 切换技能ID（126次错误）
            "is_familiar_skill",      // 宠物技能标记（96次错误）
            "physical_bonus_attr1",   // 物理奖励属性1（96次）
            "physical_bonus_attr2",   // 物理奖励属性2（94次）
            "physical_bonus_attr3",   // 物理奖励属性3（76次）
            "physical_bonus_attr4",   // 物理奖励属性4（42次）
            "magical_bonus_attr1",    // 魔法奖励属性1（96次）
            "magical_bonus_attr2",    // 魔法奖励属性2（94次）
            "magical_bonus_attr3",    // 魔法奖励属性3（76次）
            "magical_bonus_attr4",    // 魔法奖励属性4（42次）
            "skill_skin_id",          // 技能外观ID
            "enhanced_effect",        // 增强效果
            "cp_enchant_name",        // CP强化名称（415次）
            "cp_cost",                // CP消耗（415次）
            "cp_cost_adj",            // CP消耗调整（415次）
            "cp_count_max",           // CP最大数量（347次）
            "cp_cost_max"             // CP最大消耗（333次）
    );

    /**
     * NPC系统黑名单字段
     *
     * <p>统计：extra_npc_fx(44次), extra_npc_fx_bone(44次), camera(279次)
     */
    public static final Set<String> NPC_BLACKLIST = Set.of(
            "erect",             // 直立姿态（60次错误）
            "monsterbook_race",  // 怪物图鉴种族（30次错误）
            "ai_pattern_v2",     // 新版AI模式
            "behavior_tree",     // 行为树配置
            "extra_npc_fx",      // NPC额外特效（44次）
            "extra_npc_fx_bone", // NPC特效骨骼绑定（44次）
            "camera"             // 相机配置（279次）
    );

    /**
     * 掉落系统黑名单字段
     *
     * <p>服务器仅支持 drop_*_1~5，6~9为新增字段
     * <p>统计：NPCServer分析新增 drop_each_member_6~9（24次错误），MainServer（16次错误）
     */
    public static final Set<String> DROP_BLACKLIST = Set.of(
            "drop_prob_6", "drop_prob_7", "drop_prob_8", "drop_prob_9",
            "drop_monster_6", "drop_monster_7", "drop_monster_8", "drop_monster_9",
            "drop_item_6", "drop_item_7", "drop_item_8", "drop_item_9",
            "drop_each_member_6", "drop_each_member_7", "drop_each_member_8", "drop_each_member_9"
    );

    /**
     * 道具系统黑名单字段
     *
     * <p>统计：道具分解系统字段各1,063次，授权系统字段900次
     */
    public static final Set<String> ITEM_BLACKLIST = Set.of(
            "item_skin_override",   // 道具外观覆盖
            "dyeable_v2",          // 新版染色系统
            "appearance_slot",     // 外观槽位
            "glamour_id",          // 幻化ID
            // 道具分解系统字段（decompose_stuff.xml）
            "material_item",       // 分解材料道具（1063次）
            "item_level_min",      // 最低道具等级（1063次）
            "item_level_max",      // 最高道具等级（1063次）
            "enchant_min",         // 最低强化等级（163次）
            "enchant_max",         // 最高强化等级（163次）
            // 授权系统字段
            "authorize_min",       // 最低授权等级（900次）
            "authorize_max"        // 最高授权等级（900次）
    );

    /**
     * 玩法系统黑名单字段
     *
     * <p>统计：playtime相关字段各150次
     */
    public static final Set<String> PLAYTIME_BLACKLIST = Set.of(
            "playtime_cycle_reset_hour",     // 玩法周期重置小时（150次）
            "playtime_cycle_max_give_item"   // 玩法周期最大给予道具（150次）
    );

    /**
     * 前置条件系统黑名单字段
     *
     * <p>统计：pre_cond_min_pc_level(101次), pre_cond_min_pc_maxcp(93次)
     */
    public static final Set<String> PRECONDITION_BLACKLIST = Set.of(
            "pre_cond_min_pc_level",   // 前置条件最低角色等级（101次）
            "pre_cond_min_pc_maxcp"    // 前置条件最低角色CP（93次）
    );

    /**
     * 判断字段是否应该被过滤
     *
     * @param tableName 表名
     * @param fieldName 字段名
     * @return true表示应该过滤，false表示保留
     */
    public static boolean shouldFilter(String tableName, String fieldName) {
        // 1. 检查全局黑名单
        if (GLOBAL_BLACKLIST.contains(fieldName)) {
            return true;
        }

        // 2. 检查玩法系统黑名单（适用于所有表）
        if (PLAYTIME_BLACKLIST.contains(fieldName)) {
            return true;
        }

        // 3. 检查前置条件系统黑名单（适用于所有表）
        if (PRECONDITION_BLACKLIST.contains(fieldName)) {
            return true;
        }

        // 4. 根据表名检查专用黑名单
        if (tableName.startsWith("skill_") || tableName.contains("_skill_")) {
            return SKILL_BLACKLIST.contains(fieldName);
        }

        if (tableName.startsWith("npc_") || tableName.contains("_npc_")) {
            return NPC_BLACKLIST.contains(fieldName);
        }

        if (tableName.startsWith("item_") || tableName.contains("_item_")) {
            return ITEM_BLACKLIST.contains(fieldName);
        }

        if (tableName.contains("drop") || tableName.contains("quest")) {
            return DROP_BLACKLIST.contains(fieldName);
        }

        return false;
    }

    /**
     * 获取字段被过滤的原因
     *
     * @param tableName 表名
     * @param fieldName 字段名
     * @return 过滤原因，如果不过滤则返回null
     */
    public static String getFilterReason(String tableName, String fieldName) {
        if (GLOBAL_BLACKLIST.contains(fieldName)) {
            return "全局黑名单：XML工具内部字段，服务器不需要（__order_index出现44,312次错误）";
        }

        if (PLAYTIME_BLACKLIST.contains(fieldName)) {
            return "玩法系统：服务器不支持玩法时间周期配置（150次错误）";
        }

        if (PRECONDITION_BLACKLIST.contains(fieldName)) {
            return "前置条件系统：服务器不支持此类前置条件字段（101+93次错误）";
        }

        if ((tableName.startsWith("skill_") || tableName.contains("_skill_"))
                && SKILL_BLACKLIST.contains(fieldName)) {
            return "技能系统：旧版本服务器不识别此字段（status_fx_slot_lv 135次, cp_* 415+次错误）";
        }

        if ((tableName.startsWith("npc_") || tableName.contains("_npc_"))
                && NPC_BLACKLIST.contains(fieldName)) {
            return "NPC系统：新增字段，服务器不支持（extra_npc_fx 44次, camera 279次错误）";
        }

        if ((tableName.startsWith("item_") || tableName.contains("_item_"))
                && ITEM_BLACKLIST.contains(fieldName)) {
            return "道具系统：新版本特性，服务器不支持（分解系统1,063次, 授权系统900次错误）";
        }

        if ((tableName.contains("drop") || tableName.contains("quest"))
                && DROP_BLACKLIST.contains(fieldName)) {
            return "掉落系统：服务器仅支持 drop_*_1~5，扩展掉落字段6~9无效（72次错误，新增drop_each_member字段）";
        }

        return null;
    }

    /**
     * 统计表中被过滤的字段数量
     *
     * @param tableName 表名
     * @param allFields 所有字段集合
     * @return 被过滤的字段数量
     */
    public static int countFilteredFields(String tableName, Set<String> allFields) {
        return (int) allFields.stream()
                .filter(field -> shouldFilter(tableName, field))
                .count();
    }
}
