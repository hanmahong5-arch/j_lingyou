package red.jiuzhou.ops.model;

import java.time.LocalDateTime;

/**
 * 游戏角色数据模型
 *
 * 对应 AionWorldLive 数据库中的角色数据
 *
 * @author yanxq
 * @date 2026-01-16
 */
public record GameCharacter(
        int charId,
        int accountId,
        String name,
        String race,           // ELYOS / ASMODIAN
        String charClass,      // WARRIOR, MAGE, etc.
        int level,
        long exp,
        long kinah,            // 金币
        int hp,
        int mp,
        int dp,                // 神圣力量
        int titleId,
        int guildId,
        String guildName,
        int worldId,
        float x,
        float y,
        float z,
        int heading,
        boolean online,
        LocalDateTime createTime,
        LocalDateTime lastOnline,
        LocalDateTime deleteTime,
        int deletionState      // 0=正常, 1=待删除
) {
    /**
     * Check if character is deleted
     */
    public boolean isDeleted() {
        return deletionState > 0;
    }

    /**
     * Check if character has guild
     */
    public boolean hasGuild() {
        return guildId > 0;
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
     * Get display class name
     */
    public String getClassDisplay() {
        return switch (charClass) {
            case "WARRIOR" -> "战士";
            case "GLADIATOR" -> "剑星";
            case "TEMPLAR" -> "守护星";
            case "SCOUT" -> "斥候";
            case "ASSASSIN" -> "杀星";
            case "RANGER" -> "弓星";
            case "MAGE" -> "法师";
            case "SORCERER" -> "魔道星";
            case "SPIRIT_MASTER" -> "精灵星";
            case "PRIEST" -> "祭司";
            case "CLERIC" -> "治愈星";
            case "CHANTER" -> "护法星";
            case "ENGINEER" -> "工程师";
            case "GUNNER" -> "枪炮星";
            case "RIDER" -> "机甲星";
            case "ARTIST" -> "艺术家";
            case "BARD" -> "吟游星";
            case "PAINTER" -> "彩绘星";
            default -> charClass;
        };
    }

    /**
     * Get status display
     */
    public String getStatusDisplay() {
        if (isDeleted()) return "已删除";
        if (online) return "在线";
        return "离线";
    }

    /**
     * Get position string
     */
    public String getPositionString() {
        return String.format("世界%d (%.1f, %.1f, %.1f)", worldId, x, y, z);
    }

    /**
     * Create from database row
     */
    public static GameCharacter fromMap(java.util.Map<String, Object> row) {
        return new GameCharacter(
                getInt(row, "char_id"),
                getInt(row, "account_id"),
                getString(row, "name"),
                getString(row, "race"),
                getString(row, "player_class"),
                getInt(row, "level"),
                getLong(row, "exp"),
                getLong(row, "kinah"),
                getInt(row, "hp"),
                getInt(row, "mp"),
                getInt(row, "dp"),
                getInt(row, "title_id"),
                getInt(row, "guild_id"),
                getString(row, "guild_name"),
                getInt(row, "world_id"),
                getFloat(row, "x"),
                getFloat(row, "y"),
                getFloat(row, "z"),
                getInt(row, "heading"),
                getBoolean(row, "online"),
                getDateTime(row, "create_time"),
                getDateTime(row, "last_online"),
                getDateTime(row, "delete_time"),
                getInt(row, "deletion_state")
        );
    }

    private static int getInt(java.util.Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }

    private static long getLong(java.util.Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        return Long.parseLong(val.toString());
    }

    private static float getFloat(java.util.Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return 0f;
        if (val instanceof Number n) return n.floatValue();
        return Float.parseFloat(val.toString());
    }

    private static String getString(java.util.Map<String, Object> row, String key) {
        Object val = row.get(key);
        return val != null ? val.toString() : "";
    }

    private static boolean getBoolean(java.util.Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(val.toString());
    }

    private static LocalDateTime getDateTime(java.util.Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val == null) return null;
        if (val instanceof LocalDateTime dt) return dt;
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }
}
