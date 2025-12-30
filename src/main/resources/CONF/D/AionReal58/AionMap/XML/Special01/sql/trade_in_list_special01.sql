DROP TABLE IF EXISTS xmldb_suiyue.trade_in_list_special01;
CREATE TABLE xmldb_suiyue.trade_in_list_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(38) COMMENT 'name',
    `desc` VARCHAR(33) COMMENT 'desc',
    `use_category` VARCHAR(1) COMMENT 'use_category',
    `goods_list` VARCHAR(64) COMMENT 'goods_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'trade_in_list_special01';

DROP TABLE IF EXISTS xmldb_suiyue.trade_in_list_special01__goods_list__data;
CREATE TABLE xmldb_suiyue.trade_in_list_special01__goods_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `item` VARCHAR(49) COMMENT 'item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'trade_in_list_special01__goods_list__data';

