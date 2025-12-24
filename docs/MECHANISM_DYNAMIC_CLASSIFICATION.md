# 游戏机制动态分类系统

## 概述

**实现日期**: 2025-12-21
**版本**: v2.0

全新的混合配置系统，结合**自动预归类**和**设计师手动调整**能力，让游戏机制分类既智能又灵活。

## 核心特性

### 1. 自动预归类

系统会自动扫描所有已录入目录的XML文件，使用多层检测策略进行智能分类：

- **文件夹级别匹配** - 根据父目录名称分类（如 `spawns/` 目录）
- **精确文件名匹配** - 41个核心文件的精确映射
- **正则模式匹配** - 基于文件名模式的智能识别

### 2. 手动覆盖调整

设计师可以通过配置文件轻松调整分类结果：

- ✅ **无需编译代码** - 直接编辑YAML配置文件
- ✅ **重启即生效** - 修改后重启应用即可
- ✅ **优先级最高** - 手动覆盖优先于自动检测
- ✅ **支持排除** - 可以明确排除不属于任何机制的文件

## 工作原理

### 检测优先级（从高到低）

```
优先级0: 手动覆盖配置（置信度 0.99）
    ↓
优先级1: 排除列表（置信度 0.95）
    ↓
优先级2: 文件夹级别匹配（置信度 0.95）
    ↓
优先级3: 精确文件名匹配（置信度 0.98）
    ↓
优先级4: 正则模式匹配（置信度 0.5-0.9）
    ↓
优先级5: 兜底分类 OTHER（置信度 0.3）
```

### 工作流程示例

```
文件: my_custom_skill.xml

1. 检查手动覆盖配置
   → 如果在 mechanism_manual_overrides.yml 中配置为 SKILL
   → 返回 SKILL（置信度 0.99）✅

2. 如果没有手动覆盖，检查排除列表
   → 如果在 excluded_files 中
   → 返回 OTHER（置信度 0.95）

3. 如果没有排除，执行自动检测
   → 文件夹匹配：检查父目录名
   → 精确匹配：检查是否在 EXACT_FILE_MAPPINGS 中
   → 正则匹配：检查文件名是否匹配技能系统正则模式
```

## 配置文件说明

### 配置文件位置

```
src/main/resources/mechanism_manual_overrides.yml
```

### 配置文件结构

```yaml
# 游戏机制手动覆盖配置

# ========================================
# 手动覆盖配置
# ========================================
manual_overrides:
  # 技能系统
  SKILL:
    - custom_skill_file.xml
    - another_skill.xml

  # 物品系统
  ITEM:
    - custom_item_file.xml

  # NPC系统
  NPC:
    - custom_npc_file.xml

  # ... 其他机制

# ========================================
# 排除列表
# ========================================
excluded_files:
  - test_data.xml
  - temp_backup.xml
  - debug_output.xml
```

## 使用指南

### 场景1: 自动检测错误，需要手动调整

**问题**: `my_special_skill.xml` 被自动检测为 `OTHER` 分类，但它实际上是技能文件。

**解决方案**:

1. 打开 `mechanism_manual_overrides.yml`
2. 在 `SKILL` 机制下添加文件名：

```yaml
manual_overrides:
  SKILL:
    - my_special_skill.xml  # 添加这一行
```

3. 保存文件
4. 重启应用
5. ✅ 文件现在会被分类为 SKILL（置信度 0.99）

### 场景2: 某个文件不属于任何游戏机制

**问题**: `backup_20241201.xml` 是临时备份文件，不应该出现在任何机制分类中。

**解决方案**:

1. 打开 `mechanism_manual_overrides.yml`
2. 在 `excluded_files` 中添加文件名：

```yaml
excluded_files:
  - backup_20241201.xml  # 添加这一行
```

3. 保存文件
4. 重启应用
5. ✅ 文件现在会被分类为 OTHER（置信度 0.95）

### 场景3: 批量调整多个文件

**问题**: 有5个自定义NPC文件，都被错误分类。

**解决方案**:

```yaml
manual_overrides:
  NPC:
    - custom_npc_1.xml
    - custom_npc_2.xml
    - custom_npc_3.xml
    - custom_npc_4.xml
    - custom_npc_5.xml
```

## 支持的机制分类

| 机制代码 | 显示名称 | 说明 |
|---------|---------|------|
| SKILL | 技能系统 | 技能配置、技能学习、德瓦尼恩技能等 |
| ITEM | 物品系统 | 物品配置、物品集合、装备强化等 |
| NPC | NPC系统 | NPC配置、怪物、部落关系等 |
| QUEST | 任务系统 | 任务配置、任务奖励、任务链等 |
| ABYSS | 深渊系统 | 深渊配置、军衔、据点等 |
| SHOP | 商店系统 | 商品列表、商人配置等 |
| PET | 宠物系统 | 宠物配置、灵魂兽等 |
| CRAFT | 制作系统 | 配方、组装、合成等 |
| TITLE | 称号系统 | 称号配置 |
| PORTAL | 传送系统 | 传送点、飞行路径等 |
| INSTANCE | 副本系统 | 副本配置 |
| GOTCHA | 抽卡系统 | 抽卡配置 |
| WORLD | 世界地图 | 地图、刷怪点、采集点等 |
| SPAWN | 刷怪系统 | 怪物刷新、刷怪组等 |
| GATHER | 采集系统 | 采集点配置 |
| AI | AI系统 | NPC AI、怪物AI等 |
| DIALOG | 对话系统 | NPC对话、选项等 |
| RIDE | 骑乘系统 | 坐骑、骑宠配置 |
| EMOTION | 表情系统 | 表情、动作配置 |
| MOTION | 动作系统 | 角色动作、特效等 |
| HOUSE | 房屋系统 | 房屋、家具配置 |
| ASSEMBLY | 组装系统 | 组装配置 |
| CUBE | 魔方系统 | 魔方配置 |
| STATS | 属性系统 | 角色属性、怪物属性等 |
| GAME_CONFIG | 游戏配置 | 全局配置、系统设置等 |
| CLIENT_STRINGS | 客户端字符串 | UI文本、本地化字符串 |
| OTHER | 其他 | 未分类或无法识别 |

## 技术实现

### 核心类

| 类名 | 职责 |
|-----|------|
| `MechanismOverrideConfig.java` | 配置加载器（单例模式）|
| `AionMechanismDetector.java` | 机制检测器（集成手动覆盖）|
| `mechanism_manual_overrides.yml` | 手动覆盖配置文件 |

### 配置加载器 API

```java
// 获取单例实例
MechanismOverrideConfig config = MechanismOverrideConfig.getInstance();

// 检查文件是否有手动覆盖
boolean hasOverride = config.hasOverride("my_file.xml");

// 获取手动覆盖的分类
AionMechanismCategory category = config.getOverride("my_file.xml");

// 检查文件是否被排除
boolean isExcluded = config.isExcluded("temp.xml");

// 重新加载配置（无需重启应用）
config.reload();

// 获取统计信息
int overrideCount = config.getOverrideCount();
int excludedCount = config.getExcludedCount();
```

### 集成到检测器

```java
public DetectionResult detect(File file, String relativePath, boolean isLocalized) {
    String fileName = file.getName();

    // 加载手动覆盖配置
    MechanismOverrideConfig overrideConfig = MechanismOverrideConfig.getInstance();

    // 0. 手动覆盖检查（最高优先级）
    if (overrideConfig.hasOverride(fileName)) {
        AionMechanismCategory overrideCategory = overrideConfig.getOverride(fileName);
        return DetectionResult.builder()
                .category(overrideCategory)
                .confidence(0.99)
                .reasoning("手动覆盖配置: " + fileName)
                .build();
    }

    // 0.5. 排除列表检查
    if (overrideConfig.isExcluded(fileName)) {
        return DetectionResult.builder()
                .category(AionMechanismCategory.OTHER)
                .confidence(0.95)
                .reasoning("手动排除: " + fileName)
                .build();
    }

    // 1-4. 自动检测逻辑...
}
```

## 配置文件语法规范

### YAML基础语法

```yaml
# 注释以 # 开头

# 键值对格式
key: value

# 列表格式
list:
  - item1
  - item2
  - item3

# 嵌套结构
parent:
  child1: value1
  child2:
    - list_item1
    - list_item2
```

### 常见错误

❌ **错误1: 缩进使用Tab**
```yaml
manual_overrides:
	SKILL:  # ❌ 使用了Tab
```

✅ **正确: 使用空格**
```yaml
manual_overrides:
  SKILL:  # ✅ 使用2个空格
```

❌ **错误2: 文件名包含特殊字符未加引号**
```yaml
excluded_files:
  - file:with:colons.xml  # ❌ 冒号需要引号
```

✅ **正确: 特殊字符使用引号**
```yaml
excluded_files:
  - "file:with:colons.xml"  # ✅ 使用引号
```

❌ **错误3: 列表项缺少 `-` 符号**
```yaml
SKILL:
  skills.xml  # ❌ 缺少 -
```

✅ **正确: 列表项使用 `-` 前缀**
```yaml
SKILL:
  - skills.xml  # ✅ 使用 -
```

## 调试和日志

### 启用调试日志

在 `logback-spring.xml` 中设置：

```xml
<logger name="red.jiuzhou.analysis.aion" level="DEBUG"/>
```

### 日志输出示例

```
[DEBUG] 手动覆盖匹配: my_custom_skill.xml -> 技能系统
[DEBUG] 文件在排除列表中: temp_backup.xml
[DEBUG] 精确匹配成功: skills.xml -> 技能系统
[INFO] 成功加载机制覆盖配置 - 手动覆盖: 3 个文件, 排除: 2 个文件
```

## 常见问题

### Q1: 修改配置后为什么不生效？

**A**: 需要**完全重启应用**。配置在应用启动时加载，运行时不会自动重新加载。

**解决方案**:
1. 关闭应用
2. 修改 `mechanism_manual_overrides.yml`
3. 重新启动应用

### Q2: 如何查看当前配置加载了多少个覆盖？

**A**: 查看应用启动日志：

```
[INFO] 成功加载机制覆盖配置 - 手动覆盖: 5 个文件, 排除: 2 个文件
```

### Q3: 一个文件可以属于多个机制吗？

**A**: 不可以。如果在多个机制中重复添加同一个文件，系统会使用**第一个匹配项**，并输出警告日志：

```
[WARN] 文件 my_file.xml 在多个机制中重复配置，将使用第一个匹配项: 技能系统
```

### Q4: 配置文件格式错误会怎样？

**A**: 系统会捕获异常，输出错误日志，并使用**空配置**（即不应用任何手动覆盖）：

```
[ERROR] 加载配置文件失败: mechanism_manual_overrides.yml, 错误: ...
```

**解决方案**: 检查YAML语法，确保：
- 使用空格缩进（不是Tab）
- 列表项使用 `-` 前缀
- 特殊字符使用引号

### Q5: 如何临时禁用某个手动覆盖？

**A**: 使用注释符 `#`：

```yaml
manual_overrides:
  SKILL:
    - active_file.xml
    # - disabled_file.xml  # 临时禁用
```

## 性能优化

### 配置缓存

- 配置在应用启动时加载一次
- 使用 `ConcurrentHashMap` 存储，支持多线程并发访问
- 查询时间复杂度为 O(1)

### 内存占用

每个手动覆盖条目占用约 **100 bytes** 内存：
- 100个覆盖 ≈ 10 KB
- 1000个覆盖 ≈ 100 KB

对于典型的游戏配置项目（50-200个覆盖），内存占用可忽略不计。

## 迁移指南

### 从旧版本迁移

**旧版本（v1.0）**: 所有分类都硬编码在 `AionMechanismDetector.java` 中

**新版本（v2.0）**: 自动检测 + 手动覆盖

**迁移步骤**:

1. ✅ **无需迁移** - 新版本完全兼容旧版本
2. 自动检测逻辑保持不变（41个精确映射 + 正则模式）
3. 手动覆盖配置为**可选**功能
4. 如果不创建 `mechanism_manual_overrides.yml`，系统将只使用自动检测

**迁移建议**:

- 保留现有的精确映射（`EXACT_FILE_MAPPINGS`）- 对于自动检测错误的文件，使用手动覆盖
- 逐步将自定义映射迁移到配置文件

## 最佳实践

### 1. 优先使用自动检测

只在自动检测失败时才使用手动覆盖：

```yaml
# ✅ 好的做法：只覆盖自动检测错误的文件
manual_overrides:
  SKILL:
    - weird_skill_file.xml  # 自动检测失败，手动指定

# ❌ 不好的做法：把所有文件都手动配置
manual_overrides:
  SKILL:
    - skills.xml  # 自动检测已经能正确识别
    - skill_base.xml  # 自动检测已经能正确识别
    # ... 100多个文件
```

### 2. 添加注释说明

为每个手动覆盖添加注释，说明为什么需要覆盖：

```yaml
manual_overrides:
  SKILL:
    # 自动检测误判为ITEM，实际是技能相关
    - custom_skill_reward.xml
    # 新增的自定义技能文件，自动检测无法识别
    - new_skill_system.xml
```

### 3. 使用排除列表管理临时文件

```yaml
excluded_files:
  # 开发测试文件
  - test_data.xml
  - debug_output.xml
  # 备份文件
  - backup_*.xml  # 注意：当前版本不支持通配符
```

**注意**: 当前版本不支持通配符，每个文件需要单独列出。

### 4. 定期审查配置

建议每月审查一次手动覆盖配置：
- 删除已经修复的自动检测错误
- 将常见模式提交给开发团队，更新自动检测逻辑

## 后续改进方向

### 计划中的功能

1. **通配符支持** - 支持 `backup_*.xml` 这样的模式
2. **UI配置编辑器** - 图形化界面管理手动覆盖
3. **热重载** - 无需重启应用即可重新加载配置
4. **导出报告** - 导出当前分类结果和覆盖统计
5. **批量导入** - 从CSV/Excel批量导入覆盖配置

### 扩展建议

**为设计师提供更便捷的调整能力**:

1. 在机制浏览器UI中添加右键菜单：
   - "移动到其他机制..."
   - "从机制中排除"
   - "重置为自动检测"

2. 自动生成配置：
   - 点击"移动到其他机制"后，自动更新 `mechanism_manual_overrides.yml`
   - 无需手动编辑YAML文件

3. 配置验证工具：
   - 检查YAML语法错误
   - 检测重复配置
   - 提示未使用的覆盖（文件不存在）

## 相关文档

- `docs/MECHANISM_EXPLORER_GUIDE.md` - 机制浏览器使用指南
- `docs/MECHANISM_CLASSIFICATION_FIX.md` - 机制分类系统修复报告（v1.0）
- `src/main/java/red/jiuzhou/analysis/aion/AionMechanismCategory.java` - 机制分类枚举
- `src/main/java/red/jiuzhou/analysis/aion/AionMechanismDetector.java` - 机制检测器
- `src/main/java/red/jiuzhou/analysis/aion/MechanismOverrideConfig.java` - 配置加载器

## 贡献者

- Claude Sonnet 4.5 (AI Assistant) - 系统设计与实现

## 更新日志

| 日期 | 版本 | 说明 |
|------|------|------|
| 2025-12-21 | v2.0 | 实现混合配置系统（自动检测 + 手动覆盖）|
| 2025-12-21 | v1.0 | 初始版本，仅支持自动检测（41个精确映射）|

---

**实现完成** ✅ - 混合配置系统已上线，编译测试通过。
