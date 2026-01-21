package red.jiuzhou.ops.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 游戏物品数据模型
 *
 * 对应 AionWorldLive 数据库中的物品数据
 *
 * @author yanxq
 * @date 2026-01-16
 */
public record GameItem(
        long itemUniqueId,      // 物品唯一ID
        int itemId,             // 物品模板ID
        String itemName,
        int ownerId,            // 所有者角色ID
        String ownerName,
        int count,              // 数量
        int slot,               // 背包槽位
        int storageType,        // 存储类型 (0=背包, 1=仓库, etc.)
        int enchantLevel,       // 强化等级
        int fusionedItemId,     // 融合物品ID
        int optionalSocket,     // 可选孔数
        int optionalFusionSocket,
        LocalDateTime expireTime,
        boolean isSoulBound,    // 是否绑定
        boolean isEquipped      // 是否装备中
) {
    /**
     * Storage type constants
     */
    public static final int STORAGE_INVENTORY = 0;      // 背包
    public static final int STORAGE_REGULAR_WAREHOUSE = 1;  // 仓库
    public static final int STORAGE_ACCOUNT_WAREHOUSE = 2;  // 账号仓库
    public static final int STORAGE_LEGION_WAREHOUSE = 3;   // 公会仓库

    /**
     * Check if item is expired
     */
    public boolean isExpired() {
        return expireTime != null && expireTime.isBefore(LocalDateTime.now());
    }

    /**
     * Check if item has enchantment
     */
    public boolean isEnchanted() {
        return enchantLevel > 0;
    }

    /**
     * Check if item is fused
     */
    public boolean isFused() {
        return fusionedItemId > 0;
    }

    /**
     * Get storage type display
     */
    public String getStorageTypeDisplay() {
        return switch (storageType) {
            case STORAGE_INVENTORY -> "背包";
            case STORAGE_REGULAR_WAREHOUSE -> "仓库";
            case STORAGE_ACCOUNT_WAREHOUSE -> "账号仓库";
            case STORAGE_LEGION_WAREHOUSE -> "公会仓库";
            default -> "存储" + storageType;
        };
    }

    /**
     * Get status display
     */
    public String getStatusDisplay() {
        StringBuilder sb = new StringBuilder();
        if (isEquipped) sb.append("装备中 ");
        if (isSoulBound) sb.append("已绑定 ");
        if (isEnchanted()) sb.append("+").append(enchantLevel).append(" ");
        if (isExpired()) sb.append("已过期 ");
        return sb.toString().trim();
    }

    /**
     * Get display name with enchant
     */
    public String getDisplayName() {
        if (isEnchanted()) {
            return itemName + " +" + enchantLevel;
        }
        return itemName;
    }

    /**
     * Create from database row
     */
    public static GameItem fromMap(Map<String, Object> row) {
        return new GameItem(
                getLong(row, "item_unique_id"),
                getInt(row, "item_id"),
                getString(row, "item_name"),
                getInt(row, "item_owner"),
                getString(row, "owner_name"),
                getInt(row, "item_count"),
                getInt(row, "slot"),
                getInt(row, "storage_type"),
                getInt(row, "enchant_level"),
                getInt(row, "fusioned_item"),
                getInt(row, "optional_socket"),
                getInt(row, "optional_fusion_socket"),
                getDateTime(row, "expire_time"),
                getBoolean(row, "is_soul_bound"),
                getBoolean(row, "is_equipped")
        );
    }

    private static int getInt(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }

    private static long getLong(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }

    private static String getString(Map<String, Object> row, String key) {
        Object val = row.get(key);
        return val != null ? val.toString() : "";
    }

    private static boolean getBoolean(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(val.toString());
    }

    private static LocalDateTime getDateTime(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return null;
        if (val instanceof LocalDateTime dt) return dt;
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }
}
