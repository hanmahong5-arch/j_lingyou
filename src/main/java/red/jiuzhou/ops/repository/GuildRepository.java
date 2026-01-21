package red.jiuzhou.ops.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.core.AuditLog;
import red.jiuzhou.ops.core.AuditLog.OperationType;
import red.jiuzhou.ops.core.CacheManager;
import red.jiuzhou.ops.db.SqlServerConnection;
import red.jiuzhou.ops.model.GameGuild;

import java.util.*;

/**
 * 公会数据仓储实现
 *
 * 提供公会数据的 CRUD 操作，集成：
 * - 缓存层
 * - 审计日志
 * - 存储过程调用
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class GuildRepository implements GameRepository<GameGuild, Integer> {

    private static final Logger log = LoggerFactory.getLogger(GuildRepository.class);
    private static final String CACHE_PREFIX = "guild:";

    private final SqlServerConnection connection;
    private final CacheManager cache;
    private final AuditLog auditLog;

    public GuildRepository(SqlServerConnection connection) {
        this.connection = connection;
        this.cache = CacheManager.getInstance();
        this.auditLog = AuditLog.getInstance();
    }

    @Override
    public Optional<GameGuild> findById(Integer id) {
        // Check cache first
        String cacheKey = CACHE_PREFIX + id;
        GameGuild cached = cache.get(cacheKey, GameGuild.class);
        if (cached != null) {
            log.debug("缓存命中: {}", cacheKey);
            return Optional.of(cached);
        }

        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetGuildInfo",
                    Map.of("guild_id", id)
            );

            if (!results.isEmpty()) {
                GameGuild guild = GameGuild.fromMap(results.get(0));
                cache.put(cacheKey, guild, 300); // 5 minutes TTL
                return Optional.of(guild);
            }
        } catch (Exception e) {
            log.error("查询公会失败: id={}", id, e);
            auditLog.failure(OperationType.GUILD, "查询公会", "ID:" + id, e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public Optional<GameGuild> findByName(String name) {
        String cacheKey = CACHE_PREFIX + "name:" + name;
        GameGuild cached = cache.get(cacheKey, GameGuild.class);
        if (cached != null) {
            return Optional.of(cached);
        }

        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetGuildInfoByName",
                    Map.of("name", name)
            );

            if (!results.isEmpty()) {
                GameGuild guild = GameGuild.fromMap(results.get(0));
                cache.put(cacheKey, guild, 300);
                cache.put(CACHE_PREFIX + guild.guildId(), guild, 300);
                return Optional.of(guild);
            }
        } catch (Exception e) {
            log.error("查询公会失败: name={}", name, e);
        }

        return Optional.empty();
    }

    @Override
    public List<GameGuild> findAll(int offset, int limit) {
        try {
            String sql = """
                SELECT TOP (?) * FROM guilds
                WHERE disband_time IS NULL
                ORDER BY id
                OFFSET ? ROWS
                """;
            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql, limit, offset);

            return results.stream()
                    .map(GameGuild::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("查询公会列表失败", e);
            return List.of();
        }
    }

    @Override
    public List<GameGuild> findByCondition(Map<String, Object> conditions, int offset, int limit) {
        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM guilds WHERE disband_time IS NULL");
            List<Object> params = new ArrayList<>();

            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if ("min_level".equals(key)) {
                    sql.append(" AND level >= ?");
                    params.add(value);
                } else if ("name".equals(key)) {
                    sql.append(" AND name LIKE ?");
                    params.add("%" + value + "%");
                } else {
                    sql.append(" AND ").append(key).append(" = ?");
                    params.add(value);
                }
            }

            sql.append(" ORDER BY id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            params.add(offset);
            params.add(limit);

            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql.toString(), params.toArray());

            return results.stream()
                    .map(GameGuild::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("条件查询公会失败", e);
            return List.of();
        }
    }

    @Override
    public List<GameGuild> search(String keyword, int limit) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_SearchGuilds",
                    Map.of("keyword", "%" + keyword + "%", "limit", limit)
            );

            return results.stream()
                    .map(GameGuild::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("搜索公会失败: keyword={}", keyword, e);
            return List.of();
        }
    }

    @Override
    public long count() {
        try {
            return connection.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM guilds WHERE disband_time IS NULL",
                    Long.class
            );
        } catch (Exception e) {
            log.error("统计公会数量失败", e);
            return 0;
        }
    }

    @Override
    public long countByCondition(Map<String, Object> conditions) {
        try {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM guilds WHERE disband_time IS NULL");
            List<Object> params = new ArrayList<>();

            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                sql.append(" AND ").append(entry.getKey()).append(" = ?");
                params.add(entry.getValue());
            }

            return connection.getJdbcTemplate().queryForObject(sql.toString(), Long.class, params.toArray());
        } catch (Exception e) {
            log.error("条件统计公会失败", e);
            return 0;
        }
    }

    @Override
    public boolean exists(Integer id) {
        return findById(id).isPresent();
    }

    @Override
    public GameGuild save(GameGuild entity) {
        throw new UnsupportedOperationException("公会创建应通过游戏客户端进行");
    }

    @Override
    public List<GameGuild> saveAll(List<GameGuild> entities) {
        throw new UnsupportedOperationException("批量公会创建不支持");
    }

    @Override
    public boolean update(Integer id, Map<String, Object> fields) {
        try {
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();

                String procName = switch (field) {
                    case "name" -> "aion_ChangeGuildName";
                    case "announcement" -> "aion_SetGuildAnnouncement";
                    case "level" -> "aion_SetGuildLevel";
                    default -> null;
                };

                if (procName != null) {
                    connection.executeProcedure(procName, Map.of("guild_id", id, field, value));
                    auditLog.success(OperationType.GUILD, "更新字段",
                            "公会:" + id, field + "=" + value);
                }
            }

            // Invalidate cache
            cache.remove(CACHE_PREFIX + id);
            return true;
        } catch (Exception e) {
            log.error("更新公会失败: id={}, fields={}", id, fields, e);
            auditLog.failure(OperationType.GUILD, "更新公会", "ID:" + id, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(Integer id) {
        try {
            connection.executeProcedure("aion_DisbandGuild", Map.of("guild_id", id));
            cache.remove(CACHE_PREFIX + id);
            auditLog.warning(OperationType.GUILD, "解散公会", "ID:" + id, null);
            return true;
        } catch (Exception e) {
            log.error("解散公会失败: id={}", id, e);
            auditLog.failure(OperationType.GUILD, "解散公会", "ID:" + id, e.getMessage());
            return false;
        }
    }

    @Override
    public int deleteAll(List<Integer> ids) {
        int deleted = 0;
        for (Integer id : ids) {
            if (delete(id)) {
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public List<GameGuild> findByIds(List<Integer> ids) {
        return ids.stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    @Override
    public int batchUpdate(Map<String, Object> fields, Map<String, Object> conditions) {
        List<GameGuild> guilds = findByCondition(conditions, 0, 10000);
        int updated = 0;

        for (GameGuild g : guilds) {
            if (update(g.guildId(), fields)) {
                updated++;
            }
        }

        auditLog.success(OperationType.GUILD, "批量更新",
                "条件:" + conditions, "更新 " + updated + " 个");
        return updated;
    }

    @Override
    public List<Map<String, Object>> executeQuery(String sql, Object... params) {
        return connection.getJdbcTemplate().queryForList(sql, params);
    }

    @Override
    public int executeUpdate(String sql, Object... params) {
        return connection.getJdbcTemplate().update(sql, params);
    }

    @Override
    public List<Map<String, Object>> callProcedure(String procedureName, Map<String, Object> params) {
        return connection.callProcedure(procedureName, params);
    }

    // ==================== 特定方法 ====================

    /**
     * 获取公会成员列表
     */
    public List<Map<String, Object>> getMembers(int guildId) {
        try {
            return connection.callProcedure(
                    "aion_GetGuildMemberList",
                    Map.of("guild_id", guildId)
            );
        } catch (Exception e) {
            log.error("获取公会成员失败: guildId={}", guildId, e);
            return List.of();
        }
    }

    /**
     * 获取公会历史记录
     */
    public List<Map<String, Object>> getHistory(int guildId) {
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
     * 获取公会仓库日志
     */
    public List<Map<String, Object>> getWarehouseHistory(int guildId) {
        try {
            return connection.callProcedure(
                    "aion_GetGuildWarehouseHistoryList",
                    Map.of("guild_id", guildId)
            );
        } catch (Exception e) {
            log.error("获取公会仓库日志失败: guildId={}", guildId, e);
            return List.of();
        }
    }

    /**
     * 修改公会公告
     */
    public void updateAnnouncement(int guildId, String announcement) {
        update(guildId, Map.of("announcement", announcement));
    }

    /**
     * 清除公会缓存
     */
    public void evictCache(int guildId) {
        cache.remove(CACHE_PREFIX + guildId);
    }

    /**
     * 清除所有公会缓存
     */
    public void evictAllCache() {
        cache.removeByPrefix(CACHE_PREFIX);
    }
}
