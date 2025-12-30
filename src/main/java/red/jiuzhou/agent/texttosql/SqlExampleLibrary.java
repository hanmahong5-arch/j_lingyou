package red.jiuzhou.agent.texttosql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL示例库 - Few-Shot Learning（枚举单例模式）
 *
 * <p>类似Vanna.AI的训练数据，存储自然语言→SQL的映射示例
 * <p>用于增强AI理解游戏领域查询的准确性
 * <p>使用枚举单例模式确保线程安全且防止反射攻击
 *
 * @author Claude
 * @date 2025-12-20
 */
public enum SqlExampleLibrary {
    /** 单例实例 */
    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(SqlExampleLibrary.class);

    /**
     * SQL示例条目
     */
    public static class SqlExample {
        private String naturalLanguage;  // 自然语言描述
        private String sql;               // 对应的SQL
        private String category;          // 分类（物品/技能/任务等）
        private List<String> keywords;    // 关键词
        private int useCount;             // 使用次数（用于排序）
        private double successRate;       // 成功率

        public SqlExample(String naturalLanguage, String sql, String category) {
            this.naturalLanguage = naturalLanguage;
            this.sql = sql;
            this.category = category;
            this.keywords = extractKeywords(naturalLanguage);
            this.useCount = 0;
            this.successRate = 1.0;
        }

        private List<String> extractKeywords(String text) {
            // 简单的关键词提取（可以使用更复杂的NLP方法）
            List<String> keywords = new ArrayList<>();
            String[] words = text.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\u4e00-\u9fa5\\s]", " ")
                .split("\\s+");

            for (String word : words) {
                if (word.length() > 1) {  // 过滤单字
                    keywords.add(word);
                }
            }
            return keywords;
        }

        // Getters and setters
        public String getNaturalLanguage() { return naturalLanguage; }
        public String getSql() { return sql; }
        public String getCategory() { return category; }
        public List<String> getKeywords() { return keywords; }
        public int getUseCount() { return useCount; }
        public void incrementUseCount() { this.useCount++; }
        public double getSuccessRate() { return successRate; }
        public void updateSuccessRate(boolean success) {
            // 使用移动平均更新成功率
            double alpha = 0.2;  // 学习率
            this.successRate = alpha * (success ? 1.0 : 0.0) + (1 - alpha) * this.successRate;
        }
    }

    // 示例库存储
    private final Map<String, List<SqlExample>> examplesByCategory = new ConcurrentHashMap<>();
    private final List<SqlExample> allExamples = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean initialized = false;

    /**
     * 获取单例实例（兼容性方法）
     */
    public static SqlExampleLibrary getInstance() {
        INSTANCE.ensureInitialized();
        return INSTANCE;
    }

    /**
     * 确保初始化（延迟初始化）
     */
    private synchronized void ensureInitialized() {
        if (!initialized) {
            initializeDefaultExamples();
            initialized = true;
        }
    }

    /**
     * 初始化默认示例
     */
    private void initializeDefaultExamples() {
        // ========== 物品相关 ==========
        addExample("查询所有紫色品质的物品",
            "SELECT * FROM item_armors WHERE quality = 'epic' LIMIT 20",
            "物品");

        addExample("查询所有50级以上的武器",
            "SELECT * FROM item_weapons WHERE level >= 50 LIMIT 20",
            "物品");

        addExample("查询稀有度大于4的装备",
            "SELECT * FROM item_armors WHERE quality >= 4 LIMIT 20",
            "物品");

        addExample("查询所有传说品质的物品",
            "SELECT * FROM item_armors WHERE quality = 'legendary' OR quality >= 5 LIMIT 20",
            "物品");

        addExample("查询防御力大于1000的护甲",
            "SELECT * FROM item_armors WHERE physical_defense > 1000 LIMIT 20",
            "物品");

        // ========== 技能相关 ==========
        addExample("查询所有火属性技能",
            "SELECT * FROM client_skill WHERE element = 'fire' OR element_type = 1 LIMIT 20",
            "技能");

        addExample("查询伤害大于500的技能",
            "SELECT * FROM client_skill WHERE base_damage > 500 LIMIT 20",
            "技能");

        addExample("查询冷却时间小于10秒的技能",
            "SELECT * FROM client_skill WHERE cooldown < 10 LIMIT 20",
            "技能");

        addExample("查询所有群体攻击技能",
            "SELECT * FROM client_skill WHERE target_type = 'aoe' OR is_area_effect = 1 LIMIT 20",
            "技能");

        // ========== NPC相关 ==========
        addExample("查询所有BOSS级别的NPC",
            "SELECT * FROM client_npc WHERE rank = 'boss' OR npc_grade >= 4 LIMIT 20",
            "NPC");

        addExample("查询等级大于60的怪物",
            "SELECT * FROM client_npc WHERE level > 60 LIMIT 20",
            "NPC");

        addExample("查询血量超过100万的NPC",
            "SELECT * FROM client_npc WHERE hp > 1000000 LIMIT 20",
            "NPC");

        // ========== 任务相关 ==========
        addExample("查询主线任务",
            "SELECT * FROM quest WHERE quest_type = 'main' OR category = 'mainquest' LIMIT 20",
            "任务");

        addExample("查询50级以上的任务",
            "SELECT * FROM quest WHERE min_level >= 50 LIMIT 20",
            "任务");

        addExample("查询经验奖励大于10000的任务",
            "SELECT * FROM quest WHERE exp_reward > 10000 LIMIT 20",
            "任务");

        // ========== 元数据查询 ==========
        addExample("查询所有物品表",
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE '%item%' LIMIT 50",
            "元数据");

        addExample("列出所有表",
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name LIMIT 100",
            "元数据");

        addExample("查询表的字段信息",
            "SELECT column_name, column_type, column_comment FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'item_armors' ORDER BY ordinal_position",
            "元数据");

        addExample("查询包含skill的表",
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE '%skill%' LIMIT 50",
            "元数据");

        log.info("SQL示例库初始化完成，共加载 {} 个示例", allExamples.size());
    }

    /**
     * 添加示例
     */
    public void addExample(String naturalLanguage, String sql, String category) {
        SqlExample example = new SqlExample(naturalLanguage, sql, category);
        allExamples.add(example);

        examplesByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(example);
    }

    /**
     * 查找最相似的示例（Few-Shot Prompting）
     *
     * @param query 用户查询
     * @param topK 返回前K个最相似的
     * @return 相似示例列表
     */
    public List<SqlExample> findSimilarExamples(String query, int topK) {
        List<String> queryKeywords = extractKeywords(query);

        // 计算每个示例与查询的相似度
        List<ScoredExample> scored = new ArrayList<>();
        for (SqlExample example : allExamples) {
            double score = calculateSimilarity(queryKeywords, example.getKeywords());
            // 考虑成功率权重
            score *= (0.7 + 0.3 * example.getSuccessRate());
            scored.add(new ScoredExample(example, score));
        }

        // 按相似度排序（Record使用方法访问器）
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));

        // 返回topK个结果
        List<SqlExample> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            results.add(scored.get(i).example());
        }

        return results;
    }

    /**
     * 计算关键词相似度（简单的Jaccard相似度）
     */
    private double calculateSimilarity(List<String> keywords1, List<String> keywords2) {
        Set<String> set1 = new HashSet<>(keywords1);
        Set<String> set2 = new HashSet<>(keywords2);

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }

    /**
     * 提取关键词
     */
    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        String[] words = text.toLowerCase()
            .replaceAll("[^a-zA-Z0-9\u4e00-\u9fa5\\s]", " ")
            .split("\\s+");

        for (String word : words) {
            if (word.length() > 1) {
                keywords.add(word);
            }
        }
        return keywords;
    }

    /** Few-Shot提示词模板（文本块） */
    private static final String FEW_SHOT_HEADER = """
        ## SQL查询示例

        以下是一些类似查询的正确示例，请参考这些示例来生成SQL：

        """;

    /** 单个示例模板 */
    private static final String EXAMPLE_TEMPLATE = """
        **示例 %d**:
        - 用户意图: %s
        - SQL查询: `%s`

        """;

    /**
     * 生成Few-Shot提示词
     */
    public String generateFewShotPrompt(String userQuery, int exampleCount) {
        List<SqlExample> examples = findSimilarExamples(userQuery, exampleCount);

        StringBuilder sb = new StringBuilder(FEW_SHOT_HEADER);
        for (int i = 0; i < examples.size(); i++) {
            SqlExample ex = examples.get(i);
            sb.append(EXAMPLE_TEMPLATE.formatted(i + 1, ex.getNaturalLanguage(), ex.getSql()));
        }

        return sb.toString();
    }

    /**
     * 记录查询反馈（用于优化）
     */
    public void recordFeedback(String query, String sql, boolean success) {
        // 找到最相似的示例并更新其成功率
        List<SqlExample> similar = findSimilarExamples(query, 1);
        if (!similar.isEmpty()) {
            similar.get(0).updateSuccessRate(success);
            similar.get(0).incrementUseCount();
        }
    }

    /**
     * 获取分类下的所有示例
     */
    public List<SqlExample> getExamplesByCategory(String category) {
        return examplesByCategory.getOrDefault(category, Collections.emptyList());
    }

    /**
     * 获取所有示例
     */
    public List<SqlExample> getAllExamples() {
        return new ArrayList<>(allExamples);
    }

    /**
     * 带分数的示例（用于排序）- Record模式
     */
    private record ScoredExample(SqlExample example, double score) {}
}
