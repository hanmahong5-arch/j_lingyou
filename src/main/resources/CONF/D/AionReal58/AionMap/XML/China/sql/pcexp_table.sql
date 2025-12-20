DROP TABLE IF EXISTS xmldb_suiyue.pcexp_table;
CREATE TABLE xmldb_suiyue.pcexp_table (
    `exp_light` VARCHAR(255) PRIMARY KEY COMMENT 'exp_light',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `exp_dark` VARCHAR(14) COMMENT 'exp_dark',
    `level_cp` VARCHAR(3) COMMENT 'level_cp',
    `level_up_cp` VARCHAR(1) COMMENT 'level_up_cp',
    `feverpoint_boost` VARCHAR(3) COMMENT 'feverpoint_boost',
    `feverpoint_max` VARCHAR(9) COMMENT 'feverpoint_max',
    `feverpoint_monster_limit_count` VARCHAR(2) COMMENT 'feverpoint_monster_limit_count',
    `feverpoint_login` VARCHAR(1) COMMENT 'feverpoint_login',
    `feverpoint_get_cp` VARCHAR(3) COMMENT 'feverpoint_get_cp',
    `_attr_level` VARCHAR(128) COMMENT 'level'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'pcexp_table';

