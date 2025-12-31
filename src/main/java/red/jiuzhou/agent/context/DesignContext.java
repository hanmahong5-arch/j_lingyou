package red.jiuzhou.agent.context;

import red.jiuzhou.analysis.aion.AionMechanismCategory;

import java.util.*;

/**
 * 设计上下文 - AI介入点的统一上下文容器
 *
 * 用于在每个AI介入点自动收集足够的上下文信息，
 * 让AI能够理解设计师当前的操作位置和意图。
 *
 * @author Claude
 * @version 1.0
 */
public class DesignContext {

    /**
     * 上下文位置类型
     */
    public enum ContextLocation {
        FILE,       // 文件级别（菜单树节点）
        TABLE,      // 表级别（数据库表）
        ROW,        // 行级别（表格行）
        FIELD,      // 字段级别（单元格）
        MECHANISM   // 机制级别（机制浏览器节点）
    }

    // ==================== 位置信息 ====================

    /** 上下文位置类型 */
    private ContextLocation location;

    /** 当前焦点路径（文件路径或表名） */
    private String focusPath;

    /** 当前焦点ID（记录ID或字段名） */
    private String focusId;

    /** 当前焦点值 */
    private Object focusValue;

    // ==================== 结构信息 ====================

    /** 表结构元数据 */
    private TableMetadata tableSchema;

    /** 被谁引用（入度） */
    private List<FieldReference> incomingRefs = new ArrayList<>();

    /** 引用了谁（出度） */
    private List<FieldReference> outgoingRefs = new ArrayList<>();

    // ==================== 语义信息 ====================

    /** 所属机制分类 */
    private AionMechanismCategory mechanism;

    /** 语义提示映射（游戏术语 -> SQL条件） */
    private Map<String, String> semanticHints = new HashMap<>();

    // ==================== 历史信息 ====================

    /** 最近的操作历史 */
    private List<String> recentOperations = new ArrayList<>();

    /** 相关文件列表 */
    private List<String> relatedFiles = new ArrayList<>();

    // ==================== 行数据（用于ROW级别） ====================

    /** 当前行数据 */
    private Map<String, Object> rowData;

    /** 表名 */
    private String tableName;

    // ==================== 构造器 ====================

    public DesignContext() {
    }

    public DesignContext(ContextLocation location) {
        this.location = location;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 从文件路径创建上下文
     */
    public static DesignContext fromFile(String filePath) {
        DesignContext ctx = new DesignContext(ContextLocation.FILE);
        ctx.setFocusPath(filePath);
        return ctx;
    }

    /**
     * 从表名创建上下文
     */
    public static DesignContext fromTable(String tableName) {
        DesignContext ctx = new DesignContext(ContextLocation.TABLE);
        ctx.setTableName(tableName);
        ctx.setFocusPath(tableName);
        return ctx;
    }

    /**
     * 从表格行创建上下文
     */
    public static DesignContext fromRow(String tableName, Map<String, Object> rowData) {
        DesignContext ctx = new DesignContext(ContextLocation.ROW);
        ctx.setTableName(tableName);
        ctx.setRowData(rowData);
        ctx.setFocusPath(tableName);
        // 尝试提取ID
        if (rowData.containsKey("id")) {
            ctx.setFocusId(String.valueOf(rowData.get("id")));
        }
        return ctx;
    }

    /**
     * 从机制分类创建上下文
     */
    public static DesignContext fromMechanism(AionMechanismCategory mechanism) {
        DesignContext ctx = new DesignContext(ContextLocation.MECHANISM);
        ctx.setMechanism(mechanism);
        ctx.setFocusPath(mechanism.name());
        return ctx;
    }

    // ==================== 转换为Prompt ====================

    /**
     * 将上下文转换为AI提示词格式
     */
    public String toPrompt() {
        StringBuilder sb = new StringBuilder();

        // 位置信息
        sb.append("=== 当前位置 ===\n");
        sb.append("类型: ").append(getLocationDescription()).append("\n");
        if (focusPath != null) {
            sb.append("路径: ").append(focusPath).append("\n");
        }
        if (focusId != null) {
            sb.append("ID: ").append(focusId).append("\n");
        }

        // 机制信息
        if (mechanism != null) {
            sb.append("\n=== 所属机制 ===\n");
            sb.append("分类: ").append(mechanism.getDisplayName()).append("\n");
            sb.append("描述: ").append(mechanism.getDescription()).append("\n");
        }

        // 表结构信息
        if (tableSchema != null) {
            sb.append("\n=== 表结构 ===\n");
            sb.append("表名: ").append(tableSchema.getTableName()).append("\n");
            sb.append("字段数: ").append(tableSchema.getColumns().size()).append("\n");
            sb.append("字段列表:\n");
            for (ColumnMetadata col : tableSchema.getColumns()) {
                sb.append("  - ").append(col.getName())
                  .append(" (").append(col.getType()).append(")")
                  .append(col.getComment() != null ? " // " + col.getComment() : "")
                  .append("\n");
            }
        }

        // 行数据
        if (rowData != null && !rowData.isEmpty()) {
            sb.append("\n=== 当前行数据 ===\n");
            for (Map.Entry<String, Object> entry : rowData.entrySet()) {
                sb.append("  ").append(entry.getKey()).append(": ")
                  .append(entry.getValue()).append("\n");
            }
        }

        // 引用关系
        if (!incomingRefs.isEmpty() || !outgoingRefs.isEmpty()) {
            sb.append("\n=== 引用关系 ===\n");
            if (!outgoingRefs.isEmpty()) {
                sb.append("引用了:\n");
                for (FieldReference ref : outgoingRefs) {
                    sb.append("  → ").append(ref.toString()).append("\n");
                }
            }
            if (!incomingRefs.isEmpty()) {
                sb.append("被引用:\n");
                for (FieldReference ref : incomingRefs) {
                    sb.append("  ← ").append(ref.toString()).append("\n");
                }
            }
        }

        // 相关文件
        if (!relatedFiles.isEmpty()) {
            sb.append("\n=== 相关文件 ===\n");
            for (String file : relatedFiles) {
                sb.append("  - ").append(file).append("\n");
            }
        }

        // 语义提示
        if (!semanticHints.isEmpty()) {
            sb.append("\n=== 游戏语义 ===\n");
            for (Map.Entry<String, String> hint : semanticHints.entrySet()) {
                sb.append("  ").append(hint.getKey()).append(" = ")
                  .append(hint.getValue()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * 获取位置类型的中文描述
     */
    private String getLocationDescription() {
        if (location == null) return "未知";
        return switch (location) {
            case FILE -> "文件";
            case TABLE -> "数据库表";
            case ROW -> "表格行";
            case FIELD -> "字段";
            case MECHANISM -> "游戏机制";
        };
    }

    /**
     * 获取简短的上下文摘要（用于UI显示）
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(getLocationDescription()).append("] ");

        if (mechanism != null) {
            sb.append(mechanism.getDisplayName()).append(" - ");
        }

        if (focusPath != null) {
            // 只显示文件名
            String fileName = focusPath;
            int lastSlash = Math.max(focusPath.lastIndexOf('/'), focusPath.lastIndexOf('\\'));
            if (lastSlash >= 0) {
                fileName = focusPath.substring(lastSlash + 1);
            }
            sb.append(fileName);
        }

        if (focusId != null) {
            sb.append(" #").append(focusId);
        }

        return sb.toString();
    }

    // ==================== Getters and Setters ====================

    public ContextLocation getLocation() {
        return location;
    }

    public void setLocation(ContextLocation location) {
        this.location = location;
    }

    public String getFocusPath() {
        return focusPath;
    }

    public void setFocusPath(String focusPath) {
        this.focusPath = focusPath;
    }

    public String getFocusId() {
        return focusId;
    }

    public void setFocusId(String focusId) {
        this.focusId = focusId;
    }

    public Object getFocusValue() {
        return focusValue;
    }

    public void setFocusValue(Object focusValue) {
        this.focusValue = focusValue;
    }

    public TableMetadata getTableSchema() {
        return tableSchema;
    }

    public void setTableSchema(TableMetadata tableSchema) {
        this.tableSchema = tableSchema;
    }

    public List<FieldReference> getIncomingRefs() {
        return incomingRefs;
    }

    public void setIncomingRefs(List<FieldReference> incomingRefs) {
        this.incomingRefs = incomingRefs;
    }

    public List<FieldReference> getOutgoingRefs() {
        return outgoingRefs;
    }

    public void setOutgoingRefs(List<FieldReference> outgoingRefs) {
        this.outgoingRefs = outgoingRefs;
    }

    public AionMechanismCategory getMechanism() {
        return mechanism;
    }

    public void setMechanism(AionMechanismCategory mechanism) {
        this.mechanism = mechanism;
    }

    public Map<String, String> getSemanticHints() {
        return semanticHints;
    }

    public void setSemanticHints(Map<String, String> semanticHints) {
        this.semanticHints = semanticHints;
    }

    public List<String> getRecentOperations() {
        return recentOperations;
    }

    public void setRecentOperations(List<String> recentOperations) {
        this.recentOperations = recentOperations;
    }

    public List<String> getRelatedFiles() {
        return relatedFiles;
    }

    public void setRelatedFiles(List<String> relatedFiles) {
        this.relatedFiles = relatedFiles;
    }

    public Map<String, Object> getRowData() {
        return rowData;
    }

    public void setRowData(Map<String, Object> rowData) {
        this.rowData = rowData;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 获取当前表名（tableName的别名方法）
     */
    public String getCurrentTableName() {
        return tableName;
    }

    /**
     * 设置当前表名（tableName的别名方法）
     */
    public void setCurrentTableName(String tableName) {
        this.tableName = tableName;
    }

    // ==================== 链式方法 ====================

    public DesignContext withMechanism(AionMechanismCategory mechanism) {
        this.mechanism = mechanism;
        return this;
    }

    public DesignContext withTableSchema(TableMetadata schema) {
        this.tableSchema = schema;
        return this;
    }

    public DesignContext addSemanticHint(String key, String value) {
        this.semanticHints.put(key, value);
        return this;
    }

    public DesignContext addRelatedFile(String file) {
        this.relatedFiles.add(file);
        return this;
    }

    public DesignContext addIncomingRef(FieldReference ref) {
        this.incomingRefs.add(ref);
        return this;
    }

    public DesignContext addOutgoingRef(FieldReference ref) {
        this.outgoingRefs.add(ref);
        return this;
    }

    @Override
    public String toString() {
        return "DesignContext{" +
                "location=" + location +
                ", focusPath='" + focusPath + '\'' +
                ", mechanism=" + mechanism +
                '}';
    }
}
