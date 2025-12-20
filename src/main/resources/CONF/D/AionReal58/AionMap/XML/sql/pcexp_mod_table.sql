DROP TABLE IF EXISTS xmldb_suiyue.pcexp_mod_table;
CREATE TABLE xmldb_suiyue.pcexp_mod_table (
    `level_5` VARCHAR(255) PRIMARY KEY COMMENT 'level_5',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `level_10` VARCHAR(3) COMMENT 'level_10',
    `level_15` VARCHAR(3) COMMENT 'level_15',
    `level_20` VARCHAR(3) COMMENT 'level_20',
    `level_25` VARCHAR(3) COMMENT 'level_25',
    `level_30` VARCHAR(3) COMMENT 'level_30',
    `level_35` VARCHAR(3) COMMENT 'level_35',
    `level_40` VARCHAR(3) COMMENT 'level_40',
    `level_45` VARCHAR(3) COMMENT 'level_45',
    `level_50` VARCHAR(3) COMMENT 'level_50',
    `level_55` VARCHAR(3) COMMENT 'level_55',
    `level_60` VARCHAR(3) COMMENT 'level_60',
    `level_65` VARCHAR(3) COMMENT 'level_65',
    `level_70` VARCHAR(3) COMMENT 'level_70',
    `level_75` VARCHAR(3) COMMENT 'level_75',
    `level_80` VARCHAR(3) COMMENT 'level_80',
    `level_85` VARCHAR(3) COMMENT 'level_85',
    `level_90` VARCHAR(3) COMMENT 'level_90',
    `level_95` VARCHAR(3) COMMENT 'level_95',
    `level_100` VARCHAR(3) COMMENT 'level_100',
    `level_105` VARCHAR(3) COMMENT 'level_105',
    `level_110` VARCHAR(3) COMMENT 'level_110',
    `_attr_level_diff_mod` VARCHAR(128) COMMENT 'level_diff_mod'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'pcexp_mod_table';

