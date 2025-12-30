DROP TABLE IF EXISTS xmldb_suiyue.housing_custom_part;
CREATE TABLE xmldb_suiyue.housing_custom_part (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(42) COMMENT 'name',
    `desc` VARCHAR(56) COMMENT 'desc',
    `type` VARCHAR(11) COMMENT 'type',
    `tag` VARCHAR(24) COMMENT 'tag'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'housing_custom_part';

