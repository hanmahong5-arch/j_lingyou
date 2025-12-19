package red.jiuzhou.agent.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.ui.mapping.DatabaseTableScanner;
import red.jiuzhou.ui.mapping.DatabaseTableScanner.TableInfo;
import red.jiuzhou.ui.mapping.DatabaseTableScanner.ColumnInfo;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schema元数据服务
 *
 * 为AI Agent提供数据库表结构信息
 * 支持表名模糊查找、字段查询、游戏语义映射等
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class SchemaMetadataService {

    private static final Logger log = LoggerFactory.getLogger(SchemaMetadataService.class);

    /** 表元数据缓存 */
    private final Map<String, TableInfo> tableCache = new ConcurrentHashMap<>();

    /** 表名索引（小写 -> 原始表名） */
    private final Map<String, String> tableNameIndex = new ConcurrentHashMap<>();

    /** 游戏语义映射 */
    private final Map<String, String> semanticMappings = new HashMap<>();

    /** JdbcTemplate */
    private JdbcTemplate jdbcTemplate;

    /** 是否已初始化 */
    private boolean initialized = false;

    public SchemaMetadataService() {
        initSemanticMappings();
    }

    public SchemaMetadataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initSemanticMappings();
    }

    /**
     * 初始化游戏语义映射
     */
    private void initSemanticMappings() {
        // 品质映射
        semanticMappings.put("白色装备", "quality = 0");
        semanticMappings.put("绿色装备", "quality = 1");
        semanticMappings.put("蓝色装备", "quality = 2");
        semanticMappings.put("紫色装备", "quality = 4");
        semanticMappings.put("橙色装备", "quality = 5");
        semanticMappings.put("金色装备", "quality = 5");

        // 属性映射
        semanticMappings.put("火属性", "element_type = 1");
        semanticMappings.put("水属性", "element_type = 2");
        semanticMappings.put("风属性", "element_type = 3");
        semanticMappings.put("土属性", "element_type = 4");

        // 通用字段映射
        semanticMappings.put("攻击力", "attack");
        semanticMappings.put("物理攻击", "p_attack");
        semanticMappings.put("魔法攻击", "m_attack");
        semanticMappings.put("防御力", "defense");
        semanticMappings.put("物理防御", "p_defense");
        semanticMappings.put("魔法防御", "m_defense");
        semanticMappings.put("生命值", "hp");
        semanticMappings.put("血量", "hp");
        semanticMappings.put("魔法值", "mp");
        semanticMappings.put("蓝量", "mp");
        semanticMappings.put("等级", "level");
        semanticMappings.put("等级限制", "level_require");
        semanticMappings.put("最低等级", "min_level");
        semanticMappings.put("价格", "price");
        semanticMappings.put("售价", "sell_price");
        semanticMappings.put("购买价", "buy_price");
    }

    /**
     * 初始化元数据
     */
    public void initialize() {
        if (initialized) {
            return;
        }

        log.info("开始加载数据库表元数据...");
        long startTime = System.currentTimeMillis();

        try {
            List<TableInfo> tables = DatabaseTableScanner.scanAllTables();

            for (TableInfo table : tables) {
                String tableName = table.getTableName();
                tableCache.put(tableName, table);
                tableNameIndex.put(tableName.toLowerCase(), tableName);
            }

            initialized = true;
            log.info("成功加载 {} 个表的元数据，耗时 {}ms",
                    tables.size(), System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("加载表元数据失败", e);
        }
    }

    /**
     * 刷新元数据缓存
     */
    public void refresh() {
        initialized = false;
        tableCache.clear();
        tableNameIndex.clear();
        initialize();
    }

    /**
     * 获取所有表名
     */
    public List<String> getAllTableNames() {
        ensureInitialized();
        return new ArrayList<>(tableCache.keySet());
    }

    /**
     * 获取表信息
     */
    public TableInfo getTableInfo(String tableName) {
        ensureInitialized();

        // 先精确匹配
        TableInfo info = tableCache.get(tableName);
        if (info != null) {
            return info;
        }

        // 再尝试小写匹配
        String realName = tableNameIndex.get(tableName.toLowerCase());
        if (realName != null) {
            return tableCache.get(realName);
        }

        return null;
    }

    /**
     * 模糊查找表名
     */
    public List<String> findTablesByKeyword(String keyword) {
        ensureInitialized();

        List<String> results = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();

        for (String tableName : tableCache.keySet()) {
            if (tableName.toLowerCase().contains(lowerKeyword)) {
                results.add(tableName);
            }
        }

        return results;
    }

    /**
     * 获取表的字段列表
     */
    public List<ColumnInfo> getTableColumns(String tableName) {
        TableInfo info = getTableInfo(tableName);
        if (info != null) {
            return info.getColumns();
        }
        return Collections.emptyList();
    }

    /**
     * 检查表是否存在
     */
    public boolean tableExists(String tableName) {
        return getTableInfo(tableName) != null;
    }

    /**
     * 检查字段是否存在
     */
    public boolean columnExists(String tableName, String columnName) {
        TableInfo info = getTableInfo(tableName);
        if (info != null) {
            for (ColumnInfo col : info.getColumns()) {
                if (col.getColumnName().equalsIgnoreCase(columnName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取语义映射
     */
    public String getSemanticMapping(String semanticTerm) {
        return semanticMappings.get(semanticTerm);
    }

    /**
     * 添加自定义语义映射
     */
    public void addSemanticMapping(String term, String sqlFragment) {
        semanticMappings.put(term, sqlFragment);
    }

    /**
     * 生成表结构描述（用于AI提示词）
     *
     * @param tableNames 要描述的表名列表，null表示所有表
     * @return 表结构描述文本
     */
    public String generateSchemaDescription(List<String> tableNames) {
        ensureInitialized();

        StringBuilder sb = new StringBuilder();
        sb.append("## 数据库表结构\n\n");

        Collection<String> tables = tableNames != null ? tableNames : tableCache.keySet();
        int count = 0;

        for (String tableName : tables) {
            TableInfo info = tableCache.get(tableName);
            if (info == null) continue;

            if (count >= 50) {
                sb.append("\n... 共 ").append(tableCache.size()).append(" 个表，仅显示前50个\n");
                break;
            }

            sb.append("### ").append(tableName);
            if (info.getTableComment() != null && !info.getTableComment().isEmpty()) {
                sb.append(" (").append(info.getTableComment()).append(")");
            }
            sb.append("\n");

            // 显示关键字段（最多10个）
            List<ColumnInfo> columns = info.getColumns();
            int colCount = Math.min(columns.size(), 10);

            sb.append("| 字段名 | 类型 | 说明 |\n");
            sb.append("|--------|------|------|\n");

            for (int i = 0; i < colCount; i++) {
                ColumnInfo col = columns.get(i);
                sb.append("| ").append(col.getColumnName());
                sb.append(" | ").append(col.getDataType());
                sb.append(" | ").append(col.getComment() != null ? col.getComment() : "");
                if (col.isPrimaryKey()) {
                    sb.append(" [PK]");
                }
                sb.append(" |\n");
            }

            if (columns.size() > colCount) {
                sb.append("| ... | | 共 ").append(columns.size()).append(" 个字段 |\n");
            }

            sb.append("\n");
            count++;
        }

        return sb.toString();
    }

    /**
     * 生成简洁的表列表（用于AI）
     */
    public String generateTableList() {
        ensureInitialized();

        StringBuilder sb = new StringBuilder();
        sb.append("数据库中的表：\n");

        List<String> sortedNames = new ArrayList<>(tableCache.keySet());
        Collections.sort(sortedNames);

        // 按前缀分组
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        grouped.put("client_", new ArrayList<>());
        grouped.put("server_", new ArrayList<>());
        grouped.put("其他", new ArrayList<>());

        for (String name : sortedNames) {
            if (name.startsWith("client_")) {
                grouped.get("client_").add(name);
            } else if (name.startsWith("server_")) {
                grouped.get("server_").add(name);
            } else {
                grouped.get("其他").add(name);
            }
        }

        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                sb.append("\n### ").append(entry.getKey()).append("表 (")
                  .append(entry.getValue().size()).append("个)\n");

                for (String name : entry.getValue()) {
                    TableInfo info = tableCache.get(name);
                    sb.append("- ").append(name);
                    if (info.getTableComment() != null && !info.getTableComment().isEmpty()) {
                        sb.append(": ").append(info.getTableComment());
                    }
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * 生成游戏语义映射说明
     */
    public String generateSemanticMappingDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 游戏语义映射\n\n");
        sb.append("以下游戏术语可以直接使用：\n\n");

        // 按类别分组
        sb.append("### 装备品质\n");
        for (Map.Entry<String, String> entry : semanticMappings.entrySet()) {
            if (entry.getKey().contains("装备")) {
                sb.append("- \"").append(entry.getKey()).append("\" → `")
                  .append(entry.getValue()).append("`\n");
            }
        }

        sb.append("\n### 元素属性\n");
        for (Map.Entry<String, String> entry : semanticMappings.entrySet()) {
            if (entry.getKey().contains("属性")) {
                sb.append("- \"").append(entry.getKey()).append("\" → `")
                  .append(entry.getValue()).append("`\n");
            }
        }

        sb.append("\n### 通用字段\n");
        for (Map.Entry<String, String> entry : semanticMappings.entrySet()) {
            if (!entry.getKey().contains("装备") && !entry.getKey().contains("属性")) {
                sb.append("- \"").append(entry.getKey()).append("\" → `")
                  .append(entry.getValue()).append("`\n");
            }
        }

        return sb.toString();
    }

    /**
     * 根据关键词智能推荐表
     */
    public List<TableInfo> suggestTables(String keyword) {
        ensureInitialized();

        List<TableInfo> suggestions = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();

        // 关键词到表的映射
        Map<String, List<String>> keywordToTables = new HashMap<>();
        keywordToTables.put("物品", Arrays.asList("item", "client_item"));
        keywordToTables.put("装备", Arrays.asList("item", "equipment", "client_item"));
        keywordToTables.put("武器", Arrays.asList("item", "weapon", "client_item"));
        keywordToTables.put("技能", Arrays.asList("skill", "client_skill"));
        keywordToTables.put("任务", Arrays.asList("quest", "client_quest", "server_quest"));
        keywordToTables.put("NPC", Arrays.asList("npc", "client_npc"));
        keywordToTables.put("怪物", Arrays.asList("npc", "monster", "client_npc"));
        keywordToTables.put("商店", Arrays.asList("shop", "trade", "client_shop"));
        keywordToTables.put("地图", Arrays.asList("map", "world", "client_world"));

        // 检查关键词映射
        for (Map.Entry<String, List<String>> entry : keywordToTables.entrySet()) {
            if (lowerKeyword.contains(entry.getKey().toLowerCase())) {
                for (String tableName : entry.getValue()) {
                    List<String> matches = findTablesByKeyword(tableName);
                    for (String match : matches) {
                        TableInfo info = tableCache.get(match);
                        if (info != null && !suggestions.contains(info)) {
                            suggestions.add(info);
                        }
                    }
                }
            }
        }

        // 如果没有通过关键词映射找到，尝试直接搜索
        if (suggestions.isEmpty()) {
            List<String> matches = findTablesByKeyword(keyword);
            for (String match : matches) {
                TableInfo info = tableCache.get(match);
                if (info != null) {
                    suggestions.add(info);
                }
            }
        }

        return suggestions;
    }

    /**
     * 获取表的样本数据
     *
     * @param tableName 表名
     * @param limit 数量限制
     * @return 样本数据
     */
    public List<Map<String, Object>> getSampleData(String tableName, int limit) {
        if (jdbcTemplate == null) {
            log.warn("JdbcTemplate未设置，无法获取样本数据");
            return Collections.emptyList();
        }

        if (!tableExists(tableName)) {
            return Collections.emptyList();
        }

        try {
            String sql = String.format("SELECT * FROM `%s` LIMIT %d", tableName, Math.min(limit, 100));
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("获取表 {} 样本数据失败: {}", tableName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取表的行数
     */
    public long getTableRowCount(String tableName) {
        TableInfo info = getTableInfo(tableName);
        if (info != null) {
            return info.getRowCount();
        }
        return 0;
    }

    /**
     * 确保已初始化
     */
    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    // ========== Getter/Setter ==========

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int getTableCount() {
        return tableCache.size();
    }
}
