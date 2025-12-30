DROP TABLE IF EXISTS xmldb_suiyue.bm_config;
CREATE TABLE xmldb_suiyue.bm_config (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(35) COMMENT 'name',
    `value` VARCHAR(8) COMMENT 'value'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'bm_config';

