package red.jiuzhou.pattern.rule.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 设计规则 - 意图驱动系统的核心
 *
 * 设计师定义一条规则，系统自动应用到所有匹配的数据
 *
 * 工作流：
 *   1. 定义作用范围（条件筛选）
 *   2. 定义修改规则（表达式）
 *   3. 预览影响（preview）
 *   4. 确认执行（execute）
 *   5. 支持回滚（rollback）
 */
public class DesignRule {

    /** 规则ID */
    private String id;

    /** 规则名称 */
    private String name;

    /** 规则描述 */
    private String description;

    /** 目标机制（ITEM, NPC, SKILL, QUEST等） */
    private String targetMechanism;

    /** 目标表名（可选，默认根据机制自动推断） */
    private String targetTable;

    /** 筛选条件列表 */
    private List<Condition> conditions = new ArrayList<>();

    /** 字段修改规则列表 */
    private List<FieldModification> modifications = new ArrayList<>();

    /** 规则状态 */
    private RuleStatus status = RuleStatus.DRAFT;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 最后修改时间 */
    private LocalDateTime updatedAt;

    /** 最后执行时间 */
    private LocalDateTime lastExecutedAt;

    /** 创建者 */
    private String createdBy;

    /** 版本号（用于乐观锁） */
    private int version = 1;

    /** 所属版本方案ID */
    private String versionSchemeId;

    public DesignRule() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public DesignRule(String name, String targetMechanism) {
        this();
        this.name = name;
        this.targetMechanism = targetMechanism;
    }

    /**
     * 规则状态
     */
    public enum RuleStatus {
        /** 草稿 */
        DRAFT("草稿", "规则正在编辑中"),
        /** 待审核 */
        PENDING_REVIEW("待审核", "规则等待审核"),
        /** 已批准 */
        APPROVED("已批准", "规则已批准，可以执行"),
        /** 执行中 */
        EXECUTING("执行中", "规则正在执行"),
        /** 已完成 */
        COMPLETED("已完成", "规则执行完成"),
        /** 已回滚 */
        ROLLED_BACK("已回滚", "规则已回滚"),
        /** 已废弃 */
        DEPRECATED("已废弃", "规则不再使用");

        private final String displayName;
        private final String description;

        RuleStatus(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * 添加条件
     */
    public DesignRule addCondition(Condition condition) {
        this.conditions.add(condition);
        this.updatedAt = LocalDateTime.now();
        return this;
    }

    /**
     * 添加条件（便捷方法）
     */
    public DesignRule where(String field, Condition.Operator op, Object value) {
        return addCondition(new Condition(field, op, value));
    }

    /**
     * 添加AND条件
     */
    public DesignRule and(String field, Condition.Operator op, Object value) {
        return addCondition(new Condition(field, op, value, Condition.LogicOperator.AND));
    }

    /**
     * 添加OR条件
     */
    public DesignRule or(String field, Condition.Operator op, Object value) {
        return addCondition(new Condition(field, op, value, Condition.LogicOperator.OR));
    }

    /**
     * 添加修改规则
     */
    public DesignRule addModification(FieldModification modification) {
        this.modifications.add(modification);
        this.updatedAt = LocalDateTime.now();
        return this;
    }

    /**
     * 添加修改规则（便捷方法）
     */
    public DesignRule set(String field, String expression) {
        return addModification(new FieldModification(field, expression));
    }

    /**
     * 添加带描述的修改规则
     */
    public DesignRule set(String field, String expression, String description) {
        return addModification(new FieldModification(field, expression, description));
    }

    /**
     * 生成人类可读的规则摘要
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(name).append("】\n");
        sb.append("目标: ").append(targetMechanism);
        if (targetTable != null) {
            sb.append(" (").append(targetTable).append(")");
        }
        sb.append("\n");

        sb.append("条件: ");
        if (conditions.isEmpty()) {
            sb.append("全部记录");
        } else {
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    sb.append(" ").append(conditions.get(i).getLogicOperator().getDisplayName()).append(" ");
                }
                sb.append(conditions.get(i).toDisplayString());
            }
        }
        sb.append("\n");

        sb.append("修改: ");
        for (int i = 0; i < modifications.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(modifications.get(i).toDisplayString());
        }

        return sb.toString();
    }

    /**
     * 生成SQL WHERE子句
     */
    public String toWhereClause() {
        if (conditions.isEmpty()) {
            return "1=1";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) {
                sb.append(" ").append(conditions.get(i).getLogicOperator().getSymbol()).append(" ");
            }
            sb.append("(").append(conditions.get(i).toSqlFragment()).append(")");
        }
        return sb.toString();
    }

    /**
     * 验证规则完整性
     */
    public boolean isValid() {
        return name != null && !name.isEmpty()
            && targetMechanism != null && !targetMechanism.isEmpty()
            && !modifications.isEmpty();
    }

    /**
     * 获取验证错误信息
     */
    public List<String> getValidationErrors() {
        List<String> errors = new ArrayList<>();
        if (name == null || name.isEmpty()) {
            errors.add("规则名称不能为空");
        }
        if (targetMechanism == null || targetMechanism.isEmpty()) {
            errors.add("目标机制不能为空");
        }
        if (modifications.isEmpty()) {
            errors.add("至少需要一条修改规则");
        }
        return errors;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) {
        this.name = name;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    public String getTargetMechanism() { return targetMechanism; }
    public void setTargetMechanism(String targetMechanism) {
        this.targetMechanism = targetMechanism;
        this.updatedAt = LocalDateTime.now();
    }

    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
        this.updatedAt = LocalDateTime.now();
    }

    public List<Condition> getConditions() { return conditions; }
    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
        this.updatedAt = LocalDateTime.now();
    }

    public List<FieldModification> getModifications() { return modifications; }
    public void setModifications(List<FieldModification> modifications) {
        this.modifications = modifications;
        this.updatedAt = LocalDateTime.now();
    }

    public RuleStatus getStatus() { return status; }
    public void setStatus(RuleStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastExecutedAt() { return lastExecutedAt; }
    public void setLastExecutedAt(LocalDateTime lastExecutedAt) { this.lastExecutedAt = lastExecutedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getVersionSchemeId() { return versionSchemeId; }
    public void setVersionSchemeId(String versionSchemeId) { this.versionSchemeId = versionSchemeId; }
}
