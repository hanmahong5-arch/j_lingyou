DROP TABLE IF EXISTS xmldb_suiyue.abyss_rank_special01;
CREATE TABLE xmldb_suiyue.abyss_rank_special01 (
    `id` VARCHAR(255) PRIMARY KEY COMMENT 'id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `name` VARCHAR(19) COMMENT 'name',
    `desc` VARCHAR(22) COMMENT 'desc',
    `race` VARCHAR(8) COMMENT 'race',
    `rank` VARCHAR(2) COMMENT 'rank',
    `drop_point` VARCHAR(4) COMMENT 'drop_point',
    `deathpenalty_point` VARCHAR(4) COMMENT 'deathpenalty_point',
    `give_max_per_user` VARCHAR(5) COMMENT 'give_max_per_user',
    `delay_time` VARCHAR(5) COMMENT 'delay_time',
    `get_max_from_all_user` VARCHAR(5) COMMENT 'get_max_from_all_user',
    `get_max_reduce_amount` VARCHAR(4) COMMENT 'get_max_reduce_amount',
    `get_max_reduce_interval` VARCHAR(3) COMMENT 'get_max_reduce_interval',
    `notify_die` VARCHAR(1) COMMENT 'notify_die',
    `require_point` VARCHAR(6) COMMENT 'require_point',
    `require_order` VARCHAR(4) COMMENT 'require_order',
    `rank_diff_mod` VARCHAR(64) COMMENT 'rank_diff_mod',
    `abyss_ranker_skills` VARCHAR(64) COMMENT 'abyss_ranker_skills'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_rank_special01';

DROP TABLE IF EXISTS xmldb_suiyue.abyss_rank_special01__rank_diff_mod__data;
CREATE TABLE xmldb_suiyue.abyss_rank_special01__rank_diff_mod__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `diff_mod` VARCHAR(3) COMMENT 'diff_mod'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_rank_special01__rank_diff_mod__data';

DROP TABLE IF EXISTS xmldb_suiyue.abyss_rank_special01__abyss_ranker_skills__data;
CREATE TABLE xmldb_suiyue.abyss_rank_special01__abyss_ranker_skills__data (
    `id` VARCHAR(255) COMMENT '继承父id',
    `__order_index` INT NOT NULL DEFAULT 0 COMMENT '顺序索引',
    `ranker_skill` VARCHAR(38) COMMENT 'ranker_skill',
    `ranker_skill_level` VARCHAR(1) COMMENT 'ranker_skill_level'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT = 'abyss_rank_special01__abyss_ranker_skills__data';

