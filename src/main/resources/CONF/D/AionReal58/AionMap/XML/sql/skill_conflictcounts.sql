DROP TABLE IF EXISTS xmldb_suiyue.skill_conflictcounts;
CREATE TABLE xmldb_suiyue.skill_conflictcounts (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(2) COMMENT 'name',
    `count` VARCHAR(1) COMMENT 'count'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'skill_conflictcounts';

