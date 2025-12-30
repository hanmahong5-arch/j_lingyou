DROP TABLE IF EXISTS xmldb_suiyue.jumping_pc_special01;
CREATE TABLE xmldb_suiyue.jumping_pc_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(19) COMMENT 'name',
    `race` VARCHAR(8) COMMENT 'race',
    `class` VARCHAR(13) COMMENT 'class',
    `level` VARCHAR(2) COMMENT 'level',
    `inven` VARCHAR(2) COMMENT 'inven',
    `item_set` VARCHAR(54) COMMENT 'item_set',
    `item_set2` VARCHAR(32) COMMENT 'item_set2',
    `item_set3` VARCHAR(23) COMMENT 'item_set3',
    `title_set` VARCHAR(58) COMMENT 'title_set',
    `addquest_set` VARCHAR(22) COMMENT 'addquest_set',
    `endquest_set` VARCHAR(166) COMMENT 'endquest_set',
    `world` VARCHAR(5) COMMENT 'world',
    `coord` VARCHAR(16) COMMENT 'coord',
    `risen_world` VARCHAR(5) COMMENT 'risen_world',
    `risen_coord` VARCHAR(16) COMMENT 'risen_coord',
    `quick_bar` VARCHAR(16) COMMENT 'quick_bar',
    `add_skill` VARCHAR(39) COMMENT 'add_skill'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'jumping_pc_special01';

