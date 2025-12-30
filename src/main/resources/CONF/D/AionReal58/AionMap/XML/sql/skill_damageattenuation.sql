DROP TABLE IF EXISTS xmldb_suiyue.skill_damageattenuation;
CREATE TABLE xmldb_suiyue.skill_damageattenuation (
    `attenuation_value_a` VARCHAR(255) PRIMARY KEY COMMENT 'attenuation_value_a',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `attenuation_value_b` VARCHAR(3) COMMENT 'attenuation_value_b',
    `attenuation_value_m` VARCHAR(3) COMMENT 'attenuation_value_m',
    `_attr_attenuation_type` VARCHAR(128) COMMENT 'attenuation_type'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'skill_damageattenuation';

