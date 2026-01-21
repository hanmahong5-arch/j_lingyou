package red.jiuzhou.ops.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.core.AuditLog;
import red.jiuzhou.ops.core.AuditLog.OperationType;
import red.jiuzhou.ops.core.CacheManager;
import red.jiuzhou.ops.core.OperationChain;
import red.jiuzhou.ops.db.SqlServerConnection;
import red.jiuzhou.ops.model.GameCharacter;
import red.jiuzhou.ops.model.GameGuild;
import red.jiuzhou.ops.model.GameItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 游戏运维核心服务
 *
 * 封装常用的游戏运维操作：
 * - 角色管理（查询、改名、删除等）
 * - 公会管理（查询、改名、解散等）
 * - 物品管理（查询、发送、删除等）
 * - 系统维护（清理、优化等）
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class GameOpsService {

    private static final Logger log = LoggerFactory.getLogger(GameOpsService.class);

    private final SqlServerConnection connection;
    private final AuditLog auditLog;
    private final CacheManager cache;

    public GameOpsService(SqlServerConnection connection) {
        this.connection = connection;
        this.auditLog = AuditLog.getInstance();
        this.cache = CacheManager.getInstance();
    }

    /**
     * Get underlying connection (for advanced operations)
     */
    public SqlServerConnection getConnection() {
        return connection;
    }

    // ==================== 角色管理 ====================

    /**
     * 根据角色ID查询角色信息
     */
    public Optional<GameCharacter> getCharacterById(int charId) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetCharInfo",
                    Map.of("char_id", charId)
            );
            if (!results.isEmpty()) {
                return Optional.of(GameCharacter.fromMap(results.get(0)));
            }
        } catch (Exception e) {
            log.error("查询角色失败: charId={}", charId, e);
        }
        return Optional.empty();
    }

    /**
     * 根据角色名称查询角色信息
     */
    public Optional<GameCharacter> getCharacterByName(String name) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetCharInfoByName",
                    Map.of("name", name)
            );
            if (!results.isEmpty()) {
                return Optional.of(GameCharacter.fromMap(results.get(0)));
            }
        } catch (Exception e) {
            log.error("查询角色失败: name={}", name, e);
        }
        return Optional.empty();
    }

    /**
     * 查询账号下所有角色
     */
    public List<GameCharacter> getCharactersByAccount(int accountId) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetCharIdList",
                    Map.of("account_id", accountId)
            );
            return results.stream()
                    .map(GameCharacter::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("查询账号角色失败: accountId={}", accountId, e);
            return List.of();
        }
    }

    /**
     * 修改角色名称
     */
    public boolean changeCharacterName(int charId, String newName) {
        try {
            log.info("修改角色名称: charId={}, newName={}", charId, newName);
            connection.executeProcedure(
                    "aion_ChangeCharName",
                    Map.of("char_id", charId, "new_name", newName)
            );
            return true;
        } catch (Exception e) {
            log.error("修改角色名称失败: charId={}, newName={}", charId, newName, e);
            return false;
        }
    }

    /**
     * 修改角色种族
     */
    public boolean changeCharacterRace(int charId, String newRace) {
        try {
            log.info("修改角色种族: charId={}, newRace={}", charId, newRace);
            connection.executeProcedure(
                    "aion_ChangeCharRace",
                    Map.of("char_id", charId, "race", newRace)
            );
            return true;
        } catch (Exception e) {
            log.error("修改角色种族失败: charId={}, newRace={}", charId, newRace, e);
            return false;
        }
    }

    /**
     * 删除角色
     */
    public boolean deleteCharacter(int charId) {
        try {
            log.warn("删除角色: charId={}", charId);
            connection.executeProcedure(
                    "aion_DeleteChar",
                    Map.of("char_id", charId)
            );
            return true;
        } catch (Exception e) {
            log.error("删除角色失败: charId={}", charId, e);
            return false;
        }
    }

    // ==================== 公会管理 ====================

    /**
     * 根据公会ID查询公会信息
     */
    public Optional<GameGuild> getGuildById(int guildId) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetGuild",
                    Map.of("guild_id", guildId)
            );
            if (!results.isEmpty()) {
                return Optional.of(GameGuild.fromMap(results.get(0)));
            }
        } catch (Exception e) {
            log.error("查询公会失败: guildId={}", guildId, e);
        }
        return Optional.empty();
    }

    /**
     * 根据公会名称查询公会信息
     */
    public Optional<GameGuild> getGuildByName(String name) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetGuildByName",
                    Map.of("name", name)
            );
            if (!results.isEmpty()) {
                return Optional.of(GameGuild.fromMap(results.get(0)));
            }
        } catch (Exception e) {
            log.error("查询公会失败: name={}", name, e);
        }
        return Optional.empty();
    }

    /**
     * 查询公会成员列表
     */
    public List<Map<String, Object>> getGuildMembers(int guildId) {
        try {
            return connection.callProcedure(
                    "aion_GetGuildMemberList",
                    Map.of("guild_id", guildId)
            );
        } catch (Exception e) {
            log.error("查询公会成员失败: guildId={}", guildId, e);
            return List.of();
        }
    }

    /**
     * 修改公会名称
     */
    public boolean changeGuildName(int guildId, String newName) {
        try {
            log.info("修改公会名称: guildId={}, newName={}", guildId, newName);
            connection.executeProcedure(
                    "aion_ChangeGuildName",
                    Map.of("guild_id", guildId, "new_name", newName)
            );
            return true;
        } catch (Exception e) {
            log.error("修改公会名称失败: guildId={}, newName={}", guildId, newName, e);
            return false;
        }
    }

    /**
     * 解散公会
     */
    public boolean disbandGuild(int guildId) {
        try {
            log.warn("解散公会: guildId={}", guildId);
            connection.executeProcedure(
                    "aion_DeleteGuild",
                    Map.of("guild_id", guildId)
            );
            return true;
        } catch (Exception e) {
            log.error("解散公会失败: guildId={}", guildId, e);
            return false;
        }
    }

    // ==================== 物品管理 ====================

    /**
     * 查询角色背包物品
     */
    public List<GameItem> getCharacterItems(int charId, int storageType) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetItemList",
                    Map.of("char_id", charId, "storage_type", storageType)
            );
            return results.stream()
                    .map(GameItem::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("查询角色物品失败: charId={}, storageType={}", charId, storageType, e);
            return List.of();
        }
    }

    /**
     * 给角色发送物品
     */
    public boolean sendItem(int charId, int itemId, int count) {
        try {
            log.info("发送物品: charId={}, itemId={}, count={}", charId, itemId, count);
            connection.executeProcedure(
                    "aion_AddItemAmount",
                    Map.of("char_id", charId, "item_id", itemId, "count", count)
            );
            return true;
        } catch (Exception e) {
            log.error("发送物品失败: charId={}, itemId={}, count={}", charId, itemId, count, e);
            return false;
        }
    }

    /**
     * 删除物品
     */
    public boolean deleteItem(long itemUniqueId) {
        try {
            log.warn("删除物品: itemUniqueId={}", itemUniqueId);
            connection.executeProcedure(
                    "aion_DeleteItem",
                    Map.of("item_unique_id", itemUniqueId)
            );
            return true;
        } catch (Exception e) {
            log.error("删除物品失败: itemUniqueId={}", itemUniqueId, e);
            return false;
        }
    }

    /**
     * 查询角色资产统计
     */
    public Map<String, Object> getCharacterAssets(int charId) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetKinaAsset",
                    Map.of("char_id", charId)
            );
            if (!results.isEmpty()) {
                return results.get(0);
            }
        } catch (Exception e) {
            log.error("查询角色资产失败: charId={}", charId, e);
        }
        return Map.of();
    }

    // ==================== 系统维护 ====================

    /**
     * 清理低级不活跃角色
     */
    public int cleanupInactiveCharacters(int inactiveDays, int maxLevel) {
        try {
            log.warn("清理不活跃角色: days={}, maxLevel={}", inactiveDays, maxLevel);
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_CheckOldUserOfLowlevelToDelete",
                    Map.of("days", inactiveDays, "max_level", maxLevel)
            );
            if (!results.isEmpty() && results.get(0).containsKey("deleted_count")) {
                return ((Number) results.get(0).get("deleted_count")).intValue();
            }
            return 0;
        } catch (Exception e) {
            log.error("清理不活跃角色失败", e);
            return -1;
        }
    }

    /**
     * 重建数据库索引
     */
    public boolean reindexDatabase() {
        try {
            log.info("开始重建数据库索引");
            connection.executeProcedure("aion_dbreindex_all", null);
            log.info("数据库索引重建完成");
            return true;
        } catch (Exception e) {
            log.error("数据库索引重建失败", e);
            return false;
        }
    }

    /**
     * 清理过期好友请求
     */
    public int cleanupExpiredBuddyRequests() {
        try {
            log.info("清理过期好友请求");
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_clearexpiredofflinebuddy",
                    null
            );
            if (!results.isEmpty() && results.get(0).containsKey("cleared_count")) {
                return ((Number) results.get(0).get("cleared_count")).intValue();
            }
            return 0;
        } catch (Exception e) {
            log.error("清理过期好友请求失败", e);
            return -1;
        }
    }

    /**
     * 获取数据库性能统计
     */
    public Map<String, Object> getDatabaseProfile() {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetDbProfile_Summary",
                    null
            );
            if (!results.isEmpty()) {
                return results.get(0);
            }
        } catch (Exception e) {
            log.error("获取数据库性能统计失败", e);
        }
        return Map.of();
    }

    // ==================== 账号管理 ====================

    /**
     * 封禁账号
     */
    public boolean banAccount(int accountId, String reason, int durationHours) {
        try {
            log.warn("封禁账号: accountId={}, reason={}, hours={}", accountId, reason, durationHours);
            connection.executeProcedure(
                    "aion_BanAccount",
                    Map.of(
                            "account_id", accountId,
                            "reason", reason,
                            "duration_hours", durationHours
                    )
            );
            return true;
        } catch (Exception e) {
            log.error("封禁账号失败: accountId={}", accountId, e);
            return false;
        }
    }

    /**
     * 解封账号
     */
    public boolean unbanAccount(int accountId) {
        try {
            log.info("解封账号: accountId={}", accountId);
            connection.executeProcedure(
                    "aion_UnbanAccount",
                    Map.of("account_id", accountId)
            );
            return true;
        } catch (Exception e) {
            log.error("解封账号失败: accountId={}", accountId, e);
            return false;
        }
    }

    // ==================== 统计查询 ====================

    /**
     * 获取在线玩家数量
     * 从 user_data 表查询 online 状态的玩家
     */
    public int getOnlinePlayerCount() {
        try {
            // 直接查询 user_data 表的 online 字段
            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList("SELECT COUNT(*) as cnt FROM user_data WHERE online = 1");
            if (!results.isEmpty() && results.get(0).containsKey("cnt")) {
                return ((Number) results.get(0).get("cnt")).intValue();
            }
        } catch (Exception e) {
            log.warn("获取在线玩家数量失败，尝试备用方案: {}", e.getMessage());
            // 备用方案：如果 online 字段不存在，返回 0
        }
        return 0;
    }

    /**
     * 获取服务器统计信息
     * 使用 Aion 数据库实际的表名：user_data, guild 等
     */
    public Map<String, Object> getServerStatistics() {
        Map<String, Object> stats = new HashMap<>();
        try {
            // 在线玩家
            stats.put("onlinePlayers", getOnlinePlayerCount());

            // 总角色数 - 从 user_data 表查询
            try {
                List<Map<String, Object>> charCount = connection.getJdbcTemplate()
                        .queryForList("SELECT COUNT(*) as cnt FROM user_data");
                if (!charCount.isEmpty()) {
                    stats.put("totalCharacters", charCount.get(0).get("cnt"));
                }
            } catch (Exception e) {
                log.warn("获取角色总数失败: {}", e.getMessage());
                stats.put("totalCharacters", 0);
            }

            // 总公会数 - 尝试多个可能的表名
            try {
                List<Map<String, Object>> guildCount = connection.getJdbcTemplate()
                        .queryForList("SELECT COUNT(*) as cnt FROM guild");
                if (!guildCount.isEmpty()) {
                    stats.put("totalGuilds", guildCount.get(0).get("cnt"));
                }
            } catch (Exception e1) {
                // 尝试 legion 表
                try {
                    List<Map<String, Object>> guildCount = connection.getJdbcTemplate()
                            .queryForList("SELECT COUNT(*) as cnt FROM legion");
                    if (!guildCount.isEmpty()) {
                        stats.put("totalGuilds", guildCount.get(0).get("cnt"));
                    }
                } catch (Exception e2) {
                    log.warn("获取公会总数失败: {}", e2.getMessage());
                    stats.put("totalGuilds", 0);
                }
            }

            // 今日新增角色
            try {
                List<Map<String, Object>> todayCount = connection.getJdbcTemplate()
                        .queryForList("SELECT COUNT(*) as cnt FROM user_data WHERE CAST(creation_date AS DATE) = CAST(GETDATE() AS DATE)");
                if (!todayCount.isEmpty()) {
                    stats.put("todayNewCharacters", todayCount.get(0).get("cnt"));
                }
            } catch (Exception e) {
                stats.put("todayNewCharacters", 0);
            }

        } catch (Exception e) {
            log.error("获取服务器统计失败", e);
        }
        return stats;
    }

    // ==================== 角色扩展操作 ====================

    /**
     * 获取角色信息（用于面板）
     */
    public GameCharacter getCharacterInfo(int charId) {
        return getCharacterById(charId).orElse(null);
    }

    /**
     * 搜索角色
     */
    public List<GameCharacter> searchCharacters(Map<String, Object> conditions, int limit) {
        try {
            // Aion 数据库使用 user_data 表存储角色数据
            StringBuilder sql = new StringBuilder(
                    "SELECT TOP (?) * FROM user_data WHERE 1=1"
            );
            List<Object> params = new ArrayList<>();
            params.add(limit);

            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if ("name".equals(key)) {
                    sql.append(" AND name LIKE ?");
                    params.add("%" + value + "%");
                } else if ("min_level".equals(key)) {
                    sql.append(" AND level >= ?");
                    params.add(value);
                } else {
                    sql.append(" AND ").append(key).append(" = ?");
                    params.add(value);
                }
            }

            sql.append(" ORDER BY char_id DESC");

            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql.toString(), params.toArray());

            return results.stream()
                    .map(GameCharacter::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("搜索角色失败: conditions={}", conditions, e);
            return List.of();
        }
    }

    /**
     * 设置角色等级
     */
    public boolean setCharacterLevel(int charId, int level) {
        try {
            log.info("设置角色等级: charId={}, level={}", charId, level);
            connection.executeProcedure(
                    "aion_SetCharLevel",
                    Map.of("char_id", charId, "level", level)
            );
            auditLog.success(OperationType.CHARACTER, "设置等级",
                    "角色:" + charId, "等级:" + level);
            return true;
        } catch (Exception e) {
            log.error("设置角色等级失败: charId={}, level={}", charId, level, e);
            auditLog.failure(OperationType.CHARACTER, "设置等级", "角色:" + charId, e.getMessage());
            return false;
        }
    }

    /**
     * 设置角色金币
     */
    public boolean setCharacterKinah(int charId, long kinah) {
        try {
            log.info("设置角色金币: charId={}, kinah={}", charId, kinah);
            connection.executeProcedure(
                    "aion_SetKinah",
                    Map.of("char_id", charId, "kinah", kinah)
            );
            auditLog.success(OperationType.CHARACTER, "设置金币",
                    "角色:" + charId, "金币:" + kinah);
            return true;
        } catch (Exception e) {
            log.error("设置角色金币失败: charId={}, kinah={}", charId, kinah, e);
            return false;
        }
    }

    /**
     * 踢玩家下线
     */
    public boolean kickPlayer(int charId, String reason) {
        try {
            log.warn("踢玩家下线: charId={}, reason={}", charId, reason);
            connection.executeProcedure(
                    "aion_KickPlayer",
                    Map.of("char_id", charId, "reason", reason != null ? reason : "管理员操作")
            );
            auditLog.warning(OperationType.CHARACTER, "踢下线",
                    "角色:" + charId, reason);
            return true;
        } catch (Exception e) {
            log.error("踢玩家下线失败: charId={}", charId, e);
            return false;
        }
    }

    /**
     * 传送角色到指定坐标
     */
    public boolean teleportCharacter(int charId, int worldId, float x, float y, float z) {
        try {
            log.info("传送角色: charId={}, worldId={}, pos=({},{},{})", charId, worldId, x, y, z);
            connection.executeProcedure(
                    "aion_TeleportChar",
                    Map.of(
                            "char_id", charId,
                            "world_id", worldId,
                            "x", x, "y", y, "z", z
                    )
            );
            auditLog.success(OperationType.CHARACTER, "传送角色",
                    "角色:" + charId, String.format("世界:%d 坐标:(%.1f,%.1f,%.1f)", worldId, x, y, z));
            return true;
        } catch (Exception e) {
            log.error("传送角色失败: charId={}", charId, e);
            return false;
        }
    }

    // ==================== 公会扩展操作 ====================

    /**
     * 搜索公会
     */
    public List<GameGuild> searchGuilds(Map<String, Object> conditions, int limit) {
        try {
            // Aion 数据库中公会表可能是 guild 或 legion
            StringBuilder sql = new StringBuilder(
                    "SELECT TOP (?) * FROM guild WHERE 1=1"
            );
            List<Object> params = new ArrayList<>();
            params.add(limit);

            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if ("name".equals(key)) {
                    sql.append(" AND name LIKE ?");
                    params.add("%" + value + "%");
                } else if ("min_level".equals(key)) {
                    sql.append(" AND level >= ?");
                    params.add(value);
                } else if ("id".equals(key)) {
                    sql.append(" AND id = ?");
                    params.add(value);
                } else {
                    sql.append(" AND ").append(key).append(" = ?");
                    params.add(value);
                }
            }

            sql.append(" ORDER BY level DESC, member_count DESC");

            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql.toString(), params.toArray());

            return results.stream()
                    .map(GameGuild::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("搜索公会失败: conditions={}", conditions, e);
            return List.of();
        }
    }

    /**
     * 获取所有公会
     */
    public List<GameGuild> getAllGuilds(int offset, int limit) {
        try {
            // Aion 数据库使用 guild 表存储公会数据
            String sql = """
                SELECT * FROM guild
                ORDER BY level DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql, offset, limit);

            return results.stream()
                    .map(GameGuild::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("获取公会列表失败", e);
            return List.of();
        }
    }

    /**
     * 公会改名（别名）
     */
    public void renameGuild(int guildId, String newName) {
        if (!changeGuildName(guildId, newName)) {
            throw new RuntimeException("公会改名失败");
        }
    }

    /**
     * 获取公会历史
     */
    public List<Map<String, Object>> getGuildHistory(int guildId) {
        try {
            return connection.callProcedure(
                    "aion_GetGuildHistoryList",
                    Map.of("guild_id", guildId)
            );
        } catch (Exception e) {
            log.error("获取公会历史失败: guildId={}", guildId, e);
            return List.of();
        }
    }

    /**
     * 更新公会公告
     */
    public void updateGuildAnnouncement(int guildId, String announcement) {
        try {
            connection.executeProcedure(
                    "aion_SetGuildAnnouncement",
                    Map.of("guild_id", guildId, "announcement", announcement)
            );
            auditLog.success(OperationType.GUILD, "修改公告", "公会:" + guildId, null);
        } catch (Exception e) {
            log.error("更新公会公告失败: guildId={}", guildId, e);
            throw new RuntimeException("更新公告失败: " + e.getMessage());
        }
    }

    /**
     * 设置公会等级
     */
    public boolean setGuildLevel(int guildId, int level) {
        try {
            connection.executeProcedure(
                    "aion_SetGuildLevel",
                    Map.of("guild_id", guildId, "level", level)
            );
            auditLog.success(OperationType.GUILD, "设置等级", "公会:" + guildId, "等级:" + level);
            return true;
        } catch (Exception e) {
            log.error("设置公会等级失败: guildId={}, level={}", guildId, level, e);
            return false;
        }
    }

    // ==================== 物品扩展操作 ====================

    /**
     * 按所有者查询物品
     */
    public List<GameItem> getItemsByOwner(int ownerId, Integer storageType) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT * FROM inventory WHERE item_owner = ?"
            );
            List<Object> params = new ArrayList<>();
            params.add(ownerId);

            if (storageType != null) {
                sql.append(" AND storage_type = ?");
                params.add(storageType);
            }

            sql.append(" ORDER BY slot");

            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql.toString(), params.toArray());

            return results.stream()
                    .map(GameItem::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("查询物品失败: ownerId={}", ownerId, e);
            return List.of();
        }
    }

    /**
     * 按所有者名称查询物品
     */
    public List<GameItem> getItemsByOwnerName(String ownerName, Integer storageType) {
        try {
            // 使用 user_data 表（Aion 的角色数据表）关联查询
            StringBuilder sql = new StringBuilder("""
                SELECT i.* FROM inventory i
                JOIN user_data p ON i.item_owner = p.id
                WHERE p.name = ?
                """);
            List<Object> params = new ArrayList<>();
            params.add(ownerName);

            if (storageType != null) {
                sql.append(" AND i.storage_type = ?");
                params.add(storageType);
            }

            sql.append(" ORDER BY i.slot");

            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql.toString(), params.toArray());

            return results.stream()
                    .map(GameItem::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("查询物品失败: ownerName={}", ownerName, e);
            return List.of();
        }
    }

    /**
     * 按物品ID查询
     */
    public List<GameItem> getItemsByItemId(int itemId) {
        try {
            String sql = "SELECT TOP 500 * FROM inventory WHERE item_id = ? ORDER BY item_unique_id DESC";
            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql, itemId);

            return results.stream()
                    .map(GameItem::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("查询物品失败: itemId={}", itemId, e);
            return List.of();
        }
    }

    /**
     * 按唯一ID查询物品
     */
    public GameItem getItemByUniqueId(long uniqueId) {
        try {
            String sql = "SELECT * FROM inventory WHERE item_unique_id = ?";
            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql, uniqueId);

            if (!results.isEmpty()) {
                return GameItem.fromMap(results.get(0));
            }
        } catch (Exception e) {
            log.error("查询物品失败: uniqueId={}", uniqueId, e);
        }
        return null;
    }

    /**
     * 发送物品（增强版）
     */
    public void sendItem(int charId, int itemId, int count, int enchantLevel, boolean soulBound) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("char_id", charId);
            params.put("item_id", itemId);
            params.put("item_count", count);
            params.put("enchant_level", enchantLevel);
            params.put("is_soul_bound", soulBound ? 1 : 0);

            connection.executeProcedure("aion_AddItemAmount", params);

            auditLog.success(OperationType.ITEM, "发送物品",
                    "角色:" + charId,
                    String.format("物品:%d x%d +%d", itemId, count, enchantLevel));
        } catch (Exception e) {
            log.error("发送物品失败: charId={}, itemId={}", charId, itemId, e);
            throw new RuntimeException("发送物品失败: " + e.getMessage());
        }
    }

    /**
     * 转移物品
     */
    public void transferItem(long itemUniqueId, int targetCharId) {
        try {
            connection.executeProcedure(
                    "aion_TransferItem",
                    Map.of("item_unique_id", itemUniqueId, "target_char_id", targetCharId)
            );
            auditLog.success(OperationType.ITEM, "转移物品",
                    "唯一ID:" + itemUniqueId, "目标:" + targetCharId);
        } catch (Exception e) {
            log.error("转移物品失败: itemUniqueId={}, targetCharId={}", itemUniqueId, targetCharId, e);
            throw new RuntimeException("转移物品失败: " + e.getMessage());
        }
    }

    // ==================== 邮件系统 ====================

    /**
     * 发送系统邮件
     */
    public boolean sendSystemMail(int charId, String title, String content,
                                   int itemId, int itemCount, long kinah) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("char_id", charId);
            params.put("title", title);
            params.put("content", content);
            params.put("item_id", itemId);
            params.put("item_count", itemCount);
            params.put("kinah", kinah);

            connection.executeProcedure("aion_SendSystemMail", params);

            auditLog.success(OperationType.MAIL, "发送邮件",
                    "角色:" + charId, title);
            return true;
        } catch (Exception e) {
            log.error("发送系统邮件失败: charId={}", charId, e);
            return false;
        }
    }

    /**
     * 批量发送系统邮件
     */
    public int sendBulkMail(List<Integer> charIds, String title, String content,
                            int itemId, int itemCount, long kinah) {
        int sent = 0;
        for (int charId : charIds) {
            if (sendSystemMail(charId, title, content, itemId, itemCount, kinah)) {
                sent++;
            }
        }
        auditLog.success(OperationType.MAIL, "批量邮件",
                "数量:" + charIds.size(), "成功:" + sent);
        return sent;
    }

    // ==================== 拍卖系统 ====================

    /**
     * 查询拍卖行物品
     */
    public List<Map<String, Object>> searchAuctionItems(int itemId, int minPrice, int maxPrice, int limit) {
        try {
            return connection.callProcedure(
                    "aion_SearchAuction",
                    Map.of(
                            "item_id", itemId,
                            "min_price", minPrice,
                            "max_price", maxPrice,
                            "limit", limit
                    )
            );
        } catch (Exception e) {
            log.error("搜索拍卖行失败", e);
            return List.of();
        }
    }

    /**
     * 取消拍卖
     */
    public boolean cancelAuction(long auctionId) {
        try {
            connection.executeProcedure(
                    "aion_CancelAuction",
                    Map.of("auction_id", auctionId)
            );
            auditLog.success(OperationType.AUCTION, "取消拍卖", "ID:" + auctionId, null);
            return true;
        } catch (Exception e) {
            log.error("取消拍卖失败: auctionId={}", auctionId, e);
            return false;
        }
    }

    // ==================== 任务系统 ====================

    /**
     * 完成任务
     */
    public boolean completeQuest(int charId, int questId) {
        try {
            connection.executeProcedure(
                    "aion_CompleteQuest",
                    Map.of("char_id", charId, "quest_id", questId)
            );
            auditLog.success(OperationType.QUEST, "完成任务",
                    "角色:" + charId, "任务:" + questId);
            return true;
        } catch (Exception e) {
            log.error("完成任务失败: charId={}, questId={}", charId, questId, e);
            return false;
        }
    }

    /**
     * 重置任务
     */
    public boolean resetQuest(int charId, int questId) {
        try {
            connection.executeProcedure(
                    "aion_ResetQuest",
                    Map.of("char_id", charId, "quest_id", questId)
            );
            auditLog.success(OperationType.QUEST, "重置任务",
                    "角色:" + charId, "任务:" + questId);
            return true;
        } catch (Exception e) {
            log.error("重置任务失败: charId={}, questId={}", charId, questId, e);
            return false;
        }
    }

    // ==================== 技能系统 ====================

    /**
     * 添加技能
     */
    public boolean addSkill(int charId, int skillId, int skillLevel) {
        try {
            connection.executeProcedure(
                    "aion_AddSkill",
                    Map.of("char_id", charId, "skill_id", skillId, "skill_level", skillLevel)
            );
            auditLog.success(OperationType.CHARACTER, "添加技能",
                    "角色:" + charId, "技能:" + skillId + " Lv" + skillLevel);
            return true;
        } catch (Exception e) {
            log.error("添加技能失败: charId={}, skillId={}", charId, skillId, e);
            return false;
        }
    }

    /**
     * 移除技能
     */
    public boolean removeSkill(int charId, int skillId) {
        try {
            connection.executeProcedure(
                    "aion_RemoveSkill",
                    Map.of("char_id", charId, "skill_id", skillId)
            );
            auditLog.success(OperationType.CHARACTER, "移除技能",
                    "角色:" + charId, "技能:" + skillId);
            return true;
        } catch (Exception e) {
            log.error("移除技能失败: charId={}, skillId={}", charId, skillId, e);
            return false;
        }
    }

    // ==================== 批量操作 (带事务链) ====================

    /**
     * 批量设置角色等级（使用事务链）
     */
    public OperationChain.ChainResult batchSetLevel(List<Integer> charIds, int level) {
        OperationChain.Builder builder = OperationChain.builder("批量设置等级");

        for (int charId : charIds) {
            builder.step("设置角色" + charId + "等级",
                    () -> setCharacterLevel(charId, level),
                    () -> log.warn("回滚角色{}等级设置", charId));
        }

        builder.step("记录审计", () -> {
            auditLog.success(OperationType.CHARACTER, "批量设置等级",
                    "数量:" + charIds.size(), "等级:" + level);
        }, null);

        return builder.build().execute();
    }

    /**
     * 批量发送物品（使用事务链）
     */
    public OperationChain.ChainResult batchSendItems(List<Integer> charIds,
                                                       int itemId, int count, int enchantLevel) {
        OperationChain.Builder builder = OperationChain.builder("批量发送物品");

        for (int charId : charIds) {
            builder.step("发送给角色" + charId,
                    () -> sendItem(charId, itemId, count, enchantLevel, false),
                    () -> log.warn("物品发送无法回滚: charId={}", charId));
        }

        builder.step("记录审计", () -> {
            auditLog.success(OperationType.ITEM, "批量发送物品",
                    "数量:" + charIds.size(),
                    String.format("物品:%d x%d +%d", itemId, count, enchantLevel));
        }, null);

        return builder.build().execute();
    }

    /**
     * 批量封禁账号
     */
    public OperationChain.ChainResult batchBanAccounts(List<Integer> accountIds,
                                                        String reason, int durationHours) {
        OperationChain.Builder builder = OperationChain.builder("批量封禁账号");

        for (int accountId : accountIds) {
            int finalAccountId = accountId;
            builder.step("封禁账号" + accountId,
                    () -> banAccount(finalAccountId, reason, durationHours),
                    () -> {
                        try {
                            unbanAccount(finalAccountId);
                        } catch (Exception e) {
                            log.error("回滚解封失败: accountId={}", finalAccountId, e);
                        }
                    });
        }

        return builder.build().execute();
    }
}
