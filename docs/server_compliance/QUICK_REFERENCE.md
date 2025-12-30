# 服务器合规性过滤器 - 快速参考

> 一页纸速查手册

---

## 核心概念

**宽进严出原则**：
- ✅ 导入（DB→XML）：保留所有字段
- ✅ 导出（XML→Server）：自动过滤不兼容字段

---

## 最常用代码

### 1. 基本用法

```java
// 创建过滤器
ServerComplianceFilter filter = new ServerComplianceFilter();

// 单条数据过滤
FilterResult result = filter.filterForExport("items", itemData);
Map<String, Object> cleanData = result.getFilteredData();

// 批量数据过滤
List<FilterResult> results = filter.filterBatch("items", itemList);
```

### 2. 检查过滤结果

```java
if (result.hasChanges()) {
    System.out.println("移除字段: " + result.getRemovedFields());
}

if (result.hasWarnings()) {
    result.getWarnings().forEach(System.out::println);
}
```

### 3. 生成报告

```java
// 单条数据报告
String report = filter.generateFilterReport("items", result);

// 批量数据统计
String stats = filter.generateBatchFilterStatistics("items", results);
```

---

## TOP错误字段

| 字段 | 错误次数 | 解决方案 |
|------|---------|---------|
| `__order_index` | 44,324 | 自动移除 |
| `status_fx_slot_lv` | 405 | 自动移除 |
| `toggle_id` | 378 | 自动移除 |
| `drop_prob_6~9` | 24 | 自动移除 |

---

## 规则查询

```java
// 检查是否有规则
filter.hasRules("items");

// 获取规则详情
Optional<FileValidationRule> rule = filter.getRule("items");

// 查看所有表
Set<String> tables = XmlFileValidationRules.getAllTableNames();

// 规则统计
String summary = XmlFileValidationRules.generateRuleSummary();
```

---

## 集成到导出流程

```java
public void generateXml(String tableName, String outputPath) {
    // 1. 读取数据
    List<Map<String, Object>> dataList = queryFromDatabase(tableName);

    // 2. 过滤数据
    ServerComplianceFilter filter = new ServerComplianceFilter();
    List<FilterResult> results = filter.filterBatch(tableName, dataList);

    // 3. 记录日志
    logger.info(filter.generateBatchFilterStatistics(tableName, results));

    // 4. 使用过滤后的数据
    List<Map<String, Object>> cleanData = results.stream()
        .map(FilterResult::getFilteredData)
        .collect(Collectors.toList());

    // 5. 生成XML
    writeToXml(cleanData, outputPath);
}
```

---

## 已支持的表（18个）

### 核心表
- `items` (14个黑名单字段)
- `skills` (4个黑名单字段)
- `quest_random_rewards`
- `npcs`

### 物品分类表
- `item_weapons`
- `item_armors`
- `item_accessories`
- `item_consumables`
- `item_materials`
- `item_quest`

### 技能相关表
- `skill_learns`
- `skill_charge`
- `skill_conflictcounts`
- `skill_damageattenuation`
- `skill_prohibit`
- `skill_qualification`
- `skill_randomdamage`
- `skill_signetdata`

---

## 故障排查

| 问题 | 检查点 |
|------|--------|
| 规则不生效 | 1. 表名是否正确？<br>2. 是否调用了`filterForExport()`？<br>3. 是否使用了`getFilteredData()`？ |
| 性能慢 | 使用 `parallelStream()` 或在SQL查询时就过滤字段 |
| 数据为空 | 检查是否所有字段都被移除了 |
| 必填字段警告 | 补充默认值 |

---

## 统计数据

- **分析日志行数**: 206,352
- **识别错误模式**: 22,891
- **构建规则表数**: 18
- **总规则数**: 138
- **黑名单字段**: 92
- **覆盖错误率**: ~100%

---

## 相关文档

📄 **详细分析**: `SERVER_COMPLIANCE_ANALYSIS.md`
📘 **使用指南**: `USAGE_GUIDE.md`
📊 **错误统计**: `error_statistics.csv`

---

**版本**: 1.0 | **日期**: 2025-12-29 | **作者**: Claude Code
