DROP TABLE IF EXISTS xmldb_suiyue.toypet_doping;
CREATE TABLE xmldb_suiyue.toypet_doping (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(22) COMMENT 'name',
    `use_doping_drink` VARCHAR(5) COMMENT 'use_doping_drink',
    `use_doping_food` VARCHAR(5) COMMENT 'use_doping_food',
    `use_doping_scroll` VARCHAR(1) COMMENT 'use_doping_scroll'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'toypet_doping';

