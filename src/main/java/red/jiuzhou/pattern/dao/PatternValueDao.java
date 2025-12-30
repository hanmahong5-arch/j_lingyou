package red.jiuzhou.pattern.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import red.jiuzhou.pattern.model.PatternValue;
import red.jiuzhou.util.DatabaseUtil;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * 值域分布表DAO
 */
public class PatternValueDao {
    private static final Logger log = LoggerFactory.getLogger(PatternValueDao.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<PatternValue> ROW_MAPPER = (rs, rowNum) -> {
        PatternValue value = new PatternValue();
        value.setId(rs.getInt("id"));
        value.setFieldId(rs.getInt("field_id"));
        value.setValueContent(rs.getString("value_content"));
        value.setValueDisplay(rs.getString("value_display"));
        value.setOccurrenceCount(rs.getInt("occurrence_count"));
        value.setPercentage(rs.getBigDecimal("percentage"));
        value.setSourceFiles(rs.getString("source_files"));
        value.setFirstSeenFile(rs.getString("first_seen_file"));
        value.setCreatedAt(rs.getTimestamp("created_at"));
        value.setUpdatedAt(rs.getTimestamp("updated_at"));
        return value;
    };

    public PatternValueDao() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    public PatternValueDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入新的值分布
     */
    public int insert(PatternValue value) {
        String sql = "INSERT INTO pattern_value (field_id, value_content, value_display, " +
                "occurrence_count, percentage, source_files, first_seen_file) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, value.getFieldId());
            ps.setString(2, value.getValueContent());
            ps.setString(3, value.getValueDisplay());
            ps.setInt(4, value.getOccurrenceCount() != null ? value.getOccurrenceCount() : 1);
            ps.setBigDecimal(5, value.getPercentage() != null ? value.getPercentage() : BigDecimal.ZERO);
            ps.setString(6, value.getSourceFiles());
            ps.setString(7, value.getFirstSeenFile());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        int id = key != null ? key.intValue() : -1;
        value.setId(id);
        return id;
    }

    /**
     * 更新值分布
     */
    public int update(PatternValue value) {
        String sql = "UPDATE pattern_value SET value_display = ?, occurrence_count = ?, " +
                "percentage = ?, source_files = ? WHERE id = ?";

        return jdbcTemplate.update(sql,
                value.getValueDisplay(),
                value.getOccurrenceCount(),
                value.getPercentage(),
                value.getSourceFiles(),
                value.getId());
    }

    /**
     * 增加出现次数
     */
    public int incrementCount(Integer id) {
        String sql = "UPDATE pattern_value SET occurrence_count = occurrence_count + 1 WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 根据ID查询
     */
    public Optional<PatternValue> findById(Integer id) {
        String sql = "SELECT * FROM pattern_value WHERE id = ?";
        List<PatternValue> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据字段ID查询所有值
     */
    public List<PatternValue> findByFieldId(Integer fieldId) {
        String sql = "SELECT * FROM pattern_value WHERE field_id = ? ORDER BY occurrence_count DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, fieldId);
    }

    /**
     * 根据字段ID和值内容查询
     */
    public Optional<PatternValue> findByFieldIdAndValue(Integer fieldId, String valueContent) {
        String sql = "SELECT * FROM pattern_value WHERE field_id = ? AND value_content = ?";
        List<PatternValue> results = jdbcTemplate.query(sql, ROW_MAPPER, fieldId, valueContent);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 查询某字段的Top N值
     */
    public List<PatternValue> findTopByFieldId(Integer fieldId, int limit) {
        String sql = "SELECT * FROM pattern_value WHERE field_id = ? ORDER BY occurrence_count DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, fieldId, limit);
    }

    /**
     * 删除值分布
     */
    public int deleteById(Integer id) {
        String sql = "DELETE FROM pattern_value WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 删除某字段的所有值分布
     */
    public int deleteByFieldId(Integer fieldId) {
        String sql = "DELETE FROM pattern_value WHERE field_id = ?";
        return jdbcTemplate.update(sql, fieldId);
    }

    /**
     * 清空所有数据
     */
    public int deleteAll() {
        String sql = "DELETE FROM pattern_value";
        return jdbcTemplate.update(sql);
    }

    /**
     * 保存或更新（增加计数）
     */
    public PatternValue saveOrIncrement(PatternValue value) {
        Optional<PatternValue> existing = findByFieldIdAndValue(value.getFieldId(), value.getValueContent());
        if (existing.isPresent()) {
            PatternValue old = existing.get();
            old.setOccurrenceCount(old.getOccurrenceCount() + 1);
            update(old);
            return old;
        } else {
            insert(value);
            return value;
        }
    }

    /**
     * 重新计算百分比
     */
    public void recalculatePercentages(Integer fieldId) {
        // 获取总数
        String countSql = "SELECT SUM(occurrence_count) FROM pattern_value WHERE field_id = ?";
        Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, fieldId);

        if (total != null && total > 0) {
            String updateSql = "UPDATE pattern_value SET percentage = occurrence_count * 1.0 / ? WHERE field_id = ?";
            jdbcTemplate.update(updateSql, total, fieldId);
        }
    }

    /**
     * 批量插入
     */
    public void batchInsert(List<PatternValue> values) {
        String sql = "INSERT INTO pattern_value (field_id, value_content, value_display, " +
                "occurrence_count, first_seen_file) VALUES (?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, values, values.size(), (ps, value) -> {
            ps.setInt(1, value.getFieldId());
            ps.setString(2, value.getValueContent());
            ps.setString(3, value.getValueDisplay());
            ps.setInt(4, value.getOccurrenceCount() != null ? value.getOccurrenceCount() : 1);
            ps.setString(5, value.getFirstSeenFile());
        });
    }
}
