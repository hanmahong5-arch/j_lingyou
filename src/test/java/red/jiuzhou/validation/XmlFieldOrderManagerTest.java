package red.jiuzhou.validation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XmlFieldOrderManager 单元测试
 */
public class XmlFieldOrderManagerTest {

    @BeforeEach
    public void setUp() {
        // 每个测试前清除缓存
        XmlFieldOrderManager.clearCache();
    }

    @AfterEach
    public void tearDown() {
        // 每个测试后清除缓存
        XmlFieldOrderManager.clearCache();
    }

    @Test
    public void testInitialize() {
        // 测试初始化
        boolean result = XmlFieldOrderManager.initialize();
        assertTrue(result, "XmlFieldOrderManager应该成功初始化");

        // 验证统计信息
        String stats = XmlFieldOrderManager.getStatistics();
        assertNotNull(stats);
        assertTrue(stats.contains("表:"), "统计信息应包含表数量");
        assertTrue(stats.contains("字段:"), "统计信息应包含字段数量");
    }

    @Test
    public void testIdFieldFirst() {
        // 测试ID字段始终排在第一位
        XmlFieldOrderManager.initialize();

        Set<String> fields = new LinkedHashSet<>(Arrays.asList(
                "name", "level", "id", "attack", "__order_index"
        ));

        Set<String> sorted = XmlFieldOrderManager.sortFields("skill_base", fields);

        assertNotNull(sorted);
        assertFalse(sorted.isEmpty());

        // ID应该排在第一位
        assertEquals("id", sorted.iterator().next(), "ID字段应该排在第一位");
    }

    @Test
    public void testBlacklistFiltering() {
        // 测试黑名单字段被过滤
        XmlFieldOrderManager.initialize();

        Set<String> fields = new LinkedHashSet<>(Arrays.asList(
                "id", "name", "__order_index", "level", "__row_index"
        ));

        Set<String> sorted = XmlFieldOrderManager.sortFields("skill_base", fields);

        assertNotNull(sorted);

        // 黑名单字段应该被过滤掉
        assertFalse(sorted.contains("__order_index"), "__order_index应该被过滤");
        assertFalse(sorted.contains("__row_index"), "__row_index应该被过滤");

        // 正常字段应该保留
        assertTrue(sorted.contains("id"));
        assertTrue(sorted.contains("name"));
        assertTrue(sorted.contains("level"));
    }

    @Test
    public void testSkillBlacklistFields() {
        // 测试技能系统黑名单
        XmlFieldOrderManager.initialize();

        Set<String> fields = new LinkedHashSet<>(Arrays.asList(
                "id", "name", "status_fx_slot_lv", "toggle_id", "level"
        ));

        Set<String> sorted = XmlFieldOrderManager.sortFields("skill_base", fields);

        // 技能黑名单字段应该被过滤
        assertFalse(sorted.contains("status_fx_slot_lv"), "status_fx_slot_lv应该被过滤");
        assertFalse(sorted.contains("toggle_id"), "toggle_id应该被过滤");

        // 正常字段应该保留
        assertTrue(sorted.contains("id"));
        assertTrue(sorted.contains("name"));
        assertTrue(sorted.contains("level"));
    }

    @Test
    public void testNpcBlacklistFields() {
        // 测试NPC系统黑名单
        XmlFieldOrderManager.initialize();

        Set<String> fields = new LinkedHashSet<>(Arrays.asList(
                "id", "name", "erect", "monsterbook_race", "hp"
        ));

        Set<String> sorted = XmlFieldOrderManager.sortFields("npc_template", fields);

        // NPC黑名单字段应该被过滤
        assertFalse(sorted.contains("erect"), "erect应该被过滤");
        assertFalse(sorted.contains("monsterbook_race"), "monsterbook_race应该被过滤");

        // 正常字段应该保留
        assertTrue(sorted.contains("id"));
        assertTrue(sorted.contains("name"));
        assertTrue(sorted.contains("hp"));
    }

    @Test
    public void testFieldOrderStability() {
        // 测试字段顺序稳定性（多次调用结果一致）
        XmlFieldOrderManager.initialize();

        Set<String> fields = new LinkedHashSet<>(Arrays.asList(
                "name", "id", "level", "attack", "defense"
        ));

        Set<String> sorted1 = XmlFieldOrderManager.sortFields("item_weapon", fields);
        Set<String> sorted2 = XmlFieldOrderManager.sortFields("item_weapon", fields);
        Set<String> sorted3 = XmlFieldOrderManager.sortFields("item_weapon", fields);

        // 多次排序结果应该完全一致
        assertEquals(new ArrayList<>(sorted1), new ArrayList<>(sorted2), "第1次和第2次排序结果应该一致");
        assertEquals(new ArrayList<>(sorted2), new ArrayList<>(sorted3), "第2次和第3次排序结果应该一致");
    }

    @Test
    public void testEmptyFields() {
        // 测试空字段集合
        XmlFieldOrderManager.initialize();

        Set<String> fields = new HashSet<>();
        Set<String> sorted = XmlFieldOrderManager.sortFields("skill_base", fields);

        assertNotNull(sorted);
        assertTrue(sorted.isEmpty(), "空字段集合排序后应该仍为空");
    }

    @Test
    public void testUnknownTable() {
        // 测试未知表名
        XmlFieldOrderManager.initialize();

        Set<String> fields = new LinkedHashSet<>(Arrays.asList(
                "id", "name", "__order_index", "value"
        ));

        Set<String> sorted = XmlFieldOrderManager.sortFields("unknown_table_xyz", fields);

        assertNotNull(sorted);

        // 即使表名未知，也应该过滤黑名单字段
        assertFalse(sorted.contains("__order_index"), "黑名单字段仍应被过滤");

        // 其他字段应该保留
        assertTrue(sorted.contains("id"));
        assertTrue(sorted.contains("name"));
        assertTrue(sorted.contains("value"));
    }

    @Test
    public void testGetOrderedFields() {
        // 测试获取有序字段列表
        XmlFieldOrderManager.initialize();

        List<String> ordered = XmlFieldOrderManager.getOrderedFields("skill_base");

        assertNotNull(ordered);
        assertFalse(ordered.isEmpty(), "skill_base表应该有字段定义");

        // ID应该排在第一位
        assertTrue(ordered.get(0).equals("id") || ordered.get(0).equals("_attr_id"),
                "第一个字段应该是ID字段");

        // 不应该包含黑名单字段
        assertFalse(ordered.contains("__order_index"), "不应该包含__order_index");
        assertFalse(ordered.contains("status_fx_slot_lv"), "不应该包含status_fx_slot_lv");
    }

    @Test
    public void testGetFieldPosition() {
        // 测试获取字段位置
        XmlFieldOrderManager.initialize();

        Integer pos1 = XmlFieldOrderManager.getFieldPosition("skill_base", "id");
        Integer pos2 = XmlFieldOrderManager.getFieldPosition("skill_base", "name");

        assertNotNull(pos1, "id字段应该有位置定义");
        assertEquals(1, pos1.intValue(), "id字段的ordinalPosition应该是1");

        // name字段的位置应该大于id
        if (pos2 != null) {
            assertTrue(pos2 > pos1, "name字段的位置应该在id之后");
        }
    }

    @Test
    public void testMultipleIdFields() {
        // 测试多种ID字段名
        XmlFieldOrderManager.initialize();

        // 测试 "id"
        Set<String> fields1 = new LinkedHashSet<>(Arrays.asList("name", "id", "level"));
        Set<String> sorted1 = XmlFieldOrderManager.sortFields("test_table", fields1);
        assertEquals("id", sorted1.iterator().next());

        // 测试 "_attr_id"
        Set<String> fields2 = new LinkedHashSet<>(Arrays.asList("name", "_attr_id", "level"));
        Set<String> sorted2 = XmlFieldOrderManager.sortFields("test_table", fields2);
        assertEquals("_attr_id", sorted2.iterator().next());

        // 测试 "ID"
        Set<String> fields3 = new LinkedHashSet<>(Arrays.asList("name", "ID", "level"));
        Set<String> sorted3 = XmlFieldOrderManager.sortFields("test_table", fields3);
        assertEquals("ID", sorted3.iterator().next());
    }

    @Test
    public void testClearCache() {
        // 测试缓存清除
        XmlFieldOrderManager.initialize();

        String stats1 = XmlFieldOrderManager.getStatistics();
        assertNotNull(stats1);

        // 清除缓存
        XmlFieldOrderManager.clearCache();

        // 重新初始化后应该有相同的统计信息
        XmlFieldOrderManager.initialize();
        String stats2 = XmlFieldOrderManager.getStatistics();
        assertEquals(stats1, stats2, "重新初始化后统计信息应该一致");
    }

    @Test
    public void testDropBlacklistFields() {
        // 测试掉落系统黑名单
        XmlFieldOrderManager.initialize();

        Set<String> fields = new LinkedHashSet<>(Arrays.asList(
                "id", "drop_prob_1", "drop_prob_6", "drop_prob_9", "npc_id"
        ));

        Set<String> sorted = XmlFieldOrderManager.sortFields("drop_list", fields);

        // drop_prob_1~5 应该保留
        assertTrue(sorted.contains("drop_prob_1"), "drop_prob_1应该保留");

        // drop_prob_6~9 应该被过滤
        assertFalse(sorted.contains("drop_prob_6"), "drop_prob_6应该被过滤");
        assertFalse(sorted.contains("drop_prob_9"), "drop_prob_9应该被过滤");
    }

    @Test
    public void testItemBlacklistFields() {
        // 测试道具系统黑名单
        XmlFieldOrderManager.initialize();

        Set<String> fields = new LinkedHashSet<>(Arrays.asList(
                "id", "name", "item_skin_override", "dyeable_v2", "attack"
        ));

        Set<String> sorted = XmlFieldOrderManager.sortFields("item_weapon", fields);

        // 道具黑名单字段应该被过滤
        assertFalse(sorted.contains("item_skin_override"), "item_skin_override应该被过滤");
        assertFalse(sorted.contains("dyeable_v2"), "dyeable_v2应该被过滤");

        // 正常字段应该保留
        assertTrue(sorted.contains("id"));
        assertTrue(sorted.contains("name"));
        assertTrue(sorted.contains("attack"));
    }

    @Test
    public void testOrderPreservation() {
        // 测试排序后字段的相对顺序
        XmlFieldOrderManager.initialize();

        // 如果缓存中有ordinalPosition定义，字段应该按照该顺序排列
        Set<String> fields = new LinkedHashSet<>(Arrays.asList(
                "level", "name", "id", "attack"
        ));

        Set<String> sorted = XmlFieldOrderManager.sortFields("item_weapon", fields);

        // 转为List便于检查顺序
        List<String> sortedList = new ArrayList<>(sorted);

        // ID应该在第一位
        assertEquals("id", sortedList.get(0));

        // 后续字段应该按照ordinalPosition排序（具体顺序取决于数据库定义）
        // 这里只验证结果是稳定的
        assertNotNull(sortedList);
        assertTrue(sortedList.size() >= 3);
    }
}
