package red.jiuzhou.analysis.aion.mechanism;

import red.jiuzhou.analysis.aion.AionMechanismCategory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 机制节点
 *
 * <p>表示关系图中的一个游戏机制节点，包含该机制的统计信息和布局位置。
 *
 * @author Claude
 * @version 1.0
 */
public class MechanismNode {

    private final AionMechanismCategory category;
    private int fileCount;
    private int fieldCount;
    private List<String> representativeFiles;
    private Set<String> keyFields;

    // 布局相关（用于力导向图）
    private double x;
    private double y;
    private double vx;  // 速度x分量
    private double vy;  // 速度y分量
    private boolean pinned;  // 是否固定位置

    public MechanismNode(AionMechanismCategory category) {
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.fileCount = 0;
        this.fieldCount = 0;
        this.representativeFiles = new ArrayList<>();
        this.keyFields = new HashSet<>();
        this.x = 0;
        this.y = 0;
        this.vx = 0;
        this.vy = 0;
        this.pinned = false;
    }

    // ========== Getters ==========

    public AionMechanismCategory getCategory() {
        return category;
    }

    public String getDisplayName() {
        return category.getDisplayName();
    }

    public String getColor() {
        return category.getColor();
    }

    public String getIcon() {
        return category.getIcon();
    }

    public int getFileCount() {
        return fileCount;
    }

    public int getFieldCount() {
        return fieldCount;
    }

    public List<String> getRepresentativeFiles() {
        return representativeFiles;
    }

    public Set<String> getKeyFields() {
        return keyFields;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getVx() {
        return vx;
    }

    public double getVy() {
        return vy;
    }

    public boolean isPinned() {
        return pinned;
    }

    // ========== Setters ==========

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public void setFieldCount(int fieldCount) {
        this.fieldCount = fieldCount;
    }

    public void setRepresentativeFiles(List<String> representativeFiles) {
        this.representativeFiles = representativeFiles != null ? representativeFiles : new ArrayList<>();
    }

    public void setKeyFields(Set<String> keyFields) {
        this.keyFields = keyFields != null ? keyFields : new HashSet<>();
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void setVx(double vx) {
        this.vx = vx;
    }

    public void setVy(double vy) {
        this.vy = vy;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    // ========== 便捷方法 ==========

    public void addFile(String fileName) {
        if (fileName != null && !representativeFiles.contains(fileName)) {
            representativeFiles.add(fileName);
            fileCount = representativeFiles.size();
        }
    }

    public void addKeyField(String fieldName) {
        if (fieldName != null) {
            keyFields.add(fieldName);
        }
    }

    public void incrementFieldCount() {
        this.fieldCount++;
    }

    /**
     * 计算节点半径（基于文件数量）
     *
     * @param baseRadius 基础半径
     * @param scaleFactor 缩放因子
     * @return 计算后的半径
     */
    public double calculateRadius(double baseRadius, double scaleFactor) {
        return baseRadius + Math.log1p(fileCount) * scaleFactor;
    }

    /**
     * 应用速度更新位置
     *
     * @param damping 阻尼系数 (0-1)
     */
    public void applyVelocity(double damping) {
        if (!pinned) {
            x += vx;
            y += vy;
            vx *= damping;
            vy *= damping;
        }
    }

    /**
     * 应用力
     *
     * @param fx 力的x分量
     * @param fy 力的y分量
     */
    public void applyForce(double fx, double fy) {
        if (!pinned) {
            vx += fx;
            vy += fy;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MechanismNode that = (MechanismNode) o;
        return category == that.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(category);
    }

    @Override
    public String toString() {
        return "MechanismNode{" +
                "category=" + category.name() +
                ", displayName='" + getDisplayName() + '\'' +
                ", fileCount=" + fileCount +
                ", fieldCount=" + fieldCount +
                '}';
    }
}
