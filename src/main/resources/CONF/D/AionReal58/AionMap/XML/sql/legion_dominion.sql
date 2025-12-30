DROP TABLE IF EXISTS xmldb_suiyue.legion_dominion;
CREATE TABLE xmldb_suiyue.legion_dominion (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(13) COMMENT 'name',
    `instance_name` VARCHAR(26) COMMENT 'instance_name',
    `legion_dominion_area` VARCHAR(21) COMMENT 'legion_dominion_area',
    `legion_dominion_desc` VARCHAR(27) COMMENT 'legion_dominion_desc',
    `race` VARCHAR(8) COMMENT 'race',
    `rank_reward` VARCHAR(64) COMMENT 'rank_reward'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'legion_dominion';

DROP TABLE IF EXISTS xmldb_suiyue.legion_dominion__rank_reward__data;
CREATE TABLE xmldb_suiyue.legion_dominion__rank_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `rank` VARCHAR(1) COMMENT 'rank',
    `rank_reward_item1_name` VARCHAR(22) COMMENT 'rank_reward_item1_name',
    `rank_reward_item1_count` VARCHAR(1) COMMENT 'rank_reward_item1_count',
    `rank_reward_item1_timer` VARCHAR(6) COMMENT 'rank_reward_item1_timer',
    `rank_reward_item2_name` VARCHAR(23) COMMENT 'rank_reward_item2_name',
    `rank_reward_item2_count` VARCHAR(1) COMMENT 'rank_reward_item2_count',
    `rank_reward_item2_timer` VARCHAR(6) COMMENT 'rank_reward_item2_timer',
    `rank_reward_item3_name` VARCHAR(19) COMMENT 'rank_reward_item3_name',
    `rank_reward_item3_count` VARCHAR(1) COMMENT 'rank_reward_item3_count',
    `rank_reward_item3_timer` VARCHAR(6) COMMENT 'rank_reward_item3_timer'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'legion_dominion__rank_reward__data';

