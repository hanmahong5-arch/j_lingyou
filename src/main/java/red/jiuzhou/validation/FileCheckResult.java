package red.jiuzhou.validation;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 单个文件的检查结果
 *
 * @author Claude
 * @date 2025-12-28
 */
public class FileCheckResult {

    private String tableName;
    private File file;
    private Action action;

    private List<String> errors;
    private List<String> warnings;
    private List<String> fixes;
    private Set<String> errorTypes;

    public enum Action {
        IMPORT,      // 正常导入
        AUTO_FIX,    // 自动修复后导入
        SKIP,        // 跳过
        MANUAL_FIX   // 需要人工修复
    }

    public FileCheckResult(String tableName, File file) {
        this.tableName = tableName;
        this.file = file;
        this.action = Action.IMPORT;  // 默认正常导入
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.fixes = new ArrayList<>();
        this.errorTypes = new HashSet<>();
    }

    public void addError(String errorType, String message) {
        errors.add(message);
        errorTypes.add(errorType);
        if (action == Action.IMPORT) {
            action = Action.MANUAL_FIX;  // 有错误默认需要人工修复
        }
    }

    public void addWarning(String warningType, String message) {
        warnings.add(message);
        errorTypes.add(warningType);
    }

    public void addFix(String fixType, String message) {
        fixes.add(message);
        if (action == Action.MANUAL_FIX) {
            action = Action.AUTO_FIX;  // 可以自动修复
        }
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String getTableName() {
        return tableName;
    }

    public File getFile() {
        return file;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<String> getFixes() {
        return fixes;
    }

    public Set<String> getErrorTypes() {
        return errorTypes;
    }

    @Override
    public String toString() {
        return String.format("FileCheck[table=%s, action=%s, errors=%d, warnings=%d]",
                tableName, action, errors.size(), warnings.size());
    }
}
