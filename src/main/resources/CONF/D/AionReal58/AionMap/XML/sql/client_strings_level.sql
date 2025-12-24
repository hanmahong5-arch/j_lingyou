DROP TABLE IF EXISTS xmldb_suiyue.client_strings_level;
CREATE TABLE xmldb_suiyue.client_strings_level (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(43) COMMENT 'name',
    `body` VARCHAR(205) COMMENT 'body'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_level';

