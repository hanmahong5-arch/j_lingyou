DROP TABLE IF EXISTS xmldb_suiyue.client_strings_item2;
CREATE TABLE xmldb_suiyue.client_strings_item2 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(64) COMMENT 'name',
    `body` TEXT COMMENT 'body',
    `message_type` VARCHAR(64) COMMENT 'message_type',
    `__review__` VARCHAR(1) COMMENT '__review__'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_item2';

