DROP TABLE IF EXISTS xmldb_suiyue.item_random_option;
CREATE TABLE xmldb_suiyue.item_random_option (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(32) COMMENT 'name',
    `random_attr_group_list` VARCHAR(64) COMMENT 'random_attr_group_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_random_option';

DROP TABLE IF EXISTS xmldb_suiyue.item_random_option__random_attr_group_list__data;
CREATE TABLE xmldb_suiyue.item_random_option__random_attr_group_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `attr_group_id` VARCHAR(2) COMMENT 'attr_group_id',
    `prob` VARCHAR(5) COMMENT 'prob',
    `random_attr1` VARCHAR(26) COMMENT 'random_attr1',
    `random_attr2` VARCHAR(26) COMMENT 'random_attr2',
    `random_attr3` VARCHAR(26) COMMENT 'random_attr3',
    `random_attr4` VARCHAR(25) COMMENT 'random_attr4',
    `random_attr5` VARCHAR(25) COMMENT 'random_attr5',
    `random_attr6` VARCHAR(20) COMMENT 'random_attr6',
    `random_attr7` VARCHAR(17) COMMENT 'random_attr7',
    `random_attr8` VARCHAR(17) COMMENT 'random_attr8'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_random_option__random_attr_group_list__data';

