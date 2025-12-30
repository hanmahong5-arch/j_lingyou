DROP TABLE IF EXISTS xmldb_suiyue.guild_rank_reward;
CREATE TABLE xmldb_suiyue.guild_rank_reward (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(1) COMMENT 'name',
    `rank_period_start` VARCHAR(19) COMMENT 'rank_period_start',
    `rank_period_end` VARCHAR(19) COMMENT 'rank_period_end',
    `rank_list` VARCHAR(64) COMMENT 'rank_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'guild_rank_reward';

DROP TABLE IF EXISTS xmldb_suiyue.guild_rank_reward__rank_list__data;
CREATE TABLE xmldb_suiyue.guild_rank_reward__rank_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `rank` VARCHAR(2) COMMENT 'rank',
    `reward_ratio` VARCHAR(4) COMMENT 'reward_ratio',
    `correction_min` VARCHAR(8) COMMENT 'correction_min',
    `correction_max` VARCHAR(9) COMMENT 'correction_max'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'guild_rank_reward__rank_list__data';

