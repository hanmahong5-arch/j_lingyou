DROP TABLE IF EXISTS xmldb_suiyue.client_item_custom_set;
CREATE TABLE xmldb_suiyue.client_item_custom_set (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(36) COMMENT 'name',
    `custom_enchant_value` VARCHAR(2) COMMENT 'custom_enchant_value',
    `custom_option_slot_1` VARCHAR(40) COMMENT 'custom_option_slot_1',
    `custom_option_slot_2` VARCHAR(40) COMMENT 'custom_option_slot_2',
    `custom_option_slot_3` VARCHAR(40) COMMENT 'custom_option_slot_3',
    `custom_option_slot_4` VARCHAR(38) COMMENT 'custom_option_slot_4',
    `custom_option_slot_5` VARCHAR(38) COMMENT 'custom_option_slot_5',
    `custom_option_slot_6` VARCHAR(38) COMMENT 'custom_option_slot_6',
    `custom_authorize_value` VARCHAR(2) COMMENT 'custom_authorize_value'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_item_custom_set';

