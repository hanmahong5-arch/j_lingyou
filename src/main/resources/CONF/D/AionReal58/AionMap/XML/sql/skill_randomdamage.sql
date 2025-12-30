DROP TABLE IF EXISTS xmldb_suiyue.skill_randomdamage;
CREATE TABLE xmldb_suiyue.skill_randomdamage (
    `range0` VARCHAR(255) PRIMARY KEY COMMENT 'range0',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `range1` VARCHAR(3) COMMENT 'range1',
    `range2` VARCHAR(3) COMMENT 'range2',
    `range3` VARCHAR(3) COMMENT 'range3',
    `range4` VARCHAR(3) COMMENT 'range4',
    `range5` VARCHAR(3) COMMENT 'range5',
    `range6` VARCHAR(3) COMMENT 'range6',
    `range7` VARCHAR(3) COMMENT 'range7',
    `range8` VARCHAR(3) COMMENT 'range8',
    `range9` VARCHAR(3) COMMENT 'range9',
    `range10` VARCHAR(3) COMMENT 'range10',
    `range11` VARCHAR(3) COMMENT 'range11',
    `range12` VARCHAR(3) COMMENT 'range12',
    `range13` VARCHAR(3) COMMENT 'range13',
    `range14` VARCHAR(3) COMMENT 'range14',
    `range15` VARCHAR(3) COMMENT 'range15',
    `range16` VARCHAR(3) COMMENT 'range16',
    `range17` VARCHAR(3) COMMENT 'range17',
    `range18` VARCHAR(3) COMMENT 'range18',
    `range19` VARCHAR(3) COMMENT 'range19',
    `_attr_random_type` VARCHAR(128) COMMENT 'random_type'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'skill_randomdamage';

