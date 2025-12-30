DROP TABLE IF EXISTS xmldb_suiyue.game_config;
CREATE TABLE xmldb_suiyue.game_config (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(31) COMMENT 'name',
    `value_int` VARCHAR(3) COMMENT 'value_int',
    `__comment__` VARCHAR(36) COMMENT '__comment__'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'game_config';

