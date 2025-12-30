-- ========================================
-- 字段引用关系表
-- 记录XML文件之间的字段级ID引用链接
-- ========================================

CREATE TABLE IF NOT EXISTS `field_references` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,

  -- 源端信息
  `source_file` VARCHAR(512) NOT NULL COMMENT '源文件完整路径',
  `source_file_name` VARCHAR(128) NOT NULL COMMENT '源文件名（不含路径）',
  `source_field` VARCHAR(128) NOT NULL COMMENT '字段名（如 item_id）',
  `source_field_path` VARCHAR(512) NOT NULL COMMENT '字段在XML中的路径（如 items/item@item_id）',
  `source_mechanism` VARCHAR(64) COMMENT '源文件所属机制分类',

  -- 目标端信息
  `target_system` VARCHAR(64) NOT NULL COMMENT '目标系统名称（如 物品系统）',
  `target_table` VARCHAR(128) NOT NULL COMMENT '目标数据库表名（如 client_items）',

  -- 统计信息
  `reference_count` INT DEFAULT 0 COMMENT '引用总数',
  `distinct_values` INT DEFAULT 0 COMMENT '不重复的ID值数量',
  `valid_references` INT DEFAULT 0 COMMENT '有效引用数（在目标表中存在）',
  `invalid_references` INT DEFAULT 0 COMMENT '无效引用数（在目标表中不存在）',

  -- 样本数据
  `sample_values` TEXT COMMENT '示例ID值（JSON数组，最多10个）',
  `sample_names` TEXT COMMENT '示例名称（JSON数组，对应sample_values）',

  -- 元数据
  `confidence` DECIMAL(4,3) DEFAULT 1.000 COMMENT '检测置信度（0.000-1.000）',
  `last_analysis_time` DATETIME COMMENT '最后分析时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  -- 唯一约束：同一文件的同一字段路径对同一目标系统只能有一条记录
  UNIQUE KEY `uk_field_ref` (`source_file`, `source_field_path`, `target_system`),

  -- 索引优化
  INDEX `idx_source_file` (`source_file_name`),
  INDEX `idx_target_system` (`target_system`),
  INDEX `idx_source_mechanism` (`source_mechanism`),
  INDEX `idx_target_table` (`target_table`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字段引用关系表';


-- ========================================
-- 引用统计视图
-- 按目标系统统计引用情况
-- ========================================

CREATE OR REPLACE VIEW `v_reference_stats_by_system` AS
SELECT
  target_system,
  target_table,
  COUNT(*) AS field_count,
  SUM(reference_count) AS total_references,
  SUM(distinct_values) AS total_distinct_values,
  SUM(valid_references) AS total_valid,
  SUM(invalid_references) AS total_invalid,
  ROUND(SUM(valid_references) * 100.0 / NULLIF(SUM(valid_references + invalid_references), 0), 2) AS valid_rate
FROM field_references
GROUP BY target_system, target_table
ORDER BY total_references DESC;


-- ========================================
-- 引用统计视图
-- 按源机制统计引用情况
-- ========================================

CREATE OR REPLACE VIEW `v_reference_stats_by_mechanism` AS
SELECT
  source_mechanism,
  COUNT(DISTINCT source_file_name) AS file_count,
  COUNT(*) AS field_count,
  SUM(reference_count) AS total_references,
  COUNT(DISTINCT target_system) AS target_system_count
FROM field_references
WHERE source_mechanism IS NOT NULL
GROUP BY source_mechanism
ORDER BY total_references DESC;
