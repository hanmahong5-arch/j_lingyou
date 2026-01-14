package red.jiuzhou.batch;

import java.util.List;

/**
 * Data Conflict Exception
 *
 * Thrown when primary key conflicts are detected during XML import
 * Provides user-friendly error messages for game designers
 *
 * @author Claude AI
 * @date 2026-01-14
 */
public class DataConflictException extends RuntimeException {

    private final int conflictCount;
    private final List<String> conflictKeys;

    /**
     * Constructor with conflict details
     *
     * @param message User-friendly error message
     * @param conflictCount Number of conflicting records
     * @param conflictKeys List of conflicting primary key values
     */
    public DataConflictException(String message, int conflictCount, List<String> conflictKeys) {
        super(message);
        this.conflictCount = conflictCount;
        this.conflictKeys = conflictKeys;
    }

    /**
     * Constructor with message and cause
     */
    public DataConflictException(String message, Throwable cause) {
        super(message, cause);
        this.conflictCount = 0;
        this.conflictKeys = null;
    }

    /**
     * Simple constructor
     */
    public DataConflictException(String message) {
        super(message);
        this.conflictCount = 0;
        this.conflictKeys = null;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public List<String> getConflictKeys() {
        return conflictKeys;
    }

    /**
     * Check if this exception contains conflict details
     */
    public boolean hasConflictDetails() {
        return conflictKeys != null && !conflictKeys.isEmpty();
    }
}
