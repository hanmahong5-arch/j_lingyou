DROP TABLE IF EXISTS xmldb_suiyue.items_looting_fx;
CREATE TABLE xmldb_suiyue.items_looting_fx (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(19) COMMENT 'name',
    `priority` VARCHAR(1) COMMENT 'priority'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'items_looting_fx';

