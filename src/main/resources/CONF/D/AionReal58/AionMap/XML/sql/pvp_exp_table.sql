DROP TABLE IF EXISTS xmldb_suiyue.pvp_exp_table;
CREATE TABLE xmldb_suiyue.pvp_exp_table (
    `exp` VARCHAR(255) PRIMARY KEY COMMENT 'exp',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `delay_time` VARCHAR(4) COMMENT 'delay_time',
    `get_max_from_all_user` VARCHAR(6) COMMENT 'get_max_from_all_user',
    `get_max_reduce_amount` VARCHAR(6) COMMENT 'get_max_reduce_amount',
    `get_max_reduce_interval` VARCHAR(2) COMMENT 'get_max_reduce_interval',
    `_attr_level` VARCHAR(128) COMMENT 'level'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'pvp_exp_table';

