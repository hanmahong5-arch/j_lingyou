package red.jiuzhou.agent.tools;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.dbxml.TableConf;
import red.jiuzhou.dbxml.ColumnMapping;
import red.jiuzhou.util.DatabaseUtil;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于配置文件的Schema提供者
 *
 * 从项目的JSON配置文件中提取真实的表结构和关联关系
 * 不依赖硬编码猜测，完全基于实际配置
 *
 * @author yanxq
 * @date 2025-12-19
 */
public class ConfigBasedSchemaProvider {

    private static final Logger log = LoggerFactory.getLogger(ConfigBasedSchemaProvider.class);

    private final DatabaseSchemaProvider baseProvider;
    private final JdbcTemplate jdbcTemplate;

    /** 配置文件缓存: 表名 -> TableConf */
    private static final Map<String, TableConf> tableConfCache = new ConcurrentHashMap<>();

    /** 表分类缓存: 表名 -> 分类 */
    private static final Map<String, String> tableCategoryCache = new ConcurrentHashMap<>();

    /** 配置文件根目录 */
    private static final String CONF_ROOT = "src/main/resources/CONF";

    public ConfigBasedSchemaProvider() {
        this(DatabaseUtil.getJdbcTemplate(null));
    }

    public ConfigBasedSchemaProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.baseProvider = new DatabaseSchemaProvider(jdbcTemplate);
        loadAllConfigs();
    }

    /**
     * 加载所有配置文件
     */
    private void loadAllConfigs() {
        try {
            File confDir = new File(CONF_ROOT);
            if (!confDir.exists()) {
                log.warn("配置目录不存在: {}", CONF_ROOT);
                return;
            }

            List<File> jsonFiles = FileUtil.loopFiles(confDir).stream()
                .filter(f -> f.getName().endsWith(".json"))
                .filter(f -> !f.getPath().contains("analysis")) // 跳过分析相关配置
                .collect(Collectors.toList());

            log.info("开始加载配置文件: {} 个", jsonFiles.size());

            for (File jsonFile : jsonFiles) {
                try {
                    String content = FileUtil.readUtf8String(jsonFile);
                    TableConf conf = JSON.parseObject(content, TableConf.class);

                    if (conf != null && conf.getTableName() != null) {
                        tableConfCache.put(conf.getTableName(), conf);

                        // 推断分类
                        String category = inferCategory(conf.getTableName(), jsonFile.getPath());
                        tableCategoryCache.put(conf.getTableName(), category);

                        // 子表也加入缓存
                        if (conf.getList() != null) {
                            for (ColumnMapping cm : conf.getList()) {
                                if (cm.getTableName() != null) {
                                    tableCategoryCache.put(cm.getTableName(), category);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("跳过配置文件: {} - {}", jsonFile.getName(), e.getMessage());
                }
            }

            log.info("配置加载完成: {} 个表配置", tableConfCache.size());

        } catch (Exception e) {
            log.error("加载配置文件失败", e);
        }
    }

    /**
     * 根据表名和路径推断分类
     */
    private String inferCategory(String tableName, String path) {
        String lowerName = tableName.toLowerCase();
        String lowerPath = path.toLowerCase();

        // 基于路径判断
        if (lowerPath.contains("npcs")) return "NPC";
        if (lowerPath.contains("items")) return "道具";
        if (lowerPath.contains("skill")) return "技能";
        if (lowerPath.contains("quest")) return "任务";
        if (lowerPath.contains("world")) return "地图";

        // 基于表名判断
        if (lowerName.contains("npc")) return "NPC";
        if (lowerName.contains("item")) return "道具";
        if (lowerName.contains("skill")) return "技能";
        if (lowerName.contains("quest")) return "任务";
        if (lowerName.contains("world") || lowerName.contains("spawn")) return "地图";

        return "其他";
    }

    /**
     * 获取表的配置信息
     */
    public TableConf getTableConfig(String tableName) {
        return tableConfCache.get(tableName);
    }

    /**
     * 获取表的分类
     */
    public String getTableCategory(String tableName) {
        return tableCategoryCache.getOrDefault(tableName, "未分类");
    }

    /**
     * 获取表的关联表列表
     */
    public List<String> getRelatedTables(String tableName) {
        TableConf conf = getTableConfig(tableName);
        if (conf == null || conf.getList() == null) {
            return Collections.emptyList();
        }

        return conf.getList().stream()
            .map(ColumnMapping::getTableName)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    }

    /**
     * 智能推荐相关表
     */
    public List<String> recommendRelatedTables(String query) {
        Set<String> recommended = new HashSet<>();
        String lowerQuery = query.toLowerCase();

        // 基于查询关键字匹配表名
        for (String tableName : tableConfCache.keySet()) {
            String lowerTable = tableName.toLowerCase();

            // 直接包含匹配
            if (lowerQuery.contains(lowerTable.replace("_", ""))) {
                recommended.add(tableName);
                continue;
            }

            // 分类匹配
            String category = getTableCategory(tableName);
            if (matchesCategory(lowerQuery, category)) {
                recommended.add(tableName);
            }
        }

        // 如果没有匹配，返回常用核心表
        if (recommended.isEmpty()) {
            return getCoreTables();
        }

        // 限制返回数量
        return recommended.stream()
            .limit(10)
            .collect(Collectors.toList());
    }

    /**
     * 查询是否匹配分类
     */
    private boolean matchesCategory(String query, String category) {
        switch (category) {
            case "NPC":
                return query.contains("npc") || query.contains("怪物") ||
                       query.contains("boss") || query.contains("精英");
            case "道具":
                return query.contains("道具") || query.contains("装备") ||
                       query.contains("物品") || query.contains("武器");
            case "技能":
                return query.contains("技能") || query.contains("法术") ||
                       query.contains("魔法") || query.contains("伤害");
            case "任务":
                return query.contains("任务") || query.contains("剧情") ||
                       query.contains("主线") || query.contains("支线");
            case "地图":
                return query.contains("地图") || query.contains("刷怪") ||
                       query.contains("spawn") || query.contains("世界");
            default:
                return false;
        }
    }

    /**
     * 获取核心表列表
     */
    private List<String> getCoreTables() {
        List<String> allTables = new ArrayList<>(tableConfCache.keySet());

        // 优先返回client_开头的核心表
        return allTables.stream()
            .filter(t -> t.startsWith("client_"))
            .sorted()
            .limit(20)
            .collect(Collectors.toList());
    }

    /**
     * 生成增强的Schema描述
     */
    public String getEnhancedSchemaDescription(List<String> tableNames) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Aion游戏数据库Schema (基于实际配置)\n\n");

        if (tableNames == null || tableNames.isEmpty()) {
            tableNames = recommendRelatedTables("");
        }

        sb.append("## 表列表\n\n");

        for (String tableName : tableNames) {
            // 获取基础Schema
            DatabaseSchemaProvider.TableInfo tableInfo = baseProvider.getTableInfo(tableName);
            if (tableInfo == null) {
                continue;
            }

            // 获取配置信息
            TableConf conf = getTableConfig(tableName);
            String category = getTableCategory(tableName);

            sb.append("### 表: ").append(tableName);
            if (category != null && !category.equals("未分类")) {
                sb.append(" [").append(category).append("]");
            }
            sb.append("\n");

            // XML配置信息
            if (conf != null) {
                if (conf.getXmlRootTag() != null) {
                    sb.append("**XML根标签**: ").append(conf.getXmlRootTag()).append("\n");
                }
                if (conf.getXmlItemTag() != null) {
                    sb.append("**XML项标签**: ").append(conf.getXmlItemTag()).append("\n");
                }
            }

            // 字段列表 (从数据库实际获取)
            sb.append("**字段**:\n");
            for (DatabaseSchemaProvider.ColumnInfo col : tableInfo.getColumns()) {
                sb.append("  - `").append(col.getColumnName())
                  .append("` (").append(col.getDataType()).append(")");

                if (col.getComment() != null && !col.getComment().isEmpty()) {
                    sb.append(" -- ").append(col.getComment());
                }

                if (!col.isNullable()) {
                    sb.append(" [必填]");
                }

                if (tableInfo.getPrimaryKeys().contains(col.getColumnName())) {
                    sb.append(" [主键]");
                }

                sb.append("\n");
            }

            // 关联表 (从配置中获取)
            List<String> related = getRelatedTables(tableName);
            if (!related.isEmpty()) {
                sb.append("**子表**: ");
                sb.append(String.join(", ", related));
                sb.append("\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 生成SQL提示
     */
    public String generateSqlHints(String query) {
        StringBuilder hints = new StringBuilder();

        List<String> tables = recommendRelatedTables(query);
        if (!tables.isEmpty()) {
            hints.append("## 推荐使用的表\n\n");

            for (String tableName : tables) {
                String category = getTableCategory(tableName);
                hints.append("- **").append(tableName).append("**");
                if (!category.equals("未分类")) {
                    hints.append(" [").append(category).append("]");
                }

                TableConf conf = getTableConfig(tableName);
                if (conf != null && conf.getXmlItemTag() != null) {
                    hints.append(" (XML: ").append(conf.getXmlItemTag()).append(")");
                }

                hints.append("\n");
            }

            hints.append("\n");
        }

        return hints.toString();
    }

    /**
     * 获取所有已配置的表名
     */
    public List<String> getAllConfiguredTables() {
        return new ArrayList<>(tableConfCache.keySet());
    }

    /**
     * 按分类获取表
     */
    public Map<String, List<String>> getTablesByCategory() {
        Map<String, List<String>> result = new HashMap<>();

        for (Map.Entry<String, String> entry : tableCategoryCache.entrySet()) {
            String category = entry.getValue();
            String tableName = entry.getKey();

            result.computeIfAbsent(category, k -> new ArrayList<>()).add(tableName);
        }

        return result;
    }

    /**
     * 获取表的示例数据
     */
    public String getTableSampleData(String tableName, int limit) {
        try {
            String sql = "SELECT * FROM " + tableName + " LIMIT " + limit;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            if (rows.isEmpty()) {
                return "表 " + tableName + " 无数据";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("表 ").append(tableName).append(" 示例数据 (前").append(Math.min(3, rows.size())).append("行):\n");

            for (int i = 0; i < Math.min(3, rows.size()); i++) {
                Map<String, Object> row = rows.get(i);
                sb.append("  Row ").append(i + 1).append(": ");

                // 只显示前5个字段
                int count = 0;
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if (count >= 5) {
                        sb.append("...");
                        break;
                    }
                    sb.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
                    count++;
                }

                sb.append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("获取表示例数据失败: {}", tableName, e);
            return "获取示例数据失败: " + e.getMessage();
        }
    }

    /**
     * 获取基础Schema提供者
     */
    public DatabaseSchemaProvider getBaseProvider() {
        return baseProvider;
    }

    /**
     * 获取统计信息
     */
    public String getStatistics() {
        Map<String, List<String>> byCategory = getTablesByCategory();

        StringBuilder sb = new StringBuilder();
        sb.append("## 配置统计\n\n");
        sb.append("总表数: ").append(tableConfCache.size()).append("\n\n");

        sb.append("按分类统计:\n");
        for (Map.Entry<String, List<String>> entry : byCategory.entrySet()) {
            sb.append("  - ").append(entry.getKey()).append(": ")
              .append(entry.getValue().size()).append(" 个表\n");
        }

        return sb.toString();
    }
}
