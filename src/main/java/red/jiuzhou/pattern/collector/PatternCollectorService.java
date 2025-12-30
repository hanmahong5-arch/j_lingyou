package red.jiuzhou.pattern.collector;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.analysis.aion.AionMechanismCategory;
import red.jiuzhou.analysis.aion.AionMechanismDetector;
import red.jiuzhou.analysis.aion.DetectionResult;
import red.jiuzhou.pattern.dao.*;
import red.jiuzhou.pattern.model.*;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模式收集主服务
 * 编排整个模式收集流程：扫描文件 -> 机制分类 -> 字段提取 -> 类型推断 -> 持久化
 */
public class PatternCollectorService {
    private static final Logger log = LoggerFactory.getLogger(PatternCollectorService.class);

    private final PatternSchemaDao schemaDao;
    private final PatternFieldDao fieldDao;
    private final PatternValueDao valueDao;
    private final PatternRefDao refDao;
    private final PatternSampleDao sampleDao;
    private final AttrDictionaryDao attrDao;

    private final FieldTypeInferrer typeInferrer;
    private final AttrDictionaryCollector attrCollector;
    private AionMechanismDetector mechanismDetector;

    // 进度回调
    private ProgressCallback progressCallback;

    // 线程池
    private final ExecutorService executor;

    // XML根目录
    private File xmlRootDir;

    public PatternCollectorService() {
        this.schemaDao = new PatternSchemaDao();
        this.fieldDao = new PatternFieldDao();
        this.valueDao = new PatternValueDao();
        this.refDao = new PatternRefDao();
        this.sampleDao = new PatternSampleDao();
        this.attrDao = new AttrDictionaryDao();

        this.typeInferrer = new FieldTypeInferrer();
        this.attrCollector = new AttrDictionaryCollector(attrDao);
        // mechanismDetector 延迟初始化，需要xml路径

        // 使用虚拟线程（Java 21+）
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 进度回调接口
     */
    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
        void onComplete(CollectionSummary summary);
        void onError(String message, Exception e);
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    /**
     * 执行完整的模式收集
     */
    public CollectionSummary collectAll() {
        CollectionSummary summary = new CollectionSummary();
        long startTime = System.currentTimeMillis();

        try {
            // 1. 初始化机制分类（27个）
            reportProgress(0, 5, "初始化机制分类...");
            initMechanismSchemas();
            summary.setSchemaCount(27);

            // 2. 收集属性词典
            reportProgress(1, 5, "收集属性词典...");
            AttrDictionaryCollector.CollectionResult attrResult = attrCollector.collectFromXml();
            if (attrResult.isSuccess()) {
                summary.setAttrCount(attrResult.getCollectedCount());
            }

            // 3. 扫描XML文件并分类
            reportProgress(2, 5, "扫描XML文件...");
            Map<AionMechanismCategory, List<File>> filesByMechanism = scanAndClassifyFiles();
            summary.setFileCount(countFiles(filesByMechanism));

            // 4. 提取字段模式
            reportProgress(3, 5, "提取字段模式...");
            int fieldCount = extractFieldPatterns(filesByMechanism);
            summary.setFieldCount(fieldCount);

            // 5. 更新统计信息
            reportProgress(4, 5, "更新统计信息...");
            updateSchemaStatistics();

            reportProgress(5, 5, "收集完成");
            summary.setSuccess(true);
            summary.setDurationMs(System.currentTimeMillis() - startTime);

            if (progressCallback != null) {
                progressCallback.onComplete(summary);
            }

        } catch (Exception e) {
            log.error("模式收集失败", e);
            summary.setSuccess(false);
            summary.setErrorMessage(e.getMessage());

            if (progressCallback != null) {
                progressCallback.onError("模式收集失败", e);
            }
        }

        return summary;
    }

    /**
     * 仅收集属性词典
     */
    public AttrDictionaryCollector.CollectionResult collectAttrDictionary() {
        return attrCollector.collectFromXml();
    }

    /**
     * 初始化27个机制分类
     */
    private void initMechanismSchemas() {
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            PatternSchema schema = new PatternSchema(category.name(), category.getDisplayName());
            schema.setMechanismIcon(category.getIcon());
            schema.setMechanismColor(category.getColor());
            schema.setDescription(category.getDescription());
            schemaDao.saveOrUpdate(schema);
        }
    }

    /**
     * 扫描并分类XML文件
     */
    private Map<AionMechanismCategory, List<File>> scanAndClassifyFiles() {
        Map<AionMechanismCategory, List<File>> result = new EnumMap<>(AionMechanismCategory.class);

        // 初始化
        for (AionMechanismCategory cat : AionMechanismCategory.values()) {
            result.put(cat, new ArrayList<>());
        }

        // 获取XML根目录
        String xmlPath = getXmlPath();
        if (xmlPath == null) {
            log.warn("未配置XML路径");
            return result;
        }

        File xmlDir = new File(xmlPath);
        if (!xmlDir.exists() || !xmlDir.isDirectory()) {
            log.warn("XML目录不存在: {}", xmlPath);
            return result;
        }

        // 保存根目录供后续使用
        this.xmlRootDir = xmlDir;

        // 初始化机制检测器
        File chinaDir = new File(xmlDir, "China");
        this.mechanismDetector = new AionMechanismDetector(xmlDir, chinaDir.exists() ? chinaDir : null);

        // 扫描XML文件
        List<File> xmlFiles = FileUtil.loopFiles(xmlDir, file ->
                file.getName().endsWith(".xml") && !file.getName().startsWith("."));

        log.info("发现 {} 个XML文件", xmlFiles.size());

        // 分类
        for (File file : xmlFiles) {
            String relativePath = getRelativePath(file, xmlDir);
            boolean isLocalized = relativePath.toLowerCase().contains("china");
            DetectionResult detection = mechanismDetector.detect(file, relativePath, isLocalized);
            if (detection != null && detection.getCategory() != null) {
                result.get(detection.getCategory()).add(file);
            } else {
                result.get(AionMechanismCategory.OTHER).add(file);
            }
        }

        return result;
    }

    /**
     * 获取相对路径
     */
    private String getRelativePath(File file, File rootDir) {
        String filePath = file.getAbsolutePath();
        String rootPath = rootDir.getAbsolutePath();
        if (filePath.startsWith(rootPath)) {
            return filePath.substring(rootPath.length() + 1);
        }
        return file.getName();
    }

    /**
     * 提取字段模式
     */
    private int extractFieldPatterns(Map<AionMechanismCategory, List<File>> filesByMechanism) {
        AtomicInteger totalFields = new AtomicInteger(0);

        for (Map.Entry<AionMechanismCategory, List<File>> entry : filesByMechanism.entrySet()) {
            AionMechanismCategory category = entry.getKey();
            List<File> files = entry.getValue();

            if (files.isEmpty()) continue;

            // 获取schema
            Optional<PatternSchema> schemaOpt = schemaDao.findByMechanismCode(category.name());
            if (!schemaOpt.isPresent()) continue;

            PatternSchema schema = schemaOpt.get();

            // 收集该机制下所有字段
            Map<String, FieldStatistics> fieldStats = new ConcurrentHashMap<>();

            // 限制处理的文件数量（避免过长时间）
            int maxFiles = Math.min(files.size(), 50);
            for (int i = 0; i < maxFiles; i++) {
                File file = files.get(i);
                try {
                    extractFieldsFromFile(file, fieldStats);
                } catch (Exception e) {
                    log.warn("解析文件失败: {} - {}", file.getName(), e.getMessage());
                }
            }

            // 保存字段模式
            for (Map.Entry<String, FieldStatistics> fieldEntry : fieldStats.entrySet()) {
                String fieldName = fieldEntry.getKey();
                FieldStatistics stats = fieldEntry.getValue();

                PatternField field = new PatternField(schema.getId(), fieldName);

                // 类型推断
                List<String> samples = stats.getSampleValues(10);
                FieldTypeInferrer.InferenceResult inference = typeInferrer.inferFromValues(fieldName, samples);
                typeInferrer.applyToField(field, inference);

                // 统计信息
                field.setTotalCount(stats.getTotalCount());
                field.setDistinctCount(stats.getDistinctCount());
                field.setSampleValues(JSON.toJSONString(samples));

                fieldDao.saveOrUpdate(field);
                totalFields.incrementAndGet();
            }
        }

        return totalFields.get();
    }

    /**
     * 从文件中提取字段
     */
    private void extractFieldsFromFile(File file, Map<String, FieldStatistics> fieldStats) throws Exception {
        String content = FileUtil.readString(file, StandardCharsets.UTF_16);
        Document document = DocumentHelper.parseText(content);
        Element root = document.getRootElement();

        // 递归提取字段
        extractFieldsFromElement(root, "", fieldStats);
    }

    /**
     * 递归提取元素中的字段
     */
    private void extractFieldsFromElement(Element element, String path, Map<String, FieldStatistics> fieldStats) {
        // 处理属性
        element.attributeIterator().forEachRemaining(attr -> {
            String fieldName = "_attr_" + attr.getName();
            fieldStats.computeIfAbsent(fieldName, k -> new FieldStatistics())
                    .addValue(attr.getValue());
        });

        // 处理子元素
        List<Element> children = element.elements();
        for (Element child : children) {
            String fieldName = child.getName();

            if (child.elements().isEmpty()) {
                // 叶子节点
                String value = child.getTextTrim();
                fieldStats.computeIfAbsent(fieldName, k -> new FieldStatistics())
                        .addValue(value);

                // 处理叶子节点的属性
                child.attributeIterator().forEachRemaining(attr -> {
                    String attrFieldName = "_attr__" + fieldName + "__" + attr.getName();
                    fieldStats.computeIfAbsent(attrFieldName, k -> new FieldStatistics())
                            .addValue(attr.getValue());
                });
            } else {
                // 非叶子节点，递归处理
                extractFieldsFromElement(child, path + "/" + fieldName, fieldStats);
            }
        }
    }

    /**
     * 更新schema统计信息
     */
    private void updateSchemaStatistics() {
        List<PatternSchema> schemas = schemaDao.findAll();
        for (PatternSchema schema : schemas) {
            List<PatternField> fields = fieldDao.findBySchemaId(schema.getId());
            int sampleCount = sampleDao.countBySchemaId(schema.getId());

            schemaDao.updateCounts(schema.getId(),
                    schema.getFileCount() != null ? schema.getFileCount() : 0,
                    fields.size(),
                    sampleCount);
        }
    }

    /**
     * 获取XML路径配置
     */
    private String getXmlPath() {
        try {
            return YamlUtils.getProperty("aion.xmlPath");
        } catch (Exception e) {
            log.warn("无法获取aion.xmlPath配置", e);
            return null;
        }
    }

    private int countFiles(Map<AionMechanismCategory, List<File>> map) {
        return map.values().stream().mapToInt(List::size).sum();
    }

    private void reportProgress(int current, int total, String message) {
        log.info("进度: {}/{} - {}", current, total, message);
        if (progressCallback != null) {
            progressCallback.onProgress(current, total, message);
        }
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 字段统计信息
     */
    private static class FieldStatistics {
        private final List<String> values = new ArrayList<>();
        private final Set<String> distinctValues = new HashSet<>();

        synchronized void addValue(String value) {
            values.add(value);
            if (value != null && !value.isEmpty()) {
                distinctValues.add(value);
            }
        }

        int getTotalCount() {
            return values.size();
        }

        int getDistinctCount() {
            return distinctValues.size();
        }

        List<String> getSampleValues(int max) {
            List<String> samples = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String v : values) {
                if (v != null && !v.isEmpty() && !seen.contains(v)) {
                    samples.add(v);
                    seen.add(v);
                    if (samples.size() >= max) break;
                }
            }
            return samples;
        }
    }

    /**
     * 收集汇总结果
     */
    public static class CollectionSummary {
        private boolean success;
        private String errorMessage;
        private int schemaCount;
        private int attrCount;
        private int fileCount;
        private int fieldCount;
        private int sampleCount;
        private long durationMs;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public int getSchemaCount() { return schemaCount; }
        public void setSchemaCount(int schemaCount) { this.schemaCount = schemaCount; }

        public int getAttrCount() { return attrCount; }
        public void setAttrCount(int attrCount) { this.attrCount = attrCount; }

        public int getFileCount() { return fileCount; }
        public void setFileCount(int fileCount) { this.fileCount = fileCount; }

        public int getFieldCount() { return fieldCount; }
        public void setFieldCount(int fieldCount) { this.fieldCount = fieldCount; }

        public int getSampleCount() { return sampleCount; }
        public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }

        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

        @Override
        public String toString() {
            return String.format(
                    "收集结果: %s\n" +
                    "- 机制分类: %d\n" +
                    "- 属性词典: %d\n" +
                    "- 扫描文件: %d\n" +
                    "- 字段模式: %d\n" +
                    "- 耗时: %.2f秒",
                    success ? "成功" : "失败 - " + errorMessage,
                    schemaCount, attrCount, fileCount, fieldCount,
                    durationMs / 1000.0);
        }
    }
}
