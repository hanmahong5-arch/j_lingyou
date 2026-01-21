package red.jiuzhou.ops.db;

/**
 * SQL Server 数据库方言实现
 *
 * 实现 SQL Server 特有的 SQL 语法规则
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class SqlServerDialect implements DatabaseDialect {

    public static final SqlServerDialect INSTANCE = new SqlServerDialect();

    private SqlServerDialect() {
        // Singleton
    }

    @Override
    public String getName() {
        return "SQL Server";
    }

    @Override
    public String getQuoteChar() {
        return "\"";
    }

    @Override
    public String quote(String identifier) {
        // SQL Server supports both [] and "" for quoting
        // We use [] as it's more common in SQL Server
        return "[" + identifier + "]";
    }

    @Override
    public String getLimitClause(int limit) {
        // SQL Server uses TOP instead of LIMIT
        return "TOP " + limit;
    }

    @Override
    public String getPaginationClause(int limit, int offset) {
        // SQL Server 2012+ syntax
        // Requires ORDER BY clause before OFFSET
        return String.format("OFFSET %d ROWS FETCH NEXT %d ROWS ONLY", offset, limit);
    }

    @Override
    public String getCurrentTimestampFunction() {
        return "GETDATE()";
    }

    @Override
    public String getRandomFunction() {
        return "NEWID()";
    }

    @Override
    public String getConcatOperator() {
        return "+";
    }

    @Override
    public String getCoalesceFunction() {
        return "ISNULL";
    }

    @Override
    public String getAutoIncrementSyntax() {
        return "BIGINT IDENTITY(1,1)";
    }

    @Override
    public boolean supportsStoredProcedures() {
        return true;
    }

    @Override
    public String getCallProcedureSyntax(String procedureName) {
        return "EXEC " + procedureName;
    }

    /**
     * Build SELECT with TOP clause
     * Note: SQL Server puts TOP after SELECT, not at the end
     */
    public String buildSelectWithLimit(String columns, String table, int limit) {
        return String.format("SELECT TOP %d %s FROM %s", limit, columns, quote(table));
    }

    /**
     * Build SELECT with pagination
     * Requires ORDER BY clause
     */
    public String buildSelectWithPagination(String columns, String table,
                                            String orderBy, int limit, int offset) {
        return String.format(
                "SELECT %s FROM %s ORDER BY %s %s",
                columns,
                quote(table),
                orderBy,
                getPaginationClause(limit, offset)
        );
    }

    /**
     * Get SQL Server version query
     */
    public String getVersionQuery() {
        return "SELECT @@VERSION";
    }

    /**
     * Get database size query
     */
    public String getDatabaseSizeQuery(String database) {
        return String.format("""
                SELECT
                    DB_NAME(database_id) AS database_name,
                    SUM(size * 8 / 1024) AS size_mb
                FROM sys.master_files
                WHERE DB_NAME(database_id) = '%s'
                GROUP BY database_id
                """, database);
    }

    /**
     * Get table list query
     */
    public String getTablesQuery() {
        return """
                SELECT TABLE_NAME, TABLE_SCHEMA
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_SCHEMA, TABLE_NAME
                """;
    }

    /**
     * Get stored procedures query
     */
    public String getStoredProceduresQuery() {
        return """
                SELECT
                    p.name AS proc_name,
                    s.name AS schema_name,
                    p.create_date,
                    p.modify_date
                FROM sys.procedures p
                INNER JOIN sys.schemas s ON p.schema_id = s.schema_id
                WHERE p.is_ms_shipped = 0
                ORDER BY s.name, p.name
                """;
    }

    /**
     * Get procedure parameters query
     */
    public String getProcedureParametersQuery() {
        return """
                SELECT
                    p.name AS param_name,
                    t.name AS type_name,
                    p.max_length,
                    p.precision,
                    p.scale,
                    p.is_output
                FROM sys.parameters p
                INNER JOIN sys.procedures pr ON p.object_id = pr.object_id
                INNER JOIN sys.types t ON p.user_type_id = t.user_type_id
                WHERE pr.name = ?
                ORDER BY p.parameter_id
                """;
    }
}
