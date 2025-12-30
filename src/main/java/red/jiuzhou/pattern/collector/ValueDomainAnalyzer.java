package red.jiuzhou.pattern.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.pattern.dao.PatternFieldDao;
import red.jiuzhou.pattern.dao.PatternValueDao;
import red.jiuzhou.pattern.model.PatternField;
import red.jiuzhou.pattern.model.PatternValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 值域统计分析器
 * 分析字段值的分布、频率、范围等统计特征
 */
public class ValueDomainAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(ValueDomainAnalyzer.class);

    private final PatternFieldDao fieldDao;
    private final PatternValueDao valueDao;

    // 枚举值阈值
    private int maxEnumValues = 20;          // 最多枚举值数量
    private double minEnumOccurrence = 0.05; // 最小出现频率（5%）

    public ValueDomainAnalyzer() {
        this.fieldDao = new PatternFieldDao();
        this.valueDao = new PatternValueDao();
    }

    public ValueDomainAnalyzer(PatternFieldDao fieldDao, PatternValueDao valueDao) {
        this.fieldDao = fieldDao;
        this.valueDao = valueDao;
    }

    /**
     * 分析字段值域
     */
    public ValueDomainStatistics analyzeField(Integer fieldId, List<String> values) {
        ValueDomainStatistics stats = new ValueDomainStatistics(fieldId);

        if (values == null || values.isEmpty()) {
            return stats;
        }

        // 过滤空值
        List<String> nonNullValues = values.stream()
                .filter(v -> v != null && !v.trim().isEmpty())
                .collect(Collectors.toList());

        int totalCount = values.size();
        int nullCount = totalCount - nonNullValues.size();
        stats.setTotalCount(totalCount);
        stats.setNullCount(nullCount);
        stats.setNullRate(totalCount > 0 ? (double) nullCount / totalCount : 0);

        if (nonNullValues.isEmpty()) {
            return stats;
        }

        // 统计值分布
        Map<String, Integer> valueCounts = new HashMap<>();
        for (String v : nonNullValues) {
            valueCounts.merge(v, 1, Integer::sum);
        }

        stats.setDistinctCount(valueCounts.size());
        stats.setDistinctRate((double) valueCounts.size() / nonNullValues.size());

        // 按频率排序
        List<Map.Entry<String, Integer>> sortedEntries = valueCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .collect(Collectors.toList());

        // 保存值分布（Top 100）
        int limit = Math.min(100, sortedEntries.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> entry = sortedEntries.get(i);
            PatternValue pv = new PatternValue(fieldId, entry.getKey());
            pv.setOccurrenceCount(entry.getValue());
            pv.setPercentage(BigDecimal.valueOf((double) entry.getValue() / nonNullValues.size())
                    .setScale(4, RoundingMode.HALF_UP));

            valueDao.saveOrIncrement(pv);
            stats.addTopValue(entry.getKey(), entry.getValue());
        }

        // 判断是否为枚举类型
        boolean isEnum = valueCounts.size() <= maxEnumValues &&
                        valueCounts.size() < nonNullValues.size() * 0.5;
        stats.setLikelyEnum(isEnum);

        if (isEnum) {
            stats.setEnumValues(new ArrayList<>(valueCounts.keySet()));
        }

        // 数值范围分析
        analyzeNumericRange(nonNullValues, stats);

        return stats;
    }

    /**
     * 分析数值范围
     */
    private void analyzeNumericRange(List<String> values, ValueDomainStatistics stats) {
        List<Double> numericValues = new ArrayList<>();
        int numericCount = 0;

        for (String v : values) {
            try {
                double num = Double.parseDouble(v);
                numericValues.add(num);
                numericCount++;
            } catch (NumberFormatException ignored) {
            }
        }

        if (numericCount == 0) {
            return;
        }

        // 全都是数值
        if (numericCount == values.size()) {
            stats.setAllNumeric(true);
            Collections.sort(numericValues);

            double min = numericValues.get(0);
            double max = numericValues.get(numericValues.size() - 1);
            double sum = numericValues.stream().mapToDouble(v -> v).sum();
            double avg = sum / numericValues.size();

            stats.setMinValue(min);
            stats.setMaxValue(max);
            stats.setAvgValue(avg);

            // 判断是否全是整数
            boolean allInteger = numericValues.stream().allMatch(v -> v == v.intValue());
            stats.setAllInteger(allInteger);
        }
    }

    /**
     * 更新字段统计信息
     */
    public void updateFieldStatistics(Integer fieldId, ValueDomainStatistics stats) {
        Optional<PatternField> fieldOpt = fieldDao.findById(fieldId);
        if (!fieldOpt.isPresent()) {
            return;
        }

        PatternField field = fieldOpt.get();
        field.setTotalCount(stats.getTotalCount());
        field.setDistinctCount(stats.getDistinctCount());
        field.setNullRate(BigDecimal.valueOf(stats.getNullRate()).setScale(4, RoundingMode.HALF_UP));

        if (stats.isAllNumeric()) {
            field.setValueMin(String.valueOf(stats.getMinValue()));
            field.setValueMax(String.valueOf(stats.getMaxValue()));

            if (stats.isAllInteger()) {
                field.setInferredType(PatternField.FieldType.INTEGER);
            } else {
                field.setInferredType(PatternField.FieldType.DECIMAL);
            }
            field.setValueDomainType(PatternField.ValueDomainType.RANGE);
        }

        if (stats.isLikelyEnum() && stats.getEnumValues() != null) {
            field.setInferredType(PatternField.FieldType.ENUM);
            field.setValueDomainType(PatternField.ValueDomainType.ENUM);
            field.setValueEnum(String.join(",", stats.getEnumValues()));
        }

        fieldDao.update(field);

        // 重新计算百分比
        valueDao.recalculatePercentages(fieldId);
    }

    /**
     * 批量分析并更新
     */
    public int analyzeAndUpdateAllFields(Integer schemaId) {
        List<PatternField> fields = fieldDao.findBySchemaId(schemaId);
        int analyzedCount = 0;

        for (PatternField field : fields) {
            try {
                // 从样本值中分析
                String sampleValuesJson = field.getSampleValues();
                if (sampleValuesJson != null && !sampleValuesJson.isEmpty()) {
                    List<String> sampleValues = com.alibaba.fastjson.JSON.parseArray(sampleValuesJson, String.class);
                    if (sampleValues != null && !sampleValues.isEmpty()) {
                        ValueDomainStatistics stats = analyzeField(field.getId(), sampleValues);
                        updateFieldStatistics(field.getId(), stats);
                        analyzedCount++;
                    }
                }
            } catch (Exception e) {
                log.warn("分析字段失败: {} - {}", field.getFieldName(), e.getMessage());
            }
        }

        return analyzedCount;
    }

    /**
     * 获取字段的Top值
     */
    public List<PatternValue> getTopValues(Integer fieldId, int limit) {
        return valueDao.findTopByFieldId(fieldId, limit);
    }

    /**
     * 设置参数
     */
    public void setParameters(int maxEnumValues, double minEnumOccurrence) {
        this.maxEnumValues = maxEnumValues;
        this.minEnumOccurrence = minEnumOccurrence;
    }

    /**
     * 值域统计结果
     */
    public static class ValueDomainStatistics {
        private Integer fieldId;
        private int totalCount;
        private int nullCount;
        private double nullRate;
        private int distinctCount;
        private double distinctRate;

        private boolean likelyEnum;
        private List<String> enumValues;

        private boolean allNumeric;
        private boolean allInteger;
        private double minValue;
        private double maxValue;
        private double avgValue;

        private Map<String, Integer> topValues = new LinkedHashMap<>();

        public ValueDomainStatistics(Integer fieldId) {
            this.fieldId = fieldId;
        }

        public void addTopValue(String value, int count) {
            topValues.put(value, count);
        }

        // Getters and Setters
        public Integer getFieldId() { return fieldId; }

        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

        public int getNullCount() { return nullCount; }
        public void setNullCount(int nullCount) { this.nullCount = nullCount; }

        public double getNullRate() { return nullRate; }
        public void setNullRate(double nullRate) { this.nullRate = nullRate; }

        public int getDistinctCount() { return distinctCount; }
        public void setDistinctCount(int distinctCount) { this.distinctCount = distinctCount; }

        public double getDistinctRate() { return distinctRate; }
        public void setDistinctRate(double distinctRate) { this.distinctRate = distinctRate; }

        public boolean isLikelyEnum() { return likelyEnum; }
        public void setLikelyEnum(boolean likelyEnum) { this.likelyEnum = likelyEnum; }

        public List<String> getEnumValues() { return enumValues; }
        public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }

        public boolean isAllNumeric() { return allNumeric; }
        public void setAllNumeric(boolean allNumeric) { this.allNumeric = allNumeric; }

        public boolean isAllInteger() { return allInteger; }
        public void setAllInteger(boolean allInteger) { this.allInteger = allInteger; }

        public double getMinValue() { return minValue; }
        public void setMinValue(double minValue) { this.minValue = minValue; }

        public double getMaxValue() { return maxValue; }
        public void setMaxValue(double maxValue) { this.maxValue = maxValue; }

        public double getAvgValue() { return avgValue; }
        public void setAvgValue(double avgValue) { this.avgValue = avgValue; }

        public Map<String, Integer> getTopValues() { return topValues; }

        @Override
        public String toString() {
            return String.format("ValueDomainStats{total=%d, distinct=%d, nullRate=%.2f%%, enum=%s, numeric=%s}",
                    totalCount, distinctCount, nullRate * 100, likelyEnum, allNumeric);
        }
    }
}
