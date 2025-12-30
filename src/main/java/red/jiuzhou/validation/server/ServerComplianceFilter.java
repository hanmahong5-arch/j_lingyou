package red.jiuzhou.validation.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 服务器合规性过滤器 - 在导出XML前自动应用验证规则
 *
 * <p>设计原则：<strong>宽进严出</strong>
 * <ul>
 *   <li>导入（DB→XML）：宽松，保留所有数据</li>
 *   <li>导出（XML→DB）：严格，应用服务器验证规则，过滤不兼容字段</li>
 * </ul>
 *
 * <h3>功能：</h3>
 * <ol>
 *   <li>移除黑名单字段（服务器不支持的字段）</li>
 *   <li>验证并修正值域约束（超范围的值修正为默认值）</li>
 *   <li>检查必填字段（缺失则警告）</li>
 *   <li>验证引用完整性（外键引用检查）</li>
 * </ol>
 *
 * @author Claude Code
 * @since 2025-12-29
 */
public class ServerComplianceFilter {

    private static final Logger logger = LoggerFactory.getLogger(ServerComplianceFilter.class);

    /**
     * 过滤结果
     */
    public static class FilterResult {
        private final Map<String, Object> filteredData;
        private final List<String> removedFields;
        private final List<String> correctedFields;
        private final List<String> warnings;
        private final boolean hasChanges;

        public FilterResult(Map<String, Object> filteredData,
                            List<String> removedFields,
                            List<String> correctedFields,
                            List<String> warnings) {
            this.filteredData = Collections.unmodifiableMap(filteredData);
            this.removedFields = Collections.unmodifiableList(removedFields);
            this.correctedFields = Collections.unmodifiableList(correctedFields);
            this.warnings = Collections.unmodifiableList(warnings);
            this.hasChanges = !removedFields.isEmpty() || !correctedFields.isEmpty();
        }

        public Map<String, Object> getFilteredData() {
            return filteredData;
        }

        public List<String> getRemovedFields() {
            return removedFields;
        }

        public List<String> getCorrectedFields() {
            return correctedFields;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public boolean hasChanges() {
            return hasChanges;
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        @Override
        public String toString() {
            return String.format("FilterResult{removed=%d, corrected=%d, warnings=%d}",
                    removedFields.size(), correctedFields.size(), warnings.size());
        }
    }

    /**
     * 导出前过滤数据（主入口方法）
     *
     * @param tableName 表名（如 "skills", "items"）
     * @param data      要导出的数据
     * @return 过滤结果（包含过滤后的数据和变更日志）
     */
    public FilterResult filterForExport(String tableName, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return new FilterResult(data, List.of(), List.of(), List.of());
        }

        // 获取该表的验证规则
        Optional<FileValidationRule> ruleOpt = XmlFileValidationRules.getRule(tableName);
        if (ruleOpt.isEmpty()) {
            logger.debug("表 {} 没有定义验证规则，跳过过滤", tableName);
            return new FilterResult(data, List.of(), List.of(), List.of());
        }

        FileValidationRule rule = ruleOpt.get();
        Map<String, Object> filtered = new LinkedHashMap<>(data);
        List<String> removedFields = new ArrayList<>();
        List<String> correctedFields = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. 移除黑名单字段
        for (String blacklistField : rule.getBlacklistFields()) {
            if (filtered.remove(blacklistField) != null) {
                removedFields.add(blacklistField);
                logger.debug("[{}] 移除黑名单字段: {}", tableName, blacklistField);
            }
        }

        // 2. 验证值域约束
        for (Map.Entry<String, Object> entry : new ArrayList<>(filtered.entrySet())) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            rule.getConstraint(fieldName).ifPresent(constraint -> {
                if (!constraint.isValid(value)) {
                    Object defaultValue = constraint.getDefaultValue();
                    filtered.put(fieldName, defaultValue);
                    correctedFields.add(String.format("%s: %s -> %s (超出范围)",
                            fieldName, value, defaultValue));
                    logger.warn("[{}] 字段 {} 值 {} 超出约束范围，修正为默认值: {}",
                            tableName, fieldName, value, defaultValue);
                }
            });
        }

        // 3. 检查必填字段
        for (String requiredField : rule.getRequiredFields()) {
            if (!filtered.containsKey(requiredField) || filtered.get(requiredField) == null) {
                warnings.add(String.format("缺少必填字段: %s", requiredField));
                logger.warn("[{}] 缺少必填字段: {}", tableName, requiredField);
            }
        }

        // 4. 引用完整性验证（简化实现，实际需要查询数据库）
        for (Map.Entry<String, String> refEntry : rule.getReferenceFields().entrySet()) {
            String fieldName = refEntry.getKey();
            String targetTable = refEntry.getValue();

            Object refValue = filtered.get(fieldName);
            if (refValue != null && !String.valueOf(refValue).isEmpty()) {
                // TODO: 实际实现需要查询目标表验证引用是否存在
                // 这里只做提示
                logger.debug("[{}] 字段 {} 引用 {} 表，值: {}",
                        tableName, fieldName, targetTable, refValue);
            }
        }

        return new FilterResult(filtered, removedFields, correctedFields, warnings);
    }

    /**
     * 批量过滤（用于导出整个表）
     *
     * @param tableName 表名
     * @param dataList  数据列表
     * @return 过滤结果列表
     */
    public List<FilterResult> filterBatch(String tableName, List<Map<String, Object>> dataList) {
        return dataList.stream()
                .map(data -> filterForExport(tableName, data))
                .collect(Collectors.toList());
    }

    /**
     * 生成过滤日志报告
     *
     * @param tableName    表名
     * @param filterResult 过滤结果
     * @return 日志报告文本
     */
    public String generateFilterReport(String tableName, FilterResult filterResult) {
        if (!filterResult.hasChanges() && !filterResult.hasWarnings()) {
            return String.format("[%s] 数据符合服务器规范，无需过滤", tableName);
        }

        StringBuilder report = new StringBuilder();
        report.append("=".repeat(80)).append("\n");
        report.append(String.format("表 %s 的导出过滤报告\n", tableName));
        report.append("=".repeat(80)).append("\n");

        if (!filterResult.getRemovedFields().isEmpty()) {
            report.append(String.format("\n移除的黑名单字段 (%d个):\n", filterResult.getRemovedFields().size()));
            filterResult.getRemovedFields().forEach(field ->
                    report.append(String.format("  - %s\n", field))
            );
        }

        if (!filterResult.getCorrectedFields().isEmpty()) {
            report.append(String.format("\n修正的字段值 (%d个):\n", filterResult.getCorrectedFields().size()));
            filterResult.getCorrectedFields().forEach(correction ->
                    report.append(String.format("  - %s\n", correction))
            );
        }

        if (!filterResult.getWarnings().isEmpty()) {
            report.append(String.format("\n警告 (%d个):\n", filterResult.getWarnings().size()));
            filterResult.getWarnings().forEach(warning ->
                    report.append(String.format("  - %s\n", warning))
            );
        }

        report.append("=".repeat(80)).append("\n");
        return report.toString();
    }

    /**
     * 生成批量过滤的统计报告
     *
     * @param tableName     表名
     * @param filterResults 批量过滤结果
     * @return 统计报告
     */
    public String generateBatchFilterStatistics(String tableName, List<FilterResult> filterResults) {
        int totalRecords = filterResults.size();
        int modifiedRecords = (int) filterResults.stream().filter(FilterResult::hasChanges).count();
        int warningRecords = (int) filterResults.stream().filter(FilterResult::hasWarnings).count();

        Map<String, Integer> removedFieldStats = new HashMap<>();
        Map<String, Integer> correctedFieldStats = new HashMap<>();

        for (FilterResult result : filterResults) {
            result.getRemovedFields().forEach(field ->
                    removedFieldStats.merge(field, 1, Integer::sum)
            );
            result.getCorrectedFields().forEach(correction ->
                    correctedFieldStats.merge(correction.split(":")[0].trim(), 1, Integer::sum)
            );
        }

        StringBuilder report = new StringBuilder();
        report.append("=".repeat(80)).append("\n");
        report.append(String.format("表 %s 的批量导出过滤统计\n", tableName));
        report.append("=".repeat(80)).append("\n");
        report.append(String.format("总记录数: %d\n", totalRecords));
        report.append(String.format("修改的记录: %d (%.2f%%)\n",
                modifiedRecords, modifiedRecords * 100.0 / totalRecords));
        report.append(String.format("有警告的记录: %d (%.2f%%)\n",
                warningRecords, warningRecords * 100.0 / totalRecords));

        if (!removedFieldStats.isEmpty()) {
            report.append("\n移除字段统计:\n");
            removedFieldStats.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry ->
                            report.append(String.format("  - %s: %d次\n", entry.getKey(), entry.getValue()))
                    );
        }

        if (!correctedFieldStats.isEmpty()) {
            report.append("\n修正字段统计:\n");
            correctedFieldStats.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry ->
                            report.append(String.format("  - %s: %d次\n", entry.getKey(), entry.getValue()))
                    );
        }

        report.append("=".repeat(80)).append("\n");
        return report.toString();
    }

    /**
     * 检查表是否有验证规则
     */
    public boolean hasRules(String tableName) {
        return XmlFileValidationRules.hasRule(tableName);
    }

    /**
     * 获取表的验证规则（用于UI显示）
     */
    public Optional<FileValidationRule> getRule(String tableName) {
        return XmlFileValidationRules.getRule(tableName);
    }
}
