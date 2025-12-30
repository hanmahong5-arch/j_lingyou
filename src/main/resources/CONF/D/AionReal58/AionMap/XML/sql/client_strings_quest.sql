DROP TABLE IF EXISTS xmldb_suiyue.client_strings_quest;
CREATE TABLE xmldb_suiyue.client_strings_quest (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(48) COMMENT 'name',
    `body` VARCHAR(132) COMMENT 'body',
    `message_type` VARCHAR(18) COMMENT 'message_type'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_quest';

