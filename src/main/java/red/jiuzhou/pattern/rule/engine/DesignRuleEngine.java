package red.jiuzhou.pattern.rule.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.pattern.rule.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 设计规则引擎
 *
 * 核心职责：
 * 1. 预览 - 展示规则将产生的影响
 * 2. 执行 - 应用规则修改数据
 * 3. 回滚 - 撤销执行的修改
 */
public class DesignRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(DesignRuleEngine.class);

    private final JdbcTemplate jdbcTemplate;
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

    /** 机制到表名的映射 */
    private static final Map<String, String> MECHANISM_TABLE_MAP = new HashMap<>();
    static {
        MECHANISM_TABLE_MAP.put("ITEM", "item_templates");
        MECHANISM_TABLE_MAP.put("NPC", "npc_templates");
        MECHANISM_TABLE_MAP.put("SKILL", "skill_data");
        MECHANISM_TABLE_MAP.put("QUEST", "quest_data");
        MECHANISM_TABLE_MAP.put("DROP", "drop_templates");
        MECHANISM_TABLE_MAP.put("RECIPE", "recipe_templates");
        MECHANISM_TABLE_MAP.put("PET", "pet_templates");
        MECHANISM_TABLE_MAP.put("INSTANCE", "instance_templates");
        MECHANISM_TABLE_MAP.put("SPAWN", "spawn_data");
        MECHANISM_TABLE_MAP.put("SHOP", "shop_list");
        // 可根据实际情况扩展
    }

    /** 执行历史（支持回滚） */
    private final LinkedList<ExecutionResult> executionHistory = new LinkedList<>();
    private static final int MAX_HISTORY_SIZE = 50;

    public DesignRuleEngine(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 预览规则效果
     *
     * @param rule 设计规则
     * @return 预览结果
     */
    public PreviewResult preview(DesignRule rule) {
        PreviewResult result = new PreviewResult();
        result.setRuleId(rule.getId());
        result.setRuleName(rule.getName());

        try {
            // 验证规则
            if (!rule.isValid()) {
                result.markFailed("规则验证失败: " + String.join(", ", rule.getValidationErrors()));
                return result;
            }

            // 获取目标表
            String tableName = resolveTableName(rule);
            if (tableName == null) {
                result.markFailed("无法确定目标表，请指定targetTable或检查targetMechanism");
                return result;
            }

            // 查询匹配的记录
            String whereClause = rule.toWhereClause();
            String selectSql = "SELECT * FROM " + tableName + " WHERE " + whereClause;
            log.debug("预览查询SQL: {}", selectSql);

            List<Map<String, Object>> matchedRecords = jdbcTemplate.queryForList(selectSql);
            result.setMatchedCount(matchedRecords.size());

            if (matchedRecords.isEmpty()) {
                result.addWarning("没有匹配的记录");
                return result;
            }

            // 计算每条记录的变更
            List<PreviewResult.RecordChange> changes = new ArrayList<>();
            Map<String, List<Double>> beforeValues = new HashMap<>();
            Map<String, List<Double>> afterValues = new HashMap<>();

            for (Map<String, Object> record : matchedRecords) {
                PreviewResult.RecordChange change = calculateChange(rule, record);
                if (change != null) {
                    changes.add(change);

                    // 收集数值统计
                    for (String field : change.getOriginalValues().keySet()) {
                        Object oldVal = change.getOriginalValues().get(field);
                        Object newVal = change.getNewValues().get(field);

                        if (oldVal instanceof Number && newVal instanceof Number) {
                            beforeValues.computeIfAbsent(field, k -> new ArrayList<>())
                                .add(((Number) oldVal).doubleValue());
                            afterValues.computeIfAbsent(field, k -> new ArrayList<>())
                                .add(((Number) newVal).doubleValue());
                        }
                    }
                }
            }

            result.setRecordChanges(changes);

            // 计算字段统计
            for (String field : beforeValues.keySet()) {
                PreviewResult.FieldChangeStats stats = calculateFieldStats(
                    field, beforeValues.get(field), afterValues.get(field));
                result.getFieldStats().put(field, stats);
            }

            // 检查潜在问题
            checkForWarnings(result, changes, rule);

        } catch (Exception e) {
            log.error("预览失败", e);
            result.markFailed(e.getMessage());
        }

        return result;
    }

    /**
     * 执行规则
     *
     * @param rule 设计规则
     * @return 执行结果
     */
    public ExecutionResult execute(DesignRule rule) {
        ExecutionResult result = new ExecutionResult(rule.getId(), rule.getName());

        try {
            // 验证规则
            if (!rule.isValid()) {
                result.markFailed("规则验证失败: " + String.join(", ", rule.getValidationErrors()));
                return result;
            }

            // 获取目标表
            String tableName = resolveTableName(rule);
            if (tableName == null) {
                result.markFailed("无法确定目标表");
                return result;
            }
            result.setTargetTable(tableName);

            // 先预览获取变更数据
            PreviewResult preview = preview(rule);
            if (!preview.isSuccess()) {
                result.markFailed("预览失败: " + preview.getErrorMessage());
                return result;
            }

            if (preview.getMatchedCount() == 0) {
                result.markComplete(0);
                return result;
            }

            // 保存回滚数据
            result.setRollbackData(preview.getRecordChanges());

            // 获取主键字段（假设为id）
            String pkField = detectPrimaryKey(tableName);

            // 执行更新
            int affectedCount = 0;
            for (PreviewResult.RecordChange change : preview.getRecordChanges()) {
                String updateSql = buildUpdateSql(tableName, pkField, change);
                result.addExecutedSql(updateSql);

                // 生成回滚SQL
                String rollbackSql = buildRollbackSql(tableName, pkField, change);
                result.addRollbackSql(rollbackSql);

                // 执行更新
                int updated = jdbcTemplate.update(updateSql);
                affectedCount += updated;
            }

            result.markComplete(affectedCount);

            // 更新规则状态
            rule.setStatus(DesignRule.RuleStatus.COMPLETED);
            rule.setLastExecutedAt(java.time.LocalDateTime.now());

            // 保存到历史
            addToHistory(result);

            log.info("规则执行完成: {} - 影响 {} 条记录", rule.getName(), affectedCount);

        } catch (Exception e) {
            log.error("规则执行失败", e);
            result.markFailed(e.getMessage());
        }

        return result;
    }

    /**
     * 回滚执行
     *
     * @param executionId 执行ID
     * @return 回滚结果
     */
    public ExecutionResult rollback(String executionId) {
        ExecutionResult original = findExecution(executionId);
        if (original == null) {
            ExecutionResult result = new ExecutionResult();
            result.markFailed("找不到执行记录: " + executionId);
            return result;
        }

        if (!original.canRollback()) {
            ExecutionResult result = new ExecutionResult();
            result.markFailed("该执行不可回滚");
            return result;
        }

        ExecutionResult result = new ExecutionResult(original.getRuleId(), "回滚: " + original.getRuleName());

        try {
            int affectedCount = 0;

            // 执行回滚SQL（逆序）
            List<String> rollbackSqls = new ArrayList<>(original.getRollbackSqls());
            Collections.reverse(rollbackSqls);

            for (String sql : rollbackSqls) {
                result.addExecutedSql(sql);
                int updated = jdbcTemplate.update(sql);
                affectedCount += updated;
            }

            result.markComplete(affectedCount);
            original.markRolledBack();

            log.info("回滚完成: {} - 恢复 {} 条记录", original.getRuleName(), affectedCount);

        } catch (Exception e) {
            log.error("回滚失败", e);
            result.markFailed(e.getMessage());
        }

        return result;
    }

    /**
     * 获取执行历史
     */
    public List<ExecutionResult> getExecutionHistory() {
        return new ArrayList<>(executionHistory);
    }

    /**
     * 获取可回滚的执行
     */
    public List<ExecutionResult> getRollbackableExecutions() {
        return executionHistory.stream()
            .filter(ExecutionResult::canRollback)
            .collect(Collectors.toList());
    }

    // ========== 私有方法 ==========

    /**
     * 解析目标表名
     */
    private String resolveTableName(DesignRule rule) {
        if (rule.getTargetTable() != null && !rule.getTargetTable().isEmpty()) {
            return rule.getTargetTable();
        }

        String mechanism = rule.getTargetMechanism().toUpperCase();
        return MECHANISM_TABLE_MAP.get(mechanism);
    }

    /**
     * 计算单条记录的变更
     */
    private PreviewResult.RecordChange calculateChange(DesignRule rule, Map<String, Object> record) {
        // 获取记录ID和名称
        Object id = record.get("id");
        if (id == null) id = record.get("ID");
        String name = getRecordName(record);

        PreviewResult.RecordChange change = new PreviewResult.RecordChange(id, name);
        change.setOriginalRecord(new HashMap<>(record));

        // 应用每个修改规则
        for (FieldModification mod : rule.getModifications()) {
            Object originalValue = record.get(mod.getFieldName());
            Object newValue = evaluator.evaluate(mod, record);

            // 只记录实际变化的字段
            if (!Objects.equals(originalValue, newValue)) {
                change.addChange(mod.getFieldName(), originalValue, newValue);
            }
        }

        // 如果没有变化，返回null
        if (change.getOriginalValues().isEmpty()) {
            return null;
        }

        return change;
    }

    /**
     * 获取记录的显示名称
     */
    private String getRecordName(Map<String, Object> record) {
        // 尝试常见的名称字段
        String[] nameFields = {"name", "name_id", "title", "desc", "description"};
        for (String field : nameFields) {
            Object value = record.get(field);
            if (value != null && !value.toString().isEmpty()) {
                return value.toString();
            }
        }
        return "(无名称)";
    }

    /**
     * 计算字段变更统计
     */
    private PreviewResult.FieldChangeStats calculateFieldStats(
            String field, List<Double> beforeValues, List<Double> afterValues) {

        PreviewResult.FieldChangeStats stats = new PreviewResult.FieldChangeStats(field);

        if (beforeValues.isEmpty()) return stats;

        // 计算变更前统计
        stats.setBeforeMin(beforeValues.stream().mapToDouble(d -> d).min().orElse(0));
        stats.setBeforeMax(beforeValues.stream().mapToDouble(d -> d).max().orElse(0));
        stats.setBeforeAvg(beforeValues.stream().mapToDouble(d -> d).average().orElse(0));

        // 计算变更后统计
        stats.setAfterMin(afterValues.stream().mapToDouble(d -> d).min().orElse(0));
        stats.setAfterMax(afterValues.stream().mapToDouble(d -> d).max().orElse(0));
        stats.setAfterAvg(afterValues.stream().mapToDouble(d -> d).average().orElse(0));

        // 计算变化
        double avgChange = stats.getAfterAvg() - stats.getBeforeAvg();
        stats.setChangeAbsolute(avgChange);

        if (stats.getBeforeAvg() != 0) {
            stats.setChangePercent(avgChange / stats.getBeforeAvg());
        }

        return stats;
    }

    /**
     * 检查潜在问题并添加警告
     */
    private void checkForWarnings(PreviewResult result, List<PreviewResult.RecordChange> changes, DesignRule rule) {
        // 检查是否有大量记录
        if (result.getMatchedCount() > 1000) {
            result.addWarning("将影响超过1000条记录，请确认操作");
        }

        // 检查数值变化是否过大
        for (PreviewResult.FieldChangeStats stats : result.getFieldStats().values()) {
            if (Math.abs(stats.getChangePercent()) > 0.5) {
                result.addWarning(String.format("字段 %s 变化超过50%%，当前变化: %.1f%%",
                    stats.getFieldName(), stats.getChangePercent() * 100));
            }
        }
    }

    /**
     * 检测主键字段
     */
    private String detectPrimaryKey(String tableName) {
        // 简化实现，可以通过INFORMATION_SCHEMA查询
        return "id";
    }

    /**
     * 构建UPDATE SQL
     */
    private String buildUpdateSql(String tableName, String pkField, PreviewResult.RecordChange change) {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ").append(tableName).append(" SET ");

        List<String> setClauses = new ArrayList<>();
        for (Map.Entry<String, Object> entry : change.getNewValues().entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            setClauses.add(field + " = " + formatValue(value));
        }
        sb.append(String.join(", ", setClauses));

        sb.append(" WHERE ").append(pkField).append(" = ").append(formatValue(change.getRecordId()));

        return sb.toString();
    }

    /**
     * 构建回滚SQL
     */
    private String buildRollbackSql(String tableName, String pkField, PreviewResult.RecordChange change) {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ").append(tableName).append(" SET ");

        List<String> setClauses = new ArrayList<>();
        for (Map.Entry<String, Object> entry : change.getOriginalValues().entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            setClauses.add(field + " = " + formatValue(value));
        }
        sb.append(String.join(", ", setClauses));

        sb.append(" WHERE ").append(pkField).append(" = ").append(formatValue(change.getRecordId()));

        return sb.toString();
    }

    /**
     * 格式化SQL值
     */
    private String formatValue(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number) return value.toString();
        if (value instanceof Boolean) return (Boolean) value ? "1" : "0";
        return "'" + value.toString().replace("'", "''") + "'";
    }

    /**
     * 添加到历史
     */
    private void addToHistory(ExecutionResult result) {
        executionHistory.addFirst(result);
        while (executionHistory.size() > MAX_HISTORY_SIZE) {
            executionHistory.removeLast();
        }
    }

    /**
     * 查找执行记录
     */
    private ExecutionResult findExecution(String executionId) {
        return executionHistory.stream()
            .filter(e -> e.getExecutionId().equals(executionId))
            .findFirst()
            .orElse(null);
    }
}
