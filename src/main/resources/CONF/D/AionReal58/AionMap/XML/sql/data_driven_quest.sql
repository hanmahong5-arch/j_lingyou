DROP TABLE IF EXISTS xmldb_suiyue.data_driven_quest;
CREATE TABLE xmldb_suiyue.data_driven_quest (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(6) COMMENT 'name',
    `dev_name` VARCHAR(58) COMMENT 'dev_name',
    `category_acquire_` VARCHAR(12) COMMENT 'category_acquire_',
    `value0_acquire_` VARCHAR(41) COMMENT 'value0_acquire_',
    `reward_npc_name` VARCHAR(41) COMMENT 'reward_npc_name',
    `progress_info` VARCHAR(64) COMMENT 'progress_info',
    `value1_acquire_` VARCHAR(88) COMMENT 'value1_acquire_',
    `con_quest` VARCHAR(5) COMMENT 'con_quest',
    `value4_acquire_` VARCHAR(12) COMMENT 'value4_acquire_',
    `value3_acquire_` VARCHAR(24) COMMENT 'value3_acquire_',
    `value10_acquire_` VARCHAR(8) COMMENT 'value10_acquire_',
    `con_quest_list` VARCHAR(17) COMMENT 'con_quest_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'data_driven_quest';

DROP TABLE IF EXISTS xmldb_suiyue.data_driven_quest__progress_info__data;
CREATE TABLE xmldb_suiyue.data_driven_quest__progress_info__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `category_progress_` VARCHAR(11) COMMENT 'category_progress_',
    `value0_progress_` TEXT COMMENT 'value0_progress_',
    `value1_progress_` VARCHAR(39) COMMENT 'value1_progress_',
    `value2_progress_` VARCHAR(88) COMMENT 'value2_progress_',
    `value3_progress_` VARCHAR(30) COMMENT 'value3_progress_',
    `value5_progress_` VARCHAR(171) COMMENT 'value5_progress_',
    `value4_progress_` VARCHAR(35) COMMENT 'value4_progress_',
    `value7_progress_` VARCHAR(28) COMMENT 'value7_progress_',
    `value9_progress_` VARCHAR(46) COMMENT 'value9_progress_',
    `value8_progress_` VARCHAR(30) COMMENT 'value8_progress_',
    `value6_progress_` VARCHAR(1) COMMENT 'value6_progress_',
    `value10_progress_` VARCHAR(11) COMMENT 'value10_progress_'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'data_driven_quest__progress_info__data';

