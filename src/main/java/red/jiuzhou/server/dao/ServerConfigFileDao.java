package red.jiuzhou.server.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import red.jiuzhou.server.model.ServerConfigFile;
import red.jiuzhou.util.DatabaseUtil;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * 服务器配置文件 DAO
 */
public class ServerConfigFileDao {
    private static final Logger log = LoggerFactory.getLogger(ServerConfigFileDao.class);

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<ServerConfigFile> ROW_MAPPER = (rs, rowNum) -> {
        ServerConfigFile file = new ServerConfigFile();
        file.setId(rs.getInt("id"));
        file.setFileName(rs.getString("file_name"));
        file.setFilePath(rs.getString("file_path"));
        file.setTableName(rs.getString("table_name"));
        file.setIsServerLoaded(rs.getBoolean("is_server_loaded"));
        file.setLoadPriority(rs.getInt("load_priority"));
        file.setServerModule(rs.getString("server_module"));
        file.setFileCategory(rs.getString("file_category"));
        file.setFileEncoding(rs.getString("file_encoding"));
        file.setFileSize(rs.getLong("file_size"));
        file.setLastValidationTime(rs.getTimestamp("last_validation_time"));
        file.setValidationStatus(rs.getString("validation_status"));
        file.setValidationErrors(rs.getString("validation_errors"));
        file.setDependsOn(rs.getString("depends_on"));
        file.setReferencedBy(rs.getString("referenced_by"));
        file.setDesignerNotes(rs.getString("designer_notes"));
        file.setIsCritical(rs.getBoolean("is_critical"));
        file.setIsDeprecated(rs.getBoolean("is_deprecated"));
        file.setImportCount(rs.getInt("import_count"));
        file.setExportCount(rs.getInt("export_count"));
        file.setLastImportTime(rs.getTimestamp("last_import_time"));
        file.setLastExportTime(rs.getTimestamp("last_export_time"));
        file.setCreatedAt(rs.getTimestamp("created_at"));
        file.setUpdatedAt(rs.getTimestamp("updated_at"));
        return file;
    };

    public ServerConfigFileDao() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    public ServerConfigFileDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入新的配置文件记录
     */
    public int insert(ServerConfigFile file) {
        String sql = "INSERT INTO server_config_files " +
                "(file_name, file_path, table_name, is_server_loaded, load_priority, server_module, " +
                "file_category, file_encoding, file_size, validation_status, is_critical, is_deprecated) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, file.getFileName());
            ps.setString(2, file.getFilePath());
            ps.setString(3, file.getTableName());
            ps.setBoolean(4, file.getIsServerLoaded() != null ? file.getIsServerLoaded() : false);
            ps.setInt(5, file.getLoadPriority() != null ? file.getLoadPriority() : 3);
            ps.setString(6, file.getServerModule());
            ps.setString(7, file.getFileCategory());
            ps.setString(8, file.getFileEncoding());
            ps.setObject(9, file.getFileSize());
            ps.setString(10, file.getValidationStatus());
            ps.setBoolean(11, file.getIsCritical() != null ? file.getIsCritical() : false);
            ps.setBoolean(12, file.getIsDeprecated() != null ? file.getIsDeprecated() : false);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        int id = key != null ? key.intValue() : -1;
        file.setId(id);
        return id;
    }

    /**
     * 更新配置文件记录
     */
    public int update(ServerConfigFile file) {
        String sql = "UPDATE server_config_files SET " +
                "file_path = ?, table_name = ?, is_server_loaded = ?, load_priority = ?, " +
                "server_module = ?, file_category = ?, file_encoding = ?, file_size = ?, " +
                "validation_status = ?, validation_errors = ?, depends_on = ?, referenced_by = ?, " +
                "designer_notes = ?, is_critical = ?, is_deprecated = ? " +
                "WHERE id = ?";

        return jdbcTemplate.update(sql,
                file.getFilePath(), file.getTableName(), file.getIsServerLoaded(), file.getLoadPriority(),
                file.getServerModule(), file.getFileCategory(), file.getFileEncoding(), file.getFileSize(),
                file.getValidationStatus(), file.getValidationErrors(), file.getDependsOn(), file.getReferencedBy(),
                file.getDesignerNotes(), file.getIsCritical(), file.getIsDeprecated(),
                file.getId());
    }

    /**
     * 根据文件名查询
     */
    public Optional<ServerConfigFile> findByFileName(String fileName) {
        String sql = "SELECT * FROM server_config_files WHERE file_name = ?";
        List<ServerConfigFile> results = jdbcTemplate.query(sql, ROW_MAPPER, fileName);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 根据表名查询
     */
    public Optional<ServerConfigFile> findByTableName(String tableName) {
        String sql = "SELECT * FROM server_config_files WHERE table_name = ?";
        List<ServerConfigFile> results = jdbcTemplate.query(sql, ROW_MAPPER, tableName);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 查询所有服务器加载的文件
     */
    public List<ServerConfigFile> findServerLoaded() {
        String sql = "SELECT * FROM server_config_files WHERE is_server_loaded = 1 ORDER BY load_priority, file_name";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /**
     * 查询核心配置文件
     */
    public List<ServerConfigFile> findCriticalFiles() {
        String sql = "SELECT * FROM server_config_files WHERE is_critical = 1 ORDER BY file_name";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /**
     * 按优先级查询
     */
    public List<ServerConfigFile> findByPriority(int priority) {
        String sql = "SELECT * FROM server_config_files WHERE load_priority = ? ORDER BY file_name";
        return jdbcTemplate.query(sql, ROW_MAPPER, priority);
    }

    /**
     * 按分类查询
     */
    public List<ServerConfigFile> findByCategory(String category) {
        String sql = "SELECT * FROM server_config_files WHERE file_category = ? ORDER BY file_name";
        return jdbcTemplate.query(sql, ROW_MAPPER, category);
    }

    /**
     * 查询所有文件
     */
    public List<ServerConfigFile> findAll() {
        String sql = "SELECT * FROM server_config_files ORDER BY load_priority, file_name";
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    /**
     * 增加导入次数
     */
    public void incrementImportCount(String fileName) {
        String sql = "UPDATE server_config_files SET import_count = import_count + 1, " +
                "last_import_time = NOW() WHERE file_name = ?";
        jdbcTemplate.update(sql, fileName);
    }

    /**
     * 增加导出次数
     */
    public void incrementExportCount(String fileName) {
        String sql = "UPDATE server_config_files SET export_count = export_count + 1, " +
                "last_export_time = NOW() WHERE file_name = ?";
        jdbcTemplate.update(sql, fileName);
    }

    /**
     * 批量插入或更新
     */
    public int upsert(ServerConfigFile file) {
        Optional<ServerConfigFile> existing = findByFileName(file.getFileName());
        if (existing.isPresent()) {
            file.setId(existing.get().getId());
            return update(file);
        } else {
            return insert(file);
        }
    }

    /**
     * 删除记录
     */
    public int deleteByFileName(String fileName) {
        String sql = "DELETE FROM server_config_files WHERE file_name = ?";
        return jdbcTemplate.update(sql, fileName);
    }

    /**
     * 清空所有数据
     */
    public int deleteAll() {
        String sql = "DELETE FROM server_config_files";
        return jdbcTemplate.update(sql);
    }
}
