# 服务器合规性过滤器 - 使用指南

> **快速开始指南** - 如何在导出XML时自动应用服务器验证规则

---

## 目录

1. [快速开始](#快速开始)
2. [基本用法](#基本用法)
3. [高级功能](#高级功能)
4. [集成到现有代码](#集成到现有代码)
5. [规则管理](#规则管理)
6. [故障排查](#故障排查)

---

## 快速开始

### 1分钟上手

```java
import red.jiuzhou.validation.server.ServerComplianceFilter;
import red.jiuzhou.validation.server.ServerComplianceFilter.FilterResult;

// 1. 创建过滤器
ServerComplianceFilter filter = new ServerComplianceFilter();

// 2. 准备要导出的数据
Map<String, Object> itemData = Map.of(
    "id", 100000001,
    "name", "测试物品",
    "level", 50,
    "__order_index", 1,  // 这个字段会被自动移除
    "drop_prob_6", 0.5   // 这个字段会被自动移除
);

// 3. 应用过滤
FilterResult result = filter.filterForExport("items", itemData);

// 4. 获取过滤后的数据
Map<String, Object> cleanData = result.getFilteredData();
// cleanData 中不再包含 __order_index 和 drop_prob_6

// 5. 查看过滤日志
if (result.hasChanges()) {
    System.out.println("移除的字段: " + result.getRemovedFields());
    // 输出: 移除的字段: [__order_index, drop_prob_6]
}
```

---

## 基本用法

### 1. 单条数据过滤

```java
ServerComplianceFilter filter = new ServerComplianceFilter();

// 从数据库读取数据
Map<String, Object> skillData = jdbcTemplate.queryForMap(
    "SELECT * FROM skills WHERE id = ?", skillId
);

// 应用过滤规则
FilterResult result = filter.filterForExport("skills", skillData);

// 检查是否有修改
if (result.hasChanges()) {
    logger.info("数据已过滤：");
    logger.info("  移除字段: {}", result.getRemovedFields());
    logger.info("  修正字段: {}", result.getCorrectedFields());
}

// 检查是否有警告
if (result.hasWarnings()) {
    result.getWarnings().forEach(warning ->
        logger.warn("验证警告: {}", warning)
    );
}

// 使用过滤后的数据
Map<String, Object> cleanData = result.getFilteredData();
exportToXml(cleanData);
```

### 2. 批量数据过滤

```java
ServerComplianceFilter filter = new ServerComplianceFilter();

// 从数据库读取所有数据
List<Map<String, Object>> allItems = jdbcTemplate.queryForList(
    "SELECT * FROM items"
);

// 批量过滤
List<FilterResult> results = filter.filterBatch("items", allItems);

// 生成批量过滤统计报告
String statistics = filter.generateBatchFilterStatistics("items", results);
System.out.println(statistics);

// 提取过滤后的数据
List<Map<String, Object>> cleanDataList = results.stream()
    .map(FilterResult::getFilteredData)
    .collect(Collectors.toList());

// 导出到XML
exportToXml(cleanDataList);
```

### 3. 生成详细报告

```java
FilterResult result = filter.filterForExport("items", itemData);

// 生成详细的过滤报告
String report = filter.generateFilterReport("items", result);
System.out.println(report);
```

**输出示例**：

```
================================================================================
表 items 的导出过滤报告
================================================================================

移除的黑名单字段 (3个):
  - __order_index
  - drop_prob_6
  - erect

修正的字段值 (1个):
  - stack: 10000 -> 9999 (超出范围)

警告 (0个):

================================================================================
```

---

## 高级功能

### 1. 检查表是否有规则

```java
ServerComplianceFilter filter = new ServerComplianceFilter();

if (filter.hasRules("items")) {
    System.out.println("items 表有验证规则");
} else {
    System.out.println("items 表没有定义验证规则，将跳过过滤");
}
```

### 2. 获取表的验证规则详情

```java
import red.jiuzhou.validation.server.FileValidationRule;

Optional<FileValidationRule> ruleOpt = filter.getRule("skills");

if (ruleOpt.isPresent()) {
    FileValidationRule rule = ruleOpt.get();

    System.out.println("表名: " + rule.getTableName());
    System.out.println("XML文件: " + rule.getXmlFileName());
    System.out.println("黑名单字段: " + rule.getBlacklistFields());
    System.out.println("必填字段: " + rule.getRequiredFields());
    System.out.println("引用字段: " + rule.getReferenceFields());
}
```

### 3. 查看所有已注册的规则

```java
import red.jiuzhou.validation.server.XmlFileValidationRules;

// 获取所有表名
Set<String> allTables = XmlFileValidationRules.getAllTableNames();
System.out.println("已注册的表: " + allTables);

// 获取所有规则
Collection<FileValidationRule> allRules = XmlFileValidationRules.getAllRules();
System.out.println("总规则数: " + XmlFileValidationRules.getTotalRuleCount());

// 生成规则统计报告
String summary = XmlFileValidationRules.generateRuleSummary();
System.out.println(summary);
```

**输出示例**：

```
================================================================================
服务器合规性验证规则统计
================================================================================
共注册 18 个XML文件/表的验证规则
总规则数：138 条

规则详情：
--------------------------------------------------------------------------------
表: items                          规则数:  17  黑名单字段: 14  必填字段:  3
表: skills                         规则数:  10  黑名单字段:  4  必填字段:  3
表: quest_random_rewards           规则数:   2  黑名单字段:  1  必填字段:  1
表: npcs                           规则数:   4  黑名单字段:  1  必填字段:  3
...
================================================================================
```

### 4. 黑名单字段统计

```java
import red.jiuzhou.validation.server.XmlFileValidationRules;

// 获取黑名单字段的使用频率
Map<String, Integer> blacklistStats =
    XmlFileValidationRules.getBlacklistFieldStatistics();

// 按频率排序并输出
blacklistStats.entrySet().stream()
    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
    .forEach(entry ->
        System.out.printf("字段 %s 在 %d 个表中被禁用\n",
            entry.getKey(), entry.getValue())
    );
```

**输出示例**：

```
字段 __order_index 在 18 个表中被禁用
字段 drop_prob_6 在 7 个表中被禁用
字段 drop_prob_7 在 7 个表中被禁用
字段 status_fx_slot_lv 在 9 个表中被禁用
字段 toggle_id 在 9 个表中被禁用
```

---

## 集成到现有代码

### 方案1：修改 DbToXmlGenerator

**文件**: `src/main/java/red/jiuzhou/dbxml/DbToXmlGenerator.java`

```java
package red.jiuzhou.dbxml;

import red.jiuzhou.validation.server.ServerComplianceFilter;
import red.jiuzhou.validation.server.ServerComplianceFilter.FilterResult;

public class DbToXmlGenerator {

    // ✨ 新增：服务器合规性过滤器
    private final ServerComplianceFilter complianceFilter = new ServerComplianceFilter();

    public void generateXml(String tableName, String outputPath) {
        try {
            logger.info("开始导出表 {} 到 XML", tableName);

            // 1. 从数据库读取数据
            List<Map<String, Object>> dataList = jdbcTemplate.queryForList(
                "SELECT * FROM " + tableName
            );

            // ✨ 2. 应用服务器合规性过滤
            if (complianceFilter.hasRules(tableName)) {
                logger.info("为表 {} 应用服务器合规性规则", tableName);

                List<FilterResult> filterResults = complianceFilter.filterBatch(tableName, dataList);

                // 生成过滤统计报告
                String filterReport = complianceFilter.generateBatchFilterStatistics(
                    tableName, filterResults
                );
                logger.info("过滤统计:\n{}", filterReport);

                // 使用过滤后的数据
                dataList = filterResults.stream()
                    .map(FilterResult::getFilteredData)
                    .collect(Collectors.toList());
            }

            // 3. 生成XML（使用过滤后的数据）
            generateXmlFromData(dataList, outputPath);

            logger.info("表 {} 导出完成", tableName);

        } catch (Exception e) {
            logger.error("导出表 {} 失败", tableName, e);
            throw new RuntimeException("导出失败", e);
        }
    }

    // ... 其他现有方法 ...
}
```

### 方案2：创建包装方法

如果不想修改现有代码，可以创建一个包装类：

```java
package red.jiuzhou.dbxml;

import red.jiuzhou.validation.server.ServerComplianceFilter;

public class ServerCompliantDbToXmlGenerator extends DbToXmlGenerator {

    private final ServerComplianceFilter filter = new ServerComplianceFilter();

    @Override
    protected List<Map<String, Object>> preprocessData(
            String tableName,
            List<Map<String, Object>> dataList) {

        if (!filter.hasRules(tableName)) {
            return dataList;
        }

        List<FilterResult> results = filter.filterBatch(tableName, dataList);

        String statistics = filter.generateBatchFilterStatistics(tableName, results);
        logger.info(statistics);

        return results.stream()
            .map(FilterResult::getFilteredData)
            .collect(Collectors.toList());
    }
}
```

### 方案3：配置开关

添加一个配置项，允许启用/禁用过滤：

**application.yml**:

```yaml
dbxmltool:
  export:
    enable-server-compliance: true  # 是否启用服务器合规性过滤
    compliance-report-level: INFO   # 过滤报告日志级别: DEBUG/INFO/WARN
```

**代码**:

```java
@Value("${dbxmltool.export.enable-server-compliance:true}")
private boolean enableServerCompliance;

public void generateXml(String tableName, String outputPath) {
    // ...

    if (enableServerCompliance) {
        dataList = applyServerComplianceFilter(tableName, dataList);
    }

    // ...
}
```

---

## 规则管理

### 查看当前规则

```java
import red.jiuzhou.validation.server.XmlFileValidationRules;

// 打印所有规则
String summary = XmlFileValidationRules.generateRuleSummary();
System.out.println(summary);
```

### 检查特定字段是否被禁用

```java
FileValidationRule rule = XmlFileValidationRules.getRule("items").orElse(null);

if (rule != null) {
    boolean isBlacklisted = rule.isBlacklisted("__order_index");
    System.out.println("__order_index 是否在黑名单中: " + isBlacklisted);
}
```

### 添加新规则

**文件**: `XmlFileValidationRules.java`

```java
// 在 initializeRules() 方法中添加：

RULES.put("your_new_table", new FileValidationRule.Builder("your_new_table")
    .xmlFileName("your_new_table.xml")
    .description("你的表的描述")
    .addBlacklistFields("field1", "field2")
    .addRequiredFields("id", "name")
    .build()
);
```

---

## 故障排查

### 问题1：过滤后数据为空

**症状**：过滤后的数据完全为空

**原因**：可能所有字段都被移除了

**解决**：
```java
FilterResult result = filter.filterForExport(tableName, data);

if (result.getFilteredData().isEmpty() && !data.isEmpty()) {
    logger.error("警告：所有字段都被过滤了！原始数据: {}", data);
    logger.error("移除的字段: {}", result.getRemovedFields());
}
```

### 问题2：必填字段缺失警告

**症状**：日志中出现"缺少必填字段"警告

**原因**：数据库中的数据本身就缺少必填字段

**解决**：
```java
if (result.hasWarnings()) {
    for (String warning : result.getWarnings()) {
        if (warning.contains("缺少必填字段")) {
            // 补充默认值
            String fieldName = extractFieldName(warning);
            data.put(fieldName, getDefaultValue(fieldName));
        }
    }
}
```

### 问题3：性能问题

**症状**：大表（如items：22,000条）过滤很慢

**解决方案1**：使用并行流
```java
List<FilterResult> results = dataList.parallelStream()
    .map(data -> filter.filterForExport(tableName, data))
    .collect(Collectors.toList());
```

**解决方案2**：在数据库查询时就过滤字段
```java
// 获取允许的字段列表
FileValidationRule rule = XmlFileValidationRules.getRule(tableName).orElse(null);
Set<String> allowedFields = getAllFieldsExcept(rule.getBlacklistFields());

// 构建SELECT语句
String sql = String.format("SELECT %s FROM %s",
    String.join(", ", allowedFields), tableName);
```

### 问题4：规则不生效

**症状**：黑名单字段仍然出现在导出的XML中

**检查清单**：
1. ✅ 确认表名正确（大小写敏感）
2. ✅ 确认规则已注册到 `XmlFileValidationRules`
3. ✅ 确认调用了 `filterForExport()`
4. ✅ 确认使用了过滤后的数据 `getFilteredData()`

**调试代码**：
```java
// 打印调试信息
System.out.println("表名: " + tableName);
System.out.println("是否有规则: " + filter.hasRules(tableName));

FilterResult result = filter.filterForExport(tableName, data);
System.out.println("移除的字段: " + result.getRemovedFields());
System.out.println("原始数据键: " + data.keySet());
System.out.println("过滤后数据键: " + result.getFilteredData().keySet());
```

---

## 附录

### A. 已支持的表列表

| 表名 | 黑名单字段数 | 必填字段数 | 约束数 |
|------|------------|----------|--------|
| items | 14 | 3 | 2 |
| skills | 4 | 3 | 3 |
| quest_random_rewards | 1 | 1 | 0 |
| npcs | 1 | 3 | 1 |
| item_weapons | 14 | 2 | 0 |
| item_armors | 14 | 2 | 0 |
| skill_learns | 3 | 0 | 0 |
| skill_charge | 3 | 0 | 0 |
| ... | ... | ... | ... |

**查看完整列表**：
```java
XmlFileValidationRules.getAllTableNames().forEach(System.out::println);
```

### B. 黑名单字段速查

| 字段名 | 影响表数 | 来源 | 说明 |
|--------|---------|------|------|
| `__order_index` | 18 | dbxmlTool | 内部排序字段 |
| `status_fx_slot_lv` | 9 | SkillDB | 状态效果槽位等级 |
| `toggle_id` | 9 | SkillDB | 切换技能ID |
| `drop_prob_6~9` | 7 | ItemDB | 扩展掉落概率 |
| `erect` | 7 | ItemDB | 直立属性 |
| `is_familiar_skill` | 9 | SkillDB | 宠物技能标记 |

### C. 相关文档

- **分析报告**: `docs/server_compliance/SERVER_COMPLIANCE_ANALYSIS.md`
- **错误统计**: `docs/server_compliance/error_statistics.csv`
- **源代码**:
  - `FieldConstraint.java`
  - `FileValidationRule.java`
  - `XmlFileValidationRules.java`
  - `ServerComplianceFilter.java`

---

**最后更新**: 2025-12-29
**作者**: Claude Code
**版本**: 1.0
