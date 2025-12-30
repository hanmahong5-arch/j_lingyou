DROP TABLE IF EXISTS xmldb_suiyue.client_strings_skill;
CREATE TABLE xmldb_suiyue.client_strings_skill (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(67) COMMENT 'name',
    `body` TEXT COMMENT 'body',
    `message_type` VARCHAR(55) COMMENT 'message_type'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_skill';

