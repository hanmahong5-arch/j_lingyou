package red.jiuzhou.diagnosis;

import cn.hutool.core.io.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.batch.diagnosis.DiagnosticFailure;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.xmltosql.XmlProcess;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 自动修复服务
 *
 * 提供确定性强的自动修复功能：
 * - 自动生成配置文件（.conf）
 * - 自动建表（执行 DDL）
 * - 清理表中旧数据
 * - 修复文件编码问题
 *
 * 设计原则：
 * - 只提供确定性操作，避免猜测性修复
 * - 所有操作都有明确的成功/失败结果
 * - 修复前提示风险，修复后提示下一步
 *
 * @author Claude AI
 * @date 2026-01-15
 */
public class AutoFixService {

    private static final Logger log = LoggerFactory.getLogger(AutoFixService.class);

    /**
     * 修复结果
     */
    public static class FixResult {
        private final boolean success;
        private final String message;
        private final String nextStep;

        public FixResult(boolean success, String message, String nextStep) {
            this.success = success;
            this.message = message;
            this.nextStep = nextStep;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getNextStep() {
            return nextStep;
        }

        public static FixResult success(String message, String nextStep) {
            return new FixResult(true, message, nextStep);
        }

        public static FixResult failure(String message) {
            return new FixResult(false, message, null);
        }
    }

    /**
     * 自动生成配置文件（.conf）
     *
     * 适用场景：错误码 CFG-0001（配置文件缺失）
     *
     * @param failure 失败诊断记录
     * @return 修复结果
     */
    public static FixResult autoGenerateConfig(DiagnosticFailure failure) {
        try {
            File xmlFile = failure.file();
            String tableName = failure.tableName();

            log.info("尝试自动生成配置文件: {}", tableName);

            // 检查 XML 文件是否存在
            if (!xmlFile.exists()) {
                return FixResult.failure("XML 文件不存在: " + xmlFile.getAbsolutePath());
            }

            // 获取配置文件路径（与 XML 同目录）
            String xmlDir = xmlFile.getParent();
            String confFileName = tableName + ".conf";
            File confFile = new File(xmlDir, confFileName);

            // 检查是否已存在
            if (confFile.exists()) {
                return FixResult.failure("配置文件已存在: " + confFile.getAbsolutePath());
            }

            // 生成配置文件内容（基于 XML 结构自动推断）
            String confContent = generateConfContent(xmlFile, tableName);

            // 写入配置文件
            FileUtil.writeString(confContent, confFile, StandardCharsets.UTF_8);

            log.info("✅ 配置文件生成成功: {}", confFile.getAbsolutePath());

            return FixResult.success(
                String.format("✅ 已自动生成配置文件：\n%s\n\n包含 %d 个字段映射",
                    confFile.getAbsolutePath(),
                    confContent.split("\n").length - 1),
                "请点击「重试选中项」重新导入该文件"
            );

        } catch (Exception e) {
            log.error("自动生成配置文件失败", e);
            return FixResult.failure("生成失败: " + e.getMessage());
        }
    }

    /**
     * 自动建表（执行 DDL）
     *
     * 适用场景：错误码 DB-0003（表不存在）
     *
     * @param failure 失败诊断记录
     * @return 修复结果
     */
    public static FixResult autoCreateTable(DiagnosticFailure failure) {
        try {
            File xmlFile = failure.file();
            String tableName = failure.tableName();

            log.info("尝试自动建表: {}", tableName);

            // 检查表是否已存在
            if (DatabaseUtil.tableExists(tableName)) {
                return FixResult.failure("表已存在，无需建表: " + tableName);
            }

            // 生成 DDL SQL 脚本
            String sqlDdlFilePath = XmlProcess.parseXmlFile(xmlFile.getAbsolutePath());
            log.info("DDL 脚本已生成: {}", sqlDdlFilePath);

            // 执行 DDL 脚本建表
            DatabaseUtil.executeSqlScript(sqlDdlFilePath);

            log.info("✅ 自动建表成功: {}", tableName);

            return FixResult.success(
                String.format("✅ 已成功创建数据表：%s\n\nDDL 脚本路径：\n%s",
                    tableName, sqlDdlFilePath),
                "请点击「重试选中项」重新导入数据"
            );

        } catch (Exception e) {
            log.error("自动建表失败", e);
            return FixResult.failure("建表失败: " + e.getMessage());
        }
    }

    /**
     * 清空表数据
     *
     * 适用场景：主键冲突时，选择清空后重新导入
     *
     * @param tableName 表名
     * @return 修复结果
     */
    public static FixResult clearTableData(String tableName) {
        try {
            log.info("尝试清空表数据: {}", tableName);

            // 检查表是否存在
            if (!DatabaseUtil.tableExists(tableName)) {
                return FixResult.failure("表不存在，无法清空: " + tableName);
            }

            // 获取清空前的记录数
            int beforeCount = DatabaseUtil.getTableRowCount(tableName);

            // 清空表数据（使用 TRUNCATE 或 DELETE）
            DatabaseUtil.clearTable(tableName);

            log.info("✅ 表数据清空成功: {}, 删除了 {} 条记录", tableName, beforeCount);

            return FixResult.success(
                String.format("✅ 已清空表 %s 的数据\n\n删除了 %d 条旧记录",
                    tableName, beforeCount),
                "请点击「重试选中项」重新导入该文件"
            );

        } catch (Exception e) {
            log.error("清空表数据失败", e);
            return FixResult.failure("清空失败: " + e.getMessage());
        }
    }

    /**
     * 修复文件编码问题
     *
     * 适用场景：IO-0002（编码错误）
     *
     * @param xmlFile XML 文件
     * @return 修复结果
     */
    public static FixResult fixFileEncoding(File xmlFile) {
        try {
            log.info("尝试修复文件编码: {}", xmlFile.getName());

            // 读取文件内容（自动检测编码）
            Path path = Paths.get(xmlFile.getAbsolutePath());
            byte[] bytes = Files.readAllBytes(path);

            // 尝试多种编码读取
            String content = null;
            String detectedEncoding = null;

            // 尝试 UTF-8
            try {
                content = new String(bytes, StandardCharsets.UTF_8);
                if (!content.contains("�")) {  // 检查是否有乱码
                    detectedEncoding = "UTF-8";
                }
            } catch (Exception ignored) {
            }

            // 尝试 GBK
            if (detectedEncoding == null) {
                try {
                    content = new String(bytes, "GBK");
                    detectedEncoding = "GBK";
                } catch (Exception ignored) {
                }
            }

            if (content == null || detectedEncoding == null) {
                return FixResult.failure("无法检测文件编码");
            }

            // 备份原文件
            String backupPath = xmlFile.getAbsolutePath() + ".bak";
            FileUtil.copy(xmlFile, new File(backupPath), true);

            // 写回为 UTF-8
            FileUtil.writeString(content, xmlFile, StandardCharsets.UTF_8);

            log.info("✅ 文件编码修复成功: {} -> UTF-8", detectedEncoding);

            return FixResult.success(
                String.format("✅ 已修复文件编码：%s → UTF-8\n\n原文件已备份到：\n%s",
                    detectedEncoding, backupPath),
                "请点击「重试选中项」重新导入该文件"
            );

        } catch (Exception e) {
            log.error("修复文件编码失败", e);
            return FixResult.failure("修复失败: " + e.getMessage());
        }
    }

    /**
     * 删除重复记录（保留最新的）
     *
     * 适用场景：主键冲突，需要清理重复数据
     *
     * @param tableName 表名
     * @param primaryKey 主键列名
     * @return 修复结果
     */
    public static FixResult removeDuplicateRecords(String tableName, String primaryKey) {
        try {
            log.info("尝试删除重复记录: 表={}, 主键={}", tableName, primaryKey);

            // 检查表是否存在
            if (!DatabaseUtil.tableExists(tableName)) {
                return FixResult.failure("表不存在: " + tableName);
            }

            // 执行去重 SQL（保留最新的记录）
            String sql = String.format(
                "DELETE FROM \"%s\" WHERE ctid NOT IN (" +
                "  SELECT MAX(ctid) FROM \"%s\" GROUP BY \"%s\"" +
                ")",
                tableName, tableName, primaryKey
            );

            int deletedCount = DatabaseUtil.getJdbcTemplate().update(sql);

            log.info("✅ 删除重复记录成功: {}, 删除了 {} 条", tableName, deletedCount);

            return FixResult.success(
                String.format("✅ 已删除重复记录\n\n删除了 %d 条旧数据，每个主键保留最新的一条",
                    deletedCount),
                "重复数据已清理，可以继续导入新数据"
            );

        } catch (Exception e) {
            log.error("删除重复记录失败", e);
            return FixResult.failure("删除失败: " + e.getMessage());
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成配置文件内容（基于 XML 结构自动推断）
     */
    private static String generateConfContent(File xmlFile, String tableName) throws Exception {
        StringBuilder sb = new StringBuilder();

        // 解析 XML 获取第一个元素的所有属性
        org.dom4j.Document doc = org.dom4j.io.SAXReader.createDefault().read(xmlFile);
        org.dom4j.Element root = doc.getRootElement();

        if (root.elements().isEmpty()) {
            throw new IllegalStateException("XML 文件为空，无法生成配置");
        }

        // 获取第一个子元素
        org.dom4j.Element firstElement = (org.dom4j.Element) root.elements().get(0);

        // 遍历所有属性生成映射
        for (Object attrObj : firstElement.attributes()) {
            org.dom4j.Attribute attr = (org.dom4j.Attribute) attrObj;
            String attrName = attr.getName();

            // 推断数据类型
            String value = attr.getValue();
            String dbType = inferDbType(value);

            // 生成配置行：xml_attr=db_column,db_type
            sb.append(String.format("%s=%s,%s\n", attrName, attrName, dbType));
        }

        return sb.toString();
    }

    /**
     * 推断数据库类型
     */
    private static String inferDbType(String value) {
        if (value == null || value.isEmpty()) {
            return "VARCHAR(255)";
        }

        // 尝试解析为整数
        try {
            Long.parseLong(value);
            return "BIGINT";
        } catch (NumberFormatException ignored) {
        }

        // 尝试解析为浮点数
        try {
            Double.parseDouble(value);
            return "DOUBLE PRECISION";
        } catch (NumberFormatException ignored) {
        }

        // 尝试解析为布尔值
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "BOOLEAN";
        }

        // 根据长度选择类型
        if (value.length() > 500) {
            return "TEXT";
        } else if (value.length() > 255) {
            return "VARCHAR(500)";
        } else {
            return "VARCHAR(255)";
        }
    }
}
