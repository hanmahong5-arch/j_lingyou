# 服务器配置文件清单 - 快速开始指南

## ✅ 系统已部署成功！

### 当前状态

**数据库表**: ✅ 已创建
- `server_config_files` - 配置文件清单表（24个字段）
- `server_log_analysis` - 日志分析记录表

**测试数据**: ✅ 已导入
- 总文件数：**45个**
- 核心配置：**6个** （items.xml, quest.xml, skill_base.xml 等）
- 重要配置：**8个**（quest_random_rewards.xml, familiar_contract.xml 等）
- 一般配置：**31个**（server_name.xml, bm_config.xml 等）

**UI 集成**: ✅ 已完成
- 工具栏新增「📋 配置清单」按钮
- 完整的管理界面（筛选、搜索、详情查看）

---

## 🚀 如何使用

### 方法1：查看已有数据（推荐先用这个）

1. **启动应用**
   ```bash
   run.bat
   ```

2. **打开配置清单**
   - 点击工具栏「📋 配置清单」按钮
   - 自动加载 45 个服务器配置文件

3. **使用筛选器**
   - **✅ 服务器已加载** - 显示 45 个文件
   - **🔥 核心配置** - 显示 6 个核心文件
   - **📦 物品配置** - 显示 7 个 items 文件
   - **⚔️ 技能配置** - 显示 3 个 skills 文件
   - **📜 任务配置** - 显示 4 个 quests 文件

4. **查看详情**
   - 双击任意行 - 查看文件完整信息
   - 搜索框 - 快速查找文件名或表名

---

### 方法2：分析真实服务器日志（生产环境）

1. **启动应用**
   ```bash
   run.bat
   ```

2. **打开配置清单**
   - 点击工具栏「📋 配置清单」按钮

3. **分析服务器日志**
   - 点击「📊 分析服务器日志」按钮
   - 选择日志目录：`d:/AionReal58/AionServer/MainServer/log`
   - 等待分析完成（通常 5-10 秒）

4. **查看分析结果**
   - 系统会自动：
     - 扫描所有 `.err` 和 `.log` 文件
     - 提取 XML 文件加载记录
     - 推断文件分类和优先级
     - 更新数据库

5. **对比差异**
   - 查看日志分析发现了哪些新文件
   - 对比测试数据和真实服务器的差异

---

## 📊 数据查询示例

### SQL 查询

```sql
-- 连接数据库
mysql -u root -p xmldb_suiyue

-- 查看所有服务器加载的文件
SELECT file_name, table_name, load_priority, file_category
FROM server_config_files
WHERE is_server_loaded = 1
ORDER BY load_priority, file_name;

-- 查看核心配置文件
SELECT file_name, file_category
FROM server_config_files
WHERE is_critical = 1;

-- 按分类统计
SELECT file_category, COUNT(*) as count
FROM server_config_files
GROUP BY file_category
ORDER BY count DESC;

-- 查看从未导入过的服务器文件
SELECT file_name, file_category
FROM server_config_files
WHERE is_server_loaded = 1 AND import_count = 0;
```

---

## 📋 核心配置文件清单（当前数据）

### 🔥 核心配置（优先级 1）- 6个
1. **items.xml** - 物品主配置
2. **item_weapons.xml** - 武器配置
3. **Npc.xml** - NPC 配置
4. **quest.xml** - 任务配置
5. **skills.xml** - 技能主配置
6. **skill_base.xml** - 技能基础配置

### ⚠️ 重要配置（优先级 2）- 8个
- quest_random_rewards.xml - 任务随机奖励
- familiar_contract.xml - 魔宠契约
- familiars.xml - 魔宠
- luna_gotcha.xml - 露娜抽奖
- matchmaker.xml - 匹配系统
- item_enchanttable.xml - 装备强化
- combine_recipe.xml - 合成配方
- disassembly_item_setList.xml - 分解物品

### 📄 一般配置（优先级 3）- 31个
- 服务器配置：server_name.xml, bm_config.xml 等 20 个
- 世界相关：abyss.xml, airports.xml 等 5 个
- 其他功能：housing_address.xml, event_quest.xml 等 6 个

---

## 🔧 未来功能（计划）

### 1. 导入导出优化
**目标**: 优先处理服务器已加载的文件

```java
// 按优先级顺序导入
List<String> filesToImport = dao.findServerLoaded()
    .sorted(by: loadPriority)
    .map(ServerConfigFile::getFileName);
```

### 2. UI 增强
**目标**: 在左侧菜单树中标记服务器文件

```
📁 items
  ✅ items.xml          <- 服务器已加载
  ✅ item_weapons.xml   <- 服务器已加载
  ❌ item_test.xml      <- 服务器未加载
```

### 3. 验证集成
**目标**: 导入前检查文件是否在服务器清单中

```
⚠️ 警告: item_custom.xml 不在服务器加载清单中
是否继续导入？
```

### 4. 依赖关系分析
**目标**: 分析文件间的引用依赖

```
quest.xml
  ├─ 引用 → item_weapons.xml (奖励物品)
  ├─ 引用 → Npc.xml (任务NPC)
  └─ 引用 → skills.xml (任务技能)
```

---

## 📖 相关文档

- **系统架构**: `docs/SERVER_CONFIG_FILE_REGISTRY.md`
- **数据库脚本**: `src/main/resources/sql/server_config_files.sql`
- **日志分析脚本**: `analyze_server_logs.sh`

---

## ❓ 常见问题

### Q: 如何添加新文件到清单？
**A**: 重新分析服务器日志，或手动插入数据库：
```sql
INSERT INTO server_config_files 
(file_name, table_name, is_server_loaded, load_priority, server_module, file_category)
VALUES ('new_file.xml', 'new_table', 1, 3, 'MainServer', 'config');
```

### Q: 如何更新文件优先级？
**A**: 在 UI 中查看详情后，或直接更新数据库：
```sql
UPDATE server_config_files 
SET load_priority = 1, is_critical = 1
WHERE file_name = 'important_file.xml';
```

### Q: 如何清空重新分析？
**A**: 清空表后重新分析：
```sql
DELETE FROM server_config_files;
```
然后在 UI 中点击「分析服务器日志」

---

## ✅ 下一步建议

1. **先熟悉 UI**
   - 启动应用，打开「📋 配置清单」
   - 试用各种筛选器和搜索
   - 双击查看详情

2. **分析真实日志**
   - 点击「分析服务器日志」
   - 对比测试数据和真实数据的差异

3. **集成到工作流**
   - 导入导出时参考清单
   - 修改配置时检查是否在清单中
   - 定期更新清单保持同步

4. **反馈改进**
   - 记录使用中的问题
   - 提出功能改进建议

---

**设计理念**: 永远以服务器的视角看待配置文件 - 这是"文件层的唯一真理"

