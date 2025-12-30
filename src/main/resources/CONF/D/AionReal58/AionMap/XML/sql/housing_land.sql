DROP TABLE IF EXISTS xmldb_suiyue.housing_land;
CREATE TABLE xmldb_suiyue.housing_land (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(12) COMMENT 'name',
    `desc` VARCHAR(33) COMMENT 'desc',
    `tribe` VARCHAR(5) COMMENT 'tribe',
    `type` VARCHAR(14) COMMENT 'type',
    `size` VARCHAR(1) COMMENT 'size',
    `building_offset` VARCHAR(64) COMMENT 'building_offset',
    `door_offset` VARCHAR(64) COMMENT 'door_offset',
    `default_building_name` VARCHAR(16) COMMENT 'default_building_name',
    `default_manager_npc` VARCHAR(18) COMMENT 'default_manager_npc',
    `default_sign_nosale` VARCHAR(24) COMMENT 'default_sign_nosale',
    `default_sign_sale` VARCHAR(22) COMMENT 'default_sign_sale',
    `default_sign_waiting` VARCHAR(25) COMMENT 'default_sign_waiting',
    `default_sign_home` VARCHAR(22) COMMENT 'default_sign_home',
    `default_teleport_obj` VARCHAR(23) COMMENT 'default_teleport_obj',
    `sale_price_gold` VARCHAR(10) COMMENT 'sale_price_gold',
    `sale_price_housingpoint` VARCHAR(1) COMMENT 'sale_price_housingpoint',
    `fee` VARCHAR(8) COMMENT 'fee',
    `sale_level` VARCHAR(2) COMMENT 'sale_level',
    `interior` VARCHAR(2) COMMENT 'interior',
    `exterior` VARCHAR(2) COMMENT 'exterior',
    `building_list` VARCHAR(64) COMMENT 'building_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'housing_land';

DROP TABLE IF EXISTS xmldb_suiyue.housing_land__building_offset;
CREATE TABLE xmldb_suiyue.housing_land__building_offset (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `center_x` VARCHAR(8) COMMENT 'center_x',
    `center_y` VARCHAR(8) COMMENT 'center_y',
    `center_z` VARCHAR(8) COMMENT 'center_z'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'housing_land__building_offset';

DROP TABLE IF EXISTS xmldb_suiyue.housing_land__door_offset;
CREATE TABLE xmldb_suiyue.housing_land__door_offset (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `door_x` VARCHAR(9) COMMENT 'door_x',
    `door_y` VARCHAR(10) COMMENT 'door_y'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'housing_land__door_offset';

DROP TABLE IF EXISTS xmldb_suiyue.housing_land__building_list__data;
CREATE TABLE xmldb_suiyue.housing_land__building_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `building` VARCHAR(16) COMMENT 'building'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'housing_land__building_list__data';

