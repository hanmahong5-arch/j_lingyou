DROP TABLE IF EXISTS xmldb_suiyue.vip_benefit_special01;
CREATE TABLE xmldb_suiyue.vip_benefit_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(24) COMMENT 'name',
    `name_str` VARCHAR(34) COMMENT 'name_str',
    `base_check` VARCHAR(5) COMMENT 'base_check',
    `apply` VARCHAR(5) COMMENT 'apply',
    `grade1` VARCHAR(21) COMMENT 'grade1',
    `grade2` VARCHAR(21) COMMENT 'grade2',
    `grade3` VARCHAR(21) COMMENT 'grade3',
    `grade4` VARCHAR(21) COMMENT 'grade4',
    `grade5` VARCHAR(21) COMMENT 'grade5',
    `grade6` VARCHAR(14) COMMENT 'grade6'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'vip_benefit_special01';

