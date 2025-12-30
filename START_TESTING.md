# 开始导入导出测试 - 快速指南

## 🎯 测试目标

验证所有XML文件的导入导出功能正常，并确认服务器合规过滤器成功消除服务器加载错误。

---

## 📋 准备工作（5分钟）

### 1. 检查文件准备情况

运行检查脚本：
```bash
check-test-files.bat
```

**预期输出**：
```
✅ 有 8 个表可以进行测试

下一步：
  1. 启动应用: quick-start.bat
  2. 查看测试计划: IMPORT_EXPORT_TEST_PLAN.md
  3. 开始测试第一个表
```

### 2. 启动应用

```bash
quick-start.bat
```

### 3. 确认服务器合规过滤器已启用

检查 `src\main\resources\application.yml`：
```yaml
server:
  compliance:
    enabled: true  # ✅ 确保是 true
```

### 4. 打开测试计划文档

```bash
notepad IMPORT_EXPORT_TEST_PLAN.md
```

---

## 🚀 快速测试（15分钟）

### 测试第1个表：Skills

#### Step 1: 导出 Skills

1. **在应用左侧菜单找到 `skills` 表**
   - 展开目录树
   - 点击 `skills`

2. **点击导出按钮**
   - 查看右侧面板
   - 点击"导出XML"按钮

3. **观察日志输出**
   ```
   [INFO] sql: SELECT * FROM skills LIMIT 1000 OFFSET 0
   [INFO] ✅ 服务器合规过滤 [skills]:
          处理了127/1000条记录，移除405个字段，修正12个字段值
   [INFO] 进度：1000/2547，完成度：39.3%
   [INFO] ✅ 文件已导出到: D:\workspace\dbxmlTool\data\TEMP\skills.xml
   ```

4. **记录关键数据**
   - 导出记录数：_______
   - 移除字段数：_______ （预期：status_fx_slot_lv, toggle_id）
   - 修正字段数：_______
   - 导出文件：`data\TEMP\skills.xml`

5. **验证导出文件**
   ```bash
   # 查看文件大小（应该 > 0）
   dir data\TEMP\skills.xml

   # 打开文件查看（可选）
   notepad data\TEMP\skills.xml
   ```

#### Step 2: 验证过滤效果

1. **检查黑名单字段已移除**

   在导出的 `skills.xml` 中搜索（应该找不到）：
   - `status_fx_slot_lv` ❌ 不应存在
   - `toggle_id` ❌ 不应存在
   - `is_familiar_skill` ❌ 不应存在

2. **检查值域修正**

   搜索 `casting_delay`，值应该 ≤ 30000

#### Step 3: 服务器验证（关键步骤）

1. **备份原始文件**
   ```bash
   copy D:\AionReal58\AionMap\XML\skills.xml D:\AionReal58\AionMap\XML\skills.xml.backup
   ```

2. **复制导出文件到服务器**
   ```bash
   copy data\TEMP\skills.xml D:\AionReal58\AionMap\XML\skills.xml
   ```

3. **重启服务器**
   - 停止 MainServer 和 NPCServer
   - 启动 MainServer 和 NPCServer
   - 观察启动日志

4. **检查错误日志**
   ```bash
   # 在服务器日志中搜索 skills 相关错误
   grep -i "skilldb.*error\|skilldb.*undefined" D:\AionReal58\AionServer\MainServer\log\2025-12-29.err
   grep -i "skilldb.*error\|skilldb.*undefined" D:\AionReal58\AionServer\NPCServer\log\2025-12-29.err
   ```

5. **验证结果**
   - [ ] ✅ 服务器启动成功
   - [ ] ✅ Skills 表加载零错误
   - [ ] ✅ 游戏内技能功能正常

6. **恢复原始文件（如果需要）**
   ```bash
   copy D:\AionReal58\AionMap\XML\skills.xml.backup D:\AionReal58\AionMap\XML\skills.xml
   ```

---

### 测试第2个表：Items

重复上述步骤，重点关注：

**预期过滤项**：
- `__order_index` ❌ 应被移除（44,324次）
- `drop_prob_6` ~ `drop_prob_9` ❌ 应被移除
- `drop_monster_6` ~ `drop_monster_9` ❌ 应被移除
- `drop_item_6` ~ `drop_item_9` ❌ 应被移除
- `drop_each_member_6` ~ `drop_each_member_9` ❌ 应被移除

**服务器验证**：
```bash
grep -i "itemdb.*error\|itemdb.*undefined" D:\AionReal58\AionServer\MainServer\log\2025-12-29.err
```

---

### 测试第3个表：NPC Templates

（如果 npc_templates.xml 文件存在）

**服务器验证**：
```bash
grep -i "npc.*error\|npc.*undefined" D:\AionReal58\AionServer\MainServer\log\2025-12-29.err
```

---

## 📊 测试记录表

请在测试时填写以下表格：

| 表名 | 导出记录数 | 移除字段数 | 修正字段数 | 服务器错误数 | 状态 |
|------|-----------|-----------|-----------|-------------|------|
| skills | _______ | _______ | _______ | _______ | ⏳ |
| items | _______ | _______ | _______ | _______ | ⏳ |
| npc_templates | _______ | _______ | _______ | _______ | ⏳ |
| quests | _______ | _______ | _______ | _______ | ⏳ |
| airports | _______ | _______ | _______ | _______ | ⏳ |

**状态说明**：
- ✅ PASS：导出成功 + 服务器零错误
- ⚠️ WARNING：导出成功但有警告
- ❌ FAIL：导出失败或服务器有错误
- ⏳ 待测试

---

## 🎯 成功标准

### 单表测试通过标准

- ✅ 导出成功（记录数 > 0）
- ✅ 日志显示过滤统计
- ✅ 导出文件大小 > 0
- ✅ 黑名单字段已移除
- ✅ 服务器加载零错误

### 整体测试通过标准

**核心目标**：
- Skills 表：✅ 消除 783 个错误（status_fx_slot_lv + toggle_id）
- Items 表：✅ 消除 44,324 个错误（__order_index）

**总体目标**：
- 消除 65,131 个服务器错误 → 0 个

---

## 🐛 常见问题

### Q1：导出时没有看到过滤统计日志？

**A**：检查以下几点：
1. 确认 `application.yml` 中 `server.compliance.enabled: true`
2. 该表可能没有专属规则（查看日志是否有 "✅ 服务器合规过滤"）
3. 该表的数据可能没有黑名单字段

### Q2：服务器仍然报错？

**A**：检查：
1. 确认使用的是导出的XML（不是原始XML）
2. 查看错误是否是该表的（可能是其他表）
3. 查看错误字段是否在黑名单中（可能是新发现的字段）

### Q3：导出文件比原始文件小很多？

**A**：这是正常的！过滤器移除了大量黑名单字段：
- Skills: 移除 405 个字段实例
- Items: 移除 44,324 个字段实例

---

## 📖 相关文档

- **测试计划详细版**：`IMPORT_EXPORT_TEST_PLAN.md`
- **集成说明**：`SERVER_COMPLIANCE_INTEGRATION.md`
- **规则详解**：`docs/server_compliance/README.md`
- **分析报告**：`docs/server_compliance/SERVER_COMPLIANCE_ANALYSIS.md`

---

## ⏱️ 预计耗时

| 阶段 | 耗时 |
|------|------|
| 准备工作 | 5 分钟 |
| 测试 Skills | 5 分钟 |
| 测试 Items | 5 分钟 |
| 测试其他3个表 | 15 分钟 |
| **总计** | **30 分钟** |

---

## ✅ 测试清单

### 准备阶段
- [ ] 运行 `check-test-files.bat` 检查文件
- [ ] 启动应用 `quick-start.bat`
- [ ] 确认服务器合规过滤器已启用
- [ ] 打开测试计划文档

### 测试阶段
- [ ] 测试 Skills 表
  - [ ] 导出成功
  - [ ] 过滤统计正常
  - [ ] 服务器验证通过
- [ ] 测试 Items 表
  - [ ] 导出成功
  - [ ] 过滤统计正常
  - [ ] 服务器验证通过
- [ ] 测试其他表（至少3个）

### 总结阶段
- [ ] 填写测试记录表
- [ ] 统计总体通过率
- [ ] 记录发现的问题
- [ ] 编写测试总结

---

## 🎉 预期成果

完成测试后，您将获得：

1. ✅ **验证的核心表**：至少5个表的完整测试
2. ✅ **零错误导出**：导出的XML符合服务器要求
3. ✅ **消除的错误数**：预期消除 65,131 个服务器错误
4. ✅ **测试数据**：详细的测试记录和统计
5. ✅ **问题清单**：发现的问题和改进建议

---

**准备好了吗？**

现在运行 `check-test-files.bat` 开始测试吧！

---

**编写日期**：2025-12-29
**作者**：Claude Code
