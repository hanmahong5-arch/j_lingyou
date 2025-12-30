-- ========================================
-- 快速修复任务系统表结构问题
-- 执行方式：mysql -u root -p xmldb_suiyue < scripts/fix-quest-tables.sql
-- ========================================

USE xmldb_suiyue;
SET NAMES utf8mb4;

-- 检查数据库连接
SELECT '开始修复任务系统表结构...' AS status;

-- ========================================
-- 1. 修复 data_driven_quest 字段长度问题
-- ========================================
SELECT '修复 data_driven_quest.dev_name 字段长度...' AS status;

ALTER TABLE data_driven_quest
  MODIFY COLUMN `dev_name` TEXT COMMENT 'dev_name - 使用TEXT避免长度限制';

-- ========================================
-- 2. 修复 Quest_Simple* 系列表主键
-- ========================================
SELECT '修复 Quest_Simple* 系列表主键...' AS status;

-- Quest_SimpleCollectItem
ALTER TABLE Quest_SimpleCollectItem
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (`_attr_id`);

-- Quest_SimpleHunt
ALTER TABLE Quest_SimpleHunt
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (`_attr_id`);

-- Quest_SimpleItemPlay
ALTER TABLE Quest_SimpleItemPlay
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (`_attr_id`);

-- Quest_SimpleSerialHunt
ALTER TABLE Quest_SimpleSerialHunt
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (`_attr_id`);

-- Quest_SimpleTalk
ALTER TABLE Quest_SimpleTalk
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (`_attr_id`);

-- Quest_SimpleUseItem
ALTER TABLE Quest_SimpleUseItem
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (`_attr_id`);

-- ========================================
-- 3. 修复 combine_task 主键
-- ========================================
SELECT '修复 combine_task 主键...' AS status;

ALTER TABLE combine_task
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (`_attr_id`);

-- ========================================
-- 4. 验证修复结果
-- ========================================
SELECT '验证表结构...' AS status;

-- 检查 data_driven_quest.dev_name 字段类型
SELECT
    TABLE_NAME,
    COLUMN_NAME,
    DATA_TYPE,
    CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'xmldb_suiyue'
  AND TABLE_NAME = 'data_driven_quest'
  AND COLUMN_NAME = 'dev_name';

-- 检查所有 Quest_Simple* 表的主键
SELECT
    TABLE_NAME,
    COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'xmldb_suiyue'
  AND TABLE_NAME LIKE 'Quest_Simple%'
  AND CONSTRAINT_NAME = 'PRIMARY'
ORDER BY TABLE_NAME;

-- 检查 combine_task 主键
SELECT
    TABLE_NAME,
    COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'xmldb_suiyue'
  AND TABLE_NAME = 'combine_task'
  AND CONSTRAINT_NAME = 'PRIMARY';

SELECT '✅ 表结构修复完成！' AS status;
SELECT '请在应用中重新执行批量导入操作' AS next_step;
