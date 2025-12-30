package red.jiuzhou.analysis.aion.reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import red.jiuzhou.analysis.aion.AionMechanismDetector;
import red.jiuzhou.analysis.aion.DetectionResult;
import red.jiuzhou.analysis.aion.IdNameResolver;
import red.jiuzhou.util.YamlUtils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 字段引用分析器
 *
 * <p>扫描XML文件，检测所有*_id类型的字段引用关系。
 *
 * <p>设计原则：
 * <ul>
 *   <li>复用现有模式 - 使用IdNameResolver的系统配置</li>
 *   <li>机制感知 - 自动检测源文件所属机制</li>
 *   <li>高效扫描 - 支持多文件并行分析</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class FieldReferenceAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(FieldReferenceAnalyzer.class);

    // ID字段匹配模式 -> 目标系统
    private static final Map<Pattern, String> ID_PATTERNS = new LinkedHashMap<>();

    // 系统名 -> 目标表名
    private static final Map<String, String> SYSTEM_TABLES = new LinkedHashMap<>();

    static {
        // 配置ID字段匹配模式（复用自IdNameResolver）
        ID_PATTERNS.put(Pattern.compile("(?i).*item[_]?id.*"), "物品系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*npc[_]?id.*"), "NPC系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*monster[_]?id.*"), "NPC系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*skill[_]?id.*"), "技能系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*quest[_]?id.*"), "任务系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*instance[_]?id.*"), "副本系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*map[_]?id.*"), "地图系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*world[_]?id.*"), "地图系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*title[_]?id.*"), "称号系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*pet[_]?id.*"), "宠物系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*ride[_]?id.*"), "坐骑系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*mount[_]?id.*"), "坐骑系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*drop[_]?id.*"), "掉落系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*shop[_]?id.*"), "商店系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*goods[_]?id.*"), "商店系统");
        ID_PATTERNS.put(Pattern.compile("(?i).*recipe[_]?id.*"), "配方系统");

        // 配置系统到表的映射（复用自IdNameResolver）
        SYSTEM_TABLES.put("物品系统", "client_items");
        SYSTEM_TABLES.put("NPC系统", "client_npcs_npc");
        SYSTEM_TABLES.put("技能系统", "client_skills");
        SYSTEM_TABLES.put("任务系统", "client_quests");
        SYSTEM_TABLES.put("副本系统", "instance_cooltime");
        SYSTEM_TABLES.put("地图系统", "world");
        SYSTEM_TABLES.put("称号系统", "client_titles");
        SYSTEM_TABLES.put("宠物系统", "client_toypets");
        SYSTEM_TABLES.put("坐骑系统", "client_rides");
        SYSTEM_TABLES.put("掉落系统", "drop_templates");
        SYSTEM_TABLES.put("商店系统", "shop_templates");
        SYSTEM_TABLES.put("配方系统", "recipe_templates");
    }

    // 机制检测器（延迟初始化）
    private AionMechanismDetector mechanismDetector;

    // ID解析器
    private final IdNameResolver idNameResolver;

    public FieldReferenceAnalyzer() {
        this.idNameResolver = IdNameResolver.getInstance();
    }

    /**
     * 获取或创建机制检测器
     */
    private AionMechanismDetector getMechanismDetector() {
        if (mechanismDetector == null) {
            String xmlPath = YamlUtils.getProperty("aion.xmlPath");
            String localizedPath = YamlUtils.getProperty("aion.localizedPath");
            if (xmlPath != null && localizedPath != null) {
                File publicRoot = new File(xmlPath);
                File localizedRoot = new File(localizedPath);
                mechanismDetector = new AionMechanismDetector(publicRoot, localizedRoot);
            }
        }
        return mechanismDetector;
    }

    /**
     * 分析单个XML文件
     *
     * @param xmlFile XML文件
     * @return 引用条目列表
     */
    public List<FieldReferenceEntry> analyzeFile(File xmlFile) {
        List<FieldReferenceEntry> entries = new ArrayList<>();

        try {
            // 解析XML
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);

            // 检测机制分类
            String mechanism = detectMechanism(xmlFile);

            // 收集所有ID字段的值
            Map<String, FieldCollector> collectors = new LinkedHashMap<>();
            Element root = doc.getDocumentElement();
            collectIdFields(root, "", collectors);

            // 转换为引用条目
            for (Map.Entry<String, FieldCollector> entry : collectors.entrySet()) {
                String fieldPath = entry.getKey();
                FieldCollector collector = entry.getValue();

                FieldReferenceEntry refEntry = new FieldReferenceEntry();
                refEntry.setSourceFile(xmlFile.getAbsolutePath());
                refEntry.setSourceField(collector.fieldName);
                refEntry.setSourceFieldPath(fieldPath);
                refEntry.setSourceMechanism(mechanism);
                refEntry.setTargetSystem(collector.targetSystem);
                refEntry.setTargetTable(SYSTEM_TABLES.getOrDefault(collector.targetSystem, "unknown"));
                refEntry.setReferenceCount(collector.values.size());
                refEntry.setDistinctValues(collector.distinctValues.size());

                // 添加样本值
                int sampleCount = 0;
                for (String value : collector.distinctValues) {
                    if (sampleCount >= 10) break;
                    String name = idNameResolver.resolveName(collector.targetSystem, value);
                    refEntry.addSample(value, name);
                    sampleCount++;
                }

                entries.add(refEntry);
            }

        } catch (Exception e) {
            log.warn("分析文件失败: {} - {}", xmlFile.getName(), e.getMessage());
        }

        return entries;
    }

    /**
     * 分析多个XML文件
     *
     * @param xmlFiles XML文件列表
     * @return 分析结果
     */
    public FieldReferenceResult analyzeFiles(List<File> xmlFiles) {
        FieldReferenceResult result = new FieldReferenceResult();
        long startTime = System.currentTimeMillis();

        int processed = 0;
        for (File file : xmlFiles) {
            try {
                List<FieldReferenceEntry> entries = analyzeFile(file);
                result.addAll(entries);
                processed++;

                if (processed % 50 == 0) {
                    log.debug("已分析 {}/{} 个文件", processed, xmlFiles.size());
                }
            } catch (Exception e) {
                log.warn("分析文件异常: {}", file.getName());
            }
        }

        result.setAnalyzedFileCount(processed);
        result.setAnalysisDuration(System.currentTimeMillis() - startTime);

        log.info(result.getSummary());
        return result;
    }

    /**
     * 分析目录下的所有XML文件
     *
     * @param directory 目录
     * @return 分析结果
     */
    public FieldReferenceResult analyzeDirectory(File directory) {
        List<File> xmlFiles = new ArrayList<>();
        collectXmlFiles(directory, xmlFiles);
        return analyzeFiles(xmlFiles);
    }

    /**
     * 递归收集XML文件
     */
    private void collectXmlFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectXmlFiles(file, result);
            } else if (file.getName().toLowerCase().endsWith(".xml")) {
                result.add(file);
            }
        }
    }

    /**
     * 递归收集ID字段
     */
    private void collectIdFields(Node node, String path, Map<String, FieldCollector> collectors) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }

        Element element = (Element) node;
        String currentPath = path.isEmpty() ? element.getTagName() : path + "/" + element.getTagName();

        // 检查属性
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String attrName = attr.getNodeName();
            String attrValue = attr.getNodeValue();

            String targetSystem = detectIdSystem(attrName);
            if (targetSystem != null && isValidIdValue(attrValue)) {
                String fieldPath = currentPath + "@" + attrName;
                FieldCollector collector = collectors.computeIfAbsent(
                        fieldPath, k -> new FieldCollector(attrName, targetSystem));
                collector.addValue(attrValue);
            }
        }

        // 检查子元素（限制深度）
        if (currentPath.split("/").length < 6) {
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                collectIdFields(children.item(i), currentPath, collectors);
            }
        }
    }

    /**
     * 检测字段所属的目标系统
     */
    private String detectIdSystem(String fieldName) {
        if (fieldName == null) return null;

        for (Map.Entry<Pattern, String> entry : ID_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(fieldName).matches()) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 检测文件所属机制
     */
    private String detectMechanism(File xmlFile) {
        try {
            AionMechanismDetector detector = getMechanismDetector();
            if (detector != null) {
                DetectionResult detection = detector.detect(xmlFile, xmlFile.getName(), false);
                if (detection != null && detection.getCategory() != null) {
                    return detection.getCategory().getDisplayName();
                }
            }
        } catch (Exception e) {
            // 忽略检测失败
        }
        return null;
    }

    /**
     * 检查是否是有效的ID值（数字）
     */
    private boolean isValidIdValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        // 去除空白
        value = value.trim();

        // 检查是否是数字
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 获取支持的目标系统列表
     */
    public Set<String> getSupportedSystems() {
        return Collections.unmodifiableSet(SYSTEM_TABLES.keySet());
    }

    /**
     * 获取系统对应的表名
     */
    public String getTableForSystem(String system) {
        return SYSTEM_TABLES.get(system);
    }

    // ========== 内部类 ==========

    /**
     * 字段值收集器
     */
    private static class FieldCollector {
        final String fieldName;
        final String targetSystem;
        final List<String> values = new ArrayList<>();
        final Set<String> distinctValues = new LinkedHashSet<>();

        FieldCollector(String fieldName, String targetSystem) {
            this.fieldName = fieldName;
            this.targetSystem = targetSystem;
        }

        void addValue(String value) {
            values.add(value);
            distinctValues.add(value);
        }
    }
}
