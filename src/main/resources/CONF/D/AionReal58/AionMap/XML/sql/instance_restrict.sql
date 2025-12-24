DROP TABLE IF EXISTS xmldb_suiyue.instance_restrict;
CREATE TABLE xmldb_suiyue.instance_restrict (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(33) COMMENT 'name',
    `worldname` VARCHAR(21) COMMENT 'worldname',
    `item1_name` VARCHAR(28) COMMENT 'item1_name',
    `item1_cnt` VARCHAR(1) COMMENT 'item1_cnt',
    `item1_remove` VARCHAR(1) COMMENT 'item1_remove',
    `item2_name` VARCHAR(11) COMMENT 'item2_name',
    `item2_cnt` VARCHAR(1) COMMENT 'item2_cnt',
    `item2_remove` VARCHAR(1) COMMENT 'item2_remove',
    `item3_name` VARCHAR(11) COMMENT 'item3_name',
    `item3_cnt` VARCHAR(1) COMMENT 'item3_cnt',
    `item3_remove` VARCHAR(1) COMMENT 'item3_remove',
    `item4_name` VARCHAR(11) COMMENT 'item4_name',
    `item4_cnt` VARCHAR(1) COMMENT 'item4_cnt',
    `item4_remove` VARCHAR(1) COMMENT 'item4_remove',
    `finished_quest_cond1` VARCHAR(4) COMMENT 'finished_quest_cond1',
    `finished_quest_cond2` VARCHAR(4) COMMENT 'finished_quest_cond2'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'instance_restrict';

