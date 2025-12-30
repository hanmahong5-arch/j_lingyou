DROP TABLE IF EXISTS xmldb_suiyue.airline_special01;
CREATE TABLE xmldb_suiyue.airline_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(29) COMMENT 'name',
    `type` VARCHAR(5) COMMENT 'type',
    `need_confirm` VARCHAR(1) COMMENT 'need_confirm',
    `airline_world` VARCHAR(19) COMMENT 'airline_world',
    `cur_airport_name` VARCHAR(29) COMMENT 'cur_airport_name',
    `airline_list` VARCHAR(64) COMMENT 'airline_list'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'airline_special01';

DROP TABLE IF EXISTS xmldb_suiyue.airline_special01__airline_list__data;
CREATE TABLE xmldb_suiyue.airline_special01__airline_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `airport_name` VARCHAR(29) COMMENT 'airport_name',
    `fee` VARCHAR(5) COMMENT 'fee',
    `pvpon_fee` VARCHAR(5) COMMENT 'pvpon_fee',
    `required_quest` VARCHAR(4) COMMENT 'required_quest',
    `teleport_type` VARCHAR(1) COMMENT 'teleport_type',
    `flight_path_group_id` VARCHAR(3) COMMENT 'flight_path_group_id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'airline_special01__airline_list__data';

