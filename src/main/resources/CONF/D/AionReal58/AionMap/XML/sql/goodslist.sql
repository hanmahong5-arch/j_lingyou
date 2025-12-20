DROP TABLE IF EXISTS xmldb_suiyue.goodslist;
CREATE TABLE xmldb_suiyue.goodslist (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(51) COMMENT 'name',
    `desc` VARCHAR(47) COMMENT 'desc',
    `goods_list` VARCHAR(64) COMMENT 'goods_list',
    `use_category` VARCHAR(1) COMMENT 'use_category',
    `guild_level` VARCHAR(1) COMMENT 'guild_level',
    `salestime_table_name` VARCHAR(21) COMMENT 'salestime_table_name',
    `sale_explain_desc` VARCHAR(40) COMMENT 'sale_explain_desc',
    `advertise_msg` VARCHAR(44) COMMENT 'advertise_msg',
    `gossip_msg` VARCHAR(35) COMMENT 'gossip_msg',
    `sales_clear_turn` VARCHAR(2) COMMENT 'sales_clear_turn',
    `sales_clear_interval` VARCHAR(3) COMMENT 'sales_clear_interval',
    `sales_server` VARCHAR(3) COMMENT 'sales_server'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'goodslist';

DROP TABLE IF EXISTS xmldb_suiyue.goodslist__goods_list;
CREATE TABLE xmldb_suiyue.goodslist__goods_list (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `data` VARCHAR(64) COMMENT 'data',
    `goods_list` VARCHAR(64) COMMENT 'goods_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'goodslist__goods_list';

DROP TABLE IF EXISTS xmldb_suiyue.goodslist__goods_list__data;
CREATE TABLE xmldb_suiyue.goodslist__goods_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `item` VARCHAR(69) COMMENT 'item',
    `sell_limit` VARCHAR(5) COMMENT 'sell_limit',
    `buy_limit` VARCHAR(3) COMMENT 'buy_limit'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'goodslist__goods_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.goodslist__goods_list__goods_list__data;
CREATE TABLE xmldb_suiyue.goodslist__goods_list__goods_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `item` VARCHAR(69) COMMENT 'item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'goodslist__goods_list__goods_list__data';

