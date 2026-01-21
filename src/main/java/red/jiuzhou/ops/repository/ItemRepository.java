package red.jiuzhou.ops.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.core.AuditLog;
import red.jiuzhou.ops.core.AuditLog.OperationType;
import red.jiuzhou.ops.core.CacheManager;
import red.jiuzhou.ops.db.SqlServerConnection;
import red.jiuzhou.ops.model.GameItem;

import java.util.*;

/**
 * 物品数据仓储实现
 *
 * 提供物品数据的 CRUD 操作，集成：
 * - 缓存层
 * - 审计日志
 * - 存储过程调用
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class ItemRepository implements GameRepository<GameItem, Long> {

    private static final Logger log = LoggerFactory.getLogger(ItemRepository.class);
    private static final String CACHE_PREFIX = "item:";

    private final SqlServerConnection connection;
    private final CacheManager cache;
    private final AuditLog auditLog;

    public ItemRepository(SqlServerConnection connection) {
        this.connection = connection;
        this.cache = CacheManager.getInstance();
        this.auditLog = AuditLog.getInstance();
    }

    @Override
    public Optional<GameItem> findById(Long uniqueId) {
        // Check cache first
        String cacheKey = CACHE_PREFIX + uniqueId;
        GameItem cached = cache.get(cacheKey, GameItem.class);
        if (cached != null) {
            log.debug("缓存命中: {}", cacheKey);
            return Optional.of(cached);
        }

        try {
            List<Map<String, Object>> results = connection.callProcedure(
                    "aion_GetItem",
                    Map.of("item_unique_id", uniqueId)
            );

            if (!results.isEmpty()) {
                GameItem item = GameItem.fromMap(results.get(0));
                cache.put(cacheKey, item, 120); // 2 minutes TTL (items change often)
                return Optional.of(item);
            }
        } catch (Exception e) {
            log.error("查询物品失败: uniqueId={}", uniqueId, e);
            auditLog.failure(OperationType.ITEM, "查询物品", "唯一ID:" + uniqueId, e.getMessage());
        }

        return Optional.empty();
    }

    @Override
    public Optional<GameItem> findByName(String name) {
        // Items don't have unique names, so this searches by item template name
        try {
            String sql = """
                SELECT TOP 1 * FROM inventory
                WHERE item_name LIKE ?
                ORDER BY item_unique_id DESC
                """;
            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql, "%" + name + "%");

            if (!results.isEmpty()) {
                return Optional.of(GameItem.fromMap(results.get(0)));
            }
        } catch (Exception e) {
            log.error("按名称查询物品失败: name={}", name, e);
        }

        return Optional.empty();
    }

    @Override
    public List<GameItem> findAll(int offset, int limit) {
        try {
            String sql = """
                SELECT TOP (?) * FROM inventory
                ORDER BY item_unique_id DESC
                OFFSET ? ROWS
                """;
            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql, limit, offset);

            return results.stream()
                    .map(GameItem::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("查询物品列表失败", e);
            return List.of();
        }
    }

    @Override
    public List<GameItem> findByCondition(Map<String, Object> conditions, int offset, int limit) {
        try {
            StringBuilder sql = new StringBuilder("SELECT * FROM inventory WHERE 1=1");
            List<Object> params = new ArrayList<>();

            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                sql.append(" AND ").append(entry.getKey()).append(" = ?");
                params.add(entry.getValue());
            }

            sql.append(" ORDER BY item_unique_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            params.add(offset);
            params.add(limit);

            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql.toString(), params.toArray());

            return results.stream()
                    .map(GameItem::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("条件查询物品失败", e);
            return List.of();
        }
    }

    @Override
    public List<GameItem> search(String keyword, int limit) {
        try {
            String sql = """
                SELECT TOP (?) * FROM inventory
                WHERE item_name LIKE ?
                ORDER BY item_unique_id DESC
                """;
            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql, limit, "%" + keyword + "%");

            return results.stream()
                    .map(GameItem::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("搜索物品失败: keyword={}", keyword, e);
            return List.of();
        }
    }

    @Override
    public long count() {
        try {
            return connection.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM inventory",
                    Long.class
            );
        } catch (Exception e) {
            log.error("统计物品数量失败", e);
            return 0;
        }
    }

    @Override
    public long countByCondition(Map<String, Object> conditions) {
        try {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM inventory WHERE 1=1");
            List<Object> params = new ArrayList<>();

            for (Map.Entry<String, Object> entry : conditions.entrySet()) {
                sql.append(" AND ").append(entry.getKey()).append(" = ?");
                params.add(entry.getValue());
            }

            return connection.getJdbcTemplate().queryForObject(sql.toString(), Long.class, params.toArray());
        } catch (Exception e) {
            log.error("条件统计物品失败", e);
            return 0;
        }
    }

    @Override
    public boolean exists(Long id) {
        return findById(id).isPresent();
    }

    @Override
    public GameItem save(GameItem entity) {
        throw new UnsupportedOperationException("物品创建应通过发送物品接口进行");
    }

    @Override
    public List<GameItem> saveAll(List<GameItem> entities) {
        throw new UnsupportedOperationException("批量物品创建不支持");
    }

    @Override
    public boolean update(Long id, Map<String, Object> fields) {
        try {
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();

                String procName = switch (field) {
                    case "item_count" -> "aion_SetItemCount";
                    case "enchant_level" -> "aion_SetItemEnchant";
                    case "item_owner" -> "aion_TransferItem";
                    default -> null;
                };

                if (procName != null) {
                    connection.executeProcedure(procName, Map.of("item_unique_id", id, field, value));
                    auditLog.success(OperationType.ITEM, "更新字段",
                            "物品:" + id, field + "=" + value);
                }
            }

            // Invalidate cache
            cache.remove(CACHE_PREFIX + id);
            return true;
        } catch (Exception e) {
            log.error("更新物品失败: id={}, fields={}", id, fields, e);
            auditLog.failure(OperationType.ITEM, "更新物品", "ID:" + id, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(Long id) {
        try {
            connection.executeProcedure("aion_DeleteItem", Map.of("item_unique_id", id));
            cache.remove(CACHE_PREFIX + id);
            auditLog.warning(OperationType.ITEM, "删除物品", "唯一ID:" + id, null);
            return true;
        } catch (Exception e) {
            log.error("删除物品失败: id={}", id, e);
            auditLog.failure(OperationType.ITEM, "删除物品", "ID:" + id, e.getMessage());
            return false;
        }
    }

    @Override
    public int deleteAll(List<Long> ids) {
        int deleted = 0;
        for (Long id : ids) {
            if (delete(id)) {
                deleted++;
            }
        }
        return deleted;
    }

    @Override
    public List<GameItem> findByIds(List<Long> ids) {
        return ids.stream()
                .map(this::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    @Override
    public int batchUpdate(Map<String, Object> fields, Map<String, Object> conditions) {
        List<GameItem> items = findByCondition(conditions, 0, 10000);
        int updated = 0;

        for (GameItem item : items) {
            if (update(item.itemUniqueId(), fields)) {
                updated++;
            }
        }

        auditLog.success(OperationType.ITEM, "批量更新",
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
     * 根据所有者查询物品
     */
    public List<GameItem> findByOwner(int ownerId, Integer storageType) {
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("item_owner", ownerId);
        if (storageType != null) {
            conditions.put("storage_type", storageType);
        }
        return findByCondition(conditions, 0, 1000);
    }

    /**
     * 根据所有者名称查询物品
     */
    public List<GameItem> findByOwnerName(String ownerName, Integer storageType) {
        try {
            StringBuilder sql = new StringBuilder("""
                SELECT i.* FROM inventory i
                JOIN players p ON i.item_owner = p.char_id
                WHERE p.name = ?
                """);
            List<Object> params = new ArrayList<>();
            params.add(ownerName);

            if (storageType != null) {
                sql.append(" AND i.storage_type = ?");
                params.add(storageType);
            }

            sql.append(" ORDER BY i.item_unique_id DESC");

            List<Map<String, Object>> results = connection.getJdbcTemplate()
                    .queryForList(sql.toString(), params.toArray());

            return results.stream()
                    .map(GameItem::fromMap)
                    .toList();
        } catch (Exception e) {
            log.error("按所有者名称查询物品失败: ownerName={}", ownerName, e);
            return List.of();
        }
    }

    /**
     * 根据物品模板ID查询
     */
    public List<GameItem> findByItemId(int itemId) {
        return findByCondition(Map.of("item_id", itemId), 0, 500);
    }

    /**
     * 发送物品给玩家
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
            auditLog.failure(OperationType.ITEM, "发送物品", "角色:" + charId, e.getMessage());
            throw new RuntimeException("发送物品失败: " + e.getMessage(), e);
        }
    }

    /**
     * 转移物品
     */
    public void transferItem(long itemUniqueId, int targetCharId) {
        try {
            connection.executeProcedure("aion_TransferItem",
                    Map.of("item_unique_id", itemUniqueId, "target_char_id", targetCharId));

            cache.remove(CACHE_PREFIX + itemUniqueId);
            auditLog.success(OperationType.ITEM, "转移物品",
                    "唯一ID:" + itemUniqueId, "目标:" + targetCharId);
        } catch (Exception e) {
            log.error("转移物品失败: itemUniqueId={}, targetCharId={}", itemUniqueId, targetCharId, e);
            throw new RuntimeException("转移物品失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取物品统计
     */
    public Map<String, Long> getItemStats() {
        Map<String, Long> stats = new HashMap<>();
        try {
            stats.put("total", count());
            stats.put("bound", countByCondition(Map.of("is_soul_bound", 1)));
            stats.put("enchanted", connection.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM inventory WHERE enchant_level > 0", Long.class));
            stats.put("expired", connection.getJdbcTemplate().queryForObject(
                    "SELECT COUNT(*) FROM inventory WHERE expire_time IS NOT NULL AND expire_time < GETDATE()", Long.class));
        } catch (Exception e) {
            log.error("获取物品统计失败", e);
        }
        return stats;
    }

    /**
     * 获取物品排行
     */
    public List<Map<String, Object>> getItemRanking(int limit) {
        try {
            String sql = """
                SELECT item_id, item_name as name, SUM(item_count) as total,
                       COUNT(DISTINCT item_owner) as owners
                FROM inventory
                GROUP BY item_id, item_name
                ORDER BY total DESC
                OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;
            return connection.getJdbcTemplate().queryForList(sql, limit);
        } catch (Exception e) {
            log.error("获取物品排行失败", e);
            return List.of();
        }
    }

    /**
     * 清除物品缓存
     */
    public void evictCache(long itemUniqueId) {
        cache.remove(CACHE_PREFIX + itemUniqueId);
    }

    /**
     * 清除所有物品缓存
     */
    public void evictAllCache() {
        cache.removeByPrefix(CACHE_PREFIX);
    }
}
