package red.jiuzhou.ops.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存管理器
 *
 * 提供内存缓存支持：
 * - TTL 过期控制
 * - LRU 淘汰策略
 * - 缓存统计
 * - 线程安全
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class CacheManager {

    private static final Logger log = LoggerFactory.getLogger(CacheManager.class);

    private static CacheManager instance;

    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private final int maxSize;
    private final ScheduledExecutorService cleanupExecutor;

    // Statistics
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    private CacheManager() {
        this(10000); // Default max 10000 entries
    }

    private CacheManager(int maxSize) {
        this.maxSize = maxSize;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cache-cleanup");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic cleanup
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 1, 1, TimeUnit.MINUTES);
    }

    public static synchronized CacheManager getInstance() {
        if (instance == null) {
            instance = new CacheManager();
        }
        return instance;
    }

    // ==================== 基本操作 ====================

    /**
     * 获取缓存值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        CacheEntry<?> entry = cache.get(key);

        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(key);
            misses.incrementAndGet();
            evictions.incrementAndGet();
            return null;
        }

        entry.touch();
        hits.incrementAndGet();

        try {
            return (T) entry.value;
        } catch (ClassCastException e) {
            log.warn("缓存类型不匹配: key={}, expected={}, actual={}",
                    key, type.getName(), entry.value.getClass().getName());
            return null;
        }
    }

    /**
     * 获取或计算缓存值
     */
    public <T> T getOrCompute(String key, Class<T> type,
                              java.util.function.Supplier<T> supplier, int ttlSeconds) {
        T value = get(key, type);
        if (value != null) {
            return value;
        }

        value = supplier.get();
        if (value != null) {
            put(key, value, ttlSeconds);
        }
        return value;
    }

    /**
     * 放入缓存（默认5分钟）
     */
    public <T> void put(String key, T value) {
        put(key, value, 300);
    }

    /**
     * 放入缓存（指定TTL）
     */
    public <T> void put(String key, T value, int ttlSeconds) {
        if (value == null) {
            return;
        }

        // Evict if at capacity
        if (cache.size() >= maxSize) {
            evictOldest();
        }

        cache.put(key, new CacheEntry<>(value, ttlSeconds));
    }

    /**
     * 移除缓存
     */
    public void remove(String key) {
        CacheEntry<?> removed = cache.remove(key);
        if (removed != null) {
            evictions.incrementAndGet();
        }
    }

    /**
     * 按前缀移除
     */
    public int removeByPrefix(String prefix) {
        int removed = 0;
        for (String key : cache.keySet()) {
            if (key.startsWith(prefix)) {
                cache.remove(key);
                removed++;
            }
        }
        evictions.addAndGet(removed);
        return removed;
    }

    /**
     * 检查是否存在
     */
    public boolean contains(String key) {
        CacheEntry<?> entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }

    /**
     * 清空缓存
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        evictions.addAndGet(size);
        log.info("缓存已清空，移除 {} 个条目", size);
    }

    // ==================== 统计 ====================

    /**
     * 获取缓存大小
     */
    public int size() {
        return cache.size();
    }

    /**
     * 获取命中率
     */
    public double getHitRate() {
        long total = hits.get() + misses.get();
        return total > 0 ? (double) hits.get() / total : 0;
    }

    /**
     * 获取统计信息
     */
    public CacheStats getStats() {
        return new CacheStats(
                cache.size(),
                maxSize,
                hits.get(),
                misses.get(),
                evictions.get(),
                getHitRate()
        );
    }

    /**
     * 重置统计
     */
    public void resetStats() {
        hits.set(0);
        misses.set(0);
        evictions.set(0);
    }

    // ==================== 内部方法 ====================

    private void cleanup() {
        int expired = 0;
        for (Map.Entry<String, CacheEntry<?>> entry : cache.entrySet()) {
            if (entry.getValue().isExpired()) {
                cache.remove(entry.getKey());
                expired++;
            }
        }

        if (expired > 0) {
            evictions.addAndGet(expired);
            log.debug("清理过期缓存: {} 个", expired);
        }
    }

    private void evictOldest() {
        String oldestKey = null;
        long oldestAccess = Long.MAX_VALUE;

        for (Map.Entry<String, CacheEntry<?>> entry : cache.entrySet()) {
            if (entry.getValue().lastAccess < oldestAccess) {
                oldestAccess = entry.getValue().lastAccess;
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            cache.remove(oldestKey);
            evictions.incrementAndGet();
        }
    }

    /**
     * 关闭缓存管理器
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 内部类 ====================

    private static class CacheEntry<T> {
        final T value;
        final long expireTime;
        volatile long lastAccess;

        CacheEntry(T value, int ttlSeconds) {
            this.value = value;
            this.expireTime = System.currentTimeMillis() + ttlSeconds * 1000L;
            this.lastAccess = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }

        void touch() {
            this.lastAccess = System.currentTimeMillis();
        }
    }

    /**
     * 缓存统计
     */
    public record CacheStats(
            int size,
            int maxSize,
            long hits,
            long misses,
            long evictions,
            double hitRate
    ) {
        public String getSummary() {
            return String.format(
                    "缓存: %d/%d | 命中率: %.1f%% | 命中: %d | 未命中: %d | 驱逐: %d",
                    size, maxSize, hitRate * 100, hits, misses, evictions
            );
        }
    }
}
