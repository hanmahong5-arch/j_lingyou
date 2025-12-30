DROP TABLE IF EXISTS xmldb_suiyue.feather_authorizetable_special01;
CREATE TABLE xmldb_suiyue.feather_authorizetable_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(30) COMMENT 'name',
    `tshirt_attr_list` VARCHAR(64) COMMENT 'tshirt_attr_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'feather_authorizetable_special01';

DROP TABLE IF EXISTS xmldb_suiyue.feather_authorizetable_special01__tshirt_attr_list__data;
CREATE TABLE xmldb_suiyue.feather_authorizetable_special01__tshirt_attr_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `index` VARCHAR(1) COMMENT 'index',
    `attr_name` VARCHAR(22) COMMENT 'attr_name',
    `attr_value` VARCHAR(3) COMMENT 'attr_value',
    `random_authorize` VARCHAR(1) COMMENT 'random_authorize',
    `random_range` VARCHAR(2) COMMENT 'random_range',
    `random_prob` VARCHAR(2) COMMENT 'random_prob'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'feather_authorizetable_special01__tshirt_attr_list__data';

