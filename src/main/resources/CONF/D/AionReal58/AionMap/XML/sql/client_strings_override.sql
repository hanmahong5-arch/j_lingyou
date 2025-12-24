DROP TABLE IF EXISTS xmldb_suiyue.client_strings_override;
CREATE TABLE xmldb_suiyue.client_strings_override (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(65) COMMENT 'name',
    `body` TEXT COMMENT 'body',
    `_attr__body__cdata` VARCHAR(128) COMMENT 'cdata',
    `string` VARCHAR(64) COMMENT 'string'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_override';

DROP TABLE IF EXISTS xmldb_suiyue.client_strings_override__string;
CREATE TABLE xmldb_suiyue.client_strings_override__string (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `id` VARCHAR(7) COMMENT 'id',
    `name` VARCHAR(65) COMMENT 'name',
    `body` TEXT COMMENT 'body'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_override__string';

