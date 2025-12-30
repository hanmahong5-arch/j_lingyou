DROP TABLE IF EXISTS xmldb_suiyue.item_prohibit;
CREATE TABLE xmldb_suiyue.item_prohibit (
    `limit_item` VARCHAR(255) PRIMARY KEY COMMENT 'limit_item',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `allow_item` VARCHAR(73) COMMENT 'allow_item',
    `ban_item1` TEXT COMMENT 'ban_item1',
    `ban_item2` TEXT COMMENT 'ban_item2',
    `ban_item3` TEXT COMMENT 'ban_item3',
    `ban_item4` TEXT COMMENT 'ban_item4',
    `_attr_ID` VARCHAR(128) COMMENT 'ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_prohibit';

