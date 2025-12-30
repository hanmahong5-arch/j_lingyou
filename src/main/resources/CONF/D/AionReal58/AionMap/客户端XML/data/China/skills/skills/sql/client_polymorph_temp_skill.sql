DROP TABLE IF EXISTS xmldb_suiyue.client_polymorph_temp_skill;
CREATE TABLE xmldb_suiyue.client_polymorph_temp_skill (
    `desc` VARCHAR(255) PRIMARY KEY COMMENT 'desc',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `temp_skill_name1` VARCHAR(43) COMMENT 'temp_skill_name1',
    `temp_skill_level1` VARCHAR(2) COMMENT 'temp_skill_level1',
    `temp_skill_name2` VARCHAR(43) COMMENT 'temp_skill_name2',
    `temp_skill_level2` VARCHAR(2) COMMENT 'temp_skill_level2',
    `temp_skill_name3` VARCHAR(39) COMMENT 'temp_skill_name3',
    `temp_skill_level3` VARCHAR(2) COMMENT 'temp_skill_level3',
    `temp_skill_name4` VARCHAR(35) COMMENT 'temp_skill_name4',
    `temp_skill_level4` VARCHAR(2) COMMENT 'temp_skill_level4',
    `temp_skill_name5` VARCHAR(35) COMMENT 'temp_skill_name5',
    `temp_skill_level5` VARCHAR(2) COMMENT 'temp_skill_level5',
    `item_name1` VARCHAR(25) COMMENT 'item_name1',
    `_attr_ID` VARCHAR(128) COMMENT 'ID'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_polymorph_temp_skill';

