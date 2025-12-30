DROP TABLE IF EXISTS xmldb_suiyue.monster_scaling_drop_setList_special01;
CREATE TABLE xmldb_suiyue.monster_scaling_drop_setList_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(35) COMMENT 'name',
    `scailing_create_item_list` VARCHAR(64) COMMENT 'scailing_create_item_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'monster_scaling_drop_setList_special01';

DROP TABLE IF EXISTS xmldb_suiyue.monster_scaling_drop_setList_special01__s_c_i_l__data;
CREATE TABLE xmldb_suiyue.monster_scaling_drop_setList_special01__s_c_i_l__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `create_` VARCHAR(37) COMMENT 'create_',
    `num_` VARCHAR(2) COMMENT 'num_',
    `rate_` VARCHAR(5) COMMENT 'rate_'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'monster_scaling_drop_setList_special01__s_c_i_l__data';

