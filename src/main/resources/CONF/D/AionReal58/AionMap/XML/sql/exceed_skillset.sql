DROP TABLE IF EXISTS xmldb_suiyue.exceed_skillset;
CREATE TABLE xmldb_suiyue.exceed_skillset (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(29) COMMENT 'name',
    `enchant_skill_01` VARCHAR(27) COMMENT 'enchant_skill_01',
    `enchant_skill_02` VARCHAR(27) COMMENT 'enchant_skill_02',
    `enchant_skill_03` VARCHAR(27) COMMENT 'enchant_skill_03'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'exceed_skillset';

