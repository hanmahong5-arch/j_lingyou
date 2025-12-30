DROP TABLE IF EXISTS xmldb_suiyue.instance_scaling_special01;
CREATE TABLE xmldb_suiyue.instance_scaling_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(12) COMMENT 'name',
    `instant_dungeon_name` VARCHAR(12) COMMENT 'instant_dungeon_name',
    `up_limit_level` VARCHAR(2) COMMENT 'up_limit_level',
    `down_limit_level` VARCHAR(2) COMMENT 'down_limit_level',
    `exp_scaling_ratio` VARCHAR(3) COMMENT 'exp_scaling_ratio',
    `ap_scaling_ratio` VARCHAR(1) COMMENT 'ap_scaling_ratio',
    `gp_scaling_ratio` VARCHAR(1) COMMENT 'gp_scaling_ratio',
    `stat_scaling_ratio` VARCHAR(2) COMMENT 'stat_scaling_ratio',
    `skill_scaling` VARCHAR(1) COMMENT 'skill_scaling'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'instance_scaling_special01';

