package red.jiuzhou.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * XML文件质量检查结果
 *
 * @author Claude
 * @date 2025-12-28
 */
public class QualityCheckResult {

    private boolean isEmpty;           // 是否为空文件
    private boolean isTemplate;        // 是否为模板文件
    private int itemCount;             // 数据条目数量
    private List<String> sampleErrors; // 样本数据错误
    private boolean hasStructureError; // 是否有结构错误
    private List<String> structureErrors; // 结构错误列表

    public QualityCheckResult() {
        this.isEmpty = false;
        this.isTemplate = false;
        this.itemCount = 0;
        this.sampleErrors = new ArrayList<>();
        this.hasStructureError = false;
        this.structureErrors = new ArrayList<>();
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    public void setEmpty(boolean empty) {
        isEmpty = empty;
    }

    public boolean isTemplate() {
        return isTemplate;
    }

    public void setTemplate(boolean template) {
        isTemplate = template;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public List<String> getSampleErrors() {
        return sampleErrors;
    }

    public void setSampleErrors(List<String> sampleErrors) {
        this.sampleErrors = sampleErrors;
    }

    public void addSampleError(String error) {
        this.sampleErrors.add(error);
    }

    public boolean hasStructureError() {
        return hasStructureError;
    }

    public void setHasStructureError(boolean hasStructureError) {
        this.hasStructureError = hasStructureError;
    }

    public List<String> getStructureErrors() {
        return structureErrors;
    }

    public void addStructureError(String error) {
        this.structureErrors.add(error);
        this.hasStructureError = true;
    }

    /**
     * 是否可以导入（不为空且无结构错误）
     */
    public boolean isImportable() {
        return !isEmpty && !hasStructureError;
    }

    @Override
    public String toString() {
        return String.format("QualityCheck[empty=%s, template=%s, items=%d, errors=%d]",
                isEmpty, isTemplate, itemCount, sampleErrors.size());
    }
}
