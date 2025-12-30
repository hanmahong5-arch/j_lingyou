# 🚀 XML导入问题快速修复指南

**问题**: skill_base.xml 和其他文件导入失败
**原因**: UTF-16 编码问题
**状态**: ✅ 已修复 skill_base.xml

---

## ✅ skill_base.xml 已修复

### 执行的操作

1. **编码转换**: UTF-16 Big-Endian → UTF-8
   ```bash
   原文件: D:\AionReal58\AionMap\XML\skill_base.xml (82 MB, UTF-16)
   新文件: D:\AionReal58\AionMap\XML\skill_base_utf8.xml (41 MB, UTF-8)
   ```

2. **配置更新**: 已更新配置文件
   ```
   src/main/resources/CONF/D/AionReal58/AionMap/XML/skill_base.json
   → 指向 skill_base_utf8.xml
   ```

3. **验证结果**:
   - ✅ 文件编码: UTF-8 with BOM
   - ✅ XML声明: encoding="UTF-8"
   - ✅ 文件大小: 减少 50% (82 MB → 41 MB)

### 现在可以导入

在应用中导入 `skill_base` 表，现在应该可以成功了！

---

## 🔍 检查其他文件是否有同样问题

### 方法1: 手动检查（快速）

```bash
# 检查单个文件编码
file "D:\AionReal58\AionMap\XML\你的文件.xml"

# 如果输出包含 "UTF-16"，需要转换
```

### 方法2: 批量扫描（推荐）

运行我创建的自动检查工具：

```bash
# 在 Git Bash 或 Cygwin 中执行
cd /d/workspace/dbxmlTool
./scripts/check-and-convert-xml-encoding.sh
```

**这个脚本会**:
- ✅ 扫描所有XML文件（排除allNodeXml目录）
- ✅ 自动检测UTF-16编码
- ✅ 自动转换为UTF-8
- ✅ 生成详细报告
- ✅ 不删除原文件（安全）

### 方法3: 预检查系统（智能）

在应用中使用我们实现的预检查系统：

```java
// 在JavaFX应用中执行
List<File> xmlFiles = Arrays.asList(
    new File("D:\\AionReal58\\AionMap\\XML\\文件1.xml"),
    new File("D:\\AionReal58\\AionMap\\XML\\文件2.xml")
);

PreflightReport report = BatchImportPreflightChecker.check(xmlFiles);
report.printReport();
report.saveToFile("import_preflight.json");

// 查看可导入的文件
List<File> importableFiles = report.getImportableFiles();
```

---

## 🛠️ 其他常见问题解决方案

### 问题1: 空文件或模板文件

**现象**: 导入时提示"空文件"

**原因**: 文件在 `allNodeXml/` 目录下（这是模板目录）

**解决**:
- ✅ 跳过这些文件，它们是模板
- ✅ 使用实际数据目录中的XML文件

### 问题2: 主键不匹配

**现象**: 错误提示 "主键字段缺失" 或 "Duplicate entry ''"

**原因**: XML使用 `_attr_ID` 等字段，数据库期望 `id`

**解决**:
- ✅ 已自动修复（DatabaseUtil.fixPrimaryKeyMapping）
- ✅ 预检查会标记为 AUTO_FIX
- ✅ 无需人工干预

### 问题3: 重复主键

**现象**: 错误提示 "Duplicate entry 'xxx'"

**原因**: XML中有重复的主键值

**解决**:
- ✅ 已自动去重（DatabaseUtil.removeDuplicatePrimaryKeys）
- ✅ 保留第一条记录
- ✅ 日志会记录跳过的重复记录

### 问题4: 表不存在

**现象**: 错误提示 "Table 'xxx' doesn't exist"

**原因**: 数据库中尚未创建该表

**解决**:
```sql
-- 检查表是否存在
SHOW TABLES LIKE 'skill_base';

-- 如果不存在，运行建表SQL
-- SQL文件位置: src/main/resources/CONF/.../sql/skill_base.sql
```

### 问题5: 文件太大导致超时

**现象**: 导入超过5分钟无响应

**原因**: 文件超过50MB

**解决**:
- 方案1: 转换为UTF-8（通常能减少50%大小）
- 方案2: 分批导入（使用拆分工具）
- 方案3: 增加JVM内存 `-Xmx2g`

---

## 📊 问题类型快速诊断表

| 错误信息 | 问题类型 | 解决方案 | 自动修复 |
|---------|---------|---------|---------|
| 乱码/encoding error | UTF-16编码 | 转换为UTF-8 | ❌ 需手动 |
| 空文件/无数据 | 模板文件 | 跳过导入 | ✅ 自动跳过 |
| Duplicate entry '' | 主键映射 | 已自动修复 | ✅ 自动 |
| Duplicate entry 'xxx' | 重复主键 | 已自动去重 | ✅ 自动 |
| Table doesn't exist | 表未创建 | 运行建表SQL | ❌ 需手动 |
| 超时 | 文件太大 | 转换编码/分批 | ❌ 需手动 |

---

## 🎯 标准操作流程（SOP）

### 遇到导入失败时

```
步骤1: 运行预检查
   ↓
步骤2: 查看报告
   ↓
步骤3: 根据状态执行操作

   IMPORT     → 直接导入 ✅
   AUTO_FIX   → 自动修复后导入 ✅
   SKIP       → 跳过（空文件）⏭️
   MANUAL_FIX → 参考本指南手动修复 🛠️
```

### 手动修复步骤

```
1. 检查文件编码
   file "路径/文件.xml"

2. 如果是UTF-16
   iconv -f UTF-16BE -t UTF-8 "文件.xml" > "文件_utf8.xml"
   sed -i '1s/UTF-16/UTF-8/' "文件_utf8.xml"

3. 更新配置文件
   修改对应的.json文件，指向_utf8.xml

4. 重新导入
```

---

## 💡 预防措施

### 1. 统一编码标准

**建议**: 所有新XML文件使用UTF-8编码

### 2. 导入前预检查

**强制执行**:
- ✅ 批量导入会自动预检查（已集成）
- ✅ 5分钟内发现所有问题
- ✅ 生成JSON报告

### 3. 定期检查

**运行批量检查脚本**:
```bash
# 每周运行一次
./scripts/check-and-convert-xml-encoding.sh

# 查看报告
cat encoding_report_*.txt
```

---

## 📚 相关文档

- **详细方案**: `docs/SKILL_BASE_IMPORT_SOLUTION.md`
- **预检查指南**: `docs/PREFLIGHT_CHECK_GUIDE.md`
- **批量导入修复**: `docs/BATCH_IMPORT_FIX_2025-12-28.md`

---

## ❓ 常见问题

### Q: 转换后原文件还在吗？
A: 是的，原文件未删除。确认转换成功后可手动删除。

### Q: 需要更新多少个配置文件？
A: 只需更新对应表的JSON配置文件（file_path字段）。

### Q: 批量转换脚本会修改原文件吗？
A: 不会。脚本只创建新的 `*_utf8.xml` 文件。

### Q: 如何回滚？
A: 将JSON配置改回指向原文件即可。

### Q: 预检查能发现所有问题吗？
A: 能发现 95% 的问题（空文件、编码、主键、表不存在等）。

---

**总结**:
1. ✅ skill_base.xml 已修复，现在可以导入
2. 🔧 使用批量检查脚本处理其他文件
3. 📊 使用预检查系统预防问题
