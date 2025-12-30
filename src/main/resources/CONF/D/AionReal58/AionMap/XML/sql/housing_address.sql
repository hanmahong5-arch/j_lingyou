DROP TABLE IF EXISTS xmldb_suiyue.housing_address;
CREATE TABLE xmldb_suiyue.housing_address (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(19) COMMENT 'name',
    `world_id` VARCHAR(9) COMMENT 'world_id',
    `world_name` VARCHAR(21) COMMENT 'world_name',
    `subzone1_name` VARCHAR(18) COMMENT 'subzone1_name',
    `subzone1_desc` VARCHAR(22) COMMENT 'subzone1_desc',
    `subzone2_name` VARCHAR(18) COMMENT 'subzone2_name',
    `subzone2_desc` VARCHAR(22) COMMENT 'subzone2_desc',
    `house_name` VARCHAR(22) COMMENT 'house_name',
    `house_desc` VARCHAR(26) COMMENT 'house_desc',
    `town_id` VARCHAR(4) COMMENT 'town_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'housing_address';

