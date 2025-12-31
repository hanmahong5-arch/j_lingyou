package red.jiuzhou.ui.components;

/**
 * 文件状态信息模型
 *
 * 存储文件的各种状态信息，用于在菜单树节点上显示状态徽章
 *
 * @author Claude
 * @version 1.0
 */
public class FileStatusInfo {

    // ==================== 导入状态 ====================
    public enum ImportStatus {
        /** 未导入 */
        NOT_IMPORTED("❌", "未导入到数据库"),
        /** 已导入 */
        IMPORTED("✅", "已导入且数据一致"),
        /** 有差异 */
        MODIFIED("⚠️", "已导入但文件有修改"),
        /** 检测中 */
        CHECKING("🔄", "正在检测状态..."),
        /** 未知 */
        UNKNOWN("❓", "状态未知");

        private final String icon;
        private final String tooltip;

        ImportStatus(String icon, String tooltip) {
            this.icon = icon;
            this.tooltip = tooltip;
        }

        public String getIcon() { return icon; }
        public String getTooltip() { return tooltip; }
    }

    // ==================== DDL状态 ====================
    public enum DdlStatus {
        /** DDL已生成 */
        GENERATED("💾", "DDL已生成"),
        /** 待生成 */
        PENDING("📝", "待生成DDL"),
        /** 未知 */
        UNKNOWN("", "DDL状态未知");

        private final String icon;
        private final String tooltip;

        DdlStatus(String icon, String tooltip) {
            this.icon = icon;
            this.tooltip = tooltip;
        }

        public String getIcon() { return icon; }
        public String getTooltip() { return tooltip; }
    }

    // ==================== 引用完整性 ====================
    public enum ReferenceStatus {
        /** 引用完整 */
        COMPLETE("🔗", "所有引用完整"),
        /** 有断链 */
        BROKEN("⛓️", "存在断链引用"),
        /** 未检查 */
        UNCHECKED("", "引用未检查");

        private final String icon;
        private final String tooltip;

        ReferenceStatus(String icon, String tooltip) {
            this.icon = icon;
            this.tooltip = tooltip;
        }

        public String getIcon() { return icon; }
        public String getTooltip() { return tooltip; }
    }

    // ==================== 服务器加载优先级 ====================
    public enum ServerPriority {
        /** 核心配置（优先级1） */
        CRITICAL(1, "🚀", "服务器核心配置"),
        /** 重要配置（优先级2） */
        IMPORTANT(2, "⭐", "重要配置"),
        /** 普通配置（优先级3） */
        NORMAL(3, "📋", "普通配置"),
        /** 非服务器配置 */
        NONE(0, "", "非服务器加载文件");

        private final int level;
        private final String icon;
        private final String tooltip;

        ServerPriority(int level, String icon, String tooltip) {
            this.level = level;
            this.icon = icon;
            this.tooltip = tooltip;
        }

        public int getLevel() { return level; }
        public String getIcon() { return icon; }
        public String getTooltip() { return tooltip; }

        public static ServerPriority fromLevel(int level) {
            return switch (level) {
                case 1 -> CRITICAL;
                case 2 -> IMPORTANT;
                case 3 -> NORMAL;
                default -> NONE;
            };
        }
    }

    // ==================== 字段 ====================
    private final String tableName;
    private final String filePath;
    private ImportStatus importStatus;
    private DdlStatus ddlStatus;
    private ReferenceStatus referenceStatus;
    private ServerPriority serverPriority;

    // 额外信息
    private long recordCount;
    private String encoding;
    private boolean hasBom;
    private boolean roundTripValidated;
    private int dependsOnCount;
    private int referencedByCount;
    private String serverModule;

    // 缓存时间戳
    private long lastUpdated;

    // ==================== 构造函数 ====================
    public FileStatusInfo(String tableName, String filePath) {
        this.tableName = tableName;
        this.filePath = filePath;
        this.importStatus = ImportStatus.UNKNOWN;
        this.ddlStatus = DdlStatus.UNKNOWN;
        this.referenceStatus = ReferenceStatus.UNCHECKED;
        this.serverPriority = ServerPriority.NONE;
        this.lastUpdated = System.currentTimeMillis();
    }

    // ==================== 快捷方法 ====================

    /**
     * 获取导入状态图标
     */
    public String getImportIcon() {
        return importStatus.getIcon();
    }

    /**
     * 获取导入状态提示
     */
    public String getImportTooltip() {
        String base = importStatus.getTooltip();
        if (importStatus == ImportStatus.IMPORTED && recordCount > 0) {
            return base + String.format(" (%,d条记录)", recordCount);
        }
        return base;
    }

    /**
     * 获取DDL状态图标
     */
    public String getDdlIcon() {
        return ddlStatus.getIcon();
    }

    /**
     * 获取DDL状态提示
     */
    public String getDdlTooltip() {
        return ddlStatus.getTooltip();
    }

    /**
     * 获取引用状态图标
     */
    public String getReferenceIcon() {
        return referenceStatus.getIcon();
    }

    /**
     * 获取引用状态提示
     */
    public String getReferenceTooltip() {
        return referenceStatus.getTooltip();
    }

    /**
     * 获取服务器优先级图标
     */
    public String getPriorityIcon() {
        return serverPriority.getIcon();
    }

    /**
     * 获取服务器优先级提示
     */
    public String getPriorityTooltip() {
        String base = serverPriority.getTooltip();
        if (serverModule != null && !serverModule.isEmpty()) {
            base += " | 模块: " + serverModule;
        }
        if (dependsOnCount > 0) {
            base += " | 依赖: " + dependsOnCount + "个文件";
        }
        if (referencedByCount > 0) {
            base += " | 被依赖: " + referencedByCount + "个文件";
        }
        return base;
    }

    /**
     * 是否为服务器加载的文件
     */
    public boolean isServerLoaded() {
        return serverPriority != ServerPriority.NONE;
    }

    /**
     * 获取组合状态徽章文本
     */
    public String getBadgeText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append(importStatus.getIcon());

        if (ddlStatus != DdlStatus.UNKNOWN) {
            sb.append(ddlStatus.getIcon());
        }

        if (isServerLoaded()) {
            sb.append(serverPriority.getIcon());
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * 获取完整的徽章提示文本
     */
    public String getFullTooltip() {
        StringBuilder sb = new StringBuilder();
        sb.append("📄 ").append(tableName).append("\n");
        sb.append("─────────────────\n");
        sb.append("导入状态: ").append(getImportTooltip()).append("\n");

        if (ddlStatus != DdlStatus.UNKNOWN) {
            sb.append("DDL状态: ").append(getDdlTooltip()).append("\n");
        }

        if (isServerLoaded()) {
            sb.append("服务器: ").append(getPriorityTooltip()).append("\n");
        }

        if (encoding != null && !encoding.isEmpty()) {
            sb.append("编码: ").append(encoding);
            if (hasBom) sb.append(" (BOM)");
            sb.append("\n");
        }

        if (roundTripValidated) {
            sb.append("往返验证: ✅ 通过\n");
        }

        return sb.toString();
    }

    // ==================== Getters and Setters ====================

    public String getTableName() { return tableName; }
    public String getFilePath() { return filePath; }

    public ImportStatus getImportStatus() { return importStatus; }
    public void setImportStatus(ImportStatus importStatus) {
        this.importStatus = importStatus;
        this.lastUpdated = System.currentTimeMillis();
    }

    public DdlStatus getDdlStatus() { return ddlStatus; }
    public void setDdlStatus(DdlStatus ddlStatus) {
        this.ddlStatus = ddlStatus;
        this.lastUpdated = System.currentTimeMillis();
    }

    public ReferenceStatus getReferenceStatus() { return referenceStatus; }
    public void setReferenceStatus(ReferenceStatus referenceStatus) {
        this.referenceStatus = referenceStatus;
        this.lastUpdated = System.currentTimeMillis();
    }

    public ServerPriority getServerPriority() { return serverPriority; }
    public void setServerPriority(ServerPriority serverPriority) {
        this.serverPriority = serverPriority;
        this.lastUpdated = System.currentTimeMillis();
    }

    public int getPriority() {
        return serverPriority.getLevel();
    }

    public long getRecordCount() { return recordCount; }
    public void setRecordCount(long recordCount) { this.recordCount = recordCount; }

    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }

    public boolean isHasBom() { return hasBom; }
    public void setHasBom(boolean hasBom) { this.hasBom = hasBom; }

    public boolean isRoundTripValidated() { return roundTripValidated; }
    public void setRoundTripValidated(boolean roundTripValidated) {
        this.roundTripValidated = roundTripValidated;
    }

    public int getDependsOnCount() { return dependsOnCount; }
    public void setDependsOnCount(int dependsOnCount) { this.dependsOnCount = dependsOnCount; }

    public int getReferencedByCount() { return referencedByCount; }
    public void setReferencedByCount(int referencedByCount) { this.referencedByCount = referencedByCount; }

    public String getServerModule() { return serverModule; }
    public void setServerModule(String serverModule) { this.serverModule = serverModule; }

    public long getLastUpdated() { return lastUpdated; }

    /**
     * 检查缓存是否过期（默认5分钟）
     */
    public boolean isExpired() {
        return isExpired(5 * 60 * 1000L);
    }

    /**
     * 检查缓存是否过期
     * @param ttlMs 过期时间（毫秒）
     */
    public boolean isExpired(long ttlMs) {
        return System.currentTimeMillis() - lastUpdated > ttlMs;
    }

    @Override
    public String toString() {
        return String.format("FileStatusInfo{table=%s, import=%s, ddl=%s, priority=%s}",
            tableName, importStatus, ddlStatus, serverPriority);
    }
}
