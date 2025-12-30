DROP TABLE IF EXISTS xmldb_suiyue.abyss_special01;
CREATE TABLE xmldb_suiyue.abyss_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` TEXT COMMENT 'name',
    `desc` TEXT COMMENT 'desc',
    `world` TEXT COMMENT 'world',
    `abyss_type` TEXT COMMENT 'abyss_type',
    `castle_desc` TEXT COMMENT 'castle_desc',
    `value` TEXT COMMENT 'value',
    `occupy_count` TEXT COMMENT 'occupy_count',
    `siege_skill` TEXT COMMENT 'siege_skill',
    `siege_skill_level` TEXT COMMENT 'siege_skill_level',
    `siege_skill_fire_type` TEXT COMMENT 'siege_skill_fire_type',
    `abyss_dome_skill` TEXT COMMENT 'abyss_dome_skill',
    `max_occupy_point` TEXT COMMENT 'max_occupy_point',
    `weak_race_buff_set` TEXT COMMENT 'weak_race_buff_set',
    `door_repair_fee_type` TEXT COMMENT 'door_repair_fee_type',
    `door_repair_fee` TEXT COMMENT 'door_repair_fee',
    `door_repair_hp` TEXT COMMENT 'door_repair_hp',
    `door_repair_cooltime` TEXT COMMENT 'door_repair_cooltime',
    `reward_receive_percent` TEXT COMMENT 'reward_receive_percent',
    `guild_reward_rate` TEXT COMMENT 'guild_reward_rate',
    `contributor_reward` TEXT COMMENT 'contributor_reward',
    `defender_reward` TEXT COMMENT 'defender_reward',
    `siege_duration` TEXT COMMENT 'siege_duration',
    `defend_check_num` TEXT COMMENT 'defend_check_num',
    `pvp_siege_contribute_rate` TEXT COMMENT 'pvp_siege_contribute_rate',
    `pvp_defend_contribute_rate` TEXT COMMENT 'pvp_defend_contribute_rate',
    `reward_dec_rate` TEXT COMMENT 'reward_dec_rate',
    `reward_dec_interval` TEXT COMMENT 'reward_dec_interval',
    `reward_mail_interval` TEXT COMMENT 'reward_mail_interval',
    `occupy_bonus_max` TEXT COMMENT 'occupy_bonus_max',
    `main_abyss_id` TEXT COMMENT 'main_abyss_id',
    `occupy_count_reward` TEXT COMMENT 'occupy_count_reward',
    `occupy_reward_condition_l` TEXT COMMENT 'occupy_reward_condition_l',
    `occupy_reward_condition_d` TEXT COMMENT 'occupy_reward_condition_d',
    `protect_reward` TEXT COMMENT 'protect_reward',
    `luna_reward` TEXT COMMENT 'luna_reward',
    `siege_reward` TEXT COMMENT 'siege_reward',
    `siege_leader_reward` TEXT COMMENT 'siege_leader_reward',
    `use_groupfunc` TEXT COMMENT 'use_groupfunc',
    `group_min_lv` TEXT COMMENT 'group_min_lv',
    `group_build_min_pc` TEXT COMMENT 'group_build_min_pc',
    `use_leaderskill_set` TEXT COMMENT 'use_leaderskill_set',
    `leaderskill_set_li` TEXT COMMENT 'leaderskill_set_li',
    `leaderskill_set_da` TEXT COMMENT 'leaderskill_set_da',
    `takeby_score` TEXT COMMENT 'takeby_score',
    `dragon_score_set` TEXT COMMENT 'dragon_score_set',
    `siege_skill_attacker_fail` TEXT COMMENT 'siege_skill_attacker_fail',
    `siege_skill_attacker_fail_level` TEXT COMMENT 'siege_skill_attacker_fail_level',
    `siege_skill_attacker_fail_type` TEXT COMMENT 'siege_skill_attacker_fail_type',
    `siege_skill_ex1` TEXT COMMENT 'siege_skill_ex1',
    `siege_skill_level_ex1` TEXT COMMENT 'siege_skill_level_ex1',
    `siege_skill_fire_type_ex1` TEXT COMMENT 'siege_skill_fire_type_ex1',
    `siege_skill_defender_success` TEXT COMMENT 'siege_skill_defender_success',
    `siege_skill_defender_success_level` TEXT COMMENT 'siege_skill_defender_success_level',
    `siege_skill_defender_success_type` TEXT COMMENT 'siege_skill_defender_success_type',
    `reward_receive_max` TEXT COMMENT 'reward_receive_max'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_special01';

DROP TABLE IF EXISTS xmldb_suiyue.abyss_special01__abyss_dome_skill__data;
CREATE TABLE xmldb_suiyue.abyss_special01__abyss_dome_skill__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `dome_skill_index` VARCHAR(1) COMMENT 'dome_skill_index',
    `dome_skill_default` VARCHAR(35) COMMENT 'dome_skill_default',
    `dome_skill_upgrade_max` VARCHAR(1) COMMENT 'dome_skill_upgrade_max',
    `dome_available_skills` VARCHAR(64) COMMENT 'dome_available_skills',
    `not_use` VARCHAR(1) COMMENT 'not_use'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_special01__abyss_dome_skill__data';

DROP TABLE IF EXISTS xmldb_suiyue.abyss_special01__abyss_dome_skill__data__d_a_s__data;
CREATE TABLE xmldb_suiyue.abyss_special01__abyss_dome_skill__data__d_a_s__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `dome_skill_name` VARCHAR(35) COMMENT 'dome_skill_name',
    `dome_skill_change_fee` VARCHAR(4) COMMENT 'dome_skill_change_fee'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_special01__abyss_dome_skill__data__d_a_s__data';

DROP TABLE IF EXISTS xmldb_suiyue.abyss_special01__weak_race_buff_set__data;
CREATE TABLE xmldb_suiyue.abyss_special01__weak_race_buff_set__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `weak_race_buff_level` VARCHAR(1) COMMENT 'weak_race_buff_level',
    `weak_race_buff_condition` VARCHAR(2) COMMENT 'weak_race_buff_condition',
    `weak_race_buff_light` VARCHAR(26) COMMENT 'weak_race_buff_light',
    `weak_race_buff_dark` VARCHAR(26) COMMENT 'weak_race_buff_dark'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_special01__weak_race_buff_set__data';

DROP TABLE IF EXISTS xmldb_suiyue.abyss_special01__contributor_reward__data;
CREATE TABLE xmldb_suiyue.abyss_special01__contributor_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `contributor_rank` VARCHAR(1) COMMENT 'contributor_rank',
    `contributor_num` VARCHAR(3) COMMENT 'contributor_num',
    `reward_rate` VARCHAR(2) COMMENT 'reward_rate',
    `trial_reward_item` VARCHAR(20) COMMENT 'trial_reward_item',
    `trial_reward_item_count` VARCHAR(2) COMMENT 'trial_reward_item_count',
    `contributor_name` VARCHAR(23) COMMENT 'contributor_name',
    `reward_item` VARCHAR(20) COMMENT 'reward_item',
    `reward_item_count` VARCHAR(2) COMMENT 'reward_item_count',
    `reward_item_add` VARCHAR(12) COMMENT 'reward_item_add',
    `reward_item_count_add` VARCHAR(1) COMMENT 'reward_item_count_add',
    `reward_item_timer` VARCHAR(6) COMMENT 'reward_item_timer'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_special01__contributor_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.abyss_special01__defender_reward__data;
CREATE TABLE xmldb_suiyue.abyss_special01__defender_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `defender_rank` VARCHAR(1) COMMENT 'defender_rank',
    `defender_num` VARCHAR(3) COMMENT 'defender_num',
    `defender_reward_rate` VARCHAR(2) COMMENT 'defender_reward_rate',
    `trial_defender_reward_item` VARCHAR(20) COMMENT 'trial_defender_reward_item',
    `trial_defender_reward_item_count` VARCHAR(2) COMMENT 'trial_defender_reward_item_count',
    `defender_name` VARCHAR(23) COMMENT 'defender_name',
    `defender_reward_item` VARCHAR(20) COMMENT 'defender_reward_item',
    `defender_reward_item_count` VARCHAR(2) COMMENT 'defender_reward_item_count',
    `defender_reward_item_add` VARCHAR(12) COMMENT 'defender_reward_item_add',
    `defender_reward_item_count_add` VARCHAR(1) COMMENT 'defender_reward_item_count_add',
    `defender_reward_item_timer` VARCHAR(6) COMMENT 'defender_reward_item_timer'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_special01__defender_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.abyss_special01__protect_reward__data;
CREATE TABLE xmldb_suiyue.abyss_special01__protect_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `protect_item` VARCHAR(8) COMMENT 'protect_item',
    `protect_item_count` VARCHAR(3) COMMENT 'protect_item_count'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_special01__protect_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.abyss_special01__luna_reward__data;
CREATE TABLE xmldb_suiyue.abyss_special01__luna_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `luna_reward_name` VARCHAR(8) COMMENT 'luna_reward_name',
    `luna_reward_count` VARCHAR(2) COMMENT 'luna_reward_count'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_special01__luna_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.abyss_special01__siege_reward__data;
CREATE TABLE xmldb_suiyue.abyss_special01__siege_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `rank_name` VARCHAR(23) COMMENT 'rank_name',
    `rank_result_type` VARCHAR(1) COMMENT 'rank_result_type',
    `contribute_point_to_reward` VARCHAR(1) COMMENT 'contribute_point_to_reward',
    `new_reward_item` VARCHAR(22) COMMENT 'new_reward_item',
    `new_reward_item_count` VARCHAR(3) COMMENT 'new_reward_item_count',
    `new_reward_item_add` VARCHAR(22) COMMENT 'new_reward_item_add',
    `new_reward_item_count_add` VARCHAR(3) COMMENT 'new_reward_item_count_add',
    `rank_point_min` VARCHAR(4) COMMENT 'rank_point_min',
    `new_reward_gp` VARCHAR(3) COMMENT 'new_reward_gp'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_special01__siege_reward__data';

DROP TABLE IF EXISTS xmldb_suiyue.abyss_special01__siege_leader_reward__data;
CREATE TABLE xmldb_suiyue.abyss_special01__siege_leader_reward__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `leader_reward_name` VARCHAR(25) COMMENT 'leader_reward_name',
    `leader_type` VARCHAR(1) COMMENT 'leader_type',
    `leader_reward_item` VARCHAR(34) COMMENT 'leader_reward_item',
    `leader_reward_item_count` VARCHAR(1) COMMENT 'leader_reward_item_count'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_special01__siege_leader_reward__data';

