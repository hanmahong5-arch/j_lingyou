DROP TABLE IF EXISTS xmldb_suiyue.pc_death_penalty_special01;
CREATE TABLE xmldb_suiyue.pc_death_penalty_special01 (
    `permanent_loss_light` VARCHAR(255) PRIMARY KEY COMMENT 'permanent_loss_light',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `permanent_loss_dark` VARCHAR(8) COMMENT 'permanent_loss_dark',
    `temporary_loss_light` VARCHAR(8) COMMENT 'temporary_loss_light',
    `temporary_loss_dark` VARCHAR(8) COMMENT 'temporary_loss_dark',
    `temporary_max_light` VARCHAR(11) COMMENT 'temporary_max_light',
    `temporary_max_dark` VARCHAR(11) COMMENT 'temporary_max_dark',
    `_attr_level` VARCHAR(128) COMMENT 'level'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'pc_death_penalty_special01';

