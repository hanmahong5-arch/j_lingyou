DROP TABLE IF EXISTS xmldb_suiyue.gameexperience_setting_items;
CREATE TABLE xmldb_suiyue.gameexperience_setting_items (
    `item_nameid` VARCHAR(255) PRIMARY KEY COMMENT 'item_nameid',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `player_type` VARCHAR(9) COMMENT 'player_type',
    `_attr_id` VARCHAR(128) COMMENT 'id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'gameexperience_setting_items';

