package red.jiuzhou.pattern.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import red.jiuzhou.pattern.model.DataTemplate;
import red.jiuzhou.pattern.model.DataTemplate.TemplateType;
import red.jiuzhou.util.DatabaseUtil;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * 生成模板表DAO
 */
public class DataTemplateDao {
    private static final Logger log = LoggerFactory.getLogger(DataTemplateDao.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<DataTemplate> ROW_MAPPER = (rs, rowNum) -> {
        DataTemplate template = new DataTemplate();
        template.setId(rs.getInt("id"));
        template.setSchemaId(rs.getInt("schema_id"));
        template.setTemplateName(rs.getString("template_name"));
        template.setTemplateCode(rs.getString("template_code"));

        String typeStr = rs.getString("template_type");
        if (typeStr != null) {
            try {
                template.setTemplateType(TemplateType.valueOf(typeStr));
            } catch (IllegalArgumentException e) {
                template.setTemplateType(TemplateType.CREATE);
            }
        }

        template.setTemplateXml(rs.getString("template_xml"));
        template.setPlaceholderList(rs.getString("placeholder_list"));
        template.setDefaultValues(rs.getString("default_values"));
        template.setValueGenerators(rs.getString("value_generators"));
        template.setValidationRules(rs.getString("validation_rules"));
        template.setDescription(rs.getString("description"));
        template.setUsageCount(rs.getInt("usage_count"));
        template.setIsActive(rs.getBoolean("is_active"));
        template.setCreatedBy(rs.getString("created_by"));
        template.setCreatedAt(rs.getTimestamp("created_at"));
        template.setUpdatedAt(rs.getTimestamp("updated_at"));
        return template;
    };

    public DataTemplateDao() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    public DataTemplateDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入新的模板
     */
    public int insert(DataTemplate template) {
        String sql = "INSERT INTO data_template (schema_id, template_name, template_code, " +
                "template_type, template_xml, placeholder_list, default_values, value_generators, " +
                "validation_rules, description, usage_count, is_active, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, template.getSchemaId());
            ps.setString(2, template.getTemplateName());
            ps.setString(3, template.getTemplateCode());
            ps.setString(4, template.getTemplateType() != null ? template.getTemplateType().name() : "CREATE");
            ps.setString(5, template.getTemplateXml());
            ps.setString(6, template.getPlaceholderList());
            ps.setString(7, template.getDefaultValues());
            ps.setString(8, template.getValueGenerators());
            ps.setString(9, template.getValidationRules());
            ps.setString(10, template.getDescription());
            ps.setInt(11, template.getUsageCount() != null ? template.getUsageCount() : 0);
            ps.setBoolean(12, template.getIsActive() != null ? template.getIsActive() : true);
            ps.setString(13, template.getCreatedBy());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        int id = key != null ? key.intValue() : -1;
        template.setId(id);
        return id;
    }

    /**
     * 更新模板
     */
    public int update(DataTemplate template) {
        String sql = "UPDATE data_template SET template_name = ?, template_type = ?, " +
                "template_xml = ?, placeholder_list = ?, default_values = ?, value_generators = ?, " +
                "validation_rules = ?, description = ?, is_active = ? WHERE id = ?";

        return jdbcTemplate.update(sql,
                template.getTemplateName(),
                template.getTemplateType() != null ? template.getTemplateType().name() : "CREATE",
                template.getTemplateXml(),
                template.getPlaceholderList(),
                template.getDefaultValues(),
                template.getValueGenerators(),
                template.getValidationRules(),
                template.getDescription(),
                template.getIsActive(),
                template.getId());
    }

    /**
     * 增加使用次数
     */
    public int incrementUsage(Integer id) {
        String sql = "UPDATE data_template SET usage_count = usage_count + 1 WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 根据ID查询
     */
    public Optional<DataTemplate> findById(Integer id) {
        String sql = "SELECT * FROM data_template WHERE id = ?";
        List<DataTemplate> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据模板代码查询
     */
    public Optional<DataTemplate> findByTemplateCode(String templateCode) {
        String sql = "SELECT * FROM data_template WHERE template_code = ?";
        List<DataTemplate> results = jdbcTemplate.query(sql, ROW_MAPPER, templateCode);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据模式ID查询
     */
    public List<DataTemplate> findBySchemaId(Integer schemaId) {
        String sql = "SELECT * FROM data_template WHERE schema_id = ? AND is_active = 1 " +
                "ORDER BY usage_count DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, schemaId);
    }

    /**
     * 查询所有活跃模板
     */
    public List<DataTemplate> findAllActive() {
        String sql = "SELECT * FROM data_template WHERE is_active = 1 ORDER BY schema_id, template_name";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /**
     * 查询所有模板
     */
    public List<DataTemplate> findAll() {
        String sql = "SELECT * FROM data_template ORDER BY schema_id, template_name";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /**
     * 按类型查询
     */
    public List<DataTemplate> findByType(TemplateType type) {
        String sql = "SELECT * FROM data_template WHERE template_type = ? AND is_active = 1";
        return jdbcTemplate.query(sql, ROW_MAPPER, type.name());
    }

    /**
     * 删除模板
     */
    public int deleteById(Integer id) {
        String sql = "DELETE FROM data_template WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 停用模板（软删除）
     */
    public int deactivate(Integer id) {
        String sql = "UPDATE data_template SET is_active = 0 WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 清空所有数据
     */
    public int deleteAll() {
        String sql = "DELETE FROM data_template";
        return jdbcTemplate.update(sql);
    }

    /**
     * 增加模板使用次数
     */
    public void incrementUsageCount(int templateId) {
        String sql = "UPDATE data_template SET usage_count = usage_count + 1 WHERE id = ?";
        jdbcTemplate.update(sql, templateId);
        log.debug("模板 {} 使用次数已增加", templateId);
    }
}
