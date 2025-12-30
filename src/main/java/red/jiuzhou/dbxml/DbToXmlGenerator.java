package red.jiuzhou.dbxml;

import cn.hutool.core.io.FileUtil;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import red.jiuzhou.util.*;
import red.jiuzhou.util.RoundTripValidator;
import red.jiuzhou.validation.XmlFieldBlacklist;
import red.jiuzhou.validation.XmlFieldOrderManager;
import red.jiuzhou.validation.XmlFieldValueCorrector;
import red.jiuzhou.validation.server.ServerComplianceFilter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * @className: red.jiuzhou.dbxml.DbToXmlGenerator.java
 * @description: 数据库导出为XML
 * @author: yanxq
 * @date:  2025-04-09 16:02
 * @version V1.0
 */
public class DbToXmlGenerator {
    private static final Logger log = LoggerFactory.getLogger(DbToXmlGenerator.class);
    private static TableConf table;
    // 每页数据量
    private static final int PAGE_SIZE = 1000;
    // 临时文件存放目录
    private static final String TEMP_DIR = "temp_xml/";
    private int total;
    private CounterUtil counterUtil = new CounterUtil();
    private static String mapType;

    static List<String> worldSpecialTabNames = Arrays.asList(YamlUtils.getProperty("world.specialTabName").split(","));

    private static final SubTablePreloader subTablePreloader = new SubTablePreloader();

    // ==================== 服务器合规性过滤器（2025-12-29新增）====================
    // 从102,825行服务器日志中提取的规则，确保导出的XML符合服务器要求
    // 配置项：server.compliance.enabled (默认true)
    private static final ServerComplianceFilter complianceFilter = new ServerComplianceFilter();
    private static final boolean COMPLIANCE_ENABLED =
        Boolean.parseBoolean(YamlUtils.getProperty("application.yml", "server.compliance.enabled", "true"));


    public DbToXmlGenerator(String tabName, String mapType, String tabFilePath) {
        this.mapType = mapType;
        TableConf table = TabConfLoad.getTale(tabName, tabFilePath);
        if (table == null) {
            throw new RuntimeException("找不到表配置信息：" + tabName);
        }
        table.chk();
        subTablePreloader.preloadAllSubTables(table);
        this.table = table;
    }

    public String processAndMerge() {
        try {
            // 0. 初始化字段顺序管理器（确保字段顺序稳定性）
            if (!XmlFieldOrderManager.initialize()) {
                log.warn("字段顺序管理器初始化失败，将使用默认顺序");
            } else {
                log.info("字段顺序管理器已初始化：{}", XmlFieldOrderManager.getStatistics());
            }

            // 1. 获取总数据量
            int totalRecords = DatabaseUtil.getTotalRowCount(table.getTableName());
            this.total = totalRecords;
            int totalPages = (totalRecords + PAGE_SIZE - 1) / PAGE_SIZE;

            FileUtil.del(YamlUtils.getProperty("file.exportDataPath") + File.separator + TEMP_DIR);

            // 2. 使用虚拟线程并行处理分页（Java 21+）
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Runnable> tasks = new ArrayList<>();
                for (int page = 0; page < totalPages; page++) {
                    int offset = page * PAGE_SIZE;
                    int finalPage = page;
                    tasks.add(() -> {
                        String fileName = TEMP_DIR + "part_" + finalPage + ".xml";
                        log.info("开始处理分页：{}", finalPage);
                        generateXmlPart(table, offset, PAGE_SIZE, fileName);
                    });
                }

                // 3. 提交所有任务并等待完成
                tasks.stream()
                    .map(executor::submit)
                    .toList()
                    .forEach(future -> {
                        try {
                            future.get();
                        } catch (Exception e) {
                            throw new RuntimeException("分页处理失败", e);
                        }
                    });
            } // try-with-resources 自动关闭 executor

            // 5. 合并所有临时文件
            List<File> tempFileList = FileUtil.loopFiles(YamlUtils.getProperty("file.exportDataPath") + File.separator + TEMP_DIR).stream()
                    .filter(file -> file.getName().endsWith(".xml"))
                    .collect(Collectors.toList());
            tempFileList.sort(Comparator.comparing(File::getName));
            String exportedFilePath = mergeXmlFiles(tempFileList);

            // 6. 清理临时文件
            FileUtil.del(YamlUtils.getProperty("file.exportDataPath") + File.separator + TEMP_DIR);

            // 7. 输出字段值修正统计
            String correctionStats = XmlFieldValueCorrector.getStatistics();
            if (!correctionStats.contains("未进行")) {
                log.info("📊 {}", correctionStats);
            }

            // 8. 返回导出的文件路径
            return exportedFilePath;

        } catch (Exception e) {
            throw new RuntimeException("处理失败", e);
        }
    }

    // 生成分页XML
    private void generateXmlPart(TableConf table, int offset, int limit, String outputFileName) {
        try {
            String sql = table.getSql();
            if(mapType != null && !mapType.isEmpty()){
                sql = sql.replace("$mapType", mapType) + " LIMIT " + limit + " OFFSET " + offset;
            }else{
                sql = sql + " LIMIT " + limit + " OFFSET " + offset;
            }

            log.info("sql:{}", sql);
            JdbcTemplate jdbcTemplate = DatabaseUtil.getJdbcTemplate();
            List<Map<String, Object>> itemList = jdbcTemplate.queryForList(sql);

            // ==================== 服务器合规性过滤（2025-12-29新增）====================
            // 应用从服务器日志反推的验证规则，确保导出的XML符合服务器要求
            if (COMPLIANCE_ENABLED && complianceFilter.hasRules(table.getTableName())) {
                int originalSize = itemList.size();
                List<ServerComplianceFilter.FilterResult> filterResults =
                    complianceFilter.filterBatch(table.getTableName(), itemList);

                // 使用过滤后的数据替换原数据
                itemList = filterResults.stream()
                    .map(ServerComplianceFilter.FilterResult::getFilteredData)
                    .collect(Collectors.toList());

                // 统计过滤信息
                long changedCount = filterResults.stream()
                    .filter(ServerComplianceFilter.FilterResult::hasChanges)
                    .count();
                long totalRemovedFields = filterResults.stream()
                    .mapToLong(r -> r.getRemovedFields().size())
                    .sum();
                long totalCorrectedFields = filterResults.stream()
                    .mapToLong(r -> r.getCorrectedFields().size())
                    .sum();

                if (changedCount > 0) {
                    log.info("✅ 服务器合规过滤 [{}]: 处理了{}/{}条记录，移除{}个字段，修正{}个字段值",
                        table.getTableName(), changedCount, originalSize,
                        totalRemovedFields, totalCorrectedFields);

                    // 记录具体的变更（仅DEBUG级别，避免日志过多）
                    if (log.isDebugEnabled()) {
                        filterResults.stream()
                            .filter(ServerComplianceFilter.FilterResult::hasChanges)
                            .limit(5)  // 只记录前5条作为示例
                            .forEach(result -> {
                                if (!result.getRemovedFields().isEmpty()) {
                                    log.debug("  移除字段: {}", result.getRemovedFields());
                                }
                                if (!result.getCorrectedFields().isEmpty()) {
                                    log.debug("  修正字段: {}", result.getCorrectedFields());
                                }
                            });
                    }
                }
            }

            List<String> listDbcolumnList = table.getListDbcolumnList();
            Document document = DocumentHelper.createDocument();
            Element root = document.addElement(table.getXmlRootTag());

            for (Map<String, Object> itemMap : itemList) {
                Element element = null;
                if(table.getXmlItemTag() != null && !table.getXmlItemTag().isEmpty()){
                    element = root.addElement(table.getXmlItemTag());
                }else{
                    element = root;
                }
                Set<String> keySet = itemMap.keySet();

                // ==================== 字段排序：使用XmlFieldOrderManager保证稳定顺序 ====================
                // 1. 按照数据库定义顺序排序（同时自动过滤黑名单）
                keySet = XmlFieldOrderManager.sortFields(table.getTableName(), keySet);

                // 2. 统计过滤的字段数量
                int originalCount = itemMap.keySet().size();
                int filteredCount = originalCount - keySet.size();
                if (filteredCount > 0) {
                    log.info("表 {} 过滤了 {} 个黑名单字段", table.getTableName(), filteredCount);
                }

                // 3. 特殊字段顺序调整（attacks/skills）
                keySet = reorderIfNeeded(keySet, "attacks", "skills");

                if("world".equals(table.getTableName())){
                    total = keySet.size();
                }
                for (String key : keySet) {

                    if("world".equals(table.getTableName()) && "mapTp".equals(key)){
                        continue;
                    }
                    if (itemMap.get(key) != null) {
                        String value = String.valueOf(itemMap.get(key));

                        // ==================== 字段值自动修正（确保符合服务器要求）====================
                        value = XmlFieldValueCorrector.correctValue(table.getTableName(), key, value);

                        if(key.startsWith("_attr_")){
                            element.addAttribute(key.replace("_attr_", ""), value);
                        }else{
                            element.addElement(key).setText(value);
                        }
                    }
                    // 跳过 NULL 值，不创建空节点（保持与原始 XML 一致）
                    if (listDbcolumnList.contains(key)) {
                        ColumnMapping columnMapping = table.getColumnMapping(key);
                        String parentVal = getParentVal(itemMap, columnMapping);
                        parseSubquery(element, columnMapping, jdbcTemplate, parentVal);
                    }
                    if("world".equals(table.getTableName())){
                        counterUtil.increment();
                    }
                }
                counterUtil.increment();
                // 只在每10%进度时打印日志，减少日志输出频率
                long count = counterUtil.getCount();
                double progress = (count / (double) total * 100);
                if (count == 1 || count == total || count % Math.max(1, total / 10) == 0) {
                    log.info("进度：" + count + "/" + total + "，完成度：" + String.format("%.1f", progress) + "%");
                }
            }

            // 保存为临时文件
            FileUtil.writeString(document.asXML(), YamlUtils.getProperty("file.exportDataPath") + File.separator + outputFileName, StandardCharsets.UTF_16);
        } catch (Exception e) {
            log.error("err::::::::::::" + JSONRecord.getErrorMsg(e));
            throw new RuntimeException("生成分页XML失败", e);
        }
    }
    // 合并所有XML文件
    private String mergeXmlFiles(List<File> xmlFiles) throws Exception {
        Document document = DocumentHelper.createDocument();
        Element root = document.addElement(table.getXmlRootTag());
        if(table.getXmlRootAttr() != null && !table.getXmlRootAttr().trim().isEmpty()){
            root.addAttribute(table.getXmlRootAttr().split("=")[0], table.getXmlRootAttr().split("=")[1]);
        }

        for (File file : xmlFiles) {
            Document doc = DocumentHelper.parseText(FileUtil.readString(file, StandardCharsets.UTF_16));
            Element childRoot = doc.getRootElement();
            for (Iterator<?> it = childRoot.elementIterator(); it.hasNext(); ) {
                Object obj = it.next();
                if (obj instanceof Element) {
                    Element element = (Element) obj;
                    // 先从原来的 Document 移除
                    element.detach();
                    // 然后添加到新的 Document
                    root.add(element);
                }
            }
        }
        String exportFileName = StringUtils.hasLength(table.getRealTableName()) ? table.getRealTableName() : table.getTableName();
        String xmlFile = YamlUtils.getProperty("file.exportDataPath") + File.separator + exportFileName + ".xml";
        // 传入表名和 mapType 以查询和恢复原始编码（支持 World 表）
        saveFormatXml(document, xmlFile, table.getTableName(), mapType);
        if(table.getFilePath().contains("AionMap")){
            XmlStringModifier.insertStringAfterFirstLine(xmlFile);
        }

        // 返回导出的文件绝对路径
        log.info("✅ 文件已导出到: {}", xmlFile);
        return xmlFile;
    }


    public double getProgress() {
        return (double) counterUtil.getCount() / total;
    }

    private static void parseSubquery(Element element, ColumnMapping columnMapping, JdbcTemplate jdbcTemplate, String id) {
        String sql = columnMapping.getSql().replace("#associated_filed", id);
        List<Map<String, Object>> subList = new ArrayList<>();
        if("world".equals(table.getTableName())){
            sql = sql.replace("$mapType", mapType.toLowerCase());
            subList = jdbcTemplate.queryForList(sql);
        }else{
            subList = subTablePreloader.getSubData(columnMapping.getTableName(), id);
        }
        if (subList.isEmpty()) {
            return;
        }
        Element subEle = createElement(element, columnMapping);

        for (Map<String, Object> subMap : subList) {
            Element dataElement = subEle.addElement(columnMapping.getXmlTag());
            Set<String> subKeySet = subMap.keySet();

            // ==================== 子表字段排序：使用XmlFieldOrderManager ====================
            subKeySet = XmlFieldOrderManager.sortFields(columnMapping.getTableName(), subKeySet);
            subKeySet = reorderIfNeeded(subKeySet, "attacks", "skills");

            for (String subKey : subKeySet) {
                if(subKey.equals(columnMapping.getAssociatedFiled()) || columnMapping.getAssociatedFiled().contains(">" + subKey)){
                    continue;
                }
                if(worldSpecialTabNames.contains(columnMapping.getTableName()) && "world__id".equals(subKey)){
                    continue;
                }
                if("world".equals(table.getTableName()) && "mapTp".equals(subKey)){
                    continue;
                }
                if(subKey.startsWith("_attr_") && subMap.get(subKey) != null){
                    String subValue = String.valueOf(subMap.get(subKey));
                    // 子表字段值也需要修正
                    subValue = XmlFieldValueCorrector.correctValue(columnMapping.getTableName(), subKey, subValue);

                    if(subKey.contains("__")){
                        String[] attrArr = subKey.split("__");
                        dataElement.element(attrArr[1]).addAttribute(attrArr[2], subValue);
                    }else{
                        dataElement.addAttribute(subKey.replace("_attr_", ""), subValue);
                    }
                }else if (subMap.get(subKey) != null) {
                    String subValue = String.valueOf(subMap.get(subKey));
                    // 子表字段值也需要修正
                    subValue = XmlFieldValueCorrector.correctValue(columnMapping.getTableName(), subKey, subValue);

                    dataElement.addElement(subKey).setText(subValue);
                }

            }
            if (columnMapping.getList() != null && !columnMapping.getList().isEmpty()) {
                for (ColumnMapping subColumnMapping : columnMapping.getList()) {
                    String parentVal = getParentVal(subMap, subColumnMapping);
                    parseSubquery(dataElement, subColumnMapping, jdbcTemplate, parentVal);
                }
            }

        }
    }

    private static Element createElement(Element element, ColumnMapping columnMapping) {
        String addDataNode = columnMapping.getAddDataNode();
        if(addDataNode.trim().isEmpty()){
            return element;
        }
        String[] xmlTagArr = addDataNode.split(":");
        for (String tag : xmlTagArr) {
            try {
                element = element.addElement(tag);
            }catch (Exception e){
                log.error("addDataNode {}::::::::::::tag {}" ,addDataNode, tag);
                log.error("err::::::::::::" + JSONRecord.getErrorMsg(e));
            }

        }
        //element = element.addElement(columnMapping.getXmlTag());
        return element;
    }

    public static void saveFormatXml(Document document, String filePath, String tableName) throws Exception {
        saveFormatXml(document, filePath, tableName, "");
    }

    public static void saveFormatXml(Document document, String filePath, String tableName, String mapType) throws Exception {
        // ========== 透明编码转换层：恢复原始编码（带缓存优化）==========
        FileEncodingDetector.EncodingInfo encoding = EncodingMetadataCache.getWithCache(tableName, mapType != null ? mapType : "");
        log.info("✅ 导出时使用原始编码: 表={}, mapType={}, 编码={}", tableName, mapType, encoding);
        // =================================================

        // 设置格式化方式
        // 美化格式（缩进 + 换行）
        OutputFormat format = OutputFormat.createPrettyPrint();

        // ========== XML 声明编码标准化（服务端兼容性）==========
        // XML 声明统一使用 "UTF-16"（而不是 "UTF-16BE"/"UTF-16LE"）
        // 但实际文件写入仍使用正确的字节序（通过 BOM 和 Charset 控制）
        String xmlDeclarationEncoding = encoding.getEncoding();
        if (xmlDeclarationEncoding.startsWith("UTF-16")) {
            xmlDeclarationEncoding = "UTF-16"; // 统一为 UTF-16
            log.debug("✅ XML 声明编码标准化: {} → UTF-16", encoding.getEncoding());
        }
        format.setEncoding(xmlDeclarationEncoding);
        // ====================================================
        // 设置缩进大小（4 个空格）
        //format.setIndentSize(4);
        format.setIndent("\t");
        // 允许换行
        format.setNewlines(true);
        // **关键：避免自动去除空格**
        format.setTrimText(false);

        // ========== BOM 写入支持：确保往返一致性 ==========
        OutputStream fileOutputStream = Files.newOutputStream(Paths.get(filePath));
        if (encoding.hasBOM()) {
            writeBOM(fileOutputStream, encoding);
            log.debug("✅ 已写入 BOM 标记: {}", encoding.getEncoding());
        }
        // =================================================

        OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, encoding.toCharset());
        XMLWriter xmlWriter = new XMLWriter(writer, format);
        try {
            xmlWriter.write(document);

            // 更新导出时间戳（支持 World 表 mapType）
            EncodingMetadataManager.updateExportTime(tableName, mapType != null ? mapType : "");

            // ========== 自动验证往返一致性 ==========
            xmlWriter.flush();
            writer.flush();
            fileOutputStream.flush();
        } finally {
            xmlWriter.close();
            writer.close();
        }

        // 文件关闭后立即验证（确保所有数据已写入磁盘）
        try {
            RoundTripValidator.ValidationResult result =
                    RoundTripValidator.validateRoundTrip(tableName, mapType != null ? mapType : "", new File(filePath));
            log.info(result.getMessage());
        } catch (Exception e) {
            log.warn("⚠️ 往返一致性验证失败: {}", e.getMessage());
        }
        // =================================================
    }

    /**
     * 写入 BOM (Byte Order Mark) 标记
     *
     * @param out 输出流
     * @param encoding 编码信息
     * @throws IOException IO异常
     */
    private static void writeBOM(OutputStream out, FileEncodingDetector.EncodingInfo encoding) throws IOException {
        if ("UTF-16BE".equals(encoding.getEncoding())) {
            // UTF-16 Big Endian BOM: FE FF
            out.write(new byte[]{(byte)0xFE, (byte)0xFF});
        } else if ("UTF-16LE".equals(encoding.getEncoding())) {
            // UTF-16 Little Endian BOM: FF FE
            out.write(new byte[]{(byte)0xFF, (byte)0xFE});
        } else if ("UTF-16".equals(encoding.getEncoding())) {
            // UTF-16（自动检测字节序）默认使用 Big Endian BOM
            out.write(new byte[]{(byte)0xFE, (byte)0xFF});
        } else if ("UTF-8".equals(encoding.getEncoding())) {
            // UTF-8 BOM: EF BB BF
            out.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF});
        }
    }

    private static String getParentVal(Map<String, Object> itemMap, ColumnMapping columnMapping){
        if(columnMapping.getAssociatedFiled().contains((">"))){
            String parentKey = columnMapping.getAssociatedFiled().split(">")[0];
            return itemMap.get(parentKey).toString();
        }
        return itemMap.get(columnMapping.getAssociatedFiled()).toString();
    }

    /**
     * 确保ID字段始终排在最前面
     *
     * @param set 原始字段集合
     * @return ID字段在最前面的有序集合
     */
    public static Set<String> ensureIdFirst(Set<String> set) {
        List<String> list = new ArrayList<>();

        // 先添加id字段（多种可能的ID字段名）
        for (String idField : Arrays.asList("id", "_attr_id", "ID")) {
            if (set.contains(idField)) {
                list.add(idField);
            }
        }

        // 再添加其他字段
        for (String field : set) {
            if (!list.contains(field)) {
                list.add(field);
            }
        }

        return new LinkedHashSet<>(list);
    }

    public static Set<String> reorderIfNeeded(Set<String> set, String a, String b) {
        if (set.contains(a) && set.contains(b)) {
            List<String> list = new ArrayList<>(set);
            int indexA = list.indexOf(a);
            int indexB = list.indexOf(b);

            if (indexA > indexB) {
                list.set(indexA, b);
                list.set(indexB, a);
            }

            return new LinkedHashSet<>(list); // 总是返回有序结果
        }
        return new LinkedHashSet<>(set); // 返回新的 LinkedHashSet 保证一致性
    }

}
