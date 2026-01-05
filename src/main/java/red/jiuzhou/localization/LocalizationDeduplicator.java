package red.jiuzhou.localization;

import cn.hutool.core.io.FileUtil;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.BomAwareFileReader;
import red.jiuzhou.util.FileEncodingDetector;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * Localization Deduplicator
 *
 * <p>Removes duplicate entries from public XML directory based on China localization files.
 * When an ID exists in both China and public directory, the entry in public directory is removed.
 *
 * @author yanxq
 * @date 2025-01-05
 */
public class LocalizationDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(LocalizationDeduplicator.class);

    private final Path chinaPath;
    private final Path publicPath;
    private Consumer<String> logCallback;

    public LocalizationDeduplicator(String chinaPath, String publicPath) {
        this.chinaPath = Paths.get(chinaPath);
        this.publicPath = Paths.get(publicPath);
    }

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    private void log(String message) {
        log.info(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    /**
     * Scan China directory and extract IDs from each XML file
     *
     * @return Map of filename to set of IDs
     */
    public Map<String, Set<String>> scanChinaDirectory() {
        Map<String, Set<String>> result = new LinkedHashMap<>();

        File chinaDir = chinaPath.toFile();
        if (!chinaDir.exists() || !chinaDir.isDirectory()) {
            log("China directory not found: " + chinaPath);
            return result;
        }

        File[] xmlFiles = chinaDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));
        if (xmlFiles == null || xmlFiles.length == 0) {
            log("No XML files found in China directory");
            return result;
        }

        log("Found " + xmlFiles.length + " XML files in China directory");

        for (File xmlFile : xmlFiles) {
            try {
                Set<String> ids = extractIds(xmlFile);
                if (!ids.isEmpty()) {
                    result.put(xmlFile.getName(), ids);
                    log("  " + xmlFile.getName() + ": " + ids.size() + " IDs");
                }
            } catch (Exception e) {
                log("Failed to parse " + xmlFile.getName() + ": " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Extract all entry IDs from an XML file
     *
     * @param xmlFile XML file to parse
     * @return Set of ID strings
     */
    public Set<String> extractIds(File xmlFile) throws Exception {
        Set<String> ids = new LinkedHashSet<>();

        // Detect encoding and read file
        FileEncodingDetector.EncodingInfo encoding = FileEncodingDetector.detect(xmlFile);
        String content = BomAwareFileReader.readString(xmlFile, encoding);
        Document document = DocumentHelper.parseText(content);

        Element root = document.getRootElement();
        for (Element entry : root.elements()) {
            // Strategy 1: <id> child element
            Element idElement = entry.element("id");
            if (idElement != null) {
                String id = idElement.getTextTrim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
                continue;
            }

            // Strategy 2: id attribute
            String idAttr = entry.attributeValue("id");
            if (idAttr != null && !idAttr.isEmpty()) {
                ids.add(idAttr);
            }
        }

        return ids;
    }

    /**
     * Find duplicate IDs between China and public directories
     *
     * @param chinaIds Map of China file IDs
     * @return List of deduplication results for preview
     */
    public List<DeduplicationPreview> findDuplicates(Map<String, Set<String>> chinaIds) {
        List<DeduplicationPreview> previews = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : chinaIds.entrySet()) {
            String fileName = entry.getKey();
            Set<String> idsToRemove = entry.getValue();

            File publicFile = publicPath.resolve(fileName).toFile();
            if (!publicFile.exists()) {
                log("Public file not found: " + fileName);
                continue;
            }

            try {
                Set<String> publicIds = extractIds(publicFile);
                Set<String> duplicateIds = new LinkedHashSet<>(publicIds);
                duplicateIds.retainAll(idsToRemove);

                if (!duplicateIds.isEmpty()) {
                    DeduplicationPreview preview = new DeduplicationPreview();
                    preview.setFileName(fileName);
                    preview.setChinaIdCount(idsToRemove.size());
                    preview.setPublicIdCount(publicIds.size());
                    preview.setDuplicateCount(duplicateIds.size());
                    preview.setDuplicateIds(duplicateIds);
                    previews.add(preview);
                }
            } catch (Exception e) {
                log("Failed to analyze " + fileName + ": " + e.getMessage());
            }
        }

        return previews;
    }

    /**
     * Execute deduplication with optional backup
     *
     * @param chinaIds      Map of China file IDs
     * @param createBackup  Whether to create backup before modification
     * @param progressCallback Progress callback (0.0 - 1.0)
     * @return Total number of entries removed
     */
    public int executeDeduplication(Map<String, Set<String>> chinaIds, boolean createBackup,
                                     Consumer<Double> progressCallback) {
        int totalRemoved = 0;
        int processed = 0;
        int total = chinaIds.size();

        // Create backup directory if needed
        Path backupDir = null;
        if (createBackup) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            backupDir = publicPath.resolve(".backup").resolve("dedupe_" + timestamp);
            try {
                Files.createDirectories(backupDir);
                log("Backup directory created: " + backupDir);
            } catch (IOException e) {
                log("Failed to create backup directory: " + e.getMessage());
                return 0;
            }
        }

        for (Map.Entry<String, Set<String>> entry : chinaIds.entrySet()) {
            String fileName = entry.getKey();
            Set<String> idsToRemove = entry.getValue();

            File publicFile = publicPath.resolve(fileName).toFile();
            if (!publicFile.exists()) {
                processed++;
                continue;
            }

            try {
                // Create backup
                if (backupDir != null) {
                    FileUtil.copy(publicFile, backupDir.resolve(fileName).toFile(), true);
                }

                // Remove duplicates
                int removed = removeDuplicateIds(publicFile, idsToRemove);
                totalRemoved += removed;

                if (removed > 0) {
                    log("Removed " + removed + " entries from " + fileName);
                }

            } catch (Exception e) {
                log("Failed to process " + fileName + ": " + e.getMessage());
            }

            processed++;
            if (progressCallback != null) {
                progressCallback.accept((double) processed / total);
            }
        }

        log("Deduplication complete. Total entries removed: " + totalRemoved);
        return totalRemoved;
    }

    /**
     * Remove entries with specified IDs from a public XML file
     *
     * @param publicFile  The public XML file to modify
     * @param idsToRemove Set of IDs to remove
     * @return Number of entries removed
     */
    public int removeDuplicateIds(File publicFile, Set<String> idsToRemove) throws Exception {
        // Detect encoding
        FileEncodingDetector.EncodingInfo encoding = FileEncodingDetector.detect(publicFile);

        // Read and parse
        String content = BomAwareFileReader.readString(publicFile, encoding);
        Document document = DocumentHelper.parseText(content);

        Element root = document.getRootElement();
        List<Element> toRemove = new ArrayList<>();

        // Find entries to remove
        for (Element entry : root.elements()) {
            String id = null;

            // Check <id> child element
            Element idElement = entry.element("id");
            if (idElement != null) {
                id = idElement.getTextTrim();
            } else {
                // Check id attribute
                id = entry.attributeValue("id");
            }

            if (id != null && idsToRemove.contains(id)) {
                toRemove.add(entry);
            }
        }

        // Remove entries
        for (Element e : toRemove) {
            root.remove(e);
        }

        // Save file if modified
        if (!toRemove.isEmpty()) {
            saveXml(document, publicFile.getAbsolutePath(), encoding);
        }

        return toRemove.size();
    }

    /**
     * Save XML document with proper encoding
     */
    private void saveXml(Document document, String filePath, FileEncodingDetector.EncodingInfo encoding) throws Exception {
        OutputFormat format = OutputFormat.createPrettyPrint();

        // Normalize encoding for XML declaration
        String xmlEncoding = encoding.getEncoding();
        if (xmlEncoding.startsWith("UTF-16")) {
            xmlEncoding = "UTF-16";
        }
        format.setEncoding(xmlEncoding);
        format.setIndent("\t");
        format.setNewlines(true);
        format.setTrimText(false);

        try (OutputStreamWriter writer = new OutputStreamWriter(
                Files.newOutputStream(Paths.get(filePath)),
                encoding.toCharset())) {

            XMLWriter xmlWriter = new XMLWriter(writer, format);
            xmlWriter.write(document);
            xmlWriter.close();
        }
    }

    /**
     * Preview data class
     */
    public static class DeduplicationPreview {
        private String fileName;
        private int chinaIdCount;
        private int publicIdCount;
        private int duplicateCount;
        private Set<String> duplicateIds;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public int getChinaIdCount() { return chinaIdCount; }
        public void setChinaIdCount(int chinaIdCount) { this.chinaIdCount = chinaIdCount; }

        public int getPublicIdCount() { return publicIdCount; }
        public void setPublicIdCount(int publicIdCount) { this.publicIdCount = publicIdCount; }

        public int getDuplicateCount() { return duplicateCount; }
        public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }

        public Set<String> getDuplicateIds() { return duplicateIds; }
        public void setDuplicateIds(Set<String> duplicateIds) { this.duplicateIds = duplicateIds; }
    }
}
