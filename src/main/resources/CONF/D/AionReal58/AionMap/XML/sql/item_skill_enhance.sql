DROP TABLE IF EXISTS xmldb_suiyue.item_skill_enhance;
CREATE TABLE xmldb_suiyue.item_skill_enhance (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(49) COMMENT 'name',
    `enchant_prob_list` VARCHAR(64) COMMENT 'enchant_prob_list',
    `enchant_skill_list` VARCHAR(64) COMMENT 'enchant_skill_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_skill_enhance';

DROP TABLE IF EXISTS xmldb_suiyue.item_skill_enhance__enchant_prob_list__data;
CREATE TABLE xmldb_suiyue.item_skill_enhance__enchant_prob_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `enchant_prob` VARCHAR(4) COMMENT 'enchant_prob'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_skill_enhance__enchant_prob_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.item_skill_enhance__enchant_skill_list__data;
CREATE TABLE xmldb_suiyue.item_skill_enhance__enchant_skill_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `skill_group_name` VARCHAR(24) COMMENT 'skill_group_name',
    `skill_prob` VARCHAR(4) COMMENT 'skill_prob'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_skill_enhance__enchant_skill_list__data';

