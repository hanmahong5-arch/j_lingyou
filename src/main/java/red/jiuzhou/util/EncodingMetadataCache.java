package red.jiuzhou.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 编码元数据缓存
 *
 * 功能：
 * - 缓存编码元数据，减少数据库查询
 * - 线程安全的并发访问
 * - 自动过期机制（TTL）
 * - 手动失效控制
 *
 * @author Claude
 * @date 2025-12-29
 */
public class EncodingMetadataCache {

    private static final Logger log = LoggerFactory.getLogger(EncodingMetadataCache.class);

    /**
     * 缓存容器（tableName + mapType -> EncodingInfo）
     */
    private static final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 默认缓存过期时间（毫秒）: 1小时
     */
    private static final long DEFAULT_TTL = 3600 * 1000;

    /**
     * 定时清理任务
     */
    private static final ScheduledExecutorService cleanupExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "EncodingMetadataCache-Cleanup");
                thread.setDaemon(true);
                return thread;
            });

    static {
        // 每10分钟清理一次过期缓存
        cleanupExecutor.scheduleAtFixedRate(
                EncodingMetadataCache::cleanupExpired,
                10, 10, TimeUnit.MINUTES
        );
    }

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        private final FileEncodingDetector.EncodingInfo encoding;
        private final long expireTime;

        public CacheEntry(FileEncodingDetector.EncodingInfo encoding, long ttl) {
            this.encoding = encoding;
            this.expireTime = System.currentTimeMillis() + ttl;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }

        public FileEncodingDetector.EncodingInfo getEncoding() {
            return encoding;
        }
    }

    /**
     * 生成缓存键
     *
     * @param tableName 表名
     * @param mapType   World表的地图类型
     * @return 缓存键
     */
    private static String buildCacheKey(String tableName, String mapType) {
        return tableName + ":" + (mapType == null ? "" : mapType);
    }

    /**
     * 带缓存的获取编码元数据
     *
     * @param tableName 表名
     * @return 编码信息
     */
    public static FileEncodingDetector.EncodingInfo getWithCache(String tableName) {
        return getWithCache(tableName, "");
    }

    /**
     * 带缓存的获取编码元数据（支持 mapType）
     *
     * @param tableName 表名
     * @param mapType   World表的地图类型
     * @return 编码信息
     */
    public static FileEncodingDetector.EncodingInfo getWithCache(String tableName, String mapType) {
        String cacheKey = buildCacheKey(tableName, mapType);

        // 1. 尝试从缓存获取
        CacheEntry entry = cache.get(cacheKey);
        if (entry != null) {
            if (!entry.isExpired()) {
                log.debug("✅ 缓存命中: {}", cacheKey);
                return entry.getEncoding();
            } else {
                // 缓存过期，移除
                cache.remove(cacheKey);
                log.debug("⏰ 缓存过期: {}", cacheKey);
            }
        }

        // 2. 缓存未命中，从数据库查询
        log.debug("❌ 缓存未命中，查询数据库: {}", cacheKey);
        FileEncodingDetector.EncodingInfo encoding =
                EncodingMetadataManager.getMetadata(tableName, mapType);

        // 3. 存入缓存
        cache.put(cacheKey, new CacheEntry(encoding, DEFAULT_TTL));
        log.debug("💾 已缓存: {}", cacheKey);

        return encoding;
    }

    /**
     * 使缓存失效
     *
     * @param tableName 表名
     */
    public static void invalidate(String tableName) {
        invalidate(tableName, "");
    }

    /**
     * 使缓存失效（支持 mapType）
     *
     * @param tableName 表名
     * @param mapType   World表的地图类型
     */
    public static void invalidate(String tableName, String mapType) {
        String cacheKey = buildCacheKey(tableName, mapType);
        cache.remove(cacheKey);
        log.debug("🗑️ 缓存已失效: {}", cacheKey);
    }

    /**
     * 清空所有缓存
     */
    public static void clearAll() {
        int size = cache.size();
        cache.clear();
        log.info("🗑️ 已清空所有缓存，共 {} 项", size);
    }

    /**
     * 预热缓存（批量加载）
     *
     * @param tableNames 表名列表
     */
    public static void warmup(String... tableNames) {
        for (String tableName : tableNames) {
            getWithCache(tableName);
        }
        log.info("🔥 缓存预热完成，加载 {} 个表", tableNames.length);
    }

    /**
     * 清理过期缓存
     */
    private static void cleanupExpired() {
        try {
            int removed = 0;
            for (var entry : cache.entrySet()) {
                if (entry.getValue().isExpired()) {
                    cache.remove(entry.getKey());
                    removed++;
                }
            }
            if (removed > 0) {
                log.debug("🧹 清理过期缓存: {} 项", removed);
            }
        } catch (Exception e) {
            log.error("清理缓存失败", e);
        }
    }

    /**
     * 获取缓存统计信息
     *
     * @return 统计信息
     */
    public static String getStatistics() {
        int total = cache.size();
        int active = 0;
        int expired = 0;

        for (var entry : cache.values()) {
            if (entry.isExpired()) {
                expired++;
            } else {
                active++;
            }
        }

        return String.format(
                "缓存统计: 总计=%d, 有效=%d, 过期=%d",
                total, active, expired
        );
    }

    /**
     * 获取缓存命中率（需要配合调用计数器实现）
     *
     * @return 缓存详情
     */
    public static String getCacheDetails() {
        StringBuilder details = new StringBuilder("=== 编码元数据缓存详情 ===\n");
        details.append(getStatistics()).append("\n");

        if (cache.isEmpty()) {
            details.append("缓存为空\n");
        } else {
            details.append("\n缓存内容:\n");
            for (var entry : cache.entrySet()) {
                CacheEntry value = entry.getValue();
                long remainingTime = value.expireTime - System.currentTimeMillis();
                details.append(String.format("  %s: %s (剩余: %d秒)\n",
                        entry.getKey(),
                        value.getEncoding(),
                        Math.max(0, remainingTime / 1000)));
            }
        }

        return details.toString();
    }
}
