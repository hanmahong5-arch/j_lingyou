DROP TABLE IF EXISTS xmldb_suiyue.instance_cooltime2_special01;
CREATE TABLE xmldb_suiyue.instance_cooltime2_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(27) COMMENT 'name',
    `type` VARCHAR(8) COMMENT 'type',
    `value` VARCHAR(5) COMMENT 'value',
    `maxcount` VARCHAR(2) COMMENT 'maxcount',
    `typevalue` VARCHAR(19) COMMENT 'typevalue',
    `price` VARCHAR(1) COMMENT 'price',
    `component` VARCHAR(4) COMMENT 'component',
    `component_count` VARCHAR(1) COMMENT 'component_count'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'instance_cooltime2_special01';

