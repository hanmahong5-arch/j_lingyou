DROP TABLE IF EXISTS xmldb_suiyue.abyss_op;
CREATE TABLE xmldb_suiyue.abyss_op (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(41) COMMENT 'name',
    `entity_name` VARCHAR(36) COMMENT 'entity_name',
    `entity_type` VARCHAR(16) COMMENT 'entity_type',
    `group_id` VARCHAR(5) COMMENT 'group_id',
    `point` VARCHAR(5) COMMENT 'point',
    `race` VARCHAR(9) COMMENT 'race',
    `world` VARCHAR(3) COMMENT 'world',
    `linked_npc_name` VARCHAR(31) COMMENT 'linked_npc_name',
    `x` VARCHAR(11) COMMENT 'x',
    `y` VARCHAR(11) COMMENT 'y',
    `z` VARCHAR(11) COMMENT 'z',
    `dir` VARCHAR(3) COMMENT 'dir',
    `respawn_time` VARCHAR(4) COMMENT 'respawn_time'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_op';

