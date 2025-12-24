DROP TABLE IF EXISTS xmldb_suiyue.familiar_sgrade_ratio;
CREATE TABLE xmldb_suiyue.familiar_sgrade_ratio (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(19) COMMENT 'name',
    `desc` VARCHAR(10) COMMENT 'desc',
    `sgrade_ratio_1` VARCHAR(5) COMMENT 'sgrade_ratio_1',
    `sgrade_ratio_2` VARCHAR(4) COMMENT 'sgrade_ratio_2',
    `sgrade_ratio_3` VARCHAR(4) COMMENT 'sgrade_ratio_3',
    `sgrade_ratio_4` VARCHAR(5) COMMENT 'sgrade_ratio_4'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'familiar_sgrade_ratio';

