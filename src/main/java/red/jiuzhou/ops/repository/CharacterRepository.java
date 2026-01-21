package red.jiuzhou.ops.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.core.AuditLog;
import red.jiuzhou.ops.core.AuditLog.OperationType;
import red.jiuzhou.ops.core.CacheManager;
import red.jiuzhou.ops.db.SqlServerConnection;
import red.jiuzhou.ops.model.GameCharacter;

import java.util.*;

/**
 * 角色数据仓储实现
 *
 * 提供角色数据的 CRUD 操作，集成：
 * - 缓存层
 * - 审计日志
 * - 事件发布
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class CharacterRepository implements GameRepository<GameCharacter, Integer> {

    private static final Logger log = LoggerFactory.getLogger(CharacterRepository.class);
    private static final String CACHE_PREFIX = "char:";

    private final SqlServerConnection connection;
    private final CacheManager cache;
    private final AuditLog auditLog;

    public CharacterRepository(SqlServerConnection connection) {
        this.connection = connection;
        this.cache = CacheManager.getInstance();
        this.auditLog = AuditLog.getInstance();
    }

    @Override
    public Optional<GameCharacter> findById(Integer id) {
        // Check cache first
        String cacheKey = CACHE_PREFIX + id;
        GameCharacter cached = cache.get(cacheKey, GameCharacter.class);
        if (cached != null) {
            log.debug("缓存命中: {}", cacheKey);
            return Optional.of(cached);
        }

        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetCharInfo",
                    Map.of("char_id", id)
            );

            if (!results.isEmpty()) {
                GameCharacter character = GameCharacter.fromMap(results.get(0));
                cache.put(cacheKey, character, 300); // 5 minutes TTL
                return Optional.of(character);
            }
        } catch (Exception e) {
            log.error("查询角色失败: id={}", id, e);
            auditLog.failure(OperationType.QUERY, "查询角色", "ID:" + id, e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public Optional<GameCharacter> findByName(String name) {
        String cacheKey = CACHE_PREFIX + "name:" + name;
        GameCharacter cached = cache.get(cacheKey, GameCharacter.class);
        if (cached != null) {
            return Optional.of(cached);
        }

        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetCharInfoByName",
                    Map.of("name", name)
            );

            if (!results.isEmpty()) {
                GameCharacter character = GameCharacter.fromMap(results.get(0));
                cache.put(cacheKey, character, 300);
                cache.put(CACHE_PREFIX + character.charId(), character, 300);
                return Optional.of(character);
            }
        } catch (Exception e) {
            log.error("查询角色失败: name={}", name, e);
        }

        return Optional.empty();
    }

    @Override
    public List<GameCharacter> findAll(int offset, int limit) {
        try {
            String sql = """
                SELECT TOP (?) * FROM players
                WHERE deletion_state = 0
                ORDER BY char_id
                OFFSET ? ROWS
                """;
            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql, limit, offset);

            return results.stream()
                    .map(GameCharacter::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("查询角色列表失败", e);
            return List.of();
        }
    }

    @Override
    public List<GameCharacter> findByCondition(Map<String, Object> conditions, int offset, int limit) {
        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM players WHERE deletion_state = 0");
            List<Object> params = new ArrayList<>();

            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                sql.append(" AND ").append(entry.getKey()).append(" = ?");
                params.add(entry.getValue());
            }

            sql.append(" ORDER BY char_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            params.add(offset);
            params.add(limit);

            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql.toString(), params.toArray());

            return results.stream()
                    .map(GameCharacter::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("条件查询角色失败", e);
            return List.of();
        }
    }

    @Override
    public List<GameCharacter> search(String keyword, int limit) {
        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_SearchCharacters",
                    Map.of("keyword", "%" + keyword + "%", "limit", limit)
            );

            return results.stream()
                    .map(GameCharacter::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("搜索角色失败: keyword={}", keyword, e);
            return List.of();
        }
    }

    @Override
    public long count() {
        try {
            return connection.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM players WHERE deletion_state = 0",
                    Long.class
            );
        } catch (Exception e) {
            log.error("统计角色数量失败", e);
            return 0;
        }
    }

    @Override
    public long countByCondition(Map<String, Object> conditions) {
        try {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM players WHERE deletion_state = 0");
            List<Object> params = new ArrayList<>();

            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                sql.append(" AND ").append(entry.getKey()).append(" = ?");
                params.add(entry.getValue());
            }

            return connection.getJdbcTemplate().queryForObject(sql.toString(), Long.class, params.toArray());
        } catch (Exception e) {
            log.error("条件统计角色失败", e);
            return 0;
        }
    }

    @Override
    public boolean exists(Integer id) {
        return findById(id).isPresent();
    }

    @Override
    public GameCharacter save(GameCharacter entity) {
        // For game data, we typically don't create characters via admin
        // This would call appropriate stored procedure
        throw new UnsupportedOperationException("角色创建应通过游戏客户端进行");
    }

    @Override
    public List<GameCharacter> saveAll(List<GameCharacter> entities) {
        throw new UnsupportedOperationException("批量角色创建不支持");
    }

    @Override
    public boolean update(Integer id, Map<String, Object> fields) {
        try {
            // Build update based on fields
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();

                String procName = switch (field) {
                    case "name" -> "aion_ChangeCharName";
                    case "race" -> "aion_ChangeCharRace";
                    case "level" -> "aion_SetCharLevel";
                    case "kinah" -> "aion_SetKinah";
                    default -> null;
                };

                if (procName != null) {
                    connection.executeProcedure(procName, Map.of("char_id", id, field, value));

                    auditLog.success(OperationType.CHARACTER, "更新字段",
                            "角色:" + id, field + "=" + value);
                }
            }

            // Invalidate cache
            cache.remove(CACHE_PREFIX + id);

            return true;
        } catch (Exception e) {
            log.error("更新角色失败: id={}, fields={}", id, fields, e);
            auditLog.failure(OperationType.CHARACTER, "更新角色", "ID:" + id, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(Integer id) {
        try {
            connection.executeProcedure("aion_DeleteChar", Map.of("char_id", id));
            cache.remove(CACHE_PREFIX + id);

            auditLog.success(OperationType.CHARACTER, "删除角色", "ID:" + id, null);
            return true;
        } catch (Exception e) {
            log.error("删除角色失败: id={}", id, e);
            auditLog.failure(OperationType.CHARACTER, "删除角色", "ID:" + id, e.getMessage());
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
    public List<GameCharacter> findByIds(List<Integer> ids) {
        return ids.stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    @Override
    public int batchUpdate(Map<String, Object> fields, Map<String, Object> conditions) {
        List<GameCharacter> characters = findByCondition(conditions, 0, 10000);
        int updated = 0;

        for (GameCharacter c : characters) {
            if (update(c.charId(), fields)) {
                updated++;
            }
        }

        auditLog.success(OperationType.CHARACTER, "批量更新",
                "条件:" + conditions, "更新 " + updated + " 条");
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
     * 查询账号下所有角色
     */
    public List<GameCharacter> findByAccountId(int accountId) {
        return findByCondition(Map.of("account_id", accountId), 0, 100);
    }

    /**
     * 查询公会成员
     */
    public List<GameCharacter> findByGuildId(int guildId) {
        return findByCondition(Map.of("guild_id", guildId), 0, 1000);
    }

    /**
     * 查询在线角色
     */
    public List<GameCharacter> findOnline() {
        return findByCondition(Map.of("online", 1), 0, 10000);
    }

    /**
     * 获取在线角色数量
     */
    public long countOnline() {
        return countByCondition(Map.of("online", 1));
    }

    /**
     * 清除角色缓存
     */
    public void evictCache(int charId) {
        cache.remove(CACHE_PREFIX + charId);
    }

    /**
     * 清除所有角色缓存
     */
    public void evictAllCache() {
        cache.removeByPrefix(CACHE_PREFIX);
    }
}
