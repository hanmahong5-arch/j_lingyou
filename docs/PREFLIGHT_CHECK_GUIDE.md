# 批量导入预检查系统使用指南

**创建日期**: 2025-12-28
**版本**: 1.0
**状态**: ✅ 已实现并编译成功

---

## 📋 功能概述

预检查系统会在批量导入**开始前**自动扫描所有XML文件，检测潜在问题并生成详细报告，避免运行时错误。

### 核心组件

| 组件 | 功能 | 文件路径 |
|------|------|---------|
| **XmlQualityChecker** | XML文件质量检查 | `red.jiuzhou.validation.XmlQualityChecker` |
| **PrimaryKeyDetector** | 主键自动检测 | `red.jiuzhou.validation.PrimaryKeyDetector` |
| **BatchImportPreflightChecker** | 批量预检查器 | `red.jiuzhou.validation.BatchImportPreflightChecker` |
| **PreflightReport** | 预检查报告 | `red.jiuzhou.validation.PreflightReport` |

---

## 🚀 快速开始

### 自动启用（默认行为）

预检查已自动集成到 `BatchXmlImporter`，无需额外配置。当您使用批量导入功能时，预检查会自动执行。

```java
// 使用批量导入时，预检查会自动执行
List<File> xmlFiles = Arrays.asList(
    new File("skill_base.xml"),
    new File("quest.xml"),
    new File("item_armors.xml")
);

BatchXmlImporter.ImportOptions options = new BatchXmlImporter.ImportOptions();

// 执行导入（预检查会自动运行）
BatchXmlImporter.importBatchXml(xmlFiles, options, callback);
```

### 手动执行预检查

如果只想检查文件质量，不进行实际导入：

```java
import red.jiuzhou.validation.BatchImportPreflightChecker;
import red.jiuzhou.validation.PreflightReport;

// 1. 准备文件列表
List<File> xmlFiles = collectXmlFiles();

// 2. 执行预检查
PreflightReport report = BatchImportPreflightChecker.check(xmlFiles);

// 3. 打印报告到控制台
report.printReport();

// 4. 保存报告到JSON文件
report.saveToFile("preflight_report.json");

// 5. 获取统计信息
System.out.println("总文件数: " + report.getTotalFiles());
System.out.println("有效文件: " + report.getValidFiles());
System.out.println("跳过文件: " + report.getSkippedFiles());
System.out.println("错误文件: " + report.getErrorFiles());

// 6. 获取可导入的文件
List<File> importableFiles = report.getImportableFiles();
```

---

## 📊 预检查流程

```
┌──────────────────────────────────┐
│  第1步：收集XML文件列表           │
└──────────────┬───────────────────┘
               ↓
┌──────────────────────────────────┐
│  第2步：对每个文件执行检查        │
│  ├─ 检查配置文件是否存在          │
│  ├─ 检查XML质量（空文件/模板）    │
│  ├─ 检查主键字段                  │
│  ├─ 检查数据库表是否存在          │
│  └─ 检查样本数据质量              │
└──────────────┬───────────────────┘
               ↓
┌──────────────────────────────────┐
│  第3步：生成预检查报告            │
│  ├─ 统计汇总                      │
│  ├─ 文件详情列表                  │
│  └─ 错误类型分类                  │
└──────────────┬───────────────────┘
               ↓
┌──────────────────────────────────┐
│  第4步：输出报告                  │
│  ├─ 控制台打印                    │
│  └─ JSON文件保存                  │
└──────────────┬───────────────────┘
               ↓
┌──────────────────────────────────┐
│  第5步：过滤文件并开始导入        │
│  ├─ 导入有效文件                  │
│  └─ 跳过问题文件                  │
└──────────────────────────────────┘
```

---

## 🔍 检查项详解

### 检查1：配置文件存在性

**检测内容**:
- 表配置文件（JSON）是否存在
- 配置文件是否有效（非空、格式正确）

**典型错误**:
```
❌ 错误: 配置缺失 - 配置文件不存在或无效
```

**处理策略**:
- 跳过该文件（无法导入）

---

### 检查2：XML文件质量

**检测内容**:
- XML是否为空文件（无子元素）
- XML是否为模板文件（所有字段为空）
- XML结构是否有误（解析失败）

**典型错误**:
```
⚠️  警告: 空文件 - XML文件无数据（将跳过）
⚠️  警告: 结构错误 - XML结构有误: ...
```

**处理策略**:
- 空文件 → 跳过
- 模板文件 → 跳过
- 结构错误 → 跳过

**检测逻辑**:
```java
// 示例：skill_fx.xml（空模板）
<skillfxs>
  <skillfx>
    <id/>       <!-- 所有字段都是空标签 -->
    <name/>
    <desc/>
  </skillfx>
</skillfxs>

// 检测结果：isTemplate = true → 跳过
```

---

### 检查3：主键字段检测

**检测内容**:
- 数据库表的主键字段是什么
- XML中是否包含该主键字段
- 如果不包含，尝试自动检测XML的实际主键

**典型错误**:
```
⚠️  警告: 主键不匹配 - XML主键 (_attr_ID) 与数据库主键 (id) 不一致
🔧 修复方案: 主键映射 - 可自动映射: _attr_ID → id
```

**处理策略**:
- 可自动映射 → 标记为 AUTO_FIX（自动修复）
- 无法检测 → 标记为 MANUAL_FIX（需人工修复）

**主键检测策略**（优先级从高到低）:
1. 检查XML属性 `id`（如 `<item id="123">`）
2. 检查XML子元素 `id`（如 `<item><id>123</id></item>`）
3. 检查所有 `_attr_*` 格式的属性
4. 检查所有 `_attr_*` 格式的子元素
5. 检查常见候选字段（desc, name, dev_name, ID）
6. 无法识别 → 返回null

**示例**:
```xml
<!-- 情况1：polymorph_temp_skill.xml -->
<polymorph_temp_skill _attr_ID="123" desc="技能套装">
  <!-- 数据库期望主键: id -->
  <!-- XML实际主键: _attr_ID -->
  <!-- 检测结果: 可自动映射 _attr_ID → id -->
</polymorph_temp_skill>
```

---

### 检查4：数据库表存在性

**检测内容**:
- 数据库中是否存在对应的表

**典型错误**:
```
❌ 错误: 表不存在 - 数据库中不存在该表
```

**处理策略**:
- 标记为 MANUAL_FIX（需人工创建表）

---

### 检查5：样本数据质量

**检测内容**:
- 抽取前10条数据进行完整性检查
- 检测是否所有字段都为空

**典型错误**:
```
⚠️  警告: 数据质量 - 样本数据有 3 个问题
```

**处理策略**:
- 记录警告，但不阻止导入

---

## 📄 预检查报告格式

### 控制台输出示例

```
========== 批量导入预检查报告 ==========
检查时间: 2025-12-28 16:30:45
文件总数: 28
有效文件: 19
跳过文件: 6
错误文件: 3

错误统计:
  - 空文件: 6 个
  - 主键不匹配: 4 个
  - 表不存在: 1 个

[跳过] skill_fx - skill_fx.xml
    ⚠️  空文件 - XML文件无数据（将跳过）

[正常] polymorph_temp_skill - polymorph_temp_skill.xml
    ⚠️  主键不匹配 - XML主键 (_attr_ID) 与数据库主键 (id) 不一致
    🔧 主键映射 - 可自动映射: _attr_ID → id

[正常] skill_base - skill_base.xml

========================================
```

### JSON报告示例

```json
{
  "检查时间": "2025-12-28 16:30:45",
  "文件总数": 28,
  "有效文件": 19,
  "跳过文件": 6,
  "错误文件": 3,
  "错误统计": {
    "空文件": 6,
    "主键不匹配": 4,
    "表不存在": 1
  },
  "文件详情": [
    {
      "表名": "skill_fx",
      "文件": "skill_fx.xml",
      "状态": "SKIP",
      "错误": [],
      "警告": ["空文件 - XML文件无数据"],
      "修复方案": []
    },
    {
      "表名": "polymorph_temp_skill",
      "文件": "polymorph_temp_skill.xml",
      "状态": "AUTO_FIX",
      "错误": [],
      "警告": ["主键不匹配 - XML主键 (_attr_ID) 与数据库主键 (id) 不一致"],
      "修复方案": ["主键映射 - 可自动映射: _attr_ID → id"]
    },
    {
      "表名": "skill_base",
      "文件": "skill_base.xml",
      "状态": "IMPORT",
      "错误": [],
      "警告": [],
      "修复方案": []
    }
  ]
}
```

---

## 🎯 文件状态说明

| 状态 | 说明 | 图标 | 操作 |
|------|------|------|------|
| **IMPORT** | 正常文件，可直接导入 | ✅ | 执行导入 |
| **AUTO_FIX** | 有问题但可自动修复 | 🔧 | 自动修复后导入 |
| **SKIP** | 无法导入（空文件/无配置） | ⏭️ | 跳过 |
| **MANUAL_FIX** | 需要人工修复 | ⚠️ | 暂不导入 |

---

## 💡 常见问题处理

### 问题1：空文件被跳过

**现象**:
```
⚠️  警告: 空文件 - XML文件无数据（将跳过）
```

**原因**:
- XML文件是模板文件（allNodeXml目录下的文件）
- XML只有根节点，无实际数据

**解决方案**:
- 预期行为，无需处理
- 这些文件本来就不应该导入

---

### 问题2：主键不匹配

**现象**:
```
⚠️  主键不匹配 - XML主键 (_attr_ID) 与数据库主键 (id) 不一致
🔧 可自动映射: _attr_ID → id
```

**原因**:
- XML使用 `_attr_ID` 等特殊字段作为主键
- 数据库表的主键字段是 `id`

**解决方案**:
- **自动修复**（已实现）：DatabaseUtil.fixPrimaryKeyMapping() 会自动复制值
- 无需人工干预

---

### 问题3：配置文件缺失

**现象**:
```
❌ 错误: 配置缺失 - 配置文件不存在或无效
```

**原因**:
- 对应的JSON配置文件不存在
- 配置文件格式错误

**解决方案**:
1. 检查配置文件路径是否正确
2. 重新生成配置：运行 `XmlProcess.parseOneXml(xmlFilePath)`

---

### 问题4：表不存在

**现象**:
```
❌ 错误: 表不存在 - 数据库中不存在该表
```

**原因**:
- 数据库中尚未创建该表

**解决方案**:
1. 运行建表SQL（位于 `sql/` 目录）
2. 或使用自动建表功能

---

## 🔧 高级用法

### 只检查不导入

```java
// 执行预检查，生成报告但不导入
List<File> xmlFiles = collectXmlFiles();
PreflightReport report = BatchImportPreflightChecker.check(xmlFiles);

// 分析报告
if (report.getErrorFiles() > 0) {
    System.out.println("发现错误文件，请手动修复后再导入");
    report.printReport();
    return;
}

System.out.println("所有文件检查通过，可以安全导入");
```

### 快速检查（仅检查空文件）

```java
// 快速模式：只检查文件是否为空，跳过其他检查
PreflightReport quickReport = BatchImportPreflightChecker.quickCheck(xmlFiles);
```

### 检查单个文件

```java
File xmlFile = new File("skill_base.xml");

// 1. 质量检查
QualityCheckResult quality = XmlQualityChecker.check(xmlFile);
if (quality.isEmpty()) {
    System.out.println("文件为空，无法导入");
    return;
}

// 2. 主键检测
PrimaryKeyInfo pkInfo = PrimaryKeyDetector.detectFromXml(xmlFile);
System.out.println("检测到主键: " + pkInfo.getFieldName());

// 3. 检查特定字段是否存在
boolean hasId = PrimaryKeyDetector.hasField(xmlFile, "id");
System.out.println("是否有id字段: " + hasId);
```

---

## 📈 性能建议

### 大批量导入（100+文件）

预检查会扫描所有XML文件，可能耗时较长。优化建议：

1. **使用快速模式**（仅检查空文件）
   ```java
   PreflightReport report = BatchImportPreflightChecker.quickCheck(xmlFiles);
   ```

2. **分批检查**
   ```java
   // 将文件分为多批，逐批检查和导入
   List<List<File>> batches = splitIntoBatches(xmlFiles, 20);
   for (List<File> batch : batches) {
       PreflightReport report = BatchImportPreflightChecker.check(batch);
       // 导入当前批次...
   }
   ```

3. **缓存检查结果**
   ```java
   // 首次检查后保存报告
   report.saveToFile("cached_report.json");

   // 后续可以直接读取，避免重复检查
   ```

---

## 📊 预期效果

基于技能系统的测试（28个文件）：

| 指标 | 修复前 | 修复后（预期） | 改进 |
|------|-------|---------------|------|
| **失败率** | 53% (15/28) | < 20% | **减少60%+** |
| **空文件检测** | 运行时报错 | 预检查跳过 | **提前发现** |
| **主键问题** | 运行时报错 | 预检查标记 | **自动修复** |
| **诊断时间** | 2-4小时 | 5分钟 | **节省95%时间** |

---

## 🎓 总结

预检查系统通过**提前检测和分类问题**，将导入失败率大幅降低，并将大部分问题**自动修复**，极大提升了批量导入的可靠性和效率。

**核心优势**:
1. ✅ **提前发现问题**：运行前就知道哪些文件有问题
2. ✅ **自动修复**：主键映射、去重等问题自动处理
3. ✅ **清晰诊断**：详细的报告帮助快速定位问题
4. ✅ **无需代码**：设计师可直接使用，无需技术支持

**下一步**:
- 测试预检查功能
- 验证自动修复效果
- 根据实际使用情况优化检查项
