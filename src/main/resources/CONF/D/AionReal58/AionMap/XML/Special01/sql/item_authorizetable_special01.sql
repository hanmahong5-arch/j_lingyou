DROP TABLE IF EXISTS xmldb_suiyue.item_authorizetable_special01;
CREATE TABLE xmldb_suiyue.item_authorizetable_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(32) COMMENT 'name',
    `enchant_attr_list` VARCHAR(64) COMMENT 'enchant_attr_list',
    `start_limitless` VARCHAR(2) COMMENT 'start_limitless',
    `limitless_attr_list` VARCHAR(64) COMMENT 'limitless_attr_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_authorizetable_special01';

DROP TABLE IF EXISTS xmldb_suiyue.item_authorizetable_special01__enchant_attr_list__data;
CREATE TABLE xmldb_suiyue.item_authorizetable_special01__enchant_attr_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `level` VARCHAR(2) COMMENT 'level',
    `attr1` VARCHAR(36) COMMENT 'attr1',
    `attr2` VARCHAR(32) COMMENT 'attr2',
    `attr3` VARCHAR(32) COMMENT 'attr3',
    `attr4` VARCHAR(35) COMMENT 'attr4',
    `attr9` VARCHAR(29) COMMENT 'attr9',
    `attr10` VARCHAR(28) COMMENT 'attr10',
    `attr5` VARCHAR(18) COMMENT 'attr5',
    `attr6` VARCHAR(22) COMMENT 'attr6'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_authorizetable_special01__enchant_attr_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.item_authorizetable_special01__limitless_attr_list__data;
CREATE TABLE xmldb_suiyue.item_authorizetable_special01__limitless_attr_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `index` VARCHAR(1) COMMENT 'index',
    `attr_name` VARCHAR(29) COMMENT 'attr_name',
    `attr_value` VARCHAR(3) COMMENT 'attr_value',
    `random_enchant` VARCHAR(1) COMMENT 'random_enchant',
    `random_range` VARCHAR(2) COMMENT 'random_range',
    `random_prob` VARCHAR(2) COMMENT 'random_prob'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_authorizetable_special01__limitless_attr_list__data';

