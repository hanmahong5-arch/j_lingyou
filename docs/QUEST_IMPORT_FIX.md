# 任务系统批量导入问题修复指南

## 问题总结

### 1. 表不存在（BadSqlGrammarException）
**受影响的表**：
- challenge_task
- jumping_addquest
- jumping_endquest
- Quest_SimpleItemPlay
- Quest_SimpleSerialHunt
- Quest_SimpleTalk
- Quest_SimpleUseItem
- quest（175个字段的超大表）

**原因**：SQL 文件已生成，但未在数据库中执行。

### 2. 字段长度超限（DataIntegrityViolationException）
**受影响的表**：
- data_driven_quest.dev_name - 定义为 VARCHAR(58)，实际数据超长

**原因**：XML 数据中的 `dev_name` 字段值超过 58 个字符。

### 3. 主键字段映射错误（DuplicateKeyException）
**受影响的表**：
- combine_task
- 所有 Quest_Simple* 系列表

**原因**：
- SQL 定义主键为 `dev_name`
- 但 INSERT 语句使用的是 `_attr_id` 字段
- XML 中某些记录的 `dev_name` 为空，导致主键冲突

## 解决方案

### 步骤 1：执行 SQL 创建表结构

```bash
# 方式1：使用MySQL命令行
mysql -u root -p xmldb_suiyue < scripts/import-quest-tables.sql

# 方式2：分别执行各个SQL文件
mysql -u root -p xmldb_suiyue < src/main/resources/CONF/D/AionReal58/AionMap/XML/sql/challenge_task.sql
mysql -u root -p xmldb_suiyue < src/main/resources/CONF/D/AionReal58/AionMap/XML/sql/jumping_addquest.sql
# ... 依次执行
```

### 步骤 2：修复 data_driven_quest 字段长度

```sql
-- 修改 dev_name 字段为 TEXT 类型
ALTER TABLE xmldb_suiyue.data_driven_quest
MODIFY COLUMN `dev_name` TEXT COMMENT 'dev_name - 使用TEXT避免长度限制';
```

### 步骤 3：修复主键字段映射问题

有两种方案：

#### 方案 A：修改表结构（推荐）

将主键改为 `_attr_id`（因为导入逻辑实际使用这个字段作为ID）：

```sql
-- Quest_SimpleItemPlay 示例
ALTER TABLE xmldb_suiyue.Quest_SimpleItemPlay DROP PRIMARY KEY;
ALTER TABLE xmldb_suiyue.Quest_SimpleItemPlay
  ADD PRIMARY KEY (`_attr_id`);

-- 对所有 Quest_Simple* 表重复此操作
```

#### 方案 B：修改 XML 数据

确保 XML 中所有记录都有非空的 `dev_name` 属性。

### 步骤 4：处理 combine_task 空主键

```sql
-- 检查空主键记录
SELECT * FROM combine_task WHERE dev_name = '' OR dev_name IS NULL;

-- 方案1：使用_attr_id作为主键
ALTER TABLE xmldb_suiyue.combine_task DROP PRIMARY KEY;
ALTER TABLE xmldb_suiyue.combine_task
  ADD PRIMARY KEY (`_attr_id`);

-- 方案2：清理XML中的空dev_name记录（手动编辑XML）
```

### 步骤 5：重新导入数据

执行修复后，重新运行批量导入操作。

## 根本原因分析

### XmlToDbGenerator 字段映射逻辑

问题出在 `XmlToDbGenerator.java` 的字段映射：

1. XML 属性 `id` → 数据库字段 `_attr_id`
2. 但 SQL 建表时主键是 `dev_name`
3. 导致 INSERT 语句中缺少主键字段值

### 建议的代码修复

修改 `XmlToDbGenerator` 或 `XMLToMySQLGenerator`，确保：
- 如果表主键是 `dev_name`，INSERT 语句应包含该字段
- 或者统一使用 `_attr_id` 作为主键（更推荐）

## 快速修复脚本

```sql
-- 一键修复所有表结构
USE xmldb_suiyue;

-- 1. 修复 data_driven_quest
ALTER TABLE data_driven_quest
  MODIFY COLUMN `dev_name` TEXT;

-- 2. 修复 Quest_Simple* 系列表主键
ALTER TABLE Quest_SimpleItemPlay DROP PRIMARY KEY, ADD PRIMARY KEY (`_attr_id`);
ALTER TABLE Quest_SimpleSerialHunt DROP PRIMARY KEY, ADD PRIMARY KEY (`_attr_id`);
ALTER TABLE Quest_SimpleTalk DROP PRIMARY KEY, ADD PRIMARY KEY (`_attr_id`);
ALTER TABLE Quest_SimpleUseItem DROP PRIMARY KEY, ADD PRIMARY KEY (`_attr_id`);

-- 3. 修复 combine_task 主键
ALTER TABLE combine_task DROP PRIMARY KEY, ADD PRIMARY KEY (`_attr_id`);

SELECT '✅ 表结构修复完成，请重新导入数据' AS status;
```

## 预防措施

1. **代码层面**：
   - 在 `XmlToDbGenerator` 中添加主键字段检查
   - 生成 INSERT 语句时自动包含主键字段
   - 添加字段长度动态检测（TEXT vs VARCHAR）

2. **数据层面**：
   - XML 导出时确保所有记录有唯一ID
   - 使用 `_attr_id` 作为统一主键字段

3. **流程层面**：
   - 导入前自动执行 DDL
   - 添加数据预校验功能
