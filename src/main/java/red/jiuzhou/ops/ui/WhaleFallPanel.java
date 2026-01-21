package red.jiuzhou.ops.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.ops.db.SqlServerConnection;

import java.util.List;
import java.util.Map;

/**
 * 鲸落系统管理面板
 *
 * 功能:
 * - 系统开关
 * - 模式切换 (交易所/击杀者继承/销毁)
 * - 配置管理
 * - 日志查看
 * - 手动触发鲸落
 *
 * @author yanxq
 * @date 2026-01-17
 */
public class WhaleFallPanel extends VBox {

    private static final Logger log = LoggerFactory.getLogger(WhaleFallPanel.class);

    private final SqlServerConnection connection;

    // UI Components
    private ToggleButton systemToggle;
    private ToggleGroup modeGroup;
    private RadioButton brokerMode;
    private RadioButton killerMode;
    private RadioButton destroyMode;
    private Spinner<Integer> deathThreshold;
    private Spinner<Integer> brokerPrice;
    private Spinner<Integer> minLevel;
    private Spinner<Integer> killerTimeout;
    private CheckBox excludeBound;
    private CheckBox excludeEquipped;
    private TableView<Map<String, Object>> logTable;
    private Label statusLabel;

    public WhaleFallPanel(SqlServerConnection connection) {
        this.connection = connection;

        setSpacing(15);
        setPadding(new Insets(20));
        setStyle("-fx-background-color: #1e1e1e;");

        initUI();
        loadConfig();
        loadLogs();
    }

    private void initUI() {
        // Title
        Label title = new Label("🐋 鲸落系统管理");
        title.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 24));
        title.setTextFill(Color.web("#4fc3f7"));

        Label subtitle = new Label("当玩家死亡时，物品回馈给全服玩家或击杀者");
        subtitle.setTextFill(Color.GRAY);

        // System Toggle
        HBox toggleBox = createToggleSection();

        // Mode Selection
        VBox modeBox = createModeSection();

        // Configuration
        GridPane configGrid = createConfigSection();

        // Action Buttons
        HBox actionBox = createActionButtons();

        // Log Table
        VBox logBox = createLogSection();

        // Status
        statusLabel = new Label("就绪");
        statusLabel.setTextFill(Color.LIGHTGREEN);

        getChildren().addAll(
                title, subtitle,
                new Separator(),
                toggleBox,
                new Separator(),
                modeBox,
                new Separator(),
                configGrid,
                new Separator(),
                actionBox,
                new Separator(),
                logBox,
                statusLabel
        );
    }

    private HBox createToggleSection() {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("系统状态:");
        label.setTextFill(Color.WHITE);

        systemToggle = new ToggleButton("已禁用");
        systemToggle.setStyle("-fx-background-color: #555; -fx-text-fill: white;");
        systemToggle.setOnAction(e -> toggleSystem());

        box.getChildren().addAll(label, systemToggle);
        return box;
    }

    private VBox createModeSection() {
        VBox box = new VBox(10);

        Label label = new Label("鲸落模式:");
        label.setTextFill(Color.WHITE);
        label.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        modeGroup = new ToggleGroup();

        brokerMode = new RadioButton("交易所模式 - 物品以低价上架交易所，惠及全服玩家");
        brokerMode.setToggleGroup(modeGroup);
        brokerMode.setTextFill(Color.LIGHTBLUE);
        brokerMode.setUserData("BROKER");

        killerMode = new RadioButton("击杀继承模式 - 击杀者获得被杀者的所有物品");
        killerMode.setToggleGroup(modeGroup);
        killerMode.setTextFill(Color.ORANGE);
        killerMode.setUserData("KILLER");

        destroyMode = new RadioButton("销毁模式 - 物品直接销毁，仅删除角色");
        destroyMode.setToggleGroup(modeGroup);
        destroyMode.setTextFill(Color.LIGHTCORAL);
        destroyMode.setUserData("DESTROY");

        modeGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                String mode = (String) newVal.getUserData();
                setMode(mode);
            }
        });

        box.getChildren().addAll(label, brokerMode, killerMode, destroyMode);
        return box;
    }

    private GridPane createConfigSection() {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);

        // Row 0: Death Threshold
        Label thresholdLabel = new Label("死亡阈值:");
        thresholdLabel.setTextFill(Color.WHITE);
        deathThreshold = new Spinner<>(1, 99, 1);
        deathThreshold.setEditable(true);
        deathThreshold.setPrefWidth(80);
        deathThreshold.valueProperty().addListener((obs, old, val) -> updateConfig("death_threshold", val.toString()));

        // Row 0: Broker Price
        Label priceLabel = new Label("上架价格:");
        priceLabel.setTextFill(Color.WHITE);
        brokerPrice = new Spinner<>(1, 999999999, 1);
        brokerPrice.setEditable(true);
        brokerPrice.setPrefWidth(120);
        brokerPrice.valueProperty().addListener((obs, old, val) -> updateConfig("broker_price", val.toString()));

        // Row 1: Min Level
        Label levelLabel = new Label("最低等级:");
        levelLabel.setTextFill(Color.WHITE);
        minLevel = new Spinner<>(0, 99, 0);
        minLevel.setEditable(true);
        minLevel.setPrefWidth(80);
        minLevel.valueProperty().addListener((obs, old, val) -> updateConfig("min_level", val.toString()));

        // Row 1: Killer Timeout
        Label timeoutLabel = new Label("击杀记录有效(分):");
        timeoutLabel.setTextFill(Color.WHITE);
        killerTimeout = new Spinner<>(1, 60, 5);
        killerTimeout.setEditable(true);
        killerTimeout.setPrefWidth(80);
        killerTimeout.valueProperty().addListener((obs, old, val) -> updateConfig("killer_timeout_minutes", val.toString()));

        // Row 2: Checkboxes
        excludeBound = new CheckBox("排除绑定物品");
        excludeBound.setTextFill(Color.WHITE);
        excludeBound.setOnAction(e -> updateConfig("exclude_bound_items", excludeBound.isSelected() ? "1" : "0"));

        excludeEquipped = new CheckBox("排除已装备物品");
        excludeEquipped.setTextFill(Color.WHITE);
        excludeEquipped.setOnAction(e -> updateConfig("exclude_equipped_items", excludeEquipped.isSelected() ? "1" : "0"));

        grid.add(thresholdLabel, 0, 0);
        grid.add(deathThreshold, 1, 0);
        grid.add(priceLabel, 2, 0);
        grid.add(brokerPrice, 3, 0);

        grid.add(levelLabel, 0, 1);
        grid.add(minLevel, 1, 1);
        grid.add(timeoutLabel, 2, 1);
        grid.add(killerTimeout, 3, 1);

        grid.add(excludeBound, 0, 2, 2, 1);
        grid.add(excludeEquipped, 2, 2, 2, 1);

        return grid;
    }

    private HBox createActionButtons() {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_LEFT);

        Button refreshBtn = new Button("🔄 刷新配置");
        refreshBtn.setOnAction(e -> {
            loadConfig();
            loadLogs();
        });

        Button deployBtn = new Button("📦 部署系统");
        deployBtn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white;");
        deployBtn.setOnAction(e -> deploySql());

        Button testBtn = new Button("🧪 测试鲸落");
        testBtn.setOnAction(e -> showTestDialog());

        Button killRecordBtn = new Button("⚔️ 记录击杀");
        killRecordBtn.setOnAction(e -> showKillRecordDialog());

        box.getChildren().addAll(refreshBtn, deployBtn, testBtn, killRecordBtn);
        return box;
    }

    private VBox createLogSection() {
        VBox box = new VBox(10);

        Label label = new Label("鲸落日志 (最近20条):");
        label.setTextFill(Color.WHITE);
        label.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 14));

        logTable = new TableView<>();
        logTable.setPrefHeight(200);
        logTable.setStyle("-fx-background-color: #2d2d2d;");

        TableColumn<Map<String, Object>, String> timeCol = new TableColumn<>("时间");
        timeCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().get("fall_time") != null ? data.getValue().get("fall_time").toString() : ""));
        timeCol.setPrefWidth(150);

        TableColumn<Map<String, Object>, String> nameCol = new TableColumn<>("角色");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(
                (String) data.getValue().get("char_name")));
        nameCol.setPrefWidth(100);

        TableColumn<Map<String, Object>, String> modeCol = new TableColumn<>("模式");
        modeCol.setCellValueFactory(data -> {
            String mode = (String) data.getValue().get("fall_mode");
            String display = switch (mode != null ? mode : "") {
                case "BROKER" -> "交易所";
                case "KILLER" -> "击杀继承";
                case "DESTROY" -> "销毁";
                default -> mode;
            };
            return new SimpleStringProperty(display);
        });
        modeCol.setPrefWidth(80);

        TableColumn<Map<String, Object>, String> killerCol = new TableColumn<>("击杀者");
        killerCol.setCellValueFactory(data -> new SimpleStringProperty(
                (String) data.getValue().get("killer_name")));
        killerCol.setPrefWidth(100);

        TableColumn<Map<String, Object>, String> itemsCol = new TableColumn<>("物品数");
        itemsCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().get("total_items") != null ? data.getValue().get("total_items").toString() : "0"));
        itemsCol.setPrefWidth(60);

        TableColumn<Map<String, Object>, String> levelCol = new TableColumn<>("等级");
        levelCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().get("level") != null ? data.getValue().get("level").toString() : ""));
        levelCol.setPrefWidth(50);

        logTable.getColumns().addAll(timeCol, nameCol, modeCol, killerCol, itemsCol, levelCol);

        Button viewDetailBtn = new Button("查看详情");
        viewDetailBtn.setOnAction(e -> {
            Map<String, Object> selected = logTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showLogDetail(selected);
            }
        });

        box.getChildren().addAll(label, logTable, viewDetailBtn);
        return box;
    }

    private void loadConfig() {
        try {
            connection.connect("AionWorldLive");

            // Check if table exists
            List<Map<String, Object>> tables = connection.getJdbcTemplate().queryForList(
                    "SELECT name FROM sys.tables WHERE name = 'whale_fall_config'");

            if (tables.isEmpty()) {
                setStatus("配置表不存在，请先部署系统", Color.YELLOW);
                return;
            }

            List<Map<String, Object>> configs = connection.getJdbcTemplate().queryForList(
                    "SELECT config_name, config_value FROM whale_fall_config");

            for (Map<String, Object> config : configs) {
                String name = (String) config.get("config_name");
                String value = (String) config.get("config_value");

                switch (name) {
                    case "system_enabled" -> {
                        boolean enabled = "1".equals(value);
                        systemToggle.setSelected(enabled);
                        systemToggle.setText(enabled ? "已启用" : "已禁用");
                        systemToggle.setStyle(enabled ?
                                "-fx-background-color: #4caf50; -fx-text-fill: white;" :
                                "-fx-background-color: #555; -fx-text-fill: white;");
                    }
                    case "fall_mode" -> {
                        switch (value) {
                            case "BROKER" -> brokerMode.setSelected(true);
                            case "KILLER" -> killerMode.setSelected(true);
                            case "DESTROY" -> destroyMode.setSelected(true);
                        }
                    }
                    case "death_threshold" -> deathThreshold.getValueFactory().setValue(Integer.parseInt(value));
                    case "broker_price" -> brokerPrice.getValueFactory().setValue(Integer.parseInt(value));
                    case "min_level" -> minLevel.getValueFactory().setValue(Integer.parseInt(value));
                    case "killer_timeout_minutes" -> killerTimeout.getValueFactory().setValue(Integer.parseInt(value));
                    case "exclude_bound_items" -> excludeBound.setSelected("1".equals(value));
                    case "exclude_equipped_items" -> excludeEquipped.setSelected("1".equals(value));
                }
            }

            setStatus("配置已加载", Color.LIGHTGREEN);
        } catch (Exception e) {
            log.error("加载配置失败", e);
            setStatus("加载配置失败: " + e.getMessage(), Color.RED);
        }
    }

    private void loadLogs() {
        try {
            List<Map<String, Object>> tables = connection.getJdbcTemplate().queryForList(
                    "SELECT name FROM sys.tables WHERE name = 'whale_fall_log'");

            if (tables.isEmpty()) {
                return;
            }

            List<Map<String, Object>> logs = connection.getJdbcTemplate().queryForList(
                    "SELECT TOP 20 * FROM whale_fall_log ORDER BY fall_time DESC");

            ObservableList<Map<String, Object>> data = FXCollections.observableArrayList(logs);
            logTable.setItems(data);
        } catch (Exception e) {
            log.error("加载日志失败", e);
        }
    }

    private void toggleSystem() {
        boolean enabled = systemToggle.isSelected();
        updateConfig("system_enabled", enabled ? "1" : "0");
        systemToggle.setText(enabled ? "已启用" : "已禁用");
        systemToggle.setStyle(enabled ?
                "-fx-background-color: #4caf50; -fx-text-fill: white;" :
                "-fx-background-color: #555; -fx-text-fill: white;");
        setStatus(enabled ? "鲸落系统已启用" : "鲸落系统已禁用", enabled ? Color.LIGHTGREEN : Color.ORANGE);
    }

    private void setMode(String mode) {
        updateConfig("fall_mode", mode);
        String modeName = switch (mode) {
            case "BROKER" -> "交易所模式";
            case "KILLER" -> "击杀继承模式";
            case "DESTROY" -> "销毁模式";
            default -> mode;
        };
        setStatus("已切换到: " + modeName, Color.LIGHTBLUE);
    }

    private void updateConfig(String name, String value) {
        try {
            connection.getJdbcTemplate().update(
                    "UPDATE whale_fall_config SET config_value = ?, modified_date = GETDATE() WHERE config_name = ?",
                    value, name);
        } catch (Exception e) {
            log.error("更新配置失败: {} = {}", name, value, e);
            setStatus("更新失败: " + e.getMessage(), Color.RED);
        }
    }

    private void deploySql() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("部署确认");
        confirm.setHeaderText("部署鲸落系统 V2");
        confirm.setContentText("这将在数据库中创建以下对象:\n\n" +
                "• whale_fall_config (配置表)\n" +
                "• whale_fall_log (日志表)\n" +
                "• whale_fall_items (物品详情表)\n" +
                "• whale_fall_kill_record (击杀记录表)\n" +
                "• sp_whale_fall_execute (主存储过程)\n" +
                "• tr_whale_fall (触发器)\n\n" +
                "确定要继续吗？");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                executeDeployment();
            }
        });
    }

    /**
     * Execute SQL deployment in background thread
     */
    private void executeDeployment() {
        setStatus("正在部署鲸落系统...", Color.YELLOW);

        Thread deployThread = new Thread(() -> {
            try {
                connection.connect("AionWorldLive");
                var jdbc = connection.getJdbcTemplate();

                int step = 0;
                int totalSteps = 8;

                // Step 1: Drop existing trigger (if exists)
                step++;
                updateDeployProgress(step, totalSteps, "删除旧触发器...");
                executeSafely(jdbc, "IF EXISTS (SELECT * FROM sys.triggers WHERE name = 'tr_whale_fall') DROP TRIGGER tr_whale_fall");

                // Step 2: Drop existing procedures (if exists)
                step++;
                updateDeployProgress(step, totalSteps, "删除旧存储过程...");
                executeSafely(jdbc, "IF EXISTS (SELECT * FROM sys.procedures WHERE name = 'sp_whale_fall_execute') DROP PROCEDURE sp_whale_fall_execute");
                executeSafely(jdbc, "IF EXISTS (SELECT * FROM sys.procedures WHERE name = 'sp_whale_fall_record_kill') DROP PROCEDURE sp_whale_fall_record_kill");
                executeSafely(jdbc, "IF EXISTS (SELECT * FROM sys.procedures WHERE name = 'sp_whale_fall_set_mode') DROP PROCEDURE sp_whale_fall_set_mode");
                executeSafely(jdbc, "IF EXISTS (SELECT * FROM sys.procedures WHERE name = 'sp_whale_fall_toggle') DROP PROCEDURE sp_whale_fall_toggle");

                // Step 3: Create config table
                step++;
                updateDeployProgress(step, totalSteps, "创建配置表...");
                jdbc.execute("""
                    IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'whale_fall_config')
                    CREATE TABLE whale_fall_config (
                        config_id INT PRIMARY KEY IDENTITY(1,1),
                        config_name NVARCHAR(50) NOT NULL UNIQUE,
                        config_value NVARCHAR(200),
                        description NVARCHAR(500),
                        created_date DATETIME DEFAULT GETDATE(),
                        modified_date DATETIME DEFAULT GETDATE()
                    )
                    """);

                // Insert default config
                jdbc.execute("DELETE FROM whale_fall_config");
                jdbc.execute("""
                    INSERT INTO whale_fall_config (config_name, config_value, description) VALUES
                    ('system_enabled', '1', N'鲸落系统总开关'),
                    ('fall_mode', 'BROKER', N'模式: BROKER/KILLER/DESTROY'),
                    ('death_threshold', '1', N'死亡阈值'),
                    ('broker_price', '1', N'交易所价格'),
                    ('broker_expire_days', '7', N'物品有效期'),
                    ('exclude_bound_items', '1', N'排除绑定物品'),
                    ('exclude_equipped_items', '0', N'排除装备物品'),
                    ('min_level', '0', N'最低等级'),
                    ('killer_timeout_minutes', '5', N'击杀记录有效时间'),
                    ('system_seller_name', N'[鲸落]', N'卖家名称'),
                    ('log_items_detail', '1', N'记录物品详情')
                    """);

                // Step 4: Create kill record table
                step++;
                updateDeployProgress(step, totalSteps, "创建击杀记录表...");
                jdbc.execute("""
                    IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'whale_fall_kill_record')
                    CREATE TABLE whale_fall_kill_record (
                        record_id BIGINT PRIMARY KEY IDENTITY(1,1),
                        victim_char_id INT NOT NULL,
                        killer_char_id INT NOT NULL,
                        killer_name NVARCHAR(64),
                        kill_time DATETIME DEFAULT GETDATE(),
                        kill_type NVARCHAR(20) DEFAULT 'PVP',
                        world_id INT
                    )
                    """);

                // Step 5: Create log table
                step++;
                updateDeployProgress(step, totalSteps, "创建日志表...");
                jdbc.execute("""
                    IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'whale_fall_log')
                    CREATE TABLE whale_fall_log (
                        log_id BIGINT PRIMARY KEY IDENTITY(1,1),
                        char_id INT NOT NULL,
                        char_name NVARCHAR(64),
                        account_id INT,
                        race TINYINT,
                        level INT,
                        death_count INT,
                        fall_mode NVARCHAR(20),
                        killer_char_id INT,
                        killer_name NVARCHAR(64),
                        total_items INT,
                        total_value BIGINT,
                        fall_time DATETIME DEFAULT GETDATE(),
                        status NVARCHAR(20) DEFAULT 'SUCCESS'
                    )
                    """);

                // Step 6: Create items table
                step++;
                updateDeployProgress(step, totalSteps, "创建物品详情表...");
                jdbc.execute("""
                    IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'whale_fall_items')
                    CREATE TABLE whale_fall_items (
                        item_log_id BIGINT PRIMARY KEY IDENTITY(1,1),
                        log_id BIGINT NOT NULL,
                        char_id INT NOT NULL,
                        user_item_id BIGINT NOT NULL,
                        item_name_id INT,
                        amount BIGINT,
                        destination NVARCHAR(20),
                        dest_char_id INT,
                        price BIGINT,
                        transfer_time DATETIME DEFAULT GETDATE()
                    )
                    """);

                // Step 7: Create stored procedures
                step++;
                updateDeployProgress(step, totalSteps, "创建存储过程...");
                createStoredProcedures(jdbc);

                // Step 8: Create trigger
                step++;
                updateDeployProgress(step, totalSteps, "创建触发器...");
                createTrigger(jdbc);

                // Done
                Platform.runLater(() -> {
                    setStatus("✅ 鲸落系统部署成功!", Color.LIGHTGREEN);
                    loadConfig();
                    loadLogs();

                    Alert success = new Alert(Alert.AlertType.INFORMATION);
                    success.setTitle("部署成功");
                    success.setHeaderText("鲸落系统 V2 已成功部署!");
                    success.setContentText("已创建:\n" +
                            "• 4 个数据表\n" +
                            "• 4 个存储过程\n" +
                            "• 1 个触发器\n\n" +
                            "系统已自动启用，默认为交易所模式。");
                    success.showAndWait();
                });

            } catch (Exception e) {
                log.error("部署鲸落系统失败", e);
                Platform.runLater(() -> {
                    setStatus("❌ 部署失败: " + e.getMessage(), Color.RED);
                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle("部署失败");
                    error.setHeaderText("鲸落系统部署失败");
                    error.setContentText(e.getMessage());
                    error.showAndWait();
                });
            }
        });

        deployThread.setDaemon(true);
        deployThread.start();
    }

    private void updateDeployProgress(int step, int total, String message) {
        Platform.runLater(() -> setStatus(String.format("部署中 (%d/%d): %s", step, total, message), Color.YELLOW));
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    }

    private void executeSafely(org.springframework.jdbc.core.JdbcTemplate jdbc, String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception e) {
            log.debug("SQL执行跳过: {}", e.getMessage());
        }
    }

    private void createStoredProcedures(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        // Record kill procedure
        jdbc.execute("""
            CREATE PROCEDURE sp_whale_fall_record_kill
                @victim_char_id INT,
                @killer_char_id INT,
                @kill_type NVARCHAR(20) = 'PVP',
                @world_id INT = 0
            AS
            BEGIN
                SET NOCOUNT ON
                DECLARE @killer_name NVARCHAR(64)
                SELECT @killer_name = cn.name FROM character_name cn
                JOIN user_data ud ON cn.name_id = ud.name_id WHERE ud.char_id = @killer_char_id
                DELETE FROM whale_fall_kill_record WHERE victim_char_id = @victim_char_id
                INSERT INTO whale_fall_kill_record (victim_char_id, killer_char_id, killer_name, kill_type, world_id)
                VALUES (@victim_char_id, @killer_char_id, @killer_name, @kill_type, @world_id)
            END
            """);

        // Set mode procedure
        jdbc.execute("""
            CREATE PROCEDURE sp_whale_fall_set_mode @mode NVARCHAR(20)
            AS
            BEGIN
                IF @mode NOT IN ('BROKER', 'KILLER', 'DESTROY') RETURN -1
                UPDATE whale_fall_config SET config_value = @mode, modified_date = GETDATE()
                WHERE config_name = 'fall_mode'
            END
            """);

        // Toggle procedure
        jdbc.execute("""
            CREATE PROCEDURE sp_whale_fall_toggle @enabled BIT
            AS
            BEGIN
                UPDATE whale_fall_config SET config_value = CAST(@enabled AS NVARCHAR), modified_date = GETDATE()
                WHERE config_name = 'system_enabled'
            END
            """);

        // Main execute procedure
        jdbc.execute("""
            CREATE PROCEDURE sp_whale_fall_execute @char_id INT, @force_mode NVARCHAR(20) = NULL
            AS
            BEGIN
                SET NOCOUNT ON
                DECLARE @race TINYINT, @level INT, @death_count INT, @char_name NVARCHAR(64), @account_id INT
                DECLARE @fall_mode NVARCHAR(20), @broker_price BIGINT = 1, @commit_date INT
                DECLARE @total_items INT = 0, @log_id BIGINT, @exclude_bound BIT = 1
                DECLARE @killer_char_id INT = NULL, @killer_name NVARCHAR(64) = NULL, @killer_timeout INT = 5

                SELECT @fall_mode = ISNULL(@force_mode, config_value) FROM whale_fall_config WHERE config_name = 'fall_mode'
                SELECT @broker_price = CAST(config_value AS BIGINT) FROM whale_fall_config WHERE config_name = 'broker_price'
                SELECT @exclude_bound = CAST(config_value AS BIT) FROM whale_fall_config WHERE config_name = 'exclude_bound_items'
                SELECT @killer_timeout = CAST(config_value AS INT) FROM whale_fall_config WHERE config_name = 'killer_timeout_minutes'
                SET @commit_date = DATEDIFF(SECOND, '1970-01-01', GETDATE())

                SELECT @race = race, @level = lev, @death_count = death_count, @account_id = account_id
                FROM user_data WHERE char_id = @char_id
                SELECT @char_name = cn.name FROM character_name cn JOIN user_data ud ON cn.name_id = ud.name_id WHERE ud.char_id = @char_id
                IF @race IS NULL RETURN -1

                IF @fall_mode = 'KILLER'
                BEGIN
                    SELECT TOP 1 @killer_char_id = killer_char_id, @killer_name = killer_name
                    FROM whale_fall_kill_record WHERE victim_char_id = @char_id
                    AND DATEDIFF(MINUTE, kill_time, GETDATE()) <= @killer_timeout ORDER BY kill_time DESC
                    IF @killer_char_id IS NULL SET @fall_mode = 'BROKER'
                END

                INSERT INTO whale_fall_log (char_id, char_name, account_id, race, level, death_count, fall_mode, killer_char_id, killer_name, total_items, total_value)
                VALUES (@char_id, @char_name, @account_id, @race, @level, @death_count, @fall_mode, @killer_char_id, @killer_name, 0, 0)
                SET @log_id = SCOPE_IDENTITY()

                CREATE TABLE #items (user_item_id BIGINT, name_id INT, amount BIGINT)
                INSERT INTO #items SELECT ui.id, ui.name_id, ui.amount FROM user_item ui WHERE ui.char_id = @char_id AND ui.warehouse IN (0, 1)
                IF @exclude_bound = 1 DELETE t FROM #items t WHERE EXISTS (SELECT 1 FROM user_item_bind b WHERE b.item_id = t.user_item_id)
                SELECT @total_items = COUNT(*) FROM #items

                IF @fall_mode = 'BROKER'
                BEGIN
                    IF @race = 0
                        INSERT INTO vendor_item_light (char_id, user_item_id, user_price, sale_price, commit_amount, remain_amount, commit_date, can_buy_partial, afterUnitFee, afterUnitTax)
                        SELECT @char_id, user_item_id, @broker_price, @broker_price, amount, amount, @commit_date, 1, 0, 0 FROM #items
                    ELSE
                        INSERT INTO vendor_item_dark (char_id, user_item_id, user_price, sale_price, commit_amount, remain_amount, commit_date, can_buy_partial, afterUnitFee, afterUnitTax)
                        SELECT @char_id, user_item_id, @broker_price, @broker_price, amount, amount, @commit_date, 1, 0, 0 FROM #items
                    INSERT INTO whale_fall_items (log_id, char_id, user_item_id, item_name_id, amount, destination, price)
                    SELECT @log_id, @char_id, user_item_id, name_id, amount, CASE @race WHEN 0 THEN 'BROKER_LIGHT' ELSE 'BROKER_DARK' END, @broker_price FROM #items
                END
                ELSE IF @fall_mode = 'KILLER'
                BEGIN
                    UPDATE ui SET ui.char_id = @killer_char_id, ui.warehouse = 0, ui.update_date = GETDATE()
                    FROM user_item ui JOIN #items t ON ui.id = t.user_item_id
                    INSERT INTO whale_fall_items (log_id, char_id, user_item_id, item_name_id, amount, destination, dest_char_id)
                    SELECT @log_id, @char_id, user_item_id, name_id, amount, 'KILLER', @killer_char_id FROM #items
                END
                ELSE IF @fall_mode = 'DESTROY'
                BEGIN
                    INSERT INTO whale_fall_items (log_id, char_id, user_item_id, item_name_id, amount, destination)
                    SELECT @log_id, @char_id, user_item_id, name_id, amount, 'DESTROYED' FROM #items
                    DELETE ui FROM user_item ui JOIN #items t ON ui.id = t.user_item_id
                END

                UPDATE whale_fall_log SET total_items = @total_items, total_value = @total_items * @broker_price WHERE log_id = @log_id
                DROP TABLE #items
                UPDATE user_data SET delete_date = DATEDIFF(SECOND, '1970-01-01', GETDATE()),
                    delete_type = CASE @fall_mode WHEN 'BROKER' THEN 101 WHEN 'KILLER' THEN 102 WHEN 'DESTROY' THEN 103 ELSE 100 END
                WHERE char_id = @char_id
                RETURN @total_items
            END
            """);
    }

    private void createTrigger(org.springframework.jdbc.core.JdbcTemplate jdbc) {
        jdbc.execute("""
            CREATE TRIGGER tr_whale_fall ON user_data AFTER UPDATE
            AS
            BEGIN
                SET NOCOUNT ON
                DECLARE @enabled BIT = 0
                SELECT @enabled = CAST(config_value AS BIT) FROM whale_fall_config WHERE config_name = 'system_enabled'
                IF @enabled = 0 RETURN
                IF NOT UPDATE(death_count) RETURN

                DECLARE @threshold INT = 1, @min_level INT = 0
                SELECT @threshold = CAST(config_value AS INT) FROM whale_fall_config WHERE config_name = 'death_threshold'
                SELECT @min_level = CAST(config_value AS INT) FROM whale_fall_config WHERE config_name = 'min_level'

                DECLARE @char_id INT
                DECLARE cur CURSOR LOCAL FAST_FORWARD FOR
                    SELECT i.char_id FROM inserted i JOIN deleted d ON i.char_id = d.char_id
                    WHERE i.death_count >= @threshold AND d.death_count < i.death_count AND i.delete_date = 0 AND i.lev >= @min_level
                OPEN cur
                FETCH NEXT FROM cur INTO @char_id
                WHILE @@FETCH_STATUS = 0
                BEGIN
                    EXEC sp_whale_fall_execute @char_id
                    FETCH NEXT FROM cur INTO @char_id
                END
                CLOSE cur
                DEALLOCATE cur
            END
            """);
    }

    private void showTestDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("测试鲸落");
        dialog.setHeaderText("输入角色ID进行鲸落测试");
        dialog.setContentText("角色ID:");

        dialog.showAndWait().ifPresent(charIdStr -> {
            try {
                int charId = Integer.parseInt(charIdStr);
                connection.getJdbcTemplate().update("EXEC sp_whale_fall_execute ?", charId);
                setStatus("鲸落测试完成: 角色 " + charId, Color.LIGHTGREEN);
                loadLogs();
            } catch (Exception e) {
                setStatus("测试失败: " + e.getMessage(), Color.RED);
            }
        });
    }

    private void showKillRecordDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("记录击杀");
        dialog.setHeaderText("记录PVP击杀信息 (用于击杀继承模式)");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField victimField = new TextField();
        victimField.setPromptText("被杀者角色ID");
        TextField killerField = new TextField();
        killerField.setPromptText("击杀者角色ID");

        grid.add(new Label("被杀者ID:"), 0, 0);
        grid.add(victimField, 1, 0);
        grid.add(new Label("击杀者ID:"), 0, 1);
        grid.add(killerField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    int victimId = Integer.parseInt(victimField.getText());
                    int killerId = Integer.parseInt(killerField.getText());
                    connection.getJdbcTemplate().update(
                            "EXEC sp_whale_fall_record_kill ?, ?", victimId, killerId);
                    setStatus("击杀记录已添加", Color.LIGHTGREEN);
                } catch (Exception e) {
                    setStatus("记录失败: " + e.getMessage(), Color.RED);
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void showLogDetail(Map<String, Object> logEntry) {
        try {
            Object logIdObj = logEntry.get("log_id");
            if (logIdObj == null) return;

            long logId = ((Number) logIdObj).longValue();

            List<Map<String, Object>> items = connection.getJdbcTemplate().queryForList(
                    "SELECT * FROM whale_fall_items WHERE log_id = ? ORDER BY item_log_id", logId);

            StringBuilder sb = new StringBuilder();
            sb.append("角色: ").append(logEntry.get("char_name")).append("\n");
            sb.append("模式: ").append(logEntry.get("fall_mode")).append("\n");
            sb.append("时间: ").append(logEntry.get("fall_time")).append("\n\n");
            sb.append("物品列表:\n");
            sb.append("-".repeat(50)).append("\n");

            for (Map<String, Object> item : items) {
                sb.append("  物品ID: ").append(item.get("item_name_id"));
                sb.append(" x").append(item.get("amount"));
                sb.append(" -> ").append(item.get("destination"));
                sb.append("\n");
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("鲸落详情");
            alert.setHeaderText("日志 #" + logId);

            TextArea textArea = new TextArea(sb.toString());
            textArea.setEditable(false);
            textArea.setPrefRowCount(20);
            alert.getDialogPane().setContent(textArea);
            alert.showAndWait();
        } catch (Exception e) {
            log.error("获取日志详情失败", e);
        }
    }

    private void setStatus(String message, Color color) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setTextFill(color);
        });
    }
}
