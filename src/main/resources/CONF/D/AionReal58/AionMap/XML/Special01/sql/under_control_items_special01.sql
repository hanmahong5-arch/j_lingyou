DROP TABLE IF EXISTS xmldb_suiyue.under_control_items_special01;
CREATE TABLE xmldb_suiyue.under_control_items_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(31) COMMENT 'name',
    `drop_ctrl_period_type` VARCHAR(10) COMMENT 'drop_ctrl_period_type',
    `drop_limit` VARCHAR(3) COMMENT 'drop_limit',
    `intersvr_drop_limit` VARCHAR(3) COMMENT 'intersvr_drop_limit',
    `indunsvr_drop_limit` VARCHAR(3) COMMENT 'indunsvr_drop_limit'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'under_control_items_special01';

