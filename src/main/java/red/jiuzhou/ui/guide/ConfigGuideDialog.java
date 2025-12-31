package red.jiuzhou.ui.guide;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.config.validation.ConfigValidationService;
import red.jiuzhou.config.validation.ConfigValidationService.*;
import red.jiuzhou.ui.ConfigEditorStage;
import red.jiuzhou.ui.error.structured.ErrorLevel;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配置引导对话框 - 启动时检测配置并引导用户完成配置
 *
 * @author Claude
 * @version 1.0
 */
public class ConfigGuideDialog extends Stage {

    private static final Logger log = LoggerFactory.getLogger(ConfigGuideDialog.class);

    private final ConfigValidationService validationService;
    private final List<ConfigStatus> configStatus;

    private boolean userChoseToEdit = false;
    private boolean userSkipped = false;

    public ConfigGuideDialog(ConfigValidationService validationService) {
        this.validationService = validationService;
        this.configStatus = validationService.getAllConfigStatus();

        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.DECORATED);
        setTitle("配置向导");
        setWidth(700);
        setHeight(550);
        setResizable(false);

        initUI();

        log.info("配置引导对话框已创建，显示 {} 个配置项", configStatus.size());
    }

    private void initUI() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");

        // 顶部 - 标题和说明
        VBox header = createHeader();
        root.setTop(header);

        // 中央 - 配置项列表
        ScrollPane configList = createConfigList();
        root.setCenter(configList);

        // 底部 - 操作按钮
        HBox buttonBar = createButtonBar();
        root.setBottom(buttonBar);

        Scene scene = new Scene(root);
        setScene(scene);
    }

    /**
     * 创建标题区域
     */
    private VBox createHeader() {
        VBox header = new VBox(8);
        header.setPadding(new Insets(20, 25, 15, 25));
        header.setStyle("-fx-background-color: #1976d2;");

        Label title = new Label("⚙️ 配置向导");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));
        title.setTextFill(Color.WHITE);

        // 统计信息
        long errorCount = configStatus.stream()
            .filter(s -> !s.isValid() && s.requirement().required())
            .count();
        long warningCount = configStatus.stream()
            .filter(s -> !s.isValid() && !s.requirement().required())
            .count();

        String statusText;
        if (errorCount > 0) {
            statusText = String.format("发现 %d 个必填项未配置，%d 个可选项未配置",
                errorCount, warningCount);
        } else if (warningCount > 0) {
            statusText = String.format("所有必填项已配置，%d 个可选项未配置", warningCount);
        } else {
            statusText = "所有配置项已正确配置";
        }

        Label subtitle = new Label(statusText);
        subtitle.setFont(Font.font("System", 13));
        subtitle.setTextFill(Color.web("#bbdefb"));

        header.getChildren().addAll(title, subtitle);
        return header;
    }

    /**
     * 创建配置项列表
     */
    private ScrollPane createConfigList() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(15, 20, 15, 20));

        // 按分类分组显示
        Map<ConfigCategory, List<ConfigStatus>> grouped = configStatus.stream()
            .collect(Collectors.groupingBy(s -> s.requirement().category()));

        for (ConfigCategory category : ConfigCategory.values()) {
            List<ConfigStatus> items = grouped.get(category);
            if (items == null || items.isEmpty()) continue;

            // 分类标题
            HBox categoryHeader = new HBox(8);
            categoryHeader.setAlignment(Pos.CENTER_LEFT);

            Label categoryIcon = new Label(category.getIcon());
            categoryIcon.setFont(Font.font(16));

            Label categoryLabel = new Label(category.getDisplayName());
            categoryLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
            categoryLabel.setTextFill(Color.web("#424242"));

            // 分类状态
            long categoryErrors = items.stream().filter(s -> !s.isValid()).count();
            if (categoryErrors > 0) {
                Label badge = new Label(String.valueOf(categoryErrors));
                badge.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; " +
                    "-fx-padding: 2 6; -fx-background-radius: 10;");
                badge.setFont(Font.font(11));
                categoryHeader.getChildren().addAll(categoryIcon, categoryLabel, badge);
            } else {
                Label okBadge = new Label("✓");
                okBadge.setStyle("-fx-text-fill: #4caf50; -fx-font-weight: bold;");
                categoryHeader.getChildren().addAll(categoryIcon, categoryLabel, okBadge);
            }

            container.getChildren().add(categoryHeader);

            // 配置项卡片
            for (ConfigStatus status : items) {
                Node card = createConfigCard(status);
                container.getChildren().add(card);
            }

            // 分隔线
            Separator sep = new Separator();
            sep.setPadding(new Insets(5, 0, 5, 0));
            container.getChildren().add(sep);
        }

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        return scrollPane;
    }

    /**
     * 创建单个配置项卡片
     */
    private Node createConfigCard(ConfigStatus status) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(12));
        card.setAlignment(Pos.CENTER_LEFT);

        // 根据状态设置背景色
        String bgColor = status.isValid() ? "#ffffff" : "#fff3e0";
        if (!status.isValid() && status.requirement().required()) {
            bgColor = "#ffebee";
        }
        card.setStyle("-fx-background-color: " + bgColor + "; " +
            "-fx-background-radius: 8; " +
            "-fx-border-color: #e0e0e0; -fx-border-radius: 8;");

        // 状态图标
        Label statusIcon = new Label(status.getStatusIcon());
        statusIcon.setFont(Font.font(18));
        statusIcon.setMinWidth(25);

        // 配置信息
        VBox infoBox = new VBox(3);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        // 名称行
        HBox nameRow = new HBox(8);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(status.requirement().displayName());
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 13));

        if (status.requirement().required()) {
            Label requiredBadge = new Label("必填");
            requiredBadge.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; " +
                "-fx-padding: 1 4; -fx-background-radius: 3; -fx-font-size: 10;");
            nameRow.getChildren().addAll(nameLabel, requiredBadge);
        } else {
            nameRow.getChildren().add(nameLabel);
        }

        // 配置键
        Label keyLabel = new Label(status.requirement().key());
        keyLabel.setStyle("-fx-text-fill: #757575; -fx-font-size: 11;");
        keyLabel.setFont(Font.font("Consolas", 11));

        // 描述
        Label descLabel = new Label(status.requirement().description());
        descLabel.setStyle("-fx-text-fill: #616161; -fx-font-size: 12;");
        descLabel.setWrapText(true);

        infoBox.getChildren().addAll(nameRow, keyLabel, descLabel);

        // 验证消息
        if (!status.isValid()) {
            Label msgLabel = new Label("💡 " + status.validationResult().message());
            String msgColor = status.validationResult().level() == ErrorLevel.ERROR
                ? "#c62828" : "#ef6c00";
            msgLabel.setStyle("-fx-text-fill: " + msgColor + "; -fx-font-size: 11;");
            msgLabel.setWrapText(true);
            infoBox.getChildren().add(msgLabel);
        }

        // 当前值显示
        if (status.currentValue() != null && !status.currentValue().isBlank()) {
            Label valueLabel = new Label("当前值: " + status.currentValue());
            valueLabel.setStyle("-fx-text-fill: #9e9e9e; -fx-font-size: 10;");
            infoBox.getChildren().add(valueLabel);
        }

        // 操作按钮
        VBox actionBox = new VBox(5);
        actionBox.setAlignment(Pos.CENTER);

        if (!status.isValid()) {
            Button editBtn = new Button("编辑");
            editBtn.setStyle("-fx-background-color: #1976d2; -fx-text-fill: white; " +
                "-fx-cursor: hand;");
            editBtn.setOnAction(e -> openConfigEditor(status.requirement().key()));
            actionBox.getChildren().add(editBtn);

            if (status.requirement().defaultValue() != null) {
                Hyperlink defaultLink = new Hyperlink("使用默认值");
                defaultLink.setStyle("-fx-font-size: 11;");
                defaultLink.setOnAction(e -> {
                    // TODO: 实现自动填充默认值
                    showInfo("提示", "请在配置编辑器中设置为: " +
                        status.requirement().defaultValue());
                });
                actionBox.getChildren().add(defaultLink);
            }
        }

        card.getChildren().addAll(statusIcon, infoBox, actionBox);
        return card;
    }

    /**
     * 创建底部按钮栏
     */
    private HBox createButtonBar() {
        HBox buttonBar = new HBox(15);
        buttonBar.setPadding(new Insets(15, 25, 20, 25));
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setStyle("-fx-background-color: #eeeeee; " +
            "-fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");

        // 左侧提示
        Label hint = new Label("💡 完成配置后重启应用以生效");
        hint.setStyle("-fx-text-fill: #757575; -fx-font-size: 12;");
        HBox.setHgrow(hint, Priority.ALWAYS);

        // 跳过按钮 (仅当没有必填错误时可用)
        Button skipBtn = new Button("稍后配置");
        skipBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #757575;");
        skipBtn.setOnAction(e -> {
            userSkipped = true;
            close();
        });

        // 如果有必填项未配置，禁用跳过按钮
        boolean hasMandatoryErrors = configStatus.stream()
            .anyMatch(s -> !s.isValid() && s.requirement().required());
        skipBtn.setDisable(hasMandatoryErrors);
        if (hasMandatoryErrors) {
            skipBtn.setTooltip(new Tooltip("请先完成必填配置项"));
        }

        // 打开配置编辑器按钮
        Button editBtn = new Button("打开配置编辑器");
        editBtn.setStyle("-fx-background-color: #1976d2; -fx-text-fill: white; " +
            "-fx-font-weight: bold; -fx-padding: 8 20; -fx-cursor: hand;");
        editBtn.setOnAction(e -> {
            userChoseToEdit = true;
            openConfigEditor(null);
            close();
        });

        buttonBar.getChildren().addAll(hint, skipBtn, editBtn);
        return buttonBar;
    }

    /**
     * 打开配置编辑器
     */
    private void openConfigEditor(String configKey) {
        Platform.runLater(() -> {
            try {
                ConfigEditorStage editor = new ConfigEditorStage();
                editor.show();

                if (configKey != null) {
                    // 延迟导航，等待编辑器加载完成
                    Platform.runLater(() -> {
                        try {
                            Thread.sleep(500);
                            editor.navigateToKey(configKey);
                        } catch (Exception ex) {
                            log.debug("导航到配置键失败: {}", configKey);
                        }
                    });
                }
            } catch (Exception e) {
                log.error("打开配置编辑器失败", e);
                showError("错误", "无法打开配置编辑器: " + e.getMessage());
            }
        });
    }

    /**
     * 显示信息对话框
     */
    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * 显示错误对话框
     */
    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ==================== 公共方法 ====================

    /**
     * 用户是否选择了编辑配置
     */
    public boolean isUserChoseToEdit() {
        return userChoseToEdit;
    }

    /**
     * 用户是否选择了跳过
     */
    public boolean isUserSkipped() {
        return userSkipped;
    }
}
