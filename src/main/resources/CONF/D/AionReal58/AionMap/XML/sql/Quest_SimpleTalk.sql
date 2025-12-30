DROP TABLE IF EXISTS xmldb_suiyue.Quest_SimpleTalk;
CREATE TABLE xmldb_suiyue.Quest_SimpleTalk (
    `acquired_npc_name` VARCHAR(255) PRIMARY KEY COMMENT 'acquired_npc_name',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `reward_npc_name` VARCHAR(35) COMMENT 'reward_npc_name',
    `dev_name` VARCHAR(47) COMMENT 'dev_name',
    `con_quest` VARCHAR(5) COMMENT 'con_quest',
    `item_check` VARCHAR(1) COMMENT 'item_check',
    `give_item` VARCHAR(31) COMMENT 'give_item',
    `talk_npc1` VARCHAR(98) COMMENT 'talk_npc1',
    `give_item1` VARCHAR(35) COMMENT 'give_item1',
    `remove_item1` VARCHAR(31) COMMENT 'remove_item1',
    `talk_npc2` VARCHAR(98) COMMENT 'talk_npc2',
    `give_item2` VARCHAR(23) COMMENT 'give_item2',
    `remove_item2` VARCHAR(23) COMMENT 'remove_item2',
    `cutsceneid1` VARCHAR(3) COMMENT 'cutsceneid1',
    `cs1_haction` VARCHAR(5) COMMENT 'cs1_haction',
    `talk_npc3` VARCHAR(98) COMMENT 'talk_npc3',
    `give_item3` VARCHAR(22) COMMENT 'give_item3',
    `remove_item3` VARCHAR(22) COMMENT 'remove_item3',
    `_attr_id` VARCHAR(128) COMMENT 'id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'Quest_SimpleTalk';

