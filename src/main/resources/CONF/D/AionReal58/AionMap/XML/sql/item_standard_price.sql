DROP TABLE IF EXISTS xmldb_suiyue.item_standard_price;
CREATE TABLE xmldb_suiyue.item_standard_price (
    `price` VARCHAR(255) PRIMARY KEY COMMENT 'price',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `_attr_level` VARCHAR(128) COMMENT 'level'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_standard_price';

