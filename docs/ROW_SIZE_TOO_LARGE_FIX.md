# MySQL "Row size too large" 问题修复

## 问题描述

**错误信息**:
```
java.sql.SQLSyntaxErrorException: Row size too large (> 8126). Changing some columns to TEXT or BLOB may help. In current row format, BLOB prefix of 0 bytes is stored inline.
```

**触发条件**:
- 表的字段数量超过100个
- 使用COMPRESSED行格式 + MEDIUMTEXT字段类型
- skill_base等超大字段表

## 根本原因

### MySQL InnoDB行大小限制

MySQL InnoDB存储引擎的行大小限制：
- **COMPRESSED行格式**: 单行最大约8KB（8126字节）
- **DYNAMIC行格式**: 单行最大约8KB，但TEXT/BLOB可off-page存储

### 原有问题代码

**位置**: `src/main/java/red/jiuzhou/xmltosql/XMLToMySQLGenerator.java`

```java
// 旧代码（有问题）
String rowFormat = totalFieldCount > 100 ? "COMPRESSED" : "DYNAMIC";
int fieldTypeLevel = totalFieldCount > 100 ? 2 : (totalFieldCount > 50 ? 1 : 0);
// fieldTypeLevel=2 表示使用MEDIUMTEXT
```

**问题分析**:
1. 超过100字段的表使用COMPRESSED行格式
2. 所有字段被设置为MEDIUMTEXT
3. 虽然MEDIUMTEXT的prefix是0字节，但大量字段累积仍会超过8KB限制
4. COMPRESSED格式压缩效果有限，仍会触发行大小限制

### TEXT vs MEDIUMTEXT vs BLOB

| 类型 | 最大长度 | Prefix存储 | 适用场景 |
|------|---------|-----------|---------|
| VARCHAR(N) | 0-65,535字节 | 全部inline | 短字符串 |
| TEXT | 0-65,535字节 | 768字节prefix | 中等文本，超过部分off-page |
| MEDIUMTEXT | 0-16,777,215字节 | 0字节prefix | 大文本（不推荐用于多字段表）|
| LONGTEXT | 0-4,294,967,295字节 | 0字节prefix | 超大文本 |

**关键点**:
- TEXT类型在DYNAMIC行格式下，超过768字节的部分会存储在off-page页面
- MEDIUMTEXT虽然prefix=0，但字段元数据仍占用空间
- 大量MEDIUMTEXT字段 + COMPRESSED格式 → 行大小超限

## 解决方案

### 修复策略

**核心思路**:
1. **统一使用DYNAMIC行格式** - 支持TEXT off-page存储
2. **移除MEDIUMTEXT** - 改用TEXT（已足够且更节省空间）
3. **简化字段类型级别** - 只使用TEXT和VARCHAR

### 修改内容

#### 1. 修改行格式和字段类型策略（第193-201行）

```java
// 修复后的代码
// 根据字段数量选择行格式和字段类型策略
// - 超过150个字段: 使用DYNAMIC格式 + TEXT
// - 超过50个字段: 使用DYNAMIC格式 + TEXT
// - 其他: 使用DYNAMIC格式 + VARCHAR
// 注意: COMPRESSED + MEDIUMTEXT 会导致 "Row size too large" 错误
// MySQL InnoDB限制: COMPRESSED行格式单行最大约8KB，大量TEXT列会超限
// 解决方案: 统一使用DYNAMIC行格式，TEXT列会自动off-page存储
String rowFormat = "DYNAMIC";
int fieldTypeLevel = totalFieldCount > 50 ? 1 : 0;
```

**变化**:
- ❌ 移除COMPRESSED行格式
- ✅ 统一使用DYNAMIC行格式
- ❌ 移除fieldTypeLevel=2（MEDIUMTEXT）
- ✅ 只使用fieldTypeLevel=0（VARCHAR）和1（TEXT）

#### 2. 修改getColumnType方法（第261-289行）

```java
// 修复后的代码
private static String getColumnType(String fieldName, int fieldTypeLevel, GenerationContext context) {
    // ...

    // 策略: 根据字段级别和长度选择类型
    // - fieldTypeLevel=1 (超过50字段): 使用TEXT (off-page存储，不占用行空间)
    // - fieldTypeLevel=0: 使用VARCHAR，超过255时使用TEXT
    // 注意: 不再使用MEDIUMTEXT，TEXT已足够且更节省行空间
    if (fieldTypeLevel == 1 || len > 255) {
        return "TEXT";
    }

    return "VARCHAR(" + len + ")";
}
```

**变化**:
- ❌ 移除`if (fieldTypeLevel >= 2) return "MEDIUMTEXT";`
- ✅ 所有超过255字节或字段多的表统一使用TEXT

#### 3. 修改getTextTypeByLevel方法（第291-300行）

```java
// 修复后的代码
private static String getTextTypeByLevel(int level, int defaultVarcharLen) {
    // 只使用TEXT和VARCHAR，不再使用MEDIUMTEXT避免行大小超限
    if (level >= 1) {
        return "TEXT";
    }
    return "VARCHAR(" + defaultVarcharLen + ")";
}
```

**变化**:
- ❌ 移除`if (level >= 2) return "MEDIUMTEXT";`
- ✅ level >= 1 统一返回TEXT

## 修复效果

### 修复前后对比

**skill_base表（约300个字段）**:

| 项目 | 修复前 | 修复后 |
|------|--------|--------|
| 行格式 | COMPRESSED | DYNAMIC |
| 字段类型 | MEDIUMTEXT | TEXT |
| 单行大小 | > 8126字节 ❌ | < 8126字节 ✅ |
| 建表结果 | Row size too large | 创建成功 |

### 性能影响

**优点**:
- ✅ 解决了行大小超限问题
- ✅ TEXT off-page存储，大数据不影响行内空间
- ✅ DYNAMIC行格式性能更好（InnoDB默认推荐）

**缺点**:
- ⚠️ 对于短字符串，TEXT比VARCHAR略慢（可忽略）
- ⚠️ TEXT字段无法创建完整索引（只能前缀索引）

**总体评估**: 性能影响极小，可忽略不计。对于超大字段表，这是唯一可行方案。

## 使用建议

### 1. 重新生成DDL

对于已经遇到错误的表，需要重新生成DDL：

```java
// 方法1: 在机制浏览器中右键机制卡片
右键点击 → ⚡ 一键DDL+建表

// 方法2: 批量生成
右键点击 → 📝 批量生成DDL
```

### 2. 删除旧的错误SQL文件

```bash
# 删除旧的DDL文件（可选）
rm src/main/resources/CONF/D/AionReal58/AionMap/XML/sql/skill_base.sql

# 重新生成
# 在UI中右键生成即可
```

### 3. 验证新DDL

生成后的DDL应该包含：

```sql
CREATE TABLE xmldb_suiyue.skill_base (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` TEXT COMMENT 'name',  -- 注意: 使用TEXT而不是MEDIUMTEXT
    `desc` TEXT COMMENT 'desc',
    ...
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'skill_base';
-- 注意: ROW_FORMAT=DYNAMIC
```

### 4. 对于特殊大字段

如果某个字段确实需要存储超大文本（>64KB），可以手动修改DDL：

```sql
-- 只对真正需要的字段使用MEDIUMTEXT或LONGTEXT
`very_large_field` MEDIUMTEXT COMMENT '超大字段',
```

## 技术要点

### DYNAMIC vs COMPRESSED

| 特性 | DYNAMIC | COMPRESSED |
|------|---------|-----------|
| 默认推荐 | ✅ InnoDB默认 | ❌ 特殊场景 |
| 压缩 | 不压缩 | 页面级压缩 |
| 性能 | 更快 | 较慢（压缩开销）|
| 行大小限制 | 约8KB | 约8KB |
| TEXT/BLOB存储 | 自动off-page | 仍受限制 |
| 适用场景 | 通用 | 磁盘空间紧张 |

**结论**: 对于大量TEXT字段的表，DYNAMIC是更好的选择。

### Off-page存储机制

**DYNAMIC行格式下TEXT字段存储**:
```
行内: 768字节prefix + 指针
行外: 剩余数据存储在独立页面
```

**好处**:
- 行内只存储前768字节，不影响行大小限制
- 超过768字节的部分自动存储到overflow页面
- 读取大字段时才访问overflow页面，不影响其他字段查询性能

## 相关资源

- [MySQL InnoDB Row Format](https://dev.mysql.com/doc/refman/8.0/en/innodb-row-format.html)
- [MySQL TEXT vs VARCHAR](https://dev.mysql.com/doc/refman/8.0/en/blob.html)
- [InnoDB Limits](https://dev.mysql.com/doc/refman/8.0/en/innodb-limits.html)

## 修复日期

2025-12-20

## 贡献者

- Claude (AI Assistant) - 问题分析和代码修复
