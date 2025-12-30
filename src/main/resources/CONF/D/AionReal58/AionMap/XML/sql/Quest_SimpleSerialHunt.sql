DROP TABLE IF EXISTS xmldb_suiyue.Quest_SimpleSerialHunt;
CREATE TABLE xmldb_suiyue.Quest_SimpleSerialHunt (
    `dev_name` VARCHAR(255) PRIMARY KEY COMMENT 'dev_name',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `acquired_npc_name` VARCHAR(30) COMMENT 'acquired_npc_name',
    `talk_npc1` VARCHAR(9) COMMENT 'talk_npc1',
    `talk_npc2` VARCHAR(9) COMMENT 'talk_npc2',
    `talk_npc3` VARCHAR(9) COMMENT 'talk_npc3',
    `count_first` VARCHAR(1) COMMENT 'count_first',
    `monster_first` TEXT COMMENT 'monster_first',
    `count_second` VARCHAR(1) COMMENT 'count_second',
    `monster_second` VARCHAR(174) COMMENT 'monster_second',
    `count_third` VARCHAR(1) COMMENT 'count_third',
    `monster_third` VARCHAR(98) COMMENT 'monster_third',
    `count_fourth` VARCHAR(1) COMMENT 'count_fourth',
    `monster_fourth` VARCHAR(34) COMMENT 'monster_fourth',
    `count_fifth` VARCHAR(1) COMMENT 'count_fifth',
    `monster_fifth` VARCHAR(40) COMMENT 'monster_fifth',
    `reward_npc_name` VARCHAR(23) COMMENT 'reward_npc_name',
    `_attr_id` VARCHAR(128) COMMENT 'id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'Quest_SimpleSerialHunt';

