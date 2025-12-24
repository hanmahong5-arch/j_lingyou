DROP TABLE IF EXISTS xmldb_suiyue.abysspoint_world_mod;
CREATE TABLE xmldb_suiyue.abysspoint_world_mod (
    `world` VARCHAR(255) PRIMARY KEY COMMENT 'world',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `drop_point_mod_light` VARCHAR(3) COMMENT 'drop_point_mod_light',
    `deathpenalty_point_mod_light` VARCHAR(3) COMMENT 'deathpenalty_point_mod_light',
    `drop_point_mod_dark` VARCHAR(3) COMMENT 'drop_point_mod_dark',
    `deathpenalty_point_mod_dark` VARCHAR(3) COMMENT 'deathpenalty_point_mod_dark',
    `_attr_ID` VARCHAR(128) COMMENT 'ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abysspoint_world_mod';

