DROP TABLE IF EXISTS xmldb_suiyue.boost_time_table_no_level_step;
CREATE TABLE xmldb_suiyue.boost_time_table_no_level_step (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(4) COMMENT 'name',
    `weekday` VARCHAR(3) COMMENT 'weekday',
    `time` VARCHAR(2) COMMENT 'time',
    `craft_bonus` VARCHAR(3) COMMENT 'craft_bonus',
    `abyss_point_bonus` VARCHAR(3) COMMENT 'abyss_point_bonus',
    `cash_bonus` VARCHAR(3) COMMENT 'cash_bonus',
    `drop_bonus` VARCHAR(3) COMMENT 'drop_bonus'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'boost_time_table_no_level_step';

