package red.jiuzhou.ui.components;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.DatabaseUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 文件状态缓存服务
 *
 * 提供异步加载、批量预加载和缓存管理功能
 * 用于在菜单树节点上显示文件状态徽章
 *
 * @author Claude
 * @version 1.0
 */
public class FileStatusCache {

    private static final Logger log = LoggerFactory.getLogger(FileStatusCache.class);

    // 单例实例
    private static volatile FileStatusCache instance;

    // 缓存存储
    private final ConcurrentHashMap<String, FileStatusInfo> cache = new ConcurrentHashMap<>();

    // 异步执行器
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "FileStatusCache-Worker");
        t.setDaemon(true);
        return t;
    });

    // 批量加载队列
    private final Set<String> pendingLoads = ConcurrentHashMap.newKeySet();

    // 状态更新监听器
    private final List<Consumer<String>> updateListeners = new CopyOnWriteArrayList<>();

    // 缓存过期时间（毫秒）
    private static final long CACHE_TTL = 5 * 60 * 1000L; // 5分钟

    // 批量加载延迟（毫秒）
    private static final long BATCH_DELAY = 100L;

    // 批量加载调度器
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> batchLoadTask;

    // ==================== 单例访问 ====================

    public static FileStatusCache getInstance() {
        if (instance == null) {
            synchronized (FileStatusCache.class) {
                if (instance == null) {
                    instance = new FileStatusCache();
                }
            }
        }
        return instance;
    }

    /**
     * 快捷方法：获取文件状态
     */
    public static FileStatusInfo get(String tableName) {
        return getInstance().getStatus(tableName);
    }

    /**
     * 快捷方法：异步获取文件状态
     */
    public static void getAsync(String tableName, Consumer<FileStatusInfo> callback) {
        getInstance().getStatusAsync(tableName, callback);
    }

    // ==================== 构造函数 ====================

    private FileStatusCache() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FileStatusCache-Scheduler");
            t.setDaemon(true);
            return t;
        });
        log.info("FileStatusCache 初始化完成");
    }

    // ==================== 核心方法 ====================

    /**
     * 获取文件状态（同步，可能返回缓存或占位符）
     */
    public FileStatusInfo getStatus(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return createPlaceholder(tableName, null);
        }

        String key = normalizeKey(tableName);
        FileStatusInfo cached = cache.get(key);

        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        // 缓存不存在或已过期，返回占位符并触发异步加载
        FileStatusInfo placeholder = createPlaceholder(tableName, null);
        placeholder.setImportStatus(FileStatusInfo.ImportStatus.CHECKING);

        // 加入待加载队列
        scheduleBatchLoad(key);

        return cached != null ? cached : placeholder;
    }

    /**
     * 获取文件状态（异步，加载完成后回调）
     */
    public void getStatusAsync(String tableName, Consumer<FileStatusInfo> callback) {
        if (tableName == null || tableName.isEmpty()) {
            callback.accept(createPlaceholder(tableName, null));
            return;
        }

        String key = normalizeKey(tableName);
        FileStatusInfo cached = cache.get(key);

        if (cached != null && !cached.isExpired()) {
            callback.accept(cached);
            return;
        }

        // 异步加载
        executor.submit(() -> {
            try {
                FileStatusInfo status = loadStatus(key, null);
                cache.put(key, status);
                Platform.runLater(() -> callback.accept(status));
            } catch (Exception e) {
                log.error("异步加载文件状态失败: {}", key, e);
                Platform.runLater(() -> callback.accept(createPlaceholder(tableName, null)));
            }
        });
    }

    /**
     * 批量预加载文件状态
     */
    public void preloadBatch(Collection<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) return;

        executor.submit(() -> {
            log.debug("批量预加载 {} 个文件状态", tableNames.size());
            long startTime = System.currentTimeMillis();

            for (String tableName : tableNames) {
                String key = normalizeKey(tableName);
                if (!cache.containsKey(key) || cache.get(key).isExpired()) {
                    try {
                        FileStatusInfo status = loadStatus(key, null);
                        cache.put(key, status);
                    } catch (Exception e) {
                        log.debug("预加载失败: {}", key);
                    }
                }
            }

            log.debug("批量预加载完成，耗时 {} ms", System.currentTimeMillis() - startTime);
        });
    }

    /**
     * 刷新指定文件的状态
     */
    public void refresh(String tableName) {
        if (tableName == null) return;

        String key = normalizeKey(tableName);
        cache.remove(key);
        scheduleBatchLoad(key);
    }

    /**
     * 刷新所有缓存
     */
    public void refreshAll() {
        cache.clear();
        notifyListeners(null); // null 表示全部刷新
    }

    /**
     * 使指定表名的缓存失效
     */
    public void invalidate(String tableName) {
        if (tableName != null) {
            cache.remove(normalizeKey(tableName));
        }
    }

    // ==================== 状态加载逻辑 ====================

    /**
     * 从数据库加载文件状态
     */
    private FileStatusInfo loadStatus(String tableName, String filePath) {
        FileStatusInfo status = new FileStatusInfo(tableName, filePath);

        try {
            var jdbc = DatabaseUtil.getJdbcTemplate();

            // 1. 检查表是否存在
            boolean tableExists = checkTableExists(jdbc, tableName);

            if (tableExists) {
                // 2. 获取记录数
                long recordCount = getRecordCount(jdbc, tableName);
                status.setRecordCount(recordCount);

                if (recordCount > 0) {
                    status.setImportStatus(FileStatusInfo.ImportStatus.IMPORTED);
                } else {
                    status.setImportStatus(FileStatusInfo.ImportStatus.MODIFIED);
                }

                // DDL已生成
                status.setDdlStatus(FileStatusInfo.DdlStatus.GENERATED);
            } else {
                status.setImportStatus(FileStatusInfo.ImportStatus.NOT_IMPORTED);
                status.setDdlStatus(FileStatusInfo.DdlStatus.PENDING);
            }

            // 3. 获取编码元数据
            loadEncodingMetadata(jdbc, tableName, status);

            // 4. 获取服务器配置信息
            loadServerConfigInfo(jdbc, tableName, status);

        } catch (Exception e) {
            log.debug("加载文件状态异常: {} - {}", tableName, e.getMessage());
            status.setImportStatus(FileStatusInfo.ImportStatus.UNKNOWN);
        }

        return status;
    }

    /**
     * 检查表是否存在
     */
    private boolean checkTableExists(org.springframework.jdbc.core.JdbcTemplate jdbc, String tableName) {
        try {
            String sql = "SELECT COUNT(*) FROM information_schema.tables " +
                         "WHERE table_schema = DATABASE() AND table_name = ?";
            Integer count = jdbc.queryForObject(sql, Integer.class, tableName);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取记录数
     */
    private long getRecordCount(org.springframework.jdbc.core.JdbcTemplate jdbc, String tableName) {
        try {
            String sql = "SELECT COUNT(*) FROM `" + tableName + "`";
            Long count = jdbc.queryForObject(sql, Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 加载编码元数据
     */
    private void loadEncodingMetadata(org.springframework.jdbc.core.JdbcTemplate jdbc,
                                       String tableName, FileStatusInfo status) {
        try {
            String sql = "SELECT original_encoding, has_bom, last_validation_result, " +
                         "last_import_time FROM file_encoding_metadata WHERE table_name = ?";
            List<Map<String, Object>> results = jdbc.queryForList(sql, tableName);

            if (!results.isEmpty()) {
                Map<String, Object> meta = results.get(0);
                status.setEncoding((String) meta.get("original_encoding"));
                status.setHasBom(Boolean.TRUE.equals(meta.get("has_bom")));

                Boolean validated = (Boolean) meta.get("last_validation_result");
                status.setRoundTripValidated(Boolean.TRUE.equals(validated));

                // 检查文件修改时间
                Object lastImport = meta.get("last_import_time");
                if (lastImport != null && status.getFilePath() != null) {
                    checkFileModification(status, lastImport);
                }
            }
        } catch (Exception e) {
            log.debug("加载编码元数据失败: {}", tableName);
        }
    }

    /**
     * 检查文件是否在导入后被修改
     */
    private void checkFileModification(FileStatusInfo status, Object lastImport) {
        try {
            File file = new File(status.getFilePath());
            if (file.exists()) {
                long fileModTime = file.lastModified();
                long importTime = 0;
                if (lastImport instanceof java.sql.Timestamp) {
                    importTime = ((java.sql.Timestamp) lastImport).getTime();
                } else if (lastImport instanceof java.util.Date) {
                    importTime = ((java.util.Date) lastImport).getTime();
                }

                // 文件修改时间比导入时间晚超过1秒
                if (fileModTime - importTime > 1000) {
                    status.setImportStatus(FileStatusInfo.ImportStatus.MODIFIED);
                }
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    /**
     * 加载服务器配置信息
     */
    private void loadServerConfigInfo(org.springframework.jdbc.core.JdbcTemplate jdbc,
                                       String tableName, FileStatusInfo status) {
        try {
            String sql = "SELECT load_priority, server_module, depends_on, referenced_by " +
                         "FROM server_config_files WHERE table_name = ? AND is_server_loaded = TRUE";
            List<Map<String, Object>> results = jdbc.queryForList(sql, tableName);

            if (!results.isEmpty()) {
                Map<String, Object> config = results.get(0);

                Integer priority = (Integer) config.get("load_priority");
                if (priority != null) {
                    status.setServerPriority(FileStatusInfo.ServerPriority.fromLevel(priority));
                }

                status.setServerModule((String) config.get("server_module"));

                // 解析依赖关系（JSON格式）
                String dependsOn = (String) config.get("depends_on");
                String referencedBy = (String) config.get("referenced_by");

                if (dependsOn != null && !dependsOn.isEmpty() && !dependsOn.equals("[]")) {
                    status.setDependsOnCount(countJsonArray(dependsOn));
                }
                if (referencedBy != null && !referencedBy.isEmpty() && !referencedBy.equals("[]")) {
                    status.setReferencedByCount(countJsonArray(referencedBy));
                }
            }
        } catch (Exception e) {
            log.debug("加载服务器配置信息失败: {}", tableName);
        }
    }

    /**
     * 简单计算JSON数组元素数量
     */
    private int countJsonArray(String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) return 0;
        // 简单统计逗号数量+1
        return (int) json.chars().filter(c -> c == ',').count() + 1;
    }

    // ==================== 批量加载调度 ====================

    /**
     * 调度批量加载
     */
    private void scheduleBatchLoad(String key) {
        pendingLoads.add(key);

        // 取消之前的调度
        if (batchLoadTask != null) {
            batchLoadTask.cancel(false);
        }

        // 延迟执行批量加载
        batchLoadTask = scheduler.schedule(this::executeBatchLoad, BATCH_DELAY, TimeUnit.MILLISECONDS);
    }

    /**
     * 执行批量加载
     */
    private void executeBatchLoad() {
        if (pendingLoads.isEmpty()) return;

        Set<String> toLoad = new HashSet<>(pendingLoads);
        pendingLoads.clear();

        executor.submit(() -> {
            log.debug("执行批量加载: {} 个文件", toLoad.size());

            for (String key : toLoad) {
                try {
                    FileStatusInfo status = loadStatus(key, null);
                    cache.put(key, status);
                    notifyListeners(key);
                } catch (Exception e) {
                    log.debug("批量加载失败: {}", key);
                }
            }
        });
    }

    // ==================== 监听器 ====================

    /**
     * 添加状态更新监听器
     */
    public void addUpdateListener(Consumer<String> listener) {
        updateListeners.add(listener);
    }

    /**
     * 移除状态更新监听器
     */
    public void removeUpdateListener(Consumer<String> listener) {
        updateListeners.remove(listener);
    }

    /**
     * 通知监听器
     */
    private void notifyListeners(String tableName) {
        Platform.runLater(() -> {
            for (Consumer<String> listener : updateListeners) {
                try {
                    listener.accept(tableName);
                } catch (Exception e) {
                    log.debug("通知监听器失败", e);
                }
            }
        });
    }

    // ==================== 工具方法 ====================

    /**
     * 创建占位符状态
     */
    private FileStatusInfo createPlaceholder(String tableName, String filePath) {
        return new FileStatusInfo(tableName != null ? tableName : "", filePath);
    }

    /**
     * 标准化缓存键
     */
    private String normalizeKey(String tableName) {
        if (tableName == null) return "";
        return tableName.toLowerCase().trim();
    }

    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * 关闭缓存服务
     */
    public void shutdown() {
        log.info("关闭 FileStatusCache");
        executor.shutdownNow();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        cache.clear();
    }
}
