package red.jiuzhou.ui.components;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 全局快捷键管理系统
 *
 * <p>为游戏设计师提供便捷的键盘快捷操作：
 * <ul>
 *   <li>全局快捷键注册和管理</li>
 *   <li>场景级别快捷键绑定</li>
 *   <li>快捷键冲突检测</li>
 *   <li>快捷键帮助面板</li>
 * </ul>
 *
 * <h3>预设快捷键：</h3>
 * <ul>
 *   <li>Ctrl+F - 搜索/查找</li>
 *   <li>Ctrl+S - 保存</li>
 *   <li>Ctrl+E - 导出</li>
 *   <li>Ctrl+R - 刷新</li>
 *   <li>Ctrl+G - 跳转到</li>
 *   <li>F1 - 帮助</li>
 *   <li>F5 - 刷新</li>
 *   <li>Escape - 关闭/取消</li>
 * </ul>
 *
 * @author Claude
 * @version 1.0
 */
public class HotkeyManager {

    private static final Logger log = LoggerFactory.getLogger(HotkeyManager.class);

    // 单例实例
    private static HotkeyManager instance;

    // 全局快捷键映射
    private final Map<KeyCombination, HotkeyAction> globalHotkeys = new ConcurrentHashMap<>();

    // 场景级别快捷键映射
    private final Map<Scene, Map<KeyCombination, HotkeyAction>> sceneHotkeys = new ConcurrentHashMap<>();

    // 快捷键分类
    private final Map<String, List<HotkeyInfo>> hotkeyCategories = new LinkedHashMap<>();

    // 是否启用
    private boolean enabled = true;

    /**
     * 快捷键动作
     */
    public static class HotkeyAction {
        private final String name;
        private final String description;
        private final Runnable action;
        private final String category;
        private boolean enabled = true;

        public HotkeyAction(String name, String description, Runnable action, String category) {
            this.name = name;
            this.description = description;
            this.action = action;
            this.category = category;
        }

        public void execute() {
            if (enabled && action != null) {
                action.run();
            }
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /**
     * 快捷键信息（用于帮助显示）
     */
    public static class HotkeyInfo {
        private final KeyCombination keyCombination;
        private final String name;
        private final String description;

        public HotkeyInfo(KeyCombination keyCombination, String name, String description) {
            this.keyCombination = keyCombination;
            this.name = name;
            this.description = description;
        }

        public KeyCombination getKeyCombination() { return keyCombination; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getKeyText() { return keyCombination.getDisplayText(); }
    }

    private HotkeyManager() {
        initDefaultCategories();
    }

    /**
     * 获取单例实例
     */
    public static synchronized HotkeyManager getInstance() {
        if (instance == null) {
            instance = new HotkeyManager();
        }
        return instance;
    }

    /**
     * 初始化默认分类
     */
    private void initDefaultCategories() {
        hotkeyCategories.put("通用", new ArrayList<>());
        hotkeyCategories.put("文件", new ArrayList<>());
        hotkeyCategories.put("编辑", new ArrayList<>());
        hotkeyCategories.put("视图", new ArrayList<>());
        hotkeyCategories.put("工具", new ArrayList<>());
        hotkeyCategories.put("帮助", new ArrayList<>());
    }

    /**
     * 注册全局快捷键
     */
    public void registerGlobal(KeyCombination combination, String name, String description,
                               String category, Runnable action) {
        if (combination == null || action == null) {
            log.warn("尝试注册无效的快捷键");
            return;
        }

        // 检查冲突
        if (globalHotkeys.containsKey(combination)) {
            log.warn("快捷键冲突: {} 已被 {} 占用", combination.getDisplayText(),
                    globalHotkeys.get(combination).getName());
        }

        HotkeyAction hotkeyAction = new HotkeyAction(name, description, action, category);
        globalHotkeys.put(combination, hotkeyAction);

        // 添加到分类
        if (!hotkeyCategories.containsKey(category)) {
            hotkeyCategories.put(category, new ArrayList<>());
        }
        hotkeyCategories.get(category).add(new HotkeyInfo(combination, name, description));

        log.debug("注册全局快捷键: {} -> {}", combination.getDisplayText(), name);
    }

    /**
     * 注册场景级别快捷键
     */
    public void registerForScene(Scene scene, KeyCombination combination, String name,
                                 String description, String category, Runnable action) {
        if (scene == null || combination == null || action == null) {
            log.warn("尝试注册无效的场景快捷键");
            return;
        }

        sceneHotkeys.computeIfAbsent(scene, k -> new ConcurrentHashMap<>());

        HotkeyAction hotkeyAction = new HotkeyAction(name, description, action, category);
        sceneHotkeys.get(scene).put(combination, hotkeyAction);

        log.debug("为场景注册快捷键: {} -> {}", combination.getDisplayText(), name);
    }

    /**
     * 绑定快捷键到场景
     */
    public void bindToScene(Scene scene) {
        if (scene == null) return;

        scene.setOnKeyPressed(event -> {
            if (!enabled) return;

            // 构建按键组合
            KeyCombination.ModifierValue ctrl = event.isControlDown() ?
                    KeyCombination.ModifierValue.DOWN : KeyCombination.ModifierValue.UP;
            KeyCombination.ModifierValue shift = event.isShiftDown() ?
                    KeyCombination.ModifierValue.DOWN : KeyCombination.ModifierValue.UP;
            KeyCombination.ModifierValue alt = event.isAltDown() ?
                    KeyCombination.ModifierValue.DOWN : KeyCombination.ModifierValue.UP;

            KeyCodeCombination pressed = new KeyCodeCombination(event.getCode(), shift, ctrl, alt,
                    KeyCombination.ModifierValue.UP, KeyCombination.ModifierValue.UP);

            // 先检查场景级别快捷键
            Map<KeyCombination, HotkeyAction> sceneMap = sceneHotkeys.get(scene);
            if (sceneMap != null) {
                for (Map.Entry<KeyCombination, HotkeyAction> entry : sceneMap.entrySet()) {
                    if (entry.getKey().match(event)) {
                        entry.getValue().execute();
                        event.consume();
                        log.debug("执行场景快捷键: {}", entry.getValue().getName());
                        return;
                    }
                }
            }

            // 检查全局快捷键
            for (Map.Entry<KeyCombination, HotkeyAction> entry : globalHotkeys.entrySet()) {
                if (entry.getKey().match(event)) {
                    entry.getValue().execute();
                    event.consume();
                    log.debug("执行全局快捷键: {}", entry.getValue().getName());
                    return;
                }
            }
        });

        log.info("已绑定快捷键监听到场景");
    }

    /**
     * 绑定快捷键到Stage
     */
    public void bindToStage(Stage stage) {
        if (stage != null && stage.getScene() != null) {
            bindToScene(stage.getScene());
        }

        // 监听场景变化
        if (stage != null) {
            stage.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    bindToScene(newScene);
                }
            });
        }
    }

    /**
     * 注销场景的所有快捷键
     */
    public void unregisterScene(Scene scene) {
        if (scene != null) {
            sceneHotkeys.remove(scene);
            scene.setOnKeyPressed(null);
        }
    }

    /**
     * 注销指定快捷键
     */
    public void unregister(KeyCombination combination) {
        HotkeyAction removed = globalHotkeys.remove(combination);
        if (removed != null) {
            // 从分类中移除
            for (List<HotkeyInfo> infos : hotkeyCategories.values()) {
                infos.removeIf(info -> info.getKeyCombination().equals(combination));
            }
            log.debug("注销全局快捷键: {}", combination.getDisplayText());
        }
    }

    /**
     * 获取所有快捷键信息（按分类）
     */
    public Map<String, List<HotkeyInfo>> getAllHotkeysByCategory() {
        return Collections.unmodifiableMap(hotkeyCategories);
    }

    /**
     * 获取快捷键帮助文本
     */
    public String getHelpText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 快捷键帮助 ===\n\n");

        for (Map.Entry<String, List<HotkeyInfo>> entry : hotkeyCategories.entrySet()) {
            if (entry.getValue().isEmpty()) continue;

            sb.append("【").append(entry.getKey()).append("】\n");
            for (HotkeyInfo info : entry.getValue()) {
                sb.append(String.format("  %-15s  %s\n", info.getKeyText(), info.getName()));
                if (info.getDescription() != null && !info.getDescription().isEmpty()) {
                    sb.append("                    ").append(info.getDescription()).append("\n");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 启用/禁用所有快捷键
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        log.info("快捷键系统 {}", enabled ? "已启用" : "已禁用");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 清除所有快捷键
     */
    public void clearAll() {
        globalHotkeys.clear();
        sceneHotkeys.clear();
        for (List<HotkeyInfo> infos : hotkeyCategories.values()) {
            infos.clear();
        }
        log.info("已清除所有快捷键");
    }

    // ==================== 便捷方法 ====================

    /**
     * 注册 Ctrl+Key 快捷键
     */
    public void registerCtrl(KeyCode key, String name, String description,
                             String category, Runnable action) {
        KeyCombination combination = new KeyCodeCombination(key, KeyCombination.CONTROL_DOWN);
        registerGlobal(combination, name, description, category, action);
    }

    /**
     * 注册 Ctrl+Shift+Key 快捷键
     */
    public void registerCtrlShift(KeyCode key, String name, String description,
                                  String category, Runnable action) {
        KeyCombination combination = new KeyCodeCombination(key,
                KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
        registerGlobal(combination, name, description, category, action);
    }

    /**
     * 注册 Alt+Key 快捷键
     */
    public void registerAlt(KeyCode key, String name, String description,
                            String category, Runnable action) {
        KeyCombination combination = new KeyCodeCombination(key, KeyCombination.ALT_DOWN);
        registerGlobal(combination, name, description, category, action);
    }

    /**
     * 注册功能键快捷键
     */
    public void registerFunctionKey(KeyCode key, String name, String description,
                                    String category, Runnable action) {
        KeyCombination combination = new KeyCodeCombination(key);
        registerGlobal(combination, name, description, category, action);
    }

    // ==================== 预设快捷键注册 ====================

    /**
     * 注册默认快捷键
     */
    public void registerDefaults(DefaultHotkeyHandler handler) {
        // 通用
        registerCtrl(KeyCode.F, "搜索", "打开搜索对话框", "通用", handler::onSearch);
        registerCtrl(KeyCode.G, "跳转到", "跳转到指定位置", "通用", handler::onGoto);
        registerFunctionKey(KeyCode.F5, "刷新", "刷新当前视图", "通用", handler::onRefresh);
        registerFunctionKey(KeyCode.ESCAPE, "关闭/取消", "关闭当前对话框或取消操作", "通用", handler::onEscape);

        // 文件
        registerCtrl(KeyCode.S, "保存", "保存当前更改", "文件", handler::onSave);
        registerCtrl(KeyCode.E, "导出", "导出数据", "文件", handler::onExport);
        registerCtrl(KeyCode.O, "打开", "打开文件", "文件", handler::onOpen);

        // 编辑
        registerCtrl(KeyCode.Z, "撤销", "撤销上一步操作", "编辑", handler::onUndo);
        registerCtrlShift(KeyCode.Z, "重做", "重做撤销的操作", "编辑", handler::onRedo);
        registerCtrl(KeyCode.A, "全选", "选择所有内容", "编辑", handler::onSelectAll);

        // 视图
        registerCtrl(KeyCode.DIGIT1, "机制浏览器", "打开机制浏览器", "视图", handler::onMechanismExplorer);
        registerCtrl(KeyCode.DIGIT2, "设计洞察", "打开设计洞察面板", "视图", handler::onDesignerInsight);
        registerCtrl(KeyCode.DIGIT3, "数据操作", "打开数据操作中心", "视图", handler::onDataOperation);

        // 帮助
        registerFunctionKey(KeyCode.F1, "帮助", "显示帮助信息", "帮助", handler::onHelp);
        registerCtrl(KeyCode.SLASH, "快捷键", "显示快捷键列表", "帮助", handler::onShowHotkeys);

        log.info("已注册默认快捷键");
    }

    /**
     * 默认快捷键处理接口
     */
    public interface DefaultHotkeyHandler {
        default void onSearch() {}
        default void onGoto() {}
        default void onRefresh() {}
        default void onEscape() {}
        default void onSave() {}
        default void onExport() {}
        default void onOpen() {}
        default void onUndo() {}
        default void onRedo() {}
        default void onSelectAll() {}
        default void onMechanismExplorer() {}
        default void onDesignerInsight() {}
        default void onDataOperation() {}
        default void onHelp() {}
        default void onShowHotkeys() {}
    }
}
