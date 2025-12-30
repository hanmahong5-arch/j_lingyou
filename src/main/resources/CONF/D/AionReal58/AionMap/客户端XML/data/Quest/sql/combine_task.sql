DROP TABLE IF EXISTS xmldb_suiyue.combine_task;
CREATE TABLE xmldb_suiyue.combine_task (
    `dev_name` VARCHAR(255) PRIMARY KEY COMMENT 'dev_name',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `task_npc` VARCHAR(17) COMMENT 'task_npc',
    `combineskill` VARCHAR(11) COMMENT 'combineskill',
    `combine_skillpoint` VARCHAR(3) COMMENT 'combine_skillpoint',
    `recipe_name` VARCHAR(11) COMMENT 'recipe_name',
    `product` VARCHAR(18) COMMENT 'product',
    `give_component1` VARCHAR(25) COMMENT 'give_component1',
    `give_component2` VARCHAR(25) COMMENT 'give_component2',
    `_attr_id` VARCHAR(128) COMMENT 'id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'combine_task';

