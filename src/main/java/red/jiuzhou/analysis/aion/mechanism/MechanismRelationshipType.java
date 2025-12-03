package red.jiuzhou.analysis.aion.mechanism;

/**
 * 机制关系类型枚举
 *
 * <p>定义游戏机制之间的关系类型，用于构建机制依赖图。
 *
 * @author Claude
 * @version 1.0
 */
public enum MechanismRelationshipType {

    /**
     * 引用关系 - 字段级引用
     * 例如：NPC的skill_id引用技能表的id
     */
    REFERENCES("引用", "字段级别的ID引用关系", "#4169E1"),

    /**
     * 包含关系 - 逻辑包含
     * 例如：NPC包含掉落表配置
     */
    CONTAINS("包含", "逻辑上的包含或组合关系", "#228B22"),

    /**
     * 触发关系 - 行为触发
     * 例如：任务完成触发奖励发放
     */
    TRIGGERS("触发", "行为或事件触发关系", "#FF8C00"),

    /**
     * 依赖关系 - 功能依赖
     * 例如：强化系统依赖物品系统
     */
    DEPENDS_ON("依赖", "功能实现上的依赖关系", "#800080"),

    /**
     * 关联关系 - 弱关联
     * 例如：商店与物品的展示关联
     */
    ASSOCIATES("关联", "弱耦合的关联关系", "#708090");

    private final String displayName;
    private final String description;
    private final String color;

    MechanismRelationshipType(String displayName, String description, String color) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getColor() {
        return color;
    }

    /**
     * 根据字段名推断关系类型
     *
     * @param fieldName 字段名
     * @return 推断的关系类型
     */
    public static MechanismRelationshipType inferFromFieldName(String fieldName) {
        if (fieldName == null) {
            return ASSOCIATES;
        }
        String lower = fieldName.toLowerCase();

        // ID引用关系
        if (lower.endsWith("_id") || lower.endsWith("id")) {
            return REFERENCES;
        }

        // 触发关系
        if (lower.contains("trigger") || lower.contains("event") || lower.contains("action")) {
            return TRIGGERS;
        }

        // 包含关系
        if (lower.contains("list") || lower.contains("items") || lower.contains("children")) {
            return CONTAINS;
        }

        // 默认为关联
        return ASSOCIATES;
    }
}
