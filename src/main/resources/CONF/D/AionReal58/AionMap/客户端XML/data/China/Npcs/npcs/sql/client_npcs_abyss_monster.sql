DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_abyss_monster;
CREATE TABLE xmldb_suiyue.client_npcs_abyss_monster (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` TEXT COMMENT 'name',
    `desc` TEXT COMMENT 'desc',
    `dir` TEXT COMMENT 'dir',
    `mesh` TEXT COMMENT 'mesh',
    `ui_type` TEXT COMMENT 'ui_type',
    `cursor_type` TEXT COMMENT 'cursor_type',
    `erect` TEXT COMMENT 'erect',
    `bound_radius` TEXT COMMENT 'bound_radius',
    `scale` TEXT COMMENT 'scale',
    `weapon_scale` TEXT COMMENT 'weapon_scale',
    `altitude` TEXT COMMENT 'altitude',
    `stare_angle` TEXT COMMENT 'stare_angle',
    `stare_distance` TEXT COMMENT 'stare_distance',
    `move_speed_normal_walk` TEXT COMMENT 'move_speed_normal_walk',
    `art_org_move_speed_normal_walk` TEXT COMMENT 'art_org_move_speed_normal_walk',
    `move_speed_normal_run` TEXT COMMENT 'move_speed_normal_run',
    `move_speed_combat_run` TEXT COMMENT 'move_speed_combat_run',
    `art_org_speed_combat_run` TEXT COMMENT 'art_org_speed_combat_run',
    `pushed_range` TEXT COMMENT 'pushed_range',
    `hpgauge_level` TEXT COMMENT 'hpgauge_level',
    `magical_skill_boost` TEXT COMMENT 'magical_skill_boost',
    `magical_skill_boost_resist` TEXT COMMENT 'magical_skill_boost_resist',
    `attack_delay` TEXT COMMENT 'attack_delay',
    `ai_name` TEXT COMMENT 'ai_name',
    `tribe` TEXT COMMENT 'tribe',
    `race_type` TEXT COMMENT 'race_type',
    `pet_ai_name` TEXT COMMENT 'pet_ai_name',
    `sensory_range` TEXT COMMENT 'sensory_range',
    `npc_type` TEXT COMMENT 'npc_type',
    `talking_distance` TEXT COMMENT 'talking_distance',
    `abyss_npc_type` TEXT COMMENT 'abyss_npc_type',
    `artifact_id` TEXT COMMENT 'artifact_id',
    `user_animation` TEXT COMMENT 'user_animation',
    `material` TEXT COMMENT 'material',
    `foot_mat` TEXT COMMENT 'foot_mat',
    `dmg_decal_texture` TEXT COMMENT 'dmg_decal_texture',
    `hide_shadow` TEXT COMMENT 'hide_shadow',
    `disk_type` TEXT COMMENT 'disk_type',
    `in_time` TEXT COMMENT 'in_time',
    `out_time` TEXT COMMENT 'out_time',
    `neck_angle` TEXT COMMENT 'neck_angle',
    `spine_angle` TEXT COMMENT 'spine_angle',
    `game_lang` TEXT COMMENT 'game_lang',
    `attack_range` TEXT COMMENT 'attack_range',
    `attack_rate` TEXT COMMENT 'attack_rate',
    `show_dmg_decal` TEXT COMMENT 'show_dmg_decal',
    `appearance` TEXT COMMENT 'appearance',
    `visible_equipments` TEXT COMMENT 'visible_equipments',
    `fxc_type` TEXT COMMENT 'fxc_type',
    `npc_function_type` TEXT COMMENT 'npc_function_type',
    `npc_title` TEXT COMMENT 'npc_title',
    `ui_race_type` TEXT COMMENT 'ui_race_type',
    `hide_path` TEXT COMMENT 'hide_path',
    `hide_map` TEXT COMMENT 'hide_map',
    `recovery` TEXT COMMENT 'recovery',
    `recovery_opt1` TEXT COMMENT 'recovery_opt1',
    `recovery_opt2` TEXT COMMENT 'recovery_opt2',
    `weapon_hit_fx` TEXT COMMENT 'weapon_hit_fx',
    `ammo_hit_fx` TEXT COMMENT 'ammo_hit_fx',
    `float_corpse` TEXT COMMENT 'float_corpse',
    `appearance_custom` TEXT COMMENT 'appearance_custom',
    `static` TEXT COMMENT 'static',
    `ment` TEXT COMMENT 'ment',
    `quest_ai_name` TEXT COMMENT 'quest_ai_name',
    `ammo_bone` TEXT COMMENT 'ammo_bone',
    `ammo_fx` TEXT COMMENT 'ammo_fx',
    `ammo_speed` TEXT COMMENT 'ammo_speed',
    `visible_range` TEXT COMMENT 'visible_range',
    `str_type` TEXT COMMENT 'str_type',
    `can_talk_invisible` TEXT COMMENT 'can_talk_invisible',
    `talk_delay_time` TEXT COMMENT 'talk_delay_time',
    `check_can_see` TEXT COMMENT 'check_can_see',
    `subdialog_type` TEXT COMMENT 'subdialog_type',
    `ammo_hit2_fx` TEXT COMMENT 'ammo_hit2_fx',
    `ammo2_fx` TEXT COMMENT 'ammo2_fx',
    `spawn_animation` TEXT COMMENT 'spawn_animation',
    `dmg_texture` TEXT COMMENT 'dmg_texture',
    `huge_mob` TEXT COMMENT 'huge_mob',
    `idle_animation` TEXT COMMENT 'idle_animation',
    `mobile_event` TEXT COMMENT 'mobile_event',
    `sanctuary_animation` TEXT COMMENT 'sanctuary_animation',
    `deadbody_name_id` TEXT COMMENT 'deadbody_name_id',
    `airlines_name` TEXT COMMENT 'airlines_name',
    `extendcharwarehouse_start` TEXT COMMENT 'extendcharwarehouse_start',
    `extendcharwarehouse_end` TEXT COMMENT 'extendcharwarehouse_end',
    `title_offset` TEXT COMMENT 'title_offset',
    `unrotatable` TEXT COMMENT 'unrotatable',
    `undetectable` TEXT COMMENT 'undetectable',
    `talk_animation` TEXT COMMENT 'talk_animation',
    `massive_looting_num` TEXT COMMENT 'massive_looting_num',
    `massive_looting_item` TEXT COMMENT 'massive_looting_item',
    `remove_item_option` TEXT COMMENT 'remove_item_option',
    `extra_currency_trade_info` TEXT COMMENT 'extra_currency_trade_info',
    `walk_animation` TEXT COMMENT 'walk_animation',
    `abyss_trade_info` TEXT COMMENT 'abyss_trade_info',
    `abyss_trade_buy_info` TEXT COMMENT 'abyss_trade_buy_info',
    `use_script` TEXT COMMENT 'use_script',
    `match_maker` TEXT COMMENT 'match_maker',
    `ammo_hit3_fx` TEXT COMMENT 'ammo_hit3_fx',
    `ammo3_fx` TEXT COMMENT 'ammo3_fx',
    `massive_looting_min_level` TEXT COMMENT 'massive_looting_min_level',
    `massive_looting_max_level` TEXT COMMENT 'massive_looting_max_level'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_abyss_monster';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_abyss_monster__bound_radius;
CREATE TABLE xmldb_suiyue.client_npcs_abyss_monster__bound_radius (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `front` VARCHAR(9) COMMENT 'front',
    `side` VARCHAR(9) COMMENT 'side',
    `upper` VARCHAR(9) COMMENT 'upper'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_abyss_monster__bound_radius';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_abyss_monster__appearance;
CREATE TABLE xmldb_suiyue.client_npcs_abyss_monster__appearance (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `pc_type` VARCHAR(11) COMMENT 'pc_type',
    `face_type` VARCHAR(1) COMMENT 'face_type',
    `hair_type` VARCHAR(2) COMMENT 'hair_type',
    `hair_color` VARCHAR(11) COMMENT 'hair_color',
    `face_color` VARCHAR(11) COMMENT 'face_color'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_abyss_monster__appearance';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_abyss_monster__visible_equipments;
CREATE TABLE xmldb_suiyue.client_npcs_abyss_monster__visible_equipments (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `torso` VARCHAR(32) COMMENT 'torso',
    `leg` VARCHAR(32) COMMENT 'leg',
    `foot` VARCHAR(32) COMMENT 'foot',
    `shoulder` VARCHAR(35) COMMENT 'shoulder',
    `glove` VARCHAR(32) COMMENT 'glove',
    `main` VARCHAR(33) COMMENT 'main',
    `sub` VARCHAR(32) COMMENT 'sub',
    `head` VARCHAR(31) COMMENT 'head',
    `wing_always` VARCHAR(1) COMMENT 'wing_always'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_abyss_monster__visible_equipments';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_abyss_monster__extra_currency_trade_info__t_l__d;
CREATE TABLE xmldb_suiyue.client_npcs_abyss_monster__extra_currency_trade_info__t_l__d (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `etab` VARCHAR(32) COMMENT 'etab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_abyss_monster__extra_currency_trade_info__t_l__d';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_abyss_monster__abyss_trade_info__tab_list__data;
CREATE TABLE xmldb_suiyue.client_npcs_abyss_monster__abyss_trade_info__tab_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `atab` VARCHAR(25) COMMENT 'atab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_abyss_monster__abyss_trade_info__tab_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_abyss_monster__abyss_trade_buy_info;
CREATE TABLE xmldb_suiyue.client_npcs_abyss_monster__abyss_trade_buy_info (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `tab_list` VARCHAR(64) COMMENT 'tab_list',
    `ap_buy_price_rate2` VARCHAR(4) COMMENT 'ap_buy_price_rate2'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_abyss_monster__abyss_trade_buy_info';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_abyss_monster__abyss_trade_buy_info__tab_list__d;
CREATE TABLE xmldb_suiyue.client_npcs_abyss_monster__abyss_trade_buy_info__tab_list__d (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `buy_atab` VARCHAR(28) COMMENT 'buy_atab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_abyss_monster__abyss_trade_buy_info__tab_list__d';

