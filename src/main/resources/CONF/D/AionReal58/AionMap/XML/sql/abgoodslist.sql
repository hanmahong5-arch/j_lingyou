DROP TABLE IF EXISTS xmldb_suiyue.abgoodslist;
CREATE TABLE xmldb_suiyue.abgoodslist (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(20) COMMENT 'name',
    `desc` VARCHAR(19) COMMENT 'desc',
    `goods_list` VARCHAR(64) COMMENT 'goods_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abgoodslist';

DROP TABLE IF EXISTS xmldb_suiyue.abgoodslist__goods_list__data;
CREATE TABLE xmldb_suiyue.abgoodslist__goods_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `item` VARCHAR(15) COMMENT 'item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abgoodslist__goods_list__data';

