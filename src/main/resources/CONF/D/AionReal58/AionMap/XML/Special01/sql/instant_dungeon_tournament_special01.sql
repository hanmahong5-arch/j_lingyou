DROP TABLE IF EXISTS xmldb_suiyue.instant_dungeon_tournament_special01;
CREATE TABLE xmldb_suiyue.instant_dungeon_tournament_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(15) COMMENT 'name',
    `type` VARCHAR(13) COMMENT 'type',
    `lobby_insname` VARCHAR(37) COMMENT 'lobby_insname',
    `match_insname` VARCHAR(36) COMMENT 'match_insname',
    `door_id` VARCHAR(1) COMMENT 'door_id',
    `wait_time` VARCHAR(2) COMMENT 'wait_time',
    `limit_time` VARCHAR(3) COMMENT 'limit_time',
    `over_time` VARCHAR(3) COMMENT 'over_time',
    `round_list` VARCHAR(64) COMMENT 'round_list',
    `door_reopen_time` VARCHAR(2) COMMENT 'door_reopen_time',
    `relay_time` VARCHAR(2) COMMENT 'relay_time',
    `start_condition` VARCHAR(21) COMMENT 'start_condition',
    `over_condition` VARCHAR(21) COMMENT 'over_condition',
    `rebirthbuff_id` VARCHAR(1) COMMENT 'rebirthbuff_id',
    `rebirthbuff_duration` VARCHAR(2) COMMENT 'rebirthbuff_duration'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'instant_dungeon_tournament_special01';

DROP TABLE IF EXISTS xmldb_suiyue.instant_dungeon_tournament_special01__round_list__data;
CREATE TABLE xmldb_suiyue.instant_dungeon_tournament_special01__round_list__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `round` VARCHAR(1) COMMENT 'round',
    `win_kill_point` VARCHAR(3) COMMENT 'win_kill_point',
    `item1_name` VARCHAR(17) COMMENT 'item1_name',
    `item1_cnt` VARCHAR(2) COMMENT 'item1_cnt',
    `item2_name` VARCHAR(17) COMMENT 'item2_name',
    `item2_cnt` VARCHAR(2) COMMENT 'item2_cnt',
    `exp` VARCHAR(7) COMMENT 'exp',
    `ap` VARCHAR(5) COMMENT 'ap',
    `item3_name` VARCHAR(17) COMMENT 'item3_name',
    `item3_cnt` VARCHAR(2) COMMENT 'item3_cnt',
    `gold` VARCHAR(6) COMMENT 'gold'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'instant_dungeon_tournament_special01__round_list__data';

