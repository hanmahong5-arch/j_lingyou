package red.jiuzhou.server.model;

import java.sql.Timestamp;

/**
 * 服务器配置文件实体
 * 记录服务器启动时实际加载的 XML 文件 - "文件层的唯一真理"
 */
public class ServerConfigFile {
    private Integer id;

    // 文件标识
    private String fileName;          // XML文件名（不含路径）
    private String filePath;          // 完整文件路径
    private String tableName;         // 对应的数据库表名

    // 服务器加载信息
    private Boolean isServerLoaded;   // 是否被服务器加载
    private Integer loadPriority;     // 加载优先级（1=核心，2=重要，3=一般）
    private String serverModule;      // 所属服务器模块

    // 文件元数据
    private String fileCategory;      // 文件分类
    private String fileEncoding;      // 文件编码
    private Long fileSize;            // 文件大小

    // 验证信息
    private Timestamp lastValidationTime;
    private String validationStatus;  // valid/invalid/missing
    private String validationErrors;  // JSON格式

    // 依赖关系
    private String dependsOn;         // JSON数组
    private String referencedBy;      // JSON数组

    // 设计师标注
    private String designerNotes;
    private Boolean isCritical;       // 是否核心配置文件
    private Boolean isDeprecated;     // 是否已废弃

    // 统计信息
    private Integer importCount;
    private Integer exportCount;
    private Timestamp lastImportTime;
    private Timestamp lastExportTime;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    /**
     * 加载优先级枚举
     */
    public enum LoadPriority {
        CRITICAL(1, "核心配置"),
        IMPORTANT(2, "重要配置"),
        NORMAL(3, "一般配置");

        private final int level;
        private final String description;

        LoadPriority(int level, String description) {
            this.level = level;
            this.description = description;
        }

        public int getLevel() { return level; }
        public String getDescription() { return description; }
    }

    /**
     * 验证状态枚举
     */
    public enum ValidationStatus {
        VALID("有效"),
        INVALID("无效"),
        MISSING("缺失"),
        UNKNOWN("未知");

        private final String description;

        ValidationStatus(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    public ServerConfigFile() {
        this.isServerLoaded = false;
        this.loadPriority = LoadPriority.NORMAL.getLevel();
        this.isCritical = false;
        this.isDeprecated = false;
        this.importCount = 0;
        this.exportCount = 0;
    }

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public Boolean getIsServerLoaded() { return isServerLoaded; }
    public void setIsServerLoaded(Boolean isServerLoaded) { this.isServerLoaded = isServerLoaded; }

    public Integer getLoadPriority() { return loadPriority; }
    public void setLoadPriority(Integer loadPriority) { this.loadPriority = loadPriority; }

    public String getServerModule() { return serverModule; }
    public void setServerModule(String serverModule) { this.serverModule = serverModule; }

    public String getFileCategory() { return fileCategory; }
    public void setFileCategory(String fileCategory) { this.fileCategory = fileCategory; }

    public String getFileEncoding() { return fileEncoding; }
    public void setFileEncoding(String fileEncoding) { this.fileEncoding = fileEncoding; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public Timestamp getLastValidationTime() { return lastValidationTime; }
    public void setLastValidationTime(Timestamp lastValidationTime) { this.lastValidationTime = lastValidationTime; }

    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }

    public String getValidationErrors() { return validationErrors; }
    public void setValidationErrors(String validationErrors) { this.validationErrors = validationErrors; }

    public String getDependsOn() { return dependsOn; }
    public void setDependsOn(String dependsOn) { this.dependsOn = dependsOn; }

    public String getReferencedBy() { return referencedBy; }
    public void setReferencedBy(String referencedBy) { this.referencedBy = referencedBy; }

    public String getDesignerNotes() { return designerNotes; }
    public void setDesignerNotes(String designerNotes) { this.designerNotes = designerNotes; }

    public Boolean getIsCritical() { return isCritical; }
    public void setIsCritical(Boolean isCritical) { this.isCritical = isCritical; }

    public Boolean getIsDeprecated() { return isDeprecated; }
    public void setIsDeprecated(Boolean isDeprecated) { this.isDeprecated = isDeprecated; }

    public Integer getImportCount() { return importCount; }
    public void setImportCount(Integer importCount) { this.importCount = importCount; }

    public Integer getExportCount() { return exportCount; }
    public void setExportCount(Integer exportCount) { this.exportCount = exportCount; }

    public Timestamp getLastImportTime() { return lastImportTime; }
    public void setLastImportTime(Timestamp lastImportTime) { this.lastImportTime = lastImportTime; }

    public Timestamp getLastExportTime() { return lastExportTime; }
    public void setLastExportTime(Timestamp lastExportTime) { this.lastExportTime = lastExportTime; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "ServerConfigFile{" +
                "fileName='" + fileName + '\'' +
                ", tableName='" + tableName + '\'' +
                ", isServerLoaded=" + isServerLoaded +
                ", loadPriority=" + loadPriority +
                ", isCritical=" + isCritical +
                '}';
    }
}
