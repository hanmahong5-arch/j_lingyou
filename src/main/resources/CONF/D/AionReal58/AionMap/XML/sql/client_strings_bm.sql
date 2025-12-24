DROP TABLE IF EXISTS xmldb_suiyue.client_strings_bm;
CREATE TABLE xmldb_suiyue.client_strings_bm (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(53) COMMENT 'name',
    `body` VARCHAR(60) COMMENT 'body'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_bm';

