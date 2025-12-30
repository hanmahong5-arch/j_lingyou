DROP TABLE IF EXISTS xmldb_suiyue.Quest_SimpleUseItem;
CREATE TABLE xmldb_suiyue.Quest_SimpleUseItem (
    `dev_name` VARCHAR(255) PRIMARY KEY COMMENT 'dev_name',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `use_item_name` VARCHAR(21) COMMENT 'use_item_name',
    `reward_npc_name` VARCHAR(31) COMMENT 'reward_npc_name',
    `talk_npc1` VARCHAR(19) COMMENT 'talk_npc1',
    `talk_npc2` VARCHAR(20) COMMENT 'talk_npc2',
    `talk_npc3` VARCHAR(26) COMMENT 'talk_npc3',
    `give_item3` VARCHAR(18) COMMENT 'give_item3',
    `remove_item3` VARCHAR(18) COMMENT 'remove_item3',
    `remove_item2` VARCHAR(22) COMMENT 'remove_item2',
    `give_item1` VARCHAR(22) COMMENT 'give_item1',
    `remove_item1` VARCHAR(23) COMMENT 'remove_item1',
    `give_item2` VARCHAR(22) COMMENT 'give_item2',
    `con_quest` VARCHAR(5) COMMENT 'con_quest',
    `item_check` VARCHAR(1) COMMENT 'item_check',
    `_attr_id` VARCHAR(128) COMMENT 'id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'Quest_SimpleUseItem';

