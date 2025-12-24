DROP TABLE IF EXISTS xmldb_suiyue.client_strings_msg;
CREATE TABLE xmldb_suiyue.client_strings_msg (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(85) COMMENT 'name',
    `body` TEXT COMMENT 'body',
    `message_type` VARCHAR(3) COMMENT 'message_type',
    `display_type` VARCHAR(4) COMMENT 'display_type',
    `ment` VARCHAR(38) COMMENT 'ment',
    `__review__` VARCHAR(1) COMMENT '__review__'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_msg';

