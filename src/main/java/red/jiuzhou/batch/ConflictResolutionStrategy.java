package red.jiuzhou.batch;

/**
 * Conflict Resolution Strategy Enum
 *
 * Defines how to handle primary key conflicts during XML import
 *
 * @author Claude AI
 * @date 2026-01-14
 */
public enum ConflictResolutionStrategy {

    /**
     * REPLACE_UPDATE (Recommended)
     * Use PostgreSQL UPSERT: INSERT ... ON CONFLICT DO UPDATE
     * If record exists, update it; otherwise insert new record
     */
    REPLACE_UPDATE("覆盖更新", "用XML中的新数据替换数据库中的旧数据", true),

    /**
     * SKIP_CONFLICT
     * Use PostgreSQL: INSERT ... ON CONFLICT DO NOTHING
     * Keep existing records, only insert new ones
     */
    SKIP_CONFLICT("跳过冲突", "保留数据库中的旧数据，只导入新记录", false),

    /**
     * CANCEL_IMPORT
     * Pre-detect conflicts and throw exception
     * Abort import if any conflict found
     */
    CANCEL_IMPORT("取消导入", "发现冲突时立即中止，不修改数据库", false),

    /**
     * SMART_MERGE (Advanced)
     * Compare timestamps and keep the newer record
     * Requires updated_at field in table
     */
    SMART_MERGE("智能合并", "比较时间戳，保留较新的记录", false);

    private final String displayName;
    private final String description;
    private final boolean isRecommended;

    ConflictResolutionStrategy(String displayName, String description, boolean isRecommended) {
        this.displayName = displayName;
        this.description = description;
        this.isRecommended = isRecommended;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRecommended() {
        return isRecommended;
    }

    /**
     * Get full description for UI display
     */
    public String getFullDescription() {
        String recommended = isRecommended ? "（推荐）" : "";
        return displayName + recommended + " - " + description;
    }
}
