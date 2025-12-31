package red.jiuzhou.agent.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.agent.templates.AiTemplateLibrary;
import red.jiuzhou.agent.templates.AiTemplateLibrary.AiTemplate;
import red.jiuzhou.agent.templates.AiTemplateLibrary.TemplateCategory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * AI SQL 模板面板
 *
 * 提供预设模板选择和自然语言 SQL 生成功能
 * 可嵌入到右侧面板或作为独立弹窗使用
 *
 * @author Claude
 * @version 1.0
 */
public class AiSqlTemplatePanel extends VBox {

    private static final Logger log = LoggerFactory.getLogger(AiSqlTemplatePanel.class);

    // 模板库
    private final AiTemplateLibrary templateLibrary = AiTemplateLibrary.getInstance();

    // UI 组件
    private ComboBox<TemplateCategory> categoryCombo;
    private ListView<AiTemplate> templateList;
    private TextArea promptInput;
    private TextArea sqlPreview;
    private VBox parameterBox;
    private Button generateButton;
    private Button executeButton;
    private Button saveButton;

    // 当前状态
    private String currentTableName;
    private AiTemplate selectedTemplate;
    private Map<String, TextField> parameterFields = new HashMap<>();

    // 回调
    private BiConsumer<String, String> onExecuteSql;  // (sql, description) -> void
    private Consumer<String> onGenerateRequest;       // (naturalLanguagePrompt) -> void

    // 异步执行器
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SqlTemplatePanel-Worker");
        t.setDaemon(true);
        return t;
    });

    public AiSqlTemplatePanel() {
        initializeUI();
    }

    private void initializeUI() {
        setSpacing(10);
        setPadding(new Insets(12));
        setStyle("-fx-background-color: #ffffff; -fx-border-color: #e0e0e0; -fx-border-radius: 8; -fx-background-radius: 8;");

        // ==================== 标题区 ====================
        HBox titleBox = new HBox(8);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("🤖 SQL 智能助手");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        titleLabel.setTextFill(Color.web("#333333"));

        Label subtitleLabel = new Label("选择模板或输入自然语言");
        subtitleLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleBox.getChildren().addAll(titleLabel, subtitleLabel, spacer);

        // ==================== 分类选择 ====================
        HBox categoryBox = new HBox(8);
        categoryBox.setAlignment(Pos.CENTER_LEFT);

        Label categoryLabel = new Label("分类:");
        categoryLabel.setStyle("-fx-text-fill: #555555; -fx-font-size: 11px;");

        categoryCombo = new ComboBox<>();
        categoryCombo.getItems().add(null); // 全部
        categoryCombo.getItems().addAll(TemplateCategory.values());
        categoryCombo.setPromptText("全部模板");
        categoryCombo.setCellFactory(lv -> new CategoryCell());
        categoryCombo.setButtonCell(new CategoryCell());
        categoryCombo.setOnAction(e -> refreshTemplateList());
        categoryCombo.setPrefWidth(120);

        categoryBox.getChildren().addAll(categoryLabel, categoryCombo);

        // ==================== 模板列表 ====================
        templateList = new ListView<>();
        templateList.setPrefHeight(150);
        templateList.setCellFactory(lv -> new TemplateCell());
        templateList.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            onTemplateSelected(newVal);
        });

        // ==================== 参数输入区 ====================
        TitledPane paramPane = new TitledPane();
        paramPane.setText("📝 参数设置");
        paramPane.setCollapsible(true);
        paramPane.setExpanded(false);

        parameterBox = new VBox(6);
        parameterBox.setPadding(new Insets(8));
        paramPane.setContent(parameterBox);

        // ==================== 自然语言输入区 ====================
        TitledPane nlpPane = new TitledPane();
        nlpPane.setText("✨ 自然语言描述");
        nlpPane.setCollapsible(true);
        nlpPane.setExpanded(true);

        VBox nlpContent = new VBox(8);
        nlpContent.setPadding(new Insets(8));

        promptInput = new TextArea();
        promptInput.setPromptText("用自然语言描述你想查询的数据...\n例如: 查找所有50级以上的火属性技能");
        promptInput.setPrefRowCount(3);
        promptInput.setWrapText(true);
        promptInput.setStyle("-fx-font-size: 12px;");

        generateButton = createGradientButton("🤖 生成 SQL", "#667eea", "#764ba2");
        generateButton.setOnAction(e -> handleGenerateSql());
        generateButton.setMaxWidth(Double.MAX_VALUE);

        nlpContent.getChildren().addAll(promptInput, generateButton);
        nlpPane.setContent(nlpContent);

        // ==================== SQL 预览区 ====================
        TitledPane sqlPane = new TitledPane();
        sqlPane.setText("📋 SQL 预览");
        sqlPane.setCollapsible(true);
        sqlPane.setExpanded(true);

        VBox sqlContent = new VBox(8);
        sqlContent.setPadding(new Insets(8));

        sqlPreview = new TextArea();
        sqlPreview.setPromptText("生成的 SQL 将显示在这里...");
        sqlPreview.setPrefRowCount(4);
        sqlPreview.setWrapText(true);
        sqlPreview.setStyle("-fx-font-family: 'Consolas', 'Monaco', monospace; -fx-font-size: 11px;");
        sqlPreview.setEditable(true);

        HBox buttonRow = new HBox(8);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        executeButton = createGradientButton("▶ 执行", "#28a745", "#20c997");
        executeButton.setOnAction(e -> handleExecuteSql());
        executeButton.setDisable(true);

        Button copyButton = new Button("📋 复制");
        copyButton.setOnAction(e -> copyToClipboard());

        saveButton = new Button("💾 保存模板");
        saveButton.setOnAction(e -> handleSaveTemplate());
        saveButton.setDisable(true);

        buttonRow.getChildren().addAll(saveButton, copyButton, executeButton);

        sqlContent.getChildren().addAll(sqlPreview, buttonRow);
        sqlPane.setContent(sqlContent);

        // ==================== 组装 ====================
        getChildren().addAll(
            titleBox,
            new Separator(),
            categoryBox,
            templateList,
            paramPane,
            nlpPane,
            sqlPane
        );

        // 初始加载模板
        refreshTemplateList();
    }

    /**
     * 创建渐变按钮
     */
    private Button createGradientButton(String text, String color1, String color2) {
        Button btn = new Button(text);
        String baseStyle = String.format(
            "-fx-background-color: linear-gradient(to bottom, %s, %s);" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 12px;" +
            "-fx-font-weight: bold;" +
            "-fx-padding: 8 16;" +
            "-fx-background-radius: 4;" +
            "-fx-cursor: hand;",
            color1, color2
        );
        btn.setStyle(baseStyle);

        btn.setOnMouseEntered(e -> btn.setStyle(baseStyle +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 6, 0, 0, 2);"));
        btn.setOnMouseExited(e -> btn.setStyle(baseStyle));

        return btn;
    }

    /**
     * 刷新模板列表
     */
    private void refreshTemplateList() {
        TemplateCategory category = categoryCombo.getValue();

        List<AiTemplate> templates;
        if (category == null) {
            templates = new ArrayList<>(templateLibrary.getAllPresetTemplates());
            templates.addAll(templateLibrary.getAllUserTemplates());
        } else {
            templates = templateLibrary.getTemplatesByCategory(category);
        }

        // 按优先级排序
        templates.sort((a, b) -> b.priority() - a.priority());

        templateList.getItems().clear();
        templateList.getItems().addAll(templates);
    }

    /**
     * 模板选中事件
     */
    private void onTemplateSelected(AiTemplate template) {
        this.selectedTemplate = template;

        if (template == null) {
            parameterBox.getChildren().clear();
            promptInput.clear();
            return;
        }

        // 更新 Prompt 输入
        promptInput.setText(template.promptTemplate());

        // 构建参数输入区
        parameterBox.getChildren().clear();
        parameterFields.clear();

        if (template.params() != null && !template.params().isEmpty()) {
            for (var param : template.params()) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);

                Label label = new Label(param.label() + ":");
                label.setMinWidth(70);
                label.setStyle("-fx-text-fill: #555555; -fx-font-size: 11px;");

                if (param.options() != null && !param.options().isEmpty()) {
                    // 下拉选择
                    ComboBox<String> combo = new ComboBox<>();
                    combo.getItems().addAll(param.options());
                    combo.setValue(param.defaultValue());
                    combo.setPrefWidth(150);

                    // 用一个隐藏的 TextField 存值
                    TextField hidden = new TextField();
                    hidden.setVisible(false);
                    hidden.textProperty().bind(combo.valueProperty());
                    parameterFields.put(param.name(), hidden);

                    row.getChildren().addAll(label, combo);
                } else {
                    // 文本输入
                    TextField field = new TextField(param.defaultValue());
                    field.setPromptText(param.label());
                    field.setPrefWidth(150);
                    parameterFields.put(param.name(), field);

                    row.getChildren().addAll(label, field);
                }

                parameterBox.getChildren().add(row);
            }
        }

        // 自动渲染 SQL（如果有 SQL 模板）
        if (template.sqlTemplate() != null) {
            updateSqlPreview();
        }
    }

    /**
     * 更新 SQL 预览
     */
    private void updateSqlPreview() {
        if (selectedTemplate == null || selectedTemplate.sqlTemplate() == null) return;

        Map<String, String> values = new HashMap<>();

        // 如果有当前表名，默认填入
        if (currentTableName != null && !currentTableName.isEmpty()) {
            values.put("table", currentTableName);
        }

        // 收集参数值
        for (var entry : parameterFields.entrySet()) {
            String value = entry.getValue().getText();
            if (value != null && !value.isEmpty()) {
                values.put(entry.getKey(), value);
            }
        }

        String sql = selectedTemplate.renderSql(values);
        sqlPreview.setText(sql);
        executeButton.setDisable(false);
    }

    /**
     * 处理生成 SQL
     */
    private void handleGenerateSql() {
        String prompt = promptInput.getText();
        if (prompt == null || prompt.trim().isEmpty()) {
            showAlert("提示", "请输入自然语言描述");
            return;
        }

        // 记录模板使用
        if (selectedTemplate != null) {
            templateLibrary.recordUsage(selectedTemplate.id());
        }

        // 构建完整的 Prompt
        String fullPrompt = prompt;
        if (currentTableName != null && !currentTableName.isEmpty()) {
            fullPrompt = "针对表 " + currentTableName + "：" + prompt;
        }

        // 替换参数
        for (var entry : parameterFields.entrySet()) {
            fullPrompt = fullPrompt.replace("{" + entry.getKey() + "}", entry.getValue().getText());
        }

        // 显示加载状态
        generateButton.setDisable(true);
        generateButton.setText("⏳ 生成中...");

        if (onGenerateRequest != null) {
            final String finalPrompt = fullPrompt;
            executor.submit(() -> {
                try {
                    onGenerateRequest.accept(finalPrompt);
                } finally {
                    Platform.runLater(() -> {
                        generateButton.setDisable(false);
                        generateButton.setText("🤖 生成 SQL");
                    });
                }
            });
        } else {
            // 没有配置生成回调，尝试从模板渲染
            if (selectedTemplate != null && selectedTemplate.sqlTemplate() != null) {
                updateSqlPreview();
            } else {
                showAlert("提示", "AI 生成功能尚未配置，请使用预设模板");
            }
            generateButton.setDisable(false);
            generateButton.setText("🤖 生成 SQL");
        }
    }

    /**
     * 处理执行 SQL
     */
    private void handleExecuteSql() {
        String sql = sqlPreview.getText();
        if (sql == null || sql.trim().isEmpty()) {
            showAlert("提示", "请先生成 SQL");
            return;
        }

        if (onExecuteSql != null) {
            String description = selectedTemplate != null ? selectedTemplate.name() : "自定义查询";
            onExecuteSql.accept(sql.trim(), description);
        } else {
            showAlert("提示", "SQL 执行功能尚未配置");
        }
    }

    /**
     * 处理保存模板
     */
    private void handleSaveTemplate() {
        String sql = sqlPreview.getText();
        String prompt = promptInput.getText();

        if ((sql == null || sql.isEmpty()) && (prompt == null || prompt.isEmpty())) {
            showAlert("提示", "请先生成内容再保存");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("我的模板");
        dialog.setTitle("保存模板");
        dialog.setHeaderText("保存为用户模板");
        dialog.setContentText("模板名称:");

        dialog.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) {
                showAlert("错误", "模板名称不能为空");
                return;
            }

            String id = "user_" + System.currentTimeMillis();
            AiTemplate userTemplate = new AiTemplate(
                id,
                name.trim(),
                "用户自定义模板",
                TemplateCategory.QUERY,
                prompt,
                sql,
                List.of(),
                List.of("*"),
                50
            );

            templateLibrary.saveUserTemplate(userTemplate);
            refreshTemplateList();
            showAlert("成功", "模板已保存: " + name);
        });
    }

    /**
     * 复制到剪贴板
     */
    private void copyToClipboard() {
        String sql = sqlPreview.getText();
        if (sql != null && !sql.isEmpty()) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(sql);
            clipboard.setContent(content);
            showToast("已复制到剪贴板");
        }
    }

    /**
     * 显示提示框
     */
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 显示轻提示
     */
    private void showToast(String message) {
        // 简单实现，可以后续增强为悬浮提示
        log.info(message);
    }

    // ==================== 公共方法 ====================

    /**
     * 设置当前表名
     */
    public void setCurrentTable(String tableName) {
        this.currentTableName = tableName;

        // 更新参数中的表名默认值
        TextField tableField = parameterFields.get("table");
        if (tableField != null) {
            tableField.setText(tableName);
        }

        // 更新 SQL 预览
        if (selectedTemplate != null) {
            updateSqlPreview();
        }
    }

    /**
     * 设置 SQL 预览内容（供外部调用）
     */
    public void setSqlPreview(String sql) {
        Platform.runLater(() -> {
            sqlPreview.setText(sql);
            executeButton.setDisable(sql == null || sql.isEmpty());
            saveButton.setDisable(sql == null || sql.isEmpty());
        });
    }

    /**
     * 设置 SQL 执行回调
     */
    public void setOnExecuteSql(BiConsumer<String, String> handler) {
        this.onExecuteSql = handler;
    }

    /**
     * 设置 AI 生成请求回调
     */
    public void setOnGenerateRequest(Consumer<String> handler) {
        this.onGenerateRequest = handler;
    }

    /**
     * 清理资源
     */
    public void dispose() {
        executor.shutdownNow();
    }

    // ==================== 自定义单元格 ====================

    /**
     * 分类下拉单元格
     */
    private static class CategoryCell extends ListCell<TemplateCategory> {
        @Override
        protected void updateItem(TemplateCategory item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText("全部模板");
            } else {
                setText(item.getName());
            }
        }
    }

    /**
     * 模板列表单元格
     */
    private class TemplateCell extends ListCell<AiTemplate> {
        @Override
        protected void updateItem(AiTemplate item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            VBox box = new VBox(2);
            box.setPadding(new Insets(4, 8, 4, 8));

            HBox titleRow = new HBox(6);
            titleRow.setAlignment(Pos.CENTER_LEFT);

            Label icon = new Label(getCategoryIcon(item.category()));
            Label name = new Label(item.name());
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

            titleRow.getChildren().addAll(icon, name);

            Label desc = new Label(item.description());
            desc.setStyle("-fx-text-fill: #666666; -fx-font-size: 10px;");
            desc.setWrapText(true);

            box.getChildren().addAll(titleRow, desc);
            setGraphic(box);
        }

        private String getCategoryIcon(TemplateCategory category) {
            return switch (category) {
                case QUERY -> "🔍";
                case ANALYSIS -> "📊";
                case VALIDATION -> "✅";
                case GENERATION -> "✨";
                case MODIFICATION -> "✏️";
            };
        }
    }
}
