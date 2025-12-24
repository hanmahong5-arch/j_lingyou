DROP TABLE IF EXISTS xmldb_suiyue.instance_cooltime2;
CREATE TABLE xmldb_suiyue.instance_cooltime2 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(27) COMMENT 'name',
    `type` VARCHAR(8) COMMENT 'type',
    `value` VARCHAR(5) COMMENT 'value',
    `maxcount` VARCHAR(2) COMMENT 'maxcount',
    `price` VARCHAR(9) COMMENT 'price',
    `priceincrease` VARCHAR(3) COMMENT 'priceincrease',
    `pricemaxcount` VARCHAR(2) COMMENT 'pricemaxcount',
    `typevalue` VARCHAR(19) COMMENT 'typevalue',
    `component` VARCHAR(21) COMMENT 'component',
    `component_count` VARCHAR(2) COMMENT 'component_count',
    `extra_count_buildup_level` VARCHAR(1) COMMENT 'extra_count_buildup_level',
    `extra_count_buildup_dailyreset` VARCHAR(5) COMMENT 'extra_count_buildup_dailyreset',
    `extra_count_buildup` VARCHAR(1) COMMENT 'extra_count_buildup'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'instance_cooltime2';

