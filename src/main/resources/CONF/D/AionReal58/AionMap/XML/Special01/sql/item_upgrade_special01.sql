DROP TABLE IF EXISTS xmldb_suiyue.item_upgrade_special01;
CREATE TABLE xmldb_suiyue.item_upgrade_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(37) COMMENT 'name',
    `upgrade_list` VARCHAR(64) COMMENT 'upgrade_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_upgrade_special01';

DROP TABLE IF EXISTS xmldb_suiyue.item_upgrade_special01__upgrade_list__data;
CREATE TABLE xmldb_suiyue.item_upgrade_special01__upgrade_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `upgrade_item` VARCHAR(40) COMMENT 'upgrade_item',
    `upgrade_npc` VARCHAR(103) COMMENT 'upgrade_npc',
    `check_enchant_count` VARCHAR(2) COMMENT 'check_enchant_count',
    `check_authorize_count` VARCHAR(2) COMMENT 'check_authorize_count',
    `sub_material_item1` VARCHAR(33) COMMENT 'sub_material_item1',
    `sub_material_item_count1` VARCHAR(4) COMMENT 'sub_material_item_count1',
    `sub_material_item2` VARCHAR(24) COMMENT 'sub_material_item2',
    `sub_material_item_count2` VARCHAR(4) COMMENT 'sub_material_item_count2',
    `sub_material_item3` VARCHAR(13) COMMENT 'sub_material_item3',
    `sub_material_item_count3` VARCHAR(4) COMMENT 'sub_material_item_count3',
    `sub_material_item4` VARCHAR(13) COMMENT 'sub_material_item4',
    `sub_material_item_count4` VARCHAR(3) COMMENT 'sub_material_item_count4',
    `sub_material_item5` VARCHAR(7) COMMENT 'sub_material_item5',
    `sub_material_item_count5` VARCHAR(1) COMMENT 'sub_material_item_count5',
    `need_qina` VARCHAR(6) COMMENT 'need_qina',
    `need_abyss_point` VARCHAR(6) COMMENT 'need_abyss_point',
    `upgrade_enchant_type` VARCHAR(1) COMMENT 'upgrade_enchant_type',
    `upgrade_enchant_count` VARCHAR(2) COMMENT 'upgrade_enchant_count',
    `upgrade_authorize_type` VARCHAR(1) COMMENT 'upgrade_authorize_type',
    `upgrade_authorize_count` VARCHAR(2) COMMENT 'upgrade_authorize_count'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'item_upgrade_special01__upgrade_list__data';

