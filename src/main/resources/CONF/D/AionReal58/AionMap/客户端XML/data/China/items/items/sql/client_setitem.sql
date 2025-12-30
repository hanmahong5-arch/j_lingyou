DROP TABLE IF EXISTS xmldb_suiyue.client_setitem;
CREATE TABLE xmldb_suiyue.client_setitem (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(32) COMMENT 'name',
    `desc` VARCHAR(36) COMMENT 'desc',
    `item1` VARCHAR(32) COMMENT 'item1',
    `item3` VARCHAR(33) COMMENT 'item3',
    `item4` VARCHAR(33) COMMENT 'item4',
    `item5` VARCHAR(36) COMMENT 'item5',
    `item6` VARCHAR(33) COMMENT 'item6',
    `item7` VARCHAR(34) COMMENT 'item7',
    `piece_bonus2` VARCHAR(104) COMMENT 'piece_bonus2',
    `piece_bonus3` VARCHAR(84) COMMENT 'piece_bonus3',
    `piece_bonus4` VARCHAR(87) COMMENT 'piece_bonus4',
    `piece_bonus5` VARCHAR(76) COMMENT 'piece_bonus5',
    `piece_bonus6` VARCHAR(89) COMMENT 'piece_bonus6',
    `fullset_bonus` VARCHAR(86) COMMENT 'fullset_bonus',
    `item2` VARCHAR(28) COMMENT 'item2'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_setitem';

