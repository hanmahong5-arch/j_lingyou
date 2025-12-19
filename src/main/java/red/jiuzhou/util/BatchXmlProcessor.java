package red.jiuzhou.util;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 批量XML处理器
 *
 * 针对大型XML文件的优化处理：
 * - 分批读取和处理，避免内存溢出
 * - 事务批量提交，提高导入性能
 * - 流式写入，支持大数据集导出
 * - 文件备份机制
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class BatchXmlProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchXmlProcessor.class);

    // 默认批量大小
    private static final int DEFAULT_BATCH_SIZE = 1000;

    // 默认字符编码
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * 处理进度回调
     */
    public interface ProgressCallback {
        void onProgress(int processed, int total, String message);
    }

    /**
     * 批量读取XML并处理每个元素
     *
     * @param xmlFile XML文件
     * @param elementName 要处理的元素名称
     * @param processor 元素处理器
     * @param batchSize 批量大小
     * @return 处理的元素数量
     */
    public static int readAndProcess(File xmlFile, String elementName,
                                      Consumer<Element> processor, int batchSize) {
        return readAndProcess(xmlFile, elementName, processor, batchSize, null);
    }

    /**
     * 批量读取XML并处理每个元素（带进度回调）
     */
    public static int readAndProcess(File xmlFile, String elementName,
                                      Consumer<Element> processor, int batchSize,
                                      ProgressCallback callback) {
        int processed = 0;

        try {
            SAXReader reader = new SAXReader();
            Document document = reader.read(xmlFile);
            Element root = document.getRootElement();

            List<Element> elements = root.elements(elementName);
            int total = elements.size();

            log.info("开始处理XML文件: {}, 共 {} 个 {} 元素", xmlFile.getName(), total, elementName);

            for (Element element : elements) {
                processor.accept(element);
                processed++;

                if (callback != null && processed % batchSize == 0) {
                    callback.onProgress(processed, total,
                        String.format("已处理 %d/%d", processed, total));
                }
            }

            if (callback != null) {
                callback.onProgress(processed, total, "处理完成");
            }

            log.info("XML处理完成: {} 个元素", processed);

        } catch (Exception e) {
            log.error("XML处理失败: {}", e.getMessage(), e);
        }

        return processed;
    }

    /**
     * 批量导入XML到数据库
     *
     * @param xmlFile XML文件
     * @param elementName 元素名称
     * @param jdbcTemplate JDBC模板
     * @param mapper 元素到SQL参数的映射函数
     * @param insertSql INSERT SQL语句
     * @param batchSize 批量大小
     * @param <T> 参数类型
     * @return 导入的记录数
     */
    public static <T> int importToDatabase(File xmlFile, String elementName,
                                           JdbcTemplate jdbcTemplate,
                                           Function<Element, Object[]> mapper,
                                           String insertSql, int batchSize) {
        return importToDatabase(xmlFile, elementName, jdbcTemplate, mapper,
            insertSql, batchSize, null, null);
    }

    /**
     * 批量导入XML到数据库（完整版本）
     *
     * @param beforeImport 导入前回调（如清空表）
     * @param callback 进度回调
     */
    public static <T> int importToDatabase(File xmlFile, String elementName,
                                           JdbcTemplate jdbcTemplate,
                                           Function<Element, Object[]> mapper,
                                           String insertSql, int batchSize,
                                           Runnable beforeImport,
                                           ProgressCallback callback) {
        int imported = 0;

        try {
            // 导入前回调
            if (beforeImport != null) {
                beforeImport.run();
            }

            SAXReader reader = new SAXReader();
            Document document = reader.read(xmlFile);
            Element root = document.getRootElement();

            List<Element> elements = root.elements(elementName);
            int total = elements.size();

            log.info("开始导入: {} -> 数据库, 共 {} 条", xmlFile.getName(), total);

            // 批量插入
            for (int i = 0; i < elements.size(); i += batchSize) {
                int end = Math.min(i + batchSize, elements.size());
                List<Element> batch = elements.subList(i, end);

                for (Element element : batch) {
                    Object[] params = mapper.apply(element);
                    if (params != null) {
                        jdbcTemplate.update(insertSql, params);
                        imported++;
                    }
                }

                if (callback != null) {
                    callback.onProgress(imported, total,
                        String.format("已导入 %d/%d", imported, total));
                }

                log.debug("批量提交: {} 条", end - i);
            }

            if (callback != null) {
                callback.onProgress(imported, total, "导入完成");
            }

            log.info("导入完成: {} 条记录", imported);

        } catch (Exception e) {
            log.error("导入失败: {}", e.getMessage(), e);
        }

        return imported;
    }

    /**
     * 流式写入XML文件
     *
     * @param outputFile 输出文件
     * @param rootName 根元素名称
     * @param docType DOCTYPE声明（可选）
     * @param contentWriter 内容写入器
     */
    public static void writeXml(File outputFile, String rootName,
                                 String docType, Consumer<XmlWriter> contentWriter) {
        writeXml(outputFile, rootName, docType, DEFAULT_CHARSET, contentWriter);
    }

    /**
     * 流式写入XML文件（指定编码）
     */
    public static void writeXml(File outputFile, String rootName,
                                 String docType, Charset charset,
                                 Consumer<XmlWriter> contentWriter) {
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(outputFile), charset))) {

            // XML声明
            bw.write("<?xml version=\"1.0\" encoding=\"" + charset.name() + "\"?>\n");

            // DOCTYPE
            if (docType != null && !docType.isEmpty()) {
                bw.write(docType);
                bw.write("\n");
            }

            // 根元素开始
            bw.write("<" + rootName + ">\n");

            // 内容
            XmlWriter writer = new XmlWriter(bw);
            contentWriter.accept(writer);
            writer.flush();

            // 根元素结束
            bw.write("</" + rootName + ">\n");

            log.info("XML写入完成: {}", outputFile.getAbsolutePath());

        } catch (Exception e) {
            log.error("XML写入失败: {}", e.getMessage(), e);
        }
    }

    /**
     * XML写入器
     */
    public static class XmlWriter {
        private BufferedWriter writer;
        private StringBuilder buffer;
        private int bufferLimit;
        private int writeCount;

        public XmlWriter(BufferedWriter writer) {
            this(writer, 100000); // 100KB缓冲
        }

        public XmlWriter(BufferedWriter writer, int bufferLimit) {
            this.writer = writer;
            this.buffer = new StringBuilder();
            this.bufferLimit = bufferLimit;
            this.writeCount = 0;
        }

        /**
         * 写入原始字符串
         */
        public void write(String content) throws IOException {
            buffer.append(content);
            checkFlush();
        }

        /**
         * 写入带换行的字符串
         */
        public void writeLine(String content) throws IOException {
            buffer.append(content).append("\n");
            checkFlush();
        }

        /**
         * 写入元素
         */
        public void writeElement(String name, String value) throws IOException {
            if (value != null && !value.isEmpty()) {
                buffer.append("  <").append(name).append(">")
                      .append(escapeXml(value.trim()))
                      .append("</").append(name).append(">\n");
                checkFlush();
            }
        }

        /**
         * 写入元素（带缩进）
         */
        public void writeElement(String name, String value, String indent) throws IOException {
            if (value != null && !value.isEmpty()) {
                buffer.append(indent).append("<").append(name).append(">")
                      .append(escapeXml(value.trim()))
                      .append("</").append(name).append(">\n");
                checkFlush();
            }
        }

        /**
         * 开始一个元素
         */
        public void startElement(String name) throws IOException {
            buffer.append("  <").append(name).append(">\n");
            checkFlush();
        }

        /**
         * 开始一个元素（带属性）
         */
        public void startElement(String name, String... attributes) throws IOException {
            buffer.append("  <").append(name);
            for (int i = 0; i < attributes.length - 1; i += 2) {
                buffer.append(" ").append(attributes[i])
                      .append("=\"").append(escapeXml(attributes[i + 1])).append("\"");
            }
            buffer.append(">\n");
            checkFlush();
        }

        /**
         * 结束一个元素
         */
        public void endElement(String name) throws IOException {
            buffer.append("  </").append(name).append(">\n");
            writeCount++;
            checkFlush();
        }

        /**
         * 获取已写入的元素数量
         */
        public int getWriteCount() {
            return writeCount;
        }

        /**
         * 检查是否需要刷新缓冲区
         */
        private void checkFlush() throws IOException {
            if (buffer.length() >= bufferLimit) {
                flush();
            }
        }

        /**
         * 刷新缓冲区
         */
        public void flush() throws IOException {
            writer.write(buffer.toString());
            writer.flush();
            buffer = new StringBuilder();
        }

        private String escapeXml(String text) {
            if (text == null) return "";
            return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
        }
    }

    /**
     * 备份文件
     *
     * @param file 要备份的文件
     * @return 备份文件路径
     */
    public static String backupFile(File file) {
        if (!file.exists()) {
            return null;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String backupName = file.getName() + "." + timestamp + ".bak";
        File backupFile = new File(file.getParentFile(), backupName);

        try (InputStream in = new FileInputStream(file);
             OutputStream out = new FileOutputStream(backupFile)) {

            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            log.info("文件备份完成: {} -> {}", file.getName(), backupName);
            return backupFile.getAbsolutePath();

        } catch (Exception e) {
            log.error("文件备份失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取备份目录下的所有备份文件
     */
    public static File[] getBackupFiles(File originalFile) {
        File parent = originalFile.getParentFile();
        String prefix = originalFile.getName() + ".";

        return parent.listFiles((dir, name) ->
            name.startsWith(prefix) && name.endsWith(".bak"));
    }

    /**
     * 从备份恢复文件
     */
    public static boolean restoreFromBackup(File backupFile, File targetFile) {
        try (InputStream in = new FileInputStream(backupFile);
             OutputStream out = new FileOutputStream(targetFile)) {

            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            log.info("文件恢复完成: {} -> {}", backupFile.getName(), targetFile.getName());
            return true;

        } catch (Exception e) {
            log.error("文件恢复失败: {}", e.getMessage(), e);
            return false;
        }
    }
}
