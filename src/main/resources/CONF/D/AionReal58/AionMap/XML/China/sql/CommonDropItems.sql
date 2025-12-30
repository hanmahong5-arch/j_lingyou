DROP TABLE IF EXISTS xmldb_suiyue.CommonDropItems;
CREATE TABLE xmldb_suiyue.CommonDropItems (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(42) COMMENT 'name',
    `items` VARCHAR(64) COMMENT 'items'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'CommonDropItems';

DROP TABLE IF EXISTS xmldb_suiyue.CommonDropItems__items__data;
CREATE TABLE xmldb_suiyue.CommonDropItems__items__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `item` VARCHAR(45) COMMENT 'item',
    `item_count` VARCHAR(3) COMMENT 'item_count',
    `prob` VARCHAR(8) COMMENT 'prob'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'CommonDropItems__items__data';

