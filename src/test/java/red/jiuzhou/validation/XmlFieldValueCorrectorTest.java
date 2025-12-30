package red.jiuzhou.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XmlFieldValueCorrector 综合测试
 *
 * <p>测试字段值自动修正功能，确保导出的XML符合服务器要求
 *
 * <p>基于服务器日志分析的错误模式：
 * <ul>
 *   <li>MainServer: 100,698 行错误日志</li>
 *   <li>NPCServer: 105,654 行错误日志</li>
 * </ul>
 */
public class XmlFieldValueCorrectorTest {

    @BeforeEach
    public void setUp() {
        // 每次测试前重置统计信息
        XmlFieldValueCorrector.resetStatistics();
    }

    // ==================== 技能字段修正测试 ====================

    @Test
    public void testCorrectTargetFlyingRestriction() {
        // 错误模式: invalid SkillFlyingRestriction(target_flying_restriction) :  "0"
        // 修正: 0 → 1
        String result = XmlFieldValueCorrector.correctValue(
                "skill_base", "target_flying_restriction", "0"
        );
        assertEquals("1", result, "target_flying_restriction=0 应该修正为 1");

        // 有效值不应被修正
        String validResult = XmlFieldValueCorrector.correctValue(
                "skill_base", "target_flying_restriction", "2"
        );
        assertEquals("2", validResult, "有效值不应被修正");
    }

    @Test
    public void testCorrectTargetMaxcount() {
        // 错误模式: Target_MaxCount : invalid value 0 must be (1..120)
        // 修正: 0 → 1
        String resultZero = XmlFieldValueCorrector.correctValue(
                "skill_base", "target_maxcount", "0"
        );
        assertEquals("1", resultZero, "target_maxcount=0 应该修正为 1");

        // 修正: >120 → 120
        String resultOver = XmlFieldValueCorrector.correctValue(
                "skill_base", "target_maxcount", "150"
        );
        assertEquals("120", resultOver, "target_maxcount>120 应该修正为 120");

        // 有效值（范围内）不应被修正
        String validResult = XmlFieldValueCorrector.correctValue(
                "skill_base", "target_maxcount", "50"
        );
        assertEquals("50", validResult, "有效值不应被修正");
    }

    @Test
    public void testCorrectPenaltyTimeSucc() {
        // 错误模式: penalty_time_succ : invalid value  0
        // 修正: 0 → 1
        String result = XmlFieldValueCorrector.correctValue(
                "skill_base", "penalty_time_succ", "0"
        );
        assertEquals("1", result, "penalty_time_succ=0 应该修正为 1");

        // 有效值不应被修正
        String validResult = XmlFieldValueCorrector.correctValue(
                "skill_base", "penalty_time_succ", "100"
        );
        assertEquals("100", validResult, "有效值不应被修正");
    }

    @Test
    public void testCorrectMaxBurstSignetLevel() {
        // 错误模式: invalid maxBurstSignetLevel:0
        // 修正: 0 → 1（两种字段名格式）
        String result1 = XmlFieldValueCorrector.correctValue(
                "skill_base", "maxBurstSignetLevel", "0"
        );
        assertEquals("1", result1, "maxBurstSignetLevel=0 应该修正为 1");

        String result2 = XmlFieldValueCorrector.correctValue(
                "skill_base", "max_burst_signet_level", "0"
        );
        assertEquals("1", result2, "max_burst_signet_level=0 应该修正为 1");

        // 有效值不应被修正
        String validResult = XmlFieldValueCorrector.correctValue(
                "skill_base", "maxBurstSignetLevel", "5"
        );
        assertEquals("5", validResult, "有效值不应被修正");
    }

    @Test
    public void testCorrectCastingDelay() {
        // 错误模式: casting_delay, too invalid number 0
        // 修正: 0 → 100
        String resultZero = XmlFieldValueCorrector.correctValue(
                "skill_base", "casting_delay", "0"
        );
        assertEquals("100", resultZero, "casting_delay=0 应该修正为 100");

        // 修正: >=60000 → 59999
        String resultMax = XmlFieldValueCorrector.correctValue(
                "skill_base", "casting_delay", "60000"
        );
        assertEquals("59999", resultMax, "casting_delay>=60000 应该修正为 59999");

        String resultOver = XmlFieldValueCorrector.correctValue(
                "skill_base", "casting_delay", "100000"
        );
        assertEquals("59999", resultOver, "casting_delay>60000 应该修正为 59999");

        // 有效值（100-59999）不应被修正
        String validResult = XmlFieldValueCorrector.correctValue(
                "skill_base", "casting_delay", "5000"
        );
        assertEquals("5000", validResult, "有效值不应被修正");
    }

    @Test
    public void testCorrectCostParameter() {
        // 错误模式: 服务器不支持 cost_parameter='DP'
        // 修正: DP → HP
        String result = XmlFieldValueCorrector.correctValue(
                "skill_base", "cost_parameter", "DP"
        );
        assertEquals("HP", result, "cost_parameter=DP 应该修正为 HP");

        // 其他有效值不应被修正
        String validResult = XmlFieldValueCorrector.correctValue(
                "skill_base", "cost_parameter", "MP"
        );
        assertEquals("MP", validResult, "有效值不应被修正");
    }

    // ==================== 世界字段修正测试 ====================

    @Test
    public void testCorrectStrparamPureNumber() {
        // 错误模式: World::Load, world name="Ab1", is not string type(node:strparam2)
        // 修正: 纯数字 → str_前缀
        String result1 = XmlFieldValueCorrector.correctValue(
                "world", "strparam1", "123"
        );
        assertEquals("str_123", result1, "strparam1 纯数字应该添加 str_ 前缀");

        String result2 = XmlFieldValueCorrector.correctValue(
                "world", "strparam2", "456"
        );
        assertEquals("str_456", result2, "strparam2 纯数字应该添加 str_ 前缀");

        String result3 = XmlFieldValueCorrector.correctValue(
                "world", "strparam3", "789"
        );
        assertEquals("str_789", result3, "strparam3 纯数字应该添加 str_ 前缀");

        // 已经是字符串的不应被修正
        String validResult = XmlFieldValueCorrector.correctValue(
                "world", "strparam1", "abc123"
        );
        assertEquals("abc123", validResult, "字符串值不应被修正");
    }

    @Test
    public void testCorrectInstanceCooltime() {
        // 错误模式: instance_cooltime 值 7080 无效
        // 修正: 7080 → 7200
        String result = XmlFieldValueCorrector.correctValue(
                "world", "instance_cooltime", "7080"
        );
        assertEquals("7200", result, "instance_cooltime=7080 应该修正为 7200");

        // 其他值不应被修正
        String validResult = XmlFieldValueCorrector.correctValue(
                "world", "instance_cooltime", "3600"
        );
        assertEquals("3600", validResult, "有效值不应被修正");
    }

    // ==================== NPC字段修正测试 ====================

    @Test
    public void testCorrectNpcSkillLevel() {
        // 错误模式: invalid skill_level=255 for NPC
        // 修正: 255 → 1
        String result = XmlFieldValueCorrector.correctValue(
                "npc_template", "skill_level", "255"
        );
        assertEquals("1", result, "NPC skill_level=255 应该修正为 1");

        // 有效值不应被修正
        String validResult = XmlFieldValueCorrector.correctValue(
                "npc_template", "skill_level", "50"
        );
        assertEquals("50", validResult, "有效值不应被修正");
    }

    @Test
    public void testCorrectAbnormalStatusResistName() {
        // 错误模式: abnormal_status_resist_name 应该是状态名，不是数字ID
        // 修正: 数字ID → 状态名
        Map<String, String> testCases = Map.of(
                "0", "无",
                "50", "沉默",
                "900", "眩晕",
                "100", "定身",
                "200", "减速",
                "300", "睡眠",
                "400", "恐惧",
                "500", "魅惑",
                "600", "缠绕",
                "700", "石化",
                "800", "失明"
        );

        for (Map.Entry<String, String> entry : testCases.entrySet()) {
            String id = entry.getKey();
            String expectedName = entry.getValue();
            String result = XmlFieldValueCorrector.correctValue(
                    "npc_template", "abnormal_status_resist_name", id
            );
            assertEquals(expectedName, result,
                    String.format("abnormal_status_resist_name=%s 应该转换为 %s", id, expectedName));
        }

        // 未知ID应该有默认格式
        String unknownResult = XmlFieldValueCorrector.correctValue(
                "npc_template", "abnormal_status_resist_name", "999"
        );
        assertEquals("未知状态_999", unknownResult, "未知ID应该生成默认格式");

        // 已经是字符串的不应被修正
        String validResult = XmlFieldValueCorrector.correctValue(
                "npc_template", "abnormal_status_resist_name", "石化"
        );
        assertEquals("石化", validResult, "字符串状态名不应被修正");
    }

    // ==================== 道具字段修正测试 ====================

    @Test
    public void testCorrectItemCastingDelay() {
        // 道具的 casting_delay 也不能为 0
        // 修正: 0 → 100
        String result = XmlFieldValueCorrector.correctValue(
                "item_weapon", "casting_delay", "0"
        );
        assertEquals("100", result, "道具 casting_delay=0 应该修正为 100");

        // 有效值不应被修正
        String validResult = XmlFieldValueCorrector.correctValue(
                "item_weapon", "casting_delay", "500"
        );
        assertEquals("500", validResult, "有效值不应被修正");
    }

    // ==================== 批量修正测试 ====================

    @Test
    public void testCorrectRow() {
        // 准备测试数据：包含多个需要修正的字段
        Map<String, String> row = new HashMap<>();
        row.put("id", "12345");
        row.put("target_flying_restriction", "0");  // 需要修正 → 1
        row.put("target_maxcount", "0");            // 需要修正 → 1
        row.put("casting_delay", "0");              // 需要修正 → 100
        row.put("cost_parameter", "DP");            // 需要修正 → HP
        row.put("valid_field", "valid_value");      // 不需要修正

        // 执行批量修正
        Map<String, String> correctedRow = XmlFieldValueCorrector.correctRow("skill_base", row);

        // 验证修正结果
        assertEquals("12345", correctedRow.get("id"), "ID不应被修正");
        assertEquals("1", correctedRow.get("target_flying_restriction"), "应该修正为 1");
        assertEquals("1", correctedRow.get("target_maxcount"), "应该修正为 1");
        assertEquals("100", correctedRow.get("casting_delay"), "应该修正为 100");
        assertEquals("HP", correctedRow.get("cost_parameter"), "应该修正为 HP");
        assertEquals("valid_value", correctedRow.get("valid_field"), "有效字段不应被修正");
    }

    // ==================== 验证功能测试 ====================

    @Test
    public void testValidateValue() {
        // 测试验证功能（不修正，只返回错误信息）

        // 技能字段验证
        String error1 = XmlFieldValueCorrector.validateValue(
                "skill_base", "target_flying_restriction", "0"
        );
        assertNotNull(error1, "应该检测到 target_flying_restriction=0 的错误");
        assertTrue(error1.contains("不能为 0"), "错误信息应该说明不能为0");

        String error2 = XmlFieldValueCorrector.validateValue(
                "skill_base", "target_maxcount", "0"
        );
        assertNotNull(error2, "应该检测到 target_maxcount=0 的错误");

        String error3 = XmlFieldValueCorrector.validateValue(
                "skill_base", "casting_delay", "0"
        );
        assertNotNull(error3, "应该检测到 casting_delay=0 的错误");

        // 世界字段验证
        String error4 = XmlFieldValueCorrector.validateValue(
                "world", "strparam1", "123"
        );
        assertNotNull(error4, "应该检测到 strparam 纯数字的错误");
        assertTrue(error4.contains("字符串类型"), "错误信息应该说明需要字符串类型");

        // NPC字段验证
        String error5 = XmlFieldValueCorrector.validateValue(
                "npc_template", "skill_level", "255"
        );
        assertNotNull(error5, "应该检测到 skill_level=255 的错误");

        // 有效值应该通过验证（返回null）
        String noError = XmlFieldValueCorrector.validateValue(
                "skill_base", "target_maxcount", "50"
        );
        assertNull(noError, "有效值应该通过验证");
    }

    // ==================== 统计功能测试 ====================

    @Test
    public void testStatistics() {
        // 执行多次修正
        XmlFieldValueCorrector.correctValue("skill_base", "target_flying_restriction", "0");
        XmlFieldValueCorrector.correctValue("skill_base", "target_flying_restriction", "0");
        XmlFieldValueCorrector.correctValue("skill_base", "target_maxcount", "0");
        XmlFieldValueCorrector.correctValue("world", "strparam1", "123");

        // 获取统计信息
        String stats = XmlFieldValueCorrector.getStatistics();

        // 验证统计信息
        assertNotNull(stats, "统计信息不应为null");
        assertTrue(stats.contains("skill_base.target_flying_restriction"),
                "应该包含 target_flying_restriction 的统计");
        assertTrue(stats.contains("2 次修正"),
                "target_flying_restriction 应该有2次修正");
        assertTrue(stats.contains("总修正次数: 4"),
                "总修正次数应该是4次");
    }

    @Test
    public void testResetStatistics() {
        // 执行一些修正
        XmlFieldValueCorrector.correctValue("skill_base", "target_flying_restriction", "0");
        XmlFieldValueCorrector.correctValue("skill_base", "target_maxcount", "0");

        // 验证统计不为空
        String stats1 = XmlFieldValueCorrector.getStatistics();
        assertTrue(stats1.contains("总修正次数"), "修正后应该有统计信息");

        // 重置统计
        XmlFieldValueCorrector.resetStatistics();

        // 验证统计已清空
        String stats2 = XmlFieldValueCorrector.getStatistics();
        assertEquals("未进行任何字段值修正", stats2, "重置后统计应该为空");
    }

    // ==================== 边界情况测试 ====================

    @Test
    public void testNullValue() {
        // null值不应被修正
        String result = XmlFieldValueCorrector.correctValue(
                "skill_base", "target_flying_restriction", null
        );
        assertNull(result, "null值不应被修正");
    }

    @Test
    public void testEmptyValue() {
        // 空字符串不应被修正
        String result = XmlFieldValueCorrector.correctValue(
                "skill_base", "target_flying_restriction", ""
        );
        assertEquals("", result, "空字符串不应被修正");
    }

    @Test
    public void testValidValue() {
        // 有效值不应被修正
        String result = XmlFieldValueCorrector.correctValue(
                "skill_base", "target_flying_restriction", "2"
        );
        assertEquals("2", result, "有效值不应被修正");

        // 不应产生统计
        String stats = XmlFieldValueCorrector.getStatistics();
        assertEquals("未进行任何字段值修正", stats, "有效值不应产生修正统计");
    }

    @Test
    public void testUnknownTable() {
        // 未知表名应该不进行修正
        String result = XmlFieldValueCorrector.correctValue(
                "unknown_table", "target_flying_restriction", "0"
        );
        assertEquals("0", result, "未知表名不应进行修正");
    }

    @Test
    public void testUnknownField() {
        // 已知表但未知字段应该不进行修正
        String result = XmlFieldValueCorrector.correctValue(
                "skill_base", "unknown_field", "invalid_value"
        );
        assertEquals("invalid_value", result, "未知字段不应进行修正");
    }

    // ==================== 多表类型匹配测试 ====================

    @Test
    public void testSkillTableMatching() {
        // 测试各种技能表名匹配
        String[] skillTables = {
                "skill_base",
                "skill_advanced",
                "item_skill_enhance",
                "npc_skill_list"
        };

        for (String table : skillTables) {
            String result = XmlFieldValueCorrector.correctValue(
                    table, "target_flying_restriction", "0"
            );
            assertEquals("1", result,
                    String.format("表 %s 应该应用技能字段修正规则", table));
        }
    }

    @Test
    public void testWorldTableMatching() {
        // 测试各种世界表名匹配
        String[] worldTables = {
                "world",
                "world_map",
                "world_settings"
        };

        for (String table : worldTables) {
            String result = XmlFieldValueCorrector.correctValue(
                    table, "strparam1", "123"
            );
            assertEquals("str_123", result,
                    String.format("表 %s 应该应用世界字段修正规则", table));
        }
    }

    @Test
    public void testNpcTableMatching() {
        // 测试各种NPC表名匹配
        String[] npcTables = {
                "npc_template",
                "npc_spawns",
                "item_npc_sell"
        };

        for (String table : npcTables) {
            String result = XmlFieldValueCorrector.correctValue(
                    table, "skill_level", "255"
            );
            assertEquals("1", result,
                    String.format("表 %s 应该应用NPC字段修正规则", table));
        }
    }

    @Test
    public void testItemTableMatching() {
        // 测试各种道具表名匹配
        String[] itemTables = {
                "item_weapon",
                "item_armor",
                "item_consumable"
        };

        for (String table : itemTables) {
            String result = XmlFieldValueCorrector.correctValue(
                    table, "casting_delay", "0"
            );
            assertEquals("100", result,
                    String.format("表 %s 应该应用道具字段修正规则", table));
        }
    }

    // ==================== 综合场景测试 ====================

    @Test
    public void testRealWorldScenario() {
        // 模拟真实导出场景：一个技能数据行包含多个需要修正的字段

        Map<String, String> skillData = new HashMap<>();
        skillData.put("id", "11001");
        skillData.put("name", "火球术");
        skillData.put("target_flying_restriction", "0");  // 需要修正
        skillData.put("target_maxcount", "150");          // 需要修正（超出范围）
        skillData.put("casting_delay", "0");              // 需要修正
        skillData.put("cost_parameter", "DP");            // 需要修正
        skillData.put("penalty_time_succ", "0");          // 需要修正

        // 批量修正
        Map<String, String> corrected = XmlFieldValueCorrector.correctRow("skill_base", skillData);

        // 验证所有字段
        assertEquals("11001", corrected.get("id"));
        assertEquals("火球术", corrected.get("name"));
        assertEquals("1", corrected.get("target_flying_restriction"));
        assertEquals("120", corrected.get("target_maxcount"));
        assertEquals("100", corrected.get("casting_delay"));
        assertEquals("HP", corrected.get("cost_parameter"));
        assertEquals("1", corrected.get("penalty_time_succ"));

        // 验证统计
        String stats = XmlFieldValueCorrector.getStatistics();
        assertTrue(stats.contains("5 个字段") || stats.contains("总修正次数: 5"),
                "应该统计到5个字段的修正");
    }

    @Test
    public void testNoCorrectionsNeeded() {
        // 测试完全有效的数据不会被修正

        Map<String, String> validData = new HashMap<>();
        validData.put("id", "11001");
        validData.put("name", "火球术");
        validData.put("target_flying_restriction", "1");
        validData.put("target_maxcount", "50");
        validData.put("casting_delay", "1000");
        validData.put("cost_parameter", "MP");

        // 批量修正
        Map<String, String> corrected = XmlFieldValueCorrector.correctRow("skill_base", validData);

        // 验证数据未被修改
        assertEquals(validData, corrected, "有效数据不应被修正");

        // 验证无统计
        String stats = XmlFieldValueCorrector.getStatistics();
        assertEquals("未进行任何字段值修正", stats, "有效数据不应产生修正统计");
    }
}
