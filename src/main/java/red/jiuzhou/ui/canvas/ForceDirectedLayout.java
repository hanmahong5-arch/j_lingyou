package red.jiuzhou.ui.canvas;

import red.jiuzhou.analysis.aion.mechanism.MechanismNode;
import red.jiuzhou.analysis.aion.mechanism.MechanismRelationship;
import red.jiuzhou.analysis.aion.mechanism.MechanismRelationshipGraph;

import java.util.List;
import java.util.Random;

/**
 * 力导向布局算法
 *
 * <p>基于 Fruchterman-Reingold 算法的简化版本，用于对机制关系图进行布局。
 *
 * <p>力模型：
 * <ul>
 *   <li>斥力：所有节点之间互相排斥（库仑力）</li>
 *   <li>引力：有边连接的节点之间互相吸引（弹簧力）</li>
 *   <li>中心力：所有节点向画布中心聚拢</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class ForceDirectedLayout {

    // 布局参数
    private double repulsionStrength = 5000;    // 斥力强度
    private double attractionStrength = 0.01;   // 引力强度
    private double centerStrength = 0.05;       // 中心力强度
    private double damping = 0.85;              // 阻尼系数
    private double minDistance = 50;            // 最小节点距离
    private double idealEdgeLength = 150;       // 理想边长度

    // 画布尺寸
    private double width = 800;
    private double height = 600;

    // 迭代控制
    private int maxIterations = 300;
    private double convergenceThreshold = 0.5;  // 收敛阈值

    // 随机数生成器
    private final Random random = new Random(42);

    /**
     * 创建布局器
     *
     * @param width 画布宽度
     * @param height 画布高度
     */
    public ForceDirectedLayout(double width, double height) {
        this.width = width;
        this.height = height;
    }

    /**
     * 执行布局（一次性完成所有迭代）
     *
     * @param graph 关系图
     */
    public void layout(MechanismRelationshipGraph graph) {
        List<MechanismNode> nodes = graph.getActiveNodes();
        List<MechanismRelationship> edges = graph.getSignificantRelationships(1, 0.0);

        if (nodes.isEmpty()) {
            return;
        }

        // 初始化位置
        initializePositions(nodes);

        // 迭代计算
        for (int i = 0; i < maxIterations; i++) {
            double totalMovement = iterate(nodes, edges);

            // 检查收敛
            if (totalMovement < convergenceThreshold) {
                break;
            }
        }

        // 确保节点在画布内
        constrainToCanvas(nodes);
    }

    /**
     * 执行单次迭代
     *
     * @param graph 关系图
     * @return 总移动量
     */
    public double step(MechanismRelationshipGraph graph) {
        List<MechanismNode> nodes = graph.getActiveNodes();
        List<MechanismRelationship> edges = graph.getSignificantRelationships(1, 0.0);

        if (nodes.isEmpty()) {
            return 0;
        }

        double totalMovement = iterate(nodes, edges);
        constrainToCanvas(nodes);
        return totalMovement;
    }

    /**
     * 初始化节点位置（圆形分布）
     */
    public void initializePositions(List<MechanismNode> nodes) {
        double centerX = width / 2;
        double centerY = height / 2;
        double radius = Math.min(width, height) * 0.35;

        for (int i = 0; i < nodes.size(); i++) {
            MechanismNode node = nodes.get(i);
            if (!node.isPinned()) {
                double angle = 2 * Math.PI * i / nodes.size();
                // 添加一点随机性
                double r = radius * (0.8 + random.nextDouble() * 0.4);
                node.setX(centerX + r * Math.cos(angle));
                node.setY(centerY + r * Math.sin(angle));
                node.setVx(0);
                node.setVy(0);
            }
        }
    }

    /**
     * 执行单次迭代
     */
    private double iterate(List<MechanismNode> nodes, List<MechanismRelationship> edges) {
        // 重置力
        for (MechanismNode node : nodes) {
            if (!node.isPinned()) {
                node.setVx(0);
                node.setVy(0);
            }
        }

        // 计算斥力（所有节点对之间）
        applyRepulsionForces(nodes);

        // 计算引力（有边连接的节点之间）
        applyAttractionForces(nodes, edges);

        // 计算中心力
        applyCenterForce(nodes);

        // 应用速度，计算总移动量
        double totalMovement = 0;
        for (MechanismNode node : nodes) {
            if (!node.isPinned()) {
                // 限制最大速度
                double speed = Math.sqrt(node.getVx() * node.getVx() + node.getVy() * node.getVy());
                double maxSpeed = 20;
                if (speed > maxSpeed) {
                    node.setVx(node.getVx() * maxSpeed / speed);
                    node.setVy(node.getVy() * maxSpeed / speed);
                }

                // 更新位置
                double dx = node.getVx() * damping;
                double dy = node.getVy() * damping;
                node.setX(node.getX() + dx);
                node.setY(node.getY() + dy);

                totalMovement += Math.abs(dx) + Math.abs(dy);
            }
        }

        return totalMovement;
    }

    /**
     * 应用斥力（库仑力模型）
     */
    private void applyRepulsionForces(List<MechanismNode> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            MechanismNode node1 = nodes.get(i);
            for (int j = i + 1; j < nodes.size(); j++) {
                MechanismNode node2 = nodes.get(j);

                double dx = node2.getX() - node1.getX();
                double dy = node2.getY() - node1.getY();
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance < minDistance) {
                    distance = minDistance;
                }

                // 库仑力: F = k / d^2
                double force = repulsionStrength / (distance * distance);

                double fx = (dx / distance) * force;
                double fy = (dy / distance) * force;

                if (!node1.isPinned()) {
                    node1.setVx(node1.getVx() - fx);
                    node1.setVy(node1.getVy() - fy);
                }
                if (!node2.isPinned()) {
                    node2.setVx(node2.getVx() + fx);
                    node2.setVy(node2.getVy() + fy);
                }
            }
        }
    }

    /**
     * 应用引力（弹簧力模型）
     */
    private void applyAttractionForces(List<MechanismNode> nodes,
                                        List<MechanismRelationship> edges) {
        for (MechanismRelationship edge : edges) {
            MechanismNode source = findNode(nodes, edge.getSource());
            MechanismNode target = findNode(nodes, edge.getTarget());

            if (source == null || target == null) {
                continue;
            }

            double dx = target.getX() - source.getX();
            double dy = target.getY() - source.getY();
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance < 1) {
                distance = 1;
            }

            // 弹簧力: F = k * (d - L)，其中L是理想长度
            double displacement = distance - idealEdgeLength;
            double force = attractionStrength * displacement;

            // 根据关系强度调整引力
            double strengthMultiplier = 1 + edge.getStrength() * 0.1;
            force *= strengthMultiplier;

            double fx = (dx / distance) * force;
            double fy = (dy / distance) * force;

            if (!source.isPinned()) {
                source.setVx(source.getVx() + fx);
                source.setVy(source.getVy() + fy);
            }
            if (!target.isPinned()) {
                target.setVx(target.getVx() - fx);
                target.setVy(target.getVy() - fy);
            }
        }
    }

    /**
     * 应用中心力（防止节点飞散）
     */
    private void applyCenterForce(List<MechanismNode> nodes) {
        double centerX = width / 2;
        double centerY = height / 2;

        for (MechanismNode node : nodes) {
            if (!node.isPinned()) {
                double dx = centerX - node.getX();
                double dy = centerY - node.getY();

                node.setVx(node.getVx() + dx * centerStrength);
                node.setVy(node.getVy() + dy * centerStrength);
            }
        }
    }

    /**
     * 限制节点在画布内
     */
    private void constrainToCanvas(List<MechanismNode> nodes) {
        double padding = 50;  // 边缘留白

        for (MechanismNode node : nodes) {
            double x = node.getX();
            double y = node.getY();

            if (x < padding) {
                node.setX(padding);
                node.setVx(0);
            } else if (x > width - padding) {
                node.setX(width - padding);
                node.setVx(0);
            }

            if (y < padding) {
                node.setY(padding);
                node.setVy(0);
            } else if (y > height - padding) {
                node.setY(height - padding);
                node.setVy(0);
            }
        }
    }

    private MechanismNode findNode(List<MechanismNode> nodes,
                                    red.jiuzhou.analysis.aion.AionMechanismCategory category) {
        for (MechanismNode node : nodes) {
            if (node.getCategory() == category) {
                return node;
            }
        }
        return null;
    }

    // ========== Getters and Setters ==========

    public double getRepulsionStrength() {
        return repulsionStrength;
    }

    public void setRepulsionStrength(double repulsionStrength) {
        this.repulsionStrength = repulsionStrength;
    }

    public double getAttractionStrength() {
        return attractionStrength;
    }

    public void setAttractionStrength(double attractionStrength) {
        this.attractionStrength = attractionStrength;
    }

    public double getCenterStrength() {
        return centerStrength;
    }

    public void setCenterStrength(double centerStrength) {
        this.centerStrength = centerStrength;
    }

    public double getDamping() {
        return damping;
    }

    public void setDamping(double damping) {
        this.damping = damping;
    }

    public double getIdealEdgeLength() {
        return idealEdgeLength;
    }

    public void setIdealEdgeLength(double idealEdgeLength) {
        this.idealEdgeLength = idealEdgeLength;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    /**
     * 更新画布尺寸并重新布局
     */
    public void resize(double width, double height) {
        this.width = width;
        this.height = height;
    }
}
