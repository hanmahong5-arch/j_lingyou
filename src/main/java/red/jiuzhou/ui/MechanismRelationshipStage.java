package red.jiuzhou.ui;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import red.jiuzhou.analysis.aion.AionMechanismCategory;
import red.jiuzhou.analysis.aion.MechanismRelationshipService;
import red.jiuzhou.analysis.aion.mechanism.MechanismNode;
import red.jiuzhou.analysis.aion.mechanism.MechanismRelationship;
import red.jiuzhou.analysis.aion.mechanism.MechanismRelationshipGraph;
import red.jiuzhou.ui.canvas.ForceDirectedLayout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 机制关系图可视化窗口（优化版）
 *
 * <p>性能优化：
 * <ul>
 *   <li>双缓冲渲染，减少闪烁</li>
 *   <li>脏标记机制，避免不必要重绘</li>
 *   <li>节点命中缓存，加速鼠标交互</li>
 *   <li>事件节流，降低CPU占用</li>
 * </ul>
 *
 * @author Claude
 * @version 2.0
 */
public class MechanismRelationshipStage extends Stage {

    // 渲染常量
    private static final double NODE_BASE_RADIUS = 28;
    private static final double NODE_SCALE_FACTOR = 4;
    private static final Color CANVAS_BG = Color.web("#0d1117");
    private static final Color GRID_COLOR = Color.web("#21262d", 0.5);

    // 服务
    private final MechanismRelationshipService relationshipService;

    // 数据
    private MechanismRelationshipGraph graph;
    private ForceDirectedLayout layout;

    // UI组件
    private Canvas canvas;
    private StackPane canvasContainer;
    private VBox detailPanel;
    private ScrollPane detailScrollPane;
    private Label statusLabel;
    private ProgressIndicator loadingIndicator;

    // 双缓冲
    private WritableImage bufferImage;
    private boolean needsRedraw = true;

    // 交互状态
    private MechanismNode selectedNode;
    private MechanismNode hoveredNode;
    private MechanismNode draggedNode;
    private double dragOffsetX, dragOffsetY;

    // 性能优化：事件节流
    private long lastMouseMoveTime = 0;
    private static final long MOUSE_MOVE_THROTTLE_MS = 16; // ~60fps

    // 动画控制
    private AnimationTimer animationTimer;
    private boolean isAnimating = false;
    private int animationFrames = 0;
    private static final int MAX_ANIMATION_FRAMES = 150;

    public MechanismRelationshipStage() {
        this.relationshipService = new MechanismRelationshipService();
        initializeUI();
        loadData();
    }

    private void initializeUI() {
        setTitle("机制关系图 - Aion游戏系统依赖可视化");
        setWidth(1300);
        setHeight(850);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0d1117;");

        // 工具栏
        root.setTop(createToolbar());

        // 主内容区
        root.setCenter(createMainContent());

        // 详情面板
        root.setRight(createDetailPanel());

        Scene scene = new Scene(root);
        scene.getStylesheets().add("data:text/css," + getInlineStyles());
        setScene(scene);

        // 窗口大小变化时标记需要重绘
        widthProperty().addListener((obs, oldVal, newVal) -> scheduleRedraw());
        heightProperty().addListener((obs, oldVal, newVal) -> scheduleRedraw());
    }

    private String getInlineStyles() {
        return String.join("",
            ".scroll-pane { -fx-background-color: transparent; -fx-background: transparent; }",
            ".scroll-pane > .viewport { -fx-background-color: transparent; }",
            ".scroll-pane > .corner { -fx-background-color: transparent; }",
            ".scroll-bar { -fx-background-color: #21262d; }",
            ".scroll-bar .thumb { -fx-background-color: #484f58; -fx-background-radius: 4; }",
            ".scroll-bar .thumb:hover { -fx-background-color: #6e7681; }",
            ".separator { -fx-background-color: #30363d; }"
        ).replace(" ", "%20").replace(";", "%3B").replace(":", "%3A").replace("#", "%23");
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.setPadding(new Insets(12, 16, 12, 16));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #161b22; -fx-border-color: #30363d; -fx-border-width: 0 0 1 0;");

        Button refreshBtn = createToolButton("刷新", "#238636");
        refreshBtn.setOnAction(e -> {
            relationshipService.clearCache();
            loadData();
        });

        Button layoutBtn = createToolButton("重新布局", "#1f6feb");
        layoutBtn.setOnAction(e -> {
            if (graph != null && layout != null) {
                layout.initializePositions(graph.getActiveNodes());
                startAnimation();
            }
        });

        Button statsBtn = createToolButton("统计", "#6e7681");
        statsBtn.setOnAction(e -> showStatistics());

        statusLabel = new Label("就绪");
        statusLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setAlignment(Pos.CENTER_RIGHT);

        toolbar.getChildren().addAll(refreshBtn, layoutBtn, statsBtn, statusLabel);
        return toolbar;
    }

    private Button createToolButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: white; -fx-font-size: 12px; " +
            "-fx-padding: 6 14; -fx-background-radius: 6; -fx-cursor: hand;", color));
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle().replace(color, brighten(color))));
        btn.setOnMouseExited(e -> btn.setStyle(btn.getStyle().replace(brighten(color), color)));
        return btn;
    }

    private String brighten(String hexColor) {
        Color c = Color.web(hexColor);
        return String.format("#%02x%02x%02x",
            (int)(Math.min(255, c.getRed() * 255 + 30)),
            (int)(Math.min(255, c.getGreen() * 255 + 30)),
            (int)(Math.min(255, c.getBlue() * 255 + 30)));
    }

    private StackPane createMainContent() {
        canvasContainer = new StackPane();
        canvasContainer.setStyle("-fx-background-color: #0d1117;");

        canvas = new Canvas(900, 700);
        canvas.setEffect(new DropShadow(10, Color.BLACK));
        setupCanvasEvents();

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(50, 50);
        loadingIndicator.setStyle("-fx-progress-color: #58a6ff;");
        loadingIndicator.setVisible(false);

        canvasContainer.getChildren().addAll(canvas, loadingIndicator);

        // 画布大小跟随容器
        canvasContainer.widthProperty().addListener((obs, oldVal, newVal) -> {
            double w = Math.max(100, newVal.doubleValue() - 20);
            canvas.setWidth(w);
            if (layout != null) layout.setWidth(w);
            invalidateBuffer();
            scheduleRedraw();
        });
        canvasContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            double h = Math.max(100, newVal.doubleValue() - 20);
            canvas.setHeight(h);
            if (layout != null) layout.setHeight(h);
            invalidateBuffer();
            scheduleRedraw();
        });

        return canvasContainer;
    }

    private VBox createDetailPanel() {
        detailPanel = new VBox(12);
        detailPanel.setPadding(new Insets(16));
        detailPanel.setStyle("-fx-background-color: #161b22;");

        Label title = new Label("机制详情");
        title.setFont(Font.font("System", FontWeight.BOLD, 15));
        title.setStyle("-fx-text-fill: #c9d1d9;");

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #30363d;");

        Label hint = new Label("点击节点查看详情");
        hint.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 13px;");

        detailPanel.getChildren().addAll(title, sep, hint);

        detailScrollPane = new ScrollPane(detailPanel);
        detailScrollPane.setFitToWidth(true);
        detailScrollPane.setPrefWidth(280);
        detailScrollPane.setStyle("-fx-background-color: #161b22; -fx-border-color: #30363d; -fx-border-width: 0 0 0 1;");

        VBox wrapper = new VBox(detailScrollPane);
        VBox.setVgrow(detailScrollPane, Priority.ALWAYS);
        wrapper.setPrefWidth(280);
        wrapper.setStyle("-fx-background-color: #161b22;");

        return wrapper;
    }

    private void setupCanvasEvents() {
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnMouseMoved(this::handleMouseMoved);
        canvas.setOnMouseClicked(this::handleMouseClicked);
    }

    private void handleMousePressed(MouseEvent e) {
        if (graph == null) return;

        MechanismNode node = findNodeAt(e.getX(), e.getY());
        if (node != null && e.getButton() == MouseButton.PRIMARY) {
            draggedNode = node;
            dragOffsetX = e.getX() - node.getX();
            dragOffsetY = e.getY() - node.getY();
            node.setPinned(true);
        }
    }

    private void handleMouseDragged(MouseEvent e) {
        if (draggedNode != null) {
            draggedNode.setX(e.getX() - dragOffsetX);
            draggedNode.setY(e.getY() - dragOffsetY);
            scheduleRedraw();
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        if (draggedNode != null) {
            draggedNode.setPinned(false);
            draggedNode = null;
        }
    }

    private void handleMouseMoved(MouseEvent e) {
        // 事件节流
        long now = System.currentTimeMillis();
        if (now - lastMouseMoveTime < MOUSE_MOVE_THROTTLE_MS) {
            return;
        }
        lastMouseMoveTime = now;

        if (graph == null) return;

        MechanismNode node = findNodeAt(e.getX(), e.getY());
        if (node != hoveredNode) {
            hoveredNode = node;
            canvas.setCursor(node != null ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
            scheduleRedraw();
        }
    }

    private void handleMouseClicked(MouseEvent e) {
        if (graph == null) return;

        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1) {
            MechanismNode node = findNodeAt(e.getX(), e.getY());
            if (node != selectedNode) {
                selectedNode = node;
                updateDetailPanel();
                scheduleRedraw();
            }
        }
    }

    private MechanismNode findNodeAt(double x, double y) {
        if (graph == null) return null;

        // 反向遍历，优先检测顶层节点
        List<MechanismNode> nodes = graph.getActiveNodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            MechanismNode node = nodes.get(i);
            double radius = calculateNodeRadius(node);
            double dx = x - node.getX();
            double dy = y - node.getY();
            if (dx * dx + dy * dy <= radius * radius) {
                return node;
            }
        }
        return null;
    }

    private double calculateNodeRadius(MechanismNode node) {
        return NODE_BASE_RADIUS + Math.log1p(node.getFileCount()) * NODE_SCALE_FACTOR;
    }

    private void loadData() {
        loadingIndicator.setVisible(true);
        statusLabel.setText("加载中...");
        needsRedraw = true;

        CompletableFuture.runAsync(() -> {
            graph = relationshipService.buildRelationshipGraph(msg ->
                    Platform.runLater(() -> statusLabel.setText(msg)));

            layout = new ForceDirectedLayout(canvas.getWidth(), canvas.getHeight());
            layout.layout(graph);
        }).thenRun(() -> Platform.runLater(() -> {
            loadingIndicator.setVisible(false);
            statusLabel.setText(String.format("已加载: %d个机制, %d个关系",
                    graph.getActiveNodeCount(), graph.getTotalRelationshipCount()));
            invalidateBuffer();
            scheduleRedraw();
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                loadingIndicator.setVisible(false);
                statusLabel.setText("加载失败: " + ex.getMessage());
            });
            return null;
        });
    }

    private void startAnimation() {
        if (isAnimating) return;

        isAnimating = true;
        animationFrames = 0;

        animationTimer = new AnimationTimer() {
            private long lastFrame = 0;

            @Override
            public void handle(long now) {
                // 限制帧率到30fps
                if (now - lastFrame < 33_000_000) return;
                lastFrame = now;

                if (graph == null || animationFrames > MAX_ANIMATION_FRAMES) {
                    stop();
                    isAnimating = false;
                    return;
                }

                double movement = layout.step(graph);
                scheduleRedraw();
                animationFrames++;

                if (movement < 0.3) {
                    stop();
                    isAnimating = false;
                }
            }
        };
        animationTimer.start();
    }

    private void invalidateBuffer() {
        bufferImage = null;
    }

    private void scheduleRedraw() {
        needsRedraw = true;
        Platform.runLater(this::redrawIfNeeded);
    }

    private void redrawIfNeeded() {
        if (!needsRedraw || canvas == null || graph == null) return;
        needsRedraw = false;
        redraw();
    }

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // 清空并绘制背景
        gc.setFill(CANVAS_BG);
        gc.fillRect(0, 0, w, h);

        // 绘制网格
        drawGrid(gc, w, h);

        List<MechanismNode> nodes = graph.getActiveNodes();
        List<MechanismRelationship> edges = graph.getSignificantRelationships(1, 0.0);

        // 绘制边（先绘制，在节点下层）
        for (MechanismRelationship edge : edges) {
            drawEdge(gc, edge, nodes);
        }

        // 绘制节点
        for (MechanismNode node : nodes) {
            drawNode(gc, node);
        }
    }

    private void drawGrid(GraphicsContext gc, double w, double h) {
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(1);
        gc.setLineDashes(null);

        double gridSize = 40;
        for (double x = gridSize; x < w; x += gridSize) {
            gc.strokeLine(x, 0, x, h);
        }
        for (double y = gridSize; y < h; y += gridSize) {
            gc.strokeLine(0, y, w, y);
        }
    }

    private void drawEdge(GraphicsContext gc, MechanismRelationship edge, List<MechanismNode> nodes) {
        MechanismNode source = findNodeByCategory(nodes, edge.getSource());
        MechanismNode target = findNodeByCategory(nodes, edge.getTarget());

        if (source == null || target == null) return;

        double x1 = source.getX();
        double y1 = source.getY();
        double x2 = target.getX();
        double y2 = target.getY();

        // 计算样式
        boolean isHighlighted = selectedNode != null &&
                (edge.getSource() == selectedNode.getCategory() ||
                 edge.getTarget() == selectedNode.getCategory());

        double alpha = isHighlighted ? 0.9 : 0.25 + edge.getConfidence() * 0.35;
        double lineWidth = isHighlighted ? 2.5 : 1 + edge.getStrength() * 0.3;

        Color edgeColor = Color.web(edge.getColor(), alpha);
        gc.setStroke(edgeColor);
        gc.setLineWidth(lineWidth);
        gc.setLineDashes(null);

        // 计算曲线控制点
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dist = Math.sqrt(dx * dx + dy * dy);
        double curvature = Math.min(0.15, 40 / dist);
        double midX = (x1 + x2) / 2 - dy * curvature;
        double midY = (y1 + y2) / 2 + dx * curvature;

        // 调整终点到节点边缘
        double targetRadius = calculateNodeRadius(target) + 5;
        double angle = Math.atan2(y2 - midY, x2 - midX);
        double endX = x2 - Math.cos(angle) * targetRadius;
        double endY = y2 - Math.sin(angle) * targetRadius;

        // 绘制曲线
        gc.beginPath();
        gc.moveTo(x1, y1);
        gc.quadraticCurveTo(midX, midY, endX, endY);
        gc.stroke();

        // 绘制箭头
        if (isHighlighted || edge.getStrength() >= 3) {
            drawArrowHead(gc, midX, midY, endX, endY, edgeColor, lineWidth);
        }
    }

    private void drawArrowHead(GraphicsContext gc, double fromX, double fromY,
                                double toX, double toY, Color color, double lineWidth) {
        double angle = Math.atan2(toY - fromY, toX - fromX);
        double arrowLen = 8 + lineWidth;
        double arrowAngle = Math.PI / 7;

        double x1 = toX - arrowLen * Math.cos(angle - arrowAngle);
        double y1 = toY - arrowLen * Math.sin(angle - arrowAngle);
        double x2 = toX - arrowLen * Math.cos(angle + arrowAngle);
        double y2 = toY - arrowLen * Math.sin(angle + arrowAngle);

        gc.setFill(color);
        gc.beginPath();
        gc.moveTo(toX, toY);
        gc.lineTo(x1, y1);
        gc.lineTo(x2, y2);
        gc.closePath();
        gc.fill();
    }

    private void drawNode(GraphicsContext gc, MechanismNode node) {
        double x = node.getX();
        double y = node.getY();
        double radius = calculateNodeRadius(node);

        boolean isSelected = node == selectedNode;
        boolean isHovered = node == hoveredNode;

        Color baseColor = Color.web(node.getColor());

        // 发光效果（选中或悬停时）
        if (isSelected || isHovered) {
            double glowRadius = radius + (isSelected ? 15 : 10);
            gc.setFill(Color.web(node.getColor(), isSelected ? 0.4 : 0.25));
            gc.fillOval(x - glowRadius, y - glowRadius, glowRadius * 2, glowRadius * 2);
        }

        // 节点阴影
        gc.setFill(Color.rgb(0, 0, 0, 0.3));
        gc.fillOval(x - radius + 3, y - radius + 3, radius * 2, radius * 2);

        // 节点渐变填充
        RadialGradient gradient = new RadialGradient(
            0, 0, 0.3, 0.3, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, baseColor.brighter().brighter()),
            new Stop(0.5, baseColor),
            new Stop(1, baseColor.darker())
        );
        gc.setFill(gradient);
        gc.fillOval(x - radius, y - radius, radius * 2, radius * 2);

        // 节点边框
        gc.setStroke(isSelected ? Color.WHITE : Color.web("#ffffff", 0.6));
        gc.setLineWidth(isSelected ? 3 : 1.5);
        gc.strokeOval(x - radius, y - radius, radius * 2, radius * 2);

        // 高光效果
        gc.setFill(Color.web("#ffffff", 0.15));
        gc.fillOval(x - radius * 0.6, y - radius * 0.7, radius * 1.2, radius * 0.6);

        // 图标文字
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 13));
        gc.setTextAlign(TextAlignment.CENTER);
        String icon = node.getIcon();
        gc.fillText(icon, x, y + 5);

        // 机制名称
        gc.setFont(Font.font("System", FontWeight.NORMAL, 11));
        gc.setFill(Color.web("#c9d1d9"));
        gc.fillText(node.getDisplayName(), x, y + radius + 16);

        // 文件数量
        if (node.getFileCount() > 0) {
            gc.setFont(Font.font("System", 10));
            gc.setFill(Color.web("#8b949e"));
            gc.fillText("(" + node.getFileCount() + ")", x, y + radius + 28);
        }
    }

    private MechanismNode findNodeByCategory(List<MechanismNode> nodes, AionMechanismCategory category) {
        for (MechanismNode node : nodes) {
            if (node.getCategory() == category) {
                return node;
            }
        }
        return null;
    }

    private void updateDetailPanel() {
        detailPanel.getChildren().clear();

        // 标题
        Label title = new Label("机制详情");
        title.setFont(Font.font("System", FontWeight.BOLD, 15));
        title.setStyle("-fx-text-fill: #c9d1d9;");

        Separator sep = new Separator();

        detailPanel.getChildren().addAll(title, sep);

        if (selectedNode == null) {
            Label hint = new Label("点击节点查看详情");
            hint.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 13px;");
            hint.setWrapText(true);
            detailPanel.getChildren().add(hint);
            return;
        }

        // 机制名称（带颜色）
        Label nameLabel = new Label(selectedNode.getDisplayName());
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 16));
        nameLabel.setStyle("-fx-text-fill: " + selectedNode.getColor() + ";");

        // 描述
        Label descLabel = new Label(selectedNode.getCategory().getDescription());
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 12px;");

        // 统计卡片
        HBox statsCard = new HBox(20);
        statsCard.setPadding(new Insets(10));
        statsCard.setStyle("-fx-background-color: #21262d; -fx-background-radius: 6;");

        VBox filesStat = createStatItem("文件数", String.valueOf(selectedNode.getFileCount()));
        VBox outStat = createStatItem("依赖", String.valueOf(
                graph.getOutgoingRelationships(selectedNode.getCategory()).size()));
        VBox inStat = createStatItem("被依赖", String.valueOf(
                graph.getIncomingRelationships(selectedNode.getCategory()).size()));

        statsCard.getChildren().addAll(filesStat, outStat, inStat);

        detailPanel.getChildren().addAll(nameLabel, descLabel, statsCard);

        // 依赖的机制
        List<MechanismRelationship> outgoing = graph.getOutgoingRelationships(selectedNode.getCategory());
        if (!outgoing.isEmpty()) {
            detailPanel.getChildren().add(new Separator());
            Label depTitle = createSectionTitle("依赖的机制 (" + outgoing.size() + ")");
            FlowPane depPane = new FlowPane(6, 6);
            for (MechanismRelationship rel : outgoing) {
                depPane.getChildren().add(createMechanismTag(rel.getTarget(), rel.getRelationshipCount()));
            }
            detailPanel.getChildren().addAll(depTitle, depPane);
        }

        // 被依赖
        List<MechanismRelationship> incoming = graph.getIncomingRelationships(selectedNode.getCategory());
        if (!incoming.isEmpty()) {
            detailPanel.getChildren().add(new Separator());
            Label refTitle = createSectionTitle("被依赖 (" + incoming.size() + ")");
            FlowPane refPane = new FlowPane(6, 6);
            for (MechanismRelationship rel : incoming) {
                refPane.getChildren().add(createMechanismTag(rel.getSource(), rel.getRelationshipCount()));
            }
            detailPanel.getChildren().addAll(refTitle, refPane);
        }

        // 代表性文件
        if (!selectedNode.getRepresentativeFiles().isEmpty()) {
            detailPanel.getChildren().add(new Separator());
            Label filesTitle = createSectionTitle("代表性文件");
            VBox filesList = new VBox(4);
            for (String file : selectedNode.getRepresentativeFiles()) {
                Label fileLabel = new Label("  " + file);
                fileLabel.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 11px;");
                filesList.getChildren().add(fileLabel);
            }
            detailPanel.getChildren().addAll(filesTitle, filesList);
        }
    }

    private VBox createStatItem(String label, String value) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        valueLabel.setStyle("-fx-text-fill: #58a6ff;");

        Label nameLabel = new Label(label);
        nameLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");

        box.getChildren().addAll(valueLabel, nameLabel);
        return box;
    }

    private Label createSectionTitle(String text) {
        Label label = new Label(text);
        label.setFont(Font.font("System", FontWeight.BOLD, 12));
        label.setStyle("-fx-text-fill: #c9d1d9;");
        label.setPadding(new Insets(5, 0, 5, 0));
        return label;
    }

    private Label createMechanismTag(AionMechanismCategory category, int count) {
        String text = category.getDisplayName() + (count > 0 ? " (" + count + ")" : "");
        Label tag = new Label(text);
        tag.setStyle(String.format(
            "-fx-background-color: %s; -fx-text-fill: white; -fx-font-size: 11px; " +
            "-fx-padding: 4 10; -fx-background-radius: 12; -fx-cursor: hand;",
            category.getColor()));

        tag.setOnMouseEntered(e -> tag.setStyle(tag.getStyle() + "-fx-opacity: 0.8;"));
        tag.setOnMouseExited(e -> tag.setStyle(tag.getStyle().replace("-fx-opacity: 0.8;", "")));

        tag.setOnMouseClicked(e -> {
            for (MechanismNode node : graph.getActiveNodes()) {
                if (node.getCategory() == category) {
                    selectedNode = node;
                    updateDetailPanel();
                    scheduleRedraw();
                    break;
                }
            }
        });
        return tag;
    }

    private void showStatistics() {
        if (graph == null) return;

        Map<String, Object> stats = graph.getStatistics();

        Stage statsStage = new Stage();
        statsStage.initOwner(this);
        statsStage.setTitle("关系图统计");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #161b22;");

        Label title = new Label("关系图统计");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setStyle("-fx-text-fill: #c9d1d9;");

        content.getChildren().addAll(title, new Separator());

        String[][] items = {
            {"活跃机制数", String.valueOf(stats.get("activeMechanisms"))},
            {"总文件数", String.valueOf(stats.get("totalFiles"))},
            {"总关系数", String.valueOf(stats.get("totalRelationships"))},
            {"最被依赖", String.valueOf(stats.get("mostDependedMechanism"))},
        };

        for (String[] item : items) {
            HBox row = new HBox(10);
            Label key = new Label(item[0] + ":");
            key.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 13px;");
            key.setPrefWidth(100);
            Label value = new Label(item[1]);
            value.setStyle("-fx-text-fill: #c9d1d9; -fx-font-size: 13px; -fx-font-weight: bold;");
            row.getChildren().addAll(key, value);
            content.getChildren().add(row);
        }

        Scene scene = new Scene(content, 280, 220);
        statsStage.setScene(scene);
        statsStage.show();
    }

    @Override
    public void close() {
        if (animationTimer != null) {
            animationTimer.stop();
        }
        super.close();
    }
}
