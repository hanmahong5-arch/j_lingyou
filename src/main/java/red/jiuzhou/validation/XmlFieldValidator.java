package red.jiuzhou.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * XML字段参数验证器
 *
 * <p>基于Aion服务器启动日志分析，对XML配置字段进行参数范围和类型验证。
 *
 * <p>验证规则来源：
 * <ul>
 *   <li>MainServer错误日志中的参数限制</li>
 *   <li>NPCServer错误日志中的类型约束</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class XmlFieldValidator {

    private static final Logger log = LoggerFactory.getLogger(XmlFieldValidator.class);

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String message) {
            errors.add(message);
        }

        public void addWarning(String message) {
            warnings.add(message);
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            if (hasErrors()) {
                sb.append("❌ 错误 (").append(errors.size()).append("):\n");
                errors.forEach(e -> sb.append("  - ").append(e).append("\n"));
            }
            if (hasWarnings()) {
                sb.append("⚠ 警告 (").append(warnings.size()).append("):\n");
                warnings.forEach(w -> sb.append("  - ").append(w).append("\n"));
            }
            return sb.toString();
        }
    }

    /**
     * 验证单条数据记录
     *
     * @param tableName 表名
     * @param row       数据行
     * @param rowIndex  行索引（用于错误提示）
     * @return 验证结果
     */
    public static ValidationResult validate(String tableName, Map<String, String> row, int rowIndex) {
        ValidationResult result = new ValidationResult();

        // 根据表名选择验证策略
        if (tableName.startsWith("skill_")) {
            validateSkillConfig(row, rowIndex, result);
        } else if (tableName.equals("world") || tableName.startsWith("world_")) {
            validateWorldConfig(row, rowIndex, result);
        } else if (tableName.startsWith("npc_") || tableName.contains("_npc_")) {
            validateNpcConfig(row, rowIndex, result);
        }

        return result;
    }

    /**
     * 验证技能配置
     */
    private static void validateSkillConfig(Map<String, String> row, int rowIndex, ValidationResult result) {
        String skillName = row.getOrDefault("name", "未知技能");

        // 1. 验证 casting_delay（施法延迟）
        String castingDelay = row.get("casting_delay");
        if (castingDelay != null && !castingDelay.isEmpty()) {
            try {
                int delay = Integer.parseInt(castingDelay);
                if (delay >= 60000) {
                    result.addWarning(String.format(
                            "行%d - 技能 [%s]：casting_delay=%d ms 超过限制（最大59999ms），服务器可能拒绝加载（宽进：已允许导入）",
                            rowIndex, skillName, delay
                    ));
                } else if (delay > 30000) {
                    result.addWarning(String.format(
                            "行%d - 技能 [%s]：casting_delay=%d ms 过长（超过30秒），可能影响游戏体验",
                            rowIndex, skillName, delay
                    ));
                }
            } catch (NumberFormatException e) {
                result.addWarning(String.format(
                        "行%d - 技能 [%s]：casting_delay 应为整数，当前值：%s（宽进：已允许导入）",
                        rowIndex, skillName, castingDelay
                ));
            }
        }

        // 2. 验证 skill_level（技能等级）
        String skillLevel = row.get("skill_level");
        if (skillLevel != null && !skillLevel.isEmpty()) {
            try {
                int level = Integer.parseInt(skillLevel);
                if (level < 1 || level > 100) {
                    result.addWarning(String.format(
                            "行%d - 技能 [%s]：skill_level=%d 超出有效范围（1-100）（宽进：已允许导入）",
                            rowIndex, skillName, level
                    ));
                }
            } catch (NumberFormatException e) {
                result.addWarning(String.format(
                        "行%d - 技能 [%s]：skill_level 应为整数，当前值：%s（宽进：已允许导入）",
                        rowIndex, skillName, skillLevel
                ));
            }
        }

        // 3. 验证 cost_parameter（消耗参数）
        String costParameter = row.get("cost_parameter");
        if (costParameter != null && costParameter.equals("DP")) {
            result.addWarning(String.format(
                    "行%d - 技能 [%s]：cost_parameter='DP' 可能无效，服务器可能不支持此参数类型（宽进：已允许导入）",
                    rowIndex, skillName
            ));
        }

        // 4. 验证 target_flying_restriction（飞行限制）
        String flyingRestriction = row.get("target_flying_restriction");
        if (flyingRestriction != null) {
            if (flyingRestriction.equals("0")) {
                result.addWarning(String.format(
                        "行%d - 技能 [%s]：target_flying_restriction='0' 可能无效，建议使用1/2/3/4等枚举值（宽进：已允许导入）",
                        rowIndex, skillName
                ));
            } else {
                try {
                    int restriction = Integer.parseInt(flyingRestriction);
                    if (restriction < 1 || restriction > 4) {
                        result.addWarning(String.format(
                                "行%d - 技能 [%s]：target_flying_restriction=%d 可能无效（通常为1-4）",
                                rowIndex, skillName, restriction
                        ));
                    }
                } catch (NumberFormatException e) {
                    result.addWarning(String.format(
                            "行%d - 技能 [%s]：target_flying_restriction 应为整数，当前值：%s（宽进：已允许导入）",
                            rowIndex, skillName, flyingRestriction
                    ));
                }
            }
        }
    }

    /**
     * 验证World配置
     */
    private static void validateWorldConfig(Map<String, String> row, int rowIndex, ValidationResult result) {
        String worldName = row.getOrDefault("name", "未知世界");

        // 验证 strparam1/2/3 必须为字符串类型（不能是纯数字）
        for (String field : List.of("strparam1", "strparam2", "strparam3")) {
            String value = row.get(field);
            if (value != null && !value.isEmpty()) {
                // 如果是纯数字（没有任何字母或符号）
                if (value.matches("^\\d+$")) {
                    result.addWarning(String.format(
                            "行%d - 世界 [%s]：%s 应为字符串类型，当前为纯数字：%s（宽进：已允许导入）",
                            rowIndex, worldName, field, value
                    ));
                }
            }
        }
    }

    /**
     * 验证NPC配置
     */
    private static void validateNpcConfig(Map<String, String> row, int rowIndex, ValidationResult result) {
        String npcName = row.getOrDefault("name", "未知NPC");

        // 验证 abnormal_status_resist_name（异常状态抗性名称）
        String statusName = row.get("abnormal_status_resist_name");
        if (statusName != null && !statusName.isEmpty()) {
            // 如果是纯数字（应该是状态名称字符串）
            if (statusName.matches("^\\d+$")) {
                result.addWarning(String.format(
                        "行%d - NPC [%s]：abnormal_status_resist_name 应为状态名称字符串，当前为数值：%s（宽进：已允许导入）",
                        rowIndex, npcName, statusName
                ));
                result.addWarning(String.format(
                        "  提示：常见状态名称如 '沉默'、'眩晕'、'定身' 等，而非 50/900/0"
                ));
            }
        }

        // 验证 skill_level（NPC技能等级）
        String skillLevel = row.get("skill_level");
        if (skillLevel != null && !skillLevel.isEmpty()) {
            try {
                int level = Integer.parseInt(skillLevel);
                if (level == 255) {
                    result.addWarning(String.format(
                            "行%d - NPC [%s]：skill_level=255 可能无效（日志中常见错误值）（宽进：已允许导入）",
                            rowIndex, npcName
                    ));
                }
            } catch (NumberFormatException e) {
                // 忽略非数字的情况（可能是变量名）
            }
        }
    }

    /**
     * 批量验证数据
     *
     * @param tableName 表名
     * @param data      数据列表
     * @return 总体验证结果
     */
    public static ValidationResult validateBatch(String tableName, List<Map<String, String>> data) {
        ValidationResult totalResult = new ValidationResult();

        for (int i = 0; i < data.size(); i++) {
            ValidationResult rowResult = validate(tableName, data.get(i), i + 1);
            totalResult.getErrors().addAll(rowResult.getErrors());
            totalResult.getWarnings().addAll(rowResult.getWarnings());
        }

        if (totalResult.hasErrors() || totalResult.hasWarnings()) {
            log.info("表 {} 验证完成：{} 条数据，{} 个错误，{} 个警告",
                    tableName, data.size(),
                    totalResult.getErrors().size(),
                    totalResult.getWarnings().size());
        }

        return totalResult;
    }
}
