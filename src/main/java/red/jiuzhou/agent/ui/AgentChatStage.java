package red.jiuzhou.agent.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.agent.core.AgentMessage;
import red.jiuzhou.agent.core.GameDataAgent;
import red.jiuzhou.util.DatabaseUtil;

import java.time.format.DateTimeFormatter;

/**
 * AI Agent 对话窗口
 *
 * 提供聊天式交互界面
 *
 * @author yanxq
 * @date 2025-01-13
 */
public class AgentChatStage extends Stage {

    private static final Logger log = LoggerFactory.getLogger(AgentChatStage.class);

    private GameDataAgent agent;
    private JdbcTemplate jdbcTemplate;

    // UI组件
    private VBox chatContainer;
    private ScrollPane chatScrollPane;
    private TextArea inputArea;
    private Button sendButton;
    private ComboBox<String> modelSelector;
    private Label statusLabel;
    private ProgressIndicator loadingIndicator;

    // 样式常量
    private static final String USER_BG = "#E3F2FD";
    private static final String ASSISTANT_BG = "#F5F5F5";
    private static final String SYSTEM_BG = "#FFF3E0";
    private static final String ERROR_BG = "#FFEBEE";
    private static final String PENDING_BG = "#FFF8E1";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 无参构造函数，使用DatabaseUtil获取JdbcTemplate
     */
    public AgentChatStage() {
        this(DatabaseUtil.getJdbcTemplate(null));
    }

    public AgentChatStage(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initUI();
        initAgent();
    }

    private void initUI() {
        setTitle("AI 游戏数据助手");
        setWidth(900);
        setHeight(700);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: white;");

        // 顶部工具栏
        root.setTop(createToolbar());

        // 中间聊天区域
        root.setCenter(createChatArea());

        // 底部输入区域
        root.setBottom(createInputArea());

        Scene scene = new Scene(root);
        setScene(scene);

        // 关闭时清理
        setOnCloseRequest(e -> {
            if (agent != null) {
                agent.clearSession();
            }
        });
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        // 模型选择
        Label modelLabel = new Label("AI模型:");
        modelSelector = new ComboBox<>();
        modelSelector.getItems().addAll("qwen", "doubao", "kimi", "deepseek");
        modelSelector.setValue("qwen");
        modelSelector.setOnAction(e -> {
            if (agent != null) {
                agent.switchModel(modelSelector.getValue());
                addSystemMessage("已切换到 " + modelSelector.getValue() + " 模型");
            }
        });

        // 新对话按钮
        Button newChatBtn = new Button("新对话");
        newChatBtn.setOnAction(e -> {
            if (agent != null) {
                agent.startNewSession();
                chatContainer.getChildren().clear();
                addSystemMessage("已开始新对话");
            }
        });

        // 清空按钮
        Button clearBtn = new Button("清空");
        clearBtn.setOnAction(e -> {
            if (agent != null) {
                agent.clearSession();
                chatContainer.getChildren().clear();
                addSystemMessage("对话已清空");
            }
        });

        // 状态指示
        statusLabel = new Label("就绪");
        statusLabel.setStyle("-fx-text-fill: #666;");

        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(20, 20);
        loadingIndicator.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(
            modelLabel, modelSelector,
            new Separator(),
            newChatBtn, clearBtn,
            spacer,
            loadingIndicator, statusLabel
        );

        return toolbar;
    }

    private ScrollPane createChatArea() {
        chatContainer = new VBox(10);
        chatContainer.setPadding(new Insets(15));
        chatContainer.setStyle("-fx-background-color: white;");

        chatScrollPane = new ScrollPane(chatContainer);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setStyle("-fx-background-color: white; -fx-background: white;");

        // 自动滚动到底部
        chatContainer.heightProperty().addListener((obs, old, newVal) -> {
            chatScrollPane.setVvalue(1.0);
        });

        return chatScrollPane;
    }

    private VBox createInputArea() {
        VBox inputBox = new VBox(5);
        inputBox.setPadding(new Insets(10));
        inputBox.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");

        // 待确认提示条
        HBox pendingBar = createPendingBar();
        pendingBar.setVisible(false);
        pendingBar.setManaged(false);

        // 输入区域
        HBox inputRow = new HBox(10);
        inputRow.setAlignment(Pos.BOTTOM_CENTER);

        inputArea = new TextArea();
        inputArea.setPromptText("输入您的问题...（Ctrl+Enter发送）");
        inputArea.setPrefRowCount(3);
        inputArea.setWrapText(true);
        HBox.setHgrow(inputArea, Priority.ALWAYS);

        // 快捷键
        inputArea.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && e.isControlDown()) {
                sendMessage();
                e.consume();
            }
        });

        sendButton = new Button("发送");
        sendButton.setPrefWidth(80);
        sendButton.setPrefHeight(60);
        sendButton.setStyle("-fx-background-color: #1976D2; -fx-text-fill: white; -fx-font-weight: bold;");
        sendButton.setOnAction(e -> sendMessage());

        inputRow.getChildren().addAll(inputArea, sendButton);

        // 快捷提示
        HBox hints = new HBox(15);
        hints.setAlignment(Pos.CENTER_LEFT);

        Label hintLabel = new Label("快捷输入:");
        hintLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11;");

        String[] quickInputs = {"查询所有物品表", "显示紫色装备", "分析技能伤害分布", "查看操作历史"};
        for (String hint : quickInputs) {
            Hyperlink link = new Hyperlink(hint);
            link.setStyle("-fx-font-size: 11;");
            link.setOnAction(e -> {
                inputArea.setText(hint);
                sendMessage();
            });
            hints.getChildren().add(link);
        }

        hints.getChildren().add(0, hintLabel);

        inputBox.getChildren().addAll(pendingBar, inputRow, hints);
        return inputBox;
    }

    private HBox createPendingBar() {
        HBox bar = new HBox(15);
        bar.setPadding(new Insets(8));
        bar.setAlignment(Pos.CENTER);
        bar.setStyle("-fx-background-color: #FFF8E1; -fx-border-color: #FFB300; " +
                    "-fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4;");

        Label icon = new Label("⚠️");
        Label text = new Label("有待确认的操作");
        text.setStyle("-fx-font-weight: bold;");

        Button confirmBtn = new Button("确认执行");
        confirmBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        confirmBtn.setOnAction(e -> {
            inputArea.setText("确认");
            sendMessage();
        });

        Button cancelBtn = new Button("取消");
        cancelBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        cancelBtn.setOnAction(e -> {
            inputArea.setText("取消");
            sendMessage();
        });

        bar.getChildren().addAll(icon, text, confirmBtn, cancelBtn);
        return bar;
    }

    private void initAgent() {
        agent = new GameDataAgent();

        // 设置消息回调
        agent.setMessageCallback(message -> {
            Platform.runLater(() -> addMessageToChat(message));
        });

        // 初始化Agent
        try {
            agent.initialize(jdbcTemplate);
            addSystemMessage("AI游戏数据助手已就绪！\n可以用自然语言查询和修改游戏数据。\n例如：\"查询所有50级以上的紫色武器\"");
        } catch (Exception e) {
            log.error("Agent初始化失败", e);
            addErrorMessage("Agent初始化失败: " + e.getMessage());
        }
    }

    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        inputArea.clear();
        setLoading(true);

        // 在后台线程执行
        new Thread(() -> {
            try {
                agent.chat(text);
            } catch (Exception e) {
                log.error("消息处理失败", e);
                Platform.runLater(() -> addErrorMessage("处理失败: " + e.getMessage()));
            } finally {
                Platform.runLater(() -> {
                    setLoading(false);
                    updatePendingBar();
                });
            }
        }).start();
    }

    private void addMessageToChat(AgentMessage message) {
        VBox messageBox = createMessageBox(message);
        chatContainer.getChildren().add(messageBox);
    }

    private VBox createMessageBox(AgentMessage message) {
        VBox box = new VBox(5);
        box.setPadding(new Insets(10));
        box.setMaxWidth(700);

        // 根据角色设置样式
        String bgColor;
        Pos alignment;

        switch (message.getRole()) {
            case USER:
                bgColor = USER_BG;
                alignment = Pos.CENTER_RIGHT;
                break;
            case ASSISTANT:
                bgColor = message.getType() == AgentMessage.MessageType.PENDING_CONFIRMATION ?
                    PENDING_BG : ASSISTANT_BG;
                alignment = Pos.CENTER_LEFT;
                break;
            case SYSTEM:
                bgColor = SYSTEM_BG;
                alignment = Pos.CENTER;
                break;
            default:
                bgColor = message.getType() == AgentMessage.MessageType.ERROR ? ERROR_BG : ASSISTANT_BG;
                alignment = Pos.CENTER_LEFT;
        }

        box.setStyle(String.format(
            "-fx-background-color: %s; -fx-background-radius: 8; -fx-border-radius: 8;",
            bgColor));

        // 头部：角色图标和时间
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label roleIcon = new Label(message.getRole().getIcon());
        roleIcon.setFont(Font.font(14));

        Label timeLabel = new Label(message.getTimestamp().format(TIME_FORMATTER));
        timeLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 10;");

        header.getChildren().addAll(roleIcon, timeLabel);

        // 内容
        Label content = new Label(message.getContent());
        content.setWrapText(true);
        content.setStyle("-fx-font-size: 13;");

        // 特殊类型标记
        if (message.getType() == AgentMessage.MessageType.PENDING_CONFIRMATION) {
            Label badge = new Label("待确认");
            badge.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; " +
                          "-fx-padding: 2 6; -fx-background-radius: 3; -fx-font-size: 10;");
            header.getChildren().add(badge);
        } else if (message.getType() == AgentMessage.MessageType.ERROR) {
            Label badge = new Label("错误");
            badge.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; " +
                          "-fx-padding: 2 6; -fx-background-radius: 3; -fx-font-size: 10;");
            header.getChildren().add(badge);
        }

        box.getChildren().addAll(header, content);

        // 外层容器用于对齐
        HBox wrapper = new HBox();
        wrapper.setAlignment(alignment);
        wrapper.getChildren().add(box);

        VBox result = new VBox();
        result.getChildren().add(wrapper);
        return result;
    }

    private void addSystemMessage(String text) {
        AgentMessage msg = AgentMessage.system(text);
        addMessageToChat(msg);
    }

    private void addErrorMessage(String text) {
        AgentMessage msg = AgentMessage.error(text);
        addMessageToChat(msg);
    }

    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        sendButton.setDisable(loading);
        inputArea.setDisable(loading);
        statusLabel.setText(loading ? "思考中..." : "就绪");
    }

    private void updatePendingBar() {
        HBox pendingBar = (HBox) ((VBox) chatScrollPane.getParent().lookup(".input-area")).getChildren().get(0);

        boolean hasPending = agent != null && agent.hasPendingOperation();

        // 查找pending bar（第一个子节点）
        VBox inputBox = (VBox) sendButton.getParent().getParent();
        if (inputBox.getChildren().size() > 0 && inputBox.getChildren().get(0) instanceof HBox) {
            HBox bar = (HBox) inputBox.getChildren().get(0);
            if (bar.getChildren().size() > 2) { // 确保是pending bar
                bar.setVisible(hasPending);
                bar.setManaged(hasPending);
            }
        }
    }

    /**
     * 显示窗口
     */
    public static void showAgent(JdbcTemplate jdbcTemplate) {
        Platform.runLater(() -> {
            AgentChatStage stage = new AgentChatStage(jdbcTemplate);
            stage.show();
        });
    }
}
