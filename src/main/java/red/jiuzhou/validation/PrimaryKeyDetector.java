package red.jiuzhou.validation;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * 主键自动检测器
 *
 * 功能：
 * 从XML文件自动识别主键字段
 *
 * 检测策略（优先级从高到低）：
 * 1. 检查XML属性中的 id
 * 2. 检查子元素中的 id
 * 3. 检查所有 _attr_* 格式的属性
 * 4. 检查所有 _attr_* 格式的子元素
 * 5. 检查常见候选字段（desc, name, dev_name, ID）
 * 6. 无法识别 → 返回null
 *
 * @author Claude
 * @date 2025-12-28
 */
public class PrimaryKeyDetector {

    private static final Logger log = LoggerFactory.getLogger(PrimaryKeyDetector.class);

    // 常见主键候选字段（优先级从高到低）
    private static final String[] COMMON_CANDIDATES = {
        "id", "ID", "desc", "name", "dev_name", "_dev_name_"
    };

    /**
     * 从XML文件自动检测主键字段
     *
     * @param xmlFile XML文件
     * @return 主键信息，如果无法识别则返回null
     */
    public static PrimaryKeyInfo detectFromXml(File xmlFile) {
        try {
            SAXReader reader = SAXReader.createDefault();
            Document doc = reader.read(xmlFile);
            Element root = doc.getRootElement();

            if (root == null || root.elements().isEmpty()) {
                log.warn("XML文件 {} 无数据，无法检测主键", xmlFile.getName());
                return null;
            }

            // 使用第一个数据项进行检测
            Element firstItem = (Element) root.elements().get(0);

            return detectFromElement(firstItem, xmlFile.getName());

        } catch (DocumentException e) {
            log.error("解析XML文件失败: {}", xmlFile.getName(), e);
            return null;
        } catch (Exception e) {
            log.error("主键检测异常: {}", xmlFile.getName(), e);
            return null;
        }
    }

    /**
     * 从XML元素检测主键
     */
    public static PrimaryKeyInfo detectFromElement(Element element, String fileName) {
        if (element == null) {
            return null;
        }

        // 策略1: 检查是否有 id 属性（XML属性，不是子元素）
        Attribute idAttr = element.attribute("id");
        if (idAttr != null && !idAttr.getValue().trim().isEmpty()) {
            log.debug("检测到主键（属性）: id, 文件: {}", fileName);
            return new PrimaryKeyInfo("id", PrimaryKeyInfo.PrimaryKeyType.ATTRIBUTE, "ATTRIBUTE_ID");
        }

        // 策略2: 检查是否有 id 子元素
        Element idElem = element.element("id");
        if (idElem != null) {
            String value = idElem.getText().trim();
            if (!value.isEmpty()) {
                log.debug("检测到主键（元素）: id, 文件: {}", fileName);
                return new PrimaryKeyInfo("id", PrimaryKeyInfo.PrimaryKeyType.ELEMENT, "ELEMENT_ID");
            }
        }

        // 策略3: 检查所有 _attr_* 开头的属性
        @SuppressWarnings("unchecked")
        List<Attribute> attributes = element.attributes();
        for (Attribute attr : attributes) {
            String name = attr.getName();
            if (name.startsWith("_attr_") && !attr.getValue().trim().isEmpty()) {
                log.info("检测到主键（_attr_属性）: {}, 文件: {}", name, fileName);
                return new PrimaryKeyInfo(name, PrimaryKeyInfo.PrimaryKeyType.ATTRIBUTE, "ATTR_PREFIX_ATTRIBUTE");
            }
        }

        // 策略4: 检查所有 _attr_* 开头的子元素
        @SuppressWarnings("unchecked")
        List<Element> children = element.elements();
        for (Element child : children) {
            String name = child.getName();
            if (name.startsWith("_attr_") && !child.getText().trim().isEmpty()) {
                log.info("检测到主键（_attr_元素）: {}, 文件: {}", name, fileName);
                return new PrimaryKeyInfo(name, PrimaryKeyInfo.PrimaryKeyType.ELEMENT, "ATTR_PREFIX_ELEMENT");
            }
        }

        // 策略5: 检查常见候选字段
        for (String candidate : COMMON_CANDIDATES) {
            Element candElem = element.element(candidate);
            if (candElem != null && !candElem.getText().trim().isEmpty()) {
                log.debug("检测到主键（常见候选）: {}, 文件: {}", candidate, fileName);
                return new PrimaryKeyInfo(candidate, PrimaryKeyInfo.PrimaryKeyType.ELEMENT, "COMMON_CANDIDATE");
            }

            Attribute candAttr = element.attribute(candidate);
            if (candAttr != null && !candAttr.getValue().trim().isEmpty()) {
                log.debug("检测到主键（常见候选属性）: {}, 文件: {}", candidate, fileName);
                return new PrimaryKeyInfo(candidate, PrimaryKeyInfo.PrimaryKeyType.ATTRIBUTE, "COMMON_CANDIDATE_ATTR");
            }
        }

        // 策略6: 无法识别
        log.warn("无法自动识别主键: {}", fileName);
        return null;
    }

    /**
     * 检测XML文件中是否存在指定的主键字段
     *
     * @param xmlFile XML文件
     * @param primaryKeyField 主键字段名
     * @return true 如果存在
     */
    public static boolean hasField(File xmlFile, String primaryKeyField) {
        try {
            SAXReader reader = SAXReader.createDefault();
            Document doc = reader.read(xmlFile);
            Element root = doc.getRootElement();

            if (root == null || root.elements().isEmpty()) {
                return false;
            }

            Element firstItem = (Element) root.elements().get(0);

            // 检查属性
            if (firstItem.attribute(primaryKeyField) != null) {
                return true;
            }

            // 检查子元素
            if (firstItem.element(primaryKeyField) != null) {
                return true;
            }

            return false;

        } catch (Exception e) {
            log.warn("检查字段存在性失败: {}, 字段: {}", xmlFile.getName(), primaryKeyField);
            return false;
        }
    }
}
