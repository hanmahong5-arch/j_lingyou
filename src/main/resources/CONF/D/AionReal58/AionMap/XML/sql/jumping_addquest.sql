DROP TABLE IF EXISTS xmldb_suiyue.jumping_addquest;
CREATE TABLE xmldb_suiyue.jumping_addquest (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(26) COMMENT 'name',
    `addquest_expansion` VARCHAR(64) COMMENT 'addquest_expansion'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'jumping_addquest';

DROP TABLE IF EXISTS xmldb_suiyue.jumping_addquest__addquest_expansion__data;
CREATE TABLE xmldb_suiyue.jumping_addquest__addquest_expansion__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `add_quest` VARCHAR(6) COMMENT 'add_quest'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'jumping_addquest__addquest_expansion__data';

