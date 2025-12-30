DROP TABLE IF EXISTS xmldb_suiyue.guild_emblem;
CREATE TABLE xmldb_suiyue.guild_emblem (
    `server` VARCHAR(255) PRIMARY KEY COMMENT 'server',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `id` VARCHAR(4) COMMENT 'id',
    `ver` VARCHAR(1) COMMENT 'ver',
    `bgcolor` VARCHAR(10) COMMENT 'bgcolor',
    `name` VARCHAR(9) COMMENT 'name'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'guild_emblem';

