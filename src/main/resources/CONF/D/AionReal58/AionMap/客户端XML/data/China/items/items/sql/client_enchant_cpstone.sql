DROP TABLE IF EXISTS xmldb_suiyue.client_enchant_cpstone;
CREATE TABLE xmldb_suiyue.client_enchant_cpstone (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(23) COMMENT 'name',
    `base_cpstone` VARCHAR(1) COMMENT 'base_cpstone',
    `enchant_cpstone_list` VARCHAR(64) COMMENT 'enchant_cpstone_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_enchant_cpstone';

DROP TABLE IF EXISTS xmldb_suiyue.client_enchant_cpstone__enchant_cpstone_list__data;
CREATE TABLE xmldb_suiyue.client_enchant_cpstone__enchant_cpstone_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `enchant_cpstone` VARCHAR(2) COMMENT 'enchant_cpstone'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_enchant_cpstone__enchant_cpstone_list__data';

