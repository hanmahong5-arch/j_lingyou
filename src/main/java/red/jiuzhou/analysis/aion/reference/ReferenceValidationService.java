package red.jiuzhou.analysis.aion.reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 引用验证服务
 *
 * <p>验证ID引用是否在目标表中存在。
 *
 * <p>优化策略：
 * <ul>
 *   <li>批量加载目标表ID - 避免逐条查询</li>
 *   <li>缓存有效ID集合 - 复用验证结果</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class ReferenceValidationService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceValidationService.class);

    // 系统名 -> 目标表配置 [表名, ID列, NAME列]
    private static final Map<String, String[]> SYSTEM_TABLE_CONFIG = new LinkedHashMap<>();

    static {
        SYSTEM_TABLE_CONFIG.put("物品系统", new String[]{"client_items", "id", "name"});
        SYSTEM_TABLE_CONFIG.put("NPC系统", new String[]{"client_npcs_npc", "id", "name"});
        SYSTEM_TABLE_CONFIG.put("技能系统", new String[]{"client_skills", "id", "name"});
        SYSTEM_TABLE_CONFIG.put("任务系统", new String[]{"client_quests", "id", "name"});
        SYSTEM_TABLE_CONFIG.put("副本系统", new String[]{"instance_cooltime", "id", "name"});
        SYSTEM_TABLE_CONFIG.put("地图系统", new String[]{"world", "id", "name"});
        SYSTEM_TABLE_CONFIG.put("称号系统", new String[]{"client_titles", "id", "name"});
        SYSTEM_TABLE_CONFIG.put("宠物系统", new String[]{"client_toypets", "id", "name"});
        SYSTEM_TABLE_CONFIG.put("坐骑系统", new String[]{"client_rides", "id", "name"});
        SYSTEM_TABLE_CONFIG.put("掉落系统", new String[]{"drop_templates", "id", "name"});
        SYSTEM_TABLE_CONFIG.put("商店系统", new String[]{"shop_templates", "id", "name"});
        SYSTEM_TABLE_CONFIG.put("配方系统", new String[]{"recipe_templates", "id", "name"});
    }

    // 缓存：系统名 -> 有效ID集合
    private final Map<String, Set<String>> validIdCache = new ConcurrentHashMap<>();

    // 缓存：系统名 -> ID到NAME映射
    private final Map<String, Map<String, String>> idNameCache = new ConcurrentHashMap<>();

    // 缓存时间戳
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    // 缓存有效期（10分钟）
    private static final long CACHE_TTL_MS = 10 * 60 * 1000;

    /**
     * 验证分析结果中所有引用的有效性
     *
     * @param result 分析结果
     */
    public void validateReferences(FieldReferenceResult result) {
        log.info("开始验证引用有效性...");
        long startTime = System.currentTimeMillis();

        // 按目标系统分组
        Map<String, List<FieldReferenceEntry>> bySystem = result.groupByTargetSystem();

        for (Map.Entry<String, List<FieldReferenceEntry>> entry : bySystem.entrySet()) {
            String system = entry.getKey();
            List<FieldReferenceEntry> entries = entry.getValue();

            // 确保缓存已加载
            ensureCacheLoaded(system);

            // 验证每个条目
            Set<String> validIds = validIdCache.get(system);
            if (validIds == null) {
                // 目标表不存在，标记所有引用为无效
                for (FieldReferenceEntry refEntry : entries) {
                    refEntry.setInvalidReferences(refEntry.getDistinctValues());
                    refEntry.setValidReferences(0);
                }
                continue;
            }

            for (FieldReferenceEntry refEntry : entries) {
                int valid = 0;
                int invalid = 0;

                for (String id : refEntry.getSampleValues()) {
                    if (validIds.contains(id)) {
                        valid++;
                    } else {
                        invalid++;
                    }
                }

                // 根据样本比例估算总体有效性
                int sampleSize = refEntry.getSampleValues().size();
                if (sampleSize > 0) {
                    double validRate = (double) valid / sampleSize;
                    refEntry.setValidReferences((int) (refEntry.getDistinctValues() * validRate));
                    refEntry.setInvalidReferences(refEntry.getDistinctValues() - refEntry.getValidReferences());
                }

                // 更新样本名称
                Map<String, String> nameMap = idNameCache.get(system);
                if (nameMap != null) {
                    List<String> names = new ArrayList<>();
                    for (String id : refEntry.getSampleValues()) {
                        names.add(nameMap.getOrDefault(id, "???"));
                    }
                    refEntry.setSampleNames(names);
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("引用验证完成，耗时 {}ms", elapsed);
    }

    /**
     * 验证单个ID是否有效
     *
     * @param system 目标系统
     * @param id ID值
     * @return 是否有效
     */
    public boolean isValidId(String system, String id) {
        ensureCacheLoaded(system);
        Set<String> validIds = validIdCache.get(system);
        return validIds != null && validIds.contains(id);
    }

    /**
     * 解析ID对应的名称
     *
     * @param system 目标系统
     * @param id ID值
     * @return 名称，如果未找到返回原ID
     */
    public String resolveName(String system, String id) {
        ensureCacheLoaded(system);
        Map<String, String> nameMap = idNameCache.get(system);
        return nameMap != null ? nameMap.getOrDefault(id, id) : id;
    }

    /**
     * 批量验证ID
     *
     * @param system 目标系统
     * @param ids ID列表
     * @return 验证结果：ID -> 是否有效
     */
    public Map<String, Boolean> batchValidate(String system, Collection<String> ids) {
        ensureCacheLoaded(system);
        Set<String> validIds = validIdCache.get(system);

        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String id : ids) {
            result.put(id, validIds != null && validIds.contains(id));
        }
        return result;
    }

    /**
     * 获取目标系统的所有有效ID
     *
     * @param system 目标系统
     * @return 有效ID集合
     */
    public Set<String> getValidIds(String system) {
        ensureCacheLoaded(system);
        Set<String> ids = validIdCache.get(system);
        return ids != null ? Collections.unmodifiableSet(ids) : Collections.emptySet();
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        validIdCache.clear();
        idNameCache.clear();
        cacheTimestamps.clear();
        log.info("已清除验证缓存");
    }

    /**
     * 清除特定系统的缓存
     */
    public void clearCache(String system) {
        validIdCache.remove(system);
        idNameCache.remove(system);
        cacheTimestamps.remove(system);
    }

    // ========== 私有方法 ==========

    private void ensureCacheLoaded(String system) {
        Long timestamp = cacheTimestamps.get(system);
        long now = System.currentTimeMillis();

        if (timestamp != null && (now - timestamp) < CACHE_TTL_MS) {
            return;  // 缓存仍有效
        }

        loadSystemCache(system);
    }

    private void loadSystemCache(String system) {
        String[] config = SYSTEM_TABLE_CONFIG.get(system);
        if (config == null) {
            log.debug("未知系统: {}", system);
            return;
        }

        String tableName = config[0];
        String idColumn = config[1];
        String nameColumn = config[2];

        try {
            JdbcTemplate jdbc = DatabaseUtil.getJdbcTemplate();

            // 检查表是否存在
            if (!DatabaseUtil.tableExists(tableName)) {
                log.debug("表 {} 不存在，跳过加载系统 {}", tableName, system);
                validIdCache.put(system, Collections.emptySet());
                idNameCache.put(system, Collections.emptyMap());
                cacheTimestamps.put(system, System.currentTimeMillis());
                return;
            }

            // 查询所有ID和NAME
            String sql = String.format(
                    "SELECT `%s`, `%s` FROM `%s` WHERE `%s` IS NOT NULL",
                    idColumn, nameColumn, tableName, idColumn
            );

            List<Map<String, Object>> rows = jdbc.queryForList(sql);

            Set<String> ids = new HashSet<>();
            Map<String, String> names = new HashMap<>();

            for (Map<String, Object> row : rows) {
                Object idObj = row.get(idColumn);
                Object nameObj = row.get(nameColumn);

                if (idObj != null) {
                    String id = idObj.toString();
                    ids.add(id);
                    if (nameObj != null) {
                        names.put(id, nameObj.toString());
                    }
                }
            }

            validIdCache.put(system, ids);
            idNameCache.put(system, names);
            cacheTimestamps.put(system, System.currentTimeMillis());

            log.debug("已加载系统 {} 的验证缓存: {}个ID", system, ids.size());

        } catch (Exception e) {
            log.warn("加载系统 {} 的缓存失败: {}", system, e.getMessage());
            validIdCache.put(system, Collections.emptySet());
            idNameCache.put(system, Collections.emptyMap());
            cacheTimestamps.put(system, System.currentTimeMillis());
        }
    }

    /**
     * 获取缓存统计
     */
    public Map<String, Integer> getCacheStats() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : validIdCache.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }

    /**
     * 获取支持的系统列表
     */
    public Set<String> getSupportedSystems() {
        return Collections.unmodifiableSet(SYSTEM_TABLE_CONFIG.keySet());
    }
}
