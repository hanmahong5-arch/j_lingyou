DROP TABLE IF EXISTS xmldb_suiyue.housing_building;
CREATE TABLE xmldb_suiyue.housing_building (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(16) COMMENT 'name',
    `desc` VARCHAR(38) COMMENT 'desc',
    `category` VARCHAR(8) COMMENT 'category',
    `type` VARCHAR(14) COMMENT 'type',
    `size` VARCHAR(1) COMMENT 'size',
    `tag` VARCHAR(4) COMMENT 'tag',
    `default_roof` VARCHAR(17) COMMENT 'default_roof',
    `default_outwall` VARCHAR(20) COMMENT 'default_outwall',
    `default_frame` VARCHAR(18) COMMENT 'default_frame',
    `default_door` VARCHAR(17) COMMENT 'default_door',
    `default_inwall` VARCHAR(20) COMMENT 'default_inwall',
    `default_infloor` VARCHAR(21) COMMENT 'default_infloor',
    `default_garden` VARCHAR(19) COMMENT 'default_garden',
    `default_fence` VARCHAR(18) COMMENT 'default_fence'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'housing_building';

