DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc;
CREATE TABLE xmldb_suiyue.client_npcs_npc (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` TEXT COMMENT 'name',
    `desc` TEXT COMMENT 'desc',
    `dir` TEXT COMMENT 'dir',
    `mesh` TEXT COMMENT 'mesh',
    `material` TEXT COMMENT 'material',
    `show_dmg_decal` TEXT COMMENT 'show_dmg_decal',
    `ui_type` TEXT COMMENT 'ui_type',
    `cursor_type` TEXT COMMENT 'cursor_type',
    `hide_path` TEXT COMMENT 'hide_path',
    `hide_map` TEXT COMMENT 'hide_map',
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
    `in_time` TEXT COMMENT 'in_time',
    `out_time` TEXT COMMENT 'out_time',
    `neck_angle` TEXT COMMENT 'neck_angle',
    `spine_angle` TEXT COMMENT 'spine_angle',
    `ammo_bone` TEXT COMMENT 'ammo_bone',
    `ammo_fx` TEXT COMMENT 'ammo_fx',
    `ammo_speed` TEXT COMMENT 'ammo_speed',
    `pushed_range` TEXT COMMENT 'pushed_range',
    `hpgauge_level` TEXT COMMENT 'hpgauge_level',
    `magical_skill_boost` TEXT COMMENT 'magical_skill_boost',
    `attack_delay` TEXT COMMENT 'attack_delay',
    `ai_name` TEXT COMMENT 'ai_name',
    `tribe` TEXT COMMENT 'tribe',
    `pet_ai_name` TEXT COMMENT 'pet_ai_name',
    `sensory_range` TEXT COMMENT 'sensory_range',
    `attack_range` TEXT COMMENT 'attack_range',
    `attack_rate` TEXT COMMENT 'attack_rate',
    `magical_skill_boost_resist` TEXT COMMENT 'magical_skill_boost_resist',
    `race_type` TEXT COMMENT 'race_type',
    `npc_type` TEXT COMMENT 'npc_type',
    `talking_distance` TEXT COMMENT 'talking_distance',
    `foot_mat` TEXT COMMENT 'foot_mat',
    `dmg_decal_texture` TEXT COMMENT 'dmg_decal_texture',
    `disk_type` TEXT COMMENT 'disk_type',
    `weapon_hit_fx` TEXT COMMENT 'weapon_hit_fx',
    `hide_shadow` TEXT COMMENT 'hide_shadow',
    `ammo_hit_fx` TEXT COMMENT 'ammo_hit_fx',
    `float_corpse` TEXT COMMENT 'float_corpse',
    `npc_title` TEXT COMMENT 'npc_title',
    `ui_race_type` TEXT COMMENT 'ui_race_type',
    `idle_animation` TEXT COMMENT 'idle_animation',
    `appearance` TEXT COMMENT 'appearance',
    `visible_equipments` TEXT COMMENT 'visible_equipments',
    `appearance_custom` TEXT COMMENT 'appearance_custom',
    `game_lang` TEXT COMMENT 'game_lang',
    `quest_ai_name` TEXT COMMENT 'quest_ai_name',
    `ment` TEXT COMMENT 'ment',
    `dmg_texture` TEXT COMMENT 'dmg_texture',
    `spawn_animation` TEXT COMMENT 'spawn_animation',
    `str_type` TEXT COMMENT 'str_type',
    `undetectable` TEXT COMMENT 'undetectable',
    `talk_animation` TEXT COMMENT 'talk_animation',
    `npc_function_type` TEXT COMMENT 'npc_function_type',
    `trade_info` TEXT COMMENT 'trade_info',
    `recovery` TEXT COMMENT 'recovery',
    `recovery_opt1` TEXT COMMENT 'recovery_opt1',
    `recovery_opt2` TEXT COMMENT 'recovery_opt2',
    `walk_animation` TEXT COMMENT 'walk_animation',
    `airlines_name` TEXT COMMENT 'airlines_name',
    `title_offset` TEXT COMMENT 'title_offset',
    `data` TEXT COMMENT 'data',
    `extra_currency_trade_info` TEXT COMMENT 'extra_currency_trade_info',
    `extendcharwarehouse_start` TEXT COMMENT 'extendcharwarehouse_start',
    `extendcharwarehouse_end` TEXT COMMENT 'extendcharwarehouse_end',
    `give_item_proc` TEXT COMMENT 'give_item_proc',
    `remove_item_option` TEXT COMMENT 'remove_item_option',
    `change_item_skin` TEXT COMMENT 'change_item_skin',
    `gather_skill_levelup` TEXT COMMENT 'gather_skill_levelup',
    `func_giveup_craftskill` TEXT COMMENT 'func_giveup_craftskill',
    `extendinventory_start` TEXT COMMENT 'extendinventory_start',
    `extendinventory_end` TEXT COMMENT 'extendinventory_end',
    `package_permitted` TEXT COMMENT 'package_permitted',
    `town_residence` TEXT COMMENT 'town_residence',
    `extendaccountwarehouse_start` TEXT COMMENT 'extendaccountwarehouse_start',
    `extendaccountwarehouse_end` TEXT COMMENT 'extendaccountwarehouse_end',
    `npcfaction_name` TEXT COMMENT 'npcfaction_name',
    `guide_func` TEXT COMMENT 'guide_func',
    `abyss_trade_info` TEXT COMMENT 'abyss_trade_info',
    `pvpzone` TEXT COMMENT 'pvpzone',
    `pvpzone_world_name` TEXT COMMENT 'pvpzone_world_name',
    `pvpzone_location_alias` TEXT COMMENT 'pvpzone_location_alias',
    `edit_character_gender` TEXT COMMENT 'edit_character_gender',
    `edit_character_all` TEXT COMMENT 'edit_character_all',
    `fxc_type` TEXT COMMENT 'fxc_type',
    `save_trade_count` TEXT COMMENT 'save_trade_count',
    `talk_delay_time` TEXT COMMENT 'talk_delay_time',
    `user_animation` TEXT COMMENT 'user_animation',
    `func_itemcharge` TEXT COMMENT 'func_itemcharge',
    `trade_in_trade_info` TEXT COMMENT 'trade_in_trade_info',
    `func_town_challenge` TEXT COMMENT 'func_town_challenge',
    `abyss_trade_buy_info` TEXT COMMENT 'abyss_trade_buy_info',
    `huge_mob` TEXT COMMENT 'huge_mob',
    `func_itemcharge2` TEXT COMMENT 'func_itemcharge2',
    `exclusion_list` TEXT COMMENT 'exclusion_list',
    `check_can_see` TEXT COMMENT 'check_can_see',
    `item_replace` TEXT COMMENT 'item_replace',
    `custom_match_maker` TEXT COMMENT 'custom_match_maker',
    `use_script` TEXT COMMENT 'use_script',
    `static` TEXT COMMENT 'static',
    `bindstone_type` TEXT COMMENT 'bindstone_type',
    `bindstone_capacity` TEXT COMMENT 'bindstone_capacity',
    `visible_range` TEXT COMMENT 'visible_range',
    `bindstone_usecount` TEXT COMMENT 'bindstone_usecount',
    `abyss_npc_type` TEXT COMMENT 'abyss_npc_type',
    `object_type` TEXT COMMENT 'object_type',
    `html_bg` TEXT COMMENT 'html_bg',
    `omit_system_msg` TEXT COMMENT 'omit_system_msg',
    `flag_type` TEXT COMMENT 'flag_type',
    `unrotatable` TEXT COMMENT 'unrotatable',
    `no_autotarget` TEXT COMMENT 'no_autotarget',
    `sanctuary_animation` TEXT COMMENT 'sanctuary_animation',
    `can_talk_invisible` TEXT COMMENT 'can_talk_invisible',
    `faction_amount` TEXT COMMENT 'faction_amount',
    `subdialog_type` TEXT COMMENT 'subdialog_type',
    `instance_entry` TEXT COMMENT 'instance_entry',
    `ins_creation_id_1` TEXT COMMENT 'ins_creation_id_1',
    `ins_creation_id_2` TEXT COMMENT 'ins_creation_id_2',
    `func_enterhouse_to_instant` TEXT COMMENT 'func_enterhouse_to_instant',
    `subdialog_value` TEXT COMMENT 'subdialog_value',
    `compound_weapon` TEXT COMMENT 'compound_weapon',
    `coupon_trade_info` TEXT COMMENT 'coupon_trade_info',
    `func_pet_manage` TEXT COMMENT 'func_pet_manage',
    `match_maker` TEXT COMMENT 'match_maker',
    `abyss_qina_trade_info` TEXT COMMENT 'abyss_qina_trade_info',
    `trade_buy_info` TEXT COMMENT 'trade_buy_info',
    `item_upgrade` TEXT COMMENT 'item_upgrade',
    `itemcharge_rate` TEXT COMMENT 'itemcharge_rate',
    `func_personal_auction` TEXT COMMENT 'func_personal_auction',
    `func_housing_pay_rent` TEXT COMMENT 'func_housing_pay_rent',
    `func_housing_kick` TEXT COMMENT 'func_housing_kick',
    `func_housing_change_building` TEXT COMMENT 'func_housing_change_building',
    `func_housing_config` TEXT COMMENT 'func_housing_config',
    `func_housing_script` TEXT COMMENT 'func_housing_script',
    `func_recreate_personal_ins` TEXT COMMENT 'func_recreate_personal_ins',
    `vip_grade_min` TEXT COMMENT 'vip_grade_min',
    `vip_grade_max` TEXT COMMENT 'vip_grade_max',
    `war_flag_group_id` TEXT COMMENT 'war_flag_group_id',
    `scaling_drop` TEXT COMMENT 'scaling_drop',
    `event_tool_trade_info` TEXT COMMENT 'event_tool_trade_info'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__bound_radius;
CREATE TABLE xmldb_suiyue.client_npcs_npc__bound_radius (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `front` VARCHAR(9) COMMENT 'front',
    `side` VARCHAR(9) COMMENT 'side',
    `upper` VARCHAR(9) COMMENT 'upper'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__bound_radius';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__appearance;
CREATE TABLE xmldb_suiyue.client_npcs_npc__appearance (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `pc_type` VARCHAR(12) COMMENT 'pc_type',
    `face_type` VARCHAR(2) COMMENT 'face_type',
    `hair_type` VARCHAR(2) COMMENT 'hair_type',
    `face_color` VARCHAR(12) COMMENT 'face_color',
    `hair_color` VARCHAR(12) COMMENT 'hair_color'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__appearance';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__visible_equipments;
CREATE TABLE xmldb_suiyue.client_npcs_npc__visible_equipments (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `torso` VARCHAR(45) COMMENT 'torso',
    `leg` VARCHAR(39) COMMENT 'leg',
    `foot` VARCHAR(39) COMMENT 'foot',
    `shoulder` VARCHAR(42) COMMENT 'shoulder',
    `glove` VARCHAR(39) COMMENT 'glove',
    `main` VARCHAR(43) COMMENT 'main',
    `sub` VARCHAR(40) COMMENT 'sub',
    `head` VARCHAR(41) COMMENT 'head',
    `wing_mesh` VARCHAR(44) COMMENT 'wing_mesh',
    `wing_always` VARCHAR(1) COMMENT 'wing_always',
    `left_ear` VARCHAR(19) COMMENT 'left_ear',
    `right_ear` VARCHAR(22) COMMENT 'right_ear'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__visible_equipments';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__trade_info;
CREATE TABLE xmldb_suiyue.client_npcs_npc__trade_info (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `tab_list` VARCHAR(64) COMMENT 'tab_list',
    `buy_price_rate` VARCHAR(4) COMMENT 'buy_price_rate',
    `sell_price_rate` VARCHAR(8) COMMENT 'sell_price_rate'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__trade_info';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__trade_info__tab_list__data;
CREATE TABLE xmldb_suiyue.client_npcs_npc__trade_info__tab_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `tab` VARCHAR(44) COMMENT 'tab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__trade_info__tab_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__data;
CREATE TABLE xmldb_suiyue.client_npcs_npc__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `buy_price_rate` VARCHAR(4) COMMENT 'buy_price_rate',
    `sell_price_rate` VARCHAR(8) COMMENT 'sell_price_rate',
    `tab_list` VARCHAR(64) COMMENT 'tab_list',
    `ap_sell_price_rate` VARCHAR(4) COMMENT 'ap_sell_price_rate',
    `sell_price_rate2` VARCHAR(4) COMMENT 'sell_price_rate2',
    `ap_sell_price_rate2` VARCHAR(4) COMMENT 'ap_sell_price_rate2',
    `ap_buy_price_rate2` VARCHAR(6) COMMENT 'ap_buy_price_rate2'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__data__tab_list__data;
CREATE TABLE xmldb_suiyue.client_npcs_npc__data__tab_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `tab` VARCHAR(44) COMMENT 'tab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__data__tab_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__extra_currency_trade_info__tab_list__data;
CREATE TABLE xmldb_suiyue.client_npcs_npc__extra_currency_trade_info__tab_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `etab` VARCHAR(51) COMMENT 'etab',
    `tab` VARCHAR(44) COMMENT 'tab',
    `ttab` VARCHAR(38) COMMENT 'ttab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__extra_currency_trade_info__tab_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__abyss_trade_info;
CREATE TABLE xmldb_suiyue.client_npcs_npc__abyss_trade_info (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `tab_list` VARCHAR(64) COMMENT 'tab_list',
    `ap_buy_price_rate` VARCHAR(3) COMMENT 'ap_buy_price_rate',
    `ap_sell_price_rate` VARCHAR(4) COMMENT 'ap_sell_price_rate'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__abyss_trade_info';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__abyss_trade_info__tab_list__data;
CREATE TABLE xmldb_suiyue.client_npcs_npc__abyss_trade_info__tab_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `atab` VARCHAR(43) COMMENT 'atab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__abyss_trade_info__tab_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__trade_in_trade_info__tab_list__data;
CREATE TABLE xmldb_suiyue.client_npcs_npc__trade_in_trade_info__tab_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `ttab` VARCHAR(38) COMMENT 'ttab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__trade_in_trade_info__tab_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__abyss_trade_buy_info;
CREATE TABLE xmldb_suiyue.client_npcs_npc__abyss_trade_buy_info (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `tab_list` VARCHAR(64) COMMENT 'tab_list',
    `ap_buy_price_rate2` VARCHAR(6) COMMENT 'ap_buy_price_rate2'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__abyss_trade_buy_info';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__abyss_trade_buy_info__tab_list__data;
CREATE TABLE xmldb_suiyue.client_npcs_npc__abyss_trade_buy_info__tab_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `buy_atab` VARCHAR(41) COMMENT 'buy_atab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__abyss_trade_buy_info__tab_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__coupon_trade_info__tab_list__data;
CREATE TABLE xmldb_suiyue.client_npcs_npc__coupon_trade_info__tab_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `ctab` VARCHAR(21) COMMENT 'ctab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__coupon_trade_info__tab_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__abyss_qina_trade_info;
CREATE TABLE xmldb_suiyue.client_npcs_npc__abyss_qina_trade_info (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `tab_list` VARCHAR(64) COMMENT 'tab_list',
    `sell_price_rate2` VARCHAR(4) COMMENT 'sell_price_rate2',
    `ap_sell_price_rate2` VARCHAR(4) COMMENT 'ap_sell_price_rate2'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__abyss_qina_trade_info';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__abyss_qina_trade_info__tab_list__data;
CREATE TABLE xmldb_suiyue.client_npcs_npc__abyss_qina_trade_info__tab_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `ktab` VARCHAR(47) COMMENT 'ktab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__abyss_qina_trade_info__tab_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__trade_buy_info;
CREATE TABLE xmldb_suiyue.client_npcs_npc__trade_buy_info (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `tab_list` VARCHAR(64) COMMENT 'tab_list',
    `buy_price_rate2` VARCHAR(8) COMMENT 'buy_price_rate2'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__trade_buy_info';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__trade_buy_info__tab_list__data;
CREATE TABLE xmldb_suiyue.client_npcs_npc__trade_buy_info__tab_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `buy_tab` VARCHAR(37) COMMENT 'buy_tab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__trade_buy_info__tab_list__data';

DROP TABLE IF EXISTS xmldb_suiyue.client_npcs_npc__event_tool_trade_info__tab_list__data;
CREATE TABLE xmldb_suiyue.client_npcs_npc__event_tool_trade_info__tab_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `ettab` VARCHAR(30) COMMENT 'ettab'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'client_npcs_npc__event_tool_trade_info__tab_list__data';

