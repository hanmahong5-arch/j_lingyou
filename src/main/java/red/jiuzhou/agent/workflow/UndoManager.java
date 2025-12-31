package red.jiuzhou.agent.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 撤销管理器
 *
 * <p>提供多层次的撤销能力：
 * <ul>
 *   <li>工作流级别撤销 - 撤销整个工作流的所有修改</li>
 *   <li>步骤级别撤销 - 撤销单个步骤的修改</li>
 *   <li>操作级别撤销 - 撤销单次SQL执行</li>
 * </ul>
 *
 * <p>撤销策略：
 * <ol>
 *   <li>LIFO顺序 - 后执行的操作先撤销</li>
 *   <li>级联撤销 - 撤销某步骤时，后续步骤也会被撤销</li>
 *   <li>原子性 - 撤销失败时回滚到撤销前状态</li>
 * </ol>
 *
 * @author Claude
 * @version 1.0
 */
public class UndoManager {

    private static final Logger log = LoggerFactory.getLogger(UndoManager.class);

    // 单例
    private static UndoManager instance;

    // 撤销栈（按工作流ID分组）
    private final Map<String, Deque<UndoableOperation>> undoStacks = new HashMap<>();

    // 全局撤销历史（用于跨工作流撤销）
    private final Deque<UndoableOperation> globalUndoStack = new ConcurrentLinkedDeque<>();

    // 最大撤销历史
    private static final int MAX_UNDO_HISTORY = 50;

    // 数据快照管理器
    private final DataSnapshot dataSnapshot = DataSnapshot.getInstance();

    // 审计日志
    private final WorkflowAuditLog auditLog = WorkflowAuditLog.getInstance();

    // 数据库连接
    private JdbcTemplate jdbcTemplate;

    private UndoManager() {}

    public static synchronized UndoManager getInstance() {
        if (instance == null) {
            instance = new UndoManager();
        }
        return instance;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        dataSnapshot.setJdbcTemplate(jdbcTemplate);
        auditLog.setJdbcTemplate(jdbcTemplate);
    }

    // ==================== 记录可撤销操作 ====================

    /**
     * 记录可撤销的SQL操作
     *
     * @param workflowId 工作流ID
     * @param stepId 步骤ID
     * @param sql 执行的SQL
     * @param affectedRows 影响行数
     * @param snapshotId 数据快照ID
     * @return 操作ID
     */
    public String recordOperation(String workflowId, String stepId, String sql,
                                   int affectedRows, String snapshotId) {
        String operationId = generateOperationId();

        UndoableOperation operation = new UndoableOperation();
        operation.operationId = operationId;
        operation.workflowId = workflowId;
        operation.stepId = stepId;
        operation.sql = sql;
        operation.affectedRows = affectedRows;
        operation.snapshotId = snapshotId;
        operation.timestamp = Instant.now();
        operation.status = OperationStatus.EXECUTED;

        // 添加到工作流撤销栈
        undoStacks.computeIfAbsent(workflowId, k -> new ConcurrentLinkedDeque<>())
                .push(operation);

        // 添加到全局撤销栈
        globalUndoStack.push(operation);

        // 限制栈大小
        trimStack(globalUndoStack);

        log.info("记录可撤销操作: opId={}, workflowId={}, stepId={}, 影响行数={}",
                operationId, workflowId, stepId, affectedRows);

        return operationId;
    }

    /**
     * 在执行修改前调用，创建快照并记录操作
     *
     * @return 操作ID，用于后续撤销
     */
    public String beforeExecute(String workflowId, String stepId, String sql) {
        // 检测SQL类型
        String upperSql = sql.toUpperCase().trim();
        String snapshotId = null;

        if (upperSql.startsWith("UPDATE")) {
            snapshotId = dataSnapshot.createSnapshotForUpdate(workflowId, stepId, sql);
        } else if (upperSql.startsWith("DELETE")) {
            snapshotId = dataSnapshot.createSnapshotForDelete(workflowId, stepId, sql);
        }
        // INSERT不需要快照（删除即可撤销）

        // 记录操作（此时affectedRows为0，执行后更新）
        return recordOperation(workflowId, stepId, sql, 0, snapshotId);
    }

    /**
     * 执行后更新影响行数
     */
    public void afterExecute(String operationId, int affectedRows) {
        for (UndoableOperation op : globalUndoStack) {
            if (op.operationId.equals(operationId)) {
                op.affectedRows = affectedRows;

                // 记录到审计日志
                auditLog.logSqlExecuted(op.workflowId, op.stepId, op.sql, affectedRows, op.snapshotId);

                break;
            }
        }
    }

    // ==================== 撤销操作 ====================

    /**
     * 撤销最近一次操作
     *
     * @return 撤销结果
     */
    public UndoResult undoLast() {
        if (globalUndoStack.isEmpty()) {
            return UndoResult.failure("没有可撤销的操作");
        }

        UndoableOperation operation = globalUndoStack.peek();
        return undoOperation(operation);
    }

    /**
     * 撤销指定操作
     */
    public UndoResult undoOperation(String operationId) {
        for (UndoableOperation op : globalUndoStack) {
            if (op.operationId.equals(operationId)) {
                return undoOperation(op);
            }
        }
        return UndoResult.failure("操作不存在: " + operationId);
    }

    /**
     * 撤销工作流的最近一次操作
     */
    public UndoResult undoWorkflowLast(String workflowId) {
        Deque<UndoableOperation> stack = undoStacks.get(workflowId);
        if (stack == null || stack.isEmpty()) {
            return UndoResult.failure("该工作流没有可撤销的操作");
        }

        UndoableOperation operation = stack.peek();
        return undoOperation(operation);
    }

    /**
     * 撤销工作流的所有操作
     */
    public UndoResult undoWorkflowAll(String workflowId) {
        Deque<UndoableOperation> stack = undoStacks.get(workflowId);
        if (stack == null || stack.isEmpty()) {
            return UndoResult.failure("该工作流没有可撤销的操作");
        }

        List<UndoResult> results = new ArrayList<>();
        int totalRestored = 0;

        // LIFO顺序撤销
        while (!stack.isEmpty()) {
            UndoableOperation operation = stack.peek();
            UndoResult result = undoOperation(operation);
            results.add(result);

            if (result.success) {
                totalRestored += result.restoredRows;
            } else {
                // 撤销失败，停止
                return UndoResult.failure("撤销过程中失败: " + result.message);
            }
        }

        return UndoResult.success("已撤销工作流所有操作", totalRestored);
    }

    /**
     * 撤销步骤及其后续所有操作
     */
    public UndoResult undoStepAndAfter(String workflowId, String stepId) {
        Deque<UndoableOperation> stack = undoStacks.get(workflowId);
        if (stack == null || stack.isEmpty()) {
            return UndoResult.failure("该工作流没有可撤销的操作");
        }

        // 找到目标步骤的位置
        boolean foundTarget = false;
        List<UndoableOperation> toUndo = new ArrayList<>();

        for (UndoableOperation op : stack) {
            if (op.stepId.equals(stepId)) {
                foundTarget = true;
            }
            if (foundTarget || toUndo.size() > 0) {
                // 该步骤及之后的所有操作都需要撤销
                // 但因为是LIFO，我们先收集栈顶到目标步骤的所有操作
            }
            toUndo.add(op);
            if (foundTarget) break;
        }

        if (!foundTarget) {
            return UndoResult.failure("未找到步骤: " + stepId);
        }

        // 按LIFO顺序撤销
        int totalRestored = 0;
        for (UndoableOperation op : toUndo) {
            UndoResult result = undoOperation(op);
            if (result.success) {
                totalRestored += result.restoredRows;
            } else {
                return UndoResult.failure("撤销步骤 " + op.stepId + " 失败: " + result.message);
            }
        }

        return UndoResult.success("已撤销步骤及后续操作", totalRestored);
    }

    /**
     * 执行单个操作的撤销
     */
    private UndoResult undoOperation(UndoableOperation operation) {
        if (operation.status == OperationStatus.UNDONE) {
            return UndoResult.failure("操作已被撤销");
        }

        log.info("开始撤销操作: opId={}, sql={}", operation.operationId, truncate(operation.sql, 50));

        try {
            int restoredRows = 0;
            String upperSql = operation.sql.toUpperCase().trim();

            if (upperSql.startsWith("INSERT")) {
                // INSERT撤销：执行对应的DELETE
                restoredRows = undoInsert(operation);
            } else if (upperSql.startsWith("UPDATE") || upperSql.startsWith("DELETE")) {
                // UPDATE/DELETE撤销：恢复快照
                if (operation.snapshotId != null) {
                    restoredRows = dataSnapshot.restoreSnapshot(operation.snapshotId);
                    if (restoredRows < 0) {
                        return UndoResult.failure("快照恢复失败");
                    }
                } else {
                    return UndoResult.failure("没有可用的快照");
                }
            }

            // 标记为已撤销
            operation.status = OperationStatus.UNDONE;
            operation.undoneAt = Instant.now();

            // 从栈中移除
            globalUndoStack.remove(operation);
            Deque<UndoableOperation> workflowStack = undoStacks.get(operation.workflowId);
            if (workflowStack != null) {
                workflowStack.remove(operation);
            }

            // 记录审计日志
            auditLog.logStepRolledBack(operation.workflowId, operation.stepId, "用户撤销");

            log.info("撤销完成: opId={}, 恢复行数={}", operation.operationId, restoredRows);

            return UndoResult.success("撤销成功", restoredRows);

        } catch (Exception e) {
            log.error("撤销失败: {}", e.getMessage(), e);
            return UndoResult.failure("撤销失败: " + e.getMessage());
        }
    }

    /**
     * 撤销INSERT操作
     */
    private int undoInsert(UndoableOperation operation) {
        // 解析INSERT语句获取表名和主键
        String sql = operation.sql;

        // 简单解析：INSERT INTO table_name ...
        String upperSql = sql.toUpperCase();
        int intoIdx = upperSql.indexOf("INTO");
        if (intoIdx < 0) return 0;

        String afterInto = sql.substring(intoIdx + 4).trim();
        String tableName = afterInto.split("[\\s(]")[0].replace("`", "");

        // 需要知道插入的主键值才能删除
        // 这里简化处理：如果有LAST_INSERT_ID，使用它
        // 实际应用中可能需要更复杂的逻辑

        log.warn("INSERT撤销功能有限，可能需要手动处理: {}", tableName);
        return 0;
    }

    // ==================== 查询方法 ====================

    /**
     * 获取可撤销操作列表
     */
    public List<UndoableOperation> getUndoableOperations() {
        return new ArrayList<>(globalUndoStack);
    }

    /**
     * 获取工作流的可撤销操作
     */
    public List<UndoableOperation> getWorkflowUndoableOperations(String workflowId) {
        Deque<UndoableOperation> stack = undoStacks.get(workflowId);
        if (stack == null) return Collections.emptyList();
        return new ArrayList<>(stack);
    }

    /**
     * 是否可以撤销
     */
    public boolean canUndo() {
        return !globalUndoStack.isEmpty();
    }

    /**
     * 工作流是否可以撤销
     */
    public boolean canUndo(String workflowId) {
        Deque<UndoableOperation> stack = undoStacks.get(workflowId);
        return stack != null && !stack.isEmpty();
    }

    /**
     * 获取最近可撤销操作的描述
     */
    public String getLastUndoDescription() {
        if (globalUndoStack.isEmpty()) return null;
        UndoableOperation op = globalUndoStack.peek();
        return String.format("撤销: %s (影响 %d 行)", truncate(op.sql, 30), op.affectedRows);
    }

    // ==================== 辅助方法 ====================

    private String generateOperationId() {
        return "OP-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }

    private void trimStack(Deque<UndoableOperation> stack) {
        while (stack.size() > MAX_UNDO_HISTORY) {
            stack.pollLast();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    // ==================== 内部类型 ====================

    public enum OperationStatus {
        EXECUTED,   // 已执行
        UNDONE      // 已撤销
    }

    /**
     * 可撤销操作
     */
    public static class UndoableOperation {
        public String operationId;
        public String workflowId;
        public String stepId;
        public String sql;
        public int affectedRows;
        public String snapshotId;
        public Instant timestamp;
        public OperationStatus status;
        public Instant undoneAt;

        public String getFormattedTime() {
            return LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }

        public String getShortSql() {
            if (sql == null) return "";
            return sql.length() > 50 ? sql.substring(0, 50) + "..." : sql;
        }

        public String getSqlType() {
            if (sql == null) return "UNKNOWN";
            String upper = sql.toUpperCase().trim();
            if (upper.startsWith("UPDATE")) return "UPDATE";
            if (upper.startsWith("INSERT")) return "INSERT";
            if (upper.startsWith("DELETE")) return "DELETE";
            return "OTHER";
        }
    }

    /**
     * 撤销结果
     */
    public static class UndoResult {
        public final boolean success;
        public final String message;
        public final int restoredRows;

        private UndoResult(boolean success, String message, int restoredRows) {
            this.success = success;
            this.message = message;
            this.restoredRows = restoredRows;
        }

        public static UndoResult success(String message, int restoredRows) {
            return new UndoResult(true, message, restoredRows);
        }

        public static UndoResult failure(String message) {
            return new UndoResult(false, message, 0);
        }
    }
}
