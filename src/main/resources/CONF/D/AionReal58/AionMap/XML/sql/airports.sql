DROP TABLE IF EXISTS xmldb_suiyue.airports;
CREATE TABLE xmldb_suiyue.airports (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(34) COMMENT 'name',
    `desc` VARCHAR(40) COMMENT 'desc',
    `world` VARCHAR(19) COMMENT 'world',
    `race` VARCHAR(5) COMMENT 'race',
    `location_alias` VARCHAR(34) COMMENT 'location_alias',
    `ui_map_pos_x` VARCHAR(5) COMMENT 'ui_map_pos_x',
    `ui_map_pos_y` VARCHAR(5) COMMENT 'ui_map_pos_y',
    `abyss_id` VARCHAR(4) COMMENT 'abyss_id',
    `npcname` VARCHAR(25) COMMENT 'npcname'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'airports';

