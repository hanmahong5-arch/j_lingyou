DROP TABLE IF EXISTS xmldb_suiyue.jumping_endquest;
CREATE TABLE xmldb_suiyue.jumping_endquest (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(28) COMMENT 'name',
    `endquest_expansion` VARCHAR(64) COMMENT 'endquest_expansion'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'jumping_endquest';

DROP TABLE IF EXISTS xmldb_suiyue.jumping_endquest__endquest_expansion__data;
CREATE TABLE xmldb_suiyue.jumping_endquest__endquest_expansion__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `end_quest` VARCHAR(6) COMMENT 'end_quest'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'jumping_endquest__endquest_expansion__data';

