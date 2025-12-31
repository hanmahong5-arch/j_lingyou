DROP TABLE IF EXISTS xmldb_suiyue.abyss_race_bonuses;
CREATE TABLE xmldb_suiyue.abyss_race_bonuses (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(11) COMMENT 'name',
    `desc_name` VARCHAR(22) COMMENT 'desc_name',
    `desc_long` VARCHAR(27) COMMENT 'desc_long',
    `condition` VARCHAR(10) COMMENT 'condition',
    `race` VARCHAR(5) COMMENT 'race',
    `bonus_attrs` VARCHAR(64) COMMENT 'bonus_attrs'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_race_bonuses';

DROP TABLE IF EXISTS xmldb_suiyue.abyss_race_bonuses__bonus_attrs__data;
CREATE TABLE xmldb_suiyue.abyss_race_bonuses__bonus_attrs__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `bonus_attr` VARCHAR(18) COMMENT 'bonus_attr'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_race_bonuses__bonus_attrs__data';

