DROP TABLE IF EXISTS xmldb_suiyue.monster_scaling_drop_special01;
CREATE TABLE xmldb_suiyue.monster_scaling_drop_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(37) COMMENT 'name',
    `scailing_set_list` VARCHAR(64) COMMENT 'scailing_set_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'monster_scaling_drop_special01';

DROP TABLE IF EXISTS xmldb_suiyue.monster_scaling_drop_special01__scailing_set_list__data;
CREATE TABLE xmldb_suiyue.monster_scaling_drop_special01__scailing_set_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `set_list_name_` VARCHAR(35) COMMENT 'set_list_name_',
    `rate_` VARCHAR(4) COMMENT 'rate_',
    `apply_level_` VARCHAR(5) COMMENT 'apply_level_',
    `apply_race_` VARCHAR(8) COMMENT 'apply_race_',
    `apply_class_` VARCHAR(15) COMMENT 'apply_class_'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'monster_scaling_drop_special01__scailing_set_list__data';

