DROP TABLE IF EXISTS xmldb_suiyue.toypet_merchant;
CREATE TABLE xmldb_suiyue.toypet_merchant (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(14) COMMENT 'name',
    `rate_price` VARCHAR(2) COMMENT 'rate_price'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'toypet_merchant';

