DROP TABLE IF EXISTS xmldb_suiyue.pc_level_func_restrict_special01;
CREATE TABLE xmldb_suiyue.pc_level_func_restrict_special01 (
    `sellgold_limit` VARCHAR(255) PRIMARY KEY COMMENT 'sellgold_limit',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `trial_sellgold_limit` VARCHAR(8) COMMENT 'trial_sellgold_limit',
    `trial_tradegold_limit` VARCHAR(7) COMMENT 'trial_tradegold_limit',
    `trial_decompose_limit` VARCHAR(1) COMMENT 'trial_decompose_limit',
    `trial_gather_limit` VARCHAR(2) COMMENT 'trial_gather_limit',
    `trial_extract_gather_limit` VARCHAR(2) COMMENT 'trial_extract_gather_limit',
    `_attr_level` VARCHAR(128) COMMENT 'level'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'pc_level_func_restrict_special01';

