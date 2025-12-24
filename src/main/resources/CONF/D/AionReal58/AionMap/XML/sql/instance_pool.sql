DROP TABLE IF EXISTS xmldb_suiyue.instance_pool;
CREATE TABLE xmldb_suiyue.instance_pool (
    `base` VARCHAR(255) PRIMARY KEY COMMENT 'base',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `min` VARCHAR(2) COMMENT 'min',
    `max` VARCHAR(2) COMMENT 'max',
    `delay` VARCHAR(4) COMMENT 'delay'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'instance_pool';

