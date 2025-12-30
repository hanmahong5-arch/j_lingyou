package red.jiuzhou.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.util.Map;

/**
 * 编码检测降级策略
 *
 * 多层级降级逻辑：
 * 1. 尝试自动检测（BOM/XML声明/file命令）
 * 2. 查询历史同名文件记录
 * 3. 查询同表其他记录的编码
 * 4. 使用文件扩展名推断
 * 5. 最终降级到 UTF-16（向后兼容）
 *
 * @author Claude
 * @date 2025-12-29
 */
public class EncodingFallbackStrategy {

    private static final Logger log = LoggerFactory.getLogger(EncodingFallbackStrategy.class);

    /**
     * 带降级策略的编码检测
     *
     * @param file      XML文件
     * @param tableName 表名（用于查询表级默认配置）
     * @return 编码信息
     */
    public static FileEncodingDetector.EncodingInfo detectWithFallback(File file, String tableName) {
        // 1. 尝试自动检测（最可靠）
        FileEncodingDetector.EncodingInfo detected = FileEncodingDetector.detect(file);
        if (detected != null && !detected.getEncoding().equals("UTF-16")) {
            // 检测成功且非默认值，说明检测可靠
            log.debug("✅ 编码检测成功: {}", detected);
            return detected;
        }

        log.warn("⚠️ 自动检测未能确定编码，启用降级策略: 文件={}", file.getName());

        // 2. 查询历史同名文件记录
        FileEncodingDetector.EncodingInfo historical = queryHistoricalEncoding(file.getName());
        if (historical != null) {
            log.info("📜 使用历史编码记录: {}", historical);
            return historical;
        }

        // 3. 查询同表其他记录的编码（表级默认）
        FileEncodingDetector.EncodingInfo tableDefault = getTableDefaultEncoding(tableName);
        if (tableDefault != null) {
            log.info("📊 使用表级默认编码: {}", tableDefault);
            return tableDefault;
        }

        // 4. 使用文件扩展名推断
        FileEncodingDetector.EncodingInfo extensionBased = inferFromExtension(file);
        if (extensionBased != null) {
            log.info("📁 根据扩展名推断编码: {}", extensionBased);
            return extensionBased;
        }

        // 5. 最终降级到 UTF-16（向后兼容）
        log.warn("⚠️ 所有降级策略均失败，使用默认 UTF-16: 文件={}", file.getName());
        return new FileEncodingDetector.EncodingInfo("UTF-16", false);
    }

    /**
     * 查询历史同名文件的编码
     *
     * @param fileName 文件名
     * @return 编码信息，未找到返回 null
     */
    private static FileEncodingDetector.EncodingInfo queryHistoricalEncoding(String fileName) {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = """
                SELECT original_encoding, has_bom
                FROM file_encoding_metadata
                WHERE original_file_path LIKE ?
                ORDER BY last_import_time DESC
                LIMIT 1
                """;

            Map<String, Object> row = jdbcTemplate.queryForMap(sql, "%" + fileName);
            String encoding = (String) row.get("original_encoding");
            Boolean hasBOM = (Boolean) row.get("has_bom");

            return new FileEncodingDetector.EncodingInfo(encoding, hasBOM != null && hasBOM);

        } catch (Exception e) {
            log.trace("未找到历史编码记录: {}", fileName);
            return null;
        }
    }

    /**
     * 获取表级默认编码（查询同表其他记录的常用编码）
     *
     * @param tableName 表名
     * @return 编码信息，未找到返回 null
     */
    private static FileEncodingDetector.EncodingInfo getTableDefaultEncoding(String tableName) {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = """
                SELECT original_encoding, has_bom, COUNT(*) as cnt
                FROM file_encoding_metadata
                WHERE table_name = ?
                GROUP BY original_encoding, has_bom
                ORDER BY cnt DESC
                LIMIT 1
                """;

            Map<String, Object> row = jdbcTemplate.queryForMap(sql, tableName);
            String encoding = (String) row.get("original_encoding");
            Boolean hasBOM = (Boolean) row.get("has_bom");
            int count = ((Number) row.get("cnt")).intValue();

            log.debug("表 {} 的历史编码统计: {} (出现{}次)", tableName, encoding, count);
            return new FileEncodingDetector.EncodingInfo(encoding, hasBOM != null && hasBOM);

        } catch (Exception e) {
            log.trace("未找到表级默认编码: {}", tableName);
            return null;
        }
    }

    /**
     * 根据文件扩展名推断编码
     *
     * @param file 文件
     * @return 编码信息，无法推断返回 null
     */
    private static FileEncodingDetector.EncodingInfo inferFromExtension(File file) {
        String fileName = file.getName().toLowerCase();

        // XML 文件通常使用 UTF-16（Aion游戏服务器约定）
        if (fileName.endsWith(".xml")) {
            return new FileEncodingDetector.EncodingInfo("UTF-16", false);
        }

        // JSON 文件通常使用 UTF-8
        if (fileName.endsWith(".json")) {
            return new FileEncodingDetector.EncodingInfo("UTF-8", false);
        }

        // TXT 文件可能使用 GBK（中文环境）
        if (fileName.endsWith(".txt")) {
            return new FileEncodingDetector.EncodingInfo("GBK", false);
        }

        return null;
    }

    /**
     * 验证编码检测结果的可信度
     *
     * @param encoding 编码信息
     * @param file     文件
     * @return 可信度评分（0-100），越高越可信
     */
    public static int calculateConfidence(FileEncodingDetector.EncodingInfo encoding, File file) {
        int confidence = 0;

        // BOM 标记最可靠（+60分）
        if (encoding.hasBOM()) {
            confidence += 60;
        }

        // UTF-16BE/UTF-16LE 明确指定字节序（+30分）
        if (encoding.getEncoding().equals("UTF-16BE") || encoding.getEncoding().equals("UTF-16LE")) {
            confidence += 30;
        }

        // UTF-8 BOM（+20分）
        if (encoding.getEncoding().equals("UTF-8") && encoding.hasBOM()) {
            confidence += 20;
        }

        // 文件大小验证（大文件更可能被正确检测）
        if (file.length() > 1024 * 1024) { // 大于1MB
            confidence += 10;
        }

        return Math.min(confidence, 100);
    }

    /**
     * 获取降级策略统计信息
     *
     * @return 统计信息字符串
     */
    public static String getStrategyStatistics() {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = """
                SELECT
                    original_encoding,
                    has_bom,
                    COUNT(*) as total,
                    SUM(CASE WHEN import_count > 1 THEN 1 ELSE 0 END) as repeated_imports
                FROM file_encoding_metadata
                GROUP BY original_encoding, has_bom
                ORDER BY total DESC
                """;

            var results = jdbcTemplate.queryForList(sql);
            StringBuilder stats = new StringBuilder("=== 编码策略统计 ===\n");

            for (var row : results) {
                String encoding = (String) row.get("original_encoding");
                Boolean hasBOM = (Boolean) row.get("has_bom");
                int total = ((Number) row.get("total")).intValue();
                int repeated = ((Number) row.get("repeated_imports")).intValue();

                stats.append(String.format("%s%s: %d个文件, %d次重复导入\n",
                        encoding,
                        hasBOM ? " (BOM)" : "",
                        total,
                        repeated));
            }

            return stats.toString();

        } catch (Exception e) {
            return "无法获取统计信息: " + e.getMessage();
        }
    }
}
