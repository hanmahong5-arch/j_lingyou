DROP TABLE IF EXISTS xmldb_suiyue.toypet_warehouse;
CREATE TABLE xmldb_suiyue.toypet_warehouse (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(20) COMMENT 'name',
    `warehouse_slot_type` VARCHAR(6) COMMENT 'warehouse_slot_type',
    `warehouse_slot_count` VARCHAR(2) COMMENT 'warehouse_slot_count'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'toypet_warehouse';

