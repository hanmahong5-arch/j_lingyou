package red.jiuzhou.ops.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.service.CharacterService;
import red.jiuzhou.ops.service.GameOpsService;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 快捷操作栏
 *
 * 提供常用运维操作的一键执行入口：
 * - 角色查询
 * - 发送物品
 * - 封禁账号
 * - 数据清理
 * - 系统备份
 *
 * @author yanxq
 * @date 2026-01-16
 */
public class QuickActionBar extends HBox {

    private static final Logger log = LoggerFactory.getLogger(QuickActionBar.class);

    private final GameOpsService opsService;
    private final CharacterService characterService;
    private Consumer<String> statusCallback;

    public QuickActionBar(GameOpsService opsService, CharacterService characterService) {
        this.opsService = opsService;
        this.characterService = characterService;
        buildUI();
    }

    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }

    private void buildUI() {
        setSpacing(12);
        setPadding(new Insets(8, 12, 8, 12));
        setAlignment(Pos.CENTER_LEFT);
        setStyle("-fx-background-color: linear-gradient(to right, #667eea, #764ba2);");

        Label quickLabel = new Label("⚡ 快捷操作");
        quickLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");

        // Core quick actions
        Button queryCharBtn = createQuickButton("🔍 查角色", "快速查询角色信息", this::doQueryCharacter);
        Button sendItemBtn = createQuickButton("📦 发物品", "给角色发送物品", this::doSendItem);
        Button addKinahBtn = createQuickButton("💰 加金币", "给角色增加金币", this::doAddKinah);
        Button banAccountBtn = createQuickButton("🚫 封号", "封禁账号", this::doBanAccount);

        Separator sep1 = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep1.setStyle("-fx-background-color: rgba(255,255,255,0.3);");

        // Maintenance actions
        Button cleanDataBtn = createQuickButton("🧹 清数据", "清理不活跃角色", this::doCleanData);
        Button reindexBtn = createQuickButton("🔄 重建索引", "重建数据库索引", this::doReindex);

        Separator sep2 = new Separator(javafx.geometry.Orientation.VERTICAL);
        sep2.setStyle("-fx-background-color: rgba(255,255,255,0.3);");

        // Statistics
        Button statsBtn = createQuickButton("📊 统计", "查看服务器统计", this::doShowStats);

        getChildren().addAll(
                quickLabel,
                queryCharBtn, sendItemBtn, addKinahBtn, banAccountBtn,
                sep1,
                cleanDataBtn, reindexBtn,
                sep2,
                statsBtn
        );
    }

    private Button createQuickButton(String text, String tooltip, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle("""
                -fx-background-color: rgba(255,255,255,0.2);
                -fx-text-fill: white;
                -fx-font-weight: bold;
                -fx-cursor: hand;
                """);
        btn.setTooltip(new Tooltip(tooltip));

        btn.setOnMouseEntered(e -> btn.setStyle("""
                -fx-background-color: rgba(255,255,255,0.4);
                -fx-text-fill: white;
                -fx-font-weight: bold;
                -fx-cursor: hand;
                """));
        btn.setOnMouseExited(e -> btn.setStyle("""
                -fx-background-color: rgba(255,255,255,0.2);
                -fx-text-fill: white;
                -fx-font-weight: bold;
                -fx-cursor: hand;
                """));

        btn.setOnAction(e -> action.run());
        return btn;
    }

    // ==================== Quick Actions ====================

    private void doQueryCharacter() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("快速查询角色");
        dialog.setHeaderText("输入角色名称或ID");
        dialog.setContentText("角色名/ID:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(input -> {
            updateStatus("正在查询: " + input);

            // Try as ID first, then as name
            try {
                int charId = Integer.parseInt(input);
                characterService.findById(charId).ifPresentOrElse(
                        this::showCharacterInfo,
                        () -> showAlert(Alert.AlertType.WARNING, "未找到", "未找到ID为 " + charId + " 的角色")
                );
            } catch (NumberFormatException e) {
                characterService.findByName(input).ifPresentOrElse(
                        this::showCharacterInfo,
                        () -> showAlert(Alert.AlertType.WARNING, "未找到", "未找到名为 \"" + input + "\" 的角色")
                );
            }
        });
    }

    private void showCharacterInfo(red.jiuzhou.ops.model.GameCharacter c) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("角色信息");
        alert.setHeaderText(c.name() + " (ID: " + c.charId() + ")");

        String content = String.format("""
                种族: %s | 职业: %s
                等级: %d | 状态: %s
                金币: %,d
                公会: %s
                位置: %s
                """,
                c.getRaceDisplay(), c.getClassDisplay(),
                c.level(), c.getStatusDisplay(),
                c.kinah(),
                c.hasGuild() ? c.guildName() : "无",
                c.getPositionString()
        );
        alert.setContentText(content);
        alert.showAndWait();
        updateStatus("查询完成: " + c.name());
    }

    private void doSendItem() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("发送物品");
        dialog.setHeaderText("给角色发送物品");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        TextField charField = new TextField();
        charField.setPromptText("角色名称或ID");

        TextField itemField = new TextField();
        itemField.setPromptText("物品ID");

        TextField countField = new TextField("1");
        countField.setPromptText("数量");

        content.getChildren().addAll(
                new Label("角色:"), charField,
                new Label("物品ID:"), itemField,
                new Label("数量:"), countField
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return new String[]{charField.getText(), itemField.getText(), countField.getText()};
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(data -> {
            try {
                String charInput = data[0];
                int itemId = Integer.parseInt(data[1]);
                int count = Integer.parseInt(data[2]);

                // Get character ID
                int charId;
                try {
                    charId = Integer.parseInt(charInput);
                } catch (NumberFormatException e) {
                    var character = characterService.findByName(charInput);
                    if (character.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "错误", "未找到角色: " + charInput);
                        return;
                    }
                    charId = character.get().charId();
                }

                if (opsService.sendItem(charId, itemId, count)) {
                    showAlert(Alert.AlertType.INFORMATION, "成功",
                            String.format("已发送 %d 个物品(ID:%d) 给角色", count, itemId));
                    updateStatus("物品发送成功");
                } else {
                    showAlert(Alert.AlertType.ERROR, "失败", "发送物品失败");
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "错误", "请输入有效的数字");
            }
        });
    }

    private void doAddKinah() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("增加金币");
        dialog.setHeaderText("给角色增加金币");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        TextField charField = new TextField();
        charField.setPromptText("角色名称或ID");

        TextField amountField = new TextField("1000000");
        amountField.setPromptText("金币数量");

        content.getChildren().addAll(
                new Label("角色:"), charField,
                new Label("金币数量:"), amountField
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return new String[]{charField.getText(), amountField.getText()};
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(data -> {
            try {
                String charInput = data[0];
                long amount = Long.parseLong(data[1]);

                int charId;
                try {
                    charId = Integer.parseInt(charInput);
                } catch (NumberFormatException e) {
                    var character = characterService.findByName(charInput);
                    if (character.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "错误", "未找到角色: " + charInput);
                        return;
                    }
                    charId = character.get().charId();
                }

                if (characterService.addKinah(charId, amount)) {
                    showAlert(Alert.AlertType.INFORMATION, "成功",
                            String.format("已增加 %,d 金币", amount));
                    updateStatus("金币增加成功");
                } else {
                    showAlert(Alert.AlertType.ERROR, "失败", "增加金币失败");
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "错误", "请输入有效的数字");
            }
        });
    }

    private void doBanAccount() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("封禁账号");
        dialog.setHeaderText("⚠️ 警告：此操作将封禁账号");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        TextField accountField = new TextField();
        accountField.setPromptText("账号ID");

        TextField reasonField = new TextField();
        reasonField.setPromptText("封禁原因");

        TextField durationField = new TextField("24");
        durationField.setPromptText("封禁时长(小时)");

        content.getChildren().addAll(
                new Label("账号ID:"), accountField,
                new Label("封禁原因:"), reasonField,
                new Label("封禁时长(小时):"), durationField
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return new String[]{accountField.getText(), reasonField.getText(), durationField.getText()};
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(data -> {
            try {
                int accountId = Integer.parseInt(data[0]);
                String reason = data[1];
                int duration = Integer.parseInt(data[2]);

                // Confirm
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("确认封禁");
                confirm.setHeaderText("确认封禁账号 " + accountId + "?");
                confirm.setContentText("原因: " + reason + "\n时长: " + duration + " 小时");

                if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    if (opsService.banAccount(accountId, reason, duration)) {
                        showAlert(Alert.AlertType.INFORMATION, "成功", "账号已封禁");
                        updateStatus("账号封禁成功: " + accountId);
                    } else {
                        showAlert(Alert.AlertType.ERROR, "失败", "封禁账号失败");
                    }
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "错误", "请输入有效的数字");
            }
        });
    }

    private void doCleanData() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("清理不活跃角色");
        dialog.setHeaderText("⚠️ 此操作将删除符合条件的角色");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        TextField daysField = new TextField("90");
        daysField.setPromptText("不活跃天数");

        TextField levelField = new TextField("10");
        levelField.setPromptText("最高等级");

        content.getChildren().addAll(
                new Label("不活跃天数 (超过此天数未登录):"), daysField,
                new Label("最高等级 (等级低于此值):"), levelField
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return new String[]{daysField.getText(), levelField.getText()};
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(data -> {
            try {
                int days = Integer.parseInt(data[0]);
                int maxLevel = Integer.parseInt(data[1]);

                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                confirm.setTitle("确认清理");
                confirm.setHeaderText("确认清理数据?");
                confirm.setContentText(String.format(
                        "将删除超过 %d 天未登录且等级低于 %d 的角色",
                        days, maxLevel
                ));

                if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    int deleted = opsService.cleanupInactiveCharacters(days, maxLevel);
                    if (deleted >= 0) {
                        showAlert(Alert.AlertType.INFORMATION, "完成",
                                "已清理 " + deleted + " 个不活跃角色");
                        updateStatus("清理完成: " + deleted + " 个角色");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "失败", "清理操作失败");
                    }
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "错误", "请输入有效的数字");
            }
        });
    }

    private void doReindex() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("重建索引");
        confirm.setHeaderText("确认重建数据库索引?");
        confirm.setContentText("此操作可能需要较长时间，建议在低峰期执行。");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            updateStatus("正在重建索引...");

            new Thread(() -> {
                boolean success = opsService.reindexDatabase();
                Platform.runLater(() -> {
                    if (success) {
                        showAlert(Alert.AlertType.INFORMATION, "完成", "数据库索引重建完成");
                        updateStatus("索引重建完成");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "失败", "索引重建失败");
                        updateStatus("索引重建失败");
                    }
                });
            }).start();
        }
    }

    private void doShowStats() {
        updateStatus("正在获取统计信息...");

        new Thread(() -> {
            var stats = opsService.getServerStatistics();
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("服务器统计");
                alert.setHeaderText("当前服务器状态");

                StringBuilder sb = new StringBuilder();
                sb.append("📊 服务器统计\n");
                sb.append("═══════════════════\n\n");

                if (stats.containsKey("onlinePlayers")) {
                    sb.append("🟢 在线玩家: ").append(stats.get("onlinePlayers")).append("\n");
                }
                if (stats.containsKey("totalCharacters")) {
                    sb.append("👤 总角色数: ").append(stats.get("totalCharacters")).append("\n");
                }
                if (stats.containsKey("totalGuilds")) {
                    sb.append("👥 总公会数: ").append(stats.get("totalGuilds")).append("\n");
                }

                alert.setContentText(sb.toString());
                alert.showAndWait();
                updateStatus("统计信息已获取");
            });
        }).start();
    }

    // ==================== Helpers ====================

    private void updateStatus(String message) {
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
        log.info("QuickAction: {}", message);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}
