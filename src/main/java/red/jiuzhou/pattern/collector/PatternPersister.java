package red.jiuzhou.pattern.collector;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import red.jiuzhou.pattern.dao.*;
import red.jiuzhou.pattern.model.*;
import red.jiuzhou.util.DatabaseUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模式持久化服务
 * 负责将收集的模式数据批量写入数据库
 */
public class PatternPersister {
    private static final Logger log = LoggerFactory.getLogger(PatternPersister.class);

    private final PatternSchemaDao schemaDao;
    private final PatternFieldDao fieldDao;
    private final PatternValueDao valueDao;
    private final PatternRefDao refDao;
    private final PatternSampleDao sampleDao;
    private final AttrDictionaryDao attrDao;
    private final DataTemplateDao templateDao;
    private final TemplateParamDao paramDao;

    public PatternPersister() {
        this.schemaDao = new PatternSchemaDao();
        this.fieldDao = new PatternFieldDao();
        this.valueDao = new PatternValueDao();
        this.refDao = new PatternRefDao();
        this.sampleDao = new PatternSampleDao();
        this.attrDao = new AttrDictionaryDao();
        this.templateDao = new DataTemplateDao();
        this.paramDao = new TemplateParamDao();
    }

    /**
     * 初始化数据库表
     * 执行 pattern_tables.sql 中的建表语句
     */
    public void initializeTables() {
        log.info("初始化模式收集数据库表...");

        // 检查表是否存在
        if (schemaDao.tableExists()) {
            log.info("数据库表已存在，跳过初始化");
            return;
        }

        // 从SQL文件读取建表语句
        try {
            String sqlPath = "src/main/resources/sql/pattern_tables.sql";
            String sqlContent = cn.hutool.core.io.FileUtil.readString(sqlPath, "UTF-8");

            // 分割并执行SQL语句
            String[] statements = sqlContent.split(";");
            for (String stmt : statements) {
                stmt = stmt.trim();
                if (stmt.isEmpty() || stmt.startsWith("--")) {
                    continue;
                }
                try {
                    DatabaseUtil.getJdbcTemplate().execute(stmt);
                } catch (Exception e) {
                    log.warn("执行SQL失败: {} - {}", stmt.substring(0, Math.min(50, stmt.length())), e.getMessage());
                }
            }

            log.info("数据库表初始化完成");
        } catch (Exception e) {
            log.error("初始化数据库表失败", e);
            throw new RuntimeException("初始化数据库表失败", e);
        }
    }

    /**
     * 清空所有模式数据（谨慎使用）
     */
    public void clearAllData() {
        log.warn("开始清空所有模式数据...");

        TransactionStatus tx = DatabaseUtil.beginTransaction();
        try {
            // 按依赖顺序删除
            paramDao.deleteAll();
            templateDao.deleteAll();
            sampleDao.deleteAll();
            refDao.deleteAll();
            valueDao.deleteAll();
            fieldDao.deleteAll();
            schemaDao.deleteAll();
            // 注意：不清空 attrDao，因为属性词典是基础数据

            DatabaseUtil.commitTransaction(tx);
            log.info("模式数据已清空");
        } catch (Exception e) {
            DatabaseUtil.rollbackTransaction(tx);
            log.error("清空数据失败", e);
            throw new RuntimeException("清空数据失败", e);
        }
    }

    /**
     * 保存模式分类（带字段）
     */
    public void saveSchema(PatternSchema schema) {
        schemaDao.saveOrUpdate(schema);

        if (schema.getFields() != null) {
            for (PatternField field : schema.getFields()) {
                field.setSchemaId(schema.getId());
                fieldDao.saveOrUpdate(field);

                // 保存值分布
                if (field.getValues() != null) {
                    for (PatternValue value : field.getValues()) {
                        value.setFieldId(field.getId());
                        valueDao.saveOrIncrement(value);
                    }
                }
            }
        }
    }

    /**
     * 批量保存字段模式
     */
    public void saveFields(Integer schemaId, List<PatternField> fields) {
        for (PatternField field : fields) {
            field.setSchemaId(schemaId);
            fieldDao.saveOrUpdate(field);
        }
    }

    /**
     * 保存引用关系
     */
    public void saveRef(PatternRef ref) {
        refDao.saveOrUpdate(ref);
    }

    /**
     * 批量保存引用关系
     */
    public void saveRefs(List<PatternRef> refs) {
        for (PatternRef ref : refs) {
            refDao.saveOrUpdate(ref);
        }
    }

    /**
     * 保存样本数据
     */
    public void saveSample(PatternSample sample) {
        sampleDao.insert(sample);
    }

    /**
     * 批量保存样本
     */
    public void saveSamples(List<PatternSample> samples) {
        sampleDao.batchInsert(samples);
    }

    /**
     * 保存属性词典
     */
    public void saveAttr(AttrDictionary attr) {
        attrDao.saveOrUpdate(attr);
    }

    /**
     * 批量保存属性
     */
    public void saveAttrs(List<AttrDictionary> attrs) {
        attrDao.batchInsert(attrs);
    }

    /**
     * 保存模板（带参数）
     */
    public void saveTemplate(DataTemplate template) {
        if (template.getId() == null) {
            templateDao.insert(template);
        } else {
            templateDao.update(template);
        }

        if (template.getParams() != null) {
            // 先删除旧参数
            paramDao.deleteByTemplateId(template.getId());

            // 插入新参数
            for (TemplateParam param : template.getParams()) {
                param.setTemplateId(template.getId());
                paramDao.insert(param);
            }
        }
    }

    /**
     * 导出模式数据为JSON
     */
    public String exportToJson(Integer schemaId) {
        PatternSchema schema = schemaDao.findById(schemaId).orElse(null);
        if (schema == null) {
            return "{}";
        }

        // 加载关联数据
        schema.setFields(fieldDao.findBySchemaId(schemaId));
        for (PatternField field : schema.getFields()) {
            field.setValues(valueDao.findByFieldId(field.getId()));
        }

        return JSON.toJSONString(schema, true);
    }

    /**
     * 获取统计信息
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        stats.put("schemas", schemaDao.findAll().size());
        stats.put("attrs", attrDao.count());
        stats.put("fields", countAllFields());
        stats.put("templates", templateDao.findAll().size());
        return stats;
    }

    private int countAllFields() {
        List<PatternSchema> schemas = schemaDao.findAll();
        int total = 0;
        for (PatternSchema schema : schemas) {
            total += fieldDao.findBySchemaId(schema.getId()).size();
        }
        return total;
    }

    // Getter for DAOs (供外部使用)
    public PatternSchemaDao getSchemaDao() { return schemaDao; }
    public PatternFieldDao getFieldDao() { return fieldDao; }
    public PatternValueDao getValueDao() { return valueDao; }
    public PatternRefDao getRefDao() { return refDao; }
    public PatternSampleDao getSampleDao() { return sampleDao; }
    public AttrDictionaryDao getAttrDao() { return attrDao; }
    public DataTemplateDao getTemplateDao() { return templateDao; }
    public TemplateParamDao getParamDao() { return paramDao; }
}
