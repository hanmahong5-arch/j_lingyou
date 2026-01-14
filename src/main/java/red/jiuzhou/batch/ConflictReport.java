package red.jiuzhou.batch;

import java.util.Collections;
import java.util.List;

/**
 * Conflict Detection Report
 *
 * Stores the result of pre-checking for primary key conflicts
 *
 * @author Claude AI
 * @date 2026-01-14
 */
public class ConflictReport {

    private final int conflictCount;
    private final List<String> conflictKeys;

    public ConflictReport(int conflictCount, List<String> conflictKeys) {
        this.conflictCount = conflictCount;
        this.conflictKeys = conflictKeys != null ? conflictKeys : Collections.emptyList();
    }

    /**
     * Check if any conflicts were found
     */
    public boolean hasConflicts() {
        return conflictCount > 0;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public List<String> getConflictKeys() {
        return Collections.unmodifiableList(conflictKeys);
    }

    /**
     * Get a preview of conflict keys (first N items)
     */
    public List<String> getPreviewKeys(int limit) {
        if (conflictKeys.size() <= limit) {
            return getConflictKeys();
        }
        return conflictKeys.subList(0, limit);
    }

    /**
     * Get summary message for logging
     */
    public String getSummary() {
        if (!hasConflicts()) {
            return "无冲突";
        }

        String preview = String.join(", ", getPreviewKeys(5));
        String more = conflictKeys.size() > 5 ? " ..." : "";

        return String.format("发现 %d 条重复记录：%s%s",
            conflictCount, preview, more);
    }

    @Override
    public String toString() {
        return getSummary();
    }
}
