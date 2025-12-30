package red.jiuzhou.validation.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务器合规性过滤器单元测试
 *
 * @author Claude Code
 * @since 2025-12-29
 */
@DisplayName("ServerComplianceFilter 单元测试")
class ServerComplianceFilterTest {

    private ServerComplianceFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ServerComplianceFilter();
    }

    @Test
    @DisplayName("测试 items 表黑名单字段过滤")
    void testItemsBlacklistFieldsFiltering() {
        // 准备测试数据
        Map<String, Object> itemData = new LinkedHashMap<>();
        itemData.put("id", 100000001);
        itemData.put("name", "测试物品");
        itemData.put("level", 50);
        itemData.put("stack", 100);
        itemData.put("__order_index", 1);          // 应被移除
        itemData.put("drop_prob_6", 0.5);          // 应被移除
        itemData.put("drop_prob_7", 0.3);          // 应被移除
        itemData.put("drop_monster_6", 123456);    // 应被移除
        itemData.put("erect", "test");             // 应被移除
        itemData.put("monsterbook_race", "dragon"); // 应被移除

        // 应用过滤
        ServerComplianceFilter.FilterResult result = filter.filterForExport("items", itemData);

        // 验证黑名单字段被移除
        assertFalse(result.getFilteredData().containsKey("__order_index"),
                "__order_index 应该被移除");
        assertFalse(result.getFilteredData().containsKey("drop_prob_6"),
                "drop_prob_6 应该被移除");
        assertFalse(result.getFilteredData().containsKey("drop_prob_7"),
                "drop_prob_7 应该被移除");
        assertFalse(result.getFilteredData().containsKey("drop_monster_6"),
                "drop_monster_6 应该被移除");
        assertFalse(result.getFilteredData().containsKey("erect"),
                "erect 应该被移除");
        assertFalse(result.getFilteredData().containsKey("monsterbook_race"),
                "monsterbook_race 应该被移除");

        // 验证正常字段保留
        assertTrue(result.getFilteredData().containsKey("id"), "id 应该保留");
        assertTrue(result.getFilteredData().containsKey("name"), "name 应该保留");
        assertTrue(result.getFilteredData().containsKey("level"), "level 应该保留");
        assertTrue(result.getFilteredData().containsKey("stack"), "stack 应该保留");

        // 验证修改统计
        assertEquals(6, result.getRemovedFields().size(),
                "应该有6个字段被移除");
        assertTrue(result.hasChanges(), "应该有修改");
    }

    @Test
    @DisplayName("测试 skills 表黑名单字段过滤")
    void testSkillsBlacklistFieldsFiltering() {
        // 准备测试数据
        Map<String, Object> skillData = new LinkedHashMap<>();
        skillData.put("id", "FI_KneeCrash_G1");
        skillData.put("name", "膝撞");
        skillData.put("level", 10);
        skillData.put("casting_delay", 1000);
        skillData.put("__order_index", 1);           // 应被移除
        skillData.put("status_fx_slot_lv", 5);       // 应被移除
        skillData.put("toggle_id", "toggle_test");   // 应被移除
        skillData.put("is_familiar_skill", true);    // 应被移除

        // 应用过滤
        ServerComplianceFilter.FilterResult result = filter.filterForExport("skills", skillData);

        // 验证黑名单字段被移除
        assertFalse(result.getFilteredData().containsKey("__order_index"));
        assertFalse(result.getFilteredData().containsKey("status_fx_slot_lv"));
        assertFalse(result.getFilteredData().containsKey("toggle_id"));
        assertFalse(result.getFilteredData().containsKey("is_familiar_skill"));

        // 验证正常字段保留
        assertTrue(result.getFilteredData().containsKey("id"));
        assertTrue(result.getFilteredData().containsKey("name"));
        assertTrue(result.getFilteredData().containsKey("level"));

        // 验证修改统计
        assertEquals(4, result.getRemovedFields().size());
    }

    @Test
    @DisplayName("测试值域约束验证和修正")
    void testValueConstraintValidationAndCorrection() {
        // 准备测试数据（stack超出范围）
        Map<String, Object> itemData = new LinkedHashMap<>();
        itemData.put("id", 100000001);
        itemData.put("name", "测试物品");
        itemData.put("level", 50);
        itemData.put("stack", 10000);  // 超出最大值9999

        // 应用过滤
        ServerComplianceFilter.FilterResult result = filter.filterForExport("items", itemData);

        // 验证值被修正
        assertEquals(9999, result.getFilteredData().get("stack"),
                "stack 应该被修正为 9999");

        // 验证修正统计
        assertTrue(result.getCorrectedFields().size() > 0,
                "应该有字段被修正");
    }

    @Test
    @DisplayName("测试必填字段检查")
    void testRequiredFieldsValidation() {
        // 准备测试数据（缺少必填字段 name）
        Map<String, Object> itemData = new LinkedHashMap<>();
        itemData.put("id", 100000001);
        // 缺少 name 字段
        itemData.put("level", 50);

        // 应用过滤
        ServerComplianceFilter.FilterResult result = filter.filterForExport("items", itemData);

        // 验证警告
        assertTrue(result.hasWarnings(), "应该有警告");
        assertTrue(result.getWarnings().stream()
                        .anyMatch(w -> w.contains("name")),
                "警告应该提到缺少 name 字段");
    }

    @Test
    @DisplayName("测试无规则表的处理")
    void testTableWithoutRules() {
        // 准备测试数据（假设的没有规则的表）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", 1);
        data.put("field1", "value1");
        data.put("field2", "value2");

        // 应用过滤（使用不存在的表名）
        ServerComplianceFilter.FilterResult result = filter.filterForExport("unknown_table", data);

        // 验证没有修改
        assertFalse(result.hasChanges(), "没有规则的表不应该有修改");
        assertEquals(data, result.getFilteredData(),
                "数据应该完全保持原样");
    }

    @Test
    @DisplayName("测试批量过滤")
    void testBatchFiltering() {
        // 准备测试数据
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", 100000001 + i);
            item.put("name", "测试物品" + i);
            item.put("level", 50);
            item.put("__order_index", i);  // 应被移除
            itemList.add(item);
        }

        // 批量过滤
        List<ServerComplianceFilter.FilterResult> results = filter.filterBatch("items", itemList);

        // 验证结果
        assertEquals(10, results.size(), "应该有10个过滤结果");

        for (ServerComplianceFilter.FilterResult result : results) {
            assertTrue(result.hasChanges(), "每个结果都应该有修改");
            assertFalse(result.getFilteredData().containsKey("__order_index"),
                    "__order_index 应该被移除");
        }
    }

    @Test
    @DisplayName("测试 hasRules 方法")
    void testHasRulesMethod() {
        assertTrue(filter.hasRules("items"), "items 应该有规则");
        assertTrue(filter.hasRules("skills"), "skills 应该有规则");
        assertFalse(filter.hasRules("unknown_table"), "未知表不应该有规则");
    }

    @Test
    @DisplayName("测试 getRule 方法")
    void testGetRuleMethod() {
        Optional<FileValidationRule> ruleOpt = filter.getRule("items");

        assertTrue(ruleOpt.isPresent(), "items 规则应该存在");

        FileValidationRule rule = ruleOpt.get();
        assertEquals("items", rule.getTableName());
        assertEquals("items.xml", rule.getXmlFileName());
        assertTrue(rule.getBlacklistFields().contains("__order_index"));
    }

    @Test
    @DisplayName("测试过滤报告生成")
    void testFilterReportGeneration() {
        // 准备测试数据
        Map<String, Object> itemData = new LinkedHashMap<>();
        itemData.put("id", 100000001);
        itemData.put("name", "测试物品");
        itemData.put("__order_index", 1);

        // 应用过滤
        ServerComplianceFilter.FilterResult result = filter.filterForExport("items", itemData);

        // 生成报告
        String report = filter.generateFilterReport("items", result);

        // 验证报告内容
        assertNotNull(report, "报告不应该为null");
        assertTrue(report.contains("items"), "报告应该包含表名");
        assertTrue(report.contains("__order_index"), "报告应该包含移除的字段");
    }

    @Test
    @DisplayName("测试批量过滤统计报告生成")
    void testBatchFilterStatisticsGeneration() {
        // 准备测试数据
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", 100000001 + i);
            item.put("name", "测试物品" + i);
            item.put("__order_index", i);
            itemList.add(item);
        }

        // 批量过滤
        List<ServerComplianceFilter.FilterResult> results = filter.filterBatch("items", itemList);

        // 生成统计报告
        String statistics = filter.generateBatchFilterStatistics("items", results);

        // 验证统计内容
        assertNotNull(statistics, "统计报告不应该为null");
        assertTrue(statistics.contains("总记录数: 5"), "应该包含记录数");
        assertTrue(statistics.contains("__order_index"), "应该包含移除的字段统计");
    }

    @Test
    @DisplayName("测试空数据处理")
    void testEmptyDataHandling() {
        // 空数据
        Map<String, Object> emptyData = new HashMap<>();

        // 应用过滤
        ServerComplianceFilter.FilterResult result = filter.filterForExport("items", emptyData);

        // 验证
        assertFalse(result.hasChanges(), "空数据不应该有修改");
        assertTrue(result.getFilteredData().isEmpty(), "过滤后的数据也应该为空");
    }

    @Test
    @DisplayName("测试 null 数据处理")
    void testNullDataHandling() {
        // null 数据
        ServerComplianceFilter.FilterResult result = filter.filterForExport("items", null);

        // 验证
        assertFalse(result.hasChanges(), "null数据不应该有修改");
    }

    @Test
    @DisplayName("测试 XmlFileValidationRules 规则统计")
    void testXmlFileValidationRulesStatistics() {
        // 获取所有表名
        Set<String> allTables = XmlFileValidationRules.getAllTableNames();

        // 验证
        assertNotNull(allTables, "表名集合不应该为null");
        assertTrue(allTables.size() >= 18, "应该至少有18个表");
        assertTrue(allTables.contains("items"), "应该包含 items");
        assertTrue(allTables.contains("skills"), "应该包含 skills");

        // 验证总规则数
        int totalRules = XmlFileValidationRules.getTotalRuleCount();
        assertTrue(totalRules >= 138, "总规则数应该至少138条");

        // 生成规则摘要
        String summary = XmlFileValidationRules.generateRuleSummary();
        assertNotNull(summary, "规则摘要不应该为null");
        assertTrue(summary.contains("服务器合规性验证规则统计"));
    }

    @Test
    @DisplayName("测试黑名单字段统计")
    void testBlacklistFieldStatistics() {
        Map<String, Integer> stats = XmlFileValidationRules.getBlacklistFieldStatistics();

        assertNotNull(stats, "统计结果不应该为null");
        assertTrue(stats.containsKey("__order_index"), "应该包含 __order_index");

        // __order_index 应该在所有18个表中
        assertTrue(stats.get("__order_index") >= 18,
                "__order_index 应该至少在18个表中被禁用");
    }
}
