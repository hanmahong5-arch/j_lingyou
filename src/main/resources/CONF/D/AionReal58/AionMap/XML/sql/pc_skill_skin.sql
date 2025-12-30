DROP TABLE IF EXISTS xmldb_suiyue.pc_skill_skin;
CREATE TABLE xmldb_suiyue.pc_skill_skin (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(33) COMMENT 'name',
    `desc` VARCHAR(37) COMMENT 'desc',
    `desc_long` VARCHAR(42) COMMENT 'desc_long',
    `skill_group_name` VARCHAR(23) COMMENT 'skill_group_name',
    `next_charge_skill_skin_name` VARCHAR(28) COMMENT 'next_charge_skill_skin_name'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'pc_skill_skin';

