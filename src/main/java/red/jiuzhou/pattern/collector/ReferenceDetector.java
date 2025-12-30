package red.jiuzhou.pattern.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.pattern.dao.PatternFieldDao;
import red.jiuzhou.pattern.dao.PatternRefDao;
import red.jiuzhou.pattern.dao.PatternSchemaDao;
import red.jiuzhou.pattern.model.PatternField;
import red.jiuzhou.pattern.model.PatternRef;
import red.jiuzhou.pattern.model.PatternSchema;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 引用关系检测器
 * 检测字段间的跨表引用关系，复用 XmlFieldParser.REFERENCE_PATTERNS
 */
public class ReferenceDetector {
    private static final Logger log = LoggerFactory.getLogger(ReferenceDetector.class);

    private final PatternSchemaDao schemaDao;
    private final PatternFieldDao fieldDao;
    private final PatternRefDao refDao;

    // 引用模式（复用 XmlFieldParser 的定义）
    private static final Map<Pattern, ReferenceTarget> REFERENCE_PATTERNS = new LinkedHashMap<>();

    static {
        // 物品引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*item[_]?id.*"), new ReferenceTarget("ITEM", "items", "id"));
        REFERENCE_PATTERNS.put(Pattern.compile("(?i)^item$"), new ReferenceTarget("ITEM", "items", "id"));

        // NPC引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*npc[_]?id.*"), new ReferenceTarget("NPC", "npcs", "id"));
        REFERENCE_PATTERNS.put(Pattern.compile("(?i)^npc$"), new ReferenceTarget("NPC", "npcs", "id"));

        // 技能引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*skill[_]?id.*"), new ReferenceTarget("SKILL", "skills", "id"));
        REFERENCE_PATTERNS.put(Pattern.compile("(?i)^skill$"), new ReferenceTarget("SKILL", "skills", "id"));

        // 任务引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*quest[_]?id.*"), new ReferenceTarget("QUEST", "quests", "id"));
        REFERENCE_PATTERNS.put(Pattern.compile("(?i)^quest$"), new ReferenceTarget("QUEST", "quests", "id"));

        // 称号引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*title[_]?id.*"), new ReferenceTarget("TITLE", "titles", "id"));

        // 地图引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*map[_]?id.*"), new ReferenceTarget("MAP", "maps", "id"));
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*world[_]?id.*"), new ReferenceTarget("WORLD", "worlds", "id"));

        // 副本引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*instance[_]?id.*"), new ReferenceTarget("INSTANCE", "instances", "id"));

        // 掉落引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*drop[_]?id.*"), new ReferenceTarget("DROP", "drops", "id"));

        // 怪物引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*monster[_]?id.*"), new ReferenceTarget("NPC", "npcs", "id"));

        // 宠物引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*pet[_]?id.*"), new ReferenceTarget("PET", "pets", "id"));
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*toypet[_]?id.*"), new ReferenceTarget("PET", "toypets", "id"));

        // 效果/Buff引用
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*effect[_]?id.*"), new ReferenceTarget("EFFECT", "effects", "id"));
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*buff[_]?id.*"), new ReferenceTarget("EFFECT", "effects", "id"));

        // 名称引用（通常不是ID）
        REFERENCE_PATTERNS.put(Pattern.compile("(?i).*_name$"), new ReferenceTarget("NAME", null, "name"));
    }

    public ReferenceDetector() {
        this.schemaDao = new PatternSchemaDao();
        this.fieldDao = new PatternFieldDao();
        this.refDao = new PatternRefDao();
    }

    public ReferenceDetector(PatternSchemaDao schemaDao, PatternFieldDao fieldDao, PatternRefDao refDao) {
        this.schemaDao = schemaDao;
        this.fieldDao = fieldDao;
        this.refDao = refDao;
    }

    /**
     * 检测单个字段的引用关系
     */
    public ReferenceDetectionResult detectField(PatternField field) {
        String fieldName = field.getFieldName();

        for (Map.Entry<Pattern, ReferenceTarget> entry : REFERENCE_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(fieldName).matches()) {
                ReferenceTarget target = entry.getValue();
                return new ReferenceDetectionResult(true, target.mechanismCode, target.tableName, target.fieldName, 0.85);
            }
        }

        return new ReferenceDetectionResult(false, null, null, null, 0.0);
    }

    /**
     * 批量检测并保存引用关系
     */
    public int detectAndSaveReferences(Integer schemaId) {
        List<PatternField> fields = fieldDao.findBySchemaId(schemaId);
        int detectedCount = 0;

        for (PatternField field : fields) {
            ReferenceDetectionResult detection = detectField(field);

            if (detection.isReference()) {
                // 查找目标schema
                Integer targetSchemaId = null;
                if (detection.getTargetMechanismCode() != null) {
                    Optional<PatternSchema> targetSchema = schemaDao.findByMechanismCode(detection.getTargetMechanismCode());
                    if (targetSchema.isPresent()) {
                        targetSchemaId = targetSchema.get().getId();
                    }
                }

                // 创建引用关系
                PatternRef ref = new PatternRef(schemaId, field.getId(), field.getFieldName());
                ref.setTargetSchemaId(targetSchemaId);
                ref.setTargetFieldName(detection.getTargetFieldName());
                ref.setTargetTableName(detection.getTargetTableName());
                ref.setRefType(PatternRef.RefType.ID_REFERENCE);
                ref.setConfidence(BigDecimal.valueOf(detection.getConfidence()));

                refDao.saveOrUpdate(ref);
                detectedCount++;
            }
        }

        return detectedCount;
    }

    /**
     * 检测所有schema的引用关系
     */
    public Map<String, Integer> detectAllReferences() {
        Map<String, Integer> results = new LinkedHashMap<>();
        List<PatternSchema> schemas = schemaDao.findAll();

        for (PatternSchema schema : schemas) {
            int count = detectAndSaveReferences(schema.getId());
            if (count > 0) {
                results.put(schema.getMechanismCode(), count);
            }
        }

        return results;
    }

    /**
     * 查找反向引用（谁引用了我）
     */
    public List<PatternRef> findIncomingReferences(Integer schemaId) {
        return refDao.findByTargetSchemaId(schemaId);
    }

    /**
     * 查找正向引用（我引用了谁）
     */
    public List<PatternRef> findOutgoingReferences(Integer schemaId) {
        return refDao.findBySourceSchemaId(schemaId);
    }

    /**
     * 构建引用关系图
     */
    public ReferenceGraph buildReferenceGraph() {
        ReferenceGraph graph = new ReferenceGraph();
        List<PatternRef> allRefs = refDao.findAll();

        for (PatternRef ref : allRefs) {
            graph.addEdge(ref.getSourceSchemaId(), ref.getTargetSchemaId(), ref);
        }

        return graph;
    }

    /**
     * 引用目标
     */
    private static class ReferenceTarget {
        String mechanismCode;
        String tableName;
        String fieldName;

        ReferenceTarget(String mechanismCode, String tableName, String fieldName) {
            this.mechanismCode = mechanismCode;
            this.tableName = tableName;
            this.fieldName = fieldName;
        }
    }

    /**
     * 检测结果
     */
    public static class ReferenceDetectionResult {
        private boolean isReference;
        private String targetMechanismCode;
        private String targetTableName;
        private String targetFieldName;
        private double confidence;

        public ReferenceDetectionResult(boolean isReference, String targetMechanismCode,
                                       String targetTableName, String targetFieldName, double confidence) {
            this.isReference = isReference;
            this.targetMechanismCode = targetMechanismCode;
            this.targetTableName = targetTableName;
            this.targetFieldName = targetFieldName;
            this.confidence = confidence;
        }

        public boolean isReference() { return isReference; }
        public String getTargetMechanismCode() { return targetMechanismCode; }
        public String getTargetTableName() { return targetTableName; }
        public String getTargetFieldName() { return targetFieldName; }
        public double getConfidence() { return confidence; }
    }

    /**
     * 引用关系图
     */
    public static class ReferenceGraph {
        private Map<Integer, List<PatternRef>> outgoingEdges = new HashMap<>();
        private Map<Integer, List<PatternRef>> incomingEdges = new HashMap<>();

        public void addEdge(Integer sourceId, Integer targetId, PatternRef ref) {
            outgoingEdges.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(ref);
            if (targetId != null) {
                incomingEdges.computeIfAbsent(targetId, k -> new ArrayList<>()).add(ref);
            }
        }

        public List<PatternRef> getOutgoingEdges(Integer schemaId) {
            return outgoingEdges.getOrDefault(schemaId, Collections.emptyList());
        }

        public List<PatternRef> getIncomingEdges(Integer schemaId) {
            return incomingEdges.getOrDefault(schemaId, Collections.emptyList());
        }

        public int getTotalEdges() {
            return outgoingEdges.values().stream().mapToInt(List::size).sum();
        }
    }
}
