package red.jiuzhou.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import red.jiuzhou.validation.XmlFieldValidator;

/**
 * 数据验证结果对话框
 *
 * <p>用于显示XML配置数据的验证错误和警告，帮助设计师快速定位和修复配置问题。
 *
 * <p>基于Aion服务器日志分析，提供准确的错误提示和修复建议。
 *
 * @author Claude
 * @version 1.0
 */
public class ValidationResultDialog extends Stage {

    private final XmlFieldValidator.ValidationResult validationResult;
    private boolean continueImport = false;

    public ValidationResultDialog(Stage owner, XmlFieldValidator.ValidationResult result) {
        this.validationResult = result;

        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setTitle("数据验证结果");
        setResizable(true);
        setWidth(900);
        setHeight(700);

        initUI();
    }

    private void initUI() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");

        // 顶部：摘要信息
        VBox header = createHeader();
        root.setTop(header);

        // 中间：详细错误和警告列表
        TabPane centerPane = createCenterPane();
        root.setCenter(centerPane);

        // 底部：操作按钮
        HBox bottomPane = createBottomPane();
        root.setBottom(bottomPane);

        Scene scene = new Scene(root);
        setScene(scene);
    }

    /**
     * 创建顶部摘要信息
     */
    private VBox createHeader() {
        VBox header = new VBox(15);
        header.setPadding(new Insets(20));
        header.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        // 标题
        Label titleLabel = new Label("📊 数据验证报告");
        titleLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 20));

        // 摘要统计
        HBox summaryBox = new HBox(30);
        summaryBox.setAlignment(Pos.CENTER_LEFT);

        int errorCount = validationResult.getErrors().size();
        int warningCount = validationResult.getWarnings().size();

        // 错误计数
        Label errorLabel = new Label();
        if (errorCount > 0) {
            errorLabel.setText("❌ " + errorCount + " 个错误");
            errorLabel.setTextFill(Color.web("#f44336"));
            errorLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        } else {
            errorLabel.setText("✅ 无错误");
            errorLabel.setTextFill(Color.web("#4caf50"));
            errorLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        }

        // 警告计数
        Label warningLabel = new Label();
        if (warningCount > 0) {
            warningLabel.setText("⚠ " + warningCount + " 个警告");
            warningLabel.setTextFill(Color.web("#ff9800"));
            warningLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        } else {
            warningLabel.setText("✅ 无警告");
            warningLabel.setTextFill(Color.web("#4caf50"));
            warningLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 16));
        }

        summaryBox.getChildren().addAll(errorLabel, warningLabel);

        // 说明文字
        Label descLabel = new Label();
        if (errorCount > 0) {
            descLabel.setText("发现配置错误，必须修正后才能导入。这些错误会导致服务器无法正常加载配置。");
            descLabel.setTextFill(Color.web("#f44336"));
        } else if (warningCount > 0) {
            descLabel.setText("发现配置警告，建议修正以确保最佳兼容性。您可以选择忽略警告继续导入。");
            descLabel.setTextFill(Color.web("#ff9800"));
        } else {
            descLabel.setText("数据验证通过，可以安全导入。");
            descLabel.setTextFill(Color.web("#4caf50"));
        }
        descLabel.setFont(Font.font("Microsoft YaHei", 14));

        header.getChildren().addAll(titleLabel, summaryBox, descLabel);
        return header;
    }

    /**
     * 创建中间内容面板
     */
    private TabPane createCenterPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // 错误标签页
        if (!validationResult.getErrors().isEmpty()) {
            Tab errorTab = new Tab("❌ 错误 (" + validationResult.getErrors().size() + ")");
            errorTab.setContent(createMessageListView(validationResult.getErrors(), true));
            tabPane.getTabs().add(errorTab);
        }

        // 警告标签页
        if (!validationResult.getWarnings().isEmpty()) {
            Tab warningTab = new Tab("⚠ 警告 (" + validationResult.getWarnings().size() + ")");
            warningTab.setContent(createMessageListView(validationResult.getWarnings(), false));
            tabPane.getTabs().add(warningTab);
        }

        // 如果既没有错误也没有警告
        if (validationResult.getErrors().isEmpty() && validationResult.getWarnings().isEmpty()) {
            Tab successTab = new Tab("✅ 验证通过");
            VBox successBox = new VBox(20);
            successBox.setAlignment(Pos.CENTER);
            successBox.setPadding(new Insets(50));

            Label successLabel = new Label("🎉 所有数据验证通过！");
            successLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 18));
            successLabel.setTextFill(Color.web("#4caf50"));

            Label tipLabel = new Label("配置文件符合服务器要求，可以安全导入数据库。");
            tipLabel.setFont(Font.font("Microsoft YaHei", 14));

            successBox.getChildren().addAll(successLabel, tipLabel);
            successTab.setContent(successBox);
            tabPane.getTabs().add(successTab);
        }

        return tabPane;
    }

    /**
     * 创建消息列表视图
     */
    private ListView<String> createMessageListView(java.util.List<String> messages, boolean isError) {
        ListView<String> listView = new ListView<>();
        listView.getItems().addAll(messages);
        listView.setPadding(new Insets(10));
        listView.setStyle("-fx-font-family: 'Microsoft YaHei'; -fx-font-size: 13px;");

        // 自定义单元格渲染
        listView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    if (isError) {
                        setStyle("-fx-text-fill: #d32f2f; -fx-padding: 8px;");
                    } else {
                        setStyle("-fx-text-fill: #f57c00; -fx-padding: 8px;");
                    }
                }
            }
        });

        return listView;
    }

    /**
     * 创建底部按钮面板
     */
    private HBox createBottomPane() {
        HBox bottomPane = new HBox(15);
        bottomPane.setPadding(new Insets(15));
        bottomPane.setAlignment(Pos.CENTER_RIGHT);
        bottomPane.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");

        // 根据错误情况显示不同按钮
        if (validationResult.hasErrors()) {
            // 有错误：只能关闭
            Button closeButton = new Button("关闭并修正错误");
            closeButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8 20;");
            closeButton.setOnAction(e -> {
                continueImport = false;
                close();
            });

            Label tipLabel = new Label("💡 提示：请修正所有错误后重新导入");
            tipLabel.setFont(Font.font("Microsoft YaHei", 12));
            tipLabel.setTextFill(Color.web("#666"));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            bottomPane.getChildren().addAll(tipLabel, spacer, closeButton);
        } else if (validationResult.hasWarnings()) {
            // 只有警告：可以选择继续或取消
            Button cancelButton = new Button("取消导入");
            cancelButton.setStyle("-fx-background-color: #757575; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8 20;");
            cancelButton.setOnAction(e -> {
                continueImport = false;
                close();
            });

            Button continueButton = new Button("忽略警告并继续");
            continueButton.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8 20;");
            continueButton.setOnAction(e -> {
                continueImport = true;
                close();
            });

            Label tipLabel = new Label("💡 提示：警告不会阻止导入，但建议修正以确保兼容性");
            tipLabel.setFont(Font.font("Microsoft YaHei", 12));
            tipLabel.setTextFill(Color.web("#666"));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            bottomPane.getChildren().addAll(tipLabel, spacer, cancelButton, continueButton);
        } else {
            // 无错误无警告：可以继续
            Button okButton = new Button("确定并继续导入");
            okButton.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8 20;");
            okButton.setOnAction(e -> {
                continueImport = true;
                close();
            });

            bottomPane.getChildren().add(okButton);
        }

        return bottomPane;
    }

    /**
     * 显示对话框并返回用户选择
     *
     * @return true表示继续导入，false表示取消
     */
    public boolean showAndWaitForDecision() {
        showAndWait();
        return continueImport;
    }
}
