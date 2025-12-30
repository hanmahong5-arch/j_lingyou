DROP TABLE IF EXISTS xmldb_suiyue.aitimeconditions_special01;
CREATE TABLE xmldb_suiyue.aitimeconditions_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(12) COMMENT 'name',
    `type` VARCHAR(6) COMMENT 'type',
    `typevalue` VARCHAR(4) COMMENT 'typevalue',
    `start` VARCHAR(4) COMMENT 'start',
    `duration` VARCHAR(2) COMMENT 'duration'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'aitimeconditions_special01';

