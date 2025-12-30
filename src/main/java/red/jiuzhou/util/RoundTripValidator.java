package red.jiuzhou.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;

/**
 * 往返一致性验证器
 *
 * 功能：
 * - 保存导入文件的 MD5 哈希
 * - 验证导出文件与原始文件的一致性
 * - 记录验证结果到数据库
 *
 * @author Claude
 * @date 2025-12-29
 */
public class RoundTripValidator {

    private static final Logger log = LoggerFactory.getLogger(RoundTripValidator.class);

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean passed;
        private final String originalHash;
        private final String exportedHash;
        private final String message;

        public ValidationResult(boolean passed, String originalHash, String exportedHash) {
            this.passed = passed;
            this.originalHash = originalHash;
            this.exportedHash = exportedHash;
            this.message = passed ?
                    "✅ 往返一致性验证通过！导出文件与原始文件完全一致" :
                    String.format("❌ 往返一致性验证失败！原始哈希: %s, 导出哈希: %s",
                            originalHash, exportedHash);
        }

        public boolean isPassed() {
            return passed;
        }

        public String getOriginalHash() {
            return originalHash;
        }

        public String getExportedHash() {
            return exportedHash;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            return message;
        }
    }

    /**
     * 保存原始文件哈希（导入时调用）
     *
     * @param tableName 表名
     * @param mapType   World表的地图类型
     * @param xmlFile   原始XML文件
     * @return MD5哈希值
     */
    public static String saveFileHash(String tableName, String mapType, File xmlFile) {
        try {
            // 计算文件 MD5
            String md5 = DigestUtil.md5Hex(FileUtil.readBytes(xmlFile));
            log.debug("计算文件哈希: {} -> {}", xmlFile.getName(), md5);

            // 保存到数据库
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = """
                UPDATE file_encoding_metadata
                SET original_file_hash = ?
                WHERE table_name = ? AND map_type = ?
                """;

            int updated = jdbcTemplate.update(sql, md5, tableName, mapType == null ? "" : mapType);

            if (updated > 0) {
                log.info("✅ 保存文件哈希: 表={}, mapType={}, MD5={}", tableName, mapType, md5);
            } else {
                log.warn("⚠️ 未找到元数据记录，无法保存哈希: 表={}, mapType={}", tableName, mapType);
            }

            return md5;

        } catch (Exception e) {
            log.error("保存文件哈希失败: 表={}, 错误={}", tableName, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 验证往返一致性（导出后调用）
     *
     * @param tableName    表名
     * @param mapType      World表的地图类型
     * @param exportedFile 导出的XML文件
     * @return 验证结果
     */
    public static ValidationResult validateRoundTrip(String tableName, String mapType, File exportedFile) {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();

            // 1. 查询原始文件哈希
            String sql = "SELECT original_file_hash FROM file_encoding_metadata WHERE table_name = ? AND map_type = ?";
            String originalHash = jdbcTemplate.queryForObject(sql, String.class, tableName, mapType == null ? "" : mapType);

            if (originalHash == null || originalHash.isEmpty()) {
                log.warn("⚠️ 未找到原始文件哈希，无法验证: 表={}, mapType={}", tableName, mapType);
                return new ValidationResult(false, "未记录", "N/A");
            }

            // 2. 计算导出文件哈希
            String exportedHash = DigestUtil.md5Hex(FileUtil.readBytes(exportedFile));
            log.debug("导出文件哈希: {} -> {}", exportedFile.getName(), exportedHash);

            // 3. 对比哈希
            boolean passed = originalHash.equalsIgnoreCase(exportedHash);

            // 4. 记录验证结果
            saveValidationResult(tableName, mapType, passed);

            ValidationResult result = new ValidationResult(passed, originalHash, exportedHash);
            log.info(result.getMessage());

            return result;

        } catch (Exception e) {
            log.error("往返一致性验证失败: 表={}, mapType={}, 错误={}", tableName, mapType, e.getMessage(), e);
            return new ValidationResult(false, "错误", e.getMessage());
        }
    }

    /**
     * 保存验证结果到数据库
     *
     * @param tableName 表名
     * @param mapType   World表的地图类型
     * @param passed    是否通过验证
     */
    private static void saveValidationResult(String tableName, String mapType, boolean passed) {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = """
                UPDATE file_encoding_metadata
                SET last_validation_time = NOW(),
                    last_validation_result = ?,
                    validation_count = validation_count + 1
                WHERE table_name = ? AND map_type = ?
                """;

            jdbcTemplate.update(sql, passed, tableName, mapType == null ? "" : mapType);
            log.debug("验证结果已保存: 表={}, mapType={}, 结果={}", tableName, mapType, passed ? "通过" : "失败");

        } catch (Exception e) {
            log.trace("保存验证结果失败（忽略）: {}", e.getMessage());
        }
    }

    /**
     * 获取验证历史
     *
     * @param tableName 表名
     * @param mapType   World表的地图类型
     * @return 验证统计信息
     */
    public static String getValidationHistory(String tableName, String mapType) {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = """
                SELECT
                    validation_count,
                    last_validation_time,
                    last_validation_result
                FROM file_encoding_metadata
                WHERE table_name = ? AND map_type = ?
                """;

            var result = jdbcTemplate.queryForMap(sql, tableName, mapType == null ? "" : mapType);

            int count = ((Number) result.get("validation_count")).intValue();
            Object lastTime = result.get("last_validation_time");
            Boolean lastResult = (Boolean) result.get("last_validation_result");

            return String.format(
                    "验证次数: %d, 最后验证: %s, 结果: %s",
                    count,
                    lastTime != null ? lastTime.toString() : "未验证",
                    lastResult == null ? "N/A" : (lastResult ? "✅ 通过" : "❌ 失败")
            );

        } catch (Exception e) {
            return "无验证记录";
        }
    }

    /**
     * 批量验证所有表的往返一致性
     *
     * @return 验证报告
     */
    public static String validateAllTables() {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = """
                SELECT table_name, map_type, original_file_hash, last_validation_result
                FROM file_encoding_metadata
                WHERE original_file_hash IS NOT NULL
                """;

            var results = jdbcTemplate.queryForList(sql);
            StringBuilder report = new StringBuilder("=== 往返一致性验证报告 ===\n");

            int total = results.size();
            int passed = 0;
            int failed = 0;
            int notValidated = 0;

            for (var row : results) {
                String tableName = (String) row.get("table_name");
                String mapType = (String) row.get("map_type");
                Boolean validationResult = (Boolean) row.get("last_validation_result");

                if (validationResult == null) {
                    notValidated++;
                    report.append(String.format("⚪ %s (mapType=%s): 未验证\n", tableName, mapType));
                } else if (validationResult) {
                    passed++;
                    report.append(String.format("✅ %s (mapType=%s): 通过\n", tableName, mapType));
                } else {
                    failed++;
                    report.append(String.format("❌ %s (mapType=%s): 失败\n", tableName, mapType));
                }
            }

            report.append(String.format("\n总计: %d, 通过: %d, 失败: %d, 未验证: %d\n",
                    total, passed, failed, notValidated));

            return report.toString();

        } catch (Exception e) {
            return "批量验证失败: " + e.getMessage();
        }
    }
}
