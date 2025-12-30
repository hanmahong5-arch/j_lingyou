package red.jiuzhou.pattern.collector;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.pattern.dao.PatternSampleDao;
import red.jiuzhou.pattern.model.PatternSample;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 样本数据提取器
 * 从XML文件中提取真实配置样本，用于模板生成和数据分析
 */
public class SampleExtractor {
    private static final Logger log = LoggerFactory.getLogger(SampleExtractor.class);

    private final PatternSampleDao sampleDao;
    private final BonusAttrPatternAnalyzer bonusAttrAnalyzer;

    // 采样策略
    private int maxSamplesPerFile = 10;      // 每个文件最多采样数量
    private double samplingRate = 0.1;       // 采样率（10%）

    public SampleExtractor() {
        this.sampleDao = new PatternSampleDao();
        this.bonusAttrAnalyzer = new BonusAttrPatternAnalyzer();
    }

    public SampleExtractor(PatternSampleDao sampleDao) {
        this.sampleDao = sampleDao;
        this.bonusAttrAnalyzer = new BonusAttrPatternAnalyzer();
    }

    /**
     * 从文件中提取样本
     */
    public List<PatternSample> extractFromFile(File file, Integer schemaId) {
        List<PatternSample> samples = new ArrayList<>();

        try {
            String content = FileUtil.readString(file, StandardCharsets.UTF_16);
            Document document = DocumentHelper.parseText(content);
            Element root = document.getRootElement();

            List<Element> items = root.elements();
            if (items.isEmpty()) {
                return samples;
            }

            // 采样
            int totalItems = items.size();
            int sampleCount = Math.min(maxSamplesPerFile, (int) Math.ceil(totalItems * samplingRate));
            if (sampleCount < 1) sampleCount = 1;

            // 等间隔采样
            int step = Math.max(1, totalItems / sampleCount);
            for (int i = 0; i < totalItems && samples.size() < sampleCount; i += step) {
                Element item = items.get(i);
                PatternSample sample = extractSample(item, file, schemaId);
                if (sample != null) {
                    samples.add(sample);
                }
            }

        } catch (Exception e) {
            log.warn("提取样本失败: {} - {}", file.getName(), e.getMessage());
        }

        return samples;
    }

    /**
     * 从单个元素提取样本
     */
    private PatternSample extractSample(Element element, File sourceFile, Integer schemaId) {
        PatternSample sample = new PatternSample(schemaId, sourceFile.getAbsolutePath());
        sample.setSourceFileName(sourceFile.getName());

        // 提取记录标识
        String id = extractId(element);
        String name = extractName(element);
        sample.setRecordId(id);
        sample.setRecordName(name);

        // 保存原始XML
        sample.setRawXml(element.asXML());

        // 转换为JSON
        Map<String, Object> jsonData = elementToMap(element);
        sample.setParsedJson(JSON.toJSONString(jsonData));

        // 分析属性增益
        Map<String, String> bonusAttrFields = extractBonusAttrFields(element);
        if (!bonusAttrFields.isEmpty()) {
            sample.setHasBonusAttr(true);
            sample.setBonusAttrCount(bonusAttrFields.size());

            List<BonusAttrPatternAnalyzer.BonusAttrValue> bonusAttrs = bonusAttrAnalyzer.analyzeGroup(bonusAttrFields);
            sample.setBonusAttrSummary(bonusAttrAnalyzer.generateSummary(bonusAttrs));
        }

        // 评估模板适用性
        BigDecimal score = evaluateTemplateScore(jsonData, bonusAttrFields.size());
        sample.setTemplateScore(score);
        sample.setIsTemplateCandidate(score.compareTo(new BigDecimal("0.7")) >= 0);

        return sample;
    }

    /**
     * 提取ID
     */
    private String extractId(Element element) {
        // 优先顺序：id, item_id, npc_id, skill_id, quest_id
        String[] idFields = {"id", "item_id", "npc_id", "skill_id", "quest_id", "map_id", "world_id"};

        for (String field : idFields) {
            Element idElem = element.element(field);
            if (idElem != null && !idElem.getTextTrim().isEmpty()) {
                return idElem.getTextTrim();
            }
        }

        // 检查属性
        org.dom4j.Attribute idAttr = element.attribute("id");
        if (idAttr != null) {
            return idAttr.getValue();
        }

        return null;
    }

    /**
     * 提取名称
     */
    private String extractName(Element element) {
        Element nameElem = element.element("name");
        if (nameElem != null && !nameElem.getTextTrim().isEmpty()) {
            return nameElem.getTextTrim();
        }

        // 检查其他可能的名称字段
        String[] nameFields = {"title", "desc", "description"};
        for (String field : nameFields) {
            Element elem = element.element(field);
            if (elem != null && !elem.getTextTrim().isEmpty()) {
                return elem.getTextTrim();
            }
        }

        return null;
    }

    /**
     * 提取属性增益字段
     */
    private Map<String, String> extractBonusAttrFields(Element element) {
        Map<String, String> bonusAttrs = new LinkedHashMap<>();

        List<Element> children = element.elements();
        for (Element child : children) {
            String fieldName = child.getName();
            if (bonusAttrAnalyzer.isBonusAttrField(fieldName)) {
                String value = child.getTextTrim();
                if (!value.isEmpty()) {
                    bonusAttrs.put(fieldName, value);
                }
            }
        }

        return bonusAttrs;
    }

    /**
     * 元素转Map
     */
    private Map<String, Object> elementToMap(Element element) {
        Map<String, Object> map = new LinkedHashMap<>();

        // 处理属性
        element.attributeIterator().forEachRemaining(attr -> {
            map.put("@" + attr.getName(), attr.getValue());
        });

        // 处理子元素
        List<Element> children = element.elements();
        for (Element child : children) {
            String name = child.getName();
            if (child.elements().isEmpty()) {
                // 叶子节点
                map.put(name, child.getTextTrim());
            } else {
                // 非叶子节点，递归
                map.put(name, elementToMap(child));
            }
        }

        return map;
    }

    /**
     * 评估模板适用性评分
     */
    private BigDecimal evaluateTemplateScore(Map<String, Object> data, int bonusAttrCount) {
        double score = 0.5; // 基础分

        // 字段完整性（字段数量）
        int fieldCount = data.size();
        if (fieldCount > 20) score += 0.1;
        if (fieldCount > 50) score += 0.1;

        // 有属性增益加分
        if (bonusAttrCount > 0) score += 0.1;
        if (bonusAttrCount > 3) score += 0.1;

        // 有ID和名称加分
        boolean hasId = data.containsKey("id") || data.containsKey("item_id") ||
                       data.containsKey("npc_id") || data.containsKey("skill_id");
        boolean hasName = data.containsKey("name");

        if (hasId) score += 0.05;
        if (hasName) score += 0.05;

        return BigDecimal.valueOf(Math.min(1.0, score));
    }

    /**
     * 批量提取并保存样本
     */
    public int extractAndSaveSamples(List<File> files, Integer schemaId, int maxTotalSamples) {
        int totalExtracted = 0;

        for (File file : files) {
            if (totalExtracted >= maxTotalSamples) {
                break;
            }

            List<PatternSample> samples = extractFromFile(file, schemaId);
            sampleDao.batchInsert(samples);
            totalExtracted += samples.size();
        }

        return totalExtracted;
    }

    /**
     * 设置采样参数
     */
    public void setSamplingParameters(int maxSamplesPerFile, double samplingRate) {
        this.maxSamplesPerFile = maxSamplesPerFile;
        this.samplingRate = samplingRate;
    }

    /**
     * 查找最佳模板候选
     */
    public List<PatternSample> findBestTemplateCandidates(Integer schemaId, int limit) {
        return sampleDao.findBySchemaId(schemaId, limit);
    }

    /**
     * 统计样本中的属性使用情况
     */
    public Map<String, Integer> analyzeAttrUsageInSamples(Integer schemaId) {
        Map<String, Integer> attrUsage = new HashMap<>();
        List<PatternSample> samples = sampleDao.findWithBonusAttr(schemaId);

        for (PatternSample sample : samples) {
            if (sample.getBonusAttrSummary() != null) {
                try {
                    Map<String, Object> summary = JSON.parseObject(sample.getBonusAttrSummary());
                    Map<String, Integer> attrs = (Map<String, Integer>) summary.get("attributes");
                    if (attrs != null) {
                        for (Map.Entry<String, Integer> entry : attrs.entrySet()) {
                            attrUsage.merge(entry.getKey(), entry.getValue(), Integer::sum);
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析样本摘要失败: {}", e.getMessage());
                }
            }
        }

        return attrUsage;
    }
}
