package red.jiuzhou.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 数据库Schema提供者
 *
 * 为AI提供数据库元信息上下文,支持智能SQL生成
 *
 * 核心功能:
 * - 获取所有表名和字段信息
 * - 获取表之间的关系(外键)
 * - 缓存schema信息提高性能
 * - 生成格式化的schema描述文本
 *
 * @author yanxq
 * @date 2025-12-19
 */
public class DatabaseSchemaProvider {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaProvider.class);

    private final JdbcTemplate jdbcTemplate;

    /** Schema缓存 */
    private static final Map<String, DatabaseSchema> schemaCache = new ConcurrentHashMap<>();

    /** 缓存过期时间(5分钟) */
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000;

    /**
     * 数据库Schema模型
     */
    public static class DatabaseSchema {
        private String databaseName;
        private List<TableInfo> tables;
        private long cacheTime;

        public DatabaseSchema(String databaseName, List<TableInfo> tables) {
            this.databaseName = databaseName;
            this.tables = tables;
            this.cacheTime = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - cacheTime > CACHE_EXPIRY_MS;
        }

        public List<TableInfo> getTables() {
            return tables;
        }

        public String getDatabaseName() {
            return databaseName;
        }
    }

    /**
     * 表信息模型
     */
    public static class TableInfo {
        private String tableName;
        private String comment;
        private List<ColumnInfo> columns;
        private List<String> primaryKeys;
        private List<ForeignKeyInfo> foreignKeys;

        public TableInfo(String tableName) {
            this.tableName = tableName;
            this.columns = new ArrayList<>();
            this.primaryKeys = new ArrayList<>();
            this.foreignKeys = new ArrayList<>();
        }

        public String getTableName() {
            return tableName;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public String getComment() {
            return comment;
        }

        public List<ColumnInfo> getColumns() {
            return columns;
        }

        public List<String> getPrimaryKeys() {
            return primaryKeys;
        }

        public List<ForeignKeyInfo> getForeignKeys() {
            return foreignKeys;
        }
    }

    /**
     * 字段信息模型
     */
    public static class ColumnInfo {
        private String columnName;
        private String dataType;
        private boolean nullable;
        private String comment;
        private String defaultValue;

        public ColumnInfo(String columnName, String dataType, boolean nullable) {
            this.columnName = columnName;
            this.dataType = dataType;
            this.nullable = nullable;
        }

        public String getColumnName() {
            return columnName;
        }

        public String getDataType() {
            return dataType;
        }

        public boolean isNullable() {
            return nullable;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public String getComment() {
            return comment;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getDefaultValue() {
            return defaultValue;
        }
    }

    /**
     * 外键信息模型
     */
    public static class ForeignKeyInfo {
        private String columnName;
        private String referencedTable;
        private String referencedColumn;

        public ForeignKeyInfo(String columnName, String referencedTable, String referencedColumn) {
            this.columnName = columnName;
            this.referencedTable = referencedTable;
            this.referencedColumn = referencedColumn;
        }

        public String getColumnName() {
            return columnName;
        }

        public String getReferencedTable() {
            return referencedTable;
        }

        public String getReferencedColumn() {
            return referencedColumn;
        }
    }

    public DatabaseSchemaProvider() {
        this(DatabaseUtil.getJdbcTemplate(null));
    }

    public DatabaseSchemaProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 获取完整的数据库Schema
     *
     * @return 数据库Schema对象
     */
    public DatabaseSchema getSchema() {
        return getSchema(false);
    }

    /**
     * 获取数据库Schema
     *
     * @param forceRefresh 是否强制刷新缓存
     * @return 数据库Schema对象
     */
    public DatabaseSchema getSchema(boolean forceRefresh) {
        try (Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            String databaseName = conn.getCatalog();

            // 检查缓存
            if (!forceRefresh) {
                DatabaseSchema cached = schemaCache.get(databaseName);
                if (cached != null && !cached.isExpired()) {
                    log.debug("使用缓存的Schema: {}", databaseName);
                    return cached;
                }
            }

            log.info("加载数据库Schema: {}", databaseName);
            long startTime = System.currentTimeMillis();

            DatabaseMetaData metaData = conn.getMetaData();
            List<TableInfo> tables = new ArrayList<>();

            // 获取所有表
            try (ResultSet rs = metaData.getTables(databaseName, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    TableInfo tableInfo = new TableInfo(tableName);

                    // 表注释
                    String remarks = rs.getString("REMARKS");
                    if (remarks != null && !remarks.isEmpty()) {
                        tableInfo.setComment(remarks);
                    }

                    // 获取字段信息
                    loadColumns(metaData, databaseName, tableName, tableInfo);

                    // 获取主键
                    loadPrimaryKeys(metaData, databaseName, tableName, tableInfo);

                    // 获取外键
                    loadForeignKeys(metaData, databaseName, tableName, tableInfo);

                    tables.add(tableInfo);
                }
            }

            DatabaseSchema schema = new DatabaseSchema(databaseName, tables);
            schemaCache.put(databaseName, schema);

            log.info("Schema加载完成: {} 个表, 耗时 {} ms",
                tables.size(), System.currentTimeMillis() - startTime);

            return schema;

        } catch (Exception e) {
            log.error("加载数据库Schema失败", e);
            throw new RuntimeException("加载Schema失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载表的字段信息
     */
    private void loadColumns(DatabaseMetaData metaData, String databaseName,
                            String tableName, TableInfo tableInfo) throws Exception {
        try (ResultSet rs = metaData.getColumns(databaseName, null, tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String dataType = rs.getString("TYPE_NAME");
                int columnSize = rs.getInt("COLUMN_SIZE");
                boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                String comment = rs.getString("REMARKS");
                String defaultValue = rs.getString("COLUMN_DEF");

                // 组合数据类型和长度
                String fullType = dataType;
                if (columnSize > 0 && needsSize(dataType)) {
                    fullType = dataType + "(" + columnSize + ")";
                }

                ColumnInfo columnInfo = new ColumnInfo(columnName, fullType, nullable);
                if (comment != null && !comment.isEmpty()) {
                    columnInfo.setComment(comment);
                }
                if (defaultValue != null) {
                    columnInfo.setDefaultValue(defaultValue);
                }

                tableInfo.getColumns().add(columnInfo);
            }
        }
    }

    /**
     * 加载表的主键信息
     */
    private void loadPrimaryKeys(DatabaseMetaData metaData, String databaseName,
                                 String tableName, TableInfo tableInfo) throws Exception {
        try (ResultSet rs = metaData.getPrimaryKeys(databaseName, null, tableName)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                tableInfo.getPrimaryKeys().add(columnName);
            }
        }
    }

    /**
     * 加载表的外键信息
     */
    private void loadForeignKeys(DatabaseMetaData metaData, String databaseName,
                                 String tableName, TableInfo tableInfo) throws Exception {
        try (ResultSet rs = metaData.getImportedKeys(databaseName, null, tableName)) {
            while (rs.next()) {
                String columnName = rs.getString("FKCOLUMN_NAME");
                String refTable = rs.getString("PKTABLE_NAME");
                String refColumn = rs.getString("PKCOLUMN_NAME");

                ForeignKeyInfo fkInfo = new ForeignKeyInfo(columnName, refTable, refColumn);
                tableInfo.getForeignKeys().add(fkInfo);
            }
        }
    }

    /**
     * 判断数据类型是否需要显示长度
     */
    private boolean needsSize(String dataType) {
        String type = dataType.toUpperCase();
        return type.contains("CHAR") || type.contains("BINARY");
    }

    /**
     * 获取指定表的信息
     *
     * @param tableName 表名
     * @return 表信息对象
     */
    public TableInfo getTableInfo(String tableName) {
        DatabaseSchema schema = getSchema();
        return schema.getTables().stream()
            .filter(t -> t.getTableName().equalsIgnoreCase(tableName))
            .findFirst()
            .orElse(null);
    }

    /**
     * 获取所有表名列表
     *
     * @return 表名列表
     */
    public List<String> getAllTableNames() {
        DatabaseSchema schema = getSchema();
        return schema.getTables().stream()
            .map(TableInfo::getTableName)
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * 搜索包含指定关键字的表
     *
     * @param keyword 关键字
     * @return 匹配的表名列表
     */
    public List<String> searchTables(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        return getAllTableNames().stream()
            .filter(name -> name.toLowerCase().contains(lowerKeyword))
            .collect(Collectors.toList());
    }

    /**
     * 生成格式化的Schema描述(用于AI Prompt)
     *
     * @param includeAllTables 是否包含所有表(false则只包含核心表)
     * @return 格式化的Schema描述文本
     */
    public String getSchemaDescription(boolean includeAllTables) {
        DatabaseSchema schema = getSchema();
        StringBuilder sb = new StringBuilder();

        sb.append("# 数据库Schema\n\n");
        sb.append("数据库: ").append(schema.getDatabaseName()).append("\n");
        sb.append("表数量: ").append(schema.getTables().size()).append("\n\n");

        List<TableInfo> tables = schema.getTables();

        // 如果不包含所有表,只显示前50个核心表
        if (!includeAllTables && tables.size() > 50) {
            sb.append("(仅显示部分核心表)\n\n");
            tables = tables.stream().limit(50).collect(Collectors.toList());
        }

        for (TableInfo table : tables) {
            sb.append("## 表: ").append(table.getTableName());
            if (table.getComment() != null && !table.getComment().isEmpty()) {
                sb.append(" - ").append(table.getComment());
            }
            sb.append("\n");

            // 字段列表
            sb.append("字段:\n");
            for (ColumnInfo col : table.getColumns()) {
                sb.append("  - ").append(col.getColumnName())
                  .append(" (").append(col.getDataType()).append(")");

                if (!col.isNullable()) {
                    sb.append(" NOT NULL");
                }

                if (table.getPrimaryKeys().contains(col.getColumnName())) {
                    sb.append(" PRIMARY KEY");
                }

                if (col.getComment() != null && !col.getComment().isEmpty()) {
                    sb.append(" -- ").append(col.getComment());
                }

                sb.append("\n");
            }

            // 外键关系
            if (!table.getForeignKeys().isEmpty()) {
                sb.append("外键:\n");
                for (ForeignKeyInfo fk : table.getForeignKeys()) {
                    sb.append("  - ").append(fk.getColumnName())
                      .append(" -> ").append(fk.getReferencedTable())
                      .append(".").append(fk.getReferencedColumn()).append("\n");
                }
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 生成指定表的详细描述
     *
     * @param tableName 表名
     * @return 表的详细描述
     */
    public String getTableDescription(String tableName) {
        TableInfo table = getTableInfo(tableName);
        if (table == null) {
            return "表不存在: " + tableName;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("表: ").append(table.getTableName());
        if (table.getComment() != null && !table.getComment().isEmpty()) {
            sb.append(" - ").append(table.getComment());
        }
        sb.append("\n\n");

        sb.append("字段:\n");
        for (ColumnInfo col : table.getColumns()) {
            sb.append("  ").append(col.getColumnName())
              .append(" ").append(col.getDataType());

            if (!col.isNullable()) {
                sb.append(" NOT NULL");
            }

            if (table.getPrimaryKeys().contains(col.getColumnName())) {
                sb.append(" PRIMARY KEY");
            }

            if (col.getComment() != null && !col.getComment().isEmpty()) {
                sb.append(" -- ").append(col.getComment());
            }

            sb.append("\n");
        }

        if (!table.getForeignKeys().isEmpty()) {
            sb.append("\n外键:\n");
            for (ForeignKeyInfo fk : table.getForeignKeys()) {
                sb.append("  ").append(fk.getColumnName())
                  .append(" -> ").append(fk.getReferencedTable())
                  .append(".").append(fk.getReferencedColumn()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        schemaCache.clear();
        log.info("Schema缓存已清除");
    }

    /**
     * 预加载Schema到缓存(异步)
     */
    public void preloadSchema() {
        new Thread(() -> {
            try {
                getSchema(true);
            } catch (Exception e) {
                log.error("预加载Schema失败", e);
            }
        }, "schema-preloader").start();
    }
}
