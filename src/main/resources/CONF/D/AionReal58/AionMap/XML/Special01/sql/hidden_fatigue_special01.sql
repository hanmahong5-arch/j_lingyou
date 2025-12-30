DROP TABLE IF EXISTS xmldb_suiyue.hidden_fatigue_special01;
CREATE TABLE xmldb_suiyue.hidden_fatigue_special01 (
    `name` VARCHAR(255) PRIMARY KEY COMMENT 'name',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `base_point` VARCHAR(6) COMMENT 'base_point',
    `base_npc_kill` VARCHAR(6) COMMENT 'base_npc_kill',
    `pvp_offence_rate` VARCHAR(3) COMMENT 'pvp_offence_rate',
    `pvp_defence_rate` VARCHAR(3) COMMENT 'pvp_defence_rate',
    `exp_gain_rate` VARCHAR(3) COMMENT 'exp_gain_rate',
    `item_drop_rate` VARCHAR(3) COMMENT 'item_drop_rate',
    `item_gain_limit` VARCHAR(3) COMMENT 'item_gain_limit',
    `money_gain_rate` VARCHAR(3) COMMENT 'money_gain_rate',
    `ap_gain_rate` VARCHAR(3) COMMENT 'ap_gain_rate',
    `gather_gain_rate` VARCHAR(3) COMMENT 'gather_gain_rate',
    `enter_hidden_channel` VARCHAR(1) COMMENT 'enter_hidden_channel',
    `_attr_hidden_fatigue_level` VARCHAR(128) COMMENT 'hidden_fatigue_level'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'hidden_fatigue_special01';

