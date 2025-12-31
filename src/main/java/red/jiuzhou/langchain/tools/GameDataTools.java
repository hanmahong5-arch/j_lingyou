package red.jiuzhou.langchain.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.agent.security.SqlSecurityFilter;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 游戏数据工具集
 *
 * <p>使用 LangChain4j @Tool 注解定义工具，供 AI Service 自动调用。
 *
 * <p>包含的工具：
 * <ul>
 *   <li>query - 执行 SELECT 查询</li>
 *   <li>modify - 生成修改 SQL（需确认）</li>
 *   <li>analyze - 数据分析</li>
 *   <li>getTableSchema - 获取表结构</li>
 *   <li>listTables - 列出所有表</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class GameDataTools {

    private static final Logger log = LoggerFactory.getLogger(GameDataTools.class);

    private final JdbcTemplate jdbcTemplate;
    private final SqlSecurityFilter securityFilter;

    /** 默认返回行数限制 */
    private static final int DEFAULT_LIMIT = 100;

    /** 最大返回行数 */
    private static final int MAX_LIMIT = 1000;

    /** 待确认的操作队列 */
    private final Map<String, PendingOperation> pendingOperations = new LinkedHashMap<>();

    public GameDataTools(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityFilter = new SqlSecurityFilter();
        this.securityFilter.setJdbcTemplate(jdbcTemplate);
    }

    public GameDataTools(JdbcTemplate jdbcTemplate, SqlSecurityFilter securityFilter) {
        this.jdbcTemplate = jdbcTemplate;
        this.securityFilter = securityFilter;
        this.securityFilter.setJdbcTemplate(jdbcTemplate);
    }

    // ==================== 查询工具 ====================

    @Tool("执行 SELECT 查询，返回游戏数据。用于查看数据库中的游戏配置数据。")
    public QueryResult query(
            @P("要执行的 SELECT SQL 语句") String sql,
            @P("返回行数限制，默认100，最大1000") Integer limit
    ) {
        log.info("执行查询: {}", sql);

        if (sql == null || sql.trim().isEmpty()) {
            return QueryResult.error("SQL 语句不能为空");
        }

        // 安全验证
        SqlSecurityFilter.ValidationResult validation = securityFilter.validate(sql);
        if (!validation.isValid()) {
            return QueryResult.error("SQL 验证失败: " + validation.getMessage());
        }

        if (!"SELECT".equals(validation.getSqlType())) {
            return QueryResult.error("此工具只支持 SELECT 查询，请使用 modify 工具执行修改操作");
        }

        // 确保有 LIMIT
        int effectiveLimit = (limit != null) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        String finalSql = ensureLimit(sql, effectiveLimit);

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(finalSql);

            List<String> columns = new ArrayList<>();
            if (!results.isEmpty()) {
                columns.addAll(results.get(0).keySet());
            }

            log.info("查询成功，返回 {} 条记录", results.size());
            return QueryResult.success(results, columns);

        } catch (Exception e) {
            log.error("查询执行失败", e);
            return QueryResult.error("查询执行失败: " + e.getMessage());
        }
    }

    // ==================== 修改工具 ====================

    @Tool("生成数据修改 SQL（UPDATE/INSERT/DELETE），需要用户确认后执行。返回待确认操作的预览信息。")
    public ModifyResult modify(
            @P("要执行的 SQL 语句（UPDATE/INSERT/DELETE）") String sql,
            @P("操作描述，说明这个修改的目的") String description
    ) {
        log.info("生成修改操作: {}", sql);

        if (sql == null || sql.trim().isEmpty()) {
            return ModifyResult.error("SQL 语句不能为空");
        }

        if (description == null || description.trim().isEmpty()) {
            return ModifyResult.error("请提供操作描述");
        }

        // 安全验证
        SqlSecurityFilter.ValidationResult validation = securityFilter.validate(sql);
        if (!validation.isValid()) {
            return ModifyResult.error("SQL 验证失败: " + validation.getMessage());
        }

        String sqlType = validation.getSqlType();
        if ("SELECT".equals(sqlType)) {
            return ModifyResult.error("此工具不支持 SELECT，请使用 query 工具");
        }

        // 获取预览数据
        List<Map<String, Object>> previewData = getPreviewData(sql, sqlType);
        int estimatedRows = validation.getEstimatedAffectedRows();

        if (estimatedRows == 0 && !previewData.isEmpty()) {
            estimatedRows = getAffectedRowCount(sql, sqlType);
        }

        // 生成操作 ID
        String operationId = UUID.randomUUID().toString().substring(0, 8);

        // 创建待确认操作
        PendingOperation pending = new PendingOperation(
                operationId, sql, sqlType, description, estimatedRows, previewData
        );
        pendingOperations.put(operationId, pending);

        log.info("修改操作已生成，等待确认。操作ID: {}, 预计影响: {} 行", operationId, estimatedRows);

        return ModifyResult.pendingConfirmation(operationId, sqlType, description, sql, estimatedRows, previewData);
    }

    // ==================== 分析工具 ====================

    @Tool("分析游戏数据分布，返回统计信息。用于了解数据的分布特征。")
    public AnalysisResult analyze(
            @P("要分析的表名") String tableName,
            @P("要分析的列名，逗号分隔") String columns,
            @P("分析类型: distribution(分布), statistics(统计), top(排行)") String analysisType
    ) {
        log.info("执行数据分析: 表={}, 列={}, 类型={}", tableName, columns, analysisType);

        if (tableName == null || tableName.trim().isEmpty()) {
            return AnalysisResult.error("表名不能为空");
        }

        // 验证表名安全
        if (!isValidTableName(tableName)) {
            return AnalysisResult.error("无效的表名: " + tableName);
        }

        try {
            return switch (analysisType != null ? analysisType.toLowerCase() : "statistics") {
                case "distribution" -> analyzeDistribution(tableName, columns);
                case "top" -> analyzeTop(tableName, columns);
                default -> analyzeStatistics(tableName, columns);
            };
        } catch (Exception e) {
            log.error("数据分析失败", e);
            return AnalysisResult.error("数据分析失败: " + e.getMessage());
        }
    }

    // ==================== 表结构工具 ====================

    @Tool("获取指定表的结构信息，包括列名、数据类型、注释等。")
    public String getTableSchema(
            @P("要查询结构的表名") String tableName
    ) {
        log.info("获取表结构: {}", tableName);

        if (tableName == null || tableName.trim().isEmpty()) {
            return "错误: 表名不能为空";
        }

        if (!isValidTableName(tableName)) {
            return "错误: 无效的表名";
        }

        try {
            String sql = "SHOW CREATE TABLE `" + tableName + "`";
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);
            return (String) result.get("Create Table");
        } catch (Exception e) {
            return "获取表结构失败: " + e.getMessage();
        }
    }

    @Tool("列出数据库中的所有表，可选按关键字过滤。")
    public List<String> listTables(
            @P("可选的表名过滤关键字") String keyword
    ) {
        log.info("列出所有表，关键字: {}", keyword);

        try {
            String sql = "SHOW TABLES";
            List<String> tables = jdbcTemplate.queryForList(sql, String.class);

            if (keyword != null && !keyword.isEmpty()) {
                String lowerKeyword = keyword.toLowerCase();
                tables = tables.stream()
                        .filter(t -> t.toLowerCase().contains(lowerKeyword))
                        .toList();
            }

            return tables;
        } catch (Exception e) {
            log.error("列出表失败", e);
            return Collections.emptyList();
        }
    }

    // ==================== 确认/执行操作 ====================

    /**
     * 确认并执行待确认的操作
     */
    public ExecutionResult confirmAndExecute(String operationId) {
        PendingOperation pending = pendingOperations.remove(operationId);
        if (pending == null) {
            return ExecutionResult.error("未找到待确认操作: " + operationId);
        }

        log.info("执行已确认的操作: {}", operationId);

        try {
            int affectedRows = jdbcTemplate.update(pending.sql());
            log.info("操作执行成功，影响 {} 行", affectedRows);
            return ExecutionResult.success(affectedRows, pending.description());
        } catch (Exception e) {
            log.error("操作执行失败", e);
            return ExecutionResult.error("操作执行失败: " + e.getMessage());
        }
    }

    /**
     * 取消待确认的操作
     */
    public boolean cancelOperation(String operationId) {
        PendingOperation removed = pendingOperations.remove(operationId);
        if (removed != null) {
            log.info("操作已取消: {}", operationId);
            return true;
        }
        return false;
    }

    /**
     * 获取所有待确认的操作
     */
    public Collection<PendingOperation> getPendingOperations() {
        return pendingOperations.values();
    }

    // ==================== 私有方法 ====================

    private String ensureLimit(String sql, int limit) {
        String upperSql = sql.toUpperCase().trim();

        if (upperSql.contains("LIMIT")) {
            Pattern pattern = Pattern.compile("LIMIT\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(sql);
            if (matcher.find()) {
                int existingLimit = Integer.parseInt(matcher.group(1));
                if (existingLimit > MAX_LIMIT) {
                    sql = sql.substring(0, matcher.start(1)) + MAX_LIMIT + sql.substring(matcher.end(1));
                }
            }
            return sql;
        }

        return sql.trim() + " LIMIT " + limit;
    }

    private List<Map<String, Object>> getPreviewData(String sql, String sqlType) {
        if ("UPDATE".equals(sqlType) || "DELETE".equals(sqlType)) {
            String tableName = securityFilter.extractTableName(sql);
            if (tableName != null) {
                String upperSql = sql.toUpperCase();
                int whereIdx = upperSql.indexOf("WHERE");
                if (whereIdx > 0) {
                    String whereClause = sql.substring(sql.toUpperCase().indexOf("WHERE"));
                    String previewSql = String.format("SELECT * FROM `%s` %s LIMIT 5", tableName, whereClause);
                    try {
                        return jdbcTemplate.queryForList(previewSql);
                    } catch (Exception e) {
                        log.debug("获取预览数据失败: {}", e.getMessage());
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    private int getAffectedRowCount(String sql, String sqlType) {
        String tableName = securityFilter.extractTableName(sql);
        if (tableName != null) {
            String upperSql = sql.toUpperCase();
            int whereIdx = upperSql.indexOf("WHERE");
            if (whereIdx > 0) {
                String whereClause = sql.substring(sql.toUpperCase().indexOf("WHERE"));
                String countSql = String.format("SELECT COUNT(*) FROM `%s` %s", tableName, whereClause);
                try {
                    Integer count = jdbcTemplate.queryForObject(countSql, Integer.class);
                    return count != null ? count : 0;
                } catch (Exception e) {
                    log.debug("获取影响行数失败: {}", e.getMessage());
                }
            }
        }
        return 0;
    }

    private AnalysisResult analyzeStatistics(String tableName, String columns) {
        String sql = "SELECT COUNT(*) as total_rows FROM `" + tableName + "`";
        Integer totalRows = jdbcTemplate.queryForObject(sql, Integer.class);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("表名", tableName);
        stats.put("总行数", totalRows);

        if (columns != null && !columns.isEmpty()) {
            for (String col : columns.split(",")) {
                col = col.trim();
                if (!col.isEmpty() && isValidColumnName(col)) {
                    try {
                        String statSql = String.format(
                                "SELECT MIN(`%s`) as min_val, MAX(`%s`) as max_val, AVG(`%s`) as avg_val FROM `%s`",
                                col, col, col, tableName
                        );
                        Map<String, Object> colStats = jdbcTemplate.queryForMap(statSql);
                        stats.put(col + "_min", colStats.get("min_val"));
                        stats.put(col + "_max", colStats.get("max_val"));
                        stats.put(col + "_avg", colStats.get("avg_val"));
                    } catch (Exception e) {
                        stats.put(col + "_error", e.getMessage());
                    }
                }
            }
        }

        return AnalysisResult.success("statistics", stats);
    }

    private AnalysisResult analyzeDistribution(String tableName, String columns) {
        if (columns == null || columns.isEmpty()) {
            return AnalysisResult.error("分布分析需要指定列名");
        }

        String column = columns.split(",")[0].trim();
        if (!isValidColumnName(column)) {
            return AnalysisResult.error("无效的列名: " + column);
        }

        String sql = String.format(
                "SELECT `%s`, COUNT(*) as count FROM `%s` GROUP BY `%s` ORDER BY count DESC LIMIT 20",
                column, tableName, column
        );

        List<Map<String, Object>> distribution = jdbcTemplate.queryForList(sql);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("列名", column);
        result.put("分布数据", distribution);

        return AnalysisResult.success("distribution", result);
    }

    private AnalysisResult analyzeTop(String tableName, String columns) {
        if (columns == null || columns.isEmpty()) {
            return AnalysisResult.error("排行分析需要指定列名");
        }

        String column = columns.split(",")[0].trim();
        if (!isValidColumnName(column)) {
            return AnalysisResult.error("无效的列名: " + column);
        }

        String sql = String.format(
                "SELECT * FROM `%s` ORDER BY `%s` DESC LIMIT 10",
                tableName, column
        );

        List<Map<String, Object>> topData = jdbcTemplate.queryForList(sql);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("排序列", column);
        result.put("Top10数据", topData);

        return AnalysisResult.success("top", result);
    }

    private boolean isValidTableName(String name) {
        return name != null && name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }

    private boolean isValidColumnName(String name) {
        return name != null && name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }

    // ==================== 结果类型 ====================

    /**
     * 查询结果
     */
    public record QueryResult(
            boolean success,
            String message,
            List<Map<String, Object>> data,
            List<String> columns,
            int rowCount
    ) {
        public static QueryResult success(List<Map<String, Object>> data, List<String> columns) {
            return new QueryResult(true, "查询成功", data, columns, data.size());
        }

        public static QueryResult error(String message) {
            return new QueryResult(false, message, Collections.emptyList(), Collections.emptyList(), 0);
        }
    }

    /**
     * 修改结果（待确认）
     */
    public record ModifyResult(
            boolean success,
            boolean pendingConfirmation,
            String message,
            String operationId,
            String sqlType,
            String description,
            String sql,
            int estimatedRows,
            List<Map<String, Object>> previewData
    ) {
        public static ModifyResult pendingConfirmation(
                String operationId, String sqlType, String description,
                String sql, int estimatedRows, List<Map<String, Object>> previewData
        ) {
            String message = String.format(
                    "操作已生成，等待确认。类型: %s, 预计影响: %d 行。请确认或取消操作。",
                    sqlType, estimatedRows
            );
            return new ModifyResult(true, true, message, operationId, sqlType, description, sql, estimatedRows, previewData);
        }

        public static ModifyResult error(String message) {
            return new ModifyResult(false, false, message, null, null, null, null, 0, Collections.emptyList());
        }
    }

    /**
     * 执行结果
     */
    public record ExecutionResult(
            boolean success,
            String message,
            int affectedRows
    ) {
        public static ExecutionResult success(int affectedRows, String description) {
            return new ExecutionResult(true, "操作执行成功: " + description, affectedRows);
        }

        public static ExecutionResult error(String message) {
            return new ExecutionResult(false, message, 0);
        }
    }

    /**
     * 分析结果
     */
    public record AnalysisResult(
            boolean success,
            String message,
            String analysisType,
            Map<String, Object> data
    ) {
        public static AnalysisResult success(String type, Map<String, Object> data) {
            return new AnalysisResult(true, "分析完成", type, data);
        }

        public static AnalysisResult error(String message) {
            return new AnalysisResult(false, message, null, Collections.emptyMap());
        }
    }

    /**
     * 待确认操作
     */
    public record PendingOperation(
            String operationId,
            String sql,
            String sqlType,
            String description,
            int estimatedRows,
            List<Map<String, Object>> previewData
    ) {}
}
