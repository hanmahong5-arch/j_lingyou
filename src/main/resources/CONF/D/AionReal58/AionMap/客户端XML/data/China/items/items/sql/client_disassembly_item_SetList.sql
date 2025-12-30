DROP TABLE IF EXISTS xmldb_suiyue.client_disassembly_item_SetList;
CREATE TABLE xmldb_suiyue.client_disassembly_item_SetList (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(75) COMMENT 'name',
    `disassembly_create_item_list` VARCHAR(64) COMMENT 'disassembly_create_item_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_disassembly_item_SetList';

DROP TABLE IF EXISTS xmldb_suiyue.client_disassembly_item_SetList__d_c_i_l__data;
CREATE TABLE xmldb_suiyue.client_disassembly_item_SetList__d_c_i_l__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `create_` VARCHAR(61) COMMENT 'create_',
    `num_` VARCHAR(1) COMMENT 'num_',
    `rate_` VARCHAR(5) COMMENT 'rate_'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_disassembly_item_SetList__d_c_i_l__data';

