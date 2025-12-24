package red.jiuzhou.dbxml;

import cn.hutool.core.io.FileUtil;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 往返一致性验证工具
 *
 * <p>用于验证 XML → DB → XML 或 DB → XML → DB 操作后数据的一致性。
 *
 * <p><b>设计思想：Round-Trip Consistency</b>
 * <ul>
 *   <li>导入后再导出的XML应与原始XML语义等价</li>
 *   <li>导出后再导入的数据应与原始数据完全一致</li>
 *   <li>容忍格式差异（空白、属性顺序），关注语义一致性</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class RoundTripValidator {

    private static final Logger log = LoggerFactory.getLogger(RoundTripValidator.class);

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private boolean success;
        private int totalElements;
        private int matchedElements;
        private int missingElements;
        private int extraElements;
        private int valueDifferences;
        private List<String> differences;

        public ValidationResult() {
            this.differences = new ArrayList<>();
        }

        public boolean isSuccess() { return success; }
        public int getTotalElements() { return totalElements; }
        public int getMatchedElements() { return matchedElements; }
        public int getMissingElements() { return missingElements; }
        public int getExtraElements() { return extraElements; }
        public int getValueDifferences() { return valueDifferences; }
        public List<String> getDifferences() { return differences; }

        public void setSuccess(boolean success) { this.success = success; }
        public void setTotalElements(int total) { this.totalElements = total; }
        public void setMatchedElements(int matched) { this.matchedElements = matched; }
        public void setMissingElements(int missing) { this.missingElements = missing; }
        public void setExtraElements(int extra) { this.extraElements = extra; }
        public void setValueDifferences(int diffs) { this.valueDifferences = diffs; }

        public void addDifference(String diff) {
            differences.add(diff);
        }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== 往返一致性验证结果 ===\n");
            sb.append(String.format("状态: %s\n", success ? "✓ 通过" : "✗ 失败"));
            sb.append(String.format("总元素: %d, 匹配: %d, 缺失: %d, 多余: %d, 值差异: %d\n",
                    totalElements, matchedElements, missingElements, extraElements, valueDifferences));

            if (!differences.isEmpty()) {
                sb.append("\n差异详情 (最多显示20条):\n");
                int limit = Math.min(differences.size(), 20);
                for (int i = 0; i < limit; i++) {
                    sb.append("  - ").append(differences.get(i)).append("\n");
                }
                if (differences.size() > 20) {
                    sb.append(String.format("  ... 还有 %d 条差异\n", differences.size() - 20));
                }
            }

            return sb.toString();
        }
    }

    /**
     * 比较两个XML文件的语义一致性
     *
     * @param originalFile 原始XML文件
     * @param roundTripFile 往返后的XML文件
     * @return 验证结果
     */
    public static ValidationResult compareXmlFiles(File originalFile, File roundTripFile) {
        ValidationResult result = new ValidationResult();

        try {
            String originalContent = FileUtil.readString(originalFile, StandardCharsets.UTF_16);
            String roundTripContent = FileUtil.readString(roundTripFile, StandardCharsets.UTF_16);

            Document originalDoc = DocumentHelper.parseText(originalContent);
            Document roundTripDoc = DocumentHelper.parseText(roundTripContent);

            compareElements(originalDoc.getRootElement(), roundTripDoc.getRootElement(), "", result);

            result.setSuccess(result.getMissingElements() == 0 &&
                            result.getExtraElements() == 0 &&
                            result.getValueDifferences() == 0);

        } catch (Exception e) {
            log.error("XML比较失败", e);
            result.setSuccess(false);
            result.addDifference("解析错误: " + e.getMessage());
        }

        return result;
    }

    /**
     * 递归比较元素
     */
    private static void compareElements(Element original, Element roundTrip, String path, ValidationResult result) {
        result.setTotalElements(result.getTotalElements() + 1);

        // 比较文本内容
        String origText = original.getTextTrim();
        String rtText = roundTrip.getTextTrim();

        // 处理NULL标记
        boolean origIsNull = "true".equals(original.attributeValue("null"));
        boolean rtIsNull = "true".equals(roundTrip.attributeValue("null"));

        if (origIsNull != rtIsNull) {
            result.setValueDifferences(result.getValueDifferences() + 1);
            result.addDifference(String.format("NULL差异 @ %s: 原=%s, 新=%s",
                    path, origIsNull, rtIsNull));
        } else if (!origIsNull && !Objects.equals(origText, rtText)) {
            result.setValueDifferences(result.getValueDifferences() + 1);
            result.addDifference(String.format("值差异 @ %s: 原=\"%s\", 新=\"%s\"",
                    path, truncate(origText, 50), truncate(rtText, 50)));
        } else {
            result.setMatchedElements(result.getMatchedElements() + 1);
        }

        // 比较属性（跳过null标记）
        for (Iterator<?> it = original.attributeIterator(); it.hasNext(); ) {
            org.dom4j.Attribute attr = (org.dom4j.Attribute) it.next();
            if ("null".equals(attr.getName())) continue;

            String rtAttrValue = roundTrip.attributeValue(attr.getName());
            if (rtAttrValue == null) {
                result.setMissingElements(result.getMissingElements() + 1);
                result.addDifference(String.format("缺失属性 @ %s/@%s", path, attr.getName()));
            } else if (!Objects.equals(attr.getValue(), rtAttrValue)) {
                result.setValueDifferences(result.getValueDifferences() + 1);
                result.addDifference(String.format("属性差异 @ %s/@%s: 原=\"%s\", 新=\"%s\"",
                        path, attr.getName(), attr.getValue(), rtAttrValue));
            }
        }

        // 比较子元素
        Map<String, List<Element>> origChildren = groupByName(original.elements());
        Map<String, List<Element>> rtChildren = groupByName(roundTrip.elements());

        Set<String> allNames = new HashSet<>();
        allNames.addAll(origChildren.keySet());
        allNames.addAll(rtChildren.keySet());

        for (String name : allNames) {
            List<Element> origList = origChildren.getOrDefault(name, Collections.emptyList());
            List<Element> rtList = rtChildren.getOrDefault(name, Collections.emptyList());

            int minSize = Math.min(origList.size(), rtList.size());
            for (int i = 0; i < minSize; i++) {
                String childPath = path + "/" + name + "[" + i + "]";
                compareElements(origList.get(i), rtList.get(i), childPath, result);
            }

            if (origList.size() > rtList.size()) {
                result.setMissingElements(result.getMissingElements() + (origList.size() - rtList.size()));
                result.addDifference(String.format("缺失元素 @ %s/%s: 原=%d个, 新=%d个",
                        path, name, origList.size(), rtList.size()));
            } else if (rtList.size() > origList.size()) {
                result.setExtraElements(result.getExtraElements() + (rtList.size() - origList.size()));
                result.addDifference(String.format("多余元素 @ %s/%s: 原=%d个, 新=%d个",
                        path, name, origList.size(), rtList.size()));
            }
        }
    }

    /**
     * 按元素名分组
     */
    private static Map<String, List<Element>> groupByName(List<?> elements) {
        Map<String, List<Element>> map = new LinkedHashMap<>();
        for (Object obj : elements) {
            Element elem = (Element) obj;
            map.computeIfAbsent(elem.getName(), k -> new ArrayList<>()).add(elem);
        }
        return map;
    }

    /**
     * 截断字符串
     */
    private static String truncate(String str, int maxLen) {
        if (str == null) return "null";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen) + "...";
    }

    /**
     * 快速验证两个XML文件是否语义等价
     *
     * @param file1 第一个XML文件
     * @param file2 第二个XML文件
     * @return true如果等价
     */
    public static boolean areEquivalent(File file1, File file2) {
        ValidationResult result = compareXmlFiles(file1, file2);
        return result.isSuccess();
    }
}
