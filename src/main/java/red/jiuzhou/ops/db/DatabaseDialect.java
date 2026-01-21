package red.jiuzhou.ops.db;

/**
 * 数据库方言接口
 *
 * 定义不同数据库系统的 SQL 语法差异，
 * 用于支持 PostgreSQL 和 SQL Server 的双数据库架构
 *
 * @author yanxq
 * @date 2026-01-16
 */
public interface DatabaseDialect {

    /**
     * Get dialect name
     */
    String getName();

    /**
     * Get identifier quote character
     * - PostgreSQL: "
     * - SQL Server: [] or "
     * - MySQL: `
     */
    String getQuoteChar();

    /**
     * Quote an identifier (table name, column name)
     */
    String quote(String identifier);

    /**
     * Get LIMIT clause syntax
     * - PostgreSQL/MySQL: LIMIT n
     * - SQL Server: TOP n
     */
    String getLimitClause(int limit);

    /**
     * Get pagination syntax
     * - PostgreSQL: LIMIT n OFFSET m
     * - SQL Server 2012+: OFFSET m ROWS FETCH NEXT n ROWS ONLY
     */
    String getPaginationClause(int limit, int offset);

    /**
     * Get current timestamp function
     * - PostgreSQL: CURRENT_TIMESTAMP
     * - SQL Server: GETDATE()
     */
    String getCurrentTimestampFunction();

    /**
     * Get random function
     * - PostgreSQL: RANDOM()
     * - SQL Server: RAND() or NEWID()
     */
    String getRandomFunction();

    /**
     * Get string concatenation operator
     * - PostgreSQL: ||
     * - SQL Server: +
     */
    String getConcatOperator();

    /**
     * Get IFNULL/COALESCE syntax
     * - PostgreSQL: COALESCE(a, b)
     * - SQL Server: ISNULL(a, b) or COALESCE(a, b)
     */
    String getCoalesceFunction();

    /**
     * Get auto-increment syntax for table creation
     * - PostgreSQL: BIGSERIAL
     * - SQL Server: BIGINT IDENTITY(1,1)
     */
    String getAutoIncrementSyntax();

    /**
     * Check if database supports stored procedures natively
     */
    boolean supportsStoredProcedures();

    /**
     * Get stored procedure call syntax
     * - PostgreSQL: CALL proc_name(args)
     * - SQL Server: EXEC proc_name args
     */
    String getCallProcedureSyntax(String procedureName);
}
