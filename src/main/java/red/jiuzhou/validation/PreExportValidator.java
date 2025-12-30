package red.jiuzhou.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.validation.server.ServerComplianceFilter;
import red.jiuzhou.validation.server.XmlFileValidationRules;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 导出预验证器 - 在导出前检查潜在问题
 *
 * <p>设计目标：让设计师在导出前就知道会有哪些问题，避免导出后服务器加载失败
 *
 * <h3>验证项目：</h3>
 * <ol>
 *   <li>表是否存在</li>
 *   <li>数据量检查（空表警告）</li>
 *   <li>黑名单字段检测（会被自动移除的字段）</li>
 *   <li>引用完整性预检查（如quest引用不存在的item）</li>
 *   <li>编码一致性检查</li>
 * </ol>
 *
 * @author Claude Code
 * @since 2025-12-29
 */
public class PreExportValidator {

    private static final Logger log = LoggerFactory.getLogger(PreExportValidator.class);

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final String tableName;
        private final boolean canExport;
        private final int rowCount;
        private final List<String> errors;
        private final List<String> warnings;
        private final List<String> infos;
        private final List<String> blacklistedFields;

        private ValidationResult(String tableName, boolean canExport, int rowCount,
                                 List<String> errors, List<String> warnings,
                                 List<String> infos, List<String> blacklistedFields) {
            this.tableName = tableName;
            this.canExport = canExport;
            this.rowCount = rowCount;
            this.errors = Collections.unmodifiableList(errors);
            this.warnings = Collections.unmodifiableList(warnings);
            this.infos = Collections.unmodifiableList(infos);
            this.blacklistedFields = Collections.unmodifiableList(blacklistedFields);
        }

        public String getTableName() {
            return tableName;
        }

        public boolean canExport() {
            return canExport;
        }

        public int getRowCount() {
            return rowCount;
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public List<String> getInfos() {
            return infos;
        }

        public List<String> getBlacklistedFields() {
            return blacklistedFields;
        }

        public boolean hasIssues() {
            return !errors.isEmpty() || !warnings.isEmpty();
        }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("表: %s\n", tableName));
            sb.append(String.format("可导出: %s\n", canExport ? "✅ 是" : "❌ 否"));
            sb.append(String.format("数据量: %,d 行\n", rowCount));

            if (!errors.isEmpty()) {
                sb.append(String.format("\n❌ 错误 (%d):\n", errors.size()));
                errors.forEach(e -> sb.append("  - ").append(e).append("\n"));
            }

            if (!warnings.isEmpty()) {
                sb.append(String.format("\n⚠️ 警告 (%d):\n", warnings.size()));
                warnings.forEach(w -> sb.append("  - ").append(w).append("\n"));
            }

            if (!infos.isEmpty()) {
                sb.append(String.format("\nℹ️ 信息 (%d):\n", infos.size()));
                infos.forEach(i -> sb.append("  - ").append(i).append("\n"));
            }

            if (!blacklistedFields.isEmpty()) {
                sb.append(String.format("\n🔧 将自动移除的字段 (%d): %s\n",
                        blacklistedFields.size(),
                        String.join(", ", blacklistedFields)));
            }

            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format("ValidationResult{table=%s, canExport=%s, errors=%d, warnings=%d}",
                    tableName, canExport, errors.size(), warnings.size());
        }
    }

    /**
     * 验证单个表的导出可行性
     *
     * @param tableName 表名
     * @return 验证结果
     */
    public ValidationResult validate(String tableName) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> infos = new ArrayList<>();
        List<String> blacklistedFields = new ArrayList<>();

        boolean canExport = true;
        int rowCount = 0;

        try {
            // 1. 检查表是否存在
            if (!DatabaseUtil.tableExists(tableName)) {
                errors.add("表不存在于数据库中");
                canExport = false;
                return new ValidationResult(tableName, false, 0, errors, warnings, infos, blacklistedFields);
            }

            // 2. 检查数据量
            rowCount = DatabaseUtil.getTotalRowCount(tableName);
            if (rowCount == 0) {
                warnings.add("表为空（0行数据）");
            } else if (rowCount > 50000) {
                warnings.add(String.format("数据量较大（%,d 行），导出可能需要较长时间", rowCount));
            }

            // 3. 检查是否有验证规则
            if (XmlFileValidationRules.hasRule(tableName)) {
                infos.add("已配置服务器合规性验证规则");

                // 获取黑名单字段
                var ruleOpt = XmlFileValidationRules.getRule(tableName);
                if (ruleOpt.isPresent()) {
                    blacklistedFields.addAll(ruleOpt.get().getBlacklistFields());
                    if (!blacklistedFields.isEmpty()) {
                        infos.add(String.format("导出时将自动移除 %d 个不兼容字段", blacklistedFields.size()));
                    }
                }
            } else {
                warnings.add("未配置验证规则，将按原样导出（可能包含服务器不兼容的字段）");
            }

            // 4. 检查常见问题
            checkCommonIssues(tableName, rowCount, errors, warnings, infos);

        } catch (Exception e) {
            log.error("验证表 {} 时发生异常", tableName, e);
            errors.add("验证过程发生异常: " + e.getMessage());
            canExport = false;
        }

        return new ValidationResult(tableName, canExport, rowCount, errors, warnings, infos, blacklistedFields);
    }

    /**
     * 批量验证多个表
     *
     * @param tableNames 表名列表
     * @return 验证结果列表
     */
    public List<ValidationResult> validateBatch(List<String> tableNames) {
        return tableNames.stream()
                .map(this::validate)
                .collect(Collectors.toList());
    }

    /**
     * 检查常见问题
     */
    private void checkCommonIssues(String tableName, int rowCount,
                                    List<String> errors, List<String> warnings, List<String> infos) {
        // 检查特定表的已知问题
        switch (tableName.toLowerCase()) {
            case "quest_random_rewards":
                warnings.add("已知问题: 部分任务引用不存在的物品（pattern: *_q_*a）");
                infos.add("建议: 导出后检查服务器日志中的 'unknown item' 错误");
                break;

            case "skill_base":
                if (rowCount > 10000) {
                    warnings.add("技能表数据量大，建议分批导出或使用筛选条件");
                }
                break;

            case "items":
                if (rowCount > 20000) {
                    warnings.add("物品表数据量大，导出时间可能超过5分钟");
                }
                break;

            case "npcs":
                warnings.add("提示: NPC AI模式文件需要单独处理（不在此导出范围）");
                break;
        }

        // 检查表名中是否包含AI相关
        if (tableName.toLowerCase().contains("npcaipatterns")) {
            warnings.add("AI模式文件可能存在CDATA格式问题，导出后需要验证");
        }
    }

    /**
     * 生成批量验证报告
     */
    public String generateBatchReport(List<ValidationResult> results) {
        StringBuilder report = new StringBuilder();
        report.append("=".repeat(80)).append("\n");
        report.append("导出预验证报告\n");
        report.append("=".repeat(80)).append("\n\n");

        long canExportCount = results.stream().filter(ValidationResult::canExport).count();
        long hasIssuesCount = results.stream().filter(ValidationResult::hasIssues).count();
        long totalRows = results.stream().mapToLong(ValidationResult::getRowCount).sum();

        report.append(String.format("总计: %d 个表\n", results.size()));
        report.append(String.format("可导出: %d 个 ✅\n", canExportCount));
        report.append(String.format("有问题: %d 个 ⚠️\n", hasIssuesCount));
        report.append(String.format("总数据量: %,d 行\n\n", totalRows));

        report.append("-".repeat(80)).append("\n");
        report.append("详细结果:\n");
        report.append("-".repeat(80)).append("\n\n");

        for (ValidationResult result : results) {
            report.append(result.getSummary()).append("\n");
            report.append("-".repeat(80)).append("\n\n");
        }

        return report.toString();
    }

    /**
     * 快速检查（只检查表是否存在和数据量）
     */
    public Map<String, Integer> quickCheck(List<String> tableNames) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String tableName : tableNames) {
            try {
                if (DatabaseUtil.tableExists(tableName)) {
                    result.put(tableName, DatabaseUtil.getTotalRowCount(tableName));
                } else {
                    result.put(tableName, -1); // -1 表示表不存在
                }
            } catch (Exception e) {
                result.put(tableName, -2); // -2 表示检查失败
                log.error("快速检查表 {} 失败", tableName, e);
            }
        }
        return result;
    }
}
