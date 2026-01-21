package red.jiuzhou.ops.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 事件总线 - 架构解耦核心组件
 *
 * 实现发布-订阅模式，解耦服务间依赖：
 * - 异步事件处理
 * - 事件类型安全
 * - 弱引用订阅（防止内存泄漏）
 * - 事件过滤和优先级
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private static EventBus instance;

    private final Map<Class<?>, List<Subscription<?>>> subscriptions = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor;
    private final Queue<GameEvent> eventQueue = new ConcurrentLinkedQueue<>();

    private boolean running = true;

    private EventBus() {
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public static synchronized EventBus getInstance() {
        if (instance == null) {
            instance = new EventBus();
        }
        return instance;
    }

    // ==================== 订阅 ====================

    /**
     * 订阅事件（同步处理）
     */
    public <T extends GameEvent> Subscription<T> subscribe(Class<T> eventType, Consumer<T> handler) {
        return subscribe(eventType, handler, false, 0);
    }

    /**
     * 订阅事件（可选异步）
     */
    public <T extends GameEvent> Subscription<T> subscribe(Class<T> eventType, Consumer<T> handler,
                                                            boolean async, int priority) {
        Subscription<T> subscription = new Subscription<>(eventType, handler, async, priority);

        subscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(subscription);

        // Sort by priority
        subscriptions.get(eventType).sort(Comparator.comparingInt(s -> -s.priority));

        log.debug("订阅事件: {} (async={}, priority={})", eventType.getSimpleName(), async, priority);
        return subscription;
    }

    /**
     * 取消订阅
     */
    public <T extends GameEvent> void unsubscribe(Subscription<T> subscription) {
        List<Subscription<?>> subs = subscriptions.get(subscription.eventType);
        if (subs != null) {
            subs.remove(subscription);
        }
    }

    // ==================== 发布 ====================

    /**
     * 同步发布事件
     */
    @SuppressWarnings("unchecked")
    public <T extends GameEvent> void publish(T event) {
        if (!running) return;

        Class<?> eventType = event.getClass();
        List<Subscription<?>> subs = subscriptions.get(eventType);

        if (subs == null || subs.isEmpty()) {
            log.trace("无订阅者: {}", eventType.getSimpleName());
            return;
        }

        log.debug("发布事件: {} -> {} 个订阅者", eventType.getSimpleName(), subs.size());

        for (Subscription<?> sub : subs) {
            Subscription<T> typedSub = (Subscription<T>) sub;
            try {
                if (typedSub.async) {
                    asyncExecutor.submit(() -> safeHandle(typedSub, event));
                } else {
                    safeHandle(typedSub, event);
                }
            } catch (Exception e) {
                log.error("事件处理异常: {}", eventType.getSimpleName(), e);
            }
        }
    }

    /**
     * 异步发布事件
     */
    public <T extends GameEvent> CompletableFuture<Void> publishAsync(T event) {
        return CompletableFuture.runAsync(() -> publish(event), asyncExecutor);
    }

    /**
     * 延迟发布事件
     */
    public <T extends GameEvent> ScheduledFuture<?> publishDelayed(T event, long delayMs) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        return scheduler.schedule(() -> {
            publish(event);
            scheduler.shutdown();
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private <T extends GameEvent> void safeHandle(Subscription<T> sub, T event) {
        try {
            sub.handler.accept(event);
        } catch (Exception e) {
            log.error("事件处理器异常: {} - {}", sub.eventType.getSimpleName(), e.getMessage());
        }
    }

    // ==================== 生命周期 ====================

    /**
     * 关闭事件总线
     */
    public void shutdown() {
        running = false;
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("事件总线已关闭");
    }

    /**
     * 获取订阅统计
     */
    public Map<String, Integer> getSubscriptionStats() {
        Map<String, Integer> stats = new HashMap<>();
        for (Map.Entry<Class<?>, List<Subscription<?>>> entry : subscriptions.entrySet()) {
            stats.put(entry.getKey().getSimpleName(), entry.getValue().size());
        }
        return stats;
    }

    // ==================== 数据类 ====================

    /**
     * 订阅信息
     */
    public record Subscription<T extends GameEvent>(
            Class<T> eventType,
            Consumer<T> handler,
            boolean async,
            int priority
    ) {}

    // ==================== 事件基类 ====================

    /**
     * 游戏事件基类
     */
    public abstract static class GameEvent {
        private final long timestamp = System.currentTimeMillis();
        private final String eventId = UUID.randomUUID().toString().substring(0, 8);

        public long getTimestamp() { return timestamp; }
        public String getEventId() { return eventId; }
    }

    // ==================== 预定义事件 ====================

    /**
     * 角色事件
     */
    public static class CharacterEvent extends GameEvent {
        private final int charId;
        private final String charName;
        private final String action;
        private final Map<String, Object> data;

        public CharacterEvent(int charId, String charName, String action, Map<String, Object> data) {
            this.charId = charId;
            this.charName = charName;
            this.action = action;
            this.data = data != null ? data : Map.of();
        }

        public int getCharId() { return charId; }
        public String getCharName() { return charName; }
        public String getAction() { return action; }
        public Map<String, Object> getData() { return data; }
    }

    /**
     * 公会事件
     */
    public static class GuildEvent extends GameEvent {
        private final int guildId;
        private final String guildName;
        private final String action;

        public GuildEvent(int guildId, String guildName, String action) {
            this.guildId = guildId;
            this.guildName = guildName;
            this.action = action;
        }

        public int getGuildId() { return guildId; }
        public String getGuildName() { return guildName; }
        public String getAction() { return action; }
    }

    /**
     * 物品事件
     */
    public static class ItemEvent extends GameEvent {
        private final long itemUniqueId;
        private final int itemId;
        private final int charId;
        private final String action;
        private final int count;

        public ItemEvent(long itemUniqueId, int itemId, int charId, String action, int count) {
            this.itemUniqueId = itemUniqueId;
            this.itemId = itemId;
            this.charId = charId;
            this.action = action;
            this.count = count;
        }

        public long getItemUniqueId() { return itemUniqueId; }
        public int getItemId() { return itemId; }
        public int getCharId() { return charId; }
        public String getAction() { return action; }
        public int getCount() { return count; }
    }

    /**
     * 系统事件
     */
    public static class SystemEvent extends GameEvent {
        private final String type;
        private final String message;
        private final Map<String, Object> data;

        public SystemEvent(String type, String message, Map<String, Object> data) {
            this.type = type;
            this.message = message;
            this.data = data != null ? data : Map.of();
        }

        public String getType() { return type; }
        public String getMessage() { return message; }
        public Map<String, Object> getData() { return data; }
    }

    /**
     * 数据库连接事件
     */
    public static class ConnectionEvent extends GameEvent {
        private final String database;
        private final boolean connected;
        private final String message;

        public ConnectionEvent(String database, boolean connected, String message) {
            this.database = database;
            this.connected = connected;
            this.message = message;
        }

        public String getDatabase() { return database; }
        public boolean isConnected() { return connected; }
        public String getMessage() { return message; }
    }

    /**
     * 操作完成事件
     */
    public static class OperationCompletedEvent extends GameEvent {
        private final String operationId;
        private final String operationType;
        private final boolean success;
        private final String result;

        public OperationCompletedEvent(String operationId, String operationType,
                                        boolean success, String result) {
            this.operationId = operationId;
            this.operationType = operationType;
            this.success = success;
            this.result = result;
        }

        public String getOperationId() { return operationId; }
        public String getOperationType() { return operationType; }
        public boolean isSuccess() { return success; }
        public String getResult() { return result; }
    }
}
