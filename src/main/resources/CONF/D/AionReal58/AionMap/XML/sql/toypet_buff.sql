DROP TABLE IF EXISTS xmldb_suiyue.toypet_buff;
CREATE TABLE xmldb_suiyue.toypet_buff (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(19) COMMENT 'name',
    `food_item` VARCHAR(32) COMMENT 'food_item',
    `food_consume_count` VARCHAR(1) COMMENT 'food_consume_count',
    `bonus_attr1` VARCHAR(17) COMMENT 'bonus_attr1',
    `bonus_attr2` VARCHAR(20) COMMENT 'bonus_attr2',
    `bonus_attr3` VARCHAR(25) COMMENT 'bonus_attr3',
    `bonus_attr4` VARCHAR(20) COMMENT 'bonus_attr4',
    `bonus_attr5` VARCHAR(25) COMMENT 'bonus_attr5'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'toypet_buff';

