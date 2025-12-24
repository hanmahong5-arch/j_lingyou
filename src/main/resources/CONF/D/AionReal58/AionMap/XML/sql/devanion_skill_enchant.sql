DROP TABLE IF EXISTS xmldb_suiyue.devanion_skill_enchant;
CREATE TABLE xmldb_suiyue.devanion_skill_enchant (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(32) COMMENT 'name',
    `material_grade` VARCHAR(8) COMMENT 'material_grade',
    `material_special_grade` VARCHAR(8) COMMENT 'material_special_grade',
    `reinforce_level_list` VARCHAR(64) COMMENT 'reinforce_level_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'devanion_skill_enchant';

DROP TABLE IF EXISTS xmldb_suiyue.devanion_skill_enchant__reinforce_level_list__data;
CREATE TABLE xmldb_suiyue.devanion_skill_enchant__reinforce_level_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `reinforce_level` VARCHAR(2) COMMENT 'reinforce_level',
    `reinforce_rate` VARCHAR(5) COMMENT 'reinforce_rate',
    `reinforce_rate_bonus` VARCHAR(4) COMMENT 'reinforce_rate_bonus',
    `reinforce_penalty_type` VARCHAR(2) COMMENT 'reinforce_penalty_type',
    `reinforce_fee` VARCHAR(6) COMMENT 'reinforce_fee'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'devanion_skill_enchant__reinforce_level_list__data';

