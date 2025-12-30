DROP TABLE IF EXISTS xmldb_suiyue.Quest_SimpleItemPlay;
CREATE TABLE xmldb_suiyue.Quest_SimpleItemPlay (
    `dev_name` VARCHAR(255) PRIMARY KEY COMMENT 'dev_name',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `acquired_npc_name` VARCHAR(30) COMMENT 'acquired_npc_name',
    `talk_npc1` VARCHAR(30) COMMENT 'talk_npc1',
    `talk_npc2` VARCHAR(16) COMMENT 'talk_npc2',
    `use_item_name` VARCHAR(35) COMMENT 'use_item_name',
    `reward_npc_name` VARCHAR(30) COMMENT 'reward_npc_name',
    `give_item` VARCHAR(26) COMMENT 'give_item',
    `give_item2` VARCHAR(23) COMMENT 'give_item2',
    `remove_item2` VARCHAR(19) COMMENT 'remove_item2',
    `con_quest` VARCHAR(5) COMMENT 'con_quest',
    `give_item1` VARCHAR(35) COMMENT 'give_item1',
    `cutsceneid1` VARCHAR(3) COMMENT 'cutsceneid1',
    `remove_item1` VARCHAR(37) COMMENT 'remove_item1',
    `_attr_id` VARCHAR(128) COMMENT 'id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'Quest_SimpleItemPlay';

