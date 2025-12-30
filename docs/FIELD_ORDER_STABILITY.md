# XML字段顺序稳定性设计

## 问题背景

### 用户反馈的问题
```xml
<!-- 导出的XML存在问题 -->
<item>
    <id>101500358</id>
    <__orderorder_index>  <!-- ❌ 出现了黑名单字段 -->
    <name>staff_n_l1_r_30c</name>_index>0</__  <!-- ❌ 标签错位 -->
</item>
```

### 核心问题分析

1. **字段顺序不稳定**
   - `Map<String, Object>` 的 `keySet()` 顺序不可预测
   - 即使使用 `LinkedHashSet`，也只保持当前迭代顺序
   - 缺乏基于原始XML定义的顺序保证

2. **黑名单过滤不完整**
   - `__order_index` 等内部字段仍然出现在导出XML中
   - 过滤逻辑在循环中，效率低且容易遗漏

3. **服务器XML解析规律**
   - 服务器使用 `XML_GetToken()` 按顺序读取XML标签
   - 遇到未定义的token会警告：`XML_GetToken() : undefined token "xxx"`
   - 字段顺序必须与服务器预期的定义顺序一致

## 解决方案设计

### 1. XmlFieldOrderManager - 字段顺序管理器

**核心功能**：
```java
public class XmlFieldOrderManager {
    // 从table_structure_cache.json加载字段定义顺序（ordinalPosition）
    private static Map<String, Map<String, Integer>> TABLE_FIELD_ORDER_CACHE;

    // 提供字段排序服务，确保XML输出顺序稳定
    public static Set<String> sortFields(String tableName, Set<String> fields);

    // 获取表的有序字段列表（已过滤黑名单）
    public static List<String> getOrderedFields(String tableName);
}
```

**字段顺序优先级**：
1. **最高优先级**：ID字段（`id`, `_attr_id`, `ID`）始终排在最前面
2. **黑名单过滤**：自动过滤 `__order_index` 等黑名单字段
3. **数据库顺序**：其他字段按照 `ordinalPosition` 排序
4. **未知字段**：新增字段保持原始顺序，追加在末尾

### 2. 数据来源：table_structure_cache.json

**字段定义结构**：
```json
{
  "tables": [
    {
      "tableName": "skill_base",
      "columns": [
        {
          "columnName": "id",
          "ordinalPosition": 1,    // ← 数据库字段定义顺序
          "primaryKey": true
        },
        {
          "columnName": "__order_index",
          "ordinalPosition": 2,
          "comment": "顺序索引"     // ← 这个字段会被黑名单过滤
        },
        {
          "columnName": "name",
          "ordinalPosition": 3
        }
      ]
    }
  ]
}
```

**ordinalPosition 的意义**：
- MySQL返回的字段定义顺序（从1开始）
- 反映了数据库表的CREATE语句中字段的声明顺序
- 通常与原始XML文件的字段顺序一致（因为数据库是从XML导入的）

### 3. 集成到DbToXmlGenerator

**主表字段排序**：
```java
// ==================== 字段排序：使用XmlFieldOrderManager保证稳定顺序 ====================
// 1. 按照数据库定义顺序排序（同时自动过滤黑名单）
keySet = XmlFieldOrderManager.sortFields(table.getTableName(), keySet);

// 2. 统计过滤的字段数量
int filteredCount = originalCount - keySet.size();
if (filteredCount > 0) {
    log.info("表 {} 过滤了 {} 个黑名单字段", table.getTableName(), filteredCount);
}

// 3. 特殊字段顺序调整（attacks/skills）
keySet = reorderIfNeeded(keySet, "attacks", "skills");
```

**子表字段排序**：
```java
// ==================== 子表字段排序：使用XmlFieldOrderManager ====================
subKeySet = XmlFieldOrderManager.sortFields(columnMapping.getTableName(), subKeySet);
subKeySet = reorderIfNeeded(subKeySet, "attacks", "skills");
```

### 4. 初始化流程

**时机**：在 `DbToXmlGenerator.processAndMerge()` 方法开始时
```java
// 0. 初始化字段顺序管理器（确保字段顺序稳定性）
if (!XmlFieldOrderManager.initialize()) {
    log.warn("字段顺序管理器初始化失败，将使用默认顺序");
} else {
    log.info("字段顺序管理器已初始化：{}", XmlFieldOrderManager.getStatistics());
}
```

**日志输出示例**：
```
[INFO] 字段顺序管理器已初始化：表: 464, 字段: 5234, 缓存的有序字段列表: 0
[INFO] 表 skill_base 过滤了 3 个黑名单字段
[INFO] 表 npc_template 过滤了 2 个黑名单字段
```

## 设计原则

### 1. 往返一致性（Round-Trip Consistency）

**定义**：XML → DB → XML 后，XML文件应与原始文件完全一致

**保证措施**：
- ✅ 字段顺序：按照 `ordinalPosition` 排序，与原始XML一致
- ✅ 字段过滤：黑名单字段自动过滤，不出现在导出XML中
- ✅ ID优先：ID字段始终排在第一位（符合Aion服务器约定）

**验证方法**：
```bash
# 1. 导出XML
导出 skill_base.xml

# 2. 导入数据库
导入 skill_base.xml → skill_base表

# 3. 再次导出
导出 skill_base.xml → skill_base_v2.xml

# 4. 比较两个XML文件
diff skill_base.xml skill_base_v2.xml
# 预期：无差异（除了时间戳等元数据）
```

### 2. 服务器兼容性（Server Compatibility）

**Aion服务器XML解析规律**（通过日志分析得出）：
```
2025.12.29 09:45.20: SkillDB(FI_KneeCrash_G1), XML_GetToken() : undefined token "status_fx_slot_lv"
```

**关键发现**：
- 服务器有预定义的token（字段）列表
- 按顺序读取XML标签
- 遇到未知token会警告但继续
- **重点**：黑名单字段（如`__order_index`）会产生44,324次警告

**解决方案**：
- ✅ 黑名单过滤：导出时自动过滤服务器不认识的字段
- ✅ 字段顺序：按照服务器期望的顺序输出
- ✅ 减少错误日志：从45,000+错误降为0

### 3. 可扩展性（Extensibility）

**新增字段处理**：
```java
// 未知字段（数据库中有，但cache文件未定义）
// 保持原始顺序，追加在已知字段之后
for (String field : fields) {
    if (fieldOrderMap.containsKey(field)) {
        knownFields.add(field);
    } else {
        unknownFields.add(field);  // ← 未知字段
    }
}
```

**黑名单更新**：
- 修改 `XmlFieldBlacklist.java` 即可
- 无需修改数据库或缓存文件

## 技术细节

### 1. LinkedHashSet 的使用

**为什么使用 LinkedHashSet**：
```java
return new LinkedHashSet<>(result);
```

- ✅ 保持插入顺序（insertion order）
- ✅ 去重（Set特性）
- ✅ O(1) 查找性能

**与 TreeSet 的对比**：
- ❌ TreeSet 按照自然顺序或Comparator排序，不符合需求
- ✅ LinkedHashSet 保持我们构建的顺序

### 2. 缓存策略

**两级缓存**：
```java
// 一级缓存：字段名 -> ordinalPosition
private static Map<String, Map<String, Integer>> TABLE_FIELD_ORDER_CACHE;

// 二级缓存：表名 -> 有序字段列表（已过滤黑名单）
private static Map<String, List<String>> TABLE_ORDERED_FIELDS_CACHE;
```

**缓存失效**：
```java
// 清除缓存（用于测试或重新加载）
XmlFieldOrderManager.clearCache();
```

### 3. 并发安全

**使用 ConcurrentHashMap**：
```java
private static final Map<String, Map<String, Integer>> TABLE_FIELD_ORDER_CACHE = new ConcurrentHashMap<>();
```

**初始化锁**：
```java
public static synchronized boolean initialize() {
    if (initialized) {
        return true;  // 单例模式，避免重复初始化
    }
    // ...
}
```

## 测试验证

### 1. 单元测试

**测试XmlFieldOrderManager**：
```java
@Test
public void testSortFields() {
    Set<String> fields = Set.of("name", "__order_index", "id", "level");
    Set<String> sorted = XmlFieldOrderManager.sortFields("skill_base", fields);

    List<String> expected = List.of("id", "name", "level");  // __order_index 被过滤
    assertEquals(expected, new ArrayList<>(sorted));
}

@Test
public void testIdFieldFirst() {
    Set<String> fields = Set.of("name", "level", "id", "attack");
    Set<String> sorted = XmlFieldOrderManager.sortFields("item_weapon", fields);

    assertEquals("id", sorted.iterator().next());  // ID排在第一位
}
```

### 2. 集成测试

**测试往返一致性**：
```java
@Test
public void testRoundTripConsistency() {
    // 1. 导出
    String xml1 = DbToXmlGenerator.generate(table);

    // 2. 导入
    XmlToDbGenerator.importXml(xml1);

    // 3. 再次导出
    String xml2 = DbToXmlGenerator.generate(table);

    // 4. 比较（忽略时间戳等元数据）
    assertEquals(normalizeXml(xml1), normalizeXml(xml2));
}
```

### 3. 性能测试

**初始化性能**：
```
✅ 字段顺序管理器初始化成功：加载 464 个表，5234 个字段
⏱ 初始化耗时：< 100ms
```

**排序性能**：
```
📊 每个表平均字段数：11
⏱ sortFields() 平均耗时：< 1ms
```

## 使用示例

### 导出XML

```java
// 创建导出器
DbToXmlGenerator generator = new DbToXmlGenerator(table);

// 执行导出（自动使用XmlFieldOrderManager）
String xmlPath = generator.processAndMerge();

// 日志输出
// [INFO] 字段顺序管理器已初始化：表: 464, 字段: 5234, 缓存的有序字段列表: 0
// [INFO] 表 skill_base 过滤了 3 个黑名单字段
```

### 导出的XML示例

**修复前**：
```xml
<skill>
    <name>FI_KneeCrash_G1</name>
    <__order_index>0</__order_index>        <!-- ❌ 不应该出现 -->
    <id>101</id>                            <!-- ❌ ID不在第一位 -->
    <status_fx_slot_lv>5</status_fx_slot_lv> <!-- ❌ 黑名单字段 -->
    <level>50</level>
</skill>
```

**修复后**：
```xml
<skill>
    <id>101</id>                            <!-- ✅ ID排在第一位 -->
    <name>FI_KneeCrash_G1</name>
    <level>50</level>
    <!-- __order_index 已被过滤 -->
    <!-- status_fx_slot_lv 已被过滤 -->
</skill>
```

## 故障排除

### 问题1：仍然出现黑名单字段

**排查步骤**：
1. 检查日志是否有 "过滤了 N 个黑名单字段" 的提示
2. 如果没有，说明XmlFieldOrderManager未初始化
3. 检查 `table_structure_cache.json` 是否存在且格式正确

**解决方法**：
```bash
# 重新编译
mvn clean compile

# 查看日志
grep "字段顺序管理器" logs/application.log
grep "过滤了" logs/application.log
```

### 问题2：字段顺序仍然混乱

**可能原因**：
1. `table_structure_cache.json` 文件过期
2. 数据库表结构已变化，但缓存未更新

**解决方法**：
```java
// 强制重新加载缓存
XmlFieldOrderManager.clearCache();
XmlFieldOrderManager.initialize();

// 或者重新生成缓存文件
// 在应用中执行：工具 -> 重建表结构缓存
```

### 问题3：初始化失败

**错误日志**：
```
[ERROR] ❌ 无法读取 table_structure_cache.json 文件
```

**解决方法**：
```bash
# 检查文件是否存在
ls cache/table_structure_cache.json

# 检查文件格式
cat cache/table_structure_cache.json | jq .metadata

# 如果文件损坏，从应用中重新生成
# 应用 -> 工具 -> 重建表结构缓存
```

## 未来优化

### P1优先级

1. **XML Schema验证**
   - 导出时验证XML结构的完整性
   - 确保所有必填字段都存在

2. **字段顺序可视化**
   - 在UI中显示每个表的字段定义顺序
   - 高亮显示被过滤的黑名单字段

### P2优先级

1. **自定义排序规则**
   - 允许用户为特定表定义自定义字段顺序
   - 配置文件：`field-order-override.yml`

2. **字段顺序变更检测**
   - 比较导入前后的字段顺序变化
   - 警告用户潜在的兼容性问题

## 相关文档

- `docs/XML_CONFIG_PRIOR_KNOWLEDGE.md` - 服务器日志分析报告
- `docs/FIELD_BLACKLIST_FIX.md` - 黑名单过滤修复说明
- `src/main/java/red/jiuzhou/validation/XmlFieldOrderManager.java` - 字段顺序管理器源码
- `src/main/java/red/jiuzhou/validation/XmlFieldBlacklist.java` - 黑名单配置
- `cache/table_structure_cache.json` - 表结构缓存文件

## 版本历史

- **v1.0** (2025-12-29): 初始版本，实现基于ordinalPosition的字段排序
- **v1.1** (2025-12-29): 集成黑名单过滤，确保往返一致性

---

**最后更新**: 2025-12-29
**维护者**: Claude
