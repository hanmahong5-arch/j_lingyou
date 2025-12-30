package red.jiuzhou.validation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * XmlFieldValidator 单元测试
 */
public class XmlFieldValidatorTest {

    @Test
    public void testSkillCastingDelayValid() {
        // 测试合法的施法延迟
        Map<String, String> row = new HashMap<>();
        row.put("name", "测试技能");
        row.put("casting_delay", "5000");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("skill_base", row, 1);

        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testSkillCastingDelayExceedsLimit() {
        // 测试超过限制的施法延迟（≥60000ms）
        Map<String, String> row = new HashMap<>();
        row.put("name", "超长施法技能");
        row.put("casting_delay", "60000");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("skill_base", row, 1);

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).contains("casting_delay=60000"));
        assertTrue(result.getErrors().get(0).contains("超过限制"));
    }

    @Test
    public void testSkillCastingDelayWarning() {
        // 测试过长但未超限的施法延迟（>30s但<60s）
        Map<String, String> row = new HashMap<>();
        row.put("name", "长施法技能");
        row.put("casting_delay", "35000");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("skill_base", row, 1);

        assertFalse(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertTrue(result.getWarnings().get(0).contains("casting_delay=35000"));
        assertTrue(result.getWarnings().get(0).contains("过长"));
    }

    @Test
    public void testSkillCastingDelayInvalidFormat() {
        // 测试非整数的施法延迟
        Map<String, String> row = new HashMap<>();
        row.put("name", "格式错误技能");
        row.put("casting_delay", "abc");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("skill_base", row, 1);

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).contains("应为整数"));
        assertTrue(result.getErrors().get(0).contains("abc"));
    }

    @Test
    public void testSkillLevelValid() {
        // 测试合法的技能等级（1-100）
        Map<String, String> row = new HashMap<>();
        row.put("name", "测试技能");
        row.put("skill_level", "50");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("skill_base", row, 1);

        assertFalse(result.hasErrors());
    }

    @Test
    public void testSkillLevelOutOfRange() {
        // 测试超出范围的技能等级
        Map<String, String> row1 = new HashMap<>();
        row1.put("name", "等级过低");
        row1.put("skill_level", "0");

        XmlFieldValidator.ValidationResult result1 =
            XmlFieldValidator.validate("skill_base", row1, 1);

        assertTrue(result1.hasErrors());
        assertTrue(result1.getErrors().get(0).contains("超出有效范围（1-100）"));

        Map<String, String> row2 = new HashMap<>();
        row2.put("name", "等级过高");
        row2.put("skill_level", "101");

        XmlFieldValidator.ValidationResult result2 =
            XmlFieldValidator.validate("skill_base", row2, 2);

        assertTrue(result2.hasErrors());
        assertTrue(result2.getErrors().get(0).contains("超出有效范围（1-100）"));
    }

    @Test
    public void testSkillCostParameterDP() {
        // 测试无效的消耗参数类型（DP）
        Map<String, String> row = new HashMap<>();
        row.put("name", "错误消耗技能");
        row.put("cost_parameter", "DP");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("skill_base", row, 1);

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).contains("cost_parameter='DP' 无效"));
    }

    @Test
    public void testSkillFlyingRestrictionZero() {
        // 测试无效的飞行限制值（0）
        Map<String, String> row = new HashMap<>();
        row.put("name", "错误飞行限制");
        row.put("target_flying_restriction", "0");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("skill_base", row, 1);

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).contains("target_flying_restriction='0' 无效"));
    }

    @Test
    public void testSkillFlyingRestrictionValid() {
        // 测试合法的飞行限制值（1-4）
        for (String value : List.of("1", "2", "3", "4")) {
            Map<String, String> row = new HashMap<>();
            row.put("name", "合法飞行限制");
            row.put("target_flying_restriction", value);

            XmlFieldValidator.ValidationResult result =
                XmlFieldValidator.validate("skill_base", row, 1);

            assertFalse(result.hasErrors(), "value=" + value + " 应该合法");
        }
    }

    @Test
    public void testWorldStrparamValid() {
        // 测试合法的字符串参数（含字母）
        Map<String, String> row = new HashMap<>();
        row.put("name", "测试世界");
        row.put("strparam1", "map_name");
        row.put("strparam2", "config123");
        row.put("strparam3", "setting_a");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("world", row, 1);

        assertFalse(result.hasErrors());
    }

    @Test
    public void testWorldStrparamPureNumber() {
        // 测试纯数字的字符串参数（应报错）
        Map<String, String> row = new HashMap<>();
        row.put("name", "错误世界");
        row.put("strparam1", "12345");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("world", row, 1);

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).contains("应为字符串类型"));
        assertTrue(result.getErrors().get(0).contains("12345"));
    }

    @Test
    public void testWorldStrparamMultipleErrors() {
        // 测试多个字符串参数同时错误
        Map<String, String> row = new HashMap<>();
        row.put("name", "多错误世界");
        row.put("strparam1", "123");
        row.put("strparam2", "456");
        row.put("strparam3", "789");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("world", row, 1);

        assertTrue(result.hasErrors());
        assertEquals(3, result.getErrors().size());
    }

    @Test
    public void testNpcAbnormalStatusResistNameValid() {
        // 测试合法的状态抗性名称（字符串）
        Map<String, String> row = new HashMap<>();
        row.put("name", "测试NPC");
        row.put("abnormal_status_resist_name", "沉默");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("npc_template", row, 1);

        assertFalse(result.hasErrors());
    }

    @Test
    public void testNpcAbnormalStatusResistNamePureNumber() {
        // 测试纯数字的状态抗性名称（应报错）
        Map<String, String> row = new HashMap<>();
        row.put("name", "错误NPC");
        row.put("abnormal_status_resist_name", "50");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("npc_template", row, 1);

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).contains("应为状态名称字符串"));
        assertTrue(result.getWarnings().get(0).contains("常见状态名称如"));
    }

    @Test
    public void testNpcSkillLevel255() {
        // 测试无效的技能等级255
        Map<String, String> row = new HashMap<>();
        row.put("name", "错误技能等级NPC");
        row.put("skill_level", "255");

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("npc_spawns", row, 1);

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().get(0).contains("skill_level=255 无效"));
    }

    @Test
    public void testBatchValidation() {
        // 测试批量验证
        List<Map<String, String>> dataList = new ArrayList<>();

        // 第1条：合法数据
        Map<String, String> row1 = new HashMap<>();
        row1.put("name", "合法技能1");
        row1.put("casting_delay", "3000");
        row1.put("skill_level", "50");
        dataList.add(row1);

        // 第2条：有错误
        Map<String, String> row2 = new HashMap<>();
        row2.put("name", "错误技能");
        row2.put("casting_delay", "70000");
        dataList.add(row2);

        // 第3条：有警告
        Map<String, String> row3 = new HashMap<>();
        row3.put("name", "警告技能");
        row3.put("casting_delay", "40000");
        dataList.add(row3);

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validateBatch("skill_base", dataList);

        assertTrue(result.hasErrors());
        assertTrue(result.hasWarnings());
        assertEquals(1, result.getErrors().size());
        assertEquals(1, result.getWarnings().size());
    }

    @Test
    public void testValidationResultSummary() {
        // 测试验证结果摘要格式
        XmlFieldValidator.ValidationResult result = new XmlFieldValidator.ValidationResult();
        result.addError("错误1：字段超限");
        result.addError("错误2：类型不匹配");
        result.addWarning("警告1：建议修改");

        String summary = result.getSummary();

        assertTrue(summary.contains("❌ 错误 (2)"));
        assertTrue(summary.contains("⚠ 警告 (1)"));
        assertTrue(summary.contains("错误1：字段超限"));
        assertTrue(summary.contains("警告1：建议修改"));
    }

    @Test
    public void testMultipleValidationErrors() {
        // 测试单条数据多个字段同时错误
        Map<String, String> row = new HashMap<>();
        row.put("name", "多错误技能");
        row.put("casting_delay", "80000");         // 错误：超限
        row.put("skill_level", "200");             // 错误：超范围
        row.put("cost_parameter", "DP");           // 错误：无效类型
        row.put("target_flying_restriction", "0"); // 错误：无效值

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("skill_base", row, 1);

        assertTrue(result.hasErrors());
        assertEquals(4, result.getErrors().size());
    }

    @Test
    public void testEmptyFieldsIgnored() {
        // 测试空字段不触发验证
        Map<String, String> row = new HashMap<>();
        row.put("name", "测试技能");
        row.put("casting_delay", "");
        row.put("skill_level", null);

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("skill_base", row, 1);

        assertFalse(result.hasErrors());
        assertFalse(result.hasWarnings());
    }

    @Test
    public void testNonSkillTableSkipsSkillValidation() {
        // 测试非技能表不触发技能验证
        Map<String, String> row = new HashMap<>();
        row.put("name", "物品");
        row.put("casting_delay", "99999"); // 如果是技能表会报错，但物品表应跳过

        XmlFieldValidator.ValidationResult result =
            XmlFieldValidator.validate("item_weapon", row, 1);

        assertFalse(result.hasErrors());
    }
}
