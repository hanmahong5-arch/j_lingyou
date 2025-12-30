DROP TABLE IF EXISTS xmldb_suiyue.client_npc_purchase_list;
CREATE TABLE xmldb_suiyue.client_npc_purchase_list (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(41) COMMENT 'name',
    `desc` VARCHAR(29) COMMENT 'desc',
    `use_category` VARCHAR(1) COMMENT 'use_category',
    `goods_list` VARCHAR(64) COMMENT 'goods_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npc_purchase_list';

DROP TABLE IF EXISTS xmldb_suiyue.client_npc_purchase_list__goods_list__data;
CREATE TABLE xmldb_suiyue.client_npc_purchase_list__goods_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `item` VARCHAR(43) COMMENT 'item'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npc_purchase_list__goods_list__data';

