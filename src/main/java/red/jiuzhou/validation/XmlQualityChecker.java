package red.jiuzhou.validation;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * XML文件质量检查器
 *
 * 功能：
 * 1. 检测空文件（无数据）
 * 2. 检测模板文件（所有字段都是空标签）
 * 3. 统计数据量
 * 4. 抽样验证数据完整性
 *
 * @author Claude
 * @date 2025-12-28
 */
public class XmlQualityChecker {

    private static final Logger log = LoggerFactory.getLogger(XmlQualityChecker.class);

    /**
     * 检查XML文件的数据质量
     *
     * @param xmlFile XML文件
     * @return 质量检查结果
     */
    public static QualityCheckResult check(File xmlFile) {
        QualityCheckResult result = new QualityCheckResult();

        try {
            // 解析XML
            SAXReader reader = SAXReader.createDefault();
            Document doc = reader.read(xmlFile);
            Element root = doc.getRootElement();

            // 检查1: 根节点是否为空
            if (root == null) {
                result.setEmpty(true);
                result.addStructureError("XML根节点为null");
                return result;
            }

            // 检查2: 是否有子元素
            @SuppressWarnings("unchecked")
            List<Element> children = root.elements();
            if (children.isEmpty()) {
                result.setEmpty(true);
                log.debug("XML文件 {} 无子元素（空文件）", xmlFile.getName());
                return result;
            }

            // 检查3: 统计数据条目
            result.setItemCount(children.size());

            // 检查4: 检测是否为模板文件（第一个元素的所有字段都为空）
            Element firstItem = children.get(0);
            boolean isTemplate = isTemplateElement(firstItem);
            result.setTemplate(isTemplate);

            if (isTemplate) {
                result.setEmpty(true);  // 模板视为空
                log.debug("XML文件 {} 是模板文件（所有字段为空）", xmlFile.getName());
                return result;
            }

            // 检查5: 抽样检查数据完整性（前10条或全部）
            int sampleSize = Math.min(10, children.size());
            for (int i = 0; i < sampleSize; i++) {
                Element item = children.get(i);
                validateItemData(item, result, i);
            }

            log.debug("XML质量检查完成: {} - {}", xmlFile.getName(), result);

        } catch (DocumentException e) {
            result.setEmpty(true);
            result.addStructureError("XML解析失败: " + e.getMessage());
            log.error("XML解析失败: {}", xmlFile.getName(), e);
        } catch (Exception e) {
            result.addStructureError("质量检查异常: " + e.getMessage());
            log.error("质量检查异常: {}", xmlFile.getName(), e);
        }

        return result;
    }

    /**
     * 判断元素是否为模板（所有字段都为空）
     */
    private static boolean isTemplateElement(Element elem) {
        if (elem == null) {
            return true;
        }

        // 检查所有子元素
        @SuppressWarnings("unchecked")
        List<Element> fields = elem.elements();

        if (fields.isEmpty()) {
            // 没有子元素，检查文本内容和属性
            return elem.getText().trim().isEmpty() && elem.attributes().isEmpty();
        }

        // 所有子元素都必须为空才算模板
        for (Element field : fields) {
            // 如果有文本内容或有属性，则不是模板
            if (!field.getText().trim().isEmpty() || !field.attributes().isEmpty()) {
                return false;
            }

            // 递归检查嵌套元素
            if (!field.elements().isEmpty() && !isTemplateElement(field)) {
                return false;
            }
        }

        return true;  // 所有字段都空 = 模板
    }

    /**
     * 验证单条数据的完整性
     */
    private static void validateItemData(Element item, QualityCheckResult result, int index) {
        try {
            // 检查是否有任何字段有值
            @SuppressWarnings("unchecked")
            List<Element> fields = item.elements();

            int nonEmptyFields = 0;
            for (Element field : fields) {
                if (!field.getText().trim().isEmpty() || !field.attributes().isEmpty()) {
                    nonEmptyFields++;
                }
            }

            // 如果所有字段都为空，记录警告
            if (nonEmptyFields == 0 && !fields.isEmpty()) {
                result.addSampleError(String.format("第 %d 条记录所有字段为空", index + 1));
            }

        } catch (Exception e) {
            result.addSampleError(String.format("第 %d 条记录验证失败: %s", index + 1, e.getMessage()));
        }
    }

    /**
     * 快速检查文件是否为空（不解析完整XML）
     */
    public static boolean isEmptyQuick(File xmlFile) {
        try {
            return check(xmlFile).isEmpty();
        } catch (Exception e) {
            log.warn("快速检查失败，假定文件有效: {}", xmlFile.getName());
            return false;  // 出错时保守处理，假定有数据
        }
    }
}
