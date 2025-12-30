package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.*;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.dbxml.DbToXmlGenerator;
import red.jiuzhou.dbxml.WorldDbToXmlGenerator;
import red.jiuzhou.dbxml.XmlToDbGenerator;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.util.AIAssistant;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.IncrementalMenuJsonGenerator;
import red.jiuzhou.util.XmlUtil;
import red.jiuzhou.xmltosql.XmlProcess;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * MenuTabPaneExample的扩展功能类
 * 包含AI助手、批量操作等高级功能
 * @author yanxq
 * @date 2025-09-19
 * @version V1.0
 */
public class MenuTabPaneExampleExtensions {

    private static final Logger log = LoggerFactory.getLogger(MenuTabPaneExampleExtensions.class);

    // ============================= 文件夹操作功能实现 =============================

    /**
     * 显示文件夹统计信息
     */
    public static void showFolderStatistics(TreeItem<String> selectedItem, MenuTabPaneExample menuExample) {
        try {
            String folderPath = getFolderPathFromTreeItem(selectedItem);
            if (folderPath == null) return;

            File folder = new File(folderPath);
            if (!folder.exists() || !folder.isDirectory()) {
                showError("文件夹不存在");
                return;
            }

            List<File> files = FileUtil.loopFiles(folder);
            long totalSize = files.stream().mapToLong(File::length).sum();
            long xmlCount = files.stream().filter(f -> f.getName().endsWith(".xml")).count();

            StringBuilder stats = new StringBuilder();
            stats.append("文件夹: ").append(folder.getName()).append("\n");
            stats.append("路径: ").append(folder.getAbsolutePath()).append("\n");
            stats.append("总文件数: ").append(files.size()).append("\n");
            stats.append("XML文件数: ").append(xmlCount).append("\n");
            stats.append("总大小: ").append(formatFileSize(totalSize)).append("\n");

            showInformation("文件夹统计", stats.toString());

        } catch (Exception e) {
            log.error("显示文件夹统计失败", e);
            showError("显示文件夹统计失败: " + e.getMessage());
        }
    }

    /**
     * 在文件夹中搜索文件
     */
    public static void searchInFolder(TreeItem<String> selectedItem) {
        try {
            String folderPath = getFolderPathFromTreeItem(selectedItem);
            if (folderPath == null) return;

            TextInputDialog searchDialog = new TextInputDialog("");
            searchDialog.setTitle("搜索文件");
            searchDialog.setHeaderText("在文件夹中搜索文件");
            searchDialog.setContentText("搜索关键词:");

            searchDialog.showAndWait().ifPresent(keyword -> {
                if (!keyword.trim().isEmpty()) {
                    performFileSearch(folderPath, keyword.trim());
                }
            });

        } catch (Exception e) {
            log.error("搜索文件失败", e);
            showError("搜索文件失败: " + e.getMessage());
        }
    }

    private static void performFileSearch(String folderPath, String keyword) {
        try {
            File folder = new File(folderPath);
            List<File> files = FileUtil.loopFiles(folder);

            List<File> results = files.stream()
                .filter(f -> f.getName().toLowerCase().contains(keyword.toLowerCase()))
                .collect(Collectors.toList());

            if (results.isEmpty()) {
                showInformation("搜索结果", "未找到包含关键词 \"" + keyword + "\" 的文件");
            } else {
                StringBuilder resultText = new StringBuilder();
                resultText.append("找到 ").append(results.size()).append(" 个文件:\n\n");

                for (File file : results.subList(0, Math.min(results.size(), 20))) {
                    resultText.append(file.getName()).append("\n");
                }

                if (results.size() > 20) {
                    resultText.append("\n... 还有 ").append(results.size() - 20).append(" 个文件");
                }

                showInformation("搜索结果", resultText.toString());
            }

        } catch (Exception e) {
            log.error("执行文件搜索失败", e);
            showError("执行文件搜索失败: " + e.getMessage());
        }
    }

    /**
     * 批量格式化文件夹
     */
    public static void batchFormatFolder(TreeItem<String> selectedItem) {
        try {
            String folderPath = getFolderPathFromTreeItem(selectedItem);
            if (folderPath == null) return;

            File folder = new File(folderPath);
            List<String> xmlFiles = FileUtil.loopFiles(folder).stream()
                .filter(f -> f.getName().endsWith(".xml"))
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());

            if (xmlFiles.isEmpty()) {
                showError("文件夹中没有XML文件");
                return;
            }

            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("批量格式化确认");
            confirmAlert.setHeaderText("批量格式化文件夹");
            confirmAlert.setContentText("确定要格式化文件夹中的 " + xmlFiles.size() + " 个XML文件吗？\n原文件将被备份。");

            confirmAlert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    ProgressDialog progressDialog = new ProgressDialog("批量格式化",
                        "正在格式化 " + xmlFiles.size() + " 个XML文件...");
                    progressDialog.show();

                    // 在后台线程执行格式化
                    CompletableFuture.runAsync(() -> {
                        int successCount = 0;
                        for (String filePath : xmlFiles) {
                            try {
                                String content = FileUtil.readUtf8String(filePath);

                                String backupPath = filePath + ".backup_" + System.currentTimeMillis();
                                FileUtil.copy(filePath, backupPath, true);

                                try {
                                    String formattedContent = XmlUtil.formatXml(content);
                                    FileUtil.writeUtf8String(formattedContent, filePath);
                                    successCount++;
                                } catch (org.dom4j.DocumentException | java.io.IOException e) {
                                    log.error("XML格式化失败: {} - {}", filePath, e.getMessage());
                                }
                            } catch (Exception e) {
                                log.error("格式化文件失败: {}", filePath, e);
                            }
                        }

                        int finalSuccessCount = successCount;
                        Platform.runLater(() -> {
                            progressDialog.complete(null);
                            showInformation("批量格式化完成",
                                String.format("成功格式化 %d 个文件，共 %d 个文件", finalSuccessCount, xmlFiles.size()));
                        });
                    });
                }
            });

        } catch (Exception e) {
            log.error("批量格式化文件夹失败", e);
            showError("批量格式化文件夹失败: " + e.getMessage());
        }
    }

    /**
     * 批量备份文件夹
     */
    public static void batchBackupFolder(TreeItem<String> selectedItem) {
        try {
            String folderPath = getFolderPathFromTreeItem(selectedItem);
            if (folderPath == null) return;

            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle("选择备份目录");
            File backupDir = dirChooser.showDialog(null);

            if (backupDir == null) return;

            File folder = new File(folderPath);
            List<String> xmlFiles = FileUtil.loopFiles(folder).stream()
                .filter(f -> f.getName().endsWith(".xml"))
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());

            if (xmlFiles.isEmpty()) {
                showError("文件夹中没有XML文件");
                return;
            }

            ProgressDialog progressDialog = new ProgressDialog("批量备份",
                "正在备份 " + xmlFiles.size() + " 个XML文件...");
            progressDialog.show();

            // 在后台线程执行备份
            CompletableFuture.runAsync(() -> {
                int successCount = 0;
                for (String filePath : xmlFiles) {
                    try {
                        File sourceFile = new File(filePath);
                        String backupPath = backupDir.getAbsolutePath() + File.separator +
                                          sourceFile.getName() + ".backup_" + System.currentTimeMillis();

                        FileUtil.copy(sourceFile, new File(backupPath), true);
                        successCount++;
                    } catch (Exception e) {
                        log.error("备份文件失败: {}", filePath, e);
                    }
                }

                int finalSuccessCount = successCount;
                Platform.runLater(() -> {
                    progressDialog.complete(null);
                    showInformation("批量备份完成",
                        String.format("成功备份 %d 个文件到 %s", finalSuccessCount, backupDir.getName()));
                });
            });

        } catch (Exception e) {
            log.error("批量备份文件夹失败", e);
            showError("批量备份文件夹失败: " + e.getMessage());
        }
    }

    /**
     * 一键为目录内所有 XML 文件生成 DDL
     * 包含完整的单文件DDL生成功能：解析XML → 生成SQL → 执行建表
     * 注意：仅处理当前目录，不包括子目录
     */
    public static void generateDdlForFolder(TreeItem<String> selectedItem) {
        executeFolderOperation(
            "批量DDL生成",
            "正在准备生成DDL文件...",
            selectedItem,
            xmlFile -> {
                String tabName = FileUtil.mainName(xmlFile.getName());
                if ("world".equalsIgnoreCase(tabName)) {
                    return "跳过（无需DDL生成，视为成功）";
                }

                // 使用与单文件DDL生成相同的方法：parseOneXml
                // 这确保批量生成与单文件生成的行为完全一致
                String sqlFilePath = XmlProcess.parseOneXml(xmlFile.getAbsolutePath());

                // 执行SQL脚本，在数据库中创建表
                try {
                    DatabaseUtil.executeSqlScript(sqlFilePath);
                    log.info("成功为 {} 生成并执行DDL", xmlFile.getName());
                } catch (Exception ex) {
                    throw new RuntimeException("执行DDL失败: " + XmlUtil.getErrorMsg(ex), ex);
                }

                return "生成并执行成功";
            },
            () -> {
                // 批量操作完成后，刷新目录结构
                try {
                    IncrementalMenuJsonGenerator.createJsonIncrementally();
                    log.info("批量DDL生成完成，已刷新目录结构");
                } catch (Exception e) {
                    log.warn("刷新目录结构失败: {}", XmlUtil.getErrorMsg(e));
                }
            }
        );
    }

    /**
     * 一键将目录内所有 XML 文件导入数据库
     */
    public static void importFolderToDatabase(TreeItem<String> selectedItem) {
        executeFolderOperation(
            "批量导入数据库",
            "正在导入XML数据到数据库...",
            selectedItem,
            xmlFile -> {
                String xmlPath = xmlFile.getAbsolutePath();
                String tabName = FileUtil.mainName(xmlFile);
                String tabFilePath = stripXmlExtension(xmlPath);
                String mapType = deriveMapType(tabName, xmlFile);

                // 检查XML文件是否为空
                if (xmlFile.length() == 0) {
                    log.warn("XML文件为空，跳过导入: {}", xmlFile.getName());
                    throw new RuntimeException("XML文件为空，无法导入");
                }

                // 尝试创建生成器，如果配置文件不存在，先生成DDL
                try {
                    XmlToDbGenerator generator = new XmlToDbGenerator(tabName, mapType, xmlPath, tabFilePath);
                    generator.xmlTodb(null, null);
                    return "导入成功";
                } catch (RuntimeException e) {
                    // 特殊处理：配置错误（tableName/xmlRootTag/sql为空）说明XML文件无效
                    if (e.getMessage() != null && e.getMessage().contains("配置错误")) {
                        log.warn("XML文件无效或配置错误，跳过导入: {} - {}", xmlFile.getName(), e.getMessage());
                        throw new RuntimeException("XML文件无效: " + e.getMessage());
                    }

                    // 如果配置文件不存在，尝试自动生成DDL
                    if (e.getMessage() != null && e.getMessage().contains("表配置文件不存在")) {
                        log.warn("配置文件不存在，尝试自动生成DDL: {}", xmlFile.getName());
                        try {
                            // 生成DDL
                            String sqlFilePath = XmlProcess.parseOneXml(xmlPath);
                            DatabaseUtil.executeSqlScript(sqlFilePath);
                            log.info("已自动生成并执行DDL: {}", xmlFile.getName());

                            // 重试导入
                            XmlToDbGenerator generator = new XmlToDbGenerator(tabName, mapType, xmlPath, tabFilePath);
                            generator.xmlTodb(null, null);
                            return "导入成功（已自动生成DDL）";
                        } catch (Exception ddlEx) {
                            throw new RuntimeException("自动生成DDL失败: " + XmlUtil.getErrorMsg(ddlEx), ddlEx);
                        }
                    }
                    throw e;
                }
            },
            null
        );
    }

    /**
     * 一键将目录内所有表导出为 XML
     */
    public static void exportFolderToXml(TreeItem<String> selectedItem) {
        // ==================== 导出预验证（2025-12-29新增）====================
        if (selectedItem == null) {
            showError("请选择一个目录执行此操作");
            return;
        }

        String folderPath = getFolderPathFromTreeItem(selectedItem);
        if (folderPath == null || folderPath.trim().isEmpty()) {
            showError("无法获取目录路径");
            return;
        }

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            showError("请选择有效的目录节点");
            return;
        }

        List<File> xmlFiles = collectXmlFiles(folder);
        if (xmlFiles.isEmpty()) {
            showInformation("批量导出XML", "目录中没有可处理的XML文件。");
            return;
        }

        // 收集所有表名进行预验证
        List<String> tableNames = xmlFiles.stream()
            .map(f -> FileUtil.mainName(f))
            .collect(Collectors.toList());

        log.info("批量导出XML - 开始预验证 {} 个表", tableNames.size());
        red.jiuzhou.validation.PreExportValidator validator = new red.jiuzhou.validation.PreExportValidator();
        List<red.jiuzhou.validation.PreExportValidator.ValidationResult> validationResults =
            validator.validateBatch(tableNames);

        long canExportCount = validationResults.stream()
            .filter(red.jiuzhou.validation.PreExportValidator.ValidationResult::canExport)
            .count();
        long hasIssuesCount = validationResults.stream()
            .filter(red.jiuzhou.validation.PreExportValidator.ValidationResult::hasIssues)
            .count();
        long totalBlacklistedFields = validationResults.stream()
            .mapToLong(r -> r.getBlacklistedFields().size())
            .sum();

        log.info("预检查完成: {}个可导出, {}个有警告, 共{}个字段将被过滤",
            canExportCount, hasIssuesCount, totalBlacklistedFields);

        // 显示预验证结果摘要（如果有警告）
        if (hasIssuesCount > 0) {
            StringBuilder summary = new StringBuilder();
            summary.append(String.format("预检查完成:\n\n"));
            summary.append(String.format("✅ %d 个表可导出\n", canExportCount));
            summary.append(String.format("⚠️  %d 个表有警告\n", hasIssuesCount));
            summary.append(String.format("🔧 %d 个不兼容字段将自动过滤\n\n", totalBlacklistedFields));
            summary.append("是否继续导出？");

            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("导出预检查");
            confirmAlert.setHeaderText("发现潜在问题");
            confirmAlert.setContentText(summary.toString());

            Optional<ButtonType> result = confirmAlert.showAndWait();
            if (!result.isPresent() || result.get() != ButtonType.OK) {
                log.info("用户取消导出操作");
                return;
            }
        }
        // ======================================================================

        executeFolderOperation(
            "批量导出XML",
            "正在导出数据库数据为XML...",
            selectedItem,
            xmlFile -> {
                String xmlPath = xmlFile.getAbsolutePath();
                String tabName = FileUtil.mainName(xmlFile);
                String tabFilePath = stripXmlExtension(xmlPath);
                String mapType = deriveMapType(tabName, xmlFile);
                String exportedFilePath;
                if ("world".equalsIgnoreCase(tabName)) {
                    WorldDbToXmlGenerator generator = new WorldDbToXmlGenerator(tabName, mapType, tabFilePath);
                    exportedFilePath = generator.processAndMerge();
                } else {
                    DbToXmlGenerator generator = new DbToXmlGenerator(tabName, mapType, tabFilePath);
                    exportedFilePath = generator.processAndMerge();
                }
                return "导出成功 → " + exportedFilePath;
            },
            null
        );
    }

    // ============================= AI功能实现 =============================

    /**
     * 执行AI优化
     */
    public static void executeAIOptimization(TreeItem<String> selectedItem,
                                             AIAssistant.OptimizeType optimizeType,
                                             AIAssistant aiAssistant) {
        if (aiAssistant == null) {
            showError("AI助手未初始化，请检查系统配置");
            return;
        }

        String filePath = getFilePathFromTreeItem(selectedItem);
        if (filePath == null) {
            showError("无法获取文件路径");
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog("AI优化进行中",
            "正在使用AI优化文件内容，请稍候...");
        progressDialog.show();

        CompletableFuture<String> future;
        try {
            future = aiAssistant.optimizeFileContent(filePath, optimizeType);
        } catch (Exception ex) {
            log.error("执行AI优化失败", ex);
            Platform.runLater(() -> {
                progressDialog.complete(null);
                showError("AI优化失败: " + ex.getMessage());
            });
            return;
        }

        future.whenComplete((result, throwable) -> {
            Platform.runLater(() -> {
                progressDialog.complete(null);

                if (throwable != null) {
                    log.error("AI优化失败", throwable);
                    String message = throwable.getMessage() != null ? throwable.getMessage() : "未知错误";
                    showError("AI优化失败: " + message);
                } else {
                    showInformation("AI优化结果", result);
                }
            });
        });
    }

    public static void optimizeFileWithDialog(TreeItem<String> selectedItem,
                                              AIAssistant aiAssistant) {
        if (selectedItem == null) {
            showError("请先选择要处理的文件");
            return;
        }
        if (aiAssistant == null) {
            showError("AI助手未初始化，请检查系统配置");
            return;
        }

        AIOptimizationDialog dialog = new AIOptimizationDialog();
        dialog.showAndWait().ifPresent(optimizeType ->
            executeAIOptimization(selectedItem, optimizeType, aiAssistant)
        );
    }

    /**
     * 执行智能分析
     */
    public static void executeContentAnalysis(TreeItem<String> selectedItem,
                                              AIAssistant aiAssistant) {
        if (aiAssistant == null) {
            showError("AI助手未初始化，请检查系统配置");
            return;
        }

        String filePath = getFilePathFromTreeItem(selectedItem);
        if (filePath == null) {
            showError("无法获取文件路径");
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog("智能分析进行中",
            "正在分析文件内容，请稍候...");
        progressDialog.show();

        CompletableFuture<String> future;
        try {
            future = aiAssistant.analyzeContent(filePath);
        } catch (Exception ex) {
            log.error("执行智能分析失败", ex);
            Platform.runLater(() -> {
                progressDialog.complete(null);
                showError("智能分析失败: " + ex.getMessage());
            });
            return;
        }

        future.whenComplete((result, throwable) -> {
            Platform.runLater(() -> {
                progressDialog.complete(null);

                if (throwable != null) {
                    log.error("智能分析失败", throwable);
                    String message = throwable.getMessage() != null ? throwable.getMessage() : "未知错误";
                    showError("智能分析失败: " + message);
                } else {
                    showAnalysisResult("智能分析结果", result);
                }
            });
        });
    }

    /**
     * 批量优化文件夹
     */
    public static void batchOptimizeFolder(TreeItem<String> selectedItem,
                                           AIAssistant aiAssistant) {
        if (aiAssistant == null) {
            showError("AI助手未初始化，请检查系统配置");
            return;
        }

        try {
            String folderPath = getFolderPathFromTreeItem(selectedItem);
            if (folderPath == null) {
                showError("无法获取目录路径");
                return;
            }

            File folder = new File(folderPath);
            List<String> xmlFiles = FileUtil.loopFiles(folder).stream()
                .filter(f -> f.getName().endsWith(".xml"))
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());

            if (xmlFiles.isEmpty()) {
                showError("目录中没有XML文件");
                return;
            }

            AIOptimizationDialog dialog = new AIOptimizationDialog();
            dialog.showAndWait().ifPresent(optimizeType -> {
                ProgressDialog progressDialog = new ProgressDialog("批量AI优化",
                    "正在批量优化 " + xmlFiles.size() + " 个文件，请稍候...");
                progressDialog.show();

                CompletableFuture<Map<String, String>> future;
                try {
                    future = aiAssistant.batchOptimizeFiles(xmlFiles, optimizeType);
                } catch (Exception ex) {
                    log.error("批量AI优化任务启动失败", ex);
                    Platform.runLater(() -> {
                        progressDialog.complete(null);
                        showError("批量优化失败: " + ex.getMessage());
                    });
                    return;
                }

                future.whenComplete((results, throwable) -> {
                    Platform.runLater(() -> {
                        progressDialog.complete(null);

                        if (throwable != null) {
                            log.error("批量AI优化失败", throwable);
                            String msg = throwable.getMessage() != null ? throwable.getMessage() : "未知错误";
                            showError("批量优化失败: " + msg);
                        } else {
                            showBatchResults("批量优化结果", results);
                        }
                    });
                });
            });

        } catch (Exception e) {
            log.error("批量优化文件夹失败", e);
            showError("批量优化文件夹失败: " + e.getMessage());
        }
    }
// ============================= 辅助工具方法 =============================

    private static void executeFolderOperation(String title,
                                               String initialMessage,
                                               TreeItem<String> selectedItem,
                                               FolderFileProcessor processor,
                                               Runnable afterAll) {
        if (selectedItem == null) {
            showError("请选择一个目录执行此操作");
            return;
        }

        String folderPath = getFolderPathFromTreeItem(selectedItem);
        if (folderPath == null || folderPath.trim().isEmpty()) {
            showError("无法获取目录路径");
            return;
        }

        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            showError("请选择有效的目录节点");
            return;
        }

        List<File> xmlFiles = collectXmlFiles(folder);
        if (xmlFiles.isEmpty()) {
            showInformation(title, "目录中没有可处理的XML文件。");
            return;
        }

        log.info("{} - 开始处理目录: {} ({} 个XML)", title, folderPath, xmlFiles.size());

        ProgressDialog progressDialog = new ProgressDialog(title, initialMessage);
        progressDialog.show();

        CompletableFuture.runAsync(() -> {
            Map<String, String> results = new LinkedHashMap<>();
            AtomicInteger processed = new AtomicInteger();
            int total = xmlFiles.size();

            for (File xmlFile : xmlFiles) {
                try {
                    String message = processor.process(xmlFile);
                    results.put(xmlFile.getAbsolutePath(), message);
                } catch (Exception ex) {
                    log.error("{} 失败: {}", title, xmlFile.getAbsolutePath(), ex);
                    results.put(xmlFile.getAbsolutePath(), "失败: " + XmlUtil.getErrorMsg(ex));
                }

                int current = processed.incrementAndGet();
                progressDialog.updateProgress(current / (double) total,
                    String.format("正在处理 (%d/%d)...", current, total));
            }

            if (afterAll != null) {
                try {
                    afterAll.run();
                } catch (Exception ex) {
                    log.warn("{} 后续操作执行失败: {}", title, XmlUtil.getErrorMsg(ex));
                }
            }

            Platform.runLater(() -> {
                progressDialog.updateProgress(1.0, "操作完成，正在汇总结果...");
                progressDialog.complete(null);
                showBatchResults(title + "结果", results);
            });
        }).exceptionally(ex -> {
            log.error("{} 执行异常", title, ex);
            Platform.runLater(() -> {
                progressDialog.complete(null);
                showError(title + "失败: " + XmlUtil.getErrorMsg(ex));
            });
            return null;
        });
    }

    /**
     * 收集目录下的XML文件（不包括子目录）
     * @param folder 目录
     * @return XML文件列表
     */
    private static List<File> collectXmlFiles(File folder) {
        // 只收集当前目录下的XML文件，不递归子目录
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));
        if (files == null || files.length == 0) {
            return new ArrayList<>();
        }
        return Arrays.stream(files)
            .filter(File::isFile)
            .collect(Collectors.toList());
    }

    private static String stripXmlExtension(String filePath) {
        if (filePath == null) {
            return null;
        }
        return filePath.toLowerCase().endsWith(".xml")
            ? filePath.substring(0, filePath.length() - 4)
            : filePath;
    }

    private static String deriveMapType(String tabName, File xmlFile) {
        if (tabName == null || xmlFile == null) {
            return null;
        }
        if (!"world".equalsIgnoreCase(tabName)) {
            return null;
        }
        File parent = xmlFile.getParentFile();
        return parent != null ? parent.getName() : null;
    }

    private static String getFilePathFromTreeItem(TreeItem<String> treeItem) {
        try {
            String fullPath = getTabFullPath(treeItem);
            if (fullPath != null && (fullPath.contains(":") || fullPath.startsWith("/") || fullPath.startsWith("\\"))) {
                return fullPath + ".xml";
            } else {
                String homePath = YamlUtils.getProperty("file.homePath");
                return homePath + File.separator + fullPath + ".xml";
            }
        } catch (Exception e) {
            log.error("获取文件路径失败", e);
            return null;
        }
    }

    private static String getFolderPathFromTreeItem(TreeItem<String> treeItem) {
        try {
            String fullPath = getTabFullPath(treeItem);
            if (fullPath != null && (fullPath.contains(":") || fullPath.startsWith("/") || fullPath.startsWith("\\"))) {
                return fullPath;
            } else {
                String homePath = YamlUtils.getProperty("file.homePath");
                return homePath + File.separator + fullPath;
            }
        } catch (Exception e) {
            log.error("获取文件夹路径失败", e);
            return null;
        }
    }

    private static String getTabFullPath(TreeItem<String> treeItem) {
        return getParentPath(treeItem, treeItem.getValue());
    }

    private static String getParentPath(TreeItem<String> treeItem, String cpath) {
        TreeItem<String> parentTreeItem = treeItem.getParent();
        if (parentTreeItem != null) {
            String path = parentTreeItem.getValue();
            cpath = path + File.separator + cpath;
            return getParentPath(parentTreeItem, cpath);
        }
        return cpath.replace("Root" + File.separator, "");
    }

    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }

    private static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误提示");
        alert.setHeaderText("发生异常");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static void showInformation(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private static void showAnalysisResult(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText("AI智能分析结果");

        TextArea textArea = new TextArea(content);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(20);
        textArea.setPrefColumnCount(60);

        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }

    private static void showBatchResults(String title, Map<String, String> results) {
        StringBuilder resultText = new StringBuilder();
        int successCount = 0;
        int failureCount = 0;

        for (Map.Entry<String, String> entry : results.entrySet()) {
            String fileName = new File(entry.getKey()).getName();
            String result = entry.getValue();

            resultText.append(fileName).append(": ").append(result).append("\n");

            if (result.contains("成功")) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        resultText.insert(0, String.format("成功: %d, 失败: %d\n\n", successCount, failureCount));

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText("批量操作完成");

        TextArea textArea = new TextArea(resultText.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(15);
        textArea.setPrefColumnCount(80);

        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }

    // ============================= 对话框类 =============================

    @FunctionalInterface
    private interface FolderFileProcessor {
        String process(File xmlFile) throws Exception;
    }

    /**
     * 进度对话框
     */
    public static class ProgressDialog extends Alert {
        private final ProgressIndicator progressIndicator;
        private final Button closeButton;
        private final BooleanProperty completed = new SimpleBooleanProperty(false);

        public ProgressDialog(String title, String message) {
            super(AlertType.INFORMATION);
            setTitle(title);
            setHeaderText(null);
            setContentText(message);
            getDialogPane().getButtonTypes().clear();

            progressIndicator = new ProgressIndicator();
            progressIndicator.setPrefSize(70, 70);
            progressIndicator.setProgress(0);
            getDialogPane().setGraphic(progressIndicator);

            ButtonType closeType = new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE);
            getDialogPane().getButtonTypes().add(closeType);
            closeButton = (Button) getDialogPane().lookupButton(closeType);
            closeButton.setDisable(true);
            closeButton.disableProperty().bind(completed.not());

            setOnCloseRequest(event -> {
                if (!completed.get()) {
                    event.consume();
                }
            });
        }

        public void updateProgress(double progress, String message) {
            Platform.runLater(() -> {
                progressIndicator.setProgress(Math.min(1.0, Math.max(0.0, progress)));
                if (message != null && !message.isEmpty()) {
                    setContentText(message);
                }
            });
        }

        public void complete(String message) {
            Platform.runLater(() -> {
                completed.set(true);
                closeButton.disableProperty().unbind();
                closeButton.setDisable(false);
                if (message != null && !message.isEmpty()) {
                    setContentText(message);
                }
                Window window = getDialogPane().getScene() != null ? getDialogPane().getScene().getWindow() : null;
                if (window != null) {
                    window.hide();
                } else {
                    close();
                }
            });
        }
    }

    /**
     * AI优化类型选择对话框
     */
    public static class AIOptimizationDialog extends Dialog<AIAssistant.OptimizeType> {
        public AIOptimizationDialog() {
            setTitle("选择AI优化类型");
            setHeaderText("请选择要执行的AI优化类型");

            ButtonType okButtonType = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
            getDialogPane().getButtonTypes().addAll(okButtonType, ButtonType.CANCEL);

            ComboBox<AIAssistant.OptimizeType> comboBox = new ComboBox<>();
            comboBox.getItems().addAll(AIAssistant.OptimizeType.values());
            comboBox.setValue(AIAssistant.OptimizeType.WUXIA_STYLE);

            // 自定义显示文本
            comboBox.setCellFactory(param -> new ListCell<AIAssistant.OptimizeType>() {
                @Override
                protected void updateItem(AIAssistant.OptimizeType item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.getDisplayName());
                    }
                }
            });

            comboBox.setButtonCell(new ListCell<AIAssistant.OptimizeType>() {
                @Override
                protected void updateItem(AIAssistant.OptimizeType item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                    } else {
                        setText(item.getDisplayName());
                    }
                }
            });

            VBox content = new VBox(10);
            content.getChildren().addAll(new Label("优化类型:"), comboBox);
            getDialogPane().setContent(content);

            setResultConverter(dialogButton -> {
                if (dialogButton == okButtonType) {
                    return comboBox.getValue();
                }
                return null;
            });
        }
    }
}




