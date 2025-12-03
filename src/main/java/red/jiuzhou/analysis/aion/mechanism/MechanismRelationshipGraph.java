package red.jiuzhou.analysis.aion.mechanism;

import red.jiuzhou.analysis.aion.AionMechanismCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 机制关系图
 *
 * <p>表示27个游戏机制之间的关系网络，支持依赖查询、影响分析等操作。
 *
 * @author Claude
 * @version 1.0
 */
public class MechanismRelationshipGraph {

    // 机制节点 (27个)
    private final Map<AionMechanismCategory, MechanismNode> nodes;

    // 机制关系边
    private final List<MechanismRelationship> relationships;

    // 正向索引：源机制 -> 目标机制列表
    private final Map<AionMechanismCategory, List<MechanismRelationship>> outgoingIndex;

    // 反向索引：目标机制 -> 依赖它的机制列表
    private final Map<AionMechanismCategory, List<MechanismRelationship>> incomingIndex;

    // 统计信息
    private int totalFileCount;
    private int totalRelationshipCount;
    private long buildTimestamp;

    public MechanismRelationshipGraph() {
        this.nodes = new EnumMap<>(AionMechanismCategory.class);
        this.relationships = new ArrayList<>();
        this.outgoingIndex = new EnumMap<>(AionMechanismCategory.class);
        this.incomingIndex = new EnumMap<>(AionMechanismCategory.class);
        this.totalFileCount = 0;
        this.totalRelationshipCount = 0;
        this.buildTimestamp = System.currentTimeMillis();

        // 初始化所有机制节点
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            nodes.put(category, new MechanismNode(category));
            outgoingIndex.put(category, new ArrayList<>());
            incomingIndex.put(category, new ArrayList<>());
        }
    }

    // ========== 构建方法 ==========

    /**
     * 获取或创建机制节点
     *
     * @param category 机制分类
     * @return 机制节点
     */
    public MechanismNode getNode(AionMechanismCategory category) {
        return nodes.get(category);
    }

    /**
     * 添加或更新关系
     *
     * @param source 源机制
     * @param target 目标机制
     * @param type 关系类型
     * @return 关系对象
     */
    public MechanismRelationship addRelationship(AionMechanismCategory source,
                                                  AionMechanismCategory target,
                                                  MechanismRelationshipType type) {
        if (source == null || target == null || source == target) {
            return null;
        }

        // 查找现有关系
        MechanismRelationship existing = findRelationship(source, target);
        if (existing != null) {
            existing.incrementCount();
            return existing;
        }

        // 创建新关系
        MechanismRelationship relationship = new MechanismRelationship(source, target, type);
        relationship.incrementCount();
        relationships.add(relationship);
        outgoingIndex.get(source).add(relationship);
        incomingIndex.get(target).add(relationship);
        totalRelationshipCount++;

        return relationship;
    }

    /**
     * 查找两个机制之间的关系
     *
     * @param source 源机制
     * @param target 目标机制
     * @return 关系对象，不存在返回null
     */
    public MechanismRelationship findRelationship(AionMechanismCategory source,
                                                   AionMechanismCategory target) {
        List<MechanismRelationship> outgoing = outgoingIndex.get(source);
        if (outgoing != null) {
            for (MechanismRelationship rel : outgoing) {
                if (rel.getTarget() == target) {
                    return rel;
                }
            }
        }
        return null;
    }

    /**
     * 构建完成后调用，计算统计信息
     */
    public void finalizeBuild() {
        this.buildTimestamp = System.currentTimeMillis();
        this.totalFileCount = nodes.values().stream()
                .mapToInt(MechanismNode::getFileCount)
                .sum();
        this.totalRelationshipCount = relationships.size();

        // 计算每个关系的置信度
        for (MechanismRelationship rel : relationships) {
            double confidence = calculateConfidence(rel);
            rel.setConfidence(confidence);
        }
    }

    private double calculateConfidence(MechanismRelationship rel) {
        // 基于关系数量和示例数量计算置信度
        int count = rel.getRelationshipCount();
        int examples = rel.getExamples().size();

        double countScore = Math.min(1.0, count / 50.0);  // 50个引用得满分
        double exampleScore = Math.min(1.0, examples / 5.0);  // 5个示例得满分

        return countScore * 0.7 + exampleScore * 0.3;
    }

    // ========== 查询方法 ==========

    /**
     * 获取所有机制节点
     *
     * @return 节点列表
     */
    public List<MechanismNode> getNodes() {
        return new ArrayList<>(nodes.values());
    }

    /**
     * 获取有效节点（至少有一个文件）
     *
     * @return 有效节点列表
     */
    public List<MechanismNode> getActiveNodes() {
        return nodes.values().stream()
                .filter(node -> node.getFileCount() > 0)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有关系
     *
     * @return 关系列表
     */
    public List<MechanismRelationship> getRelationships() {
        return Collections.unmodifiableList(relationships);
    }

    /**
     * 获取有意义的关系（过滤弱关系）
     *
     * @param minCount 最小关系数量
     * @param minConfidence 最小置信度
     * @return 过滤后的关系列表
     */
    public List<MechanismRelationship> getSignificantRelationships(int minCount, double minConfidence) {
        return relationships.stream()
                .filter(rel -> rel.isSignificant(minCount, minConfidence))
                .collect(Collectors.toList());
    }

    /**
     * 获取机制的出向关系（该机制依赖的其他机制）
     *
     * @param category 机制分类
     * @return 出向关系列表
     */
    public List<MechanismRelationship> getOutgoingRelationships(AionMechanismCategory category) {
        List<MechanismRelationship> result = outgoingIndex.get(category);
        return result != null ? Collections.unmodifiableList(result) : Collections.emptyList();
    }

    /**
     * 获取机制的入向关系（依赖该机制的其他机制）
     *
     * @param category 机制分类
     * @return 入向关系列表
     */
    public List<MechanismRelationship> getIncomingRelationships(AionMechanismCategory category) {
        List<MechanismRelationship> result = incomingIndex.get(category);
        return result != null ? Collections.unmodifiableList(result) : Collections.emptyList();
    }

    /**
     * 获取依赖链（DFS遍历）
     *
     * @param category 起始机制
     * @param maxDepth 最大深度
     * @return 依赖链列表
     */
    public List<DependencyChain> getDependencyChains(AionMechanismCategory category, int maxDepth) {
        List<DependencyChain> chains = new ArrayList<>();
        Set<AionMechanismCategory> visited = new HashSet<>();
        visited.add(category);

        traverseDependencies(category, new ArrayList<>(), chains, visited, maxDepth);
        return chains;
    }

    private void traverseDependencies(AionMechanismCategory current,
                                      List<AionMechanismCategory> path,
                                      List<DependencyChain> chains,
                                      Set<AionMechanismCategory> visited,
                                      int remainingDepth) {
        if (remainingDepth <= 0) {
            return;
        }

        List<MechanismRelationship> outgoing = outgoingIndex.get(current);
        if (outgoing == null || outgoing.isEmpty()) {
            return;
        }

        for (MechanismRelationship rel : outgoing) {
            AionMechanismCategory target = rel.getTarget();
            List<AionMechanismCategory> newPath = new ArrayList<>(path);
            newPath.add(current);
            newPath.add(target);

            chains.add(new DependencyChain(newPath, rel));

            if (!visited.contains(target)) {
                visited.add(target);
                traverseDependencies(target, newPath, chains, visited, remainingDepth - 1);
            }
        }
    }

    /**
     * 获取影响范围（BFS遍历反向索引）
     *
     * @param category 被删除/修改的机制
     * @param maxDepth 最大深度
     * @return 受影响的机制列表
     */
    public List<ImpactedMechanism> getImpactedMechanisms(AionMechanismCategory category, int maxDepth) {
        List<ImpactedMechanism> impacted = new ArrayList<>();
        Map<AionMechanismCategory, Integer> depthMap = new HashMap<>();
        Queue<AionMechanismCategory> queue = new LinkedList<>();

        queue.offer(category);
        depthMap.put(category, 0);

        while (!queue.isEmpty()) {
            AionMechanismCategory current = queue.poll();
            int currentDepth = depthMap.get(current);

            if (currentDepth >= maxDepth) {
                continue;
            }

            List<MechanismRelationship> incoming = incomingIndex.get(current);
            if (incoming != null) {
                for (MechanismRelationship rel : incoming) {
                    AionMechanismCategory source = rel.getSource();
                    if (!depthMap.containsKey(source)) {
                        depthMap.put(source, currentDepth + 1);
                        queue.offer(source);
                        impacted.add(new ImpactedMechanism(
                                source,
                                currentDepth + 1,
                                rel.getRelationshipCount(),
                                rel.getType()
                        ));
                    }
                }
            }
        }

        return impacted;
    }

    // ========== 统计信息 ==========

    public int getTotalFileCount() {
        return totalFileCount;
    }

    public int getTotalRelationshipCount() {
        return totalRelationshipCount;
    }

    public int getActiveNodeCount() {
        return (int) nodes.values().stream()
                .filter(node -> node.getFileCount() > 0)
                .count();
    }

    public long getBuildTimestamp() {
        return buildTimestamp;
    }

    /**
     * 获取统计摘要
     *
     * @return 统计信息Map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMechanisms", nodes.size());
        stats.put("activeMechanisms", getActiveNodeCount());
        stats.put("totalFiles", totalFileCount);
        stats.put("totalRelationships", totalRelationshipCount);
        stats.put("buildTimestamp", buildTimestamp);

        // 关系类型分布
        Map<MechanismRelationshipType, Long> typeDistribution = relationships.stream()
                .collect(Collectors.groupingBy(MechanismRelationship::getType, Collectors.counting()));
        stats.put("relationshipTypeDistribution", typeDistribution);

        // 入度最高的机制（最被依赖）
        AionMechanismCategory mostDepended = null;
        int maxIncoming = 0;
        for (Map.Entry<AionMechanismCategory, List<MechanismRelationship>> entry : incomingIndex.entrySet()) {
            if (entry.getValue().size() > maxIncoming) {
                maxIncoming = entry.getValue().size();
                mostDepended = entry.getKey();
            }
        }
        stats.put("mostDependedMechanism", mostDepended != null ? mostDepended.getDisplayName() : "N/A");
        stats.put("mostDependedCount", maxIncoming);

        return stats;
    }

    @Override
    public String toString() {
        return "MechanismRelationshipGraph{" +
                "activeNodes=" + getActiveNodeCount() +
                ", relationships=" + relationships.size() +
                ", totalFiles=" + totalFileCount +
                '}';
    }

    // ========== 内部类 ==========

    /**
     * 依赖链
     */
    public static class DependencyChain {
        private final List<AionMechanismCategory> path;
        private final MechanismRelationship relationship;

        public DependencyChain(List<AionMechanismCategory> path, MechanismRelationship relationship) {
            this.path = path;
            this.relationship = relationship;
        }

        public List<AionMechanismCategory> getPath() {
            return path;
        }

        public MechanismRelationship getRelationship() {
            return relationship;
        }

        public int getLength() {
            return path.size() - 1;
        }

        @Override
        public String toString() {
            return path.stream()
                    .map(AionMechanismCategory::getDisplayName)
                    .collect(Collectors.joining(" → "));
        }
    }

    /**
     * 受影响的机制
     */
    public static class ImpactedMechanism {
        private final AionMechanismCategory category;
        private final int depth;
        private final int relationshipCount;
        private final MechanismRelationshipType relationshipType;

        public ImpactedMechanism(AionMechanismCategory category, int depth,
                                  int relationshipCount, MechanismRelationshipType relationshipType) {
            this.category = category;
            this.depth = depth;
            this.relationshipCount = relationshipCount;
            this.relationshipType = relationshipType;
        }

        public AionMechanismCategory getCategory() {
            return category;
        }

        public int getDepth() {
            return depth;
        }

        public int getRelationshipCount() {
            return relationshipCount;
        }

        public MechanismRelationshipType getRelationshipType() {
            return relationshipType;
        }

        @Override
        public String toString() {
            return category.getDisplayName() + " (深度:" + depth +
                   ", 关系数:" + relationshipCount + ")";
        }
    }
}
