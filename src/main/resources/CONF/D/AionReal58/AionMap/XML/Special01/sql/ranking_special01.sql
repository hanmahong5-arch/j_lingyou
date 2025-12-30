DROP TABLE IF EXISTS xmldb_suiyue.ranking_special01;
CREATE TABLE xmldb_suiyue.ranking_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(20) COMMENT 'name',
    `rank_id` VARCHAR(1) COMMENT 'rank_id',
    `rank_type` VARCHAR(6) COMMENT 'rank_type',
    `point_calc_type` VARCHAR(3) COMMENT 'point_calc_type',
    `rank_ui_type` VARCHAR(6) COMMENT 'rank_ui_type',
    `rank_name` VARCHAR(28) COMMENT 'rank_name',
    `period_list` VARCHAR(64) COMMENT 'period_list',
    `reward_type` VARCHAR(5) COMMENT 'reward_type',
    `rank_list` VARCHAR(64) COMMENT 'rank_list',
    `rate_id` VARCHAR(3) COMMENT 'rate_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'ranking_special01';

DROP TABLE IF EXISTS xmldb_suiyue.ranking_special01__period_list__data;
CREATE TABLE xmldb_suiyue.ranking_special01__period_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `rank_period_no` VARCHAR(2) COMMENT 'rank_period_no',
    `rank_period_start` VARCHAR(19) COMMENT 'rank_period_start',
    `rank_period_end` VARCHAR(19) COMMENT 'rank_period_end'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'ranking_special01__period_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.ranking_special01__rank_list__data;
CREATE TABLE xmldb_suiyue.ranking_special01__rank_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `rank` VARCHAR(2) COMMENT 'rank',
    `value` VARCHAR(4) COMMENT 'value',
    `gold` VARCHAR(9) COMMENT 'gold',
    `ap` VARCHAR(7) COMMENT 'ap',
    `gp` VARCHAR(5) COMMENT 'gp',
    `reward_item1_name` VARCHAR(26) COMMENT 'reward_item1_name',
    `reward_item1_count` VARCHAR(1) COMMENT 'reward_item1_count',
    `reward_item2_name` VARCHAR(25) COMMENT 'reward_item2_name',
    `reward_item2_count` VARCHAR(1) COMMENT 'reward_item2_count'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'ranking_special01__rank_list__data';

