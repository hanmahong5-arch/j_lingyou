package red.jiuzhou.pattern.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import red.jiuzhou.pattern.model.TemplateParam;
import red.jiuzhou.pattern.model.TemplateParam.GeneratorType;
import red.jiuzhou.pattern.model.TemplateParam.ParamType;
import red.jiuzhou.util.DatabaseUtil;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * 模板参数表DAO
 */
public class TemplateParamDao {
    private static final Logger log = LoggerFactory.getLogger(TemplateParamDao.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<TemplateParam> ROW_MAPPER = (rs, rowNum) -> {
        TemplateParam param = new TemplateParam();
        param.setId(rs.getInt("id"));
        param.setTemplateId(rs.getInt("template_id"));
        param.setParamName(rs.getString("param_name"));
        param.setParamCode(rs.getString("param_code"));

        String typeStr = rs.getString("param_type");
        if (typeStr != null) {
            try {
                param.setParamType(ParamType.valueOf(typeStr));
            } catch (IllegalArgumentException e) {
                param.setParamType(ParamType.STRING);
            }
        }

        param.setIsRequired(rs.getBoolean("is_required"));
        param.setDefaultValue(rs.getString("default_value"));
        param.setMinValue(rs.getString("min_value"));
        param.setMaxValue(rs.getString("max_value"));
        param.setEnumValues(rs.getString("enum_values"));

        String genTypeStr = rs.getString("generator_type");
        if (genTypeStr != null) {
            try {
                param.setGeneratorType(GeneratorType.valueOf(genTypeStr));
            } catch (IllegalArgumentException e) {
                param.setGeneratorType(null);
            }
        }

        param.setGeneratorConfig(rs.getString("generator_config"));
        param.setDisplayOrder(rs.getInt("display_order"));
        param.setDisplayHint(rs.getString("display_hint"));
        param.setDisplayGroup(rs.getString("display_group"));
        param.setCreatedAt(rs.getTimestamp("created_at"));
        param.setUpdatedAt(rs.getTimestamp("updated_at"));
        return param;
    };

    public TemplateParamDao() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    public TemplateParamDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入新的参数
     */
    public int insert(TemplateParam param) {
        String sql = "INSERT INTO template_param (template_id, param_name, param_code, param_type, " +
                "is_required, default_value, min_value, max_value, enum_values, generator_type, " +
                "generator_config, display_order, display_hint, display_group) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, param.getTemplateId());
            ps.setString(2, param.getParamName());
            ps.setString(3, param.getParamCode());
            ps.setString(4, param.getParamType() != null ? param.getParamType().name() : "STRING");
            ps.setBoolean(5, param.getIsRequired() != null ? param.getIsRequired() : true);
            ps.setString(6, param.getDefaultValue());
            ps.setString(7, param.getMinValue());
            ps.setString(8, param.getMaxValue());
            ps.setString(9, param.getEnumValues());
            ps.setString(10, param.getGeneratorType() != null ? param.getGeneratorType().name() : null);
            ps.setString(11, param.getGeneratorConfig());
            ps.setInt(12, param.getDisplayOrder() != null ? param.getDisplayOrder() : 0);
            ps.setString(13, param.getDisplayHint());
            ps.setString(14, param.getDisplayGroup());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        int id = key != null ? key.intValue() : -1;
        param.setId(id);
        return id;
    }

    /**
     * 更新参数
     */
    public int update(TemplateParam param) {
        String sql = "UPDATE template_param SET param_name = ?, param_type = ?, is_required = ?, " +
                "default_value = ?, min_value = ?, max_value = ?, enum_values = ?, " +
                "generator_type = ?, generator_config = ?, display_order = ?, display_hint = ?, " +
                "display_group = ? WHERE id = ?";

        return jdbcTemplate.update(sql,
                param.getParamName(),
                param.getParamType() != null ? param.getParamType().name() : "STRING",
                param.getIsRequired(),
                param.getDefaultValue(),
                param.getMinValue(),
                param.getMaxValue(),
                param.getEnumValues(),
                param.getGeneratorType() != null ? param.getGeneratorType().name() : null,
                param.getGeneratorConfig(),
                param.getDisplayOrder(),
                param.getDisplayHint(),
                param.getDisplayGroup(),
                param.getId());
    }

    /**
     * 根据ID查询
     */
    public Optional<TemplateParam> findById(Integer id) {
        String sql = "SELECT * FROM template_param WHERE id = ?";
        List<TemplateParam> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据模板ID查询所有参数
     */
    public List<TemplateParam> findByTemplateId(Integer templateId) {
        String sql = "SELECT * FROM template_param WHERE template_id = ? ORDER BY display_order, param_code";
        return jdbcTemplate.query(sql, ROW_MAPPER, templateId);
    }

    /**
     * 根据模板ID和参数代码查询
     */
    public Optional<TemplateParam> findByTemplateIdAndCode(Integer templateId, String paramCode) {
        String sql = "SELECT * FROM template_param WHERE template_id = ? AND param_code = ?";
        List<TemplateParam> results = jdbcTemplate.query(sql, ROW_MAPPER, templateId, paramCode);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 查询必填参数
     */
    public List<TemplateParam> findRequiredByTemplateId(Integer templateId) {
        String sql = "SELECT * FROM template_param WHERE template_id = ? AND is_required = 1 " +
                "ORDER BY display_order";
        return jdbcTemplate.query(sql, ROW_MAPPER, templateId);
    }

    /**
     * 删除参数
     */
    public int deleteById(Integer id) {
        String sql = "DELETE FROM template_param WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 删除某模板的所有参数
     */
    public int deleteByTemplateId(Integer templateId) {
        String sql = "DELETE FROM template_param WHERE template_id = ?";
        return jdbcTemplate.update(sql, templateId);
    }

    /**
     * 清空所有数据
     */
    public int deleteAll() {
        String sql = "DELETE FROM template_param";
        return jdbcTemplate.update(sql);
    }

    /**
     * 批量插入
     */
    public void batchInsert(List<TemplateParam> params) {
        String sql = "INSERT INTO template_param (template_id, param_name, param_code, param_type, " +
                "is_required, default_value, display_order) VALUES (?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, params, params.size(), (ps, param) -> {
            ps.setInt(1, param.getTemplateId());
            ps.setString(2, param.getParamName());
            ps.setString(3, param.getParamCode());
            ps.setString(4, param.getParamType() != null ? param.getParamType().name() : "STRING");
            ps.setBoolean(5, param.getIsRequired() != null ? param.getIsRequired() : true);
            ps.setString(6, param.getDefaultValue());
            ps.setInt(7, param.getDisplayOrder() != null ? param.getDisplayOrder() : 0);
        });
    }
}
