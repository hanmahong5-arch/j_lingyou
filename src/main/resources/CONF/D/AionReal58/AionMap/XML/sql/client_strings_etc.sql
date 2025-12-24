DROP TABLE IF EXISTS xmldb_suiyue.client_strings_etc;
CREATE TABLE xmldb_suiyue.client_strings_etc (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(44) COMMENT 'name',
    `body` VARCHAR(44) COMMENT 'body'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_etc';

