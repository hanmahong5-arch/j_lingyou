package red.jiuzhou.validation.server;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * XML服务器合规性检查器 - 模拟服务器加载过程，检测潜在的"undefined token"错误
 *
 * <p>核心功能：
 * <ul>
 *   <li>解析导出的XML文件，检测是否包含黑名单字段</li>
 *   <li>模拟服务器XML解析器的行为，预测加载错误</li>
 *   <li>生成详细的合规性报告</li>
 *   <li>提供修复建议</li>
 * </ul>
 *
 * <p>设计目标：让设计师在部署前就知道XML文件是否会导致服务器加载失败
 *
 * @author Claude Code
 * @since 2025-12-29
 */
public class XmlServerComplianceChecker {

    private static final Logger log = LoggerFactory.getLogger(XmlServerComplianceChecker.class);

    /**
     * 检查结果
     */
    public static class CheckResult {
        private final String fileName;
        private final String tableName;
        private final boolean compliant;
        private final int totalRecords;
        private final List<String> blacklistedFieldsFound;
        private final List<FieldIssue> fieldIssues;
        private final List<String> warnings;
        private final List<String> suggestions;

        private CheckResult(String fileName, String tableName, boolean compliant,
                            int totalRecords, List<String> blacklistedFieldsFound,
                            List<FieldIssue> fieldIssues, List<String> warnings,
                            List<String> suggestions) {
            this.fileName = fileName;
            this.tableName = tableName;
            this.compliant = compliant;
            this.totalRecords = totalRecords;
            this.blacklistedFieldsFound = Collections.unmodifiableList(blacklistedFieldsFound);
            this.fieldIssues = Collections.unmodifiableList(fieldIssues);
            this.warnings = Collections.unmodifiableList(warnings);
            this.suggestions = Collections.unmodifiableList(suggestions);
        }

        public String getFileName() { return fileName; }
        public String getTableName() { return tableName; }
        public boolean isCompliant() { return compliant; }
        public int getTotalRecords() { return totalRecords; }
        public List<String> getBlacklistedFieldsFound() { return blacklistedFieldsFound; }
        public List<FieldIssue> getFieldIssues() { return fieldIssues; }
        public List<String> getWarnings() { return warnings; }
        public List<String> getSuggestions() { return suggestions; }

        public boolean hasIssues() {
            return !blacklistedFieldsFound.isEmpty() || !fieldIssues.isEmpty() || !warnings.isEmpty();
        }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=" .repeat(80)).append("\n");
            sb.append(String.format("文件: %s\n", fileName));
            sb.append(String.format("表名: %s\n", tableName));
            sb.append(String.format("服务器兼容性: %s\n", compliant ? "✅ 合规" : "❌ 不合规"));
            sb.append(String.format("记录数: %,d\n", totalRecords));
            sb.append("=" .repeat(80)).append("\n\n");

            if (!blacklistedFieldsFound.isEmpty()) {
                sb.append(String.format("❌ 发现黑名单字段 (%d个):\n", blacklistedFieldsFound.size()));
                blacklistedFieldsFound.forEach(f -> sb.append(String.format("  • %s - 会导致服务器\"undefined token\"错误\n", f)));
                sb.append("\n");
            }

            if (!fieldIssues.isEmpty()) {
                sb.append(String.format("⚠️  字段问题 (%d个):\n", fieldIssues.size()));
                fieldIssues.forEach(issue -> sb.append(String.format("  • %s: %s (样本值: %s)\n",
                    issue.fieldName, issue.issue, issue.sampleValue)));
                sb.append("\n");
            }

            if (!warnings.isEmpty()) {
                sb.append(String.format("⚠️  警告 (%d个):\n", warnings.size()));
                warnings.forEach(w -> sb.append(String.format("  • %s\n", w)));
                sb.append("\n");
            }

            if (!suggestions.isEmpty()) {
                sb.append("💡 修复建议:\n");
                suggestions.forEach(s -> sb.append(String.format("  • %s\n", s)));
            }

            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format("CheckResult{file=%s, compliant=%s, blacklisted=%d, issues=%d}",
                fileName, compliant, blacklistedFieldsFound.size(), fieldIssues.size());
        }
    }

    /**
     * 字段问题记录
     */
    public static class FieldIssue {
        private final String fieldName;
        private final String issue;
        private final String sampleValue;

        public FieldIssue(String fieldName, String issue, String sampleValue) {
            this.fieldName = fieldName;
            this.issue = issue;
            this.sampleValue = sampleValue;
        }

        public String getFieldName() { return fieldName; }
        public String getIssue() { return issue; }
        public String getSampleValue() { return sampleValue; }
    }

    /**
     * 检查单个XML文件的服务器合规性
     *
     * @param xmlFile XML文件
     * @return 检查结果
     */
    public CheckResult check(File xmlFile) {
        String fileName = xmlFile.getName();
        String tableName = fileName.replace(".xml", "");

        List<String> blacklistedFieldsFound = new ArrayList<>();
        List<FieldIssue> fieldIssues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        try {
            log.info("开始检查文件: {}", fileName);

            // 1. 解析XML文件
            SAXReader reader = new SAXReader();
            Document document = reader.read(xmlFile);
            Element root = document.getRootElement();

            if (root == null) {
                warnings.add("XML文件为空或格式错误");
                return new CheckResult(fileName, tableName, false, 0,
                    blacklistedFieldsFound, fieldIssues, warnings, suggestions);
            }

            // 2. 获取所有记录元素
            List<?> records = root.elements();
            int totalRecords = records.size();

            if (totalRecords == 0) {
                warnings.add("XML文件不包含任何记录");
                return new CheckResult(fileName, tableName, true, 0,
                    blacklistedFieldsFound, fieldIssues, warnings, suggestions);
            }

            // 3. 获取验证规则
            Optional<FileValidationRule> ruleOpt = XmlFileValidationRules.getRule(tableName);

            if (!ruleOpt.isPresent()) {
                warnings.add("未配置服务器验证规则，无法进行深度检查");
                warnings.add("该文件将按原样导出，可能包含服务器不兼容的字段");
                suggestions.add("建议在 XmlFileValidationRules 中为该表配置验证规则");
            }

            FileValidationRule rule = ruleOpt.orElse(null);

            // 4. 收集所有实际使用的字段（从第一条记录）
            Element firstRecord = (Element) records.get(0);
            Set<String> actualFields = firstRecord.attributes().stream()
                .map(attr -> ((org.dom4j.Attribute) attr).getName())
                .collect(Collectors.toSet());

            log.debug("文件 {} 包含字段: {}", fileName, actualFields);

            // 5. 检查黑名单字段
            if (rule != null) {
                Set<String> blacklistFields = rule.getBlacklistFields();
                for (String field : actualFields) {
                    if (blacklistFields.contains(field)) {
                        blacklistedFieldsFound.add(field);
                    }
                }

                if (!blacklistedFieldsFound.isEmpty()) {
                    warnings.add(String.format("包含 %d 个黑名单字段，服务器加载时会报\"undefined token\"错误",
                        blacklistedFieldsFound.size()));
                    suggestions.add("这些字段应该在导出时被ServerComplianceFilter自动过滤");
                    suggestions.add("如果仍然存在，请检查导出逻辑是否正确应用了过滤器");
                }
            }

            // 6. 检查常见问题字段
            checkCommonFieldIssues(firstRecord, actualFields, fieldIssues, warnings);

            // 7. 检查必填字段（如果有规则）
            if (rule != null && !rule.getRequiredFields().isEmpty()) {
                Set<String> requiredFields = rule.getRequiredFields();
                List<String> missingFields = requiredFields.stream()
                    .filter(f -> !actualFields.contains(f))
                    .collect(Collectors.toList());

                if (!missingFields.isEmpty()) {
                    warnings.add(String.format("缺少必填字段: %s", String.join(", ", missingFields)));
                    suggestions.add("确保数据库表包含所有必填字段");
                }
            }

            // 8. 生成最终建议
            if (blacklistedFieldsFound.isEmpty() && fieldIssues.isEmpty() && warnings.isEmpty()) {
                suggestions.add("✅ 该文件符合服务器加载要求，可以安全部署");
            }

            boolean compliant = blacklistedFieldsFound.isEmpty();

            log.info("文件 {} 检查完成: {} (记录数: {}, 黑名单字段: {})",
                fileName, compliant ? "合规" : "不合规", totalRecords, blacklistedFieldsFound.size());

            return new CheckResult(fileName, tableName, compliant, totalRecords,
                blacklistedFieldsFound, fieldIssues, warnings, suggestions);

        } catch (Exception e) {
            log.error("检查文件 {} 时发生异常", fileName, e);
            warnings.add("检查过程发生异常: " + e.getMessage());
            suggestions.add("请检查XML文件格式是否正确");

            return new CheckResult(fileName, tableName, false, 0,
                blacklistedFieldsFound, fieldIssues, warnings, suggestions);
        }
    }

    /**
     * 批量检查多个XML文件
     *
     * @param xmlFiles XML文件列表
     * @return 检查结果列表
     */
    public List<CheckResult> checkBatch(List<File> xmlFiles) {
        log.info("开始批量检查 {} 个文件", xmlFiles.size());
        return xmlFiles.stream()
            .map(this::check)
            .collect(Collectors.toList());
    }

    /**
     * 检查目录下的所有XML文件
     *
     * @param directory 目录
     * @return 检查结果列表
     */
    public List<CheckResult> checkDirectory(File directory) {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("无效的目录: " + directory.getAbsolutePath());
        }

        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));
        if (files == null || files.length == 0) {
            log.warn("目录 {} 中没有XML文件", directory.getAbsolutePath());
            return Collections.emptyList();
        }

        return checkBatch(Arrays.asList(files));
    }

    /**
     * 检查常见字段问题
     */
    private void checkCommonFieldIssues(Element record, Set<String> actualFields,
                                        List<FieldIssue> fieldIssues, List<String> warnings) {
        // 检查是否包含 __order_index（最常见的黑名单字段）
        if (actualFields.contains("__order_index")) {
            String sampleValue = record.attributeValue("__order_index");
            fieldIssues.add(new FieldIssue("__order_index",
                "服务器不识别的排序索引字段（44,324次错误）", sampleValue));
        }

        // 检查是否包含 status_fx_slot_lv（技能表常见错误）
        if (actualFields.contains("status_fx_slot_lv")) {
            String sampleValue = record.attributeValue("status_fx_slot_lv");
            fieldIssues.add(new FieldIssue("status_fx_slot_lv",
                "服务器不识别的技能状态字段（405次错误）", sampleValue));
        }

        // 检查是否包含 toggle_id（技能表常见错误）
        if (actualFields.contains("toggle_id")) {
            String sampleValue = record.attributeValue("toggle_id");
            fieldIssues.add(new FieldIssue("toggle_id",
                "服务器不识别的切换ID字段（378次错误）", sampleValue));
        }

        // 检查空值字段
        for (String field : actualFields) {
            String value = record.attributeValue(field);
            if (value != null && value.trim().isEmpty()) {
                // 空值不一定是问题，只记录警告
                // warnings.add(String.format("字段 %s 包含空值", field));
            }
        }
    }

    /**
     * 生成批量检查报告
     */
    public String generateBatchReport(List<CheckResult> results) {
        StringBuilder report = new StringBuilder();
        report.append("=" .repeat(100)).append("\n");
        report.append("服务器合规性检查报告\n");
        report.append("=" .repeat(100)).append("\n\n");

        long compliantCount = results.stream().filter(CheckResult::isCompliant).count();
        long nonCompliantCount = results.size() - compliantCount;
        long totalBlacklistedFields = results.stream()
            .mapToLong(r -> r.getBlacklistedFieldsFound().size())
            .sum();
        long totalRecords = results.stream()
            .mapToLong(CheckResult::getTotalRecords)
            .sum();

        report.append(String.format("总计: %d 个文件\n", results.size()));
        report.append(String.format("✅ 合规: %d 个\n", compliantCount));
        report.append(String.format("❌ 不合规: %d 个\n", nonCompliantCount));
        report.append(String.format("总记录数: %,d\n", totalRecords));
        report.append(String.format("黑名单字段总数: %d\n\n", totalBlacklistedFields));

        if (nonCompliantCount > 0) {
            report.append("-" .repeat(100)).append("\n");
            report.append("不合规文件详情:\n");
            report.append("-" .repeat(100)).append("\n\n");

            results.stream()
                .filter(r -> !r.isCompliant())
                .forEach(r -> {
                    report.append(r.getSummary()).append("\n");
                    report.append("-" .repeat(100)).append("\n\n");
                });
        }

        if (compliantCount > 0) {
            report.append("-" .repeat(100)).append("\n");
            report.append("合规文件列表:\n");
            report.append("-" .repeat(100)).append("\n");

            results.stream()
                .filter(CheckResult::isCompliant)
                .forEach(r -> report.append(String.format("✅ %s (%,d 条记录)\n",
                    r.getFileName(), r.getTotalRecords())));
        }

        report.append("\n" ).append("=" .repeat(100)).append("\n");
        report.append("检查完成\n");
        report.append("=" .repeat(100)).append("\n");

        return report.toString();
    }

    /**
     * 快速检查（只检查是否包含黑名单字段，不做深度分析）
     */
    public Map<String, Boolean> quickCheck(List<File> xmlFiles) {
        Map<String, Boolean> results = new LinkedHashMap<>();

        for (File xmlFile : xmlFiles) {
            try {
                CheckResult result = check(xmlFile);
                results.put(xmlFile.getName(), result.isCompliant());
            } catch (Exception e) {
                log.error("快速检查文件 {} 失败", xmlFile.getName(), e);
                results.put(xmlFile.getName(), false);
            }
        }

        return results;
    }
}
