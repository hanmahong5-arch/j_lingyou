DROP TABLE IF EXISTS xmldb_suiyue.familiar_contract;
CREATE TABLE xmldb_suiyue.familiar_contract (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(36) COMMENT 'name',
    `dev_name` VARCHAR(26) COMMENT 'dev_name',
    `contract_list` VARCHAR(64) COMMENT 'contract_list',
    `composite_result` VARCHAR(14) COMMENT 'composite_result',
    `composite_ratio` VARCHAR(4) COMMENT 'composite_ratio',
    `server_type` VARCHAR(1) COMMENT 'server_type'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'familiar_contract';

DROP TABLE IF EXISTS xmldb_suiyue.familiar_contract__contract_list__data;
CREATE TABLE xmldb_suiyue.familiar_contract__contract_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `familiar_group_name` VARCHAR(26) COMMENT 'familiar_group_name',
    `familiar_ratio` VARCHAR(4) COMMENT 'familiar_ratio',
    `sgrade_ratio` VARCHAR(19) COMMENT 'sgrade_ratio'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'familiar_contract__contract_list__data';

