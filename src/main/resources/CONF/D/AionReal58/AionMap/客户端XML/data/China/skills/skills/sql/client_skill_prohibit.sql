DROP TABLE IF EXISTS xmldb_suiyue.client_skill_prohibit;
CREATE TABLE xmldb_suiyue.client_skill_prohibit (
    `desc` VARCHAR(255) PRIMARY KEY COMMENT 'desc',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `limit_type` VARCHAR(2) COMMENT 'limit_type',
    `allow_skill` TEXT COMMENT 'allow_skill',
    `ban_skill` TEXT COMMENT 'ban_skill',
    `clear_in_skill` TEXT COMMENT 'clear_in_skill',
    `clear_out_skill` TEXT COMMENT 'clear_out_skill',
    `_attr_ID` VARCHAR(128) COMMENT 'ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_skill_prohibit';

