DROP TABLE IF EXISTS xmldb_suiyue.login_event;
CREATE TABLE xmldb_suiyue.login_event (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(26) COMMENT 'name',
    `active` VARCHAR(1) COMMENT 'active',
    `attend_type` VARCHAR(11) COMMENT 'attend_type',
    `reward_item_list` VARCHAR(64) COMMENT 'reward_item_list',
    `attend_stamp_title` VARCHAR(33) COMMENT 'attend_stamp_title',
    `attend_stamp_sub_title` VARCHAR(25) COMMENT 'attend_stamp_sub_title'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'login_event';

DROP TABLE IF EXISTS xmldb_suiyue.login_event__reward_item_list__data;
CREATE TABLE xmldb_suiyue.login_event__reward_item_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `reward_item` VARCHAR(45) COMMENT 'reward_item',
    `reward_item_count` VARCHAR(2) COMMENT 'reward_item_count',
    `reward_permit_level` VARCHAR(2) COMMENT 'reward_permit_level'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'login_event__reward_item_list__data';

