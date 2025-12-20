DROP TABLE IF EXISTS xmldb_suiyue.quest_random_rewards;
CREATE TABLE xmldb_suiyue.quest_random_rewards (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(35) COMMENT 'name',
    `__comment__` VARCHAR(32) COMMENT '__comment__',
    `items` VARCHAR(64) COMMENT 'items'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest_random_rewards';

DROP TABLE IF EXISTS xmldb_suiyue.quest_random_rewards__items__data;
CREATE TABLE xmldb_suiyue.quest_random_rewards__items__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `item` VARCHAR(51) COMMENT 'item',
    `item_count` VARCHAR(3) COMMENT 'item_count',
    `prob` VARCHAR(7) COMMENT 'prob'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'quest_random_rewards__items__data';

