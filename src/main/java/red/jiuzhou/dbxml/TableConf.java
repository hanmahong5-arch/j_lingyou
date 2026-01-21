package red.jiuzhou.dbxml;

import com.alibaba.fastjson.annotation.JSONField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/**
 * @className: red.jiuzhou.dbxml.TableConf.java
 * @description: 表配置
 * @author: yanxq
 * @date:  2025-04-15 20:41
 * @version V1.0
 */
public class TableConf {
    private static final Logger log = LoggerFactory.getLogger(DbToXmlGenerator.class);

    // ========== 索引缓存（性能优化：O(n) -> O(1)）==========
    // 使用 transient 避免序列化，使用 volatile 保证多线程可见性
    private transient volatile Map<String, ColumnMapping> columnMappingIndex;
    private transient volatile Map<String, ColumnMapping> xmlTagMappingIndex;
    private transient volatile List<String> listDbColumnListCache;

    @JSONField(name = "file_path")
    private String filePath;

    @JSONField(name = "table_name")
    private String tableName;

    @JSONField(name = "real_table_name")
    private String realTableName;

    @JSONField(name = "xml_root_tag")
    private String xmlRootTag;

    @JSONField(name = "xml_root_attr")
    private String xmlRootAttr;

    @JSONField(name = "xml_item_tag")
    private String xmlItemTag;

    @JSONField(name = "sql")
    private String sql;

    @JSONField(name = "change_fileds") // 注意 JSON 中是 `change_fileds`，如果是拼写错误可改为 `change_fields`
    private String changeFields;

    @JSONField(name = "list")
    private List<ColumnMapping> list;

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getXmlRootTag() { return xmlRootTag; }
    public void setXmlRootTag(String xmlRootTag) { this.xmlRootTag = xmlRootTag; }

    public String getXmlItemTag() { return xmlItemTag; }
    public void setXmlItemTag(String xmlItemTag) { this.xmlItemTag = xmlItemTag; }

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public String getChangeFields() { return changeFields; }
    public void setChangeFields(String changeFields) { this.changeFields = changeFields; }

    public List<ColumnMapping> getList() {
        if(list == null){
            list = new ArrayList<>();
        }
        return list;
    }
    public void setList(List<ColumnMapping> list) { this.list = list; }

    public String getXmlRootAttr() {
        return xmlRootAttr;
    }

    public void setXmlRootAttr(String xmlRootAttr) {
        this.xmlRootAttr = xmlRootAttr;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getRealTableName() {
        return realTableName;
    }

    public void setRealTableName(String realTableName) {
        this.realTableName = realTableName;
    }

    /**
     * Get primary key column name for this table configuration.
     * Uses the first DB column from the mapping list, or defaults to "id".
     * @return primary key column name
     */
    public String getPrimaryKey() {
        // Try to get first column from mapping as primary key
        if (list != null && !list.isEmpty()) {
            ColumnMapping firstMapping = list.get(0);
            String dbColumn = firstMapping.getDbColumn();
            if (StringUtils.hasLength(dbColumn)) {
                return dbColumn;
            }
        }
        // Default to "id" if no mapping available
        return "id";
    }

    /**
     * 获取所有数据库列名列表（带缓存）
     *
     * 优化：避免每次调用都创建新 ArrayList
     */
    public List<String> getListDbcolumnList() {
        if (listDbColumnListCache != null) {
            return listDbColumnListCache;
        }

        synchronized (this) {
            if (listDbColumnListCache == null) {
                List<String> dbColumnList = new ArrayList<>();
                getList().forEach(columnMapping -> {
                    if (!columnMapping.getAddDataNode().isEmpty()) {
                        if (columnMapping.getAddDataNode().contains(":")) {
                            String[] split = columnMapping.getAddDataNode().split(":");
                            dbColumnList.add(split[0]);
                        } else {
                            dbColumnList.add(columnMapping.getAddDataNode());
                        }
                    } else {
                        dbColumnList.add(columnMapping.getDbColumn());
                    }
                });
                listDbColumnListCache = dbColumnList;
            }
        }
        return listDbColumnListCache;
    }

    public List<String> getListXmlTagList() {

        return list.stream()
                .map(ColumnMapping::getXmlTag)
                .collect(Collectors.toList());
    }

    /**
     * 根据数据库列名获取 ColumnMapping（带索引缓存）
     *
     * 优化：O(n) -> O(1) 查询复杂度
     *
     * @param dbColumn 数据库列名或 addDataNode
     * @return 匹配的 ColumnMapping，未找到返回 null
     */
    public ColumnMapping getColumnMapping(String dbColumn) {
        if (dbColumn == null) return null;

        // 构建索引（双重检查锁定）
        if (columnMappingIndex == null) {
            synchronized (this) {
                if (columnMappingIndex == null) {
                    columnMappingIndex = buildColumnMappingIndex();
                }
            }
        }

        // 先尝试直接查找
        ColumnMapping result = columnMappingIndex.get(dbColumn);
        if (result != null) {
            return result;
        }

        // 回退到线性查找（处理包含 ":" 的特殊情况）
        for (ColumnMapping columnMapping : getList()) {
            String addDataNode = columnMapping.getAddDataNode();
            if (addDataNode.contains(dbColumn + ":")) {
                return columnMapping;
            }
        }
        return null;
    }

    /**
     * 根据 XML 标签获取 ColumnMapping（带索引缓存）
     *
     * 优化：O(n) -> O(1) 查询复杂度
     *
     * @param xmlTag XML 标签名
     * @return 匹配的 ColumnMapping，未找到返回 null
     */
    public ColumnMapping getColumnMappingByXmlTag(String xmlTag) {
        if (xmlTag == null) return null;

        // 构建索引（双重检查锁定）
        if (xmlTagMappingIndex == null) {
            synchronized (this) {
                if (xmlTagMappingIndex == null) {
                    xmlTagMappingIndex = buildXmlTagMappingIndex();
                }
            }
        }

        // 先尝试直接查找
        ColumnMapping result = xmlTagMappingIndex.get(xmlTag);
        if (result != null) {
            return result;
        }

        // 回退到线性查找（处理包含 ":" 的特殊情况）
        for (ColumnMapping columnMapping : getList()) {
            String addDataNode = columnMapping.getAddDataNode();
            if (addDataNode.contains(xmlTag + ":")) {
                return columnMapping;
            }
        }
        return null;
    }

    /**
     * 构建列名索引
     */
    private Map<String, ColumnMapping> buildColumnMappingIndex() {
        Map<String, ColumnMapping> index = new HashMap<>();
        for (ColumnMapping cm : getList()) {
            // 索引 addDataNode
            String addDataNode = cm.getAddDataNode();
            if (addDataNode != null && !addDataNode.trim().isEmpty()) {
                // 处理 "nodeName:suffix" 格式
                if (addDataNode.contains(":")) {
                    String[] parts = addDataNode.split(":");
                    index.put(parts[0], cm);
                } else {
                    index.put(addDataNode, cm);
                }
            }
            // 索引 dbColumn
            String dbColumn = cm.getDbColumn();
            if (dbColumn != null && !dbColumn.isEmpty()) {
                index.putIfAbsent(dbColumn, cm);
            }
        }
        return index;
    }

    /**
     * 构建 XML 标签索引
     */
    private Map<String, ColumnMapping> buildXmlTagMappingIndex() {
        Map<String, ColumnMapping> index = new HashMap<>();
        for (ColumnMapping cm : getList()) {
            // 索引 addDataNode
            String addDataNode = cm.getAddDataNode();
            if (addDataNode != null && !addDataNode.trim().isEmpty()) {
                if (addDataNode.contains(":")) {
                    String[] parts = addDataNode.split(":");
                    index.put(parts[0], cm);
                } else {
                    index.put(addDataNode, cm);
                }
            }
            // 索引 xmlTag
            String xmlTag = cm.getXmlTag();
            if (xmlTag != null && !xmlTag.isEmpty()) {
                index.putIfAbsent(xmlTag, cm);
            }
        }
        return index;
    }

    /**
     * 清除索引缓存（当列表变更时调用）
     */
    public void clearIndexCache() {
        columnMappingIndex = null;
        xmlTagMappingIndex = null;
        listDbColumnListCache = null;
    }

     public void chk(){
        if (!StringUtils.hasLength(this.getTableName())){
            throw new RuntimeException("tableName is null");
        }
        if (!StringUtils.hasLength(this.getXmlRootTag())){
            throw new RuntimeException("xmlRootTag is null");
        }
//        if (!StringUtils.hasLength(this.getXmlItemTag())){
//            throw new RuntimeException("xmlItemTag is null");
//        }
        if (!StringUtils.hasLength(this.getSql())){
            throw new RuntimeException("sql is null");
        }
        if(this.getList() != null){
            for (ColumnMapping columnMapping : this.getList()) {
                columnMapping.chk();
            }
        }
     }

     public List<String> getAllTableNameList(){
        if(this.getList() == null){
            ArrayList<String> tabNameList = new ArrayList<>();
            tabNameList.add(this.getTableName());
            return tabNameList;
        }
         List<String> tabNameList = this.getList().stream()
                 .map(ColumnMapping::getTableName)
                 .collect(Collectors.toList());
         List<List<String>> subTabNameList = this.getList().stream()
                 .map(ColumnMapping::getAllTableNameList)
                 .collect(Collectors.toList());
         subTabNameList.forEach(tabNameList::addAll);

         tabNameList.add(this.getTableName());
         // Using Stream API to remove duplicates
         return tabNameList.stream()
                 .distinct()
                 .collect(Collectors.toList());
         //return tabNameList;


     }

    @Override
    public String toString() {
        return "TableConf{" +
                "tableName='" + tableName + '\'' +
                ", xmlRootTag='" + xmlRootTag + '\'' +
                ", xmlItemTag='" + xmlItemTag + '\'' +
                ", sql='" + sql + '\'' +
                ", changeFields='" + changeFields + '\'' +
                ", list=" + list +
                '}';
    }
}