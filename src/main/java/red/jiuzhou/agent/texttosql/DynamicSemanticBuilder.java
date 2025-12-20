package red.jiuzhou.agent.texttosql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态语义映射构建器
 *
 * 基于TypeFieldDiscovery发现的字段，自动构建自然语言→SQL的语义映射
 * 支持设计师自定义和扩展
 *
 * @author Claude
 * @date 2025-12-20
 */
public class DynamicSemanticBuilder {

    private static final Logger log = LoggerFactory.getLogger(DynamicSemanticBuilder.class);

    private final TypeFieldDiscovery discovery;
    private final Map<String, SemanticMapping> mappings = new ConcurrentHashMap<>();
    private final Map<String, PresetContext> presetContexts = new ConcurrentHashMap<>();

    /**
     * 语义映射
     */
    public static class SemanticMapping {
        private String keyword;           // 自然语言关键词
        private String sqlCondition;      // SQL条件表达式
        private String tableName;         // 所属表
        private String columnName;        // 所属字段
        private String description;       // 描述
        private boolean enabled;          // 是否启用
        private boolean userDefined;      // 是否用户自定义
        private int useCount;             // 使用次数

        public SemanticMapping(String keyword, String sqlCondition, String tableName,
                             String columnName, String description) {
            this.keyword = keyword;
            this.sqlCondition = sqlCondition;
            this.tableName = tableName;
            this.columnName = columnName;
            this.description = description;
            this.enabled = true;
            this.userDefined = false;
            this.useCount = 0;
        }

        // Getters and Setters
        public String getKeyword() { return keyword; }
        public String getSqlCondition() { return sqlCondition; }
        public String getTableName() { return tableName; }
        public String getColumnName() { return columnName; }
        public String getDescription() { return description; }
        public boolean isEnabled() { return enabled; }
        public boolean isUserDefined() { return userDefined; }
        public int getUseCount() { return useCount; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public void setUserDefined(boolean userDefined) { this.userDefined = userDefined; }
        public void incrementUseCount() { this.useCount++; }

        public String getFullKey() {
            return tableName + "." + columnName + "." + keyword;
        }
    }

    /**
     * 预设上下文（设计师可选的上下文模板）
     */
    public static class PresetContext {
        private String id;
        private String name;              // 名称
        private String description;       // 描述
        private String category;          // 分类
        private List<SemanticMapping> mappings;  // 包含的映射
        private boolean enabled;          // 是否启用

        public PresetContext(String id, String name, String description, String category) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.mappings = new ArrayList<>();
            this.enabled = true;
        }

        public void addMapping(SemanticMapping mapping) {
            this.mappings.add(mapping);
        }

        // Getters and Setters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public List<SemanticMapping> getMappings() { return mappings; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public DynamicSemanticBuilder(JdbcTemplate jdbcTemplate) {
        this.discovery = new TypeFieldDiscovery(jdbcTemplate);
    }

    /**
     * 自动构建语义映射
     */
    public void buildSemanticMappings() {
        log.info("开始构建动态语义映射...");

        List<TypeFieldDiscovery.TypeFieldInfo> typeFields = discovery.discoverAllTypeFields();

        for (TypeFieldDiscovery.TypeFieldInfo field : typeFields) {
            if (!field.isEnumLike()) {
                // 值域过大，跳过
                continue;
            }

            buildMappingsForField(field);
        }

        // 构建预设上下文
        buildPresetContexts();

        log.info("动态语义映射构建完成，共 {} 个映射，{} 个预设上下文",
            mappings.size(), presetContexts.size());
    }

    /**
     * 为单个字段构建映射
     */
    private void buildMappingsForField(TypeFieldDiscovery.TypeFieldInfo field) {
        String tableName = field.getTableName();
        String columnName = field.getColumnName();
        TypeFieldDiscovery.SemanticType semanticType = field.getSemanticType();

        for (TypeFieldDiscovery.ValueInfo valueInfo : field.getValues()) {
            String value = valueInfo.getValueString();

            // 根据语义类型生成关键词和SQL条件
            List<String> keywords = generateKeywords(value, semanticType, columnName);
            String sqlCondition = generateSqlCondition(columnName, value, field.getColumnType());

            for (String keyword : keywords) {
                String description = String.format("%s类型: %s (在%s表中占%.1f%%)",
                    semanticType.getDescription(), value, tableName, valueInfo.getPercentage());

                SemanticMapping mapping = new SemanticMapping(
                    keyword, sqlCondition, tableName, columnName, description
                );

                mappings.put(mapping.getFullKey(), mapping);
            }
        }
    }

    /**
     * 生成关键词
     */
    private List<String> generateKeywords(String value, TypeFieldDiscovery.SemanticType semanticType,
                                         String columnName) {
        List<String> keywords = new ArrayList<>();
        String lowerValue = value.toLowerCase();

        // 原始值作为关键词
        keywords.add(value);

        // 根据语义类型生成额外关键词
        switch (semanticType) {
            case ELEMENT:
                // 元素属性
                if (lowerValue.equals("fire") || lowerValue.equals("1")) {
                    keywords.add("火属性");
                    keywords.add("火系");
                    keywords.add("火元素");
                } else if (lowerValue.equals("water") || lowerValue.equals("2")) {
                    keywords.add("水属性");
                    keywords.add("水系");
                    keywords.add("水元素");
                } else if (lowerValue.equals("wind") || lowerValue.equals("3")) {
                    keywords.add("风属性");
                    keywords.add("风系");
                    keywords.add("风元素");
                } else if (lowerValue.equals("earth") || lowerValue.equals("4")) {
                    keywords.add("土属性");
                    keywords.add("土系");
                    keywords.add("土元素");
                } else if (lowerValue.equals("light") || lowerValue.equals("5")) {
                    keywords.add("光属性");
                    keywords.add("光系");
                    keywords.add("光元素");
                } else if (lowerValue.equals("dark") || lowerValue.equals("6")) {
                    keywords.add("暗属性");
                    keywords.add("暗系");
                    keywords.add("暗元素");
                }
                break;

            case QUALITY:
                // 品质等级
                if (lowerValue.equals("common") || lowerValue.equals("0") || lowerValue.equals("white")) {
                    keywords.add("普通");
                    keywords.add("白色品质");
                    keywords.add("白装");
                } else if (lowerValue.equals("uncommon") || lowerValue.equals("1") || lowerValue.equals("green")) {
                    keywords.add("精良");
                    keywords.add("绿色品质");
                    keywords.add("绿装");
                } else if (lowerValue.equals("rare") || lowerValue.equals("2") || lowerValue.equals("blue")) {
                    keywords.add("稀有");
                    keywords.add("蓝色品质");
                    keywords.add("蓝装");
                } else if (lowerValue.equals("epic") || lowerValue.equals("3") || lowerValue.equals("purple")) {
                    keywords.add("史诗");
                    keywords.add("紫色品质");
                    keywords.add("紫装");
                } else if (lowerValue.equals("legendary") || lowerValue.equals("4") || lowerValue.equals("orange")) {
                    keywords.add("传说");
                    keywords.add("橙色品质");
                    keywords.add("橙装");
                } else if (lowerValue.equals("mythic") || lowerValue.equals("5")) {
                    keywords.add("神话");
                    keywords.add("神器");
                }
                break;

            case NPC_RANK:
                // NPC等级
                if (lowerValue.equals("normal") || lowerValue.equals("0")) {
                    keywords.add("普通怪");
                    keywords.add("小怪");
                } else if (lowerValue.equals("elite") || lowerValue.equals("1")) {
                    keywords.add("精英怪");
                    keywords.add("精英");
                } else if (lowerValue.equals("boss") || lowerValue.contains("boss")) {
                    keywords.add("BOSS");
                    keywords.add("首领");
                    keywords.add("头目");
                } else if (lowerValue.contains("world")) {
                    keywords.add("世界BOSS");
                    keywords.add("世界首领");
                }
                break;

            case BOOLEAN_FLAG:
                // 布尔标志
                if (lowerValue.equals("1") || lowerValue.equals("true") || lowerValue.equals("yes")) {
                    keywords.add("是");
                    keywords.add("启用");
                    keywords.add("开启");
                } else if (lowerValue.equals("0") || lowerValue.equals("false") || lowerValue.equals("no")) {
                    keywords.add("否");
                    keywords.add("禁用");
                    keywords.add("关闭");
                }
                break;
        }

        return keywords;
    }

    /**
     * 生成SQL条件
     */
    private String generateSqlCondition(String columnName, String value, String columnType) {
        boolean isNumeric = columnType.contains("int") || columnType.contains("decimal") ||
                          columnType.contains("float") || columnType.contains("double");

        if (isNumeric) {
            // 数字类型，直接比较
            return String.format("%s = %s", columnName, value);
        } else {
            // 字符串类型，需要加引号
            return String.format("%s = '%s'", columnName, value.replace("'", "''"));
        }
    }

    /**
     * 构建预设上下文
     */
    private void buildPresetContexts() {
        // 按语义类型分组
        Map<TypeFieldDiscovery.SemanticType, List<SemanticMapping>> grouped = new HashMap<>();
        for (SemanticMapping mapping : mappings.values()) {
            TypeFieldDiscovery.TypeFieldInfo field = findFieldInfo(mapping.getTableName(), mapping.getColumnName());
            if (field != null) {
                grouped.computeIfAbsent(field.getSemanticType(), k -> new ArrayList<>()).add(mapping);
            }
        }

        // 为每个语义类型创建预设上下文
        for (Map.Entry<TypeFieldDiscovery.SemanticType, List<SemanticMapping>> entry : grouped.entrySet()) {
            TypeFieldDiscovery.SemanticType type = entry.getKey();
            List<SemanticMapping> typeMappings = entry.getValue();

            PresetContext preset = new PresetContext(
                type.name().toLowerCase(),
                type.getDescription(),
                String.format("包含所有%s相关的语义映射", type.getDescription()),
                "自动生成"
            );

            for (SemanticMapping mapping : typeMappings) {
                preset.addMapping(mapping);
            }

            presetContexts.put(preset.getId(), preset);
        }

        // 创建通用预设上下文
        createCommonPresets();
    }

    /**
     * 创建通用预设上下文
     */
    private void createCommonPresets() {
        // 物品查询上下文
        PresetContext itemPreset = new PresetContext(
            "item_query",
            "物品查询",
            "包含物品相关的所有语义（品质、类型等）",
            "通用查询"
        );

        for (SemanticMapping mapping : mappings.values()) {
            if (mapping.getTableName().toLowerCase().contains("item") ||
                mapping.getTableName().toLowerCase().contains("armor") ||
                mapping.getTableName().toLowerCase().contains("weapon")) {
                itemPreset.addMapping(mapping);
            }
        }

        if (!itemPreset.getMappings().isEmpty()) {
            presetContexts.put(itemPreset.getId(), itemPreset);
        }

        // 技能查询上下文
        PresetContext skillPreset = new PresetContext(
            "skill_query",
            "技能查询",
            "包含技能相关的所有语义（元素、类型等）",
            "通用查询"
        );

        for (SemanticMapping mapping : mappings.values()) {
            if (mapping.getTableName().toLowerCase().contains("skill")) {
                skillPreset.addMapping(mapping);
            }
        }

        if (!skillPreset.getMappings().isEmpty()) {
            presetContexts.put(skillPreset.getId(), skillPreset);
        }

        // NPC查询上下文
        PresetContext npcPreset = new PresetContext(
            "npc_query",
            "NPC查询",
            "包含NPC相关的所有语义（等级、类型等）",
            "通用查询"
        );

        for (SemanticMapping mapping : mappings.values()) {
            if (mapping.getTableName().toLowerCase().contains("npc") ||
                mapping.getTableName().toLowerCase().contains("monster")) {
                npcPreset.addMapping(mapping);
            }
        }

        if (!npcPreset.getMappings().isEmpty()) {
            presetContexts.put(npcPreset.getId(), npcPreset);
        }
    }

    /**
     * 查找字段信息
     */
    private TypeFieldDiscovery.TypeFieldInfo findFieldInfo(String tableName, String columnName) {
        List<TypeFieldDiscovery.TypeFieldInfo> fields = discovery.discoverAllTypeFields();
        for (TypeFieldDiscovery.TypeFieldInfo field : fields) {
            if (field.getTableName().equals(tableName) && field.getColumnName().equals(columnName)) {
                return field;
            }
        }
        return null;
    }

    /**
     * 生成动态提示词
     */
    public String generateDynamicPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("# 动态语义映射\n\n");
        sb.append("根据实际数据库分析，以下是自动发现的游戏术语映射：\n\n");

        // 按预设上下文组织
        for (PresetContext preset : presetContexts.values()) {
            if (!preset.isEnabled() || preset.getMappings().isEmpty()) {
                continue;
            }

            sb.append(String.format("## %s\n\n", preset.getName()));
            sb.append(String.format("*%s*\n\n", preset.getDescription()));

            // 按表分组
            Map<String, List<SemanticMapping>> byTable = new HashMap<>();
            for (SemanticMapping mapping : preset.getMappings()) {
                if (mapping.isEnabled()) {
                    byTable.computeIfAbsent(mapping.getTableName(), k -> new ArrayList<>()).add(mapping);
                }
            }

            for (Map.Entry<String, List<SemanticMapping>> entry : byTable.entrySet()) {
                String tableName = entry.getKey();
                List<SemanticMapping> tableMappings = entry.getValue();

                sb.append(String.format("### 表: %s\n\n", tableName));

                // 按字段分组
                Map<String, List<SemanticMapping>> byColumn = new HashMap<>();
                for (SemanticMapping mapping : tableMappings) {
                    byColumn.computeIfAbsent(mapping.getColumnName(), k -> new ArrayList<>()).add(mapping);
                }

                for (Map.Entry<String, List<SemanticMapping>> colEntry : byColumn.entrySet()) {
                    String columnName = colEntry.getKey();
                    List<SemanticMapping> colMappings = colEntry.getValue();

                    sb.append(String.format("**字段: %s**\n\n", columnName));
                    for (SemanticMapping mapping : colMappings) {
                        sb.append(String.format("- \"%s\" → `%s`\n",
                            mapping.getKeyword(), mapping.getSqlCondition()));
                    }
                    sb.append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * 获取所有语义映射
     */
    public Map<String, SemanticMapping> getMappings() {
        return new HashMap<>(mappings);
    }

    /**
     * 获取所有预设上下文
     */
    public Map<String, PresetContext> getPresetContexts() {
        return new HashMap<>(presetContexts);
    }

    /**
     * 启用/禁用预设上下文
     */
    public void setPresetEnabled(String presetId, boolean enabled) {
        PresetContext preset = presetContexts.get(presetId);
        if (preset != null) {
            preset.setEnabled(enabled);
            log.info("预设上下文 {} 已{}", preset.getName(), enabled ? "启用" : "禁用");
        }
    }

    /**
     * 添加用户自定义映射
     */
    public void addUserMapping(String keyword, String sqlCondition, String tableName,
                              String columnName, String description) {
        SemanticMapping mapping = new SemanticMapping(keyword, sqlCondition, tableName, columnName, description);
        mapping.setUserDefined(true);
        mappings.put(mapping.getFullKey(), mapping);
        log.info("添加用户自定义映射: {} → {}", keyword, sqlCondition);
    }

    /**
     * 删除映射
     */
    public void removeMapping(String fullKey) {
        SemanticMapping removed = mappings.remove(fullKey);
        if (removed != null) {
            log.info("删除映射: {} → {}", removed.getKeyword(), removed.getSqlCondition());
        }
    }
}
