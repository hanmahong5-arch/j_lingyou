DROP TABLE IF EXISTS xmldb_suiyue.client_strings_gossip;
CREATE TABLE xmldb_suiyue.client_strings_gossip (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(58) COMMENT 'name',
    `body` VARCHAR(76) COMMENT 'body',
    `message_type` VARCHAR(2) COMMENT 'message_type',
    `display_type` VARCHAR(2) COMMENT 'display_type',
    `ment` VARCHAR(49) COMMENT 'ment'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_gossip';

