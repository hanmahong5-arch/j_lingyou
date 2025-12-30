DROP TABLE IF EXISTS xmldb_suiyue.skill_charge;
CREATE TABLE xmldb_suiyue.skill_charge (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(29) COMMENT 'name',
    `min_charge_time` VARCHAR(3) COMMENT 'min_charge_time',
    `move_charge` VARCHAR(1) COMMENT 'move_charge',
    `charge_time_bonus_type` VARCHAR(8) COMMENT 'charge_time_bonus_type',
    `skill_name1` VARCHAR(33) COMMENT 'skill_name1',
    `charge_time1` VARCHAR(4) COMMENT 'charge_time1',
    `skill_name2` VARCHAR(33) COMMENT 'skill_name2',
    `charge_time2` VARCHAR(4) COMMENT 'charge_time2',
    `skill_name3` VARCHAR(29) COMMENT 'skill_name3',
    `charge_time3` VARCHAR(4) COMMENT 'charge_time3'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'skill_charge';

