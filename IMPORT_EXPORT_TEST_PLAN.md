# 导入导出往返测试计划

## 测试目标

逐个测试现有XML文件的导入导出功能，验证：
1. ✅ 导入功能正常
2. ✅ 导出功能正常
3. ✅ 服务器合规过滤器生效
4. ✅ 往返一致性
5. ✅ 服务器加载零错误

---

## 测试策略

### 阶段1：核心表测试（优先级：HIGH）

重点测试**服务器合规过滤器支持的18个表**，这些表有专属验证规则：

| # | 表名 | XML文件 | 配置文件 | 预期过滤项 | 状态 |
|---|------|--------|---------|-----------|------|
| 1 | skills | skills.xml | ✓ | status_fx_slot_lv, toggle_id | ⏳ 待测试 |
| 2 | items | items.xml | ✓ | __order_index, drop_prob_6~9 | ⏳ 待测试 |
| 3 | npc_templates | npc_templates.xml | ✓ | 黑名单字段 | ⏳ 待测试 |
| 4 | quests | quests.xml | ✓ | 必填字段检查 | ⏳ 待测试 |
| 5 | boost_time_table | boost_time_table.xml | ✓ | 值域约束 | ⏳ 待测试 |

### 阶段2：常用表测试（优先级：MEDIUM）

| # | 表名 | XML文件 | 状态 |
|---|------|--------|------|
| 6 | airports | airports.xml | ⏳ 待测试 |
| 7 | airline | airline.xml | ⏳ 待测试 |
| 8 | abyss | abyss.xml | ⏳ 待测试 |
| 9 | abyss_rank | abyss_rank.xml | ⏳ 待测试 |
| 10 | teleport_locations | teleport_locations.xml | ⏳ 待测试 |

### 阶段3：抽样测试（优先级：LOW）

从6585个XML文件中随机抽样20-30个进行测试。

---

## 测试步骤（每个表）

### 步骤1：准备工作

1. 启动应用：`quick-start.bat`
2. 确认服务器合规过滤器已启用：
   ```yaml
   # application.yml
   server:
     compliance:
       enabled: true
   ```
3. 打开日志窗口（查看过滤统计）

### 步骤2：测试导入

1. **选择XML文件**
   - 从文件浏览器打开：`D:\AionReal58\AionMap\XML\[表名].xml`

2. **执行导入**
   - 在工具中选择"导入XML"
   - 选择对应的XML文件

3. **验证导入结果**
   - [ ] 导入成功（无异常）
   - [ ] 查看日志：确认记录数
   - [ ] 数据库验证：`SELECT COUNT(*) FROM [表名]`

4. **记录数据**
   - 导入记录数：_______
   - 导入耗时：_______
   - 错误信息（如有）：_______

### 步骤3：测试导出

1. **执行导出**
   - 在左侧菜单选择表
   - 点击"导出XML"按钮

2. **观察日志输出**
   - [ ] 查看服务器合规过滤统计：
     ```
     [INFO] ✅ 服务器合规过滤 [表名]:
           处理了X/Y条记录，移除Z个字段，修正W个字段值
     ```

3. **验证导出结果**
   - [ ] 导出成功（无异常）
   - [ ] 导出文件存在：`data/TEMP/[表名].xml`
   - [ ] 文件大小合理（> 0 字节）

4. **记录数据**
   - 导出记录数：_______
   - 移除字段数：_______
   - 修正字段数：_______
   - 导出耗时：_______
   - 错误信息（如有）：_______

### 步骤4：往返一致性检查

1. **对比原始XML和导出XML**
   ```bash
   # 使用文本对比工具
   fc D:\AionReal58\AionMap\XML\[表名].xml data\TEMP\[表名].xml
   ```

2. **验证关键字段**
   - [ ] ID字段保持一致
   - [ ] 核心数据未丢失
   - [ ] 黑名单字段已移除
   - [ ] 值域约束字段已修正

3. **记录差异**
   - 差异行数：_______
   - 主要差异（预期的过滤项）：_______
   - 意外差异（需要修复）：_______

### 步骤5：服务器验证（关键步骤）

1. **复制导出的XML到服务器**
   ```bash
   copy data\TEMP\[表名].xml D:\AionReal58\AionServer\[对应目录]\
   ```

2. **重启服务器**
   - 停止服务器
   - 启动服务器
   - 观察启动日志

3. **检查错误日志**
   ```bash
   # 查看错误日志
   tail -100 D:\AionReal58\AionServer\MainServer\log\[日期].err
   ```

4. **验证结果**
   - [ ] 服务器启动成功
   - [ ] 该表加载零错误
   - [ ] 游戏内功能正常

5. **记录结果**
   - 错误数量：_______
   - 错误详情（如有）：_______

---

## 测试记录表

### Skills 表测试

| 项目 | 结果 | 备注 |
|------|------|------|
| **导入测试** |||
| 导入记录数 | | |
| 导入耗时 | | |
| 导入状态 | ⏳ 待测试 | |
| **导出测试** |||
| 导出记录数 | | |
| 移除字段数 | | 预期：status_fx_slot_lv, toggle_id |
| 修正字段数 | | 预期：casting_delay |
| 导出耗时 | | |
| 导出状态 | ⏳ 待测试 | |
| **往返一致性** |||
| 差异类型 | | 仅黑名单字段差异 = PASS |
| 一致性状态 | ⏳ 待测试 | |
| **服务器验证** |||
| 错误数量 | | 预期：0 |
| 加载状态 | ⏳ 待测试 | |
| **综合结论** | ⏳ 待测试 | PASS / FAIL |

### Items 表测试

| 项目 | 结果 | 备注 |
|------|------|------|
| **导入测试** |||
| 导入记录数 | | |
| 导入耗时 | | |
| 导入状态 | ⏳ 待测试 | |
| **导出测试** |||
| 导出记录数 | | |
| 移除字段数 | | 预期：__order_index, drop_prob_6~9 |
| 修正字段数 | | |
| 导出耗时 | | |
| 导出状态 | ⏳ 待测试 | |
| **往返一致性** |||
| 差异类型 | | |
| 一致性状态 | ⏳ 待测试 | |
| **服务器验证** |||
| 错误数量 | | 预期：0 |
| 加载状态 | ⏳ 待测试 | |
| **综合结论** | ⏳ 待测试 | PASS / FAIL |

### NPC Templates 表测试

| 项目 | 结果 | 备注 |
|------|------|------|
| **导入测试** |||
| 导入记录数 | | |
| 导入耗时 | | |
| 导入状态 | ⏳ 待测试 | |
| **导出测试** |||
| 导出记录数 | | |
| 移除字段数 | | |
| 修正字段数 | | |
| 导出耗时 | | |
| 导出状态 | ⏳ 待测试 | |
| **往返一致性** |||
| 差异类型 | | |
| 一致性状态 | ⏳ 待测试 | |
| **服务器验证** |||
| 错误数量 | | 预期：0 |
| 加载状态 | ⏳ 待测试 | |
| **综合结论** | ⏳ 待测试 | PASS / FAIL |

---

## 快速测试脚本

### 检查XML文件和配置文件

```bash
# 检查主要表的文件是否齐全
cd D:\AionReal58\AionMap\XML

# Skills
ls -l skills.xml
ls -l D:\workspace\dbxmlTool\src\main\resources\CONF\D\AionReal58\AionMap\XML\skills.json

# Items
ls -l items.xml
ls -l D:\workspace\dbxmlTool\src\main\resources\CONF\D\AionReal58\AionMap\XML\items.json

# NPC Templates
ls -l npc_templates.xml
ls -l D:\workspace\dbxmlTool\src\main\resources\CONF\D\AionReal58\AionMap\XML\npc_templates.json
```

### 数据库记录数查询

```sql
-- 导入前后对比
SELECT 'skills' AS table_name, COUNT(*) AS record_count FROM skills
UNION ALL
SELECT 'items', COUNT(*) FROM items
UNION ALL
SELECT 'npc_templates', COUNT(*) FROM npc_templates
UNION ALL
SELECT 'quests', COUNT(*) FROM quests;
```

### 服务器错误日志检查

```bash
# 检查MainServer错误
grep "skills\|items\|npc_templates\|quests" D:\AionReal58\AionServer\MainServer\log\2025-12-29.err | grep -i "error\|undefined"

# 检查NPCServer错误
grep "skills\|items\|npc_templates\|quests" D:\AionReal58\AionServer\NPCServer\log\2025-12-29.err | grep -i "error\|undefined"
```

---

## 测试通过标准

### 单个表测试PASS标准

- ✅ 导入成功（记录数 > 0）
- ✅ 导出成功（文件大小 > 0）
- ✅ 往返一致性（仅黑名单字段差异）
- ✅ 服务器加载零错误

### 整体测试PASS标准

- ✅ 核心5个表：100% PASS
- ✅ 常用5个表：≥ 80% PASS
- ✅ 抽样20个表：≥ 70% PASS

---

## 问题记录

### 问题模板

**问题编号**：#001
**表名**：[表名]
**测试阶段**：导入 / 导出 / 往返一致性 / 服务器验证
**问题描述**：[详细描述]
**错误日志**：
```
[粘贴错误日志]
```
**复现步骤**：
1. [步骤1]
2. [步骤2]
3. ...

**影响范围**：Critical / High / Medium / Low
**解决方案**：[描述或留空待处理]
**状态**：Open / In Progress / Resolved

---

## 测试时间安排

| 阶段 | 表数量 | 预计耗时 | 负责人 | 状态 |
|------|--------|---------|--------|------|
| 阶段1：核心表 | 5 | 2小时 | | ⏳ 待开始 |
| 阶段2：常用表 | 5 | 1.5小时 | | ⏳ 待开始 |
| 阶段3：抽样测试 | 20 | 3小时 | | ⏳ 待开始 |
| **合计** | **30** | **6.5小时** | | |

---

## 测试总结（测试完成后填写）

### 测试结果统计

- 总测试数：_______
- 通过数：_______
- 失败数：_______
- 通过率：_______%

### 发现的问题

1. [问题1描述]
2. [问题2描述]
3. ...

### 服务器合规过滤器效果

- 总过滤字段数：_______
- 总修正值数：_______
- 消除的服务器错误数：_______

### 改进建议

1. [建议1]
2. [建议2]
3. ...

---

**测试计划编写日期**：2025-12-29
**测试执行日期**：_______
**测试完成日期**：_______
**测试负责人**：_______
