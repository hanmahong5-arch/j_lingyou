DROP TABLE IF EXISTS xmldb_suiyue.toypets;
CREATE TABLE xmldb_suiyue.toypets (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(37) COMMENT 'name',
    `desc` VARCHAR(41) COMMENT 'desc',
    `func_type1` VARCHAR(9) COMMENT 'func_type1',
    `func_type_name1` VARCHAR(33) COMMENT 'func_type_name1',
    `func_type2` VARCHAR(9) COMMENT 'func_type2',
    `func_type_name2` VARCHAR(35) COMMENT 'func_type_name2',
    `enemy_alarm` VARCHAR(1) COMMENT 'enemy_alarm',
    `cam_pos` VARCHAR(11) COMMENT 'cam_pos',
    `target_sound` VARCHAR(25) COMMENT 'target_sound',
    `pet_condition_reward` VARCHAR(35) COMMENT 'pet_condition_reward',
    `dir` VARCHAR(23) COMMENT 'dir',
    `mesh` VARCHAR(30) COMMENT 'mesh',
    `customize_color` VARCHAR(1) COMMENT 'customize_color',
    `color` VARCHAR(11) COMMENT 'color',
    `wing` VARCHAR(18) COMMENT 'wing',
    `bag` VARCHAR(35) COMMENT 'bag',
    `customize_attach` VARCHAR(1) COMMENT 'customize_attach',
    `attach_mesh_size` VARCHAR(3) COMMENT 'attach_mesh_size',
    `combat_reaction` VARCHAR(8) COMMENT 'combat_reaction',
    `ui_colors` VARCHAR(64) COMMENT 'ui_colors',
    `iserect` VARCHAR(1) COMMENT 'iserect',
    `bound_radius` VARCHAR(64) COMMENT 'bound_radius',
    `scale` VARCHAR(3) COMMENT 'scale',
    `altitude` VARCHAR(8) COMMENT 'altitude',
    `art_org_move_speed_normal_walk` VARCHAR(8) COMMENT 'art_org_move_speed_normal_walk',
    `art_org_speed_normal_run` VARCHAR(8) COMMENT 'art_org_speed_normal_run'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'toypets';

DROP TABLE IF EXISTS xmldb_suiyue.toypets__ui_colors__data;
CREATE TABLE xmldb_suiyue.toypets__ui_colors__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `ui_color` VARCHAR(9) COMMENT 'ui_color'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'toypets__ui_colors__data';

DROP TABLE IF EXISTS xmldb_suiyue.toypets__bound_radius;
CREATE TABLE xmldb_suiyue.toypets__bound_radius (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `front` VARCHAR(8) COMMENT 'front',
    `side` VARCHAR(8) COMMENT 'side',
    `upper` VARCHAR(8) COMMENT 'upper'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'toypets__bound_radius';

