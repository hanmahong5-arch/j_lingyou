package red.jiuzhou.validation;

import cn.hutool.core.io.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.dbxml.TableConf;
import red.jiuzhou.util.DatabaseUtil;

import java.io.File;
import java.util.List;

/**
 * 批量导入预检查器
 *
 * 功能：
 * 1. 扫描所有待导入的XML文件
 * 2. 检测所有潜在问题
 * 3. 生成诊断报告
 * 4. 标记可自动修复的问题
 *
 * @author Claude
 * @date 2025-12-28
 */
public class BatchImportPreflightChecker {

    private static final Logger log = LoggerFactory.getLogger(BatchImportPreflightChecker.class);

    /**
     * 执行批量导入预检查
     *
     * @param xmlFiles 待导入的XML文件列表
     * @return 预检查报告
     */
    public static PreflightReport check(List<File> xmlFiles) {
        log.info("开始执行批量导入预检查，文件数量: {}", xmlFiles.size());

        PreflightReport report = new PreflightReport();

        for (File xmlFile : xmlFiles) {
            FileCheckResult fileResult = checkSingleFile(xmlFile);
            report.addResult(fileResult);
        }

        log.info("预检查完成: {}", report);
        return report;
    }

    /**
     * 检查单个文件
     */
    private static FileCheckResult checkSingleFile(File xmlFile) {
        String fileName = xmlFile.getName();
        String tableName = getTableName(fileName);

        FileCheckResult result = new FileCheckResult(tableName, xmlFile);

        try {
            // ===== 检查1: 配置文件是否存在 =====
            TableConf config = TabConfLoad.getTale(tableName, xmlFile.getAbsolutePath());
            if (config == null) {
                result.addError("配置缺失", "配置文件不存在或无效");
                result.setAction(FileCheckResult.Action.SKIP);
                return result;
            }

            // ===== 检查2: XML文件质量 =====
            QualityCheckResult qualityCheck = XmlQualityChecker.check(xmlFile);

            if (qualityCheck.isEmpty()) {
                result.addWarning("空文件", "XML文件无数据（将跳过）");
                result.setAction(FileCheckResult.Action.SKIP);
                return result;
            }

            if (qualityCheck.hasStructureError()) {
                result.addError("结构错误", "XML结构有误: " + qualityCheck.getStructureErrors());
                result.setAction(FileCheckResult.Action.SKIP);
                return result;
            }

            // 记录数据量
            if (qualityCheck.getItemCount() > 0) {
                log.debug("文件 {} 包含 {} 条数据", fileName, qualityCheck.getItemCount());
            }

            // ===== 检查3: 主键字段 =====
            String dbPrimaryKey = DatabaseUtil.getPrimaryKeyColumn(tableName);

            if (dbPrimaryKey == null) {
                result.addWarning("无主键", "数据库表无主键定义");
            } else {
                // 检查XML中是否有该主键字段
                boolean hasDbPrimaryKey = PrimaryKeyDetector.hasField(xmlFile, dbPrimaryKey);

                if (!hasDbPrimaryKey) {
                    // XML中没有数据库期望的主键字段，尝试自动检测
                    PrimaryKeyInfo detected = PrimaryKeyDetector.detectFromXml(xmlFile);

                    if (detected != null) {
                        result.addWarning("主键不匹配",
                                String.format("XML主键 (%s) 与数据库主键 (%s) 不一致",
                                        detected.getFieldName(), dbPrimaryKey));
                        result.addFix("主键映射",
                                String.format("可自动映射: %s → %s",
                                        detected.getFieldName(), dbPrimaryKey));
                        result.setAction(FileCheckResult.Action.AUTO_FIX);
                    } else {
                        result.addError("主键缺失",
                                String.format("XML中缺少主键字段: %s，且无法自动检测", dbPrimaryKey));
                        result.setAction(FileCheckResult.Action.MANUAL_FIX);
                    }
                }
            }

            // ===== 检查4: 数据库表是否存在 =====
            if (!DatabaseUtil.tableExists(tableName)) {
                result.addError("表不存在", "数据库中不存在该表");
                result.setAction(FileCheckResult.Action.MANUAL_FIX);
            }

            // ===== 检查5: 样本数据问题 =====
            if (!qualityCheck.getSampleErrors().isEmpty()) {
                result.addWarning("数据质量",
                        String.format("样本数据有 %d 个问题", qualityCheck.getSampleErrors().size()));
            }

        } catch (Exception e) {
            result.addError("检查异常", "预检查过程出错: " + e.getMessage());
            log.error("预检查文件 {} 时出错", fileName, e);
        }

        return result;
    }

    /**
     * 从文件名提取表名
     */
    private static String getTableName(String fileName) {
        if (fileName.toLowerCase().endsWith(".xml")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    /**
     * 快速检查（仅检查关键问题）
     */
    public static PreflightReport quickCheck(List<File> xmlFiles) {
        log.info("执行快速预检查，文件数量: {}", xmlFiles.size());

        PreflightReport report = new PreflightReport();

        for (File xmlFile : xmlFiles) {
            String fileName = xmlFile.getName();
            String tableName = getTableName(fileName);

            FileCheckResult result = new FileCheckResult(tableName, xmlFile);

            // 只检查是否为空
            if (XmlQualityChecker.isEmptyQuick(xmlFile)) {
                result.addWarning("空文件", "XML文件无数据");
                result.setAction(FileCheckResult.Action.SKIP);
            }

            report.addResult(result);
        }

        return report;
    }
}
