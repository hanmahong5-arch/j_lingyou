DROP TABLE IF EXISTS xmldb_suiyue.instance_bonusattr;
CREATE TABLE xmldb_suiyue.instance_bonusattr (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(29) COMMENT 'name',
    `penalty_attr1` VARCHAR(20) COMMENT 'penalty_attr1',
    `penalty_attr2` VARCHAR(23) COMMENT 'penalty_attr2',
    `penalty_attr3` VARCHAR(19) COMMENT 'penalty_attr3',
    `penalty_attr4` VARCHAR(19) COMMENT 'penalty_attr4',
    `penalty_attr5` VARCHAR(10) COMMENT 'penalty_attr5'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'instance_bonusattr';

