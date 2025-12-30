DROP TABLE IF EXISTS xmldb_suiyue.infinity_indun_reward_special01;
CREATE TABLE xmldb_suiyue.infinity_indun_reward_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(10) COMMENT 'name',
    `floor` VARCHAR(2) COMMENT 'floor',
    `exp` VARCHAR(7) COMMENT 'exp',
    `gold` VARCHAR(6) COMMENT 'gold',
    `reward_ap` VARCHAR(5) COMMENT 'reward_ap',
    `reward_gp` VARCHAR(1) COMMENT 'reward_gp',
    `reward_item1_name` VARCHAR(38) COMMENT 'reward_item1_name',
    `reward_item1_count` VARCHAR(3) COMMENT 'reward_item1_count',
    `reward_item2_name` VARCHAR(16) COMMENT 'reward_item2_name',
    `reward_item2_count` VARCHAR(3) COMMENT 'reward_item2_count',
    `reward_item3_name` VARCHAR(1) COMMENT 'reward_item3_name',
    `reward_item3_count` VARCHAR(1) COMMENT 'reward_item3_count',
    `reward_item4_name` VARCHAR(1) COMMENT 'reward_item4_name',
    `reward_item4_count` VARCHAR(1) COMMENT 'reward_item4_count',
    `reward_item5_name` VARCHAR(1) COMMENT 'reward_item5_name',
    `reward_item5_count` VARCHAR(1) COMMENT 'reward_item5_count',
    `reward_item6_name` VARCHAR(1) COMMENT 'reward_item6_name',
    `reward_item6_count` VARCHAR(1) COMMENT 'reward_item6_count'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'infinity_indun_reward_special01';

