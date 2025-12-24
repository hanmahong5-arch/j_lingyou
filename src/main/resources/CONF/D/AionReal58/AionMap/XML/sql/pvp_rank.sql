DROP TABLE IF EXISTS xmldb_suiyue.pvp_rank;
CREATE TABLE xmldb_suiyue.pvp_rank (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(16) COMMENT 'name',
    `desc` VARCHAR(14) COMMENT 'desc',
    `title_desc` VARCHAR(14) COMMENT 'title_desc',
    `rank` VARCHAR(2) COMMENT 'rank',
    `require_point` VARCHAR(4) COMMENT 'require_point',
    `require_order` VARCHAR(2) COMMENT 'require_order'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'pvp_rank';

