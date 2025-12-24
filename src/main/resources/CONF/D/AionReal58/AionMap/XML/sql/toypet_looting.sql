DROP TABLE IF EXISTS xmldb_suiyue.toypet_looting;
CREATE TABLE xmldb_suiyue.toypet_looting (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(14) COMMENT 'name'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'toypet_looting';

