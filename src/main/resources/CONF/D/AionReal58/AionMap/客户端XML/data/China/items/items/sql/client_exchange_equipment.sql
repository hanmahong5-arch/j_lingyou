DROP TABLE IF EXISTS xmldb_suiyue.client_exchange_equipment;
CREATE TABLE xmldb_suiyue.client_exchange_equipment (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(21) COMMENT 'name',
    `replace_list` VARCHAR(64) COMMENT 'replace_list',
    `period_end` VARCHAR(20) COMMENT 'period_end',
    `period_start` VARCHAR(20) COMMENT 'period_start'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_exchange_equipment';

DROP TABLE IF EXISTS xmldb_suiyue.client_exchange_equipment__replace_list__data;
CREATE TABLE xmldb_suiyue.client_exchange_equipment__replace_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `replace_type` VARCHAR(1) COMMENT 'replace_type',
    `replace_level` VARCHAR(2) COMMENT 'replace_level',
    `replace_name` VARCHAR(21) COMMENT 'replace_name',
    `after_replace_level` VARCHAR(2) COMMENT 'after_replace_level'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_exchange_equipment__replace_list__data';

