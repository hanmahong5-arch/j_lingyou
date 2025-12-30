DROP TABLE IF EXISTS xmldb_suiyue.instant_dungeon_define_special01;
CREATE TABLE xmldb_suiyue.instant_dungeon_define_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(37) COMMENT 'name',
    `desc` VARCHAR(36) COMMENT 'desc',
    `value` VARCHAR(38) COMMENT 'value'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'instant_dungeon_define_special01';

