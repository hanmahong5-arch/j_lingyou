package red.jiuzhou.validation;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 批量导入预检查报告
 *
 * @author Claude
 * @date 2025-12-28
 */
public class PreflightReport {

    private static final Logger log = LoggerFactory.getLogger(PreflightReport.class);

    private LocalDateTime checkTime;
    private int totalFiles;
    private int validFiles;
    private int skippedFiles;
    private int errorFiles;

    private List<FileCheckResult> results;
    private Map<String, Integer> errorStats;  // 错误类型统计

    public PreflightReport() {
        this.checkTime = LocalDateTime.now();
        this.results = new ArrayList<>();
        this.errorStats = new HashMap<>();
    }

    public void addResult(FileCheckResult result) {
        results.add(result);

        // 更新统计
        if (result.getAction() == FileCheckResult.Action.SKIP) {
            skippedFiles++;
        } else if (result.hasErrors()) {
            errorFiles++;
        } else {
            validFiles++;
        }

        // 统计错误类型
        for (String errorType : result.getErrorTypes()) {
            errorStats.put(errorType, errorStats.getOrDefault(errorType, 0) + 1);
        }
    }

    public int getTotalFiles() {
        return results.size();
    }

    public int getValidFiles() {
        return validFiles;
    }

    public int getSkippedFiles() {
        return skippedFiles;
    }

    public int getErrorFiles() {
        return errorFiles;
    }

    public List<FileCheckResult> getResults() {
        return results;
    }

    /**
     * 获取可以导入的文件列表
     */
    public List<File> getImportableFiles() {
        return results.stream()
                .filter(r -> r.getAction() == FileCheckResult.Action.IMPORT ||
                             r.getAction() == FileCheckResult.Action.AUTO_FIX)
                .map(FileCheckResult::getFile)
                .collect(Collectors.toList());
    }

    /**
     * 获取跳过的文件列表
     */
    public List<File> getSkippedFileList() {
        return results.stream()
                .filter(r -> r.getAction() == FileCheckResult.Action.SKIP)
                .map(FileCheckResult::getFile)
                .collect(Collectors.toList());
    }

    /**
     * 打印报告到控制台
     */
    public void printReport() {
        log.info("========== 批量导入预检查报告 ==========");
        log.info("检查时间: {}", checkTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        log.info("文件总数: {}", getTotalFiles());
        log.info("有效文件: {}", validFiles);
        log.info("跳过文件: {}", skippedFiles);
        log.info("错误文件: {}", errorFiles);
        log.info("");

        if (!errorStats.isEmpty()) {
            log.info("错误统计:");
            errorStats.forEach((type, count) ->
                log.info("  - {}: {} 个", type, count));
            log.info("");
        }

        // 打印每个文件的详细信息
        for (FileCheckResult result : results) {
            String status = result.getAction() == FileCheckResult.Action.SKIP ? "跳过" :
                           result.hasErrors() ? "错误" : "正常";

            log.info("[{}] {} - {}", status, result.getTableName(), result.getFile().getName());

            if (!result.getErrors().isEmpty()) {
                result.getErrors().forEach(error -> log.info("    ❌ {}", error));
            }

            if (!result.getWarnings().isEmpty()) {
                result.getWarnings().forEach(warning -> log.info("    ⚠️  {}", warning));
            }

            if (!result.getFixes().isEmpty()) {
                result.getFixes().forEach(fix -> log.info("    🔧 {}", fix));
            }
        }

        log.info("========================================");
    }

    /**
     * 保存报告到JSON文件
     */
    public void saveToFile(String filePath) {
        try {
            Map<String, Object> reportData = new HashMap<>();
            reportData.put("检查时间", checkTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            reportData.put("文件总数", getTotalFiles());
            reportData.put("有效文件", validFiles);
            reportData.put("跳过文件", skippedFiles);
            reportData.put("错误文件", errorFiles);
            reportData.put("错误统计", errorStats);

            List<Map<String, Object>> fileDetails = new ArrayList<>();
            for (FileCheckResult result : results) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("表名", result.getTableName());
                detail.put("文件", result.getFile().getName());
                detail.put("状态", result.getAction().toString());
                detail.put("错误", result.getErrors());
                detail.put("警告", result.getWarnings());
                detail.put("修复方案", result.getFixes());
                fileDetails.add(detail);
            }
            reportData.put("文件详情", fileDetails);

            String json = JSON.toJSONString(reportData, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
            Files.writeString(new File(filePath).toPath(), json);

            log.info("预检查报告已保存到: {}", filePath);

        } catch (IOException e) {
            log.error("保存预检查报告失败: {}", filePath, e);
        }
    }

    @Override
    public String toString() {
        return String.format("PreflightReport[total=%d, valid=%d, skipped=%d, error=%d]",
                getTotalFiles(), validFiles, skippedFiles, errorFiles);
    }
}
