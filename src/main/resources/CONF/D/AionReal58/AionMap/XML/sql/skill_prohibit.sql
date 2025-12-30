DROP TABLE IF EXISTS xmldb_suiyue.skill_prohibit;
CREATE TABLE xmldb_suiyue.skill_prohibit (
    `limit_type` VARCHAR(255) PRIMARY KEY COMMENT 'limit_type',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `allow_skill` TEXT COMMENT 'allow_skill',
    `buff_clear_in` VARCHAR(38) COMMENT 'buff_clear_in',
    `buff_clear_out` VARCHAR(38) COMMENT 'buff_clear_out',
    `ban_skill` TEXT COMMENT 'ban_skill',
    `clear_in_skill` TEXT COMMENT 'clear_in_skill',
    `clear_out_skill` TEXT COMMENT 'clear_out_skill',
    `_attr_ID` VARCHAR(128) COMMENT 'ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'skill_prohibit';

