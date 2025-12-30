package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.util.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 编码元数据诊断面板
 *
 * 功能：
 * - 编码分布统计
 * - 往返验证结果查看
 * - 缓存状态监控
 * - 批量迁移工具
 * - 元数据管理
 *
 * @author Claude
 * @date 2025-12-29
 */
public class EncodingDiagnosticStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(EncodingDiagnosticStage.class);

    private final TextArea statisticsArea;
    private final TableView<MetadataRecord> metadataTable;
    private final TextArea cacheInfoArea;
    private final Label statusLabel;

    public EncodingDiagnosticStage() {
        setTitle("编码元数据诊断中心");
        setWidth(1000);
        setHeight(700);

        // 主布局
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // ========== 顶部工具栏 ==========
        HBox toolbar = createToolbar();
        root.setTop(toolbar);

        // ========== 中间内容区（Tab页）==========
        TabPane tabPane = new TabPane();

        // Tab 1: 统计信息
        statisticsArea = new TextArea();
        statisticsArea.setEditable(false);
        statisticsArea.setStyle("-fx-font-family: 'Consolas', monospace;");
        Tab statisticsTab = new Tab("编码统计", new VBox(5, statisticsArea));
        statisticsTab.setClosable(false);

        // Tab 2: 元数据列表
        metadataTable = createMetadataTable();
        VBox tableBox = new VBox(5, metadataTable);
        VBox.setVgrow(metadataTable, Priority.ALWAYS);
        Tab metadataTab = new Tab("元数据列表", tableBox);
        metadataTab.setClosable(false);

        // Tab 3: 缓存状态
        cacheInfoArea = new TextArea();
        cacheInfoArea.setEditable(false);
        cacheInfoArea.setStyle("-fx-font-family: 'Consolas', monospace;");
        Tab cacheTab = new Tab("缓存状态", new VBox(5, cacheInfoArea));
        cacheTab.setClosable(false);

        tabPane.getTabs().addAll(statisticsTab, metadataTab, cacheTab);
        root.setCenter(tabPane);

        // ========== 底部状态栏 ==========
        statusLabel = new Label("就绪");
        statusLabel.setStyle("-fx-padding: 5; -fx-background-color: #f0f0f0;");
        root.setBottom(statusLabel);

        // 设置场景
        Scene scene = new Scene(root);
        setScene(scene);

        // 初始加载数据
        refreshAll();
    }

    /**
     * 创建工具栏
     */
    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(5));
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button refreshBtn = new Button("🔄 刷新");
        refreshBtn.setOnAction(e -> refreshAll());

        Button migrateBtn = new Button("📦 批量迁移");
        migrateBtn.setOnAction(e -> runMigration());

        Button validateBtn = new Button("✅ 批量验证");
        validateBtn.setOnAction(e -> runBatchValidation());

        Button clearCacheBtn = new Button("🗑️ 清空缓存");
        clearCacheBtn.setOnAction(e -> clearCache());

        Button cleanupBtn = new Button("🧹 清理无效");
        cleanupBtn.setOnAction(e -> cleanupInvalid());

        Button redetectBtn = new Button("🔍 重新检测");
        redetectBtn.setOnAction(e -> redetectEncodings());

        toolbar.getChildren().addAll(
                refreshBtn,
                new Separator(),
                migrateBtn,
                validateBtn,
                new Separator(),
                clearCacheBtn,
                cleanupBtn,
                redetectBtn
        );

        return toolbar;
    }

    /**
     * 创建元数据表格
     */
    private TableView<MetadataRecord> createMetadataTable() {
        TableView<MetadataRecord> table = new TableView<>();

        TableColumn<MetadataRecord, String> tableNameCol = new TableColumn<>("表名");
        tableNameCol.setCellValueFactory(new PropertyValueFactory<>("tableName"));
        tableNameCol.setPrefWidth(150);

        TableColumn<MetadataRecord, String> mapTypeCol = new TableColumn<>("MapType");
        mapTypeCol.setCellValueFactory(new PropertyValueFactory<>("mapType"));
        mapTypeCol.setPrefWidth(80);

        TableColumn<MetadataRecord, String> encodingCol = new TableColumn<>("编码");
        encodingCol.setCellValueFactory(new PropertyValueFactory<>("encoding"));
        encodingCol.setPrefWidth(100);

        TableColumn<MetadataRecord, String> bomCol = new TableColumn<>("BOM");
        bomCol.setCellValueFactory(new PropertyValueFactory<>("hasBom"));
        bomCol.setPrefWidth(60);

        TableColumn<MetadataRecord, String> hashCol = new TableColumn<>("MD5哈希");
        hashCol.setCellValueFactory(new PropertyValueFactory<>("fileHash"));
        hashCol.setPrefWidth(150);

        TableColumn<MetadataRecord, String> validationCol = new TableColumn<>("验证结果");
        validationCol.setCellValueFactory(new PropertyValueFactory<>("validationResult"));
        validationCol.setPrefWidth(80);

        TableColumn<MetadataRecord, Integer> importCountCol = new TableColumn<>("导入次数");
        importCountCol.setCellValueFactory(new PropertyValueFactory<>("importCount"));
        importCountCol.setPrefWidth(80);

        TableColumn<MetadataRecord, Integer> exportCountCol = new TableColumn<>("导出次数");
        exportCountCol.setCellValueFactory(new PropertyValueFactory<>("exportCount"));
        exportCountCol.setPrefWidth(80);

        TableColumn<MetadataRecord, String> lastImportCol = new TableColumn<>("最后导入");
        lastImportCol.setCellValueFactory(new PropertyValueFactory<>("lastImport"));
        lastImportCol.setPrefWidth(150);

        table.getColumns().addAll(
                tableNameCol, mapTypeCol, encodingCol, bomCol,
                hashCol, validationCol,
                importCountCol, exportCountCol, lastImportCol
        );

        return table;
    }

    /**
     * 刷新所有数据
     */
    private void refreshAll() {
        updateStatus("正在刷新数据...");

        new Thread(() -> {
            try {
                // 加载统计信息
                String stats = loadStatistics();
                Platform.runLater(() -> statisticsArea.setText(stats));

                // 加载元数据列表
                List<MetadataRecord> records = loadMetadataRecords();
                Platform.runLater(() -> metadataTable.getItems().setAll(records));

                // 加载缓存信息
                String cacheInfo = EncodingMetadataCache.getCacheDetails();
                Platform.runLater(() -> cacheInfoArea.setText(cacheInfo));

                Platform.runLater(() -> updateStatus("刷新完成 - " + getCurrentTime()));

            } catch (Exception e) {
                log.error("刷新数据失败", e);
                Platform.runLater(() -> updateStatus("刷新失败: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 加载统计信息
     */
    private String loadStatistics() {
        StringBuilder sb = new StringBuilder();

        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();

            // 编码分布统计
            sb.append("=== 编码分布统计 ===\n\n");
            String encodingSql = """
                SELECT original_encoding, has_bom, COUNT(*) as count
                FROM file_encoding_metadata
                GROUP BY original_encoding, has_bom
                ORDER BY count DESC
                """;

            jdbcTemplate.queryForList(encodingSql).forEach(row -> {
                String encoding = (String) row.get("original_encoding");
                Boolean hasBOM = (Boolean) row.get("has_bom");
                int count = ((Number) row.get("count")).intValue();
                sb.append(String.format("  %s%s: %d 个文件\n",
                        encoding,
                        hasBOM ? " (BOM)" : "",
                        count));
            });

            // 验证结果统计
            sb.append("\n=== 往返验证统计 ===\n\n");
            String validationSql = """
                SELECT
                    COUNT(*) as total,
                    SUM(CASE WHEN last_validation_result = TRUE THEN 1 ELSE 0 END) as passed,
                    SUM(CASE WHEN last_validation_result = FALSE THEN 1 ELSE 0 END) as failed,
                    SUM(CASE WHEN last_validation_result IS NULL THEN 1 ELSE 0 END) as not_validated
                FROM file_encoding_metadata
                """;

            Map<String, Object> validationStats = jdbcTemplate.queryForMap(validationSql);
            int total = ((Number) validationStats.get("total")).intValue();
            int passed = ((Number) validationStats.get("passed")).intValue();
            int failed = ((Number) validationStats.get("failed")).intValue();
            int notValidated = ((Number) validationStats.get("not_validated")).intValue();

            sb.append(String.format("  总计: %d\n", total));
            sb.append(String.format("  ✅ 通过: %d (%.1f%%)\n", passed, total > 0 ? passed * 100.0 / total : 0));
            sb.append(String.format("  ❌ 失败: %d (%.1f%%)\n", failed, total > 0 ? failed * 100.0 / total : 0));
            sb.append(String.format("  ⚪ 未验证: %d (%.1f%%)\n", notValidated, total > 0 ? notValidated * 100.0 / total : 0));

            // 导入导出统计
            sb.append("\n=== 导入导出统计 ===\n\n");
            String ioSql = """
                SELECT
                    SUM(import_count) as total_imports,
                    SUM(export_count) as total_exports,
                    AVG(import_count) as avg_imports,
                    AVG(export_count) as avg_exports
                FROM file_encoding_metadata
                """;

            Map<String, Object> ioStats = jdbcTemplate.queryForMap(ioSql);
            int totalImports = ((Number) ioStats.get("total_imports")).intValue();
            int totalExports = ((Number) ioStats.get("total_exports")).intValue();
            double avgImports = ((Number) ioStats.get("avg_imports")).doubleValue();
            double avgExports = ((Number) ioStats.get("avg_exports")).doubleValue();

            sb.append(String.format("  总导入次数: %d\n", totalImports));
            sb.append(String.format("  总导出次数: %d\n", totalExports));
            sb.append(String.format("  平均导入次数: %.2f\n", avgImports));
            sb.append(String.format("  平均导出次数: %.2f\n", avgExports));

            // 缓存统计
            sb.append("\n=== 缓存统计 ===\n\n");
            sb.append("  ").append(EncodingMetadataCache.getStatistics()).append("\n");

        } catch (Exception e) {
            sb.append("\n❌ 加载统计信息失败: ").append(e.getMessage()).append("\n");
        }

        return sb.toString();
    }

    /**
     * 加载元数据记录
     */
    private List<MetadataRecord> loadMetadataRecords() {
        try {
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            String sql = """
                SELECT table_name, map_type, original_encoding, has_bom,
                       original_file_hash, last_validation_result,
                       import_count, export_count, last_import_time
                FROM file_encoding_metadata
                ORDER BY last_import_time DESC
                """;

            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                MetadataRecord record = new MetadataRecord();
                record.setTableName(rs.getString("table_name"));
                record.setMapType(rs.getString("map_type"));
                record.setEncoding(rs.getString("original_encoding"));
                record.setHasBom(rs.getBoolean("has_bom") ? "是" : "否");
                record.setFileHash(rs.getString("original_file_hash"));

                Boolean validation = (Boolean) rs.getObject("last_validation_result");
                record.setValidationResult(
                        validation == null ? "未验证" : (validation ? "✅ 通过" : "❌ 失败")
                );

                record.setImportCount(rs.getInt("import_count"));
                record.setExportCount(rs.getInt("export_count"));

                Object lastImport = rs.getObject("last_import_time");
                record.setLastImport(lastImport != null ? lastImport.toString() : "N/A");

                return record;
            });

        } catch (Exception e) {
            log.error("加载元数据记录失败", e);
            return List.of();
        }
    }

    /**
     * 执行批量迁移
     */
    private void runMigration() {
        updateStatus("正在执行批量迁移...");

        new Thread(() -> {
            try {
                String tabFilePath = YamlUtils.getProperty("file.tabFildPath");
                EncodingMetadataMigrationTool.MigrationResult result =
                        EncodingMetadataMigrationTool.migrateAllTables(tabFilePath);

                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("迁移完成");
                    alert.setHeaderText("批量迁移结果");
                    alert.setContentText(result.getSummary());
                    alert.showAndWait();

                    refreshAll();
                });

            } catch (Exception e) {
                log.error("批量迁移失败", e);
                Platform.runLater(() -> updateStatus("迁移失败: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 执行批量验证
     */
    private void runBatchValidation() {
        updateStatus("正在执行批量验证...");

        new Thread(() -> {
            try {
                String report = RoundTripValidator.validateAllTables();

                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("验证完成");
                    alert.setHeaderText("批量验证结果");
                    alert.setContentText(report);
                    alert.showAndWait();

                    refreshAll();
                });

            } catch (Exception e) {
                log.error("批量验证失败", e);
                Platform.runLater(() -> updateStatus("验证失败: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 清空缓存
     */
    private void clearCache() {
        EncodingMetadataCache.clearAll();
        updateStatus("缓存已清空");
        refreshAll();
    }

    /**
     * 清理无效元数据
     */
    private void cleanupInvalid() {
        updateStatus("正在清理无效元数据...");

        new Thread(() -> {
            int deleted = EncodingMetadataMigrationTool.cleanupInvalidMetadata();

            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("清理完成");
                alert.setContentText("已删除 " + deleted + " 条无效元数据");
                alert.showAndWait();

                refreshAll();
            });
        }).start();
    }

    /**
     * 重新检测编码
     */
    private void redetectEncodings() {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("确认操作");
        confirmAlert.setHeaderText("重新检测所有编码");
        confirmAlert.setContentText("这将重新检测所有默认 UTF-16 编码的表。\n确定要继续吗？");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                updateStatus("正在重新检测编码...");

                new Thread(() -> {
                    try {
                        String tabFilePath = YamlUtils.getProperty("file.tabFildPath");
                        int redetected = EncodingMetadataMigrationTool.redetectAllEncodings(tabFilePath, false);

                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                            alert.setTitle("重新检测完成");
                            alert.setContentText("已更新 " + redetected + " 个表的编码");
                            alert.showAndWait();

                            refreshAll();
                        });

                    } catch (Exception e) {
                        log.error("重新检测失败", e);
                        Platform.runLater(() -> updateStatus("重新检测失败: " + e.getMessage()));
                    }
                }).start();
            }
        });
    }

    /**
     * 更新状态栏
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    /**
     * 获取当前时间
     */
    private String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 元数据记录（JavaFX Bean）
     */
    public static class MetadataRecord {
        private String tableName;
        private String mapType;
        private String encoding;
        private String hasBom;
        private String fileHash;
        private String validationResult;
        private int importCount;
        private int exportCount;
        private String lastImport;

        // Getters and Setters
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public String getMapType() { return mapType; }
        public void setMapType(String mapType) { this.mapType = mapType; }

        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }

        public String getHasBom() { return hasBom; }
        public void setHasBom(String hasBom) { this.hasBom = hasBom; }

        public String getFileHash() { return fileHash; }
        public void setFileHash(String fileHash) { this.fileHash = fileHash; }

        public String getValidationResult() { return validationResult; }
        public void setValidationResult(String validationResult) { this.validationResult = validationResult; }

        public int getImportCount() { return importCount; }
        public void setImportCount(int importCount) { this.importCount = importCount; }

        public int getExportCount() { return exportCount; }
        public void setExportCount(int exportCount) { this.exportCount = exportCount; }

        public String getLastImport() { return lastImport; }
        public void setLastImport(String lastImport) { this.lastImport = lastImport; }
    }
}
