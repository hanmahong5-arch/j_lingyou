package red.jiuzhou.pattern.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import red.jiuzhou.pattern.model.PatternRef;
import red.jiuzhou.pattern.model.PatternRef.RefType;
import red.jiuzhou.util.DatabaseUtil;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * 引用关系表DAO
 */
public class PatternRefDao {
    private static final Logger log = LoggerFactory.getLogger(PatternRefDao.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<PatternRef> ROW_MAPPER = (rs, rowNum) -> {
        PatternRef ref = new PatternRef();
        ref.setId(rs.getInt("id"));
        ref.setSourceSchemaId(rs.getInt("source_schema_id"));
        ref.setSourceFieldId(rs.getInt("source_field_id"));
        ref.setSourceFieldName(rs.getString("source_field_name"));
        ref.setTargetSchemaId(rs.getInt("target_schema_id"));
        ref.setTargetFieldName(rs.getString("target_field_name"));
        ref.setTargetTableName(rs.getString("target_table_name"));

        String refTypeStr = rs.getString("ref_type");
        if (refTypeStr != null) {
            try {
                ref.setRefType(RefType.valueOf(refTypeStr));
            } catch (IllegalArgumentException e) {
                ref.setRefType(RefType.ID_REFERENCE);
            }
        }

        ref.setConfidence(rs.getBigDecimal("confidence"));
        ref.setIsVerified(rs.getBoolean("is_verified"));
        ref.setSamplePairs(rs.getString("sample_pairs"));
        ref.setCreatedAt(rs.getTimestamp("created_at"));
        ref.setUpdatedAt(rs.getTimestamp("updated_at"));
        return ref;
    };

    public PatternRefDao() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    public PatternRefDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入新的引用关系
     */
    public int insert(PatternRef ref) {
        String sql = "INSERT INTO pattern_ref (source_schema_id, source_field_id, source_field_name, " +
                "target_schema_id, target_field_name, target_table_name, ref_type, confidence, " +
                "is_verified, sample_pairs) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, ref.getSourceSchemaId());
            ps.setInt(2, ref.getSourceFieldId());
            ps.setString(3, ref.getSourceFieldName());
            if (ref.getTargetSchemaId() != null) {
                ps.setInt(4, ref.getTargetSchemaId());
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.setString(5, ref.getTargetFieldName());
            ps.setString(6, ref.getTargetTableName());
            ps.setString(7, ref.getRefType() != null ? ref.getRefType().name() : "ID_REFERENCE");
            ps.setBigDecimal(8, ref.getConfidence() != null ? ref.getConfidence() : new BigDecimal("0.5"));
            ps.setBoolean(9, ref.getIsVerified() != null && ref.getIsVerified());
            ps.setString(10, ref.getSamplePairs());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        int id = key != null ? key.intValue() : -1;
        ref.setId(id);
        return id;
    }

    /**
     * 更新引用关系
     */
    public int update(PatternRef ref) {
        String sql = "UPDATE pattern_ref SET target_schema_id = ?, target_field_name = ?, " +
                "target_table_name = ?, ref_type = ?, confidence = ?, is_verified = ?, " +
                "sample_pairs = ? WHERE id = ?";

        return jdbcTemplate.update(sql,
                ref.getTargetSchemaId(),
                ref.getTargetFieldName(),
                ref.getTargetTableName(),
                ref.getRefType() != null ? ref.getRefType().name() : "ID_REFERENCE",
                ref.getConfidence(),
                ref.getIsVerified(),
                ref.getSamplePairs(),
                ref.getId());
    }

    /**
     * 根据ID查询
     */
    public Optional<PatternRef> findById(Integer id) {
        String sql = "SELECT * FROM pattern_ref WHERE id = ?";
        List<PatternRef> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据源字段ID查询
     */
    public List<PatternRef> findBySourceFieldId(Integer sourceFieldId) {
        String sql = "SELECT * FROM pattern_ref WHERE source_field_id = ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, sourceFieldId);
    }

    /**
     * 根据源模式ID查询
     */
    public List<PatternRef> findBySourceSchemaId(Integer sourceSchemaId) {
        String sql = "SELECT * FROM pattern_ref WHERE source_schema_id = ? ORDER BY source_field_name";
        return jdbcTemplate.query(sql, ROW_MAPPER, sourceSchemaId);
    }

    /**
     * 根据目标模式ID查询
     */
    public List<PatternRef> findByTargetSchemaId(Integer targetSchemaId) {
        String sql = "SELECT * FROM pattern_ref WHERE target_schema_id = ? ORDER BY source_field_name";
        return jdbcTemplate.query(sql, ROW_MAPPER, targetSchemaId);
    }

    /**
     * 查询所有引用关系
     */
    public List<PatternRef> findAll() {
        String sql = "SELECT * FROM pattern_ref ORDER BY source_schema_id, source_field_name";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /**
     * 按引用类型查询
     */
    public List<PatternRef> findByRefType(RefType refType) {
        String sql = "SELECT * FROM pattern_ref WHERE ref_type = ? ORDER BY confidence DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, refType.name());
    }

    /**
     * 查询高置信度的引用
     */
    public List<PatternRef> findHighConfidence(BigDecimal minConfidence) {
        String sql = "SELECT * FROM pattern_ref WHERE confidence >= ? ORDER BY confidence DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, minConfidence);
    }

    /**
     * 删除引用关系
     */
    public int deleteById(Integer id) {
        String sql = "DELETE FROM pattern_ref WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 删除某模式的所有引用关系
     */
    public int deleteBySourceSchemaId(Integer sourceSchemaId) {
        String sql = "DELETE FROM pattern_ref WHERE source_schema_id = ?";
        return jdbcTemplate.update(sql, sourceSchemaId);
    }

    /**
     * 清空所有数据
     */
    public int deleteAll() {
        String sql = "DELETE FROM pattern_ref";
        return jdbcTemplate.update(sql);
    }

    /**
     * 检查是否已存在相同的引用关系
     */
    public boolean exists(Integer sourceFieldId, String targetTableName, String targetFieldName) {
        String sql = "SELECT COUNT(*) FROM pattern_ref WHERE source_field_id = ? " +
                "AND target_table_name = ? AND target_field_name = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class,
                sourceFieldId, targetTableName, targetFieldName);
        return count != null && count > 0;
    }

    /**
     * 保存或更新
     */
    public PatternRef saveOrUpdate(PatternRef ref) {
        if (ref.getId() != null) {
            update(ref);
        } else if (exists(ref.getSourceFieldId(), ref.getTargetTableName(), ref.getTargetFieldName())) {
            // 已存在，不重复插入
            return ref;
        } else {
            insert(ref);
        }
        return ref;
    }
}
