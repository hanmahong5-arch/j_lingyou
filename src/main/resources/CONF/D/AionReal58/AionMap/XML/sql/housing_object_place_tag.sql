DROP TABLE IF EXISTS xmldb_suiyue.housing_object_place_tag;
CREATE TABLE xmldb_suiyue.housing_object_place_tag (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(17) COMMENT 'name',
    `personal_types_max` VARCHAR(1) COMMENT 'personal_types_max',
    `personal_typea_max` VARCHAR(1) COMMENT 'personal_typea_max',
    `personal_typeb_max` VARCHAR(1) COMMENT 'personal_typeb_max',
    `personal_typec_max` VARCHAR(1) COMMENT 'personal_typec_max',
    `personal_typed_max` VARCHAR(1) COMMENT 'personal_typed_max',
    `trial_personal_types_max` VARCHAR(1) COMMENT 'trial_personal_types_max',
    `trial_personal_typea_max` VARCHAR(1) COMMENT 'trial_personal_typea_max',
    `trial_personal_typeb_max` VARCHAR(1) COMMENT 'trial_personal_typeb_max',
    `trial_personal_typec_max` VARCHAR(1) COMMENT 'trial_personal_typec_max',
    `trial_personal_typed_max` VARCHAR(1) COMMENT 'trial_personal_typed_max'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'housing_object_place_tag';

