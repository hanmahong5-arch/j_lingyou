DROP TABLE IF EXISTS xmldb_suiyue.Quest_SimpleCollectItem;
CREATE TABLE xmldb_suiyue.Quest_SimpleCollectItem (
    `dev_name` VARCHAR(255) PRIMARY KEY COMMENT 'dev_name',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `acquired_npc_name` VARCHAR(30) COMMENT 'acquired_npc_name',
    `object1` VARCHAR(35) COMMENT 'object1',
    `reward_npc_name` VARCHAR(30) COMMENT 'reward_npc_name',
    `con_quest` VARCHAR(5) COMMENT 'con_quest',
    `object2` VARCHAR(24) COMMENT 'object2',
    `party_drop` VARCHAR(1) COMMENT 'party_drop',
    `object3` VARCHAR(24) COMMENT 'object3',
    `give_item` VARCHAR(22) COMMENT 'give_item',
    `object4` VARCHAR(24) COMMENT 'object4',
    `cutsceneid1` VARCHAR(3) COMMENT 'cutsceneid1',
    `cs1_haction` VARCHAR(4) COMMENT 'cs1_haction',
    `talk_npc1` VARCHAR(15) COMMENT 'talk_npc1',
    `talk_npc2` VARCHAR(7) COMMENT 'talk_npc2',
    `talk_npc3` VARCHAR(5) COMMENT 'talk_npc3',
    `reward_check` VARCHAR(12) COMMENT 'reward_check',
    `give_item1` VARCHAR(19) COMMENT 'give_item1',
    `remove_item2` VARCHAR(19) COMMENT 'remove_item2',
    `_attr_id` VARCHAR(128) COMMENT 'id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'Quest_SimpleCollectItem';

