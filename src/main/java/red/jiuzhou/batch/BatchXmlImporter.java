package red.jiuzhou.batch;

import cn.hutool.core.io.FileUtil;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.dbxml.TableConf;
import red.jiuzhou.dbxml.WorldXmlToDbGenerator;
import red.jiuzhou.dbxml.XmlToDbGenerator;

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
     * 批量导入结果
     */
    public static class BatchImportResult {
        private int total;          // 总文件数
        private int success;        // 成功数
        private int failed;         // 失败数
        private int skipped;        // 跳过数
        private List<String> successFiles = new ArrayList<>();
        private List<FailedFile> failedFiles = new ArrayList<>();

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public int getSuccess() { return success; }
        public void setSuccess(int success) { this.success = success; }

        public int getFailed() { return failed; }
        public void setFailed(int failed) { this.failed = failed; }

        public int getSkipped() { return skipped; }
        public void setSkipped(int skipped) { this.skipped = skipped; }

        public List<String> getSuccessFiles() { return successFiles; }
        public List<FailedFile> getFailedFiles() { return failedFiles; }

        public String getSummary() {
            return String.format("总计: %d, 成功: %d, 失败: %d, 跳过: %d",
                total, success, failed, skipped);
        }
    }

    /**
     * 失败文件记录
     */
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

        public String getAiModule() { return aiModule; }
        public void setAiModule(String aiModule) { this.aiModule = aiModule; }

        public List<String> getSelectedColumns() { return selectedColumns; }
        public void setSelectedColumns(List<String> selectedColumns) { this.selectedColumns = selectedColumns; }

        public String getMapType() { return mapType; }
        public void setMapType(String mapType) { this.mapType = mapType; }

        public boolean isClearTableFirst() { return clearTableFirst; }
        public void setClearTableFirst(boolean clearTableFirst) { this.clearTableFirst = clearTableFirst; }
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
            try {
                log.info("开始导入XML: {}", xmlFilePath);

                // 从文件名提取表名
                String fileName = FileUtil.getName(xmlFilePath);
                String tableName = fileName.substring(0, fileName.lastIndexOf('.'));

                // 判断是否为World类型
                boolean isWorldType = isWorldTable(tableName);
                String mapType = options != null ? options.getMapType() : null;

                if (isWorldType && mapType != null) {
                    // World类型文件
                    WorldXmlToDbGenerator generator = new WorldXmlToDbGenerator(tableName, mapType);
                    generator.xmlTodb();
                } else {
                    // 普通文件
                    XmlToDbGenerator generator = new XmlToDbGenerator(
                        tableName,
                        mapType,
                        xmlFilePath,
                        null
                    );

                    String aiModule = options != null ? options.getAiModule() : null;
                    List<String> selectedColumns = options != null ? options.getSelectedColumns() : null;
                    generator.xmlTodb(aiModule, selectedColumns);
                }

                log.info("导入成功: {}", xmlFilePath);
                return true;

            } catch (Exception e) {
                log.error("导入失败: " + xmlFilePath, e);
                throw new RuntimeException("导入失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 批量导入多个文件
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
            BatchImportResult result = new BatchImportResult();
            result.setTotal(xmlFiles.size());

            AtomicInteger processed = new AtomicInteger(0);

            for (File file : xmlFiles) {
                String fileName = file.getName();
                String filePath = file.getAbsolutePath();

                try {
                    // 跳过非XML文件
                    if (!fileName.toLowerCase().endsWith(".xml")) {
                        result.setSkipped(result.getSkipped() + 1);
                        continue;
                    }

                    // 检查是否有对应的表配置
                    String tableName = fileName.substring(0, fileName.lastIndexOf('.'));
                    TableConf tableConf = TabConfLoad.getTale(tableName, null);
                    if (tableConf == null) {
                        log.warn("跳过（无表配置）: {}", fileName);
                        result.setSkipped(result.getSkipped() + 1);
                        continue;
                    }

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

                    log.info("进度 [{}/{}] 导入成功: {}", current, result.getTotal(), fileName);

                } catch (Exception e) {
                    result.getFailedFiles().add(new FailedFile(filePath, e.getMessage()));
                    result.setFailed(result.getFailed() + 1);
                    log.error("进度 [{}/{}] 导入失败: {}", processed.get(), result.getTotal(), fileName, e);
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
                xmlFiles = files != null ? List.of(files) : new ArrayList<>();
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
                            allXmlFiles.addAll(List.of(files));
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
