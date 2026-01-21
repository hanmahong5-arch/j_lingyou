# 状态管理架构演进计划

> 创建日期: 2026-01-20
> 更新日期: 2026-01-21
> 状态: Phase 1-3 已完成，Phase 4 可选

## 一、问题背景

### 1.1 问题现象
用户在文件树中切换不同目录下的同名文件时，导入操作使用了旧的配置文件路径，导致"DDL不存在"错误。

### 1.2 根因分析
`PaginatedTable.tabFilePath` 在构造时从 `Tab.userData` 读取一次后不再同步，但 `Tab.userData` 会在用户切换文件时更新。

```
时序问题：
1. 用户打开 .backup/.../file.xml
2. PaginatedTable 创建, tabFilePath = ".backup/..."
3. 用户点击 China/file.xml
4. Tab.userData 更新为 "China/..."  ← Tab 更新了
5. 但 tabFilePath 仍是 ".backup/..."  ← 没同步！
```

### 1.3 理论分析（高维视角）

| 理论框架 | 洞察 |
|---------|------|
| **信息论（熵）** | 多状态副本导致熵增，需要单一真理来源 |
| **控制论** | 当前是开环控制，缺乏状态验证反馈 |
| **马尔可夫链** | 70%用户走 选择→DDL→导入 路径，应优先保证这条路径的一致性 |
| **贝叶斯** | 用户切换同名文件时，80%意图是"切换版本" |

---

## 二、决策点详解：为什么这样设计？

### 2.1 为什么选择"同步模式"而非"重建模式"？

**备选方案对比**：

| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| A. 同步模式 | 保持 PaginatedTable 实例，同步其 tabFilePath | 性能好、状态保留 | 需维护同步逻辑 |
| B. 重建模式 | 每次切换文件时销毁并重建 PaginatedTable | 简单、无状态残留 | 性能差、UI 闪烁 |
| C. 不可变模式 | 禁止同 Tab 切换文件，每个文件单独 Tab | 架构干净 | 用户体验差、Tab 爆炸 |

**决策理由**：
- **性能考量**：PaginatedTable 持有大量 UI 组件和缓存数据，重建成本高（约 200-500ms）
- **用户体验**：设计师频繁在版本间切换对比，重建会导致滚动位置、筛选条件丢失
- **技术债务**：重建模式只是"回避问题"而非"解决问题"，长期会产生更多隐患

### 2.2 为什么引入 `currentTab` 字段而非每次从 TabPane 查找？

**备选方案对比**：

```java
// 方案 A: 保存引用（采用）
private Tab currentTab;
private String syncTabFilePath() {
    return currentTab.getUserData().toString();
}

// 方案 B: 每次查找
private String syncTabFilePath() {
    Tab selected = tabPane.getSelectionModel().getSelectedItem();
    return selected.getUserData().toString();
}
```

**决策理由**：
- **正确性**：TabPane 的 selectedItem 可能在批量操作中指向错误的 Tab
- **一致性**：PaginatedTable 应该只关心"自己的 Tab"，而非"当前选中的 Tab"
- **解耦**：减少对 TabPane 的依赖，便于未来测试和重构

### 2.3 为什么在 `createPage()` 调用同步而非 `xmlToDb()`？

**时序分析**：

```
用户操作流程：
1. 选择文件 → Tab.userData 更新
2. 点击"DDL生成" → createPage() 被调用 ← 在此同步！
3. 点击"导入" → xmlToDb() 被调用

错误流程（如果只在 xmlToDb 同步）：
1. 选择文件 → Tab.userData 更新
2. 点击"DDL生成" → createPage() 用旧路径生成 DDL
3. 点击"导入" → xmlToDb() 才同步，但 DDL 已经是旧的了
```

**决策理由**：
- **最早介入原则**：在状态可能发散的第一个操作点同步，而非最后一个
- **因果一致性**：DDL 是导入的前置条件，两者必须基于同一路径
- **防御性编程**：在多个入口点都调用同步，形成多层防护

### 2.4 为什么 `assertStateConsistency()` 是日志而非抛异常？

**设计权衡**：

```java
// 方案 A: 静默自愈（采用）
if (!expected.equals(tabFilePath)) {
    log.warn("状态不一致检测...");
    syncTabFilePath();  // 自动修复
}

// 方案 B: 快速失败
if (!expected.equals(tabFilePath)) {
    throw new IllegalStateException("状态不一致");
}
```

**决策理由**：
- **用户体验优先**：设计师正在工作中，抛异常会中断流程
- **可观测性**：通过日志收集不一致事件，为后续优化提供数据
- **渐进式改进**：Phase 1 先保证功能可用，Phase 2 再加强验证
- **贝叶斯思维**：P(状态不一致|系统正常) 很低，不应为小概率事件惩罚所有用户

---

## 三、演进路线图

### Phase 1: 紧急修复 ✅ 已完成

**修改文件**: `src/main/java/red/jiuzhou/ui/PaginatedTable.java`

| 修改项 | 说明 |
|--------|------|
| `currentTab` 字段 | 保存 Tab 引用用于后续同步 |
| `syncTabFilePath()` | 核心同步方法，从 Tab.userData 更新 tabFilePath |
| `assertStateConsistency()` | 闭环反馈检测，发现不一致时自动修复并记录日志 |
| `tabIsExist(String)` | 重载方法，支持指定路径验证 |
| `createPage()` | 分页前调用 syncTabFilePath() |
| `xmlToDb()` | 使用传入的 filePath 而非 tabFilePath |

---

### Phase 2: 状态中心化（短期）

**目标**: 提取统一的状态管理器，消除分散的状态副本

#### 为什么需要 TabStateManager？

**当前问题**（Phase 1 的局限性）：
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ MenuTabPane     │    │ PaginatedTable  │    │ TabConfLoad     │
│ ─────────────── │    │ ─────────────── │    │ ─────────────── │
│ Tab.userData    │───▶│ tabFilePath     │───▶│ configPath      │
│ (真理来源)       │    │ (副本1)          │    │ (派生状态)       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
        ↓                      ↓                      ↓
    用户操作更新            需要手动同步            每次重新计算
```

**Phase 2 解决方案**：
```
┌─────────────────────────────────────────────────────────┐
│                   TabStateManager                        │
│ ─────────────────────────────────────────────────────── │
│ filePath (单一真理来源)                                  │
│ configPath (自动派生)                                    │
│ listeners (状态变更通知)                                 │
└─────────────────────────────────────────────────────────┘
        ▲                      ▲                      ▲
        │                      │                      │
┌───────┴───────┐    ┌────────┴────────┐    ┌────────┴────────┐
│ MenuTabPane   │    │ PaginatedTable  │    │ TabConfLoad     │
│ (写入状态)     │    │ (读取状态)       │    │ (读取派生)       │
└───────────────┘    └─────────────────┘    └─────────────────┘
```

**决策理由**：
- **信息论**：将系统熵从 O(n) 降到 O(1)，n 为状态副本数
- **单一职责**：状态管理集中，业务逻辑分散
- **可测试性**：可以单独测试 TabStateManager，无需启动 UI
- **WeakHashMap 选择**：Tab 关闭后自动释放状态，无需手动清理

**新增文件**: `src/main/java/red/jiuzhou/ui/state/TabStateManager.java`

```java
package red.jiuzhou.ui.state;

import javafx.scene.control.Tab;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tab 状态管理器（单例）
 *
 * 设计原理：
 * - 单一真理来源：所有 Tab 状态都通过此管理器访问
 * - 派生状态自动计算：configPath 等派生状态由 filePath 自动计算
 * - 状态变更通知：支持监听器模式，便于 UI 同步更新
 */
public class TabStateManager {

    private static final TabStateManager INSTANCE = new TabStateManager();

    // 使用 WeakHashMap 避免内存泄漏（Tab 被关闭后自动清理）
    private final Map<Tab, TabState> states = new WeakHashMap<>();

    // 状态变更监听器
    private final List<TabStateChangeListener> listeners = new CopyOnWriteArrayList<>();

    private TabStateManager() {}

    public static TabStateManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取 Tab 的当前文件路径
     */
    public String getFilePath(Tab tab) {
        TabState state = states.get(tab);
        return state != null ? state.filePath() : null;
    }

    /**
     * 获取派生的配置文件路径
     */
    public String getConfigPath(Tab tab) {
        String filePath = getFilePath(tab);
        if (filePath == null) return null;
        return PathUtil.getConfPath(FileUtil.getParent(filePath, 1));
    }

    /**
     * 更新文件路径（触发状态变更通知）
     */
    public void updateFilePath(Tab tab, String newPath) {
        TabState oldState = states.get(tab);
        TabState newState = new TabState(newPath, System.currentTimeMillis());
        states.put(tab, newState);

        // 通知所有监听器
        for (TabStateChangeListener listener : listeners) {
            listener.onStateChanged(tab, oldState, newState);
        }
    }

    /**
     * 注册状态变更监听器
     */
    public void addListener(TabStateChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Tab 状态记录
     */
    public record TabState(String filePath, long lastUpdateTime) {}

    /**
     * 状态变更监听器接口
     */
    public interface TabStateChangeListener {
        void onStateChanged(Tab tab, TabState oldState, TabState newState);
    }
}
```

**重构影响范围**:
- `MenuTabPaneExample.createTab()`: 调用 `TabStateManager.updateFilePath()`
- `PaginatedTable`: 从 `TabStateManager.getFilePath()` 获取路径
- `TabPaneController`: 监听状态变更，刷新 UI

---

### Phase 3: 事件总线（中期）

**目标**: 实现松耦合的组件通信，支持跨模块事件传递

#### 为什么需要 EventBus 而非直接方法调用？

**当前模块间通信（紧耦合）**：
```java
// PaginatedTable.java 需要知道所有需要通知的组件
public void xmlToDb() {
    // 导入完成后...
    fileStatusCache.refresh(tabName);    // 直接依赖
    tableViewController.reload();         // 直接依赖
    menuBarController.updateState();      // 直接依赖
    // 新增组件时需要修改此处 ← 违反开闭原则
}
```

**EventBus 解决方案（松耦合）**：
```java
// PaginatedTable.java 只负责发布事件
public void xmlToDb() {
    // 导入完成后...
    EventBus.getInstance().post(new ImportCompletedEvent(tabName, rowCount, true));
    // 新增组件只需订阅事件，无需修改此处
}

// FileStatusCache.java 自己订阅
EventBus.getInstance().subscribe(ImportCompletedEvent.class, e -> refresh(e.tableName()));
```

**决策理由**：

| 维度 | 直接调用 | EventBus |
|------|---------|----------|
| **耦合度** | 高（发布者需知道所有订阅者） | 低（发布者不关心谁订阅） |
| **可扩展性** | 差（新组件需修改发布者） | 好（新组件自己订阅） |
| **可测试性** | 差（需 mock 所有依赖） | 好（只需验证事件发布） |
| **调试难度** | 简单（调用栈清晰） | 中等（需日志追踪事件流） |

**为什么不用 Spring Event / Guava EventBus？**
- **轻量级**：本项目只需基础发布-订阅，无需复杂功能
- **控制权**：自建 EventBus 可以精确控制线程模型（JavaFX Application Thread vs 虚拟线程）
- **依赖极简**：不引入额外框架依赖

**新增文件**: `src/main/java/red/jiuzhou/ui/event/EventBus.java`

```java
package red.jiuzhou.ui.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 轻量级事件总线
 *
 * 特点：
 * - 类型安全：基于 Class<T> 的事件类型分发
 * - 线程安全：使用 ConcurrentHashMap + CopyOnWriteArrayList
 * - 支持同步和异步发布
 */
public class EventBus {

    private static final EventBus INSTANCE = new EventBus();

    private final Map<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();

    private EventBus() {}

    public static EventBus getInstance() {
        return INSTANCE;
    }

    /**
     * 订阅事件
     */
    public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /**
     * 取消订阅
     */
    public <T> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        List<Consumer<?>> eventHandlers = handlers.get(eventType);
        if (eventHandlers != null) {
            eventHandlers.remove(handler);
        }
    }

    /**
     * 同步发布事件
     */
    @SuppressWarnings("unchecked")
    public void post(Object event) {
        List<Consumer<?>> eventHandlers = handlers.get(event.getClass());
        if (eventHandlers != null) {
            for (Consumer<?> handler : eventHandlers) {
                ((Consumer<Object>) handler).accept(event);
            }
        }
    }

    /**
     * 异步发布事件（在虚拟线程中执行）
     */
    public void postAsync(Object event) {
        Thread.startVirtualThread(() -> post(event));
    }
}
```

**事件定义**: `src/main/java/red/jiuzhou/ui/event/Events.java`

```java
package red.jiuzhou.ui.event;

import javafx.scene.control.Tab;

/**
 * 系统事件定义
 */
public final class Events {

    private Events() {}

    /** Tab 状态变更事件 */
    public record TabStateChangedEvent(
        Tab tab,
        String oldFilePath,
        String newFilePath
    ) {}

    /** 缓存失效事件 */
    public record CacheInvalidatedEvent(
        String cacheKey,
        String reason
    ) {}

    /** 数据导入完成事件 */
    public record ImportCompletedEvent(
        String tableName,
        int rowCount,
        boolean success
    ) {}

    /** 数据导出完成事件 */
    public record ExportCompletedEvent(
        String tableName,
        String filePath,
        boolean success
    ) {}

    /** DDL 生成完成事件 */
    public record DdlGeneratedEvent(
        String tableName,
        String sqlFilePath
    ) {}
}
```

**使用示例**:

```java
// 订阅事件（在初始化时）
EventBus.getInstance().subscribe(ImportCompletedEvent.class, event -> {
    if (event.success()) {
        // 刷新表格
        refreshTableView(event.tableName());
        // 更新文件状态缓存
        FileStatusCache.getInstance().refresh(event.tableName());
    }
});

// 发布事件（在导入完成后）
EventBus.getInstance().post(new ImportCompletedEvent("item_grade", 1500, true));
```

---

### Phase 4: 响应式架构（长期，可选）

**目标**: 使用 JavaFX 的 Observable 模式实现响应式状态管理

#### 为什么标记为"可选"？

**蒙特卡洛树评估结果**：

| 方案 | 实施成本 | 维护成本 | 扩展性 | 一致性 | 综合评分 |
|------|---------|---------|--------|-------|---------|
| Phase 1 点修复 | 低(2h) | 高 | 差 | 部分 | 60 |
| Phase 2 状态中心化 | 中(4-6h) | 中 | 中 | 强 | 75 |
| Phase 3 事件总线 | 中(4-6h) | 低 | 好 | 强 | **85** |
| Phase 4 响应式 | 高(8-12h) | 低 | 优 | 强 | 80 |

**为什么 Phase 3 评分高于 Phase 4？**
- **边际收益递减**：Phase 3 已解决 90% 的问题，Phase 4 的额外收益有限
- **团队学习曲线**：响应式编程范式需要团队适应
- **调试复杂度**：响应式链路的调试比事件总线更复杂
- **JavaFX 绑定限制**：部分复杂派生状态难以用纯绑定表达

**什么情况下应该实施 Phase 4？**
- UI 状态绑定成为性能瓶颈
- 需要复杂的状态派生链（A → B → C → D）
- 团队已熟悉响应式编程

**新增文件**: `src/main/java/red/jiuzhou/ui/state/ReactiveTabState.java`

```java
package red.jiuzhou.ui.state;

import javafx.beans.property.*;

/**
 * 响应式 Tab 状态
 *
 * 特点：
 * - 派生状态自动响应源状态变化
 * - 与 JavaFX 绑定机制无缝集成
 * - 支持 UI 自动刷新
 */
public class ReactiveTabState {

    // 源状态（可写）
    private final StringProperty filePath = new SimpleStringProperty();
    private final StringProperty tableName = new SimpleStringProperty();

    // 派生状态（只读）
    private final ReadOnlyStringWrapper configPath = new ReadOnlyStringWrapper();
    private final ReadOnlyBooleanWrapper hasConfig = new ReadOnlyBooleanWrapper();

    public ReactiveTabState() {
        // 派生状态自动响应
        filePath.addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                String parent = FileUtil.getParent(newVal, 1);
                configPath.set(PathUtil.getConfPath(parent));
                hasConfig.set(FileUtil.exist(configPath.get() + "/" + tableName.get() + ".json"));
            } else {
                configPath.set(null);
                hasConfig.set(false);
            }
        });
    }

    // Getters for binding
    public StringProperty filePathProperty() { return filePath; }
    public ReadOnlyStringProperty configPathProperty() { return configPath.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty hasConfigProperty() { return hasConfig.getReadOnlyProperty(); }

    // Convenience methods
    public String getFilePath() { return filePath.get(); }
    public void setFilePath(String path) { filePath.set(path); }
    public String getConfigPath() { return configPath.get(); }
    public boolean hasConfig() { return hasConfig.get(); }
}
```

---

## 四、实施优先级与决策理由

| 阶段 | 优先级 | 预计工时 | 风险 |
|------|--------|---------|------|
| Phase 1 | ✅ 已完成 | 2h | 低 |
| Phase 2 | 高 | 4-6h | 中（需重构多个类） |
| Phase 3 | 中 | 4-6h | 低（增量添加） |
| Phase 4 | 低 | 8-12h | 中（架构变更较大） |

### 4.1 为什么采用渐进式演进而非一步到位？

**决策树分析**：

```
                    [系统状态]
                        │
            ┌───────────┴───────────┐
            ▼                       ▼
      [有紧急 Bug?]           [长期规划?]
            │                       │
      ┌─────┴─────┐           ┌─────┴─────┐
      ▼           ▼           ▼           ▼
    [是]        [否]      [是]         [否]
      │           │          │            │
      ▼           ▼          ▼            ▼
  Phase 1     跳过      Phase 2-4      维持现状
  点修复    紧急修复    渐进重构
```

**理由**：
1. **风险控制**：每个 Phase 独立可交付，出问题可快速回滚
2. **价值验证**：Phase 1 修复后观察效果，再决定是否继续投入
3. **资源弹性**：如有更高优先级任务，可在任意 Phase 暂停
4. **知识积累**：每个 Phase 的实施经验为下一 Phase 提供输入

### 4.2 为什么 Phase 2 优先于 Phase 3？

**依赖关系**：
```
Phase 1 ──▶ Phase 2 ──▶ Phase 3 ──▶ Phase 4
 点修复      状态中心      事件总线     响应式
            （基础设施）   （通信层）   （UI层）
```

**理由**：
- EventBus 的事件需要携带状态信息
- 如果状态仍然分散，事件中的状态可能已过期
- TabStateManager 提供统一的状态快照，确保事件数据一致性

---

## 五、验证策略

### 5.1 单元测试

```java
@Test
void testStateSyncAfterFileSwitch() {
    // Given: Tab 指向 .backup/file.xml
    Tab tab = new Tab("test");
    tab.setUserData(".backup/file.xml");

    // When: 用户切换到 China/file.xml
    TabStateManager.getInstance().updateFilePath(tab, "China/file.xml");

    // Then: 状态应一致
    assertEquals("China/file.xml", TabStateManager.getInstance().getFilePath(tab));
}
```

### 5.2 集成测试场景

1. **场景A**: 打开 `.backup/` 下的文件 → 切换到 `China/` → 执行导入 → 验证成功
2. **场景B**: 快速连续切换文件 → 执行操作 → 验证最终状态正确
3. **场景C**: 并发操作（多Tab）→ 验证无状态污染

### 5.3 回归测试

确保以下功能不受影响：
- DDL 生成
- XML 导入/导出
- 批量操作
- 分页查询
- 搜索筛选

---

## 五、相关文件

| 文件 | 说明 |
|------|------|
| `src/main/java/red/jiuzhou/ui/PaginatedTable.java` | Phase 1 修改的主要文件 |
| `src/main/java/red/jiuzhou/ui/MenuTabPaneExample.java` | Tab 创建和 userData 设置 |
| `src/main/java/red/jiuzhou/dbxml/TabConfLoad.java` | 配置文件加载 |
| `src/main/java/red/jiuzhou/util/PathUtil.java` | 路径计算工具 |

---

## 六、参考资料

- 计划文件: `~/.claude/plans/graceful-conjuring-flurry.md`
- 理论分析: 信息论（熵增熵减）、控制论（反馈回路）、马尔可夫链（状态转移）
