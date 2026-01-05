package red.jiuzhou.agent.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import red.jiuzhou.analysis.aion.AionMechanismCategory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 领域知识卡片面板
 *
 * <p>将游戏领域知识以卡片形式展示，帮助设计师快速查阅和应用。
 *
 * <p>知识卡片类型：
 * <ul>
 *   <li>语义映射卡片 - 游戏术语到数据库值的映射（品质、元素、职业）</li>
 *   <li>机制分类卡片 - 27个Aion机制的说明和相关表</li>
 *   <li>ID引用卡片 - 9个ID系统的跨表关系</li>
 *   <li>SQL示例卡片 - Few-Shot库中的常用查询</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class DomainKnowledgeCards extends VBox {

    // ==================== 卡片分类枚举 ====================

    public enum CardCategory {
        ALL("全部", "📚"),
        QUALITY("品质", "⭐"),
        ELEMENT("元素", "🔥"),
        CLASS("职业", "👤"),
        MECHANISM("机制", "🎮"),
        ID_SYSTEM("ID系统", "🔗"),
        SQL_EXAMPLE("SQL示例", "📊");

        private final String displayName;
        private final String icon;

        CardCategory(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() { return displayName; }
        public String getIcon() { return icon; }
    }

    // ==================== 知识卡片数据结构 ====================

    public record KnowledgeCard(
        String id,
        String title,
        String icon,
        CardCategory category,
        Map<String, String> mappings,
        String description,
        String sql  // 可执行的SQL（可选）
    ) {}

    // ==================== UI组件 ====================

    private final ComboBox<CardCategory> categoryFilter;
    private final TextField searchField;
    private final FlowPane cardsContainer;
    private final Label statusLabel;

    // ==================== 状态 ====================

    private final List<KnowledgeCard> allCards = new ArrayList<>();
    private CardCategory currentCategory = CardCategory.ALL;
    private String currentSearchText = "";

    // ==================== 回调 ====================

    private Consumer<String> onCopyClicked;
    private Consumer<String> onInsertClicked;
    private Consumer<String> onExecuteClicked;

    // ==================== 构造器 ====================

    public DomainKnowledgeCards() {
        this.setSpacing(12);
        this.setPadding(new Insets(12));
        this.getStyleClass().add("domain-knowledge-cards");
        this.setStyle("-fx-background-color: #FAFAFA;");

        // 标题
        Label titleLabel = new Label("领域知识库");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        HBox titleBox = new HBox(8, new Label("📚"), titleLabel);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        // ==================== 过滤器区域 ====================
        Label filterLabel = new Label("🏷️ 分类:");
        filterLabel.setStyle("-fx-font-size: 12px;");

        categoryFilter = new ComboBox<>();
        categoryFilter.getItems().addAll(CardCategory.values());
        categoryFilter.setValue(CardCategory.ALL);
        categoryFilter.setOnAction(e -> filterCards());

        // 自定义显示
        categoryFilter.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CardCategory item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getIcon() + " " + item.getDisplayName());
                }
            }
        });
        categoryFilter.setButtonCell(categoryFilter.getCellFactory().call(null));

        searchField = new TextField();
        searchField.setPromptText("搜索知识...");
        searchField.setPrefWidth(150);
        searchField.textProperty().addListener((obs, old, newVal) -> {
            currentSearchText = newVal;
            filterCards();
        });

        HBox filterBox = new HBox(12, filterLabel, categoryFilter, searchField);
        filterBox.setAlignment(Pos.CENTER_LEFT);

        // ==================== 卡片容器 ====================
        cardsContainer = new FlowPane();
        cardsContainer.setHgap(12);
        cardsContainer.setVgap(12);
        cardsContainer.setPrefWrapLength(600);

        ScrollPane scrollPane = new ScrollPane(cardsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // 状态标签
        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #999;");

        // ==================== 组装面板 ====================
        this.getChildren().addAll(titleBox, filterBox, scrollPane, statusLabel);

        // 初始化卡片数据
        initializeCards();
        displayCards();
    }

    // ==================== 初始化卡片数据 ====================

    private void initializeCards() {
        // 品质映射卡片
        allCards.add(new KnowledgeCard(
            "quality_mapping",
            "品质等级",
            "⭐",
            CardCategory.QUALITY,
            Map.of(
                "1", "白装 (普通)",
                "2", "绿装 (优秀)",
                "3", "紫装 (稀有)",
                "4", "橙装 (史诗)",
                "5", "传说 (神话)",
                "6", "唯一"
            ),
            "游戏物品品质等级对应的数值",
            "SELECT * FROM client_items WHERE quality = 3 LIMIT 100"
        ));

        // 元素属性卡片
        allCards.add(new KnowledgeCard(
            "element_mapping",
            "元素属性",
            "🔥",
            CardCategory.ELEMENT,
            Map.of(
                "fire", "火属性",
                "water", "水属性",
                "earth", "土属性",
                "wind", "风属性",
                "light", "光属性",
                "dark", "暗属性"
            ),
            "游戏元素属性的英文标识",
            null
        ));

        // 职业类型卡片
        allCards.add(new KnowledgeCard(
            "class_mapping",
            "职业类型",
            "👤",
            CardCategory.CLASS,
            Map.of(
                "1", "战士",
                "2", "法师",
                "3", "牧师",
                "4", "游侠",
                "5", "刺客",
                "6", "守护"
            ),
            "游戏职业类型对应的数值",
            "SELECT * FROM client_skill_base WHERE class_type = 1 LIMIT 100"
        ));

        // ID系统卡片
        addIdSystemCards();

        // 机制分类卡片
        addMechanismCards();

        // SQL示例卡片
        addSqlExampleCards();
    }

    private void addIdSystemCards() {
        Map<String, String> idSystems = Map.of(
            "item_id", "client_items",
            "npc_id", "client_npcs_npc",
            "quest_id", "client_quest_base",
            "skill_id", "client_skill_base",
            "drop_id", "client_drop_base",
            "recipe_id", "client_recipe_base",
            "zone_id", "client_worldmaps",
            "dialog_id", "client_dialogs",
            "spawn_id", "client_spawns"
        );

        allCards.add(new KnowledgeCard(
            "id_systems",
            "ID引用系统",
            "🔗",
            CardCategory.ID_SYSTEM,
            idSystems,
            "常用的ID字段及其对应的主表",
            null
        ));
    }

    private void addMechanismCards() {
        // 添加主要机制的卡片
        Map<String, String> mainMechanisms = new LinkedHashMap<>();
        mainMechanisms.put("ITEM", "物品系统 - 装备、道具、材料");
        mainMechanisms.put("NPC", "NPC系统 - 怪物、商人、任务NPC");
        mainMechanisms.put("QUEST", "任务系统 - 主线、支线、日常");
        mainMechanisms.put("SKILL", "技能系统 - 主动、被动、烙印");
        mainMechanisms.put("DROP", "掉落系统 - 怪物掉落配置");
        mainMechanisms.put("SHOP", "商店系统 - NPC商店、游戏币商店");
        mainMechanisms.put("INSTANCE", "副本系统 - 地下城、战场");
        mainMechanisms.put("HOUSING", "房屋系统 - 家具、装饰");

        allCards.add(new KnowledgeCard(
            "mechanism_overview",
            "27个游戏机制",
            "🎮",
            CardCategory.MECHANISM,
            mainMechanisms,
            "Aion游戏的主要机制分类概览",
            null
        ));

        // 为每个主要机制添加详细卡片
        for (AionMechanismCategory category : AionMechanismCategory.values()) {
            if (category == AionMechanismCategory.OTHER) continue;

            Map<String, String> details = new LinkedHashMap<>();
            details.put("名称", category.getDisplayName());
            details.put("描述", category.getDescription());
            details.put("优先级", String.valueOf(category.getPriority()));

            allCards.add(new KnowledgeCard(
                "mechanism_" + category.name().toLowerCase(),
                category.getDisplayName(),
                category.getIcon(),
                CardCategory.MECHANISM,
                details,
                category.getDescription(),
                null
            ));
        }
    }

    private void addSqlExampleCards() {
        // 查询示例
        allCards.add(new KnowledgeCard(
            "sql_query_quality",
            "按品质查询",
            "📊",
            CardCategory.SQL_EXAMPLE,
            Map.of(
                "紫装", "SELECT * FROM client_items WHERE quality = 3 LIMIT 100",
                "橙装", "SELECT * FROM client_items WHERE quality = 4 LIMIT 100",
                "高等级", "SELECT * FROM client_items WHERE level > 50 LIMIT 100"
            ),
            "按品质等级查询物品的SQL示例",
            "SELECT * FROM client_items WHERE quality = 3 LIMIT 100"
        ));

        allCards.add(new KnowledgeCard(
            "sql_join_example",
            "关联查询",
            "📊",
            CardCategory.SQL_EXAMPLE,
            Map.of(
                "物品与掉落", "SELECT i.*, d.drop_rate FROM client_items i JOIN client_drop_base d ON i.id = d.item_id",
                "NPC与任务", "SELECT n.*, q.quest_name FROM client_npcs_npc n JOIN client_quest_base q ON n.id = q.npc_id"
            ),
            "多表关联查询的SQL示例",
            null
        ));

        allCards.add(new KnowledgeCard(
            "sql_statistics",
            "统计查询",
            "📊",
            CardCategory.SQL_EXAMPLE,
            Map.of(
                "品质分布", "SELECT quality, COUNT(*) as count FROM client_items GROUP BY quality",
                "等级分布", "SELECT level, COUNT(*) as count FROM client_items GROUP BY level ORDER BY level",
                "职业技能", "SELECT class_type, COUNT(*) as skill_count FROM client_skill_base GROUP BY class_type"
            ),
            "数据统计分析的SQL示例",
            "SELECT quality, COUNT(*) as count FROM client_items GROUP BY quality"
        ));
    }

    // ==================== 公共方法 ====================

    /**
     * 显示特定分类
     */
    public void showCategory(CardCategory category) {
        this.currentCategory = category;
        categoryFilter.setValue(category);
        filterCards();
    }

    /**
     * 搜索过滤卡片
     */
    public void filterCards(String keyword) {
        this.currentSearchText = keyword;
        searchField.setText(keyword);
        filterCards();
    }

    /**
     * 展开指定卡片
     */
    public void expandCard(String cardId) {
        // 找到卡片并展开详情
        for (var node : cardsContainer.getChildren()) {
            if (node.getUserData() != null && node.getUserData().equals(cardId)) {
                // 触发点击展开
                if (node instanceof VBox cardBox) {
                    showCardDetail(findCardById(cardId));
                }
                break;
            }
        }
    }

    /**
     * 设置复制回调
     */
    public void setOnCopyClicked(Consumer<String> callback) {
        this.onCopyClicked = callback;
    }

    /**
     * 设置插入回调
     */
    public void setOnInsertClicked(Consumer<String> callback) {
        this.onInsertClicked = callback;
    }

    /**
     * 设置执行回调
     */
    public void setOnExecuteClicked(Consumer<String> callback) {
        this.onExecuteClicked = callback;
    }

    /**
     * 刷新卡片（从数据库或配置重新加载）
     */
    public void refreshFromDatabase() {
        // 这里可以从数据库动态加载新的知识
        // 当前实现使用静态数据
        displayCards();
    }

    /**
     * 添加自定义卡片
     */
    public void addCard(KnowledgeCard card) {
        allCards.add(card);
        filterCards();
    }

    // ==================== 内部方法 ====================

    /**
     * 过滤并显示卡片
     */
    private void filterCards() {
        currentCategory = categoryFilter.getValue();
        displayCards();
    }

    /**
     * 显示卡片
     */
    private void displayCards() {
        cardsContainer.getChildren().clear();

        List<KnowledgeCard> filteredCards = allCards.stream()
            .filter(card -> currentCategory == CardCategory.ALL || card.category() == currentCategory)
            .filter(card -> currentSearchText.isEmpty() ||
                    card.title().toLowerCase().contains(currentSearchText.toLowerCase()) ||
                    card.description().toLowerCase().contains(currentSearchText.toLowerCase()) ||
                    card.mappings().values().stream().anyMatch(v -> v.toLowerCase().contains(currentSearchText.toLowerCase())))
            .collect(Collectors.toList());

        for (KnowledgeCard card : filteredCards) {
            cardsContainer.getChildren().add(createCardNode(card));
        }

        statusLabel.setText("显示 " + filteredCards.size() + " / " + allCards.size() + " 个卡片");
    }

    /**
     * 创建卡片节点
     */
    private VBox createCardNode(KnowledgeCard card) {
        VBox cardBox = new VBox(6);
        cardBox.setPrefWidth(180);
        cardBox.setMinHeight(120);
        cardBox.setPadding(new Insets(10));
        cardBox.setStyle("-fx-background-color: white; -fx-background-radius: 8; " +
                        "-fx-border-color: #E0E0E0; -fx-border-radius: 8; -fx-cursor: hand;");
        cardBox.setUserData(card.id());

        // 标题行
        Label iconLabel = new Label(card.icon());
        iconLabel.setStyle("-fx-font-size: 16px;");

        Label titleLabel = new Label(card.title());
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        HBox titleRow = new HBox(6, iconLabel, titleLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        // 内容区域 - 显示前几个映射
        VBox contentBox = new VBox(2);
        int count = 0;
        for (Map.Entry<String, String> entry : card.mappings().entrySet()) {
            if (count >= 4) {
                Label moreLabel = new Label("...");
                moreLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 10px;");
                contentBox.getChildren().add(moreLabel);
                break;
            }
            String text = entry.getKey() + " = " + entry.getValue();
            if (text.length() > 20) {
                text = text.substring(0, 17) + "...";
            }
            Label mappingLabel = new Label(text);
            mappingLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
            contentBox.getChildren().add(mappingLabel);
            count++;
        }

        // 操作按钮
        Button copyBtn = new Button("复制");
        copyBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 8;");
        copyBtn.setOnAction(e -> {
            e.consume();
            copyCardContent(card);
        });

        Button insertBtn = new Button("插入");
        insertBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 8;");
        insertBtn.setOnAction(e -> {
            e.consume();
            if (onInsertClicked != null) {
                onInsertClicked.accept(formatCardForInsert(card));
            }
        });

        HBox buttonRow = new HBox(6, copyBtn, insertBtn);

        // 如果有SQL，添加执行按钮
        if (card.sql() != null && !card.sql().isEmpty()) {
            Button executeBtn = new Button("执行");
            executeBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 8; -fx-background-color: #4CAF50; -fx-text-fill: white;");
            executeBtn.setOnAction(e -> {
                e.consume();
                if (onExecuteClicked != null) {
                    onExecuteClicked.accept(card.sql());
                }
            });
            buttonRow.getChildren().add(executeBtn);
        }

        buttonRow.setAlignment(Pos.CENTER_LEFT);

        cardBox.getChildren().addAll(titleRow, contentBox, buttonRow);
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        // 点击展开详情
        cardBox.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                showCardDetail(card);
            }
        });

        // 悬停效果
        cardBox.setOnMouseEntered(e -> cardBox.setStyle(cardBox.getStyle().replace("-fx-background-color: white;", "-fx-background-color: #f8f9fa;")));
        cardBox.setOnMouseExited(e -> cardBox.setStyle(cardBox.getStyle().replace("-fx-background-color: #f8f9fa;", "-fx-background-color: white;")));

        return cardBox;
    }

    /**
     * 复制卡片内容到剪贴板
     */
    private void copyCardContent(KnowledgeCard card) {
        StringBuilder sb = new StringBuilder();
        sb.append(card.title()).append("\n");
        sb.append("---\n");
        for (Map.Entry<String, String> entry : card.mappings().entrySet()) {
            sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);

        if (onCopyClicked != null) {
            onCopyClicked.accept(sb.toString());
        }

        // 显示提示
        showToast("已复制到剪贴板");
    }

    /**
     * 格式化卡片内容用于插入
     */
    private String formatCardForInsert(KnowledgeCard card) {
        if (card.sql() != null && !card.sql().isEmpty()) {
            return card.sql();
        }
        // 返回第一个映射值
        return card.mappings().values().stream().findFirst().orElse("");
    }

    /**
     * 显示卡片详情对话框
     */
    private void showCardDetail(KnowledgeCard card) {
        if (card == null) return;

        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle(card.icon() + " " + card.title());
        dialog.setHeaderText(card.description());

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));

        // 显示所有映射
        for (Map.Entry<String, String> entry : card.mappings().entrySet()) {
            HBox row = new HBox(8);
            Label keyLabel = new Label(entry.getKey());
            keyLabel.setStyle("-fx-font-weight: bold;");
            Label valueLabel = new Label("= " + entry.getValue());
            row.getChildren().addAll(keyLabel, valueLabel);
            content.getChildren().add(row);
        }

        // 如果有SQL，显示SQL
        if (card.sql() != null && !card.sql().isEmpty()) {
            Label sqlLabel = new Label("SQL示例:");
            sqlLabel.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 0 0;");

            TextArea sqlArea = new TextArea(card.sql());
            sqlArea.setEditable(false);
            sqlArea.setPrefRowCount(3);
            sqlArea.setStyle("-fx-font-family: monospace;");

            content.getChildren().addAll(sqlLabel, sqlArea);
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(500);
        dialog.showAndWait();
    }

    /**
     * 根据ID查找卡片
     */
    private KnowledgeCard findCardById(String id) {
        return allCards.stream()
            .filter(c -> c.id().equals(id))
            .findFirst()
            .orElse(null);
    }

    /**
     * 显示临时提示
     */
    private void showToast(String message) {
        Label toast = new Label(message);
        toast.setStyle("-fx-background-color: #333; -fx-text-fill: white; -fx-padding: 8 16; " +
                      "-fx-background-radius: 4;");

        // 添加到面板顶部
        if (!this.getChildren().contains(toast)) {
            this.getChildren().add(1, toast);

            // 2秒后移除
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Platform.runLater(() -> this.getChildren().remove(toast));
                } catch (InterruptedException ignored) {}
            }).start();
        }
    }

    /**
     * 获取所有卡片
     */
    public List<KnowledgeCard> getAllCards() {
        return new ArrayList<>(allCards);
    }

    /**
     * 获取指定分类的卡片数量
     */
    public int getCardCount(CardCategory category) {
        if (category == CardCategory.ALL) {
            return allCards.size();
        }
        return (int) allCards.stream().filter(c -> c.category() == category).count();
    }
}
