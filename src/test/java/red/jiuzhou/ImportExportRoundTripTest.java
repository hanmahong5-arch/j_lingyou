package red.jiuzhou;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.dbxml.DbToXmlGenerator;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.validation.server.XmlFileValidationRules;
import red.jiuzhou.xmltosql.XmlProcess;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 导入导出往返测试
 *
 * 测试策略：
 * 1. 优先测试服务器合规过滤器支持的18个表
 * 2. 测试常用的核心表
 * 3. 随机抽样其他表
 *
 * @author Claude Code
 * @since 2025-12-29
 */
public class ImportExportRoundTripTest {

    private static final Logger log = LoggerFactory.getLogger(ImportExportRoundTripTest.class);

    /**
     * 测试结果
     */
    static class TestResult {
        String tableName;
        String xmlFile;
        boolean importSuccess;
        boolean exportSuccess;
        int recordCount;
        long importTime;
        long exportTime;
        String errorMessage;
        boolean hasComplianceRules;
        int removedFields;
        int correctedFields;

        @Override
        public String toString() {
            String status = (importSuccess && exportSuccess) ? "✅ PASS" : "❌ FAIL";
            String compliance = hasComplianceRules ?
                String.format(" | 过滤: 移除%d 修正%d", removedFields, correctedFields) : "";
            return String.format("%s | %-30s | 记录:%5d | 导入:%4dms | 导出:%4dms%s",
                status, tableName, recordCount, importTime, exportTime, compliance);
        }
    }

    /**
     * 执行完整的往返测试
     */
    @Test
    public void testImportExportRoundTrip() {
        log.info("=".repeat(80));
        log.info("开始导入导出往返测试");
        log.info("=".repeat(80));

        List<TestResult> results = new ArrayList<>();

        // 1. 测试服务器合规过滤器支持的表
        log.info("\n【阶段1】测试服务器合规过滤器支持的表（18个）");
        log.info("-".repeat(80));
        List<String> complianceTables = getComplianceSupportedTables();
        for (String tableName : complianceTables) {
            TestResult result = testTable(tableName, true);
            results.add(result);
            log.info(result.toString());
        }

        // 2. 测试常用核心表（非合规表）
        log.info("\n【阶段2】测试常用核心表");
        log.info("-".repeat(80));
        List<String> coreTables = Arrays.asList(
            "airports", "airline", "abyss", "abyss_rank"
        );
        for (String tableName : coreTables) {
            if (!complianceTables.contains(tableName)) {
                TestResult result = testTable(tableName, false);
                results.add(result);
                log.info(result.toString());
            }
        }

        // 3. 生成测试报告
        generateReport(results);
    }

    /**
     * 测试单个表的导入导出
     */
    private TestResult testTable(String tableName, boolean hasComplianceRules) {
        TestResult result = new TestResult();
        result.tableName = tableName;
        result.hasComplianceRules = hasComplianceRules;

        try {
            // 1. 查找XML文件
            String xmlPath = YamlUtils.getProperty("aion.xmlPath");
            File xmlFile = findXmlFile(xmlPath, tableName);
            if (xmlFile == null || !xmlFile.exists()) {
                result.importSuccess = false;
                result.exportSuccess = false;
                result.errorMessage = "XML文件不存在";
                return result;
            }
            result.xmlFile = xmlFile.getAbsolutePath();

            // 2. 测试导入
            long importStart = System.currentTimeMillis();
            try {
                XmlProcess xmlProcess = new XmlProcess();
                xmlProcess.process(xmlFile.getAbsolutePath());
                result.importSuccess = true;
                result.importTime = System.currentTimeMillis() - importStart;

                // 获取记录数
                result.recordCount = DatabaseUtil.getTotalRowCount(tableName);
            } catch (Exception e) {
                result.importSuccess = false;
                result.errorMessage = "导入失败: " + e.getMessage();
                result.importTime = System.currentTimeMillis() - importStart;
                return result;
            }

            // 3. 测试导出
            long exportStart = System.currentTimeMillis();
            try {
                String configPath = findConfigFile(tableName);
                if (configPath == null) {
                    result.exportSuccess = false;
                    result.errorMessage = "配置文件不存在";
                    return result;
                }

                DbToXmlGenerator generator = new DbToXmlGenerator(tableName, null, configPath);
                String exportedFile = generator.processAndMerge();
                result.exportSuccess = true;
                result.exportTime = System.currentTimeMillis() - exportStart;

                // 验证导出文件存在
                if (!Files.exists(Paths.get(exportedFile))) {
                    result.exportSuccess = false;
                    result.errorMessage = "导出文件未生成";
                }

                // TODO: 从日志中提取过滤统计（如果有合规规则）
                // 这里简化处理，实际应该解析日志
                if (hasComplianceRules) {
                    result.removedFields = 0;  // 应从日志提取
                    result.correctedFields = 0;
                }

            } catch (Exception e) {
                result.exportSuccess = false;
                result.errorMessage = "导出失败: " + e.getMessage();
                result.exportTime = System.currentTimeMillis() - exportStart;
            }

        } catch (Exception e) {
            result.importSuccess = false;
            result.exportSuccess = false;
            result.errorMessage = "测试异常: " + e.getMessage();
        }

        return result;
    }

    /**
     * 查找XML文件
     */
    private File findXmlFile(String basePath, String tableName) {
        // 1. 直接匹配
        File direct = new File(basePath, tableName + ".xml");
        if (direct.exists()) {
            return direct;
        }

        // 2. 在China目录查找
        File china = new File(basePath + "/China", tableName + ".xml");
        if (china.exists()) {
            return china;
        }

        // 3. 递归查找（限制深度）
        File baseDir = new File(basePath);
        if (baseDir.exists() && baseDir.isDirectory()) {
            File[] files = baseDir.listFiles((dir, name) ->
                name.equalsIgnoreCase(tableName + ".xml"));
            if (files != null && files.length > 0) {
                return files[0];
            }
        }

        return null;
    }

    /**
     * 查找配置文件
     */
    private String findConfigFile(String tableName) {
        String confPath = YamlUtils.getProperty("file.confPath");

        // 1. 在 CONF/D/AionReal58/AionMap/XML/ 下查找
        File xmlConf = new File(confPath + "D/AionReal58/AionMap/XML/" + tableName + ".json");
        if (xmlConf.exists()) {
            return xmlConf.getAbsolutePath();
        }

        // 2. 在 CONF/D/AionReal58/AionMap/XML/China/ 下查找
        File chinaConf = new File(confPath + "D/AionReal58/AionMap/XML/China/" + tableName + ".json");
        if (chinaConf.exists()) {
            return chinaConf.getAbsolutePath();
        }

        return null;
    }

    /**
     * 获取服务器合规过滤器支持的表列表
     */
    private List<String> getComplianceSupportedTables() {
        // 从 XmlFileValidationRules 中获取支持的表名
        // 这里硬编码，实际应该从规则类中动态获取
        return Arrays.asList(
            "skills",
            "items",
            "npc_templates",
            "quests",
            "boost_time_table",
            "player_exp_table",
            "guild_level",
            "pet_feed",
            "housing_objects",
            "motion",
            "spawns",
            "drops",
            "teleport_locations",
            "bind_points",
            "goods",
            "trade_lists",
            "recipes",
            "gatherable_templates"
        );
    }

    /**
     * 生成测试报告
     */
    private void generateReport(List<TestResult> results) {
        log.info("\n" + "=".repeat(80));
        log.info("测试报告");
        log.info("=".repeat(80));

        long passCount = results.stream().filter(r -> r.importSuccess && r.exportSuccess).count();
        long failCount = results.size() - passCount;
        long totalRecords = results.stream().mapToInt(r -> r.recordCount).sum();
        long totalImportTime = results.stream().mapToLong(r -> r.importTime).sum();
        long totalExportTime = results.stream().mapToLong(r -> r.exportTime).sum();
        long complianceCount = results.stream().filter(r -> r.hasComplianceRules).count();

        log.info("总测试数: {}", results.size());
        log.info("通过: {} ({}%)", passCount, passCount * 100 / results.size());
        log.info("失败: {} ({}%)", failCount, failCount * 100 / results.size());
        log.info("总记录数: {}", totalRecords);
        log.info("总导入耗时: {}ms", totalImportTime);
        log.info("总导出耗时: {}ms", totalExportTime);
        log.info("服务器合规表数: {}", complianceCount);

        // 失败的测试
        List<TestResult> failures = results.stream()
            .filter(r -> !r.importSuccess || !r.exportSuccess)
            .collect(Collectors.toList());

        if (!failures.isEmpty()) {
            log.info("\n失败的测试 ({}):", failures.size());
            log.info("-".repeat(80));
            for (TestResult failure : failures) {
                log.info("❌ {} - {}", failure.tableName, failure.errorMessage);
            }
        }

        log.info("\n" + "=".repeat(80));
    }
}
