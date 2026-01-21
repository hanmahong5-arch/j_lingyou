package red.jiuzhou.ops.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import red.jiuzhou.ops.model.StoredProcedure;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL Server 数据库连接管理
 *
 * 支持特性:
 * - SQL Server 身份验证
 * - 多数据库切换
 * - 连接池管理
 * - 存储过程元数据获取
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class SqlServerConnection {

    private static final Logger log = LoggerFactory.getLogger(SqlServerConnection.class);

    private String serverHost;
    private String username;
    private String password;
    private int port = 1433;

    private final Map<String, JdbcTemplate> jdbcTemplates = new ConcurrentHashMap<>();
    private DataSource currentDataSource;
    private String currentDatabase;

    /**
     * Create connection with connection string format:
     * Server=.;Database=LIVE_AionGM;User ID=sa;Password=xxx
     */
    public static SqlServerConnection fromConnectionString(String connectionString) {
        SqlServerConnection conn = new SqlServerConnection();
        conn.parseConnectionString(connectionString);
        return conn;
    }

    /**
     * Create connection with explicit parameters
     */
    public static SqlServerConnection create(String host, int port, String username, String password) {
        SqlServerConnection conn = new SqlServerConnection();
        conn.serverHost = host;
        conn.port = port;
        conn.username = username;
        conn.password = password;
        return conn;
    }

    private void parseConnectionString(String connectionString) {
        // Parse: Server=.;Database=LIVE_AionGM;User ID=sa;Password=xxx
        String[] parts = connectionString.split(";");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().toLowerCase();
                String value = kv[1].trim();
                switch (key) {
                    case "server" -> this.serverHost = value.equals(".") ? "localhost" : value;
                    case "database" -> this.currentDatabase = value;
                    case "user id" -> this.username = value;
                    case "password" -> this.password = value;
                }
            }
        }
    }

    /**
     * Connect to specified database
     */
    public void connect(String database) {
        this.currentDatabase = database;
        getJdbcTemplate(database);
        log.info("已连接到 SQL Server: {}:{}/{}", serverHost, port, database);
    }

    /**
     * Test connection
     */
    public boolean testConnection() {
        try {
            JdbcTemplate jdbc = getJdbcTemplate(currentDatabase);
            jdbc.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.error("连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get JdbcTemplate for specified database
     */
    public JdbcTemplate getJdbcTemplate(String database) {
        return jdbcTemplates.computeIfAbsent(database, db -> {
            DataSource ds = createDataSource(db);
            return new JdbcTemplate(ds);
        });
    }

    /**
     * Get current JdbcTemplate
     */
    public JdbcTemplate getJdbcTemplate() {
        if (currentDatabase == null) {
            throw new IllegalStateException("No database selected. Call connect() first.");
        }
        return getJdbcTemplate(currentDatabase);
    }

    private DataSource createDataSource(String database) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

        // Handle "." as localhost for SQL Server local instance
        String host = ".".equals(serverHost) ? "localhost" : serverHost;

        // JDBC URL format: jdbc:sqlserver://localhost:1433;database=xxx;encrypt=false;trustServerCertificate=true
        String url = String.format(
                "jdbc:sqlserver://%s:%d;database=%s;encrypt=false;trustServerCertificate=true;loginTimeout=10",
                host, port, database
        );

        log.debug("SQL Server 连接 URL: {}", url);
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        currentDataSource = dataSource;
        return dataSource;
    }

    /**
     * List all databases on the server
     */
    public List<String> listDatabases() {
        List<String> databases = new ArrayList<>();

        // Connect to master to list databases
        JdbcTemplate masterJdbc = getJdbcTemplate("master");
        List<Map<String, Object>> rows = masterJdbc.queryForList(
                "SELECT name FROM sys.databases WHERE database_id > 4 ORDER BY name"
        );

        for (Map<String, Object> row : rows) {
            databases.add((String) row.get("name"));
        }

        return databases;
    }

    /**
     * List all stored procedures in current database
     */
    public List<StoredProcedure> listStoredProcedures() {
        return listStoredProcedures(currentDatabase);
    }

    /**
     * List all stored procedures in specified database
     * 获取存储过程列表，包含从定义头部提取的注释
     */
    public List<StoredProcedure> listStoredProcedures(String database) {
        List<StoredProcedure> procedures = new ArrayList<>();

        JdbcTemplate jdbc = getJdbcTemplate(database);
        // 获取存储过程基本信息和定义（用于提取注释）
        String sql = """
                SELECT
                    p.name AS proc_name,
                    s.name AS schema_name,
                    p.create_date,
                    p.modify_date,
                    CAST(ep.value AS NVARCHAR(MAX)) AS ms_description,
                    SUBSTRING(m.definition, 1, 2000) AS definition_head
                FROM sys.procedures p
                INNER JOIN sys.schemas s ON p.schema_id = s.schema_id
                LEFT JOIN sys.extended_properties ep ON ep.major_id = p.object_id
                    AND ep.minor_id = 0
                    AND ep.name = 'MS_Description'
                LEFT JOIN sys.sql_modules m ON m.object_id = p.object_id
                WHERE p.is_ms_shipped = 0
                ORDER BY p.name
                """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql);

        for (Map<String, Object> row : rows) {
            String procName = (String) row.get("proc_name");
            String schemaName = (String) row.get("schema_name");
            String msDescription = row.get("ms_description") != null ? (String) row.get("ms_description") : "";
            String definitionHead = row.get("definition_head") != null ? (String) row.get("definition_head") : "";

            // 优先使用 MS_Description，否则从定义头部提取注释
            String description = msDescription;
            if (description.isEmpty() && !definitionHead.isEmpty()) {
                description = extractCommentFromDefinition(definitionHead);
            }

            StoredProcedure proc = new StoredProcedure(
                    procName,
                    schemaName,
                    description,
                    StoredProcedure.inferCategory(procName)
            );
            procedures.add(proc);
        }

        return procedures;
    }

    /**
     * 从存储过程定义头部提取注释
     * 支持格式:
     * - 单行注释: -- 注释内容
     * - 多行注释: /* 注释内容 * /
     * - Description: 描述内容
     */
    private String extractCommentFromDefinition(String definition) {
        if (definition == null || definition.isEmpty()) {
            return "";
        }

        StringBuilder comment = new StringBuilder();
        String[] lines = definition.split("\n");
        boolean inMultilineComment = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // 跳过 CREATE PROCEDURE 行
            if (trimmed.toUpperCase().contains("CREATE") &&
                trimmed.toUpperCase().contains("PROC")) {
                break;
            }

            // 处理多行注释 /* ... */
            if (trimmed.startsWith("/*")) {
                inMultilineComment = true;
                String content = trimmed.substring(2);
                if (content.contains("*/")) {
                    content = content.substring(0, content.indexOf("*/"));
                    inMultilineComment = false;
                }
                if (!content.trim().isEmpty() && !content.contains("****")) {
                    comment.append(content.trim()).append(" ");
                }
                continue;
            }

            if (inMultilineComment) {
                if (trimmed.contains("*/")) {
                    String content = trimmed.substring(0, trimmed.indexOf("*/"));
                    if (!content.trim().isEmpty() && !content.contains("****")) {
                        comment.append(content.trim()).append(" ");
                    }
                    inMultilineComment = false;
                } else if (!trimmed.isEmpty() && !trimmed.startsWith("*") &&
                           !trimmed.contains("Author") && !trimmed.contains("Date") &&
                           !trimmed.contains("====")) {
                    comment.append(trimmed).append(" ");
                }
                continue;
            }

            // 处理单行注释 --
            if (trimmed.startsWith("--")) {
                String content = trimmed.substring(2).trim();
                // 提取 Description: 后的内容
                if (content.toLowerCase().startsWith("description:")) {
                    content = content.substring(12).trim();
                }
                // 跳过分隔线和元数据行
                if (!content.isEmpty() && !content.startsWith("===") &&
                    !content.startsWith("---") && !content.contains("Author") &&
                    !content.contains("Date:") && !content.contains("Modified")) {
                    comment.append(content).append(" ");
                }
            }

            // 最多提取200字符
            if (comment.length() > 200) {
                break;
            }
        }

        return comment.toString().trim();
    }

    /**
     * Get stored procedure parameters
     */
    public List<StoredProcedure.Parameter> getProcedureParameters(String procedureName) {
        List<StoredProcedure.Parameter> params = new ArrayList<>();

        JdbcTemplate jdbc = getJdbcTemplate();
        String sql = """
                SELECT
                    p.name AS param_name,
                    t.name AS type_name,
                    p.max_length,
                    p.precision,
                    p.scale,
                    p.is_output,
                    p.has_default_value,
                    p.default_value
                FROM sys.parameters p
                INNER JOIN sys.procedures pr ON p.object_id = pr.object_id
                INNER JOIN sys.types t ON p.user_type_id = t.user_type_id
                WHERE pr.name = ?
                ORDER BY p.parameter_id
                """;

        List<Map<String, Object>> rows = jdbc.queryForList(sql, procedureName);

        for (Map<String, Object> row : rows) {
            String paramName = (String) row.get("param_name");
            String typeName = (String) row.get("type_name");
            boolean isOutput = (Boolean) row.get("is_output");
            boolean hasDefault = (Boolean) row.get("has_default_value");

            params.add(new StoredProcedure.Parameter(
                    paramName.startsWith("@") ? paramName.substring(1) : paramName,
                    typeName,
                    isOutput,
                    hasDefault
            ));
        }

        return params;
    }

    /**
     * Execute stored procedure with parameters
     */
    public List<Map<String, Object>> callProcedure(String procedureName, Map<String, Object> params) {
        JdbcTemplate jdbc = getJdbcTemplate();

        // Build EXEC statement
        StringBuilder sql = new StringBuilder("EXEC ");
        sql.append(procedureName);

        if (params != null && !params.isEmpty()) {
            sql.append(" ");
            List<String> paramStrings = new ArrayList<>();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String paramName = entry.getKey();
                if (!paramName.startsWith("@")) {
                    paramName = "@" + paramName;
                }
                paramStrings.add(paramName + " = ?");
            }
            sql.append(String.join(", ", paramStrings));
        }

        log.info("执行存储过程: {}", sql);

        if (params == null || params.isEmpty()) {
            return jdbc.queryForList(sql.toString());
        } else {
            return jdbc.queryForList(sql.toString(), params.values().toArray());
        }
    }

    /**
     * Execute stored procedure without results (for update operations)
     */
    public void executeProcedure(String procedureName, Map<String, Object> params) {
        JdbcTemplate jdbc = getJdbcTemplate();

        StringBuilder sql = new StringBuilder("EXEC ");
        sql.append(procedureName);

        if (params != null && !params.isEmpty()) {
            sql.append(" ");
            List<String> paramStrings = new ArrayList<>();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String paramName = entry.getKey();
                if (!paramName.startsWith("@")) {
                    paramName = "@" + paramName;
                }
                paramStrings.add(paramName + " = ?");
            }
            sql.append(String.join(", ", paramStrings));
        }

        log.info("执行存储过程: {}", sql);

        if (params == null || params.isEmpty()) {
            jdbc.execute(sql.toString());
        } else {
            jdbc.update(sql.toString(), params.values().toArray());
        }
    }

    /**
     * Get server version info
     */
    public String getServerVersion() {
        try {
            JdbcTemplate jdbc = getJdbcTemplate();
            return jdbc.queryForObject("SELECT @@VERSION", String.class);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * Get current database name
     */
    public String getCurrentDatabase() {
        return currentDatabase;
    }

    /**
     * Get server host
     */
    public String getServerHost() {
        return serverHost;
    }

    /**
     * Get connection status summary
     */
    public String getConnectionSummary() {
        return String.format("SQL Server: %s:%d | Database: %s",
                serverHost, port, currentDatabase);
    }

    /**
     * Close all connections
     */
    public void close() {
        jdbcTemplates.clear();
        currentDataSource = null;
        log.info("已关闭所有 SQL Server 连接");
    }
}
