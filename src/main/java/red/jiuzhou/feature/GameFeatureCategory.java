package red.jiuzhou.feature;

import red.jiuzhou.analysis.aion.AionMechanismCategory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏功能抽象层枚举
 *
 * <p>将底层的27个游戏机制分类（AionMechanismCategory）聚合为12个面向设计师的游戏功能模块。
 * 每个功能模块包含一组相关的机制和数据表，提供高层次的抽象，使设计师能够以游戏功能的视角
 * （如"创建副本"、"编辑任务"）而非技术视角（如"修改10个关联表"）来操作游戏数据。
 *
 * <p><b>核心设计理念：</b>
 * <ul>
 *   <li>功能级抽象：设计师思考"创建副本"而非"修改instance_cooltime等10个表"</li>
 *   <li>模板驱动：从现有数据复制，系统自动调整ID和引用</li>
 *   <li>智能表单：字段类型自动识别，引用字段下拉选择</li>
 * </ul>
 *
 * <p><b>三层抽象架构：</b>
 * <pre>
 * 功能层（本类）：12个游戏功能模块（副本、活动、任务链等）
 *         ↓ 基于
 * 机制层（AionMechanismCategory）：27个机制分类（物品、技能、NPC等）
 *         ↓ 映射到
 * 数据层：数据库表 ↔ 6,508个XML文件
 * </pre>
 *
 * @author Claude
 * @version 1.0
 * @since 2025-12-30
 */
public enum GameFeatureCategory {

    // ========== 核心游戏功能 ==========

    /**
     * 副本管理
     * <p>创建副本、配置Boss、设置掉落等副本相关的所有内容
     * <p>关联机制：副本系统、掉落系统、NPC系统
     * <p>主表：instance_cooltime（副本配置）
     * <p>关联表：instance_*、drop_*、npc_*
     */
    INSTANCE_MANAGER("副本管理", "创建副本、配置Boss、设置掉落",
            "instance_cooltime",
            AionMechanismCategory.INSTANCE,
            AionMechanismCategory.DROP,
            AionMechanismCategory.NPC),

    /**
     * 任务编辑
     * <p>创建任务链、设置奖励、绑定NPC等任务相关的所有内容
     * <p>关联机制：任务系统、NPC系统、物品系统
     * <p>主表：quest（任务配置）
     * <p>关联表：quest_*、npc_*、item_*
     */
    QUEST_EDITOR("任务编辑", "创建任务链、设置奖励、绑定NPC",
            "quest",
            AionMechanismCategory.QUEST,
            AionMechanismCategory.NPC,
            AionMechanismCategory.ITEM),

    /**
     * 物品配置
     * <p>创建装备、设置属性、配置合成等物品相关的所有内容
     * <p>关联机制：物品系统、强化系统、制作系统
     * <p>主表：items（物品配置）
     * <p>关联表：item_*、enchant_*、combine_*
     */
    ITEM_CONFIG("物品配置", "创建装备、设置属性、配置合成",
            "items",
            AionMechanismCategory.ITEM,
            AionMechanismCategory.ENCHANT,
            AionMechanismCategory.CRAFT),

    /**
     * NPC设计
     * <p>创建怪物/NPC、设置AI、配置技能等NPC相关的所有内容
     * <p>关联机制：NPC系统、NPC AI系统、技能系统
     * <p>主表：npc（NPC配置）
     * <p>关联表：npc_*, NpcAIPattern*, skill_*
     */
    NPC_DESIGN("NPC设计", "创建怪物/NPC、设置AI、配置技能",
            "npc",
            AionMechanismCategory.NPC,
            AionMechanismCategory.NPC_AI,
            AionMechanismCategory.SKILL),

    /**
     * 技能系统
     * <p>创建技能、设置效果、配置学习条件等技能相关的所有内容
     * <p>关联机制：技能系统
     * <p>主表：skill_base（技能配置）
     * <p>关联表：skill_*
     */
    SKILL_SYSTEM("技能系统", "创建技能、设置效果、配置学习条件",
            "skill_base",
            AionMechanismCategory.SKILL),

    /**
     * 活动策划
     * <p>创建限时活动、节日事件、登录奖励等活动相关的所有内容
     * <p>关联机制：时间事件系统
     * <p>主表：login_event（活动配置）
     * <p>关联表：*_event、*_times
     */
    EVENT_PLANNING("活动策划", "创建限时活动、节日事件、登录奖励",
            "login_event",
            AionMechanismCategory.TIME_EVENT),

    /**
     * 商店管理
     * <p>配置NPC商店、设置商品、调整价格等商店相关的所有内容
     * <p>关联机制：商店交易系统
     * <p>主表：goodslist（商店配置）
     * <p>关联表：goodslist、purchase_list、trade_in_list
     */
    SHOP_MANAGEMENT("商店管理", "配置NPC商店、设置商品、调整价格",
            "goodslist",
            AionMechanismCategory.SHOP),

    /**
     * 宠物坐骑
     * <p>创建宠物、坐骑、召唤物等宠物相关的所有内容
     * <p>关联机制：宠物系统
     * <p>主表：toypets（宠物配置）
     * <p>关联表：toypet_*、familiar_*
     */
    PET_MOUNT("宠物坐骑", "创建宠物、坐骑、召唤物",
            "toypets",
            AionMechanismCategory.PET),

    /**
     * 军团系统
     * <p>配置军团功能、军团领地、军团战等军团相关的所有内容
     * <p>关联机制：军团系统
     * <p>主表：legion_dominion（军团配置）
     * <p>关联表：legion_*、guild_*
     */
    LEGION_SYSTEM("军团系统", "配置军团功能、军团领地、军团战",
            "legion_dominion",
            AionMechanismCategory.LEGION),

    /**
     * 成长系统
     * <p>配置角色成长、称号系统等成长相关的所有内容
     * <p>关联机制：角色成长系统、称号系统
     * <p>主表：pcexp（经验配置）
     * <p>关联表：pcexp、title_*
     */
    GROWTH_SYSTEM("成长系统", "配置角色成长、称号系统",
            "pcexp",
            AionMechanismCategory.PLAYER_GROWTH,
            AionMechanismCategory.TITLE),

    /**
     * 世界配置
     * <p>配置传送门、副本入口、世界Boss等世界相关的所有内容
     * <p>关联机制：传送系统、副本系统
     * <p>主表：portal（传送配置）
     * <p>关联表：portal、rift、fly_path
     */
    WORLD_CONFIG("世界配置", "配置传送门、副本入口、世界Boss",
            "portal",
            AionMechanismCategory.PORTAL,
            AionMechanismCategory.INSTANCE),

    /**
     * 客户端资源
     * <p>管理UI文本、物品名称、技能描述等本地化字符串
     * <p>关联机制：客户端字符串系统
     * <p>主表：client_strings_item（物品字符串）
     * <p>关联表：client_strings_*
     */
    CLIENT_RESOURCES("客户端资源", "管理UI文本、物品名称、技能描述",
            "client_strings_item",
            AionMechanismCategory.CLIENT_STRINGS);

    // ========== 枚举字段 ==========

    private final String displayName;
    private final String description;
    private final String primaryTable;
    private final AionMechanismCategory[] relatedMechanisms;

    // ========== 构造函数 ==========

    /**
     * 构造游戏功能分类
     *
     * @param displayName 显示名称
     * @param description 功能描述
     * @param primaryTable 主表名称（用于模板提取）
     * @param relatedMechanisms 关联的游戏机制（可变参数）
     */
    GameFeatureCategory(String displayName, String description, String primaryTable,
                        AionMechanismCategory... relatedMechanisms) {
        this.displayName = displayName;
        this.description = description;
        this.primaryTable = primaryTable;
        this.relatedMechanisms = relatedMechanisms;
    }

    // ========== Getter 方法 ==========

    /**
     * 获取显示名称
     *
     * @return 显示名称（如"副本管理"）
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取功能描述
     *
     * @return 功能描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取主表名称
     * <p>主表用于模板提取，通常是该功能最核心的数据表
     *
     * @return 主表名称（如"instance_cooltime"）
     */
    public String getPrimaryTable() {
        return primaryTable;
    }

    /**
     * 获取关联的游戏机制
     *
     * @return 游戏机制数组
     */
    public AionMechanismCategory[] getRelatedMechanisms() {
        return relatedMechanisms;
    }

    // ========== 业务方法 ==========

    /**
     * 获取关联的所有数据表
     * <p>从关联的游戏机制中提取所有相关的数据表名称
     * <p><b>注意：</b>当前实现返回示例表名，实际应通过MechanismFileMapper查询实际表名
     *
     * @return 数据表名称列表（去重后）
     */
    public List<String> getRelatedTables() {
        // TODO: 集成MechanismFileMapper，从实际文件映射中获取表名
        // 当前返回基于机制名称的推断表名（占位实现）

        Set<String> tables = new HashSet<>();

        // 添加主表
        tables.add(primaryTable);

        // 根据关联机制推断相关表
        for (AionMechanismCategory mechanism : relatedMechanisms) {
            tables.addAll(inferTablesFromMechanism(mechanism));
        }

        return new ArrayList<>(tables);
    }

    /**
     * 从机制推断相关表名（占位实现）
     * <p><b>TODO：</b>应替换为从MechanismFileMapper查询实际表名
     *
     * @param mechanism 游戏机制
     * @return 推断的表名列表
     */
    private List<String> inferTablesFromMechanism(AionMechanismCategory mechanism) {
        // 简化实现：基于机制名称推断表名模式
        return switch (mechanism) {
            case INSTANCE -> List.of("instance_cooltime", "instance_rift");
            case DROP -> List.of("drop_list", "drop_npc");
            case NPC -> List.of("npc", "npc_spawn");
            case QUEST -> List.of("quest", "quest_rewards");
            case ITEM -> List.of("items", "item_armors", "item_weapons");
            case SKILL -> List.of("skill_base", "skill_learns");
            case ENCHANT -> List.of("item_enchant", "enchant_templates");
            case CRAFT -> List.of("combine_recipe", "assembly_items");
            case SHOP -> List.of("goodslist", "purchase_list");
            case TIME_EVENT -> List.of("login_event", "abyss_mist_times");
            case PET -> List.of("toypets", "toypet_feed");
            case LEGION -> List.of("legion_dominion", "guild_rank_reward");
            case PLAYER_GROWTH -> List.of("pcexp", "boost_times");
            case TITLE -> List.of("title_templates");
            case PORTAL -> List.of("portal", "fly_path");
            case CLIENT_STRINGS -> List.of("client_strings_item", "client_strings_skill");
            case NPC_AI -> List.of("NpcAIPattern");
            default -> Collections.emptyList();
        };
    }

    /**
     * 获取功能图标
     * <p>复用关联的首个机制的图标
     *
     * @return Emoji 图标
     */
    public String getIcon() {
        if (relatedMechanisms.length > 0) {
            return relatedMechanisms[0].getIcon();
        }
        return "🎮";  // 默认图标
    }

    /**
     * 获取功能颜色
     * <p>复用关联的首个机制的颜色
     *
     * @return CSS 颜色值
     */
    public String getColor() {
        if (relatedMechanisms.length > 0) {
            return relatedMechanisms[0].getColor();
        }
        return "#696969";  // 默认灰色
    }

    /**
     * 获取功能的详细信息摘要
     *
     * @return 包含名称、描述、主表、关联机制数量的摘要字符串
     */
    public String getSummary() {
        return String.format("%s %s - %s%n主表: %s%n关联机制: %d个",
                getIcon(), displayName, description, primaryTable, relatedMechanisms.length);
    }

    /**
     * 按显示名称查找游戏功能
     *
     * @param displayName 显示名称
     * @return 匹配的游戏功能，如果未找到则返回null
     */
    public static GameFeatureCategory findByDisplayName(String displayName) {
        for (GameFeatureCategory category : values()) {
            if (category.displayName.equals(displayName)) {
                return category;
            }
        }
        return null;
    }

    /**
     * 获取所有游戏功能的显示名称列表
     *
     * @return 显示名称列表
     */
    public static List<String> getAllDisplayNames() {
        return Arrays.stream(values())
                .map(GameFeatureCategory::getDisplayName)
                .collect(Collectors.toList());
    }
}
