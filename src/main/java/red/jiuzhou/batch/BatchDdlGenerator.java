package red.jiuzhou.batch;

import cn.hutool.core.io.FileUtil;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.xmltosql.XmlProcess;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 批量DDL生成服务
 *
 * 支持：
 * - 单个文件生成DDL
 * - 多个选中文件批量生成
 * - 整个目录递归生成
 * - 进度回调和结果统计
 *
 * @author yanxq
 * @date 2025-12-19
 */
public class BatchDdlGenerator {

    private static final Logger log = LoggerFactory.getLogger(BatchDdlGenerator.class);

    /**
     * 批量生成结果
     */
    public static class BatchResult {
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
     * 进度回调接口
     */
    public interface ProgressCallback {
        void onProgress(int current, int total, String currentFile);
        void onComplete(BatchResult result);
    }

    /**
     * 生成单个文件的DDL
     */
    public static CompletableFuture<String> generateSingleDdl(String xmlFilePath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("开始生成DDL: {}", xmlFilePath);
                String sqlPath = XmlProcess.parseOneXml(xmlFilePath);
                log.info("DDL生成成功: {} -> {}", xmlFilePath, sqlPath);
                return sqlPath;
            } catch (Exception e) {
                log.error("DDL生成失败: " + xmlFilePath, e);
                throw new RuntimeException("生成失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 批量生成多个文件的DDL
     *
     * @param xmlFiles XML文件列表
     * @param callback 进度回调
     * @return 批量生成结果
     */
    public static CompletableFuture<BatchResult> generateBatchDdl(
            List<File> xmlFiles,
            ProgressCallback callback) {

        return CompletableFuture.supplyAsync(() -> {
            BatchResult result = new BatchResult();
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

                    // 更新进度（UI线程）
                    int current = processed.incrementAndGet();
                    if (callback != null) {
                        Platform.runLater(() ->
                            callback.onProgress(current, result.getTotal(), fileName)
                        );
                    }

                    // 生成DDL
                    String sqlPath = XmlProcess.parseOneXml(filePath);
                    result.getSuccessFiles().add(filePath);
                    result.setSuccess(result.getSuccess() + 1);

                    log.info("进度 [{}/{}] 成功: {}", current, result.getTotal(), fileName);

                } catch (Exception e) {
                    result.getFailedFiles().add(new FailedFile(filePath, e.getMessage()));
                    result.setFailed(result.getFailed() + 1);
                    log.error("进度 [{}/{}] 失败: {}", processed.get(), result.getTotal(), fileName, e);
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
     * 生成目录下所有XML文件的DDL（递归）
     *
     * @param directory 目录路径
     * @param recursive 是否递归子目录
     * @param callback 进度回调
     * @return 批量生成结果
     */
    public static CompletableFuture<BatchResult> generateDirectoryDdl(
            String directory,
            boolean recursive,
            ProgressCallback callback) {

        return CompletableFuture.supplyAsync(() -> {
            File dir = new File(directory);
            if (!dir.exists() || !dir.isDirectory()) {
                BatchResult result = new BatchResult();
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

            // 批量生成
            return generateBatchDdl(xmlFiles, callback).join();
        });
    }

    /**
     * 生成选中文件的DDL（支持文件和目录混合）
     *
     * @param paths 文件或目录路径列表
     * @param recursive 遇到目录时是否递归
     * @param callback 进度回调
     * @return 批量生成结果
     */
    public static CompletableFuture<BatchResult> generateSelectedDdl(
            List<String> paths,
            boolean recursive,
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

            // 批量生成
            return generateBatchDdl(allXmlFiles, callback).join();
        });
    }

    /**
     * 简化版：生成单个文件DDL（阻塞）
     */
    public static String generateSingleDdlSync(String xmlFilePath) {
        try {
            return generateSingleDdl(xmlFilePath).get();
        } catch (Exception e) {
            throw new RuntimeException("生成DDL失败: " + e.getMessage(), e);
        }
    }

    /**
     * 简化版：生成目录DDL（阻塞）
     */
    public static BatchResult generateDirectoryDdlSync(String directory, boolean recursive) {
        try {
            return generateDirectoryDdl(directory, recursive, null).get();
        } catch (Exception e) {
            BatchResult result = new BatchResult();
            result.getFailedFiles().add(new FailedFile(directory, e.getMessage()));
            result.setFailed(1);
            return result;
        }
    }
}
