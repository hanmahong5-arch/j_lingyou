DROP TABLE IF EXISTS xmldb_suiyue.pvp_exp_mod_table;
CREATE TABLE xmldb_suiyue.pvp_exp_mod_table (
    `level_10` VARCHAR(255) PRIMARY KEY COMMENT 'level_10',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `level_20` VARCHAR(3) COMMENT 'level_20',
    `level_30` VARCHAR(3) COMMENT 'level_30',
    `level_40` VARCHAR(3) COMMENT 'level_40',
    `level_50` VARCHAR(3) COMMENT 'level_50',
    `level_60` VARCHAR(3) COMMENT 'level_60',
    `level_70` VARCHAR(3) COMMENT 'level_70',
    `level_80` VARCHAR(3) COMMENT 'level_80',
    `level_90` VARCHAR(3) COMMENT 'level_90',
    `level_100` VARCHAR(3) COMMENT 'level_100',
    `level_110` VARCHAR(3) COMMENT 'level_110',
    `_attr_level_diff_mod` VARCHAR(128) COMMENT 'level_diff_mod'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'pvp_exp_mod_table';

