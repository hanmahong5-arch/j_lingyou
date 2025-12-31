package red.jiuzhou.ui.components;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * 依赖关系图组件
 *
 * 使用力导向布局可视化展示文件依赖关系：
 * - 节点表示配置文件
 * - 边表示依赖关系（箭头方向表示依赖方向）
 * - 支持拖拽、缩放、平移
 * - 支持节点高亮和选择
 *
 * @author Claude
 * @version 1.0
 */
public class DependencyGraphView extends BorderPane {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphView.class);

    // ==================== 节点和边 ====================

    public record GraphNode(
        String id,
        String label,
        NodeType type,
        int priority,       // 服务器加载优先级 (1-3, 0表示非服务器文件)
        double x,
        double y,
        double vx,          // 速度x
        double vy           // 速度y
    ) {
        public GraphNode withPosition(double newX, double newY) {
            return new GraphNode(id, label, type, priority, newX, newY, vx, vy);
        }

        public GraphNode withVelocity(double newVx, double newVy) {
            return new GraphNode(id, label, type, priority, x, y, newVx, newVy);
        }
    }

    public record GraphEdge(
        String sourceId,
        String targetId,
        EdgeType type
    ) {}

    public enum NodeType {
        CORE("#e74c3c", "核心"),       // 红色 - 核心配置
        IMPORTANT("#f39c12", "重要"),  // 橙色 - 重要配置
        NORMAL("#3498db", "普通"),     // 蓝色 - 普通配置
        CLIENT("#2ecc71", "客户端");   // 绿色 - 客户端配置

        private final String color;
        private final String label;

        NodeType(String color, String label) {
            this.color = color;
            this.label = label;
        }

        public String getColor() { return color; }
        public String getLabel() { return label; }

        public static NodeType fromPriority(int priority) {
            return switch (priority) {
                case 1 -> CORE;
                case 2 -> IMPORTANT;
                case 3 -> NORMAL;
                default -> CLIENT;
            };
        }
    }

    public enum EdgeType {
        DEPENDS("#95a5a6", "依赖"),         // 灰色 - A依赖B
        REFERENCED("#9b59b6", "被引用");    // 紫色 - A被B引用

        private final String color;
        private final String label;

        EdgeType(String color, String label) {
            this.color = color;
            this.label = label;
        }

        public String getColor() { return color; }
        public String getLabel() { return label; }
    }

    // ==================== 图数据 ====================

    private final Map<String, GraphNode> nodes = new HashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();

    // ==================== 画布 ====================

    private Canvas canvas;
    private GraphicsContext gc;

    // ==================== 视图状态 ====================

    private double offsetX = 0;
    private double offsetY = 0;
    private double scale = 1.0;

    private String selectedNodeId = null;
    private String hoveredNodeId = null;
    private String draggedNodeId = null;
    private double dragStartX, dragStartY;

    // ==================== 力导向布局参数 ====================

    private static final double REPULSION_STRENGTH = 5000;  // 斥力强度
    private static final double ATTRACTION_STRENGTH = 0.01; // 引力强度
    private static final double DAMPING = 0.9;              // 阻尼系数
    private static final double MIN_DISTANCE = 50;          // 最小距离
    private static final double NODE_RADIUS = 25;           // 节点半径

    private boolean layoutRunning = false;
    private AnimationTimer layoutTimer;

    // ==================== 回调 ====================

    private Consumer<String> onNodeSelected;
    private Consumer<String> onNodeDoubleClick;

    // ==================== 构造函数 ====================

    public DependencyGraphView() {
        initializeUI();
        initializeInteraction();
    }

    private void initializeUI() {
        // 工具栏
        ToolBar toolbar = createToolbar();
        setTop(toolbar);

        // 画布容器
        StackPane canvasContainer = new StackPane();
        canvasContainer.setStyle("-fx-background-color: #f8f9fa;");

        canvas = new Canvas(600, 400);
        gc = canvas.getGraphicsContext2D();

        // 响应式调整画布大小
        canvasContainer.widthProperty().addListener((obs, old, newVal) -> {
            canvas.setWidth(newVal.doubleValue());
            draw();
        });
        canvasContainer.heightProperty().addListener((obs, old, newVal) -> {
            canvas.setHeight(newVal.doubleValue());
            draw();
        });

        canvasContainer.getChildren().add(canvas);
        setCenter(canvasContainer);

        // 图例
        HBox legend = createLegend();
        setBottom(legend);
    }

    /**
     * 创建工具栏
     */
    private ToolBar createToolbar() {
        Button zoomInBtn = new Button("🔍+");
        zoomInBtn.setTooltip(new Tooltip("放大"));
        zoomInBtn.setOnAction(e -> { scale *= 1.2; draw(); });

        Button zoomOutBtn = new Button("🔍-");
        zoomOutBtn.setTooltip(new Tooltip("缩小"));
        zoomOutBtn.setOnAction(e -> { scale /= 1.2; draw(); });

        Button resetBtn = new Button("↺");
        resetBtn.setTooltip(new Tooltip("重置视图"));
        resetBtn.setOnAction(e -> {
            scale = 1.0;
            offsetX = 0;
            offsetY = 0;
            draw();
        });

        Button layoutBtn = new Button("▶ 布局");
        layoutBtn.setTooltip(new Tooltip("启动/停止力导向布局"));
        layoutBtn.setOnAction(e -> {
            if (layoutRunning) {
                stopLayout();
                layoutBtn.setText("▶ 布局");
            } else {
                startLayout();
                layoutBtn.setText("⏸ 停止");
            }
        });

        Button centerBtn = new Button("◎");
        centerBtn.setTooltip(new Tooltip("居中显示"));
        centerBtn.setOnAction(e -> centerView());

        Label titleLabel = new Label("📊 依赖关系图");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new ToolBar(titleLabel, spacer, zoomInBtn, zoomOutBtn, resetBtn, layoutBtn, centerBtn);
    }

    /**
     * 创建图例
     */
    private HBox createLegend() {
        HBox legend = new HBox(16);
        legend.setPadding(new Insets(8));
        legend.setAlignment(javafx.geometry.Pos.CENTER);
        legend.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");

        for (NodeType type : NodeType.values()) {
            HBox item = new HBox(4);
            item.setAlignment(javafx.geometry.Pos.CENTER);

            Region dot = new Region();
            dot.setMinSize(12, 12);
            dot.setMaxSize(12, 12);
            dot.setStyle("-fx-background-color: " + type.getColor() + "; -fx-background-radius: 6;");

            Label label = new Label(type.getLabel());
            label.setStyle("-fx-font-size: 11px; -fx-text-fill: #555555;");

            item.getChildren().addAll(dot, label);
            legend.getChildren().add(item);
        }

        return legend;
    }

    /**
     * 初始化交互
     */
    private void initializeInteraction() {
        // 鼠标按下
        canvas.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                String nodeId = findNodeAt(e.getX(), e.getY());
                if (nodeId != null) {
                    draggedNodeId = nodeId;
                    dragStartX = e.getX();
                    dragStartY = e.getY();
                } else {
                    // 开始拖拽画布
                    dragStartX = e.getX() - offsetX;
                    dragStartY = e.getY() - offsetY;
                }
            }
        });

        // 鼠标拖拽
        canvas.setOnMouseDragged(e -> {
            if (draggedNodeId != null) {
                // 拖拽节点
                GraphNode node = nodes.get(draggedNodeId);
                if (node != null) {
                    double newX = (e.getX() - offsetX) / scale;
                    double newY = (e.getY() - offsetY) / scale;
                    nodes.put(draggedNodeId, node.withPosition(newX, newY).withVelocity(0, 0));
                    draw();
                }
            } else {
                // 拖拽画布
                offsetX = e.getX() - dragStartX;
                offsetY = e.getY() - dragStartY;
                draw();
            }
        });

        // 鼠标释放
        canvas.setOnMouseReleased(e -> {
            draggedNodeId = null;
        });

        // 鼠标移动（悬停）
        canvas.setOnMouseMoved(e -> {
            String nodeId = findNodeAt(e.getX(), e.getY());
            if (!Objects.equals(nodeId, hoveredNodeId)) {
                hoveredNodeId = nodeId;
                draw();
            }
        });

        // 鼠标点击
        canvas.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                String nodeId = findNodeAt(e.getX(), e.getY());

                if (e.getClickCount() == 2 && nodeId != null) {
                    // 双击
                    if (onNodeDoubleClick != null) {
                        onNodeDoubleClick.accept(nodeId);
                    }
                } else if (e.getClickCount() == 1) {
                    // 单击选择
                    selectedNodeId = nodeId;
                    if (onNodeSelected != null && nodeId != null) {
                        onNodeSelected.accept(nodeId);
                    }
                    draw();
                }
            }
        });

        // 滚轮缩放
        canvas.setOnScroll((ScrollEvent e) -> {
            double delta = e.getDeltaY() > 0 ? 1.1 : 0.9;
            double oldScale = scale;
            scale *= delta;
            scale = Math.max(0.1, Math.min(5.0, scale));

            // 以鼠标位置为中心缩放
            double mouseX = e.getX();
            double mouseY = e.getY();
            offsetX = mouseX - (mouseX - offsetX) * (scale / oldScale);
            offsetY = mouseY - (mouseY - offsetY) * (scale / oldScale);

            draw();
        });
    }

    // ==================== 绘制 ====================

    /**
     * 绘制图形
     */
    public void draw() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();

        gc.clearRect(0, 0, width, height);

        // 背景
        gc.setFill(Color.web("#f8f9fa"));
        gc.fillRect(0, 0, width, height);

        // 绘制网格（可选）
        drawGrid(width, height);

        // 应用变换
        gc.save();
        gc.translate(offsetX, offsetY);
        gc.scale(scale, scale);

        // 绘制边
        for (GraphEdge edge : edges) {
            drawEdge(edge);
        }

        // 绘制节点
        for (GraphNode node : nodes.values()) {
            drawNode(node);
        }

        gc.restore();
    }

    /**
     * 绘制网格
     */
    private void drawGrid(double width, double height) {
        gc.setStroke(Color.web("#e8e8e8"));
        gc.setLineWidth(1);

        double gridSize = 50 * scale;
        double startX = offsetX % gridSize;
        double startY = offsetY % gridSize;

        for (double x = startX; x < width; x += gridSize) {
            gc.strokeLine(x, 0, x, height);
        }
        for (double y = startY; y < height; y += gridSize) {
            gc.strokeLine(0, y, width, y);
        }
    }

    /**
     * 绘制节点
     */
    private void drawNode(GraphNode node) {
        double x = node.x();
        double y = node.y();
        double r = NODE_RADIUS;

        // 节点颜色
        Color baseColor = Color.web(node.type().getColor());

        // 选中状态
        if (node.id().equals(selectedNodeId)) {
            gc.setStroke(Color.web("#2c3e50"));
            gc.setLineWidth(3);
            gc.strokeOval(x - r - 3, y - r - 3, (r + 3) * 2, (r + 3) * 2);
        }

        // 悬停状态
        if (node.id().equals(hoveredNodeId)) {
            gc.setFill(baseColor.brighter());
            gc.setEffect(new javafx.scene.effect.DropShadow(10, Color.gray(0.5)));
        } else {
            gc.setFill(baseColor);
            gc.setEffect(null);
        }

        // 绘制圆形节点
        gc.fillOval(x - r, y - r, r * 2, r * 2);

        // 边框
        gc.setStroke(baseColor.darker());
        gc.setLineWidth(2);
        gc.strokeOval(x - r, y - r, r * 2, r * 2);

        // 标签
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 10));
        gc.setTextAlign(TextAlignment.CENTER);

        String label = node.label();
        if (label.length() > 8) {
            label = label.substring(0, 6) + "..";
        }
        gc.fillText(label, x, y + 4);

        gc.setEffect(null);
    }

    /**
     * 绘制边
     */
    private void drawEdge(GraphEdge edge) {
        GraphNode source = nodes.get(edge.sourceId());
        GraphNode target = nodes.get(edge.targetId());

        if (source == null || target == null) return;

        double x1 = source.x();
        double y1 = source.y();
        double x2 = target.x();
        double y2 = target.y();

        // 计算方向
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 0.01) return;

        // 调整起点和终点到节点边缘
        double ux = dx / length;
        double uy = dy / length;

        x1 += ux * NODE_RADIUS;
        y1 += uy * NODE_RADIUS;
        x2 -= ux * NODE_RADIUS;
        y2 -= uy * NODE_RADIUS;

        // 绘制线
        gc.setStroke(Color.web(edge.type().getColor()));
        gc.setLineWidth(1.5);
        gc.strokeLine(x1, y1, x2, y2);

        // 绘制箭头
        double arrowSize = 8;
        double angle = Math.atan2(dy, dx);

        double ax1 = x2 - arrowSize * Math.cos(angle - Math.PI / 6);
        double ay1 = y2 - arrowSize * Math.sin(angle - Math.PI / 6);
        double ax2 = x2 - arrowSize * Math.cos(angle + Math.PI / 6);
        double ay2 = y2 - arrowSize * Math.sin(angle + Math.PI / 6);

        gc.setFill(Color.web(edge.type().getColor()));
        gc.fillPolygon(
            new double[]{x2, ax1, ax2},
            new double[]{y2, ay1, ay2},
            3
        );
    }

    // ==================== 力导向布局 ====================

    /**
     * 启动力导向布局
     */
    public void startLayout() {
        if (layoutRunning) return;

        layoutRunning = true;
        layoutTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                stepLayout();
                draw();
            }
        };
        layoutTimer.start();
    }

    /**
     * 停止布局
     */
    public void stopLayout() {
        layoutRunning = false;
        if (layoutTimer != null) {
            layoutTimer.stop();
        }
    }

    /**
     * 执行一步布局计算
     */
    private void stepLayout() {
        // 计算斥力
        for (GraphNode node1 : nodes.values()) {
            double fx = 0, fy = 0;

            for (GraphNode node2 : nodes.values()) {
                if (node1.id().equals(node2.id())) continue;

                double dx = node1.x() - node2.x();
                double dy = node1.y() - node2.y();
                double distance = Math.max(Math.sqrt(dx * dx + dy * dy), MIN_DISTANCE);

                double force = REPULSION_STRENGTH / (distance * distance);
                fx += force * dx / distance;
                fy += force * dy / distance;
            }

            // 更新速度
            double newVx = (node1.vx() + fx) * DAMPING;
            double newVy = (node1.vy() + fy) * DAMPING;
            nodes.put(node1.id(), node1.withVelocity(newVx, newVy));
        }

        // 计算引力（边的吸引）
        for (GraphEdge edge : edges) {
            GraphNode source = nodes.get(edge.sourceId());
            GraphNode target = nodes.get(edge.targetId());

            if (source == null || target == null) continue;

            double dx = target.x() - source.x();
            double dy = target.y() - source.y();
            double distance = Math.sqrt(dx * dx + dy * dy);

            double force = distance * ATTRACTION_STRENGTH;

            double newSourceVx = source.vx() + force * dx / distance;
            double newSourceVy = source.vy() + force * dy / distance;
            double newTargetVx = target.vx() - force * dx / distance;
            double newTargetVy = target.vy() - force * dy / distance;

            nodes.put(source.id(), source.withVelocity(newSourceVx, newSourceVy));
            nodes.put(target.id(), target.withVelocity(newTargetVx, newTargetVy));
        }

        // 更新位置
        for (GraphNode node : nodes.values()) {
            if (node.id().equals(draggedNodeId)) continue; // 跳过正在拖拽的节点

            double newX = node.x() + node.vx();
            double newY = node.y() + node.vy();
            nodes.put(node.id(), node.withPosition(newX, newY));
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 查找指定位置的节点
     */
    private String findNodeAt(double screenX, double screenY) {
        // 转换为世界坐标
        double worldX = (screenX - offsetX) / scale;
        double worldY = (screenY - offsetY) / scale;

        for (GraphNode node : nodes.values()) {
            double dx = worldX - node.x();
            double dy = worldY - node.y();
            if (dx * dx + dy * dy <= NODE_RADIUS * NODE_RADIUS) {
                return node.id();
            }
        }
        return null;
    }

    /**
     * 居中视图
     */
    public void centerView() {
        if (nodes.isEmpty()) return;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (GraphNode node : nodes.values()) {
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.x());
            maxY = Math.max(maxY, node.y());
        }

        double centerX = (minX + maxX) / 2;
        double centerY = (minY + maxY) / 2;

        offsetX = canvas.getWidth() / 2 - centerX * scale;
        offsetY = canvas.getHeight() / 2 - centerY * scale;

        draw();
    }

    // ==================== 公共API ====================

    /**
     * 设置图数据
     */
    public void setGraphData(Collection<GraphNode> nodeList, Collection<GraphEdge> edgeList) {
        nodes.clear();
        edges.clear();

        for (GraphNode node : nodeList) {
            nodes.put(node.id(), node);
        }
        edges.addAll(edgeList);

        // 初始随机布局
        randomLayout();

        draw();
    }

    /**
     * 添加节点
     */
    public void addNode(GraphNode node) {
        nodes.put(node.id(), node);
        draw();
    }

    /**
     * 添加边
     */
    public void addEdge(GraphEdge edge) {
        edges.add(edge);
        draw();
    }

    /**
     * 清空图
     */
    public void clear() {
        nodes.clear();
        edges.clear();
        selectedNodeId = null;
        hoveredNodeId = null;
        draw();
    }

    /**
     * 随机布局
     */
    public void randomLayout() {
        Random random = new Random();
        double width = canvas.getWidth() / scale;
        double height = canvas.getHeight() / scale;

        for (GraphNode node : nodes.values()) {
            double x = random.nextDouble() * width * 0.8 + width * 0.1;
            double y = random.nextDouble() * height * 0.8 + height * 0.1;
            nodes.put(node.id(), node.withPosition(x, y));
        }
    }

    /**
     * 高亮节点
     */
    public void highlightNode(String nodeId) {
        selectedNodeId = nodeId;
        draw();
    }

    /**
     * 设置节点选择回调
     */
    public void setOnNodeSelected(Consumer<String> handler) {
        this.onNodeSelected = handler;
    }

    /**
     * 设置节点双击回调
     */
    public void setOnNodeDoubleClick(Consumer<String> handler) {
        this.onNodeDoubleClick = handler;
    }

    /**
     * 获取节点数量
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * 获取边数量
     */
    public int getEdgeCount() {
        return edges.size();
    }
}
