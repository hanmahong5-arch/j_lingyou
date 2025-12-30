# 导入导出测试 - 快速指南

## ✅ 配置读取错误已修复

刚刚修复了 `DbToXmlGenerator.java` 中服务器合规配置读取错误。

---

## 📋 可测试的表(共5个)

根据文件检查结果,以下表已准备好进行测试:

### 🎯 具有服务器合规规则的表(优先测试)

| 表名 | XML文件 | 配置文件 | 预期过滤效果 |
|------|---------|---------|-------------|
| **items** | ✅ 514MB | ✅ | 移除 `__order_index` (44,324个错误) |
| **boost_time_table** | ✅ | ✅ | 值域约束检查 |

### 📦 其他可测试的表

| 表名 | XML文件 | 配置文件 | 备注 |
|------|---------|---------|------|
| **airports** | ✅ | ✅ | 无专属合规规则 |
| **airline** | ✅ | ✅ | 无专属合规规则 |
| **abyss** | ✅ | ✅ | 无专属合规规则 |

---

## 🚀 快速测试步骤

### 步骤1: 重启应用

**重要**: 必须重启应用以加载修复后的代码!

```bash
# 方式1: 使用启动脚本
quick-start.bat

# 方式2: 使用标准脚本
run.bat
```

### 步骤2: 测试 items 表导出(最重要)

1. **在左侧菜单找到 items 表**
   - 展开目录树
   - 点击 `items`

2. **点击"导出XML"按钮**
   - 查看右侧面板的导出按钮
   - 点击执行导出

3. **观察日志输出** - 应该看到类似以下内容:
   ```
   [INFO] ✅ 服务器合规过滤 [items]:
          处理了X/Y条记录，移除Z个字段，修正W个字段值
   [INFO] 导出完成: data/TEMP/items.xml
   ```

4. **验证导出结果**
   - 导出文件路径: `data/TEMP/items.xml`
   - 检查文件大小 > 0
   - 检查日志中是否有过滤统计信息

### 步骤3: 验证过滤效果(可选)

打开导出的 `items.xml`,搜索以下字段(应该不存在):
- `__order_index` ❌ 应被移除
- `drop_prob_6` ~ `drop_prob_9` ❌ 应被移除
- `drop_monster_6` ~ `drop_monster_9` ❌ 应被移除

### 步骤4: 测试其他表

按照相同步骤测试:
1. boost_time_table(有合规规则)
2. airports
3. airline
4. abyss

---

## 📊 预期测试结果

### Items 表(最重要)

**服务器合规规则**:
- 黑名单字段(应被移除):
  - `__order_index` (44,324 occurrences)
  - `drop_prob_6` ~ `drop_prob_9`
  - `drop_monster_6` ~ `drop_monster_9`
  - `drop_item_6` ~ `drop_item_9`
  - `drop_each_member_6` ~ `drop_each_member_9`

**预期日志输出**:
```
[INFO] ✅ 服务器合规过滤 [items]:
       处理了44,324/44,324条记录，移除44,324个字段，修正0个字段值
```

### Boost_time_table 表

**服务器合规规则**:
- 值域约束检查
- 必填字段验证

---

## ❌ 如果仍然报错

如果重启后仍然出现 `FileNotFoundException: server.compliance.enabled`,请检查:

1. **确认编译成功**:
   ```bash
   mvn compile -DskipTests
   # 应显示 BUILD SUCCESS
   ```

2. **确认 application.yml 存在**:
   ```bash
   ls -l src/main/resources/application.yml
   # 应显示文件存在
   ```

3. **确认配置项存在**:
   ```bash
   grep -A 2 "compliance:" src/main/resources/application.yml
   # 应显示:
   # compliance:
   #   enabled: true
   ```

---

## 📝 测试记录模板

请记录测试结果:

| 表名 | 导出成功? | 过滤统计 | 文件大小 | 备注 |
|------|----------|---------|---------|------|
| items | ⏳ | ___ 个字段被移除 | ___ MB | |
| boost_time_table | ⏳ | ___ | ___ | |
| airports | ⏳ | N/A | ___ | |
| airline | ⏳ | N/A | ___ | |
| abyss | ⏳ | N/A | ___ | |

**图例**:
- ✅ PASS - 导出成功,日志正常
- ⚠️ WARNING - 导出成功但有警告
- ❌ FAIL - 导出失败或有错误
- ⏳ 待测试

---

## 🎯 测试成功标准

### 单表测试通过标准

- ✅ 导出成功(无异常)
- ✅ 文件大小 > 0
- ✅ (如有合规规则) 日志显示过滤统计
- ✅ (如有合规规则) 黑名单字段已移除

### 整体测试通过标准

- ✅ items 表: 成功导出 + 显示过滤了 44,324 个 `__order_index` 字段
- ✅ boost_time_table 表: 成功导出 + 显示值域检查
- ✅ 其他3个表: 至少 2/3 成功导出

---

**编写日期**: 2025-12-29
**最后更新**: 2025-12-29 21:47
**作者**: Claude Code
