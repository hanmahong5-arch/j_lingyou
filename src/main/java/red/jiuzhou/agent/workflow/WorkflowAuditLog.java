package red.jiuzhou.agent.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工作流审计日志系统
 *
 * <p>提供完整的操作追溯能力：
 * <ul>
 *   <li>工作流级别日志（开始、完成、取消、失败）</li>
 *   <li>步骤级别日志（执行、确认、修正、跳过）</li>
 *   <li>数据变更记录（修改前后的值）</li>
 *   <li>用户操作记录（确认、修正内容）</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class WorkflowAuditLog {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAuditLog.class);

    // 单例
    private static WorkflowAuditLog instance;

    // 内存中的日志存储（按工作流ID索引）
    private final Map<String, WorkflowLogEntry> workflowLogs = new ConcurrentHashMap<>();

    // 最近的工作流历史（用于快速访问）
    private final List<String> recentWorkflowIds = new CopyOnWriteArrayList<>();

    // 最大保留历史数量
    private static final int MAX_HISTORY = 100;

    // 数据库持久化（可选）
    private JdbcTemplate jdbcTemplate;

    // 日期格式
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private WorkflowAuditLog() {}

    public static synchronized WorkflowAuditLog getInstance() {
        if (instance == null) {
            instance = new WorkflowAuditLog();
        }
        return instance;
    }

    /**
     * 设置数据库连接（用于持久化）
     */
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
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS workflow_audit_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    workflow_id VARCHAR(50) NOT NULL,
                    workflow_type VARCHAR(30),
                    step_id VARCHAR(50),
                    step_name VARCHAR(100),
                    event_type VARCHAR(30) NOT NULL,
                    event_detail TEXT,
                    user_input TEXT,
                    sql_executed TEXT,
                    affected_rows INT DEFAULT 0,
                    data_snapshot_id VARCHAR(50),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_workflow_id (workflow_id),
                    INDEX idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
            log.info("工作流审计日志表初始化完成");
        } catch (Exception e) {
            log.warn("创建审计日志表失败（可能已存在）: {}", e.getMessage());
        }
    }

    // ==================== 工作流级别日志 ====================

    /**
     * 记录工作流开始
     */
    public void logWorkflowStarted(WorkflowState state) {
        WorkflowLogEntry entry = new WorkflowLogEntry(state.getWorkflowId());
        entry.workflowType = state.getWorkflowType();
        entry.userIntent = state.getUserIntent();
        entry.startTime = state.getStartTime();
        entry.status = WorkflowLogStatus.RUNNING;

        // 记录步骤列表
        for (WorkflowStep step : state.getSteps()) {
            entry.stepLogs.add(new StepLogEntry(step.id(), step.name(), step.type().name()));
        }

        workflowLogs.put(state.getWorkflowId(), entry);
        addToRecentHistory(state.getWorkflowId());

        logEvent(state.getWorkflowId(), null, EventType.WORKFLOW_STARTED,
                "工作流开始: " + state.getWorkflowType(), null, null, 0);

        log.info("审计日志: 工作流开始 [{}] 类型={}", state.getWorkflowId(), state.getWorkflowType());
    }

    /**
     * 记录工作流完成
     */
    public void logWorkflowCompleted(WorkflowState state) {
        WorkflowLogEntry entry = workflowLogs.get(state.getWorkflowId());
        if (entry != null) {
            entry.endTime = state.getEndTime();
            entry.status = WorkflowLogStatus.COMPLETED;
            entry.totalAffectedRows = state.getTotalAffectedRows();
        }

        logEvent(state.getWorkflowId(), null, EventType.WORKFLOW_COMPLETED,
                "工作流完成，影响行数: " + state.getTotalAffectedRows(), null, null, state.getTotalAffectedRows());

        log.info("审计日志: 工作流完成 [{}] 影响行数={}", state.getWorkflowId(), state.getTotalAffectedRows());
    }

    /**
     * 记录工作流取消
     */
    public void logWorkflowCancelled(String workflowId, String reason) {
        WorkflowLogEntry entry = workflowLogs.get(workflowId);
        if (entry != null) {
            entry.endTime = Instant.now();
            entry.status = WorkflowLogStatus.CANCELLED;
            entry.cancellationReason = reason;
        }

        logEvent(workflowId, null, EventType.WORKFLOW_CANCELLED,
                "工作流取消: " + (reason != null ? reason : "用户取消"), null, null, 0);

        log.info("审计日志: 工作流取消 [{}] 原因={}", workflowId, reason);
    }

    /**
     * 记录工作流失败
     */
    public void logWorkflowFailed(String workflowId, String stepId, Throwable error) {
        WorkflowLogEntry entry = workflowLogs.get(workflowId);
        if (entry != null) {
            entry.endTime = Instant.now();
            entry.status = WorkflowLogStatus.FAILED;
            entry.errorMessage = error.getMessage();
            entry.failedStepId = stepId;
        }

        logEvent(workflowId, stepId, EventType.WORKFLOW_FAILED,
                "工作流失败: " + error.getMessage(), null, null, 0);

        log.error("审计日志: 工作流失败 [{}] 步骤={} 错误={}", workflowId, stepId, error.getMessage());
    }

    // ==================== 步骤级别日志 ====================

    /**
     * 记录步骤开始
     */
    public void logStepStarted(String workflowId, WorkflowStep step) {
        WorkflowLogEntry entry = workflowLogs.get(workflowId);
        if (entry != null) {
            StepLogEntry stepLog = findStepLog(entry, step.id());
            if (stepLog != null) {
                stepLog.startTime = Instant.now();
                stepLog.status = StepLogStatus.RUNNING;
            }
        }

        logEvent(workflowId, step.id(), EventType.STEP_STARTED,
                "步骤开始: " + step.name(), null, null, 0);
    }

    /**
     * 记录步骤结果就绪
     */
    public void logStepResultReady(String workflowId, WorkflowStep step, WorkflowStepResult result) {
        WorkflowLogEntry entry = workflowLogs.get(workflowId);
        if (entry != null) {
            StepLogEntry stepLog = findStepLog(entry, step.id());
            if (stepLog != null) {
                stepLog.status = StepLogStatus.PENDING_CONFIRMATION;
                stepLog.generatedSql = result.getGeneratedSql();
                stepLog.resultSummary = result.getSummary();
                stepLog.affectedRows = result.getTotalRows();
            }
        }

        String detail = String.format("步骤结果就绪: %s, 数据行数=%d",
                result.getSummary(), result.getTotalRows());
        logEvent(workflowId, step.id(), EventType.STEP_RESULT_READY,
                detail, null, result.getGeneratedSql(), result.getTotalRows());
    }

    /**
     * 记录步骤确认
     */
    public void logStepConfirmed(String workflowId, WorkflowStep step) {
        WorkflowLogEntry entry = workflowLogs.get(workflowId);
        if (entry != null) {
            StepLogEntry stepLog = findStepLog(entry, step.id());
            if (stepLog != null) {
                stepLog.endTime = Instant.now();
                stepLog.status = StepLogStatus.CONFIRMED;
            }
        }

        logEvent(workflowId, step.id(), EventType.STEP_CONFIRMED,
                "步骤已确认: " + step.name(), null, null, 0);

        log.info("审计日志: 步骤确认 [{}] {}", workflowId, step.name());
    }

    /**
     * 记录步骤修正
     */
    public void logStepCorrected(String workflowId, WorkflowStep step, String correction) {
        WorkflowLogEntry entry = workflowLogs.get(workflowId);
        if (entry != null) {
            StepLogEntry stepLog = findStepLog(entry, step.id());
            if (stepLog != null) {
                stepLog.corrections.add(new CorrectionEntry(correction, Instant.now()));
                stepLog.status = StepLogStatus.CORRECTED;
            }
        }

        logEvent(workflowId, step.id(), EventType.STEP_CORRECTED,
                "步骤修正: " + step.name(), correction, null, 0);

        log.info("审计日志: 步骤修正 [{}] {} 修正内容={}", workflowId, step.name(), correction);
    }

    /**
     * 记录步骤跳过
     */
    public void logStepSkipped(String workflowId, WorkflowStep step) {
        WorkflowLogEntry entry = workflowLogs.get(workflowId);
        if (entry != null) {
            StepLogEntry stepLog = findStepLog(entry, step.id());
            if (stepLog != null) {
                stepLog.endTime = Instant.now();
                stepLog.status = StepLogStatus.SKIPPED;
            }
        }

        logEvent(workflowId, step.id(), EventType.STEP_SKIPPED,
                "步骤跳过: " + step.name(), null, null, 0);

        log.info("审计日志: 步骤跳过 [{}] {}", workflowId, step.name());
    }

    /**
     * 记录步骤回退
     */
    public void logStepRolledBack(String workflowId, String stepId, String reason) {
        logEvent(workflowId, stepId, EventType.STEP_ROLLED_BACK,
                "步骤回退: " + reason, null, null, 0);

        log.info("审计日志: 步骤回退 [{}] {} 原因={}", workflowId, stepId, reason);
    }

    // ==================== 数据变更日志 ====================

    /**
     * 记录SQL执行
     */
    public void logSqlExecuted(String workflowId, String stepId, String sql, int affectedRows, String snapshotId) {
        WorkflowLogEntry entry = workflowLogs.get(workflowId);
        if (entry != null) {
            StepLogEntry stepLog = findStepLog(entry, stepId);
            if (stepLog != null) {
                stepLog.executedSql = sql;
                stepLog.affectedRows = affectedRows;
                stepLog.snapshotId = snapshotId;
            }
        }

        logEvent(workflowId, stepId, EventType.SQL_EXECUTED,
                "SQL执行完成，影响行数: " + affectedRows, null, sql, affectedRows);

        log.info("审计日志: SQL执行 [{}] 影响行数={} 快照ID={}", workflowId, affectedRows, snapshotId);
    }

    /**
     * 记录数据回滚
     */
    public void logDataRolledBack(String workflowId, String stepId, String snapshotId, int restoredRows) {
        logEvent(workflowId, stepId, EventType.DATA_ROLLED_BACK,
                String.format("数据回滚完成，恢复行数: %d, 快照ID: %s", restoredRows, snapshotId),
                null, null, restoredRows);

        log.info("审计日志: 数据回滚 [{}] 恢复行数={} 快照ID={}", workflowId, restoredRows, snapshotId);
    }

    // ==================== 查询方法 ====================

    /**
     * 获取工作流日志
     */
    public WorkflowLogEntry getWorkflowLog(String workflowId) {
        return workflowLogs.get(workflowId);
    }

    /**
     * 获取最近的工作流历史
     */
    public List<WorkflowLogEntry> getRecentWorkflows(int limit) {
        List<WorkflowLogEntry> result = new ArrayList<>();
        int count = 0;
        for (int i = recentWorkflowIds.size() - 1; i >= 0 && count < limit; i--) {
            WorkflowLogEntry entry = workflowLogs.get(recentWorkflowIds.get(i));
            if (entry != null) {
                result.add(entry);
                count++;
            }
        }
        return result;
    }

    /**
     * 获取工作流的详细时间线
     */
    public List<TimelineEvent> getWorkflowTimeline(String workflowId) {
        List<TimelineEvent> timeline = new ArrayList<>();

        WorkflowLogEntry entry = workflowLogs.get(workflowId);
        if (entry == null) return timeline;

        // 添加工作流开始事件
        timeline.add(new TimelineEvent(
                entry.startTime,
                EventType.WORKFLOW_STARTED,
                "工作流开始",
                "类型: " + entry.workflowType + ", 意图: " + entry.userIntent
        ));

        // 添加步骤事件
        for (StepLogEntry stepLog : entry.stepLogs) {
            if (stepLog.startTime != null) {
                timeline.add(new TimelineEvent(
                        stepLog.startTime,
                        EventType.STEP_STARTED,
                        stepLog.stepName,
                        "步骤开始执行"
                ));
            }

            // 添加修正事件
            for (CorrectionEntry correction : stepLog.corrections) {
                timeline.add(new TimelineEvent(
                        correction.timestamp,
                        EventType.STEP_CORRECTED,
                        stepLog.stepName + " - 用户修正",
                        correction.content
                ));
            }

            if (stepLog.endTime != null) {
                String statusDesc = switch (stepLog.status) {
                    case CONFIRMED -> "步骤已确认";
                    case SKIPPED -> "步骤已跳过";
                    case FAILED -> "步骤执行失败";
                    default -> "步骤完成";
                };
                timeline.add(new TimelineEvent(
                        stepLog.endTime,
                        stepLog.status == StepLogStatus.CONFIRMED ? EventType.STEP_CONFIRMED :
                                stepLog.status == StepLogStatus.SKIPPED ? EventType.STEP_SKIPPED :
                                        EventType.STEP_STARTED,
                        stepLog.stepName,
                        statusDesc + (stepLog.affectedRows > 0 ? ", 影响行数: " + stepLog.affectedRows : "")
                ));
            }
        }

        // 添加工作流结束事件
        if (entry.endTime != null) {
            EventType endType = switch (entry.status) {
                case COMPLETED -> EventType.WORKFLOW_COMPLETED;
                case CANCELLED -> EventType.WORKFLOW_CANCELLED;
                case FAILED -> EventType.WORKFLOW_FAILED;
                default -> EventType.WORKFLOW_COMPLETED;
            };

            String endDetail = switch (entry.status) {
                case COMPLETED -> "总影响行数: " + entry.totalAffectedRows;
                case CANCELLED -> "取消原因: " + entry.cancellationReason;
                case FAILED -> "错误: " + entry.errorMessage;
                default -> "";
            };

            timeline.add(new TimelineEvent(
                    entry.endTime,
                    endType,
                    "工作流结束",
                    endDetail
            ));
        }

        // 按时间排序
        timeline.sort(Comparator.comparing(e -> e.timestamp));

        return timeline;
    }

    // ==================== 辅助方法 ====================

    private void addToRecentHistory(String workflowId) {
        recentWorkflowIds.add(workflowId);
        // 超出限制时移除最旧的
        while (recentWorkflowIds.size() > MAX_HISTORY) {
            String oldId = recentWorkflowIds.remove(0);
            workflowLogs.remove(oldId);
        }
    }

    private StepLogEntry findStepLog(WorkflowLogEntry entry, String stepId) {
        for (StepLogEntry stepLog : entry.stepLogs) {
            if (stepLog.stepId.equals(stepId)) {
                return stepLog;
            }
        }
        return null;
    }

    private void logEvent(String workflowId, String stepId, EventType eventType,
                          String detail, String userInput, String sql, int affectedRows) {
        // 持久化到数据库（如果配置了）
        if (jdbcTemplate != null) {
            try {
                WorkflowLogEntry entry = workflowLogs.get(workflowId);
                StepLogEntry stepLog = entry != null && stepId != null ? findStepLog(entry, stepId) : null;

                jdbcTemplate.update("""
                    INSERT INTO workflow_audit_log
                    (workflow_id, workflow_type, step_id, step_name, event_type, event_detail,
                     user_input, sql_executed, affected_rows, data_snapshot_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                        workflowId,
                        entry != null ? entry.workflowType : null,
                        stepId,
                        stepLog != null ? stepLog.stepName : null,
                        eventType.name(),
                        detail,
                        userInput,
                        sql,
                        affectedRows,
                        stepLog != null ? stepLog.snapshotId : null
                );
            } catch (Exception e) {
                log.warn("持久化审计日志失败: {}", e.getMessage());
            }
        }
    }

    // ==================== 内部类型定义 ====================

    public enum EventType {
        WORKFLOW_STARTED,
        WORKFLOW_COMPLETED,
        WORKFLOW_CANCELLED,
        WORKFLOW_FAILED,
        STEP_STARTED,
        STEP_RESULT_READY,
        STEP_CONFIRMED,
        STEP_CORRECTED,
        STEP_SKIPPED,
        STEP_ROLLED_BACK,
        SQL_EXECUTED,
        DATA_ROLLED_BACK
    }

    public enum WorkflowLogStatus {
        RUNNING, COMPLETED, CANCELLED, FAILED
    }

    public enum StepLogStatus {
        PENDING, RUNNING, PENDING_CONFIRMATION, CONFIRMED, CORRECTED, SKIPPED, FAILED
    }

    /**
     * 工作流日志条目
     */
    public static class WorkflowLogEntry {
        public final String workflowId;
        public String workflowType;
        public String userIntent;
        public Instant startTime;
        public Instant endTime;
        public WorkflowLogStatus status = WorkflowLogStatus.RUNNING;
        public int totalAffectedRows;
        public String cancellationReason;
        public String errorMessage;
        public String failedStepId;
        public final List<StepLogEntry> stepLogs = new ArrayList<>();

        public WorkflowLogEntry(String workflowId) {
            this.workflowId = workflowId;
        }

        public String getFormattedDuration() {
            if (startTime == null) return "-";
            Instant end = endTime != null ? endTime : Instant.now();
            long seconds = java.time.Duration.between(startTime, end).getSeconds();
            if (seconds < 60) return seconds + "秒";
            if (seconds < 3600) return (seconds / 60) + "分" + (seconds % 60) + "秒";
            return (seconds / 3600) + "小时" + ((seconds % 3600) / 60) + "分";
        }

        public String getStatusDisplay() {
            return switch (status) {
                case RUNNING -> "运行中";
                case COMPLETED -> "已完成";
                case CANCELLED -> "已取消";
                case FAILED -> "失败";
            };
        }
    }

    /**
     * 步骤日志条目
     */
    public static class StepLogEntry {
        public final String stepId;
        public final String stepName;
        public final String stepType;
        public Instant startTime;
        public Instant endTime;
        public StepLogStatus status = StepLogStatus.PENDING;
        public String generatedSql;
        public String executedSql;
        public String resultSummary;
        public int affectedRows;
        public String snapshotId;
        public final List<CorrectionEntry> corrections = new ArrayList<>();

        public StepLogEntry(String stepId, String stepName, String stepType) {
            this.stepId = stepId;
            this.stepName = stepName;
            this.stepType = stepType;
        }
    }

    /**
     * 修正记录
     */
    public static class CorrectionEntry {
        public final String content;
        public final Instant timestamp;

        public CorrectionEntry(String content, Instant timestamp) {
            this.content = content;
            this.timestamp = timestamp;
        }
    }

    /**
     * 时间线事件
     */
    public static class TimelineEvent {
        public final Instant timestamp;
        public final EventType type;
        public final String title;
        public final String detail;

        public TimelineEvent(Instant timestamp, EventType type, String title, String detail) {
            this.timestamp = timestamp;
            this.type = type;
            this.title = title;
            this.detail = detail;
        }

        public String getFormattedTime() {
            return LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()).format(TIME_FORMAT);
        }

        public String getTypeIcon() {
            return switch (type) {
                case WORKFLOW_STARTED -> "\u25B6\uFE0F"; // ▶️
                case WORKFLOW_COMPLETED -> "\u2705"; // ✅
                case WORKFLOW_CANCELLED -> "\u26D4"; // ⛔
                case WORKFLOW_FAILED -> "\u274C"; // ❌
                case STEP_STARTED -> "\u23F3"; // ⏳
                case STEP_RESULT_READY -> "\uD83D\uDCCB"; // 📋
                case STEP_CONFIRMED -> "\u2714\uFE0F"; // ✔️
                case STEP_CORRECTED -> "\u270F\uFE0F"; // ✏️
                case STEP_SKIPPED -> "\u23ED\uFE0F"; // ⏭️
                case STEP_ROLLED_BACK -> "\u21A9\uFE0F"; // ↩️
                case SQL_EXECUTED -> "\u26A1"; // ⚡
                case DATA_ROLLED_BACK -> "\uD83D\uDD04"; // 🔄
            };
        }
    }
}
