package red.jiuzhou.pattern.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import red.jiuzhou.pattern.model.AttrDictionary;
import red.jiuzhou.util.DatabaseUtil;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * 属性词汇表DAO
 */
public class AttrDictionaryDao {
    private static final Logger log = LoggerFactory.getLogger(AttrDictionaryDao.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<AttrDictionary> ROW_MAPPER = (rs, rowNum) -> {
        AttrDictionary attr = new AttrDictionary();
        attr.setId(rs.getInt("id"));
        attr.setAttrCode(rs.getString("attr_code"));
        attr.setAttrName(rs.getString("attr_name"));
        attr.setAttrCategory(rs.getString("attr_category"));
        attr.setTypicalMin(rs.getBigDecimal("typical_min"));
        attr.setTypicalMax(rs.getBigDecimal("typical_max"));
        attr.setValueUnit(rs.getString("value_unit"));
        attr.setIsPercentage(rs.getBoolean("is_percentage"));
        attr.setUsedInItems(rs.getInt("used_in_items"));
        attr.setUsedInTitles(rs.getInt("used_in_titles"));
        attr.setUsedInPets(rs.getInt("used_in_pets"));
        attr.setUsedInSkills(rs.getInt("used_in_skills"));
        attr.setUsedInBuffs(rs.getInt("used_in_buffs"));
        attr.setTotalUsage(rs.getInt("total_usage"));
        attr.setSourceFile(rs.getString("source_file"));
        attr.setDescription(rs.getString("description"));
        attr.setCreatedAt(rs.getTimestamp("created_at"));
        attr.setUpdatedAt(rs.getTimestamp("updated_at"));
        return attr;
    };

    public AttrDictionaryDao() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    public AttrDictionaryDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入新的属性定义
     */
    public int insert(AttrDictionary attr) {
        String sql = "INSERT INTO attr_dictionary (attr_code, attr_name, attr_category, " +
                "typical_min, typical_max, value_unit, is_percentage, used_in_items, " +
                "used_in_titles, used_in_pets, used_in_skills, used_in_buffs, total_usage, " +
                "source_file, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, attr.getAttrCode());
            ps.setString(2, attr.getAttrName());
            ps.setString(3, attr.getAttrCategory());
            ps.setBigDecimal(4, attr.getTypicalMin());
            ps.setBigDecimal(5, attr.getTypicalMax());
            ps.setString(6, attr.getValueUnit());
            ps.setBoolean(7, attr.getIsPercentage() != null && attr.getIsPercentage());
            ps.setInt(8, attr.getUsedInItems() != null ? attr.getUsedInItems() : 0);
            ps.setInt(9, attr.getUsedInTitles() != null ? attr.getUsedInTitles() : 0);
            ps.setInt(10, attr.getUsedInPets() != null ? attr.getUsedInPets() : 0);
            ps.setInt(11, attr.getUsedInSkills() != null ? attr.getUsedInSkills() : 0);
            ps.setInt(12, attr.getUsedInBuffs() != null ? attr.getUsedInBuffs() : 0);
            ps.setInt(13, attr.getTotalUsage() != null ? attr.getTotalUsage() : 0);
            ps.setString(14, attr.getSourceFile());
            ps.setString(15, attr.getDescription());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        int id = key != null ? key.intValue() : -1;
        attr.setId(id);
        return id;
    }

    /**
     * 更新属性定义
     */
    public int update(AttrDictionary attr) {
        String sql = "UPDATE attr_dictionary SET attr_name = ?, attr_category = ?, " +
                "typical_min = ?, typical_max = ?, value_unit = ?, is_percentage = ?, " +
                "used_in_items = ?, used_in_titles = ?, used_in_pets = ?, used_in_skills = ?, " +
                "used_in_buffs = ?, total_usage = ?, source_file = ?, description = ? " +
                "WHERE id = ?";

        return jdbcTemplate.update(sql,
                attr.getAttrName(),
                attr.getAttrCategory(),
                attr.getTypicalMin(),
                attr.getTypicalMax(),
                attr.getValueUnit(),
                attr.getIsPercentage(),
                attr.getUsedInItems(),
                attr.getUsedInTitles(),
                attr.getUsedInPets(),
                attr.getUsedInSkills(),
                attr.getUsedInBuffs(),
                attr.getTotalUsage(),
                attr.getSourceFile(),
                attr.getDescription(),
                attr.getId());
    }

    /**
     * 更新使用统计
     */
    public int updateUsage(Integer id, int usedInItems, int usedInTitles, int usedInPets,
                          int usedInSkills, int usedInBuffs, int totalUsage) {
        String sql = "UPDATE attr_dictionary SET used_in_items = ?, used_in_titles = ?, " +
                "used_in_pets = ?, used_in_skills = ?, used_in_buffs = ?, total_usage = ? " +
                "WHERE id = ?";
        return jdbcTemplate.update(sql, usedInItems, usedInTitles, usedInPets,
                usedInSkills, usedInBuffs, totalUsage, id);
    }

    /**
     * 增加使用计数
     */
    public int incrementUsage(String attrCode, String usageType) {
        String column;
        switch (usageType.toLowerCase()) {
            case "item":
            case "items":
                column = "used_in_items";
                break;
            case "title":
            case "titles":
                column = "used_in_titles";
                break;
            case "pet":
            case "pets":
            case "familiar":
                column = "used_in_pets";
                break;
            case "skill":
            case "skills":
                column = "used_in_skills";
                break;
            case "buff":
            case "buffs":
            case "effect":
                column = "used_in_buffs";
                break;
            default:
                column = null;
        }

        if (column == null) {
            String sql = "UPDATE attr_dictionary SET total_usage = total_usage + 1 WHERE attr_code = ?";
            return jdbcTemplate.update(sql, attrCode);
        } else {
            // PostgreSQL: Use double quotes for column names
            String sql = "UPDATE attr_dictionary SET \"" + column + "\" = \"" + column + "\" + 1, " +
                    "total_usage = total_usage + 1 WHERE attr_code = ?";
            return jdbcTemplate.update(sql, attrCode);
        }
    }

    /**
     * 根据ID查询
     */
    public Optional<AttrDictionary> findById(Integer id) {
        String sql = "SELECT * FROM attr_dictionary WHERE id = ?";
        List<AttrDictionary> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据属性代码查询
     */
    public Optional<AttrDictionary> findByAttrCode(String attrCode) {
        String sql = "SELECT * FROM attr_dictionary WHERE attr_code = ?";
        List<AttrDictionary> results = jdbcTemplate.query(sql, ROW_MAPPER, attrCode);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 查询所有属性
     */
    public List<AttrDictionary> findAll() {
        String sql = "SELECT * FROM attr_dictionary ORDER BY attr_category, attr_code";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /**
     * 按分类查询
     */
    public List<AttrDictionary> findByCategory(String category) {
        String sql = "SELECT * FROM attr_dictionary WHERE attr_category = ? ORDER BY attr_code";
        return jdbcTemplate.query(sql, ROW_MAPPER, category);
    }

    /**
     * 查询高频使用的属性
     */
    public List<AttrDictionary> findHighUsage(int minUsage) {
        String sql = "SELECT * FROM attr_dictionary WHERE total_usage >= ? ORDER BY total_usage DESC";
        return jdbcTemplate.query(sql, ROW_MAPPER, minUsage);
    }

    /**
     * 查询所有属性代码列表
     */
    public List<String> findAllAttrCodes() {
        String sql = "SELECT attr_code FROM attr_dictionary ORDER BY attr_code";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    /**
     * 检查属性代码是否存在
     */
    public boolean existsByAttrCode(String attrCode) {
        String sql = "SELECT COUNT(*) FROM attr_dictionary WHERE attr_code = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, attrCode);
        return count != null && count > 0;
    }

    /**
     * 获取属性数量
     */
    public int count() {
        String sql = "SELECT COUNT(*) FROM attr_dictionary";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    /**
     * 删除属性
     */
    public int deleteById(Integer id) {
        String sql = "DELETE FROM attr_dictionary WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    /**
     * 清空所有数据
     */
    public int deleteAll() {
        String sql = "DELETE FROM attr_dictionary";
        return jdbcTemplate.update(sql);
    }

    /**
     * 保存或更新
     */
    public AttrDictionary saveOrUpdate(AttrDictionary attr) {
        Optional<AttrDictionary> existing = findByAttrCode(attr.getAttrCode());
        if (existing.isPresent()) {
            attr.setId(existing.get().getId());
            // 保留已有的使用统计
            AttrDictionary old = existing.get();
            if (attr.getUsedInItems() == null || attr.getUsedInItems() == 0) {
                attr.setUsedInItems(old.getUsedInItems());
            }
            if (attr.getUsedInTitles() == null || attr.getUsedInTitles() == 0) {
                attr.setUsedInTitles(old.getUsedInTitles());
            }
            if (attr.getUsedInPets() == null || attr.getUsedInPets() == 0) {
                attr.setUsedInPets(old.getUsedInPets());
            }
            if (attr.getUsedInSkills() == null || attr.getUsedInSkills() == 0) {
                attr.setUsedInSkills(old.getUsedInSkills());
            }
            if (attr.getUsedInBuffs() == null || attr.getUsedInBuffs() == 0) {
                attr.setUsedInBuffs(old.getUsedInBuffs());
            }
            if (attr.getTotalUsage() == null || attr.getTotalUsage() == 0) {
                attr.setTotalUsage(old.getTotalUsage());
            }
            update(attr);
        } else {
            insert(attr);
        }
        return attr;
    }

    /**
     * 批量插入 (PostgreSQL)
     */
    public void batchInsert(List<AttrDictionary> attrs) {
        String sql = "INSERT INTO attr_dictionary (attr_code, attr_name, attr_category, " +
                "value_unit, is_percentage, source_file, description) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (attr_code) DO UPDATE SET attr_name = EXCLUDED.attr_name, " +
                "attr_category = EXCLUDED.attr_category";

        jdbcTemplate.batchUpdate(sql, attrs, attrs.size(), (ps, attr) -> {
            ps.setString(1, attr.getAttrCode());
            ps.setString(2, attr.getAttrName());
            ps.setString(3, attr.getAttrCategory());
            ps.setString(4, attr.getValueUnit());
            ps.setBoolean(5, attr.getIsPercentage() != null && attr.getIsPercentage());
            ps.setString(6, attr.getSourceFile());
            ps.setString(7, attr.getDescription());
        });
    }
}
