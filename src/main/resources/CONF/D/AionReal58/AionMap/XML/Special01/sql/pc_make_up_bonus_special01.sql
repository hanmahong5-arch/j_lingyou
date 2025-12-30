DROP TABLE IF EXISTS xmldb_suiyue.pc_make_up_bonus_special01;
CREATE TABLE xmldb_suiyue.pc_make_up_bonus_special01 (
    `standard_playtime` VARCHAR(255) PRIMARY KEY COMMENT 'standard_playtime',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `standard_exp_per_hour` VARCHAR(8) COMMENT 'standard_exp_per_hour',
    `check_start_time` VARCHAR(4) COMMENT 'check_start_time',
    `check_interval` VARCHAR(2) COMMENT 'check_interval',
    `bonus_point_per_min` VARCHAR(1) COMMENT 'bonus_point_per_min',
    `bonus_interval` VARCHAR(1) COMMENT 'bonus_interval',
    `bonus_use_ratio` VARCHAR(1) COMMENT 'bonus_use_ratio',
    `display_max_point` VARCHAR(7) COMMENT 'display_max_point',
    `drop_ratio` VARCHAR(1) COMMENT 'drop_ratio',
    `_attr_level` VARCHAR(128) COMMENT 'level'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'pc_make_up_bonus_special01';

