package red.jiuzhou.agent.context;

import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.analysis.aion.AionMechanismCategory;
import red.jiuzhou.analysis.aion.MechanismFileMapper;
import red.jiuzhou.util.DatabaseUtil;

import java.io.File;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 上下文收集器
 *
 * 负责从不同的数据源（菜单树节点、表格行、机制分类等）收集完整的设计上下文，
 * 为AI助手提供足够的信息来理解设计师的意图。
 *
 * @author Claude
 * @version 1.0
 */
public class ContextCollector {

    private static final Logger log = LoggerFactory.getLogger(ContextCollector.class);

    private final JdbcTemplate jdbcTemplate;

    public ContextCollector() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
    }

    public ContextCollector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ==================== 从菜单树节点收集 ====================

    /**
     * 从菜单树节点收集上下文
     *
     * @param item 树节点
     * @param pathResolver 路径解析器（从节点提取文件路径）
     * @return 设计上下文
     */
    public <T> DesignContext collectFromTreeItem(TreeItem<T> item,
                                                  java.util.function.Function<TreeItem<T>, String> pathResolver) {
        if (item == null) {
            return new DesignContext(DesignContext.ContextLocation.FILE);
        }

        String filePath = pathResolver != null ? pathResolver.apply(item) : item.getValue().toString();
        DesignContext ctx = DesignContext.fromFile(filePath);

        try {
            // 检测机制分类
            AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(filePath);
            ctx.setMechanism(mechanism);

            // 推断表名
            String tableName = inferTableName(filePath);
            if (tableName != null) {
                ctx.setTableName(tableName);

                // 收集表结构（异步，但这里同步等待）
                TableMetadata schema = collectTableMetadata(tableName);
                ctx.setTableSchema(schema);

                // 收集引用关系
                collectReferences(ctx, tableName);
            }

            // 收集相关文件
            collectRelatedFiles(ctx, filePath, mechanism);

        } catch (Exception e) {
            log.warn("收集上下文时出错: {}", e.getMessage());
        }

        return ctx;
    }

    /**
     * 异步收集菜单树节点上下文
     */
    public <T> CompletableFuture<DesignContext> collectFromTreeItemAsync(
            TreeItem<T> item,
            java.util.function.Function<TreeItem<T>, String> pathResolver) {
        return CompletableFuture.supplyAsync(() -> collectFromTreeItem(item, pathResolver));
    }

    // ==================== 从表格行收集 ====================

    /**
     * 从表格行收集上下文
     *
     * @param tableName 表名
     * @param rowData 行数据
     * @return 设计上下文
     */
    public DesignContext collectFromTableRow(String tableName, Map<String, Object> rowData) {
        DesignContext ctx = DesignContext.fromRow(tableName, rowData);

        try {
            // 收集表结构
            TableMetadata schema = collectTableMetadata(tableName);
            ctx.setTableSchema(schema);

            // 尝试检测机制
            AionMechanismCategory mechanism = inferMechanismFromTableName(tableName);
            ctx.setMechanism(mechanism);

            // 收集引用关系
            collectReferences(ctx, tableName);

            // 添加语义提示
            addSemanticHints(ctx, tableName, rowData);

        } catch (Exception e) {
            log.warn("从表格行收集上下文时出错: {}", e.getMessage());
        }

        return ctx;
    }

    // ==================== 从机制分类收集 ====================

    /**
     * 从机制分类收集上下文
     *
     * @param mechanism 机制分类
     * @return 设计上下文
     */
    public DesignContext collectFromMechanism(AionMechanismCategory mechanism) {
        DesignContext ctx = DesignContext.fromMechanism(mechanism);

        try {
            // 获取机制相关的所有文件
            Set<String> relatedFilesSet = MechanismFileMapper.getInstance().getFiles(mechanism);
            List<String> relatedFiles = new ArrayList<>(relatedFilesSet);
            ctx.setRelatedFiles(relatedFiles);

            // 获取机制相关的表（通过文件名推断）
            Set<String> relatedTables = new HashSet<>();
            for (String file : relatedFiles) {
                String tableName = inferTableName(file);
                if (tableName != null) {
                    relatedTables.add(tableName);
                }
            }

            // 添加机制特定的语义提示
            addMechanismSemantics(ctx, mechanism);

        } catch (Exception e) {
            log.warn("从机制收集上下文时出错: {}", e.getMessage());
        }

        return ctx;
    }

    // ==================== 辅助方法 ====================

    /**
     * 从文件路径推断表名
     */
    private String inferTableName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        // 提取文件名（不含扩展名）
        File file = new File(filePath);
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            fileName = fileName.substring(0, dotIndex);
        }

        // 常见的命名映射
        // client_skills.xml → client_skills
        // skill_base.xml → skill_base
        return fileName.toLowerCase();
    }

    /**
     * 从表名推断机制分类
     */
    private AionMechanismCategory inferMechanismFromTableName(String tableName) {
        if (tableName == null) {
            return AionMechanismCategory.OTHER;
        }

        String lowerName = tableName.toLowerCase();

        // 根据表名前缀/关键词推断机制
        if (lowerName.contains("skill")) return AionMechanismCategory.SKILL;
        if (lowerName.contains("item")) return AionMechanismCategory.ITEM;
        if (lowerName.contains("npc")) return AionMechanismCategory.NPC;
        if (lowerName.contains("quest")) return AionMechanismCategory.QUEST;
        if (lowerName.contains("abyss")) return AionMechanismCategory.ABYSS;
        if (lowerName.contains("instance") || lowerName.contains("dungeon"))
            return AionMechanismCategory.INSTANCE;
        if (lowerName.contains("drop")) return AionMechanismCategory.DROP;
        if (lowerName.contains("spawn")) return AionMechanismCategory.NPC;
        if (lowerName.contains("shop") || lowerName.contains("goods"))
            return AionMechanismCategory.SHOP;
        if (lowerName.contains("housing")) return AionMechanismCategory.HOUSING;
        if (lowerName.contains("luna")) return AionMechanismCategory.LUNA;

        return AionMechanismCategory.OTHER;
    }

    /**
     * 收集表结构元数据
     */
    private TableMetadata collectTableMetadata(String tableName) {
        TableMetadata metadata = new TableMetadata(tableName);

        try {
            // 获取表的列信息
            jdbcTemplate.query(
                "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT, COLUMN_KEY, IS_NULLABLE, COLUMN_DEFAULT " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE table_schema = current_schema() AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION",
                rs -> {
                    ColumnMetadata col = new ColumnMetadata();
                    col.setName(rs.getString("COLUMN_NAME"));
                    col.setType(rs.getString("DATA_TYPE"));
                    col.setComment(rs.getString("COLUMN_COMMENT"));
                    col.setPrimaryKey("PRI".equals(rs.getString("COLUMN_KEY")));
                    col.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
                    col.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
                    metadata.addColumn(col);
                },
                tableName
            );

            // 获取表行数
            try {
                Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + tableName,
                    Long.class
                );
                metadata.setRowCount(count != null ? count : 0);
            } catch (Exception e) {
                // 表可能不存在
                log.debug("无法获取表 {} 的行数", tableName);
            }

        } catch (Exception e) {
            log.debug("无法获取表 {} 的元数据: {}", tableName, e.getMessage());
        }

        return metadata;
    }

    /**
     * 收集字段引用关系
     */
    private void collectReferences(DesignContext ctx, String tableName) {
        if (tableName == null) return;

        try {
            // 分析ID字段的引用（简化版，基于命名约定）
            // 例如: item_id 字段可能引用 items 表
            if (ctx.getTableSchema() != null) {
                for (ColumnMetadata col : ctx.getTableSchema().getColumns()) {
                    String colName = col.getName().toLowerCase();

                    // 检测 _id 后缀的字段
                    if (colName.endsWith("_id") && !colName.equals("id")) {
                        String refTable = colName.substring(0, colName.length() - 3);
                        // 添加复数形式
                        if (!refTable.endsWith("s")) {
                            refTable = refTable + "s";
                        }

                        FieldReference ref = FieldReference.outgoing(
                            tableName, col.getName(), refTable, "id"
                        );
                        ctx.addOutgoingRef(ref);
                    }
                }
            }

        } catch (Exception e) {
            log.debug("收集引用关系时出错: {}", e.getMessage());
        }
    }

    /**
     * 收集相关文件
     */
    private void collectRelatedFiles(DesignContext ctx, String filePath,
                                     AionMechanismCategory mechanism) {
        if (mechanism == null) return;

        try {
            // 获取同一机制下的其他文件（限制数量）
            Set<String> files = MechanismFileMapper.getInstance().getFiles(mechanism);
            if (files != null && !files.isEmpty()) {
                // 只保留前10个相关文件
                files.stream()
                    .filter(f -> !f.equals(filePath))
                    .limit(10)
                    .forEach(ctx::addRelatedFile);
            }
        } catch (Exception e) {
            log.debug("收集相关文件时出错: {}", e.getMessage());
        }
    }

    /**
     * 添加语义提示
     */
    private void addSemanticHints(DesignContext ctx, String tableName,
                                  Map<String, Object> rowData) {
        // 根据常见字段添加语义提示
        if (rowData.containsKey("quality")) {
            Object quality = rowData.get("quality");
            ctx.addSemanticHint("品质", getQualityName(quality));
        }

        if (rowData.containsKey("level")) {
            ctx.addSemanticHint("等级", String.valueOf(rowData.get("level")));
        }

        if (rowData.containsKey("race")) {
            Object race = rowData.get("race");
            ctx.addSemanticHint("种族", getRaceName(race));
        }

        if (rowData.containsKey("class") || rowData.containsKey("player_class")) {
            Object playerClass = rowData.containsKey("class") ?
                rowData.get("class") : rowData.get("player_class");
            ctx.addSemanticHint("职业", getClassName(playerClass));
        }
    }

    /**
     * 添加机制特定的语义
     */
    private void addMechanismSemantics(DesignContext ctx, AionMechanismCategory mechanism) {
        switch (mechanism) {
            case SKILL -> {
                ctx.addSemanticHint("系统", "技能系统");
                ctx.addSemanticHint("相关表", "skill_base, skill_enhance, skill_combo");
            }
            case ITEM -> {
                ctx.addSemanticHint("系统", "物品系统");
                ctx.addSemanticHint("品质说明", "0=白色, 1=绿色, 2=蓝色, 3=紫色, 4=橙色, 5=金色");
            }
            case NPC -> {
                ctx.addSemanticHint("系统", "NPC系统");
                ctx.addSemanticHint("类型说明", "普通怪/精英/BOSS");
            }
            case QUEST -> {
                ctx.addSemanticHint("系统", "任务系统");
                ctx.addSemanticHint("相关表", "quests, quest_data, quest_rewards");
            }
            case ABYSS -> {
                ctx.addSemanticHint("系统", "深渊系统");
                ctx.addSemanticHint("说明", "Aion特有的PvPvE区域");
            }
            case INSTANCE -> {
                ctx.addSemanticHint("系统", "副本系统");
                ctx.addSemanticHint("相关表", "instance_cooltime, instance_npc");
            }
            default -> {
                // 其他机制暂不添加特定语义
            }
        }
    }

    // ==================== 枚举值转换 ====================

    private String getQualityName(Object quality) {
        if (quality == null) return "未知";
        int q = quality instanceof Number ? ((Number) quality).intValue() : -1;
        return switch (q) {
            case 0 -> "白色(普通)";
            case 1 -> "绿色(优秀)";
            case 2 -> "蓝色(精良)";
            case 3 -> "紫色(史诗)";
            case 4 -> "橙色(传说)";
            case 5 -> "金色(神话)";
            default -> String.valueOf(quality);
        };
    }

    private String getRaceName(Object race) {
        if (race == null) return "未知";
        String r = race.toString().toLowerCase();
        return switch (r) {
            case "elyos", "0" -> "天族";
            case "asmodians", "1" -> "魔族";
            case "all", "2" -> "全部种族";
            default -> r;
        };
    }

    private String getClassName(Object playerClass) {
        if (playerClass == null) return "未知";
        String c = playerClass.toString().toLowerCase();
        return switch (c) {
            case "warrior", "1" -> "战士";
            case "scout", "2" -> "斥候";
            case "mage", "3" -> "法师";
            case "priest", "4" -> "牧师";
            case "engineer", "5" -> "工程师";
            case "artist", "6" -> "艺术家";
            default -> c;
        };
    }
}
