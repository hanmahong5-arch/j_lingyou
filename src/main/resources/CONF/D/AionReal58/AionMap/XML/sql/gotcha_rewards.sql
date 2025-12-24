DROP TABLE IF EXISTS xmldb_suiyue.gotcha_rewards;
CREATE TABLE xmldb_suiyue.gotcha_rewards (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(18) COMMENT 'name',
    `basic_item_name` VARCHAR(8) COMMENT 'basic_item_name',
    `basic_item_count_in_fever` VARCHAR(1) COMMENT 'basic_item_count_in_fever',
    `reward_items` VARCHAR(64) COMMENT 'reward_items'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'gotcha_rewards';

DROP TABLE IF EXISTS xmldb_suiyue.gotcha_rewards__reward_items__data;
CREATE TABLE xmldb_suiyue.gotcha_rewards__reward_items__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `item_name` VARCHAR(45) COMMENT 'item_name',
    `item_count` VARCHAR(3) COMMENT 'item_count',
    `prob` VARCHAR(4) COMMENT 'prob',
    `item_count_in_fever` VARCHAR(3) COMMENT 'item_count_in_fever',
    `prob_in_fever` VARCHAR(4) COMMENT 'prob_in_fever'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'gotcha_rewards__reward_items__data';

