DROP TABLE IF EXISTS xmldb_suiyue.special_server_config;
CREATE TABLE xmldb_suiyue.special_server_config (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(25) COMMENT 'name',
    `value1` VARCHAR(4) COMMENT 'value1'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'special_server_config';

