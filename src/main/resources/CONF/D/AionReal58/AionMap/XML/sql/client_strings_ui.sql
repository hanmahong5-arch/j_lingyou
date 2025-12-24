DROP TABLE IF EXISTS xmldb_suiyue.client_strings_ui;
CREATE TABLE xmldb_suiyue.client_strings_ui (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(67) COMMENT 'name',
    `body` TEXT COMMENT 'body',
    `__review__` VARCHAR(1) COMMENT '__review__'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_ui';

