DROP TABLE IF EXISTS xmldb_suiyue.boost_time_table_level_step_test_inter_special01;
CREATE TABLE xmldb_suiyue.boost_time_table_level_step_test_inter_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(4) COMMENT 'name',
    `weekday` VARCHAR(3) COMMENT 'weekday',
    `time` VARCHAR(2) COMMENT 'time',
    `level_step` VARCHAR(2) COMMENT 'level_step',
    `exp_bonus` VARCHAR(3) COMMENT 'exp_bonus',
    `recovery_fee_bonus` VARCHAR(3) COMMENT 'recovery_fee_bonus'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'boost_time_table_level_step_test_inter_special01';

