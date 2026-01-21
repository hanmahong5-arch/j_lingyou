package red.jiuzhou.ops.model;

/**
 * Aion 游戏存储过程分类
 *
 * 基于对 500+ 存储过程的分析，按功能模块分类
 *
 * @author yanxq
 * @date 2026-01-16
 */
public enum ProcedureCategory {

    /**
     * 角色管理
     * 包括: 创建、修改、删除角色，外观、名称、种族变更等
     */
    CHARACTER("角色管理", "Character Management", "👤"),

    /**
     * 社交系统
     * 包括: 好友、黑名单、私信、社交关系等
     */
    SOCIAL("社交系统", "Social System", "💬"),

    /**
     * 公会系统
     * 包括: 公会创建、管理、成员、仓库、历史等
     */
    GUILD("公会系统", "Guild System", "👥"),

    /**
     * 物品经济
     * 包括: 物品查询、添加、删除、背包、仓库、资产等
     */
    ITEM("物品经济", "Item & Economy", "💰"),

    /**
     * 拍卖系统
     * 包括: 上架、竞拍、购买、历史记录等
     */
    AUCTION("拍卖系统", "Auction House", "🏪"),

    /**
     * 战斗PVP
     * 包括: 排名、战绩、深渊点数、PVP统计等
     */
    COMBAT("战斗PVP", "Combat & PVP", "⚔️"),

    /**
     * 任务进度
     * 包括: 任务列表、进度、完成状态、奖励等
     */
    QUEST("任务进度", "Quest & Progress", "📜"),

    /**
     * 房屋系统
     * 包括: 房屋购买、装修、家具、宠物等
     */
    HOUSING("房屋系统", "Housing System", "🏠"),

    /**
     * 系统维护
     * 包括: 数据库优化、清理、备份、索引重建等
     */
    SYSTEM("系统维护", "System Maintenance", "⚙️"),

    /**
     * 鲸落系统
     * 玩家死亡后物品处理机制
     */
    WHALE_FALL("鲸落系统", "Whale Fall System", "🐋"),

    /**
     * 服务器管理
     * 服务器进程启动、停止、监控
     */
    SERVER_MANAGER("服务器管理", "Server Manager", "🖥️"),

    /**
     * 账号管理
     * 包括: 登录、注册、封禁、解封等
     */
    ACCOUNT("账号管理", "Account Management", "🔐"),

    /**
     * 邮件系统
     * 包括: 发送、接收、附件、删除等
     */
    MAIL("邮件系统", "Mail System", "📧"),

    /**
     * 其他
     * 未分类的存储过程
     */
    OTHER("其他", "Other", "📋");

    private final String displayName;
    private final String englishName;
    private final String icon;

    ProcedureCategory(String displayName, String englishName, String icon) {
        this.displayName = displayName;
        this.englishName = englishName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getIcon() {
        return icon;
    }

    /**
     * Get display string with icon
     */
    public String getDisplayWithIcon() {
        return icon + " " + displayName;
    }

    /**
     * Get all categories sorted by display order
     */
    public static ProcedureCategory[] displayOrder() {
        return new ProcedureCategory[]{
                SERVER_MANAGER,
                CHARACTER,
                SOCIAL,
                GUILD,
                ITEM,
                AUCTION,
                COMBAT,
                QUEST,
                HOUSING,
                ACCOUNT,
                MAIL,
                WHALE_FALL,
                SYSTEM,
                OTHER
        };
    }
}
