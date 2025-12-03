package red.jiuzhou.analysis.aion.mechanism;

import red.jiuzhou.analysis.aion.AionMechanismCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 机制关系边
 *
 * <p>表示两个游戏机制之间的关系，包含关系类型、强度和示例引用。
 *
 * @author Claude
 * @version 1.0
 */
public class MechanismRelationship {

    private final AionMechanismCategory source;
    private final AionMechanismCategory target;
    private MechanismRelationshipType type;
    private int relationshipCount;
    private double confidence;
    private List<FieldReference> examples;

    public MechanismRelationship(AionMechanismCategory source, AionMechanismCategory target) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.type = MechanismRelationshipType.REFERENCES;
        this.relationshipCount = 0;
        this.confidence = 0.0;
        this.examples = new ArrayList<>();
    }

    public MechanismRelationship(AionMechanismCategory source,
                                  AionMechanismCategory target,
                                  MechanismRelationshipType type) {
        this(source, target);
        this.type = type != null ? type : MechanismRelationshipType.REFERENCES;
    }

    // ========== Getters ==========

    public AionMechanismCategory getSource() {
        return source;
    }

    public AionMechanismCategory getTarget() {
        return target;
    }

    public MechanismRelationshipType getType() {
        return type;
    }

    public int getRelationshipCount() {
        return relationshipCount;
    }

    public double getConfidence() {
        return confidence;
    }

    public List<FieldReference> getExamples() {
        return examples;
    }

    // ========== Setters ==========

    public void setType(MechanismRelationshipType type) {
        this.type = type;
    }

    public void setRelationshipCount(int relationshipCount) {
        this.relationshipCount = relationshipCount;
    }

    public void setConfidence(double confidence) {
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    public void setExamples(List<FieldReference> examples) {
        this.examples = examples != null ? examples : new ArrayList<>();
    }

    // ========== 便捷方法 ==========

    public void incrementCount() {
        this.relationshipCount++;
    }

    public void addExample(FieldReference example) {
        if (example != null && examples.size() < 10) {  // 最多保留10个示例
            examples.add(example);
        }
    }

    public void addExample(String sourceFile, String sourceField,
                          String targetFile, String targetField,
                          double fieldConfidence) {
        addExample(new FieldReference(sourceFile, sourceField, targetFile, targetField, fieldConfidence));
    }

    /**
     * 计算关系强度（用于可视化边的粗细）
     *
     * @return 强度值 (1-10)
     */
    public int getStrength() {
        if (relationshipCount <= 0) return 1;
        if (relationshipCount <= 5) return 2;
        if (relationshipCount <= 10) return 3;
        if (relationshipCount <= 20) return 4;
        if (relationshipCount <= 50) return 5;
        if (relationshipCount <= 100) return 6;
        if (relationshipCount <= 200) return 7;
        if (relationshipCount <= 500) return 8;
        if (relationshipCount <= 1000) return 9;
        return 10;
    }

    /**
     * 获取关系的显示标签
     *
     * @return 显示标签
     */
    public String getLabel() {
        return source.getDisplayName() + " → " + target.getDisplayName() +
               " (" + relationshipCount + ")";
    }

    /**
     * 获取关系边的颜色
     *
     * @return CSS颜色值
     */
    public String getColor() {
        return type.getColor();
    }

    /**
     * 判断是否为有意义的关系（用于过滤弱关系）
     *
     * @param minCount 最小关系数量
     * @param minConfidence 最小置信度
     * @return 是否有意义
     */
    public boolean isSignificant(int minCount, double minConfidence) {
        return relationshipCount >= minCount && confidence >= minConfidence;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MechanismRelationship that = (MechanismRelationship) o;
        return source == that.source && target == that.target;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target);
    }

    @Override
    public String toString() {
        return "MechanismRelationship{" +
                "source=" + source.name() +
                ", target=" + target.name() +
                ", type=" + type.name() +
                ", count=" + relationshipCount +
                ", confidence=" + String.format("%.2f", confidence) +
                '}';
    }

    // ========== 内部类：字段引用示例 ==========

    /**
     * 字段引用示例
     */
    public static class FieldReference {
        private final String sourceFile;
        private final String sourceField;
        private final String targetFile;
        private final String targetField;
        private final double confidence;

        public FieldReference(String sourceFile, String sourceField,
                             String targetFile, String targetField,
                             double confidence) {
            this.sourceFile = sourceFile;
            this.sourceField = sourceField;
            this.targetFile = targetFile;
            this.targetField = targetField;
            this.confidence = confidence;
        }

        public String getSourceFile() {
            return sourceFile;
        }

        public String getSourceField() {
            return sourceField;
        }

        public String getTargetFile() {
            return targetFile;
        }

        public String getTargetField() {
            return targetField;
        }

        public double getConfidence() {
            return confidence;
        }

        @Override
        public String toString() {
            return sourceFile + "::" + sourceField + " → " +
                   targetFile + "::" + targetField +
                   " (" + String.format("%.0f%%", confidence * 100) + ")";
        }
    }
}
