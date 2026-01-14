package red.jiuzhou.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.batch.ConflictReport;
import red.jiuzhou.batch.ConflictResolutionStrategy;
import red.jiuzhou.batch.DataConflictException;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Smart Insert Executor
 *
 * Handles batch insertions with flexible conflict resolution strategies
 * Supports PostgreSQL 16 ON CONFLICT syntax
 *
 * @author Claude AI
 * @date 2026-01-14
 */
public class SmartInsertExecutor {

    private static final Logger log = LoggerFactory.getLogger(SmartInsertExecutor.class);

    /**
     * Pre-detect conflicts by querying existing primary keys
     *
     * @param tableName Table name
     * @param dataList Data to insert
     * @return Conflict report with count and key list
     */
    public static ConflictReport detectConflicts(String tableName, List<Map<String, String>> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return new ConflictReport(0, Collections.emptyList());
        }

        String primaryKey = DatabaseUtil.getPrimaryKeyColumn(tableName);
        if (primaryKey == null) {
            log.warn("Table {} has no primary key, conflict detection skipped", tableName);
            return new ConflictReport(0, Collections.emptyList());
        }

        // Extract primary key values from data
        List<String> pkValues = dataList.stream()
            .map(row -> row.get(primaryKey))
            .filter(Objects::nonNull)
            .filter(val -> !val.trim().isEmpty())
            .distinct()
            .collect(Collectors.toList());

        if (pkValues.isEmpty()) {
            return new ConflictReport(0, Collections.emptyList());
        }

        // Query existing keys (PostgreSQL: WHERE pk IN (?, ?, ...))
        String placeholders = String.join(",", Collections.nCopies(pkValues.size(), "?"));
        String sql = String.format(
            "SELECT \"%s\" FROM \"%s\" WHERE \"%s\" IN (%s)",
            primaryKey, tableName, primaryKey, placeholders
        );

        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            List<String> existingKeys = jdbcTemplate.queryForList(sql, String.class, pkValues.toArray());

            log.debug("Conflict detection for table {}: {} existing out of {} total",
                tableName, existingKeys.size(), pkValues.size());

            return new ConflictReport(existingKeys.size(), existingKeys);

        } catch (Exception e) {
            log.error("Failed to detect conflicts for table {}: {}", tableName, e.getMessage());
            // On error, assume no conflicts to avoid blocking
            return new ConflictReport(0, Collections.emptyList());
        }
    }

    /**
     * Execute batch insert with specified conflict resolution strategy
     *
     * @param tableName Table name
     * @param dataList Data to insert
     * @param strategy Conflict resolution strategy
     */
    public static void executeBatchInsert(
        String tableName,
        List<Map<String, String>> dataList,
        ConflictResolutionStrategy strategy) {

        if (dataList == null || dataList.isEmpty()) {
            log.debug("No data to insert for table {}", tableName);
            return;
        }

        log.info("Executing batch insert for table {} with strategy: {}",
            tableName, strategy.getDisplayName());

        switch (strategy) {
            case REPLACE_UPDATE:
                executeBatchUpsert(tableName, dataList);
                break;

            case SKIP_CONFLICT:
                executeBatchInsertIgnore(tableName, dataList);
                break;

            case CANCEL_IMPORT:
                // Pre-detect conflicts, throw exception if any found
                ConflictReport report = detectConflicts(tableName, dataList);
                if (report.hasConflicts()) {
                    String message = String.format(
                        "表 [%s] 发现 %d 条重复记录。\n主键值: %s\n\n请选择其他策略继续导入。",
                        tableName,
                        report.getConflictCount(),
                        String.join(", ", report.getPreviewKeys(5))
                    );
                    throw new DataConflictException(message, report.getConflictCount(), report.getConflictKeys());
                }
                // No conflicts, use standard insert
                DatabaseUtil.batchInsert(tableName, dataList);
                break;

            case SMART_MERGE:
                executeBatchSmartMerge(tableName, dataList);
                break;

            default:
                throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }
    }

    /**
     * PostgreSQL UPSERT implementation (ON CONFLICT DO UPDATE)
     */
    private static void executeBatchUpsert(String tableName, List<Map<String, String>> dataList) {
        String primaryKey = DatabaseUtil.getPrimaryKeyColumn(tableName);
        if (primaryKey == null) {
            log.warn("Table {} has no primary key, fallback to standard insert", tableName);
            DatabaseUtil.batchInsert(tableName, dataList);
            return;
        }

        // Get complete column set
        Set<String> columnSet = new LinkedHashSet<>();
        for (Map<String, String> row : dataList) {
            columnSet.addAll(row.keySet());
        }
        List<String> columns = new ArrayList<>(columnSet);

        // Build UPDATE SET clause (all columns except primary key)
        String updateSet = columns.stream()
            .filter(col -> !col.equals(primaryKey))
            .map(col -> String.format("\"%s\" = EXCLUDED.\"%s\"", col, col))
            .collect(Collectors.joining(", "));

        // Build SQL
        String sql = String.format(
            "INSERT INTO \"%s\" (%s) VALUES (%s) ON CONFLICT (\"%s\") DO UPDATE SET %s",
            tableName,
            columns.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(",")),
            String.join(",", Collections.nCopies(columns.size(), "?")),
            primaryKey,
            updateSet
        );

        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Map<String, String> row = dataList.get(i);
                    int index = 1;
                    for (String column : columns) {
                        ps.setObject(index++, row.get(column));
                    }
                }

                @Override
                public int getBatchSize() {
                    return dataList.size();
                }
            });

            log.debug("UPSERT completed for table {}: {} records", tableName, dataList.size());

        } catch (DataAccessException e) {
            handleInsertError(tableName, dataList, e);
        }
    }

    /**
     * PostgreSQL INSERT IGNORE implementation (ON CONFLICT DO NOTHING)
     */
    private static void executeBatchInsertIgnore(String tableName, List<Map<String, String>> dataList) {
        String primaryKey = DatabaseUtil.getPrimaryKeyColumn(tableName);
        if (primaryKey == null) {
            log.warn("Table {} has no primary key, fallback to standard insert", tableName);
            DatabaseUtil.batchInsert(tableName, dataList);
            return;
        }

        // Get complete column set
        Set<String> columnSet = new LinkedHashSet<>();
        for (Map<String, String> row : dataList) {
            columnSet.addAll(row.keySet());
        }
        List<String> columns = new ArrayList<>(columnSet);

        // Build SQL (ON CONFLICT DO NOTHING)
        String sql = String.format(
            "INSERT INTO \"%s\" (%s) VALUES (%s) ON CONFLICT (\"%s\") DO NOTHING",
            tableName,
            columns.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(",")),
            String.join(",", Collections.nCopies(columns.size(), "?")),
            primaryKey
        );

        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Map<String, String> row = dataList.get(i);
                    int index = 1;
                    for (String column : columns) {
                        ps.setObject(index++, row.get(column));
                    }
                }

                @Override
                public int getBatchSize() {
                    return dataList.size();
                }
            });

            log.debug("INSERT IGNORE completed for table {}: {} records", tableName, dataList.size());

        } catch (DataAccessException e) {
            handleInsertError(tableName, dataList, e);
        }
    }

    /**
     * Smart merge implementation (compare timestamps)
     * Requires updated_at field in table
     */
    private static void executeBatchSmartMerge(String tableName, List<Map<String, String>> dataList) {
        String primaryKey = DatabaseUtil.getPrimaryKeyColumn(tableName);
        if (primaryKey == null) {
            log.warn("Table {} has no primary key, fallback to UPSERT", tableName);
            executeBatchUpsert(tableName, dataList);
            return;
        }

        // Check if table has updated_at field
        List<String> columnNames = DatabaseUtil.getColumnNamesFromDb(tableName);
        boolean hasUpdatedAt = columnNames.stream()
            .anyMatch(col -> col.equalsIgnoreCase("updated_at"));

        if (!hasUpdatedAt) {
            log.warn("Table {} has no updated_at field, fallback to UPSERT", tableName);
            executeBatchUpsert(tableName, dataList);
            return;
        }

        // Build conditional UPSERT (only update if newer)
        Set<String> columnSet = new LinkedHashSet<>();
        for (Map<String, String> row : dataList) {
            columnSet.addAll(row.keySet());
        }
        List<String> columns = new ArrayList<>(columnSet);

        String updateSet = columns.stream()
            .filter(col -> !col.equals(primaryKey))
            .map(col -> String.format("\"%s\" = EXCLUDED.\"%s\"", col, col))
            .collect(Collectors.joining(", "));

        // Add condition: only update if new record is newer
        String sql = String.format(
            "INSERT INTO \"%s\" (%s) VALUES (%s) " +
            "ON CONFLICT (\"%s\") DO UPDATE SET %s " +
            "WHERE \"%s\".\"updated_at\" < EXCLUDED.\"updated_at\"",
            tableName,
            columns.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(",")),
            String.join(",", Collections.nCopies(columns.size(), "?")),
            primaryKey,
            updateSet,
            tableName
        );

        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    Map<String, String> row = dataList.get(i);
                    int index = 1;
                    for (String column : columns) {
                        ps.setObject(index++, row.get(column));
                    }
                }

                @Override
                public int getBatchSize() {
                    return dataList.size();
                }
            });

            log.debug("SMART_MERGE completed for table {}: {} records", tableName, dataList.size());

        } catch (DataAccessException e) {
            handleInsertError(tableName, dataList, e);
        }
    }

    /**
     * Handle insert errors with user-friendly messages
     */
    private static void handleInsertError(String tableName, List<Map<String, String>> dataList, DataAccessException e) {
        String originalMsg = e.getMessage();

        // Detect duplicate key error
        if (originalMsg.contains("duplicate key") || originalMsg.contains("唯一约束")) {
            // Extract primary key value from error message
            Pattern pattern = Pattern.compile("键值\"?\\((.+?)\\)\"?");
            Matcher matcher = pattern.matcher(originalMsg);
            String conflictKey = matcher.find() ? matcher.group(1) : "未知";

            String message = String.format(
                "导入失败：表 [%s] 发现重复记录 [%s]。\n\n" +
                "建议：\n" +
                "1. 使用\"覆盖更新\"策略替换旧数据\n" +
                "2. 使用\"跳过冲突\"策略保留旧数据\n" +
                "3. 检查XML文件是否包含重复数据",
                tableName, conflictKey
            );

            throw new DataConflictException(message, e);
        }

        // Other errors: re-throw with context
        log.error("Batch insert failed for table {}, data size: {}", tableName, dataList.size());
        throw new RuntimeException("批量插入失败: " + e.getMessage(), e);
    }
}
