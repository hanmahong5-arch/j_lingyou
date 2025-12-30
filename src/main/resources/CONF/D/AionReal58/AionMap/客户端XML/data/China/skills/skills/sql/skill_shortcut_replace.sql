DROP TABLE IF EXISTS xmldb_suiyue.skill_shortcut_replace;
CREATE TABLE xmldb_suiyue.skill_shortcut_replace (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(35) COMMENT 'name',
    `replace_name` VARCHAR(35) COMMENT 'replace_name'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'skill_shortcut_replace';

