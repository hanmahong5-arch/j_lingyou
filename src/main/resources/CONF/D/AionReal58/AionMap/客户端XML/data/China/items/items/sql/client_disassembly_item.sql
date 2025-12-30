DROP TABLE IF EXISTS xmldb_suiyue.client_disassembly_item;
CREATE TABLE xmldb_suiyue.client_disassembly_item (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(62) COMMENT 'name',
    `disassemble_set_list` VARCHAR(64) COMMENT 'disassemble_set_list',
    `slot_effect` VARCHAR(1) COMMENT 'slot_effect',
    `retry_max_count` VARCHAR(2) COMMENT 'retry_max_count',
    `per_type` VARCHAR(4) COMMENT 'per_type',
    `per_retry` VARCHAR(7) COMMENT 'per_retry',
    `per_retry_rate` VARCHAR(8) COMMENT 'per_retry_rate',
    `component_per_retry` VARCHAR(32) COMMENT 'component_per_retry',
    `component_per_retry_type` VARCHAR(5) COMMENT 'component_per_retry_type',
    `component_per_retry_count` VARCHAR(3) COMMENT 'component_per_retry_count',
    `disassembly_create_item_list` VARCHAR(64) COMMENT 'disassembly_create_item_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_disassembly_item';

DROP TABLE IF EXISTS xmldb_suiyue.client_disassembly_item__disassemble_set_list__data;
CREATE TABLE xmldb_suiyue.client_disassembly_item__disassemble_set_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `set_list_name_` VARCHAR(75) COMMENT 'set_list_name_',
    `apply_level_` VARCHAR(6) COMMENT 'apply_level_',
    `apply_class_` VARCHAR(80) COMMENT 'apply_class_',
    `apply_race_` VARCHAR(8) COMMENT 'apply_race_',
    `rate_` VARCHAR(5) COMMENT 'rate_'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_disassembly_item__disassemble_set_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_disassembly_item__disassembly_create_item_list__data;
CREATE TABLE xmldb_suiyue.client_disassembly_item__disassembly_create_item_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `create_` VARCHAR(29) COMMENT 'create_',
    `num_` VARCHAR(1) COMMENT 'num_',
    `rate_` VARCHAR(5) COMMENT 'rate_'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_disassembly_item__disassembly_create_item_list__data';

