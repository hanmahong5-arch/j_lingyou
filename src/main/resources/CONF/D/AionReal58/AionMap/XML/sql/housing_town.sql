DROP TABLE IF EXISTS xmldb_suiyue.housing_town;
CREATE TABLE xmldb_suiyue.housing_town (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(17) COMMENT 'name',
    `desc` VARCHAR(21) COMMENT 'desc'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'housing_town';

