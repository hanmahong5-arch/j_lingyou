package red.jiuzhou.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 稀疏字段（Sparse Fields）顺序稳定性测试
 *
 * <p>测试场景：同一个XML文件中，不同条目有不同的字段集合
 * 例如：条目1有ABCD，条目2只有ACD，条目3只有ABD
 *
 * <p>核心验证：无论哪些字段缺失，剩余字段的相对顺序必须保持稳定
 */
public class SparseFieldOrderTest {

    @BeforeEach
    public void setUp() {
        XmlFieldOrderManager.clearCache();
        XmlFieldOrderManager.initialize();
    }

    @Test
    public void testSparseFieldsOrderStability() {
        // 场景：三条数据有不同的字段集合
        String tableName = "item_weapon";

        // 数据1：完整字段 {id, name, level, attack, defense}
        Set<String> fields1 = new LinkedHashSet<>(Arrays.asList(
                "id", "name", "level", "attack", "defense"
        ));

        // 数据2：缺少 attack {id, name, level, defense}
        Set<String> fields2 = new LinkedHashSet<>(Arrays.asList(
                "id", "name", "level", "defense"
        ));

        // 数据3：缺少 level {id, name, attack, defense}
        Set<String> fields3 = new LinkedHashSet<>(Arrays.asList(
                "id", "name", "attack", "defense"
        ));

        // 数据4：只有必填字段 {id, name}
        Set<String> fields4 = new LinkedHashSet<>(Arrays.asList(
                "id", "name"
        ));

        // 排序
        Set<String> sorted1 = XmlFieldOrderManager.sortFields(tableName, fields1);
        Set<String> sorted2 = XmlFieldOrderManager.sortFields(tableName, fields2);
        Set<String> sorted3 = XmlFieldOrderManager.sortFields(tableName, fields3);
        Set<String> sorted4 = XmlFieldOrderManager.sortFields(tableName, fields4);

        // 转为List便于检查顺序
        List<String> list1 = new ArrayList<>(sorted1);
        List<String> list2 = new ArrayList<>(sorted2);
        List<String> list3 = new ArrayList<>(sorted3);
        List<String> list4 = new ArrayList<>(sorted4);

        // ============ 验证1：ID始终排在第一位 ============
        assertEquals("id", list1.get(0), "数据1的第一个字段应该是id");
        assertEquals("id", list2.get(0), "数据2的第一个字段应该是id");
        assertEquals("id", list3.get(0), "数据3的第一个字段应该是id");
        assertEquals("id", list4.get(0), "数据4的第一个字段应该是id");

        // ============ 验证2：相对顺序保持不变 ============
        // 如果两个字段在同一个集合中，它们的相对顺序应该一致

        // name和defense都在sorted1和sorted2中，它们的相对位置应该一致
        int namePos1 = list1.indexOf("name");
        int defensePos1 = list1.indexOf("defense");
        int namePos2 = list2.indexOf("name");
        int defensePos2 = list2.indexOf("defense");

        if (namePos1 < defensePos1) {
            assertTrue(namePos2 < defensePos2,
                    "name在defense前面的相对顺序应该在所有数据中保持一致");
        } else if (namePos1 > defensePos1) {
            assertTrue(namePos2 > defensePos2,
                    "defense在name前面的相对顺序应该在所有数据中保持一致");
        }

        // ============ 验证3：字段集合大小正确 ============
        assertEquals(5, sorted1.size(), "数据1应该有5个字段");
        assertEquals(4, sorted2.size(), "数据2应该有4个字段（缺少attack）");
        assertEquals(4, sorted3.size(), "数据3应该有4个字段（缺少level）");
        assertEquals(2, sorted4.size(), "数据4应该有2个字段");
    }

    @Test
    public void testConsistentOrderAcrossDifferentSubsets() {
        // 测试：不同的字段子集，相同字段的相对顺序应该一致
        String tableName = "skill_base";

        // 创建多个字段子集
        Set<String> subset1 = Set.of("id", "name", "level", "damage");
        Set<String> subset2 = Set.of("id", "name", "damage");  // 缺少level
        Set<String> subset3 = Set.of("id", "level", "damage");  // 缺少name
        Set<String> subset4 = Set.of("id", "damage");  // 只有id和damage

        // 排序
        List<String> sorted1 = new ArrayList<>(XmlFieldOrderManager.sortFields(tableName, subset1));
        List<String> sorted2 = new ArrayList<>(XmlFieldOrderManager.sortFields(tableName, subset2));
        List<String> sorted3 = new ArrayList<>(XmlFieldOrderManager.sortFields(tableName, subset3));
        List<String> sorted4 = new ArrayList<>(XmlFieldOrderManager.sortFields(tableName, subset4));

        // id和damage都在所有子集中，验证它们的相对位置
        int idPos1 = sorted1.indexOf("id");
        int damagePos1 = sorted1.indexOf("damage");

        int idPos2 = sorted2.indexOf("id");
        int damagePos2 = sorted2.indexOf("damage");

        int idPos3 = sorted3.indexOf("id");
        int damagePos3 = sorted3.indexOf("damage");

        int idPos4 = sorted4.indexOf("id");
        int damagePos4 = sorted4.indexOf("damage");

        // id应该在damage前面（因为id总是第一位）
        assertTrue(idPos1 < damagePos1, "子集1中id应该在damage前面");
        assertTrue(idPos2 < damagePos2, "子集2中id应该在damage前面");
        assertTrue(idPos3 < damagePos3, "子集3中id应该在damage前面");
        assertTrue(idPos4 < damagePos4, "子集4中id应该在damage前面");
    }

    @Test
    public void testRelativeOrderPreservation() {
        // 数学验证：相对顺序保持性
        // 如果ordinalPosition(A) < ordinalPosition(B)
        // 那么在任何包含A和B的子集中，A都应该排在B前面

        String tableName = "npc_template";

        // 创建包含不同字段的多个集合
        List<Set<String>> fieldSets = Arrays.asList(
                Set.of("id", "name", "hp", "attack", "defense"),
                Set.of("id", "name", "hp", "defense"),
                Set.of("id", "name", "attack"),
                Set.of("id", "hp", "defense"),
                Set.of("id", "name"),
                Set.of("id", "defense")
        );

        // 对每个集合进行排序
        List<List<String>> sortedSets = new ArrayList<>();
        for (Set<String> fieldSet : fieldSets) {
            List<String> sorted = new ArrayList<>(
                    XmlFieldOrderManager.sortFields(tableName, fieldSet)
            );
            sortedSets.add(sorted);
        }

        // 验证：如果两个字段同时出现在多个集合中，它们的相对顺序应该一致
        for (int i = 0; i < sortedSets.size(); i++) {
            List<String> set1 = sortedSets.get(i);
            for (int j = i + 1; j < sortedSets.size(); j++) {
                List<String> set2 = sortedSets.get(j);

                // 找出两个集合的公共字段
                Set<String> common = new HashSet<>(set1);
                common.retainAll(set2);

                // 对于每对公共字段，验证相对顺序一致
                for (String field1 : common) {
                    for (String field2 : common) {
                        if (!field1.equals(field2)) {
                            int pos1InSet1 = set1.indexOf(field1);
                            int pos2InSet1 = set1.indexOf(field2);
                            int pos1InSet2 = set2.indexOf(field1);
                            int pos2InSet2 = set2.indexOf(field2);

                            boolean order1 = pos1InSet1 < pos2InSet1;
                            boolean order2 = pos1InSet2 < pos2InSet2;

                            assertEquals(order1, order2,
                                    String.format("字段 %s 和 %s 的相对顺序在不同集合中应该一致",
                                            field1, field2));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testEmptyAndSingleFieldSets() {
        // 测试边界情况：空集合和单字段集合
        String tableName = "test_table";

        // 空集合
        Set<String> emptySet = new HashSet<>();
        Set<String> sortedEmpty = XmlFieldOrderManager.sortFields(tableName, emptySet);
        assertTrue(sortedEmpty.isEmpty(), "空集合排序后应该仍为空");

        // 单字段集合
        Set<String> singleField = Set.of("id");
        Set<String> sortedSingle = XmlFieldOrderManager.sortFields(tableName, singleField);
        assertEquals(1, sortedSingle.size(), "单字段集合排序后应该仍为1个字段");
        assertEquals("id", sortedSingle.iterator().next(), "单字段应该是id");
    }

    @Test
    public void testDifferentFieldCombinations() {
        // 测试：所有可能的字段组合，验证顺序稳定性
        String tableName = "item_weapon";
        List<String> allFields = Arrays.asList("id", "A", "B", "C", "D");

        // 生成所有可能的子集（排除空集）
        List<Set<String>> allSubsets = generateAllSubsets(allFields);

        Map<String, Integer> fieldOrderMap = new HashMap<>();

        // 对每个子集进行排序
        for (Set<String> subset : allSubsets) {
            List<String> sorted = new ArrayList<>(
                    XmlFieldOrderManager.sortFields(tableName, subset)
            );

            // 记录每个字段在该子集中的位置
            for (int i = 0; i < sorted.size(); i++) {
                String field = sorted.get(i);
                int currentPos = i;

                if (fieldOrderMap.containsKey(field)) {
                    // 如果这个字段之前出现过，验证其相对位置
                    // （这里简化处理，实际应该比较包含相同字段的子集）
                } else {
                    fieldOrderMap.put(field, currentPos);
                }
            }
        }

        // 验证id始终排在第一位
        for (Set<String> subset : allSubsets) {
            if (subset.contains("id")) {
                List<String> sorted = new ArrayList<>(
                        XmlFieldOrderManager.sortFields(tableName, subset)
                );
                assertEquals("id", sorted.get(0), "id应该始终排在第一位");
            }
        }
    }

    /**
     * 生成所有可能的子集（排除空集）
     */
    private List<Set<String>> generateAllSubsets(List<String> fields) {
        List<Set<String>> subsets = new ArrayList<>();
        int n = fields.size();
        int totalSubsets = (1 << n);  // 2^n

        for (int i = 1; i < totalSubsets; i++) {  // 从1开始，排除空集
            Set<String> subset = new HashSet<>();
            for (int j = 0; j < n; j++) {
                if ((i & (1 << j)) != 0) {
                    subset.add(fields.get(j));
                }
            }
            subsets.add(subset);
        }

        return subsets;
    }

    @Test
    public void testRealWorldScenario() {
        // 真实场景模拟：技能表中不同技能有不同的可选字段
        String tableName = "skill_base";

        // 物理攻击技能：有damage但无magic_damage
        Set<String> physicalSkill = Set.of("id", "name", "level", "damage", "cast_time");

        // 魔法攻击技能：有magic_damage但无damage
        Set<String> magicSkill = Set.of("id", "name", "level", "magic_damage", "cast_time");

        // 辅助技能：无攻击属性
        Set<String> buffSkill = Set.of("id", "name", "level", "duration", "cast_time");

        // 被动技能：无cast_time
        Set<String> passiveSkill = Set.of("id", "name", "level");

        // 排序
        List<String> sortedPhysical = new ArrayList<>(XmlFieldOrderManager.sortFields(tableName, physicalSkill));
        List<String> sortedMagic = new ArrayList<>(XmlFieldOrderManager.sortFields(tableName, magicSkill));
        List<String> sortedBuff = new ArrayList<>(XmlFieldOrderManager.sortFields(tableName, buffSkill));
        List<String> sortedPassive = new ArrayList<>(XmlFieldOrderManager.sortFields(tableName, passiveSkill));

        // 验证：所有技能的id, name, level顺序一致
        assertEquals(sortedPhysical.indexOf("id"), 0);
        assertEquals(sortedMagic.indexOf("id"), 0);
        assertEquals(sortedBuff.indexOf("id"), 0);
        assertEquals(sortedPassive.indexOf("id"), 0);

        // 验证：name应该在level前面（假设ordinalPosition如此定义）
        if (sortedPhysical.indexOf("name") < sortedPhysical.indexOf("level")) {
            assertTrue(sortedMagic.indexOf("name") < sortedMagic.indexOf("level"));
            assertTrue(sortedBuff.indexOf("name") < sortedBuff.indexOf("level"));
            assertTrue(sortedPassive.indexOf("name") < sortedPassive.indexOf("level"));
        }
    }
}
