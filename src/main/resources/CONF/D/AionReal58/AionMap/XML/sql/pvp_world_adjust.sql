DROP TABLE IF EXISTS xmldb_suiyue.pvp_world_adjust;
CREATE TABLE xmldb_suiyue.pvp_world_adjust (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(4) COMMENT 'name',
    `owner_of_field` VARCHAR(5) COMMENT 'owner_of_field'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'pvp_world_adjust';

