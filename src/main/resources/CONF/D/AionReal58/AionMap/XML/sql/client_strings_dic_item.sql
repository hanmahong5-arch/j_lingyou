DROP TABLE IF EXISTS xmldb_suiyue.client_strings_dic_item;
CREATE TABLE xmldb_suiyue.client_strings_dic_item (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(52) COMMENT 'name',
    `body` TEXT COMMENT 'body'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_strings_dic_item';

