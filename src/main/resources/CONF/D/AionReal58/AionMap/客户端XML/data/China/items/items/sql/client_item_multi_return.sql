DROP TABLE IF EXISTS xmldb_suiyue.client_item_multi_return;
CREATE TABLE xmldb_suiyue.client_item_multi_return (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(19) COMMENT 'name',
    `return_loc_list` VARCHAR(64) COMMENT 'return_loc_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_item_multi_return';

DROP TABLE IF EXISTS xmldb_suiyue.client_item_multi_return__return_loc_list__data;
CREATE TABLE xmldb_suiyue.client_item_multi_return__return_loc_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `return_alias` VARCHAR(24) COMMENT 'return_alias',
    `return_worldid` VARCHAR(9) COMMENT 'return_worldid',
    `return_desc` VARCHAR(28) COMMENT 'return_desc'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_item_multi_return__return_loc_list__data';

