# 刷怪工具幂等性改进方案

## 一、现状分析

### 当前实现

**WorldSpawnService.java**
- ✅ 读取和解析 world_N.xml 文件
- ✅ 缓存管理（mapCache、mapInfoCache）
- ✅ 按NPC名称、区域名称搜索
- ✅ 地图统计信息
- ❌ **缺失：修改和保存功能**

**GameToolsStage.java**
- ✅ 地图浏览器（查看所有地图和刷怪区域）
- ✅ 刷怪点生成器（生成坐标，但不保存）
- ✅ 概率模拟器（验证权重配置）
- ❌ **缺失：编辑、保存、撤销功能**

**SpawnTerritory.java**
- ✅ 完整的数据模型（区域、刷怪点、NPC配置）
- ❌ **缺失：唯一性标识、比较方法、幂等性保证**

### 核心问题

1. **无法修改现有配置** - 只能读取，不能编辑和保存
2. **无幂等性保证** - 重复操作会产生不同结果
3. **无唯一性校验** - 可能创建重复的刷怪区域
4. **无操作审计** - 无法追踪谁在何时修改了什么
5. **无回滚机制** - 错误修改后无法恢复

---

## 二、幂等性设计原则

### 2.1 唯一性标识

**Territory唯一键**：`地图名 + 区域名`
```java
public class TerritoryIdentifier {
    private String mapName;      // 地图名称（如 "ab1"）
    private String territoryName; // 区域名称

    @Override
    public boolean equals(Object obj) {
        // 基于mapName和territoryName的比较
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapName, territoryName);
    }
}
```

### 2.2 幂等操作定义

| 操作类型 | 幂等性保证 | 实现方式 |
|---------|-----------|---------|
| **添加刷怪区域** | 相同标识的区域只添加一次 | 先检查是否存在，存在则更新而非新增 |
| **修改刷怪区域** | 多次修改为相同配置结果一致 | 基于唯一键的更新操作 |
| **删除刷怪区域** | 删除不存在的区域不报错 | 先检查是否存在再删除 |
| **批量导入** | 重复导入相同文件结果一致 | 使用UPSERT语义（存在则更新，不存在则插入）|

### 2.3 一致性保证

**XML文件级锁**
```java
public class WorldSpawnEditor {
    // 文件锁，防止并发修改同一个XML
    private final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    public void modifyTerritory(String mapName, Consumer<SpawnTerritory> modifier) {
        Lock lock = fileLocks.computeIfAbsent(mapName, k -> new ReentrantLock());
        lock.lock();
        try {
            // 1. 读取XML
            // 2. 修改数据
            // 3. 保存XML（原子性替换）
        } finally {
            lock.unlock();
        }
    }
}
```

---

## 三、详细实现方案

### 3.1 WorldSpawnEditor 服务

```java
package red.jiuzhou.util.game;

import org.dom4j.*;
import org.dom4j.io.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * 刷怪配置编辑器 - 支持幂等性修改和保存
 *
 * 核心特性：
 * 1. 基于唯一键的UPSERT操作
 * 2. 原子性文件保存（先写临时文件，再原子替换）
 * 3. 文件级并发控制
 * 4. 操作审计日志
 * 5. 自动备份和回滚
 */
public class WorldSpawnEditor {

    private final WorldSpawnService spawnService;

    // 文件锁映射（防止并发修改）
    private final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    // 备份目录
    private static final String BACKUP_DIR = "world_backups/";

    // 审计日志
    private final SpawnEditLogger auditLogger = new SpawnEditLogger();

    /**
     * 添加或更新刷怪区域（幂等）
     *
     * @param mapName 地图名称
     * @param territory 刷怪区域
     * @return 操作结果（CREATED/UPDATED/NO_CHANGE）
     */
    public OperationResult upsertTerritory(String mapName, SpawnTerritory territory) {
        Lock lock = fileLocks.computeIfAbsent(mapName, k -> new ReentrantLock());
        lock.lock();
        try {
            // 1. 加载现有数据
            List<SpawnTerritory> existing = spawnService.loadMapSpawns(mapName);

            // 2. 查找是否已存在（基于区域名）
            Optional<SpawnTerritory> found = existing.stream()
                .filter(t -> t.getName().equals(territory.getName()))
                .findFirst();

            if (found.isPresent()) {
                SpawnTerritory oldTerritory = found.get();

                // 3. 检查是否有实际变化
                if (isTerritoryEqual(oldTerritory, territory)) {
                    auditLogger.log("NO_CHANGE", mapName, territory.getName(), "配置未变化");
                    return new OperationResult(OperationStatus.NO_CHANGE, oldTerritory);
                }

                // 4. 执行更新
                replaceTerritoryInXml(mapName, territory);
                auditLogger.log("UPDATE", mapName, territory.getName(), "更新刷怪区域");
                return new OperationResult(OperationStatus.UPDATED, territory);
            } else {
                // 5. 执行新增
                addTerritoryToXml(mapName, territory);
                auditLogger.log("CREATE", mapName, territory.getName(), "新增刷怪区域");
                return new OperationResult(OperationStatus.CREATED, territory);
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * 删除刷怪区域（幂等）
     */
    public OperationResult deleteTerritory(String mapName, String territoryName) {
        Lock lock = fileLocks.computeIfAbsent(mapName, k -> new ReentrantLock());
        lock.lock();
        try {
            List<SpawnTerritory> existing = spawnService.loadMapSpawns(mapName);
            Optional<SpawnTerritory> found = existing.stream()
                .filter(t -> t.getName().equals(territoryName))
                .findFirst();

            if (found.isPresent()) {
                removeTerritoryFromXml(mapName, territoryName);
                auditLogger.log("DELETE", mapName, territoryName, "删除刷怪区域");
                return new OperationResult(OperationStatus.DELETED, found.get());
            } else {
                // 不存在也返回成功（幂等性）
                auditLogger.log("NO_CHANGE", mapName, territoryName, "区域不存在，无需删除");
                return new OperationResult(OperationStatus.NO_CHANGE, null);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 批量导入刷怪区域（幂等）
     */
    public BatchOperationResult batchUpsert(String mapName, List<SpawnTerritory> territories) {
        Lock lock = fileLocks.computeIfAbsent(mapName, k -> new ReentrantLock());
        lock.lock();
        try {
            int created = 0, updated = 0, noChange = 0;
            List<String> errors = new ArrayList<>();

            for (SpawnTerritory territory : territories) {
                try {
                    OperationResult result = upsertTerritory(mapName, territory);
                    switch (result.getStatus()) {
                        case CREATED: created++; break;
                        case UPDATED: updated++; break;
                        case NO_CHANGE: noChange++; break;
                    }
                } catch (Exception e) {
                    errors.add(territory.getName() + ": " + e.getMessage());
                }
            }

            return new BatchOperationResult(created, updated, noChange, errors);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 保存XML到文件（原子性操作）
     *
     * 流程：
     * 1. 创建备份
     * 2. 写入临时文件
     * 3. 验证XML格式
     * 4. 原子替换原文件
     */
    private void saveXmlToFile(String mapName, Document document) throws IOException {
        MapInfo mapInfo = getMapInfo(mapName);
        Path xmlPath = Paths.get(mapInfo.getWorldNPath());
        Path tempPath = Paths.get(xmlPath.toString() + ".tmp");
        Path backupPath = createBackup(xmlPath);

        try {
            // 1. 写入临时文件（UTF-16编码）
            OutputFormat format = OutputFormat.createPrettyPrint();
            format.setEncoding("UTF-16");
            format.setIndent(true);
            format.setIndentSize(2);

            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(tempPath.toFile()), "UTF-16")) {
                XMLWriter xmlWriter = new XMLWriter(writer, format);
                xmlWriter.write(document);
                xmlWriter.close();
            }

            // 2. 验证临时文件可读
            SAXReader reader = new SAXReader();
            try (InputStreamReader isr = new InputStreamReader(
                    new FileInputStream(tempPath.toFile()), "UTF-16")) {
                reader.read(isr); // 验证XML格式正确
            }

            // 3. 原子替换（Windows使用REPLACE_EXISTING）
            Files.move(tempPath, xmlPath, StandardCopyOption.REPLACE_EXISTING,
                                         StandardCopyOption.ATOMIC_MOVE);

            // 4. 清除缓存
            spawnService.clearMapCache(mapName);

        } catch (Exception e) {
            // 失败时从备份恢复
            if (backupPath != null && Files.exists(backupPath)) {
                Files.copy(backupPath, xmlPath, StandardCopyOption.REPLACE_EXISTING);
            }
            throw new IOException("保存XML失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建备份文件
     */
    private Path createBackup(Path xmlPath) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Path backupDir = Paths.get(BACKUP_DIR);
        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
        }

        String fileName = xmlPath.getFileName().toString();
        String mapName = xmlPath.getParent().getFileName().toString();
        Path backupPath = backupDir.resolve(mapName + "_" + fileName + "." + timestamp + ".bak");

        Files.copy(xmlPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        return backupPath;
    }

    /**
     * 比较两个Territory是否相等（用于检测变化）
     */
    private boolean isTerritoryEqual(SpawnTerritory a, SpawnTerritory b) {
        if (!Objects.equals(a.getName(), b.getName())) return false;
        if (a.isNoRespawn() != b.isNoRespawn()) return false;
        if (a.isAerialSpawn() != b.isAerialSpawn()) return false;
        if (a.getSpawnVersion() != b.getSpawnVersion()) return false;

        // 比较NPC列表
        if (a.getNpcs().size() != b.getNpcs().size()) return false;
        for (int i = 0; i < a.getNpcs().size(); i++) {
            if (!isNpcEqual(a.getNpcs().get(i), b.getNpcs().get(i))) {
                return false;
            }
        }

        // 比较刷怪点
        if (a.getSpawnPoints().size() != b.getSpawnPoints().size()) return false;
        // ... 更多比较逻辑

        return true;
    }

    private boolean isNpcEqual(SpawnTerritory.SpawnNpc a, SpawnTerritory.SpawnNpc b) {
        return Objects.equals(a.getName(), b.getName())
            && a.getCount() == b.getCount()
            && a.getSpawnTime() == b.getSpawnTime()
            && a.getSpawnTimeEx() == b.getSpawnTimeEx();
    }
}
```

### 3.2 操作结果模型

```java
public class OperationResult {
    private OperationStatus status;
    private SpawnTerritory territory;
    private String message;

    public enum OperationStatus {
        CREATED,   // 新创建
        UPDATED,   // 已更新
        DELETED,   // 已删除
        NO_CHANGE  // 无变化（幂等）
    }
}

public class BatchOperationResult {
    private int created;
    private int updated;
    private int noChange;
    private List<String> errors;

    public String getSummary() {
        return String.format("创建: %d, 更新: %d, 无变化: %d, 错误: %d",
            created, updated, noChange, errors.size());
    }
}
```

### 3.3 审计日志

```java
public class SpawnEditLogger {
    private static final String AUDIT_LOG = "spawn_edit_audit.log";

    public void log(String operation, String mapName, String territoryName, String details) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logEntry = String.format("[%s] %s | %s | %s | %s\n",
            timestamp, operation, mapName, territoryName, details);

        try (FileWriter fw = new FileWriter(AUDIT_LOG, true)) {
            fw.write(logEntry);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

---

## 四、UI改进方案

### 4.1 GameToolsStage 新增功能

**新增Tab：🛠️ 区域编辑**

功能列表：
1. **编辑刷怪区域**
   - 修改区域名称、属性（空中刷怪、不重生等）
   - 修改NPC配置（数量、刷新时间）
   - 修改刷怪点坐标

2. **批量导入**
   - 从CSV导入刷怪配置
   - 从其他地图复制区域

3. **保存和撤销**
   - 保存到XML按钮
   - 撤销上一次操作
   - 查看修改历史

4. **验证和预览**
   - 保存前验证配置合法性
   - 显示与原配置的差异对比

### 4.2 UI交互流程

```
[选择地图] → [选择区域] → [编辑配置] → [预览变化] → [保存到XML]
                           ↓
                      [验证幂等性]
                      - 检查区域名是否已存在
                      - 检查配置是否有实际变化
                      - 显示操作结果（创建/更新/无变化）
```

### 4.3 权限和安全

1. **确认对话框** - 保存前显示变更摘要，要求确认
2. **备份提示** - 显示备份文件路径
3. **只读模式** - 可配置只读模式，防止误操作

---

## 五、测试用例

### 5.1 幂等性测试

| 测试场景 | 预期结果 |
|---------|---------|
| 连续两次添加相同区域 | 第1次：CREATED，第2次：NO_CHANGE |
| 连续两次修改为相同配置 | 第1次：UPDATED，第2次：NO_CHANGE |
| 连续两次删除同一区域 | 第1次：DELETED，第2次：NO_CHANGE |
| 批量导入相同文件2次 | 结果完全一致 |

### 5.2 并发测试

| 测试场景 | 预期结果 |
|---------|---------|
| 两个线程同时修改同一地图 | 串行执行，不会丢失修改 |
| 两个线程修改不同地图 | 并行执行，互不影响 |

### 5.3 异常测试

| 测试场景 | 预期结果 |
|---------|---------|
| 保存时磁盘空间不足 | 回滚到原文件，不损坏数据 |
| XML格式错误 | 拒绝保存，显示错误信息 |
| 文件权限不足 | 提示用户，不会崩溃 |

---

## 六、实施计划

### Phase 1: 核心服务（1-2天）
- [x] WorldSpawnEditor 基础框架
- [ ] 幂等性UPSERT实现
- [ ] XML保存和备份机制
- [ ] 单元测试

### Phase 2: UI集成（1天）
- [ ] 在GameToolsStage添加"区域编辑"Tab
- [ ] 实现编辑表单和保存按钮
- [ ] 集成差异对比和确认对话框

### Phase 3: 审计和安全（0.5天）
- [ ] 操作审计日志
- [ ] 备份和回滚机制
- [ ] 权限控制

### Phase 4: 测试和文档（0.5天）
- [ ] 幂等性测试
- [ ] 并发测试
- [ ] 用户手册更新

**总计：3-4天开发周期**

---

## 七、使用示例

### 7.1 代码示例

```java
// 示例1: 添加刷怪区域（幂等）
WorldSpawnEditor editor = new WorldSpawnEditor(worldSpawnService);

SpawnTerritory territory = new SpawnTerritory();
territory.setName("BOSS刷怪区_1");
territory.setAerialSpawn(false);

SpawnTerritory.SpawnNpc npc = new SpawnTerritory.SpawnNpc();
npc.setName("Boss_Dragon");
npc.setCount(1);
npc.setSpawnTime(300);
territory.getNpcs().add(npc);

// 第1次调用 - 返回 CREATED
OperationResult result1 = editor.upsertTerritory("ab1", territory);
System.out.println(result1.getStatus()); // CREATED

// 第2次调用 - 返回 NO_CHANGE（幂等）
OperationResult result2 = editor.upsertTerritory("ab1", territory);
System.out.println(result2.getStatus()); // NO_CHANGE
```

### 7.2 UI操作示例

1. 用户打开"刷怪工具" → "🛠️ 区域编辑"
2. 选择地图 "ab1"
3. 点击"新增区域"按钮
4. 填写区域名称："测试刷怪区_001"
5. 添加NPC配置
6. 点击"保存"
7. 系统显示：
   ```
   ✅ 操作成功
   状态: 已创建
   备份文件: world_backups/ab1_world_N.xml.20250119_143022.bak
   ```
8. 用户再次点击"保存"
9. 系统显示：
   ```
   ℹ️ 无变化
   状态: 配置未变化，无需保存
   ```

---

## 八、注意事项

### 8.1 编码问题
- world_N.xml 使用 **UTF-16** 编码
- 保存时必须保持UTF-16，否则游戏无法读取

### 8.2 文件格式
- 保持原有的XML格式和缩进
- 不要修改DOCTYPE声明
- 保留所有实体定义

### 8.3 性能考虑
- 大地图文件可能超过10MB
- 使用流式解析而非全部加载到内存
- 缓存策略要考虑内存占用

### 8.4 兼容性
- 确保生成的XML能被游戏服务器正确解析
- 不要添加游戏不支持的字段
- 测试环境先验证再应用到生产

---

## 九、总结

本方案通过以下措施保证刷怪工具的幂等性：

1. ✅ **唯一性标识** - 使用地图名+区域名作为唯一键
2. ✅ **UPSERT语义** - 存在则更新，不存在则插入
3. ✅ **状态检测** - 比较新旧配置，无变化则不操作
4. ✅ **原子性保存** - 临时文件+原子替换，防止数据损坏
5. ✅ **并发控制** - 文件级锁，避免并发修改冲突
6. ✅ **审计追踪** - 记录所有操作，便于问题排查
7. ✅ **备份恢复** - 自动备份，支持回滚

**幂等性保证**：无论执行多少次相同的操作，最终结果和执行一次相同。
