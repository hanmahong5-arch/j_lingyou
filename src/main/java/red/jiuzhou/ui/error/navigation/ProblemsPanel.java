package red.jiuzhou.ui.error.navigation;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ui.error.structured.ErrorLevel;
import red.jiuzhou.ui.error.structured.FixSuggestion;
import red.jiuzhou.ui.error.structured.StructuredError;

import java.util.List;
import java.util.function.Predicate;

/**
 * 问题面板 - 统一显示所有错误和警告
 *
 * <p>功能:
 * <ul>
 *   <li>按严重级别分组显示</li>
 *   <li>双击跳转到错误位置</li>
 *   <li>右键菜单提供快速修复</li>
 *   <li>支持过滤和搜索</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class ProblemsPanel extends VBox {

    private static final Logger log = LoggerFactory.getLogger(ProblemsPanel.class);

    private final TableView<ProblemItem> problemTable;
    private final ObservableList<ProblemItem> allProblems;
    private final FilteredList<ProblemItem> filteredProblems;

    private final ErrorNavigationService navigationService;

    // 过滤状态
    private boolean showErrors = true;
    private boolean showWarnings = true;
    private boolean showInfo = true;
    private String searchText = "";

    // 统计标签
    private Label statsLabel;

    public ProblemsPanel(ErrorNavigationService navigationService) {
        this.navigationService = navigationService;
        this.allProblems = FXCollections.observableArrayList();
        this.filteredProblems = new FilteredList<>(allProblems);
        this.problemTable = new TableView<>();

        initUI();

        log.info("问题面板初始化完成");
    }

    private void initUI() {
        setSpacing(0);
        setStyle("-fx-background-color: #f5f5f5;");

        // 工具栏
        ToolBar toolbar = createToolbar();

        // 问题列表
        setupProblemTable();

        // 布局
        VBox.setVgrow(problemTable, Priority.ALWAYS);
        getChildren().addAll(toolbar, problemTable);
    }

    private ToolBar createToolbar() {
        ToolBar toolbar = new ToolBar();
        toolbar.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e0e0e0; " +
            "-fx-border-width: 0 0 1 0;");

        // 过滤按钮
        ToggleButton errorsBtn = new ToggleButton("❌ 错误");
        errorsBtn.setSelected(true);
        errorsBtn.setOnAction(e -> {
            showErrors = errorsBtn.isSelected();
            updateFilter();
        });

        ToggleButton warningsBtn = new ToggleButton("⚠️ 警告");
        warningsBtn.setSelected(true);
        warningsBtn.setOnAction(e -> {
            showWarnings = warningsBtn.isSelected();
            updateFilter();
        });

        ToggleButton infoBtn = new ToggleButton("ℹ️ 信息");
        infoBtn.setSelected(true);
        infoBtn.setOnAction(e -> {
            showInfo = infoBtn.isSelected();
            updateFilter();
        });

        // 搜索框
        TextField searchField = new TextField();
        searchField.setPromptText("过滤问题...");
        searchField.setPrefWidth(200);
        searchField.textProperty().addListener((obs, old, text) -> {
            searchText = text != null ? text.toLowerCase() : "";
            updateFilter();
        });

        // 统计标签
        statsLabel = new Label("0 个问题");
        statsLabel.setStyle("-fx-text-fill: #757575;");

        // 清除按钮
        Button clearBtn = new Button("清除全部");
        clearBtn.setStyle("-fx-background-color: transparent;");
        clearBtn.setOnAction(e -> clearAllProblems());

        // 刷新按钮
        Button refreshBtn = new Button("🔄");
        refreshBtn.setTooltip(new Tooltip("刷新"));
        refreshBtn.setOnAction(e -> refreshProblems());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getItems().addAll(
            errorsBtn, warningsBtn, infoBtn,
            new Separator(Orientation.VERTICAL),
            searchField,
            spacer,
            statsLabel,
            new Separator(Orientation.VERTICAL),
            refreshBtn, clearBtn
        );

        return toolbar;
    }

    private void setupProblemTable() {
        problemTable.setItems(filteredProblems);
        problemTable.setPlaceholder(new Label("暂无问题"));
        problemTable.setStyle("-fx-background-color: white;");

        // 图标列
        TableColumn<ProblemItem, String> iconCol = new TableColumn<>("");
        iconCol.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getIcon()));
        iconCol.setPrefWidth(35);
        iconCol.setSortable(false);

        // 消息列
        TableColumn<ProblemItem, String> messageCol = new TableColumn<>("消息");
        messageCol.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getMessage()));
        messageCol.setPrefWidth(400);

        // 错误码列
        TableColumn<ProblemItem, String> codeCol = new TableColumn<>("错误码");
        codeCol.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getErrorCode()));
        codeCol.setPrefWidth(80);

        // 位置列
        TableColumn<ProblemItem, String> locationCol = new TableColumn<>("位置");
        locationCol.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getLocationString()));
        locationCol.setPrefWidth(200);

        // 时间列
        TableColumn<ProblemItem, String> timeCol = new TableColumn<>("时间");
        timeCol.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getTimestamp()));
        timeCol.setPrefWidth(100);

        problemTable.getColumns().addAll(iconCol, messageCol, codeCol, locationCol, timeCol);

        // 双击导航
        problemTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                ProblemItem selected = problemTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    navigationService.navigateToError(selected.getError());
                }
            }
        });

        // 右键菜单
        problemTable.setContextMenu(createContextMenu());

        // 行样式
        problemTable.setRowFactory(tv -> {
            TableRow<ProblemItem> row = new TableRow<>() {
                @Override
                protected void updateItem(ProblemItem item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setStyle("");
                    } else {
                        ErrorLevel level = item.getError().level();
                        String bgColor = switch (level) {
                            case ERROR, FATAL -> "#ffebee";
                            case WARNING -> "#fff3e0";
                            default -> "#ffffff";
                        };
                        setStyle("-fx-background-color: " + bgColor + ";");
                    }
                }
            };
            return row;
        });
    }

    private ContextMenu createContextMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem navigateItem = new MenuItem("跳转到位置");
        navigateItem.setOnAction(e -> {
            ProblemItem selected = getSelectedProblem();
            if (selected != null) {
                navigationService.navigateToError(selected.getError());
            }
        });

        MenuItem copyItem = new MenuItem("复制错误信息");
        copyItem.setOnAction(e -> {
            ProblemItem selected = getSelectedProblem();
            if (selected != null) {
                copyToClipboard(selected.getError().toHumanReadable());
            }
        });

        MenuItem copyJsonItem = new MenuItem("复制为JSON (给AI分析)");
        copyJsonItem.setOnAction(e -> {
            ProblemItem selected = getSelectedProblem();
            if (selected != null) {
                copyToClipboard(selected.getError().toJson());
            }
        });

        MenuItem removeItem = new MenuItem("移除此问题");
        removeItem.setOnAction(e -> {
            ProblemItem selected = getSelectedProblem();
            if (selected != null) {
                allProblems.remove(selected);
                updateStats();
            }
        });

        // 快速修复子菜单
        Menu fixMenu = new Menu("快速修复");

        menu.setOnShowing(e -> {
            ProblemItem selected = getSelectedProblem();
            fixMenu.getItems().clear();

            if (selected != null && selected.getError().suggestions() != null) {
                for (FixSuggestion suggestion : selected.getError().suggestions()) {
                    MenuItem fixItem = new MenuItem(suggestion.toDisplayString());
                    fixItem.setOnAction(evt -> {
                        if (suggestion.apply()) {
                            log.info("修复成功: {}", suggestion.title());
                        }
                    });
                    fixMenu.getItems().add(fixItem);
                }
            }

            if (fixMenu.getItems().isEmpty()) {
                MenuItem noFix = new MenuItem("(无可用修复)");
                noFix.setDisable(true);
                fixMenu.getItems().add(noFix);
            }
        });

        menu.getItems().addAll(
            navigateItem,
            new SeparatorMenuItem(),
            copyItem, copyJsonItem,
            new SeparatorMenuItem(),
            fixMenu,
            new SeparatorMenuItem(),
            removeItem
        );

        return menu;
    }

    /**
     * 更新过滤器
     */
    private void updateFilter() {
        filteredProblems.setPredicate(item -> {
            // 级别过滤
            ErrorLevel level = item.getError().level();
            if (level == ErrorLevel.ERROR || level == ErrorLevel.FATAL) {
                if (!showErrors) return false;
            } else if (level == ErrorLevel.WARNING) {
                if (!showWarnings) return false;
            } else if (level == ErrorLevel.INFO) {
                if (!showInfo) return false;
            }

            // 文本过滤
            if (!searchText.isEmpty()) {
                String message = item.getMessage().toLowerCase();
                String code = item.getErrorCode().toLowerCase();
                String location = item.getLocationString().toLowerCase();
                return message.contains(searchText) ||
                       code.contains(searchText) ||
                       location.contains(searchText);
            }

            return true;
        });

        updateStats();
    }

    /**
     * 更新统计信息
     */
    private void updateStats() {
        long errorCount = allProblems.stream()
            .filter(p -> p.getError().level() == ErrorLevel.ERROR ||
                        p.getError().level() == ErrorLevel.FATAL)
            .count();
        long warningCount = allProblems.stream()
            .filter(p -> p.getError().level() == ErrorLevel.WARNING)
            .count();
        long infoCount = allProblems.stream()
            .filter(p -> p.getError().level() == ErrorLevel.INFO)
            .count();

        String stats = String.format("%d 错误, %d 警告, %d 信息",
            errorCount, warningCount, infoCount);
        Platform.runLater(() -> statsLabel.setText(stats));
    }

    /**
     * 获取选中的问题项
     */
    private ProblemItem getSelectedProblem() {
        return problemTable.getSelectionModel().getSelectedItem();
    }

    /**
     * 复制到剪贴板
     */
    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    // ==================== 公共方法 ====================

    /**
     * 添加问题
     */
    public void addProblem(StructuredError error) {
        Platform.runLater(() -> {
            allProblems.add(new ProblemItem(error));
            updateStats();
        });
    }

    /**
     * 批量添加问题
     */
    public void addProblems(List<StructuredError> errors) {
        Platform.runLater(() -> {
            errors.forEach(e -> allProblems.add(new ProblemItem(e)));
            updateStats();
        });
    }

    /**
     * 清除所有问题
     */
    public void clearAllProblems() {
        Platform.runLater(() -> {
            allProblems.clear();
            updateStats();
        });
    }

    /**
     * 刷新问题列表
     */
    public void refreshProblems() {
        problemTable.refresh();
        updateStats();
    }

    /**
     * 获取问题总数
     */
    public int getProblemCount() {
        return allProblems.size();
    }

    /**
     * 获取所有问题
     */
    public List<StructuredError> getAllErrors() {
        return allProblems.stream()
            .map(ProblemItem::getError)
            .toList();
    }

    // ==================== 内部类 ====================

    /**
     * 问题项
     */
    public static class ProblemItem {
        private final StructuredError error;

        public ProblemItem(StructuredError error) {
            this.error = error;
        }

        public StructuredError getError() {
            return error;
        }

        public String getIcon() {
            return error.level().getIcon();
        }

        public String getMessage() {
            return error.title() + (error.message() != null ? " - " + error.message() : "");
        }

        public String getErrorCode() {
            return error.errorCode();
        }

        public String getLocationString() {
            if (error.location() != null && error.location().isNavigable()) {
                return error.location().toClickableString();
            }
            return "";
        }

        public String getTimestamp() {
            return error.getFormattedTimestamp();
        }
    }
}
