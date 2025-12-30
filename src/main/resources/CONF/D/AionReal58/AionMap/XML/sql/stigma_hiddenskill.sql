DROP TABLE IF EXISTS xmldb_suiyue.stigma_hiddenskill;
CREATE TABLE xmldb_suiyue.stigma_hiddenskill (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(2) COMMENT 'name',
    `hidden_skill` VARCHAR(23) COMMENT 'hidden_skill',
    `class` VARCHAR(13) COMMENT 'class',
    `level` VARCHAR(2) COMMENT 'level',
    `condition_skill` VARCHAR(64) COMMENT 'condition_skill'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'stigma_hiddenskill';

DROP TABLE IF EXISTS xmldb_suiyue.stigma_hiddenskill__condition_skill__data;
CREATE TABLE xmldb_suiyue.stigma_hiddenskill__condition_skill__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `condition_skill_num` VARCHAR(1) COMMENT 'condition_skill_num',
    `condition_skill_group` VARCHAR(30) COMMENT 'condition_skill_group',
    `condition_skill_level` VARCHAR(1) COMMENT 'condition_skill_level',
    `only_enchant` VARCHAR(4) COMMENT 'only_enchant'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'stigma_hiddenskill__condition_skill__data';

