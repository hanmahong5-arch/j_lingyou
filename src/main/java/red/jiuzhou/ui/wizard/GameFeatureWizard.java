package red.jiuzhou.ui.wizard;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.feature.GameFeatureCategory;
import red.jiuzhou.pattern.dao.TemplateParamDao;
import red.jiuzhou.pattern.model.TemplateParam;
import red.jiuzhou.template.TemplateExtractor;
import red.jiuzhou.template.TemplateInstantiator;
import red.jiuzhou.ui.form.SmartFormBuilder;
import red.jiuzhou.util.DatabaseUtil;

import java.util.*;

/**
 * 游戏功能创建向导
 *
 * <p>三步向导式界面，引导设计师快速创建游戏功能（副本、任务、物品等）。
 * 通过选择功能类型、选择模板、填写参数三个步骤，自动处理ID分配、引用更新等技术细节。
 *
 * <p><b>向导流程：</b>
 * <ol>
 *   <li><b>步骤1：选择功能类型</b> - 从12个游戏功能中选择（副本、任务、物品等）</li>
 *   <li><b>步骤2：选择模板</b> - 从现有数据中选择要克隆的记录</li>
 *   <li><b>步骤3：填写参数</b> - 使用智能表单填写可变字段</li>
 * </ol>
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>功能类型选择（12个卡片式按钮）</li>
 *   <li>模板提取（从选中的记录提取模板）</li>
 *   <li>智能表单（自动识别字段类型）</li>
 *   <li>预览实例化结果</li>
 *   <li>保存到数据库</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * GameFeatureWizard wizard = new GameFeatureWizard();
 * wizard.showAndWait();
 *
 * // 向导关闭后
 * if (wizard.isSuccess()) {
 *     String newRecordId = wizard.getNewRecordId();
 *     System.out.println("创建成功: " + newRecordId);
 * }
 * }</pre>
 *
 * @author Claude
 * @version 1.0
 * @since 2025-12-30
 */
public class GameFeatureWizard extends Stage {
    private static final Logger log = LoggerFactory.getLogger(GameFeatureWizard.class);

    private final JdbcTemplate jdbcTemplate;
    private final TemplateExtractor templateExtractor;
    private final TemplateInstantiator templateInstantiator;
    private final TemplateParamDao templateParamDao;

    // 向导状态
    private int currentStep = 1;
    private GameFeatureCategory selectedFeature;
    private String selectedRecordId;
    private int extractedTemplateId;
    private SmartFormBuilder formBuilder;

    // 结果
    private boolean success = false;
    private String newRecordId;

    // UI组件
    private BorderPane rootPane;
    private StackPane contentPane;
    private HBox navigationBar;
    private Button prevButton;
    private Button nextButton;
    private Button cancelButton;
    private Button finishButton;
    private Label stepIndicator;

    // 步骤面板
    private VBox step1Panel;
    private VBox step2Panel;
    private VBox step3Panel;

    // ========== 构造函数 ==========

    public GameFeatureWizard() {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        this.templateExtractor = new TemplateExtractor(jdbcTemplate);
        this.templateInstantiator = new TemplateInstantiator(jdbcTemplate);
        this.templateParamDao = new TemplateParamDao(jdbcTemplate);

        initializeUI();
        updateStepDisplay();
    }

    // ========== UI 初始化 ==========

    /**
     * 初始化UI
     */
    private void initializeUI() {
        setTitle("游戏功能创建向导");
        initModality(Modality.APPLICATION_MODAL);
        setWidth(900);
        setHeight(700);

        // 根布局
        rootPane = new BorderPane();
        rootPane.setPadding(new Insets(10));

        // 顶部：步骤指示器
        stepIndicator = new Label();
        stepIndicator.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        BorderPane.setMargin(stepIndicator, new Insets(0, 0, 10, 0));
        rootPane.setTop(stepIndicator);

        // 中间：内容面板
        contentPane = new StackPane();
        contentPane.setPadding(new Insets(10));
        rootPane.setCenter(contentPane);

        // 底部：导航栏
        navigationBar = createNavigationBar();
        BorderPane.setMargin(navigationBar, new Insets(10, 0, 0, 0));
        rootPane.setBottom(navigationBar);

        // 创建各步骤面板
        step1Panel = createStep1Panel();
        step2Panel = createStep2Panel();
        step3Panel = createStep3Panel();

        // 场景
        Scene scene = new Scene(rootPane);
        setScene(scene);
    }

    /**
     * 创建导航栏
     */
    private HBox createNavigationBar() {
        HBox nav = new HBox(10);
        nav.setAlignment(Pos.CENTER_RIGHT);
        nav.setPadding(new Insets(10));

        prevButton = new Button("< 上一步");
        prevButton.setOnAction(e -> previousStep());

        nextButton = new Button("下一步 >");
        nextButton.setOnAction(e -> nextStep());

        finishButton = new Button("完成 ✓");
        finishButton.setOnAction(e -> finish());
        finishButton.setVisible(false);

        cancelButton = new Button("取消");
        cancelButton.setOnAction(e -> cancel());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        nav.getChildren().addAll(cancelButton, spacer, prevButton, nextButton, finishButton);
        return nav;
    }

    // ========== 步骤1：选择功能类型 ==========

    /**
     * 创建步骤1面板
     */
    private VBox createStep1Panel() {
        VBox panel = new VBox(20);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(20));

        Label title = new Label("选择要创建的游戏功能");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // 功能卡片网格
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);

        int col = 0;
        int row = 0;

        for (GameFeatureCategory feature : GameFeatureCategory.values()) {
            VBox card = createFeatureCard(feature);
            grid.add(card, col, row);

            col++;
            if (col >= 3) {
                col = 0;
                row++;
            }
        }

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        panel.getChildren().addAll(title, scrollPane);
        return panel;
    }

    /**
     * 创建功能卡片
     */
    private VBox createFeatureCard(GameFeatureCategory feature) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20));
        card.setStyle(String.format(
                "-fx-border-color: %s; -fx-border-width: 2; -fx-border-radius: 5; " +
                "-fx-background-color: white; -fx-background-radius: 5; -fx-cursor: hand;",
                feature.getColor()
        ));
        card.setPrefSize(250, 150);

        // 图标和名称
        Label icon = new Label(feature.getIcon());
        icon.setStyle("-fx-font-size: 36px;");

        Label name = new Label(feature.getDisplayName());
        name.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Label desc = new Label(feature.getDescription());
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        desc.setWrapText(true);
        desc.setMaxWidth(230);

        card.getChildren().addAll(icon, name, desc);

        // 点击事件
        card.setOnMouseClicked(e -> {
            selectedFeature = feature;
            highlightSelectedCard(card);
            nextButton.setDisable(false);
        });

        // 悬停效果
        card.setOnMouseEntered(e -> {
            if (selectedFeature != feature) {
                card.setStyle(card.getStyle() + "-fx-background-color: #f0f0f0;");
            }
        });

        card.setOnMouseExited(e -> {
            if (selectedFeature != feature) {
                card.setStyle(card.getStyle().replace("-fx-background-color: #f0f0f0;", "-fx-background-color: white;"));
            }
        });

        return card;
    }

    /**
     * 高亮选中的卡片
     */
    private void highlightSelectedCard(VBox selectedCard) {
        // 重置所有卡片
        GridPane grid = (GridPane) ((ScrollPane) step1Panel.getChildren().get(1)).getContent();
        for (javafx.scene.Node node : grid.getChildren()) {
            if (node instanceof VBox card) {
                card.setStyle(card.getStyle().replace("-fx-background-color: #e0f7fa;", "-fx-background-color: white;"));
            }
        }

        // 高亮选中卡片
        selectedCard.setStyle(selectedCard.getStyle().replace("-fx-background-color: white;", "-fx-background-color: #e0f7fa;"));
    }

    // ========== 步骤2：选择模板 ==========

    /**
     * 创建步骤2面板
     */
    private VBox createStep2Panel() {
        VBox panel = new VBox(15);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(20));

        Label title = new Label("选择要克隆的模板");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label hint = new Label("从现有数据中选择一个记录作为模板，系统将自动提取其结构");
        hint.setStyle("-fx-text-fill: gray;");

        // 表格：显示可选的记录
        TableView<Map<String, String>> tableView = new TableView<>();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Map<String, String>, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().get("id")));

        TableColumn<Map<String, String>, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().get("name")));

        tableView.getColumns().addAll(idCol, nameCol);

        // 选择事件
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedRecordId = newVal.get("id");
                nextButton.setDisable(false);
            }
        });

        VBox.setVgrow(tableView, Priority.ALWAYS);

        panel.getChildren().addAll(title, hint, tableView);
        panel.setUserData(tableView);  // 保存引用，用于后续加载数据

        return panel;
    }

    /**
     * 加载步骤2的数据
     */
    private void loadStep2Data() {
        if (selectedFeature == null) {
            return;
        }

        @SuppressWarnings("unchecked")
        TableView<Map<String, String>> tableView = (TableView<Map<String, String>>) step2Panel.getUserData();

        String tableName = selectedFeature.getPrimaryTable();

        try {
            String sql = String.format("SELECT id, name FROM %s ORDER BY id DESC LIMIT 100", tableName);
            List<Map<String, String>> records = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, String> record = new HashMap<>();
                record.put("id", rs.getString("id"));
                record.put("name", rs.getString("name"));
                return record;
            });

            tableView.getItems().setAll(records);

        } catch (Exception e) {
            log.error("加载模板数据失败: 表={}", tableName, e);
            showError("加载数据失败: " + e.getMessage());
        }
    }

    // ========== 步骤3：填写参数 ==========

    /**
     * 创建步骤3面板
     */
    private VBox createStep3Panel() {
        VBox panel = new VBox(15);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setPadding(new Insets(20));

        Label title = new Label("填写参数");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label hint = new Label("请填写以下参数，系统将自动生成新的数据记录");
        hint.setStyle("-fx-text-fill: gray;");

        // 表单容器（动态加载）
        ScrollPane formContainer = new ScrollPane();
        formContainer.setFitToWidth(true);
        VBox.setVgrow(formContainer, Priority.ALWAYS);

        panel.getChildren().addAll(title, hint, formContainer);
        panel.setUserData(formContainer);  // 保存引用

        return panel;
    }

    /**
     * 加载步骤3的表单
     */
    private void loadStep3Form() {
        if (selectedFeature == null || selectedRecordId == null) {
            return;
        }

        try {
            String tableName = selectedFeature.getPrimaryTable();
            String templateName = String.format("%s 克隆模板", selectedFeature.getDisplayName());

            // 提取模板
            log.info("提取模板: 表={}, ID={}", tableName, selectedRecordId);
            var template = templateExtractor.extractFromRecord(tableName, selectedRecordId, templateName);
            extractedTemplateId = template.getId();

            // 加载参数定义
            List<TemplateParam> params = templateParamDao.findByTemplateId(extractedTemplateId);

            // 构建智能表单
            formBuilder = new SmartFormBuilder();
            VBox formPanel = formBuilder.buildForm(tableName, params);

            // 显示表单
            ScrollPane formContainer = (ScrollPane) step3Panel.getUserData();
            formContainer.setContent(formPanel);

            finishButton.setDisable(false);

        } catch (Exception e) {
            log.error("加载表单失败", e);
            showError("加载表单失败: " + e.getMessage());
        }
    }

    // ========== 导航控制 ==========

    /**
     * 更新步骤显示
     */
    private void updateStepDisplay() {
        // 更新指示器
        stepIndicator.setText(String.format("步骤 %d/3", currentStep));

        // 显示对应步骤面板
        contentPane.getChildren().clear();
        switch (currentStep) {
            case 1 -> contentPane.getChildren().add(step1Panel);
            case 2 -> {
                contentPane.getChildren().add(step2Panel);
                loadStep2Data();
            }
            case 3 -> {
                contentPane.getChildren().add(step3Panel);
                loadStep3Form();
            }
        }

        // 更新按钮状态
        prevButton.setDisable(currentStep == 1);
        nextButton.setVisible(currentStep < 3);
        nextButton.setDisable(currentStep == 1 && selectedFeature == null);
        finishButton.setVisible(currentStep == 3);
    }

    /**
     * 上一步
     */
    private void previousStep() {
        if (currentStep > 1) {
            currentStep--;
            updateStepDisplay();
        }
    }

    /**
     * 下一步
     */
    private void nextStep() {
        if (currentStep < 3) {
            currentStep++;
            updateStepDisplay();
        }
    }

    /**
     * 完成
     */
    private void finish() {
        try {
            // 验证表单
            List<String> errors = formBuilder.validateForm();
            if (!errors.isEmpty()) {
                showError("表单验证失败:\n" + String.join("\n", errors));
                return;
            }

            // 获取表单值
            Map<String, String> params = formBuilder.getFormValues();

            // 实例化模板
            log.info("实例化模板: ID={}, 参数数={}", extractedTemplateId, params.size());
            var result = templateInstantiator.instantiate(extractedTemplateId, params);

            if (result.isSuccess()) {
                success = true;
                newRecordId = result.getNewRecordId();

                showInfo(String.format("创建成功！\n\n表: %s\n新记录ID: %s",
                        result.getTableName(), newRecordId));

                close();
            } else {
                showError("创建失败: " + result.getMessage());
            }

        } catch (Exception e) {
            log.error("完成向导失败", e);
            showError("操作失败: " + e.getMessage());
        }
    }

    /**
     * 取消
     */
    private void cancel() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认");
        alert.setHeaderText("确认取消？");
        alert.setContentText("取消后将丢失所有输入，确定要取消吗？");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            success = false;
            close();
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 显示错误提示
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误");
        alert.setHeaderText("操作失败");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示信息提示
     */
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("成功");
        alert.setHeaderText("操作成功");
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ========== Getter 方法 ==========

    /**
     * 是否成功创建
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取新记录ID
     */
    public String getNewRecordId() {
        return newRecordId;
    }

    /**
     * 获取选中的功能
     */
    public GameFeatureCategory getSelectedFeature() {
        return selectedFeature;
    }
}
