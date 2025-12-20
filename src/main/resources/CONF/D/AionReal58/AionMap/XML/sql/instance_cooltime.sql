DROP TABLE IF EXISTS xmldb_suiyue.instance_cooltime;
CREATE TABLE xmldb_suiyue.instance_cooltime (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(23) COMMENT 'name',
    `desc` VARCHAR(37) COMMENT 'desc',
    `indun_type` VARCHAR(11) COMMENT 'indun_type',
    `max_member_light` VARCHAR(3) COMMENT 'max_member_light',
    `max_member_dark` VARCHAR(3) COMMENT 'max_member_dark',
    `enter_min_level_light` VARCHAR(2) COMMENT 'enter_min_level_light',
    `enter_min_level_dark` VARCHAR(2) COMMENT 'enter_min_level_dark',
    `can_enter_mentor` VARCHAR(5) COMMENT 'can_enter_mentor',
    `coolt_tbl_id` VARCHAR(3) COMMENT 'coolt_tbl_id',
    `f2p_coolt_tbl_id` VARCHAR(4) COMMENT 'f2p_coolt_tbl_id',
    `coolt_sync_id` VARCHAR(3) COMMENT 'coolt_sync_id',
    `data` VARCHAR(64) COMMENT 'data',
    `exit_world_1` VARCHAR(13) COMMENT 'exit_world_1',
    `exit_alias_1` VARCHAR(30) COMMENT 'exit_alias_1',
    `exit_world_2` VARCHAR(13) COMMENT 'exit_world_2',
    `exit_alias_2` VARCHAR(30) COMMENT 'exit_alias_2',
    `bm_restrict_category` VARCHAR(1) COMMENT 'bm_restrict_category',
    `share_max_member` VARCHAR(1) COMMENT 'share_max_member',
    `enter_max_level_light` VARCHAR(2) COMMENT 'enter_max_level_light',
    `enter_max_level_dark` VARCHAR(2) COMMENT 'enter_max_level_dark',
    `ui_gauge_max` VARCHAR(3) COMMENT 'ui_gauge_max',
    `ext_condition_variable` VARCHAR(64) COMMENT 'ext_condition_variable',
    `using_warpoint` VARCHAR(1) COMMENT 'using_warpoint',
    `warpoint` VARCHAR(2) COMMENT 'warpoint',
    `bonus_time` VARCHAR(4) COMMENT 'bonus_time',
    `bonus_warpoint` VARCHAR(2) COMMENT 'bonus_warpoint',
    `pc_kill_warpoint` VARCHAR(2) COMMENT 'pc_kill_warpoint',
    `intrusion_warpoint` VARCHAR(2) COMMENT 'intrusion_warpoint'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'instance_cooltime';

DROP TABLE IF EXISTS xmldb_suiyue.instance_cooltime__data__data;
CREATE TABLE xmldb_suiyue.instance_cooltime__data__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `variable` VARCHAR(43) COMMENT 'variable'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'instance_cooltime__data__data';

DROP TABLE IF EXISTS xmldb_suiyue.instance_cooltime__ext_condition_variable__data;
CREATE TABLE xmldb_suiyue.instance_cooltime__ext_condition_variable__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `variable` VARCHAR(43) COMMENT 'variable'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'instance_cooltime__ext_condition_variable__data';

