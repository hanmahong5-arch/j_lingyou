package red.jiuzhou.pattern.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import red.jiuzhou.pattern.model.PatternField;
import red.jiuzhou.pattern.model.PatternField.FieldType;
import red.jiuzhou.pattern.model.PatternField.ValueDomainType;
import red.jiuzhou.util.DatabaseUtil;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * 字段模式表DAO
 */
public class PatternFieldDao {
    private static final Logger log = LoggerFactory.getLogger(PatternFieldDao.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<PatternField> ROW_MAPPER = (rs, rowNum) -> {
        PatternField field = new PatternField();
        field.setId(rs.getInt("id"));
        field.setSchemaId(rs.getInt("schema_id"));
        field.setFieldName(rs.getString("field_name"));
        field.setFieldPath(rs.getString("field_path"));
        field.setIsAttribute(rs.getBoolean("is_attribute"));

        String typeStr = rs.getString("inferred_type");
        if (typeStr != null) {
            try {
                field.setInferredType(FieldType.valueOf(typeStr));
            } catch (IllegalArgumentException e) {
                field.setInferredType(FieldType.STRING);
            }
        }

        String domainStr = rs.getString("value_domain_type");
        if (domainStr != null) {
            try {
                field.setValueDomainType(ValueDomainType.valueOf(domainStr));
            } catch (IllegalArgumentException e) {
                field.setValueDomainType(ValueDomainType.UNBOUNDED);
            }
        }

        field.setValueMin(rs.getString("value_min"));
        field.setValueMax(rs.getString("value_max"));
        field.setValueEnum(rs.getString("value_enum"));
        field.setReferenceTarget(rs.getString("reference_target"));
        field.setIsBonusAttr(rs.getBoolean("is_bonus_attr"));
        field.setBonusAttrSlot(rs.getString("bonus_attr_slot"));
        field.setOccurrenceRate(rs.getBigDecimal("occurrence_rate"));
        field.setNullRate(rs.getBigDecimal("null_rate"));
        field.setDistinctCount(rs.getInt("distinct_count"));
        field.setTotalCount(rs.getInt("total_count"));
        field.setSampleValues(rs.getString("sample_values"));
        field.setCreatedAt(rs.getTimestamp("created_at"));
        field.setUpdatedAt(rs.getTimestamp("updated_at"));
        return field;
    };

    public PatternFieldDao() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    public PatternFieldDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入新的字段模式
     */
    public int insert(PatternField field) {
        String sql = "INSERT INTO pattern_field (schema_id, field_name, field_path, is_attribute, " +
                "inferred_type, value_domain_type, value_min, value_max, value_enum, reference_target, " +
                "is_bonus_attr, bonus_attr_slot, occurrence_rate, null_rate, distinct_count, " +
                "total_count, sample_values) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, field.getSchemaId());
            ps.setString(2, field.getFieldName());
            ps.setString(3, field.getFieldPath());
            ps.setBoolean(4, field.getIsAttribute() != null && field.getIsAttribute());
            ps.setString(5, field.getInferredType() != null ? field.getInferredType().name() : "STRING");
            ps.setString(6, field.getValueDomainType() != null ? field.getValueDomainType().name() : "UNBOUNDED");
            ps.setString(7, field.getValueMin());
            ps.setString(8, field.getValueMax());
            ps.setString(9, field.getValueEnum());
            ps.setString(10, field.getReferenceTarget());
            ps.setBoolean(11, field.getIsBonusAttr() != null && field.getIsBonusAttr());
            ps.setString(12, field.getBonusAttrSlot());
            ps.setBigDecimal(13, field.getOccurrenceRate() != null ? field.getOccurrenceRate() : BigDecimal.ZERO);
            ps.setBigDecimal(14, field.getNullRate() != null ? field.getNullRate() : BigDecimal.ZERO);
            ps.setInt(15, field.getDistinctCount() != null ? field.getDistinctCount() : 0);
            ps.setInt(16, field.getTotalCount() != null ? field.getTotalCount() : 0);
            ps.setString(17, field.getSampleValues());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        int id = key != null ? key.intValue() : -1;
        field.setId(id);
        return id;
    }

    /**
     * 更新字段模式
     */
    public int update(PatternField field) {
        String sql = "UPDATE pattern_field SET field_name = ?, field_path = ?, is_attribute = ?, " +
                "inferred_type = ?, value_domain_type = ?, value_min = ?, value_max = ?, " +
                "value_enum = ?, reference_target = ?, is_bonus_attr = ?, bonus_attr_slot = ?, " +
                "occurrence_rate = ?, null_rate = ?, distinct_count = ?, total_count = ?, " +
                "sample_values = ? WHERE id = ?";

        return jdbcTemplate.update(sql,
                field.getFieldName(),
                field.getFieldPath(),
                field.getIsAttribute(),
                field.getInferredType() != null ? field.getInferredType().name() : "STRING",
                field.getValueDomainType() != null ? field.getValueDomainType().name() : "UNBOUNDED",
                field.getValueMin(),
                field.getValueMax(),
                field.getValueEnum(),
                field.getReferenceTarget(),
                field.getIsBonusAttr(),
                field.getBonusAttrSlot(),
                field.getOccurrenceRate(),
                field.getNullRate(),
                field.getDistinctCount(),
                field.getTotalCount(),
                field.getSampleValues(),
                field.getId());
    }

    /**
     * 根据ID查询
     */
    public Optional<PatternField> findById(Integer id) {
        String sql = "SELECT * FROM pattern_field WHERE id = ?";
        List<PatternField> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据模式ID查询所有字段
     */
    public List<PatternField> findBySchemaId(Integer schemaId) {
        String sql = "SELECT * FROM pattern_field WHERE schema_id = ? ORDER BY field_name";
        return jdbcTemplate.query(sql, ROW_MAPPER, schemaId);
    }

    /**
     * 根据模式ID和字段名查询
     */
    public Optional<PatternField> findBySchemaIdAndFieldName(Integer schemaId, String fieldName) {
        String sql = "SELECT * FROM pattern_field WHERE schema_id = ? AND field_name = ?";
        List<PatternField> results = jdbcTemplate.query(sql, ROW_MAPPER, schemaId, fieldName);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 查询所有属性增益字段
     */
    public List<PatternField> findBonusAttrFields() {
        String sql = "SELECT * FROM pattern_field WHERE is_bonus_attr = 1 ORDER BY schema_id, bonus_attr_slot";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /**
     * 查询所有引用类型字段
     */
    public List<PatternField> findReferenceFields() {
        String sql = "SELECT * FROM pattern_field WHERE inferred_type = 'REFERENCE' ORDER BY schema_id, field_name";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /**
     * 查询所有枚举类型字段
     */
    public List<PatternField> findEnumFields() {
        String sql = "SELECT * FROM pattern_field WHERE inferred_type = 'ENUM' ORDER BY schema_id, field_name";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /**
     * 删除字段模式
     */
    public int deleteById(Integer id) {
        String sql = "DELETE FROM pattern_field WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 删除某个模式下的所有字段
     */
    public int deleteBySchemaId(Integer schemaId) {
        String sql = "DELETE FROM pattern_field WHERE schema_id = ?";
        return jdbcTemplate.update(sql, schemaId);
    }

    /**
     * 清空所有数据
     */
    public int deleteAll() {
        String sql = "DELETE FROM pattern_field";
        return jdbcTemplate.update(sql);
    }

    /**
     * 保存或更新
     */
    public PatternField saveOrUpdate(PatternField field) {
        Optional<PatternField> existing = findBySchemaIdAndFieldName(field.getSchemaId(), field.getFieldName());
        if (existing.isPresent()) {
            field.setId(existing.get().getId());
            update(field);
        } else {
            insert(field);
        }
        return field;
    }

    /**
     * 批量插入
     */
    public void batchInsert(List<PatternField> fields) {
        String sql = "INSERT INTO pattern_field (schema_id, field_name, field_path, is_attribute, " +
                "inferred_type, value_domain_type, is_bonus_attr, bonus_attr_slot) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, fields, fields.size(), (ps, field) -> {
            ps.setInt(1, field.getSchemaId());
            ps.setString(2, field.getFieldName());
            ps.setString(3, field.getFieldPath());
            ps.setBoolean(4, field.getIsAttribute() != null && field.getIsAttribute());
            ps.setString(5, field.getInferredType() != null ? field.getInferredType().name() : "STRING");
            ps.setString(6, field.getValueDomainType() != null ? field.getValueDomainType().name() : "UNBOUNDED");
            ps.setBoolean(7, field.getIsBonusAttr() != null && field.getIsBonusAttr());
            ps.setString(8, field.getBonusAttrSlot());
        });
    }
}
