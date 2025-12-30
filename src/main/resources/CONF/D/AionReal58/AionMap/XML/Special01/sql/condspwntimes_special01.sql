DROP TABLE IF EXISTS xmldb_suiyue.condspwntimes_special01;
CREATE TABLE xmldb_suiyue.condspwntimes_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(27) COMMENT 'name',
    `condition` VARCHAR(25) COMMENT 'condition',
    `world` VARCHAR(13) COMMENT 'world',
    `type` VARCHAR(7) COMMENT 'type',
    `typevalue` VARCHAR(3) COMMENT 'typevalue',
    `start` VARCHAR(4) COMMENT 'start',
    `duration` VARCHAR(4) COMMENT 'duration'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'condspwntimes_special01';

