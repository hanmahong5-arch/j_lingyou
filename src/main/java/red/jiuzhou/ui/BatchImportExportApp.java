package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.dbxml.DbToXmlGenerator;
import red.jiuzhou.dbxml.WorldDbToXmlGenerator;
import red.jiuzhou.util.XmlUtil;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.xmltosql.XmlProcess;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 批量导入导出工具
 * 支持批量处理目录下所有XML文件的导入导出操作
 *
 * @author Claude
 * @date 2025-11-13
 */
public class BatchImportExportApp {

    private static final Logger log = LoggerFactory.getLogger(BatchImportExportApp.class);

    private TextArea resultArea;
    private TextField directoryField;
    private Stage currentStage;

    public void show(Stage primaryStage) {
        currentStage = new Stage();
        currentStage.setTitle("📁 批量导入/导出工具");
        currentStage.initOwner(primaryStage);

        // 目录选择区域
        Label dirLabel = new Label("目录:");
        directoryField = new TextField();
        directoryField.setPromptText("请选择包含XML文件的目录");
        directoryField.setPrefWidth(600);
        directoryField.setEditable(false);

        Button chooseDirBtn = new Button("📂 选择目录");
        chooseDirBtn.setOnAction(e -> chooseDirectory());

        HBox dirBox = new HBox(10, dirLabel, directoryField, chooseDirBtn);
        dirBox.setAlignment(Pos.CENTER_LEFT);
        dirBox.setPadding(new Insets(10));

        // 结果显示区域
        resultArea = new TextArea();
        resultArea.setPrefHeight(400);
        resultArea.setWrapText(true);
        resultArea.setEditable(false);
        VBox.setVgrow(resultArea, Priority.ALWAYS);

        // 按钮区域
        Button batchExportBtn = new Button("📤 批量导出 (DB→XML)");
        batchExportBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        batchExportBtn.setTooltip(new Tooltip("将数据库中的数据批量导出为XML文件"));
        batchExportBtn.setOnAction(e -> batchExport());

        Button batchImportBtn = new Button("📥 批量导入 (XML→DB)");
        batchImportBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");
        batchImportBtn.setTooltip(new Tooltip("将目录下所有XML文件批量导入到数据库"));
        batchImportBtn.setOnAction(e -> batchImport());

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);

        HBox buttonBox = new HBox(15, batchExportBtn, batchImportBtn, spinner);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10));

        // 主布局
        VBox root = new VBox(10);
        root.getChildren().addAll(dirBox, resultArea, buttonBox);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 1200, 600);
        currentStage.setScene(scene);
        currentStage.show();

        // 自动加载默认目录
        loadDefaultDirectory();
    }

    /**
     * 选择目录
     */
    private void chooseDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择包含XML文件的目录");

        // 设置初始目录
        String currentPath = directoryField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists() && currentDir.isDirectory()) {
                chooser.setInitialDirectory(currentDir);
            }
        }

        File selectedDir = chooser.showDialog(currentStage);
        if (selectedDir != null) {
            directoryField.setText(selectedDir.getAbsolutePath());
            resultArea.appendText(String.format("已选择目录: %s\n\n", selectedDir.getAbsolutePath()));
        }
    }

    /**
     * 加载默认目录
     */
    private void loadDefaultDirectory() {
        try {
            String cltDataPath = YamlUtils.getProperty("file.cltDataPath");
            if (cltDataPath != null && !cltDataPath.isEmpty()) {
                directoryField.setText(cltDataPath);
                resultArea.appendText(String.format("默认目录: %s\n", cltDataPath));
                resultArea.appendText("提示: 您可以点击'选择目录'按钮更改处理目录\n\n");
            }
        } catch (Exception e) {
            log.warn("加载默认目录失败: {}", e.getMessage());
        }
    }

    /**
     * 批量导出 (DB → XML) - 支持选择性导出
     */
    private void batchExport() {
        String directory = directoryField.getText();
        if (directory == null || directory.trim().isEmpty()) {
            showAlert("请先选择目录！");
            return;
        }

        File dir = new File(directory);
        if (!dir.exists() || !dir.isDirectory()) {
            showAlert("选择的目录不存在！");
            return;
        }

        // 获取目录下所有XML文件（作为模板）
        List<File> allXmlFiles = FileUtil.loopFiles(directory).stream()
                .filter(file -> file.getName().endsWith(".xml"))
                .collect(Collectors.toList());

        if (allXmlFiles.isEmpty()) {
            showAlert("目录中没有找到XML文件！");
            return;
        }

        // ==================== 选择性导出对话框（2025-12-29新增）====================
        // 显示表选择对话框，让用户勾选要导出的表
        List<File> selectedFiles = showTableSelectionDialog(allXmlFiles);
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            resultArea.appendText("用户取消导出操作\n");
            return;
        }
        // ========================================================================

        resultArea.clear();
        resultArea.appendText("========================================\n");
        resultArea.appendText("开始批量导出 (数据库 → XML文件)\n");
        resultArea.appendText("========================================\n\n");

        final List<File> xmlFiles = selectedFiles; // 使用用户选择的文件

        new Thread(() -> {
            try {
                Platform.runLater(() -> resultArea.appendText(
                        String.format("已选择 %d 个表，开始批量导出...\n\n", xmlFiles.size())));

                int successCount = 0;
                int failedCount = 0;
                StringBuilder failedFiles = new StringBuilder();

                for (File xmlFile : xmlFiles) {
                    String tableName = xmlFile.getName().replace(".xml", "");

                    // 创建final变量供Lambda表达式使用
                    final int currentIndex = successCount + failedCount + 1;
                    final String currentTableName = tableName;
                    final int totalFiles = xmlFiles.size();

                    Platform.runLater(() -> resultArea.appendText(
                            String.format("[%d/%d] 导出: %s\n",
                                    currentIndex,
                                    totalFiles,
                                    currentTableName)));

                    try {
                        // ==================== 导出前检查（2025-12-29新增）====================
                        // 1. 检查表是否存在
                        if (!red.jiuzhou.util.DatabaseUtil.tableExists(tableName)) {
                            String errorMsg = String.format("表 %s 不存在，跳过导出", tableName);
                            log.warn(errorMsg);
                            failedCount++;
                            failedFiles.append(String.format("  ⚠️ %s.xml: %s\n", tableName, errorMsg));
                            final String finalErrorMsg = errorMsg;
                            Platform.runLater(() -> resultArea.appendText(String.format("  ⚠️ 跳过（表不存在）\n")));
                            continue;
                        }

                        // 2. 检查表数据量
                        int rowCount = red.jiuzhou.util.DatabaseUtil.getTotalRowCount(tableName);
                        if (rowCount == 0) {
                            String warnMsg = String.format("表 %s 无数据（0行）", tableName);
                            log.warn(warnMsg);
                            final String finalWarnMsg = warnMsg;
                            Platform.runLater(() -> resultArea.appendText(String.format("  ⚠️ 表为空，跳过导出\n")));
                            continue; // 空表也算成功，不计入失败
                        }

                        final int finalRowCount = rowCount;
                        Platform.runLater(() -> resultArea.appendText(
                            String.format("     数据量: %,d 行\n", finalRowCount)));
                        // ====================================================================

                        // 导出数据库数据到XML
                        String tabFilePath = stripXmlExtension(xmlFile.getAbsolutePath());
                        String mapType = deriveMapType(tableName, xmlFile);

                        String exportedFilePath;
                        if ("world".equalsIgnoreCase(tableName)) {
                            WorldDbToXmlGenerator generator = new WorldDbToXmlGenerator(tableName, mapType, tabFilePath);
                            exportedFilePath = generator.processAndMerge();
                        } else {
                            DbToXmlGenerator generator = new DbToXmlGenerator(tableName, mapType, tabFilePath);
                            exportedFilePath = generator.processAndMerge();
                        }

                        // ==================== 导出后验证（2025-12-29新增）====================
                        // 验证导出的文件是否真实存在且大小>0
                        File exportedFile = new File(exportedFilePath);
                        if (!exportedFile.exists()) {
                            throw new RuntimeException("导出文件不存在: " + exportedFilePath);
                        }
                        if (exportedFile.length() == 0) {
                            throw new RuntimeException("导出文件为空（0字节）: " + exportedFilePath);
                        }

                        // 显示文件大小
                        long fileSize = exportedFile.length();
                        String fileSizeStr;
                        if (fileSize < 1024) {
                            fileSizeStr = fileSize + " B";
                        } else if (fileSize < 1024 * 1024) {
                            fileSizeStr = String.format("%.2f KB", fileSize / 1024.0);
                        } else {
                            fileSizeStr = String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
                        }
                        // ====================================================================

                        successCount++;
                        final String finalExportedPath = exportedFilePath;
                        final String finalFileSizeStr = fileSizeStr;
                        Platform.runLater(() -> resultArea.appendText(
                            String.format("  ✅ 导出成功 → %s (%s)\n", finalExportedPath, finalFileSizeStr)));

                    } catch (Exception ex) {
                        failedCount++;
                        log.error("导出文件失败: {}", xmlFile.getName(), ex);
                        String errorDetail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                        failedFiles.append(String.format("  ❌ %s: %s\n",
                                xmlFile.getName(), errorDetail));
                        final String finalErrorDetail = errorDetail;
                        Platform.runLater(() -> resultArea.appendText(
                            String.format("  ❌ 导出失败: %s\n", finalErrorDetail)));
                    }
                }

                int finalSuccessCount = successCount;
                int finalFailedCount = failedCount;
                String finalFailedFiles = failedFiles.toString();

                Platform.runLater(() -> {
                    resultArea.appendText("\n========================================\n");
                    resultArea.appendText("批量导出完成！\n");
                    resultArea.appendText(String.format("成功: %d 个\n", finalSuccessCount));
                    resultArea.appendText(String.format("失败: %d 个\n", finalFailedCount));

                    if (finalFailedCount > 0) {
                        resultArea.appendText("\n失败文件列表:\n");
                        resultArea.appendText(finalFailedFiles);
                    }

                    resultArea.appendText("========================================\n");
                });

                log.info("批量导出完成: 成功={}, 失败={}", successCount, failedCount);

            } catch (Exception ex) {
                log.error("批量导出出错: {}", XmlUtil.getErrorMsg(ex));
                Platform.runLater(() -> resultArea.appendText(
                        "批量导出失败，请检查日志！\n" + XmlUtil.getErrorMsg(ex)));
            }
        }).start();
    }

    /**
     * 批量导入 (XML → DB)
     */
    private void batchImport() {
        String directory = directoryField.getText();
        if (directory == null || directory.trim().isEmpty()) {
            showAlert("请先选择目录！");
            return;
        }

        File dir = new File(directory);
        if (!dir.exists() || !dir.isDirectory()) {
            showAlert("选择的目录不存在！");
            return;
        }

        resultArea.clear();
        resultArea.appendText("========================================\n");
        resultArea.appendText("开始批量导入 (XML文件 → 数据库)\n");
        resultArea.appendText("========================================\n\n");

        new Thread(() -> {
            try {
                // 获取目录下所有XML文件
                List<File> xmlFiles = FileUtil.loopFiles(directory).stream()
                        .filter(file -> file.getName().endsWith(".xml"))
                        .collect(Collectors.toList());

                Platform.runLater(() -> resultArea.appendText(
                        String.format("找到 %d 个XML文件，开始批量导入...\n\n", xmlFiles.size())));

                int successCount = 0;
                int failedCount = 0;
                StringBuilder failedFiles = new StringBuilder();

                for (File xmlFile : xmlFiles) {
                    try {
                        // 创建final变量供Lambda表达式使用
                        final int currentIndex = successCount + failedCount + 1;
                        final String currentFileName = xmlFile.getName();
                        final int totalFiles = xmlFiles.size();

                        Platform.runLater(() -> resultArea.appendText(
                                String.format("[%d/%d] 导入: %s\n",
                                        currentIndex,
                                        totalFiles,
                                        currentFileName)));

                        // 解析XML并生成SQL，然后导入数据库
                        String sqlFilePath = XmlProcess.parseOneXml(xmlFile.getAbsolutePath());
                        red.jiuzhou.util.DatabaseUtil.executeSqlScript(sqlFilePath);

                        successCount++;
                        Platform.runLater(() -> resultArea.appendText("  ✅ 导入成功\n"));

                    } catch (Exception ex) {
                        failedCount++;
                        log.error("导入文件失败: {}", xmlFile.getName(), ex);
                        failedFiles.append(String.format("  ❌ %s: %s\n",
                                xmlFile.getName(), XmlUtil.getErrorMsg(ex)));
                        Platform.runLater(() -> resultArea.appendText("  ❌ 导入失败\n"));
                    }
                }

                int finalSuccessCount = successCount;
                int finalFailedCount = failedCount;
                String finalFailedFiles = failedFiles.toString();

                Platform.runLater(() -> {
                    resultArea.appendText("\n========================================\n");
                    resultArea.appendText("批量导入完成！\n");
                    resultArea.appendText(String.format("成功: %d 个\n", finalSuccessCount));
                    resultArea.appendText(String.format("失败: %d 个\n", finalFailedCount));

                    if (finalFailedCount > 0) {
                        resultArea.appendText("\n失败文件列表:\n");
                        resultArea.appendText(finalFailedFiles);
                    }

                    resultArea.appendText("========================================\n");
                });

                log.info("批量导入完成: 成功={}, 失败={}", successCount, failedCount);

            } catch (Exception ex) {
                log.error("批量导入出错: {}", XmlUtil.getErrorMsg(ex));
                Platform.runLater(() -> resultArea.appendText(
                        "批量导入失败，请检查日志！\n" + XmlUtil.getErrorMsg(ex)));
            }
        }).start();
    }

    /**
     * 显示表选择对话框（支持勾选表）
     *
     * @param allXmlFiles 所有XML文件列表
     * @return 用户选择的文件列表，如果用户取消则返回null
     */
    private List<File> showTableSelectionDialog(List<File> allXmlFiles) {
        Stage dialog = new Stage();
        dialog.setTitle("选择要导出的表");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(currentStage);

        // 创建表格数据模型
        class TableItem {
            private final javafx.beans.property.BooleanProperty selected = new javafx.beans.property.SimpleBooleanProperty(true);
            private final String tableName;
            private final File file;
            private String status;
            private int rowCount;

            public TableItem(File file) {
                this.file = file;
                this.tableName = file.getName().replace(".xml", "");

                // 检查表是否存在并获取数据量
                try {
                    if (red.jiuzhou.util.DatabaseUtil.tableExists(tableName)) {
                        this.rowCount = red.jiuzhou.util.DatabaseUtil.getTotalRowCount(tableName);
                        if (rowCount == 0) {
                            this.status = "⚠️ 空表";
                            this.selected.set(false); // 空表默认不选
                        } else {
                            this.status = "✅ 就绪";
                        }
                    } else {
                        this.status = "❌ 不存在";
                        this.selected.set(false); // 表不存在默认不选
                        this.rowCount = 0;
                    }
                } catch (Exception e) {
                    this.status = "⚠️ 错误";
                    this.selected.set(false);
                    this.rowCount = 0;
                }
            }

            public javafx.beans.property.BooleanProperty selectedProperty() { return selected; }
            public boolean isSelected() { return selected.get(); }
            public void setSelected(boolean value) { selected.set(value); }
            public String getTableName() { return tableName; }
            public String getStatus() { return status; }
            public int getRowCount() { return rowCount; }
            public String getRowCountStr() { return String.format("%,d", rowCount); }
            public File getFile() { return file; }
        }

        // 创建表格
        javafx.scene.control.TableView<TableItem> tableView = new javafx.scene.control.TableView<>();
        javafx.collections.ObservableList<TableItem> items = javafx.collections.FXCollections.observableArrayList();

        // 加载数据
        for (File file : allXmlFiles) {
            items.add(new TableItem(file));
        }
        tableView.setItems(items);

        // 创建列
        // 1. 选择列（CheckBox）
        javafx.scene.control.TableColumn<TableItem, Boolean> selectCol = new javafx.scene.control.TableColumn<>("选择");
        selectCol.setPrefWidth(60);
        selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectCol.setCellFactory(javafx.scene.control.cell.CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setEditable(true);

        // 2. 表名列
        javafx.scene.control.TableColumn<TableItem, String> nameCol = new javafx.scene.control.TableColumn<>("表名");
        nameCol.setPrefWidth(300);
        nameCol.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getTableName()));

        // 3. 状态列
        javafx.scene.control.TableColumn<TableItem, String> statusCol = new javafx.scene.control.TableColumn<>("状态");
        statusCol.setPrefWidth(100);
        statusCol.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getStatus()));

        // 4. 数据量列
        javafx.scene.control.TableColumn<TableItem, String> rowCountCol = new javafx.scene.control.TableColumn<>("数据量");
        rowCountCol.setPrefWidth(120);
        rowCountCol.setCellValueFactory(cellData ->
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getRowCountStr()));

        tableView.getColumns().addAll(selectCol, nameCol, statusCol, rowCountCol);
        tableView.setEditable(true);

        // 统计信息标签
        javafx.scene.control.Label statsLabel = new javafx.scene.control.Label();
        updateStatsLabel(statsLabel, items);

        // 按钮区域
        javafx.scene.control.Button selectAllBtn = new javafx.scene.control.Button("✓ 全选");
        selectAllBtn.setOnAction(e -> {
            items.forEach(item -> item.setSelected(true));
            updateStatsLabel(statsLabel, items);
        });

        javafx.scene.control.Button deselectAllBtn = new javafx.scene.control.Button("✗ 全不选");
        deselectAllBtn.setOnAction(e -> {
            items.forEach(item -> item.setSelected(false));
            updateStatsLabel(statsLabel, items);
        });

        javafx.scene.control.Button invertBtn = new javafx.scene.control.Button("⇄ 反选");
        invertBtn.setOnAction(e -> {
            items.forEach(item -> item.setSelected(!item.isSelected()));
            updateStatsLabel(statsLabel, items);
        });

        javafx.scene.control.Button selectReadyBtn = new javafx.scene.control.Button("✅ 仅选就绪");
        selectReadyBtn.setOnAction(e -> {
            items.forEach(item -> item.setSelected(item.getStatus().equals("✅ 就绪")));
            updateStatsLabel(statsLabel, items);
        });

        javafx.scene.layout.HBox quickSelectBox = new javafx.scene.layout.HBox(10, selectAllBtn, deselectAllBtn, invertBtn, selectReadyBtn);
        quickSelectBox.setAlignment(Pos.CENTER_LEFT);
        quickSelectBox.setPadding(new Insets(10));

        // 确认和取消按钮
        javafx.scene.control.Button confirmBtn = new javafx.scene.control.Button("确定导出");
        confirmBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");
        confirmBtn.setPrefWidth(120);

        javafx.scene.control.Button cancelBtn = new javafx.scene.control.Button("取消");
        cancelBtn.setPrefWidth(120);

        javafx.scene.layout.HBox actionBox = new javafx.scene.layout.HBox(15, confirmBtn, cancelBtn);
        actionBox.setAlignment(Pos.CENTER);
        actionBox.setPadding(new Insets(10));

        // 布局
        javafx.scene.layout.VBox layout = new javafx.scene.layout.VBox(10);
        layout.getChildren().addAll(
            new javafx.scene.control.Label("📋 请勾选要导出的表（默认已选择所有有数据的表）："),
            tableView,
            statsLabel,
            quickSelectBox,
            actionBox
        );
        layout.setPadding(new Insets(15));
        javafx.scene.layout.VBox.setVgrow(tableView, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.Scene scene = new javafx.scene.Scene(layout, 700, 600);
        dialog.setScene(scene);

        // 结果存储
        final List<File>[] result = new List[]{null};

        // 确认按钮事件
        confirmBtn.setOnAction(e -> {
            List<File> selectedFiles = items.stream()
                .filter(TableItem::isSelected)
                .map(TableItem::getFile)
                .collect(Collectors.toList());

            if (selectedFiles.isEmpty()) {
                showAlert("请至少选择一个表！");
                return;
            }

            result[0] = selectedFiles;
            dialog.close();
        });

        // 取消按钮事件
        cancelBtn.setOnAction(e -> dialog.close());

        // 监听选择变化，更新统计信息
        items.forEach(item -> item.selectedProperty().addListener((obs, oldVal, newVal) -> {
            updateStatsLabel(statsLabel, items);
        }));

        dialog.showAndWait();
        return result[0];
    }

    /**
     * 更新统计信息标签
     */
    private void updateStatsLabel(javafx.scene.control.Label label, javafx.collections.ObservableList items) {
        long selectedCount = items.stream().filter(item -> {
            try {
                java.lang.reflect.Method m = item.getClass().getMethod("isSelected");
                return (Boolean) m.invoke(item);
            } catch (Exception e) {
                return false;
            }
        }).count();

        long totalDataRows = items.stream().mapToLong(item -> {
            try {
                java.lang.reflect.Method m = item.getClass().getMethod("getRowCount");
                return ((Integer) m.invoke(item)).longValue();
            } catch (Exception e) {
                return 0L;
            }
        }).sum();

        label.setText(String.format("📊 总计：%d 个表 | 已选：%d 个 | 预计导出：%,d 行数据",
            items.size(), selectedCount, totalDataRows));
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
    }

    /**
     * 显示警告对话框
     */
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("警告");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 移除文件路径中的.xml扩展名
     */
    private String stripXmlExtension(String filePath) {
        if (filePath == null) {
            return null;
        }
        return filePath.toLowerCase().endsWith(".xml")
                ? filePath.substring(0, filePath.length() - 4)
                : filePath;
    }

    /**
     * 推导mapType（仅对world表有效）
     */
    private String deriveMapType(String tabName, File xmlFile) {
        if (tabName == null || xmlFile == null) {
            return null;
        }
        if (!"world".equalsIgnoreCase(tabName)) {
            return null;
        }
        File parent = xmlFile.getParentFile();
        return parent != null ? parent.getName() : null;
    }
}
