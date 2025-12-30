package red.jiuzhou.analysis.aion;

/**
 * 机制检测结果（Record）
 *
 * <p>包含检测到的机制分类、置信度、识别原因等信息。
 * <p>使用Java 16+ Record特性，自动生成构造器、getter、equals、hashCode、toString
 *
 * @param category     机制分类
 * @param confidence   置信度 (0.0-1.0)
 * @param reasoning    识别原因
 * @param localized    是否为本地化版本
 * @param relativePath 相对路径
 */
public record DetectionResult(
    AionMechanismCategory category,
    double confidence,
    String reasoning,
    boolean localized,
    String relativePath
) {
    /**
     * 紧凑构造器 - 参数验证和默认值
     */
    public DetectionResult {
        if (category == null) {
            category = AionMechanismCategory.OTHER;
        }
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        if (reasoning == null) {
            reasoning = "";
        }
        if (relativePath == null) {
            relativePath = "";
        }
    }

    // ========== 兼容性Getter (Record访问器别名) ==========

    /** @deprecated Use {@link #category()} instead */
    public AionMechanismCategory getCategory() { return category; }

    /** @deprecated Use {@link #confidence()} instead */
    public double getConfidence() { return confidence; }

    /** @deprecated Use {@link #reasoning()} instead */
    public String getReasoning() { return reasoning; }

    /** @deprecated Use {@link #localized()} instead */
    public boolean isLocalized() { return localized; }

    /** @deprecated Use {@link #relativePath()} instead */
    public String getRelativePath() { return relativePath; }

    /**
     * 获取置信度等级描述
     */
    public String getConfidenceLevel() {
        return switch ((int) (confidence * 10)) {
            case 9, 10 -> "高";
            case 7, 8 -> "中";
            case 5, 6 -> "低";
            default -> "猜测";
        };
    }

    /**
     * 获取带本地化标记的分类显示名
     */
    public String getDisplayName() {
        String name = category.getDisplayName();
        return localized ? name + " [本地化]" : name;
    }

    @Override
    public String toString() {
        return "DetectionResult{category=%s, confidence=%.2f, localized=%s, path=%s}"
            .formatted(category.getDisplayName(), confidence, localized, relativePath);
    }

    // ========== Builder Pattern (保持向后兼容) ==========

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AionMechanismCategory category = AionMechanismCategory.OTHER;
        private double confidence = 0.5;
        private String reasoning = "";
        private boolean localized = false;
        private String relativePath = "";

        public Builder category(AionMechanismCategory category) {
            this.category = category;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public Builder localized(boolean localized) {
            this.localized = localized;
            return this;
        }

        public Builder relativePath(String relativePath) {
            this.relativePath = relativePath;
            return this;
        }

        public DetectionResult build() {
            return new DetectionResult(category, confidence, reasoning, localized, relativePath);
        }
    }
}
