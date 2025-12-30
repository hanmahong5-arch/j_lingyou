package red.jiuzhou.validation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * XmlFieldBlacklist 单元测试
 */
public class XmlFieldBlacklistTest {

    @Test
    public void testGlobalBlacklist() {
        // 测试全局黑名单字段
        assertTrue(XmlFieldBlacklist.shouldFilter("any_table", "__order_index"));
        assertTrue(XmlFieldBlacklist.shouldFilter("skill_base", "__order_index"));
        assertTrue(XmlFieldBlacklist.shouldFilter("npc_template", "__order_index"));

        // 验证过滤原因
        String reason = XmlFieldBlacklist.getFilterReason("any_table", "__order_index");
        assertNotNull(reason);
        assertTrue(reason.contains("XML工具内部字段"));
    }

    @Test
    public void testSkillBlacklist() {
        // 测试技能系统黑名单
        assertTrue(XmlFieldBlacklist.shouldFilter("skill_base", "status_fx_slot_lv"));
        assertTrue(XmlFieldBlacklist.shouldFilter("skill_data", "toggle_id"));
        assertTrue(XmlFieldBlacklist.shouldFilter("skill_template", "is_familiar_skill"));

        // 验证非技能表不过滤
        assertFalse(XmlFieldBlacklist.shouldFilter("item_weapon", "status_fx_slot_lv"));
        assertFalse(XmlFieldBlacklist.shouldFilter("npc_template", "toggle_id"));
    }

    @Test
    public void testNpcBlacklist() {
        // 测试NPC系统黑名单
        assertTrue(XmlFieldBlacklist.shouldFilter("npc_template", "erect"));
        assertTrue(XmlFieldBlacklist.shouldFilter("npc_spawns", "monsterbook_race"));
        assertTrue(XmlFieldBlacklist.shouldFilter("template_npc", "erect"));

        // 验证非NPC表不过滤
        assertFalse(XmlFieldBlacklist.shouldFilter("skill_base", "erect"));
    }

    @Test
    public void testDropBlacklist() {
        // 测试掉落系统黑名单（drop_prob_6~9）
        assertTrue(XmlFieldBlacklist.shouldFilter("drop_list", "drop_prob_6"));
        assertTrue(XmlFieldBlacklist.shouldFilter("npc_drop", "drop_prob_9"));
        assertTrue(XmlFieldBlacklist.shouldFilter("drop_list", "drop_monster_7"));
        assertTrue(XmlFieldBlacklist.shouldFilter("drop_list", "drop_item_8"));

        // 验证合法的掉落字段不过滤
        assertFalse(XmlFieldBlacklist.shouldFilter("drop_list", "drop_prob_1"));
        assertFalse(XmlFieldBlacklist.shouldFilter("drop_list", "drop_prob_5"));
    }

    @Test
    public void testItemBlacklist() {
        // 测试道具系统黑名单
        assertTrue(XmlFieldBlacklist.shouldFilter("item_weapon", "item_skin_override"));
        assertTrue(XmlFieldBlacklist.shouldFilter("item_armor", "dyeable_v2"));
        assertTrue(XmlFieldBlacklist.shouldFilter("item_accessory", "glamour_id"));

        // 验证非道具表不过滤
        assertFalse(XmlFieldBlacklist.shouldFilter("npc_template", "item_skin_override"));
    }

    @Test
    public void testNormalFieldsNotFiltered() {
        // 验证正常字段不被过滤
        assertFalse(XmlFieldBlacklist.shouldFilter("skill_base", "name"));
        assertFalse(XmlFieldBlacklist.shouldFilter("skill_base", "level"));
        assertFalse(XmlFieldBlacklist.shouldFilter("npc_template", "hp"));
        assertFalse(XmlFieldBlacklist.shouldFilter("item_weapon", "damage"));
    }

    @Test
    public void testCountFilteredFields() {
        // 测试统计过滤字段数量
        java.util.Set<String> skillFields = java.util.Set.of(
            "name", "level", "status_fx_slot_lv", "toggle_id", "damage", "__order_index"
        );

        int filtered = XmlFieldBlacklist.countFilteredFields("skill_base", skillFields);
        assertEquals(3, filtered); // status_fx_slot_lv, toggle_id, __order_index
    }
}
