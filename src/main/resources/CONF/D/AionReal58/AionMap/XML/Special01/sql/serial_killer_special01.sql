DROP TABLE IF EXISTS xmldb_suiyue.serial_killer_special01;
CREATE TABLE xmldb_suiyue.serial_killer_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(15) COMMENT 'name',
    `desc` VARCHAR(25) COMMENT 'desc',
    `race` VARCHAR(8) COMMENT 'race',
    `rank` VARCHAR(1) COMMENT 'rank',
    `base_point` VARCHAR(5) COMMENT 'base_point',
    `entering_add_point` VARCHAR(1) COMMENT 'entering_add_point',
    `reduce_point` VARCHAR(5) COMMENT 'reduce_point',
    `reduce_point_logoff` VARCHAR(5) COMMENT 'reduce_point_logoff',
    `show_title` VARCHAR(1) COMMENT 'show_title',
    `restrict_direct_portal` VARCHAR(1) COMMENT 'restrict_direct_portal',
    `restrict_dynamic_bindstone` VARCHAR(1) COMMENT 'restrict_dynamic_bindstone',
    `level_diff_add_point` VARCHAR(64) COMMENT 'level_diff_add_point',
    `penalty_attr1` VARCHAR(17) COMMENT 'penalty_attr1',
    `info_message` VARCHAR(24) COMMENT 'info_message',
    `death_message` VARCHAR(31) COMMENT 'death_message'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'serial_killer_special01';

DROP TABLE IF EXISTS xmldb_suiyue.serial_killer_special01__level_diff_add_point__data;
CREATE TABLE xmldb_suiyue.serial_killer_special01__level_diff_add_point__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `add_point` VARCHAR(4) COMMENT 'add_point'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'serial_killer_special01__level_diff_add_point__data';

