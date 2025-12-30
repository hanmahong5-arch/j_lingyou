DROP TABLE IF EXISTS xmldb_suiyue.challenge_task;
CREATE TABLE xmldb_suiyue.challenge_task (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(4) COMMENT 'name',
    `desc` VARCHAR(28) COMMENT 'desc',
    `type` VARCHAR(6) COMMENT 'type',
    `race` VARCHAR(8) COMMENT 'race',
    `level_min` VARCHAR(1) COMMENT 'level_min',
    `level_max` VARCHAR(1) COMMENT 'level_max',
    `challenge_task_repeat` VARCHAR(1) COMMENT 'challenge_task_repeat',
    `quest_list` VARCHAR(64) COMMENT 'quest_list',
    `contributor_reward` VARCHAR(64) COMMENT 'contributor_reward',
    `challenge_task_union_reward_type` VARCHAR(5) COMMENT 'challenge_task_union_reward_type',
    `challenge_task_prev` VARCHAR(3) COMMENT 'challenge_task_prev',
    `challenge_task_union_reward_value` VARCHAR(4) COMMENT 'challenge_task_union_reward_value',
    `town_id` VARCHAR(4) COMMENT 'town_id',
    `town_residence` VARCHAR(1) COMMENT 'town_residence'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'challenge_task';

DROP TABLE IF EXISTS xmldb_suiyue.challenge_task__quest_list__data;
CREATE TABLE xmldb_suiyue.challenge_task__quest_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `quest_id` VARCHAR(5) COMMENT 'quest_id',
    `quest_repeat` VARCHAR(2) COMMENT 'quest_repeat',
    `score` VARCHAR(4) COMMENT 'score'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'challenge_task__quest_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.challenge_task__contributor_reward__data;
CREATE TABLE xmldb_suiyue.challenge_task__contributor_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `contributor_rank` VARCHAR(1) COMMENT 'contributor_rank',
    `contributor_num` VARCHAR(3) COMMENT 'contributor_num',
    `reward_item` VARCHAR(33) COMMENT 'reward_item',
    `reward_item_count` VARCHAR(3) COMMENT 'reward_item_count',
    `contributor_name` VARCHAR(44) COMMENT 'contributor_name',
    `reward_item_timer` VARCHAR(6) COMMENT 'reward_item_timer'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'challenge_task__contributor_reward__data';

