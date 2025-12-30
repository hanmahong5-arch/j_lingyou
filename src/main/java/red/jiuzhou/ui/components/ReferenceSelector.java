package red.jiuzhou.ui.components;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import red.jiuzhou.pattern.model.PatternField;
import red.jiuzhou.util.DatabaseUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 引用字段选择器
 *
 * <p>专为游戏数据引用字段设计的智能下拉选择器。自动从引用表加载数据，
 * 显示格式为"ID - 名称"，支持搜索过滤，异步加载确保UI不卡顿。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>自动从引用目标表加载数据（如 items 表）</li>
 *   <li>显示格式：ID - 名称（如 "100001 - 火焰长剑"）</li>
 *   <li>支持实时搜索过滤（输入时自动过滤列表）</li>
 *   <li>异步加载数据（使用虚拟线程，不阻塞UI）</li>
 *   <li>分页限制（默认1000条，防止数据量过大）</li>
 *   <li>智能表名提取（从引用目标字符串解析）</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>任务系统选择奖励物品（item_id → items表）</li>
 *   <li>NPC配置选择技能（skill_id → skill_base表）</li>
 *   <li>副本设置选择Boss（npc_id → npc表）</li>
 *   <li>商店配置选择商品（item_id → items表）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 方式1：从PatternField创建
 * PatternField field = ...;  // referenceTarget = "items.id"
 * ReferenceSelector selector = new ReferenceSelector(field);
 *
 * // 方式2：直接指定目标表
 * ReferenceSelector selector = new ReferenceSelector("items", "npc");
 *
 * // 获取选中的ID
 * String selectedId = selector.getSelectedId();
 * }</pre>
 *
 * @author Claude
 * @version 1.0
 * @since 2025-12-30
 */
public class ReferenceSelector extends ComboBox<ReferenceSelector.ReferenceOption> {
    private static final Logger log = LoggerFactory.getLogger(ReferenceSelector.class);

    private final JdbcTemplate jdbcTemplate;
    private final String targetTable;
    private final String displayName;
    private final int pageLimit;

    private ObservableList<ReferenceOption> allOptions;
    private String currentFilter = "";

    /** 默认分页限制 */
    private static final int DEFAULT_PAGE_LIMIT = 1000;

    // ========== 构造函数 ==========

    /**
     * 从PatternField创建引用选择器
     *
     * @param field 字段定义（必须包含referenceTarget）
     */
    public ReferenceSelector(PatternField field) {
        this(extractTableName(field.getReferenceTarget()), field.getFieldName());
    }

    /**
     * 直接指定目标表创建引用选择器
     *
     * @param targetTable 目标表名（如 "items"）
     * @param displayName 显示名称（用于日志和提示）
     */
    public ReferenceSelector(String targetTable, String displayName) {
        this(targetTable, displayName, DEFAULT_PAGE_LIMIT);
    }

    /**
     * 完整构造函数
     *
     * @param targetTable 目标表名
     * @param displayName 显示名称
     * @param pageLimit 分页限制
     */
    public ReferenceSelector(String targetTable, String displayName, int pageLimit) {
        this.jdbcTemplate = DatabaseUtil.getJdbcTemplate();
        this.targetTable = targetTable;
        this.displayName = displayName;
        this.pageLimit = pageLimit;
        this.allOptions = FXCollections.observableArrayList();

        initialize();
    }

    // ========== 初始化 ==========

    /**
     * 初始化选择器
     */
    private void initialize() {
        // 设置为可编辑（启用搜索）
        setEditable(true);

        // 设置提示
        setPromptText("搜索或选择...");
        setTooltip(new Tooltip(String.format("从 %s 表选择（共 %d 条）", targetTable, pageLimit)));

        // 设置单元格渲染器
        setCellFactory(param -> new ReferenceCell());
        setButtonCell(new ReferenceCell());

        // 配置搜索过滤
        setupSearchFilter();

        // 异步加载数据
        loadDataAsync();

        // 设置样式
        setMaxWidth(Double.MAX_VALUE);
        setPrefWidth(300);
    }

    /**
     * 配置搜索过滤
     */
    private void setupSearchFilter() {
        getEditor().textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue.equals(currentFilter)) {
                return;
            }

            currentFilter = newValue;
            filterOptions(newValue);
        });

        // 失去焦点时恢复选中项的显示
        getEditor().focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                ReferenceOption selected = getValue();
                if (selected != null) {
                    getEditor().setText(selected.toString());
                }
            }
        });
    }

    /**
     * 异步加载数据
     */
    private void loadDataAsync() {
        Task<List<ReferenceOption>> task = new Task<>() {
            @Override
            protected List<ReferenceOption> call() {
                return loadDataFromDatabase();
            }
        };

        task.setOnSucceeded(event -> {
            List<ReferenceOption> options = task.getValue();
            allOptions.setAll(options);
            setItems(allOptions);
            log.info("引用数据加载成功: 表={}, 记录数={}", targetTable, options.size());
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();
            log.error("引用数据加载失败: 表={}", targetTable, error);
            setPromptText("加载失败");
            setTooltip(new Tooltip("加载数据失败: " + error.getMessage()));
        });

        // 使用虚拟线程执行（Java 21+特性）
        Thread.ofVirtual().start(task);
    }

    /**
     * 从数据库加载数据
     *
     * @return 引用选项列表
     */
    private List<ReferenceOption> loadDataFromDatabase() {
        try {
            // 尝试查询 id 和 name 字段
            String sql = String.format(
                    "SELECT id, name FROM %s ORDER BY id LIMIT %d",
                    targetTable, pageLimit
            );

            return jdbcTemplate.query(sql, (rs, rowNum) ->
                    new ReferenceOption(
                            rs.getString("id"),
                            rs.getString("name")
                    )
            );

        } catch (Exception e) {
            // 如果没有name字段，尝试其他常见名称字段
            log.warn("查询 name 字段失败，尝试其他字段: 表={}", targetTable);
            return loadDataWithAlternativeNameField();
        }
    }

    /**
     * 使用备选名称字段加载数据
     *
     * @return 引用选项列表
     */
    private List<ReferenceOption> loadDataWithAlternativeNameField() {
        String[] nameFields = {"name", "title", "desc", "description", "label", "code"};

        for (String nameField : nameFields) {
            try {
                String sql = String.format(
                        "SELECT id, %s as name FROM %s ORDER BY id LIMIT %d",
                        nameField, targetTable, pageLimit
                );

                List<ReferenceOption> options = jdbcTemplate.query(sql, (rs, rowNum) ->
                        new ReferenceOption(
                                rs.getString("id"),
                                rs.getString("name")
                        )
                );

                if (!options.isEmpty()) {
                    log.info("使用备选字段加载数据: 表={}, 字段={}", targetTable, nameField);
                    return options;
                }

            } catch (Exception ignored) {
            }
        }

        // 如果所有名称字段都失败，只加载ID
        log.warn("未找到名称字段，仅加载ID: 表={}", targetTable);
        return loadDataIdOnly();
    }

    /**
     * 仅加载ID（无名称字段）
     *
     * @return 引用选项列表
     */
    private List<ReferenceOption> loadDataIdOnly() {
        try {
            String sql = String.format(
                    "SELECT id FROM %s ORDER BY id LIMIT %d",
                    targetTable, pageLimit
            );

            return jdbcTemplate.query(sql, (rs, rowNum) ->
                    new ReferenceOption(
                            rs.getString("id"),
                            null  // 无名称
                    )
            );
        } catch (Exception e) {
            log.error("加载ID失败: 表={}", targetTable, e);
            return new ArrayList<>();
        }
    }

    /**
     * 过滤选项
     *
     * @param filter 过滤文本
     */
    private void filterOptions(String filter) {
        if (filter == null || filter.isEmpty()) {
            setItems(allOptions);
            return;
        }

        String lowerFilter = filter.toLowerCase();
        ObservableList<ReferenceOption> filtered = allOptions.stream()
                .filter(option -> option.matches(lowerFilter))
                .collect(Collectors.toCollection(FXCollections::observableArrayList));

        setItems(filtered);

        // 自动展开下拉列表
        if (!filtered.isEmpty() && !isShowing()) {
            show();
        }
    }

    // ========== 公共方法 ==========

    /**
     * 获取选中的ID
     *
     * @return 选中的ID，如果未选中则返回null
     */
    public String getSelectedId() {
        ReferenceOption selected = getValue();
        return selected != null ? selected.id : null;
    }

    /**
     * 根据ID设置选中项
     *
     * @param id 要选中的ID
     */
    public void setSelectedId(String id) {
        if (id == null || id.isEmpty()) {
            setValue(null);
            return;
        }

        for (ReferenceOption option : allOptions) {
            if (option.id.equals(id)) {
                setValue(option);
                return;
            }
        }

        log.warn("未找到匹配的引用项: 表={}, ID={}", targetTable, id);
    }

    /**
     * 刷新数据
     */
    public void refresh() {
        setValue(null);
        allOptions.clear();
        loadDataAsync();
    }

    /**
     * 获取目标表名
     *
     * @return 目标表名
     */
    public String getTargetTable() {
        return targetTable;
    }

    // ========== 静态工具方法 ==========

    /**
     * 从引用目标字符串提取表名
     * <p>支持格式：
     * <ul>
     *   <li>"items.id" → "items"</li>
     *   <li>"client_npcs_npc.id" → "client_npcs_npc"</li>
     *   <li>"items" → "items"</li>
     * </ul>
     *
     * @param referenceTarget 引用目标（如 "items.id"）
     * @return 表名
     */
    private static String extractTableName(String referenceTarget) {
        if (referenceTarget == null || referenceTarget.isEmpty()) {
            throw new IllegalArgumentException("引用目标不能为空");
        }

        // 移除 ".id" 后缀
        if (referenceTarget.contains(".")) {
            return referenceTarget.substring(0, referenceTarget.indexOf('.'));
        }

        return referenceTarget;
    }

    // ========== 内部类 ==========

    /**
     * 引用选项（ID + 名称）
     */
    public static class ReferenceOption {
        private final String id;
        private final String name;

        public ReferenceOption(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        /**
         * 检查是否匹配过滤条件
         *
         * @param filter 过滤文本（小写）
         * @return 是否匹配
         */
        public boolean matches(String filter) {
            if (id.toLowerCase().contains(filter)) {
                return true;
            }
            if (name != null && name.toLowerCase().contains(filter)) {
                return true;
            }
            return false;
        }

        @Override
        public String toString() {
            if (name != null && !name.isEmpty()) {
                return id + " - " + name;
            }
            return id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ReferenceOption other)) return false;
            return id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    /**
     * 引用单元格渲染器
     */
    private static class ReferenceCell extends ListCell<ReferenceOption> {
        @Override
        protected void updateItem(ReferenceOption item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
            } else {
                setText(item.toString());

                // 设置悬停提示
                if (item.name != null && !item.name.isEmpty()) {
                    setTooltip(new Tooltip(
                            String.format("ID: %s\n名称: %s", item.id, item.name)
                    ));
                } else {
                    setTooltip(new Tooltip("ID: " + item.id));
                }
            }
        }
    }
}
