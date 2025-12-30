package red.jiuzhou.pattern.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import red.jiuzhou.pattern.model.PatternSample;
import red.jiuzhou.util.DatabaseUtil;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * 样本数据表DAO
 */
public class PatternSampleDao {
    private static final Logger log = LoggerFactory.getLogger(PatternSampleDao.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<PatternSample> ROW_MAPPER = (rs, rowNum) -> {
        PatternSample sample = new PatternSample();
        sample.setId(rs.getInt("id"));
        sample.setSchemaId(rs.getInt("schema_id"));
        sample.setSourceFile(rs.getString("source_file"));
        sample.setSourceFileName(rs.getString("source_file_name"));
        sample.setRecordId(rs.getString("record_id"));
        sample.setRecordName(rs.getString("record_name"));
        sample.setRawXml(rs.getString("raw_xml"));
        sample.setParsedJson(rs.getString("parsed_json"));
        sample.setHasBonusAttr(rs.getBoolean("has_bonus_attr"));
        sample.setBonusAttrCount(rs.getInt("bonus_attr_count"));
        sample.setBonusAttrSummary(rs.getString("bonus_attr_summary"));
        sample.setIsTemplateCandidate(rs.getBoolean("is_template_candidate"));
        sample.setTemplateScore(rs.getBigDecimal("template_score"));
        sample.setCreatedAt(rs.getTimestamp("created_at"));
        sample.setUpdatedAt(rs.getTimestamp("updated_at"));
        return sample;
    };

    public PatternSampleDao() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    public PatternSampleDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入新的样本数据
     */
    public int insert(PatternSample sample) {
        String sql = "INSERT INTO pattern_sample (schema_id, source_file, source_file_name, " +
                "record_id, record_name, raw_xml, parsed_json, has_bonus_attr, bonus_attr_count, " +
                "bonus_attr_summary, is_template_candidate, template_score) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, sample.getSchemaId());
            ps.setString(2, sample.getSourceFile());
            ps.setString(3, sample.getSourceFileName());
            ps.setString(4, sample.getRecordId());
            ps.setString(5, sample.getRecordName());
            ps.setString(6, sample.getRawXml());
            ps.setString(7, sample.getParsedJson());
            ps.setBoolean(8, sample.getHasBonusAttr() != null && sample.getHasBonusAttr());
            ps.setInt(9, sample.getBonusAttrCount() != null ? sample.getBonusAttrCount() : 0);
            ps.setString(10, sample.getBonusAttrSummary());
            ps.setBoolean(11, sample.getIsTemplateCandidate() != null && sample.getIsTemplateCandidate());
            ps.setBigDecimal(12, sample.getTemplateScore() != null ? sample.getTemplateScore() : BigDecimal.ZERO);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        int id = key != null ? key.intValue() : -1;
        sample.setId(id);
        return id;
    }

    /**
     * 根据ID查询
     */
    public Optional<PatternSample> findById(Integer id) {
        String sql = "SELECT * FROM pattern_sample WHERE id = ?";
        List<PatternSample> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据模式ID查询
     */
    public List<PatternSample> findBySchemaId(Integer schemaId) {
        String sql = "SELECT * FROM pattern_sample WHERE schema_id = ? ORDER BY record_id";
        return jdbcTemplate.query(sql, ROW_MAPPER, schemaId);
    }

    /**
     * 根据模式ID查询（限制数量）
     */
    public List<PatternSample> findBySchemaId(Integer schemaId, int limit) {
        String sql = "SELECT * FROM pattern_sample WHERE schema_id = ? ORDER BY template_score DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, schemaId, limit);
    }

    /**
     * 查询包含属性增益的样本
     */
    public List<PatternSample> findWithBonusAttr(Integer schemaId) {
        String sql = "SELECT * FROM pattern_sample WHERE schema_id = ? AND has_bonus_attr = 1 " +
                "ORDER BY bonus_attr_count DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, schemaId);
    }

    /**
     * 查询模板候选样本
     */
    public List<PatternSample> findTemplateCandidates(Integer schemaId) {
        String sql = "SELECT * FROM pattern_sample WHERE schema_id = ? AND is_template_candidate = 1 " +
                "ORDER BY template_score DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, schemaId);
    }

    /**
     * 根据文件和记录ID查询
     */
    public Optional<PatternSample> findByFileAndRecordId(String sourceFile, String recordId) {
        String sql = "SELECT * FROM pattern_sample WHERE source_file = ? AND record_id = ?";
        List<PatternSample> results = jdbcTemplate.query(sql, ROW_MAPPER, sourceFile, recordId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 删除样本
     */
    public int deleteById(Integer id) {
        String sql = "DELETE FROM pattern_sample WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 删除某模式的所有样本
     */
    public int deleteBySchemaId(Integer schemaId) {
        String sql = "DELETE FROM pattern_sample WHERE schema_id = ?";
        return jdbcTemplate.update(sql, schemaId);
    }

    /**
     * 清空所有数据
     */
    public int deleteAll() {
        String sql = "DELETE FROM pattern_sample";
        return jdbcTemplate.update(sql);
    }

    /**
     * 获取某模式的样本数量
     */
    public int countBySchemaId(Integer schemaId) {
        String sql = "SELECT COUNT(*) FROM pattern_sample WHERE schema_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, schemaId);
        return count != null ? count : 0;
    }

    /**
     * 批量插入
     */
    public void batchInsert(List<PatternSample> samples) {
        String sql = "INSERT INTO pattern_sample (schema_id, source_file, source_file_name, " +
                "record_id, record_name, raw_xml, parsed_json, has_bonus_attr, bonus_attr_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, samples, samples.size(), (ps, sample) -> {
            ps.setInt(1, sample.getSchemaId());
            ps.setString(2, sample.getSourceFile());
            ps.setString(3, sample.getSourceFileName());
            ps.setString(4, sample.getRecordId());
            ps.setString(5, sample.getRecordName());
            ps.setString(6, sample.getRawXml());
            ps.setString(7, sample.getParsedJson());
            ps.setBoolean(8, sample.getHasBonusAttr() != null && sample.getHasBonusAttr());
            ps.setInt(9, sample.getBonusAttrCount() != null ? sample.getBonusAttrCount() : 0);
        });
    }
}
