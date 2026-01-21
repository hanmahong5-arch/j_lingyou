package red.jiuzhou.ops.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 游戏公会数据模型
 *
 * 对应 AionWorldLive 数据库中的公会数据
 *
 * @author yanxq
 * @date 2026-01-16
 */
public record GameGuild(
        int guildId,
        String name,
        int level,
        int contribution,       // 贡献点
        int memberCount,
        int maxMembers,
        int leaderId,           // 会长角色ID
        String leaderName,
        String race,            // ELYOS / ASMODIAN
        long kinah,             // 公会资金
        int warehouseLevel,
        String emblem,          // 公会徽章
        String announcement,    // 公告
        LocalDateTime createTime,
        LocalDateTime disbandTime,
        int rank                // 公会排名
) {
    /**
     * Check if guild is disbanded
     */
    public boolean isDisbanded() {
        return disbandTime != null;
    }

    /**
     * Check if guild is full
     */
    public boolean isFull() {
        return memberCount >= maxMembers;
    }

    /**
     * Get available slots
     */
    public int getAvailableSlots() {
        return maxMembers - memberCount;
    }

    /**
     * Get display race name
     */
    public String getRaceDisplay() {
        return switch (race) {
            case "ELYOS" -> "天族";
            case "ASMODIAN" -> "魔族";
            default -> race;
        };
    }

    /**
     * Get status display
     */
    public String getStatusDisplay() {
        if (isDisbanded()) return "已解散";
        return "正常";
    }

    /**
     * Create from database row
     */
    public static GameGuild fromMap(Map<String, Object> row) {
        return new GameGuild(
                getInt(row, "id"),
                getString(row, "name"),
                getInt(row, "level"),
                getInt(row, "contribution_points"),
                getInt(row, "member_count"),
                getInt(row, "max_members"),
                getInt(row, "leader_id"),
                getString(row, "leader_name"),
                getString(row, "race"),
                getLong(row, "kinah"),
                getInt(row, "warehouse_level"),
                getString(row, "emblem"),
                getString(row, "announcement"),
                getDateTime(row, "create_time"),
                getDateTime(row, "disband_time"),
                getInt(row, "rank")
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

    private static LocalDateTime getDateTime(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return null;
        if (val instanceof LocalDateTime dt) return dt;
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }
}
