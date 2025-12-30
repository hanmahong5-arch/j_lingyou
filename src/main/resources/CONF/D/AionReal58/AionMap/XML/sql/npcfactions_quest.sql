DROP TABLE IF EXISTS xmldb_suiyue.npcfactions_quest;
CREATE TABLE xmldb_suiyue.npcfactions_quest (
    `dev_name` VARCHAR(255) PRIMARY KEY COMMENT 'dev_name',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `npcfaction_name` VARCHAR(16) COMMENT 'npcfaction_name',
    `mon` VARCHAR(1) COMMENT 'mon',
    `tue` VARCHAR(1) COMMENT 'tue',
    `wed` VARCHAR(1) COMMENT 'wed',
    `thu` VARCHAR(1) COMMENT 'thu',
    `fri` VARCHAR(1) COMMENT 'fri',
    `sat` VARCHAR(1) COMMENT 'sat',
    `sun` VARCHAR(1) COMMENT 'sun',
    `_attr_quest_id` VARCHAR(128) COMMENT 'quest_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'npcfactions_quest';

