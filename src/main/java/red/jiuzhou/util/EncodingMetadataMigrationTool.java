package red.jiuzhou.util;

import cn.hutool.core.io.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.dbxml.TableConf;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 编码元数据迁移工具
 *
 * 功能：
 * - 扫描所有已导入的表（数据库中有数据的表）
 * - 为缺少编码元数据的表自动补充元数据
 * - 查找对应的 XML 文件并检测编码
 * - 批量迁移历史数据
 *
 * @author Claude
 * @date 2025-12-29
 */
public class EncodingMetadataMigrationTool {

    private static final Logger log = LoggerFactory.getLogger(EncodingMetadataMigrationTool.class);

    /**
     * 迁移结果统计
     */
    public static class MigrationResult {
        private int totalTables = 0;
        private int alreadyHasMetadata = 0;
        private int xmlFileNotFound = 0;
        private int migrated = 0;
        private int failed = 0;
        private final List<String> successList = new ArrayList<>();
        private final List<String> failedList = new ArrayList<>();
        private final List<String> notFoundList = new ArrayList<>();

        public void incrementTotal() {
            totalTables++;
        }

        public void incrementAlreadyHas() {
            alreadyHasMetadata++;
        }

        public void incrementNotFound(String tableName) {
            xmlFileNotFound++;
            notFoundList.add(tableName);
        }

        public void incrementMigrated(String tableName) {
            migrated++;
            successList.add(tableName);
        }

        public void incrementFailed(String tableName) {
            failed++;
            failedList.add(tableName);
        }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== 编码元数据迁移报告 ===\n");
            sb.append(String.format("总表数: %d\n", totalTables));
            sb.append(String.format("已有元数据: %d\n", alreadyHasMetadata));
            sb.append(String.format("成功迁移: %d\n", migrated));
            sb.append(String.format("XML未找到: %d\n", xmlFileNotFound));
            sb.append(String.format("迁移失败: %d\n", failed));

            if (!successList.isEmpty()) {
                sb.append("\n✅ 成功迁移的表:\n");
                successList.forEach(t -> sb.append("  - ").append(t).append("\n"));
            }

            if (!notFoundList.isEmpty()) {
                sb.append("\n⚠️ XML文件未找到的表:\n");
                notFoundList.forEach(t -> sb.append("  - ").append(t).append("\n"));
            }

            if (!failedList.isEmpty()) {
                sb.append("\n❌ 迁移失败的表:\n");
                failedList.forEach(t -> sb.append("  - ").append(t).append("\n"));
            }

            return sb.toString();
        }
    }

    /**
     * 迁移所有已导入的表
     *
     * @param tabFilePath 表配置文件路径
     * @return 迁移结果
     */
    public static MigrationResult migrateAllTables(String tabFilePath) {
        log.info("开始批量迁移编码元数据...");
        MigrationResult result = new MigrationResult();

        try {
            // 1. 获取所有表配置
            List<String> allTableNames = getAllConfiguredTables(tabFilePath);
            log.info("找到 {} 个已配置的表", allTableNames.size());

            // 2. 逐个检查并迁移
            for (String tableName : allTableNames) {
                result.incrementTotal();
                migrateTable(tableName, tabFilePath, result);
            }

            log.info(result.getSummary());

        } catch (Exception e) {
            log.error("批量迁移失败", e);
        }

        return result;
    }

    /**
     * 迁移单个表
     *
     * @param tableName   表名
     * @param tabFilePath 表配置路径
     * @param result      结果统计
     */
    private static void migrateTable(String tableName, String tabFilePath, MigrationResult result) {
        try {
            // 1. 检查是否已有元数据
            if (EncodingMetadataManager.hasMetadata(tableName)) {
                log.debug("表 {} 已有元数据，跳过", tableName);
                result.incrementAlreadyHas();
                return;
            }

            // 2. 加载表配置
            TableConf tableConf = TabConfLoad.getTale(tableName, tabFilePath);
            if (tableConf == null) {
                log.warn("表 {} 的配置未找到，跳过", tableName);
                result.incrementNotFound(tableName);
                return;
            }

            // 3. 查找 XML 文件
            File xmlFile = findXmlFile(tableConf);
            if (xmlFile == null || !xmlFile.exists()) {
                log.warn("表 {} 的XML文件未找到: {}", tableName, tableConf.getFilePath());
                result.incrementNotFound(tableName);
                return;
            }

            // 4. 检测编码
            FileEncodingDetector.EncodingInfo encoding =
                    EncodingFallbackStrategy.detectWithFallback(xmlFile, tableName);
            int confidence = EncodingFallbackStrategy.calculateConfidence(encoding, xmlFile);

            // 5. 保存元数据
            EncodingMetadataManager.saveMetadata(tableName, "", xmlFile, encoding);

            log.info("✅ 成功迁移表 {}: 编码={}, 可信度={}%, 文件={}",
                    tableName, encoding, confidence, xmlFile.getName());
            result.incrementMigrated(tableName);

        } catch (Exception e) {
            log.error("迁移表 {} 失败: {}", tableName, e.getMessage());
            result.incrementFailed(tableName);
        }
    }

    /**
     * 迁移 World 表（支持多个 mapType）
     *
     * @param tabFilePath 表配置路径
     * @param mapTypes    地图类型列表（如 ["China", "Korea", "Japan"]）
     * @return 迁移结果
     */
    public static MigrationResult migrateWorldTable(String tabFilePath, List<String> mapTypes) {
        log.info("开始迁移 World 表（mapType数量: {}）", mapTypes.size());
        MigrationResult result = new MigrationResult();

        try {
            TableConf tableConf = TabConfLoad.getTale("world", tabFilePath);
            if (tableConf == null) {
                log.error("World 表配置未找到");
                return result;
            }

            for (String mapType : mapTypes) {
                result.incrementTotal();

                // 检查是否已有元数据
                if (EncodingMetadataManager.hasMetadata("world", mapType)) {
                    log.debug("World 表 (mapType={}) 已有元数据，跳过", mapType);
                    result.incrementAlreadyHas();
                    continue;
                }

                // 构造 XML 文件路径
                String xmlFilePath = tableConf.getFilePath();
                String parent = FileUtil.getParent(xmlFilePath, 1);
                xmlFilePath = parent + File.separator + mapType + File.separator + FileUtil.getName(xmlFilePath);

                File xmlFile = new File(xmlFilePath);
                if (!xmlFile.exists()) {
                    log.warn("World XML文件未找到: {}", xmlFilePath);
                    result.incrementNotFound("world:" + mapType);
                    continue;
                }

                // 检测编码并保存
                FileEncodingDetector.EncodingInfo encoding =
                        EncodingFallbackStrategy.detectWithFallback(xmlFile, "world");
                EncodingMetadataManager.saveMetadata("world", mapType, xmlFile, encoding);

                log.info("✅ 成功迁移 World 表 (mapType={}): 编码={}, 文件={}",
                        mapType, encoding, xmlFile.getName());
                result.incrementMigrated("world:" + mapType);
            }

        } catch (Exception e) {
            log.error("迁移 World 表失败", e);
        }

        return result;
    }

    /**
     * 查找 XML 文件
     *
     * @param tableConf 表配置
     * @return XML文件，未找到返回null
     */
    private static File findXmlFile(TableConf tableConf) {
        String xmlFilePath = tableConf.getFilePath();

        // 1. 直接路径查找
        File xmlFile = new File(xmlFilePath);
        if (xmlFile.exists()) {
            return xmlFile;
        }

        // 2. 尝试相对路径（相对于项目根目录）
        String projectRoot = System.getProperty("user.dir");
        xmlFile = new File(projectRoot, xmlFilePath);
        if (xmlFile.exists()) {
            return xmlFile;
        }

        // 3. 尝试在 resources 目录查找
        String resourcePath = "src/main/resources/" + xmlFilePath;
        xmlFile = new File(projectRoot, resourcePath);
        if (xmlFile.exists()) {
            return xmlFile;
        }

        return null;
    }

    /**
     * 获取所有已配置的表名
     *
     * @param tabFilePath 表配置路径
     * @return 表名列表
     */
    private static List<String> getAllConfiguredTables(String tabFilePath) {
        List<String> tables = new ArrayList<>();

        try {
            // 从数据库获取所有表名 (PostgreSQL)
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = "SELECT tablename FROM pg_tables WHERE schemaname = current_schema()";
            List<String> result = jdbcTemplate.queryForList(sql, String.class);

            for (String tableName : result) {
                // 排除系统表和元数据表
                if (!tableName.equals("file_encoding_metadata") &&
                    !tableName.startsWith("sys_") &&
                    !tableName.startsWith("pg_")) {
                    tables.add(tableName);
                }
            }

        } catch (Exception e) {
            log.error("获取表列表失败", e);
        }

        return tables;
    }

    /**
     * 清理无效的元数据（对应的表已不存在）
     *
     * @return 清理数量
     */
    public static int cleanupInvalidMetadata() {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();

            // 获取所有数据库表 (PostgreSQL)
            Set<String> existingTables = new HashSet<>();
            String showTablesSql = "SELECT tablename FROM pg_tables WHERE schemaname = current_schema()";
            jdbcTemplate.queryForList(showTablesSql, String.class).forEach(tableName -> {
                existingTables.add(tableName);
            });

            // 获取所有元数据记录
            String selectSql = "SELECT DISTINCT table_name FROM file_encoding_metadata";
            List<String> metadataTables = jdbcTemplate.queryForList(selectSql, String.class);

            // 删除无效记录
            int deleted = 0;
            for (String tableName : metadataTables) {
                if (!existingTables.contains(tableName)) {
                    EncodingMetadataManager.deleteMetadata(tableName);
                    deleted++;
                    log.info("清理无效元数据: {}", tableName);
                }
            }

            log.info("清理完成，共删除 {} 条无效元数据", deleted);
            return deleted;

        } catch (Exception e) {
            log.error("清理无效元数据失败", e);
            return 0;
        }
    }

    /**
     * 重新检测所有表的编码（用于修正错误的检测结果）
     *
     * @param tabFilePath 表配置路径
     * @param force       是否强制重新检测（即使已有元数据）
     * @return 重新检测数量
     */
    public static int redetectAllEncodings(String tabFilePath, boolean force) {
        log.info("开始重新检测所有表的编码（强制模式: {}）", force);
        AtomicInteger redetected = new AtomicInteger(0);

        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = force ?
                    "SELECT DISTINCT table_name, map_type FROM file_encoding_metadata" :
                    "SELECT DISTINCT table_name, map_type FROM file_encoding_metadata WHERE original_encoding = 'UTF-16'";

            jdbcTemplate.queryForList(sql).forEach(row -> {
                String tableName = (String) row.get("table_name");
                String mapType = (String) row.get("map_type");

                try {
                    TableConf tableConf = TabConfLoad.getTale(tableName, tabFilePath);
                    if (tableConf == null) return;

                    File xmlFile = findXmlFile(tableConf);
                    if (xmlFile == null || !xmlFile.exists()) return;

                    // 重新检测编码
                    FileEncodingDetector.EncodingInfo oldEncoding =
                            EncodingMetadataManager.getMetadata(tableName, mapType);
                    FileEncodingDetector.EncodingInfo newEncoding =
                            EncodingFallbackStrategy.detectWithFallback(xmlFile, tableName);

                    // 只有编码改变时才更新
                    if (!oldEncoding.getEncoding().equals(newEncoding.getEncoding()) ||
                        oldEncoding.hasBOM() != newEncoding.hasBOM()) {

                        EncodingMetadataManager.saveMetadata(tableName, mapType, xmlFile, newEncoding);
                        log.info("✅ 重新检测 {}: {} → {}",
                                tableName, oldEncoding, newEncoding);
                        redetected.incrementAndGet();
                    }

                } catch (Exception e) {
                    log.warn("重新检测表 {} 失败: {}", tableName, e.getMessage());
                }
            });

            log.info("重新检测完成，共更新 {} 个表", redetected.get());

        } catch (Exception e) {
            log.error("重新检测失败", e);
        }

        return redetected.get();
    }
}
