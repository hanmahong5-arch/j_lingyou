package red.jiuzhou.batch;

import cn.hutool.core.io.FileUtil;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.dbxml.TableConf;
import red.jiuzhou.dbxml.WorldXmlToDbGenerator;
import red.jiuzhou.dbxml.XmlToDbGenerator;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.xmltosql.XmlProcess;
import red.jiuzhou.validation.BatchImportPreflightChecker;
import red.jiuzhou.validation.PreflightReport;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 批量XML导入服务
 *
 * 支持：
 * - 单个文件导入数据库
 * - 多个选中文件批量导入
 * - 整个目录递归导入
 * - World类型文件特殊处理
 * - 进度回调和结果统计
 *
 * @author yanxq
 * @date 2025-12-19
 */
public class BatchXmlImporter {

    private static final Logger log = LoggerFactory.getLogger(BatchXmlImporter.class);

    /** World类型表名列表 */
    private static final List<String> WORLD_TABLES = Arrays.asList("world");

    /**
     * 批量导入结果（增强版 - 支持智能诊断）
     */
    public static class BatchImportResult {
        private int total;          // 总文件数
        private int success;        // 成功数
        private int failed;         // 失败数
        private int skipped;        // 跳过数
        private List<String> successFiles = new ArrayList<>();
        private List<red.jiuzhou.batch.diagnosis.DiagnosticFailure> failedFiles = new ArrayList<>();

        // 新增：错误统计（用于可视化）
        private Map<red.jiuzhou.ui.error.structured.ErrorCategory, Integer> errorsByCategory = new HashMap<>();
        private Map<red.jiuzhou.ui.error.structured.ErrorLevel, Integer> errorsByLevel = new HashMap<>();

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public int getSuccess() { return success; }
        public void setSuccess(int success) { this.success = success; }

        public int getFailed() { return failed; }
        public void setFailed(int failed) { this.failed = failed; }

        public int getSkipped() { return skipped; }
        public void setSkipped(int skipped) { this.skipped = skipped; }

        public List<String> getSuccessFiles() { return successFiles; }
        public List<red.jiuzhou.batch.diagnosis.DiagnosticFailure> getFailedFiles() { return failedFiles; }

        public Map<red.jiuzhou.ui.error.structured.ErrorCategory, Integer> getErrorsByCategory() {
            return errorsByCategory;
        }

        public Map<red.jiuzhou.ui.error.structured.ErrorLevel, Integer> getErrorsByLevel() {
            return errorsByLevel;
        }

        public String getSummary() {
            return String.format("总计: %d, 成功: %d, 失败: %d, 跳过: %d",
                total, success, failed, skipped);
        }

        /**
         * 获取可重试的失败项
         */
        public List<red.jiuzhou.batch.diagnosis.DiagnosticFailure> getRetryableFailures() {
            return failedFiles.stream()
                .filter(red.jiuzhou.batch.diagnosis.DiagnosticFailure::retryable)
                .filter(f -> !f.hasExceededRetryLimit())
                .collect(java.util.stream.Collectors.toList());
        }

        /**
         * 获取高优先级失败项（ERROR 或 FATAL）
         */
        public List<red.jiuzhou.batch.diagnosis.DiagnosticFailure> getHighPriorityFailures() {
            return failedFiles.stream()
                .filter(red.jiuzhou.batch.diagnosis.DiagnosticFailure::isHighPriority)
                .collect(java.util.stream.Collectors.toList());
        }

        /**
         * 获取诊断摘要（包含错误分类统计）
         */
        public String getDiagnosticSummary() {
            StringBuilder sb = new StringBuilder(getSummary());

            if (!errorsByCategory.isEmpty()) {
                sb.append("\n\n错误分类:");
                errorsByCategory.forEach((category, count) ->
                    sb.append(String.format("\n  - %s: %d", category.getDisplayName(), count))
                );
            }

            int retryableCount = getRetryableFailures().size();
            if (retryableCount > 0) {
                sb.append(String.format("\n\n可重试项: %d", retryableCount));
            }

            return sb.toString();
        }
    }

    /**
     * 失败文件记录（已废弃）
     *
     * @deprecated 已被 DiagnosticFailure 取代，提供更强大的诊断能力
     * @see red.jiuzhou.batch.diagnosis.DiagnosticFailure
     */
    @Deprecated(since = "2026-01-15", forRemoval = true)
    public static class FailedFile {
        private String path;
        private String error;

        public FailedFile(String path, String error) {
            this.path = path;
            this.error = error;
        }

        public String getPath() { return path; }
        public String getError() { return error; }

        @Override
        public String toString() {
            return path + ": " + error;
        }
    }

    /**
     * 导入选项
     */
    public static class ImportOptions {
        private String aiModule;            // AI模块（可选）
        private List<String> selectedColumns; // 需要AI处理的字段
        private String mapType;             // 地图类型（World类型文件用）
        private boolean clearTableFirst = true; // 导入前是否清空表

        // 新增：冲突解决策略（默认为覆盖更新）
        private ConflictResolutionStrategy conflictStrategy = ConflictResolutionStrategy.REPLACE_UPDATE;
        private boolean applyToAllFiles = false;  // 批量导入时，策略是否应用到所有文件

        public String getAiModule() { return aiModule; }
        public void setAiModule(String aiModule) { this.aiModule = aiModule; }

        public List<String> getSelectedColumns() { return selectedColumns; }
        public void setSelectedColumns(List<String> selectedColumns) { this.selectedColumns = selectedColumns; }

        public String getMapType() { return mapType; }
        public void setMapType(String mapType) { this.mapType = mapType; }

        public boolean isClearTableFirst() { return clearTableFirst; }
        public void setClearTableFirst(boolean clearTableFirst) { this.clearTableFirst = clearTableFirst; }

        // 新增 getter/setter
        public ConflictResolutionStrategy getConflictStrategy() {
            return conflictStrategy;
        }

        public void setConflictStrategy(ConflictResolutionStrategy strategy) {
            this.conflictStrategy = strategy;
        }

        public boolean isApplyToAllFiles() {
            return applyToAllFiles;
        }

        public void setApplyToAllFiles(boolean apply) {
            this.applyToAllFiles = apply;
        }
    }

    /**
     * 进度回调接口
     */
    public interface ProgressCallback {
        void onProgress(int current, int total, String currentFile);
        void onComplete(BatchImportResult result);
    }

    /**
     * 导入单个文件到数据库
     *
     * @param xmlFilePath XML文件路径
     * @param options 导入选项
     * @return 导入是否成功
     */
    public static CompletableFuture<Boolean> importSingleXml(
            String xmlFilePath,
            ImportOptions options) {

        return CompletableFuture.supplyAsync(() -> {
            File xmlFile = new File(xmlFilePath);
            String currentPhase = "初始化";  // 跟踪当前执行阶段
            Map<String, Object> context = new HashMap<>();  // 上下文信息

            try {
                log.info("开始导入XML: {}", xmlFilePath);

                // 从文件名提取表名
                String fileName = FileUtil.getName(xmlFilePath);
                String tableName = fileName.substring(0, fileName.lastIndexOf('.'));
                context.put("table", tableName);
                context.put("file_size", xmlFile.length());

                // ==================== 导入前自动建表（2025-12-29新增）====================
                currentPhase = "DDL生成与建表";
                // 检查表是否存在，如果不存在则先执行DDL建表
                try {
                    boolean tableExists = DatabaseUtil.tableExists(tableName);
                    if (!tableExists) {
                        log.info("表 {} 不存在，开始自动生成DDL并建表...", tableName);

                        // 生成DDL SQL脚本
                        String sqlDdlFilePath = XmlProcess.parseXmlFile(xmlFilePath);
                        log.info("DDL脚本已生成: {}", sqlDdlFilePath);
                        context.put("ddl_script", sqlDdlFilePath);

                        // 执行DDL脚本建表
                        DatabaseUtil.executeSqlScript(sqlDdlFilePath);
                        log.info("✅ 自动建表成功: {}", tableName);
                    } else {
                        log.debug("表 {} 已存在，跳过DDL生成", tableName);
                    }
                } catch (Exception ddlException) {
                    log.error("自动建表失败，表: {}", tableName, ddlException);
                    context.put("ddl_error", ddlException.getMessage());
                    throw ddlException;  // 继续抛出以被外层捕获
                }
                // =======================================================================

                // 判断是否为World类型
                currentPhase = "数据导入";
                boolean isWorldType = isWorldTable(tableName);
                String mapType = options != null ? options.getMapType() : null;
                context.put("is_world_type", isWorldType);
                context.put("map_type", mapType);

                if (isWorldType && mapType != null) {
                    // World类型文件
                    WorldXmlToDbGenerator generator = new WorldXmlToDbGenerator(tableName, mapType);
                    generator.xmlTodb();
                } else {
                    // 普通文件
                    // 传入xmlFilePath作为tabFielPath参数，让TabConfLoad.getTale()可以计算配置文件路径
                    XmlToDbGenerator generator = new XmlToDbGenerator(
                        tableName,
                        mapType,
                        xmlFilePath,
                        xmlFilePath  // 修复：传入XML文件路径而不是null
                    );

                    String aiModule = options != null ? options.getAiModule() : null;
                    List<String> selectedColumns = options != null ? options.getSelectedColumns() : null;

                    if (aiModule != null) {
                        currentPhase = "AI处理";
                        context.put("ai_module", aiModule);
                        context.put("ai_columns", selectedColumns);
                    }

                    // 新增：传递 options 以支持冲突解决策略
                    generator.xmlTodb(aiModule, selectedColumns, options);
                }

                log.info("导入成功: {}", xmlFilePath);
                return true;

            } catch (Exception e) {
                log.error("导入失败: {} [阶段: {}]", xmlFilePath, currentPhase, e);

                // 创建 DiagnosticFailure 并包装为 DiagnosticImportException
                red.jiuzhou.batch.diagnosis.DiagnosticFailure failure =
                    red.jiuzhou.batch.diagnosis.DiagnosticFailure.fromException(
                        xmlFile, e, currentPhase, context
                    );

                throw new red.jiuzhou.batch.diagnosis.DiagnosticImportException(failure, e);
            }
        });
    }

    /**
     * 批量导入多个文件（增强版 - 带预检查）
     *
     * @param xmlFiles XML文件列表
     * @param options 导入选项
     * @param callback 进度回调
     * @return 批量导入结果
     */
    public static CompletableFuture<BatchImportResult> importBatchXml(
            List<File> xmlFiles,
            ImportOptions options,
            ProgressCallback callback) {

        return CompletableFuture.supplyAsync(() -> {
            // ===== 新增：预检查阶段 =====
            log.info("========== 第1阶段：执行导入前预检查 ==========");
            PreflightReport preflight = BatchImportPreflightChecker.check(xmlFiles);

            // 打印预检查报告
            preflight.printReport();

            // 保存预检查报告到文件
            preflight.saveToFile("batch_import_preflight.json");

            // 获取可导入的文件列表（排除跳过和错误的）
            List<File> importableFiles = preflight.getImportableFiles();
            List<File> skippedFiles = preflight.getSkippedFileList();

            log.info("预检查完成：可导入 {} 个，跳过 {} 个，错误 {} 个",
                    importableFiles.size(), skippedFiles.size(), preflight.getErrorFiles());
            log.info("========== 第2阶段：开始批量导入 ==========");

            // ===== 原有逻辑：导入有效文件 =====
            BatchImportResult result = new BatchImportResult();
            result.setTotal(importableFiles.size());
            result.setSkipped(skippedFiles.size());  // 预检查跳过的数量

            AtomicInteger processed = new AtomicInteger(0);

            for (File file : importableFiles) {
                String fileName = file.getName();
                String filePath = file.getAbsolutePath();

                try {
                    // 预检查已过滤，这里直接导入
                    // 更新进度（UI线程）
                    int current = processed.incrementAndGet();
                    if (callback != null) {
                        Platform.runLater(() ->
                            callback.onProgress(current, result.getTotal(), fileName)
                        );
                    }

                    // 导入
                    importSingleXml(filePath, options).join();
                    result.getSuccessFiles().add(filePath);
                    result.setSuccess(result.getSuccess() + 1);

                    log.info("✅ 进度 [{}/{}] 导入成功: {}", current, result.getTotal(), fileName);

                } catch (java.util.concurrent.CompletionException ce) {
                    // CompletableFuture.join() 会包装异常为 CompletionException
                    Throwable cause = ce.getCause();

                    red.jiuzhou.batch.diagnosis.DiagnosticFailure failure;

                    if (cause instanceof red.jiuzhou.batch.diagnosis.DiagnosticImportException) {
                        // 已包含诊断信息，直接提取
                        failure = ((red.jiuzhou.batch.diagnosis.DiagnosticImportException) cause)
                            .getDiagnosticFailure();
                        log.debug("提取 DiagnosticFailure: {}", failure.getErrorCode());
                    } else {
                        // 其他异常，创建 DiagnosticFailure
                        failure = red.jiuzhou.batch.diagnosis.DiagnosticFailure.fromException(
                            file, cause != null ? cause : ce, "批量导入", Map.of()
                        );
                        log.debug("创建 DiagnosticFailure: {}", failure.getErrorCode());
                    }

                    // 添加到失败列表
                    result.getFailedFiles().add(failure);
                    result.setFailed(result.getFailed() + 1);

                    // 更新错误统计（用于可视化）
                    result.getErrorsByCategory().merge(failure.getCategory(), 1, Integer::sum);
                    result.getErrorsByLevel().merge(failure.getLevel(), 1, Integer::sum);

                    log.error("❌ 进度 [{}/{}] 导入失败: {} - {} [{}]",
                        processed.get(), result.getTotal(), fileName,
                        failure.structuredError().title(), failure.getErrorCode());

                } catch (Exception e) {
                    // 其他未预期的异常（保底处理）
                    red.jiuzhou.batch.diagnosis.DiagnosticFailure failure =
                        red.jiuzhou.batch.diagnosis.DiagnosticFailure.fromException(
                            file, e, "未知阶段", Map.of()
                        );

                    result.getFailedFiles().add(failure);
                    result.setFailed(result.getFailed() + 1);
                    result.getErrorsByCategory().merge(failure.getCategory(), 1, Integer::sum);
                    result.getErrorsByLevel().merge(failure.getLevel(), 1, Integer::sum);

                    log.error("❌ 进度 [{}/{}] 导入失败（未预期异常）: {}", processed.get(), result.getTotal(), fileName, e);
                }
            }

            // 完成回调（UI线程）
            if (callback != null) {
                Platform.runLater(() -> callback.onComplete(result));
            }

            return result;
        });
    }

    /**
     * 导入目录下所有XML文件（递归）
     *
     * @param directory 目录路径
     * @param recursive 是否递归子目录
     * @param options 导入选项
     * @param callback 进度回调
     * @return 批量导入结果
     */
    public static CompletableFuture<BatchImportResult> importDirectoryXml(
            String directory,
            boolean recursive,
            ImportOptions options,
            ProgressCallback callback) {

        return CompletableFuture.supplyAsync(() -> {
            File dir = new File(directory);
            if (!dir.exists() || !dir.isDirectory()) {
                BatchImportResult result = new BatchImportResult();
                result.getFailedFiles().add(new FailedFile(directory, "目录不存在或不是目录"));
                result.setFailed(1);
                return result;
            }

            // 收集所有XML文件
            List<File> xmlFiles;
            if (recursive) {
                xmlFiles = FileUtil.loopFiles(directory).stream()
                    .filter(f -> f.getName().toLowerCase().endsWith(".xml"))
                    .collect(Collectors.toList());
            } else {
                File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xml"));
                xmlFiles = files != null ? Arrays.asList(files) : new ArrayList<>();
            }

            log.info("扫描目录 {}, 找到 {} 个XML文件", directory, xmlFiles.size());

            // 批量导入
            return importBatchXml(xmlFiles, options, callback).join();
        });
    }

    /**
     * 导入选中文件（支持文件和目录混合）
     *
     * @param paths 文件或目录路径列表
     * @param recursive 遇到目录时是否递归
     * @param options 导入选项
     * @param callback 进度回调
     * @return 批量导入结果
     */
    public static CompletableFuture<BatchImportResult> importSelectedXml(
            List<String> paths,
            boolean recursive,
            ImportOptions options,
            ProgressCallback callback) {

        return CompletableFuture.supplyAsync(() -> {
            List<File> allXmlFiles = new ArrayList<>();

            // 收集所有XML文件
            for (String path : paths) {
                File file = new File(path);
                if (!file.exists()) {
                    log.warn("文件不存在: {}", path);
                    continue;
                }

                if (file.isDirectory()) {
                    // 目录：递归收集XML
                    if (recursive) {
                        List<File> xmlFiles = FileUtil.loopFiles(path).stream()
                            .filter(f -> f.getName().toLowerCase().endsWith(".xml"))
                            .collect(Collectors.toList());
                        allXmlFiles.addAll(xmlFiles);
                    } else {
                        File[] files = file.listFiles((d, name) -> name.toLowerCase().endsWith(".xml"));
                        if (files != null) {
                            allXmlFiles.addAll(Arrays.asList(files));
                        }
                    }
                } else if (file.getName().toLowerCase().endsWith(".xml")) {
                    // 单个XML文件
                    allXmlFiles.add(file);
                }
            }

            log.info("选中 {} 个路径, 收集到 {} 个XML文件", paths.size(), allXmlFiles.size());

            // 批量导入
            return importBatchXml(allXmlFiles, options, callback).join();
        });
    }

    /**
     * 判断是否为World类型表
     */
    private static boolean isWorldTable(String tableName) {
        return WORLD_TABLES.stream()
            .anyMatch(wt -> tableName.toLowerCase().startsWith(wt.toLowerCase()));
    }

    /**
     * 检查XML文件是否为空（只有根节点，无实际数据）
     *
     * @param xmlFile XML文件
     * @return true 如果文件为空或只包含空的根节点
     */
    private static boolean isEmptyXmlFile(File xmlFile) {
        try {
            org.dom4j.Document doc = org.dom4j.io.SAXReader.createDefault()
                .read(xmlFile);
            org.dom4j.Element root = doc.getRootElement();

            // 检查根节点是否有子元素
            if (root.elements().isEmpty()) {
                return true; // 没有子元素，视为空文件
            }

            // 检查第一个子元素是否包含数据
            org.dom4j.Element firstChild = (org.dom4j.Element) root.elements().get(0);
            if (firstChild.elements().isEmpty() && firstChild.getText().trim().isEmpty()) {
                // 第一个子元素也是空的（没有子元素且没有文本），可能是模板
                boolean hasData = false;
                for (Object obj : root.elements()) {
                    org.dom4j.Element elem = (org.dom4j.Element) obj;
                    // 检查是否有任何非空的子元素或属性
                    if (!elem.elements().isEmpty() ||
                        !elem.attributes().isEmpty() ||
                        !elem.getText().trim().isEmpty()) {
                        hasData = true;
                        break;
                    }
                }
                return !hasData;
            }

            return false; // 有数据

        } catch (Exception e) {
            log.warn("检查XML文件是否为空时出错: {}, 错误: {}", xmlFile.getName(), e.getMessage());
            return false; // 出错时不跳过，让后续处理决定
        }
    }

    /**
     * 简化版：导入单个文件（阻塞）
     */
    public static boolean importSingleXmlSync(String xmlFilePath, ImportOptions options) {
        try {
            return importSingleXml(xmlFilePath, options).get();
        } catch (Exception e) {
            throw new RuntimeException("导入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 简化版：导入目录（阻塞）
     */
    public static BatchImportResult importDirectoryXmlSync(
            String directory,
            boolean recursive,
            ImportOptions options) {
        try {
            return importDirectoryXml(directory, recursive, options, null).get();
        } catch (Exception e) {
            BatchImportResult result = new BatchImportResult();
            result.getFailedFiles().add(new FailedFile(directory, e.getMessage()));
            result.setFailed(1);
            return result;
        }
    }
}
