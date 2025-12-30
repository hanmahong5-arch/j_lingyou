package red.jiuzhou.template;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.pattern.collector.FieldTypeInferrer;
import red.jiuzhou.pattern.dao.DataTemplateDao;
import red.jiuzhou.pattern.dao.TemplateParamDao;
import red.jiuzhou.pattern.model.DataTemplate;
import red.jiuzhou.pattern.model.PatternField.FieldType;
import red.jiuzhou.pattern.model.TemplateParam;
import red.jiuzhou.pattern.model.TemplateParam.GeneratorType;
import red.jiuzhou.pattern.model.TemplateParam.ParamType;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 模板提取器
 *
 * <p>从数据库中的现有记录提取数据模板，用于后续的克隆和修改操作。
 * 自动识别可变字段（ID、名称、引用字段等），生成带占位符的XML模板，
 * 并使用 FieldTypeInferrer 推断字段类型，创建参数定义。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>从数据库记录提取数据</li>
 *   <li>识别可变字段（ID、name、desc、*_id引用字段）</li>
 *   <li>生成带占位符的XML模板（如 {ITEM_ID}、{ITEM_NAME}）</li>
 *   <li>使用 FieldTypeInferrer 推断字段类型</li>
 *   <li>创建参数定义（TemplateParam）</li>
 *   <li>保存到 data_template 和 template_param 表</li>
 * </ul>
 *
 * <p><b>识别规则：</b>
 * <ol>
 *   <li>规则1：ID字段（id、*_id）</li>
 *   <li>规则2：name/desc文本字段（name、desc、title、description等）</li>
 *   <li>规则3：引用字段（*_id模式，如item_id、npc_id）</li>
 *   <li>规则4：bonus_attr字段（bonus_attr1-10）</li>
 * </ol>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * TemplateExtractor extractor = new TemplateExtractor();
 * DataTemplate template = extractor.extractFromRecord(
 *     "items",           // 表名
 *     "100001",          // 记录ID
 *     "暗影长剑模板",     // 模板名称
 *     "epic_sword_tpl"   // 模板代码
 * );
 * }</pre>
 *
 * @author Claude
 * @version 1.0
 * @since 2025-12-30
 */
public class TemplateExtractor {
    private static final Logger log = LoggerFactory.getLogger(TemplateExtractor.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataTemplateDao dataTemplateDao;
    private final TemplateParamDao templateParamDao;
    private final FieldTypeInferrer fieldTypeInferrer;

    // ========== 字段识别规则 ==========

    /** ID字段模式（主键或外键） */
    private static final Pattern ID_FIELD_PATTERN = Pattern.compile("(?i)^id$|.*_id$");

    /** 名称字段模式 */
    private static final Pattern NAME_FIELD_PATTERN = Pattern.compile(
            "(?i)^(name|title|desc|description|label|comment|remarks?)$");

    /** 引用字段模式（外键） */
    private static final Pattern REFERENCE_FIELD_PATTERN = Pattern.compile("(?i).*_(id|key)$");

    /** 属性增益字段模式 */
    private static final Pattern BONUS_ATTR_PATTERN = Pattern.compile("(?i)^bonus_attr[_]?(\\d+|[a-z]\\d*)$");

    /** 排除字段模式（不需要占位符） */
    private static final Set<String> EXCLUDED_FIELDS = Set.of(
            "created_at", "updated_at", "created_by", "updated_by",
            "deleted_at", "deleted_by", "is_deleted", "version"
    );

    // ========== 构造函数 ==========

    public TemplateExtractor() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        this.dataTemplateDao = new DataTemplateDao(jdbcTemplate);
        this.templateParamDao = new TemplateParamDao(jdbcTemplate);
        this.fieldTypeInferrer = new FieldTypeInferrer();
    }

    public TemplateExtractor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataTemplateDao = new DataTemplateDao(jdbcTemplate);
        this.templateParamDao = new TemplateParamDao(jdbcTemplate);
        this.fieldTypeInferrer = new FieldTypeInferrer();
    }

    // ========== 核心方法 ==========

    /**
     * 从数据库记录提取模板
     *
     * @param tableName 表名
     * @param recordId 记录ID
     * @param templateName 模板名称
     * @return 提取的数据模板
     */
    public DataTemplate extractFromRecord(String tableName, String recordId, String templateName) {
        return extractFromRecord(tableName, recordId, templateName, generateTemplateCode(tableName, recordId));
    }

    /**
     * 从数据库记录提取模板（带模板代码）
     *
     * @param tableName 表名
     * @param recordId 记录ID
     * @param templateName 模板名称
     * @param templateCode 模板代码（唯一标识）
     * @return 提取的数据模板
     */
    public DataTemplate extractFromRecord(String tableName, String recordId,
                                         String templateName, String templateCode) {
        log.info("开始提取模板: 表={}, 记录ID={}, 模板名={}", tableName, recordId, templateName);

        try {
            // 1. 从数据库读取源记录
            Map<String, String> sourceData = fetchRecord(tableName, recordId);
            if (sourceData.isEmpty()) {
                throw new IllegalArgumentException("记录不存在: " + tableName + ".id=" + recordId);
            }

            // 2. 识别可变字段
            Set<String> variableFields = identifyVariableFields(tableName, sourceData);
            log.info("识别到 {} 个可变字段: {}", variableFields.size(), variableFields);

            // 3. 生成带占位符的XML模板
            String templateXml = generateTemplateXml(tableName, sourceData, variableFields);

            // 4. 创建占位符列表
            List<String> placeholders = createPlaceholderList(variableFields);

            // 5. 创建参数定义
            List<TemplateParam> params = createParameters(variableFields, sourceData);

            // 6. 创建并保存模板
            DataTemplate template = new DataTemplate();
            template.setTemplateName(templateName);
            template.setTemplateCode(templateCode);
            template.setTemplateType(DataTemplate.TemplateType.CLONE);
            template.setTemplateXml(templateXml);
            template.setPlaceholderList(JSON.toJSONString(placeholders));
            template.setDescription(String.format("从 %s.id=%s 提取的克隆模板", tableName, recordId));
            template.setCreatedBy("system");

            // 保存模板
            int templateId = dataTemplateDao.insert(template);
            template.setId(templateId);

            // 保存参数
            for (TemplateParam param : params) {
                param.setTemplateId(templateId);
                templateParamDao.insert(param);
            }

            template.setParams(params);
            log.info("模板提取成功: ID={}, 占位符数={}, 参数数={}", templateId, placeholders.size(), params.size());

            return template;

        } catch (Exception e) {
            log.error("提取模板失败: 表={}, 记录ID={}", tableName, recordId, e);
            throw new RuntimeException("模板提取失败: " + e.getMessage(), e);
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 从数据库获取记录
     *
     * @param tableName 表名
     * @param recordId 记录ID
     * @return 记录数据（字段名 → 字段值）
     */
    private Map<String, String> fetchRecord(String tableName, String recordId) {
        String sql = String.format("SELECT * FROM %s WHERE id = ? LIMIT 1", tableName);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, recordId);
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> row = rows.get(0);
        Map<String, String> result = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();
            result.put(fieldName, value != null ? value.toString() : "");
        }

        return result;
    }

    /**
     * 识别可变字段
     * <p>规则：
     * <ol>
     *   <li>ID字段（主键和外键）</li>
     *   <li>name/desc等文本字段</li>
     *   <li>引用字段（*_id模式）</li>
     *   <li>bonus_attr字段</li>
     * </ol>
     *
     * @param tableName 表名
     * @param data 记录数据
     * @return 可变字段集合
     */
    private Set<String> identifyVariableFields(String tableName, Map<String, String> data) {
        Set<String> variableFields = new LinkedHashSet<>();

        for (String fieldName : data.keySet()) {
            // 跳过排除字段
            if (EXCLUDED_FIELDS.contains(fieldName.toLowerCase())) {
                continue;
            }

            // 规则1：ID字段
            if (ID_FIELD_PATTERN.matcher(fieldName).matches()) {
                variableFields.add(fieldName);
                continue;
            }

            // 规则2：名称/描述字段
            if (NAME_FIELD_PATTERN.matcher(fieldName).matches()) {
                variableFields.add(fieldName);
                continue;
            }

            // 规则3：引用字段
            if (REFERENCE_FIELD_PATTERN.matcher(fieldName).matches()) {
                variableFields.add(fieldName);
                continue;
            }

            // 规则4：属性增益字段
            if (BONUS_ATTR_PATTERN.matcher(fieldName).matches()) {
                variableFields.add(fieldName);
            }
        }

        return variableFields;
    }

    /**
     * 生成带占位符的XML模板
     *
     * @param tableName 表名
     * @param data 原始数据
     * @param variableFields 可变字段集合
     * @return XML模板字符串
     */
    private String generateTemplateXml(String tableName, Map<String, String> data,
                                      Set<String> variableFields) {
        try {
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement(tableName);

            // 添加字段（转换为占位符）
            for (Map.Entry<String, String> entry : data.entrySet()) {
                String fieldName = entry.getKey();
                String value = entry.getValue();

                if (variableFields.contains(fieldName)) {
                    // 可变字段：替换为占位符
                    String placeholder = createPlaceholder(fieldName);
                    root.addAttribute(fieldName, placeholder);
                } else {
                    // 不可变字段：保留原值
                    root.addAttribute(fieldName, value);
                }
            }

            return doc.asXML();

        } catch (Exception e) {
            log.error("生成XML模板失败: 表={}", tableName, e);
            throw new RuntimeException("生成XML模板失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建占位符
     *
     * @param fieldName 字段名
     * @return 占位符（如 {ITEM_ID}、{ITEM_NAME}）
     */
    private String createPlaceholder(String fieldName) {
        return "{" + fieldName.toUpperCase() + "}";
    }

    /**
     * 创建占位符列表
     *
     * @param variableFields 可变字段集合
     * @return 占位符列表
     */
    private List<String> createPlaceholderList(Set<String> variableFields) {
        List<String> placeholders = new ArrayList<>();
        for (String fieldName : variableFields) {
            placeholders.add(createPlaceholder(fieldName));
        }
        return placeholders;
    }

    /**
     * 创建参数定义列表
     *
     * @param variableFields 可变字段集合
     * @param sourceData 源数据（用于推断类型）
     * @return 参数定义列表
     */
    private List<TemplateParam> createParameters(Set<String> variableFields, Map<String, String> sourceData) {
        List<TemplateParam> params = new ArrayList<>();
        int displayOrder = 1;

        for (String fieldName : variableFields) {
            TemplateParam param = new TemplateParam();
            param.setParamName(fieldName);
            param.setParamCode(createPlaceholder(fieldName));
            param.setDisplayOrder(displayOrder++);

            // 使用 FieldTypeInferrer 推断字段类型
            FieldTypeInferrer.InferenceResult inference = fieldTypeInferrer.inferFromName(fieldName);

            // 设置参数类型
            param.setParamType(mapFieldTypeToParamType(inference.getFieldType()));

            // 设置默认值
            String defaultValue = sourceData.get(fieldName);
            param.setDefaultValue(defaultValue);

            // 设置生成器类型
            if (ID_FIELD_PATTERN.matcher(fieldName).matches()) {
                param.setGeneratorType(GeneratorType.SEQUENCE);
            } else if (inference.getFieldType() == FieldType.REFERENCE) {
                param.setGeneratorType(GeneratorType.LOOKUP);
                // 保存引用目标到生成器配置
                JSONObject config = new JSONObject();
                config.put("target", inference.getReferenceTarget());
                param.setGeneratorConfig(config.toJSONString());
            } else if (inference.isBonusAttr()) {
                param.setGeneratorType(GeneratorType.BONUS_ATTR);
            }

            // 设置显示提示
            param.setDisplayHint(generateDisplayHint(fieldName, inference));

            params.add(param);
        }

        return params;
    }

    /**
     * 映射 FieldType 到 ParamType
     *
     * @param fieldType 字段类型
     * @return 参数类型
     */
    private ParamType mapFieldTypeToParamType(FieldType fieldType) {
        return switch (fieldType) {
            case INTEGER -> ParamType.INTEGER;
            case DECIMAL -> ParamType.DECIMAL;
            case BOOLEAN -> ParamType.BOOLEAN;
            case ENUM -> ParamType.ENUM;
            case BONUS_ATTR -> ParamType.BONUS_ATTR;
            case REFERENCE -> ParamType.INTEGER;  // 引用字段通常是整数ID
            default -> ParamType.STRING;
        };
    }

    /**
     * 生成显示提示
     *
     * @param fieldName 字段名
     * @param inference 推断结果
     * @return 显示提示文本
     */
    private String generateDisplayHint(String fieldName, FieldTypeInferrer.InferenceResult inference) {
        if (ID_FIELD_PATTERN.matcher(fieldName).matches()) {
            return "系统将自动生成新的ID";
        } else if (inference.getFieldType() == FieldType.REFERENCE) {
            return String.format("引用 %s", inference.getReferenceTarget());
        } else if (inference.isBonusAttr()) {
            return "属性增益字段，从126个属性中选择";
        } else if (NAME_FIELD_PATTERN.matcher(fieldName).matches()) {
            return "建议修改为新的名称";
        } else {
            return "可选，默认沿用原值";
        }
    }

    /**
     * 生成模板代码
     *
     * @param tableName 表名
     * @param recordId 记录ID
     * @return 模板代码（如 items_100001_tpl）
     */
    private String generateTemplateCode(String tableName, String recordId) {
        return String.format("%s_%s_tpl", tableName, recordId);
    }

    // ========== 公共工具方法 ==========

    /**
     * 批量提取模板
     *
     * @param tableName 表名
     * @param recordIds 记录ID列表
     * @param templateNamePrefix 模板名称前缀
     * @return 提取的模板列表
     */
    public List<DataTemplate> batchExtract(String tableName, List<String> recordIds, String templateNamePrefix) {
        List<DataTemplate> templates = new ArrayList<>();

        for (int i = 0; i < recordIds.size(); i++) {
            String recordId = recordIds.get(i);
            String templateName = String.format("%s_%d", templateNamePrefix, i + 1);

            try {
                DataTemplate template = extractFromRecord(tableName, recordId, templateName);
                templates.add(template);
            } catch (Exception e) {
                log.error("批量提取失败: 表={}, 记录ID={}", tableName, recordId, e);
            }
        }

        log.info("批量提取完成: 表={}, 成功={}/{}", tableName, templates.size(), recordIds.size());
        return templates;
    }

    /**
     * 从查询结果提取模板
     *
     * @param tableName 表名
     * @param sqlWhere WHERE子句（不含WHERE关键字）
     * @param templateNamePrefix 模板名称前缀
     * @return 提取的模板列表
     */
    public List<DataTemplate> extractFromQuery(String tableName, String sqlWhere, String templateNamePrefix) {
        String sql = String.format("SELECT id FROM %s WHERE %s", tableName, sqlWhere);
        List<String> recordIds = jdbcTemplate.queryForList(sql, String.class);

        log.info("从查询提取模板: 表={}, WHERE={}, 记录数={}", tableName, sqlWhere, recordIds.size());
        return batchExtract(tableName, recordIds, templateNamePrefix);
    }
}
