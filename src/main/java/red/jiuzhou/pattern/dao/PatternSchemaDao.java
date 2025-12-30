package red.jiuzhou.pattern.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import red.jiuzhou.pattern.model.PatternSchema;
import red.jiuzhou.util.DatabaseUtil;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * 模式分类表DAO
 */
public class PatternSchemaDao {
    private static final Logger log = LoggerFactory.getLogger(PatternSchemaDao.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<PatternSchema> ROW_MAPPER = (rs, rowNum) -> {
        PatternSchema schema = new PatternSchema();
        schema.setId(rs.getInt("id"));
        schema.setMechanismCode(rs.getString("mechanism_code"));
        schema.setMechanismName(rs.getString("mechanism_name"));
        schema.setMechanismIcon(rs.getString("mechanism_icon"));
        schema.setMechanismColor(rs.getString("mechanism_color"));
        schema.setTypicalFields(rs.getString("typical_fields"));
        schema.setTypicalStructure(rs.getString("typical_structure"));
        schema.setFileCount(rs.getInt("file_count"));
        schema.setFieldCount(rs.getInt("field_count"));
        schema.setSampleCount(rs.getInt("sample_count"));
        schema.setDescription(rs.getString("description"));
        schema.setCreatedAt(rs.getTimestamp("created_at"));
        schema.setUpdatedAt(rs.getTimestamp("updated_at"));
        return schema;
    };

    public PatternSchemaDao() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    public PatternSchemaDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入新的模式分类
     */
    public int insert(PatternSchema schema) {
        String sql = "INSERT INTO pattern_schema (mechanism_code, mechanism_name, mechanism_icon, " +
                "mechanism_color, typical_fields, typical_structure, file_count, field_count, " +
                "sample_count, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, schema.getMechanismCode());
            ps.setString(2, schema.getMechanismName());
            ps.setString(3, schema.getMechanismIcon());
            ps.setString(4, schema.getMechanismColor());
            ps.setString(5, schema.getTypicalFields());
            ps.setString(6, schema.getTypicalStructure());
            ps.setInt(7, schema.getFileCount() != null ? schema.getFileCount() : 0);
            ps.setInt(8, schema.getFieldCount() != null ? schema.getFieldCount() : 0);
            ps.setInt(9, schema.getSampleCount() != null ? schema.getSampleCount() : 0);
            ps.setString(10, schema.getDescription());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        int id = key != null ? key.intValue() : -1;
        schema.setId(id);
        return id;
    }

    /**
     * 更新模式分类
     */
    public int update(PatternSchema schema) {
        String sql = "UPDATE pattern_schema SET mechanism_name = ?, mechanism_icon = ?, " +
                "mechanism_color = ?, typical_fields = ?, typical_structure = ?, " +
                "file_count = ?, field_count = ?, sample_count = ?, description = ? " +
                "WHERE id = ?";

        return jdbcTemplate.update(sql,
                schema.getMechanismName(),
                schema.getMechanismIcon(),
                schema.getMechanismColor(),
                schema.getTypicalFields(),
                schema.getTypicalStructure(),
                schema.getFileCount(),
                schema.getFieldCount(),
                schema.getSampleCount(),
                schema.getDescription(),
                schema.getId());
    }

    /**
     * 根据ID查询
     */
    public Optional<PatternSchema> findById(Integer id) {
        String sql = "SELECT * FROM pattern_schema WHERE id = ?";
        List<PatternSchema> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据机制代码查询
     */
    public Optional<PatternSchema> findByMechanismCode(String mechanismCode) {
        String sql = "SELECT * FROM pattern_schema WHERE mechanism_code = ?";
        List<PatternSchema> results = jdbcTemplate.query(sql, ROW_MAPPER, mechanismCode);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 查询所有模式分类
     */
    public List<PatternSchema> findAll() {
        String sql = "SELECT * FROM pattern_schema ORDER BY mechanism_code";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /**
     * 删除模式分类
     */
    public int deleteById(Integer id) {
        String sql = "DELETE FROM pattern_schema WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 清空所有数据
     */
    public int deleteAll() {
        String sql = "DELETE FROM pattern_schema";
        return jdbcTemplate.update(sql);
    }

    /**
     * 检查表是否存在
     */
    public boolean tableExists() {
        try {
            jdbcTemplate.queryForObject("SELECT 1 FROM pattern_schema LIMIT 1", Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 更新统计计数
     */
    public void updateCounts(Integer id, int fileCount, int fieldCount, int sampleCount) {
        String sql = "UPDATE pattern_schema SET file_count = ?, field_count = ?, sample_count = ? WHERE id = ?";
        jdbcTemplate.update(sql, fileCount, fieldCount, sampleCount, id);
    }

    /**
     * 保存或更新（根据mechanism_code）
     */
    public PatternSchema saveOrUpdate(PatternSchema schema) {
        Optional<PatternSchema> existing = findByMechanismCode(schema.getMechanismCode());
        if (existing.isPresent()) {
            schema.setId(existing.get().getId());
            update(schema);
        } else {
            insert(schema);
        }
        return schema;
    }
}
