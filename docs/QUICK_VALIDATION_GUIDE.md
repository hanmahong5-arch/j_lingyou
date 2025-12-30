# 服务器合规性系统快速验证指南

**预计时间**: 10分钟
**目标**: 验证三层验证机制是否正常工作

---

## 前提条件

- ✅ 已启动应用（run.bat）
- ✅ 数据库连接正常
- ✅ 有至少一个表包含数据（推荐：items或skill_base）

---

## 验证步骤

### 步骤1：验证导出前预验证（2分钟）

**操作**：
1. 打开应用，点击主界面上的 "Aion机制浏览器" 按钮
2. 在机制卡片上右键点击任一机制（例如："物品系统"）
3. 选择 "📤 批量导出 (DB → XML)"
4. 观察批量操作对话框的前几行日志

**预期结果**：
```
开始批量导出数据...
正在进行导出前检查...
预检查完成: X个可导出, Y个有警告
```

**验证点**：
- ✅ 看到 "正在进行导出前检查..." 消息
- ✅ 看到 "预检查完成" 消息
- ✅ 如果有警告，会显示警告数量

**如果失败**：
- 检查 `PreExportValidator.java` 是否编译成功
- 检查日志中是否有异常

---

### 步骤2：验证导出时字段过滤（3分钟）

**操作**：
1. 继续上一步的导出操作，等待导出完成
2. 打开导出的XML文件（在目录树中找到对应的表文件）
3. 用文本编辑器打开，搜索黑名单字段：
   - `__order_index`
   - `status_fx_slot_lv`
   - `toggle_id`

**预期结果**：
- ❌ 搜索不到任何黑名单字段（应该已被过滤）
- ✅ 文件包含正常字段（如 id, name, level等）

**验证点**：
- ✅ XML文件存在且大小>0
- ✅ 不包含 `__order_index` 字段
- ✅ 包含正常字段（id, name等）

**如果失败**：
- 检查 `ServerComplianceFilter.java` 是否被正确调用
- 检查 `DbToXmlGenerator.java` 中是否使用了过滤器

---

### 步骤3：验证导出后合规性检查（3分钟）

**操作**：
1. 继续上一步的导出操作，等待全部完成
2. 观察批量操作对话框的最后几行日志

**预期结果**：
```
正在进行服务器合规性检查...
✅ 合规性检查通过: X 个文件全部符合服务器要求

批量导出完成
```

**验证点**：
- ✅ 看到 "正在进行服务器合规性检查..." 消息
- ✅ 看到合规性检查结果
- ✅ 如果全部合规，显示绿色 ✅ 消息

**如果失败**：
- 检查 `XmlServerComplianceChecker.java` 是否编译成功
- 检查日志中是否有XML解析异常

---

### 步骤4：验证命令行工具（2分钟）

**操作**：
1. 打开命令行终端
2. 切换到项目目录
3. 运行以下命令（替换为实际导出的XML文件路径）：

```bash
# Windows
java -cp "target\classes;target\dependency\*" red.jiuzhou.validation.server.XmlComplianceCheckCLI "src\main\resources\CONF\D\AionReal58\AionMap\XML\China\allNodeXml\items.xml"

# Linux/Mac
java -cp "target/classes:target/dependency/*" red.jiuzhou.validation.server.XmlComplianceCheckCLI "src/main/resources/CONF/D/AionReal58/AionMap/XML/China/allNodeXml/items.xml"
```

**预期结果**：
```
================================================================================
XML服务器合规性检查工具
================================================================================

检查文件: /path/to/items.xml

================================================================================
文件: items.xml
表名: items
服务器兼容性: ✅ 合规
记录数: 12,345
================================================================================

💡 修复建议:
  • ✅ 该文件符合服务器加载要求，可以安全部署

✅ 该文件符合服务器加载要求，可以安全部署。
```

**验证点**：
- ✅ 命令执行成功
- ✅ 显示文件信息
- ✅ 显示合规性状态
- ✅ 退出码为 0

**如果失败**：
- 检查Java classpath是否正确
- 检查文件路径是否存在
- 检查依赖是否完整

---

## 故障排查

### 问题1：预验证不显示

**症状**：批量导出时没有看到 "正在进行导出前检查..." 消息

**可能原因**：
1. `PreExportValidator` 未编译
2. UI集成代码被注释或移除
3. 代码冲突

**解决方法**：
```bash
# 重新编译
mvn clean compile

# 检查代码
grep -n "PreExportValidator" src/main/java/red/jiuzhou/ui/AionMechanismExplorerStage.java
```

### 问题2：导出的XML仍包含黑名单字段

**症状**：在导出的XML文件中搜索到 `__order_index` 等黑名单字段

**可能原因**：
1. `ServerComplianceFilter` 未被调用
2. 验证规则未配置
3. 过滤逻辑有bug

**解决方法**：
1. 检查 `DbToXmlGenerator.java` 是否调用过滤器
2. 检查 `XmlFileValidationRules` 是否包含该表的规则
3. 查看日志中是否有过滤器相关的错误信息

### 问题3：合规性检查不执行

**症状**：导出完成后没有看到 "正在进行服务器合规性检查..." 消息

**可能原因**：
1. `XmlServerComplianceChecker` 未编译
2. UI集成代码被注释
3. 导出流程异常退出

**解决方法**：
```bash
# 重新编译
mvn clean compile

# 检查代码
grep -n "XmlServerComplianceChecker" src/main/java/red/jiuzhou/ui/AionMechanismExplorerStage.java
```

### 问题4：命令行工具找不到类

**症状**：运行命令行工具时报 `ClassNotFoundException`

**可能原因**：
1. classpath不正确
2. 类未编译
3. 包名错误

**解决方法**：
```bash
# 确保已编译
mvn clean compile

# 检查类文件是否存在
ls -la target/classes/red/jiuzhou/validation/server/XmlComplianceCheckCLI.class

# 使用完整classpath
java -cp "target/classes:target/dependency/*" red.jiuzhou.validation.server.XmlComplianceCheckCLI --help
```

---

## 高级验证

### 验证黑名单字段完整性

**创建测试文件**：手动创建一个包含黑名单字段的XML文件：

```xml
<!-- test_blacklist.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<items>
    <item id="1" name="Test Item" __order_index="1" status_fx_slot_lv="5" toggle_id="10">
    </item>
</items>
```

**运行检查**：
```bash
java -cp "target/classes:target/dependency/*" red.jiuzhou.validation.server.XmlComplianceCheckCLI test_blacklist.xml
```

**预期结果**：
```
❌ 发现黑名单字段 (3个):
  • __order_index - 会导致服务器"undefined token"错误
  • status_fx_slot_lv - 会导致服务器"undefined token"错误
  • toggle_id - 会导致服务器"undefined token"错误

⚠️  警告: 该文件不符合服务器加载要求，部署后可能导致服务器错误！
```

---

## 完整性检查清单

**部署前必查**：
- [ ] 导出前预验证正常显示
- [ ] 导出的XML文件不包含黑名单字段
- [ ] 导出后合规性检查通过
- [ ] 命令行工具能正常执行
- [ ] 所有验证规则已配置（90+个表）

**可选检查**：
- [ ] 性能测试（导出大表，如items、skill_base）
- [ ] 压力测试（批量导出所有13个游戏系统）
- [ ] 实际服务器部署验证

---

## 成功标准

如果以上所有验证点都通过，说明服务器合规性系统已正常工作：

✅ **导出前预验证** - 在导出前就知道潜在问题
✅ **导出时字段过滤** - 自动移除92个黑名单字段
✅ **导出后合规性检查** - 确保导出的文件符合服务器要求
✅ **命令行工具** - 提供独立的验证能力

**下一步**：
1. 批量导出所有游戏系统进行全面测试
2. 部署到测试服务器验证实际加载
3. 记录任何新发现的问题
4. 更新验证规则（如需要）

---

## 验证报告模板

```
【服务器合规性系统验证报告】

验证日期：____________________
验证人员：____________________

【验证结果】
□ 步骤1：导出前预验证 - 通过/失败
□ 步骤2：导出时字段过滤 - 通过/失败
□ 步骤3：导出后合规性检查 - 通过/失败
□ 步骤4：命令行工具 - 通过/失败

【发现的问题】
1. _____________________________
2. _____________________________

【建议的改进】
1. _____________________________
2. _____________________________

【总体评价】
□ 系统正常，可以部署
□ 存在问题，需要修复后再部署

签名：____________________
```

---

**文档结束**

如有问题，请查看 `docs/SERVER_COMPLIANCE_IMPLEMENTATION_2025-12-29.md` 获取详细信息。
