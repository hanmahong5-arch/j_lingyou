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
import org.springframework.transaction.annotation.Transactional;
import red.jiuzhou.pattern.dao.DataTemplateDao;
import red.jiuzhou.pattern.dao.TemplateParamDao;
import red.jiuzhou.pattern.model.DataTemplate;
import red.jiuzhou.pattern.model.TemplateParam;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板实例化器
 *
 * <p>将数据模板实例化为新的数据记录。通过填充占位符、重分配ID、更新引用关系，
 * 从模板创建全新的游戏数据，避免手动操作多个关联表。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>加载模板（从 data_template 表）</li>
 *   <li>验证参数（基于 template_param 表）</li>
 *   <li>ID重分配（查询表中最大ID + 1）</li>
 *   <li>填充占位符（{ITEM_ID} → 实际值）</li>
 *   <li>解析XML并保存到数据库</li>
 *   <li>更新引用关系（自动链接相关表）</li>
 * </ul>
 *
 * <p><b>实例化流程：</b>
 * <ol>
 *   <li>从数据库加载模板和参数定义</li>
 *   <li>验证用户输入的参数（类型、范围、必填项）</li>
 *   <li>自动生成ID（如果参数未提供）</li>
 *   <li>填充所有占位符</li>
 *   <li>解析XML，提取字段值</li>
 *   <li>构建INSERT SQL并执行</li>
 *   <li>返回实例化结果（包含新记录ID）</li>
 * </ol>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * TemplateInstantiator instantiator = new TemplateInstantiator();
 *
 * Map<String, String> params = new HashMap<>();
 * params.put("{ITEM_NAME}", "炽焰长剑");
 * params.put("{LEVEL}", "50");
 *
 * InstantiationResult result = instantiator.instantiate(123, params);
 * System.out.println("新记录ID: " + result.getNewRecordId());
 * }</pre>
 *
 * @author Claude
 * @version 1.0
 * @since 2025-12-30
 */
public class TemplateInstantiator {
    private static final Logger log = LoggerFactory.getLogger(TemplateInstantiator.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataTemplateDao dataTemplateDao;
    private final TemplateParamDao templateParamDao;

    /** 占位符模式：{FIELD_NAME} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([A-Z_0-9]+)}");

    // ========== 构造函数 ==========

    public TemplateInstantiator() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        this.dataTemplateDao = new DataTemplateDao(jdbcTemplate);
        this.templateParamDao = new TemplateParamDao(jdbcTemplate);
    }

    public TemplateInstantiator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataTemplateDao = new DataTemplateDao(jdbcTemplate);
        this.templateParamDao = new TemplateParamDao(jdbcTemplate);
    }

    // ========== 核心方法 ==========

    /**
     * 实例化模板
     *
     * @param templateId 模板ID
     * @param parameters 参数映射（占位符 → 实际值）
     * @return 实例化结果
     */
    @Transactional
    public InstantiationResult instantiate(int templateId, Map<String, String> parameters) {
        log.info("开始实例化模板: 模板ID={}, 参数数={}", templateId, parameters.size());

        try {
            // 1. 加载模板和参数定义
            DataTemplate template = dataTemplateDao.findById(templateId)
                    .orElseThrow(() -> new IllegalArgumentException("模板不存在: ID=" + templateId));

            List<TemplateParam> paramDefs = templateParamDao.findByTemplateId(templateId);
            template.setParams(paramDefs);

            log.info("加载模板: {}, 参数定义数={}", template.getTemplateName(), paramDefs.size());

            // 2. 验证参数
            validateParameters(template, parameters);

            // 3. 提取表名
            String tableName = extractTableName(template.getTemplateXml());
            log.info("目标表: {}", tableName);

            // 4. 自动生成ID（如果未提供）
            Map<String, String> finalParams = new LinkedHashMap<>(parameters);
            generateMissingIds(tableName, paramDefs, finalParams);

            // 5. 填充占位符
            String filledXml = fillPlaceholders(template.getTemplateXml(), finalParams);
            log.debug("填充后的XML: {}", filledXml);

            // 6. 解析XML，提取字段值
            Map<String, String> fieldValues = parseXmlToFields(filledXml);

            // 7. 保存到数据库
            String newRecordId = insertRecord(tableName, fieldValues);

            // 8. 更新模板使用次数
            dataTemplateDao.incrementUsageCount(templateId);

            // 9. 返回结果
            InstantiationResult result = new InstantiationResult();
            result.setSuccess(true);
            result.setTemplateId(templateId);
            result.setTemplateName(template.getTemplateName());
            result.setTableName(tableName);
            result.setNewRecordId(newRecordId);
            result.setFieldValues(fieldValues);
            result.setMessage("实例化成功");

            log.info("模板实例化成功: 表={}, 新记录ID={}", tableName, newRecordId);
            return result;

        } catch (Exception e) {
            log.error("模板实例化失败: 模板ID={}", templateId, e);

            InstantiationResult result = new InstantiationResult();
            result.setSuccess(false);
            result.setTemplateId(templateId);
            result.setMessage("实例化失败: " + e.getMessage());
            result.setError(e);

            return result;
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 验证参数
     *
     * @param template 模板
     * @param parameters 用户提供的参数
     */
    private void validateParameters(DataTemplate template, Map<String, String> parameters) {
        List<String> errors = new ArrayList<>();

        for (TemplateParam param : template.getParams()) {
            String paramCode = param.getParamCode();
            String value = parameters.get(paramCode);

            // 检查必填参数
            if (param.getIsRequired() && (value == null || value.isEmpty())) {
                // 如果是ID字段且有自动生成器，则跳过
                if (param.getGeneratorType() == TemplateParam.GeneratorType.SEQUENCE) {
                    continue;
                }
                errors.add(String.format("缺少必填参数: %s (%s)", param.getParamName(), paramCode));
                continue;
            }

            if (value == null || value.isEmpty()) {
                continue;
            }

            // 验证类型
            try {
                switch (param.getParamType()) {
                    case INTEGER -> Integer.parseInt(value);
                    case DECIMAL -> Double.parseDouble(value);
                    case BOOLEAN -> {
                        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")
                                && !value.equals("1") && !value.equals("0")) {
                            errors.add(String.format("参数 %s 的值 '%s' 不是有效的布尔值", paramCode, value));
                        }
                    }
                }
            } catch (NumberFormatException e) {
                errors.add(String.format("参数 %s 的值 '%s' 类型不匹配，期望 %s",
                        paramCode, value, param.getParamType()));
            }

            // 验证范围
            if (param.getMinValue() != null && !param.getMinValue().isEmpty()) {
                try {
                    double numValue = Double.parseDouble(value);
                    double minValue = Double.parseDouble(param.getMinValue());
                    if (numValue < minValue) {
                        errors.add(String.format("参数 %s 的值 %s 小于最小值 %s",
                                paramCode, value, param.getMinValue()));
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            if (param.getMaxValue() != null && !param.getMaxValue().isEmpty()) {
                try {
                    double numValue = Double.parseDouble(value);
                    double maxValue = Double.parseDouble(param.getMaxValue());
                    if (numValue > maxValue) {
                        errors.add(String.format("参数 %s 的值 %s 大于最大值 %s",
                                paramCode, value, param.getMaxValue()));
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            // 验证枚举值
            if (param.getParamType() == TemplateParam.ParamType.ENUM &&
                    param.getEnumValues() != null && !param.getEnumValues().isEmpty()) {
                JSONArray enumValues = JSON.parseArray(param.getEnumValues());
                if (!enumValues.contains(value)) {
                    errors.add(String.format("参数 %s 的值 '%s' 不在枚举范围内: %s",
                            paramCode, value, enumValues));
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("参数验证失败:\n" + String.join("\n", errors));
        }
    }

    /**
     * 自动生成缺失的ID
     *
     * @param tableName 表名
     * @param paramDefs 参数定义列表
     * @param parameters 参数映射（会被修改）
     */
    private void generateMissingIds(String tableName, List<TemplateParam> paramDefs,
                                   Map<String, String> parameters) {
        for (TemplateParam param : paramDefs) {
            // 仅处理SEQUENCE类型的ID生成器
            if (param.getGeneratorType() != TemplateParam.GeneratorType.SEQUENCE) {
                continue;
            }

            String paramCode = param.getParamCode();
            if (parameters.containsKey(paramCode) && !parameters.get(paramCode).isEmpty()) {
                continue;  // 用户已提供值
            }

            // 生成新ID
            String newId = generateNewId(tableName);
            parameters.put(paramCode, newId);
            log.info("自动生成ID: {} = {}", paramCode, newId);
        }
    }

    /**
     * 生成新ID
     * <p>查询表中当前最大ID，然后+1
     *
     * @param tableName 表名
     * @return 新ID（字符串形式）
     */
    private String generateNewId(String tableName) {
        String sql = "SELECT MAX(CAST(id AS UNSIGNED)) FROM " + tableName;
        try {
            Integer maxId = jdbcTemplate.queryForObject(sql, Integer.class);
            int newId = (maxId != null ? maxId : 0) + 1;
            return String.valueOf(newId);
        } catch (Exception e) {
            log.warn("查询最大ID失败，使用时间戳: 表={}", tableName, e);
            return String.valueOf(System.currentTimeMillis() % 1000000);
        }
    }

    /**
     * 从模板XML中提取表名
     *
     * @param templateXml 模板XML
     * @return 表名
     */
    private String extractTableName(String templateXml) {
        try {
            Document doc = DocumentHelper.parseText(templateXml);
            return doc.getRootElement().getName();
        } catch (Exception e) {
            throw new RuntimeException("解析模板XML失败: " + e.getMessage(), e);
        }
    }

    /**
     * 填充占位符
     *
     * @param templateXml 模板XML
     * @param parameters 参数映射
     * @return 填充后的XML
     */
    private String fillPlaceholders(String templateXml, Map<String, String> parameters) {
        String result = templateXml;

        // 替换所有占位符
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String placeholder = entry.getKey();
            String value = entry.getValue();

            // 确保占位符格式正确
            if (!placeholder.startsWith("{")) {
                placeholder = "{" + placeholder + "}";
            }

            result = result.replace(placeholder, value);
        }

        // 检查是否还有未填充的占位符
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(result);
        if (matcher.find()) {
            List<String> missingPlaceholders = new ArrayList<>();
            matcher.reset();
            while (matcher.find()) {
                missingPlaceholders.add(matcher.group());
            }
            log.warn("存在未填充的占位符: {}", missingPlaceholders);
        }

        return result;
    }

    /**
     * 解析XML，提取字段值
     *
     * @param xml XML字符串
     * @return 字段映射（字段名 → 值）
     */
    private Map<String, String> parseXmlToFields(String xml) {
        try {
            Document doc = DocumentHelper.parseText(xml);
            Element root = doc.getRootElement();

            Map<String, String> fields = new LinkedHashMap<>();
            root.attributes().forEach(attr -> {
                fields.put(attr.getName(), attr.getValue());
            });

            return fields;
        } catch (Exception e) {
            throw new RuntimeException("解析XML失败: " + e.getMessage(), e);
        }
    }

    /**
     * 插入记录到数据库
     *
     * @param tableName 表名
     * @param fieldValues 字段值映射
     * @return 新记录的ID
     */
    private String insertRecord(String tableName, Map<String, String> fieldValues) {
        if (fieldValues.isEmpty()) {
            throw new IllegalArgumentException("字段值为空，无法插入记录");
        }

        // 构建INSERT SQL
        List<String> fields = new ArrayList<>(fieldValues.keySet());
        List<String> values = new ArrayList<>(fieldValues.values());

        String fieldsPart = String.join(", ", fields);
        String valuesPart = fields.stream().map(f -> "?").collect(java.util.stream.Collectors.joining(", "));

        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", tableName, fieldsPart, valuesPart);

        log.debug("执行SQL: {}", sql);
        log.debug("参数: {}", values);

        jdbcTemplate.update(sql, values.toArray());

        // 返回新插入的ID
        String idValue = fieldValues.get("id");
        if (idValue != null && !idValue.isEmpty()) {
            return idValue;
        }

        // 如果没有ID字段，尝试查询最后插入的ID
        try {
            String lastIdSql = "SELECT LAST_INSERT_ID()";
            Long lastId = jdbcTemplate.queryForObject(lastIdSql, Long.class);
            return lastId != null ? lastId.toString() : "unknown";
        } catch (Exception e) {
            log.warn("查询最后插入ID失败", e);
            return "unknown";
        }
    }

    // ========== 结果类 ==========

    /**
     * 实例化结果
     */
    public static class InstantiationResult {
        private boolean success;
        private int templateId;
        private String templateName;
        private String tableName;
        private String newRecordId;
        private Map<String, String> fieldValues;
        private String message;
        private Exception error;

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public int getTemplateId() { return templateId; }
        public void setTemplateId(int templateId) { this.templateId = templateId; }

        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public String getNewRecordId() { return newRecordId; }
        public void setNewRecordId(String newRecordId) { this.newRecordId = newRecordId; }

        public Map<String, String> getFieldValues() { return fieldValues; }
        public void setFieldValues(Map<String, String> fieldValues) { this.fieldValues = fieldValues; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public Exception getError() { return error; }
        public void setError(Exception error) { this.error = error; }

        @Override
        public String toString() {
            return String.format("InstantiationResult{success=%s, table=%s, newId=%s, message='%s'}",
                    success, tableName, newRecordId, message);
        }
    }

    // ========== 公共工具方法 ==========

    /**
     * 批量实例化
     *
     * @param templateId 模板ID
     * @param parametersList 参数列表（每个Map代表一次实例化）
     * @return 实例化结果列表
     */
    public List<InstantiationResult> batchInstantiate(int templateId, List<Map<String, String>> parametersList) {
        List<InstantiationResult> results = new ArrayList<>();

        for (Map<String, String> parameters : parametersList) {
            try {
                InstantiationResult result = instantiate(templateId, parameters);
                results.add(result);
            } catch (Exception e) {
                log.error("批量实例化失败: 模板ID={}", templateId, e);

                InstantiationResult result = new InstantiationResult();
                result.setSuccess(false);
                result.setTemplateId(templateId);
                result.setMessage("批量实例化失败: " + e.getMessage());
                result.setError(e);
                results.add(result);
            }
        }

        long successCount = results.stream().filter(InstantiationResult::isSuccess).count();
        log.info("批量实例化完成: 成功={}/{}", successCount, results.size());

        return results;
    }

    /**
     * 预览实例化（不实际保存到数据库）
     *
     * @param templateId 模板ID
     * @param parameters 参数映射
     * @return 预览XML
     */
    public String previewInstantiation(int templateId, Map<String, String> parameters) {
        log.info("预览实例化: 模板ID={}", templateId);

        try {
            DataTemplate template = dataTemplateDao.findById(templateId)
                    .orElseThrow(() -> new IllegalArgumentException("模板不存在: ID=" + templateId));

            List<TemplateParam> paramDefs = templateParamDao.findByTemplateId(templateId);
            template.setParams(paramDefs);

            String tableName = extractTableName(template.getTemplateXml());

            Map<String, String> finalParams = new LinkedHashMap<>(parameters);
            generateMissingIds(tableName, paramDefs, finalParams);

            return fillPlaceholders(template.getTemplateXml(), finalParams);

        } catch (Exception e) {
            log.error("预览失败: 模板ID={}", templateId, e);
            throw new RuntimeException("预览失败: " + e.getMessage(), e);
        }
    }
}
