package red.jiuzhou.analysis.aion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import red.jiuzhou.analysis.aion.mechanism.MechanismNode;
import red.jiuzhou.analysis.aion.mechanism.MechanismRelationship;
import red.jiuzhou.analysis.aion.mechanism.MechanismRelationshipGraph;
import red.jiuzhou.analysis.aion.mechanism.MechanismRelationshipType;
import red.jiuzhou.relationship.XmlRelationshipAnalyzer;
import red.jiuzhou.relationship.XmlRelationshipAnalyzer.RelationshipReport;
import red.jiuzhou.relationship.XmlRelationshipAnalyzer.RelationshipSnapshot;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 机制关系服务
 *
 * <p>负责构建和查询游戏机制之间的关系图，核心功能：
 * <ul>
 *   <li>聚合字段级关系到机制级关系</li>
 *   <li>提供依赖链查询</li>
 *   <li>提供影响分析</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
@Service
public class MechanismRelationshipService {

    private static final Logger log = LoggerFactory.getLogger(MechanismRelationshipService.class);

    // 缓存构建好的关系图
    private volatile MechanismRelationshipGraph cachedGraph;
    private volatile long cacheTimestamp;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;  // 5分钟缓存

    // 文件名到机制的映射缓存
    private final Map<String, AionMechanismCategory> fileToMechanismCache = new ConcurrentHashMap<>();

    /**
     * 构建机制关系图
     *
     * @return 关系图
     */
    public MechanismRelationshipGraph buildRelationshipGraph() {
        return buildRelationshipGraph(null);
    }

    /**
     * 构建机制关系图（带进度回调）
     *
     * @param progressCallback 进度回调
     * @return 关系图
     */
    public MechanismRelationshipGraph buildRelationshipGraph(Consumer<String> progressCallback) {
        // 检查缓存
        if (cachedGraph != null && (System.currentTimeMillis() - cacheTimestamp) < CACHE_TTL_MS) {
            log.info("使用缓存的机制关系图");
            return cachedGraph;
        }

        log.info("开始构建机制关系图...");
        long startTime = System.currentTimeMillis();

        notifyProgress(progressCallback, "初始化机制检测器...");

        // 1. 获取XML路径配置
        String xmlPath = YamlUtils.getProperty("aion.xmlPath");
        String localizedPath = YamlUtils.getProperty("aion.localizedPath");

        if (xmlPath == null || xmlPath.isEmpty()) {
            log.warn("未配置 aion.xmlPath，无法构建机制关系图");
            return new MechanismRelationshipGraph();
        }

        File publicRoot = new File(xmlPath);
        File localizedRoot = localizedPath != null && !localizedPath.isEmpty()
                ? new File(localizedPath) : null;

        // 2. 扫描机制视图
        notifyProgress(progressCallback, "扫描游戏机制分类...");
        AionMechanismDetector detector = new AionMechanismDetector(publicRoot, localizedRoot);
        AionMechanismView mechanismView = detector.scan();

        // 3. 构建文件到机制的映射
        notifyProgress(progressCallback, "构建文件-机制映射...");
        buildFileToMechanismMapping(mechanismView);

        // 4. 获取字段级关系报告
        notifyProgress(progressCallback, "分析字段级关系...");
        RelationshipReport relationshipReport = getOrLoadRelationshipReport();

        // 5. 聚合到机制级关系
        notifyProgress(progressCallback, "聚合机制级关系...");
        MechanismRelationshipGraph graph = aggregateToMechanismLevel(mechanismView, relationshipReport);

        // 6. 完成构建
        graph.finalizeBuild();

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("机制关系图构建完成，耗时 {}ms，{}个活跃机制，{}个关系",
                elapsed, graph.getActiveNodeCount(), graph.getTotalRelationshipCount());

        // 缓存
        cachedGraph = graph;
        cacheTimestamp = System.currentTimeMillis();

        return graph;
    }

    /**
     * 获取缓存的关系图（如果存在）
     *
     * @return 关系图，如果没有缓存则返回null
     */
    public MechanismRelationshipGraph getCachedGraph() {
        return cachedGraph;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        cachedGraph = null;
        cacheTimestamp = 0;
        fileToMechanismCache.clear();
        log.info("机制关系图缓存已清除");
    }

    /**
     * 根据文件名获取所属机制
     *
     * @param fileName 文件名（不含路径）
     * @return 机制分类
     */
    public AionMechanismCategory getMechanismForFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return AionMechanismCategory.OTHER;
        }

        // 先查缓存
        AionMechanismCategory cached = fileToMechanismCache.get(fileName.toLowerCase());
        if (cached != null) {
            return cached;
        }

        // 使用枚举的正则匹配
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            if (category.matches(fileName)) {
                fileToMechanismCache.put(fileName.toLowerCase(), category);
                return category;
            }
        }

        return AionMechanismCategory.OTHER;
    }

    /**
     * 获取机制的依赖链
     *
     * @param category 机制分类
     * @param maxDepth 最大深度
     * @return 依赖链列表
     */
    public List<MechanismRelationshipGraph.DependencyChain> getDependencyChains(
            AionMechanismCategory category, int maxDepth) {
        MechanismRelationshipGraph graph = buildRelationshipGraph();
        return graph.getDependencyChains(category, maxDepth);
    }

    /**
     * 获取受影响的机制
     *
     * @param category 被修改/删除的机制
     * @param maxDepth 最大深度
     * @return 受影响的机制列表
     */
    public List<MechanismRelationshipGraph.ImpactedMechanism> getImpactedMechanisms(
            AionMechanismCategory category, int maxDepth) {
        MechanismRelationshipGraph graph = buildRelationshipGraph();
        return graph.getImpactedMechanisms(category, maxDepth);
    }

    // ========== 私有方法 ==========

    private void notifyProgress(Consumer<String> callback, String message) {
        if (callback != null) {
            callback.accept(message);
        }
        log.debug(message);
    }

    private void buildFileToMechanismMapping(AionMechanismView mechanismView) {
        fileToMechanismCache.clear();

        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            AionMechanismView.MechanismGroup group = mechanismView.getGroup(category);
            if (group == null) {
                continue;
            }

            // 公共文件
            for (AionMechanismView.FileEntry file : group.getPublicFiles()) {
                fileToMechanismCache.put(file.getFileName().toLowerCase(), category);
            }

            // 本地化文件
            for (AionMechanismView.FileEntry file : group.getLocalizedFiles()) {
                fileToMechanismCache.put(file.getFileName().toLowerCase(), category);
            }
        }

        log.debug("文件-机制映射构建完成，共 {} 个文件", fileToMechanismCache.size());
    }

    private RelationshipReport getOrLoadRelationshipReport() {
        try {
            return XmlRelationshipAnalyzer.analyzeCurrentDatabase();
        } catch (Exception e) {
            log.warn("加载关系报告失败: {}", e.getMessage());
            return null;
        }
    }

    private MechanismRelationshipGraph aggregateToMechanismLevel(
            AionMechanismView mechanismView,
            RelationshipReport relationshipReport) {

        MechanismRelationshipGraph graph = new MechanismRelationshipGraph();

        // 1. 填充节点信息
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            AionMechanismView.MechanismGroup group = mechanismView.getGroup(category);
            if (group == null) {
                continue;
            }

            MechanismNode node = graph.getNode(category);
            int fileCount = group.getPublicFiles().size() + group.getLocalizedFiles().size();
            node.setFileCount(fileCount);

            // 添加代表性文件
            int maxFiles = Math.min(5, group.getPublicFiles().size());
            for (int i = 0; i < maxFiles; i++) {
                node.addFile(group.getPublicFiles().get(i).getFileName());
            }
        }

        // 2. 聚合字段级关系到机制级
        if (relationshipReport != null) {
            List<RelationshipSnapshot> snapshots = relationshipReport.getRelationshipSnapshots();

            for (RelationshipSnapshot snapshot : snapshots) {
                // 提取文件名
                String sourceFile = extractFileName(snapshot.getSourceFile());
                String targetFile = extractFileName(snapshot.getTargetFile());

                // 映射到机制
                AionMechanismCategory sourceMechanism = getMechanismForFile(sourceFile);
                AionMechanismCategory targetMechanism = getMechanismForFile(targetFile);

                // 跳过自引用和OTHER
                if (sourceMechanism == targetMechanism) {
                    continue;
                }
                if (sourceMechanism == AionMechanismCategory.OTHER ||
                    targetMechanism == AionMechanismCategory.OTHER) {
                    continue;
                }

                // 推断关系类型
                MechanismRelationshipType type = MechanismRelationshipType.inferFromFieldName(
                        snapshot.getSourceColumn());

                // 添加或更新关系
                MechanismRelationship rel = graph.addRelationship(sourceMechanism, targetMechanism, type);
                if (rel != null) {
                    rel.addExample(
                            sourceFile, snapshot.getSourceColumn(),
                            targetFile, snapshot.getTargetColumn(),
                            snapshot.getConfidence()
                    );
                }
            }
        }

        // 3. 添加预定义的机制关系（基于游戏领域知识）
        addPredefinedRelationships(graph);

        return graph;
    }

    private String extractFileName(String fileKey) {
        if (fileKey == null || fileKey.isEmpty()) {
            return "";
        }

        // fileKey 格式通常是 "path/to/file.xml" 或 "file.xml"
        int lastSlash = Math.max(fileKey.lastIndexOf('/'), fileKey.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            return fileKey.substring(lastSlash + 1);
        }
        return fileKey;
    }

    /**
     * 添加预定义的机制关系（基于Aion游戏领域知识）
     */
    private void addPredefinedRelationships(MechanismRelationshipGraph graph) {
        // NPC系统的核心依赖
        addKnownRelationship(graph, AionMechanismCategory.NPC, AionMechanismCategory.SKILL,
                MechanismRelationshipType.REFERENCES, "NPC使用技能");
        addKnownRelationship(graph, AionMechanismCategory.NPC, AionMechanismCategory.DROP,
                MechanismRelationshipType.CONTAINS, "NPC掉落物品");
        addKnownRelationship(graph, AionMechanismCategory.NPC, AionMechanismCategory.NPC_AI,
                MechanismRelationshipType.REFERENCES, "NPC行为AI");

        // 物品系统的核心依赖
        addKnownRelationship(graph, AionMechanismCategory.DROP, AionMechanismCategory.ITEM,
                MechanismRelationshipType.REFERENCES, "掉落表引用物品");
        addKnownRelationship(graph, AionMechanismCategory.SHOP, AionMechanismCategory.ITEM,
                MechanismRelationshipType.REFERENCES, "商店出售物品");
        addKnownRelationship(graph, AionMechanismCategory.CRAFT, AionMechanismCategory.ITEM,
                MechanismRelationshipType.REFERENCES, "合成产出物品");

        // 任务系统的核心依赖
        addKnownRelationship(graph, AionMechanismCategory.QUEST, AionMechanismCategory.NPC,
                MechanismRelationshipType.REFERENCES, "任务关联NPC");
        addKnownRelationship(graph, AionMechanismCategory.QUEST, AionMechanismCategory.ITEM,
                MechanismRelationshipType.REFERENCES, "任务奖励物品");
        addKnownRelationship(graph, AionMechanismCategory.QUEST, AionMechanismCategory.INSTANCE,
                MechanismRelationshipType.REFERENCES, "任务关联副本");

        // 技能系统
        addKnownRelationship(graph, AionMechanismCategory.SKILL, AionMechanismCategory.ITEM,
                MechanismRelationshipType.REFERENCES, "技能消耗物品");
        addKnownRelationship(graph, AionMechanismCategory.STIGMA_TRANSFORM, AionMechanismCategory.SKILL,
                MechanismRelationshipType.CONTAINS, "烙印提供技能");

        // 副本系统
        addKnownRelationship(graph, AionMechanismCategory.INSTANCE, AionMechanismCategory.NPC,
                MechanismRelationshipType.CONTAINS, "副本包含NPC");
        addKnownRelationship(graph, AionMechanismCategory.INSTANCE, AionMechanismCategory.PORTAL,
                MechanismRelationshipType.REFERENCES, "副本传送门");

        // 其他系统
        addKnownRelationship(graph, AionMechanismCategory.PET, AionMechanismCategory.SKILL,
                MechanismRelationshipType.REFERENCES, "宠物技能");
        addKnownRelationship(graph, AionMechanismCategory.GOTCHA, AionMechanismCategory.ITEM,
                MechanismRelationshipType.REFERENCES, "抽卡产出物品");
        addKnownRelationship(graph, AionMechanismCategory.ENCHANT, AionMechanismCategory.ITEM,
                MechanismRelationshipType.REFERENCES, "强化目标物品");
        addKnownRelationship(graph, AionMechanismCategory.HOUSING, AionMechanismCategory.ITEM,
                MechanismRelationshipType.REFERENCES, "房屋家具物品");
        addKnownRelationship(graph, AionMechanismCategory.ABYSS, AionMechanismCategory.ITEM,
                MechanismRelationshipType.REFERENCES, "深渊奖励物品");
    }

    private void addKnownRelationship(MechanismRelationshipGraph graph,
                                       AionMechanismCategory source,
                                       AionMechanismCategory target,
                                       MechanismRelationshipType type,
                                       String description) {
        // 如果已经通过分析发现了该关系，不重复添加
        MechanismRelationship existing = graph.findRelationship(source, target);
        if (existing == null) {
            MechanismRelationship rel = graph.addRelationship(source, target, type);
            if (rel != null) {
                // 标记为预定义关系（通过设置一个基础计数）
                rel.setRelationshipCount(1);
                rel.setConfidence(0.5);  // 预定义关系置信度设为0.5
            }
        }
    }
}
