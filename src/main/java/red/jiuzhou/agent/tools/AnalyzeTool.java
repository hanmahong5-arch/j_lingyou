package red.jiuzhou.agent.tools;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.core.AgentContext;

import java.util.*;

/**
 * 分析工具
 *
 * 分析游戏数据分布和平衡性
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class AnalyzeTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeTool.class);

    @Override
    public String getName() {
        return "analyze";
    }

    @Override
    public String getDescription() {
        return "分析游戏数据分布、统计和平衡性，给出建议。";
    }

    /** 参数 Schema（文本块） */
    private static final String PARAMETER_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "table": {
              "type": "string",
              "description": "要分析的表名"
            },
            "field": {
              "type": "string",
              "description": "要分析的字段名（可选）"
            },
            "type": {
              "type": "string",
              "enum": ["distribution", "stats", "balance", "overview"],
              "description": "分析类型：distribution=分布, stats=统计, balance=平衡性, overview=概览"
            }
          },
          "required": ["table"]
        }""";

    @Override
    public String getParameterSchema() {
        return PARAMETER_SCHEMA;
    }

    @Override
    public ToolResult execute(AgentContext context, String parameters) {
        log.info("执行分析工具，参数: {}", parameters);

        // 解析参数
        JSONObject params;
        try {
            params = JSON.parseObject(parameters);
        } catch (Exception e) {
            return ToolResult.error("参数解析失败: " + e.getMessage());
        }

        String tableName = params.getString("table");
        String fieldName = params.getString("field");
        String analysisType = params.getString("type");

        if (tableName == null || tableName.trim().isEmpty()) {
            return ToolResult.error("表名不能为空");
        }

        if (analysisType == null) {
            analysisType = "overview";
        }

        if (context.getJdbcTemplate() == null) {
            return ToolResult.error("数据库连接未配置");
        }

        try {
            Map<String, Object> extraData = new HashMap<>();

            // Switch 表达式（Java 25）
            String report = switch (analysisType) {
                case "distribution" -> analyzeDistribution(context, tableName, fieldName, extraData);
                case "stats" -> analyzeStats(context, tableName, fieldName, extraData);
                case "balance" -> analyzeBalance(context, tableName, extraData);
                default -> analyzeOverview(context, tableName, extraData);
            };

            return ToolResult.analysis(report, extraData);

        } catch (Exception e) {
            log.error("分析执行失败", e);
            return ToolResult.error("分析执行失败: " + e.getMessage());
        }
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.ANALYZE;
    }

    /**
     * 表概览分析
     */
    private String analyzeOverview(AgentContext context, String tableName,
                                   Map<String, Object> extraData) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 表概览分析: ").append(tableName).append("\n\n");

        // 获取行数
        String countSql = String.format("SELECT COUNT(*) FROM `%s`", tableName);
        Integer rowCount = context.getJdbcTemplate().queryForObject(countSql, Integer.class);
        sb.append("**总记录数**: ").append(rowCount).append("\n\n");
        extraData.put("rowCount", rowCount);

        // 获取表结构信息
        try {
            String columnsSql = String.format(
                "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT " +
                "FROM information_schema.COLUMNS " +
                "WHERE table_schema = current_schema() AND TABLE_NAME = '%s' " +
                "ORDER BY ORDINAL_POSITION", tableName);

            List<Map<String, Object>> columns = context.getJdbcTemplate().queryForList(columnsSql);

            sb.append("### 字段列表（共 ").append(columns.size()).append(" 个）\n\n");
            sb.append("| 字段名 | 类型 | 说明 |\n");
            sb.append("|--------|------|------|\n");

            for (Map<String, Object> col : columns) {
                sb.append("| ").append(col.get("COLUMN_NAME"));
                sb.append(" | ").append(col.get("DATA_TYPE"));
                sb.append(" | ").append(col.get("COLUMN_COMMENT") != null ? col.get("COLUMN_COMMENT") : "");
                sb.append(" |\n");
            }

            extraData.put("columns", columns);

        } catch (Exception e) {
            log.warn("获取表结构失败: {}", e.getMessage());
        }

        // 获取样本数据
        try {
            String sampleSql = String.format("SELECT * FROM `%s` LIMIT 5", tableName);
            List<Map<String, Object>> samples = context.getJdbcTemplate().queryForList(sampleSql);

            if (!samples.isEmpty()) {
                sb.append("\n### 样本数据（前5条）\n\n");
                sb.append("```\n");
                for (Map<String, Object> row : samples) {
                    sb.append(row.toString()).append("\n");
                }
                sb.append("```\n");
            }

        } catch (Exception e) {
            log.warn("获取样本数据失败: {}", e.getMessage());
        }

        return sb.toString();
    }

    /**
     * 分布分析
     */
    private String analyzeDistribution(AgentContext context, String tableName,
                                       String fieldName, Map<String, Object> extraData) {
        StringBuilder sb = new StringBuilder();

        if (fieldName == null || fieldName.isEmpty()) {
            return analyzeOverview(context, tableName, extraData);
        }

        sb.append("## 字段分布分析: ").append(tableName).append(".").append(fieldName).append("\n\n");

        // 获取分布数据
        String distSql = String.format(
            "SELECT `%s` as value, COUNT(*) as count " +
            "FROM `%s` " +
            "GROUP BY `%s` " +
            "ORDER BY count DESC " +
            "LIMIT 20", fieldName, tableName, fieldName);

        List<Map<String, Object>> distribution = context.getJdbcTemplate().queryForList(distSql);

        sb.append("### 值分布（Top 20）\n\n");
        sb.append("| 值 | 数量 | 占比 |\n");
        sb.append("|----|------|------|\n");

        // 计算总数
        int total = 0;
        for (Map<String, Object> row : distribution) {
            total += ((Number) row.get("count")).intValue();
        }

        for (Map<String, Object> row : distribution) {
            Object value = row.get("value");
            int count = ((Number) row.get("count")).intValue();
            double percentage = total > 0 ? (count * 100.0 / total) : 0;

            sb.append("| ").append(value != null ? value : "NULL");
            sb.append(" | ").append(count);
            sb.append(" | ").append(String.format("%.1f%%", percentage));
            sb.append(" |\n");
        }

        extraData.put("distribution", distribution);
        extraData.put("total", total);

        return sb.toString();
    }

    /**
     * 统计分析
     */
    private String analyzeStats(AgentContext context, String tableName,
                               String fieldName, Map<String, Object> extraData) {
        StringBuilder sb = new StringBuilder();

        if (fieldName == null || fieldName.isEmpty()) {
            // 分析所有数值字段
            sb.append("## 数值字段统计分析: ").append(tableName).append("\n\n");

            // 获取数值类型字段
            String numFieldsSql = String.format(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
                "WHERE table_schema = current_schema() AND TABLE_NAME = '%s' " +
                "AND DATA_TYPE IN ('int', 'bigint', 'decimal', 'float', 'double', 'tinyint', 'smallint')",
                tableName);

            List<Map<String, Object>> numFields = context.getJdbcTemplate().queryForList(numFieldsSql);

            for (Map<String, Object> field : numFields) {
                String fn = (String) field.get("COLUMN_NAME");
                sb.append(analyzeFieldStats(context, tableName, fn, extraData));
                sb.append("\n");
            }

            return sb.toString();
        }

        return analyzeFieldStats(context, tableName, fieldName, extraData);
    }

    /**
     * 单字段统计
     */
    private String analyzeFieldStats(AgentContext context, String tableName,
                                    String fieldName, Map<String, Object> extraData) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(fieldName).append("\n\n");

        String statsSql = String.format(
            "SELECT " +
            "MIN(`%s`) as min_val, " +
            "MAX(`%s`) as max_val, " +
            "AVG(`%s`) as avg_val, " +
            "SUM(`%s`) as sum_val, " +
            "COUNT(`%s`) as count_val, " +
            "COUNT(DISTINCT `%s`) as distinct_val " +
            "FROM `%s`",
            fieldName, fieldName, fieldName, fieldName, fieldName, fieldName, tableName);

        Map<String, Object> stats = context.getJdbcTemplate().queryForMap(statsSql);

        sb.append("| 统计项 | 值 |\n");
        sb.append("|--------|----|\n");
        sb.append("| 最小值 | ").append(stats.get("min_val")).append(" |\n");
        sb.append("| 最大值 | ").append(stats.get("max_val")).append(" |\n");
        sb.append("| 平均值 | ").append(String.format("%.2f", ((Number) stats.get("avg_val")).doubleValue())).append(" |\n");
        sb.append("| 总和 | ").append(stats.get("sum_val")).append(" |\n");
        sb.append("| 非空数量 | ").append(stats.get("count_val")).append(" |\n");
        sb.append("| 去重数量 | ").append(stats.get("distinct_val")).append(" |\n");

        extraData.put(fieldName + "_stats", stats);

        return sb.toString();
    }

    /**
     * 平衡性分析
     */
    private String analyzeBalance(AgentContext context, String tableName,
                                 Map<String, Object> extraData) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 平衡性分析: ").append(tableName).append("\n\n");

        // 根据表名推断可能的平衡性相关字段
        List<String> balanceFields = new ArrayList<>();

        // 常见的游戏平衡性相关字段
        String[] possibleFields = {
            "level", "damage", "attack", "defense", "hp", "mp",
            "price", "cost", "cooldown", "duration", "range",
            "quality", "grade", "tier", "rarity"
        };

        // 检查哪些字段存在
        for (String field : possibleFields) {
            String checkSql = String.format(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE table_schema = current_schema() AND TABLE_NAME = '%s' " +
                "AND COLUMN_NAME = '%s'", tableName, field);
            Integer count = context.getJdbcTemplate().queryForObject(checkSql, Integer.class);
            if (count != null && count > 0) {
                balanceFields.add(field);
            }
        }

        if (balanceFields.isEmpty()) {
            sb.append("未找到常见的平衡性相关字段（level, damage, attack等）\n");
            sb.append("请使用 stats 分析类型指定具体字段。\n");
            return sb.toString();
        }

        sb.append("### 发现的平衡性相关字段\n\n");
        sb.append(String.join(", ", balanceFields)).append("\n\n");

        // 对每个字段进行统计
        for (String field : balanceFields) {
            sb.append(analyzeFieldStats(context, tableName, field, extraData));
            sb.append("\n");
        }

        // 如果有level和damage，分析等级-伤害曲线
        if (balanceFields.contains("level") && balanceFields.contains("damage")) {
            sb.append("### 等级-伤害曲线\n\n");

            String curveSql = String.format(
                "SELECT level, AVG(damage) as avg_damage, COUNT(*) as count " +
                "FROM `%s` " +
                "GROUP BY level " +
                "ORDER BY level", tableName);

            List<Map<String, Object>> curve = context.getJdbcTemplate().queryForList(curveSql);

            sb.append("| 等级 | 平均伤害 | 数量 |\n");
            sb.append("|------|----------|------|\n");

            for (Map<String, Object> row : curve) {
                sb.append("| ").append(row.get("level"));
                sb.append(" | ").append(String.format("%.1f", ((Number) row.get("avg_damage")).doubleValue()));
                sb.append(" | ").append(row.get("count"));
                sb.append(" |\n");
            }

            extraData.put("levelDamageCurve", curve);
        }

        return sb.toString();
    }
}
