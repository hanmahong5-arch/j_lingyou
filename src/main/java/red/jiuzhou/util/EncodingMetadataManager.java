package red.jiuzhou.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.util.Map;

/**
 * 编码元数据管理器
 *
 * 功能：
 * - 保存文件原始编码信息
 * - 查询文件原始编码信息
 * - 更新导入/导出时间
 * - 确保导入导出往返一致性
 *
 * @author Claude
 * @date 2025-12-28
 */
public class EncodingMetadataManager {

    private static final Logger log = LoggerFactory.getLogger(EncodingMetadataManager.class);

    /**
     * 保存编码元数据
     *
     * @param tableName 表名
     * @param xmlFile XML文件
     * @param encoding 编码信息
     */
    public static void saveMetadata(String tableName, File xmlFile, FileEncodingDetector.EncodingInfo encoding) {
        saveMetadata(tableName, "", xmlFile, encoding);
    }

    /**
     * 保存编码元数据（支持 mapType）
     *
     * @param tableName 表名
     * @param mapType World表的地图类型（China/Korea/Japan等），普通表传空字符串
     * @param xmlFile XML文件
     * @param encoding 编码信息
     */
    public static void saveMetadata(String tableName, String mapType, File xmlFile, FileEncodingDetector.EncodingInfo encoding) {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();

            // 计算文件哈希（用于往返验证）
            String fileHash = cn.hutool.crypto.digest.DigestUtil.md5Hex(cn.hutool.core.io.FileUtil.readBytes(xmlFile));

            // PostgreSQL: 使用 ON CONFLICT ... DO UPDATE SET
            String sql = """
                INSERT INTO file_encoding_metadata
                (table_name, map_type, original_encoding, has_bom, original_file_path, original_file_hash, file_size_bytes, last_import_time, import_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), 1)
                ON CONFLICT (table_name, map_type) DO UPDATE SET
                    original_encoding = EXCLUDED.original_encoding,
                    has_bom = EXCLUDED.has_bom,
                    original_file_path = EXCLUDED.original_file_path,
                    original_file_hash = EXCLUDED.original_file_hash,
                    file_size_bytes = EXCLUDED.file_size_bytes,
                    last_import_time = NOW(),
                    import_count = file_encoding_metadata.import_count + 1
                """;

            jdbcTemplate.update(sql,
                tableName,
                mapType == null ? "" : mapType,
                encoding.getEncoding(),
                encoding.hasBOM(),
                xmlFile.getAbsolutePath(),
                fileHash,
                xmlFile.length());

            log.info("✅ 保存编码元数据: 表={}, mapType={}, 编码={}, BOM={}, MD5={}, 文件={}",
                    tableName, mapType, encoding.getEncoding(), encoding.hasBOM(), fileHash, xmlFile.getName());

            // 使缓存失效，确保下次查询获取最新数据
            EncodingMetadataCache.invalidate(tableName, mapType);

        } catch (Exception e) {
            // 如果表不存在，警告但不抛出异常（允许降级为硬编码UTF-16）
            log.warn("保存编码元数据失败（表可能不存在）: 表={}, mapType={}, 错误={}",
                    tableName, mapType, e.getMessage());
        }
    }

    /**
     * 查询编码元数据
     *
     * @param tableName 表名
     * @return 编码信息
     */
    public static FileEncodingDetector.EncodingInfo getMetadata(String tableName) {
        return getMetadata(tableName, "");
    }

    /**
     * 查询编码元数据（支持 mapType）
     *
     * @param tableName 表名
     * @param mapType World表的地图类型，普通表传空字符串
     * @return 编码信息
     */
    public static FileEncodingDetector.EncodingInfo getMetadata(String tableName, String mapType) {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();

            String sql = "SELECT original_encoding, has_bom FROM file_encoding_metadata WHERE table_name = ? AND map_type = ?";

            Map<String, Object> row = jdbcTemplate.queryForMap(sql, tableName, mapType == null ? "" : mapType);
            String encoding = (String) row.get("original_encoding");
            Boolean hasBOM = (Boolean) row.get("has_bom");

            FileEncodingDetector.EncodingInfo info =
                    new FileEncodingDetector.EncodingInfo(encoding, hasBOM != null && hasBOM);

            log.debug("查询到编码元数据: 表={}, mapType={}, 编码={}", tableName, mapType, info);
            return info;

        } catch (EmptyResultDataAccessException e) {
            // 未找到元数据，返回默认UTF-16（保持向后兼容）
            log.warn("未找到表 {} (mapType={}) 的编码元数据，使用默认UTF-16", tableName, mapType);
            return new FileEncodingDetector.EncodingInfo("UTF-16", false);

        } catch (Exception e) {
            // 其他错误（如表不存在），也返回默认UTF-16
            log.warn("查询编码元数据失败（表可能不存在）: 表={}, mapType={}, 使用默认UTF-16, 错误={}",
                    tableName, mapType, e.getMessage());
            return new FileEncodingDetector.EncodingInfo("UTF-16", false);
        }
    }

    /**
     * 更新导出时间
     *
     * @param tableName 表名
     */
    public static void updateExportTime(String tableName) {
        updateExportTime(tableName, "");
    }

    /**
     * 更新导出时间（支持 mapType）
     *
     * @param tableName 表名
     * @param mapType World表的地图类型，普通表传空字符串
     */
    public static void updateExportTime(String tableName, String mapType) {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = "UPDATE file_encoding_metadata SET last_export_time = NOW(), export_count = export_count + 1 WHERE table_name = ? AND map_type = ?";
            int updated = jdbcTemplate.update(sql, tableName, mapType == null ? "" : mapType);

            if (updated > 0) {
                log.debug("更新导出时间: 表={}, mapType={}", tableName, mapType);
            }
        } catch (Exception e) {
            log.trace("更新导出时间失败（忽略）: 表={}, mapType={}, 错误={}", tableName, mapType, e.getMessage());
        }
    }

    /**
     * 检查元数据是否存在
     *
     * @param tableName 表名
     * @return true表示存在
     */
    public static boolean hasMetadata(String tableName) {
        return hasMetadata(tableName, "");
    }

    /**
     * 检查元数据是否存在（支持 mapType）
     *
     * @param tableName 表名
     * @param mapType World表的地图类型，普通表传空字符串
     * @return true表示存在
     */
    public static boolean hasMetadata(String tableName, String mapType) {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = "SELECT COUNT(*) FROM file_encoding_metadata WHERE table_name = ? AND map_type = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tableName, mapType == null ? "" : mapType);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 删除元数据
     *
     * @param tableName 表名
     */
    public static void deleteMetadata(String tableName) {
        deleteMetadata(tableName, "");
    }

    /**
     * 删除元数据（支持 mapType）
     *
     * @param tableName 表名
     * @param mapType World表的地图类型，普通表传空字符串
     */
    public static void deleteMetadata(String tableName, String mapType) {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = "DELETE FROM file_encoding_metadata WHERE table_name = ? AND map_type = ?";
            int deleted = jdbcTemplate.update(sql, tableName, mapType == null ? "" : mapType);

            if (deleted > 0) {
                log.info("删除编码元数据: 表={}, mapType={}", tableName, mapType);
            }
        } catch (Exception e) {
            log.warn("删除编码元数据失败: 表={}, mapType={}, 错误={}", tableName, mapType, e.getMessage());
        }
    }

    /**
     * 获取所有编码统计信息（用于诊断）
     *
     * @return 统计信息
     */
    public static String getEncodingStatistics() {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = """
                SELECT
                    original_encoding,
                    COUNT(*) as count,
                    SUM(import_count) as total_imports,
                    SUM(export_count) as total_exports
                FROM file_encoding_metadata
                GROUP BY original_encoding
                ORDER BY count DESC
                """;

            StringBuilder result = new StringBuilder("编码统计信息:\n");
            jdbcTemplate.queryForList(sql).forEach(row -> {
                result.append(String.format("  %s: %d个文件, 导入%d次, 导出%d次\n",
                        row.get("original_encoding"),
                        row.get("count"),
                        row.get("total_imports"),
                        row.get("total_exports")));
            });

            return result.toString();

        } catch (Exception e) {
            return "无法获取统计信息: " + e.getMessage();
        }
    }
}
