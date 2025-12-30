package red.jiuzhou.pattern.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 属性词汇实体
 * 对应 attr_dictionary 表，存储126个服务端属性定义
 * 数据来源：absolute_stat_to_pc.xml
 */
public class AttrDictionary {
    private Integer id;
    private String attrCode;           // 属性代码（如str, max_hp, physical_attack）
    private String attrName;           // 属性名称（中文）
    private String attrCategory;       // 属性分类

    // 值域信息
    private BigDecimal typicalMin;     // 典型最小值
    private BigDecimal typicalMax;     // 典型最大值
    private String valueUnit;          // 值单位
    private Boolean isPercentage;      // 是否为百分比值

    // 使用统计
    private Integer usedInItems;
    private Integer usedInTitles;
    private Integer usedInPets;
    private Integer usedInSkills;
    private Integer usedInBuffs;
    private Integer totalUsage;

    // 来源信息
    private String sourceFile;
    private String description;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    /**
     * 属性分类枚举
     */
    public enum AttrCategory {
        BASIC("基础属性"),           // str, agi, vit, int, wis
        COMBAT("战斗属性"),          // physical_attack, magical_attack, physical_defense
        HP_MP("生命魔力"),           // max_hp, max_mp, hp_regen, mp_regen
        CRITICAL("暴击属性"),        // physical_critical, magical_critical
        ACCURACY("命中回避"),        // physical_accuracy, magical_accuracy, evasion
        SPEED("速度属性"),           // attack_speed, movement_speed, casting_speed
        RESIST("抗性属性"),          // fire_resist, water_resist, earth_resist
        SPECIAL("特殊属性"),         // pvp_attack, pvp_defense, heal_boost
        UNKNOWN("未分类");

        private final String displayName;

        AttrCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    public AttrDictionary() {
        this.isPercentage = false;
        this.usedInItems = 0;
        this.usedInTitles = 0;
        this.usedInPets = 0;
        this.usedInSkills = 0;
        this.usedInBuffs = 0;
        this.totalUsage = 0;
    }

    public AttrDictionary(String attrCode) {
        this();
        this.attrCode = attrCode;
    }

    /**
     * 根据属性代码推断分类
     */
    public static AttrCategory inferCategory(String attrCode) {
        if (attrCode == null) return AttrCategory.UNKNOWN;
        String code = attrCode.toLowerCase();

        if (code.matches("(str|agi|vit|int|wis|will|knowledge)")) {
            return AttrCategory.BASIC;
        }
        if (code.contains("max_hp") || code.contains("max_mp") || code.contains("_regen")) {
            return AttrCategory.HP_MP;
        }
        if (code.contains("attack") && !code.contains("speed")) {
            return AttrCategory.COMBAT;
        }
        if (code.contains("defense") || code.contains("defence")) {
            return AttrCategory.COMBAT;
        }
        if (code.contains("critical") || code.contains("crit")) {
            return AttrCategory.CRITICAL;
        }
        if (code.contains("accuracy") || code.contains("evasion") || code.contains("parry") || code.contains("block")) {
            return AttrCategory.ACCURACY;
        }
        if (code.contains("speed")) {
            return AttrCategory.SPEED;
        }
        if (code.contains("resist") || code.contains("reduction")) {
            return AttrCategory.RESIST;
        }
        if (code.contains("pvp") || code.contains("heal") || code.contains("boost")) {
            return AttrCategory.SPECIAL;
        }

        return AttrCategory.UNKNOWN;
    }

    /**
     * 增加使用计数
     */
    public void incrementUsage(String usageType) {
        switch (usageType.toLowerCase()) {
            case "item":
            case "items":
                this.usedInItems++;
                break;
            case "title":
            case "titles":
                this.usedInTitles++;
                break;
            case "pet":
            case "pets":
            case "familiar":
                this.usedInPets++;
                break;
            case "skill":
            case "skills":
                this.usedInSkills++;
                break;
            case "buff":
            case "buffs":
            case "effect":
                this.usedInBuffs++;
                break;
        }
        this.totalUsage++;
    }

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getAttrCode() { return attrCode; }
    public void setAttrCode(String attrCode) { this.attrCode = attrCode; }

    public String getAttrName() { return attrName; }
    public void setAttrName(String attrName) { this.attrName = attrName; }

    public String getAttrCategory() { return attrCategory; }
    public void setAttrCategory(String attrCategory) { this.attrCategory = attrCategory; }

    public BigDecimal getTypicalMin() { return typicalMin; }
    public void setTypicalMin(BigDecimal typicalMin) { this.typicalMin = typicalMin; }

    public BigDecimal getTypicalMax() { return typicalMax; }
    public void setTypicalMax(BigDecimal typicalMax) { this.typicalMax = typicalMax; }

    public String getValueUnit() { return valueUnit; }
    public void setValueUnit(String valueUnit) { this.valueUnit = valueUnit; }

    public Boolean getIsPercentage() { return isPercentage; }
    public void setIsPercentage(Boolean isPercentage) { this.isPercentage = isPercentage; }

    public Integer getUsedInItems() { return usedInItems; }
    public void setUsedInItems(Integer usedInItems) { this.usedInItems = usedInItems; }

    public Integer getUsedInTitles() { return usedInTitles; }
    public void setUsedInTitles(Integer usedInTitles) { this.usedInTitles = usedInTitles; }

    public Integer getUsedInPets() { return usedInPets; }
    public void setUsedInPets(Integer usedInPets) { this.usedInPets = usedInPets; }

    public Integer getUsedInSkills() { return usedInSkills; }
    public void setUsedInSkills(Integer usedInSkills) { this.usedInSkills = usedInSkills; }

    public Integer getUsedInBuffs() { return usedInBuffs; }
    public void setUsedInBuffs(Integer usedInBuffs) { this.usedInBuffs = usedInBuffs; }

    public Integer getTotalUsage() { return totalUsage; }
    public void setTotalUsage(Integer totalUsage) { this.totalUsage = totalUsage; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "AttrDictionary{" +
                "attrCode='" + attrCode + '\'' +
                ", attrName='" + attrName + '\'' +
                ", attrCategory='" + attrCategory + '\'' +
                ", totalUsage=" + totalUsage +
                '}';
    }
}
