DROP TABLE IF EXISTS xmldb_suiyue.item_replace_special01;
CREATE TABLE xmldb_suiyue.item_replace_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(36) COMMENT 'name',
    `__dev_name1__` VARCHAR(31) COMMENT '__dev_name1__',
    `__dev_name2__` VARCHAR(28) COMMENT '__dev_name2__',
    `itemid_min` VARCHAR(9) COMMENT 'itemid_min',
    `itemid_max` VARCHAR(9) COMMENT 'itemid_max',
    `itemid_replace` VARCHAR(9) COMMENT 'itemid_replace'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_replace_special01';

