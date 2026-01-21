package red.jiuzhou.ops.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 操作审计日志系统
 *
 * 记录所有运维操作，支持：
 * - 操作追踪和回溯
 * - 实时日志推送
 * - 按类型/时间/操作员筛选
 * - 导出日志报告
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);
    private static final int MAX_LOG_SIZE = 10000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static AuditLog instance;

    private final Deque<AuditEntry> entries = new ConcurrentLinkedDeque<>();
    private final List<Consumer<AuditEntry>> listeners = new CopyOnWriteArrayList<>();

    private AuditLog() {}

    public static synchronized AuditLog getInstance() {
        if (instance == null) {
            instance = new AuditLog();
        }
        return instance;
    }

    // ==================== 日志记录 ====================

    /**
     * 记录操作日志
     */
    public AuditEntry record(AuditEntry entry) {
        entries.addFirst(entry);

        // Limit size
        while (entries.size() > MAX_LOG_SIZE) {
            entries.removeLast();
        }

        // Notify listeners
        for (Consumer<AuditEntry> listener : listeners) {
            try {
                listener.accept(entry);
            } catch (Exception e) {
                log.error("审计日志监听器异常", e);
            }
        }

        // Also log to file
        logToFile(entry);

        return entry;
    }

    /**
     * 快速记录操作
     */
    public AuditEntry record(OperationType type, String operation, String target, String detail) {
        return record(AuditEntry.builder()
                .type(type)
                .operation(operation)
                .target(target)
                .detail(detail)
                .build());
    }

    /**
     * 记录成功操作
     */
    public AuditEntry success(OperationType type, String operation, String target, String detail) {
        return record(AuditEntry.builder()
                .type(type)
                .operation(operation)
                .target(target)
                .detail(detail)
                .status(OperationStatus.SUCCESS)
                .build());
    }

    /**
     * 记录失败操作
     */
    public AuditEntry failure(OperationType type, String operation, String target, String error) {
        return record(AuditEntry.builder()
                .type(type)
                .operation(operation)
                .target(target)
                .detail(error)
                .status(OperationStatus.FAILED)
                .build());
    }

    /**
     * 记录警告
     */
    public AuditEntry warning(OperationType type, String operation, String target, String warning) {
        return record(AuditEntry.builder()
                .type(type)
                .operation(operation)
                .target(target)
                .detail(warning)
                .status(OperationStatus.WARNING)
                .build());
    }

    /**
     * 记录信息（等同于 success，用于一般性信息记录）
     */
    public AuditEntry info(OperationType type, String operation, String target, String info) {
        return record(AuditEntry.builder()
                .type(type)
                .operation(operation)
                .target(target)
                .detail(info)
                .status(OperationStatus.SUCCESS)
                .build());
    }

    // ==================== 日志查询 ====================

    /**
     * 获取最近的日志
     */
    public List<AuditEntry> getRecent(int count) {
        return entries.stream()
                .limit(count)
                .toList();
    }

    /**
     * 获取最近的日志条目（别名）
     */
    public List<AuditEntry> getRecentEntries(int count) {
        return getRecent(count);
    }

    /**
     * 获取所有日志
     */
    public List<AuditEntry> getAll() {
        return new ArrayList<>(entries);
    }

    /**
     * 按类型筛选
     */
    public List<AuditEntry> filterByType(OperationType type) {
        return entries.stream()
                .filter(e -> e.type() == type)
                .toList();
    }

    /**
     * 按状态筛选
     */
    public List<AuditEntry> filterByStatus(OperationStatus status) {
        return entries.stream()
                .filter(e -> e.status() == status)
                .toList();
    }

    /**
     * 按时间范围筛选
     */
    public List<AuditEntry> filterByTimeRange(LocalDateTime from, LocalDateTime to) {
        return entries.stream()
                .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to))
                .toList();
    }

    /**
     * 按目标筛选（模糊匹配）
     */
    public List<AuditEntry> filterByTarget(String targetKeyword) {
        String lower = targetKeyword.toLowerCase();
        return entries.stream()
                .filter(e -> e.target() != null && e.target().toLowerCase().contains(lower))
                .toList();
    }

    /**
     * 复合筛选
     */
    public List<AuditEntry> filter(AuditFilter filter) {
        return entries.stream()
                .filter(e -> filter.matches(e))
                .toList();
    }

    // ==================== 统计 ====================

    /**
     * 获取操作统计
     */
    public Map<OperationType, Long> getStatsByType() {
        return entries.stream()
                .collect(Collectors.groupingBy(AuditEntry::type, Collectors.counting()));
    }

    /**
     * 获取状态统计
     */
    public Map<OperationStatus, Long> getStatsByStatus() {
        return entries.stream()
                .collect(Collectors.groupingBy(AuditEntry::status, Collectors.counting()));
    }

    /**
     * 获取最近N分钟内的操作数
     */
    public long getRecentOperationCount(int minutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(minutes);
        return entries.stream()
                .filter(e -> e.timestamp().isAfter(threshold))
                .count();
    }

    // ==================== 监听器 ====================

    /**
     * 添加日志监听器（用于实时UI更新）
     */
    public void addListener(Consumer<AuditEntry> listener) {
        listeners.add(listener);
    }

    /**
     * 移除监听器
     */
    public void removeListener(Consumer<AuditEntry> listener) {
        listeners.remove(listener);
    }

    // ==================== 导出 ====================

    /**
     * 导出为文本格式
     */
    public String exportAsText(List<AuditEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════════════\n");
        sb.append("                    操作审计日志报告\n");
        sb.append("                 生成时间: ").append(LocalDateTime.now()).append("\n");
        sb.append("═══════════════════════════════════════════════════════════════\n\n");

        for (AuditEntry entry : entries) {
            sb.append(formatEntry(entry)).append("\n");
        }

        sb.append("\n═══════════════════════════════════════════════════════════════\n");
        sb.append("总计: ").append(entries.size()).append(" 条记录\n");

        return sb.toString();
    }

    /**
     * 格式化单条日志
     */
    public String formatEntry(AuditEntry entry) {
        return String.format("[%s] %s %s | %s | %s | %s",
                entry.timestamp().format(TIME_FORMAT),
                entry.status().getIcon(),
                entry.type().getDisplay(),
                entry.operation(),
                entry.target() != null ? entry.target() : "-",
                entry.detail() != null ? entry.detail() : ""
        );
    }

    private void logToFile(AuditEntry entry) {
        if (entry.status() == OperationStatus.FAILED) {
            log.error("[AUDIT] {} - {} - {} - {}",
                    entry.type(), entry.operation(), entry.target(), entry.detail());
        } else if (entry.status() == OperationStatus.WARNING) {
            log.warn("[AUDIT] {} - {} - {} - {}",
                    entry.type(), entry.operation(), entry.target(), entry.detail());
        } else {
            log.info("[AUDIT] {} - {} - {} - {}",
                    entry.type(), entry.operation(), entry.target(), entry.detail());
        }
    }

    /**
     * 清空日志
     */
    public void clear() {
        entries.clear();
        log.info("审计日志已清空");
    }

    // ==================== 数据类 ====================

    /**
     * 审计日志条目
     */
    public record AuditEntry(
            String id,
            LocalDateTime timestamp,
            OperationType type,
            String operation,
            String target,
            String detail,
            OperationStatus status,
            String operator,
            Map<String, Object> metadata
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private OperationType type = OperationType.OTHER;
            private String operation = "";
            private String target;
            private String detail;
            private OperationStatus status = OperationStatus.SUCCESS;
            private String operator = "system";
            private Map<String, Object> metadata = new HashMap<>();

            public Builder type(OperationType type) {
                this.type = type;
                return this;
            }

            public Builder operation(String operation) {
                this.operation = operation;
                return this;
            }

            public Builder target(String target) {
                this.target = target;
                return this;
            }

            public Builder detail(String detail) {
                this.detail = detail;
                return this;
            }

            public Builder status(OperationStatus status) {
                this.status = status;
                return this;
            }

            public Builder operator(String operator) {
                this.operator = operator;
                return this;
            }

            public Builder metadata(String key, Object value) {
                this.metadata.put(key, value);
                return this;
            }

            public AuditEntry build() {
                String id = "LOG-" + System.currentTimeMillis() + "-" +
                        (int) (Math.random() * 10000);
                return new AuditEntry(
                        id,
                        LocalDateTime.now(),
                        type,
                        operation,
                        target,
                        detail,
                        status,
                        operator,
                        metadata
                );
            }
        }
    }

    /**
     * 操作类型
     */
    public enum OperationType {
        CHARACTER("角色操作", "👤"),
        GUILD("公会操作", "👥"),
        ITEM("物品操作", "📦"),
        ACCOUNT("账号操作", "🔐"),
        SYSTEM("系统操作", "⚙️"),
        DATABASE("数据库操作", "💾"),
        QUERY("查询操作", "🔍"),
        MAIL("邮件操作", "📧"),
        AUCTION("拍卖操作", "🏪"),
        QUEST("任务操作", "📜"),
        SKILL("技能操作", "⚔️"),
        OTHER("其他操作", "📋");

        private final String display;
        private final String icon;

        OperationType(String display, String icon) {
            this.display = display;
            this.icon = icon;
        }

        public String getDisplay() {
            return display;
        }

        public String getIcon() {
            return icon;
        }
    }

    /**
     * 操作状态
     */
    public enum OperationStatus {
        SUCCESS("成功", "✅", "#27ae60"),
        FAILED("失败", "❌", "#e74c3c"),
        WARNING("警告", "⚠️", "#f39c12"),
        PENDING("进行中", "⏳", "#3498db");

        private final String display;
        private final String icon;
        private final String color;

        OperationStatus(String display, String icon, String color) {
            this.display = display;
            this.icon = icon;
            this.color = color;
        }

        public String getDisplay() {
            return display;
        }

        public String getIcon() {
            return icon;
        }

        public String getColor() {
            return color;
        }
    }

    /**
     * 日志筛选器
     */
    public record AuditFilter(
            OperationType type,
            OperationStatus status,
            LocalDateTime from,
            LocalDateTime to,
            String targetKeyword,
            String operator
    ) {
        public boolean matches(AuditEntry entry) {
            if (type != null && entry.type() != type) return false;
            if (status != null && entry.status() != status) return false;
            if (from != null && entry.timestamp().isBefore(from)) return false;
            if (to != null && entry.timestamp().isAfter(to)) return false;
            if (targetKeyword != null && (entry.target() == null ||
                    !entry.target().toLowerCase().contains(targetKeyword.toLowerCase()))) return false;
            if (operator != null && !operator.equals(entry.operator())) return false;
            return true;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private OperationType type;
            private OperationStatus status;
            private LocalDateTime from;
            private LocalDateTime to;
            private String targetKeyword;
            private String operator;

            public Builder type(OperationType type) { this.type = type; return this; }
            public Builder status(OperationStatus status) { this.status = status; return this; }
            public Builder from(LocalDateTime from) { this.from = from; return this; }
            public Builder to(LocalDateTime to) { this.to = to; return this; }
            public Builder targetKeyword(String keyword) { this.targetKeyword = keyword; return this; }
            public Builder operator(String operator) { this.operator = operator; return this; }

            public AuditFilter build() {
                return new AuditFilter(type, status, from, to, targetKeyword, operator);
            }
        }
    }

    /**
     * 审计统计信息
     */
    public record AuditStats(
            long totalEntries,
            long successCount,
            long failureCount,
            long warningCount,
            Map<OperationType, Long> typeBreakdown
    ) {
        public String getSummary() {
            return String.format("总计: %d, 成功: %d, 失败: %d, 警告: %d",
                    totalEntries, successCount, failureCount, warningCount);
        }

        public long totalOperations() {
            return totalEntries;
        }
    }

    /**
     * 获取审计统计信息
     */
    public AuditStats getStats() {
        Map<OperationStatus, Long> statusStats = getStatsByStatus();
        Map<OperationType, Long> typeStats = getStatsByType();

        return new AuditStats(
                entries.size(),
                statusStats.getOrDefault(OperationStatus.SUCCESS, 0L),
                statusStats.getOrDefault(OperationStatus.FAILED, 0L),
                statusStats.getOrDefault(OperationStatus.WARNING, 0L),
                typeStats
        );
    }
}
