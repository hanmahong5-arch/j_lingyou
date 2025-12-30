package red.jiuzhou.ui.components;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.validation.DatabaseValidationService;
import red.jiuzhou.validation.DatabaseValidationService.Severity;
import red.jiuzhou.validation.DatabaseValidationService.ValidationIssue;

import java.util.List;
import java.util.function.Consumer;

/**
 * 数据校验面板
 *
 * 设计理念：
 * - 一键操作：点击按钮即可校验当前表
 * - 即时反馈：问题列表直接展示，无需切换页面
 * - 可操作：点击问题可跳转到对应记录
 *
 * 使用方式：
 * 1. 嵌入到表格视图旁边
 * 2. 自动感知当前选中的表
 * 3. 点击"校验"按钮执行检查
 */
public class DataValidationPanel extends VBox {

    private final DatabaseValidationService validationService;

    private String currentTable;
    private Consumer<Object> onRecordSelected;

    // UI组件
    private Label tableLabel;
    private Button validateBtn;
    private Label summaryLabel;
    private ListView<ValidationIssue> issueList;
    private VBox detailPane;

    public DataValidationPanel(JdbcTemplate jdbcTemplate) {
        this.validationService = new DatabaseValidationService(jdbcTemplate);
        initUI();
    }

    private void initUI() {
        setSpacing(10);
        setPadding(new Insets(10));
        setMinWidth(280);
        setMaxWidth(350);
        setStyle("-fx-background-color: #fafafa; -fx-border-color: #e0e0e0; -fx-border-radius: 5;");

        // 标题栏
        HBox header = createHeader();

        // 摘要
        summaryLabel = new Label("点击「校验」按钮开始检查");
        summaryLabel.setStyle("-fx-text-fill: #666;");

        // 问题列表
        issueList = new ListView<>();
        issueList.setCellFactory(lv -> new IssueCellFactory());
        issueList.setPrefHeight(200);
        issueList.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                showIssueDetail(selected);
            }
        });

        // 详情面板
        detailPane = new VBox(8);
        detailPane.setPadding(new Insets(10));
        detailPane.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5;");
        detailPane.setVisible(false);
        detailPane.setManaged(false);

        getChildren().addAll(header, summaryLabel, issueList, detailPane);
    }

    private HBox createHeader() {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("数据校验");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        tableLabel = new Label("");
        tableLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-size: 12;");

        validateBtn = new Button("校验");
        validateBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        validateBtn.setDisable(true);
        validateBtn.setOnAction(e -> runValidation());

        header.getChildren().addAll(title, spacer, tableLabel, validateBtn);
        return header;
    }

    /**
     * 设置当前表
     */
    public void setCurrentTable(String tableName) {
        this.currentTable = tableName;
        if (tableName != null && !tableName.isEmpty()) {
            tableLabel.setText(tableName);
            validateBtn.setDisable(false);
            summaryLabel.setText("点击「校验」检查 " + tableName);
        } else {
            tableLabel.setText("");
            validateBtn.setDisable(true);
            summaryLabel.setText("请先选择一个表");
        }

        // 清空之前的结果
        issueList.getItems().clear();
        detailPane.setVisible(false);
        detailPane.setManaged(false);
    }

    /**
     * 设置记录选中回调
     */
    public void setOnRecordSelected(Consumer<Object> callback) {
        this.onRecordSelected = callback;
    }

    /**
     * 执行校验
     */
    private void runValidation() {
        if (currentTable == null || currentTable.isEmpty()) {
            return;
        }

        validateBtn.setDisable(true);
        validateBtn.setText("校验中...");
        summaryLabel.setText("正在检查...");

        // 异步执行校验
        new Thread(() -> {
            try {
                List<ValidationIssue> issues = validationService.validateTable(currentTable);

                Platform.runLater(() -> {
                    displayResults(issues);
                    validateBtn.setDisable(false);
                    validateBtn.setText("校验");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    summaryLabel.setText("校验失败: " + e.getMessage());
                    validateBtn.setDisable(false);
                    validateBtn.setText("校验");
                });
            }
        }).start();
    }

    /**
     * 显示校验结果
     */
    private void displayResults(List<ValidationIssue> issues) {
        ObservableList<ValidationIssue> items = FXCollections.observableArrayList(issues);
        issueList.setItems(items);

        // 统计
        long errors = issues.stream().filter(i -> i.getSeverity() == Severity.ERROR).count();
        long warnings = issues.stream().filter(i -> i.getSeverity() == Severity.WARNING).count();
        long infos = issues.stream().filter(i -> i.getSeverity() == Severity.INFO).count();

        if (issues.isEmpty()) {
            summaryLabel.setText("✅ 未发现问题");
            summaryLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
        } else {
            StringBuilder sb = new StringBuilder();
            if (errors > 0) sb.append("❌ ").append(errors).append("个错误  ");
            if (warnings > 0) sb.append("⚠️ ").append(warnings).append("个警告  ");
            if (infos > 0) sb.append("ℹ️ ").append(infos).append("个提示");
            summaryLabel.setText(sb.toString().trim());
            summaryLabel.setStyle(errors > 0 ? "-fx-text-fill: #d32f2f;" : "-fx-text-fill: #f57c00;");
        }
    }

    /**
     * 显示问题详情
     */
    private void showIssueDetail(ValidationIssue issue) {
        detailPane.getChildren().clear();

        // 标题
        Label titleLabel = new Label(issue.getType());
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
        titleLabel.setTextFill(Color.web(issue.getSeverityColor()));

        // 消息
        Label messageLabel = new Label(issue.getMessage());
        messageLabel.setWrapText(true);

        detailPane.getChildren().addAll(titleLabel, messageLabel);

        // 记录ID
        if (issue.getRecordId() != null) {
            HBox idRow = new HBox(5);
            idRow.setAlignment(Pos.CENTER_LEFT);

            Label idLabel = new Label("记录ID: " + issue.getRecordId());
            idLabel.setStyle("-fx-text-fill: #666;");

            Button gotoBtn = new Button("跳转");
            gotoBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-size: 11;");
            gotoBtn.setOnAction(e -> {
                if (onRecordSelected != null) {
                    onRecordSelected.accept(issue.getRecordId());
                }
            });

            idRow.getChildren().addAll(idLabel, gotoBtn);
            detailPane.getChildren().add(idRow);
        }

        // 字段信息
        if (issue.getFieldName() != null) {
            Label fieldLabel = new Label("字段: " + issue.getFieldName());
            fieldLabel.setStyle("-fx-text-fill: #666;");
            detailPane.getChildren().add(fieldLabel);
        }

        // 当前值
        if (issue.getCurrentValue() != null) {
            Label valueLabel = new Label("当前值: " + issue.getCurrentValue());
            valueLabel.setStyle("-fx-text-fill: #666;");
            detailPane.getChildren().add(valueLabel);
        }

        // 建议
        if (issue.getSuggestion() != null) {
            Label suggestLabel = new Label("💡 " + issue.getSuggestion());
            suggestLabel.setWrapText(true);
            suggestLabel.setStyle("-fx-text-fill: #1976d2; -fx-font-style: italic;");
            detailPane.getChildren().add(suggestLabel);
        }

        detailPane.setVisible(true);
        detailPane.setManaged(true);
    }

    /**
     * 问题列表单元格工厂
     */
    private static class IssueCellFactory extends ListCell<ValidationIssue> {
        @Override
        protected void updateItem(ValidationIssue issue, boolean empty) {
            super.updateItem(issue, empty);

            if (empty || issue == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox cell = new HBox(8);
                cell.setAlignment(Pos.CENTER_LEFT);

                // 图标
                Label icon = new Label(issue.getSeverityIcon());

                // 类型
                Label type = new Label(issue.getType());
                type.setStyle("-fx-font-weight: bold;");
                type.setTextFill(Color.web(issue.getSeverityColor()));

                // 简短描述
                String shortMsg = issue.getMessage();
                if (shortMsg.length() > 40) {
                    shortMsg = shortMsg.substring(0, 37) + "...";
                }
                Label msg = new Label(shortMsg);
                msg.setStyle("-fx-text-fill: #666;");

                cell.getChildren().addAll(icon, type, msg);
                setGraphic(cell);
            }
        }
    }

    /**
     * 创建可折叠的校验面板
     */
    public static TitledPane createCollapsible(JdbcTemplate jdbcTemplate) {
        DataValidationPanel panel = new DataValidationPanel(jdbcTemplate);

        TitledPane titledPane = new TitledPane("数据校验", panel);
        titledPane.setCollapsible(true);
        titledPane.setExpanded(false);

        return titledPane;
    }
}
