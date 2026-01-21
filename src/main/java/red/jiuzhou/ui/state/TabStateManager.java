package red.jiuzhou.ui.state;

import cn.hutool.core.io.FileUtil;
import javafx.scene.control.Tab;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.PathUtil;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tab 状态管理器 - 单一真理来源 (Single Source of Truth)
 *
 * 设计原理:
 * - 信息论: 将系统熵从 O(n) 降到 O(1)，n 为状态副本数
 * - 控制论: 提供闭环反馈，状态变更通知所有监听器
 * - 单一职责: 状态管理集中，业务逻辑分散
 *
 * 使用场景:
 * - MenuTabPaneExample: 用户切换文件时调用 updateFilePath()
 * - PaginatedTable: 通过 getFilePath() 获取当前文件路径
 * - TabPaneController: 监听状态变更，刷新 UI
 */
public class TabStateManager {
    private static final Logger log = LoggerFactory.getLogger(TabStateManager.class);

    private static final TabStateManager INSTANCE = new TabStateManager();

    // WeakHashMap: Tab 被关闭后自动清理状态，避免内存泄漏
    private final Map<Tab, TabState> states = Collections.synchronizedMap(new WeakHashMap<>());

    // 状态变更监听器（线程安全）
    private final List<StateChangeListener> listeners = new CopyOnWriteArrayList<>();

    private TabStateManager() {
        log.info("TabStateManager 初始化");
    }

    /**
     * 获取单例实例
     */
    public static TabStateManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取 Tab 的当前文件路径
     *
     * @param tab 目标 Tab
     * @return 文件路径，如果未注册则返回 null
     */
    public String getFilePath(Tab tab) {
        TabState state = states.get(tab);
        return state != null ? state.filePath() : null;
    }

    /**
     * 获取派生的配置文件路径
     *
     * 派生状态自动计算，无需手动同步
     *
     * @param tab 目标 Tab
     * @return 配置文件目录路径
     */
    public String getConfigPath(Tab tab) {
        String filePath = getFilePath(tab);
        if (filePath == null) return null;
        return PathUtil.getConfPath(FileUtil.getParent(filePath, 1));
    }

    /**
     * 获取 Tab 的表名
     *
     * @param tab 目标 Tab
     * @return 表名
     */
    public String getTableName(Tab tab) {
        TabState state = states.get(tab);
        return state != null ? state.tableName() : null;
    }

    /**
     * 更新 Tab 的文件路径（触发状态变更通知）
     *
     * 这是状态变更的唯一入口，确保所有组件都能感知变化
     *
     * @param tab 目标 Tab
     * @param newPath 新的文件路径
     */
    public void updateFilePath(Tab tab, String newPath) {
        if (tab == null) {
            log.warn("尝试更新 null Tab 的状态");
            return;
        }

        TabState oldState = states.get(tab);
        String oldPath = oldState != null ? oldState.filePath() : null;
        String tableName = oldState != null ? oldState.tableName() : extractTableName(newPath);

        TabState newState = new TabState(newPath, tableName, System.currentTimeMillis());
        states.put(tab, newState);

        // 仅当路径变化时通知监听器
        if (!Objects.equals(oldPath, newPath)) {
            log.info("Tab 状态变更: {} -> {}", oldPath, newPath);
            notifyListeners(tab, oldPath, newPath);
        }
    }

    /**
     * 初始化 Tab 状态
     *
     * @param tab 目标 Tab
     * @param filePath 文件路径
     * @param tableName 表名
     */
    public void initState(Tab tab, String filePath, String tableName) {
        if (tab == null) return;

        TabState state = new TabState(filePath, tableName, System.currentTimeMillis());
        states.put(tab, state);
        log.debug("初始化 Tab 状态: tab={}, filePath={}, tableName={}", tab.getText(), filePath, tableName);
    }

    /**
     * 移除 Tab 状态
     *
     * @param tab 目标 Tab
     */
    public void removeState(Tab tab) {
        if (tab != null) {
            states.remove(tab);
            log.debug("移除 Tab 状态: {}", tab.getText());
        }
    }

    /**
     * 检查状态一致性
     *
     * 用于调试和验证，比对 Tab.userData 与管理器中的状态
     *
     * @param tab 目标 Tab
     * @return 是否一致
     */
    public boolean checkConsistency(Tab tab) {
        if (tab == null) return true;

        Object userData = tab.getUserData();
        String managerPath = getFilePath(tab);

        if (userData == null && managerPath == null) return true;
        if (userData == null || managerPath == null) {
            log.warn("状态不一致: Tab.userData={}, manager.filePath={}", userData, managerPath);
            return false;
        }

        boolean consistent = userData.toString().equals(managerPath);
        if (!consistent) {
            log.warn("状态不一致: Tab.userData={}, manager.filePath={}", userData, managerPath);
        }
        return consistent;
    }

    /**
     * 添加状态变更监听器
     */
    public void addListener(StateChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 移除状态变更监听器
     */
    public void removeListener(StateChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * 通知所有监听器
     */
    private void notifyListeners(Tab tab, String oldPath, String newPath) {
        for (StateChangeListener listener : listeners) {
            try {
                listener.onStateChanged(tab, oldPath, newPath);
            } catch (Exception e) {
                log.error("监听器执行异常", e);
            }
        }
    }

    /**
     * 从文件路径提取表名
     */
    private String extractTableName(String filePath) {
        if (filePath == null) return null;
        String fileName = FileUtil.getName(filePath);
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("TabStateManager: %d 个 Tab 状态, %d 个监听器",
                states.size(), listeners.size());
    }

    // ========== 内部类型定义 ==========

    /**
     * Tab 状态记录（不可变）
     */
    public record TabState(String filePath, String tableName, long lastUpdateTime) {}

    /**
     * 状态变更监听器接口
     */
    @FunctionalInterface
    public interface StateChangeListener {
        /**
         * 当 Tab 状态变更时调用
         *
         * @param tab 目标 Tab
         * @param oldPath 旧文件路径
         * @param newPath 新文件路径
         */
        void onStateChanged(Tab tab, String oldPath, String newPath);
    }
}
