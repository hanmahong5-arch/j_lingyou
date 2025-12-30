DROP TABLE IF EXISTS xmldb_suiyue.vip_grade_exp_special01;
CREATE TABLE xmldb_suiyue.vip_grade_exp_special01 (
    `grade` VARCHAR(255) PRIMARY KEY COMMENT 'grade',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `min_vipexp` VARCHAR(4) COMMENT 'min_vipexp',
    `max_vipexp` VARCHAR(4) COMMENT 'max_vipexp',
    `_attr_vip_lv` VARCHAR(128) COMMENT 'vip_lv'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'vip_grade_exp_special01';

