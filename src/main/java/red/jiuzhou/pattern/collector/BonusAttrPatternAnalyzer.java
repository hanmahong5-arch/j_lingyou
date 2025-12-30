package red.jiuzhou.pattern.collector;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.pattern.dao.AttrDictionaryDao;
import red.jiuzhou.pattern.model.AttrDictionary;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 属性增益模式分析器
 * 分析 bonus_attr1-12, bonus_attr_a1-4, bonus_attr_b1-4 等字段的值模式
 * 提取属性代码和数值，统计使用频率
 */
public class BonusAttrPatternAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(BonusAttrPatternAnalyzer.class);

    private final AttrDictionaryDao attrDao;

    // 属性增益值格式：<attr_code> <value>
    private static final Pattern BONUS_ATTR_PATTERN = Pattern.compile("^([a-zA-Z_]+)\\s+(-?\\d+(?:\\.\\d+)?)$");

    // 已知的属性增益槽位模式
    private static final List<Pattern> SLOT_PATTERNS = new ArrayList<>();
    static {
        SLOT_PATTERNS.add(Pattern.compile("^bonus_attr(\\d+)$"));              // bonus_attr1-12
        SLOT_PATTERNS.add(Pattern.compile("^bonus_attr_([a-z])(\\d+)$"));      // bonus_attr_a1-4
        SLOT_PATTERNS.add(Pattern.compile("^physical_bonus_attr(\\d+)$"));     // physical_bonus_attr1-4
        SLOT_PATTERNS.add(Pattern.compile("^magical_bonus_attr(\\d+)$"));      // magical_bonus_attr1-4
    }

    public BonusAttrPatternAnalyzer() {
        this.attrDao = new AttrDictionaryDao();
    }

    public BonusAttrPatternAnalyzer(AttrDictionaryDao attrDao) {
        this.attrDao = attrDao;
    }

    /**
     * 分析单个属性增益值
     */
    public BonusAttrValue parseValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = BONUS_ATTR_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            log.debug("无法解析属性增益值: {}", value);
            return null;
        }

        String attrCode = matcher.group(1);
        String numStr = matcher.group(2);

        try {
            double numValue = Double.parseDouble(numStr);
            return new BonusAttrValue(attrCode, numValue, value);
        } catch (NumberFormatException e) {
            log.warn("数值解析失败: {}", value);
            return null;
        }
    }

    /**
     * 分析一组属性增益字段
     * @param bonusAttrMap 字段名 -> 值
     * @return 解析结果列表
     */
    public List<BonusAttrValue> analyzeGroup(Map<String, String> bonusAttrMap) {
        List<BonusAttrValue> results = new ArrayList<>();

        for (Map.Entry<String, String> entry : bonusAttrMap.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();

            if (!isBonusAttrField(fieldName)) {
                continue;
            }

            BonusAttrValue parsed = parseValue(value);
            if (parsed != null) {
                parsed.setSlotName(fieldName);
                results.add(parsed);
            }
        }

        return results;
    }

    /**
     * 统计属性使用情况
     */
    public void updateAttrUsageStats(List<BonusAttrValue> bonusAttrs, String usageType) {
        Map<String, Integer> attrCounts = new HashMap<>();

        // 统计每个属性的使用次数
        for (BonusAttrValue attr : bonusAttrs) {
            String code = attr.getAttrCode();
            attrCounts.put(code, attrCounts.getOrDefault(code, 0) + 1);
        }

        // 更新数据库
        for (Map.Entry<String, Integer> entry : attrCounts.entrySet()) {
            String attrCode = entry.getKey();
            int count = entry.getValue();

            // 检查属性是否存在
            if (!attrDao.existsByAttrCode(attrCode)) {
                // 创建新属性记录
                AttrDictionary newAttr = new AttrDictionary(attrCode);
                newAttr.setAttrCategory(AttrDictionary.inferCategory(attrCode).getDisplayName());
                attrDao.saveOrUpdate(newAttr);
            }

            // 更新使用计数
            for (int i = 0; i < count; i++) {
                attrDao.incrementUsage(attrCode, usageType);
            }
        }
    }

    /**
     * 分析属性值范围
     */
    public BonusAttrStatistics analyzeValueRange(List<BonusAttrValue> bonusAttrs) {
        BonusAttrStatistics stats = new BonusAttrStatistics();

        Map<String, List<Double>> valuesByAttr = new HashMap<>();

        for (BonusAttrValue attr : bonusAttrs) {
            String code = attr.getAttrCode();
            valuesByAttr.computeIfAbsent(code, k -> new ArrayList<>()).add(attr.getValue());
        }

        for (Map.Entry<String, List<Double>> entry : valuesByAttr.entrySet()) {
            String attrCode = entry.getKey();
            List<Double> values = entry.getValue();

            if (values.isEmpty()) continue;

            Collections.sort(values);
            double min = values.get(0);
            double max = values.get(values.size() - 1);
            double avg = values.stream().mapToDouble(v -> v).average().orElse(0);

            stats.addAttrRange(attrCode, min, max, avg);
        }

        return stats;
    }

    /**
     * 生成属性组合摘要
     */
    public String generateSummary(List<BonusAttrValue> bonusAttrs) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", bonusAttrs.size());

        Map<String, Integer> attrFreq = new HashMap<>();
        for (BonusAttrValue attr : bonusAttrs) {
            attrFreq.put(attr.getAttrCode(), attrFreq.getOrDefault(attr.getAttrCode(), 0) + 1);
        }
        summary.put("attributes", attrFreq);

        return JSON.toJSONString(summary);
    }

    /**
     * 判断是否为属性增益字段
     */
    public boolean isBonusAttrField(String fieldName) {
        if (fieldName == null) return false;

        for (Pattern pattern : SLOT_PATTERNS) {
            if (pattern.matcher(fieldName).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 提取槽位信息
     */
    public String extractSlotInfo(String fieldName) {
        for (Pattern pattern : SLOT_PATTERNS) {
            Matcher matcher = pattern.matcher(fieldName);
            if (matcher.matches()) {
                if (matcher.groupCount() == 1) {
                    return "slot_" + matcher.group(1);
                } else if (matcher.groupCount() == 2) {
                    return "slot_" + matcher.group(1) + matcher.group(2);
                }
            }
        }
        return null;
    }

    /**
     * 属性增益值对象
     */
    public static class BonusAttrValue {
        private String attrCode;
        private double value;
        private String rawValue;
        private String slotName;

        public BonusAttrValue(String attrCode, double value, String rawValue) {
            this.attrCode = attrCode;
            this.value = value;
            this.rawValue = rawValue;
        }

        public String getAttrCode() { return attrCode; }
        public void setAttrCode(String attrCode) { this.attrCode = attrCode; }

        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }

        public String getRawValue() { return rawValue; }
        public void setRawValue(String rawValue) { this.rawValue = rawValue; }

        public String getSlotName() { return slotName; }
        public void setSlotName(String slotName) { this.slotName = slotName; }

        @Override
        public String toString() {
            return String.format("%s: %s = %.2f", slotName, attrCode, value);
        }
    }

    /**
     * 属性统计信息
     */
    public static class BonusAttrStatistics {
        private Map<String, AttrRange> attrRanges = new LinkedHashMap<>();

        public void addAttrRange(String attrCode, double min, double max, double avg) {
            attrRanges.put(attrCode, new AttrRange(min, max, avg));
        }

        public Map<String, AttrRange> getAttrRanges() {
            return attrRanges;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("属性值域统计:\n");
            for (Map.Entry<String, AttrRange> entry : attrRanges.entrySet()) {
                sb.append(String.format("  %s: %s\n", entry.getKey(), entry.getValue()));
            }
            return sb.toString();
        }
    }

    /**
     * 属性值域
     */
    public static class AttrRange {
        private double min;
        private double max;
        private double avg;

        public AttrRange(double min, double max, double avg) {
            this.min = min;
            this.max = max;
            this.avg = avg;
        }

        public double getMin() { return min; }
        public double getMax() { return max; }
        public double getAvg() { return avg; }

        @Override
        public String toString() {
            return String.format("[%.2f - %.2f], avg=%.2f", min, max, avg);
        }
    }
}
