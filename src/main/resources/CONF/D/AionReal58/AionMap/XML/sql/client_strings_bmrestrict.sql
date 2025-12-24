DROP TABLE IF EXISTS xmldb_suiyue.client_strings_bmrestrict;
CREATE TABLE xmldb_suiyue.client_strings_bmrestrict (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(55) COMMENT 'name',
    `body` VARCHAR(129) COMMENT 'body',
    `message_type` VARCHAR(2) COMMENT 'message_type',
    `display_type` VARCHAR(1) COMMENT 'display_type'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_bmrestrict';

