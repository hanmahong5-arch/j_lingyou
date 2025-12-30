DROP TABLE IF EXISTS xmldb_suiyue.skill_signetdata;
CREATE TABLE xmldb_suiyue.skill_signetdata (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(2) COMMENT 'name',
    `signet_type` VARCHAR(1) COMMENT 'signet_type',
    `signet_level` VARCHAR(1) COMMENT 'signet_level',
    `basic_carve_prob` VARCHAR(3) COMMENT 'basic_carve_prob',
    `basic_burst_damage` VARCHAR(3) COMMENT 'basic_burst_damage',
    `burst_add_effect_prob` VARCHAR(3) COMMENT 'burst_add_effect_prob',
    `signet_skill` VARCHAR(9) COMMENT 'signet_skill'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'skill_signetdata';

