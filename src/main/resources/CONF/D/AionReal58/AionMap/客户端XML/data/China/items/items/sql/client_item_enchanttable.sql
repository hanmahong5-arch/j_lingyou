DROP TABLE IF EXISTS xmldb_suiyue.client_item_enchanttable;
CREATE TABLE xmldb_suiyue.client_item_enchanttable (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(30) COMMENT 'name',
    `stat_type` VARCHAR(11) COMMENT 'stat_type',
    `enchant_attr_list` VARCHAR(64) COMMENT 'enchant_attr_list',
    `start_limitless` VARCHAR(2) COMMENT 'start_limitless',
    `limitless_attr_list` VARCHAR(64) COMMENT 'limitless_attr_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_item_enchanttable';

DROP TABLE IF EXISTS xmldb_suiyue.client_item_enchanttable__enchant_attr_list__data;
CREATE TABLE xmldb_suiyue.client_item_enchanttable__enchant_attr_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `level` VARCHAR(2) COMMENT 'level',
    `attr1` VARCHAR(33) COMMENT 'attr1',
    `attr2` VARCHAR(34) COMMENT 'attr2',
    `attr3` VARCHAR(36) COMMENT 'attr3',
    `attr4` VARCHAR(34) COMMENT 'attr4',
    `attr5` VARCHAR(32) COMMENT 'attr5',
    `attr6` VARCHAR(35) COMMENT 'attr6',
    `attr7` VARCHAR(27) COMMENT 'attr7',
    `attr8` VARCHAR(26) COMMENT 'attr8',
    `attr9` VARCHAR(28) COMMENT 'attr9',
    `attr10` VARCHAR(27) COMMENT 'attr10'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_item_enchanttable__enchant_attr_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_item_enchanttable__limitless_attr_list__data;
CREATE TABLE xmldb_suiyue.client_item_enchanttable__limitless_attr_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `index` VARCHAR(1) COMMENT 'index',
    `attr_name` VARCHAR(30) COMMENT 'attr_name',
    `attr_value` VARCHAR(3) COMMENT 'attr_value',
    `random_enchant` VARCHAR(1) COMMENT 'random_enchant',
    `random_range` VARCHAR(3) COMMENT 'random_range',
    `random_prob` VARCHAR(1) COMMENT 'random_prob'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_item_enchanttable__limitless_attr_list__data';

