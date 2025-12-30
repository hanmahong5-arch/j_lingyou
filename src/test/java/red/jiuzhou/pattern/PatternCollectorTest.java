package red.jiuzhou.pattern;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.pattern.collector.*;
import red.jiuzhou.pattern.dao.AttrDictionaryDao;
import red.jiuzhou.pattern.dao.PatternSchemaDao;
import red.jiuzhou.pattern.model.AttrDictionary;
import red.jiuzhou.pattern.model.PatternSchema;

import java.util.List;

/**
 * 模式收集器测试
 */
public class PatternCollectorTest {
    private static final Logger log = LoggerFactory.getLogger(PatternCollectorTest.class);

    @Test
    public void testAttrDictionaryCollection() {
        log.info("=== 测试属性词典收集 ===");

        AttrDictionaryCollector collector = new AttrDictionaryCollector();
        AttrDictionaryCollector.CollectionResult result = collector.collectFromXml();

        log.info("收集结果: {}", result);

        if (result.isSuccess()) {
            // 验证数据
            AttrDictionaryDao dao = new AttrDictionaryDao();
            int count = dao.count();
            log.info("数据库中的属性数量: {}", count);

            // 显示前10个属性
            List<AttrDictionary> attrs = dao.findAll();
            log.info("前10个属性:");
            for (int i = 0; i < Math.min(10, attrs.size()); i++) {
                log.info("  {}", attrs.get(i));
            }
        }
    }

    @Test
    public void testPatternSchemaInit() {
        log.info("=== 测试模式分类初始化 ===");

        PatternCollectorService service = new PatternCollectorService();

        // 测试初始化27个机制
        PatternSchemaDao dao = new PatternSchemaDao();
        List<PatternSchema> schemas = dao.findAll();

        log.info("机制分类数量: {}", schemas.size());
        for (PatternSchema schema : schemas) {
            log.info("  {} - {} {}", schema.getMechanismCode(), schema.getMechanismIcon(), schema.getMechanismName());
        }
    }

    @Test
    public void testFieldTypeInference() {
        log.info("=== 测试字段类型推断 ===");

        FieldTypeInferrer inferrer = new FieldTypeInferrer();

        // 测试各种字段名
        String[] testFields = {
            "item_id",
            "npc_id",
            "bonus_attr1",
            "bonus_attr_a2",
            "is_active",
            "can_fly",
            "item_type",
            "level",
            "name"
        };

        for (String fieldName : testFields) {
            FieldTypeInferrer.InferenceResult result = inferrer.inferFromName(fieldName);
            log.info("字段: {} -> 类型: {}, 置信度: {}",
                    fieldName, result.getFieldType(), result.getConfidence());
        }
    }

    @Test
    public void testBonusAttrAnalyzer() {
        log.info("=== 测试属性增益分析器 ===");

        BonusAttrPatternAnalyzer analyzer = new BonusAttrPatternAnalyzer();

        // 测试解析
        String[] testValues = {
            "max_hp 500",
            "physical_attack 120",
            "magical_defend 80",
            "pvp_attack_ratio_physical 15"
        };

        for (String value : testValues) {
            BonusAttrPatternAnalyzer.BonusAttrValue result = analyzer.parseValue(value);
            if (result != null) {
                log.info("解析: {} -> 属性: {}, 值: {}",
                        value, result.getAttrCode(), result.getValue());
            }
        }

        // 测试槽位识别
        String[] slotNames = {
            "bonus_attr1",
            "bonus_attr12",
            "bonus_attr_a1",
            "physical_bonus_attr3",
            "magical_bonus_attr2"
        };

        for (String slotName : slotNames) {
            boolean isSlot = analyzer.isBonusAttrField(slotName);
            String slotInfo = analyzer.extractSlotInfo(slotName);
            log.info("槽位: {} -> 是否属性槽: {}, 槽位信息: {}",
                    slotName, isSlot, slotInfo);
        }
    }

    @Test
    public void testReferenceDetector() {
        log.info("=== 测试引用关系检测 ===");

        ReferenceDetector detector = new ReferenceDetector();

        String[] testFields = {
            "item_id",
            "npc_id",
            "skill_id",
            "quest_id",
            "map_id",
            "target_item_id",
            "reward_item1"
        };

        for (String fieldName : testFields) {
            red.jiuzhou.pattern.model.PatternField mockField = new red.jiuzhou.pattern.model.PatternField();
            mockField.setFieldName(fieldName);

            ReferenceDetector.ReferenceDetectionResult result = detector.detectField(mockField);
            if (result.isReference()) {
                log.info("字段: {} -> 引用目标: {}.{}, 置信度: {}",
                        fieldName,
                        result.getTargetTableName(),
                        result.getTargetFieldName(),
                        result.getConfidence());
            }
        }
    }

    @Test
    public void testValueDomainAnalyzer() {
        log.info("=== 测试值域统计分析 ===");

        ValueDomainAnalyzer analyzer = new ValueDomainAnalyzer();

        // 测试数值类型
        List<String> numericValues = java.util.Arrays.asList(
            "100", "200", "150", "180", "100", "200", "150"
        );

        ValueDomainAnalyzer.ValueDomainStatistics stats1 = analyzer.analyzeField(1, numericValues);
        log.info("数值类型分析: {}", stats1);

        // 测试枚举类型
        List<String> enumValues = java.util.Arrays.asList(
            "NORMAL", "RARE", "EPIC", "NORMAL", "RARE", "NORMAL"
        );

        ValueDomainAnalyzer.ValueDomainStatistics stats2 = analyzer.analyzeField(2, enumValues);
        log.info("枚举类型分析: {}", stats2);

        // 测试混合类型
        List<String> mixedValues = java.util.Arrays.asList(
            "item1", "item2", "item1", "npc3", "item1", "item2"
        );

        ValueDomainAnalyzer.ValueDomainStatistics stats3 = analyzer.analyzeField(3, mixedValues);
        log.info("混合类型分析: {}", stats3);
    }
}
