DROP TABLE IF EXISTS xmldb_suiyue.zonemap_hotspot_special01;
CREATE TABLE xmldb_suiyue.zonemap_hotspot_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(16) COMMENT 'name',
    `name_dev` VARCHAR(13) COMMENT 'name_dev',
    `desc` VARCHAR(20) COMMENT 'desc',
    `world` VARCHAR(15) COMMENT 'world',
    `x` VARCHAR(11) COMMENT 'x',
    `y` VARCHAR(11) COMMENT 'y',
    `z` VARCHAR(10) COMMENT 'z',
    `dir` VARCHAR(3) COMMENT 'dir',
    `race` VARCHAR(5) COMMENT 'race',
    `required_base_gold` VARCHAR(5) COMMENT 'required_base_gold',
    `required_distance_gold` VARCHAR(9) COMMENT 'required_distance_gold'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'zonemap_hotspot_special01';

