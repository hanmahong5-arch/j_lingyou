package red.jiuzhou.ui.event;

import javafx.scene.control.Tab;
import red.jiuzhou.ops.core.EventBus;

/**
 * UI 层事件定义
 *
 * 基于现有 EventBus 框架，定义 UI 层特有的事件类型。
 * 这些事件用于解耦 UI 组件间的通信，实现松耦合架构。
 *
 * 使用示例:
 * <pre>
 * // 订阅事件
 * EventBus.getInstance().subscribe(UIEvents.ImportCompletedEvent.class, event -> {
 *     if (event.isSuccess()) {
 *         refreshTableView(event.getTableName());
 *     }
 * });
 *
 * // 发布事件
 * EventBus.getInstance().publish(new UIEvents.ImportCompletedEvent("armor", 1500, true, filePath));
 * </pre>
 */
public final class UIEvents {

    private UIEvents() {
        // 工具类，禁止实例化
    }

    // ==================== Tab 状态相关事件 ====================

    /**
     * Tab 状态变更事件
     *
     * 当用户在文件树中切换文件时发布此事件，
     * 所有关心 Tab 状态的组件都可以订阅并响应。
     */
    public static class TabStateChangedEvent extends EventBus.GameEvent {
        private final Tab tab;
        private final String oldFilePath;
        private final String newFilePath;

        public TabStateChangedEvent(Tab tab, String oldFilePath, String newFilePath) {
            this.tab = tab;
            this.oldFilePath = oldFilePath;
            this.newFilePath = newFilePath;
        }

        public Tab getTab() { return tab; }
        public String getOldFilePath() { return oldFilePath; }
        public String getNewFilePath() { return newFilePath; }

        @Override
        public String toString() {
            return String.format("TabStateChangedEvent[%s -> %s]", oldFilePath, newFilePath);
        }
    }

    // ==================== 数据操作事件 ====================

    /**
     * 数据导入完成事件
     *
     * XML 导入到数据库完成后发布，用于：
     * - 刷新表格视图
     * - 更新文件状态缓存
     * - 显示操作结果通知
     */
    public static class ImportCompletedEvent extends EventBus.GameEvent {
        private final String tableName;
        private final int rowCount;
        private final boolean success;
        private final String filePath;
        private final String errorMessage;

        public ImportCompletedEvent(String tableName, int rowCount, boolean success, String filePath) {
            this(tableName, rowCount, success, filePath, null);
        }

        public ImportCompletedEvent(String tableName, int rowCount, boolean success, String filePath, String errorMessage) {
            this.tableName = tableName;
            this.rowCount = rowCount;
            this.success = success;
            this.filePath = filePath;
            this.errorMessage = errorMessage;
        }

        public String getTableName() { return tableName; }
        public int getRowCount() { return rowCount; }
        public boolean isSuccess() { return success; }
        public String getFilePath() { return filePath; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            return String.format("ImportCompletedEvent[%s, rows=%d, success=%s]", tableName, rowCount, success);
        }
    }

    /**
     * 数据导出完成事件
     *
     * 数据库导出到 XML 完成后发布
     */
    public static class ExportCompletedEvent extends EventBus.GameEvent {
        private final String tableName;
        private final String filePath;
        private final boolean success;
        private final String errorMessage;

        public ExportCompletedEvent(String tableName, String filePath, boolean success) {
            this(tableName, filePath, success, null);
        }

        public ExportCompletedEvent(String tableName, String filePath, boolean success, String errorMessage) {
            this.tableName = tableName;
            this.filePath = filePath;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public String getTableName() { return tableName; }
        public String getFilePath() { return filePath; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }

        @Override
        public String toString() {
            return String.format("ExportCompletedEvent[%s, success=%s]", tableName, success);
        }
    }

    /**
     * DDL 生成完成事件
     *
     * DDL 生成并执行完成后发布，用于：
     * - 更新表结构缓存
     * - 刷新 DDL 状态图标
     */
    public static class DdlGeneratedEvent extends EventBus.GameEvent {
        private final String tableName;
        private final String sqlFilePath;
        private final boolean success;

        public DdlGeneratedEvent(String tableName, String sqlFilePath, boolean success) {
            this.tableName = tableName;
            this.sqlFilePath = sqlFilePath;
            this.success = success;
        }

        public String getTableName() { return tableName; }
        public String getSqlFilePath() { return sqlFilePath; }
        public boolean isSuccess() { return success; }

        @Override
        public String toString() {
            return String.format("DdlGeneratedEvent[%s, success=%s]", tableName, success);
        }
    }

    // ==================== 缓存相关事件 ====================

    /**
     * 缓存失效事件
     *
     * 当缓存被手动或自动失效时发布，
     * 需要刷新数据的组件可以订阅此事件。
     */
    public static class CacheInvalidatedEvent extends EventBus.GameEvent {
        private final String cacheKey;
        private final String cacheType; // "config", "metadata", "structure"
        private final String reason;

        public CacheInvalidatedEvent(String cacheKey, String cacheType, String reason) {
            this.cacheKey = cacheKey;
            this.cacheType = cacheType;
            this.reason = reason;
        }

        public String getCacheKey() { return cacheKey; }
        public String getCacheType() { return cacheType; }
        public String getReason() { return reason; }

        @Override
        public String toString() {
            return String.format("CacheInvalidatedEvent[%s, type=%s, reason=%s]", cacheKey, cacheType, reason);
        }
    }

    // ==================== 批量操作事件 ====================

    /**
     * 批量操作进度事件
     *
     * 批量导入/导出过程中发布，用于更新进度条
     */
    public static class BatchProgressEvent extends EventBus.GameEvent {
        private final String operationType; // "import", "export", "ddl"
        private final int current;
        private final int total;
        private final String currentItem;

        public BatchProgressEvent(String operationType, int current, int total, String currentItem) {
            this.operationType = operationType;
            this.current = current;
            this.total = total;
            this.currentItem = currentItem;
        }

        public String getOperationType() { return operationType; }
        public int getCurrent() { return current; }
        public int getTotal() { return total; }
        public String getCurrentItem() { return currentItem; }
        public double getProgress() { return total > 0 ? (double) current / total : 0; }

        @Override
        public String toString() {
            return String.format("BatchProgressEvent[%s, %d/%d, %s]", operationType, current, total, currentItem);
        }
    }

    /**
     * 批量操作完成事件
     */
    public static class BatchCompletedEvent extends EventBus.GameEvent {
        private final String operationType;
        private final int successCount;
        private final int failureCount;
        private final long durationMs;

        public BatchCompletedEvent(String operationType, int successCount, int failureCount, long durationMs) {
            this.operationType = operationType;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.durationMs = durationMs;
        }

        public String getOperationType() { return operationType; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public int getTotalCount() { return successCount + failureCount; }
        public long getDurationMs() { return durationMs; }

        @Override
        public String toString() {
            return String.format("BatchCompletedEvent[%s, success=%d, failure=%d, duration=%dms]",
                    operationType, successCount, failureCount, durationMs);
        }
    }
}
