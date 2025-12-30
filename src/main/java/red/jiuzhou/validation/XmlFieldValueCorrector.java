package red.jiuzhou.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * XML字段值自动修正器
 *
 * <p>基于Aion服务器日志分析，自动修正不符合服务器要求的字段值。
 *
 * <p>设计理念：<strong>导出时自动修正，让结果符合服务器要求</strong>
 *
 * <p>错误来源：
 * <ul>
 *   <li>MainServer日志：100,698行错误日志分析</li>
 *   <li>NPCServer日志：105,654行错误日志分析</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class XmlFieldValueCorrector {

    private static final Logger log = LoggerFactory.getLogger(XmlFieldValueCorrector.class);

    /**
     * 修正统计信息
     */
    private static final Map<String, AtomicInteger> CORRECTION_STATS = new HashMap<>();

    /**
     * 修正字段值（在导出XML时调用）
     *
     * @param tableName 表名
     * @param fieldName 字段名
     * @param value     原始值
     * @return 修正后的值（如果不需要修正则返回原值）
     */
    public static String correctValue(String tableName, String fieldName, String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String correctedValue = value;

        // 根据表名和字段名应用修正规则
        if (tableName.startsWith("skill_") || tableName.contains("_skill_")) {
            correctedValue = correctSkillField(fieldName, value);
        } else if (tableName.equals("world") || tableName.startsWith("world_")) {
            correctedValue = correctWorldField(fieldName, value);
        } else if (tableName.startsWith("npc_") || tableName.contains("_npc_")) {
            correctedValue = correctNpcField(fieldName, value);
        } else if (tableName.startsWith("item_")) {
            correctedValue = correctItemField(fieldName, value);
        }

        // 记录修正
        if (!correctedValue.equals(value)) {
            String key = tableName + "." + fieldName;
            CORRECTION_STATS.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        }

        return correctedValue;
    }

    /**
     * 修正技能字段
     */
    private static String correctSkillField(String fieldName, String value) {
        switch (fieldName) {
            case "target_flying_restriction":
                // 错误日志：invalid SkillFlyingRestriction(target_flying_restriction) :  "0"
                // 修正：0 → 1（默认值，表示无飞行限制）
                if ("0".equals(value)) {
                    log.debug("修正 target_flying_restriction: 0 → 1");
                    return "1";
                }
                break;

            case "target_maxcount":
                // 错误日志：Target_MaxCount : invalid value 0 must be (1..120)
                // 修正：0 → 1，>120 → 120
                try {
                    int count = Integer.parseInt(value);
                    if (count == 0) {
                        log.debug("修正 target_maxcount: 0 → 1");
                        return "1";
                    } else if (count > 120) {
                        log.warn("修正 target_maxcount: {} → 120（超过最大值）", count);
                        return "120";
                    }
                } catch (NumberFormatException e) {
                    // 忽略非数字值
                }
                break;

            case "penalty_time_succ":
                // 错误日志：penalty_time_succ : invalid value  0
                // 修正：0 → 1（最小值）
                if ("0".equals(value)) {
                    log.debug("修正 penalty_time_succ: 0 → 1");
                    return "1";
                }
                break;

            case "maxBurstSignetLevel":
            case "max_burst_signet_level":
                // 错误日志：invalid maxBurstSignetLevel:0
                // 修正：0 → 1
                if ("0".equals(value)) {
                    log.debug("修正 maxBurstSignetLevel: 0 → 1");
                    return "1";
                }
                break;

            case "casting_delay":
                // 错误日志：casting_delay, too invalid number 0
                // 修正：0 → 100（默认100ms）
                if ("0".equals(value)) {
                    log.debug("修正 casting_delay: 0 → 100");
                    return "100";
                }
                // 验证最大值（<60000ms）
                try {
                    int delay = Integer.parseInt(value);
                    if (delay >= 60000) {
                        log.warn("修正 casting_delay: {} → 59999（超过最大值）", delay);
                        return "59999";
                    }
                } catch (NumberFormatException e) {
                    // 忽略非数字值
                }
                break;

            case "cost_parameter":
                // 错误日志：服务器不支持cost_parameter='DP'
                // 修正：DP → HP（使用生命值消耗）
                if ("DP".equals(value)) {
                    log.info("修正 cost_parameter: DP → HP（服务器不支持DP消耗）");
                    return "HP";
                }
                break;
        }

        return value;
    }

    /**
     * 修正世界（World）字段
     */
    private static String correctWorldField(String fieldName, String value) {
        // strparam1/2/3 必须是字符串类型，不能是纯数字
        if (fieldName.matches("strparam[123]")) {
            if (value.matches("^\\d+$")) {
                // 纯数字，添加前缀使其成为字符串
                String corrected = "str_" + value;
                log.info("修正 {}: {} → {}（纯数字转字符串）", fieldName, value, corrected);
                return corrected;
            }
        }

        // instance_cooltime 值7080无效
        if ("instance_cooltime".equals(fieldName)) {
            if ("7080".equals(value)) {
                log.warn("修正 instance_cooltime: 7080 → 7200（无效值）");
                return "7200";
            }
        }

        return value;
    }

    /**
     * 修正NPC字段
     */
    private static String correctNpcField(String fieldName, String value) {
        // skill_level = 255 无效
        if ("skill_level".equals(fieldName)) {
            if ("255".equals(value)) {
                log.warn("修正NPC skill_level: 255 → 1（无效值）");
                return "1";
            }
        }

        // abnormal_status_resist_name 必须是字符串，不能是纯数字
        if ("abnormal_status_resist_name".equals(fieldName)) {
            if (value.matches("^\\d+$")) {
                // 纯数字，映射到状态名称
                String statusName = mapAbnormalStatusId(value);
                log.info("修正 abnormal_status_resist_name: {} → {}（数字转状态名）", value, statusName);
                return statusName;
            }
        }

        return value;
    }

    /**
     * 修正道具字段
     */
    private static String correctItemField(String fieldName, String value) {
        // 道具的casting_delay也不能为0
        if ("casting_delay".equals(fieldName)) {
            if ("0".equals(value)) {
                log.debug("修正道具 casting_delay: 0 → 100");
                return "100";
            }
        }

        return value;
    }

    /**
     * 将异常状态ID映射到状态名称
     *
     * <p>基于服务器日志分析，常见的异常状态ID和名称映射
     */
    private static String mapAbnormalStatusId(String id) {
        // 常见异常状态ID到名称的映射
        Map<String, String> statusMap = Map.ofEntries(
                Map.entry("0", "无"),
                Map.entry("50", "沉默"),
                Map.entry("900", "眩晕"),
                Map.entry("100", "定身"),
                Map.entry("200", "减速"),
                Map.entry("300", "睡眠"),
                Map.entry("400", "恐惧"),
                Map.entry("500", "魅惑"),
                Map.entry("600", "缠绕"),
                Map.entry("700", "石化"),
                Map.entry("800", "失明")
        );

        return statusMap.getOrDefault(id, "未知状态_" + id);
    }

    /**
     * 批量修正数据行的所有字段
     *
     * @param tableName 表名
     * @param row       数据行
     * @return 修正后的数据行
     */
    public static Map<String, String> correctRow(String tableName, Map<String, String> row) {
        Map<String, String> correctedRow = new HashMap<>(row);

        for (Map.Entry<String, String> entry : row.entrySet()) {
            String fieldName = entry.getKey();
            String value = entry.getValue();

            if (value != null && !value.isEmpty()) {
                String correctedValue = correctValue(tableName, fieldName, value);
                if (!correctedValue.equals(value)) {
                    correctedRow.put(fieldName, correctedValue);
                }
            }
        }

        return correctedRow;
    }

    /**
     * 获取修正统计信息
     *
     * @return 统计信息字符串
     */
    public static String getStatistics() {
        if (CORRECTION_STATS.isEmpty()) {
            return "未进行任何字段值修正";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("字段值修正统计（共 ").append(CORRECTION_STATS.size()).append(" 个字段）:\n");

        CORRECTION_STATS.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .limit(20)
                .forEach(entry -> {
                    sb.append(String.format("  - %s: %d 次修正\n",
                            entry.getKey(), entry.getValue().get()));
                });

        int totalCorrections = CORRECTION_STATS.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();
        sb.append(String.format("总修正次数: %d", totalCorrections));

        return sb.toString();
    }

    /**
     * 重置统计信息
     */
    public static void resetStatistics() {
        CORRECTION_STATS.clear();
    }

    /**
     * 验证字段值是否符合服务器要求（不修正，只验证）
     *
     * @param tableName 表名
     * @param fieldName 字段名
     * @param value     字段值
     * @return 验证结果（null表示通过，非null表示错误信息）
     */
    public static String validateValue(String tableName, String fieldName, String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        // 技能字段验证
        if (tableName.startsWith("skill_")) {
            switch (fieldName) {
                case "target_flying_restriction":
                    if ("0".equals(value)) {
                        return "target_flying_restriction 不能为 0（服务器不接受）";
                    }
                    break;

                case "target_maxcount":
                    try {
                        int count = Integer.parseInt(value);
                        if (count == 0) {
                            return "target_maxcount 不能为 0（必须是 1-120）";
                        } else if (count > 120) {
                            return "target_maxcount 超过最大值 120";
                        }
                    } catch (NumberFormatException e) {
                        return "target_maxcount 必须是整数";
                    }
                    break;

                case "casting_delay":
                    try {
                        int delay = Integer.parseInt(value);
                        if (delay == 0) {
                            return "casting_delay 不能为 0";
                        } else if (delay >= 60000) {
                            return "casting_delay 超过最大值 59999ms";
                        }
                    } catch (NumberFormatException e) {
                        return "casting_delay 必须是整数";
                    }
                    break;
            }
        }

        // World字段验证
        if (tableName.equals("world") || tableName.startsWith("world_")) {
            if (fieldName.matches("strparam[123]")) {
                if (value.matches("^\\d+$")) {
                    return fieldName + " 必须是字符串类型，不能是纯数字";
                }
            }
        }

        // NPC字段验证
        if (tableName.startsWith("npc_")) {
            if ("skill_level".equals(fieldName) && "255".equals(value)) {
                return "NPC skill_level 不能为 255（无效值）";
            }
        }

        return null;  // 验证通过
    }
}
