DROP TABLE IF EXISTS xmldb_suiyue.client_ultra_skills;
CREATE TABLE xmldb_suiyue.client_ultra_skills (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(9) COMMENT 'name',
    `ultra_skill` VARCHAR(37) COMMENT 'ultra_skill',
    `pet_name` VARCHAR(37) COMMENT 'pet_name',
    `order_skill` VARCHAR(33) COMMENT 'order_skill'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_ultra_skills';

