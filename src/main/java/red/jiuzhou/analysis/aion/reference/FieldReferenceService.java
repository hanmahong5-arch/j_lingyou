package red.jiuzhou.analysis.aion.reference;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.util.DatabaseUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 字段引用服务
 *
 * <p>提供字段引用的分析、缓存和持久化功能。
 *
 * <p>缓存策略（双层）：
 * <ul>
 *   <li>JSON文件缓存 - 快速本地访问</li>
 *   <li>数据库持久化 - 跨会话共享</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class FieldReferenceService {

    private static final Logger log = LoggerFactory.getLogger(FieldReferenceService.class);

    /** JSON缓存文件名 */
    private static final String CACHE_FILE_NAME = "field-references.json";

    /** 缓存目录名 */
    private static final String CACHE_DIR_NAME = "analysis";

    private final FieldReferenceAnalyzer analyzer;
    private final ReferenceValidationService validationService;

    // 内存缓存
    private FieldReferenceResult cachedResult;
    private long cacheTimestamp;
    private static final long CACHE_TTL_MS = 30 * 60 * 1000;  // 30分钟

    public FieldReferenceService() {
        this.analyzer = new FieldReferenceAnalyzer();
        this.validationService = new ReferenceValidationService();
    }

    // ========== 核心分析方法 ==========

    /**
     * 分析目录并缓存结果
     *
     * @param xmlDirectory XML文件目录
     * @param forceRefresh 是否强制刷新（忽略缓存）
     * @return 分析结果
     */
    public FieldReferenceResult analyze(File xmlDirectory, boolean forceRefresh) {
        // 检查内存缓存
        if (!forceRefresh && cachedResult != null) {
            long now = System.currentTimeMillis();
            if (now - cacheTimestamp < CACHE_TTL_MS) {
                log.debug("使用内存缓存的分析结果");
                return cachedResult;
            }
        }

        // 检查JSON文件缓存
        if (!forceRefresh) {
            FieldReferenceResult cached = loadFromJsonCache(xmlDirectory);
            if (cached != null) {
                log.info("从JSON缓存加载分析结果: {}个条目", cached.getTotalEntries());
                this.cachedResult = cached;
                this.cacheTimestamp = System.currentTimeMillis();
                return cached;
            }
        }

        // 执行分析
        log.info("开始分析目录: {}", xmlDirectory.getAbsolutePath());
        FieldReferenceResult result = analyzer.analyzeDirectory(xmlDirectory);

        // 验证引用有效性
        validationService.validateReferences(result);

        // 保存到JSON缓存
        saveToJsonCache(xmlDirectory, result);

        // 保存到数据库
        persistToDatabase(result);

        // 更新内存缓存
        this.cachedResult = result;
        this.cacheTimestamp = System.currentTimeMillis();

        return result;
    }

    /**
     * 仅从缓存加载（不执行分析）
     *
     * @param xmlDirectory XML文件目录
     * @return 缓存的结果，如果没有缓存返回null
     */
    public FieldReferenceResult loadFromCache(File xmlDirectory) {
        // 先检查内存缓存
        if (cachedResult != null) {
            return cachedResult;
        }

        // 检查JSON缓存
        FieldReferenceResult cached = loadFromJsonCache(xmlDirectory);
        if (cached != null) {
            this.cachedResult = cached;
            this.cacheTimestamp = System.currentTimeMillis();
            return cached;
        }

        // 尝试从数据库加载
        return loadFromDatabase();
    }

    /**
     * 清除所有缓存
     */
    public void clearCache(File xmlDirectory) {
        this.cachedResult = null;
        this.cacheTimestamp = 0;

        // 删除JSON缓存文件
        File cacheFile = getCacheFile(xmlDirectory);
        if (cacheFile.exists()) {
            cacheFile.delete();
            log.info("已删除JSON缓存: {}", cacheFile.getAbsolutePath());
        }
    }

    // ========== JSON缓存操作 ==========

    private File getCacheFile(File xmlDirectory) {
        File analysisDir = new File(xmlDirectory, CACHE_DIR_NAME);
        if (!analysisDir.exists()) {
            analysisDir.mkdirs();
        }
        return new File(analysisDir, CACHE_FILE_NAME);
    }

    private void saveToJsonCache(File xmlDirectory, FieldReferenceResult result) {
        File cacheFile = getCacheFile(xmlDirectory);

        try {
            // 构建可序列化的数据结构
            Map<String, Object> cacheData = new LinkedHashMap<>();
            cacheData.put("analysisTime", result.getAnalysisTime().toString());
            cacheData.put("analysisDuration", result.getAnalysisDuration());
            cacheData.put("analyzedFileCount", result.getAnalyzedFileCount());
            cacheData.put("entries", result.getEntries());

            String json = JSON.toJSONString(cacheData,
                    SerializerFeature.PrettyFormat,
                    SerializerFeature.WriteMapNullValue);

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(cacheFile), StandardCharsets.UTF_8)) {
                writer.write(json);
            }

            log.info("已保存JSON缓存: {} ({}个条目)", cacheFile.getName(), result.getTotalEntries());

        } catch (Exception e) {
            log.warn("保存JSON缓存失败: {}", e.getMessage());
        }
    }

    private FieldReferenceResult loadFromJsonCache(File xmlDirectory) {
        File cacheFile = getCacheFile(xmlDirectory);

        if (!cacheFile.exists()) {
            return null;
        }

        try {
            StringBuilder content = new StringBuilder();
            try (Reader reader = new InputStreamReader(new FileInputStream(cacheFile), StandardCharsets.UTF_8)) {
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    content.append(buffer, 0, read);
                }
            }

            Map<String, Object> cacheData = JSON.parseObject(content.toString(), Map.class);

            FieldReferenceResult result = new FieldReferenceResult();
            result.setAnalysisTime(LocalDateTime.parse((String) cacheData.get("analysisTime")));
            result.setAnalysisDuration(((Number) cacheData.get("analysisDuration")).longValue());
            result.setAnalyzedFileCount(((Number) cacheData.get("analyzedFileCount")).intValue());

            // 解析条目
            List<Object> entriesData = (List<Object>) cacheData.get("entries");
            if (entriesData != null) {
                for (Object obj : entriesData) {
                    FieldReferenceEntry entry = JSON.parseObject(JSON.toJSONString(obj), FieldReferenceEntry.class);
                    result.addEntry(entry);
                }
            }

            return result;

        } catch (Exception e) {
            log.warn("加载JSON缓存失败: {}", e.getMessage());
            return null;
        }
    }

    // ========== 数据库操作 ==========

    /**
     * 持久化到数据库
     */
    public void persistToDatabase(FieldReferenceResult result) {
        try {
            JdbcTemplate jdbc = DatabaseUtil.getJdbcTemplate();

            // 确保表存在
            ensureTableExists(jdbc);

            // 清空旧数据
            jdbc.update("DELETE FROM field_references");

            // 批量插入
            String sql = "INSERT INTO field_references (" +
                    "source_file, source_file_name, source_field, source_field_path, source_mechanism, " +
                    "target_system, target_table, " +
                    "reference_count, distinct_values, valid_references, invalid_references, " +
                    "sample_values, sample_names, confidence, last_analysis_time" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

            List<Object[]> batchArgs = new ArrayList<>();
            for (FieldReferenceEntry entry : result.getEntries()) {
                batchArgs.add(new Object[]{
                        truncate(entry.getSourceFile(), 512),
                        truncate(entry.getSourceFileName(), 128),
                        truncate(entry.getSourceField(), 128),
                        truncate(entry.getSourceFieldPath(), 512),
                        truncate(entry.getSourceMechanism(), 64),
                        truncate(entry.getTargetSystem(), 64),
                        truncate(entry.getTargetTable(), 128),
                        entry.getReferenceCount(),
                        entry.getDistinctValues(),
                        entry.getValidReferences(),
                        entry.getInvalidReferences(),
                        entry.getSampleValuesJson(),
                        entry.getSampleNamesJson(),
                        entry.getConfidence()
                });
            }

            jdbc.batchUpdate(sql, batchArgs);
            log.info("已持久化到数据库: {}个条目", batchArgs.size());

        } catch (Exception e) {
            log.warn("持久化到数据库失败: {}", e.getMessage());
        }
    }

    /**
     * 从数据库加载
     */
    public FieldReferenceResult loadFromDatabase() {
        try {
            JdbcTemplate jdbc = DatabaseUtil.getJdbcTemplate();

            // 检查表是否存在
            if (!DatabaseUtil.tableExists("field_references")) {
                return null;
            }

            String sql = "SELECT * FROM field_references ORDER BY reference_count DESC";
            List<Map<String, Object>> rows = jdbc.queryForList(sql);

            if (rows.isEmpty()) {
                return null;
            }

            FieldReferenceResult result = new FieldReferenceResult();

            for (Map<String, Object> row : rows) {
                FieldReferenceEntry entry = new FieldReferenceEntry();
                entry.setId(((Number) row.get("id")).longValue());
                entry.setSourceFile((String) row.get("source_file"));
                entry.setSourceFileName((String) row.get("source_file_name"));
                entry.setSourceField((String) row.get("source_field"));
                entry.setSourceFieldPath((String) row.get("source_field_path"));
                entry.setSourceMechanism((String) row.get("source_mechanism"));
                entry.setTargetSystem((String) row.get("target_system"));
                entry.setTargetTable((String) row.get("target_table"));
                entry.setReferenceCount(((Number) row.get("reference_count")).intValue());
                entry.setDistinctValues(((Number) row.get("distinct_values")).intValue());
                entry.setValidReferences(((Number) row.get("valid_references")).intValue());
                entry.setInvalidReferences(((Number) row.get("invalid_references")).intValue());
                entry.setSampleValuesFromJson((String) row.get("sample_values"));
                entry.setSampleNamesFromJson((String) row.get("sample_names"));

                Number confidence = (Number) row.get("confidence");
                if (confidence != null) {
                    entry.setConfidence(confidence.doubleValue());
                }

                result.addEntry(entry);
            }

            log.info("从数据库加载: {}个条目", result.getTotalEntries());
            return result;

        } catch (Exception e) {
            log.warn("从数据库加载失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 确保数据库表存在 (PostgreSQL)
     */
    private void ensureTableExists(JdbcTemplate jdbc) {
        try {
            if (!DatabaseUtil.tableExists("field_references")) {
                // PostgreSQL: 使用 BIGSERIAL，移除 ENGINE 和 CHARSET
                String createSql = "CREATE TABLE IF NOT EXISTS field_references (" +
                        "id BIGSERIAL PRIMARY KEY, " +
                        "source_file VARCHAR(512) NOT NULL, " +
                        "source_file_name VARCHAR(128) NOT NULL, " +
                        "source_field VARCHAR(128) NOT NULL, " +
                        "source_field_path VARCHAR(512) NOT NULL, " +
                        "source_mechanism VARCHAR(64), " +
                        "target_system VARCHAR(64) NOT NULL, " +
                        "target_table VARCHAR(128) NOT NULL, " +
                        "reference_count INT DEFAULT 0, " +
                        "distinct_values INT DEFAULT 0, " +
                        "valid_references INT DEFAULT 0, " +
                        "invalid_references INT DEFAULT 0, " +
                        "sample_values TEXT, " +
                        "sample_names TEXT, " +
                        "confidence DECIMAL(4,3) DEFAULT 1.000, " +
                        "last_analysis_time TIMESTAMP, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")";

                jdbc.execute(createSql);

                // PostgreSQL: 使用 MD5 哈希创建唯一约束（绕过长度限制）
                jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_field_ref ON field_references " +
                        "(md5(source_file), md5(source_field_path), target_system)");

                // PostgreSQL: 创建触发器函数用于自动更新 updated_at
                jdbc.execute("""
                    CREATE OR REPLACE FUNCTION update_field_references_updated_at()
                    RETURNS TRIGGER AS $$
                    BEGIN
                        NEW.updated_at = CURRENT_TIMESTAMP;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);

                // PostgreSQL: 创建触发器
                jdbc.execute("""
                    DROP TRIGGER IF EXISTS trigger_update_field_references_updated_at ON field_references
                    """);
                jdbc.execute("""
                    CREATE TRIGGER trigger_update_field_references_updated_at
                    BEFORE UPDATE ON field_references
                    FOR EACH ROW EXECUTE FUNCTION update_field_references_updated_at()
                    """);

                log.info("已创建 field_references 表");
            }
        } catch (Exception e) {
            log.warn("创建表失败: {}", e.getMessage());
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }

    // ========== 查询方法 ==========

    /**
     * 按目标系统查询
     */
    public List<FieldReferenceEntry> queryByTargetSystem(String targetSystem) {
        if (cachedResult != null) {
            return cachedResult.filterByTargetSystem(targetSystem);
        }

        try {
            JdbcTemplate jdbc = DatabaseUtil.getJdbcTemplate();
            String sql = "SELECT * FROM field_references WHERE target_system = ? ORDER BY reference_count DESC";
            List<Map<String, Object>> rows = jdbc.queryForList(sql, targetSystem);
            return mapToEntries(rows);
        } catch (Exception e) {
            log.warn("查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 按源机制查询
     */
    public List<FieldReferenceEntry> queryByMechanism(String mechanism) {
        if (cachedResult != null) {
            return cachedResult.filterByMechanism(mechanism);
        }

        try {
            JdbcTemplate jdbc = DatabaseUtil.getJdbcTemplate();
            String sql = "SELECT * FROM field_references WHERE source_mechanism = ? ORDER BY reference_count DESC";
            List<Map<String, Object>> rows = jdbc.queryForList(sql, mechanism);
            return mapToEntries(rows);
        } catch (Exception e) {
            log.warn("查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询有无效引用的条目
     */
    public List<FieldReferenceEntry> queryInvalidReferences() {
        if (cachedResult != null) {
            return cachedResult.getInvalidEntries();
        }

        try {
            JdbcTemplate jdbc = DatabaseUtil.getJdbcTemplate();
            String sql = "SELECT * FROM field_references WHERE invalid_references > 0 ORDER BY invalid_references DESC";
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            return mapToEntries(rows);
        } catch (Exception e) {
            log.warn("查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<FieldReferenceEntry> mapToEntries(List<Map<String, Object>> rows) {
        List<FieldReferenceEntry> entries = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            FieldReferenceEntry entry = new FieldReferenceEntry();
            entry.setId(((Number) row.get("id")).longValue());
            entry.setSourceFile((String) row.get("source_file"));
            entry.setSourceFileName((String) row.get("source_file_name"));
            entry.setSourceField((String) row.get("source_field"));
            entry.setSourceFieldPath((String) row.get("source_field_path"));
            entry.setSourceMechanism((String) row.get("source_mechanism"));
            entry.setTargetSystem((String) row.get("target_system"));
            entry.setTargetTable((String) row.get("target_table"));
            entry.setReferenceCount(((Number) row.get("reference_count")).intValue());
            entry.setDistinctValues(((Number) row.get("distinct_values")).intValue());
            entry.setValidReferences(((Number) row.get("valid_references")).intValue());
            entry.setInvalidReferences(((Number) row.get("invalid_references")).intValue());
            entry.setSampleValuesFromJson((String) row.get("sample_values"));
            entry.setSampleNamesFromJson((String) row.get("sample_names"));
            entries.add(entry);
        }
        return entries;
    }
}
