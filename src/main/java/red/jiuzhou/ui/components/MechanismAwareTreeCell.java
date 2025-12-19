package red.jiuzhou.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import red.jiuzhou.analysis.aion.AionMechanismCategory;
import red.jiuzhou.analysis.aion.MechanismFileMapper;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 机制感知的树节点单元格
 *
 * 为文件节点添加机制标记：
 * - 彩色圆点指示所属机制
 * - 悬停显示机制详情
 * - 右键菜单支持机制相关操作
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class MechanismAwareTreeCell<T> extends TreeCell<T> {

    /** 路径解析器 */
    private final Function<TreeItem<T>, String> pathResolver;

    /** 机制过滤回调 */
    private Consumer<AionMechanismCategory> onFilterByMechanism;

    /** 打开机制浏览器回调 */
    private Consumer<AionMechanismCategory> onOpenMechanismExplorer;

    /** 是否显示机制标记 */
    private boolean showMechanismMarker = true;

    /** 机制标记圆点大小 */
    private static final double MARKER_SIZE = 6;

    public MechanismAwareTreeCell(Function<TreeItem<T>, String> pathResolver) {
        this.pathResolver = pathResolver;
        setupContextMenu();
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setTooltip(null);
            return;
        }

        String displayText = item.toString();
        TreeItem<T> treeItem = getTreeItem();

        // 判断是否为文件（叶子节点）
        boolean isFile = treeItem != null && treeItem.isLeaf();

        if (isFile && showMechanismMarker && pathResolver != null) {
            String filePath = pathResolver.apply(treeItem);
            if (filePath != null && filePath.toLowerCase().endsWith(".xml")) {
                // 获取文件机制
                AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(filePath);

                // 创建带机制标记的布局
                HBox container = createMarkedContent(displayText, mechanism, filePath);
                setGraphic(container);
                setText(null);

                // 设置悬停提示
                setTooltip(createMechanismTooltip(mechanism, filePath));
                return;
            }
        }

        // 普通显示
        setText(displayText);
        setGraphic(null);
        setTooltip(null);
    }

    /**
     * 创建带机制标记的内容
     */
    private HBox createMarkedContent(String text, AionMechanismCategory mechanism, String filePath) {
        HBox container = new HBox(6);
        container.setAlignment(Pos.CENTER_LEFT);

        // 机制颜色标记
        Circle marker = new Circle(MARKER_SIZE / 2);
        try {
            marker.setFill(Color.web(mechanism.getColor()));
        } catch (Exception e) {
            marker.setFill(Color.GRAY);
        }
        marker.setStroke(Color.web(mechanism.getColor()).darker());
        marker.setStrokeWidth(0.5);

        // 文件名标签
        Label nameLabel = new Label(text);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        // 机制简称标签（可选，用于OTHER以外的机制）
        if (mechanism != AionMechanismCategory.OTHER) {
            Label mechanismLabel = new Label(mechanism.getIcon());
            mechanismLabel.setStyle(String.format(
                "-fx-font-size: 9px; " +
                "-fx-text-fill: %s; " +
                "-fx-padding: 0 3; " +
                "-fx-background-color: %s; " +
                "-fx-background-radius: 3;",
                mechanism.getColor(),
                lightenColor(mechanism.getColor(), 0.85)
            ));
            container.getChildren().addAll(marker, nameLabel, mechanismLabel);
        } else {
            container.getChildren().addAll(marker, nameLabel);
        }

        return container;
    }

    /**
     * 创建机制提示
     */
    private Tooltip createMechanismTooltip(AionMechanismCategory mechanism, String filePath) {
        StringBuilder sb = new StringBuilder();
        sb.append("📁 ").append(new File(filePath).getName()).append("\n");
        sb.append("🎮 机制: ").append(mechanism.getDisplayName()).append("\n");
        sb.append("📝 ").append(mechanism.getDescription()).append("\n");
        sb.append("\n右键可快速过滤此机制的所有文件");

        Tooltip tooltip = new Tooltip(sb.toString());
        tooltip.setStyle("-fx-font-size: 11px;");
        return tooltip;
    }

    /**
     * 设置右键菜单
     */
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        // 查看文件机制
        MenuItem viewMechanismItem = new MenuItem("🎮 查看文件机制");
        viewMechanismItem.setOnAction(e -> {
            TreeItem<T> selected = getTreeItem();
            if (selected != null && pathResolver != null) {
                String path = pathResolver.apply(selected);
                if (path != null) {
                    AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(path);
                    showMechanismInfo(mechanism, path);
                }
            }
        });

        // 过滤此机制
        MenuItem filterMechanismItem = new MenuItem("🔍 只显示此机制的文件");
        filterMechanismItem.setOnAction(e -> {
            TreeItem<T> selected = getTreeItem();
            if (selected != null && pathResolver != null && onFilterByMechanism != null) {
                String path = pathResolver.apply(selected);
                if (path != null) {
                    AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(path);
                    onFilterByMechanism.accept(mechanism);
                }
            }
        });

        // 在机制浏览器中查看
        MenuItem openExplorerItem = new MenuItem("📊 在机制浏览器中打开");
        openExplorerItem.setOnAction(e -> {
            TreeItem<T> selected = getTreeItem();
            if (selected != null && pathResolver != null && onOpenMechanismExplorer != null) {
                String path = pathResolver.apply(selected);
                if (path != null) {
                    AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(path);
                    onOpenMechanismExplorer.accept(mechanism);
                }
            }
        });

        // 分隔符
        SeparatorMenuItem separator = new SeparatorMenuItem();

        // 复制机制名称
        MenuItem copyMechanismItem = new MenuItem("📋 复制机制名称");
        copyMechanismItem.setOnAction(e -> {
            TreeItem<T> selected = getTreeItem();
            if (selected != null && pathResolver != null) {
                String path = pathResolver.apply(selected);
                if (path != null) {
                    AionMechanismCategory mechanism = MechanismFileMapper.detectMechanismStatic(path);
                    ContextMenuFactory.copyToClipboard(mechanism.getDisplayName());
                }
            }
        });

        contextMenu.getItems().addAll(
            viewMechanismItem,
            filterMechanismItem,
            openExplorerItem,
            separator,
            copyMechanismItem
        );

        // 动态显示菜单项
        contextMenu.setOnShowing(e -> {
            TreeItem<T> selected = getTreeItem();
            boolean isFile = selected != null && selected.isLeaf();
            boolean hasPath = isFile && pathResolver != null;

            viewMechanismItem.setDisable(!hasPath);
            filterMechanismItem.setDisable(!hasPath || onFilterByMechanism == null);
            openExplorerItem.setDisable(!hasPath || onOpenMechanismExplorer == null);
            copyMechanismItem.setDisable(!hasPath);
        });

        // 只为文件节点设置右键菜单
        setOnContextMenuRequested(event -> {
            TreeItem<T> item = getTreeItem();
            if (item != null && item.isLeaf()) {
                contextMenu.show(this, event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });
    }

    /**
     * 显示机制信息对话框
     */
    private void showMechanismInfo(AionMechanismCategory mechanism, String filePath) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("文件机制信息");
        alert.setHeaderText(new File(filePath).getName());

        StringBuilder content = new StringBuilder();
        content.append("机制分类: ").append(mechanism.getDisplayName()).append("\n");
        content.append("机制图标: ").append(mechanism.getIcon()).append("\n");
        content.append("机制描述: ").append(mechanism.getDescription()).append("\n");
        content.append("优先级: ").append(mechanism.getPriority()).append("\n");
        content.append("\n文件路径:\n").append(filePath);

        alert.setContentText(content.toString());
        alert.showAndWait();
    }

    /**
     * 颜色变浅
     */
    private String lightenColor(String hexColor, double factor) {
        try {
            Color color = Color.web(hexColor);
            double r = color.getRed() + (1 - color.getRed()) * factor;
            double g = color.getGreen() + (1 - color.getGreen()) * factor;
            double b = color.getBlue() + (1 - color.getBlue()) * factor;
            return String.format("#%02X%02X%02X",
                (int)(r * 255), (int)(g * 255), (int)(b * 255));
        } catch (Exception e) {
            return "#f8f9fa";
        }
    }

    // ==================== Setters ====================

    /**
     * 设置是否显示机制标记
     */
    public void setShowMechanismMarker(boolean show) {
        this.showMechanismMarker = show;
    }

    /**
     * 设置机制过滤回调
     */
    public void setOnFilterByMechanism(Consumer<AionMechanismCategory> callback) {
        this.onFilterByMechanism = callback;
    }

    /**
     * 设置打开机制浏览器回调
     */
    public void setOnOpenMechanismExplorer(Consumer<AionMechanismCategory> callback) {
        this.onOpenMechanismExplorer = callback;
    }

    /**
     * 创建工厂方法
     */
    public static <T> javafx.util.Callback<TreeView<T>, TreeCell<T>> createFactory(
            Function<TreeItem<T>, String> pathResolver,
            Consumer<AionMechanismCategory> onFilterByMechanism,
            Consumer<AionMechanismCategory> onOpenMechanismExplorer) {

        return treeView -> {
            MechanismAwareTreeCell<T> cell = new MechanismAwareTreeCell<>(pathResolver);
            cell.setOnFilterByMechanism(onFilterByMechanism);
            cell.setOnOpenMechanismExplorer(onOpenMechanismExplorer);
            return cell;
        };
    }
}
