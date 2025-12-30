# 字段黑名单和字段顺序修复说明

## 问题描述

导出的XML文件存在以下问题：
1. ❌ `__order_index` 等内部字段未被过滤，仍然出现在XML中
2. ❌ XML字段顺序混乱，ID字段不在最前面
3. ❌ XML结构可能出现错位（如 `<__orderorder_index>` 这样的错误标签）

## 修复内容

### 1. 增强黑名单过滤机制

**文件**: `src/main/java/red/jiuzhou/dbxml/DbToXmlGenerator.java`

**主表字段过滤** (lines 137-149):
```java
// ==================== 字段黑名单预过滤 ====================
Set<String> filteredKeySet = new LinkedHashSet<>();
int filteredCount = 0;
for (String key : keySet) {
    if (XmlFieldBlacklist.shouldFilter(table.getTableName(), key)) {
        filteredCount++;
        continue;
    }
    filteredKeySet.add(key);
}
if (filteredCount > 0) {
    log.info("表 {} 过滤了 {} 个黑名单字段", table.getTableName(), filteredCount);
}
```

**改进点**：
- ✅ 使用**预过滤**机制，在循环开始前就移除黑名单字段
- ✅ 改用 `log.info` 而非 `log.debug`，确保用户能看到过滤信息
- ✅ 统计并显示过滤的字段数量

**子表字段过滤** (lines 255-262):
```java
// ==================== 子表字段黑名单预过滤 ====================
Set<String> filteredSubKeySet = new LinkedHashSet<>();
for (String subKey : subKeySet) {
    if (XmlFieldBlacklist.shouldFilter(columnMapping.getTableName(), subKey)) {
        continue;
    }
    filteredSubKeySet.add(subKey);
}
```

### 2. 新增字段排序功能

**新增方法**: `ensureIdFirst()` (lines 414-438)
```java
/**
 * 确保ID字段始终排在最前面
 */
public static Set<String> ensureIdFirst(Set<String> set) {
    List<String> list = new ArrayList<>();

    // 先添加id字段（多种可能的ID字段名）
    for (String idField : Arrays.asList("id", "_attr_id", "ID")) {
        if (set.contains(idField)) {
            list.add(idField);
        }
    }

    // 再添加其他字段
    for (String field : set) {
        if (!list.contains(field)) {
            list.add(field);
        }
    }

    return new LinkedHashSet<>(list);
}
```

**调用位置** (lines 151-153):
```java
// ==================== 字段排序：ID优先 ====================
keySet = ensureIdFirst(filteredKeySet);
keySet = reorderIfNeeded(keySet, "attacks", "skills");
```

**效果**：
- ✅ ID字段（`id`, `_attr_id`, `ID`）始终排在最前面
- ✅ 生成的XML符合Aion服务器的解析顺序要求
- ✅ 便于人工查看和调试

### 3. 黑名单配置

**文件**: `src/main/java/red/jiuzhou/validation/XmlFieldBlacklist.java`

**全局黑名单**（所有表通用）：
- `__order_index` - XML工具内部排序索引（44,324次错误）
- `__row_index` - 行索引字段
- `__original_id` - 原始ID字段

**技能系统黑名单**：
- `status_fx_slot_lv` - 状态效果槽位等级（405次错误）
- `toggle_id` - 切换技能ID（378次错误）
- `is_familiar_skill` - 宠物技能标记（288次错误）

**NPC系统黑名单**：
- `erect` - 直立姿态（60次错误）
- `monsterbook_race` - 怪物图鉴种族（30次错误）

**掉落系统黑名单**：
- `drop_prob_6~9`, `drop_monster_6~9`, `drop_item_6~9` - 服务器仅支持1~5

**道具系统黑名单**：
- `item_skin_override` - 道具外观覆盖
- `dyeable_v2` - 新版染色系统
- `glamour_id` - 幻化ID

## 编译和测试

### 1. 重新编译项目

**Windows CMD**:
```bash
cd D:\workspace\dbxmlTool
mvn clean compile
```

**Windows PowerShell**:
```powershell
cd D:\workspace\dbxmlTool
mvn clean compile
```

**如果使用项目启动脚本**:
```bash
run.bat
```

### 2. 验证修复效果

**启动应用后，执行导出操作，观察日志**：

✅ **成功的日志示例**：
```
[INFO] 表 skill_base 过滤了 3 个黑名单字段
[INFO] 表 npc_template 过滤了 2 个黑名单字段
[INFO] 表 item_weapon 过滤了 1 个黑名单字段
```

✅ **导出的XML示例**（修复后）：
```xml
<item>
    <id>101500358</id>                    <!-- ID字段排在最前面 -->
    <name>staff_n_l1_r_30c</name>
    <level>30</level>
    <attack>100</attack>
    <!-- __order_index 已被过滤，不再出现 -->
</item>
```

❌ **修复前的XML示例**：
```xml
<item>
    <name>staff_n_l1_r_30c</name>
    <__order_index>0</__order_index>     <!-- 不应该出现 -->
    <id>101500358</id>                    <!-- ID不在最前面 -->
    <level>30</level>
</item>
```

### 3. 测试清单

- [ ] 编译成功，无错误
- [ ] 启动应用成功
- [ ] 执行导出操作
- [ ] 日志中显示 "表 XXX 过滤了 N 个黑名单字段"
- [ ] 生成的XML文件中不包含 `__order_index` 字段
- [ ] XML中ID字段排在第一位
- [ ] XML结构正常，无错位或重复标签

## 预期效果

### 导出性能提升
- **减少XML文件大小**：每条记录减少1-5个无用字段
- **减少服务器错误日志**：从45,000+错误降为0

### 兼容性改善
- ✅ 生成的XML可被旧版Aion服务器正常加载
- ✅ 无 "undefined token" 错误
- ✅ 字段顺序符合服务器解析要求

### 开发体验优化
- ✅ 导出时自动显示过滤统计信息
- ✅ ID字段始终在最前面，便于查看
- ✅ 黑名单配置独立，易于维护和扩展

## 故障排除

### 问题1：编译失败

**错误**: `The JAVA_HOME environment variable is not defined correctly`

**解决**:
```bash
# 设置 JAVA_HOME
set JAVA_HOME=D:\jdk-25.0.1.8-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

# 验证
java -version
javac -version
```

### 问题2：日志中没有过滤信息

**可能原因**:
1. 代码未重新编译
2. 使用了旧的class文件缓存

**解决**:
```bash
# 清理并重新编译
mvn clean compile

# 或者使用IDE的 "Rebuild Project"
```

### 问题3：仍然出现 `__order_index`

**排查步骤**:
1. 检查日志中是否有 "过滤了 N 个黑名单字段" 的提示
2. 如果没有，说明黑名单过滤未生效，需要重新编译
3. 检查 `XmlFieldBlacklist.shouldFilter()` 方法是否被调用

**调试命令**:
```bash
# 在日志中搜索过滤信息
grep "过滤了" logs/application.log

# 查看黑名单方法调用
grep "XmlFieldBlacklist" logs/application.log
```

## 进一步优化建议

### P1优先级

1. **跨文件引用检查**
   - 验证 `item_id`, `npc_id`, `skill_id` 等引用的完整性
   - 在导入前检测缺失的引用数据

2. **UI实时验证**
   - 在字段编辑时显示参数范围限制
   - 实时提示字段是否在黑名单中

### P2优先级

1. **依赖顺序自动排序**
   - 根据表依赖关系自动调整导入顺序
   - 避免外键约束错误

2. **XML版本兼容性识别**
   - 自动检测XML版本
   - 根据服务器版本动态调整黑名单

## 相关文档

- `docs/XML_CONFIG_PRIOR_KNOWLEDGE.md` - 服务器日志分析报告
- `src/main/java/red/jiuzhou/validation/XmlFieldBlacklist.java` - 黑名单配置
- `src/test/java/red/jiuzhou/validation/XmlFieldBlacklistTest.java` - 单元测试

## 版本历史

- **v1.0** (2025-12-29): 初始版本，实现黑名单过滤和字段排序
- **v1.1** (2025-12-29): 增强预过滤机制，添加统计日志

---

**最后更新**: 2025-12-29
**维护者**: Claude
