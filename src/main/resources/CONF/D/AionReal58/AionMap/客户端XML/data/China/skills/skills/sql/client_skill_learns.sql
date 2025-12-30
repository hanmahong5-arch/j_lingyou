DROP TABLE IF EXISTS xmldb_suiyue.client_skill_learns;
CREATE TABLE xmldb_suiyue.client_skill_learns (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(4) COMMENT 'name',
    `race` VARCHAR(8) COMMENT 'race',
    `class` VARCHAR(13) COMMENT 'class',
    `pc_level` VARCHAR(2) COMMENT 'pc_level',
    `skill` VARCHAR(35) COMMENT 'skill',
    `skill_level` VARCHAR(1) COMMENT 'skill_level',
    `autolearn` VARCHAR(4) COMMENT 'autolearn',
    `ui_display` VARCHAR(1) COMMENT 'ui_display',
    `stigma_display` VARCHAR(1) COMMENT 'stigma_display'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_skill_learns';

