DROP TABLE IF EXISTS xmldb_suiyue.pc_vital_point_special01;
CREATE TABLE xmldb_suiyue.pc_vital_point_special01 (
    `vp_per_tic_light` VARCHAR(255) PRIMARY KEY COMMENT 'vp_per_tic_light',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `vp_per_tic_dark` VARCHAR(4) COMMENT 'vp_per_tic_dark',
    `vp_use_ratio_light` VARCHAR(2) COMMENT 'vp_use_ratio_light',
    `vp_use_ratio_dark` VARCHAR(2) COMMENT 'vp_use_ratio_dark',
    `vp_max_light` VARCHAR(7) COMMENT 'vp_max_light',
    `vp_max_dark` VARCHAR(7) COMMENT 'vp_max_dark',
    `drop_ratio` VARCHAR(1) COMMENT 'drop_ratio',
    `boost_vp_per_tic_light` VARCHAR(6) COMMENT 'boost_vp_per_tic_light',
    `boost_vp_per_tic_dark` VARCHAR(6) COMMENT 'boost_vp_per_tic_dark',
    `boost_vp_use_ratio_light` VARCHAR(2) COMMENT 'boost_vp_use_ratio_light',
    `boost_vp_use_ratio_dark` VARCHAR(2) COMMENT 'boost_vp_use_ratio_dark',
    `boost_vp_max_light` VARCHAR(8) COMMENT 'boost_vp_max_light',
    `boost_vp_max_dark` VARCHAR(8) COMMENT 'boost_vp_max_dark',
    `boost_drop_ratio` VARCHAR(1) COMMENT 'boost_drop_ratio',
    `_attr_level` VARCHAR(128) COMMENT 'level'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'pc_vital_point_special01';

