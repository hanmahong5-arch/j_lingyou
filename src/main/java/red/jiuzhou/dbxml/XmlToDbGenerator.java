package red.jiuzhou.dbxml;

import cn.hutool.core.io.FileUtil;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import red.jiuzhou.ai.DashScopeBatchHelper;
import red.jiuzhou.util.AliyunTranslateUtil;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.util.FileEncodingDetector;
import red.jiuzhou.util.EncodingFallbackStrategy;
import red.jiuzhou.util.EncodingMetadataManager;
import red.jiuzhou.util.BomAwareFileReader;
import red.jiuzhou.validation.XmlFieldValidator;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
/**
 * @className: red.jiuzhou.dbxml.XmlToDbGenerator.java
 * @description: xml转db
 * @author: yanxq
 * @date:  2025-04-15 20:42
 * @version V1.0
 */
public class XmlToDbGenerator {

    private static final Logger log = LoggerFactory.getLogger(XmlToDbGenerator.class);

    private final TableConf table;
    private final Document document;
    private double progress;
    private String mapType;

    private final List<Map<String, String>> mainTabList = new ArrayList<>();
    //private final Map<String, List<Map<String, String>>> subTabList = new HashMap<>();
    private final  Map<String, List<Map<String, String>>> subTabList = new TreeMap<>(
            Comparator.comparingInt(String::length).thenComparing(String::compareTo)
    );

    private static List<String> worldSpecialTabNames;

    /**
     * 延迟初始化world特殊表名列表
     */
    private static List<String> getWorldSpecialTabNames() {
        if (worldSpecialTabNames == null) {
            synchronized (XmlToDbGenerator.class) {
                if (worldSpecialTabNames == null) {
                    try {
                        String prop = YamlUtils.getProperty("world.specialTabName");
                        if (prop != null && !prop.isEmpty()) {
                            worldSpecialTabNames = Arrays.asList(prop.split(","));
                        } else {
                            worldSpecialTabNames = Collections.emptyList();
                        }
                    } catch (Exception e) {
                        log.warn("无法加载world.specialTabName配置，使用空列表", e);
                        worldSpecialTabNames = Collections.emptyList();
                    }
                }
            }
        }
        return worldSpecialTabNames;
    }

    public XmlToDbGenerator(String tabName, String mapType, String filePath, String tabFielPath) {
        this.mapType = mapType;
        try {
            TableConf table = TabConfLoad.getTale(tabName, tabFielPath);
            if (table == null) {
                throw new RuntimeException("找不到表配置信息：" + tabName);
            }
            table.chk();
            this.table = table;
            String xmlFilePath = table.getFilePath();
            if(mapType != null){
                String parent = FileUtil.getParent(xmlFilePath, 1);
                xmlFilePath = parent + File.separator + mapType + File.separator + FileUtil.getName(xmlFilePath);
            }
            if(filePath != null){
                xmlFilePath = filePath;
            }
            log.info("xml文件路径：{}", xmlFilePath);
            File xmlFile = new File(xmlFilePath);

            // ========== 透明编码转换层：智能编码检测（带降级策略）==========
            FileEncodingDetector.EncodingInfo encoding = EncodingFallbackStrategy.detectWithFallback(xmlFile, tabName);
            int confidence = EncodingFallbackStrategy.calculateConfidence(encoding, xmlFile);
            log.info("✅ 检测到文件编码: {} (可信度: {}%)", encoding, confidence);

            // 保存编码元数据（确保导出时能还原），支持 World 表的 mapType 区分
            EncodingMetadataManager.saveMetadata(tabName, mapType != null ? mapType : "", xmlFile, encoding);
            // =================================================

            // ========== 使用 BOM-aware 读取器，避免 "前言中不允许有内容" 错误 ==========
            String fileContent = BomAwareFileReader.readString(xmlFile, encoding);
            log.debug("文件内容长度: {} 字符, 是否以BOM开头: {}",
                     fileContent.length(), BomAwareFileReader.startsWithBOM(fileContent));
            // ==========================================================================

            this.document = DocumentHelper.parseText(fileContent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public void xmlTodb(String aiModule, List<String> selectedColumns) {
        xmlToDb(table, document);
        List<String> allTableNameList = table.getAllTableNameList();
        if("world".equals(table.getTableName())){
            allTableNameList = allTableNameList.stream()
                    .map(tabName -> tabName + " where mapTp = '" + mapType + "'")
                    .collect(Collectors.toList());
        }
        // 按字符串长度倒序排序
        allTableNameList.sort(Comparator.comparingInt(String::length).reversed());

        // 计算总数据量
        int totalMain = mainTabList.size();
        int totalSub = subTabList.values().stream().mapToInt(List::size).sum();
        int totalRecords = totalMain + totalSub;
        final int[] processedRecords = {0};

        System.out.printf("开始数据导入，总记录数: %d (主表: %d, 子表: %d)\n", totalRecords, totalMain, totalSub);

        // ==================== 数据验证（在事务外进行，基于服务器日志分析）====================
        log.info("开始数据验证...");
        XmlFieldValidator.ValidationResult mainValidation = XmlFieldValidator.validateBatch(table.getTableName(), mainTabList);

        // 验证所有子表
        for (Map.Entry<String, List<Map<String, String>>> entry : subTabList.entrySet()) {
            XmlFieldValidator.ValidationResult subValidation =
                XmlFieldValidator.validateBatch(entry.getKey(), entry.getValue());
            mainValidation.getErrors().addAll(subValidation.getErrors());
            mainValidation.getWarnings().addAll(subValidation.getWarnings());
        }

        // 处理验证结果
        if (mainValidation.hasWarnings()) {
            log.warn("数据验证发现警告:\n{}", mainValidation.getSummary());
        }

        if (mainValidation.hasErrors()) {
            String errorMsg = mainValidation.getSummary();
            log.error("数据验证失败:\n{}", errorMsg);
            throw new RuntimeException("数据验证失败，中止导入。请修正以下错误后重试:\n" + errorMsg);
        }

        log.info("✅ 数据验证通过");
        // =================================================================

        // AI处理字段（在事务外进行）
        if(selectedColumns != null){
            log.info("selectedColumns：{}", selectedColumns.toString());
            selectedColumns.forEach(column -> {
                DashScopeBatchHelper.rewriteField(mainTabList, table.getTableName(), column, aiModule);

                int[] len = {DatabaseUtil.getColumnLength(table.getTableName(), column)};
                mainTabList.forEach(itemMap -> {
                    String val = itemMap.get(column);
                    if(val != null && val.length() > len[0]){
                        try {
                            DatabaseUtil.ensureVarcharLengthIfNeeded(table.getTableName(), column, val.length());
                            len[0] = val.length();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            });
        }

        // 使用统一事务确保数据一致性（原子性：全部成功或全部回滚）
        TransactionStatus globalTransaction = DatabaseUtil.beginTransaction();
        try {
            // 1. 在事务内删除旧数据
            final List<String> finalTableList = allTableNameList;
            finalTableList.forEach(DatabaseUtil::delTable);

            // 2. 插入主表数据
            List<List<Map<String, String>>> mainBatches = splitList(mainTabList, 1000);
            for (List<Map<String, String>> batch : mainBatches) {
                DatabaseUtil.batchInsert(table.getTableName(), batch);
                processedRecords[0] += batch.size();
                printProgress(processedRecords[0], totalRecords);
            }

            // 3. 插入子表数据
            for (Map.Entry<String, List<Map<String, String>>> entry : subTabList.entrySet()) {
                String tableName = entry.getKey();
                List<Map<String, String>> list = entry.getValue();
                for (List<Map<String, String>> batch : splitList(list, 1000)) {
                    DatabaseUtil.batchInsert(tableName, batch);
                    processedRecords[0] += batch.size();
                    printProgress(processedRecords[0], totalRecords);
                }
            }

            // 4. 全部成功，提交事务
            DatabaseUtil.commitTransaction(globalTransaction);
            System.out.println("数据导入完成！");

        } catch (Exception e) {
            // 任何失败都回滚，保证数据一致性
            log.error("导入失败，回滚事务: {}", e.getMessage());
            DatabaseUtil.rollbackTransaction(globalTransaction);
            throw new RuntimeException("数据导入失败，已回滚: " + e.getMessage(), e);
        }
    }

    public double getProgress() {
        return progress;
    }

    /**
     * 计算并打印进度
     */
    private void printProgress(int processed, int total) {
        progress = (double) processed / total;
        //System.out.printf("进度: %d/%d (%.2f%%)\n", processed, total, progress);
    }

    private void xmlToDb(TableConf table, Document document) {
        try {
            List<Element> elements = null;
            if(table.getXmlItemTag() == null || table.getXmlItemTag().isEmpty()){
                Element rootElement = document.getRootElement();
                elements = new ArrayList<>();
                elements.add(rootElement);
            }else{
                elements = document.getRootElement().elements(table.getXmlItemTag());
            }

            for (Element element : elements) {

                Iterator<Element> subEle = element.elementIterator();
                // 使用 LinkedHashMap 保证字段顺序稳定（导入导出对称性）
                Map<String, String> mainMap = new LinkedHashMap<>();
                while (subEle.hasNext()) {
                    Element subElement = subEle.next();
                    if (!subElement.elements().isEmpty()) {
                        //log.info("subEleName:::::{}", subElement.getName());
                        generateSubSql(element, subElement, table.getColumnMappingByXmlTag(subElement.getName()), mainMap);
                    } else {
                        // 检查是否有 null="true" 属性标记（往返一致性）
                        String nullAttr = subElement.attributeValue("null");
                        if ("true".equals(nullAttr)) {
                            mainMap.put(subElement.getName(), null);
                        } else {
                            mainMap.put(subElement.getName(), subElement.getText());
                        }
                    }
                }
                // 修复：遍历所有属性（之前只取第一个导致属性丢失）
                element.attributeIterator().forEachRemaining(attr -> {
                    mainMap.put("_attr_" + attr.getName(), attr.getValue());
                });
                if("world".equals(table.getTableName())){
                    mapType = element.elementText("name");
                    mainMap.put("mapTp", mapType);
                }
                mainTabList.add(mainMap);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void generateSubSql(Element parentElement, Element element, ColumnMapping columnMaping, Map<String, String> parentMap) {
        if(columnMaping.getAddDataNode().contains(":")){
            String[] splitNodes = columnMaping.getAddDataNode().split(":");
            for (int i = 1; i < splitNodes.length; i++) {
                element = element.element(splitNodes[i]);
            }
        }
        if (!columnMaping.getAddDataNode().trim().isEmpty()) {

            List<Element> elements = element.elements(columnMaping.getXmlTag());
            elements.forEach(oneEle -> {
                // 使用 LinkedHashMap 保证字段顺序稳定
                Map<String, String> subMap = new LinkedHashMap<>();
                List<Map<String, String>> subList = subTabList.getOrDefault(columnMaping.getTableName(), new ArrayList<>());
                Iterator<Element> subEle = oneEle.elementIterator();
                if(!columnMaping.getAssociatedFiled().contains(">")){
                    if(parentMap.get(columnMaping.getAssociatedFiled()) == null && mapType != null){
                        subMap.put(columnMaping.getAssociatedFiled(), mapType);
                    }else{
                        subMap.put(columnMaping.getAssociatedFiled(), parentMap.get(columnMaping.getAssociatedFiled()));
                    }
                }else{
                    String[] splitNodes = columnMaping.getAssociatedFiled().split(">");
                    if(parentMap.get(splitNodes[0]) == null && mapType != null){
                        subMap.put(splitNodes[1], mapType);
                    }else{
                        subMap.put(splitNodes[1], parentMap.get(splitNodes[0]));
                    }
                }
                //特殊处理，表加id
                if(getWorldSpecialTabNames().contains(columnMaping.getTableName())){
                    subMap.put("world__id",UUID.randomUUID().toString());
                }
                if("world".equals(table.getTableName())){
                    subMap.put("mapTp", mapType);
                }
                while (subEle.hasNext()) {
                    Element subElement = subEle.next();
                    if (!subElement.elements().isEmpty()) {
                        generateSubSql(oneEle, subElement, columnMaping.getColumnMappingByXmlTag(subElement.getName()), subMap);
                    } else {

                        // 检查是否有 null="true" 属性标记
                        String nullAttr = subElement.attributeValue("null");
                        if ("true".equals(nullAttr)) {
                            subMap.put(subElement.getName(), null);
                        } else if(subMap.get(subElement.getName())!= null){
                            subMap.put(subElement.getName(), subMap.get(subElement.getName()) + "!@#" + subElement.getText());
                        }else{
                            subMap.put(subElement.getName(), subElement.getText());
                        }
                        if(subElement.elements().isEmpty() && !subElement.attributes().isEmpty()){
                            subElement.attributes().forEach( attribute -> {
                                // 跳过 null 标记属性
                                if (!"null".equals(attribute.getName())) {
                                    subMap.put("_attr__" +subElement.getName() + "__" + attribute.getName(), attribute.getText());
                                }
                            });
                        }
                    }

                }
                oneEle.attributeIterator().forEachRemaining(attr -> {
                    subMap.put("_attr_"+attr.getName(), attr.getValue());
                });

                subList.add(subMap);
                subTabList.put(columnMaping.getTableName(), subList);
            });
        }else{
            // 使用 LinkedHashMap 保证字段顺序稳定
            Map<String, String> subMap = new LinkedHashMap<>();
            List<Map<String, String>> subList = subTabList.getOrDefault(columnMaping.getTableName(), new ArrayList<>());
            Iterator<Element> subEle = element.elementIterator();
            if(!columnMaping.getAssociatedFiled().contains(">")){
                if(parentMap.get(columnMaping.getAssociatedFiled()) == null && mapType != null){
                    subMap.put(columnMaping.getAssociatedFiled(), mapType);
                }else{
                    subMap.put(columnMaping.getAssociatedFiled(), parentMap.get(columnMaping.getAssociatedFiled()));
                }
            }else{
                String[] splitNodes = columnMaping.getAssociatedFiled().split(">");
                if(parentMap.get(splitNodes[0]) == null && mapType != null){
                    subMap.put(splitNodes[1], mapType);
                }else{
                    subMap.put(splitNodes[1], parentMap.get(splitNodes[0]));
                }
            }
            //特殊处理，表加id
            if(getWorldSpecialTabNames().contains(columnMaping.getTableName())){
                subMap.put("world__id",UUID.randomUUID().toString());
            }
            if("world".equals(table.getTableName())){
                subMap.put("mapTp", mapType);
            }
            while (subEle.hasNext()) {
                Element subElement = subEle.next();
                if (!subElement.elements().isEmpty()) {
                    generateSubSql(element, subElement, columnMaping.getColumnMappingByXmlTag(subElement.getName()), subMap);
                } else {
                    // 检查是否有 null="true" 属性标记
                    String nullAttr = subElement.attributeValue("null");
                    if ("true".equals(nullAttr)) {
                        subMap.put(subElement.getName(), null);
                    } else if(subMap.get(subElement.getName())!= null){
                        subMap.put(subElement.getName(), subMap.get(subElement.getName()) + "!@#" + subElement.getText());
                    }else{
                        subMap.put(subElement.getName(), subElement.getText());
                    }
                    if(subElement.elements().isEmpty() && !subElement.attributes().isEmpty()){
                        subElement.attributes().forEach( attribute -> {
                            // 跳过 null 标记属性
                            if (!"null".equals(attribute.getName())) {
                                subMap.put("_attr__" +subElement.getName() + "__" + attribute.getName(), attribute.getText());
                            }
                        });
                    }
                }
            }
            element.attributeIterator().forEachRemaining(attr -> {
                subMap.put("_attr_"+attr.getName(), attr.getValue());
            });
            subList.add(subMap);
            subTabList.put(columnMaping.getTableName(), subList);
        }
    }

    public static <T> List<List<T>> splitList(List<T> list, int chunkSize) {
        List<List<T>> result = new ArrayList<>();
        Iterator<T> iterator = list.iterator();

        while (iterator.hasNext()) {
            List<T> chunk = new ArrayList<>(chunkSize);
            for (int i = 0; i < chunkSize && iterator.hasNext(); i++) {
                chunk.add(iterator.next());
            }
            result.add(chunk);
        }
        return result;
    }
}
