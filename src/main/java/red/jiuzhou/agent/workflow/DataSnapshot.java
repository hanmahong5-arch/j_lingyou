package red.jiuzhou.agent.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据快照管理器
 *
 * <p>在执行修改操作前保存数据快照，支持：
 * <ul>
 *   <li>修改前数据完整备份</li>
 *   <li>按快照ID恢复数据</li>
 *   <li>快照过期自动清理</li>
 *   <li>数据库持久化（可选）</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class DataSnapshot {

    private static final Logger log = LoggerFactory.getLogger(DataSnapshot.class);

    // 单例
    private static DataSnapshot instance;

    // 内存快照存储
    private final Map<String, SnapshotEntry> snapshots = new ConcurrentHashMap<>();

    // 快照过期时间（毫秒）- 默认1小时
    private long snapshotTtlMs = 60 * 60 * 1000;

    // 最大快照数量
    private int maxSnapshots = 50;

    // 数据库连接
    private JdbcTemplate jdbcTemplate;

    private DataSnapshot() {
        // 启动清理线程
        startCleanupThread();
    }

    public static synchronized DataSnapshot getInstance() {
        if (instance == null) {
            instance = new DataSnapshot();
        }
        return instance;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initDatabaseTable();
    }

    /**
     * 初始化数据库表
     */
    private void initDatabaseTable() {
        if (jdbcTemplate == null) return;

        try {
            // PostgreSQL: 使用 TEXT 替代 LONGTEXT
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS data_snapshots (
                    snapshot_id VARCHAR(50) PRIMARY KEY,
                    workflow_id VARCHAR(50) NOT NULL,
                    step_id VARCHAR(50),
                    table_name VARCHAR(100) NOT NULL,
                    primary_key_column VARCHAR(100) NOT NULL,
                    row_count INT DEFAULT 0,
                    snapshot_data TEXT,
                    sql_executed TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP,
                    restored_at TIMESTAMP NULL
                )
            """);

            // PostgreSQL: 单独创建索引
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_snapshot_workflow_id ON data_snapshots (workflow_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_expires_at ON data_snapshots (expires_at)");

            log.info("数据快照表初始化完成");
        } catch (Exception e) {
            log.warn("创建快照表失败（可能已存在）: {}", e.getMessage());
        }
    }

    // ==================== 快照创建 ====================

    /**
     * 在执行修改前创建数据快照
     *
     * @param workflowId 工作流ID
     * @param stepId 步骤ID
     * @param tableName 表名
     * @param whereClause WHERE条件（不含WHERE关键字）
     * @param sqlToExecute 将要执行的SQL
     * @return 快照ID
     */
    public String createSnapshot(String workflowId, String stepId, String tableName,
                                  String whereClause, String sqlToExecute) {
        if (jdbcTemplate == null) {
            log.warn("未配置数据库连接，无法创建快照");
            return null;
        }

        String snapshotId = generateSnapshotId();

        try {
            // 获取主键列名
            String primaryKeyColumn = detectPrimaryKey(tableName);
            if (primaryKeyColumn == null) {
                primaryKeyColumn = "id"; // 默认使用id
            }

            // 查询将被修改的数据
            String selectSql = String.format("SELECT * FROM `%s`", tableName);
            if (whereClause != null && !whereClause.trim().isEmpty()) {
                selectSql += " WHERE " + whereClause;
            }

            List<Map<String, Object>> data = jdbcTemplate.queryForList(selectSql);

            if (data.isEmpty()) {
                log.info("没有数据需要快照: {}", selectSql);
                return null;
            }

            // 创建快照条目
            SnapshotEntry entry = new SnapshotEntry();
            entry.snapshotId = snapshotId;
            entry.workflowId = workflowId;
            entry.stepId = stepId;
            entry.tableName = tableName;
            entry.primaryKeyColumn = primaryKeyColumn;
            entry.whereClause = whereClause;
            entry.sqlExecuted = sqlToExecute;
            entry.data = data;
            entry.createdAt = Instant.now();
            entry.expiresAt = Instant.now().plusMillis(snapshotTtlMs);

            // 存储到内存
            snapshots.put(snapshotId, entry);

            // 持久化到数据库
            persistSnapshot(entry);

            // 检查并清理超出限制的快照
            cleanupExcessSnapshots();

            log.info("创建数据快照: id={}, 表={}, 行数={}", snapshotId, tableName, data.size());

            return snapshotId;

        } catch (Exception e) {
            log.error("创建快照失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 为UPDATE语句创建快照
     */
    public String createSnapshotForUpdate(String workflowId, String stepId, String updateSql) {
        // 解析UPDATE语句
        UpdateInfo info = parseUpdateSql(updateSql);
        if (info == null) {
            log.warn("无法解析UPDATE语句: {}", updateSql);
            return null;
        }

        return createSnapshot(workflowId, stepId, info.tableName, info.whereClause, updateSql);
    }

    /**
     * 为DELETE语句创建快照
     */
    public String createSnapshotForDelete(String workflowId, String stepId, String deleteSql) {
        // 解析DELETE语句
        DeleteInfo info = parseDeleteSql(deleteSql);
        if (info == null) {
            log.warn("无法解析DELETE语句: {}", deleteSql);
            return null;
        }

        return createSnapshot(workflowId, stepId, info.tableName, info.whereClause, deleteSql);
    }

    // ==================== 快照恢复 ====================

    /**
     * 恢复快照数据
     *
     * @param snapshotId 快照ID
     * @return 恢复的行数，-1表示失败
     */
    public int restoreSnapshot(String snapshotId) {
        if (jdbcTemplate == null) {
            log.error("未配置数据库连接，无法恢复快照");
            return -1;
        }

        SnapshotEntry entry = snapshots.get(snapshotId);
        if (entry == null) {
            // 尝试从数据库加载
            entry = loadSnapshotFromDb(snapshotId);
            if (entry == null) {
                log.error("快照不存在: {}", snapshotId);
                return -1;
            }
        }

        try {
            int restoredCount = 0;

            for (Map<String, Object> row : entry.data) {
                // 获取主键值
                Object pkValue = row.get(entry.primaryKeyColumn);
                if (pkValue == null) {
                    log.warn("行数据缺少主键: {}", row);
                    continue;
                }

                // 检查记录是否存在
                String checkSql = String.format("SELECT COUNT(*) FROM `%s` WHERE `%s` = ?",
                        entry.tableName, entry.primaryKeyColumn);
                Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, pkValue);

                if (count != null && count > 0) {
                    // 记录存在，执行UPDATE
                    restoredCount += restoreRowByUpdate(entry.tableName, entry.primaryKeyColumn, pkValue, row);
                } else {
                    // 记录不存在（可能被删除了），执行INSERT
                    restoredCount += restoreRowByInsert(entry.tableName, row);
                }
            }

            // 标记快照已恢复
            entry.restoredAt = Instant.now();

            // 记录到审计日志
            WorkflowAuditLog.getInstance().logDataRolledBack(
                    entry.workflowId, entry.stepId, snapshotId, restoredCount);

            log.info("快照恢复完成: id={}, 恢复行数={}", snapshotId, restoredCount);

            return restoredCount;

        } catch (Exception e) {
            log.error("恢复快照失败: {}", e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 通过UPDATE恢复行数据
     */
    private int restoreRowByUpdate(String tableName, String pkColumn, Object pkValue, Map<String, Object> row) {
        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE `").append(tableName).append("` SET ");

        List<Object> params = new ArrayList<>();
        boolean first = true;

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String column = entry.getKey();
            if (column.equals(pkColumn)) continue; // 跳过主键

            if (!first) sql.append(", ");
            sql.append("`").append(column).append("` = ?");
            params.add(entry.getValue());
            first = false;
        }

        sql.append(" WHERE `").append(pkColumn).append("` = ?");
        params.add(pkValue);

        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    /**
     * 通过INSERT恢复行数据
     */
    private int restoreRowByInsert(String tableName, Map<String, Object> row) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO `").append(tableName).append("` (");

        StringBuilder values = new StringBuilder();
        List<Object> params = new ArrayList<>();
        boolean first = true;

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (!first) {
                sql.append(", ");
                values.append(", ");
            }
            sql.append("`").append(entry.getKey()).append("`");
            values.append("?");
            params.add(entry.getValue());
            first = false;
        }

        sql.append(") VALUES (").append(values).append(")");

        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    // ==================== 快照查询 ====================

    /**
     * 获取快照信息
     */
    public SnapshotEntry getSnapshot(String snapshotId) {
        SnapshotEntry entry = snapshots.get(snapshotId);
        if (entry == null) {
            entry = loadSnapshotFromDb(snapshotId);
        }
        return entry;
    }

    /**
     * 获取工作流的所有快照
     */
    public List<SnapshotEntry> getWorkflowSnapshots(String workflowId) {
        List<SnapshotEntry> result = new ArrayList<>();
        for (SnapshotEntry entry : snapshots.values()) {
            if (workflowId.equals(entry.workflowId)) {
                result.add(entry);
            }
        }
        // 按时间排序
        result.sort(Comparator.comparing(e -> e.createdAt));
        return result;
    }

    /**
     * 检查快照是否可恢复
     */
    public boolean canRestore(String snapshotId) {
        SnapshotEntry entry = getSnapshot(snapshotId);
        if (entry == null) return false;
        if (entry.restoredAt != null) return false; // 已恢复过
        if (entry.expiresAt != null && Instant.now().isAfter(entry.expiresAt)) return false; // 已过期
        return true;
    }

    // ==================== 辅助方法 ====================

    private String generateSnapshotId() {
        return "SNAP-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }

    private String detectPrimaryKey(String tableName) {
        if (jdbcTemplate == null) return null;

        try {
            // PostgreSQL: 通过 information_schema 查询主键
            String sql = """
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                    ON tc.constraint_name = kcu.constraint_name
                    AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = current_schema()
                    AND tc.table_name = ?
                    AND tc.constraint_type = 'PRIMARY KEY'
                """;
            List<Map<String, Object>> keys = jdbcTemplate.queryForList(sql, tableName);
            if (!keys.isEmpty()) {
                return (String) keys.get(0).get("column_name");
            }
        } catch (Exception e) {
            log.debug("检测主键失败: {}", e.getMessage());
        }
        return null;
    }

    private UpdateInfo parseUpdateSql(String sql) {
        String upperSql = sql.toUpperCase().trim();
        if (!upperSql.startsWith("UPDATE")) return null;

        try {
            // 提取表名
            int setIdx = upperSql.indexOf(" SET ");
            if (setIdx < 0) return null;

            String tablePart = sql.substring(6, setIdx).trim();
            String tableName = tablePart.replace("`", "").split("\\s+")[0];

            // 提取WHERE条件
            int whereIdx = upperSql.indexOf(" WHERE ");
            String whereClause = whereIdx > 0 ? sql.substring(whereIdx + 7).trim() : null;

            UpdateInfo info = new UpdateInfo();
            info.tableName = tableName;
            info.whereClause = whereClause;
            return info;

        } catch (Exception e) {
            log.debug("解析UPDATE语句失败: {}", e.getMessage());
            return null;
        }
    }

    private DeleteInfo parseDeleteSql(String sql) {
        String upperSql = sql.toUpperCase().trim();
        if (!upperSql.startsWith("DELETE")) return null;

        try {
            // 提取表名
            int fromIdx = upperSql.indexOf(" FROM ");
            if (fromIdx < 0) return null;

            String afterFrom = sql.substring(fromIdx + 6).trim();
            int whereIdx = afterFrom.toUpperCase().indexOf(" WHERE ");

            String tableName;
            String whereClause = null;

            if (whereIdx > 0) {
                tableName = afterFrom.substring(0, whereIdx).trim().replace("`", "");
                whereClause = afterFrom.substring(whereIdx + 7).trim();
            } else {
                tableName = afterFrom.replace("`", "").split("\\s+")[0];
            }

            DeleteInfo info = new DeleteInfo();
            info.tableName = tableName;
            info.whereClause = whereClause;
            return info;

        } catch (Exception e) {
            log.debug("解析DELETE语句失败: {}", e.getMessage());
            return null;
        }
    }

    private void persistSnapshot(SnapshotEntry entry) {
        if (jdbcTemplate == null) return;

        try {
            String dataJson = com.alibaba.fastjson.JSON.toJSONString(entry.data);

            jdbcTemplate.update("""
                INSERT INTO data_snapshots
                (snapshot_id, workflow_id, step_id, table_name, primary_key_column,
                 row_count, snapshot_data, sql_executed, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                    entry.snapshotId,
                    entry.workflowId,
                    entry.stepId,
                    entry.tableName,
                    entry.primaryKeyColumn,
                    entry.data.size(),
                    dataJson,
                    entry.sqlExecuted,
                    java.sql.Timestamp.from(entry.expiresAt)
            );
        } catch (Exception e) {
            log.warn("持久化快照失败: {}", e.getMessage());
        }
    }

    private SnapshotEntry loadSnapshotFromDb(String snapshotId) {
        if (jdbcTemplate == null) return null;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM data_snapshots WHERE snapshot_id = ?", snapshotId);

            if (rows.isEmpty()) return null;

            Map<String, Object> row = rows.get(0);

            SnapshotEntry entry = new SnapshotEntry();
            entry.snapshotId = (String) row.get("snapshot_id");
            entry.workflowId = (String) row.get("workflow_id");
            entry.stepId = (String) row.get("step_id");
            entry.tableName = (String) row.get("table_name");
            entry.primaryKeyColumn = (String) row.get("primary_key_column");
            entry.sqlExecuted = (String) row.get("sql_executed");

            String dataJson = (String) row.get("snapshot_data");
            if (dataJson != null) {
                entry.data = com.alibaba.fastjson.JSON.parseObject(dataJson,
                        new com.alibaba.fastjson.TypeReference<List<Map<String, Object>>>() {});
            }

            java.sql.Timestamp createdAt = (java.sql.Timestamp) row.get("created_at");
            if (createdAt != null) {
                entry.createdAt = createdAt.toInstant();
            }

            java.sql.Timestamp expiresAt = (java.sql.Timestamp) row.get("expires_at");
            if (expiresAt != null) {
                entry.expiresAt = expiresAt.toInstant();
            }

            java.sql.Timestamp restoredAt = (java.sql.Timestamp) row.get("restored_at");
            if (restoredAt != null) {
                entry.restoredAt = restoredAt.toInstant();
            }

            // 缓存到内存
            snapshots.put(snapshotId, entry);

            return entry;

        } catch (Exception e) {
            log.warn("从数据库加载快照失败: {}", e.getMessage());
            return null;
        }
    }

    private void cleanupExcessSnapshots() {
        if (snapshots.size() <= maxSnapshots) return;

        // 按创建时间排序，删除最旧的
        List<SnapshotEntry> sorted = new ArrayList<>(snapshots.values());
        sorted.sort(Comparator.comparing(e -> e.createdAt));

        while (snapshots.size() > maxSnapshots && !sorted.isEmpty()) {
            SnapshotEntry oldest = sorted.remove(0);
            snapshots.remove(oldest.snapshotId);
            log.debug("清理过期快照: {}", oldest.snapshotId);
        }
    }

    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60 * 1000); // 每分钟检查一次

                    Instant now = Instant.now();
                    List<String> expired = new ArrayList<>();

                    for (SnapshotEntry entry : snapshots.values()) {
                        if (entry.expiresAt != null && now.isAfter(entry.expiresAt)) {
                            expired.add(entry.snapshotId);
                        }
                    }

                    for (String id : expired) {
                        snapshots.remove(id);
                        log.debug("清理过期快照: {}", id);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("快照清理线程异常: {}", e.getMessage());
                }
            }
        }, "SnapshotCleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    // ==================== 配置方法 ====================

    public void setSnapshotTtlMs(long ttlMs) {
        this.snapshotTtlMs = ttlMs;
    }

    public void setMaxSnapshots(int max) {
        this.maxSnapshots = max;
    }

    // ==================== 内部类 ====================

    /**
     * 快照条目
     */
    public static class SnapshotEntry {
        public String snapshotId;
        public String workflowId;
        public String stepId;
        public String tableName;
        public String primaryKeyColumn;
        public String whereClause;
        public String sqlExecuted;
        public List<Map<String, Object>> data;
        public Instant createdAt;
        public Instant expiresAt;
        public Instant restoredAt;

        public int getRowCount() {
            return data != null ? data.size() : 0;
        }

        public boolean isRestored() {
            return restoredAt != null;
        }

        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }

    private static class UpdateInfo {
        String tableName;
        String whereClause;
    }

    private static class DeleteInfo {
        String tableName;
        String whereClause;
    }
}
