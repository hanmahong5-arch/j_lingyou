package red.jiuzhou.langchain.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 游戏表结构嵌入服务
 *
 * <p>将数据库表结构信息嵌入到向量存储，用于 RAG 检索。
 *
 * <p>嵌入内容：
 * <ul>
 *   <li>表结构元数据 - 表名、列名、数据类型、注释</li>
 *   <li>游戏语义映射 - 术语解释（紫装=quality:3等）</li>
 *   <li>SQL 示例库 - 常用查询示例</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
@Service
@ConditionalOnBean({EmbeddingStore.class, EmbeddingModel.class})
public class GameSchemaEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(GameSchemaEmbeddingService.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    private final AtomicInteger indexedTableCount = new AtomicInteger(0);
    private boolean initialized = false;

    public GameSchemaEmbeddingService(
            JdbcTemplate jdbcTemplate,
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 初始化时索引表结构
     */
    @PostConstruct
    public void initialize() {
        log.info("开始初始化游戏表结构嵌入服务...");

        try {
            // 索引表结构
            indexTableSchemas();

            // 索引游戏语义
            indexGameSemantics();

            // 索引 SQL 示例
            indexSqlExamples();

            initialized = true;
            log.info("游戏表结构嵌入服务初始化完成，索引了 {} 个表", indexedTableCount.get());

        } catch (Exception e) {
            log.error("初始化嵌入服务失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 索引所有表结构
     */
    private void indexTableSchemas() {
        log.info("开始索引表结构...");

        List<String> tables = getTableList();
        log.info("发现 {} 个表", tables.size());

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        for (String tableName : tables) {
            try {
                String description = generateTableDescription(tableName);
                if (description != null && !description.isEmpty()) {
                    Document doc = Document.from(
                            description,
                            Metadata.from(Map.of(
                                    "type", "table_schema",
                                    "table", tableName
                            ))
                    );
                    ingestor.ingest(doc);
                    indexedTableCount.incrementAndGet();
                }
            } catch (Exception e) {
                log.debug("索引表 {} 失败: {}", tableName, e.getMessage());
            }
        }

        log.info("表结构索引完成");
    }

    /**
     * 索引游戏语义映射
     */
    private void indexGameSemantics() {
        log.info("索引游戏语义映射...");

        // 游戏术语映射
        List<String> semantics = List.of(
                // 装备品质
                "白色装备/普通装备 对应 quality=1 或 quality_level=1",
                "绿色装备/优秀装备 对应 quality=2 或 quality_level=2",
                "蓝色装备/稀有装备 对应 quality=3 或 quality_level=3",
                "紫色装备/紫装/史诗装备 对应 quality=4 或 quality_level=4",
                "橙色装备/金装/传说装备 对应 quality=5 或 quality_level=5",
                "红色装备/神器 对应 quality=6 或 quality_level=6",

                // 装备类型
                "武器 对应 equipment_type=1 或 category=weapon",
                "防具/盔甲 对应 equipment_type=2 或 category=armor",
                "饰品/首饰 对应 equipment_type=3 或 category=accessory",
                "消耗品 对应 item_type=consumable",
                "材料 对应 item_type=material",

                // 等级相关
                "满级/最高级 通常是 level=60 或 level=80 或 max_level 字段",
                "初始等级/1级 对应 level=1 或 min_level=1",
                "等级需求 对应 require_level 或 level_requirement 字段",

                // NPC 相关
                "BOSS/首领 对应 npc_type=boss 或 is_boss=1",
                "精英怪 对应 npc_type=elite 或 is_elite=1",
                "普通怪 对应 npc_type=normal 或 is_normal=1",
                "友好NPC 对应 faction=friendly 或 is_hostile=0",

                // 技能相关
                "主动技能 对应 skill_type=active",
                "被动技能 对应 skill_type=passive",
                "天赋/天赋技能 对应 skill_type=talent",
                "冷却时间/CD 对应 cooldown 或 cd 字段",

                // 任务相关
                "主线任务 对应 quest_type=main 或 is_main_quest=1",
                "支线任务 对应 quest_type=side",
                "日常任务 对应 quest_type=daily",
                "每周任务 对应 quest_type=weekly"
        );

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        for (String semantic : semantics) {
            Document doc = Document.from(
                    semantic,
                    Metadata.from("type", "game_semantic")
            );
            ingestor.ingest(doc);
        }

        log.info("游戏语义索引完成，共 {} 条", semantics.size());
    }

    /**
     * 索引 SQL 示例
     */
    private void indexSqlExamples() {
        log.info("索引SQL示例...");

        List<String> examples = List.of(
                // 查询示例
                "查询所有紫色武器: SELECT * FROM item_templates WHERE quality = 4 AND equipment_type = 1",
                "查询50级以上的装备: SELECT * FROM item_templates WHERE require_level >= 50",
                "查询BOSS类型的NPC: SELECT * FROM npc_templates WHERE npc_type = 'boss' OR is_boss = 1",
                "查询主线任务: SELECT * FROM quest_templates WHERE quest_type = 'main'",
                "查询技能冷却时间: SELECT name, cooldown FROM skill_templates ORDER BY cooldown DESC",

                // 统计示例
                "统计各品质装备数量: SELECT quality, COUNT(*) as count FROM item_templates GROUP BY quality",
                "统计各等级段物品分布: SELECT FLOOR(level/10)*10 as level_range, COUNT(*) FROM item_templates GROUP BY level_range",
                "查看NPC等级分布: SELECT level, COUNT(*) as count FROM npc_templates GROUP BY level ORDER BY level",

                // 修改示例
                "批量提升武器攻击力10%: UPDATE item_templates SET attack = attack * 1.1 WHERE equipment_type = 1",
                "修改特定物品价格: UPDATE item_templates SET price = 1000 WHERE id = 12345",
                "调整BOSS的血量: UPDATE npc_templates SET hp = hp * 1.2 WHERE is_boss = 1"
        );

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        for (String example : examples) {
            Document doc = Document.from(
                    example,
                    Metadata.from("type", "sql_example")
            );
            ingestor.ingest(doc);
        }

        log.info("SQL示例索引完成，共 {} 条", examples.size());
    }

    /**
     * 获取所有表名
     */
    private List<String> getTableList() {
        try {
            return jdbcTemplate.queryForList("SHOW TABLES", String.class);
        } catch (Exception e) {
            log.error("获取表列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 生成表描述
     */
    private String generateTableDescription(String tableName) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("表名: ").append(tableName).append("\n");

            // 获取列信息
            String sql = """
                SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT, IS_NULLABLE, COLUMN_KEY
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """;

            List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, tableName);

            sb.append("列信息:\n");
            for (Map<String, Object> col : columns) {
                String colName = (String) col.get("COLUMN_NAME");
                String dataType = (String) col.get("DATA_TYPE");
                String comment = (String) col.get("COLUMN_COMMENT");
                String isKey = (String) col.get("COLUMN_KEY");

                sb.append("  - ").append(colName)
                        .append(" (").append(dataType).append(")");

                if ("PRI".equals(isKey)) {
                    sb.append(" [主键]");
                }

                if (comment != null && !comment.isEmpty()) {
                    sb.append(": ").append(comment);
                }

                sb.append("\n");
            }

            // 获取示例数据
            try {
                String sampleSql = "SELECT * FROM `" + tableName + "` LIMIT 3";
                List<Map<String, Object>> samples = jdbcTemplate.queryForList(sampleSql);
                if (!samples.isEmpty()) {
                    sb.append("示例数据:\n");
                    for (Map<String, Object> sample : samples) {
                        sb.append("  ").append(sample.toString()).append("\n");
                    }
                }
            } catch (Exception e) {
                // 忽略示例数据获取失败
            }

            return sb.toString();

        } catch (Exception e) {
            log.debug("生成表描述失败 {}: {}", tableName, e.getMessage());
            return null;
        }
    }

    /**
     * 重新索引所有内容
     */
    public void reindex() {
        log.info("重新索引所有内容...");
        indexedTableCount.set(0);
        initialize();
    }

    /**
     * 添加自定义文档
     */
    public void addDocument(String content, Map<String, String> metadata) {
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        Document doc = Document.from(content, Metadata.from(metadata));
        ingestor.ingest(doc);
        log.info("添加自定义文档: {}", metadata);
    }

    /**
     * 获取索引统计
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("initialized", initialized);
        stats.put("indexedTableCount", indexedTableCount.get());
        return stats;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
