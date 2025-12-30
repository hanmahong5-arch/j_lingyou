DROP TABLE IF EXISTS xmldb_suiyue.item_option_probability;
CREATE TABLE xmldb_suiyue.item_option_probability (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(28) COMMENT 'name',
    `option_slot_adj_value` VARCHAR(4) COMMENT 'option_slot_adj_value',
    `special_slot_adj_value` VARCHAR(4) COMMENT 'special_slot_adj_value'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_option_probability';

