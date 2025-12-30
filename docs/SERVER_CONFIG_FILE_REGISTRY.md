# 服务器配置文件清单系统

**"文件层的唯一真理"** - 基于服务器日志分析的配置文件管理系统

## 核心理念

**问题**: 工具扫描到数千个 XML 文件，但服务器实际只加载其中一小部分，导致：
- 设计师不知道哪些文件需要关注
- 导入导出处理大量无用文件，浪费时间
- 缺乏服务器加载状态的可见性

**解决方案**: 通过分析服务器启动日志，建立"服务器实际加载的文件清单"，以此为准进行所有操作。

## 系统架构

### 数据库表

#### `server_config_files` - 配置文件清单表

```sql
CREATE TABLE server_config_files (
    id INT AUTO_INCREMENT PRIMARY KEY,
    
    -- 文件标识
    file_name VARCHAR(200) NOT NULL,          -- XML文件名
    file_path VARCHAR(500),                   -- 完整路径
    table_name VARCHAR(100),                  -- 对应数据库表名
    
    -- 服务器加载信息
    is_server_loaded BOOLEAN DEFAULT FALSE,   -- 是否被服务器加载
    load_priority INT DEFAULT 0,              -- 加载优先级（1=核心，2=重要，3=一般）
    server_module VARCHAR(100),               -- 所属模块（MainServer/NPCServer）
    
    -- 文件元数据
    file_category VARCHAR(50),                -- 文件分类（items/skills/quests等）
    file_encoding VARCHAR(20),                -- 文件编码
    file_size BIGINT,                         -- 文件大小
    
    -- 验证信息
    validation_status VARCHAR(20),            -- 验证状态（valid/invalid/missing）
    validation_errors TEXT,                   -- 验证错误信息（JSON）
    
    -- 统计信息
    import_count INT DEFAULT 0,               -- 导入次数
    export_count INT DEFAULT 0,               -- 导出次数
    last_import_time DATETIME,                -- 最后导入时间
    last_export_time DATETIME,                -- 最后导出时间
    
    UNIQUE KEY uk_file_name (file_name),
    KEY idx_server_loaded (is_server_loaded),
    KEY idx_load_priority (load_priority)
);
```

### 核心类

#### `ServerConfigFile` - 配置文件实体
- 文件基本信息（名称、路径、表名）
- 服务器加载状态和优先级
- 验证状态和错误信息
- 导入导出统计

#### `ServerConfigFileDao` - 数据访问层
- CRUD 操作
- 按加载状态/优先级/分类查询
- 导入导出次数统计

#### `ServerLogAnalyzer` - 日志分析器
核心功能：
1. 扫描服务器日志目录（`.err`、`.log` 文件）
2. 使用正则表达式提取 XML 文件加载记录
3. 区分成功加载和失败加载
4. 推断文件分类、优先级、是否核心文件
5. 保存分析结果到数据库

日志匹配模式：
```java
// 成功加载模式
"Loading.*?([a-zA-Z0-9_-]+\.xml)"
"Loaded.*?([a-zA-Z0-9_-]+\.xml)"
"Reading.*?([a-zA-Z0-9_-]+\.xml)"
"([a-zA-Z0-9_-]+\.xml).*?loaded"
"([a-zA-Z0-9_-]+\.xml).*?successfully"

// 错误加载模式（说明服务器尝试加载过）
"Error.*?([a-zA-Z0-9_-]+\.xml)"
"Failed.*?([a-zA-Z0-9_-]+\.xml)"
```

#### `ServerConfigFileManagerStage` - UI 管理界面
功能：
- 📊 **分析服务器日志** - 扫描日志目录提取文件列表
- 🔄 **刷新** - 重新加载数据库中的清单
- 🔍 **筛选器** - 按加载状态、优先级、分类筛选
- 🔎 **搜索** - 文件名/表名快速搜索
- 📋 **查看详情** - 双击查看文件详细信息

## 使用流程

### 1. 初始化数据库表

```bash
# 登录 MySQL
mysql -u root -p xmldb_suiyue

# 执行建表脚本
source src/main/resources/sql/server_config_files.sql;

# 验证表结构
DESC server_config_files;
```

### 2. 分析服务器日志

**方式 A：使用 Shell 脚本（预览）**
```bash
# 快速预览日志中的 XML 文件
./analyze_server_logs.sh d:/AionReal58/AionServer/MainServer/log
```

**方式 B：使用应用 UI（推荐）**
1. 启动应用
2. 点击工具栏「📋 配置清单」按钮
3. 点击「📊 分析服务器日志」
4. 选择日志目录：`d:/AionReal58/AionServer/MainServer/log`
5. 等待分析完成

### 3. 查看和管理清单

**筛选器选项**：
- **全部文件** - 显示所有记录
- **✅ 服务器已加载** - 只显示服务器实际加载的文件
- **🔥 核心配置** - 显示优先级 1 的核心文件
- **📦 物品配置** - 显示 items 分类
- **⚔️ 技能配置** - 显示 skills 分类
- **📜 任务配置** - 显示 quests 分类
- **🧑 NPC配置** - 显示 npcs 分类
- **🗺️ 世界配置** - 显示 worlds 分类

**表格列**：
- 文件名
- 数据库表名
- 服务器加载状态（✅/❌）
- 优先级（🔥核心/⚠️重要/📄一般）
- 文件分类
- 导入次数
- 导出次数
- 验证状态

## 推断规则

### 文件分类推断
```java
if (fileName.contains("item")) return "items";
if (fileName.contains("skill")) return "skills";
if (fileName.contains("quest")) return "quests";
if (fileName.contains("npc")) return "npcs";
if (fileName.contains("world") || fileName.contains("map")) return "worlds";
```

### 优先级推断
```java
// 核心配置文件 - 优先级 1
if (fileName.matches("(items?|skills?|npcs?|quests?|world)s?\.xml"))
    return CRITICAL;

// 重要配置文件 - 优先级 2
if (category.matches("items|skills|quests|npcs|worlds"))
    return IMPORTANT;

// 一般配置文件 - 优先级 3
return NORMAL;
```

### 核心文件判断
```java
boolean isCritical = fileName.matches(
    "(items?|skills?|skill_base|npcs?|quests?|world)s?\.xml"
);
```

## 与工具其他功能集成

### 导入导出优化
**计划**：在批量导入导出时，优先处理 `is_server_loaded = TRUE` 的文件。

```java
// 伪代码
List<String> filesToProcess = serverConfigFileDao.findServerLoaded()
    .stream()
    .sorted(Comparator.comparing(ServerConfigFile::getLoadPriority))
    .map(ServerConfigFile::getFileName)
    .toList();

// 优先导入核心文件
for (String fileName : filesToProcess) {
    importXmlFile(fileName);
}
```

### UI 展示增强
**计划**：在文件树中标记服务器已加载的文件。

```java
// 左侧菜单树中添加图标
if (serverConfigFileDao.isServerLoaded(fileName)) {
    menuItem.setGraphic(new Label("✅"));
}
```

### 验证系统集成
**计划**：导入时自动检查文件是否在服务器清单中。

```java
// 导入前检查
Optional<ServerConfigFile> config = dao.findByFileName(fileName);
if (config.isEmpty()) {
    log.warn("⚠️ 文件 {} 不在服务器加载清单中，可能无效", fileName);
}
```

## 典型日志分析案例

### MainServer 日志示例

```log
2025.12.29 09:45.24: OpenDividedMapXmlFiles(), Failed to load L10N ItemID data file, 
    'D:\AionReal58\AionMap\XML\China\item_weapons.xml', Line:1, Col:1 '<' expected

2025.12.29 09:45.26: (quest_random_rewards.xml)(Quest_L_coin_w_16a) 
    quest_random_rewards, item , unknown item name "dagger_n_r0_c_16a"

2025.12.29 09:46.07: 10122 quests loaded successfully in 19625msec
```

**分析结果**：
- `item_weapons.xml` - 服务器尝试加载但失败（编码问题）
- `quest_random_rewards.xml` - 服务器加载成功但存在数据引用错误
- `quests.xml` - 服务器成功加载 10,122 个任务

## 未来增强方向

1. **依赖关系分析** - 分析文件间的引用依赖关系
2. **加载顺序优化** - 根据依赖关系优化导入顺序
3. **变更影响分析** - 修改某个文件后，分析影响哪些其他文件
4. **服务器对比** - 对比 MainServer 和 NPCServer 的加载差异
5. **历史趋势** - 跟踪配置文件加载状态的历史变化
6. **自动备份** - 导入核心文件前自动创建备份

## 命令行工具

### 快速查询

```sql
-- 查看所有服务器加载的文件
SELECT file_name, load_priority, file_category, import_count, export_count
FROM server_config_files
WHERE is_server_loaded = 1
ORDER BY load_priority, file_name;

-- 查看核心配置文件
SELECT file_name, validation_status, import_count
FROM server_config_files
WHERE is_critical = 1;

-- 查看从未导入过的服务器文件
SELECT file_name, file_category
FROM server_config_files
WHERE is_server_loaded = 1 AND import_count = 0;

-- 统计各分类的文件数量
SELECT file_category, COUNT(*) as count
FROM server_config_files
WHERE is_server_loaded = 1
GROUP BY file_category
ORDER BY count DESC;
```

## 总结

服务器配置文件清单系统提供了：
✅ 明确的"文件层真理" - 只关注服务器真正使用的文件  
✅ 可见性 - 清晰展示哪些文件被加载、哪些被忽略  
✅ 优先级管理 - 自动分类核心/重要/一般配置  
✅ 统计追踪 - 导入导出操作的完整记录  
✅ 与工具集成 - 为其他功能提供可靠的文件清单基础

**设计理念**：永远以服务器的视角看待配置文件，而不是盲目扫描目录。
