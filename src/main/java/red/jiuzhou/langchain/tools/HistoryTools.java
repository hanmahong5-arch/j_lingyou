package red.jiuzhou.langchain.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.workflow.UndoManager;
import red.jiuzhou.agent.workflow.WorkflowAuditLog;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 历史操作工具
 *
 * <p>使用 LangChain4j @Tool 注解定义历史相关工具。
 *
 * <p>包含的工具：
 * <ul>
 *   <li>getOperationHistory - 获取操作历史</li>
 *   <li>getUndoableOperations - 获取可撤销的操作</li>
 *   <li>undoLastOperation - 撤销最近一次操作</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class HistoryTools {

    private static final Logger log = LoggerFactory.getLogger(HistoryTools.class);

    private final UndoManager undoManager;
    private final WorkflowAuditLog auditLog;

    public HistoryTools() {
        this.undoManager = UndoManager.getInstance();
        this.auditLog = WorkflowAuditLog.getInstance();
    }

    public HistoryTools(UndoManager undoManager, WorkflowAuditLog auditLog) {
        this.undoManager = undoManager;
        this.auditLog = auditLog;
    }

    @Tool("获取最近的操作历史记录，包括执行的 SQL、影响行数、执行时间等。")
    public HistoryResult getOperationHistory(
            @P("返回的最大记录数，默认20") Integer limit
    ) {
        log.info("获取操作历史，限制: {}", limit);

        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 100) : 20;

        try {
            List<UndoManager.UndoableOperation> operations = undoManager.getUndoableOperations();

            List<OperationRecord> records = operations.stream()
                    .limit(effectiveLimit)
                    .map(op -> new OperationRecord(
                            op.operationId,
                            op.stepId,
                            op.getSqlType(),
                            op.getShortSql(),
                            op.affectedRows,
                            op.getFormattedTime(),
                            op.status.name()
                    ))
                    .collect(Collectors.toList());

            return HistoryResult.success(records);

        } catch (Exception e) {
            log.error("获取操作历史失败", e);
            return HistoryResult.error("获取操作历史失败: " + e.getMessage());
        }
    }

    @Tool("获取当前可撤销的操作列表。只有已执行且未被撤销的操作才能撤销。")
    public UndoableListResult getUndoableOperations() {
        log.info("获取可撤销操作列表");

        try {
            List<UndoManager.UndoableOperation> operations = undoManager.getUndoableOperations();

            List<UndoableItem> items = operations.stream()
                    .filter(op -> op.status == UndoManager.OperationStatus.EXECUTED)
                    .map(op -> new UndoableItem(
                            op.operationId,
                            op.getSqlType(),
                            op.getShortSql(),
                            op.affectedRows,
                            op.getFormattedTime()
                    ))
                    .collect(Collectors.toList());

            String description = items.isEmpty()
                    ? "没有可撤销的操作"
                    : String.format("共有 %d 个操作可撤销", items.size());

            return UndoableListResult.success(items, description);

        } catch (Exception e) {
            log.error("获取可撤销操作失败", e);
            return UndoableListResult.error("获取可撤销操作失败: " + e.getMessage());
        }
    }

    @Tool("撤销最近一次操作。只能撤销已执行的修改操作（UPDATE/DELETE）。INSERT 操作撤销功能有限。")
    public UndoResult undoLastOperation() {
        log.info("撤销最近一次操作");

        if (!undoManager.canUndo()) {
            return UndoResult.error("没有可撤销的操作");
        }

        try {
            UndoManager.UndoResult result = undoManager.undoLast();

            if (result.success) {
                return UndoResult.success(result.message, result.restoredRows);
            } else {
                return UndoResult.error(result.message);
            }

        } catch (Exception e) {
            log.error("撤销操作失败", e);
            return UndoResult.error("撤销操作失败: " + e.getMessage());
        }
    }

    @Tool("撤销指定的操作。需要提供操作ID。")
    public UndoResult undoOperation(
            @P("要撤销的操作ID") String operationId
    ) {
        log.info("撤销指定操作: {}", operationId);

        if (operationId == null || operationId.trim().isEmpty()) {
            return UndoResult.error("操作ID不能为空");
        }

        try {
            UndoManager.UndoResult result = undoManager.undoOperation(operationId);

            if (result.success) {
                return UndoResult.success(result.message, result.restoredRows);
            } else {
                return UndoResult.error(result.message);
            }

        } catch (Exception e) {
            log.error("撤销操作失败", e);
            return UndoResult.error("撤销操作失败: " + e.getMessage());
        }
    }

    @Tool("检查是否有可撤销的操作，返回最近可撤销操作的描述。")
    public String checkUndoAvailable() {
        if (!undoManager.canUndo()) {
            return "没有可撤销的操作";
        }

        String description = undoManager.getLastUndoDescription();
        return description != null ? description : "有可撤销的操作";
    }

    // ==================== 结果类型 ====================

    /**
     * 历史记录结果
     */
    public record HistoryResult(
            boolean success,
            String message,
            List<OperationRecord> records
    ) {
        public static HistoryResult success(List<OperationRecord> records) {
            return new HistoryResult(true, "获取成功，共 " + records.size() + " 条记录", records);
        }

        public static HistoryResult error(String message) {
            return new HistoryResult(false, message, Collections.emptyList());
        }
    }

    /**
     * 操作记录
     */
    public record OperationRecord(
            String operationId,
            String stepId,
            String sqlType,
            String shortSql,
            int affectedRows,
            String executedAt,
            String status
    ) {}

    /**
     * 可撤销操作列表结果
     */
    public record UndoableListResult(
            boolean success,
            String message,
            List<UndoableItem> items
    ) {
        public static UndoableListResult success(List<UndoableItem> items, String description) {
            return new UndoableListResult(true, description, items);
        }

        public static UndoableListResult error(String message) {
            return new UndoableListResult(false, message, Collections.emptyList());
        }
    }

    /**
     * 可撤销操作项
     */
    public record UndoableItem(
            String operationId,
            String sqlType,
            String shortSql,
            int affectedRows,
            String executedAt
    ) {}

    /**
     * 撤销结果
     */
    public record UndoResult(
            boolean success,
            String message,
            int restoredRows
    ) {
        public static UndoResult success(String message, int restoredRows) {
            return new UndoResult(true, message, restoredRows);
        }

        public static UndoResult error(String message) {
            return new UndoResult(false, message, 0);
        }
    }
}
