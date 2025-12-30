DROP TABLE IF EXISTS xmldb_suiyue.abyss_mist_times_special01;
CREATE TABLE xmldb_suiyue.abyss_mist_times_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(4) COMMENT 'name',
    `__real_time` VARCHAR(8) COMMENT '__real_time',
    `mon` VARCHAR(12) COMMENT 'mon',
    `wed` VARCHAR(12) COMMENT 'wed',
    `fri` VARCHAR(12) COMMENT 'fri',
    `sun` VARCHAR(40) COMMENT 'sun'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_mist_times_special01';

